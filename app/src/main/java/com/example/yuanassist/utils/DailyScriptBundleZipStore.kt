package com.example.yuanassist.utils

import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PackedDailyScriptBundle(
    val file: File,
    val taskCount: Int,
    val sizeBytes: Long,
)

object DailyScriptBundleZipStore {
    private const val SCRIPT_FILE_NAME = "script.json"
    private const val TEMPLATES_DIR_NAME = "templates"

    fun packBundle(cacheDir: File, bundle: UserDailyScriptBundle, gson: Gson = Gson()): PackedDailyScriptBundle {
        val plan = UserDailyScriptStore.loadPlan(bundle, gson)
        cacheDir.mkdirs()
        val target = File(cacheDir, "daily_script_${bundle.scriptId}_${System.currentTimeMillis()}.zip")
        if (target.exists()) target.delete()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            addFile(zip, bundle.scriptFile, SCRIPT_FILE_NAME)
            bundle.templatesDir
                .listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { templateFile ->
                    addFile(zip, templateFile, "$TEMPLATES_DIR_NAME/${templateFile.name}")
                }
        }
        return PackedDailyScriptBundle(
            file = target,
            taskCount = plan.tasks.size,
            sizeBytes = target.length(),
        )
    }

    fun unpackToBundle(zipFile: File, bundle: UserDailyScriptBundle, gson: Gson = Gson()): DailyTaskPlan {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IllegalArgumentException("脚本压缩包不存在")
        }
        if (bundle.rootDir.exists()) {
            bundle.rootDir.deleteRecursively()
        }
        bundle.rootDir.mkdirs()
        bundle.templatesDir.mkdirs()
        val rootPath = bundle.rootDir.canonicalFile.toPath()

        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory) {
                    val target = File(bundle.rootDir, entry.name)
                    val targetPath = target.canonicalFile.toPath()
                    if (!targetPath.startsWith(rootPath)) {
                        throw IllegalArgumentException("非法压缩包路径：${entry.name}")
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }

        if (!bundle.scriptFile.exists()) {
            throw IllegalArgumentException("压缩包缺少 script.json")
        }
        return UserDailyScriptStore.loadPlan(bundle, gson)
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}
