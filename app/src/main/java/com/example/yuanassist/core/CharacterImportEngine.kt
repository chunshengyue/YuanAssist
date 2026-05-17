package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.model.CharacterImportConfig
import com.example.yuanassist.model.CharacterSwitchPoint
import com.example.yuanassist.model.ImportedCharacterRecord
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.utils.RunLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.min

internal object CharacterImportInferenceDecider {
    fun shouldAccept(
        rawName: String,
        bestName: String,
        bestTotalScore: Float,
        bestFateScore: Float,
        bestFateHitCount: Int,
        bestUniqueFateHitCount: Int,
        bestUniqueFateScore: Float,
        secondTotalScore: Float?,
        correctedName: String?,
    ): Boolean {
        val separated = secondTotalScore == null || bestTotalScore - secondTotalScore >= 0.10f
        val strongByFates = bestFateHitCount >= 2 && bestFateScore >= 0.58f
        val decisiveByFates = bestFateHitCount >= 3 && bestFateScore >= 0.72f
        val decisiveByUniqueFate = bestUniqueFateHitCount >= 1 && bestUniqueFateScore >= 0.75f
        val strongByName = correctedName == bestName && bestTotalScore >= 0.62f
        val hasSharedCharacterBoostedByFates =
            rawName.length in 2..3 &&
                rawName.any { bestName.contains(it) } &&
                bestFateHitCount >= 2 &&
                bestFateScore >= 0.66f
        return when {
            decisiveByFates -> true
            decisiveByUniqueFate -> true
            hasSharedCharacterBoostedByFates -> true
            (strongByFates || strongByName) && separated -> true
            else -> false
        }
    }
}

internal data class FateCorrection(
    val displayText: String,
    val score: Float,
)

internal data class FateScoreResult(
    val fateScore: Float,
    val hitCount: Int,
    val uniqueHitCount: Int,
    val uniqueScore: Float,
    val correctedFates: List<FateCorrection?>,
)

internal data class AgentInference(
    val name: String,
    val totalScore: Float,
    val nameScore: Float,
    val fateScore: Float,
    val fateHitCount: Int,
    val uniqueFateHitCount: Int,
    val uniqueFateScore: Float,
    val correctedFates: List<FateCorrection?>,
)

internal object CharacterImportFateScorer {
    private val fateAgentCounts by lazy {
        AgentRepository.AGENT_MAP
            .flatMap { (agentName, attr) ->
                attr.talents.values
                    .map(::normalizeFateText)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { normalizedFate -> normalizedFate to agentName }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, agentNames) -> agentNames.distinct().size }
    }

    fun scoreAgent(
        rawFates: List<String>,
        candidates: Collection<String>,
    ): FateScoreResult {
        val normalizedFates = rawFates.map(::normalizeFateText)
        val correctedFates = normalizedFates.map { rawFate ->
            bestFateForAgent(rawFate, candidates)
        }
        val validFates = correctedFates.filterNotNull()
        val slotCount = normalizedFates.size.coerceAtLeast(1)
        val coverageScore = correctedFates.sumOf { correction ->
            (correction?.score ?: 0f).toDouble()
        }.toFloat() / slotCount
        val uniqueFates = validFates.filter { correction ->
            fateAgentCounts[normalizeFateText(correction.displayText)] == 1
        }
        val uniqueScore = uniqueFates.maxOfOrNull { it.score } ?: 0f
        return FateScoreResult(
            fateScore = maxOf(coverageScore, uniqueScore),
            hitCount = validFates.size,
            uniqueHitCount = uniqueFates.size,
            uniqueScore = uniqueScore,
            correctedFates = correctedFates,
        )
    }

    fun bestFateForAgent(rawFate: String, candidates: Collection<String>): FateCorrection? {
        if (rawFate.isBlank()) return null
        return candidates
            .mapNotNull { candidate ->
                val normalizedCandidate = normalizeFateText(candidate)
                if (normalizedCandidate.isBlank()) return@mapNotNull null
                FateCorrection(candidate, fateTextSimilarity(rawFate, normalizedCandidate))
            }
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= 0.55f }
    }

    fun normalizeFateText(text: String): String =
        text.replace("橙", "")
            .replace("紫", "")
            .filter { char ->
                Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN || char.isDigit()
            }

    fun fateTextSimilarity(left: String, right: String): Float {
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 2 && left.any { right.contains(it) } && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.65f
        }
        if (maxLength == 3 && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.67f
        }
        if (left == right) return 1f
        if (maxLength == 0) return 0f
        return textSimilarity(left, right)
    }

    private fun textSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 0f
        return (1f - levenshteinDistance(left, right).toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}

