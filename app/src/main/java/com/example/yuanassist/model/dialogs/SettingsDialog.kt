package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.DialogUtils

object SettingsDialog {

    fun show(context: Context, onConfigSaved: () -> Unit) {
        val themeContext = DialogUtils.getThemeContext(context)
        val currentConfig = ConfigManager.getAllConfig(context)

        val layout = LinearLayout(themeContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

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
                setTextColor(Color.DKGRAY)
                width = (140 * context.resources.displayMetrics.density).toInt()
            }
            val editText = EditText(themeContext).apply {
                setText(defaultValue)
                this.inputType = inputType
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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

        val etAttack = createIntRow("普攻间隔(ms):", currentConfig.intervalAttack.toString())
        val etSkill = createIntRow("技能间隔(ms):", currentConfig.intervalSkill.toString())
        val etWait = createIntRow("敌方回合(ms):", currentConfig.waitTurn.toString())
        val etStart = createIntRow("起始回合:", currentConfig.startTurn.toString())
        val etThreshold = createIntRow("滑动阈值:", currentConfig.swipeThreshold.toString())
        val etHeight = createIntRow("录制区高度(%):", currentConfig.inputHeightRatio.toString())
        val etRecordDelay = createIntRow("录制延迟(ms):", currentConfig.recordDelay.toString())

        val speedLabel = TextView(themeContext).apply {
            text = "游戏倍速"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 10)
        }
        scrollContent.addView(speedLabel)

        val rgSpeed = RadioGroup(themeContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val rb2x = RadioButton(themeContext).apply {
            text = "2倍速"
            id = 2
        }
        val rb3x = RadioButton(themeContext).apply {
            text = "3倍速"
            id = 3
        }
        rgSpeed.addView(rb2x)
        rgSpeed.addView(rb3x)
        if (currentConfig.gameSpeed == 2) rgSpeed.check(2) else rgSpeed.check(3)
        scrollContent.addView(rgSpeed)

        val actionConfigLabel = TextView(themeContext).apply {
            text = "战斗动作距离底部"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 10)
        }
        scrollContent.addView(actionConfigLabel)

        val etAttackY = createFloatRow("A 距离底部距离:", currentConfig.attackYFromBottom.toString())
        val etUpY = createFloatRow("↑ 距离底部距离:", currentConfig.upYFromBottom.toString())
        val etDownY = createFloatRow("↓ 距离底部距离:", currentConfig.downYFromBottom.toString())
        val etCircleY = createFloatRow("圈 距离底部距离:", currentConfig.circleYFromBottom.toString())

        scrollView.addView(scrollContent)
        layout.addView(scrollView)

        val builder = AlertDialog.Builder(themeContext)
            .setTitle("参数设置")
            .setView(layout)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)

        val dialog = DialogUtils.safeShowOverlayDialog(builder)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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

                if (attack > 0 && skill > 0 && wait > 0 && start > 0 && threshold > 0 && height in 10..100) {
                    val newConfig = currentConfig.copy(
                        intervalAttack = attack,
                        intervalSkill = skill,
                        waitTurn = wait,
                        startTurn = start,
                        swipeThreshold = threshold,
                        inputHeightRatio = height,
                        recordDelay = delay,
                        gameSpeed = speed,
                        attackYFromBottom = attackY,
                        upYFromBottom = upY,
                        downYFromBottom = downY,
                        circleYFromBottom = circleY
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
