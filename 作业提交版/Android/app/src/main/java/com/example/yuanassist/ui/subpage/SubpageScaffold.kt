package com.example.yuanassist.ui.subpage

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.main.theme.WarmRose
import com.example.yuanassist.R

@Composable
fun SubpageScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    SubpageThemeBridge(modifier = modifier) {
        val bodyModifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .widthIn(max = 760.dp)

        Column(
            modifier = if (scrollable) {
                bodyModifier.verticalScroll(rememberScrollState())
            } else {
                bodyModifier
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SubpageTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = actions,
            )
            content()
        }
    }
}

@Composable
fun SubpageTopBar(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
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
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                SubpageBackButton(onClick = onBack)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    color = TitleInk,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = subtitle,
                    color = BodyInk.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.sp,
                )
            }
            if (actions != null) {
                Box(contentAlignment = Alignment.TopEnd) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun SubpageBackButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .requiredSize(width = 72.dp, height = 62.dp)
            .drawWithCache {
                val radius = 30.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
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
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "‹",
                color = TitleInk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

object SubpageShapes {
    val roundBadge = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val section = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
}
