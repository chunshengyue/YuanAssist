package com.example.yuanassist.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R

@Composable
fun JobTabScreen(
    actions: JobTabActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        SectionPageTitle(
            title = "作业",
            subtitle = "选择你要查看的攻略来源",
        )
        JobStationSourceCard(
            title = "本站攻略",
            description = "攻略较少，但是可以收藏、评论、发布攻略。",
            iconRes = R.drawable.card_shixie,
            featured = false,
            onClick = actions.onOpenCommunity,
        )
        JobStationSourceCard(
            title = "发布攻略",
            description = "分享你的攻略及作业，其他广陵王可以一键导入。",
            iconRes = R.drawable.card_huangyueying,
            featured = true,
            mirrored = true,
            onClick = actions.onOpenPublishStrategy,
        )
        JobStationSourceCard(
            title = "MaaYuan Share",
            description = "丰富的作业！来源于 MaaYuan 作业站。",
            iconRes = R.drawable.card_zhangfei,
            featured = false,
            onClick = actions.onOpenMaaYuan,
        )
        JobStationSourceCard(
            title = "生成练度表",
            description = "自动识别角色练度和命盘，生成练度表。",
            iconRes = R.drawable.card_zhugeliang_table,
            featured = true,
            mirrored = true,
            onClick = actions.onOpenCharacterImport,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun JobStationSourceCard(
    title: String,
    description: String,
    iconRes: Int,
    featured: Boolean,
    mirrored: Boolean = false,
    onClick: () -> Unit,
) {
    val cardHeight = if (featured) 136.dp else 128.dp
    val portraitFrameSize = cardHeight
    val portraitIconSize = cardHeight + 9.dp
    val portraitOffsetX = if (featured) 32.dp else 28.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawWithCache {
                val bodyRadius = 24.dp.toPx()
                val cardColor = if (featured) Color(0xFFFFF8EA) else Color(0xFFFFFDF8)
                val lineColor = Color(0xFFA65D35).copy(alpha = 0.80f)
                val shadowColor = Color(0xFF6E4937).copy(alpha = if (featured) 0.10f else 0.07f)
                val stroke = 1.35.dp.toPx()
                val borderInset = 1.dp.toPx()
                val shadowOffsetY = 6.dp.toPx()
                val portraitCenterX = if (mirrored) {
                    portraitFrameSize.toPx() / 2f - portraitOffsetX.toPx()
                } else {
                    size.width + portraitOffsetX.toPx() - portraitFrameSize.toPx() / 2f
                }
                val circleCenter = Offset(
                    x = portraitCenterX,
                    y = size.height / 2f,
                )
                val circleRadius = size.height / 2f - borderInset
                val bodyWidth = if (mirrored) size.width - circleCenter.x else circleCenter.x
                val outlinePath = Path().apply {
                    val top = borderInset
                    val bottom = size.height - borderInset
                    val left = borderInset
                    val right = size.width - borderInset
                    if (mirrored) {
                        moveTo(circleCenter.x, top)
                        lineTo(right - bodyRadius, top)
                        arcTo(
                            rect = Rect(
                                left = right - bodyRadius * 2f,
                                top = top,
                                right = right,
                                bottom = top + bodyRadius * 2f,
                            ),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(right, bottom - bodyRadius)
                        arcTo(
                            rect = Rect(
                                left = right - bodyRadius * 2f,
                                top = bottom - bodyRadius * 2f,
                                right = right,
                                bottom = bottom,
                            ),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(circleCenter.x, bottom)
                        arcTo(
                            rect = Rect(
                                left = circleCenter.x - circleRadius,
                                top = top,
                                right = circleCenter.x + circleRadius,
                                bottom = bottom,
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false,
                        )
                        close()
                    } else {
                        moveTo(left + bodyRadius, top)
                        lineTo(circleCenter.x, top)
                        arcTo(
                            rect = Rect(
                                left = circleCenter.x - circleRadius,
                                top = top,
                                right = circleCenter.x + circleRadius,
                                bottom = bottom,
                            ),
                            startAngleDegrees = -90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false,
                        )
                        lineTo(left + bodyRadius, bottom)
                        arcTo(
                            rect = Rect(
                                left = left,
                                top = bottom - bodyRadius * 2f,
                                right = left + bodyRadius * 2f,
                                bottom = bottom,
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        lineTo(left, top + bodyRadius)
                        arcTo(
                            rect = Rect(
                                left = left,
                                top = top,
                                right = left + bodyRadius * 2f,
                                bottom = top + bodyRadius * 2f,
                            ),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false,
                        )
                        close()
                    }
                }
                onDrawBehind {
                    if (mirrored) {
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(circleCenter.x - circleRadius, shadowOffsetY),
                            size = androidx.compose.ui.geometry.Size(size.width - (circleCenter.x - circleRadius), size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                    } else {
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(0f, shadowOffsetY),
                            size = androidx.compose.ui.geometry.Size(bodyWidth, size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                    }
                    drawCircle(
                        color = shadowColor,
                        radius = circleRadius,
                        center = circleCenter.copy(y = circleCenter.y + shadowOffsetY),
                    )
                    if (mirrored) {
                        drawRoundRect(
                            color = Color(0xFF9D6A45).copy(alpha = if (featured) 0.075f else 0.05f),
                            topLeft = Offset(circleCenter.x - circleRadius, 4.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - (circleCenter.x - circleRadius), size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                        drawRoundRect(
                            color = cardColor,
                            topLeft = Offset(circleCenter.x, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width - circleCenter.x, size.height),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                    } else {
                        drawRoundRect(
                            color = Color(0xFF9D6A45).copy(alpha = if (featured) 0.075f else 0.05f),
                            topLeft = Offset(0f, 4.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(bodyWidth + circleRadius, size.height - 2.dp.toPx()),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                        drawRoundRect(
                            color = cardColor,
                            size = androidx.compose.ui.geometry.Size(bodyWidth, size.height),
                            cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                        )
                    }
                    drawCircle(
                        color = cardColor,
                        radius = circleRadius,
                        center = circleCenter,
                    )
                    drawPath(path = outlinePath, color = lineColor, style = Stroke(width = stroke))
                    if (mirrored) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(circleCenter.x + 20.dp.toPx(), 1.8.dp.toPx()),
                            end = Offset(size.width - 18.dp.toPx(), 1.8.dp.toPx()),
                            strokeWidth = 0.6.dp.toPx(),
                        )
                    } else {
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(18.dp.toPx(), 1.8.dp.toPx()),
                            end = Offset(bodyWidth - 20.dp.toPx(), 1.8.dp.toPx()),
                            strokeWidth = 0.6.dp.toPx(),
                        )
                    }
                }
            },
        contentAlignment = if (mirrored) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (mirrored) portraitFrameSize - 18.dp else 18.dp,
                    top = 10.dp,
                    end = if (mirrored) 18.dp else portraitFrameSize - 18.dp,
                    bottom = 10.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    color = Color(0xFF3D3222),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = description,
                    color = Color(0xFF715D3A).copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 8.dp)
                    .drawWithCache {
                        val startX = 4.dp.toPx()
                        val endX = size.width - 4.dp.toPx()
                        val lineY = 2.4.dp.toPx()
                        val shadowY = 1.8.dp.toPx()
                        onDrawBehind {
                            drawLine(
                                color = Color(0xFF8D5E43).copy(alpha = 0.10f),
                                start = Offset(startX, lineY + shadowY),
                                end = Offset(endX, lineY + shadowY),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                            drawLine(
                                color = Color(0xFFB58D66).copy(alpha = 0.40f),
                                start = Offset(startX, lineY),
                                end = Offset(endX, lineY),
                                strokeWidth = 0.9.dp.toPx(),
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.26f),
                                start = Offset(startX + 8.dp.toPx(), lineY - 0.5.dp.toPx()),
                                end = Offset(endX - 8.dp.toPx(), lineY - 0.5.dp.toPx()),
                                strokeWidth = 0.5.dp.toPx(),
                            )
                        }
                    },
            )
        }
        Box(
            modifier = Modifier
                .size(portraitFrameSize)
                .align(if (mirrored) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (mirrored) -portraitOffsetX else portraitOffsetX),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.requiredSize(portraitIconSize),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
