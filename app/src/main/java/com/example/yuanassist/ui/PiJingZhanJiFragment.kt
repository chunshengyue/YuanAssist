package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.PiJingZhanJiBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.PiJingZhanJiConfig
import com.example.yuanassist.model.PiJingZhanJiGameVariant
import com.example.yuanassist.model.PiJingZhanJiStargazingMode
import com.example.yuanassist.model.PiJingZhanJiTaskConfig
import com.example.yuanassist.model.PiJingZhanJiTaskType
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageCheckOption
import com.example.yuanassist.ui.subpage.SubpageCircleIndicator
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.SubpageSquareIndicator
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField

private const val PREFS_APP = "app_prefs"
private const val ACTION_START_PI_JING_ZHAN_JI = "ACTION_START_PI_JING_ZHAN_JI"
private const val KEY_PJZJ_GAME_VARIANT = "pjzj_game_variant"
private const val KEY_PJZJ_LOW_SPEC_DELAY = "pjzj_low_spec_delay"
private const val KEY_PJZJ_TASK_ONCE = "pjzj_task_once"
private const val KEY_PJZJ_TASK_TWICE = "pjzj_task_twice"
private const val KEY_PJZJ_TASK_GIFT_30 = "pjzj_task_gift_30"
private const val KEY_PJZJ_TASK_XING_NANG = "pjzj_task_xing_nang"
private const val KEY_PJZJ_TASK_YUAN_BAO_26 = "pjzj_task_yuan_bao_26"
private const val KEY_PJZJ_TASK_JIA_JU_TI_LI = "pjzj_task_jia_ju_ti_li"
private const val KEY_PJZJ_TASK_JIA_JU_DA_ZAO = "pjzj_task_jia_ju_da_zao"
private const val KEY_PJZJ_TASK_CAI_LIAO_HE_CHENG = "pjzj_task_cai_liao_he_cheng"
private const val KEY_PJZJ_TASK_GUAN_XING_WU_ZHU_QIAN = "pjzj_task_guan_xing_wu_zhu_qian"
private const val KEY_PJZJ_GUAN_XING_MODE = "pjzj_guan_xing_mode"
private const val KEY_PJZJ_GUAN_XING_VALUE = "pjzj_guan_xing_value"
private const val KEY_PJZJ_ATTR_ONCE = "pjzj_attr_once"
private const val KEY_PJZJ_ATTR_TWICE = "pjzj_attr_twice"
private const val KEY_PJZJ_ACTIVITY_MODULE = "pjzj_activity_module"
private const val KEY_PJZJ_ACTIVITY_DEBUG_MODE = "pjzj_activity_debug_mode"
private const val KEY_PJZJ_REFRESH_UNSUPPORTED_ACTIVITY_TASK = "pjzj_refresh_unsupported_activity_task"

private val ALL_ATTRIBUTES = listOf("地", "水", "火", "风", "阳", "阴")
private val PiJingSelectedText = Color(0xFF6E4523)
private val PiJingUnselectedText = Color(0xFF7C5A34)

class PiJingZhanJiFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val savedVariant = prefs.getString(KEY_PJZJ_GAME_VARIANT, PiJingZhanJiGameVariant.RU_YUAN.name)
            ?.let { value -> PiJingZhanJiGameVariant.entries.firstOrNull { it.name == value } }
            ?: PiJingZhanJiGameVariant.RU_YUAN
        val savedLowSpecDelay = prefs.getString(KEY_PJZJ_LOW_SPEC_DELAY, "").orEmpty()
        val savedTaskOnce = prefs.getBoolean(KEY_PJZJ_TASK_ONCE, true)
        val savedTaskTwice = prefs.getBoolean(KEY_PJZJ_TASK_TWICE, false)
        val savedTaskGift30 = prefs.getBoolean(KEY_PJZJ_TASK_GIFT_30, false)
        val savedTaskXingNang = prefs.getBoolean(KEY_PJZJ_TASK_XING_NANG, false)
        val savedTaskYuanBao26 = prefs.getBoolean(KEY_PJZJ_TASK_YUAN_BAO_26, false)
        val savedTaskJiaJuTiLi = prefs.getBoolean(KEY_PJZJ_TASK_JIA_JU_TI_LI, false)
        val savedTaskJiaJuDaZao = prefs.getBoolean(KEY_PJZJ_TASK_JIA_JU_DA_ZAO, false)
        val savedTaskCaiLiaoHeCheng = prefs.getBoolean(KEY_PJZJ_TASK_CAI_LIAO_HE_CHENG, false)
        val savedTaskGuanXingWuZhuQian = prefs.getBoolean(KEY_PJZJ_TASK_GUAN_XING_WU_ZHU_QIAN, false)
        val savedGuanXingMode = prefs.getString(KEY_PJZJ_GUAN_XING_MODE, PiJingZhanJiStargazingMode.NO_MONTH_CARD.name)
            ?.let { value -> PiJingZhanJiStargazingMode.entries.firstOrNull { it.name == value } }
            ?: PiJingZhanJiStargazingMode.NO_MONTH_CARD
        val savedGuanXingValue = prefs.getString(KEY_PJZJ_GUAN_XING_VALUE, "").orEmpty()
        val savedOnceAttributes = prefs.getString(KEY_PJZJ_ATTR_ONCE, "地,水,火")
            .orEmpty()
            .split(",")
            .map(String::trim)
            .filter { it in ALL_ATTRIBUTES }
            .distinct()
        val savedActivityModule = prefs.getBoolean(KEY_PJZJ_ACTIVITY_MODULE, true)
        val savedActivityDebugMode = prefs.getBoolean(KEY_PJZJ_ACTIVITY_DEBUG_MODE, false)
        val savedRefreshUnsupportedActivityTask = prefs.getBoolean(KEY_PJZJ_REFRESH_UNSUPPORTED_ACTIVITY_TASK, false)
        val savedTwiceAttributes = prefs.getString(KEY_PJZJ_ATTR_TWICE, ALL_ATTRIBUTES.joinToString(","))
            .orEmpty()
            .split(",")
            .map(String::trim)
            .filter { it in ALL_ATTRIBUTES }
            .distinct()

        return ComposeView(context).apply {
            setContent {
                PiJingZhanJiScreen(
                    initialState = PiJingZhanJiUiState(
                        gameVariant = savedVariant,
                        lowSpecDelay = savedLowSpecDelay,
                        enableOnce624 = savedTaskOnce,
                        enableTwice624 = savedTaskTwice,
                        enableGift30 = savedTaskGift30,
                        enableXingNang = savedTaskXingNang,
                        enableYuanBao26 = savedTaskYuanBao26,
                        enableJiaJuTiLi = savedTaskJiaJuTiLi,
                        enableJiaJuDaZao = savedTaskJiaJuDaZao,
                        enableCaiLiaoHeCheng = savedTaskCaiLiaoHeCheng,
                        enableGuanXingWuZhuQian = savedTaskGuanXingWuZhuQian,
                        guanXingMode = savedGuanXingMode,
                        guanXingValue = savedGuanXingValue,
                        onceAttributes = savedOnceAttributes,
                        twiceAttributes = if (savedTwiceAttributes.size == 6) savedTwiceAttributes else ALL_ATTRIBUTES,
                        enableActivityModule = savedActivityModule,
                        activityDebugModeEnabled = savedActivityDebugMode,
                        refreshUnsupportedActivityTask = savedRefreshUnsupportedActivityTask,
                    ),
                    onBack = {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            activity?.finish()
                        }
                    },
                    onConfirm = { state ->
                        val config = buildConfig(state) ?: return@PiJingZhanJiScreen
                        saveSettings(state, config)
                        PiJingZhanJiBridge.pendingConfig = config
                        startPiJingZhanJiService()
                    }
                )
            }
        }
    }

    private fun buildConfig(state: PiJingZhanJiUiState): PiJingZhanJiConfig? {
        val tasks = mutableListOf<PiJingZhanJiTaskConfig>()
        val lowSpecDelayMs = state.lowSpecDelay.trim().toLongOrNull() ?: 0L

        if (!state.enableOnce624 && !state.enableTwice624 && !state.enableGift30 && !state.enableXingNang && !state.enableYuanBao26 && !state.enableJiaJuTiLi && !state.enableJiaJuDaZao && !state.enableCaiLiaoHeCheng && !state.enableGuanXingWuZhuQian && !state.enableActivityModule) {
            Toast.makeText(requireContext(), "请至少勾选一个任务", Toast.LENGTH_SHORT).show()
            return null
        }
        if (state.enableOnce624 && state.onceAttributes.size != 3) {
            Toast.makeText(requireContext(), "一次624需要刚好选择3个属性", Toast.LENGTH_SHORT).show()
            return null
        }
        if (state.enableTwice624 && state.twiceAttributes.distinct().size != 6) {
            Toast.makeText(requireContext(), "两次624需要6个属性全选", Toast.LENGTH_SHORT).show()
            return null
        }
        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }

        if (state.enableOnce624) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.MAINLINE_624_ONCE,
                selectedAttributes = state.onceAttributes
            )
        }
        if (state.enableTwice624) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.MAINLINE_624_TWICE,
                selectedAttributes = state.twiceAttributes
            )
        }
        if (state.enableGift30) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.MI_TAN_ZENG_LI_30,
            )
        }
        if (state.enableXingNang) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.XING_NANG,
            )
        }
        if (state.enableYuanBao26) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.YUAN_BAO_26,
            )
        }
        if (state.enableJiaJuTiLi) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.JIA_JU_TI_LI,
            )
        }
        if (state.enableJiaJuDaZao) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.JIA_JU_DA_ZAO,
            )
        }
        if (state.enableCaiLiaoHeCheng) {
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.CAI_LIAO_HE_CHENG,
            )
        }
        if (state.enableGuanXingWuZhuQian) {
            val guanXingValue = state.guanXingValue.trim().toIntOrNull()
            if (guanXingValue == null || guanXingValue <= 0) {
                Toast.makeText(requireContext(), "请输入有效的观星配置数值", Toast.LENGTH_SHORT).show()
                return null
            }
            tasks += PiJingZhanJiTaskConfig(
                type = PiJingZhanJiTaskType.GUAN_XING_WU_ZHU_QIAN,
                stargazingMode = state.guanXingMode,
                stargazingValue = guanXingValue
            )
        }

        return PiJingZhanJiConfig(
            gameVariant = state.gameVariant,
            tasks = tasks,
            lowSpecDelayMs = lowSpecDelayMs,
            activityDebugModeEnabled = state.activityDebugModeEnabled,
            enableActivityModule = state.enableActivityModule,
            refreshUnsupportedActivityTask = state.refreshUnsupportedActivityTask
        )
    }

    private fun saveSettings(state: PiJingZhanJiUiState, config: PiJingZhanJiConfig) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString(KEY_PJZJ_GAME_VARIANT, config.gameVariant.name)
            .putString(KEY_PJZJ_LOW_SPEC_DELAY, state.lowSpecDelay.trim())
            .putBoolean(KEY_PJZJ_TASK_ONCE, state.enableOnce624)
            .putBoolean(KEY_PJZJ_TASK_TWICE, state.enableTwice624)
            .putBoolean(KEY_PJZJ_TASK_GIFT_30, state.enableGift30)
            .putBoolean(KEY_PJZJ_TASK_XING_NANG, state.enableXingNang)
            .putBoolean(KEY_PJZJ_TASK_YUAN_BAO_26, state.enableYuanBao26)
            .putBoolean(KEY_PJZJ_TASK_JIA_JU_TI_LI, state.enableJiaJuTiLi)
            .putBoolean(KEY_PJZJ_TASK_JIA_JU_DA_ZAO, state.enableJiaJuDaZao)
            .putBoolean(KEY_PJZJ_TASK_CAI_LIAO_HE_CHENG, state.enableCaiLiaoHeCheng)
            .putBoolean(KEY_PJZJ_TASK_GUAN_XING_WU_ZHU_QIAN, state.enableGuanXingWuZhuQian)
            .putString(KEY_PJZJ_GUAN_XING_MODE, state.guanXingMode.name)
            .putString(KEY_PJZJ_GUAN_XING_VALUE, state.guanXingValue.trim())
            .putString(KEY_PJZJ_ATTR_ONCE, state.onceAttributes.joinToString(","))
            .putString(KEY_PJZJ_ATTR_TWICE, state.twiceAttributes.joinToString(","))
            .putBoolean(KEY_PJZJ_ACTIVITY_MODULE, state.enableActivityModule)
            .putBoolean(KEY_PJZJ_ACTIVITY_DEBUG_MODE, state.activityDebugModeEnabled)
            .putBoolean(KEY_PJZJ_REFRESH_UNSUPPORTED_ACTIVITY_TASK, state.refreshUnsupportedActivityTask)
            .apply()
    }

    private fun startPiJingZhanJiService() {
        val context = requireContext()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_PI_JING_ZHAN_JI)
            .apply()

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(context, "请启用 YuanAssist 无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            val intent = Intent(context, YuanAssistService::class.java).apply {
                action = ACTION_START_PI_JING_ZHAN_JI
            }
            context.startService(intent)
            Toast.makeText(context, "披荆斩棘已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(requireContext(), YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) return true
        }
        return false
    }
}

