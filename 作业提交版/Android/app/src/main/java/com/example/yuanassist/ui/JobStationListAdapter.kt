package com.example.yuanassist.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.signature.ObjectKey
import com.example.yuanassist.R
import java.security.MessageDigest

class JobStationListAdapter(
    items: List<JobStationAssetRepository.JobStationListItem>,
    showLoadMore: Boolean,
    isLoadingMore: Boolean,
    private val onClick: (JobStationAssetRepository.JobStationListItem) -> Unit,
    private val onLoadMore: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MAA = 1
        private const val VIEW_TYPE_COMMUNITY = 2
        private const val VIEW_TYPE_LOAD_MORE = 3
    }

    private var items: List<JobStationAssetRepository.JobStationListItem> = items
    private var showLoadMore: Boolean = showLoadMore
    private var isLoadingMore: Boolean = isLoadingMore

    fun updateData(
        items: List<JobStationAssetRepository.JobStationListItem>,
        showLoadMore: Boolean,
        isLoadingMore: Boolean
    ) {
        this.items = items
        this.showLoadMore = showLoadMore
        this.isLoadingMore = isLoadingMore
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        if (showLoadMore && position == items.size) {
            return VIEW_TYPE_LOAD_MORE
        }
        return when (items[position].type) {
            JobStationAssetRepository.JobStationListItemType.COMMUNITY -> VIEW_TYPE_COMMUNITY
            JobStationAssetRepository.JobStationListItemType.MAA -> VIEW_TYPE_MAA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMMUNITY -> CommunityViewHolder(
                inflater.inflate(R.layout.item_job_station_strategy_entry, parent, false),
                onClick
            )

            VIEW_TYPE_LOAD_MORE -> LoadMoreViewHolder(
                inflater.inflate(R.layout.item_job_station_load_more, parent, false),
                onLoadMore
            )

            else -> MaaViewHolder(
                inflater.inflate(R.layout.item_job_station_entry, parent, false),
                onClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MaaViewHolder -> holder.bind(items[position])
            is CommunityViewHolder -> holder.bind(items[position])
            is LoadMoreViewHolder -> holder.bind(isLoadingMore)
        }
    }

    override fun getItemCount(): Int = items.size + if (showLoadMore) 1 else 0

    private abstract class BaseViewHolder(
        itemView: View,
        private val onClick: (JobStationAssetRepository.JobStationListItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        protected var currentItem: JobStationAssetRepository.JobStationListItem? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let(onClick)
            }
        }

        protected fun bindTags(
            container: LinearLayout,
            tags: List<String>
        ) {
            container.removeAllViews()
            val context = itemView.context

            tags.forEach { tagText ->
                if (tagText.isBlank()) return@forEach
                val isRuyuanTag = tagText == "如鸢"
                val isDaihaoTag = tagText == "代号鸢"
                val isMaaTag = tagText == "MaaYuanShare"
                val (bgColor, strokeColor, textColor) = when {
                    isRuyuanTag -> Triple("#F8E0B8", "#C88A2C", "#8F5A11")
                    isDaihaoTag -> Triple("#E2E7DA", "#9AA98B", "#5D6B51")
                    isMaaTag -> Triple("#E8F3FF", "#5B8FD6", "#215A9A")
                    else -> Triple("#F8F2E5", "#D8C18A", "#7B5B17")
                }

                val textView = TextView(context).apply {
                    text = tagText
                    textSize = 11f
                    setTextColor(Color.parseColor(textColor))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor(bgColor))
                        setStroke(dpToPx(1f), Color.parseColor(strokeColor))
                        cornerRadius = dpToPx(999f).toFloat()
                    }
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(7f), dpToPx(2f), dpToPx(7f), dpToPx(2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(5f)
                    }
                }
                container.addView(textView)
            }
        }

        protected fun loadAvatarFromAssets(context: Context, name: String): Drawable? {
            return runCatching {
                context.assets.open("$name.png").use { stream ->
                    Drawable.createFromStream(stream, null)
                }
            }.getOrNull() ?: runCatching {
                context.assets.open("$name.jpg").use { stream ->
                    Drawable.createFromStream(stream, null)
                }
            }.getOrNull()
        }

        protected fun dpToPx(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                itemView.resources.displayMetrics
            ).toInt()
        }
    }

    private class MaaViewHolder(
        itemView: View,
        onClick: (JobStationAssetRepository.JobStationListItem) -> Unit
    ) : BaseViewHolder(itemView, onClick) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_title)
        private val titleAuthorView: TextView = itemView.findViewById(R.id.tv_title_author)
        private val tagsContainer: LinearLayout = itemView.findViewById(R.id.ll_tags_container)
        private val rosterContainer: LinearLayout = itemView.findViewById(R.id.ll_roster_container)
        private val timeView: TextView = itemView.findViewById(R.id.tv_time)

        fun bind(item: JobStationAssetRepository.JobStationListItem) {
            currentItem = item
            titleView.text = item.title
            titleAuthorView.text = item.author
            timeView.text = item.publishTime
            bindTags(tagsContainer, item.tags)
            bindRoster(item.roster)
        }

        private fun bindRoster(roster: List<String>) {
            rosterContainer.removeAllViews()
            val context = itemView.context
            val avatarSize = dpToPx(38f)
            val avatarMargin = dpToPx(6f)

            roster.filter { it.isNotBlank() }.take(5).forEach { opName ->
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                        marginEnd = avatarMargin
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_job_station_avatar)
                    clipToOutline = true
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                }

                val avatarDrawable = loadAvatarFromAssets(context, opName)
                if (avatarDrawable != null) {
                    imageView.setImageDrawable(avatarDrawable)
                } else {
                    imageView.setImageDrawable(null)
                }

                rosterContainer.addView(imageView)
            }
        }
    }

    private class CommunityViewHolder(
        itemView: View,
        onClick: (JobStationAssetRepository.JobStationListItem) -> Unit
    ) : BaseViewHolder(itemView, onClick) {
        private val coverView: ImageView = itemView.findViewById(R.id.iv_cover)
        private val titleView: TextView = itemView.findViewById(R.id.tv_title)
        private val authorAvatarView: ImageView = itemView.findViewById(R.id.iv_author_avatar)
        private val authorView: TextView = itemView.findViewById(R.id.tv_author)
        private val timeView: TextView = itemView.findViewById(R.id.tv_time)
        private val agentsView: TextView = itemView.findViewById(R.id.tv_agents)
        private val tagsContainer: LinearLayout = itemView.findViewById(R.id.ll_tags_container)

        fun bind(item: JobStationAssetRepository.JobStationListItem) {
            currentItem = item
            titleView.text = item.title
            authorView.text = item.author
            timeView.text = item.publishTime
            agentsView.text = item.agentsText.ifBlank { "未配置阵容" }
            bindTags(tagsContainer, item.tags)

            val displayCoverUrl = item.agentImageUrl.ifBlank { item.coverUrl }
            if (displayCoverUrl.isNotBlank()) {
                Glide.with(coverView.context)
                    .load(displayCoverUrl)
                    .signature(ObjectKey(JobStationAssetRepository.IMAGE_CACHE_SIGNATURE))
                    .transform(CommunityCoverCrop())
                    .placeholder(R.drawable.cover)
                    .error(R.drawable.cover)
                    .into(coverView)
            } else {
                coverView.setImageResource(R.drawable.cover)
            }

            if (item.authorAvatarUrl.isNotBlank()) {
                Glide.with(authorAvatarView.context)
                    .load(item.authorAvatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.cover)
                    .error(R.drawable.cover)
                    .into(authorAvatarView)
            } else {
                authorAvatarView.setImageResource(R.drawable.cover)
            }
        }
    }

    private class LoadMoreViewHolder(
        itemView: View,
        onLoadMore: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val button: Button = itemView.findViewById(R.id.btn_load_more)

        init {
            button.setOnClickListener { onLoadMore() }
        }

        fun bind(isLoading: Boolean) {
            button.text = if (isLoading) "加载中..." else "加载更多"
            button.isEnabled = !isLoading
        }
    }

    private class CommunityCoverCrop : BitmapTransformation() {
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
                // 封面固定从左上角开始裁切：
                // 常规竖图裁掉底部，极宽横图裁掉右侧。
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
            messageDigest.update("JobStationCommunityCoverCrop".toByteArray())
        }
    }
}

