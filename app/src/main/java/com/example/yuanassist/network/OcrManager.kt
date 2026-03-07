// 檔案路徑：yuanassist/network/OcrManager.kt
package com.example.yuanassist.network

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object OcrManager {
    // 獨立的 OkHttpClient，可重複使用
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 定義 OCR 辨識結果的三種狀態
    sealed class OcrResult {
        data class Success(val parsedText: String, val strategyUsed: String) : OcrResult()
        data class Error(val message: String, val errorCode: Int = -1) : OcrResult()
    }

    /**
     * 掛起函數 (suspend)：在背景執行緒處理所有耗時操作
     */
    suspend fun recognizeImage(
        bitmap: Bitmap,
        deviceId: String,
        onRetryMsg: () -> Unit
    ): OcrResult = withContext(Dispatchers.IO) {
        try {
            // 1. 圖片壓縮與 Base64 編碼
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val requestBody = FormBody.Builder()
                .add("image", base64Image)
                .add("force_mode", "0")
                .build()

            val request = Request.Builder()
                .url("unknown") // TODO: 替換為你的雲端 API
                .post(requestBody)
                .addHeader("X-Device-ID", deviceId)
                .addHeader("X-Api-Secret", "unknown") // TODO: 替換為你的 API 密鑰
                .build()

            // 2. 自動重試機制
            var retryCount = 0
            val maxRetries = 1

            while (retryCount <= maxRetries) {
                try {
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val jsonResp = JSONObject(responseStr)

                        // 業務邏輯錯誤判斷
                        if (jsonResp.optBoolean("error", false)) {
                            val errCode = jsonResp.optInt("error_code", -1)
                            val suggestion = jsonResp.optString("suggestion", "未知錯誤")

                            // 捕捉到 QPS 限制 (18)，觸發自動重試
                            if (errCode == 18 && retryCount < maxRetries) {
                                retryCount++
                                withContext(Dispatchers.Main) { onRetryMsg() } // 切回主執行緒彈 Toast
                                delay(600) // 協程無阻塞延遲
                                continue
                            } else {
                                return@withContext OcrResult.Error(
                                    "$suggestion (代碼:$errCode)",
                                    errCode
                                )
                            }
                        }
                        // 成功解析
                        else if (jsonResp.has("parsed_text")) {
                            val parsedText = jsonResp.getString("parsed_text")
                            val strategyUsed = jsonResp.optString("_strategy_used", "")
                            return@withContext OcrResult.Success(parsedText, strategyUsed)
                        }
                    } else {
                        return@withContext OcrResult.Error("服务器错误 (${response.code}): $responseStr")
                    }
                } catch (e: Exception) {
                    if (retryCount >= maxRetries) throw e
                    retryCount++
                    delay(600)
                }
            }
            return@withContext OcrResult.Error("重试次数达上限，请稍后再试")

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext OcrResult.Error("网络请求失败: ${e.message}")
        }
    }
}