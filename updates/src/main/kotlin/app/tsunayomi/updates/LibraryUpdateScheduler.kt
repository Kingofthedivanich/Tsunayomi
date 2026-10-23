package app.tsunayomi.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.tsunayomi.core.common.ApplicationScope
import app.tsunayomi.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the periodic library update in line with the settings, and runs one on demand. */
@Singleton
class LibraryUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesDataSource,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Call once at app start: (re)schedules whenever the interval or the Wi-Fi setting changes. */
    fun start() {
        preferences.settings
            .map { it.libraryUpdateIntervalHours to it.libraryUpdateWifiOnly }
            .distinctUntilChanged()
            .onEach { (hours, wifiOnly) -> schedule(hours, wifiOnly) }
            .launchIn(scope)
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** True while a scheduled or manual update is running. */
    val isRunning: Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(NOW_WORK),
    ) { periodic, now -> (periodic + now).any { it.state == WorkInfo.State.RUNNING } }

    private fun schedule(hours: Int, wifiOnly: Boolean) {
        if (hours <= 0) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            // Don't run right after scheduling; the first run comes after one interval.
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val PERIODIC_WORK = "library-update-periodic"
        const val NOW_WORK = "library-update-now"
    }
}
