package com.example.yuanassist.ui.main.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R

private val CapsuleIvory = Color(0xFFFFF2DD)
private val CapsuleWarmPaper = Color(0xFFF2E0C3)
private val CapsuleGold = Color(0xFFC99A52)
private val CapsuleInk = Color(0xFF73332E)

@Composable
fun GufengDecorActionButton(
    text: String,
    @DrawableRes itemRes: Int,
    @DrawableRes decorRes: Int = R.drawable.decor_butterfly,
    showOuterSurface: Boolean = true,
    showShadow: Boolean = true,
    showDecor: Boolean = true,
    showPortraitPlate: Boolean = true,
    containerHeight: Dp = 82.dp,
    surfaceHeight: Dp = 64.dp,
    portraitSize: Dp = 56.dp,
    portraitOffsetX: Dp = 5.dp,
    textStartPadding: Dp = 66.dp,
    textEndPadding: Dp = 14.dp,
    textSize: TextUnit = 16.sp,
    modifier: Modifier = Modifier,
) {
    val resolvedTextSize = when {
        text.length <= 4 -> textSize
        text.length == 5 -> (textSize.value - 2f).coerceAtLeast(10f).sp
        else -> (textSize.value - 3f).coerceAtLeast(10f).sp
    }

    Box(
        modifier = modifier.height(containerHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (showOuterSurface) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .fillMaxWidth()
                    .height(surfaceHeight)
                    .align(Alignment.Center)
                    .then(
                        if (showShadow) {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(32.dp),
                                clip = false,
                                ambientColor = Color(0xFFD39A72).copy(alpha = 0.23f),
                                spotColor = Color(0xFF8F5C40).copy(alpha = 0.10f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .drawWithCache {
                        val radius = 32.dp.toPx()
                        onDrawBehind {
                            if (showShadow) {
                                drawRoundRect(
                                    color = Color(0xFFB06D4F).copy(alpha = 0.07f),
                                    topLeft = Offset((-2).dp.toPx(), 5.dp.toPx()),
                                    size = Size(size.width + 4.dp.toPx(), size.height),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                            }
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFF0DFBE),
                                        CapsuleIvory,
                                        Color(0xFFFFF6E8),
                                        CapsuleWarmPaper,
                                    ),
                                ),
                                cornerRadius = CornerRadius(radius, radius),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = CapsuleInk,
                    modifier = Modifier.padding(start = textStartPadding, end = textEndPadding),
                    fontSize = resolvedTextSize,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = text,
                color = CapsuleInk,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = textStartPadding, end = textEndPadding),
                fontSize = resolvedTextSize,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
        }

        if (showPortraitPlate) {
            Image(
                painter = painterResource(id = R.drawable.decor_ink_plate),
                contentDescription = null,
                modifier = Modifier
                    .size(surfaceHeight + 8.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = (-2).dp)
                    .graphicsLayer(alpha = 0.96f),
                contentScale = ContentScale.Fit,
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .fillMaxWidth()
                .height(surfaceHeight)
                .align(Alignment.Center)
                .drawWithCache {
                    val inset = 6.5.dp.toPx()
                    val stroke = 0.58.dp.toPx()
                    val radius = (size.height - inset * 2f) / 2f
                    val joinX = inset + radius
                    val right = size.width - inset
                    val rightArcLeft = right - radius * 2f
                    onDrawBehind {
                        val lineColor = CapsuleGold.copy(alpha = 0.82f)
                        drawLine(
                            color = lineColor,
                            start = Offset(joinX, inset),
                            end = Offset(right - radius, inset),
                            strokeWidth = stroke,
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(joinX, size.height - inset),
                            end = Offset(right - radius, size.height - inset),
                            strokeWidth = stroke,
                        )
                        drawArc(
                            color = lineColor,
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(rightArcLeft, inset),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = stroke),
                        )
                    }
                },
        )

        Image(
            painter = painterResource(id = itemRes),
            contentDescription = null,
            modifier = Modifier
                .size(portraitSize)
                .align(Alignment.CenterStart)
                .offset(x = portraitOffsetX)
                .graphicsLayer(alpha = 0.92f),
            contentScale = ContentScale.Fit,
        )

        if (showDecor) {
            Image(
                painter = painterResource(id = decorRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-11).dp, y = 6.dp)
                    .graphicsLayer(alpha = 0.72f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
