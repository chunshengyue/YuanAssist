package com.example.yuanassist.utils

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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.yuanassist.model.ImportedCharacterRecord

object CharacterImportOverviewImageRenderer {

    private const val BACKGROUND_ASSET = "character_import_table_bg.png"
    private val columnCenters = listOf(284f, 452f, 619f, 787f, 955f)

    fun render(
        context: Context,
        records: List<ImportedCharacterRecord>,
        gameTitle: String = "如鸢",
        exportSubtitle: String = "",
    ): Bitmap {
        val safeRecords = List(5) { index ->
            val item = records.getOrNull(index)
            if (item == null) {
                ImportedCharacterRecord(slot = index + 1, fates = List(3) { "" })
            } else {
                item.copy(
                    slot = index + 1,
                    fates = List(3) { fateIndex -> item.fates.getOrNull(fateIndex).orEmpty() },
                )
            }
        }

        val background = context.assets.open(BACKGROUND_ASSET).use { BitmapFactory.decodeStream(it) }
        val bitmap = background.copy(Bitmap.Config.ARGB_8888, true)
        background.recycle()
        val canvas = Canvas(bitmap)
        drawExportHeader(canvas, gameTitle, exportSubtitle)

        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F443C")
            textSize = 27f
            isFakeBoldText = true
        }
        val faintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A4907C")
            textSize = 22f
        }

        safeRecords.forEachIndexed { index, record ->
            val centerX = columnCenters[index]
            drawRoleCell(canvas, context, centerX, record)
            drawStarCell(canvas, RectF(centerX - 74f, 510f, centerX + 74f, 618f), record.starCount)
            drawCenteredText(
                canvas,
                record.attack.ifBlank { "未填" },
                RectF(centerX - 74f, 619f, centerX + 74f, 727f),
                if (record.attack.isBlank()) faintPaint else valuePaint,
            )
            drawCenteredText(
                canvas,
                record.hp.ifBlank { "未填" },
                RectF(centerX - 74f, 728f, centerX + 74f, 837f),
                if (record.hp.isBlank()) faintPaint else valuePaint,
            )
            drawFates(canvas, centerX, record.fates)
            drawRemarkCell(canvas, RectF(centerX - 74f, 1052f, centerX + 74f, 1207f), record.remark, faintPaint, valuePaint)
        }

        return bitmap
    }

    private fun drawExportHeader(canvas: Canvas, gameTitle: String, exportSubtitle: String) {
        val titleShadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C633F")
            textSize = 84f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9A441")
            textSize = 84f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(gameTitle, 543f, 115f, titleShadowPaint)
        canvas.drawText(gameTitle, 543f, 111f, titlePaint)

        val subtitle = exportSubtitle.trim()
        if (subtitle.isBlank()) return
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3F3832")
            textSize = fitTextSize(this, subtitle, 520f, 44f, 26f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subtitle, 543f, 211f, subtitlePaint)
    }

    private fun drawRoleCell(canvas: Canvas, context: Context, centerX: Float, record: ImportedCharacterRecord) {
        val avatarRect = RectF(centerX - 40f, 370f, centerX + 40f, 450f)
        drawAvatar(canvas, context, avatarRect, record.name)

        val nameText = record.name.ifBlank { "未识别角色" }
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (record.name.isBlank()) Color.parseColor("#9E8872") else Color.parseColor("#5A3A22")
            textSize = 26f
            isFakeBoldText = true
        }
        drawCenteredParagraph(
            canvas = canvas,
            text = nameText,
            rect = RectF(centerX - 74f, 448f, centerX + 74f, 480f),
            paint = namePaint,
            maxLines = 2,
        )
    }

    private fun drawAvatar(canvas: Canvas, context: Context, rect: RectF, agentName: String) {
        val bitmap = if (agentName.isBlank()) null else runCatching {
            context.assets.open("$agentName.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        if (bitmap == null) {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E9D9C2")
            }
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, fallbackPaint)
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B513E")
                textSize = 34f
                isFakeBoldText = true
            }
            val text = agentName.takeIf { it.isNotBlank() }?.take(1) ?: "?"
            drawCenteredText(canvas, text, rect, textPaint)
            return
        }

        val path = Path().apply {
            addOval(rect, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        val src = centerCropSquare(bitmap)
        canvas.drawBitmap(bitmap, src, rect, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
        bitmap.recycle()
    }

    private fun centerCropSquare(bitmap: Bitmap): Rect {
        val size = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - size) / 2
        val top = (bitmap.height - size) / 2
        return Rect(left, top, left + size, top + size)
    }

    private fun drawFates(canvas: Canvas, centerX: Float, fates: List<String>) {
        val fateRects = listOf(
            RectF(centerX - 76f, 858f, centerX + 76f, 922f),
            RectF(centerX - 76f, 922f, centerX + 76f, 986f),
            RectF(centerX - 76f, 986f, centerX + 76f, 1050f),
        )
        fates.filter(String::isNotBlank).take(3).forEachIndexed { index, text ->
            val displayText = text.removePrefix("橙").removePrefix("紫")
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when {
                    text.startsWith("橙") -> Color.parseColor("#C97924")
                    text.startsWith("紫") -> Color.parseColor("#7658C8")
                    else -> Color.parseColor("#2E6DB4")
                }
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            drawWrappedCenteredText(canvas, displayText, fateRects[index], paint, maxSize = 24f, minSize = 12f)
        }
    }

    private fun drawStarCell(canvas: Canvas, rect: RectF, starCount: Int) {
        val count = starCount.coerceIn(0, 6)
        if (count <= 0) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A4907C")
                textSize = 26f
            }
            drawCenteredText(canvas, "无", rect, paint)
            return
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D89A44")
            textSize = if (count >= 5) 24f else 27f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("★".repeat(count), rect.centerX(), rect.centerY() - paint.fontMetrics.ascent / 2f - paint.fontMetrics.descent / 2f, paint)
    }

    private fun drawRemarkCell(
        canvas: Canvas,
        rect: RectF,
        text: String,
        faintPaint: TextPaint,
        valuePaint: TextPaint,
    ) {
        val renderPaint = TextPaint(if (text.isBlank()) faintPaint else valuePaint).apply {
            textSize = if (text.length > 12) 18f else 22f
        }
        drawCenteredParagraph(
            canvas = canvas,
            text = text.ifBlank { " " },
            rect = RectF(rect.left + 16f, rect.top + 14f, rect.right - 16f, rect.bottom - 14f),
            paint = renderPaint,
            maxLines = 4,
        )
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, paint: TextPaint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(
            text,
            rect.centerX(),
            rect.centerY() - bounds.exactCenterY(),
            paint.apply { textAlign = Paint.Align.CENTER },
        )
    }

    private fun drawCenteredParagraph(canvas: Canvas, text: String, rect: RectF, paint: TextPaint, maxLines: Int = 2) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, rect.width().toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .build()
        canvas.save()
        canvas.translate(rect.left, rect.centerY() - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
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

    private fun drawWrappedCenteredText(
        canvas: Canvas,
        text: String,
        rect: RectF,
        paint: TextPaint,
        maxSize: Float,
        minSize: Float,
    ) {
        var size = maxSize
        var lines: List<String>
        while (true) {
            paint.textSize = size
            lines = wrapTextToLines(text, paint, rect.width())
            val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent) * 1.08f
            if (lines.size <= 2 && lineHeight * lines.size <= rect.height()) break
            if (size <= minSize) break
            size -= 1f
        }
        if (lines.size > 2) {
            lines = listOf(lines.first(), lines.drop(1).joinToString(""))
        }

        val fontMetrics = paint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * 1.08f
        val totalHeight = lineHeight * lines.size
        var baseline = rect.centerY() - totalHeight / 2f - fontMetrics.ascent
        lines.forEach { line ->
            canvas.drawText(line, rect.centerX(), baseline, paint)
            baseline += lineHeight
        }
    }

    private fun wrapTextToLines(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.forEach { char ->
            val next = current + char
            if (current.isNotEmpty() && paint.measureText(next) > maxWidth) {
                lines += current
                current = char.toString()
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
