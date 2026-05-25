package com.example.yuanassist.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R
import com.example.yuanassist.ui.main.components.GufengDecorActionButton
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun HomeTabScreen(
    overlayState: HomeOverlayState,
    actions: HomeTabActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeTitle()
        ModeSection(
            title = "战斗版",
            subtitle = "跟打运行 · 参数配置",
            actions = listOf(
                HomeEntryButton(
                    text = overlayState.combatButtonLabel,
                    iconRes = R.drawable.item_lvbu,
                    decorRes = R.drawable.decor_butterfly,
                    weight = 1.18f,
                    onClick = actions.onToggleCombat,
                ),
                HomeEntryButton(
                    text = "设置",
                    iconRes = R.drawable.item_liuzhang,
                    decorRes = R.drawable.decor_xian,
                    weight = 0.82f,
                    onClick = actions.onOpenSettings,
                ),
            ),
        )
        ModeSection(
            title = "日常版",
            subtitle = "一键运行 · 解放双手",
            actions = listOf(
                HomeEntryButton("刷鸟食", R.drawable.item_fenggongjiu, R.drawable.decor_xian, onClick = actions.onOpenBirdFood),
                HomeEntryButton("刷6-24", R.drawable.item_fenggongzhu, R.drawable.decor_xian, onClick = actions.onOpenMainline624),
                HomeEntryButton("无月卡观星", R.drawable.item_chendeng2, R.drawable.decor_que, onClick = actions.onOpenStargazing),
                HomeEntryButton("星石拼图", R.drawable.item_shizimiao, R.drawable.decor_que, onClick = actions.onOpenInventoryStitch),
                HomeEntryButton("披荆斩棘", R.drawable.item_chenji, R.drawable.decor_que, onClick = actions.onOpenPiJingZhanJi),
                HomeEntryButton("哀牢15min", R.drawable.item_caiyan, R.drawable.decor_que, onClick = actions.onOpenAilao15Min),
                HomeEntryButton("脚本录制", R.drawable.item_zhouyu, R.drawable.decor_xian, onClick = actions.onOpenScriptRecorder),
                HomeEntryButton("脚本库", R.drawable.item_zhangzhao, R.drawable.decor_que, onClick = actions.onOpenScriptLibrary),
                HomeEntryButton("云端脚本", R.drawable.item_zhanghe, R.drawable.decor_yuan, onClick = actions.onOpenCloudDailyScript),
            ),
        )
        ModeSection(
            title = "常用入口",
            subtitle = "日志 · 调试 · 问答 · 反馈 · 更新",
            actions = listOf(
                HomeEntryButton("运行日志", R.drawable.item_zhanghe, R.drawable.decor_yuan, onClick = actions.onOpenRunLog),
                HomeEntryButton("调试", R.drawable.item_linghumao, R.drawable.decor_yuan, onClick = actions.onOpenDebugTab),
                HomeEntryButton("常见问题", R.drawable.item_xunyu, R.drawable.decor_xian, onClick = actions.onOpenFaq),
                HomeEntryButton("问题反馈", R.drawable.item_xunyou, R.drawable.decor_butterfly, onClick = actions.onOpenFeedback),
                HomeEntryButton("检查更新", R.drawable.item_chendeng, R.drawable.decor_que, onClick = actions.onCheckUpdate),
                HomeEntryButton("屏幕选点", R.drawable.item_zhangmiao, R.drawable.decor_butterfly, onClick = actions.onOpenCoordinatePicker),
                HomeEntryButton("框选OCR", R.drawable.item_zhouzhong, R.drawable.decor_xian, onClick = actions.onOpenBoxOcr),
            ),
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private data class HomeEntryButton(
    val text: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val decorRes: Int,
    val weight: Float = 1f,
    val onClick: () -> Unit,
)

@Composable
private fun ModeSection(
    title: String,
    subtitle: String,
    actions: List<HomeEntryButton>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(title = title, subtitle = subtitle)
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowActions.forEach { action ->
                    Box(
                        modifier = Modifier
                            .weight(action.weight)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = action.onClick,
                            ),
                    ) {
                        GufengDecorActionButton(
                            text = action.text,
                            itemRes = action.iconRes,
                            decorRes = action.decorRes,
                        )
                    }
                }
                if (rowActions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        androidx.compose.material3.Text(
            text = title,
            color = TitleInk,
            fontSize = 21.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            letterSpacing = 0.sp,
        )
        androidx.compose.material3.Text(
            text = "  $subtitle",
            color = BodyInk.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            letterSpacing = 0.sp,
        )
    }
}
