package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.core.DailyPlanSelection
import com.example.yuanassist.core.OneKeyDailyBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageCheckOption
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.google.gson.Gson

class OneKeyDailyActivity : AppCompatActivity() {

    companion object {
        private const val DAILY_ASSET_DIR = "daily_scripts/daily"
        private const val PREFS_APP = "app_prefs"
        private const val KEY_PENDING_START_ACTION = "pending_start_action"
        private const val KEY_PENDING_PERMISSION_REQUEST = "pending_one_key_daily_permission_request"
        private const val KEY_REMEMBERED_SELECTED_FILES = "one_key_daily_selected_files"
        private const val KEY_REMEMBERED_TRAINING_OPTION = "one_key_daily_training_option"
        private const val KEY_REMEMBERED_STARGAZING_OPTION = "one_key_daily_stargazing_option"
        private const val KEY_REMEMBERED_STARGAZING_WITH_CARD_COUNT =
            "one_key_daily_stargazing_with_card_count"
        private const val KEY_REMEMBERED_STARGAZING_WITHOUT_CARD_COUNT =
            "one_key_daily_stargazing_without_card_count"
        private const val KEY_REMEMBERED_DEBUG_MODE = "one_key_daily_debug_mode"
        private const val KEY_REMEMBERED_SAVE_SCREENSHOT = "one_key_daily_save_screenshot"
        private const val PENDING_PERMISSION_OVERLAY = "overlay"
        private const val PENDING_PERMISSION_ACCESSIBILITY = "accessibility"
        private const val TRAINING_FILE_NAME = "历练.json"
        private const val STARGAZING_FILE_NAME = "观星.json"
        private const val STARGAZING_DEFAULT_COUNT = "30"
        private const val STARGAZING_BATCH_SIZE = 30
        private const val STARGAZING_CLICK_INTERVAL = "1200"
        private val PREFERRED_ORDER = listOf(
            "领取体力",
            "领取月卡",
            "行囊派遣",
            "送礼一次",
            "历练",
            "观星",
            "白鹄扫荡",
            "相见",
            "鸢报一轮",
            "密探升级",
            "家具互动",
            "密探特训",
            "家具历险",
            "家具打造",
            "材料打造",
        )
        private val TRAINING_OPTIONS = listOf(
            TrainingOption("1", "铜钱"),
            TrainingOption("2", "经验"),
            TrainingOption("3", "风火"),
            TrainingOption("4", "地水"),
            TrainingOption("5", "阴阳"),
        )
        private const val DEFAULT_TRAINING_OPTION = "2"
        private const val DEFAULT_STARGAZING_OPTION = "single"
        private val CONFIG_OPTION_FONT_SIZE = 13.sp
        private val CONFIG_FIELD_LABEL_FONT_SIZE = 12.sp
        private val CONFIG_INDENT = 18.dp
        private val CONFIG_RADIO_INDICATOR_SIZE = 14.dp
    }

    private data class DailyAssetEntry(
        val fileName: String,
        val displayName: String,
        val jsonContent: String,
    )

    private data class TrainingOption(
        val value: String,
        val label: String,
    )

    private data class StargazingOption(
        val value: String,
        val label: String,
    )

