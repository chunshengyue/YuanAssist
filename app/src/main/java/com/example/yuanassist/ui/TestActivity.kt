package com.example.yuanassist.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.yuanassist.R
import com.example.yuanassist.model.BattleStageNavigationRegistry
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.utils.TemplateOverrideStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.gson.Gson
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.min

class TestActivity : AppCompatActivity() {

    companion object {
        private const val START_BATTLE_RED_OPTION = "START_BATTLE_RED_REGION"
        private const val START_BATTLE_RED_LABEL = "\u5F00\u59CB\u6218\u6597\uFF08\u5E95\u90E8\u7EA2\u6309\u94AE\uFF09"
        private const val BATTLE_TURN_OCR_OPTION = "BATTLE_TURN_OCR"
        private const val BATTLE_TURN_OCR_LABEL = "\u6218\u6597\u56DE\u5408 OCR\uFF08\u53F3\u4E0A\u89D2\uFF09"
        private const val ORANGE_STAR_OPTION = "ORANGE_STAR_CHECK"
        private const val ORANGE_STAR_LABEL = "\u6A59\u661F\u68C0\u6D4B\uFF08\u5DE6\u4E0A\u89D2\uFF09"
        private const val CAVE_DONGKU_OPTION = "CAVE_DONGKU_FALLBACK"
        private const val CAVE_DONGKU_LABEL = "\u6D1E\u7A9F\u5165\u53E3\uFF08dongku / dongku2\uFF09"
        private const val CAVE_NEXT_FLOOR_TEMPLATE = "xiayiceng.png"
        private const val CAVE_NEXT_FLOOR_LABEL = "\u6D1E\u7A9F\u4E0B\u4E00\u5C42\uFF08xiayiceng\uFF09"
        private const val DEATH_CHECK_OPTION_PREFIX = "DEATH_CHECK_SLOT_"
        private const val YOUZHOU_TEMPLATE = "youzhou.png"
        private const val JINRU_TEMPLATE = "jinru.png"
        private const val ALL_WIPE_TEMPLATE = "zaicitiaozhan.png"
        private const val ALL_WIPE_TEMPLATE_THRESHOLD = 0.80f
        private const val ALL_WIPE_TEMPLATE_CENTER_X = 785f
        private const val ALL_WIPE_TEMPLATE_CENTER_Y = 1699f
        private const val ALL_WIPE_TEMPLATE_ROI_SIZE = 300f
        private const val ORANGE_STAR_RECOVERY_TEMPLATE = "queding2.png"
        private const val ORANGE_STAR_RECOVERY_THRESHOLD = 0.80f
        private const val ORANGE_STAR_RECOVERY_CENTER_X = 759f
        private const val ORANGE_STAR_RECOVERY_CENTER_Y = 1158f
        private const val ORANGE_STAR_RECOVERY_ROI_SIZE = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_TEMPLATE = "lantai.png"
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_X = 499f
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_Y = 567f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE = "yuanbao.png"
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_X = 441f
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y = 920f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD = 0.85f
        private const val STAGE_HOME_RECOVERY_BAIHU_TEMPLATE = "baihu.png"
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_X = 84f
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_Y = 1505f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_BAIHU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_DIGONG_TEMPLATE = "digong.png"
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_X = 573f
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_Y = 811f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE = "yijirukou.png"
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X = 931f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y = 1688f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_XINZHI_TEMPLATE = "xinzhi.png"
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_X = 999f
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_Y = 1544f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE = "lixian.png"
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_X = 922f
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y = 1039f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD = 0.80f
        private const val CAVE_DONGKU_TEMPLATE = "dongku.png"
        private const val CAVE_DONGKU_TEMPLATE_FALLBACK = "dongku2.png"
        private const val CAVE_DONGKU_CENTER_X = 666f
        private const val CAVE_DONGKU_CENTER_Y = 313f
        private const val CAVE_DONGKU_ROI_WIDTH = 300f
        private const val CAVE_DONGKU_ROI_HEIGHT = 300f
        private const val CAVE_DONGKU_THRESHOLD = 0.75f
        private const val CAVE_NEXT_FLOOR_CENTER_X = 700f
        private const val CAVE_NEXT_FLOOR_CENTER_Y = 1700f
        private const val CAVE_NEXT_FLOOR_ROI_WIDTH = 500f
        private const val CAVE_NEXT_FLOOR_ROI_HEIGHT = 500f
        private const val CAVE_NEXT_FLOOR_THRESHOLD = 0.80f
        private const val MAINLINE_624_DYNAMIC_SOURCE = "zhu_xian_6_24(dynamic)"
        private const val DEFAULT_TEMPLATE_TEST_THRESHOLD = 0.90f
        private const val MAINLINE_624_BRANCH_THRESHOLD = 0.75f
        private const val START_BATTLE_RED_MIN_CONFIDENCE = 0.55f
        private const val START_BATTLE_RED_THRESHOLD = 0.72f
        private const val START_BATTLE_RED_CENTER_X = 540f
        private const val START_BATTLE_RED_CENTER_Y = 1700f
        private const val START_BATTLE_RED_ROI_WIDTH = 500f
        private const val START_BATTLE_RED_ROI_HEIGHT = 400f
        private const val BATTLE_TURN_OCR_W = 400f
        private const val BATTLE_TURN_OCR_H = 300f
        private const val DEATH_CHECK_TOP_Y = 1350f
        private const val DEATH_CHECK_BOTTOM_Y = 1700f
        private const val DEATH_CHECK_SLOT_COUNT = 5
        private const val DEATH_CHECK_SATURATION_THRESHOLD = 20f
        private const val ORANGE_STAR_CENTER_X = 163f
        private const val ORANGE_STAR_CENTER_Y = 302f
        private const val ORANGE_STAR_ROI_SIZE = 200f
        private const val ORANGE_STAR_THRESHOLD = 0.60f
        private const val ORANGE_STAR_SHAPE_THRESHOLD = 0.34f
        private const val ORANGE_STAR_MIN_GLOW_RATIO = 0.07f
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val REPLACEMENT_SIZE = 60
        private val DAILY_TEST_SCRIPT_FILES = listOf(
            "tu_fa_qing_kuang.json",
            "xiao_dao_xiao_xi.json",
            "ta_de_chuan_wen.json",
            "dai_ban_gong_wu.json",
            "zhu_xian_6_24.json"
        )
    }

    private data class RedRegionMatch(
        val center: PointF,
        val confidence: Float,
        val sizeScore: Float,
        val fillRatio: Float,
        val rednessScore: Float,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val step: Int
    )

    private data class OrangeStarCandidate(
        val center: PointF,
        val confidence: Float,
        val glowScore: Float,
        val glowRatio: Float,
        val shapeScore: Float,
        val armContinuity: Float,
        val boundsWidth: Int,
        val boundsHeight: Int
    )

    private data class DeathCheckResult(
        val slotIndex: Int,
        val meanSaturation: Float,
        val isDead: Boolean
    )

    private data class LocalTemplateRegion(
        val scriptFileName: String,
        val taskId: Int,
        val roi: ROI,
        val threshold: Float? = null
    )

    private data class SearchArea(val label: String, val rect: Rect, val threshold: Float)
    private data class AreaMatchScan(
        val hits: List<TemplateMatchHit>,
        val bestCandidate: TemplateMatchHit?
    )
    private data class TemplateScanSummary(
        val hits: List<TemplateMatchHit>,
        val bestCandidate: TemplateMatchHit?
    )
    private data class DisplayMapping(
        val displayWidth: Float,
        val displayHeight: Float,
        val displayToScreenshotX: Float,
        val displayToScreenshotY: Float
    )
    private data class TemplateMatchHit(val rect: Rect, val score: Float, val areaLabel: String)
    private data class ReplacementTarget(val fileName: String, val label: String)

