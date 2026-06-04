package com.example.yuanassist.ui.main

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import com.example.yuanassist.core.CharacterStarDetectionResult
import com.example.yuanassist.core.CharacterStarDetector
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.model.BattleStageNavigationRegistry
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.utils.BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION
import com.example.yuanassist.utils.BATTLE_FLOW_TEST_TASK_KEY
import com.example.yuanassist.utils.BIRD_FOOD_NAV_TEST_TASK_KEY
import com.example.yuanassist.utils.DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION
import com.example.yuanassist.utils.MAINLINE_624_START_BATTLE_DELAY_OPTION
import com.example.yuanassist.utils.StartBattleShared
import com.example.yuanassist.utils.StonePaddleLocalRecognizer
import com.example.yuanassist.utils.TemplateDelayOverrideStore
import com.example.yuanassist.utils.TemplateOverrideStore
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.tableocr.PaddleTextResult
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class DebugWorkbenchCoordinator(
    private val activity: Activity,
    private val onStateChanged: (DebugWorkbenchState) -> Unit,
) {
    private val ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val START_BATTLE_OCR_OPTION = "START_BATTLE_OCR"
        private const val START_BATTLE_OCR_LABEL = "开始战斗（OCR）"
        private const val DAI_BAN_GONG_WU_SCRIPT = "dai_ban_gong_wu.json"
        private const val DAI_BAN_GONG_WU_SWEEP_TEMPLATE = "saodang.png"
        private const val DAI_BAN_GONG_WU_SWEEP_LABEL = "待办公务扫荡按钮"
        private const val BATTLE_TURN_OCR_OPTION = "BATTLE_TURN_OCR"
        private const val BATTLE_TURN_OCR_LABEL = "战斗回合 OCR（右上角）"
        private const val STONE_GRID_DEBUG_OPTION = "STONE_GRID_DEBUG"
        private const val STONE_GRID_DEBUG_LABEL = "星石区域划分（Paddle划分 + Paddle识别）"
        private const val CHARACTER_NAME_OPTION = "CHARACTER_NAME_OCR"
        private const val CHARACTER_PROFICIENCY_OPTION = "CHARACTER_PROFICIENCY_OCR"
        private const val CHARACTER_PROFICIENCY_LABEL = "角色练度"
        private const val CHARACTER_FATE_OPTION = "CHARACTER_FATE_OCR"
        private const val CHARACTER_FATE_LABEL = "角色命盘"
        private const val ORANGE_STAR_OPTION = "ORANGE_STAR_CHECK"
        private const val ORANGE_STAR_LABEL = "橙星检测（左上角）"
        private const val PURPLE_STAR_OPTION = "PURPLE_STAR_CHECK"
        private const val PURPLE_STAR_LABEL = "紫星检测（左上角）"
        private const val CAVE_DONGKU_OPTION = "CAVE_DONGKU_FALLBACK"
        private const val CAVE_DONGKU_LABEL = "洞窟入口（dongku / dongku2）"
        private const val CAVE_NEXT_FLOOR_TEMPLATE = "xiayiceng.png"
        private const val CAVE_NEXT_FLOOR_LABEL = "洞窟下一层（xiayiceng）"
        private const val DEATH_CHECK_OPTION_PREFIX = "DEATH_CHECK_SLOT_"
        private const val YOUZHOU_TEMPLATE = "youzhou.png"
        private const val JINRU_TEMPLATE = "jinru.png"
        private const val ALL_WIPE_TEMPLATE = "zaicitiaozhan.png"
        private const val ALL_WIPE_TEMPLATE_THRESHOLD = 0.80f
        private const val ALL_WIPE_TEMPLATE_CENTER_X = 785f
        private const val ALL_WIPE_TEMPLATE_CENTER_Y = 1699f
        private const val ALL_WIPE_TEMPLATE_ROI_SIZE = 300f
        private const val ORANGE_STAR_RECOVERY_TEMPLATE = "queding2.png"
        private const val ORANGE_STAR_RECOVERY_THRESHOLD = 0.80f
        private const val ORANGE_STAR_RECOVERY_CENTER_X = 759f
        private const val ORANGE_STAR_RECOVERY_CENTER_Y = 1158f
        private const val ORANGE_STAR_RECOVERY_ROI_SIZE = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_TEMPLATE = "lantai.png"
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_X = 499f
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_Y = 567f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE = "yuanbao.png"
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_X = 441f
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y = 920f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD = 0.85f
        private const val BIRD_FOOD_SCREEN_TEMPLATE = "tfqk.png"
        private const val BIRD_FOOD_SCREEN_CENTER_X = 170f
        private const val BIRD_FOOD_SCREEN_CENTER_Y = 1054f
        private const val BIRD_FOOD_SCREEN_ROI_WIDTH = 200f
        private const val BIRD_FOOD_SCREEN_ROI_HEIGHT = 300f
        private const val BIRD_FOOD_SCREEN_THRESHOLD = 0.85f
        private const val STAGE_HOME_RECOVERY_BAIHU_TEMPLATE = "baihu.png"
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_X = 84f
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_Y = 1505f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_BAIHU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_DIGONG_TEMPLATE = "digong.png"
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_X = 573f
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_Y = 811f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE = "yijirukou.png"
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X = 931f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y = 1688f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_XINZHI_TEMPLATE = "xinzhi.png"
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_X = 999f
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_Y = 1544f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE = "lixian.png"
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_X = 922f
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y = 1039f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD = 0.80f
        private const val CAVE_DONGKU_TEMPLATE = "dongku.png"
        private const val CAVE_DONGKU_TEMPLATE_FALLBACK = "dongku2.png"
        private const val CAVE_DONGKU_CENTER_X = 666f
        private const val CAVE_DONGKU_CENTER_Y = 313f
        private const val CAVE_DONGKU_ROI_WIDTH = 300f
        private const val CAVE_DONGKU_ROI_HEIGHT = 300f
        private const val CAVE_DONGKU_THRESHOLD = 0.75f
        private const val AUTO_SELECT_FILTER_TEMPLATE = "btn_filter.png"
        private const val AUTO_SELECT_FILTER_CENTER_X = 972f
        private const val AUTO_SELECT_FILTER_CENTER_Y = 1254f
        private const val AUTO_SELECT_FILTER_ROI_WIDTH = 216f
        private const val AUTO_SELECT_FILTER_ROI_HEIGHT = 300f
        private const val AUTO_SELECT_FILTER_THRESHOLD = 0.80f
        private const val HIDDEN_TEMPLATE_OPTIONS = "xingshishili.jpg"
        private const val MAINLINE_624_BRANCH_THRESHOLD = 0.75f
        private const val BIRD_FOOD_RUNTIME_TEMPLATE_DELAY_MS = 500L
        private const val BIRD_FOOD_YUANBAO_TASK_ID = 9001
        private const val BIRD_FOOD_SCREEN_TASK_ID = 9002
        private const val DEATH_CHECK_SLOT_COUNT = 5
        private const val DEATH_CHECK_TOP_Y = 1350f
        private const val DEATH_CHECK_BOTTOM_Y = 1700f
        private const val DEATH_CHECK_SATURATION_THRESHOLD = 20f
        private const val ORANGE_STAR_CENTER_X = 163f
        private const val ORANGE_STAR_CENTER_Y = 302f
        private const val ORANGE_STAR_ROI_SIZE = 200f
        private const val ORANGE_STAR_THRESHOLD = 0.66f
        private const val PURPLE_STAR_THRESHOLD = 0.66f
        private const val ORANGE_STAR_SHAPE_THRESHOLD = 0.34f
        private const val ORANGE_STAR_MIN_GLOW_RATIO = 0.07f
        private const val TASK_BATTLE_FLOW = BATTLE_FLOW_TEST_TASK_KEY
        private const val TASK_BIRD_FOOD_NAV = BIRD_FOOD_NAV_TEST_TASK_KEY
        private const val START_BATTLE_RED_THRESHOLD = StartBattleShared.RED_THRESHOLD
        private const val START_BATTLE_RED_CENTER_X = StartBattleShared.CENTER_X
        private const val START_BATTLE_RED_CENTER_Y = StartBattleShared.CENTER_Y
        private const val START_BATTLE_RED_ROI_WIDTH = StartBattleShared.ROI_WIDTH
        private const val START_BATTLE_RED_ROI_HEIGHT = StartBattleShared.ROI_HEIGHT
        private const val BATTLE_TURN_OCR_W = 400f
        private const val BATTLE_TURN_OCR_H = 300f
        private const val CHARACTER_NAME_CENTER_X = 662f
        private const val CHARACTER_NAME_CENTER_Y = 197f
        private const val CHARACTER_NAME_ROI_WIDTH = 300f
        private const val CHARACTER_NAME_ROI_HEIGHT = 120f
        private const val CHARACTER_VALUE_CENTER_X = 340f
        private const val CHARACTER_VALUE_CENTER_Y = 1289f
        private const val CHARACTER_VALUE_ROI_WIDTH = 360f
        private const val CHARACTER_VALUE_ROI_HEIGHT = 180f
        private const val CHARACTER_STAR_CENTER_X = 625f
        private const val CHARACTER_STAR_CENTER_Y = 1663f
        private const val CHARACTER_STAR_ROI_SIZE = 260f
        private const val CHARACTER_FATE_ROI_SIZE = 220f
        private val CHARACTER_FATE_POINTS = listOf(
            174f to 426f,
            935f to 694f,
            415f to 1219f,
        )
        private val CHARACTER_FATE_SPECIAL_POINTS = mapOf(
            "孙尚香" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
            "华佗" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
            "张辽" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
            "张修" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
            "周瑜" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
            "陆逊" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
            "鲁肃" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
            "干吉" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
            "荀彧" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
            "庞统" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
            "颜良" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
            "史子眇" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
            "张角" to listOf(938f to 444f, 156f to 702f, 666f to 1225f),
            "王粲" to listOf(680f to 437f, 147f to 959f, 932f to 1214f),
        )
        private const val CAVE_NEXT_FLOOR_CENTER_X = 700f
        private const val CAVE_NEXT_FLOOR_CENTER_Y = 1700f
        private const val CAVE_NEXT_FLOOR_ROI_WIDTH = 500f
        private const val CAVE_NEXT_FLOOR_ROI_HEIGHT = 500f
        private const val CAVE_NEXT_FLOOR_THRESHOLD = 0.80f
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val REPLACEMENT_TEMPLATE_BASE_SIZE = 60f
        private const val DEFAULT_TEMPLATE_TEST_THRESHOLD = 0.90f

        private const val DAILY_SCRIPT_DIR = "daily_scripts"
        private const val LEVELS_SCRIPT_FILE = "levels.json"

        private val DEFAULT_DAILY_TEST_SCRIPT_FILES = listOf(
            "tu_fa_qing_kuang.json",
            "xiao_dao_xiao_xi.json",
            "ta_de_chuan_wen.json",
            DAI_BAN_GONG_WU_SCRIPT,
            "zhu_xian_6_24.json",
        )

        private val TASK_ORDER = listOf(
            TASK_BATTLE_FLOW,
            TASK_BIRD_FOOD_NAV,
        )

        private val TASK_DISPLAY_NAME_MAP = mapOf(
            TASK_BATTLE_FLOW to "战斗流程",
            TASK_BIRD_FOOD_NAV to "鸢报界面导航",
            "tu_fa_qing_kuang.json" to "突发情况",
            "xiao_dao_xiao_xi.json" to "小道消息",
            "ta_de_chuan_wen.json" to "他的传闻",
            DAI_BAN_GONG_WU_SCRIPT to "待办公务",
            "zhu_xian_6_24.json" to "主线624",
        )

    }

    private data class TestTaskEntry(
        val key: String,
        val displayName: String,
        val bundle: UserDailyScriptBundle? = null,
    ) {
        val isUserScript: Boolean
            get() = bundle != null
    }

    private data class ReplacementTarget(
        val fileName: String,
        val label: String,
        val node: DailyScriptDebugNode? = null,
    )

    private data class ReplacementSession(
        val id: Long,
        val target: ReplacementTarget,
        val previewBitmap: Bitmap,
        val sourceRect: Rect,
        val gameScale: Float,
        val boxWidthPx: Int,
        val boxHeightPx: Int,
        val initialLeftPx: Int,
        val initialTopPx: Int,
        val saveMode: SaveMode,
        val scriptTemplateFile: File? = null,
    )

    private enum class SaveMode {
        OVERRIDE,
        OVERWRITE_SOURCE,
    }

    private enum class StarDetectionMode(
        val optionName: String,
        val label: String,
        val colorLabel: String,
        val threshold: Float,
    ) {
        ORANGE(ORANGE_STAR_OPTION, ORANGE_STAR_LABEL, "orange", ORANGE_STAR_THRESHOLD),
        PURPLE(PURPLE_STAR_OPTION, PURPLE_STAR_LABEL, "purple", PURPLE_STAR_THRESHOLD),
    }

    private data class StarCandidate(
        val center: PointF,
        val confidence: Float,
        val glowScore: Float,
        val glowRatio: Float,
        val shapeScore: Float,
        val armContinuity: Float,
        val boundsWidth: Int,
        val boundsHeight: Int,
    )

    private data class DisplayMapping(
        val displayWidth: Float,
        val displayHeight: Float,
        val displayToScreenshotX: Float,
        val displayToScreenshotY: Float,
    )

    private data class TemplateDelayEntry(
        val taskId: Int,
        val baseDelayMs: Long,
    )

    private data class SearchArea(
        val label: String,
        val rect: Rect,
        val threshold: Float,
    )

    private data class AreaMatchScan(
        val hits: List<TemplateMatchHit>,
        val bestCandidate: TemplateMatchHit?,
    )

    private data class TemplateScanSummary(
        val hits: List<TemplateMatchHit>,
        val bestCandidate: TemplateMatchHit?,
        val areas: List<SearchArea> = emptyList(),
    )

    private data class TemplateMatchHit(
        val rect: Rect,
        val score: Float,
        val areaLabel: String,
    )

    private data class FateCorrection(
        val displayText: String,
        val score: Float,
    )

    private data class NameCorrection(
        val displayText: String,
        val score: Float,
    )

    private val gson = Gson()
    private val templateOptionsByTask = linkedMapOf<String, List<String>>()
    private val dailyScriptIndexesByTask = linkedMapOf<String, DailyScriptDebugIndex>()
    private val templateDelayEntriesByTaskTemplate = linkedMapOf<String, MutableList<TemplateDelayEntry>>()
    private val taskEntriesByKey = linkedMapOf<String, TestTaskEntry>()
    private val fateCorrectionCandidates by lazy {
        AgentRepository.AGENT_MAP.values
            .flatMap { attr -> attr.talents.values }
            .distinct()
    }
    private val agentNames by lazy { AgentRepository.AGENT_MAP.keys.toList() }
    private val logs = mutableListOf<String>()

    private var currentBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var availableTasks = emptyList<String>()
    private var availableTemplates = emptyList<String>()
    private var selectedTask = TASK_BATTLE_FLOW
    private var selectedTemplate = START_BATTLE_OCR_OPTION
    private var isLocalScopeEnabled = false
    private var isOpenCvReady = false
    private var delayInput = ""
    private var uploadedImageUri: Uri? = null
    private var uploadedImageName: String? = null
    private var activeReplacementSession: ReplacementSession? = null

    fun initialize() {
        initializeOpenCv()
        loadTaskTemplateOptions()
        log("调试页已切到新工作台")
        pushState()
    }

    fun refreshFromExternalChanges() {
        delayInput = storedDelayValue().takeIf { it > 0L }?.toString().orEmpty()
        pushState()
    }

    fun handleImagePicked(uri: Uri?) {
        if (uri == null) return
        try {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            currentBitmap = activity.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            previewBitmap = currentBitmap
            uploadedImageUri = uri
            uploadedImageName = displayNameForUri(uri)
            log("Screenshot loaded: ${currentBitmap?.width ?: 0} x ${currentBitmap?.height ?: 0}")
            pushState()
        } catch (error: Exception) {
            showToast("截图读取失败")
            log("Image load failed: ${error.message}")
        }
    }

    fun selectTask(taskKey: String) {
        if (taskKey == selectedTask) return
        selectedTask = taskKey
        refreshTemplateOptions()
    }

    fun selectTemplate(templateName: String) {
        if (templateName == selectedTemplate) return
        selectedTemplate = templateName
        delayInput = storedDelayValue().takeIf { it > 0L }?.toString().orEmpty()
        pushState()
    }

    fun selectScope(localScope: Boolean) {
        isLocalScopeEnabled = localScope
        pushState()
    }

    fun updateDelayInput(value: String) {
        delayInput = value
        pushState()
    }

    fun saveDelayIncrement() {
        if (currentTemplateDelayEntries().isEmpty()) {
            showToast("当前识别项不支持延迟增量")
            return
        }
        val input = delayInput.trim()
        val incrementMs = input.toLongOrNull()
        if (input.isNotEmpty() && incrementMs == null) {
            showToast("请输入有效的毫秒数")
            return
        }
        val normalized = (incrementMs ?: 0L).coerceAtLeast(0L)
        TemplateDelayOverrideStore.setIncrementMs(activity, selectedTask, selectedTemplate, normalized)
        delayInput = if (normalized > 0L) normalized.toString() else ""
        log("Delay override saved: ${taskDisplayName(selectedTask)} / ${templateDisplayName(selectedTemplate)} +${normalized}ms")
        pushState()
    }

    fun clearDelayIncrement() {
        TemplateDelayOverrideStore.setIncrementMs(activity, selectedTask, selectedTemplate, 0L)
        delayInput = ""
        log("Delay override cleared: ${taskDisplayName(selectedTask)} / ${templateDisplayName(selectedTemplate)}")
        pushState()
    }

    fun restoreCurrentTemplate() {
        dismissReplacementDialog()
        val userScriptNode = dailyScriptIndexesByTask[selectedTask]
            ?.nodesFor(selectedTemplate)
            ?.firstOrNull { it.replacementTemplateName != null }
        if (currentTaskEntry()?.isUserScript == true && userScriptNode?.action != "OCR") {
            showToast("录制脚本素材已直接覆盖，暂不支持还原")
            return
        }
        val templateName = selectedTemplate
        val replacementTargets = replacementTargetsForOption(templateName)
        if (replacementTargets.isEmpty()) {
            showToast("当前识别项不支持素材还原")
            return
        }
        if (!hasTemplateOverride(templateName)) {
            showToast("当前没有替换版素材")
            return
        }
        val restored = if (templateName == START_BATTLE_OCR_OPTION) {
            TemplateOverrideStore.restoreOverride(activity, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)
        } else {
            replacementTargets
                .map { TemplateOverrideStore.restoreOverride(activity, it.fileName) }
                .all { it }
        }
        if (!restored) {
            showToast("还原失败")
            return
        }
        log("Template restored: ${templateDisplayName(templateName)}")
        pushState()
    }

    fun runCurrentTest() {
        if (
            selectedTemplate != BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION &&
            selectedTemplate != DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION &&
            selectedTemplate != MAINLINE_624_START_BATTLE_DELAY_OPTION &&
            currentBitmap == null
        ) {
            showToast("请先上传截图")
            return
        }
        when (selectedTemplate) {
            BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION -> runBattleFlowFirstActionDelayTest()
            DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION -> runDaiBanGongWuStartBattleDelayTest()
            MAINLINE_624_START_BATTLE_DELAY_OPTION -> runMainline624StartBattleDelayTest()
            BATTLE_TURN_OCR_OPTION -> runBattleTurnOcrTest()
            STONE_GRID_DEBUG_OPTION -> runStoneGridDebugTest()
            CHARACTER_NAME_OPTION -> runCharacterFateOcrTest()
            CHARACTER_PROFICIENCY_OPTION -> runCharacterProficiencyOcrTest()
            CHARACTER_FATE_OPTION -> runCharacterFateOcrTest()
            START_BATTLE_OCR_OPTION -> runStartBattleOcrTest()
            ORANGE_STAR_OPTION -> runStarTest(StarDetectionMode.ORANGE)
            PURPLE_STAR_OPTION -> runStarTest(StarDetectionMode.PURPLE)
            else -> {
                if (isDeathCheckOption(selectedTemplate)) {
                    val slotIndex = deathCheckSlotFromOption(selectedTemplate)
                    if (slotIndex == null) {
                        showToast("当前阵亡检测配置无效")
                    } else {
                        runDeathCheckTest(slotIndex)
                    }
                } else if (shouldRunDailyScriptOcrTest(selectedTemplate)) {
                    runDailyScriptConfiguredOcrTest(selectedTemplate)
                } else if (shouldRunTemplateMatchLocally(selectedTemplate)) {
                    runLocalTemplateMatchTest(selectedTemplate)
                } else {
                    stopCurrentRun(
                        reason = "当前识别项尚未接入新工作台: ${taskDisplayName(selectedTask)} / ${templateDisplayName(selectedTemplate)}",
                        toastMessage = "当前识别项尚未接入新工作台",
                    )
                }
            }
        }
    }

    fun replaceCurrentTemplate() {
        if (currentBitmap == null) {
            showToast("请先上传截图")
            return
        }
        if (replacementTargetsForOption(selectedTemplate).isEmpty()) {
            showToast("当前识别项不支持替换素材")
            return
        }
        openReplacementDialog()
    }

    fun dismissReplacementDialog() {
        activeReplacementSession?.previewBitmap?.takeIf { !it.isRecycled }?.recycle()
        activeReplacementSession = null
        pushState()
    }

    fun confirmReplacement(selectionLeftPx: Int, selectionTopPx: Int) {
        val session = activeReplacementSession ?: return
        val safeLeft = selectionLeftPx.coerceIn(0, (session.previewBitmap.width - session.boxWidthPx).coerceAtLeast(0))
        val safeTop = selectionTopPx.coerceIn(0, (session.previewBitmap.height - session.boxHeightPx).coerceAtLeast(0))
        val replacementBitmap = Bitmap.createBitmap(
            session.previewBitmap,
            safeLeft,
            safeTop,
            session.boxWidthPx.coerceAtLeast(1),
            session.boxHeightPx.coerceAtLeast(1),
        )
        val normalizedBitmap = Bitmap.createScaledBitmap(
            replacementBitmap,
            REPLACEMENT_TEMPLATE_BASE_SIZE.toInt(),
            REPLACEMENT_TEMPLATE_BASE_SIZE.toInt(),
            true,
        )
        val saved = try {
            when (session.saveMode) {
                SaveMode.OVERRIDE -> TemplateOverrideStore.saveOverride(activity, session.target.fileName, normalizedBitmap)
                SaveMode.OVERWRITE_SOURCE -> {
                    val targetFile = session.scriptTemplateFile ?: return
                    overwriteTemplateFile(targetFile, normalizedBitmap)
                }
            }
        } finally {
            normalizedBitmap.recycle()
            replacementBitmap.recycle()
        }
        if (!saved) {
            showToast("替换失败")
            return
        }
        log(
            when (session.saveMode) {
                SaveMode.OVERRIDE -> "Template replaced: ${taskDisplayName(selectedTask)} / ${session.target.label}"
                SaveMode.OVERWRITE_SOURCE -> "Template replaced: ${taskDisplayName(selectedTask)} / ${session.target.label} -> ${session.target.fileName}"
            }
        )
        dismissReplacementDialog()
        pushState()
    }

    fun copyLog() {
        val logText = logs.joinToString("\n").ifBlank { "暂无日志" }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("debug_log", logText))
        showToast("日志已复制")
    }

    private fun initializeOpenCv() {
        if (isOpenCvReady) return
        isOpenCvReady = OpenCVLoader.initDebug()
        log(if (isOpenCvReady) "OpenCV init success" else "OpenCV init failed")
    }

    private fun runBattleFlowFirstActionDelayTest() {
        val baseDelayMs = battleFlowFirstActionBaseDelayMs()
        val incrementMs = storedDelayValue()
        val actualDelayMs = baseDelayMs + incrementMs
        log("第1回合第1个操作延时：基础 ${baseDelayMs}ms，当前项增加 ${incrementMs}ms，运行时 ${actualDelayMs}ms")
        pushState()
        showToast("当前运行时延时 ${actualDelayMs}ms")
    }

    private fun runDaiBanGongWuStartBattleDelayTest() {
        val baseDelayMs = daiBanGongWuStartBattleBaseDelayMs()
        val incrementMs = storedDelayValue()
        val actualDelayMs = baseDelayMs + incrementMs
        log("待办公务开始战斗前延时：基础 ${baseDelayMs}ms，当前项增加 ${incrementMs}ms，运行时 ${actualDelayMs}ms")
        pushState()
        showToast("当前运行时延时 ${actualDelayMs}ms")
    }

    private fun runMainline624StartBattleDelayTest() {
        val baseDelayMs = 3000L
        val incrementMs = storedDelayValue()
        val actualDelayMs = baseDelayMs + incrementMs
        log("主线624开始战斗前延时：基础 ${baseDelayMs}ms，当前项增加 ${incrementMs}ms，运行时 ${actualDelayMs}ms")
        pushState()
        showToast("当前运行时延时 ${actualDelayMs}ms")
    }

    private fun runBattleTurnOcrTest() {
        log("------------------------")
        log("Start matching: $BATTLE_TURN_OCR_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val areaRect = buildTopRightRect(screenshot, BATTLE_TURN_OCR_W, BATTLE_TURN_OCR_H)
        if (areaRect == null) {
            previewBitmap = screenshot
            log("构建识别范围失败")
            pushState()
            return
        }

        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(areaRect, searchPaint)
        previewBitmap = screenshot
        pushState()

        val bitmap = Bitmap.createBitmap(screenshot, areaRect.left, areaRect.top, areaRect.width(), areaRect.height())
        recognizeChineseText(
            bitmap = bitmap,
            onSuccess = { rawText ->
                val normalizedText = rawText.filterNot { it.isWhitespace() }
                val hitChars = listOf('波', '次', '回', '合').filter { normalizedText.contains(it) }
                val turnNumber = parseBattleTurnNumber(normalizedText)
                log("OCR raw=${formatOcrLog(rawText)}")
                log("OCR normalized=${formatOcrLog(normalizedText)}")
                log("OCR hits=${if (hitChars.isEmpty()) "无" else hitChars.joinToString("")} count=${hitChars.size}")
                log("OCR turn=${turnNumber ?: "无"}")
                pushState()
                bitmap.recycle()
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                pushState()
                bitmap.recycle()
            },
        )
    }

    private fun runStoneGridDebugTest() {
        val source = currentBitmap ?: return
        log("------------------------")
        log("Start matching: $STONE_GRID_DEBUG_LABEL")
        ocrScope.launch {
            runCatching {
                val analysis = StonePaddleLocalRecognizer.analyzeForDebug(activity, source)
                val preview = StonePaddleLocalRecognizer.drawDebugPreview(source, analysis)
                withContext(Dispatchers.Main) {
                    previewBitmap = preview
                    analysis.debugLines.forEach(::log)
                    analysis.cardRows.forEach { row ->
                        row.cards.forEach { card ->
                            log(
                                "row=${row.rowIndex + 1} col=${card.columnIndex + 1} " +
                                    "level=${card.levelText.ifBlank { "?" }} name=${card.nameText.ifBlank { "?" }}"
                            )
                        }
                    }
                    pushState()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    previewBitmap = source
                    log("星石区域划分失败: ${error.message}")
                    pushState()
                }
            }
        }
    }

    private fun runStartBattleOcrTest() {
        if (isStartBattleTemplateModeEnabled()) {
            runStartBattleTemplateMatchTest()
            return
        }
        log("------------------------")
        log("Start matching: $START_BATTLE_OCR_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val searchRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = START_BATTLE_RED_CENTER_X,
            y = START_BATTLE_RED_CENTER_Y,
            align = "bottom",
            w = START_BATTLE_RED_ROI_WIDTH,
            h = START_BATTLE_RED_ROI_HEIGHT,
        )
        if (searchRect == null) {
            previewBitmap = screenshot
            log("构建识别范围失败")
            pushState()
            return
        }

        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(searchRect, searchPaint)
        previewBitmap = screenshot
        log("开始战斗识别范围已标记，阈值=${"%.2f".format(START_BATTLE_RED_THRESHOLD)}")
        pushState()

        val bitmap = Bitmap.createBitmap(
            screenshot,
            searchRect.left,
            searchRect.top,
            searchRect.width(),
            searchRect.height(),
        )
        recognizeChineseText(
            bitmap = bitmap,
            onSuccess = { rawText ->
                logStartBattleOcrResult(prefix = "OCR", rawText = rawText)
                pushState()
                bitmap.recycle()
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                pushState()
                bitmap.recycle()
            },
        )
    }

    private fun runStartBattleTemplateMatchTest() {
        log("------------------------")
        log("Start matching: $START_BATTLE_OCR_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val templateBitmap = TemplateOverrideStore.loadBitmap(
            activity,
            activity.assets,
            TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME,
        ) ?: return log("Load template failed: ${TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME}")
        val searchAreas = if (!isLocalScopeEnabled) {
            listOf(SearchArea("full-screen", Rect(0, 0, screenshot.width, screenshot.height), TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD))
        } else {
            buildStartBattleSearchAreas(screenshot)
        }
        if (searchAreas.isEmpty()) {
            log("No start battle search area")
            templateBitmap.recycle()
            return
        }
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val scaledWidth = (templateBitmap.width * gameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (templateBitmap.height * gameScale).toInt().coerceAtLeast(1)
        val scaledTemplate =
            if (scaledWidth == templateBitmap.width && scaledHeight == templateBitmap.height) {
                templateBitmap
            } else {
                Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
            }
        try {
            val canvas = Canvas(screenshot)
            val searchPaint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val hitPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            var bestHit: TemplateMatchHit? = null
            searchAreas.forEach { area ->
                canvas.drawRect(area.rect, searchPaint)
                val scan = findMatchesInArea(screenshot, area, scaledTemplate, area.threshold)
                scan.bestCandidate?.let { candidate ->
                    log("Candidate ${area.label}: score=${"%.3f".format(candidate.score)}")
                    if (candidate.score >= area.threshold && (bestHit == null || candidate.score > bestHit!!.score)) {
                        bestHit = candidate
                    }
                } ?: log("Candidate ${area.label}: score=0.000")
            }
            val hit = bestHit
            if (hit == null) {
                log("No start battle template detected")
            } else {
                canvas.drawRect(hit.rect, hitPaint)
                log("Start battle template hit: ${hit.areaLabel} score=${"%.3f".format(hit.score)}")
            }
            previewBitmap = screenshot
            pushState()
        } finally {
            if (scaledTemplate !== templateBitmap) scaledTemplate.recycle()
            templateBitmap.recycle()
        }
    }

    private fun runCharacterProficiencyOcrTest() {
        log("------------------------")
        log("Start matching: $CHARACTER_PROFICIENCY_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val valueRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = CHARACTER_VALUE_CENTER_X,
            y = CHARACTER_VALUE_CENTER_Y,
            align = "bottom",
            w = CHARACTER_VALUE_ROI_WIDTH,
            h = CHARACTER_VALUE_ROI_HEIGHT,
        )
        val starRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = CHARACTER_STAR_CENTER_X,
            y = CHARACTER_STAR_CENTER_Y,
            align = "bottom",
            w = CHARACTER_STAR_ROI_SIZE,
            h = CHARACTER_STAR_ROI_SIZE,
        )
        if (valueRect == null || starRect == null) {
            previewBitmap = screenshot
            log("构建角色练度识别范围失败")
            pushState()
            return
        }

        val canvas = Canvas(screenshot)
        val valuePaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(valueRect, valuePaint)
        canvas.drawRect(
            starRect,
            Paint().apply {
                color = Color.CYAN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            },
        )
        previewBitmap = screenshot
        log("角色练度识别范围已标记")
        pushState()

        val valueBitmap = Bitmap.createBitmap(source, valueRect.left, valueRect.top, valueRect.width(), valueRect.height())
        val starBitmap = Bitmap.createBitmap(source, starRect.left, starRect.top, starRect.width(), starRect.height())
        val starResult = CharacterStarDetector.detect(starBitmap)
        recognizeCharacterValueText(
            valueBitmap = valueBitmap,
            onSuccess = { valueResult ->
                logCharacterProficiencyResult(valueResult, starResult)
                pushState()
                valueBitmap.recycle()
                starBitmap.recycle()
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                pushState()
                valueBitmap.recycle()
                starBitmap.recycle()
            },
        )
    }

    private fun runCharacterNameOcrTest() {
        log("------------------------")
        log("Start matching: $CHARACTER_FATE_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val nameRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = CHARACTER_NAME_CENTER_X,
            y = CHARACTER_NAME_CENTER_Y,
            align = "center",
            w = CHARACTER_NAME_ROI_WIDTH,
            h = CHARACTER_NAME_ROI_HEIGHT,
        )
        if (nameRect == null) {
            previewBitmap = screenshot
            log("构建角色名识别范围失败")
            pushState()
            return
        }

        Canvas(screenshot).drawRect(
            nameRect,
            Paint().apply {
                color = Color.CYAN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            },
        )
        previewBitmap = screenshot
        log("角色名识别范围已标记")
        pushState()

        val nameBitmap = Bitmap.createBitmap(source, nameRect.left, nameRect.top, nameRect.width(), nameRect.height())
        recognizeCharacterNameText(
            nameBitmap = nameBitmap,
            onSuccess = { nameResult ->
                logCharacterNameResult(nameResult)
                pushState()
                nameBitmap.recycle()
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                pushState()
                nameBitmap.recycle()
            },
        )
    }

    private fun runCharacterFateOcrTest() {
        log("------------------------")
        log("Start matching: $CHARACTER_FATE_LABEL")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val nameRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = CHARACTER_NAME_CENTER_X,
            y = CHARACTER_NAME_CENTER_Y,
            align = "center",
            w = CHARACTER_NAME_ROI_WIDTH,
            h = CHARACTER_NAME_ROI_HEIGHT,
        )
        if (nameRect == null) {
            previewBitmap = screenshot
            log("构建角色名识别范围失败")
            pushState()
            return
        }
        val canvas = Canvas(screenshot)
        val paint = Paint().apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(nameRect, paint)
        previewBitmap = screenshot
        log("角色名识别范围已标记")
        pushState()

        val nameBitmap = Bitmap.createBitmap(source, nameRect.left, nameRect.top, nameRect.width(), nameRect.height())
        recognizeCharacterNameText(
            nameBitmap = nameBitmap,
            onSuccess = { nameResult ->
                val roleName = parseCharacterNames(nameResult.text).firstOrNull().orEmpty()
                val fatePoints = resolveCharacterFatePoints(roleName)
                val fateRects = fatePoints.mapIndexedNotNull { index, point ->
                    buildFixedRectFromVisionRegion(
                        screenshot = screenshot,
                        x = point.first,
                        y = point.second,
                        align = "center",
                        w = CHARACTER_FATE_ROI_SIZE,
                        h = CHARACTER_FATE_ROI_SIZE,
                    )?.let { rect -> index to rect }
                }
                if (fateRects.size != fatePoints.size) {
                    previewBitmap = screenshot
                    logCharacterNameResult(nameResult)
                    log("构建命盘识别范围失败")
                    pushState()
                    nameBitmap.recycle()
                    return@recognizeCharacterNameText
                }

                fateRects.forEach { (index, rect) ->
                    canvas.drawRect(rect, paint)
                    log("命盘识别范围 ${index + 1} 已标记")
                }
                log(
                    "命盘取点角色=${roleName.ifBlank { "未识别" }} " +
                        "points=${fatePoints.joinToString(" / ") { "${it.first.toInt()},${it.second.toInt()}" }}"
                )
                previewBitmap = screenshot
                pushState()

                val bitmaps = fateRects.map { (_, rect) ->
                    Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
                }
                recognizeCharacterFateText(
                    bitmaps = bitmaps,
                    onSuccess = { results ->
                        logCharacterNameResult(nameResult)
                        logCharacterFateResult(results)
                        pushState()
                        bitmaps.forEach(Bitmap::recycle)
                        nameBitmap.recycle()
                    },
                    onFailure = { error ->
                        logCharacterNameResult(nameResult)
                        log("OCR failed: ${error.message}")
                        pushState()
                        bitmaps.forEach(Bitmap::recycle)
                        nameBitmap.recycle()
                    },
                )
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                pushState()
                nameBitmap.recycle()
            },
        )
    }

    private fun runDeathCheckTest(slotIndex: Int) {
        log("------------------------")
        log("Start matching: ${templateDisplayName(deathCheckOption(slotIndex))}")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val slotRect = buildDeathCheckRect(screenshot, slotIndex)
        if (slotRect == null) {
            previewBitmap = screenshot
            log("构建槽位识别范围失败")
            pushState()
            return
        }
        val slotBitmap = Bitmap.createBitmap(screenshot, slotRect.left, slotRect.top, slotRect.width(), slotRect.height())
        val meanSaturation = try {
            computeCenterMeanSaturation(slotBitmap)
        } finally {
            slotBitmap.recycle()
        }
        val canvas = Canvas(screenshot)
        val searchPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(slotRect, searchPaint)
        previewBitmap = screenshot
        log(
            "Death check slot=$slotIndex " +
                "meanSaturation=${"%.2f".format(meanSaturation)} " +
                "threshold=${"%.2f".format(DEATH_CHECK_SATURATION_THRESHOLD)} " +
                "isDead=${meanSaturation < DEATH_CHECK_SATURATION_THRESHOLD}"
        )
        pushState()
    }

    private fun runStarTest(mode: StarDetectionMode) {
        log("------------------------")
        log("Start matching: ${mode.label}")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val roiRect = buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = ORANGE_STAR_CENTER_X,
            y = ORANGE_STAR_CENTER_Y,
            align = "top",
            w = ORANGE_STAR_ROI_SIZE,
            h = ORANGE_STAR_ROI_SIZE,
        )
        if (roiRect == null) {
            log("${mode.colorLabel}星识别范围无效")
            previewBitmap = screenshot
            pushState()
            return
        }
        Canvas(screenshot).drawRect(
            roiRect,
            Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            },
        )
        val candidate = findStarCandidateInScreenshot(screenshot, mode)
        if (candidate == null || candidate.shapeScore < ORANGE_STAR_SHAPE_THRESHOLD) {
            log("${mode.colorLabel} star not found")
        } else {
            val hit = candidate.confidence >= mode.threshold &&
                candidate.glowRatio >= ORANGE_STAR_MIN_GLOW_RATIO
            val halfW = (candidate.boundsWidth / 2f).coerceAtLeast(8f)
            val halfH = (candidate.boundsHeight / 2f).coerceAtLeast(8f)
            val hitRect = Rect(
                (candidate.center.x - halfW).toInt(),
                (candidate.center.y - halfH).toInt(),
                (candidate.center.x + halfW).toInt(),
                (candidate.center.y + halfH).toInt(),
            )
            Canvas(screenshot).drawRect(
                hitRect,
                Paint().apply {
                    color = if (hit) Color.RED else Color.YELLOW
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                },
            )
            log(
                "${mode.colorLabel.replaceFirstChar { it.uppercase() }} star candidate: " +
                    "x=${candidate.center.x.toInt()} y=${candidate.center.y.toInt()} " +
                    "confidence=${"%.3f".format(candidate.confidence)} " +
                    "shape=${"%.3f".format(candidate.shapeScore)} " +
                    "continuity=${"%.3f".format(candidate.armContinuity)} " +
                    "glow=${"%.3f".format(candidate.glowScore)} " +
                    "glowRatio=${"%.3f".format(candidate.glowRatio)} " +
                    "threshold=${"%.3f".format(mode.threshold)} " +
                    "hit=$hit",
            )
        }
        previewBitmap = screenshot
        pushState()
    }

    private fun runLocalTemplateMatchTest(templateName: String) {
        log("------------------------")
        log("Start matching: ${templateDisplayName(templateName)}")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val summary = when (templateName) {
            CAVE_DONGKU_OPTION -> runCaveDongkuTemplateFallbackTest(screenshot)
            else -> runTemplateSummaryTest(screenshot, templateName, isLocalScopeEnabled)
        } ?: return
        val areaPaint = Paint().apply {
            color = Color.argb(220, 229, 192, 123)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val hitPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val canvas = Canvas(screenshot)
        summary.areas.forEach { area -> canvas.drawRect(area.rect, areaPaint) }
        summary.hits.forEach { hit -> canvas.drawRect(hit.rect, hitPaint) }
        previewBitmap = screenshot
        pushState()
    }

    private fun runDailyScriptConfiguredOcrTest(optionKey: String) {
        log("------------------------")
        log("Start matching: ${templateDisplayName(optionKey)} OCR")
        val source = currentBitmap ?: return
        val screenshot = source.copy(Bitmap.Config.ARGB_8888, true)
        val node = dailyScriptIndexesByTask[selectedTask]
            ?.nodesFor(optionKey)
            ?.firstOrNull { it.isOcrLike }
            ?: return stopCurrentRun(
                reason = "当前脚本 OCR 节点缺少配置: ${taskDisplayName(selectedTask)} / ${templateDisplayName(optionKey)}",
                toastMessage = "当前脚本 OCR 节点缺少配置",
            )
        val area = buildSearchAreaForNode(screenshot, node, dailyScriptIndexesByTask[selectedTask]?.scriptDisplayName)
            ?: return stopCurrentRun(
                reason = "当前脚本 OCR 节点缺少识别范围配置: ${taskDisplayName(selectedTask)} / ${templateDisplayName(optionKey)}",
                toastMessage = "当前脚本 OCR 节点缺少识别范围配置",
            )
        val canvas = Canvas(screenshot)
        canvas.drawRect(
            area.rect,
            Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 5f
            },
        )
        previewBitmap = screenshot
        pushState()

        val roiBitmap = Bitmap.createBitmap(
            source,
            area.rect.left,
            area.rect.top,
            area.rect.width(),
            area.rect.height(),
        )
        val ocrBitmap = createConfiguredOcrBitmap(roiBitmap, node.ocrPreprocess) ?: roiBitmap
        recognizeChineseText(
            bitmap = ocrBitmap,
            onSuccess = { rawText ->
        if (node.ocrTargetChars.isEmpty()) {
                    logStartBattleOcrResult(prefix = "OCR", rawText = rawText)
                } else {
                    logOcrNodeResult(rawText, node)
                }
                if (ocrBitmap !== roiBitmap) ocrBitmap.recycle()
                roiBitmap.recycle()
            },
            onFailure = { error ->
                log("OCR failed: ${error.message}")
                if (ocrBitmap !== roiBitmap) ocrBitmap.recycle()
                roiBitmap.recycle()
            },
        )
    }

    private fun runCaveDongkuTemplateFallbackTest(screenshot: Bitmap): TemplateScanSummary? {
        val primarySummary = runTemplateSummaryTest(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE,
            useLocalScope = isLocalScopeEnabled,
            logLabel = CAVE_DONGKU_TEMPLATE,
        ) ?: return null
        if (primarySummary.hits.isNotEmpty()) {
            return primarySummary
        }
        return runTemplateSummaryTest(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE_FALLBACK,
            useLocalScope = isLocalScopeEnabled,
            logLabel = CAVE_DONGKU_TEMPLATE_FALLBACK,
        )
    }

    private fun overwriteTemplateFile(targetFile: File, bitmap: Bitmap): Boolean {
        val format = when (targetFile.extension.lowercase()) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP
            }
            else -> Bitmap.CompressFormat.PNG
        }
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        return runCatching {
            tempFile.outputStream().use { output ->
                check(bitmap.compress(format, 100, output)) { "compress failed" }
                output.flush()
            }
            tempFile.copyTo(targetFile, overwrite = true)
            true
        }.getOrDefault(false).also {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun openReplacementDialog() {
        val source = currentBitmap ?: return
        val target = replacementTargetsForOption(selectedTemplate).firstOrNull() ?: run {
            showToast("当前识别项不支持替换素材")
            return
        }
        val session = if (currentTaskEntry()?.isUserScript == true) {
            buildUserScriptReplacementSession(source, target)
        } else {
            buildBuiltInReplacementSession(source, target)
        } ?: return
        dismissReplacementDialog()
        activeReplacementSession = session
        pushState()
    }

    private fun buildUserScriptReplacementSession(
        source: Bitmap,
        target: ReplacementTarget,
    ): ReplacementSession? {
        val bundle = currentTaskEntry()?.bundle
        val node = target.node ?: dailyScriptIndexesByTask[selectedTask]
            ?.nodesFor(selectedTemplate)
            ?.firstOrNull { it.replacementTemplateName != null }
        val area = buildSearchAreaForNode(source, node)
        val replacementTemplateName = node?.replacementTemplateName?.takeIf { it.isNotBlank() }
        if (bundle == null || replacementTemplateName == null || area == null) {
            showToast("当前节点不支持替换素材")
            return null
        }
        val replacementNode = node ?: return null
        val templateFile = File(bundle.templatesDir, replacementTemplateName)
        if (replacementNode.action == "MATCH_TEMPLATE" && !templateFile.exists()) {
            showToast("模板文件不存在")
            return null
        }
        val previewBitmap = Bitmap.createBitmap(source, area.rect.left, area.rect.top, area.rect.width(), area.rect.height())
        val gameScale = min(source.width / BASE_W, source.height / BASE_H)
        if (replacementNode.action == "OCR") {
            return buildReplacementSession(
                target = target,
                previewBitmap = previewBitmap,
                sourceRect = area.rect,
                gameScale = gameScale,
                saveMode = SaveMode.OVERRIDE,
            )
        }
        return buildReplacementSession(
            target = target,
            previewBitmap = previewBitmap,
            sourceRect = area.rect,
            gameScale = gameScale,
            saveMode = SaveMode.OVERWRITE_SOURCE,
            scriptTemplateFile = templateFile,
        )
    }

    private fun buildBuiltInReplacementSession(
        source: Bitmap,
        target: ReplacementTarget,
    ): ReplacementSession? {
        val nodeArea = buildSearchAreaForNode(source, target.node)
        val area = nodeArea ?: when (selectedTemplate) {
            START_BATTLE_OCR_OPTION -> buildStartBattleSearchAreas(source).firstOrNull()
            CAVE_DONGKU_OPTION -> buildFixedSearchAreasForTemplate(source, CAVE_DONGKU_TEMPLATE).firstOrNull()
            else -> buildFixedSearchAreasForTemplate(source, selectedTemplate).firstOrNull()
        }
        if (area == null) {
            showToast("当前识别项没有可裁剪的 ROI")
            return null
        }
        val previewBitmap = Bitmap.createBitmap(source, area.rect.left, area.rect.top, area.rect.width(), area.rect.height())
        val gameScale = min(source.width / BASE_W, source.height / BASE_H)
        return buildReplacementSession(
            target = target,
            previewBitmap = previewBitmap,
            sourceRect = area.rect,
            gameScale = gameScale,
            saveMode = SaveMode.OVERRIDE,
        )
    }

    private fun buildReplacementSession(
        target: ReplacementTarget,
        previewBitmap: Bitmap,
        sourceRect: Rect,
        gameScale: Float,
        saveMode: SaveMode,
        scriptTemplateFile: File? = null,
    ): ReplacementSession {
        val baseSizePx = (REPLACEMENT_TEMPLATE_BASE_SIZE * gameScale).toInt().coerceAtLeast(1)
        val boxWidthPx = baseSizePx
        val boxHeightPx = baseSizePx
        val safeBoxWidth = boxWidthPx.coerceAtLeast(1).coerceAtMost(previewBitmap.width)
        val safeBoxHeight = boxHeightPx.coerceAtLeast(1).coerceAtMost(previewBitmap.height)
        val initialLeft = ((previewBitmap.width - safeBoxWidth) / 2).coerceAtLeast(0)
        val initialTop = ((previewBitmap.height - safeBoxHeight) / 2).coerceAtLeast(0)
        return ReplacementSession(
            id = System.currentTimeMillis(),
            target = target,
            previewBitmap = previewBitmap,
            sourceRect = sourceRect,
            gameScale = gameScale,
            boxWidthPx = safeBoxWidth,
            boxHeightPx = safeBoxHeight,
            initialLeftPx = initialLeft,
            initialTopPx = initialTop,
            saveMode = saveMode,
            scriptTemplateFile = scriptTemplateFile,
        )
    }

    private fun runTemplateSummaryTest(
        screenshot: Bitmap,
        templateName: String,
        useLocalScope: Boolean,
        logLabel: String = templateDisplayName(templateName),
    ): TemplateScanSummary? {
        initializeOpenCv()
        if (!isOpenCvReady) {
            stopCurrentRun(
                reason = "OpenCV 初始化失败，无法在新工作台执行模板匹配",
                toastMessage = "OpenCV 初始化失败",
            )
            return null
        }
        val templateBitmap = loadTemplateBitmapForCurrentTask(templateName)
            ?: return TemplateScanSummary(emptyList(), null).also {
                log("Load template failed: $templateName")
            }
        return try {
            val summary = findTemplateMatches(screenshot, templateName, templateBitmap, useLocalScope)
                ?: return null
            logTemplateSummary(logLabel, summary)
            summary
        } finally {
            templateBitmap.recycle()
        }
    }

    private fun logTemplateSummary(label: String, summary: TemplateScanSummary) {
        summary.hits.forEachIndexed { index, hit ->
            log("[$label] Hit ${index + 1}: ${hit.areaLabel} x=${hit.rect.centerX()} y=${hit.rect.centerY()} score=${"%.3f".format(hit.score)}")
        }
        summary.bestCandidate?.let {
            log("[$label] Best score: ${it.areaLabel} x=${it.rect.centerX()} y=${it.rect.centerY()} score=${"%.3f".format(it.score)}")
        }
        if (summary.hits.isEmpty()) {
            log("[$label] No match above threshold")
        }
    }

    private fun findTemplateMatches(
        screenshot: Bitmap,
        templateName: String,
        templateBitmap: Bitmap,
        useLocalScope: Boolean,
    ): TemplateScanSummary? {
        val areas = buildTemplateSearchAreas(screenshot, templateName, useLocalScope) ?: return null
        if (areas.isEmpty()) return TemplateScanSummary(emptyList(), null)
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val scaledWidth = (templateBitmap.width * gameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (templateBitmap.height * gameScale).toInt().coerceAtLeast(1)
        val scaledTemplate =
            if (scaledWidth == templateBitmap.width && scaledHeight == templateBitmap.height) {
                templateBitmap
            } else {
                Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
            }
        return try {
            val scans = areas.map { findMatchesInArea(screenshot, it, scaledTemplate, it.threshold) }
            val hits = dedupeHits(scans.flatMap { it.hits }, scaledTemplate.width / 2.0)
            val bestCandidate = scans.mapNotNull { it.bestCandidate }.maxByOrNull { it.score }
            TemplateScanSummary(hits, bestCandidate, areas)
        } finally {
            if (scaledTemplate !== templateBitmap) {
                scaledTemplate.recycle()
            }
        }
    }

    private fun findMatchesInArea(
        screenshot: Bitmap,
        area: SearchArea,
        templateBitmap: Bitmap,
        threshold: Float,
    ): AreaMatchScan {
        if (area.rect.width() < templateBitmap.width || area.rect.height() < templateBitmap.height) {
            return AreaMatchScan(emptyList(), null)
        }
        val searchBitmap = Bitmap.createBitmap(
            screenshot,
            area.rect.left,
            area.rect.top,
            area.rect.width(),
            area.rect.height(),
        )
        val srcMat = Mat()
        val tmplMat = Mat()
        val resultMat = Mat()
        return try {
            Utils.bitmapToMat(searchBitmap, srcMat)
            Utils.bitmapToMat(templateBitmap, tmplMat)
            Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
            val cols = resultMat.cols()
            val rows = resultMat.rows()
            val scores = FloatArray(cols * rows)
            resultMat.get(0, 0, scores)
            val hits = mutableListOf<TemplateMatchHit>()
            var bestScore = Float.NEGATIVE_INFINITY
            var bestHit: TemplateMatchHit? = null
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val score = scores[y * cols + x]
                    val hit = TemplateMatchHit(
                        rect = Rect(
                            area.rect.left + x,
                            area.rect.top + y,
                            area.rect.left + x + templateBitmap.width,
                            area.rect.top + y + templateBitmap.height,
                        ),
                        score = score,
                        areaLabel = area.label,
                    )
                    if (score > bestScore) {
                        bestScore = score
                        bestHit = hit
                    }
                    if (score >= threshold) {
                        hits += hit
                    }
                }
            }
            AreaMatchScan(hits, bestHit)
        } finally {
            srcMat.release()
            tmplMat.release()
            resultMat.release()
            searchBitmap.recycle()
        }
    }

    private fun buildTemplateSearchAreas(
        screenshot: Bitmap,
        templateName: String,
        useLocalScope: Boolean,
    ): List<SearchArea>? {
        if (!useLocalScope) {
            return listOf(
                SearchArea(
                    label = "full-screen",
                    rect = Rect(0, 0, screenshot.width, screenshot.height),
                    threshold = templateTestThreshold(templateName),
                ),
            )
        }
        val scriptAreas = buildDailyScriptSearchAreas(screenshot, templateName)
        if (scriptAreas.isNotEmpty()) {
            return scriptAreas
        }
        val fixedAreas = buildFixedSearchAreasForTemplate(screenshot, templateName)
        if (fixedAreas.isNotEmpty()) {
            return fixedAreas
        }
        stopCurrentRun(
            reason = "当前局部识别范围尚未接入新工作台: ${taskDisplayName(selectedTask)} / ${templateDisplayName(templateName)}",
            toastMessage = "当前识别项暂未接入新工作台局部识别",
        )
        return null
    }

    private fun buildFixedSearchAreasForTemplate(
        screenshot: Bitmap,
        templateName: String,
    ): List<SearchArea> {
        fun area(
            label: String,
            x: Float,
            y: Float,
            align: String,
            w: Float,
            h: Float,
            threshold: Float,
        ): SearchArea? {
            return buildFixedRectFromVisionRegion(
                screenshot = screenshot,
                x = x,
                y = y,
                align = align,
                w = w,
                h = h,
            )?.let { rect ->
                SearchArea(label = label, rect = rect, threshold = threshold)
            }
        }

        return when (templateName) {
            ALL_WIPE_TEMPLATE -> listOfNotNull(
                area("CombatEngine/失败后再次挑战", ALL_WIPE_TEMPLATE_CENTER_X, ALL_WIPE_TEMPLATE_CENTER_Y, "bottom", ALL_WIPE_TEMPLATE_ROI_SIZE, ALL_WIPE_TEMPLATE_ROI_SIZE, ALL_WIPE_TEMPLATE_THRESHOLD),
            )
            ORANGE_STAR_RECOVERY_TEMPLATE -> listOfNotNull(
                area("CombatEngine/战斗返回确定", ORANGE_STAR_RECOVERY_CENTER_X, ORANGE_STAR_RECOVERY_CENTER_Y, "center", ORANGE_STAR_RECOVERY_ROI_SIZE, ORANGE_STAR_RECOVERY_ROI_SIZE, ORANGE_STAR_RECOVERY_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_LANTAI_TEMPLATE -> listOfNotNull(
                area("主页/兰台", STAGE_HOME_RECOVERY_LANTAI_CENTER_X, STAGE_HOME_RECOVERY_LANTAI_CENTER_Y, "center", STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH, STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT, STAGE_HOME_RECOVERY_LANTAI_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE -> listOfNotNull(
                area("主页/鸢报", STAGE_HOME_RECOVERY_YUANBAO_CENTER_X, STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y, "center", STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH, STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT, STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD),
            )
            BIRD_FOOD_SCREEN_TEMPLATE -> listOfNotNull(
                area("鸢报/突发情况", BIRD_FOOD_SCREEN_CENTER_X, BIRD_FOOD_SCREEN_CENTER_Y, "center", BIRD_FOOD_SCREEN_ROI_WIDTH, BIRD_FOOD_SCREEN_ROI_HEIGHT, BIRD_FOOD_SCREEN_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_BAIHU_TEMPLATE -> listOfNotNull(
                area("主页/白鹄", STAGE_HOME_RECOVERY_BAIHU_CENTER_X, STAGE_HOME_RECOVERY_BAIHU_CENTER_Y, "center", STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH, STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT, STAGE_HOME_RECOVERY_BAIHU_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_DIGONG_TEMPLATE -> listOfNotNull(
                area("主页/地宫", STAGE_HOME_RECOVERY_DIGONG_CENTER_X, STAGE_HOME_RECOVERY_DIGONG_CENTER_Y, "center", STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH, STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT, STAGE_HOME_RECOVERY_DIGONG_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE -> listOfNotNull(
                area("主页/遗迹入口", STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X, STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y, "center", STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH, STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT, STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_XINZHI_TEMPLATE -> listOfNotNull(
                area("主页/心纸营建", STAGE_HOME_RECOVERY_XINZHI_CENTER_X, STAGE_HOME_RECOVERY_XINZHI_CENTER_Y, "center", STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH, STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT, STAGE_HOME_RECOVERY_XINZHI_THRESHOLD),
            )
            STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE -> listOfNotNull(
                area("心纸/历险", STAGE_HOME_RECOVERY_LIXIAN_CENTER_X, STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y, "center", STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH, STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT, STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD),
            )
            CAVE_DONGKU_TEMPLATE, CAVE_DONGKU_TEMPLATE_FALLBACK -> listOfNotNull(
                area("洞窟入口", CAVE_DONGKU_CENTER_X, CAVE_DONGKU_CENTER_Y, "center", CAVE_DONGKU_ROI_WIDTH, CAVE_DONGKU_ROI_HEIGHT, CAVE_DONGKU_THRESHOLD),
            )
            CAVE_NEXT_FLOOR_TEMPLATE -> listOfNotNull(
                area("洞窟/下一层", CAVE_NEXT_FLOOR_CENTER_X, CAVE_NEXT_FLOOR_CENTER_Y, "bottom", CAVE_NEXT_FLOOR_ROI_WIDTH, CAVE_NEXT_FLOOR_ROI_HEIGHT, CAVE_NEXT_FLOOR_THRESHOLD),
            )
            AUTO_SELECT_FILTER_TEMPLATE -> listOfNotNull(
                area("选人界面/筛选", AUTO_SELECT_FILTER_CENTER_X, AUTO_SELECT_FILTER_CENTER_Y, "dynamic_filter_bounds", AUTO_SELECT_FILTER_ROI_WIDTH, AUTO_SELECT_FILTER_ROI_HEIGHT, AUTO_SELECT_FILTER_THRESHOLD),
            )
            else -> emptyList()
        }
    }

    private fun buildDailyScriptSearchAreas(
        screenshot: Bitmap,
        optionKey: String,
    ): List<SearchArea> {
        val index = dailyScriptIndexesByTask[selectedTask] ?: return emptyList()
        return index.nodesFor(optionKey).mapNotNull { node ->
            buildSearchAreaForNode(screenshot, node, index.scriptDisplayName)
        }
    }

    private fun buildSearchAreaForNode(
        screenshot: Bitmap,
        node: DailyScriptDebugNode?,
        scriptDisplayName: String? = null,
    ): SearchArea? {
        node ?: return null
        val nodeLabel = node.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: "ID ${node.taskId}"
        val label = "${scriptDisplayName ?: taskDisplayName(selectedTask)}/$nodeLabel"
        val roi = node.roi
        val hasFixedGeometry = roi?.let {
            (it.x != null || it.centerX != null) &&
                (it.y != null || it.centerY != null) &&
                (it.w != null || it.radius != null) &&
                (it.h != null || it.radius != null)
        } == true
        val isDynamicGeometry = roi?.align == "dynamic_avatar_bounds" || roi?.align == "dynamic_filter_bounds"
        if (roi == null || (roi.w == 0f && roi.h == 0f) || (!isDynamicGeometry && !hasFixedGeometry)) {
            return SearchArea(
                label = label,
                rect = Rect(0, 0, screenshot.width, screenshot.height),
                threshold = node.threshold,
            )
        }
        return buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = roi.x ?: roi.centerX ?: return null,
            y = roi.y ?: roi.centerY ?: return null,
            align = roi.align,
            w = roi.w ?: roi.radius?.let { it * 2 } ?: return null,
            h = roi.h ?: roi.radius?.let { it * 2 } ?: return null,
        )?.let { rect ->
            SearchArea(
                label = label,
                rect = rect,
                threshold = node.threshold,
            )
        }
    }

    private fun buildStartBattleSearchAreas(screenshot: Bitmap): List<SearchArea> =
        buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = START_BATTLE_RED_CENTER_X,
            y = START_BATTLE_RED_CENTER_Y,
            align = "bottom",
            w = START_BATTLE_RED_ROI_WIDTH,
            h = START_BATTLE_RED_ROI_HEIGHT,
        )?.let { rect ->
            listOf(
                SearchArea(
                    label = "CombatEngine/开始战斗",
                    rect = rect,
                    threshold = TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD,
                ),
            )
        }.orEmpty()

    private fun templateTestThreshold(templateName: String): Float =
        dailyScriptIndexesByTask[selectedTask]?.thresholdFor(templateName) ?: when (templateName) {
            YOUZHOU_TEMPLATE, JINRU_TEMPLATE -> MAINLINE_624_BRANCH_THRESHOLD
            ALL_WIPE_TEMPLATE -> ALL_WIPE_TEMPLATE_THRESHOLD
            ORANGE_STAR_RECOVERY_TEMPLATE -> ORANGE_STAR_RECOVERY_THRESHOLD
            STAGE_HOME_RECOVERY_LANTAI_TEMPLATE -> STAGE_HOME_RECOVERY_LANTAI_THRESHOLD
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE -> STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD
            STAGE_HOME_RECOVERY_BAIHU_TEMPLATE -> STAGE_HOME_RECOVERY_BAIHU_THRESHOLD
            STAGE_HOME_RECOVERY_DIGONG_TEMPLATE -> STAGE_HOME_RECOVERY_DIGONG_THRESHOLD
            STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE -> STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD
            STAGE_HOME_RECOVERY_XINZHI_TEMPLATE -> STAGE_HOME_RECOVERY_XINZHI_THRESHOLD
            STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE -> STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD
            CAVE_DONGKU_TEMPLATE, CAVE_DONGKU_TEMPLATE_FALLBACK -> CAVE_DONGKU_THRESHOLD
            CAVE_NEXT_FLOOR_TEMPLATE -> CAVE_NEXT_FLOOR_THRESHOLD
            AUTO_SELECT_FILTER_TEMPLATE -> AUTO_SELECT_FILTER_THRESHOLD
            DAI_BAN_GONG_WU_SWEEP_TEMPLATE, "jinrutiaozhan.png", "jinruzhandou.png", "qianwangtaofa.png" -> 0.80f
            "yiji1.png", "yiji2.png", "yiji3.png", "yiji4.png", "yiji5.png" -> 0.75f
            else -> DEFAULT_TEMPLATE_TEST_THRESHOLD
        }

    private fun loadTemplateBitmapForCurrentTask(templateName: String): Bitmap? {
        val node = dailyScriptIndexesByTask[selectedTask]
            ?.nodesFor(templateName)
            ?.firstOrNull { it.replacementTemplateName != null }
        val actualTemplateName = node
            ?.replacementTemplateName
            ?: templateName
        val bundle = currentTaskEntry()?.bundle
        if (bundle != null) {
            val templateFile = File(bundle.templatesDir, actualTemplateName)
            return if (templateFile.exists()) BitmapFactory.decodeFile(templateFile.absolutePath) else null
        }
        return TemplateOverrideStore.loadBitmap(activity, activity.assets, templateAssetKey(node, actualTemplateName))
    }

    private fun dedupeHits(hits: List<TemplateMatchHit>, radius: Double): List<TemplateMatchHit> {
        val result = mutableListOf<TemplateMatchHit>()
        hits.sortedByDescending { it.score }.forEach { hit ->
            val overlap = result.any {
                hypot(
                    (hit.rect.exactCenterX() - it.rect.exactCenterX()).toDouble(),
                    (hit.rect.exactCenterY() - it.rect.exactCenterY()).toDouble(),
                ) < radius
            }
            if (!overlap) {
                result += hit
            }
        }
        return result
    }

    private fun shouldRunTemplateMatchLocally(templateName: String): Boolean {
        if (shouldRunDailyScriptOcrTest(templateName)) {
            return false
        }
        return templateName == CAVE_DONGKU_OPTION || isImageTemplateFile(templateName)
    }

    private fun shouldRunDailyScriptOcrTest(templateName: String): Boolean {
        val index = dailyScriptIndexesByTask[selectedTask] ?: return false
        return index.isOcrNode(templateName) && !hasTemplateOverride(templateName)
    }

    private fun supportsLocalTemplateScope(templateName: String): Boolean =
        when (templateName) {
            CAVE_DONGKU_OPTION,
            ALL_WIPE_TEMPLATE,
            ORANGE_STAR_RECOVERY_TEMPLATE,
            STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
            BIRD_FOOD_SCREEN_TEMPLATE,
            STAGE_HOME_RECOVERY_BAIHU_TEMPLATE,
            STAGE_HOME_RECOVERY_DIGONG_TEMPLATE,
            STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE,
            STAGE_HOME_RECOVERY_XINZHI_TEMPLATE,
            STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE,
            CAVE_DONGKU_TEMPLATE,
            CAVE_DONGKU_TEMPLATE_FALLBACK,
            CAVE_NEXT_FLOOR_TEMPLATE,
            AUTO_SELECT_FILTER_TEMPLATE -> true
            else -> false
        }

    private fun stopCurrentRun(
        reason: String,
        toastMessage: String,
    ) {
        log(reason)
        showToast(toastMessage)
        pushState()
    }

    private fun loadTaskTemplateOptions() {
        try {
            loadUserScriptTaskEntries()
            buildTaskTemplateOptions()
            loadTemplateDelayEntries()
            availableTasks = buildList {
                TASK_ORDER.forEach { taskKey ->
                    if (templateOptionsByTask[taskKey].orEmpty().isNotEmpty()) {
                        add(taskKey)
                    }
                }
                dailyScriptIndexesByTask.keys.forEach { scriptFile ->
                    if (templateOptionsByTask[scriptFile].orEmpty().isNotEmpty()) {
                        add(scriptFile)
                    }
                }
            }
            if (selectedTask !in availableTasks) {
                selectedTask = availableTasks.firstOrNull() ?: TASK_BATTLE_FLOW
            }
            refreshTemplateOptions()
        } catch (error: Exception) {
            log("Read task materials failed: ${error.message}")
        }
    }

    private fun loadUserScriptTaskEntries() {
        val builtInEntries = TASK_ORDER.associateWith { taskKey ->
            TestTaskEntry(
                key = taskKey,
                displayName = TASK_DISPLAY_NAME_MAP[taskKey] ?: taskKey,
            )
        }
        taskEntriesByKey.clear()
        taskEntriesByKey.putAll(builtInEntries)
        loadDailyScriptFiles().forEach { scriptFile ->
            val index = buildDailyScriptDebugIndex(scriptFile)
            taskEntriesByKey[scriptFile] = TestTaskEntry(
                key = scriptFile,
                displayName = index?.scriptDisplayName
                    ?: TASK_DISPLAY_NAME_MAP[scriptFile]
                    ?: baseNameWithoutExtension(scriptFile),
            )
        }
        UserDailyScriptStore.listBundles(activity).forEach { bundle ->
            val taskKey = userTaskKey(bundle.scriptId)
            taskEntriesByKey[taskKey] = TestTaskEntry(
                key = taskKey,
                displayName = bundle.scriptId,
                bundle = bundle,
            )
        }
    }

    private fun buildTaskTemplateOptions() {
        templateOptionsByTask.clear()
        dailyScriptIndexesByTask.clear()
        templateOptionsByTask[TASK_BATTLE_FLOW] = buildBattleFlowTemplateOptions()
        templateOptionsByTask[TASK_BIRD_FOOD_NAV] = buildBirdFoodNavigationTemplateOptions()
        loadDailyScriptFiles().forEach { scriptFile ->
            val index = dailyScriptIndexesByTask[scriptFile] ?: buildDailyScriptDebugIndex(scriptFile)
            if (index != null) {
                dailyScriptIndexesByTask[scriptFile] = index
                templateOptionsByTask[scriptFile] = dailyScriptTemplateOptions(scriptFile, index.templateNames)
            } else {
                templateOptionsByTask[scriptFile] = emptyList()
            }
        }
        taskEntriesByKey.values
            .filter(TestTaskEntry::isUserScript)
            .forEach { entry ->
                val bundle = entry.bundle ?: return@forEach
                val index = buildUserScriptDebugIndex(entry.key, entry.displayName, bundle)
                if (index != null) {
                    dailyScriptIndexesByTask[entry.key] = index
                    templateOptionsByTask[entry.key] = index.templateNames
                } else {
                    templateOptionsByTask[entry.key] = emptyList()
                }
            }
    }

    private fun dailyScriptTemplateOptions(scriptFile: String, templateNames: List<String>): List<String> {
        return buildList {
            addAll(templateNames)
            when (scriptFile) {
                DAI_BAN_GONG_WU_SCRIPT -> add(DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION)
                "zhu_xian_6_24.json" -> add(MAINLINE_624_START_BATTLE_DELAY_OPTION)
            }
        }.distinct()
    }

    private fun loadTemplateDelayEntries() {
        templateDelayEntriesByTaskTemplate.clear()
        dailyScriptIndexesByTask.forEach { (taskKey, index) ->
            index.templateNames.forEach { templateName ->
                index.nodesFor(templateName).forEach { node ->
                    templateDelayEntriesByTaskTemplate
                        .getOrPut(taskTemplateKey(taskKey, templateName)) { mutableListOf() }
                        .add(TemplateDelayEntry(node.taskId, node.delayMs))
                }
            }
        }
        registerTemplateDelay(
            TASK_BIRD_FOOD_NAV,
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
            BIRD_FOOD_YUANBAO_TASK_ID,
            BIRD_FOOD_RUNTIME_TEMPLATE_DELAY_MS
        )
        registerTemplateDelay(
            TASK_BIRD_FOOD_NAV,
            BIRD_FOOD_SCREEN_TEMPLATE,
            BIRD_FOOD_SCREEN_TASK_ID,
            BIRD_FOOD_RUNTIME_TEMPLATE_DELAY_MS
        )
        registerTemplateDelay(
            DAI_BAN_GONG_WU_SCRIPT,
            DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION,
            0,
            daiBanGongWuStartBattleBaseDelayMs()
        )
        registerTemplateDelay(
            "zhu_xian_6_24.json",
            MAINLINE_624_START_BATTLE_DELAY_OPTION,
            0,
            3000L
        )
        registerBattleFlowTemplateDelayEntries()
    }

    private fun registerTemplateDelay(
        taskKey: String,
        templateName: String,
        taskId: Int,
        baseDelayMs: Long
    ) {
        templateDelayEntriesByTaskTemplate
            .getOrPut(taskTemplateKey(taskKey, templateName)) { mutableListOf() }
            .add(TemplateDelayEntry(taskId, baseDelayMs))
    }

    private fun registerBattleFlowTemplateDelayEntries() {
        fun add(templateName: String, baseDelayMs: Long = 0L) {
            templateDelayEntriesByTaskTemplate
                .getOrPut(taskTemplateKey(TASK_BATTLE_FLOW, templateName)) { mutableListOf() }
                .add(TemplateDelayEntry(0, baseDelayMs))
        }
        add(BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION, battleFlowFirstActionBaseDelayMs())
        add(ALL_WIPE_TEMPLATE)
        add(ORANGE_STAR_RECOVERY_TEMPLATE)
    }

    private fun buildBattleFlowTemplateOptions(): List<String> {
        val options = linkedSetOf(
            START_BATTLE_OCR_OPTION,
            BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION,
            BATTLE_TURN_OCR_OPTION,
            STONE_GRID_DEBUG_OPTION,
            CHARACTER_PROFICIENCY_OPTION,
            CHARACTER_FATE_OPTION,
            ORANGE_STAR_OPTION,
            PURPLE_STAR_OPTION,
            CAVE_DONGKU_OPTION,
            AUTO_SELECT_FILTER_TEMPLATE,
            CAVE_NEXT_FLOOR_TEMPLATE,
            ALL_WIPE_TEMPLATE,
            ORANGE_STAR_RECOVERY_TEMPLATE,
            STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
            STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
            STAGE_HOME_RECOVERY_BAIHU_TEMPLATE,
            STAGE_HOME_RECOVERY_DIGONG_TEMPLATE,
            STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE,
            STAGE_HOME_RECOVERY_XINZHI_TEMPLATE,
            STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE,
        )
        options += (1..DEATH_CHECK_SLOT_COUNT).map(::deathCheckOption)
        BattleStageNavigationRegistry.supportedTargets.forEach { target ->
            val config = BattleStageNavigationRegistry.getDirectConfig(target) ?: return@forEach
            options += config.entryTemplateRegion.templateName
            config.recoverySelectionRegion?.let { options += it.templateName }
        }
        return options.filter(::shouldShowMaterialOption)
    }

    private fun buildBirdFoodNavigationTemplateOptions(): List<String> =
        listOf(STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE, BIRD_FOOD_SCREEN_TEMPLATE)

    private fun buildDailyScriptDebugIndex(scriptFile: String): DailyScriptDebugIndex? {
        return runCatching {
            val plan = activity.assets
                .open("$DAILY_SCRIPT_DIR/$scriptFile")
                .use { gson.fromJson(it.readUtf8TextWithoutBom(), DailyTaskPlan::class.java) }
            DailyScriptDebugIndex.fromPlan(
                scriptFileName = scriptFile,
                scriptDisplayName = plan.display_name?.trim()?.takeIf { it.isNotBlank() }
                    ?: TASK_DISPLAY_NAME_MAP[scriptFile]
                    ?: baseNameWithoutExtension(scriptFile),
                plan = plan,
            )
        }.getOrElse { error ->
            log("Read daily script failed: $scriptFile ${error.message}")
            null
        }
    }

    private fun buildUserScriptDebugIndex(
        taskKey: String,
        displayName: String,
        bundle: UserDailyScriptBundle,
    ): DailyScriptDebugIndex? {
        return runCatching {
            val plan = UserDailyScriptStore.loadPlan(bundle, gson)
            DailyScriptDebugIndex.fromPlan(
                scriptFileName = taskKey,
                scriptDisplayName = displayName,
                plan = plan,
            )
        }.getOrElse { error ->
            log("Read user script failed: ${bundle.scriptId} ${error.message}")
            null
        }
    }

    private fun loadDailyScriptFiles(): List<String> {
        val discovered = runCatching {
            activity.assets.list(DAILY_SCRIPT_DIR).orEmpty()
                .filter { it.endsWith(".json", ignoreCase = true) && it != LEVELS_SCRIPT_FILE }
        }.getOrDefault(emptyList())
        return (DEFAULT_DAILY_TEST_SCRIPT_FILES + discovered).distinct()
    }

    private fun refreshTemplateOptions() {
        val currentTemplate = selectedTemplate
        availableTemplates = templateOptionsByTask[selectedTask].orEmpty()
        selectedTemplate = currentTemplate.takeIf { it in availableTemplates }
            ?: availableTemplates.firstOrNull()
            ?: START_BATTLE_OCR_OPTION
        delayInput = storedDelayValue().takeIf { it > 0L }?.toString().orEmpty()
        pushState()
    }

    private fun pushState() {
        val taskOptions = availableTasks.map { DebugSelectionOption(it, taskDisplayName(it)) }
        val templateOptions = availableTemplates.map { DebugSelectionOption(it, templateDisplayName(it)) }
        val delayEntries = currentTemplateDelayEntries()
        val incrementMs = storedDelayValue()
        val replacementTargets = replacementTargetsForOption(selectedTemplate)
        onStateChanged(
            DebugWorkbenchState(
                taskOptions = taskOptions,
                selectedTaskKey = selectedTask,
                selectedTaskLabel = taskDisplayName(selectedTask),
                templateOptions = templateOptions,
                selectedTemplateKey = selectedTemplate,
                selectedTemplateLabel = templateDisplayName(selectedTemplate),
                screenshotBitmap = previewBitmap ?: currentBitmap,
                screenshotTitle = uploadedImageName ?: "未上传截图",
                screenshotSubtitle = (previewBitmap ?: currentBitmap)?.let { "${it.width} x ${it.height}" }
                    ?: "点击上传截图后可继续调试",
                isLocalScopeEnabled = isLocalScopeEnabled,
                scopeHint = scopeHint(),
                delayInput = delayInput,
                delaySupported = delayEntries.isNotEmpty(),
                canClearDelay = incrementMs > 0L,
                delaySummary = buildDelaySummary(delayEntries, incrementMs),
                canReplaceTemplate = replacementTargets.isNotEmpty(),
                canRestoreTemplate = replacementTargets.isNotEmpty() && hasTemplateOverride(selectedTemplate),
                replacementDialog = activeReplacementSession?.let { session ->
                    DebugReplacementDialogState(
                        sessionId = session.id,
                        previewBitmap = session.previewBitmap,
                        title = "替换素材",
                        hint = "红框按标准1080下60x60换算，拖动选择后会保存为标准60x60素材",
                        gameScale = session.gameScale,
                        boxWidthPx = session.boxWidthPx,
                        boxHeightPx = session.boxHeightPx,
                        initialLeftPx = session.initialLeftPx,
                        initialTopPx = session.initialTopPx,
                    )
                },
                logText = logs.joinToString("\n").ifBlank { "调试日志会显示在这里。" },
            ),
        )
    }

    private fun buildDelaySummary(entries: List<TemplateDelayEntry>, incrementMs: Long): String {
        if (entries.isEmpty()) {
            return "当前任务下，这个识别项没有可调整的延时节点。"
        }
        return buildString {
            append("运行时实际延迟 = 基础 delay + 全局增加延迟 + 当前项增加延迟\n")
            append("当前项增加延迟：")
            append(incrementMs)
            append("ms\n")
            append("涉及节点：\n")
            entries.forEachIndexed { index, entry ->
                if (index > 0) append('\n')
                append("ID ")
                append(if (entry.taskId > 0) entry.taskId.toString() else "运行时")
                append(" | 基础 ")
                append(entry.baseDelayMs)
                append("ms | 本地预览 ")
                append(entry.baseDelayMs + incrementMs)
                append("ms")
            }
        }
    }

    private fun scopeHint(): String {
        return when {
            selectedTemplate == ORANGE_STAR_OPTION ->
                "使用实战相同的左上角 200x200 检测区域，输出 shape/glow/confidence"
            selectedTemplate == PURPLE_STAR_OPTION ->
                "使用实战相同的左上角 200x200 检测区域，输出 shape/glow/confidence"
            isDeathCheckOption(selectedTemplate) ->
                "使用实战相同的底部槽位 ROI，输出中心区域平均饱和度"
            selectedTemplate == CAVE_DONGKU_OPTION && isLocalScopeEnabled ->
                "局部识别会先按实战 ROI 检索 dongku，失败后再检索 dongku2"
            selectedTemplate == CAVE_DONGKU_OPTION ->
                "全屏识别会先搜索 dongku，未命中再搜索 dongku2"
            selectedTemplate == CAVE_NEXT_FLOOR_TEMPLATE && isLocalScopeEnabled ->
                "局部识别会使用 CombatEngine 实战的 bottom ROI：700,1700 / 500x500"
            selectedTemplate == CAVE_NEXT_FLOOR_TEMPLATE ->
                "全屏识别会在整张截图中检索 xiayiceng"
            selectedTemplate == START_BATTLE_OCR_OPTION && isStartBattleTemplateModeEnabled() ->
                "已启用开始战斗替换模板。新工作台会使用替换模板执行实战 ROI 匹配。"
            selectedTemplate == START_BATTLE_OCR_OPTION ->
                "未启用开始战斗替换模板时，使用与实战相同 ROI 的 OCR 检测"
            isDaiBanGongWuSweepOption(selectedTemplate) && hasTemplateOverride(selectedTemplate) ->
                "检测到待办公务扫荡替换模板后会优先走模板匹配；未替换时走扫/荡 OCR"
            isDaiBanGongWuSweepOption(selectedTemplate) ->
                "未检测到待办公务扫荡替换模板时，使用待办公务 id=6 相同 ROI 的扫/荡 OCR"
            selectedTemplate == BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION ->
                "这个选项用来调整“识别到开始战斗并点击后，第1回合第1个操作开始前”的等待时间"
            selectedTemplate == DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION ->
                "这个选项用来调整待办公务脚本里“点开始战斗前”的等待时间"
            selectedTemplate == MAINLINE_624_START_BATTLE_DELAY_OPTION ->
                "这个选项用来调整主线624脚本里“点开始战斗前”的等待时间"
            selectedTemplate == BATTLE_TURN_OCR_OPTION ->
                "使用实战相同的右上角 OCR 区域：400x300，输出 raw/normalized/hits/turn"
            selectedTemplate == STONE_GRID_DEBUG_OPTION ->
                "使用 Paddle 检测框聚类候选行并四等分，显示检测框、候选行、等级ROI、名称ROI，并输出每格识别结果"
            selectedTemplate == CHARACTER_PROFICIENCY_OPTION ->
                "使用角色练度 ROI：数值 bottom(340,1289)，星级 bottom(625,1663)，输出生命/攻击/星级"
            selectedTemplate == CHARACTER_FATE_OPTION ->
                "先识别角色名 ROI：center(662,177)，再按角色名切换命盘 3 个 center ROI，统一输出角色名和命盘结果"
            dailyScriptIndexesByTask[selectedTask] != null && isLocalScopeEnabled ->
                "局部识别会使用脚本 JSON 中的 ROI 和阈值检索 ${templateDisplayName(selectedTemplate)}"
            dailyScriptIndexesByTask[selectedTask] != null ->
                "全屏识别会使用脚本 JSON 中的阈值检索 ${templateDisplayName(selectedTemplate)}"
            isLocalScopeEnabled && shouldRunTemplateMatchLocally(selectedTemplate) && !supportsLocalTemplateScope(selectedTemplate) ->
                "这个识别项暂未登记新工作台局部 ROI，请先切回全屏识别。"
            isLocalScopeEnabled ->
                "局部识别会使用已登记的 ROI 检索 ${templateDisplayName(selectedTemplate)}"
            else ->
                "全屏识别会在整张截图中检索 ${templateDisplayName(selectedTemplate)}"
        }
    }

    private fun storedDelayValue(): Long =
        TemplateDelayOverrideStore.getIncrementMs(activity, selectedTask, selectedTemplate)

    private fun currentTemplateDelayEntries(): List<TemplateDelayEntry> =
        templateDelayEntriesByTaskTemplate[taskTemplateKey(selectedTask, selectedTemplate)].orEmpty()

    private fun replacementTargetsForOption(optionName: String): List<ReplacementTarget> {
        dailyScriptIndexesByTask[selectedTask]?.let { index ->
            val node = index.nodesFor(optionName).firstOrNull { it.replacementTemplateName != null }
                ?: return emptyList()
            val templateName = node.replacementTemplateName ?: return emptyList()
            val fileName = if (currentTaskEntry()?.isUserScript == true) {
                templateName
            } else if (node.action == "OCR" && node.templateName == null) {
                templateName
            } else {
                templateAssetKey(node, templateName)
            }
            return listOf(ReplacementTarget(fileName, templateDisplayName(optionName), node))
        }
        return when {
            optionName == START_BATTLE_OCR_OPTION -> listOf(
                ReplacementTarget(
                    fileName = TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME,
                    label = START_BATTLE_OCR_LABEL,
                ),
            )
            optionName == CAVE_DONGKU_OPTION -> listOf(
                ReplacementTarget(CAVE_DONGKU_TEMPLATE, CAVE_DONGKU_TEMPLATE),
                ReplacementTarget(CAVE_DONGKU_TEMPLATE_FALLBACK, CAVE_DONGKU_TEMPLATE_FALLBACK),
            )
            optionName == ORANGE_STAR_OPTION ||
                optionName == PURPLE_STAR_OPTION ||
                optionName == BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION ||
                optionName == DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION ||
                optionName == MAINLINE_624_START_BATTLE_DELAY_OPTION ||
                optionName == BATTLE_TURN_OCR_OPTION ||
                optionName == CHARACTER_NAME_OPTION ||
                optionName == CHARACTER_PROFICIENCY_OPTION ||
                optionName == CHARACTER_FATE_OPTION ||
                isDeathCheckOption(optionName) -> emptyList()
            else -> listOf(ReplacementTarget(optionName, optionName))
        }
    }

    private fun hasTemplateOverride(templateName: String): Boolean {
        if (currentTaskEntry()?.isUserScript == true) {
            val node = dailyScriptIndexesByTask[selectedTask]
                ?.nodesFor(templateName)
                ?.firstOrNull { it.replacementTemplateName != null }
            if (node?.action != "OCR") return false
        }
        return if (templateName == START_BATTLE_OCR_OPTION) {
            TemplateOverrideStore.hasOverride(activity, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)
        } else {
            replacementTargetsForOption(templateName).any { TemplateOverrideStore.hasOverride(activity, it.fileName) }
        }
    }

    private fun templateAssetKey(node: DailyScriptDebugNode?, templateName: String): String {
        val dir = node?.assetTemplateDir?.trim()?.takeIf { it.isNotBlank() }
        return if (dir == null || templateName.contains('/') || templateName.contains('\\')) {
            templateName
        } else {
            "$dir/$templateName"
        }
    }

    private fun taskDisplayName(taskKey: String): String =
        taskEntriesByKey[taskKey]?.displayName ?: TASK_DISPLAY_NAME_MAP[taskKey] ?: taskKey

    private fun templateDisplayName(templateName: String): String =
        when {
            templateName == START_BATTLE_OCR_OPTION -> START_BATTLE_OCR_LABEL
            templateName == BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION -> "第1回合第1个操作延时"
            templateName == DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION -> "待办公务开始战斗前延时"
            templateName == MAINLINE_624_START_BATTLE_DELAY_OPTION -> "主线624开始战斗前延时"
            templateName == BATTLE_TURN_OCR_OPTION -> BATTLE_TURN_OCR_LABEL
            templateName == STONE_GRID_DEBUG_OPTION -> STONE_GRID_DEBUG_LABEL
            templateName == CHARACTER_NAME_OPTION -> CHARACTER_FATE_LABEL
            templateName == CHARACTER_PROFICIENCY_OPTION -> CHARACTER_PROFICIENCY_LABEL
            templateName == CHARACTER_FATE_OPTION -> CHARACTER_FATE_LABEL
            templateName == ORANGE_STAR_OPTION -> ORANGE_STAR_LABEL
            templateName == PURPLE_STAR_OPTION -> PURPLE_STAR_LABEL
            templateName == CAVE_DONGKU_OPTION -> CAVE_DONGKU_LABEL
            templateName == CAVE_NEXT_FLOOR_TEMPLATE -> CAVE_NEXT_FLOOR_LABEL
            isDeathCheckOption(templateName) -> "阵亡检测（${deathCheckSlotFromOption(templateName)}号位）"
            dailyScriptIndexesByTask[selectedTask] != null ->
                dailyScriptIndexesByTask[selectedTask]?.displayNameFor(templateName) ?: templateName
            else -> DailyScriptTemplateNames.displayNameFor(selectedTask, templateName) ?: templateName
        }

    private fun currentTaskEntry(): TestTaskEntry? = taskEntriesByKey[selectedTask]

    private fun isDaiBanGongWuSweepOption(optionName: String): Boolean =
        optionName == DAI_BAN_GONG_WU_SWEEP_TEMPLATE

    private fun deathCheckOption(slotIndex: Int): String = "$DEATH_CHECK_OPTION_PREFIX$slotIndex"

    private fun isDeathCheckOption(optionName: String): Boolean =
        optionName.startsWith(DEATH_CHECK_OPTION_PREFIX)

    private fun deathCheckSlotFromOption(optionName: String): Int? =
        optionName.removePrefix(DEATH_CHECK_OPTION_PREFIX).toIntOrNull()

    private fun shouldShowMaterialOption(optionName: String): Boolean =
        when {
            optionName == START_BATTLE_OCR_OPTION ||
                optionName == BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION ||
                optionName == DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION ||
                optionName == MAINLINE_624_START_BATTLE_DELAY_OPTION ||
                optionName == BATTLE_TURN_OCR_OPTION ||
                optionName == STONE_GRID_DEBUG_OPTION ||
                optionName == CHARACTER_NAME_OPTION ||
                optionName == CHARACTER_PROFICIENCY_OPTION ||
                optionName == CHARACTER_FATE_OPTION ||
                optionName == ORANGE_STAR_OPTION ||
                optionName == PURPLE_STAR_OPTION ||
                optionName == CAVE_DONGKU_OPTION ||
                isDeathCheckOption(optionName) -> true
            else -> shouldShowInTemplateOptions(optionName)
        }

    private fun shouldShowInTemplateOptions(fileName: String): Boolean {
        return isImageTemplateFile(fileName) &&
            fileName != HIDDEN_TEMPLATE_OPTIONS &&
            !fileName.any(::isChineseCharacter)
    }

    private fun isImageTemplateFile(fileName: String): Boolean =
        fileName.endsWith(".png", true) || fileName.endsWith(".jpg", true)

    private fun isChineseCharacter(char: Char): Boolean =
        Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

    private fun displayNameForUri(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return runCatching {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun userTaskKey(scriptId: String): String = "user:$scriptId"

    private fun baseNameWithoutExtension(fileName: String): String =
        fileName.substringAfterLast('/').substringBeforeLast('.', fileName.substringAfterLast('/'))

    private fun taskTemplateKey(taskKey: String, templateName: String): String = "$taskKey::$templateName"

    private fun battleFlowFirstActionBaseDelayMs(): Long {
        val delayValues = BattleStageNavigationRegistry.supportedTargets
            .mapNotNull { BattleStageNavigationRegistry.getDirectConfig(it)?.delayAfterStartBattleClickMs }
            .distinct()
        return delayValues.firstOrNull() ?: 4000L
    }

    private fun daiBanGongWuStartBattleBaseDelayMs(): Long =
        loadDailyScriptTaskDelay(
            scriptFileName = DAI_BAN_GONG_WU_SCRIPT,
            taskId = 6,
            expectedAction = "OCR",
        ) ?: 0L

    private fun loadDailyScriptTaskDelay(
        scriptFileName: String,
        taskId: Int,
        expectedAction: String? = null,
    ): Long? {
        return try {
            activity.assets.open("$DAILY_SCRIPT_DIR/$scriptFileName").use { input ->
                val plan = gson.fromJson(input.readUtf8TextWithoutBom(), DailyTaskPlan::class.java)
                plan.tasks.firstOrNull { task ->
                    task.id == taskId && (expectedAction == null || task.action == expectedAction)
                }?.delay
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun InputStream.readUtf8TextWithoutBom(): String =
        readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")

    private fun openSiblingJsonInputStream(imageUri: Uri, jsonName: String): InputStream? {
        if (imageUri.scheme == "file") {
            val imageFile = File(imageUri.path ?: return null)
            val jsonFile = File(imageFile.parentFile ?: return null, jsonName)
            return if (jsonFile.exists()) jsonFile.inputStream() else null
        }
        return runCatching {
            if (!DocumentsContract.isDocumentUri(activity, imageUri)) return@runCatching null
            val documentId = DocumentsContract.getDocumentId(imageUri)
            val slashIndex = documentId.lastIndexOf('/')
            if (slashIndex < 0) return@runCatching null
            val siblingId = documentId.substring(0, slashIndex + 1) + jsonName
            val siblingUri = DocumentsContract.buildDocumentUri(imageUri.authority, siblingId)
            activity.contentResolver.openInputStream(siblingUri)
        }.getOrNull()
    }

    private fun isStartBattleTemplateModeEnabled(): Boolean =
        TemplateOverrideStore.hasOverride(activity, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)

    private fun buildTopRightRect(
        screenshot: Bitmap,
        widthBase: Float,
        heightBase: Float,
    ): Rect? {
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val gameWidth = BASE_W * gameScale
        val offsetX = (screenshot.width - gameWidth) / 2f
        val gameRight = offsetX + gameWidth
        val width = (widthBase * gameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (heightBase * gameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        if (width <= 0 || height <= 0) return null
        val right = gameRight.toInt().coerceIn(1, screenshot.width)
        val left = (right - width).coerceAtLeast(0)
        return Rect(left, 0, right, height)
    }

    private fun buildFixedRectFromVisionRegion(
        screenshot: Bitmap,
        x: Float,
        y: Float,
        align: String,
        w: Float,
        h: Float,
    ): Rect? {
        val mapping = buildDisplayMapping(screenshot)
        val (realCenterX, realCenterY) = calculateRealCoordinate(x, y, align, mapping)
        val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
        val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
        val gameScale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val realW = w * gameScale
        val realH = h * gameScale
        val left = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val width = realW.toInt().coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val height = realH.toInt().coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        return Rect(left, top, left + width, top + height)
    }

    private fun buildDeathCheckRect(screenshot: Bitmap, slotIndex: Int): Rect? {
        if (slotIndex !in 1..DEATH_CHECK_SLOT_COUNT) return null
        val slotWidth = BASE_W / DEATH_CHECK_SLOT_COUNT.toFloat()
        return buildFixedRectFromVisionRegion(
            screenshot = screenshot,
            x = slotWidth * (slotIndex - 0.5f),
            y = (DEATH_CHECK_TOP_Y + DEATH_CHECK_BOTTOM_Y) / 2f,
            align = "bottom",
            w = slotWidth,
            h = DEATH_CHECK_BOTTOM_Y - DEATH_CHECK_TOP_Y,
        )
    }

    private fun buildDisplayMapping(screenshot: Bitmap): DisplayMapping {
        val (screenWidth, screenHeight) = getRealScreenSize()
        return DisplayMapping(
            displayWidth = screenWidth,
            displayHeight = screenHeight,
            displayToScreenshotX = screenshot.width / screenWidth,
            displayToScreenshotY = screenshot.height / screenHeight,
        )
    }

    private fun calculateRealCoordinate(
        baseX: Float,
        baseY: Float,
        align: String,
        mapping: DisplayMapping,
    ): Pair<Float, Float> {
        val gameScale = min(mapping.displayWidth / BASE_W, mapping.displayHeight / BASE_H)
        val offsetX = (mapping.displayWidth - BASE_W * gameScale) / 2f
        val offsetY = (mapping.displayHeight - BASE_H * gameScale) / 2f
        val statusBarHeight = getStatusBarHeightCompat() / 2f
        val realX = offsetX + (baseX * gameScale)
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (mapping.displayHeight / BASE_H)
            "dynamic_filter_bounds" -> baseY * gameScale
            "top" -> statusBarHeight + (baseY * gameScale)
            "bottom" -> mapping.displayHeight - ((BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }
        return realX to realY
    }

    private fun getStatusBarHeightCompat(): Float {
        val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) activity.resources.getDimensionPixelSize(id).toFloat() else 40f
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.maximumWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getRealSize(point)
            point.x.toFloat() to point.y.toFloat()
        }
    }

    private fun recognizeChineseText(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        ocrScope.launch {
            try {
                val text = PaddleTextRecognizer.recognize(activity, bitmap).text
                withContext(Dispatchers.Main) {
                    onSuccess(text)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(error)
                }
            }
        }
    }

    private fun recognizeCharacterNameText(
        nameBitmap: Bitmap,
        onSuccess: (PaddleTextResult) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        ocrScope.launch {
            try {
                val nameResult = PaddleTextRecognizer.recognize(activity, nameBitmap)
                withContext(Dispatchers.Main) {
                    onSuccess(nameResult)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(error)
                }
            }
        }
    }

    private fun recognizeCharacterValueText(
        valueBitmap: Bitmap,
        onSuccess: (PaddleTextResult) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        ocrScope.launch {
            try {
                val valueResult = PaddleTextRecognizer.recognize(activity, valueBitmap)
                withContext(Dispatchers.Main) {
                    onSuccess(valueResult)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(error)
                }
            }
        }
    }

    private fun recognizeCharacterFateText(
        bitmaps: List<Bitmap>,
        onSuccess: (List<PaddleTextResult>) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        ocrScope.launch {
            try {
                val results = bitmaps.map { bitmap ->
                    PaddleTextRecognizer.recognize(activity, bitmap)
                }
                withContext(Dispatchers.Main) {
                    onSuccess(results)
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(error)
                }
            }
        }
    }

    private fun createConfiguredOcrBitmap(source: Bitmap, preprocess: String?): Bitmap? =
        when (preprocess?.lowercase()) {
            "light_text" -> createLightTextOcrBitmap(source)
            "yellow_text" -> createYellowTextOcrBitmap(source)
            else -> null
        }

    private fun createLightTextOcrBitmap(source: Bitmap): Bitmap? {
        return runCatching {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val isLightWarmText = r >= 145 && g >= 120 && b >= 90 && r >= b - 10 && g >= b - 35
                output[i] = if (isLightWarmText) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun createYellowTextOcrBitmap(source: Bitmap): Bitmap? {
        return runCatching {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val isYellowText = r >= 150 && g >= 110 && b <= 180 && (r - b) >= 40 && (g - b) >= 30
                output[i] = if (isYellowText) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun logOcrNodeResult(
        rawText: String,
        node: DailyScriptDebugNode,
    ) {
        val normalizedText = rawText.filterNot { it.isWhitespace() }
        val chars = node.ocrTargetChars.mapNotNull { it.trim().firstOrNull() }.distinct()
        log("OCR raw=${formatOcrLog(rawText)}")
        log("OCR normalized=${formatOcrLog(normalizedText)}")
        if (chars.isNotEmpty()) {
            val hitChars = chars.filter { normalizedText.contains(it) }
            val requiredCount = (node.ocrMinHitCount ?: chars.size).coerceAtLeast(1)
            log("OCR target=${chars.joinToString("")} minHit=$requiredCount")
            log("OCR hits=${if (hitChars.isEmpty()) "无" else hitChars.joinToString("")} count=${hitChars.size} hit=${hitChars.size >= requiredCount}")
            return
        }
        val targetText = (node.targetText ?: node.buttonName).orEmpty()
        val normalizedTarget = targetText.filterNot { it.isWhitespace() }
        val hit = normalizedTarget.isNotEmpty() && normalizedText.contains(normalizedTarget)
        log("OCR target=${formatOcrLog(targetText)} hit=$hit")
    }

    private fun logStartBattleOcrResult(prefix: String, rawText: String) {
        val normalizedText = rawText.filterNot { it.isWhitespace() }
        val hitChars = listOf('开', '始', '战', '斗').filter { normalizedText.contains(it) }
        val containsPhrase = normalizedText.contains("开始战斗")
        log("$prefix raw=${formatOcrLog(rawText)}")
        log("$prefix normalized=${formatOcrLog(normalizedText)}")
        log("$prefix hits=${if (hitChars.isEmpty()) "无" else hitChars.joinToString("")} count=${hitChars.size}")
        log("$prefix phrase=${if (containsPhrase) "开始战斗" else "未完整命中"}")
    }

    private fun logCharacterNameResult(nameResult: PaddleTextResult) {
        val nameText = nameResult.text
        val names = parseCharacterNames(nameText)
        log("Name det lines=${nameResult.detectedLineCount}")
        log("Name OCR raw=${formatOcrLog(nameText)}")
        log("角色名：${names.firstOrNull().orEmpty()}")
    }

    private fun logCharacterProficiencyResult(
        valueResult: PaddleTextResult,
        starResult: CharacterStarDetectionResult,
    ) {
        val valueText = valueResult.text
        val numbers = parseCharacterNumbers(valueText)
        val rows = buildCharacterProficiencyRows(numbers)
        log("Value det lines=${valueResult.detectedLineCount}")
        log("Value OCR raw=${formatOcrLog(valueText)}")
        log("Value numbers=${numbers.joinToString(", ").ifBlank { "无" }}")
        log("Star count=${starResult.starCount}")
        starResult.candidates.forEachIndexed { index, candidate ->
            log(
                "Star ${index + 1}: center=(${candidate.center.x.toInt()},${candidate.center.y.toInt()}) " +
                    "size=${candidate.bounds.width()}x${candidate.bounds.height()} " +
                    "pixels=${candidate.pixelCount} " +
                    "colorRatio=${"%.3f".format(candidate.colorRatio)} " +
                    "shape=${"%.3f".format(candidate.shapeScore)}"
            )
        }
        log("角色练度：生命=${rows.hp} 攻击=${rows.attack} 星级=${starResult.starCount}")
    }

    private fun logCharacterFateResult(results: List<PaddleTextResult>) {
        results.forEachIndexed { index, result ->
            val correction = correctFateText(result.text)
            log("Fate ${index + 1} det lines=${result.detectedLineCount}")
            log("Fate ${index + 1} OCR raw=${formatOcrLog(result.text)}")
            log(
                "Fate ${index + 1} corrected=" +
                    "${formatOcrLog(correction?.displayText.orEmpty())} " +
                    "score=${correction?.score?.let { "%.3f".format(it) } ?: "无"}"
            )
        }
    }

    private fun resolveCharacterFatePoints(roleName: String): List<Pair<Float, Float>> {
        return CHARACTER_FATE_SPECIAL_POINTS[roleName] ?: CHARACTER_FATE_POINTS
    }

    private fun correctFateText(rawText: String): FateCorrection? {
        val normalizedRaw = normalizeFateText(rawText)
        if (normalizedRaw.isBlank()) return null
        val ranked = fateCorrectionCandidates
            .asSequence()
            .mapNotNull { candidate ->
                val normalizedCandidate = normalizeFateText(candidate)
                if (normalizedCandidate.isBlank()) return@mapNotNull null
                val score = fateTextSimilarity(normalizedRaw, normalizedCandidate)
                FateCorrection(displayText = candidate, score = score)
            }
            .filter { it.score >= 0.55f }
            .sortedByDescending { it.score }
            .take(2)
            .toList()
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        if (best.score >= 0.999f || second == null || best.score - second.score >= 0.15f) {
            return best
        }
        return null
    }

    private fun correctAgentName(rawName: String): NameCorrection? {
        val normalizedRaw = normalizeNameText(rawName)
        if (normalizedRaw.isBlank()) return null
        val ranked = agentNames
            .map { candidate ->
                NameCorrection(candidate, agentNameSimilarity(normalizedRaw, normalizeNameText(candidate)))
            }
            .filter { it.score >= 0.55f }
            .sortedByDescending { it.score }
            .take(2)
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return if (best.score >= 0.999f || second == null || best.score - second.score >= 0.12f) best else null
    }

    private fun findExactAgentName(rawName: String): String? {
        val normalizedRaw = normalizeNameText(rawName)
        if (normalizedRaw.isBlank()) return null
        return agentNames.firstOrNull { normalizeNameText(it) == normalizedRaw }
    }

    private fun normalizeFateText(text: String): String {
        return text
            .replace("橙", "")
            .replace("紫", "")
            .filter { char ->
                Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN ||
                    char.isLetterOrDigit()
            }
    }

    private fun normalizeNameText(text: String): String {
        return cleanDetectedAgentName(text).filter { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN ||
                char.isLetterOrDigit()
        }
    }

    private fun cleanDetectedAgentName(text: String): String =
        text.replace("命", "").replace("盘", "").trim()

    private fun fateTextSimilarity(left: String, right: String): Float {
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 2 && hasAnySameCharacter(left, right) && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.65f
        }
        if (maxLength == 3 && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.67f
        }
        return textSimilarity(left, right)
    }

    private fun agentNameSimilarity(left: String, right: String): Float {
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 2 && hasAnySameCharacter(left, right) && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.65f
        }
        if (maxLength == 3 && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.72f
        }
        return textSimilarity(left, right)
    }

    private fun hasAnySameCharacter(left: String, right: String): Boolean =
        left.any { char -> right.contains(char) }

    private fun textSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 0f
        val distance = levenshteinDistance(left, right)
        return (1f - distance.toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private data class CharacterProficiencyRow(
        val hp: String,
        val attack: String,
    )

    private fun parseCharacterNames(rawText: String): List<String> {
        return rawText
            .lineSequence()
            .map { line ->
                cleanDetectedAgentName(
                    line
                        .replace(Regex("[^\\p{IsHan}A-Za-z0-9·・（）()\\-]"), "")
                        .trim(),
                )
            }
            .filter { it.isNotBlank() }
            .map { name -> findExactAgentName(name) ?: correctAgentName(name)?.displayText ?: name }
            .take(1)
            .toList()
    }

    private fun parseCharacterNumbers(rawText: String): List<String> {
        return Regex("\\d[\\d,，.]*")
            .findAll(rawText)
            .map { match -> match.value.filter(Char::isDigit) }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun buildCharacterProficiencyRows(numbers: List<String>): CharacterProficiencyRow {
        return CharacterProficiencyRow(
            hp = numbers.getOrNull(0).orEmpty(),
            attack = numbers.getOrNull(1).orEmpty(),
        )
    }

    private fun computeCenterMeanSaturation(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return 255f

        val left = (width * 0.2f).toInt().coerceIn(0, width - 1)
        val top = (height * 0.2f).toInt().coerceIn(0, height - 1)
        val right = (width * 0.8f).toInt().coerceIn(left + 1, width)
        val bottom = (height * 0.8f).toInt().coerceIn(top + 1, height)
        val step = if ((right - left) * (bottom - top) >= 40000) 2 else 1
        val hsv = FloatArray(3)
        var sum = 0f
        var count = 0

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                sum += hsv[1] * 255f
                count += 1
            }
        }

        return if (count == 0) 255f else sum / count.toFloat()
    }


    private fun findStarCandidateInScreenshot(bitmap: Bitmap, mode: StarDetectionMode): StarCandidate? {
        val roiRect = buildFixedRectFromVisionRegion(
            screenshot = bitmap,
            x = ORANGE_STAR_CENTER_X,
            y = ORANGE_STAR_CENTER_Y,
            align = "top",
            w = ORANGE_STAR_ROI_SIZE,
            h = ORANGE_STAR_ROI_SIZE,
        ) ?: return null
        val roiBitmap = Bitmap.createBitmap(bitmap, roiRect.left, roiRect.top, roiRect.width(), roiRect.height())
        return try {
            findStarInRegion(roiBitmap, mode)?.let { candidate ->
                candidate.copy(center = PointF(candidate.center.x + roiRect.left, candidate.center.y + roiRect.top))
            }
        } finally {
            roiBitmap.recycle()
        }
    }

    private fun findStarInRegion(bitmap: Bitmap, mode: StarDetectionMode): StarCandidate? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val pixelCount = width * height
        val coreScores = FloatArray(pixelCount)
        val glowScores = FloatArray(pixelCount)
        val shapeSignalScores = FloatArray(pixelCount)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = bitmap.getPixel(x, y)
                val coreScore = computeStarCoreScore(color)
                val glowScore = computeStarGlowScore(color, mode)
                val highlightScore = computeStarHighlightScore(color)
                coreScores[index] = coreScore
                glowScores[index] = glowScore
                shapeSignalScores[index] = maxOf(coreScore, highlightScore * 0.85f)
            }
        }
        val centers = buildStarCenterCandidates(width, height, coreScores, shapeSignalScores)
        var best: StarCandidate? = null
        for (center in centers) {
            val candidate = evaluateStarCandidate(
                width = width,
                height = height,
                coreScores = coreScores,
                glowScores = glowScores,
                signalScores = shapeSignalScores,
                centerX = center.first,
                centerY = center.second,
            ) ?: continue
            val currentBest = best
            val shouldReplace = currentBest == null ||
                candidate.shapeScore > currentBest.shapeScore ||
                (abs(candidate.shapeScore - currentBest.shapeScore) < 0.0001f && candidate.confidence > currentBest.confidence)
            if (shouldReplace) best = candidate
        }
        return best
    }

    private fun buildStarCenterCandidates(width: Int, height: Int, coreScores: FloatArray, signalScores: FloatArray): List<Pair<Int, Int>> {
        data class Candidate(val x: Int, val y: Int, val score: Float)
        val rawCandidates = ArrayList<Candidate>()
        for (y in 4 until height - 4 step 2) {
            for (x in 4 until width - 4 step 2) {
                val core = sampleAverage(coreScores, width, height, x, y, 2)
                val signal = sampleAverage(signalScores, width, height, x, y, 4)
                val score = core * 0.72f + signal * 0.28f
                if (score >= 0.42f) rawCandidates.add(Candidate(x, y, score))
            }
        }
        if (rawCandidates.isEmpty()) return emptyList()
        rawCandidates.sortByDescending { it.score }
        val selected = ArrayList<Pair<Int, Int>>(16)
        for (candidate in rawCandidates) {
            val duplicate = selected.any { (sx, sy) ->
                val dx = sx - candidate.x
                val dy = sy - candidate.y
                dx * dx + dy * dy <= 36
            }
            if (duplicate) continue
            selected.add(candidate.x to candidate.y)
            if (selected.size >= 16) break
        }
        return selected
    }

    private fun evaluateStarCandidate(
        width: Int,
        height: Int,
        coreScores: FloatArray,
        glowScores: FloatArray,
        signalScores: FloatArray,
        centerX: Int,
        centerY: Int,
    ): StarCandidate? {
        val maxRadius = minOf(centerX, centerY, width - 1 - centerX, height - 1 - centerY, 42)
        if (maxRadius < 10) return null
        val coreScore = sampleAverage(coreScores, width, height, centerX, centerY, 3)
        if (coreScore < 0.45f) return null
        var bestShapeScore = 0f
        var bestContinuity = 0f
        var bestHalfSpanX = 0
        var bestHalfSpanY = 0
        for (degree in 0 until 90 step 6) {
            val theta = Math.toRadians(degree.toDouble())
            val armAngles = doubleArrayOf(theta, theta + Math.PI / 2.0, theta + Math.PI, theta + Math.PI * 1.5)
            val gapAngles = doubleArrayOf(theta + Math.PI / 4.0, theta + Math.PI * 3.0 / 4.0, theta + Math.PI * 5.0 / 4.0, theta + Math.PI * 7.0 / 4.0)
            val armMeans = FloatArray(4)
            val armContinuities = FloatArray(4)
            val armSpans = IntArray(4)
            for (i in armAngles.indices) {
                var sum = 0f
                var count = 0
                var active = 0
                var span = 0
                for (radius in 4..maxRadius step 2) {
                    val sample = sampleDirectional(signalScores, width, height, centerX, centerY, armAngles[i], radius)
                    sum += sample
                    count += 1
                    if (sample >= 0.22f) {
                        active += 1
                        span = radius
                    }
                }
                armMeans[i] = if (count == 0) 0f else sum / count.toFloat()
                armContinuities[i] = if (count == 0) 0f else active.toFloat() / count.toFloat()
                armSpans[i] = span
            }
            var gapMeanSum = 0f
            var gapCount = 0
            for (angle in gapAngles) {
                for (radius in 5..maxRadius step 2) {
                    gapMeanSum += sampleDirectional(signalScores, width, height, centerX, centerY, angle, radius)
                    gapCount += 1
                }
            }
            val gapMean = if (gapCount == 0) 0f else gapMeanSum / gapCount.toFloat()
            val armMean = armMeans.average().toFloat()
            val armContinuity = armContinuities.average().toFloat()
            val balanceScore = (minOf(armMeans[0], armMeans[2]) / maxOf(armMeans[0], armMeans[2], 0.0001f) + minOf(armMeans[1], armMeans[3]) / maxOf(armMeans[1], armMeans[3], 0.0001f)) / 2f
            val contrastScore = ((armMean - gapMean) / maxOf(armMean, 0.0001f)).coerceIn(0f, 1f)
            val spanScore = armSpans.average().toFloat() / maxRadius.toFloat()
            val shapeScore = (contrastScore * 0.42f + armContinuity.coerceIn(0f, 1f) * 0.28f + balanceScore.coerceIn(0f, 1f) * 0.18f + spanScore.coerceIn(0f, 1f) * 0.12f).coerceIn(0f, 1f)
            if (shapeScore > bestShapeScore) {
                bestShapeScore = shapeScore
                bestContinuity = armContinuity
                bestHalfSpanX = maxOf(armSpans[0], armSpans[2]).coerceAtLeast(4)
                bestHalfSpanY = maxOf(armSpans[1], armSpans[3]).coerceAtLeast(4)
            }
        }
        if (bestShapeScore < 0.18f) return null
        val glowScore = sampleAnnulusAverage(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 24))
        val glowRatio = sampleAnnulusRatio(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 26), 0.42f)
        val sizeScore = (maxOf(bestHalfSpanX, bestHalfSpanY).toFloat() / maxRadius.toFloat()).coerceIn(0f, 1f)
        val confidence = (coreScore * 0.26f + bestShapeScore * 0.39f + glowScore * 0.20f + glowRatio.coerceIn(0f, 1f) * 0.10f + sizeScore * 0.05f).coerceIn(0f, 1f)
        return StarCandidate(PointF(centerX.toFloat(), centerY.toFloat()), confidence, glowScore, glowRatio, bestShapeScore, bestContinuity.coerceIn(0f, 1f), bestHalfSpanX * 2, bestHalfSpanY * 2)
    }

    private fun sampleDirectional(scores: FloatArray, width: Int, height: Int, centerX: Int, centerY: Int, angle: Double, radius: Int): Float {
        val x = centerX + Math.cos(angle).toFloat() * radius
        val y = centerY + Math.sin(angle).toFloat() * radius
        return sampleNearest(scores, width, height, x, y)
    }

    private fun sampleAverage(scores: FloatArray, width: Int, height: Int, centerX: Int, centerY: Int, radius: Int): Float {
        var sum = 0f
        var count = 0
        for (y in centerY - radius..centerY + radius) {
            for (x in centerX - radius..centerX + radius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy > radius * radius) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusAverage(scores: FloatArray, width: Int, height: Int, centerX: Int, centerY: Int, innerRadius: Int, outerRadius: Int): Float {
        var sum = 0f
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusRatio(scores: FloatArray, width: Int, height: Int, centerX: Int, centerY: Int, innerRadius: Int, outerRadius: Int, threshold: Float): Float {
        var hit = 0
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                count += 1
                if (scores[y * width + x] >= threshold) hit += 1
            }
        }
        return if (count == 0) 0f else hit.toFloat() / count.toFloat()
    }

    private fun sampleNearest(scores: FloatArray, width: Int, height: Int, x: Float, y: Float): Float {
        val safeX = x.toInt().coerceIn(0, width - 1)
        val safeY = y.toInt().coerceIn(0, height - 1)
        return scores[safeY * width + safeX]
    }

    private fun computeStarCoreScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.72f) return 0f
        val brightnessScore = ((brightness - 0.72f) / 0.28f).coerceIn(0f, 1f)
        val whitenessScore = (1f - saturation).coerceIn(0f, 1f)
        val warmScore = when {
            saturation <= 0.12f -> 0.90f
            hue in 20f..68f -> 1f
            hue in 0f..20f -> 0.70f
            hue in 68f..90f -> 0.60f
            else -> 0f
        }
        if (warmScore <= 0f) return 0f
        return (brightnessScore * 0.45f + whitenessScore * 0.35f + warmScore * 0.20f).coerceIn(0f, 1f)
    }

    private fun computeOrangePixelScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (hue < 20f || hue > 62f) return 0f
        if (saturation < 0.35f || brightness < 0.45f) return 0f
        val hueScore = (1f - abs(hue - 41f) / 21f).coerceIn(0f, 1f)
        val saturationScore = ((saturation - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val brightnessScore = ((brightness - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return (hueScore * 0.50f + saturationScore * 0.25f + brightnessScore * 0.25f).coerceIn(0f, 1f)
    }

    private fun computePurplePixelScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (hue < 260f || hue > 330f) return 0f
        if (saturation < 0.20f || brightness < 0.45f) return 0f
        val hueScore = (1f - abs(hue - 292f) / 38f).coerceIn(0f, 1f)
        val saturationScore = ((saturation - 0.20f) / 0.80f).coerceIn(0f, 1f)
        val brightnessScore = ((brightness - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return (hueScore * 0.50f + saturationScore * 0.25f + brightnessScore * 0.25f).coerceIn(0f, 1f)
    }

    private fun computeStarGlowScore(color: Int, mode: StarDetectionMode): Float =
        when (mode) {
            StarDetectionMode.ORANGE -> computeOrangePixelScore(color)
            StarDetectionMode.PURPLE -> computePurplePixelScore(color)
        }

    private fun computeStarHighlightScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.58f) return 0f
        val brightnessScore = ((brightness - 0.58f) / 0.42f).coerceIn(0f, 1f)
        val hueScore = when {
            saturation <= 0.18f -> 0.90f
            hue in 15f..80f -> 1f
            hue in 0f..15f -> 0.70f
            hue in 80f..100f -> 0.55f
            else -> 0f
        }
        if (hueScore <= 0f) return 0f
        val saturationScore = when {
            saturation <= 0.18f -> 0.75f
            else -> ((saturation - 0.18f) / 0.82f).coerceIn(0.45f, 1f)
        }
        return (brightnessScore * 0.50f + hueScore * 0.30f + saturationScore * 0.20f).coerceIn(0f, 1f)
    }


    private fun parseBattleTurnNumber(normalizedText: String): Int? {
        val match = Regex("回合(\\d+)(?:/(\\d+))?").find(normalizedText) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun formatOcrLog(text: String): String {
        if (text.isEmpty()) return "\"\""
        return "\"" + text.replace("\n", "\\n") + "\""
    }

    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun log(message: String) {
        logs += message
    }
}
