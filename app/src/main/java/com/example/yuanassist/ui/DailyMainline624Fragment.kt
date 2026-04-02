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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.example.yuanassist.R
import com.example.yuanassist.core.DailyMainline624Bridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.model.Mainline624GameVariant

class DailyMainline624Fragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_daily_mainline_624, container, false)
        bindViews(view)
        return view
    }

    private fun bindViews(view: View) {
        bindHeaderInsets(view)
        bindStopConditionInputs(view)
        restoreSavedSettings(view)

        view.findViewById<ImageView>(R.id.btn_daily_mainline_624_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btn_daily_mainline_624_confirm).setOnClickListener {
            val config = buildConfig(view) ?: return@setOnClickListener
            saveSettings(view, config)
            DailyMainline624Bridge.pendingConfig = config
            startMainline624Service()
        }
    }

    private fun bindHeaderInsets(view: View) {
        val header = view.findViewById<View>(R.id.layout_daily_mainline_624_header)
        val topSpace = view.findViewById<View>(R.id.view_daily_mainline_624_status_space)
        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topSpace.updateLayoutParams {
                height = statusBarTop / 2
            }
            insets
        }
        ViewCompat.requestApplyInsets(header)
    }

    private fun bindStopConditionInputs(view: View) {
        val stopGroup = view.findViewById<RadioGroup>(R.id.rg_daily_mainline_624_stop_condition)
        val runCount = view.findViewById<RadioButton>(R.id.rb_daily_mainline_624_stop_run_count)
        val runCountLayout = view.findViewById<View>(R.id.layout_daily_mainline_624_run_count)

        fun refreshInputs() {
            runCountLayout.visibility = if (runCount.isChecked) View.VISIBLE else View.GONE
        }

        stopGroup.setOnCheckedChangeListener { _, _ ->
            refreshInputs()
        }
        refreshInputs()
    }

    private fun buildConfig(view: View): Mainline624Config? {
        val runCountOption = view.findViewById<RadioButton>(R.id.rb_daily_mainline_624_stop_run_count)
        val resourceOption = view.findViewById<RadioButton>(R.id.rb_daily_mainline_624_stop_resource)
        val ruyuanOption = view.findViewById<RadioButton>(R.id.rb_daily_mainline_624_variant_ruyuan)
        val codeNameOption = view.findViewById<RadioButton>(R.id.rb_daily_mainline_624_variant_codename)
        val maxRuns = view.findViewById<EditText>(R.id.et_daily_mainline_624_run_count)
            .text.toString().trim().toIntOrNull()
        val lowSpecDelayMs = view.findViewById<EditText>(R.id.et_daily_mainline_624_low_spec_delay)
            .text.toString().trim().toLongOrNull() ?: 0L
        val gameVariant = when {
            ruyuanOption.isChecked -> Mainline624GameVariant.RU_YUAN
            codeNameOption.isChecked -> Mainline624GameVariant.CODE_NAME_YUAN
            else -> null
        }

        if (!runCountOption.isChecked && !resourceOption.isChecked) {
            Toast.makeText(requireContext(), "请选择运行方式", Toast.LENGTH_SHORT).show()
            return null
        }

        if (gameVariant == null) {
            Toast.makeText(requireContext(), "请选择游戏版本", Toast.LENGTH_SHORT).show()
            return null
        }

        if (runCountOption.isChecked && (maxRuns == null || maxRuns <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的运行次数", Toast.LENGTH_SHORT).show()
            return null
        }

        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }

        return Mainline624Config(
            maxRuns = if (runCountOption.isChecked) maxRuns else null,
            lowSpecDelayMs = lowSpecDelayMs,
            gameVariant = gameVariant
        )
    }

    private fun restoreSavedSettings(view: View) {
        val prefs = requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_STOP_MODE, MODE_RESOURCE)
        val savedRunCount = prefs.getString(KEY_RUN_COUNT, "").orEmpty()
        val savedLowSpecDelay = prefs.getString(KEY_LOW_SPEC_DELAY, "").orEmpty()
        val savedGameVariant = prefs.getString(KEY_GAME_VARIANT, Mainline624GameVariant.RU_YUAN.name)

        view.findViewById<RadioButton>(
            if (savedMode == MODE_RUN_COUNT) {
                R.id.rb_daily_mainline_624_stop_run_count
            } else {
                R.id.rb_daily_mainline_624_stop_resource
            }
        ).isChecked = true

        view.findViewById<RadioButton>(
            if (savedGameVariant == Mainline624GameVariant.CODE_NAME_YUAN.name) {
                R.id.rb_daily_mainline_624_variant_codename
            } else {
                R.id.rb_daily_mainline_624_variant_ruyuan
            }
        ).isChecked = true

        view.findViewById<EditText>(R.id.et_daily_mainline_624_run_count).setText(savedRunCount)
        view.findViewById<EditText>(R.id.et_daily_mainline_624_low_spec_delay).setText(savedLowSpecDelay)
    }

    private fun saveSettings(view: View, config: Mainline624Config) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString(KEY_STOP_MODE, if (config.maxRuns != null) MODE_RUN_COUNT else MODE_RESOURCE)
            .putString(
                KEY_RUN_COUNT,
                view.findViewById<EditText>(R.id.et_daily_mainline_624_run_count).text.toString().trim()
            )
            .putString(
                KEY_LOW_SPEC_DELAY,
                view.findViewById<EditText>(R.id.et_daily_mainline_624_low_spec_delay).text.toString().trim()
            )
            .putString(KEY_GAME_VARIANT, config.gameVariant.name)
            .apply()
    }

    private fun startMainline624Service() {
        val context = requireContext()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_MAINLINE_624)
            .apply()

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
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
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) return true
        }
        return false
    }

    companion object {
        private const val PREFS_APP = "app_prefs"
        private const val ACTION_START_MAINLINE_624 = "ACTION_START_MAINLINE_624"
        private const val KEY_STOP_MODE = "daily_mainline_624_stop_mode"
        private const val KEY_RUN_COUNT = "daily_mainline_624_run_count"
        private const val KEY_LOW_SPEC_DELAY = "daily_mainline_624_low_spec_delay"
        private const val KEY_GAME_VARIANT = "daily_mainline_624_game_variant"
        private const val MODE_RESOURCE = "resource"
        private const val MODE_RUN_COUNT = "run_count"
    }
}
