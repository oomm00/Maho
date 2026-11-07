package com.omsati.dropwatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.math.roundToInt

object Notifier {
    private const val CHANNEL = "price_drops"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Price drops", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a saved product gets cheaper"
            }
        )
    }

    fun priceDrop(ctx: Context, p: Product, old: Double?, hitTarget: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("productId", p.id)
        }
        val pi = PendingIntent.getActivity(
            ctx, p.id.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nowStr = money(p.price, p.currency)
        val oldStr = if (old != null) money(old, p.currency) else null
        val pctSaved = if (old != null && old > 0 && p.price != null && p.price < old) {
            val pct = (((old - p.price) / old) * 100).roundToInt()
            " (-$pct%)"
        } else ""

        val text = when {
            hitTarget && oldStr != null -> "Hit target price: $nowStr$pctSaved (was $oldStr)"
            hitTarget -> "Hit target price: $nowStr"
            oldStr != null -> "$oldStr → $nowStr$pctSaved"
            else -> nowStr
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Price drop: ${p.title.take(60)}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${p.domain}"))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(p.id.toInt(), n)
    }

    fun sendTestNotification(ctx: Context) {
        ensureChannel(ctx)
        val testProduct = Product(
            id = 999999L,
            url = "https://example.com/demo",
            domain = "amazon.in",
            title = "Noise ColorFit Pulse Smart Watch",
            price = 1499.0,
            previousPrice = 1999.0,
            currency = "INR",
            targetPrice = 1500.0,
            status = "ok",
        )
        priceDrop(ctx, testProduct, old = 1999.0, hitTarget = true)
    }
}
