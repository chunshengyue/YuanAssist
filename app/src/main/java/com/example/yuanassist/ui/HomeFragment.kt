package com.example.yuanassist.ui

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
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
import java.io.File

class HomeFragment : Fragment() {

    private lateinit var ddlAdapter: DdlAdapter
    private lateinit var btnStartCombat: Button
    private lateinit var btnStartDaily: Button
    private val ddlList = ArrayList<ddl_list>()
    private var updateDownloadId: Long = -1L
    private var updateApkFile: File? = null
    private var updateReceiverRegistered = false
    private var pendingInstallAfterPermission = false
    private var overlayPrefsListenerRegistered = false
    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId != updateDownloadId || downloadId == -1L) return
            handleDownloadedApk(downloadId)
        }
    }
    private val overlayPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_COMBAT_WINDOW_OPEN || key == KEY_DAILY_WINDOW_OPEN) {
                activity?.runOnUiThread {
                    if (view != null) {
                        updateOverlayButtonTexts()
                    }
                }
            }
        }

    companion object {
        private const val PREFS_APP = "app_prefs"
        private const val KEY_COMBAT_WINDOW_OPEN = "combat_window_open"
        private const val KEY_DAILY_WINDOW_OPEN = "daily_window_open"
        private const val UPDATE_APK_DIR = "update"
        private const val UPDATE_APK_NAME = "yuanassist-latest.apk"
    }

    private fun openUpdateInBrowser(apkUrl: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "无法打开浏览器下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUpdateOptionsDialog(updateInfo: update) {
        val titleView = android.widget.TextView(requireContext()).apply {
            text = "发现新版本 ${updateInfo.versionName}"
            textSize = 18f
            setPadding(60, 50, 60, 10)
            setTextColor(android.graphics.Color.parseColor("#E5C07B"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val messageView = android.widget.TextView(requireContext()).apply {
            text = updateInfo.releaseNotes
            textSize = 14f
            setPadding(60, 20, 60, 20)
            setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            setLineSpacing(0f, 1.2f)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton("浏览器下载") { dialog, _ ->
                openUpdateInBrowser(updateInfo.apkUrl)
                dialog.dismiss()
            }
            .setPositiveButton("应用内下载") { dialog, _ ->
                startInAppUpdateDownload(updateInfo)
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dark_glass)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#E5C07B"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#888888"))
    }

    private fun startInAppUpdateDownload(updateInfo: update) {
        val context = context ?: return
        val apkUrl = updateInfo.apkUrl.trim()
        if (apkUrl.isEmpty()) {
            Toast.makeText(context, "更新地址为空", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Toast.makeText(context, "系统下载服务不可用，改用浏览器下载", Toast.LENGTH_SHORT).show()
            openUpdateInBrowser(apkUrl)
            return
        }

        cleanupUpdateArtifacts(downloadManager)

        val downloadRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadRoot == null) {
            Toast.makeText(context, "应用内下载目录不可用，改用浏览器下载", Toast.LENGTH_SHORT).show()
            openUpdateInBrowser(apkUrl)
            return
        }

        val updateDir = File(downloadRoot, UPDATE_APK_DIR).apply { mkdirs() }
        val apkFile = File(updateDir, UPDATE_APK_NAME)
        updateApkFile = apkFile

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("YuanAssist ${updateInfo.versionName}")
            setDescription("正在下载更新包")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationUri(Uri.fromFile(apkFile))
        }

        runCatching {
            registerUpdateReceiverIfNeeded()
            updateDownloadId = downloadManager.enqueue(request)
            Toast.makeText(context, "开始应用内下载更新", Toast.LENGTH_SHORT).show()
        }.onFailure {
            cleanupUpdateArtifacts(downloadManager)
            Toast.makeText(context, "应用内下载启动失败，改用浏览器下载", Toast.LENGTH_SHORT).show()
            openUpdateInBrowser(apkUrl)
        }
    }

    private fun handleDownloadedApk(downloadId: Long) {
        val context = context ?: return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor == null) {
            cleanupUpdateArtifacts(downloadManager)
            Toast.makeText(context, "下载结果读取失败，已清理缓存", Toast.LENGTH_SHORT).show()
            return
        }

        cursor.use {
            if (!it.moveToFirst()) {
                cleanupUpdateArtifacts(downloadManager)
                Toast.makeText(context, "下载结果缺失，已清理缓存", Toast.LENGTH_SHORT).show()
                return
            }
            val status = getDownloadInt(it, DownloadManager.COLUMN_STATUS, DownloadManager.STATUS_FAILED)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                if (!canInstallUnknownApps()) {
                    pendingInstallAfterPermission = true
                    Toast.makeText(context, "请允许本应用安装未知应用后继续安装", Toast.LENGTH_LONG).show()
                    openUnknownAppsSettings()
                    return
                }
                installDownloadedApk()
            } else {
                val reason = getDownloadInt(it, DownloadManager.COLUMN_REASON, -1)
                cleanupUpdateArtifacts(downloadManager)
                Toast.makeText(context, "应用内下载失败($reason)，已清理缓存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDownloadInt(cursor: Cursor, column: String, defaultValue: Int): Int {
        val index = cursor.getColumnIndex(column)
        return if (index >= 0) cursor.getInt(index) else defaultValue
    }

    private fun installDownloadedApk() {
        val context = context ?: return
        val apkFile = updateApkFile
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            startActivity(installIntent)
        }.onFailure {
            cleanupUpdateArtifacts()
            Toast.makeText(context, "无法拉起安装界面，已清理缓存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun canInstallUnknownApps(): Boolean {
        val context = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openUnknownAppsSettings() {
        val context = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    private fun registerUpdateReceiverIfNeeded() {
        if (updateReceiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(updateDownloadReceiver, filter)
        }
        updateReceiverRegistered = true
    }

    private fun unregisterUpdateReceiverIfNeeded() {
        if (!updateReceiverRegistered) return
        val context = context ?: return
        runCatching {
            context.unregisterReceiver(updateDownloadReceiver)
        }
        updateReceiverRegistered = false
    }

    private fun cleanupUpdateArtifacts(downloadManager: DownloadManager? = null) {
        if (updateDownloadId != -1L) {
            downloadManager?.remove(updateDownloadId)
        }
        updateDownloadId = -1L
        pendingInstallAfterPermission = false
        updateApkFile?.let { file ->
            if (file.exists()) {
                runCatching { file.delete() }
            }
        }
        updateApkFile = null
    }

    private fun registerOverlayPrefsListenerIfNeeded() {
        if (overlayPrefsListenerRegistered || context == null) return
        requireContext()
            .getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(overlayPrefsListener)
        overlayPrefsListenerRegistered = true
    }

    private fun unregisterOverlayPrefsListenerIfNeeded() {
        if (!overlayPrefsListenerRegistered || context == null) return
        requireContext()
            .getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(overlayPrefsListener)
        overlayPrefsListenerRegistered = false
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
        registerOverlayPrefsListenerIfNeeded()
        if (view != null) {
            updateOverlayButtonTexts()
        }
        if (pendingInstallAfterPermission && updateApkFile?.exists() == true && canInstallUnknownApps()) {
            pendingInstallAfterPermission = false
            installDownloadedApk()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterOverlayPrefsListenerIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterUpdateReceiverIfNeeded()
        unregisterOverlayPrefsListenerIfNeeded()
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
                            showUpdateOptionsDialog(updateInfo)
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
