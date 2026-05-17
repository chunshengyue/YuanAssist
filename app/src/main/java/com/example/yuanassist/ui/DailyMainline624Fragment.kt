package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.DailyMainline624Bridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.model.Mainline624GameVariant
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageRadioOption

private const val PREFS_APP = "app_prefs"
private const val ACTION_START_MAINLINE_624 = "ACTION_START_MAINLINE_624"
private const val KEY_STOP_MODE = "daily_mainline_624_stop_mode"
private const val KEY_RUN_COUNT = "daily_mainline_624_run_count"
private const val KEY_LOW_SPEC_DELAY = "daily_mainline_624_low_spec_delay"
private const val KEY_GAME_VARIANT = "daily_mainline_624_game_variant"
private const val MODE_RESOURCE = "resource"
private const val MODE_RUN_COUNT = "run_count"

class DailyMainline624Fragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_STOP_MODE, MODE_RESOURCE).orEmpty()
        val savedRunCount = prefs.getString(KEY_RUN_COUNT, "").orEmpty()
        val savedLowSpecDelay = prefs.getString(KEY_LOW_SPEC_DELAY, "").orEmpty()
        val savedGameVariant = prefs.getString(KEY_GAME_VARIANT, Mainline624GameVariant.RU_YUAN.name)
            ?.let { value ->
                Mainline624GameVariant.entries.firstOrNull { it.name == value }
            }
            ?: Mainline624GameVariant.RU_YUAN

        return ComposeView(context).apply {
            setContent {
                DailyMainline624Screen(
                    initialConfig = DailyMainline624UiState(
                        gameVariant = savedGameVariant,
                        stopMode = savedMode,
                        runCount = savedRunCount,
                        lowSpecDelay = savedLowSpecDelay,
                    ),
                    onBack = {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            activity?.finish()
                        }
                    },
                    onConfirm = { state ->
                        val config = buildConfig(state) ?: return@DailyMainline624Screen
                        saveSettings(state, config)
                        DailyMainline624Bridge.pendingConfig = config
                        startMainline624Service()
                    },
                )
            }
        }
    }

    private fun buildConfig(state: DailyMainline624UiState): Mainline624Config? {
        val maxRuns = state.runCount.trim().toIntOrNull()
        val lowSpecDelayMs = state.lowSpecDelay.trim().toLongOrNull() ?: 0L

        if (state.stopMode != MODE_RUN_COUNT && state.stopMode != MODE_RESOURCE) {
            Toast.makeText(requireContext(), "请选择运行方式", Toast.LENGTH_SHORT).show()
            return null
        }

        if (state.stopMode == MODE_RUN_COUNT && (maxRuns == null || maxRuns <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的运行次数", Toast.LENGTH_SHORT).show()
            return null
        }

        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }

        return Mainline624Config(
            maxRuns = if (state.stopMode == MODE_RUN_COUNT) maxRuns else null,
            lowSpecDelayMs = lowSpecDelayMs,
            gameVariant = state.gameVariant,
        )
    }

    private fun saveSettings(state: DailyMainline624UiState, config: Mainline624Config) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString(KEY_STOP_MODE, if (config.maxRuns != null) MODE_RUN_COUNT else MODE_RESOURCE)
            .putString(KEY_RUN_COUNT, state.runCount.trim())
            .putString(KEY_LOW_SPEC_DELAY, state.lowSpecDelay.trim())
            .putString(KEY_GAME_VARIANT, config.gameVariant.name)
            .apply()
    }

    private fun startMainline624Service() {
        val context = requireContext()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_MAINLINE_624)
            .apply()

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(context, "请启用 YuanAssist 无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            val intent = Intent(context, YuanAssistService::class.java).apply {
                action = ACTION_START_MAINLINE_624
            }
            context.startService(intent)
            Toast.makeText(context, "6-24 已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(requireContext(), YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) return true
        }
        return false
    }
}

