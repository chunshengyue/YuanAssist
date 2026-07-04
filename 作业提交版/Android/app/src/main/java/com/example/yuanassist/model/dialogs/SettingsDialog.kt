package com.example.yuanassist.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.DialogUtils

object SettingsDialog {

    private const val TURN_CHECK_HINT = "此为实验性功能，每回合开始时检测右上角回合数是否对应，如有场地角色，慎开"

    fun show(context: Context, onConfigSaved: () -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val currentConfig = ConfigManager.getAllConfig(context)

        val rootLayout = StyledDialogUi.createDialogCard(themeContext)
        rootLayout.addView(StyledDialogUi.createDialogTitle(themeContext, "参数设置"))
        rootLayout.addView(StyledDialogUi.createDialogSubtitle(themeContext, "调整战斗悬浮窗和录制参数"))

        val scrollView = ScrollView(themeContext)
        val scrollContent = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun createRow(label: String, defaultValue: String, inputType: Int): EditText {
            val row = LinearLayout(themeContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 15, 0, 15)
            }
            val tvLabel = TextView(themeContext).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#6C5B43"))
                width = StyledDialogUi.dpToPx(themeContext, 140f)
            }
            val editText = StyledDialogUi.createStyledInput(themeContext, "").apply {
                setText(defaultValue)
                this.inputType = inputType
                layoutParams = LinearLayout.LayoutParams(0, StyledDialogUi.dpToPx(themeContext, 44f), 1f)
            }
            row.addView(tvLabel)
            row.addView(editText)
            scrollContent.addView(row)
            return editText
        }

        fun createIntRow(label: String, defaultValue: String): EditText =
            createRow(label, defaultValue, InputType.TYPE_CLASS_NUMBER)

        fun createFloatRow(label: String, defaultValue: String): EditText =
            createRow(
                label,
                defaultValue,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            )

        fun createSwitchRow(label: String, checked: Boolean): Switch {
            val row = LinearLayout(themeContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }
            val switchView = Switch(themeContext).apply {
                StyledDialogUi.styleCompactCheckControl(
                    context = themeContext,
                    button = this,
                    label = label,
                    checked = checked,
                )
            }
            row.addView(switchView)
            row.setOnClickListener { switchView.isChecked = !switchView.isChecked }
            scrollContent.addView(row)
            return switchView
        }

        val etAttack = createIntRow("普攻间隔(ms):", currentConfig.intervalAttack.toString())
        val etSkill = createIntRow("技能间隔(ms):", currentConfig.intervalSkill.toString())
        val etWait = createIntRow("敌方回合(ms):", currentConfig.waitTurn.toString())
        val etStart = createIntRow("起始回合:", currentConfig.startTurn.toString())
        val switchTurnNumberCheck = createSwitchRow("回合检测:", currentConfig.enableTurnNumberCheck)
        scrollContent.addView(TextView(themeContext).apply {
            text = TURN_CHECK_HINT
            textSize = 12f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, 0, 0, 12)
        })
        val etThreshold = createIntRow("滑动阈值:", currentConfig.swipeThreshold.toString())
        val etHeight = createIntRow("录制区高度(%):", currentConfig.inputHeightRatio.toString())
        val etRecordDelay = createIntRow("录制延迟(ms):", currentConfig.recordDelay.toString())

        val speedLabel = TextView(themeContext).apply {
            text = "游戏倍速"
            textSize = 14f
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(0, 20, 0, 10)
        }
        scrollContent.addView(speedLabel)

        val rgSpeed = RadioGroup(themeContext).apply { orientation = LinearLayout.HORIZONTAL }
        val rb2x = RadioButton(themeContext).apply {
            id = 2
            StyledDialogUi.styleCompactCheckControl(themeContext, this, "2倍速", currentConfig.gameSpeed == 2)
        }
        val rb3x = RadioButton(themeContext).apply {
            id = 3
            StyledDialogUi.styleCompactCheckControl(themeContext, this, "3倍速", currentConfig.gameSpeed != 2)
        }
        rgSpeed.addView(rb2x)
        rgSpeed.addView(rb3x)
        if (currentConfig.gameSpeed == 2) rgSpeed.check(2) else rgSpeed.check(3)
        scrollContent.addView(rgSpeed)

        val actionConfigLabel = TextView(themeContext).apply {
            text = "战斗动作距离底部"
            textSize = 14f
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(0, 20, 0, 10)
        }
        scrollContent.addView(actionConfigLabel)

        val etAttackY = createFloatRow("A 距离底部距离:", currentConfig.attackYFromBottom.toString())
        val etUpY = createFloatRow("↑ 距离底部距离:", currentConfig.upYFromBottom.toString())
        val etDownY = createFloatRow("↓ 距离底部距离:", currentConfig.downYFromBottom.toString())
        val etCircleY = createFloatRow("圈 距离底部距离:", currentConfig.circleYFromBottom.toString())

        scrollContent.addView(TextView(themeContext).apply {
            text = "高级参数"
            textSize = 14f
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(0, 20, 0, 10)
        })
        scrollContent.addView(TextView(themeContext).apply {
            text = "录制模式"
            textSize = 13f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, 8, 0, 6)
        })
        val etRecordClickDuration = createIntRow("点击持续时间(ms):", currentConfig.recordClickDurationMs.toString())
        val etRecordSwipeDuration = createIntRow("滑动持续时间(ms):", currentConfig.recordSwipeDurationMs.toString())
        val etRecordSwipeDistance = createFloatRow("滑动距离:", currentConfig.recordSwipeDistance.toString())
        scrollContent.addView(TextView(themeContext).apply {
            text = "跟打模式"
            textSize = 13f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, 16, 0, 6)
        })
        val etFollowClickDuration = createIntRow("点击持续时间(ms):", currentConfig.followClickDurationMs.toString())
        val etFollowSwipeDuration = createIntRow("滑动持续时间(ms):", currentConfig.followSwipeDurationMs.toString())
        val etFollowSwipeDistance = createFloatRow("滑动距离:", currentConfig.followSwipeDistance.toString())

        scrollView.addView(scrollContent)
        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = StyledDialogUi.dpToPx(themeContext, 10f)
            }
        )

        val buttonRow = StyledDialogUi.createActionRow(themeContext)
        val btnCancel = StyledDialogUi.createActionButton(themeContext, "取消", false)
        val btnSave = StyledDialogUi.createActionButton(themeContext, "保存", true)
        buttonRow.addView(btnCancel, StyledDialogUi.createWeightedButtonParams(themeContext, false))
        buttonRow.addView(btnSave, StyledDialogUi.createWeightedButtonParams(themeContext, true))
        rootLayout.addView(buttonRow)

        val dialog = StyledDialogUi.showStyledDialog(themeContext, rootLayout)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            try {
                val attack = etAttack.text.toString().toLong()
                val skill = etSkill.text.toString().toLong()
                val wait = etWait.text.toString().toLong()
                val start = etStart.text.toString().toInt()
                val threshold = etThreshold.text.toString().toInt()
                val height = etHeight.text.toString().toInt()
                val delay = etRecordDelay.text.toString().toLong()
                val speed = if (rgSpeed.checkedRadioButtonId == 2) 2 else 3

                val attackY = etAttackY.text.toString().toFloat()
                val upY = etUpY.text.toString().toFloat()
                val downY = etDownY.text.toString().toFloat()
                val circleY = etCircleY.text.toString().toFloat()
                val recordClickDuration = etRecordClickDuration.text.toString().toLong()
                val recordSwipeDuration = etRecordSwipeDuration.text.toString().toLong()
                val recordSwipeDistance = etRecordSwipeDistance.text.toString().toFloat()
                val followClickDuration = etFollowClickDuration.text.toString().toLong()
                val followSwipeDuration = etFollowSwipeDuration.text.toString().toLong()
                val followSwipeDistance = etFollowSwipeDistance.text.toString().toFloat()

                if (
                    attack > 0 &&
                    skill > 0 &&
                    wait > 0 &&
                    start > 0 &&
                    threshold > 0 &&
                    height in 10..100 &&
                    recordClickDuration > 0 &&
                    recordSwipeDuration > 0 &&
                    recordSwipeDistance > 0f &&
                    followClickDuration > 0 &&
                    followSwipeDuration > 0 &&
                    followSwipeDistance > 0f
                ) {
                    val newConfig = currentConfig.copy(
                        intervalAttack = attack,
                        intervalSkill = skill,
                        waitTurn = wait,
                        startTurn = start,
                        enableTurnNumberCheck = switchTurnNumberCheck.isChecked,
                        swipeThreshold = threshold,
                        inputHeightRatio = height,
                        recordDelay = delay,
                        gameSpeed = speed,
                        attackYFromBottom = attackY,
                        upYFromBottom = upY,
                        downYFromBottom = downY,
                        circleYFromBottom = circleY,
                        recordClickDurationMs = recordClickDuration,
                        recordSwipeDurationMs = recordSwipeDuration,
                        recordSwipeDistance = recordSwipeDistance,
                        followClickDurationMs = followClickDuration,
                        followSwipeDurationMs = followSwipeDuration,
                        followSwipeDistance = followSwipeDistance
                    )
                    ConfigManager.saveSettings(context, newConfig)
                    onConfigSaved()
                    Toast.makeText(context, "设置已保存并生效", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "参数数值不合理，请检查", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
