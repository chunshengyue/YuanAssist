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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.google.gson.Gson
import java.io.InputStreamReader

private const val AILAO_SCRIPT_FILE = "script(1).json"
private const val ACTION_IMPORT_RECORDED_DAILY_PLAN = "ACTION_IMPORT_RECORDED_DAILY_PLAN"

class Ailao15MinFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return androidx.compose.ui.platform.ComposeView(context).apply {
            setContent {
                Ailao15MinScreen(
                    onBack = {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            activity?.finish()
                        }
                    },
                    onImport = ::importAilaoScript,
                )
            }
        }
    }

    private fun importAilaoScript() {
        val context = requireContext()
        val content = try {
            context.assets.open(AILAO_SCRIPT_FILE).use { input ->
                InputStreamReader(input, Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "读取脚本失败：${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Gson().fromJson(content, DailyTaskPlan::class.java)
        } catch (e: Exception) {
            Toast.makeText(context, "脚本解析失败：${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(context, "请先开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        try {
            context.startService(Intent(context, YuanAssistService::class.java).apply {
                action = ACTION_IMPORT_RECORDED_DAILY_PLAN
                putExtra("EXTRA_DAILY_PLAN_FILE_NAME", "哀牢15min.json")
                putExtra("EXTRA_DAILY_PLAN_JSON", content)
            })
            Toast.makeText(context, "哀牢15min已导入到日常版悬浮窗", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
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

@Composable
private fun Ailao15MinScreen(
    onBack: () -> Unit,
    onImport: () -> Unit,
) {
    SubpageScaffold(
        title = "哀牢15min",
        subtitle = "幻境难度 · 体力恢复循环",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "说明",
            subtitle = "导入后通过日常版悬浮窗启动",
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "每隔15分钟（体力15分钟恢复1）刷一次幻境难度，战斗限时60s。",
                    color = BodyInk,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        StoneStyleButton(
            text = "导入",
            onClick = onImport,
        )
    }
}
