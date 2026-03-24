package com.example.yuanassist.ui

import android.provider.Settings
import cn.bmob.v3.BmobUser
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.SaveListener
import com.example.yuanassist.model.MyUser
import com.example.yuanassist.model.strategy_detail
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.core.LocalScriptJson
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.model.StrategyPreviewData
import com.google.gson.Gson
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class UploadStrategyActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
        private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
        private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
            "庞羲", "吕布", "刘璋", "夏侯渊", "鄷公珠", "鄷公玖", "法正", "庞德",
            "SP陈登", "SP史子渺", "曹丕", "程普", "钟繇", "蒯良", "马腾", "陈群",
            "卢植", "简雍", "郭女王", "周忠"
        )

        private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
            "庞曦",
            "SP史子眇"
        )
    }

    // 导入与表格区块
    private lateinit var layoutTableContainer: LinearLayout
    private lateinit var rvUploadTable: RecyclerView
    private lateinit var tvUploadInstructions: TextView
    private lateinit var layoutImportLibrary: LinearLayout
    private lateinit var tvSelectedScript: TextView
    private lateinit var layoutImportText: LinearLayout
    private lateinit var etImportText: EditText
    private var btnTableActionLeft: Button? = null
    private var currentInstructionsJson: String? = null
    private lateinit var layoutAgentSlots: LinearLayout
    private lateinit var layoutAgentImageUpload: RelativeLayout
    private lateinit var layoutStrategyImageUpload: RelativeLayout
    private lateinit var etAgentTextDesc: EditText
    private var currentImageUploadTarget = 0
    private var agentImageUri: Uri? = null
    private var strategyImageUri: Uri? = null
    private val selectedAgentsForSlots = arrayOfNulls<String>(5)

    // 配置参数区块
    private var currentAttackDelay = 2500L
    private var currentSkillDelay = 4000L
    private var currentWaitTurn = 8000L

    // 图片选择器注册
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                if (currentImageUploadTarget == 1) {
                    // 处理密探截图
                    agentImageUri = uri
                    findViewById<ImageView>(R.id.iv_agent_image_preview).apply {
                        setImageURI(uri)
                        visibility = View.VISIBLE
                    }
                    findViewById<View>(R.id.ll_agent_image_placeholder).visibility = View.GONE
                    findViewById<View>(R.id.tv_agent_image_reupload).visibility = View.VISIBLE
                } else if (currentImageUploadTarget == 2) {
                    // 处理攻略原图
                    strategyImageUri = uri
                    findViewById<ImageView>(R.id.iv_strategy_image_preview).apply {
                        setImageURI(uri)
                        visibility = View.VISIBLE
                    }
                    findViewById<View>(R.id.ll_strategy_image_placeholder).visibility = View.GONE
                    findViewById<View>(R.id.tv_strategy_image_reupload).visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_strategy)

        initTopBar()
        initViews()
        initTabs()
        initAgentSlots()
        loadSystemConfig()

        // 默认尝试读取当前悬浮窗数据
        loadCurrentWindowData()
    }

    private fun initViews() {
        layoutTableContainer = findViewById(R.id.layout_table_container)
        rvUploadTable = findViewById(R.id.rv_upload_table)
        tvUploadInstructions = findViewById(R.id.tv_upload_instructions)
        layoutImportLibrary = findViewById(R.id.layout_import_library)
        tvSelectedScript = findViewById(R.id.tv_selected_script)
        layoutImportText = findViewById(R.id.layout_import_text)
        etImportText = findViewById(R.id.et_import_text)
        btnTableActionLeft = findViewById(R.id.btn_table_action_left)
        layoutAgentSlots = findViewById(R.id.layout_agent_slots)
        layoutAgentImageUpload = findViewById(R.id.layout_agent_image_upload)
        layoutStrategyImageUpload = findViewById(R.id.layout_strategy_image_upload)
        etAgentTextDesc = findViewById(R.id.et_agent_text_desc)

        // 绑定底部按钮
        findViewById<TextView>(R.id.btn_upload_back).setOnClickListener { finish() }

        // 🟢 绑定预览按钮逻辑
        findViewById<Button>(R.id.btn_preview_strategy).setOnClickListener {
            val aType = when {
                layoutAgentSlots.visibility == View.VISIBLE -> 0
                layoutAgentImageUpload.visibility == View.VISIBLE -> 1
                else -> 2
            }

            val tData = (rvUploadTable.adapter as? UploadTableAdapter)?.getDataList()

            // 读取每个槽位的名字、星级、天赋，并拼接成 StrategyDetailActivity 支援的格式
            val builtAgents = mutableListOf<String>()
            for (i in 0 until 5) {
                val slot = layoutAgentSlots.getChildAt(i)
                val name = selectedAgentsForSlots[i]
                if (name != null) {
                    val t1 = slot.findViewById<TextView>(R.id.tv_talent_1).tag as? Int
                    val t2 = slot.findViewById<TextView>(R.id.tv_talent_2).tag as? Int
                    val t3 = slot.findViewById<TextView>(R.id.tv_talent_3).tag as? Int
                    val talentsStr = listOfNotNull(t1, t2, t3).joinToString("、")

                    val star = slot.findViewById<TextView>(R.id.tv_upload_agent_star).tag as? String
                    val displayName = if (star != null) "$name $star" else name

                    if (talentsStr.isNotEmpty()) {
                        builtAgents.add("$displayName-$talentsStr")
                    } else {
                        builtAgents.add(displayName)
                    }
                }
            }

            val previewData = StrategyPreviewData(
                title = findViewById<EditText>(R.id.et_strategy_title).text.toString().ifEmpty { "未命名攻略" },
                content = findViewById<EditText>(R.id.et_strategy_content).text.toString(),
                attackDelay = currentAttackDelay,
                skillDelay = currentSkillDelay,
                waitTurn = currentWaitTurn,
                strategyImageUri = strategyImageUri?.toString(),
                agentType = aType,
                agentSelection = builtAgents,
                agentImageUri = agentImageUri?.toString(),
                agentTextDesc = etAgentTextDesc.text.toString(),
                tableData = tData,
                instructionsJson = currentInstructionsJson
            )

            val intent = Intent(this, StrategyDetailActivity::class.java).apply {
                putExtra("IS_PREVIEW", true)
                putExtra("PREVIEW_DATA_JSON", Gson().toJson(previewData))
            }
            startActivity(intent)
        }

        // 绑定图片上传区域点击
        layoutAgentImageUpload.setOnClickListener {
            currentImageUploadTarget = 1
            openImagePicker()
        }
        layoutStrategyImageUpload.setOnClickListener {
            currentImageUploadTarget = 2
            openImagePicker()
        }

        // 绑定文字解析功能
        findViewById<Button>(R.id.btn_confirm_text).setOnClickListener {
            var text = etImportText.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val items = parseTextToUploadItems(text)
            if (items.isNotEmpty()) {
                layoutImportText.visibility = View.GONE
                renderTable(items, "文字导入成功")
                btnTableActionLeft?.apply {
                    visibility = View.VISIBLE
                    text = "↺ 返回修改文本"
                    setOnClickListener {
                        layoutTableContainer.visibility = View.GONE
                        layoutImportText.visibility = View.VISIBLE
                    }
                }
            } else {
                Toast.makeText(this, "解析失败，请检查格式", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_clear_text).setOnClickListener {
            etImportText.setText("")
            layoutTableContainer.visibility = View.GONE
            layoutImportText.visibility = View.VISIBLE
        }

        tvSelectedScript.setOnClickListener { showScriptLibraryDialog() }
        findViewById<Button>(R.id.btn_publish_strategy).setOnClickListener {
            val title = findViewById<EditText>(R.id.et_strategy_title).text.toString().trim()
            val originalUrl = findViewById<EditText>(R.id.et_original_post_url)?.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "攻略标题不能为空！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            findViewById<Button>(R.id.btn_publish_strategy).isEnabled = false
            Toast.makeText(this, "正在验证身份...", Toast.LENGTH_SHORT).show()

            // 1. 自动注册/登录当前设备
            ensureUserLoggedIn(
                onSuccess = { user ->
                    // 2. 组装数据并上传
                    uploadImagesAndPublish(title, originalUrl,user)
                },
                onError = { errorMsg ->
                    Toast.makeText(this, "验证失败: $errorMsg", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.btn_publish_strategy).isEnabled = true
                }
            )
        }
    }

    private fun initTopBar() {
        val topBar = findViewById<View>(R.id.top_bar)
        if (topBar != null) {
            topBar.findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }
            topBar.findViewById<TextView>(R.id.tv_top_title)?.text = "发布跟打攻略"
            topBar.findViewById<View>(R.id.tv_top_author_name)?.visibility = View.GONE

            val avatarView = topBar.findViewById<View>(R.id.iv_top_author_avatar) ?: topBar.findViewById<View>(R.id.iv_top_author_avatar)
            avatarView?.visibility = View.GONE
            (avatarView?.parent as? View)?.visibility = View.GONE

        } else {
            findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }
            findViewById<TextView>(R.id.tv_title)?.text = "发布跟打攻略"
        }
    }

    private fun loadSystemConfig() {
        val tvUploadConfig = findViewById<TextView>(R.id.tv_upload_config)

        val config = com.example.yuanassist.utils.ConfigManager.getAllConfig(this)
        currentAttackDelay = config.intervalAttack
        currentSkillDelay = config.intervalSkill
        currentWaitTurn = config.waitTurn

        tvUploadConfig.text = "参数：普${currentAttackDelay / 1000f}s / 技${currentSkillDelay / 1000f}s / 等待${currentWaitTurn / 1000f}s"
        findViewById<View>(R.id.btn_edit_config)?.setOnClickListener {
            showConfigEditDialog(tvUploadConfig)
        }
        val btnUploadBack = findViewById<TextView>(R.id.btn_upload_back)
        btnUploadBack.text = "返回"
        btnUploadBack.setOnClickListener { finish() }
    }

    private fun initTabs() {
        val tabImportCurrent = findViewById<TextView>(R.id.tab_import_current)
        val tabImportLibrary = findViewById<TextView>(R.id.tab_import_library)
        val tabImportText = findViewById<TextView>(R.id.tab_import_text)
        val importTabs = listOf(tabImportCurrent, tabImportLibrary, tabImportText)

        tabImportCurrent.setOnClickListener {
            updateTabUI(tabImportCurrent, importTabs)
            layoutImportLibrary.visibility = View.GONE
            layoutImportText.visibility = View.GONE
            loadCurrentWindowData()
        }
        tabImportLibrary.setOnClickListener {
            updateTabUI(tabImportLibrary, importTabs)
            layoutTableContainer.visibility = View.GONE
            layoutImportText.visibility = View.GONE
            layoutImportLibrary.visibility = View.VISIBLE
            showScriptLibraryDialog()
        }
        tabImportText.setOnClickListener {
            updateTabUI(tabImportText, importTabs)
            layoutTableContainer.visibility = View.GONE
            layoutImportLibrary.visibility = View.GONE
            layoutImportText.visibility = View.VISIBLE
        }

        val tabAgentSelect = findViewById<TextView>(R.id.tab_agent_select)
        val tabAgentImage = findViewById<TextView>(R.id.tab_agent_image)
        val tabAgentText = findViewById<TextView>(R.id.tab_agent_text)
        val agentTabs = listOf(tabAgentSelect, tabAgentImage, tabAgentText)

        tabAgentSelect.setOnClickListener {
            updateTabUI(tabAgentSelect, agentTabs)
            layoutAgentSlots.visibility = View.VISIBLE
            layoutAgentImageUpload.visibility = View.GONE
            etAgentTextDesc.visibility = View.GONE
        }
        tabAgentImage.setOnClickListener {
            updateTabUI(tabAgentImage, agentTabs)
            layoutAgentSlots.visibility = View.GONE
            layoutAgentImageUpload.visibility = View.VISIBLE
            etAgentTextDesc.visibility = View.GONE
        }
        tabAgentText.setOnClickListener {
            updateTabUI(tabAgentText, agentTabs)
            layoutAgentSlots.visibility = View.GONE
            layoutAgentImageUpload.visibility = View.GONE
            etAgentTextDesc.visibility = View.VISIBLE
        }
    }

    // 🔴 提取出来的公共方法：用于将指定的密探名字填入指定槽位，并自动展开星级/命盘UI
    private fun applyAgentToSlot(index: Int, agentName: String) {
        val slotView = layoutAgentSlots.getChildAt(index) ?: return
        val tvAddIcon = slotView.findViewById<TextView>(R.id.tv_agent_add_icon)
        val ivAvatar = slotView.findViewById<ImageView>(R.id.iv_upload_agent_avatar)
        val tvName = slotView.findViewById<TextView>(R.id.tv_upload_agent_name)
        val tvStar = slotView.findViewById<TextView>(R.id.tv_upload_agent_star)
        val layoutTalents = slotView.findViewById<LinearLayout>(R.id.layout_upload_talents)
        val tvTalent1 = slotView.findViewById<TextView>(R.id.tv_talent_1)
        val tvTalent2 = slotView.findViewById<TextView>(R.id.tv_talent_2)
        val tvTalent3 = slotView.findViewById<TextView>(R.id.tv_talent_3)

        // 数据记录
        selectedAgentsForSlots[index] = agentName

        // 隐藏加号，显示名字
        tvAddIcon.visibility = View.GONE
        tvName.text = agentName
        tvName.setTextColor(Color.parseColor("#E0E0E0"))

        // 加载头像并强制平铺充满（解决加号缩放异常）
        loadAgentAvatar(agentName, ivAvatar)
        ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP

        // 🔴 核心：激活并显示星级和命盘UI
        tvStar.visibility = View.VISIBLE
        layoutTalents.visibility = View.VISIBLE
        tvStar.text = "星级(选填)▾"
        tvStar.setTextColor(Color.parseColor("#E5C07B"))
        tvStar.tag = null
        resetTalentStyle(tvTalent1, "命盘一 ▾")
        resetTalentStyle(tvTalent2, "命盘二 ▾")
        resetTalentStyle(tvTalent3, "命盘三 ▾")
    }

    private fun initAgentSlots() {
        layoutAgentSlots.removeAllViews()
        for (i in 0 until 5) {
            val slotView = layoutInflater.inflate(R.layout.item_upload_agent, layoutAgentSlots, false)
            val params = slotView.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            slotView.layoutParams = params

            val cvAvatarContainer = slotView.findViewById<View>(R.id.cv_agent_avatar_container)
            val tvStar = slotView.findViewById<TextView>(R.id.tv_upload_agent_star)
            val tvTalent1 = slotView.findViewById<TextView>(R.id.tv_talent_1)
            val tvTalent2 = slotView.findViewById<TextView>(R.id.tv_talent_2)
            val tvTalent3 = slotView.findViewById<TextView>(R.id.tv_talent_3)

            // 🔴 修改：点击时直接调用刚才提取的公共方法
            cvAvatarContainer.setOnClickListener {
                showAgentSelectionDialog { selectedAgentName ->
                    applyAgentToSlot(i, selectedAgentName)
                }
            }

            tvStar.setOnClickListener {
                val stars = arrayOf("默认", "⭐", "⭐⭐", "⭐⭐⭐", "⭐⭐⭐⭐", "⭐⭐⭐⭐⭐", "觉醒")
                android.app.AlertDialog.Builder(this)
                    .setItems(stars) { _, which ->
                        if (which == 0) {
                            tvStar.text = "星级(选填)▾"
                            tvStar.setTextColor(Color.parseColor("#E5C07B"))
                            tvStar.tag = null
                        } else {
                            tvStar.text = "${stars[which]} ▾"
                            tvStar.setTextColor(Color.parseColor("#FFD700"))
                            tvStar.tag = stars[which]
                        }
                    }.show()
            }

            val talentClickListener = View.OnClickListener { view ->
                val agentName = selectedAgentsForSlots[i] ?: return@OnClickListener Toast.makeText(this, "请先选择密探", Toast.LENGTH_SHORT).show()
                val agentAttr = AgentRepository.AGENT_MAP[agentName]
                if (agentAttr == null || agentAttr.talents.isEmpty()) {
                    return@OnClickListener Toast.makeText(this, "该密探暂无命盘数据", Toast.LENGTH_SHORT).show()
                }

                val options = arrayOf("默认 (不选)") + agentAttr.talents.values.toTypedArray()

                val adapter = object : ArrayAdapter<String>(this, 0, options) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val density = resources.displayMetrics.density
                        val layout = (convertView as? LinearLayout) ?: LinearLayout(this@UploadStrategyActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding((20 * density).toInt(), (10 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
                        }

                        val tv = (layout.getChildAt(0) as? TextView) ?: TextView(this@UploadStrategyActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            textSize = 14f
                            layout.addView(this)
                        }

                        val rawText = getItem(position) ?: ""
                        if (position == 0) {
                            tv.text = rawText
                            tv.setTextColor(Color.parseColor("#888888"))
                            tv.background = null
                            tv.setPadding(0, 0, 0, 0)
                        } else {
                            val (colorHex, displayText) = when {
                                rawText.startsWith("橙") -> Pair("#FFA726", rawText.substring(1))
                                rawText.startsWith("紫") -> Pair("#B388FF", rawText.substring(1))
                                else -> Pair("#64B5F6", rawText)
                            }
                            val mainColor = Color.parseColor(colorHex)
                            tv.text = displayText
                            tv.setTextColor(mainColor)

                            tv.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 4f * density
                                setStroke((1 * density).toInt(), mainColor)
                                setColor(Color.argb(38, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor)))
                            }
                            val padV = (6 * density).toInt()
                            val padH = (12 * density).toInt()
                            tv.setPadding(padH, padV, padH, padV)
                        }
                        return layout
                    }
                }

                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("选择命盘")
                    .setAdapter(adapter) { _, which ->
                        val tv = view as TextView
                        if (which == 0) {
                            val defaultText = when (tv.id) {
                                R.id.tv_talent_1 -> "命盘一 ▾"
                                R.id.tv_talent_2 -> "命盘二 ▾"
                                else -> "命盘三 ▾"
                            }
                            resetTalentStyle(tv, defaultText)
                            tv.tag = null
                        } else {
                            val entry = agentAttr.talents.entries.elementAt(which - 1)
                            tv.tag = entry.key
                            applyTalentStyle(tv, options[which])
                        }
                    }.create()
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dark_glass)
                dialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))?.setTextColor(Color.parseColor("#E5C07B"))
            }

            tvTalent1.setOnClickListener(talentClickListener)
            tvTalent2.setOnClickListener(talentClickListener)
            tvTalent3.setOnClickListener(talentClickListener)
            layoutAgentSlots.addView(slotView)
        }
    }

    private fun loadCurrentWindowData() {
        val service = YuanAssistService.instance
        if (service == null) {
            Toast.makeText(this, "无障碍服务未运行，无法获取当前窗口", Toast.LENGTH_SHORT).show()
            layoutTableContainer.visibility = View.GONE
            return
        }

        val currentData = service.currentDisplayData
        if (currentData.isEmpty()) {
            layoutTableContainer.visibility = View.GONE
            return
        }

        val items = currentData.map { turnData ->
            UploadTurnItem(
                turnNum = turnData.turnNumber,
                actions = turnData.characterActions.map { it.toString() }
            )
        }

        val insts = service.getExportableInstructions()

        if (insts.isNotEmpty()) {
            currentInstructionsJson = Gson().toJson(insts)
            val text = insts.joinToString("\n") { "第${it.turn}回合 第${it.step}步: [${it.type}] 数值:${it.value}" }
            renderTable(items, "当前窗口附带指令：\n$text")
        } else {
            currentInstructionsJson = null
            renderTable(items, "附带指令：当前窗口暂无附加指令")
        }

        btnTableActionLeft?.apply {
            visibility = View.VISIBLE
            text = "↻ 重新读取窗口"
            setOnClickListener {
                loadCurrentWindowData()
                Toast.makeText(this@UploadStrategyActivity, "已刷新当前窗口数据", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showScriptLibraryDialog() {
        val dir = File(filesDir, "scripts")
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

        if (files.isEmpty()) {
            Toast.makeText(this, "本地脚本库为空", Toast.LENGTH_SHORT).show()
            return
        }

        val fileNames = files.map { it.name.replace(".json", "") }.toTypedArray()
        val themeContext = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog)

        android.app.AlertDialog.Builder(themeContext)
            .setTitle("请选择要导入的脚本")
            .setItems(fileNames) { _, which ->
                try {
                    val scriptObj = Gson().fromJson(files[which].readText(), LocalScriptJson::class.java)
                    tvSelectedScript.text = scriptObj.title ?: "已选择脚本"
                    val items = parseTextToUploadItems(scriptObj.scriptContent)

                    if (!scriptObj.instructions.isNullOrEmpty()) {
                        currentInstructionsJson = Gson().toJson(scriptObj.instructions)
                        val instText = scriptObj.instructions.joinToString("\n") { "第${it.turn}回合 第${it.step}步: [${it.type}] 数值:${it.value}" }
                        renderTable(items, "脚本附带指令：\n$instText")
                    } else {
                        currentInstructionsJson = null
                        renderTable(items, "该脚本无附加指令")
                    }

                    btnTableActionLeft?.visibility = View.GONE
                } catch (e: Exception) {
                    Toast.makeText(this, "脚本解析失败", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun parseTextToUploadItems(text: String): List<UploadTurnItem> {
        try {
            val jsonObject = org.json.JSONObject(text)

            if (jsonObject.has("opers") && jsonObject.has("actions")) {
                val items = mutableListOf<UploadTurnItem>()

                // 🔴 自动匹配上阵密探
                val opersArray = jsonObject.getJSONArray("opers")
                runOnUiThread {
                    for (i in 0 until minOf(5, opersArray.length())) {
                        val agentName = opersArray.getJSONObject(i).optString("name").trim()
                        if (agentName.isEmpty()) continue
                        // 🔴 这里直接调用刚才抽取的公共方法，效果和手动点击一模一样！
                        applyAgentToSlot(i, agentName)
                    }
                    Toast.makeText(this, "✅ 阵容与动作自动装载完毕！", Toast.LENGTH_SHORT).show()
                }

                val actionsObj = jsonObject.getJSONObject("actions")
                val turnKeys = actionsObj.keys().asSequence().mapNotNull { it.toIntOrNull() }.sorted().toList()

                for (turn in turnKeys) {
                    val turnActionsArray = actionsObj.getJSONArray(turn.toString())
                    val rowActions = mutableListOf<String>()

                    for (i in 0 until minOf(5, turnActionsArray.length())) {
                        val innerArray = turnActionsArray.getJSONArray(i)
                        var actionText = if (innerArray.length() > 0) innerArray.getString(0) else ""

                        actionText = actionText.replace("大", "↑")
                            .replace("下", "↓")
                            .replace("普", "A")

                        rowActions.add(actionText)
                    }

                    while (rowActions.size < 5) rowActions.add("")
                    items.add(UploadTurnItem(turn, rowActions))
                }

                return items
            }
        } catch (e: Exception) {
            // 解析 JSON 失败说明是旧格式文本，忽略错误走下面
        }

        // ================= 2. 退回备用方案：旧版的普通文本行解析 =================
        val lines = text.split("\n")
        val items = mutableListOf<UploadTurnItem>()
        var currentTurn = 1

        for (line in lines) {
            val trimLine = line.trim()
            if (trimLine.isEmpty()) continue
            val parts = trimLine.split(Regex("\\s+"))
            val startIndex = if (parts.isNotEmpty() && (parts[0].contains("回") || parts[0].all { it.isDigit() })) 1 else 0

            val actions = mutableListOf<String>()
            var charIdx = 0
            for (i in startIndex until parts.size) {
                if (charIdx >= 5) break
                actions.add(if (parts[i] == "-") "" else parts[i])
                charIdx++
            }
            while (actions.size < 5) actions.add("")

            items.add(UploadTurnItem(currentTurn, actions))
            currentTurn++
        }
        return items
    }

    private fun renderTable(items: List<UploadTurnItem>, instructionsInfo: String) {
        layoutTableContainer.visibility = View.VISIBLE
        tvUploadInstructions.text = instructionsInfo
        rvUploadTable.layoutManager = LinearLayoutManager(this)
        rvUploadTable.adapter = UploadTableAdapter(items)
    }

    private fun showConfigEditDialog(tvConfigDisplay: TextView) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_config, null)
        val etAttack = dialogView.findViewById<EditText>(R.id.et_config_attack)
        val etSkill = dialogView.findViewById<EditText>(R.id.et_config_skill)
        val etWait = dialogView.findViewById<EditText>(R.id.et_config_wait)

        etAttack.setText(currentAttackDelay.toString())
        etSkill.setText(currentSkillDelay.toString())
        etWait.setText(currentWaitTurn.toString())

        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btn_config_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btn_config_confirm).setOnClickListener {
            currentAttackDelay = etAttack.text.toString().toLongOrNull() ?: 2500L
            currentSkillDelay = etSkill.text.toString().toLongOrNull() ?: 4000L
            currentWaitTurn = etWait.text.toString().toLongOrNull() ?: 8000L
            tvConfigDisplay.text = "参数：普${currentAttackDelay / 1000f}s / 技${currentSkillDelay / 1000f}s / 等待${currentWaitTurn / 1000f}s"
            dialog.dismiss()
        }
    }

    private fun showAgentSelectionDialog(onAgentSelected: (String) -> Unit) {
        val filterPrefs = getSharedPreferences(PREFS_AGENT_FILTER, MODE_PRIVATE)
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

        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 10, 28, 0)
        }
        val filterLabel = TextView(this).apply {
            text = "代号鸢"
            textSize = 14f
            setTextColor(Color.parseColor("#E5C07B"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val filterSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        val filterBox = CheckBox(this).apply {
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E5C07B"))
        }
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@UploadStrategyActivity, 4)
            setPadding(20, 30, 20, 30)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        filterRow.addView(filterBox)
        filterRow.addView(filterLabel)
        filterRow.addView(filterSpacer)
        dialogContainer.addView(filterRow)
        dialogContainer.addView(recyclerView)

        // 🔴 1. 自定义弹窗标题的样式 (深色背景 + 金色字体)
        val titleView = TextView(this).apply {
            text = "选择出战密探"
            textSize = 18f
            setPadding(0, 40, 0, 10)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E5C07B")) // 暗金字体
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(dialogContainer)
            .create()

        val displayAgents = buildAgentList(includeDaihaoYuan = defaultChecked).toMutableList()
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val density = resources.displayMetrics.density
                val itemLayout = LinearLayout(this@UploadStrategyActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(0, 15.toInt(), 0, 15.toInt())
                }
                val ivAvatar = ImageView(this@UploadStrategyActivity).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams((50 * density).toInt(), (50 * density).toInt())
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_gold_border)
                }
                val tvName = TextView(this@UploadStrategyActivity).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (5 * density).toInt() }
                    textSize = 12f
                    // 🔴 2. 把原本的深灰色字体改成浅灰色，适应深色背景
                    setTextColor(Color.parseColor("#E0E0E0"))
                    maxLines = 1
                }
                itemLayout.addView(ivAvatar)
                itemLayout.addView(tvName)
                return object : RecyclerView.ViewHolder(itemLayout) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val agentName = displayAgents[position]
                val layout = holder.itemView as LinearLayout
                val ivAvatar = layout.getChildAt(0) as ImageView
                val tvName = layout.getChildAt(1) as TextView
                tvName.text = agentName
                loadAgentAvatar(agentName, ivAvatar)
                layout.setOnClickListener {
                    onAgentSelected(agentName)
                    dialog.dismiss()
                }
            }
            override fun getItemCount() = displayAgents.size
        }

        // 🔴 3. 把弹窗本身的背景换成深色玻璃
        recyclerView.adapter = adapter
        val refreshAgents: (Boolean) -> Unit = { includeDaihaoYuan ->
            displayAgents.clear()
            displayAgents.addAll(buildAgentList(includeDaihaoYuan))
            adapter.notifyDataSetChanged()
        }
        filterRow.setOnClickListener { filterBox.isChecked = !filterBox.isChecked }
        filterBox.setOnCheckedChangeListener { _, isChecked ->
            filterPrefs.edit().putBoolean(KEY_SHOW_DAIHAOYUAN, isChecked).apply()
            refreshAgents(isChecked)
        }
        filterBox.isChecked = defaultChecked
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dark_glass)
        dialog.show()
    }
    private fun applyTalentStyle(textView: TextView, rawTalentText: String) {
        val (colorHex, displayText) = when {
            rawTalentText.startsWith("橙") -> Pair("#FFA726", rawTalentText.substring(1))
            rawTalentText.startsWith("紫") -> Pair("#B388FF", rawTalentText.substring(1))
            else -> Pair("#64B5F6", rawTalentText)
        }
        val mainColor = Color.parseColor(colorHex)
        textView.text = "$displayText ▾"
        textView.setTextColor(mainColor)
        textView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * resources.displayMetrics.density
            setStroke((1 * resources.displayMetrics.density).toInt(), mainColor)
            setColor(Color.argb(38, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor)))
        }
        textView.setPadding(0, 0, 0, 0)
        textView.textSize = 10f
    }

    private fun resetTalentStyle(textView: TextView, defaultText: String) {
        textView.text = defaultText
        textView.setTextColor(Color.parseColor("#A0A0A0"))
        textView.background = ColorDrawable(Color.parseColor("#1AFFFFFF"))
        textView.setPadding(0, 0, 0, 0)
        textView.textSize = 10f
    }

    private fun loadAgentAvatar(agentName: String, imageView: ImageView) {
        try {
            val bitmap = BitmapFactory.decodeStream(assets.open("$agentName.png"))
            imageView.setImageBitmap(bitmap)
            imageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            imageView.visibility = View.GONE
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun updateTabUI(selectedTab: TextView, allTabs: List<TextView>) {
        for (tab in allTabs) {
            if (tab == selectedTab) {
                tab.setBackgroundResource(R.drawable.btn_dark_gold)
                tab.setTextColor(Color.parseColor("#1A1A1A"))
            } else {
                tab.setBackgroundResource(R.drawable.btn_dark_hollow)
                tab.setTextColor(Color.parseColor("#E5C07B"))
            }
        }
    }
    private fun ensureUserLoggedIn(onSuccess: (MyUser) -> Unit, onError: (String) -> Unit) {
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
                            if (le == null && lu != null) onSuccess(lu) else onError(le?.message ?: "静默登录失败")
                        }
                    })
                } else {
                    onError(e?.message ?: "注册失败")
                }
            }
        })
    }

    private fun uriToCacheFile(uri: Uri, prefix: String): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
            file.outputStream().use { inputStream.copyTo(it) }
            file
        } catch (e: Exception) { null }
    }

    private fun uploadImagesAndPublish(title: String, originalUrl: String, author: MyUser) {
        Toast.makeText(this, "正在处理数据...", Toast.LENGTH_SHORT).show()

        val aType = when {
            layoutAgentSlots.visibility == View.VISIBLE -> 0
            layoutAgentImageUpload.visibility == View.VISIBLE -> 1
            else -> 2
        }

        val builtAgents = mutableListOf<String>()
        if (aType == 0) {
            for (i in 0 until 5) {
                val slot = layoutAgentSlots.getChildAt(i)
                val name = selectedAgentsForSlots[i]
                if (name != null) {
                    val t1 = slot.findViewById<TextView>(R.id.tv_talent_1).tag as? Int
                    val t2 = slot.findViewById<TextView>(R.id.tv_talent_2).tag as? Int
                    val t3 = slot.findViewById<TextView>(R.id.tv_talent_3).tag as? Int
                    val talentsStr = listOfNotNull(t1, t2, t3).joinToString("、")

                    val starTag = slot.findViewById<TextView>(R.id.tv_upload_agent_star).tag as? String
                    val starPrefix = when (starTag) {
                        "⭐" -> "1"
                        "⭐⭐" -> "2"
                        "⭐⭐⭐" -> "3"
                        "⭐⭐⭐⭐" -> "4"
                        "⭐⭐⭐⭐⭐" -> "5"
                        "觉醒" -> "6"
                        else -> ""
                    }
                    val finalString = if (talentsStr.isNotEmpty()) "$starPrefix$name-$talentsStr" else "$starPrefix$name"
                    builtAgents.add(finalString)
                }
            }
        }

        val tData = (rvUploadTable.adapter as? UploadTableAdapter)?.getDataList() ?: emptyList()
        val scriptContentStr = tData.joinToString("\n") { item ->
            "${item.turnNum}回合\t${item.actions.joinToString("\t")}"
        }

        val configMap = mapOf(
            "intervalAttack" to currentAttackDelay,
            "intervalSkill" to currentSkillDelay,
            "waitTurn" to currentWaitTurn
        )
        val configJson = Gson().toJson(configMap)

        val contentStr = findViewById<EditText>(R.id.et_strategy_content).text.toString().trim()
        val instJson = currentInstructionsJson ?: ""

        val agentFile = if (aType == 1) agentImageUri?.let { uriToCacheFile(it, "agent") } else null
        val strategyFile = strategyImageUri?.let { uriToCacheFile(it, "strategy") }

        val filesToUpload = mutableListOf<File>()
        if (agentFile != null) filesToUpload.add(agentFile)
        if (strategyFile != null) filesToUpload.add(strategyFile)

        if (filesToUpload.isNotEmpty()) {
            Toast.makeText(this, "正在上传图片到图床...", Toast.LENGTH_SHORT).show()

            var uploadedCount = 0
            var hasError = false
            val uploadedUrls = mutableMapOf<File, String>()

            filesToUpload.forEach { file ->
                uploadImageToImageBed(file,
                    onSuccess = { url ->
                        if (hasError) return@uploadImageToImageBed
                        uploadedUrls[file] = url
                        uploadedCount++

                        if (uploadedCount == filesToUpload.size) {
                            runOnUiThread {
                                val finalAgentUrl = agentFile?.let { uploadedUrls[it] } ?: ""
                                val finalStrategyUrl = strategyFile?.let { uploadedUrls[it] } ?: ""

                                executeFinalPublish(title, author, aType, builtAgents, finalAgentUrl, finalStrategyUrl, scriptContentStr, configJson, contentStr, instJson, originalUrl)
                                filesToUpload.forEach { it.delete() }
                            }
                        }
                    },
                    onError = { errorMsg ->
                        if (!hasError) {
                            hasError = true
                            runOnUiThread {
                                Toast.makeText(this@UploadStrategyActivity, errorMsg, Toast.LENGTH_SHORT).show()
                                findViewById<Button>(R.id.btn_publish_strategy).isEnabled = true
                                filesToUpload.forEach { it.delete() }
                            }
                        }
                    }
                )
            }
        } else {
            executeFinalPublish(title, author, aType, builtAgents, "", "", scriptContentStr, configJson, contentStr, instJson, originalUrl)
        }
    }

    private fun executeFinalPublish(
        title: String, author: MyUser, aType: Int, builtAgents: List<String>,
        agentUrl: String, strategyUrl: String, scriptContent: String,
        config: String, content: String, instructions: String,originalUrl: String
    ) {
        val detail = strategy_detail()
        detail.title = title
        detail.author = author
        detail.agentType = aType
        detail.agentSelection = Gson().toJson(builtAgents)
        detail.agentImageUrl = agentUrl
        detail.agentTextDesc = etAgentTextDesc.text.toString().trim()
        detail.originalPostUrl = originalUrl
        detail.strategyImage = strategyUrl

        detail.coverUrl = when {
            strategyUrl.isNotEmpty() -> strategyUrl
            agentUrl.isNotEmpty() -> agentUrl
            else -> ""
        }

        val agentNames = builtAgents.map { raw ->
            raw.replaceFirst("^\\d+".toRegex(), "").substringBefore("-").trim()
        }
        detail.agents = agentNames.joinToString("、")

        detail.scriptContent = scriptContent
        detail.config = config
        detail.content = content
        detail.instructions = instructions

        Toast.makeText(this, "正在保存攻略数据...", Toast.LENGTH_SHORT).show()
        detail.save(object : SaveListener<String>() {
            override fun done(objectId: String?, e: BmobException?) {
                if (e == null) {
                    Toast.makeText(this@UploadStrategyActivity, "🎉 发布成功！", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this@UploadStrategyActivity, "发布失败: ${e.message}", Toast.LENGTH_LONG).show()
                    findViewById<Button>(R.id.btn_publish_strategy).isEnabled = true
                }
            }
        })
    }
    private fun uploadImageToImageBed(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
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
                onError("图床网络请求失败: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = org.json.JSONObject(responseBody)
                        if (json.optBoolean("success")) {
                            val url = json.optString("url")
                            onSuccess(url)
                        } else {
                            onError(json.optString("message", "上传被图床拒绝"))
                        }
                    } catch (e: Exception) {
                        onError("图床JSON解析失败: ${e.message}")
                    }
                } else {
                    onError("图床服务器错误: HTTP ${response.code}")
                }
            }
        })
    }
}
