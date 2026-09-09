package com.example.yuanassist.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.DialogUtils

object ExcludedAgentsDialog {
    private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
    private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
    private val NON_DAIHAOYUAN_PREFIX_AGENTS = listOf("吕布", "曹丕")
    private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
        "赵云", "司马孚", "张松", "孙辅",
        "刘璋", "夏侯渊", "酆公珠", "酆公玖", "法正", "庞德",
        "SP陈登", "SP史子渺", "程普", "钟繇", "蒯良", "陈群",
        "卢植", "简雍", "郭女王", "周忠", "陈纪", "陈应",
    )
    private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
        "庞曦",
        "SP史子眇",
    )

    fun show(activity: AppCompatActivity, onSaved: (Int) -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) return
        val filterPrefs = activity.getSharedPreferences(PREFS_AGENT_FILTER, Context.MODE_PRIVATE)
        val defaultChecked = filterPrefs.getBoolean(KEY_SHOW_DAIHAOYUAN, false)
        val baseAgents = AgentRepository.ALL_AGENTS.filterNot {
            it in DAIHAOYUAN_HIDDEN_ALIASES || it in NON_DAIHAOYUAN_PREFIX_AGENTS
        }
        fun buildAgentList(includeDaihaoYuan: Boolean): List<String> {
            val result = LinkedHashSet<String>()
            if (includeDaihaoYuan) {
                result.addAll(DAIHAOYUAN_EXTRA_AGENTS)
                result.addAll(NON_DAIHAOYUAN_PREFIX_AGENTS)
            } else {
                result.addAll(NON_DAIHAOYUAN_PREFIX_AGENTS)
            }
            result.addAll(baseAgents)
            return result.toList()
        }

        val selectedAgents = ConfigManager.getExcludedAgents(activity).toMutableSet()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_excluded_agents, null)
        val filterRow = dialogView.findViewById<LinearLayout>(R.id.layout_excluded_agent_game_filter)
        val filterBox = dialogView.findViewById<CheckBox>(R.id.cb_excluded_agent_daihao)
        val searchInput = dialogView.findViewById<EditText>(R.id.et_excluded_agent_search)
        val countView = dialogView.findViewById<TextView>(R.id.tv_excluded_agent_count)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_excluded_agents)
        val displayAgents = buildAgentList(defaultChecked).toMutableList()
        val adapter = object : RecyclerView.Adapter<ExcludedAgentViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExcludedAgentViewHolder {
                val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_excluded_agent, parent, false)
                return ExcludedAgentViewHolder(itemView)
            }

            override fun onBindViewHolder(holder: ExcludedAgentViewHolder, position: Int) {
                val agentName = displayAgents[position]
                val selected = agentName in selectedAgents
                holder.nameView.text = agentName
                loadAgentAvatar(activity, agentName, holder.avatarView)
                holder.avatarFrame.background = createExcludedAgentBackground(activity, selected)
                holder.overlayView.setBackgroundColor(
                    Color.parseColor(if (selected) "#36000000" else "#00000000"),
                )
                holder.nameView.setTextColor(
                    Color.parseColor(if (selected) "#75322D" else "#8A6B5E"),
                )
                holder.itemView.setOnClickListener {
                    if (selectedAgents.contains(agentName)) {
                        selectedAgents.remove(agentName)
                    } else {
                        selectedAgents.add(agentName)
                    }
                    updateExcludedAgentCount(countView, selectedAgents.size)
                    notifyItemChanged(position)
                }
            }

            override fun getItemCount(): Int = displayAgents.size
        }
        recyclerView.layoutManager = GridLayoutManager(activity, 4)
        recyclerView.adapter = adapter
        updateExcludedAgentCount(countView, selectedAgents.size)
        filterBox.buttonTintList = ColorStateList.valueOf(Color.parseColor("#C79C5C"))
        filterRow.setOnClickListener { filterBox.isChecked = !filterBox.isChecked }

        val dialogTitle = TextView(activity).apply {
            text = "排除密探"
            textSize = 18f
            setPadding(0, 28, 0, 8)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#75322D"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(activity))
            .setCustomTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                ConfigManager.saveExcludedAgents(activity, selectedAgents)
                onSaved(selectedAgents.size)
                Toast.makeText(activity, "排除密探已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清空") { _, _ ->
                ConfigManager.saveExcludedAgents(activity, emptySet())
                onSaved(0)
                Toast.makeText(activity, "已清空排除密探", Toast.LENGTH_SHORT).show()
            }
            .create()
        val refreshDisplayAgents: () -> Unit = {
            val query = searchInput.text?.toString().orEmpty().trim()
            displayAgents.clear()
            displayAgents.addAll(
                buildAgentList(filterBox.isChecked).filter { agentName ->
                    query.isBlank() || agentName.contains(query, ignoreCase = true)
                },
            )
            adapter.notifyDataSetChanged()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshDisplayAgents()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        filterBox.setOnCheckedChangeListener { _, isChecked ->
            filterPrefs.edit().putBoolean(KEY_SHOW_DAIHAOYUAN, isChecked).apply()
            refreshDisplayAgents()
        }
        filterBox.isChecked = defaultChecked
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        dialog.window?.setLayout((screenWidth * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateExcludedAgentCount(view: TextView, count: Int) {
        view.text = "已选 $count 个"
    }

    private fun createExcludedAgentBackground(activity: AppCompatActivity, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 10f * activity.resources.displayMetrics.density
            setColor(Color.parseColor("#FFF7EA"))
            setStroke(
                (1.2f * activity.resources.displayMetrics.density).toInt(),
                Color.parseColor(if (selected) "#C79C5C" else "#80B57A45"),
            )
        }
    }

    private fun loadAgentAvatar(activity: AppCompatActivity, agentName: String, imageView: ImageView) {
        try {
            val bitmap = BitmapFactory.decodeStream(activity.assets.open("$agentName.png"))
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
        } catch (_: Exception) {
            imageView.setImageDrawable(null)
            imageView.visibility = View.INVISIBLE
        }
    }

    private class ExcludedAgentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarFrame: View = itemView.findViewById(R.id.layout_excluded_agent_avatar_frame)
        val avatarView: ImageView = itemView.findViewById(R.id.iv_excluded_agent_avatar)
        val overlayView: View = itemView.findViewById(R.id.view_excluded_agent_overlay)
        val nameView: TextView = itemView.findViewById(R.id.tv_excluded_agent_name)
    }
}
