package com.example.yuanassist.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R

data class RunLogSection(
    val title: String,
    val subtitle: String,
    val content: String,
    var expanded: Boolean = false
)

class RunLogSectionAdapter(
    private val onCopySection: (RunLogSection) -> Unit
) : RecyclerView.Adapter<RunLogSectionAdapter.ViewHolder>() {

    companion object {
        private val LOG_LINE_REGEX = Regex("""^\[[^\]]+] \[([^\]]+)] (.*)$""")
        private const val COLOR_NORMAL = "#FFFFFF"
        private const val COLOR_ERROR = "#FF6B6B"
        private const val COLOR_COOLDOWN = "#F6C665"
        private const val COLOR_CLICK = "#7EE0B5"
        private const val COLOR_TEMPLATE = "#7CC7FF"
        private const val COLOR_MARKER = "#F6D28D"
    }

    private val items = mutableListOf<RunLogSection>()

    fun submitList(newItems: List<RunLogSection>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_log_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.titleView.text = item.title
        holder.subtitleView.text = item.subtitle
        holder.toggleView.text = if (item.expanded) {
            "\u6536\u8d77"
        } else {
            "\u5c55\u5f00"
        }

        if (item.expanded) {
            holder.logsView.visibility = View.VISIBLE
            holder.logsView.text = buildStyledContent(item.content)
        } else {
            holder.logsView.text = ""
            holder.logsView.visibility = View.GONE
        }

        holder.copyView.setOnClickListener {
            onCopySection(item)
        }
        holder.toggleView.setOnClickListener {
            toggle(position)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun toggle(position: Int) {
        items[position].expanded = !items[position].expanded
        notifyItemChanged(position)
    }

    private fun buildStyledContent(content: String): CharSequence {
        val builder = SpannableStringBuilder()
        content.lineSequence().forEachIndexed { index, rawLine ->
            if (index > 0) builder.append('\n')
            val start = builder.length
            builder.append(rawLine)
            val end = builder.length
            val style = classifyLine(rawLine)
            builder.setSpan(
                ForegroundColorSpan(Color.parseColor(style.color)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (style.bold) {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun classifyLine(line: String): LineStyle {
        if (line.startsWith("\u3010") && line.endsWith("\u3011")) {
            return LineStyle(COLOR_MARKER, bold = true)
        }

        val match = LOG_LINE_REGEX.matchEntire(line)
        val level = match?.groupValues?.getOrNull(1).orEmpty()
        val message = match?.groupValues?.getOrNull(2) ?: line

        return when {
            level == "E" ||
                containsAny(
                    message,
                    "\u5931\u8d25",
                    "\u9519\u8bef",
                    "\u5d29\u6e83",
                    "\u5f02\u5e38",
                    "\u53d6\u6d88",
                    "\u7f3a\u5931"
                ) -> {
                LineStyle(COLOR_ERROR, bold = true)
            }

            containsAny(message, "\u51b7\u5374", "\u5df2\u8017\u5c3d") -> {
                LineStyle(COLOR_COOLDOWN, bold = true)
            }

            containsAny(message, "\u70b9\u51fb", "\u624b\u52bf") -> {
                LineStyle(COLOR_CLICK)
            }

            containsAny(
                message,
                "\u6a21\u677f",
                "\u5339\u914d",
                "\u8bc6\u522b",
                "\u68c0\u6d4b",
                "OCR"
            ) -> {
                LineStyle(COLOR_TEMPLATE)
            }

            else -> LineStyle(COLOR_NORMAL)
        }
    }

    private fun containsAny(message: String, vararg keywords: String): Boolean {
        return keywords.any { message.contains(it, ignoreCase = false) }
    }

    private data class LineStyle(
        val color: String,
        val bold: Boolean = false
    )

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.tv_section_title)
        val subtitleView: TextView = itemView.findViewById(R.id.tv_section_subtitle)
        val copyView: TextView = itemView.findViewById(R.id.btn_copy_section)
        val toggleView: TextView = itemView.findViewById(R.id.btn_toggle_section)
        val logsView: TextView = itemView.findViewById(R.id.tv_section_logs)
    }
}
