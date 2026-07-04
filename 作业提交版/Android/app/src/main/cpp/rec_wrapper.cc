#include "rec_wrapper.h"
#include "paddle_api.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <memory>
#include <vector>

using paddle::lite_api::CreatePaddlePredictor;
using paddle::lite_api::MobileConfig;
using paddle::lite_api::PaddlePredictor;
using paddle::lite_api::Tensor;
using paddle::lite_api::shape_t;

static const int REC_IMAGE_HEIGHT = 48;
static const int REC_MAX_WIDTH = 320;

struct RecWrapperImpl {
    std::shared_ptr<PaddlePredictor> predictor;
    std::vector<std::string> dict;
    std::vector<int> labelIndices;
    int numClasses = 0;
};

static void loadLabels(RecWrapperImpl* impl, const std::string& path) {
    impl->dict.clear();
    impl->labelIndices.clear();
    std::ifstream file(path);
    if (!file.is_open()) return;

    std::string line;
    while (std::getline(file, line)) {
        if (!line.empty()) {
            impl->dict.push_back(line);
        }
    }
    // PP-OCRv5 rec: output classes = len(dict) + 1 (blank is index 0)
    impl->numClasses = static_cast<int>(impl->dict.size()) + 1;
}

static int computeResizedWidth(int srcW, int srcH) {
    float ratio = static_cast<float>(srcW) / static_cast<float>(srcH);
    int resizedW = static_cast<int>(std::ceil(REC_IMAGE_HEIGHT * ratio));
    return std::min(resizedW, REC_MAX_WIDTH);
}

static void preprocess(const unsigned char* src, int srcW, int srcH, int srcStride,
                       float* dst, int dstW) {
    float ratioW = static_cast<float>(srcW) / static_cast<float>(dstW);
    int effectiveH = static_cast<int>(REC_IMAGE_HEIGHT);
    int effectiveW = dstW;

    // Normalize: (pixel/255 - mean) / std  where mean=0.5, std=0.5
    for (int c = 0; c < 3; ++c) {
        float* dstC = dst + c * effectiveH * effectiveW;
        for (int y = 0; y < effectiveH; ++y) {
            for (int x = 0; x < effectiveW; ++x) {
                float srcX = x * ratioW;
                int x1 = static_cast<int>(srcX);
                int x2 = std::min(x1 + 1, srcW - 1);
                float fx = srcX - x1;
                float srcY = y * static_cast<float>(srcH) / static_cast<float>(effectiveH);
                int y1 = static_cast<int>(srcY);
                int y2 = std::min(y1 + 1, srcH - 1);
                float fy = srcY - y1;

                float p1 = src[y1 * srcStride + x1 * 3 + (2 - c)]; // BGR -> RGB
                float p2 = src[y1 * srcStride + x2 * 3 + (2 - c)];
                float p3 = src[y2 * srcStride + x1 * 3 + (2 - c)];
                float p4 = src[y2 * srcStride + x2 * 3 + (2 - c)];

                float val = (p1 * (1 - fx) + p2 * fx) * (1 - fy) + (p3 * (1 - fx) + p4 * fx) * fy;
                dstC[y * effectiveW + x] = (val / 255.0f - 0.5f) / 0.5f;
            }
        }
    }
}

static std::string ctcDecode(const float* output, int timeSteps, int numClasses,
                             const std::vector<std::string>& dict) {
    std::string result;
    int lastIndex = 0;
    for (int t = 0; t < timeSteps; ++t) {
        int maxIdx = 0;
        float maxVal = output[t * numClasses];
        for (int c = 1; c < numClasses; ++c) {
            float val = output[t * numClasses + c];
            if (val > maxVal) {
                maxVal = val;
                maxIdx = c;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIndex) {
            result += dict[maxIdx - 1];
        }
        lastIndex = maxIdx;
    }
    return result;
}

RecWrapper::RecWrapper() : impl_(new RecWrapperImpl()) {}

RecWrapper::~RecWrapper() {
    release();
    delete impl_;
    impl_ = nullptr;
}

bool RecWrapper::init(const std::string& modelPath, const std::string& labelPath) {
    release();
    loadLabels(impl_, labelPath);
    if (impl_->dict.empty()) return false;

    MobileConfig config;
    config.set_model_from_file(modelPath);
    config.set_power_mode(paddle::lite_api::LITE_POWER_NO_BIND);
    config.set_threads(1);

    impl_->predictor = CreatePaddlePredictor(config);
    return impl_->predictor != nullptr;
}

std::string RecWrapper::recognize(const unsigned char* bgrData, int width, int height, int stride) {
    if (!impl_->predictor || width <= 0 || height <= 0) return "";

    int resizedW = computeResizedWidth(width, height);
    std::vector<float> inputData(3 * REC_IMAGE_HEIGHT * resizedW);
    preprocess(bgrData, width, height, stride, inputData.data(), resizedW);

    auto input = impl_->predictor->GetInput(0);
    input->Resize(shape_t{1, 3, REC_IMAGE_HEIGHT, resizedW});
    auto* inputPtr = input->mutable_data<float>();
    std::memcpy(inputPtr, inputData.data(), inputData.size() * sizeof(float));

    impl_->predictor->Run();

    auto output = impl_->predictor->GetOutput(0);
    auto outputShape = output->shape();
    if (outputShape.size() != 3) return "";
    int timeSteps = static_cast<int>(outputShape[1]);
    int numClasses = static_cast<int>(outputShape[2]);
    const float* outputData = output->data<float>();

    return ctcDecode(outputData, timeSteps, numClasses, impl_->dict);
}

void RecWrapper::release() {
    impl_->predictor.reset();
}
