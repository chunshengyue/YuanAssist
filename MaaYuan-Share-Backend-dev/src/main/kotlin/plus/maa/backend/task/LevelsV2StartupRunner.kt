package plus.maa.backend.task

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import plus.maa.backend.config.external.MaaCopilotProperties
import plus.maa.backend.service.level.LevelsV2SyncService

@Component
class LevelsV2StartupRunner(
    private val properties: MaaCopilotProperties,
    private val syncService: LevelsV2SyncService,
) : ApplicationRunner {
    private val log = KotlinLogging.logger { }

    override fun run(args: ApplicationArguments?) {
        if (!properties.levels.enableGithub) {
            log.info { "levels v2 github sync disabled on startup; skip" }
            return
        }
        try {
            runBlocking { syncService.syncOnce() }
        } catch (e: Exception) {
            log.error(e) { "levels v2 startup sync failed" }
        }
    }
}

