#include "det_wrapper.h"
#include "paddle_api.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <queue>
#include <vector>

using paddle::lite_api::CreatePaddlePredictor;
using paddle::lite_api::MobileConfig;
using paddle::lite_api::PaddlePredictor;
using paddle::lite_api::shape_t;

static const int DET_LIMIT_SIDE_LEN = 736;
static const int DET_STRIDE = 32;
static const float DET_BOX_THRESH = 0.30f;
static const float DET_MIN_SCORE = 0.35f;
static const int DET_MIN_AREA = 12;
static const int DET_MAX_BOXES = 80;

struct DetWrapperImpl {
    std::shared_ptr<PaddlePredictor> predictor;
};

static int alignToStride(int value) {
    int aligned = static_cast<int>(std::ceil(value / static_cast<float>(DET_STRIDE))) * DET_STRIDE;
    return std::max(DET_STRIDE, aligned);
}

static void computeDetSize(int srcW, int srcH, int* dstW, int* dstH) {
    float ratio = 1.0f;
    int maxSide = std::max(srcW, srcH);
    if (maxSide > DET_LIMIT_SIDE_LEN) {
        ratio = DET_LIMIT_SIDE_LEN / static_cast<float>(maxSide);
    }
    *dstW = alignToStride(static_cast<int>(std::round(srcW * ratio)));
    *dstH = alignToStride(static_cast<int>(std::round(srcH * ratio)));
}

static void preprocessDet(const unsigned char* src, int srcW, int srcH, int srcStride,
                          float* dst, int dstW, int dstH) {
    static const float mean[3] = {0.485f, 0.456f, 0.406f};
    static const float stdVal[3] = {0.229f, 0.224f, 0.225f};

    for (int c = 0; c < 3; ++c) {
        float* dstC = dst + c * dstH * dstW;
        for (int y = 0; y < dstH; ++y) {
            float srcY = y * static_cast<float>(srcH) / static_cast<float>(dstH);
            int y1 = std::min(static_cast<int>(srcY), srcH - 1);
            int y2 = std::min(y1 + 1, srcH - 1);
            float fy = srcY - y1;
            for (int x = 0; x < dstW; ++x) {
                float srcX = x * static_cast<float>(srcW) / static_cast<float>(dstW);
                int x1 = std::min(static_cast<int>(srcX), srcW - 1);
                int x2 = std::min(x1 + 1, srcW - 1);
                float fx = srcX - x1;
                float p1 = src[y1 * srcStride + x1 * 3 + (2 - c)];
                float p2 = src[y1 * srcStride + x2 * 3 + (2 - c)];
                float p3 = src[y2 * srcStride + x1 * 3 + (2 - c)];
                float p4 = src[y2 * srcStride + x2 * 3 + (2 - c)];
                float val = (p1 * (1 - fx) + p2 * fx) * (1 - fy) + (p3 * (1 - fx) + p4 * fx) * fy;
                dstC[y * dstW + x] = (val / 255.0f - mean[c]) / stdVal[c];
            }
        }
    }
}

static std::vector<DetBox> extractBoxes(const float* scoreMap, int mapW, int mapH, int srcW, int srcH) {
    std::vector<unsigned char> visited(mapW * mapH, 0);
    std::vector<DetBox> boxes;
    const int dx[4] = {1, -1, 0, 0};
    const int dy[4] = {0, 0, 1, -1};
    float scaleX = srcW / static_cast<float>(mapW);
    float scaleY = srcH / static_cast<float>(mapH);

    for (int y = 0; y < mapH; ++y) {
        for (int x = 0; x < mapW; ++x) {
            int start = y * mapW + x;
            if (visited[start] || scoreMap[start] < DET_BOX_THRESH) continue;

            std::queue<int> queue;
            queue.push(start);
            visited[start] = 1;
            int minX = x;
            int maxX = x;
            int minY = y;
            int maxY = y;
            int count = 0;
            float scoreSum = 0.0f;

            while (!queue.empty()) {
                int index = queue.front();
                queue.pop();
                int cx = index % mapW;
                int cy = index / mapW;
                ++count;
                scoreSum += scoreMap[index];
                minX = std::min(minX, cx);
                maxX = std::max(maxX, cx);
                minY = std::min(minY, cy);
                maxY = std::max(maxY, cy);

                for (int k = 0; k < 4; ++k) {
                    int nx = cx + dx[k];
                    int ny = cy + dy[k];
                    if (nx < 0 || ny < 0 || nx >= mapW || ny >= mapH) continue;
                    int ni = ny * mapW + nx;
                    if (visited[ni] || scoreMap[ni] < DET_BOX_THRESH) continue;
                    visited[ni] = 1;
                    queue.push(ni);
                }
            }

            float score = scoreSum / std::max(1, count);
            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            if (count < DET_MIN_AREA || score < DET_MIN_SCORE || width < 2 || height < 2) continue;

            DetBox box;
            box.left = std::max(0, static_cast<int>(std::floor(minX * scaleX)));
            box.top = std::max(0, static_cast<int>(std::floor(minY * scaleY)));
            box.right = std::min(srcW, static_cast<int>(std::ceil((maxX + 1) * scaleX)));
            box.bottom = std::min(srcH, static_cast<int>(std::ceil((maxY + 1) * scaleY)));
            box.score = score;
            if (box.right > box.left && box.bottom > box.top) {
                boxes.push_back(box);
            }
        }
    }

    std::sort(boxes.begin(), boxes.end(), [](const DetBox& a, const DetBox& b) {
        if (std::abs(a.top - b.top) > 8) return a.top < b.top;
        return a.left < b.left;
    });
    if (boxes.size() > DET_MAX_BOXES) boxes.resize(DET_MAX_BOXES);
    return boxes;
}

DetWrapper::DetWrapper() : impl_(new DetWrapperImpl()) {}

DetWrapper::~DetWrapper() {
    release();
    delete impl_;
    impl_ = nullptr;
}

bool DetWrapper::init(const std::string& modelPath) {
    release();
    MobileConfig config;
    config.set_model_from_file(modelPath);
    config.set_power_mode(paddle::lite_api::LITE_POWER_NO_BIND);
    config.set_threads(1);
    impl_->predictor = CreatePaddlePredictor(config);
    return impl_->predictor != nullptr;
}

std::vector<DetBox> DetWrapper::detect(const unsigned char* bgrData, int width, int height, int stride) {
    if (!impl_->predictor || width <= 0 || height <= 0) return {};

    int detW = 0;
    int detH = 0;
    computeDetSize(width, height, &detW, &detH);
    std::vector<float> inputData(3 * detH * detW);
    preprocessDet(bgrData, width, height, stride, inputData.data(), detW, detH);

    auto input = impl_->predictor->GetInput(0);
    input->Resize(shape_t{1, 3, detH, detW});
    auto* inputPtr = input->mutable_data<float>();
    std::memcpy(inputPtr, inputData.data(), inputData.size() * sizeof(float));

    impl_->predictor->Run();

    auto output = impl_->predictor->GetOutput(0);
    auto shape = output->shape();
    const float* outputData = output->data<float>();
    if (shape.size() == 4) {
        int outH = static_cast<int>(shape[2]);
        int outW = static_cast<int>(shape[3]);
        return extractBoxes(outputData, outW, outH, width, height);
    }
    if (shape.size() == 3) {
        int outH = static_cast<int>(shape[1]);
        int outW = static_cast<int>(shape[2]);
        return extractBoxes(outputData, outW, outH, width, height);
    }
    return {};
}

void DetWrapper::release() {
    impl_->predictor.reset();
}
