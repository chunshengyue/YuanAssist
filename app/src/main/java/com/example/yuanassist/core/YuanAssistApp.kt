package com.example.yuanassist.core

import android.app.Application
import android.os.Environment
import android.util.Log
import cn.bmob.v3.Bmob

class YuanAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        cleanupStaleUpdateApk()
        try {
            Bmob.initialize(this, "ce7c10b39d790e195d0f3e11bf1945ee")
        } catch (e: Exception) {
            Log.e("YuanAssistApp", "Bmob 初始化失败: ${e.message}", e)
        }
    }

    private fun cleanupStaleUpdateApk() {
        val updateDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve("update")
            ?: return
        if (!updateDir.exists() || !updateDir.isDirectory) return

        updateDir.listFiles()?.forEach { file ->
            runCatching {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }.onFailure { error ->
                Log.w("YuanAssistApp", "更新缓存清理失败: ${file.absolutePath}", error)
            }
        }
    }
}
