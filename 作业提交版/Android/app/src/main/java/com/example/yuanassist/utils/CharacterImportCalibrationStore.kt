package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.CharacterSwitchPoint

object CharacterImportCalibrationStore {
    private const val PREFS = "character_import_calibration"
    private const val RIGHT_X = "right_x_ratio"
    private const val RIGHT_Y = "right_y_ratio"

    private val DEFAULT_RIGHT = CharacterSwitchPoint(1035f / 1080f, 989f / 1920f)

    data class Calibration(
        val right: CharacterSwitchPoint = DEFAULT_RIGHT,
    )

    fun load(context: Context): Calibration {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Calibration(
            right = CharacterSwitchPoint(
                xRatio = prefs.getFloat(RIGHT_X, DEFAULT_RIGHT.xRatio),
                yRatio = prefs.getFloat(RIGHT_Y, DEFAULT_RIGHT.yRatio),
            ),
        )
    }

    fun save(context: Context, right: CharacterSwitchPoint) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(RIGHT_X, right.xRatio.coerceIn(0f, 1f))
            .putFloat(RIGHT_Y, right.yRatio.coerceIn(0f, 1f))
            .apply()
    }
}
