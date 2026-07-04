package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun SubpageFieldGroup(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = TitleInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = BodyInk.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
        }
        content()
    }
}

@Composable
fun SubpageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = FontFamily.Serif,
            )
        },
        modifier = modifier,
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GlassPanel,
            unfocusedContainerColor = GlassPanel,
            focusedBorderColor = HighlightGold,
            unfocusedBorderColor = GlassStroke.copy(alpha = 0.55f),
            focusedTextColor = TitleInk,
            unfocusedTextColor = TitleInk,
            focusedLabelColor = HighlightGold,
            unfocusedLabelColor = BodyInk,
            cursorColor = HighlightGold,
        ),
    )
}
