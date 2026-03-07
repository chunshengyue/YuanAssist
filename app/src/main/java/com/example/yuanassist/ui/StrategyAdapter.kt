package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.yuanassist.R
import com.example.yuanassist.model.StrategyItem

class StrategyAdapter(
    private var list: List<StrategyItem>,
    private val onClick: (StrategyItem) -> Unit
) : RecyclerView.Adapter<StrategyAdapter.ViewHolder>() {

    fun updateData(newList: List<StrategyItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_strategy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.title.text = item.title

        // 🔴 智能排版：分別填入作者和陣容
        val rawAgentsText = item.agents
        if (rawAgentsText.contains("-")) {
            val parts = rawAgentsText.split("-", limit = 2)
            val authorPart = parts[0].trim() // 取出 "by夜瀾遙"
            val lineupPart = parts[1].trim() // 取出 "諸葛亮、蒯越..."

            // 顯示作者
            holder.author.text = authorPart
            holder.author.visibility = View.VISIBLE

            // 顯示陣容，並把頓號替換成 3 個空格
            holder.agents.text = lineupPart.replace("、", "   ")
        } else {
            // 如果沒有橫線（容錯處理），隱藏作者行，全部顯示在陣容行
            holder.author.visibility = View.GONE
            holder.agents.text = rawAgentsText.replace("、", "   ")
        }

        // 使用 Glide 加載圖片
        Glide.with(holder.cover.context)
            .load(item.coverUrl)
            .transform(CenterCrop(), RoundedCorners(16))
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(holder.cover)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val author: TextView = view.findViewById(R.id.tv_author) // 🔴 新增作者的綁定
        val agents: TextView = view.findViewById(R.id.tv_agents)
        val cover: ImageView = view.findViewById(R.id.iv_cover)
    }
}