package com.example.yuanassist.ui

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.yuanassist.utils.RunLogger
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object JobStationApiModels {
    data class MaaResult<T>(
        @SerializedName(value = "status_code", alternate = ["statusCode"]) val statusCode: Int,
        @SerializedName("message") val message: String?,
        @SerializedName("data") val data: T?
    )

    data class CopilotPageInfo(
        @SerializedName("has_next") val hasNext: Boolean,
        @SerializedName("page") val page: Int,
        @SerializedName("total") val total: Long,
        @SerializedName("data") val data: List<CopilotInfo>
    )

    data class CommentPageInfo(
        @SerializedName("has_next") val hasNext: Boolean,
        @SerializedName("page") val page: Int,
        @SerializedName("total") val total: Long,
        @SerializedName("data") val data: List<JsonObject>?
    )

    data class CopilotInfo(
        @SerializedName("id") val id: Long,
        @SerializedName("upload_time") val uploadTime: String?,
        @SerializedName("uploader_id") val uploaderId: String?,
        @SerializedName("uploader") val uploader: String,
        @SerializedName("stage_id") val stageId: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("cat_one") val catOne: String?,
        @SerializedName("cat_two") val catTwo: String?,
        @SerializedName("cat_three") val catThree: String?,
        @SerializedName("tags") val tags: List<String>?,
        @SerializedName("views") val views: Long,
        @SerializedName("like") val like: Long,
        @SerializedName("content") val content: String,
        @SerializedName("metadata") val metadata: CopilotMetadataInfo?
    )

    data class CopilotMetadataInfo(
        @SerializedName("source_type") val sourceType: String?,
        @SerializedName("repost_author") val repostAuthor: String?,
        @SerializedName("repost_platform") val repostPlatform: String?,
        @SerializedName("repost_url") val repostUrl: String?
    )
}

