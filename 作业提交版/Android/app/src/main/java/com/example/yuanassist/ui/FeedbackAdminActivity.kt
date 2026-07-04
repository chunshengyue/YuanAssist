package com.example.yuanassist.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.example.yuanassist.utils.firstFeedbackImageUrl
import com.example.yuanassist.utils.isFeedbackAdminDevice
import com.example.yuanassist.utils.SupabaseTimeFormatter

class FeedbackAdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = SupabaseRepository.currentDeviceId(this)
        if (!isFeedbackAdminDevice(deviceId)) {
            Toast.makeText(this, "当前设备无反馈管理权限", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FeedbackAdminScreen(
                deviceId = deviceId,
                onBack = ::finish,
                onLoadRecords = { onLoaded, onError ->
                    SupabaseRepository.listFeedbackForAdmin(
                        context = this,
                        onSuccess = onLoaded,
                        onError = onError,
                    )
                },
                onReply = { feedbackObjectId, reply, onSuccess, onError ->
                    SupabaseRepository.replyFeedbackAsAdmin(
                        context = this,
                        feedbackObjectId = feedbackObjectId,
                        reply = reply,
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                },
            )
        }
    }
}

@Composable
private fun FeedbackAdminScreen(
    deviceId: String,
    onBack: () -> Unit,
    onLoadRecords: (
        onLoaded: (List<issue_feedback>) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    onReply: (
        feedbackObjectId: String,
        reply: String,
        onSuccess: (issue_feedback) -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    var loading by rememberSaveable { mutableStateOf(true) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<issue_feedback>>(emptyList()) }
    var replyDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submittingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun updateRecord(updated: issue_feedback) {
        val targetId = updated.objectId.orEmpty()
        records = records.map { record ->
            if (record.objectId.orEmpty() == targetId) updated else record
        }
    }

    fun reload() {
        loading = true
        errorText = null
        onLoadRecords(
            { items ->
                records = items
                replyDrafts = items.associate { item ->
                    item.objectId.orEmpty() to item.reply
                }
                loading = false
            },
            { message ->
                errorText = message
                loading = false
            },
        )
    }

    LaunchedEffect(Unit) {
        reload()
    }

    SubpageScaffold(
        title = "反馈管理",
        subtitle = "全表阅览 · 官方回复",
        onBack = onBack,
        scrollable = false,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SubpageSectionCard(
                    title = "管理员设备",
                    subtitle = deviceId,
                ) {
                    Text(
                        text = "仅当前设备可查看全部反馈并写入官方回复。",
                        color = BodyInk,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }

            when {
                loading -> item {
                    SubpageSectionCard(
                        title = "反馈记录",
                        subtitle = "正在加载全表反馈",
                    ) {
                        CircularProgressIndicator(color = HighlightGold)
                    }
                }

                errorText != null -> item {
                    SubpageEmptyState(
                        title = "反馈记录暂不可用",
                        subtitle = errorText.orEmpty(),
                    )
                }

                records.isEmpty() -> item {
                    SubpageEmptyState(
                        title = "暂无反馈记录",
                        subtitle = "目前 issue_feedback 表中没有可展示的数据。",
                    )
                }

                else -> items(
                    items = records,
                    key = { it.objectId.orEmpty() },
                ) { record ->
                    val feedbackObjectId = record.objectId.orEmpty()
                    val draft = replyDrafts[feedbackObjectId] ?: record.reply
                    val submitting = feedbackObjectId in submittingIds
                    SubpageSectionCard {
                        FeedbackAdminRecordCard(
                            record = record,
                            draft = draft,
                            submitting = submitting,
                            onDraftChange = { value ->
                                replyDrafts = replyDrafts.toMutableMap().apply {
                                    put(feedbackObjectId, value)
                                }
                            },
                            onSubmit = {
                                if (feedbackObjectId.isBlank()) {
                                    Toast.makeText(context, "反馈记录缺少 objectId", Toast.LENGTH_SHORT).show()
                                    return@FeedbackAdminRecordCard
                                }
                                if (draft.isBlank()) {
                                    Toast.makeText(context, "请先填写官方回复", Toast.LENGTH_SHORT).show()
                                    return@FeedbackAdminRecordCard
                                }
                                if (submitting) return@FeedbackAdminRecordCard
                                submittingIds = submittingIds + feedbackObjectId
                                onReply(
                                    feedbackObjectId,
                                    draft,
                                    { updated ->
                                        submittingIds = submittingIds - feedbackObjectId
                                        replyDrafts = replyDrafts.toMutableMap().apply {
                                            put(feedbackObjectId, updated.reply)
                                        }
                                        updateRecord(updated)
                                        Toast.makeText(context, "回复已提交", Toast.LENGTH_SHORT).show()
                                    },
                                    { message ->
                                        submittingIds = submittingIds - feedbackObjectId
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackAdminRecordCard(
    record: issue_feedback,
    draft: String,
    submitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val imageUrl = firstFeedbackImageUrl(record.imageUrls)
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
            SubpageBadge(if (replied) "已回复" else "待回复")
        }
        Text(
            text = "设备ID：${record.deviceId}",
            color = BodyInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
        )
        record.user?.nickname?.takeIf { it.isNotBlank() }?.let { nickname ->
            Text(
                text = "用户：$nickname",
                color = BodyInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
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
            text = "当前回复：${record.reply.ifBlank { "暂未回复" }}",
            color = BodyInk.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Serif,
        )
        SubpageTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = "官方回复",
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )
        StoneStyleButton(
            text = if (submitting) "提交中..." else "提交回复",
            onClick = onSubmit,
            enabled = !submitting,
        )
    }
}
