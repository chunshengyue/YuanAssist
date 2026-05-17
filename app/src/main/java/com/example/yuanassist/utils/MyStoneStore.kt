package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

data class MyStoneImageEntry(
    val fileName: String,
    val width: Int,
    val height: Int
)

data class MyStoneCell(
    var level: String = "",
    var name: String = ""
)

data class MyStoneRow(
    val cells: MutableList<MyStoneCell> = mutableListOf()
)

data class MyStoneRecord(
    val stoneType: String = MyStoneStore.TYPE_MAIN,
    val updatedAt: Long,
    val images: List<MyStoneImageEntry>,
    val statsLines: List<String> = emptyList(),
    val rows: List<MyStoneRow> = emptyList(),
    val ocrStrategy: String? = null
)

data class MyStoneArchive(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

private data class MyStoneArchiveMeta(
    val archives: List<MyStoneArchive> = emptyList()
)

object MyStoneStore {

    private const val DIR_NAME = "my_stones"
    private const val SLOTS_DIR_NAME = "slots"
    private const val ARCHIVES_FILE_NAME = "archives.json"
    private const val RECORD_FILE_NAME = "record.json"
    private const val LEGACY_RECORD_FILE_NAME = "record.json"
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SELECTED_STONE_TYPE = "selected_stone_type"
    private const val KEY_SELECTED_ARCHIVE_ID = "selected_stone_archive_id"
    const val TYPE_MAIN = "main"
    const val TYPE_SUPPORT = "support"
    const val DEFAULT_ARCHIVE_ID = "default"
    const val DEFAULT_ARCHIVE_NAME = "默认存档"
    private val gson = Gson()

    private fun rootDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun legacyTypeDir(context: Context, stoneType: String): File =
        File(rootDir(context), normalizeType(stoneType))

    private fun slotsRootDir(context: Context): File =
        File(rootDir(context), SLOTS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun archiveDir(context: Context, archiveId: String): File =
        File(slotsRootDir(context), archiveId).apply {
            if (!exists()) mkdirs()
        }

    private fun storeDir(context: Context, archiveId: String, stoneType: String): File =
        File(archiveDir(context, archiveId), normalizeType(stoneType)).apply {
            if (!exists()) mkdirs()
        }

    private fun legacyRecordFile(context: Context): File = File(rootDir(context), LEGACY_RECORD_FILE_NAME)

    private fun recordFile(context: Context, archiveId: String, stoneType: String): File =
        File(storeDir(context, archiveId, stoneType), RECORD_FILE_NAME)

    private fun archivesFile(context: Context): File =
        File(rootDir(context), ARCHIVES_FILE_NAME)

    fun setSelectedType(context: Context, stoneType: String) {
        ensureInitialized(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_STONE_TYPE, normalizeType(stoneType))
            .apply()
    }

    fun getSelectedType(context: Context): String {
        ensureInitialized(context)
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_STONE_TYPE, TYPE_MAIN)
        return normalizeType(stored)
    }

    fun setSelectedArchiveId(context: Context, archiveId: String) {
        ensureInitialized(context)
        val archives = listArchives(context)
        val selected = archiveId.takeIf { target -> archives.any { it.id == target } }
            ?: archives.firstOrNull()?.id
            ?: DEFAULT_ARCHIVE_ID
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_ARCHIVE_ID, selected)
            .apply()
    }

    fun getSelectedArchiveId(context: Context): String {
        ensureInitialized(context)
        val archives = listArchives(context)
        val fallbackId = archives.firstOrNull()?.id ?: DEFAULT_ARCHIVE_ID
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ARCHIVE_ID, fallbackId)
        val selected = stored.takeIf { target -> archives.any { it.id == target } } ?: fallbackId
        if (stored != selected) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ARCHIVE_ID, selected)
                .apply()
        }
        return selected
    }

    fun getSelectedArchive(context: Context): MyStoneArchive {
        ensureInitialized(context)
        val selectedId = getSelectedArchiveId(context)
        return listArchives(context).firstOrNull { it.id == selectedId } ?: defaultArchive()
    }

    fun listArchives(context: Context): List<MyStoneArchive> {
        ensureInitialized(context)
        val archives = readArchiveMeta(context).archives
            .filter { it.id.isNotBlank() }
            .sortedBy { it.createdAt }
        return if (archives.isEmpty()) listOf(defaultArchive()) else archives
    }

    fun resolveArchiveId(context: Context, archiveId: String?): String {
        ensureInitialized(context)
        val archives = listArchives(context)
        return archiveId?.takeIf { target -> archives.any { it.id == target } }
            ?: getSelectedArchiveId(context)
    }

    fun createArchive(context: Context, name: String): MyStoneArchive {
        ensureInitialized(context)
        val normalizedName = requireArchiveName(name)
        val currentArchives = listArchives(context)
        if (currentArchives.any { it.name == normalizedName }) {
            throw IllegalArgumentException("已存在同名存档")
        }

        val now = System.currentTimeMillis()
        val archive = MyStoneArchive(
            id = "archive_${now}_${System.nanoTime()}",
            name = normalizedName,
            createdAt = now,
            updatedAt = now
        )
        saveArchiveMeta(context, currentArchives + archive)
        setSelectedArchiveId(context, archive.id)
        return archive
    }

    fun renameArchive(context: Context, archiveId: String, name: String): MyStoneArchive {
        ensureInitialized(context)
        val normalizedName = requireArchiveName(name)
        val currentArchives = listArchives(context)
        if (currentArchives.any { it.id != archiveId && it.name == normalizedName }) {
            throw IllegalArgumentException("已存在同名存档")
        }

        var renamed: MyStoneArchive? = null
        val updatedArchives = currentArchives.map { archive ->
            if (archive.id == archiveId) {
                archive.copy(
                    name = normalizedName,
                    updatedAt = System.currentTimeMillis()
                ).also { renamed = it }
            } else {
                archive
            }
        }
        val result = renamed ?: throw IllegalArgumentException("未找到对应存档")
        saveArchiveMeta(context, updatedArchives)
        return result
    }

    fun normalizeType(stoneType: String?): String =
        when (stoneType) {
            TYPE_SUPPORT -> TYPE_SUPPORT
            else -> TYPE_MAIN
        }

    fun displayName(stoneType: String): String =
        if (normalizeType(stoneType) == TYPE_SUPPORT) "辅星" else "主星"

    fun saveImages(
        context: Context,
        stoneType: String,
        bitmaps: List<Bitmap>,
        archiveId: String = getSelectedArchiveId(context)
    ): MyStoneRecord {
        ensureInitialized(context)
        require(bitmaps.isNotEmpty()) { "至少需要一张图片" }

        val resolvedArchiveId = resolveArchiveId(context, archiveId)
        val normalizedType = normalizeType(stoneType)
        val dir = storeDir(context, resolvedArchiveId, normalizedType)
        clearStore(dir)

        val images = bitmaps.mapIndexed { index, bitmap ->
            val fileName = "stone_result_${index + 1}.png"
            val target = File(dir, fileName)
            FileOutputStream(target).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("保存本地星石图片失败: $fileName")
                }
                output.flush()
            }
            MyStoneImageEntry(
                fileName = fileName,
                width = bitmap.width,
                height = bitmap.height
            )
        }

        val record = MyStoneRecord(
            stoneType = normalizedType,
            updatedAt = System.currentTimeMillis(),
            images = images
        )
        saveRecord(context, resolvedArchiveId, normalizedType, record)
        touchArchive(context, resolvedArchiveId, record.updatedAt)
        setSelectedArchiveId(context, resolvedArchiveId)
        setSelectedType(context, normalizedType)
        return record
    }

    fun saveOcrResult(
        context: Context,
        stoneType: String,
        rows: List<MyStoneRow>,
        statsLines: List<String>,
        ocrStrategy: String?,
        archiveId: String = getSelectedArchiveId(context)
    ): MyStoneRecord {
        ensureInitialized(context)
        val resolvedArchiveId = resolveArchiveId(context, archiveId)
        val normalizedType = normalizeType(stoneType)
        val record = loadRecord(context, normalizedType, resolvedArchiveId)
            ?: throw IllegalStateException("没有可更新的星石记录")
        val updated = record.copy(
            stoneType = normalizedType,
            updatedAt = System.currentTimeMillis(),
            rows = rows.deepCopyRows(),
            statsLines = statsLines,
            ocrStrategy = ocrStrategy
        )
        saveRecord(context, resolvedArchiveId, normalizedType, updated)
        touchArchive(context, resolvedArchiveId, updated.updatedAt)
        setSelectedArchiveId(context, resolvedArchiveId)
        setSelectedType(context, normalizedType)
        return updated
    }

    fun updateRows(
        context: Context,
        stoneType: String,
        rows: List<MyStoneRow>,
        statsLines: List<String>,
        archiveId: String = getSelectedArchiveId(context)
    ): MyStoneRecord {
        ensureInitialized(context)
        val resolvedArchiveId = resolveArchiveId(context, archiveId)
        val normalizedType = normalizeType(stoneType)
        val record = loadRecord(context, normalizedType, resolvedArchiveId) ?: MyStoneRecord(
            stoneType = normalizedType,
            updatedAt = System.currentTimeMillis(),
            images = emptyList()
        )
        val updated = record.copy(
            stoneType = normalizedType,
            updatedAt = System.currentTimeMillis(),
            rows = rows.deepCopyRows(),
            statsLines = statsLines
        )
        saveRecord(context, resolvedArchiveId, normalizedType, updated)
        touchArchive(context, resolvedArchiveId, updated.updatedAt)
        setSelectedArchiveId(context, resolvedArchiveId)
        setSelectedType(context, normalizedType)
        return updated
    }

    fun loadRecord(
        context: Context,
        stoneType: String,
        archiveId: String = getSelectedArchiveId(context)
    ): MyStoneRecord? {
        ensureInitialized(context)
        val resolvedArchiveId = resolveArchiveId(context, archiveId)
        val normalizedType = normalizeType(stoneType)
        val file = recordFile(context, resolvedArchiveId, normalizedType)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), MyStoneRecord::class.java)
                ?.copy(stoneType = normalizedType)
        }.getOrNull()
    }

    fun imageFiles(
        context: Context,
        stoneType: String,
        record: MyStoneRecord,
        archiveId: String = getSelectedArchiveId(context)
    ): List<File> {
        ensureInitialized(context)
        val resolvedArchiveId = resolveArchiveId(context, archiveId)
        val normalizedType = normalizeType(stoneType)
        val dir = storeDir(context, resolvedArchiveId, normalizedType)
        return record.images.map { File(dir, it.fileName) }.filter { it.exists() }
    }

    fun migrateLegacyMainRecordIfNeeded(context: Context) {
        ensureInitialized(context)
    }

    private fun ensureInitialized(context: Context) {
        migrateLegacyFlatRecordIfNeeded(context)
        migrateLegacyDataIntoDefaultArchiveIfNeeded(context)
        ensureArchiveMeta(context)
        ensureSelectedArchivePref(context)
    }

    private fun migrateLegacyFlatRecordIfNeeded(context: Context) {
        val mainRecord = File(legacyTypeDir(context, TYPE_MAIN), RECORD_FILE_NAME)
        val legacyRecord = legacyRecordFile(context)
        if (mainRecord.exists() || !legacyRecord.exists()) return

        val legacyDir = rootDir(context)
        val mainDir = legacyTypeDir(context, TYPE_MAIN).apply {
            if (!exists()) mkdirs()
        }
        legacyDir.listFiles()?.forEach { file ->
            if (file.name == TYPE_MAIN || file.name == TYPE_SUPPORT || file.name == SLOTS_DIR_NAME || file.name == ARCHIVES_FILE_NAME) {
                return@forEach
            }
            if (file.isFile && file.name != LEGACY_RECORD_FILE_NAME) {
                file.copyTo(File(mainDir, file.name), overwrite = true)
            }
        }
        legacyRecord.copyTo(mainRecord, overwrite = true)
    }

    private fun migrateLegacyDataIntoDefaultArchiveIfNeeded(context: Context) {
        val defaultArchiveDir = File(slotsRootDir(context), DEFAULT_ARCHIVE_ID)
        val hasDefaultData = defaultArchiveDir.exists() && defaultArchiveDir.walkTopDown().any { it != defaultArchiveDir }
        if (hasDefaultData) return

        listOf(TYPE_MAIN, TYPE_SUPPORT).forEach { stoneType ->
            val sourceDir = legacyTypeDir(context, stoneType)
            if (!sourceDir.exists()) return@forEach
            val targetDir = storeDir(context, DEFAULT_ARCHIVE_ID, stoneType)
            copyDirectoryContents(sourceDir, targetDir)
        }
    }

    private fun copyDirectoryContents(sourceDir: File, targetDir: File) {
        sourceDir.listFiles()?.forEach { file ->
            val target = File(targetDir, file.name)
            if (file.isDirectory) {
                file.copyRecursively(target, overwrite = true)
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
    }

    private fun ensureArchiveMeta(context: Context) {
        val file = archivesFile(context)
        if (file.exists()) {
            val archives = readArchiveMeta(context).archives
            if (archives.isNotEmpty()) return
        }
        saveArchiveMeta(context, listOf(defaultArchive()))
    }

    private fun ensureSelectedArchivePref(context: Context) {
        val archives = readArchiveMeta(context).archives.ifEmpty { listOf(defaultArchive()) }
        val fallbackId = archives.firstOrNull()?.id ?: DEFAULT_ARCHIVE_ID
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SELECTED_ARCHIVE_ID, fallbackId)
        if (archives.none { it.id == stored }) {
            prefs.edit().putString(KEY_SELECTED_ARCHIVE_ID, fallbackId).apply()
        }
    }

    private fun readArchiveMeta(context: Context): MyStoneArchiveMeta {
        val file = archivesFile(context)
        if (!file.exists()) return MyStoneArchiveMeta()
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), MyStoneArchiveMeta::class.java) ?: MyStoneArchiveMeta()
        }.getOrDefault(MyStoneArchiveMeta())
    }

    private fun saveArchiveMeta(context: Context, archives: List<MyStoneArchive>) {
        archivesFile(context).writeText(
            gson.toJson(MyStoneArchiveMeta(archives = archives.sortedBy { it.createdAt })),
            Charsets.UTF_8
        )
    }

    private fun defaultArchive(): MyStoneArchive {
        val now = System.currentTimeMillis()
        return MyStoneArchive(
            id = DEFAULT_ARCHIVE_ID,
            name = DEFAULT_ARCHIVE_NAME,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun requireArchiveName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "存档名称不能为空" }
        return normalized
    }

    private fun touchArchive(context: Context, archiveId: String, updatedAt: Long) {
        val updatedArchives = listArchives(context).map { archive ->
            if (archive.id == archiveId) {
                archive.copy(updatedAt = updatedAt)
            } else {
                archive
            }
        }
        saveArchiveMeta(context, updatedArchives)
    }

    private fun saveRecord(context: Context, archiveId: String, stoneType: String, record: MyStoneRecord) {
        recordFile(context, archiveId, stoneType).writeText(gson.toJson(record), Charsets.UTF_8)
    }

    private fun clearStore(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    private fun List<MyStoneRow>.deepCopyRows(): List<MyStoneRow> {
        return map { row ->
            MyStoneRow(
                cells = row.cells.map { cell ->
                    MyStoneCell(level = cell.level, name = cell.name)
                }.toMutableList()
            )
        }
    }
}
