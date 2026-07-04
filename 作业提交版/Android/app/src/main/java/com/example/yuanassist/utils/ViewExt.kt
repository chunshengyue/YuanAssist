package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.example.yuanassist.R

fun EditText.disableShowSoftInput() {
    protectInputLongPress()
    this.showSoftInputOnFocus = false
    this.setOnFocusChangeListener { view, hasFocus ->
        if (hasFocus) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}

fun EditText.protectInputLongPress() {
    this.isFocusable = true
    this.isFocusableInTouchMode = true
    this.isClickable = true
    this.isLongClickable = false
    this.setOnLongClickListener { true }

    val callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }
    this.customSelectionActionModeCallback = callback
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.customInsertionActionModeCallback = callback
    }
}

fun EditText.applyYuanInputStyle() {
    val density = resources.displayMetrics.density
    setTextColor(Color.parseColor("#4E3C1E"))
    setHintTextColor(Color.parseColor("#9A8A71"))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    setBackgroundResource(R.drawable.bg_job_station_icon_button)
    setPadding(
        (14f * density).toInt(),
        (8f * density).toInt(),
        (14f * density).toInt(),
        (8f * density).toInt()
    )
    minHeight = (44f * density).toInt()
}
