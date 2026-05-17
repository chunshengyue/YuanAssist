package com.example.yuanassist.ui.main

import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI

data class DailyScriptDebugNode(
    val scriptFileName: String,
    val scriptDisplayName: String,
    val taskId: Int,
    val action: String,
    val optionKey: String,
    val templateName: String?,
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
            plan.tasks.forEach { task ->
                val node = task.toDebugNode(scriptFileName, scriptDisplayName) ?: return@forEach
                nodesByOption.getOrPut(node.optionKey) { mutableListOf() }.add(node)
            }
            return DailyScriptDebugIndex(
                scriptFileName = scriptFileName,
                scriptDisplayName = scriptDisplayName,
                nodesByOption = nodesByOption,
            )
        }

        private fun DailyTask.toDebugNode(
            scriptFileName: String,
            scriptDisplayName: String,
        ): DailyScriptDebugNode? {
            val params = params ?: return null
            val optionKey = when (action) {
                "MATCH_TEMPLATE" -> params.template_name
                "OCR" -> params.template_name ?: "ocr:$id:ocr"
                else -> null
            } ?: return null
            return DailyScriptDebugNode(
                scriptFileName = scriptFileName,
                scriptDisplayName = scriptDisplayName,
                taskId = id,
                action = action,
                optionKey = optionKey,
                templateName = params.template_name,
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
    }
}

val DailyScriptDebugNode.isOcrLike: Boolean
    get() = action == "OCR"

val DailyScriptDebugNode.ocrDisplayName: String
    get() {
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
    private val namesByScriptAndTemplate = mapOf(
        "tu_fa_qing_kuang.json" to mapOf(
            "diaocha.png" to "突发情况开始调查按钮",
            "kuaisudiaocha.png" to "突发情况快速调查按钮",
            "sherou.png" to "突发情况鸟食按钮",
            "queren.png" to "突发情况确认按钮",
            "jiedao1.png" to "突发情况街道1",
            "huise_queding.png" to "突发情况灰色确定按钮",
            "biaoxian.png" to "突发情况表现按钮",
            "tiaoguo1.png" to "突发情况跳过按钮",
            "cunzi4.png" to "突发情况村子按钮",
            "shulin.png" to "突发情况树林按钮",
            "jiedao2.png" to "突发情况街道2",
            "taohua.png" to "突发情况桃花按钮",
            "queding.png" to "突发情况确定按钮",
        ),
        "xiao_dao_xiao_xi.json" to mapOf(
            "shouji.png" to "小道消息手机按钮",
            "shouqu.png" to "小道消息收取按钮",
            "yezi.png" to "小道消息叶子按钮",
            "mazi.png" to "小道消息鸟食按钮",
            "queren.png" to "小道消息确认按钮",
        ),
        "ta_de_chuan_wen.json" to mapOf(
            "large_diaocha.png" to "他的传闻大调查按钮",
            "jiangli.png" to "他的传闻奖励按钮",
            "zhuyu.png" to "他的传闻鸟食按钮",
            "queren.png" to "他的传闻确认按钮",
            "tiyan.png" to "他的传闻体验按钮",
            "chuanwen.png" to "他的传闻入口按钮",
            "tiaoguo.png" to "他的传闻跳过按钮",
            "xuanxiang.png" to "他的传闻选项按钮",
            "chukou.png" to "他的传闻出口按钮",
            "no_tiaoguo.png" to "他的传闻无跳过按钮",
        ),
        "dai_ban_gong_wu.json" to mapOf(
            "gongwu.png" to "待办公务入口按钮",
            "qianwang.png" to "待办公务前往按钮",
            "jizhi.png" to "待办公务鸟食按钮",
            "queren.png" to "待办公务确认按钮",
            "xuanze.png" to "待办公务选择按钮",
            "jiangli.png" to "待办公务奖励按钮",
            "tijiao.png" to "待办公务提交按钮",
        ),
        "zhu_xian_6_24.json" to mapOf(
            "6-24.png" to "主线624入口",
            "jinruguanqia.png" to "主线624进入关卡按钮",
            "queren.png" to "主线624确认按钮",
            "624.png" to "主线624关卡按钮",
            "queding.png" to "主线624战斗确定按钮",
            "chenggong.png" to "主线624成功按钮",
        ),
    )

    private val fallbackNamesByTemplate = mapOf(
        "6-24(1)" to "代号鸢624入口",
        "baihu" to "白鹄行动按钮",
        "btnfilter" to "选人界面筛选",
        "digong" to "主页地宫入口",
        "dongku" to "如鸢洞窟入口",
        "dongku2" to "代号鸢洞窟入口",
        "gushi" to "主线入口故事",
        "jinru" to "代号鸢624徐州篇进入按钮",
        "lantai" to "主页兰台入口",
        "lixian" to "心纸-历险按钮",
        "queding2" to "战斗中返回确定按钮",
        "saodang" to "待办公务扫荡按钮",
        "tfqk" to "鸢报界面确认",
        "xinzhi" to "主页心纸营建按钮",
        "xiayiceng" to "洞窟下一层（xiayiceng）",
        "yijirukou" to "遗迹入口",
        "youzhou" to "代号鸢主线右上角幽州篇",
        "yuanbao" to "主页鸢报入口",
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
