// 文件路径：yuanassist/ui/dialogs/ServiceDialogs.kt
package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.disableShowSoftInput

object ServiceDialogs {

    fun showTextImportDialog(context: Context, onImport: (String) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val container = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        val editText = EditText(themeContext).apply {
            hint = "请在此处粘贴文本..."
            setTextColor(Color.BLACK)
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            disableShowSoftInput()
        }
        val btnPaste = Button(themeContext).apply {
            text = "点击粘贴剪贴板内容"
            setTextColor(Color.WHITE)
            background.setTint(0xFF1976D2.toInt())
            setOnClickListener {
                editText.requestFocus()
                if (editText.onTextContextMenuItem(android.R.id.paste)) {
                    Toast.makeText(context, "粘贴成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "粘贴失败或剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(editText)
        container.addView(
            btnPaste,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        )

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("导入文字")
                .setView(container)
                .setPositiveButton("确定") { _, _ -> onImport(editText.text.toString()) }
                .setNegativeButton("取消", null)
        )
    }

    fun showInsertTurnDialog(context: Context, onInsert: (Int) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val layout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        val etTurn = EditText(themeContext).apply {
            hint = "在第几回合后新增？(例如: 12)"
            inputType = InputType.TYPE_CLASS_NUMBER
            disableShowSoftInput()
        }
        layout.addView(etTurn)

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("新增回合")
                .setView(layout)
                .setPositiveButton("确定") { _, _ ->
                    val turnStr = etTurn.text.toString()
                    if (turnStr.isNotEmpty()) {
                        onInsert(turnStr.toInt())
                    } else {
                        Toast.makeText(context, "请输入回合数", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
        )
    }

    fun showEditActionDialog(
        context: Context,
        currentText: String,
        onSave: (String) -> Unit,
        onClear: () -> Unit
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val editText = EditText(themeContext).apply {
            setText(currentText)
            setTextColor(Color.BLACK)
            disableShowSoftInput()
            setPadding(50, 50, 50, 50)
        }
        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("编辑")
                .setView(editText)
                .setPositiveButton("确定") { _, _ -> onSave(editText.text.toString()) }
                .setNegativeButton("取消", null)
                .setNeutralButton("清空") { _, _ -> onClear() }
        )
    }

    fun showExportImageSettingsDialog(context: Context, onExport: (Array<String>) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
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
            et.disableShowSoftInput()
            et.isLongClickable = false
            et.setOnLongClickListener { true }
            et.setTextIsSelectable(false)
        }

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("导出设置")
                .setView(dialogView)
                .setPositiveButton("生成图片") { _, _ ->
                    onExport(Array(5) { i -> ets[i].text.toString() })
                }
                .setNegativeButton("取消", null)
        )
    }

    fun showSaveToLibraryDialog(context: Context, defaultName: String, onSave: (String) -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val layout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        val etName = EditText(themeContext).apply {
            hint = "请输入脚本名称"
            setText(defaultName)
            disableShowSoftInput()
        }
        layout.addView(etName)

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("导出到脚本库")
                .setView(layout)
                .setPositiveButton("确认") { _, _ ->
                    val scriptName = etName.text.toString().trim()
                    if (scriptName.isEmpty()) {
                        Toast.makeText(context, "名称不能为空", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(scriptName)
                    }
                }
                .setNegativeButton("取消", null)
        )
    }
}
