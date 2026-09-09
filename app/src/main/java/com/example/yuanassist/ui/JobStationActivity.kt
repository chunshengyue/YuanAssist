package com.example.yuanassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.util.Patterns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.strategy_comment
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.network.FavoriteState
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.SupabaseTimeFormatter
import retrofit2.Call

class JobStationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COPILOT_ID = "extra_copilot_id"
        const val EXTRA_STRATEGY_ID = "extra_strategy_id"
        const val EXTRA_ASSET_FILE_NAME = "extra_asset_file_name"
        private const val PREFS_STRATEGY_STATS = "strategy_detail_stats"
        private const val KEY_LAST_VIEW_PREFIX = "last_view_"
        private const val VIEW_THROTTLE_WINDOW_MS = 10 * 60 * 1000L
        private const val MAA_YUAN_HOME_URL = "https://maayuan.com/"
        private const val MAA_YUAN_SHARE_URL = "https://share.maayuan.top/"
        private const val TURN_COLUMN_WIDTH_DP = 42f
        private const val DISC_NAME_MAX_LENGTH = 6
        private const val DISC_CHIP_WIDTH_DP = 60f
    }

    private var currentDetailCall: Call<*>? = null
    private var detailRequestVersion = 0
    private lateinit var statsPrefs: SharedPreferences
    private var currentStrategyDetail: strategy_detail? = null
    private var isFavorited = false
    private var favoriteInFlight = false
    private var favoriteObjectId: String? = null
    private var commentsRequestVersion = 0
    private var commentSubmitInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station)
        statsPrefs = getSharedPreferences(PREFS_STRATEGY_STATS, MODE_PRIVATE)
        applyStatusBarInsets()
        findViewById<ImageView>(R.id.btn_job_station_back).setOnClickListener { finish() }

        val copilotId = intent.getLongExtra(EXTRA_COPILOT_ID, -1L)
        val strategyId = intent.getStringExtra(EXTRA_STRATEGY_ID).orEmpty()

        RunLogger.i(module = "作业站", section = "详情页", message = "打开详情：copilotId=$copilotId strategyId=${strategyId.ifBlank { "empty" }}")

        renderLoadingState()
        when {
            copilotId > 0L -> loadMaaYuanDetail(copilotId)
            strategyId.isNotBlank() -> loadStrategyDetail(strategyId)
            else -> {
                Toast.makeText(this, "缺少攻略 id", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        currentDetailCall?.cancel()
        currentDetailCall = null
        detailRequestVersion += 1
        super.onDestroy()
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
        currentStrategyDetail = null
        favoriteObjectId = null
        favoriteInFlight = false
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
        findViewById<View>(R.id.card_comments).visibility = View.GONE
        findViewById<TextView>(R.id.btn_detail_original_link).visibility = View.GONE
        findViewById<TextView>(R.id.btn_favorite).visibility = View.GONE
        updateFavoriteUi(false, 0)
    }

    private fun loadMaaYuanDetail(copilotId: Long) {
        currentStrategyDetail = null
        favoriteObjectId = null
        commentsRequestVersion += 1
        currentDetailCall?.cancel()
        val requestVersion = ++detailRequestVersion
        RunLogger.i(module = "作业站", section = "详情页", message = "加载 MaaYuan：copilotId=$copilotId")
        currentDetailCall = JobStationRemoteRepository.loadDetail(
            copilotId = copilotId,
            onSuccess = { data ->
                if (requestVersion != detailRequestVersion || isFinishing || isDestroyed) return@loadDetail
                currentDetailCall = null
                renderDetailSafely(
                    source = "MaaYuan",
                    detailKey = "copilotId=$copilotId",
                    data = data
                )
            },
            onError = { message ->
                if (requestVersion != detailRequestVersion || isFinishing || isDestroyed) return@loadDetail
                currentDetailCall = null
                RunLogger.e(module = "作业站", section = "详情页", message = "MaaYuan 加载失败：$message")
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun loadStrategyDetail(strategyId: String) {
        SupabaseRepository.getStrategyDetail(
            context = this,
            strategyId = strategyId,
            onSuccess = { detail ->
                if (isFinishing || isDestroyed) return@getStrategyDetail
                currentStrategyDetail = detail
                favoriteObjectId = null
                updateFavoriteUi(false, detail.favoriteCount ?: 0)
                val shouldIncreaseViewCount = recordStrategyViewIfNeeded(detail)
                if (shouldIncreaseViewCount) {
                    detail.viewCount = (detail.viewCount ?: 0) + 1
                }
                val data = JobStationAssetRepository.fromCommunityDetailData(detail)
                renderDetailSafely(
                    source = "Supabase",
                    detailKey = "strategyId=$strategyId",
                    data = data
                )
                syncFavoriteState(detail)
                loadStrategyComments(strategyId)
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@getStrategyDetail
                Toast.makeText(this, "攻略详情加载失败: $message", Toast.LENGTH_SHORT).show()
                finish()
            },
        )
    }

    private fun renderDetailSafely(
        source: String,
        detailKey: String,
        data: JobStationAssetRepository.JobStationDetailData
    ) {
        RunLogger.i(module = "作业站", section = "详情页", message = "$source 渲染：${data.title.take(40)}，阵容=${data.roster.size}，回合=${data.turns.size}")
        try {
            bindHeaderAndContent(data)
            bindRosterCard(data)
            bindTableAndOtherActions(data)
            bindBottomBar(data)
            RunLogger.i(module = "作业站", section = "详情页", message = "$source 渲染完成")
        } catch (t: Throwable) {
            RunLogger.e(module = "作业站", section = "详情页", message = "$source 渲染异常：$detailKey", throwable = t)
            showRenderErrorState(source, detailKey, t)
        }
    }

    private fun showRenderErrorState(source: String, detailKey: String, throwable: Throwable) {
        findViewById<TextView>(R.id.tv_detail_title).text = "详情渲染异常"
        findViewById<TextView>(R.id.tv_detail_summary).apply {
            visibility = View.VISIBLE
            text = "$source 详情渲染异常，请打开运行日志并反馈。\n$detailKey\n${throwable.javaClass.simpleName}: ${throwable.message ?: "无错误信息"}"
        }
        Toast.makeText(this, "详情渲染异常，请打开运行日志", Toast.LENGTH_LONG).show()
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
                .signature(ObjectKey(JobStationAssetRepository.IMAGE_CACHE_SIGNATURE))
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
                .signature(ObjectKey(JobStationAssetRepository.IMAGE_CACHE_SIGNATURE))
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
        findViewById<TextView>(R.id.tv_stats).text = "收藏 ${data.likeCount}    阅读 ${data.readCount}"

        findViewById<TextView>(R.id.btn_detail_original_link).apply {
            if (data.originalLink.isBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = if (data.isFromMaaYuan) "原帖链接" else "复制链接"
                setOnClickListener {
                    if (data.isFromMaaYuan) {
                        openExternalLink(data.originalLink, "无法打开原帖链接")
                    } else {
                        copyLinkToClipboard(data.originalLink)
                    }
                }
            }
        }

        findViewById<TextView>(R.id.btn_favorite).apply {
            visibility = if (currentStrategyDetail != null) View.VISIBLE else View.GONE
            setOnClickListener { handleFavoriteClick() }
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

        findViewById<TextView>(R.id.btn_write_comment).setOnClickListener {
            showCommentInputDialog()
        }
    }

    private fun handleFavoriteClick() {
        val detail = currentStrategyDetail ?: return
        if (favoriteInFlight) return
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            showFavoriteLoginDialog()
            return
        }
        if (isFavorited) {
            removeFavorite(detail, currentUser)
        } else {
            addFavorite(detail, currentUser, false)
        }
    }

    private fun syncFavoriteState(detail: strategy_detail) {
        val strategyId = detail.objectId ?: return
        val currentUser = SupabaseRepository.getCurrentUser(this) ?: run {
            favoriteObjectId = null
            updateFavoriteUi(false, detail.favoriteCount ?: 0)
            return
        }
        SupabaseRepository.getFavoriteState(
            context = this,
            strategyId = strategyId,
            onSuccess = { state ->
                if (currentStrategyDetail?.objectId != strategyId) return@getFavoriteState
                favoriteObjectId = state.favoriteObjectId.takeIf { it.isNotBlank() }
                currentStrategyDetail?.favoriteCount = state.favoriteCount
                updateFavoriteUi(state.favorited, state.favoriteCount)
                refreshBottomStats()
            },
            onError = {
                if (currentUser.objectId != null) {
                    favoriteObjectId = null
                    updateFavoriteUi(false, currentStrategyDetail?.favoriteCount ?: 0)
                }
            },
        )
    }

    private fun showFavoriteLoginDialog() {
        val detail = currentStrategyDetail ?: return
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("登录后即可收藏")
            .setMessage("收藏的攻略会出现在“我的收藏”里。")
            .setNegativeButton("暂不", null)
            .setPositiveButton("一键登录并收藏", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                performOneClickLogin(
                    onSuccess = {
                        runOnUiThread {
                            dialog.dismiss()
                            addFavorite(detail, it, true)
                        }
                    },
                    onError = {
                        runOnUiThread { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                    }
                )
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun performOneClickLogin(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser != null) {
            onSuccess(currentUser)
            return
        }
        SupabaseRepository.loginWithDevice(
            context = this,
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    private fun addFavorite(detail: strategy_detail, user: MyUser, fromLogin: Boolean) {
        val strategyId = detail.objectId ?: return
        favoriteInFlight = true
        SupabaseRepository.setFavorite(
            context = this,
            strategyId = strategyId,
            favorited = true,
            onSuccess = { state: FavoriteState ->
                favoriteInFlight = false
                favoriteObjectId = state.favoriteObjectId.takeIf { it.isNotBlank() }
                currentStrategyDetail?.favoriteCount = state.favoriteCount
                updateFavoriteUi(true, state.favoriteCount)
                refreshBottomStats()
                Toast.makeText(
                    this,
                    if (fromLogin) "登录成功，已加入收藏" else "已收藏",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onError = { message ->
                favoriteInFlight = false
                Toast.makeText(this, "收藏失败: $message", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun removeFavorite(detail: strategy_detail, user: MyUser) {
        val strategyId = detail.objectId ?: return
        favoriteInFlight = true
        SupabaseRepository.setFavorite(
            context = this,
            strategyId = strategyId,
            favorited = false,
            onSuccess = { state: FavoriteState ->
                favoriteInFlight = false
                favoriteObjectId = state.favoriteObjectId.takeIf { it.isNotBlank() }
                currentStrategyDetail?.favoriteCount = state.favoriteCount
                updateFavoriteUi(false, state.favoriteCount)
                refreshBottomStats()
                Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show()
            },
            onError = { message ->
                favoriteInFlight = false
                Toast.makeText(this, "取消收藏失败: $message", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun updateFavoriteUi(favorited: Boolean, count: Int) {
        isFavorited = favorited
        findViewById<TextView>(R.id.btn_favorite).apply {
            text = if (favorited) "已收藏" else "收藏"
            if (favorited) {
                setBackgroundResource(R.drawable.bg_job_station_chip)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B89B62"))
                setTextColor(Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_job_station_icon_button)
                backgroundTintList = null
                setTextColor(Color.parseColor("#82683A"))
            }
        }
    }

    private fun refreshBottomStats() {
        val detail = currentStrategyDetail ?: return
        val data = JobStationAssetRepository.fromCommunityDetailData(detail)
        findViewById<TextView>(R.id.tv_stats).text = "收藏 ${data.likeCount}    阅读 ${data.readCount}"
    }

    private fun loadStrategyComments(strategyId: String) {
        val requestVersion = ++commentsRequestVersion
        showCommentsLoadingState()
        SupabaseRepository.listComments(
            strategyId = strategyId,
            onSuccess = { list ->
                if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@listComments
                renderComments(list)
            },
            onError = { message ->
                if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@listComments
                showCommentsErrorState(message.ifBlank { "评论加载失败" })
            },
        )
    }

    private fun showCommentsLoadingState() {
        findViewById<View>(R.id.card_comments).visibility = if (currentStrategyDetail != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tv_comments_title).text = "评论"
        findViewById<TextView>(R.id.tv_comments_hint).apply {
            visibility = View.VISIBLE
            text = "正在加载评论..."
        }
        findViewById<LinearLayout>(R.id.ll_comments_container).removeAllViews()
    }

    private fun showCommentsErrorState(message: String) {
        findViewById<View>(R.id.card_comments).visibility = if (currentStrategyDetail != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tv_comments_title).text = "评论"
        findViewById<TextView>(R.id.tv_comments_hint).apply {
            visibility = View.VISIBLE
            text = "评论加载失败：$message"
        }
        findViewById<LinearLayout>(R.id.ll_comments_container).removeAllViews()
    }

    private fun renderComments(comments: List<strategy_comment>) {
        findViewById<View>(R.id.card_comments).visibility = if (currentStrategyDetail != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tv_comments_title).text = "评论 ${comments.size}"
        val hintView = findViewById<TextView>(R.id.tv_comments_hint)
        val container = findViewById<LinearLayout>(R.id.ll_comments_container)
        container.removeAllViews()

        if (comments.isEmpty()) {
            hintView.visibility = View.VISIBLE
            hintView.text = "还没有评论，来抢个沙发吧。"
            return
        }

        hintView.visibility = View.GONE
        comments.forEachIndexed { index, comment ->
            container.addView(createCommentItemView(comment))
            if (index < comments.lastIndex) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    ).apply {
                        topMargin = dpToPx(12f)
                    }
                    setBackgroundColor(Color.parseColor("#F2EDE1"))
                })
            }
        }
    }

    private fun createCommentItemView(comment: strategy_comment): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val headerLayout = LinearLayout(this@JobStationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatarView = ImageView(this@JobStationActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(30f), dpToPx(30f))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_job_station_avatar)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            val avatarUrl = comment.user?.avatarUrl.orEmpty()
            if (avatarUrl.isBlank()) {
                avatarView.setImageResource(R.drawable.cover)
            } else {
                Glide.with(this@JobStationActivity)
                    .load(avatarUrl)
                    .placeholder(R.drawable.cover)
                    .error(R.drawable.cover)
                    .circleCrop()
                    .into(avatarView)
            }
            headerLayout.addView(avatarView)

            headerLayout.addView(LinearLayout(this@JobStationActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(10f)
                }

                addView(TextView(this@JobStationActivity).apply {
                    text = resolveUserDisplayName(comment.user)
                    textSize = 14f
                    setTextColor(Color.parseColor("#2F261B"))
                    setTypeface(typeface, Typeface.BOLD)
                })

                addView(TextView(this@JobStationActivity).apply {
                    text = SupabaseTimeFormatter.formatToBeijing(comment.createdAt)
                    textSize = 11f
                    setTextColor(Color.parseColor("#9C8E77"))
                })
            })

            headerLayout.addView(LinearLayout(this@JobStationActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(createCommentActionButton("回复") {
                    showCommentInputDialog(comment)
                })

                if (isOwnComment(comment)) {
                    addView(createCommentActionButton("删除") {
                        showDeleteCommentDialog(comment)
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = dpToPx(8f)
                        }
                    })
                }
            })

            addView(headerLayout)

            addView(TextView(this@JobStationActivity).apply {
                text = buildCommentContent(comment)
                textSize = 14f
                setLineSpacing(dpToPx(2f).toFloat(), 1f)
                setTextColor(Color.parseColor("#524634"))
                setPadding(0, dpToPx(10f), 0, 0)
            })
        }
    }

    private fun createCommentActionButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#8F6A2B"))
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8F2E5"))
                setStroke(dpToPx(1f), Color.parseColor("#D8C18A"))
                cornerRadius = dpToPx(999f).toFloat()
            }
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            setOnClickListener { onClick() }
        }
    }

    private fun buildCommentContent(comment: strategy_comment): String {
        val replyTargetName = resolveReplyTargetName(comment)
        val content = comment.content.trim()
        return if (replyTargetName.isBlank()) {
            content
        } else {
            "回复 $replyTargetName：$content"
        }
    }

    private fun resolveReplyTargetName(comment: strategy_comment): String {
        return comment.replyToUserName.takeIf { it.isNotBlank() }
            ?: resolveUserDisplayName(comment.replyToUser).takeIf { it != "热心玩家" }
            ?: ""
    }

    private fun resolveUserDisplayName(user: MyUser?): String {
        if (user == null) return "热心玩家"
        return user.nickname.takeIf { it.isNotBlank() }
            ?: user.username?.takeIf { it.isNotBlank() }
            ?: "热心玩家"
    }

    private fun isOwnComment(comment: strategy_comment): Boolean {
        val currentUserId = SupabaseRepository.getCurrentUser(this)?.objectId
        val commentUserId = comment.user?.objectId
        return !currentUserId.isNullOrBlank() && currentUserId == commentUserId
    }

    private fun showDeleteCommentDialog(comment: strategy_comment) {
        val strategyId = currentStrategyDetail?.objectId ?: return
        if (commentSubmitInFlight) return

        val titleView = TextView(this).apply {
            text = "删除评论"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#3D3222"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(8f))
        }
        val messageView = TextView(this).apply {
            text = "确定删除这条评论吗？"
            textSize = 14f
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(2f).toFloat(), 1f)
            setTextColor(Color.parseColor("#6C5B43"))
            setPadding(dpToPx(24f), dpToPx(8f), dpToPx(24f), dpToPx(4f))
        }

        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setCustomTitle(titleView)
            .setView(messageView)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(Color.parseColor("#8F7A56"))
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                deleteComment(comment, strategyId, dialog)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(Color.parseColor("#C25B4A"))
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun showCommentInputDialog(replyTarget: strategy_comment? = null) {
        val detail = currentStrategyDetail ?: return
        if (commentSubmitInFlight) return

        val editText = EditText(this).apply {
            hint = if (replyTarget == null) "写下你的评论..." else "回复 ${resolveUserDisplayName(replyTarget.user)}"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.parseColor("#2F261B"))
            setHintTextColor(Color.parseColor("#A89B84"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FBF8F1"))
                setStroke(dpToPx(1f), Color.parseColor("#E0DCD3"))
                cornerRadius = dpToPx(12f).toFloat()
            }
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            filters = arrayOf(InputFilter.LengthFilter(500))
        }

        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle(if (replyTarget == null) "发表评论" else "回复 ${resolveUserDisplayName(replyTarget.user)}")
            .setView(editText)
            .setNegativeButton("取消", null)
            .setPositiveButton("发布", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val content = editText.text?.toString()?.trim().orEmpty()
                if (content.isBlank()) {
                    editText.error = "请输入评论内容"
                    return@setOnClickListener
                }
                ensureLoggedIn(
                    onSuccess = { user ->
                        submitComment(detail, user, content, replyTarget, dialog)
                    },
                    onError = { message ->
                        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                    }
                )
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun ensureLoggedIn(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser != null) {
            onSuccess(currentUser)
            return
        }
        performOneClickLogin(onSuccess, onError)
    }

    private fun submitComment(
        detail: strategy_detail,
        user: MyUser,
        content: String,
        replyTarget: strategy_comment?,
        dialog: AlertDialog
    ) {
        val strategyId = detail.objectId ?: return
        commentSubmitInFlight = true
        SupabaseRepository.createComment(
            context = this,
            strategyId = strategyId,
            content = content,
            replyTarget = replyTarget,
            onSuccess = {
                if (isFinishing || isDestroyed) return@createComment
                commentSubmitInFlight = false
                dialog.dismiss()
                Toast.makeText(this, "评论已发布", Toast.LENGTH_SHORT).show()
                loadStrategyComments(strategyId)
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@createComment
                commentSubmitInFlight = false
                Toast.makeText(this, "评论发布失败: $message", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun deleteComment(comment: strategy_comment, strategyId: String, dialog: AlertDialog) {
        val commentId = comment.objectId ?: return
        commentSubmitInFlight = true
        SupabaseRepository.deleteComment(
            context = this,
            commentId = commentId,
            onSuccess = {
                if (isFinishing || isDestroyed) return@deleteComment
                commentSubmitInFlight = false
                dialog.dismiss()
                Toast.makeText(this, "评论已删除", Toast.LENGTH_SHORT).show()
                loadStrategyComments(strategyId)
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@deleteComment
                commentSubmitInFlight = false
                Toast.makeText(this, "删除评论失败: $message", Toast.LENGTH_SHORT).show()
            },
        )
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
                        discsContainer.addView(createDiscChip(oper.name, discId, data.isFromMaaYuan))
                    }
                }
                operLayout.addView(discsContainer)
            }

            rosterContainer.addView(operLayout)
        }
    }

    private fun recordStrategyViewIfNeeded(detail: strategy_detail): Boolean {
        val strategyId = detail.objectId ?: return false
        val key = KEY_LAST_VIEW_PREFIX + strategyId
        val lastViewedAt = statsPrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        if (now - lastViewedAt < VIEW_THROTTLE_WINDOW_MS) return false
        statsPrefs.edit().putLong(key, now).apply()
        SupabaseRepository.incrementStrategyView(strategyId) { message ->
            statsPrefs.edit().putLong(key, lastViewedAt).apply()
            RunLogger.e(module = "作业站", section = "详情页", message = "阅读量自增失败：$message")
        }
        return true
    }

    private fun bindTableAndOtherActions(data: JobStationAssetRepository.JobStationDetailData) {
        val headerContainer = findViewById<LinearLayout>(R.id.ll_table_header)
        val bodyContainer = findViewById<LinearLayout>(R.id.ll_table_body)
        val otherActionsCard = findViewById<View>(R.id.card_other_actions)
        val otherActionsContainer = findViewById<LinearLayout>(R.id.ll_other_actions)
        val remoteHighlightInstructions: Map<Int, List<JobStationAssetRepository.ActionChip>> = if (data.isFromMaaYuan) {
            JobStationAssetRepository.parseHighlightInstructionChips(data.importPayload?.instructionsJson)
        } else {
            emptyMap()
        }

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

            val otherChips = (turn.slotActions[0].orEmpty() + remoteHighlightInstructions[turn.turnNum].orEmpty())
                .distinctBy { it.globalOrder to it.label }
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
        val normalizedLabel = normalizeOtherActionLabel(chip.label)
        val (bgColor, strokeColor, textColor) = resolveOtherActionStyle(normalizedLabel)
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(5f), 0, dpToPx(5f))

            addView(TextView(this@JobStationActivity).apply {
                text = if (chip.globalOrder <= 0) {
                    "第${turnNum}回合"
                } else {
                    "第${turnNum}回合 行动${chip.globalOrder}"
                }
                textSize = 12f
                setTextColor(Color.parseColor("#857864"))
            })

            addView(TextView(this@JobStationActivity).apply {
                text = normalizedLabel
                textSize = 12f
                setPadding(dpToPx(10f), dpToPx(3f), dpToPx(10f), dpToPx(3f))
                setTextColor(Color.parseColor(textColor))
                setTypeface(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgColor))
                    setStroke(dpToPx(1f), Color.parseColor(strokeColor))
                    cornerRadius = dpToPx(999f).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(8f)
                }
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

    private fun normalizeOtherActionLabel(label: String): String {
        return when {
            label.contains("切换左侧目标") || label.contains("左侧目标") || label.contains("左目标") -> "切换左侧目标"
            label.contains("切换右侧目标") || label.contains("右侧目标") || label.contains("右目标") -> "切换右侧目标"
            else -> label
        }
    }

    private fun resolveOtherActionStyle(label: String): Triple<String, String, String> {
        return when {
            label.contains("全灭检测") ->
                Triple("#FFF1F1", "#E29A9A", "#B23A3A")
            label.contains("阵亡检测") ->
                Triple("#FFF4F6", "#E2A3B7", "#A63F67")
            label.contains("暴击检测") ->
                Triple("#FFF4F0", "#E5A17A", "#B3522A")
            label.contains("庞统复制检测") ->
                Triple("#FFFBEF", "#E4C87A", "#9A6A12")
            label.contains("橙星检测") ->
                Triple("#FFF6E8", "#E3B15F", "#B76A11")
            label.contains("紫星检测") ->
                Triple("#FBF2FF", "#C9A2E6", "#7B43B6")
            label.contains("龙气检测") ->
                Triple("#FFF4E6", "#E4B46A", "#A6651B")
            label.contains("切换左侧目标") || label.contains("切换右侧目标") ->
                Triple("#EEF5FF", "#9BBBE7", "#3F6EA6")
            else ->
                Triple("#F7F5F0", "#E0DCD3", "#3D3222")
        }
    }

    private fun createDiscChip(agentName: String, discId: Int, isFromMaaYuan: Boolean): TextView {
        val discSpec = if (isFromMaaYuan) {
            JobStationAssetRepository.resolveMaaDiscDisplaySpec(this, agentName, discId)
        } else {
            JobStationAssetRepository.resolveCommunityDiscDisplaySpec(agentName, discId)
        }
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
        val normalizedUrl = normalizeExternalLink(url)
        if (normalizedUrl == null) {
            RunLogger.e(module = "作业站", section = "外链", message = "链接格式无效：${url.take(80)}")
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            RunLogger.e(module = "作业站", section = "外链", message = "打开异常", throwable = it)
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeExternalLink(rawUrl: String?): String? {
        val trimmed = rawUrl
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
            ?: return null

        val extractedUrl = extractExternalUrl(trimmed) ?: trimmed
        val cleanedUrl = extractedUrl.trim().trimEnd('。', '，', ',', '.', '；', ';', '！', '!', '？', '?', '）', ')', '】', ']', '》', '>', '\"', '\'')
        if (cleanedUrl.isBlank()) return null

        val normalizedUrl = when {
            cleanedUrl.startsWith("https://", ignoreCase = true) ||
                cleanedUrl.startsWith("http://", ignoreCase = true) -> cleanedUrl
            cleanedUrl.startsWith("//") -> "https:$cleanedUrl"
            Patterns.WEB_URL.matcher(cleanedUrl).matches() -> "https://$cleanedUrl"
            else -> return null
        }

        val uri = Uri.parse(normalizedUrl)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.trim().orEmpty()
        return normalizedUrl.takeIf {
            (scheme == "http" || scheme == "https") && host.isNotBlank()
        }
    }

    private fun extractExternalUrl(text: String): String? {
        val regexes = listOf(
            Regex("""(?i)https?://[^\s<>"'()（）]+"""),
            Regex("""(?i)(?:www\.)[^\s<>"'()（）]+"""),
            Regex("""(?i)(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/[^\s<>"'()（）]*)?""")
        )
        return regexes.asSequence()
            .mapNotNull { it.find(text)?.value }
            .firstOrNull()
    }

    private fun copyLinkToClipboard(link: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("原帖链接", link))
        Toast.makeText(this, "原帖链接已复制", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}

