#include <jni.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include "rec_wrapper.h"

static RecWrapper gWrapper;

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

JNIEXPORT void JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeRelease(
    JNIEnv*, jclass) {
    gWrapper.release();
}

} // extern "C"
