package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageFieldGroup
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageToggleRow
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.utils.ConfigManager

private const val TURN_CHECK_HINT = "此为实验性功能，每回合开始时检测右上角回合数是否对应，如有场地角色，慎开"

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentConfig = ConfigManager.getAllConfig(this)
        setContent {
            SettingsScreen(
                initialState = SettingsFormState(
                    attackInterval = currentConfig.intervalAttack.toString(),
                    skillInterval = currentConfig.intervalSkill.toString(),
                    waitTurn = currentConfig.waitTurn.toString(),
                    startTurn = currentConfig.startTurn.toString(),
                    turnNumberCheckEnabled = currentConfig.enableTurnNumberCheck,
                    gameSpeed = currentConfig.gameSpeed.toString(),
                    swipeThreshold = currentConfig.swipeThreshold.toString(),
                    inputHeightRatio = currentConfig.inputHeightRatio.toString(),
                    recordDelay = currentConfig.recordDelay.toString(),
                    attackYFromBottom = currentConfig.attackYFromBottom.toString(),
                    upYFromBottom = currentConfig.upYFromBottom.toString(),
                    downYFromBottom = currentConfig.downYFromBottom.toString(),
                    circleYFromBottom = currentConfig.circleYFromBottom.toString(),
                    recordClickDurationMs = currentConfig.recordClickDurationMs.toString(),
                    recordSwipeDurationMs = currentConfig.recordSwipeDurationMs.toString(),
                    recordSwipeDistance = currentConfig.recordSwipeDistance.toString(),
                    followClickDurationMs = currentConfig.followClickDurationMs.toString(),
                    followSwipeDurationMs = currentConfig.followSwipeDurationMs.toString(),
                    followSwipeDistance = currentConfig.followSwipeDistance.toString(),
                ),
                onBack = ::finish,
                onSave = { state ->
                    try {
                        val newConfig = currentConfig.copy(
                            intervalAttack = state.attackInterval.toLong(),
                            intervalSkill = state.skillInterval.toLong(),
                            waitTurn = state.waitTurn.toLong(),
                            startTurn = state.startTurn.toInt(),
                            enableTurnNumberCheck = state.turnNumberCheckEnabled,
                            swipeThreshold = state.swipeThreshold.toInt(),
                            inputHeightRatio = state.inputHeightRatio.toInt(),
                            recordDelay = state.recordDelay.toLong(),
                            gameSpeed = state.gameSpeed.toInt(),
                            attackYFromBottom = state.attackYFromBottom.toFloat(),
                            upYFromBottom = state.upYFromBottom.toFloat(),
                            downYFromBottom = state.downYFromBottom.toFloat(),
                            circleYFromBottom = state.circleYFromBottom.toFloat(),
                            recordClickDurationMs = state.recordClickDurationMs.toLong(),
                            recordSwipeDurationMs = state.recordSwipeDurationMs.toLong(),
                            recordSwipeDistance = state.recordSwipeDistance.toFloat(),
                            followClickDurationMs = state.followClickDurationMs.toLong(),
                            followSwipeDurationMs = state.followSwipeDurationMs.toLong(),
                            followSwipeDistance = state.followSwipeDistance.toFloat(),
                        )
                        require(newConfig.intervalAttack > 0)
                        require(newConfig.intervalSkill > 0)
                        require(newConfig.waitTurn > 0)
                        require(newConfig.startTurn > 0)
                        require(newConfig.swipeThreshold > 0)
                        require(newConfig.inputHeightRatio in 10..100)
                        require(newConfig.recordDelay >= 0)
                        require(newConfig.recordClickDurationMs > 0)
                        require(newConfig.recordSwipeDurationMs > 0)
                        require(newConfig.recordSwipeDistance > 0f)
                        require(newConfig.followClickDurationMs > 0)
                        require(newConfig.followSwipeDurationMs > 0)
                        require(newConfig.followSwipeDistance > 0f)
                        ConfigManager.saveSettings(this@SettingsActivity, newConfig)
                        startService(Intent(this@SettingsActivity, YuanAssistService::class.java).apply {
                            action = "ACTION_RELOAD_CONFIG"
                        })
                        Toast.makeText(this@SettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (_: Exception) {
                        Toast.makeText(this@SettingsActivity, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

private data class SettingsFormState(
    val attackInterval: String,
    val skillInterval: String,
    val waitTurn: String,
    val startTurn: String,
    val turnNumberCheckEnabled: Boolean,
    val gameSpeed: String,
    val swipeThreshold: String,
    val inputHeightRatio: String,
    val recordDelay: String,
    val attackYFromBottom: String,
    val upYFromBottom: String,
    val downYFromBottom: String,
    val circleYFromBottom: String,
    val recordClickDurationMs: String,
    val recordSwipeDurationMs: String,
    val recordSwipeDistance: String,
    val followClickDurationMs: String,
    val followSwipeDurationMs: String,
    val followSwipeDistance: String,
)

@Composable
private fun SettingsScreen(
    initialState: SettingsFormState,
    onBack: () -> Unit,
    onSave: (SettingsFormState) -> Unit,
) {
    var attackInterval by rememberSaveable { mutableStateOf(initialState.attackInterval) }
    var skillInterval by rememberSaveable { mutableStateOf(initialState.skillInterval) }
    var waitTurn by rememberSaveable { mutableStateOf(initialState.waitTurn) }
    var startTurn by rememberSaveable { mutableStateOf(initialState.startTurn) }
    var turnNumberCheckEnabled by rememberSaveable { mutableStateOf(initialState.turnNumberCheckEnabled) }
    var gameSpeed by rememberSaveable { mutableStateOf(initialState.gameSpeed) }
    var swipeThreshold by rememberSaveable { mutableStateOf(initialState.swipeThreshold) }
    var inputHeightRatio by rememberSaveable { mutableStateOf(initialState.inputHeightRatio) }
    var recordDelay by rememberSaveable { mutableStateOf(initialState.recordDelay) }
    var attackYFromBottom by rememberSaveable { mutableStateOf(initialState.attackYFromBottom) }
    var upYFromBottom by rememberSaveable { mutableStateOf(initialState.upYFromBottom) }
    var downYFromBottom by rememberSaveable { mutableStateOf(initialState.downYFromBottom) }
    var circleYFromBottom by rememberSaveable { mutableStateOf(initialState.circleYFromBottom) }
    var recordClickDurationMs by rememberSaveable { mutableStateOf(initialState.recordClickDurationMs) }
    var recordSwipeDurationMs by rememberSaveable { mutableStateOf(initialState.recordSwipeDurationMs) }
    var recordSwipeDistance by rememberSaveable { mutableStateOf(initialState.recordSwipeDistance) }
    var followClickDurationMs by rememberSaveable { mutableStateOf(initialState.followClickDurationMs) }
    var followSwipeDurationMs by rememberSaveable { mutableStateOf(initialState.followSwipeDurationMs) }
    var followSwipeDistance by rememberSaveable { mutableStateOf(initialState.followSwipeDistance) }

    val state = SettingsFormState(
        attackInterval = attackInterval,
        skillInterval = skillInterval,
        waitTurn = waitTurn,
        startTurn = startTurn,
        turnNumberCheckEnabled = turnNumberCheckEnabled,
        gameSpeed = gameSpeed,
        swipeThreshold = swipeThreshold,
        inputHeightRatio = inputHeightRatio,
        recordDelay = recordDelay,
        attackYFromBottom = attackYFromBottom,
        upYFromBottom = upYFromBottom,
        downYFromBottom = downYFromBottom,
        circleYFromBottom = circleYFromBottom,
        recordClickDurationMs = recordClickDurationMs,
        recordSwipeDurationMs = recordSwipeDurationMs,
        recordSwipeDistance = recordSwipeDistance,
        followClickDurationMs = followClickDurationMs,
        followSwipeDurationMs = followSwipeDurationMs,
        followSwipeDistance = followSwipeDistance,
    )

    SubpageScaffold(
        title = "战斗版参数设置",
        subtitle = "执行节奏 · 录制参数 · 操作坐标",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "跟打执行参数",
            subtitle = "保留原有保存语义，只优化信息排布",
        ) {
            SettingsNumberField("普攻间隔(ms)", attackInterval) { attackInterval = it }
            SettingsNumberField("技能间隔(ms)", skillInterval) { skillInterval = it }
            SettingsNumberField("敌方回合(ms)", waitTurn) { waitTurn = it }
            SettingsNumberField("起始回合", startTurn) { startTurn = it }
            SubpageToggleRow(
                title = "回合检测",
                subtitle = TURN_CHECK_HINT,
                checked = turnNumberCheckEnabled,
                onCheckedChange = { turnNumberCheckEnabled = it },
            )
            SubpageFieldGroup(
                title = "游戏倍速",
                subtitle = "沿用原有 2 倍 / 3 倍逻辑",
            ) {
                SpeedRadioRow(
                    selectedSpeed = gameSpeed,
                    onSelect = { selected -> gameSpeed = selected },
                )
            }
        }

        SubpageSectionCard(
            title = "录制参数",
            subtitle = "用于录制与输入区域控制",
        ) {
            SettingsNumberField("滑动阈值", swipeThreshold) { swipeThreshold = it }
            SettingsNumberField("录制区高度(%)", inputHeightRatio) { inputHeightRatio = it }
            SettingsNumberField("录制延迟(ms)", recordDelay) { recordDelay = it }
        }

        SubpageSectionCard(
            title = "战斗动作距离底部",
            subtitle = "沿用现有距离计算逻辑",
        ) {
            SettingsDecimalField("A 距离底部距离", attackYFromBottom) { attackYFromBottom = it }
            SettingsDecimalField("↑ 距离底部距离", upYFromBottom) { upYFromBottom = it }
            SettingsDecimalField("↓ 距离底部距离", downYFromBottom) { downYFromBottom = it }
            SettingsDecimalField("圈 距离底部距离", circleYFromBottom) { circleYFromBottom = it }
        }

        SubpageSectionCard(
            title = "高级参数",
            subtitle = "分别控制录制模拟和跟打执行的动作手势",
        ) {
            SubpageFieldGroup(
                title = "录制模式",
                subtitle = "用于录制时穿透模拟动作",
            ) {
                SettingsNumberField("点击持续时间(ms)", recordClickDurationMs) { recordClickDurationMs = it }
                SettingsNumberField("滑动持续时间(ms)", recordSwipeDurationMs) { recordSwipeDurationMs = it }
                SettingsDecimalField("滑动距离", recordSwipeDistance) { recordSwipeDistance = it }
            }
            SubpageFieldGroup(
                title = "跟打模式",
                subtitle = "用于跟打时执行 A / ↑ / ↓",
            ) {
                SettingsNumberField("点击持续时间(ms)", followClickDurationMs) { followClickDurationMs = it }
                SettingsNumberField("滑动持续时间(ms)", followSwipeDurationMs) { followSwipeDurationMs = it }
                SettingsDecimalField("滑动距离", followSwipeDistance) { followSwipeDistance = it }
            }
        }

        SaveSettingsButton(
            onClick = { onSave(state) },
        )
    }
}

@Composable
private fun SpeedRadioRow(
    selectedSpeed: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubpageRadioOption(
            text = "2 倍速",
            selected = selectedSpeed == "2",
            onClick = { onSelect("2") },
            modifier = Modifier.weight(1f),
        )
        SubpageRadioOption(
            text = "3 倍速",
            selected = selectedSpeed != "2",
            onClick = { onSelect("3") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SettingsTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    )
}

@Composable
private fun SettingsDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SettingsTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = TitleInk,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = androidx.compose.ui.text.TextStyle(color = TitleInk),
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
            supportingText = {
                Text(
                    text = "保持原参数语义",
                    color = BodyInk.copy(alpha = 0.74f),
                )
            },
        )
    }
}

@Composable
private fun SaveSettingsButton(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StoneStyleButton(
                text = "保存设置",
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            )
        }
    }
}
