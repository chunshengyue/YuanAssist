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
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.ui.subpage.SubpageEmptyState
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
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
private val SCOPED_MESSAGE_REGEX = Regex("""^\[([^]/\]]+?)(?:\s*/\s*([^\]]+))?]\s*(.*)$""")

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
                    buildRuntimeSections()
                },
                onCopySection = { section ->
                    copyText(section.content, "该段日志已复制")
                },
                onBack = ::finish,
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
            .let(::mergeWrappedLogLines)
        if (lines.isEmpty()) return emptyList()

        val sections = mutableListOf<RunLogSection>()
        val scopedLines = linkedMapOf<String, MutableList<String>>()
        val roundsByTask = mutableMapOf<String, Int>()
        var currentSection: MutableSection? = null

        fun flushLooseLines() {
            scopedLines.forEach { (key, value) ->
                val keyParts = key.split('\u0001')
                val title = keyParts.firstOrNull().orEmpty().ifBlank { "其他日志" }
                val subtitle = keyParts.getOrNull(1) ?: defaultSubtitleForModule(title)
                sections += RunLogSection(
                    title = title,
                    subtitle = subtitle,
                    content = value.joinToString("\n"),
                    expanded = false,
                )
            }
            scopedLines.clear()
        }

        fun flushCurrentSection() {
            currentSection?.let { sections += it.toSection() }
            currentSection = null
        }

        lines.forEach { line ->
            val message = extractMessage(line)
            val scoped = parseScopedMessage(message)
            if (scoped != null) {
                flushCurrentSection()
                val key = scoped.title + "\u0001" + scoped.subtitle
                scopedLines.getOrPut(key) { mutableListOf() } += line
                return@forEach
            }
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
                val module = inferModule(message)
                val section = inferSection(module, message)
                val key = module + "\u0001" + section
                scopedLines.getOrPut(key) { mutableListOf() } += line
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

    private fun mergeWrappedLogLines(lines: Sequence<String>): List<String> {
        val merged = mutableListOf<String>()
        lines.forEach { line ->
            if (LOG_LINE_REGEX.matchEntire(line) != null || merged.isEmpty()) {
                merged += line
            } else {
                val previous = merged.removeAt(merged.lastIndex)
                merged += "$previous\\n${line.trimStart()}"
            }
        }
        return merged
    }

    private data class ParsedScopedMessage(
        val title: String,
        val subtitle: String,
    )

    private fun parseScopedMessage(message: String): ParsedScopedMessage? {
        val match = SCOPED_MESSAGE_REGEX.find(message) ?: return null
        val module = normalizeModuleName(match.groupValues[1])
        val section = match.groupValues.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?: defaultSubtitleForModule(module)
        return ParsedScopedMessage(title = module, subtitle = section)
    }

    private fun inferModule(message: String): String {
        return when {
            message.contains("鸟食") || message.contains("鸢报界面") ||
                message.contains("突发情况") || message.contains("小道消息") ||
                message.contains("他的传闻") || message.contains("待办公务") -> "刷鸟食"
            message.contains("6-24") || message.contains("624") -> "刷6-24"
            message.contains("无月卡观星") -> "无月卡观星"
            message.contains("作业站") || message.contains("MaaYuan") ||
                message.contains("攻略详情") || message.contains("神秘代码") -> "作业站"
            message.contains("云端脚本发布") -> "云端脚本发布"
            message.contains("角色导入") || message.contains("号位") ||
                message.contains("命盘") || message.contains("练度") -> "角色导入"
            message.contains("星石") || message.contains("本地OCR返回原文本") -> "星石 OCR"
            else -> "其他日志"
        }
    }

    private fun inferSection(module: String, message: String): String {
        return when (module) {
            "刷鸟食" -> when {
                message.contains("突发情况") -> "突发情况"
                message.contains("小道消息") -> "小道消息"
                message.contains("他的传闻") -> "他的传闻"
                message.contains("待办公务") -> "待办公务"
                message.contains("冷却") -> "冷却"
                else -> "导航与调度"
            }
            "刷6-24" -> when {
                message.contains("进图") -> "进图流程"
                message.contains("第") && message.contains("轮") -> "战斗轮次"
                else -> "总流程"
            }
            "无月卡观星" -> if (message.contains("批")) "批次" else "总流程"
            "角色导入" -> Regex("""(\d+)号位""").find(message)?.groupValues?.getOrNull(1)?.let { "${it}号位" } ?: "总览"
            "星石 OCR" -> if (message.contains("原文本")) "原文" else "解析结果"
            else -> defaultSubtitleForModule(module)
        }
    }

    private fun normalizeModuleName(value: String): String {
        val text = value.trim()
        return when (text) {
            "6-24", "刷624", "主线624" -> "刷6-24"
            "鸟食" -> "刷鸟食"
            "观星" -> "无月卡观星"
            else -> text.ifBlank { "其他日志" }
        }
    }

    private fun defaultSubtitleForModule(module: String): String {
        return when (module) {
            "其他日志" -> "未归到特定任务"
            else -> "总流程"
        }
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

}

@Composable
private fun RunLogScreen(
    loadSections: () -> List<RunLogSection>,
    onCopySection: (RunLogSection) -> Unit,
    onBack: () -> Unit,
) {
    var sections by remember { mutableStateOf<List<RunLogSection>>(emptyList()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var expandedTitles by rememberSaveable { mutableStateOf(setOf<String>()) }

    fun reload() {
        loading = true
        sections = loadSections().map { section ->
            section.copy(expanded = expandedTitles.contains(sectionKey(section)))
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
                    subtitle = "正在汇总运行记录",
                ) {
                    Text("请稍候…")
                }
            }

            sections.isEmpty() -> {
                SubpageEmptyState(
                    title = "暂无运行日志",
                    subtitle = "当前没有可展示的运行记录。",
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
                                    val key = sectionKey(section)
                                    expandedTitles = if (expandedTitles.contains(key)) {
                                        expandedTitles - key
                                    } else {
                                        expandedTitles + key
                                    }
                                    sections = sections.map {
                                        if (sectionKey(it) == key) {
                                            it.copy(expanded = !it.expanded)
                                        } else {
                                            it
                                        }
                                    }
                                },
                                onCopy = { onCopySection(section) },
                                onDelete = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sectionKey(section: RunLogSection): String = "${section.title}\u0001${section.subtitle}"

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
        message.contains("OCR") -> Color(0xFF2D8C88)
        message.startsWith("[调试诊断]") -> Color(0xFF7D7D7D)
        line.startsWith("【") -> Color(0xFF9A6435)
        else -> Color(0xFF8A6B5E)
    }
}
