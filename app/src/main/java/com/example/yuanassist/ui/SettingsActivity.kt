package com.example.yuanassist.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.R
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.ConfigManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 1. 获取所有控件 (删除了坐标相关的 EditText)
        val etAttack = findViewById<EditText>(R.id.et_interval_attack)
        val etSkill = findViewById<EditText>(R.id.et_interval_skill)
        val etWait = findViewById<EditText>(R.id.et_wait_turn)
        val etStart = findViewById<EditText>(R.id.et_start_turn)
        val etThreshold = findViewById<EditText>(R.id.et_swipe_threshold)
        val etHeight = findViewById<EditText>(R.id.et_input_height)
        val etRecordDelay = findViewById<EditText>(R.id.et_record_delay)
        val rgGameSpeed = findViewById<android.widget.RadioGroup>(R.id.rg_game_speed)
        val btnSave = findViewById<Button>(R.id.btn_save_settings)

        // 2. 读取当前配置并回显到界面
        val currentConfig = ConfigManager.getAllConfig(this)

        etAttack.setText(currentConfig.intervalAttack.toString())
        etSkill.setText(currentConfig.intervalSkill.toString())
        etWait.setText(currentConfig.waitTurn.toString())
        etStart.setText(currentConfig.startTurn.toString())
        etThreshold.setText(currentConfig.swipeThreshold.toString())
        etHeight.setText(currentConfig.inputHeightRatio.toString())
        etRecordDelay.setText(currentConfig.recordDelay.toString())
        if (currentConfig.gameSpeed == 2) {
            rgGameSpeed.check(R.id.rb_speed_2x)
        } else {
            rgGameSpeed.check(R.id.rb_speed_3x) // 默认 3 倍速
        }
        // 3. 保存逻辑
        btnSave.setOnClickListener {
            try {
                // 获取基本参数
                val attack = etAttack.text.toString().toLong()
                val skill = etSkill.text.toString().toLong()
                val wait = etWait.text.toString().toLong()
                val start = etStart.text.toString().toInt()
                val threshold = etThreshold.text.toString().toInt()
                val height = etHeight.text.toString().toInt()
                val delay = etRecordDelay.text.toString().toLong()
                val selectedSpeed = if (rgGameSpeed.checkedRadioButtonId == R.id.rb_speed_2x) 2 else 3
                // 简单的校验
                if (attack > 0 && skill > 0 && wait > 0 && start > 0 && threshold > 0 && height in 10..100) {

                    // 🟢 构建新的 AppConfig (不再包含坐标)
                    val newConfig = AppConfig(
                        intervalAttack = attack,
                        intervalSkill = skill,
                        waitTurn = wait,
                        startTurn = start,
                        swipeThreshold = threshold,
                        inputHeightRatio = height,
                        recordDelay = delay,
                        gameSpeed = selectedSpeed
                    )

                    // 调用 ConfigManager 保存
                    ConfigManager.saveSettings(this, newConfig)

                    Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                    finish() // 关闭页面
                } else {
                    Toast.makeText(this, "参数数值不合理，请检查", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
            }
        }
    }
}