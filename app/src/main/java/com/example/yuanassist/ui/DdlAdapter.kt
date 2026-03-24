package com.example.yuanassist.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.ddl_list
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

class DdlAdapter(private var list: List<ddl_list>) : RecyclerView.Adapter<DdlAdapter.ViewHolder>() {

    fun updateData(newList: List<ddl_list>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ddl, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        // 1. 设置活动名称
        holder.tvName.text = item.eventName

        // 2. 解析时间并计算剩余天数
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val endDate = sdf.parse(item.endTime)

            if (endDate != null) {
                val diffMillis = endDate.time - System.currentTimeMillis()
                // 将毫秒转换为天数，不足1天按0算（或者你可以除以天数向上取整），如果已过期则为0
                val diffDays = max(0, diffMillis / (1000 * 60 * 60 * 24))

                holder.tvDays.text = diffDays.toString()

                // 如果过期了，可以让字体变灰以作区分 (可选)
                if (diffDays <= 0) {
                    holder.tvDays.text = "0"
                    holder.tvDays.setTextColor(Color.parseColor("#888888"))
                    holder.tvName.setTextColor(Color.parseColor("#888888"))
                } else {
                    holder.tvDays.setTextColor(Color.parseColor("#FF6D00"))
                    holder.tvName.setTextColor(Color.parseColor("#FF6D00"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            holder.tvDays.text = "?" // 解析失败显示问号
        }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_ddl_name)
        val tvDays: TextView = view.findViewById(R.id.tv_ddl_days)
    }
}