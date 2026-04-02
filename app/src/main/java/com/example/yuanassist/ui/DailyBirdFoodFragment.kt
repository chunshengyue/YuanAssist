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
import android.widget.CheckBox
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
import com.example.yuanassist.core.DailyBirdFoodBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.BirdFoodStopCondition
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.model.DaiBanGongWuOption

class DailyBirdFoodFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_daily_bird_food, container, false)
        bindViews(view)
        return view
    }

    private fun bindViews(view: View) {
        bindHeaderInsets(view)
        bindStopConditionInputs(view)
        bindGongWuOptions(view)
        restoreSavedSettings(view)

        view.findViewById<ImageView>(R.id.btn_daily_bird_food_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btn_daily_bird_food_confirm).setOnClickListener {
            val config = buildConfig(view) ?: return@setOnClickListener
            saveSettings(view)
            DailyBirdFoodBridge.pendingConfig = config
            startBirdFoodService()
        }
    }

    private fun bindGongWuOptions(view: View) {
        val gongwuCheckBox = view.findViewById<CheckBox>(R.id.cb_daily_bird_food_gongwu)
        val gongwuOptionLayout = view.findViewById<View>(R.id.layout_daily_bird_food_gongwu_option)

        fun refreshOptions() {
            gongwuOptionLayout.visibility = if (gongwuCheckBox.isChecked) View.VISIBLE else View.GONE
        }

        gongwuCheckBox.setOnCheckedChangeListener { _, _ ->
            refreshOptions()
        }
        refreshOptions()
    }

    private fun bindHeaderInsets(view: View) {
        val header = view.findViewById<View>(R.id.layout_daily_bird_food_header)
        val topSpace = view.findViewById<View>(R.id.view_daily_bird_food_status_space)
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
        val stopGroup = view.findViewById<RadioGroup>(R.id.rg_daily_bird_food_stop_condition)
        val runCount = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_run_count)
        val duration = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_duration)
        val runCountLayout = view.findViewById<View>(R.id.layout_daily_bird_food_run_count)
        val durationLayout = view.findViewById<View>(R.id.layout_daily_bird_food_duration)

        fun refreshInputs() {
            runCountLayout.visibility = if (runCount.isChecked) View.VISIBLE else View.GONE
            durationLayout.visibility = if (duration.isChecked) View.VISIBLE else View.GONE
        }

        stopGroup.setOnCheckedChangeListener { _, _ ->
            refreshInputs()
        }
        refreshInputs()
    }

    private fun buildConfig(view: View): BirdFoodConfig? {
        val selectedTasks = mutableListOf<BirdFoodTaskType>()
        if (view.findViewById<CheckBox>(R.id.cb_daily_bird_food_tufa).isChecked) {
            selectedTasks += BirdFoodTaskType.TU_FA_QING_KUANG
        }
        if (view.findViewById<CheckBox>(R.id.cb_daily_bird_food_xiaodao).isChecked) {
            selectedTasks += BirdFoodTaskType.XIAO_DAO_XIAO_XI
        }
        if (view.findViewById<CheckBox>(R.id.cb_daily_bird_food_chuanwen).isChecked) {
            selectedTasks += BirdFoodTaskType.TA_DE_CHUAN_WEN
        }
        if (view.findViewById<CheckBox>(R.id.cb_daily_bird_food_gongwu).isChecked) {
            selectedTasks += BirdFoodTaskType.DAI_BAN_GONG_WU
        }

        if (selectedTasks.isEmpty()) {
            Toast.makeText(requireContext(), "请至少选择一个任务", Toast.LENGTH_SHORT).show()
            return null
        }

        val autoEatOption = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_auto_eat)
        val currentOnlyOption = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_resource)
        val runCountOption = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_run_count)
        val durationOption = view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_duration)

        val autoEatEnabled =
            autoEatOption.isChecked || runCountOption.isChecked || durationOption.isChecked
        val stopCondition = when {
            runCountOption.isChecked -> BirdFoodStopCondition.RUN_COUNT
            durationOption.isChecked -> BirdFoodStopCondition.DURATION_MINUTES
            else -> BirdFoodStopCondition.RESOURCE_EXHAUSTED
        }

        val runCount = view.findViewById<EditText>(R.id.et_daily_bird_food_run_count)
            .text.toString().trim().toIntOrNull()
        val durationMinutes = view.findViewById<EditText>(R.id.et_daily_bird_food_duration)
            .text.toString().trim().toIntOrNull()
        val lowSpecDelayMs = view.findViewById<EditText>(R.id.et_daily_bird_food_low_spec_delay)
            .text.toString().trim().toLongOrNull() ?: 0L
        val debugModeEnabled = view.findViewById<CheckBox>(R.id.cb_daily_bird_food_debug_mode).isChecked
        val daiBanGongWuOption =
            if (view.findViewById<RadioButton>(R.id.rb_daily_bird_food_gongwu_wuzhuqian).isChecked) {
                DaiBanGongWuOption.WU_ZHU_QIAN
            } else {
                DaiBanGongWuOption.BING_SHU
            }

        if (!autoEatOption.isChecked &&
            !currentOnlyOption.isChecked &&
            !runCountOption.isChecked &&
            !durationOption.isChecked
        ) {
            Toast.makeText(requireContext(), "请选择运行方式", Toast.LENGTH_SHORT).show()
            return null
        }

        if (runCountOption.isChecked && (runCount == null || runCount <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的次数", Toast.LENGTH_SHORT).show()
            return null
        }

        if (durationOption.isChecked && (durationMinutes == null || durationMinutes <= 0)) {
            Toast.makeText(requireContext(), "请输入有效的分钟数", Toast.LENGTH_SHORT).show()
            return null
        }

        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }

        return BirdFoodConfig(
            selectedTasks = selectedTasks,
            autoEatEnabled = autoEatEnabled,
            stopCondition = stopCondition,
            debugModeEnabled = debugModeEnabled,
            lowSpecDelayMs = lowSpecDelayMs,
            maxRuns = runCount,
            maxDurationMinutes = durationMinutes,
            daiBanGongWuOption = daiBanGongWuOption
        )
    }

    private fun restoreSavedSettings(view: View) {
        val prefs = requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)

        if (prefs.contains(KEY_TASK_TUFA)) {
            view.findViewById<CheckBox>(R.id.cb_daily_bird_food_tufa)
                .isChecked = prefs.getBoolean(KEY_TASK_TUFA, true)
            view.findViewById<CheckBox>(R.id.cb_daily_bird_food_xiaodao)
                .isChecked = prefs.getBoolean(KEY_TASK_XIAODAO, false)
            view.findViewById<CheckBox>(R.id.cb_daily_bird_food_chuanwen)
                .isChecked = prefs.getBoolean(KEY_TASK_CHUANWEN, false)
            view.findViewById<CheckBox>(R.id.cb_daily_bird_food_gongwu)
                .isChecked = prefs.getBoolean(KEY_TASK_GONGWU, false)
        }

        view.findViewById<CheckBox>(R.id.cb_daily_bird_food_debug_mode)
            .isChecked = prefs.getBoolean(KEY_DEBUG_MODE, false)

        val savedGongwuOption = prefs.getString(KEY_GONGWU_OPTION, OPTION_BINGSHU)
        view.findViewById<RadioButton>(
            if (savedGongwuOption == OPTION_WUZHUQIAN) {
                R.id.rb_daily_bird_food_gongwu_wuzhuqian
            } else {
                R.id.rb_daily_bird_food_gongwu_bingshu
            }
        ).isChecked = true

        val savedStopMode = prefs.getString(KEY_STOP_MODE, null)
        when (savedStopMode) {
            MODE_AUTO_EAT -> view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_auto_eat).isChecked = true
            MODE_RESOURCE -> view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_resource).isChecked = true
            MODE_RUN_COUNT -> view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_run_count).isChecked = true
            MODE_DURATION -> view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_duration).isChecked = true
        }

        view.findViewById<EditText>(R.id.et_daily_bird_food_run_count)
            .setText(prefs.getString(KEY_RUN_COUNT, "").orEmpty())
        view.findViewById<EditText>(R.id.et_daily_bird_food_duration)
            .setText(prefs.getString(KEY_DURATION, "").orEmpty())
        view.findViewById<EditText>(R.id.et_daily_bird_food_low_spec_delay)
            .setText(prefs.getString(KEY_LOW_SPEC_DELAY, "").orEmpty())
    }

    private fun saveSettings(view: View) {
        val stopMode = when {
            view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_auto_eat).isChecked -> MODE_AUTO_EAT
            view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_run_count).isChecked -> MODE_RUN_COUNT
            view.findViewById<RadioButton>(R.id.rb_daily_bird_food_stop_duration).isChecked -> MODE_DURATION
            else -> MODE_RESOURCE
        }
        val gongwuOption = if (
            view.findViewById<RadioButton>(R.id.rb_daily_bird_food_gongwu_wuzhuqian).isChecked
        ) {
            OPTION_WUZHUQIAN
        } else {
            OPTION_BINGSHU
        }

        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TASK_TUFA, view.findViewById<CheckBox>(R.id.cb_daily_bird_food_tufa).isChecked)
            .putBoolean(KEY_TASK_XIAODAO, view.findViewById<CheckBox>(R.id.cb_daily_bird_food_xiaodao).isChecked)
            .putBoolean(KEY_TASK_CHUANWEN, view.findViewById<CheckBox>(R.id.cb_daily_bird_food_chuanwen).isChecked)
            .putBoolean(KEY_TASK_GONGWU, view.findViewById<CheckBox>(R.id.cb_daily_bird_food_gongwu).isChecked)
            .putBoolean(KEY_DEBUG_MODE, view.findViewById<CheckBox>(R.id.cb_daily_bird_food_debug_mode).isChecked)
            .putString(KEY_GONGWU_OPTION, gongwuOption)
            .putString(KEY_STOP_MODE, stopMode)
            .putString(KEY_RUN_COUNT, view.findViewById<EditText>(R.id.et_daily_bird_food_run_count).text.toString().trim())
            .putString(KEY_DURATION, view.findViewById<EditText>(R.id.et_daily_bird_food_duration).text.toString().trim())
            .putString(KEY_LOW_SPEC_DELAY, view.findViewById<EditText>(R.id.et_daily_bird_food_low_spec_delay).text.toString().trim())
            .apply()
    }

    private fun startBirdFoodService() {
        val context = requireContext()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", "ACTION_START_BIRD_FOOD")
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
                action = "ACTION_START_BIRD_FOOD"
            }
            context.startService(intent)
            Toast.makeText(context, "鸟食任务已交给悬浮窗执行", Toast.LENGTH_SHORT).show()
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
        private const val KEY_TASK_TUFA = "daily_bird_food_task_tufa"
        private const val KEY_TASK_XIAODAO = "daily_bird_food_task_xiaodao"
        private const val KEY_TASK_CHUANWEN = "daily_bird_food_task_chuanwen"
        private const val KEY_TASK_GONGWU = "daily_bird_food_task_gongwu"
        private const val KEY_DEBUG_MODE = "daily_bird_food_debug_mode"
        private const val KEY_GONGWU_OPTION = "daily_bird_food_gongwu_option"
        private const val KEY_STOP_MODE = "daily_bird_food_stop_mode"
        private const val KEY_RUN_COUNT = "daily_bird_food_run_count"
        private const val KEY_DURATION = "daily_bird_food_duration"
        private const val KEY_LOW_SPEC_DELAY = "daily_bird_food_low_spec_delay"
        private const val MODE_AUTO_EAT = "auto_eat"
        private const val MODE_RESOURCE = "resource"
        private const val MODE_RUN_COUNT = "run_count"
        private const val MODE_DURATION = "duration"
        private const val OPTION_BINGSHU = "bingshu"
        private const val OPTION_WUZHUQIAN = "wuzhuqian"
    }
}
