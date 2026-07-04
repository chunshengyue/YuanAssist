package com.example.yuanassist.ui.main.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R

enum class GufengCardIcon {
    Ornament,
}

@Composable
fun GufengFeatureCard(
    title: String,
    subtitle: String,
    actionText: String,
    icon: GufengCardIcon,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .shadow(
                elevation = 9.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color(0xFFD2A06E).copy(alpha = 0.20f),
                spotColor = Color(0xFF8E5F3B).copy(alpha = 0.12f),
            )
            .drawWithCache {
                val radius = 28.dp.toPx()
                val outerStroke = 1.35.dp.toPx()
                val innerInset = 6.5.dp.toPx()

                onDrawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFFFBF2), Color(0xFFFFF6E8), Color(0xFFFFFBF2)),
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
                        color = Color(0xFFC88B5A).copy(alpha = 0.38f),
                        topLeft = Offset(innerInset, innerInset),
                        size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
                        cornerRadius = CornerRadius(radius - innerInset, radius - innerInset),
                        style = Stroke(width = 0.55.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            .padding(start = 22.dp, top = 18.dp, end = 18.dp, bottom = 17.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon == GufengCardIcon.Ornament) {
                Image(
                    painter = painterResource(id = R.drawable.ornament_1),
                    contentDescription = null,
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = Color(0xFF7A302B),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF8C6C61),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(13.dp))
                Text(
                    text = actionText,
                    color = Color(0xFF315B42),
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}
