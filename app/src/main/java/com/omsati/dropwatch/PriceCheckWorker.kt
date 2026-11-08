package com.omsati.dropwatch

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class PriceCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) Log.d("PriceCheck", "PriceCheckWorker started background price check")
        val repo = Repo(applicationContext)
        val products = repo.dao.allProducts()
        if (BuildConfig.DEBUG) Log.d("PriceCheck", "Found ${products.size} total products to check")

        var hasNetworkFailure = false

        for (old in products) {
            try {
                val new = repo.check(old)
                if (new.status == "error") {
                    hasNetworkFailure = true
                }
                val o = old.price
                val n = new.price
                var notified = false
                if (n != null && n > 0) {
                    val dropped = o != null && n < o
                    val target = new.targetPrice
                    val hitTarget = target != null && n <= target && (o == null || o > target)
                    if (dropped || hitTarget) {
                        Notifier.priceDrop(applicationContext, new, o, hitTarget)
                        notified = true
                    }
                }
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "PriceCheck",
                        "Product #${old.id}: oldPrice=$o, newPrice=$n, targetPrice=${old.targetPrice}, currency=${new.currency}, notified=$notified"
                    )
                }
            } catch (e: Exception) {
                hasNetworkFailure = true
                if (BuildConfig.DEBUG) Log.e("PriceCheck", "Error checking product ${old.id}: ${e.message}", e)
            }
            delay(500)
        }

        if (hasNetworkFailure && runAttemptCount < 3) {
            return Result.retry()
        }

        if (BuildConfig.DEBUG) Log.d("PriceCheck", "PriceCheckWorker finished successfully")
        return Result.success()
    }
}
