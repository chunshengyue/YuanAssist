package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.utils.RunLogger
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.min

class AutoTaskEngine(private val service: AccessibilityService) {

    var isRunning = false
    private var currentTaskPlan: DailyTaskPlan? = null
    private var currentTaskId: Int = -1

    // 🔴 1. 修改回呼的型別，加入 String 類型的錯誤訊息
    private var onPlanCompleted: ((Boolean, String) -> Unit)? = null
    private var customActionHandler: ((DailyTask, onSuccess: () -> Unit, onFail: () -> Unit) -> Unit)? =
        null
    private val screenWidth = service.resources.displayMetrics.widthPixels
    private val screenHeight = service.resources.displayMetrics.heightPixels

    var lastMatchX: Float = 0f
    var lastMatchY: Float = 0f

    private val handler = Handler(Looper.getMainLooper())

    // ==========================================
    // 啟動與流轉控制
    // ==========================================
    // 🔴 2. 修改 startPlan 接收的參數
    fun startPlan(
        plan: DailyTaskPlan,
        onCompleted: (Boolean, String) -> Unit,
        onCustomAction: ((DailyTask, () -> Unit, () -> Unit) -> Unit)? = null
    ) {
        RunLogger.i("--- 引擎启动：开始执行任务流 ---")
        currentTaskPlan = plan
        currentTaskId = plan.start_task_id
        isRunning = true
        onPlanCompleted = onCompleted
        customActionHandler = onCustomAction
        executeNextTask()
    }

    fun stop() {
        isRunning = false
        onPlanCompleted?.invoke(false, "已手动停止")
    }

