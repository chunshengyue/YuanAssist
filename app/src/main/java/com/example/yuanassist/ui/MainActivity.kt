package com.example.yuanassist.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.ui.main.DebugTabActions
import com.example.yuanassist.ui.main.DebugWorkbenchCoordinator
import com.example.yuanassist.ui.main.DebugWorkbenchState
import com.example.yuanassist.ui.main.HomeActionHandler
import com.example.yuanassist.ui.main.HomeOverlayState
import com.example.yuanassist.ui.main.HomeTabActions
import com.example.yuanassist.ui.main.JobTabActions
import com.example.yuanassist.ui.main.MainShellScreen
import com.example.yuanassist.ui.main.MainTab
import com.example.yuanassist.ui.main.MineProfileState
import com.example.yuanassist.ui.main.MineTabActions
import com.example.yuanassist.utils.ConfigManager
import com.example.yuanassist.utils.isFeedbackAdminDevice
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_TAB = "extra_target_tab"
        const val TARGET_TAB_HOME = "home"
        const val TARGET_TAB_PROFILE = "profile"
        const val TARGET_TAB_STRATEGY = "strategy"

        private const val USER_CACHE_PREFS = "user_cache"
        private const val ANNOUNCEMENT_PREFS = "announcement_prefs"
        private const val KEY_LAST_ANNOUNCEMENT_VERSION = "last_announcement_version"
        private const val OFFICIAL_SITE_URL = "https://yuanassist.space"
        private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
        private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
        private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
            "吕布", "刘璋", "夏侯渊", "酆公珠", "酆公玖", "法正", "庞德",
            "SP陈登", "SP史子渺", "曹丕", "程普", "钟繇", "蒯良", "陈群",
            "卢植", "简雍", "郭女王", "周忠", "陈纪", "陈应",
        )
        private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
            "庞曦",
            "SP史子眇",
        )
    }

    private lateinit var homeActionHandler: HomeActionHandler
    private lateinit var debugWorkbenchCoordinator: DebugWorkbenchCoordinator
    private val pickAvatarImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            tempAvatarUri = uri
            currentAvatarPreview?.let { preview ->
                if (uri != null) {
                    Glide.with(this).load(uri).circleCrop().into(preview)
                }
            }
        }

    private var selectedTab by mutableStateOf(MainTab.HOME)
    private var homeOverlayState by mutableStateOf(HomeOverlayState())
    private var mineProfileState by mutableStateOf(MineProfileState())
    private var debugWorkbenchState by mutableStateOf(DebugWorkbenchState())
    private var tempAvatarUri: Uri? = null
    private var currentAvatarPreview: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        homeActionHandler = HomeActionHandler(this)
        debugWorkbenchCoordinator = DebugWorkbenchCoordinator(this) { state ->
            debugWorkbenchState = state
        }
        debugWorkbenchCoordinator.initialize()
        selectedTab = resolveTargetTab(intent?.getStringExtra(EXTRA_TARGET_TAB))
        refreshShellState()
        requestLatestAnnouncementOnLaunch()

        setContent {
            val imagePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent(),
                onResult = debugWorkbenchCoordinator::handleImagePicked,
            )
            MainShellScreen(
                selectedTab = selectedTab,
                homeOverlayState = homeOverlayState,
                mineProfileState = mineProfileState,
                debugWorkbenchState = debugWorkbenchState,
                homeActions = HomeTabActions(
                    onToggleCombat = {
                        homeActionHandler.toggleCombatOverlay()
                        refreshHomeOverlayState()
                    },
                    onOpenSettings = homeActionHandler::openSettings,
                    onOpenBirdFood = homeActionHandler::openBirdFood,
                    onOpenMainline624 = homeActionHandler::openMainline624,
                    onOpenStargazing = homeActionHandler::openStargazing,
                    onOpenAilao15Min = homeActionHandler::openAilao15Min,
                    onOpenPiJingZhanJi = homeActionHandler::openPiJingZhanJi,
                    onOpenInventoryStitch = homeActionHandler::openInventoryStitch,
                    onOpenBoxOcr = homeActionHandler::startBoxOcr,
                    onOpenCoordinatePicker = homeActionHandler::startCoordinatePicker,
                    onOpenScriptRecorder = homeActionHandler::startDailyScriptRecorder,
                    onOpenRunLog = homeActionHandler::openRunLog,
                    onOpenDebugTab = { selectedTab = MainTab.DEBUG },
                    onOpenFaq = homeActionHandler::openFaq,
                    onOpenFeedback = homeActionHandler::openFeedbackCenter,
                    onOpenScriptLibrary = homeActionHandler::openScriptLibrary,
                    onCheckUpdate = homeActionHandler::checkUpdate,
                ),
                jobActions = JobTabActions(
                    onOpenCommunity = {
                        startActivity(JobStationListActivity.createIntent(this, JobStationListActivity.SourceMode.COMMUNITY))
                    },
                    onOpenMaaYuan = {
                        startActivity(JobStationListActivity.createIntent(this, JobStationListActivity.SourceMode.MAA))
                    },
                    onOpenPublishStrategy = {
                        startActivity(Intent(this, UploadStrategyActivity::class.java))
                    },
                    onOpenCharacterImport = homeActionHandler::openCharacterImport,
                ),
                debugActions = DebugTabActions(
                    onPickImage = { imagePicker.launch("image/*") },
                    onSelectTask = debugWorkbenchCoordinator::selectTask,
                    onSelectTemplate = debugWorkbenchCoordinator::selectTemplate,
                    onSelectScope = debugWorkbenchCoordinator::selectScope,
                    onRunTest = debugWorkbenchCoordinator::runCurrentTest,
                    onReplaceTemplate = debugWorkbenchCoordinator::replaceCurrentTemplate,
                    onDismissReplacementDialog = debugWorkbenchCoordinator::dismissReplacementDialog,
                    onConfirmReplacement = debugWorkbenchCoordinator::confirmReplacement,
                    onRestoreTemplate = debugWorkbenchCoordinator::restoreCurrentTemplate,
                    onDelayInputChange = debugWorkbenchCoordinator::updateDelayInput,
                    onSaveDelay = debugWorkbenchCoordinator::saveDelayIncrement,
                    onClearDelay = debugWorkbenchCoordinator::clearDelayIncrement,
                    onCopyLog = debugWorkbenchCoordinator::copyLog,
                ),
                mineActions = MineTabActions(
                    onPrimaryAction = {
                        performOneClickLogin()
                    },
                    onEditNickname = ::showEditNicknameDialog,
                    onOpenProfileCenter = ::showEditAvatarDialog,
                    onSyncProfile = if (mineProfileState.isLoggedIn) ::syncDataFromServer else null,
                    onOpenPublished = { startActivity(Intent(this, MyPublishedActivity::class.java)) },
                    onOpenStone = { startActivity(Intent(this, MyStoneActivity::class.java)) },
                    onOpenFavorite = { startActivity(Intent(this, MyFavoriteActivity::class.java)) },
                    onOpenMessage = ::openMyMessage,
                    onOpenOfficialSite = ::openOfficialSite,
                    onOpenFeedbackAdmin = { startActivity(Intent(this, FeedbackAdminActivity::class.java)) },
                    onOpenExcludedAgents = ::showExcludedAgentsDialog,
                ),
                onSelectTab = { selectedTab = it },
            )
        }

    }

    override fun onResume() {
        super.onResume()
        refreshShellState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val target = intent.getStringExtra(EXTRA_TARGET_TAB)
        selectedTab = resolveTargetTab(target)
        refreshShellState()
    }

    fun navigateToTab(tab: MainTab) {
        when (tab) {
            MainTab.HOME -> selectedTab = MainTab.HOME
            MainTab.JOB -> selectedTab = MainTab.JOB
            MainTab.DEBUG -> selectedTab = MainTab.DEBUG
            MainTab.MINE -> selectedTab = MainTab.MINE
        }
    }

    fun setBottomNavVisible(visible: Boolean) {
        // Compose 主壳不再使用旧 BottomNavigationView，这里保留空实现给旧调用方兜住。
    }

    private fun refreshShellState() {
        refreshHomeOverlayState()
        refreshMineProfileState()
        if (::debugWorkbenchCoordinator.isInitialized) {
            debugWorkbenchCoordinator.refreshFromExternalChanges()
        }
    }

    private fun requestLatestAnnouncementOnLaunch() {
        SupabaseRepository.getLatestAnnouncement(
            onSuccess = { announcement ->
                val onlineVersion = announcement.version
                if (onlineVersion <= 0) return@getLatestAnnouncement

                val prefs = getSharedPreferences(ANNOUNCEMENT_PREFS, Context.MODE_PRIVATE)
                val localVersion = prefs.getInt(KEY_LAST_ANNOUNCEMENT_VERSION, 0)
                if (localVersion >= onlineVersion) return@getLatestAnnouncement

                prefs.edit()
                    .putInt(KEY_LAST_ANNOUNCEMENT_VERSION, onlineVersion)
                    .apply()
                showAnnouncementDialog(
                    title = announcement.title.ifBlank { "公告" },
                    content = announcement.content.ifBlank { "暂无公告内容" },
                )
            },
            onError = {},
        )
    }

    private fun showAnnouncementDialog(title: String, content: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun refreshHomeOverlayState() {
        homeOverlayState = HomeOverlayState(
            combatWindowOpen = homeActionHandler.isCombatWindowOpen(),
        )
    }

    private fun refreshMineProfileState() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        val isFeedbackAdmin = isFeedbackAdminDevice(SupabaseRepository.currentDeviceId(this))
        if (currentUser == null) {
            mineProfileState = MineProfileState(
                isLoggedIn = false,
                isFeedbackAdmin = isFeedbackAdmin,
                nickname = "未登录",
                detail = "点击下方按钮绑定当前设备",
                avatarFallback = "我",
                avatarUrl = null,
            )
            return
        }

        val prefs = getSharedPreferences(USER_CACHE_PREFS, Context.MODE_PRIVATE)
        val nickname = prefs.getString("nickname", currentUser.nickname).orEmpty().ifBlank { "热心玩家" }
        val avatarUrl = prefs.getString("avatarUrl", currentUser.avatarUrl)
            ?.takeIf { it.isNotBlank() }
            ?: currentUser.avatarUrl?.takeIf { it.isNotBlank() }
        val detail = "设备ID: ${currentUser.username}"
        mineProfileState = MineProfileState(
            isLoggedIn = true,
            isFeedbackAdmin = isFeedbackAdmin,
            nickname = nickname,
            detail = detail,
            avatarFallback = nickname.firstOrNull()?.toString() ?: "我",
            avatarUrl = avatarUrl,
        )
    }

    private fun performOneClickLogin() {
        Toast.makeText(this, "正在验证设备...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.loginWithDevice(
            context = this,
            onSuccess = { user ->
                runOnUiThread {
                    Toast.makeText(this, "账号已同步", Toast.LENGTH_SHORT).show()
                    saveToLocalCache(user)
                    refreshMineProfileState()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun syncDataFromServer() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            Toast.makeText(this, "未登录，无法同步", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在同步...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.refreshCurrentUser(
            context = this,
            onSuccess = { user ->
                runOnUiThread {
                    Toast.makeText(this, "同步成功", Toast.LENGTH_SHORT).show()
                    saveToLocalCache(user)
                    refreshMineProfileState()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(this, "同步失败: $message", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun saveToLocalCache(user: MyUser) {
        getSharedPreferences(USER_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("nickname", user.nickname)
            .putString("avatarUrl", user.avatarUrl)
            .apply()
    }

    private fun openMyMessage() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            Toast.makeText(this, "请先登录后再查看消息", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, MyMessageActivity::class.java))
    }

    private fun openOfficialSite() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_SITE_URL)))
        }.onFailure {
            Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditNicknameDialog() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            Toast.makeText(this, "请先登录后再修改昵称", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences(USER_CACHE_PREFS, Context.MODE_PRIVATE)
        val currentName = prefs.getString("nickname", currentUser.nickname) ?: ""
        val editText = EditText(this).apply {
            setText(currentName)
            setPadding(32, 28, 32, 28)
            setTextColor(Color.parseColor("#75322D"))
            setHintTextColor(Color.parseColor("#8A6B5E"))
            setBackgroundResource(R.drawable.bg_stone_empty_panel)
        }
        val dialogTitle = TextView(this).apply {
            text = "修改昵称"
            textSize = 18f
            setPadding(0, 28, 0, 8)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#75322D"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(dialogTitle)
            .setView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 16, 28, 0)
                    addView(editText)
                },
            )
            .setPositiveButton("确认") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    updateUserProfile(newName, null)
                }
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_stone_section_card)
    }

    private fun showEditAvatarDialog() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            Toast.makeText(this, "请先登录后再修改头像", Toast.LENGTH_SHORT).show()
            return
        }
        tempAvatarUri = null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_avatar_preview, null)
        val dialogAvatarPreview = dialogView.findViewById<ImageView>(R.id.iv_dialog_avatar_preview)
        val btnSelect = dialogView.findViewById<Button>(R.id.btn_select_new_avatar)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_dialog_confirm)
        currentAvatarPreview = dialogAvatarPreview
        val prefs = getSharedPreferences(USER_CACHE_PREFS, Context.MODE_PRIVATE)
        val currentAvatar = prefs.getString("avatarUrl", currentUser.avatarUrl)
        Glide.with(this)
            .load(currentAvatar)
            .circleCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .into(dialogAvatarPreview)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnSelect.setOnClickListener { pickAvatarImageLauncher.launch("image/*") }
        btnCancel.setOnClickListener {
            currentAvatarPreview = null
            tempAvatarUri = null
            dialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            val selectedUri = tempAvatarUri
            if (selectedUri == null) {
                currentAvatarPreview = null
                tempAvatarUri = null
                dialog.dismiss()
                return@setOnClickListener
            }
            btnConfirm.text = "上传中..."
            btnConfirm.isEnabled = false
            btnCancel.isEnabled = false
            btnSelect.isEnabled = false
            val cacheFile = uriToCacheFile(selectedUri)
            if (cacheFile == null) {
                Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
                btnConfirm.text = "确认上传"
                btnConfirm.isEnabled = true
                btnCancel.isEnabled = true
                btnSelect.isEnabled = true
                return@setOnClickListener
            }
            uploadImageToImageBed(
                file = cacheFile,
                onSuccess = { url ->
                    runOnUiThread {
                        updateUserProfile(null, url)
                        cacheFile.delete()
                        currentAvatarPreview = null
                        tempAvatarUri = null
                        dialog.dismiss()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "图片上传失败: $error", Toast.LENGTH_SHORT).show()
                        btnConfirm.text = "确认上传"
                        btnConfirm.isEnabled = true
                        btnCancel.isEnabled = true
                        btnSelect.isEnabled = true
                    }
                },
            )
        }
        dialog.setOnDismissListener {
            currentAvatarPreview = null
            tempAvatarUri = null
        }
        dialog.show()
    }

    private fun updateUserProfile(newNickname: String?, newAvatarUrl: String?) {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            Toast.makeText(this, "状态异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }
        SupabaseRepository.updateCurrentUser(
            context = this,
            nickname = newNickname,
            avatarUrl = newAvatarUrl,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "更新成功", Toast.LENGTH_SHORT).show()
                    saveToLocalCache(it)
                    refreshMineProfileState()
                }
            },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun showExcludedAgentsDialog() {
        val filterPrefs = getSharedPreferences(PREFS_AGENT_FILTER, Context.MODE_PRIVATE)
        val defaultChecked = filterPrefs.getBoolean(KEY_SHOW_DAIHAOYUAN, false)
        val baseAgents = AgentRepository.ALL_AGENTS.filterNot { it in DAIHAOYUAN_HIDDEN_ALIASES }
        fun buildAgentList(includeDaihaoYuan: Boolean): List<String> {
            val result = LinkedHashSet<String>()
            if (includeDaihaoYuan) {
                result.addAll(DAIHAOYUAN_EXTRA_AGENTS)
            }
            result.addAll(baseAgents)
            return result.toList()
        }
        val selectedAgents = ConfigManager.getExcludedAgents(this).toMutableSet()
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_excluded_agents, null)
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
                loadAgentAvatar(agentName, holder.avatarView)
                holder.avatarFrame.background = createExcludedAgentBackground(selected)
                holder.overlayView.setBackgroundColor(
                    Color.parseColor(if (selected) "#36000000" else "#00000000"),
                )
                holder.nameView.setTextColor(
                    Color.parseColor(if (selected) "#75322D" else "#8A6B5E"),
                )
                holder.itemView.setOnClickListener {
                    if (selectedAgents.contains(agentName)) selectedAgents.remove(agentName) else selectedAgents.add(agentName)
                    updateExcludedAgentCount(countView, selectedAgents.size)
                    notifyItemChanged(position)
                }
            }
            override fun getItemCount(): Int = displayAgents.size
        }
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = adapter
        updateExcludedAgentCount(countView, selectedAgents.size)
        filterBox.buttonTintList = ColorStateList.valueOf(Color.parseColor("#C79C5C"))
        filterRow.setOnClickListener { filterBox.isChecked = !filterBox.isChecked }
        val dialogTitle = TextView(this).apply {
            text = "排除密探"
            textSize = 18f
            setPadding(0, 28, 0, 8)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#75322D"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                ConfigManager.saveExcludedAgents(this, selectedAgents)
                Toast.makeText(this, "排除密探已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清空") { _, _ ->
                ConfigManager.saveExcludedAgents(this, emptySet())
                Toast.makeText(this, "已清空排除密探", Toast.LENGTH_SHORT).show()
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
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshDisplayAgents() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        filterBox.setOnCheckedChangeListener { _, isChecked ->
            filterPrefs.edit().putBoolean(KEY_SHOW_DAIHAOYUAN, isChecked).apply()
            refreshDisplayAgents()
        }
        filterBox.isChecked = defaultChecked
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_stone_section_card)
        val screenWidth = resources.displayMetrics.widthPixels
        dialog.window?.setLayout((screenWidth * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateExcludedAgentCount(view: TextView, count: Int) {
        view.text = "已选 $count 个"
    }

    private fun createExcludedAgentBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 10f * resources.displayMetrics.density
            setColor(Color.parseColor("#FFF7EA"))
            setStroke(
                (1.2f * resources.displayMetrics.density).toInt(),
                Color.parseColor(if (selected) "#C79C5C" else "#80B57A45"),
            )
        }
    }

    private fun loadAgentAvatar(agentName: String, imageView: ImageView) {
        try {
            val bitmap = BitmapFactory.decodeStream(assets.open("$agentName.png"))
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

    private fun uriToCacheFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "avatar_${System.currentTimeMillis()}.png")
            file.outputStream().use { inputStream.copyTo(it) }
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadImageToImageBed(
        file: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val uploadUrl = "https://img.scdn.io/api/v1.php"
        val client = okhttp3.OkHttpClient()
        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, fileBody)
            .addFormDataPart("outputFormat", "webp")
            .build()
        val request = okhttp3.Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError("网络请求失败")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = org.json.JSONObject(responseBody)
                        if (json.optBoolean("success")) onSuccess(json.optString("url"))
                        else onError(json.optString("message", "上传被拒"))
                    } catch (_: Exception) {
                        onError("JSON解析失败")
                    }
                } else {
                    onError("HTTP ${response.code}")
                }
            }
        })
    }

    private fun resolveTargetTab(target: String?): MainTab {
        return when (target) {
            TARGET_TAB_PROFILE -> MainTab.MINE
            TARGET_TAB_STRATEGY -> MainTab.JOB
            else -> MainTab.HOME
        }
    }
}
