package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun SubpageActionRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f), SubpageShapes.roundBadge)
            .then(clickableModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = TitleInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = BodyInk.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
        Text(
            text = trailingText ?: if (onClick != null) "进入" else "",
            color = HighlightGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
        )
    }
}

@Composable
fun SubpageToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubpageSquareIndicator(
            checked = checked,
            enabled = true,
            size = 18.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = if (checked) HighlightGold.copy(alpha = 0.9f) else TitleInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = BodyInk.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
    }
}

@Composable
fun SubpageChipRow(
    items: List<String>,
    selectedItem: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val selected = item == selectedItem
            Box(modifier = Modifier.weight(1f)) {
                SubpageRadioOption(
                    text = item,
                    selected = selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}
