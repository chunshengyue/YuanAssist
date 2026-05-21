package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StoneButtonShape = RoundedCornerShape(10.dp)
private val StoneButtonInk = Color(0xFF75322D)
private val StoneButtonGold = Color(0xFFC79C5C)
private val StoneButtonStroke = Color(0xFFB57A45)

@Composable
fun StoneStyleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .shadow(
                elevation = if (selected && enabled) 6.dp else 3.dp,
                shape = StoneButtonShape,
                clip = false,
                ambientColor = Color(0xFFD2A06E).copy(alpha = if (enabled) 0.16f else 0.08f),
                spotColor = Color(0xFF8E5F3B).copy(alpha = if (enabled) 0.08f else 0.04f),
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = if (!enabled) {
                        listOf(Color(0xFFF7F0E3), Color(0xFFE9DEC8))
                    } else if (selected) {
                        listOf(Color(0xFFFFF7E6), Color(0xFFF2E0B9))
                    } else {
                        listOf(Color(0xFFFFFDF8), Color(0xFFF5E7CA))
                    },
                ),
                shape = StoneButtonShape,
            )
            .border(
                width = 1.dp,
                color = (if (selected && enabled) StoneButtonGold else StoneButtonStroke)
                    .copy(alpha = if (enabled) 0.72f else 0.36f),
                shape = StoneButtonShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) StoneButtonInk else StoneButtonInk.copy(alpha = 0.45f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StoneStyleChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SubpageRadioOption(
        text = text,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
    )
}
