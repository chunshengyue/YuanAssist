// 檔案路徑：yuanassist/network/OcrManager.kt
package com.example.yuanassist.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.yuanassist.utils.RunLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

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

    /** 将异常分类为用户可读的错误描述 */
    private fun classifyException(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "连接超时，请检查网络状况"
            is UnknownHostException -> "无法解析服务器地址，请检查网络连接"
            is SSLException -> "SSL安全连接失败: ${e.message}"
            is JSONException -> "服务器响应格式异常: ${e.message}"
            else -> "网络请求失败 (${e.javaClass.simpleName}): ${e.message}"
        }
    }

    /** 将 HTTP 状态码分类为用户可读的错误描述 */
    private fun classifyHttpError(code: Int, responseStr: String): String {
        // 尝试从响应体解析 JSON 错误信息
        val serverMsg = try {
            val json = JSONObject(responseStr)
            json.optString("suggestion", "")
                .ifEmpty { json.optString("message", "") }
                .ifEmpty { json.optString("error", "") }
        } catch (_: JSONException) {
            ""
        }
        val detail = if (serverMsg.isNotEmpty()) " - $serverMsg" else ""

        return when (code) {
            400 -> "请求参数错误 (400)$detail"
            401, 403 -> "认证失败 ($code)，请检查API密钥$detail"
            404 -> "OCR接口不存在 (404)，请检查服务器地址"
            429 -> "请求频率过高 (429)，请稍后再试"
            in 500..599 -> "服务器内部错误 ($code)$detail"
            else -> "HTTP错误 ($code)$detail"
        }
    }

    /**
     * 掛起函數 (suspend)：在背景執行緒處理所有耗時操作
     */
    suspend fun recognizeImage(
        bitmap: Bitmap,
        deviceId: String,
        onRetryMsg: () -> Unit,
        onStart: (() -> Unit)? = null,
        onFinish: (() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStart?.invoke() }
        RunLogger.i("OCR 开始识别，图片尺寸: ${bitmap.width}x${bitmap.height}")

        try {
            // 1. 圖片壓縮與 Base64 編碼
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            RunLogger.i("OCR 图片编码完成，大小: ${imageBytes.size / 1024}KB")

            val requestBody = FormBody.Builder()
                .add("image", base64Image)
                .add("force_mode", "0")
                .build()

            val request = Request.Builder()
                .url("https://1404626659-0xl5hg6b23.ap-nanjing.tencentscf.com/release/ocr") // TODO: 替換為你的雲端 API
                .post(requestBody)
                .addHeader("X-Device-ID", deviceId)
                .addHeader("X-Api-Secret", "nobodyknows") // TODO: 替換為你的 API 密鑰
                .build()

            // 2. 自動重試機制
            var retryCount = 0
            val maxRetries = 1

            while (retryCount <= maxRetries) {
                try {
                    RunLogger.i("OCR 发送请求 (第${retryCount + 1}次)")
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    RunLogger.i("OCR 收到响应: HTTP ${response.code}, body长度: ${responseStr.length}")

                    if (response.isSuccessful) {
                        val jsonResp = try {
                            JSONObject(responseStr)
                        } catch (e: JSONException) {
                            val msg = "服务器返回了非JSON内容"
                            RunLogger.e("OCR $msg: ${responseStr.take(200)}")
                            val errorResult = OcrResult.Error(msg)
                            withContext(Dispatchers.Main) { onFinish?.invoke() }
                            return@withContext errorResult
                        }

                        // 業務邏輯錯誤判斷
                        if (jsonResp.optBoolean("error", false)) {
                            val errCode = jsonResp.optInt("error_code", -1)
                            val suggestion = jsonResp.optString("suggestion", "未知錯誤")

                            // 捕捉到 QPS 限制 (18)，觸發自動重試
                            if (errCode == 18 && retryCount < maxRetries) {
                                retryCount++
                                RunLogger.i("OCR 遇到QPS限制 (代码:18)，${retryCount}/${maxRetries} 次重试...")
                                withContext(Dispatchers.Main) { onRetryMsg() } // 切回主執行緒彈 Toast
                                delay(600) // 協程無阻塞延遲
                                continue
                            } else {
                                val msg = "$suggestion (代碼:$errCode)"
                                RunLogger.e("OCR 业务错误: $msg")
                                val errorResult = OcrResult.Error(msg, errCode)
                                withContext(Dispatchers.Main) { onFinish?.invoke() }
                                return@withContext errorResult
                            }
                        }
                        // 成功解析
                        else if (jsonResp.has("parsed_text")) {
                            val parsedText = jsonResp.getString("parsed_text")
                            val strategyUsed = jsonResp.optString("_strategy_used", "")
                            RunLogger.i("OCR 识别成功，策略: ${strategyUsed.ifEmpty { "默认" }}，结果长度: ${parsedText.length}")
                            val successResult = OcrResult.Success(parsedText, strategyUsed)
                            withContext(Dispatchers.Main) { onFinish?.invoke() }
                            return@withContext successResult
                        }
                        // 响应200但缺少 parsed_text 字段
                        else {
                            val msg = "服务器响应缺少识别结果 (parsed_text)"
                            RunLogger.e("OCR $msg，响应内容: ${responseStr.take(300)}")
                            val errorResult = OcrResult.Error(msg)
                            withContext(Dispatchers.Main) { onFinish?.invoke() }
                            return@withContext errorResult
                        }
                    } else {
                        val msg = classifyHttpError(response.code, responseStr)
                        RunLogger.e("OCR HTTP错误: $msg")
                        val errorResult = OcrResult.Error(msg)
                        withContext(Dispatchers.Main) { onFinish?.invoke() }
                        return@withContext errorResult
                    }
                } catch (e: Exception) {
                     RunLogger.e("OCR 请求异常 (第${retryCount + 1}次): ${e.javaClass.simpleName} - ${e.message}")
                    if (retryCount >= maxRetries) throw e
                    retryCount++
                    RunLogger.i("OCR 等待600ms后重试...")
                    delay(600)
                }
            }
                    val msg = "重试次数达上限 ($maxRetries)，请稍后再试"
            RunLogger.e("OCR $msg")
            val errorResult = OcrResult.Error(msg)
            withContext(Dispatchers.Main) { onFinish?.invoke() }
            return@withContext errorResult

        } catch (e: Exception) {
            val msg = classifyException(e)
            RunLogger.e("OCR 最终失败: $msg")
            val errorResult = OcrResult.Error(msg)
            withContext(Dispatchers.Main) { onFinish?.invoke() }
            return@withContext errorResult
        }
    }
}
