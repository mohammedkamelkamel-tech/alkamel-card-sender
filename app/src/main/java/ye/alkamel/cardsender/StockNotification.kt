package ye.alkamel.cardsender

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object StockNotification {
    private const val CHANNEL_ID = "low_stock"
    private const val PREFS = "stock_alert_settings"
    private const val DEFAULT_THRESHOLD = 20
    private const val MAX_THRESHOLD = 9999

    fun getThreshold(context: Context, amount: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("threshold_$amount", DEFAULT_THRESHOLD)
            .coerceIn(0, MAX_THRESHOLD)

    fun setThreshold(context: Context, amount: Int, threshold: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("threshold_$amount", threshold.coerceIn(0, MAX_THRESHOLD))
            .apply()
    }

    fun notifyIfLow(context: Context, amount: Int, remaining: Int) {
        val threshold = getThreshold(context, amount)
        if (threshold <= 0 || remaining > threshold) return

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "تنبيهات نقص المخزون", NotificationManager.IMPORTANCE_HIGH
            ))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تنبيه نقص المخزون")
            .setContentText("مخزون كروت $amount ريال أصبح $remaining كرت فقط")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
