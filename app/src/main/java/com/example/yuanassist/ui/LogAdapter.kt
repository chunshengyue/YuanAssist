package com.example.yuanassist.ui

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.TurnData

class LogAdapter(
    private var dataList: List<TurnData>,
    private val onItemClick: (turnIndex: Int, colIndex: Int) -> Unit
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    fun updateData(newData: List<TurnData>) {
        this.dataList = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]

        // 1. 设置回合数 (纯数字，用默认蓝色)
        holder.tvIndex.text = item.turnNumber.toString()

        // 2. 设置列内容 (应用颜色逻辑)
        for (i in 0 until 5) {
            // 🔴 核心：调用变色函数处理文字
            holder.tvCols[i].text = colorizeText(item.characterActions[i])

            holder.tvCols[i].setOnClickListener { onItemClick(position, i) }
        }

        // 3. 高亮行逻辑 (保持不变)
        val backgroundColor: Int = when {
            item.hasConflict -> Color.parseColor("#FFCDD2")
            item.isExecuting -> Color.parseColor("#FFF0F5")
            else -> Color.WHITE
        }
        holder.tvIndex.setBackgroundColor(backgroundColor)
        for (tv in holder.tvCols) {
            tv.setBackgroundColor(backgroundColor)
        }
    }

    override fun getItemCount(): Int = dataList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIndex: TextView = itemView.findViewById(R.id.tv_round_index)
        val tvCols = arrayOf<TextView>(
            itemView.findViewById(R.id.tv_col_1),
            itemView.findViewById(R.id.tv_col_2),
            itemView.findViewById(R.id.tv_col_3),
            itemView.findViewById(R.id.tv_col_4),
            itemView.findViewById(R.id.tv_col_5)
        )
    }

    // ==========================================
    // 🎨 核心变色逻辑函数
    // ==========================================
    private fun colorizeText(text: CharSequence): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        val str = text.toString()

        for (i in str.indices) {
            val char = str[i]
            val color = when (char) {
                // 数字 -> 景泰蓝 (默认文字色)
                in '0'..'9' -> Color.parseColor("#4A6F8A")

                // A 和 圈 -> 胭脂红
                'A', 'a', '圈' -> Color.parseColor("#D68A93")

                // ↑ -> 绿色 (代表增益/向上)
                '↑' -> Color.parseColor("#FF6D00")

                // ↓ -> 蓝色 (代表减益/向下，区别于数字的深蓝，这里选个亮一点的蓝或者紫色)
                '↓' -> Color.parseColor("#00C853")

                // 其他字符 -> 默认灰黑
                else -> Color.DKGRAY
            }

            // 应用颜色
            sb.setSpan(
                ForegroundColorSpan(color),
                i, i + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return sb
    }
}