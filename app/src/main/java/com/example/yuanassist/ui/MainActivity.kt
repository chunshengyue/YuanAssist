package com.example.yuanassist.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
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
import com.bumptech.glide.Glide
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
import com.example.yuanassist.utils.CloudDailyScriptReadStore
import com.example.yuanassist.utils.DialogUtils
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
        private const val AUTHOR_HOMEPAGE_URL = "https://www.xiaohongshu.com/user/profile/66b9ca9d000000000d02489e?xsec_token=YBdKvMYYB1EsXgYkSxzK4QKna9qnPYQKfDMLYnLXIjzgY%3D&xsec_source=app_share&xhsshare=&shareRedId=ODxEOUZGPU02NzUyOTgwNjZHOTk4PT9O&apptime=1783404213&share_id=7749f56a846742109835098a7eeb5f5d&share_channel=wechat"
        private const val BIUBIU_LINK_URL = "https://www.biubiu001.com/?cfrom=yuanassist"
        private const val MAAYUAN_LINK_URL = "https://maayuan.com/"
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
    private var homeBadgesRequestVersion = 0
    private var latestAdminCloudScriptIds: List<String> = emptyList()
    private var tempAvatarUri: Uri? = null
    private var currentAvatarPreview: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        homeActionHandler = HomeActionHandler(this) { latestVersionName ->
            homeOverlayState = homeOverlayState.copy(latestVersionName = latestVersionName)
        }
        debugWorkbenchCoordinator = DebugWorkbenchCoordinator(this) { state ->
            debugWorkbenchState = state
        }
        debugWorkbenchCoordinator.initialize()
        selectedTab = resolveTargetTab(intent?.getStringExtra(EXTRA_TARGET_TAB))
        refreshShellState()
        homeActionHandler.refreshLatestVersion()
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
                    onOpenOneKeyDaily = homeActionHandler::openOneKeyDaily,
                    onOpenBirdFood = homeActionHandler::openBirdFood,
                    onOpenMainline624 = homeActionHandler::openMainline624,
                    onOpenStargazing = homeActionHandler::openStargazing,
                    onOpenInventoryStitch = homeActionHandler::openInventoryStitch,
                    onOpenXiuweiCalculator = homeActionHandler::openXiuweiCalculator,
                    onOpenBoxOcr = homeActionHandler::startBoxOcr,
                    onOpenCoordinatePicker = homeActionHandler::startCoordinatePicker,
                    onOpenScriptRecorder = homeActionHandler::startDailyScriptRecorder,
                    onOpenRunLog = homeActionHandler::openRunLog,
                    onOpenDebugTab = { selectedTab = MainTab.DEBUG },
                    onOpenFaq = homeActionHandler::openFaq,
                    onOpenFeedback = homeActionHandler::openFeedbackCenter,
                    onOpenScriptLibrary = homeActionHandler::openScriptLibrary,
                    onOpenCloudDailyScript = {
                        CloudDailyScriptReadStore.markAdminScriptIdsRead(this, latestAdminCloudScriptIds)
                        homeOverlayState = homeOverlayState.copy(hasUnreadAdminCloudScript = false)
                        startActivity(Intent(this, CloudDailyScriptListActivity::class.java))
                    },
                    onOpenAuthorHomepage = ::openAuthorHomepage,
                    onOpenBiubiuLink = ::openBiubiuLink,
                    onOpenMaaYuanLink = ::openMaaYuanLink,
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
                    onMoveCombatRoi = debugWorkbenchCoordinator::moveCombatRoi,
                    onOpenCombatRoiDialog = debugWorkbenchCoordinator::openCombatRoiDialog,
                    onDismissCombatRoiDialog = debugWorkbenchCoordinator::dismissCombatRoiDialog,
                    onSaveCombatRoi = debugWorkbenchCoordinator::saveCombatRoi,
                    onResetCombatRoi = debugWorkbenchCoordinator::resetCombatRoi,
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
                    onOpenGlobalSettings = homeActionHandler::openGlobalSettings,
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
        refreshHomeBadges()
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
        DialogUtils.showStyledDialog(
            AlertDialog.Builder(DialogUtils.getThemeContext(this))
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("知道了", null),
        )
    }

    private fun getAppVersionName(): String {
        return runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }

    private fun refreshHomeOverlayState() {
        homeOverlayState = HomeOverlayState(
            combatWindowOpen = homeActionHandler.isCombatWindowOpen(),
            hasUnreadAdminCloudScript = homeOverlayState.hasUnreadAdminCloudScript,
            currentVersionName = getAppVersionName(),
            latestVersionName = homeOverlayState.latestVersionName,
        )
    }

    private fun refreshHomeBadges() {
        val currentUser = SupabaseRepository.getCurrentUser(this)
        if (currentUser == null) {
            homeBadgesRequestVersion++
            latestAdminCloudScriptIds = emptyList()
            homeOverlayState = homeOverlayState.copy(hasUnreadAdminCloudScript = false)
            mineProfileState = mineProfileState.copy(unreadMessageCount = 0)
            return
        }

        val requestVersion = ++homeBadgesRequestVersion
        SupabaseRepository.getHomeBadges(
            context = this,
            onSuccess = { scripts ->
                runOnUiThread {
                    if (requestVersion != homeBadgesRequestVersion) return@runOnUiThread
                    if (SupabaseRepository.getCurrentUser(this) == null) return@runOnUiThread
                    latestAdminCloudScriptIds = scripts.adminCloudScriptIds
                    homeOverlayState = homeOverlayState.copy(
                        hasUnreadAdminCloudScript = CloudDailyScriptReadStore.hasUnreadAdminScriptIds(
                            this,
                            scripts.adminCloudScriptIds,
                        ),
                    )
                    mineProfileState = mineProfileState.copy(
                        unreadMessageCount = scripts.unreadMessageCount,
                    )
                }
            },
            onError = {},
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
                unreadMessageCount = 0,
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
            unreadMessageCount = mineProfileState.unreadMessageCount,
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
                    refreshShellState()
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
                    refreshShellState()
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

    private fun openBiubiuLink() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BIUBIU_LINK_URL)))
        }.onFailure {
            Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAuthorHomepage() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AUTHOR_HOMEPAGE_URL)))
        }.onFailure {
            Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMaaYuanLink() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAAYUAN_LINK_URL)))
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
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
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
        DialogUtils.styleAlertDialog(dialog)
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
