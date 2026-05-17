package com.example.yuanassist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.model.ImportedCharacterRecord
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.StoneStyleChoiceButton
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.utils.CharacterImportOverviewImageRenderer
import com.example.yuanassist.utils.ImageExportUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CharacterImportReviewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_RECORDS_JSON = "extra_records_json"

        fun createIntent(context: Context, records: List<ImportedCharacterRecord>): Intent {
            return Intent(context, CharacterImportReviewActivity::class.java).apply {
                putExtra(EXTRA_RECORDS_JSON, Gson().toJson(records))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRecords = parseRecords(intent.getStringExtra(EXTRA_RECORDS_JSON))
        setContent {
            var records by remember { mutableStateOf(initialRecords) }
            CharacterImportReviewScreen(
                records = records,
                onBack = ::finish,
                onRecordsChange = { records = it },
                onConfirm = { gameTitle, exportSubtitle ->
                    val bitmap = CharacterImportOverviewImageRenderer.render(
                        context = this,
                        records = records,
                        gameTitle = gameTitle,
                        exportSubtitle = exportSubtitle,
                    )
                    ImageExportUtils.saveBitmapToGallery(this, bitmap)
                    bitmap.recycle()
                    finish()
                },
            )
        }
    }

    private fun parseRecords(json: String?): List<ImportedCharacterRecord> {
        val type = object : TypeToken<List<ImportedCharacterRecord>>() {}.type
        val parsed = runCatching {
            Gson().fromJson<List<ImportedCharacterRecord>>(json, type)
        }.getOrNull().orEmpty()
        return List(5) { index ->
            val item = parsed.getOrNull(index)
            if (item == null) {
                ImportedCharacterRecord(slot = index + 1, fates = List(3) { "" })
            } else {
                item.copy(
                    slot = index + 1,
                    fates = List(3) { fateIndex -> item.fates.getOrNull(fateIndex).orEmpty() },
                )
            }
        }
    }
}

private enum class EditField(val label: String) {
    ATTACK("攻击"),
    HP("生命"),
    REMARK("备注"),
}

private data class EditTarget(
    val slotIndex: Int,
    val field: EditField,
)

