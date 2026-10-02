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
    private const val LOW_STOCK = 20

    fun notifyIfLow(context: Context, amount: Int, remaining: Int) {
        if (remaining != LOW_STOCK) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "تنبيهات المخزون", NotificationManager.IMPORTANCE_HIGH
            ))
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تنبيه مخزون الكروت")
            .setContentText("مخزون كرت $amount أصبح 20 كرت فقط")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(amount, n)
    }
}
