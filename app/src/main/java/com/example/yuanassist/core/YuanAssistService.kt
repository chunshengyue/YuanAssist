
package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.util.TypedValue
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

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
    private var appConfig: AppConfig = AppConfig(
        intervalAttack = 2500,
        intervalSkill = 4000,
        waitTurn = 8000,
        startTurn = 1,
        enableTurnNumberCheck = true,
        swipeThreshold = 50,
        inputHeightRatio = 31,
        recordDelay = 60,
        gameSpeed = 3
    )
    private var lastRecordedInfo = "准备录制"
    private var currentFollowInfoText = "准备执行"

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: YuanAssistService? = null

        fun onImagePicked(bitmap: Bitmap) {
            instance?.processOcrImage(bitmap)
        }

        private val REGEX_INVALID = Regex("[^0-9A\\u2191\\u2193\\u5708]")
        private val REGEX_IL = Regex("[Il|](?=\\d)")
        private val REGEX_ARROW_UP = Regex("[\\u2192\\u2190TI|l+t/]")
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

    val currentDisplayData: MutableList<TurnData>
        get() = if (isFollowMode) combatEngine.followData else recordEngine.recordData
    private var tableAdapter: LogAdapter? = null
    private lateinit var uiManager: com.example.yuanassist.ui.FloatingUIManager
    private var combatAnchorPickerView: View? = null
    private var pendingCombatAnchorType: String? = null
    private val systemWindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    @Volatile
    private var isOcrProcessing = false
    override fun onCreate() {
        super.onCreate()
        uiManager = com.example.yuanassist.ui.FloatingUIManager(this)
        gestureDispatcher = GestureDispatcher(this, uiManager, handler)
    }

    private fun updateOverlayStatePrefs(combatOpen: Boolean? = null, dailyOpen: Boolean? = null) {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().apply {
            combatOpen?.let { putBoolean("combat_window_open", it) }
            dailyOpen?.let { putBoolean("daily_window_open", it) }
            apply()
        }
    }

    fun isDailyWindowVisible(): Boolean = dailyWindowManager?.isWindowVisible() == true

    fun isCombatWindowVisible(): Boolean =
        uiManager.controlView != null || uiManager.minimizedView != null || uiManager.inputView != null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action != null &&
            action != "ACTION_START_DAILY_WINDOW" &&
            action != "ACTION_START_BIRD_FOOD" &&
            action != "ACTION_START_MAINLINE_624" &&
            action != "ACTION_START_INVENTORY_STITCH" &&
            action != "ACTION_START_COORDINATE_PICKER" &&
            action != "ACTION_CLOSE_COMBAT_WINDOW" &&
            action != "ACTION_CLOSE_DAILY_WINDOW"
        ) {
            if (!this::combatEngine.isInitialized || !this::recordEngine.isInitialized) {
                Log.w("YuanAssistService", "Service not fully connected yet, ignoring command: $action")
                return super.onStartCommand(intent, flags, startId)
            }
        }

        when (action) {
            "ACTION_RELOAD_CONFIG" -> {
                reloadGlobalConfig()
                Toast.makeText(this, "悬浮窗设置已应用", Toast.LENGTH_SHORT).show()
            }
            "ACTION_START_DAILY_WINDOW" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
            }
            "ACTION_START_BIRD_FOOD" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val config = DailyBirdFoodBridge.pendingConfig
                if (config == null) {
                    Toast.makeText(this, "鸟食配置缺失", Toast.LENGTH_SHORT).show()
                } else {
                    dailyWindowManager?.submitBirdFoodConfig(config)
                    dailyWindowManager?.showWindow()
                }
            }
            "ACTION_START_MAINLINE_624" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val config = DailyMainline624Bridge.pendingConfig
                if (config == null) {
                    Toast.makeText(this, "6-24配置缺失", Toast.LENGTH_SHORT).show()
                } else {
                    dailyWindowManager?.submitMainline624Config(config)
                    dailyWindowManager?.showWindow()
                }
            }
            "ACTION_START_INVENTORY_STITCH" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val stoneType = intent?.getStringExtra(DailyInventoryStitchFragment.KEY_PENDING_STONE_TYPE)
                    ?: MyStoneStore.getSelectedType(this)
                dailyWindowManager?.prepareInventoryStitching(stoneType)
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
            }
            "ACTION_START_COORDINATE_PICKER" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                dailyWindowManager?.startCoordinatePickerMode()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
            }
            "ACTION_START_COMBAT_WINDOW" -> {
                dailyWindowManager?.hideWindow()
                showControlWindow()
                updateOverlayStatePrefs(combatOpen = true, dailyOpen = false)
                if (tableAdapter != null) {
                    updateTableData()
                }
            }
            "ACTION_CLOSE_DAILY_WINDOW" -> {
                dailyWindowManager?.hideWindow()
                updateOverlayStatePrefs(dailyOpen = false)
            }
            "ACTION_CLOSE_COMBAT_WINDOW" -> {
                closeCombatFloatingWindows()
            }
            "ACTION_IMPORT_SCRIPT" -> {
                combatEngine.instructionList.clear()

                val scriptContent = intent.getStringExtra("SCRIPT_CONTENT")
                if (!scriptContent.isNullOrEmpty()) {
                    parseTextToTable(scriptContent)
                    if (!isFollowMode) {
                        Toast.makeText(this, "脚本已导入，请切换到跟打模式查看。", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "脚本导入成功", Toast.LENGTH_SHORT).show()
                    }
                }

                val configJson = intent.getStringExtra("CONFIG_JSON")
                if (configJson != null) {
                    try {
                        val cfg = Gson().fromJson(configJson, ScriptConfigJson::class.java)
                        val currentConfig = ConfigManager.getAllConfig(this)
                        val newConfig = currentConfig.copy(
                            intervalAttack = cfg.intervalAttack,
                            intervalSkill = cfg.intervalSkill,
                            waitTurn = cfg.waitTurn
                        )
                        ConfigManager.saveSettings(this, newConfig)
                        reloadGlobalConfig()
                        Toast.makeText(this, "已应用推荐设置", Toast.LENGTH_SHORT).show()
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
                                it.turn == ins.turn &&
                                    it.step == ins.step &&
                                    it.type.name == ins.type &&
                                    it.value == ins.value
                            }
                            if (!exists) {
                                combatEngine.instructionList.add(
                                    ScriptInstruction(ins.turn, ins.step, InstructionType.valueOf(ins.type), ins.value)
                                )
                                importCount++
                            }
                        }
                        if (importCount > 0) {
                            Toast.makeText(this, "已导入 $importCount 条额外指令", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this, "已加载 $validCount 名自动选人角色", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "OpenCV 初始化失败", Toast.LENGTH_LONG).show()
        } else {
            Log.d("GameAssist", "OpenCV 初始化成功")
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
                    else tableAdapter?.notifyItemChanged(turnIndex)
                }
            },
            onTurnInserted = { turnIndex ->
                handler.post {
                    tableAdapter?.notifyItemInserted(turnIndex)
                    uiManager.controlView?.findViewById<RecyclerView>(R.id.rv_log_table)?.scrollToPosition(turnIndex)
                }
            },
            onTurnRemoved = { turnIndex ->
                handler.post { tableAdapter?.notifyItemRemoved(turnIndex) }
            },
            onActionRecorded = { actionText ->
                handler.post {
                    lastRecordedInfo = "记录: $actionText"
                    updateMiniWindowUI()
                }
            }
        )
        combatEngine = CombatEngine(
            accessibilityService = this,
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
                    else tableAdapter?.notifyItemChanged(rowIndex)
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
            updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
        } else if (pendingAction == "ACTION_START_BIRD_FOOD") {
            if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
            DailyBirdFoodBridge.pendingConfig?.let { dailyWindowManager?.submitBirdFoodConfig(it) }
            dailyWindowManager?.showWindow()
            updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
        } else if (pendingAction == "ACTION_START_MAINLINE_624") {
            if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
            DailyMainline624Bridge.pendingConfig?.let { dailyWindowManager?.submitMainline624Config(it) }
            dailyWindowManager?.showWindow()
            updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
        } else if (pendingAction == "ACTION_START_INVENTORY_STITCH") {
            if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
            val stoneType = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString(DailyInventoryStitchFragment.KEY_PENDING_STONE_TYPE, MyStoneStore.TYPE_MAIN)
                ?: MyStoneStore.TYPE_MAIN
            dailyWindowManager?.prepareInventoryStitching(stoneType)
            updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
        } else if (pendingAction == "ACTION_START_COORDINATE_PICKER") {
            if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
            dailyWindowManager?.startCoordinatePickerMode()
            updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
        } else {
            showControlWindow()
            updateOverlayStatePrefs(combatOpen = true, dailyOpen = false)
            updateTableData()
        }
        autoTaskEngine = AutoTaskEngine(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        dailyWindowManager?.hideWindow()
        updateOverlayStatePrefs(combatOpen = false, dailyOpen = false)
        stopCombatAnchorPicker()
        autoTaskEngine?.release()
        if (this::combatEngine.isInitialized) {
            combatEngine.stop()
        }
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
    private fun startImagePicker() {
        val intent = Intent(this, ImagePickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    // Upload image to OCR backend and import the parsed result
    fun processOcrImage(bitmap: Bitmap) {
        if (isOcrProcessing) {
            Toast.makeText(this, "OCR 正在处理中", Toast.LENGTH_SHORT).show()
            return
        }

        isOcrProcessing = true
        Toast.makeText(this, "正在上传到 OCR...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            val result = OcrManager.recognizeImage(bitmap, deviceId,
                onRetryMsg = {
                    Toast.makeText(this@YuanAssistService, "网络繁忙，正在重试...", Toast.LENGTH_SHORT).show()
                },
                onStart = {
                    uiManager.controlView?.findViewById<View>(R.id.layout_ocr_loading)?.visibility = View.VISIBLE
                },
                onFinish = {
                    isOcrProcessing = false
                    uiManager.controlView?.findViewById<View>(R.id.layout_ocr_loading)?.visibility = View.GONE
                }
            )

            when (result) {
                is OcrManager.OcrResult.Success -> {
                    parseTextToTable(result.parsedText)
                    if (!isFollowMode) {
                        Toast.makeText(
                            this@YuanAssistService,
                            "OCR 识别成功，请切换到跟打模式查看。",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@YuanAssistService, "OCR 识别成功（${result.strategyUsed}）", Toast.LENGTH_SHORT).show()
                        showControlWindow()
                    }
                }
                is OcrManager.OcrResult.Error -> {
                    Toast.makeText(this@YuanAssistService, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    // Normalize OCR output into command symbols
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
        applyRule(REGEX_ARROW_UP, "\u2191")
        applyRule(REGEX_ARROW_DOWN, "\u2193")

        if (ssb.toString() == "4") {
            ssb.replace(0, 1, "\u2191")
            ssb.setSpan(ForegroundColorSpan(Color.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else applyRule(REGEX_END_4, "\u2191")

        if (ssb.toString() == "1") {
            ssb.replace(0, 1, "\u2191")
            ssb.setSpan(ForegroundColorSpan(Color.RED), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            applyRule(REGEX_MID_1, "\u2191")
            applyRule(REGEX_END_1, "\u2191")
        }
        applyRule(REGEX_DOWN_7, "\u2193")

        var match = REGEX_INVALID.find(ssb)
        while (match != null) {
            ssb.delete(match.range.first, match.range.last + 1)
            match = REGEX_INVALID.find(ssb)
        }
        return ssb
    }
    // Show import options dialog
    private fun showImportOptionsDialog() {
        val options = arrayOf(
            "导入文本（剪贴板）",
            "导入图片（OCR）",
            "导入录制表格",
            "从脚本库导入"
        )

        val themeContext =
            ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
        val dialog = AlertDialog.Builder(themeContext)
            .setTitle("选择导入来源")
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
    // Import JSON script from the local script library
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
            AlertDialog.Builder(themeContext).setTitle("选择脚本")
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
                            "已加载脚本：${scriptObj.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "脚本解析失败", Toast.LENGTH_SHORT).show()
                    }
                })
    }
    // Import recorded data into follow-mode data
    private fun importFromRecordData() {
        if (recordEngine.recordData.isEmpty()) {
            Toast.makeText(this, "录制数据为空", Toast.LENGTH_SHORT).show()
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
            "已导入 ${recordEngine.recordData.size} 行录制数据",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showTextImportDialog() {
        ServiceDialogs.showTextImportDialog(this) { text ->
            parseTextToTable(text)
            if (isFollowMode) {
                tableAdapter?.updateData(combatEngine.followData)
            }
            Toast.makeText(this, "跟打数据已导入", Toast.LENGTH_SHORT).show()
        }
    }
    // Parse plain text into follow-mode table data
    private fun parseTextToTable(text: String) {
        if (text.isBlank()) return
        combatEngine.followData.clear()
        val lines = text.split("\n")
        var successCount = 0
        for (line in lines) {
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) continue
            val parts = if (rawLine.contains("\t")) rawLine.split("\t") else rawLine.trim().split(Regex("\\s+"))
            val firstPart = parts.firstOrNull()?.trim().orEmpty()
            val startIndex =
                if (firstPart.contains("\u56DE") || firstPart.all { it.isDigit() }) 1 else 0
            val newTurn = TurnData(combatEngine.followData.size + 1)
            var charIdx = 0
            for (i in startIndex until parts.size) {
                if (charIdx >= 5) break
                val cmd = parts[i].trim()
                if (cmd == "-") {
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
            updateTableData()
            Toast.makeText(this, "已导入 $successCount 行", Toast.LENGTH_SHORT).show()
        }
    }
    @SuppressLint("ClickableViewAccessibility")


    // Update minimized floating window state
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
                    tvInfo.text = currentFollowInfoText
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

    // Show auto-select agent dialog
    private fun showAutoSelectAgentDialog(btnAutoSelect: TextView) {
        com.example.yuanassist.ui.dialogs.AutoSelectDialog.show(
            context = this,
            isCurrentlyEnabled = isAutoSelectAgentEnabled,
            currentAgents = selectedAgents,
            allAgentsLibrary = AgentRepository.ALL_AGENTS
        ) { isEnabled, newAgents ->
            isAutoSelectAgentEnabled = isEnabled
            for (i in 0 until 5) {
                selectedAgents[i] = newAgents[i]
            }
            btnAutoSelect.text = if (isAutoSelectAgentEnabled) "自动选人：开启" else "自动选人：关闭"
            Toast.makeText(this, "自动选人设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    // Switch between record mode and follow mode
    private fun switchMode() {
        isRunning = false
        removeInputWindow()
        isFollowMode = !isFollowMode
        updateTableData()
        if (isFollowMode) {
            combatEngine.followData.forEach { it.isExecuting = false }
            Toast.makeText(this, "已切换到跟打模式", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已切换到录制模式", Toast.LENGTH_SHORT).show()
        }
        refreshActionButtonUI()
    }
    // Refresh action button states on the main control panel
    private fun refreshActionButtonUI() {
        val btnAction = uiManager.controlView?.findViewById<Button>(R.id.btn_action)
        val btnNext = uiManager.controlView?.findViewById<Button>(R.id.btn_next_turn)
        val btnUndo = uiManager.controlView?.findViewById<Button>(R.id.btn_undo)
        val btnRedo = uiManager.controlView?.findViewById<Button>(R.id.btn_redo)
        val btnExport = uiManager.controlView?.findViewById<Button>(R.id.btn_export)
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
                tableAdapter?.notifyDataSetChanged()
                Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
                RunLogger.i("用户手动停止运行")
            } else {
                RunLogger.clear()
                if (isFollowMode && isAutoSelectAgentEnabled) {
                    if (combatEngine.followData.isEmpty()) {
                        Toast.makeText(this, "请先导入跟打数据", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    isRunning = true
                    refreshActionButtonUI()
                    minimizeControlWindow(autoDockToTopLeft = true)

                    RunLogger.i("=== 启动流程：自动选人 -> 跟打 ===")
                    Toast.makeText(this, "开始自动选人...", Toast.LENGTH_SHORT).show()

                    val plan =
                        AutoSelectScriptBuilder.buildPlan(selectedAgents, AgentRepository.AGENT_MAP)

                    autoTaskEngine?.startPlan(plan, onCompleted = { success, errorMsg ->
                        handler.post {
                            if (success) {
                                RunLogger.i("自动选人完成，5 秒后开始跟打")
                                Toast.makeText(
                                    this@YuanAssistService,
                                    "自动选人完成，5 秒后开始跟打。",
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
                                RunLogger.e("自动选人中断：$errorMsg")
                                isRunning = false
                                refreshActionButtonUI()
                                Toast.makeText(
                                    this@YuanAssistService,
                                    "自动选人中断：$errorMsg",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    })
                } else if (isFollowMode) {
                    RunLogger.i("=== 启动流程：直接开始跟打 ===")
                    if (combatEngine.followData.isEmpty()) {
                        Toast.makeText(this, "请先导入跟打数据", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    isRunning = true
                    refreshActionButtonUI()
                    minimizeControlWindow(autoDockToTopLeft = true)
                    combatEngine.start()
                } else {
                    isRunning = true
                    refreshActionButtonUI()
                    showInputWindow()
                }
            }
        }

        if (isFollowMode) {
            btnAction.text = if (isRunning) "停止" else "开始跟打"

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
                    tableAdapter?.notifyDataSetChanged()
                }
            }

            if (isRunning) {
                btnRedo?.text = "撤销"
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
    private fun safeShowDialog(builder: AlertDialog.Builder): AlertDialog {
        return com.example.yuanassist.utils.DialogUtils.safeShowOverlayDialog(builder)
    }

    private fun showCombatAnchorPickerDialog() {
        val options = arrayOf("A", "↑", "↓", "圈")
        val dialog = safeShowDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle("选择要修正的动作")
                .setItems(options) { _, which ->
                    startCombatAnchorPicker(options[which])
                }
                .setNegativeButton("取消", null)
        )
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCombatAnchorPicker(anchorType: String) {
        stopCombatAnchorPicker()
        pendingCombatAnchorType = anchorType

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        val hintView = TextView(this).apply {
            text = "点击屏幕，设置 $anchorType 距离底部距离"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        overlay.addView(
            hintView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        overlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    saveCombatAnchor(anchorType, event.rawY)
                    stopCombatAnchorPicker()
                    true
                }
                else -> true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        combatAnchorPickerView = overlay
        systemWindowManager.addView(overlay, params)
        Toast.makeText(this, "$anchorType 取点模式已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopCombatAnchorPicker() {
        combatAnchorPickerView?.let { view ->
            try {
                systemWindowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                combatAnchorPickerView = null
                pendingCombatAnchorType = null
            }
        }
    }

    private fun saveCombatAnchor(anchorType: String, rawY: Float) {
        val yFromBottom = ((coordinateManager.screenHeight - rawY) / coordinateManager.gameScale).coerceAtLeast(0f)

        val oldConfig = ConfigManager.getAllConfig(this)
        val newConfig = when (anchorType) {
            "A" -> oldConfig.copy(attackYFromBottom = yFromBottom)
            "↑" -> oldConfig.copy(upYFromBottom = yFromBottom)
            "↓" -> oldConfig.copy(downYFromBottom = yFromBottom)
            else -> oldConfig.copy(circleYFromBottom = yFromBottom)
        }
        ConfigManager.saveSettings(this, newConfig)
        reloadGlobalConfig()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val summary = "$anchorType 距离底部距离=${formatConfigFloat(yFromBottom)}"
        clipboard.setPrimaryClip(ClipData.newPlainText("combat-anchor", summary))
        Toast.makeText(this, "$anchorType 已保存: $summary", Toast.LENGTH_LONG).show()
    }

    private fun formatConfigFloat(value: Float): String {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun updateTableData() {
        if (tableAdapter != null) {
            tableAdapter?.updateData(currentDisplayData)
        }
    }
    private fun showInsertTurnDialog() {
        ServiceDialogs.showInsertTurnDialog(this) { targetTurn ->
            insertTurnAfter(targetTurn)
        }
    }

    private fun insertTurnAfter(targetTurnNumber: Int) {
        if (combatEngine.followData.isEmpty()) {
            Toast.makeText(this, "列表为空", Toast.LENGTH_SHORT).show()
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

            tableAdapter?.notifyDataSetChanged()
            Toast.makeText(this, "已在 T$targetTurnNumber 后插入空回合", Toast.LENGTH_SHORT).show()

            val rv = uiManager.controlView?.findViewById<RecyclerView>(R.id.rv_log_table)
            rv?.scrollToPosition(index + 1)
        } else {
            Toast.makeText(this, "未找到回合 T$targetTurnNumber", Toast.LENGTH_SHORT).show()
        }
    }


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
                    tableAdapter?.notifyItemChanged(turnIndex)
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
                tableAdapter?.notifyItemChanged(turnIndex)
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

    // Show export mode dialog
    private fun showExportModeSelectionDialog() {
        val options = arrayOf("导出表格图片", "导出到脚本库")
        val themeContext =
            android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)
        safeShowDialog(
            AlertDialog.Builder(themeContext).setTitle("选择导出方式")
                .setItems(options) { _, which ->
                    if (which == 0) showExportDialog() else showSaveToLibraryDialog()
                })
    }

    private fun showSaveToLibraryDialog() {
        val defaultName = "我的脚本_${System.currentTimeMillis() % 10000}"

        ServiceDialogs.showSaveToLibraryDialog(this, defaultName) { scriptName ->
            val sb = StringBuilder()
            for (turn in currentDisplayData) {
                sb.append("${turn.turnNumber}\u56DE\u5408")
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
                Toast.makeText(this, "已保存到脚本库", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
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
        val btnCombatAnchorPicker = view.findViewById<TextView>(R.id.btn_combat_anchor_picker)
        val btnMiniSettings = view.findViewById<TextView>(R.id.btn_mini_settings)
        btnAutoSelect.text = if (isAutoSelectAgentEnabled) "自动选人：开启" else "自动选人：关闭"
        btnAutoSelect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showAutoSelectAgentDialog(btnAutoSelect)
            } else {
                Toast.makeText(this, "自动选人需要 Android 11 及以上版本", Toast.LENGTH_SHORT).show()
            }
        }
        btnCombatAnchorPicker?.setOnClickListener {
            showCombatAnchorPickerDialog()
        }
        btnMiniSettings?.setOnClickListener {
            SettingsDialog.show(this) {
                reloadGlobalConfig()
            }
        }
        tableAdapter = LogAdapter(currentDisplayData) { turnIndex, charIndex ->
            showEditDialog(turnIndex, charIndex)
        }
        rvTable.layoutManager = LinearLayoutManager(this)
        rvTable.adapter = tableAdapter
        btnMinimize.setOnClickListener { minimizeControlWindow() }
        btnClose.setOnClickListener { closeCombatFloatingWindows() }

        view.tag = "initialized"
        refreshActionButtonUI()
    }
    // Show minimized floating window
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
    private fun showInputWindow() {
        uiManager.createInputWindow { event ->
            recordEngine.handleTouch(
                event,
                isSimulating,
                isFollowMode
            )
        }
    }
    private fun removeInputWindow() {
        uiManager.removeInputWindow()
        isSimulating = false
    }

    private fun minimizeControlWindow(autoDockToTopLeft: Boolean = false) {
        uiManager.removeControlWindow()
        showMinimizedWindow()
        if (autoDockToTopLeft) {
            uiManager.moveMinimizedWindowToTopLeftSafely()
        }
    }

    private fun closeCombatFloatingWindows() {
        isRunning = false
        autoTaskEngine?.stop()
        if (this::combatEngine.isInitialized) {
            combatEngine.stop()
        }
        removeInputWindow()
        uiManager.removeMinimizedWindow()
        uiManager.removeControlWindow()
        if (isFollowMode && this::combatEngine.isInitialized) {
            combatEngine.followData.forEach { it.isExecuting = false }
        }
        updateOverlayStatePrefs(combatOpen = false)
        refreshActionButtonUI()
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
    }

    fun getExportableInstructions(): List<InstructionJson> {
        if (!::combatEngine.isInitialized) return emptyList()
        return combatEngine.instructionList.map {
            InstructionJson(it.turn, it.step, it.type.name, it.value)
        }
    }
}

