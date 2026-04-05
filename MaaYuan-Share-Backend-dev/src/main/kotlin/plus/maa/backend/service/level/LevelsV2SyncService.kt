package plus.maa.backend.service.level

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import plus.maa.backend.config.external.MaaCopilotProperties
import plus.maa.backend.repository.GithubRepository
import plus.maa.backend.repository.RedisCache
import java.nio.file.Files
import java.nio.file.Path

@Service
class LevelsV2SyncService(
    private val properties: MaaCopilotProperties,
    private val redisCache: RedisCache,
    private val cacheManager: CacheManager,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger { }
    private val webClient: WebClient = WebClient.builder().build()

    suspend fun syncOnce(): Boolean {
        val cfg = properties.levels
        if (!cfg.enableGithub) {
            log.info { "levels v2 sync disabled; skip" }
            return false
        }
        val dir = cfg.jsonDir.trim('/',' ').ifEmpty { "" }
        val (owner, repo, branch) = parseRepoAndBranch(cfg.repoAndBranch)
        val contents = fetchContents(owner, repo, branch, dir, cfg.token)
        val target = contents.firstOrNull { it.isFile && it.name == cfg.jsonFile }
            ?: run {
                log.warn { "levels v2 json not found in contents: dir=${cfg.jsonDir}, file=${cfg.jsonFile}" }
                return false
            }
        val shaKey = "levels:v2:sha"
        val oldSha = redisCache.getCache(shaKey, String::class.java)
        if (oldSha == target.sha) {
            log.info { "levels v2 already up-to-date (sha=$oldSha)" }
            return false
        }
        val downloadUrl = target.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            log.warn { "downloadUrl missing for ${target.name}" }
            return false
        }
        log.info { "downloading levels v2 from $downloadUrl (sha=${target.sha})" }
        val body = webClient
            .get()
            .uri(downloadUrl)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
        if (body.isNullOrBlank()) {
            log.warn { "downloaded empty body; abort" }
            return false
        }
        val path = Path.of(cfg.localCache)
        Files.createDirectories(path.parent)
        Files.writeString(path, body)
        // 更新缓存标记并失效 Caffeine 缓存
        redisCache.setData(shaKey, target.sha)
        cacheManager.getCache("arkLevelInfosV2")?.clear()
        log.info { "levels v2 updated -> ${cfg.localCache}; cache cleared" }
        return true
    }

    private fun parseRepoAndBranch(repoAndBranch: String): Triple<String, String, String> {
        val parts = repoAndBranch.split('/')
        require(parts.size >= 2) { "levels.repoAndBranch 格式错误，应为 owner/repo[/branch]" }
        val owner = parts[0]
        val repo = parts[1]
        val branch = parts.getOrNull(2) ?: "main"
        return Triple(owner, repo, branch)
    }

    private fun fetchContents(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        token: String,
    ): List<plus.maa.backend.repository.entity.github.GithubContent> {
        // GitHub contents API 路径中的 "/" 不能整体编码；只对段进行编码再拼接
        val normalized = path.trim('/')
        val encodedPath = if (normalized.isBlank()) "" else normalized.split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)
        }
        val url = if (encodedPath.isBlank())
            "https://api.github.com/repos/$owner/$repo/contents?ref=$branch"
        else
            "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=$branch"

        val text = webClient
            .get()
            .uri(url)
            .headers { h ->
                h.add("Accept", "application/vnd.github+json")
                h.add("X-GitHub-Api-Version", "2022-11-28")
                if (token.isNotBlank()) h.add("Authorization", "Bearer $token")
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: "[]"

        val listType = object : TypeReference<List<plus.maa.backend.repository.entity.github.GithubContent>>() {}
        return objectMapper.readValue(text, listType)
    }
}
