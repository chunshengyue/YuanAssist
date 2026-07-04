package com.example.yuanassist.tableocr

import android.graphics.Bitmap

object PaddleOcrNative {
    private var libraryLoaded = false
    private var initialized = false
    private var detInitialized = false

    init {
        libraryLoaded = try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("paddle_light_api_shared")
            System.loadLibrary("paddle_ocr_jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
            false
        }
    }

    fun init(recModelPath: String, labelPath: String): Boolean {
        if (!libraryLoaded) return false
        if (initialized) return true
        initialized = nativeInit(recModelPath, labelPath)
        return initialized
    }

    fun initDetector(detModelPath: String): Boolean {
        if (!libraryLoaded) return false
        if (detInitialized) return true
        detInitialized = nativeInitDet(detModelPath)
        return detInitialized
    }

    fun recognize(bitmap: Bitmap): String {
        if (!initialized) return ""
        return nativeRecognize(bitmap)
    }

    fun detect(bitmap: Bitmap): IntArray {
        if (!detInitialized) return IntArray(0)
        return nativeDetect(bitmap)
    }

    fun release() {
        if (initialized) {
            nativeRelease()
            initialized = false
            detInitialized = false
        }
    }

    private external fun nativeInit(recModelPath: String, labelPath: String): Boolean
    private external fun nativeInitDet(detModelPath: String): Boolean
    private external fun nativeRecognize(bitmap: Bitmap): String
    private external fun nativeDetect(bitmap: Bitmap): IntArray
    private external fun nativeRelease()
}