    private fun executeNextTask() {
        if (!isRunning) return

        val task = currentTaskPlan?.tasks?.find { it.id == currentTaskId }
        if (task == null) {
            isRunning = false
            // 找不到任務時給出明確提示
            onPlanCompleted?.invoke(false, "找不到任务 ID: $currentTaskId")
            RunLogger.e("找不到任务 ID: $currentTaskId")
            return
        }
        RunLogger.i("准备执行步骤 ${task.id} [${task.action}]")
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            when (task.action) {
                "CLICK" -> executeClick(task)
                "SWIPE" -> executeSwipe(task)
                "BACK" -> executeGlobalBack(task)
                "CLICK_LAST_MATCH", "CLICK_LAST_OCR" -> executeContextClick(task)
                "MATCH_TEMPLATE" -> executeMatchTemplate(task)
                else -> {
                    if (customActionHandler != null) {
                        customActionHandler!!.invoke(
                            task,
                            { finishTask(task, true) },
                            { finishTask(task, false) })
                    } else {
                        finishTask(task, false)
                    }
                }
            }
        }, task.delay)
    }

    fun finishTask(task: DailyTask, isSuccess: Boolean) {
        if (!isSuccess) {
            if (task.on_fail == -1) {
                isRunning = false
                // 🔴 3. 核心：在這裡組合出具體的錯誤訊息
                val errorMsg = when (task.action) {
                    "MATCH_TEMPLATE" -> "找不到图片: ${task.params?.template_name}"
                    "CLICK_DYNAMIC_BUTTON" -> "找不到动态按钮: ${task.params?.button_name}"
                    "CLICK", "SWIPE" -> "执行 ${task.action} 动作失败"
                    else -> "未知错误: ${task.action}"
                }
                RunLogger.e("任务中止 -> $errorMsg") // 🔴 记录详细错误
                onPlanCompleted?.invoke(false, errorMsg)
                return
            }
            RunLogger.e("步骤 ${task.id} 失败，跳转到容错步骤: ${task.on_fail}")
            currentTaskId = task.on_fail
        } else {
            RunLogger.i("步骤 ${task.id} 执行成功") // 🟢 记录成功
            if (task.on_success == -1) {
                isRunning = false
                RunLogger.i("--- 引擎执行完毕 ---")
                onPlanCompleted?.invoke(true, "全部执行完毕")
                return
            }
            currentTaskId = task.on_success
        }
        executeNextTask()
    }

    // ==========================================
    // 座標計算與基礎動作
    // ==========================================
    private fun calculateRealCoordinate(
        baseX: Float,
        baseY: Float,
        align: String
    ): Pair<Float, Float> {
        val BASE_W = 1080f
        val BASE_H = 1920f

        // 1. 动态计算真实缩放比例 (大于16:9按宽，小于16:9按高)
        val widthRatio = screenWidth / BASE_W
        val heightRatio = screenHeight / BASE_H
        val gameScale = min(widthRatio, heightRatio)

        // 2. 计算游戏画面的真实物理尺寸
        val gameWidth = BASE_W * gameScale
        val gameHeight = BASE_H * gameScale

        // 3. 计算左右黑边和上下黑边
        val offsetX = (screenWidth - gameWidth) / 2f
        val offsetY = (screenHeight - gameHeight) / 2f

        // 4. 计算基础 X 坐标：左侧黑边 + (设计稿 X * 缩放比例)
        val realX = offsetX + (baseX * gameScale)

        var statusBarHeight = 0
        val resourceId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) statusBarHeight = service.resources.getDimensionPixelSize(resourceId)

        // 5. 根据对齐模式计算 Y 坐标
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (screenHeight / BASE_H)
            "center" -> offsetY + (baseY * gameScale)     // 居中元素：受黑边影响
            // 🔴 彻底修正 top：不考虑 offsetY，完全贴顶向下计算 (仅保留状态栏适配)
            "top" -> statusBarHeight + (baseY * gameScale)
            // 🔴 彻底修正 bottom：不考虑 offsetY，完全贴底向上推算
            "bottom" -> screenHeight - ((BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }

        return Pair(realX, realY)
    }

    private fun executeClick(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        if (p.x == null || p.y == null) return finishTask(task, false)
        val (realX, realY) = calculateRealCoordinate(p.x, p.y, p.align)
        dispatchClick(realX, realY) { finishTask(task, true) }
    }

    private fun executeSwipe(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        if (p.startX == null || p.startY == null || p.endX == null || p.endY == null) return finishTask(
            task,
            false
        )
        val (rStartX, rStartY) = calculateRealCoordinate(p.startX, p.startY, p.align)
        val (rEndX, rEndY) = calculateRealCoordinate(p.endX, p.endY, p.align)
        val duration = p.duration ?: 300L

        val path = Path().apply { moveTo(rStartX, rStartY); lineTo(rEndX, rEndY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                finishTask(task, true)
            }
        }, null)
    }

    private fun executeContextClick(task: DailyTask) {
        if (lastMatchX == 0f && lastMatchY == 0f) return finishTask(task, false)
        dispatchClick(lastMatchX, lastMatchY) { finishTask(task, true) }
    }

    private fun executeGlobalBack(task: DailyTask) {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        finishTask(task, success)
    }

    fun dispatchClick(x: Float, y: Float, onComplete: () -> Unit) {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete()
            }
        }, null)
    }

    // ==========================================
    // OpenCV 圖像匹配
    // ==========================================
    private fun loadTemplateFromAssets(fileName: String): Bitmap? {
        return try {
            BitmapFactory.decodeStream(service.assets.open(fileName))
        } catch (e: Exception) {
            null
        }
    }

    private fun executeMatchTemplate(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val templateName = p.template_name ?: return finishTask(task, false)
        val threshold = p.threshold

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace

                        // 1. 立即包裝並拷貝，徹底脫離硬體 Buffer 依賴
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                        val swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                        // 2. 拷貝完成後立即釋放系統資源，記憶體佔用最小化
                        hwBitmap?.recycle()
                        buffer.close()

                        if (swBitmap != null) {
                            // 1. 核心：計算真實的畫面縮放比例 (取寬高比的最小值)
                            val widthRatio = swBitmap.width / 1080f
                            val heightRatio = swBitmap.height / 1920f
                            val gameScale = min(widthRatio, heightRatio)

                            // 遊戲畫面在螢幕上的實際物理寬度
                            val gameWidth = 1080f * gameScale

                            // 只需要計算左右的黑邊 (平板會有左右黑邊，長手機 offsetX 為 0)
                            val offsetX = (swBitmap.width - gameWidth) / 2f

                            val rawTemplate = loadTemplateFromAssets(templateName)
                            if (rawTemplate == null) {
                                swBitmap.recycle(); return finishTask(task, false)
                            }

                            // 縮放模板圖片
                            val scaledTmplW =
                                (rawTemplate.width * gameScale).toInt().coerceAtLeast(1)
                            val scaledTmplH =
                                (rawTemplate.height * gameScale).toInt().coerceAtLeast(1)
                            val scaledTemplate = Bitmap.createScaledBitmap(
                                rawTemplate,
                                scaledTmplW,
                                scaledTmplH,
                                true
                            )
                            if (scaledTemplate !== rawTemplate) rawTemplate.recycle()

                            var searchBitmap = swBitmap
                            var roiOffsetX = 0f
                            var roiOffsetY = 0f

                            // 🔴 2. 尋找頭像區域 (完全無 offsetY 污染)
                            if (p.roi?.align == "dynamic_avatar_bounds") {
                                // 上邊界：從最頂部往下算 1254 乘以縮放比例
                                val topBound = 1254f * gameScale
                                // 下邊界：從最底部往上算 313 乘以縮放比例
                                val bottomBound = swBitmap.height - (313f * gameScale)

                                val safeY = topBound.toInt().coerceAtLeast(0)
                                val safeH = (bottomBound - topBound).toInt()
                                    .coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)

                                searchBitmap =
                                    Bitmap.createBitmap(swBitmap, 0, safeY, swBitmap.width, safeH)
                                roiOffsetX = 0f
                                roiOffsetY = safeY.toFloat()
                            }
                            // 🔴 3. 尋找篩選按鈕 (完全無 offsetY 污染)
                            else if (p.roi?.align == "dynamic_filter_bounds") {
                                // Y中心 = 從最頂部往下算 1254 (與頭像上邊緣保持一致)
                                val realCenterY = 1254f * gameScale

                                // X中心 = 左側黑邊 + 遊戲內部 90% 的位置
                                val realCenterX = offsetX + (gameWidth * 0.9f)
                                // 寬度 = 遊戲實際寬度的 20%
                                val realW = gameWidth * 0.2f
                                // 縱向高度約 300 乘以真實縮放
                                val realH = 300f * gameScale

                                // 換算為左上角起點
                                val realLeft = realCenterX - (realW / 2f)
                                val realTop = realCenterY - (realH / 2f)

                                // 邊界安全校驗 (防止越界崩潰)
                                val safeX = realLeft.toInt().coerceIn(0, swBitmap.width - 1)
                                val safeY = realTop.toInt().coerceIn(0, swBitmap.height - 1)
                                val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX)
                                    .coerceAtLeast(1)
                                val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY)
                                    .coerceAtLeast(1)

                                searchBitmap =
                                    Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
                                roiOffsetX = safeX.toFloat()
                                roiOffsetY = safeY.toFloat()
                            } else if (p.roi != null && p.roi.x != null && p.roi.y != null && p.roi.w != null && p.roi.h != null) {
                                val (realCenterX, realCenterY) = calculateRealCoordinate(
                                    p.roi.x,
                                    p.roi.y,
                                    p.roi.align
                                )
                                val realW = p.roi.w * gameScale
                                val realH = p.roi.h * gameScale

                                val realLeft = realCenterX - (realW / 2f)
                                val realTop = realCenterY - (realH / 2f)

                                val safeX = realLeft.toInt().coerceIn(0, swBitmap.width - 1)
                                val safeY = realTop.toInt().coerceIn(0, swBitmap.height - 1)
                                val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX)
                                    .coerceAtLeast(1)
                                val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY)
                                    .coerceAtLeast(1)

                                searchBitmap =
                                    Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
                                roiOffsetX = safeX.toFloat()
                                roiOffsetY = safeY.toFloat()
                            }

                            // 5. OpenCV 匹配執行
                            if (searchBitmap.width >= scaledTemplate.width && searchBitmap.height >= scaledTemplate.height) {
                                val matchLoc =
                                    matchTemplate(searchBitmap, scaledTemplate, threshold)
                                if (matchLoc != null) {
                                    lastMatchX = matchLoc.x + roiOffsetX
                                    lastMatchY = matchLoc.y + roiOffsetY
                                    finishTask(task, true)
                                } else {
                                    finishTask(task, false)
                                }
                            } else {
                                finishTask(task, false)
                            }

                            // 6. 徹底大清掃，釋放所有記憶體
                            scaledTemplate.recycle()
                            if (searchBitmap !== swBitmap) searchBitmap.recycle()
                            swBitmap.recycle()
                        } else {
                            finishTask(task, false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        finishTask(task, false)
                    }
                })
        } else {
            finishTask(task, false)
        }
    }

    // 辅助函数：专门用来保存排查图
    private fun matchTemplate(
        screenBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Float
    ): PointF? {
        val srcMat = Mat();
        val tmplMat = Mat()
        val swBitmap = screenBitmap.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(swBitmap, srcMat)
        Utils.bitmapToMat(templateBitmap, tmplMat)
        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
        val resultMat = Mat()
        Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
        val mmLoc = Core.minMaxLoc(resultMat)
        srcMat.release(); tmplMat.release(); resultMat.release(); swBitmap.recycle()

        if (mmLoc.maxVal >= threshold) {
            val x = mmLoc.maxLoc.x + templateBitmap.width / 2.0
            val y = mmLoc.maxLoc.y + templateBitmap.height / 2.0
            return PointF(x.toFloat(), y.toFloat())
        }
        return null
    }
    // ==========================================
    // 调试辅助：保存 Bitmap 到本地
    // ==========================================
    // 👇 將這整個函數刪除
    /*
    private fun saveDebugImage(bitmap: Bitmap, prefix: String) {
        try {
            // 保存在 Android/data/com.example.gameassist/files/Pictures/ 目录下
            val dir = service.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val fileName = "${prefix}_${System.currentTimeMillis()}.png"
            val file = java.io.File(dir, fileName)

            java.io.FileOutputStream(file).use { out ->
                // 使用 100% 质量的 PNG 保存，保证不失真
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            RunLogger.i("【排查】已保存ROI截图: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            RunLogger.e("保存调试截图失败: ${e.message}")
        }
    }
    */
}