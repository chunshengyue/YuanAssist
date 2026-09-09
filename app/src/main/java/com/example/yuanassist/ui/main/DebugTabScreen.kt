package com.example.yuanassist.ui.main

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.ConsolePanel
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.utils.CombatDetectionRoi
import com.example.yuanassist.utils.CombatDetectionRoiStore
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DebugTabScreen(
    state: DebugWorkbenchState,
    actions: DebugTabActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionPageTitle(
            title = "调试",
            subtitle = "功能测试 · 素材替换",
        )
        DebugGlassPanel(title = "测试截图") {
            val screenshotBitmap = state.screenshotBitmap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .background(ConsolePanel, RoundedCornerShape(14.dp))
                    .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            ) {
                if (screenshotBitmap != null) {
                    Image(
                        bitmap = screenshotBitmap.asImageBitmap(),
                        contentDescription = "测试截图",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "截图预览区",
                                color = TitleInk,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                            )
                            Text(
                                text = "上传截图后，这里会直接显示当前测试图",
                                color = BodyInk.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            Text(
                text = state.screenshotTitle,
                color = TitleInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = state.screenshotSubtitle,
                color = BodyInk.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DebugMiniButton(
                    text = "上传截图",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions.onPickImage,
                )
            }
        }
        DebugGlassPanel(title = "测试配置") {
            DebugDropdownField(
                label = "任务",
                value = state.selectedTaskLabel,
                options = state.taskOptions,
                onSelected = actions.onSelectTask,
            )
            DebugDropdownField(
                label = "素材",
                value = state.selectedTemplateLabel,
                options = state.templateOptions,
                onSelected = actions.onSelectTemplate,
            )
            Text(
                text = "识别范围",
                color = TitleInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DebugScopeChip(
                    label = "全屏识别",
                    selected = !state.isLocalScopeEnabled,
                    onClick = { actions.onSelectScope(false) },
                )
                DebugScopeChip(
                    label = "局部识别",
                    selected = state.isLocalScopeEnabled,
                    onClick = { actions.onSelectScope(true) },
                )
            }
            Text(
                text = state.scopeHint,
                color = BodyInk.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            DebugActionGrid(state = state, actions = actions)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFFFFF4DF), RoundedCornerShape(14.dp))
                    .border(1.dp, GlassStroke.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "素材延迟增量",
                        color = TitleInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                    OutlinedTextField(
                        value = state.delayInput,
                        onValueChange = actions.onDelayInputChange,
                        enabled = state.delaySupported,
                        singleLine = true,
                        label = { Text("输入单素材增加延迟（ms）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GlassPanel,
                            unfocusedContainerColor = GlassPanel,
                            disabledContainerColor = GlassPanel.copy(alpha = 0.55f),
                            focusedBorderColor = HighlightGold,
                            unfocusedBorderColor = GlassStroke.copy(alpha = 0.55f),
                            disabledBorderColor = GlassStroke.copy(alpha = 0.24f),
                            focusedTextColor = TitleInk,
                            unfocusedTextColor = TitleInk,
                            disabledTextColor = BodyInk.copy(alpha = 0.5f),
                            focusedLabelColor = HighlightGold,
                            unfocusedLabelColor = BodyInk,
                            disabledLabelColor = BodyInk.copy(alpha = 0.5f),
                            cursorColor = HighlightGold,
                        ),
                    )
                    Text(
                        text = state.delaySummary,
                        color = BodyInk.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DebugMiniButton(
                            text = "保存增量",
                            modifier = Modifier.weight(1f),
                            onClick = actions.onSaveDelay,
                        )
                        DebugMiniButton(
                            text = "清除增量",
                            modifier = Modifier.weight(1f),
                            onClick = actions.onClearDelay,
                        )
                    }
                }
            }
        }
        DebugGlassPanel(title = "测试日志") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFFF8EFD9), RoundedCornerShape(14.dp))
                    .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = state.logText,
                    color = TitleInk.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DebugMiniButton(
                    text = "复制日志",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions.onCopyLog,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
    state.replacementDialog?.let { dialogState ->
        DebugReplacementDialog(
            state = dialogState,
            onDismiss = actions.onDismissReplacementDialog,
            onConfirm = actions.onConfirmReplacement,
        )
    }
    state.combatRoiDialog?.let { dialogState ->
        DebugCombatRoiDialog(
            state = dialogState,
            onMove = actions.onMoveCombatRoi,
            onDismiss = actions.onDismissCombatRoiDialog,
            onSave = actions.onSaveCombatRoi,
        )
    }
}