private data class DailyMainline624UiState(
    val gameVariant: Mainline624GameVariant,
    val stopMode: String,
    val runCount: String,
    val lowSpecDelay: String,
)

@Composable
private fun DailyMainline624Screen(
    initialConfig: DailyMainline624UiState,
    onBack: () -> Unit,
    onConfirm: (DailyMainline624UiState) -> Unit,
) {
    var gameVariant by rememberSaveable { mutableStateOf(initialConfig.gameVariant) }
    var stopMode by rememberSaveable { mutableStateOf(initialConfig.stopMode) }
    var runCount by rememberSaveable { mutableStateOf(initialConfig.runCount) }
    var lowSpecDelay by rememberSaveable { mutableStateOf(initialConfig.lowSpecDelay) }

    val state = DailyMainline624UiState(
        gameVariant = gameVariant,
        stopMode = stopMode,
        runCount = runCount,
        lowSpecDelay = lowSpecDelay,
    )

    SubpageScaffold(
        title = "刷6-24",
        subtitle = "版本选择 · 运行方式 · 延时调整",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "游戏版本",
            subtitle = "决定 6-24 入口识别素材",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubpageRadioOption(
                    text = "如鸢",
                    selected = gameVariant == Mainline624GameVariant.RU_YUAN,
                    onClick = { gameVariant = Mainline624GameVariant.RU_YUAN },
                )
                SubpageRadioOption(
                    text = "代号鸢",
                    selected = gameVariant == Mainline624GameVariant.CODE_NAME_YUAN,
                    onClick = { gameVariant = Mainline624GameVariant.CODE_NAME_YUAN },
                )
            }
        }

        SubpageSectionCard(
            title = "运行方式",
            subtitle = "保留原页面两种运行方式",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubpageRadioOption(
                    text = "刷到体力耗尽",
                    selected = stopMode == MODE_RESOURCE,
                    onClick = { stopMode = MODE_RESOURCE },
                )
                SubpageRadioOption(
                    text = "指定运行次数",
                    selected = stopMode == MODE_RUN_COUNT,
                    onClick = { stopMode = MODE_RUN_COUNT },
                )
            }
            if (stopMode == MODE_RUN_COUNT) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SubpageTextField(
                        value = runCount,
                        onValueChange = { runCount = it },
                        label = "请输入次数",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "次",
                        color = BodyInk,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
        }

        SubpageSectionCard(
            title = "低配机型适应",
            subtitle = "会在所有任务原始 delay 基础上统一增加该数值，单位 ms。",
        ) {
            SubpageTextField(
                value = lowSpecDelay,
                onValueChange = { lowSpecDelay = it },
                label = "0 表示关闭",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SubpageSectionCard(
            title = "成就提示",
            subtitle = "可以携带羁绊密探刷成就，下面这些组合可供参考",
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "颜良 / 文丑",
                    "飞云 / 绣球",
                    "李真 / 李脱",
                    "陆逊 / 吕蒙",
                    "阿蝉 / 张辽",
                    "华佗 / 张仲景",
                    "刘豹 / 蔡琰",
                    "诸葛瑾 / 诸葛诞",
                    "祢衡 / 徐庶",
                    "周瑜 / 小乔",
                ).chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item ->
                            StoneHintCell(
                                text = item,
                                modifier = Modifier
                                    .weight(1f),
                            )
                        }
                        repeat(3 - row.size) {
                            SpacerCell(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        StoneActionButton(
            text = "导入",
            onClick = { onConfirm(state) },
        )
    }
}

@Composable
private fun StoneHintCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .background(
                color = Color(0xFFFFFDF8),
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = GlassStroke.copy(alpha = 0.36f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TitleInk,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun SpacerCell(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(58.dp))
}

@Composable
private fun StoneActionButton(
    text: String,
    onClick: () -> Unit,
) {
    StoneStyleButton(
        text = text,
        onClick = onClick,
    )
}
