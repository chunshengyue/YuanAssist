package com.example.yuanassist.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.yuanassist.R
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.MainShellTheme
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.main.theme.WarmRose

@Composable
fun MainShellScreen(
    selectedTab: MainTab,
    homeOverlayState: HomeOverlayState,
    mineProfileState: MineProfileState,
    debugWorkbenchState: DebugWorkbenchState,
    homeActions: HomeTabActions,
    jobActions: JobTabActions,
    debugActions: DebugTabActions,
    mineActions: MineTabActions,
    onSelectTab: (MainTab) -> Unit,
) {
    MainShellTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.background_stretch_9x21),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.FillWidth,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp, bottom = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (selectedTab) {
                    MainTab.HOME -> HomeTabScreen(
                        overlayState = homeOverlayState,
                        actions = homeActions,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .weight(1f),
                    )

                    MainTab.JOB -> JobTabScreen(
                        actions = jobActions,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .weight(1f),
                    )

                    MainTab.DEBUG -> DebugTabScreen(
                        state = debugWorkbenchState,
                        actions = debugActions,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .weight(1f),
                    )

                    MainTab.MINE -> MineTabScreen(
                        state = mineProfileState,
                        actions = mineActions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            BottomActionBar(
                items = listOf(
                    BottomNavItem("首页", R.drawable.decor_flower1),
                    BottomNavItem("作业", R.drawable.decor_flower2),
                    BottomNavItem("调试", R.drawable.decor_flower3),
                    BottomNavItem("我的", R.drawable.decor_flower4),
                ),
                selectedTab = selectedTab,
                onSelected = onSelectTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .zIndex(10f)
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 460.dp),
            )
        }
    }
}

@Composable
internal fun HomeTitle() {
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .drawWithCache {
                onDrawBehind {
                    val centerX = size.width / 2f
                    drawLine(
                        color = PaperLine.copy(alpha = 0.35f),
                        start = Offset(centerX - 78.dp.toPx(), size.height - 2.dp.toPx()),
                        end = Offset(centerX + 78.dp.toPx(), size.height - 2.dp.toPx()),
                        strokeWidth = 0.7.dp.toPx(),
                    )
                    drawCircle(
                        color = WarmRose.copy(alpha = 0.28f),
                        radius = 2.4.dp.toPx(),
                        center = Offset(centerX - 88.dp.toPx(), size.height - 2.dp.toPx()),
                    )
                    drawCircle(
                        color = PaperLine.copy(alpha = 0.30f),
                        radius = 2.1.dp.toPx(),
                        center = Offset(centerX + 88.dp.toPx(), size.height - 2.dp.toPx()),
                    )
                }
            }
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "YuanAssist",
            color = TitleInk,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
        )
        Text(
            text = "如鸢/代号鸢综合小助手",
            color = BodyInk.copy(alpha = 0.78f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
internal fun SectionPageTitle(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            color = TitleInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
        )
        Text(
            text = subtitle,
            color = BodyInk.copy(alpha = 0.78f),
            fontSize = 14.sp,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

private data class BottomNavItem(
    val title: String,
    @DrawableRes val iconRes: Int,
)

@Composable
private fun BottomActionBar(
    items: List<BottomNavItem>,
    selectedTab: MainTab,
    onSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .drawWithCache {
                val shadowHeight = 9.dp.toPx()
                onDrawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF9D6A45).copy(alpha = 0f),
                                Color(0xFF9D6A45).copy(alpha = 0.055f),
                                Color(0xFF9D6A45).copy(alpha = 0f),
                            ),
                        ),
                        topLeft = Offset(0f, 0f),
                        size = UiSize(size.width, shadowHeight),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.34f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.55.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0xFFD8B875).copy(alpha = 0.30f),
                        start = Offset(0f, 1.2.dp.toPx()),
                        end = Offset(size.width, 1.2.dp.toPx()),
                        strokeWidth = 0.7.dp.toPx(),
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            val tab = MainTab.values()[index]
            BottomNavButton(
                item = item,
                selected = tab == selectedTab,
                onClick = { onSelected(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            )
        }
    }
}

@Composable
private fun BottomNavButton(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .requiredSize(width = 72.dp, height = 62.dp)
                    .drawWithCache {
                        val radius = 33.dp.toPx()
                        val center = Offset(size.width / 2f, 34.dp.toPx())
                        onDrawBehind {
                            drawCircle(
                                color = Color(0xFFC9A86A).copy(alpha = 0.08f),
                                radius = radius,
                                center = center.copy(y = center.y + 1.dp.toPx()),
                            )
                            drawCircle(
                                color = Color(0xFFFFFEFA),
                                radius = radius - 1.dp.toPx(),
                                center = center,
                            )
                            drawCircle(
                                color = Color(0xFFE6C98E).copy(alpha = 0.82f),
                                radius = radius - 1.2.dp.toPx(),
                                center = center,
                                style = Stroke(width = 0.8.dp.toPx()),
                            )
                        }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .offset(y = 2.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = item.title,
                        color = TitleInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Image(
                    painter = painterResource(id = item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = item.title,
                    color = BodyInk,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}
