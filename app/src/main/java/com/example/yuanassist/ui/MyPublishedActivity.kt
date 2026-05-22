package com.example.yuanassist.ui

import android.app.AlertDialog
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.subpage.SubpageBadge
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.SupabaseTimeFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyPublishedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyPublishedScreen(
                onBack = ::finish,
                onOpenStrategy = { item ->
                    val intent = Intent(this, JobStationActivity::class.java)
                    intent.putExtra(JobStationActivity.EXTRA_STRATEGY_ID, item.objectId)
                    startActivity(intent)
                },
                onEditStrategy = { item ->
                    val intent = Intent(this, UploadStrategyActivity::class.java).apply {
                        putExtra(UploadStrategyActivity.EXTRA_IS_EDIT_MODE, true)
                        putExtra(UploadStrategyActivity.EXTRA_STRATEGY_ID, item.objectId)
                    }
                    startActivity(intent)
                },
                onDeleteStrategy = { item, onDeleted ->
                    showDeleteDialog(item, onDeleted)
                },
                loadStrategies = { onLoaded, onError, onRequireLogin ->
                    val currentUser = SupabaseRepository.getCurrentUser(this)
                    if (currentUser == null) {
                        onRequireLogin()
                        return@MyPublishedScreen
                    }

                    SupabaseRepository.listMyPublished(
                        context = this,
                        onSuccess = onLoaded,
                        onError = onError,
                    )
                },
            )
        }
    }

    private fun showDeleteDialog(
        item: strategy_detail,
        onDeleted: () -> Unit,
    ) {
        DialogUtils.showStyledDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle("删除攻略")
                .setMessage("确认删除《${item.title.ifBlank { "未命名攻略" }}》吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ ->
                    deleteStrategy(item, onDeleted)
                },
        )
    }

    private fun deleteStrategy(
        item: strategy_detail,
        onDeleted: () -> Unit,
    ) {
        if (item.objectId.isNullOrBlank()) {
            Toast.makeText(this, "缺少攻略ID，无法删除", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在删除攻略...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.deleteStrategy(
            context = this,
            strategyId = item.objectId.orEmpty(),
            onSuccess = {
                if (isDestroyed || isFinishing) return@deleteStrategy
                Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
                onDeleted()
            },
            onError = { message ->
                if (isDestroyed || isFinishing) return@deleteStrategy
                Toast.makeText(this, "删除失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }
}

@Composable
private fun MyPublishedScreen(
    onBack: () -> Unit,
    onOpenStrategy: (strategy_detail) -> Unit,
    onEditStrategy: (strategy_detail) -> Unit,
    onDeleteStrategy: (strategy_detail, () -> Unit) -> Unit,
    loadStrategies: (
        (List<strategy_detail>) -> Unit,
        (String) -> Unit,
        () -> Unit,
    ) -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<strategy_detail>>(emptyList()) }

    fun reload() {
        loading = true
        errorText = null
        loadStrategies(
            {
                items = it
                loading = false
            },
            {
                errorText = "加载失败：$it"
                loading = false
            },
            {
                errorText = "请先登录后再查看我的发布"
                loading = false
            },
        )
    }

    LaunchedEffect(Unit) {
        reload()
    }

    SubpageScaffold(
        title = "我的发布",
        subtitle = "查看攻略 · 继续编辑 · 删除整理",
        onBack = onBack,
        scrollable = false,
    ) {
        when {
            loading -> {
                SubpageSectionCard(
                    title = "正在加载我的发布",
                    subtitle = "稍候就能看到你已经发布过的攻略",
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
                        text = "暂无发布",
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
                    items(items, key = { it.objectId.orEmpty() + it.title }) { item ->
                        PublishedStrategyCard(
                            item = item,
                            onOpen = { onOpenStrategy(item) },
                            onEdit = { onEditStrategy(item) },
                            onDelete = {
                                onDeleteStrategy(item) {
                                    reload()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishedStrategyCard(
    item: strategy_detail,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SubpageSectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpen,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = item.agentImageUrl?.takeIf { it.isNotBlank() } ?: item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp),
            )
            Text(
                text = item.title.ifBlank { "未命名攻略" },
                color = com.example.yuanassist.ui.main.theme.TitleInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = "最近更新：${SupabaseTimeFormatter.formatToBeijing(item.updatedAt, "暂无")}",
                color = com.example.yuanassist.ui.main.theme.QuietInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = buildPublishedAgentsText(item),
                color = com.example.yuanassist.ui.main.theme.BodyInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubpageBadge("查看详情")
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(
                        text = "编辑",
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEdit,
                        ),
                        color = com.example.yuanassist.ui.main.theme.HighlightGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = "删除",
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDelete,
                        ),
                        color = androidx.compose.ui.graphics.Color(0xFFB84D4D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
        }
    }
}

private fun buildPublishedAgentsText(item: strategy_detail): String {
    val rawAgentsText = if (!item.agentSelection.isNullOrEmpty()) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            val agentsRawList: List<String> = Gson().fromJson(item.agentSelection, type)
            agentsRawList.joinToString("、")
        } catch (_: Exception) {
            item.agentSelection ?: ""
        }
    } else {
        item.agents ?: ""
    }

    val parsedNames = rawAgentsText.split("、", "，", ",").mapNotNull { raw ->
        val trimRaw = raw.trim()
        if (trimRaw.isBlank()) return@mapNotNull null
        trimRaw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
    }

    return parsedNames.joinToString(" ").ifEmpty { "未配置阵容" }
}
