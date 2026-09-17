package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageChipRow
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.ui.subpage.SubpageThemeBridge
import com.example.yuanassist.utils.GachaArchive
import com.example.yuanassist.utils.GachaArchiveStore
import com.example.yuanassist.utils.GachaArchiveSummary
import com.example.yuanassist.utils.GachaAgentFrequency
import com.example.yuanassist.utils.GachaGameVariant
import com.example.yuanassist.utils.GachaPoolCatalog
import com.example.yuanassist.utils.GachaPoolCatalogStore
import com.example.yuanassist.utils.GachaPoolCoverStore
import com.example.yuanassist.utils.GachaPoolDefinition
import com.example.yuanassist.utils.GachaPoolProgress
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File

class GachaPoolDirectoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (SupabaseRepository.getCurrentUser(this) == null) {
            showLoginRequiredDialog()
            return
        }
        setContent {
            GachaPoolDirectoryScreen(
                onBack = ::finish,
                onOpenPool = { archiveId, poolId ->
                    startActivity(GachaRecordActivity.createIntent(this, archiveId, poolId))
                },
            )
        }
    }

    private fun showLoginRequiredDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("需要登录")
            .setMessage("招募记录会绑定当前账号，请先登录或创建账号后再使用。")
            .setNegativeButton("取消") { _, _ -> finish() }
            .setPositiveButton("前往登录") { _, _ ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TARGET_TAB_PROFILE),
                )
                finish()
            }
        DialogUtils.showStyledDialog(builder)
    }
}

private val PoolPaper = Color(0xFFFFF8EA)
private val PoolGold = Color(0xFFBB8847)
private val PoolGoldSoft = Color(0xFFDEC18B)
private val PoolLine = Color(0x52A66945)
private val PoolRiskRed = Color(0xFFD36B5A)
private val PoolStatsGradient = Brush.horizontalGradient(
    listOf(Color(0xFF5A4A3E), Color(0xFF393A32), Color(0xFF222C2A)),
)
private const val PoolEntryCardAspectRatio = 3f
private const val PoolEntryCardBaseWidth = 336f

