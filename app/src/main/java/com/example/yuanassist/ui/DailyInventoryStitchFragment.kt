package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.utils.MyStoneStore

class DailyInventoryStitchFragment : Fragment() {

    private var selectedStoneType: String = MyStoneStore.TYPE_MAIN
    private var mainTypeView: TextView? = null
    private var supportTypeView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_daily_inventory_stitch, container, false)
        bindViews(view)
        loadExampleImage(view)
        return view
    }

    private fun bindViews(view: View) {
        selectedStoneType = MyStoneStore.getSelectedType(requireContext())
        mainTypeView = view.findViewById(R.id.tv_daily_inventory_stitch_type_main)
        supportTypeView = view.findViewById(R.id.tv_daily_inventory_stitch_type_support)

        view.findViewById<ImageView>(R.id.btn_daily_inventory_stitch_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        mainTypeView?.setOnClickListener {
            updateSelectedType(MyStoneStore.TYPE_MAIN)
        }
        supportTypeView?.setOnClickListener {
            updateSelectedType(MyStoneStore.TYPE_SUPPORT)
        }

        view.findViewById<Button>(R.id.btn_daily_inventory_stitch_import).setOnClickListener {
            startInventoryStitchService()
        }

        renderTypeSelection()
    }

    private fun loadExampleImage(view: View) {
        val startExampleView = view.findViewById<ImageView>(R.id.iv_daily_inventory_stitch_example)
        try {
            requireContext().assets.open("xingshishili.jpg").use { stream ->
                startExampleView.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "示例图加载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startInventoryStitchService() {
        val context = requireContext()
        MyStoneStore.setSelectedType(context, selectedStoneType)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_INVENTORY_STITCH)
            .putString(KEY_PENDING_STONE_TYPE, selectedStoneType)
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
                action = ACTION_START_INVENTORY_STITCH
                putExtra(KEY_PENDING_STONE_TYPE, selectedStoneType)
            }
            context.startService(intent)
            Toast.makeText(
                context,
                "已准备${MyStoneStore.displayName(selectedStoneType)}拼图，请点击悬浮窗开始按钮",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectedType(stoneType: String) {
        selectedStoneType = MyStoneStore.normalizeType(stoneType)
        MyStoneStore.setSelectedType(requireContext(), selectedStoneType)
        renderTypeSelection()
    }

    private fun renderTypeSelection() {
        val isMainSelected = selectedStoneType == MyStoneStore.TYPE_MAIN
        mainTypeView?.setBackgroundResource(if (isMainSelected) R.drawable.btn_dark_gold else R.drawable.btn_dark_hollow)
        mainTypeView?.setTextColor(if (isMainSelected) 0xFF1A1A1A.toInt() else 0xFFE5C07B.toInt())
        supportTypeView?.setBackgroundResource(if (isMainSelected) R.drawable.btn_dark_hollow else R.drawable.btn_dark_gold)
        supportTypeView?.setTextColor(if (isMainSelected) 0xFFE5C07B.toInt() else 0xFF1A1A1A.toInt())
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
        private const val ACTION_START_INVENTORY_STITCH = "ACTION_START_INVENTORY_STITCH"
        const val KEY_PENDING_STONE_TYPE = "pending_stone_type"
    }
}
