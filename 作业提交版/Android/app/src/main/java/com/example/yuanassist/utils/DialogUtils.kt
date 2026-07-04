package com.example.yuanassist.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.yuanassist.R

object DialogUtils {
    private const val OPTION_TEXT_COLOR = "#4E3C1E"
    private const val SELECTED_OPTION_TEXT_COLOR = "#735637"
    private const val DIALOG_MESSAGE_TEXT_COLOR = "#1F1F1F"

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

    fun showStyledDialog(builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        dialog.show()
        styleAlertDialog(dialog)
        return dialog
    }

    fun showStyledDialog(
        builder: androidx.appcompat.app.AlertDialog.Builder,
    ): androidx.appcompat.app.AlertDialog {
        val dialog = builder.create()
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
        dialog.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(Color.parseColor(DIALOG_MESSAGE_TEXT_COLOR))
    }

    fun styleAlertDialog(dialog: androidx.appcompat.app.AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_job_station_card)
        val primary = Color.parseColor("#8C6C33")
        val secondary = Color.parseColor("#6C5B43")
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(primary)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)
        dialog.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(Color.parseColor(DIALOG_MESSAGE_TEXT_COLOR))
    }

    fun fixedOptionTextAdapter(context: Context, labels: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            getThemeContext(context),
            android.R.layout.simple_list_item_1,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).applyFixedOptionTextStyle()
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).applyFixedOptionTextStyle()
            }
        }
    }

    fun fixedDropdownTextAdapter(context: Context, labels: Array<String>): ArrayAdapter<String> {
        return fixedDropdownTextAdapter(context, labels.toList())
    }

    fun fixedDropdownTextAdapter(context: Context, labels: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            getThemeContext(context),
            android.R.layout.simple_spinner_item,
            labels
        ) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).applyFixedSelectedOptionTextStyle()
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getDropDownView(position, convertView, parent).applyFixedOptionTextStyle()
            }
        }
    }

    private fun View.applyFixedOptionTextStyle(): View {
        (this as? TextView)?.setTextColor(Color.parseColor(OPTION_TEXT_COLOR))
        return this
    }

    private fun View.applyFixedSelectedOptionTextStyle(): View {
        (this as? TextView)?.setTextColor(Color.parseColor(SELECTED_OPTION_TEXT_COLOR))
        return this
    }
}
