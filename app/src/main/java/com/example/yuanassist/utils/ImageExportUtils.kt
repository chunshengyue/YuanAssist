package com.example.yuanassist.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

    // 傳入 Context 供 Toast 和 ContentResolver 使用，傳入 data 和 headers 生成圖片
    fun generateAndSaveImage(
        context: Context,
        displayData: List<TurnData>,
        headers: Array<String>
    ) {
        if (displayData.isEmpty()) {
            Toast.makeText(context, "无数据", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 检查是否有任何一行包含备注
        val hasRemarks = displayData.any { it.remark.isNotEmpty() }

        // --- 以下繪圖邏輯與原程式碼完全相同，直接照搬 ---
        val baseWidth = 1080
        val remarkColWidth = if (hasRemarks) 350 else 0
        val totalWidth = baseWidth + remarkColWidth
        val headerHeight = 100
        val baseRowHeight = 120
        val padding = 20

        val paintTxt = TextPaint().apply {
            color = Color.BLACK; textSize = 40f; textAlign = Paint.Align.LEFT; isAntiAlias = true
        }
        val paintLine = Paint().apply { color = Color.parseColor("#A1887F"); strokeWidth = 2f }
        val paintHeaderBg = Paint().apply { color = Color.parseColor("#A1887F") }
        val paintRowBgOdd = Paint().apply { color = Color.parseColor("#EFEBE9") }
        val paintRowBgEven = Paint().apply { color = Color.parseColor("#D7CCC8") }
        val paintHeaderTxt = Paint().apply {
            color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val paintRemarkTxt = TextPaint().apply {
            color = Color.parseColor("#3E2723"); textSize = 32f; textAlign =
            Paint.Align.LEFT; isAntiAlias = true
        }

        val turnColWidth = 150
        val charColWidth = (baseWidth - turnColWidth) / 5

        val rowHeights = ArrayList<Int>()
        var totalHeight = headerHeight

        // ... 中間的測量高度與繪製迴圈 (for (item in displayData) ...) 請直接從原代碼照搬 ...
        // (為了版面簡潔這裡不展開，只需將原本的 currentDisplayData 替換為傳入的 displayData 即可)

        for (item in displayData) {
            var maxH = baseRowHeight

            // 计算操作列高度
            for (text in item.characterActions) {
                if (text.isNotEmpty()) {
                    val layout = StaticLayout.Builder.obtain(
                        text, 0, text.length, paintTxt, charColWidth - (padding * 2)
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
                    remarkColWidth - (padding * 2)
                ).build()
                maxH = max(maxH, layout.height + (padding * 2))
            }

            rowHeights.add(maxH)
            totalHeight += maxH
        }

        // 4. 创建 Bitmap
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#D7CCC8"))

        // 5. 绘制表头
        canvas.drawRect(0f, 0f, totalWidth.toFloat(), headerHeight.toFloat(), paintHeaderBg)

        // 绘制 "回合"
        drawCenteredText(canvas, "回合", turnColWidth / 2f, headerHeight / 2f, paintHeaderTxt)
        canvas.drawLine(
            turnColWidth.toFloat(),
            0f,
            turnColWidth.toFloat(),
            headerHeight.toFloat(),
            paintLine
        )

        // 绘制 5个操作列表头
        for (i in 0 until 5) {
            val cx = turnColWidth + (i * charColWidth) + (charColWidth / 2f)
            drawCenteredText(canvas, headers[i], cx, headerHeight / 2f, paintHeaderTxt)
            val lx = turnColWidth + (i * charColWidth)
            // 最后一列操作后，如果有备注，需要画线；如果没有备注，这里就是边界
            if (i < 4 || hasRemarks) {
                canvas.drawLine(
                    (lx + charColWidth).toFloat(),
                    0f,
                    (lx + charColWidth).toFloat(),
                    headerHeight.toFloat(),
                    paintLine
                )
            }
        }

        // 🔴 新增：绘制 "备注" 表头
        if (hasRemarks) {
            val remarkHeaderX = baseWidth + (remarkColWidth / 2f)
            drawCenteredText(canvas, "备注", remarkHeaderX, headerHeight / 2f, paintHeaderTxt)
        }

        // 6. 绘制数据行
        var currentY = headerHeight.toFloat()
        val boldPaint = Paint(paintHeaderTxt).apply {
            color = Color.parseColor("#3E2723"); isFakeBoldText = true
        }

        for (i in displayData.indices) {
            val item = displayData[i]
            val rowH = rowHeights[i].toFloat()
            val bgPaint = if (i % 2 == 0) paintRowBgOdd else paintRowBgEven

            // 背景铺满整个宽度
            canvas.drawRect(0f, currentY, totalWidth.toFloat(), currentY + rowH, bgPaint)

            // 回合数
            drawCenteredText(
                canvas,
                "${item.turnNumber}",
                turnColWidth / 2f,
                currentY + rowH / 2f,
                boldPaint
            )
            canvas.drawLine(
                turnColWidth.toFloat(),
                currentY,
                turnColWidth.toFloat(),
                currentY + rowH,
                paintLine
            )

            // 操作列
            for (j in 0 until 5) {
                val cellX = turnColWidth + (j * charColWidth).toFloat()
                val text = item.characterActions[j]
                if (text.isNotEmpty()) {
                    canvas.save()
                    canvas.translate(cellX + padding, currentY + padding)
                    val layout = StaticLayout.Builder.obtain(
                        text, 0, text.length, paintTxt, charColWidth - (padding * 2)
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
                val remarkX = baseWidth.toFloat()
                canvas.save()
                canvas.translate(remarkX + padding, currentY + padding)
                val layout = StaticLayout.Builder.obtain(
                    item.remark,
                    0,
                    item.remark.length,
                    paintRemarkTxt,
                    remarkColWidth - (padding * 2)
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                // 垂直居中
                val textH = layout.height
                val offsetY = (rowH - (padding * 2) - textH) / 2f
                canvas.translate(0f, offsetY)

                layout.draw(canvas)
                canvas.restore()
            }

            // 底部分割线
            canvas.drawLine(0f, currentY + rowH, totalWidth.toFloat(), currentY + rowH, paintLine)
            currentY += rowH
        }

        saveBitmapToGallery(context, bitmap)
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