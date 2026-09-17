package com.example.yuanassist.ui

import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.ui.subpage.SubpageThemeBridge
import com.example.yuanassist.utils.GachaAgentRarity
import com.example.yuanassist.utils.GachaAgentRarityRegistry
import com.example.yuanassist.utils.GachaArchive
import com.example.yuanassist.utils.GachaArchiveStore
import com.example.yuanassist.utils.CloudGameAgentCache
import com.example.yuanassist.utils.GachaPoolCatalog
import com.example.yuanassist.utils.GachaPoolDefinition
import com.example.yuanassist.utils.GachaPoolProgress

class GachaRecordActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_ARCHIVE_ID = "gacha_archive_id"
        private const val EXTRA_POOL_ID = "gacha_pool_id"

        fun createIntent(context: Context, archiveId: String, poolId: String): Intent =
            Intent(context, GachaRecordActivity::class.java)
                .putExtra(EXTRA_ARCHIVE_ID, archiveId)
                .putExtra(EXTRA_POOL_ID, poolId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val archiveId = intent.getStringExtra(EXTRA_ARCHIVE_ID)
        val poolId = intent.getStringExtra(EXTRA_POOL_ID)
        setContent {
            GachaRecordScreen(onBack = ::finish, archiveId = archiveId, poolId = poolId)
        }
    }
}

private data class GachaMetric(
    val label: String,
    val value: String,
    val note: String,
    val valueColor: Color = TitleInk,
)

private data class GachaAgent(
    val entryIndex: Int,
    val name: String,
    val pulls: Int,
    val isUp: Boolean,
)

private data class UpAgent(
    val name: String,
    val count: Int,
)

private data class PrePull(
    val name: String,
    val rarity: String,
    val time: String,
)

private data class CorrectionRecord(
    val name: String,
    val rarity: String,
    val pull: String,
    val prePulls: List<PrePull>,
)

private data class PullBadgeTone(
    val text: Color,
    val border: Color,
    val background: Color,
)

private val GachaPaper = Color(0xFFFFF8EA)
private val GachaWine = Color(0xFFA6453C)
private val GachaGold = Color(0xFFBB8847)
private val GachaGoldSoft = Color(0xFFDEC18B)
private val GachaLine = Color(0x52A66945)
private val GachaFive = Color(0xFFAE7D2D)
private val GachaFour = Color(0xFF75639D)
private val GachaThree = Color(0xFF4E82AA)
private val PullRibbonShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width * .92f, size.height)
    lineTo(size.width * .04f, size.height)
    close()
}

private val correctionRecords = listOf(
    CorrectionRecord(
        name = "庞德",
        rarity = "绝密",
        pull = "第 46 抽",
        prePulls = listOf(
            PrePull("陈登", "机密", "2026.09.07 20:14:28"),
            PrePull("郭解", "隐密", "2026.09.07 20:14:21"),
            PrePull("小乔", "机密", "2026.09.07 20:14:13"),
        ),
    ),
    CorrectionRecord(
        name = "陈登",
        rarity = "绝密",
        pull = "第 78 抽",
        prePulls = listOf(
            PrePull("王粲", "机密", "2026.09.08 09:32:16"),
            PrePull("许攸", "隐密", "2026.09.08 09:32:09"),
        ),
    ),
    CorrectionRecord(
        name = "杨修",
        rarity = "绝密",
        pull = "第 103 抽",
        prePulls = listOf(
            PrePull("蒯良", "隐密", "2026.09.08 10:04:42"),
            PrePull("鲁肃", "机密", "2026.09.08 10:04:33"),
            PrePull("满宠", "隐密", "2026.09.08 10:04:25"),
        ),
    ),
)

