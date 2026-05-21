package com.example.yuanassist.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.yuanassist.R
import com.example.yuanassist.utils.MyStoneCell
import com.example.yuanassist.utils.MyStoneRecord
import com.example.yuanassist.utils.MyStoneRow
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.StoneStat
import com.example.yuanassist.utils.StoneOcrCoordinator
import com.example.yuanassist.utils.StoneOcrMode
import com.example.yuanassist.utils.StonePaddleLocalRecognizer
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.StoneOcrParser
import com.example.yuanassist.utils.applyYuanInputStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyStoneActivity : AppCompatActivity() {

    private data class DistributionItem(
        val title: String,
        val value: String
    )

    private data class PartitionPreviewResult(
        val bitmap: Bitmap?,
        val errorMessage: String? = null,
        val throwable: Throwable? = null,
    )

    private data class StoneDisplayCard(
        val name: String,
        val category: String,
        val totalCount: Int,
        val levelItems: List<DistributionItem>,
        val cultivationItems: List<DistributionItem>
    )

    private lateinit var emptyView: TextView
    private lateinit var contentView: View
    private lateinit var titleView: TextView
    private lateinit var statsSummaryView: TextView
    private lateinit var statsCardsContainer: LinearLayout
    private lateinit var archiveNameView: TextView
    private lateinit var archiveSwitchButton: TextView
    private lateinit var archiveCreateButton: TextView
    private lateinit var archiveRenameButton: TextView
    private lateinit var mainTypeView: TextView
    private lateinit var supportTypeView: TextView
    private lateinit var addStoneButton: TextView

    private lateinit var longSectionHeading: View
    private lateinit var longSectionSubtitle: TextView
    private lateinit var longSectionRecognizeButton: TextView
    private lateinit var longSectionPartitionButton: TextView
    private lateinit var longSectionToggle: TextView
    private lateinit var longSectionContent: View

    private lateinit var looseSectionHeading: View
    private lateinit var looseSectionSubtitle: TextView
    private lateinit var looseSectionRecognizeButton: TextView
    private lateinit var looseSectionPartitionButton: TextView
    private lateinit var looseSectionToggle: TextView
    private lateinit var looseSectionContent: View

    private lateinit var rowsSectionHeading: View
    private lateinit var rowsSectionSubtitle: TextView
    private lateinit var rowsSectionToggle: TextView
    private lateinit var rowsSectionContent: View
    private lateinit var rowHintView: TextView
    private lateinit var rowsContainer: LinearLayout

    private lateinit var longImagesContainer: LinearLayout
    private lateinit var looseImagesContainer: LinearLayout
    private lateinit var longPartitionPreviewView: ImageView
    private lateinit var loosePartitionPreviewsContainer: LinearLayout

    private var currentRecord: MyStoneRecord? = null
    private var currentImageFiles: List<File> = emptyList()
    private var currentLongImageFiles: List<File> = emptyList()
    private var currentLooseImageFiles: List<File> = emptyList()
    private var currentRows: MutableList<MyStoneRow> = mutableListOf()
    private var currentArchiveId: String = MyStoneStore.DEFAULT_ARCHIVE_ID
    private var currentStoneType: String = MyStoneStore.TYPE_MAIN
    private var isLongExpanded = false
    private var isLooseExpanded = false
    private var isRowsExpanded = false
    private var longOcrProcessingStoneType: String? = null
    private var looseOcrProcessingStoneType: String? = null
    private var isLongPartitionProcessing = false
    private var isLoosePartitionProcessing = false
    private var longPartitionPreviewJob: Job? = null
    private var loosePartitionPreviewJob: Job? = null
    private var longPartitionPreviewBitmap: Bitmap? = null
    private val loosePartitionPreviewBitmaps = mutableListOf<Bitmap>()
    private var longPartitionPreviewKey: String? = null
    private var loosePartitionPreviewKey: String? = null
    private val expandedStatCards = mutableSetOf<String>()
    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private val mainStoneNames = setOf(
        "武曲", "天机", "破军", "天同", "天梁", "贪狼", "天府",
        "天相", "太阳", "巨门", "太阴", "紫微", "七杀", "廉贞"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_stone)

        val backButton = findViewById<ImageView>(R.id.btn_back_my_stone)
        val header = findViewById<View>(R.id.layout_my_stone_header)
        val topSpace = findViewById<View>(R.id.view_my_stone_status_space)

        emptyView = findViewById(R.id.tv_my_stone_empty)
        contentView = findViewById(R.id.layout_my_stone_content)
        titleView = findViewById(R.id.tv_my_stone_updated_at)
        statsSummaryView = findViewById(R.id.tv_my_stone_stats_summary)
        statsCardsContainer = findViewById(R.id.layout_my_stone_stats_cards)
        archiveNameView = findViewById(R.id.tv_my_stone_archive_name)
        archiveSwitchButton = findViewById(R.id.btn_my_stone_archive_switch)
        archiveCreateButton = findViewById(R.id.btn_my_stone_archive_create)
        archiveRenameButton = findViewById(R.id.btn_my_stone_archive_rename)
        mainTypeView = findViewById(R.id.tv_my_stone_type_main)
        supportTypeView = findViewById(R.id.tv_my_stone_type_support)
        addStoneButton = findViewById(R.id.btn_my_stone_add)

        longSectionHeading = findViewById(R.id.layout_my_stone_long_heading)
        longSectionSubtitle = findViewById(R.id.tv_my_stone_long_subtitle)
        longSectionRecognizeButton = findViewById(R.id.btn_recognize_my_stone_long)
        longSectionPartitionButton = findViewById(R.id.btn_partition_my_stone_long)
        longSectionToggle = findViewById(R.id.btn_toggle_my_stone_long)
        longSectionContent = findViewById(R.id.layout_my_stone_long_content)

        looseSectionHeading = findViewById(R.id.layout_my_stone_loose_heading)
        looseSectionSubtitle = findViewById(R.id.tv_my_stone_loose_subtitle)
        looseSectionRecognizeButton = findViewById(R.id.btn_recognize_my_stone_loose)
        looseSectionPartitionButton = findViewById(R.id.btn_partition_my_stone_loose)
        looseSectionToggle = findViewById(R.id.btn_toggle_my_stone_loose)
        looseSectionContent = findViewById(R.id.layout_my_stone_loose_content)

        rowsSectionHeading = findViewById(R.id.layout_my_stone_rows_heading)
        rowsSectionSubtitle = findViewById(R.id.tv_my_stone_rows_subtitle)
        rowsSectionToggle = findViewById(R.id.btn_toggle_my_stone_rows)
        rowsSectionContent = findViewById(R.id.layout_my_stone_rows_content)
        rowHintView = findViewById(R.id.tv_my_stone_rows_hint)
        rowsContainer = findViewById(R.id.layout_my_stone_rows)

        longImagesContainer = findViewById(R.id.layout_my_stone_long_images)
        looseImagesContainer = findViewById(R.id.layout_my_stone_loose_images)
        longPartitionPreviewView = findViewById(R.id.iv_my_stone_long_partition_preview)
        loosePartitionPreviewsContainer = findViewById(R.id.layout_my_stone_loose_partition_previews)
        currentStoneType = MyStoneStore.getSelectedType(this)

        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topSpace.updateLayoutParams {
                height = statusBarTop / 2
            }
            insets
        }
        ViewCompat.requestApplyInsets(header)

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        longSectionHeading.setOnClickListener {
            setLongExpanded(!isLongExpanded)
        }
        longSectionPartitionButton.setOnClickListener {
            triggerLongPartitionPreview()
        }
        longSectionRecognizeButton.setOnClickListener {
            triggerStoneOcrIfNeeded()
        }
        looseSectionHeading.setOnClickListener {
            setLooseExpanded(!isLooseExpanded)
        }
        looseSectionRecognizeButton.setOnClickListener {
            triggerLooseStoneOcrIfNeeded()
        }
        looseSectionPartitionButton.setOnClickListener {
            triggerLoosePartitionPreview()
        }
        rowsSectionHeading.setOnClickListener {
            setRowsExpanded(!isRowsExpanded)
        }
        archiveSwitchButton.setOnClickListener { showArchiveSwitchDialog() }
        archiveCreateButton.setOnClickListener { showCreateArchiveDialog() }
        archiveRenameButton.setOnClickListener { showRenameArchiveDialog() }
        mainTypeView.setOnClickListener { switchStoneType(MyStoneStore.TYPE_MAIN) }
        supportTypeView.setOnClickListener { switchStoneType(MyStoneStore.TYPE_SUPPORT) }
        addStoneButton.setOnClickListener { showAddStoneDialog() }
    }

    override fun onResume() {
        super.onResume()
        renderStoneRecord()
    }

    private fun renderStoneRecord() {
        MyStoneStore.migrateLegacyMainRecordIfNeeded(this)
        currentArchiveId = MyStoneStore.getSelectedArchiveId(this)
        currentStoneType = MyStoneStore.getSelectedType(this)

        currentStoneType = MyStoneStore.normalizeType(currentStoneType)
        renderArchiveSelection()
        renderTypeSelection()

        val record = MyStoneStore.loadRecord(this, currentStoneType, currentArchiveId)
        val imageFiles = record?.let { MyStoneStore.imageFiles(this, currentStoneType, it, currentArchiveId) }.orEmpty()
        val longImageFiles = record?.let { MyStoneStore.longImageFiles(this, currentStoneType, it, currentArchiveId) }.orEmpty()
        val looseImageFiles = record?.let { MyStoneStore.looseImageFiles(this, currentStoneType, it, currentArchiveId) }.orEmpty()
        currentRecord = record
        currentImageFiles = imageFiles
        currentLongImageFiles = longImageFiles
        currentLooseImageFiles = looseImageFiles

        emptyView.isVisible = false
        contentView.isVisible = true

        titleView.text = if (record != null) {
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(Date(record.updatedAt))
            "最近更新：$timeText"
        } else {
            "最近更新：${archiveNameView.text}暂无${MyStoneStore.displayName(currentStoneType)}结果"
        }

        currentRows = record?.rows?.map { row ->
            MyStoneRow(
                cells = row.cells.map { cell ->
                    MyStoneCell(
                        level = StoneOcrParser.normalizeLevel(cell.level),
                        name = StoneOcrParser.normalizeToken(cell.name)
                    )
                }.toMutableList()
            )
        }?.toMutableList() ?: mutableListOf()

        if (longPartitionPreviewKey != currentLongPartitionKey()) {
            clearLongPartitionPreview()
        }
        if (loosePartitionPreviewKey != currentLoosePartitionKey()) {
            clearLoosePartitionPreview()
        }

        renderStats()
        updateSectionSubtitles()
        renderLongPartitionButton()
        renderLongRecognizeButton()
        renderLooseRecognizeButton()
        renderLoosePartitionButton()
        setLongExpanded(isLongExpanded)
        setLooseExpanded(isLooseExpanded)
        setRowsExpanded(isRowsExpanded)
    }

    private fun renderStats() {
        val stats = StoneOcrParser.aggregate(currentRows, currentStoneType)
        val cards = buildStoneDisplayCards(stats)
        val unresolvedRows = currentRows.count { !StoneOcrParser.isRowResolved(it, currentStoneType) }
        val typeLabel = MyStoneStore.displayName(currentStoneType)

        statsSummaryView.text = if (cards.isEmpty()) {
            "${typeLabel}暂未完成统计\n当前没有完整有效的统计结果，请先检查下方红色行并补全。"
        } else {
            buildString {
                append("已收录 ${cards.size} 种星石，合计 ${cards.sumOf { it.totalCount }} 颗")
                if (unresolvedRows > 0) {
                    append("\n")
                    append("仍有 ${unresolvedRows} 行待修正，修正后统计会自动更新。")
                }
            }
        }

        renderStatCards(cards)
    }

    private fun setLongExpanded(expanded: Boolean) {
        val canExpand = currentLongImageFiles.isNotEmpty()
        isLongExpanded = expanded && canExpand
        longSectionToggle.text = if (isLongExpanded) "收起" else "展开"
        longSectionContent.isVisible = isLongExpanded

        if (isLongExpanded) {
            renderLongImages()
        } else {
            longImagesContainer.removeAllViews()
            renderLongPartitionPreviewView()
        }
    }

    private fun setLooseExpanded(expanded: Boolean) {
        val canExpand = currentLooseImageFiles.isNotEmpty()
        isLooseExpanded = expanded && canExpand
        looseSectionToggle.text = if (isLooseExpanded) "收起" else "展开"
        looseSectionContent.isVisible = isLooseExpanded

        if (isLooseExpanded) {
            renderLooseImages()
        } else {
            looseImagesContainer.removeAllViews()
            renderLoosePartitionPreviewView()
        }
    }

    private fun setRowsExpanded(expanded: Boolean) {
        isRowsExpanded = expanded
        rowsSectionToggle.text = if (expanded) "收起" else "展开"
        rowsSectionContent.isVisible = expanded

        if (expanded) {
            renderRows()
        } else {
            rowsContainer.removeAllViews()
        }
    }

    private fun updateSectionSubtitles() {
        val typeLabel = MyStoneStore.displayName(currentStoneType)
        longSectionSubtitle.text = if (currentLongImageFiles.isEmpty()) {
            "暂无${typeLabel}长图"
        } else {
            "共 ${currentLongImageFiles.size} 张${typeLabel}长图，默认收起"
        }
        looseSectionSubtitle.text = if (currentLooseImageFiles.isEmpty()) {
            "暂无${typeLabel}散图"
        } else {
            "共 ${currentLooseImageFiles.size} 张${typeLabel}散图，默认收起"
        }

        val invalidRowCount = currentRows.count { !StoneOcrParser.isRowResolved(it, currentStoneType) }
        rowsSectionSubtitle.text = if (currentRows.isEmpty()) {
            "暂无${typeLabel}原位置数据"
        } else {
            "共 ${currentRows.size} 行${typeLabel}原位置数据，待修正 ${invalidRowCount} 行"
        }
    }

    private fun renderLongRecognizeButton() {
        val isProcessing = longOcrProcessingStoneType == currentStoneType
        val shouldShow = currentLongImageFiles.isNotEmpty()
        longSectionRecognizeButton.isVisible = shouldShow || isProcessing
        longSectionRecognizeButton.isEnabled = !isProcessing
        longSectionRecognizeButton.text = if (isProcessing) "识别中..." else "云端识别"
        longSectionRecognizeButton.setBackgroundResource(
            if (isProcessing) R.drawable.btn_stone_light else R.drawable.btn_dark_gold
        )
        longSectionRecognizeButton.setTextColor(
            if (isProcessing) Color.parseColor("#9A6435") else Color.parseColor("#1A1A1A")
        )
    }

    private fun renderLooseRecognizeButton() {
        val isProcessing = looseOcrProcessingStoneType == currentStoneType
        val shouldShow = currentLooseImageFiles.isNotEmpty()
        looseSectionRecognizeButton.isVisible = shouldShow || isProcessing
        looseSectionRecognizeButton.isEnabled = !isProcessing
        looseSectionRecognizeButton.text = if (isProcessing) "识别中..." else "识别"
        looseSectionRecognizeButton.setBackgroundResource(
            if (isProcessing) R.drawable.btn_stone_light else R.drawable.btn_dark_gold
        )
        looseSectionRecognizeButton.setTextColor(
            if (isProcessing) Color.parseColor("#9A6435") else Color.parseColor("#1A1A1A")
        )
    }

    private fun renderLongPartitionButton() {
        val shouldShow = currentLongImageFiles.isNotEmpty()
        longSectionPartitionButton.isVisible = shouldShow || isLongPartitionProcessing
        longSectionPartitionButton.isEnabled = !isLongPartitionProcessing
        longSectionPartitionButton.text = if (isLongPartitionProcessing) "划分中..." else "划分"
        longSectionPartitionButton.setBackgroundResource(
            if (isLongPartitionProcessing) R.drawable.btn_stone_light else R.drawable.btn_stone_soft_selected
        )
        longSectionPartitionButton.setTextColor(Color.parseColor("#75322D"))
    }

    private fun renderLoosePartitionButton() {
        val shouldShow = currentLooseImageFiles.isNotEmpty()
        looseSectionPartitionButton.isVisible = shouldShow || isLoosePartitionProcessing
        looseSectionPartitionButton.isEnabled = !isLoosePartitionProcessing
        looseSectionPartitionButton.text = if (isLoosePartitionProcessing) "划分中..." else "划分"
        looseSectionPartitionButton.setBackgroundResource(
            if (isLoosePartitionProcessing) R.drawable.btn_stone_light else R.drawable.btn_stone_soft_selected
        )
        looseSectionPartitionButton.setTextColor(Color.parseColor("#75322D"))
    }

    private fun triggerStoneOcrIfNeeded() {
        triggerLongStoneOcrIfNeeded()
    }

    private fun triggerLongStoneOcrIfNeeded() {
        if (longOcrProcessingStoneType != null || looseOcrProcessingStoneType != null) return
        if (currentLongImageFiles.isEmpty()) {
            Toast.makeText(this, "当前没有可识别的长图", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasCompletedLooseLocalOcr()) {
            Toast.makeText(this, "请先尝试结果散图的本地OCR识别", Toast.LENGTH_SHORT).show()
            return
        }

        val requestStoneType = currentStoneType
        val requestArchiveId = currentArchiveId
        val imageFiles = currentLongImageFiles.toList()
        longOcrProcessingStoneType = requestStoneType
        renderLongRecognizeButton()
        Toast.makeText(this, "正在通过云端OCR统计${MyStoneStore.displayName(requestStoneType)}长图...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = StoneOcrCoordinator.importStoneImages(
                    context = this@MyStoneActivity,
                    stoneType = requestStoneType,
                    archiveId = requestArchiveId,
                    imageFiles = imageFiles,
                    mode = StoneOcrMode.CLOUD,
                    deviceId = deviceId,
                )

                if (result.hasPendingRows) {
                    Toast.makeText(this@MyStoneActivity, "长图 OCR 已导入，可在我的星石中修正红色行", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MyStoneActivity, "长图 OCR 统计完成", Toast.LENGTH_LONG).show()
                }

                if (currentStoneType == requestStoneType && currentArchiveId == requestArchiveId) {
                    renderStoneRecord()
                }
            } catch (t: Throwable) {
                Toast.makeText(this@MyStoneActivity, "长图 OCR 统计失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (longOcrProcessingStoneType == requestStoneType) {
                    longOcrProcessingStoneType = null
                }
                renderLongRecognizeButton()
            }
        }
    }

    private fun triggerLooseStoneOcrIfNeeded() {
        if (longOcrProcessingStoneType != null || looseOcrProcessingStoneType != null) return
        if (currentLooseImageFiles.isEmpty()) {
            Toast.makeText(this, "当前没有可识别的散图", Toast.LENGTH_SHORT).show()
            return
        }

        val requestStoneType = currentStoneType
        val requestArchiveId = currentArchiveId
        val imageFiles = currentLooseImageFiles.toList()
        looseOcrProcessingStoneType = requestStoneType
        renderLooseRecognizeButton()
        Toast.makeText(this, "正在通过本地OCR统计${MyStoneStore.displayName(requestStoneType)}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = StoneOcrCoordinator.importStoneImages(
                    context = this@MyStoneActivity,
                    stoneType = requestStoneType,
                    archiveId = requestArchiveId,
                    imageFiles = imageFiles,
                    mode = StoneOcrMode.LOCAL,
                    deviceId = deviceId,
                )

                Toast.makeText(
                    this@MyStoneActivity,
                    "统计已完成，如果结果错误很多，请使用结果长图的云端OCR",
                    Toast.LENGTH_LONG
                ).show()

                if (currentStoneType == requestStoneType && currentArchiveId == requestArchiveId) {
                    renderStoneRecord()
                }
            } catch (t: Throwable) {
                Toast.makeText(this@MyStoneActivity, "星石 OCR 统计失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (looseOcrProcessingStoneType == requestStoneType) {
                    looseOcrProcessingStoneType = null
                }
                renderLooseRecognizeButton()
            }
        }
    }

    private fun hasCompletedLooseLocalOcr(): Boolean {
        return (currentRecord?.looseOcrCompletedAt ?: 0L) > 0L
    }

    private fun renderLongImages() {
        if (currentLongImageFiles.isEmpty()) return
        val imageVersion = currentRecord?.updatedAt ?: System.currentTimeMillis()
        renderImageGroup(
            container = longImagesContainer,
            files = currentLongImageFiles,
            keyPrefix = "long",
            imageVersion = imageVersion,
        )
        renderLongPartitionPreviewView()
    }

    private fun renderLooseImages() {
        if (currentLooseImageFiles.isEmpty()) return
        val imageVersion = currentRecord?.updatedAt ?: System.currentTimeMillis()
        renderImageGroup(
            container = looseImagesContainer,
            files = currentLooseImageFiles,
            keyPrefix = "loose",
            imageVersion = imageVersion,
        )
        renderLoosePartitionPreviewView()
    }

    private fun renderImageGroup(
        container: LinearLayout,
        files: List<File>,
        keyPrefix: String,
        imageVersion: Long,
    ) {
        container.removeAllViews()
        if (files.isEmpty()) return

        files.forEachIndexed { index, file ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dp(12)
                    }
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }
            container.addView(imageView)
            Glide.with(this)
                .load(file)
                .signature(ObjectKey("stone_${currentArchiveId}_${currentStoneType}_${imageVersion}_${keyPrefix}_${index}"))
                .into(imageView)
        }
    }

    private fun triggerLongPartitionPreview() {
        if (currentLongImageFiles.isEmpty()) {
            Toast.makeText(this, "当前没有可划分的长图", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isLongExpanded) {
            setLongExpanded(true)
        }
        val requestKey = currentLongPartitionKey() ?: return
        if (isLongPartitionProcessing) return
        longPartitionPreviewJob?.cancel()
        isLongPartitionProcessing = true
        renderLongPartitionButton()
        longPartitionPreviewView.isVisible = false
        longPartitionPreviewJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val source = decodePartitionBitmap(currentLongImageFiles.firstOrNull()) ?: return@withContext null
                try {
                    val analysis = StonePaddleLocalRecognizer.analyzeForDebug(this@MyStoneActivity, source)
                    PartitionPreviewResult(
                        bitmap = StonePaddleLocalRecognizer.drawDebugPreview(source, analysis)
                    )
                } catch (t: Throwable) {
                    PartitionPreviewResult(
                        bitmap = null,
                        errorMessage = t.message ?: t::class.java.simpleName,
                        throwable = t,
                    )
                } finally {
                    source.recycle()
                }
            } ?: PartitionPreviewResult(bitmap = null, errorMessage = "结果图解码失败")
            isLongPartitionProcessing = false
            renderLongPartitionButton()
            val preview = result.bitmap
            if (preview != null && requestKey == currentLongPartitionKey()) {
                clearLongPartitionPreview()
                longPartitionPreviewBitmap = preview
                longPartitionPreviewKey = requestKey
                renderLongPartitionPreviewView()
            } else {
                preview?.recycle()
                if (longPartitionPreviewBitmap == null) {
                    longPartitionPreviewView.isVisible = false
                }
                if (preview == null) {
                    val reason = result.errorMessage ?: "未知原因"
                    result.throwable?.let { throwable ->
                        RunLogger.e("星石划分图生成失败：$reason", throwable)
                    } ?: RunLogger.e("星石划分图生成失败：$reason")
                    Toast.makeText(this@MyStoneActivity, "长图划分失败：$reason", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun triggerLoosePartitionPreview() {
        if (currentLooseImageFiles.isEmpty()) {
            Toast.makeText(this, "当前没有可划分的散图", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isLooseExpanded) {
            setLooseExpanded(true)
        }
        if (isLoosePartitionProcessing) return
        loosePartitionPreviewJob?.cancel()
        isLoosePartitionProcessing = true
        renderLoosePartitionButton()
        clearLoosePartitionPreview()
        loosePartitionPreviewJob = lifecycleScope.launch {
            val previews = withContext(Dispatchers.Default) {
                currentLooseImageFiles.mapNotNull { file ->
                    val source = decodePartitionBitmap(file) ?: return@mapNotNull null
                    try {
                        val analysis = StonePaddleLocalRecognizer.analyzeForDebug(this@MyStoneActivity, source)
                        StonePaddleLocalRecognizer.drawDebugPreview(source, analysis)
                    } finally {
                        source.recycle()
                    }
                }
            }
            isLoosePartitionProcessing = false
            renderLoosePartitionButton()
            if (previews.isEmpty()) {
                Toast.makeText(this@MyStoneActivity, "散图划分失败", Toast.LENGTH_SHORT).show()
            } else {
                loosePartitionPreviewBitmaps += previews
                loosePartitionPreviewKey = currentLoosePartitionKey()
                renderLoosePartitionPreviewView()
            }
        }
    }

    private fun renderLongPartitionPreviewView() {
        val key = currentLongPartitionKey()
        val bitmap = longPartitionPreviewBitmap
        if (isLongExpanded && key != null && key == longPartitionPreviewKey && bitmap != null && !bitmap.isRecycled) {
            longPartitionPreviewView.isVisible = true
            longPartitionPreviewView.setImageBitmap(bitmap)
        } else {
            longPartitionPreviewView.isVisible = false
            longPartitionPreviewView.setImageDrawable(null)
        }
    }

    private fun renderLoosePartitionPreviewView() {
        loosePartitionPreviewsContainer.removeAllViews()
        val shouldShow = isLooseExpanded &&
            loosePartitionPreviewKey == currentLoosePartitionKey() &&
            loosePartitionPreviewBitmaps.isNotEmpty()
        loosePartitionPreviewsContainer.isVisible = shouldShow
        if (!shouldShow) return

        loosePartitionPreviewBitmaps.forEachIndexed { index, bitmap ->
            if (bitmap.isRecycled) return@forEachIndexed
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(12)
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bitmap)
            }
            loosePartitionPreviewsContainer.addView(imageView)
        }
    }

    private fun decodePartitionBitmap(file: File?): Bitmap? {
        if (file == null) return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val targetWidth = 1080
        val sampleSize = max(1, bounds.outWidth / targetWidth)
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit(sampleSize).coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun currentLongPartitionKey(): String? {
        val firstImage = currentLongImageFiles.firstOrNull()
        val imageVersion = currentRecord?.updatedAt ?: 0L
        return firstImage?.absolutePath?.let { "${currentArchiveId}_${currentStoneType}_${imageVersion}_$it" }
    }

    private fun currentLoosePartitionKey(): String? {
        val imageVersion = currentRecord?.updatedAt ?: 0L
        return if (currentLooseImageFiles.isEmpty()) null else {
            "${currentArchiveId}_${currentStoneType}_${imageVersion}_${currentLooseImageFiles.joinToString("|") { it.absolutePath }}"
        }
    }

    private fun clearLongPartitionPreview() {
        longPartitionPreviewJob?.cancel()
        longPartitionPreviewJob = null
        longPartitionPreviewBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        longPartitionPreviewBitmap = null
        longPartitionPreviewKey = null
        longPartitionPreviewView.setImageDrawable(null)
        longPartitionPreviewView.isVisible = false
    }

    private fun clearLoosePartitionPreview() {
        loosePartitionPreviewJob?.cancel()
        loosePartitionPreviewJob = null
        loosePartitionPreviewBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        loosePartitionPreviewBitmaps.clear()
        loosePartitionPreviewKey = null
        loosePartitionPreviewsContainer.removeAllViews()
        loosePartitionPreviewsContainer.isVisible = false
    }

    private fun renderRows() {
        rowsContainer.removeAllViews()
        if (currentRows.isEmpty()) {
            rowHintView.text = "尚未进行 OCR 统计，或当前${MyStoneStore.displayName(currentStoneType)}结果中没有可展示的原位置数据。"
            return
        }

        rowHintView.text =
            "红色行表示这一行名字和等级数量不匹配，或仍有无效项。每个星石可单独修改或删除。"

        currentRows.forEachIndexed { rowIndex, row ->
            rowsContainer.addView(createRowView(rowIndex, row))
        }
    }

    private fun renderStatCards(cards: List<StoneDisplayCard>) {
        statsCardsContainer.removeAllViews()
        if (cards.isEmpty()) {
            return
        }

        cards.chunked(2).forEachIndexed { rowIndex, rowCards ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) {
                        topMargin = dp(12)
                    }
                }
            }

            rowCards.forEachIndexed { index, card ->
                rowLayout.addView(createStatCard(card, marginStart = if (index == 0) 0 else dp(12)))
            }

            if (rowCards.size == 1) {
                rowLayout.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        0,
                        1f
                    ).apply {
                        marginStart = dp(12)
                    }
                })
            }

            statsCardsContainer.addView(rowLayout)
        }
    }

    private fun createStatCard(card: StoneDisplayCard, marginStart: Int): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_stone_stat_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                this.marginStart = marginStart
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleColumnLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = titleColumnLayoutParams
        }

        val nameView = TextView(this).apply {
            text = buildStoneTitle(card)
            setTextColor(Color.parseColor("#75322D"))
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.05f
        }

        val countView = TextView(this).apply {
            text = "x${card.totalCount}"
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.parseColor("#75322D"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_gold_border)
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }

        val toggleView = TextView(this).apply {
            setTextColor(Color.parseColor("#8A6B5E"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(5), 0, 0)
            }
        }

        titleColumn.addView(nameView)
        topRow.addView(titleColumn)
        topRow.addView(countView)

        container.addView(topRow)
        container.addView(toggleView)

        val detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        detailContainer.addView(
            createDistributionGrid(
                items = card.levelItems,
                titleColor = Color.parseColor("#6B4720"),
                valueColor = Color.parseColor("#7A6144"),
                backgroundRes = R.drawable.bg_stone_distribution_cell
            )
        )
        detailContainer.addView(
            createSectionPanel(
                title = "培养建议",
                color = Color.parseColor("#6F977F"),
                topMargin = dp(8),
                content = createDistributionGrid(
                    items = card.cultivationItems,
                    titleColor = Color.parseColor("#4C6C5A"),
                    valueColor = Color.parseColor("#68766F"),
                    backgroundRes = R.drawable.bg_stone_cultivation_cell
                )
            )
        )
        container.addView(detailContainer)

        fun applyExpandedState() {
            val expanded = expandedStatCards.contains(card.name)
            detailContainer.isVisible = expanded
            toggleView.text = if (expanded) "收起详情" else "展开详情"
        }

        applyExpandedState()
        container.setOnClickListener {
            if (expandedStatCards.contains(card.name)) {
                expandedStatCards.remove(card.name)
            } else {
                expandedStatCards.add(card.name)
            }
            applyExpandedState()
        }
        return container
    }

    private fun buildStoneTitle(card: StoneDisplayCard): CharSequence {
        val title = "${card.name} - ${card.category}"
        val nameEnd = card.name.length
        val styled = SpannableString(title)
        styled.setSpan(
            ForegroundColorSpan(Color.parseColor("#75322D")),
            0,
            nameEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            AbsoluteSizeSpan(18, true),
            0,
            nameEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            nameEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            ForegroundColorSpan(Color.parseColor("#8A6B5E")),
            nameEnd,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        styled.setSpan(
            AbsoluteSizeSpan(12, true),
            nameEnd,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return styled
    }

    private fun switchStoneType(stoneType: String) {
        val normalizedType = MyStoneStore.normalizeType(stoneType)
        if (currentStoneType == normalizedType) return
        currentStoneType = normalizedType
        MyStoneStore.setSelectedType(this, currentStoneType)
        renderTypeSelection()
        renderStoneRecord()
    }

    private fun renderArchiveSelection() {
        archiveNameView.text = MyStoneStore.getSelectedArchive(this).name
    }

    private fun showArchiveSwitchDialog() {
        val archives = MyStoneStore.listArchives(this)
        if (archives.isEmpty()) return

        val labels = archives.map { archive ->
            if (archive.id == currentArchiveId) "当前：${archive.name}" else archive.name
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("切换存档")
            .setItems(labels) { _, which ->
                val archive = archives[which]
                currentArchiveId = archive.id
                MyStoneStore.setSelectedArchiveId(this, archive.id)
                renderStoneRecord()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateArchiveDialog() {
        showArchiveInputDialog(
            title = "新建存档",
            positiveText = "创建",
            initialValue = ""
        ) { archiveName ->
            try {
                val archive = MyStoneStore.createArchive(this, archiveName)
                currentArchiveId = archive.id
                Toast.makeText(this, "已创建存档：${archive.name}", Toast.LENGTH_SHORT).show()
                renderStoneRecord()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, e.message ?: "创建存档失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameArchiveDialog() {
        val currentArchive = MyStoneStore.getSelectedArchive(this)
        showArchiveInputDialog(
            title = "重命名存档",
            positiveText = "保存",
            initialValue = currentArchive.name
        ) { archiveName ->
            try {
                MyStoneStore.renameArchive(this, currentArchive.id, archiveName)
                Toast.makeText(this, "存档已重命名", Toast.LENGTH_SHORT).show()
                renderStoneRecord()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, e.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showArchiveInputDialog(
        title: String,
        positiveText: String,
        initialValue: String,
        onConfirm: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
            hint = "请输入存档名称"
            setSingleLine()
            applyYuanInputStyle()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(positiveText) { _, _ ->
                onConfirm(input.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun renderTypeSelection() {
        val isMainSelected = currentStoneType == MyStoneStore.TYPE_MAIN
        mainTypeView.setBackgroundResource(if (isMainSelected) R.drawable.btn_stone_soft_selected else R.drawable.btn_stone_light)
        mainTypeView.setTextColor(Color.parseColor("#75322D"))
        supportTypeView.setBackgroundResource(if (isMainSelected) R.drawable.btn_stone_light else R.drawable.btn_stone_soft_selected)
        supportTypeView.setTextColor(Color.parseColor("#75322D"))
        addStoneButton.setBackgroundResource(R.drawable.btn_stone_soft_selected)
        addStoneButton.setTextColor(Color.parseColor("#75322D"))
        addStoneButton.text = "新增${MyStoneStore.displayName(currentStoneType)}"
    }

    private fun createDistributionGrid(
        items: List<DistributionItem>,
        titleColor: Int,
        valueColor: Int,
        backgroundRes: Int,
        topMargin: Int = 0
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, topMargin, 0, 0)
            }
        }

        items.chunked(3).forEachIndexed { rowIndex, rowItems ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) {
                        setMargins(0, dp(6), 0, 0)
                    }
                }
            }

            rowItems.forEachIndexed { index, item ->
                rowLayout.addView(
                    createDistributionCell(
                        item = item,
                        titleColor = titleColor,
                        valueColor = valueColor,
                        backgroundRes = backgroundRes,
                        marginStart = if (index == 0) 0 else dp(6)
                    )
                )
            }

            if (rowItems.size < 3) {
                repeat(3 - rowItems.size) { spacerIndex ->
                    rowLayout.addView(
                        View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                0,
                                1f
                            ).apply {
                                marginStart = if (rowItems.isEmpty() && spacerIndex == 0) 0 else dp(6)
                            }
                        }
                    )
                }
            }

            container.addView(rowLayout)
        }

        return container
    }

    private fun createSectionPanel(
        title: String,
        color: Int,
        topMargin: Int = 0,
        content: View
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, topMargin, 0, 0)
            }
        }

        val titleView = TextView(this).apply {
            text = title
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(color)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setBackgroundResource(R.drawable.bg_stone_section_tag)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(titleView)
        container.addView(content.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, 0)
            }
        })
        return container
    }

    private fun createDistributionCell(
        item: DistributionItem,
        titleColor: Int,
        valueColor: Int,
        backgroundRes: Int,
        marginStart: Int
    ): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(backgroundRes)
            setPadding(dp(4), dp(5), dp(4), dp(5))
            minimumHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(marginStart, 0, 0, 0)
            }
        }

        val accentView = View(this).apply {
            setBackgroundColor(titleColor)
            alpha = 0.35f
            layoutParams = LinearLayout.LayoutParams(
                dp(18),
                dp(2)
            )
        }

        val titleView = TextView(this).apply {
            text = item.title
            gravity = Gravity.CENTER
            setTextColor(titleColor)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(3), 0, 0)
            }
        }

        val valueView = TextView(this).apply {
            text = item.value
            gravity = Gravity.CENTER
            setTextColor(valueColor)
            textSize = 10f
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(2), 0, 0)
            }
        }

        container.addView(accentView)
        container.addView(titleView)
        container.addView(valueView)
        return container
    }

    private fun buildStoneDisplayCards(stats: List<StoneStat>): List<StoneDisplayCard> {
        if (stats.isEmpty()) return emptyList()

        val grouped = stats.groupBy { it.name }
        return grouped.entries
            .sortedBy { StoneOcrParser.validStoneNames.indexOf(it.key).let { index -> if (index >= 0) index else Int.MAX_VALUE } }
            .map { (name, items) ->
                val category = if (mainStoneNames.contains(name)) "主星" else "辅星"
                StoneDisplayCard(
                    name = name,
                    category = category,
                    totalCount = items.sumOf { it.count },
                    levelItems = items
                        .sortedByDescending { levelSortValue(it.level) }
                        .map { DistributionItem(title = it.level, value = "x${it.count}") },
                    cultivationItems = stoneCultivationAdvice(name)
                )
            }
    }

    private fun stoneCultivationAdvice(name: String): List<DistributionItem> =
        cultivationAdviceMap[name] ?: listOf(DistributionItem(title = "不培养", value = "-"))

    private fun cultivationItemsOf(vararg pairs: Pair<String, String>): List<DistributionItem> =
        pairs.map { (title, value) -> DistributionItem(title = title, value = value) }

    private val cultivationAdviceMap: Map<String, List<DistributionItem>> by lazy {
        mapOf(
            "天府" to cultivationItemsOf("60级" to "x3", "40级" to "x1", "0-20级" to "x1"),
            "武曲" to cultivationItemsOf("60级" to "x3", "40级" to "x1", "0-20级" to "x1"),
            "天机" to cultivationItemsOf("60级" to "x2", "0-30级" to "x1"),
            "破军" to cultivationItemsOf("60级" to "x1", "40级" to "x1", "20级" to "x1"),
            "太阳" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "天同" to cultivationItemsOf("60级" to "x1", "40-60级" to "x1"),
            "天梁" to cultivationItemsOf("60级" to "x1", "20-60级" to "x1"),
            "太阴" to cultivationItemsOf("60级" to "x1", "30-50级" to "x1"),
            "巨门" to cultivationItemsOf("60级" to "x1", "40-60级" to "x1"),
            "天相" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "七杀" to cultivationItemsOf("60级" to "x1", "0-40级" to "x1"),
            "紫微" to cultivationItemsOf("50-60级" to "x1"),
            "贪狼" to cultivationItemsOf("30-60级" to "x1"),
            "廉贞" to cultivationItemsOf("不养高" to "-"),
            "解神" to cultivationItemsOf("60级" to "x1", "40级" to "x1", "0-20级" to "x2"),
            "文曲" to cultivationItemsOf("60级" to "x2", "40级" to "x1", "0-20级" to "x1"),
            "天钺" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "天马" to cultivationItemsOf("60级" to "x1", "30级" to "x1"),
            "右弼" to cultivationItemsOf("60级" to "x2", "40级" to "x1"),
            "擎羊" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "左辅" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "天魁" to cultivationItemsOf("60级" to "x1", "40级" to "x1"),
            "阴煞" to cultivationItemsOf("30-50级" to "x1-2"),
            "天巫" to cultivationItemsOf("30-50级" to "x2"),
            "三台" to cultivationItemsOf("30-50级" to "x2"),
            "文昌" to cultivationItemsOf("40-60级" to "x1"),
            "红鸾" to cultivationItemsOf("20-40级" to "x2"),
            "地劫" to cultivationItemsOf("30级" to "x2"),
            "禄存" to cultivationItemsOf("30级" to "x2"),
            "陀螺" to cultivationItemsOf("30级" to "x1"),
            "火星" to cultivationItemsOf("30级" to "x1"),
            "天姚" to cultivationItemsOf("30级" to "x1"),
            "铃星" to cultivationItemsOf("30级" to "x1"),
            "地空" to cultivationItemsOf("30级" to "x1"),
            "天刑" to cultivationItemsOf("30级" to "x1"),
            "八座" to cultivationItemsOf("不培养" to "-"),
            "恩光" to cultivationItemsOf("不培养" to "-"),
            "天贵" to cultivationItemsOf("不培养" to "-")
        )
    }

    private fun levelSortValue(level: String): Int =
        level.removeSuffix("级").toIntOrNull() ?: Int.MIN_VALUE

    private fun createRowView(rowIndex: Int, row: MyStoneRow): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundResource(R.drawable.bg_stone_section_card)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) {
                    topMargin = dp(10)
                }
            }
        }

        val label = TextView(this).apply {
            text = "第 ${rowIndex + 1} 行"
            setTextColor(Color.parseColor("#75322D"))
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        outer.addView(label)

        val cellsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        row.cells.forEachIndexed { cellIndex, cell ->
            cellsRow.addView(createCellView(rowIndex, cellIndex, cell))
        }

        outer.addView(cellsRow)
        applyRowState(outer, label, row)
        return outer
    }

    private fun createCellView(
        rowIndex: Int,
        cellIndex: Int,
        cell: MyStoneCell
    ): View {
        val cellLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_stone_empty_panel)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (cellIndex > 0) {
                    marginStart = dp(6)
                }
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        headerRow.addView(createActionButton(android.R.drawable.ic_menu_edit) {
            showEditDialog(rowIndex, cellIndex)
        })
        headerRow.addView(createDeleteActionButton(marginStart = dp(4)) {
            confirmDeleteCell(rowIndex, cellIndex)
        })

        val nameView = TextView(this).apply {
            text = cell.name.ifBlank { "未填" }
            textSize = 15f
            setPadding(0, dp(6), 0, 0)
        }
        val levelView = TextView(this).apply {
            text = cell.level.ifBlank { "未填" }
            textSize = 13f
        }

        applyCellState(levelView, nameView, cell)

        cellLayout.addView(headerRow)
        cellLayout.addView(nameView)
        cellLayout.addView(levelView)
        return cellLayout
    }

    private fun createActionButton(
        iconRes: Int,
        marginStart: Int = 0,
        onClick: () -> Unit
    ): ImageView {
        return ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#9A6435"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            minimumWidth = 0
            minimumHeight = 0
            adjustViewBounds = true
            setBackgroundResource(R.drawable.btn_stone_light)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(22),
                1f
            ).apply {
                this.marginStart = marginStart
            }
            setOnClickListener { onClick() }
            contentDescription = "修改"
        }
    }

    private fun createDeleteActionButton(
        marginStart: Int = 0,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = "×"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#B84D4D"))
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setBackgroundResource(R.drawable.btn_stone_light)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(22),
                1f
            ).apply {
                this.marginStart = marginStart
            }
            setOnClickListener { onClick() }
            contentDescription = "删除"
        }
    }

    private fun applyRowState(rowView: LinearLayout, labelView: TextView, row: MyStoneRow) {
        val resolved = StoneOcrParser.isRowResolved(row, currentStoneType)
        if (resolved) {
            rowView.setBackgroundColor(Color.parseColor("#26C79C5C"))
            labelView.setTextColor(Color.parseColor("#75322D"))
        } else {
            rowView.setBackgroundColor(Color.parseColor("#66E8A8A0"))
            labelView.setTextColor(Color.parseColor("#B84D4D"))
        }
    }

    private fun applyCellState(levelView: TextView, nameView: TextView, cell: MyStoneCell) {
        val levelValid = StoneOcrParser.isValidLevel(cell.level)
        val nameValid = StoneOcrParser.isCellNameValid(cell, currentStoneType)
        levelView.setTextColor(
            if (levelValid || cell.level.isBlank()) Color.parseColor("#8A6B5E") else Color.parseColor("#B84D4D")
        )
        nameView.setTextColor(
            if (nameValid || cell.name.isBlank()) Color.parseColor("#75322D") else Color.parseColor("#B84D4D")
        )
    }

    private fun showEditDialog(rowIndex: Int, cellIndex: Int) {
        val cell = currentRows.getOrNull(rowIndex)?.cells?.getOrNull(cellIndex) ?: return
        showStoneEditorDialog(
            title = "修改${MyStoneStore.displayName(currentStoneType)}",
            confirmText = "保存",
            initialName = cell.name,
            initialLevel = cell.level
        ) { selectedName, normalizedLevel ->
            cell.level = normalizedLevel
            cell.name = selectedName
            persistRows()
            renderStats()
            updateSectionSubtitles()
            if (isRowsExpanded) {
                renderRows()
            }
        }
    }

    private fun showAddStoneDialog() {
        showStoneEditorDialog(
            title = "新增${MyStoneStore.displayName(currentStoneType)}",
            confirmText = "新增",
            initialName = null,
            initialLevel = "1级"
        ) { selectedName, normalizedLevel ->
            currentRows.add(
                MyStoneRow(
                    cells = mutableListOf(
                        MyStoneCell(
                            level = normalizedLevel,
                            name = selectedName
                        )
                    )
                )
            )
            persistRows()
            renderStats()
            updateSectionSubtitles()
            if (isRowsExpanded) {
                renderRows()
            }
        }
    }

    private fun showStoneEditorDialog(
        title: String,
        confirmText: String,
        initialName: String?,
        initialLevel: String,
        onConfirm: (selectedName: String, normalizedLevel: String) -> Unit
    ) {
        val availableNames = availableStoneNamesForCurrentType()
        if (availableNames.isEmpty()) {
            Toast.makeText(this, "当前没有可选星石", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stone, null)
        val titleText = dialogView.findViewById<TextView>(R.id.tv_add_stone_title)
        val selectedNameView = dialogView.findViewById<TextView>(R.id.tv_add_stone_selected_name)
        val namesGrid = dialogView.findViewById<GridLayout>(R.id.layout_add_stone_names)
        val levelInput = dialogView.findViewById<EditText>(R.id.et_add_stone_level)
        val cancelButton = dialogView.findViewById<Button>(R.id.btn_add_stone_cancel)
        val confirmButton = dialogView.findViewById<Button>(R.id.btn_add_stone_confirm)

        titleText.text = title
        confirmButton.text = confirmText
        levelInput.setText(initialLevel)

        var selectedName = availableNames.firstOrNull {
            StoneOcrParser.normalizeToken(it) == StoneOcrParser.normalizeToken(initialName.orEmpty())
        }

        fun renderSelectedName() {
            selectedNameView.text = selectedName ?: "请选择星石"
        }

        fun refreshOptions() {
            namesGrid.removeAllViews()
            availableNames.forEachIndexed { index, name ->
                namesGrid.addView(createStoneNameOptionView(name, name == selectedName, index) {
                    selectedName = name
                    renderSelectedName()
                    refreshOptions()
                })
            }
        }

        renderSelectedName()
        refreshOptions()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        confirmButton.setOnClickListener {
            val finalName = selectedName
            if (finalName == null) {
                Toast.makeText(this, "请选择星石", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalizedLevel = StoneOcrParser.normalizeLevel(levelInput.text.toString().ifBlank { "1级" })
            if (!StoneOcrParser.isValidLevel(normalizedLevel)) {
                Toast.makeText(this, "请输入正确等级，例如 1级", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            onConfirm(finalName, normalizedLevel)
            dialog.dismiss()
        }
    }

    private fun createStoneNameOptionView(
        name: String,
        selected: Boolean,
        index: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = name
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (selected) Color.parseColor("#1A1A1A")
                else Color.parseColor("#F6D28D")
            )
            setBackgroundResource(
                if (selected) R.drawable.bg_stone_picker_option_selected
                else R.drawable.bg_stone_picker_option
            )
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(if (index % 3 == 0) 0 else dp(8), if (index < 3) 0 else dp(8), 0, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun deleteCell(rowIndex: Int, cellIndex: Int) {
        val row = currentRows.getOrNull(rowIndex) ?: return
        if (cellIndex !in row.cells.indices) return

        row.cells.removeAt(cellIndex)
        if (row.cells.isEmpty()) {
            currentRows.removeAt(rowIndex)
        }

        persistRows()
        renderStats()
        updateSectionSubtitles()
        if (isRowsExpanded) {
            renderRows()
        }
    }

    private fun confirmDeleteCell(rowIndex: Int, cellIndex: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除星石")
            .setMessage("确认删除这颗星石吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteCell(rowIndex, cellIndex)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun persistRows() {
        val normalizedRows = currentRows.map { row ->
            MyStoneRow(
                cells = row.cells.map { cell ->
                    MyStoneCell(
                        level = StoneOcrParser.normalizeLevel(cell.level),
                        name = StoneOcrParser.normalizeToken(cell.name)
                    )
                }.toMutableList()
            )
        }
        currentRows = normalizedRows.toMutableList()
        val statsLines = StoneOcrParser.format(StoneOcrParser.aggregate(currentRows, currentStoneType))
        currentRecord = MyStoneStore.updateRows(
            context = this,
            stoneType = currentStoneType,
            rows = currentRows,
            statsLines = statsLines,
            archiveId = currentArchiveId
        )
        currentRecord?.let { record ->
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(Date(record.updatedAt))
            titleView.text = "最近更新：$timeText"
        }
    }

    private fun availableStoneNamesForCurrentType(): List<String> {
        return if (currentStoneType == MyStoneStore.TYPE_MAIN) {
            StoneOcrParser.validStoneNames.filter { it in mainStoneNames }
        } else {
            StoneOcrParser.validStoneNames.filterNot { it in mainStoneNames }
        }
    }

    private fun hasImportedOcrResult(): Boolean {
        val record = currentRecord
        return record != null && (
            record.rows.isNotEmpty() ||
                record.statsLines.isNotEmpty() ||
                record.ocrStrategy != null
            )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
