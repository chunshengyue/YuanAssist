package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.core.OneKeyDailyBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageCheckOption
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.google.gson.Gson

class OneKeyDailyActivity : AppCompatActivity() {

    companion object {
        private const val DAILY_ASSET_DIR = "daily_scripts/daily"
        private const val ACTION_IMPORT_ONE_KEY_DAILY_QUEUE = "ACTION_IMPORT_ONE_KEY_DAILY_QUEUE"
        private val PREFERRED_ORDER = listOf(
            "领取体力",
            "领取月卡",
        )
    }

    private data class DailyAssetEntry(
        val fileName: String,
        val displayName: String,
        val taskCount: Int,
    )

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = loadEntries()
        setContent {
            OneKeyDailyScreen(entries = entries)
        }
    }

    private fun loadEntries(): List<DailyAssetEntry> {
        val fileNames = try {
            assets.list(DAILY_ASSET_DIR)
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return fileNames.mapNotNull { fileName ->
            val assetPath = "$DAILY_ASSET_DIR/$fileName"
            val content = runCatching {
                assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val plan = runCatching {
                gson.fromJson(content, DailyTaskPlan::class.java)
            }.getOrNull() ?: return@mapNotNull null
            DailyAssetEntry(
                fileName = fileName,
                displayName = plan.display_name?.takeIf { it.isNotBlank() }
                    ?: fileName.removeSuffix(".json"),
                taskCount = plan.tasks.size,
            )
        }.sortedWith(
            compareBy<DailyAssetEntry> { entry ->
                PREFERRED_ORDER.indexOf(entry.displayName).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.displayName }
        )
    }

    @Composable
    private fun OneKeyDailyScreen(entries: List<DailyAssetEntry>) {
        val selectedFileNames = remember { mutableStateListOf<String>() }
        var importing by remember { mutableStateOf(false) }
        val selectedCount = selectedFileNames.size

        SubpageScaffold(
            title = "一键日常",
            subtitle = "从内置日常脚本中多选，按列表顺序交给悬浮窗依次执行",
            onBack = { finish() },
        ) {
            SubpageSectionCard {
                androidx.compose.material3.Text(
                    text = if (entries.isEmpty()) {
                        "当前没有可用脚本。"
                    } else {
                        "已选 $selectedCount / ${entries.size} 个。运行时会按当前列表顺序依次执行，单项失败不会中断后续任务。"
                    },
                    color = BodyInk,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                )
            }

            if (entries.isEmpty()) {
                SubpageSectionCard {
                    androidx.compose.material3.Text(
                        text = "未读取到 daily 目录下的脚本",
                        color = BodyInk,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                    )
                }
            } else {
                SubpageSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { entry ->
                            val checked = selectedFileNames.contains(entry.fileName)
                            SubpageCheckOption(
                                text = entry.displayName,
                                checked = checked,
                                subtitle = "文件：${entry.fileName.removeSuffix(".json")}    任务数：${entry.taskCount}",
                                onClick = {
                                    if (checked) {
                                        selectedFileNames.remove(entry.fileName)
                                    } else {
                                        selectedFileNames.add(entry.fileName)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SubpageSectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StoneStyleButton(
                            text = if (selectedCount == entries.size) "取消全选" else "全选",
                            selected = false,
                            onClick = {
                                if (selectedCount == entries.size) {
                                    selectedFileNames.clear()
                                } else {
                                    selectedFileNames.clear()
                                    selectedFileNames.addAll(entries.map { it.fileName })
                                }
                            },
                        )
                        StoneStyleButton(
                            text = if (importing) "导入中..." else "导入到悬浮窗",
                            enabled = !importing && selectedCount > 0,
                            onClick = {
                                importing = true
                                importSelections(
                                    entries = entries,
                                    selectedFileNames = selectedFileNames.toList(),
                                    onFinished = { importing = false },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun importSelections(
        entries: List<DailyAssetEntry>,
        selectedFileNames: List<String>,
        onFinished: () -> Unit,
    ) {
        if (selectedFileNames.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个脚本", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        val selectedEntries = entries.filter { selectedFileNames.contains(it.fileName) }
        if (selectedEntries.isEmpty()) {
            Toast.makeText(this, "没有可导入的脚本", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            onFinished()
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            onFinished()
            return
        }

        OneKeyDailyBridge.pendingScriptFileNames = selectedEntries.map { it.fileName }

        runCatching {
            startService(Intent(this, YuanAssistService::class.java).apply {
                action = ACTION_IMPORT_ONE_KEY_DAILY_QUEUE
            })
        }.onSuccess {
            Toast.makeText(this, "一键日常已导入到悬浮窗，请点击开始按钮执行", Toast.LENGTH_LONG).show()
            onFinished()
            finish()
        }.onFailure { error ->
            Toast.makeText(this, "导入失败：${error.message}", Toast.LENGTH_SHORT).show()
            onFinished()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, YuanAssistService::class.java)
        val enabledServicesSetting =
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
