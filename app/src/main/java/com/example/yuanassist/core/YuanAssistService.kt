/**
 * 模块：Core
 * 描述：无障碍服务的核心中枢类
 * 功能：接管系统无障碍权限，统筹生命周期，管理悬浮窗的显示与隐藏，协调录制引擎、跟打引擎、日常引擎的运行，以及处理OCR意图。
 */
package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.*
import com.example.yuanassist.network.OcrManager
import com.example.yuanassist.ui.*
import com.example.yuanassist.ui.dialogs.*
import com.example.yuanassist.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import java.io.File

private var isAutoSelectAgentEnabled = false
private var selectedAgents = Array(5) { "" }
private var autoTaskEngine: AutoTaskEngine? = null
private lateinit var gestureDispatcher: GestureDispatcher

data class ActionItem(val stepIndex: Int, val colIndex: Int, val command: String)
data class TargetSwitchTask(val turnNumber: Int, val afterStep: Int, val clickCount: Int)
data class ScriptConfigJson(val intervalAttack: Long, val intervalSkill: Long, val waitTurn: Long)
data class LocalScriptJson(
    val title: String? = null,
    val originalPostUrl: String? = null,
    val scriptContent: String,
    val config: ScriptConfigJson,
    val instructions: List<InstructionJson>? = null,
    val targetSwitches: List<TargetSwitchTask>? = null,
    val items: List<Any>? = null
)

