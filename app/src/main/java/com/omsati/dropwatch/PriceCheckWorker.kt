package com.omsati.dropwatch

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class PriceCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Log.d("PriceCheck", "PriceCheckWorker started background price check")
        val repo = Repo(applicationContext)
        val products = repo.dao.allProducts()
        Log.d("PriceCheck", "Found ${products.size} total products to check")

        for (old in products) {
            val new = repo.check(old)
            val o = old.price
            val n = new.price
            var notified = false
            if (n != null) {
                val dropped = o != null && n < o
                val target = new.targetPrice
                val hitTarget = target != null && n <= target && (o == null || o > target)
                if (dropped || hitTarget) {
                    Notifier.priceDrop(applicationContext, new, o, hitTarget)
                    notified = true
                }
            }
            Log.d(
                "PriceCheck",
                "Product #${old.id} [${old.title}]: oldPrice=$o, newPrice=$n, targetPrice=${old.targetPrice}, currency=${new.currency}, notified=$notified"
            )
            delay(700) // be polite to stores
        }

        Log.d("PriceCheck", "PriceCheckWorker finished successfully")
        return Result.success()
    }
}
