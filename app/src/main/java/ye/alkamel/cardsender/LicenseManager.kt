package ye.alkamel.cardsender

import android.content.Context
import android.os.SystemClock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseManager {
    private const val PREFS = "license"
    private const val KEY_TYPE = "type"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val SECRET = "AlKamel-License-2026-Secret-7f4b9a"

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_TYPE, null)) {
            "LIFE" -> true
            "DAY" -> {
                val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
                activatedAt > 0L && SystemClock.elapsedRealtime() - activatedAt < DAY_MS
            }
            else -> false
        }
    }

    fun activate(context: Context, rawCode: String): String? {
        val code = rawCode.trim().uppercase()
        val parts = code.split("-")
        if (parts.size != 3) return null
        val type = when (parts[0]) { "D1" -> "DAY"; "L1" -> "LIFE"; else -> return null }
        val token = parts[1]
        val signature = parts[2]
        if (!token.matches(Regex("[0-9A-F]{12}"))) return null
        if (!signature.equals(sign(type, token), ignoreCase = true)) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        prefs.putString(KEY_TYPE, type)
        if (type == "DAY") prefs.putLong(KEY_ACTIVATED_AT, SystemClock.elapsedRealtime()) else prefs.remove(KEY_ACTIVATED_AT)
        prefs.apply()
        return type
    }

    fun remainingText(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_TYPE, null)) {
            "LIFE" -> "ترخيص مدى الحياة"
            "DAY" -> {
                val activatedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)
                val left = (DAY_MS - (SystemClock.elapsedRealtime() - activatedAt)).coerceAtLeast(0L)
                val hours = left / (60L * 60L * 1000L)
                val minutes = (left / (60L * 1000L)) % 60L
                "متبقي من الترخيص: $hours ساعة و$minutes دقيقة"
            }
            else -> "غير مفعل"
        }
    }

    private fun sign(type: String, token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val payload = if (type == "DAY") "D1|$token" else "L1|$token"
        val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02X".format(it) }.take(12)
    }
}