private data class PiJingZhanJiUiState(
    val gameVariant: PiJingZhanJiGameVariant,
    val lowSpecDelay: String,
    val enableOnce624: Boolean,
    val enableTwice624: Boolean,
    val enableGift30: Boolean,
    val enableXingNang: Boolean,
    val enableYuanBao26: Boolean,
    val enableJiaJuTiLi: Boolean,
    val enableJiaJuDaZao: Boolean,
    val enableCaiLiaoHeCheng: Boolean,
    val enableGuanXingWuZhuQian: Boolean,
    val guanXingMode: PiJingZhanJiStargazingMode,
    val guanXingValue: String,
    val onceAttributes: List<String>,
    val twiceAttributes: List<String>,
    val enableActivityModule: Boolean,
    val activityDebugModeEnabled: Boolean,
    val refreshUnsupportedActivityTask: Boolean,
)

@Composable
private fun PiJingZhanJiScreen(
    initialState: PiJingZhanJiUiState,
    onBack: () -> Unit,
    onConfirm: (PiJingZhanJiUiState) -> Unit,
) {
    var gameVariant by rememberSaveable { mutableStateOf(initialState.gameVariant) }
    var lowSpecDelay by rememberSaveable { mutableStateOf(initialState.lowSpecDelay) }
    var enableOnce624 by rememberSaveable { mutableStateOf(initialState.enableOnce624) }
    var enableTwice624 by rememberSaveable { mutableStateOf(initialState.enableTwice624) }
    var enableGift30 by rememberSaveable { mutableStateOf(initialState.enableGift30) }
    var enableXingNang by rememberSaveable { mutableStateOf(initialState.enableXingNang) }
    var enableYuanBao26 by rememberSaveable { mutableStateOf(initialState.enableYuanBao26) }
    var enableJiaJuTiLi by rememberSaveable { mutableStateOf(initialState.enableJiaJuTiLi) }
    var enableJiaJuDaZao by rememberSaveable { mutableStateOf(initialState.enableJiaJuDaZao) }
    var enableCaiLiaoHeCheng by rememberSaveable { mutableStateOf(initialState.enableCaiLiaoHeCheng) }
    var enableGuanXingWuZhuQian by rememberSaveable { mutableStateOf(initialState.enableGuanXingWuZhuQian) }
    var guanXingMode by rememberSaveable { mutableStateOf(initialState.guanXingMode) }
    var guanXingValue by rememberSaveable { mutableStateOf(initialState.guanXingValue) }
    var onceAttributes by rememberSaveable { mutableStateOf(initialState.onceAttributes) }
    var twiceAttributes by rememberSaveable { mutableStateOf(initialState.twiceAttributes) }
    var enableActivityModule by rememberSaveable { mutableStateOf(initialState.enableActivityModule) }
    var activityDebugModeEnabled by rememberSaveable { mutableStateOf(initialState.activityDebugModeEnabled) }
    var refreshUnsupportedActivityTask by rememberSaveable { mutableStateOf(initialState.refreshUnsupportedActivityTask) }

    val state = PiJingZhanJiUiState(
        gameVariant = gameVariant,
        lowSpecDelay = lowSpecDelay,
        enableOnce624 = enableOnce624,
        enableTwice624 = enableTwice624,
        enableGift30 = enableGift30,
        enableXingNang = enableXingNang,
        enableYuanBao26 = enableYuanBao26,
        enableJiaJuTiLi = enableJiaJuTiLi,
        enableJiaJuDaZao = enableJiaJuDaZao,
        enableCaiLiaoHeCheng = enableCaiLiaoHeCheng,
        enableGuanXingWuZhuQian = enableGuanXingWuZhuQian,
        guanXingMode = guanXingMode,
        guanXingValue = guanXingValue,
        onceAttributes = onceAttributes,
        twiceAttributes = twiceAttributes,
        enableActivityModule = enableActivityModule,
        activityDebugModeEnabled = activityDebugModeEnabled,
        refreshUnsupportedActivityTask = refreshUnsupportedActivityTask,
    )

    SubpageScaffold(
        title = "披荆斩棘",
        subtitle = "任务勾选 · 属性配置 · 调度入口",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "游戏版本",
            subtitle = "决定主线 6-24 的入口识别变量",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SubpageRadioOption(
                        text = "如鸢",
                        selected = gameVariant == PiJingZhanJiGameVariant.RU_YUAN,
                        onClick = { gameVariant = PiJingZhanJiGameVariant.RU_YUAN }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SubpageRadioOption(
                        text = "代号鸢",
                        selected = gameVariant == PiJingZhanJiGameVariant.CODE_NAME_YUAN,
                        onClick = { gameVariant = PiJingZhanJiGameVariant.CODE_NAME_YUAN }
                    )
                }
            }
        }

        SubpageSectionCard(
            title = "前置任务",
            subtitle = "只勾选前置任务不会进入活动页，前置任务完不成会自动跳过。",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.MAINLINE_624_ONCE.displayName,
                    subtitle = "运行 1 次 6-24，并插入 3 次属性切换",
                    checked = enableOnce624,
                    onCheckedChange = { enableOnce624 = it }
                )
                if (enableOnce624) {
                    LightweightAttributeSelector(
                        title = "选择3个属性",
                        selectedAttributes = onceAttributes,
                        maxSelection = 3,
                        enabled = true,
                        onToggle = { attribute ->
                            onceAttributes = toggleAttribute(onceAttributes, attribute, 3)
                        }
                    )
                }

                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.MAINLINE_624_TWICE.displayName,
                    subtitle = "运行 2 次 6-24，并跑完 6 个属性",
                    checked = enableTwice624,
                    onCheckedChange = {
                        enableTwice624 = it
                        if (it) twiceAttributes = ALL_ATTRIBUTES
                    }
                )
                if (enableTwice624) {
                    LightweightAttributeSelector(
                        title = "固定6属性全跑",
                        selectedAttributes = twiceAttributes,
                        maxSelection = 6,
                        enabled = false,
                        onToggle = {}
                    )
                }
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.MI_TAN_ZENG_LI_30.displayName,
                    subtitle = "注意！！！默认给蛾使送喜欢的礼物，如果没有这个礼物或者不想浪费，别选！！",
                    checked = enableGift30,
                    onCheckedChange = { enableGift30 = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.XING_NANG.displayName,
                    subtitle = "去据点收取情报并一键扫荡",
                    checked = enableXingNang,
                    onCheckedChange = { enableXingNang = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.YUAN_BAO_26.displayName,
                    subtitle = "进行一轮鸟食＋一轮突发情况，一轮待办公务",
                    checked = enableYuanBao26,
                    onCheckedChange = { enableYuanBao26 = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.JIA_JU_TI_LI.displayName,
                    subtitle = "结算完成的历险并再次开始",
                    checked = enableJiaJuTiLi,
                    onCheckedChange = { enableJiaJuTiLi = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.JIA_JU_DA_ZAO.displayName,
                    subtitle = "默认打造三个地板",
                    checked = enableJiaJuDaZao,
                    onCheckedChange = { enableJiaJuDaZao = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.CAI_LIAO_HE_CHENG.displayName,
                    subtitle = "默认合成学徒材料30个",
                    checked = enableCaiLiaoHeCheng,
                    onCheckedChange = { enableCaiLiaoHeCheng = it }
                )
                LightweightTaskToggle(
                    title = PiJingZhanJiTaskType.GUAN_XING_WU_ZHU_QIAN.displayName,
                    subtitle = "60万五铢钱大概需要观星5轮",
                    checked = enableGuanXingWuZhuQian,
                    onCheckedChange = { enableGuanXingWuZhuQian = it }
                )
                if (enableGuanXingWuZhuQian) {
                    SubpageSectionCard(
                        title = "观星配置",
                        subtitle = "有月卡填几轮，无月卡填次数",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SubpageRadioOption(
                                    text = "有月卡",
                                    selected = guanXingMode == PiJingZhanJiStargazingMode.MONTH_CARD,
                                    onClick = { guanXingMode = PiJingZhanJiStargazingMode.MONTH_CARD }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SubpageRadioOption(
                                    text = "无月卡",
                                    selected = guanXingMode == PiJingZhanJiStargazingMode.NO_MONTH_CARD,
                                    onClick = { guanXingMode = PiJingZhanJiStargazingMode.NO_MONTH_CARD }
                                )
                            }
                        }
                        SubpageTextField(
                            value = guanXingValue,
                            onValueChange = { guanXingValue = it },
                            label = if (guanXingMode == PiJingZhanJiStargazingMode.MONTH_CARD) "输入轮数" else "输入次数",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        SubpageSectionCard(
            title = "活动页相关",
            subtitle = "活动页相关",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LightweightTaskToggle(
                    title = "活动页任务",
                    subtitle = "自动进入披荆斩棘，处理活动页内三类任务",
                    checked = enableActivityModule,
                    onCheckedChange = { enableActivityModule = it }
                )
                if (enableActivityModule) {
                    LightweightTaskToggle(
                        title = "第二板块活动调试模式",
                        subtitle = "运行时显示 OCR 和模板匹配的 ROI 红框",
                        checked = activityDebugModeEnabled,
                        onCheckedChange = { activityDebugModeEnabled = it }
                    )
                    LightweightTaskToggle(
                        title = "无法完成时刷新",
                        subtitle = "关闭时仅记录日志并切换密探；开启时刷新当前任务",
                        checked = refreshUnsupportedActivityTask,
                        onCheckedChange = { refreshUnsupportedActivityTask = it }
                    )
                }
            }
        }

        SubpageSectionCard(
            title = "低配机型适应",
            subtitle = "会在所有动作 delay 基础上统一增加该数值，单位 ms",
        ) {
            SubpageTextField(
                value = lowSpecDelay,
                onValueChange = { lowSpecDelay = it },
                label = "0 表示关闭",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        StoneStyleButton(
            text = "导入",
            onClick = { onConfirm(state) },
        )
    }
}

@Composable
private fun LightweightTaskToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubpageSquareIndicator(
            checked = checked,
            enabled = true,
            size = 18.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = if (checked) PiJingSelectedText else PiJingUnselectedText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = BodyInk.copy(alpha = 0.82f),
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LightweightAttributeSelector(
    title: String,
    selectedAttributes: List<String>,
    maxSelection: Int,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$title（已选 ${selectedAttributes.size}/$maxSelection）",
            color = BodyInk,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
        )
        ALL_ATTRIBUTES.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowItems.forEach { item ->
                    SubpageCheckOption(
                        text = item,
                        checked = item in selectedAttributes,
                        enabled = enabled,
                        onClick = { onToggle(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun toggleAttribute(current: List<String>, attribute: String, maxSelection: Int): List<String> =
    when {
        attribute in current -> current - attribute
        current.size >= maxSelection -> current
        else -> current + attribute
    }
