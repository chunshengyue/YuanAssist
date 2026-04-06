package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import com.example.yuanassist.R
import com.example.yuanassist.model.STRATEGY_VISIBLE_PUBLIC
import com.example.yuanassist.model.strategy_detail
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call

class JobStationListFragment : Fragment() {

    companion object {
        private val STAGE_OPTIONS = listOf("主线", "白鹄", "洞窟", "兰台", "地宫", "家具", "活动")

        private data class CachedSortState(
            val loadedMaaItems: List<JobStationAssetRepository.JobStationListItem>,
            val loadedBmobItems: List<strategy_detail>,
            val mergedItems: List<JobStationAssetRepository.JobStationListItem>,
            val currentMaaPage: Int,
            val hasNextMaaPage: Boolean
        )

        private val sortResultCache = mutableMapOf<String, CachedSortState>()
    }

    private enum class SortMode(val maaOrderBy: String) {
        HOT("hot"),
        NEWEST("id")
    }

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchInput: EditText
    private lateinit var chipSortHot: TextView
    private lateinit var chipSortNewest: TextView
    private lateinit var chipGameRuyuan: TextView
    private lateinit var chipGameDaihao: TextView
    private lateinit var chipStageFilter: TextView
    private lateinit var chipDefaultFilter: TextView
    private lateinit var listAdapter: JobStationListAdapter

