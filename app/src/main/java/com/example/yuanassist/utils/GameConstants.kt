package com.example.yuanassist.utils

object GameConstants {
    // 屏幕基准分辨率
    const val BASE_WIDTH = 1440f
    const val BASE_HEIGHT = 2560f

    // 动作基准坐标 (Bottom-Up 底部向上推算)
    const val DESIGN_Y_UP = 695f
    const val DESIGN_Y_A = 600f
    const val DESIGN_Y_DOWN = 514f
    const val DESIGN_Y_CIRCLE = 317.3f

    // 目标切换坐标 (Top-Down 顶部向下推算)
    const val DESIGN_TARGET_X = 1368f
    const val DESIGN_TARGET_Y_TOP = 845f

    // 随机偏移参数 (控制防封号的点击范围)
    const val RND_RADIUS_CLICK = 20f
    const val RND_OFFSET_SWIPE_X = 15f
    const val RND_OFFSET_SWIPE_Y = 20f

    // 默认滑动距离
    const val SWIPE_DISTANCE = 300f
}