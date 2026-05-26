package com.example.yuanassist.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.OcrConfig
import com.example.yuanassist.model.announcement
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.model.issue_feedback
import com.example.yuanassist.model.strategy_comment
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.model.strategy_message
import com.example.yuanassist.model.update
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.lang.reflect.Type

data class FavoriteState(
    val favorited: Boolean = false,
    val favoriteObjectId: String = "",
    val favoriteCount: Int = 0,
)

data class StrategySavePayload(
    val strategyId: String? = null,
    val title: String,
    val content: String,
    val scriptContent: String,
    val config: String,
    val instructions: String,
    val strategyImage: String,
    val agents: String,
    val coverUrl: String,
    val originalPostUrl: String,
    val agentType: Int,
    val agentSelection: String,
    val agentImageUrl: String,
    val agentTextDesc: String,
    val ruyuan: Int? = null,
)

data class DailyScriptUploadTicket(
    val scriptObjectId: String = "",
    val bundlePath: String = "",
    val uploadUrl: String = "",
    val token: String = "",
)

data class DailyScriptDownloadTicket(
    val downloadUrl: String = "",
    val bundlePath: String = "",
)

data class CloudDailyScriptPublishPayload(
    val scriptObjectId: String,
    val title: String,
    val description: String,
    val tags: String,
    val guideImages: List<String>,
    val bundlePath: String,
    val bundleSize: Long,
    val taskCount: Int,
)

data class HomeBadges(
    val unreadMessageCount: Int = 0,
    val adminCloudScriptIds: List<String> = emptyList(),
)

object SupabaseRepository {
    private const val PREFS_SESSION = "supabase_session"
    private const val KEY_CURRENT_USER = "current_user_json"
    private const val PREFS_USER_CACHE = "user_cache"
    private const val OCR_ROUTE_KEY = "ocr_route"
    private const val BASE_URL = "https://ftryfykwzsadgiayquvz.supabase.co/functions/v1/yuanassist-api-v3"

