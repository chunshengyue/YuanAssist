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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService

class DailyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_daily, container, false)
        bindViews(view)
        return view
    }

    private fun bindViews(view: View) {
        view.findViewById<LinearLayout>(R.id.item_daily_bird_food).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DailyBirdFoodFragment())
                .addToBackStack("daily_bird_food")
                .commit()
        }

        view.findViewById<LinearLayout>(R.id.item_daily_inventory_stitch).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DailyInventoryStitchFragment())
                .addToBackStack("daily_inventory_stitch")
                .commit()
        }

        view.findViewById<LinearLayout>(R.id.item_daily_coordinate_picker).setOnClickListener {
            startDailyToolService(
                action = ACTION_START_COORDINATE_PICKER,
                successMessage = "已打开屏幕选点"
            )
        }
    }

    private fun startDailyToolService(action: String, successMessage: String) {
        val context = requireContext()
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", action)
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
                this.action = action
            }
            context.startService(intent)
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
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
        private const val ACTION_START_COORDINATE_PICKER = "ACTION_START_COORDINATE_PICKER"
    }
}