@Composable
private fun GachaPoolDirectoryScreen(
    onBack: () -> Unit,
    onOpenPool: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var selectedArchive by remember { mutableStateOf(GachaArchiveStore.getSelectedArchive(context)) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var syncBusy by remember { mutableStateOf(false) }
    var catalogRefreshing by remember { mutableStateOf(false) }
    var catalogVersion by remember { mutableStateOf(0) }
    var showAgentSummary by remember(selectedArchive.id) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val archiveSummary = remember(selectedArchive) { GachaArchiveStore.summaryFor(selectedArchive) }
    val pools = remember(selectedArchive.gameVariant, catalogVersion) {
        GachaPoolCatalog.forVariant(selectedArchive.gameVariant)
    }
    val coverUrls = remember(pools) {
        pools.mapNotNull { pool ->
            (pool.coverUrl ?: gachaPoolCoverUrls[pool.id])?.takeIf { it.isNotBlank() }?.let { pool.id to it }
        }.toMap()
    }
    var localCoverFiles by remember(coverUrls) {
        mutableStateOf(GachaPoolCoverStore.localCoverFiles(context, coverUrls.keys))
    }

    LaunchedEffect(context) {
        GachaPoolCatalogStore.load(context)
        catalogVersion++
    }

    LaunchedEffect(coverUrls) {
        localCoverFiles = GachaPoolCoverStore.localCoverFiles(context, coverUrls.keys)
        GachaPoolCoverStore.downloadMissing(context, coverUrls) { poolId, file ->
            localCoverFiles = localCoverFiles + (poolId to file)
        }
    }

    fun refreshSelectedArchive() {
        selectedArchive = GachaArchiveStore.getSelectedArchive(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSelectedArchive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SubpageThemeBridge {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PoolDirectoryTopBar(
                    archive = selectedArchive,
                    onBack = onBack,
                    onSwitchArchive = { showSwitchDialog = true },
                    onCreateArchive = { showCreateDialog = true },
                    onDeleteArchive = { showDeleteDialog = true },
                    onUploadArchive = {
                        if (!syncBusy) {
                            syncBusy = true
                            SupabaseRepository.uploadGachaArchive(
                                context = context,
                                archive = selectedArchive,
                                onSuccess = {
                                    syncBusy = false
                                    Toast.makeText(context, "存档已上传到云端", Toast.LENGTH_SHORT).show()
                                },
                                onError = { message ->
                                    syncBusy = false
                                    Toast.makeText(context, "上传失败：$message", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    },
                    onDownloadArchive = { showDownloadDialog = true },
                    onRefreshPools = {
                        if (!catalogRefreshing) {
                            catalogRefreshing = true
                            SupabaseRepository.listGachaPools(
                                onSuccess = { cloudPools ->
                                    val refreshedPools = cloudPools.map { cloud ->
                                        GachaPoolDefinition(
                                            id = cloud.poolId,
                                            name = cloud.name,
                                            gameVariant = if (cloud.gameVersion == 0) {
                                                GachaGameVariant.DAIHAOYUAN
                                            } else {
                                                GachaGameVariant.RUYUAN
                                            },
                                            upAgents = cloud.upAgents,
                                            sortOrder = cloud.sortOrder,
                                            coverUrl = cloud.coverUrl,
                                            status = cloud.status,
                                        )
                                    }
                                    val result = GachaPoolCatalogStore.merge(context, refreshedPools)
                                    catalogRefreshing = false
                                    catalogVersion++
                                    val message = when {
                                        result.addedCount > 0 || result.updatedCount > 0 ->
                                            "已新增 ${result.addedCount} 个卡池，更新 ${result.updatedCount} 个卡池"
                                        else -> "当前已是最新卡池"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                },
                                onError = { message ->
                                    catalogRefreshing = false
                                    Toast.makeText(context, "拉取失败：$message", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    },
                )
            }
            item { PoolDirectoryIntro(selectedArchive) }
            item {
                ArchiveSummaryCard(
                    summary = archiveSummary,
                    agentSummaryExpanded = showAgentSummary,
                    onToggleAgentSummary = { showAgentSummary = !showAgentSummary },
                )
            }
            if (showAgentSummary) {
                item { ArchiveAgentSummaryCard(archiveSummary) }
            }

            items(
                items = pools,
                key = { it.id },
            ) { pool ->
                PoolEntryCard(
                    pool = pool,
                    progress = GachaArchiveStore.progressFor(selectedArchive, pool.id),
                    coverFile = localCoverFiles[pool.id],
                    coverUrl = coverUrls[pool.id].orEmpty(),
                    onClick = { onOpenPool(selectedArchive.id, pool.id) },
                )
            }
        }
    }

    GachaArchiveSwitchDialog(
        visible = showSwitchDialog,
        archives = GachaArchiveStore.listArchives(context),
        selectedArchiveId = selectedArchive.id,
        onSelect = { archive ->
            GachaArchiveStore.setSelectedArchiveId(context, archive.id)
            refreshSelectedArchive()
            showSwitchDialog = false
        },
        onDismiss = { showSwitchDialog = false },
    )
    GachaArchiveCreateDialog(
        visible = showCreateDialog,
        onCreate = { name, gameVariant ->
            runCatching {
                GachaArchiveStore.createArchive(context, name, gameVariant)
            }.onSuccess {
                refreshSelectedArchive()
            }.exceptionOrNull()?.message
        },
        onDismiss = { showCreateDialog = false },
    )
    GachaArchiveConfirmDialog(
        visible = showDeleteDialog,
        title = "删除当前存档",
        message = "“${selectedArchive.name}”将从本机删除，无法恢复。",
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            runCatching { GachaArchiveStore.deleteArchive(context, selectedArchive.id) }
                .onSuccess {
                    refreshSelectedArchive()
                    Toast.makeText(context, "存档已删除", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "删除失败", Toast.LENGTH_SHORT).show()
                }
            showDeleteDialog = false
        },
        onDismiss = { showDeleteDialog = false },
    )
    GachaArchiveConfirmDialog(
        visible = showDownloadDialog,
        title = "下载云端存档",
        message = "云端记录将覆盖当前本地存档“${selectedArchive.name}”。",
        confirmText = "下载并覆盖",
        onConfirm = {
            if (!syncBusy) {
                syncBusy = true
                SupabaseRepository.downloadGachaArchive(
                    context = context,
                    archiveId = selectedArchive.id,
                    onSuccess = { cloudArchive ->
                        syncBusy = false
                        runCatching { GachaArchiveStore.replaceArchive(context, cloudArchive) }
                            .onSuccess {
                                refreshSelectedArchive()
                                Toast.makeText(context, "云端存档已下载", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { error ->
                                Toast.makeText(context, error.message ?: "存档覆盖失败", Toast.LENGTH_SHORT).show()
                            }
                    },
                    onError = { message ->
                        syncBusy = false
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            showDownloadDialog = false
        },
        onDismiss = { showDownloadDialog = false },
    )
}

@Composable
private fun PoolDirectoryTopBar(
    archive: GachaArchive,
    onBack: () -> Unit,
    onSwitchArchive: () -> Unit,
    onCreateArchive: () -> Unit,
    onDeleteArchive: () -> Unit,
    onUploadArchive: () -> Unit,
    onDownloadArchive: () -> Unit,
    onRefreshPools: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PoolTopButton(text = "←", shape = CircleShape, onClick = onBack)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "招 募 记 录",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Text(
                text = "${archive.name} · ${archive.gameVariant.displayName}",
                modifier = Modifier.padding(top = 3.dp),
                color = BodyInk.copy(alpha = .78f),
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
            )
        }
        Box {
            PoolTopButton(text = "···", shape = RoundedCornerShape(5.dp), onClick = { expanded = true })
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(8.dp),
                containerColor = PoolPaper,
                tonalElevation = 0.dp,
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, PoolGoldSoft),
            ) {
                DropdownMenuItem(
                    text = { Text("切换存档", color = TitleInk, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onSwitchArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("新建存档", color = TitleInk, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onCreateArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("上传当前存档", color = TitleInk, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onUploadArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("下载当前存档", color = TitleInk, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onDownloadArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("拉取新卡池", color = TitleInk, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onRefreshPools()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除当前存档", color = PoolRiskRed, fontFamily = FontFamily.Serif) },
                    onClick = {
                        expanded = false
                        onDeleteArchive()
                    },
                )
            }
        }
    }
}

@Composable
private fun GachaArchiveConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(18.dp),
        title = { Text(title, color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = BodyInk, fontFamily = FontFamily.Serif) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) PoolRiskRed else PoolGold, fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TitleInk, fontFamily = FontFamily.Serif) }
        },
    )
}

@Composable
private fun PoolTopButton(
    text: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(PoolPaper.copy(alpha = .88f))
            .border(1.dp, PoolLine, shape)
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
private fun PoolDirectoryIntro(archive: GachaArchive) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "选择卡池",
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp,
            letterSpacing = 2.sp,
        )
        Text(
            text = "${archive.gameVariant.displayName} · 当前存档的招募记录",
            modifier = Modifier.padding(top = 4.dp),
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ArchiveSummaryCard(
    summary: GachaArchiveSummary,
    agentSummaryExpanded: Boolean,
    onToggleAgentSummary: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0xFF8F6040).copy(alpha = .13f))
            .clip(shape)
            .background(PoolPaper.copy(alpha = .94f))
            .border(1.dp, PoolGoldSoft, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (agentSummaryExpanded) "▲" else "▼",
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onToggleAgentSummary),
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        ArchiveSummaryMetric("总抽数", summary.totalPulls.toString(), Modifier.weight(1f))
        ArchiveSummaryMetric("平均出金", summary.averageSecretPulls?.let { "$it 抽" } ?: "—", Modifier.weight(1f))
        ArchiveSummaryMetric("歪卡占比", summary.nonUpSecretRatio?.let { "$it%" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun ArchiveAgentSummaryCard(summary: GachaArchiveSummary) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0xFF8F6040).copy(alpha = .13f))
            .clip(shape)
            .background(PoolPaper.copy(alpha = .94f))
            .border(1.dp, PoolGoldSoft, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArchiveAgentSummaryMetric(
            title = "抽出最多的绝密密探",
            frequency = summary.mostDrawnSecretAgent,
            modifier = Modifier.weight(1f),
        )
        ArchiveAgentSummaryMetric(
            title = "UP歪出次数最多的密探",
            frequency = summary.mostNonUpSecretAgent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ArchiveAgentSummaryMetric(
    title: String,
    frequency: GachaAgentFrequency?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = frequency?.agentNames
                ?.take(2)
                ?.joinToString("  ") { agentName -> "$agentName·${frequency.count}次" }
                ?: "—",
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArchiveSummaryMetric(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 3.dp),
            color = TitleInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun PoolEntryCard(
    pool: GachaPoolDefinition,
    progress: GachaPoolProgress,
    coverFile: File?,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PoolEntryCardAspectRatio),
    ) {
        val layoutScale = maxWidth.value / PoolEntryCardBaseWidth
        val shape = RoundedCornerShape(6.dp * layoutScale)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 7.dp * layoutScale,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0xFF81563B).copy(alpha = .16f),
                    spotColor = Color(0xFF81563B).copy(alpha = .10f),
                )
                .clip(shape)
                .background(PoolPaper.copy(alpha = .88f))
                .border(1.dp * layoutScale, PoolGoldSoft, shape)
                .clickable(onClick = onClick),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = coverFile ?: coverUrl,
                    contentDescription = pool.name,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(poolAspectRatio(pool.id)),
                    contentScale = ContentScale.FillBounds,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(PoolStatsGradient),
                ) {
                    PoolEntryStats(
                        pool = pool,
                        progress = GachaArchiveStore.reconciledProgress(pool, progress),
                        layoutScale = layoutScale,
                    )
                }
            }
        }
    }
}

@Composable
private fun PoolEntryStats(
    pool: GachaPoolDefinition,
    progress: GachaPoolProgress,
    layoutScale: Float,
    modifier: Modifier = Modifier,
) {
    val secretCount = progress.secretCount.coerceAtLeast(0)
    val notOffSecretCount = (secretCount - progress.nonUpSecretCount).coerceIn(0, secretCount)
    val averageSecretPulls = progress.totalPulls.takeIf { secretCount > 0 }
        ?.div(secretCount)
        ?.toString()
        ?: "—"
    val notOffRate = if (pool.countsTowardOffRate) {
        secretCount.takeIf { it > 0 }
            ?.let { count -> (notOffSecretCount * 100 / count).toString() }
    } else {
        null
    }
    val notOffRateColor = when {
        !pool.countsTowardOffRate -> Color(0xFFFFF8E8)
        secretCount == 0 -> Color(0xFFFFF8E8)
        notOffSecretCount * 3 >= secretCount * 2 -> PoolGold
        else -> PoolRiskRed
    }
    Column(
        modifier = modifier.padding(
            start = 4.dp * layoutScale,
            end = 8.dp * layoutScale,
            top = 8.dp * layoutScale,
            bottom = 7.dp * layoutScale,
        ),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        PoolEntryStat("总抽数", progress.totalPulls.toString(), layoutScale)
        PoolEntryStat("出金数", secretCount.toString(), layoutScale)
        PoolEntryStat("平均出金", averageSecretPulls, layoutScale)
        PoolEntryStat(
            label = "不歪占比",
            value = notOffRate?.let { "$it%" } ?: "—",
            layoutScale = layoutScale,
            emphasized = true,
            valueColor = notOffRateColor,
        )
    }
}

@Composable
private fun PoolEntryStat(
    label: String,
    value: String,
    layoutScale: Float,
    emphasized: Boolean = false,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = Color(0xFFF8E8CC).copy(alpha = .82f),
            fontFamily = FontFamily.Serif,
            fontSize = 9.sp * layoutScale,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.width(28.dp * layoutScale),
            textAlign = TextAlign.End,
            color = valueColor ?: if (emphasized) Color(0xFFF0C66F) else Color(0xFFFFF8E8),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (if (emphasized) 14.sp else 15.sp) * layoutScale,
            maxLines = 1,
        )
    }
}

private const val GachaPoolCoverCdn = "https://img.cdn1.vip/i/"

private val gachaPoolCoverFiles = mapOf(
    "chengzhi_yunfu" to "6aa4dfbcb97a7_1789190076.webp",
    "daihao_001" to "6aa4dfbe33fe7_1789190078.webp",
    "daihao_002" to "6aa4dfbf9a517_1789190079.webp",
    "daihao_003" to "6aa4dfc11c498_1789190081.webp",
    "daihao_004" to "6aa4dfc2875fa_1789190082.webp",
    "daihao_005" to "6aa4dfc990847_1789190089.webp",
    "daihao_006" to "6aa4dfcaf1f40_1789190090.webp",
    "daihao_007" to "6aa4dfcc925fd_1789190092.webp",
    "daihao_008" to "6aa4dfce1e5ec_1789190094.webp",
    "daihao_009" to "6aa4dfd6c47e8_1789190102.webp",
    "daihao_010" to "6aa4dfde4a95e_1789190110.webp",
    "daihao_011" to "6aa4dfdfa5dcc_1789190111.webp",
    "daihao_012" to "6aa4dfe110565_1789190113.webp",
    "daihao_013" to "6aa4dfe28d1ba_1789190114.webp",
    "daihao_014" to "6aa4dfe40b601_1789190116.webp",
    "daihao_015" to "6aa4dfeb8332a_1789190123.webp",
    "daihao_016" to "6aa4dfed23da2_1789190125.webp",
    "daihao_017" to "6aa4dfee8c2d4_1789190126.webp",
    "daihao_018" to "6aa4dff003729_1789190128.webp",
    "daihao_019" to "6aa4dff17f6ec_1789190129.webp",
    "daihao_020" to "6aa4dff913fba_1789190137.webp",
    "daihao_021" to "6aa4dffb87644_1789190139.webp",
    "daihao_022" to "6aa4e027b9f6a_1789190183.webp",
    "daihao_023" to "6aa4dffdb1700_1789190141.webp",
    "daihao_024" to "6aa4dfff22779_1789190143.webp",
    "daihao_025" to "6aa4e0069ab65_1789190150.webp",
    "daihao_026" to "6aa4e039da44e_1789190201.webp",
    "daihao_027" to "6aa4e008a1362_1789190152.webp",
    "daihao_028" to "6aa4e00a1a049_1789190154.webp",
    "daihao_029" to "6aa4e00b88f5a_1789190155.webp",
    "daihao_030" to "6aa4e012d65ff_1789190162.webp",
    "daihao_031" to "6aa4e0144eaf4_1789190164.webp",
    "daihao_032" to "6aa4e015a2d68_1789190165.webp",
    "daihao_033" to "6aa4e01701901_1789190167.webp",
    "daihao_034" to "6aa4e01865755_1789190168.webp",
    "daihao_035" to "6aa4e01fca23f_1789190175.webp",
    "daihao_036" to "6aa4e02133d96_1789190177.webp",
    "daihao_037" to "6aa4e022561d4_1789190178.webp",
    "daihao_038" to "6aa4e023d7c33_1789190179.webp",
    "daihao_039" to "6aa4e025406a9_1789190181.webp",
    "daihao_040" to "6aa4e02cb9da8_1789190188.webp",
    "daihao_041" to "6aa4e02e32dfb_1789190190.webp",
    "daihao_042" to "6aa4e02f94b7f_1789190191.webp",
    "daihao_043" to "6aa4e030db63a_1789190192.webp",
    "daihao_044" to "6aa4e104bf1be_1789190404.webp",
    "daihao_045" to "6aa4e03928a57_1789190201.webp",
    "daihao_046" to "6aa4e03a9150e_1789190202.webp",
    "daihao_047" to "6aa4e03c1b250_1789190204.webp",
    "daihao_048" to "6aa4e03dab033_1789190205.webp",
    "daihao_049" to "6aa4e03f0eec6_1789190207.webp",
    "daihao_050" to "6aa4e0468665a_1789190214.webp",
    "daihao_051" to "6aa4e04803f4f_1789190216.webp",
    "daihao_052" to "6aa4e0499d65e_1789190217.webp",
    "ruyuan_001" to "6aa4dfbe33fe7_1789190078.webp",
    "ruyuan_002" to "6aa4e04c02841_1789190220.webp",
    "ruyuan_003" to "6aa4e04803f4f_1789190216.webp",
    "ruyuan_004" to "6aa4e05449fb3_1789190228.webp",
    "ruyuan_005" to "6aa4e055c6d21_1789190229.webp",
    "ruyuan_006" to "6aa4e0574073c_1789190231.webp",
    "ruyuan_007" to "6aa4e01701901_1789190167.webp",
    "ruyuan_008" to "6aa4e05f9fb8c_1789190239.webp",
    "ruyuan_009" to "6aa4e06100745_1789190241.webp",
    "ruyuan_010" to "6aa4e0622c269_1789190242.webp",
    "ruyuan_011" to "6aa4e02e32dfb_1789190190.webp",
    "ruyuan_012" to "6aa4e012d65ff_1789190162.webp",
    "ruyuan_013" to "6aa4dffb87644_1789190139.webp",
    "ruyuan_014" to "6aa4e0069ab65_1789190150.webp",
    "ruyuan_015" to "6aa4e02133d96_1789190177.webp",
    "ruyuan_016" to "6aa4e027b9f6a_1789190183.webp",
    "ruyuan_017" to "6aa4e06dd822a_1789190253.webp",
    "ruyuan_018" to "6aa4e0754e623_1789190261.webp",
    "ruyuan_019" to "6aa4e01fca23f_1789190175.webp",
    "ruyuan_020" to "6aa4dfee8c2d4_1789190126.webp",
    "ruyuan_021" to "6aa4e0789489f_1789190264.webp",
    "ruyuan_022" to "6aa4e07aef2be_1789190266.webp",
    "ruyuan_023" to "6aa4e082660be_1789190274.webp",
    "ruyuan_024" to "6aa4e083e9451_1789190275.webp",
    "ruyuan_025" to "6aa4e085532eb_1789190277.webp",
    "ruyuan_026" to "6aa4e086b634b_1789190278.webp",
    "ruyuan_027" to "6aa4dfc2875fa_1789190082.webp",
    "ruyuan_028" to "6aa4e08f06ee1_1789190287.webp",
    "ruyuan_029" to "6aa4e09067474_1789190288.webp",
    "ruyuan_030" to "6aa4e091e3cb8_1789190289.webp",
    "ruyuan_031" to "6aa4e0933f4ed_1789190291.webp",
    "ruyuan_032" to "6aa4e008a1362_1789190152.webp",
    "ruyuan_033" to "6aa4dfbf9a517_1789190079.webp",
    "ruyuan_034" to "6aa4e09c216df_1789190300.webp",
    "wanghou_binde" to "6aa4e09d83e40_1789190301.webp",
    "zhoulang_zhizhang" to "6aa4e09ea8214_1789190302.webp",
)

private val gachaPoolCoverUrls = gachaPoolCoverFiles.mapValues { (_, fileName) ->
    GachaPoolCoverCdn + fileName
}

private fun poolAspectRatio(poolId: String): Float = when (poolId) {
    GachaPoolCatalog.ZHOULANG_ZHIZHANG,
    GachaPoolCatalog.CHENGZHI_YUNFU,
    GachaPoolCatalog.QUEYUE_LINGFENG -> 2048f / 930f
    in "ruyuan_001".."ruyuan_034",
    in "daihao_002".."daihao_052" -> 2048f / 930f
    else -> 1862f / 845f
}

@Composable
private fun GachaArchiveSwitchDialog(
    visible: Boolean,
    archives: List<GachaArchive>,
    selectedArchiveId: String,
    onSelect: (GachaArchive) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text("切换存档", color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                archives.forEach { archive ->
                    SubpageRadioOption(
                        text = archive.name,
                        subtitle = archive.gameVariant.displayName,
                        selected = archive.id == selectedArchiveId,
                        onClick = { onSelect(archive) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontFamily = FontFamily.Serif) }
        },
    )
}

@Composable
private fun GachaArchiveCreateDialog(
    visible: Boolean,
    onCreate: (String, GachaGameVariant) -> String?,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var name by remember(visible) { mutableStateOf("") }
    var variant by remember(visible) { mutableStateOf(GachaGameVariant.RUYUAN) }
    var errorMessage by remember(visible) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text("新建存档", color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SubpageTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = "存档名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("游戏版本", color = BodyInk, fontFamily = FontFamily.Serif, fontSize = 12.sp)
                SubpageChipRow(
                    items = GachaGameVariant.entries.map { it.displayName },
                    selectedItem = variant.displayName,
                    onSelect = { selected ->
                        variant = GachaGameVariant.entries.first { it.displayName == selected }
                    },
                )
                errorMessage?.let {
                    Text(it, color = Color(0xFFB33A31), fontFamily = FontFamily.Serif, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    errorMessage = onCreate(name, variant)
                    if (errorMessage == null) onDismiss()
                },
            ) {
                Text("创建", fontFamily = FontFamily.Serif)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontFamily = FontFamily.Serif) }
        },
    )
}
