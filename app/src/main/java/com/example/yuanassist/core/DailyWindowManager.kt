package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.network.OcrManager
import com.example.yuanassist.ui.MainActivity
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.StoneOcrParser
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class DailyWindowManager(private val service: AccessibilityService) {

    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val PREFS_APP = "app_prefs"
        private const val FLOAT_WINDOW_EDGE_MARGIN_DP = 12
        private const val FLOAT_WINDOW_TOP_MARGIN_DP = 20
    }

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
    private val stitchEngine = InventoryStitchEngine(service)
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val deviceId: String by lazy {
        Settings.Secure.getString(service.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private var floatView: View? = null
    private var coordinatePickerView: View? = null
    private var currentTaskPlan: DailyTaskPlan? = null
    private var currentScriptName: String? = null
    private var currentBirdFoodConfig: BirdFoodConfig? = null
    private var currentMainline624Config: Mainline624Config? = null
    private var inventoryStitchPrepared = false
    private var inventoryStitchType = MyStoneStore.TYPE_MAIN
    private var isStoneOcrProcessing = false
    private var lastWindowX = 100
    private var lastWindowY = 100

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
        dragHandle.setOnTouchListener(createDragListener(params, view))
        dragHandle.setOnClickListener { openDailyPage() }
        actionButton.setOnClickListener { toggleExecution() }
        refreshActionButton()
        windowManager.addView(view, params)
        updateOverlayState(isOpen = true)
    }

    fun hideWindow() {
        stopCurrentWork()
        removeWindow()
        updateOverlayState(isOpen = false)
    }

    fun isWindowVisible(): Boolean = floatView != null

    fun submitTaskPlan(plan: DailyTaskPlan, scriptName: String) {
        currentBirdFoodConfig = null
        currentMainline624Config = null
        currentTaskPlan = plan
        currentScriptName = scriptName
        showWindow()
        refreshActionButton()
    }

    fun submitTaskPlanJson(fileName: String, jsonContent: String): Result<Unit> {
        return runCatching {
            val plan = gson.fromJson(jsonContent, DailyTaskPlan::class.java)
            submitTaskPlan(plan, fileName)
        }
    }

    fun submitBirdFoodConfig(config: BirdFoodConfig) {
        currentTaskPlan = null
        currentScriptName = null
        inventoryStitchPrepared = false
        currentMainline624Config = null
        currentBirdFoodConfig = config
        birdFoodRuntimeManager.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun submitMainline624Config(config: Mainline624Config) {
        currentTaskPlan = null
        currentScriptName = null
        inventoryStitchPrepared = false
        currentBirdFoodConfig = null
        currentMainline624Config = config
        mainline624RuntimeManager.prepare(config)
        showWindow()
        refreshActionButton()
    }

    fun startCoordinatePickerMode() {
        currentTaskPlan = null
        currentScriptName = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        inventoryStitchPrepared = false
        showWindow()
        startCoordinatePicker()
    }

    fun prepareInventoryStitching(stoneType: String) {
        showWindow()
        if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning) {
            Toast.makeText(service, "请先停止当前日常任务", Toast.LENGTH_SHORT).show()
            return
        }
        currentTaskPlan = null
        currentScriptName = null
        currentBirdFoodConfig = null
        currentMainline624Config = null
        inventoryStitchType = MyStoneStore.normalizeType(stoneType)
        MyStoneStore.setSelectedType(service, inventoryStitchType)
        inventoryStitchPrepared = true
        Toast.makeText(
            service,
            "${MyStoneStore.displayName(inventoryStitchType)}拼图已就绪，请点击悬浮窗开始按钮",
            Toast.LENGTH_SHORT
        ).show()
        refreshActionButton()
    }

    private fun toggleExecution() {
        if (stitchEngine.isRunning) {
            stitchEngine.stop()
            refreshActionButton()
            Toast.makeText(service, "星石拼图已停止", Toast.LENGTH_SHORT).show()
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

        if (inventoryStitchPrepared) {
            if (stitchEngine.isRunning) return
            RunLogger.clear()
            RunLogger.i("开始${MyStoneStore.displayName(inventoryStitchType)}拼图")
            stitchEngine.startStitching(
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
        refreshActionButton()
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                handler.post {
                    refreshActionButton()
                    val message = if (success) {
                        "日常任务已完成"
                    } else {
                        "日常任务已停止：$errorMsg"
                    }
                    Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun stopCurrentWork() {
        birdFoodRuntimeManager.stop()
        mainline624RuntimeManager.stop()
        engine.stop()
        if (stitchEngine.isRunning) stitchEngine.stop()
        stopCoordinatePicker()
        refreshActionButton()
    }

    private fun refreshActionButton() {
        handler.post {
            val button = floatView?.findViewById<ImageButton>(R.id.btn_daily_action) ?: return@post
            if (engine.isRunning || birdFoodRuntimeManager.isRunning || mainline624RuntimeManager.isRunning || stitchEngine.isRunning) {
                button.setImageResource(R.drawable.ic_action_pause)
                button.contentDescription = "暂停"
            } else {
                button.setImageResource(android.R.drawable.ic_media_play)
                button.contentDescription = "开始"
            }
        }
    }

    private fun showStoneOcrPrompt() {
        val record = MyStoneStore.loadRecord(service, inventoryStitchType) ?: return
        if (MyStoneStore.imageFiles(service, inventoryStitchType, record).isEmpty()) return

        DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(service))
                .setTitle("${MyStoneStore.displayName(inventoryStitchType)}拼图完成")
                .setMessage("截图已保存到“我的星石”，是否通过 OCR 统计${MyStoneStore.displayName(inventoryStitchType)}个数？")
                .setPositiveButton("是") { _, _ ->
                    startStoneOcrStatistics()
                }
                .setNegativeButton("否", null)
        )
    }

    private fun startStoneOcrStatistics() {
        if (isStoneOcrProcessing) {
            Toast.makeText(service, "星石 OCR 统计正在进行中", Toast.LENGTH_SHORT).show()
            return
        }

        val record = MyStoneStore.loadRecord(service, inventoryStitchType)
        val imageFiles = record?.let { MyStoneStore.imageFiles(service, inventoryStitchType, it) }.orEmpty()
        if (imageFiles.isEmpty()) {
            Toast.makeText(service, "未找到可统计的${MyStoneStore.displayName(inventoryStitchType)}截图", Toast.LENGTH_SHORT).show()
            return
        }

        isStoneOcrProcessing = true
        Toast.makeText(service, "正在通过 OCR 统计${MyStoneStore.displayName(inventoryStitchType)}...", Toast.LENGTH_SHORT).show()

        uiScope.launch {
            try {
                val wordsGroups = mutableListOf<List<String>>()
                val rawEntryGroups = mutableListOf<List<String>>()
                val strategyUsed = linkedSetOf<String>()

                for (file in imageFiles) {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bitmap == null) {
                        Toast.makeText(service, "读取星石截图失败：${file.name}", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    try {
                        when (
                            val result = OcrManager.recognizeStoneImage(
                                bitmap = bitmap,
                                deviceId = deviceId,
                                onRetryMsg = {
                                    Toast.makeText(service, "OCR 请求繁忙，正在重试...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        ) {
                            is OcrManager.StoneOcrResult.Success -> {
                                wordsGroups += result.words
                                rawEntryGroups += result.rawEntries
                                if (result.strategyUsed.isNotEmpty()) {
                                    strategyUsed += result.strategyUsed
                                }
                            }

                            is OcrManager.StoneOcrResult.Error -> {
                                Toast.makeText(service, result.message, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                if (rawEntryGroups.isNotEmpty()) {
                    RunLogger.raw("【OCR返回原文本】")
                    StoneOcrParser.formatRawJsonByRow(rawEntryGroups).forEach { line ->
                        RunLogger.i(line)
                    }
                }
                val rows = StoneOcrParser.buildRows(wordsGroups)
                val lines = StoneOcrParser.format(StoneOcrParser.aggregate(rows))
                val hasPendingRows = rows.any { !StoneOcrParser.isRowResolved(it) }

                MyStoneStore.saveOcrResult(
                    context = service,
                    stoneType = inventoryStitchType,
                    rows = rows,
                    statsLines = lines,
                    ocrStrategy = strategyUsed.joinToString(",")
                )

                if (hasPendingRows) {
                    RunLogger.i("星石 OCR 完成，但仍有待修正行，行数=${rows.size}")
                    Toast.makeText(service, "OCR 已导入，可在我的星石中修正红色行", Toast.LENGTH_LONG).show()
                } else {
                    RunLogger.i("星石 OCR 完成：${lines.joinToString(" | ")}")
                    Toast.makeText(service, "OCR 统计完成，可在我的星石中查看", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                Toast.makeText(service, "星石 OCR 统计失败：${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                isStoneOcrProcessing = false
            }
        }
    }

    private fun openDailyPage() {
        val intent = Intent(service, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TARGET_TAB_DAILY)
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
            windowManager.updateViewLayout(targetView, params)
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

    private fun updateOverlayState(isOpen: Boolean) {
        service.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("daily_window_open", isOpen)
            .apply()
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
                        windowManager.updateViewLayout(targetView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        lastWindowX = params.x
                        lastWindowY = params.y
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
