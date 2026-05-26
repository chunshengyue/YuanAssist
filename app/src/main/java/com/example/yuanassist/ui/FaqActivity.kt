package com.example.yuanassist.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageEmptyState
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField

private data class FaqItem(
    val question: String,
    val answer: String,
    val expanded: Boolean = false,
)

class FaqActivity : AppCompatActivity() {

    private val allFaqItems = listOf(
        FaqItem(
            question = "最前面的一些话，关于大家关心的无障碍安全问题、内存问题等等",
            answer = "1.本工具本质上是可以进行图像识别的连点器，不修改游戏任何数据，只是代替你进行手点完成表格跟打等任务。市面上有类似的产品，但是YuanAssist预置好了脚本，只需要根据自己的手机进行微调，更方便。\n\n2.YuanAssit有获取屏幕进行识别的功能，有没有隐私风险？理论上本工具比你在应用商店下载的所有连点器都要安全，因为代码已经在github上开源了，逻辑也很清楚。\n\n3.图像识别中间保存的图片会不会及时清理？会！我在开发的时候很注重这些问题，不会导致手机内存增加，常见的崩溃异常问题也会考虑到，开发到现在已逾三月，没有人反馈说本工具有增加内存、卡死等问题。\n\n4.会不会出现手机不兼容的情况？大部分安卓手机运行都没有问题，业务上也做了不同机型适配。但是鸿蒙系统有点捉摸不透，如果鸿蒙系统运行出现问题请询问开发者。",
        ),
        FaqItem(
            question = "刷鸟食/624运行之后一直在主界面返回？",
            answer = "先到功能测试里选择对应的任务，如刷鸟食对应任务“鸢报界面导航”，素材选“主页鸢报入口”，上传主页截图并并进行局部识别，看看能不能匹配到入口按钮，如匹配不到（特别是代号鸢那个带雪的背景，是识别不到的！MaaYuan也出现了类似的问题），请点击【一键替换】，把红框挪到按钮上，并点击确定。\n如果再次运行依然连续返回，请联系开发者。",
        ),
        FaqItem(
            question = "突发情况一进入场景就停下？",
            answer = "大概率是当前场景识别不到，突发情况有几个场景：白天/夜晚的街道，树林，桃花，荒村，请上传场景截图，任务选择突发情况，素材选择对应场景，然后进行局部识别，如果识别不到，请点击一键替换，不需要挪动红框，点击确认即可。",
        ),
        FaqItem(
            question = "在开始战斗的页面停下？",
            answer = "第一种可能：开始战斗识别不到，请在功能测试-战斗流程-开始战斗OCR进行一次局部识别，成功条件是能识别到开始战斗四个字里面至少两个，如果OCR识别出现问题，请点击一键替换，红框挪到按钮上点击确认完成保存素材，之后会走模板匹配。\n\n第二种可能是，特别是大鸟，在切换到有开始战斗的页面时会有明显卡顿，那么工具很有可能在错误的时间截图。依然是在功能测试里面修改，对应的任务-素材分别是战斗流程-开始战斗OCR、待办公务-开始战斗前延时、主线624-开始战斗前延时。",
        ),
        FaqItem(
            question = "吕布的↑不能释放？",
            answer = "可能是↑的起始位置离圈太近了，悬浮窗上有个键位修正，可以改↑的其实位置，改的离圈远一点。",
        ),
        FaqItem(
            question = "跟打的时候，总会漏掉第一个操作？",
            answer = "代码里写的是点击开始战斗6s后开始跟表，修改位置在战斗流程-第一回合第一个操作延时。",
        ),
        FaqItem(
            question = "他的传闻男主剧情出现选项的时候卡住？",
            answer = "首先确定有没有选错模式，如鸢代号鸢的选项花纹不一样，识别不到的，鸟食设置页可以修改模式。如果依然识别不到，请在功能测试他的传闻-他的传闻剧情选项处替换素材，红框记得要挪到任意一个选项花纹上。",
        ),
        FaqItem(
            question = "代号鸢主线没打到幽州篇怎么运行刷624？",
            answer = "模式选择如鸢，然后在功能测试里面，把如鸢624入口那个素材换掉，上传你的游戏内624入口的截图，点一键替换，红框挪到624入口上。",
        ),
        FaqItem(
            question = "代号鸢624点不到幽州篇？",
            answer = "可能是点了故事之后卡了特别长的时间，导致识别的时候还没跳出来那个页面，所以可以在功能测试-素材选代号鸢幽州篇，增加识别延迟。",
        ),
        FaqItem(
            question = "MaaYuan Share作业站打不开？",
            answer = "可以重试，但是部分地区确实打不开maayuan官网，你可以去浏览器试试maayuan.top，可能也进不去，不是本APP的问题。",
        ),
        FaqItem(
            question = "刷624/待办公务在战斗中停止？",
            answer = "首先本工具暂时不能帮你点击自动战斗，必须手动打开，运行的时候有15s/20s的限制，延长战斗时间可以在对应任务-素材选择深色确定按钮（默认延时是5000ms），可以自行增加延时。",
        ),
        FaqItem(
            question = "如何更新版本？更新版本后自己的设置会不会被覆盖？",
            answer = "首页点击检查更新，有两种更新方式。设置不会被覆盖，建议一定要更新到最新版本。",
        ),
        FaqItem(
            question = "如何联系开发者？",
            answer = "小红书搜春笙月；QQ群号：891377554；QQ频道YuanAssist。免费一对一帮忙解决问题。",
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaqScreen(
                items = allFaqItems,
                onBack = ::finish,
            )
        }
    }
}

@Composable
private fun FaqScreen(
    items: List<FaqItem>,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expandedQuestions by rememberSaveable { mutableStateOf(setOf<String>()) }

    val filteredItems = remember(query, expandedQuestions, items) {
        items.filter { item ->
            query.isBlank() ||
                item.question.contains(query, ignoreCase = true) ||
                item.answer.contains(query, ignoreCase = true)
        }.map { item ->
            item.copy(expanded = expandedQuestions.contains(item.question))
        }
    }

    SubpageScaffold(
        title = "常见问题",
        subtitle = "搜索说明 · 展开查看 · 快速定位",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "问题搜索",
            subtitle = "按关键词筛选已有答疑",
        ) {
            SubpageTextField(
                value = query,
                onValueChange = { query = it },
                label = "输入关键词",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (filteredItems.isEmpty()) {
            SubpageEmptyState(
                title = "没有匹配到相关问题",
                subtitle = "可以换个关键词试试，或者到反馈页联系开发者。",
            )
        } else {
            filteredItems.forEach { item ->
                SubpageSectionCard {
                    FaqEntry(
                        item = item,
                        onToggle = {
                            expandedQuestions = if (expandedQuestions.contains(item.question)) {
                                expandedQuestions - item.question
                            } else {
                                expandedQuestions + item.question
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqEntry(
    item: FaqItem,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.question,
                modifier = Modifier.weight(1f),
                color = TitleInk,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = if (item.expanded) "收起" else "展开",
                modifier = Modifier.padding(start = 12.dp),
                color = HighlightGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
            )
        }
        if (item.expanded) {
            Text(
                text = item.answer,
                color = BodyInk.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 21.sp,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}
