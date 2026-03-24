package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
        val rootLayout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        val scrollView = ScrollView(themeContext)
        val listContainer = LinearLayout(themeContext).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listContainer)

        fun refreshList() {
            listContainer.removeAllViews()
            val notedTurns = currentDisplayData.filter { it.remark.isNotEmpty() }

            if (notedTurns.isEmpty()) {
                listContainer.addView(TextView(themeContext).apply {
                    text = "暂无备注，请点击下方按钮添加"
                    setTextColor(Color.GRAY)
                    textSize = 14f
                    setPadding(20, 40, 20, 40)
                    gravity = Gravity.CENTER
                })
            } else {
                notedTurns.forEach { turnData ->
                    val row = LinearLayout(themeContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 15, 0, 15)
                    }
                    val tvInfo = TextView(themeContext).apply {
                        text = "T${turnData.turnNumber}: ${turnData.remark}"
                        textSize = 15f
                        setTextColor(Color.BLACK)
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
                        text = "×"
                        textSize = 20f
                        setTextColor(Color.RED)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(40, 0, 20, 0)
                        setOnClickListener {
                            turnData.remark = ""
                            refreshList()
                            onDataChanged()
                            Toast.makeText(context, "备注已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(tvInfo)
                    row.addView(btnDelete)
                    listContainer.addView(row)
                    listContainer.addView(View(themeContext).apply {
                        setBackgroundColor(Color.LTGRAY)
                        layoutParams =
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    })
                }
            }
        }
        refreshList()

        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        rootLayout.addView(Button(themeContext).apply {
            text = "+ 新增备注"
            setOnClickListener {
                showEditDialog(themeContext, currentDisplayData, null) {
                    refreshList()
                    onDataChanged()
                }
            }
        })

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("备注管理")
                .setView(rootLayout)
                .setPositiveButton("关闭", null)
        )
    }

    // 显示新增/编辑备注的弹窗
    private fun showEditDialog(
        context: Context,
        currentDisplayData: List<TurnData>,
        targetData: TurnData?,
        onSave: () -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }
        val etTurn = EditText(context).apply {
            hint = "第几回合 (例如: 1)"
            inputType = InputType.TYPE_CLASS_NUMBER
            if (targetData != null) {
                setText(targetData.turnNumber.toString())
                isEnabled = false
            }
        }
        val etContent = EditText(context).apply {
            hint = "请输入备注内容"
            setText(targetData?.remark ?: "")
        }

        layout.addView(TextView(context).apply { text = "回合数:"; textSize = 12f })
        layout.addView(etTurn)
        layout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 20) })
        layout.addView(TextView(context).apply { text = "备注内容:"; textSize = 12f })
        layout.addView(etContent)

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(context)
                .setTitle(if (targetData == null) "新增备注" else "编辑备注")
                .setView(layout)
                .setPositiveButton("保存") { _, _ ->
                    val turnStr = etTurn.text.toString()
                    val content = etContent.text.toString()
                    if (turnStr.isEmpty()) {
                        Toast.makeText(context, "请输入回合数", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val turnNum = turnStr.toInt()
                    val target = currentDisplayData.find { it.turnNumber == turnNum }

                    if (target != null) {
                        target.remark = content
                        onSave()
                        Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "未找到第 $turnNum 回合，请先录制或添加回合",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("取消", null)
        )
    }
}
