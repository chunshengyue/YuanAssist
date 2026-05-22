// 文件路径：yuanassist/ui/dialogs/ServiceDialogs.kt
package com.example.yuanassist.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.ui.buildSelectableAgentList
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.disableShowSoftInput
import com.example.yuanassist.utils.protectInputLongPress

object ServiceDialogs {

    data class ExportImageSettings(
        val headers: Array<String>,
        val gameTitle: String,
        val subtitle: String,
    )

    fun showTextImportDialog(context: Context, onImport: (String) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "导入文字"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "粘贴跟打表格文本后确认导入"))

        val editText = StyledDialogUi.createStyledInput(themeContext, "请在此处粘贴文本...").apply {
            hint = "请在此处粘贴文本..."
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            setPadding(
                StyledDialogUi.dpToPx(themeContext, 14f),
                StyledDialogUi.dpToPx(themeContext, 12f),
                StyledDialogUi.dpToPx(themeContext, 14f),
                StyledDialogUi.dpToPx(themeContext, 12f)
            )
            protectInputLongPress()
        }
        rootLayout.addView(
            editText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                StyledDialogUi.dpToPx(themeContext, 150f)
            ).apply { topMargin = StyledDialogUi.dpToPx(themeContext, 12f) }
        )

        val btnPaste = StyledDialogUi.createActionButton(themeContext, "点击粘贴剪贴板内容", false).apply {
            text = "点击粘贴剪贴板内容"
            setOnClickListener {
                editText.requestFocus()
                if (editText.onTextContextMenuItem(android.R.id.paste)) {
                    Toast.makeText(context, "粘贴成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "粘贴失败或剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(
            btnPaste,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = StyledDialogUi.dpToPx(themeContext, 10f) }
        )

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnConfirm = StyledDialogUi.createActionButton(themeContext, "确定", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnConfirm, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onImport(editText.text.toString())
            dialog.dismiss()
        }
    }

    fun showInsertTurnDialog(context: Context, onInsert: (Int) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "新增回合"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "输入要在哪个回合后插入空回合"))
        rootLayout.addView(StyledDialogUi.createFieldLabel(themeContext, "目标回合"))
        val etTurn = StyledDialogUi.createStyledInput(themeContext, "在第几回合后新增？(例如: 12)").apply {
            hint = "在第几回合后新增？(例如: 12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            protectInputLongPress()
        }
        rootLayout.addView(etTurn)

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnConfirm = StyledDialogUi.createActionButton(themeContext, "确定", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnConfirm, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val turnStr = etTurn.text.toString()
            if (turnStr.isNotEmpty()) {
                onInsert(turnStr.toInt())
                dialog.dismiss()
            } else {
                Toast.makeText(context, "请输入回合数", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showEditActionDialog(
        context: Context,
        currentText: String,
        onSave: (String) -> Unit,
        onClear: () -> Unit
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "编辑"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "修改当前格子的动作内容"))
        val editText = StyledDialogUi.createStyledInput(themeContext, "").apply {
            setText(currentText)
            disableShowSoftInput()
        }
        rootLayout.addView(
            editText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = StyledDialogUi.dpToPx(context, 12f) }
        )
        editText.setSelection(editText.text?.length ?: 0)
        rootLayout.addView(
            buildActionKeyboard(themeContext) { key ->
                editText.requestFocus()
                handleActionKeyboardKey(editText, key)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = StyledDialogUi.dpToPx(themeContext, 10f) }
        )

        val clearRow = StyledDialogUi.createActionRow(themeContext)
        val btnClear = StyledDialogUi.createActionButton(themeContext, "清空", false).apply {
            setTextColor(Color.parseColor("#B64D3C"))
        }
        clearRow.addView(btnClear)
        rootLayout.addView(clearRow)

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnConfirm = StyledDialogUi.createActionButton(themeContext, "确定", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnConfirm, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnClear.setOnClickListener {
            onClear()
            dialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            onSave(editText.text.toString())
            dialog.dismiss()
        }
    }

    fun showExportImageSettingsDialog(context: Context, onExport: (ExportImageSettings) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "导出设置"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "选择五列角色，并设置导出图片标题"))

        rootLayout.addView(StyledDialogUi.createFieldLabel(themeContext, "游戏标题"))
        val gameGroup = RadioGroup(themeContext).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val rbRuyuan = RadioButton(themeContext).apply {
            StyledDialogUi.styleCompactCheckControl(themeContext, this, "如鸢", true)
            id = ViewGroup.generateViewId()
        }
        val rbDaihao = RadioButton(themeContext).apply {
            StyledDialogUi.styleCompactCheckControl(themeContext, this, "代号鸢", false)
            id = ViewGroup.generateViewId()
        }
        gameGroup.addView(rbRuyuan)
        gameGroup.addView(rbDaihao)
        gameGroup.check(rbRuyuan.id)
        rootLayout.addView(gameGroup)

        rootLayout.addView(StyledDialogUi.createFieldLabel(themeContext, "副标题"))
        val subtitleInput = StyledDialogUi.createStyledInput(themeContext, "可不填，例如：地宫单体作业").apply {
            hint = "可不填，例如：地宫单体作业"
            maxLines = 1
            protectInputLongPress()
        }
        rootLayout.addView(subtitleInput)

        rootLayout.addView(StyledDialogUi.createFieldLabel(themeContext, "角色"))
        val selectedAgents = Array(5) { "" }
        val agentRows = Array(5) { index ->
            createExportAgentRow(themeContext, index, selectedAgents[index]).also { row ->
                row.setOnClickListener {
                    showExportAgentPickerDialog(
                        context = themeContext,
                        slotIndex = index,
                        currentAgent = selectedAgents[index],
                        includeDaihaoYuanByDefault = gameGroup.checkedRadioButtonId == rbDaihao.id,
                        onSelect = { agentName ->
                            selectedAgents[index] = agentName
                            (row as TextView).text = exportAgentRowText(index, agentName)
                        },
                    )
                }
            }
        }
        val agentGrid = GridLayout(themeContext).apply {
            columnCount = 1
            agentRows.forEachIndexed { index, row ->
                addView(
                    row,
                    GridLayout.LayoutParams().apply {
                        width = GridLayout.LayoutParams.MATCH_PARENT
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        if (index > 0) topMargin = StyledDialogUi.dpToPx(themeContext, 8f)
                    }
                )
            }
        }
        rootLayout.addView(
            agentGrid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnConfirm = StyledDialogUi.createActionButton(themeContext, "生成图片", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnConfirm, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val gameTitle = if (gameGroup.checkedRadioButtonId == rbDaihao.id) "代号鸢" else "如鸢"
            onExport(
                ExportImageSettings(
                    headers = selectedAgents.copyOf(),
                    gameTitle = gameTitle,
                    subtitle = subtitleInput.text.toString().trim(),
                )
            )
            dialog.dismiss()
        }
    }

    private fun createExportAgentRow(context: Context, index: Int, agentName: String): TextView {
        return TextView(context).apply {
            text = exportAgentRowText(index, agentName)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#6B4E1C"))
            setBackgroundResource(com.example.yuanassist.R.drawable.bg_job_station_icon_button)
            setPadding(
                StyledDialogUi.dpToPx(context, 14f),
                StyledDialogUi.dpToPx(context, 11f),
                StyledDialogUi.dpToPx(context, 14f),
                StyledDialogUi.dpToPx(context, 11f),
            )
            isClickable = true
            isFocusable = true
        }
    }

    private fun exportAgentRowText(index: Int, agentName: String): String {
        return "${index + 1}号位：${agentName.ifBlank { "未选择" }}"
    }

    private fun showExportAgentPickerDialog(
        context: Context,
        slotIndex: Int,
        currentAgent: String,
        includeDaihaoYuanByDefault: Boolean,
        onSelect: (String) -> Unit,
    ) {
        val rootLayout = StyledDialogUi.createDialogCard(context)
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            StyledDialogUi.createDialogTitle(context, "选择 ${slotIndex + 1} 号位").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        val btnClear = StyledDialogUi.createActionButton(context, "清空", false).apply {
            setTextColor(Color.parseColor("#B64D3C"))
            setPadding(
                StyledDialogUi.dpToPx(context, 12f),
                StyledDialogUi.dpToPx(context, 7f),
                StyledDialogUi.dpToPx(context, 12f),
                StyledDialogUi.dpToPx(context, 7f),
            )
        }
        titleRow.addView(btnClear)
        rootLayout.addView(titleRow)
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(context, "当前：${currentAgent.ifBlank { "未选择" }}"))

        val includeDaihaoBox = CheckBox(context).apply {
            StyledDialogUi.styleCompactCheckControl(context, this, "代号鸢", includeDaihaoYuanByDefault)
        }
        rootLayout.addView(includeDaihaoBox)
        val searchInput = StyledDialogUi.createStyledInput(context, "搜索密探").apply {
            hint = "搜索密探"
            maxLines = 1
            protectInputLongPress()
        }
        rootLayout.addView(
            searchInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = StyledDialogUi.dpToPx(context, 12f) }
        )

        val scrollView = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        val optionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(
            optionsContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                StyledDialogUi.dpToPx(context, 360f)
            ).apply { topMargin = StyledDialogUi.dpToPx(context, 10f) }
        )

        val buttonRow = StyledDialogUi.createActionRow(context)
        val btnCancel = StyledDialogUi.createActionButton(context, "关闭", false)
        buttonRow.addView(btnCancel)
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(context, rootLayout)
        fun refreshOptions() {
            val query = searchInput.text?.toString().orEmpty().trim()
            val agents = buildSelectableAgentList(includeDaihaoBox.isChecked)
                .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            optionsContainer.removeAllViews()
            agents.forEachIndexed { index, agentName ->
                optionsContainer.addView(
                    TextView(context).apply {
                        text = agentName
                        textSize = 14f
                        gravity = Gravity.CENTER_VERTICAL
                        setTypeface(typeface, if (agentName == currentAgent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                        setTextColor(Color.parseColor(if (agentName == currentAgent) "#6B4E1C" else "#8C6C33"))
                        background = StyledDialogUi.createSelectableBackground(agentName == currentAgent)
                        setPadding(
                            StyledDialogUi.dpToPx(context, 14f),
                            StyledDialogUi.dpToPx(context, 11f),
                            StyledDialogUi.dpToPx(context, 14f),
                            StyledDialogUi.dpToPx(context, 11f),
                        )
                        setOnClickListener {
                            onSelect(agentName)
                            dialog.dismiss()
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) topMargin = StyledDialogUi.dpToPx(context, 8f)
                    }
                )
            }
        }

        includeDaihaoBox.setOnCheckedChangeListener { _, _ -> refreshOptions() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshOptions()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        btnClear.setOnClickListener {
            onSelect("")
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        refreshOptions()
    }

    fun showSaveToLibraryDialog(context: Context, defaultName: String, onSave: (String) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "导出到脚本库"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "输入脚本名称后保存到本地脚本库"))
        rootLayout.addView(StyledDialogUi.createFieldLabel(themeContext, "脚本名称"))
        val etName = StyledDialogUi.createStyledInput(themeContext, "请输入脚本名称").apply {
            hint = "请输入脚本名称"
            setText(defaultName)
            protectInputLongPress()
        }
        rootLayout.addView(etName)

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnConfirm = StyledDialogUi.createActionButton(themeContext, "确认", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnConfirm, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            val scriptName = etName.text.toString().trim()
            if (scriptName.isEmpty()) {
                Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
            } else {
                onSave(scriptName)
                dialog.dismiss()
            }
        }
    }

    private fun buildActionKeyboard(context: Context, onKey: (String) -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                StyledDialogUi.dpToPx(context, 4f),
                StyledDialogUi.dpToPx(context, 4f),
                StyledDialogUi.dpToPx(context, 4f),
                StyledDialogUi.dpToPx(context, 4f)
            )
            val rows = listOf(
                listOf("1", "2", "3", "4", "5", "删"),
                listOf("6", "7", "8", "9", "0", "清"),
                listOf("A", "圈", "↑", "↓", "←", "→")
            )
            rows.forEachIndexed { rowIndex, keys ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                keys.forEachIndexed { keyIndex, key ->
                    row.addView(
                        StyledDialogUi.createActionButton(context, key, key == "A" || key == "圈").apply {
                            minWidth = 0
                            minimumWidth = 0
                            setOnClickListener { onKey(key) }
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (keyIndex > 0) leftMargin = StyledDialogUi.dpToPx(context, 4f)
                        }
                    )
                }
                addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (rowIndex > 0) topMargin = StyledDialogUi.dpToPx(context, 6f)
                    }
                )
            }
        }
    }

    private fun handleActionKeyboardKey(editor: EditText, key: String) {
        val editable = editor.text ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        val left = minOf(start, end)
        val right = maxOf(start, end)
        when (key) {
            "←" -> editor.setSelection((left - 1).coerceAtLeast(0))
            "→" -> editor.setSelection((right + 1).coerceAtMost(editable.length))
            "删" -> {
                when {
                    right > left -> editable.delete(left, right)
                    left > 0 -> editable.delete(left - 1, left)
                }
            }
            "清" -> editable.clear()
            else -> editable.replace(left, right, key)
        }
    }
}
