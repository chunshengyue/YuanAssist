package com.example.yuanassist.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageActionRow
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageToggleRow
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.DailyGlobalDelayStore
import com.example.yuanassist.utils.TraditionalModeStore

class GlobalSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlobalSettingsScreen(onBack = ::finish)
        }
    }
}

@Composable
private fun GlobalSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    var traditionalModeEnabled by rememberSaveable {
        mutableStateOf(TraditionalModeStore.isEnabled(context))
    }
    var globalDelayInput by rememberSaveable {
        mutableStateOf(
            DailyGlobalDelayStore.getDelayMs(context)
                .takeIf { it > 0L }
                ?.toString()
                .orEmpty(),
        )
    }
    var excludedAgentCount by rememberSaveable {
        mutableStateOf(ConfigManager.getExcludedAgents(context).size)
    }

    SubpageScaffold(
        title = "全局设置",
        subtitle = "日常版通用偏好",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "日常版",
            subtitle = "只影响由日常引擎承接的自动化功能",
        ) {
            SubpageToggleRow(
                title = "繁体模式",
                subtitle = "开启后，日常脚本会优先应用繁体模式节点覆盖",
                checked = traditionalModeEnabled,
                onCheckedChange = { enabled ->
                    traditionalModeEnabled = enabled
                    TraditionalModeStore.setEnabled(context, enabled)
                },
            )
            GlobalDelayField(
                value = globalDelayInput,
                onValueChange = { globalDelayInput = it },
            )
            StoneStyleButton(
                text = "保存全局延时",
                onClick = {
                    val parsedDelay = parseGlobalDelayInput(globalDelayInput)
                    if (parsedDelay == null) {
                        Toast.makeText(context, "请输入有效的全局延时", Toast.LENGTH_SHORT).show()
                    } else {
                        DailyGlobalDelayStore.setDelayMs(context, parsedDelay)
                        globalDelayInput = parsedDelay.takeIf { it > 0L }?.toString().orEmpty()
                        Toast.makeText(context, "全局延时已保存", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        SubpageSectionCard(
            title = "排除密探",
            subtitle = "管理攻略列表和相关筛选中要避开的密探",
        ) {
            SubpageActionRow(
                title = "排除密探选项",
                subtitle = "当前已选 ${excludedAgentCount} 个",
                trailingText = "编辑",
                onClick = {
                    if (activity != null) {
                        ExcludedAgentsDialog.show(activity) { count ->
                            excludedAgentCount = count
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun GlobalDelayField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = "全局延时(ms，留空为 0)",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GlassPanel,
            unfocusedContainerColor = GlassPanel,
            focusedBorderColor = HighlightGold,
            unfocusedBorderColor = GlassStroke.copy(alpha = 0.55f),
            focusedTextColor = TitleInk,
            unfocusedTextColor = TitleInk,
            focusedLabelColor = HighlightGold,
            unfocusedLabelColor = BodyInk,
            cursorColor = HighlightGold,
        ),
    )
}

private fun parseGlobalDelayInput(input: String): Long? {
    val normalized = input.trim()
    if (normalized.isBlank()) return 0L
    return normalized.toLongOrNull()?.takeIf { it >= 0L }
}
