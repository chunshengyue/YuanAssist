package com.example.yuanassist.ui.main

data class HomeTabActions(
    val onToggleCombat: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenBirdFood: () -> Unit,
    val onOpenMainline624: () -> Unit,
    val onOpenStargazing: () -> Unit,
    val onOpenAilao15Min: () -> Unit,
    val onOpenPiJingZhanJi: () -> Unit,
    val onOpenInventoryStitch: () -> Unit,
    val onOpenBoxOcr: () -> Unit,
    val onOpenCoordinatePicker: () -> Unit,
    val onOpenScriptRecorder: () -> Unit,
    val onOpenRunLog: () -> Unit,
    val onOpenDebugTab: () -> Unit,
    val onOpenFaq: () -> Unit,
    val onOpenFeedback: () -> Unit,
    val onOpenScriptLibrary: () -> Unit,
    val onOpenCloudDailyScript: () -> Unit,
    val onCheckUpdate: () -> Unit,
)

data class JobTabActions(
    val onOpenCommunity: () -> Unit,
    val onOpenMaaYuan: () -> Unit,
    val onOpenPublishStrategy: () -> Unit,
    val onOpenCharacterImport: () -> Unit,
)

data class DebugTabActions(
    val onPickImage: () -> Unit,
    val onSelectTask: (String) -> Unit,
    val onSelectTemplate: (String) -> Unit,
    val onSelectScope: (Boolean) -> Unit,
    val onRunTest: () -> Unit,
    val onReplaceTemplate: () -> Unit,
    val onDismissReplacementDialog: () -> Unit,
    val onConfirmReplacement: (Int, Int) -> Unit,
    val onRestoreTemplate: () -> Unit,
    val onDelayInputChange: (String) -> Unit,
    val onSaveDelay: () -> Unit,
    val onClearDelay: () -> Unit,
    val onCopyLog: () -> Unit,
)

data class MineTabActions(
    val onPrimaryAction: () -> Unit,
    val onEditNickname: () -> Unit,
    val onOpenProfileCenter: () -> Unit,
    val onSyncProfile: (() -> Unit)?,
    val onOpenPublished: () -> Unit,
    val onOpenStone: () -> Unit,
    val onOpenFavorite: () -> Unit,
    val onOpenMessage: () -> Unit,
    val onOpenOfficialSite: () -> Unit,
    val onOpenFeedbackAdmin: () -> Unit,
    val onOpenExcludedAgents: () -> Unit,
)
