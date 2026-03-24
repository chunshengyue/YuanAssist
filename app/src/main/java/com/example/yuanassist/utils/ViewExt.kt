package com.example.yuanassist.utils

import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

fun EditText.disableShowSoftInput() {
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