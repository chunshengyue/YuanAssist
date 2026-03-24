package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.announcement
import com.example.yuanassist.model.ddl_list
import com.example.yuanassist.model.update

class HomeFragment : Fragment() {

    private lateinit var ddlAdapter: DdlAdapter
    private lateinit var btnStartCombat: Button
    private lateinit var btnStartDaily: Button
    private val ddlList = ArrayList<ddl_list>()

    companion object {
        private const val PREFS_APP = "app_prefs"
        private const val KEY_COMBAT_WINDOW_OPEN = "combat_window_open"
        private const val KEY_DAILY_WINDOW_OPEN = "daily_window_open"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 fragment_home 布局
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        initViews(view)
        loadDdlData()
        checkAnnouncement()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            updateOverlayButtonTexts()
        }
    }

    private fun initViews(view: View) {
        btnStartCombat = view.findViewById(R.id.btn_main_start_combat)
        btnStartDaily = view.findViewById(R.id.btn_main_start_daily)
        // --- 核心功能区 ---
        btnStartCombat.setOnClickListener {
            if (isCombatWindowOpen()) {
                toggleOverlay("ACTION_CLOSE_COMBAT_WINDOW", "战斗悬浮窗已关闭")
            } else {
                checkPermissionsAndStart("ACTION_START_COMBAT_WINDOW")
            }
        }

        view.findViewById<Button>(R.id.btn_main_combat_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // --- 日常版区 ---
        btnStartDaily.setOnClickListener {
            if (isDailyWindowOpen()) {
                toggleOverlay("ACTION_CLOSE_DAILY_WINDOW", "日常悬浮窗已关闭")
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                checkPermissionsAndStart("ACTION_START_DAILY_WINDOW")
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("系统版本过低")
                    .setMessage("日常自动化功能依赖安卓 11 的原生截图 API，您的设备暂不支持。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }

        view.findViewById<Button>(R.id.btn_main_daily_settings).setOnClickListener {
            Toast.makeText(requireContext(), "日常版设置开发中...", Toast.LENGTH_SHORT).show()
        }

        // --- 中间 2x2 功能网格 ---
        view.findViewById<Button>(R.id.btn_main_scripts).setOnClickListener {
            startActivity(Intent(requireContext(), ScriptLibraryActivity::class.java))
        }

        view.findViewById<Button>(R.id.btn_main_tutorial).setOnClickListener {
            startActivity(Intent(requireContext(), RunLogActivity::class.java))
        }

        view.findViewById<Button>(R.id.btn_main_update).setOnClickListener {
            checkUpdate()
        }

        view.findViewById<Button>(R.id.btn_main_theme).setOnClickListener {
            startActivity(Intent(requireContext(), TestActivity::class.java))
        }

        // --- 初始化 DDL RecyclerView ---
        val rvDdl = view.findViewById<RecyclerView>(R.id.rv_ddl_list)
        ddlAdapter = DdlAdapter(ddlList)
        rvDdl.layoutManager = LinearLayoutManager(requireContext())
        rvDdl.adapter = ddlAdapter

        view.findViewById<Button>(R.id.btn_main_daily_settings).setOnClickListener {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null) {
                mainActivity.navigateToTab(R.id.nav_daily)
            } else {
                startActivity(
                    Intent(requireContext(), MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TARGET_TAB_DAILY)
                    }
                )
            }
        }

        updateOverlayButtonTexts()
    }

    private fun loadDdlData() {
        val query = BmobQuery<ddl_list>()
        query.order("endTime")
        query.findObjects(object : FindListener<ddl_list>() {
            override fun done(list: MutableList<ddl_list>?, e: BmobException?) {
                val ctx = context ?: return
                if (e == null && list != null) {
                    activity?.runOnUiThread { ddlAdapter.updateData(list) }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(ctx, "DDL加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun checkUpdate() {
        Toast.makeText(requireContext(), "正在检查更新...", Toast.LENGTH_SHORT).show()
        val query = BmobQuery<update>()
        query.order("-versionCode")
        query.setLimit(1)

        query.findObjects(object : FindListener<update>() {
            override fun done(list: MutableList<update>?, e: BmobException?) {
                val ctx = context ?: return
                if (e == null && list != null && list.isNotEmpty()) {
                    val updateInfo = list[0]
                    val (localCode, _) = getAppVersion(ctx)

                    activity?.runOnUiThread {
                        if (updateInfo.versionCode > localCode) {
                            showUpdateDialog(updateInfo)
                        } else {
                            Toast.makeText(ctx, "已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(ctx, "获取版本信息失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showUpdateDialog(updateInfo: update) {
        // 1. 自定义标题样式 (暗金字体)
        val titleView = android.widget.TextView(requireContext()).apply {
            text = "发现新版本 ${updateInfo.versionName}"
            textSize = 18f
            setPadding(60, 50, 60, 10)
            setTextColor(android.graphics.Color.parseColor("#E5C07B"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // 2. 自定义内容样式 (浅灰字体，适应深色背景)
        val messageView = android.widget.TextView(requireContext()).apply {
            text = updateInfo.releaseNotes
            textSize = 14f
            setPadding(60, 20, 60, 20)
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            setLineSpacing(0f, 1.2f) // 稍微增加一点行距更好看
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setCustomTitle(titleView)
            .setView(messageView)
            .setPositiveButton("立即更新") { dialog, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "无法打开浏览器下载", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("稍后提醒", null)
            .setCancelable(false)
            .create()

        dialog.show()

        // 3. 核心换肤：把背景改成深色玻璃，并修改按钮颜色
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dark_glass)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#E5C07B")) // 确认按钮暗金
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#888888")) // 取消按钮暗灰
    }

    private fun checkAnnouncement() {
        val query = BmobQuery<announcement>()
        query.order("-version")
        query.setLimit(1)

        query.findObjects(object : FindListener<announcement>() {
            override fun done(list: MutableList<announcement>?, e: BmobException?) {
                val ctx = context ?: return
                if (e == null && list != null && list.isNotEmpty()) {
                    val ann = list[0]
                    val prefs: SharedPreferences = ctx.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                    val localVersion = prefs.getInt("read_announcement_version", 0)

                    if (ann.version > localVersion) {
                        activity?.runOnUiThread {
                            showAnnouncementDialog(ann.title, ann.content, ann.version, prefs)
                        }
                    }
                }
            }
        })
    }

    private fun showAnnouncementDialog(title: String, content: String, version: Int, prefs: SharedPreferences) {
        val titleView = android.widget.TextView(requireContext()).apply {
            text = title
            textSize = 18f
            setPadding(60, 50, 60, 10)
            setTextColor(android.graphics.Color.parseColor("#E5C07B"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val messageView = android.widget.TextView(requireContext()).apply {
            text = content
            textSize = 14f
            setPadding(60, 20, 60, 20)
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            setLineSpacing(0f, 1.2f)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setCustomTitle(titleView)
            .setView(messageView)
            .setCancelable(false)
            .setPositiveButton("我知道了") { dialog, _ ->
                prefs.edit().putInt("read_announcement_version", version).apply()
                dialog.dismiss()
            }
            .create()

        dialog.show()

        // 3. 核心换肤：背景换成深色玻璃，按钮变暗金
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dark_glass)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#E5C07B"))
    }

    private fun getAppVersion(context: Context): Pair<Long, String> {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val versionName = packageInfo.versionName ?: "Unknown"
            Pair(versionCode, versionName)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(-1L, "Unknown")
        }
    }

    private fun checkPermissionsAndStart(targetAction: String) {
        requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", targetAction)
            .apply()

        if (!Settings.canDrawOverlays(requireContext())) {
            Toast.makeText(requireContext(), "请开启悬浮窗权限", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
            } catch (e: Exception) {}
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(requireContext(), "请开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {}
            return
        }

        try {
            val intent = Intent(requireContext(), YuanAssistService::class.java).apply { action = targetAction }
            requireContext().startService(intent)
            markOverlayStateAfterAction(targetAction)
            updateOverlayButtonTexts()
            val msg = if (targetAction == "ACTION_START_DAILY_WINDOW") "日常版已启动" else "悬浮窗已启动"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleOverlay(action: String, successMessage: String) {
        try {
            requireContext().startService(
                Intent(requireContext(), YuanAssistService::class.java).apply {
                    this.action = action
                }
            )
            markOverlayStateAfterAction(action)
            updateOverlayButtonTexts()
            Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "关闭悬浮窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateOverlayButtonTexts() {
        btnStartCombat.text = if (isCombatWindowOpen()) "关闭悬浮窗" else getString(R.string.btn_start_combat)
        btnStartDaily.text = if (isDailyWindowOpen()) "关闭悬浮窗" else getString(R.string.btn_start_daily)
    }

    private fun isCombatWindowOpen(): Boolean {
        return requireContext()
            .getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMBAT_WINDOW_OPEN, false)
    }

    private fun isDailyWindowOpen(): Boolean {
        return requireContext()
            .getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .getBoolean(KEY_DAILY_WINDOW_OPEN, false)
    }

    private fun markOverlayStateAfterAction(action: String) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit().apply {
            when (action) {
                "ACTION_START_COMBAT_WINDOW" -> {
                    putBoolean(KEY_COMBAT_WINDOW_OPEN, true)
                    putBoolean(KEY_DAILY_WINDOW_OPEN, false)
                }
                "ACTION_START_DAILY_WINDOW" -> {
                    putBoolean(KEY_DAILY_WINDOW_OPEN, true)
                    putBoolean(KEY_COMBAT_WINDOW_OPEN, false)
                }
                "ACTION_CLOSE_COMBAT_WINDOW" -> {
                    putBoolean(KEY_COMBAT_WINDOW_OPEN, false)
                }
                "ACTION_CLOSE_DAILY_WINDOW" -> {
                    putBoolean(KEY_DAILY_WINDOW_OPEN, false)
                }
            }
            apply()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(requireContext(), YuanAssistService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) return true
        }
        return false
    }
}
