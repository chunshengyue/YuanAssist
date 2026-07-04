package com.example.yuanassist.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yuanassist.R
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun MineTabScreen(
    state: MineProfileState,
    actions: MineTabActions,
    modifier: Modifier = Modifier,
) {
    val mineEntries = listOf(
        MineEntryItem("我的发布", R.drawable.item_achan, actions.onOpenPublished),
        MineEntryItem("我的星石", R.drawable.item_zhangliao, actions.onOpenStone),
        MineEntryItem(
            title = "我的消息",
            iconRes = R.drawable.item_xiahoudun,
            onClick = actions.onOpenMessage,
            badgeCount = state.unreadMessageCount,
        ),
        MineEntryItem("我的收藏", R.drawable.item_xiahouyuan, actions.onOpenFavorite),
        MineEntryItem("排除密探", R.drawable.item_zhangjiao, actions.onOpenExcludedAgents),
    ) + if (state.isFeedbackAdmin) {
        listOf(MineEntryItem("反馈管理", R.drawable.item_zhenmi, actions.onOpenFeedbackAdmin))
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "我的",
            color = TitleInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )

        MineStage(
            state = state,
            actions = actions,
            entries = mineEntries,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class MineEntryItem(
    val title: String,
    @DrawableRes val iconRes: Int,
    val onClick: () -> Unit,
    val badgeCount: Int = 0,
)

private const val ProfileCardAspectRatio = 1441f / 689f

@Composable
private fun MineStage(
    state: MineProfileState,
    actions: MineTabActions,
    entries: List<MineEntryItem>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
    ) {
        val characterWidth = 441.dp
        val characterHeight = 735.dp
        val characterOffsetX = (-11).dp

        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds(),
        ) {
            Image(
                painter = painterResource(id = R.drawable.mine_guanglingwang),
                contentDescription = "广陵王立绘",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .requiredHeight(characterHeight)
                    .requiredWidth(characterWidth)
                    .offset(x = characterOffsetX, y = 50.dp),
            )
        }

        MineProfilePanel(
            state = state,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .requiredWidth(maxWidth + 24.dp)
                .offset(x = 0.dp)
                .aspectRatio(ProfileCardAspectRatio)
        )

        MineEntryRail(
            entries = entries,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(78.dp)
                .padding(top = 70.dp, end = 10.dp),
        )
    }
}

@Composable
private fun MineProfilePanel(
    state: MineProfileState,
    actions: MineTabActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Image(
            painter = painterResource(id = R.drawable.profile_card),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alpha = 0.82f,
            modifier = Modifier
                .matchParentSize()
                .requiredWidth(maxWidth + 48.dp)
                .align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 32.dp, end = 32.dp, top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF7E5C9))
                        .border(1.dp, PaperLine.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = actions.onOpenProfileCenter,
                            ),
                    )
                    if (!state.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.avatarUrl,
                            contentDescription = "头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    } else {
                        Text(
                            text = state.avatarFallback,
                            color = TitleInk,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.nickname,
                        color = TitleInk,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (state.isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = actions.onEditNickname,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "修改昵称",
                                tint = HighlightGold,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                Text(
                    text = state.detail,
                    color = BodyInk.copy(alpha = 0.88f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (!state.isLoggedIn) {
                    MineProfileActionChip(
                        text = "一键登录",
                        onClick = actions.onPrimaryAction,
                    )
                }
                actions.onSyncProfile?.let { sync ->
                    MineProfileActionChip(
                        text = "同步资料",
                        onClick = sync,
                    )
                }
            }
        }
    }
}

@Composable
private fun MineProfileActionChip(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF6E9D3))
            .border(1.dp, GlassStroke.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = HighlightGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
        )
    }
}

@Composable
private fun MineEntryRail(
    entries: List<MineEntryItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry ->
            MineEntryButton(
                title = entry.title,
                iconRes = entry.iconRes,
                onClick = entry.onClick,
                badgeCount = entry.badgeCount,
                modifier = Modifier.width(68.dp),
            )
        }
    }
}

@Composable
private fun MineEntryButton(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    badgeCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Box {
            Text(
                text = title,
                color = TitleInk,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            )
            UnreadBadge(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-6).dp),
            )
        }
    }
}
