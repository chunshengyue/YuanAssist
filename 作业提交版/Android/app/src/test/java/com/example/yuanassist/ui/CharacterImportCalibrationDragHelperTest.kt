package com.example.yuanassist.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.example.yuanassist.model.CharacterSwitchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterImportCalibrationDragHelperTest {

    @Test
    fun next_offset_accumulates_across_multiple_drag_steps() {
        val size = IntSize(100, 200)
        val start = CharacterImportCalibrationDragHelper.pointToOffset(
            CharacterSwitchPoint(0.1f, 0.2f),
            size,
        )

        val afterFirst = CharacterImportCalibrationDragHelper.nextOffset(start, Offset(10f, 15f), size)
        val afterSecond = CharacterImportCalibrationDragHelper.nextOffset(afterFirst, Offset(10f, 15f), size)
        val result = CharacterImportCalibrationDragHelper.offsetToPoint(afterSecond, size)

        assertEquals(0.3f, result.xRatio, 0.0001f)
        assertEquals(0.35f, result.yRatio, 0.0001f)
    }

    @Test
    fun is_handle_touched_accepts_label_hitbox() {
        val touched = CharacterImportCalibrationDragHelper.isHandleTouched(
            position = Offset(244f, 54f),
            center = Offset(220f, 100f),
        )

        assertTrue(touched)
    }
}
