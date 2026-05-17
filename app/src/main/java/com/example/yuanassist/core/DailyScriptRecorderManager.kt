package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.R
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.UserDailyScriptStore
import com.example.yuanassist.utils.applyYuanInputStyle
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DailyScriptRecorderManager(
    private val service: AccessibilityService,
    private val onScriptSaved: (scriptName: String, plan: DailyTaskPlan, templateDir: File) -> Unit,
    private val onVisibilityChanged: (Boolean) -> Unit
) {

    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val DEFAULT_DELAY = 2500L
        private const val DEFAULT_TEMPLATE_THRESHOLD = 0.77f
        private const val DEFAULT_TEMPLATE_SIZE = 60f
        private const val DEFAULT_TEMPLATE_ROI_SIZE = 250f
        private const val DEFAULT_OCR_ROI_SIZE = 200f
    }

    private enum class RecorderAction(val value: String, val label: String) {
        CLICK("CLICK", "点击"),
        MATCH_TEMPLATE("MATCH_TEMPLATE", "模板匹配"),
        OCR("OCR", "OCR"),
        SET_VAR("SET_VAR", "设置分支"),
        BACK("BACK", "返回")
    }

    private enum class ClickPositionMode(val label: String) {
        CURRENT_COORDINATE("点击位置-当前坐标"),
        PREVIOUS_RECOGNIZED_TARGET("点击先前识别到的按钮或位置")
    }

    private enum class PositionType(val value: String, val label: String) {
        CENTER("center", "居中"),
        BOTTOM("bottom", "靠下"),
        TOP("top", "靠上")
    }

    private data class BaseRect(
        var centerX: Float,
        var centerY: Float,
        var width: Float,
        var height: Float
    )

    private data class ScreenRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun toRectF(): RectF = RectF(left, top, right, bottom)
    }

    private data class NodeDraft(
        val id: Int,
        var name: String = "",
        var designX: Float,
        var designY: Float,
        var positionType: PositionType = PositionType.CENTER,
        var action: RecorderAction = RecorderAction.CLICK,
        var delay: Long = DEFAULT_DELAY,
        var onSuccess: Int = id + 1,
        var onFail: Int = -2,
        var templateName: String = "",
        var threshold: Float = DEFAULT_TEMPLATE_THRESHOLD,
        var roiWidth: Float = DEFAULT_TEMPLATE_ROI_SIZE,
        var roiHeight: Float = DEFAULT_TEMPLATE_ROI_SIZE,
        var templateFollowsRoiCenter: Boolean = true,
        var templateRect: BaseRect = BaseRect(designX, designY, DEFAULT_TEMPLATE_SIZE, DEFAULT_TEMPLATE_SIZE),
        var ocrText: String = "",
        var ocrMinHitCount: Int = 1,
        var clickOnSuccess: Boolean = true,
        var clickPositionMode: ClickPositionMode = ClickPositionMode.CURRENT_COORDINATE,
        var clickRefTaskId: Int? = null,
        var branchVarName: String = "",
        var branchVarValue: String = "",
        var successBranchEnabled: Boolean = false,
        var successBranchVar: String = "",
        var successBranchRouteText: String = "",
        var failBranchEnabled: Boolean = false,
        var failBranchVar: String = "",
        var failBranchRouteText: String = "",
        var pickedScreenX: Float? = null,
        var pickedScreenY: Float? = null,
        var templateBitmap: Bitmap? = null
    )

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gson = Gson()
    private val recordedNodes = mutableListOf<NodeDraft>()

    private var controlView: View? = null
    private var controlLayoutParams: WindowManager.LayoutParams? = null
    private var controlCountView: TextView? = null
    private var controlBodyView: LinearLayout? = null
    private var pointPickerView: View? = null
    private var frameAdjustView: View? = null
    private var lastWindowX = 100
    private var lastWindowY = 180
    private var activeAdjustDraft: NodeDraft? = null
    private var activeAdjustScreenshot: Bitmap? = null
    private var activeAdjustView: RecorderAdjustView? = null

    val isVisible: Boolean
        get() = controlView != null || pointPickerView != null || frameAdjustView != null

    fun show() {
        if (controlView != null) {
            updateNodeCount()
            onVisibilityChanged(true)
            return
        }
        val density = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastWindowX
            y = lastWindowY
        }

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setBackgroundResource(R.drawable.bg_dark_glass)
            elevation = 12f
        }

        val titleView = TextView(service).apply {
            text = "脚本录制器"
            setTextColor(Color.parseColor("#E5C07B"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        panel.addView(titleView)

        val countView = TextView(service).apply {
            id = View.generateViewId()
            setTextColor(Color.parseColor("#D7C39A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        }
        controlCountView = countView
        panel.addView(countView)

        val body = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
        }
        controlBodyView = body
        panel.addView(body)

        titleView.setOnTouchListener(createDragListener(params, panel))
        controlView = panel
        controlLayoutParams = params
        windowManager.addView(panel, params)
        renderControlBody()
        updateNodeCount()
        onVisibilityChanged(true)
        Toast.makeText(service, "脚本录制器已开启", Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        removePointPicker()
        removeFrameAdjustView()
        controlView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                controlView = null
                controlLayoutParams = null
                controlCountView = null
                controlBodyView = null
            }
        }
        recordedNodes.forEach { it.templateBitmap?.recycle() }
        recordedNodes.clear()
        onVisibilityChanged(false)
    }

    private fun buildControlButton(text: String, accent: Boolean = false): Button {
        return Button(service).apply {
            this.text = text
            isAllCaps = false
            minWidth = dp(64)
            minimumHeight = 0
            minHeight = 0
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setTextColor(if (accent) Color.parseColor("#20170D") else Color.parseColor("#E5C07B"))
            setBackgroundResource(if (accent) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
        }
    }

    private fun spaceView(density: Float): View {
        return View(service).apply {
            layoutParams = LinearLayout.LayoutParams((8f * density).roundToInt(), 1)
        }
    }

    private fun updateNodeCount() {
        val countView = controlCountView ?: return
        countView.text = "当前节点数：${recordedNodes.size}"
    }

    private fun renderControlBody() {
        val body = controlBodyView ?: return
        body.removeAllViews()
        val density = service.resources.displayMetrics.density
        val draft = activeAdjustDraft
        updateControlWindowFocus(draft != null)
        controlCountView?.visibility = if (draft == null) View.VISIBLE else View.GONE
        if (draft == null) {
            val buttons = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
            }
            val firstRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            firstRow.addView(buildControlButton("新增", true).apply { setOnClickListener { beginPickPoint() } })
            firstRow.addView(spaceView(density))
            firstRow.addView(buildControlButton("新增分支").apply { setOnClickListener { beginAddBranchNode() } })
            val secondRow = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            secondRow.addView(buildControlButton("保存").apply { setOnClickListener { promptSaveScript() } })
            secondRow.addView(spaceView(density))
            secondRow.addView(buildControlButton("关闭").apply { setOnClickListener { stop() } })
            buttons.addView(firstRow)
            buttons.addView(secondRow)
            body.addView(buttons)
            return
        }

        val scrollView = ScrollView(service).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val form = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(
            form,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        body.addView(
            scrollView,
            LinearLayout.LayoutParams(
                dp(214),
                dp(182)
            )
        )

        val roiWidthEdit = buildEditorNumberEdit(service, draft.roiWidth.toInt().toString())
        val roiHeightEdit = buildEditorNumberEdit(service, draft.roiHeight.toInt().toString())
        val clickEdit = buildEditorNumberEdit(service, if (draft.clickOnSuccess) "1" else "0")
        form.addView(buildEditorField(service, "黄框宽度", roiWidthEdit))
        form.addView(buildEditorField(service, "黄框高度", roiHeightEdit))
        val templateFollowCheck = if (draft.action == RecorderAction.MATCH_TEMPLATE) {
            buildEditorCheckBox(service, "红框跟随黄框中心", draft.templateFollowsRoiCenter).also {
                form.addView(it)
            }
        } else {
            null
        }
        form.addView(buildEditorField(service, "是否点击（1为点击，0不）", clickEdit))

        val templateNameEdit: EditText?
        val thresholdEdit: EditText?
        val ocrTextEdit: EditText?
        val ocrMinHitEdit: EditText?
        if (draft.action == RecorderAction.MATCH_TEMPLATE) {
            templateNameEdit = buildEditorTextEdit(service, stripPngSuffix(draft.templateName))
            thresholdEdit = buildEditorDecimalEdit(service, draft.threshold.toString())
            ocrTextEdit = null
            ocrMinHitEdit = null
            form.addView(buildEditorField(service, "模板文件名", buildTemplateNameInputRow(service, templateNameEdit)))
            form.addView(buildEditorField(service, "阈值", thresholdEdit))
        } else {
            templateNameEdit = null
            thresholdEdit = null
            ocrTextEdit = buildEditorTextEdit(service, draft.ocrText)
            ocrMinHitEdit = buildEditorNumberEdit(service, draft.ocrMinHitCount.toString())
            form.addView(buildEditorField(service, "识别文字", ocrTextEdit))
            form.addView(buildEditorField(service, "最少 hit", ocrMinHitEdit))
        }

        addFloatLiveWatcher(roiWidthEdit) { value ->
            if (value > 0f) {
                draft.roiWidth = value
                activeAdjustView?.invalidate()
            }
        }
        addFloatLiveWatcher(roiHeightEdit) { value ->
            if (value > 0f) {
                draft.roiHeight = value
                activeAdjustView?.invalidate()
            }
        }
        addIntLiveWatcher(clickEdit) { value ->
            draft.clickOnSuccess = value == 1
        }
        templateFollowCheck?.setOnCheckedChangeListener { _, isChecked ->
            draft.templateFollowsRoiCenter = isChecked
            if (isChecked) {
                alignTemplateRectToRoiCenter(draft)
                activeAdjustView?.syncTemplateRectToRoiCenter()
            }
        }
        templateNameEdit?.addTextChangedListener(simpleTextWatcher {
            draft.templateName = stripPngSuffix(it.trim())
        })
        thresholdEdit?.let { edit ->
            addFloatLiveWatcher(edit) { value ->
                if (value > 0f) draft.threshold = value
            }
        }
        ocrTextEdit?.addTextChangedListener(simpleTextWatcher { draft.ocrText = it })
        ocrMinHitEdit?.let { edit ->
            addIntLiveWatcher(edit) { value ->
                if (value > 0) draft.ocrMinHitCount = value
            }
        }

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        buttons.addView(buildEditorButton(service, "取消", false).apply {
            setOnClickListener { cancelActiveAdjust() }
        })
        buttons.addView(spaceView(density))
        buttons.addView(buildEditorButton(service, "确认", true).apply {
            setOnClickListener {
                val roiWidth = roiWidthEdit.text.toString().toFloatOrNull()
                val roiHeight = roiHeightEdit.text.toString().toFloatOrNull()
                val clickValue = clickEdit.text.toString().toIntOrNull()
                if (roiWidth == null || roiWidth <= 0f || roiHeight == null || roiHeight <= 0f) {
                    Toast.makeText(service, "请填写合法的黄框宽高", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (clickValue !in 0..1) {
                    Toast.makeText(service, "click 只能填写 0 或 1", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                draft.roiWidth = roiWidth
                draft.roiHeight = roiHeight
                draft.clickOnSuccess = clickValue == 1

                when (draft.action) {
                    RecorderAction.MATCH_TEMPLATE -> {
                        val templateBaseName = templateNameEdit?.text?.toString()?.trim().orEmpty()
                        val threshold = thresholdEdit?.text?.toString()?.toFloatOrNull()
                        if (templateBaseName.isBlank()) {
                            Toast.makeText(service, "请填写模板文件名", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (threshold == null || threshold <= 0f) {
                            Toast.makeText(service, "请填写合法阈值", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val result = activeAdjustView?.captureResult()
                        val cropped = cropTemplateBitmap(
                            screenshot = activeAdjustScreenshot,
                            screenRect = result?.redScreenRect,
                            normalizedWidth = draft.templateRect.width.roundToInt(),
                            normalizedHeight = draft.templateRect.height.roundToInt()
                        )
                        if (cropped == null) {
                            Toast.makeText(service, "模板截图失败，请重新录制这个节点", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        draft.templateName = stripPngSuffix(templateBaseName)
                        draft.threshold = threshold
                        draft.templateRect = result?.redRect ?: draft.templateRect
                        draft.templateBitmap?.recycle()
                        draft.templateBitmap = cropped
                    }
                    RecorderAction.OCR -> {
                        val text = ocrTextEdit?.text?.toString()?.trim().orEmpty()
                        val minHit = ocrMinHitEdit?.text?.toString()?.toIntOrNull()
                        if (text.isBlank()) {
                            Toast.makeText(service, "请填写要识别的文字", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (minHit == null || minHit <= 0) {
                            Toast.makeText(service, "请填写合法的最少 hit", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        draft.ocrText = text
                        draft.ocrMinHitCount = minHit
                    }
                    RecorderAction.SET_VAR -> Unit
                    RecorderAction.CLICK -> Unit
                    RecorderAction.BACK -> Unit
                }
                finishActiveAdjustAndAppend()
            }
        })
        body.addView(buttons)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun beginPickPoint() {
        if (pointPickerView != null || frameAdjustView != null) return
        val overlay = FrameLayout(service).apply {
            setBackgroundColor(Color.parseColor("#55000000"))
        }
        overlay.addView(
            TextView(service).apply {
                text = "点击屏幕记录节点位置"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(20), dp(14), dp(20), dp(14))
                setBackgroundColor(Color.parseColor("#AA000000"))
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        overlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    removePointPicker()
                    handlePickedPoint(event.rawX, event.rawY)
                    true
                }
                else -> true
            }
        }

        pointPickerView = overlay
        windowManager.addView(
            overlay,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )
    }

    private fun handlePickedPoint(rawX: Float, rawY: Float) {
        val basePoint = screenToBasePoint(rawX, rawY, PositionType.CENTER)
        showNodeEditor(
            NodeDraft(
                id = recordedNodes.size + 1,
                designX = basePoint.x,
                designY = basePoint.y,
                templateRect = BaseRect(basePoint.x, basePoint.y, DEFAULT_TEMPLATE_SIZE, DEFAULT_TEMPLATE_SIZE),
                pickedScreenX = rawX,
                pickedScreenY = rawY
            )
        )
    }

    private fun beginAddBranchNode() {
        if (pointPickerView != null || frameAdjustView != null) return
        showNodeEditor(
            NodeDraft(
                id = recordedNodes.size + 1,
                designX = 0f,
                designY = 0f,
                action = RecorderAction.SET_VAR,
                onSuccess = -1,
                onFail = -2
            )
        )
    }

    private fun showNodeEditor(draft: NodeDraft) {
        val themeContext = DialogUtils.getThemeContext(service)
        val root = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(themeContext).apply {
            text = "新增节点 #${draft.id}"
            setTextColor(Color.parseColor("#E5C07B"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, dp(10))
        })
        val scrollView = ScrollView(themeContext)
        val form = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(form)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val actions = RecorderAction.values().toList()
        val positionTypes = PositionType.values().toList()
        val clickPositionModes = ClickPositionMode.values().toList()
        val clickReferenceCandidates = buildClickReferenceCandidates(draft.id)
        val actionSpinner = Spinner(themeContext).apply {
            adapter = buildDarkSpinnerAdapter(themeContext, actions.map { it.label })
            styleDarkSpinner(this)
        }
        form.addView(buildDarkField(themeContext, "节点类型", actionSpinner))

        val delayEdit = buildDarkNumberEdit(themeContext, draft.delay.toString())
        val successEdit = buildDarkNumberEdit(themeContext, draft.onSuccess.toString())
        val failEdit = buildDarkNumberEdit(themeContext, draft.onFail.toString())
        val nodeNameEdit = buildDarkTextEdit(themeContext, draft.name)
        form.addView(buildDarkField(themeContext, "delay", delayEdit))
        form.addView(buildDarkField(themeContext, "on_success", successEdit))
        form.addView(buildDarkField(themeContext, "on_fail", failEdit))
        form.addView(buildDarkField(themeContext, "节点名称", nodeNameEdit))

        val setVarNameEdit = buildDarkTextEdit(themeContext, draft.branchVarName)
        val setVarValueEdit = buildDarkTextEdit(themeContext, draft.branchVarValue)
        val setVarSection = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(buildDarkField(themeContext, "var_name", setVarNameEdit))
            addView(buildDarkField(themeContext, "var_value", setVarValueEdit))
        }
        form.addView(setVarSection)

        val successBranchSection = buildDarkBranchConfigSection(
            context = themeContext,
            title = "成功分支",
            enabled = draft.successBranchEnabled,
            varName = draft.successBranchVar,
            routesText = draft.successBranchRouteText
        )
        form.addView(successBranchSection.container)

        val failBranchSection = buildDarkBranchConfigSection(
            context = themeContext,
            title = "失败分支",
            enabled = draft.failBranchEnabled,
            varName = draft.failBranchVar,
            routesText = draft.failBranchRouteText
        )
        form.addView(failBranchSection.container)

        val positionTypeSpinner = Spinner(themeContext).apply {
            adapter = buildDarkSpinnerAdapter(themeContext, positionTypes.map { it.label })
            styleDarkSpinner(this)
        }
        val positionTypeField = buildDarkField(themeContext, "位置类型", positionTypeSpinner)
        form.addView(positionTypeField)

        val clickPositionSpinner = Spinner(themeContext).apply {
            adapter = buildDarkSpinnerAdapter(themeContext, clickPositionModes.map { it.label })
            styleDarkSpinner(this)
        }
        val clickPositionField = buildDarkField(themeContext, "点击位置", clickPositionSpinner)
        form.addView(clickPositionField)

        val clickReferenceSpinner = Spinner(themeContext).apply {
            adapter = buildDarkSpinnerAdapter(
                themeContext,
                if (clickReferenceCandidates.isEmpty()) {
                    listOf("暂无可引用节点")
                } else {
                    clickReferenceCandidates.map { describeClickReferenceNode(it) }
                }
            )
            styleDarkSpinner(this)
            isEnabled = clickReferenceCandidates.isNotEmpty()
        }
        val clickReferenceField = buildDarkField(themeContext, "引用节点", clickReferenceSpinner)
        form.addView(clickReferenceField)
        val clickReferenceTip = TextView(themeContext).apply {
            text = "前面还没有可引用的识别节点"
            setTextColor(Color.parseColor("#8A7A58"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(dp(2), 0, 0, dp(6))
        }
        form.addView(clickReferenceTip)

        actionSpinner.setSelection(actions.indexOf(draft.action))
        positionTypeSpinner.setSelection(positionTypes.indexOf(draft.positionType))
        clickPositionSpinner.setSelection(clickPositionModes.indexOf(draft.clickPositionMode))
        val selectedRefIndex = clickReferenceCandidates.indexOfFirst { it.id == draft.clickRefTaskId }
        if (selectedRefIndex >= 0) {
            clickReferenceSpinner.setSelection(selectedRefIndex)
        }

        val updateActionConfigVisibility = {
            val selectedAction = actions[actionSpinner.selectedItemPosition]
            val isClick = selectedAction == RecorderAction.CLICK
            val isSetVar = selectedAction == RecorderAction.SET_VAR
            val useReference = isClick &&
                clickPositionModes[clickPositionSpinner.selectedItemPosition] ==
                ClickPositionMode.PREVIOUS_RECOGNIZED_TARGET
            positionTypeField.visibility = if (selectedAction == RecorderAction.BACK || isSetVar) View.GONE else View.VISIBLE
            clickPositionField.visibility = if (isClick) View.VISIBLE else View.GONE
            clickReferenceField.visibility =
                if (useReference && clickReferenceCandidates.isNotEmpty()) View.VISIBLE else View.GONE
            clickReferenceTip.visibility =
                if (useReference && clickReferenceCandidates.isEmpty()) View.VISIBLE else View.GONE
            setVarSection.visibility = if (isSetVar) View.VISIBLE else View.GONE
        }
        actionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateActionConfigVisibility()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        clickPositionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateActionConfigVisibility()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        updateActionConfigVisibility()

        val dialog = DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setView(root)
                .setPositiveButton("下一步", null)
                .setNegativeButton("取消", null)
        )
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
            window.setWindowAnimations(0)
        }
        styleDarkDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), true)
        styleDarkDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), false)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selectedAction = actions[actionSpinner.selectedItemPosition]
            val parsedDelay = delayEdit.text.toString().toLongOrNull()
            val parsedSuccess = successEdit.text.toString().toIntOrNull()
            val parsedFail = failEdit.text.toString().toIntOrNull()
            if (parsedDelay == null || parsedSuccess == null || parsedFail == null) {
                Toast.makeText(service, "请先填写合法的 delay / success / fail", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            draft.action = selectedAction
            draft.positionType = positionTypes[positionTypeSpinner.selectedItemPosition]
            draft.delay = parsedDelay
            draft.onSuccess = parsedSuccess
            draft.onFail = parsedFail
            draft.name = nodeNameEdit.text.toString().trim()
            draft.branchVarName = setVarNameEdit.text.toString().trim()
            draft.branchVarValue = setVarValueEdit.text.toString().trim()
            draft.successBranchEnabled = successBranchSection.enabledCheck.isChecked
            draft.successBranchVar = successBranchSection.varNameEdit.text.toString().trim()
            draft.successBranchRouteText = formatBranchRoutes(collectBranchRoutes(successBranchSection))
            draft.failBranchEnabled = failBranchSection.enabledCheck.isChecked
            draft.failBranchVar = failBranchSection.varNameEdit.text.toString().trim()
            draft.failBranchRouteText = formatBranchRoutes(collectBranchRoutes(failBranchSection))
            syncDraftPositionWithSelectedType(draft)

            when (selectedAction) {
                RecorderAction.CLICK -> {
                    val clickPositionMode = clickPositionModes[clickPositionSpinner.selectedItemPosition]
                    draft.clickPositionMode = clickPositionMode
                    draft.clickRefTaskId = if (clickPositionMode == ClickPositionMode.PREVIOUS_RECOGNIZED_TARGET) {
                        if (clickReferenceCandidates.isEmpty()) {
                            Toast.makeText(service, "前面还没有可引用的识别节点", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        clickReferenceCandidates
                            .getOrNull(clickReferenceSpinner.selectedItemPosition)
                            ?.id
                    } else {
                        null
                    }
                    dialog.dismiss()
                    appendNode(draft)
                }
                RecorderAction.BACK -> {
                    draft.clickRefTaskId = null
                    dialog.dismiss()
                    appendNode(draft)
                }
                RecorderAction.SET_VAR -> {
                    if (draft.branchVarName.isBlank()) {
                        Toast.makeText(service, "请填写 var_name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (draft.branchVarValue.isBlank()) {
                        Toast.makeText(service, "请填写 var_value", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val successRoutes = if (draft.successBranchEnabled) {
                        parseBranchRoutesText(draft.successBranchRouteText)
                    } else {
                        emptyMap()
                    }
                    if (draft.successBranchEnabled && draft.successBranchVar.isBlank()) {
                        Toast.makeText(service, "请填写成功分支变量名", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (draft.successBranchEnabled && successRoutes.isEmpty()) {
                        Toast.makeText(service, "请填写成功分支路由", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val failRoutes = if (draft.failBranchEnabled) {
                        parseBranchRoutesText(draft.failBranchRouteText)
                    } else {
                        emptyMap()
                    }
                    if (draft.failBranchEnabled && draft.failBranchVar.isBlank()) {
                        Toast.makeText(service, "请填写失败分支变量名", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (draft.failBranchEnabled && failRoutes.isEmpty()) {
                        Toast.makeText(service, "请填写失败分支路由", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    appendNode(draft)
                }
                RecorderAction.MATCH_TEMPLATE -> {
                    dialog.setOnDismissListener {
                        dialog.setOnDismissListener(null)
                        captureScreenshotForTemplateRecord { screenshot ->
                            showFrameAdjustOverlay(draft, screenshot)
                        }
                    }
                    dialog.dismiss()
                }
                RecorderAction.OCR -> {
                    dialog.dismiss()
                    showFrameAdjustOverlay(draft, null)
                }
            }
        }
    }

    private fun appendNode(draft: NodeDraft) {
        recordedNodes.add(draft)
        updateNodeCount()
        Toast.makeText(service, "节点 #${draft.id} 已添加", Toast.LENGTH_SHORT).show()
    }

    private fun promptSaveScript() {
        if (recordedNodes.isEmpty()) {
            Toast.makeText(service, "还没有录制任何节点", Toast.LENGTH_SHORT).show()
            return
        }
        val themeContext = DialogUtils.getThemeContext(service)
        val nameEdit = buildTextEdit(themeContext, "script_${System.currentTimeMillis()}")
        val dialog = DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(themeContext)
                .setTitle("保存脚本")
                .setView(labeledView(themeContext, "脚本名称", nameEdit))
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val bundle = UserDailyScriptStore.createBundle(service, nameEdit.text.toString())
            val plan = buildPlanForSave()
            recordedNodes.forEach { node ->
                if (node.action == RecorderAction.MATCH_TEMPLATE) {
                    val bitmap = node.templateBitmap
                    if (bitmap == null) {
                        Toast.makeText(service, "模板节点缺少模板图：${normalizedTemplateFileName(node.templateName)}", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    saveBitmap(File(bundle.templatesDir, normalizedTemplateFileName(node.templateName)), bitmap)
                }
            }
            UserDailyScriptStore.savePlan(bundle, plan, gson)
            dialog.dismiss()
            Toast.makeText(service, "脚本已保存：${bundle.scriptId}", Toast.LENGTH_LONG).show()
            stop()
            onScriptSaved(bundle.scriptId, plan, bundle.templatesDir)
        }
    }

    private fun buildPlanForSave(): DailyTaskPlan {
        val tasks = recordedNodes.mapIndexed { index, node ->
            val nextId = recordedNodes.getOrNull(index + 1)?.id ?: -1
            val normalizedSuccess = if (node.onSuccess == node.id + 1 && nextId == -1) -1 else node.onSuccess
            val successBranchVar = node.successBranchVar.takeIf { node.successBranchEnabled && it.isNotBlank() }
            val successBranchRoutes = parseBranchRoutesText(node.successBranchRouteText).takeIf {
                node.successBranchEnabled && it.isNotEmpty()
            }
            val failBranchVar = node.failBranchVar.takeIf { node.failBranchEnabled && it.isNotBlank() }
            val failBranchRoutes = parseBranchRoutesText(node.failBranchRouteText).takeIf {
                node.failBranchEnabled && it.isNotEmpty()
            }
            DailyTask(
                id = node.id,
                name = node.name.takeIf { it.isNotBlank() },
                action = node.action.value,
                delay = node.delay,
                params = when (node.action) {
                    RecorderAction.CLICK -> if (node.clickRefTaskId != null) {
                        TaskParams(
                            ref_task_id = node.clickRefTaskId,
                            branch_var = successBranchVar,
                            branch_routes = successBranchRoutes,
                            fail_branch_var = failBranchVar,
                            fail_branch_routes = failBranchRoutes
                        )
                    } else {
                        TaskParams(
                            x = node.designX,
                            y = node.designY,
                            align = node.positionType.value,
                            branch_var = successBranchVar,
                            branch_routes = successBranchRoutes,
                            fail_branch_var = failBranchVar,
                            fail_branch_routes = failBranchRoutes
                        )
                    }
                    RecorderAction.MATCH_TEMPLATE -> TaskParams(
                        template_name = normalizedTemplateFileName(node.templateName),
                        threshold = node.threshold,
                        roi = ROI(
                            x = node.designX,
                            y = node.designY,
                            w = node.roiWidth,
                            h = node.roiHeight,
                            align = node.positionType.value
                        ),
                        click = if (node.clickOnSuccess) 1 else 0,
                        branch_var = successBranchVar,
                        branch_routes = successBranchRoutes,
                        fail_branch_var = failBranchVar,
                        fail_branch_routes = failBranchRoutes
                    )
                    RecorderAction.OCR -> TaskParams(
                        roi = ROI(
                            x = node.designX,
                            y = node.designY,
                            w = node.roiWidth,
                            h = node.roiHeight,
                            align = node.positionType.value
                        ),
                        target_chars = node.ocrText
                            .filterNot { it.isWhitespace() }
                            .map { it.toString() },
                        min_hit_count = max(node.ocrMinHitCount, 1),
                        click = if (node.clickOnSuccess) 1 else 0,
                        branch_var = successBranchVar,
                        branch_routes = successBranchRoutes,
                        fail_branch_var = failBranchVar,
                        fail_branch_routes = failBranchRoutes
                    )
                    RecorderAction.SET_VAR -> TaskParams(
                        var_name = node.branchVarName.ifBlank { null },
                        var_value = node.branchVarValue.ifBlank { null },
                        branch_var = successBranchVar,
                        branch_routes = successBranchRoutes,
                        fail_branch_var = failBranchVar,
                        fail_branch_routes = failBranchRoutes
                    )
                    RecorderAction.BACK -> TaskParams(
                        branch_var = successBranchVar,
                        branch_routes = successBranchRoutes,
                        fail_branch_var = failBranchVar,
                        fail_branch_routes = failBranchRoutes
                    ).takeIf {
                        successBranchVar != null || successBranchRoutes != null ||
                            failBranchVar != null || failBranchRoutes != null
                    }
                },
                on_success = if (normalizedSuccess == node.id + 1) nextId else normalizedSuccess,
                on_fail = node.onFail
            )
        }
        return DailyTaskPlan(
            start_task_id = tasks.firstOrNull()?.id ?: 1,
            tasks = tasks
        )
    }

    private fun buildClickReferenceCandidates(currentNodeId: Int): List<NodeDraft> {
        return recordedNodes.filter { node ->
            node.id < currentNodeId &&
                (node.action == RecorderAction.MATCH_TEMPLATE || node.action == RecorderAction.OCR)
        }
    }

    private fun describeClickReferenceNode(node: NodeDraft): String {
        return when (node.action) {
            RecorderAction.MATCH_TEMPLATE -> "#${node.id} 模板匹配 ${normalizedTemplateFileName(node.templateName)}"
            RecorderAction.OCR -> "#${node.id} OCR ${node.ocrText.ifBlank { "(未填写文字)" }}"
            RecorderAction.SET_VAR -> "#${node.id} 分支 ${node.branchVarName.ifBlank { "(变量名空)" }}=${node.branchVarValue.ifBlank { "(空)" }}"
            RecorderAction.CLICK -> "#${node.id} 点击"
            RecorderAction.BACK -> "#${node.id} 返回"
        }
    }

    private fun showFrameAdjustOverlay(draft: NodeDraft, screenshot: Bitmap?) {
        removeFrameAdjustView()
        val overlay = FrameLayout(service)
        val adjustView = RecorderAdjustView(service, draft).apply {
            isClickable = true
        }
        overlay.addView(
            adjustView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        frameAdjustView = overlay
        windowManager.addView(
            overlay,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )
        bringControlViewToFront()
        activeAdjustDraft = draft
        activeAdjustScreenshot = screenshot
        activeAdjustView = adjustView
        renderControlBody()
    }

    private fun cropTemplateBitmap(
        screenshot: Bitmap?,
        screenRect: ScreenRect?,
        normalizedWidth: Int = DEFAULT_TEMPLATE_SIZE.toInt(),
        normalizedHeight: Int = DEFAULT_TEMPLATE_SIZE.toInt()
    ): Bitmap? {
        screenshot ?: return null
        screenRect ?: return null
        val (screenWidth, screenHeight) = getRealScreenSize()
        if (screenWidth <= 0f || screenHeight <= 0f) return null
        val ratioX = screenshot.width / screenWidth
        val ratioY = screenshot.height / screenHeight
        val left = (screenRect.left * ratioX).roundToInt().coerceIn(0, screenshot.width - 1)
        val top = (screenRect.top * ratioY).roundToInt().coerceIn(0, screenshot.height - 1)
        val right = (screenRect.right * ratioX).roundToInt().coerceIn(left + 1, screenshot.width)
        val bottom = (screenRect.bottom * ratioY).roundToInt().coerceIn(top + 1, screenshot.height)
        val cropped = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        val targetWidth = normalizedWidth.coerceAtLeast(1)
        val targetHeight = normalizedHeight.coerceAtLeast(1)
        if (cropped.width == targetWidth && cropped.height == targetHeight) {
            return cropped
        }
        return Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true).also {
            cropped.recycle()
        }
    }

    private fun alignTemplateRectToRoiCenter(draft: NodeDraft) {
        draft.templateRect.centerX = draft.designX
        draft.templateRect.centerY = draft.designY
    }

    private fun saveBitmap(file: File, bitmap: Bitmap) {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
    }

    private fun cancelActiveAdjust() {
        removeFrameAdjustView()
    }

    private fun finishActiveAdjustAndAppend() {
        val draft = activeAdjustDraft ?: return
        activeAdjustScreenshot?.recycle()
        activeAdjustScreenshot = null
        activeAdjustDraft = null
        activeAdjustView = null
        removeFrameAdjustOverlayOnly()
        renderControlBody()
        appendNode(draft)
    }

    private fun stripPngSuffix(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.lowercase().endsWith(".png")) trimmed.dropLast(4) else trimmed
    }

    private fun normalizedTemplateFileName(value: String): String {
        val baseName = stripPngSuffix(value)
        return if (baseName.isBlank()) ".png" else "$baseName.png"
    }

    private fun removePointPicker() {
        pointPickerView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                pointPickerView = null
            }
        }
    }

    private fun removeFrameAdjustView() {
        activeAdjustScreenshot?.recycle()
        activeAdjustScreenshot = null
        activeAdjustDraft = null
        activeAdjustView = null
        removeFrameAdjustOverlayOnly()
        renderControlBody()
    }

    private fun removeFrameAdjustOverlayOnly() {
        frameAdjustView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                frameAdjustView = null
            }
        }
    }

    private fun updateControlWindowFocus(editing: Boolean) {
        val view = controlView ?: return
        val params = controlLayoutParams ?: return
        val nextFlags = if (editing) {
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) and
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv() or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            (params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        }
        if (params.flags == nextFlags) return
        params.flags = nextFlags
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun bringControlViewToFront() {
        val view = controlView ?: return
        val params = controlLayoutParams ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
        }
        try {
            windowManager.addView(view, params)
        } catch (_: IllegalStateException) {
        }
    }

    private fun protectInputLongPress(editText: EditText) {
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.isClickable = true
        editText.isLongClickable = false
        editText.setOnLongClickListener { true }

        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }
        editText.customSelectionActionModeCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            editText.customInsertionActionModeCallback = callback
        }
    }

    private fun buildNumberEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value)
            applyYuanInputStyle()
            protectInputLongPress(this)
        }
    }

    private fun buildDecimalEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(value)
            applyYuanInputStyle()
            protectInputLongPress(this)
        }
    }

    private fun buildTextEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            setText(value)
            applyYuanInputStyle()
            protectInputLongPress(this)
        }
    }

    private fun buildEditorTextEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            setText(value)
            styleEditorInput(this)
        }
    }

    private fun buildEditorNumberEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value)
            styleEditorInput(this)
        }
    }

    private fun buildEditorDecimalEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(value)
            styleEditorInput(this)
        }
    }

    private fun styleEditorInput(editText: EditText) {
        editText.setTextColor(Color.parseColor("#F4E7C7"))
        editText.setHintTextColor(Color.parseColor("#8A7A58"))
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        editText.setPadding(dp(10), dp(8), dp(10), dp(8))
        editText.setBackgroundResource(R.drawable.bg_dark_hollow)
        protectInputLongPress(editText)
    }

    private fun buildEditorField(context: Context, label: String, child: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#E5C07B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), 0, dp(2), dp(4))
            })
            addView(child)
        }
    }

    private fun buildEditorButton(context: Context, text: String, accent: Boolean): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            minWidth = dp(56)
            minimumHeight = 0
            minHeight = 0
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(if (accent) Color.parseColor("#20170D") else Color.parseColor("#E5C07B"))
            setBackgroundResource(if (accent) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
        }
    }

    private fun buildDarkNumberEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(value)
            styleEditorInput(this)
        }
    }

    private fun buildDarkSpinnerAdapter(context: Context, items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.parseColor("#F4E7C7"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.parseColor("#20170D"))
                    setBackgroundColor(Color.parseColor("#E7D7AE"))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            }
        }
    }

    private fun styleDarkSpinner(spinner: Spinner) {
        spinner.setBackgroundResource(R.drawable.bg_dark_hollow)
        spinner.setPadding(dp(8), dp(2), dp(8), dp(2))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            spinner.setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#E7D7AE")))
        }
    }

    private fun buildDarkField(context: Context, label: String, child: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#E5C07B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(2), 0, dp(2), dp(4))
            })
            addView(child)
        }
    }

    private fun buildEditorCheckBox(context: Context, text: String, checked: Boolean): CheckBox {
        return CheckBox(context).apply {
            this.text = text
            isChecked = checked
            setTextColor(Color.parseColor("#F4E7C7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(2), 0, dp(2), dp(8))
        }
    }

    private fun buildTemplateNameInputRow(context: Context, editText: EditText): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                editText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(TextView(context).apply {
                text = ".png"
                setTextColor(Color.parseColor("#6B5A3A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(8), 0, dp(2), 0)
            })
        }
    }

    private fun styleDarkDialogButton(button: Button?, accent: Boolean) {
        button ?: return
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.setPadding(dp(12), dp(6), dp(12), dp(6))
        button.setTextColor(if (accent) Color.parseColor("#20170D") else Color.parseColor("#E5C07B"))
        button.setBackgroundResource(if (accent) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
    }

    private fun buildDarkTextEdit(context: Context, value: String): EditText {
        return EditText(context).apply {
            setText(value)
            styleEditorInput(this)
        }
    }

    private fun buildDarkBranchConfigSection(
        context: Context,
        title: String,
        enabled: Boolean,
        varName: String,
        routesText: String
    ): BranchConfigSection {
        val enabledCheck = buildEditorCheckBox(context, "启用$title", enabled)
        val varNameEdit = buildDarkTextEdit(context, varName).apply {
            hint = "branch_var"
        }
        val routesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val fieldsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(buildDarkField(context, "变量名", varNameEdit))
            addView(buildDarkField(context, "分支路由", routesContainer))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(enabledCheck)
            addView(fieldsContainer)
        }
        val routeRows = mutableListOf<BranchRouteRow>()
        fun addRouteRow(initialValue: String = "", initialTarget: String = "") {
            val row = buildDarkBranchRouteRow(context, initialValue, initialTarget) {
                routesContainer.removeView(it.container)
                routeRows.remove(it)
            }
            routeRows += row
            routesContainer.addView(row.container)
        }
        parseBranchRoutesText(routesText).forEach { (value, targetId) ->
            addRouteRow(value, targetId.toString())
        }
        if (routeRows.isEmpty()) {
            addRouteRow()
        }
        routesContainer.addView(buildEditorButton(context, "新增", false).apply {
            setOnClickListener { addRouteRow() }
        })
        fun syncVisibility() {
            fieldsContainer.visibility = if (enabledCheck.isChecked) View.VISIBLE else View.GONE
        }
        enabledCheck.setOnCheckedChangeListener { _, _ -> syncVisibility() }
        syncVisibility()
        return BranchConfigSection(container, enabledCheck, varNameEdit, routeRows)
    }

    private fun buildDarkBranchRouteRow(
        context: Context,
        initialValue: String,
        initialTarget: String,
        onRemove: (BranchRouteRow) -> Unit
    ): BranchRouteRow {
        lateinit var row: BranchRouteRow
        val valueEdit = buildDarkTextEdit(context, initialValue).apply {
            hint = "分支值"
        }
        val targetEdit = buildDarkNumberEdit(context, initialTarget).apply {
            hint = "后继节点"
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(valueEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "→"
                setTextColor(Color.parseColor("#F4E7C7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(8), 0, dp(8), 0)
            })
            addView(targetEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(buildEditorButton(context, "删", false).apply {
                setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener { onRemove(row) }
            })
        }
        row = BranchRouteRow(container, valueEdit, targetEdit)
        return row
    }

    private fun collectBranchRoutes(section: BranchConfigSection): Map<String, Int> {
        return section.routeRows.mapNotNull { row ->
            val value = row.valueEdit.text.toString().trim()
            val targetId = row.targetEdit.text.toString().trim().toIntOrNull()
            if (value.isBlank() || targetId == null) {
                null
            } else {
                value to targetId
            }
        }.toMap(linkedMapOf())
    }

    private fun formatBranchRoutes(routes: Map<String, Int>): String {
        return routes.entries.joinToString("\n") { (key, value) -> "$key=$value" }
    }

    private fun parseBranchRoutesText(text: String): Map<String, Int> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val delimiterIndex = line.indexOf('=').takeIf { it >= 0 } ?: line.indexOf("->").takeIf { it >= 0 }
                if (delimiterIndex == null) return@mapNotNull null
                val key = line.substring(0, delimiterIndex).trim()
                val valueText = if (line.substring(delimiterIndex).startsWith("->")) {
                    line.substring(delimiterIndex + 2).trim()
                } else {
                    line.substring(delimiterIndex + 1).trim()
                }
                val targetId = valueText.toIntOrNull() ?: return@mapNotNull null
                key.takeIf { it.isNotBlank() }?.let { it to targetId }
            }
            .toMap(linkedMapOf())
    }

    private fun labeledView(context: Context, label: String, child: View): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#333333"))
            })
            addView(child)
        }
    }

    private fun addFloatLiveWatcher(editText: EditText, onValidValue: (Float) -> Unit) {
        editText.addTextChangedListener(simpleTextWatcher { text ->
            text.toFloatOrNull()?.let(onValidValue)
        })
    }

    private fun addIntLiveWatcher(editText: EditText, onValidValue: (Int) -> Unit) {
        editText.addTextChangedListener(simpleTextWatcher { text ->
            text.toIntOrNull()?.let(onValidValue)
        })
    }

    private fun simpleTextWatcher(onChanged: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                onChanged(s?.toString().orEmpty())
            }
        }
    }

    private fun captureScreenshot(onResult: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null)
            return
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    var hardwareBitmap: Bitmap? = null
                    try {
                        hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        onResult(hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false))
                    } catch (_: Throwable) {
                        onResult(null)
                    } finally {
                        hardwareBitmap?.recycle()
                        result.hardwareBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null)
                }
            }
        )
    }

    private fun captureScreenshotForTemplateRecord(onResult: (Bitmap?) -> Unit) {
        val panel = controlView
        val previousVisibility = panel?.visibility ?: View.VISIBLE
        panel?.visibility = View.INVISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            captureScreenshot { bitmap ->
                panel?.visibility = previousVisibility
                onResult(bitmap)
            }
        }, 120L)
    }

    private fun syncDraftPositionWithSelectedType(draft: NodeDraft) {
        val screenX = draft.pickedScreenX ?: return
        val screenY = draft.pickedScreenY ?: return
        val basePoint = screenToBasePoint(screenX, screenY, draft.positionType)
        draft.designX = basePoint.x
        draft.designY = basePoint.y
        draft.templateRect = BaseRect(basePoint.x, basePoint.y, draft.templateRect.width, draft.templateRect.height)
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

    private fun screenToBasePoint(screenX: Float, screenY: Float, positionType: PositionType): PointF {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val scale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val offsetX = (screenWidth - BASE_W * scale) / 2f
        val offsetY = (screenHeight - BASE_H * scale) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        return PointF(
            ((screenX - offsetX) / scale).coerceIn(0f, BASE_W),
            when (positionType) {
                PositionType.CENTER -> ((screenY - offsetY) / scale)
                PositionType.TOP -> ((screenY - statusBarHeight) / scale)
                PositionType.BOTTOM -> (BASE_H - ((screenHeight - screenY) / scale))
            }.coerceIn(0f, BASE_H)
        )
    }

    private fun baseToScreenRect(rect: BaseRect, positionType: PositionType): ScreenRect {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val scale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val offsetX = (screenWidth - BASE_W * scale) / 2f
        val offsetY = (screenHeight - BASE_H * scale) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val halfWidth = rect.width * scale / 2f
        val halfHeight = rect.height * scale / 2f
        val centerX = offsetX + rect.centerX * scale
        val centerY = when (positionType) {
            PositionType.CENTER -> offsetY + rect.centerY * scale
            PositionType.TOP -> statusBarHeight + rect.centerY * scale
            PositionType.BOTTOM -> screenHeight - ((BASE_H - rect.centerY) * scale)
        }
        return ScreenRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    private fun getRawStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 40
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics
        ).roundToInt()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDialogDrag(dialog: AlertDialog, dragHandle: View) {
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val window = dialog.window ?: return false
                val params = window.attributes
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
                        window.attributes = params
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
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
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    private inner class RecorderAdjustView(
        context: Context,
        private val draft: NodeDraft
    ) : View(context) {

        private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            style = Paint.Style.FILL
        }
        private var redRectScreen = baseToScreenRect(draft.templateRect, draft.positionType)
        private var draggingRed = false
        private var dragOffsetX = 0f
        private var dragOffsetY = 0f
        private val redDragHitSlop = dp(18).toFloat()

        init {
            syncTemplateRectToRoiCenter()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#33000000"))
            val anchor = screenRectToLocal(baseToScreenRect(BaseRect(draft.designX, draft.designY, 12f, 12f), draft.positionType))
            canvas.drawCircle(anchor.centerX(), anchor.centerY(), 8f, pointPaint)
            canvas.drawRect(
                screenRectToLocal(
                    baseToScreenRect(
                        BaseRect(draft.designX, draft.designY, draft.roiWidth, draft.roiHeight),
                        draft.positionType
                    )
                ),
                yellowPaint
            )
            if (draft.action == RecorderAction.MATCH_TEMPLATE) {
                canvas.drawRect(screenRectToLocal(currentRedRectScreen()), redPaint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (draft.action != RecorderAction.MATCH_TEMPLATE) return true
            val redRectLocal = screenRectToLocal(currentRedRectScreen())
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val hitRect = RectF(redRectLocal).apply {
                        inset(-redDragHitSlop, -redDragHitSlop)
                    }
                    if (hitRect.contains(event.x, event.y)) {
                        draggingRed = true
                        dragOffsetX = event.x - redRectLocal.centerX()
                        dragOffsetY = event.y - redRectLocal.centerY()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!draggingRed) return true
                    val halfWidth = redRectLocal.width() / 2f
                    val halfHeight = redRectLocal.height() / 2f
                    val centerX = event.x - dragOffsetX
                    val centerY = event.y - dragOffsetY
                    val movedLocalRect = RectF(
                        centerX - halfWidth,
                        centerY - halfHeight,
                        centerX + halfWidth,
                        centerY + halfHeight
                    )
                    redRectScreen = localRectToScreen(movedLocalRect)
                    val movedBaseRect = screenRectToBase(redRectScreen)
                    if (draft.templateFollowsRoiCenter) {
                        draft.designX = movedBaseRect.centerX
                        draft.designY = movedBaseRect.centerY
                        alignTemplateRectToRoiCenter(draft)
                        redRectScreen = baseToScreenRect(draft.templateRect, draft.positionType)
                    } else {
                        draft.templateRect = movedBaseRect
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    draggingRed = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        fun syncTemplateRectToRoiCenter() {
            if (!draft.templateFollowsRoiCenter) return
            alignTemplateRectToRoiCenter(draft)
            redRectScreen = baseToScreenRect(draft.templateRect, draft.positionType)
            invalidate()
        }

        private fun currentRedRectScreen(): ScreenRect {
            if (draft.templateFollowsRoiCenter) {
                alignTemplateRectToRoiCenter(draft)
                redRectScreen = baseToScreenRect(draft.templateRect, draft.positionType)
            }
            return redRectScreen
        }

        fun captureResult(): AdjustResult {
            val redScreenRect = if (draft.action == RecorderAction.MATCH_TEMPLATE) {
                currentRedRectScreen()
            } else {
                null
            }
            return AdjustResult(
                redRect = if (draft.action == RecorderAction.MATCH_TEMPLATE && redScreenRect != null) {
                    screenRectToBase(redScreenRect)
                } else {
                    null
                },
                redScreenRect = redScreenRect
            )
        }

        private fun screenRectToBase(rect: ScreenRect?): BaseRect {
            rect ?: return draft.templateRect
            val baseLeftTop = screenToBasePoint(rect.left, rect.top, draft.positionType)
            val baseRightBottom = screenToBasePoint(rect.right, rect.bottom, draft.positionType)
            return BaseRect(
                centerX = (baseLeftTop.x + baseRightBottom.x) / 2f,
                centerY = (baseLeftTop.y + baseRightBottom.y) / 2f,
                width = abs(baseRightBottom.x - baseLeftTop.x),
                height = abs(baseRightBottom.y - baseLeftTop.y)
            )
        }

        private fun screenRectToLocal(rect: ScreenRect): RectF {
            val origin = getViewOriginOnScreen()
            return RectF(
                rect.left - origin.x,
                rect.top - origin.y,
                rect.right - origin.x,
                rect.bottom - origin.y
            )
        }

        private fun localRectToScreen(rect: RectF): ScreenRect {
            val origin = getViewOriginOnScreen()
            return ScreenRect(
                left = rect.left + origin.x,
                top = rect.top + origin.y,
                right = rect.right + origin.x,
                bottom = rect.bottom + origin.y
            )
        }

        private fun getViewOriginOnScreen(): PointF {
            val location = IntArray(2)
            getLocationOnScreen(location)
            return PointF(location[0].toFloat(), location[1].toFloat())
        }
    }

    private data class AdjustResult(
        val redRect: BaseRect?,
        val redScreenRect: ScreenRect?
    )

    private data class BranchConfigSection(
        val container: LinearLayout,
        val enabledCheck: CheckBox,
        val varNameEdit: EditText,
        val routeRows: MutableList<BranchRouteRow>
    )

    private data class BranchRouteRow(
        val container: LinearLayout,
        val valueEdit: EditText,
        val targetEdit: EditText
    )

    private class SimpleItemSelectedListener(
        private val onSelected: (Int) -> Unit
    ) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            onSelected(position)
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