class CharacterImportEngine(private val service: AccessibilityService) {
    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val VALUE_X = 340f
        private const val VALUE_Y = 1289f
        private const val VALUE_W = 360f
        private const val VALUE_H = 180f
        private const val STAR_X = 625f
        private const val STAR_Y = 1663f
        private const val STAR_SIZE = 260f
        private const val NAME_X = 662f
        private const val NAME_Y = 197f
        private const val NAME_W = 300f
        private const val NAME_H = 120f
        private const val FATE_SIZE = 220f
        private const val ENTER_FATE_X = 878f
        private const val ENTER_FATE_Y = 1591f
        private const val CLICK_DURATION_MS = 80L
    }

    private data class CaptureSpec(
        val label: String,
        val x: Float,
        val y: Float,
        val align: String,
        val w: Float,
        val h: Float,
    )

    private data class NameCorrection(
        val displayText: String,
        val score: Float,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val records = MutableList(5) { ImportedCharacterRecord(slot = it + 1) }
    private val defaultFatePoints = listOf(174f to 426f, 935f to 694f, 415f to 1219f)
    private val specialFatePointsByAgent = mapOf(
        "孙尚香" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
        "华佗" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
        "张辽" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
        "张修" to listOf(415f to 435f, 918f to 950f, 147f to 1241f),
        "周瑜" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
        "陆逊" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
        "鲁肃" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
        "干吉" to listOf(936f to 442f, 156f to 705f, 677f to 1230f),
        "荀彧" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
        "庞统" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
        "颜良" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
        "史子渺" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
        "史子眇" to listOf(415f to 430f, 154f to 1223f, 945f to 960f),
        "张角" to listOf(938f to 444f, 156f to 702f, 666f to 1225f),
        "王粲" to listOf(680f to 437f, 147f to 959f, 932f to 1214f),
    )
    private val fateCorrectionCandidates by lazy {
        AgentRepository.AGENT_MAP.values
            .flatMap { attr -> attr.talents.values }
            .distinct()
    }
    private val agentNames by lazy { AgentRepository.AGENT_MAP.keys.toList() }

    var isRunning = false
        private set

    private var config = CharacterImportConfig()
    private var onCompleted: ((Boolean) -> Unit)? = null

    fun prepare(config: CharacterImportConfig) {
        this.config = config
    }

    fun start(onCompleted: (Boolean) -> Unit): Boolean {
        if (isRunning) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            RunLogger.e("当前安卓版本不支持系统截图 API")
            return false
        }
        this.onCompleted = onCompleted
        isRunning = true
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        records.indices.forEach { index -> records[index] = ImportedCharacterRecord(slot = index + 1) }
        RunLogger.clear()
        RunLogger.i("角色导入开始")
        handler.postDelayed({ captureValueSlot(1) }, config.operationIntervalMs)
        return true
    }

    fun stop(showLog: Boolean = true) {
        if (!isRunning) return
        isRunning = false
        scope.cancel()
        if (showLog) RunLogger.i("角色导入已停止")
        onCompleted?.invoke(false)
    }

    fun snapshotRecords(): List<ImportedCharacterRecord> {
        return records.map { it.copy(fates = it.fates.toList()) }
    }

    private fun captureValueSlot(slot: Int) {
        if (!isRunning) return
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val record = runCatching { readValueAndStars(slot, screenshot) }
                        .getOrElse { error ->
                            RunLogger.e("${slot}号位练度识别失败：${error.message}", error)
                            ImportedCharacterRecord(slot = slot)
                        }
                    records[slot - 1] = records[slot - 1].copy(
                        hp = record.hp,
                        attack = record.attack,
                        starCount = record.starCount,
                    )
                    handler.post {
                        screenshot.recycle()
                        if (!isRunning) return@post
                        clickVisionPoint(ENTER_FATE_X, ENTER_FATE_Y, "bottom")
                        handler.postDelayed({ captureFateSlot(slot) }, config.operationIntervalMs)
                    }
                }
            },
            onFailure = { finish(false, "截图失败，无法识别${slot}号位练度") },
        )
    }

    private fun captureFateSlot(slot: Int) {
        if (!isRunning) return
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val record = runCatching { readNameAndFates(slot, screenshot) }
                        .getOrElse { error ->
                            RunLogger.e("${slot}号位命盘识别失败：${error.message}", error)
                            ImportedCharacterRecord(slot = slot)
                        }
                    records[slot - 1] = records[slot - 1].copy(
                        name = record.name,
                        fates = record.fates,
                    )
                    handler.post {
                        screenshot.recycle()
                        if (!isRunning) return@post
                        if (!performSystemBack()) {
                            finish(false, "系统返回失败，无法退出${slot}号位命盘页")
                            return@post
                        }
                        if (slot < 5) {
                            handler.postDelayed(
                                {
                                    if (isRunning) {
                                        clickSwitchPoint(config.switchRightPoint, "右切")
                                        handler.postDelayed({ captureValueSlot(slot + 1) }, config.operationIntervalMs)
                                    }
                                },
                                config.operationIntervalMs,
                            )
                        } else {
                            finish(true, null)
                        }
                    }
                }
            },
            onFailure = { finish(false, "截图失败，无法识别${slot}号位命盘") },
        )
    }

    private fun readValueAndStars(slot: Int, screenshot: Bitmap): ImportedCharacterRecord {
        val valueBitmap = cropBySpec(
            screenshot,
            CaptureSpec("values", VALUE_X, VALUE_Y, "bottom", VALUE_W, VALUE_H),
        )
        val starBitmap = cropBySpec(
            screenshot,
            CaptureSpec("stars", STAR_X, STAR_Y, "bottom", STAR_SIZE, STAR_SIZE),
        )
        try {
            val valueText = PaddleTextRecognizer.recognize(service, valueBitmap).text
            val numbers = parseNumbers(valueText)
            val starResult = CharacterStarDetector.detect(starBitmap)
            RunLogger.i("${slot}号位练度 raw=${formatLog(valueText)} numbers=${numbers.joinToString(",")} stars=${starResult.starCount}")
            return ImportedCharacterRecord(
                slot = slot,
                hp = numbers.getOrNull(0).orEmpty(),
                attack = numbers.getOrNull(1).orEmpty(),
                starCount = starResult.starCount,
            )
        } finally {
            valueBitmap.recycle()
            starBitmap.recycle()
        }
    }

    private fun readNameAndFates(slot: Int, screenshot: Bitmap): ImportedCharacterRecord {
        val nameBitmap = cropBySpec(
            screenshot,
            CaptureSpec("name", NAME_X, NAME_Y, "center", NAME_W, NAME_H),
        )
        val nameText = try {
            PaddleTextRecognizer.recognize(service, nameBitmap).text
        } finally {
            nameBitmap.recycle()
        }
        val rawName = parseName(nameText)
        val exactAgentName = findExactAgentName(rawName)
        val nameCorrection = correctAgentName(rawName)
        val preliminaryName = exactAgentName ?: nameCorrection?.displayText ?: rawName
        val selectedFatePoints = resolveFatePoints(preliminaryName)
        val fateBitmaps = selectedFatePoints.mapIndexed { index, point ->
            cropBySpec(
                screenshot,
                CaptureSpec("fate${index + 1}", point.first, point.second, "center", FATE_SIZE, FATE_SIZE),
            )
        }
        try {
            RunLogger.i(
                "${slot}号位命盘取点：$preliminaryName -> ${
                    selectedFatePoints.joinToString(" / ") { "${it.first.toInt()},${it.second.toInt()}" }
                }"
            )
            val rawFates = fateBitmaps.mapIndexed { index, bitmap ->
                val raw = PaddleTextRecognizer.recognize(service, bitmap).text
                RunLogger.i("${slot}号位命盘${index + 1} raw=${formatLog(raw)}")
                raw
            }
            val inference = if (exactAgentName != null) {
                inferLockedAgentFromFates(exactAgentName, rawFates)
            } else {
                inferAgentFromNameAndFates(rawName, rawFates, nameCorrection)
            }
            val correctedFates = inference?.correctedFates ?: rawFates.map(::correctFateText)
            val fates = correctedFates.map { it?.displayText.orEmpty() }
            correctedFates.forEachIndexed { index, correction ->
                RunLogger.i(
                    "${slot}号位命盘${index + 1} corrected=${formatLog(correction?.displayText.orEmpty())} " +
                        "score=${correction?.score?.let { "%.2f".format(it) } ?: "无"}"
                )
            }
            val finalName = exactAgentName ?: inference?.name ?: nameCorrection?.displayText ?: rawName
            RunLogger.i(
                "${slot}号位角色名 raw=${formatLog(nameText)} parsed=${formatLog(rawName)} " +
                    "name=$finalName nameScore=${"%.2f".format(nameCorrection?.score ?: 0f)} " +
                    "fateScore=${"%.2f".format(inference?.fateScore ?: 0f)} " +
                    "hits=${inference?.fateHitCount ?: 0} unique=${inference?.uniqueFateHitCount ?: 0} " +
                    "total=${"%.2f".format(inference?.totalScore ?: 0f)}"
            )
            return ImportedCharacterRecord(slot = slot, name = finalName, fates = fates)
        } finally {
            fateBitmaps.forEach(Bitmap::recycle)
        }
    }

    private fun resolveFatePoints(agentName: String): List<Pair<Float, Float>> {
        return specialFatePointsByAgent[agentName] ?: defaultFatePoints
    }

    private fun finish(success: Boolean, errorMessage: String?) {
        if (!isRunning) return
        isRunning = false
        errorMessage?.let(RunLogger::e)
        logSummary()
        onCompleted?.invoke(success)
    }

    private fun logSummary() {
        RunLogger.i("角色导入结果：")
        records.forEach { record ->
            RunLogger.i(
                "${record.slot}号位 | 角色=${record.name} | 生命=${record.hp} | 攻击=${record.attack} | " +
                    "星级=${record.starCount} | 命盘=${record.fates.filter { it.isNotBlank() }.joinToString("、")}"
            )
        }
    }

    private fun captureScreenshot(onSuccess: (Bitmap) -> Unit, onFailure: (Int) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()
                    if (softwareBitmap == null) {
                        onFailure(-1)
                    } else {
                        onSuccess(softwareBitmap)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    onFailure(errorCode)
                }
            },
        )
    }

    private fun cropBySpec(screenshot: Bitmap, spec: CaptureSpec): Bitmap {
        val (centerX, centerY) = screenshotCoordinate(screenshot, spec.x, spec.y, spec.align)
        val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val width = (spec.w * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (spec.h * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        val left = (centerX - width / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (centerY - height / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val safeW = width.coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val safeH = height.coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(screenshot, left, top, safeW, safeH)
    }

    private fun clickVisionPoint(x: Float, y: Float, align: String) {
        val (realX, realY) = realCoordinate(x, y, align)
        clickScreenPoint(realX, realY)
    }

    private fun clickSwitchPoint(point: CharacterSwitchPoint, label: String) {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val realX = point.xRatio.coerceIn(0f, 1f) * screenWidth
        val realY = point.yRatio.coerceIn(0f, 1f) * screenHeight
        RunLogger.i("${label}坐标点击：x=${realX.toInt()} y=${realY.toInt()}")
        clickScreenPoint(realX, realY)
    }

    private fun clickScreenPoint(realX: Float, realY: Float) {
        val path = Path().apply {
            moveTo(realX, realY)
            lineTo(realX, realY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, CLICK_DURATION_MS))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun performSystemBack(): Boolean {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (success) {
            RunLogger.i("系统返回")
        } else {
            RunLogger.e("系统返回失败")
        }
        return success
    }

    private fun screenshotCoordinate(screenshot: Bitmap, x: Float, y: Float, align: String): Pair<Float, Float> {
        val (realX, realY) = realCoordinate(x, y, align)
        val (screenWidth, screenHeight) = getRealScreenSize()
        return realX * screenshot.width / screenWidth to realY * screenshot.height / screenHeight
    }

    private fun realCoordinate(baseX: Float, baseY: Float, align: String): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val offsetX = (screenWidth - BASE_W * gameScale) / 2f
        val offsetY = (screenHeight - BASE_H * gameScale) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val realX = offsetX + baseX * gameScale
        val realY = when (align.lowercase()) {
            "top" -> statusBarHeight + baseY * gameScale
            "bottom" -> screenHeight - ((BASE_H - baseY) * gameScale)
            "absolute" -> baseY * (screenHeight / BASE_H)
            else -> offsetY + baseY * gameScale
        }
        return realX to realY
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.x.toFloat() to point.y.toFloat()
        }
    }

    private fun getRawStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 40
    }

    private fun parseNumbers(rawText: String): List<String> =
        Regex("\\d[\\d,，.]*")
            .findAll(rawText)
            .map { match -> match.value.filter(Char::isDigit) }
            .filter { it.isNotBlank() }
            .toList()

    private fun parseName(rawText: String): String =
        rawText.lineSequence()
            .map { line ->
                cleanDetectedAgentName(line.replace(Regex("[^\\p{IsHan}A-Za-z0-9·・（）()\\-]"), "").trim())
            }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun correctFateText(rawText: String): FateCorrection? {
        val normalizedRaw = normalizeFateText(rawText)
        if (normalizedRaw.isBlank()) return null
        val ranked = fateCorrectionCandidates
            .asSequence()
            .mapNotNull { candidate ->
                val normalizedCandidate = normalizeFateText(candidate)
                if (normalizedCandidate.isBlank()) return@mapNotNull null
                val score = fateTextSimilarity(normalizedRaw, normalizedCandidate)
                FateCorrection(candidate, score)
            }
            .filter { it.score >= 0.55f }
            .sortedByDescending { it.score }
            .take(2)
            .toList()
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return if (best.score >= 0.999f || second == null || best.score - second.score >= 0.15f) best else null
    }

    private fun correctAgentName(rawName: String): NameCorrection? {
        val normalizedRaw = normalizeNameText(rawName)
        if (normalizedRaw.isBlank()) return null
        val ranked = agentNames
            .map { candidate ->
                NameCorrection(candidate, agentNameSimilarity(normalizedRaw, normalizeNameText(candidate)))
            }
            .filter { it.score >= 0.55f }
            .sortedByDescending { it.score }
            .take(2)
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return if (best.score >= 0.999f || second == null || best.score - second.score >= 0.12f) best else null
    }

    private fun findExactAgentName(rawName: String): String? {
        val normalizedRaw = normalizeNameText(rawName)
        if (normalizedRaw.isBlank()) return null
        return agentNames.firstOrNull { normalizeNameText(it) == normalizedRaw }
    }

    private fun inferLockedAgentFromFates(agentName: String, rawFates: List<String>): AgentInference {
        val attr = AgentRepository.AGENT_MAP.getValue(agentName)
        val fateResult = CharacterImportFateScorer.scoreAgent(rawFates, attr.talents.values)
        return AgentInference(
            name = agentName,
            totalScore = 1f,
            nameScore = 1f,
            fateScore = fateResult.fateScore,
            fateHitCount = fateResult.hitCount,
            uniqueFateHitCount = fateResult.uniqueHitCount,
            uniqueFateScore = fateResult.uniqueScore,
            correctedFates = fateResult.correctedFates,
        )
    }

    private fun inferAgentFromNameAndFates(
        rawName: String,
        rawFates: List<String>,
        nameCorrection: NameCorrection?,
    ): AgentInference? {
        val normalizedName = normalizeNameText(rawName)
        val ranked = AgentRepository.AGENT_MAP.mapNotNull { (agentName, attr) ->
            val fateResult = CharacterImportFateScorer.scoreAgent(rawFates, attr.talents.values)
            val nameScore = if (normalizedName.isBlank()) {
                0f
            } else {
                agentNameSimilarity(normalizedName, normalizeNameText(agentName))
            }
            val totalScore = if (nameScore >= 0.55f) {
                nameScore * 0.35f + fateResult.fateScore * 0.65f
            } else {
                fateResult.fateScore
            }
            if (totalScore <= 0f) return@mapNotNull null
            AgentInference(
                name = agentName,
                totalScore = totalScore,
                nameScore = nameScore,
                fateScore = fateResult.fateScore,
                fateHitCount = fateResult.hitCount,
                uniqueFateHitCount = fateResult.uniqueHitCount,
                uniqueFateScore = fateResult.uniqueScore,
                correctedFates = fateResult.correctedFates,
            )
        }.sortedByDescending { it.totalScore }

        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return if (
            CharacterImportInferenceDecider.shouldAccept(
                rawName = rawName,
                bestName = best.name,
                bestTotalScore = best.totalScore,
                bestFateScore = best.fateScore,
                bestFateHitCount = best.fateHitCount,
                bestUniqueFateHitCount = best.uniqueFateHitCount,
                bestUniqueFateScore = best.uniqueFateScore,
                secondTotalScore = second?.totalScore,
                correctedName = nameCorrection?.displayText,
            )
        ) {
            best
        } else {
            null
        }
    }

    private fun normalizeFateText(text: String): String =
        CharacterImportFateScorer.normalizeFateText(text)

    private fun normalizeNameText(text: String): String =
        cleanDetectedAgentName(text).filter { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN || char.isLetterOrDigit()
        }

    private fun cleanDetectedAgentName(text: String): String =
        text.replace("命", "").replace("盘", "").trim()

    private fun agentNameSimilarity(left: String, right: String): Float {
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 2 && left.any { right.contains(it) } && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.65f
        }
        if (maxLength == 3 && levenshteinDistance(left, right) <= 1) {
            return if (left == right) 1f else 0.72f
        }
        return textSimilarity(left, right)
    }

    private fun fateTextSimilarity(left: String, right: String): Float {
        return CharacterImportFateScorer.fateTextSimilarity(left, right)
    }

    private fun textSimilarity(left: String, right: String): Float {
        if (left == right) return 1f
        val maxLength = maxOf(left.length, right.length)
        if (maxLength == 0) return 0f
        return (1f - levenshteinDistance(left, right).toFloat() / maxLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun formatLog(text: String): String =
        "\"" + text.replace("\n", "\\n") + "\""
}
