package ye.alkamel.cardsender
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

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

    fun restoreBackup(context: Context, uri: Uri): Result {
        return try {
            val tempRoot = File(context.cacheDir, "restore_" + System.currentTimeMillis()).apply {
                deleteRecursively()
                mkdirs()
            }
            val tempCards = File(tempRoot, "cards").apply { mkdirs() }
            var hasCardFile = false
            var hasSales = false
            val thresholds = mutableMapOf<Int, Int>()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }

                        val name = entry.name.replace('\\', '/')
                        if (name.contains("..") || name.startsWith("/") || name.contains(":")) {
                            throw IllegalArgumentException("ملف النسخة الاحتياطية غير صالح")
                        }

                        when {
                            name.startsWith("cards/") && name.endsWith(".txt") -> {
                                val fileName = name.removePrefix("cards/")
                                val amount = fileName.removeSuffix(".txt").toIntOrNull()
                                    ?: throw IllegalArgumentException("فئة كرت غير صالحة")
                                if (amount <= 0) throw IllegalArgumentException("فئة كرت غير صالحة")
                                val out = File(tempCards, "$amount.txt")
                                BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                hasCardFile = true
                            }
                            name == "sales.csv" -> {
                                val out = File(tempRoot, "sales.csv")
                                BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                hasSales = true
                            }
                            name == "settings.txt" -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                text.lineSequence().forEach { line ->
                                    val prefix = "stock_alert_threshold_"
                                    if (line.startsWith(prefix)) {
                                        val p = line.substring(prefix.length).split("=", limit = 2)
                                        val amount = p.getOrNull(0)?.toIntOrNull()
                                        val value = p.getOrNull(1)?.toIntOrNull()
                                        if (amount != null && value != null && amount > 0 && value >= 0) {
                                            thresholds[amount] = value
                                        }
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: throw IllegalArgumentException("تعذر قراءة ملف النسخة الاحتياطية")

            if (!hasCardFile && !hasSales) {
                throw IllegalArgumentException("ملف النسخة الاحتياطية لا يحتوي على بيانات صالحة")
            }

            val cardFiles = tempCards.listFiles()?.filter { it.isFile && it.extension == "txt" } ?: emptyList()
            val categories = cardFiles.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                .filter { it > 0 }.distinct().sorted()
            if (categories.isEmpty()) {
                throw IllegalArgumentException("لا توجد فئات كروت في النسخة الاحتياطية")
            }

            val liveCards = File(context.filesDir, "cards").apply { mkdirs() }
            liveCards.listFiles()?.filter { it.isFile && it.extension == "txt" }?.forEach { it.delete() }
            cardFiles.forEach { it.copyTo(File(liveCards, it.name), overwrite = true) }

            if (hasSales) {
                File(tempRoot, "sales.csv").copyTo(File(context.filesDir, "sales.csv"), overwrite = true)
            } else {
                File(context.filesDir, "sales.csv").writeText("time,amount,phone,card\n")
            }

            context.getSharedPreferences("card_categories", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("categories", categories.map { it.toString() }.toSet())
                .apply()

            categories.forEach { amount ->
                StockNotification.setThreshold(
                    context,
                    amount,
                    thresholds[amount] ?: StockNotification.getThreshold(context, amount)
                )
            }

            CardStore.initializeFiles(context)
            tempRoot.deleteRecursively()
            Result.Success(categories.size)
        } catch (e: Exception) {
            Result.Error(e.message ?: "تعذر استعادة النسخة الاحتياطية")
        }
    }

    sealed class Result {
        data class Success(val categories: Int) : Result()
        data class Error(val message: String) : Result()
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