class YuanAssistService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appConfig: AppConfig = AppConfig(2500, 4000, 8000, 1, 50, 31, 60, 3)
    private var lastRecordedInfo = "准备录制"
    private var currentFollowInfoText = "准备执行"

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: YuanAssistService? = null

        fun onImagePicked(bitmap: Bitmap) {
            instance?.processOcrImage(bitmap)
        }

        private val REGEX_INVALID = Regex("[^0-9A↑↓圈]")
        private val REGEX_IL = Regex("[Il|](?=\\d)")
        private val REGEX_ARROW_UP = Regex("[→←TI|l+t/]")
        private val REGEX_ARROW_DOWN = Regex("[J!?]")
        private val REGEX_END_4 = Regex("(?<=\\d)4$")
        private val REGEX_MID_1 = Regex("(?<=\\d)1(?=\\d)")
        private val REGEX_END_1 = Regex("(?<=[02-9])1$")
        private val REGEX_DOWN_7 = Regex("(?<=\\d)7(?![A\\d])")
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var coordinateManager: CoordinateManager

    private var isFollowMode = false
    private var isRunning = false
    private var isSimulating = false

    private var dailyWindowManager: DailyWindowManager? = null
    private lateinit var recordEngine: RecordEngine
    private lateinit var combatEngine: CombatEngine

    private val currentDisplayData: ArrayList<TurnData>
        get() = if (isFollowMode) combatEngine.followData else recordEngine.recordData
    private lateinit var tableAdapter: LogAdapter
    private lateinit var uiManager: com.example.yuanassist.ui.FloatingUIManager

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    @Volatile
    private var isOcrProcessing = false
    // --- 生命周期与服务初始化 ---
    override fun onCreate() {
        super.onCreate()
        uiManager = com.example.yuanassist.ui.FloatingUIManager(this)
        gestureDispatcher = GestureDispatcher(this, uiManager, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            "ACTION_START_DAILY_WINDOW" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                dailyWindowManager?.showWindow()
            }
            "ACTION_START_COMBAT_WINDOW" -> {
                dailyWindowManager?.hideWindow()
                showControlWindow()
                if (::tableAdapter.isInitialized) {
                    updateTableData()
                }
            }
            "ACTION_IMPORT_SCRIPT" -> {
                val scriptContent = intent.getStringExtra("SCRIPT_CONTENT")
                if (!scriptContent.isNullOrEmpty()) {
                    parseTextToTable(scriptContent)
                    if (!isFollowMode) {
                        Toast.makeText(this, "脚本已导入，请手动切换到跟打模式查看", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "脚本导入成功！", Toast.LENGTH_SHORT).show()
                    }
                }

                val configJson = intent.getStringExtra("CONFIG_JSON")
                if (configJson != null) {
                    try {
                        val cfg = Gson().fromJson(configJson, StrategyConfig::class.java)
                        val currentConfig = ConfigManager.getAllConfig(this)
                        val newConfig = currentConfig.copy(
                            intervalAttack = cfg.intervalAttack,
                            intervalSkill = cfg.intervalSkill,
                            waitTurn = cfg.waitTurn
                        )
                        ConfigManager.saveSettings(this, newConfig)
                        reloadGlobalConfig()
                        Toast.makeText(this, "已应用攻略推荐设置", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val instructionsJson = intent.getStringExtra("INSTRUCTIONS_JSON")
                if (instructionsJson != null) {
                    try {
                        val type = object : TypeToken<List<InstructionJson>>() {}.type
                        val list = Gson().fromJson<List<InstructionJson>>(instructionsJson, type)

                        var importCount = 0
                        list.forEach { ins ->
                            val exists = combatEngine.instructionList.any {
                                it.turn == ins.turn && it.step == ins.step && it.type.name == ins.type
                            }
                            if (!exists) {
                                combatEngine.instructionList.add(
                                    ScriptInstruction(ins.turn, ins.step, InstructionType.valueOf(ins.type), ins.value)
                                )
                                importCount++
                            }
                        }
                        if (importCount > 0) {
                            Toast.makeText(this, "成功导入 $importCount 条附带指令", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val agentsJson = intent.getStringExtra("AGENTS_JSON")
                if (agentsJson != null) {
                    try {
                        val type = object : TypeToken<List<String>>() {}.type
                        val list = Gson().fromJson<List<String>>(agentsJson, type)

                        var validCount = 0
                        for (i in 0 until minOf(5, list.size)) {
                            selectedAgents[i] = list[i].trim()
                            if (selectedAgents[i].isNotEmpty()) validCount++
                        }
                        if (validCount > 0) {
                            Toast.makeText(this, "已将 $validCount 名密探填入自动选人名单（未开启）", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        gestureDispatcher = GestureDispatcher(this, uiManager, handler)
        super.onServiceConnected()
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV 初始化失败！", Toast.LENGTH_LONG).show()
        } else {
            Log.d("GameAssist", "OpenCV 初始化成功！")
        }
        instance = this
        coordinateManager = CoordinateManager(this)

        recordEngine = RecordEngine(
            coordinateManager = coordinateManager,
            gestureDispatcher = gestureDispatcher,
            getConfig = { appConfig },
            onDataUpdated = { turnIndex ->
                handler.post {
                    if (turnIndex == -1) updateTableData()
                    else tableAdapter.notifyItemChanged(turnIndex)
                }
            },
            onTurnInserted = { turnIndex ->
                handler.post {
                    tableAdapter.notifyItemInserted(turnIndex)
                    uiManager.controlView?.findViewById<RecyclerView>(R.id.rv_log_table)?.scrollToPosition(turnIndex)
                }
            },
            onTurnRemoved = { turnIndex ->
                handler.post { tableAdapter.notifyItemRemoved(turnIndex) }
            },
            onActionRecorded = { actionText ->
                handler.post {
                    lastRecordedInfo = "记录: $actionText"
                    updateMiniWindowUI()
                }
            }
        )
        combatEngine = CombatEngine(
            serviceScope = serviceScope,
            coordinateManager = coordinateManager,
            gestureDispatcher = gestureDispatcher,
            getConfig = { appConfig },
            onStateChanged = {
                handler.post {
                    if (!combatEngine.isRunning) isRunning = false
                    refreshActionButtonUI()
                    updateMiniWindowUI()
                }
            },
            onRowUpdated = { rowIndex ->
                handler.post {
                    if (rowIndex == -1) updateTableData()
                    else tableAdapter.notifyItemChanged(rowIndex)
                }
            },
            onScrollToRow = { rowIndex ->
                handler.post {
                    uiManager.controlView?.findViewById<RecyclerView>(R.id.rv_log_table)?.smoothScrollToPosition(rowIndex)
                }
            },
            onHudUpdated = { text, isWarning ->
                handler.post {
                    val btnHud = uiManager.controlView?.findViewById<Button>(R.id.btn_next_turn)
                    btnHud?.text = text
                    btnHud?.setTextColor(if (isWarning) Color.parseColor("#C0392B") else Color.parseColor("#4A6F8A"))
                    currentFollowInfoText = text
                    if (uiManager.minimizedView != null) updateMiniWindowUI()
                }
            },
            showToast = { msg, isLong ->
                handler.post {
                    Toast.makeText(this@YuanAssistService, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                }
            }
        )
        reloadGlobalConfig()
        if (recordEngine.recordData.isEmpty()) {
            recordEngine.recordData.add(TurnData(1, currentStep = 1))
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val pendingAction = prefs.getString("pending_start_action", "ACTION_START_COMBAT_WINDOW")
        prefs.edit().remove("pending_start_action").apply()

        if (pendingAction == "ACTION_START_DAILY_WINDOW") {
            if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
            dailyWindowManager?.showWindow()
        } else {
            showControlWindow()
            updateTableData()
        }
        autoTaskEngine = AutoTaskEngine(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private var recordSwipeThreshold: Int = 50
    private var recordInputHeightRatio: Int = 31
    private fun reloadGlobalConfig() {
        appConfig = ConfigManager.getAllConfig(this)
        recordSwipeThreshold = appConfig.swipeThreshold
        if (recordInputHeightRatio != appConfig.inputHeightRatio) {
            recordInputHeightRatio = appConfig.inputHeightRatio
        }
    }
    // 唤起系统相册选择图片
    private fun startImagePicker() {
        val intent = Intent(this, ImagePickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    // 接收图片并上传至云端进行OCR识别
    fun processOcrImage(bitmap: Bitmap) {
        if (isOcrProcessing) {
            Toast.makeText(this, "正在处理中...", Toast.LENGTH_SHORT).show()
            return
        }

        isOcrProcessing = true
        Toast.makeText(this, "正在上传至云端...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            val result = OcrManager.recognizeImage(bitmap, deviceId) {
                Toast.makeText(this@YuanAssistService, "当前路线拥挤，正在自动重试", Toast.LENGTH_SHORT).show()
            }

            when (result) {
                is OcrManager.OcrResult.Success -> {
                    parseTextToTable(result.parsedText)
                    Toast.makeText(this@YuanAssistService, "识别成功 (策略: ${result.strategyUsed})", Toast.LENGTH_SHORT).show()
                }
                is OcrManager.OcrResult.Error -> {
                    Toast.makeText(this@YuanAssistService, result.message, Toast.LENGTH_LONG).show()
                }
            }
            isOcrProcessing = false
        }
    }
    // 正则化处理OCR返回的字符串指令
    private fun normalizeCommand(text: String): CharSequence {
        val ssb = SpannableStringBuilder(text.uppercase().replace("\\s+".toRegex(), ""))
        fun applyRule(regex: Regex, replacement: String) {
            var match = regex.find(ssb)
            while (match != null) {
                val start = match.range.first
                val end = match.range.last + 1
                ssb.replace(start, end, replacement)
                ssb.setSpan(ForegroundColorSpan(Color.RED), start, start + replacement.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                match = regex.find(ssb, start + replacement.length)
            }
        }

        applyRule(REGEX_IL, "1")
        applyRule(REGEX_ARROW_UP, "↑")
        applyRule(REGEX_ARROW_DOWN, "↓")

        if (ssb.toString() == "4") {
            ssb.replace(0, 1, "↑")
            ssb.setSpan(ForegroundColorSpan(Color.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else applyRule(REGEX_END_4, "↑")

        if (ssb.toString() == "1") {
            ssb.replace(0, 1, "↑")
            ssb.setSpan(ForegroundColorSpan(Color.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            applyRule(REGEX_MID_1, "↑")
            applyRule(REGEX_END_1, "↑")
        }
        applyRule(REGEX_DOWN_7, "↓")

        var match = REGEX_INVALID.find(ssb)
        while (match != null) {
            ssb.delete(match.range.first, match.range.last + 1)
            match = REGEX_INVALID.find(ssb)
        }
        return ssb
    }
    // 显示剪贴板文本导入弹窗
    private fun showImportOptionsDialog() {
        val options = arrayOf(
            "📋 导入文字 (剪贴板)",
            "🖼️ 导入图片 (OCR)",
            "📝 导入已录制表格",
            "📁 从脚本库导入"
        )

        val themeContext =
            ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
        val dialog = AlertDialog.Builder(themeContext)
            .setTitle("选择数据来源")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTextImportDialog()
                    1 -> startImagePicker()
                    2 -> importFromRecordData()
                    3 -> showLocalScriptImportDialog()
                }
            }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }
    // 从本地脚本库导入 JSON 脚本
    private fun showLocalScriptImportDialog() {
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

        if (files.isEmpty()) {
            Toast.makeText(this, "脚本库为空", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name.replace(".json", "") }.toTypedArray()
        val themeContext =
            ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)

        safeShowDialog(
            AlertDialog.Builder(themeContext).setTitle("请选择脚本")
                .setItems(fileNames) { _, which ->
                    try {
                        val jsonStr = files[which].readText()
                        val scriptObj = Gson().fromJson(jsonStr, LocalScriptJson::class.java)

                        val newConfig = ConfigManager.getAllConfig(this).copy(
                            intervalAttack = scriptObj.config.intervalAttack,
                            intervalSkill = scriptObj.config.intervalSkill,
                            waitTurn = scriptObj.config.waitTurn
                        )
                        ConfigManager.saveSettings(this, newConfig)
                        reloadGlobalConfig()

                        combatEngine.instructionList.clear()

                        scriptObj.instructions?.forEach { ins ->
                            combatEngine.instructionList.add(
                                ScriptInstruction(
                                    ins.turn,
                                    ins.step,
                                    InstructionType.valueOf(ins.type),
                                    ins.value
                                )
                            )
                        }

                        scriptObj.targetSwitches?.forEach { oldTask ->
                            combatEngine.instructionList.add(
                                ScriptInstruction(
                                    oldTask.turnNumber,
                                    oldTask.afterStep,
                                    InstructionType.TARGET_SWITCH,
                                    oldTask.clickCount.toLong()
                                )
                            )
                        }

                        parseTextToTable(scriptObj.scriptContent)
                        if (isFollowMode) updateTableData()

                        Toast.makeText(
                            this,
                            "已成功載入腳本：${scriptObj.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "腳本解析失敗", Toast.LENGTH_SHORT).show()
                    }
                })
    }
    // 从录制的数据源导入到跟打数据源
    private fun importFromRecordData() {
        if (recordEngine.recordData.isEmpty()) {
            Toast.makeText(this, "录制数据为空，无法导入", Toast.LENGTH_SHORT).show()
            return
        }
        combatEngine.followData.clear()
        for (src in recordEngine.recordData) {
            val dest = TurnData(src.turnNumber)
            dest.currentStep = src.currentStep
            for (i in 0 until 5) {
                val srcText = src.characterActions[i]
                if (srcText.isNotEmpty()) {
                    dest.characterActions[i] = SpannableStringBuilder(srcText)
                }
            }
            combatEngine.followData.add(dest)
        }
        if (isFollowMode) {
            updateTableData()
        }
        Toast.makeText(
            this,
            "成功导入 ${recordEngine.recordData.size} 行录制数据",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showTextImportDialog() {
        ServiceDialogs.showTextImportDialog(this) { text ->
            parseTextToTable(text)
            if (isFollowMode) {
                tableAdapter.updateData(combatEngine.followData)
            }
            Toast.makeText(this, "跟打数据已导入", Toast.LENGTH_SHORT).show()
        }
    }
    // 将纯文本解析为二维表格回合数据
    private fun parseTextToTable(text: String) {
        if (text.isBlank()) return
        combatEngine.followData.clear()
        val lines = text.split("\n")
        var successCount = 0
        for (line in lines) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue
            val parts = trimLine.split(Regex("\\s+"))
            var startIndex =
                if (parts.isNotEmpty() && (parts[0].contains("回") || parts[0].all { it.isDigit() })) 1 else 0
            val newTurn = TurnData(combatEngine.followData.size + 1)
            var charIdx = 0
            for (i in startIndex until parts.size) {
                if (charIdx >= 5) break
                val cmd = parts[i]
                if (cmd == "-") {
                    // 遇到占位符，留空，但列索引照常推进
                    newTurn.characterActions[charIdx] = ""
                } else {
                    newTurn.characterActions[charIdx] = normalizeCommand(cmd)
                }
                charIdx++
            }
            combatEngine.followData.add(newTurn)
            successCount++
        }
        handler.post {
            updateTableData(); Toast.makeText(
            this,
            "成功导入 $successCount 行",
            Toast.LENGTH_SHORT
        ).show()
        }
    }
    @SuppressLint("ClickableViewAccessibility")


    // 更新小悬浮窗的状态信息
    private fun updateMiniWindowUI() {
        if (uiManager.minimizedView == null) return

        val layoutControls =
            uiManager.minimizedView!!.findViewById<LinearLayout>(R.id.layout_mini_controls)
        val btnAction = uiManager.minimizedView!!.findViewById<ImageButton>(R.id.btn_mini_action)
        val tvInfo = uiManager.minimizedView!!.findViewById<TextView>(R.id.tv_mini_info)

        if (!isRunning) {
            layoutControls.visibility = View.GONE
        } else {
            layoutControls.visibility = View.VISIBLE

            if (isFollowMode) {
                if (combatEngine.isPaused) {
                    btnAction.setImageResource(R.drawable.ic_action_resume)
                    tvInfo.text = "已暂停"
                    tvInfo.setTextColor(Color.parseColor("#FBC02D"))
                } else {
                    btnAction.setImageResource(R.drawable.ic_action_pause)
                    tvInfo.setTextColor(Color.parseColor("#4A6F8A"))
                    tvInfo.setTextColor(Color.parseColor("#4A6F8A"))
                }
                btnAction.setOnClickListener(null)
                btnAction.setOnClickListener { combatEngine.togglePauseResume(); updateMiniWindowUI() }
            } else {
                btnAction.setImageResource(R.drawable.ic_next_turn_chevron)
                tvInfo.text = lastRecordedInfo
                tvInfo.setTextColor(Color.parseColor("#C0392B"))
                btnAction.setOnClickListener(null)
                btnAction.setOnClickListener {
                    recordEngine.addNewTurn()
                    lastRecordedInfo = "新回合 T${recordEngine.recordData.size}"
                    updateMiniWindowUI()
                }
            }
        }
    }

    // 显示自动选人配置弹窗
    private fun showAutoSelectAgentDialog(btnAutoSelect: TextView) {
        com.example.yuanassist.ui.dialogs.AutoSelectDialog.show(
            context = this,
            isCurrentlyEnabled = isAutoSelectAgentEnabled,
            currentAgents = selectedAgents,
            allAgentsLibrary = AgentRepository.ALL_AGENTS
        ) { isEnabled, newAgents ->
            // 這個區塊是回呼，彈窗點擊儲存後會執行這裡
            isAutoSelectAgentEnabled = isEnabled
            for (i in 0 until 5) {
                selectedAgents[i] = newAgents[i]
            }
            btnAutoSelect.text = if (isAutoSelectAgentEnabled) "自动选人：开启" else "自动选人：关闭"
            Toast.makeText(this, "选人设置已保存", Toast.LENGTH_SHORT).show()
        }
    }
    // 切换录制模式与跟打模式
    private fun switchMode() {
        isRunning = false
        removeInputWindow()
        isFollowMode = !isFollowMode
        updateTableData()
        if (isFollowMode) {
            combatEngine.followData.forEach { it.isExecuting = false }
            Toast.makeText(this, "已切换：跟打模式", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已切换：录制模式", Toast.LENGTH_SHORT).show()
        }
        refreshActionButtonUI()
    }
    // 刷新主控制面板的按钮状态（播放、暂停、录制等）
    private fun refreshActionButtonUI() {
        val btnAction = uiManager.controlView?.findViewById<Button>(R.id.btn_action)
        val btnNext = uiManager.controlView?.findViewById<Button>(R.id.btn_next_turn) // 右上角
        val btnUndo = uiManager.controlView?.findViewById<Button>(R.id.btn_undo)      // 左下角 (初始化)
        val btnRedo = uiManager.controlView?.findViewById<Button>(R.id.btn_redo)      // 中下角
        val btnExport = uiManager.controlView?.findViewById<Button>(R.id.btn_export)  // 右下角
        val btnFollow = uiManager.controlView?.findViewById<Button>(R.id.btn_follow_play)

        if (btnAction == null || btnUndo == null) return

        listOf(btnAction, btnNext, btnUndo, btnRedo, btnExport, btnFollow).forEach {
            it?.setOnClickListener(null)
            it?.visibility = View.VISIBLE
            it?.background?.clearColorFilter()
            it?.setTextColor(Color.parseColor("#4A6F8A"))
        }
        btnAction.setTextColor(Color.WHITE)

        btnAction.setOnClickListener {
            if (isRunning) {
                isRunning = false
                autoTaskEngine?.stop()
                combatEngine.stop()
                removeInputWindow()
                refreshActionButtonUI()
                combatEngine.followData.forEach { it.isExecuting = false }
                tableAdapter.notifyDataSetChanged()
                Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
                RunLogger.i("用户手动停止了运行")
            } else {
                RunLogger.clear()
                if (isFollowMode && isAutoSelectAgentEnabled) {
                    if (combatEngine.followData.isEmpty()) {
                        Toast.makeText(this, "请先导入跟打数据", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    isRunning = true
                    refreshActionButtonUI()
                    minimizeControlWindow()

                    RunLogger.i("=== 启动流程：自动选人 -> 开始跟打 ===")
                    Toast.makeText(this, "开始自动选人...", Toast.LENGTH_SHORT).show()

                    val plan =
                        AutoSelectScriptBuilder.buildPlan(selectedAgents, AgentRepository.AGENT_MAP)

                    autoTaskEngine?.startPlan(plan, onCompleted = { success, errorMsg ->
                        handler.post {
                            if (success) {
                                RunLogger.i("✅ 自动选人完毕，等待 4 秒后正式进入跟打模式！")
                                Toast.makeText(
                                    this@YuanAssistService,
                                    "选人完毕，4秒后开始跟打！",
                                    Toast.LENGTH_SHORT
                                ).show()

                                isAutoSelectAgentEnabled = false
                                uiManager.controlView?.findViewById<TextView>(R.id.btn_feedback_update)?.text =
                                    "自动选人：关闭"

                                handler.postDelayed({
                                    if (isRunning) {
                                        combatEngine.start()
                                    }
                                }, 5000)

                            } else {
                                RunLogger.e("❌ 选人异常中断: $errorMsg")
                                isRunning = false
                                refreshActionButtonUI()
                                Toast.makeText(
                                    this@YuanAssistService,
                                    "选人中断: $errorMsg",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    })
                }
                else if (isFollowMode) {
                    RunLogger.i("=== 启动流程：直接开始跟打 ===")
                    if (combatEngine.followData.isEmpty()) {
                        Toast.makeText(this, "请先导入跟打数据", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    isRunning = true
                    refreshActionButtonUI()
                    minimizeControlWindow()
                    combatEngine.start()                }
                else {
                    isRunning = true
                    refreshActionButtonUI()
                    showInputWindow()
                }
            }
        }

        if (isFollowMode) {
            btnAction.text = if (isRunning) "停止运行" else "开始跟打"

            if (isRunning) {
                btnNext?.text = currentFollowInfoText
                btnNext?.setTextColor(Color.parseColor("#C0392B"))
                btnNext?.setOnClickListener(null)
            } else {
                btnNext?.text = "新增回合"
                btnNext?.setTextColor(Color.parseColor("#4A6F8A"))
                btnNext?.setOnClickListener { showInsertTurnDialog() }
            }

            btnUndo.text = "指令"
            btnUndo.setOnClickListener {
                InstructionDialogs.showListDialog(this, combatEngine.instructionList)
            }

            if (isRunning) {
                btnRedo?.text = if (combatEngine.isPaused) "继续" else "暂停"
                btnRedo?.setTextColor(
                    if (combatEngine.isPaused) Color.parseColor("#FBC02D") else Color.parseColor(
                        "#388E3C"
                    )
                )
                btnRedo?.setOnClickListener { combatEngine.togglePauseResume() }
            } else {
                btnRedo?.text = "导入"
                btnRedo?.setOnClickListener { showImportOptionsDialog() }
            }

            btnExport?.text = "导出"
            btnExport?.setOnClickListener { showSaveToLibraryDialog() }

            btnFollow?.text = "返回录制"
            btnFollow?.setOnClickListener { switchMode() }

        } else {
            btnAction.text = if (isRunning) "停止录制" else "开始录制"

            btnNext?.text = "下一回合"
            btnNext?.setOnClickListener { recordEngine.addNewTurn() }

            btnUndo.text = "备注"
            btnUndo.setOnClickListener {
                NoteDialogs.showListDialog(this, currentDisplayData) {
                    tableAdapter.notifyDataSetChanged()
                }
            }

            if (isRunning) {
                btnRedo?.text = "撤回"
                btnRedo?.setOnClickListener { recordEngine.undo() }
            } else {
                btnRedo?.text = "清空"
                btnRedo?.setOnClickListener { recordEngine.clearData() }
            }

            btnExport?.text = "导出"
            btnExport?.setOnClickListener { showExportModeSelectionDialog() }
            btnFollow?.text = "跟打模式"
            btnFollow?.setOnClickListener { switchMode() }
        }
    }
    // 安全显示系统级全局弹窗
    private fun safeShowDialog(builder: AlertDialog.Builder): AlertDialog {
        return com.example.yuanassist.utils.DialogUtils.safeShowOverlayDialog(builder)
    }
    // 刷新表格RecyclerView的数据
    private fun updateTableData() {
        if (::tableAdapter.isInitialized) {
            tableAdapter.updateData(currentDisplayData)
        }
    }
    // 在指定回合后插入空回合
    private fun showInsertTurnDialog() {
        ServiceDialogs.showInsertTurnDialog(this) { targetTurn ->
            insertTurnAfter(targetTurn)
        }
    }

    private fun insertTurnAfter(targetTurnNumber: Int) {
        if (combatEngine.followData.isEmpty()) {
            Toast.makeText(this, "列表为空，无法新增", Toast.LENGTH_SHORT).show()
            return
        }

        val index = combatEngine.followData.indexOfFirst { it.turnNumber == targetTurnNumber }
        if (index != -1) {
            for (i in index + 1 until combatEngine.followData.size) {
                combatEngine.followData[i].turnNumber += 1
            }

            combatEngine.instructionList.forEach { if (it.turn > targetTurnNumber) it.turn += 1 }
            val newTurn = TurnData(targetTurnNumber + 1)
            combatEngine.followData.add(index + 1, newTurn)

            tableAdapter.notifyDataSetChanged()
            Toast.makeText(this, "已在 T$targetTurnNumber 后新增空回合", Toast.LENGTH_SHORT).show()

            val rv = uiManager.controlView?.findViewById<RecyclerView>(R.id.rv_log_table)
            rv?.scrollToPosition(index + 1)
        } else {
            Toast.makeText(this, "找不到第 $targetTurnNumber 回合，请检查", Toast.LENGTH_SHORT)
                .show()
        }
    }


    // 弹出单步动作的编辑弹窗
    private fun showEditDialog(turnIndex: Int, charIndex: Int) {
        if (turnIndex >= currentDisplayData.size) return
        val currentText = currentDisplayData[turnIndex].characterActions[charIndex]

        ServiceDialogs.showEditActionDialog(
            this, currentText.toString(),
            onSave = { newText ->
                if (newText != currentText.toString()) {
                    if (!isFollowMode) recordEngine.recordAction(
                        turnIndex,
                        charIndex,
                        currentText,
                        newText,
                        currentDisplayData[turnIndex].currentStep,
                        currentDisplayData[turnIndex].currentStep
                    )
                    currentDisplayData[turnIndex].characterActions[charIndex] = newText
                    tableAdapter.notifyItemChanged(turnIndex)
                }
            },
            onClear = {
                if (!isFollowMode) recordEngine.recordAction(
                    turnIndex,
                    charIndex,
                    currentText,
                    "",
                    currentDisplayData[turnIndex].currentStep,
                    currentDisplayData[turnIndex].currentStep
                )
                currentDisplayData[turnIndex].characterActions[charIndex] = ""
                tableAdapter.notifyItemChanged(turnIndex)
            }
        )
    }

    private fun showExportDialog() {
        ServiceDialogs.showExportImageSettingsDialog(this) { headers ->
            com.example.yuanassist.utils.ImageExportUtils.generateAndSaveImage(
                this,
                currentDisplayData,
                headers
            )
        }
    }

    // 弹出导出图片或脚本库的选项
    private fun showExportModeSelectionDialog() {
        val options = arrayOf("🖼️ 导出表格图片", "💾 导出到脚本库")
        val themeContext =
            android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
        safeShowDialog(
            AlertDialog.Builder(themeContext).setTitle("请选择导出方式")
                .setItems(options) { _, which ->
                    if (which == 0) showExportDialog() else showSaveToLibraryDialog()
                })
    }

    private fun showSaveToLibraryDialog() {
        val defaultName = "我的脚本_${System.currentTimeMillis() % 10000}"

        ServiceDialogs.showSaveToLibraryDialog(this, defaultName) { scriptName ->
            val sb = StringBuilder()
            for (turn in currentDisplayData) {
                sb.append("${turn.turnNumber}回合")
                for (action in turn.characterActions) {
                    sb.append("\t").append(action.ifEmpty { "-" })
                }
                sb.append("\n")
            }

            val configJson = ScriptConfigJson(
                appConfig.intervalAttack,
                appConfig.intervalSkill,
                appConfig.waitTurn
            )
            val insJsonList = combatEngine.instructionList.map {
                InstructionJson(
                    it.turn,
                    it.step,
                    it.type.name,
                    it.value
                )
            }
            val finalJsonObj = LocalScriptJson(
                title = scriptName,
                scriptContent = sb.toString(),
                config = configJson,
                instructions = insJsonList
            )

            try {
                val dir = File(filesDir, "scripts")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$scriptName.json")
                file.writeText(Gson().toJson(finalJsonObj))
                Toast.makeText(this, "已成功保存至脚本库！", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun showControlWindow() {
        val view = uiManager.createControlWindow()
        if (view.tag == "initialized") return

        val rvTable = view.findViewById<RecyclerView>(R.id.rv_log_table)
        val btnMinimize = view.findViewById<Button>(R.id.btn_minimize)
        val btnClose = view.findViewById<Button>(R.id.btn_close)
        val btnAutoSelect = view.findViewById<TextView>(R.id.btn_feedback_update)

        btnAutoSelect.text = if (isAutoSelectAgentEnabled) "自动选人：开启" else "自动选人：关闭"
        btnAutoSelect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showAutoSelectAgentDialog(btnAutoSelect)
            } else {
                Toast.makeText(this, "「自动选人」功能需要安卓 11 及以上系统支持", Toast.LENGTH_SHORT).show()
            }
        }
        tableAdapter = LogAdapter(currentDisplayData) { turnIndex, charIndex ->
            showEditDialog(turnIndex, charIndex)
        }
        rvTable.layoutManager = LinearLayoutManager(this)
        rvTable.adapter = tableAdapter
        btnMinimize.setOnClickListener { minimizeControlWindow() }
        btnClose.setOnClickListener { disableSelf(); System.exit(0) }

        view.tag = "initialized"
        refreshActionButtonUI()
    }
    // 显示迷你悬浮球
    @SuppressLint("ClickableViewAccessibility")
    private fun showMinimizedWindow() {
        val view = uiManager.createMinimizedWindow()
        if (view.tag == "initialized") {
            updateMiniWindowUI()
            return
        }

        val dragHandle = view.findViewById<ImageView>(R.id.iv_mini_drag_handle)
        val btnStop = view.findViewById<ImageButton>(R.id.btn_mini_stop)

        dragHandle.setOnClickListener {
            uiManager.removeMinimizedWindow()
            showControlWindow()
        }

        btnStop.setOnClickListener {
            isRunning = false
            autoTaskEngine?.stop()
            combatEngine.stop()
            removeInputWindow()
            if (isFollowMode) combatEngine.followData.forEach { it.isExecuting = false }
            refreshActionButtonUI()
            uiManager.removeMinimizedWindow()
            showControlWindow()
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        }

        view.tag = "initialized"
        updateMiniWindowUI()
    }
    // 展开透明的屏幕输入拦截层（录制用）
    private fun showInputWindow() {
        uiManager.createInputWindow { event ->
            recordEngine.handleTouch(
                event,
                isSimulating,
                isFollowMode
            )
        }
    }
    // 移除相关悬浮窗
    private fun removeInputWindow() {
        uiManager.removeInputWindow()
        isSimulating = false
    }

    private fun minimizeControlWindow() {
        uiManager.removeControlWindow()
        showMinimizedWindow()
    }
}

