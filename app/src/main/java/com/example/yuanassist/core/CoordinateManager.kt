// 檔案路徑：yuanassist/core/CoordinateManager.kt
package com.example.yuanassist.core

import android.content.Context
import android.graphics.PointF
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import kotlin.math.min

class CoordinateManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    var screenWidth = 0
        private set
    var screenHeight = 0
        private set
    var gameScale = 1f
        private set
    var gameOffsetX = 0f
        private set
    var gameOffsetY = 0f
        private set
    var colWidth = 0f
        private set
    private var displayRotation = 0

    init {
        calculate()
    }

    // 🔴 核心算法：自适应坐标计算
    fun calculate() {
        val (width, height) = getRealScreenSize()
        screenWidth = width
        screenHeight = height
        @Suppress("DEPRECATION")
        displayRotation = windowManager.defaultDisplay.rotation

        // 1. 计算缩放因子 (基于 1440x2560 设计图)
        val widthRatio = screenWidth / 1440f
        val heightRatio = screenHeight / 2560f
        gameScale = min(widthRatio, heightRatio)

        // 2. 计算游戏画面实际大小
        val gameWidth = 1440f * gameScale
        val gameHeight = 2560f * gameScale

        // 3. 计算黑边偏移 (居中显示)
        gameOffsetX = (screenWidth - gameWidth) / 2f
        gameOffsetY = (screenHeight - gameHeight) / 2f

        // 4. 计算列宽
        colWidth = gameWidth / 5f
    }

    fun refreshIfNeeded() {
        val (width, height) = getRealScreenSize()
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        if (width != screenWidth || height != screenHeight || rotation != displayRotation) {
            calculate()
        }
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.x to point.y
        }
    }

    // 获取指定列和高度类型的最终屏幕坐标 (Bottom-Up 算法)
    fun getActionCoordinates(colIndex: Int, designYFromBottom: Float, designXOffset: Float = 0f): PointF {
        refreshIfNeeded()
        val x = gameOffsetX + (colIndex * colWidth) + (colWidth / 2f) + (designXOffset * gameScale)
        val y = screenHeight - (designYFromBottom * gameScale)
        return PointF(x, y)
    }

    // 获取绝对位置的屏幕坐标 (Top-Down 算法)
    fun getTargetCoordinates(designX: Float, designYTop: Float): PointF {
        refreshIfNeeded()
        val x = gameOffsetX + (designX * gameScale)
        val y = gameOffsetY + (designYTop * gameScale)
        return PointF(x, y)
    }
}