private interface JobStationApiService {
    @GET("api/copilot/query")
    fun queryCopilots(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("levelKeyword") levelKeyword: String? = null,
        @Query("document") document: String? = null,
        @Query("desc") desc: Boolean = true,
        @Query("orderBy") orderBy: String = "hot"
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>>

    @GET("api/copilot/get/{id}")
    fun getCopilot(
        @Path("id") id: Long
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>>

    @GET("api/comments/query")
    fun queryComments(
        @Query("copilotId") copilotId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("desc") desc: Boolean = true
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>>
}

object JobStationRemoteRepository {

    private const val TAG = "JobStationRemoteRepo"
    private const val BASE_URL = "https://share.maayuan.top/"
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 10L
    private const val WRITE_TIMEOUT_SECONDS = 10L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val api: JobStationApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JobStationApiService::class.java)
    }

    private fun formatElapsedMs(startedAtMs: Long): String {
        return String.format(Locale.US, "%.2fs", (System.currentTimeMillis() - startedAtMs) / 1000f)
    }

    private fun classifyFailure(call: Call<*>, throwable: Throwable): String {
        if (call.isCanceled()) {
            return "请求已取消，通常是页面退出、手动刷新，或新请求覆盖了旧请求"
        }
        return when (throwable) {
            is SocketTimeoutException ->
                "请求超时：MaaYuan 接口在 ${READ_TIMEOUT_SECONDS}s 内没有返回。常见原因是对方服务响应过慢、网络拥堵，或网关卡住"
            is UnknownHostException ->
                "域名解析失败：当前网络无法解析 share.maayuan.top，通常是断网、DNS 异常或网络被拦截"
            is ConnectException ->
                "连接失败：未能连上 MaaYuan 服务，可能是网络断开、目标服务不可达，或连接被系统/代理拦截"
            is SSLException ->
                "TLS/SSL 握手失败：可能是证书校验、代理、抓包环境或系统时间异常导致"
            else ->
                "请求异常：${throwable.javaClass.simpleName}${throwable.message?.let { ": $it" } ?: ""}"
        }
    }

    private fun logRequestStart(scene: String, call: Call<*>) {
        Log.i(TAG, "$scene start url=${call.request().url}")
        RunLogger.i("作业站请求开始: $scene url=${call.request().url}")
    }

    private fun logRequestFailure(scene: String, call: Call<*>, throwable: Throwable, startedAtMs: Long) {
        val url = call.request().url
        val reason = classifyFailure(call, throwable)
        Log.e(
            TAG,
            "$scene failed after ${formatElapsedMs(startedAtMs)} url=$url reason=$reason cause=${throwable.javaClass.simpleName}: ${throwable.message}",
            throwable
        )
        RunLogger.e(
            "作业站请求失败: $scene after=${formatElapsedMs(startedAtMs)} url=$url reason=$reason",
            throwable
        )
    }

    fun loadList(
        page: Int = 1,
        orderBy: String = "hot",
        levelKeyword: String? = null,
        document: String? = null,
        onSuccess: (JobStationApiModels.CopilotPageInfo, List<JobStationAssetRepository.JobStationListItem>) -> Unit,
        onError: (String) -> Unit
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>> {
        val call = api.queryCopilots(
            page = page,
            orderBy = orderBy,
            levelKeyword = levelKeyword?.takeIf { it.isNotBlank() },
            document = document?.takeIf { it.isNotBlank() }
        )
        val startedAtMs = System.currentTimeMillis()
        logRequestStart(
            scene = "loadList(page=$page, orderBy=$orderBy, levelKeyword=${levelKeyword?.takeIf { it.isNotBlank() } ?: "null"})",
            call = call
        )
        call.enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>> {
            override fun onResponse(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>>,
                response: Response<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>>
            ) {
                val body = response.body()
                val data = body?.data?.data
                if (!response.isSuccessful || body == null || body.statusCode != 200 || data == null) {
                    val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    val message = buildString {
                        append("作业站列表加载失败")
                        append(" [http=").append(response.code()).append("]")
                        body?.statusCode?.let { append(" [biz=").append(it).append("]") }
                        body?.message?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                        errorBody?.takeIf { it.isNotBlank() }?.let { append(" ").append(it.take(240)) }
                    }
                    Log.e(
                        TAG,
                        "loadList failed after ${formatElapsedMs(startedAtMs)} url=${call.request().url} http=${response.code()} biz=${body?.statusCode}"
                    )
                    RunLogger.e(
                        "作业站列表加载失败 http=${response.code()} biz=${body?.statusCode} message=${body?.message ?: "empty"}"
                    )
                    onError(message)
                    return
                }

                Log.i(
                    TAG,
                    "loadList success after ${formatElapsedMs(startedAtMs)} url=${call.request().url} count=${data.size} page=${body.data.page} hasNext=${body.data.hasNext}"
                )
                RunLogger.i(
                    "作业站列表加载成功 count=${data.size} page=${body.data.page} hasNext=${body.data.hasNext}"
                )
                onSuccess(body.data, data.map(JobStationAssetRepository::fromRemoteListItem))
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>>,
                t: Throwable
            ) {
                val message = "作业站列表加载失败: ${classifyFailure(call, t)}"
                logRequestFailure("loadList", call, t, startedAtMs)
                onError(message)
            }
        })
        return call
    }

    fun loadDetail(
        copilotId: Long,
        onSuccess: (JobStationAssetRepository.JobStationDetailData) -> Unit,
        onError: (String) -> Unit
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>> {
        val call = api.getCopilot(copilotId)
        val startedAtMs = System.currentTimeMillis()
        logRequestStart("loadDetail(id=$copilotId)", call)
        call.enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>> {
            override fun onResponse(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>>,
                response: Response<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>>
            ) {
                val body = response.body()
                val data = body?.data
                if (!response.isSuccessful || body == null || body.statusCode != 200 || data == null) {
                    val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    val message = buildString {
                        append("作业详情加载失败")
                        append(" [http=").append(response.code()).append("]")
                        body?.statusCode?.let { append(" [biz=").append(it).append("]") }
                        body?.message?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                        errorBody?.takeIf { it.isNotBlank() }?.let { append(" ").append(it.take(240)) }
                    }
                    Log.e(
                        TAG,
                        "loadDetail failed after ${formatElapsedMs(startedAtMs)} url=${call.request().url} id=$copilotId http=${response.code()} biz=${body?.statusCode}"
                    )
                    RunLogger.e(
                        "作业详情加载失败 id=$copilotId http=${response.code()} biz=${body?.statusCode} message=${body?.message ?: "empty"}"
                    )
                    onError(message)
                    return
                }

                Log.i(
                    TAG,
                    "loadDetail success after ${formatElapsedMs(startedAtMs)} url=${call.request().url} id=$copilotId"
                )
                RunLogger.i(
                    "作业详情加载成功 id=$copilotId title=${data.name.orEmpty().ifBlank { "empty" }} uploader=${data.uploader.ifBlank { "empty" }} contentLength=${data.content.length} views=${data.views} likes=${data.like}"
                )
                onSuccess(JobStationAssetRepository.fromRemoteDetailData(data))
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>>,
                t: Throwable
            ) {
                val message = "作业详情加载失败: ${classifyFailure(call, t)}"
                logRequestFailure("loadDetail(id=$copilotId)", call, t, startedAtMs)
                onError(message)
            }
        })
        return call
    }

    fun loadComments(
        copilotId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): Call<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>> {
        val call = api.queryComments(copilotId = copilotId)
        val startedAtMs = System.currentTimeMillis()
        logRequestStart("loadComments(id=$copilotId)", call)
        call.enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>> {
            override fun onResponse(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>>,
                response: Response<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>>
            ) {
                val body = response.body()
                if (!response.isSuccessful || body == null || body.statusCode != 200 || body.data == null) {
                    val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    val message = buildString {
                        append("神秘代码访问失败")
                        append(" [http=").append(response.code()).append("]")
                        body?.statusCode?.let { append(" [biz=").append(it).append("]") }
                        body?.message?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                        errorBody?.takeIf { it.isNotBlank() }?.let { append(" ").append(it.take(240)) }
                    }
                    Log.e(
                        TAG,
                        "loadComments failed after ${formatElapsedMs(startedAtMs)} url=${call.request().url} id=$copilotId http=${response.code()} biz=${body?.statusCode}"
                    )
                    RunLogger.e(
                        "神秘代码访问失败 id=$copilotId http=${response.code()} biz=${body?.statusCode} message=${body?.message ?: "empty"}"
                    )
                    onError(message)
                    return
                }

                Log.i(
                    TAG,
                    "loadComments success after ${formatElapsedMs(startedAtMs)} url=${call.request().url} id=$copilotId"
                )
                RunLogger.i("神秘代码访问成功 id=$copilotId")
                onSuccess()
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>>,
                t: Throwable
            ) {
                val message = "神秘代码访问失败: ${classifyFailure(call, t)}"
                logRequestFailure("loadComments(id=$copilotId)", call, t, startedAtMs)
                onError(message)
            }
        })
        return call
    }
}
