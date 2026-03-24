package com.example.yuanassist.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.core.YuanAssistService

class ImagePickerActivity : AppCompatActivity() {

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        // 成功拿到图片，传给 Service
                        YuanAssistService.onImagePicked(bitmap)
                    } else {
                        Toast.makeText(this, "图片解析失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "读取出错: ${e.message}", Toast.LENGTH_SHORT).show()
                } catch (e: OutOfMemoryError) {
                    Toast.makeText(this, "图片太大，内存不足", Toast.LENGTH_SHORT).show()
                }
            }
            finish() // 无论成功失败，都要关闭自己
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启动时直接打开相册
        try {
            pickImage.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}