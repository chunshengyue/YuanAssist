package com.example.yuanassist.ui

import android.os.Bundle
import android.text.InputFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.model.cloud_daily_script_comment
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.CloudScriptOverrideStore
import com.example.yuanassist.utils.DailyScriptBundleZipStore
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.SupabaseTimeFormatter
import com.example.yuanassist.utils.UserDailyScriptStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CloudDailyScriptActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCRIPT_ID = "extra_script_id"
    }

    private val gson = Gson()
    private var detail: cloud_daily_script? = null
    private var downloading = false
    private var commentsRequestVersion = 0
    private var commentSubmitInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_daily_script)
        applyStatusBarInsets()
        findViewById<ImageView>(R.id.btn_cloud_detail_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_cloud_save_local).setOnClickListener { downloadAndSave() }
        findViewById<TextView>(R.id.btn_cloud_write_comment).setOnClickListener {
            showCommentInputDialog(null)
        }
        loadDetail()
    }

    private fun loadDetail() {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
        if (scriptId.isBlank()) {
            Toast.makeText(this, "脚本不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        SupabaseRepository.getDailyScriptDetail(
            scriptId = scriptId,
            onSuccess = {
                detail = it
                bindDetail(it)
                loadComments(it.objectId.orEmpty())
            },
            onError = { message ->
                Toast.makeText(this, "加载失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun bindDetail(item: cloud_daily_script) {
        findViewById<TextView>(R.id.tv_cloud_detail_title).text = item.title.ifBlank { "未命名脚本" }
        val author = item.author?.nickname?.takeIf { it.isNotBlank() }
            ?: item.author?.username?.takeIf { it.isNotBlank() }
            ?: "匿名用户"
        findViewById<TextView>(R.id.tv_cloud_detail_author).text = "作者：$author"
        findViewById<TextView>(R.id.tv_cloud_detail_meta).text =
            "${item.taskCount}步 · ${item.downloadCount}次下载"
        findViewById<TextView>(R.id.tv_cloud_detail_description).text = item.description.ifBlank { "暂无说明" }
        bindGuideImages(parseGuideImages(item.guideImages))
    }

    private fun bindGuideImages(urls: List<String>) {
        val container = findViewById<LinearLayout>(R.id.layout_cloud_guide_images)
        container.removeAllViews()
        if (urls.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "暂无图片指引"
                setTextColor(android.graphics.Color.parseColor("#8C7A61"))
                textSize = 14f
            })
            return
        }
        urls.forEachIndexed { index, url ->
            if (index > 0) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12.dp())
                })
            }
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 260.dp())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#EFE6D6"))
            }
            container.addView(imageView)
            Coil.imageLoader(this).enqueue(
                ImageRequest.Builder(this)
                    .data(url)
                    .target(imageView)
                    .build(),
            )
        }
    }

    private fun loadComments(scriptId: String) {
        if (scriptId.isBlank()) return
        val requestVersion = ++commentsRequestVersion
        showCommentsLoadingState()
        SupabaseRepository.listCloudDailyScriptComments(
            scriptId = scriptId,
            onSuccess = { comments ->
                if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@listCloudDailyScriptComments
                renderComments(comments)
            },
            onError = { message ->
                if (requestVersion != commentsRequestVersion || isFinishing || isDestroyed) return@listCloudDailyScriptComments
                showCommentsErrorState(message.ifBlank { "评论加载失败" })
            },
        )
    }

    private fun showCommentsLoadingState() {
        findViewById<TextView>(R.id.tv_cloud_comments_title).text = "评论"
        findViewById<TextView>(R.id.tv_cloud_comments_hint).apply {
            visibility = View.VISIBLE
            text = "正在加载评论..."
        }
        findViewById<LinearLayout>(R.id.ll_cloud_comments_container).removeAllViews()
    }

    private fun showCommentsErrorState(message: String) {
        findViewById<TextView>(R.id.tv_cloud_comments_title).text = "评论"
        findViewById<TextView>(R.id.tv_cloud_comments_hint).apply {
            visibility = View.VISIBLE
            text = "评论加载失败：$message"
        }
        findViewById<LinearLayout>(R.id.ll_cloud_comments_container).removeAllViews()
    }

    private fun renderComments(comments: List<cloud_daily_script_comment>) {
        findViewById<TextView>(R.id.tv_cloud_comments_title).text = "评论 ${comments.size}"
        val hintView = findViewById<TextView>(R.id.tv_cloud_comments_hint)
        val container = findViewById<LinearLayout>(R.id.ll_cloud_comments_container)
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
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp()).apply {
                        topMargin = 12.dp()
                        bottomMargin = 12.dp()
                    }
                    setBackgroundColor(Color.parseColor("#F0E6D4"))
                })
            }
        }
    }

    private fun createCommentItemView(comment: cloud_daily_script_comment): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val headerLayout = LinearLayout(this@CloudDailyScriptActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val avatarView = ImageView(this@CloudDailyScriptActivity).apply {
                layoutParams = LinearLayout.LayoutParams(30.dp(), 30.dp())
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_job_station_avatar)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            val avatarUrl = comment.user?.avatarUrl.orEmpty()
            if (avatarUrl.isBlank()) {
                avatarView.setImageResource(R.drawable.cover)
            } else {
                Coil.imageLoader(this@CloudDailyScriptActivity).enqueue(
                    ImageRequest.Builder(this@CloudDailyScriptActivity)
                        .data(avatarUrl)
                        .placeholder(R.drawable.cover)
                        .error(R.drawable.cover)
                        .target(avatarView)
                        .build(),
                )
            }
            headerLayout.addView(avatarView)

            headerLayout.addView(LinearLayout(this@CloudDailyScriptActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 10.dp()
                }
                addView(TextView(this@CloudDailyScriptActivity).apply {
                    text = resolveUserDisplayName(comment.user)
                    textSize = 14f
                    setTextColor(Color.parseColor("#2F261B"))
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@CloudDailyScriptActivity).apply {
                    text = SupabaseTimeFormatter.formatToBeijing(comment.createdAt)
                    textSize = 11f
                    setTextColor(Color.parseColor("#9C8E77"))
                })
            })

            headerLayout.addView(LinearLayout(this@CloudDailyScriptActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(createCommentActionButton("回复") { showCommentInputDialog(comment) })
                if (isOwnComment(comment)) {
                    addView(createCommentActionButton("删除") { showDeleteCommentDialog(comment) }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = 8.dp() }
                    })
                }
            })

            addView(headerLayout)
            addView(TextView(this@CloudDailyScriptActivity).apply {
                text = buildCommentContent(comment)
                textSize = 14f
                setTextColor(Color.parseColor("#4D4030"))
                setLineSpacing(3.dp().toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 8.dp() }
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
                setStroke(1.dp(), Color.parseColor("#D8C18A"))
                cornerRadius = 999.dp().toFloat()
            }
            setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            setOnClickListener { onClick() }
        }
    }

    private fun showCommentInputDialog(replyTarget: cloud_daily_script_comment?) {
        val item = detail ?: return
        if (commentSubmitInFlight) return
        val editText = EditText(this).apply {
            hint = if (replyTarget == null) "写下你的评论..." else "回复 ${resolveUserDisplayName(replyTarget.user)}"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.parseColor("#2F261B"))
            setHintTextColor(Color.parseColor("#A89B84"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FBF8F1"))
                setStroke(1.dp(), Color.parseColor("#E0DCD3"))
                cornerRadius = 12.dp().toFloat()
            }
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
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
                    onSuccess = {
                        submitComment(item, content, replyTarget, dialog)
                    },
                    onError = { message ->
                        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
                    },
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
        SupabaseRepository.loginWithDevice(this, onSuccess, onError)
    }

    private fun submitComment(
        item: cloud_daily_script,
        content: String,
        replyTarget: cloud_daily_script_comment?,
        dialog: AlertDialog,
    ) {
        val scriptId = item.objectId ?: return
        commentSubmitInFlight = true
        SupabaseRepository.createCloudDailyScriptComment(
            context = this,
            scriptId = scriptId,
            content = content,
            replyTarget = replyTarget,
            onSuccess = {
                if (isFinishing || isDestroyed) return@createCloudDailyScriptComment
                commentSubmitInFlight = false
                dialog.dismiss()
                Toast.makeText(this, "评论已发布", Toast.LENGTH_SHORT).show()
                loadComments(scriptId)
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@createCloudDailyScriptComment
                commentSubmitInFlight = false
                Toast.makeText(this, "评论发布失败：$message", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun showDeleteCommentDialog(comment: cloud_daily_script_comment) {
        val scriptId = detail?.objectId ?: return
        if (commentSubmitInFlight) return
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("删除评论")
            .setMessage("确定删除这条评论吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                deleteComment(comment, scriptId, dialog)
            }
        }
        dialog.show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun deleteComment(comment: cloud_daily_script_comment, scriptId: String, dialog: AlertDialog) {
        val commentId = comment.objectId ?: return
        commentSubmitInFlight = true
        SupabaseRepository.deleteCloudDailyScriptComment(
            context = this,
            commentId = commentId,
            onSuccess = {
                if (isFinishing || isDestroyed) return@deleteCloudDailyScriptComment
                commentSubmitInFlight = false
                dialog.dismiss()
                Toast.makeText(this, "评论已删除", Toast.LENGTH_SHORT).show()
                loadComments(scriptId)
            },
            onError = { message ->
                if (isFinishing || isDestroyed) return@deleteCloudDailyScriptComment
                commentSubmitInFlight = false
                Toast.makeText(this, "删除评论失败：$message", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun buildCommentContent(comment: cloud_daily_script_comment): String {
        val replyTargetName = comment.replyToUserName.takeIf { it.isNotBlank() }
            ?: resolveUserDisplayName(comment.replyToUser).takeIf { it != "热心玩家" }
            ?: ""
        val content = comment.content.trim()
        return if (replyTargetName.isBlank()) content else "回复 $replyTargetName：$content"
    }

    private fun resolveUserDisplayName(user: MyUser?): String {
        if (user == null) return "热心玩家"
        return user.nickname.takeIf { it.isNotBlank() }
            ?: user.username.takeIf { it.isNotBlank() }
            ?: "热心玩家"
    }

    private fun isOwnComment(comment: cloud_daily_script_comment): Boolean {
        val currentUserId = SupabaseRepository.getCurrentUser(this)?.objectId
        val commentUserId = comment.user?.objectId
        return !currentUserId.isNullOrBlank() && currentUserId == commentUserId
    }

    private fun downloadAndSave() {
        val item = detail ?: return
        if (downloading) return
        val scriptId = item.objectId.orEmpty()
        downloading = true
        Toast.makeText(this, "正在获取脚本包...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.createDailyScriptDownloadUrl(
            scriptId = scriptId,
            onSuccess = { ticket ->
                val zip = File(cacheDir, "cloud_daily_${scriptId}.zip")
                SupabaseRepository.downloadDailyScriptBundle(
                    downloadUrl = ticket.downloadUrl,
                    targetFile = zip,
                    onSuccess = { file -> saveDownloadedBundle(item, file) },
                    onError = { message ->
                        downloading = false
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    },
                )
            },
            onError = { message ->
                downloading = false
                Toast.makeText(this, "下载地址获取失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun saveDownloadedBundle(item: cloud_daily_script, zipFile: File) {
        runCatching {
            val targetScriptName = CloudScriptOverrideStore
                .normalizeTargetScriptName(item.overrideAssetScript)
            if (targetScriptName.isNotBlank()) {
                saveDownloadedOverrideBundle(item, zipFile, targetScriptName)
            } else {
                val bundle = UserDailyScriptStore.createBundle(this, item.title)
                DailyScriptBundleZipStore.unpackToBundle(zipFile, bundle, gson)
                Toast.makeText(this, "已保存到本地脚本库", Toast.LENGTH_LONG).show()
            }
            SupabaseRepository.incrementDailyScriptDownload(item.objectId.orEmpty())
        }.onFailure { error ->
            Toast.makeText(this, "脚本导入失败：${error.message}", Toast.LENGTH_LONG).show()
        }
        downloading = false
    }

    private fun saveDownloadedOverrideBundle(
        item: cloud_daily_script,
        zipFile: File,
        targetScriptName: String
    ) {
        val tempBundle = UserDailyScriptStore.createBundle(
            this,
            "cloud_override_${targetScriptName.removeSuffix(".json")}"
        )
        try {
            val plan = DailyScriptBundleZipStore.unpackToBundle(zipFile, tempBundle, gson)
            CloudScriptOverrideStore.saveOverride(
                context = this,
                targetScriptName = targetScriptName,
                plan = plan,
                title = item.title,
                gson = gson
            )
            Toast.makeText(this, "已保存官方修正脚本，将优先覆盖 $targetScriptName", Toast.LENGTH_LONG).show()
        } finally {
            UserDailyScriptStore.deleteBundle(tempBundle)
        }
    }

    private fun parseGuideImages(raw: String): List<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
        }.getOrNull().orEmpty().filter { it.isNotBlank() }
    }

    private fun applyStatusBarInsets() {
        val spacer = findViewById<View>(R.id.view_cloud_detail_status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams = view.layoutParams.apply { height = top }
            insets
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
