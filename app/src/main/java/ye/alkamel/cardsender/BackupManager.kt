package ye.alkamel.cardsender
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import android.util.Base64
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
    /**
     * ينشئ نسخة احتياطية واحدة فقط ويستبدل النسخة السابقة.
     */
    fun createBackup(context: Context): String? {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return try {
            val name = "alkamel_backup" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".zip"
            val selectedTree = prefs.getString("backup_tree_uri", null)
            var savedLocation = ""

            if (!selectedTree.isNullOrBlank()) {
                val treeUri = Uri.parse(selectedTree)
                val tree = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw IllegalStateException("مسار النسخ الاحتياطي غير متاح؛ أعد تحديد المجلد")
                if (!tree.canWrite()) throw IllegalStateException("لا توجد صلاحية للكتابة في المجلد المحدد؛ أعد تحديد مسار النسخ الاحتياطي")
                val file = tree.createFile("application/zip", name)
                    ?: throw IllegalStateException("تعذر إنشاء ملف داخل المجلد المحدد")
                try {
                    val output = context.contentResolver.openOutputStream(file.uri, "w")
                        ?: throw IllegalStateException("تعذر فتح ملف النسخة للكتابة")
                    output.use { stream ->
                        ZipOutputStream(BufferedOutputStream(stream)).use { zip -> writeZip(context, zip) }
                    }
                    if (file.length() <= 0L) throw IllegalStateException("تم إنشاء ملف النسخة لكنه فارغ")
                    savedLocation = tree.name ?: selectedTree
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val relativePath = Environment.DIRECTORY_DOWNLOADS + "/الكامل أونلاين/نسخ احتياطية/"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolverInsert(context, collection, values)
                    ?: throw IllegalStateException("تعذر إنشاء ملف النسخة في مجلد التنزيلات")
                try {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("تعذر فتح ملف النسخة للكتابة")
                    output.use { stream ->
                        ZipOutputStream(BufferedOutputStream(stream)).use { zip -> writeZip(context, zip) }
                    }
                    val valuesDone = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    if (context.contentResolver.update(uri, valuesDone, null, null) <= 0) {
                        throw IllegalStateException("تعذر إنهاء حفظ النسخة في مجلد التنزيلات")
                    }
                    savedLocation = "التنزيلات/الكامل أونلاين/نسخ احتياطية"
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IllegalStateException("مساحة التنزيلات غير متاحة")
                val dir = File(base, "الكامل أونلاين/نسخ احتياطية")
                if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("تعذر إنشاء مجلد النسخ الاحتياطية")
                val target = File(dir, name)
                target.outputStream().use { output ->
                    ZipOutputStream(BufferedOutputStream(output)).use { zip -> writeZip(context, zip) }
                }
                if (!target.exists() || target.length() <= 0L) throw IllegalStateException("لم يتم حفظ ملف النسخة أو أنه فارغ")
                savedLocation = target.absolutePath
            }

            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            prefs.edit().putString("last_backup", stamp).putString("last_backup_path", savedLocation)
                .remove("last_backup_error").commit()
            name
        } catch (e: Exception) {
            prefs.edit().putString("last_backup_error", e.localizedMessage ?: e.javaClass.simpleName).commit()
            null
        }
    }
    private fun resolverInsert(context: Context, collection: Uri, values: ContentValues): Uri? =
        context.contentResolver.insert(collection, values)

    fun restoreBackup(context: Context, uri: Uri): Result {
        return try {
            val tempRoot = File(context.cacheDir, "restore_" + System.currentTimeMillis()).apply {
                deleteRecursively()
                mkdirs()
            }
            val tempCards = File(tempRoot, "cards").apply { mkdirs() }
            var hasCardFile = false
            var hasSales = false
            var hasOperations = false
            var hasAppSettings = false
            val thresholds = mutableMapOf<Int, Int>()
            val alternateMappings = mutableMapOf<String, String>()
            var messageTemplate: String? = null

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
                            name.startsWith("cards/") && (name.endsWith(".txt") || name.endsWith(".csv")) -> {
                                val relative = name.removePrefix("cards/")
                                val parts = relative.split("/")
                                val sim = if (parts.size >= 2 && (parts[0] == "sim1" || parts[0] == "sim2")) parts[0] else "sim1"
                                val fileName = if (parts.size >= 2) parts.last() else parts[0]
                                val targetDir = File(tempCards, sim).apply { mkdirs() }
                                if (fileName.endsWith(".txt")) {
                                    val amount = fileName.removeSuffix(".txt").toIntOrNull()
                                        ?: throw IllegalArgumentException("فئة كرت غير صالحة")
                                    if (amount <= 0) throw IllegalArgumentException("فئة كرت غير صالحة")
                                    val out = File(targetDir, "$amount.txt")
                                    BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                    hasCardFile = true
                                } else {
                                    val out = File(targetDir, "sales.csv")
                                    BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                    hasSales = true
                                }
                            }
                            name == "sales.csv" -> {
                                val out = File(tempCards, "sim1/sales.csv")
                                out.parentFile?.mkdirs()
                                BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                hasSales = true
                            }
                            name == "operations.log" -> {
                                val out = File(tempRoot, "operations.log")
                                BufferedOutputStream(out.outputStream()).use { zip.copyTo(it) }
                                hasOperations = true
                            }
                            name == "app_settings.txt" -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8)
                                text.lineSequence().forEach { line ->
                                    when {
                                        line.startsWith("template_b64=") -> {
                                            runCatching {
                                                messageTemplate = String(
                                                    Base64.decode(line.removePrefix("template_b64="), Base64.DEFAULT),
                                                    Charsets.UTF_8
                                                )
                                            }
                                        }
                                        line.startsWith("map_b64=") -> {
                                            val parts = line.removePrefix("map_b64=").split("|", limit = 2)
                                            if (parts.size == 2) {
                                                runCatching {
                                                    val a = String(Base64.decode(parts[0], Base64.DEFAULT), Charsets.UTF_8)
                                                    val p = String(Base64.decode(parts[1], Base64.DEFAULT), Charsets.UTF_8)
                                                    if (a.isNotBlank() && p.isNotBlank()) alternateMappings[a] = p
                                                }
                                            }
                                        }
                                    }
                                }
                                hasAppSettings = true
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

            if (!hasCardFile && !hasSales && !hasOperations && !hasAppSettings) {
                throw IllegalArgumentException("ملف النسخة الاحتياطية لا يحتوي على بيانات صالحة")
            }

            val cardFiles = tempCards.walkTopDown().filter { it.isFile && it.extension == "txt" }.toList()
            val categories = cardFiles.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                .filter { it > 0 }.distinct().sorted()
            if (categories.isEmpty()) {
                throw IllegalArgumentException("لا توجد فئات كروت في النسخة الاحتياطية")
            }

            val liveCards = File(context.filesDir, "cards").apply { mkdirs() }
            liveCards.listFiles()?.forEach { it.deleteRecursively() }
            tempCards.listFiles()?.filter { it.isDirectory }?.forEach { source ->
                source.copyRecursively(File(liveCards, source.name), overwrite = true)
            }

            if (hasSales) {
                for (sim in listOf("sim1", "sim2")) {
                    val source = File(tempCards, "$sim/sales.csv")
                    if (source.exists()) source.copyTo(File(liveCards, "$sim/sales.csv"), overwrite = true)
                }
            }
            if (hasOperations) {
                File(tempRoot, "operations.log").copyTo(OperationLog.backupFile(context), overwrite = true)
            }

            if (hasAppSettings) {
                if (messageTemplate != null) {
                    context.getSharedPreferences("message_settings", Context.MODE_PRIVATE)
                        .edit().putString("template", messageTemplate).apply()
                }
                alternateMappings.forEach { (alternate, phone) ->
                    ContactMap.setPhone(context, alternate, phone)
                }
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
                val parent = file.parentFile?.name
                val relative = when {
                    file.name == "operations.log" -> "operations.log"
                    parent == "sim1" || parent == "sim2" -> "cards/$parent/${file.name}"
                    else -> "cards/${file.name}"
                }
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.putNextEntry(ZipEntry("settings.txt"))
        val settings = buildString {
            append("app=الكامل أونلاين\n")
            append("version=1.10.0\n")
            CardStore.categories(context).forEach { amount ->
                append("stock_alert_threshold_$amount=${StockNotification.getThreshold(context, amount)}\n")
            }
        }
        zip.write(settings.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("app_settings.txt"))
        val prefs = context.getSharedPreferences("message_settings", Context.MODE_PRIVATE)
        val template = prefs.getString("template", "") ?: ""
        val appSettings = buildString {
            append("template_b64=")
            append(Base64.encodeToString(template.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            append("\n")
            ContactMap.all(context).forEach { (alternate, phone) ->
                append("map_b64=")
                append(Base64.encodeToString(alternate.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                append("|")
                append(Base64.encodeToString(phone.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                append("\n")
            }
        }
        zip.write(appSettings.toByteArray(Charsets.UTF_8))
        zip.closeEntry()    }
}