    private val client = OkHttpClient()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun currentDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }

    fun getCurrentUser(context: Context): MyUser? {
        val raw = context.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_USER, null)
            ?: return null
        return runCatching { gson.fromJson(raw, MyUser::class.java) }.getOrNull()
    }

    fun ensureUser(context: Context, onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        getCurrentUser(context)?.takeIf { !it.objectId.isNullOrBlank() }?.let {
            dispatchSuccess(onSuccess, it)
            return
        }
        loginWithDevice(context, onSuccess, onError)
    }

    fun loginWithDevice(context: Context, onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        request<MyUser>(
            action = "bootstrap-user",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = MyUser::class.java,
            onSuccess = { user ->
                cacheCurrentUser(context, user)
                onSuccess(user)
            },
            onError = onError,
        )
    }

    fun refreshCurrentUser(context: Context, onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        loginWithDevice(context, onSuccess, onError)
    }

    fun updateCurrentUser(
        context: Context,
        nickname: String? = null,
        avatarUrl: String? = null,
        onSuccess: (MyUser) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<MyUser>(
            action = "update-user-profile",
            payload = buildMap {
                put("deviceId", currentDeviceId(context))
                if (nickname != null) put("nickname", nickname)
                if (avatarUrl != null) put("avatarUrl", avatarUrl)
            },
            type = MyUser::class.java,
            onSuccess = { user ->
                cacheCurrentUser(context, user)
                onSuccess(user)
            },
            onError = onError,
        )
    }

    fun listPublicStrategies(
        sortMode: String = "newest",
        limit: Int = 200,
        onSuccess: (List<strategy_detail>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<strategy_detail>>(
            action = "list-public-strategies",
            payload = mapOf(
                "sortMode" to if (sortMode == "newest") "newest" else "hot",
                "limit" to limit,
            ),
            type = object : TypeToken<List<strategy_detail>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getStrategyDetail(
        context: Context,
        strategyId: String,
        onSuccess: (strategy_detail) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<strategy_detail>(
            action = "get-strategy-detail",
            payload = buildMap {
                put("strategyId", strategyId)
                put("deviceId", currentDeviceId(context))
            },
            type = strategy_detail::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun incrementStrategyView(strategyId: String, onError: ((String) -> Unit)? = null) {
        request<JsonObject>(
            action = "increment-strategy-view",
            payload = mapOf("strategyId" to strategyId),
            type = JsonObject::class.java,
            onSuccess = {},
            onError = { message -> onError?.invoke(message) },
        )
    }

    fun getFavoriteState(
        context: Context,
        strategyId: String,
        onSuccess: (FavoriteState) -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentUser = getCurrentUser(context)
        if (currentUser == null) {
            dispatchSuccess(onSuccess, FavoriteState())
            return
        }
        request<FavoriteState>(
            action = "get-favorite-state",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "strategyId" to strategyId,
            ),
            type = FavoriteState::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun setFavorite(
        context: Context,
        strategyId: String,
        favorited: Boolean,
        onSuccess: (FavoriteState) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<FavoriteState>(
                    action = "set-favorite",
                    payload = mapOf(
                        "deviceId" to currentDeviceId(context),
                        "strategyId" to strategyId,
                        "favorited" to favorited,
                    ),
                    type = FavoriteState::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun listComments(
        strategyId: String,
        onSuccess: (List<strategy_comment>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<strategy_comment>>(
            action = "list-comments",
            payload = mapOf("strategyId" to strategyId),
            type = object : TypeToken<List<strategy_comment>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun createComment(
        context: Context,
        strategyId: String,
        content: String,
        replyTarget: strategy_comment?,
        onSuccess: (strategy_comment) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<strategy_comment>(
                    action = "create-comment",
                    payload = buildMap {
                        put("deviceId", currentDeviceId(context))
                        put("strategyId", strategyId)
                        put("content", content)
                        if (!replyTarget?.objectId.isNullOrBlank()) {
                            put("replyToCommentId", replyTarget?.objectId)
                        }
                        if (!replyTarget?.user?.objectId.isNullOrBlank()) {
                            put("replyToUserId", replyTarget?.user?.objectId)
                        }
                        if (!replyTarget?.replyToUserName.isNullOrBlank()) {
                            put("replyToUserName", replyTarget?.replyToUserName)
                        } else if (!replyTarget?.user?.nickname.isNullOrBlank()) {
                            put("replyToUserName", replyTarget?.user?.nickname)
                        } else if (!replyTarget?.user?.username.isNullOrBlank()) {
                            put("replyToUserName", replyTarget?.user?.username)
                        }
                    },
                    type = strategy_comment::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun deleteComment(
        context: Context,
        commentId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        request<JsonObject>(
            action = "delete-comment",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "commentId" to commentId,
            ),
            type = JsonObject::class.java,
            onSuccess = { onSuccess() },
            onError = onError,
        )
    }

    fun listMessages(
        context: Context,
        onSuccess: (List<strategy_message>) -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentUser = getCurrentUser(context)
        if (currentUser == null) {
            dispatchSuccess(onSuccess, emptyList())
            return
        }
        request<List<strategy_message>>(
            action = "list-messages",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = object : TypeToken<List<strategy_message>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getHomeBadges(
        context: Context,
        onSuccess: (HomeBadges) -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentUser = getCurrentUser(context)
        if (currentUser == null) {
            dispatchSuccess(onSuccess, HomeBadges())
            return
        }
        request<HomeBadges>(
            action = "get-home-badges",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = HomeBadges::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun markMessagesRead(
        context: Context,
        messageIds: List<String>,
        onComplete: () -> Unit = {},
        onError: ((String) -> Unit)? = null,
    ) {
        if (messageIds.isEmpty()) {
            dispatchSuccess(onComplete, Unit)
            return
        }
        request<JsonObject>(
            action = "mark-messages-read",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "messageIds" to messageIds,
            ),
            type = JsonObject::class.java,
            onSuccess = { onComplete() },
            onError = { message -> onError?.invoke(message) },
        )
    }

    fun listMyFavorites(
        context: Context,
        onSuccess: (List<strategy_detail>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<strategy_detail>>(
            action = "list-my-favorites",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = object : TypeToken<List<strategy_detail>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun listMyPublished(
        context: Context,
        onSuccess: (List<strategy_detail>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<strategy_detail>>(
            action = "list-my-published",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = object : TypeToken<List<strategy_detail>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun deleteStrategy(
        context: Context,
        strategyId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        request<JsonObject>(
            action = "delete-strategy",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "strategyId" to strategyId,
            ),
            type = JsonObject::class.java,
            onSuccess = { onSuccess() },
            onError = onError,
        )
    }

    fun saveStrategy(
        context: Context,
        payload: StrategySavePayload,
        onSuccess: (strategy_detail) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<strategy_detail>(
                    action = "save-strategy",
                    payload = buildMap {
                        put("deviceId", currentDeviceId(context))
                        if (!payload.strategyId.isNullOrBlank()) {
                            put("strategyId", payload.strategyId)
                        }
                        put("title", payload.title)
                        put("content", payload.content)
                        put("scriptContent", payload.scriptContent)
                        put("config", payload.config)
                        put("instructions", payload.instructions)
                        put("strategyImage", payload.strategyImage)
                        put("agents", payload.agents)
                        put("coverUrl", payload.coverUrl)
                        put("originalPostUrl", payload.originalPostUrl)
                        put("agentType", payload.agentType)
                        put("agentSelection", payload.agentSelection)
                        put("agentImageUrl", payload.agentImageUrl)
                        put("agentTextDesc", payload.agentTextDesc)
                        if (payload.ruyuan != null) {
                            put("ruyuan", payload.ruyuan)
                        }
                    },
                    type = strategy_detail::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun createDailyScriptUpload(
        context: Context,
        title: String,
        bundleSize: Long,
        onSuccess: (DailyScriptUploadTicket) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<DailyScriptUploadTicket>(
                    action = "create-daily-script-upload",
                    payload = mapOf(
                        "deviceId" to currentDeviceId(context),
                        "title" to title,
                        "bundleSize" to bundleSize,
                    ),
                    type = DailyScriptUploadTicket::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun publishDailyScript(
        context: Context,
        payload: CloudDailyScriptPublishPayload,
        onSuccess: (cloud_daily_script) -> Unit,
        onError: (String) -> Unit,
    ) {
        ensureUser(
            context = context,
            onSuccess = {
                request<cloud_daily_script>(
                    action = "publish-daily-script",
                    payload = mapOf(
                        "deviceId" to currentDeviceId(context),
                        "scriptObjectId" to payload.scriptObjectId,
                        "title" to payload.title,
                        "description" to payload.description,
                        "tags" to payload.tags,
                        "guideImages" to payload.guideImages,
                        "bundlePath" to payload.bundlePath,
                        "bundleSize" to payload.bundleSize,
                        "taskCount" to payload.taskCount,
                    ),
                    type = cloud_daily_script::class.java,
                    onSuccess = onSuccess,
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun listDailyScripts(
        sortMode: String = "newest",
        keyword: String = "",
        limit: Int = 100,
        onSuccess: (List<cloud_daily_script>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<cloud_daily_script>>(
            action = "list-daily-scripts",
            payload = mapOf(
                "sortMode" to if (sortMode == "hot") "hot" else "newest",
                "keyword" to keyword,
                "limit" to limit,
            ),
            type = object : TypeToken<List<cloud_daily_script>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getDailyScriptDetail(
        scriptId: String,
        onSuccess: (cloud_daily_script) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<cloud_daily_script>(
            action = "get-daily-script-detail",
            payload = mapOf("scriptId" to scriptId),
            type = cloud_daily_script::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun createDailyScriptDownloadUrl(
        scriptId: String,
        onSuccess: (DailyScriptDownloadTicket) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<DailyScriptDownloadTicket>(
            action = "create-daily-script-download-url",
            payload = mapOf("scriptId" to scriptId),
            type = DailyScriptDownloadTicket::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun incrementDailyScriptDownload(scriptId: String, onError: ((String) -> Unit)? = null) {
        request<JsonObject>(
            action = "increment-daily-script-download",
            payload = mapOf("scriptId" to scriptId),
            type = JsonObject::class.java,
            onSuccess = {},
            onError = { message -> onError?.invoke(message) },
        )
    }

    fun listFeedback(
        context: Context,
        onSuccess: (List<issue_feedback>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<issue_feedback>>(
            action = "list-feedback",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = object : TypeToken<List<issue_feedback>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun listFeedbackForAdmin(
        context: Context,
        onSuccess: (List<issue_feedback>) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<List<issue_feedback>>(
            action = "list-feedback-admin",
            payload = mapOf("deviceId" to currentDeviceId(context)),
            type = object : TypeToken<List<issue_feedback>>() {}.type,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun createFeedback(
        context: Context,
        description: String,
        logContent: String,
        imageUrls: String,
        onSuccess: (issue_feedback) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<issue_feedback>(
            action = "create-feedback",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "description" to description,
                "logContent" to logContent,
                "imageUrls" to imageUrls,
                "status" to 0,
            ),
            type = issue_feedback::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun replyFeedbackAsAdmin(
        context: Context,
        feedbackObjectId: String,
        reply: String,
        onSuccess: (issue_feedback) -> Unit,
        onError: (String) -> Unit,
    ) {
        request<issue_feedback>(
            action = "reply-feedback-admin",
            payload = mapOf(
                "deviceId" to currentDeviceId(context),
                "feedbackObjectId" to feedbackObjectId,
                "reply" to reply,
            ),
            type = issue_feedback::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getLatestUpdate(onSuccess: (update) -> Unit, onError: (String) -> Unit) {
        request<update>(
            action = "get-latest-update",
            payload = emptyMap<String, Any?>(),
            type = update::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getLatestAnnouncement(onSuccess: (announcement) -> Unit, onError: (String) -> Unit) {
        request<announcement>(
            action = "get-latest-announcement",
            payload = emptyMap<String, Any?>(),
            type = announcement::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun getOcrRouteConfig(onSuccess: (OcrConfig) -> Unit, onError: (String) -> Unit) {
        request<OcrConfig>(
            action = "get-ocr-config",
            payload = mapOf("key" to OCR_ROUTE_KEY),
            type = OcrConfig::class.java,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun uploadDailyScriptBundle(
        uploadUrl: String,
        zipFile: File,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val body = zipFile.asRequestBody("application/zip".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .header("cache-control", "max-age=3600")
            .header("x-upsert", "false")
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                dispatchError(onError, "脚本包上传失败: ${e.message ?: "网络请求失败"}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        dispatchSuccess(onSuccess, Unit)
                    } else {
                        val raw = it.body?.string().orEmpty()
                        val detail = raw.take(240).ifBlank { "无响应内容" }
                        dispatchError(onError, "脚本包上传失败: HTTP ${it.code} $detail")
                    }
                }
            }
        })
    }

    fun downloadDailyScriptBundle(
        downloadUrl: String,
        targetFile: File,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val request = Request.Builder().url(downloadUrl).get().build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                dispatchError(onError, "脚本包下载失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        dispatchError(onError, "脚本包下载失败: HTTP ${it.code}")
                        return
                    }
                    runCatching {
                        targetFile.parentFile?.mkdirs()
                        val body = it.body ?: error("响应为空")
                        targetFile.outputStream().use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        }
                        targetFile
                    }.onSuccess { file ->
                        dispatchSuccess(onSuccess, file)
                    }.onFailure { error ->
                        dispatchError(onError, error.message ?: "脚本包保存失败")
                    }
                }
            }
        })
    }

    private fun cacheCurrentUser(context: Context, user: MyUser) {
        context.getSharedPreferences(PREFS_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_USER, gson.toJson(user))
            .apply()
        context.getSharedPreferences(PREFS_USER_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putString("nickname", user.nickname)
            .putString("avatarUrl", user.avatarUrl)
            .apply()
    }

    private fun <T> request(
        action: String,
        payload: Map<String, Any?>,
        type: Type,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit,
    ) {
        val bodyJson = gson.toJson(buildMap {
            put("action", action)
            putAll(payload)
        })
        val request = Request.Builder()
            .url(BASE_URL)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                dispatchError(onError, "网络请求失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        dispatchError(onError, parseErrorMessage(raw, "请求失败: HTTP ${it.code}"))
                        return
                    }
                    runCatching {
                        val root = JsonParser.parseString(raw).asJsonObject
                        val success = root.get("success")?.asBoolean ?: false
                        if (!success) {
                            throw IllegalStateException(parseErrorMessage(raw, "请求失败"))
                        }
                        val dataElement = root.get("data")
                        gson.fromJson<T>(dataElement, type)
                    }.onSuccess { data ->
                        dispatchSuccess(onSuccess, data)
                    }.onFailure { error ->
                        dispatchError(onError, error.message ?: "响应解析失败")
                    }
                }
            }
        })
    }

    private fun parseErrorMessage(raw: String, fallback: String): String {
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.get("message")?.asString?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }

    private fun dispatchError(onError: (String) -> Unit, message: String) {
        mainHandler.post { onError(message) }
    }

    private fun dispatchSuccess(onSuccess: () -> Unit, unit: Unit) {
        mainHandler.post { onSuccess() }
    }

    private fun <T> dispatchSuccess(onSuccess: (T) -> Unit, data: T) {
        mainHandler.post { onSuccess(data) }
    }
}