@Composable
private fun GachaRecordScreen(
    onBack: () -> Unit,
    archiveId: String?,
    poolId: String?,
) {
    val context = LocalContext.current
    val archive = remember(archiveId) {
        GachaArchiveStore.listArchives(context).firstOrNull { it.id == archiveId }
            ?: GachaArchiveStore.getSelectedArchive(context)
    }
    val pool = remember(poolId, archive.gameVariant) {
        GachaPoolCatalog.find(poolId)
            ?.takeIf { it.gameVariant == archive.gameVariant }
            ?: GachaPoolCatalog.forVariant(archive.gameVariant).first()
    }
    var progress by remember(archive.id, pool.id) {
        mutableStateOf(
            GachaArchiveStore.reconciledProgress(
                pool = pool,
                progress = GachaArchiveStore.progressFor(archive, pool.id),
            ),
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var showSecretPicker by remember { mutableStateOf(false) }
    var manualAgentName by remember { mutableStateOf<String?>(null) }
    var manualPulls by remember { mutableStateOf("") }
    var showPityEditor by remember { mutableStateOf(false) }
    var pendingDeleteAgent by remember { mutableStateOf<GachaAgent?>(null) }

    SubpageThemeBridge {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GachaTopBar(onBack = onBack, archive = archive)
            GachaHero(
                pool = pool,
                progress = progress,
                onImportImage = {
                    Toast.makeText(context, "图片导入功能暂未接入", Toast.LENGTH_SHORT).show()
                },
                onManualEntry = { showManualEntryDialog = true },
            )
            GachaTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
            )
            when (selectedTab) {
                0 -> GachaOverview(
                    pool = pool,
                    progress = progress,
                    onEditPity = { showPityEditor = true },
                )
                else -> GachaSecretRoster(
                    pool = pool,
                    progress = progress,
                    onDelete = { pendingDeleteAgent = it },
                )
            }
        }
    }

    GachaManualEntryDialog(
        visible = showManualEntryDialog,
        selectedAgentName = manualAgentName,
        pulls = manualPulls,
        onSelectAgent = { showSecretPicker = true },
        onPullsChange = { manualPulls = it },
        onSave = { agentName, pulls ->
            runCatching {
                progress = GachaArchiveStore.reconciledProgress(
                    pool = pool,
                    progress = GachaArchiveStore.appendManualSecret(
                        context = context,
                        archiveId = archive.id,
                        poolId = pool.id,
                        agentName = agentName,
                        pulls = pulls,
                    ),
                )
            }.onSuccess {
                manualAgentName = null
                manualPulls = ""
            }.exceptionOrNull()?.message
        },
        onDismiss = {
            showManualEntryDialog = false
            manualAgentName = null
            manualPulls = ""
        },
    )
    if (showSecretPicker) {
        SharedAgentPickerDialog(
            title = "选择绝密密探",
            agentFilter = GachaAgentRarityRegistry::isSecret,
            priorityAgents = pool.upAgents,
            priorityLabel = "UP",
            showSearch = true,
            showCloudAgents = true,
            gameVersion = if (pool.gameVariant.storageValue == "daihaoyuan") 0 else 1,
            initialIncludeDaihaoYuan = pool.gameVariant.storageValue == "daihaoyuan",
            syncCloudGameWithDaihaoToggle = true,
            onDismiss = { showSecretPicker = false },
            onSelect = { agentName ->
                manualAgentName = agentName
                showSecretPicker = false
            },
        )
    }
    GachaPityEditorDialog(
        visible = showPityEditor,
        initialRemainingPity = GachaArchiveStore.remainingPityFor(progress),
        onSave = { remainingPity ->
            runCatching {
                progress = GachaArchiveStore.reconciledProgress(
                    pool = pool,
                    progress = GachaArchiveStore.updateRemainingPity(
                    context = context,
                    archiveId = archive.id,
                    poolId = pool.id,
                    remainingPity = remainingPity,
                    ),
                )
            }.exceptionOrNull()?.message
        },
        onDismiss = { showPityEditor = false },
    )
    pendingDeleteAgent?.let { agent ->
        GachaDeleteSecretDialog(
            agent = agent,
            onConfirm = {
                runCatching {
                    progress = GachaArchiveStore.reconciledProgress(
                        pool = pool,
                        progress = GachaArchiveStore.deleteSecretEntry(
                            context = context,
                            archiveId = archive.id,
                            poolId = pool.id,
                            entryIndex = agent.entryIndex,
                        ),
                    )
                }.onFailure { error ->
                    Toast.makeText(context, error.message ?: "删除失败", Toast.LENGTH_SHORT).show()
                }
                pendingDeleteAgent = null
            },
            onDismiss = { pendingDeleteAgent = null },
        )
    }
}

