package com.example.yuanassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        private val titleView: TextView = itemView.findViewById(R.id.tv_job_station_item_title)
        private val summaryView: TextView = itemView.findViewById(R.id.tv_job_station_item_summary)
        private val stageView: TextView = itemView.findViewById(R.id.tv_job_station_item_stage)
        private var currentItem: JobStationAssetRepository.JobStationListItem? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let(onClick)
            }
        }

        fun bind(item: JobStationAssetRepository.JobStationListItem) {
            currentItem = item
            titleView.text = item.title
            summaryView.text = item.summary
            stageView.text = item.stage
        }
    }
}
