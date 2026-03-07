package com.example.yuanassist.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
// 注意：如果原来已经有部分 import 了，别重复贴，把缺的补上就行
import androidx.recyclerview.widget.RecyclerView
import com.example.yuanassist.R
import com.example.yuanassist.core.YuanAssistService
import com.example.yuanassist.model.DdlItem
import com.example.yuanassist.network.NetworkClient

// 1. 新增一个数据类来接收 update.json 的内容
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class MainActivity : AppCompatActivity() {

    // 2. 🔴 把你的 Gitee update.json raw 链接填在这里
    private val UPDATE_JSON_URL =
        "https://gitee.com/chunshengyue/yuan-assist-data/raw/master/update.json"

    // 🔴 新增 DDL 的 Adapter 变量
    private lateinit var ddlAdapter: DdlAdapter
    private val ddlList = ArrayList<DdlItem>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        initViews()
        // 🔴 启动时拉取 DDL 数据
        loadDdlData()
        checkAnnouncement()
    }

    private fun initViews() {
        // --- 核心功能区 ---
        findViewById<Button>(R.id.btn_main_start_combat).setOnClickListener {
            checkPermissionsAndStart("ACTION_START_COMBAT_WINDOW")
        }

        findViewById<Button>(R.id.btn_main_combat_settings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // --- 日常版区 ---
        findViewById<Button>(R.id.btn_main_start_daily).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                checkPermissionsAndStart("ACTION_START_DAILY_WINDOW")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("系统版本过低")
                    .setMessage("日常自动化功能依赖安卓 11 的原生截图 API，您的设备暂不支持。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }

        findViewById<Button>(R.id.btn_main_daily_settings).setOnClickListener {
            Toast.makeText(this, "日常版设置开发中...", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_main_scripts).setOnClickListener {
            startActivity(Intent(this@MainActivity, ScriptLibraryActivity::class.java))
        }

        findViewById<Button>(R.id.btn_main_strategies).setOnClickListener {
            val intent = Intent(this, StrategyListActivity::class.java)
            startActivity(intent)
        }

        // --- 底部功能区 ---
        findViewById<Button>(R.id.btn_main_tutorial).apply {
            text = "运行日志" // 修改按钮文字
            setOnClickListener {
                val intent = Intent(this@MainActivity, RunLogActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btn_main_author).setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://gitee.com/chunshengyue/yuan-assist")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 🔴 [检查更新] 按钮绑定新功能
        findViewById<Button>(R.id.btn_main_update).setOnClickListener {
            checkUpdate()
        }

        findViewById<Button>(R.id.btn_main_theme).apply {
            text = "功能测试" // 动态把按钮文字改成“功能测试”
            setOnClickListener {
                // 跳转到我们刚刚写好的测试沙盒模块
                val intent = Intent(this@MainActivity, TestActivity::class.java)
                startActivity(intent)
            }
        }
        // 🔴 初始化 DDL RecyclerView
        val rvDdl = findViewById<RecyclerView>(R.id.rv_ddl_list)
        ddlAdapter = DdlAdapter(ddlList)
        rvDdl.layoutManager = LinearLayoutManager(this)
        rvDdl.adapter = ddlAdapter
    }

    private fun loadDdlData() {
        // 强制指定使用 retrofit2 里的 Callback、Call 和 Response
        NetworkClient.api.getDdlList().enqueue(object : retrofit2.Callback<List<DdlItem>> {
            override fun onResponse(
                call: retrofit2.Call<List<DdlItem>>,
                response: retrofit2.Response<List<DdlItem>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    runOnUiThread {
                        ddlAdapter.updateData(data)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "DDL加载失败: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<List<DdlItem>>, t: Throwable) {
                t.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "DDL网络错误", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // 4. 🔴 新增：检查更新的完整逻辑
    private fun checkUpdate() {
        Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()

        val client = OkHttpClient()
        val request = Request.Builder().url(UPDATE_JSON_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "检查更新失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        try {
                            val updateInfo = Gson().fromJson(body, UpdateInfo::class.java)
                            val (localCode, _) = getAppVersion(this@MainActivity)

                            runOnUiThread {
                                if (updateInfo.versionCode > localCode) {
                                    showUpdateDialog(updateInfo)
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "已是最新版本",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "版本信息解析失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "获取版本信息失败: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    // 5. 🔴 新增：显示更新对话框的逻辑
    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("发现新版本 ${updateInfo.versionName}")
            .setMessage(updateInfo.releaseNotes)
            .setPositiveButton("立即更新") { dialog, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开浏览器下载", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("稍后提醒", null)
            .setCancelable(false)
            .show()
    }

    // 6. 🔴 新增：获取当前 App 版本信息的辅助方法
    private fun getAppVersion(context: Context): Pair<Long, String> {
        return try {
            val packageInfo: PackageInfo =
                context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong() // 🟢 强制转换为 Long
            }
            val versionName = packageInfo.versionName ?: "Unknown" // 🟢 加上非空保护
            Pair(versionCode, versionName)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(-1L, "Unknown") // 🟢 这里的 -1 也最好写成 -1L，更规范
        }
    }


    // --- (以下是原有的权限检查和启动服务逻辑，保持不变) ---
    private fun checkPermissionsAndStart(targetAction: String) {
        // 🔴 关键修复：第一步就立刻把用户的点击意图存起来，防止 onServiceConnected 读不到
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putString("pending_start_action", targetAction)
            .apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请开启悬浮窗权限", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
            }
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请开启无障碍服务: YuanAssist", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
            }
            return
        }

        try {
            val intent = Intent(this, YuanAssistService::class.java).apply { action = targetAction }
            startService(intent)
            val msg =
                if (targetAction == "ACTION_START_DAILY_WINDOW") "日常版已启动" else "悬浮窗已启动"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, YuanAssistService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) return true
        }
        return false
    }

    private fun checkAnnouncement() {
        // 开启子线程发起网络请求，避免阻塞主线程（UI卡顿）
        thread {
            try {
                // 🔴 直接指向你 Gitee 仓库里的 Raw 链接
                val url =
                    URL("https://gitee.com/chunshengyue/yuan-assist-data/raw/master/announcement.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000 // 3秒超时，避免网络差时卡太久
                connection.readTimeout = 3000

                if (connection.responseCode == 200) {
                    // 读取 Gitee 返回的 JSON 文本
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)

                    val serverVersion = jsonObject.getInt("version")
                    val title = jsonObject.getString("title")
                    val content = jsonObject.getString("content")

                    // 读取本地保存的已读版本号（默认是0）
                    val prefs: SharedPreferences = getSharedPreferences("AppConfig", MODE_PRIVATE)
                    val localVersion = prefs.getInt("read_announcement_version", 0)

                    // 如果云端的版本号大于本地版本号，说明有新公告！
                    if (serverVersion > localVersion) {
                        runOnUiThread {
                            showAnnouncementDialog(title, content, serverVersion, prefs)
                        }
                    }
                }
            } catch (e: Exception) {
                // 网络不通或 JSON 解析失败时静默处理，不打扰用户
                e.printStackTrace()
            }
        }
    }

    private fun showAnnouncementDialog(
        title: String,
        content: String,
        version: Int,
        prefs: SharedPreferences
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setCancelable(false) // 强制用户必须点击按钮关闭
            .setPositiveButton("我知道了") { dialog, _ ->
                // 🔴 用户点击后，把这个版本号存起来，下次启动就不会再弹了
                prefs.edit().putInt("read_announcement_version", version).apply()
                dialog.dismiss()
            }
            .show()
    }
}
