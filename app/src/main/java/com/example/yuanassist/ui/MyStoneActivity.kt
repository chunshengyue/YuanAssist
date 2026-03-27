package com.example.yuanassist.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.example.yuanassist.R
import com.example.yuanassist.utils.MyStoneCell
import com.example.yuanassist.utils.MyStoneRecord
import com.example.yuanassist.utils.MyStoneRow
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.StoneOcrParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyStoneActivity : AppCompatActivity() {

    private lateinit var emptyView: TextView
    private lateinit var contentView: View
    private lateinit var titleView: TextView
    private lateinit var statsView: TextView

    private lateinit var imageSectionHeading: View
    private lateinit var imageSectionSubtitle: TextView
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
    private var isImagesExpanded = false
    private var isRowsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_stone)

        val backButton = findViewById<ImageView>(R.id.btn_back_my_stone)
        val header = findViewById<View>(R.id.layout_my_stone_header)
        val topSpace = findViewById<View>(R.id.view_my_stone_status_space)

        emptyView = findViewById(R.id.tv_my_stone_empty)
        contentView = findViewById(R.id.layout_my_stone_content)
        titleView = findViewById(R.id.tv_my_stone_updated_at)
        statsView = findViewById(R.id.tv_my_stone_stats)

        imageSectionHeading = findViewById(R.id.layout_my_stone_images_heading)
        imageSectionSubtitle = findViewById(R.id.tv_my_stone_images_subtitle)
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
        rowsSectionHeading.setOnClickListener {
            setRowsExpanded(!isRowsExpanded)
        }
    }

    override fun onResume() {
        super.onResume()
        renderStoneRecord()
    }

    private fun renderStoneRecord() {
        val record = MyStoneStore.loadRecord(this)
        val imageFiles = record?.let { MyStoneStore.imageFiles(this, it) }.orEmpty()
        currentRecord = record
        currentImageFiles = imageFiles

        if (record == null || imageFiles.isEmpty()) {
            emptyView.text = "暂未生成星石结果"
            emptyView.isVisible = true
            contentView.isVisible = false
            return
        }

        emptyView.isVisible = false
        contentView.isVisible = true

        val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            .format(Date(record.updatedAt))
        titleView.text = "最近更新：$timeText"

        currentRows = record.rows.map { row ->
            MyStoneRow(
                cells = row.cells.map { cell ->
                    MyStoneCell(
                        level = StoneOcrParser.normalizeLevel(cell.level),
                        name = StoneOcrParser.normalizeToken(cell.name)
                    )
                }.toMutableList()
            )
        }.toMutableList()

        renderStats()
        updateSectionSubtitles()
        setImagesExpanded(isImagesExpanded)
        setRowsExpanded(isRowsExpanded)
    }

    private fun renderStats() {
        val stats = StoneOcrParser.aggregate(currentRows)
        statsView.text = if (stats.isEmpty()) {
            "当前没有完整有效的统计结果，请先检查下方红色行并补全。"
        } else {
            StoneOcrParser.format(stats).joinToString("\n")
        }
    }

    private fun setImagesExpanded(expanded: Boolean) {
        isImagesExpanded = expanded
        imageSectionToggle.text = if (expanded) "收起" else "展开"
        imageSectionContent.isVisible = expanded

        if (expanded) {
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
        imageSectionSubtitle.text = if (currentImageFiles.isEmpty()) {
            "暂无结果图"
        } else {
            "共 ${currentImageFiles.size} 张，默认收起"
        }

        val invalidRowCount = currentRows.count { !StoneOcrParser.isRowResolved(it) }
        rowsSectionSubtitle.text = if (currentRows.isEmpty()) {
            "暂无原位置数据"
        } else {
            "共 ${currentRows.size} 行，待修正 ${invalidRowCount} 行"
        }
    }

    private fun renderImages() {
        if (currentImageFiles.isEmpty()) return

        Glide.with(this)
            .load(currentImageFiles[0])
            .into(imageOneView)

        if (currentImageFiles.size > 1) {
            imageTwoView.isVisible = true
            Glide.with(this)
                .load(currentImageFiles[1])
                .into(imageTwoView)
        } else {
            imageTwoView.isVisible = false
            Glide.with(this).clear(imageTwoView)
        }
    }

    private fun renderRows() {
        rowsContainer.removeAllViews()
        if (currentRows.isEmpty()) {
            rowHintView.text = "尚未进行 OCR 统计，或当前结果中没有可展示的原位置数据。"
            return
        }

        rowHintView.text =
            "红色行表示这一行名字和等级数量不匹配，或仍有无效项。每个星石可单独修改或删除。"

        currentRows.forEachIndexed { rowIndex, row ->
            rowsContainer.addView(createRowView(rowIndex, row))
        }
    }

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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val levelInput = EditText(this).apply {
            setText(cell.level)
            hint = "等级"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val nameInput = EditText(this).apply {
            setText(cell.name)
            hint = "名字"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        container.addView(levelInput)
        container.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("修改星石")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                cell.level = StoneOcrParser.normalizeLevel(levelInput.text.toString())
                cell.name = StoneOcrParser.normalizeToken(nameInput.text.toString())
                persistRows()
                renderStats()
                updateSectionSubtitles()
                if (isRowsExpanded) {
                    renderRows()
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
        MyStoneStore.updateRows(
            context = this,
            rows = currentRows,
            statsLines = statsLines
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
