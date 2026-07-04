package com.example.yuanassist.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.yuanassist.model.DailyPlanGraph
import com.example.yuanassist.model.GraphEdgeType
import kotlin.math.max

class DailyPlanGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onNodeClick: ((Int) -> Unit)? = null

    private var graph: DailyPlanGraph? = null
    private val nodeRects = linkedMapOf<Int, RectF>()
    private val density = resources.displayMetrics.density
    private val nodeWidth = dp(260)
    private val nodeHeight = dp(160)
    private val colGap = dp(34)
    private val rowGap = dp(120)
    private val viewPadding = dp(32)
    private val terminalLaneHeight = dp(96)
    private val terminalOverflowWidth = dp(220)
    private val cornerRadius = dp(16).toFloat()
    private val edgeExitInset = dp(52).toFloat()
    private val minScaleFactor = 0.6f
    private val maxScaleFactor = 2.2f
    private var scaleFactor = 1f
    private var isScaling = false
    private var activePointerCount = 0
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val nextScale = (scaleFactor * detector.scaleFactor).coerceIn(minScaleFactor, maxScaleFactor)
                if (nextScale != scaleFactor) {
                    scaleFactor = nextScale
                    requestLayout()
                    invalidate()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    )

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#9F8352")
        strokeWidth = dp(1).toFloat()
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7E5B8")
        textSize = sp(14)
        isFakeBoldText = true
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E9DDC1")
        textSize = sp(12)
    }
    private val chipTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2E7CC")
        textSize = sp(11)
    }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8A98D")
        textSize = sp(11)
    }
    private val edgeLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE4A3")
        textSize = sp(10)
        isFakeBoldText = true
    }
    private val terminalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#31251A")
    }
    private val terminalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7E5F32")
        strokeWidth = dp(1).toFloat()
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A3127")
    }
    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8B6A3E")
        strokeWidth = dp(1).toFloat()
    }

    fun setGraph(value: DailyPlanGraph) {
        graph = value
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val graphValue = graph
        if (graphValue == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = viewPadding * 2 +
            (graphValue.maxRow + 1) * nodeWidth +
            max(0, graphValue.maxRow) * colGap +
            terminalOverflowWidth
        val height = viewPadding * 2 +
            (graphValue.maxLayer + 1) * nodeHeight +
            max(0, graphValue.maxLayer) * rowGap +
            terminalLaneHeight
        setMeasuredDimension(
            resolveSize((width * scaleFactor).toInt(), widthMeasureSpec),
            resolveSize((height * scaleFactor).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val graphValue = graph ?: return
        rebuildNodeRects(graphValue)
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        drawEdges(canvas, graphValue)
        drawNodes(canvas, graphValue)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        activePointerCount = event.pointerCount
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isScaling || activePointerCount > 1) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                val scaledX = event.x / scaleFactor
                val scaledY = event.y / scaleFactor
                nodeRects.entries.firstOrNull { it.value.contains(scaledX, scaledY) }?.let {
                    onNodeClick?.invoke(it.key)
                    performClick()
                    return true
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun drawEdges(canvas: Canvas, graphValue: DailyPlanGraph) {
        val terminalCountByNode = mutableMapOf<Int, Int>()
        graphValue.edges.forEach { edge ->
            val sourceRect = nodeRects[edge.fromTaskId] ?: return@forEach
            val startX = edgeStartX(sourceRect, edge.type)
            val startY = sourceRect.bottom
            val edgeColor = when (edge.type) {
                GraphEdgeType.SUCCESS -> Color.parseColor("#6ECF7A")
                GraphEdgeType.FAIL -> Color.parseColor("#F58F7F")
                GraphEdgeType.BRANCH -> Color.parseColor("#7DC8FF")
            }
            edgePaint.color = edgeColor
            val path = Path()
            path.moveTo(startX, startY)

            val label = edge.label.orEmpty()
            val labelPosition = edgeLabelPosition(sourceRect, startX, startY, label, edge.type)
            if (edge.toTaskId != null) {
                val targetRect = nodeRects[edge.toTaskId] ?: return@forEach
                val endX = edgeStartX(targetRect, edge.type)
                val endY = targetRect.top
                val midY = (startY + endY) / 2f
                path.lineTo(startX, midY)
                path.lineTo(endX, midY)
                path.lineTo(endX, endY)
                drawVerticalArrowHead(canvas, endX, endY, edgeColor)
            } else {
                val terminalIndex = terminalCountByNode.getOrDefault(edge.fromTaskId, 0)
                terminalCountByNode[edge.fromTaskId] = terminalIndex + 1
                val endY = startY + dp(26)
                val endX = startX + (terminalIndex * dp(104))
                val badgeTop = endY + dp(8).toFloat()
                path.lineTo(startX, endY)
                path.lineTo(endX, endY)
                path.lineTo(endX, badgeTop)
                canvas.drawPath(path, edgePaint)
                val badgeRect = RectF(
                    endX - dp(44).toFloat(),
                    badgeTop,
                    endX + dp(44).toFloat(),
                    endY + dp(32).toFloat()
                )
                drawVerticalArrowHead(canvas, endX, badgeTop, edgeColor)
                canvas.drawRoundRect(badgeRect, dp(10).toFloat(), dp(10).toFloat(), terminalPaint)
                canvas.drawRoundRect(badgeRect, dp(10).toFloat(), dp(10).toFloat(), terminalStrokePaint)
                val terminalText = edge.terminalLabel.orEmpty()
                canvas.drawText(
                    terminalText,
                    badgeRect.left + dp(8),
                    badgeRect.centerY() + dp(4),
                    edgeLabelPaint
                )
                if (label.isNotBlank()) {
                    canvas.drawText(label, labelPosition.first, labelPosition.second, edgeLabelPaint)
                }
                return@forEach
            }

            canvas.drawPath(path, edgePaint)
            if (label.isNotBlank()) {
                canvas.drawText(label, labelPosition.first, labelPosition.second, edgeLabelPaint)
            }
        }
    }

    private fun edgeStartX(rect: RectF, type: GraphEdgeType): Float {
        return when (type) {
            GraphEdgeType.SUCCESS -> rect.left + edgeExitInset
            GraphEdgeType.FAIL -> rect.right - edgeExitInset
            GraphEdgeType.BRANCH -> rect.centerX()
        }
    }

    private fun edgeLabelPosition(
        sourceRect: RectF,
        startX: Float,
        startY: Float,
        label: String,
        type: GraphEdgeType
    ): Pair<Float, Float> {
        if (label.isBlank()) return 0f to 0f
        val textWidth = edgeLabelPaint.measureText(label)
        val y = startY + dp(14).toFloat()
        return when (type) {
            GraphEdgeType.SUCCESS -> {
                val x = (startX - textWidth - dp(6)).coerceAtLeast(sourceRect.left + dp(6).toFloat())
                x to y
            }
            GraphEdgeType.FAIL -> {
                val x = (startX + dp(6)).coerceAtMost(sourceRect.right - textWidth - dp(6).toFloat())
                x to y
            }
            GraphEdgeType.BRANCH -> {
                (startX + dp(6)) to y
            }
        }
    }

    private fun drawNodes(canvas: Canvas, graphValue: DailyPlanGraph) {
        graphValue.layoutNodes.forEach { layoutNode ->
            val rect = nodeRects[layoutNode.node.taskId] ?: return@forEach
            nodePaint.color = backgroundColorForAction(layoutNode.node.action)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, nodePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, nodeStrokePaint)

            val titleX = rect.left + dp(12)
            var textY = rect.top + dp(22)
            canvas.drawText(layoutNode.node.title, titleX, textY.toFloat(), titlePaint)
            drawInfoChips(canvas, rect, layoutNode.node.infoChips)
        }
    }

    private fun drawInfoChips(canvas: Canvas, rect: RectF, chips: List<String>) {
        if (chips.isEmpty()) return
        val chipTopStart = rect.top + dp(36)
        val chipLeftPadding = dp(12).toFloat()
        val chipHorizontalPadding = dp(8).toFloat()
        val chipVerticalPadding = dp(5).toFloat()
        val chipGapX = dp(8).toFloat()
        val chipGapY = dp(8).toFloat()
        val chipCorner = dp(10).toFloat()
        val maxRight = rect.right - dp(12)
        val maxBottom = rect.bottom - dp(10)

        var cursorX = rect.left + chipLeftPadding
        var cursorY = chipTopStart
        chips.forEach { rawChip ->
            val chipText = ellipsize(rawChip, chipTextPaint, rect.width() - dp(40))
            val textWidth = chipTextPaint.measureText(chipText)
            val chipWidth = textWidth + chipHorizontalPadding * 2
            val chipHeight = chipTextPaint.textSize + chipVerticalPadding * 2
            if (cursorX + chipWidth > maxRight) {
                cursorX = rect.left + chipLeftPadding
                cursorY += chipHeight + chipGapY
            }
            if (cursorY + chipHeight > maxBottom) return
            val chipRect = RectF(
                cursorX,
                cursorY,
                cursorX + chipWidth,
                cursorY + chipHeight
            )
            canvas.drawRoundRect(chipRect, chipCorner, chipCorner, chipPaint)
            canvas.drawRoundRect(chipRect, chipCorner, chipCorner, chipStrokePaint)
            canvas.drawText(
                chipText,
                chipRect.left + chipHorizontalPadding,
                chipRect.top + chipVerticalPadding + chipTextPaint.textSize,
                chipTextPaint
            )
            cursorX = chipRect.right + chipGapX
        }
    }

    private fun rebuildNodeRects(graphValue: DailyPlanGraph) {
        nodeRects.clear()
        graphValue.layoutNodes.forEach { layoutNode ->
            val left = viewPadding + layoutNode.row * (nodeWidth + colGap)
            val top = viewPadding + layoutNode.layer * (nodeHeight + rowGap)
            nodeRects[layoutNode.node.taskId] = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + nodeWidth).toFloat(),
                (top + nodeHeight).toFloat()
            )
        }
    }

    private fun drawVerticalArrowHead(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int
    ) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val size = dp(6).toFloat()
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x - size, y - size)
            lineTo(x + size, y - size)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String {
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
    }

    private fun backgroundColorForAction(action: String): Int {
        return when (action) {
            "CLICK" -> Color.parseColor("#2A2F38")
            "MATCH_TEMPLATE" -> Color.parseColor("#2E2A22")
            "OCR" -> Color.parseColor("#26322E")
            "SET_VAR" -> Color.parseColor("#2D2431")
            "BACK" -> Color.parseColor("#302726")
            else -> Color.parseColor("#252525")
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun sp(value: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value.toFloat(),
            resources.displayMetrics
        )
    }
}
