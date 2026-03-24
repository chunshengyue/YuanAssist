package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.utils.DialogUtils

object AutoSelectDialog {

    // 透過參數傳入初始狀態，並用 onSave 回呼返回結果，與 Service 完美解耦
    fun show(
        context: Context,
        isCurrentlyEnabled: Boolean,
        currentAgents: Array<String>,
        allAgentsLibrary: Array<String>,
        onSave: (isEnabled: Boolean, newAgents: Array<String>) -> Unit
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val layout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val switchAutoSelect = Switch(themeContext).apply {
            text = "开启自动选择密探"
            isChecked = isCurrentlyEnabled
            textSize = 16f
            setPadding(0, 0, 0, 10)
        }
        layout.addView(switchAutoSelect)

        val tvHint = TextView(themeContext).apply {
            text =
                "小提示：\n若选择开启，必须在编队（选人）界面点击开始跟打。\n若关闭，开始战斗后画面稳定再点击开始跟打。"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 40)
        }
        layout.addView(tvHint)

        val inputFields = Array(5) { i ->
            AutoCompleteTextView(themeContext).apply {
                hint = "请输入 ${i + 1} 号位角色名"
                setText(currentAgents[i])
                setTextColor(Color.BLACK)
                setSingleLine()
                val adapter = ArrayAdapter(
                    themeContext,
                    android.R.layout.simple_dropdown_item_1line,
                    allAgentsLibrary
                )
                setAdapter(adapter)
                threshold = 1
                dropDownHeight = (200 * context.resources.displayMetrics.density).toInt()
            }
        }

        for (i in 0 until 5) {
            val row = LinearLayout(themeContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
            }
            val tvLabel = TextView(themeContext).apply {
                text = "${i + 1}号位:"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                width = (60 * context.resources.displayMetrics.density).toInt()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            inputFields[i].layoutParams = lp

            row.addView(tvLabel)
            row.addView(inputFields[i])
            layout.addView(row)
        }

        val builder = AlertDialog.Builder(themeContext)
            .setTitle("自动选择角色设置")
            .setView(layout)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)

        // 使用我們剛寫好的 Utils
        val dialog = DialogUtils.safeShowOverlayDialog(builder)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val isChecked = switchAutoSelect.isChecked
            val tempNames = Array(5) { "" }
            var allFilled = true

            for (i in 0 until 5) {
                tempNames[i] = inputFields[i].text.toString().trim()
                if (tempNames[i].isEmpty()) {
                    allFilled = false
                }
            }

            if (isChecked && !allFilled) {
                Toast.makeText(context, "必须填满 5 个密探才能开启自动选择！", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // 校驗通過，透過回呼把資料傳出去，關閉彈窗
            onSave(isChecked, tempNames)
            dialog.dismiss()
        }
    }
}