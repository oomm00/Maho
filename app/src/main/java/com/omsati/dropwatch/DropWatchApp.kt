package com.omsati.dropwatch

import android.app.Application
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class DropWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        schedulePeriodicCheck(this, update = false)
    }

    companion object {
        fun schedulePeriodicCheck(ctx: Context, update: Boolean = false) {
            val prefs = ctx.getSharedPreferences("dropwatch_prefs", Context.MODE_PRIVATE)
            val hours = prefs.getInt("check_interval_hours", 6).toLong().coerceIn(1L, 24L)
            val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            val policy = if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("price-check", policy, req)
        }

        fun runCheckNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<PriceCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("price-check-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
