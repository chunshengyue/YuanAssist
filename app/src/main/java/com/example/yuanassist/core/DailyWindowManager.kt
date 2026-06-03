package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.CharacterImportConfig
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.model.StargazingConfig
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.ui.CharacterImportReviewActivity
import com.example.yuanassist.ui.MainActivity
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.StoneOcrCoordinator
import com.example.yuanassist.utils.StoneOcrMode
import com.example.yuanassist.ui.MyStoneActivity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DailyWindowManager(private val service: AccessibilityService) {

    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val PREFS_APP = "app_prefs"
        private const val FLOAT_WINDOW_EDGE_MARGIN_DP = 12
        private const val FLOAT_WINDOW_TOP_MARGIN_DP = 20
        private const val BOX_OCR_MIN_SIZE_DP = 120
        private const val BOX_OCR_ACTION_SPACING_DP = 16
        private const val BOX_OCR_HANDLE_SIZE_DP = 28
        private const val BOX_OCR_STROKE_DP = 2
        private const val BOX_OCR_CLICK_DURATION_MS = 80L
    }

    private enum class DailyMode {
        TASK_PLAN,
        BIRD_FOOD,
        MAINLINE_624,
        STARGAZING,
        CHARACTER_IMPORT,
        INVENTORY_STITCH,
        BOX_OCR,
    }

    private enum class BoxHandle {
        MOVE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private data class NormalizedSelectionRect(
        val leftRatio: Float,
        val topRatio: Float,
        val widthRatio: Float,
        val heightRatio: Float,
    )

    private val engine = AutoTaskEngine(service)
    private val birdFoodRuntimeManager = BirdFoodRuntimeManager(service) { isRunning ->
        if (isRunning) {
            moveWindowToTopLeftSafely()
        }
        refreshActionButton()
    }
    private val mainline624RuntimeManager = Mainline624RuntimeManager(service) { isRunning ->
        if (isRunning) {
            moveWindowToTopLeftSafely()
        }
        refreshActionButton()
    }
    private val stargazingRuntimeManager = StargazingRuntimeManager(service) { isRunning ->
        if (isRunning) {
            moveWindowToTopLeftSafely()
        }
        refreshActionButton()
    }
    private val stitchEngine = InventoryStitchEngine(service)
    private val characterImportEngine = CharacterImportEngine(service)
    private val ailaoStatusBarManager = AilaoStatusBarManager(service)
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val scriptRecorderManager = DailyScriptRecorderManager(
        service = service,
        onScriptSaved = { scriptName, plan, templateDir ->
            submitTaskPlan(plan, scriptName, templateDir)
            showWindow()
        },
        onVisibilityChanged = { isVisible ->
            updateOverlayState(isOpen = isVisible || floatView != null)
        }
    )
    private val deviceId: String by lazy {
        Settings.Secure.getString(service.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private var floatView: View? = null
    private var coordinatePickerView: View? = null
    private var currentTaskPlan: DailyTaskPlan? = null
    private var currentScriptName: String? = null
    private var currentTemplateDir: File? = null
    private var currentBirdFoodConfig: BirdFoodConfig? = null
    private var currentMainline624Config: Mainline624Config? = null
    private var currentStargazingConfig: StargazingConfig? = null
    private var currentCharacterImportConfig: CharacterImportConfig? = null
    private var inventoryStitchPrepared = false
    private var inventoryStitchArchiveId = MyStoneStore.DEFAULT_ARCHIVE_ID
    private var inventoryStitchType = MyStoneStore.TYPE_MAIN
    private var currentMode: DailyMode? = null
    private var isStoneOcrProcessing = false
    private var isBoxOcrProcessing = false
    private var lastWindowX = 100
    private var lastWindowY = 100
    private var boxOcrOverlayView: FrameLayout? = null

    fun showWindow() {
        if (floatView != null) {
            refreshActionButton()
            updateOverlayState(isOpen = true)
            return
        }

        val density = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            (50f * density + 0.5f).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastWindowX
            y = lastWindowY
        }

        val view = LayoutInflater.from(service).inflate(R.layout.layout_daily_window, null)
        floatView = view
        val dragHandle = view.findViewById<ImageView>(R.id.iv_daily_drag_handle)
        val actionButton = view.findViewById<ImageButton>(R.id.btn_daily_action)
        val closeButton = view.findViewById<TextView>(R.id.btn_daily_close)
        dragHandle.setOnTouchListener(createDragListener(params, view))
        dragHandle.setOnClickListener { openDailyPage() }
        actionButton.setOnClickListener { toggleExecution() }
        closeButton.setOnClickListener { hideWindow() }
        refreshActionButton()
        windowManager.addView(view, params)
        updateOverlayState(isOpen = true)
    }

    fun hideWindow() {
        stopCurrentWork()
        removeWindow()
        updateOverlayState(isOpen = false)
    }

    fun release() {
        stopCurrentWork()
        removeWindow()
        handler.removeCallbacksAndMessages(null)
        uiScope.cancel()
        updateOverlayState(isOpen = false)
    }

    fun isWindowVisible(): Boolean =
        floatView != null || scriptRecorderManager.isVisible

    fun submitTaskPlan(plan: DailyTaskPlan, scriptName: String, templateDir: File? = null) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        inventoryStitchPrepared = false
        currentTaskPlan = plan
        currentScriptName = scriptName
        currentTemplateDir = templateDir
        currentMode = DailyMode.TASK_PLAN
        showWindow()
        refreshActionButton()
    }

    fun submitTaskPlanJson(fileName: String, jsonContent: String): Result<Unit> {
        return runCatching {
            val plan = gson.fromJson(jsonContent, DailyTaskPlan::class.java)
            submitTaskPlan(plan, fileName)
        }
    }

    fun submitTaskPlanJson(
        fileName: String,
        jsonContent: String,
        templateDirPath: String?
    ): Result<Unit> {
        return runCatching {
            val plan = gson.fromJson(jsonContent, DailyTaskPlan::class.java)
            val templateDir = templateDirPath
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.isDirectory }
            submitTaskPlan(plan, fileName, templateDir)
        }
    }

    fun submitBirdFoodConfig(config: BirdFoodConfig) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        inventoryStitchPrepared = false
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        currentBirdFoodConfig = config
        currentMode = DailyMode.BIRD_FOOD
        birdFoodRuntimeManager.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun submitMainline624Config(config: Mainline624Config) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        inventoryStitchPrepared = false
        currentBirdFoodConfig = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        currentMainline624Config = config
        currentMode = DailyMode.MAINLINE_624
        mainline624RuntimeManager.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun submitStargazingConfig(config: StargazingConfig) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        inventoryStitchPrepared = false
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentCharacterImportConfig = null
        currentStargazingConfig = config
        currentMode = DailyMode.STARGAZING
        stargazingRuntimeManager.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun submitCharacterImportConfig(config: CharacterImportConfig) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        inventoryStitchPrepared = false
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = config
        currentMode = DailyMode.CHARACTER_IMPORT
        characterImportEngine.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun startBoxOcrMode() {
        if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning || stargazingRuntimeManager.isRunning || stitchEngine.isRunning || characterImportEngine.isRunning || isBoxOcrProcessing) {
            Toast.makeText(service, "请先停止当前日常任务", Toast.LENGTH_SHORT).show()
            return
        }
        ailaoStatusBarManager.hide()
        stopCoordinatePicker()
        stopBoxOcrOverlay()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        inventoryStitchPrepared = false
        currentMode = DailyMode.BOX_OCR
        showWindow()
        refreshActionButton()
    }

    fun startCoordinatePickerMode() {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        inventoryStitchPrepared = false
        currentMode = null
        showWindow()
        startCoordinatePicker()
    }

    fun startScriptRecorderMode() {
        if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning || stargazingRuntimeManager.isRunning || stitchEngine.isRunning || characterImportEngine.isRunning) {
            Toast.makeText(service, "请先停止当前日常任务", Toast.LENGTH_SHORT).show()
            return
        }
        ailaoStatusBarManager.hide()
        stopCoordinatePicker()
        scriptRecorderManager.stop()
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        inventoryStitchPrepared = false
        currentMode = null
        removeWindow()
        scriptRecorderManager.show()
        updateOverlayState(isOpen = true)
    }

    fun prepareInventoryStitching(stoneType: String, archiveId: String? = null) {
        ailaoStatusBarManager.hide()
        scriptRecorderManager.stop()
        showWindow()
        if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning || stargazingRuntimeManager.isRunning) {
            Toast.makeText(service, "请先停止当前日常任务", Toast.LENGTH_SHORT).show()
            return
        }
        currentTaskPlan = null
        currentScriptName = null
        currentTemplateDir = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentStargazingConfig = null
        currentCharacterImportConfig = null
        inventoryStitchType = MyStoneStore.normalizeType(stoneType)
        inventoryStitchArchiveId = MyStoneStore.resolveArchiveId(service, archiveId)
        MyStoneStore.setSelectedArchiveId(service, inventoryStitchArchiveId)
        MyStoneStore.setSelectedType(service, inventoryStitchType)
        inventoryStitchPrepared = true
        currentMode = DailyMode.INVENTORY_STITCH
        Toast.makeText(
            service,
            "${MyStoneStore.getSelectedArchive(service).name}的${MyStoneStore.displayName(inventoryStitchType)}拼图已就绪，请点击悬浮窗开始按钮",
            Toast.LENGTH_SHORT
        ).show()
        refreshActionButton()
    }

    private fun toggleExecution() {
        if (currentMode == DailyMode.BOX_OCR) {
            if (isBoxOcrProcessing) {
                Toast.makeText(service, "框选OCR识别中，请稍候", Toast.LENGTH_SHORT).show()
            } else {
                startBoxOcrOverlay()
            }
            return
        }

        if (stitchEngine.isRunning) {
            stitchEngine.stop()
            refreshActionButton()
            Toast.makeText(service, "星石拼图已停止", Toast.LENGTH_SHORT).show()
            return
        }

        if (characterImportEngine.isRunning) {
            characterImportEngine.stop(showLog = true)
            refreshActionButton()
            Toast.makeText(service, "角色导入已停止", Toast.LENGTH_SHORT).show()
            return
        }

        if (birdFoodRuntimeManager.isRunning) {
            birdFoodRuntimeManager.stop(showToast = true)
            refreshActionButton()
            return
        }

        if (mainline624RuntimeManager.isRunning) {
            mainline624RuntimeManager.stop(showToast = true)
            refreshActionButton()
            return
        }

        if (stargazingRuntimeManager.isRunning) {
            stargazingRuntimeManager.stop(showToast = true)
            refreshActionButton()
            return
        }

        if (engine.isRunning) {
            engine.stop()
            refreshActionButton()
            Toast.makeText(service, "日常任务已暂停", Toast.LENGTH_SHORT).show()
            return
        }

        currentBirdFoodConfig?.let {
            if (!birdFoodRuntimeManager.start()) {
                Toast.makeText(service, "请先确认鸟食配置", Toast.LENGTH_SHORT).show()
                openDailyPage()
                return
            }
            refreshActionButton()
            return
        }

        currentMainline624Config?.let {
            if (!mainline624RuntimeManager.start()) {
                Toast.makeText(service, "请先确认6-24配置", Toast.LENGTH_SHORT).show()
                openDailyPage()
                return
            }
            refreshActionButton()
            return
        }

        currentStargazingConfig?.let {
            if (!stargazingRuntimeManager.start()) {
                Toast.makeText(service, "请先确认观星配置", Toast.LENGTH_SHORT).show()
                openDailyPage()
                return
            }
            refreshActionButton()
            return
        }

        currentCharacterImportConfig?.let {
            if (!characterImportEngine.start { success ->
                    handler.post {
                        refreshActionButton()
                        Toast.makeText(
                            service,
                            if (success) "角色导入已完成" else "角色导入已停止",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (success) {
                            openCharacterImportReview(characterImportEngine.snapshotRecords())
                        }
                    }
                }
            ) {
                Toast.makeText(service, "角色导入启动失败", Toast.LENGTH_SHORT).show()
                openDailyPage()
                return
            }
            refreshActionButton()
            return
        }

        if (inventoryStitchPrepared) {
            if (stitchEngine.isRunning) return
            RunLogger.clear()
            RunLogger.i("开始${MyStoneStore.displayName(inventoryStitchType)}拼图")
            stitchEngine.startStitching(
                archiveId = inventoryStitchArchiveId,
                stoneType = inventoryStitchType,
                onStatusUpdate = { message ->
                    RunLogger.i("日常工具状态：$message")
                },
                onCompleted = { success ->
                    handler.post {
                        if (success) {
                            RunLogger.i("${MyStoneStore.displayName(inventoryStitchType)}拼图完成")
                            Toast.makeText(
                                service,
                                "${MyStoneStore.displayName(inventoryStitchType)}截图已保存到我的星石",
                                Toast.LENGTH_SHORT
                            ).show()
                            showStoneOcrPrompt()
                        } else {
                            RunLogger.i("${MyStoneStore.displayName(inventoryStitchType)}拼图已结束")
                        }
                        refreshActionButton()
                    }
                }
            )
            refreshActionButton()
            return
        }

        val plan = currentTaskPlan
        if (plan == null) {
            Toast.makeText(service, "请先确认日常任务", Toast.LENGTH_SHORT).show()
            openDailyPage()
            return
        }

        RunLogger.clear()
        RunLogger.i("开始日常脚本：${currentScriptName ?: "未命名"}")
        if (isAilao15MinScript()) {
            ailaoStatusBarManager.showRunning(lastWindowX, lastWindowY)
        }
        refreshActionButton()
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                handler.post {
                    if (isAilao15MinScript()) {
                        ailaoStatusBarManager.hide()
                    }
                    refreshActionButton()
                    val message = if (success) {
                        "日常任务已完成"
                    } else {
                        "日常任务已停止：$errorMsg"
                    }
                    Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
                }
            },
            onTaskScheduled = { task, effectiveDelay ->
                if (isAilao15MinScript() && task.id == 20 && effectiveDelay > 0L) {
                    handler.post {
                        ailaoStatusBarManager.showCooldown(lastWindowX, lastWindowY)
                    }
                }
            },
            templateDir = currentTemplateDir,
            scriptFileName = currentScriptName,
        )
    }

    private fun stopCurrentWork() {
        ailaoStatusBarManager.hide()
        birdFoodRuntimeManager.stop()
        mainline624RuntimeManager.stop()
        stargazingRuntimeManager.stop()
        characterImportEngine.stop(showLog = false)
        engine.stop()
        if (stitchEngine.isRunning) stitchEngine.stop()
        stopCoordinatePicker()
        stopBoxOcrOverlay()
        scriptRecorderManager.stop()
        isBoxOcrProcessing = false
        refreshActionButton()
    }

    private fun refreshActionButton() {
        handler.post {
            val button = floatView?.findViewById<ImageButton>(R.id.btn_daily_action) ?: return@post
            if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning || stargazingRuntimeManager.isRunning || stitchEngine.isRunning || characterImportEngine.isRunning) {
                button.setImageResource(R.drawable.ic_action_pause)
                button.contentDescription = "暂停"
            } else {
                button.setImageResource(android.R.drawable.ic_media_play)
                button.contentDescription = "开始"
            }
        }
    }

    private fun isAilao15MinScript(): Boolean =
        currentScriptName == AilaoStatusBarManager.SCRIPT_FILE_NAME

    private fun showStoneOcrPrompt() {
        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(service))
                .setTitle("${MyStoneStore.displayName(inventoryStitchType)}拼图完成")
                .setMessage("截图已保存在我的星石，点击前往界面进行星石统计")
                .setNegativeButton("取消", null)
                .setPositiveButton("前往") { _, _ ->
                    openMyStonePage()
                }
        )
    }

    private fun openMyStonePage() {
        val intent = Intent(service, MyStoneActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        service.startActivity(intent)
    }

    fun triggerStoneStatistics(mode: StoneOcrMode): Boolean {
        if (isStoneOcrProcessing) {
            Toast.makeText(service, "星石 OCR 统计正在进行中", Toast.LENGTH_SHORT).show()
            return false
        }

        if (mode == StoneOcrMode.CLOUD) {
            val record = MyStoneStore.loadRecord(service, inventoryStitchType, inventoryStitchArchiveId)
            if (record?.looseOcrCompletedAt ?: 0L <= 0L) {
                Toast.makeText(service, "请先尝试结果散图的本地OCR识别", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        val localSession = if (mode == StoneOcrMode.LOCAL) {
            stitchEngine.getLocalOcrSessionSnapshot()
        } else {
            stitchEngine.clearPendingLocalOcrSession()
            null
        }

        val record = MyStoneStore.loadRecord(service, inventoryStitchType, inventoryStitchArchiveId)
        val imageFiles = record?.let { MyStoneStore.imageFiles(service, inventoryStitchType, it, inventoryStitchArchiveId) }.orEmpty()
        if (imageFiles.isEmpty()) {
            Toast.makeText(service, "未找到可统计的${MyStoneStore.displayName(inventoryStitchType)}截图", Toast.LENGTH_SHORT).show()
            return false
        }

        isStoneOcrProcessing = true
        Toast.makeText(service, "正在通过${mode.label}统计${MyStoneStore.displayName(inventoryStitchType)}...", Toast.LENGTH_SHORT).show()

        uiScope.launch {
            try {
                val result = if (mode == StoneOcrMode.LOCAL) {
                    localSession?.let { session ->
                        StoneOcrCoordinator.importLocalSession(
                            context = service,
                            stoneType = inventoryStitchType,
                            archiveId = inventoryStitchArchiveId,
                            session = session,
                        ).also {
                            stitchEngine.clearPendingLocalOcrSession()
                        }
                    } ?: StoneOcrCoordinator.importStoneImages(
                        context = service,
                        stoneType = inventoryStitchType,
                        archiveId = inventoryStitchArchiveId,
                        imageFiles = imageFiles,
                        mode = mode,
                    )
                } else {
                    StoneOcrCoordinator.importStoneImages(
                        context = service,
                        stoneType = inventoryStitchType,
                        archiveId = inventoryStitchArchiveId,
                        imageFiles = imageFiles,
                        mode = mode,
                        deviceId = deviceId,
                        onCloudRetryMsg = {
                            Toast.makeText(service, "OCR 请求繁忙，正在重试...", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                if (result.hasPendingRows) {
                    if (mode == StoneOcrMode.LOCAL) {
                        Toast.makeText(service, "统计已完成，如果结果错误很多，请使用结果长图的云端OCR", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(service, "OCR 已导入，可在我的星石中修正红色行", Toast.LENGTH_LONG).show()
                    }
                } else {
                    if (mode == StoneOcrMode.LOCAL) {
                        Toast.makeText(service, "统计已完成，如果结果错误很多，请使用结果长图的云端OCR", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(service, "OCR 统计完成，可在我的星石中查看", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (t: Throwable) {
                Toast.makeText(service, "星石 OCR 统计失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                isStoneOcrProcessing = false
            }
        }
        return true
    }

    private fun openDailyPage() {
        val intent = Intent(service, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TARGET_TAB_HOME)
        }
        service.startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCoordinatePicker() {
        if (coordinatePickerView != null) return

        val overlay = FrameLayout(service).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        val hintView = TextView(service).apply {
            text = "点击坐标点以复制坐标"
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
                    handlePickedCoordinate(event.rawX, event.rawY)
                    stopCoordinatePicker()
                    true
                }
                else -> true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        coordinatePickerView = overlay
        windowManager.addView(overlay, params)
        Toast.makeText(service, "坐标拾取器已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopCoordinatePicker() {
        coordinatePickerView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                coordinatePickerView = null
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startBoxOcrOverlay() {
        if (boxOcrOverlayView != null) return

        val overlay = FrameLayout(service).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
        }
        val density = service.resources.displayMetrics.density
        val minSizePx = (BOX_OCR_MIN_SIZE_DP * density).roundToInt()
        val actionSpacing = (BOX_OCR_ACTION_SPACING_DP * density).roundToInt()
        val handleSize = (BOX_OCR_HANDLE_SIZE_DP * density).roundToInt()
        val strokeWidth = (BOX_OCR_STROKE_DP * density).roundToInt().coerceAtLeast(1)
        val screenWidth = service.resources.displayMetrics.widthPixels
        val screenHeight = service.resources.displayMetrics.heightPixels
        val initialWidth = (screenWidth * 0.62f).roundToInt().coerceAtLeast(minSizePx)
        val initialHeight = (screenHeight * 0.18f).roundToInt().coerceAtLeast(minSizePx)
        val initialLeft = ((screenWidth - initialWidth) / 2f).roundToInt()
        val initialTop = ((screenHeight - initialHeight) / 2.6f).roundToInt().coerceAtLeast(actionSpacing)

        val selectionRect = Rect(
            initialLeft,
            initialTop,
            (initialLeft + initialWidth).coerceAtMost(screenWidth),
            (initialTop + initialHeight).coerceAtMost(screenHeight),
        )

        val selectionContainer = FrameLayout(service).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(strokeWidth, Color.parseColor("#F6D59A"))
            }
        }

        val cancelButton = buildBoxOcrActionButton("×", false)
        val confirmButton = buildBoxOcrActionButton("√", true)
        val actionRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(cancelButton)
            addView(confirmButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = actionSpacing
            })
        }

        val handles: List<Pair<BoxHandle, Int>> = listOf(
            BoxHandle.TOP_LEFT to (Gravity.TOP or Gravity.START),
            BoxHandle.TOP_RIGHT to (Gravity.TOP or Gravity.END),
            BoxHandle.BOTTOM_LEFT to (Gravity.BOTTOM or Gravity.START),
            BoxHandle.BOTTOM_RIGHT to (Gravity.BOTTOM or Gravity.END),
        )
        handles.forEach { (_, gravity) ->
            selectionContainer.addView(
                View(service).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.parseColor("#FFF7EA"))
                        setStroke(strokeWidth, Color.parseColor("#C88A2C"))
                    }
                },
                FrameLayout.LayoutParams(handleSize, handleSize, gravity),
            )
        }

        val selectionParams = FrameLayout.LayoutParams(selectionRect.width(), selectionRect.height()).apply {
            leftMargin = selectionRect.left
            topMargin = selectionRect.top
        }
        overlay.addView(selectionContainer, selectionParams)
        val actionParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            leftMargin = selectionRect.centerX()
            topMargin = selectionRect.bottom + actionSpacing
        }
        overlay.addView(actionRow, actionParams)

        fun updateSelectionLayout() {
            val params = selectionContainer.layoutParams as FrameLayout.LayoutParams
            params.width = selectionRect.width()
            params.height = selectionRect.height()
            params.leftMargin = selectionRect.left
            params.topMargin = selectionRect.top
            selectionContainer.layoutParams = params

            actionRow.post {
                val rowParams = actionRow.layoutParams as FrameLayout.LayoutParams
                rowParams.leftMargin = (selectionRect.centerX() - actionRow.width / 2f).roundToInt()
                    .coerceIn(0, max(0, screenWidth - actionRow.width))
                rowParams.topMargin = (selectionRect.bottom + actionSpacing)
                    .coerceAtMost(max(0, screenHeight - actionRow.height))
                actionRow.layoutParams = rowParams
            }
        }

        overlay.setOnTouchListener(object : View.OnTouchListener {
            private var activeHandle: BoxHandle? = null
            private var startRawX = 0f
            private var startRawY = 0f
            private var startRect = Rect()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val rowLocation = IntArray(2)
                actionRow.getLocationOnScreen(rowLocation)
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val withinActions = event.rawX in rowLocation[0].toFloat()..(rowLocation[0] + actionRow.width).toFloat() &&
                        event.rawY in rowLocation[1].toFloat()..(rowLocation[1] + actionRow.height).toFloat()
                    if (withinActions) return false

                    activeHandle = resolveHandle(selectionRect, event.rawX, event.rawY, handleSize.toFloat())
                    if (activeHandle == null) return true
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startRect = Rect(selectionRect)
                    return true
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        val handle = activeHandle ?: return true
                        val dx = (event.rawX - startRawX).roundToInt()
                        val dy = (event.rawY - startRawY).roundToInt()
                        applyBoxHandleDelta(
                            target = selectionRect,
                            source = startRect,
                            handle = handle,
                            dx = dx,
                            dy = dy,
                            minSize = minSizePx,
                            boundsWidth = screenWidth,
                            boundsHeight = screenHeight,
                        )
                        updateSelectionLayout()
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeHandle = null
                        return true
                    }
                }
                return false
            }
        })

        cancelButton.setOnClickListener {
            stopBoxOcrOverlay()
        }
        confirmButton.setOnClickListener {
            val normalizedRect = buildNormalizedSelectionRect(
                selectionRect = selectionRect,
                containerWidth = overlay.width.takeIf { it > 0 } ?: screenWidth,
                containerHeight = overlay.height.takeIf { it > 0 } ?: screenHeight,
            )
            stopBoxOcrOverlay()
            runBoxOcr(normalizedRect)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        boxOcrOverlayView = overlay
        windowManager.addView(overlay, params)
        updateSelectionLayout()
        Toast.makeText(service, "请拖动或缩放识别区域后点击√", Toast.LENGTH_SHORT).show()
    }

    private fun stopBoxOcrOverlay() {
        boxOcrOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                boxOcrOverlayView = null
            }
        }
    }

    private fun buildBoxOcrActionButton(text: String, primary: Boolean): TextView {
        return TextView(service).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor(if (primary) "#6B4E1C" else "#A84D41"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor(if (primary) "#FFF6E4" else "#FFF0ED"))
                setStroke(dp(1), Color.parseColor(if (primary) "#D0A04C" else "#D49C95"))
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
        }
    }

    private fun resolveHandle(rect: Rect, rawX: Float, rawY: Float, touchRadius: Float): BoxHandle? {
        val nearTopLeft = distance(rawX, rawY, rect.left.toFloat(), rect.top.toFloat()) <= touchRadius * 1.7f
        val nearTopRight = distance(rawX, rawY, rect.right.toFloat(), rect.top.toFloat()) <= touchRadius * 1.7f
        val nearBottomLeft = distance(rawX, rawY, rect.left.toFloat(), rect.bottom.toFloat()) <= touchRadius * 1.7f
        val nearBottomRight = distance(rawX, rawY, rect.right.toFloat(), rect.bottom.toFloat()) <= touchRadius * 1.7f
        return when {
            nearTopLeft -> BoxHandle.TOP_LEFT
            nearTopRight -> BoxHandle.TOP_RIGHT
            nearBottomLeft -> BoxHandle.BOTTOM_LEFT
            nearBottomRight -> BoxHandle.BOTTOM_RIGHT
            rect.contains(rawX.roundToInt(), rawY.roundToInt()) -> BoxHandle.MOVE
            else -> null
        }
    }

    private fun applyBoxHandleDelta(
        target: Rect,
        source: Rect,
        handle: BoxHandle,
        dx: Int,
        dy: Int,
        minSize: Int,
        boundsWidth: Int,
        boundsHeight: Int,
    ) {
        when (handle) {
            BoxHandle.MOVE -> {
                val width = source.width()
                val height = source.height()
                val newLeft = (source.left + dx).coerceIn(0, boundsWidth - width)
                val newTop = (source.top + dy).coerceIn(0, boundsHeight - height)
                target.set(newLeft, newTop, newLeft + width, newTop + height)
            }
            BoxHandle.TOP_LEFT -> {
                val left = (source.left + dx).coerceIn(0, source.right - minSize)
                val top = (source.top + dy).coerceIn(0, source.bottom - minSize)
                target.set(left, top, source.right, source.bottom)
            }
            BoxHandle.TOP_RIGHT -> {
                val right = (source.right + dx).coerceIn(source.left + minSize, boundsWidth)
                val top = (source.top + dy).coerceIn(0, source.bottom - minSize)
                target.set(source.left, top, right, source.bottom)
            }
            BoxHandle.BOTTOM_LEFT -> {
                val left = (source.left + dx).coerceIn(0, source.right - minSize)
                val bottom = (source.bottom + dy).coerceIn(source.top + minSize, boundsHeight)
                target.set(left, source.top, source.right, bottom)
            }
            BoxHandle.BOTTOM_RIGHT -> {
                val right = (source.right + dx).coerceIn(source.left + minSize, boundsWidth)
                val bottom = (source.bottom + dy).coerceIn(source.top + minSize, boundsHeight)
                target.set(source.left, source.top, right, bottom)
            }
        }
    }

    private fun runBoxOcr(selectionRect: NormalizedSelectionRect) {
        if (isBoxOcrProcessing) return
        isBoxOcrProcessing = true
        Toast.makeText(service, "正在识别框选内容...", Toast.LENGTH_SHORT).show()
        uiScope.launch {
            captureScreenshot(
                onSuccess = { screenshot ->
                    uiScope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.Default) {
                                val screenshotRect = mapNormalizedRectToScreenshot(selectionRect, screenshot)
                                val cropped = Bitmap.createBitmap(
                                    screenshot,
                                    screenshotRect.left,
                                    screenshotRect.top,
                                    screenshotRect.width(),
                                    screenshotRect.height(),
                                )
                                try {
                                    PaddleTextRecognizer.recognize(service, cropped).text.trim()
                                } finally {
                                    cropped.recycle()
                                }
                            }
                        }
                        screenshot.recycle()
                        isBoxOcrProcessing = false
                        result.onSuccess { text ->
                            showBoxOcrResultDialog(text)
                        }.onFailure { error ->
                            Toast.makeText(service, "框选OCR失败：${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onFailure = {
                    isBoxOcrProcessing = false
                    Toast.makeText(service, "截图失败，无法进行框选OCR", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    private fun buildNormalizedSelectionRect(
        selectionRect: Rect,
        containerWidth: Int,
        containerHeight: Int,
    ): NormalizedSelectionRect {
        val safeWidth = containerWidth.coerceAtLeast(1)
        val safeHeight = containerHeight.coerceAtLeast(1)
        return NormalizedSelectionRect(
            leftRatio = (selectionRect.left.toFloat() / safeWidth).coerceIn(0f, 1f),
            topRatio = (selectionRect.top.toFloat() / safeHeight).coerceIn(0f, 1f),
            widthRatio = (selectionRect.width().toFloat() / safeWidth).coerceIn(0f, 1f),
            heightRatio = (selectionRect.height().toFloat() / safeHeight).coerceIn(0f, 1f),
        )
    }

    private fun showBoxOcrResultDialog(text: String) {
        val themeContext = DialogUtils.getThemeContext(service)
        val input = EditText(themeContext).apply {
            setText(text.ifBlank { "未识别到文本" })
            setTextColor(Color.parseColor("#4E3C1E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundResource(R.drawable.bg_stone_empty_panel)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isSingleLine = false
            minLines = 6
            maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val container = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), 0)
            addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("框选OCR结果")
                .setView(container)
                .setNegativeButton("关闭", null)
                .setPositiveButton("复制") { _, _ ->
                    copyToClipboard(text.ifBlank { "未识别到文本" })
                    Toast.makeText(service, "识别结果已复制", Toast.LENGTH_SHORT).show()
                },
        )
    }

    private fun captureScreenshot(onSuccess: (Bitmap) -> Unit, onFailure: (Int) -> Unit) {
        service.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()
                    if (softwareBitmap == null) {
                        onFailure(-1)
                    } else {
                        onSuccess(softwareBitmap)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onFailure(errorCode)
                }
            },
        )
    }

    private fun mapNormalizedRectToScreenshot(
        selectionRect: NormalizedSelectionRect,
        screenshot: Bitmap,
    ): Rect {
        val left = (selectionRect.leftRatio * screenshot.width).roundToInt()
            .coerceIn(0, screenshot.width - 1)
        val top = (selectionRect.topRatio * screenshot.height).roundToInt()
            .coerceIn(0, screenshot.height - 1)
        val width = (selectionRect.widthRatio * screenshot.width).roundToInt()
            .coerceIn(1, screenshot.width - left)
        val height = (selectionRect.heightRatio * screenshot.height).roundToInt()
            .coerceIn(1, screenshot.height - top)
        val right = (left + width).coerceIn(left + 1, screenshot.width)
        val bottom = (top + height).coerceIn(top + 1, screenshot.height)
        return Rect(left, top, right, bottom)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).roundToInt()

    private fun handlePickedCoordinate(rawX: Float, rawY: Float) {
        val coordinate = buildPickedCoordinate(rawX, rawY)
        copyToClipboard(coordinate.clipboardText)
        Toast.makeText(service, coordinate.toastText, Toast.LENGTH_LONG).show()
    }

    private fun buildPickedCoordinate(rawX: Float, rawY: Float): PickedCoordinate {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val gameWidth = BASE_W * gameScale
        val gameHeight = BASE_H * gameScale
        val offsetX = (screenWidth - gameWidth) / 2f
        val offsetY = (screenHeight - gameHeight) / 2f
        val statusBarHeight = getScaledStatusBarHeight()

        val screenX = rawX.roundToInt()
        val screenY = rawY.roundToInt()
        val designX = ((rawX - offsetX) / gameScale).roundToInt()
        val centerY = ((rawY - offsetY) / gameScale).roundToInt()
        val topY = ((rawY - statusBarHeight) / gameScale).roundToInt()
        val absoluteY = (rawY * BASE_H / screenHeight).roundToInt()

        val clipboardText = buildString {
            appendLine("屏幕坐标: x=$screenX, y=$screenY")
            appendLine("""center: { "x": $designX, "y": $centerY, "align": "center" }""")
            appendLine("""top: { "x": $designX, "y": $topY, "align": "top" }""")
            append("""absolute: { "x": $designX, "y": $absoluteY, "align": "absolute" }""")
        }

        return PickedCoordinate(
            clipboardText = clipboardText,
            toastText = "已复制 ($screenX, $screenY)"
        )
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Pair(bounds.width().toFloat(), bounds.height().toFloat())
        } else {
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            Pair(point.x.toFloat(), point.y.toFloat())
        }
    }

    private fun getRawStatusBarHeight(): Int {
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) service.resources.getDimensionPixelSize(resourceId) else 40
    }

    private fun getScaledStatusBarHeight(): Float = getRawStatusBarHeight() / 2f

    private fun copyToClipboard(text: String) {
        val clipboard =
            service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("daily-coordinate", text))
    }

    private fun moveWindowToTopLeftSafely() {
        val targetView = floatView ?: return
        targetView.post {
            val params = targetView.layoutParams as? WindowManager.LayoutParams ?: return@post
            val density = service.resources.displayMetrics.density
            val marginPx = (FLOAT_WINDOW_EDGE_MARGIN_DP * density).roundToInt()
            val topSafeMargin = (FLOAT_WINDOW_TOP_MARGIN_DP * density).roundToInt()
            val (screenWidth, screenHeight) = getRealScreenSize()
            val viewWidth = targetView.width.takeIf { it > 0 } ?: targetView.measuredWidth
            val viewHeight = targetView.height.takeIf { it > 0 } ?: targetView.measuredHeight
            val maxX = (screenWidth.roundToInt() - viewWidth - marginPx).coerceAtLeast(marginPx)
            val maxY = (screenHeight.roundToInt() - viewHeight - marginPx).coerceAtLeast(topSafeMargin)

            params.x = marginPx.coerceAtMost(maxX)
            params.y = topSafeMargin.coerceAtMost(maxY)
            lastWindowX = params.x
            lastWindowY = params.y
            safelyUpdateViewLayout(targetView, params, "更新悬浮窗位置失败")
            ailaoStatusBarManager.updateAnchor(lastWindowX, lastWindowY)
        }
    }

    private fun removeWindow() {
        floatView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                floatView = null
            }
        }
    }

    private fun openCharacterImportReview(records: List<com.example.yuanassist.model.ImportedCharacterRecord>) {
        val intent = CharacterImportReviewActivity.createIntent(service, records).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
    }

    private fun updateOverlayState(isOpen: Boolean) {
        service.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("daily_window_open", isOpen)
            .apply()
    }

    private fun safelyUpdateViewLayout(
        targetView: View,
        params: WindowManager.LayoutParams,
        logLabel: String
    ) {
        try {
            windowManager.updateViewLayout(targetView, params)
        } catch (t: Throwable) {
            RunLogger.e(logLabel, t)
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragListener(
        params: WindowManager.LayoutParams,
        targetView: View
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        safelyUpdateViewLayout(targetView, params, "拖动悬浮窗失败")
                        ailaoStatusBarManager.updateAnchor(params.x, params.y)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        lastWindowX = params.x
                        lastWindowY = params.y
                        ailaoStatusBarManager.updateAnchor(lastWindowX, lastWindowY)
                        if (abs(event.rawX - initialTouchX) < 10 &&
                            abs(event.rawY - initialTouchY) < 10
                        ) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    private data class PickedCoordinate(
        val clipboardText: String,
        val toastText: String
    )
}
