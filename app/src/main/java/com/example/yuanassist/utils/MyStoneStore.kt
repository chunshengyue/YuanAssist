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

object MyStoneStore {

    private const val DIR_NAME = "my_stones"
    private const val RECORD_FILE_NAME = "record.json"
    private const val LEGACY_RECORD_FILE_NAME = "record.json"
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SELECTED_STONE_TYPE = "selected_stone_type"
    const val TYPE_MAIN = "main"
    const val TYPE_SUPPORT = "support"
    private val gson = Gson()

    private fun rootDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun storeDir(context: Context, stoneType: String): File =
        File(rootDir(context), stoneType).apply {
            if (!exists()) mkdirs()
        }

    private fun legacyRecordFile(context: Context): File = File(rootDir(context), LEGACY_RECORD_FILE_NAME)

    private fun recordFile(context: Context, stoneType: String): File =
        File(storeDir(context, stoneType), RECORD_FILE_NAME)

    fun setSelectedType(context: Context, stoneType: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_STONE_TYPE, normalizeType(stoneType))
            .apply()
    }

    fun getSelectedType(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_STONE_TYPE, TYPE_MAIN)
        return normalizeType(stored)
    }

    fun normalizeType(stoneType: String?): String =
        when (stoneType) {
            TYPE_SUPPORT -> TYPE_SUPPORT
            else -> TYPE_MAIN
        }

    fun displayName(stoneType: String): String =
        if (normalizeType(stoneType) == TYPE_SUPPORT) "辅星" else "主星"

    fun saveImages(context: Context, stoneType: String, bitmaps: List<Bitmap>): MyStoneRecord {
        require(bitmaps.isNotEmpty()) { "至少需要一张图片" }

        val normalizedType = normalizeType(stoneType)
        val dir = storeDir(context, normalizedType)
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
        saveRecord(context, normalizedType, record)
        setSelectedType(context, normalizedType)
        return record
    }

    fun saveOcrResult(
        context: Context,
        stoneType: String,
        rows: List<MyStoneRow>,
        statsLines: List<String>,
        ocrStrategy: String?
    ): MyStoneRecord {
        val normalizedType = normalizeType(stoneType)
        val record = loadRecord(context, normalizedType) ?: throw IllegalStateException("没有可更新的星石记录")
        val updated = record.copy(
            stoneType = normalizedType,
            updatedAt = System.currentTimeMillis(),
            rows = rows.deepCopyRows(),
            statsLines = statsLines,
            ocrStrategy = ocrStrategy
        )
        saveRecord(context, normalizedType, updated)
        setSelectedType(context, normalizedType)
        return updated
    }

    fun updateRows(
        context: Context,
        stoneType: String,
        rows: List<MyStoneRow>,
        statsLines: List<String>
    ): MyStoneRecord {
        val normalizedType = normalizeType(stoneType)
        val record = loadRecord(context, normalizedType) ?: MyStoneRecord(
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
        saveRecord(context, normalizedType, updated)
        setSelectedType(context, normalizedType)
        return updated
    }

    fun loadRecord(context: Context, stoneType: String): MyStoneRecord? {
        val normalizedType = normalizeType(stoneType)
        val file = recordFile(context, normalizedType)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), MyStoneRecord::class.java)
                ?.copy(stoneType = normalizedType)
        }.getOrNull()
    }

    fun imageFiles(context: Context, stoneType: String, record: MyStoneRecord): List<File> {
        val normalizedType = normalizeType(stoneType)
        val dir = storeDir(context, normalizedType)
        return record.images.map { File(dir, it.fileName) }.filter { it.exists() }
    }

    fun migrateLegacyMainRecordIfNeeded(context: Context) {
        val mainRecord = recordFile(context, TYPE_MAIN)
        val legacyRecord = legacyRecordFile(context)
        if (mainRecord.exists() || !legacyRecord.exists()) return

        val legacyDir = rootDir(context)
        val mainDir = storeDir(context, TYPE_MAIN)
        legacyDir.listFiles()?.forEach { file ->
            if (file.name == TYPE_MAIN || file.name == TYPE_SUPPORT) return@forEach
            if (file.isFile && file.name != LEGACY_RECORD_FILE_NAME) {
                file.copyTo(File(mainDir, file.name), overwrite = true)
                file.delete()
            }
        }
        legacyRecord.copyTo(mainRecord, overwrite = true)
        legacyRecord.delete()
    }

    private fun saveRecord(context: Context, stoneType: String, record: MyStoneRecord) {
        recordFile(context, stoneType).writeText(gson.toJson(record), Charsets.UTF_8)
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
