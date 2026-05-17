package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.yuanassist.model.STRATEGY_MESSAGE_TYPE_COMMENT_REPLY
import com.example.yuanassist.model.STRATEGY_MESSAGE_TYPE_STRATEGY_COMMENT
import com.example.yuanassist.model.strategy_message
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.subpage.SubpageBadge
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.SupabaseTimeFormatter

class MyMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyMessageScreen(
                onBack = ::finish,
                onOpenStrategy = { item ->
                    val strategyId = item.strategy?.objectId.orEmpty()
                    if (strategyId.isBlank()) {
                        Toast.makeText(this, "这条消息缺少攻略信息", Toast.LENGTH_SHORT).show()
                    } else {
                        startActivity(Intent(this, JobStationActivity::class.java).apply {
                            putExtra(JobStationActivity.EXTRA_STRATEGY_ID, strategyId)
                        })
                    }
                },
                loadMessages = { onLoaded, onError, onRequireLogin ->
                    val currentUser = SupabaseRepository.getCurrentUser(this)
                    if (currentUser == null) {
                        onRequireLogin()
                        return@MyMessageScreen
                    }
                    SupabaseRepository.listMessages(
                        context = this,
                        onSuccess = {
                            val unreadMessages = it.filter { message -> !message.isRead }
                            unreadMessages.forEach { message -> message.isRead = true }
                            SupabaseRepository.markMessagesRead(
                                context = this,
                                messageIds = unreadMessages.mapNotNull { message -> message.objectId },
                            )
                            onLoaded(it)
                        },
                        onError = onError,
                    )
                },
            )
        }
    }
}

@Composable
private fun MyMessageScreen(
    onBack: () -> Unit,
    onOpenStrategy: (strategy_message) -> Unit,
    loadMessages: (
        (List<strategy_message>) -> Unit,
        (String) -> Unit,
        () -> Unit,
    ) -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var items by androidx.compose.runtime.remember { mutableStateOf<List<strategy_message>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        errorText = null
        loadMessages(
            {
                items = it
                loading = false
            },
            {
                errorText = "加载失败：$it"
                loading = false
            },
            {
                errorText = "请先登录后再查看消息"
                loading = false
            },
        )
    }

    SubpageScaffold(
        title = "消息",
        subtitle = "评论提醒 · 回复提醒 · 快速跳转",
        onBack = onBack,
        scrollable = false,
    ) {
        when {
            loading -> {
                SubpageSectionCard(
                    title = "正在加载消息",
                    subtitle = "稍候就能看到评论和回复提醒",
                ) {
                    Text("请稍候…")
                }
            }

            errorText != null -> {
                SubpageSectionCard {
                    Text(
                        text = errorText.orEmpty(),
                        color = com.example.yuanassist.ui.main.theme.BodyInk,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }

            items.isEmpty() -> {
                SubpageSectionCard {
                    Text(
                        text = "暂无消息",
                        color = com.example.yuanassist.ui.main.theme.BodyInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.objectId.orEmpty() + it.createdAt.orEmpty() }) { item ->
                        MessageCard(item = item, onClick = { onOpenStrategy(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    item: strategy_message,
    onClick: () -> Unit,
) {
    val senderName = item.sender?.nickname?.takeIf { it.isNotBlank() }
        ?: item.sender?.username?.takeIf { it.isNotBlank() }
        ?: "热心玩家"
    val title = when (item.type) {
        STRATEGY_MESSAGE_TYPE_STRATEGY_COMMENT -> "$senderName 评论了你的攻略"
        STRATEGY_MESSAGE_TYPE_COMMENT_REPLY -> "$senderName 回复了你的评论"
        else -> "$senderName 给你发来一条消息"
    }

    SubpageSectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalArrangement = Arrangement.spacedBy(8.sp.value.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = com.example.yuanassist.ui.main.theme.TitleInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                )
                if (!item.isRead) {
                    SubpageBadge("未读")
                }
            }
            Text(
                text = item.strategy?.title?.takeIf { it.isNotBlank() } ?: "未命名攻略",
                color = com.example.yuanassist.ui.main.theme.BodyInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = item.contentSnapshot.ifBlank { item.comment?.content?.orEmpty().orEmpty() },
                color = com.example.yuanassist.ui.main.theme.BodyInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = SupabaseTimeFormatter.formatToBeijing(item.createdAt),
                color = com.example.yuanassist.ui.main.theme.QuietInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}
