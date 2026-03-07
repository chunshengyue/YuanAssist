package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yuanassist.R

// 🔴 重点检查这里：泛型必须是 StrategyDetailAdapter.BaseViewHolder
class StrategyDetailAdapter(
    private var items: List<DetailItem>
) : RecyclerView.Adapter<StrategyDetailAdapter.BaseViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_TEXT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items.getOrNull(position)?.type) {
            "image" -> TYPE_IMAGE
            else -> TYPE_TEXT
        }
    }

    // 🔴 这里的返回值必须是 BaseViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> ImageViewHolder(
                inflater.inflate(
                    R.layout.item_detail_image,
                    parent,
                    false
                )
            )

            else -> TextViewHolder(inflater.inflate(R.layout.item_detail_text, parent, false))
        }
    }

    // 🔴 这里的 holder 参数必须是 BaseViewHolder
    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bind(item.content)
    }

    override fun getItemCount(): Int = items.size

    // ==========================================
    // 基类和子类定义
    // ==========================================
    abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(content: String)
    }

    class ImageViewHolder(view: View) : BaseViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.iv_detail_image)
        override fun bind(content: String) {
            Glide.with(itemView.context)
                .load(content)
                .into(imageView)
        }
    }

    class TextViewHolder(view: View) : BaseViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.tv_detail_text)
        override fun bind(content: String) {
            textView.text = content
        }
    }
}