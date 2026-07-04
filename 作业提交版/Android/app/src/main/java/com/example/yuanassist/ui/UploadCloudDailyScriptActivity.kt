package com.example.yuanassist.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.yuanassist.R
import com.example.yuanassist.network.CloudDailyScriptPublishPayload
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.DailyScriptBundleZipStore
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.PackedDailyScriptBundle
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.UserDailyScriptBundle
import com.example.yuanassist.utils.UserDailyScriptStore
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class UploadCloudDailyScriptActivity : AppCompatActivity() {
    private var selectedBundle: UserDailyScriptBundle? = null
    private var selectedGuideUris: List<Uri> = emptyList()
    private lateinit var progress: ProgressBar

    private val guidePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedGuideUris = uris
        bindGuidePreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_cloud_daily_script)
        applyStatusBarInsets()
        progress = findViewById(R.id.progress_upload_cloud_daily_script)
        findViewById<ImageView>(R.id.btn_upload_cloud_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_select_cloud_local_script).setOnClickListener { showBundlePicker() }
        findViewById<Button>(R.id.btn_select_cloud_guide_images).setOnClickListener { guidePicker.launch("image/*") }
        findViewById<Button>(R.id.btn_publish_cloud_daily_script).setOnClickListener { publish() }
    }

    private fun showBundlePicker() {
        val bundles = UserDailyScriptStore.listBundles(this)
        if (bundles.isEmpty()) {
            Toast.makeText(this, "本地脚本库暂无录制脚本", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = bundles.map { it.scriptId }.toTypedArray()
        val dialog = AlertDialog.Builder(DialogUtils.getThemeContext(this))
            .setTitle("选择脚本")
            .setAdapter(DialogUtils.fixedOptionTextAdapter(this, labels)) { _, which ->
                selectedBundle = bundles[which]
                findViewById<TextView>(R.id.tv_selected_cloud_local_script).text = labels[which]
                val titleInput = findViewById<EditText>(R.id.et_upload_cloud_title)
                if (titleInput.text.isNullOrBlank()) titleInput.setText(labels[which])
            }
            .show()
        DialogUtils.styleAlertDialog(dialog)
    }

    private fun publish() {
        val bundle = selectedBundle
        val title = findViewById<EditText>(R.id.et_upload_cloud_title).text?.toString().orEmpty().trim()
        val description = findViewById<EditText>(R.id.et_upload_cloud_description).text?.toString().orEmpty().trim()
        val tags = findViewById<EditText>(R.id.et_upload_cloud_tags).text?.toString().orEmpty().trim()
        if (bundle == null) {
            Toast.makeText(this, "请先选择本地录制脚本", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isBlank()) {
            Toast.makeText(this, "标题不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        RunLogger.clear()
        RunLogger.i(module = "云端脚本发布", section = "总流程", message = "开始：$title，图片=${selectedGuideUris.size}张")
        setPublishing(true)
        val packed = runCatching { DailyScriptBundleZipStore.packBundle(cacheDir, bundle) }.getOrElse {
            RunLogger.e(module = "云端脚本发布", section = "打包", message = "失败：${it.message ?: "无错误信息"}", throwable = it)
            setPublishing(false)
            Toast.makeText(this, "打包失败：${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        RunLogger.i(module = "云端脚本发布", section = "打包", message = "完成：任务=${packed.taskCount}，大小=${packed.sizeBytes / 1024}KB")
        uploadGuideImagesThenBundle(title, description, tags, packed)
    }

    private fun uploadGuideImagesThenBundle(
        title: String,
        description: String,
        tags: String,
        packed: PackedDailyScriptBundle,
    ) {
        if (selectedGuideUris.isEmpty()) {
            RunLogger.i(module = "云端脚本发布", section = "图片指引", message = "未选择，跳过")
            uploadBundleAndPublish(title, description, tags, emptyList(), packed)
            return
        }
        Toast.makeText(this, "正在上传图片指引...", Toast.LENGTH_SHORT).show()
        RunLogger.i(module = "云端脚本发布", section = "图片指引", message = "读取${selectedGuideUris.size}张")
        val files = selectedGuideUris.mapNotNull { uriToCacheFile(it, "cloud_guide") }
        if (files.size != selectedGuideUris.size) {
            RunLogger.e(module = "云端脚本发布", section = "图片指引", message = "读取失败：选中${selectedGuideUris.size}张，缓存${files.size}张")
            setPublishing(false)
            files.forEach { it.delete() }
            packed.file.delete()
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_LONG).show()
            return
        }
        RunLogger.i(module = "云端脚本发布", section = "图片指引", message = "读取完成：${files.size}张")
        val uploadedUrls = mutableMapOf<File, String>()
        val uploadLock = Any()
        var finished = false
        files.forEachIndexed { index, file ->
            val imageIndex = index + 1
            uploadImageToImageBed(
                file = file,
                onSuccess = { url ->
                    val readyUrls = synchronized(uploadLock) {
                        if (finished) return@uploadImageToImageBed
                        uploadedUrls[file] = url
                        RunLogger.i(module = "云端脚本发布", section = "图片指引", message = "第$imageIndex/${files.size}张上传成功")
                        if (uploadedUrls.size == files.size) {
                            finished = true
                            files.mapNotNull { uploadedUrls[it] }
                        } else {
                            null
                        }
                    }
                    if (readyUrls != null) {
                        RunLogger.i(module = "云端脚本发布", section = "图片指引", message = "全部上传完成：${readyUrls.size}张")
                        runOnUiThread {
                            files.forEach { it.delete() }
                            uploadBundleAndPublish(title, description, tags, readyUrls, packed)
                        }
                    }
                },
                onError = { message ->
                    val shouldReport = synchronized(uploadLock) {
                        if (finished) {
                            false
                        } else {
                            finished = true
                            true
                        }
                    }
                    if (shouldReport) {
                        RunLogger.e(module = "云端脚本发布", section = "图片指引", message = "第$imageIndex/${files.size}张上传失败：$message")
                        runOnUiThread {
                            setPublishing(false)
                            files.forEach { it.delete() }
                            packed.file.delete()
                            Toast.makeText(this, "图片上传失败：$message", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }

    private fun uploadBundleAndPublish(
        title: String,
        description: String,
        tags: String,
        guideImages: List<String>,
        packed: PackedDailyScriptBundle,
    ) {
        Toast.makeText(this, "正在上传脚本包...", Toast.LENGTH_SHORT).show()
        RunLogger.i(module = "云端脚本发布", section = "脚本包", message = "创建上传票据，图片=${guideImages.size}张")
        SupabaseRepository.createDailyScriptUpload(
            context = this,
            title = title,
            bundleSize = packed.sizeBytes,
            onSuccess = { ticket ->
                RunLogger.i(module = "云端脚本发布", section = "脚本包", message = "票据创建成功，开始上传 zip")
                RunLogger.i(module = "云端脚本发布", section = "脚本包", message = "zip 信息：path=${ticket.bundlePath} size=${packed.file.length() / 1024}KB")
                SupabaseRepository.uploadDailyScriptBundle(
                    uploadUrl = ticket.uploadUrl,
                    zipFile = packed.file,
                    onSuccess = {
                        RunLogger.i(module = "云端脚本发布", section = "脚本包", message = "zip 上传成功，发布元数据")
                        SupabaseRepository.publishDailyScript(
                            context = this,
                            payload = CloudDailyScriptPublishPayload(
                                scriptObjectId = ticket.scriptObjectId,
                                title = title,
                                description = description,
                                tags = tags,
                                guideImages = guideImages,
                                bundlePath = ticket.bundlePath,
                                bundleSize = packed.sizeBytes,
                                taskCount = packed.taskCount,
                            ),
                            onSuccess = {
                                RunLogger.i(module = "云端脚本发布", section = "总流程", message = "发布成功")
                                packed.file.delete()
                                setPublishing(false)
                                Toast.makeText(this, "发布成功", Toast.LENGTH_LONG).show()
                                finish()
                            },
                            onError = { message ->
                                RunLogger.e(module = "云端脚本发布", section = "脚本包", message = "发布元数据失败：$message")
                                setPublishing(false)
                                Toast.makeText(this, "发布失败：$message", Toast.LENGTH_LONG).show()
                            },
                        )
                    },
                    onError = { message ->
                        RunLogger.e(module = "云端脚本发布", section = "脚本包", message = "zip 上传失败：$message")
                        setPublishing(false)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    },
                )
            },
            onError = { message ->
                RunLogger.e(module = "云端脚本发布", section = "脚本包", message = "创建上传票据失败：$message")
                setPublishing(false)
                Toast.makeText(this, "创建上传失败：$message", Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun bindGuidePreview() {
        val container = findViewById<LinearLayout>(R.id.layout_upload_cloud_guide_preview)
        container.removeAllViews()
        if (selectedGuideUris.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "未选择图片"
                setTextColor(android.graphics.Color.parseColor("#8C7A61"))
                textSize = 13f
            })
            return
        }
        selectedGuideUris.forEach { uri ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180.dp()).apply {
                    bottomMargin = 10.dp()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            container.addView(imageView)
            Coil.imageLoader(this).enqueue(
                ImageRequest.Builder(this)
                    .data(uri)
                    .target(imageView)
                    .build(),
            )
        }
    }

    private fun uriToCacheFile(uri: Uri, prefix: String): File? {
        return runCatching {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}_${uri.hashCode()}.png")
            inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }.getOrNull()
    }

    private fun uploadImageToImageBed(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val fileBody = file.asRequestBody("image/*".toMediaTypeOrNull())
        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, fileBody)
            .addFormDataPart("outputFormat", "webp")
            .build()
        val request = okhttp3.Request.Builder()
            .url("https://img.scdn.io/api/v1.php")
            .post(requestBody)
            .build()
        okhttp3.OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                RunLogger.e(module = "云端脚本发布", section = "图片指引", message = "图床网络请求失败：${e.message ?: "无错误信息"}", throwable = e)
                onError(e.message ?: "图床网络请求失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    onError("HTTP ${response.code}")
                    return
                }
                runCatching {
                    val json = org.json.JSONObject(responseBody)
                    if (!json.optBoolean("success")) error(json.optString("message", "上传被图床拒绝"))
                    json.optString("url").takeIf { it.isNotBlank() } ?: error("图床未返回图片链接")
                }.onSuccess(onSuccess).onFailure {
                    RunLogger.e(module = "云端脚本发布", section = "图片指引", message = "图床响应解析失败：${it.message ?: "无错误信息"}", throwable = it)
                    onError(it.message ?: "图床响应解析失败")
                }
            }
        })
    }

    private fun setPublishing(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btn_publish_cloud_daily_script).isEnabled = !value
    }

    private fun applyStatusBarInsets() {
        val spacer = findViewById<View>(R.id.view_upload_cloud_status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(spacer) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams = view.layoutParams.apply { height = top }
            insets
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