@Composable
private fun GachaTopBar(onBack: () -> Unit, archive: GachaArchive) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GachaTopButton(
            text = "←",
            shape = CircleShape,
            onClick = onBack,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "招 募 记 录",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 3.sp,
            )
            Text(
                text = archive.name,
                modifier = Modifier.padding(top = 2.dp),
                color = BodyInk.copy(alpha = .78f),
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
            )
        }
        Spacer(modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun GachaTopButton(
    text: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(GachaPaper.copy(alpha = .55f))
            .border(1.dp, GachaLine, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontSize = if (text == "←") 22.sp else 15.sp,
            letterSpacing = if (text == "←") 0.sp else 2.sp,
        )
    }
}

@Composable
private fun GachaHero(
    pool: GachaPoolDefinition,
    progress: GachaPoolProgress,
    onImportImage: () -> Unit,
    onManualEntry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .shadow(4.dp, RoundedCornerShape(2.dp), ambientColor = Color(0xFF81563B).copy(alpha = .16f))
            .border(1.dp, GachaGoldSoft)
            .background(GachaPaper.copy(alpha = .94f))
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = .48f),
                    start = androidx.compose.ui.geometry.Offset(1.dp.toPx(), 1.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = "${pool.gameVariant.displayName} · 招募卡池",
                    color = GachaWine,
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = pool.name,
                    modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
                    color = TitleInk,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = buildAnnotatedString {
                        append("本期已记录 ")
                        withStyle(SpanStyle(color = GachaWine, fontSize = 14.sp, fontWeight = FontWeight.Bold)) {
                            append(progress.totalPulls.toString())
                        }
                        append(" 次招募\n距离保底还剩 ")
                        withStyle(SpanStyle(color = GachaWine, fontSize = 13.sp, fontWeight = FontWeight.Bold)) {
                            append(GachaArchiveStore.remainingPityFor(progress).toString())
                        }
                        append(" 抽")
                    },
                    color = BodyInk,
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
            }
            Column(
                modifier = Modifier.width(92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GachaActionButton(
                    text = "导入图片",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onImportImage,
                )
                GachaActionButton(
                    text = "手动录入",
                    secondary = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onManualEntry,
                )
            }
        }
    }
}

@Composable
private fun GachaActionButton(
    text: String,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp, topEnd = 2.dp, bottomStart = 2.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (secondary) GachaPaper.copy(alpha = .76f) else GachaPaper)
            .border(1.dp, if (secondary) GachaLine else GachaFive, shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .rotate(45f)
                .border(1.dp, if (secondary) BodyInk else GachaFive),
        )
        Text(
            text = text,
            color = if (secondary) BodyInk else Color(0xFF8D672E),
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun GachaManualEntryDialog(
    visible: Boolean,
    selectedAgentName: String?,
    pulls: String,
    onSelectAgent: () -> Unit,
    onPullsChange: (String) -> Unit,
    onSave: (String, Int) -> String?,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var errorMessage by remember(visible) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GachaPaper,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "手动录入",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = .45f))
                        .border(1.dp, GachaGoldSoft, RoundedCornerShape(6.dp))
                        .clickable(onClick = onSelectAgent)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedAgentName ?: "选择绝密密探",
                        color = if (selectedAgentName == null) BodyInk else TitleInk,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                    )
                    Text("选择", color = GachaWine, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                }
                SubpageTextField(
                    value = pulls,
                    onValueChange = {
                        onPullsChange(it.filter(Char::isDigit))
                        errorMessage = null
                    },
                    label = "本次抽数（1-40）",
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let {
                    Text(it, color = Color(0xFFB33A31), fontFamily = FontFamily.Serif, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val agentName = selectedAgentName
                    val pullCount = pulls.toIntOrNull()
                    errorMessage = when {
                        agentName.isNullOrBlank() -> "请选择绝密密探"
                        pullCount == null || pullCount !in 1..40 -> "抽数需在 1 到 40 之间"
                        else -> onSave(agentName, pullCount)
                    }
                    if (errorMessage == null) onDismiss()
                },
            ) {
                Text("保存", color = TitleInk, fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = BodyInk, fontFamily = FontFamily.Serif)
            }
        },
    )
}

