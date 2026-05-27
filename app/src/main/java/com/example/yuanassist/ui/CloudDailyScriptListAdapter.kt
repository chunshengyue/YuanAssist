package com.example.yuanassist.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.Coil
import coil.request.ImageRequest
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
        private val authorAvatarView: ImageView = itemView.findViewById(R.id.iv_cloud_item_author_avatar)
        private val authorView: TextView = itemView.findViewById(R.id.tv_cloud_item_author)
        private val metaView: TextView = itemView.findViewById(R.id.tv_cloud_item_meta)
        private val tagsContainer: LinearLayout = itemView.findViewById(R.id.layout_cloud_item_tags)

        fun bind(item: cloud_daily_script) {
            titleView.text = item.title.ifBlank { "未命名脚本" }
            descriptionView.text = item.description.ifBlank { "暂无说明" }
            authorView.text = item.author?.nickname?.takeIf { it.isNotBlank() }
                ?: item.author?.username?.takeIf { it.isNotBlank() }
                ?: "匿名用户"
            metaView.text = "${item.taskCount}步 · ${item.downloadCount}次下载"
            bindAvatar(item.author?.avatarUrl.orEmpty())
            val tags = parseTags(item.tags).ifEmpty { listOf("日常脚本") }
            bindTags(
                buildList {
                    if (item.overrideAssetScript.isNotBlank()) add("官方修正")
                    if (item.isAdminPublished) add("管理员发布")
                    addAll(tags)
                }
            )
            itemView.setOnClickListener { onClick(item) }
        }

        private fun bindAvatar(avatarUrl: String) {
            if (avatarUrl.isBlank()) {
                authorAvatarView.setImageResource(R.drawable.cover)
                return
            }
            Coil.imageLoader(itemView.context).enqueue(
                ImageRequest.Builder(itemView.context)
                    .data(avatarUrl)
                    .placeholder(R.drawable.cover)
                    .error(R.drawable.cover)
                    .target(authorAvatarView)
                    .build(),
            )
        }

        private fun bindTags(tags: List<String>) {
            tagsContainer.removeAllViews()
            tags.forEach { tag ->
                tagsContainer.addView(createTagView(tag))
            }
        }

        private fun createTagView(tag: String): TextView {
            val (backgroundColor, strokeColor, textColor) = when {
                tag == "官方修正" -> Triple("#F7D9D4", "#D46F63", "#8E3028")
                tag == "管理员发布" -> Triple("#F8E0B8", "#C88A2C", "#8F5A11")
                tag.contains("如鸢") -> Triple("#F8E0B8", "#C88A2C", "#8F5A11")
                tag.contains("代号鸢") -> Triple("#E2E7DA", "#9AA98B", "#5D6B51")
                tag.contains("MaaYuanShare", ignoreCase = true) -> Triple("#E8F3FF", "#5B8FD6", "#215A9A")
                else -> Triple("#F8F2E5", "#D8C18A", "#7B5B17")
            }
            return TextView(itemView.context).apply {
                text = tag
                textSize = 11f
                setTextColor(Color.parseColor(textColor))
                setPadding(7.dp(), 2.dp(), 7.dp(), 2.dp())
                maxLines = 1
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10.dp().toFloat()
                    setColor(Color.parseColor(backgroundColor))
                    setStroke(1.dp(), Color.parseColor(strokeColor))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginEnd = 5.dp()
                }
            }
        }

        private fun parseTags(raw: String): List<String> {
            return raw.split(Regex("[,，\\s]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        private fun Int.dp(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }
}
