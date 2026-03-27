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
    val updatedAt: Long,
    val images: List<MyStoneImageEntry>,
    val statsLines: List<String> = emptyList(),
    val rows: List<MyStoneRow> = emptyList(),
    val ocrStrategy: String? = null
)

object MyStoneStore {

    private const val DIR_NAME = "my_stones"
    private const val RECORD_FILE_NAME = "record.json"
    private val gson = Gson()

    private fun storeDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    private fun recordFile(context: Context): File = File(storeDir(context), RECORD_FILE_NAME)

    fun saveImages(context: Context, bitmaps: List<Bitmap>): MyStoneRecord {
        require(bitmaps.isNotEmpty()) { "至少需要一张图片" }

        val dir = storeDir(context)
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
            updatedAt = System.currentTimeMillis(),
            images = images
        )
        saveRecord(context, record)
        return record
    }

    fun saveOcrResult(
        context: Context,
        rows: List<MyStoneRow>,
        statsLines: List<String>,
        ocrStrategy: String?
    ): MyStoneRecord {
        val record = loadRecord(context) ?: throw IllegalStateException("没有可更新的星石记录")
        val updated = record.copy(
            updatedAt = System.currentTimeMillis(),
            rows = rows.deepCopyRows(),
            statsLines = statsLines,
            ocrStrategy = ocrStrategy
        )
        saveRecord(context, updated)
        return updated
    }

    fun updateRows(
        context: Context,
        rows: List<MyStoneRow>,
        statsLines: List<String>
    ): MyStoneRecord {
        val record = loadRecord(context) ?: throw IllegalStateException("没有可更新的星石记录")
        val updated = record.copy(
            updatedAt = System.currentTimeMillis(),
            rows = rows.deepCopyRows(),
            statsLines = statsLines
        )
        saveRecord(context, updated)
        return updated
    }

    fun loadRecord(context: Context): MyStoneRecord? {
        val file = recordFile(context)
        if (!file.exists()) return null
        return runCatching {
            gson.fromJson(file.readText(Charsets.UTF_8), MyStoneRecord::class.java)
        }.getOrNull()
    }

    fun imageFiles(context: Context, record: MyStoneRecord): List<File> {
        val dir = storeDir(context)
        return record.images.map { File(dir, it.fileName) }.filter { it.exists() }
    }

    private fun saveRecord(context: Context, record: MyStoneRecord) {
        recordFile(context).writeText(gson.toJson(record), Charsets.UTF_8)
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
