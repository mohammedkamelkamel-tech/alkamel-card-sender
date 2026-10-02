package ye.alkamel.cardsender

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Server-backed license manager. A license is claimed atomically by the server against this device's Android ID. */
object LicenseManager {
    private const val PREFS = "license"
    private const val KEY_TYPE = "type"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_TOKEN = "token"

    // Replace this after deploying server/cloudflare-worker/worker.js.
    private const val SERVER_URL = "https://REPLACE-WITH-YOUR-LICENSE-SERVER.workers.dev"

    private val executor = Executors.newSingleThreadExecutor()

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_TYPE, null)) {
            "LIFE" -> true
            "DAY" -> prefs.getLong(KEY_EXPIRES_AT, 0L) > System.currentTimeMillis()
            else -> false
        }
    }

    fun activate(context: Context, rawCode: String, callback: (Result) -> Unit) {
        val code = rawCode.trim().uppercase()
        if (!Regex("[DL]1-[0-9A-F]{12}-[0-9A-F]{12}").matches(code)) {
            callback(Result.Error("صيغة كود التفعيل غير صحيحة"))
            return
        }

        executor.execute {
            try {
                val url = URL("$SERVER_URL/activate")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown-device"
                val body = JSONObject()
                    .put("code", code)
                    .put("deviceId", deviceId)
                    .put("appVersion", "1.5.0")
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()

                if (status !in 200..299) {
                    val message = runCatching { JSONObject(response).optString("message") }.getOrDefault("")
                    callback(Result.Error(message.ifBlank { "تعذر تفعيل الكود" }))
                    return@execute
                }

                val json = JSONObject(response)
                val type = json.optString("type")
                val expiresAt = json.optLong("expiresAt", 0L)
                if (type != "DAY" && type != "LIFE") {
                    callback(Result.Error("استجابة الخادم غير صالحة"))
                    return@execute
                }

                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TYPE, type)
                    .putString(KEY_TOKEN, code.substringAfter("-").substringBefore("-"))
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply()

                callback(Result.Success(type))
            } catch (_: Exception) {
                callback(Result.Error("تعذر الاتصال بخادم التفعيل. تحقق من الإنترنت وحاول مرة أخرى."))
            }
        }
    }

    fun remainingText(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_TYPE, null)) {
            "LIFE" -> "ترخيص مدى الحياة"
            "DAY" -> {
                val left = (prefs.getLong(KEY_EXPIRES_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
                val hours = left / 3_600_000L
                val minutes = (left / 60_000L) % 60L
                "متبقي من الترخيص: $hours ساعة و$minutes دقيقة"
            }
            else -> "غير مفعل"
        }
    }

    sealed class Result {
        data class Success(val type: String) : Result()
        data class Error(val message: String) : Result()
    }
}
