package com.example.yuanassist.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.R
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.min

class TestActivity : AppCompatActivity() {

    private lateinit var ivScreenshot: ImageView
    private lateinit var tvLog: TextView
    private lateinit var spinnerTemplates: Spinner
    private var currentBitmap: Bitmap? = null
    private var availableTemplates = listOf<String>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(it)
                    currentBitmap = BitmapFactory.decodeStream(inputStream)
                    ivScreenshot.setImageBitmap(currentBitmap)
                    log("截图加载成功: ${currentBitmap?.width} x ${currentBitmap?.height}")
                } catch (e: Exception) {
                    log("图片加载失败: ${e.message}")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        ivScreenshot = findViewById(R.id.iv_test_screenshot)
        tvLog = findViewById(R.id.tv_test_log)
        spinnerTemplates = findViewById(R.id.spinner_templates)

        if (!OpenCVLoader.initDebug()) {
            log("OpenCV 初始化失败！")
        } else {
            log("OpenCV 初始化成功！")
        }

        // 🔴 1. 动态读取 Assets 文件夹下的所有图片
        loadAssetsTemplates()

        findViewById<Button>(R.id.btn_test_upload).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<Button>(R.id.btn_test_run).setOnClickListener {
            if (currentBitmap == null) {
                Toast.makeText(this, "请先上传截图", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (availableTemplates.isEmpty()) {
                Toast.makeText(this, "Assets 文件夹中没有图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 获取下拉菜单当前选中的文件名
            val selectedTemplate = spinnerTemplates.selectedItem.toString()
            runMultiTargetMatchTest(selectedTemplate)
        }
    }

    private fun loadAssetsTemplates() {
        try {
            // 获取 assets 根目录所有文件
            val allFiles = assets.list("") ?: emptyArray()
            // 过滤出 .png 和 .jpg 文件
            availableTemplates =
                allFiles.filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) }

            if (availableTemplates.isNotEmpty()) {
                // 将文件名绑定到下拉菜单
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    availableTemplates
                )
                spinnerTemplates.adapter = adapter
                log("成功扫描到 ${availableTemplates.size} 个素材！")
            } else {
                log("⚠️ Assets 中未找到任何图片素材")
            }
        } catch (e: Exception) {
            log("读取 Assets 失败: ${e.message}")
        }
    }

    private fun log(msg: String) {
        tvLog.append("\n$msg")
        tvLog.post { (tvLog.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
    }

    private fun runMultiTargetMatchTest(templateName: String) {
        log("------------------------")
        log("开始【多目标】搜索: $templateName")
        val hwBitmap = currentBitmap ?: return

        // 等比例压缩到 1080
        val scale = 1080f / min(hwBitmap.width, hwBitmap.height)
        val scaledWidth = (hwBitmap.width * scale).toInt()
        val scaledHeight = (hwBitmap.height * scale).toInt()
        val scaledScreen = Bitmap.createScaledBitmap(hwBitmap, scaledWidth, scaledHeight, true)

        val templateBitmap = try {
            val inputStream = assets.open(templateName)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            log("加载素材失败: ${e.message}")
            return
        }

        if (templateBitmap == null) return

        val srcMat = Mat()
        val tmplMat = Mat()
        val swBitmap = scaledScreen.copy(Bitmap.Config.ARGB_8888, true)

        Utils.bitmapToMat(swBitmap, srcMat)
        Utils.bitmapToMat(templateBitmap, tmplMat)

        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)

        val resultMat = Mat()
        Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

        // 🔴 2. 核心：提取所有大于 0.9 的坐标
        val threshold = 0.90f
        val cols = resultMat.cols()
        val rows = resultMat.rows()

        // OpenCV 的 resultMat 是 32 位浮点矩阵，把它导出为一维 Float 数组极速遍历
        val floatArray = FloatArray(cols * rows)
        resultMat.get(0, 0, floatArray)

        // 暂存所有达标的坐标点和它的分数
        val rawMatches = mutableListOf<Pair<PointF, Float>>()
        var maxScore = 0f

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val score = floatArray[y * cols + x]
                if (score > maxScore) maxScore = score // 顺便记录一下全局最高分

                if (score >= threshold) {
                    rawMatches.add(Pair(PointF(x.toFloat(), y.toFloat()), score))
                }
            }
        }

        log("🔎 全局最高相似度: $maxScore")

        if (rawMatches.isEmpty()) {
            log("❌ 未找到相似度 >= $threshold 的目标")
        } else {
            // 🔴 3. 核心：NMS 过滤 (剔除重叠框)
            // 先按分数从高到低排序，优先保留最精准的
            rawMatches.sortByDescending { it.second }

            val finalMatches = mutableListOf<PointF>()
            // 剔除半径：只要两个点距离小于模板宽度的一半，就认为是同一个目标
            val radius = templateBitmap.width / 2.0

            for (match in rawMatches) {
                val pt = match.first
                var isOverlapping = false

                // 检查是否和已经保留的目标靠得太近
                for (fp in finalMatches) {
                    if (hypot((pt.x - fp.x).toDouble(), (pt.y - fp.y).toDouble()) < radius) {
                        isOverlapping = true
                        break
                    }
                }

                // 如果不重叠，则加入最终结果
                if (!isOverlapping) {
                    finalMatches.add(pt)
                }
            }

            log("✅ 成功锁定 ${finalMatches.size} 个有效目标！(阈值 $threshold)")

            // 🔴 4. 绘制所有的红框
            val canvas = Canvas(swBitmap)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }

            for ((index, pt) in finalMatches.withIndex()) {
                log("  -> 目标 ${index + 1}: X=${pt.x.toInt()}, Y=${pt.y.toInt()}")
                canvas.drawRect(
                    pt.x, pt.y,
                    pt.x + templateBitmap.width, pt.y + templateBitmap.height,
                    paint
                )
            }
            ivScreenshot.setImageBitmap(swBitmap)
        }

        // 释放内存
        srcMat.release(); tmplMat.release(); resultMat.release()
        templateBitmap.recycle()
        if (scaledScreen !== hwBitmap) scaledScreen.recycle()
    }
}