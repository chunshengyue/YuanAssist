package com.example.yuanassist.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.yuanassist.R
import com.example.yuanassist.core.DailyPlanSelection
import com.example.yuanassist.core.DailyScriptLibraryBridge
import com.example.yuanassist.model.cloud_daily_script
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.DailyScriptBundleZipStore
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CloudDailyScriptActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCRIPT_ID = "extra_script_id"
    }

    private val gson = Gson()
    private var detail: cloud_daily_script? = null
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_daily_script)
        applyStatusBarInsets()
        findViewById<ImageView>(R.id.btn_cloud_detail_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_cloud_save_local).setOnClickListener { downloadAndSave(importAfterSave = false) }
        findViewById<Button>(R.id.btn_cloud_import_daily_window).setOnClickListener { downloadAndSave(importAfterSave = true) }
        loadDetail()
    }

    private fun loadDetail() {
        val scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
        if (scriptId.isBlank()) {
            Toast.makeText(this, "脚本不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        SupabaseRepository.getDailyScriptDetail(
            scriptId = scriptId,
            onSuccess = {
                detail = it
                bindDetail(it)
            },
            onError = { message ->
                Toast.makeText(this, "加载失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun bindDetail(item: cloud_daily_script) {
        findViewById<TextView>(R.id.tv_cloud_detail_title).text = item.title.ifBlank { "未命名脚本" }
        val author = item.author?.nickname?.takeIf { it.isNotBlank() }
            ?: item.author?.username?.takeIf { it.isNotBlank() }
            ?: "匿名用户"
        findViewById<TextView>(R.id.tv_cloud_detail_meta).text =
            "$author · ${item.taskCount}步 · ${item.downloadCount}次下载"
        findViewById<TextView>(R.id.tv_cloud_detail_tags).text = item.tags.ifBlank { "日常脚本" }
        findViewById<TextView>(R.id.tv_cloud_detail_description).text = item.description.ifBlank { "暂无说明" }
        bindGuideImages(parseGuideImages(item.guideImages))
    }

    private fun bindGuideImages(urls: List<String>) {
        val container = findViewById<LinearLayout>(R.id.layout_cloud_guide_images)
        container.removeAllViews()
        if (urls.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "暂无图片指引"
                setTextColor(android.graphics.Color.parseColor("#8C7A61"))
                textSize = 14f
            })
            return
        }
        urls.forEachIndexed { index, url ->
            if (index > 0) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12.dp())
                })
            }
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 260.dp())
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#EFE6D6"))
            }
            container.addView(imageView)
            Coil.imageLoader(this).enqueue(
                ImageRequest.Builder(this)
                    .data(url)
                    .target(imageView)
                    .build(),
            )
        }
    }

    private fun downloadAndSave(importAfterSave: Boolean) {
        val item = detail ?: return
        if (downloading) return
        val scriptId = item.objectId.orEmpty()
        downloading = true
        Toast.makeText(this, "正在获取脚本包...", Toast.LENGTH_SHORT).show()
        SupabaseRepository.createDailyScriptDownloadUrl(
            scriptId = scriptId,
            onSuccess = { ticket ->
                val zip = File(cacheDir, "cloud_daily_${scriptId}.zip")
                SupabaseRepository.downloadDailyScriptBundle(
                    downloadUrl = ticket.downloadUrl,
                    targetFile = zip,
                    onSuccess = { file -> saveDownloadedBundle(item, file, importAfterSave) },
                    onError = { message ->
                        downloading = false
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    },
                )
            },
            onError = { message ->
                downloading = false
                Toast.makeText(this, "下载地址获取失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun saveDownloadedBundle(item: cloud_daily_script, zipFile: File, importAfterSave: Boolean) {
        runCatching {
            val bundle = UserDailyScriptStore.createBundle(this, item.title)
            DailyScriptBundleZipStore.unpackToBundle(zipFile, bundle, gson)
            SupabaseRepository.incrementDailyScriptDownload(item.objectId.orEmpty())
            if (importAfterSave) {
                importBundle(bundle)
            } else {
                Toast.makeText(this, "已保存到本地脚本库", Toast.LENGTH_LONG).show()
            }
        }.onFailure { error ->
            Toast.makeText(this, "脚本导入失败：${error.message}", Toast.LENGTH_LONG).show()
        }
        downloading = false
    }

    private fun importBundle(bundle: UserDailyScriptBundle) {
        DailyScriptLibraryBridge.onDailyPlanSelected?.invoke(
            DailyPlanSelection(
                fileName = bundle.scriptId,
                jsonContent = bundle.scriptFile.readText(Charsets.UTF_8),
                templateDirPath = bundle.templatesDir.absolutePath,
            ),
        )
        Toast.makeText(this, "已导入日常悬浮窗", Toast.LENGTH_LONG).show()
    }

    private fun parseGuideImages(raw: String): List<String> {
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)
        }.getOrNull().orEmpty().filter { it.isNotBlank() }
    }

    private fun applyStatusBarInsets() {
        val spacer = findViewById<View>(R.id.view_cloud_detail_status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams = view.layoutParams.apply { height = top }
            insets
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
