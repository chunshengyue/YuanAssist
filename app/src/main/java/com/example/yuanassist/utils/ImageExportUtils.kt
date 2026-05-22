package com.example.yuanassist.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import com.example.yuanassist.model.TurnData
import java.io.OutputStream
import kotlin.math.max

object ImageExportUtils {

    private const val BACKGROUND_ASSET = "character_import_table_bg.png"

    // 傳入 Context 供 Toast 和 ContentResolver 使用，傳入 data 和 headers 生成圖片
    fun generateAndSaveImage(
        context: Context,
        displayData: List<TurnData>,
        headers: Array<String>,
        gameTitle: String = "如鸢",
        exportSubtitle: String = "",
    ) {
        if (displayData.isEmpty()) {
            Toast.makeText(context, "无数据", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 检查是否有任何一行包含备注
        val hasRemarks = displayData.any { it.remark.isNotEmpty() }

        val baseWidth = 1080
        val remarkColWidth = if (hasRemarks) 350f else 0f
        val totalWidth = baseWidth + remarkColWidth.toInt()
        val sidePadding = 42f
        val topPadding = 230f
        val bottomPadding = 56f
        val headerHeight = 118
        val baseRowHeight = 120
        val padding = 18

        val paintTxt = TextPaint().apply {
            color = Color.parseColor("#4F443C")
            textSize = 36f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C79A61")
            strokeWidth = 2f
        }
        val paintHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AA7447") }
        val paintRowBgOdd = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF7E8") }
        val paintRowBgEven = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8EBD6") }
        val paintHeaderTxt = Paint().apply {
            color = Color.parseColor("#FFF5DA")
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintRemarkTxt = TextPaint().apply {
            color = Color.parseColor("#5A3A22")
            textSize = 30f
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableLeft = sidePadding
        val tableTop = topPadding
        val tableWidth = totalWidth - sidePadding * 2f
        val tableBaseWidth = tableWidth - remarkColWidth
        val turnColWidth = 132f
        val charColWidth = (tableBaseWidth - turnColWidth) / 5f
        val tableRight = tableLeft + tableWidth

        val rowHeights = ArrayList<Int>()
        var tableContentHeight = headerHeight

        for (item in displayData) {
            var maxH = baseRowHeight

            // 计算操作列高度
            for (text in item.characterActions) {
                if (text.isNotEmpty()) {
                    val layout = StaticLayout.Builder.obtain(
                        text, 0, text.length, paintTxt, (charColWidth - (padding * 2)).toInt()
                    ).build()
                    maxH = max(maxH, layout.height + (padding * 2))
                }
            }

            // 🔴 新增：计算备注列高度
            if (hasRemarks && item.remark.isNotEmpty()) {
                val layout = StaticLayout.Builder.obtain(
                    item.remark,
                    0,
                    item.remark.length,
                    paintRemarkTxt,
                    (remarkColWidth - (padding * 2)).toInt()
                ).build()
                maxH = max(maxH, layout.height + (padding * 2))
            }

            rowHeights.add(maxH)
            tableContentHeight += maxH
        }

        // 4. 创建 Bitmap
        val desiredHeight = (topPadding + tableContentHeight + bottomPadding).toInt()
        val bitmap = createStyledBitmap(context, totalWidth, desiredHeight)
        val canvas = Canvas(bitmap)
        val panelBottom = max(tableTop + tableContentHeight + 14f, bitmap.height - 42f)
        val panelRect = RectF(
            tableLeft - 12f,
            tableTop - 14f,
            tableRight + 12f,
            panelBottom,
        )
        drawExportTitle(canvas, gameTitle, exportSubtitle)
        drawPaperPanel(canvas, panelRect)

        // 5. 绘制表头
        canvas.drawRoundRect(
            RectF(tableLeft, tableTop, tableRight, tableTop + headerHeight),
            10f,
            10f,
            paintHeaderBg
        )

        // 绘制 "回合"
        drawCenteredText(canvas, "回合", tableLeft + turnColWidth / 2f, tableTop + headerHeight / 2f, paintHeaderTxt)
        canvas.drawLine(
            tableLeft + turnColWidth,
            tableTop,
            tableLeft + turnColWidth,
            tableTop + headerHeight,
            paintLine
        )

        // 绘制 5个操作列表头
        for (i in 0 until 5) {
            val cellX = tableLeft + turnColWidth + (i * charColWidth)
            val headerText = headers.getOrNull(i).orEmpty().trim()
            drawHeaderAgent(canvas, context, headerText, RectF(cellX, tableTop, cellX + charColWidth, tableTop + headerHeight), paintHeaderTxt)
            val lx = tableLeft + turnColWidth + (i * charColWidth)
            // 最后一列操作后，如果有备注，需要画线；如果没有备注，这里就是边界
            if (i < 4 || hasRemarks) {
                canvas.drawLine(
                    lx + charColWidth,
                    tableTop,
                    lx + charColWidth,
                    tableTop + headerHeight,
                    paintLine
                )
            }
        }

        // 🔴 新增：绘制 "备注" 表头
        if (hasRemarks) {
            val remarkHeaderX = tableLeft + tableBaseWidth + (remarkColWidth / 2f)
            drawCenteredText(canvas, "备注", remarkHeaderX, tableTop + headerHeight / 2f, paintHeaderTxt)
        }

        // 6. 绘制数据行
        var currentY = tableTop + headerHeight
        val boldPaint = Paint(paintHeaderTxt).apply {
            color = Color.parseColor("#5A3A22")
            textSize = 38f
            isFakeBoldText = true
        }

        for (i in displayData.indices) {
            val item = displayData[i]
            val rowH = rowHeights[i].toFloat()
            val bgPaint = if (i % 2 == 0) paintRowBgOdd else paintRowBgEven

            // 背景铺满整个宽度
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + rowH, bgPaint)

            // 回合数
            drawCenteredText(
                canvas,
                "${item.turnNumber}",
                tableLeft + turnColWidth / 2f,
                currentY + rowH / 2f,
                boldPaint
            )
            canvas.drawLine(
                tableLeft + turnColWidth,
                currentY,
                tableLeft + turnColWidth,
                currentY + rowH,
                paintLine
            )

            // 操作列
            for (j in 0 until 5) {
                val cellX = tableLeft + turnColWidth + (j * charColWidth)
                val text = item.characterActions[j]
                if (text.isNotEmpty()) {
                    canvas.save()
                    canvas.translate(cellX + padding, currentY + padding)
                    val layout = StaticLayout.Builder.obtain(
                        text, 0, text.length, paintTxt, (charColWidth - (padding * 2)).toInt()
                    ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()

                    // 垂直居中
                    val textH = layout.height
                    val offsetY = (rowH - (padding * 2) - textH) / 2f
                    canvas.translate(0f, offsetY)

                    layout.draw(canvas)
                    canvas.restore()
                }

                // 竖线
                val lx = cellX + charColWidth
                if (j < 4 || hasRemarks) {
                    canvas.drawLine(lx, currentY, lx, currentY + rowH, paintLine)
                }
            }

            // 🔴 新增：绘制备注内容
            if (hasRemarks && item.remark.isNotEmpty()) {
                val remarkX = tableLeft + tableBaseWidth
                canvas.save()
                canvas.translate(remarkX + padding, currentY + padding)
                val layout = StaticLayout.Builder.obtain(
                    item.remark,
                    0,
                    item.remark.length,
                    paintRemarkTxt,
                    (remarkColWidth - (padding * 2)).toInt()
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                // 垂直居中
                val textH = layout.height
                val offsetY = (rowH - (padding * 2) - textH) / 2f
                canvas.translate(0f, offsetY)

                layout.draw(canvas)
                canvas.restore()
            }

            // 底部分割线
            canvas.drawLine(tableLeft, currentY + rowH, tableRight, currentY + rowH, paintLine)
            currentY += rowH
        }
        canvas.drawRoundRect(
            RectF(tableLeft, tableTop, tableRight, tableTop + tableContentHeight),
            10f,
            10f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8D633D")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
        )

        saveBitmapToGallery(context, bitmap)
    }

    private fun createStyledBitmap(context: Context, width: Int, desiredHeight: Int): Bitmap {
        val background = runCatching {
            context.assets.open(BACKGROUND_ASSET).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        val baseHeight = background?.let { bg ->
            (bg.height * (width / bg.width.toFloat())).toInt()
        } ?: desiredHeight
        val height = max(desiredHeight, baseHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background == null) {
            canvas.drawColor(Color.parseColor("#E9D7B7"))
            return bitmap
        }
        canvas.drawBitmap(
            background,
            null,
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        background.recycle()
        return bitmap
    }

    private fun drawExportTitle(canvas: Canvas, title: String, subtitle: String) {
        val x = canvas.width / 2f
        val shadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C633F")
            textSize = 78f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9A441")
            textSize = 78f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, x, 116f, shadowPaint)
        canvas.drawText(title, x, 112f, titlePaint)
        val cleanSubtitle = subtitle.trim()
        if (cleanSubtitle.isBlank()) return
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3F3832")
            textSize = fitTextSize(this, cleanSubtitle, canvas.width - 160f, 38f, 22f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(cleanSubtitle, x, 178f, subtitlePaint)
    }

    private fun drawPaperPanel(canvas: Canvas, rect: RectF) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 91, 56, 28)
        }
        canvas.drawRoundRect(RectF(rect.left + 4f, rect.top + 6f, rect.right + 4f, rect.bottom + 6f), 18f, 18f, shadowPaint)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(242, 255, 248, 232)
        }
        canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C79A61")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, 18f, 18f, strokePaint)
    }

    private fun drawHeaderAgent(
        canvas: Canvas,
        context: Context,
        text: String,
        rect: RectF,
        paint: Paint,
    ) {
        val avatar = loadAgentAvatar(context, text)
        if (avatar == null) {
            if (text.isNotBlank()) {
                drawFittedCenteredText(canvas, text, rect, paint, maxSize = 34f, minSize = 20f)
            }
            return
        }

        val avatarRect = RectF(rect.centerX() - 24f, rect.top + 13f, rect.centerX() + 24f, rect.top + 61f)
        val path = Path().apply { addOval(avatarRect, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(avatar, centerCropSquare(avatar), avatarRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        avatar.recycle()

        drawFittedCenteredText(
            canvas,
            text,
            RectF(rect.left + 8f, rect.top + 66f, rect.right - 8f, rect.bottom - 10f),
            paint,
            maxSize = 25f,
            minSize = 15f,
        )
    }

    private fun loadAgentAvatar(context: Context, agentName: String): Bitmap? {
        if (agentName.isBlank()) return null
        return runCatching {
            context.assets.open("$agentName.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun centerCropSquare(bitmap: Bitmap): Rect {
        val size = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        return Rect(left, top, left + size, top + size)
    }

    private fun drawFittedCenteredText(
        canvas: Canvas,
        text: String,
        rect: RectF,
        paint: Paint,
        maxSize: Float,
        minSize: Float,
    ) {
        var size = maxSize
        paint.textSize = size
        while (size > minSize && paint.measureText(text) > rect.width() - 8f) {
            size -= 1f
            paint.textSize = size
        }
        drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), paint)
    }

    private fun fitTextSize(paint: TextPaint, text: String, maxWidth: Float, maxSize: Float, minSize: Float): Float {
        var size = maxSize
        paint.textSize = size
        while (size > minSize && paint.measureText(text) > maxWidth) {
            size -= 1f
            paint.textSize = size
        }
        return size
    }

    // 輔助方法：畫文字 (直接照搬，不需修改)
    fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, x, y - bounds.exactCenterY(), paint)
    }

    // 儲存到相簿 (需加入 context 參數)
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val filename = "GameAssist_${System.currentTimeMillis()}.png"
        var outputStream: OutputStream? = null
        var imageUri: Uri? = null
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/GameAssist"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val contentResolver = context.contentResolver
            imageUri =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { uri ->
                outputStream = contentResolver.openOutputStream(uri)
                outputStream?.let { stream ->
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        stream
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageUri != null) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
            }
            Toast.makeText(context, "图片已保存", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
            }
        }
    }
}