    private val gson = Gson()
    private val stargazingOptions = listOf(
        StargazingOption("single", "观星一次"),
        StargazingOption("with_card", "有月卡观星"),
        StargazingOption("without_card", "无月卡观星"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = loadEntries()
        setContent {
            OneKeyDailyScreen(entries = entries)
        }
    }

    override fun onResume() {
        super.onResume()
        restorePendingImportIfPossible()
    }

    private fun loadEntries(): List<DailyAssetEntry> {
        val fileNames = try {
            assets.list(DAILY_ASSET_DIR)
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return fileNames.mapNotNull { fileName ->
            val assetPath = "$DAILY_ASSET_DIR/$fileName"
            val content = runCatching {
                assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val plan = runCatching {
                gson.fromJson(content, DailyTaskPlan::class.java)
            }.getOrNull() ?: return@mapNotNull null
            DailyAssetEntry(
                fileName = fileName,
                displayName = plan.display_name?.takeIf { it.isNotBlank() }
                    ?: fileName.removeSuffix(".json"),
                jsonContent = content,
            )
        }.sortedWith(
            compareBy<DailyAssetEntry> { entry ->
                PREFERRED_ORDER.indexOf(entry.displayName).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.displayName }
        )
    }

    @Composable
    private fun OneKeyDailyScreen(entries: List<DailyAssetEntry>) {
        val selectedFileNames = remember(entries) {
            mutableStateListOf<String>().apply {
                addAll(loadRememberedSelectedFileNames(entries))
            }
        }
        var trainingSelection by remember { mutableStateOf(loadRememberedTrainingSelection()) }
        var stargazingSelection by remember { mutableStateOf(loadRememberedStargazingSelection()) }
        var stargazingWithCardCount by remember { mutableStateOf(loadRememberedStargazingWithCardCount()) }
        var stargazingWithoutCardCount by remember {
            mutableStateOf(loadRememberedStargazingWithoutCardCount())
        }
        var debugModeEnabled by remember { mutableStateOf(loadRememberedDebugModeEnabled()) }
        var saveDebugScreenshotsEnabled by remember {
            mutableStateOf(loadRememberedSaveDebugScreenshotsEnabled())
        }
        var importing by remember { mutableStateOf(false) }
        val selectedCount = selectedFileNames.size

        SubpageScaffold(
            title = "一键日常",
            subtitle = "从内置日常脚本中多选，按列表顺序交给悬浮窗依次执行",
            onBack = { finish() },
        ) {
            SubpageSectionCard {
                androidx.compose.material3.Text(
                    text = if (entries.isEmpty()) {
                        "当前没有可用脚本。"
                    } else {
                        "已选 $selectedCount / ${entries.size} 个。运行时会按当前列表顺序依次执行，单项失败不会中断后续任务。"
                    },
                    color = BodyInk,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                )
            }

            if (entries.isEmpty()) {
                SubpageSectionCard {
                    androidx.compose.material3.Text(
                        text = "未读取到 daily 目录下的脚本",
                        color = BodyInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            } else {
                SubpageSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.chunked(2).forEach { rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowEntries.forEach { entry ->
                                    val checked = selectedFileNames.contains(entry.fileName)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        SubpageCheckOption(
                                            text = entry.displayName,
                                            checked = checked,
                                            onClick = {
                                                if (checked) {
                                                    selectedFileNames.remove(entry.fileName)
                                                } else {
                                                    selectedFileNames.add(entry.fileName)
                                                }
                                                saveRememberedSelectedFileNames(selectedFileNames.toList())
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        if (checked && entry.fileName == TRAINING_FILE_NAME) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = CONFIG_INDENT),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                TRAINING_OPTIONS.forEach { option ->
                                                    SubpageRadioOption(
                                                        text = option.label,
                                                        selected = trainingSelection == option.value,
                                                        onClick = {
                                                            trainingSelection = option.value
                                                            saveRememberedTrainingSelection(option.value)
                                                        },
                                                        titleFontSize = CONFIG_OPTION_FONT_SIZE,
                                                        indicatorSize = CONFIG_RADIO_INDICATOR_SIZE,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                            }
                                        }
                                        if (checked && entry.fileName == STARGAZING_FILE_NAME) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = CONFIG_INDENT),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                stargazingOptions.forEach { option ->
                                                    SubpageRadioOption(
                                                        text = option.label,
                                                        selected = stargazingSelection == option.value,
                                                        onClick = {
                                                            stargazingSelection = option.value
                                                            saveRememberedStargazingSelection(option.value)
                                                        },
                                                        titleFontSize = CONFIG_OPTION_FONT_SIZE,
                                                        indicatorSize = CONFIG_RADIO_INDICATOR_SIZE,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                                if (stargazingSelection != DEFAULT_STARGAZING_OPTION) {
                                                    val isWithCard = stargazingSelection == "with_card"
                                                    SubpageTextField(
                                                        value = if (isWithCard) {
                                                            stargazingWithCardCount
                                                        } else {
                                                            stargazingWithoutCardCount
                                                        },
                                                        onValueChange = { value ->
                                                            if (isWithCard) {
                                                                stargazingWithCardCount = value
                                                                saveRememberedStargazingWithCardCount(value)
                                                            } else {
                                                                stargazingWithoutCardCount = value
                                                                saveRememberedStargazingWithoutCardCount(value)
                                                            }
                                                        },
                                                        label = if (isWithCard) {
                                                            "输入有月卡观星次数"
                                                        } else {
                                                            "输入无月卡观星次数"
                                                        },
                                                        labelFontSize = CONFIG_FIELD_LABEL_FONT_SIZE,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowEntries.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                SubpageSectionCard(
                    title = "调试",
                    subtitle = "用于运行时查看识别 ROI 红框，也可按需保存调试截图",
                ) {
                    SubpageCheckOption(
                        text = "启用调试模式",
                        checked = debugModeEnabled,
                        onClick = {
                            debugModeEnabled = !debugModeEnabled
                            saveRememberedDebugModeEnabled(debugModeEnabled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (debugModeEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SubpageRadioOption(
                                text = "保存调试截图",
                                selected = saveDebugScreenshotsEnabled,
                                onClick = {
                                    saveDebugScreenshotsEnabled = true
                                    saveRememberedSaveDebugScreenshotsEnabled(true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SubpageRadioOption(
                                text = "不保存调试截图",
                                selected = !saveDebugScreenshotsEnabled,
                                onClick = {
                                    saveDebugScreenshotsEnabled = false
                                    saveRememberedSaveDebugScreenshotsEnabled(false)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SubpageSectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StoneStyleButton(
                            text = if (selectedCount == entries.size) "取消全选" else "全选",
                            selected = false,
                            onClick = {
                                if (selectedCount == entries.size) {
                                    selectedFileNames.clear()
                                } else {
                                    selectedFileNames.clear()
                                    selectedFileNames.addAll(entries.map { it.fileName })
                                }
                                saveRememberedSelectedFileNames(selectedFileNames.toList())
                            },
                        )
                        StoneStyleButton(
                            text = if (importing) "导入中..." else "导入到悬浮窗",
                            enabled = !importing && selectedCount > 0,
                            onClick = {
                                importing = true
                                importSelections(
                                    entries = entries,
                                    selectedFileNames = selectedFileNames.toList(),
                                    trainingSelection = trainingSelection,
                                    stargazingSelection = stargazingSelection,
                                    stargazingWithCardCount = stargazingWithCardCount,
                                    stargazingWithoutCardCount = stargazingWithoutCardCount,
                                    debugModeEnabled = debugModeEnabled,
                                    saveDebugScreenshotsEnabled = saveDebugScreenshotsEnabled,
                                    onFinished = { importing = false },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun importSelections(
        entries: List<DailyAssetEntry>,
        selectedFileNames: List<String>,
        trainingSelection: String,
        stargazingSelection: String,
        stargazingWithCardCount: String,
        stargazingWithoutCardCount: String,
        debugModeEnabled: Boolean,
        saveDebugScreenshotsEnabled: Boolean,
        onFinished: () -> Unit,
    ) {
        if (selectedFileNames.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个脚本", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        val selectedEntries = entries.filter { selectedFileNames.contains(it.fileName) }
        if (selectedEntries.isEmpty()) {
            Toast.makeText(this, "没有可导入的脚本", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        val pendingSelections = runCatching {
            selectedEntries.map { entry ->
                DailyPlanSelection(
                    fileName = entry.fileName.removeSuffix(".json"),
                    jsonContent = buildImportJson(
                        entry = entry,
                        trainingSelection = trainingSelection,
                        stargazingSelection = stargazingSelection,
                        stargazingWithCardCount = stargazingWithCardCount,
                        stargazingWithoutCardCount = stargazingWithoutCardCount,
                    ),
                )
            }
        }.getOrElse { error ->
            Toast.makeText(this, error.message ?: "导入参数无效", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }
        prefs().edit()
            .putString(KEY_PENDING_START_ACTION, OneKeyDailyBridge.ACTION_IMPORT_ONE_KEY_DAILY_QUEUE)
            .putBoolean(KEY_REMEMBERED_DEBUG_MODE, debugModeEnabled)
            .putBoolean(KEY_REMEMBERED_SAVE_SCREENSHOT, saveDebugScreenshotsEnabled)
            .apply()
        OneKeyDailyBridge.savePendingSelections(this, pendingSelections)

        if (!Settings.canDrawOverlays(this)) {
            prefs().edit().putString(KEY_PENDING_PERMISSION_REQUEST, PENDING_PERMISSION_OVERLAY).apply()
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            onFinished()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            prefs().edit().putString(KEY_PENDING_PERMISSION_REQUEST, PENDING_PERMISSION_ACCESSIBILITY).apply()
            Toast.makeText(this, "请先开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            onFinished()
            return
        }

        prefs().edit().remove(KEY_PENDING_PERMISSION_REQUEST).apply()
        startPendingImport(
            onSuccess = {
                Toast.makeText(this, "一键日常已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_LONG).show()
                onFinished()
                finish()
            },
            onFailure = { error ->
                Toast.makeText(this, "导入失败：${error.message}", Toast.LENGTH_SHORT).show()
                onFinished()
            },
        )
    }

    private fun restorePendingImportIfPossible() {
        val pendingAction = prefs().getString(KEY_PENDING_START_ACTION, null)
        if (pendingAction != OneKeyDailyBridge.ACTION_IMPORT_ONE_KEY_DAILY_QUEUE) return
        val pendingPermissionRequest = prefs().getString(KEY_PENDING_PERMISSION_REQUEST, null)
        if (pendingPermissionRequest != PENDING_PERMISSION_OVERLAY) return
        val pendingSelections = OneKeyDailyBridge.peekPendingSelections(this)
        if (pendingSelections.isNullOrEmpty()) {
            prefs().edit()
                .remove(KEY_PENDING_START_ACTION)
                .remove(KEY_PENDING_PERMISSION_REQUEST)
                .apply()
            return
        }
        if (!Settings.canDrawOverlays(this) || !isAccessibilityServiceEnabled()) return
        startPendingImport(
            onSuccess = {
                prefs().edit().remove(KEY_PENDING_PERMISSION_REQUEST).apply()
                Toast.makeText(this, "一键日常已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_LONG).show()
                finish()
            },
            onFailure = { error ->
                Toast.makeText(this, "导入失败：${error.message}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun startPendingImport(
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        runCatching {
            startService(Intent(this, YuanAssistService::class.java).apply {
                action = OneKeyDailyBridge.ACTION_IMPORT_ONE_KEY_DAILY_QUEUE
            })
        }.onSuccess {
            onSuccess()
        }.onFailure(onFailure)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, YuanAssistService::class.java)
        val enabledServicesSetting =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun buildImportJson(
        entry: DailyAssetEntry,
        trainingSelection: String,
        stargazingSelection: String,
        stargazingWithCardCount: String,
        stargazingWithoutCardCount: String,
    ): String {
        if (entry.fileName == TRAINING_FILE_NAME) {
            val plan = gson.fromJson(entry.jsonContent, DailyTaskPlan::class.java)
            val updatedTasks = plan.tasks.map { task ->
                if (task.id == 100 && task.action == "SET_VAR" && task.params?.var_name == "which") {
                    task.copy(params = task.params.copy(var_value = trainingSelection))
                } else {
                    task
                }
            }
            return gson.toJson(plan.copy(tasks = updatedTasks))
        }
        if (entry.fileName == STARGAZING_FILE_NAME) {
            return gson.toJson(
                buildStargazingPlan(
                    selection = stargazingSelection,
                    withCardCountText = stargazingWithCardCount,
                    withoutCardCountText = stargazingWithoutCardCount,
                )
            )
        }
        return entry.jsonContent
    }

    private fun buildStargazingPlan(
        selection: String,
        withCardCountText: String,
        withoutCardCountText: String,
    ): DailyTaskPlan {
        return when (selection) {
            "with_card" -> buildWithCardStargazingPlan(
                parsePositiveCount(withCardCountText, "有月卡观星"),
            )
            "without_card" -> buildWithoutCardStargazingPlan(
                totalCount = parsePositiveCount(withoutCardCountText, "无月卡观星"),
                displayName = "无月卡观星",
            )
            else -> buildWithoutCardStargazingPlan(totalCount = 1, displayName = "观星一次")
        }
    }

    private fun buildWithCardStargazingPlan(totalCount: Int): DailyTaskPlan {
        val tasks = mutableListOf<DailyTask>()
        val fullBatchCount = totalCount / STARGAZING_BATCH_SIZE
        val remainder = totalCount % STARGAZING_BATCH_SIZE
        var nextTaskId = 1

        repeat(fullBatchCount) { index ->
            val taskId = nextTaskId
            nextTaskId += 1
            val onSuccess = if (index == fullBatchCount - 1 && remainder == 0) -1 else nextTaskId
            tasks += DailyTask(
                id = taskId,
                action = "RUN_SCRIPT_SEGMENT",
                delay = 0,
                params = TaskParams(
                    script_name = "you_yue_ka_guan_xing_batch.json",
                    entry_task_id = 0,
                    inherit_variables = true,
                ),
                on_success = onSuccess,
                on_fail = -2,
            )
        }

        if (remainder > 0 || tasks.isEmpty()) {
            appendWithoutCardStargazingTasks(
                tasks = tasks,
                startTaskId = nextTaskId,
                totalCount = if (remainder > 0) remainder else totalCount,
                firstEntryTaskId = 0,
            )
        }

        return DailyTaskPlan(
            start_task_id = 1,
            tasks = tasks,
            asset_template_dir = "daily_script_templates/wu_yue_ka_guan_xing",
            display_name = "有月卡观星",
        )
    }

    private fun buildWithoutCardStargazingPlan(totalCount: Int, displayName: String): DailyTaskPlan {
        val tasks = mutableListOf<DailyTask>()
        appendWithoutCardStargazingTasks(
            tasks = tasks,
            startTaskId = 1,
            totalCount = totalCount,
            firstEntryTaskId = 0,
        )
        return DailyTaskPlan(
            start_task_id = 1,
            tasks = tasks,
            asset_template_dir = "daily_script_templates/wu_yue_ka_guan_xing",
            display_name = displayName,
        )
    }

    private fun appendWithoutCardStargazingTasks(
        tasks: MutableList<DailyTask>,
        startTaskId: Int,
        totalCount: Int,
        firstEntryTaskId: Int,
    ) {
        var nextTaskId = startTaskId
        val intervalTaskId = nextTaskId
        nextTaskId += 1
        tasks += DailyTask(
            id = intervalTaskId,
            action = "SET_VAR",
            delay = 0,
            params = TaskParams(
                var_name = "stargazing_click_interval",
                var_value = STARGAZING_CLICK_INTERVAL,
            ),
            on_success = nextTaskId,
            on_fail = -2,
        )

        val batches = buildStargazingBatches(totalCount)
        batches.forEachIndexed { index, batchCount ->
            val setCountTaskId = nextTaskId
            val runSegmentTaskId = nextTaskId + 1
            nextTaskId += 2
            val onSuccess = if (index == batches.lastIndex) -1 else nextTaskId
            tasks += DailyTask(
                id = setCountTaskId,
                action = "SET_VAR",
                delay = 0,
                params = TaskParams(
                    var_name = "current_batch_count",
                    var_value = batchCount.toString(),
                ),
                on_success = runSegmentTaskId,
                on_fail = -2,
            )
            tasks += DailyTask(
                id = runSegmentTaskId,
                action = "RUN_SCRIPT_SEGMENT",
                delay = 0,
                params = TaskParams(
                    script_name = "wu_yue_ka_guan_xing_batch.json",
                    entry_task_id = if (index == 0) firstEntryTaskId else 3,
                    inherit_variables = true,
                ),
                on_success = onSuccess,
                on_fail = -2,
            )
        }
    }

    private fun buildStargazingBatches(totalCount: Int): List<Int> {
        val batches = mutableListOf<Int>()
        var remaining = totalCount
        while (remaining > 0) {
            val batchCount = remaining.coerceAtMost(STARGAZING_BATCH_SIZE)
            batches += batchCount
            remaining -= batchCount
        }
        return batches
    }

    private fun parsePositiveCount(raw: String, label: String): Int {
        val value = raw.trim().toIntOrNull()
        require(value != null && value > 0) { "请输入有效的${label}次数" }
        return value
    }

    private fun loadRememberedSelectedFileNames(entries: List<DailyAssetEntry>): List<String> {
        val availableFileNames = entries.mapTo(linkedSetOf()) { it.fileName }
        return prefs()
            .getStringSet(KEY_REMEMBERED_SELECTED_FILES, emptySet())
            .orEmpty()
            .filter { it in availableFileNames }
    }

    private fun saveRememberedSelectedFileNames(fileNames: List<String>) {
        prefs().edit()
            .putStringSet(KEY_REMEMBERED_SELECTED_FILES, fileNames.toCollection(linkedSetOf()))
            .apply()
    }

    private fun loadRememberedTrainingSelection(): String {
        val saved = prefs().getString(KEY_REMEMBERED_TRAINING_OPTION, DEFAULT_TRAINING_OPTION)
        return saved?.takeIf { value -> TRAINING_OPTIONS.any { it.value == value } }
            ?: DEFAULT_TRAINING_OPTION
    }

    private fun saveRememberedTrainingSelection(value: String) {
        prefs().edit()
            .putString(KEY_REMEMBERED_TRAINING_OPTION, value)
            .apply()
    }

    private fun loadRememberedStargazingSelection(): String {
        val saved = prefs().getString(KEY_REMEMBERED_STARGAZING_OPTION, DEFAULT_STARGAZING_OPTION)
        return saved?.takeIf { value -> stargazingOptions.any { it.value == value } }
            ?: DEFAULT_STARGAZING_OPTION
    }

    private fun saveRememberedStargazingSelection(value: String) {
        prefs().edit()
            .putString(KEY_REMEMBERED_STARGAZING_OPTION, value)
            .apply()
    }

    private fun loadRememberedStargazingWithCardCount(): String {
        return prefs().getString(
            KEY_REMEMBERED_STARGAZING_WITH_CARD_COUNT,
            STARGAZING_DEFAULT_COUNT,
        ) ?: STARGAZING_DEFAULT_COUNT
    }

    private fun saveRememberedStargazingWithCardCount(value: String) {
        prefs().edit()
            .putString(KEY_REMEMBERED_STARGAZING_WITH_CARD_COUNT, value)
            .apply()
    }

    private fun loadRememberedStargazingWithoutCardCount(): String {
        return prefs().getString(
            KEY_REMEMBERED_STARGAZING_WITHOUT_CARD_COUNT,
            STARGAZING_DEFAULT_COUNT,
        ) ?: STARGAZING_DEFAULT_COUNT
    }

    private fun saveRememberedStargazingWithoutCardCount(value: String) {
        prefs().edit()
            .putString(KEY_REMEMBERED_STARGAZING_WITHOUT_CARD_COUNT, value)
            .apply()
    }

    private fun loadRememberedDebugModeEnabled(): Boolean =
        prefs().getBoolean(KEY_REMEMBERED_DEBUG_MODE, false)

    private fun saveRememberedDebugModeEnabled(value: Boolean) {
        prefs().edit()
            .putBoolean(KEY_REMEMBERED_DEBUG_MODE, value)
            .apply()
    }

    private fun loadRememberedSaveDebugScreenshotsEnabled(): Boolean =
        prefs().getBoolean(KEY_REMEMBERED_SAVE_SCREENSHOT, false)

    private fun saveRememberedSaveDebugScreenshotsEnabled(value: Boolean) {
        prefs().edit()
            .putBoolean(KEY_REMEMBERED_SAVE_SCREENSHOT, value)
            .apply()
    }

    private fun prefs() = getSharedPreferences(PREFS_APP, MODE_PRIVATE)
}