    private var currentSortMode = SortMode.HOT
    private var selectedGameTag = ""
    private var selectedStageTag = ""
    private var currentMaaPage = 1
    private var hasNextMaaPage = false
    private var isLoadingMore = false
    private var loadedMaaItems: List<JobStationAssetRepository.JobStationListItem> = emptyList()
    private var loadedBmobItems: List<strategy_detail> = emptyList()
    private var mergedItems: List<JobStationAssetRepository.JobStationListItem> = emptyList()
    private var stageDropdown: ListPopupWindow? = null
    private var currentListCall: Call<*>? = null
    private var currentCommentsCall: Call<*>? = null
    private var listRequestVersion = 0
    private var commentsRequestVersion = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_job_station_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyStatusBarInsets(view)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    (activity as? MainActivity)?.navigateToTab(R.id.nav_home)
                }
            }
        )

        view.findViewById<ImageView>(R.id.btn_job_station_list_back).visibility = View.GONE
        emptyView = view.findViewById(R.id.tv_job_station_list_empty)
        recyclerView = view.findViewById(R.id.rv_job_station_list)
        swipeRefreshLayout = view.findViewById(R.id.swipe_job_station_list)
        searchInput = view.findViewById(R.id.et_job_station_search_keyword)
        chipSortHot = view.findViewById(R.id.chip_sort_hot)
        chipSortNewest = view.findViewById(R.id.chip_sort_newest)
        chipGameRuyuan = view.findViewById(R.id.chip_game_ruyuan)
        chipGameDaihao = view.findViewById(R.id.chip_game_daihao)
        chipStageFilter = view.findViewById(R.id.chip_stage_filter)
        chipDefaultFilter = view.findViewById(R.id.chip_default_filter)
        view.findViewById<FloatingActionButton>(R.id.fab_upload_strategy).setOnClickListener {
            startActivity(Intent(requireContext(), UploadStrategyActivity::class.java))
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        listAdapter = JobStationListAdapter(
            items = emptyList(),
            showLoadMore = false,
            isLoadingMore = false,
            onClick = { item ->
                when (item.type) {
                    JobStationAssetRepository.JobStationListItemType.BMOB -> {
                        item.strategyId?.let { strategyId ->
                            startActivity(Intent(requireContext(), JobStationActivity::class.java).apply {
                                putExtra(JobStationActivity.EXTRA_STRATEGY_ID, strategyId)
                            })
                        }
                    }

                    JobStationAssetRepository.JobStationListItemType.MAA -> {
                        item.copilotId?.let { copilotId ->
                            startActivity(Intent(requireContext(), JobStationActivity::class.java).apply {
                                putExtra(JobStationActivity.EXTRA_COPILOT_ID, copilotId)
                            })
                        }
                    }
                }
            },
            onLoadMore = {
                if (!isLoadingMore && hasNextMaaPage) {
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
            reloadAll(fromPullRefresh = true)
        }
        view.findViewById<Button>(R.id.btn_job_station_search).setOnClickListener {
            submitSearch()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            submitSearch()
            true
        }

        setupFilterChips()
        if (!restoreSortCacheIfAvailable()) {
            reloadAll()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavVisible(false)
    }

    override fun onPause() {
        (activity as? MainActivity)?.setBottomNavVisible(true)
        super.onPause()
    }

    override fun onDestroyView() {
        currentListCall?.cancel()
        currentCommentsCall?.cancel()
        currentListCall = null
        currentCommentsCall = null
        listRequestVersion += 1
        commentsRequestVersion += 1
        stageDropdown?.dismiss()
        stageDropdown = null
        super.onDestroyView()
    }

    private fun setupFilterChips() {
        chipSortHot.setOnClickListener {
            if (currentSortMode != SortMode.HOT) {
                currentSortMode = SortMode.HOT
                updateFilterChipUi()
                if (!restoreSortCacheIfAvailable()) {
                    reloadAll()
                }
            }
        }

        chipSortNewest.setOnClickListener {
            if (currentSortMode != SortMode.NEWEST) {
                currentSortMode = SortMode.NEWEST
                updateFilterChipUi()
                if (!restoreSortCacheIfAvailable()) {
                    reloadAll()
                }
            }
        }

        chipGameRuyuan.setOnClickListener {
            selectedGameTag = if (selectedGameTag == "如鸢") "" else "如鸢"
            updateFilterChipUi()
            applyFilters()
        }

        chipGameDaihao.setOnClickListener {
            selectedGameTag = if (selectedGameTag == "代号鸢") "" else "代号鸢"
            updateFilterChipUi()
            applyFilters()
        }

        chipStageFilter.setOnClickListener {
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
        stageDropdown = ListPopupWindow(requireContext()).apply {
            anchorView = chipStageFilter
            width = popupWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isModal = true
            verticalOffset = dpToPx(6f)
            setBackgroundDrawable(
                AppCompatResources.getDrawable(requireContext(), R.drawable.bg_job_station_card)
            )
            setAdapter(object : ArrayAdapter<String>(
                requireContext(),
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
                reloadAll()
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

    private fun reloadAll(fromPullRefresh: Boolean = false) {
        if (fromPullRefresh) {
            clearCurrentSortCacheIfNeeded()
        }
        loadedMaaItems = emptyList()
        loadedBmobItems = emptyList()
        mergedItems = emptyList()
        currentMaaPage = 1
        hasNextMaaPage = false
        isLoadingMore = false
        if (!fromPullRefresh) {
            swipeRefreshLayout.isRefreshing = false
        } else {
            swipeRefreshLayout.isRefreshing = true
        }
        updateLoadMoreUi()
        showEmpty("正在加载攻略...")
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
            onSuccess = { pageInfo, items ->
                activity?.runOnUiThread {
                    if (!isFragmentViewAlive() || requestVersion != listRequestVersion) return@runOnUiThread
                    currentListCall = null
                    currentMaaPage = pageInfo.page
                    hasNextMaaPage = pageInfo.hasNext
                    loadedMaaItems = if (append) {
                        (loadedMaaItems + items).distinctBy { it.copilotId }
                    } else {
                        items
                    }
                    loadBmobStrategies(requestVersion)
                }
            },
            onError = { message ->
                activity?.runOnUiThread {
                    if (!isFragmentViewAlive() || requestVersion != listRequestVersion) return@runOnUiThread
                    currentListCall = null
                    isLoadingMore = false
                    if (append) {
                        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
                    } else {
                        loadedMaaItems = emptyList()
                        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
                        loadBmobStrategies(requestVersion)
                        return@runOnUiThread
                    }
                    swipeRefreshLayout.isRefreshing = false
                    updateLoadMoreUi()
                }
            }
        )
    }

    private fun loadBmobStrategies(requestVersion: Int) {
        val query = BmobQuery<strategy_detail>()
        query.addWhereEqualTo("visible", STRATEGY_VISIBLE_PUBLIC)
        query.order(
            when (currentSortMode) {
                SortMode.HOT -> "-viewCount"
                SortMode.NEWEST -> "-createdAt"
            }
        )
        query.setLimit(200)
        query.include("author")
        query.findObjects(object : FindListener<strategy_detail>() {
            override fun done(list: MutableList<strategy_detail>?, e: BmobException?) {
                activity?.runOnUiThread {
                    if (!isFragmentViewAlive() || requestVersion != listRequestVersion) return@runOnUiThread
                    swipeRefreshLayout.isRefreshing = false
                    if (e != null) {
                        context?.let {
                            Toast.makeText(
                                it,
                                "社区攻略加载失败: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    loadedBmobItems = list.orEmpty()
                    mergedItems = mergeStrategies(loadedMaaItems, loadedBmobItems)
                    cacheCurrentSortResultIfNeeded()
                    isLoadingMore = false
                    updateLoadMoreUi()
                    applyFilters()
                }
            }
        })
    }

    private fun shouldUseSortCache(): Boolean = selectedStageTag.isBlank()

    private fun currentSortCacheKey(): String = currentSortMode.name

    private fun clearCurrentSortCacheIfNeeded() {
        if (shouldUseSortCache()) {
            sortResultCache.remove(currentSortCacheKey())
        }
    }

    private fun cacheCurrentSortResultIfNeeded() {
        if (!shouldUseSortCache()) return
        sortResultCache[currentSortCacheKey()] = CachedSortState(
            loadedMaaItems = loadedMaaItems,
            loadedBmobItems = loadedBmobItems,
            mergedItems = mergedItems,
            currentMaaPage = currentMaaPage,
            hasNextMaaPage = hasNextMaaPage
        )
    }

    private fun restoreSortCacheIfAvailable(): Boolean {
        if (!shouldUseSortCache()) return false
        val cached = sortResultCache[currentSortCacheKey()] ?: return false
        loadedMaaItems = cached.loadedMaaItems
        loadedBmobItems = cached.loadedBmobItems
        mergedItems = cached.mergedItems
        currentMaaPage = cached.currentMaaPage
        hasNextMaaPage = cached.hasNextMaaPage
        isLoadingMore = false
        swipeRefreshLayout.isRefreshing = false
        updateLoadMoreUi()
        applyFilters()
        return true
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

        return when (currentSortMode) {
            SortMode.HOT -> merged.sortedWith(
                compareByDescending<JobStationAssetRepository.JobStationListItem> { it.hotScore }
                    .thenByDescending { it.publishTimestamp }
            )

            SortMode.NEWEST -> merged.sortedByDescending { it.publishTimestamp }
        }
    }

    private fun applyFilters() {
        var filteredItems = mergedItems

        if (selectedGameTag.isNotBlank()) {
            filteredItems = filteredItems.filter { it.gameTag == selectedGameTag }
        }

        if (selectedStageTag.isNotBlank()) {
            filteredItems = filteredItems.filter { it.categoryTag == selectedStageTag }
        }

        if (filteredItems.isEmpty()) {
            showEmpty("没有匹配的攻略，换个关键词试试")
        } else {
            showItems(filteredItems)
        }
    }

    private fun showItems(items: List<JobStationAssetRepository.JobStationListItem>) {
        swipeRefreshLayout.isRefreshing = false
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        listAdapter.updateData(
            items = items,
            showLoadMore = hasNextMaaPage || isLoadingMore,
            isLoadingMore = isLoadingMore,
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
        bindChip(chipStageFilter, selectedStageTag.isNotBlank(), true)
        chipStageFilter.text = if (selectedStageTag.isBlank()) "关卡" else selectedStageTag
        bindChip(chipDefaultFilter, true, false)
    }

    private fun updateLoadMoreUi() {
        // 列表底部 footer 负责展示加载更多状态，这里不再维护固定按钮。
    }

    private fun submitSearch() {
        val newKeyword = searchInput.text.toString().trim()
        parseMysteryCopilotId(newKeyword)?.let { copilotId ->
            currentCommentsCall?.cancel()
            val requestVersion = ++commentsRequestVersion
            currentCommentsCall = JobStationRemoteRepository.loadComments(
                copilotId = copilotId,
                onSuccess = {
                    activity?.runOnUiThread {
                        if (!isFragmentViewAlive() || requestVersion != commentsRequestVersion) return@runOnUiThread
                        currentCommentsCall = null
                        openJobStationDetail(copilotId)
                    }
                },
                onError = { message ->
                    activity?.runOnUiThread {
                        if (!isFragmentViewAlive() || requestVersion != commentsRequestVersion) return@runOnUiThread
                        currentCommentsCall = null
                        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
                    }
                }
            )
            return
        }

        val matchedStageTag = resolveStageKeyword(newKeyword)
        if (newKeyword.isBlank()) {
            if (selectedStageTag.isBlank()) {
                applyFilters()
                return
            }
            selectedStageTag = ""
            updateFilterChipUi()
            reloadAll()
            return
        }

        if (matchedStageTag == null) {
            context?.let { Toast.makeText(it, "未识别到对应关卡关键词", Toast.LENGTH_SHORT).show() }
            return
        }

        if (matchedStageTag == selectedStageTag) {
            applyFilters()
            return
        }
        selectedStageTag = matchedStageTag
        updateFilterChipUi()
        reloadAll()
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

    private fun openJobStationDetail(copilotId: Long) {
        startActivity(Intent(requireContext(), JobStationActivity::class.java).apply {
            putExtra(JobStationActivity.EXTRA_COPILOT_ID, copilotId)
        })
    }

    private fun isFragmentViewAlive(): Boolean =
        isAdded && view != null && activity != null

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

    private fun applyStatusBarInsets(root: View) {
        val statusBarSpacer = root.findViewById<View>(R.id.view_job_station_list_status_bar_spacer)
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
