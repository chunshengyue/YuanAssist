package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import com.example.yuanassist.R
import com.example.yuanassist.model.strategy_detail
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton

class StrategyFragment : Fragment() {

    private lateinit var adapter: StrategyAdapter
    private val fullList = ArrayList<strategy_detail>()    // 从数据库拉取的完整列表
    private val displayList = ArrayList<strategy_detail>() // 当前展示的列表

    // 状态记录
    private var isRuYuanSelected = false
    private var isDaiHaoSelected = false
    private var currentSort = "clicks" // 默认最多点击: "clicks", "newest", "likes"
    private var selectedEventTag = ""  // 当前选中的活动标签
    private var currentKeyword = ""

    // 预设的活动标签
    private val eventTags = listOf("地宫", "遗迹", "咪教模拟器", "3月洞窟", "3月白鹄")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_strategy, container, false)
        initViews(view)
        loadDataFromBmob()
        return view
    }

    private fun initViews(view: View) {
        val rvStrategy = view.findViewById<RecyclerView>(R.id.rv_strategy_list)

        // 3. 这里的 item 现在会自动识别为 strategy_detail 类型
        adapter = StrategyAdapter(displayList) { item ->
            val intent = Intent(requireContext(), StrategyDetailActivity::class.java)
            // 因为 strategy_detail 继承自 BmobObject，所以 objectId 绝对存在
            intent.putExtra("STRATEGY_ID", item.objectId)
            startActivity(intent)
        }
        rvStrategy.layoutManager = LinearLayoutManager(requireContext())
        rvStrategy.adapter = adapter

        // 2. 搜索逻辑
        val etSearch = view.findViewById<EditText>(R.id.et_search_keyword)
        view.findViewById<Button>(R.id.btn_search).setOnClickListener {
            currentKeyword = etSearch.text.toString().trim()
            applyFilters()
        }
        val fabUpload = view.findViewById<FloatingActionButton>(R.id.fab_upload_strategy)
        fabUpload.setOnClickListener {
            startActivity(Intent(requireContext(), UploadStrategyActivity::class.java))
        }
        // 3. 如鸢 / 代号鸢 互斥逻辑
        val tvRuYuan = view.findViewById<TextView>(R.id.tv_filter_ruyuan)
        val tvDaiHao = view.findViewById<TextView>(R.id.tv_filter_daihao)

        tvRuYuan.setOnClickListener {
            isRuYuanSelected = !isRuYuanSelected
            updateToggleButtonUI(tvRuYuan, isRuYuanSelected)
            applyFilters()
        }

        tvDaiHao.setOnClickListener {
            isDaiHaoSelected = !isDaiHaoSelected
            updateToggleButtonUI(tvDaiHao, isDaiHaoSelected)
            applyFilters()
        }

        // 4. 高级筛选弹窗
        view.findViewById<LinearLayout>(R.id.btn_advanced_filter).setOnClickListener {
            showFilterBottomSheet()
        }
    }

    // 🔴 核心过滤逻辑：把所有的条件综合起来过滤数据
    private fun applyFilters() {
        var filtered = fullList.toList()

        // 1. 关键词搜索 (加入安全兜底，把 null 视为空字符串)
        if (currentKeyword.isNotEmpty()) {
            filtered = filtered.filter {
                (it.title ?: "").contains(currentKeyword, true) ||
                        (it.agents ?: "").contains(currentKeyword, true)
            }
        }

        // 2. 游戏版本排他过滤
        if (isRuYuanSelected && !isDaiHaoSelected) {
            filtered = filtered.filter { !(it.title ?: "").contains("代号鸢") }
        } else if (!isRuYuanSelected && isDaiHaoSelected) {
            filtered = filtered.filter { !(it.title ?: "").contains("如鸢") }
        }

        // 3. 活动标签过滤
        if (selectedEventTag.isNotEmpty()) {
            filtered = filtered.filter { (it.title ?: "").contains(selectedEventTag) }
        }

        // 4. 排序方式
        filtered = when (currentSort) {
            "newest" -> filtered.sortedByDescending { it.createdAt }
            "likes" -> filtered.sortedByDescending { it.createdAt }
            else -> filtered.sortedByDescending { it.createdAt }
        }

        displayList.clear()
        displayList.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    // 辅助方法：动态改变方框的实心/空心状态
    private fun updateToggleButtonUI(textView: TextView, isSelected: Boolean) {
        if (isSelected) {
            textView.setBackgroundResource(R.drawable.btn_dark_gold)
            textView.setTextColor(Color.parseColor("#1A1A1A"))
        } else {
            textView.setBackgroundResource(R.drawable.btn_dark_hollow)
            textView.setTextColor(Color.parseColor("#E5C07B"))
        }
    }

    // 显示底部筛选弹窗
    private fun showFilterBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_strategy_filter, null)
        bottomSheet.setContentView(view)

        val rgSort = view.findViewById<RadioGroup>(R.id.rg_sort)
        val gridTags = view.findViewById<GridLayout>(R.id.grid_tags)
        val btnReset = view.findViewById<Button>(R.id.btn_filter_reset)
        val btnConfirm = view.findViewById<Button>(R.id.btn_filter_confirm)

        // 恢复当前的排序状态
        when (currentSort) {
            "newest" -> rgSort.check(R.id.rb_sort_newest)
            "likes" -> rgSort.check(R.id.rb_sort_likes)
            else -> rgSort.check(R.id.rb_sort_clicks)
        }

        // 动态添加活动标签
        var tempSelectedTag = selectedEventTag
        eventTags.forEach { tag ->
            val tagView = TextView(requireContext()).apply {
                text = tag
                textSize = 12f
                setPadding(20, 10, 20, 10)
                layoutParams = GridLayout.LayoutParams().apply {
                    setMargins(8, 8, 8, 8)
                }
            }
            updateToggleButtonUI(tagView, tag == tempSelectedTag)

            tagView.setOnClickListener {
                // 实现单选逻辑
                tempSelectedTag = if (tempSelectedTag == tag) "" else tag
                // 刷新所有标签的UI
                for (i in 0 until gridTags.childCount) {
                    val child = gridTags.getChildAt(i) as TextView
                    updateToggleButtonUI(child, child.text.toString() == tempSelectedTag)
                }
            }
            gridTags.addView(tagView)
        }

        btnReset.setOnClickListener {
            rgSort.check(R.id.rb_sort_clicks)
            tempSelectedTag = ""
            for (i in 0 until gridTags.childCount) {
                updateToggleButtonUI(gridTags.getChildAt(i) as TextView, false)
            }
        }

        btnConfirm.setOnClickListener {
            currentSort = when (rgSort.checkedRadioButtonId) {
                R.id.rb_sort_newest -> "newest"
                R.id.rb_sort_likes -> "likes"
                else -> "clicks"
            }
            selectedEventTag = tempSelectedTag
            bottomSheet.dismiss()
            applyFilters() // 重新应用过滤
        }

        bottomSheet.show()
    }

    private fun loadDataFromBmob() {
        Toast.makeText(requireContext(), "正在加载攻略...", Toast.LENGTH_SHORT).show()
        val query = BmobQuery<strategy_detail>()
        query.order("-createdAt")
        query.setLimit(500)
        query.include("author")
        query.findObjects(object : FindListener<strategy_detail>() {
            override fun done(list: MutableList<strategy_detail>?, e: BmobException?) {
                val ctx = context ?: return
                activity?.runOnUiThread {
                    if (e == null && list != null) {
                        fullList.clear()
                        fullList.addAll(list)
                        applyFilters() // 拉取成功后应用一次当前规则
                    } else {
                        Toast.makeText(ctx, "加载失败: ${e?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}