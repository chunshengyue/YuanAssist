package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.ConsolePanel
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.main.theme.WarmRose

@Composable
fun SubpageSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    content: @Composable () -> Unit,
) {
    val sectionShape = SubpageShapes.section
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 9.dp,
                shape = sectionShape,
                clip = false,
                ambientColor = Color(0xFFD2A06E).copy(alpha = 0.18f),
                spotColor = Color(0xFF8E5F3B).copy(alpha = 0.10f),
            )
            .drawWithCache {
                val radius = 24.dp.toPx()
                val outerStroke = 1.25.dp.toPx()
                val innerInset = 6.dp.toPx()
                onDrawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                GlassPanel.copy(alpha = 0.98f),
                                ConsolePanel.copy(alpha = 0.97f),
                                GlassPanel.copy(alpha = 0.98f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFE9A9),
                                Color(0xFFB87535),
                                Color(0xFFE0B15D),
                                Color(0xFFFFF1BE),
                                Color(0xFFB87535),
                                Color(0xFFE0B15D),
                                Color(0xFFFFE9A9),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        topLeft = Offset(outerStroke / 2f, outerStroke / 2f),
                        size = Size(size.width - outerStroke, size.height - outerStroke),
                        cornerRadius = CornerRadius(radius - outerStroke / 2f, radius - outerStroke / 2f),
                        style = Stroke(width = outerStroke, cap = StrokeCap.Round),
                    )
                    drawRoundRect(
                        color = Color(0xFFC88B5A).copy(alpha = 0.32f),
                        topLeft = Offset(innerInset, innerInset),
                        size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
                        cornerRadius = CornerRadius(radius - innerInset, radius - innerInset),
                        style = Stroke(width = 0.55.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            .background(
                color = Color.Transparent,
                shape = sectionShape,
            )
            .border(
                width = 0.dp,
                color = Color.Transparent,
                shape = sectionShape,
            )
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null || subtitle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        color = TitleInk,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = BodyInk.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun SubpagePaperPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.38f), SubpageShapes.roundBadge)
            .border(
                width = 1.dp,
                color = GlassStroke.copy(alpha = 0.28f),
                shape = SubpageShapes.roundBadge,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
fun SubpageInfoStrip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.45f), SubpageShapes.roundBadge)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = QuietInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
        )
        Text(
            text = value,
            color = TitleInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
        )
    }
}

@Composable
fun SubpageBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = WarmRose,
) {
    Box(
        modifier = modifier
            .background(tint.copy(alpha = 0.16f), SubpageShapes.roundBadge)
            .border(1.dp, PaperLine.copy(alpha = 0.55f), SubpageShapes.roundBadge)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TitleInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
        )
    }
}