@Composable
private fun CharacterImportReviewScreen(
    records: List<ImportedCharacterRecord>,
    onBack: () -> Unit,
    onRecordsChange: (List<ImportedCharacterRecord>) -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var agentPickerSlot by remember { mutableIntStateOf(-1) }
    var talentPickerTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var starPickerSlot by remember { mutableIntStateOf(-1) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var gameTitle by remember { mutableStateOf("如鸢") }
    var exportSubtitle by remember { mutableStateOf("") }

    SubpageScaffold(
        title = "导入结果校对",
        subtitle = "5列单屏，直接点格子修改",
        onBack = onBack,
    ) {
        ExportHeaderEditor(
            gameTitle = gameTitle,
            exportSubtitle = exportSubtitle,
            onGameTitleChange = { gameTitle = it },
            onSubtitleChange = { exportSubtitle = it },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFCF5), Color(0xFFF5EBDD)),
                    ),
                )
                .border(1.dp, Color(0xFFE4D1B6), RoundedCornerShape(22.dp))
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                records.forEachIndexed { index, record ->
                    ReviewCharacterColumn(
                        modifier = Modifier.weight(1f),
                        record = record,
                        onRoleClick = { agentPickerSlot = index },
                        onStarClick = { starPickerSlot = index },
                        onAttackClick = { editTarget = EditTarget(index, EditField.ATTACK) },
                        onHpClick = { editTarget = EditTarget(index, EditField.HP) },
                        onRemarkClick = { editTarget = EditTarget(index, EditField.REMARK) },
                        onFateClick = { fateIndex -> talentPickerTarget = index to fateIndex },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StoneStyleButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                selected = false,
            )
            StoneStyleButton(
                text = "确认并保存",
                onClick = { onConfirm(gameTitle, exportSubtitle) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    if (agentPickerSlot >= 0) {
        SharedAgentPickerDialog(
            onDismiss = { agentPickerSlot = -1 },
            onSelect = { agentName ->
                val next = records.toMutableList()
                val current = next[agentPickerSlot]
                next[agentPickerSlot] = current.copy(
                    name = agentName,
                    fates = if (current.name == agentName) current.fates else List(3) { "" },
                )
                onRecordsChange(next)
                agentPickerSlot = -1
            },
        )
    }

    talentPickerTarget?.let { (slotIndex, fateIndex) ->
        val record = records.getOrNull(slotIndex)
        val agentName = record?.name
        if (!agentName.isNullOrBlank() && record != null) {
            val selectedId = resolveTalentIdByLabel(agentName, record.fates.getOrNull(fateIndex).orEmpty())
            val usedTalentIds = record.fates.mapIndexedNotNull { index, label ->
                resolveTalentIdByLabel(agentName, label)?.takeIf { index != fateIndex }
            }.toSet()
            SharedTalentPickerDialog(
                agentName = agentName,
                selectedTalentId = selectedId,
                usedTalentIds = usedTalentIds,
                onDismiss = { talentPickerTarget = null },
                onSelect = { talentId ->
                    val next = records.toMutableList()
                    val fates = record.fates.toMutableList()
                    fates[fateIndex] = resolveTalentLabel(agentName, talentId).orEmpty()
                    next[slotIndex] = record.copy(fates = List(3) { index -> fates.getOrNull(index).orEmpty() })
                    onRecordsChange(next)
                    talentPickerTarget = null
                },
            )
        } else {
            talentPickerTarget = null
        }
    }

    if (starPickerSlot >= 0) {
        StarPickerDialog(
            selectedStar = records.getOrNull(starPickerSlot)?.starCount ?: 0,
            onDismiss = { starPickerSlot = -1 },
            onSelect = { star ->
                val next = records.toMutableList()
                val current = next[starPickerSlot]
                next[starPickerSlot] = current.copy(starCount = star)
                onRecordsChange(next)
                starPickerSlot = -1
            },
        )
    }

    editTarget?.let { target ->
        val record = records.getOrNull(target.slotIndex) ?: return@let
        val currentValue = when (target.field) {
            EditField.ATTACK -> record.attack
            EditField.HP -> record.hp
            EditField.REMARK -> record.remark
        }
        TextEditDialog(
            title = "修改${target.field.label}",
            initialValue = currentValue,
            digitsOnly = target.field != EditField.REMARK,
            onDismiss = { editTarget = null },
            onConfirm = { newValue ->
                val next = records.toMutableList()
                val current = next[target.slotIndex]
                next[target.slotIndex] = when (target.field) {
                    EditField.ATTACK -> current.copy(attack = newValue)
                    EditField.HP -> current.copy(hp = newValue)
                    EditField.REMARK -> current.copy(remark = newValue)
                }
                onRecordsChange(next)
                editTarget = null
            },
        )
    }
}

@Composable
private fun ExportHeaderEditor(
    gameTitle: String,
    exportSubtitle: String,
    onGameTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StoneStyleChoiceButton(
                text = "如鸢",
                selected = gameTitle == "如鸢",
                onClick = { onGameTitleChange("如鸢") },
                modifier = Modifier.weight(1f),
            )
            StoneStyleChoiceButton(
                text = "代号鸢",
                selected = gameTitle == "代号鸢",
                onClick = { onGameTitleChange("代号鸢") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = exportSubtitle,
            onValueChange = onSubtitleChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    text = "自定义副标题",
                    fontFamily = FontFamily.Serif,
                )
            },
            placeholder = {
                Text(
                    text = "关卡名、作者名等",
                    fontFamily = FontFamily.Serif,
                )
            },
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
}

@Composable
private fun ReviewCharacterColumn(
    modifier: Modifier = Modifier,
    record: ImportedCharacterRecord,
    onRoleClick: () -> Unit,
    onStarClick: () -> Unit,
    onAttackClick: () -> Unit,
    onHpClick: () -> Unit,
    onRemarkClick: () -> Unit,
    onFateClick: (Int) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        RoleCell(record = record, onClick = onRoleClick)
        StarCell(starCount = record.starCount, onClick = onStarClick)
        ValueCell(label = "攻", text = record.attack.ifBlank { "-" }, onClick = onAttackClick)
        ValueCell(label = "命", text = record.hp.ifBlank { "-" }, onClick = onHpClick)
        FateListCell(
            fates = record.fates,
            enabled = record.name.isNotBlank(),
            onFateClick = onFateClick,
        )
        RemarkCell(text = record.remark, onClick = onRemarkClick)
    }
}

@Composable
private fun RoleCell(record: ImportedCharacterRecord, onClick: () -> Unit) {
    ReviewCellShell(height = 122.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            AgentAvatar(record.name.takeIf { it.isNotBlank() }, Modifier.size(40.dp))
            Text(
                text = record.name.ifBlank { "选角色" },
                color = if (record.name.isBlank()) QuietInk else TitleInk,
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StarCell(starCount: Int, onClick: () -> Unit) {
    ReviewCellShell(height = 54.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StarIcons(starCount)
        }
    }
}

@Composable
private fun ValueCell(label: String, text: String, onClick: () -> Unit) {
    ReviewCellShell(height = 48.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                color = if (text == "-") QuietInk else BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FateListCell(fates: List<String>, enabled: Boolean, onFateClick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(208.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(3) { index ->
            val text = fates.getOrNull(index).orEmpty()
            FateTextLine(
                text = text.ifBlank { if (enabled) "选择" else "先选角色" },
                placeholder = text.isBlank(),
                onClick = if (enabled) {
                    { onFateClick(index) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun FateTextLine(text: String, placeholder: Boolean, onClick: (() -> Unit)?) {
    val displayText = text.removePrefix("橙").removePrefix("紫")
    val baseModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2.dp, vertical = 3.dp)
    val clickableModifier = if (onClick == null) {
        baseModifier
    } else {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }
    Text(
        text = displayText,
        color = when {
            placeholder -> QuietInk
            text.startsWith("橙") -> Color(0xFFD07A1F)
            text.startsWith("紫") -> Color(0xFF7658C8)
            else -> BodyInk
        },
        modifier = clickableModifier,
        fontFamily = FontFamily.Serif,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RemarkCell(text: String, onClick: () -> Unit) {
    ReviewCellShell(height = 62.dp, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text.ifBlank { "点击填写备注" },
                color = if (text.isBlank()) QuietInk else BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReviewCellShell(
    height: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(15.dp))
        .background(Color.White.copy(alpha = 0.78f))
        .border(1.dp, GlassStroke.copy(alpha = 0.32f), RoundedCornerShape(15.dp))
        .padding(horizontal = 4.dp, vertical = 6.dp)
    val clickableModifier = if (onClick == null) {
        baseModifier
    } else {
        baseModifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }
    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StarIcons(starCount: Int) {
    val count = starCount.coerceIn(0, 6)
    if (count <= 0) {
        Text(
            text = "无",
            color = QuietInk,
            fontFamily = FontFamily.Serif,
            fontSize = 9.sp,
        )
        return
    }
    Text(
        text = "★".repeat(count),
        color = Color(0xFFE1A63B),
        fontSize = if (count >= 5) 7.sp else 8.sp,
        fontFamily = FontFamily.Serif,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StarPickerDialog(
    selectedStar: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TitleInk, fontFamily = FontFamily.Serif)
            }
        },
        title = {
            Text("选择星级", color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3).forEach { star ->
                        StarChoiceChip(star = star, selected = selectedStar == star, onClick = { onSelect(star) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 5, 6).forEach { star ->
                        StarChoiceChip(star = star, selected = selectedStar == star, onClick = { onSelect(star) })
                    }
                }
            }
        },
    )
}

@Composable
private fun StarChoiceChip(star: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFFFF1CE) else Color.White.copy(alpha = 0.34f))
            .border(1.dp, if (selected) HighlightGold.copy(alpha = 0.72f) else GlassStroke.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        StarIcons(star)
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initialValue: String,
    digitsOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        confirmButton = {
            TextButton(onClick = { onConfirm(if (digitsOnly) value.filter { it.isDigit() } else value.trim()) }) {
                Text("确认", color = HighlightGold, fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TitleInk, fontFamily = FontFamily.Serif)
            }
        },
        title = {
            Text(title, color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = if (digitsOnly) it.filter { char -> char.isDigit() } else it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                singleLine = digitsOnly,
                label = { Text(title, fontFamily = FontFamily.Serif) },
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
        },
    )
}
