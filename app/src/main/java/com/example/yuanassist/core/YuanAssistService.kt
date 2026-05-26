
package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
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
import android.view.inputmethod.InputMethodManager
import android.util.TypedValue
import android.widget.FrameLayout
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.*
import com.example.yuanassist.network.OcrManager
import com.example.yuanassist.tableocr.TableOcrPreviewResult
import com.example.yuanassist.ui.*
import com.example.yuanassist.ui.dialogs.*
import com.example.yuanassist.utils.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import java.io.File
import kotlin.coroutines.resume
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

data class PendingCombatScriptExport(
    val targetMode: String,
    val scriptJson: String
)

object CombatScriptExportBridge {
    @Volatile
    var pendingRequest: PendingCombatScriptExport? = null
}

class YuanAssistService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appConfig: AppConfig = AppConfig(
        intervalAttack = 3000,
        intervalSkill = 4500,
        waitTurn = 8000,
        startTurn = 1,
        enableTurnNumberCheck = false,
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
    private var combatCircleButtonView: TextView? = null
    private var combatCircleButtonRestoreRunnable: Runnable? = null
    private var combatLeftSwitchButtonView: TextView? = null
    private var combatLeftSwitchButtonRestoreRunnable: Runnable? = null
    private var combatRightSwitchButtonView: TextView? = null
    private var combatRightSwitchButtonRestoreRunnable: Runnable? = null
    private var combatCircleSlotIndex = 0
    private var pendingCombatAnchorType: String? = null
    private data class CombatAnchorAdjustSpec(
        val type: String,
        val slotIndex: Int,
        val color: String,
        val yFromBottom: () -> Float
    )
    private val systemWindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    @Volatile
    private var isOcrProcessing = false
    override fun onCreate() {
        try {
            super.onCreate()
            uiManager = com.example.yuanassist.ui.FloatingUIManager(this)
            gestureDispatcher = GestureDispatcher(this, uiManager, handler)
        } catch (t: Throwable) {
            ExceptionLogStore.recordServiceException(this, "onCreate", t)
            throw t
        }
    }

    private fun appendAccessibilityTrace(message: String) {
        ExceptionLogStore.appendAccessibilityTrace(this, message)
    }

    private fun buildAccessibilityStatusSummary(): String {
        val overlayPermission = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                "已开启"
            } else {
                "未开启"
            }
        }.getOrElse {
            "获取失败"
        }
        val accessibilitySwitch = runCatching {
            val enabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (enabled) "已开启" else "未开启"
        }.getOrElse {
            "获取失败"
        }
        val serviceEnabled = runCatching {
            val expectedComponent = ComponentName(this, YuanAssistService::class.java)
            val setting = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(setting)
            var matched = false
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expectedComponent) {
                    matched = true
                    break
                }
            }
            if (matched) "已启用" else "未启用"
        }.getOrElse {
            "获取失败"
        }
        return "悬浮窗=$overlayPermission，无障碍=总开关$accessibilitySwitch/本服务$serviceEnabled"
    }

    private fun buildWindowVisibilitySummary(): String {
        return "战斗悬浮窗=${isCombatWindowVisible()}，日常悬浮窗=${isDailyWindowVisible()}"
    }

    private fun scheduleAccessibilityStateCheck(
        checkpoint: String,
        delayMs: Long,
        pendingAction: String?
    ) {
        handler.postDelayed({
            appendAccessibilityTrace(
                "$checkpoint：${buildAccessibilityStatusSummary()}，${buildWindowVisibilitySummary()}，pendingAction=$pendingAction"
            )
        }, delayMs)
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
        try {
            val action = intent?.action

            if (action != null &&
                action != "ACTION_START_BIRD_FOOD" &&
                action != "ACTION_START_MAINLINE_624" &&
                action != "ACTION_START_STARGAZING" &&
                action != "ACTION_START_INVENTORY_STITCH" &&
                action != "ACTION_START_CHARACTER_IMPORT" &&
                action != "ACTION_START_BOX_OCR" &&
                action != "ACTION_START_COORDINATE_PICKER" &&
                action != "ACTION_START_DAILY_SCRIPT_RECORDER" &&
                action != "ACTION_IMPORT_RECORDED_DAILY_PLAN" &&
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
            "ACTION_START_STARGAZING" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val config = StargazingBridge.pendingConfig
                if (config == null) {
                    Toast.makeText(this, "观星配置缺失", Toast.LENGTH_SHORT).show()
                } else {
                    dailyWindowManager?.submitStargazingConfig(config)
                    dailyWindowManager?.showWindow()
                }
            }
            "ACTION_START_PI_JING_ZHAN_JI" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val config = PiJingZhanJiBridge.pendingConfig
                if (config == null) {
                    Toast.makeText(this, "披荆斩棘配置缺失", Toast.LENGTH_SHORT).show()
                } else {
                    dailyWindowManager?.submitPiJingZhanJiConfig(config)
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
                val archiveId = intent?.getStringExtra(DailyInventoryStitchFragment.KEY_PENDING_STONE_ARCHIVE_ID)
                    ?: MyStoneStore.getSelectedArchiveId(this)
                dailyWindowManager?.prepareInventoryStitching(stoneType, archiveId)
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
            }
            "ACTION_START_CHARACTER_IMPORT" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val config = CharacterImportBridge.pendingConfig
                if (config == null) {
                    Toast.makeText(this, "角色导入配置缺失", Toast.LENGTH_SHORT).show()
                } else {
                    dailyWindowManager?.submitCharacterImportConfig(config)
                    dailyWindowManager?.showWindow()
                    updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                }
            }
            "ACTION_START_BOX_OCR" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                dailyWindowManager?.startBoxOcrMode()
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
            "ACTION_START_DAILY_SCRIPT_RECORDER" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                dailyWindowManager?.startScriptRecorderMode()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
            }
            "ACTION_IMPORT_RECORDED_DAILY_PLAN" -> {
                removeInputWindow()
                uiManager.removeControlWindow()
                uiManager.removeMinimizedWindow()
                if (dailyWindowManager == null) {
                    dailyWindowManager = DailyWindowManager(this)
                }
                val fileName = intent?.getStringExtra("EXTRA_DAILY_PLAN_FILE_NAME")
                val jsonContent = intent?.getStringExtra("EXTRA_DAILY_PLAN_JSON")
                val templateDirPath = intent?.getStringExtra("EXTRA_DAILY_PLAN_TEMPLATE_DIR")
                if (fileName.isNullOrBlank() || jsonContent.isNullOrBlank()) {
                    Toast.makeText(this, "录制脚本导入参数缺失", Toast.LENGTH_SHORT).show()
                } else {
                    val result = dailyWindowManager?.submitTaskPlanJson(fileName, jsonContent, templateDirPath)
                    if (result?.isFailure == true) {
                        Toast.makeText(
                            this,
                            "录制脚本导入失败：${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        dailyWindowManager?.showWindow()
                        updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                    }
                }
            }
            "ACTION_START_COMBAT_WINDOW" -> {
                dailyWindowManager?.hideWindow()
                showControlWindow()
                updateOverlayStatePrefs(combatOpen = true, dailyOpen = false)
                if (tableAdapter != null) {
                    updateTableData()
                }
                applyPendingCombatScriptExportIfNeeded()
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
                            val normalizedInstruction = ins.toScriptInstructionOrNull() ?: return@forEach
                            val exists = combatEngine.instructionList.any {
                                val existingInstruction = it.normalized()
                                existingInstruction.turn == normalizedInstruction.turn &&
                                    existingInstruction.step == normalizedInstruction.step &&
                                    existingInstruction.type == normalizedInstruction.type &&
                                    existingInstruction.value == normalizedInstruction.value
                            }
                            if (!exists) {
                                combatEngine.instructionList.add(normalizedInstruction)
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
        } catch (t: Throwable) {
            ExceptionLogStore.recordServiceException(
                this,
                "onStartCommand",
                t,
                mapOf("action" to intent?.action)
            )
            throw t
        }
    }

    override fun onServiceConnected() {
        var pendingActionForLog: String? = null
        try {
            ExceptionLogStore.beginAccessibilityTrace(this)
            appendAccessibilityTrace("进入 onServiceConnected：${buildAccessibilityStatusSummary()}")
            gestureDispatcher = GestureDispatcher(this, uiManager, handler)
            super.onServiceConnected()
            if (!OpenCVLoader.initDebug()) {
                Toast.makeText(this, "OpenCV 初始化失败", Toast.LENGTH_LONG).show()
                appendAccessibilityTrace("OpenCV 初始化失败")
            } else {
                Log.d("GameAssist", "OpenCV 初始化成功")
                appendAccessibilityTrace("OpenCV 初始化成功")
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
            appendAccessibilityTrace("RecordEngine 初始化完成")
            combatEngine = CombatEngine(
                accessibilityService = this,
                serviceScope = serviceScope,
                coordinateManager = coordinateManager,
                gestureDispatcher = gestureDispatcher,
                getConfig = { appConfig },
                shouldRunAutoSelectBeforeStageBattle = {
                    isAutoSelectAgentEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        selectedAgents.all { it.isNotBlank() }
                },
                runAutoSelectBeforeStageBattle = {
                    runAutoSelectAgentForStageNavigation()
                },
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
            appendAccessibilityTrace("CombatEngine 初始化完成")
            reloadGlobalConfig()
            if (recordEngine.recordData.isEmpty()) {
                recordEngine.recordData.add(TurnData(1, currentStep = 1))
            }

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val pendingAction = prefs.getString("pending_start_action", null)
            pendingActionForLog = pendingAction
            prefs.edit().remove("pending_start_action").apply()
            appendAccessibilityTrace("待执行动作=$pendingAction")

            if (pendingAction == "ACTION_START_BIRD_FOOD") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                DailyBirdFoodBridge.pendingConfig?.let { dailyWindowManager?.submitBirdFoodConfig(it) }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("鸟食悬浮窗显示完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_MAINLINE_624") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                DailyMainline624Bridge.pendingConfig?.let { dailyWindowManager?.submitMainline624Config(it) }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("6-24 悬浮窗显示完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_STARGAZING") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                StargazingBridge.pendingConfig?.let { dailyWindowManager?.submitStargazingConfig(it) }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("无月卡观星悬浮窗显示完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_PI_JING_ZHAN_JI") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                PiJingZhanJiBridge.pendingConfig?.let { dailyWindowManager?.submitPiJingZhanJiConfig(it) }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("披荆斩棘悬浮窗显示完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_INVENTORY_STITCH") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val stoneType = prefs
                    .getString(DailyInventoryStitchFragment.KEY_PENDING_STONE_TYPE, MyStoneStore.TYPE_MAIN)
                    ?: MyStoneStore.TYPE_MAIN
                val archiveId = prefs
                    .getString(DailyInventoryStitchFragment.KEY_PENDING_STONE_ARCHIVE_ID, MyStoneStore.getSelectedArchiveId(this))
                dailyWindowManager?.prepareInventoryStitching(stoneType, archiveId)
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("星石拼图悬浮窗准备完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_CHARACTER_IMPORT") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                CharacterImportBridge.pendingConfig?.let { dailyWindowManager?.submitCharacterImportConfig(it) }
                dailyWindowManager?.showWindow()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("角色导入悬浮窗准备完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_BOX_OCR") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                dailyWindowManager?.startBoxOcrMode()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("框选OCR悬浮窗准备完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_COORDINATE_PICKER") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                dailyWindowManager?.startCoordinatePickerMode()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("坐标拾取器悬浮窗准备完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction == "ACTION_START_DAILY_SCRIPT_RECORDER") {
                if (dailyWindowManager == null) dailyWindowManager = DailyWindowManager(this)
                dailyWindowManager?.startScriptRecorderMode()
                updateOverlayStatePrefs(combatOpen = false, dailyOpen = true)
                appendAccessibilityTrace("脚本录制器悬浮窗准备完成：${buildWindowVisibilitySummary()}")
            } else if (pendingAction != null) {
                appendAccessibilityTrace("准备显示战斗悬浮窗")
                showControlWindow()
                updateOverlayStatePrefs(combatOpen = true, dailyOpen = false)
                updateTableData()
                applyPendingCombatScriptExportIfNeeded()
                appendAccessibilityTrace("战斗悬浮窗显示完成：${buildWindowVisibilitySummary()}")
            }
            scheduleAccessibilityStateCheck("连接后300ms", 300L, pendingActionForLog)
            scheduleAccessibilityStateCheck("连接后1500ms", 1500L, pendingActionForLog)
            autoTaskEngine = AutoTaskEngine(this)
        } catch (t: Throwable) {
            ExceptionLogStore.recordServiceException(
                this,
                "onServiceConnected",
                t,
                mapOf("pending_action" to pendingActionForLog)
            )
            throw t
        }
    }

    override fun onDestroy() {
        appendAccessibilityTrace("onDestroy：${buildAccessibilityStatusSummary()}，${buildWindowVisibilitySummary()}")
        super.onDestroy()
        dailyWindowManager?.release()
        dailyWindowManager = null
        updateOverlayStatePrefs(combatOpen = false, dailyOpen = false)
        stopCombatAnchorPicker()
        autoTaskEngine?.release()
        if (this::combatEngine.isInitialized) {
            combatEngine.stop()
        }
        instance = null
        serviceScope.cancel()
        ExceptionLogStore.clearActiveTrace(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        appendAccessibilityTrace("onInterrupt：${buildAccessibilityStatusSummary()}，${buildWindowVisibilitySummary()}")
    }

    private var recordSwipeThreshold: Int = 50
    private var recordInputHeightRatio: Int = 31
    private fun reloadGlobalConfig() {
        appConfig = ConfigManager.getAllConfig(this)
        recordSwipeThreshold = appConfig.swipeThreshold
        if (recordInputHeightRatio != appConfig.inputHeightRatio) {
            recordInputHeightRatio = appConfig.inputHeightRatio
        }
        updateCombatCircleButtonPosition()
    }
    private fun startImagePicker() {
        val intent = Intent(this, ImagePickerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    // Recognize the imported combat table locally, then let the user review before importing.
    fun processOcrImage(bitmap: Bitmap) {
        if (isOcrProcessing) {
            Toast.makeText(this, "OCR 正在处理中", Toast.LENGTH_SHORT).show()
            return
        }

        isOcrProcessing = true
        Toast.makeText(this, "正在本地识别表格...", Toast.LENGTH_SHORT).show()
        uiManager.controlView?.findViewById<View>(R.id.layout_ocr_loading)?.visibility = View.VISIBLE

        serviceScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                delay(180L)
                val result = withContext(Dispatchers.Default) {
                    TableOcrEngine.recognizeWithPreview(this@YuanAssistService, bitmap)
                }
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < 420L) delay(420L - elapsed)
                isOcrProcessing = false
                uiManager.controlView?.findViewById<View>(R.id.layout_ocr_loading)?.visibility = View.GONE
                showLocalTableOcrReviewDialog(bitmap, result)
            } catch (t: Throwable) {
                isOcrProcessing = false
                uiManager.controlView?.findViewById<View>(R.id.layout_ocr_loading)?.visibility = View.GONE
                Toast.makeText(this@YuanAssistService, "本地识别失败：${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Upload image to OCR backend and import the parsed result.
    private fun processOnlineOcrImage(bitmap: Bitmap) {
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

        val themeContext = DialogUtils.getThemeContext(this)
        StyledDialogUi.showOptionDialog(
            context = themeContext,
            title = "选择导入来源",
            options = options.toList()
        ) { which ->
                when (which) {
                    0 -> showTextImportDialog()
                    1 -> startImagePicker()
                    2 -> importFromRecordData()
                    3 -> showLocalScriptImportDialog()
                }
        }
    }
    // Import JSON script from the local script library
    private fun showLocalScriptImportDialog() {
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

        if (files.isEmpty()) {
            Toast.makeText(this, "脚本库为空", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name.replace(".json", "") }
        val themeContext = DialogUtils.getThemeContext(this)

        StyledDialogUi.showOptionDialog(
            context = themeContext,
            title = "选择脚本",
            options = fileNames
        ) { which ->
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
                            ins.toScriptInstructionOrNull()?.let { combatEngine.instructionList.add(it) }
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
        }
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
            dest.remark = src.remark
            dest.instructions.addAll(src.instructions.map { it.copy() })
            for (i in 0 until 5) {
                val srcText = src.characterActions[i]
                if (srcText.isNotEmpty()) {
                    dest.characterActions[i] = SpannableStringBuilder(srcText)
                }
            }
            combatEngine.followData.add(dest)
        }
        combatEngine.instructionList.clear()
        combatEngine.instructionList.addAll(collectRecordModeInstructions())
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
        val parsedTurns = parseScriptTextToTurns(text)
        combatEngine.followData.clear()
        combatEngine.followData.addAll(parsedTurns)
        handler.post {
            updateTableData()
            Toast.makeText(this, "已导入 ${parsedTurns.size} 行", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseScriptTextToTurns(text: String): List<TurnData> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<TurnData>()
        val lines = text.split("\n")
        for (line in lines) {
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) continue
            val parts = if (rawLine.contains("\t")) rawLine.split("\t") else rawLine.trim().split(Regex("\\s+"))
            val firstPart = parts.firstOrNull()?.trim().orEmpty()
            val startIndex = if (firstPart.contains("\u56DE") || firstPart.all { it.isDigit() }) 1 else 0
            val turn = TurnData(result.size + 1)
            var charIdx = 0
            var maxStep = 0
            for (i in startIndex until parts.size) {
                if (charIdx >= 5) break
                val cmd = parts[i].trim()
                if (cmd != "-") {
                    val normalized = normalizeCommand(cmd)
                    turn.characterActions[charIdx] = normalized
                    val stepNumbers = Regex("""\d+(?=[A↑↓圈])""").findAll(normalized)
                        .mapNotNull { it.value.toIntOrNull() }
                        .toList()
                    val lineMaxStep = if (stepNumbers.isEmpty()) 0 else stepNumbers.maxOrNull() ?: 0
                    if (lineMaxStep > maxStep) {
                        maxStep = lineMaxStep
                    }
                }
                charIdx++
            }
            turn.currentStep = if (maxStep > 0) maxStep + 1 else 1
            result.add(turn)
        }
        return result
    }

    private fun applyPendingCombatScriptExportIfNeeded() {
        val pending = CombatScriptExportBridge.pendingRequest ?: return
        CombatScriptExportBridge.pendingRequest = null

        val scriptObj = runCatching {
            Gson().fromJson(pending.scriptJson, LocalScriptJson::class.java)
        }.getOrElse { error ->
            Toast.makeText(this, "脚本解析失败：${error.message}", Toast.LENGTH_SHORT).show()
            return
        }

        prepareForCombatScriptImport()
        applyScriptConfig(scriptObj)
        applyScriptInstructions(scriptObj)
        val turns = parseScriptTextToTurns(scriptObj.scriptContent)
        populateRecordData(turns)
        populateFollowData(turns)

        when (pending.targetMode) {
            "record" -> {
                if (isFollowMode) {
                    isFollowMode = false
                }
                lastRecordedInfo = "已导入 ${scriptObj.title ?: "战斗脚本"}"
                currentDisplayData.forEach { it.isExecuting = false }
                uiManager.removeMinimizedWindow()
                showControlWindow()
                updateTableData()
                refreshActionButtonUI()
                Toast.makeText(this, "已导出到录制悬浮窗", Toast.LENGTH_SHORT).show()
            }

            else -> {
                if (!isFollowMode) {
                    isFollowMode = true
                }
                combatEngine.followData.forEach { it.isExecuting = false }
                uiManager.removeMinimizedWindow()
                showControlWindow()
                updateTableData()
                refreshActionButtonUI()
                Toast.makeText(this, "已导出到跟打悬浮窗", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateRecordData(turns: List<TurnData>) {
        recordEngine.recordData.clear()
        if (turns.isEmpty()) {
            recordEngine.recordData.add(TurnData(1, currentStep = 1))
            return
        }

        turns.forEach { src ->
            val dest = TurnData(src.turnNumber, currentStep = src.currentStep)
            dest.remark = src.remark
            for (i in 0 until 5) {
                dest.characterActions[i] = SpannableStringBuilder(src.characterActions[i])
            }
            recordEngine.recordData.add(dest)
        }
        assignInstructionsToTurns(recordEngine.recordData, combatEngine.instructionList)
    }

    private fun populateFollowData(turns: List<TurnData>) {
        combatEngine.followData.clear()
        turns.forEach { src ->
            val dest = TurnData(src.turnNumber, currentStep = src.currentStep)
            dest.remark = src.remark
            for (i in 0 until 5) {
                dest.characterActions[i] = SpannableStringBuilder(src.characterActions[i])
            }
            combatEngine.followData.add(dest)
        }
        assignInstructionsToTurns(combatEngine.followData, combatEngine.instructionList)
    }

    private fun prepareForCombatScriptImport() {
        isRunning = false
        autoTaskEngine?.stop()
        if (this::combatEngine.isInitialized) {
            combatEngine.stop()
            combatEngine.followData.forEach { it.isExecuting = false }
        }
        removeInputWindow()
    }

    private fun applyScriptConfig(scriptObj: LocalScriptJson) {
        val newConfig = ConfigManager.getAllConfig(this).copy(
            intervalAttack = scriptObj.config.intervalAttack,
            intervalSkill = scriptObj.config.intervalSkill,
            waitTurn = scriptObj.config.waitTurn
        )
        ConfigManager.saveSettings(this, newConfig)
        reloadGlobalConfig()
    }

    private fun applyScriptInstructions(scriptObj: LocalScriptJson) {
        combatEngine.instructionList.clear()
        scriptObj.instructions?.forEach { ins ->
            ins.toScriptInstructionOrNull()?.let { combatEngine.instructionList.add(it) }
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
    }

    private fun assignInstructionsToTurns(turns: List<TurnData>, instructions: List<ScriptInstruction>) {
        turns.forEach { it.instructions.clear() }
        instructions
            .sortedWith(compareBy({ it.turn }, { it.step }, { it.type.name }))
            .forEach { instruction ->
                turns.find { it.turnNumber == instruction.turn }
                    ?.instructions
                    ?.add(instruction.copy())
            }
    }

    private fun collectRecordModeInstructions(): List<ScriptInstruction> {
        return recordEngine.recordData
            .asSequence()
            .flatMap { turnData ->
                turnData.instructions.asSequence().map { it.copy(turn = turnData.turnNumber).normalized() }
            }
            .sortedWith(compareBy({ it.turn }, { it.step }, { it.type.name }))
            .toList()
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
    private fun showAutoSelectAgentDialog() {
        com.example.yuanassist.ui.dialogs.AutoSelectDialog.show(
            context = this,
            isCurrentlyEnabled = isAutoSelectAgentEnabled,
            currentAgents = selectedAgents,
            allAgentsLibrary = AgentRepository.ALL_AGENTS
        ) { isEnabled, newAgents ->
            setAutoSelectAgentEnabled(isEnabled)
            for (i in 0 until 5) {
                selectedAgents[i] = newAgents[i]
            }
            Toast.makeText(this, "自动选人设置已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setAutoSelectAgentEnabled(enabled: Boolean) {
        isAutoSelectAgentEnabled = enabled
        uiManager.controlView?.findViewById<TextView>(R.id.btn_feedback_update)?.text =
            if (enabled) "自动选人：开启" else "自动选人：关闭"
    }

    private suspend fun runAutoSelectAgentForStageNavigation(): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                RunLogger.e("自动导航中的自动选人仅支持 Android 11 及以上")
                handler.post {
                    Toast.makeText(this, "自动选人需要 Android 11 及以上版本", Toast.LENGTH_SHORT).show()
                }
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            if (!isAutoSelectAgentEnabled || selectedAgents.any { it.isBlank() }) {
                RunLogger.e("自动导航中的自动选人配置无效")
                handler.post {
                    Toast.makeText(this, "自动选人未开启或角色未填满", Toast.LENGTH_SHORT).show()
                }
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val engine = autoTaskEngine
            if (engine == null) {
                RunLogger.e("自动选人引擎未初始化")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            RunLogger.i("=== 启动流程：自动导航 -> 自动选人 -> 跟打 ===")
            handler.post {
                Toast.makeText(this, "开始自动选人...", Toast.LENGTH_SHORT).show()
            }

            val plan = AutoSelectScriptBuilder.buildPlan(selectedAgents, AgentRepository.AGENT_MAP)
            engine.startPlan(plan, onCompleted = { success, errorMsg ->
                handler.post {
                    if (!continuation.isActive) return@post
                    if (success) {
                        RunLogger.i("自动选人完成，已自动关闭")
                        setAutoSelectAgentEnabled(false)
                        continuation.resume(true)
                    } else {
                        RunLogger.e("自动选人中断：$errorMsg")
                        Toast.makeText(
                            this@YuanAssistService,
                            "自动选人中断：$errorMsg",
                            Toast.LENGTH_LONG
                        ).show()
                        continuation.resume(false)
                    }
                }
            })
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
                val hasStageAutoNavigation = combatEngine.hasStageAutoNavigationInstruction()
                if (isFollowMode && isAutoSelectAgentEnabled && !hasStageAutoNavigation) {
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

                                setAutoSelectAgentEnabled(false)

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
                    RunLogger.i(
                        if (hasStageAutoNavigation && isAutoSelectAgentEnabled) {
                            "=== 启动流程：自动导航 -> 自动选人 -> 跟打 ==="
                        } else {
                            "=== 启动流程：直接开始跟打 ==="
                        }
                    )
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
        startCombatAnchorPicker()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCombatAnchorPicker() {
        stopCombatAnchorPicker()

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        val hintView = TextView(this).apply {
            text = "拖动动作标记修正坐标，只保存上下位置\n点击空白处关闭"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        overlay.addView(
            hintView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(28)
            }
        )

        combatAnchorAdjustSpecs().forEach { spec ->
            overlay.addView(
                createCombatAnchorAdjustMarker(spec),
                createCombatAnchorAdjustMarkerParams(spec)
            )
        }

        overlay.setOnClickListener {
            stopCombatAnchorPicker()
        }
        overlay.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        combatAnchorPickerView = overlay
        systemWindowManager.addView(overlay, params)
        Toast.makeText(this, "键位修正已开启，拖动标记保存", Toast.LENGTH_SHORT).show()
    }

    private fun combatAnchorAdjustSpecs(): List<CombatAnchorAdjustSpec> = listOf(
        CombatAnchorAdjustSpec("A", 0, "#C0392B") { appConfig.attackYFromBottom },
        CombatAnchorAdjustSpec("↑", 1, "#2E7D32") { appConfig.upYFromBottom },
        CombatAnchorAdjustSpec("↓", 2, "#1565C0") { appConfig.downYFromBottom },
        CombatAnchorAdjustSpec("圈", 3, "#D68A93") { appConfig.circleYFromBottom }
    )

    private fun createCombatAnchorAdjustMarkerParams(spec: CombatAnchorAdjustSpec): FrameLayout.LayoutParams {
        val sizePx = dp(42)
        val point = coordinateManager.getActionCoordinates(spec.slotIndex, spec.yFromBottom())
        return FrameLayout.LayoutParams(sizePx, sizePx).apply {
            leftMargin = (point.x - sizePx / 2f).roundToInt()
            topMargin = (point.y - sizePx / 2f).roundToInt()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCombatAnchorAdjustMarker(spec: CombatAnchorAdjustSpec): TextView {
        val sizePx = dp(42)
        return TextView(this).apply {
            text = spec.type
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(spec.color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFF8F2"))
                setStroke(dp(2), Color.parseColor(spec.color))
            }
            elevation = 12f * resources.displayMetrics.density
            setOnTouchListener(object : View.OnTouchListener {
                private val touchSlop = 8f * resources.displayMetrics.density
                private var initialTop = 0
                private var initialTouchY = 0f
                private var moved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val params = v.layoutParams as? FrameLayout.LayoutParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            pendingCombatAnchorType = spec.type
                            initialTop = params.topMargin
                            initialTouchY = event.rawY
                            moved = false
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dy = event.rawY - initialTouchY
                            if (kotlin.math.abs(dy) > touchSlop) {
                                moved = true
                            }
                            val maxTop = (coordinateManager.screenHeight - sizePx).coerceAtLeast(0)
                            params.topMargin = (initialTop + dy.toInt()).coerceIn(0, maxTop)
                            v.layoutParams = params
                            return true
                        }

                        MotionEvent.ACTION_UP -> {
                            val centerY = getViewCenterOnScreen(v, null).second
                            saveCombatAnchor(spec.type, centerY)
                            if (!moved) {
                                Toast.makeText(
                                    this@YuanAssistService,
                                    "上下拖动 ${spec.type} 标记即可修正",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingCombatAnchorType = null
                            return true
                        }
                    }
                    return false
                }
            })
        }
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
        ServiceDialogs.showInsertTurnDialog(this) { targetTurn, copyTurn ->
            insertTurnAfter(targetTurn, copyTurn)
        }
    }

    private fun insertTurnAfter(targetTurnNumber: Int, copyTurnNumber: Int? = null) {
        if (combatEngine.followData.isEmpty()) {
            Toast.makeText(this, "列表为空", Toast.LENGTH_SHORT).show()
            return
        }

        val index = combatEngine.followData.indexOfFirst { it.turnNumber == targetTurnNumber }
        if (index != -1) {
            val copyActions = copyTurnNumber?.let { number ->
                val sourceTurn = combatEngine.followData.firstOrNull { it.turnNumber == number }
                if (sourceTurn == null) {
                    Toast.makeText(this, "未找到要复制的回合 T$number", Toast.LENGTH_SHORT).show()
                    return
                }
                sourceTurn.characterActions.copyOf()
            }

            for (i in index + 1 until combatEngine.followData.size) {
                combatEngine.followData[i].turnNumber += 1
            }

            combatEngine.instructionList.forEach { if (it.turn > targetTurnNumber) it.turn += 1 }
            val newTurn = TurnData(
                turnNumber = targetTurnNumber + 1,
                characterActions = copyActions ?: Array(5) { "" }
            )
            combatEngine.followData.add(index + 1, newTurn)

            tableAdapter?.notifyDataSetChanged()
            val toastText = if (copyTurnNumber == null) {
                "已在 T$targetTurnNumber 后插入空回合"
            } else {
                "已在 T$targetTurnNumber 后插入并复制 T$copyTurnNumber 操作"
            }
            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()

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
        ServiceDialogs.showExportImageSettingsDialog(this) { settings ->
            com.example.yuanassist.utils.ImageExportUtils.generateAndSaveImage(
                this,
                currentDisplayData,
                settings.headers,
                settings.gameTitle,
                settings.subtitle
            )
        }
    }

    // Show export mode dialog
    private fun showExportModeSelectionDialog() {
        val options = arrayOf("导出表格图片", "导出到脚本库")
        StyledDialogUi.showOptionDialog(
            context = DialogUtils.getThemeContext(this),
            title = "选择导出方式",
            options = options.toList()
        ) { which ->
            if (which == 0) showExportDialog() else showSaveToLibraryDialog()
        }
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
            val insJsonList = getCurrentModeScriptInstructions().map { it.toInstructionJson() }
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
        setAutoSelectAgentEnabled(isAutoSelectAgentEnabled)
        btnAutoSelect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showAutoSelectAgentDialog()
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
        showCombatCircleButton()
        showCombatTargetSwitchButtons()
    }
    private fun removeInputWindow() {
        uiManager.removeInputWindow()
        removeCombatCircleButton()
        removeCombatTargetSwitchButtons()
        isSimulating = false
    }

    private fun minimizeControlWindow(autoDockToTopLeft: Boolean = false) {
        uiManager.removeControlWindow()
        showMinimizedWindow()
        if (autoDockToTopLeft) {
            uiManager.moveMinimizedWindowNearTopSafely()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showCombatCircleButton() {
        if (combatCircleButtonView != null || isFollowMode || !isRunning) return

        val sizePx = (40f * resources.displayMetrics.density).roundToInt()
        val view = TextView(this).apply {
            text = "圈"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D68A93"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FDF7F8"))
                setStroke((2f * resources.displayMetrics.density).roundToInt(), Color.parseColor("#D68A93"))
            }
            elevation = 12f * resources.displayMetrics.density
            setOnClickListener {
                if (isFollowMode) return@setOnClickListener
                if (!isRunning) {
                    Toast.makeText(this@YuanAssistService, "请先开始录制", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                setCombatCircleButtonTouchable(false)
                scheduleCombatCircleButtonRestore(appConfig.recordDelay + 1200L)
                val centerPoint = getCombatCircleButtonCenterOnScreen()
                val recorded = recordEngine.recordCircleAction(
                    charIndex = combatCircleSlotIndex,
                    clickX = centerPoint.first,
                    clickY = centerPoint.second,
                    isSimulating = isSimulating,
                    isFollowMode = isFollowMode,
                    onActionDone = { restoreCombatCircleButtonTouchability() }
                )
                if (!recorded) {
                    restoreCombatCircleButtonTouchability()
                }
            }
        }

        val point = coordinateManager.getActionCoordinates(combatCircleSlotIndex, appConfig.circleYFromBottom)
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (point.x - sizePx / 2f).roundToInt()
            y = (point.y - sizePx / 2f).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        view.setOnTouchListener(object : View.OnTouchListener {
            private val touchSlop = 12f * resources.displayMetrics.density
            private var initialX = 0
            private var initialTouchX = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val layoutParams = v.layoutParams as? WindowManager.LayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialTouchX = event.rawX
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        if (kotlin.math.abs(dx) > touchSlop) {
                            moved = true
                        }
                        layoutParams.x = initialX + dx.toInt()
                        systemWindowManager.updateViewLayout(v, layoutParams)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        combatCircleSlotIndex = findNearestCombatCircleSlotIndex(layoutParams.x + v.width / 2f)
                        updateCombatCircleButtonPosition()
                        if (!moved) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        combatCircleButtonView = view
        systemWindowManager.addView(view, params)
    }

    private fun removeCombatCircleButton() {
        combatCircleButtonRestoreRunnable?.let(handler::removeCallbacks)
        combatCircleButtonRestoreRunnable = null
        combatCircleButtonView?.let { view ->
            try {
                systemWindowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                combatCircleButtonView = null
            }
        }
    }

    private fun showCombatTargetSwitchButtons() {
        showCombatTargetSwitchButton(
            type = InstructionType.TARGET_SWITCH_LEFT,
            text = "左"
        )
        showCombatTargetSwitchButton(
            type = InstructionType.TARGET_SWITCH_RIGHT,
            text = "右"
        )
    }

    private fun showCombatTargetSwitchButton(type: InstructionType, text: String) {
        if (isFollowMode || !isRunning) return
        if (getCombatTargetSwitchButtonView(type) != null) return

        val sizePx = (40f * resources.displayMetrics.density).roundToInt()
        val point = when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> coordinateManager.getTargetCoordinates(
                GameConstants.DESIGN_TARGET_LEFT_X,
                GameConstants.DESIGN_TARGET_Y_TOP
            )

            else -> coordinateManager.getTargetCoordinates(
                GameConstants.DESIGN_TARGET_X,
                GameConstants.DESIGN_TARGET_Y_TOP
            )
        }
        val view = TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#4A6F8A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F8F4ED"))
                setStroke((2f * resources.displayMetrics.density).roundToInt(), Color.parseColor("#4A6F8A"))
            }
            elevation = 12f * resources.displayMetrics.density
            setOnClickListener { handleCombatTargetSwitchButtonClick(type) }
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (point.x - sizePx / 2f).roundToInt()
            y = (point.y - sizePx / 2f).roundToInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> combatLeftSwitchButtonView = view
            InstructionType.TARGET_SWITCH_RIGHT, InstructionType.TARGET_SWITCH -> combatRightSwitchButtonView = view
            else -> return
        }
        systemWindowManager.addView(view, params)
    }

    private fun handleCombatTargetSwitchButtonClick(type: InstructionType) {
        if (isFollowMode) return
        if (!isRunning) {
            Toast.makeText(this, "请先开始录制", Toast.LENGTH_SHORT).show()
            return
        }

        val buttonView = getCombatTargetSwitchButtonView(type) ?: return
        val fallbackPoint = when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> coordinateManager.getTargetCoordinates(
                GameConstants.DESIGN_TARGET_LEFT_X,
                GameConstants.DESIGN_TARGET_Y_TOP
            )

            else -> coordinateManager.getTargetCoordinates(
                GameConstants.DESIGN_TARGET_X,
                GameConstants.DESIGN_TARGET_Y_TOP
            )
        }
        val centerPoint = getViewCenterOnScreen(buttonView, fallbackPoint.x to fallbackPoint.y)

        setCombatTargetSwitchButtonTouchable(type, false)
        scheduleCombatTargetSwitchButtonRestore(type, appConfig.recordDelay + 1200L)
        gestureDispatcher.performActionPenetrate(
            centerPoint.first,
            centerPoint.second,
            true,
            centerPoint.first,
            centerPoint.second,
            appConfig.recordDelay
        ) {
            val recorded = recordEngine.recordTargetSwitchInstruction(type)
            handler.post {
                restoreCombatTargetSwitchButtonTouchability(type)
                if (!recorded) {
                    Toast.makeText(this, "记录切换目标失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLocalTableOcrReviewDialog(bitmap: Bitmap, result: TableOcrPreviewResult) {
        if (result.rows.isEmpty()) {
            Toast.makeText(this, "本地 OCR 未识别到表格", Toast.LENGTH_LONG).show()
            return
        }

        val themeContext = DialogUtils.getThemeContext(this)
        val root = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_job_station_card)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            minimumWidth = dp(300)
        }
        root.addView(createTableOcrDialogTitle(themeContext, "调整本地识别结果"))
        root.addView(createTableOcrDialogSubtitle(themeContext, "点击动作格后，用下方小键盘修正识别内容"))

        val scroll = ScrollView(themeContext).apply {
            isFillViewport = false
        }
        val content = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val actionEditors = mutableListOf<List<EditText>>()
        var activeEditor: EditText? = null

        result.rows.forEachIndexed { index, row ->
            val group = LinearLayout(themeContext).apply {
                orientation = LinearLayout.VERTICAL
            }
            result.rowImages.getOrNull(index)?.let { rowImage ->
                val imageView = ImageView(themeContext).apply {
                    setImageBitmap(rowImage)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = createTableOcrRoundedBackground("#F8F2E5", "#D8C18A", 8)
                }
                group.addView(
                    imageView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            val editRow = LinearLayout(themeContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, 0)
            }
            val columnWidths = result.rowColumnWidths.getOrNull(index)
                ?.takeIf { it.size >= 6 }
                ?: listOf(1, 1, 1, 1, 1, 1)
            val rowMaxLines = row.actions
                .take(5)
                .map { estimateActionLineCount(it) }
                .maxOrNull()
                ?.coerceIn(1, 4)
                ?: 1
            val resultRowHeight = when (rowMaxLines) {
                1 -> dp(28)
                2 -> dp(44)
                3 -> dp(60)
                else -> dp(76)
            }
            val roundSpacer = Space(themeContext)
            editRow.addView(
                roundSpacer,
                LinearLayout.LayoutParams(0, resultRowHeight, columnWidths[0].toFloat())
            )

            val editors = (0 until 5).map { actionIndex ->
                EditText(themeContext).apply {
                    setSingleLine(false)
                    maxLines = rowMaxLines
                    minLines = 1
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setText(row.actions.getOrNull(actionIndex).orEmpty())
                    setSelectAllOnFocus(false)
                    disableShowSoftInput()
                    setHorizontallyScrolling(false)
                    setTextColor(Color.parseColor("#4E3C1E"))
                    setHintTextColor(Color.parseColor("#9A8A71"))
                    setBackgroundResource(R.drawable.bg_table_ocr_underline)
                    setPadding(dp(4), 0, dp(4), 0)
                    setOnFocusChangeListener { view, hasFocus ->
                        if (hasFocus) {
                            activeEditor = view as? EditText
                            hideSoftInput(view)
                        }
                    }
                    setOnClickListener {
                        activeEditor = this
                        hideSoftInput(this)
                    }
                }.also { editor ->
                    val params = LinearLayout.LayoutParams(
                        0,
                        resultRowHeight,
                        columnWidths[actionIndex + 1].toFloat()
                    )
                    editRow.addView(editor, params)
                }
            }
            actionEditors.add(editors)
            group.addView(
                editRow,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            content.addView(
                group,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(8)
                }
            )
        }

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.50f).roundToInt()
            ).apply {
                topMargin = dp(10)
            }
        )

        val buttonRow = LinearLayout(themeContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val useOnlineButton = createTableOcrDialogButton(themeContext, "使用线上OCR", false)
        val confirmButton = createTableOcrDialogButton(themeContext, "确定", true)
        buttonRow.addView(
            useOnlineButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        buttonRow.addView(
            confirmButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        )
        root.addView(buttonRow)

        val keyboard = buildTableOcrKeyboard(themeContext) { key ->
            val editor = activeEditor ?: actionEditors.firstOrNull()?.firstOrNull()?.also {
                activeEditor = it
                it.requestFocus()
                hideSoftInput(it)
            } ?: return@buildTableOcrKeyboard
            handleTableOcrKeyboardKey(editor, key)
            hideSoftInput(editor)
        }
        root.addView(
            keyboard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        val dialog = AlertDialog.Builder(themeContext)
            .setView(root)
            .create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        useOnlineButton.setOnClickListener {
            dialog.dismiss()
            processOnlineOcrImage(bitmap)
        }
        confirmButton.setOnClickListener {
            val text = buildReviewedTableText(actionEditors)
            dialog.dismiss()
            parseTextToTable(text)
            if (!isFollowMode) {
                Toast.makeText(this, "本地 OCR 已导入，请切换到跟打模式查看。", Toast.LENGTH_LONG).show()
            } else {
                showControlWindow()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95f).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun buildReviewedTableText(actionEditors: List<List<EditText>>): String {
        return actionEditors.mapIndexed { index, editors ->
            buildString {
                append("${index + 1}回合")
                editors.forEach { editor ->
                    append('\t')
                    append(editor.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "-")
                }
            }
        }.joinToString("\n")
    }

    private fun buildTableOcrKeyboard(context: Context, onKey: (String) -> Unit): LinearLayout {
        val keyboard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createTableOcrRoundedBackground("#FFF9EE", "#E1C894", 10)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "删"),
            listOf("6", "7", "8", "9", "0", "清"),
            listOf("A", "圈", "↑", "↓", "←", "→")
        )
        rows.forEach { keys ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            keys.forEach { key ->
                val button = TextView(context).apply {
                    text = key
                    gravity = Gravity.CENTER
                    minWidth = 0
                    minHeight = 0
                    minimumWidth = 0
                    minimumHeight = 0
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(
                        Color.parseColor(
                            when (key) {
                                "删", "清" -> "#B64D3C"
                                "←", "→" -> "#6C5B43"
                                else -> "#6B4E1C"
                            }
                        )
                    )
                    background = createTableOcrKeyBackground(key)
                    setPadding(0, 0, 0, 0)
                    isClickable = true
                    isFocusable = false
                    setOnClickListener { onKey(key) }
                }
                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                        topMargin = dp(2)
                        bottomMargin = dp(2)
                    }
                )
            }
            keyboard.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return keyboard
    }

    private fun createTableOcrDialogTitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#2F261B"))
        }
    }

    private fun createTableOcrDialogSubtitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, dp(6), 0, 0)
        }
    }

    private fun createTableOcrDialogButton(context: Context, text: String, primary: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (primary) "#6B4E1C" else "#8C6C33"))
            setBackgroundResource(if (primary) R.drawable.bg_job_station_chip else R.drawable.bg_job_station_icon_button)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
        }
    }

    private fun createTableOcrKeyBackground(key: String): GradientDrawable {
        return when (key) {
            "A", "圈", "↑", "↓" -> createTableOcrRoundedBackground("#F6D59A", "#C88A2C", 8)
            "删", "清" -> createTableOcrRoundedBackground("#F8EDE8", "#D8A48F", 8)
            else -> createTableOcrRoundedBackground("#F8F2E5", "#D8C18A", 8)
        }
    }

    private fun createTableOcrRoundedBackground(
        fillColor: String,
        strokeColor: String,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun hideSoftInput(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun handleTableOcrKeyboardKey(editor: EditText, key: String) {
        val editable = editor.text ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        val left = minOf(start, end)
        val right = maxOf(start, end)
        when (key) {
            "←" -> editor.setSelection((left - 1).coerceAtLeast(0))
            "→" -> editor.setSelection((right + 1).coerceAtMost(editable.length))
            "删" -> {
                when {
                    right > left -> editable.delete(left, right)
                    left > 0 -> editable.delete(left - 1, left)
                }
            }
            "清" -> editable.clear()
            else -> editable.replace(left, right, key)
        }
    }

    private fun estimateActionLineCount(text: String): Int {
        val compact = text.trim()
        if (compact.length <= 4) return 1
        return ((compact.length + 3) / 4).coerceAtLeast(1)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).roundToInt()

    private fun removeCombatTargetSwitchButtons() {
        combatLeftSwitchButtonRestoreRunnable?.let(handler::removeCallbacks)
        combatRightSwitchButtonRestoreRunnable?.let(handler::removeCallbacks)
        combatLeftSwitchButtonRestoreRunnable = null
        combatRightSwitchButtonRestoreRunnable = null
        combatLeftSwitchButtonView?.let { view ->
            try {
                systemWindowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                combatLeftSwitchButtonView = null
            }
        }
        combatRightSwitchButtonView?.let { view ->
            try {
                systemWindowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                combatRightSwitchButtonView = null
            }
        }
    }

    private fun updateCombatCircleButtonPosition() {
        val view = combatCircleButtonView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val point = coordinateManager.getActionCoordinates(combatCircleSlotIndex, appConfig.circleYFromBottom)
        params.x = (point.x - view.width.coerceAtLeast(params.width) / 2f).roundToInt()
        params.y = (point.y - view.height.coerceAtLeast(params.height) / 2f).roundToInt()
        systemWindowManager.updateViewLayout(view, params)
    }

    private fun findNearestCombatCircleSlotIndex(centerX: Float): Int {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        for (index in 0 until 5) {
            val slotCenterX = coordinateManager.getActionCoordinates(index, appConfig.circleYFromBottom).x
            val distance = kotlin.math.abs(slotCenterX - centerX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun setCombatCircleButtonTouchable(touchable: Boolean) {
        val view = combatCircleButtonView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val nextFlags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (nextFlags != params.flags) {
            params.flags = nextFlags
            systemWindowManager.updateViewLayout(view, params)
        }
        view.alpha = if (touchable) 1f else 0.45f
    }

    private fun scheduleCombatCircleButtonRestore(delayMs: Long) {
        combatCircleButtonRestoreRunnable?.let(handler::removeCallbacks)
        combatCircleButtonRestoreRunnable = Runnable {
            restoreCombatCircleButtonTouchability()
        }
        handler.postDelayed(combatCircleButtonRestoreRunnable!!, delayMs)
    }

    private fun restoreCombatCircleButtonTouchability() {
        combatCircleButtonRestoreRunnable?.let(handler::removeCallbacks)
        combatCircleButtonRestoreRunnable = null
        setCombatCircleButtonTouchable(true)
    }

    private fun getCombatCircleButtonCenterOnScreen(): Pair<Float, Float> {
        val view = combatCircleButtonView
        val fallback = coordinateManager.getActionCoordinates(combatCircleSlotIndex, appConfig.circleYFromBottom)
        if (view == null) {
            return fallback.x to fallback.y
        }
        return getViewCenterOnScreen(view, fallback.x to fallback.y)
    }

    private fun getViewCenterOnScreen(view: View, fallback: Pair<Float, Float>?): Pair<Float, Float> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val width = view.width.takeIf { it > 0 } ?: view.measuredWidth
        val height = view.height.takeIf { it > 0 } ?: view.measuredHeight
        if (width <= 0 || height <= 0) {
            return fallback ?: (0f to 0f)
        }
        return (location[0] + width / 2f) to (location[1] + height / 2f)
    }

    private fun getCombatTargetSwitchButtonView(type: InstructionType): TextView? {
        return when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> combatLeftSwitchButtonView
            InstructionType.TARGET_SWITCH_RIGHT, InstructionType.TARGET_SWITCH -> combatRightSwitchButtonView
            else -> null
        }
    }

    private fun setCombatTargetSwitchButtonTouchable(type: InstructionType, touchable: Boolean) {
        val view = getCombatTargetSwitchButtonView(type) ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val nextFlags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (nextFlags != params.flags) {
            params.flags = nextFlags
            systemWindowManager.updateViewLayout(view, params)
        }
        view.alpha = if (touchable) 1f else 0.45f
    }

    private fun scheduleCombatTargetSwitchButtonRestore(type: InstructionType, delayMs: Long) {
        val oldRunnable = when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> combatLeftSwitchButtonRestoreRunnable
            else -> combatRightSwitchButtonRestoreRunnable
        }
        oldRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { restoreCombatTargetSwitchButtonTouchability(type) }
        when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> combatLeftSwitchButtonRestoreRunnable = runnable
            else -> combatRightSwitchButtonRestoreRunnable = runnable
        }
        handler.postDelayed(runnable, delayMs)
    }

    private fun restoreCombatTargetSwitchButtonTouchability(type: InstructionType) {
        when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> {
                combatLeftSwitchButtonRestoreRunnable?.let(handler::removeCallbacks)
                combatLeftSwitchButtonRestoreRunnable = null
            }

            else -> {
                combatRightSwitchButtonRestoreRunnable?.let(handler::removeCallbacks)
                combatRightSwitchButtonRestoreRunnable = null
            }
        }
        setCombatTargetSwitchButtonTouchable(type, true)
    }

    private fun getCurrentModeScriptInstructions(): List<ScriptInstruction> {
        return if (isFollowMode) {
            combatEngine.instructionList.map { it.copy() }
        } else {
            collectRecordModeInstructions()
        }
    }

    fun getExportableInstructions(): List<InstructionJson> {
        if (!::combatEngine.isInitialized && isFollowMode) return emptyList()
        return getCurrentModeScriptInstructions().map { it.toInstructionJson() }
    }
}

