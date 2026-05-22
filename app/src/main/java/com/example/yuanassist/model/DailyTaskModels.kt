package com.example.yuanassist.model

data class DailyTaskPlan(
    val start_task_id: Int,
    val tasks: List<DailyTask>,
    val asset_template_dir: String? = null,
    val display_name: String? = null
)

data class DailyTask(
    val id: Int,
    val name: String? = null,
    val action: String, // CLICK, MATCH_TEMPLATE, OCR, SCREENSHOT_GROUP, CLICK_LAST_MATCH...
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
    val repeat_count: Int? = null,
    val repeat_count_var: String? = null,
    val repeat_interval: Long? = null,
    val repeat_interval_var: String? = null,
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
    val target_chars: List<String>? = null,
    val min_hit_count: Int? = null,
    val preprocess: String? = null,
    val var_name: String? = null,
    val var_value: String? = null,
    val branch_var: String? = null,
    val branch_routes: Map<String, Int>? = null,
    val fail_branch_var: String? = null,
    val fail_branch_routes: Map<String, Int>? = null,
    val terminal_note: String? = null,
    val screenshot_steps: List<ScreenshotStep>? = null,

    // 脚本调度参数
    val script_name: String? = null,
    val script_name_var: String? = null,
    val script_branch_var: String? = null,
    val script_name_routes: Map<String, String>? = null,
    val entry_task_id: Int? = null,
    val entry_task_id_var: String? = null,
    val exit_task_id: Int? = null,
    val exit_task_id_var: String? = null,
    val inherit_variables: Boolean? = null,
    val script_variables: Map<String, String>? = null
)

data class ScreenshotStep(
    val name: String? = null,
    val type: String,
    val target_chars: List<String>? = null,
    val target_text: String? = null,
    val template_name: String? = null,
    val threshold: Float? = null,
    val roi: ROI? = null,
    val click: Int? = null,
    val min_hit_count: Int? = null,
    val preprocess: String? = null,
    val button_name: String? = null,
    val on_success: Int = -1,
    val terminal_note: String? = null
)

data class ROI(
    val x: Float? = null, val y: Float? = null, val w: Float? = null, val h: Float? = null,
    val centerX: Float? = null, val centerY: Float? = null, val radius: Float? = null,
    val align: String = "center"
)
