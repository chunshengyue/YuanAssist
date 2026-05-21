package com.example.yuanassist.ui.main

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.update
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.FaqActivity
import com.example.yuanassist.ui.FeedbackCenterActivity
import com.example.yuanassist.ui.LegacyFragmentHostActivity
import com.example.yuanassist.ui.RunLogActivity
import com.example.yuanassist.ui.ScriptLibraryActivity
import com.example.yuanassist.ui.SettingsActivity
import java.io.File

class HomeActionHandler(
    private val activity: AppCompatActivity,
) {

    fun isCombatWindowOpen(): Boolean {
        return prefs().getBoolean(KEY_COMBAT_WINDOW_OPEN, false)
    }

    fun toggleCombatOverlay() {
        if (isCombatWindowOpen()) {
            toggleOverlay("ACTION_CLOSE_COMBAT_WINDOW", "战斗悬浮窗已关闭")
        } else {
            checkPermissionsAndStart("ACTION_START_COMBAT_WINDOW")
        }
    }

    fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    fun openBirdFood() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_BIRD_FOOD)
    }

    fun openMainline624() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_MAINLINE_624)
    }

    fun openStargazing() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_STARGAZING)
    }

    fun openAilao15Min() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_AILAO_15_MIN)
    }

    fun openPiJingZhanJi() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_PI_JING_ZHAN_JI)
    }

    fun openInventoryStitch() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.DAILY_INVENTORY_STITCH)
    }

    fun openCharacterImport() {
        openDailyScreen(LegacyFragmentHostActivity.Screen.CHARACTER_IMPORT)
    }

    fun openRunLog() {
        activity.startActivity(Intent(activity, RunLogActivity::class.java))
    }

    fun openFaq() {
        activity.startActivity(Intent(activity, FaqActivity::class.java))
    }

    fun openScriptLibrary() {
        activity.startActivity(Intent(activity, ScriptLibraryActivity::class.java))
    }

    fun openFeedbackCenter() {
        val currentUser = SupabaseRepository.getCurrentUser(activity)
        if (currentUser == null) {
            Toast.makeText(activity, "请先登录后再反馈问题", Toast.LENGTH_SHORT).show()
            return
        }
        activity.startActivity(Intent(activity, FeedbackCenterActivity::class.java))
    }

    fun startCoordinatePicker() {
        startDailyToolService(
            action = ACTION_START_COORDINATE_PICKER,
            successMessage = "已打开屏幕选点",
        )
    }

    fun startBoxOcr() {
        startDailyToolService(
            action = ACTION_START_BOX_OCR,
            successMessage = "框选OCR已导入到悬浮窗，请点击开始按钮执行",
        )
    }

    fun startDailyScriptRecorder() {
        startDailyToolService(
            action = ACTION_START_DAILY_SCRIPT_RECORDER,
            successMessage = "已打开脚本录制器",
        )
    }

    fun checkUpdate() {
        Toast.makeText(activity, "正在检查更新...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.getLatestUpdate(
            onSuccess = { updateInfo ->
                activity.runOnUiThread {
                    handleUpdateInfo(updateInfo)
                }
            },
            onError = { message ->
                activity.runOnUiThread {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun handleUpdateInfo(updateInfo: update) {
        val localVersionCode = getAppVersionCode()
        if (updateInfo.versionCode.toLong() <= localVersionCode) {
            Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${updateInfo.versionName}")
            .setMessage(updateInfo.releaseNotes.ifBlank { "检测到新版本，是否前往下载？" })
            .setPositiveButton("应用内下载") { _, _ ->
                downloadUpdateInApp(updateInfo)
            }
            .setNeutralButton("浏览器下载") { _, _ ->
                openUpdateInBrowser(updateInfo.apkUrl)
            }
            .setNegativeButton("稍后提醒", null)
            .show()
    }

    private fun downloadUpdateInApp(updateInfo: update) {
        val apkUrl = updateInfo.apkUrl.trim()
        if (apkUrl.isBlank()) {
            openUpdateInBrowser(updateInfo.apkUrl)
            return
        }

        val apkFile = updateApkFile(updateInfo)
        runCatching {
            apkFile.parentFile?.mkdirs()
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("YuanAssist ${updateInfo.versionName.ifBlank { "新版本" }}")
                .setDescription("正在下载更新安装包")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(apkFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val downloadId = manager.enqueue(request)
            registerInstallAfterDownloadReceiver(downloadId, apkFile, manager)
            Toast.makeText(activity, "已开始应用内下载", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(activity, "系统下载器不可用，改用浏览器下载", Toast.LENGTH_SHORT).show()
            openUpdateInBrowser(updateInfo.apkUrl)
        }
    }

    private fun registerInstallAfterDownloadReceiver(
        downloadId: Long,
        apkFile: File,
        manager: DownloadManager,
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return

                runCatching { context.unregisterReceiver(this) }
                if (isDownloadSuccessful(manager, downloadId)) {
                    installDownloadedApk(apkFile)
                } else {
                    Toast.makeText(activity, "下载失败，请尝试浏览器下载", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }
    }

    private fun isDownloadSuccessful(manager: DownloadManager, downloadId: Long): Boolean {
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return false
            return cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun installDownloadedApk(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, "请允许 YuanAssist 安装未知应用后再安装", Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            activity.startActivity(installIntent)
        }.onFailure {
            Toast.makeText(activity, "无法打开安装器，请尝试浏览器下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateApkFile(updateInfo: update): File {
        val safeVersionName = updateInfo.versionName.ifBlank { updateInfo.versionCode.toString() }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "update/YuanAssist-$safeVersionName.apk",
        )
    }

    private fun openUpdateInBrowser(apkUrl: String) {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
        }.onFailure {
            Toast.makeText(activity, "无法打开浏览器下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDailyScreen(screen: LegacyFragmentHostActivity.Screen) {
        if (!supportsDailyModule()) {
            showDailyUnsupportedDialog()
            return
        }
        activity.startActivity(LegacyFragmentHostActivity.createIntent(activity, screen))
    }

    private fun startDailyToolService(action: String, successMessage: String) {
        if (!supportsDailyModule()) {
            showDailyUnsupportedDialog()
            return
        }

        prefs().edit()
            .putString(KEY_PENDING_START_ACTION, action)
            .apply()

        if (!Settings.canDrawOverlays(activity)) {
            Toast.makeText(activity, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(activity, "请启用 YuanAssist 无障碍服务", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            activity.startService(
                Intent(activity, YuanAssistService::class.java).apply {
                    this.action = action
                },
            )
            Toast.makeText(activity, successMessage, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(activity, "启动失败：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart(targetAction: String) {
        prefs().edit()
            .putString(KEY_PENDING_START_ACTION, targetAction)
            .apply()

        if (!Settings.canDrawOverlays(activity)) {
            Toast.makeText(activity, "请开启悬浮窗权限", Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(activity, "请开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            activity.startService(
                Intent(activity, YuanAssistService::class.java).apply {
                    action = targetAction
                },
            )
            markOverlayStateAfterAction(targetAction)
            Toast.makeText(activity, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(activity, "启动服务失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleOverlay(action: String, successMessage: String) {
        try {
            activity.startService(
                Intent(activity, YuanAssistService::class.java).apply {
                    this.action = action
                },
            )
            markOverlayStateAfterAction(action)
            Toast.makeText(activity, successMessage, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(activity, "关闭悬浮窗失败: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markOverlayStateAfterAction(action: String) {
        prefs().edit().apply {
            when (action) {
                "ACTION_START_COMBAT_WINDOW" -> {
                    putBoolean(KEY_COMBAT_WINDOW_OPEN, true)
                    putBoolean(KEY_DAILY_WINDOW_OPEN, false)
                }

                "ACTION_CLOSE_COMBAT_WINDOW" -> putBoolean(KEY_COMBAT_WINDOW_OPEN, false)
                "ACTION_CLOSE_DAILY_WINDOW" -> putBoolean(KEY_DAILY_WINDOW_OPEN, false)
            }
            apply()
        }
    }

    private fun supportsDailyModule(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private fun showDailyUnsupportedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("系统版本过低")
            .setMessage("日常自动化功能依赖安卓 11 的原生截图 API，您的设备暂不支持。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(activity, YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) {
                return true
            }
        }
        return false
    }

    private fun getAppVersionCode(): Long {
        return try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun prefs() = activity.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_APP = "app_prefs"
        private const val KEY_COMBAT_WINDOW_OPEN = "combat_window_open"
        private const val KEY_DAILY_WINDOW_OPEN = "daily_window_open"
        private const val KEY_PENDING_START_ACTION = "pending_start_action"
        private const val ACTION_START_BOX_OCR = "ACTION_START_BOX_OCR"
        private const val ACTION_START_COORDINATE_PICKER = "ACTION_START_COORDINATE_PICKER"
        private const val ACTION_START_DAILY_SCRIPT_RECORDER = "ACTION_START_DAILY_SCRIPT_RECORDER"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