@Composable
private fun GachaPityEditorDialog(
    visible: Boolean,
    initialRemainingPity: Int,
    onSave: (Int) -> String?,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var remainingPity by remember(visible, initialRemainingPity) {
        mutableStateOf(initialRemainingPity.toString())
    }
    var errorMessage by remember(visible) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GachaPaper,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "距离保底",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubpageTextField(
                    value = remainingPity,
                    onValueChange = {
                        remainingPity = it.filter { character -> character.isDigit() }.take(2)
                        errorMessage = null
                    },
                    label = "距离保底还剩几抽",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "修改后会同步更新总抽数：所有绝密抽数之和 + 40 - 剩余抽数。",
                    color = BodyInk,
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                )
                errorMessage?.let {
                    Text(it, color = GachaWine, fontFamily = FontFamily.Serif, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = remainingPity.toIntOrNull()
                    errorMessage = when {
                        value == null || value !in 0..40 -> "请输入 0 到 40 的整数"
                        else -> onSave(value)
                    }
                    if (errorMessage == null) onDismiss()
                },
            ) {
                Text("保存", color = TitleInk, fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = BodyInk, fontFamily = FontFamily.Serif)
            }
        },
    )
}

@Composable
private fun GachaDeleteSecretDialog(
    agent: GachaAgent,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GachaPaper,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = "删除绝密记录",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        },
        text = {
            Text(
                text = "确认删除 ${agent.name} 的 ${agent.pulls} 抽记录？删除后会同步更新总抽数和 UP 统计。",
                color = BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = GachaWine, fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = BodyInk, fontFamily = FontFamily.Serif)
            }
        },
    )
}

