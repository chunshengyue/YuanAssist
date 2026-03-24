package com.example.yuanassist.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.BmobUser
import cn.bmob.v3.datatype.BmobPointer
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.strategy_favorite

class MyFavoriteActivity : AppCompatActivity() {

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StrategyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_favorite)

        val backButton = findViewById<ImageView>(R.id.btn_back_my_favorite)
        val header = findViewById<View>(R.id.layout_my_favorite_header)
        val topSpace = findViewById<View>(R.id.view_my_favorite_status_space)
        emptyView = findViewById(R.id.tv_my_favorite_empty)
        recyclerView = findViewById(R.id.rv_my_favorite)

        adapter = StrategyAdapter(emptyList()) { item ->
            startActivity(Intent(this, StrategyDetailActivity::class.java).apply {
                putExtra("STRATEGY_ID", item.objectId)
            })
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { _, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            topSpace.updateLayoutParams {
                height = statusBarTop / 2
            }
            insets
        }
        ViewCompat.requestApplyInsets(header)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadMyFavoriteStrategies()
    }

    private fun loadMyFavoriteStrategies() {
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        if (currentUser == null) {
            showEmpty("请先登录后再查看我的收藏")
            return
        }

        showEmpty("正在加载我的收藏...")

        val query = BmobQuery<strategy_favorite>()
        query.addWhereEqualTo("user", BmobPointer(currentUser))
        query.include("strategy,strategy.author")
        query.order("-updatedAt")
        query.setLimit(100)
        query.findObjects(object : FindListener<strategy_favorite>() {
            override fun done(list: MutableList<strategy_favorite>?, e: BmobException?) {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    if (e == null) {
                        val strategies = list
                            ?.mapNotNull { it.strategy }
                            ?.distinctBy { it.objectId }
                            ?: emptyList()
                        if (strategies.isEmpty()) {
                            showEmpty("你还没有收藏过攻略")
                        } else {
                            adapter.updateData(strategies)
                            recyclerView.visibility = View.VISIBLE
                            emptyView.visibility = View.GONE
                        }
                    } else {
                        showEmpty("加载失败：${e.message}")
                        Toast.makeText(
                            this@MyFavoriteActivity,
                            "我的收藏加载失败：${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun showEmpty(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }
}