    private inner class ReplacementPreviewView(
        private val bitmap: Bitmap,
        private val roiRect: Rect,
        initialCropRect: Rect,
        private val instructionText: String
    ) : View(this) {

        private val bitmapDstRect = RectF()
        private val roiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5C07B")
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat().coerceAtLeast(4f)
        }
        private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat().coerceAtLeast(4f)
        }
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#44000000")
            style = Paint.Style.FILL
        }
        private val instructionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AA111111")
            style = Paint.Style.FILL
        }
        private val instructionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(12).toFloat().coerceAtLeast(26f)
        }
        private val cropRect = Rect(initialCropRect)
        private var dragging = false
        private var downBitmapX = 0f
        private var downBitmapY = 0f
        private var startCenterX = cropRect.exactCenterX()
        private var startCenterY = cropRect.exactCenterY()

        fun getCropRect(): Rect = Rect(cropRect)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            val maxHeight = (resources.displayMetrics.heightPixels * 0.72f).toInt().coerceAtLeast(1)
            val aspectHeight = (width * (bitmap.height / bitmap.width.toFloat())).toInt().coerceAtLeast(1)
            val height = min(aspectHeight, maxHeight)
            setMeasuredDimension(width, height)
            updateBitmapDstRect(width, height)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawBitmap(bitmap, null, bitmapDstRect, null)
            val roi = mapBitmapRectToView(roiRect)
            val crop = mapBitmapRectToView(cropRect)
            canvas.drawRect(roi, roiPaint)
            canvas.drawRect(0f, 0f, width.toFloat(), crop.top, shadePaint)
            canvas.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), shadePaint)
            canvas.drawRect(0f, crop.top, crop.left, crop.bottom, shadePaint)
            canvas.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, shadePaint)
            canvas.drawRect(crop, cropPaint)
            drawInstruction(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (bitmapDstRect.isEmpty) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val bitmapPoint = viewPointToBitmap(event.x, event.y) ?: return false
                    if (!cropRect.contains(bitmapPoint.x.toInt(), bitmapPoint.y.toInt())) {
                        return false
                    }
                    dragging = true
                    downBitmapX = bitmapPoint.x
                    downBitmapY = bitmapPoint.y
                    startCenterX = cropRect.exactCenterX()
                    startCenterY = cropRect.exactCenterY()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return false
                    val bitmapPoint = viewPointToBitmap(event.x, event.y) ?: return true
                    val dx = bitmapPoint.x - downBitmapX
                    val dy = bitmapPoint.y - downBitmapY
                    moveCropTo(startCenterX + dx, startCenterY + dy)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) return false
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun moveCropTo(targetCenterX: Float, targetCenterY: Float) {
            val halfW = cropRect.width() / 2f
            val halfH = cropRect.height() / 2f
            val minCenterX = roiRect.left + halfW
            val maxCenterX = roiRect.right - halfW
            val minCenterY = roiRect.top + halfH
            val maxCenterY = roiRect.bottom - halfH
            val clampedCenterX = targetCenterX.coerceIn(minCenterX, maxCenterX)
            val clampedCenterY = targetCenterY.coerceIn(minCenterY, maxCenterY)
            cropRect.offsetTo(
                (clampedCenterX - halfW).toInt(),
                (clampedCenterY - halfH).toInt()
            )
            invalidate()
        }

        private fun updateBitmapDstRect(viewWidth: Int, viewHeight: Int) {
            val scale = min(viewWidth / bitmap.width.toFloat(), viewHeight / bitmap.height.toFloat())
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (viewWidth - drawWidth) / 2f
            val top = (viewHeight - drawHeight) / 2f
            bitmapDstRect.set(left, top, left + drawWidth, top + drawHeight)
        }

        private fun drawInstruction(canvas: Canvas) {
            val padding = dp(10).toFloat()
            val lineHeight = instructionTextPaint.fontMetrics.run { bottom - top }
            val textWidth = instructionTextPaint.measureText(instructionText)
            val boxLeft = bitmapDstRect.left + padding
            val boxTop = bitmapDstRect.top + padding
            val boxRight = (boxLeft + textWidth + padding * 2f).coerceAtMost(bitmapDstRect.right - padding)
            val boxBottom = boxTop + lineHeight + padding * 1.6f
            canvas.drawRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), dp(8).toFloat(), dp(8).toFloat(), instructionBgPaint)
            val baseline = boxTop + padding - instructionTextPaint.fontMetrics.top
            canvas.drawText(instructionText, boxLeft + padding, baseline, instructionTextPaint)
        }

        private fun mapBitmapRectToView(bitmapRect: Rect): RectF {
            val scaleX = bitmapDstRect.width() / bitmap.width
            val scaleY = bitmapDstRect.height() / bitmap.height
            return RectF(
                bitmapDstRect.left + bitmapRect.left * scaleX,
                bitmapDstRect.top + bitmapRect.top * scaleY,
                bitmapDstRect.left + bitmapRect.right * scaleX,
                bitmapDstRect.top + bitmapRect.bottom * scaleY
            )
        }

        private fun viewPointToBitmap(viewX: Float, viewY: Float): PointF? {
            if (!bitmapDstRect.contains(viewX, viewY)) return null
            val scaleX = bitmap.width / bitmapDstRect.width()
            val scaleY = bitmap.height / bitmapDstRect.height()
            return PointF(
                ((viewX - bitmapDstRect.left) * scaleX).coerceIn(0f, bitmap.width - 1f),
                ((viewY - bitmapDstRect.top) * scaleY).coerceIn(0f, bitmap.height - 1f)
            )
        }
    }

    private lateinit var ivScreenshot: ImageView
    private lateinit var tvLog: TextView
    private lateinit var spinnerTemplates: Spinner
    private lateinit var rgScope: RadioGroup
    private lateinit var rbScopeLocal: RadioButton
    private lateinit var tvScopeHint: TextView
    private lateinit var btnReplaceTemplate: Button
    private lateinit var btnRestoreTemplate: Button

    private var currentBitmap: Bitmap? = null
    private var availableTemplates = listOf<String>()
    private val gson = Gson()
    private val localSearchRegionsByTemplate = linkedMapOf<String, List<LocalTemplateRegion>>()
    private val redRegionSearchRegions = mutableListOf<LocalTemplateRegion>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(it)
                    currentBitmap = BitmapFactory.decodeStream(inputStream)
                    ivScreenshot.setImageBitmap(currentBitmap)
                    log("Screenshot loaded: ${currentBitmap?.width} x ${currentBitmap?.height}")
                } catch (e: Exception) {
                    log("Image load failed: ${e.message}")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val header = findViewById<View>(R.id.layout_test_header)
        val topSpace = findViewById<View>(R.id.view_test_status_space)
        findViewById<ImageView>(R.id.btn_back_test).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topSpace.updateLayoutParams { height = (statusBarTop / 2).coerceAtLeast(0) }
            insets
        }
        ViewCompat.requestApplyInsets(header)

        ivScreenshot = findViewById(R.id.iv_test_screenshot)
        tvLog = findViewById(R.id.tv_test_log)
        spinnerTemplates = findViewById(R.id.spinner_templates)
        rgScope = findViewById(R.id.rg_test_scope)
        rbScopeLocal = findViewById(R.id.rb_test_scope_local)
        tvScopeHint = findViewById(R.id.tv_test_scope_hint)
        btnReplaceTemplate = findViewById(R.id.btn_test_replace_template)
        btnRestoreTemplate = findViewById(R.id.btn_test_restore_template)

        loadDailyTemplateRegions()
        rgScope.setOnCheckedChangeListener { _, _ -> updateScopeHint() }

        log(if (OpenCVLoader.initDebug()) "OpenCV init success" else "OpenCV init failed")
        loadAssetsTemplates()

        findViewById<Button>(R.id.btn_test_upload).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btn_test_run).setOnClickListener { runCurrentTest() }
        btnReplaceTemplate.setOnClickListener { startTemplateReplacementFlow() }
        btnRestoreTemplate.setOnClickListener { restoreCurrentTemplate() }
    }

    private fun runCurrentTest() {
        if (currentBitmap == null) {
            Toast.makeText(this, "\u8BF7\u5148\u4E0A\u4F20\u622A\u56FE", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedTemplate = currentSelectedTemplate()
        when {
            selectedTemplate == START_BATTLE_RED_OPTION -> runScopedStartBattleRedMatchTest(isLocalScopeEnabled())
            selectedTemplate == BATTLE_TURN_OCR_OPTION -> runBattleTurnOcrTest()
            selectedTemplate == ORANGE_STAR_OPTION -> runOrangeStarTest()
            selectedTemplate == CAVE_DONGKU_OPTION -> runCaveDongkuFallbackTest(isLocalScopeEnabled())
            isDeathCheckOption(selectedTemplate) -> runDeathCheckTest(deathCheckSlotFromOption(selectedTemplate) ?: return)
            else -> runScopedMultiTargetMatchTest(selectedTemplate)
        }
    }

    private fun loadAssetsTemplates() {
        try {
            val allFiles = assets.list("") ?: emptyArray()
            availableTemplates = buildList {
                add(START_BATTLE_RED_OPTION)
                add(BATTLE_TURN_OCR_OPTION)
                add(ORANGE_STAR_OPTION)
                add(CAVE_DONGKU_OPTION)
                addAll((1..DEATH_CHECK_SLOT_COUNT).map(::deathCheckOption))
                addAll(allFiles.filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) })
            }
            spinnerTemplates.adapter = TemplateSpinnerAdapter(availableTemplates)
            spinnerTemplates.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateScopeHint()
                    updateTemplateActionButtons()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            updateScopeHint()
            updateTemplateActionButtons()
            log("Scanned ${availableTemplates.size} templates")
        } catch (e: Exception) {
            log("Read assets failed: ${e.message}")
        }
    }

    private fun currentSelectedTemplate(): String =
        spinnerTemplates.selectedItem as? String ?: START_BATTLE_RED_OPTION

    private fun isLocalScopeEnabled(): Boolean = rbScopeLocal.isChecked

    private fun displayName(templateName: String): String =
        when {
            templateName == START_BATTLE_RED_OPTION -> START_BATTLE_RED_LABEL
            templateName == BATTLE_TURN_OCR_OPTION -> BATTLE_TURN_OCR_LABEL
            templateName == ORANGE_STAR_OPTION -> ORANGE_STAR_LABEL
            templateName == CAVE_DONGKU_OPTION -> CAVE_DONGKU_LABEL
            templateName == CAVE_NEXT_FLOOR_TEMPLATE -> CAVE_NEXT_FLOOR_LABEL
            isDeathCheckOption(templateName) -> "\u9635\u4EA1\u68C0\u6D4B\uFF08${deathCheckSlotFromOption(templateName)}\u53F7\u4F4D\uFF09"
            else -> templateName
        }

    private fun updateScopeHint() {
        val template = currentSelectedTemplate()
        tvScopeHint.text = when {
            template == ORANGE_STAR_OPTION ->
                "\u4F7F\u7528\u5B9E\u6218\u76F8\u540C\u7684\u5DE6\u4E0A\u89D2 200x200 \u68C0\u6D4B\u533A\u57DF\uFF0C\u8F93\u51FA shape/glow/confidence"
            isDeathCheckOption(template) ->
                "\u4F7F\u7528\u5B9E\u6218\u76F8\u540C\u7684\u5E95\u90E8\u69FD\u4F4D ROI\uFF0C\u8F93\u51FA\u4E2D\u5FC3\u533A\u57DF\u5E73\u5747\u9971\u548C\u5EA6"
            template == CAVE_DONGKU_OPTION && isLocalScopeEnabled() ->
                "\u5C40\u90E8\u8BC6\u522B\u4F1A\u5148\u6309\u5B9E\u6218 ROI \u68C0\u7D22 dongku\uFF0C\u5931\u8D25\u540E\u518D\u68C0\u7D22 dongku2"
            template == CAVE_DONGKU_OPTION ->
                "\u5168\u5C4F\u8BC6\u522B\u4F1A\u5148\u641C\u7D22 dongku\uFF0C\u672A\u547D\u4E2D\u518D\u641C\u7D22 dongku2"
            template == CAVE_NEXT_FLOOR_TEMPLATE && isLocalScopeEnabled() ->
                "\u5C40\u90E8\u8BC6\u522B\u4F1A\u4F7F\u7528 CombatEngine \u5B9E\u6218\u7684 bottom ROI\uFF1A700,1700 / 500x500"
            template == CAVE_NEXT_FLOOR_TEMPLATE ->
                "\u5168\u5C4F\u8BC6\u522B\u4F1A\u5728\u6574\u5F20\u622A\u56FE\u4E2D\u68C0\u7D22 xiayiceng"
            template == START_BATTLE_RED_OPTION && isStartBattleTemplateModeEnabled() && isLocalScopeEnabled() ->
                "\u5F53\u524D\u4E3A\u5F00\u59CB\u6218\u6597\u6A21\u677F\u6A21\u5F0F\uFF0C\u5C40\u90E8\u8BC6\u522B\u4F1A\u4F7F\u7528 CombatEngine \u5B9E\u9645 ROI"
            template == START_BATTLE_RED_OPTION && isStartBattleTemplateModeEnabled() ->
                "\u5F53\u524D\u4E3A\u5F00\u59CB\u6218\u6597\u6A21\u677F\u6A21\u5F0F\uFF0C\u5168\u5C4F\u6D4B\u8BD5\u4F1A\u76F4\u63A5\u8D70\u6A21\u677F\u5339\u914D"
            template == START_BATTLE_RED_OPTION && isLocalScopeEnabled() ->
                "\u5C40\u90E8\u8BC6\u522B\u4F1A\u4F7F\u7528 CombatEngine \u5B9E\u9645\u7684 500x400 \u5E95\u90E8 ROI"
            template == START_BATTLE_RED_OPTION ->
                "\u5168\u5C4F\u6A21\u5F0F\u4F1A\u4FDD\u7559\u5F53\u524D\u7EA2\u533A\u6D4B\u8BD5\u903B\u8F91"
            template == BATTLE_TURN_OCR_OPTION ->
                "\u4F7F\u7528\u5B9E\u6218\u76F8\u540C\u7684\u53F3\u4E0A\u89D2 OCR \u533A\u57DF\uFF1A400x300\uFF0C\u8F93\u51FA raw/normalized/hits/turn"
            isLocalScopeEnabled() -> "\u5C40\u90E8\u8BC6\u522B\u4F1A\u4F7F\u7528\u5DF2\u767B\u8BB0\u7684 ROI \u68C0\u7D22 ${displayName(template)}"
            else -> "\u5168\u5C4F\u8BC6\u522B\u4F1A\u5728\u6574\u5F20\u622A\u56FE\u4E2D\u68C0\u7D22 ${displayName(template)}"
        }
    }

    private fun updateTemplateActionButtons() {
        val template = currentSelectedTemplate()
        val replacementTargets = replacementTargetsForOption(template)
        btnReplaceTemplate.isEnabled = replacementTargets.isNotEmpty()
        btnRestoreTemplate.isEnabled = replacementTargets.isNotEmpty()
    }

    private fun startTemplateReplacementFlow() {
        val templateName = currentSelectedTemplate()
        val screenshot = currentBitmap
        if (screenshot == null) {
            Toast.makeText(this, "\u8BF7\u5148\u4E0A\u4F20\u622A\u56FE", Toast.LENGTH_SHORT).show()
            return
        }
        val replacementTargets = replacementTargetsForOption(templateName)
        if (replacementTargets.isEmpty()) {
            Toast.makeText(this, "\u5F53\u524D\u8BC6\u522B\u9879\u4E0D\u652F\u6301\u66FF\u6362\u7D20\u6750", Toast.LENGTH_SHORT).show()
            return
        }
        if (templateName == START_BATTLE_RED_OPTION) {
            val regions = distinctRegions(startBattleLocalRegions())
            if (regions.isEmpty()) {
                showReplacementPreviewDialog(templateName, replacementTargets.first().fileName, screenshot, null)
                return
            }
            if (regions.size == 1) {
                showReplacementPreviewDialog(templateName, replacementTargets.first().fileName, screenshot, regions.first())
                return
            }
            AlertDialog.Builder(this)
                .setTitle("\u9009\u62E9\u5F00\u59CB\u6218\u6597 ROI \u6765\u6E90")
                .setItems(regions.map(::regionLabel).toTypedArray()) { _, which ->
                    showReplacementPreviewDialog(templateName, replacementTargets.first().fileName, screenshot, regions[which])
                }
                .setNegativeButton("\u53D6\u6D88", null)
                .show()
            return
        }
        if (replacementTargets.size > 1) {
            AlertDialog.Builder(this)
                .setTitle("\u9009\u62E9\u8981\u66FF\u6362\u7684\u7D20\u6750")
                .setItems(replacementTargets.map { it.label }.toTypedArray()) { _, which ->
                    startReplacementPreviewForTarget(templateName, replacementTargets[which], screenshot)
                }
                .setNegativeButton("\u53D6\u6D88", null)
                .show()
            return
        }
        startReplacementPreviewForTarget(templateName, replacementTargets.first(), screenshot)
    }

    private fun startReplacementPreviewForTarget(
        optionName: String,
        target: ReplacementTarget,
        screenshot: Bitmap
    ) {
        val regions = distinctRegions(replacementRegionsForOption(optionName))
        if (regions.isEmpty()) {
            showReplacementPreviewDialog(optionName, target.fileName, screenshot, null)
            return
        }
        if (regions.size == 1) {
            showReplacementPreviewDialog(optionName, target.fileName, screenshot, regions.first())
            return
        }
        AlertDialog.Builder(this)
            .setTitle("\u9009\u62E9 ROI \u6765\u6E90")
            .setItems(regions.map(::regionLabel).toTypedArray()) { _, which ->
                showReplacementPreviewDialog(optionName, target.fileName, screenshot, regions[which])
            }
            .setNegativeButton("\u53D6\u6D88", null)
            .show()
    }

    private fun showReplacementPreviewDialog(
        templateName: String,
        overrideFileName: String,
        screenshot: Bitmap,
        region: LocalTemplateRegion?
    ) {
        val replacementBaseBitmap = normalizeReplacementBitmap(screenshot)
        val roiRect = region?.let { buildRectFromRoi(replacementBaseBitmap, it.roi) }
            ?: Rect(0, 0, replacementBaseBitmap.width, replacementBaseBitmap.height)
        if (roiRect.width() <= 0 || roiRect.height() <= 0) {
            replacementBaseBitmap.recycle()
            return
        }
        val cropRect = buildCenteredCropRect(
            roiRect,
            replacementBaseBitmap.width,
            replacementBaseBitmap.height,
            REPLACEMENT_SIZE
        )
        val previewView = ReplacementPreviewView(
            replacementBaseBitmap,
            roiRect,
            cropRect,
            "\u628A\u7EA2\u6846\u79FB\u5230\u8FD9\u4E2A\u754C\u9762\u5E94\u8BE5\u70B9\u51FB\u7684\u4F4D\u7F6E"
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }
        layout.addView(TextView(this).apply {
            text = buildString {
                append(displayName(templateName))
                append('\n')
                append(region?.let(::regionLabel) ?: "\u5168\u5C4F\u66FF\u6362")
            }
            setTextColor(Color.WHITE)
            textSize = 15f
        })
        layout.addView(previewView.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        })
        layout.addView(TextView(this).apply {
            text = if (region == null) {
                "\u5F53\u524D\u7D20\u6750\u6CA1\u6709 ROI\uFF0C\u5DF2\u5207\u6362\u4E3A\u5168\u5C4F\u9009\u70B9\u6A21\u5F0F\u3002\u7EA2\u6846\u4E3A 60x60 \u66FF\u6362\u7D20\u6750\uFF0C\u53EF\u4EE5\u62D6\u52A8\u3002"
            } else {
                "\u91D1\u6846\u662F ROI\uFF0C\u7EA2\u6846\u662F 60x60 \u66FF\u6362\u7D20\u6750\uFF0C\u53EF\u4EE5\u62D6\u52A8\u3002"
            }
            setTextColor(Color.parseColor("#D9D2C3"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("\u786E\u5B9A\u66FF\u6362") { _, _ ->
                val selectedCropRect = previewView.getCropRect()
                val replacement = Bitmap.createBitmap(
                    replacementBaseBitmap,
                    selectedCropRect.left,
                    selectedCropRect.top,
                    selectedCropRect.width(),
                    selectedCropRect.height()
                )
                val saved = saveTemplateOverride(templateName, overrideFileName, replacement)
                replacement.recycle()
                if (saved) {
                    Toast.makeText(this, "\u5DF2\u66FF\u6362 ${displayName(templateName)}", Toast.LENGTH_SHORT).show()
                    log("Template replaced: ${displayName(templateName)}")
                    updateTemplateActionButtons()
                    updateScopeHint()
                } else {
                    Toast.makeText(this, "\u66FF\u6362\u5931\u8D25", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("\u53D6\u6D88", null)
            .create()
        dialog.setOnDismissListener {
            if (!replacementBaseBitmap.isRecycled) {
                replacementBaseBitmap.recycle()
            }
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun restoreCurrentTemplate() {
        val templateName = currentSelectedTemplate()
        if (replacementTargetsForOption(templateName).isEmpty()) {
            Toast.makeText(this, "\u5F53\u524D\u8BC6\u522B\u9879\u4E0D\u652F\u6301\u7D20\u6750\u8FD8\u539F", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasTemplateOverride(templateName)) {
            Toast.makeText(this, "\u5F53\u524D\u6CA1\u6709\u66FF\u6362\u7248\u7D20\u6750", Toast.LENGTH_SHORT).show()
            return
        }
        if (restoreTemplateOverride(templateName)) {
            Toast.makeText(this, "\u5DF2\u8FD8\u539F ${displayName(templateName)}", Toast.LENGTH_SHORT).show()
            log("Template restored: ${displayName(templateName)}")
            updateTemplateActionButtons()
            updateScopeHint()
        } else {
            Toast.makeText(this, "\u8FD8\u539F\u5931\u8D25", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isStartBattleTemplateModeEnabled(): Boolean =
        TemplateOverrideStore.isStartBattleTemplateMode(this)

    private fun hasTemplateOverride(templateName: String): Boolean {
        return if (templateName == START_BATTLE_RED_OPTION) {
            TemplateOverrideStore.hasOverride(this, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)
        } else {
            replacementTargetsForOption(templateName).any { TemplateOverrideStore.hasOverride(this, it.fileName) }
        }
    }

    private fun saveTemplateOverride(templateName: String, overrideFileName: String, bitmap: Bitmap): Boolean {
        return if (templateName == START_BATTLE_RED_OPTION) {
            val saved = TemplateOverrideStore.saveOverride(this, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME, bitmap)
            if (saved) {
                TemplateOverrideStore.enableStartBattleTemplateMode(this)
            }
            saved
        } else {
            TemplateOverrideStore.saveOverride(this, overrideFileName, bitmap)
        }
    }

    private fun restoreTemplateOverride(templateName: String): Boolean {
        return if (templateName == START_BATTLE_RED_OPTION) {
            val restored = TemplateOverrideStore.restoreOverride(this, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)
            if (restored) {
                TemplateOverrideStore.disableStartBattleTemplateMode(this)
            }
            restored
        } else {
            replacementTargetsForOption(templateName)
                .map { TemplateOverrideStore.restoreOverride(this, it.fileName) }
                .all { it }
        }
    }

    private fun loadDailyTemplateRegions() {
        localSearchRegionsByTemplate.clear()
        redRegionSearchRegions.clear()
        val regionMap = linkedMapOf<String, MutableList<LocalTemplateRegion>>()
        for (scriptFile in DAILY_TEST_SCRIPT_FILES) {
            try {
                val plan = assets.open("daily_scripts/$scriptFile").use { gson.fromJson(it.reader(), DailyTaskPlan::class.java) }
                for (task in plan.tasks) {
                    val params = task.params ?: continue
                    val roi = params.roi
                    val templateName = params.template_name ?: continue
                    if (roi != null && templateName.all { ch -> ch.code in 0..127 }) {
                        regionMap.getOrPut(templateName) { mutableListOf() }
                            .add(LocalTemplateRegion(scriptFile, task.id, roi, params.threshold))
                    }
                }
            } catch (_: Exception) {
            }
        }
        regionMap.getOrPut(YOUZHOU_TEMPLATE) { mutableListOf() }
            .add(
                LocalTemplateRegion(
                    scriptFileName = MAINLINE_624_DYNAMIC_SOURCE,
                    taskId = 99,
                    roi = ROI(x = 963f, y = 236f, w = 400f, h = 400f, align = "top"),
                    threshold = MAINLINE_624_BRANCH_THRESHOLD
                )
            )
        regionMap.getOrPut(JINRU_TEMPLATE) { mutableListOf() }
            .add(
                LocalTemplateRegion(
                    scriptFileName = MAINLINE_624_DYNAMIC_SOURCE,
                    taskId = 100,
                    roi = ROI(x = 540f, y = 1440f, w = 1080f, h = 960f, align = "center"),
                    threshold = MAINLINE_624_BRANCH_THRESHOLD
                )
            )
        registerCombatEngineTemplateRegions(regionMap)
        redRegionSearchRegions += LocalTemplateRegion(
            scriptFileName = "CombatEngine/\u5F00\u59CB\u6218\u6597",
            taskId = 0,
            roi = ROI(
                x = START_BATTLE_RED_CENTER_X,
                y = START_BATTLE_RED_CENTER_Y,
                w = START_BATTLE_RED_ROI_WIDTH,
                h = START_BATTLE_RED_ROI_HEIGHT,
                align = "bottom"
            ),
            threshold = START_BATTLE_RED_THRESHOLD
        )
        regionMap.forEach { (name, regions) ->
            localSearchRegionsByTemplate[name] = distinctRegions(regions)
        }
    }

    private fun registerCombatEngineTemplateRegions(regionMap: MutableMap<String, MutableList<LocalTemplateRegion>>) {
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = ALL_WIPE_TEMPLATE,
            sourceLabel = "CombatEngine/\u5168\u706D\u68C0\u6D4B",
            centerX = ALL_WIPE_TEMPLATE_CENTER_X,
            centerY = ALL_WIPE_TEMPLATE_CENTER_Y,
            align = "bottom",
            width = ALL_WIPE_TEMPLATE_ROI_SIZE,
            height = ALL_WIPE_TEMPLATE_ROI_SIZE,
            threshold = ALL_WIPE_TEMPLATE_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = ORANGE_STAR_RECOVERY_TEMPLATE,
            sourceLabel = "CombatEngine/\u6A59\u661F\u786E\u8BA4",
            centerX = ORANGE_STAR_RECOVERY_CENTER_X,
            centerY = ORANGE_STAR_RECOVERY_CENTER_Y,
            align = "center",
            width = ORANGE_STAR_RECOVERY_ROI_SIZE,
            height = ORANGE_STAR_RECOVERY_ROI_SIZE,
            threshold = ORANGE_STAR_RECOVERY_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u5170\u53F0",
            centerX = STAGE_HOME_RECOVERY_LANTAI_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_LANTAI_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_LANTAI_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u5143\u5B9D",
            centerX = STAGE_HOME_RECOVERY_YUANBAO_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_BAIHU_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u767D\u9E44",
            centerX = STAGE_HOME_RECOVERY_BAIHU_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_BAIHU_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_BAIHU_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_DIGONG_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u5730\u5BAB",
            centerX = STAGE_HOME_RECOVERY_DIGONG_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_DIGONG_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_DIGONG_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u9057\u8FF9\u5165\u53E3",
            centerX = STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_XINZHI_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u5FC3\u7EB8",
            centerX = STAGE_HOME_RECOVERY_XINZHI_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_XINZHI_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_XINZHI_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE,
            sourceLabel = "CombatEngine/\u4E3B\u9875/\u5386\u9669",
            centerX = STAGE_HOME_RECOVERY_LIXIAN_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y,
            align = "center",
            width = STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH,
            height = STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = CAVE_DONGKU_TEMPLATE,
            sourceLabel = "CombatEngine/\u6D1E\u7A9F\u5165\u53E3",
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            width = CAVE_DONGKU_ROI_WIDTH,
            height = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = CAVE_DONGKU_TEMPLATE_FALLBACK,
            sourceLabel = "CombatEngine/\u6D1E\u7A9F\u5165\u53E3 fallback",
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            width = CAVE_DONGKU_ROI_WIDTH,
            height = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD
        )
        registerFixedTemplateRegion(
            regionMap = regionMap,
            templateName = CAVE_NEXT_FLOOR_TEMPLATE,
            sourceLabel = "CombatEngine/\u6D1E\u7A9F/\u4E0B\u4E00\u5C42",
            centerX = CAVE_NEXT_FLOOR_CENTER_X,
            centerY = CAVE_NEXT_FLOOR_CENTER_Y,
            align = "bottom",
            width = CAVE_NEXT_FLOOR_ROI_WIDTH,
            height = CAVE_NEXT_FLOOR_ROI_HEIGHT,
            threshold = CAVE_NEXT_FLOOR_THRESHOLD
        )

        BattleStageNavigationRegistry.supportedTargets.forEach { target ->
            val config = BattleStageNavigationRegistry.getDirectConfig(target) ?: return@forEach
            config.recoverySelectionRegion?.let { selection ->
                registerFixedTemplateRegion(
                    regionMap = regionMap,
                    templateName = selection.templateName,
                    sourceLabel = "CombatEngine/${target.description}/\u6062\u590D\u9009\u62E9",
                    centerX = selection.x,
                    centerY = selection.y,
                    align = selection.align,
                    width = selection.width,
                    height = selection.height,
                    threshold = selection.threshold
                )
            }
            registerFixedTemplateRegion(
                regionMap = regionMap,
                templateName = config.entryTemplateRegion.templateName,
                sourceLabel = "CombatEngine/${target.description}/\u5165\u53E3",
                centerX = config.entryTemplateRegion.x,
                centerY = config.entryTemplateRegion.y,
                align = config.entryTemplateRegion.align,
                width = config.entryTemplateRegion.width,
                height = config.entryTemplateRegion.height,
                threshold = config.entryTemplateRegion.threshold
            )
        }
    }

    private fun registerFixedTemplateRegion(
        regionMap: MutableMap<String, MutableList<LocalTemplateRegion>>,
        templateName: String,
        sourceLabel: String,
        centerX: Float,
        centerY: Float,
        align: String,
        width: Float,
        height: Float,
        threshold: Float
    ) {
        regionMap.getOrPut(templateName) { mutableListOf() }.add(
            LocalTemplateRegion(
                scriptFileName = sourceLabel,
                taskId = 0,
                roi = ROI(x = centerX, y = centerY, w = width, h = height, align = align),
                threshold = threshold
            )
        )
    }

    private fun distinctRegions(regions: List<LocalTemplateRegion>): List<LocalTemplateRegion> =
        regions.distinctBy { "${it.roi.x}_${it.roi.y}_${it.roi.w}_${it.roi.h}_${it.roi.align}_${it.threshold}" }

    private fun regionLabel(region: LocalTemplateRegion): String =
        if (region.taskId > 0) "${region.scriptFileName}#${region.taskId}" else region.scriptFileName

    private fun deathCheckOption(slotIndex: Int): String = "$DEATH_CHECK_OPTION_PREFIX$slotIndex"

    private fun isDeathCheckOption(option: String): Boolean =
        option.startsWith(DEATH_CHECK_OPTION_PREFIX)

    private fun deathCheckSlotFromOption(option: String): Int? =
        option.removePrefix(DEATH_CHECK_OPTION_PREFIX).toIntOrNull()

    private fun startBattleLocalRegions(): List<LocalTemplateRegion> = redRegionSearchRegions

    private fun caveDongkuLocalRegions(): List<LocalTemplateRegion> =
        distinctRegions(
            localSearchRegionsByTemplate[CAVE_DONGKU_TEMPLATE].orEmpty() +
                localSearchRegionsByTemplate[CAVE_DONGKU_TEMPLATE_FALLBACK].orEmpty()
        )

    private fun replacementRegionsForOption(optionName: String): List<LocalTemplateRegion> =
        when (optionName) {
            START_BATTLE_RED_OPTION -> startBattleLocalRegions()
            CAVE_DONGKU_OPTION -> caveDongkuLocalRegions()
            else -> localSearchRegionsByTemplate[optionName].orEmpty()
        }

    private fun replacementTargetsForOption(optionName: String): List<ReplacementTarget> =
        when (optionName) {
            START_BATTLE_RED_OPTION -> listOf(
                ReplacementTarget(
                    fileName = TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME,
                    label = START_BATTLE_RED_LABEL
                )
            )
            CAVE_DONGKU_OPTION -> listOf(
                ReplacementTarget(CAVE_DONGKU_TEMPLATE, CAVE_DONGKU_TEMPLATE),
                ReplacementTarget(CAVE_DONGKU_TEMPLATE_FALLBACK, CAVE_DONGKU_TEMPLATE_FALLBACK)
            )
            ORANGE_STAR_OPTION, BATTLE_TURN_OCR_OPTION -> emptyList()
            else -> {
                if (isDeathCheckOption(optionName)) emptyList() else listOf(ReplacementTarget(optionName, optionName))
            }
        }

    private inner class TemplateSpinnerAdapter(items: List<String>) :
        ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            style(super.getView(position, convertView, parent), position)
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            style(super.getDropDownView(position, convertView, parent), position)
        private fun style(view: View, position: Int): View {
            (view as? TextView)?.apply {
                text = displayName(getItem(position).orEmpty())
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            return view
        }
    }

    private fun runScopedMultiTargetMatchTest(templateName: String) {
        log("------------------------")
        log("Start matching: $templateName")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val templateBitmap = TemplateOverrideStore.loadBitmap(this, assets, templateName) ?: return log("Load template failed: $templateName")
        try {
            val summary = findTemplateMatches(screenshot, templateName, templateBitmap, isLocalScopeEnabled())
            val hits = summary.hits
            val canvas = Canvas(screenshot)
            val paint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
            hits.forEachIndexed { index, hit ->
                canvas.drawRect(hit.rect, paint)
                log("Hit ${index + 1}: ${hit.areaLabel} x=${hit.rect.centerX()} y=${hit.rect.centerY()} score=${"%.3f".format(hit.score)}")
            }
            summary.bestCandidate?.let {
                log("Best score: ${it.areaLabel} x=${it.rect.centerX()} y=${it.rect.centerY()} score=${"%.3f".format(it.score)}")
            }
            if (hits.isEmpty()) log("No match above threshold")
            ivScreenshot.setImageBitmap(screenshot)
        } finally {
            templateBitmap.recycle()
        }
    }

    private fun runScopedStartBattleRedMatchTest(useLocalScope: Boolean) {
        if (isStartBattleTemplateModeEnabled()) {
            runScopedStartBattleTemplateMatchTest(useLocalScope)
            return
        }
        log("------------------------")
        log("Start matching: $START_BATTLE_RED_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val searchAreas = if (!useLocalScope || redRegionSearchRegions.isEmpty()) {
            listOf(
                SearchArea(
                    label = "bottom-20%",
                    rect = Rect(0, (screenshot.height * 0.8f).toInt().coerceIn(0, screenshot.height - 1), screenshot.width, screenshot.height),
                    threshold = START_BATTLE_RED_THRESHOLD
                )
            )
        } else {
            startBattleLocalRegions()
                .mapNotNull { buildRectFromRoi(screenshot, it.roi)?.let { rect -> SearchArea(regionLabel(it), rect, START_BATTLE_RED_THRESHOLD) } }
        }
        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 5f }
        val hitPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
        var best: Pair<SearchArea, RedRegionMatch>? = null
        searchAreas.forEach { area ->
            canvas.drawRect(area.rect, searchPaint)
            val bitmap = Bitmap.createBitmap(screenshot, area.rect.left, area.rect.top, area.rect.width(), area.rect.height())
            var normalizedBitmap: Bitmap? = null
            try {
                normalizedBitmap = normalizeBitmapForDetection(bitmap)
                val match = findRedRegionMatch(normalizedBitmap ?: bitmap) ?: return@forEach
                log(
                    "Candidate ${area.label}: " +
                        "score=${"%.3f".format(match.confidence)} " +
                        "size=${"%.3f".format(match.sizeScore)} " +
                        "colour=${"%.3f".format(match.rednessScore)} " +
                        "full=${"%.3f".format(match.fillRatio)}"
                )
                if (best == null || match.confidence > best!!.second.confidence) best = area to match
            } finally {
                normalizedBitmap?.recycle()
                bitmap.recycle()
            }
        }
        val result = best
        if (result == null) return log("No red region detected").also { ivScreenshot.setImageBitmap(screenshot) }
        val area = result.first
        val match = result.second
        val left = area.rect.left + match.minX * match.step
        val top = area.rect.top + match.minY * match.step
        val right = area.rect.left + ((match.maxX + 1) * match.step).coerceAtMost(area.rect.width())
        val bottom = area.rect.top + ((match.maxY + 1) * match.step).coerceAtMost(area.rect.height())
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), hitPaint)
        log(
            "Red region hit: ${area.label} " +
                "score=${"%.3f".format(match.confidence)} " +
                "size=${"%.3f".format(match.sizeScore)} " +
                "colour=${"%.3f".format(match.rednessScore)} " +
                "full=${"%.3f".format(match.fillRatio)}"
        )
        if (useLocalScope && match.confidence < START_BATTLE_RED_THRESHOLD) {
            log("Red region below runtime threshold ${"%.2f".format(START_BATTLE_RED_THRESHOLD)}")
        }
        ivScreenshot.setImageBitmap(screenshot)
    }

    private fun runBattleTurnOcrTest() {
        log("------------------------")
        log("Start matching: $BATTLE_TURN_OCR_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val areaRect = buildTopRightRect(
            screenshot = screenshot,
            widthBase = BATTLE_TURN_OCR_W,
            heightBase = BATTLE_TURN_OCR_H
        ) ?: return log("Build ROI failed").also { ivScreenshot.setImageBitmap(screenshot) }

        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawRect(areaRect, searchPaint)
        ivScreenshot.setImageBitmap(screenshot)

        val bitmap = Bitmap.createBitmap(screenshot, areaRect.left, areaRect.top, areaRect.width(), areaRect.height())
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val rawText = text.text.orEmpty()
                val normalizedText = rawText.filterNot { it.isWhitespace() }
                val hitChars = listOf('波', '次', '回', '合').filter { normalizedText.contains(it) }
                val turnNumber = parseBattleTurnNumber(normalizedText)
                log("OCR raw=${formatOcrLog(rawText)}")
                log("OCR normalized=${formatOcrLog(normalizedText)}")
                log("OCR hits=${if (hitChars.isEmpty()) "无" else hitChars.joinToString("")} count=${hitChars.size}")
                log("OCR turn=${turnNumber ?: "无"}")
                ivScreenshot.setImageBitmap(screenshot)
                bitmap.recycle()
                recognizer.close()
            }
            .addOnFailureListener { error ->
                log("OCR failed: ${error.message}")
                ivScreenshot.setImageBitmap(screenshot)
                bitmap.recycle()
                recognizer.close()
            }
    }

    private fun runCaveDongkuFallbackTest(useLocalScope: Boolean) {
        log("------------------------")
        log("Start matching: $CAVE_DONGKU_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val hitPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
        val primarySummary = runTemplateSummaryTest(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE,
            useLocalScope = useLocalScope
        )
        if (primarySummary.hits.isNotEmpty()) {
            primarySummary.hits.forEach { Canvas(screenshot).drawRect(it.rect, hitPaint) }
            log("Primary hit")
            ivScreenshot.setImageBitmap(screenshot)
            return
        }
        log("Primary miss, fallback to $CAVE_DONGKU_TEMPLATE_FALLBACK")
        val fallbackSummary = runTemplateSummaryTest(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE_FALLBACK,
            useLocalScope = useLocalScope
        )
        if (fallbackSummary.hits.isEmpty()) {
            log("Fallback miss")
            ivScreenshot.setImageBitmap(screenshot)
            return
        }
        fallbackSummary.hits.forEach { Canvas(screenshot).drawRect(it.rect, hitPaint) }
        log("Fallback hit")
        ivScreenshot.setImageBitmap(screenshot)
    }

    private fun runOrangeStarTest() {
        log("------------------------")
        log("Start matching: $ORANGE_STAR_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val roiRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = ORANGE_STAR_CENTER_X,
            y = ORANGE_STAR_CENTER_Y,
            align = "top",
            w = ORANGE_STAR_ROI_SIZE,
            h = ORANGE_STAR_ROI_SIZE
        ) ?: return log("Build ROI failed").also { ivScreenshot.setImageBitmap(screenshot) }
        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 5f }
        val hitPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
        canvas.drawRect(roiRect, searchPaint)

        val candidate = findOrangeStarCandidateInScreenshot(screenshot)
        if (candidate == null || candidate.shapeScore < ORANGE_STAR_SHAPE_THRESHOLD) {
            log("No star shape detected in runtime ROI")
            ivScreenshot.setImageBitmap(screenshot)
            return
        }

        val isOrangeHit =
            candidate.confidence >= ORANGE_STAR_THRESHOLD &&
                candidate.glowRatio >= ORANGE_STAR_MIN_GLOW_RATIO
        val candidateRect = Rect(
            (candidate.center.x - candidate.boundsWidth / 2f).toInt().coerceAtLeast(0),
            (candidate.center.y - candidate.boundsHeight / 2f).toInt().coerceAtLeast(0),
            (candidate.center.x + candidate.boundsWidth / 2f).toInt().coerceAtMost(screenshot.width),
            (candidate.center.y + candidate.boundsHeight / 2f).toInt().coerceAtMost(screenshot.height)
        )
        canvas.drawRect(candidateRect, hitPaint)
        log(
            "Orange star candidate: " +
                "shape=${"%.3f".format(candidate.shapeScore)} " +
                "glow=${"%.3f".format(candidate.glowScore)} " +
                "glowRatio=${"%.3f".format(candidate.glowRatio)} " +
                "continuity=${"%.3f".format(candidate.armContinuity)} " +
                "confidence=${"%.3f".format(candidate.confidence)}"
        )
        log(if (isOrangeHit) "Orange star hit" else "Shape hit but orange threshold not reached")
        ivScreenshot.setImageBitmap(screenshot)
    }

    private fun runDeathCheckTest(slotIndex: Int) {
        log("------------------------")
        log("Start matching: ${displayName(deathCheckOption(slotIndex))}")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val result = detectCharacterDeathFromScreenshot(slotIndex, screenshot)
            ?: return log("Build slot ROI failed").also { ivScreenshot.setImageBitmap(screenshot) }
        val slotRect = buildDeathCheckRect(screenshot, slotIndex)
            ?: return log("Build slot ROI failed").also { ivScreenshot.setImageBitmap(screenshot) }
        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawRect(slotRect, searchPaint)
        log(
            "Death check slot=${result.slotIndex} " +
                "meanSaturation=${"%.2f".format(result.meanSaturation)} " +
                "threshold=${"%.2f".format(DEATH_CHECK_SATURATION_THRESHOLD)} " +
                "isDead=${result.isDead}"
        )
        ivScreenshot.setImageBitmap(screenshot)
    }

    private fun runTemplateSummaryTest(
        screenshot: Bitmap,
        templateName: String,
        useLocalScope: Boolean
    ): TemplateScanSummary {
        val templateBitmap = TemplateOverrideStore.loadBitmap(this, assets, templateName)
            ?: return TemplateScanSummary(emptyList(), null).also {
                log("Load template failed: $templateName")
            }
        return try {
            val summary = findTemplateMatches(screenshot, templateName, templateBitmap, useLocalScope)
            logTemplateSummary(templateName, summary)
            summary
        } finally {
            templateBitmap.recycle()
        }
    }

    private fun logTemplateSummary(label: String, summary: TemplateScanSummary) {
        summary.hits.forEachIndexed { index, hit ->
            log("[$label] Hit ${index + 1}: ${hit.areaLabel} x=${hit.rect.centerX()} y=${hit.rect.centerY()} score=${"%.3f".format(hit.score)}")
        }
        summary.bestCandidate?.let {
            log("[$label] Best score: ${it.areaLabel} x=${it.rect.centerX()} y=${it.rect.centerY()} score=${"%.3f".format(it.score)}")
        }
        if (summary.hits.isEmpty()) {
            log("[$label] No match above threshold")
        }
    }

    private fun runScopedStartBattleTemplateMatchTest(useLocalScope: Boolean) {
        log("------------------------")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val templateBitmap = TemplateOverrideStore.loadBitmap(this, assets, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)
            ?: return log("Load template failed: ${TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME}")
        val searchAreas = if (!useLocalScope || redRegionSearchRegions.isEmpty()) {
            listOf(
                SearchArea(
                    label = "full-screen",
                    rect = Rect(0, 0, screenshot.width, screenshot.height),
                    threshold = TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD
                )
            )
        } else {
            distinctRegions(startBattleLocalRegions())
                .mapNotNull {
                    buildRectFromRoi(screenshot, it.roi)?.let { rect ->
                        SearchArea(regionLabel(it), rect, TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD)
                    }
                }
        }
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val scaledWidth = (templateBitmap.width * gameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (templateBitmap.height * gameScale).toInt().coerceAtLeast(1)
        val scaledTemplate = if (scaledWidth == templateBitmap.width && scaledHeight == templateBitmap.height) {
            templateBitmap
        } else {
            Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
        }
        try {
            val canvas = Canvas(screenshot)
            val searchPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 5f }
            val hitPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 6f }
            var bestHit: TemplateMatchHit? = null
            searchAreas.forEach { area ->
                canvas.drawRect(area.rect, searchPaint)
                val scan = findMatchesInArea(
                    screenshot = screenshot,
                    area = area,
                    templateBitmap = scaledTemplate,
                    threshold = area.threshold
                )
                scan.bestCandidate?.let {
                    log("Candidate ${area.label}: score=${"%.3f".format(it.score)}")
                    if (it.score >= area.threshold && (bestHit == null || it.score > bestHit!!.score)) {
                        bestHit = it
                    }
                } ?: log("Candidate ${area.label}: score=0.000")
            }
            val hit = bestHit ?: return log("No start battle template detected").also { ivScreenshot.setImageBitmap(screenshot) }
            canvas.drawRect(hit.rect, hitPaint)
            log("Start battle template hit: ${hit.areaLabel} score=${"%.3f".format(hit.score)}")
            ivScreenshot.setImageBitmap(screenshot)
        } finally {
            if (scaledTemplate !== templateBitmap) scaledTemplate.recycle()
            templateBitmap.recycle()
        }
    }

    private fun findTemplateMatches(screenshot: Bitmap, templateName: String, templateBitmap: Bitmap, useLocalScope: Boolean): TemplateScanSummary {
        val areas = buildTemplateSearchAreas(screenshot, templateName, useLocalScope)
        if (areas.isEmpty()) return TemplateScanSummary(emptyList(), null)
        Canvas(screenshot).apply {
            val areaPaint = Paint().apply { color = Color.argb(220, 229, 192, 123); style = Paint.Style.STROKE; strokeWidth = 4f }
            areas.forEach { drawRect(it.rect, areaPaint) }
        }
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val scaledWidth = (templateBitmap.width * gameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (templateBitmap.height * gameScale).toInt().coerceAtLeast(1)
        val scaledTemplate = if (scaledWidth == templateBitmap.width && scaledHeight == templateBitmap.height) templateBitmap else Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
        return try {
            val scans = areas.map { findMatchesInArea(screenshot, it, scaledTemplate, it.threshold) }
            val hits = dedupeHits(scans.flatMap { it.hits }, scaledTemplate.width / 2.0)
            val bestCandidate = scans.mapNotNull { it.bestCandidate }.maxByOrNull { it.score }
            TemplateScanSummary(hits, bestCandidate)
        } finally {
            if (scaledTemplate !== templateBitmap) scaledTemplate.recycle()
        }
    }

    private fun templateTestThreshold(templateName: String): Float =
        when (templateName) {
            YOUZHOU_TEMPLATE, JINRU_TEMPLATE -> MAINLINE_624_BRANCH_THRESHOLD
            ALL_WIPE_TEMPLATE -> ALL_WIPE_TEMPLATE_THRESHOLD
            ORANGE_STAR_RECOVERY_TEMPLATE -> ORANGE_STAR_RECOVERY_THRESHOLD
            STAGE_HOME_RECOVERY_LANTAI_TEMPLATE -> STAGE_HOME_RECOVERY_LANTAI_THRESHOLD
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE -> STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD
            STAGE_HOME_RECOVERY_BAIHU_TEMPLATE -> STAGE_HOME_RECOVERY_BAIHU_THRESHOLD
            STAGE_HOME_RECOVERY_DIGONG_TEMPLATE -> STAGE_HOME_RECOVERY_DIGONG_THRESHOLD
            STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE -> STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD
            STAGE_HOME_RECOVERY_XINZHI_TEMPLATE -> STAGE_HOME_RECOVERY_XINZHI_THRESHOLD
            STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE -> STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD
            CAVE_DONGKU_TEMPLATE, CAVE_DONGKU_TEMPLATE_FALLBACK -> CAVE_DONGKU_THRESHOLD
            CAVE_NEXT_FLOOR_TEMPLATE -> CAVE_NEXT_FLOOR_THRESHOLD
            "jinrutiaozhan.png", "jinruzhandou.png", "qianwangtaofa.png" -> 0.80f
            "yiji1.png", "yiji2.png", "yiji3.png", "yiji4.png", "yiji5.png" -> 0.75f
            else -> DEFAULT_TEMPLATE_TEST_THRESHOLD
        }

    private fun findMatchesInArea(screenshot: Bitmap, area: SearchArea, templateBitmap: Bitmap, threshold: Float): AreaMatchScan {
        if (area.rect.width() < templateBitmap.width || area.rect.height() < templateBitmap.height) {
            return AreaMatchScan(emptyList(), null)
        }
        val searchBitmap = Bitmap.createBitmap(screenshot, area.rect.left, area.rect.top, area.rect.width(), area.rect.height())
        val srcMat = Mat()
        val tmplMat = Mat()
        val resultMat = Mat()
        return try {
            Utils.bitmapToMat(searchBitmap, srcMat)
            Utils.bitmapToMat(templateBitmap, tmplMat)
            Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
            val cols = resultMat.cols()
            val rows = resultMat.rows()
            val scores = FloatArray(cols * rows)
            resultMat.get(0, 0, scores)
            val hits = mutableListOf<TemplateMatchHit>()
            var bestScore = Float.NEGATIVE_INFINITY
            var bestHit: TemplateMatchHit? = null
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val score = scores[y * cols + x]
                    val hit = TemplateMatchHit(
                        Rect(
                            area.rect.left + x,
                            area.rect.top + y,
                            area.rect.left + x + templateBitmap.width,
                            area.rect.top + y + templateBitmap.height
                        ),
                        score,
                        area.label
                    )
                    if (score > bestScore) {
                        bestScore = score
                        bestHit = hit
                    }
                    if (score >= threshold) hits += hit
                }
            }
            AreaMatchScan(hits, bestHit)
        } finally {
            srcMat.release()
            tmplMat.release()
            resultMat.release()
            searchBitmap.recycle()
        }
    }

    private fun buildTemplateSearchAreas(screenshot: Bitmap, templateName: String, useLocalScope: Boolean): List<SearchArea> {
        if (!useLocalScope) {
            return listOf(
                SearchArea(
                    label = "full-screen",
                    rect = Rect(0, 0, screenshot.width, screenshot.height),
                    threshold = templateTestThreshold(templateName)
                )
            )
        }
        val regions = localSearchRegionsByTemplate[templateName].orEmpty()
        if (regions.isEmpty()) {
            return listOf(
                SearchArea(
                    label = "fallback-full-screen",
                    rect = Rect(0, 0, screenshot.width, screenshot.height),
                    threshold = templateTestThreshold(templateName)
                )
            )
        }
        return regions.mapNotNull {
            buildRectFromRoi(screenshot, it.roi)?.let { rect ->
                SearchArea(regionLabel(it), rect, it.threshold ?: templateTestThreshold(templateName))
            }
        }
    }

    private fun dedupeHits(hits: List<TemplateMatchHit>, radius: Double): List<TemplateMatchHit> {
        val result = mutableListOf<TemplateMatchHit>()
        hits.sortedByDescending { it.score }.forEach { hit ->
            val overlap = result.any {
                hypot((hit.rect.exactCenterX() - it.rect.exactCenterX()).toDouble(), (hit.rect.exactCenterY() - it.rect.exactCenterY()).toDouble()) < radius
            }
            if (!overlap) result += hit
        }
        return result
    }

    private fun buildCenteredCropRect(roiRect: Rect, imageWidth: Int, imageHeight: Int, size: Int): Rect {
        val half = size / 2
        var left = (roiRect.centerX() - half).coerceAtLeast(0)
        var top = (roiRect.centerY() - half).coerceAtLeast(0)
        if (left + size > imageWidth) left = (imageWidth - size).coerceAtLeast(0)
        if (top + size > imageHeight) top = (imageHeight - size).coerceAtLeast(0)
        return Rect(left, top, left + size.coerceAtMost(imageWidth), top + size.coerceAtMost(imageHeight))
    }

    private fun normalizeReplacementBitmap(source: Bitmap): Bitmap {
        val targetWidth = BASE_W.toInt()
        if (source.width == targetWidth) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scaledHeight = (source.height * (targetWidth / source.width.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, scaledHeight, true)
    }

    private fun buildRectFromRoi(screenshot: Bitmap, roi: ROI): Rect? {
        val x = roi.x ?: return null
        val y = roi.y ?: return null
        val w = roi.w ?: return null
        val h = roi.h ?: return null
        val mapping = buildDisplayMapping(screenshot)
        val (realCenterX, realCenterY) = calculateRealCoordinate(x, y, roi.align, mapping)
        val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
        val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val realW = w * gameScale
        val realH = h * gameScale
        val left = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val width = realW.toInt().coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val height = realH.toInt().coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        return Rect(left, top, left + width, top + height)
    }

    private fun buildDisplayMapping(screenshot: Bitmap): DisplayMapping {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        return DisplayMapping(screenWidth, screenHeight, screenshot.width / screenWidth, screenshot.height / screenHeight)
    }

    private fun calculateRealCoordinate(baseX: Float, baseY: Float, align: String, mapping: DisplayMapping): Pair<Float, Float> {
        val gameScale = min(mapping.displayWidth / BASE_W, mapping.displayHeight / BASE_H)
        val offsetX = (mapping.displayWidth - BASE_W * gameScale) / 2f
        val offsetY = (mapping.displayHeight - BASE_H * gameScale) / 2f
        val statusBarHeight = getStatusBarHeightCompat() / 2f
        val realX = offsetX + (baseX * gameScale)
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (mapping.displayHeight / BASE_H)
            "top" -> statusBarHeight + (baseY * gameScale)
            "bottom" -> mapping.displayHeight - ((BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }
        return realX to realY
    }

    private fun getStatusBarHeightCompat(): Float {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id).toFloat() else 40f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun log(msg: String) {
        tvLog.append("\n$msg")
        tvLog.post { (tvLog.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
    }

    private fun findRedRegionMatch(
        bitmap: Bitmap,
        referenceAreaOverride: Float? = null,
        excludeNearWhite: Boolean = false,
        areaWeight: Float = 0.45f,
        fillWeight: Float = 0.25f,
        redWeight: Float = 0.30f
    ): RedRegionMatch? {
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
                val redScore = computeRednessScore(
                    color = bitmap.getPixel(x, y),
                    excludeNearWhite = excludeNearWhite
                )
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
        val sizeScore = (estimatedPixelArea / (referenceAreaOverride ?: (300f * 50f))).coerceIn(0f, 1f)
        val fillRatio = bestCount.toFloat() / bboxArea.toFloat()
        val rednessScore = (bestRednessSum / bestCount.toFloat()).coerceIn(0f, 1f)
        val compactnessScore = ((fillRatio - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val weightSum = (areaWeight + fillWeight + redWeight).coerceAtLeast(0.0001f)
        val confidence = (
            sizeScore * areaWeight +
                compactnessScore * fillWeight +
                rednessScore * redWeight
            ) / weightSum
        return RedRegionMatch(
            center = PointF(((bestMinX + bestMaxX + 1) * step / 2f).coerceIn(0f, (width - 1).toFloat()), ((bestMinY + bestMaxY + 1) * step / 2f).coerceIn(0f, (height - 1).toFloat())),
            confidence = confidence.coerceIn(0f, 1f),
            sizeScore = sizeScore,
            fillRatio = fillRatio,
            rednessScore = rednessScore,
            minX = bestMinX,
            minY = bestMinY,
            maxX = bestMaxX,
            maxY = bestMaxY,
            step = step
        )
    }

    private fun computeRednessScore(color: Int, excludeNearWhite: Boolean = false): Float {
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

    private fun buildFixedRectFromVisionRegion(
        screenshot: Bitmap,
        x: Float,
        y: Float,
        align: String,
        w: Float,
        h: Float
    ): Rect? {
        val mapping = buildDisplayMapping(screenshot)
        val (realCenterX, realCenterY) = calculateRealCoordinate(x, y, align, mapping)
        val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
        val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val realW = w * gameScale
        val realH = h * gameScale
        val left = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val width = realW.toInt().coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val height = realH.toInt().coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        return Rect(left, top, left + width, top + height)
    }

    private fun buildDeathCheckRect(screenshot: Bitmap, slotIndex: Int): Rect? {
        if (slotIndex !in 1..DEATH_CHECK_SLOT_COUNT) return null
        val slotWidth = BASE_W / DEATH_CHECK_SLOT_COUNT.toFloat()
        return buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = slotWidth * (slotIndex - 0.5f),
            y = (DEATH_CHECK_TOP_Y + DEATH_CHECK_BOTTOM_Y) / 2f,
            align = "bottom",
            w = slotWidth,
            h = DEATH_CHECK_BOTTOM_Y - DEATH_CHECK_TOP_Y
        )
    }

    private fun normalizeBitmapForDetection(bitmap: Bitmap): Bitmap? {
        return try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            try {
                val fallback = Bitmap.createBitmap(
                    bitmap.width.coerceAtLeast(1),
                    bitmap.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                Canvas(fallback).drawBitmap(bitmap, 0f, 0f, null)
                fallback
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun detectCharacterDeathFromScreenshot(
        slotIndex: Int,
        screenshot: Bitmap
    ): DeathCheckResult? {
        val region = buildDeathCheckRect(screenshot, slotIndex) ?: return null
        val bitmap = Bitmap.createBitmap(screenshot, region.left, region.top, region.width(), region.height())
        return try {
            val meanSaturation = computeCenterMeanSaturation(bitmap)
            DeathCheckResult(
                slotIndex = slotIndex,
                meanSaturation = meanSaturation,
                isDead = meanSaturation < DEATH_CHECK_SATURATION_THRESHOLD
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun findOrangeStarCandidateInScreenshot(bitmap: Bitmap): OrangeStarCandidate? {
        val roiRect = buildFixedRectFromVisionRegion(
            screenshot = bitmap,
            x = ORANGE_STAR_CENTER_X,
            y = ORANGE_STAR_CENTER_Y,
            align = "top",
            w = ORANGE_STAR_ROI_SIZE,
            h = ORANGE_STAR_ROI_SIZE
        ) ?: return null
        val roiBitmap = Bitmap.createBitmap(bitmap, roiRect.left, roiRect.top, roiRect.width(), roiRect.height())
        return try {
            findOrangeStarInRegion(roiBitmap)?.let { candidate ->
                candidate.copy(
                    center = PointF(candidate.center.x + roiRect.left, candidate.center.y + roiRect.top)
                )
            }
        } finally {
            roiBitmap.recycle()
        }
    }

    private fun findOrangeStarInRegion(bitmap: Bitmap): OrangeStarCandidate? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixelCount = width * height
        val coreScores = FloatArray(pixelCount)
        val glowScores = FloatArray(pixelCount)
        val signalScores = FloatArray(pixelCount)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = bitmap.getPixel(x, y)
                val coreScore = computeStarCoreScore(color)
                val glowScore = computeOrangePixelScore(color)
                val highlightScore = computeStarHighlightScore(color)
                coreScores[index] = coreScore
                glowScores[index] = glowScore
                signalScores[index] = maxOf(coreScore, glowScore * 0.92f, highlightScore * 0.85f)
            }
        }
        val centers = buildStarCenterCandidates(width, height, coreScores, signalScores)
        var best: OrangeStarCandidate? = null
        for (center in centers) {
            val candidate = evaluateStarCandidate(
                width = width,
                height = height,
                coreScores = coreScores,
                glowScores = glowScores,
                signalScores = signalScores,
                centerX = center.first,
                centerY = center.second
            ) ?: continue
            if (best == null || candidate.confidence > best!!.confidence) {
                best = candidate
            }
        }
        return best
    }

    private fun buildStarCenterCandidates(
        width: Int,
        height: Int,
        coreScores: FloatArray,
        signalScores: FloatArray
    ): List<Pair<Int, Int>> {
        data class Candidate(val x: Int, val y: Int, val score: Float)

        val rawCandidates = ArrayList<Candidate>()
        for (y in 4 until height - 4 step 2) {
            for (x in 4 until width - 4 step 2) {
                val core = sampleAverage(coreScores, width, height, x, y, 2)
                val signal = sampleAverage(signalScores, width, height, x, y, 4)
                val score = core * 0.72f + signal * 0.28f
                if (score >= 0.42f) {
                    rawCandidates.add(Candidate(x, y, score))
                }
            }
        }
        if (rawCandidates.isEmpty()) return emptyList()

        rawCandidates.sortByDescending { it.score }
        val selected = ArrayList<Pair<Int, Int>>(16)
        for (candidate in rawCandidates) {
            val duplicate = selected.any { (sx, sy) ->
                val dx = sx - candidate.x
                val dy = sy - candidate.y
                dx * dx + dy * dy <= 36
            }
            if (duplicate) continue
            selected.add(candidate.x to candidate.y)
            if (selected.size >= 16) break
        }
        return selected
    }

    private fun evaluateStarCandidate(
        width: Int,
        height: Int,
        coreScores: FloatArray,
        glowScores: FloatArray,
        signalScores: FloatArray,
        centerX: Int,
        centerY: Int
    ): OrangeStarCandidate? {
        val maxRadius = minOf(centerX, centerY, width - 1 - centerX, height - 1 - centerY, 42)
        if (maxRadius < 10) return null

        val coreScore = sampleAverage(coreScores, width, height, centerX, centerY, 3)
        if (coreScore < 0.45f) return null

        var bestShapeScore = 0f
        var bestContinuity = 0f
        var bestHalfSpanX = 0
        var bestHalfSpanY = 0

        for (degree in 0 until 90 step 6) {
            val theta = Math.toRadians(degree.toDouble())
            val armAngles = doubleArrayOf(theta, theta + Math.PI / 2.0, theta + Math.PI, theta + Math.PI * 1.5)
            val gapAngles = doubleArrayOf(
                theta + Math.PI / 4.0,
                theta + Math.PI * 3.0 / 4.0,
                theta + Math.PI * 5.0 / 4.0,
                theta + Math.PI * 7.0 / 4.0
            )

            val armMeans = FloatArray(4)
            val armContinuities = FloatArray(4)
            val armSpans = IntArray(4)
            for (i in armAngles.indices) {
                var sum = 0f
                var count = 0
                var active = 0
                var span = 0
                for (radius in 4..maxRadius step 2) {
                    val sample = sampleDirectional(signalScores, width, height, centerX, centerY, armAngles[i], radius)
                    sum += sample
                    count += 1
                    if (sample >= 0.22f) {
                        active += 1
                        span = radius
                    }
                }
                armMeans[i] = if (count == 0) 0f else sum / count.toFloat()
                armContinuities[i] = if (count == 0) 0f else active.toFloat() / count.toFloat()
                armSpans[i] = span
            }

            var gapMeanSum = 0f
            var gapCount = 0
            for (angle in gapAngles) {
                for (radius in 5..maxRadius step 2) {
                    gapMeanSum += sampleDirectional(signalScores, width, height, centerX, centerY, angle, radius)
                    gapCount += 1
                }
            }
            val gapMean = if (gapCount == 0) 0f else gapMeanSum / gapCount.toFloat()
            val armMean = armMeans.average().toFloat()
            val armContinuity = armContinuities.average().toFloat()
            val balanceScore = (
                minOf(armMeans[0], armMeans[2]) / maxOf(armMeans[0], armMeans[2], 0.0001f) +
                    minOf(armMeans[1], armMeans[3]) / maxOf(armMeans[1], armMeans[3], 0.0001f)
                ) / 2f
            val contrastScore = ((armMean - gapMean) / maxOf(armMean, 0.0001f)).coerceIn(0f, 1f)
            val spanScore = armSpans.average().toFloat() / maxRadius.toFloat()
            val shapeScore = (
                contrastScore * 0.42f +
                    armContinuity.coerceIn(0f, 1f) * 0.28f +
                    balanceScore.coerceIn(0f, 1f) * 0.18f +
                    spanScore.coerceIn(0f, 1f) * 0.12f
                ).coerceIn(0f, 1f)

            if (shapeScore > bestShapeScore) {
                bestShapeScore = shapeScore
                bestContinuity = armContinuity
                bestHalfSpanX = maxOf(armSpans[0], armSpans[2]).coerceAtLeast(4)
                bestHalfSpanY = maxOf(armSpans[1], armSpans[3]).coerceAtLeast(4)
            }
        }

        if (bestShapeScore < 0.18f) return null

        val glowScore = sampleAnnulusAverage(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 24))
        val glowRatio = sampleAnnulusRatio(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 26), 0.42f)
        val sizeScore = (maxOf(bestHalfSpanX, bestHalfSpanY).toFloat() / maxRadius.toFloat()).coerceIn(0f, 1f)
        val confidence = (
            coreScore * 0.26f +
                bestShapeScore * 0.39f +
                glowScore * 0.20f +
                glowRatio.coerceIn(0f, 1f) * 0.10f +
                sizeScore * 0.05f
            ).coerceIn(0f, 1f)

        return OrangeStarCandidate(
            center = PointF(centerX.toFloat(), centerY.toFloat()),
            confidence = confidence,
            glowScore = glowScore,
            glowRatio = glowRatio,
            shapeScore = bestShapeScore,
            armContinuity = bestContinuity.coerceIn(0f, 1f),
            boundsWidth = bestHalfSpanX * 2,
            boundsHeight = bestHalfSpanY * 2
        )
    }

    private fun sampleDirectional(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        angle: Double,
        radius: Int
    ): Float {
        val x = centerX + Math.cos(angle).toFloat() * radius
        val y = centerY + Math.sin(angle).toFloat() * radius
        return sampleNearest(scores, width, height, x, y)
    }

    private fun sampleAverage(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Float {
        var sum = 0f
        var count = 0
        for (y in centerY - radius..centerY + radius) {
            for (x in centerX - radius..centerX + radius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy > radius * radius) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusAverage(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        innerRadius: Int,
        outerRadius: Int
    ): Float {
        var sum = 0f
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusRatio(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        innerRadius: Int,
        outerRadius: Int,
        threshold: Float
    ): Float {
        var hit = 0
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                count += 1
                if (scores[y * width + x] >= threshold) {
                    hit += 1
                }
            }
        }
        return if (count == 0) 0f else hit.toFloat() / count.toFloat()
    }

    private fun sampleNearest(
        scores: FloatArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Float {
        val safeX = x.toInt().coerceIn(0, width - 1)
        val safeY = y.toInt().coerceIn(0, height - 1)
        return scores[safeY * width + safeX]
    }

    private fun computeCenterMeanSaturation(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return 255f

        val left = (width * 0.2f).toInt().coerceIn(0, width - 1)
        val top = (height * 0.2f).toInt().coerceIn(0, height - 1)
        val right = (width * 0.8f).toInt().coerceIn(left + 1, width)
        val bottom = (height * 0.8f).toInt().coerceIn(top + 1, height)
        val step = if ((right - left) * (bottom - top) >= 40000) 2 else 1
        val hsv = FloatArray(3)
        var sum = 0f
        var count = 0

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                sum += hsv[1] * 255f
                count += 1
            }
        }

        return if (count == 0) 255f else sum / count.toFloat()
    }

    private fun computeStarCoreScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.72f) return 0f

        val brightnessScore = ((brightness - 0.72f) / 0.28f).coerceIn(0f, 1f)
        val whitenessScore = (1f - saturation).coerceIn(0f, 1f)
        val warmScore = when {
            saturation <= 0.12f -> 0.90f
            hue in 20f..68f -> 1f
            hue in 0f..20f -> 0.70f
            hue in 68f..90f -> 0.60f
            else -> 0f
        }
        if (warmScore <= 0f) return 0f

        return (
            brightnessScore * 0.45f +
                whitenessScore * 0.35f +
                warmScore * 0.20f
            ).coerceIn(0f, 1f)
    }

    private fun computeOrangePixelScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (hue < 20f || hue > 62f) return 0f
        if (saturation < 0.35f || brightness < 0.45f) return 0f

        val hueScore = (1f - kotlin.math.abs(hue - 41f) / 21f).coerceIn(0f, 1f)
        val saturationScore = ((saturation - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val brightnessScore = ((brightness - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return (
            hueScore * 0.50f +
                saturationScore * 0.25f +
                brightnessScore * 0.25f
            ).coerceIn(0f, 1f)
    }

    private fun computeStarHighlightScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.58f) return 0f

        val brightnessScore = ((brightness - 0.58f) / 0.42f).coerceIn(0f, 1f)
        val hueScore = when {
            saturation <= 0.18f -> 0.90f
            hue in 15f..80f -> 1f
            hue in 0f..15f -> 0.70f
            hue in 80f..100f -> 0.55f
            else -> 0f
        }
        if (hueScore <= 0f) return 0f

        val saturationScore = when {
            saturation <= 0.18f -> 0.75f
            else -> ((saturation - 0.18f) / 0.82f).coerceIn(0.45f, 1f)
        }

        return (
            brightnessScore * 0.50f +
                hueScore * 0.30f +
                saturationScore * 0.20f
            ).coerceIn(0f, 1f)
    }

    private fun buildTopRightRect(
        screenshot: Bitmap,
        widthBase: Float,
        heightBase: Float
    ): Rect? {
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val width = (widthBase * gameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (heightBase * gameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        if (width <= 0 || height <= 0) return null
        return Rect(screenshot.width - width, 0, screenshot.width, height)
    }

    private fun parseBattleTurnNumber(normalizedText: String): Int? {
        val match = Regex("回合(\\d+)(?:/(\\d+))?").find(normalizedText) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun formatOcrLog(text: String): String {
        if (text.isEmpty()) return "\"\""
        return "\"" + text.replace("\n", "\\n") + "\""
    }
}
