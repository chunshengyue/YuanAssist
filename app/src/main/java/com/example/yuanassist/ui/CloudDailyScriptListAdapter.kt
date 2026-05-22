package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.cloud_daily_script

class CloudDailyScriptListAdapter(
    private var items: List<cloud_daily_script>,
    private val onClick: (cloud_daily_script) -> Unit,
) : RecyclerView.Adapter<CloudDailyScriptListAdapter.ViewHolder>() {

    fun submitList(nextItems: List<cloud_daily_script>) {
        items = nextItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cloud_daily_script, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onClick: (cloud_daily_script) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_cloud_item_title)
        private val descriptionView: TextView = itemView.findViewById(R.id.tv_cloud_item_description)
        private val authorView: TextView = itemView.findViewById(R.id.tv_cloud_item_author)
        private val metaView: TextView = itemView.findViewById(R.id.tv_cloud_item_meta)
        private val tagsView: TextView = itemView.findViewById(R.id.tv_cloud_item_tags)

        fun bind(item: cloud_daily_script) {
            titleView.text = item.title.ifBlank { "未命名脚本" }
            descriptionView.text = item.description.ifBlank { "暂无说明" }
            authorView.text = item.author?.nickname?.takeIf { it.isNotBlank() }
                ?: item.author?.username?.takeIf { it.isNotBlank() }
                ?: "匿名用户"
            metaView.text = "${item.taskCount}步 · ${item.downloadCount}次下载"
            tagsView.text = item.tags.trim().ifBlank { "日常脚本" }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
