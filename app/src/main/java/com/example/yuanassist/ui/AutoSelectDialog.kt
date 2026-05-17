package com.example.yuanassist.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.view.Gravity
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
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "自动选择角色设置"))

        val switchAutoSelect = Switch(themeContext).apply {
            text = "开启自动选择密探"
            isChecked = isCurrentlyEnabled
            textSize = 16f
            setTextColor(Color.parseColor("#2F261B"))
            setPadding(0, StyledDialogUi.dpToPx(themeContext, 10f), 0, StyledDialogUi.dpToPx(themeContext, 8f))
        }
        rootLayout.addView(switchAutoSelect)

        val tvHint = TextView(themeContext).apply {
            text =
                "小提示：\n若选择开启，必须在编队（选人）界面点击开始跟打。\n若关闭，开始战斗后画面稳定再点击开始跟打。"
            textSize = 12f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, 0, 0, StyledDialogUi.dpToPx(themeContext, 12f))
        }
        rootLayout.addView(tvHint)

        val inputFields = Array(5) { i ->
            AutoCompleteTextView(themeContext).apply {
                hint = "请输入 ${i + 1} 号位角色名"
                setText(currentAgents[i])
                textSize = 14f
                setTextColor(Color.parseColor("#4E3C1E"))
                setHintTextColor(Color.parseColor("#9A8A71"))
                setBackgroundResource(com.example.yuanassist.R.drawable.bg_job_station_icon_button)
                setPadding(
                    StyledDialogUi.dpToPx(themeContext, 12f),
                    0,
                    StyledDialogUi.dpToPx(themeContext, 12f),
                    0
                )
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
                setPadding(0, StyledDialogUi.dpToPx(themeContext, 5f), 0, StyledDialogUi.dpToPx(themeContext, 5f))
            }
            val tvLabel = TextView(themeContext).apply {
                text = "${i + 1}号位:"
                textSize = 14f
                setTextColor(Color.parseColor("#6C5B43"))
                width = StyledDialogUi.dpToPx(themeContext, 60f)
            }
            val lp = LinearLayout.LayoutParams(0, StyledDialogUi.dpToPx(themeContext, 44f), 1f)
            inputFields[i].layoutParams = lp

            row.addView(tvLabel)
            row.addView(inputFields[i])
            rootLayout.addView(row)
        }

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnSave = StyledDialogUi.createActionButton(themeContext, "保存", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnSave, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnSave.setOnClickListener {
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
