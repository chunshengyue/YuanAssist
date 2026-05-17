package com.example.yuanassist.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.WindowManager
import com.example.yuanassist.R

object DialogUtils {
    // 取得統一的亮色主題 Context
    fun getThemeContext(context: Context): Context {
        return ContextThemeWrapper(context, R.style.ThemeOverlay_YuanAssist_AlertDialog)
    }

    // 安全地在懸浮窗中顯示 Dialog (消除重複代碼)
    fun safeShowOverlayDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
        styleAlertDialog(dialog)
        return dialog
    }

    fun styleAlertDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_job_station_card)
        val primary = Color.parseColor("#8C6C33")
        val secondary = Color.parseColor("#6C5B43")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primary)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)
    }
}
