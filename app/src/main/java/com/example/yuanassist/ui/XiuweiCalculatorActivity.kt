package com.example.yuanassist.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.core.XiuweiAgentInput
import com.example.yuanassist.core.XiuweiCalculationResult
import com.example.yuanassist.core.XiuweiCatalog
import com.example.yuanassist.core.XiuweiInventoryOcr
import com.example.yuanassist.core.XiuweiJob
import com.example.yuanassist.core.XiuweiCalculator
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageInfoStrip
import com.example.yuanassist.ui.subpage.SubpagePaperPanel
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.utils.SavedXiuweiAgent
import com.example.yuanassist.utils.XiuweiCalculatorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class XiuweiAgentDraft(
    val id: Int,
    val job: XiuweiJob = XiuweiJob.FENGHUO,
    val count: String = "1",
    val now: String = "1",
    val target: String = "17",
)

class XiuweiCalculatorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { XiuweiCalculatorScreen(onBack = ::finish) }
    }
}

@Composable
private fun XiuweiCalculatorScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val savedState = remember(context) { XiuweiCalculatorStore.load(context) }
    val initialAgents = remember(savedState) {
        savedState.agents.map { saved ->
            XiuweiAgentDraft(saved.id, saved.job, saved.count, saved.now, saved.target)
        }.ifEmpty { listOf(XiuweiAgentDraft(1)) }
    }
    var agents by remember(initialAgents) { mutableStateOf(initialAgents) }
    var nextAgentId by remember(initialAgents) {
        mutableStateOf((initialAgents.maxOfOrNull { it.id } ?: 0) + 1)
    }
    var inventory by remember(savedState) { mutableStateOf(savedState.inventory) }
    var selectedBitmap by remember(savedState) { mutableStateOf(savedState.previewBitmap) }
    var ocrMessage by remember(savedState) {
        mutableStateOf(if (savedState.previewBitmap == null && savedState.agents.isEmpty() && savedState.inventory.values.all { it == 0 }) {
            "尚未导入背包图片"
        } else {
            "已恢复上次修为计算数据，可继续修改"
        })
    }
    var ocrBusy by remember { mutableStateOf(false) }
    var result by remember {
        mutableStateOf(calculateResult(initialAgents, savedState.inventory))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun persistInputs(currentAgents: List<XiuweiAgentDraft> = agents, currentInventory: Map<String, Int> = inventory) {
        XiuweiCalculatorStore.saveInputs(
            context = context,
            inventory = currentInventory,
            agents = currentAgents.map { agent ->
                SavedXiuweiAgent(agent.id, agent.job, agent.count, agent.now, agent.target)
            },
        )
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            ocrBusy = true
            errorMessage = null
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bitmap == null) {
                ocrMessage = "图片读取失败，请重新选择"
                ocrBusy = false
                return@launch
            }
            // UI 预览与 OCR 工作图分离，避免 OCR/归一化过程回收或改写 Compose 正在绘制的 Bitmap。
            val previewBitmap = withContext(Dispatchers.Default) {
                createPreviewBitmap(bitmap)
            }
            selectedBitmap = previewBitmap
            withContext(Dispatchers.IO) {
                val savedPreview = previewBitmap.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    XiuweiCalculatorStore.savePreview(context, savedPreview)
                } finally {
                    savedPreview.recycle()
                }
            }
            val parsedResult = try {
                withContext(Dispatchers.Default) {
                    runCatching { XiuweiInventoryOcr.recognize(context, bitmap) }
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            parsedResult.onSuccess { parsed ->
                inventory = inventory.mapValues { (id, _) -> parsed.counts[id] ?: 0 }
                persistInputs(currentInventory = inventory)
                result = calculateResult(agents, inventory)
                ocrMessage = if (parsed.recognizedNames.isEmpty()) {
                    "未识别到材料名称，请手动填写数量"
                } else {
                    "已识别 ${parsed.recognizedNames.size} 项：${parsed.recognizedNames.joinToString("、")}"
                }
            }.onFailure { throwable ->
                ocrMessage = "OCR 失败：${throwable.message ?: "未知错误"}"
            }
            ocrBusy = false
        }
    }

    SubpageScaffold(
        title = "修为计算",
        subtitle = "识别背包材料，规划最省体力的历练",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "背包材料",
            subtitle = "上传背包截图后自动识别，数量仍可手动校正",
        ) {
            StoneStyleButton(
                text = if (ocrBusy) "正在识别…" else "上传背包图片",
                onClick = { if (!ocrBusy) imagePicker.launch("image/*") },
                enabled = !ocrBusy,
            )
            selectedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "背包截图",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = ocrMessage,
                color = BodyInk.copy(alpha = 0.82f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
            )
            XiuweiCatalog.jobs.forEach { job ->
                Text(
                    text = job.label,
                    color = TitleInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                XiuweiCatalog.materials.filter { it.job == job }.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { material ->
                            MaterialEditor(
                                material = material,
                                count = inventory[material.id] ?: 0,
                                onCountChange = { value ->
                                    inventory = inventory + (material.id to value.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(0).orZero())
                                    result = null
                                    persistInputs(currentInventory = inventory)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        SubpageSectionCard(
            title = "密探培养",
            subtitle = "可添加多个密探，目标修为按 1-17 级计算",
        ) {
            agents.forEach { agent ->
                AgentEditor(
                    agent = agent,
                    canRemove = agents.size > 1,
                    onChange = { updated ->
                        agents = agents.map { if (it.id == updated.id) updated else it }
                        result = null
                        persistInputs(currentAgents = agents)
                    },
                    onRemove = {
                        agents = agents.filterNot { it.id == agent.id }
                        result = null
                        persistInputs(currentAgents = agents)
                    },
                )
            }
            StoneStyleButton(
                text = "+ 新增密探",
                onClick = {
                    agents = agents + XiuweiAgentDraft(nextAgentId)
                    nextAgentId += 1
                    result = null
                    persistInputs(currentAgents = agents)
                },
                selected = false,
                minHeight = 42.dp,
            )
            StoneStyleButton(
                text = "计算最省体力方案",
                onClick = {
                    errorMessage = null
                    persistInputs()
                    runCatching {
                        XiuweiCalculator.calculate(
                            agents = agents.map { draft ->
                                XiuweiAgentInput(
                                    job = draft.job,
                                    count = draft.count.toIntOrNull() ?: 0,
                                    now = draft.now.toIntOrNull() ?: 0,
                                    target = draft.target.toIntOrNull() ?: 0,
                                )
                            },
                            inventory = inventory,
                            maxStage = XiuweiCatalog.stageRewards.size,
                        )
                    }.onSuccess { result = it }
                        .onFailure { errorMessage = it.message ?: "输入有误" }
                },
            )
            errorMessage?.let {
                Text(text = it, color = Color(0xFFB33A31), fontSize = 13.sp, fontFamily = FontFamily.Serif)
            }
        }

        ResultSection(
            result = result,
            onAddStageMaterials = { job, stage, times ->
                val rewards = XiuweiCatalog.stageRewards[stage - 1]
                val jobMaterials = XiuweiCatalog.materials.filter { it.job == job }
                val updatedInventory = inventory.toMutableMap()
                jobMaterials.forEachIndexed { index, material ->
                    updatedInventory[material.id] = (updatedInventory[material.id] ?: 0) + rewards[index] * times
                }
                inventory = updatedInventory
                persistInputs(currentInventory = updatedInventory)
                result = runCatching {
                    XiuweiCalculator.calculate(
                        agents = agents.map { draft ->
                            XiuweiAgentInput(
                                job = draft.job,
                                count = draft.count.toIntOrNull() ?: 0,
                                now = draft.now.toIntOrNull() ?: 0,
                                target = draft.target.toIntOrNull() ?: 0,
                            )
                        },
                        inventory = updatedInventory,
                        maxStage = XiuweiCatalog.stageRewards.size,
                    )
                }.getOrNull()
            },
        )
    }
}

@Composable
private fun MaterialEditor(
    material: com.example.yuanassist.core.XiuweiMaterial,
    count: Int,
    onCountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember(context, material.assetName) {
        runCatching {
            context.assets.open("xiuwei_materials/${material.assetName}").use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
    SubpagePaperPanel(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Wiki 素材图的圆形边缘本身带一圈深色像素，放大后再裁圆可避开外圈。
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = material.name,
                        modifier = Modifier.size(52.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Text(
                text = material.name,
                color = TitleInk,
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
            )
            SubpageTextField(
                value = count.toString(),
                onValueChange = onCountChange,
                label = "数量",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AgentEditor(
    agent: XiuweiAgentDraft,
    canRemove: Boolean,
    onChange: (XiuweiAgentDraft) -> Unit,
    onRemove: () -> Unit,
) {
    SubpagePaperPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JobSelector(
                    value = agent.job,
                    onValueChange = { onChange(agent.copy(job = it)) },
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    Text(
                        text = "删除",
                        color = Color(0xFFB33A31),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clickable(onClick = onRemove),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubpageTextField(
                    value = agent.count,
                    onValueChange = { onChange(agent.copy(count = it.filter { char -> char.isDigit() })) },
                    label = "密探数量",
                    labelFontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                SubpageTextField(
                    value = agent.now,
                    onValueChange = { onChange(agent.copy(now = it.filter { char -> char.isDigit() })) },
                    label = "当前修为",
                    labelFontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                SubpageTextField(
                    value = agent.target,
                    onValueChange = { onChange(agent.copy(target = it.filter { char -> char.isDigit() })) },
                    label = "目标修为",
                    labelFontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun JobSelector(
    value: XiuweiJob,
    onValueChange: (XiuweiJob) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(value) { mutableStateOf(false) }
    Box(modifier = modifier) {
        SubpagePaperPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        ) {
            Text(text = "属性：${value.label}  ▾", color = TitleInk, fontSize = 14.sp, fontFamily = FontFamily.Serif)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            XiuweiJob.entries.forEach { job ->
                DropdownMenuItem(
                    text = { Text(job.label) },
                    onClick = {
                        onValueChange(job)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultSection(
    result: XiuweiCalculationResult?,
    onAddStageMaterials: (XiuweiJob, Int, Int) -> Unit,
) {
    SubpageSectionCard(
        title = "计算结果",
        subtitle = "按现有材料扣除后的净需求",
    ) {
        if (result == null) {
            Text("填写密探和材料后点击计算", color = BodyInk.copy(alpha = 0.78f), fontSize = 13.sp, fontFamily = FontFamily.Serif)
            return@SubpageSectionCard
        }
        SubpageInfoStrip("总体力", "${result.totalStamina}（共 ${result.totalRuns} 次）")
        XiuweiJob.entries.forEach { job ->
            val materials = XiuweiCatalog.materials.filter { it.job == job }
            val missing = result.missingByJob.getValue(job)
            val parts = materials.mapIndexedNotNull { index, material ->
                missing[index].takeIf { it > 0 }?.let { "${material.name}×$it" }
            }
            SubpagePaperPanel {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${job.label}材料", color = TitleInk, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                    Text(if (parts.isEmpty()) "材料已足够" else parts.joinToString("、"), color = BodyInk, fontSize = 13.sp, fontFamily = FontFamily.Serif)
                    val plan = result.stagePlans.getValue(job)
                    Text(
                        if (plan.isEmpty()) "无需历练" else plan.joinToString("  ") { "历练${it.stage} × ${it.times}次" },
                        color = Color(0xFF8E4B32),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                    )
                    StageMaterialAdder(
                        job = job,
                        onAdd = onAddStageMaterials,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageMaterialAdder(
    job: XiuweiJob,
    onAdd: (XiuweiJob, Int, Int) -> Unit,
) {
    var stage by remember(job) { mutableStateOf(1) }
    var times by remember(job) { mutableStateOf("1") }
    var expanded by remember(job) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(0.95f)) {
            SubpageInfoStrip(
                label = "关卡",
                value = "历练$stage  ▾",
                modifier = Modifier.clickable { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (1..XiuweiCatalog.stageRewards.size).forEach { option ->
                    DropdownMenuItem(
                        text = { Text("历练$option") },
                        onClick = {
                            stage = option
                            expanded = false
                        },
                    )
                }
            }
        }
        SubpageTextField(
            value = times,
            onValueChange = { times = it.filter { char -> char.isDigit() } },
            label = "次数",
            labelFontSize = 12.sp,
            modifier = Modifier.weight(0.75f),
        )
        Text(
            text = "添加",
            color = Color(0xFF8E4B32),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            modifier = Modifier
                .clickable {
                    val count = times.toIntOrNull() ?: 0
                    if (count > 0) onAdd(job, stage, count)
                }
                .padding(horizontal = 5.dp, vertical = 10.dp),
        )
    }
}

private fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()

private fun calculateResult(
    agents: List<XiuweiAgentDraft>,
    inventory: Map<String, Int>,
): XiuweiCalculationResult? = runCatching {
    XiuweiCalculator.calculate(
        agents = agents.map { draft ->
            XiuweiAgentInput(
                job = draft.job,
                count = draft.count.toIntOrNull() ?: 0,
                now = draft.now.toIntOrNull() ?: 0,
                target = draft.target.toIntOrNull() ?: 0,
            )
        },
        inventory = inventory,
        maxStage = XiuweiCatalog.stageRewards.size,
    )
}.getOrNull()

private fun createPreviewBitmap(bitmap: Bitmap): Bitmap {
    val maxWidth = 720
    if (bitmap.width <= maxWidth) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
    val targetHeight = (bitmap.height * maxWidth.toFloat() / bitmap.width.toFloat()).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)
}

private fun Int?.orZero(): Int = this ?: 0
