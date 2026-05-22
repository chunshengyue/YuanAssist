package com.example.yuanassist.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.core.DailyPlanGraphBuilder
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.applyYuanInputStyle
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.Locale

class RecordedDailyScriptViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT_NAME = "extra_script_name"
        const val EXTRA_SCRIPT_JSON = "extra_script_json"
        const val EXTRA_TEMPLATE_DIR_PATH = "extra_template_dir_path"
        const val EXTRA_SCRIPT_FILE_PATH = "extra_script_file_path"
        const val EXTRA_EDITABLE = "extra_editable"
        const val DEFAULT_EXIT_KEY = "success"
    }

    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val supportedActions = listOf("CLICK", "MATCH_TEMPLATE", "OCR", "SET_VAR", "BACK")
    private val alignOptions = listOf("center", "top", "bottom")

    private lateinit var titleView: TextView
    private lateinit var summaryView: TextView
    private lateinit var modeView: TextView
    private lateinit var branchTitleView: TextView
    private lateinit var branchHintView: TextView
    private lateinit var branchTabContainer: LinearLayout
    private lateinit var pathContainer: LinearLayout
    private lateinit var exportButton: Button
    private lateinit var saveButton: Button
    private lateinit var rawJsonView: TextView

    private var scriptName: String = ""
    private var templateDirPath: String? = null
    private var scriptFilePath: String? = null
    private var editable = false
    private var rawJsonVisible = false
    private var dirty = false

    private lateinit var persistedPlan: DailyTaskPlan
    private val workingTasks = mutableListOf<DailyTask>()
    private val branchSelections = mutableMapOf<Int, String>()
    private var activeSwitchTaskId: Int? = null
    private var startTaskId: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!loadPlanFromIntent()) return
        buildContentView()
        renderPlan()
    }

    override fun onBackPressed() {
        if (!dirty) {
            super.onBackPressed()
            return
        }
        DialogUtils.showStyledDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle("放弃修改")
                .setMessage("还有未保存的更改，确认直接返回吗？")
                .setPositiveButton("返回") { _, _ ->
                    dirty = false
                    finish()
                }
                .setNegativeButton("取消", null),
        )
    }

    private fun loadPlanFromIntent(): Boolean {
        scriptName = intent.getStringExtra(EXTRA_SCRIPT_NAME).orEmpty().ifBlank { "未命名脚本" }
        templateDirPath = intent.getStringExtra(EXTRA_TEMPLATE_DIR_PATH)
        scriptFilePath = intent.getStringExtra(EXTRA_SCRIPT_FILE_PATH)
        editable = intent.getBooleanExtra(EXTRA_EDITABLE, false)
        val scriptJson = intent.getStringExtra(EXTRA_SCRIPT_JSON)
        if (scriptJson.isNullOrBlank()) {
            Toast.makeText(this, "脚本内容为空", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        return try {
            persistedPlan = gson.fromJson(scriptJson, DailyTaskPlan::class.java)
            startTaskId = persistedPlan.start_task_id
            workingTasks.clear()
            workingTasks += persistedPlan.tasks
            true
        } catch (t: Throwable) {
            Toast.makeText(this, "脚本解析失败：${t.message}", Toast.LENGTH_SHORT).show()
            finish()
            false
        }
    }

    private fun buildContentView() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4EEE3"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable("#FFFCF7", "#D7B27B", 20)
            setPadding(dp(18), dp(18), dp(18), dp(18))

            titleView = TextView(this@RecordedDailyScriptViewerActivity).apply {
                setTextColor(Color.parseColor("#342113"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, Typeface.BOLD)
            }
            addView(titleView)

            summaryView = TextView(this@RecordedDailyScriptViewerActivity).apply {
                setTextColor(Color.parseColor("#6A4A2E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(10), 0, 0)
            }
            addView(summaryView)

            modeView = TextView(this@RecordedDailyScriptViewerActivity).apply {
                setTextColor(Color.parseColor("#8A6A45"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            }
            addView(modeView)

            addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(14), 0, 0)

                addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    addView(buildActionButton("重置分支", false).apply {
                        setOnClickListener {
                            branchSelections.clear()
                            activeSwitchTaskId = null
                            renderPlan()
                        }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addSpacer(this)
                    addView(buildActionButton("原始 JSON", false).apply {
                        setOnClickListener {
                            rawJsonVisible = !rawJsonVisible
                            rawJsonView.visibility = if (rawJsonVisible) View.VISIBLE else View.GONE
                        }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addSpacer(this)
                    exportButton = buildActionButton("导出", true).apply {
                        setOnClickListener { exportBundleToDownloads() }
                    }
                    addView(exportButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })

                if (editable) {
                    addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        setPadding(0, dp(10), 0, 0)
                        addView(buildActionButton("新增节点", false).apply {
                            setOnClickListener { showEditTaskDialog(null) }
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addSpacer(this)
                        addView(buildActionButton("修改起始", false).apply {
                            setOnClickListener { showEditStartTaskDialog() }
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addSpacer(this)
                        saveButton = buildActionButton("保存", true).apply {
                            setOnClickListener { saveChanges() }
                        }
                        addView(saveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    })
                } else {
                    saveButton = buildActionButton("保存", true).apply {
                        isEnabled = false
                        alpha = 0.5f
                    }
                }
            })
        })

        rawJsonView = TextView(this).apply {
            visibility = View.GONE
            background = roundedDrawable("#FFFDF8", "#E0CFAE", 12)
            setTextColor(Color.parseColor("#3F3022"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(
            rawJsonView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable("#FFF8EF", "#E1C79D", 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))

            branchTitleView = TextView(this@RecordedDailyScriptViewerActivity).apply {
                setTextColor(Color.parseColor("#3D2919"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                text = "分支切换"
            }
            addView(branchTitleView)

            branchHintView = TextView(this@RecordedDailyScriptViewerActivity).apply {
                setTextColor(Color.parseColor("#7B5B3D"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            }
            addView(branchHintView)

            val branchTabsScroll = HorizontalScrollView(this@RecordedDailyScriptViewerActivity).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(12), 0, 0)
                branchTabContainer = LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                addView(branchTabContainer)
            }
            addView(branchTabsScroll)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        pathContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scrollView = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                pathContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(12)
            }
        )

        setContentView(root)
    }

    private fun renderPlan() {
        val plan = currentPlan()
        val chain = buildRenderedChain()
        normalizeBranchState(chain.tasks)
        titleView.text = if (dirty) "$scriptName*" else scriptName
        summaryView.text = "起始任务：$startTaskId    任务总数：${workingTasks.size}"
        modeView.text = buildString {
            append(if (editable) "模式：可编辑录制脚本" else "模式：只读")
            scriptFilePath?.let { append("\n脚本文件：$it") }
            templateDirPath?.takeIf { it.isNotBlank() }?.let { append("\n模板目录：$it") }
        }
        rawJsonView.text = prettyGson.toJson(plan)
        saveButton.isEnabled = editable && dirty
        saveButton.alpha = if (editable && dirty) 1f else 0.5f
        exportButton.isEnabled = editableBundle() != null
        exportButton.alpha = if (exportButton.isEnabled) 1f else 0.5f
        renderBranchSwitcher(chain.tasks)
        renderRenderedChain(chain)
    }

    private fun normalizeBranchState(chainTasks: List<DailyTask>) {
        val existingTaskIds = workingTasks.mapTo(linkedSetOf()) { it.id }
        branchSelections.keys.retainAll(existingTaskIds)
        val chainTaskIds = chainTasks.mapTo(linkedSetOf()) { it.id }
        if (activeSwitchTaskId != null && activeSwitchTaskId !in chainTaskIds) {
            activeSwitchTaskId = null
        }
    }

    private fun renderBranchSwitcher(chainTasks: List<DailyTask>) {
        branchTabContainer.removeAllViews()
        val activeTask = activeSwitchTaskId?.let { taskId ->
            chainTasks.firstOrNull { it.id == taskId }
        }
        if (activeTask == null) {
            branchTitleView.text = "分支切换"
            branchHintView.text = if (chainTasks.isEmpty()) {
                "当前没有可展示的节点。"
            } else {
                "点下方任一节点的出口标签，这里会在同一页面继续切换该节点后续链路。"
            }
            return
        }

        val selectedKey = resolveSelectedExit(activeTask).key
        branchTitleView.text = "当前切换节点  #${activeTask.id} ${DailyPlanGraphBuilder.displayName(activeTask)}"
        branchHintView.text = "切换标签后，下方只会重绘这个节点之后的链路，前面的节点会保留。"
        buildExitOptions(activeTask).forEachIndexed { index, option ->
            if (index > 0) addSpacer(branchTabContainer, 8)
            branchTabContainer.addView(
                buildBranchTab(activeTask, option, option.key == selectedKey)
            )
        }
    }

    private fun renderRenderedChain(result: RenderedChain) {
        pathContainer.removeAllViews()
        if (result.tasks.isEmpty()) {
            pathContainer.addView(buildTerminalView(result.terminal ?: "暂无可展示节点"))
            return
        }
        result.tasks.forEachIndexed { index, task ->
            if (index > 0) {
                val previousTask = result.tasks[index - 1]
                val previousExit = result.selectedExits[previousTask.id]
                pathContainer.addView(buildFlowConnector(previousExit?.shortLabel ?: "成功"))
            }
            pathContainer.addView(
                buildTaskCard(
                    task = task,
                    selectedExit = result.selectedExits[task.id],
                    isActiveSwitchTask = task.id == activeSwitchTaskId
                )
            )
        }
        result.terminal?.let { terminal ->
            val lastTask = result.tasks.lastOrNull()
            val lastExit = lastTask?.let { result.selectedExits[it.id] }
            pathContainer.addView(buildFlowConnector(lastExit?.shortLabel ?: "结束"))
            pathContainer.addView(buildTerminalView(terminal))
        }
    }

    private fun buildRenderedChain(): RenderedChain {
        val tasksById = workingTasks.associateBy { it.id }
        val result = mutableListOf<DailyTask>()
        val selectedExits = linkedMapOf<Int, ExitOption>()
        val visited = linkedSetOf<Int>()
        var currentId = startTaskId
        while (true) {
            val task = tasksById[currentId]
                ?: return RenderedChain(result, "找不到节点 #$currentId", selectedExits)
            if (!visited.add(task.id)) {
                return RenderedChain(result, "检测到循环：#${task.id}", selectedExits)
            }
            result += task
            val selectedExit = resolveSelectedExit(task)
            selectedExits[task.id] = selectedExit
            val nextId = selectedExit.targetId
            if (nextId <= 0) {
                return RenderedChain(
                    result,
                    "${selectedExit.shortLabel}出口：${terminalLabel(nextId)}",
                    selectedExits
                )
            }
            if (!tasksById.containsKey(nextId)) {
                return RenderedChain(
                    result,
                    "${selectedExit.shortLabel}出口指向缺失节点 #$nextId",
                    selectedExits
                )
            }
            currentId = nextId
        }
    }

    private fun resolveSelectedExit(task: DailyTask): ExitOption {
        val options = buildExitOptions(task)
        val selectedKey = branchSelections[task.id]
        val selectedOption = options.firstOrNull { it.key == selectedKey && it.targetExists(workingTasks) }
        if (selectedOption != null) return selectedOption
        branchSelections.remove(task.id)
        return options.firstOrNull { it.key == DEFAULT_EXIT_KEY } ?: options.first()
    }

    private fun buildTaskCard(
        task: DailyTask,
        selectedExit: ExitOption?,
        isActiveSwitchTask: Boolean
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                fillColor = if (isActiveSwitchTask) "#FFF5DE" else "#FFFCF7",
                strokeColor = if (isActiveSwitchTask) "#D59A32" else "#D9C19B",
                radiusDp = 20
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showNodeDetail(task.id) }

            addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(buildNodeBadge("#${task.id}"))
                addSpacer(this, 10)
                addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                    text = DailyPlanGraphBuilder.displayName(task)
                    setTextColor(Color.parseColor("#362314"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(typeface, Typeface.BOLD)
                })
                if (isActiveSwitchTask) {
                    addSpacer(this, 10)
                    addView(buildStatusPill("当前切换", true))
                }
            })
            addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                text = DailyPlanGraphBuilder.summarizeTask(task)
                setTextColor(Color.parseColor("#5A4634"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })

            addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
                addView(buildMetaPill("delay ${task.delay}ms"))
                selectedExit?.let {
                    addSpacer(this, 8)
                    addView(buildMetaPill("当前走向 ${it.shortLabel} -> ${targetLabel(it.targetId)}"))
                }
            })

            val exits = buildExitOptions(task)
            if (exits.isNotEmpty()) {
                addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(12), 0, 0)
                    addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                        text = "出口切换"
                        setTextColor(Color.parseColor("#8A6230"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    exits.chunked(3).forEach { rowOptions ->
                        addView(LinearLayout(this@RecordedDailyScriptViewerActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, dp(8), 0, 0)
                            rowOptions.forEachIndexed { index, option ->
                                if (index > 0) addSpacer(this, 8)
                                addView(
                                    buildExitChip(
                                        task = task,
                                        option = option,
                                        selected = selectedExit?.key == option.key
                                    ),
                                    LinearLayout.LayoutParams(0, dp(38), 1f)
                                )
                            }
                            repeat(3 - rowOptions.size) {
                                if (rowOptions.isNotEmpty() || it > 0) addSpacer(this, 8)
                                addView(View(this@RecordedDailyScriptViewerActivity), LinearLayout.LayoutParams(0, 1, 1f))
                            }
                        })
                    }
                })
            }
        }
    }

    private fun buildExitChip(task: DailyTask, option: ExitOption, selected: Boolean): View {
        val targetExists = option.targetExists(workingTasks)
        return TextView(this).apply {
            text = option.label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                when {
                    !targetExists -> Color.parseColor("#B0A393")
                    selected -> Color.parseColor("#2E1D10")
                    else -> Color.parseColor("#5A3D1F")
                }
            )
            background = roundedDrawable(
                when {
                    !targetExists -> "#F3EEE6"
                    selected -> "#F3C86B"
                    else -> "#FFF6E5"
                },
                when {
                    !targetExists -> "#D8CDBB"
                    selected -> "#B97A16"
                    else -> "#E3C38E"
                },
                18
            )
            isEnabled = targetExists
            if (targetExists) {
                isClickable = true
                setOnClickListener {
                    activeSwitchTaskId = task.id
                    if (option.key == DEFAULT_EXIT_KEY) {
                        branchSelections.remove(task.id)
                    } else {
                        branchSelections[task.id] = option.key
                    }
                    renderPlan()
                }
            }
        }
    }

    private fun buildExitOptions(task: DailyTask): List<ExitOption> {
        val options = mutableListOf<ExitOption>()
        options += ExitOption(
            key = DEFAULT_EXIT_KEY,
            label = "成功 -> ${targetLabel(task.on_success)}",
            shortLabel = "成功",
            targetId = task.on_success
        )
        options += ExitOption(
            key = "fail",
            label = "失败 -> ${targetLabel(task.on_fail)}",
            shortLabel = "失败",
            targetId = task.on_fail
        )
        task.params?.branch_routes
            ?.toSortedMap()
            ?.forEach { (key, targetId) ->
                options += ExitOption(
                    key = "success_branch:$key",
                    label = "成功[$key] -> ${targetLabel(targetId)}",
                    shortLabel = "成功[$key]",
                    targetId = targetId
                )
            }
        task.params?.fail_branch_routes
            ?.toSortedMap()
            ?.forEach { (key, targetId) ->
                options += ExitOption(
                    key = "fail_branch:$key",
                    label = "失败[$key] -> ${targetLabel(targetId)}",
                    shortLabel = "失败[$key]",
                    targetId = targetId
                )
            }
        return options
    }

    private fun buildBranchTab(task: DailyTask, option: ExitOption, selected: Boolean): View {
        val targetExists = option.targetExists(workingTasks)
        return TextView(this).apply {
            text = option.label
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                when {
                    !targetExists -> Color.parseColor("#B0A393")
                    selected -> Color.parseColor("#2E1D10")
                    else -> Color.parseColor("#654628")
                }
            )
            background = roundedDrawable(
                when {
                    !targetExists -> "#F3EEE6"
                    selected -> "#F2CB75"
                    else -> "#FFFDF9"
                },
                when {
                    !targetExists -> "#DDD0BC"
                    selected -> "#B97A16"
                    else -> "#DDBB89"
                },
                16
            )
            alpha = if (targetExists) 1f else 0.65f
            isEnabled = targetExists
            if (targetExists) {
                setOnClickListener {
                    activeSwitchTaskId = task.id
                    if (option.key == DEFAULT_EXIT_KEY) {
                        branchSelections.remove(task.id)
                    } else {
                        branchSelections[task.id] = option.key
                    }
                    renderPlan()
                }
            }
        }
    }

    private fun targetLabel(targetId: Int): String {
        return if (targetId > 0) "#$targetId" else terminalLabel(targetId)
    }

    private fun terminalLabel(targetId: Int): String {
        return when (targetId) {
            -1 -> "END"
            -2 -> "FAIL"
            -3 -> "RESOURCE"
            -4 -> "COOLDOWN"
            0 -> "0"
            else -> targetId.toString()
        }
    }

    private fun buildTerminalView(text: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable("#FFF7EA", "#E0C28D", 16)
            setPadding(dp(14), dp(14), dp(14), dp(14))

            addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                this.text = "链路终点"
                setTextColor(Color.parseColor("#8A6230"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                this.text = text
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#59412A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun currentPlan(): DailyTaskPlan {
        return DailyTaskPlan(
            start_task_id = startTaskId,
            tasks = workingTasks.sortedBy { it.id }
        )
    }

    private fun showNodeDetail(taskId: Int) {
        val task = workingTasks.firstOrNull { it.id == taskId } ?: return
        if (editable && canEditTask(task)) {
            showEditTaskDialog(task)
            return
        }
        val incomingReferences = DailyPlanGraphBuilder
            .buildIncomingOnSuccessOrFailRefs(workingTasks, task.id)
        val message = buildString {
            appendLine(DailyPlanGraphBuilder.summarizeTask(task))
            appendLine()
            appendLine("id: ${task.id}")
            appendLine("action: ${task.action}")
            appendLine("delay: ${task.delay}")
            appendLine("on_success: ${task.on_success}")
            appendLine("on_fail: ${task.on_fail}")
            appendLine("start_cooldown_on_success: ${task.start_cooldown_on_success}")
            appendLine()
            appendLine("params:")
            DailyPlanGraphBuilder.buildParamsLines(task).forEach { appendLine(it) }
            appendLine()
            appendLine("incoming:")
            if (incomingReferences.isEmpty()) {
                appendLine("(无)")
            } else {
                incomingReferences.forEach { appendLine(it.label) }
            }
        }
        val content = TextView(this).apply {
            setTextColor(Color.parseColor("#3F3022"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            text = message
        }
        val scrollView = ScrollView(this).apply { addView(content) }
        DialogUtils.showStyledDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle("#${task.id} ${DailyPlanGraphBuilder.displayName(task)}")
                .setView(scrollView)
                .setPositiveButton("关闭", null),
        )
    }

    private fun confirmDeleteTask(task: DailyTask) {
        val refs = DailyPlanGraphBuilder.buildIncomingOnSuccessOrFailRefs(workingTasks, task.id)
        if (refs.isNotEmpty()) {
            DialogUtils.showStyledDialog(
                AlertDialog.Builder(DialogUtils.getThemeContext(this))
                    .setTitle("无法删除")
                    .setMessage(
                        buildString {
                            appendLine("该节点仍被 on_success / on_fail 引用，请先修改：")
                            appendLine()
                            refs.forEach { appendLine(it.label) }
                        }.trim()
                    )
                    .setPositiveButton("知道了", null),
            )
            return
        }
        DialogUtils.showStyledDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle("删除节点")
                .setMessage("确认删除 #${task.id} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    workingTasks.removeAll { it.id == task.id }
                    if (startTaskId == task.id) {
                        startTaskId = workingTasks.minOfOrNull { it.id } ?: 1
                    }
                    markDirty()
                    renderPlan()
                }
                .setNegativeButton("取消", null),
        )
    }

    private fun showEditStartTaskDialog() {
        val input = buildNumberEdit(startTaskId.toString())
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("修改起始任务")
            .setView(buildField("start_task_id", input))
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null || workingTasks.none { it.id == value }) {
                    Toast.makeText(this, "起始任务必须指向现有节点", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startTaskId = value
                markDirty()
                renderPlan()
                dialog.dismiss()
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun showEditTaskDialog(existingTask: DailyTask?) {
        val isNew = existingTask == null
        val sourceTask = existingTask ?: DailyTask(
            id = (workingTasks.maxOfOrNull { it.id } ?: 0) + 1,
            name = null,
            action = "CLICK",
            delay = 2500L,
            params = TaskParams(align = "center"),
            on_success = -1,
            on_fail = -1
        )

        val root = ScrollView(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        root.addView(form)

        val idEdit = buildNumberEdit(sourceTask.id.toString()).apply { isEnabled = isNew }
        val nameEdit = buildTextEdit(sourceTask.name.orEmpty())
        val delayEdit = buildNumberEdit(sourceTask.delay.toString())
        val successEdit = buildNumberEdit(sourceTask.on_success.toString())
        val failEdit = buildNumberEdit(sourceTask.on_fail.toString())
        val cooldownCheck = CheckBox(this).apply {
            text = "start_cooldown_on_success"
            setTextColor(Color.parseColor("#E9DDC1"))
            isChecked = sourceTask.start_cooldown_on_success
        }
        val actionSpinner = buildSpinner(supportedActions, sourceTask.action)

        form.addView(buildField("id", idEdit))
        form.addView(buildField("name", nameEdit))
        form.addView(buildField("action", actionSpinner))
        form.addView(buildField("delay", delayEdit))
        form.addView(buildField("on_success", successEdit))
        form.addView(buildField("on_fail", failEdit))
        form.addView(cooldownCheck)

        val clickSection = buildEditorSection().also { form.addView(it.container) }
        val clickXEdit = buildDecimalEdit(sourceTask.params?.x?.toString().orEmpty())
        val clickYEdit = buildDecimalEdit(sourceTask.params?.y?.toString().orEmpty())
        val clickAlignSpinner = buildSpinner(alignOptions, sourceTask.params?.align ?: "center")
        val clickRefEdit = buildNumberEdit(sourceTask.params?.ref_task_id?.toString().orEmpty())
        clickSection.container.addView(buildField("x", clickXEdit))
        clickSection.container.addView(buildField("y", clickYEdit))
        clickSection.container.addView(buildField("align", clickAlignSpinner))
        clickSection.container.addView(buildField("ref_task_id", clickRefEdit))

        val templateSection = buildEditorSection().also { form.addView(it.container) }
        val templateNameEdit = buildTextEdit(stripPngSuffix(sourceTask.params?.template_name))
        val templateThresholdEdit = buildDecimalEdit(
            if (sourceTask.action == "MATCH_TEMPLATE") sourceTask.params?.threshold?.toString().orEmpty() else "0.77"
        )
        val templateClickEdit = buildNumberEdit(sourceTask.params?.click?.toString() ?: "0")
        val templateRoiXEdit = buildDecimalEdit(sourceTask.params?.roi?.x?.toString().orEmpty())
        val templateRoiYEdit = buildDecimalEdit(sourceTask.params?.roi?.y?.toString().orEmpty())
        val templateRoiWEdit = buildDecimalEdit(sourceTask.params?.roi?.w?.toString().orEmpty())
        val templateRoiHEdit = buildDecimalEdit(sourceTask.params?.roi?.h?.toString().orEmpty())
        val templateAlignSpinner = buildSpinner(alignOptions, sourceTask.params?.roi?.align ?: "center")
        val templateTerminalNoteEdit = buildTextEdit(sourceTask.params?.terminal_note.orEmpty())
        templateSection.container.addView(buildField("template_name", buildSuffixInputRow(templateNameEdit, ".png")))
        templateSection.container.addView(buildField("threshold", templateThresholdEdit))
        templateSection.container.addView(buildField("是否点击（1为点击，0不）", templateClickEdit))
        templateSection.container.addView(buildField("roi.x", templateRoiXEdit))
        templateSection.container.addView(buildField("roi.y", templateRoiYEdit))
        templateSection.container.addView(buildField("roi.w", templateRoiWEdit))
        templateSection.container.addView(buildField("roi.h", templateRoiHEdit))
        templateSection.container.addView(buildField("roi.align", templateAlignSpinner))
        templateSection.container.addView(buildField("terminal_note", templateTerminalNoteEdit))

        val ocrSection = buildEditorSection().also { form.addView(it.container) }
        val ocrCharsEdit = buildTextEdit(sourceTask.params?.target_chars?.joinToString("").orEmpty())
        val ocrMinHitEdit = buildNumberEdit(sourceTask.params?.min_hit_count?.toString() ?: "1")
        val ocrClickEdit = buildNumberEdit(sourceTask.params?.click?.toString() ?: "0")
        val ocrRoiXEdit = buildDecimalEdit(sourceTask.params?.roi?.x?.toString().orEmpty())
        val ocrRoiYEdit = buildDecimalEdit(sourceTask.params?.roi?.y?.toString().orEmpty())
        val ocrRoiWEdit = buildDecimalEdit(sourceTask.params?.roi?.w?.toString().orEmpty())
        val ocrRoiHEdit = buildDecimalEdit(sourceTask.params?.roi?.h?.toString().orEmpty())
        val ocrAlignSpinner = buildSpinner(alignOptions, sourceTask.params?.roi?.align ?: "center")
        val ocrTerminalNoteEdit = buildTextEdit(sourceTask.params?.terminal_note.orEmpty())
        ocrSection.container.addView(buildField("target_chars", ocrCharsEdit))
        ocrSection.container.addView(buildField("min_hit_count", ocrMinHitEdit))
        ocrSection.container.addView(buildField("是否点击（1为点击，0不）", ocrClickEdit))
        ocrSection.container.addView(buildField("roi.x", ocrRoiXEdit))
        ocrSection.container.addView(buildField("roi.y", ocrRoiYEdit))
        ocrSection.container.addView(buildField("roi.w", ocrRoiWEdit))
        ocrSection.container.addView(buildField("roi.h", ocrRoiHEdit))
        ocrSection.container.addView(buildField("roi.align", ocrAlignSpinner))
        ocrSection.container.addView(buildField("terminal_note", ocrTerminalNoteEdit))

        val setVarSection = buildEditorSection().also { form.addView(it.container) }
        val setVarNameEdit = buildTextEdit(sourceTask.params?.var_name.orEmpty())
        val setVarValueEdit = buildTextEdit(sourceTask.params?.var_value.orEmpty())
        setVarSection.container.addView(buildField("var_name", setVarNameEdit))
        setVarSection.container.addView(buildField("var_value", setVarValueEdit))

        val successBranchEnabled = CheckBox(this).apply {
            text = "启用成功分支"
            setTextColor(Color.parseColor("#E9DDC1"))
            isChecked = !sourceTask.params?.branch_var.isNullOrBlank() || !sourceTask.params?.branch_routes.isNullOrEmpty()
        }
        val successBranchVarEdit = buildTextEdit(sourceTask.params?.branch_var.orEmpty())
        val successBranchRoutesEditor = buildBranchRoutesEditor(sourceTask.params?.branch_routes)
        val successBranchSection = buildEditorSection().also { form.addView(it.container) }
        successBranchSection.container.addView(successBranchEnabled)
        successBranchSection.container.addView(buildField("success.branch_var", successBranchVarEdit))
        successBranchSection.container.addView(buildField("success.branch_routes", successBranchRoutesEditor.container))

        val failBranchEnabled = CheckBox(this).apply {
            text = "启用失败分支"
            setTextColor(Color.parseColor("#E9DDC1"))
            isChecked = !sourceTask.params?.fail_branch_var.isNullOrBlank() || !sourceTask.params?.fail_branch_routes.isNullOrEmpty()
        }
        val failBranchVarEdit = buildTextEdit(sourceTask.params?.fail_branch_var.orEmpty())
        val failBranchRoutesEditor = buildBranchRoutesEditor(sourceTask.params?.fail_branch_routes)
        val failBranchSection = buildEditorSection().also { form.addView(it.container) }
        failBranchSection.container.addView(failBranchEnabled)
        failBranchSection.container.addView(buildField("fail.branch_var", failBranchVarEdit))
        failBranchSection.container.addView(buildField("fail.branch_routes", failBranchRoutesEditor.container))

        fun updateSections() {
            val action = actionSpinner.selectedItem?.toString().orEmpty()
            clickSection.container.visibility = if (action == "CLICK") View.VISIBLE else View.GONE
            templateSection.container.visibility = if (action == "MATCH_TEMPLATE") View.VISIBLE else View.GONE
            ocrSection.container.visibility = if (action == "OCR") View.VISIBLE else View.GONE
            setVarSection.container.visibility = if (action == "SET_VAR") View.VISIBLE else View.GONE
        }
        actionSpinner.onItemSelectedListener = SimpleItemSelectedListener(::updateSections)
        updateSections()

        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle(if (isNew) "新增节点" else "编辑节点 #${sourceTask.id}")
            .setView(root)
            .setPositiveButton("保存", null)
            .setNeutralButton(if (isNew) null else "删除", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val task = runCatching {
                    buildTaskFromEditor(
                        isNew = isNew,
                        sourceTask = sourceTask,
                        idEdit = idEdit,
                        nameEdit = nameEdit,
                        actionSpinner = actionSpinner,
                        delayEdit = delayEdit,
                        successEdit = successEdit,
                        failEdit = failEdit,
                        cooldownCheck = cooldownCheck,
                        clickXEdit = clickXEdit,
                        clickYEdit = clickYEdit,
                        clickAlignSpinner = clickAlignSpinner,
                        clickRefEdit = clickRefEdit,
                        templateNameEdit = templateNameEdit,
                        templateThresholdEdit = templateThresholdEdit,
                        templateClickEdit = templateClickEdit,
                        templateRoiXEdit = templateRoiXEdit,
                        templateRoiYEdit = templateRoiYEdit,
                        templateRoiWEdit = templateRoiWEdit,
                        templateRoiHEdit = templateRoiHEdit,
                        templateAlignSpinner = templateAlignSpinner,
                        templateTerminalNoteEdit = templateTerminalNoteEdit,
                        ocrCharsEdit = ocrCharsEdit,
                        ocrMinHitEdit = ocrMinHitEdit,
                        ocrClickEdit = ocrClickEdit,
                        ocrRoiXEdit = ocrRoiXEdit,
                        ocrRoiYEdit = ocrRoiYEdit,
                        ocrRoiWEdit = ocrRoiWEdit,
                        ocrRoiHEdit = ocrRoiHEdit,
                        ocrAlignSpinner = ocrAlignSpinner,
                        ocrTerminalNoteEdit = ocrTerminalNoteEdit,
                        setVarNameEdit = setVarNameEdit,
                        setVarValueEdit = setVarValueEdit,
                        successBranchEnabled = successBranchEnabled,
                        successBranchVarEdit = successBranchVarEdit,
                        successBranchRoutesEditor = successBranchRoutesEditor,
                        failBranchEnabled = failBranchEnabled,
                        failBranchVarEdit = failBranchVarEdit,
                        failBranchRoutesEditor = failBranchRoutesEditor
                    )
                }.getOrElse { error ->
                    Toast.makeText(this, error.message ?: "表单校验失败", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                applyTask(task, isNew)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                dialog.dismiss()
                confirmDeleteTask(sourceTask)
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun buildTaskFromEditor(
        isNew: Boolean,
        sourceTask: DailyTask,
        idEdit: EditText,
        nameEdit: EditText,
        actionSpinner: Spinner,
        delayEdit: EditText,
        successEdit: EditText,
        failEdit: EditText,
        cooldownCheck: CheckBox,
        clickXEdit: EditText,
        clickYEdit: EditText,
        clickAlignSpinner: Spinner,
        clickRefEdit: EditText,
        templateNameEdit: EditText,
        templateThresholdEdit: EditText,
        templateClickEdit: EditText,
        templateRoiXEdit: EditText,
        templateRoiYEdit: EditText,
        templateRoiWEdit: EditText,
        templateRoiHEdit: EditText,
        templateAlignSpinner: Spinner,
        templateTerminalNoteEdit: EditText,
        ocrCharsEdit: EditText,
        ocrMinHitEdit: EditText,
        ocrClickEdit: EditText,
        ocrRoiXEdit: EditText,
        ocrRoiYEdit: EditText,
        ocrRoiWEdit: EditText,
        ocrRoiHEdit: EditText,
        ocrAlignSpinner: Spinner,
        ocrTerminalNoteEdit: EditText,
        setVarNameEdit: EditText,
        setVarValueEdit: EditText,
        successBranchEnabled: CheckBox,
        successBranchVarEdit: EditText,
        successBranchRoutesEditor: BranchRoutesEditor,
        failBranchEnabled: CheckBox,
        failBranchVarEdit: EditText,
        failBranchRoutesEditor: BranchRoutesEditor
    ): DailyTask {
        val taskId = if (isNew) {
            idEdit.text.toString().trim().toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("请填写合法 id")
        } else {
            sourceTask.id
        }
        if (isNew && workingTasks.any { it.id == taskId }) {
            throw IllegalArgumentException("节点 id 已存在：$taskId")
        }
        val delay = delayEdit.text.toString().trim().toLongOrNull()
            ?: throw IllegalArgumentException("请填写合法 delay")
        val onSuccess = successEdit.text.toString().trim().toIntOrNull()
            ?: throw IllegalArgumentException("请填写合法 on_success")
        val onFail = failEdit.text.toString().trim().toIntOrNull()
            ?: throw IllegalArgumentException("请填写合法 on_fail")
        val action = actionSpinner.selectedItem?.toString().orEmpty()
        val successRoutes = collectBranchRoutes(successBranchRoutesEditor)
        val failRoutes = collectBranchRoutes(failBranchRoutesEditor)
        val successVar = successBranchVarEdit.text.toString().trim()
        val failVar = failBranchVarEdit.text.toString().trim()
        if (successBranchEnabled.isChecked && successVar.isBlank()) {
            throw IllegalArgumentException("请填写成功分支变量名")
        }
        if (failBranchEnabled.isChecked && failVar.isBlank()) {
            throw IllegalArgumentException("请填写失败分支变量名")
        }
        fun attachBranchConfig(base: TaskParams?): TaskParams {
            val seed = base ?: TaskParams()
            return seed.copy(
                branch_var = successVar.takeIf { successBranchEnabled.isChecked && it.isNotBlank() },
                branch_routes = successRoutes.takeIf { successBranchEnabled.isChecked && it.isNotEmpty() },
                fail_branch_var = failVar.takeIf { failBranchEnabled.isChecked && it.isNotBlank() },
                fail_branch_routes = failRoutes.takeIf { failBranchEnabled.isChecked && it.isNotEmpty() }
            )
        }
        val params = when (action) {
            "CLICK" -> {
                val refTaskId = clickRefEdit.text.toString().trim().toIntOrNull()
                if (refTaskId != null) {
                    attachBranchConfig(TaskParams(ref_task_id = refTaskId))
                } else {
                    val x = clickXEdit.text.toString().trim().toFloatOrNull()
                        ?: throw IllegalArgumentException("CLICK 需要合法 x")
                    val y = clickYEdit.text.toString().trim().toFloatOrNull()
                        ?: throw IllegalArgumentException("CLICK 需要合法 y")
                    val align = clickAlignSpinner.selectedItem?.toString().orEmpty().ifBlank { "center" }
                    attachBranchConfig(TaskParams(x = x, y = y, align = align))
                }
            }
            "MATCH_TEMPLATE" -> {
                val templateName = normalizeTemplateName(templateNameEdit.text.toString())
                if (templateName.isBlank()) throw IllegalArgumentException("请填写模板文件名")
                val threshold = templateThresholdEdit.text.toString().trim().toFloatOrNull()
                    ?: throw IllegalArgumentException("请填写合法阈值")
                val click = templateClickEdit.text.toString().trim().toIntOrNull()
                    ?: throw IllegalArgumentException("请填写 click 0/1")
                if (click !in 0..1) throw IllegalArgumentException("click 只能填 0 或 1")
                attachBranchConfig(TaskParams(
                    template_name = templateName,
                    threshold = threshold,
                    click = click,
                    roi = buildRoi(
                        templateRoiXEdit,
                        templateRoiYEdit,
                        templateRoiWEdit,
                        templateRoiHEdit,
                        templateAlignSpinner
                    ),
                    terminal_note = templateTerminalNoteEdit.text.toString().trim().takeIf { it.isNotBlank() }
                ))
            }
            "OCR" -> {
                val chars = ocrCharsEdit.text.toString().filterNot { it.isWhitespace() }
                if (chars.isBlank()) throw IllegalArgumentException("请填写 OCR 文字")
                val minHit = ocrMinHitEdit.text.toString().trim().toIntOrNull()
                    ?: throw IllegalArgumentException("请填写合法 min_hit_count")
                val click = ocrClickEdit.text.toString().trim().toIntOrNull()
                    ?: throw IllegalArgumentException("请填写 click 0/1")
                if (click !in 0..1) throw IllegalArgumentException("click 只能填 0 或 1")
                attachBranchConfig(TaskParams(
                    target_chars = chars.map { it.toString() },
                    min_hit_count = minHit,
                    click = click,
                    roi = buildRoi(
                        ocrRoiXEdit,
                        ocrRoiYEdit,
                        ocrRoiWEdit,
                        ocrRoiHEdit,
                        ocrAlignSpinner
                    ),
                    terminal_note = ocrTerminalNoteEdit.text.toString().trim().takeIf { it.isNotBlank() }
                ))
            }
            "SET_VAR" -> {
                val varName = setVarNameEdit.text.toString().trim()
                val varValue = setVarValueEdit.text.toString().trim()
                if (varName.isBlank()) throw IllegalArgumentException("请填写 var_name")
                if (varValue.isBlank()) throw IllegalArgumentException("请填写 var_value")
                attachBranchConfig(TaskParams(
                    var_name = varName,
                    var_value = varValue
                ))
            }
            "BACK" -> attachBranchConfig(null).takeIf {
                successBranchEnabled.isChecked || failBranchEnabled.isChecked
            }
            else -> throw IllegalArgumentException("暂不支持编辑动作：$action")
        }

        return DailyTask(
            id = taskId,
            name = nameEdit.text.toString().trim().takeIf { it.isNotBlank() },
            action = action,
            delay = delay,
            params = params,
            on_success = onSuccess,
            on_fail = onFail,
            start_cooldown_on_success = cooldownCheck.isChecked
        )
    }

    private fun applyTask(task: DailyTask, isNew: Boolean) {
        if (isNew) {
            workingTasks += task
            if (workingTasks.size == 1) {
                startTaskId = task.id
            }
        } else {
            val index = workingTasks.indexOfFirst { it.id == task.id }
            if (index >= 0) {
                workingTasks[index] = task
            }
        }
        if (workingTasks.none { it.id == startTaskId }) {
            startTaskId = task.id
        }
        workingTasks.sortBy { it.id }
        markDirty()
        renderPlan()
    }

    private fun saveChanges() {
        val bundle = editableBundle()
        if (!editable || bundle == null) {
            Toast.makeText(this, "当前脚本不可保存", Toast.LENGTH_SHORT).show()
            return
        }
        val plan = currentPlan()
        runCatching {
            UserDailyScriptStore.syncTemplateFilesForPlanUpdate(bundle, persistedPlan, plan)
            UserDailyScriptStore.savePlan(bundle, plan, gson)
        }.onSuccess {
            persistedPlan = plan
            dirty = false
            renderPlan()
            Toast.makeText(this, "脚本已保存", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(this, "保存失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportBundleToDownloads() {
        val bundle = editableBundle()
        if (bundle == null) {
            Toast.makeText(this, "当前脚本不可导出", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            if (dirty) {
                val plan = currentPlan()
                UserDailyScriptStore.syncTemplateFilesForPlanUpdate(bundle, persistedPlan, plan)
                UserDailyScriptStore.savePlan(bundle, plan, gson)
                persistedPlan = plan
                dirty = false
                renderPlan()
            }
            UserDailyScriptStore.exportBundleToPublicDownloads(this, bundle)
        }.onSuccess { exportPath ->
            Toast.makeText(this, "已导出到：$exportPath", Toast.LENGTH_LONG).show()
        }.onFailure { error ->
            Toast.makeText(this, "导出失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun editableBundle(): UserDailyScriptBundle? {
        val scriptPath = scriptFilePath?.takeIf { it.isNotBlank() } ?: return null
        val templatePath = templateDirPath?.takeIf { it.isNotBlank() } ?: return null
        val scriptFile = File(scriptPath)
        val rootDir = scriptFile.parentFile ?: return null
        return UserDailyScriptBundle(
            scriptId = scriptName,
            rootDir = rootDir,
            scriptFile = scriptFile,
            templatesDir = File(templatePath)
        )
    }

    private fun canEditTask(task: DailyTask): Boolean = task.action in supportedActions

    private fun markDirty() {
        dirty = true
    }

    private fun buildField(label: String, child: View): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                text = label
                setTextColor(Color.parseColor("#E5C07B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), 0, dp(2), dp(4))
            })
            addView(child)
        }
    }

    private fun buildEditorSection(): EditorSection {
        return EditorSection(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        )
    }

    private fun buildFlowConnector(label: String): View {
        return TextView(this).apply {
            this.text = "⬇"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFFFFF"))
            setShadowLayer(8f, 0f, dp(2).toFloat(), Color.parseColor("#7A5730"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun buildNodeBadge(text: String): View {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            minWidth = dp(42)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(Color.parseColor("#6E4716"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable("#F6E4BC", "#D8B06A", 14)
        }
    }

    private fun buildStatusPill(text: String, accent: Boolean): View {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(if (accent) Color.parseColor("#5A3406") else Color.parseColor("#6F5738"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable(
                if (accent) "#F5D590" else "#F6EEE0",
                if (accent) "#C48A28" else "#D9C3A3",
                12
            )
        }
    }

    private fun buildMetaPill(text: String): View {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#735637"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedDrawable("#F8F1E4", "#E5D3B7", 12)
        }
    }

    private fun buildSpinner(options: List<String>, selected: String): Spinner {
        return Spinner(this).apply {
            adapter = DialogUtils.fixedDropdownTextAdapter(this@RecordedDailyScriptViewerActivity, options)
            setSelection(options.indexOf(selected).takeIf { it >= 0 } ?: 0)
        }
    }

    private fun buildActionButton(text: String, accent: Boolean): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minimumWidth = 0
            minWidth = dp(72)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (accent) Color.parseColor("#2E1D10") else Color.parseColor("#6B4829"))
            background = roundedDrawable(
                if (accent) "#E8C36D" else "#FFFDF8",
                if (accent) "#B48735" else "#D8B982",
                12
            )
        }
    }

    private fun buildTextEdit(value: String): EditText {
        return EditText(this).apply {
            setText(value)
            setSelection(text.length)
            applyYuanInputStyle()
        }
    }

    private fun buildSuffixInputRow(editText: EditText, suffix: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                editText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                text = suffix
                setTextColor(Color.parseColor("#7A7A7A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(8), 0, 0, 0)
            })
        }
    }

    private fun buildNumberEdit(value: String): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value)
            setSelection(text.length)
            applyYuanInputStyle()
        }
    }

    private fun buildDecimalEdit(value: String): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value)
            setSelection(text.length)
            applyYuanInputStyle()
        }
    }

    private fun buildRoi(
        xEdit: EditText,
        yEdit: EditText,
        wEdit: EditText,
        hEdit: EditText,
        alignSpinner: Spinner
    ): ROI {
        val x = xEdit.text.toString().trim().toFloatOrNull()
            ?: throw IllegalArgumentException("请填写合法 roi.x")
        val y = yEdit.text.toString().trim().toFloatOrNull()
            ?: throw IllegalArgumentException("请填写合法 roi.y")
        val w = wEdit.text.toString().trim().toFloatOrNull()
            ?: throw IllegalArgumentException("请填写合法 roi.w")
        val h = hEdit.text.toString().trim().toFloatOrNull()
            ?: throw IllegalArgumentException("请填写合法 roi.h")
        return ROI(
            x = x,
            y = y,
            w = w,
            h = h,
            align = alignSpinner.selectedItem?.toString().orEmpty().ifBlank { "center" }
        )
    }

    private fun stripPngSuffix(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (trimmed.lowercase(Locale.ROOT).endsWith(".png")) trimmed.dropLast(4) else trimmed
    }

    private fun normalizeTemplateName(value: String): String {
        val baseName = stripPngSuffix(value)
        return if (baseName.isBlank()) "" else "$baseName.png"
    }

    private fun buildBranchRoutesEditor(routes: Map<String, Int>?): BranchRoutesEditor {
        val rows = mutableListOf<BranchRouteRow>()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun addRow(initialValue: String = "", initialTarget: String = "") {
            lateinit var row: BranchRouteRow
            val valueEdit = buildTextEdit(initialValue).apply {
                hint = "分支值"
            }
            val targetEdit = buildNumberEdit(initialTarget).apply {
                hint = "后继节点"
            }
            val rowContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
                addView(valueEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@RecordedDailyScriptViewerActivity).apply {
                    text = "→"
                    setTextColor(Color.parseColor("#E9DDC1"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dp(8), 0, dp(8), 0)
                })
                addView(targetEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(buildActionButton("删", false).apply {
                    minWidth = dp(40)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    setOnClickListener {
                        container.removeView(row.container)
                        rows.remove(row)
                    }
                })
            }
            row = BranchRouteRow(rowContainer, valueEdit, targetEdit)
            rows += row
            container.addView(rowContainer, container.childCount.coerceAtLeast(0))
        }
        routes?.toSortedMap()?.forEach { (value, targetId) ->
            addRow(value, targetId.toString())
        }
        if (rows.isEmpty()) {
            addRow()
        }
        container.addView(buildActionButton("新增", false).apply {
            setOnClickListener { addRow() }
        })
        return BranchRoutesEditor(container, rows)
    }

    private fun collectBranchRoutes(editor: BranchRoutesEditor): Map<String, Int> {
        return editor.rows.mapNotNull { row ->
            val value = row.valueEdit.text.toString().trim()
            val targetId = row.targetEdit.text.toString().trim().toIntOrNull()
            if (value.isBlank() || targetId == null) {
                null
            } else {
                value to targetId
            }
        }.toMap(linkedMapOf())
    }

    private fun addSpacer(parent: LinearLayout, widthDp: Int = 10) {
        parent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
        })
    }

    private fun roundedDrawable(fillColor: String, strokeColor: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private data class EditorSection(
        val container: LinearLayout
    )

    private data class BranchRouteRow(
        val container: LinearLayout,
        val valueEdit: EditText,
        val targetEdit: EditText
    )

    private data class BranchRoutesEditor(
        val container: LinearLayout,
        val rows: MutableList<BranchRouteRow>
    )

    private data class RenderedChain(
        val tasks: List<DailyTask>,
        val terminal: String?,
        val selectedExits: Map<Int, ExitOption>
    )

    private data class ExitOption(
        val key: String,
        val label: String,
        val shortLabel: String,
        val targetId: Int
    ) {
        fun targetExists(tasks: List<DailyTask>): Boolean {
            return targetId <= 0 || tasks.any { it.id == targetId }
        }
    }

    private class SimpleItemSelectedListener(
        private val onSelected: () -> Unit
    ) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            onSelected()
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
