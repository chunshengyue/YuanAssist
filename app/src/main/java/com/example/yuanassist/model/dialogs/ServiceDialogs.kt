// 文件路径：yuanassist/ui/dialogs/ServiceDialogs.kt
package com.example.yuanassist.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.disableShowSoftInput
import com.example.yuanassist.utils.protectInputLongPress

object ServiceDialogs {

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
            ).apply { topMargin = StyledDialogUi.dpToPx(themeContext, 12f) }
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

    fun showExportImageSettingsDialog(context: Context, onExport: (Array<String>) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "导出设置"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "设置导出图片的五列标题"))
        val dialogView =
            LayoutInflater.from(themeContext).inflate(R.layout.dialog_edit_headers, null)
        val ets = arrayOf(
            dialogView.findViewById<EditText>(R.id.et_header_1),
            dialogView.findViewById<EditText>(R.id.et_header_2),
            dialogView.findViewById<EditText>(R.id.et_header_3),
            dialogView.findViewById<EditText>(R.id.et_header_4),
            dialogView.findViewById<EditText>(R.id.et_header_5)
        )
        for (et in ets) {
            et.protectInputLongPress()
        }
        rootLayout.addView(
            dialogView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = StyledDialogUi.dpToPx(themeContext, 12f) }
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
            onExport(Array(5) { i -> ets[i].text.toString() })
            dialog.dismiss()
        }
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
