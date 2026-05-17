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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yuanassist.R
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.subpage.SubpageBadge
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyFavoriteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyFavoriteScreen(
                onBack = ::finish,
                onOpenStrategy = { item ->
                    startActivity(Intent(this, JobStationActivity::class.java).apply {
                        putExtra(JobStationActivity.EXTRA_STRATEGY_ID, item.objectId)
                    })
                },
                loadFavorites = { onLoaded, onError, onRequireLogin ->
                    val currentUser = SupabaseRepository.getCurrentUser(this)
                    if (currentUser == null) {
                        onRequireLogin()
                        return@MyFavoriteScreen
                    }
                    SupabaseRepository.listMyFavorites(
                        context = this,
                        onSuccess = { onLoaded(it.distinctBy { strategy -> strategy.objectId }) },
                        onError = onError,
                    )
                },
            )
        }
    }
}

@Composable
private fun MyFavoriteScreen(
    onBack: () -> Unit,
    onOpenStrategy: (strategy_detail) -> Unit,
    loadFavorites: (
        (List<strategy_detail>) -> Unit,
        (String) -> Unit,
        () -> Unit,
    ) -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var items by androidx.compose.runtime.remember { mutableStateOf<List<strategy_detail>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        errorText = null
        loadFavorites(
            {
                items = it
                loading = false
            },
            {
                errorText = "加载失败：$it"
                loading = false
            },
            {
                errorText = "请先登录后再查看我的收藏"
                loading = false
            },
        )
    }

    SubpageScaffold(
        title = "我的收藏",
        subtitle = "收藏攻略 · 快速回看 · 继续参考",
        onBack = onBack,
        scrollable = false,
    ) {
        when {
            loading -> {
                SubpageSectionCard(
                    title = "正在加载我的收藏",
                    subtitle = "稍候就能看到你收藏过的攻略",
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
                        text = "暂无收藏",
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
                        FavoriteStrategyCard(
                            item = item,
                            onClick = { onOpenStrategy(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteStrategyCard(
    item: strategy_detail,
    onClick: () -> Unit,
) {
    SubpageSectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = item.agentImageUrl?.takeIf { it.isNotBlank() } ?: item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            )
            Text(
                text = item.title.ifBlank { "未命名攻略" },
                color = com.example.yuanassist.ui.main.theme.TitleInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = buildStrategyAgentsText(item),
                color = com.example.yuanassist.ui.main.theme.BodyInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
            SubpageBadge("查看详情")
        }
    }
}

private fun buildStrategyAgentsText(item: strategy_detail): String {
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
