package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R

class JobStationListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station_list)

        findViewById<ImageView>(R.id.btn_job_station_list_back).setOnClickListener {
            finish()
        }

        val items = JobStationAssetRepository.loadList(this)
        val emptyView = findViewById<TextView>(R.id.tv_job_station_list_empty)
        val recyclerView = findViewById<RecyclerView>(R.id.rv_job_station_list)

        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = JobStationListAdapter(items) { item ->
            startActivity(Intent(this, JobStationActivity::class.java).apply {
                putExtra(JobStationActivity.EXTRA_ASSET_FILE_NAME, item.assetFileName)
            })
        }
    }
}
