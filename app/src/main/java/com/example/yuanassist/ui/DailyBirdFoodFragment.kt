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

        view.findViewById<ImageView>(R.id.btn_daily_bird_food_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btn_daily_bird_food_confirm).setOnClickListener {
            val config = buildConfig(view) ?: return@setOnClickListener
            DailyBirdFoodBridge.pendingConfig = config
            startBirdFoodService()
        }
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
        val debugModeEnabled = view.findViewById<CheckBox>(R.id.cb_daily_bird_food_debug_mode).isChecked

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

        return BirdFoodConfig(
            selectedTasks = selectedTasks,
            autoEatEnabled = autoEatEnabled,
            stopCondition = stopCondition,
            debugModeEnabled = debugModeEnabled,
            maxRuns = runCount,
            maxDurationMinutes = durationMinutes
        )
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
}
