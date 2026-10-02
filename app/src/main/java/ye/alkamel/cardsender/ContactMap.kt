package ye.alkamel.cardsender

import android.content.Context

object ContactMap {
    private const val PREFS = "alternate_numbers"
    private const val KEY_MAP = "map_"

    fun getPhone(context: Context, alternate: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MAP + alternate.trim(), null)

    fun setPhone(context: Context, alternate: String, phone: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP + alternate.trim(), phone.trim())
            .apply()
    }

    fun all(context: Context): Map<String, String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filterKeys { it.startsWith(KEY_MAP) }
            .mapNotNull { (key, value) ->
                val alternate = key.removePrefix(KEY_MAP)
                val phone = value as? String
                if (alternate.isNotBlank() && !phone.isNullOrBlank()) alternate to phone else null
            }.toMap()
}
