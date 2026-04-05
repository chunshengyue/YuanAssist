package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import com.example.yuanassist.R
import com.example.yuanassist.model.STRATEGY_VISIBLE_PUBLIC
import com.example.yuanassist.model.strategy_detail

class JobStationListActivity : AppCompatActivity() {

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private var allItems: List<JobStationAssetRepository.JobStationListItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station_list)
        applyStatusBarInsets()

        findViewById<ImageView>(R.id.btn_job_station_list_back).setOnClickListener {
            finish()
        }

        emptyView = findViewById(R.id.tv_job_station_list_empty)
        recyclerView = findViewById(R.id.rv_job_station_list)
        searchInput = findViewById(R.id.et_job_station_search_keyword)

        recyclerView.layoutManager = LinearLayoutManager(this)
        findViewById<Button>(R.id.btn_job_station_search).setOnClickListener {
            applySearch()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            applySearch()
            true
        }

        loadMixedStrategies()
    }

    private fun loadMixedStrategies() {
        showEmpty("正在加载攻略...")
        JobStationRemoteRepository.loadList(
            page = 1,
            onSuccess = { _, maaItems ->
                loadBmobStrategies(maaItems)
            },
            onError = { message ->
                runOnUiThread {
                    allItems = emptyList()
                    showEmpty(message)
                }
            }
        )
    }

    private fun loadBmobStrategies(
        maaItems: List<JobStationAssetRepository.JobStationListItem>
    ) {
        val query = BmobQuery<strategy_detail>()
        query.addWhereEqualTo("visible", STRATEGY_VISIBLE_PUBLIC)
        query.order("-createdAt")
        query.setLimit(200)
        query.include("author")
        query.findObjects(object : FindListener<strategy_detail>() {
            override fun done(list: MutableList<strategy_detail>?, e: BmobException?) {
                runOnUiThread {
                    if (e != null) {
                        Toast.makeText(
                            this@JobStationListActivity,
                            "社区攻略加载失败: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    allItems = mergeStrategies(
                        maaItems = maaItems,
                        bmobItems = list.orEmpty()
                    )

                    if (allItems.isEmpty()) {
                        showEmpty("暂无可展示的攻略")
                    } else {
                        showItems(allItems)
                    }
                }
            }
        })
    }

    private fun mergeStrategies(
        maaItems: List<JobStationAssetRepository.JobStationListItem>,
        bmobItems: List<strategy_detail>
    ): List<JobStationAssetRepository.JobStationListItem> {
        val bmobMappedItems = bmobItems.map(JobStationAssetRepository::fromBmobListItem)
        val maaTimestamps = maaItems.map { it.publishTimestamp }.filter { it > 0L }

        val merged = if (maaTimestamps.isEmpty()) {
            maaItems + bmobMappedItems
        } else {
            val newestTime = maaTimestamps.maxOrNull() ?: 0L
            val oldestTime = maaTimestamps.minOrNull() ?: 0L
            val rangedBmobItems = bmobMappedItems.filter { item ->
                item.publishTimestamp in oldestTime..newestTime
            }
            maaItems + rangedBmobItems
        }

        return merged.sortedByDescending { it.publishTimestamp }
    }

    private fun applySearch() {
        val keyword = searchInput.text.toString().trim()
        if (keyword.isEmpty()) {
            showItems(allItems)
            return
        }

        val filteredItems = allItems.filter { item ->
            item.title.contains(keyword, ignoreCase = true) ||
                item.author.contains(keyword, ignoreCase = true) ||
                item.tags.any { it.contains(keyword, ignoreCase = true) } ||
                item.roster.any { it.contains(keyword, ignoreCase = true) } ||
                item.agentsText.contains(keyword, ignoreCase = true)
        }

        if (filteredItems.isEmpty()) {
            showEmpty("没有匹配的攻略，换个关键词试试")
            return
        }

        showItems(filteredItems)
    }

    private fun showItems(items: List<JobStationAssetRepository.JobStationListItem>) {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.adapter = JobStationListAdapter(
            items = items,
            showLoadMore = false,
            isLoadingMore = false,
            onClick = { item ->
                when (item.type) {
                    JobStationAssetRepository.JobStationListItemType.BMOB -> {
                        item.strategyId?.let { strategyId ->
                            startActivity(Intent(this, JobStationActivity::class.java).apply {
                                putExtra(JobStationActivity.EXTRA_STRATEGY_ID, strategyId)
                            })
                        }
                    }

                    JobStationAssetRepository.JobStationListItemType.MAA -> {
                        item.copilotId?.let { copilotId ->
                            startActivity(Intent(this, JobStationActivity::class.java).apply {
                                putExtra(JobStationActivity.EXTRA_COPILOT_ID, copilotId)
                            })
                        }
                    }
                }
            }
        )
    }

    private fun showEmpty(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun applyStatusBarInsets() {
        val statusBarSpacer = findViewById<View>(R.id.view_job_station_list_status_bar_spacer)

        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.layoutParams = view.layoutParams.apply {
                height = statusBars.top
            }
            insets
        }
        ViewCompat.requestApplyInsets(statusBarSpacer)
    }
}
