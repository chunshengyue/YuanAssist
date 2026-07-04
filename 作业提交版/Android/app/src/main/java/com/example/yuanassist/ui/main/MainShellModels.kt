package com.example.yuanassist.ui.main

import android.graphics.Bitmap

enum class MainTab {
    HOME,
    JOB,
    DEBUG,
    MINE,
}

data class HomeOverlayState(
    val combatWindowOpen: Boolean = false,
    val hasUnreadAdminCloudScript: Boolean = false,
) {
    val combatButtonLabel: String
        get() = if (combatWindowOpen) "关闭悬浮窗" else "启动悬浮窗"
}

data class MineProfileState(
    val isLoggedIn: Boolean = false,
    val isFeedbackAdmin: Boolean = false,
    val nickname: String = "未登录",
    val detail: String = "点击资料卡进入资料中心",
    val avatarFallback: String = "我",
    val avatarUrl: String? = null,
    val unreadMessageCount: Int = 0,
)

data class DebugSelectionOption(
    val key: String,
    val label: String,
)

data class DebugReplacementDialogState(
    val sessionId: Long,
    val previewBitmap: Bitmap,
    val title: String,
    val hint: String,
    val gameScale: Float,
    val boxWidthPx: Int,
    val boxHeightPx: Int,
    val initialLeftPx: Int,
    val initialTopPx: Int,
)

data class DebugWorkbenchState(
    val taskOptions: List<DebugSelectionOption> = emptyList(),
    val selectedTaskKey: String = "",
    val selectedTaskLabel: String = "未加载",
    val templateOptions: List<DebugSelectionOption> = emptyList(),
    val selectedTemplateKey: String = "",
    val selectedTemplateLabel: String = "未加载",
    val screenshotBitmap: Bitmap? = null,
    val screenshotTitle: String = "未上传截图",
    val screenshotSubtitle: String = "点击上传截图后可继续在新调试页配置任务与素材。",
    val isLocalScopeEnabled: Boolean = false,
    val scopeHint: String = "识别范围说明将在这里显示。",
    val delayInput: String = "",
    val delaySupported: Boolean = false,
    val canClearDelay: Boolean = false,
    val delaySummary: String = "当前识别项没有可调整的延时节点。",
    val canReplaceTemplate: Boolean = false,
    val canRestoreTemplate: Boolean = false,
    val replacementDialog: DebugReplacementDialogState? = null,
    val logText: String = "调试日志会显示在这里。",
)