@Composable
private fun DebugCombatRoiDialog(
    state: DebugCombatRoiDialogState,
    onMove: (Float, Float, Float, Float) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = GlassPanel,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(state.title, color = TitleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                Text(state.hint, color = BodyInk.copy(alpha = 0.82f), fontSize = 11.sp, fontFamily = FontFamily.Serif)
                DebugCombatRoiCanvas(
                    sessionId = state.sessionId,
                    bitmap = state.previewBitmap,
                    roi = CombatDetectionRoi(state.x, state.y, state.w, state.h),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onMove = onMove,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugMiniButton("取消", Modifier.weight(1f), onClick = onDismiss)
                    DebugMiniButton("保存修改", Modifier.weight(1f), onClick = onSave)
                }
            }
        }
    }
}

@Composable
private fun DebugCombatRoiCanvas(
    sessionId: Long,
    bitmap: android.graphics.Bitmap,
    roi: CombatDetectionRoi,
    modifier: Modifier = Modifier,
    onMove: (Float, Float, Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.background(ConsolePanel, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        var imageSize by remember(sessionId) { mutableStateOf(IntSize.Zero) }
        var rect by remember(sessionId) {
            mutableStateOf(CombatDetectionRoiStore.toBitmapRect(bitmap, roi, align = "bottom").let {
                RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
            })
        }
        var dragMode by remember(sessionId) { mutableStateOf(0) }
        val latestOnMove by rememberUpdatedState(onMove)
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val containerAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val imageWidth = if (imageAspect > containerAspect) maxWidth else maxHeight * imageAspect
        val imageHeight = if (imageAspect > containerAspect) maxWidth / imageAspect else maxHeight
        Box(
            modifier = Modifier.size(imageWidth, imageHeight).clip(RoundedCornerShape(12.dp)).onSizeChanged { imageSize = it },
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "战斗 ROI 预览", modifier = Modifier.fillMaxSize())
            androidx.compose.foundation.Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(sessionId, imageSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val x = offset.x * bitmap.width / imageSize.width.coerceAtLeast(1)
                            val y = offset.y * bitmap.height / imageSize.height.coerceAtLeast(1)
                            val edge = (12f * bitmap.width / imageSize.width.coerceAtLeast(1)).coerceAtLeast(8f)
                            dragMode = when {
                                kotlin.math.abs(y - rect.top) <= edge && x in rect.left..rect.right -> -1
                                kotlin.math.abs(y - rect.bottom) <= edge && x in rect.left..rect.right -> 1
                                rect.contains(x, y) -> 2
                                else -> 0
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            if (dragMode == 0 || imageSize.width <= 0) return@detectDragGestures
                            val dx = amount.x * bitmap.width / imageSize.width
                            val dy = amount.y * bitmap.height / imageSize.height
                            rect = when (dragMode) {
                                -1 -> {
                                    val top = (rect.top + dy).coerceIn(0f, rect.bottom - 1f)
                                    RectF(rect.left, top, rect.right, rect.bottom)
                                }
                                1 -> {
                                    val bottom = (rect.bottom + dy).coerceIn(rect.top + 1f, bitmap.height.toFloat())
                                    RectF(rect.left, rect.top, rect.right, bottom)
                                }
                                else -> {
                                    val left = (rect.left + dx).coerceIn(0f, bitmap.width - rect.width())
                                    val top = (rect.top + dy).coerceIn(0f, bitmap.height - rect.height())
                                    RectF(left, top, left + rect.width(), top + rect.height())
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragMode != 0) {
                                val next = CombatDetectionRoiStore.fromBitmapRect(
                                    bitmap,
                                    Rect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()),
                                    align = "bottom",
                                )
                                latestOnMove(next.x, next.y, next.w, next.h)
                            }
                            dragMode = 0
                        },
                        onDragCancel = { dragMode = 0 },
                    )
                },
            ) {
                val scaleX = size.width / bitmap.width.toFloat().coerceAtLeast(1f)
                val scaleY = size.height / bitmap.height.toFloat().coerceAtLeast(1f)
                drawRect(
                    color = Color(0xFF43A047),
                    topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                    size = androidx.compose.ui.geometry.Size(rect.width() * scaleX, rect.height() * scaleY),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}


@Composable
private fun DebugGlassPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassPanel, RoundedCornerShape(16.dp))
            .border(1.dp, GlassStroke.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(
                text = title,
                color = TitleInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            content()
        },
    )
}

