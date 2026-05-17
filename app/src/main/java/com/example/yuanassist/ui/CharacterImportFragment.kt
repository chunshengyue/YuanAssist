package com.example.yuanassist.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import com.example.yuanassist.core.CharacterImportBridge
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.CharacterSwitchPoint
import com.example.yuanassist.model.CharacterImportConfig
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.StoneStyleButton
import com.example.yuanassist.ui.subpage.SubpageScaffold
import com.example.yuanassist.ui.subpage.SubpageSectionCard
import com.example.yuanassist.utils.CharacterImportCalibrationStore

private const val ACTION_START_CHARACTER_IMPORT = "ACTION_START_CHARACTER_IMPORT"
private const val CHARACTER_IMPORT_PREFS = "character_import_prefs"
private const val KEY_OPERATION_INTERVAL_MS = "operation_interval_ms"
private const val DEFAULT_OPERATION_INTERVAL_MS = 2000L
private val DefaultRightPoint = CharacterSwitchPoint(1035f / 1080f, 989f / 1920f)

class CharacterImportFragment : Fragment() {
    private var showCalibrationDialog by mutableStateOf(false)
    private var screenshotBitmap by mutableStateOf<Bitmap?>(null)
    private var rightPoint by mutableStateOf(DefaultRightPoint)
    private var operationIntervalText by mutableStateOf(DEFAULT_OPERATION_INTERVAL_MS.toString())

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val bitmap = requireContext().contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        if (bitmap == null) {
            Toast.makeText(requireContext(), "截图读取失败", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        screenshotBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSavedCalibration()
        loadOperationInterval()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        return ComposeView(context).apply {
            setContent {
                SubpageScaffold(
                    title = "角色导入",
                    subtitle = "采集五名角色的名字、数值、星级和命盘",
                    onBack = {
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            activity?.finish()
                        }
                    },
                ) {
                    SubpageSectionCard(title = "导入", subtitle = "请先停在 1 号位角色练度页面") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "运行后会按设定间隔依次处理 1-5 号位：先记录数值和星级，再进入当前角色命盘页记录名字和命盘，返回后继续右切下一个角色。",
                                color = BodyInk,
                                fontFamily = FontFamily.Serif,
                            )
                            StoneStyleButton(text = "导入", onClick = ::startCharacterImportService)
                        }
                    }
                    SubpageSectionCard(title = "设置", subtitle = "操作间隔单位为毫秒") {
                        OutlinedTextField(
                            value = operationIntervalText,
                            onValueChange = {
                                operationIntervalText = it.filter(Char::isDigit)
                            },
                            label = { Text("操作间隔(ms)", fontFamily = FontFamily.Serif) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = GlassPanel,
                                unfocusedContainerColor = GlassPanel,
                                focusedBorderColor = HighlightGold,
                                unfocusedBorderColor = GlassStroke.copy(alpha = 0.55f),
                                focusedTextColor = TitleInk,
                                unfocusedTextColor = TitleInk,
                                focusedLabelColor = HighlightGold,
                                unfocusedLabelColor = BodyInk,
                                cursorColor = HighlightGold,
                            ),
                        )
                    }
                    SubpageSectionCard(title = "坐标校准", subtitle = "若无法切换角色，请调整此处") {
                        StoneStyleButton(
                            text = "坐标校准",
                            selected = false,
                            onClick = ::openCalibrationDialog,
                        )
                    }
                    CharacterImportCalibrationDialog()
                }
            }
        }
    }

    private fun openCalibrationDialog() {
        loadSavedCalibration()
        screenshotBitmap = null
        showCalibrationDialog = true
    }

    private fun loadSavedCalibration() {
        val calibration = CharacterImportCalibrationStore.load(requireContext())
        rightPoint = calibration.right
    }

    private fun startCharacterImportService() {
        val context = requireContext()
        val calibration = CharacterImportCalibrationStore.load(context)
        val operationIntervalMs = operationIntervalText.toLongOrNull()?.coerceAtLeast(500L) ?: DEFAULT_OPERATION_INTERVAL_MS
        saveOperationInterval(operationIntervalMs)
        CharacterImportBridge.pendingConfig = CharacterImportConfig(
            operationIntervalMs = operationIntervalMs,
            switchRightPoint = calibration.right,
        )
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("pending_start_action", ACTION_START_CHARACTER_IMPORT)
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

        runCatching {
            context.startService(
                Intent(context, YuanAssistService::class.java).apply {
                    action = ACTION_START_CHARACTER_IMPORT
                },
            )
        }.onSuccess {
            Toast.makeText(context, "角色导入已交给悬浮窗，请点击开始按钮执行", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "启动失败：${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOperationInterval() {
        val saved = requireContext()
            .getSharedPreferences(CHARACTER_IMPORT_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_OPERATION_INTERVAL_MS, DEFAULT_OPERATION_INTERVAL_MS)
        operationIntervalText = saved.toString()
    }

    private fun saveOperationInterval(value: Long) {
        operationIntervalText = value.toString()
        requireContext()
            .getSharedPreferences(CHARACTER_IMPORT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_OPERATION_INTERVAL_MS, value)
            .apply()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(requireContext(), YuanAssistService::class.java)
        val setting = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        return splitter.any { ComponentName.unflattenFromString(it) == expected }
    }

    @Composable
    private fun CharacterImportCalibrationDialog() {
        if (!showCalibrationDialog) return

        Dialog(
            onDismissRequest = { showCalibrationDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .fillMaxHeight(0.9f),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF8F0E1),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "坐标校准",
                            color = BodyInk,
                            fontFamily = FontFamily.Serif,
                        )
                        CalibrationCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CalibrationCanvas(modifier: Modifier = Modifier) {
        BoxWithConstraints(
            modifier = modifier
                .background(Color.White.copy(alpha = 0.36f), RoundedCornerShape(12.dp))
                .border(1.dp, GlassStroke.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = screenshotBitmap
            if (bitmap == null) {
                StoneStyleButton(
                    text = "上传截图",
                    selected = false,
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .offset(y = (-8).dp),
                    onClick = { imagePicker.launch("image/*") },
                )
                return@BoxWithConstraints
            }

            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            val containerAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val imageModifier = if (imageAspect > containerAspect) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspect)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(imageAspect)
            }

            Box(
                modifier = imageModifier.onSizeChanged { boxSize = it },
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "角色切换坐标校准截图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                CalibrationPointLayer(
                    size = boxSize,
                    right = rightPoint,
                    onRightChanged = { rightPoint = it },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StoneStyleButton(
                        text = "还原",
                        selected = false,
                        modifier = Modifier.weight(1f),
                        minHeight = 40.dp,
                        onClick = { rightPoint = DefaultRightPoint },
                    )
                    StoneStyleButton(
                        text = "关闭",
                        selected = false,
                        modifier = Modifier.weight(1f),
                        minHeight = 40.dp,
                        onClick = { showCalibrationDialog = false },
                    )
                    StoneStyleButton(
                        text = "保存",
                        modifier = Modifier.weight(1f),
                        minHeight = 40.dp,
                        onClick = {
                            CharacterImportCalibrationStore.save(requireContext(), rightPoint)
                            Toast.makeText(requireContext(), "坐标已保存", Toast.LENGTH_SHORT).show()
                            showCalibrationDialog = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun CalibrationPointLayer(
        size: IntSize,
        right: CharacterSwitchPoint,
        onRightChanged: (CharacterSwitchPoint) -> Unit,
    ) {
        val latestRight by rememberUpdatedState(right)
        val latestOnRightChanged by rememberUpdatedState(onRightChanged)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(size) {
                    var activeOffset: Offset? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                            val rightOffset = CharacterImportCalibrationDragHelper.pointToOffset(latestRight, size)
                            activeOffset = rightOffset.takeIf {
                                CharacterImportCalibrationDragHelper.isHandleTouched(start, it)
                            }
                        },
                        onDragEnd = { activeOffset = null },
                        onDragCancel = { activeOffset = null },
                    ) { change, dragAmount ->
                        change.consume()
                        if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                        val currentOffset = activeOffset ?: return@detectDragGestures
                        val nextOffset = CharacterImportCalibrationDragHelper.nextOffset(currentOffset, dragAmount, size)
                        activeOffset = nextOffset
                        val nextPoint = CharacterImportCalibrationDragHelper.offsetToPoint(nextOffset, size)
                        latestOnRightChanged(nextPoint)
                    }
                },
        ) {
            drawCalibrationPoint(CharacterImportCalibrationDragHelper.pointToOffset(right, size), "右切", Color(0xFF3F7BFF))
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCalibrationPoint(
        center: Offset,
        label: String,
        color: Color,
    ) {
        drawLine(color, center.copy(x = center.x - 22f), center.copy(x = center.x + 22f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(color, center.copy(y = center.y - 22f), center.copy(y = center.y + 22f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawCircle(color = color, radius = 14f, center = center, style = Stroke(width = 5f))
        drawContext.canvas.nativeCanvas.drawText(
            label,
            center.x + 20f,
            center.y - 18f,
            android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 42f
                isFakeBoldText = true
                setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
            },
        )
    }

}
