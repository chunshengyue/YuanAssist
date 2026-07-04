package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.DailyBirdFoodBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.BirdFoodStopCondition
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.model.DaiBanGongWuEntry
import com.example.yuanassist.model.DaiBanGongWuOption
import com.example.yuanassist.model.TaDeChuanWenOption
import com.example.yuanassist.ui.subpage.SubpageCheckOption
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.SubpageFieldGroup
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField

private const val PREFS_APP = "app_prefs"
private const val KEY_TASK_TUFA = "daily_bird_food_task_tufa"
private const val KEY_TASK_XIAODAO = "daily_bird_food_task_xiaodao"
private const val KEY_TASK_CHUANWEN = "daily_bird_food_task_chuanwen"
private const val KEY_TASK_GONGWU = "daily_bird_food_task_gongwu"
private const val KEY_DEBUG_MODE = "daily_bird_food_debug_mode"
private const val KEY_SAVE_SCREENSHOT = "daily_bird_food_save_screenshot"
private const val KEY_CHUANWEN_OPTION = "daily_bird_food_chuanwen_option"
private const val KEY_GONGWU_OPTION = "daily_bird_food_gongwu_option"
private const val KEY_GONGWU_SKIP_ENTRIES = "daily_bird_food_gongwu_skip_entries"
private const val KEY_STOP_MODE = "daily_bird_food_stop_mode"
private const val KEY_RUN_COUNT = "daily_bird_food_run_count"
private const val KEY_DURATION = "daily_bird_food_duration"
private const val KEY_LOW_SPEC_DELAY = "daily_bird_food_low_spec_delay"
private const val MODE_AUTO_EAT = "auto_eat"
private const val MODE_RESOURCE = "resource"
private const val MODE_RUN_COUNT = "run_count"
private const val MODE_DURATION = "duration"
private const val OPTION_RUYUAN = "ruyuan"
private const val OPTION_DAIHAOYUAN = "daihaoyuan"
private const val OPTION_BINGSHU = "bingshu"
private const val OPTION_WUZHUQIAN = "wuzhuqian"

class DailyBirdFoodFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val initialState = loadState()
        return ComposeView(requireContext()).apply {
            setContent {
                DailyBirdFoodScreen(
                    initialState = initialState,
                    onBack = ::goBack,
                    onConfirm = { state ->
                        val config = buildConfig(state) ?: return@DailyBirdFoodScreen
                        saveState(state)
                        DailyBirdFoodBridge.pendingConfig = config
                        startBirdFoodService()
                    },
                )
            }
        }
    }

    private fun goBack() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            activity?.finish()
        }
    }

    private fun loadState(): BirdFoodUiState {
        val prefs = requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val selectedTask = if (prefs.contains(KEY_TASK_TUFA)) {
            when {
                prefs.getBoolean(KEY_TASK_TUFA, false) -> BirdFoodTaskType.TU_FA_QING_KUANG
                prefs.getBoolean(KEY_TASK_XIAODAO, false) -> BirdFoodTaskType.XIAO_DAO_XIAO_XI
                prefs.getBoolean(KEY_TASK_CHUANWEN, false) -> BirdFoodTaskType.TA_DE_CHUAN_WEN
                prefs.getBoolean(KEY_TASK_GONGWU, false) -> BirdFoodTaskType.DAI_BAN_GONG_WU
                else -> BirdFoodTaskType.TU_FA_QING_KUANG
            }
        } else {
            BirdFoodTaskType.TU_FA_QING_KUANG
        }
        val skippedEntries = prefs.getStringSet(KEY_GONGWU_SKIP_ENTRIES, emptySet()).orEmpty()

        return BirdFoodUiState(
            selectedTask = selectedTask,
            debugModeEnabled = prefs.getBoolean(KEY_DEBUG_MODE, false),
            saveDebugScreenshotsEnabled = prefs.getBoolean(KEY_SAVE_SCREENSHOT, false),
            taDeChuanWenOption = if (prefs.getString(KEY_CHUANWEN_OPTION, OPTION_RUYUAN) == OPTION_DAIHAOYUAN) {
                TaDeChuanWenOption.DAIHAOYUAN
            } else {
                TaDeChuanWenOption.RUYUAN
            },
            daiBanGongWuOption = if (prefs.getString(KEY_GONGWU_OPTION, OPTION_BINGSHU) == OPTION_WUZHUQIAN) {
                DaiBanGongWuOption.WU_ZHU_QIAN
            } else {
                DaiBanGongWuOption.BING_SHU
            },
            skippedDaiBanGongWuEntries = DaiBanGongWuEntry.values()
                .filterTo(linkedSetOf()) { skippedEntries.contains(it.prefValue) },
            stopMode = prefs.getString(KEY_STOP_MODE, MODE_RESOURCE) ?: MODE_RESOURCE,
            runCount = prefs.getString(KEY_RUN_COUNT, "").orEmpty(),
            duration = prefs.getString(KEY_DURATION, "").orEmpty(),
            lowSpecDelay = prefs.getString(KEY_LOW_SPEC_DELAY, "").orEmpty(),
        )
    }

    private fun buildConfig(state: BirdFoodUiState): BirdFoodConfig? {
        val runCount = state.runCount.trim().toIntOrNull()
        val durationMinutes = state.duration.trim().toIntOrNull()
        val lowSpecDelayMs = state.lowSpecDelay.trim().toLongOrNull() ?: 0L

        if (state.stopMode == MODE_RUN_COUNT && (runCount == null || runCount <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的次数", Toast.LENGTH_SHORT).show()
            return null
        }
        if (state.stopMode == MODE_DURATION && (durationMinutes == null || durationMinutes <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的分钟数", Toast.LENGTH_SHORT).show()
            return null
        }
        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }
        if (
            state.selectedTask == BirdFoodTaskType.DAI_BAN_GONG_WU &&
            state.skippedDaiBanGongWuEntries.size == DaiBanGongWuEntry.values().size
        ) {
            Toast.makeText(requireContext(), "待办公务至少保留一个入口", Toast.LENGTH_SHORT).show()
            return null
        }

        return BirdFoodConfig(
            selectedTask = state.selectedTask,
            autoEatEnabled = state.stopMode != MODE_RESOURCE,
            stopCondition = when (state.stopMode) {
                MODE_RUN_COUNT -> BirdFoodStopCondition.RUN_COUNT
                MODE_DURATION -> BirdFoodStopCondition.DURATION_MINUTES
                else -> BirdFoodStopCondition.RESOURCE_EXHAUSTED
            },
            debugModeEnabled = state.debugModeEnabled,
            saveDebugScreenshotsEnabled = state.debugModeEnabled && state.saveDebugScreenshotsEnabled,
            lowSpecDelayMs = lowSpecDelayMs,
            maxRuns = runCount,
            maxDurationMinutes = durationMinutes,
            daiBanGongWuOption = state.daiBanGongWuOption,
            skippedDaiBanGongWuEntries = state.skippedDaiBanGongWuEntries,
            taDeChuanWenOption = state.taDeChuanWenOption,
        )
    }

    private fun saveState(state: BirdFoodUiState) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TASK_TUFA, state.selectedTask == BirdFoodTaskType.TU_FA_QING_KUANG)
            .putBoolean(KEY_TASK_XIAODAO, state.selectedTask == BirdFoodTaskType.XIAO_DAO_XIAO_XI)
            .putBoolean(KEY_TASK_CHUANWEN, state.selectedTask == BirdFoodTaskType.TA_DE_CHUAN_WEN)
            .putBoolean(KEY_TASK_GONGWU, state.selectedTask == BirdFoodTaskType.DAI_BAN_GONG_WU)
            .putBoolean(KEY_DEBUG_MODE, state.debugModeEnabled)
            .putBoolean(KEY_SAVE_SCREENSHOT, state.saveDebugScreenshotsEnabled)
            .putString(
                KEY_CHUANWEN_OPTION,
                if (state.taDeChuanWenOption == TaDeChuanWenOption.DAIHAOYUAN) OPTION_DAIHAOYUAN else OPTION_RUYUAN,
            )
            .putString(
                KEY_GONGWU_OPTION,
                if (state.daiBanGongWuOption == DaiBanGongWuOption.WU_ZHU_QIAN) OPTION_WUZHUQIAN else OPTION_BINGSHU,
            )
            .putStringSet(KEY_GONGWU_SKIP_ENTRIES, state.skippedDaiBanGongWuEntries.mapTo(linkedSetOf()) { it.prefValue })
            .putString(KEY_STOP_MODE, state.stopMode)
            .putString(KEY_RUN_COUNT, state.runCount.trim())
            .putString(KEY_DURATION, state.duration.trim())
            .putString(KEY_LOW_SPEC_DELAY, state.lowSpecDelay.trim())
            .apply()
    }

    private fun startBirdFoodService() {
        val context = requireContext()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", "ACTION_START_BIRD_FOOD")
            .apply()

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(context, "请启用 YuanAssist 无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            val intent = Intent(context, YuanAssistService::class.java).apply {
                action = "ACTION_START_BIRD_FOOD"
            }
            context.startService(intent)
            Toast.makeText(context, "鸟食任务已交给悬浮窗执行", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(requireContext(), YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) return true
        }
        return false
    }

}

