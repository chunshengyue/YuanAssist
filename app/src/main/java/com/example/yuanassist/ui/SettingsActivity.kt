package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.ConfigManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentConfig = ConfigManager.getAllConfig(this)

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        fun addTitle(text: String) {
            content.addView(TextView(this).apply {
                this.text = text
                textSize = 18f
                setTextColor(Color.parseColor("#E5C07B"))
                setPadding(0, 24, 0, 12)
            })
        }

        fun addRow(label: String, value: String, inputType: Int): EditText {
            content.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 4)
            })
            return EditText(this).also { editText ->
                editText.setText(value)
                editText.setTextColor(Color.WHITE)
                editText.setHintTextColor(Color.LTGRAY)
                editText.setBackgroundColor(Color.parseColor("#33000000"))
                editText.setPadding(20, 20, 20, 20)
                editText.inputType = inputType
                content.addView(editText, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                })
            }
        }

        fun addIntRow(label: String, value: String): EditText =
            addRow(label, value, InputType.TYPE_CLASS_NUMBER)

        fun addFloatRow(label: String, value: String): EditText =
            addRow(
                label,
                value,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            )

        content.setBackgroundColor(Color.parseColor("#1A1A1A"))

        content.addView(TextView(this).apply {
            text = "战斗版参数设置"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E5C07B"))
            setPadding(0, 0, 0, 24)
        })

        addTitle("跟打执行参数")
        val etAttack = addIntRow("普攻间隔(ms)", currentConfig.intervalAttack.toString())
        val etSkill = addIntRow("技能间隔(ms)", currentConfig.intervalSkill.toString())
        val etWait = addIntRow("敌方回合(ms)", currentConfig.waitTurn.toString())
        val etStart = addIntRow("起始回合", currentConfig.startTurn.toString())

        content.addView(TextView(this).apply {
            text = "游戏倍速"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 8)
        })
        val rgSpeed = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val rb2x = RadioButton(this).apply {
            id = 2
            text = "2倍速"
            setTextColor(Color.WHITE)
        }
        val rb3x = RadioButton(this).apply {
            id = 3
            text = "3倍速"
            setTextColor(Color.WHITE)
        }
        rgSpeed.addView(rb2x)
        rgSpeed.addView(rb3x)
        rgSpeed.check(if (currentConfig.gameSpeed == 2) 2 else 3)
        content.addView(rgSpeed)

        addTitle("录制参数")
        val etThreshold = addIntRow("滑动阈值", currentConfig.swipeThreshold.toString())
        val etHeight = addIntRow("录制区高度(%)", currentConfig.inputHeightRatio.toString())
        val etRecordDelay = addIntRow("录制延迟(ms)", currentConfig.recordDelay.toString())

        addTitle("战斗动作距离底部")
        val etAttackY = addFloatRow("A 距离底部距离", currentConfig.attackYFromBottom.toString())
        val etUpY = addFloatRow("↑ 距离底部距离", currentConfig.upYFromBottom.toString())
        val etDownY = addFloatRow("↓ 距离底部距离", currentConfig.downYFromBottom.toString())
        val etCircleY = addFloatRow("圈 距离底部距离", currentConfig.circleYFromBottom.toString())

        val btnSave = Button(this).apply {
            text = "保存设置"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#E5C07B"))
            setOnClickListener {
                try {
                    val newConfig = currentConfig.copy(
                        intervalAttack = etAttack.text.toString().toLong(),
                        intervalSkill = etSkill.text.toString().toLong(),
                        waitTurn = etWait.text.toString().toLong(),
                        startTurn = etStart.text.toString().toInt(),
                        swipeThreshold = etThreshold.text.toString().toInt(),
                        inputHeightRatio = etHeight.text.toString().toInt(),
                        recordDelay = etRecordDelay.text.toString().toLong(),
                        gameSpeed = if (rgSpeed.checkedRadioButtonId == 2) 2 else 3,
                        attackYFromBottom = etAttackY.text.toString().toFloat(),
                        upYFromBottom = etUpY.text.toString().toFloat(),
                        downYFromBottom = etDownY.text.toString().toFloat(),
                        circleYFromBottom = etCircleY.text.toString().toFloat()
                    )
                    ConfigManager.saveSettings(this@SettingsActivity, newConfig)
                    startService(Intent(this@SettingsActivity, YuanAssistService::class.java).apply {
                        action = "ACTION_RELOAD_CONFIG"
                    })
                    Toast.makeText(this@SettingsActivity, "设置已保存", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(this@SettingsActivity, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(btnSave, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 24
            bottomMargin = 40
        })

        scrollView.addView(content)
        setContentView(scrollView)
    }
}
