#include <jni.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include "det_wrapper.h"
#include "rec_wrapper.h"

static RecWrapper gWrapper;
static DetWrapper gDetWrapper;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeInit(
    JNIEnv* env, jclass, jstring recModelPath, jstring labelPath) {
    const char* modelPath = env->GetStringUTFChars(recModelPath, nullptr);
    const char* lblPath = env->GetStringUTFChars(labelPath, nullptr);
    bool ok = gWrapper.init(modelPath, lblPath);
    env->ReleaseStringUTFChars(recModelPath, modelPath);
    env->ReleaseStringUTFChars(labelPath, lblPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeInitDet(
    JNIEnv* env, jclass, jstring detModelPath) {
    const char* modelPath = env->GetStringUTFChars(detModelPath, nullptr);
    bool ok = gDetWrapper.init(modelPath);
    env->ReleaseStringUTFChars(detModelPath, modelPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeRecognize(
    JNIEnv* env, jclass, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return env->NewStringUTF("");
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return env->NewStringUTF("");
    }

    std::string result;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        int bgrStride = info.width * 3;
        std::vector<unsigned char> bgrBuf(info.height * bgrStride);
        const auto* rgba = static_cast<const unsigned char*>(pixels);
        for (int y = 0; y < info.height; ++y) {
            for (int x = 0; x < info.width; ++x) {
                int srcOff = y * info.stride + x * 4;
                int dstOff = y * bgrStride + x * 3;
                bgrBuf[dstOff + 0] = rgba[srcOff + 2];
                bgrBuf[dstOff + 1] = rgba[srcOff + 1];
                bgrBuf[dstOff + 2] = rgba[srcOff + 0];
            }
        }
        result = gWrapper.recognize(bgrBuf.data(), info.width, info.height, bgrStride);
    } else {
        result = gWrapper.recognize(
            static_cast<const unsigned char*>(pixels), info.width, info.height, info.stride);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jintArray JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeDetect(
    JNIEnv* env, jclass, jobject bitmap) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return env->NewIntArray(0);
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return env->NewIntArray(0);
    }

    std::vector<DetBox> boxes;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        int bgrStride = info.width * 3;
        std::vector<unsigned char> bgrBuf(info.height * bgrStride);
        const auto* rgba = static_cast<const unsigned char*>(pixels);
        for (int y = 0; y < info.height; ++y) {
            for (int x = 0; x < info.width; ++x) {
                int srcOff = y * info.stride + x * 4;
                int dstOff = y * bgrStride + x * 3;
                bgrBuf[dstOff + 0] = rgba[srcOff + 2];
                bgrBuf[dstOff + 1] = rgba[srcOff + 1];
                bgrBuf[dstOff + 2] = rgba[srcOff + 0];
            }
        }
        boxes = gDetWrapper.detect(bgrBuf.data(), info.width, info.height, bgrStride);
    } else {
        boxes = gDetWrapper.detect(
            static_cast<const unsigned char*>(pixels), info.width, info.height, info.stride);
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    std::vector<jint> packed;
    packed.reserve(boxes.size() * 5);
    for (const auto& box : boxes) {
        packed.push_back(box.left);
        packed.push_back(box.top);
        packed.push_back(box.right);
        packed.push_back(box.bottom);
        packed.push_back(static_cast<jint>(box.score * 10000.0f));
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(packed.size()));
    if (!packed.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(packed.size()), packed.data());
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeRelease(
    JNIEnv*, jclass) {
    gWrapper.release();
    gDetWrapper.release();
}

} // extern "C"
