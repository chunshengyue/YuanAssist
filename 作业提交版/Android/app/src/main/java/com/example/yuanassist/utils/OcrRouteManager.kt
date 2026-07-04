package com.example.yuanassist.utils

import android.content.Context
import android.util.Log
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.model.OcrConfig

object OcrRouteManager {
    private const val PREF_NAME = "ocr_route_config"
    private const val KEY_ROUTE_VALUE = "ocr_route_value"
    private const val REMOTE_KEY = "ocr_route"
    private const val ROUTE_NANJING = 0
    private const val ROUTE_HONGKONG = 1

    private const val NANJING_URL =
        "https://1404626659-0xl5hg6b23.ap-nanjing.tencentscf.com/release/ocr"
    private const val HONGKONG_URL =
        "https://ocr.yuanassist.space/release/ocr"

    @Volatile
    private var routeValue: Int = ROUTE_NANJING

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        routeValue = normalizeRouteValue(prefs.getInt(KEY_ROUTE_VALUE, ROUTE_NANJING))
        Log.i("OcrRouteManager", "OCR 路由初始化完成 routeValue=$routeValue url=${getOcrUrl()}")
    }

    fun refreshFromRemote(context: Context) {
        SupabaseRepository.getOcrRouteConfig(
            onSuccess = { config: OcrConfig ->
                val nextValue = normalizeRouteValue(config.enabled)
                routeValue = nextValue
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_ROUTE_VALUE, nextValue)
                    .apply()
                Log.i("OcrRouteManager", "OCR 路由远程更新成功 routeValue=$nextValue url=${getOcrUrl()}")
            },
            onError = { message ->
                Log.w("OcrRouteManager", "OCR 路由远程读取失败: $message")
            },
        )
    }

    fun getOcrUrl(): String {
        return when (routeValue) {
            ROUTE_HONGKONG -> HONGKONG_URL
            else -> NANJING_URL
        }
    }

    private fun normalizeRouteValue(value: Int): Int {
        return when (value) {
            ROUTE_HONGKONG -> ROUTE_HONGKONG
            else -> ROUTE_NANJING
        }
    }
}
