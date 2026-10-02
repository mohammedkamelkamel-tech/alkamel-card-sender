package ye.alkamel.cardsender
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupManager {
    fun createBackup(context: Context): String? {
        return try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val name = "alkamel_backup$stamp.zip"
            val resolver = context.contentResolver
            val uri = if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/الكامل أونلاين/نسخ احتياطية")
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } else null
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    ZipOutputStream(output).use { zip -> writeZip(context, zip) }
                } ?: return null
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "نسخ احتياطية").apply { mkdirs() }
                File(dir, name).outputStream().use { output -> ZipOutputStream(output).use { zip -> writeZip(context, zip) } }
            }
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("last_backup", stamp).apply()
            name
        } catch (_: Exception) { null }
    }
    private fun writeZip(context: Context, zip: ZipOutputStream) {
        CardStore.backupFiles(context).forEach { file ->
            if (file.exists()) {
                val relative = if (file.name == "sales.csv") "sales.csv" else "cards/${file.name}"
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.putNextEntry(ZipEntry("settings.txt"))
        val settings = buildString {
            append("app=الكامل أونلاين\n")
            append("version=1.1.0\n")
            CardStore.categories(context).forEach { amount ->
                append("stock_alert_threshold_$amount=${StockNotification.getThreshold(context, amount)}\n")
            }
        }
        zip.write(settings.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
