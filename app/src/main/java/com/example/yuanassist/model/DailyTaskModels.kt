package com.example.yuanassist.model

data class DailyTaskPlan(
    val start_task_id: Int,
    val tasks: List<DailyTask>
)

data class DailyTask(
    val id: Int,
    val action: String, // CLICK, MATCH_TEMPLATE, OCR, CLICK_LAST_MATCH...
    val delay: Long = 0,
    val params: TaskParams?, // 有些指令可能没有 params
    val on_success: Int = -1,
    val on_fail: Int = -1,
    val start_cooldown_on_success: Boolean = false
)

data class TaskParams(
    // 坐标与时间参数
    val x: Float? = null,
    val y: Float? = null,
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val duration: Long? = null,

    // 🔴 核心锚点：决定长屏幕的适配方式
    val align: String = "center", // "top", "bottom", "center"

    // 视觉相关参数 (暂时留着反序列化用)
    val template_name: String? = null,
    val target_text: String? = null,
    val threshold: Float = 0.8f,
    val roi: ROI? = null,
    val button_name: String? = null,
    val ref_task_id: Int? = null,
    val click: Int? = null,
    val var_name: String? = null,
    val var_value: String? = null,
    val branch_var: String? = null,
    val branch_routes: Map<String, Int>? = null,
    val terminal_note: String? = null
)

data class ROI(
    val x: Float? = null, val y: Float? = null, val w: Float? = null, val h: Float? = null,
    val centerX: Float? = null, val centerY: Float? = null, val radius: Float? = null,
    val align: String = "center"
)
