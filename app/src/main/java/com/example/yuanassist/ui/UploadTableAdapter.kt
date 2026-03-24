package com.example.yuanassist.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R

// 定義上傳表格專用的資料結構，包含備註
data class UploadTurnItem(
    val turnNum: Int,
    val actions: List<String>, // 長度為 5 的動作列表
    var remark: String = ""
)

class UploadTableAdapter(private val dataList: List<UploadTurnItem>) :
    RecyclerView.Adapter<UploadTableAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTurnNum: TextView = view.findViewById(R.id.tv_upload_turn_num)
        val tvActs = listOf<TextView>(
            view.findViewById(R.id.tv_up_act_1),
            view.findViewById(R.id.tv_up_act_2),
            view.findViewById(R.id.tv_up_act_3),
            view.findViewById(R.id.tv_up_act_4),
            view.findViewById(R.id.tv_up_act_5)
        )
        val etRemark: EditText = view.findViewById(R.id.et_upload_remark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_upload_table_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        holder.tvTurnNum.text = item.turnNum.toString()

        // 渲染 5 個動作，如果是 "-" 或空，顯示灰色
        for (i in 0 until 5) {
            val act = item.actions.getOrNull(i) ?: ""
            holder.tvActs[i].text = if (act.isEmpty()) "-" else act
            if (act.isEmpty() || act == "-") {
                holder.tvActs[i].setTextColor(android.graphics.Color.parseColor("#666666"))
            } else {
                holder.tvActs[i].setTextColor(android.graphics.Color.parseColor("#E0E0E0"))
            }
        }

        // 處理備註輸入防錯亂
        holder.etRemark.setOnFocusChangeListener { _, _ -> }
        holder.etRemark.removeTextChangedListener(holder.etRemark.tag as? TextWatcher)
        holder.etRemark.setText(item.remark)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                item.remark = s.toString()
            }
        }
        holder.etRemark.addTextChangedListener(watcher)
        holder.etRemark.tag = watcher
    }
    fun getDataList(): List<UploadTurnItem> = dataList
    override fun getItemCount() = dataList.size
}