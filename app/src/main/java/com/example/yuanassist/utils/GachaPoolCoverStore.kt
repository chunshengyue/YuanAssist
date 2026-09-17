package com.example.yuanassist.utils

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object GachaPoolCoverStore {
    private const val ROOT_DIR_NAME = "gacha_pool_covers"

    private val httpClient = OkHttpClient()

    fun localCoverFiles(context: Context, poolIds: Collection<String>): Map<String, File> =
        poolIds.mapNotNull { poolId ->
            localCoverFile(context, poolId)?.let { poolId to it }
        }.toMap()

    fun localCoverFile(context: Context, poolId: String): File? =
        File(rootDir(context), "$poolId.webp").takeIf { it.isFile && it.length() > 0L }

    suspend fun downloadMissing(
        context: Context,
        coverUrls: Map<String, String>,
        onStored: (poolId: String, file: File) -> Unit,
    ) = withContext(Dispatchers.IO) {
        coverUrls.forEach { (poolId, url) ->
            val existing = localCoverFile(context, poolId)
            val stored = existing ?: download(context, poolId, url)
            if (stored != null && existing == null) {
                withContext(Dispatchers.Main.immediate) {
                    onStored(poolId, stored)
                }
            }
        }
    }

    private fun rootDir(context: Context): File = File(context.filesDir, ROOT_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    private fun download(context: Context, poolId: String, url: String): File? {
        val target = File(rootDir(context), "$poolId.webp")
        val temp = File(target.parentFile, ".${target.name}.downloading")
        temp.delete()
        val copied = runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    false
                } else {
                    response.body?.byteStream()?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                }
            }
        }.getOrDefault(false)
        if (!copied || temp.length() <= 0L) {
            temp.delete()
            return null
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target.takeIf { it.isFile && it.length() > 0L }
    }
}
