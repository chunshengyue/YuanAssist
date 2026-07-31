package com.example.yuanassist.ui.main

import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.ScreenshotStep
import com.example.yuanassist.utils.TemplateOverrideStore

data class DailyScriptDebugNode(
    val scriptFileName: String,
    val scriptDisplayName: String,
    val taskId: Int,
    val action: String,
    val assetTemplateDir: String?,
    val displayName: String?,
    val optionKey: String,
    val templateName: String?,
    val replacementTemplateName: String?,
    val threshold: Float,
    val delayMs: Long,
    val roi: ROI?,
    val targetText: String?,
    val buttonName: String?,
    val ocrTargetChars: List<String>,
    val ocrMinHitCount: Int?,
    val ocrPreprocess: String?,
)

data class DailyScriptDebugIndex(
    val scriptFileName: String,
    val scriptDisplayName: String,
    private val nodesByOption: Map<String, List<DailyScriptDebugNode>>,
) {
    val templateNames: List<String> = nodesByOption.keys.toList()

    fun nodesFor(optionKey: String): List<DailyScriptDebugNode> =
        nodesByOption[optionKey].orEmpty()

    fun thresholdFor(optionKey: String): Float? =
        nodesFor(optionKey).minOfOrNull(DailyScriptDebugNode::threshold)

    fun isOcrNode(optionKey: String): Boolean =
        nodesFor(optionKey).any { it.isOcrLike }

    fun displayNameFor(optionKey: String): String {
        val node = nodesFor(optionKey).firstOrNull()
        node?.displayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        if (node?.templateName != null) {
            return DailyScriptTemplateNames.displayNameFor(scriptFileName, node.templateName) ?: node.templateName
        }
        return node?.ocrDisplayName ?: optionKey
    }

    companion object {
        fun fromPlan(
            scriptFileName: String,
            scriptDisplayName: String,
            plan: DailyTaskPlan,
        ): DailyScriptDebugIndex {
            val nodesByOption = linkedMapOf<String, MutableList<DailyScriptDebugNode>>()
            val assetTemplateDir = plan.asset_template_dir?.trim()?.takeIf { it.isNotBlank() }
            plan.tasks.forEach { task ->
                task.toDebugNodes(scriptFileName, scriptDisplayName, assetTemplateDir).forEach { node ->
                    nodesByOption.getOrPut(node.optionKey) { mutableListOf() }.add(node)
                }
            }
            return DailyScriptDebugIndex(
                scriptFileName = scriptFileName,
                scriptDisplayName = scriptDisplayName,
                nodesByOption = nodesByOption,
            )
        }

        private fun DailyTask.toDebugNodes(
            scriptFileName: String,
            scriptDisplayName: String,
            assetTemplateDir: String?,
        ): List<DailyScriptDebugNode> {
            val params = params ?: return emptyList()
            return when (action) {
                "MATCH_TEMPLATE" -> listOfNotNull(
                    params.template_name?.let { templateName ->
                        DailyScriptDebugNode(
                            scriptFileName = scriptFileName,
                            scriptDisplayName = scriptDisplayName,
                            taskId = id,
                            action = action,
                            assetTemplateDir = assetTemplateDir,
                            displayName = name,
                            optionKey = templateName,
                            templateName = templateName,
                            replacementTemplateName = templateName,
                            threshold = params.threshold,
                            delayMs = delay,
                            roi = params.roi,
                            targetText = params.target_text,
                            buttonName = params.button_name,
                            ocrTargetChars = params.target_chars.orEmpty(),
                            ocrMinHitCount = params.min_hit_count,
                            ocrPreprocess = params.preprocess,
                        )
                    }
                )
                "OCR" -> {
                    val templateName = params.template_name
                    val replacementTemplateName = templateName
                        ?: TemplateOverrideStore.ocrTemplateFileName(scriptFileName, id)
                    listOf(
                        DailyScriptDebugNode(
                            scriptFileName = scriptFileName,
                            scriptDisplayName = scriptDisplayName,
                            taskId = id,
                            action = action,
                            assetTemplateDir = assetTemplateDir,
                            displayName = name,
                            optionKey = templateName ?: replacementTemplateName ?: "ocr:$id:ocr",
                            templateName = templateName,
                            replacementTemplateName = replacementTemplateName,
                            threshold = params.threshold,
                            delayMs = delay,
                            roi = params.roi,
                            targetText = params.target_text,
                            buttonName = params.button_name,
                            ocrTargetChars = params.target_chars.orEmpty(),
                            ocrMinHitCount = params.min_hit_count,
                            ocrPreprocess = params.preprocess,
                        )
                    )
                }
                "SCREENSHOT_GROUP" -> {
                    val groupRoi = params.roi
                    params.screenshot_steps.orEmpty().mapIndexedNotNull { index, step ->
                        step.toDebugNode(
                            scriptFileName = scriptFileName,
                            scriptDisplayName = scriptDisplayName,
                            task = this,
                            index = index,
                            groupRoi = groupRoi,
                            assetTemplateDir = assetTemplateDir,
                        )
                    }
                }
                else -> emptyList()
            }
        }

        private fun ScreenshotStep.toDebugNode(
            scriptFileName: String,
            scriptDisplayName: String,
            task: DailyTask,
            index: Int,
            groupRoi: ROI?,
            assetTemplateDir: String?,
        ): DailyScriptDebugNode? {
            val normalizedType = type.trim().uppercase()
            val isTemplate = normalizedType == "TEMPLATE" || normalizedType == "MATCH_TEMPLATE"
            val isOcr = normalizedType == "OCR"
            if (!isTemplate && !isOcr) return null
            val templateName = template_name
            val replacementTemplateName = if (isOcr) {
                templateName ?: TemplateOverrideStore.ocrTemplateFileName(
                    scriptFileName,
                    task.id,
                )?.replace("_ocr.png", "_step_${index + 1}_ocr.png")
            } else {
                templateName
            }
            val optionKey = templateName
                ?: replacementTemplateName
                ?: "screenshot_group:${task.id}:${index + 1}:$normalizedType"
            return DailyScriptDebugNode(
                scriptFileName = scriptFileName,
                scriptDisplayName = scriptDisplayName,
                taskId = task.id,
                action = if (isTemplate) "MATCH_TEMPLATE" else "OCR",
                assetTemplateDir = assetTemplateDir,
                displayName = name ?: task.name,
                optionKey = optionKey,
                templateName = templateName,
                replacementTemplateName = replacementTemplateName,
                threshold = threshold ?: task.params?.threshold ?: 0.8f,
                delayMs = task.delay,
                roi = roi ?: groupRoi,
                targetText = target_text,
                buttonName = button_name,
                ocrTargetChars = target_chars.orEmpty(),
                ocrMinHitCount = min_hit_count,
                ocrPreprocess = preprocess,
            )
        }
    }
}

