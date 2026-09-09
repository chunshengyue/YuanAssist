package com.example.yuanassist.tableocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import java.util.ArrayDeque
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PaddleOcrNative {
    private const val recHeight = 48
    private const val recMaxWidth = 320
    private const val detLimit = 736
    private const val detStride = 32
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var recSession: OrtSession? = null
    private var detSession: OrtSession? = null
    private var labels = emptyList<String>()

    @Synchronized
    fun init(recModelPath: String, labelPath: String): Boolean = runCatching {
        if (recSession == null) {
            labels = File(labelPath).readLines().filter { it.isNotEmpty() }
            require(labels.isNotEmpty())
            recSession = createSession(recModelPath)
        }
    }.isSuccess

    @Synchronized
    fun initDetector(detModelPath: String): Boolean = runCatching {
        if (detSession == null) detSession = createSession(detModelPath)
    }.isSuccess

    fun recognize(bitmap: Bitmap): String {
        val session = recSession ?: return ""
        val width = min(ceil(recHeight * bitmap.width.toFloat() / bitmap.height).toInt(), recMaxWidth).coerceAtLeast(1)
        val input = preprocess(bitmap, width, recHeight, floatArrayOf(.5f, .5f, .5f), floatArrayOf(.5f, .5f, .5f))
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, recHeight.toLong(), width.toLong())).use { tensor ->
            session.run(Collections.singletonMap(session.inputNames.first(), tensor)).use { output ->
                val steps = (output[0].value as Array<*>)[0] as Array<FloatArray>
                val text = StringBuilder()
                var previous = 0
                for (scores in steps) {
                    var best = 0
                    for (index in 1 until scores.size) if (scores[index] > scores[best]) best = index
                    if (best > 0 && best != previous && best - 1 < labels.size) text.append(labels[best - 1])
                    previous = best
                }
                return text.toString()
            }
        }
    }

    fun detect(bitmap: Bitmap): IntArray {
        val session = detSession ?: return IntArray(0)
        val ratio = min(1f, detLimit.toFloat() / max(bitmap.width, bitmap.height))
        val width = align((bitmap.width * ratio).roundToInt())
        val height = align((bitmap.height * ratio).roundToInt())
        val input = preprocess(bitmap, width, height, floatArrayOf(.485f, .456f, .406f), floatArrayOf(.229f, .224f, .225f))
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, height.toLong(), width.toLong())).use { tensor ->
            session.run(Collections.singletonMap(session.inputNames.first(), tensor)).use { output ->
                val scoreMap = ((output[0].value as Array<*>)[0] as Array<*>)[0] as Array<FloatArray>
                return boxes(scoreMap, bitmap.width, bitmap.height).flatMap { listOf(it[0], it[1], it[2], it[3], 0) }.toIntArray()
            }
        }
    }

    @Synchronized
    fun release() {
        recSession?.close(); detSession?.close()
        recSession = null; detSession = null; labels = emptyList()
    }

    private fun createSession(path: String): OrtSession {
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(1); setInterOpNumThreads(1) }
        return environment.createSession(path, options)
    }

    private fun align(value: Int) = max(detStride, ceil(value / detStride.toFloat()).toInt() * detStride)

    private fun preprocess(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, mean: FloatArray, std: FloatArray): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return FloatArray(3 * targetWidth * targetHeight).also { result ->
            for (channel in 0..2) for (row in 0 until targetHeight) for (column in 0 until targetWidth) {
                val sourceX = column * bitmap.width.toFloat() / targetWidth
                val sourceY = row * bitmap.height.toFloat() / targetHeight
                val left = min(sourceX.toInt(), bitmap.width - 1); val right = min(left + 1, bitmap.width - 1)
                val top = min(sourceY.toInt(), bitmap.height - 1); val bottom = min(top + 1, bitmap.height - 1)
                val horizontal = sourceX - left; val vertical = sourceY - top
                fun pixel(columnIndex: Int, rowIndex: Int): Float {
                    val color = pixels[rowIndex * bitmap.width + columnIndex]
                    return when (channel) { 0 -> (color and 255).toFloat(); 1 -> ((color shr 8) and 255).toFloat(); else -> ((color shr 16) and 255).toFloat() }
                }
                val topValue = pixel(left, top) * (1 - horizontal) + pixel(right, top) * horizontal
                val bottomValue = pixel(left, bottom) * (1 - horizontal) + pixel(right, bottom) * horizontal
                result[channel * targetWidth * targetHeight + row * targetWidth + column] = ((topValue * (1 - vertical) + bottomValue * vertical) / 255f - mean[channel]) / std[channel]
            }
        }
    }

    private fun boxes(scoreMap: Array<FloatArray>, sourceWidth: Int, sourceHeight: Int): List<IntArray> {
        val mapHeight = scoreMap.size; if (mapHeight == 0) return emptyList()
        val mapWidth = scoreMap[0].size; val visited = BooleanArray(mapWidth * mapHeight); val result = mutableListOf<IntArray>()
        val dx = intArrayOf(1, -1, 0, 0); val dy = intArrayOf(0, 0, 1, -1)
        for (row in 0 until mapHeight) for (column in 0 until mapWidth) {
            val start = row * mapWidth + column
            if (visited[start] || scoreMap[row][column] < .2f) continue
            val queue = ArrayDeque<Int>(); queue.add(start); visited[start] = true
            var minX = column; var maxX = column; var minY = row; var maxY = row; var count = 0; var sum = 0f
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst(); val currentX = index % mapWidth; val currentY = index / mapWidth
                count++; sum += scoreMap[currentY][currentX]; minX = min(minX, currentX); maxX = max(maxX, currentX); minY = min(minY, currentY); maxY = max(maxY, currentY)
                for (direction in dx.indices) {
                    val nextX = currentX + dx[direction]; val nextY = currentY + dy[direction]
                    if (nextX !in 0 until mapWidth || nextY !in 0 until mapHeight) continue
                    val next = nextY * mapWidth + nextX
                    if (!visited[next] && scoreMap[nextY][nextX] >= .2f) { visited[next] = true; queue.add(next) }
                }
            }
            if (count < 12 || sum / count < .45f || maxX - minX < 1 || maxY - minY < 1) continue
            result += intArrayOf(max(0, floor(minX * sourceWidth.toFloat() / mapWidth).toInt()), max(0, floor(minY * sourceHeight.toFloat() / mapHeight).toInt()), min(sourceWidth, ceil((maxX + 1) * sourceWidth.toFloat() / mapWidth).toInt()), min(sourceHeight, ceil((maxY + 1) * sourceHeight.toFloat() / mapHeight).toInt()))
        }
        return result.sortedWith(compareBy<IntArray> { it[1] }.thenBy { it[0] }).take(80)
    }
}
