package com.example.yuanassist.ui

import android.app.AlertDialog
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
import com.example.yuanassist.network.OcrManager
import com.example.yuanassist.utils.MyStoneCell
import com.example.yuanassist.utils.MyStoneRecord
import com.example.yuanassist.utils.MyStoneRow
import com.example.yuanassist.utils.StoneStat
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.StoneOcrParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyStoneActivity : AppCompatActivity() {

    private data class DistributionItem(
        val title: String,
        val value: String
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
    private lateinit var mainTypeView: TextView
    private lateinit var supportTypeView: TextView
    private lateinit var addStoneButton: TextView

    private lateinit var imageSectionHeading: View
    private lateinit var imageSectionSubtitle: TextView
    private lateinit var imageSectionRecognizeButton: TextView
    private lateinit var imageSectionToggle: TextView
    private lateinit var imageSectionContent: View

    private lateinit var rowsSectionHeading: View
    private lateinit var rowsSectionSubtitle: TextView
    private lateinit var rowsSectionToggle: TextView
    private lateinit var rowsSectionContent: View
    private lateinit var rowHintView: TextView
    private lateinit var rowsContainer: LinearLayout

    private lateinit var imageOneView: ImageView
    private lateinit var imageTwoView: ImageView

    private var currentRecord: MyStoneRecord? = null
    private var currentImageFiles: List<File> = emptyList()
    private var currentRows: MutableList<MyStoneRow> = mutableListOf()
    private var currentStoneType: String = MyStoneStore.TYPE_MAIN
    private var isImagesExpanded = false
    private var isRowsExpanded = false
    private var ocrProcessingStoneType: String? = null
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
        mainTypeView = findViewById(R.id.tv_my_stone_type_main)
        supportTypeView = findViewById(R.id.tv_my_stone_type_support)
        addStoneButton = findViewById(R.id.btn_my_stone_add)

        imageSectionHeading = findViewById(R.id.layout_my_stone_images_heading)
        imageSectionSubtitle = findViewById(R.id.tv_my_stone_images_subtitle)
        imageSectionRecognizeButton = findViewById(R.id.btn_recognize_my_stone_images)
        imageSectionToggle = findViewById(R.id.btn_toggle_my_stone_images)
        imageSectionContent = findViewById(R.id.layout_my_stone_images_content)

        rowsSectionHeading = findViewById(R.id.layout_my_stone_rows_heading)
        rowsSectionSubtitle = findViewById(R.id.tv_my_stone_rows_subtitle)
        rowsSectionToggle = findViewById(R.id.btn_toggle_my_stone_rows)
        rowsSectionContent = findViewById(R.id.layout_my_stone_rows_content)
        rowHintView = findViewById(R.id.tv_my_stone_rows_hint)
        rowsContainer = findViewById(R.id.layout_my_stone_rows)

        imageOneView = findViewById(R.id.iv_my_stone_result_1)
        imageTwoView = findViewById(R.id.iv_my_stone_result_2)
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
        imageSectionHeading.setOnClickListener {
            setImagesExpanded(!isImagesExpanded)
        }
        imageSectionRecognizeButton.setOnClickListener {
            triggerStoneOcrIfNeeded()
        }
        rowsSectionHeading.setOnClickListener {
            setRowsExpanded(!isRowsExpanded)
        }
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
        currentStoneType = MyStoneStore.getSelectedType(this)

        currentStoneType = MyStoneStore.normalizeType(currentStoneType)
        renderTypeSelection()

        val record = MyStoneStore.loadRecord(this, currentStoneType)
        val imageFiles = record?.let { MyStoneStore.imageFiles(this, currentStoneType, it) }.orEmpty()
        currentRecord = record
        currentImageFiles = imageFiles

        emptyView.isVisible = false
        contentView.isVisible = true

        titleView.text = if (record != null) {
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(Date(record.updatedAt))
            "最近更新：$timeText"
        } else {
            "最近更新：${MyStoneStore.displayName(currentStoneType)}暂无结果"
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

        renderStats()
        updateSectionSubtitles()
        renderRecognizeButton()
        setImagesExpanded(isImagesExpanded)
        setRowsExpanded(isRowsExpanded)
    }

    private fun renderStats() {
        val stats = StoneOcrParser.aggregate(currentRows)
        val cards = buildStoneDisplayCards(stats)
        val unresolvedRows = currentRows.count { !StoneOcrParser.isRowResolved(it) }
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

    private fun setImagesExpanded(expanded: Boolean) {
        val canExpand = currentImageFiles.isNotEmpty()
        isImagesExpanded = expanded && canExpand
        imageSectionToggle.text = if (isImagesExpanded) "收起" else "展开"
        imageSectionContent.isVisible = isImagesExpanded

        if (isImagesExpanded) {
            renderImages()
        } else {
            Glide.with(this).clear(imageOneView)
            Glide.with(this).clear(imageTwoView)
            imageTwoView.isVisible = false
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
        imageSectionSubtitle.text = if (currentImageFiles.isEmpty()) {
            "暂无${typeLabel}结果图"
        } else {
            "共 ${currentImageFiles.size} 张${typeLabel}结果图，默认收起"
        }

        val invalidRowCount = currentRows.count { !StoneOcrParser.isRowResolved(it) }
        rowsSectionSubtitle.text = if (currentRows.isEmpty()) {
            "暂无${typeLabel}原位置数据"
        } else {
            "共 ${currentRows.size} 行${typeLabel}原位置数据，待修正 ${invalidRowCount} 行"
        }
    }

    private fun renderRecognizeButton() {
        val isProcessing = ocrProcessingStoneType == currentStoneType
        val shouldShow = currentImageFiles.isNotEmpty() && !hasImportedOcrResult()
        imageSectionRecognizeButton.isVisible = shouldShow || isProcessing
        imageSectionRecognizeButton.isEnabled = !isProcessing
        imageSectionRecognizeButton.text = if (isProcessing) "识别中..." else "识别"
        imageSectionRecognizeButton.setBackgroundResource(
            if (isProcessing) R.drawable.btn_dark_hollow else R.drawable.btn_dark_gold
        )
        imageSectionRecognizeButton.setTextColor(
            if (isProcessing) Color.parseColor("#E5C07B") else Color.parseColor("#1A1A1A")
        )
    }

    private fun triggerStoneOcrIfNeeded() {
        if (ocrProcessingStoneType != null) return
        if (currentImageFiles.isEmpty()) {
            Toast.makeText(this, "当前没有可识别的结果图", Toast.LENGTH_SHORT).show()
            return
        }
        if (hasImportedOcrResult()) {
            renderRecognizeButton()
            return
        }

        val requestStoneType = currentStoneType
        val imageFiles = currentImageFiles.toList()
        ocrProcessingStoneType = requestStoneType
        renderRecognizeButton()

        lifecycleScope.launch {
            try {
                val wordsGroups = mutableListOf<List<String>>()
                val rawEntryGroups = mutableListOf<List<String>>()
                val strategyUsed = linkedSetOf<String>()

                for (file in imageFiles) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bitmap == null) {
                        Toast.makeText(this@MyStoneActivity, "读取星石截图失败：${file.name}", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    try {
                        when (
                            val result = OcrManager.recognizeStoneImage(
                                bitmap = bitmap,
                                deviceId = deviceId,
                                onRetryMsg = {
                                    Toast.makeText(this@MyStoneActivity, "OCR 请求繁忙，正在重试...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        ) {
                            is OcrManager.StoneOcrResult.Success -> {
                                wordsGroups += result.words
                                rawEntryGroups += result.rawEntries
                                if (result.strategyUsed.isNotEmpty()) {
                                    strategyUsed += result.strategyUsed
                                }
                            }

                            is OcrManager.StoneOcrResult.Error -> {
                                Toast.makeText(this@MyStoneActivity, result.message, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                if (rawEntryGroups.isNotEmpty()) {
                    RunLogger.raw("【OCR返回原文本】")
                    StoneOcrParser.formatRawJsonByRow(rawEntryGroups).forEach { line ->
                        RunLogger.i(line)
                    }
                }
                val rows = StoneOcrParser.buildRows(wordsGroups)
                val lines = StoneOcrParser.format(StoneOcrParser.aggregate(rows))
                val hasPendingRows = rows.any { !StoneOcrParser.isRowResolved(it) }

                MyStoneStore.saveOcrResult(
                    context = this@MyStoneActivity,
                    stoneType = requestStoneType,
                    rows = rows,
                    statsLines = lines,
                    ocrStrategy = strategyUsed.joinToString(",")
                )

                if (hasPendingRows) {
                    RunLogger.i("星石 OCR 完成，但仍有待修正行，行数=${rows.size}")
                    Toast.makeText(this@MyStoneActivity, "OCR 已导入，可在我的星石中修正红色行", Toast.LENGTH_LONG).show()
                } else {
                    RunLogger.i("星石 OCR 完成：${lines.joinToString(" | ")}")
                    Toast.makeText(this@MyStoneActivity, "OCR 统计完成", Toast.LENGTH_LONG).show()
                }

                if (currentStoneType == requestStoneType) {
                    renderStoneRecord()
                }
            } catch (t: Throwable) {
                Toast.makeText(this@MyStoneActivity, "星石 OCR 统计失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                if (ocrProcessingStoneType == requestStoneType) {
                    ocrProcessingStoneType = null
                }
                renderRecognizeButton()
            }
        }
    }

    private fun renderImages() {
        if (currentImageFiles.isEmpty()) return
        val imageVersion = currentRecord?.updatedAt ?: System.currentTimeMillis()

        Glide.with(this)
            .load(currentImageFiles[0])
            .signature(ObjectKey("stone_${currentStoneType}_${imageVersion}_1"))
            .into(imageOneView)

        if (currentImageFiles.size > 1) {
            imageTwoView.isVisible = true
            Glide.with(this)
                .load(currentImageFiles[1])
                .signature(ObjectKey("stone_${currentStoneType}_${imageVersion}_2"))
                .into(imageTwoView)
        } else {
            imageTwoView.isVisible = false
            Glide.with(this).clear(imageTwoView)
        }
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
            setPadding(dp(10), dp(10), dp(10), dp(10))
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
            setTextColor(Color.parseColor("#F6D28D"))
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.05f
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.parseColor("#66210F05"))
        }

        val countView = TextView(this).apply {
            text = "x${card.totalCount}"
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.parseColor("#F9E7BF"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_gold_border)
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }

        val toggleView = TextView(this).apply {
            setTextColor(Color.parseColor("#B7C0D8"))
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
            ForegroundColorSpan(Color.parseColor("#F6D28D")),
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
            ForegroundColorSpan(Color.parseColor("#C8B58A")),
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

    private fun renderTypeSelection() {
        val isMainSelected = currentStoneType == MyStoneStore.TYPE_MAIN
        mainTypeView.setBackgroundResource(if (isMainSelected) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
        mainTypeView.setTextColor(if (isMainSelected) Color.parseColor("#1A1A1A") else Color.parseColor("#E5C07B"))
        supportTypeView.setBackgroundResource(if (isMainSelected) R.drawable.btn_dark_hollow else R.drawable.btn_dark_gold)
        supportTypeView.setTextColor(if (isMainSelected) Color.parseColor("#E5C07B") else Color.parseColor("#1A1A1A"))
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
            textSize = 11f
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
            setPadding(dp(10), dp(10), dp(10), dp(10))
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
            setTextColor(Color.parseColor("#E5C07B"))
            textSize = 13f
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
        applyRowState(outer, row)
        return outer
    }

    private fun createCellView(
        rowIndex: Int,
        cellIndex: Int,
        cell: MyStoneCell
    ): View {
        val cellLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_run_log_console)
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
            setColorFilter(Color.parseColor("#E5C07B"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            minimumWidth = 0
            minimumHeight = 0
            adjustViewBounds = true
            setBackgroundResource(R.drawable.btn_dark_hollow)
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
            setTextColor(Color.parseColor("#FF8A80"))
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setBackgroundResource(R.drawable.btn_dark_hollow)
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

    private fun applyRowState(rowView: LinearLayout, row: MyStoneRow) {
        val resolved = StoneOcrParser.isRowResolved(row)
        rowView.setBackgroundColor(
            if (resolved) {
                Color.parseColor("#3A1F1A12")
            } else {
                Color.parseColor("#66C0392B")
            }
        )
    }

    private fun applyCellState(levelView: TextView, nameView: TextView, cell: MyStoneCell) {
        val levelValid = StoneOcrParser.isValidLevel(cell.level)
        val nameValid = StoneOcrParser.isCellNameValid(cell)
        levelView.setTextColor(
            if (levelValid || cell.level.isBlank()) Color.parseColor("#F3E9D0") else Color.parseColor("#FFB3B3")
        )
        nameView.setTextColor(
            if (nameValid || cell.name.isBlank()) Color.parseColor("#F3E9D0") else Color.parseColor("#FF8A80")
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
        val statsLines = StoneOcrParser.format(StoneOcrParser.aggregate(currentRows))
        currentRecord = MyStoneStore.updateRows(
            context = this,
            stoneType = currentStoneType,
            rows = currentRows,
            statsLines = statsLines
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
