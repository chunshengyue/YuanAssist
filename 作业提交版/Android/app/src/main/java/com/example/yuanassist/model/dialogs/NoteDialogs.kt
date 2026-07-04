package com.example.yuanassist.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.model.TurnData
import com.example.yuanassist.utils.DialogUtils

object NoteDialogs {

    // 显示备注列表
    fun showListDialog(
        context: Context,
        currentDisplayData: List<TurnData>,
        onDataChanged: () -> Unit // 数据变更时的回调
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "备注管理"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "点击备注编辑，右侧按钮删除"))

        val listContainer = StyledDialogUi.createScrollableContainer(themeContext, rootLayout)

        fun refreshList() {
            listContainer.removeAllViews()
            val notedTurns = currentDisplayData.filter { it.remark.isNotEmpty() }

            if (notedTurns.isEmpty()) {
                listContainer.addView(TextView(themeContext).apply {
                    text = "暂无备注，请点击下方按钮添加"
                    setTextColor(Color.parseColor("#8C7A61"))
                    textSize = 14f
                    setBackgroundResource(R.drawable.bg_job_station_icon_button)
                    setPadding(
                        StyledDialogUi.dpToPx(themeContext, 16f),
                        StyledDialogUi.dpToPx(themeContext, 28f),
                        StyledDialogUi.dpToPx(themeContext, 16f),
                        StyledDialogUi.dpToPx(themeContext, 28f)
                    )
                    gravity = Gravity.CENTER
                })
            } else {
                notedTurns.forEach { turnData ->
                    val row = LinearLayout(themeContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_job_station_icon_button)
                        setPadding(
                            StyledDialogUi.dpToPx(themeContext, 14f),
                            StyledDialogUi.dpToPx(themeContext, 12f),
                            StyledDialogUi.dpToPx(themeContext, 14f),
                            StyledDialogUi.dpToPx(themeContext, 12f)
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (listContainer.childCount > 0) {
                                topMargin = StyledDialogUi.dpToPx(themeContext, 10f)
                            }
                        }
                        isClickable = true
                        isFocusable = true
                    }
                    val tvInfo = TextView(themeContext).apply {
                        text = "T${turnData.turnNumber}: ${turnData.remark}"
                        textSize = 15f
                        setTextColor(Color.parseColor("#2F261B"))
                        layoutParams =
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            showEditDialog(themeContext, currentDisplayData, turnData) {
                                refreshList()
                                onDataChanged()
                            }
                        }
                    }
                    val btnDelete = TextView(themeContext).apply {
                        text = "删除"
                        textSize = 12f
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#B64D3C"))
                        setTypeface(null, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_job_station_icon_button)
                        setPadding(
                            StyledDialogUi.dpToPx(themeContext, 10f),
                            StyledDialogUi.dpToPx(themeContext, 5f),
                            StyledDialogUi.dpToPx(themeContext, 10f),
                            StyledDialogUi.dpToPx(themeContext, 5f)
                        )
                        setOnClickListener {
                            turnData.remark = ""
                            refreshList()
                            onDataChanged()
                            Toast.makeText(context, "备注已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(tvInfo)
                    row.addView(btnDelete)
                    row.setOnClickListener {
                        showEditDialog(themeContext, currentDisplayData, turnData) {
                            refreshList()
                            onDataChanged()
                        }
                    }
                    listContainer.addView(row)
                }
            }
        }
        refreshList()

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnAdd = StyledDialogUi.createActionButton(themeContext, "+ 新增备注", true).apply {
            setOnClickListener {
                showEditDialog(themeContext, currentDisplayData, null) {
                    refreshList()
                    onDataChanged()
                }
            }
        }
        val btnClose = StyledDialogUi.createActionButton(themeContext, "关闭", false)
        buttonRow.addView(btnAdd, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnClose, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnClose.setOnClickListener { dialog.dismiss() }
    }

    // 显示新增/编辑备注的弹窗
    private fun showEditDialog(
        context: Context,
        currentDisplayData: List<TurnData>,
        targetData: TurnData?,
        onSave: () -> Unit
    ) {
        val rootLayout = StyledDialogUi.createDialogCard(context)
        rootLayout.addView(StyledDialogUi.createDialogTitle(context, if (targetData == null) "新增备注" else "编辑备注"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(context, "设置指定回合的备注内容"))

        val etTurn = StyledDialogUi.createStyledInput(context, "第几回合 (例如: 1)").apply {
            hint = "第几回合 (例如: 1)"
            inputType = InputType.TYPE_CLASS_NUMBER
            if (targetData != null) {
                setText(targetData.turnNumber.toString())
                isEnabled = false
            }
        }
        val etContent = StyledDialogUi.createStyledInput(context, "请输入备注内容").apply {
            hint = "请输入备注内容"
            setText(targetData?.remark ?: "")
        }

        rootLayout.addView(StyledDialogUi.createFieldLabel(context, "回合数"))
        rootLayout.addView(etTurn)
        rootLayout.addView(StyledDialogUi.createFieldLabel(context, "备注内容"))
        rootLayout.addView(etContent)

        val buttonRow = StyledDialogUi.createActionRow(context)
        val btnCancel = StyledDialogUi.createActionButton(context, "取消", false)
        val btnSave = StyledDialogUi.createActionButton(context, "保存", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(context, false))
        buttonRow.addView(btnSave, StyledDialogUi.createWeightedButtonParams(context, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(context, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val turnStr = etTurn.text.toString()
            val content = etContent.text.toString()
            if (turnStr.isEmpty()) {
                Toast.makeText(context, "请输入回合数", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val turnNum = turnStr.toInt()
            val target = currentDisplayData.find { it.turnNumber == turnNum }

            if (target != null) {
                target.remark = content
                onSave()
                Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(
                    context,
                    "未找到第 $turnNum 回合，请先录制或添加回合",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
