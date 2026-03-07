package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.StrategyItem
import com.example.yuanassist.network.NetworkClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StrategyListActivity : AppCompatActivity() {

    private lateinit var adapter: StrategyAdapter

    // 这里的两个列表用于搜索过滤
    private var fullList: List<StrategyItem> = ArrayList() // 原始完整数据
    private var displayList: MutableList<StrategyItem> = ArrayList() // 当前显示的数据

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_strategy_list)

        val etSearch = findViewById<EditText>(R.id.et_search)
        val btnSearch =
            findViewById<Button>(R.id.btn_search) // 这里的搜索按钮其实可以用 TextWatcher 实时搜索代替，或者点它强制搜
        val recyclerView = findViewById<RecyclerView>(R.id.rv_strategy)

        // 1. 初始化列表
        adapter = StrategyAdapter(displayList) { item ->
            // 点击某一项的回调
            onItemClicked(item)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 2. 加载数据
        loadDataFromGitee()

        // 3. 实时搜索逻辑
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 按钮搜索 (可选，和上面二选一，这里作为强制刷新用)
        btnSearch.setOnClickListener {
            filter(etSearch.text.toString())
        }
    }

    private fun loadDataFromGitee() {
        Toast.makeText(this, "正在加载攻略...", Toast.LENGTH_SHORT).show()

        NetworkClient.api.getStrategyList().enqueue(object : Callback<List<StrategyItem>> {
            override fun onResponse(
                call: Call<List<StrategyItem>>,
                response: Response<List<StrategyItem>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    fullList = data

                    // 初始显示全部
                    displayList.clear()
                    displayList.addAll(fullList)
                    adapter.notifyDataSetChanged()

                    Toast.makeText(
                        this@StrategyListActivity,
                        "加载成功，共${fullList.size}条",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@StrategyListActivity,
                        "加载失败: ${response.code()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<StrategyItem>>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(
                    this@StrategyListActivity,
                    "网络错误: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun filter(keyword: String) {
        displayList.clear()
        if (keyword.isEmpty()) {
            displayList.addAll(fullList)
        } else {
            // 简单的模糊搜索：匹配标题 或 密探
            for (item in fullList) {
                if (item.title.contains(keyword, ignoreCase = true) ||
                    item.agents.contains(keyword, ignoreCase = true)
                ) {
                    displayList.add(item)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun onItemClicked(item: StrategyItem) {
        Toast.makeText(this, "正在打开: ${item.title}", Toast.LENGTH_SHORT).show()

        // 🔴 跳转到详情页，并把 detailUrl 传过去
        val intent = Intent(this, StrategyDetailActivity::class.java)
        intent.putExtra("DETAIL_URL", item.detailUrl)
        startActivity(intent)
    }
}
