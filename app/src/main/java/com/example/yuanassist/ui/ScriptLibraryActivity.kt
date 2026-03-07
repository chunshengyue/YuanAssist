package com.example.yuanassist.ui

import android.R
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.core.LocalScriptJson
import com.google.gson.Gson
import java.io.File
import kotlin.collections.forEach

class ScriptLibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = "📁 本地腳本庫"
            textSize = 24f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)

        val listView = ListView(this)
        layout.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(layout)

        // 讀取檔案
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val fileNames = files.map { it.name.replace(".json", "") }

        val adapter = ArrayAdapter(this, R.layout.simple_list_item_1, fileNames)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            showScriptPreviewDialog(files[position])
        }
    }

    private fun showScriptPreviewDialog(file: File) {
        try {
            val jsonStr = file.readText()
            val scriptObj = Gson().fromJson(jsonStr, LocalScriptJson::class.java)

            val scrollContainer = ScrollView(this)
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            // 1. 動態繪製表格 (超輕量)
            val tableLayout = TableLayout(this).apply {
                setBackgroundColor(Color.LTGRAY)
                setPadding(2, 2, 2, 2) // 外邊框
            }

            val lines = scriptObj.scriptContent.split("\n").filter { it.trim().isNotEmpty() }
            for (line in lines) {
                val tableRow = TableRow(this)
                val parts = line.trim().split(Regex("\\s+"))
                for (part in parts) {
                    val tv = TextView(this).apply {
                        text = if (part == "-") "" else part
                        textSize = 14f
                        setTextColor(Color.BLACK)
                        gravity = Gravity.CENTER
                        setPadding(20, 20, 20, 20)
                        setBackgroundColor(Color.WHITE) // 單元格底色
                        layoutParams = TableRow.LayoutParams(
                            TableRow.LayoutParams.WRAP_CONTENT,
                            TableRow.LayoutParams.MATCH_PARENT
                        ).apply {
                            setMargins(1, 1, 1, 1) // 內部分隔線
                        }
                    }
                    tableRow.addView(tv)
                }
                tableLayout.addView(tableRow)
            }
            rootLayout.addView(tableLayout)

            val insTitle = TextView(this).apply {
                text = "\n📌 附帶指令："; textSize = 16f; setTextColor(Color.parseColor("#C0392B"))
            }
            rootLayout.addView(insTitle)

            if (scriptObj.instructions.isNullOrEmpty()) {
                rootLayout.addView(TextView(this).apply {
                    text = "無附加指令"; setTextColor(Color.GRAY)
                })
            } else {
                scriptObj.instructions.forEach { ins ->
                    val desc =
                        "T${ins.turn} " + (if (ins.step == 0) "整回合" else "動作${ins.step}後") +
                                " -> ${ins.type} (${ins.value})"
                    rootLayout.addView(TextView(this).apply {
                        text = desc; setTextColor(Color.BLACK); setPadding(0, 10, 0, 0)
                    })
                }
            }

            scrollContainer.addView(rootLayout)

            AlertDialog.Builder(this)
                .setTitle(scriptObj.title ?: file.name)
                .setView(scrollContainer)
                .setPositiveButton("關閉", null)
                .setNegativeButton("刪除腳本") { _, _ ->
                    file.delete()
                    recreate() // 刷新列表
                    Toast.makeText(this, "已刪除", Toast.LENGTH_SHORT).show()
                }
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "讀取失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}