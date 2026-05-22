package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.yuanassist.R
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.network.SupabaseRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CloudDailyScriptListActivity : AppCompatActivity() {
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var chipNewest: TextView
    private lateinit var chipHot: TextView
    private lateinit var listAdapter: CloudDailyScriptListAdapter
    private var sortMode = "newest"
    private var items: List<cloud_daily_script> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_daily_script_list)
        applyStatusBarInsets()

        findViewById<ImageView>(R.id.btn_cloud_daily_script_list_back).setOnClickListener { finish() }
        findViewById<FloatingActionButton>(R.id.fab_upload_cloud_daily_script).setOnClickListener {
            startActivity(Intent(this, UploadCloudDailyScriptActivity::class.java))
        }

        emptyView = findViewById(R.id.tv_cloud_daily_script_empty)
        recyclerView = findViewById(R.id.rv_cloud_daily_script_list)
        swipeRefreshLayout = findViewById(R.id.swipe_cloud_daily_script_list)
        searchInput = findViewById(R.id.et_cloud_daily_script_search_keyword)
        chipNewest = findViewById(R.id.chip_cloud_sort_newest)
        chipHot = findViewById(R.id.chip_cloud_sort_hot)

        recyclerView.layoutManager = LinearLayoutManager(this)
        listAdapter = CloudDailyScriptListAdapter(emptyList()) { item ->
            startActivity(Intent(this, CloudDailyScriptActivity::class.java).apply {
                putExtra(CloudDailyScriptActivity.EXTRA_SCRIPT_ID, item.objectId.orEmpty())
            })
        }
        recyclerView.adapter = listAdapter
        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#C88A2C"), Color.parseColor("#8F6A2B"))
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#FFF8EB"))
        swipeRefreshLayout.setOnRefreshListener { loadList() }

        findViewById<Button>(R.id.btn_cloud_daily_script_search).setOnClickListener { loadList() }
        searchInput.setOnEditorActionListener { _, _, _ ->
            loadList()
            true
        }
        chipNewest.setOnClickListener {
            sortMode = "newest"
            updateChipUi()
            loadList()
        }
        chipHot.setOnClickListener {
            sortMode = "hot"
            updateChipUi()
            loadList()
        }
        updateChipUi()
        loadList()
    }

    override fun onResume() {
        super.onResume()
        if (items.isNotEmpty()) loadList()
    }

    private fun loadList() {
        swipeRefreshLayout.isRefreshing = true
        SupabaseRepository.listDailyScripts(
            sortMode = sortMode,
            keyword = searchInput.text?.toString().orEmpty(),
            onSuccess = { loaded ->
                items = loaded
                listAdapter.submitList(loaded)
                emptyView.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
                swipeRefreshLayout.isRefreshing = false
            },
            onError = { message ->
                Toast.makeText(this, "加载失败：$message", Toast.LENGTH_LONG).show()
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                swipeRefreshLayout.isRefreshing = false
            },
        )
    }

    private fun updateChipUi() {
        bindChip(chipNewest, sortMode == "newest")
        bindChip(chipHot, sortMode == "hot")
    }

    private fun bindChip(view: TextView, selected: Boolean) {
        view.background = GradientDrawable().apply {
            cornerRadius = 16.dp().toFloat()
            setStroke(1.dp(), Color.parseColor(if (selected) "#C69244" else "#D8C8AC"))
            setColor(Color.parseColor(if (selected) "#F3E1B8" else "#FFFBF4"))
        }
        view.setTextColor(Color.parseColor(if (selected) "#6B4E1C" else "#8A7861"))
    }

    private fun applyStatusBarInsets() {
        val spacer = findViewById<View>(R.id.view_cloud_daily_script_list_status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams = view.layoutParams.apply { height = top }
            insets
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
