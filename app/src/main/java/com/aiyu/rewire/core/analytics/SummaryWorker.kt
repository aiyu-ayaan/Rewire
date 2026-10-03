package com.aiyu.rewire.core.analytics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aiyu.rewire.core.notifications.RewireNotifier
import com.aiyu.rewire.data.EventRepository
import com.aiyu.rewire.domain.analytics.DailySummary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Once a day, posts the recap of yesterday. Nothing happened, or the Daily summary preference is off
 * (the notifier checks it): nothing is posted. The notification id is fixed, so it never stacks.
 */
class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun events(): EventRepository
        fun notifier(): RewireNotifier
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val zone = ZoneId.systemDefault()
        DailySummary.forYesterday(deps.events().events.value, LocalDate.now(zone), zone)?.let { deps.notifier().dailySummary(it) }
        return Result.success()
    }

    companion object {
        private const val WORK = "rewire.daily.summary"
        private const val HOUR = 9

        /** Idempotent (KEEP), safe on every launch. First run lands at the next 09:00 local time. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(HOUR, 0)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val request = PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, first).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
