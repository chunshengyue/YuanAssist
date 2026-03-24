package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Display
import com.example.yuanassist.utils.RunLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class InventoryStitchEngine(private val service: AccessibilityService) {

    private data class TextRow(
        val text: String,
        val centerY: Float,
        val top: Int,
        val bottom: Int
    )

    private data class PreprocessedImage(
        val bitmap: Bitmap,
        val coordinateScaleBack: Float
    )

    private data class TemplateSpec(
        val templateMat: Mat,
        val centerY: Float,
        val top: Int,
        val bottom: Int
    )

    private data class PendingFrame(
        val frameIndex: Int,
        val bitmap: Bitmap,
        val lastRow: TextRow,
        val templateSpec: TemplateSpec,
        val startY: Int
    )

    private data class MatchResult(
        val score: Double,
        val acceptedThreshold: Double,
        val matchCenterY: Float,
        val matchedRow: TextRow,
        val searchHeight: Int
    )

    companion object {
        private const val INITIAL_CAPTURE_DELAY_MS = 1000L
        private const val AFTER_SWIPE_DELAY_MS = 2500L
        private const val SWIPE_DURATION_MS = 500L

        private const val TOP_CROP_BASE = 455
        private const val TOP_CROP_EXTRA = 70
        private const val BOTTOM_CROP_BASE = 385

        private const val MIN_CHINESE_CHARS_PER_LINE = 3
        private const val ROW_MERGE_TOLERANCE_PX = 4f
        private const val TEMPLATE_HEIGHT_PX = 50
        private const val DEBUG_LINE_WIDTH = 4f
        private const val OCR_UPSCALE_FACTOR = 3.0
        private const val UNCHANGED_DIFF_THRESHOLD = 3.0
        private const val LOW_SCORE_FULL_SEARCH_THRESHOLD = 0.8

        private const val MATCH_THRESHOLD_START = 0.9
        private const val MATCH_THRESHOLD_END = 0.1
        private const val MATCH_THRESHOLD_STEP = 0.1

        private val YELLOW_LOWER_BOUND = Scalar(12.0, 50.0, 90.0)
        private val YELLOW_UPPER_BOUND = Scalar(42.0, 255.0, 255.0)
    }

    private val handler = Handler(Looper.getMainLooper())

    var isRunning = false
        private set

    private var onStatusUpdate: ((String) -> Unit)? = null
    private var onCompleted: ((Boolean) -> Unit)? = null

    private var pendingFrame: PendingFrame? = null
    private var stitchedBitmap: Bitmap? = null
    private var nextFrameIndex = 1

    private val screenWidth = service.resources.displayMetrics.widthPixels
    private val screenHeight = service.resources.displayMetrics.heightPixels

    fun startStitching(
        onStatusUpdate: (String) -> Unit,
        onCompleted: (Boolean) -> Unit
    ) {
        if (isRunning) return

        isRunning = true
        this.onStatusUpdate = onStatusUpdate
        this.onCompleted = onCompleted
        resetState()

        RunLogger.i("开始物品拼接")
        onStatusUpdate("正在截图...")

        handler.postDelayed(
            { captureNextFrame() },
            INITIAL_CAPTURE_DELAY_MS
        )
    }

    fun stop() {
        if (!isRunning) return

        isRunning = false
        releaseState()
        RunLogger.i("物品拼接已停止")
        onStatusUpdate?.invoke("已停止")
        onCompleted?.invoke(false)
    }

    private fun resetState() {
        releaseState()
        nextFrameIndex = 1
    }

    private fun releaseState() {
        pendingFrame?.templateSpec?.templateMat?.release()
        pendingFrame?.bitmap?.recycle()
        pendingFrame = null
        stitchedBitmap?.recycle()
        stitchedBitmap = null
    }

    private fun captureNextFrame() {
        if (!isRunning) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            handleError("当前安卓版本不支持系统截图 API")
            return
        }

        onStatusUpdate?.invoke("截图处理中 ($nextFrameIndex)...")

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning) return

                    val buffer = result.hardwareBuffer
                    val colorSpace = result.colorSpace
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)

                    hardwareBitmap?.recycle()
                    buffer.close()

                    if (softwareBitmap != null) {
                        processScreenshot(softwareBitmap, nextFrameIndex)
                    } else {
                        handleError("获取截图 Bitmap 失败")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    handleError("无障碍截图失败: $errorCode")
                }
            }
        )
    }

    private fun processScreenshot(bitmap: Bitmap, frameIndex: Int) {
        try {
            val scale = calculateScale(bitmap)
            val croppedBitmap = cropInventoryArea(bitmap, scale)
            val previousPending = pendingFrame

            if (previousPending != null && isFrameAlmostUnchanged(previousPending.bitmap, croppedBitmap)) {
                RunLogger.i("检测到未变化帧，帧=$frameIndex，结束拼接")
                croppedBitmap.recycle()
                appendPendingFrameSegment(previousPending, previousPending.bitmap.height)
                saveStitchedImage()
                return
            }

            detectRowsWithMlKit(croppedBitmap, scale, frameIndex)
        } catch (e: Exception) {
            e.printStackTrace()
            handleError("处理截图发生异常: ${e.message}")
        }
    }

    private fun calculateScale(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val longSide = max(width, height).toFloat()
        val shortSide = min(width, height).toFloat()
        return if ((longSide / 1920f) > (shortSide / 1080f)) {
            shortSide / 1080f
        } else {
            longSide / 1920f
        }
    }

    private fun cropInventoryArea(bitmap: Bitmap, scale: Float): Bitmap {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val topCrop = (TOP_CROP_BASE * scale + TOP_CROP_EXTRA).toInt()
        val bottomCrop = (BOTTOM_CROP_BASE * scale).toInt()
        val safeTopCrop = topCrop.coerceIn(0, sourceHeight - 1)
        val croppedHeight = (sourceHeight - safeTopCrop - bottomCrop).coerceAtLeast(1)
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, safeTopCrop, sourceWidth, croppedHeight)

        bitmap.recycle()

        RunLogger.i(
            "裁剪帧：原图=${sourceWidth}x${sourceHeight}，缩放=%.3f，顶部=%d，底部=%d，结果=%dx%d".format(
                scale,
                safeTopCrop,
                bottomCrop,
                croppedBitmap.width,
                croppedBitmap.height
            )
        )

        return croppedBitmap
    }

    private fun detectRowsWithMlKit(
        croppedBitmap: Bitmap,
        scale: Float,
        frameIndex: Int
    ) {
        if (!isRunning) {
            croppedBitmap.recycle()
            return
        }

        onStatusUpdate?.invoke("ML Kit 识别文字行 ($frameIndex)...")

        val preprocessedImage = preprocessBitmapForMlKit(croppedBitmap)
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        val inputImage = InputImage.fromBitmap(preprocessedImage.bitmap, 0)

        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                try {
                    if (!isRunning) {
                        recognizer.close()
                        preprocessedImage.bitmap.recycle()
                        croppedBitmap.recycle()
                        return@addOnSuccessListener
                    }

                    logRecognizedLines(frameIndex, text, preprocessedImage.coordinateScaleBack)
                    val rows = mergeRows(
                        extractCandidateRows(text, preprocessedImage.coordinateScaleBack)
                    )
                    if (rows.isEmpty()) {
                        throw IllegalStateException("第 $frameIndex 张图没有找到满足条件的文字行")
                    }

                    val templateSpec = buildTemplateSpec(rows, preprocessedImage)
                    val previousPending = pendingFrame

                    if (previousPending == null) {
                        logMergedRows(frameIndex, rows)
                        RunLogger.i(
                            "帧 $frameIndex 模板：中心Y=%.1f，顶部=%d，底部=%d".format(
                                templateSpec.centerY,
                                templateSpec.top,
                                templateSpec.bottom
                            )
                        )

                        pendingFrame = PendingFrame(
                            frameIndex = frameIndex,
                            bitmap = croppedBitmap,
                            lastRow = rows.last(),
                            templateSpec = templateSpec,
                            startY = 0
                        )

                        preprocessedImage.bitmap.recycle()
                        recognizer.close()

                        nextFrameIndex = frameIndex + 1
                        onStatusUpdate?.invoke("首帧完成，滑动下一张...")
                        performSwipeAndContinue()
                        return@addOnSuccessListener
                    }

                    val matchResult = matchPreviousTemplate(
                        previousPending = previousPending,
                        currentPreprocessed = preprocessedImage,
                        currentRows = rows
                    )
                    logMergedRows(frameIndex, rows)
                    RunLogger.i(
                        "帧 $frameIndex 匹配：分数=%.4f，采用阈值=%.1f，匹配中心Y=%.1f，匹配行顶部=%d，匹配行底部=%d，搜索高度=%d".format(
                            matchResult.score,
                            matchResult.acceptedThreshold,
                            matchResult.matchCenterY,
                            matchResult.matchedRow.top,
                            matchResult.matchedRow.bottom,
                            matchResult.searchHeight
                        )
                    )

                    appendPendingFrameSegment(previousPending, previousPending.lastRow.bottom)
                    previousPending.templateSpec.templateMat.release()
                    previousPending.bitmap.recycle()

                    pendingFrame = PendingFrame(
                        frameIndex = frameIndex,
                        bitmap = croppedBitmap,
                        lastRow = rows.last(),
                        templateSpec = templateSpec,
                        startY = matchResult.matchedRow.bottom
                    )

                    preprocessedImage.bitmap.recycle()
                    recognizer.close()

                    nextFrameIndex = frameIndex + 1
                    onStatusUpdate?.invoke("第 $frameIndex 张图处理完成，继续滑动...")
                    performSwipeAndContinue()
                } catch (e: Exception) {
                    recognizer.close()
                    preprocessedImage.bitmap.recycle()
                    croppedBitmap.recycle()
                    e.printStackTrace()
                    handleError("处理第 $frameIndex 张图失败: ${e.message}")
                }
            }
            .addOnFailureListener { error ->
                recognizer.close()
                preprocessedImage.bitmap.recycle()
                croppedBitmap.recycle()
                handleError("ML Kit 识别失败: ${error.message}")
            }
    }

    private fun preprocessBitmapForMlKit(sourceBitmap: Bitmap): PreprocessedImage {
        val srcMat = Mat()
        val rgbMat = Mat()
        val resizedMat = Mat()
        val hsvMat = Mat()
        val yellowMask = Mat()
        val refinedMask = Mat()
        val invertedMask = Mat()
        val rgbaMat = Mat()
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 3.0))
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))

        return try {
            Utils.bitmapToMat(sourceBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.resize(
                rgbMat,
                resizedMat,
                Size(0.0, 0.0),
                OCR_UPSCALE_FACTOR,
                OCR_UPSCALE_FACTOR,
                Imgproc.INTER_CUBIC
            )
            Imgproc.cvtColor(resizedMat, hsvMat, Imgproc.COLOR_RGB2HSV)
            Core.inRange(hsvMat, YELLOW_LOWER_BOUND, YELLOW_UPPER_BOUND, yellowMask)

            Imgproc.morphologyEx(
                yellowMask,
                refinedMask,
                Imgproc.MORPH_CLOSE,
                closeKernel
            )
            Imgproc.dilate(refinedMask, refinedMask, dilateKernel)
            Imgproc.GaussianBlur(refinedMask, refinedMask, Size(3.0, 3.0), 0.0)
            Imgproc.threshold(refinedMask, refinedMask, 0.0, 255.0, Imgproc.THRESH_BINARY)

            val processedBitmap = Bitmap.createBitmap(
                refinedMask.cols(),
                refinedMask.rows(),
                Bitmap.Config.ARGB_8888
            )
            Core.bitwise_not(refinedMask, invertedMask)
            Imgproc.cvtColor(invertedMask, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgbaMat, processedBitmap)

            val nonZeroCount = Core.countNonZero(refinedMask)
            val totalPixels = refinedMask.rows().toDouble() * refinedMask.cols().toDouble()
            val maskRatio = if (totalPixels > 0) nonZeroCount / totalPixels else 0.0
            val scaleBack = sourceBitmap.width.toFloat() / processedBitmap.width.toFloat()

            RunLogger.i(
                "ML Kit 预处理：原图=${sourceBitmap.width}x${sourceBitmap.height}，处理后=${processedBitmap.width}x${processedBitmap.height}，回缩比例=%.3f，黄色遮罩占比=%.4f".format(
                    scaleBack,
                    maskRatio
                )
            )

            PreprocessedImage(
                bitmap = processedBitmap,
                coordinateScaleBack = scaleBack
            )
        } finally {
            srcMat.release()
            rgbMat.release()
            resizedMat.release()
            hsvMat.release()
            yellowMask.release()
            refinedMask.release()
            invertedMask.release()
            rgbaMat.release()
            closeKernel.release()
            dilateKernel.release()
        }
    }

    private fun extractCandidateRows(result: Text, coordinateScaleBack: Float): List<TextRow> {
        return result.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val boundingBox = line.boundingBox ?: return@mapNotNull null
                val normalizedText = normalizeWhitespace(line.text)
                val chineseCount = countChineseChars(normalizedText)
                if (chineseCount <= 0) {
                    return@mapNotNull null
                }

                TextRow(
                    text = normalizedText,
                    centerY = ((boundingBox.top + boundingBox.bottom) / 2f) * coordinateScaleBack,
                    top = (boundingBox.top * coordinateScaleBack).roundToInt(),
                    bottom = (boundingBox.bottom * coordinateScaleBack).roundToInt()
                )
            }
            .sortedBy { it.centerY }
    }

    private fun mergeRows(rows: List<TextRow>): List<TextRow> {
        if (rows.isEmpty()) return emptyList()

        val mergedRows = mutableListOf<TextRow>()
        var cluster = mutableListOf(rows.first())

        fun flushCluster() {
            if (cluster.isEmpty()) return
            val mergedText = cluster.joinToString(" | ") { it.text }
            val centerY = cluster.map { it.centerY }.average().toFloat()
            val top = cluster.minOf { it.top }
            val bottom = cluster.maxOf { it.bottom }
            mergedRows += TextRow(
                text = mergedText,
                centerY = centerY,
                top = top,
                bottom = bottom
            )
            cluster = mutableListOf()
        }

        rows.drop(1).forEach { row ->
            val clusterCenter = cluster.map { it.centerY }.average().toFloat()
            if (abs(row.centerY - clusterCenter) <= ROW_MERGE_TOLERANCE_PX) {
                cluster += row
            } else {
                flushCluster()
                cluster += row
            }
        }
        flushCluster()

        return mergedRows.filter { row ->
            countChineseChars(row.text) >= MIN_CHINESE_CHARS_PER_LINE
        }
    }

    private fun buildTemplateSpec(
        rows: List<TextRow>,
        preprocessedImage: PreprocessedImage
    ): TemplateSpec {
        val lastRow = rows.lastOrNull()
            ?: throw IllegalStateException("没有可用于模板的末行")
        val processedCenterY = (lastRow.centerY / preprocessedImage.coordinateScaleBack).roundToInt()
        val processedHalfHeight = max(
            1,
            (TEMPLATE_HEIGHT_PX / preprocessedImage.coordinateScaleBack / 2f).roundToInt()
        )

        val processedMat = Mat()
        val grayMat = Mat()
        return try {
            Utils.bitmapToMat(preprocessedImage.bitmap, processedMat)
            Imgproc.cvtColor(processedMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val top = (processedCenterY - processedHalfHeight).coerceIn(0, grayMat.rows() - 1)
            val bottomExclusive = (processedCenterY + processedHalfHeight).coerceIn(top + 1, grayMat.rows())
            val templateMat = grayMat.submat(
                Rect(0, top, grayMat.cols(), bottomExclusive - top)
            ).clone()

            TemplateSpec(
                templateMat = templateMat,
                centerY = lastRow.centerY,
                top = (top * preprocessedImage.coordinateScaleBack).roundToInt(),
                bottom = (bottomExclusive * preprocessedImage.coordinateScaleBack).roundToInt()
            )
        } finally {
            processedMat.release()
            grayMat.release()
        }
    }

    private fun matchPreviousTemplate(
        previousPending: PendingFrame,
        currentPreprocessed: PreprocessedImage,
        currentRows: List<TextRow>,
        searchFullImage: Boolean = false
    ): MatchResult {
        val halfResult = runTemplateSearch(
            previousPending = previousPending,
            currentPreprocessed = currentPreprocessed,
            currentRows = currentRows,
            searchFullImage = searchFullImage
        )
        if (searchFullImage || halfResult.score >= LOW_SCORE_FULL_SEARCH_THRESHOLD) {
            return halfResult
        }

        RunLogger.i(
            "帧 ${previousPending.frameIndex} 低分 %.4f < %.1f，重试整图搜索".format(
                halfResult.score,
                LOW_SCORE_FULL_SEARCH_THRESHOLD
            )
        )

        val fullResult = runTemplateSearch(
            previousPending = previousPending,
            currentPreprocessed = currentPreprocessed,
            currentRows = currentRows,
            searchFullImage = true
        )

        val selectedResult = if (fullResult.score > halfResult.score) {
            fullResult
        } else {
            halfResult
        }

        RunLogger.i(
            "帧 ${previousPending.frameIndex} 搜索对比：半图=%.4f，整图=%.4f，选中=%s".format(
                halfResult.score,
                fullResult.score,
                if (selectedResult === fullResult) "整图" else "半图"
            )
        )

        return selectedResult
    }

    private fun runTemplateSearch(
        previousPending: PendingFrame,
        currentPreprocessed: PreprocessedImage,
        currentRows: List<TextRow>,
        searchFullImage: Boolean
    ): MatchResult {
        val processedMat = Mat()
        val grayMat = Mat()
        val resultMat = Mat()

        return try {
            Utils.bitmapToMat(currentPreprocessed.bitmap, processedMat)
            Imgproc.cvtColor(processedMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val searchHeight = if (searchFullImage) {
                grayMat.rows()
            } else {
                max(previousPending.templateSpec.templateMat.rows() + 1, grayMat.rows() / 2)
            }
            val searchMat = grayMat.submat(Rect(0, 0, grayMat.cols(), searchHeight))
            try {
                if (searchMat.rows() < previousPending.templateSpec.templateMat.rows() ||
                    searchMat.cols() < previousPending.templateSpec.templateMat.cols()
                ) {
                    throw IllegalStateException("搜索区域尺寸不足")
                }

                Imgproc.matchTemplate(
                    searchMat,
                    previousPending.templateSpec.templateMat,
                    resultMat,
                    Imgproc.TM_CCOEFF_NORMED
                )
                val minMax = Core.minMaxLoc(resultMat)
                val matchTopProcessed = minMax.maxLoc.y.toInt()
                val matchCenterProcessed =
                    matchTopProcessed + (previousPending.templateSpec.templateMat.rows() / 2f)
                val matchCenterOriginal = matchCenterProcessed * currentPreprocessed.coordinateScaleBack

                val searchMode = if (searchFullImage) "整图" else "上半区"
                var acceptedThreshold: Double? = null
                var threshold = MATCH_THRESHOLD_START
                while (threshold >= MATCH_THRESHOLD_END - 1e-6) {
                    val matched = minMax.maxVal >= threshold
                    RunLogger.i(
                        "帧 ${previousPending.frameIndex} $searchMode 搜索阈值 %.1f -> %s（分数=%.4f）".format(
                            threshold,
                            if (matched) "命中" else "未命中",
                            minMax.maxVal
                        )
                    )
                    if (matched) {
                        acceptedThreshold = threshold
                        break
                    }
                    threshold -= MATCH_THRESHOLD_STEP
                }

                if (acceptedThreshold == null) {
                    throw IllegalStateException("模板匹配失败，最高分 %.4f".format(minMax.maxVal))
                }

                val matchedRow = currentRows.minByOrNull { row ->
                    abs(row.centerY - matchCenterOriginal)
                } ?: throw IllegalStateException("当前帧没有可匹配的文字行")

                MatchResult(
                    score = minMax.maxVal,
                    acceptedThreshold = acceptedThreshold,
                    matchCenterY = matchCenterOriginal,
                    matchedRow = matchedRow,
                    searchHeight = searchHeight
                )
            } finally {
                searchMat.release()
            }
        } finally {
            processedMat.release()
            grayMat.release()
            resultMat.release()
        }
    }

    private fun appendPendingFrameSegment(frame: PendingFrame, endY: Int) {
        val safeStart = frame.startY.coerceIn(0, frame.bitmap.height - 1)
        val safeEnd = endY.coerceIn(safeStart + 1, frame.bitmap.height)
        val segmentHeight = safeEnd - safeStart
        if (segmentHeight <= 0) {
            throw IllegalStateException(
                "无效拼接片段: frame=${frame.frameIndex}, start=$safeStart, end=$safeEnd"
            )
        }

        val segmentBitmap = Bitmap.createBitmap(
            frame.bitmap,
            0,
            safeStart,
            frame.bitmap.width,
            segmentHeight
        )

        try {
            val currentStitched = stitchedBitmap
            if (currentStitched == null) {
                stitchedBitmap = segmentBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val combinedBitmap = Bitmap.createBitmap(
                    currentStitched.width,
                    currentStitched.height + segmentBitmap.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(combinedBitmap)
                canvas.drawBitmap(currentStitched, 0f, 0f, null)
                canvas.drawBitmap(segmentBitmap, 0f, currentStitched.height.toFloat(), null)
                currentStitched.recycle()
                stitchedBitmap = combinedBitmap
            }

            RunLogger.i(
                "追加帧 ${frame.frameIndex}：起点=$safeStart，终点=$safeEnd，片段高度=$segmentHeight，总高度=${stitchedBitmap?.height ?: 0}"
            )
        } finally {
            segmentBitmap.recycle()
        }
    }

    private fun calculateFrameDiffMean(previousBitmap: Bitmap, currentBitmap: Bitmap): Double {
        val previousMat = Mat()
        val currentMat = Mat()
        val previousGray = Mat()
        val currentGray = Mat()
        val previousSmall = Mat()
        val currentSmall = Mat()
        val diff = Mat()

        return try {
            Utils.bitmapToMat(previousBitmap, previousMat)
            Utils.bitmapToMat(currentBitmap, currentMat)
            Imgproc.cvtColor(previousMat, previousGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(currentMat, currentGray, Imgproc.COLOR_RGBA2GRAY)

            val downsampleWidth = 160.0
            val downsampleHeight = max(
                1,
                ((previousGray.rows().toDouble() / previousGray.cols().toDouble()) * downsampleWidth).roundToInt()
            )
            val downsampleSize = Size(downsampleWidth, downsampleHeight.toDouble())
            Imgproc.resize(previousGray, previousSmall, downsampleSize)
            Imgproc.resize(currentGray, currentSmall, downsampleSize)
            Core.absdiff(previousSmall, currentSmall, diff)

            Core.mean(diff).`val`[0]
        } finally {
            previousMat.release()
            currentMat.release()
            previousGray.release()
            currentGray.release()
            previousSmall.release()
            currentSmall.release()
            diff.release()
        }
    }

    private fun isFrameAlmostUnchanged(previousBitmap: Bitmap, currentBitmap: Bitmap): Boolean {
        val meanDiff = calculateFrameDiffMean(previousBitmap, currentBitmap)
        RunLogger.i("帧差异均值=%.2f".format(meanDiff))
        return meanDiff < UNCHANGED_DIFF_THRESHOLD
    }

    private fun drawDebugImage(
        bitmap: Bitmap,
        rows: List<TextRow>,
        templateSpec: TemplateSpec?,
        matchResult: MatchResult?,
        scale: Float
    ): Bitmap {
        val annotatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotatedBitmap)
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            strokeWidth = max(DEBUG_LINE_WIDTH, scale * DEBUG_LINE_WIDTH)
        }
        val templatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            strokeWidth = max(2f, scale * 2f)
            style = Paint.Style.STROKE
        }
        val matchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            strokeWidth = max(DEBUG_LINE_WIDTH, scale * DEBUG_LINE_WIDTH)
        }
        val matchedRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            strokeWidth = max(DEBUG_LINE_WIDTH, scale * DEBUG_LINE_WIDTH)
        }

        rows.forEach { row ->
            canvas.drawLine(
                0f,
                row.centerY,
                annotatedBitmap.width.toFloat(),
                row.centerY,
                rowPaint
            )
        }

        templateSpec?.let { spec ->
            canvas.drawRect(
                0f,
                spec.top.toFloat(),
                annotatedBitmap.width.toFloat(),
                spec.bottom.toFloat(),
                templatePaint
            )
        }

        matchResult?.let { result ->
            canvas.drawLine(
                0f,
                result.matchCenterY,
                annotatedBitmap.width.toFloat(),
                result.matchCenterY,
                matchPaint
            )
            canvas.drawLine(
                0f,
                result.matchedRow.centerY,
                annotatedBitmap.width.toFloat(),
                result.matchedRow.centerY,
                matchedRowPaint
            )
        }

        return annotatedBitmap
    }

    private fun logRecognizedLines(frameIndex: Int, result: Text, coordinateScaleBack: Float) {
        val lines = result.textBlocks.flatMap { it.lines }
        RunLogger.i("帧 $frameIndex 原始摘要：文本块=${result.textBlocks.size}，行数=${lines.size}")

        var foundChineseLine = false
        lines.forEachIndexed { index, line ->
            val boundingBox = line.boundingBox ?: return@forEachIndexed
            val normalizedText = normalizeWhitespace(line.text)
            val chineseCount = countChineseChars(normalizedText)
            if (chineseCount <= 0) return@forEachIndexed

            foundChineseLine = true
            val centerY = ((boundingBox.top + boundingBox.bottom) / 2f) * coordinateScaleBack
            val top = (boundingBox.top * coordinateScaleBack).roundToInt()
            val bottom = (boundingBox.bottom * coordinateScaleBack).roundToInt()
            RunLogger.i(
                "帧 $frameIndex 原始行 ${index + 1}：中心Y=%.1f，顶部=%d，底部=%d，中文数=%d，文本=%s".format(
                    centerY,
                    top,
                    bottom,
                    chineseCount,
                    normalizedText
                )
            )
        }

        if (!foundChineseLine) {
            RunLogger.i("帧 $frameIndex 原始行探测：没有包含中文的行")
        }
    }

    private fun logMergedRows(frameIndex: Int, rows: List<TextRow>) {
        if (rows.isEmpty()) {
            RunLogger.i("帧 $frameIndex 合并行：无")
            return
        }

        rows.forEachIndexed { index, row ->
            RunLogger.i(
                "帧 $frameIndex 合并行 ${index + 1}：中心Y=%.1f，顶部=%d，底部=%d，文本=%s".format(
                    row.centerY,
                    row.top,
                    row.bottom,
                    row.text
                )
            )
        }
    }

    private fun countChineseChars(text: String): Int {
        return text.count { char ->
            char.code in 0x4E00..0x9FFF
        }
    }

    private fun normalizeWhitespace(text: String): String {
        return text.replace("\\s+".toRegex(), " ").trim()
    }

    private fun saveStitchedImage() {
        if (!isRunning) return

        try {
            val bitmap = stitchedBitmap ?: throw IllegalStateException("没有拼接结果可保存")
            val savedLocation = saveBitmapToGallery(bitmap, "inventory_stitched")
            RunLogger.i("拼接图片已保存：$savedLocation")
            isRunning = false
            pendingFrame?.templateSpec?.templateMat?.release()
            pendingFrame?.bitmap?.recycle()
            pendingFrame = null
            bitmap.recycle()
            stitchedBitmap = null
            onStatusUpdate?.invoke("拼图完成已保存")
            onCompleted?.invoke(true)
        } catch (e: Exception) {
            e.printStackTrace()
            handleError("保存拼图失败: ${e.message}")
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, prefix: String): String {
        val fileName = "${prefix}_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YuanAssist")
            }
        }

        val uri = service.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("创建图片 URI 失败")

        service.contentResolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("写入图片失败")
            }
        } ?: throw IllegalStateException("打开图片输出流失败")

        return uri.toString()
    }

    private fun performSwipeAndContinue() {
        if (!isRunning) return

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.75f
        val endY = startY - (screenHeight / 5f)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                RunLogger.i(
                    "单次滑动完成：起点Y=%.1f，终点Y=%.1f，位移=%.1f".format(
                        startY,
                        endY,
                        startY - endY
                    )
                )
                handler.postDelayed(
                    { captureNextFrame() },
                    AFTER_SWIPE_DELAY_MS
                )
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                handleError("滑动被系统取消")
            }
        }, null)
    }

    private fun handleError(msg: String) {
        if (!isRunning) return

        isRunning = false
        RunLogger.e("物品拼接失败：$msg")
        onStatusUpdate?.invoke("出现错误：$msg")
        releaseState()
        onCompleted?.invoke(false)
    }
}
