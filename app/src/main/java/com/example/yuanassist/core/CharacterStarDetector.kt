package com.example.yuanassist.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CharacterStarDetectionResult(
    val starCount: Int,
    val candidates: List<CharacterStarCandidate>,
)

data class CharacterStarCandidate(
    val center: PointF,
    val bounds: Rect,
    val pixelCount: Int,
    val colorRatio: Float,
    val shapeScore: Float,
)

object CharacterStarDetector {
    private const val MIN_COMPONENT_PIXELS = 16
    private const val MAX_COMPONENT_AREA_RATIO = 0.16f
    private const val MIN_COLOR_RATIO = 0.18f
    private const val MIN_SHAPE_SCORE = 0.42f

    fun detect(bitmap: Bitmap): CharacterStarDetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return CharacterStarDetectionResult(0, emptyList())
        }
        val mask = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                mask[y * width + x] = isGoldStarPixel(bitmap.getPixel(x, y))
            }
        }
        val visited = BooleanArray(mask.size)
        val candidates = mutableListOf<CharacterStarCandidate>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!mask[index] || visited[index]) continue
                val component = collectComponent(mask, visited, width, height, x, y)
                val candidate = buildCandidate(mask, width, height, component)
                if (candidate != null) {
                    candidates += candidate
                }
            }
        }
        val filtered = candidates
            .sortedByDescending { it.shapeScore * 0.65f + it.colorRatio * 0.35f }
            .fold(mutableListOf<CharacterStarCandidate>()) { accepted, candidate ->
                val overlaps = accepted.any { existing ->
                    rectsOverlap(existing.bounds, candidate.bounds)
                }
                if (!overlaps) accepted += candidate
                accepted
            }
            .sortedWith(compareBy<CharacterStarCandidate> { it.center.y }.thenBy { it.center.x })
        return CharacterStarDetectionResult(filtered.size, filtered)
    }

    private fun isGoldStarPixel(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return hue in 28f..58f &&
            saturation >= 0.28f &&
            value >= 0.70f &&
            red >= 190 &&
            green >= 140 &&
            blue <= 150
    }

    private fun collectComponent(
        mask: BooleanArray,
        visited: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
    ): List<Int> {
        val queue = ArrayDeque<Int>()
        val component = ArrayList<Int>()
        val startIndex = startY * width + startX
        visited[startIndex] = true
        queue.add(startIndex)
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            component += index
            val x = index % width
            val y = index / width
            for (ny in max(0, y - 1)..min(height - 1, y + 1)) {
                for (nx in max(0, x - 1)..min(width - 1, x + 1)) {
                    val next = ny * width + nx
                    if (visited[next] || !mask[next]) continue
                    visited[next] = true
                    queue.add(next)
                }
            }
        }
        return component
    }

    private fun buildCandidate(
        mask: BooleanArray,
        width: Int,
        height: Int,
        component: List<Int>,
    ): CharacterStarCandidate? {
        if (component.size < MIN_COMPONENT_PIXELS) return null
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        var sumX = 0f
        var sumY = 0f
        component.forEach { index ->
            val x = index % width
            val y = index / width
            left = min(left, x)
            top = min(top, y)
            right = max(right, x + 1)
            bottom = max(bottom, y + 1)
            sumX += x
            sumY += y
        }
        val bounds = Rect(left, top, right, bottom)
        val boundsArea = bounds.width() * bounds.height()
        if (bounds.width() <= 4 || bounds.height() <= 4) return null
        if (boundsArea > width * height * MAX_COMPONENT_AREA_RATIO) return null
        val aspect = bounds.width().toFloat() / bounds.height().toFloat()
        if (aspect !in 0.55f..1.70f) return null
        val colorRatio = component.size.toFloat() / boundsArea.toFloat()
        if (colorRatio < MIN_COLOR_RATIO) return null
        val centerX = sumX / component.size.toFloat()
        val centerY = sumY / component.size.toFloat()
        val shapeScore = computeFourPointShapeScore(mask, width, height, bounds, centerX, centerY)
        if (shapeScore < MIN_SHAPE_SCORE) return null
        return CharacterStarCandidate(
            center = PointF(centerX, centerY),
            bounds = bounds,
            pixelCount = component.size,
            colorRatio = colorRatio,
            shapeScore = shapeScore,
        )
    }

    private fun computeFourPointShapeScore(
        mask: BooleanArray,
        width: Int,
        height: Int,
        bounds: Rect,
        centerX: Float,
        centerY: Float,
    ): Float {
        val armThreshold = 0.18f
        val top = rayFillRatio(mask, width, height, centerX, centerY, 0f, -1f, bounds)
        val bottom = rayFillRatio(mask, width, height, centerX, centerY, 0f, 1f, bounds)
        val left = rayFillRatio(mask, width, height, centerX, centerY, -1f, 0f, bounds)
        val right = rayFillRatio(mask, width, height, centerX, centerY, 1f, 0f, bounds)
        val arms = listOf(top, bottom, left, right)
        val armPresence = arms.count { it >= armThreshold }.toFloat() / 4f
        val armBalance = arms.minOrNull().orZero() / arms.maxOrNull().orOne()
        val diagonalRatio = diagonalFillRatio(mask, width, height, bounds, centerX, centerY)
        val diagonalPenalty = (1f - diagonalRatio).coerceIn(0f, 1f)
        return (armPresence * 0.55f + armBalance * 0.25f + diagonalPenalty * 0.20f).coerceIn(0f, 1f)
    }

    private fun rayFillRatio(
        mask: BooleanArray,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        dx: Float,
        dy: Float,
        bounds: Rect,
    ): Float {
        var hits = 0
        var samples = 0
        val maxSteps = max(bounds.width(), bounds.height())
        for (step in 1..maxSteps) {
            val x = (centerX + dx * step).toInt()
            val y = (centerY + dy * step).toInt()
            if (x !in bounds.left until bounds.right || y !in bounds.top until bounds.bottom) break
            if (x !in 0 until width || y !in 0 until height) break
            samples += 1
            if (mask[y * width + x]) hits += 1
        }
        return if (samples == 0) 0f else hits.toFloat() / samples.toFloat()
    }

    private fun diagonalFillRatio(
        mask: BooleanArray,
        width: Int,
        height: Int,
        bounds: Rect,
        centerX: Float,
        centerY: Float,
    ): Float {
        var hits = 0
        var samples = 0
        val halfW = bounds.width() / 2f
        val halfH = bounds.height() / 2f
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                val nx = abs(x - centerX) / halfW.coerceAtLeast(1f)
                val ny = abs(y - centerY) / halfH.coerceAtLeast(1f)
                if (nx < 0.45f || ny < 0.45f) continue
                samples += 1
                if (x in 0 until width && y in 0 until height && mask[y * width + x]) hits += 1
            }
        }
        return if (samples == 0) 0f else hits.toFloat() / samples.toFloat()
    }

    private fun rectsOverlap(left: Rect, right: Rect): Boolean =
        left.left < right.right &&
            left.right > right.left &&
            left.top < right.bottom &&
            left.bottom > right.top

    private fun Float?.orZero(): Float = this ?: 0f

    private fun Float?.orOne(): Float = this?.takeIf { it > 0f } ?: 1f
}