val DailyScriptDebugNode.isOcrLike: Boolean
    get() = action == "OCR"

val DailyScriptDebugNode.ocrDisplayName: String
    get() {
        displayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        templateName?.let { return DailyScriptTemplateNames.displayNameFor(scriptFileName, it) ?: it }
        val target = when {
            ocrTargetChars.isNotEmpty() -> ocrTargetChars.joinToString("")
            !targetText.isNullOrBlank() -> targetText
            !buttonName.isNullOrBlank() -> buttonName
            else -> action
        }
        return "OCR ${taskId}: $target"
    }

object DailyScriptTemplateNames {
    private val namesByScriptAndTemplate: Map<String, Map<String, String>> = emptyMap()

    private val fallbackNamesByTemplate = mapOf(
        "baihu" to "白鹄行动按钮",
        "btnfilter" to "选人界面筛选",
        "digong" to "主页地宫入口",
        "dongku" to "如鸢洞窟入口",
        "dongku2" to "代号鸢洞窟入口",
        "lantai" to "主页兰台入口",
        "lixian" to "心纸-历险按钮",
        "queding2" to "战斗中返回确定按钮",
        "saodang" to "待办公务扫荡按钮",
        "xinzhi" to "主页心纸营建按钮",
        "xiayiceng" to "洞窟下一层（xiayiceng）",
        "yijirukou" to "遗迹入口",
        "zaicitiaozhan" to "失败后再次挑战按钮",
    )

    fun displayNameFor(scriptFileName: String, templateName: String): String? =
        namesByScriptAndTemplate[scriptFileName]?.get(templateName)
            ?: fallbackNamesByTemplate[normalizeTemplateName(templateName)]

    fun normalizeTemplateName(templateName: String): String =
        buildString {
            templateName
                .substringBeforeLast('.')
                .lowercase()
                .forEach { char ->
                    when (char) {
                        '（' -> append('(')
                        '）' -> append(')')
                        '_', ' ' -> Unit
                        else -> append(char)
                    }
                }
        }
}
