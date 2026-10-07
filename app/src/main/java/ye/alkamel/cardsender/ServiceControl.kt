package ye.alkamel.cardsender

import android.content.Context

object ServiceControl {
    private const val PREFS = "service_control"
    private const val KEY_ENABLED = "services_enabled"
    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
