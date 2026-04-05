package plus.maa.backend.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import plus.maa.backend.service.level.LevelsV2SyncService
import java.util.concurrent.atomic.AtomicBoolean

@Component
class LevelsV2SyncTask(
    private val syncService: LevelsV2SyncService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val flag = AtomicBoolean(false)

    @Scheduled(cron = "0 0/10 * * * ?", zone = "Asia/Shanghai")
    fun scheduledSync() {
        if (flag.compareAndSet(false, true)) {
            scope.launch {
                try { syncService.syncOnce() } finally { flag.set(false) }
            }
        }
    }
}
