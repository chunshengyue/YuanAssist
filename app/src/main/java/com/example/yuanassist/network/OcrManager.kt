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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object OcrManager {

    private const val OCR_URL = "https://1404626659-0xl5hg6b23.ap-nanjing.tencentscf.com/release/ocr"
    private const val API_SECRET = "nobodyknows"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class OcrResult {
        data class Success(val parsedText: String, val strategyUsed: String) : OcrResult()
        data class Error(val message: String, val errorCode: Int = -1) : OcrResult()
    }

    sealed class StoneOcrResult {
        data class Success(
            val words: List<String>,
            val rawEntries: List<String>,
            val strategyUsed: String,
            val wordsResultNum: Int
        ) : StoneOcrResult()

        data class Error(val message: String, val errorCode: Int = -1) : StoneOcrResult()
    }

    private sealed class JsonRequestResult {
        data class Success(val json: JSONObject) : JsonRequestResult()
        data class Error(val message: String, val errorCode: Int = -1) : JsonRequestResult()
    }

    suspend fun recognizeImage(
        bitmap: Bitmap,
        deviceId: String,
        onRetryMsg: () -> Unit,
        onStart: (() -> Unit)? = null,
        onFinish: (() -> Unit)? = null
    ): OcrResult {
        return when (
            val result = requestJson(
                bitmap = bitmap,
                deviceId = deviceId,
                forceMode = "0",
                responseMode = "parsed",
                requestName = "表格OCR",
                onRetryMsg = onRetryMsg,
                onStart = onStart,
                onFinish = onFinish
            )
        ) {
            is JsonRequestResult.Success -> {
                val parsedText = result.json.optString("parsed_text")
                if (parsedText.isEmpty() && !result.json.has("parsed_text")) {
                    OcrResult.Error("服务器响应缺少 parsed_text")
                } else {
                    val strategyUsed = result.json.optString("_strategy_used", "")
                    RunLogger.i(
                        "OCR 识别成功，策略=${strategyUsed.ifEmpty { "默认" }}，文本长度=${parsedText.length}"
                    )
                    OcrResult.Success(parsedText, strategyUsed)
                }
            }

            is JsonRequestResult.Error -> OcrResult.Error(result.message, result.errorCode)
        }
    }

    suspend fun recognizeStoneImage(
        bitmap: Bitmap,
        deviceId: String,
        onRetryMsg: () -> Unit
    ): StoneOcrResult {
        return when (
            val result = requestJson(
                bitmap = bitmap,
                deviceId = deviceId,
                forceMode = "2",
                responseMode = "raw",
                requestName = "星石OCR",
                onRetryMsg = onRetryMsg,
                onStart = null,
                onFinish = null
            )
        ) {
            is JsonRequestResult.Success -> {
                val wordsArray = result.json.optJSONArray("words_result")
                if (wordsArray == null) {
                    StoneOcrResult.Error("服务器响应缺少 words_result")
                } else {
                    val words = wordsArray.toWordList()
                    val rawEntries = wordsArray.toRawEntryList()
                    val strategyUsed = result.json.optString("_strategy_used", "")
                    val wordsResultNum = result.json.optInt("words_result_num", words.size)
                    RunLogger.i(
                        "星石OCR成功，策略=${strategyUsed.ifEmpty { "默认" }}，词数=$wordsResultNum"
                    )
                    StoneOcrResult.Success(words, rawEntries, strategyUsed, wordsResultNum)
                }
            }

            is JsonRequestResult.Error -> StoneOcrResult.Error(result.message, result.errorCode)
        }
    }

    private suspend fun requestJson(
        bitmap: Bitmap,
        deviceId: String,
        forceMode: String,
        responseMode: String,
        requestName: String,
        onRetryMsg: () -> Unit,
        onStart: (() -> Unit)?,
        onFinish: (() -> Unit)?
    ): JsonRequestResult = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStart?.invoke() }
        RunLogger.i("$requestName 开始，尺寸=${bitmap.width}x${bitmap.height}")

        try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            RunLogger.i("$requestName 编码完成，大小=${imageBytes.size / 1024}KB")

            val requestBody = FormBody.Builder()
                .add("image", base64Image)
                .add("force_mode", forceMode)
                .add("response_mode", responseMode)
                .build()

            val request = Request.Builder()
                .url(OCR_URL)
                .post(requestBody)
                .addHeader("X-Device-ID", deviceId)
                .addHeader("X-Api-Secret", API_SECRET)
                .build()

            var retryCount = 0
            val maxRetries = 1

            while (retryCount <= maxRetries) {
                try {
                    RunLogger.i("$requestName 发起请求，第${retryCount + 1}次")
                    val response = client.newCall(request).execute()
                    val responseStr = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        return@withContext JsonRequestResult.Error(
                            message = classifyHttpError(response.code, responseStr),
                            errorCode = response.code
                        )
                    }

                    val jsonResp = try {
                        JSONObject(responseStr)
                    } catch (_: JSONException) {
                        RunLogger.e("$requestName 返回了非 JSON 内容：${responseStr.take(200)}")
                        return@withContext JsonRequestResult.Error("服务器返回了非 JSON 内容")
                    }

                    if (jsonResp.optBoolean("error", false)) {
                        val errCode = jsonResp.optInt("error_code", -1)
                        val suggestion = jsonResp.optString("suggestion", "未知错误")
                        if (errCode == 18 && retryCount < maxRetries) {
                            retryCount++
                            RunLogger.i("$requestName 命中 QPS 限制，准备重试 ${retryCount}/$maxRetries")
                            withContext(Dispatchers.Main) { onRetryMsg() }
                            delay(600)
                            continue
                        }

                        return@withContext JsonRequestResult.Error(
                            message = "$suggestion (代码:$errCode)",
                            errorCode = errCode
                        )
                    }

                    return@withContext JsonRequestResult.Success(jsonResp)
                } catch (e: Exception) {
                    RunLogger.e(
                        "$requestName 请求异常，第${retryCount + 1}次：${e.javaClass.simpleName} - ${e.message}"
                    )
                    if (retryCount >= maxRetries) {
                        throw e
                    }
                    retryCount++
                    delay(600)
                }
            }

            JsonRequestResult.Error("重试次数已达上限")
        } catch (e: Exception) {
            JsonRequestResult.Error(classifyException(e))
        } finally {
            withContext(Dispatchers.Main) { onFinish?.invoke() }
        }
    }

    private fun classifyException(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "连接超时，请检查网络"
            is UnknownHostException -> "无法解析服务器地址，请检查网络"
            is SSLException -> "SSL 连接失败：${e.message}"
            is JSONException -> "服务器响应格式异常：${e.message}"
            else -> "网络请求失败 (${e.javaClass.simpleName})：${e.message}"
        }
    }

    private fun classifyHttpError(code: Int, responseStr: String): String {
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
            401, 403 -> "认证失败 ($code)$detail"
            404 -> "OCR 接口不存在 (404)"
            429 -> "请求频率过高 (429)"
            in 500..599 -> "服务器内部错误 ($code)$detail"
            else -> "HTTP 错误 ($code)$detail"
        }
    }

    private fun JSONArray.toWordList(): List<String> {
        val words = mutableListOf<String>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val word = item.optString("words").trim()
            if (word.isNotEmpty()) {
                words += word
            }
        }
        return words
    }

    private fun JSONArray.toRawEntryList(): List<String> {
        val entries = mutableListOf<String>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            entries += item.toString()
        }
        return entries
    }
}
