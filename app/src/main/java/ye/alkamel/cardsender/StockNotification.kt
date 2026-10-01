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
    private const val CHANNEL_NAME = "تنبيهات المخزون"
    private const val LOW_STOCK = 20

    fun notifyIfLow(context: Context, amount: Int, remaining: Int) {
        // الإشعار يظهر عند الوصول إلى 20 كرت بالضبط،
        // وليس مع كل عملية لاحقة تحت 20.
        if (remaining != LOW_STOCK) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه عند انخفاض مخزون أحد أنواع الكروت إلى 20 كرت"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("تنبيه مخزون الكروت")
            .setContentText("مخزون كرت $amount أصبح 20 كرت فقط")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("مخزون كرت $amount أصبح 20 كرت فقط. يرجى رفع/إضافة كروت جديدة.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(amount, notification)
    }
}
