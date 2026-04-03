package com.example.yuanassist.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R

class JobStationListAdapter(
    private val items: List<JobStationAssetRepository.JobStationListItem>,
    private val onClick: (JobStationAssetRepository.JobStationListItem) -> Unit
) : RecyclerView.Adapter<JobStationListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job_station_entry, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        itemView: View,
        private val onClick: (JobStationAssetRepository.JobStationListItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tv_title)
        private val titleAuthorView: TextView = itemView.findViewById(R.id.tv_title_author)
        private val tagsContainer: LinearLayout = itemView.findViewById(R.id.ll_tags_container)
        private val rosterContainer: LinearLayout = itemView.findViewById(R.id.ll_roster_container)
        private val timeView: TextView = itemView.findViewById(R.id.tv_time)
        private var currentItem: JobStationAssetRepository.JobStationListItem? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let(onClick)
            }
        }

        fun bind(item: JobStationAssetRepository.JobStationListItem) {
            currentItem = item
            titleView.text = item.title
            titleAuthorView.text = item.author
            timeView.text = item.publishTime
            bindTags(item.tags)
            bindRoster(item.roster)
        }

        private fun bindTags(tags: List<String>) {
            tagsContainer.removeAllViews()
            val context = itemView.context

            tags.forEach { tagText ->
                if (tagText.isBlank()) return@forEach
                val isRuyuanTag = tagText == "如鸢"
                val isDaihaoTag = tagText == "代号鸢"
                val (bgColor, strokeColor, textColor) = when {
                    isRuyuanTag -> Triple("#F8E0B8", "#C88A2C", "#8F5A11")
                    isDaihaoTag -> Triple("#E2E7DA", "#9AA98B", "#5D6B51")
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

                    val horizontalPadding = dpToPx(7f)
                    val verticalPadding = dpToPx(2f)
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(5f)
                    }
                }
                tagsContainer.addView(textView)
            }
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

        private fun loadAvatarFromAssets(context: Context, name: String): Drawable? {
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

        private fun dpToPx(dp: Float): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                itemView.resources.displayMetrics
            ).toInt()
        }
    }
}
