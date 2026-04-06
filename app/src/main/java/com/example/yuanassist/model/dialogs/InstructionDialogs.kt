package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.model.BattleStageNavigationRegistry
import com.example.yuanassist.model.BattleStageTarget
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.ScriptInstruction
import com.example.yuanassist.model.decodeStageAutoNavTarget
import com.example.yuanassist.model.encodeStageAutoNavValue
import com.example.yuanassist.model.isCaveTarget
import com.example.yuanassist.model.isStageAutoNavAutoEnterNextFloorEnabled
import com.example.yuanassist.model.toDisplaySummary
import com.example.yuanassist.utils.DialogUtils

object InstructionDialogs {

    fun showListDialog(
        context: Context,
        instructionList: ArrayList<ScriptInstruction>
    ) {
        val themeContext = DialogUtils.getThemeContext(context)
        val rootLayout = createDialogCard(themeContext)
        rootLayout.addView(createDialogTitle(themeContext, "指令管理"))
        rootLayout.addView(createDialogSubtitle(themeContext, "点击条目编辑，右侧按钮删除"))

        val scrollView = ScrollView(themeContext).apply {
            isFillViewport = true
        }
        val listContainer = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(
            listContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(themeContext, 10f)
            }
        )

        val buttonRow = createActionRow(themeContext)
        val btnAdd = createActionButton(themeContext, "+ 新增指令", true)
        val btnClose = createActionButton(themeContext, "关闭", false)
        buttonRow.addView(btnAdd, createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnClose, createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        lateinit var dialog: AlertDialog

        fun refreshList() {
            listContainer.removeAllViews()
            instructionList.sortWith(compareBy({ it.turn }, { it.step }))

            if (instructionList.isEmpty()) {
                listContainer.addView(TextView(themeContext).apply {
                    text = "暂无指令，请点击下方按钮添加"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#8C7A61"))
                    setBackgroundResource(R.drawable.bg_job_station_icon_button)
                    setPadding(
                        dpToPx(themeContext, 16f),
                        dpToPx(themeContext, 28f),
                        dpToPx(themeContext, 16f),
                        dpToPx(themeContext, 28f)
                    )
                })
                return
            }

            instructionList.forEachIndexed { index, instruction ->
                listContainer.addView(
                    createInstructionRow(
                        context = themeContext,
                        index = index,
                        instruction = instruction,
                        onEdit = {
                            showEditDialog(themeContext, instruction, instructionList) { refreshList() }
                        },
                        onDelete = {
                            showConfirmDeleteDialog(themeContext, instruction) {
                                instructionList.remove(instruction)
                                refreshList()
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                )
            }
        }

        btnAdd.setOnClickListener {
            showEditDialog(themeContext, null, instructionList) { refreshList() }
        }
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        refreshList()
        dialog = showStyledDialog(themeContext, rootLayout)
    }

    private fun showEditDialog(
        context: Context,
        target: ScriptInstruction?,
        instructionList: ArrayList<ScriptInstruction>,
        onSaveSuccess: () -> Unit
    ) {
        val rootLayout = createDialogCard(context)
        rootLayout.addView(createDialogTitle(context, if (target == null) "新增指令" else "编辑指令"))
        rootLayout.addView(createDialogSubtitle(context, "按跟打工具当前能力进行配置"))

        val scrollView = ScrollView(context).apply {
            isFillViewport = true
        }
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(
            formLayout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(context, 10f)
            }
        )

        val tvTurnLabel = createFieldLabel(context, "回合数")
        val etTurn = createStyledInput(context, "第几回合 (必填)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(target?.turn?.toString().orEmpty())
        }
        val tvStepLabel = createFieldLabel(context, "动作序号")
        val etStep = createStyledInput(context, "动作序号 (0 或空为整回合)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (target == null || target.step == 0) "" else target.step.toString())
        }
        val btnType = createSelectorButton(context).apply {
            val currentType = target?.type ?: InstructionType.DELAY_ADD
            text = "类型：${currentType.description}"
            tag = currentType
        }
        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvPrefix = TextView(context).apply {
            text = "切换"
            textSize = 14f
            setTextColor(Color.parseColor("#6C5B43"))
            visibility = View.GONE
        }
        val etValue = createStyledInput(context, "").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(target?.value?.toString() ?: "1000")
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(context, 44f), 1f)
        }
        val tvSuffix = TextView(context).apply {
            text = "ms"
            textSize = 14f
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(dpToPx(context, 10f), 0, 0, 0)
        }
        valueContainer.addView(tvPrefix)
        valueContainer.addView(etValue)
        valueContainer.addView(tvSuffix)

        val btnStage = createSelectorButton(context).apply {
            val initialStage = decodeStageAutoNavTarget(target?.value ?: 0L)
                ?: BattleStageNavigationRegistry.supportedTargets.first()
            text = "关卡：${initialStage.description}"
            tag = initialStage
            visibility = View.GONE
        }
        val tvStageOptionLabel = createFieldLabel(context, "洞窟附属选项").apply {
            visibility = View.GONE
        }
        val btnCaveNextFloor = createSelectorButton(context).apply {
            val initialEnabled = target?.takeIf { it.type == InstructionType.STAGE_AUTO_NAV }
                ?.let { isStageAutoNavAutoEnterNextFloorEnabled(it.value) }
                ?: false
            text = "自动进入下一层：${if (initialEnabled) "开启" else "关闭"}"
            tag = initialEnabled
            visibility = View.GONE
        }
        val tvHint = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, dpToPx(context, 8f), 0, 0)
            visibility = View.GONE
        }
        val tvValueLabel = createFieldLabel(context, "指令参数")

        fun updateCaveOptionUI(type: InstructionType) {
            val selectedStage = btnStage.tag as? BattleStageTarget
            val showCaveOption = type == InstructionType.STAGE_AUTO_NAV && selectedStage?.isCaveTarget() == true
            if (!showCaveOption) {
                btnCaveNextFloor.tag = false
            }
            val enabled = (btnCaveNextFloor.tag as? Boolean) == true
            tvStageOptionLabel.visibility = if (showCaveOption) View.VISIBLE else View.GONE
            btnCaveNextFloor.visibility = if (showCaveOption) View.VISIBLE else View.GONE
            btnCaveNextFloor.text = "自动进入下一层：${if (enabled && showCaveOption) "开启" else "关闭"}"
            if (type == InstructionType.STAGE_AUTO_NAV) {
                tvHint.visibility = View.VISIBLE
                tvHint.text = if (showCaveOption) {
                    "自动导航只会在点击开始后的第 1 回合前执行一次；开启后，洞窟会在每回合开始和最终回合结算后检测下一层"
                } else {
                    "自动导航只会在点击开始后的第 1 回合前执行一次"
                }
            }
        }

        fun updateValueUI(type: InstructionType) {
            val showTurnField = !type.hidesTurnField()
            val showStepField = !type.hidesStepField()
            tvTurnLabel.visibility = if (showTurnField) View.VISIBLE else View.GONE
            etTurn.visibility = if (showTurnField) View.VISIBLE else View.GONE
            tvStepLabel.visibility = if (showStepField) View.VISIBLE else View.GONE
            etStep.visibility = if (showStepField) View.VISIBLE else View.GONE

            when (type) {
                InstructionType.PAUSE,
                InstructionType.ALL_WIPE_CHECK,
                InstructionType.ORANGE_STAR_CHECK -> {
                    valueContainer.visibility = View.GONE
                    btnStage.visibility = View.GONE
                    tvStageOptionLabel.visibility = View.GONE
                    btnCaveNextFloor.visibility = View.GONE
                    tvHint.visibility = View.GONE
                    etValue.setText("0")
                }

                InstructionType.TARGET_SWITCH,
                InstructionType.TARGET_SWITCH_LEFT,
                InstructionType.TARGET_SWITCH_RIGHT -> {
                    valueContainer.visibility = View.VISIBLE
                    btnStage.visibility = View.GONE
                    tvStageOptionLabel.visibility = View.GONE
                    btnCaveNextFloor.visibility = View.GONE
                    tvPrefix.visibility = View.VISIBLE
                    tvPrefix.text = "切换"
                    tvSuffix.text = "次"
                    tvHint.visibility = View.VISIBLE
                    tvHint.text = "例：填写 1 次表示切换一次目标；方向由指令类型决定"
                    if (etValue.text.toString() == "1000" || etValue.text.toString() == "0") {
                        etValue.setText("1")
                    }
                }

                InstructionType.DEATH_CHECK -> {
                    valueContainer.visibility = View.VISIBLE
                    btnStage.visibility = View.GONE
                    tvStageOptionLabel.visibility = View.GONE
                    btnCaveNextFloor.visibility = View.GONE
                    tvPrefix.visibility = View.VISIBLE
                    tvPrefix.text = "检测第"
                    tvSuffix.text = "人"
                    tvHint.visibility = View.VISIBLE
                    tvHint.text = "填写 1-5，表示检测对应站位角色是否阵亡"
                    if (etValue.text.toString() == "1000" || etValue.text.toString() == "0") {
                        etValue.setText("1")
                    }
                }

                InstructionType.STAGE_AUTO_NAV -> {
                    valueContainer.visibility = View.GONE
                    btnStage.visibility = View.VISIBLE
                    etTurn.setText("1")
                    etStep.setText("")
                    val stage = (btnStage.tag as? BattleStageTarget)
                        ?: BattleStageNavigationRegistry.supportedTargets.first()
                    btnStage.text = "关卡：${stage.description}"
                    updateCaveOptionUI(type)
                }

                InstructionType.DELAY_ADD,
                InstructionType.DELAY_SUBTRACT -> {
                    valueContainer.visibility = View.VISIBLE
                    btnStage.visibility = View.GONE
                    tvStageOptionLabel.visibility = View.GONE
                    btnCaveNextFloor.visibility = View.GONE
                    tvPrefix.visibility = View.GONE
                    tvSuffix.text = "ms"
                    tvHint.visibility = View.GONE
                    if (etValue.text.toString() == "1" || etValue.text.toString() == "0") {
                        etValue.setText("1000")
                    }
                }
            }
        }

        val typeOptions = InstructionType.values().filter { it != InstructionType.TARGET_SWITCH }

        btnType.setOnClickListener {
            val currentType = btnType.tag as? InstructionType ?: InstructionType.DELAY_ADD
            showOptionDialog(
                context = context,
                title = "选择指令类型",
                options = typeOptions.map { it.description },
                selectedIndex = typeOptions.indexOf(currentType).coerceAtLeast(0)
            ) { which ->
                val selected = typeOptions[which]
                btnType.text = "类型：${selected.description}"
                btnType.tag = selected
                updateValueUI(selected)
            }
        }

        btnStage.setOnClickListener {
            val options = BattleStageNavigationRegistry.supportedTargets
            val currentStage = btnStage.tag as? BattleStageTarget
            showOptionDialog(
                context = context,
                title = "选择关卡",
                options = options.map { it.description },
                selectedIndex = options.indexOf(currentStage).coerceAtLeast(0)
            ) { which ->
                val selected = options[which]
                btnStage.tag = selected
                btnStage.text = "关卡：${selected.description}"
                updateCaveOptionUI(btnType.tag as? InstructionType ?: InstructionType.DELAY_ADD)
            }
        }

        btnCaveNextFloor.setOnClickListener {
            val currentEnabled = (btnCaveNextFloor.tag as? Boolean) == true
            showOptionDialog(
                context = context,
                title = "自动进入下一层",
                options = listOf("关闭", "开启"),
                selectedIndex = if (currentEnabled) 1 else 0
            ) { which ->
                val enabled = which == 1
                btnCaveNextFloor.tag = enabled
                btnCaveNextFloor.text = "自动进入下一层：${if (enabled) "开启" else "关闭"}"
            }
        }

        formLayout.addView(tvTurnLabel)
        formLayout.addView(etTurn)
        formLayout.addView(tvStepLabel)
        formLayout.addView(etStep)
        formLayout.addView(createFieldLabel(context, "指令类型"))
        formLayout.addView(btnType)
        formLayout.addView(tvValueLabel)
        formLayout.addView(valueContainer)
        formLayout.addView(btnStage)
        formLayout.addView(tvStageOptionLabel)
        formLayout.addView(btnCaveNextFloor)
        formLayout.addView(tvHint)

        val buttonRow = createActionRow(context)
        val btnCancel = createActionButton(context, "取消", false)
        val btnSave = createActionButton(context, "保存", true)
        buttonRow.addView(btnCancel, createWeightedButtonParams(context, false))
        buttonRow.addView(btnSave, createWeightedButtonParams(context, true))
        rootLayout.addView(buttonRow)

        updateValueUI(target?.type ?: InstructionType.DELAY_ADD)

        val dialog = showStyledDialog(context, rootLayout)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnSave.setOnClickListener {
            try {
                val type = btnType.tag as? InstructionType ?: InstructionType.DELAY_ADD
                val turnStr = if (type.hidesTurnField()) "1" else etTurn.text.toString().trim()
                if (turnStr.isEmpty()) {
                    Toast.makeText(context, "必须填写回合数", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val turn = type.normalizeTurn(turnStr.toInt())
                val step = type.normalizeStep(etStep.text.toString().trim().toIntOrNull() ?: 0)
                val value = when (type) {
                    InstructionType.STAGE_AUTO_NAV -> {
                        val targetStage = (btnStage.tag as? BattleStageTarget)
                            ?: BattleStageNavigationRegistry.supportedTargets.first()
                        val autoEnterNextFloor =
                            targetStage.isCaveTarget() && ((btnCaveNextFloor.tag as? Boolean) == true)
                        encodeStageAutoNavValue(targetStage, autoEnterNextFloor)
                    }
                    else -> etValue.text.toString().trim().toLongOrNull() ?: 0L
                }

                if (type == InstructionType.DEATH_CHECK && value !in 1L..5L) {
                    Toast.makeText(context, "阵亡检测的人物序号只能填写 1-5", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val normalizedInstruction = ScriptInstruction(turn, step, type, value).normalized()
                if (target == null) {
                    instructionList.add(normalizedInstruction)
                    Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                } else {
                    target.turn = normalizedInstruction.turn
                    target.step = normalizedInstruction.step
                    target.type = normalizedInstruction.type
                    target.value = normalizedInstruction.value
                    Toast.makeText(context, "已修改", Toast.LENGTH_SHORT).show()
                }

                onSaveSuccess()
                dialog.dismiss()
            } catch (_: Exception) {
                Toast.makeText(context, "输入格式错误", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmDeleteDialog(
        context: Context,
        instruction: ScriptInstruction,
        onConfirm: () -> Unit
    ) {
        val rootLayout = createDialogCard(context)
        rootLayout.addView(createDialogTitle(context, "确认删除"))
        rootLayout.addView(createDialogSubtitle(context, buildInstructionDetailText(instruction)))

        val buttonRow = createActionRow(context)
        val btnCancel = createActionButton(context, "取消", false)
        val btnDelete = createActionButton(context, "删除", true).apply {
            setTextColor(Color.parseColor("#B64D3C"))
        }
        buttonRow.addView(btnCancel, createWeightedButtonParams(context, false))
        buttonRow.addView(btnDelete, createWeightedButtonParams(context, true))
        rootLayout.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 14f)
            }
        )

        val dialog = showStyledDialog(context, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
    }

    private fun showOptionDialog(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val rootLayout = createDialogCard(context)
        rootLayout.addView(createDialogTitle(context, title))

        val scrollView = ScrollView(context).apply {
            isFillViewport = true
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(context, 10f)
            }
        )

        val btnClose = createActionButton(context, "取消", false)
        rootLayout.addView(btnClose)

        lateinit var dialog: AlertDialog

        options.forEachIndexed { index, option ->
            val optionView = TextView(context).apply {
                text = option
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, if (index == selectedIndex) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    Color.parseColor(
                        if (index == selectedIndex) "#6B4E1C" else "#8C6C33"
                    )
                )
                background = createSelectableBackground(index == selectedIndex)
                setPadding(
                    dpToPx(context, 14f),
                    dpToPx(context, 12f),
                    dpToPx(context, 14f),
                    dpToPx(context, 12f)
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dpToPx(context, 8f)
                    }
                }
                setOnClickListener {
                    onSelect(index)
                    dialog.dismiss()
                }
            }
            optionsContainer.addView(optionView)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog = showStyledDialog(context, rootLayout)
    }

    private fun createInstructionRow(
        context: Context,
        index: Int,
        instruction: ScriptInstruction,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_job_station_icon_button)
            setPadding(
                dpToPx(context, 14f),
                dpToPx(context, 12f),
                dpToPx(context, 14f),
                dpToPx(context, 12f)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = dpToPx(context, 10f)
                }
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onEdit() }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    text = "${index + 1}. ${buildInstructionTimingText(instruction)}"
                    textSize = 14f
                    setTextColor(Color.parseColor("#2F261B"))
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                addView(createActionButton(context, "删除", false).apply {
                    setTextColor(Color.parseColor("#B64D3C"))
                    textSize = 12f
                    setPadding(
                        dpToPx(context, 10f),
                        dpToPx(context, 5f),
                        dpToPx(context, 10f),
                        dpToPx(context, 5f)
                    )
                    setOnClickListener { onDelete() }
                })
            })

            addView(TextView(context).apply {
                text = buildInstructionDetailText(instruction)
                textSize = 13f
                setTextColor(Color.parseColor("#6C5B43"))
                setPadding(0, dpToPx(context, 8f), 0, 0)
            })
        }
    }

    private fun buildInstructionTimingText(instruction: ScriptInstruction): String {
        val normalized = instruction.normalized()
        return normalized.type.formatTiming(normalized.turn, normalized.step)
    }

    private fun buildInstructionDetailText(instruction: ScriptInstruction): String {
        val summary = instruction.toDisplaySummary()
        return summary.substringAfter(" | ", summary)
    }

    private fun createDialogCard(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_job_station_card)
            setPadding(
                dpToPx(context, 18f),
                dpToPx(context, 18f),
                dpToPx(context, 18f),
                dpToPx(context, 18f)
            )
            minimumWidth = dpToPx(context, 300f)
        }
    }

    private fun createDialogTitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#2F261B"))
        }
    }

    private fun createDialogSubtitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, dpToPx(context, 6f), 0, 0)
        }
    }

    private fun createFieldLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(0, dpToPx(context, 12f), 0, dpToPx(context, 6f))
        }
    }

    private fun createStyledInput(context: Context, hint: String): EditText {
        return EditText(context).apply {
            this.hint = hint
            textSize = 14f
            setTextColor(Color.parseColor("#4E3C1E"))
            setHintTextColor(Color.parseColor("#9A8A71"))
            setBackgroundResource(R.drawable.bg_job_station_icon_button)
            setPadding(
                dpToPx(context, 14f),
                0,
                dpToPx(context, 14f),
                0
            )
            minHeight = dpToPx(context, 44f)
        }
    }

    private fun createSelectorButton(context: Context): TextView {
        return createActionButton(context, "", false).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textSize = 14f
            setTextColor(Color.parseColor("#6B4E1C"))
            minHeight = dpToPx(context, 44f)
        }
    }

    private fun createActionRow(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 14f)
            }
        }
    }

    private fun createActionButton(
        context: Context,
        text: String,
        primary: Boolean
    ): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (primary) "#6B4E1C" else "#8C6C33"))
            setBackgroundResource(if (primary) R.drawable.bg_job_station_chip else R.drawable.bg_job_station_icon_button)
            setPadding(
                dpToPx(context, 14f),
                dpToPx(context, 10f),
                dpToPx(context, 14f),
                dpToPx(context, 10f)
            )
            isClickable = true
            isFocusable = true
        }
    }

    private fun createWeightedButtonParams(context: Context, hasStartMargin: Boolean): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (hasStartMargin) {
                marginStart = dpToPx(context, 10f)
            }
        }
    }

    private fun createSelectableBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 18f
            if (selected) {
                setColor(Color.parseColor("#F6D59A"))
                setStroke(1, Color.parseColor("#C88A2C"))
            } else {
                setColor(Color.parseColor("#F8F2E5"))
                setStroke(1, Color.parseColor("#D8C18A"))
            }
        }
    }

    private fun showStyledDialog(context: Context, content: View): AlertDialog {
        val dialog = DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(context)
                .setView(content)
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
