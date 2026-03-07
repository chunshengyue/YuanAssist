// 檔案路徑：yuanassist/core/CoordinateManager.kt
package com.example.yuanassist.core

import android.content.Context
import android.graphics.PointF
import kotlin.math.min

class CoordinateManager(private val context: Context) {
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

    init {
        calculate()
    }

    // 🔴 核心算法：自适应坐标计算
    fun calculate() {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

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

    // 获取指定列和高度类型的最终屏幕坐标 (Bottom-Up 算法)
    fun getActionCoordinates(colIndex: Int, designYFromBottom: Float): PointF {
        val x = gameOffsetX + (colIndex * colWidth) + (colWidth / 2f)
        val y = screenHeight - (designYFromBottom * gameScale)
        return PointF(x, y)
    }

    // 获取绝对位置的屏幕坐标 (Top-Down 算法)
    fun getTargetCoordinates(designX: Float, designYTop: Float): PointF {
        val x = gameOffsetX + (designX * gameScale)
        val y = gameOffsetY + (designYTop * gameScale)
        return PointF(x, y)
    }
}