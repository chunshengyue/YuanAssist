package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt

enum class CombatDetectionRoiKey(
    val id: String,
    val label: String,
    val defaultRoi: CombatDetectionRoi,
) {
    DRAGON_QI("dragon_qi", "龙气扫描区域", CombatDetectionRoi(540f, 1550f, 1080f, 500f)),
    PANG_TONG_COPY("pang_tong_copy", "复制扫描区域", CombatDetectionRoi(540f, 1510f, 1080f, 400f)),
}

data class CombatDetectionRoi(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

object CombatDetectionRoiStore {
    const val BASE_W = 1080f
    const val BASE_H = 1920f

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_PREFIX = "combat_detection_roi"

    fun keyForOption(option: String): CombatDetectionRoiKey? = when (option) {
        "DRAGON_QI_CHECK" -> CombatDetectionRoiKey.DRAGON_QI
        "PANG_TONG_COPY_CHECK" -> CombatDetectionRoiKey.PANG_TONG_COPY
        else -> null
    }

    fun resolveRoi(context: Context, key: CombatDetectionRoiKey): CombatDetectionRoi =
        getOverride(context, key) ?: key.defaultRoi

    fun hasOverride(context: Context, key: CombatDetectionRoiKey): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return listOf("x", "y", "w", "h").all { prefs.contains(prefKey(key, it)) }
    }

    fun getOverride(context: Context, key: CombatDetectionRoiKey): CombatDetectionRoi? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!hasOverride(context, key)) return null
        return coerceRoi(
            CombatDetectionRoi(
                prefs.getFloat(prefKey(key, "x"), key.defaultRoi.x),
                prefs.getFloat(prefKey(key, "y"), key.defaultRoi.y),
                prefs.getFloat(prefKey(key, "w"), key.defaultRoi.w),
                prefs.getFloat(prefKey(key, "h"), key.defaultRoi.h),
            ),
        )
    }

    fun saveOverride(context: Context, key: CombatDetectionRoiKey, roi: CombatDetectionRoi) {
        val normalized = coerceRoi(roi)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(prefKey(key, "x"), normalized.x)
            .putFloat(prefKey(key, "y"), normalized.y)
            .putFloat(prefKey(key, "w"), normalized.w)
            .putFloat(prefKey(key, "h"), normalized.h)
            .apply()
    }

    fun clearOverride(context: Context, key: CombatDetectionRoiKey) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey(key, "x"))
            .remove(prefKey(key, "y"))
            .remove(prefKey(key, "w"))
            .remove(prefKey(key, "h"))
            .apply()
    }

    fun toBitmapRect(bitmap: Bitmap, roi: CombatDetectionRoi, align: String = "bottom"): Rect {
        val scale = min(bitmap.width / BASE_W, bitmap.height / BASE_H)
        val offsetX = (bitmap.width - BASE_W * scale) / 2f
        val centerX = offsetX + roi.x * scale
        val centerY = when (align.lowercase()) {
            "bottom" -> bitmap.height - (BASE_H - roi.y) * scale
            else -> {
                val offsetY = (bitmap.height - BASE_H * scale) / 2f
                offsetY + roi.y * scale
            }
        }
        val width = (roi.w * scale).roundToInt().coerceAtLeast(1)
        val height = (roi.h * scale).roundToInt().coerceAtLeast(1)
        val left = (centerX - width / 2f).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (centerY - height / 2f).roundToInt().coerceIn(0, bitmap.height - 1)
        return Rect(
            left,
            top,
            (left + width).coerceIn(left + 1, bitmap.width),
            (top + height).coerceIn(top + 1, bitmap.height),
        )
    }

    fun fromBitmapRect(bitmap: Bitmap, rect: Rect, align: String = "bottom"): CombatDetectionRoi {
        val scale = min(bitmap.width / BASE_W, bitmap.height / BASE_H).takeIf { it > 0f } ?: 1f
        val offsetX = (bitmap.width - BASE_W * scale) / 2f
        val centerY = rect.exactCenterY()
        val baseY = when (align.lowercase()) {
            "bottom" -> BASE_H - (bitmap.height - centerY) / scale
            else -> {
                val offsetY = (bitmap.height - BASE_H * scale) / 2f
                (centerY - offsetY) / scale
            }
        }
        return coerceRoi(
            CombatDetectionRoi(
                x = (rect.exactCenterX() - offsetX) / scale,
                y = baseY,
                w = rect.width() / scale,
                h = rect.height() / scale,
            ),
        )
    }

    fun coerceRoi(roi: CombatDetectionRoi): CombatDetectionRoi {
        val width = roi.w.coerceIn(1f, BASE_W)
        val height = roi.h.coerceIn(1f, BASE_H)
        return roi.copy(
            x = roi.x.coerceIn(width / 2f, BASE_W - width / 2f),
            y = roi.y.coerceIn(height / 2f, BASE_H - height / 2f),
            w = width,
            h = height,
        )
    }

    private fun prefKey(key: CombatDetectionRoiKey, field: String): String =
        "$KEY_PREFIX::${key.id}::$field"
}
