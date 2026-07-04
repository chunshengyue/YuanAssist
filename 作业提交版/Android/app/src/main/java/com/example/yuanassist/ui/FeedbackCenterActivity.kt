package com.example.yuanassist.ui

import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.rememberAsyncImagePainter
import com.example.yuanassist.model.ISSUE_FEEDBACK_STATUS_REPLIED
import com.example.yuanassist.model.issue_feedback
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageBadge
import com.example.yuanassist.ui.subpage.SubpageEmptyState
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.SupabaseTimeFormatter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class FeedbackCenterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeedbackCenterScreen(
                deviceId = currentDeviceId(),
                onBack = ::finish,
                onLoadRecords = { onLoaded, onError, onRequireLogin ->
                    val currentUser = SupabaseRepository.getCurrentUser(this)
                    if (currentUser == null) {
                        onRequireLogin()
                        return@FeedbackCenterScreen
                    }
                    SupabaseRepository.listFeedback(
                        context = this,
                        onSuccess = onLoaded,
                        onError = onError,
                    )
                },
                onSubmit = { description, attachLogs, imageUri, onSuccess, onError ->
                    submitFeedback(
                        description = description,
                        attachLogs = attachLogs,
                        imageUri = imageUri,
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                },
            )
        }
    }

    private fun submitFeedback(
        description: String,
        attachLogs: Boolean,
        imageUri: Uri?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            onError("请先登录后再反馈问题")
            return
        }

        if (description.isBlank()) {
            onError("请先填写问题描述")
            return
        }

        if (imageUri == null) {
            saveFeedback(description, attachLogs, "", onSuccess, onError)
            return
        }

        val cacheFile = uriToCacheFile(imageUri)
        if (cacheFile == null) {
            onError("图片处理失败")
            return
        }

        uploadImageToImageBed(
            file = cacheFile,
            onSuccess = { url ->
                runOnUiThread {
                    cacheFile.delete()
                    saveFeedback(description, attachLogs, url, onSuccess, onError)
                }
            },
            onError = { error ->
                runOnUiThread {
                    cacheFile.delete()
                    onError("图片上传失败：$error")
                }
            },
        )
    }

    private fun saveFeedback(
        description: String,
        attachLogs: Boolean,
        imageUrl: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        SupabaseRepository.createFeedback(
            context = this,
            description = description,
            logContent = if (attachLogs) RunLogger.getAllLogs() else "",
            imageUrls = imageUrl,
            onSuccess = {
                if (isDestroyed || isFinishing) return@createFeedback
                onSuccess()
            },
            onError = { message ->
                if (isDestroyed || isFinishing) return@createFeedback
                onError("提交失败：$message")
            },
        )
    }

    private fun currentDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }

    private fun uriToCacheFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "feedback_${System.currentTimeMillis()}.png")
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadImageToImageBed(
        file: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val uploadUrl = "https://img.scdn.io/api/v1.php"
        val client = okhttp3.OkHttpClient()
        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, fileBody)
            .addFormDataPart("outputFormat", "webp")
            .build()

        val request = okhttp3.Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError("网络请求失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = org.json.JSONObject(responseBody)
                        if (json.optBoolean("success")) {
                            onSuccess(json.optString("url"))
                        } else {
                            onError(json.optString("message", "上传被拒"))
                        }
                    } catch (_: Exception) {
                        onError("JSON解析失败")
                    }
                } else {
                    onError("HTTP ${response.code}")
                }
            }
        })
    }
}

@Composable
private fun FeedbackCenterScreen(
    deviceId: String,
    onBack: () -> Unit,
    onLoadRecords: (
        onLoaded: (List<issue_feedback>) -> Unit,
        onError: (String) -> Unit,
        onRequireLogin: () -> Unit,
    ) -> Unit,
    onSubmit: (
        description: String,
        attachLogs: Boolean,
        imageUri: Uri?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    var description by rememberSaveable { mutableStateOf("") }
    var attachLogs by rememberSaveable { mutableStateOf(true) }
    var selectedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<issue_feedback>>(emptyList()) }

    fun reloadRecords() {
        loading = true
        errorText = null
        onLoadRecords(
            {
                records = it
                loading = false
            },
            {
                errorText = it
                records = emptyList()
                loading = false
            },
            {
                errorText = "请先登录后再反馈问题"
                records = emptyList()
                loading = false
            },
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> selectedImageUri = uri?.toString() },
    )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        reloadRecords()
    }

    val imageUri = selectedImageUri?.let(Uri::parse)

    SubpageScaffold(
        title = "问题反馈",
        subtitle = "提交问题 · 附图说明 · 查看回复",
        onBack = onBack,
        scrollable = false,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SubpageSectionCard(
                    title = "提交反馈",
                    subtitle = "当前设备ID：$deviceId",
                ) {
                    SubpageTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "问题描述",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = attachLogs,
                            onCheckedChange = { attachLogs = it },
                        )
                        Text(
                            text = "附带运行日志",
                            color = TitleInk,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                        )
                    }
                    if (imageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = "反馈预览图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "选择图片",
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { imagePicker.launch("image/*") },
                            ),
                            color = HighlightGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Serif,
                        )
                        if (imageUri != null) {
                            Text(
                                text = "清除图片",
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedImageUri = null },
                                ),
                                color = TitleInk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif,
                            )
                        }
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StoneStyleButton(
                            text = if (submitting) "提交中..." else "提交反馈",
                            onClick = {
                                if (submitting) return@StoneStyleButton
                                submitting = true
                                onSubmit(
                                    description,
                                    attachLogs,
                                    imageUri,
                                    {
                                        submitting = false
                                        description = ""
                                        selectedImageUri = null
                                        Toast.makeText(context, "反馈已提交", Toast.LENGTH_SHORT).show()
                                        reloadRecords()
                                    },
                                    { message ->
                                        submitting = false
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                when {
                    loading -> {
                        SubpageSectionCard(
                            title = "反馈记录",
                            subtitle = "正在加载历史反馈",
                        ) {
                            CircularProgressIndicator(color = HighlightGold)
                        }
                    }

                    errorText != null -> {
                        SubpageEmptyState(
                            title = "反馈记录暂不可用",
                            subtitle = errorText.orEmpty(),
                        )
                    }

                    records.isEmpty() -> {
                        Text(
                            text = "暂无",
                            color = BodyInk,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                        )
                    }

                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            records.forEach { record ->
                                SubpageSectionCard {
                                    FeedbackRecordCard(record = record)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackRecordCard(
    record: issue_feedback,
) {
    val imageUrl = record.imageUrls.substringBefore(',').trim()
    val replied = record.reply.isNotBlank() || record.status == ISSUE_FEEDBACK_STATUS_REPLIED

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = SupabaseTimeFormatter.formatToBeijing(record.createdAt),
                color = BodyInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            SubpageBadge(if (replied) "已回复" else "待处理")
        }
        Text(
            text = record.description.ifBlank { "未填写问题描述" },
            color = TitleInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (record.logContent.isNotBlank()) {
                SubpageBadge("附带日志")
            }
            if (imageUrl.isNotBlank()) {
                SubpageBadge("带图片")
            }
        }
        if (imageUrl.isNotBlank()) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = "反馈图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = "回复：${record.reply.ifBlank { "暂未回复" }}",
            color = BodyInk.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Serif,
        )
    }
}
