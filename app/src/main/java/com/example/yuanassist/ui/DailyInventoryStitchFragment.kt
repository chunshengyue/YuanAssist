package com.example.yuanassist.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.EditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageRadioOption
import com.example.yuanassist.ui.subpage.SubpageInfoStrip
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.MyStoneStore
import com.example.yuanassist.utils.applyYuanInputStyle

class DailyInventoryStitchFragment : Fragment() {

    private var selectedStoneType: String = MyStoneStore.TYPE_MAIN
    private var selectedArchiveId: String = MyStoneStore.DEFAULT_ARCHIVE_ID
    private var refreshUi: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        selectedStoneType = MyStoneStore.getSelectedType(requireContext())
        selectedArchiveId = MyStoneStore.getSelectedArchiveId(requireContext())
        val exampleBitmap = loadExampleBitmap()

        return ComposeView(requireContext()).apply {
            setContent {
                var stoneType by rememberSaveable { mutableStateOf(selectedStoneType) }
                var archiveId by rememberSaveable { mutableStateOf(selectedArchiveId) }
                refreshUi = {
                    stoneType = MyStoneStore.getSelectedType(requireContext())
                    archiveId = MyStoneStore.getSelectedArchiveId(requireContext())
                    selectedStoneType = stoneType
                    selectedArchiveId = archiveId
                }

                DailyInventoryStitchScreen(
                    selectedStoneType = stoneType,
                    archiveName = MyStoneStore.getSelectedArchive(requireContext()).name,
                    exampleBitmap = exampleBitmap,
                    onBack = ::goBack,
                    onSelectStoneType = { type ->
                        selectedStoneType = MyStoneStore.normalizeType(type)
                        MyStoneStore.setSelectedType(requireContext(), selectedStoneType)
                        stoneType = selectedStoneType
                    },
                    onSwitchArchive = ::showArchiveSwitchDialog,
                    onCreateArchive = ::showCreateArchiveDialog,
                    onRenameArchive = ::showRenameArchiveDialog,
                    onImport = {
                        selectedStoneType = stoneType
                        selectedArchiveId = archiveId
                        startInventoryStitchService()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        selectedStoneType = MyStoneStore.getSelectedType(requireContext())
        selectedArchiveId = MyStoneStore.getSelectedArchiveId(requireContext())
        refreshUi?.invoke()
    }

    override fun onDestroyView() {
        refreshUi = null
        super.onDestroyView()
    }

    private fun goBack() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            activity?.finish()
        }
    }

    private fun loadExampleBitmap(): Bitmap? {
        return try {
            requireContext().assets.open("xingshishili.jpg").use(BitmapFactory::decodeStream)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "示例图加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun startInventoryStitchService() {
        val context = requireContext()
        selectedArchiveId = MyStoneStore.getSelectedArchiveId(context)
        MyStoneStore.setSelectedType(context, selectedStoneType)
        MyStoneStore.setSelectedArchiveId(context, selectedArchiveId)
        val archiveName = MyStoneStore.getSelectedArchive(context).name
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_INVENTORY_STITCH)
            .putString(KEY_PENDING_STONE_TYPE, selectedStoneType)
            .putString(KEY_PENDING_STONE_ARCHIVE_ID, selectedArchiveId)
            .apply()

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "需要悬浮窗权限", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
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
                action = ACTION_START_INVENTORY_STITCH
                putExtra(KEY_PENDING_STONE_TYPE, selectedStoneType)
                putExtra(KEY_PENDING_STONE_ARCHIVE_ID, selectedArchiveId)
            }
            context.startService(intent)
            Toast.makeText(
                context,
                "已准备${archiveName}的${MyStoneStore.displayName(selectedStoneType)}拼图，请点击悬浮窗开始按钮",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showArchiveSwitchDialog() {
        val context = requireContext()
        val archives = MyStoneStore.listArchives(context)
        if (archives.isEmpty()) return

        val labels = archives.map { archive ->
            if (archive.id == selectedArchiveId) "当前：${archive.name}" else archive.name
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("切换存档")
            .setItems(labels) { _, which ->
                val archive = archives[which]
                selectedArchiveId = archive.id
                MyStoneStore.setSelectedArchiveId(context, archive.id)
                refreshUi?.invoke()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCreateArchiveDialog() {
        showArchiveInputDialog(
            title = "新建存档",
            positiveText = "创建",
            initialValue = ""
        ) { archiveName ->
            val context = requireContext()
            try {
                val archive = MyStoneStore.createArchive(context, archiveName)
                selectedArchiveId = archive.id
                MyStoneStore.setSelectedArchiveId(context, archive.id)
                refreshUi?.invoke()
                Toast.makeText(context, "已创建存档：${archive.name}", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, e.message ?: "创建存档失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameArchiveDialog() {
        val context = requireContext()
        val currentArchive = MyStoneStore.getSelectedArchive(context)
        showArchiveInputDialog(
            title = "重命名存档",
            positiveText = "保存",
            initialValue = currentArchive.name
        ) { archiveName ->
            try {
                MyStoneStore.renameArchive(context, currentArchive.id, archiveName)
                refreshUi?.invoke()
                Toast.makeText(context, "存档已重命名", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, e.message ?: "重命名失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showArchiveInputDialog(
        title: String,
        positiveText: String,
        initialValue: String,
        onConfirm: (String) -> Unit
    ) {
        val context = requireContext()
        val input = EditText(context).apply {
            setText(initialValue)
            setSelection(text.length)
            hint = "请输入存档名称"
            setSingleLine()
            applyYuanInputStyle()
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(positiveText) { _, _ ->
                onConfirm(input.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(requireContext(), YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val enabled = ComponentName.unflattenFromString(splitter.next())
            if (enabled == expected) return true
        }
        return false
    }

    companion object {
        private const val ACTION_START_INVENTORY_STITCH = "ACTION_START_INVENTORY_STITCH"
        const val KEY_PENDING_STONE_TYPE = "pending_stone_type"
        const val KEY_PENDING_STONE_ARCHIVE_ID = "pending_stone_archive_id"
    }
}

@Composable
private fun DailyInventoryStitchScreen(
    selectedStoneType: String,
    archiveName: String,
    exampleBitmap: Bitmap?,
    onBack: () -> Unit,
    onSelectStoneType: (String) -> Unit,
    onSwitchArchive: () -> Unit,
    onCreateArchive: () -> Unit,
    onRenameArchive: () -> Unit,
    onImport: () -> Unit,
) {
    SubpageScaffold(
        title = "星石拼图",
        subtitle = "背包截图 · 存档归属 · 我的星石",
        onBack = onBack,
    ) {
        SubpageSectionCard(
            title = "导入目标",
            subtitle = "导入结果会写入当前存档下的已选背包类型",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SubpageRadioOption(
                    text = "主星",
                    selected = selectedStoneType == MyStoneStore.TYPE_MAIN,
                    onClick = { onSelectStoneType(MyStoneStore.TYPE_MAIN) },
                    modifier = Modifier.weight(1f),
                )
                SubpageRadioOption(
                    text = "辅星",
                    selected = selectedStoneType == MyStoneStore.TYPE_SUPPORT,
                    onClick = { onSelectStoneType(MyStoneStore.TYPE_SUPPORT) },
                    modifier = Modifier.weight(1f),
                )
            }
            SubpageInfoStrip(
                label = "当前存档",
                value = archiveName,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StoneStyleButton(
                    text = "切换存档",
                    onClick = onSwitchArchive,
                    selected = false,
                    minHeight = 38.dp,
                    modifier = Modifier.weight(1f),
                )
                StoneStyleButton(
                    text = "新建存档",
                    onClick = onCreateArchive,
                    minHeight = 38.dp,
                    modifier = Modifier.weight(1f),
                )
                StoneStyleButton(
                    text = "重命名",
                    onClick = onRenameArchive,
                    selected = false,
                    minHeight = 38.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SubpageSectionCard(
            title = "开始位置",
            subtitle = "先打开对应的主星或辅星背包，再点击悬浮窗开始按钮进行截图",
        ) {
            if (exampleBitmap != null) {
                Image(
                    bitmap = exampleBitmap.asImageBitmap(),
                    contentDescription = "星石拼图开始位置示例",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp))
                        .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentScale = ContentScale.FillWidth,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "示例图加载失败",
                        color = BodyInk,
                        fontFamily = FontFamily.Serif,
                    )
                }
            }
            Text(
                text = "结果会分别保存到我的星石中的主星或辅星页签。",
                color = BodyInk.copy(alpha = 0.86f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        StoneStyleButton(
            text = "导入",
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        )
    }
}
