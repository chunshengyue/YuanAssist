package com.example.yuanassist.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserDailyScriptBundle(
    val scriptId: String,
    val rootDir: File,
    val scriptFile: File,
    val templatesDir: File
)

object UserDailyScriptStore {

    private const val ROOT_DIR_NAME = "user_daily_scripts"
    private const val SCRIPT_FILE_NAME = "script.json"
    private const val PUBLIC_EXPORT_ROOT_NAME = "YuanAssist/user_daily_scripts"
    private val defaultNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun rootDir(context: Context): File =
        File(context.filesDir, ROOT_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun createBundle(context: Context, requestedName: String?): UserDailyScriptBundle {
        val baseName = sanitizeScriptName(requestedName)
            .ifBlank { "script_${defaultNameFormat.format(Date())}" }
        var finalName = baseName
        var suffix = 2
        while (File(rootDir(context), finalName).exists()) {
            finalName = "${baseName}_$suffix"
            suffix += 1
        }
        val root = File(rootDir(context), finalName).apply { mkdirs() }
        val templatesDir = File(root, "templates").apply { mkdirs() }
        return UserDailyScriptBundle(
            scriptId = finalName,
            rootDir = root,
            scriptFile = File(root, SCRIPT_FILE_NAME),
            templatesDir = templatesDir
        )
    }

    fun listBundles(context: Context): List<UserDailyScriptBundle> {
        return rootDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val scriptFile = File(dir, SCRIPT_FILE_NAME)
                if (!scriptFile.exists()) return@mapNotNull null
                UserDailyScriptBundle(
                    scriptId = dir.name,
                    rootDir = dir,
                    scriptFile = scriptFile,
                    templatesDir = File(dir, "templates")
                )
            }
            ?.sortedByDescending { it.scriptFile.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    fun savePlan(bundle: UserDailyScriptBundle, plan: DailyTaskPlan, gson: Gson = Gson()) {
        bundle.rootDir.mkdirs()
        bundle.templatesDir.mkdirs()
        bundle.scriptFile.writeText(gson.toJson(plan), Charsets.UTF_8)
    }

    fun exportBundleToPublicDownloads(context: Context, bundle: UserDailyScriptBundle): String {
        val relativeScriptDir = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_EXPORT_ROOT_NAME/${bundle.scriptId}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportFileToMediaStore(
                context = context,
                sourceFile = bundle.scriptFile,
                relativePath = relativeScriptDir,
                mimeType = "application/json"
            )
            bundle.templatesDir
                .listFiles()
                ?.filter { it.isFile }
                ?.forEach { templateFile ->
                    exportFileToMediaStore(
                        context = context,
                        sourceFile = templateFile,
                        relativePath = "$relativeScriptDir/templates",
                        mimeType = guessMimeType(templateFile.name)
                    )
                }
        } else {
            exportBundleToLegacyPublicDownloads(bundle)
        }
        return "$relativeScriptDir/"
    }

    fun loadPlan(bundle: UserDailyScriptBundle, gson: Gson = Gson()): DailyTaskPlan {
        return gson.fromJson(bundle.scriptFile.readText(Charsets.UTF_8), DailyTaskPlan::class.java)
    }

    fun syncTemplateFilesForPlanUpdate(
        bundle: UserDailyScriptBundle,
        oldPlan: DailyTaskPlan,
        newPlan: DailyTaskPlan
    ) {
        val oldTasksById = oldPlan.tasks.associateBy { it.id }
        val newTasksById = newPlan.tasks.associateBy { it.id }
        val oldRefCount = oldPlan.tasks
            .mapNotNull { it.params?.template_name }
            .groupingBy { it }
            .eachCount()
            .toMutableMap()
        val newRefCount = newPlan.tasks
            .mapNotNull { it.params?.template_name }
            .groupingBy { it }
            .eachCount()

        oldTasksById.forEach { (taskId, oldTask) ->
            val newTask = newTasksById[taskId] ?: return@forEach
            if (oldTask.action != "MATCH_TEMPLATE" || newTask.action != "MATCH_TEMPLATE") return@forEach
            val oldTemplateName = normalizeTemplateFileName(oldTask.params?.template_name)
            val newTemplateName = normalizeTemplateFileName(newTask.params?.template_name)
            if (oldTemplateName.isBlank() || newTemplateName.isBlank() || oldTemplateName == newTemplateName) return@forEach

            val oldFile = File(bundle.templatesDir, oldTemplateName)
            if (!oldFile.exists()) return@forEach
            val newFile = File(bundle.templatesDir, newTemplateName)
            if (newFile.exists()) {
                throw IllegalStateException("模板文件已存在：$newTemplateName")
            }

            val remainingOldRefs = newRefCount[oldTemplateName] ?: 0
            if (remainingOldRefs <= 0) {
                if (!oldFile.renameTo(newFile)) {
                    throw IllegalStateException("模板改名失败：$oldTemplateName -> $newTemplateName")
                }
            } else {
                oldFile.copyTo(newFile, overwrite = false)
            }
            val oldCount = oldRefCount[oldTemplateName] ?: 0
            oldRefCount[oldTemplateName] = (oldCount - 1).coerceAtLeast(0)
        }
    }

    fun deleteBundle(bundle: UserDailyScriptBundle): Boolean {
        return !bundle.rootDir.exists() || bundle.rootDir.deleteRecursively()
    }

    fun sanitizeScriptName(value: String?): String {
        return value
            ?.trim()
            .orEmpty()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
    }

    private fun exportBundleToLegacyPublicDownloads(bundle: UserDailyScriptBundle) {
        val downloadRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetRoot = File(downloadRoot, "$PUBLIC_EXPORT_ROOT_NAME/${bundle.scriptId}")
        if (targetRoot.exists()) {
            targetRoot.deleteRecursively()
        }
        targetRoot.mkdirs()
        bundle.scriptFile.copyTo(File(targetRoot, SCRIPT_FILE_NAME), overwrite = true)
        val targetTemplatesDir = File(targetRoot, "templates").apply { mkdirs() }
        bundle.templatesDir
            .listFiles()
            ?.filter { it.isFile }
            ?.forEach { templateFile ->
                templateFile.copyTo(File(targetTemplatesDir, templateFile.name), overwrite = true)
            }
    }

    private fun exportFileToMediaStore(
        context: Context,
        sourceFile: File,
        relativePath: String,
        mimeType: String
    ) {
        val resolver = context.contentResolver
        val normalizedPath = normalizeRelativePath(relativePath)
        deleteExistingMediaStoreFile(resolver, normalizedPath, sourceFile.name)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建下载目录文件：${sourceFile.name}")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                if (output is FileOutputStream) {
                    output.fd.sync()
                }
            } ?: error("无法打开下载目录输出流：${sourceFile.name}")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun deleteExistingMediaStoreFile(
        resolver: android.content.ContentResolver,
        relativePath: String,
        displayName: String
    ) {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(displayName, relativePath)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idIndex)
                )
                resolver.delete(uri, null, null)
            }
        }
    }

    private fun normalizeRelativePath(relativePath: String): String {
        return relativePath.trim('/').let {
            if (it.endsWith("/")) it else "$it/"
        }
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun normalizeTemplateFileName(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return ""
        return if (normalized.lowercase(Locale.ROOT).endsWith(".png")) normalized else "$normalized.png"
    }
}
