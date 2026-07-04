package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.yuanassist.R

class StrategyDetailImagesAdapter(
    private val items: List<DetailItem>
) : RecyclerView.Adapter<StrategyDetailImagesAdapter.ImageViewHolder>() {

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.iv_detail_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return

        // 使用 Glide 加载图片，加入缓存策略让滑动更丝滑
        Glide.with(holder.itemView.context)
            .load(item.content)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_launcher_background) // 如果加载失败显示的兜底图
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = items.size
}