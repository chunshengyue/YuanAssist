package com.example.yuanassist.utils

import android.util.Log

object OcrRouteManager {
    private const val OCR_URL =
        "https://ocr.yuanassist.space/release/ocr"

    fun initialize() {
        Log.i("OcrRouteManager", "OCR 路由初始化完成 url=$OCR_URL")
    }

    fun getOcrUrl(): String {
        return OCR_URL
    }
}
