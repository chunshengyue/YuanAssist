package com.example.yuanassist.ui

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.core.DailyScriptLibraryBridge
import com.example.yuanassist.core.LocalScriptJson
import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File

class ScriptLibraryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "pick_mode"
        const val PICK_MODE_DAILY_PLAN = "daily_plan"
        private const val DAILY_ASSET_DIR = "daily_scripts"
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
        val assetPath: String? = null
    )

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entries = loadEntries()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = if (isDailyPickerMode()) "选择日常脚本" else "本地脚本库"
            textSize = 24f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 30)
        }
        root.addView(title)

        val listView = ListView(this)
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)

        if (entries.isEmpty()) {
            Toast.makeText(this, "脚本库为空", Toast.LENGTH_SHORT).show()
            return
        }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            entries.map { it.displayName }
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = entries[position]
            if (isDailyPickerMode()) {
                selectDailyPlan(entry)
                return@setOnItemClickListener
            }

            when (entry.type) {
                EntryType.LEGACY_SCRIPT -> showLegacyScriptPreview(entry)
                EntryType.DAILY_PLAN -> showDailyPlanPreview(entry)
            }
        }
    }

    private fun isDailyPickerMode(): Boolean {
        return intent.getStringExtra(EXTRA_PICK_MODE) == PICK_MODE_DAILY_PLAN
    }

    private fun loadEntries(): List<LibraryEntry> {
        val dailyEntries = loadDailyAssetEntries()
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
                file = file
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
                assetPath = assetPath
            )
        }
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
            DailyScriptLibraryBridge.onDailyPlanSelected?.invoke(entry.name, content)
            Toast.makeText(this, "已选择 ${entry.name}", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "脚本解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDailyPlanPreview(entry: LibraryEntry) {
        try {
            val plan = gson.fromJson(readEntryText(entry), DailyTaskPlan::class.java)
            val summary = buildString {
                appendLine("起始任务: ${plan.start_task_id}")
                appendLine("任务总数: ${plan.tasks.size}")
                appendLine()
                plan.tasks.forEach { task ->
                    appendLine(
                        "ID ${task.id} | ${task.action} | success -> ${task.on_success} | fail -> ${task.on_fail}"
                    )
                }
            }

            AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setMessage(summary.trim())
                .setPositiveButton("关闭", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "预览失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLegacyScriptPreview(entry: LibraryEntry) {
        try {
            val scriptObj = gson.fromJson(readEntryText(entry), LocalScriptJson::class.java)
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
                .setPositiveButton("关闭", null)

            if (entry.source == EntrySource.FILE_SYSTEM) {
                builder.setNegativeButton("删除") { _, _ ->
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
            EntrySource.FILE_SYSTEM -> entry.file?.readText()
                ?: error("脚本文件不存在")

            EntrySource.ASSET -> readAssetText(entry.assetPath ?: error("缺少资源路径"))
        }
    }

    private fun readAssetText(assetPath: String): String {
        return assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
