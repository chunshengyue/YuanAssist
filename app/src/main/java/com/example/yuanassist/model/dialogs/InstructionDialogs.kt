package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.ScriptInstruction
import com.example.yuanassist.utils.DialogUtils

object InstructionDialogs {

    fun showListDialog(
        context: Context,
        instructionList: ArrayList<ScriptInstruction>
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL; setPadding(
            20,
            20,
            20,
            20
        )
        }
        val scrollView = ScrollView(themeContext)
        val listContainer = LinearLayout(themeContext).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listContainer)

        fun refreshList() {
            listContainer.removeAllViews()
            instructionList.sortWith(compareBy({ it.turn }, { it.step }))

            if (instructionList.isEmpty()) {
                listContainer.addView(TextView(themeContext).apply {
                    text = "暂无指令，请点击下方按钮添加"; setTextColor(Color.GRAY); textSize =
                    14f; setPadding(20, 40, 20, 40); gravity = Gravity.CENTER
                })
            } else {
                instructionList.forEachIndexed { index, ins ->
                    val row = LinearLayout(themeContext).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity =
                        Gravity.CENTER_VERTICAL; setPadding(0, 15, 0, 15); setBackgroundResource(
                        android.R.drawable.list_selector_background
                    )
                    }

                    val descText = buildString {
                        append("${index + 1}. 第 ${ins.turn} 回合")
                        if (ins.step == 0) append(" (整回合)") else append(" 动作 ${ins.step} 后")
                        append(" : [${ins.type.description}]")
                        when (ins.type) {
                            InstructionType.DELAY_ADD -> append(" ${ins.value}ms")
                            InstructionType.TARGET_SWITCH -> append(" 滑动 ${ins.value} 次")
                            InstructionType.PAUSE -> {}
                        }
                    }

                    val tvInfo = TextView(themeContext).apply {
                        text = descText; textSize = 15f; setTextColor(Color.BLACK); layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            showEditDialog(
                                themeContext,
                                ins,
                                instructionList
                            ) { refreshList() }
                        }
                    }

                    val btnDelete = TextView(themeContext).apply {
                        text = "✕"; textSize = 20f; setTextColor(Color.RED); setTypeface(
                        null,
                        Typeface.BOLD
                    ); setPadding(40, 0, 20, 0)
                        setOnClickListener {
                            AlertDialog.Builder(themeContext).setTitle("确认删除?")
                                .setMessage(descText)
                                .setPositiveButton("删除") { _, _ ->
                                    instructionList.remove(ins); refreshList(); Toast.makeText(
                                    context,
                                    "已删除",
                                    Toast.LENGTH_SHORT
                                ).show()
                                }
                                .setNegativeButton("取消", null)
                                .also { DialogUtils.safeShowOverlayDialog(it) }
                        }
                    }
                    row.addView(tvInfo); row.addView(btnDelete); listContainer.addView(row)
                    listContainer.addView(View(themeContext).apply {
                        setBackgroundColor(Color.LTGRAY); layoutParams =
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
            text = "+ 新增指令"; setOnClickListener {
            showEditDialog(
                themeContext,
                null,
                instructionList
            ) { refreshList() }
        }
        })
        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext).setTitle("指令管理").setView(rootLayout)
                .setPositiveButton("关闭", null)
        )
    }

    private fun showEditDialog(
        context: Context,
        target: ScriptInstruction?,
        instructionList: ArrayList<ScriptInstruction>,
        onSaveSuccess: () -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(
            50,
            40,
            50,
            40
        )
        }

        val etTurn = EditText(context).apply {
            hint = "第几回合 (必填)"; inputType =
            InputType.TYPE_CLASS_NUMBER; setText(target?.turn?.toString() ?: "")
        }
        val etStep = EditText(context).apply {
            hint = "动作序号 (0或空为整回合)"; inputType =
            InputType.TYPE_CLASS_NUMBER; setText(if (target == null || target.step == 0) "" else target.step.toString())
        }

        val btnType = Button(context).apply {
            val currentType = target?.type ?: InstructionType.DELAY_ADD
            text = "类型: ${currentType.description}"
            tag = currentType
        }

        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val tvPrefix = TextView(context).apply {
            text = "右滑 "; textSize = 16f; setTextColor(Color.BLACK); visibility = View.GONE
        }
        val etValue = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setText(
            target?.value?.toString() ?: "1000"
        ); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvSuffix = TextView(context).apply {
            text = "ms"; textSize = 16f; setTextColor(Color.BLACK); setPadding(10, 0, 0, 0)
        }

        valueContainer.addView(tvPrefix); valueContainer.addView(etValue); valueContainer.addView(
            tvSuffix
        )

        val tvHint = TextView(context).apply {
            text = "例：右滑两次即从攻击一号位变为攻击三号位"; textSize =
            12f; setTextColor(Color.GRAY); setPadding(0, 15, 0, 0); visibility = View.GONE
        }

        fun updateValueUI(type: InstructionType) {
            when (type) {
                InstructionType.PAUSE -> {
                    valueContainer.visibility = View.GONE; tvHint.visibility =
                        View.GONE; etValue.setText("0")
                }

                InstructionType.TARGET_SWITCH -> {
                    valueContainer.visibility = View.VISIBLE; tvPrefix.visibility =
                        View.VISIBLE; tvSuffix.text = "次"; tvHint.visibility =
                        View.VISIBLE; if (etValue.text.toString() == "1000" || etValue.text.toString() == "0") etValue.setText(
                        "1"
                    )
                }

                InstructionType.DELAY_ADD -> {
                    valueContainer.visibility = View.VISIBLE; tvPrefix.visibility =
                        View.GONE; tvSuffix.text = "ms"; tvHint.visibility =
                        View.GONE; if (etValue.text.toString() == "1" || etValue.text.toString() == "0") etValue.setText(
                        "1000"
                    )
                }
            }
        }
        updateValueUI(target?.type ?: InstructionType.DELAY_ADD)

        btnType.setOnClickListener {
            val options = InstructionType.values()
            val builder = AlertDialog.Builder(context)
                .setItems(options.map { it.description }.toTypedArray()) { _, which ->
                    val selected = options[which]
                    btnType.text = "类型: ${selected.description}"
                    btnType.tag = selected
                    updateValueUI(selected)
                }
            DialogUtils.safeShowOverlayDialog(builder)
        }

        layout.addView(TextView(context).apply {
            text = "回合数:"; textSize = 12f
        }); layout.addView(etTurn)
        layout.addView(TextView(context).apply {
            text = "动作序号 (可选):"; textSize = 12f
        }); layout.addView(etStep)
        layout.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 20)
        }); layout.addView(btnType)
        layout.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, 20)
        }); layout.addView(valueContainer)
        layout.addView(tvHint)

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(context)
            .setTitle(if (target == null) "新增指令" else "编辑指令").setView(layout)
            .setPositiveButton("保存") { _, _ ->
                try {
                    val turnStr = etTurn.text.toString()
                    if (turnStr.isEmpty()) {
                        Toast.makeText(context, "必须填写回合数", Toast.LENGTH_SHORT)
                            .show(); return@setPositiveButton
                    }
                    val turn = turnStr.toInt();
                    val step = etStep.text.toString().toIntOrNull() ?: 0
                    val type = btnType.tag as InstructionType;
                    val value = etValue.text.toString().toLongOrNull() ?: 0L

                    if (target == null) {
                        instructionList.add(ScriptInstruction(turn, step, type, value))
                        Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                    } else {
                        target.turn = turn; target.step = step; target.type = type; target.value =
                            value
                        Toast.makeText(context, "已修改", Toast.LENGTH_SHORT).show()
                    }
                    onSaveSuccess()
                } catch (e: Exception) {
                    Toast.makeText(context, "输入格式错误", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("取消", null)
        )
    }
}