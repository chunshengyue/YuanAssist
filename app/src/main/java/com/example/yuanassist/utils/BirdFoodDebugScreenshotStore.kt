package com.example.yuanassist.utils

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.util.concurrent.Executors

object BirdFoodDebugScreenshotStore {

    private val DIRECTORY_PATH = Environment.DIRECTORY_PICTURES + "/YuanAssist"
    private const val ORIGINAL_FILE_NAME = "bird_food_debug_original.png"
    private const val CROPPED_FILE_NAME = "bird_food_debug_cropped.png"
    private val executor = Executors.newSingleThreadExecutor()

    fun saveSnapshots(context: Context, originalBitmap: Bitmap, croppedBitmap: Bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val originalCopy = cloneBitmap(originalBitmap) ?: return
        val croppedCopy = cloneBitmap(croppedBitmap) ?: run {
            originalCopy.recycle()
            return
        }

        executor.execute {
            try {
                saveBitmap(context, ORIGINAL_FILE_NAME, originalCopy)
                saveBitmap(context, CROPPED_FILE_NAME, croppedCopy)
            } finally {
                if (!originalCopy.isRecycled) originalCopy.recycle()
                if (!croppedCopy.isRecycled) croppedCopy.recycle()
            }
        }
    }

    private fun cloneBitmap(bitmap: Bitmap): Bitmap? {
        return try {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { copy ->
                Canvas(copy).drawBitmap(bitmap, 0f, 0f, null)
            }
        } catch (t: Throwable) {
            RunLogger.e("鸟食调试截图复制失败", t)
            null
        }
    }

    private fun saveBitmap(context: Context, fileName: String, bitmap: Bitmap) {
        val resolver = context.contentResolver
        deleteExisting(resolver, fileName)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null) {
            RunLogger.e("鸟食调试截图写入失败：无法创建 $fileName")
            return
        }

        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("bitmap.compress returned false")
                }
            } ?: throw IllegalStateException("openOutputStream returned null")

            val readyValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, readyValues, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            RunLogger.e("鸟食调试截图保存失败：$fileName", t)
        }
    }

    private fun deleteExisting(
        resolver: android.content.ContentResolver,
        fileName: String
    ) {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "$DIRECTORY_PATH%")
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idIndex)
                )
                resolver.delete(uri, null, null)
            }
        }
    }
}
