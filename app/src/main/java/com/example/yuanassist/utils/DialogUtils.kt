package com.example.yuanassist.utils

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.WindowManager

object DialogUtils {
    // 取得統一的亮色主題 Context
    fun getThemeContext(context: Context): Context {
        return ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light_Dialog)
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
        return dialog
    }
}