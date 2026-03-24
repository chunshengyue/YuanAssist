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
import com.example.yuanassist.model.strategy_detail
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.graphics.Bitmap
import android.graphics.Matrix
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
class StrategyAdapter(
    private var list: List<strategy_detail>,
    private val onClick: (strategy_detail) -> Unit
) : RecyclerView.Adapter<StrategyAdapter.ViewHolder>() {

    fun updateData(newList: List<strategy_detail>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strategy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.title.text = item.title

        // 🔴 1. 绑定作者名字和头像
        val authorName = item.author?.nickname ?: "热心玩家"
        holder.author.text = "$authorName"
        holder.author.visibility = View.VISIBLE

        val avatarUrl = item.author?.avatarUrl
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(avatarUrl)
                .centerCrop()
                .placeholder(R.drawable.cover)
                .error(R.drawable.cover)
                .into(holder.authorAvatar)
        } else {
            holder.authorAvatar.setImageResource(R.drawable.cover)
        }

        // 🔴 2. 绑定阵容 (解析 agentSelection)
        val rawAgentsText = if (!item.agentSelection.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val agentsRawList: List<String> = Gson().fromJson(item.agentSelection, type)
                agentsRawList.joinToString("、")
            } catch (e: Exception) {
                item.agentSelection ?: ""
            }
        } else {
            item.agents ?: ""
        }

        // 核心正则：无论是 "5诸葛亮-1、5" 还是 "诸葛亮-1、2、5" 还是 "士燮"，统统剥离只剩名字
        val parsedNames = rawAgentsText.split("、", "，", ",").mapNotNull { raw ->
            val trimRaw = raw.trim()
            if (trimRaw.isBlank()) return@mapNotNull null
            // 剔除前缀数字(星级)，再剔除后缀(命盘)，只保留纯名字
            trimRaw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
        }

        holder.agents.text = parsedNames.joinToString(" ").ifEmpty { "未配置阵容" }
        // 🔴 3. 封面图绑定
        val displayUrl = if (!item.agentImageUrl.isNullOrEmpty()) {
            item.agentImageUrl // 优先使用密探截图
        } else {
            item.coverUrl
        }

        if (!displayUrl.isNullOrEmpty()) {
            Glide.with(holder.cover.context)
                .load(displayUrl)
                .transform(TopLeftCrop(), RoundedCorners(16))
                .placeholder(R.drawable.cover)
                .error(R.drawable.cover)
                .into(holder.cover)
        } else {
            holder.cover.setImageResource(R.drawable.cover)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val author: TextView = view.findViewById(R.id.tv_author)
        val authorAvatar: ImageView = view.findViewById(R.id.iv_author_avatar) // 🔴 绑定新增的头像
        val agents: TextView = view.findViewById(R.id.tv_agents)
        val cover: ImageView = view.findViewById(R.id.iv_cover)
    }
    class TopLeftCrop : BitmapTransformation() {
        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            if (toTransform.width == outWidth && toTransform.height == outHeight) return toTransform

            // 计算缩放比例，取宽和高中最大的缩放比，保证图片能填满 ImageView
            val scale = maxOf(outWidth.toFloat() / toTransform.width, outHeight.toFloat() / toTransform.height)

            val matrix = Matrix()
            matrix.setScale(scale, scale)
            // 从左上角开始画，不需要做 translate 偏移
            matrix.postTranslate(0f, 0f)

            val result = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            val paint = android.graphics.Paint(android.graphics.Paint.DITHER_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(toTransform, matrix, paint)
            return result
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("TopLeftCrop".toByteArray())
        }
    }
}