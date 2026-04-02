package com.example.yuanassist.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.yuanassist.R
import com.example.yuanassist.model.strategy_detail
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

class MyPublishedAdapter(
    private var list: List<strategy_detail>,
    private val onCardClick: (strategy_detail) -> Unit,
    private val onEditClick: (strategy_detail) -> Unit,
    private val onDeleteClick: (strategy_detail) -> Unit
) : RecyclerView.Adapter<MyPublishedAdapter.ViewHolder>() {

    fun updateData(newList: List<strategy_detail>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_published_strategy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.title.text = item.title.ifBlank { "未命名攻略" }
        holder.updatedAt.text = buildUpdatedAtText(item.updatedAt)
        holder.agents.text = buildAgentsText(item)

        val displayUrl = if (!item.agentImageUrl.isNullOrEmpty()) {
            item.agentImageUrl
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

        holder.card.setOnClickListener { onCardClick(item) }
        holder.editButton.setOnClickListener { onEditClick(item) }
        holder.deleteButton.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = list.size

    private fun buildUpdatedAtText(updatedAt: String?): String {
        return if (updatedAt.isNullOrBlank()) {
            "最近更新：暂无"
        } else {
            "最近更新：$updatedAt"
        }
    }

    private fun buildAgentsText(item: strategy_detail): String {
        val rawAgentsText = if (!item.agentSelection.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val agentsRawList: List<String> = Gson().fromJson(item.agentSelection, type)
                agentsRawList.joinToString("、")
            } catch (_: Exception) {
                item.agentSelection ?: ""
            }
        } else {
            item.agents ?: ""
        }

        val parsedNames = rawAgentsText.split("、", "，", ",").mapNotNull { raw ->
            val trimRaw = raw.trim()
            if (trimRaw.isBlank()) return@mapNotNull null
            trimRaw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
        }

        return parsedNames.joinToString(" ").ifEmpty { "未配置阵容" }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.layout_my_published_card)
        val cover: ImageView = view.findViewById(R.id.iv_my_published_cover)
        val title: TextView = view.findViewById(R.id.tv_my_published_title)
        val updatedAt: TextView = view.findViewById(R.id.tv_my_published_updated_at)
        val agents: TextView = view.findViewById(R.id.tv_my_published_agents)
        val editButton: Button = view.findViewById(R.id.btn_my_published_edit)
        val deleteButton: Button = view.findViewById(R.id.btn_my_published_delete)
    }

    class TopLeftCrop : BitmapTransformation() {
        override fun transform(
            pool: BitmapPool,
            toTransform: Bitmap,
            outWidth: Int,
            outHeight: Int
        ): Bitmap {
            if (toTransform.width == outWidth && toTransform.height == outHeight) return toTransform

            val scale = maxOf(
                outWidth.toFloat() / toTransform.width,
                outHeight.toFloat() / toTransform.height
            )

            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(0f, 0f)
            }

            val result = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            val paint = android.graphics.Paint(
                android.graphics.Paint.DITHER_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
            )
            canvas.drawBitmap(toTransform, matrix, paint)
            return result
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("MyPublishedTopLeftCrop".toByteArray())
        }
    }
}
