package com.example.yuanassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.appcompat.app.AlertDialog
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.ui.subpage.SubpageEmptyState
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.ExceptionLogStore
import com.example.yuanassist.utils.RunLogger

private data class RunLogSection(
    val title: String,
    val subtitle: String,
    val content: String,
    val expanded: Boolean = false,
    val entryId: String? = null,
    val canDelete: Boolean = false,
)

private val RUN_LOG_LINE_REGEX = Regex("""^\[([^\]]+)] \[[^\]]+] (.*)$""")

class RunLogActivity : AppCompatActivity() {

    companion object {
        private const val SCHEDULE_PREFIX = "调度任务 "
        private const val TASK_END_PREFIX = "任务 "
        private const val TASK_END_SEPARATOR = "结束："
        private const val SUCCESS_MARKER = "执行成功"
        private const val FAILURE_MARKER = "执行失败"
        private const val COOLDOWN_MARKER = "冷却"
        private const val EXHAUSTED_MARKER = "已耗尽"
        private const val SWITCHED_MARKER = "切换到下一个任务"
        private val LOG_LINE_REGEX = Regex("""^\[([^\]]+)] \[[^\]]+] (.*)$""")
        private val TASK_NAME_MAP = BirdFoodTaskType.values().associate { it.name to it.displayName }
    }

