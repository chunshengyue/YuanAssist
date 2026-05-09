package com.example.yuanassist.tableocr

import android.graphics.Bitmap

object PaddleOcrNative {
    private var initialized = false

    init {
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("paddle_light_api_shared")
            System.loadLibrary("paddle_ocr_jni")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    fun init(recModelPath: String, labelPath: String): Boolean {
        if (initialized) return true
        initialized = nativeInit(recModelPath, labelPath)
        return initialized
    }

    fun recognize(bitmap: Bitmap): String {
        if (!initialized) return ""
        return nativeRecognize(bitmap)
    }

    fun release() {
        if (initialized) {
            nativeRelease()
            initialized = false
        }
    }

    private external fun nativeInit(recModelPath: String, labelPath: String): Boolean
    private external fun nativeRecognize(bitmap: Bitmap): String
    private external fun nativeRelease()
}
