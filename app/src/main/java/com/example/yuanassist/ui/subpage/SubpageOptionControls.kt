package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk

private val OptionSelectedStroke = Color(0xFFB88D41)
private val OptionUnselectedStroke = Color(0xFFD2BE98)
private val OptionSelectedText = Color(0xFF6E4523)
private val OptionUnselectedText = Color(0xFF7C5A34)
private val OptionDotFill = Color(0xFFBE8B3D)
private val OptionDotRing = Color(0xFFD9BE88)
private val OptionCheckFill = Color(0xFFC99746)

@Composable
fun SubpageRadioOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    titleFontSize: TextUnit? = null,
    indicatorSize: Dp = 18.dp,
) {
    SubpageOptionRow(
        text = text,
        subtitle = subtitle,
        selected = selected,
        enabled = enabled,
        titleFontSize = titleFontSize,
        indicator = {
            SubpageCircleIndicator(
                selected = selected,
                enabled = enabled,
                size = indicatorSize,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SubpageCheckOption(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    indicatorSize: Dp = 16.dp,
    titleFontSize: TextUnit? = null,
) {
    SubpageOptionRow(
        text = text,
        subtitle = subtitle,
        selected = checked,
        enabled = enabled,
        titleFontSize = titleFontSize,
        indicator = {
            SubpageSquareIndicator(
                checked = checked,
                enabled = enabled,
                size = indicatorSize,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun SubpageCircleIndicator(
    selected: Boolean,
    enabled: Boolean,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val dotSize = (size.value * 0.44f).coerceAtLeast(6f).dp
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 1.5.dp,
                color = when {
                    !enabled -> OptionUnselectedStroke.copy(alpha = 0.35f)
                    selected -> OptionSelectedStroke
                    else -> OptionDotRing
                },
                shape = CircleShape,
            )
            .background(
                color = Color.White.copy(alpha = if (enabled) 0.55f else 0.32f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(OptionDotFill, CircleShape),
            )
        }
    }
}

@Composable
fun SubpageSquareIndicator(
    checked: Boolean,
    enabled: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 1.5.dp,
                color = when {
                    !enabled -> OptionUnselectedStroke.copy(alpha = 0.35f)
                    checked -> OptionSelectedStroke
                    else -> OptionUnselectedStroke
                },
                shape = RoundedCornerShape(4.dp),
            )
            .background(
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.3f)
                    checked -> OptionCheckFill.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size((size.value - 6f).coerceAtLeast(6f).dp)
                    .background(OptionCheckFill, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun SubpageOptionRow(
    text: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    titleFontSize: TextUnit?,
    indicator: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor = when {
        !enabled -> OptionUnselectedText.copy(alpha = 0.45f)
        selected -> OptionSelectedText
        else -> OptionUnselectedText
    }
    val titleSize = titleFontSize ?: when {
        subtitle != null -> 15.sp
        text.length <= 4 -> 16.sp
        text.length >= 10 -> 14.sp
        else -> 15.sp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        indicator()
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(if (subtitle == null) 0.dp else 3.dp),
        ) {
            Text(
                text = text,
                color = titleColor,
                fontFamily = FontFamily.Serif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = titleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = BodyInk.copy(alpha = if (enabled) 0.82f else 0.45f),
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
