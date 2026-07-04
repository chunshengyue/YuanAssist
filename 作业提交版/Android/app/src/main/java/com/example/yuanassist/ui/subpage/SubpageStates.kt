package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.example.yuanassist.ui.main.components.GufengFeatureCard
import com.example.yuanassist.ui.main.components.GufengCardIcon
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun SubpageEmptyState(
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GufengFeatureCard(
            title = title,
            subtitle = subtitle,
            actionText = actionText ?: "静候安排",
            icon = GufengCardIcon.Ornament,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionText != null && onAction != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    ),
            ) {
                StoneStyleButton(
                    text = actionText,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
fun SubpageLoadingState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = HighlightGold)
        Text(
            text = title,
            color = TitleInk,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Start,
        )
        Text(
            text = subtitle,
            color = BodyInk,
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
fun SubpageErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = TitleInk,
        fontSize = 12.sp,
        fontFamily = FontFamily.Serif,
    )
}
