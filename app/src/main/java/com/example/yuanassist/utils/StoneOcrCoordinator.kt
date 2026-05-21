package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.yuanassist.network.OcrManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StoneOcrMode(val label: String) {
    NONE("不OCR"),
    LOCAL("本地OCR"),
    CLOUD("云端OCR"),
}

data class StoneOcrImportResult(
    val rows: List<MyStoneRow>,
    val statsLines: List<String>,
    val hasPendingRows: Boolean,
    val strategyUsed: String,
)

object StoneOcrCoordinator {

    const val LOCAL_STRATEGY = "local:paddle-box-partition-v1"

    suspend fun importStoneImages(
        context: Context,
        stoneType: String,
        archiveId: String,
        imageFiles: List<File>,
        mode: StoneOcrMode,
        deviceId: String? = null,
        onCloudRetryMsg: () -> Unit = {},
    ): StoneOcrImportResult {
        require(mode != StoneOcrMode.NONE) { "Stone OCR mode must not be NONE" }
        require(imageFiles.isNotEmpty()) { "未找到可识别的截图" }

        val wordsGroups = mutableListOf<List<String>>()
        val rawLogLines = mutableListOf<String>()
        var rawLogTitle: String? = null
        val strategies = linkedSetOf<String>()

        for (file in imageFiles) {
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(file.absolutePath)
            } ?: throw IllegalStateException("读取星石截图失败：${file.name}")

            try {
                val fileResult = when (mode) {
                    StoneOcrMode.LOCAL -> recognizeLocalStoneImage(context, bitmap, stoneType)
                    StoneOcrMode.CLOUD -> recognizeCloudStoneImage(bitmap, stoneType, deviceId, onCloudRetryMsg)
                    StoneOcrMode.NONE -> error("Unsupported OCR mode")
                }
                wordsGroups += fileResult.wordGroups
                rawLogLines += fileResult.rawLogLines
                if (rawLogTitle == null && fileResult.rawLogTitle != null) {
                    rawLogTitle = fileResult.rawLogTitle
                }
                if (fileResult.strategyUsed.isNotBlank()) {
                    strategies += fileResult.strategyUsed
                }
            } finally {
                bitmap.recycle()
            }
        }

        if (mode == StoneOcrMode.LOCAL && wordsGroups.flatten().isEmpty()) {
            throw IllegalStateException("本地 OCR 未识别到可用文本")
        }

        if (!rawLogTitle.isNullOrBlank() && rawLogLines.isNotEmpty()) {
            RunLogger.raw(rawLogTitle!!)
            rawLogLines.forEach { line -> RunLogger.i(line) }
        }

        val rows = StoneOcrParser.buildRows(wordsGroups, stoneType)
        if (mode == StoneOcrMode.LOCAL && rows.isEmpty()) {
            throw IllegalStateException("本地 OCR 未解析出可用星石行")
        }
        val statsLines = StoneOcrParser.format(StoneOcrParser.aggregate(rows, stoneType))
        val strategyUsed = strategies.joinToString(",")
        MyStoneStore.saveOcrResult(
            context = context,
            stoneType = stoneType,
            rows = rows,
            statsLines = statsLines,
            ocrStrategy = strategyUsed,
            ocrMode = mode,
            archiveId = archiveId,
        )
        return StoneOcrImportResult(
            rows = rows,
            statsLines = statsLines,
            hasPendingRows = rows.any { !StoneOcrParser.isRowResolved(it, stoneType) },
            strategyUsed = strategyUsed,
        )
    }

    fun importLocalSession(
        context: Context,
        stoneType: String,
        archiveId: String,
        session: StoneLocalOcrSession,
    ): StoneOcrImportResult {
        if (session.wordGroups.flatten().isEmpty()) {
            throw IllegalStateException("本地 OCR 未识别到可用文本")
        }

        if (session.rawLogLines.isNotEmpty()) {
            RunLogger.raw("【本地OCR返回原文本】")
            session.rawLogLines.forEach { line -> RunLogger.i(line) }
        }

        val rows = session.rows.takeIf { it.isNotEmpty() } ?: StoneOcrParser.buildRows(session.wordGroups, stoneType)
        if (rows.isEmpty()) {
            throw IllegalStateException("本地 OCR 未解析出可用星石行")
        }

        val statsLines = StoneOcrParser.format(StoneOcrParser.aggregate(rows, stoneType))
        MyStoneStore.saveOcrResult(
            context = context,
            stoneType = stoneType,
            rows = rows,
            statsLines = statsLines,
            ocrStrategy = session.strategyUsed,
            ocrMode = StoneOcrMode.LOCAL,
            archiveId = archiveId,
        )
        return StoneOcrImportResult(
            rows = rows,
            statsLines = statsLines,
            hasPendingRows = rows.any { !StoneOcrParser.isRowResolved(it, stoneType) },
            strategyUsed = session.strategyUsed,
        )
    }

    private suspend fun recognizeCloudStoneImage(
        bitmap: Bitmap,
        stoneType: String,
        deviceId: String?,
        onCloudRetryMsg: () -> Unit,
    ): StoneFileOcrResult {
        val resolvedDeviceId = deviceId?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("云端 OCR 缺少 deviceId")
        return when (
            val result = OcrManager.recognizeStoneImage(
                bitmap = bitmap,
                deviceId = resolvedDeviceId,
                onRetryMsg = onCloudRetryMsg,
            )
        ) {
            is OcrManager.StoneOcrResult.Success -> StoneFileOcrResult(
                wordGroups = listOf(result.words),
                rawLogLines = StoneOcrParser.formatRawJsonByRow(
                    rawEntryGroups = listOf(result.rawEntries),
                    stoneType = stoneType,
                ),
                rawLogTitle = "【OCR返回原文本】",
                strategyUsed = "cloud:${result.strategyUsed.ifBlank { "default" }}",
            )

            is OcrManager.StoneOcrResult.Error -> {
                throw IllegalStateException(result.message)
            }
        }
    }

    private suspend fun recognizeLocalStoneImage(
        context: Context,
        bitmap: Bitmap,
        stoneType: String,
    ): StoneFileOcrResult = withContext(Dispatchers.IO) {
        val session = StonePaddleLocalRecognizer.recognize(
            context = context,
            bitmap = bitmap,
            stoneType = stoneType,
        )

        StoneFileOcrResult(
            wordGroups = session.wordGroups,
            rawLogLines = session.rawLogLines,
            rawLogTitle = "【本地OCR返回原文本】",
            strategyUsed = session.strategyUsed,
        )
    }

    private data class StoneFileOcrResult(
        val wordGroups: List<List<String>>,
        val rawLogLines: List<String>,
        val rawLogTitle: String?,
        val strategyUsed: String,
    )
}
