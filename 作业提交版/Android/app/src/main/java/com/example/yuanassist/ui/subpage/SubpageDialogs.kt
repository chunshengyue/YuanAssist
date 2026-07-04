package com.example.yuanassist.ui.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.TitleInk

@Composable
fun SubpageConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        title = {
            Text(
                text = title,
                color = TitleInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
        },
        text = {
            Text(
                text = message,
                color = BodyInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}

@Composable
fun SubpageInputDialog(
    visible: Boolean,
    title: String,
    initialValue: String,
    label: String,
    confirmText: String = "保存",
    dismissText: String = "取消",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val valueState = remember(initialValue, visible) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        title = {
            Text(
                text = title,
                color = TitleInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SubpageTextField(
                    value = valueState.value,
                    onValueChange = { valueState.value = it },
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(valueState.value) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        },
    )
}
