package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.yuanassist.R
import com.example.yuanassist.model.AgentRepository

class JobStationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_FILE_NAME = "extra_asset_file_name"
        private const val TURN_COLUMN_WIDTH_DP = 42f
        private const val DISC_NAME_MAX_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station)
        applyStatusBarInsets()

        val assetFileName = intent.getStringExtra(EXTRA_ASSET_FILE_NAME)
        val data = JobStationAssetRepository.loadDetail(this, assetFileName)

        bindHeaderAndContent(data)
        bindRosterCard(data)
        bindTableAndOtherActions(data)
        bindBottomBar(data)
    }

    private fun bindHeaderAndContent(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<ImageView>(R.id.btn_job_station_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_detail_title).text = data.title
        bindStageTags(data.stageTags)
        bindSourceInfo(data)

        val summaryView = findViewById<TextView>(R.id.tv_detail_summary)
        if (data.summary.isBlank() || data.summary.contains("这里放帖子正文")) {
            summaryView.visibility = View.GONE
        } else {
            summaryView.visibility = View.VISIBLE
            summaryView.text = data.summary
        }
    }

    private fun bindSourceInfo(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<TextView>(R.id.tv_detail_source_author).text = data.author
        findViewById<TextView>(R.id.tv_detail_source_type).text = data.sourceType
        findViewById<TextView>(R.id.tv_detail_original_author).text = "原作者名：${data.originalAuthor}"
        findViewById<TextView>(R.id.tv_detail_original_platform).text = "原发布平台：${data.originalPlatform}"
    }

    private fun bindStageTags(tags: List<String>) {
        val scrollView = findViewById<HorizontalScrollView>(R.id.scroll_detail_tags)
        val container = findViewById<LinearLayout>(R.id.ll_detail_tags)
        container.removeAllViews()

        if (tags.isEmpty()) {
            scrollView.visibility = View.GONE
            return
        }

        scrollView.visibility = View.VISIBLE
        tags.forEach { tag ->
            if (tag.isBlank()) return@forEach
            container.addView(createStageTagView(tag))
        }
    }

    private fun bindBottomBar(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<TextView>(R.id.tv_stats).text = "点赞 ${data.likeCount}    阅读 ${data.readCount}"

        findViewById<TextView>(R.id.btn_original_link).setOnClickListener {
            if (data.originalLink.isNotBlank()) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.originalLink)))
                }.onFailure {
                    Toast.makeText(this, "无法打开原帖链接", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "未找到原帖链接", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btn_import_script).setOnClickListener {
            Toast.makeText(this, "开始导入脚本...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindRosterCard(data: JobStationAssetRepository.JobStationDetailData) {
        val rosterContainer = findViewById<LinearLayout>(R.id.ll_roster_detail_container)
        rosterContainer.removeAllViews()

        if (data.roster.isEmpty()) {
            rosterContainer.addView(TextView(this).apply {
                text = "暂无阵容数据"
                textSize = 14f
                setTextColor(Color.parseColor("#8C7A61"))
            })
            return
        }

        data.roster.forEachIndexed { index, oper ->
            val operLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                    if (index < data.roster.lastIndex) {
                        marginEnd = dpToPx(2f)
                    }
                }
            }

            val avatarView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(44f), dpToPx(44f))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_job_station_avatar_rounded)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                loadAvatarFromAssets(oper.name)?.let { setImageDrawable(it) }
            }
            operLayout.addView(avatarView)

            operLayout.addView(TextView(this).apply {
                text = oper.name.take(3)
                textSize = 12f
                setTextColor(Color.parseColor("#2A2216"))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dpToPx(6f), 0, 0)
                gravity = Gravity.CENTER
                maxLines = 1
            })

            if (oper.starLevel > 0) {
                val starsLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(2f), 0, dpToPx(2f))
                }
                for (i in 1..5) {
                    starsLayout.addView(TextView(this).apply {
                        text = "★"
                        textSize = 10f
                        setTextColor(Color.parseColor(if (i <= oper.starLevel) "#F5B041" else "#E0DCD3"))
                        setPadding(0, 0, 0, 0)
                    })
                }
                operLayout.addView(starsLayout)
            }

            if (oper.attack > 0 || oper.hp > 0) {
                operLayout.addView(TextView(this).apply {
                    text = "${oper.attack}/${oper.hp}"
                    textSize = 10f
                    setTextColor(Color.parseColor("#857864"))
                    gravity = Gravity.CENTER
                })
            }

            if (oper.discs.any { it != 0 }) {
                val discsContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(6f), 0, 0)
                }

                oper.discs.forEach { discId ->
                    if (discId != 0) {
                        discsContainer.addView(createDiscChip(oper.name, discId))
                    }
                }
                operLayout.addView(discsContainer)
            }

            rosterContainer.addView(operLayout)
        }
    }

    private fun bindTableAndOtherActions(data: JobStationAssetRepository.JobStationDetailData) {
        val headerContainer = findViewById<LinearLayout>(R.id.ll_table_header)
        val bodyContainer = findViewById<LinearLayout>(R.id.ll_table_body)
        val otherActionsCard = findViewById<View>(R.id.card_other_actions)
        val otherActionsContainer = findViewById<LinearLayout>(R.id.ll_other_actions)

        headerContainer.removeAllViews()
        bodyContainer.removeAllViews()
        otherActionsContainer.removeAllViews()

        headerContainer.addView(createTurnHeaderCell())
        for (i in 0 until 5) {
            val opName = data.roster.getOrNull(i)?.name ?: "空"
            headerContainer.addView(createAvatarHeaderCell(opName))
        }

        if (data.turns.isEmpty()) {
            bodyContainer.addView(TextView(this).apply {
                text = "暂无可解析的动作序列"
                textSize = 14f
                setTextColor(Color.parseColor("#8C7A61"))
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(18f), 0, dpToPx(18f))
            })
            otherActionsCard.visibility = View.GONE
            return
        }

        var hasOtherActions = false
        data.turns.forEachIndexed { index, turn ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(10f), 0, dpToPx(10f))
            }

            rowLayout.addView(createTurnInfoCell(turn.turnNum))
            for (slot in 1..5) {
                rowLayout.addView(createActionsCell(turn.slotActions[slot].orEmpty()))
            }
            bodyContainer.addView(rowLayout)

            if (index < data.turns.lastIndex) {
                bodyContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    )
                    setBackgroundColor(Color.parseColor("#F2EDE1"))
                })
            }

            val otherChips = turn.slotActions[0].orEmpty()
            if (otherChips.isNotEmpty()) {
                hasOtherActions = true
                otherChips.forEach { chip ->
                    otherActionsContainer.addView(createOtherActionRow(turn.turnNum, chip))
                }
            }
        }

        otherActionsCard.visibility = if (hasOtherActions) View.VISIBLE else View.GONE
    }

    private fun createTurnHeaderCell(): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(TURN_COLUMN_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "回合"
            textSize = 11f
            setTextColor(Color.parseColor("#A3967F"))
            gravity = Gravity.CENTER
        }
    }

    private fun createAvatarHeaderCell(name: String): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            val avatarView = ImageView(this@JobStationActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(32f), dpToPx(32f))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_job_station_avatar)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                loadAvatarFromAssets(name)?.let { setImageDrawable(it) }
            }

            val nameView = TextView(this@JobStationActivity).apply {
                text = name.take(3)
                textSize = 10f
                setTextColor(Color.parseColor("#3D3222"))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dpToPx(4f), 0, 0)
                maxLines = 1
                gravity = Gravity.CENTER
            }

            addView(avatarView)
            addView(nameView)
        }
    }

    private fun createTurnInfoCell(turnNum: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(TURN_COLUMN_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
            text = turnNum.toString()
            textSize = 15f
            setTextColor(Color.parseColor("#82683A"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
    }

    private fun createActionsCell(actions: List<JobStationAssetRepository.ActionChip>): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            if (actions.isEmpty()) {
                addView(TextView(this@JobStationActivity).apply {
                    text = "-"
                    textSize = 12f
                    setTextColor(Color.parseColor("#D1C6B4"))
                })
            } else {
                actions.forEach { chipData ->
                    addView(createActionChipView(chipData))
                }
            }
        }
    }

    private fun createActionChipView(chipData: JobStationAssetRepository.ActionChip): TextView {
        val (bgColor, strokeColor, textColor) = when (chipData.type) {
            JobStationAssetRepository.ActionType.ULT ->
                Triple("#FFF2F2", "#F0C7C7", "#C94242")
            JobStationAssetRepository.ActionType.DEFEND ->
                Triple("#F2F7FF", "#C2D9F2", "#3D73A8")
            JobStationAssetRepository.ActionType.ATTACK ->
                Triple("#FFF8EB", "#EEDCA8", "#A37817")
            JobStationAssetRepository.ActionType.OTHER ->
                Triple("#F7F5F0", "#E0DCD3", "#7A7369")
        }

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2f)
                bottomMargin = dpToPx(2f)
            }
            text = chipData.label
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(textColor))
            gravity = Gravity.CENTER
            maxLines = 1
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                setStroke(dpToPx(1f), Color.parseColor(strokeColor))
                cornerRadius = dpToPx(8f).toFloat()
            }
            setPadding(dpToPx(6f), dpToPx(1f), dpToPx(6f), dpToPx(1f))
        }
    }

    private fun createOtherActionRow(
        turnNum: Int,
        chip: JobStationAssetRepository.ActionChip
    ): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(5f), 0, dpToPx(5f))

            addView(TextView(this@JobStationActivity).apply {
                text = "第${turnNum}回合 行动${chip.globalOrder}"
                textSize = 12f
                setTextColor(Color.parseColor("#857864"))
            })

            addView(TextView(this@JobStationActivity).apply {
                text = chip.label
                textSize = 13f
                setPadding(dpToPx(8f), 0, 0, 0)
                setTextColor(Color.parseColor("#3D3222"))
                setTypeface(typeface, Typeface.BOLD)
            })

            chip.actionParamMs?.let { actionParam ->
                addView(TextView(this@JobStationActivity).apply {
                    text = "${actionParam}ms"
                    textSize = 11f
                    setTextColor(Color.parseColor("#8F6A2B"))
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F8F2E5"))
                        setStroke(dpToPx(1f), Color.parseColor("#D8C18A"))
                        cornerRadius = dpToPx(999f).toFloat()
                    }
                    setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dpToPx(8f)
                    }
                })
            }
        }
    }

    private fun createDiscChip(agentName: String, discId: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(1f)
                bottomMargin = dpToPx(1f)
            }
            text = translateDiscName(agentName, discId)
            textSize = 10f
            setTextColor(Color.parseColor("#82683A"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FDF8E4"))
                setStroke(dpToPx(1f), Color.parseColor("#A8813C"))
                cornerRadius = dpToPx(6f).toFloat()
            }
            setPadding(dpToPx(6f), dpToPx(1f), dpToPx(6f), dpToPx(1f))
            maxLines = 1
        }
    }

    private fun createStageTagView(tag: String): TextView {
        val (bgColor, strokeColor, textColor) = when (tag) {
            "如鸢" -> Triple("#F8E0B8", "#C88A2C", "#8F5A11")
            "代号鸢" -> Triple("#E2E7DA", "#9AA98B", "#5D6B51")
            else -> Triple("#F8F2E5", "#D8C18A", "#7B5B17")
        }
        val background = GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            setStroke(dpToPx(1f), Color.parseColor(strokeColor))
            cornerRadius = dpToPx(999f).toFloat()
        }

        return TextView(this).apply {
            text = tag
            textSize = 12f
            setTextColor(Color.parseColor(textColor))
            this.background = background
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(6f)
            }
        }
    }

    private fun applyStatusBarInsets() {
        val statusBarSpacer = findViewById<View>(R.id.view_status_bar_spacer)

        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.layoutParams = view.layoutParams.apply {
                height = systemBars.top
            }
            insets
        }
        ViewCompat.requestApplyInsets(statusBarSpacer)
    }

    private fun translateDiscName(agentName: String, discId: Int): String {
        val normalizedName = when (agentName) {
            "酆公珠" -> "鄷公珠"
            "酆公玖" -> "鄷公玖"
            "SP史子眇" -> "SP史子渺"
            else -> agentName
        }
        val rawName = AgentRepository.AGENT_MAP[normalizedName]
            ?.talents
            ?.get(discId)
            ?.removePrefix("橙")
            ?.removePrefix("紫")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "命盘$discId"

        return if (rawName.length > DISC_NAME_MAX_LENGTH) {
            rawName.take(DISC_NAME_MAX_LENGTH)
        } else {
            rawName
        }
    }

    private fun loadAvatarFromAssets(name: String): Drawable? {
        return runCatching {
            assets.open("$name.png").use { stream ->
                Drawable.createFromStream(stream, null)
            }
        }.getOrNull() ?: runCatching {
            assets.open("$name.jpg").use { stream ->
                Drawable.createFromStream(stream, null)
            }
        }.getOrNull()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
