package com.example.yuanassist.ui

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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

    private val api: JobStationApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JobStationApiService::class.java)
    }

    fun loadList(
        page: Int = 1,
        orderBy: String = "hot",
        levelKeyword: String? = null,
        document: String? = null,
        onSuccess: (JobStationApiModels.CopilotPageInfo, List<JobStationAssetRepository.JobStationListItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        api.queryCopilots(
            page = page,
            orderBy = orderBy,
            levelKeyword = levelKeyword?.takeIf { it.isNotBlank() },
            document = document?.takeIf { it.isNotBlank() }
        ).enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>> {
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
                    Log.e(TAG, "loadList onResponse failed, code=${response.code()}, body=$body, errorBody=$errorBody")
                    onError(message)
                    return
                }

                onSuccess(body.data, data.map(JobStationAssetRepository::fromRemoteListItem))
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotPageInfo>>,
                t: Throwable
            ) {
                val message = "作业站列表加载失败: ${t.javaClass.simpleName}: ${t.message ?: "未知错误"}"
                Log.e(TAG, "loadList onFailure", t)
                onError(message)
            }
        })
    }

    fun loadDetail(
        copilotId: Long,
        onSuccess: (JobStationAssetRepository.JobStationDetailData) -> Unit,
        onError: (String) -> Unit
    ) {
        api.getCopilot(copilotId).enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>> {
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
                    Log.e(TAG, "loadDetail onResponse failed, id=$copilotId, code=${response.code()}, body=$body, errorBody=$errorBody")
                    onError(message)
                    return
                }

                onSuccess(JobStationAssetRepository.fromRemoteDetailData(data))
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CopilotInfo>>,
                t: Throwable
            ) {
                val message = "作业详情加载失败: ${t.javaClass.simpleName}: ${t.message ?: "未知错误"}"
                Log.e(TAG, "loadDetail onFailure, id=$copilotId", t)
                onError(message)
            }
        })
    }

    fun loadComments(
        copilotId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        api.queryComments(copilotId = copilotId).enqueue(object : Callback<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>> {
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
                    Log.e(TAG, "loadComments onResponse failed, id=$copilotId, code=${response.code()}, body=$body, errorBody=$errorBody")
                    onError(message)
                    return
                }

                onSuccess()
            }

            override fun onFailure(
                call: Call<JobStationApiModels.MaaResult<JobStationApiModels.CommentPageInfo>>,
                t: Throwable
            ) {
                val message = "神秘代码访问失败: ${t.javaClass.simpleName}: ${t.message ?: "未知错误"}"
                Log.e(TAG, "loadComments onFailure, id=$copilotId", t)
                onError(message)
            }
        })
    }
}
