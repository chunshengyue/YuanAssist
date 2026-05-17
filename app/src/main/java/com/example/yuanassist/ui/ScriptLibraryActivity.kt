package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.R
import com.example.yuanassist.core.CombatScriptExportBridge
import com.example.yuanassist.core.PendingCombatScriptExport
import com.example.yuanassist.core.DailyPlanSelection
import com.example.yuanassist.core.DailyScriptLibraryBridge
import com.example.yuanassist.core.LocalScriptJson
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScriptLibraryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "pick_mode"
        const val PICK_MODE_DAILY_PLAN = "daily_plan"
        const val PICK_MODE_RECORDED_DAILY_PLAN = "recorded_daily_plan"
        private const val DAILY_ASSET_DIR = "daily_scripts"
        private const val PREFS_APP = "app_prefs"
        private const val ACTION_START_COMBAT_WINDOW = "ACTION_START_COMBAT_WINDOW"
    }

    private enum class EntryType {
        LEGACY_SCRIPT,
        DAILY_PLAN
    }

    private enum class EntrySource {
        FILE_SYSTEM,
        ASSET
    }

    private data class LibraryEntry(
        val name: String,
        val displayName: String,
        val type: EntryType,
        val source: EntrySource,
        val file: File? = null,
        val assetPath: String? = null,
        val templateDirPath: String? = null,
        val bundle: UserDailyScriptBundle? = null,
        val taskCount: Int? = null,
        val updatedAt: Long = 0L
    )

    private val gson = Gson()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private var recordedListContainer: LinearLayout? = null
    private var recordedEmptyView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showComposeLibrary()
    }

    private fun isDailyPickerMode(): Boolean {
        return intent.getStringExtra(EXTRA_PICK_MODE) == PICK_MODE_DAILY_PLAN
    }

    private fun isRecordedDailyPickerMode(): Boolean {
        return intent.getStringExtra(EXTRA_PICK_MODE) == PICK_MODE_RECORDED_DAILY_PLAN
    }

    private fun isDailySelectionMode(): Boolean =
        isDailyPickerMode() || isRecordedDailyPickerMode()

    private fun loadEntries(): List<LibraryEntry> {
        if (isRecordedDailyPickerMode()) {
            return loadRecordedViewerEntries()
        }
        val dailyEntries = buildList {
            addAll(loadDailyUserEntries())
            addAll(loadDailyAssetEntries())
        }
        if (isDailyPickerMode()) {
            return dailyEntries
        }

        return buildList {
            addAll(loadLegacyFileEntries())
            addAll(dailyEntries)
        }
    }

    private fun loadLegacyFileEntries(): List<LibraryEntry> {
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()

        return files.mapNotNull { file ->
            val content = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val type = detectEntryType(content) ?: return@mapNotNull null
            if (type != EntryType.LEGACY_SCRIPT) return@mapNotNull null

            LibraryEntry(
                name = file.nameWithoutExtension,
                displayName = "[跟打] ${file.nameWithoutExtension}",
                type = type,
                source = EntrySource.FILE_SYSTEM,
                file = file,
                updatedAt = file.lastModified()
            )
        }
    }

    private fun loadDailyAssetEntries(): List<LibraryEntry> {
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
            val content = runCatching { readAssetText(assetPath) }.getOrNull() ?: return@mapNotNull null
            val type = detectEntryType(content) ?: return@mapNotNull null
            if (type != EntryType.DAILY_PLAN) return@mapNotNull null

            LibraryEntry(
                name = fileName.removeSuffix(".json"),
                displayName = "[日常] ${fileName.removeSuffix(".json")}",
                type = type,
                source = EntrySource.ASSET,
                assetPath = assetPath,
                taskCount = runCatching { gson.fromJson(content, DailyTaskPlan::class.java).tasks.size }.getOrNull()
            )
        }
    }

    private fun loadDailyUserEntries(): List<LibraryEntry> {
        return UserDailyScriptStore.listBundles(this).map { bundle ->
            LibraryEntry(
                name = bundle.scriptId,
                displayName = "[录制] ${bundle.scriptId}",
                type = EntryType.DAILY_PLAN,
                source = EntrySource.FILE_SYSTEM,
                file = bundle.scriptFile,
                templateDirPath = bundle.templatesDir.absolutePath,
                bundle = bundle,
                taskCount = runCatching { UserDailyScriptStore.loadPlan(bundle, gson).tasks.size }.getOrNull(),
                updatedAt = bundle.scriptFile.lastModified()
            )
        }
    }

    private fun loadRecordedViewerEntries(): List<LibraryEntry> {
        return buildList {
            addAll(loadDailyUserEntries())
            addAll(loadDailyAssetEntries())
        }
    }

    private fun showComposeLibrary() {
        setContent {
            var entries by remember { mutableStateOf(loadEntries()) }
            ScriptLibraryScreen(
                entries = entries,
                onRefreshEntries = { entries = loadEntries() },
            )
        }
    }

    @Composable
    private fun ScriptLibraryScreen(
        entries: List<LibraryEntry>,
        onRefreshEntries: () -> Unit,
    ) {
        val title = when {
            isRecordedDailyPickerMode() -> "日常脚本"
            isDailyPickerMode() -> "选择日常脚本"
            else -> "本地脚本库"
        }
        val subtitle = when {
            isRecordedDailyPickerMode() -> "包含录制脚本和应用内置脚本，内置脚本只读"
            isDailyPickerMode() -> "选择一个日常脚本导入当前流程"
            else -> "查看跟打脚本、日常脚本和录制脚本"
        }
        SubpageScaffold(
            title = title,
            subtitle = subtitle,
            onBack = { finish() },
        ) {
            if (entries.isEmpty()) {
                SubpageSectionCard {
                    Text(
                        text = "暂无脚本",
                        color = BodyInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entries.forEach { entry ->
                        ScriptLibraryEntryCard(
                            entry = entry,
                            onRefreshEntries = onRefreshEntries,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ScriptLibraryEntryCard(
        entry: LibraryEntry,
        onRefreshEntries: () -> Unit,
    ) {
        val isReadOnly = entry.bundle == null && entry.type == EntryType.DAILY_PLAN
        val showDeleteAction = when {
            isRecordedDailyPickerMode() -> entry.bundle != null
            isDailyPickerMode() -> false
            else -> entry.source == EntrySource.FILE_SYSTEM
        }
        val sourceText = when {
            entry.type == EntryType.LEGACY_SCRIPT -> "跟打脚本"
            isReadOnly -> "应用内置只读"
            else -> "录制脚本"
        }
        SubpageSectionCard(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { handleEntryCardClick(entry) },
            )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = entry.name,
                            color = TitleInk,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                        )
                        Text(
                            text = "来源：$sourceText",
                            color = BodyInk,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Serif,
                        )
                        Text(
                            text = buildEntryMeta(entry, isReadOnly),
                            color = QuietInk,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                        )
                    }
                    Text(
                        text = if (entry.type == EntryType.DAILY_PLAN) "日常" else "跟打",
                        color = HighlightGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StoneStyleButton(
                        text = primaryActionText(),
                        modifier = Modifier.weight(1f),
                        onClick = { handlePrimaryAction(entry) },
                    )
                    if (showDeleteAction) {
                        StoneStyleButton(
                            text = "删除",
                            selected = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                confirmDeleteRecordedEntry(entry) {
                                    onRefreshEntries()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun buildEntryMeta(entry: LibraryEntry, isReadOnly: Boolean): String {
        if (entry.type == EntryType.LEGACY_SCRIPT) {
            return "更新时间：${formatTime(entry.updatedAt)}"
        }
        val taskText = entry.taskCount?.let { "任务数：$it" } ?: "任务数：解析失败"
        val timeText = if (isReadOnly) "更新时间：内置资源" else "更新时间：${formatTime(entry.updatedAt)}"
        return "$taskText    $timeText"
    }

    private fun primaryActionText(): String {
        return when {
            isRecordedDailyPickerMode() -> "导入"
            isDailyPickerMode() -> "选择"
            else -> "查看"
        }
    }

    private fun handleEntryCardClick(entry: LibraryEntry) {
        when {
            isRecordedDailyPickerMode() -> showDailyPlanPreview(entry)
            isDailySelectionMode() -> selectDailyPlan(entry)
            entry.type == EntryType.LEGACY_SCRIPT -> showLegacyScriptPreview(entry)
            entry.type == EntryType.DAILY_PLAN -> showDailyPlanPreview(entry)
        }
    }

    private fun handlePrimaryAction(entry: LibraryEntry) {
        when {
            isRecordedDailyPickerMode() -> selectDailyPlan(entry)
            isDailyPickerMode() -> selectDailyPlan(entry)
            entry.type == EntryType.LEGACY_SCRIPT -> showLegacyScriptPreview(entry)
            entry.type == EntryType.DAILY_PLAN -> showDailyPlanPreview(entry)
        }
    }

    private fun showRecordedDailyLibrary() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dark_glass_flat)
            setPadding(dp(18), dp(18), dp(18), dp(18))

            addView(TextView(this@ScriptLibraryActivity).apply {
                text = "日常脚本"
                setTextColor(Color.parseColor("#E5C07B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(this@ScriptLibraryActivity).apply {
                text = "包含录制脚本和应用内置脚本，内置脚本只读"
                setTextColor(Color.parseColor("#D6D6D6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(8), 0, 0)
            })
        })

        val scrollView = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        recordedListContainer = content
        recordedEmptyView = TextView(this).apply {
            text = "还没有可用脚本"
            setTextColor(Color.parseColor("#8A7A58"))
            gravity = Gravity.CENTER_HORIZONTAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(32), 0, dp(8))
        }

        setContentView(root)
        renderRecordedDailyEntries()
    }

    private fun renderRecordedDailyEntries() {
        val container = recordedListContainer ?: return
        val emptyView = recordedEmptyView ?: return
        val entries = loadRecordedViewerEntries()
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.addView(emptyView)
            return
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(12)
                    )
                })
            }
            container.addView(buildRecordedScriptCard(entry))
        }
    }

    private fun buildRecordedScriptCard(entry: LibraryEntry): View {
        val isReadOnly = entry.bundle == null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dark_glass)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            isClickable = true
            isFocusable = true
            setOnClickListener { showDailyPlanPreview(entry) }

            addView(TextView(this@ScriptLibraryActivity).apply {
                text = entry.name
                setTextColor(Color.parseColor("#E5C07B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(this@ScriptLibraryActivity).apply {
                text = if (isReadOnly) {
                    "来源：应用内置只读    任务数：${entry.taskCount ?: "解析失败"}"
                } else {
                    "来源：录制脚本    任务数：${entry.taskCount ?: "解析失败"}"
                }
                setTextColor(Color.parseColor("#F4E7C7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(10), 0, 0)
            })

            addView(TextView(this@ScriptLibraryActivity).apply {
                text = if (isReadOnly) "更新时间：内置资源" else "更新时间：${formatTime(entry.updatedAt)}"
                setTextColor(Color.parseColor("#B9B3A7"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, 0)
            })

            addView(LinearLayout(this@ScriptLibraryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(14), 0, 0)

                addView(buildActionButton("查看", false).apply {
                    setOnClickListener { showDailyPlanPreview(entry) }
                })
                addView(View(this@ScriptLibraryActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(10), 1)
                })

                addView(buildActionButton("导入", true).apply {
                    setOnClickListener { selectDailyPlan(entry) }
                })

                if (!isReadOnly) {
                    addView(View(this@ScriptLibraryActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(10), 1)
                    })

                    addView(buildActionButton("删除", false).apply {
                        setOnClickListener { confirmDeleteRecordedEntry(entry) }
                    })
                }
            })
        }
    }

    private fun confirmDeleteRecordedEntry(entry: LibraryEntry) {
        confirmDeleteRecordedEntry(entry) {
            renderRecordedDailyEntries()
        }
    }

    private fun confirmDeleteRecordedEntry(entry: LibraryEntry, onDeleted: () -> Unit) {
        val bundle = entry.bundle ?: return
        AlertDialog.Builder(this)
            .setTitle("删除脚本")
            .setMessage("确认删除 ${entry.name} 吗？会同时删除本地 json 和模板图片。")
            .setPositiveButton("删除") { _, _ ->
                val deleted = UserDailyScriptStore.deleteBundle(bundle)
                Toast.makeText(
                    this,
                    if (deleted) "已删除 ${entry.name}" else "删除失败",
                    Toast.LENGTH_SHORT
                ).show()
                if (deleted) {
                    onDeleted()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildActionButton(text: String, accent: Boolean): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minimumWidth = 0
            minWidth = dp(68)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (accent) Color.parseColor("#20170D") else Color.parseColor("#E5C07B"))
            setBackgroundResource(if (accent) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
        }
    }

    private fun formatTime(timeMillis: Long): String {
        return if (timeMillis <= 0L) "未知" else timeFormat.format(Date(timeMillis))
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun detectEntryType(content: String): EntryType? {
        return try {
            val jsonObject = JsonParser.parseString(content).asJsonObject
            when {
                jsonObject.has("start_task_id") && jsonObject.has("tasks") -> EntryType.DAILY_PLAN
                jsonObject.has("scriptContent") && jsonObject.has("config") -> EntryType.LEGACY_SCRIPT
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun selectDailyPlan(entry: LibraryEntry) {
        if (entry.type != EntryType.DAILY_PLAN) {
            Toast.makeText(this, "这个文件不是日常任务 JSON", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val content = readEntryText(entry)
            gson.fromJson(content, DailyTaskPlan::class.java)
            DailyScriptLibraryBridge.onDailyPlanSelected?.invoke(
                DailyPlanSelection(
                    fileName = entry.name,
                    jsonContent = content,
                    templateDirPath = entry.templateDirPath
                )
            )
            Toast.makeText(this, "已选择 ${entry.name}", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "脚本解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDailyPlanPreview(entry: LibraryEntry) {
        try {
            val content = readEntryText(entry)
            gson.fromJson(content, DailyTaskPlan::class.java)
            startActivity(
                Intent(this, RecordedDailyScriptViewerActivity::class.java).apply {
                    putExtra(RecordedDailyScriptViewerActivity.EXTRA_SCRIPT_NAME, entry.name)
                    putExtra(RecordedDailyScriptViewerActivity.EXTRA_SCRIPT_JSON, content)
                    putExtra(RecordedDailyScriptViewerActivity.EXTRA_TEMPLATE_DIR_PATH, entry.templateDirPath)
                    putExtra(RecordedDailyScriptViewerActivity.EXTRA_SCRIPT_FILE_PATH, entry.file?.absolutePath)
                    putExtra(RecordedDailyScriptViewerActivity.EXTRA_EDITABLE, entry.bundle != null)
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "预览失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLegacyScriptPreview(entry: LibraryEntry) {
        try {
            val rawContent = readEntryText(entry)
            val scriptObj = gson.fromJson(rawContent, LocalScriptJson::class.java)
            val scrollView = ScrollView(this)
            val content = TextView(this).apply {
                setPadding(40, 40, 40, 40)
                setTextColor(Color.BLACK)
                textSize = 14f
                text = buildString {
                    appendLine("标题: ${scriptObj.title ?: entry.name}")
                    appendLine()
                    appendLine("脚本内容:")
                    appendLine(scriptObj.scriptContent)
                    appendLine()
                    appendLine("附加指令:")
                    if (scriptObj.instructions.isNullOrEmpty()) {
                        append("无")
                    } else {
                        scriptObj.instructions.forEach { ins ->
                            appendLine("${ins.type} | T${ins.turn} | step ${ins.step} | value ${ins.value}")
                        }
                    }
                }
            }
            scrollView.addView(content)

            val builder = AlertDialog.Builder(this)
                .setTitle(scriptObj.title ?: entry.name)
                .setView(scrollView)
                .setPositiveButton("导出") { _, _ ->
                    showLegacyScriptExportDialog(scriptObj.title ?: entry.name, rawContent)
                }
                .setNegativeButton("关闭", null)

            if (entry.source == EntrySource.FILE_SYSTEM) {
                builder.setNeutralButton("删除") { _, _ ->
                    entry.file?.delete()
                    recreate()
                }
            }

            builder.show()
        } catch (e: Exception) {
            Toast.makeText(this, "预览失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readEntryText(entry: LibraryEntry): String {
        return when (entry.source) {
            EntrySource.FILE_SYSTEM -> entry.file?.readText(Charsets.UTF_8)
                ?: error("脚本文件不存在")

            EntrySource.ASSET -> readAssetText(entry.assetPath ?: error("缺少资源路径"))
        }
    }

    private fun readAssetText(assetPath: String): String {
        return assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun showLegacyScriptExportDialog(scriptName: String, rawContent: String) {
        val options = arrayOf("导出到录制悬浮窗", "导出到跟打悬浮窗")
        AlertDialog.Builder(this)
            .setTitle(scriptName)
            .setItems(options) { _, which ->
                val targetMode = if (which == 0) "record" else "follow"
                exportLegacyScriptToCombatWindow(rawContent, targetMode)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportLegacyScriptToCombatWindow(rawContent: String, targetMode: String) {
        CombatScriptExportBridge.pendingRequest = PendingCombatScriptExport(
            targetMode = targetMode,
            scriptJson = rawContent
        )
        getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_COMBAT_WINDOW)
            .apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        startService(Intent(this, YuanAssistService::class.java).apply {
            action = ACTION_START_COMBAT_WINDOW
        })
        Toast.makeText(
            this,
            if (targetMode == "record") "正在导出到录制悬浮窗" else "正在导出到跟打悬浮窗",
            Toast.LENGTH_SHORT
        ).show()
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
}