@Composable
private fun GachaTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .drawBehind {
                drawLine(
                    color = GachaLine,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 16.dp, bottom = 13.dp),
    ) {
        listOf("本期总览", "绝密密探").forEachIndexed { index, title ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { onSelectTab(index) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = title,
                    color = if (selectedTab == index) TitleInk else BodyInk,
                    fontFamily = FontFamily.Serif,
                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                )
                Box(
                    modifier = Modifier
                        .width(21.dp)
                        .height(3.dp)
                        .background(if (selectedTab == index) GachaWine else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun GachaOverview(
    pool: GachaPoolDefinition,
    progress: GachaPoolProgress,
    onEditPity: () -> Unit,
) {
    GachaSectionHeader(
        title = "本期小结",
        note = if (pool.countsTowardOffRate) "限定寻访" else "普池寻访",
    )
    val averageSecret = progress.totalPulls.takeIf { progress.secretCount > 0 }
        ?.div(progress.secretCount)
        ?.let { "$it 抽" }
        ?: "—"
    val nonUpRatio = if (pool.countsTowardOffRate) {
        progress.secretCount.takeIf { it > 0 }
            ?.let { "${progress.nonUpSecretCount * 100 / it}%" }
            ?: "—"
    } else {
        "—"
    }
    val metrics = buildList {
        add(GachaMetric("本期总抽数", progress.totalPulls.toString(), "当前卡池已记录"))
        add(GachaMetric("平均出绝密", averageSecret, "按已记录绝密计算", GachaFive))
        add(
            GachaMetric(
                "非 UP 绝密占比",
                nonUpRatio,
                if (pool.countsTowardOffRate) "已记录绝密中的非 UP" else "普池不计入歪卡统计",
                GachaWine,
            ),
        )
    }
    val metricColumns = 3
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(2.dp), ambientColor = Color(0xFF81563B).copy(alpha = .10f))
            .border(1.dp, GachaGoldSoft)
            .background(GachaPaper.copy(alpha = .68f)),
    ) {
        metrics.chunked(metricColumns).forEachIndexed { rowIndex, rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowMetrics.forEachIndexed { columnIndex, metric ->
                    GachaMetricCell(
                        metric = metric,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (columnIndex > 0) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = GachaLine,
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (rowIndex == 1) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = GachaLine,
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }

    GachaSectionHeader(
        title = "本期绝密密探",
        note = when {
            !pool.countsTowardOffRate -> "普池不配置 UP 密探"
            pool.upAgents.isEmpty() -> "暂未配置 UP 密探"
            else -> "两个 UP 分别统计"
        },
        topPadding = 20.dp,
    )
    if (pool.upAgents.isEmpty()) {
        GachaEmptyHint(
            text = if (pool.countsTowardOffRate) "该卡池暂未配置 UP 密探" else "普池不区分 UP 与歪卡",
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pool.upAgents.forEach { name ->
                UpAgentRow(
                    agent = UpAgent(name = name, count = progress.upAgentCounts.orEmpty()[name] ?: 0),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = "非 UP 绝密：${progress.nonUpSecretCount} 次",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
    GachaPityStrip(
        remainingPity = GachaArchiveStore.remainingPityFor(progress),
        onClick = onEditPity,
    )
}

@Composable
private fun GachaMetricCell(
    metric: GachaMetric,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 86.dp)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = metric.label,
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        Text(
            text = metric.value,
            modifier = Modifier.padding(top = 6.dp, bottom = 1.dp),
            color = metric.valueColor,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 27.sp,
        )
        Text(
            text = metric.note,
            color = GachaWine,
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun UpAgentRow(
    agent: UpAgent,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .drawBehind {
                drawLine(
                    color = GachaGold.copy(alpha = .48f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 1.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GachaAvatar(name = agent.name, size = 42.dp, borderColor = GachaFive)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.name,
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                text = "本期 UP · 绝密",
                modifier = Modifier.padding(top = 3.dp),
                color = BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = agent.count.toString(),
                color = GachaWine,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = "次",
                color = BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun GachaPityStrip(
    remainingPity: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 13.dp)
            .background(GachaGold.copy(alpha = .08f))
            .drawBehind {
                drawLine(
                    color = GachaGold,
                    start = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 0f),
                    end = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "距离保底还剩",
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$remainingPity 抽",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = "点击修改",
                color = GachaWine,
                fontFamily = FontFamily.Serif,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun GachaSecretRoster(
    pool: GachaPoolDefinition,
    progress: GachaPoolProgress,
    onDelete: (GachaAgent) -> Unit,
) {
    GachaSectionHeader(title = "本期绝密密探", note = "点击记录可删除")
    val secretAgents = progress.secretAgentNames.orEmpty().mapIndexed { index, name ->
        GachaAgent(
            entryIndex = index,
            name = name,
            pulls = GachaArchiveStore.secretPullCountAt(progress, index),
            isUp = pool.upAgents.any { it.trim() == name.trim() },
        )
    }.filter { agent ->
        GachaAgentRarityRegistry.resolve(agent.name) == GachaAgentRarity.SECRET
    }
    if (secretAgents.isEmpty()) {
        GachaEmptyHint(text = "该卡池暂无绝密密探记录")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .shadow(2.dp, RoundedCornerShape(2.dp), ambientColor = Color(0xFF81563B).copy(alpha = .08f))
            .drawBehind {
                drawLine(
                    color = GachaGold.copy(alpha = .48f),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = GachaGold.copy(alpha = .48f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(top = 9.dp, bottom = 4.dp),
    ) {
        secretAgents.chunked(5).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { index, agent ->
                    SecretAgentCard(
                        agent = agent,
                        onClick = { onDelete(agent) },
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (index > 0) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = GachaGold.copy(alpha = .25f),
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "本期共 ${progress.totalPulls} 抽", color = BodyInk, fontFamily = FontFamily.Serif, fontSize = 11.sp)
        Text(text = "${progress.secretCount} 位绝密密探", color = BodyInk, fontFamily = FontFamily.Serif, fontSize = 11.sp)
    }
}

@Composable
private fun SecretAgentCard(
    agent: GachaAgent,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 86.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(59.dp)
                .height(58.dp),
        ) {
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                GachaAvatar(name = agent.name, size = 53.dp, borderColor = GachaFive)
            }
            PullBadge(
                pulls = agent.pulls,
                isUp = agent.isUp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Text(
            text = agent.name,
            modifier = Modifier.padding(top = 2.dp),
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PullBadge(
    pulls: Int,
    isUp: Boolean,
    modifier: Modifier = Modifier,
) {
    val safePulls = pulls.coerceIn(0, 40)
    val badgeBackground = if (isUp) Color(0xFF477A60) else Color(0xFF695249)
    val badgeBorder = if (isUp) Color(0xFF9ED8B2) else Color(0xFFB08A68)
    val tone = when {
        safePulls <= 10 -> PullBadgeTone(
            text = Color(0xFFB8F2B5),
            border = badgeBorder,
            background = badgeBackground,
        )
        safePulls <= 20 -> PullBadgeTone(
            text = Color(0xFFFFE69A),
            border = badgeBorder,
            background = badgeBackground,
        )
        safePulls <= 30 -> PullBadgeTone(
            text = Color(0xFFFFBD84),
            border = badgeBorder,
            background = badgeBackground,
        )
        else -> PullBadgeTone(
            text = Color(0xFFFFAAA3),
            border = badgeBorder,
            background = badgeBackground,
        )
    }
    Box(
        modifier = modifier
            .rotate(-9f)
            .width(47.dp)
            .height(17.dp)
            .shadow(1.dp, PullRibbonShape, ambientColor = GachaWine.copy(alpha = .12f))
            .clip(PullRibbonShape)
            .background(tone.background)
            .border(1.dp, tone.border, PullRibbonShape)
            .drawBehind {
                drawRect(
                    color = tone.border,
                    size = androidx.compose.ui.geometry.Size(width = 2.2.dp.toPx(), height = size.height),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${safePulls}抽",
            modifier = Modifier
                .fillMaxWidth()
                .height(17.dp),
            color = tone.text,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun GachaCorrectionRecords(progress: GachaPoolProgress) {
    GachaSectionHeader(title = "修正招募记录", note = "共 ${progress.totalPulls} 笔")
    if (progress.totalPulls == 0) {
        GachaEmptyHint(text = "该卡池尚未录入招募记录")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .background(GachaWine.copy(alpha = .055f))
            .drawBehind {
                drawLine(
                    color = GachaWine,
                    start = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 0f),
                    end = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), size.height),
                    strokeWidth = 3.dp.toPx(),
                )
            }
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TitleInk, fontWeight = FontWeight.Bold)) { append("导入后可逐条确认：") }
                append("角色、品级和招募时间均可修正。\n下次导入将从最新记录时间之后继续识别，避免重复。")
            },
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
            lineHeight = 20.sp,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .border(1.dp, GachaGoldSoft)
            .background(GachaPaper.copy(alpha = .94f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "增量边界", color = BodyInk, fontFamily = FontFamily.Serif, fontSize = 11.sp)
            Column {
                Text(
                    text = "2026.09.07 20:14:32",
                    color = GachaWine,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = .5.sp,
                )
                Text(
                    text = "最新一笔记录时间（精确到秒）",
                    modifier = Modifier.padding(top = 4.dp),
                    color = BodyInk,
                    fontFamily = FontFamily.Serif,
                    fontSize = 10.sp,
                )
            }
        }
    }

    GachaSectionHeader(
        title = "最近记录",
        action = { GachaActionButton(text = "新增一条", secondary = true) },
    )
    var expandedIndex by rememberSaveable { mutableIntStateOf(0) }
    correctionRecords.forEachIndexed { index, record ->
        CorrectionRecordItem(
            record = record,
            expanded = expandedIndex == index,
            onClick = { expandedIndex = if (expandedIndex == index) -1 else index },
        )
    }
}

@Composable
private fun CorrectionRecordItem(
    record: CorrectionRecord,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
            .border(width = 0.dp, color = Color.Transparent)
            .padding(vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 51.dp)
                .drawBehind {
                    drawLine(
                        color = GachaLine,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(text = record.name, color = TitleInk, fontFamily = FontFamily.Serif, fontSize = 14.sp)
                val rarity = GachaAgentRarityRegistry.resolve(record.name)
                RarityLabel(text = rarity.displayName, color = rarityColor(rarity))
            }
            Text(
                text = record.pull,
                color = BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 10.sp,
            )
            Text(
                text = "›",
                modifier = Modifier
                    .padding(start = 9.dp)
                    .rotate(if (expanded) 90f else 0f),
                color = GachaWine,
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 9.dp)
                    .background(GachaWine.copy(alpha = .045f))
                    .drawBehind {
                        drawLine(
                            color = GachaGold.copy(alpha = .58f),
                            start = androidx.compose.ui.geometry.Offset(1.dp.toPx(), 0f),
                            end = androidx.compose.ui.geometry.Offset(1.dp.toPx(), size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                    .padding(start = 12.dp, end = 2.dp, top = 5.dp, bottom = 7.dp),
            ) {
                record.prePulls.forEachIndexed { index, prePull ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index > 0) {
                                    Modifier.drawBehind {
                                        drawLine(
                                            color = GachaGold.copy(alpha = .34f),
                                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = prePull.name, color = TitleInk, fontFamily = FontFamily.Serif, fontSize = 11.sp)
                            val rarity = GachaAgentRarityRegistry.resolve(prePull.name)
                            RarityLabel(text = rarity.displayName, color = rarityColor(rarity))
                        }
                        Text(text = prePull.time, color = BodyInk, fontFamily = FontFamily.Serif, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RarityLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 3.dp),
        color = color,
        fontFamily = FontFamily.Serif,
        fontSize = 10.sp,
    )
}

private fun rarityColor(rarity: GachaAgentRarity): Color = when (rarity) {
    GachaAgentRarity.CONFIDENTIAL -> GachaFour
    GachaAgentRarity.HIDDEN -> GachaThree
    GachaAgentRarity.SECRET -> GachaFive
}

@Composable
private fun GachaEmptyHint(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp)
            .border(1.dp, GachaGoldSoft, RoundedCornerShape(3.dp))
            .background(GachaPaper.copy(alpha = .92f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        color = BodyInk,
        fontFamily = FontFamily.Serif,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun GachaSectionHeader(
    title: String,
    note: String? = null,
    topPadding: androidx.compose.ui.unit.Dp = 18.dp,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 12.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 2.sp,
        )
        when {
            action != null -> action()
            note != null -> Text(
                text = note,
                color = BodyInk,
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GachaAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color,
) {
    val context = LocalContext.current
    var cloudAvatarVersion by remember(name) { mutableIntStateOf(0) }
    LaunchedEffect(name) {
        CloudGameAgentCache.ensureAvailable(context, listOf(name)) {
            cloudAvatarVersion++
        }
    }
    val bitmap = remember(context, name, cloudAvatarVersion) {
        runCatching {
            context.assets.open("$name.png").use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull() ?: CloudGameAgentCache.avatarBitmap(context, name)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE7D0A9))
            .border(1.dp, borderColor, CircleShape)
            .padding(2.dp)
            .border(1.dp, GachaPaper, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = name.take(1), color = TitleInk, fontFamily = FontFamily.Serif, fontSize = 14.sp)
        }
    }
}
