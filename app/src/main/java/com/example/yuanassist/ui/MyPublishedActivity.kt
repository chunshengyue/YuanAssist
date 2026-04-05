package com.example.yuanassist.ui

import android.app.AlertDialog
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
import cn.bmob.v3.listener.UpdateListener
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.strategy_detail

class MyPublishedActivity : AppCompatActivity() {

    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyPublishedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_published)

        val backButton = findViewById<ImageView>(R.id.btn_back_my_published)
        val header = findViewById<View>(R.id.layout_my_published_header)
        val topSpace = findViewById<View>(R.id.view_my_published_status_space)
        emptyView = findViewById(R.id.tv_my_published_empty)
        recyclerView = findViewById(R.id.rv_my_published)

        adapter = MyPublishedAdapter(
            emptyList(),
            onCardClick = { item ->
                val intent = Intent(this, JobStationActivity::class.java)
                intent.putExtra(JobStationActivity.EXTRA_STRATEGY_ID, item.objectId)
                startActivity(intent)
            },
            onEditClick = { item ->
                val intent = Intent(this, UploadStrategyActivity::class.java).apply {
                    putExtra(UploadStrategyActivity.EXTRA_IS_EDIT_MODE, true)
                    putExtra(UploadStrategyActivity.EXTRA_STRATEGY_ID, item.objectId)
                }
                startActivity(intent)
            },
            onDeleteClick = { item ->
                showDeleteDialog(item)
            }
        )

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
    }

    override fun onResume() {
        super.onResume()
        loadMyPublishedStrategies()
    }

    private fun loadMyPublishedStrategies() {
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        if (currentUser == null) {
            showEmpty("请先登录后再查看我的发布")
            return
        }

        showEmpty("正在加载我的发布...")

        val query = BmobQuery<strategy_detail>()
        query.addWhereEqualTo("author", BmobPointer(currentUser))
        query.include("author")
        query.order("-updatedAt")
        query.setLimit(100)
        query.findObjects(object : FindListener<strategy_detail>() {
            override fun done(list: MutableList<strategy_detail>?, e: BmobException?) {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    if (e == null) {
                        val result = list ?: mutableListOf()
                        if (result.isEmpty()) {
                            showEmpty("你还没有发布过攻略")
                        } else {
                            adapter.updateData(result)
                            recyclerView.visibility = View.VISIBLE
                            emptyView.visibility = View.GONE
                        }
                    } else {
                        showEmpty("加载失败：${e.message}")
                        Toast.makeText(
                            this@MyPublishedActivity,
                            "我的发布加载失败：${e.message}",
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

    private fun showDeleteDialog(item: strategy_detail) {
        AlertDialog.Builder(this)
            .setTitle("删除攻略")
            .setMessage("确认删除《${item.title.ifBlank { "未命名攻略" }}》吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                deleteStrategy(item)
            }
            .show()
    }

    private fun deleteStrategy(item: strategy_detail) {
        if (item.objectId.isNullOrBlank()) {
            Toast.makeText(this, "缺少攻略ID，无法删除", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在删除攻略...", Toast.LENGTH_SHORT).show()
        strategy_detail().apply {
            objectId = item.objectId
        }.delete(object : UpdateListener() {
            override fun done(e: BmobException?) {
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    if (e == null) {
                        Toast.makeText(this@MyPublishedActivity, "删除成功", Toast.LENGTH_SHORT).show()
                        loadMyPublishedStrategies()
                    } else {
                        Toast.makeText(
                            this@MyPublishedActivity,
                            "删除失败：${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }
}
