package com.example.yuanassist.ui

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.yuanassist.R
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.ConfigManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call

class JobStationListActivity : AppCompatActivity() {

    private enum class SortMode(val maaOrderBy: String) {
        HOT("hot"),
        NEWEST("id")
    }

    enum class SourceMode {
        COMMUNITY,
        MAA
    }

    companion object {
        private val STAGE_OPTIONS = listOf("主线", "白鹄", "洞窟", "兰台", "地宫", "家具", "活动")
        private const val EXTRA_SOURCE_MODE = "extra_source_mode"

        private data class CachedMaaState(
            val loadedItems: List<JobStationAssetRepository.JobStationListItem>,
            val currentPage: Int,
            val hasNextPage: Boolean
        )

        private val maaSortResultCache = mutableMapOf<String, CachedMaaState>()

        fun createIntent(context: Context, sourceMode: SourceMode): Intent {
            return Intent(context, JobStationListActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_MODE, sourceMode.name)
            }
        }
    }

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var uploadButton: FloatingActionButton
    private lateinit var chipSortHot: TextView
    private lateinit var chipSortNewest: TextView
    private lateinit var chipGameRuyuan: TextView
    private lateinit var chipGameDaihao: TextView
    private lateinit var chipStageFilter: TextView
    private lateinit var chipDefaultFilter: TextView
    private lateinit var listAdapter: JobStationListAdapter
    private var allItems: List<JobStationAssetRepository.JobStationListItem> = emptyList()
    private var loadedStrategyDetails: List<strategy_detail> = emptyList()
    private var currentSortMode = SortMode.HOT
    private var selectedGameTag = ""
    private var selectedStageTag = ""
    private var currentMaaPage = 1
    private var hasNextMaaPage = false
    private var isLoadingMore = false
    private var stageDropdown: ListPopupWindow? = null
    private var currentListCall: Call<*>? = null
    private var currentCommentsCall: Call<*>? = null
    private var listRequestVersion = 0
    private var commentsRequestVersion = 0
    private lateinit var sourceMode: SourceMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station_list)
        applyStatusBarInsets()
        sourceMode = intent.getStringExtra(EXTRA_SOURCE_MODE)
            ?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() }
            ?: SourceMode.COMMUNITY

        findViewById<ImageView>(R.id.btn_job_station_list_back).setOnClickListener {
            finish()
        }

        emptyView = findViewById(R.id.tv_job_station_list_empty)
        recyclerView = findViewById(R.id.rv_job_station_list)
        swipeRefreshLayout = findViewById(R.id.swipe_job_station_list)
        searchInput = findViewById(R.id.et_job_station_search_keyword)
        uploadButton = findViewById(R.id.fab_upload_strategy)
        chipSortHot = findViewById(R.id.chip_sort_hot)
        chipSortNewest = findViewById(R.id.chip_sort_newest)
        chipGameRuyuan = findViewById(R.id.chip_game_ruyuan)
        chipGameDaihao = findViewById(R.id.chip_game_daihao)
        chipStageFilter = findViewById(R.id.chip_stage_filter)
        chipDefaultFilter = findViewById(R.id.chip_default_filter)
        uploadButton.setOnClickListener {
            startActivity(Intent(this, UploadStrategyActivity::class.java))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        listAdapter = JobStationListAdapter(
            items = emptyList(),
            showLoadMore = false,
            isLoadingMore = false,
            onClick = { item ->
                when (item.type) {
                    JobStationAssetRepository.JobStationListItemType.COMMUNITY -> {
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
            },
            onLoadMore = {
                if (sourceMode == SourceMode.MAA && !isLoadingMore && hasNextMaaPage) {
                    loadMaaPage(currentMaaPage + 1, append = true)
                }
            }
        )
        recyclerView.adapter = listAdapter
        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#C88A2C"),
            Color.parseColor("#8F6A2B")
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#FFF8EB"))
        swipeRefreshLayout.setOnRefreshListener {
            when (sourceMode) {
                SourceMode.MAA -> reloadMaa(fromPullRefresh = true)
                SourceMode.COMMUNITY -> loadStrategyList()
            }
        }

        findViewById<Button>(R.id.btn_job_station_search).setOnClickListener {
            submitSearch()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            submitSearch()
            true
        }

        bindPageMode()
        setupFilterChips()
        loadStrategies()
    }

    override fun onDestroy() {
        currentListCall?.cancel()
        currentCommentsCall?.cancel()
        currentListCall = null
        currentCommentsCall = null
        listRequestVersion += 1
        commentsRequestVersion += 1
        stageDropdown?.dismiss()
        stageDropdown = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        when (sourceMode) {
            SourceMode.MAA -> if (allItems.isNotEmpty()) applyMaaFilters()
            SourceMode.COMMUNITY -> if (loadedStrategyDetails.isNotEmpty()) applyStrategyFiltersAndSearch()
        }
    }

    private fun bindPageMode() {
        when (sourceMode) {
            SourceMode.COMMUNITY -> {
                currentSortMode = SortMode.NEWEST
                selectedGameTag = ""
                selectedStageTag = ""
                searchInput.hint = "搜索关卡/密探"
                uploadButton.visibility = View.VISIBLE
                chipSortHot.visibility = View.VISIBLE
                chipSortNewest.visibility = View.VISIBLE
                chipGameRuyuan.visibility = View.VISIBLE
                chipGameDaihao.visibility = View.VISIBLE
                chipStageFilter.visibility = View.GONE
                chipDefaultFilter.visibility = View.VISIBLE
            }

            SourceMode.MAA -> {
                currentSortMode = SortMode.HOT
                selectedGameTag = ""
                selectedStageTag = ""
                searchInput.hint = "搜索关卡/密探/神秘代码"
                uploadButton.visibility = View.GONE
                chipSortHot.visibility = View.VISIBLE
                chipSortNewest.visibility = View.VISIBLE
                chipGameRuyuan.visibility = View.VISIBLE
                chipGameDaihao.visibility = View.VISIBLE
                chipStageFilter.visibility = View.VISIBLE
                chipDefaultFilter.visibility = View.VISIBLE
            }
        }
    }

    private fun loadStrategies() {
        when (sourceMode) {
            SourceMode.COMMUNITY -> loadStrategyList()
            SourceMode.MAA -> {
                if (!restoreMaaSortCacheIfAvailable()) {
                    reloadMaa()
                }
            }
        }
    }

    private fun setupFilterChips() {
        chipSortHot.setOnClickListener {
            if (currentSortMode != SortMode.HOT) {
                currentSortMode = SortMode.HOT
                updateFilterChipUi()
                when (sourceMode) {
                    SourceMode.MAA -> {
                        if (!restoreMaaSortCacheIfAvailable()) {
                            reloadMaa()
                        }
                    }

                    SourceMode.COMMUNITY -> loadStrategyList()
                }
            }
        }

        chipSortNewest.setOnClickListener {
            if (currentSortMode != SortMode.NEWEST) {
                currentSortMode = SortMode.NEWEST
                updateFilterChipUi()
                when (sourceMode) {
                    SourceMode.MAA -> {
                        if (!restoreMaaSortCacheIfAvailable()) {
                            reloadMaa()
                        }
                    }

                    SourceMode.COMMUNITY -> loadStrategyList()
                }
            }
        }

        chipGameRuyuan.setOnClickListener {
            selectedGameTag = if (selectedGameTag == "如鸢") "" else "如鸢"
            updateFilterChipUi()
            when (sourceMode) {
                SourceMode.MAA -> applyMaaFilters()
                SourceMode.COMMUNITY -> applyStrategyFiltersAndSearch()
            }
        }

        chipGameDaihao.setOnClickListener {
            selectedGameTag = if (selectedGameTag == "代号鸢") "" else "代号鸢"
            updateFilterChipUi()
            when (sourceMode) {
                SourceMode.MAA -> applyMaaFilters()
                SourceMode.COMMUNITY -> applyStrategyFiltersAndSearch()
            }
        }

        chipStageFilter.setOnClickListener {
            if (sourceMode != SourceMode.MAA) return@setOnClickListener
            showStageDropdown()
        }

        updateFilterChipUi()
    }

    private fun showStageDropdown() {
        val options = listOf("全部") + STAGE_OPTIONS
        val selectedIndex = options.indexOf(
            if (selectedStageTag.isBlank()) "全部" else selectedStageTag
        ).coerceAtLeast(0)
        val popupWidth = chipStageFilter.width.coerceAtLeast(dpToPx(64f))

        stageDropdown?.dismiss()
        stageDropdown = ListPopupWindow(this).apply {
            anchorView = chipStageFilter
            width = popupWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isModal = true
            verticalOffset = dpToPx(6f)
            setBackgroundDrawable(
                AppCompatResources.getDrawable(this@JobStationListActivity, R.drawable.bg_job_station_card)
            )
            setAdapter(object : ArrayAdapter<String>(
                this@JobStationListActivity,
                R.layout.item_job_station_stage_dropdown,
                R.id.tv_job_station_stage_dropdown,
                options
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(R.id.tv_job_station_stage_dropdown)
                    val selected = position == selectedIndex
                    textView.background = createStageDropdownItemBackground(selected)
                    textView.setTextColor(
                        Color.parseColor(
                            if (selected) "#6B4E1C" else "#8C6C33"
                        )
                    )
                    return view
                }
            })
            setOnItemClickListener { _, _, position, _ ->
                selectedStageTag = if (position == 0) "" else options[position]
                updateFilterChipUi()
                reloadMaa()
                dismiss()
            }
            show()
            listView?.apply {
                divider = null
                dividerHeight = 0
                setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        }
    }

    private fun reloadMaa(fromPullRefresh: Boolean = false) {
        if (fromPullRefresh) {
            clearMaaSortCacheIfNeeded()
        }
        allItems = emptyList()
        currentMaaPage = 1
        hasNextMaaPage = false
        isLoadingMore = false
        if (!fromPullRefresh) {
            swipeRefreshLayout.isRefreshing = false
        } else {
            swipeRefreshLayout.isRefreshing = true
        }
        updateLoadMoreUi()
        showEmpty("正在加载 MaaYuan Share...")
        loadMaaPage(page = 1, append = false)
    }

    private fun loadMaaPage(page: Int, append: Boolean) {
        isLoadingMore = append
        updateLoadMoreUi()
        currentListCall?.cancel()
        val requestVersion = ++listRequestVersion
        currentListCall = JobStationRemoteRepository.loadList(
            page = page,
            orderBy = currentSortMode.maaOrderBy,
            levelKeyword = selectedStageTag,
            onSuccess = { pageInfo, maaItems ->
                runOnUiThread {
                    if (requestVersion != listRequestVersion || isFinishing || isDestroyed) return@runOnUiThread
                    currentListCall = null
                    currentMaaPage = pageInfo.page
                    hasNextMaaPage = pageInfo.hasNext
                    allItems = if (append) {
                        (allItems + maaItems).distinctBy { it.copilotId }
                    } else {
                        maaItems
                    }
                    cacheCurrentMaaSortResultIfNeeded()
                    isLoadingMore = false
                    updateLoadMoreUi()
                    applyMaaFilters()
                }
            },
            onError = { message ->
                runOnUiThread {
                    if (requestVersion != listRequestVersion || isFinishing || isDestroyed) return@runOnUiThread
                    currentListCall = null
                    isLoadingMore = false
                    if (append) {
                        Toast.makeText(this@JobStationListActivity, message, Toast.LENGTH_SHORT).show()
                    } else {
                        allItems = emptyList()
                        Toast.makeText(this@JobStationListActivity, message, Toast.LENGTH_SHORT).show()
                        showEmpty("MaaYuan Share 加载失败")
                    }
                    swipeRefreshLayout.isRefreshing = false
                    updateLoadMoreUi()
                }
            }
        )
    }

    private fun loadStrategyList() {
        showEmpty("正在加载本站攻略...")
        val requestVersion = ++listRequestVersion
        SupabaseRepository.listPublicStrategies(
            sortMode = if (currentSortMode == SortMode.NEWEST) "newest" else "hot",
            limit = 200,
            onSuccess = { list: List<strategy_detail> ->
                if (requestVersion != listRequestVersion || isFinishing || isDestroyed) return@listPublicStrategies
                loadedStrategyDetails = list
                allItems = loadedStrategyDetails
                    .map(JobStationAssetRepository::fromCommunityListItem)
                    .sortedByDescending { it.publishTimestamp }
                applyStrategyFiltersAndSearch()
            },
            onError = { message ->
                if (requestVersion != listRequestVersion || isFinishing || isDestroyed) return@listPublicStrategies
                Toast.makeText(this, "社区攻略加载失败: $message", Toast.LENGTH_SHORT).show()
                loadedStrategyDetails = emptyList()
                allItems = emptyList()
                applyStrategyFiltersAndSearch()
            },
        )
    }

    private fun shouldUseMaaSortCache(): Boolean = selectedStageTag.isBlank()

    private fun currentMaaSortCacheKey(): String = currentSortMode.name

    private fun clearMaaSortCacheIfNeeded() {
        if (shouldUseMaaSortCache()) {
            maaSortResultCache.remove(currentMaaSortCacheKey())
        }
    }

    private fun cacheCurrentMaaSortResultIfNeeded() {
        if (!shouldUseMaaSortCache()) return
        maaSortResultCache[currentMaaSortCacheKey()] = CachedMaaState(
            loadedItems = allItems,
            currentPage = currentMaaPage,
            hasNextPage = hasNextMaaPage
        )
    }

    private fun restoreMaaSortCacheIfAvailable(): Boolean {
        if (!shouldUseMaaSortCache()) return false
        val cached = maaSortResultCache[currentMaaSortCacheKey()] ?: return false
        allItems = cached.loadedItems
        currentMaaPage = cached.currentPage
        hasNextMaaPage = cached.hasNextPage
        isLoadingMore = false
        swipeRefreshLayout.isRefreshing = false
        updateLoadMoreUi()
        applyMaaFilters()
        return true
    }

    private fun applyMaaFilters() {
        var filteredItems = allItems
        val excludedAgents = getExcludedAgentsNormalized()

        if (selectedGameTag.isNotBlank()) {
            filteredItems = filteredItems.filter { it.gameTag == selectedGameTag }
        }

        if (selectedStageTag.isNotBlank()) {
            filteredItems = filteredItems.filter { it.categoryTag == selectedStageTag }
        }

        if (excludedAgents.isNotEmpty()) {
            filteredItems = filteredItems.filterNot { item ->
                item.roster.any { normalizeAgentName(it) in excludedAgents }
            }
        }

        if (filteredItems.isEmpty()) {
            showEmpty("没有匹配的攻略，换个关键词试试")
            return
        }

        showItems(filteredItems)
    }

    private fun submitSearch() {
        val keyword = searchInput.text.toString().trim()
        if (sourceMode == SourceMode.MAA) {
            submitMaaSearch(keyword)
            return
        }

        applyStrategyFiltersAndSearch(keyword)
    }

    private fun submitMaaSearch(newKeyword: String) {
        parseMysteryCopilotId(newKeyword)?.let { copilotId ->
            currentCommentsCall?.cancel()
            val requestVersion = ++commentsRequestVersion
            currentCommentsCall = JobStationRemoteRepository.loadComments(
                copilotId = copilotId,
                onSuccess = {
                    runOnUiThread {
                        if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@runOnUiThread
                        currentCommentsCall = null
                        openJobStationDetail(copilotId)
                    }
                },
                onError = { message ->
                    runOnUiThread {
                        if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@runOnUiThread
                        currentCommentsCall = null
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            return
        }

        val matchedStageTag = resolveStageKeyword(newKeyword)
        if (newKeyword.isBlank()) {
            if (selectedStageTag.isBlank()) {
                applyMaaFilters()
                return
            }
            selectedStageTag = ""
            updateFilterChipUi()
            reloadMaa()
            return
        }

        if (matchedStageTag == null) {
            Toast.makeText(this, "未识别到对应关卡关键词", Toast.LENGTH_SHORT).show()
            return
        }

        if (matchedStageTag == selectedStageTag) {
            applyMaaFilters()
            return
        }
        selectedStageTag = matchedStageTag
        updateFilterChipUi()
        reloadMaa()
    }

    private fun applyStrategyFiltersAndSearch(keyword: String = searchInput.text.toString().trim()) {
        val keywords = tokenizeSearchKeywords(keyword)
        val excludedAgents = getExcludedAgentsNormalized()
        val filteredItems = loadedStrategyDetails.asSequence()
            .filter { detail ->
                when (selectedGameTag) {
                    "如鸢" -> detail.ruyuan == 1
                    "代号鸢" -> detail.ruyuan != 1
                    else -> true
                }
            }
            .map { detail -> detail to JobStationAssetRepository.fromCommunityListItem(detail) }
            .filter { (_, item) ->
                excludedAgents.isEmpty() || item.roster.none { normalizeAgentName(it) in excludedAgents }
            }
            .filter { (detail, item) ->
                keywords.isEmpty() || matchesAllKeywords(buildCommunitySearchableText(detail, item), keywords)
            }
            .map { it.second }
            .toList()

        if (filteredItems.isEmpty()) {
            val emptyMessage = if (loadedStrategyDetails.isEmpty()) {
                "暂无可展示的本站攻略"
            } else {
                "没有匹配的攻略，换个关键词试试"
            }
            showEmpty(emptyMessage)
            return
        }

        showItems(filteredItems)
    }

    private fun resolveStageKeyword(keyword: String): String? {
        val normalized = keyword.trim()
        if (normalized.isBlank()) return null

        return when {
            normalized.contains("白鹄") -> "白鹄"
            normalized.contains("洞窟") -> "洞窟"
            normalized.contains("兰台") -> "兰台"
            normalized.contains("遗迹") || normalized.contains("地宫") -> "地宫"
            normalized.contains("主线") -> "主线"
            normalized.contains("家具") -> "家具"
            normalized.contains("活动") -> "活动"
            else -> null
        }
    }

    private fun parseMysteryCopilotId(keyword: String): Long? {
        if (!keyword.startsWith("maay://", ignoreCase = true)) return null
        return keyword.substring(7)
            .trim()
            .toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun buildCommunitySearchableText(
        detail: strategy_detail,
        item: JobStationAssetRepository.JobStationListItem
    ): String {
        return listOf(
            detail.title,
            detail.content,
            detail.agents,
            detail.agentTextDesc,
            item.agentsText,
            item.roster.joinToString(" ")
        ).joinToString("\n") { it.trim() }
            .let(::normalizeSearchKeyword)
    }

    private fun matchesAllKeywords(searchableText: String, keywords: List<String>): Boolean {
        return keywords.all(searchableText::contains)
    }

    private fun tokenizeSearchKeywords(raw: String): List<String> {
        return raw.split(Regex("[\\s\u3000]+"))
            .map(::normalizeSearchKeyword)
            .filter { it.isNotBlank() }
    }

    private fun normalizeSearchKeyword(raw: String): String {
        return raw.lowercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
    }

    private fun getExcludedAgentsNormalized(): Set<String> {
        return ConfigManager.getExcludedAgents(this)
            .map(::normalizeAgentName)
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun normalizeAgentName(raw: String): String {
        return raw.trim()
            .replace(" ", "")
            .replace("　", "")
            .lowercase()
    }

    private fun openJobStationDetail(copilotId: Long) {
        startActivity(Intent(this, JobStationActivity::class.java).apply {
            putExtra(JobStationActivity.EXTRA_COPILOT_ID, copilotId)
        })
    }

    private fun showItems(items: List<JobStationAssetRepository.JobStationListItem>) {
        swipeRefreshLayout.isRefreshing = false
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        listAdapter.updateData(
            items = items,
            showLoadMore = sourceMode == SourceMode.MAA && (hasNextMaaPage || isLoadingMore),
            isLoadingMore = isLoadingMore
        )
    }

    private fun showEmpty(message: String) {
        swipeRefreshLayout.isRefreshing = false
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun updateFilterChipUi() {
        bindChip(chipSortHot, currentSortMode == SortMode.HOT, true)
        bindChip(chipSortNewest, currentSortMode == SortMode.NEWEST, true)
        bindChip(chipGameRuyuan, selectedGameTag == "如鸢", true)
        bindChip(chipGameDaihao, selectedGameTag == "代号鸢", true)
        if (sourceMode == SourceMode.MAA) {
            bindChip(chipStageFilter, selectedStageTag.isNotBlank(), true)
            chipStageFilter.text = if (selectedStageTag.isBlank()) "关卡" else selectedStageTag
        }
        bindChip(chipDefaultFilter, false, false)
    }

    private fun updateLoadMoreUi() {
        // 列表底部 footer 负责展示加载更多状态，这里不再维护固定按钮。
    }

    private fun bindChip(view: TextView, selected: Boolean, enabled: Boolean) {
        val background = GradientDrawable().apply {
            cornerRadius = dpToPx(999f).toFloat()
            if (selected) {
                setColor(Color.parseColor("#F6D59A"))
                setStroke(dpToPx(1f), Color.parseColor("#C88A2C"))
            } else {
                setColor(Color.parseColor("#F8F2E5"))
                setStroke(dpToPx(1f), Color.parseColor("#D8C18A"))
            }
        }
        view.background = background
        view.setTextColor(
            Color.parseColor(
                when {
                    !enabled -> "#B5A690"
                    selected -> "#6B4E1C"
                    else -> "#8C6C33"
                }
            )
        )
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.75f
    }

    private fun createStageDropdownItemBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(10f).toFloat()
            if (selected) {
                setColor(Color.parseColor("#F6D59A"))
                setStroke(dpToPx(1f), Color.parseColor("#C88A2C"))
            } else {
                setColor(Color.TRANSPARENT)
            }
        }
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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}


