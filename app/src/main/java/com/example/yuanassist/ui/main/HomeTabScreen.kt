package com.example.yuanassist.ui.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R
import com.example.yuanassist.ui.main.components.GufengDecorActionButton
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.main.theme.WarmRose

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
                HomeEntryButton("一键日常", R.drawable.item_caiyan, R.drawable.decor_xian, onClick = actions.onOpenOneKeyDaily),
                HomeEntryButton("刷鸟食", R.drawable.item_fenggongjiu, R.drawable.decor_xian, onClick = actions.onOpenBirdFood),
                HomeEntryButton("刷6-24", R.drawable.item_fenggongzhu, R.drawable.decor_xian, onClick = actions.onOpenMainline624),
                HomeEntryButton("无月卡观星", R.drawable.item_chendeng2, R.drawable.decor_que, onClick = actions.onOpenStargazing),
                HomeEntryButton("星石拼图", R.drawable.item_shizimiao, R.drawable.decor_que, onClick = actions.onOpenInventoryStitch),
                HomeEntryButton("修为计算", R.drawable.item_chenji, R.drawable.decor_xian, onClick = actions.onOpenXiuweiCalculator),
                HomeEntryButton("脚本库", R.drawable.item_zhangzhao, R.drawable.decor_que, onClick = actions.onOpenScriptLibrary),
                HomeEntryButton(
                    "云端脚本",
                    R.drawable.item_zhangxiu,
                    R.drawable.decor_yuan,
                    showUnreadDot = overlayState.hasUnreadAdminCloudScript,
                    onClick = actions.onOpenCloudDailyScript,
                ),
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
                HomeEntryButton("脚本录制", R.drawable.item_zhouyu, R.drawable.decor_xian, onClick = actions.onOpenScriptRecorder),
                HomeEntryButton("屏幕选点", R.drawable.item_zhangmiao, R.drawable.decor_butterfly, onClick = actions.onOpenCoordinatePicker),
                HomeEntryButton("框选OCR", R.drawable.item_zhouzhong, R.drawable.decor_xian, onClick = actions.onOpenBoxOcr),
            ),
        )
        FriendLinksSection(actions = actions)
        VersionInfo(
            currentVersionName = overlayState.currentVersionName,
            latestVersionName = overlayState.latestVersionName,
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private data class HomeEntryButton(
    val text: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val decorRes: Int,
    val weight: Float = 1f,
    val showUnreadDot: Boolean = false,
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
                        if (action.showUnreadDot) {
                            Spacer(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-16).dp, y = 12.dp)
                                    .size(9.dp)
                                    .background(Color(0xFFD93A2F), CircleShape),
                            )
                        }
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
private fun FriendLinksSection(actions: HomeTabActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(title = "相关链接")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FriendLinkCard(
                    title = "作者主页",
                    subtitle = "小红书主页-春笙月，反馈问题和提供建议",
                    assetPath = "author_home.png",
                    modifier = Modifier.weight(1f),
                    onClick = actions.onOpenAuthorHomepage,
                )
                FriendLinkCard(
                    title = "maayuan",
                    subtitle = "代号鸢 / 如鸢小助手，解放双手，畅玩无忧",
                    assetPath = "maayuan.png",
                    modifier = Modifier.weight(1f),
                    onClick = actions.onOpenMaaYuanLink,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FriendLinkCard(
                    title = "biubiu",
                    subtitle = "玩代号鸢，用biubiu加速器",
                    assetPath = "biubiu.jpg",
                    modifier = Modifier.weight(1f),
                    onClick = actions.onOpenBiubiuLink,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VersionInfo(
    currentVersionName: String,
    latestVersionName: String?,
) {
    Text(
        text = "当前版本：$currentVersionName · 最新版本：${latestVersionName ?: "获取中"}",
        modifier = Modifier.fillMaxWidth(),
        color = BodyInk.copy(alpha = 0.72f),
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun FriendLinkCard(
    title: String,
    subtitle: String,
    assetPath: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFFD39A72).copy(alpha = 0.12f),
                spotColor = Color(0xFF8F5C40).copy(alpha = 0.05f),
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFBF3),
                        GlassPanel,
                        Color(0xFFF8EBD5),
                    ),
                ),
            )
            .border(
                width = 0.8.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        PaperLine.copy(alpha = 0.58f),
                        WarmRose.copy(alpha = 0.25f),
                        PaperLine.copy(alpha = 0.48f),
                    ),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FriendLinkAssetIcon(
                assetPath = assetPath,
                contentDescription = title,
                fallbackText = title,
            )
            Text(
                text = title,
                color = TitleInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                color = PaperLine,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PaperLine.copy(alpha = 0.08f),
                            PaperLine.copy(alpha = 0.46f),
                            WarmRose.copy(alpha = 0.18f),
                            PaperLine.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Text(
            text = subtitle,
            color = BodyInk.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun FriendLinkAssetIcon(
    assetPath: String,
    contentDescription: String,
    fallbackText: String,
) {
    val context = LocalContext.current
    val imageBitmap = remember(context, assetPath) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFF8EF).copy(alpha = 0.86f))
            .border(0.8.dp, PaperLine.copy(alpha = 0.58f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(27.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = fallbackText,
                color = TitleInk,
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String = "",
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
