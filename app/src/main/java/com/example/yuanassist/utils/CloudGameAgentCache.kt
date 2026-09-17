package com.example.yuanassist.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.network.CloudGameAgent
import com.example.yuanassist.network.SupabaseRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.URL

object CloudGameAgentCache {
    private const val ROOT_DIR_NAME = "cloud_game_agents"
    private const val AGENTS_FILE_NAME = "agents.json"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingNames = mutableSetOf<String>()
    private var cachedAgents: MutableMap<String, CloudGameAgent>? = null

    fun ensureAvailable(
        context: Context,
        names: Collection<String>,
        onUpdated: () -> Unit,
    ) {
        val normalizedNames = names.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val missingNames = normalizedNames.filter { name ->
            AgentRepository.AGENT_MAP[name] == null && needsRefresh(context, name)
        }
        val requestNames = synchronized(pendingNames) {
            missingNames.filter { pendingNames.add(it) }
        }
        if (requestNames.isEmpty()) return

        SupabaseRepository.getCloudGameAgents(
            names = requestNames,
            onSuccess = { agents ->
                Thread {
                    val stored = store(context, agents)
                    synchronized(pendingNames) { pendingNames.removeAll(requestNames.toSet()) }
                    if (stored) mainHandler.post(onUpdated)
                }.start()
            },
            onError = {
                synchronized(pendingNames) { pendingNames.removeAll(requestNames.toSet()) }
            },
        )
    }

    fun resolveTalentLabel(context: Context, agentName: String, talentId: Int): String? {
        val disc = agents(context)[agentName]?.fateDiscs
            ?.firstOrNull { it.position == talentId }
            ?: return null
        val name = disc.shortName.ifBlank { disc.name }.trim()
        if (name.isBlank()) return null
        return when (disc.rarity.trim()) {
            "金", "橙", "orange", "gold" -> "橙$name"
            "紫", "purple" -> "紫$name"
            else -> name
        }
    }

    fun avatarDrawable(context: Context, agentName: String): Drawable? {
        return avatarBitmap(context, agentName)?.let { bitmap ->
            BitmapDrawable(context.resources, bitmap)
        }
    }

    fun avatarBitmap(context: Context, agentName: String): Bitmap? {
        val file = avatarFile(context, agentName)
        if (!file.isFile || file.length() <= 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun needsRefresh(context: Context, name: String): Boolean {
        val agent = agents(context)[name] ?: return true
        return agent.avatarUrl.isNotBlank() && !avatarFile(context, name).isFile
    }

    private fun store(context: Context, agents: List<CloudGameAgent>): Boolean {
        if (agents.isEmpty()) return false
        val cache = agents(context)
        var changed = false
        agents.filter { it.name.isNotBlank() }.forEach { agent ->
            if (cache[agent.name] != agent) changed = true
            cache[agent.name] = agent
            val hadAvatar = avatarFile(context, agent.name).isFile
            downloadAvatar(context, agent)
            if (!hadAvatar && avatarFile(context, agent.name).isFile) changed = true
        }
        agentsFile(context).writeText(gson.toJson(cache.values.toList()), Charsets.UTF_8)
        return changed
    }

    private fun downloadAvatar(context: Context, agent: CloudGameAgent) {
        if (agent.avatarUrl.isBlank()) return
        val target = avatarFile(context, agent.name)
        if (target.isFile && target.length() > 0L) return
        runCatching {
            URL(agent.avatarUrl).openStream().use { input ->
                val temp = File(target.parentFile, "${target.name}.tmp")
                temp.outputStream().use { output -> input.copyTo(output) }
                if (temp.length() > 0L) {
                    temp.renameTo(target)
                } else {
                    temp.delete()
                }
            }
        }
    }

    private fun agents(context: Context): MutableMap<String, CloudGameAgent> {
        cachedAgents?.let { return it }
        val loaded = runCatching {
            gson.fromJson<List<CloudGameAgent>>(
                agentsFile(context).takeIf(File::exists)?.readText(Charsets.UTF_8).orEmpty(),
                object : TypeToken<List<CloudGameAgent>>() {}.type,
            ).orEmpty().associateBy { it.name }.toMutableMap()
        }.getOrDefault(mutableMapOf())
        cachedAgents = loaded
        return loaded
    }

    private fun rootDir(context: Context): File =
        File(context.filesDir, ROOT_DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun agentsFile(context: Context): File = File(rootDir(context), AGENTS_FILE_NAME)

    private fun avatarFile(context: Context, name: String): File {
        val encoded = Base64.encodeToString(
            name.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return File(rootDir(context), "$encoded.webp")
    }
}