private data class BirdFoodUiState(
    val selectedTask: BirdFoodTaskType,
    val debugModeEnabled: Boolean,
    val saveDebugScreenshotsEnabled: Boolean,
    val taDeChuanWenOption: TaDeChuanWenOption,
    val daiBanGongWuOption: DaiBanGongWuOption,
    val skippedDaiBanGongWuEntries: Set<DaiBanGongWuEntry>,
    val stopMode: String,
    val runCount: String,
    val duration: String,
    val lowSpecDelay: String,
)

@Composable
private fun DailyBirdFoodScreen(
    initialState: BirdFoodUiState,
    onBack: () -> Unit,
    onConfirm: (BirdFoodUiState) -> Unit,
) {
    var selectedTask by rememberSaveable { mutableStateOf(initialState.selectedTask) }
    var debugModeEnabled by rememberSaveable { mutableStateOf(initialState.debugModeEnabled) }
    var saveDebugScreenshotsEnabled by rememberSaveable { mutableStateOf(initialState.saveDebugScreenshotsEnabled) }
    var taDeChuanWenOption by rememberSaveable { mutableStateOf(initialState.taDeChuanWenOption) }
    var daiBanGongWuOption by rememberSaveable { mutableStateOf(initialState.daiBanGongWuOption) }
    var skippedDaiBanGongWuEntries by rememberSaveable {
        mutableStateOf(initialState.skippedDaiBanGongWuEntries.map { it.name })
    }
    var stopMode by rememberSaveable { mutableStateOf(initialState.stopMode) }
    var runCount by rememberSaveable { mutableStateOf(initialState.runCount) }
    var duration by rememberSaveable { mutableStateOf(initialState.duration) }
    var lowSpecDelay by rememberSaveable { mutableStateOf(initialState.lowSpecDelay) }

    val state = BirdFoodUiState(
        selectedTask = selectedTask,
        debugModeEnabled = debugModeEnabled,
        saveDebugScreenshotsEnabled = saveDebugScreenshotsEnabled,
        taDeChuanWenOption = taDeChuanWenOption,
        daiBanGongWuOption = daiBanGongWuOption,
        skippedDaiBanGongWuEntries = skippedDaiBanGongWuEntries.mapNotNull { name ->
            DaiBanGongWuEntry.values().firstOrNull { it.name == name }
        }.toSet(),
        stopMode = stopMode,
        runCount = runCount,
        duration = duration,
        lowSpecDelay = lowSpecDelay,
    )

    SubpageScaffold(
        title = "刷鸟食",
        subtitle = "任务选择 · 运行方式 · 调试配置",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "任务类型",
            subtitle = "单选，新版本鸟食不再推荐多任务协同",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BirdFoodTaskType.values().forEach { task ->
                    SubpageRadioOption(
                        text = task.displayName,
                        selected = selectedTask == task,
                        onClick = { selectedTask = task },
                    )
                }
            }
            if (selectedTask == BirdFoodTaskType.TA_DE_CHUAN_WEN) {
                SubpageFieldGroup(
                    title = "他的传闻模式",
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubpageRadioOption(
                            text = "如鸢",
                            selected = taDeChuanWenOption == TaDeChuanWenOption.RUYUAN,
                            onClick = { taDeChuanWenOption = TaDeChuanWenOption.RUYUAN },
                            modifier = Modifier.weight(1f),
                        )
                        SubpageRadioOption(
                            text = "代号鸢",
                            selected = taDeChuanWenOption == TaDeChuanWenOption.DAIHAOYUAN,
                            onClick = { taDeChuanWenOption = TaDeChuanWenOption.DAIHAOYUAN },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (selectedTask == BirdFoodTaskType.DAI_BAN_GONG_WU) {
                SubpageFieldGroup(
                    title = "待办公务模式",
                    subtitle = "可跳过识别异常的入口，但至少保留一个",
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubpageRadioOption(
                            text = "兵书",
                            selected = daiBanGongWuOption == DaiBanGongWuOption.BING_SHU,
                            onClick = { daiBanGongWuOption = DaiBanGongWuOption.BING_SHU },
                            modifier = Modifier.weight(1f),
                        )
                        SubpageRadioOption(
                            text = "五铢钱",
                            selected = daiBanGongWuOption == DaiBanGongWuOption.WU_ZHU_QIAN,
                            onClick = { daiBanGongWuOption = DaiBanGongWuOption.WU_ZHU_QIAN },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    listOf(
                        DaiBanGongWuEntry.LEFT_TOP to "跳过左上",
                        DaiBanGongWuEntry.RIGHT_TOP to "跳过右上",
                        DaiBanGongWuEntry.LEFT_BOTTOM to "跳过左下",
                        DaiBanGongWuEntry.RIGHT_BOTTOM to "跳过右下",
                    ).forEach { (entry, label) ->
                        CheckOption(
                            text = label,
                            checked = skippedDaiBanGongWuEntries.contains(entry.name),
                            onCheckedChange = { checked ->
                                skippedDaiBanGongWuEntries = if (checked) {
                                    (skippedDaiBanGongWuEntries + entry.name).distinct()
                                } else {
                                    skippedDaiBanGongWuEntries - entry.name
                                }
                            },
                        )
                    }
                }
            }
        }

        SubpageSectionCard(
            title = "调试",
            subtitle = "用于查看模板匹配红框与保存截图",
        ) {
            CheckOption(
                text = "启用调试模式",
                checked = debugModeEnabled,
                onCheckedChange = { debugModeEnabled = it },
            )
            if (debugModeEnabled) {
                SubpageRadioOption(
                    text = "保存调试截图",
                    selected = saveDebugScreenshotsEnabled,
                    onClick = { saveDebugScreenshotsEnabled = true },
                )
                SubpageRadioOption(
                    text = "不保存调试截图",
                    selected = !saveDebugScreenshotsEnabled,
                    onClick = { saveDebugScreenshotsEnabled = false },
                )
            }
        }

        SubpageSectionCard(
            title = "运行方式",
            subtitle = "选择自动吃鸟食、现有次数、固定次数或固定时间",
        ) {
            SubpageRadioOption(
                text = "自动吃鸟食（一直运行到鸟食耗尽）",
                selected = stopMode == MODE_AUTO_EAT,
                onClick = { stopMode = MODE_AUTO_EAT },
            )
            SubpageRadioOption(
                text = "不自动吃鸟食（刷完现有次数）",
                selected = stopMode == MODE_RESOURCE,
                onClick = { stopMode = MODE_RESOURCE },
            )
            SubpageRadioOption(
                text = "运行次数",
                selected = stopMode == MODE_RUN_COUNT,
                onClick = { stopMode = MODE_RUN_COUNT },
            )
            if (stopMode == MODE_RUN_COUNT) {
                SubpageTextField(
                    value = runCount,
                    onValueChange = { runCount = it },
                    label = "请输入次数",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SubpageRadioOption(
                text = "运行时间",
                selected = stopMode == MODE_DURATION,
                onClick = { stopMode = MODE_DURATION },
            )
            if (stopMode == MODE_DURATION) {
                SubpageTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = "请输入时间（分钟）",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SubpageSectionCard(
            title = "低配机型适应",
            subtitle = "会在所有任务原始 delay 基础上统一增加该数值，单位 ms。",
        ) {
            SubpageTextField(
                value = lowSpecDelay,
                onValueChange = { lowSpecDelay = it },
                label = "0 表示关闭",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        StoneStyleButton(
            text = "导入",
            onClick = { onConfirm(state) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun CheckOption(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SubpageCheckOption(
        text = text,
        checked = checked,
        onClick = { onCheckedChange(!checked) },
    )
}
