package com.example.yuanassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import cn.bmob.v3.BmobQuery
import cn.bmob.v3.BmobUser
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.FindListener
import cn.bmob.v3.listener.QueryListener
import cn.bmob.v3.listener.SaveListener
import cn.bmob.v3.listener.UpdateListener
import com.bumptech.glide.Glide
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.InstructionJson
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.STRATEGY_VISIBLE_PUBLIC
import com.example.yuanassist.model.StrategyPreviewData
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.model.strategy_detail_counter_update
import com.example.yuanassist.model.strategy_favorite
import com.example.yuanassist.ui.UploadTurnItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.max

data class DetailItem(val type: String, val content: String)

class StrategyDetailActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_STRATEGY_STATS = "strategy_detail_stats"
        private const val KEY_LAST_VIEW_PREFIX = "last_view_"
        private const val VIEW_THROTTLE_WINDOW_MS = 10 * 60 * 1000L
    }

    private var isFavorited = false
    private var isPreviewMode = false
    private var favoriteInFlight = false
    private var favoriteObjectId: String? = null
    private var currentDetail: strategy_detail? = null
    private lateinit var statsPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_strategy_detail)
        statsPrefs = getSharedPreferences(PREFS_STRATEGY_STATS, MODE_PRIVATE)

        val topBar = findViewById<View>(R.id.top_bar)
        topBar.findViewById<ImageView>(R.id.btn_back).setOnClickListener { onBackPressed() }

        isPreviewMode = intent.getBooleanExtra("IS_PREVIEW", false)
        if (isPreviewMode) {
            val previewDataJson = intent.getStringExtra("PREVIEW_DATA_JSON")
            if (previewDataJson.isNullOrEmpty()) {
                Toast.makeText(this, "预览资料解析失败", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                renderPreviewMode(Gson().fromJson(previewDataJson, StrategyPreviewData::class.java))
            }
            return
        }

        val strategyId = intent.getStringExtra("STRATEGY_ID")
        if (strategyId.isNullOrEmpty()) {
            Toast.makeText(this, "攻略ID传递错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadAndRenderNormalMode(strategyId)
    }

    private fun renderPreviewMode(data: StrategyPreviewData) {
        bindTopBar("攻略预览：${data.title}", "作者：我自己", null)
        findViewById<View>(R.id.layout_bottom_bar).visibility = View.GONE
        clearDynamicStrategyViews()
        when {
            !data.strategyImageUri.isNullOrEmpty() -> {
                addDynamicStrategyTitle("◆ 跟打表格")
                renderStrategyImage(data.strategyImageUri)
            }
            !data.tableData.isNullOrEmpty() -> {
                addDynamicStrategyTitle("◆ 跟打表格")
                hideStrategyImage()
                addDynamicViewBeforeImage(createReadOnlyTable(data.tableData))
            }
            else -> hideStrategyImage()
        }
        renderAgents(data.agentType, data.agentSelection, data.agentImageUri, data.agentTextDesc)
        renderDescriptionSection(data.content)
    }

    private fun loadAndRenderNormalMode(objectId: String) {
        findViewById<LinearLayout>(R.id.btn_favorite).setOnClickListener { handleFavoriteClick() }
        updateFavoriteUi(false, 0)
        updateViewCountUi(0)
        Toast.makeText(this, "加载中...", Toast.LENGTH_SHORT).show()

        val query = BmobQuery<strategy_detail>()
        query.include("author")
        query.getObject(objectId, object : QueryListener<strategy_detail>() {
            override fun done(detail: strategy_detail?, e: BmobException?) {
                if (e != null || detail == null) {
                    runOnUiThread {
                        Toast.makeText(this@StrategyDetailActivity, "未找到该攻略: ${e?.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                runOnUiThread {
                    if (!canAccessDetail(detail)) {
                        Toast.makeText(this@StrategyDetailActivity, "该攻略当前不可见", Toast.LENGTH_SHORT).show()
                        finish()
                        return@runOnUiThread
                    }
                    currentDetail = detail
                    favoriteObjectId = null
                    bindTopBar(detail.title, "作者：${resolveAuthorName(detail)}", detail.author?.avatarUrl)
                    updateFavoriteUi(false, detail.favoriteCount ?: 0)
                    updateViewCountUi(detail.viewCount ?: 0)
                    syncFavoriteState(detail)
                    recordStrategyViewIfNeeded(detail)
                    clearDynamicStrategyViews()
                    bindBottomButtons(detail, !detail.scriptContent.isNullOrEmpty())
                    renderStrategyContent(detail)
                    renderAgents(detail.agentType, parseAgentSelection(detail.agentSelection), detail.agentImageUrl, detail.agentTextDesc)
                    renderDescriptionSection(detail.content)
                }
            }
        })
    }

    private fun bindTopBar(title: String, subtitle: String, avatarUrl: String?) {
        val topBar = findViewById<View>(R.id.top_bar)
        topBar.findViewById<TextView>(R.id.tv_top_title).text = title
        topBar.findViewById<TextView>(R.id.tv_top_author_name).text = subtitle
        val avatarView = topBar.findViewById<ImageView>(R.id.iv_top_author_avatar)
        avatarView.visibility = View.VISIBLE
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_launcher_background)
        }
    }

    private fun resolveAuthorName(detail: strategy_detail): String {
        return detail.author?.nickname?.takeIf { it.isNotBlank() } ?: detail.author?.username ?: "热心玩家"
    }

    private fun canAccessDetail(detail: strategy_detail): Boolean {
        if (detail.visible == STRATEGY_VISIBLE_PUBLIC) {
            return true
        }
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        return !currentUser?.objectId.isNullOrBlank() && currentUser?.objectId == detail.author?.objectId
    }

    private fun bindBottomButtons(detail: strategy_detail, hasScript: Boolean) {
        val btnCopyUrl = findViewById<TextView>(R.id.btn_copy_url)
        val btnImportScript = findViewById<TextView>(R.id.btn_import_script)
        if (!detail.originalPostUrl.isNullOrEmpty()) {
            btnCopyUrl.visibility = View.VISIBLE
            btnCopyUrl.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("原帖链接", detail.originalPostUrl))
                Toast.makeText(this, "原帖链接已复制", Toast.LENGTH_SHORT).show()
            }
        } else {
            btnCopyUrl.visibility = View.GONE
        }
        if (hasScript) {
            btnImportScript.visibility = View.VISIBLE
            btnImportScript.setOnClickListener {
                importScriptToService(
                    detail.scriptContent,
                    detail.config,
                    detail.instructions,
                    cleanAgentSelectionJson(detail.agentSelection)
                )
            }
        } else {
            btnImportScript.visibility = View.GONE
        }
    }

    private fun renderStrategyContent(detail: strategy_detail) {
        val hasImage = !detail.strategyImage.isNullOrEmpty()
        val hasScript = !detail.scriptContent.isNullOrEmpty()
        when {
            hasImage -> {
                addDynamicStrategyTitle("◆ 攻略原图")
                renderStrategyImage(detail.strategyImage)
            }
            hasScript -> {
                addDynamicStrategyTitle("◆ 跟打表格")
                hideStrategyImage()
                addDynamicViewBeforeImage(createReadOnlyTable(parseScriptContentToTableData(detail.scriptContent)))
                if (!detail.instructions.isNullOrEmpty()) {
                    addDynamicViewBeforeImage(createInstructionsView(detail.instructions))
                }
            }
            else -> hideStrategyImage()
        }
    }

    private fun handleFavoriteClick() {
        val detail = currentDetail ?: return
        if (favoriteInFlight) return
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
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
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java) ?: run {
            favoriteObjectId = null
            updateFavoriteUi(false, detail.favoriteCount ?: 0)
            return
        }
        val query = BmobQuery<strategy_favorite>()
        query.addWhereEqualTo("uniqueKey", buildFavoriteUniqueKey(currentUser.objectId, strategyId))
        query.setLimit(1)
        query.findObjects(object : FindListener<strategy_favorite>() {
            override fun done(list: MutableList<strategy_favorite>?, e: BmobException?) {
                if (currentDetail?.objectId != strategyId) return
                runOnUiThread {
                    val favorite = if (e == null) list?.firstOrNull() else null
                    favoriteObjectId = favorite?.objectId
                    updateFavoriteUi(favorite != null, currentDetail?.favoriteCount ?: 0)
                }
            }
        })
    }

    private fun showFavoriteLoginDialog() {
        val detail = currentDetail ?: return
        val dialog = AlertDialog.Builder(this)
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
    }

    private fun performOneClickLogin(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        if (currentUser != null) {
            onSuccess(currentUser)
            return
        }
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
        val user = MyUser().apply {
            username = deviceId
            setPassword("123456")
            nickname = "玩家_$deviceId"
        }
        user.signUp(object : SaveListener<MyUser>() {
            override fun done(u: MyUser?, e: BmobException?) {
                if (e == null && u != null) {
                    onSuccess(u)
                } else if (e?.errorCode == 202) {
                    user.login(object : SaveListener<MyUser>() {
                        override fun done(lu: MyUser?, le: BmobException?) {
                            if (le == null && lu != null) onSuccess(lu) else onError(le?.message ?: "登录失败")
                        }
                    })
                } else {
                    onError(e?.message ?: "创建账号失败")
                }
            }
        })
    }

    private fun addFavorite(detail: strategy_detail, user: MyUser, fromLogin: Boolean) {
        val strategyId = detail.objectId ?: return
        favoriteInFlight = true
        val query = BmobQuery<strategy_favorite>()
        query.addWhereEqualTo("uniqueKey", buildFavoriteUniqueKey(user.objectId, strategyId))
        query.setLimit(1)
        query.findObjects(object : FindListener<strategy_favorite>() {
            override fun done(list: MutableList<strategy_favorite>?, e: BmobException?) {
                val existing = if (e == null) list?.firstOrNull() else null
                if (existing != null) {
                    favoriteInFlight = false
                    favoriteObjectId = existing.objectId
                    updateFavoriteUi(true, currentDetail?.favoriteCount ?: 0)
                    if (fromLogin) {
                        Toast.makeText(this@StrategyDetailActivity, "登录成功，已加入收藏", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                strategy_favorite().apply {
                    this.user = user
                    this.strategy = strategy_detail().apply { this.objectId = strategyId }
                    uniqueKey = buildFavoriteUniqueKey(user.objectId, strategyId)
                }.save(object : SaveListener<String>() {
                    override fun done(objectId: String?, saveError: BmobException?) {
                        favoriteInFlight = false
                        if (saveError == null) {
                            favoriteObjectId = objectId
                            val newCount = (currentDetail?.favoriteCount ?: 0) + 1
                            currentDetail?.favoriteCount = newCount
                            updateFavoriteUi(true, newCount)
                            updateFavoriteCount(strategyId, 1)
                            Toast.makeText(
                                this@StrategyDetailActivity,
                                if (fromLogin) "登录成功，已加入收藏" else "已收藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(this@StrategyDetailActivity, "收藏失败: ${saveError.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        })
    }

    private fun removeFavorite(detail: strategy_detail, user: MyUser) {
        val strategyId = detail.objectId ?: return
        favoriteInFlight = true
        if (!favoriteObjectId.isNullOrEmpty()) {
            deleteFavoriteObject(favoriteObjectId!!, strategyId)
            return
        }
        val query = BmobQuery<strategy_favorite>()
        query.addWhereEqualTo("uniqueKey", buildFavoriteUniqueKey(user.objectId, strategyId))
        query.setLimit(1)
        query.findObjects(object : FindListener<strategy_favorite>() {
            override fun done(list: MutableList<strategy_favorite>?, e: BmobException?) {
                val favorite = if (e == null) list?.firstOrNull() else null
                if (favorite == null) {
                    favoriteInFlight = false
                    updateFavoriteUi(false, currentDetail?.favoriteCount ?: 0)
                    return
                }
                deleteFavoriteObject(favorite.objectId, strategyId)
            }
        })
    }

    private fun deleteFavoriteObject(objectId: String, strategyId: String) {
        strategy_favorite().apply { this.objectId = objectId }.delete(object : UpdateListener() {
            override fun done(e: BmobException?) {
                favoriteInFlight = false
                if (e == null) {
                    favoriteObjectId = null
                    val newCount = max(0, (currentDetail?.favoriteCount ?: 0) - 1)
                    currentDetail?.favoriteCount = newCount
                    updateFavoriteUi(false, newCount)
                    updateFavoriteCount(strategyId, -1)
                    Toast.makeText(this@StrategyDetailActivity, "已取消收藏", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@StrategyDetailActivity, "取消收藏失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateFavoriteCount(strategyId: String, delta: Int) {
        strategy_detail_counter_update().apply {
            increment("favoriteCount", delta)
        }.update(strategyId, object : UpdateListener() {
            override fun done(e: BmobException?) {}
        })
    }

    private fun recordStrategyViewIfNeeded(detail: strategy_detail) {
        val strategyId = detail.objectId ?: return
        val key = KEY_LAST_VIEW_PREFIX + strategyId
        val lastViewedAt = statsPrefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        if (now - lastViewedAt < VIEW_THROTTLE_WINDOW_MS) return
        statsPrefs.edit().putLong(key, now).apply()
        strategy_detail_counter_update().apply {
            increment("viewCount")
        }.update(strategyId, object : UpdateListener() {
            override fun done(e: BmobException?) {
                if (e == null) {
                    val newCount = (currentDetail?.viewCount ?: 0) + 1
                    currentDetail?.viewCount = newCount
                    runOnUiThread { updateViewCountUi(newCount) }
                } else {
                    statsPrefs.edit().putLong(key, lastViewedAt).apply()
                }
            }
        })
    }

    private fun updateFavoriteUi(favorited: Boolean, count: Int) {
        isFavorited = favorited
        val color = Color.parseColor(if (favorited) "#F44336" else "#E5C07B")
        findViewById<TextView>(R.id.tv_favorite_icon).apply {
            text = if (favorited) "\u2605" else "\u2606"
            setTextColor(color)
        }
        findViewById<TextView>(R.id.tv_favorite_count).apply {
            text = count.toString()
            setTextColor(color)
        }
    }

    private fun updateViewCountUi(count: Int) {
        findViewById<TextView>(R.id.tv_view_count).text = "阅读 $count"
    }

    private fun buildFavoriteUniqueKey(userId: String?, strategyId: String): String {
        return "${userId ?: "guest"}_$strategyId"
    }

    private fun clearDynamicStrategyViews() {
        val mainLayout = findViewById<View>(R.id.layout_image_container).parent as LinearLayout
        mainLayout.findViewWithTag<View>("DYNAMIC_TABLE_TITLE")?.let { mainLayout.removeView(it) }
        mainLayout.findViewWithTag<View>("DYNAMIC_TABLE_CONTENT")?.let { mainLayout.removeView(it) }
        mainLayout.findViewWithTag<View>("DYNAMIC_INSTRUCTIONS")?.let { mainLayout.removeView(it) }
    }

    private fun addDynamicStrategyTitle(title: String) {
        val mainLayout = findViewById<View>(R.id.layout_image_container).parent as LinearLayout
        val anchorView = findViewById<View>(R.id.layout_image_container)
        val titleView = TextView(this).apply {
            tag = "DYNAMIC_TABLE_TITLE"
            text = title
            setTextColor(Color.parseColor("#E5C07B"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (15 * resources.displayMetrics.density).toInt()
            }
        }
        mainLayout.addView(titleView, mainLayout.indexOfChild(anchorView))
    }

    private fun addDynamicViewBeforeImage(view: View) {
        val mainLayout = findViewById<View>(R.id.layout_image_container).parent as LinearLayout
        mainLayout.addView(view, mainLayout.indexOfChild(findViewById(R.id.layout_image_container)))
    }

    private fun renderStrategyImage(imageUrl: String?) {
        val layoutImageContainer = findViewById<View>(R.id.layout_image_container)
        if (imageUrl.isNullOrEmpty()) {
            layoutImageContainer.visibility = View.GONE
            return
        }
        layoutImageContainer.visibility = View.VISIBLE
        findViewById<ViewPager2>(R.id.vp_detail_images).adapter = StrategyDetailImagesAdapter(listOf(DetailItem("image", imageUrl)))
        findViewById<TextView>(R.id.tv_image_indicator).text = "1/1"
    }

    private fun hideStrategyImage() {
        findViewById<View>(R.id.layout_image_container).visibility = View.GONE
    }

    private fun createInstructionsView(instructionsJson: String): TextView {
        return TextView(this).apply {
            tag = "DYNAMIC_INSTRUCTIONS"
            setTextColor(Color.parseColor("#9E9E9E"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
            text = try {
                val type = object : TypeToken<List<InstructionJson>>() {}.type
                val instList: List<InstructionJson> = Gson().fromJson(instructionsJson, type)
                "附加指令：\n" + instList.joinToString("\n") { "回合${it.turn} 动作${it.step}: [${it.type}] ${it.value}" } + "\n"
            } catch (_: Exception) {
                "附加指令：\n$instructionsJson\n"
            }
        }
    }

    private fun renderAgents(agentType: Int, rawAgentSelection: List<String?>?, agentImageUrl: String?, agentTextDesc: String?) {
        val layoutAgents = findViewById<LinearLayout>(R.id.layout_agents_container)
        layoutAgents.removeAllViews()
        when (agentType) {
            0 -> {
                val hasAnyStar = checkHasAnyStar(rawAgentSelection)
                rawAgentSelection?.forEach { agentRawString ->
                    if (!agentRawString.isNullOrBlank()) {
                        val parts = agentRawString.split("-")
                        val agentName = parts[0].trim()
                        val talents = if (parts.size > 1) parts[1].split("、").mapNotNull { it.trim().toIntOrNull() } else emptyList()
                        renderSingleAgentView(agentName, talents, layoutAgents, hasAnyStar)
                    }
                }
            }
            1 -> if (!agentImageUrl.isNullOrEmpty()) {
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (350 * resources.displayMetrics.density).toInt())
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                Glide.with(this).load(agentImageUrl).into(iv)
                layoutAgents.addView(iv)
            }
            2 -> if (!agentTextDesc.isNullOrEmpty()) {
                val tv = TextView(this).apply {
                    text = agentTextDesc
                    setTextColor(Color.WHITE)
                    textSize = 14f
                }
                layoutAgents.addView(tv)
            }
        }
    }

    private fun renderDescriptionSection(content: String?) {
        val rvText = findViewById<RecyclerView>(R.id.rv_detail_text)
        val glassContainer = rvText.parent as LinearLayout
        val outerContainer = glassContainer.parent as LinearLayout
        rvText.visibility = View.GONE
        outerContainer.findViewWithTag<View>("DYNAMIC_DESC_TITLE")?.let { outerContainer.removeView(it) }
        glassContainer.findViewWithTag<View>("DYNAMIC_DESC_CONTENT")?.let { glassContainer.removeView(it) }
        if (content.isNullOrEmpty()) {
            glassContainer.visibility = View.GONE
            return
        }
        glassContainer.visibility = View.VISIBLE
        val titleView = TextView(this).apply {
            tag = "DYNAMIC_DESC_TITLE"
            text = "◆ 补充说明"
            setTextColor(Color.parseColor("#E5C07B"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (15 * resources.displayMetrics.density).toInt()
            }
        }
        outerContainer.addView(titleView, outerContainer.indexOfChild(glassContainer))
        glassContainer.addView(TextView(this).apply {
            tag = "DYNAMIC_DESC_CONTENT"
            text = content
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 14f
        })
    }

    private fun parseAgentSelection(agentSelectionJson: String?): List<String?>? {
        if (agentSelectionJson.isNullOrEmpty()) return null
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(agentSelectionJson, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanAgentSelectionJson(agentSelectionJson: String?): String {
        if (agentSelectionJson.isNullOrEmpty() || agentSelectionJson == "[]") return ""
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val rawAgentsList: List<String> = Gson().fromJson(agentSelectionJson, type)
            Gson().toJson(rawAgentsList.map { raw ->
                val trimRaw = raw.trim()
                if (trimRaw.isBlank()) "" else trimRaw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
            })
        } catch (_: Exception) {
            agentSelectionJson
        }
    }

    private fun checkHasAnyStar(agents: List<String?>?): Boolean {
        if (agents == null) return false
        return agents.any { raw ->
            if (raw.isNullOrBlank()) return@any false
            val agentName = raw.split("-")[0].trim()
            val pureName = agentName.substringBefore(" ").trim()
            val match = Regex("^(\\d+)(.*)").find(pureName)
            if (match != null && (match.groupValues[1].toIntOrNull() ?: 0) > 0) {
                true
            } else if (agentName.contains(" ")) {
                val suffix = agentName.substringAfter(" ").trim()
                suffix == "觉醒" || suffix.contains("★") || suffix.contains("☆")
            } else {
                false
            }
        }
    }

    private fun renderSingleAgentView(agentDisplayName: String, talents: List<Int>, parentLayout: LinearLayout, forceReserveSpace: Boolean = false) {
        val agentView = layoutInflater.inflate(R.layout.item_strategy_agent, parentLayout, false)
        val ivAvatar = agentView.findViewById<ImageView>(R.id.iv_agent_avatar)
        val tvName = agentView.findViewById<TextView>(R.id.tv_agent_name)
        val tvStar = agentView.findViewById<TextView>(R.id.tv_agent_star)
        val layoutTalents = agentView.findViewById<LinearLayout>(R.id.layout_talents)
        var pureName = agentDisplayName.substringBefore(" ").trim()
        var starText = ""
        val match = Regex("^(\\d+)(.*)").find(pureName)
        if (match != null) {
            val starNum = match.groupValues[1].toIntOrNull() ?: 0
            pureName = match.groupValues[2].trim()
            if (starNum >= 6) starText = "觉醒" else if (starNum in 1..5) starText = "★".repeat(starNum)
        } else if (agentDisplayName.contains(" ")) {
            val suffix = agentDisplayName.substringAfter(" ").trim()
            if (suffix == "觉醒" || suffix.contains("★") || suffix.contains("☆")) starText = suffix.replace("☆", "★")
        }
        tvName.text = pureName
        if (starText.isNotEmpty()) {
            tvStar.text = starText
            tvStar.visibility = View.VISIBLE
        } else {
            tvStar.visibility = if (forceReserveSpace) View.INVISIBLE else View.GONE
        }
        if (talents.isNotEmpty()) {
            layoutTalents.visibility = View.VISIBLE
            val agentData = com.example.yuanassist.model.AgentRepository.AGENT_MAP[pureName]
            talents.forEach { talentId ->
                val rawTalentText = agentData?.talents?.get(talentId) ?: "天赋$talentId"
                val (colorHex, displayText) = when {
                    rawTalentText.startsWith("橙") -> "#FFA726" to rawTalentText.substring(1)
                    rawTalentText.startsWith("紫") -> "#B388FF" to rawTalentText.substring(1)
                    else -> "#64B5F6" to rawTalentText
                }
                val mainColor = Color.parseColor(colorHex)
                layoutTalents.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (3 * resources.displayMetrics.density).toInt()
                    }
                    text = displayText
                    textSize = 9f
                    setTextColor(mainColor)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 4f * resources.displayMetrics.density
                        setStroke((1 * resources.displayMetrics.density).toInt(), mainColor)
                        setColor(Color.argb(38, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor)))
                    }
                    val pad = (2 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                })
            }
        } else {
            layoutTalents.visibility = View.GONE
        }
        try {
            ivAvatar.setImageBitmap(BitmapFactory.decodeStream(assets.open("$pureName.png")))
        } catch (_: Exception) {
            ivAvatar.setImageResource(R.drawable.ic_launcher_background)
        }
        parentLayout.addView(agentView)
    }

    private fun importScriptToService(scriptContent: String, configJson: String, instructionsJson: String, agentsJson: String) {
        val realScriptContent = scriptContent.replace("\\n", "\n").replace("\\t", "\t")
        val intent = Intent(this, YuanAssistService::class.java).apply {
            action = "ACTION_IMPORT_SCRIPT"
            putExtra("SCRIPT_CONTENT", realScriptContent)
            if (configJson.isNotEmpty()) putExtra("CONFIG_JSON", configJson)
            if (instructionsJson.isNotEmpty()) putExtra("INSTRUCTIONS_JSON", instructionsJson)
            if (agentsJson.isNotEmpty()) putExtra("AGENTS_JSON", agentsJson)
        }
        startService(intent)
        Toast.makeText(this, "脚本及配置已发送至悬浮窗", Toast.LENGTH_LONG).show()
    }

    private fun createReadOnlyTable(tableData: List<UploadTurnItem>): View {
        val tableContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (25 * resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(Color.parseColor("#444444"))
            setPadding(2, 2, 2, 2)
        }
        fun createCell(textStr: String, weight: Float, isHeader: Boolean = false): TextView {
            return TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(1, 1, 1, 1)
                }
                text = textStr
                textSize = if (isHeader) 13f else 12f
                setTextColor(Color.parseColor(if (isHeader) "#E5C07B" else "#E0E0E0"))
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor(if (isHeader) "#2D2D2D" else "#1A1A1A"))
                val padV = (10 * resources.displayMetrics.density).toInt()
                setPadding(0, padV, 0, padV)
            }
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(createCell("回合", 1.2f, true))
        for (i in 1..5) headerRow.addView(createCell(i.toString(), 1f, true))
        headerRow.addView(createCell("备注", 2f, true))
        tableContainer.addView(headerRow)
        tableData.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(createCell(item.turnNum.toString(), 1.2f))
            for (i in 0 until 5) {
                val actionText = item.actions.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "-"
                row.addView(createCell(actionText, 1f))
            }
            row.addView(createCell(item.remark, 2f))
            tableContainer.addView(row)
        }
        tableContainer.tag = "DYNAMIC_TABLE_CONTENT"
        return tableContainer
    }

    private fun parseScriptContentToTableData(text: String): List<UploadTurnItem> {
        val realText = text.replace("\\n", "\n").replace("\\t", "\t")
        val items = mutableListOf<UploadTurnItem>()
        var currentTurn = 1
        for (line in realText.split("\n")) {
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) continue
            val parts = if (rawLine.contains("\t")) rawLine.split("\t") else rawLine.trim().split(Regex("\\s+"))
            val startIndex = if (parts.isNotEmpty() && (parts[0].contains("回") || parts[0].all { it.isDigit() })) 1 else 0
            val effectiveStartIndex = if (startIndex == 1 && parts.firstOrNull()?.trim().isNullOrEmpty()) 0 else startIndex
            val actions = mutableListOf<String>()
            var charIdx = 0
            for (i in effectiveStartIndex until parts.size) {
                if (charIdx >= 5) break
                val actionText = parts[i].trim()
                actions.add(if (actionText == "-") "" else actionText)
                charIdx++
            }
            while (actions.size < 5) actions.add("")
            items.add(UploadTurnItem(currentTurn, actions))
            currentTurn++
        }
        return items
    }
}
