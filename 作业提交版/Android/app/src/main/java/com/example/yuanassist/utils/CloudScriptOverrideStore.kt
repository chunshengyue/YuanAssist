package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

data class CloudScriptOverrideBundle(
    val targetScriptName: String,
    val rootDir: File,
    val scriptFile: File,
    val title: String,
    val updatedAt: Long
)

object CloudScriptOverrideStore {
    private const val ROOT_DIR_NAME = "cloud_script_overrides"
    private const val SCRIPT_FILE_NAME = "script.json"
    private const val META_FILE_NAME = "meta.properties"

    fun rootDir(context: Context): File =
        File(context.filesDir, ROOT_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun saveOverride(
        context: Context,
        targetScriptName: String,
        plan: DailyTaskPlan,
        title: String,
        gson: Gson = Gson()
    ): CloudScriptOverrideBundle {
        val normalizedTarget = normalizeTargetScriptName(targetScriptName)
        require(normalizedTarget.isNotBlank()) { "覆盖目标脚本名不能为空" }
        val targetDir = File(rootDir(context), encodeTargetDirName(normalizedTarget))
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val scriptFile = File(targetDir, SCRIPT_FILE_NAME)
        scriptFile.writeText(gson.toJson(plan), Charsets.UTF_8)
        File(targetDir, META_FILE_NAME).writeText(
            buildString {
                appendLine("target=$normalizedTarget")
                appendLine("title=${title.replace("\n", " ").replace("\r", " ").trim()}")
                appendLine("updatedAt=${System.currentTimeMillis()}")
            },
            Charsets.UTF_8
        )
        return CloudScriptOverrideBundle(
            targetScriptName = normalizedTarget,
            rootDir = targetDir,
            scriptFile = scriptFile,
            title = title,
            updatedAt = scriptFile.lastModified()
        )
    }

    fun listOverrides(context: Context): List<CloudScriptOverrideBundle> {
        return rootDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val scriptFile = File(dir, SCRIPT_FILE_NAME)
                if (!scriptFile.exists()) return@mapNotNull null
                val meta = readMeta(File(dir, META_FILE_NAME))
                val target = normalizeTargetScriptName(meta["target"].orEmpty())
                    .ifBlank { decodeTargetDirName(dir.name) }
                if (target.isBlank()) return@mapNotNull null
                CloudScriptOverrideBundle(
                    targetScriptName = target,
                    rootDir = dir,
                    scriptFile = scriptFile,
                    title = meta["title"].orEmpty().ifBlank { target.removeSuffix(".json") },
                    updatedAt = scriptFile.lastModified()
                )
            }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            ?: emptyList()
    }

    fun loadPlan(bundle: CloudScriptOverrideBundle, gson: Gson = Gson()): DailyTaskPlan {
        return gson.fromJson(bundle.scriptFile.readText(Charsets.UTF_8), DailyTaskPlan::class.java)
    }

    fun deleteOverride(bundle: CloudScriptOverrideBundle): Boolean {
        return !bundle.rootDir.exists() || bundle.rootDir.deleteRecursively()
    }

    fun loadAssetPlanWithOverride(
        context: Context,
        assetScriptName: String,
        gson: Gson = Gson()
    ): DailyTaskPlan {
        val normalizedTarget = normalizeTargetScriptName(assetScriptName)
        val override = listOverrides(context).firstOrNull { it.targetScriptName == normalizedTarget }
        if (override != null) {
            return loadPlan(override, gson)
        }
        return context.assets.open("daily_scripts/$normalizedTarget").use { input ->
            gson.fromJson(InputStreamReader(input, Charsets.UTF_8), DailyTaskPlan::class.java)
        }
    }

    fun normalizeTargetScriptName(value: String): String {
        val trimmed = value.trim().replace('\\', '/').substringAfterLast('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.lowercase(Locale.ROOT).endsWith(".json")) trimmed else "$trimmed.json"
    }

    private fun encodeTargetDirName(targetScriptName: String): String {
        return targetScriptName.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
    }

    private fun decodeTargetDirName(dirName: String): String {
        return normalizeTargetScriptName(dirName)
    }

    private fun readMeta(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return file.readLines(Charsets.UTF_8)
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
    }
}
