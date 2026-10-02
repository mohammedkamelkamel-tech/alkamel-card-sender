package ye.alkamel.cardsender

import android.content.Context
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline license manager.
 *
 * Important: without a server there is no shared state between phones.
 * A code is therefore validated offline and locked locally to the first
 * Android device that activates it. This prevents reuse on the same
 * installation/device, but cannot prove to another phone that the code
 * was already used.
 */
object LicenseManager {
    private const val PREFS = "license"
    private const val KEY_TYPE = "type"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_CODE = "code"
    private const val KEY_DEVICE = "device"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    // Kept inside the APK for fully offline validation.
    // Offline activation cannot provide server-level anti-sharing security.
    private const val SECRET = "AlKamel-Offline-License-2026-9F7C2A"

    fun isActivated(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = p.getString(KEY_TYPE, null) ?: return false
        val device = deviceId(context)
        if (p.getString(KEY_DEVICE, null) != device) return false
        return when (type) {
            "LIFE" -> true
            "DAY" -> p.getLong(KEY_EXPIRES_AT, 0L) > System.currentTimeMillis()
            else -> false
        }
    }

    fun activate(context: Context, rawCode: String, callback: (Result) -> Unit) {
        val code = rawCode.trim().uppercase()
        val parts = code.split("-")
        if (parts.size != 3 ||
            !parts[1].matches(Regex("[0-9A-F]{12}")) ||
            !parts[2].matches(Regex("[0-9A-F]{12}"))) {
            callback(Result.Error("كود التفعيل غير صحيح"))
            return
        }

        val type = when (parts[0]) {
            "D1" -> "DAY"
            "L1" -> "LIFE"
            else -> {
                callback(Result.Error("كود التفعيل غير صحيح"))
                return
            }
        }

        if (!parts[2].equals(sign(type, parts[1]), ignoreCase = true)) {
            callback(Result.Error("كود التفعيل غير صحيح"))
            return
        }

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val device = deviceId(context)
        val savedCode = p.getString(KEY_CODE, null)
        val savedDevice = p.getString(KEY_DEVICE, null)

        if (savedCode != null && savedCode != code) {
            callback(Result.Error("يوجد كود تفعيل آخر مرتبط بهذا الجهاز"))
            return
        }
        if (savedCode == code && savedDevice != device) {
            callback(Result.Error("هذا الكود مرتبط بجهاز آخر"))
            return
        }

        val now = System.currentTimeMillis()
        val expires = if (type == "DAY") {
            if (savedCode == code) p.getLong(KEY_EXPIRES_AT, 0L)
            else now + DAY_MS
        } else 0L

        if (type == "DAY" && expires <= now) {
            callback(Result.Error("انتهت صلاحية كود اليوم"))
            return
        }

        p.edit()
            .putString(KEY_TYPE, type)
            .putString(KEY_CODE, code)
            .putString(KEY_DEVICE, device)
            .putLong(KEY_EXPIRES_AT, expires)
            .apply()

        callback(Result.Success(type))
    }

    fun remainingText(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (p.getString(KEY_TYPE, null)) {
            "LIFE" -> "ترخيص مدى الحياة"
            "DAY" -> {
                val left = (p.getLong(KEY_EXPIRES_AT, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
                val hours = left / 3_600_000L
                val minutes = (left / 60_000L) % 60L
                "متبقي من الترخيص: $hours ساعة و$minutes دقيقة"
            }
            else -> "غير مفعل"
        }
    }

    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun sign(type: String, token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val payload = "$type|$token"
        val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }.take(12)
    }

    sealed class Result {
        data class Success(val type: String) : Result()
        data class Error(val message: String) : Result()
    }
}
