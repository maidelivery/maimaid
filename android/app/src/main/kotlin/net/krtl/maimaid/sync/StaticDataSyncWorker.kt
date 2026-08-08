package net.krtl.maimaid.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import net.krtl.maimaid.MaimaidApplication
import java.util.concurrent.TimeUnit

class StaticDataSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MaimaidApplication).container
        val config = container.staticDataRepository.observeSyncConfig().first()
        val preferences = container.preferencesRepository.preferences.first()
        if (config.backgroundSyncInterval <= 0) return Result.success()
        return runCatching {
            container.syncStaticDataUseCase(preferences.syncOptions)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}

object StaticSyncScheduler {
    private const val UNIQUE_NAME = "static-data-sync"

    fun schedule(context: Context, hours: Int) {
        val workManager = WorkManager.getInstance(context)
        if (hours <= 0) {
            workManager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<StaticDataSyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
