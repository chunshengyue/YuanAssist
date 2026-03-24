package com.example.yuanassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.utils.RunLogger

class RunLogActivity : AppCompatActivity() {

    companion object {
        private const val SCHEDULE_PREFIX = "\u8c03\u5ea6\u4efb\u52a1 "
        private const val TASK_END_PREFIX = "\u4efb\u52a1 "
        private const val TASK_END_SEPARATOR = "\u7ed3\u675f\uff1a"
        private const val SUCCESS_MARKER = "\u6267\u884c\u6210\u529f"
        private const val FAILURE_MARKER = "\u6267\u884c\u5931\u8d25"
        private const val COOLDOWN_MARKER = "\u51b7\u5374"
        private const val EXHAUSTED_MARKER = "\u5df2\u8017\u5c3d"
        private const val SWITCHED_MARKER = "\u5207\u6362\u5230\u4e0b\u4e00\u4e2a\u4efb\u52a1"
        private val LOG_LINE_REGEX = Regex("""^\[([^\]]+)] \[[^\]]+] (.*)$""")
        private val TASK_NAME_MAP = BirdFoodTaskType.values().associate { it.name to it.displayName }
    }

    private data class MutableSection(
        val title: String,
        val round: Int,
        val lines: MutableList<String>,
        var endReason: String? = null
    ) {
        fun toSection(): RunLogSection {
            val finalReason = endReason ?: "\u672c\u6bb5\u65e5\u5fd7\u7ed3\u675f"
            val contentLines = lines.toMutableList()
            val endMarker = "\u3010${title}\u7b2c${round}\u8f6e\u7ed3\u675f\uff1a$finalReason\u3011"
            if (contentLines.lastOrNull() != endMarker) {
                contentLines += endMarker
            }
            return RunLogSection(
                title = "$title \u7b2c$round\u8f6e",
                subtitle = finalReason,
                content = contentLines.joinToString("\n")
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_log)

        val backButton = findViewById<ImageView>(R.id.btn_back_run_log)
        val emptyView = findViewById<TextView>(R.id.tv_run_log_empty)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_run_log_sections)
        val header = findViewById<View>(R.id.layout_run_log_header)
        val topSpace = findViewById<View>(R.id.view_run_log_status_space)
        val adapter = RunLogSectionAdapter { section ->
            copyText(section.content, "\u8be5\u6bb5\u65e5\u5fd7\u5df2\u590d\u5236")
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topSpace.updateLayoutParams {
                height = statusBarTop / 2
            }
            insets
        }
        ViewCompat.requestApplyInsets(header)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val rawLogs = RunLogger.getAllLogs()
        if (rawLogs.isBlank()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.VISIBLE
        emptyView.text = "\u65e5\u5fd7\u6574\u7406\u4e2d..."
        recyclerView.visibility = View.GONE

        Thread {
            val sections = buildSections(rawLogs)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (sections.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "\u6682\u65e0\u65e5\u5fd7\u8bb0\u5f55"
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(sections)
                }
            }
        }.start()
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
                title = "\u5176\u4ed6\u65e5\u5fd7",
                subtitle = "\u672a\u5f52\u5230\u7279\u5b9a\u4efb\u52a1",
                content = looseLines.joinToString("\n"),
                expanded = false
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
                    lines = mutableListOf(line)
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
            currentSection?.endReason = "\u672c\u6bb5\u65e5\u5fd7\u7ed3\u675f"
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
            message.contains(FAILURE_MARKER) -> "\u5931\u8d25"
            message.contains(COOLDOWN_MARKER) -> "\u51b7\u5374\u4e2d"
            message.contains(EXHAUSTED_MARKER) -> "\u5df2\u8017\u5c3d"
            message.endsWith(SUCCESS_MARKER) -> "\u5df2\u5b8c\u6210"
            message.startsWith(TASK_END_PREFIX) && message.contains(TASK_END_SEPARATOR) -> {
                message.substringAfter(TASK_END_SEPARATOR).trim().ifBlank {
                    "\u5df2\u7ed3\u675f"
                }
            }
            else -> "\u5df2\u7ed3\u675f"
        }
    }

    private fun copyText(content: String, toastText: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("run-log", content))
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }
}