@Composable
private fun DebugDropdownField(
    label: String,
    value: String,
    options: List<DebugSelectionOption>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val menuWidth = with(density) { fieldSize.width.toDp() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = TitleInk,
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .onSizeChanged { fieldSize = it }
                .background(androidx.compose.ui.graphics.Color(0xFFFFFBF2), RoundedCornerShape(10.dp))
                .border(1.dp, GlassStroke.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = value,
                    color = TitleInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = BodyInk.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(menuWidth)
                    .heightIn(max = 280.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFFFFFBF2)),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = TitleInk,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(option.key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) androidx.compose.ui.graphics.Color(0xFFE9D5B1) else androidx.compose.ui.graphics.Color(0xFFF6ECDA),
                RoundedCornerShape(999.dp),
            )
            .border(1.dp, GlassStroke.copy(alpha = if (selected) 0.56f else 0.24f), RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = TitleInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun DebugActionGrid(
    state: DebugWorkbenchState,
    actions: DebugTabActions,
) {
    val secondaryActions = if (state.isCombatDetection) {
        listOf("修改区域", "恢复默认")
    } else {
        listOf("一键替换", "还原素材")
    }
    listOf(
        listOf("上传截图", "开始测试"),
        secondaryActions,
    ).forEach { rowActions ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowActions.forEach { text ->
                    val enabled = when (text) {
                        "一键替换" -> state.canReplaceTemplate
                        "还原素材" -> state.canRestoreTemplate
                        "修改区域" -> state.canEditCombatRoi
                        "恢复默认" -> state.canResetCombatRoi
                        else -> true
                    }
                    DebugMiniButton(
                        text = text,
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        onClick = when (text) {
                            "上传截图" -> actions.onPickImage
                            "开始测试" -> actions.onRunTest
                            "一键替换" -> actions.onReplaceTemplate
                            "还原素材" -> actions.onRestoreTemplate
                            "修改区域" -> actions.onOpenCombatRoiDialog
                            "恢复默认" -> actions.onResetCombatRoi
                            else -> ({})
                        },
                    )
                }
            }
        }
}

@Composable
private fun DebugMiniButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier,
    ) {
        StoneStyleButton(
            text = text,
            onClick = onClick,
            selected = enabled,
            enabled = enabled,
            minHeight = 38.dp,
        )
    }
}

@Composable
private fun DebugReplacementDialog(
    state: DebugReplacementDialogState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF8F0E1),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = state.title,
                        color = TitleInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = state.hint,
                        color = BodyInk.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = "当前截图红框=${state.boxWidthPx}x${state.boxHeightPx}px，保存后统一转成标准1080下60x60素材",
                        color = BodyInk.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                    )
                    DebugReplacementCanvas(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onConfirm = onConfirm,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DebugMiniButton(
                            text = "取消",
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugReplacementCanvas(
    state: DebugReplacementDialogState,
    modifier: Modifier = Modifier,
    onConfirm: (Int, Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp))
            .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        var imageSize by remember(state.sessionId) { mutableStateOf(IntSize.Zero) }
        val previewBitmap = state.previewBitmap
        val imageAspect = previewBitmap.width.toFloat() / previewBitmap.height.toFloat().coerceAtLeast(1f)
        val containerAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val imageWidth = if (imageAspect > containerAspect) {
            maxWidth
        } else {
            maxHeight * imageAspect
        }
        val imageHeight = if (imageAspect > containerAspect) {
            maxWidth / imageAspect
        } else {
            maxHeight
        }
        val imageModifier = Modifier
            .size(width = imageWidth, height = imageHeight)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { imageSize = it }
        val latestConfirm by rememberUpdatedState(onConfirm)
        var selectionLeft by remember(state.sessionId) { mutableStateOf(state.initialLeftPx.toFloat()) }
        var selectionTop by remember(state.sessionId) { mutableStateOf(state.initialTopPx.toFloat()) }
        val maxLeft = (previewBitmap.width - state.boxWidthPx).coerceAtLeast(0)
        val maxTop = (previewBitmap.height - state.boxHeightPx).coerceAtLeast(0)
        Box(
            modifier = imageModifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "替换素材预览",
                modifier = Modifier.fillMaxSize(),
            )
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state.sessionId, imageSize) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (imageSize.width <= 0 || imageSize.height <= 0) return@detectDragGestures
                            val bitmapPerDisplayX = previewBitmap.width.toFloat() / imageSize.width.toFloat()
                            val bitmapPerDisplayY = previewBitmap.height.toFloat() / imageSize.height.toFloat()
                            selectionLeft = (selectionLeft + dragAmount.x * bitmapPerDisplayX).coerceIn(0f, maxLeft.toFloat())
                            selectionTop = (selectionTop + dragAmount.y * bitmapPerDisplayY).coerceIn(0f, maxTop.toFloat())
                        }
                    },
            ) {
                val scaleX = size.width / previewBitmap.width.toFloat().coerceAtLeast(1f)
                val scaleY = size.height / previewBitmap.height.toFloat().coerceAtLeast(1f)
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(selectionLeft * scaleX, selectionTop * scaleY),
                    size = androidx.compose.ui.geometry.Size(
                        state.boxWidthPx * scaleX,
                        state.boxHeightPx * scaleY,
                    ),
                    style = Stroke(width = min(size.width, size.height) / 120f + 2f),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DebugMiniButton(
                text = "居中",
                modifier = Modifier.weight(1f),
                onClick = {
                    selectionLeft = state.initialLeftPx.toFloat()
                    selectionTop = state.initialTopPx.toFloat()
                },
            )
            DebugMiniButton(
                text = "确认替换",
                modifier = Modifier.weight(1f),
                onClick = {
                    latestConfirm(selectionLeft.toInt(), selectionTop.toInt())
                },
            )
        }
    }
}
