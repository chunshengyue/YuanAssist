package com.example.yuanassist.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.example.yuanassist.model.CharacterSwitchPoint

internal object CharacterImportCalibrationDragHelper {
    private const val PointRadius = 52f
    private const val LabelOffsetX = 4f
    private const val LabelOffsetY = -56f
    private val LabelSize = Size(110f, 70f)

    fun pointToOffset(point: CharacterSwitchPoint, size: IntSize): Offset =
        Offset(point.xRatio.coerceIn(0f, 1f) * size.width, point.yRatio.coerceIn(0f, 1f) * size.height)

    fun offsetToPoint(offset: Offset, size: IntSize): CharacterSwitchPoint {
        if (size.width <= 0 || size.height <= 0) {
            return CharacterSwitchPoint(0f, 0f)
        }
        return CharacterSwitchPoint(offset.x / size.width, offset.y / size.height)
    }

    fun isHandleTouched(position: Offset, center: Offset): Boolean {
        val labelLeft = center.x + LabelOffsetX
        val labelTop = center.y + LabelOffsetY
        val insidePoint = squaredDistance(position, center) <= PointRadius * PointRadius
        val insideLabel = position.x in labelLeft..(labelLeft + LabelSize.width) &&
            position.y in labelTop..(labelTop + LabelSize.height)
        return insidePoint || insideLabel
    }

    fun nextOffset(
        current: Offset,
        dragAmount: Offset,
        size: IntSize,
    ): Offset {
        return Offset(
            x = (current.x + dragAmount.x).coerceIn(0f, size.width.toFloat()),
            y = (current.y + dragAmount.y).coerceIn(0f, size.height.toFloat()),
        )
    }

    private fun squaredDistance(left: Offset, right: Offset): Float {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }
}
