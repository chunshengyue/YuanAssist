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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.StargazingBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.StargazingConfig
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.ui.subpage.SubpageTextField

private const val PREFS_APP = "app_prefs"
private const val ACTION_START_STARGAZING = "ACTION_START_STARGAZING"
private const val KEY_STARGAZING_COUNT = "stargazing_count"
private const val KEY_STARGAZING_LOW_SPEC_DELAY = "stargazing_low_spec_delay"

class StargazingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val savedCount = prefs.getString(KEY_STARGAZING_COUNT, "").orEmpty()
        val savedLowSpecDelay = prefs.getString(KEY_STARGAZING_LOW_SPEC_DELAY, "").orEmpty()

        return ComposeView(context).apply {
            setContent {
                StargazingScreen(
                    initialState = StargazingUiState(
                        totalCount = savedCount,
                        lowSpecDelay = savedLowSpecDelay,
                    ),
                    onBack = {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            activity?.finish()
                        }
                    },
                    onConfirm = { state ->
                        val config = buildConfig(state) ?: return@StargazingScreen
                        saveSettings(state)
                        StargazingBridge.pendingConfig = config
                        startStargazingService()
                    },
                )
            }
        }
    }

    private fun buildConfig(state: StargazingUiState): StargazingConfig? {
        val totalCount = state.totalCount.trim().toIntOrNull()
        val lowSpecDelayMs = state.lowSpecDelay.trim().toLongOrNull() ?: 0L
        if (totalCount == null || totalCount <= 0) {
            Toast.makeText(requireContext(), "请输入有效的观星次数", Toast.LENGTH_SHORT).show()
            return null
        }
        if (lowSpecDelayMs < 0L) {
            Toast.makeText(requireContext(), "低配机型适应延时不能小于 0", Toast.LENGTH_SHORT).show()
            return null
        }
        return StargazingConfig(
            totalCount = totalCount,
            clickIntervalMs = 1200L,
            lowSpecDelayMs = lowSpecDelayMs,
        )
    }

    private fun saveSettings(state: StargazingUiState) {
        requireContext().getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString(KEY_STARGAZING_COUNT, state.totalCount.trim())
            .putString(KEY_STARGAZING_LOW_SPEC_DELAY, state.lowSpecDelay.trim())
            .apply()
    }

    private fun startStargazingService() {
        val context = requireContext()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_STARGAZING)
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
                action = ACTION_START_STARGAZING
            }
            context.startService(intent)
            Toast.makeText(context, "无月卡观星已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_SHORT).show()
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

private data class StargazingUiState(
    val totalCount: String,
    val lowSpecDelay: String,
)

@Composable
private fun StargazingScreen(
    initialState: StargazingUiState,
    onBack: () -> Unit,
    onConfirm: (StargazingUiState) -> Unit,
) {
    var totalCount by rememberSaveable { mutableStateOf(initialState.totalCount) }
    var lowSpecDelay by rememberSaveable { mutableStateOf(initialState.lowSpecDelay) }

    val state = StargazingUiState(
        totalCount = totalCount,
        lowSpecDelay = lowSpecDelay,
    )

    SubpageScaffold(
        title = "无月卡观星",
        subtitle = "输入总次数 · 每满30自动收取",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "观星次数",
            subtitle = "每满30次自动收取一次，最后不足30次也会收取",
        ) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SubpageTextField(
                    value = totalCount,
                    onValueChange = { totalCount = it },
                    label = "输入总观星次数",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SubpageSectionCard(
            title = "低配机型适应",
            subtitle = "会在基础动作 delay 上统一增加，单位 ms",
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
