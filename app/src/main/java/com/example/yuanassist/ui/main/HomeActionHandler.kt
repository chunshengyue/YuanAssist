package com.example.yuanassist.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
            .setPositiveButton("浏览器下载") { _, _ ->
                openUpdateInBrowser(updateInfo.apkUrl)
            }
            .setNegativeButton("稍后提醒", null)
            .show()
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
        private const val ACTION_START_COORDINATE_PICKER = "ACTION_START_COORDINATE_PICKER"
        private const val ACTION_START_DAILY_SCRIPT_RECORDER = "ACTION_START_DAILY_SCRIPT_RECORDER"
    }
}
