package com.example.yuanassist.ui

import android.app.AlertDialog as PlatformAlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yuanassist.core.LocalScriptJson
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.model.InstructionJson
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.StrategyPreviewData
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.model.toDisplaySummary
import com.example.yuanassist.network.StrategySavePayload
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.StoneStyleChoiceButton
import com.example.yuanassist.ui.subpage.SubpageFieldGroup
import com.example.yuanassist.ui.subpage.SubpagePaperPanel
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageToggleRow
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.DialogUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
    "吕布", "刘璋", "夏侯渊", "酆公珠", "酆公玖", "法正", "庞德",
    "SP陈登", "SP史子渺", "曹丕", "程普", "钟繇", "蒯良", "陈群",
    "卢植", "简雍", "郭女王", "周忠", "陈纪", "陈应",
)

private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
    "庞曦",
    "SP史子眇",
)

data class UploadTurnItem(
    val turnNum: Int,
    val actions: List<String>,
    val remark: String = "",
)

private enum class UploadImportMode(val label: String) {
    CURRENT("当前窗口"),
    LIBRARY("脚本库"),
    TEXT("文字导入"),
}

private enum class UploadAgentMode(val label: String, val type: Int) {
    SELECT("选择密探", 0),
    IMAGE("阵容截图", 1),
    TEXT("文字描述", 2),
}

private data class AgentSlotState(
    val name: String? = null,
    val starLevel: Int? = null,
    val talents: List<Int?> = listOf(null, null, null),
)

private data class UploadStrategyUiState(
    val title: String = "",
    val originalUrl: String = "",
    val content: String = "",
    val importMode: UploadImportMode = UploadImportMode.CURRENT,
    val agentMode: UploadAgentMode = UploadAgentMode.SELECT,
    val selectedScriptLabel: String = "未选择脚本",
    val importText: String = "",
    val tableItems: List<UploadTurnItem> = emptyList(),
    val instructionsInfo: String = "附带指令：当前窗口暂无附加指令",
    val instructionsJson: String? = null,
    val agentSlots: List<AgentSlotState> = List(5) { AgentSlotState() },
    val agentTextDesc: String = "",
    val agentImageUri: Uri? = null,
    val strategyImageUri: Uri? = null,
    val existingAgentImageUrl: String = "",
    val existingStrategyImageUrl: String = "",
    val attackDelayText: String = "2500",
    val skillDelayText: String = "4000",
    val waitTurnText: String = "8000",
    val publishing: Boolean = false,
    val loadingEdit: Boolean = false,
)

class UploadStrategyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_EDIT_MODE = "EXTRA_IS_EDIT_MODE"
        const val EXTRA_STRATEGY_ID = "EXTRA_STRATEGY_ID"
    }

    private var isEditMode = false
    private var editingStrategyId: String? = null
    private var currentImageUploadTarget = 0
    private var uiState by mutableStateOf(UploadStrategyUiState())

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        uiState = when (currentImageUploadTarget) {
            1 -> uiState.copy(agentImageUri = uri)
            2 -> uiState.copy(strategyImageUri = uri)
            else -> uiState
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)
        editingStrategyId = intent.getStringExtra(EXTRA_STRATEGY_ID)?.takeIf { it.isNotBlank() }
        loadSystemConfig()

        setContent {
            UploadStrategyScreen(
                state = uiState,
                isEditMode = isEditMode,
                onBack = ::finish,
                onStateChange = { uiState = it },
                onRefreshCurrent = { loadCurrentWindowData(showToast = true) },
                onPickScript = ::showScriptLibraryDialog,
                onParseText = ::parseImportText,
                onPickAgentImage = {
                    currentImageUploadTarget = 1
                    imagePickerLauncher.launch("image/*")
                },
                onPickStrategyImage = {
                    currentImageUploadTarget = 2
                    imagePickerLauncher.launch("image/*")
                },
                onPreview = ::previewStrategy,
                onPublish = ::publishStrategy,
            )
        }

        if (isEditMode) {
            loadStrategyForEdit()
        } else {
            loadCurrentWindowData(showToast = false)
        }
    }

    private fun loadSystemConfig() {
        val config = ConfigManager.getAllConfig(this)
        uiState = uiState.copy(
            attackDelayText = config.intervalAttack.toString(),
            skillDelayText = config.intervalSkill.toString(),
            waitTurnText = config.waitTurn.toString(),
        )
    }

    private fun loadStrategyForEdit() {
        val strategyId = editingStrategyId
        if (strategyId.isNullOrBlank()) {
            Toast.makeText(this, "缺少攻略ID，无法进入编辑模式", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        uiState = uiState.copy(loadingEdit = true)
        Toast.makeText(this, "正在加载攻略数据...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.getStrategyDetail(
            context = this,
            strategyId = strategyId,
            onSuccess = { detail ->
                if (isDestroyed || isFinishing) return@getStrategyDetail
                populateEditForm(detail)
            },
            onError = { message ->
                if (isDestroyed || isFinishing) return@getStrategyDetail
                Toast.makeText(this, "攻略加载失败：$message", Toast.LENGTH_LONG).show()
                finish()
            },
        )
    }

    private fun populateEditForm(detail: strategy_detail) {
        val config = parseConfig(detail.config)
        val mode = when (detail.agentType) {
            1 -> UploadAgentMode.IMAGE
            2 -> UploadAgentMode.TEXT
            else -> UploadAgentMode.SELECT
        }
        uiState = uiState.copy(
            title = detail.title,
            originalUrl = detail.originalPostUrl,
            content = detail.content,
            agentTextDesc = detail.agentTextDesc,
            attackDelayText = config.first.toString(),
            skillDelayText = config.second.toString(),
            waitTurnText = config.third.toString(),
            existingStrategyImageUrl = detail.strategyImage.orEmpty(),
            existingAgentImageUrl = detail.agentImageUrl.orEmpty(),
            agentMode = mode,
            agentSlots = if (mode == UploadAgentMode.SELECT) {
                parseAgentSelection(detail.agentSelection)
            } else {
                uiState.agentSlots
            },
            importMode = UploadImportMode.LIBRARY,
            selectedScriptLabel = "已载入原攻略脚本",
            tableItems = parseScriptContentToUploadItems(detail.scriptContent),
            instructionsJson = detail.instructions.takeIf { it.isNotBlank() },
            instructionsInfo = buildInstructionsInfoText(detail.instructions),
            loadingEdit = false,
        )
    }

    private fun parseConfig(configJson: String?): Triple<Long, Long, Long> {
        val current = Triple(
            uiState.attackDelayText.toLongOrNull() ?: 2500L,
            uiState.skillDelayText.toLongOrNull() ?: 4000L,
            uiState.waitTurnText.toLongOrNull() ?: 8000L,
        )
        if (configJson.isNullOrBlank()) return current
        return runCatching {
            val json = org.json.JSONObject(configJson)
            Triple(
                json.optLong("intervalAttack", current.first),
                json.optLong("intervalSkill", current.second),
                json.optLong("waitTurn", current.third),
            )
        }.getOrDefault(current)
    }

    private fun parseAgentSelection(agentSelectionJson: String?): List<AgentSlotState> {
        val slots = MutableList(5) { AgentSlotState() }
        if (agentSelectionJson.isNullOrBlank()) return slots
        val type = object : TypeToken<List<String>>() {}.type
        val rawAgents = runCatching {
            Gson().fromJson<List<String>>(agentSelectionJson, type)
        }.getOrNull().orEmpty()

        rawAgents.take(5).forEachIndexed { index, raw ->
            val rawText = raw.trim()
            if (rawText.isBlank()) return@forEachIndexed
            val starLevel = rawText.takeWhile { it.isDigit() }.toIntOrNull()
            val name = rawText.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
            if (name.isBlank()) return@forEachIndexed
            val talents = rawText.substringAfter("-", "")
                .takeIf { it.isNotBlank() }
                ?.split("、")
                ?.map { it.trim().toIntOrNull() }
                .orEmpty()
            slots[index] = AgentSlotState(
                name = name,
                starLevel = starLevel,
                talents = List(3) { talentIndex -> talents.getOrNull(talentIndex) },
            )
        }
        return slots
    }

    private fun buildInstructionsInfoText(instructionsJson: String?): String {
        if (instructionsJson.isNullOrBlank()) return "该脚本无附加指令"
        val type = object : TypeToken<List<InstructionJson>>() {}.type
        val instructions = runCatching {
            Gson().fromJson<List<InstructionJson>>(instructionsJson, type)
        }.getOrNull().orEmpty()
        if (instructions.isEmpty()) return "该脚本无附加指令"
        val text = instructions.joinToString("\n") { it.toDisplaySummary() }
        return "脚本附带指令：\n$text"
    }

    private fun parseScriptContentToUploadItems(text: String): List<UploadTurnItem> {
        if (text.isBlank()) return emptyList()
        val realText = text.replace("\\n", "\n").replace("\\t", "\t")
        val items = mutableListOf<UploadTurnItem>()
        var currentTurn = 1
        for (line in realText.split("\n")) {
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) continue
            val parts = if (rawLine.contains("\t")) rawLine.split("\t") else rawLine.trim().split(Regex("\\s+"))
            val startIndex = if (parts.isNotEmpty() && (parts[0].contains("回") || parts[0].all { it.isDigit() })) 1 else 0
            val effectiveStartIndex = if (startIndex == 1 && parts.firstOrNull()?.trim().isNullOrEmpty()) 0 else startIndex
            val actions = mutableListOf<String>()
            for (i in effectiveStartIndex until parts.size) {
                if (actions.size >= 5) break
                val actionText = parts[i].trim()
                actions.add(if (actionText == "-") "" else actionText)
            }
            while (actions.size < 5) actions.add("")
            items.add(UploadTurnItem(currentTurn, actions))
            currentTurn++
        }
        return items
    }

    private fun loadCurrentWindowData(showToast: Boolean) {
        val service = YuanAssistService.instance
        if (service == null) {
            if (showToast) Toast.makeText(this, "无障碍服务未运行，无法获取当前窗口", Toast.LENGTH_SHORT).show()
            uiState = uiState.copy(tableItems = emptyList())
            return
        }

        val currentData = service.currentDisplayData
        if (currentData.isEmpty()) {
            if (showToast) Toast.makeText(this, "当前窗口暂无动作数据", Toast.LENGTH_SHORT).show()
            uiState = uiState.copy(tableItems = emptyList())
            return
        }

        val items = currentData.map { turnData ->
            UploadTurnItem(
                turnNum = turnData.turnNumber,
                actions = turnData.characterActions.map { it.toString() },
            )
        }

        val insts = service.getExportableInstructions()
        val nextState = if (insts.isNotEmpty()) {
            val text = insts.joinToString("\n") { it.toDisplaySummary() }
            uiState.copy(
                importMode = UploadImportMode.CURRENT,
                tableItems = items,
                instructionsJson = Gson().toJson(insts),
                instructionsInfo = "当前窗口附带指令：\n$text",
            )
        } else {
            uiState.copy(
                importMode = UploadImportMode.CURRENT,
                tableItems = items,
                instructionsJson = null,
                instructionsInfo = "附带指令：当前窗口暂无附加指令",
            )
        }
        uiState = nextState
        if (showToast) Toast.makeText(this, "已刷新当前窗口数据", Toast.LENGTH_SHORT).show()
    }

    private fun showScriptLibraryDialog() {
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        if (files.isEmpty()) {
            Toast.makeText(this, "本地脚本库为空", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name.removeSuffix(".json") }.toTypedArray()
        PlatformAlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("请选择要导入的脚本")
            .setAdapter(DialogUtils.fixedOptionTextAdapter(this, fileNames)) { _, which ->
                runCatching {
                    val scriptObj = Gson().fromJson(files[which].readText(Charsets.UTF_8), LocalScriptJson::class.java)
                    val items = parseTextToUploadItems(scriptObj.scriptContent)
                    if (items.isEmpty()) {
                        Toast.makeText(this, "脚本解析为空", Toast.LENGTH_SHORT).show()
                    } else {
                        val instructionsJson = if (!scriptObj.instructions.isNullOrEmpty()) {
                            Gson().toJson(scriptObj.instructions)
                        } else {
                            null
                        }
                        val instructionsInfo = if (!scriptObj.instructions.isNullOrEmpty()) {
                            val instText = scriptObj.instructions.joinToString("\n") { it.toDisplaySummary() }
                            "脚本附带指令：\n$instText"
                        } else {
                            "该脚本无附加指令"
                        }
                        uiState = uiState.copy(
                            importMode = UploadImportMode.LIBRARY,
                            selectedScriptLabel = scriptObj.title ?: fileNames[which],
                            tableItems = items,
                            instructionsJson = instructionsJson,
                            instructionsInfo = instructionsInfo,
                        )
                    }
                }.onFailure {
                    Toast.makeText(this, "脚本解析失败", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun parseImportText() {
        val text = uiState.importText
        if (text.isBlank()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }
        val items = parseTextToUploadItems(text)
        if (items.isEmpty()) {
            Toast.makeText(this, "解析失败，请检查格式", Toast.LENGTH_SHORT).show()
            return
        }
        uiState = uiState.copy(
            importMode = UploadImportMode.TEXT,
            tableItems = items,
            instructionsJson = null,
            instructionsInfo = "文字导入成功",
        )
        Toast.makeText(this, "阵容与动作已装载", Toast.LENGTH_SHORT).show()
    }

    private fun parseTextToUploadItems(text: String): List<UploadTurnItem> {
        try {
            val jsonObject = org.json.JSONObject(text)
            if (jsonObject.has("opers") && jsonObject.has("actions")) {
                val opersArray = jsonObject.getJSONArray("opers")
                val updatedSlots = uiState.agentSlots.toMutableList()
                for (i in 0 until minOf(5, opersArray.length())) {
                    val agentName = opersArray.getJSONObject(i).optString("name").trim()
                    if (agentName.isNotEmpty()) {
                        updatedSlots[i] = updatedSlots[i].copy(name = agentName)
                    }
                }

                val items = mutableListOf<UploadTurnItem>()
                val actionsObj = jsonObject.getJSONObject("actions")
                val turnKeys = actionsObj.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()
                for (turn in turnKeys) {
                    val turnActionsArray = actionsObj.getJSONArray(turn.toString())
                    val rowActions = mutableListOf<String>()
                    for (i in 0 until minOf(5, turnActionsArray.length())) {
                        val innerArray = turnActionsArray.getJSONArray(i)
                        var actionText = if (innerArray.length() > 0) innerArray.getString(0) else ""
                        actionText = actionText.replace("大", "↑")
                            .replace("下", "↓")
                            .replace("普", "A")
                        rowActions.add(actionText)
                    }
                    while (rowActions.size < 5) rowActions.add("")
                    items.add(UploadTurnItem(turn, rowActions))
                }
                uiState = uiState.copy(agentSlots = updatedSlots, agentMode = UploadAgentMode.SELECT)
                return items
            }
        } catch (_: Exception) {
        }

        val items = mutableListOf<UploadTurnItem>()
        var currentTurn = 1
        for (line in text.split("\n")) {
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) continue
            val parts = if (rawLine.contains("\t")) rawLine.split("\t") else rawLine.trim().split(Regex("\\s+"))
            val startIndex = if (parts.isNotEmpty() && (parts[0].contains("回") || parts[0].all { it.isDigit() })) 1 else 0
            val effectiveStartIndex = if (startIndex == 1 && parts.firstOrNull()?.trim().isNullOrEmpty()) 0 else startIndex
            val actions = mutableListOf<String>()
            for (i in effectiveStartIndex until parts.size) {
                if (actions.size >= 5) break
                val actionText = parts[i].trim()
                actions.add(if (actionText == "-") "" else actionText)
            }
            while (actions.size < 5) actions.add("")
            items.add(UploadTurnItem(currentTurn, actions))
            currentTurn++
        }
        return items
    }

    private fun previewStrategy() {
        val aType = uiState.agentMode.type
        val previewData = StrategyPreviewData(
            title = uiState.title.ifBlank { "未命名攻略" },
            content = uiState.content,
            attackDelay = uiState.attackDelayText.toLongOrNull() ?: 2500L,
            skillDelay = uiState.skillDelayText.toLongOrNull() ?: 4000L,
            waitTurn = uiState.waitTurnText.toLongOrNull() ?: 8000L,
            strategyImageUri = uiState.strategyImageUri?.toString() ?: uiState.existingStrategyImageUrl.takeIf { it.isNotBlank() },
            agentType = aType,
            agentSelection = if (aType == 0) buildAgentSelection(uiState.agentSlots) else emptyList(),
            agentImageUri = uiState.agentImageUri?.toString() ?: uiState.existingAgentImageUrl.takeIf { it.isNotBlank() },
            agentTextDesc = uiState.agentTextDesc,
            tableData = uiState.tableItems,
            instructionsJson = uiState.instructionsJson,
        )

        val intent = Intent(this, StrategyDetailActivity::class.java).apply {
            putExtra("IS_PREVIEW", true)
            putExtra("PREVIEW_DATA_JSON", Gson().toJson(previewData))
        }
        startActivity(intent)
    }

    private fun publishStrategy() {
        val title = uiState.title.trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "攻略标题不能为空！", Toast.LENGTH_SHORT).show()
            return
        }
        uiState = uiState.copy(publishing = true)
        Toast.makeText(this, "正在验证身份...", Toast.LENGTH_SHORT).show()
        ensureUserLoggedIn(
            onSuccess = { user -> uploadImagesAndPublish(title, uiState.originalUrl.trim(), user) },
            onError = { message ->
                uiState = uiState.copy(publishing = false)
                Toast.makeText(this, "登录失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun ensureUserLoggedIn(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser != null) {
            onSuccess(currentUser)
            return
        }
        SupabaseRepository.loginWithDevice(
            context = this,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    private fun uriToCacheFile(uri: Uri, prefix: String): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
            file.outputStream().use { output -> inputStream.use { it.copyTo(output) } }
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadImagesAndPublish(title: String, originalUrl: String, author: MyUser) {
        Toast.makeText(this, "正在处理数据...", Toast.LENGTH_SHORT).show()
        val aType = uiState.agentMode.type
        val builtAgents = if (aType == 0) buildAgentSelection(uiState.agentSlots) else emptyList()
        val scriptContentStr = uiState.tableItems.joinToString("\n") { item ->
            "${item.turnNum}回合\t${item.actions.joinToString("\t")}"
        }
        val configJson = Gson().toJson(
            mapOf(
                "intervalAttack" to (uiState.attackDelayText.toLongOrNull() ?: 2500L),
                "intervalSkill" to (uiState.skillDelayText.toLongOrNull() ?: 4000L),
                "waitTurn" to (uiState.waitTurnText.toLongOrNull() ?: 8000L),
            ),
        )
        val agentFile = if (aType == 1) uiState.agentImageUri?.let { uriToCacheFile(it, "agent") } else null
        val strategyFile = uiState.strategyImageUri?.let { uriToCacheFile(it, "strategy") }
        val filesToUpload = listOfNotNull(agentFile, strategyFile)

        if (filesToUpload.isEmpty()) {
            executeFinalPublish(
                title = title,
                author = author,
                aType = aType,
                builtAgents = builtAgents,
                agentUrl = if (aType == 1) uiState.existingAgentImageUrl else "",
                strategyUrl = uiState.existingStrategyImageUrl,
                scriptContent = scriptContentStr,
                config = configJson,
                content = uiState.content.trim(),
                instructions = uiState.instructionsJson ?: "",
                originalUrl = originalUrl,
            )
            return
        }

        Toast.makeText(this, "正在上传图片到图床...", Toast.LENGTH_SHORT).show()
        var uploadedCount = 0
        var hasError = false
        val uploadedUrls = mutableMapOf<File, String>()
        filesToUpload.forEach { file ->
            uploadImageToImageBed(
                file = file,
                onSuccess = { url ->
                    if (hasError) return@uploadImageToImageBed
                    uploadedUrls[file] = url
                    uploadedCount++
                    if (uploadedCount == filesToUpload.size) {
                        runOnUiThread {
                            val finalAgentUrl = if (aType == 1) {
                                agentFile?.let { uploadedUrls[it] } ?: uiState.existingAgentImageUrl
                            } else {
                                ""
                            }
                            val finalStrategyUrl = strategyFile?.let { uploadedUrls[it] } ?: uiState.existingStrategyImageUrl
                            executeFinalPublish(
                                title = title,
                                author = author,
                                aType = aType,
                                builtAgents = builtAgents,
                                agentUrl = finalAgentUrl,
                                strategyUrl = finalStrategyUrl,
                                scriptContent = scriptContentStr,
                                config = configJson,
                                content = uiState.content.trim(),
                                instructions = uiState.instructionsJson ?: "",
                                originalUrl = originalUrl,
                            )
                            filesToUpload.forEach { it.delete() }
                        }
                    }
                },
                onError = { errorMsg ->
                    if (!hasError) {
                        hasError = true
                        runOnUiThread {
                            uiState = uiState.copy(publishing = false)
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                            filesToUpload.forEach { it.delete() }
                        }
                    }
                },
            )
        }
    }

    private fun executeFinalPublish(
        title: String,
        author: MyUser,
        aType: Int,
        builtAgents: List<String>,
        agentUrl: String,
        strategyUrl: String,
        scriptContent: String,
        config: String,
        content: String,
        instructions: String,
        originalUrl: String,
    ) {
        val editId = editingStrategyId
        val coverUrl = when {
            strategyUrl.isNotEmpty() -> strategyUrl
            agentUrl.isNotEmpty() -> agentUrl
            else -> ""
        }
        val agentNames = builtAgents.map { raw ->
            raw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
        }
        val payload = StrategySavePayload(
            strategyId = if (isEditMode && !editId.isNullOrBlank()) editId else null,
            title = title,
            content = content,
            scriptContent = scriptContent,
            config = config,
            instructions = instructions,
            strategyImage = strategyUrl,
            agents = agentNames.joinToString("、"),
            coverUrl = coverUrl,
            originalPostUrl = originalUrl,
            agentType = aType,
            agentSelection = Gson().toJson(builtAgents),
            agentImageUrl = agentUrl,
            agentTextDesc = uiState.agentTextDesc.trim(),
        )

        Toast.makeText(this, "正在保存攻略数据...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.saveStrategy(
            context = this,
            payload = payload,
            onSuccess = {
                Toast.makeText(this, if (isEditMode) "保存成功！" else "发布成功！", Toast.LENGTH_LONG).show()
                finish()
            },
            onError = { message ->
                uiState = uiState.copy(publishing = false)
                Toast.makeText(this, if (isEditMode) "保存失败: $message" else "发布失败: $message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun buildAgentSelection(slots: List<AgentSlotState>): List<String> {
        return slots.mapNotNull { slot ->
            val name = slot.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val starPrefix = when (slot.starLevel) {
                1, 2, 3, 4, 5 -> slot.starLevel.toString()
                6 -> "6"
                else -> ""
            }
            val talentsStr = slot.talents.mapNotNull { it }.joinToString("、")
            if (talentsStr.isNotEmpty()) "$starPrefix$name-$talentsStr" else "$starPrefix$name"
        }
    }

    private fun uploadImageToImageBed(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val uploadUrl = "https://img.scdn.io/api/v1.php"
        val client = okhttp3.OkHttpClient()
        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, fileBody)
            .addFormDataPart("outputFormat", "jpeg")
            .build()
        val request = okhttp3.Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError("图床网络请求失败: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = org.json.JSONObject(responseBody)
                        if (json.optBoolean("success")) {
                            onSuccess(json.optString("url"))
                        } else {
                            onError(json.optString("message", "上传被图床拒绝"))
                        }
                    } catch (e: Exception) {
                        onError("图床JSON解析失败: ${e.message}")
                    }
                } else {
                    onError("图床服务器错误: HTTP ${response.code}")
                }
            }
        })
    }
}

@Composable
private fun UploadStrategyScreen(
    state: UploadStrategyUiState,
    isEditMode: Boolean,
    onBack: () -> Unit,
    onStateChange: (UploadStrategyUiState) -> Unit,
    onRefreshCurrent: () -> Unit,
    onPickScript: () -> Unit,
    onParseText: () -> Unit,
    onPickAgentImage: () -> Unit,
    onPickStrategyImage: () -> Unit,
    onPreview: () -> Unit,
    onPublish: () -> Unit,
) {
    var agentPickerSlot by remember { mutableIntStateOf(-1) }
    var talentPickerTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    SubpageScaffold(
        title = if (isEditMode) "编辑攻略" else "发布攻略",
        subtitle = "阵容、脚本、原帖与说明",
        onBack = onBack,
    ) {
        if (state.loadingEdit) {
            SubpageSectionCard(title = "正在载入", subtitle = "稍候会回填原攻略内容") {
                BodyText("请稍候...")
            }
        }

        BasicInfoSection(state, onStateChange)
        ScriptSection(
            state = state,
            onStateChange = onStateChange,
            onRefreshCurrent = onRefreshCurrent,
            onPickScript = onPickScript,
            onParseText = onParseText,
        )
        AgentSection(
            state = state,
            onStateChange = onStateChange,
            onPickAgentImage = onPickAgentImage,
            onOpenAgentPicker = { agentPickerSlot = it },
            onOpenTalentPicker = { slotIndex, talentIndex ->
                talentPickerTarget = slotIndex to talentIndex
            },
        )
        ImageSection(state, onPickStrategyImage)
        ActionSection(
            publishing = state.publishing,
            isEditMode = isEditMode,
            onPreview = onPreview,
            onPublish = onPublish,
        )
    }

    if (agentPickerSlot >= 0) {
        SharedAgentPickerDialog(
            onDismiss = { agentPickerSlot = -1 },
            onSelect = { agentName ->
                val slots = state.agentSlots.toMutableList()
                val current = slots[agentPickerSlot]
                slots[agentPickerSlot] = current.copy(
                    name = agentName,
                    talents = if (current.name == agentName) current.talents else listOf(null, null, null),
                )
                onStateChange(state.copy(agentSlots = slots))
                agentPickerSlot = -1
            },
        )
    }

    talentPickerTarget?.let { (slotIndex, talentIndex) ->
        val slot = state.agentSlots.getOrNull(slotIndex)
        val agentName = slot?.name
        if (!agentName.isNullOrBlank()) {
            val selectedTalentId = resolveTalentIdByLabel(agentName, slot.talents.getOrNull(talentIndex)?.let {
                resolveTalentLabel(agentName, it).orEmpty()
            }.orEmpty()) ?: slot.talents.getOrNull(talentIndex)
            val usedTalentIds = slot.talents.mapIndexedNotNull { index, value ->
                value?.takeIf { index != talentIndex }
            }.toSet()
            SharedTalentPickerDialog(
                agentName = agentName,
                selectedTalentId = selectedTalentId,
                usedTalentIds = usedTalentIds,
                onDismiss = { talentPickerTarget = null },
                onSelect = { talentId ->
                    val talents = slot.talents.toMutableList()
                    talents[talentIndex] = talentId
                    val slots = state.agentSlots.toMutableList()
                    slots[slotIndex] = slot.copy(talents = talents)
                    onStateChange(state.copy(agentSlots = slots))
                    talentPickerTarget = null
                },
            )
        } else {
            talentPickerTarget = null
        }
    }
}

@Composable
private fun BasicInfoSection(
    state: UploadStrategyUiState,
    onStateChange: (UploadStrategyUiState) -> Unit,
) {
    SubpageSectionCard(title = "基础信息", subtitle = "标题会展示在攻略列表里") {
        SubpageTextField(
            value = state.title,
            onValueChange = { onStateChange(state.copy(title = it)) },
            label = "攻略标题",
            modifier = Modifier.fillMaxWidth(),
        )
        SubpageTextField(
            value = state.originalUrl,
            onValueChange = { onStateChange(state.copy(originalUrl = it)) },
            label = "原帖链接",
            modifier = Modifier.fillMaxWidth(),
        )
        SubpageTextField(
            value = state.content,
            onValueChange = { onStateChange(state.copy(content = it)) },
            label = "攻略说明",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            singleLine = false,
        )
        DelayFields(state, onStateChange)
    }
}

@Composable
private fun DelayFields(
    state: UploadStrategyUiState,
    onStateChange: (UploadStrategyUiState) -> Unit,
) {
    SubpageFieldGroup(title = "执行参数", subtitle = "单位为毫秒") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericTextField(
                value = state.attackDelayText,
                onValueChange = { onStateChange(state.copy(attackDelayText = it.digitsOnly())) },
                label = "普攻",
                modifier = Modifier.weight(1f),
            )
            NumericTextField(
                value = state.skillDelayText,
                onValueChange = { onStateChange(state.copy(skillDelayText = it.digitsOnly())) },
                label = "技能",
                modifier = Modifier.weight(1f),
            )
            NumericTextField(
                value = state.waitTurnText,
                onValueChange = { onStateChange(state.copy(waitTurnText = it.digitsOnly())) },
                label = "等待",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScriptSection(
    state: UploadStrategyUiState,
    onStateChange: (UploadStrategyUiState) -> Unit,
    onRefreshCurrent: () -> Unit,
    onPickScript: () -> Unit,
    onParseText: () -> Unit,
) {
    SubpageSectionCard(title = "脚本", subtitle = "动作表会随导入方式刷新") {
        SegmentedButtons(
            labels = UploadImportMode.entries.map { it.label },
            selectedIndex = state.importMode.ordinal,
            onSelect = { index ->
                val mode = UploadImportMode.entries[index]
                onStateChange(state.copy(importMode = mode))
                when (mode) {
                    UploadImportMode.CURRENT -> onRefreshCurrent()
                    UploadImportMode.LIBRARY -> onPickScript()
                    UploadImportMode.TEXT -> Unit
                }
            },
        )

        when (state.importMode) {
            UploadImportMode.CURRENT -> {
                StoneStyleButton(text = "重新读取当前窗口", onClick = onRefreshCurrent)
            }

            UploadImportMode.LIBRARY -> {
                SubpagePaperPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BodyText("已选脚本")
                            TitleText(state.selectedScriptLabel)
                        }
                        StoneStyleButton(
                            text = "选择",
                            onClick = onPickScript,
                            modifier = Modifier.width(96.dp),
                            minHeight = 42.dp,
                        )
                    }
                }
            }

            UploadImportMode.TEXT -> {
                SubpageTextField(
                    value = state.importText,
                    onValueChange = { onStateChange(state.copy(importText = it)) },
                    label = "粘贴脚本 JSON 或表格文本",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    singleLine = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StoneStyleButton(
                        text = "解析文本",
                        onClick = onParseText,
                        modifier = Modifier.weight(1f),
                    )
                    StoneStyleButton(
                        text = "清空",
                        onClick = { onStateChange(state.copy(importText = "", tableItems = emptyList())) },
                        modifier = Modifier.weight(1f),
                        selected = false,
                    )
                }
            }
        }

        ScriptTable(
            items = state.tableItems,
            instructionsInfo = state.instructionsInfo,
            onRemarkChange = { index, remark ->
                val next = state.tableItems.toMutableList()
                next[index] = next[index].copy(remark = remark)
                onStateChange(state.copy(tableItems = next))
            },
        )
    }
}

@Composable
private fun ScriptTable(
    items: List<UploadTurnItem>,
    instructionsInfo: String,
    onRemarkChange: (Int, String) -> Unit,
) {
    SubpagePaperPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyText(instructionsInfo)
            if (items.isEmpty()) {
                BodyText("暂无动作表")
            } else {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TableHeader()
                        items.forEachIndexed { index, item ->
                            TableRow(item = item, index = index, onRemarkChange = onRemarkChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TableCell("回合", width = 54.dp, header = true)
        repeat(5) { TableCell("${it + 1}号", width = 66.dp, header = true) }
        TableCell("备注", width = 150.dp, header = true)
    }
}

@Composable
private fun TableRow(
    item: UploadTurnItem,
    index: Int,
    onRemarkChange: (Int, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TableCell(item.turnNum.toString(), width = 54.dp)
        repeat(5) { actionIndex ->
            val action = item.actions.getOrNull(actionIndex).orEmpty()
            TableCell(if (action.isBlank()) "-" else action, width = 66.dp)
        }
        OutlinedTextField(
            value = item.remark,
            onValueChange = { onRemarkChange(index, it) },
            modifier = Modifier.width(150.dp),
            singleLine = true,
            label = { Text("备注", fontFamily = FontFamily.Serif) },
            shape = RoundedCornerShape(14.dp),
            colors = yuanOutlinedTextFieldColors(),
        )
    }
}

@Composable
private fun AgentSection(
    state: UploadStrategyUiState,
    onStateChange: (UploadStrategyUiState) -> Unit,
    onPickAgentImage: () -> Unit,
    onOpenAgentPicker: (Int) -> Unit,
    onOpenTalentPicker: (Int, Int) -> Unit,
) {
    SubpageSectionCard(title = "密探", subtitle = "可选阵容、截图或文字说明") {
        SegmentedButtons(
            labels = UploadAgentMode.entries.map { it.label },
            selectedIndex = state.agentMode.ordinal,
            onSelect = { onStateChange(state.copy(agentMode = UploadAgentMode.entries[it])) },
        )

        when (state.agentMode) {
            UploadAgentMode.SELECT -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.agentSlots.forEachIndexed { index, slot ->
                        AgentSlotCard(
                            index = index,
                            slot = slot,
                            onSelectAgent = { onOpenAgentPicker(index) },
                            onClear = {
                                val slots = state.agentSlots.toMutableList()
                                slots[index] = AgentSlotState()
                                onStateChange(state.copy(agentSlots = slots))
                            },
                            onStarChange = { star ->
                                val slots = state.agentSlots.toMutableList()
                                slots[index] = slot.copy(starLevel = star)
                                onStateChange(state.copy(agentSlots = slots))
                            },
                            onOpenTalentPicker = { talentIndex -> onOpenTalentPicker(index, talentIndex) },
                        )
                    }
                }
            }

            UploadAgentMode.IMAGE -> {
                ImageUploadPanel(
                    title = "阵容截图",
                    model = state.agentImageUri ?: state.existingAgentImageUrl.takeIf { it.isNotBlank() },
                    onClick = onPickAgentImage,
                )
            }

            UploadAgentMode.TEXT -> {
                SubpageTextField(
                    value = state.agentTextDesc,
                    onValueChange = { onStateChange(state.copy(agentTextDesc = it)) },
                    label = "密探文字描述",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp),
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun AgentSlotCard(
    index: Int,
    slot: AgentSlotState,
    onSelectAgent: () -> Unit,
    onClear: () -> Unit,
    onStarChange: (Int?) -> Unit,
    onOpenTalentPicker: (Int) -> Unit,
) {
    SubpagePaperPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AgentAvatar(slot.name, Modifier.size(46.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BodyText("${index + 1}号位")
                    TitleText(slot.name ?: "未选择密探")
                }
                StoneStyleButton(
                    text = if (slot.name == null) "选择" else "更换",
                    onClick = onSelectAgent,
                    modifier = Modifier.width(82.dp),
                    minHeight = 40.dp,
                )
                if (slot.name != null) {
                    Text(
                        text = "清空",
                        color = HighlightGold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClear,
                        ),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null, 1, 2, 3, 4, 5, 6).forEach { star ->
                    val text = when (star) {
                        null -> "无"
                        6 -> "觉醒"
                        else -> "${star}星"
                    }
                    SmallChoice(text = text, selected = slot.starLevel == star, onClick = { onStarChange(star) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { talentIndex ->
                    val label = resolveTalentLabel(slot.name, slot.talents.getOrNull(talentIndex))
                    TalentValueChip(
                        text = label ?: "命盘${talentIndex + 1}",
                        placeholder = label == null,
                        modifier = Modifier.weight(1f),
                        onClick = slot.name?.let {
                            { onOpenTalentPicker(talentIndex) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSection(
    state: UploadStrategyUiState,
    onPickStrategyImage: () -> Unit,
) {
    SubpageSectionCard(title = "攻略原图", subtitle = "可作为详情页封面或补充截图") {
        ImageUploadPanel(
            title = "攻略图片",
            model = state.strategyImageUri ?: state.existingStrategyImageUrl.takeIf { it.isNotBlank() },
            onClick = onPickStrategyImage,
        )
    }
}

@Composable
private fun ImageUploadPanel(
    title: String,
    model: Any?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.34f))
            .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TitleText(title)
                BodyText("点击选择图片")
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ActionSection(
    publishing: Boolean,
    isEditMode: Boolean,
    onPreview: () -> Unit,
    onPublish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StoneStyleButton(
            text = "预览",
            onClick = onPreview,
            modifier = Modifier.weight(1f),
            selected = false,
        )
        StoneStyleButton(
            text = when {
                publishing -> "保存中..."
                isEditMode -> "保存"
                else -> "发布"
            },
            onClick = { if (!publishing) onPublish() },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SegmentedButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            StoneStyleChoiceButton(
                text = label,
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SmallChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFFFFF2D2) else Color.White.copy(alpha = 0.34f))
            .border(1.dp, if (selected) HighlightGold.copy(alpha = 0.72f) else GlassStroke.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TitleInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Serif) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = yuanOutlinedTextFieldColors(),
    )
}

@Composable
private fun yuanOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GlassPanel,
    unfocusedContainerColor = GlassPanel,
    focusedBorderColor = HighlightGold,
    unfocusedBorderColor = GlassStroke.copy(alpha = 0.55f),
    focusedTextColor = TitleInk,
    unfocusedTextColor = TitleInk,
    focusedLabelColor = HighlightGold,
    unfocusedLabelColor = BodyInk,
    cursorColor = HighlightGold,
)

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, header: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (header) Color(0xFFFFF1CE) else Color.White.copy(alpha = 0.32f))
            .border(1.dp, GlassStroke.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (text == "-") QuietInk else TitleInk,
            fontSize = 13.sp,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Serif,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TitleText(text: String) {
    Text(
        text = text,
        color = TitleInk,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Serif,
        letterSpacing = 0.sp,
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        color = BodyInk,
        fontSize = 13.sp,
        fontFamily = FontFamily.Serif,
        letterSpacing = 0.sp,
    )
}

private fun String.digitsOnly(): String = filter { it.isDigit() }
