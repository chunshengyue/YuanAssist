package com.example.yuanassist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
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
import com.bumptech.glide.Glide
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.QueryListener
import cn.bmob.v3.BmobQuery
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.R
import com.example.yuanassist.model.strategy_detail

class JobStationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COPILOT_ID = "extra_copilot_id"
        const val EXTRA_STRATEGY_ID = "extra_strategy_id"
        const val EXTRA_ASSET_FILE_NAME = "extra_asset_file_name"
        private const val MAA_YUAN_HOME_URL = "https://maayuan.top/"
        private const val MAA_YUAN_SHARE_URL = "https://share.maayuan.top/"
        private const val TURN_COLUMN_WIDTH_DP = 42f
        private const val DISC_NAME_MAX_LENGTH = 6
        private const val DISC_CHIP_WIDTH_DP = 60f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station)
        applyStatusBarInsets()
        findViewById<ImageView>(R.id.btn_job_station_back).setOnClickListener { finish() }

        val copilotId = intent.getLongExtra(EXTRA_COPILOT_ID, -1L)
        val strategyId = intent.getStringExtra(EXTRA_STRATEGY_ID).orEmpty()

        renderLoadingState()
        when {
            copilotId > 0L -> loadMaaYuanDetail(copilotId)
            strategyId.isNotBlank() -> loadBmobDetail(strategyId)
            else -> {
                Toast.makeText(this, "缺少攻略 id", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun bindHeaderAndContent(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<TextView>(R.id.tv_detail_title).text = data.title
        bindStageTags(data.stageTags)
        bindSourceInfo(data)
        bindStrategyImage(data)
        bindMaaYuanNotice(data)

        val summaryView = findViewById<TextView>(R.id.tv_detail_summary)
        if (data.summary.isBlank() || data.summary.contains("这里放帖子正文")) {
            summaryView.visibility = View.GONE
        } else {
            summaryView.visibility = View.VISIBLE
            summaryView.text = data.summary
        }
    }

    private fun renderLoadingState() {
        findViewById<TextView>(R.id.tv_detail_title).text = "加载中..."
        findViewById<TextView>(R.id.tv_detail_summary).apply {
            visibility = View.VISIBLE
            text = "正在拉取作业详情"
        }
        findViewById<ImageView>(R.id.iv_detail_source_author_avatar).apply {
            visibility = View.GONE
            Glide.with(this@JobStationActivity).clear(this)
        }
        findViewById<TextView>(R.id.tv_detail_source_author).text = ""
        findViewById<TextView>(R.id.tv_detail_source_type).text = ""
        findViewById<TextView>(R.id.tv_detail_original_author).text = ""
        findViewById<TextView>(R.id.tv_detail_original_platform).text = ""
        findViewById<View>(R.id.layout_image_container).visibility = View.GONE
        findViewById<View>(R.id.tv_image_section_title).visibility = View.GONE
        findViewById<View>(R.id.tv_detail_strategy_image_label).visibility = View.GONE
        findViewById<View>(R.id.iv_detail_strategy_image).visibility = View.GONE
        findViewById<View>(R.id.tv_detail_agent_image_label).visibility = View.GONE
        findViewById<View>(R.id.iv_detail_agent_image).visibility = View.GONE
        findViewById<View>(R.id.card_maayuan_notice).visibility = View.GONE
    }

    private fun loadMaaYuanDetail(copilotId: Long) {
        JobStationRemoteRepository.loadDetail(
            copilotId = copilotId,
            onSuccess = { data ->
                bindHeaderAndContent(data)
                bindRosterCard(data)
                bindTableAndOtherActions(data)
                bindBottomBar(data)
            },
            onError = { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun loadBmobDetail(strategyId: String) {
        val query = BmobQuery<strategy_detail>()
        query.include("author")
        query.getObject(strategyId, object : QueryListener<strategy_detail>() {
            override fun done(detail: strategy_detail?, e: BmobException?) {
                runOnUiThread {
                    if (e != null || detail == null) {
                        Toast.makeText(
                            this@JobStationActivity,
                            "攻略详情加载失败: ${e?.message ?: "未找到该攻略"}",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@runOnUiThread
                    }

                    val data = JobStationAssetRepository.fromBmobDetailData(detail)
                    bindHeaderAndContent(data)
                    bindRosterCard(data)
                    bindTableAndOtherActions(data)
                    bindBottomBar(data)
                }
            }
        })
    }

    private fun bindSourceInfo(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<ImageView>(R.id.iv_detail_source_author_avatar).apply {
            if (data.authorAvatarUrl.isBlank()) {
                visibility = View.GONE
                Glide.with(this@JobStationActivity).clear(this)
            } else {
                visibility = View.VISIBLE
                Glide.with(this@JobStationActivity)
                    .load(data.authorAvatarUrl)
                    .placeholder(R.drawable.cover)
                    .error(R.drawable.cover)
                    .circleCrop()
                    .into(this)
            }
        }
        findViewById<TextView>(R.id.tv_detail_source_author).text = data.author
        findViewById<TextView>(R.id.tv_detail_source_type).apply {
            text = data.sourceType
            visibility = if (data.sourceType.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.tv_detail_original_author).apply {
            if (data.originalAuthor.isBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = "原作者名：${data.originalAuthor}"
            }
        }
        findViewById<TextView>(R.id.tv_detail_original_platform).apply {
            if (data.originalPlatform.isBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = "原发布平台：${data.originalPlatform}"
            }
        }
    }

    private fun bindStrategyImage(data: JobStationAssetRepository.JobStationDetailData) {
        val imageTitle = findViewById<TextView>(R.id.tv_image_section_title)
        val imageContainer = findViewById<View>(R.id.layout_image_container)
        val strategyLabel = findViewById<TextView>(R.id.tv_detail_strategy_image_label)
        val strategyImageView = findViewById<ImageView>(R.id.iv_detail_strategy_image)
        val agentLabel = findViewById<TextView>(R.id.tv_detail_agent_image_label)
        val agentImageView = findViewById<ImageView>(R.id.iv_detail_agent_image)
        val strategyImageUrl = data.strategyImageUrl.trim()
        val agentImageUrl = data.agentImageUrl.trim()
        val hasStrategyImage = strategyImageUrl.isNotBlank()
        val hasAgentImage = agentImageUrl.isNotBlank()

        if (!hasStrategyImage && !hasAgentImage) {
            imageTitle.visibility = View.GONE
            imageContainer.visibility = View.GONE
            strategyLabel.visibility = View.GONE
            strategyImageView.visibility = View.GONE
            agentLabel.visibility = View.GONE
            agentImageView.visibility = View.GONE
            Glide.with(this).clear(strategyImageView)
            Glide.with(this).clear(agentImageView)
            return
        }

        imageTitle.visibility = View.VISIBLE
        imageContainer.visibility = View.VISIBLE

        if (hasStrategyImage) {
            strategyLabel.visibility = View.VISIBLE
            strategyImageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(strategyImageUrl)
                .placeholder(R.drawable.cover)
                .error(R.drawable.cover)
                .into(strategyImageView)
        } else {
            strategyLabel.visibility = View.GONE
            strategyImageView.visibility = View.GONE
            Glide.with(this).clear(strategyImageView)
        }

        if (hasAgentImage) {
            agentLabel.visibility = View.VISIBLE
            agentImageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(agentImageUrl)
                .placeholder(R.drawable.cover)
                .error(R.drawable.cover)
                .into(agentImageView)
        } else {
            agentLabel.visibility = View.GONE
            agentImageView.visibility = View.GONE
            Glide.with(this).clear(agentImageView)
        }
    }

    private fun bindMaaYuanNotice(data: JobStationAssetRepository.JobStationDetailData) {
        val noticeCard = findViewById<View>(R.id.card_maayuan_notice)
        if (!data.isFromMaaYuan) {
            noticeCard.visibility = View.GONE
            return
        }

        noticeCard.visibility = View.VISIBLE
        findViewById<TextView>(R.id.btn_maayuan_home).setOnClickListener {
            openExternalLink(MAA_YUAN_HOME_URL, "无法打开 MaaYuan 主页")
        }
        findViewById<TextView>(R.id.btn_maayuan_share).setOnClickListener {
            openExternalLink(MAA_YUAN_SHARE_URL, "无法打开 MaaYuan Share")
        }
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
                openExternalLink(data.originalLink, "无法打开原帖链接")
            } else {
                Toast.makeText(this, "未找到原帖链接", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btn_import_script).setOnClickListener {
            val payload = data.importPayload
            if (payload == null) {
                Toast.makeText(this, "当前作业没有可导入的战斗脚本", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            importScriptToService(payload)
            Toast.makeText(this, payload.notice, Toast.LENGTH_SHORT).show()
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

        val hasAnyStarLevel = data.roster.any { it.starLevel > 0 }
        val hasAnyAttackOrHp = data.roster.any { it.attack > 0 || it.hp > 0 }

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

            if (hasAnyStarLevel) {
                val starsLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(2f), 0, dpToPx(2f))
                    visibility = if (oper.starLevel > 0) View.VISIBLE else View.INVISIBLE
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

            if (hasAnyAttackOrHp) {
                operLayout.addView(TextView(this).apply {
                    text = "${oper.attack}/${oper.hp}"
                    textSize = 10f
                    setTextColor(Color.parseColor("#857864"))
                    gravity = Gravity.CENTER
                    visibility = if (oper.attack > 0 || oper.hp > 0) View.VISIBLE else View.INVISIBLE
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
        val discSpec = JobStationAssetRepository.resolveMaaDiscDisplaySpec(this, agentName, discId)
        val (bgColor, strokeColor, textColor, displayName) = when {
            discSpec.forbidden -> listOf(
                "#FFF3F3",
                "#D37B7B",
                "#A24D4D",
                "×${discSpec.displayName.take((DISC_NAME_MAX_LENGTH - 1).coerceAtLeast(1))}"
            )
            discSpec.color == "金" -> listOf(
                "#FDF8E4",
                "#A8813C",
                "#82683A",
                discSpec.displayName
            )
            discSpec.color == "紫" -> listOf(
                "#F6F0FB",
                "#B39ACF",
                "#7A5A9C",
                discSpec.displayName
            )
            discSpec.color == "蓝" -> listOf(
                "#EFF6FC",
                "#97B6D6",
                "#587FA6",
                discSpec.displayName
            )
            else -> listOf(
                "#EFF6FC",
                "#97B6D6",
                "#587FA6",
                discSpec.displayName
            )
        }

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(DISC_CHIP_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(1f)
                bottomMargin = dpToPx(1f)
            }
            text = displayName.take(DISC_NAME_MAX_LENGTH)
            textSize = 10f
            setTextColor(Color.parseColor(textColor))
            paintFlags = if (discSpec.forbidden) {
                paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                setStroke(dpToPx(1f), Color.parseColor(strokeColor))
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

    private fun importScriptToService(payload: JobStationAssetRepository.JobStationImportPayload) {
        val intent = Intent(this, YuanAssistService::class.java).apply {
            action = "ACTION_IMPORT_SCRIPT"
            putExtra("SCRIPT_CONTENT", payload.scriptContent)
            if (payload.configJson.isNotBlank()) putExtra("CONFIG_JSON", payload.configJson)
            if (payload.instructionsJson.isNotBlank()) putExtra("INSTRUCTIONS_JSON", payload.instructionsJson)
            if (payload.agentsJson.isNotBlank()) putExtra("AGENTS_JSON", payload.agentsJson)
        }
        startService(intent)
    }

    private fun openExternalLink(url: String, failureMessage: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
