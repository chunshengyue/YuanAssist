package com.example.yuanassist.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import com.example.yuanassist.R
import com.example.yuanassist.utils.DialogUtils

internal object StyledDialogUi {
    fun createDialogCard(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_job_station_card)
            setPadding(
                dpToPx(context, 18f),
                dpToPx(context, 18f),
                dpToPx(context, 18f),
                dpToPx(context, 18f)
            )
            minimumWidth = dpToPx(context, 300f)
        }
    }

    fun createDialogTitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#2F261B"))
        }
    }

    fun createDialogSubtitle(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#8C7A61"))
            setPadding(0, dpToPx(context, 6f), 0, 0)
        }
    }

    fun createFieldLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(0, dpToPx(context, 12f), 0, dpToPx(context, 6f))
        }
    }

    fun createStyledInput(context: Context, hint: String): EditText {
        return EditText(context).apply {
            this.hint = hint
            textSize = 14f
            setTextColor(Color.parseColor("#4E3C1E"))
            setHintTextColor(Color.parseColor("#9A8A71"))
            setBackgroundResource(R.drawable.bg_job_station_icon_button)
            setPadding(
                dpToPx(context, 14f),
                0,
                dpToPx(context, 14f),
                0
            )
            minHeight = dpToPx(context, 44f)
        }
    }

    fun createActionRow(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 14f)
            }
        }
    }

    fun createActionButton(context: Context, text: String, primary: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (primary) "#6B4E1C" else "#8C6C33"))
            setBackgroundResource(if (primary) R.drawable.bg_job_station_chip else R.drawable.bg_job_station_icon_button)
            setPadding(
                dpToPx(context, 14f),
                dpToPx(context, 10f),
                dpToPx(context, 14f),
                dpToPx(context, 10f)
            )
            isClickable = true
            isFocusable = true
        }
    }

    fun createWeightedButtonParams(context: Context, hasStartMargin: Boolean): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (hasStartMargin) {
                marginStart = dpToPx(context, 10f)
            }
        }
    }

    fun createScrollableContainer(context: Context, parent: LinearLayout): LinearLayout {
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(context, 10f)
            }
        )
        return container
    }

    fun createSelectableBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 18f
            if (selected) {
                setColor(Color.parseColor("#F6D59A"))
                setStroke(1, Color.parseColor("#C88A2C"))
            } else {
                setColor(Color.parseColor("#F8F2E5"))
                setStroke(1, Color.parseColor("#D8C18A"))
            }
        }
    }

    fun styleCompactCheckControl(
        context: Context,
        button: CompoundButton,
        label: String,
        checked: Boolean,
    ) {
        button.text = label
        button.isChecked = checked
        button.textSize = 15f
        button.setTextColor(Color.parseColor("#6C4A22"))
        button.setPadding(0, dpToPx(context, 6f), 0, dpToPx(context, 6f))
        when (button) {
            is CheckBox -> {
                button.buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C79C5C"))
            }
            is RadioButton -> {
                button.buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C79C5C"))
            }
        }
    }

    fun createCompactChoiceRow(
        context: Context,
        spacingDp: Float = 18f,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = ColorDrawable(Color.TRANSPARENT)
            dividerPadding = dpToPx(context, spacingDp / 2f)
        }
    }

    fun showOptionDialog(
        context: Context,
        title: String,
        options: List<String>,
        selectedIndex: Int = -1,
        onSelect: (Int) -> Unit
    ) {
        val rootLayout = createDialogCard(context)
        rootLayout.addView(createDialogTitle(context, title))
        val optionsContainer = createScrollableContainer(context, rootLayout)
        val btnClose = createActionButton(context, "取消", false)
        rootLayout.addView(btnClose)

        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, option ->
            val optionView = TextView(context).apply {
                text = option
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, if (index == selectedIndex) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(Color.parseColor(if (index == selectedIndex) "#6B4E1C" else "#8C6C33"))
                background = createSelectableBackground(index == selectedIndex)
                setPadding(
                    dpToPx(context, 14f),
                    dpToPx(context, 12f),
                    dpToPx(context, 14f),
                    dpToPx(context, 12f)
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dpToPx(context, 8f)
                    }
                }
                setOnClickListener {
                    onSelect(index)
                    dialog.dismiss()
                }
            }
            optionsContainer.addView(optionView)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        dialog = showStyledDialog(context, rootLayout)
    }

    fun showStyledDialog(context: Context, content: View): AlertDialog {
        val dialog = DialogUtils.safeShowOverlayDialog(
            AlertDialog.Builder(context)
                .setView(content)
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
