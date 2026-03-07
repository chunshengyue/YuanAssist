package com.example.yuanassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException

// ==========================================
// 🔴 数据模型定义区
// ==========================================

// 1. 基础元素模型
data class DetailItem(
    val type: String,
    val content: String
)

// 2. 配置模型
data class StrategyConfig(
    val intervalAttack: Long,
    val intervalSkill: Long,
    val waitTurn: Long
)

// 🔴 3. 全新的指令模型 (替换了原来的 TargetSwitchJson)
data class InstructionJson(
    val turn: Int,
    val step: Int,
    val type: String,
    val value: Long
)

// 4. 主详情模型
data class StrategyDetail(
    val title: String,
    val items: List<DetailItem>,
    val originalPostUrl: String?,
    val scriptContent: String?,
    val config: StrategyConfig?,
    val instructions: List<InstructionJson>?,
    val agents: List<String>? // 🔴 新增這個欄位接收 JSON 陣容
)
// ==========================================
// Activity 逻辑区
// ==========================================

class StrategyDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_strategy_detail)

        val titleView: TextView = findViewById(R.id.tv_detail_title)
        val recyclerView: RecyclerView = findViewById(R.id.rv_detail)
        val btnCopyUrl: Button = findViewById(R.id.btn_copy_url)
        val btnImportScript: Button = findViewById(R.id.btn_import_script)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val detailUrl = intent.getStringExtra("DETAIL_URL")
        if (detailUrl.isNullOrEmpty()) {
            Toast.makeText(this, "攻略地址错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadDetailData(detailUrl) { detail ->
            // 在主线程更新UI
            runOnUiThread {
                titleView.text = detail.title
                recyclerView.adapter = StrategyDetailAdapter(detail.items)

                // 根据数据显示或隐藏按钮
                if (!detail.originalPostUrl.isNullOrEmpty()) {
                    btnCopyUrl.visibility = View.VISIBLE
                    btnCopyUrl.setOnClickListener {
                        copyToClipboard(detail.originalPostUrl!!)
                    }
                }

                if (!detail.scriptContent.isNullOrEmpty()) {
                    btnImportScript.visibility = View.VISIBLE
                    btnImportScript.setOnClickListener {
                        // 🔴 传入整个 detail 对象
                        importScriptToService(detail)
                    }
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Post URL", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun importScriptToService(detail: StrategyDetail) {
        val intent = Intent(this, YuanAssistService::class.java).apply {
            action = "ACTION_IMPORT_SCRIPT"

            // 1. 發送表格內容
            putExtra("SCRIPT_CONTENT", detail.scriptContent)

            // 2. 發送配置參數
            if (detail.config != null) {
                putExtra("CONFIG_JSON", Gson().toJson(detail.config))
            }

            // 3. 發送全新的指令清單
            if (!detail.instructions.isNullOrEmpty()) {
                putExtra("INSTRUCTIONS_JSON", Gson().toJson(detail.instructions))
            }

            // 🔴 4. 新增：發送角色陣容
            if (!detail.agents.isNullOrEmpty()) {
                putExtra("AGENTS_JSON", Gson().toJson(detail.agents))
            }
        }

        startService(intent)
        Toast.makeText(this, "脚本及附带指令已发送至悬浮窗", Toast.LENGTH_LONG).show()
        // finish() // 取决于你导入后想不想关掉这个详情页，想关就保留
    }

    private fun loadDetailData(url: String, callback: (StrategyDetail) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@StrategyDetailActivity,
                        "加载详情失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val detail = Gson().fromJson(body, StrategyDetail::class.java)
                            callback(detail)
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@StrategyDetailActivity,
                                    "JSON解析错误",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@StrategyDetailActivity,
                            "加载详情失败: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}