    private data class MutableSection(
        val title: String,
        val round: Int,
        val lines: MutableList<String>,
        var endReason: String? = null,
    ) {
        fun toSection(): RunLogSection {
            val finalReason = endReason ?: "本段日志结束"
            val contentLines = lines.toMutableList()
            val endMarker = "【${title}第${round}轮结束：$finalReason】"
            if (contentLines.lastOrNull() != endMarker) {
                contentLines += endMarker
            }
            return RunLogSection(
                title = "${title} 第${round}轮",
                subtitle = finalReason,
                content = contentLines.joinToString("\n"),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RunLogScreen(
                loadSections = {
                    buildPersistedSections() + buildRuntimeSections()
                },
                onCopySection = { section ->
                    copyText(section.content, "该段日志已复制")
                },
                onDeleteSection = { section, onDeleted ->
                    confirmDeleteSection(section, onDeleted)
                },
                onBack = ::finish,
            )
        }
    }

    private fun buildPersistedSections(): List<RunLogSection> {
        return ExceptionLogStore.loadEntries(this).map { entry ->
            RunLogSection(
                title = if (entry.title == "异常处理") "无障碍权限相关" else entry.title,
                subtitle = entry.subtitle
                    .replace("系统权限与服务异常", "无障碍权限相关")
                    .replace("异常处理", "无障碍权限相关"),
                content = entry.content.replace(
                    "【异常处理】",
                    "【无障碍权限相关】",
                ),
                entryId = entry.id,
                canDelete = true,
            )
        }
    }

    private fun buildRuntimeSections(): List<RunLogSection> {
        val rawLogs = RunLogger.getAllLogs()
        if (rawLogs.isBlank()) return emptyList()
        return buildSections(rawLogs)
    }

    private fun buildSections(rawLogs: String): List<RunLogSection> {
        val lines = rawLogs.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val sections = mutableListOf<RunLogSection>()
        val looseLines = mutableListOf<String>()
        val roundsByTask = mutableMapOf<String, Int>()
        var currentSection: MutableSection? = null

        fun flushLooseLines() {
            if (looseLines.isEmpty()) return
            sections += RunLogSection(
                title = "其他日志",
                subtitle = "未归到特定任务",
                content = looseLines.joinToString("\n"),
                expanded = false,
            )
            looseLines.clear()
        }

        fun flushCurrentSection() {
            currentSection?.let { sections += it.toSection() }
            currentSection = null
        }

        lines.forEach { line ->
            val message = extractMessage(line)
            val startedTask = extractStartedTask(message)

            if (startedTask != null) {
                if (currentSection != null && currentSection?.endReason == null) {
                    currentSection?.endReason = SWITCHED_MARKER
                }
                flushCurrentSection()
                flushLooseLines()

                val round = (roundsByTask[startedTask] ?: 0) + 1
                roundsByTask[startedTask] = round
                currentSection = MutableSection(
                    title = startedTask,
                    round = round,
                    lines = mutableListOf(line),
                )
                return@forEach
            }

            if (currentSection == null) {
                looseLines += line
                return@forEach
            }

            currentSection?.lines?.add(line)

            if (currentSection?.endReason == null && isTaskTerminalLine(message)) {
                currentSection?.endReason = deriveStatusText(message)
            }
        }

        if (currentSection != null && currentSection?.endReason == null) {
            currentSection?.endReason = "本段日志结束"
        }

        flushCurrentSection()
        flushLooseLines()
        return sections
    }

    private fun extractStartedTask(message: String): String? {
        if (message.startsWith(SCHEDULE_PREFIX)) {
            val taskName = message.removePrefix(SCHEDULE_PREFIX).trim()
            return if (taskName.isBlank()) null else normalizeTaskName(taskName)
        }
        return null
    }

    private fun normalizeTaskName(taskName: String): String {
        return TASK_NAME_MAP[taskName] ?: taskName
    }

    private fun extractMessage(line: String): String {
        val match = LOG_LINE_REGEX.matchEntire(line) ?: return line
        return match.groupValues[2]
    }

    private fun isTaskTerminalLine(message: String): Boolean {
        return (message.startsWith(TASK_END_PREFIX) && message.contains(TASK_END_SEPARATOR)) ||
            message.endsWith(SUCCESS_MARKER) ||
            message.contains(FAILURE_MARKER) ||
            message.contains(COOLDOWN_MARKER) ||
            message.contains(EXHAUSTED_MARKER)
    }

    private fun deriveStatusText(message: String): String {
        return when {
            message.contains(FAILURE_MARKER) -> "失败"
            message.contains(COOLDOWN_MARKER) -> "冷却中"
            message.contains(EXHAUSTED_MARKER) -> "已耗尽"
            message.endsWith(SUCCESS_MARKER) -> "已完成"
            message.startsWith(TASK_END_PREFIX) && message.contains(TASK_END_SEPARATOR) -> {
                message.substringAfter(TASK_END_SEPARATOR).trim().ifBlank {
                    "已结束"
                }
            }

            else -> "已结束"
        }
    }

    private fun copyText(content: String, toastText: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("run-log", content))
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSection(
        section: RunLogSection,
        onDeleted: () -> Unit,
    ) {
        val entryId = section.entryId ?: return
        AlertDialog.Builder(this)
            .setTitle("删除无障碍权限日志")
            .setMessage("这条“无障碍权限相关”日志删除后不会自动恢复，是否继续？")
            .setPositiveButton("删除") { _, _ ->
                val deleted = ExceptionLogStore.deleteEntry(this, entryId)
                if (deleted) {
                    Toast.makeText(this, "已删除无障碍权限日志", Toast.LENGTH_SHORT).show()
                    onDeleted()
                } else {
                    Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

@Composable
private fun RunLogScreen(
    loadSections: () -> List<RunLogSection>,
    onCopySection: (RunLogSection) -> Unit,
    onDeleteSection: (RunLogSection, () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var sections by remember { mutableStateOf<List<RunLogSection>>(emptyList()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var expandedTitles by rememberSaveable { mutableStateOf(setOf<String>()) }

    fun reload() {
        loading = true
        sections = loadSections().map { section ->
            section.copy(expanded = expandedTitles.contains(section.title))
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        reload()
    }

    SubpageScaffold(
        title = "运行日志",
        subtitle = "任务分段 · 展开查看 · 复制留档",
        onBack = onBack,
        scrollable = false,
    ) {
        when {
            loading -> {
                SubpageSectionCard(
                    title = "日志整理中",
                    subtitle = "正在汇总运行记录与权限异常记录",
                ) {
                    Text("请稍候…")
                }
            }

            sections.isEmpty() -> {
                SubpageEmptyState(
                    title = "暂无运行日志",
                    subtitle = "当前没有可展示的运行记录和无障碍权限异常记录。",
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sections, key = { it.title + it.subtitle + (it.entryId ?: "") }) { section ->
                        SubpageSectionCard {
                            RunLogEntry(
                                section = section,
                                onToggle = {
                                    expandedTitles = if (expandedTitles.contains(section.title)) {
                                        expandedTitles - section.title
                                    } else {
                                        expandedTitles + section.title
                                    }
                                    sections = sections.map {
                                        if (it.title == section.title && it.subtitle == section.subtitle) {
                                            it.copy(expanded = !it.expanded)
                                        } else {
                                            it
                                        }
                                    }
                                },
                                onCopy = { onCopySection(section) },
                                onDelete = {
                                    onDeleteSection(section) {
                                        expandedTitles = expandedTitles - section.title
                                        reload()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunLogEntry(
    section: RunLogSection,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = section.title,
                    color = Color(0xFF75322D),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    text = section.subtitle,
                    color = Color(0xFF8A6B5E),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
            Text(
                text = if (section.expanded) "收起" else "展开",
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    )
                    .padding(start = 12.dp),
                color = Color(0xFF9A6435),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "复制",
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCopy,
                ),
                color = Color(0xFF9A6435),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
            )
            if (section.canDelete) {
                Text(
                    text = "删除",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
                    color = Color(0xFFB84D4D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Serif,
                )
            }
        }

        if (section.expanded) {
            val coloredContent = remember(section.content) {
                buildColoredRunLogText(section.content)
            }
            Text(
                text = coloredContent,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

private fun buildColoredRunLogText(content: String) = buildAnnotatedString {
    val lines = content.lines()
    lines.forEachIndexed { index, line ->
        withStyle(SpanStyle(color = colorForRunLogLine(line))) {
            append(line)
        }
        if (index != lines.lastIndex) append("\n")
    }
}

private fun colorForRunLogLine(line: String): Color {
    val message = RUN_LOG_LINE_REGEX.matchEntire(line)?.groupValues?.getOrNull(2) ?: line
    return when {
        line.contains("[E]") -> Color(0xFFB84D4D)
        message.startsWith("[披荆神秘]") -> Color(0xFFC57A2D)
        message.startsWith("[披荆答题]") -> Color(0xFF3F6EA8)
        message.startsWith("[披荆OCR]") -> Color(0xFF2D8C88)
        message.startsWith("[披荆流程]") -> Color(0xFF8A5A3C)
        message.startsWith("[调试诊断]") -> Color(0xFF7D7D7D)
        line.startsWith("【") -> Color(0xFF9A6435)
        else -> Color(0xFF8A6B5E)
    }
}
