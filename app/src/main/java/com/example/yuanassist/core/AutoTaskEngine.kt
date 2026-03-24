package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.utils.RunLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.min

class AutoTaskEngine(private val service: AccessibilityService) {

    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
    }

    var isRunning = false
    var lastMatchX = 0f
    var lastMatchY = 0f
    var debugRoiEnabled = true
    var verboseLoggingEnabled = true

    private var currentTaskPlan: DailyTaskPlan? = null
    private var currentTaskId = -1
    private var onPlanCompleted: ((Boolean, String) -> Unit)? = null
    private var onPlanCompletedDetailed: ((DailyPlanCompletion) -> Unit)? = null
    private var customActionHandler: ((DailyTask, () -> Unit, () -> Unit) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val matchedPointsByTaskId = mutableMapOf<Int, PointF>()
    private val variables = mutableMapOf<String, String>()
    private var runGeneration = 0L
    private var debugRoiView: View? = null
    private var cooldownStartedAtMs: Long? = null
    private var treatFailMinusOneAsSuccess = false

    private fun verboseInfo(message: String) {
        if (!verboseLoggingEnabled) return
        if (shouldSuppressInfoLog(message)) return
        RunLogger.i(message)
    }

    private fun shouldSuppressInfoLog(message: String): Boolean {
        return message.contains("[startPlan]") ||
            message.contains("引擎已启动") ||
            message.contains("引擎已被用户停止") ||
            message.contains("准备执行任务") ||
            message.contains("模板缓存命中") ||
            message.contains("模板已加载") ||
            message.contains("截图缩放")
    }
    private val templateCache = object : android.util.LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private data class ScreenshotMapping(
        val screenshotToDisplayX: Float,
        val screenshotToDisplayY: Float,
        val displayToScreenshotX: Float,
        val displayToScreenshotY: Float,
        val orientationMismatch: Boolean,
        val nonUniformScale: Boolean,
        val displayWidth: Float,
        val displayHeight: Float,
        val screenshotWidth: Int,
        val screenshotHeight: Int
    )

    private data class SearchRegion(
        val bitmap: Bitmap,
        val offsetX: Float,
        val offsetY: Float,
        val ownsBitmap: Boolean
    )

    private data class RedRegionMatch(
        val center: PointF,
        val confidence: Float,
        val areaRatio: Float,
        val fillRatio: Float,
        val rednessScore: Float
    )

    fun startPlan(
        plan: DailyTaskPlan,
        onCompleted: (Boolean, String) -> Unit,
        onCustomAction: ((DailyTask, () -> Unit, () -> Unit) -> Unit)? = null,
        onCompletedDetailed: ((DailyPlanCompletion) -> Unit)? = null,
        initialVariables: Map<String, String> = emptyMap(),
        treatFailMinusOneAsSuccess: Boolean = false
    ) {
        runGeneration += 1
        handler.removeCallbacksAndMessages(null)
        clearDebugRoi()
        currentTaskPlan = plan
        currentTaskId = plan.start_task_id
        isRunning = true
        matchedPointsByTaskId.clear()
        variables.clear()
        variables.putAll(initialVariables)
        lastMatchX = 0f
        lastMatchY = 0f
        cooldownStartedAtMs = null
        this.onPlanCompleted = onCompleted
        this.onPlanCompletedDetailed = onCompletedDetailed
        customActionHandler = onCustomAction
        this.treatFailMinusOneAsSuccess = treatFailMinusOneAsSuccess
        logDisplayMetrics("startPlan")
        verboseInfo("引擎已启动")
        executeNextTask()
    }

    fun stop() {
        runGeneration += 1
        handler.removeCallbacksAndMessages(null)
        clearDebugRoi()
        isRunning = false
        matchedPointsByTaskId.clear()
        variables.clear()
        verboseInfo("引擎已被用户停止")
        completePlan(false, "已停止", -1)
    }

    fun release() {
        stop()
        currentTaskPlan = null
        currentTaskId = -1
        onPlanCompleted = null
        onPlanCompletedDetailed = null
        customActionHandler = null
        templateCache.evictAll()
    }

    private fun completePlan(
        success: Boolean,
        message: String,
        terminalCode: Int,
        task: DailyTask? = null,
        terminalNote: String? = null
    ) {
        isRunning = false
        val detailedCallback = onPlanCompletedDetailed
        val completionCallback = onPlanCompleted
        val completionCooldownStartedAtMs = cooldownStartedAtMs
        onPlanCompletedDetailed = null
        onPlanCompleted = null
        customActionHandler = null
        cooldownStartedAtMs = null
        treatFailMinusOneAsSuccess = false
        detailedCallback?.invoke(
            DailyPlanCompletion(
                success,
                message,
                terminalCode,
                task?.id ?: -1,
                terminalNote,
                completionCooldownStartedAtMs
            )
        )
        completionCallback?.invoke(success, message)
    }

    fun finishTask(task: DailyTask, isSuccess: Boolean) {
        if (!isSuccess) {
            if (treatFailMinusOneAsSuccess && task.on_fail == -1) {
                verboseInfo("Task ${task.id} on_fail=-1 treated as completed")
                completePlan(true, "completed", -1, task, task.params?.terminal_note)
                return
            }
            when (task.on_fail) {
                -4, -3, -2 -> {
                    val msg = task.params?.terminal_note
                        ?: when (task.on_fail) {
                            -4 -> "任务倒计时中"
                            -3 -> "资源耗尽"
                            else -> "任务失败"
                        }
                    if (task.on_fail == -4 || task.on_fail == -3) {
                        RunLogger.i("任务 ${task.id} 结束：$msg")
                    } else {
                        RunLogger.e("任务 ${task.id} 结束：$msg")
                    }
                    completePlan(false, msg, task.on_fail, task, task.params?.terminal_note)
                    return
                }
                -1 -> {
                    if (treatFailMinusOneAsSuccess) {
                        verboseInfo("ä»»åŠ¡ ${task.id} å¤±è´¥åˆ†æ”¯=-1ï¼ŒæŒ‰æ­£å¸¸ç»“æŸå¤„ç†")
                        completePlan(true, "å·²å®Œæˆ", -1, task, task.params?.terminal_note)
                        return
                    }
                    val msg = when (task.action) {
                        "MATCH_TEMPLATE" -> "未找到模板:${task.params?.template_name}"
                        "CLICK_OCR_TEXT" -> "未识别到文字:${task.params?.target_text ?: task.params?.button_name}"
                        "CLICK_DYNAMIC_BUTTON" -> "未找到动态按钮:${task.params?.button_name}"
                        else -> "任务失败:${task.action}"
                    }
                    RunLogger.e("任务 ${task.id} 失败：$msg")
                    completePlan(false, msg, -1, task)
                    return
                }
                else -> {
                    RunLogger.e("任务 ${task.id} 失败，跳转=${task.on_fail}")
                    currentTaskId = task.on_fail
                    executeNextTask()
                    return
                }
            }
        }

        if (task.start_cooldown_on_success && cooldownStartedAtMs == null) {
            cooldownStartedAtMs = System.currentTimeMillis()
            verboseInfo("任务 ${task.id} 成功，开始记录冷却计时")
        }

        val nextTaskId = resolveSuccessTaskId(task)
        when (nextTaskId) {
            -4, -3, -2 -> {
                val msg = task.params?.terminal_note
                    ?: when (nextTaskId) {
                        -4 -> "任务倒计时中"
                        -3 -> "资源耗尽"
                        else -> "任务失败"
                    }
                if (nextTaskId == -4 || nextTaskId == -3) {
                    RunLogger.i("任务 ${task.id} 结束：$msg")
                } else {
                    RunLogger.e("任务 ${task.id} 结束：$msg")
                }
                completePlan(false, msg, nextTaskId, task, task.params?.terminal_note)
            }
            -1 -> {
                verboseInfo("任务 ${task.id} 成功，计划完成")
                completePlan(true, "已完成", -1, task)
            }
            else -> {
                verboseInfo("任务 ${task.id} 成功，下一步=$nextTaskId")
                currentTaskId = nextTaskId
                executeNextTask()
            }
        }
    }

    fun dispatchClick(x: Float, y: Float, onComplete: () -> Unit) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onComplete()
            override fun onCancelled(gestureDescription: GestureDescription?) {
                RunLogger.e("点击手势被取消")
                onComplete()
            }
        }, null)
    }

    private fun executeNextTask() {
        if (!isRunning) return
        val generation = runGeneration
        val task = currentTaskPlan?.tasks?.find { it.id == currentTaskId }
        if (task == null) {
            val msg = "未找到任务:$currentTaskId"
            RunLogger.e(msg)
            completePlan(false, msg, -1)
            return
        }

        verboseInfo("准备执行任务 ${task.id} ${task.action}，延迟=${task.delay}")
        handler.postDelayed({
            if (!isRunning || generation != runGeneration) return@postDelayed
            try {
                when (task.action) {
                    "CLICK" -> executeClick(task)
                    "SWIPE" -> executeSwipe(task)
                    "BACK" -> executeGlobalBack(task)
                    "SET_VAR" -> executeSetVar(task)
                    "CLICK_LAST_MATCH", "CLICK_LAST_OCR" -> executeContextClick(task)
                    "CLICK_OCR_TEXT" -> executeOcrTextClick(task)
                    "CLICK_RED_REGION" -> executeRedRegionClick(task)
                    "MATCH_TEMPLATE" -> executeMatchTemplate(task)
                    else -> {
                        val custom = customActionHandler
                        if (custom != null) {
                            custom(task, { finishTask(task, true) }, { finishTask(task, false) })
                        } else {
                            RunLogger.e("不支持的动作 ${task.action}")
                            finishTask(task, false)
                        }
                    }
                }
            } catch (t: Throwable) {
                RunLogger.e("任务 ${task.id} 执行崩溃 ${task.action}", t)
                finishTask(task, false)
            }
        }, task.delay)
    }

    private fun executeClick(task: DailyTask) {
        val generation = runGeneration
        val p = task.params ?: return finishTask(task, false)
        val refPoint = p.ref_task_id?.let { matchedPointsByTaskId[it] }
        if (refPoint != null) {
            verboseInfo("任务 ${task.id} 点击 x=${refPoint.x.toInt()} y=${refPoint.y.toInt()}")
            dispatchClick(refPoint.x, refPoint.y) {
                if (generation == runGeneration && isRunning) finishTask(task, true)
            }
            return
        }
        if (p.x == null || p.y == null) return finishTask(task, false)
        val (realX, realY) = calculateRealCoordinate(p.x, p.y, p.align)
        verboseInfo("任务 ${task.id} 点击 x=${realX.toInt()} y=${realY.toInt()}")
        dispatchClick(realX, realY) {
            if (generation == runGeneration && isRunning) finishTask(task, true)
        }
    }

    private fun executeSwipe(task: DailyTask) {
        val generation = runGeneration
        val p = task.params ?: return finishTask(task, false)
        if (p.startX == null || p.startY == null || p.endX == null || p.endY == null) {
            return finishTask(task, false)
        }
        val (sx, sy) = calculateRealCoordinate(p.startX, p.startY, p.align)
        val (ex, ey) = calculateRealCoordinate(p.endX, p.endY, p.align)
        val duration = p.duration ?: 300L
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        verboseInfo("任务 ${task.id} 执行滑动")
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (generation == runGeneration && isRunning) finishTask(task, true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                RunLogger.e("滑动手势被取消")
                if (generation == runGeneration && isRunning) finishTask(task, false)
            }
        }, null)
    }

    private fun executeContextClick(task: DailyTask) {
        val generation = runGeneration
        val refPoint = task.params?.ref_task_id?.let { matchedPointsByTaskId[it] }
        if (refPoint != null) {
            dispatchClick(refPoint.x, refPoint.y) {
                if (generation == runGeneration && isRunning) finishTask(task, true)
            }
            return
        }
        if (lastMatchX == 0f && lastMatchY == 0f) return finishTask(task, false)
        dispatchClick(lastMatchX, lastMatchY) {
            if (generation == runGeneration && isRunning) finishTask(task, true)
        }
    }

    private fun executeGlobalBack(task: DailyTask) {
        verboseInfo("任务 ${task.id} 执行全局返回")
        finishTask(task, service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
    }

    private fun executeSetVar(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val name = p.var_name ?: return finishTask(task, false)
        val value = p.var_value ?: return finishTask(task, false)
        variables[name] = value
        verboseInfo("任务 ${task.id} 设置变量 $name=$value")
        finishTask(task, true)
    }

    private fun executeOcrTextClick(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val targetText = (p.target_text ?: p.button_name)?.trim().orEmpty()
        val matchAnyChinese = targetText.isEmpty()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return finishTask(task, false)

        val generation = runGeneration
        verboseInfo("任务 ${task.id} OCR识别文字=$targetText，区域=${formatRoi(p.roi)}")

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }

                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var ocrBitmap: Bitmap? = null
                    var buffer: android.hardware.HardwareBuffer? = null
                    var hwBitmap: Bitmap? = null

                    try {
                        buffer = result.hardwareBuffer
                        hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        if (swBitmap == null) {
                            RunLogger.e("任务 ${task.id} OCR截图转换失败")
                            finishTask(task, false)
                            return
                        }

                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, p.roi, mapping, task.id)
                        val recognizer = TextRecognition.getClient(
                            ChineseTextRecognizerOptions.Builder().build()
                        )
                        ocrBitmap = if (matchAnyChinese) createYellowTextOcrBitmap(searchRegion.bitmap) else null
                        val image = InputImage.fromBitmap(ocrBitmap ?: searchRegion.bitmap, 0)
                        recognizer.process(image)
                            .addOnSuccessListener { text ->
                                recognizer.close()
                                if (!isRunning || generation != runGeneration) {
                                    if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                    searchRegion?.release()
                                    swBitmap?.recycle()
                                    return@addOnSuccessListener
                                }

                                val center = findOcrTargetCenter(text, targetText, matchAnyChinese)
                                if (center == null) {
                                    finishTask(task, false)
                                } else {
                                    val screenshotX = searchRegion.offsetX + center.x
                                    val screenshotY = searchRegion.offsetY + center.y
                                    lastMatchX = screenshotX * mapping.screenshotToDisplayX
                                    lastMatchY = screenshotY * mapping.screenshotToDisplayY
                                    matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                                    verboseInfo("任务 ${task.id} OCR命中 x=${lastMatchX.toInt()} y=${lastMatchY.toInt()}")
                                    dispatchClick(lastMatchX, lastMatchY) {
                                        if (generation == runGeneration && isRunning) finishTask(task, true)
                                    }
                                }

                                if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                searchRegion?.release()
                                swBitmap?.recycle()
                            }
                            .addOnFailureListener { error ->
                                recognizer.close()
                                RunLogger.e("任务 ${task.id} OCR识别失败: ${error.message}")
                                if (generation == runGeneration && isRunning) finishTask(task, false)
                                if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                searchRegion?.release()
                                swBitmap?.recycle()
                            }
                    } catch (t: Throwable) {
                        RunLogger.e("任务 ${task.id} OCR执行失败", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                        if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                        searchRegion?.release()
                        swBitmap?.recycle()
                    } finally {
                        hwBitmap?.recycle()
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    RunLogger.e("任务 ${task.id} OCR截图失败，错误码=$errorCode")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun executeRedRegionClick(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return finishTask(task, false)
        val minConfidence = p.threshold.coerceIn(0f, 1f)

        val generation = runGeneration
        verboseInfo("任务 ${task.id} 颜色匹配红色区域，区域=${formatRoi(p.roi)}，阈值=${"%.2f".format(minConfidence)}")

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }

                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var buffer: android.hardware.HardwareBuffer? = null
                    var hwBitmap: Bitmap? = null

                    try {
                        buffer = result.hardwareBuffer
                        hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        if (swBitmap == null) {
                            RunLogger.e("任务 ${task.id} 红色区域截图转换失败")
                            finishTask(task, false)
                            return
                        }

                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, p.roi, mapping, task.id)
                        val match = findRedRegionMatch(searchRegion.bitmap)
                        if (match == null) {
                            finishTask(task, false)
                        } else if (match.confidence < minConfidence) {
                            verboseInfo(
                                "任务 ${task.id} 红色候选置信度=${"%.3f".format(match.confidence)} " +
                                    "面积=${"%.3f".format(match.areaRatio)} " +
                                    "完整=${"%.3f".format(match.fillRatio)} " +
                                    "红度=${"%.3f".format(match.rednessScore)}"
                            )
                            finishTask(task, false)
                        } else {
                            val screenshotX = searchRegion.offsetX + match.center.x
                            val screenshotY = searchRegion.offsetY + match.center.y
                            lastMatchX = screenshotX * mapping.screenshotToDisplayX
                            lastMatchY = screenshotY * mapping.screenshotToDisplayY
                            matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                            verboseInfo(
                                "任务 ${task.id} 红色命中 x=${lastMatchX.toInt()} y=${lastMatchY.toInt()} " +
                                    "score=${"%.3f".format(match.confidence)} " +
                                    "area=${"%.3f".format(match.areaRatio)} " +
                                    "fill=${"%.3f".format(match.fillRatio)} " +
                                    "red=${"%.3f".format(match.rednessScore)}"
                            )
                            dispatchClick(lastMatchX, lastMatchY) {
                                if (generation == runGeneration && isRunning) finishTask(task, true)
                            }
                        }

                        searchRegion?.release()
                        swBitmap?.recycle()
                    } catch (t: Throwable) {
                        RunLogger.e("任务 ${task.id} 红色区域执行失败", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                        searchRegion?.release()
                        swBitmap?.recycle()
                    } finally {
                        hwBitmap?.recycle()
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    RunLogger.e("任务 ${task.id} 红色区域截图失败，错误码=$errorCode")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun findRedRegionMatch(bitmap: Bitmap): RedRegionMatch? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val step = if (width >= 300 || height >= 300) 2 else 1
        val sampleWidth = (width + step - 1) / step
        val sampleHeight = (height + step - 1) / step
        val mask = BooleanArray(sampleWidth * sampleHeight)
        val queue = IntArray(mask.size)
        val redness = FloatArray(mask.size)

        var hasRed = false
        for (sy in 0 until sampleHeight) {
            val y = sy * step
            for (sx in 0 until sampleWidth) {
                val x = sx * step
                val index = sy * sampleWidth + sx
                val redScore = computeRednessScore(bitmap.getPixel(x, y))
                redness[index] = redScore
                if (redScore > 0f) {
                    mask[index] = true
                    hasRed = true
                }
            }
        }
        if (!hasRed) return null

        var bestCount = 0
        var bestMinX = 0
        var bestMaxX = 0
        var bestMinY = 0
        var bestMaxY = 0
        var bestRednessSum = 0f
        val neighbors = intArrayOf(-1, 0, 1, 0, -1)

        for (sy in 0 until sampleHeight) {
            for (sx in 0 until sampleWidth) {
                val startIndex = sy * sampleWidth + sx
                if (!mask[startIndex]) continue

                var head = 0
                var tail = 0
                queue[tail++] = startIndex
                mask[startIndex] = false

                var count = 0
                var minX = sx
                var maxX = sx
                var minY = sy
                var maxY = sy
                var rednessSum = 0f

                while (head < tail) {
                    val index = queue[head++]
                    val cx = index % sampleWidth
                    val cy = index / sampleWidth
                    count += 1
                    rednessSum += redness[index]
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    for (n in 0 until 4) {
                        val nx = cx + neighbors[n]
                        val ny = cy + neighbors[n + 1]
                        if (nx !in 0 until sampleWidth || ny !in 0 until sampleHeight) continue
                        val nextIndex = ny * sampleWidth + nx
                        if (!mask[nextIndex]) continue
                        mask[nextIndex] = false
                        queue[tail++] = nextIndex
                    }
                }

                if (count > bestCount) {
                    bestCount = count
                    bestMinX = minX
                    bestMaxX = maxX
                    bestMinY = minY
                    bestMaxY = maxY
                    bestRednessSum = rednessSum
                }
            }
        }

        if (bestCount < 12) return null

        val bboxWidth = bestMaxX - bestMinX + 1
        val bboxHeight = bestMaxY - bestMinY + 1
        val bboxArea = (bboxWidth * bboxHeight).coerceAtLeast(1)
        val estimatedPixelArea = bestCount * step * step
        val referenceButtonArea = 360f * 60f
        val areaRatio = (estimatedPixelArea / referenceButtonArea).coerceIn(0f, 1f)
        val fillRatio = bestCount.toFloat() / bboxArea.toFloat()
        val rednessScore = (bestRednessSum / bestCount.toFloat()).coerceIn(0f, 1f)
        val compactnessScore = ((fillRatio - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val confidence = (
            areaRatio * 0.45f +
                compactnessScore * 0.25f +
                rednessScore * 0.30f
            ).coerceIn(0f, 1f)

        val centerX = ((bestMinX + bestMaxX + 1) * step / 2f).coerceIn(0f, (width - 1).toFloat())
        val centerY = ((bestMinY + bestMaxY + 1) * step / 2f).coerceIn(0f, (height - 1).toFloat())
        return RedRegionMatch(PointF(centerX, centerY), confidence, areaRatio, fillRatio, rednessScore)
    }

    private fun isRelaxedRed(color: Int): Boolean {
        return computeRednessScore(color) > 0f
    }

    private fun computeRednessScore(color: Int): Float {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        if (r < 95) return 0f
        if (r - g < 20 || r - b < 20) return 0f
        if (g > 185 || b > 185) return 0f

        val redLevel = ((r - 95f) / 160f).coerceIn(0f, 1f)
        val dominance = (((r - maxOf(g, b)) - 20f) / 180f).coerceIn(0f, 1f)
        val saturation = ((255f - (g + b) / 2f) / 255f).coerceIn(0f, 1f)
        return (redLevel * 0.45f + dominance * 0.4f + saturation * 0.15f).coerceIn(0f, 1f)
    }

    private fun buildSearchRegionForOcr(
        swBitmap: Bitmap,
        roi: ROI?,
        mapping: ScreenshotMapping,
        taskId: Int
    ): SearchRegion {
        logScreenshotMapping(taskId, mapping)
        val gameScale = min(swBitmap.width / BASE_W, swBitmap.height / BASE_H)
        val gameWidth = BASE_W * gameScale
        val offsetX = (swBitmap.width - gameWidth) / 2f

        var searchBitmap = swBitmap
        var roiOffsetX = 0f
        var roiOffsetY = 0f
        var ownsBitmap = false
        var debugLeft = 0
        var debugTop = 0
        var debugWidth = swBitmap.width
        var debugHeight = swBitmap.height

        if (roi?.align == "dynamic_avatar_bounds") {
            val topBound = 1254f * gameScale
            val bottomBound = swBitmap.height - (313f * gameScale)
            val safeY = topBound.toInt().coerceAtLeast(0)
            val safeH = (bottomBound - topBound).toInt()
                .coerceAtMost(swBitmap.height - safeY)
                .coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, 0, safeY, swBitmap.width, safeH)
            roiOffsetY = safeY.toFloat()
            ownsBitmap = true
            debugTop = safeY
            debugHeight = safeH
        } else if (roi?.align == "dynamic_filter_bounds") {
            val realCenterY = 1254f * gameScale
            val realCenterX = offsetX + (gameWidth * 0.9f)
            val realW = gameWidth * 0.2f
            val realH = 300f * gameScale
            val safeX = (realCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
            val safeY = (realCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
            roiOffsetX = safeX.toFloat()
            roiOffsetY = safeY.toFloat()
            ownsBitmap = true
            debugLeft = safeX
            debugTop = safeY
            debugWidth = safeW
            debugHeight = safeH
        } else if (roi?.x != null && roi.y != null && roi.w != null && roi.h != null) {
            val (realCenterX, realCenterY) = calculateRealCoordinate(roi.x, roi.y, roi.align)
            val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
            val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
            val realW = roi.w * gameScale
            val realH = roi.h * gameScale
            val safeX = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
            val safeY = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
            roiOffsetX = safeX.toFloat()
            roiOffsetY = safeY.toFloat()
            ownsBitmap = true
            debugLeft = safeX
            debugTop = safeY
            debugWidth = safeW
            debugHeight = safeH
            logRoiCenter(taskId, realCenterX, realCenterY, debugLeft, debugTop, debugWidth, debugHeight, mapping)
        }

        if (debugRoiEnabled) {
            showDebugRoi(
                (debugLeft * mapping.screenshotToDisplayX).toInt(),
                (debugTop * mapping.screenshotToDisplayY).toInt(),
                (debugWidth * mapping.screenshotToDisplayX).toInt(),
                (debugHeight * mapping.screenshotToDisplayY).toInt(),
                1500L
            )
        }

        return SearchRegion(searchBitmap, roiOffsetX, roiOffsetY, ownsBitmap)
    }

    private fun SearchRegion.release() {
        if (ownsBitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun createYellowTextOcrBitmap(source: Bitmap): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val isYellowText = r >= 150 && g >= 110 && b <= 180 && (r - b) >= 40 && (g - b) >= 30
                output[i] = if (isYellowText) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            RunLogger.e("OCR yellow-text preprocess failed", t)
            null
        }
    }

    private fun findOcrTargetCenter(text: Text, targetText: String, matchAnyChinese: Boolean): PointF? {
        if (matchAnyChinese) {
            findAnyChineseCenter(text.textBlocks.flatMap { block -> block.lines })?.let { return it }
            findAnyChineseCenter(text.textBlocks.flatMap { block -> block.lines.flatMap { line -> line.elements } })?.let {
                return it
            }
            return findAnyChineseCenter(text.textBlocks)
        }

        findExactTextCenter(text.textBlocks.flatMap { block -> block.lines.flatMap { line -> line.elements } }, targetText)?.let {
            return it
        }
        findExactTextCenter(text.textBlocks.flatMap { block -> block.lines }, targetText)?.let { return it }
        return findExactTextCenter(text.textBlocks, targetText)
    }

    private fun findAnyChineseCenter(components: List<Any>): PointF? {
        var bestBox: android.graphics.Rect? = null
        var bestArea = -1
        for (component in components) {
            val text = extractOcrText(component)
            val box = extractOcrBoundingBox(component)
            if (!containsChinese(text) || box == null) continue
            val area = box.width() * box.height()
            if (area > bestArea) {
                bestArea = area
                bestBox = box
            }
        }
        val box = bestBox ?: return null
        return PointF(box.exactCenterX(), box.exactCenterY())
    }

    private fun findExactTextCenter(components: List<Any>, targetText: String): PointF? {
        val normalizedTarget = normalizeOcrText(targetText)
        for (component in components) {
            val componentText = normalizeOcrText(extractOcrText(component))
            val box = extractOcrBoundingBox(component)
            if (box != null && componentText.contains(normalizedTarget)) {
                return PointF(box.exactCenterX(), box.exactCenterY())
            }
        }
        return null
    }

    private fun extractOcrText(component: Any): String = when (component) {
        is Text.TextBlock -> component.text
        is Text.Line -> component.text
        is Text.Element -> component.text
        else -> ""
    }

    private fun extractOcrBoundingBox(component: Any): android.graphics.Rect? = when (component) {
        is Text.TextBlock -> component.boundingBox
        is Text.Line -> component.boundingBox
        is Text.Element -> component.boundingBox
        else -> null
    }

    private fun containsChinese(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.any { it in '\u4E00'..'\u9FFF' }
    }

    private fun normalizeOcrText(value: String): String =
        value.filterNot { it.isWhitespace() }

    private fun resolveSuccessTaskId(task: DailyTask): Int {
        val p = task.params ?: return task.on_success
        val varName = p.branch_var ?: return task.on_success
        val routes = p.branch_routes ?: return task.on_success
        val value = variables[varName] ?: return task.on_success
        return routes[value] ?: task.on_success
    }

    private fun loadTemplateFromAssets(fileName: String): Bitmap? {
        templateCache.get(fileName)?.let {
            if (!it.isRecycled) {
                verboseInfo("模板缓存命中 $fileName")
                return it
            }
            templateCache.remove(fileName)
        }
        return try {
            val bitmap = service.assets.open(fileName).use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                templateCache.put(fileName, bitmap)
                verboseInfo("模板已加载 $fileName ${bitmap.width}x${bitmap.height}")
            } else {
                RunLogger.e("模板解码失败 $fileName")
            }
            bitmap
        } catch (t: Throwable) {
            RunLogger.e("模板打开失败 $fileName", t)
            null
        }
    }

    private fun executeMatchTemplate(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val templateName = p.template_name ?: return finishTask(task, false)
        val generation = runGeneration
        verboseInfo("任务 ${task.id} 匹配模板=$templateName，区域=${formatRoi(p.roi)}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return finishTask(task, false)

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }
                    var swBitmap: Bitmap? = null
                    var searchBitmap: Bitmap? = null
                    var scaledTemplate: Bitmap? = null
                    var ownsScaledTemplate = false
                    var buffer: android.hardware.HardwareBuffer? = null
                    var hwBitmap: Bitmap? = null
                    try {
                        buffer = result.hardwareBuffer
                        hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            ?: return finishTask(task, false)
                        val mapping = buildScreenshotMapping(swBitmap)
                        logScreenshotMapping(task.id, mapping)
                        val gameScale = min(swBitmap.width / BASE_W, swBitmap.height / BASE_H)
                        val gameWidth = BASE_W * gameScale
                        val offsetX = (swBitmap.width - gameWidth) / 2f
                        val rawTemplate = loadTemplateFromAssets(templateName)
                            ?: return finishTask(task, false)
                        val scaledWidth = (rawTemplate.width * gameScale).toInt().coerceAtLeast(1)
                        val scaledHeight = (rawTemplate.height * gameScale).toInt().coerceAtLeast(1)
                        scaledTemplate = if (scaledWidth == rawTemplate.width && scaledHeight == rawTemplate.height) {
                            rawTemplate
                        } else {
                            ownsScaledTemplate = true
                            Bitmap.createScaledBitmap(rawTemplate, scaledWidth, scaledHeight, true)
                        }

                        searchBitmap = swBitmap
                        var roiOffsetX = 0f
                        var roiOffsetY = 0f
                        var debugLeft = 0
                        var debugTop = 0
                        var debugWidth = swBitmap.width
                        var debugHeight = swBitmap.height

                        if (p.roi?.align == "dynamic_avatar_bounds") {
                            val topBound = 1254f * gameScale
                            val bottomBound = swBitmap.height - (313f * gameScale)
                            val safeY = topBound.toInt().coerceAtLeast(0)
                            val safeH = (bottomBound - topBound).toInt()
                                .coerceAtMost(swBitmap.height - safeY)
                                .coerceAtLeast(1)
                            searchBitmap = Bitmap.createBitmap(swBitmap, 0, safeY, swBitmap.width, safeH)
                            roiOffsetY = safeY.toFloat()
                            debugTop = safeY
                            debugHeight = safeH
                        } else if (p.roi?.align == "dynamic_filter_bounds") {
                            val realCenterY = 1254f * gameScale
                            val realCenterX = offsetX + (gameWidth * 0.9f)
                            val realW = gameWidth * 0.2f
                            val realH = 300f * gameScale
                            val safeX = (realCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
                            val safeY = (realCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
                            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
                            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
                            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
                            roiOffsetX = safeX.toFloat()
                            roiOffsetY = safeY.toFloat()
                            debugLeft = safeX
                            debugTop = safeY
                            debugWidth = safeW
                            debugHeight = safeH
                        } else if (p.roi?.x != null && p.roi.y != null && p.roi.w != null && p.roi.h != null) {
                            val (realCenterX, realCenterY) =
                                calculateRealCoordinate(p.roi.x, p.roi.y, p.roi.align)
                            val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
                            val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
                            val realW = p.roi.w * gameScale
                            val realH = p.roi.h * gameScale
                            val safeX = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
                            val safeY = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
                            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
                            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
                            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
                            roiOffsetX = safeX.toFloat()
                            roiOffsetY = safeY.toFloat()
                            debugLeft = safeX
                            debugTop = safeY
                            debugWidth = safeW
                            debugHeight = safeH
                            logRoiCenter(task.id, realCenterX, realCenterY, debugLeft, debugTop, debugWidth, debugHeight, mapping)
                        }

                        if (debugRoiEnabled) {
                            showDebugRoi(
                                (debugLeft * mapping.screenshotToDisplayX).toInt(),
                                (debugTop * mapping.screenshotToDisplayY).toInt(),
                                (debugWidth * mapping.screenshotToDisplayX).toInt(),
                                (debugHeight * mapping.screenshotToDisplayY).toInt(),
                                1500L
                            )
                        }

                        if (searchBitmap.width < scaledTemplate.width || searchBitmap.height < scaledTemplate.height) {
                            return finishTask(task, false)
                        }

                        val matchLoc = matchTemplate(searchBitmap, scaledTemplate, p.threshold)
                        if (matchLoc != null) {
                            val screenshotMatchX = matchLoc.x + roiOffsetX
                            val screenshotMatchY = matchLoc.y + roiOffsetY
                            lastMatchX = screenshotMatchX * mapping.screenshotToDisplayX
                            lastMatchY = screenshotMatchY * mapping.screenshotToDisplayY
                            matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                            verboseInfo("任务 ${task.id} 匹配成功 x=${lastMatchX.toInt()} y=${lastMatchY.toInt()}")
                            if (p.click == 1) {
                                dispatchClick(lastMatchX, lastMatchY) {
                                    if (generation == runGeneration && isRunning) finishTask(task, true)
                                }
                            } else {
                                finishTask(task, true)
                            }
                        } else {
                            if (verboseLoggingEnabled) {
                                RunLogger.e("任务 ${task.id} 未匹配到 $templateName")
                            }
                            finishTask(task, false)
                        }
                    } catch (t: Throwable) {
                        RunLogger.e("任务 ${task.id} 匹配崩溃，模板=$templateName", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                    } finally {
                        hwBitmap?.recycle()
                        buffer?.close()
                        if (ownsScaledTemplate) scaledTemplate?.recycle()
                        if (searchBitmap != null && searchBitmap !== swBitmap) searchBitmap.recycle()
                        swBitmap?.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    RunLogger.e("任务 ${task.id} 截图失败，错误码=$errorCode，模板=$templateName")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Pair(bounds.width().toFloat(), bounds.height().toFloat())
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            Pair(point.x.toFloat(), point.y.toFloat())
        }
    }

    private fun calculateRealCoordinate(baseX: Float, baseY: Float, align: String): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val gameWidth = BASE_W * gameScale
        val gameHeight = BASE_H * gameScale
        val offsetX = (screenWidth - gameWidth) / 2f
        val offsetY = (screenHeight - gameHeight) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val realX = offsetX + (baseX * gameScale)
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (screenHeight / BASE_H)
            "top" -> statusBarHeight + (baseY * gameScale)
            "bottom" -> screenHeight - ((BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }
        return Pair(realX, realY)
    }

    private fun getRawStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 40
    }

    private fun logDisplayMetrics(tag: String) {
        val metrics = service.resources.displayMetrics
        val (realWidth, realHeight) = getRealScreenSize()
        val rawStatusBarHeight = getRawStatusBarHeight()
        val scaledStatusBarHeight = rawStatusBarHeight / 2f
        verboseInfo("显示[$tag] 实际=${realWidth.toInt()}x${realHeight.toInt()} 资源=${metrics.widthPixels}x${metrics.heightPixels}")
        verboseInfo("显示[$tag] 状态栏原始=$rawStatusBarHeight 缩放后=${scaledStatusBarHeight.toInt()}")
    }

    private fun buildScreenshotMapping(bitmap: Bitmap): ScreenshotMapping {
        val (displayWidth, displayHeight) = getRealScreenSize()
        val stx = displayWidth / bitmap.width.toFloat()
        val sty = displayHeight / bitmap.height.toFloat()
        val dtx = bitmap.width.toFloat() / displayWidth
        val dty = bitmap.height.toFloat() / displayHeight
        return ScreenshotMapping(
            stx,
            sty,
            dtx,
            dty,
            (displayWidth >= displayHeight) != (bitmap.width >= bitmap.height),
            abs(dtx - dty) > 0.02f,
            displayWidth,
            displayHeight,
            bitmap.width,
            bitmap.height
        )
    }

    private fun logScreenshotMapping(taskId: Int, mapping: ScreenshotMapping) {
        verboseInfo("任务 $taskId 截图缩放 x=${"%.4f".format(mapping.screenshotToDisplayX)} y=${"%.4f".format(mapping.screenshotToDisplayY)} 实际=${mapping.displayWidth.toInt()}x${mapping.displayHeight.toInt()} 截图=${mapping.screenshotWidth}x${mapping.screenshotHeight}")
        if (mapping.orientationMismatch) RunLogger.e("任务 $taskId 截图方向不一致")
        if (mapping.nonUniformScale) {
            RunLogger.e("任务 $taskId 截图缩放不一致 x=${"%.4f".format(mapping.displayToScreenshotX)} y=${"%.4f".format(mapping.displayToScreenshotY)}")
        }
    }

    private fun logRoiCenter(
        taskId: Int,
        realCenterX: Float,
        realCenterY: Float,
        debugLeft: Int,
        debugTop: Int,
        debugWidth: Int,
        debugHeight: Int,
        mapping: ScreenshotMapping
    ) {
        val debugCenterX = (debugLeft + debugWidth / 2f) * mapping.screenshotToDisplayX
        val debugCenterY = (debugTop + debugHeight / 2f) * mapping.screenshotToDisplayY
        verboseInfo("任务 $taskId ROI中心=(${realCenterX.toInt()},${realCenterY.toInt()}) 调试中心=(${debugCenterX.toInt()},${debugCenterY.toInt()})")
    }

    private fun showDebugRoi(left: Int, top: Int, width: Int, height: Int, holdMs: Long) {
        handler.post {
            clearDebugRoi()
            val borderView = View(service).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(4, Color.RED)
                }
            }
            val params = WindowManager.LayoutParams(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = left
                y = top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            try {
                windowManager.addView(borderView, params)
                debugRoiView = borderView
                verboseInfo("显示调试 ROI：left=$left，top=$top，width=$width，height=$height")
            } catch (t: Throwable) {
                RunLogger.e("显示调试 ROI 失败", t)
                clearDebugRoi()
                return@post
            }
            handler.postDelayed({
                if (debugRoiView === borderView) clearDebugRoi()
            }, holdMs)
        }
    }

    private fun clearDebugRoi() {
        debugRoiView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            } finally {
                debugRoiView = null
            }
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun formatRoi(roi: ROI?): String {
        if (roi == null) return "空"
        return "x=${roi.x},y=${roi.y},w=${roi.w},h=${roi.h},align=${roi.align}"
    }

    private inline fun <T> Mat.use(block: (Mat) -> T): T {
        try {
            return block(this)
        } finally {
            release()
        }
    }

    private fun matchTemplate(screenBitmap: Bitmap, templateBitmap: Bitmap, threshold: Float): PointF? {
        val ownsSourceBitmap = screenBitmap.config != Bitmap.Config.ARGB_8888
        val sourceBitmap = if (ownsSourceBitmap) screenBitmap.copy(Bitmap.Config.ARGB_8888, false) else screenBitmap
        try {
            return Mat().use { srcMat ->
                Mat().use { tmplMat ->
                    Mat().use { resultMat ->
                        Utils.bitmapToMat(sourceBitmap, srcMat)
                        Utils.bitmapToMat(templateBitmap, tmplMat)
                        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
                        val mmLoc = Core.minMaxLoc(resultMat)
                        verboseInfo("匹配分数=${"%.4f".format(mmLoc.maxVal)} 阈值=$threshold")
                        if (mmLoc.maxVal >= threshold) {
                            PointF(
                                (mmLoc.maxLoc.x + templateBitmap.width / 2.0).toFloat(),
                                (mmLoc.maxLoc.y + templateBitmap.height / 2.0).toFloat()
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        } finally {
            if (ownsSourceBitmap) sourceBitmap.recycle()
        }
    }
}
