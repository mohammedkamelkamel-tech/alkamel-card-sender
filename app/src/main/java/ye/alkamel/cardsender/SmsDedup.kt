package ye.alkamel.cardsender

import android.content.Context

object SmsDedup {
    private const val PREFS="processed_sms"
    private const val WINDOW=7L*24L*60L*60L*1000L
    fun wasProcessed(context:Context,fingerprint:String):Boolean{
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val now=System.currentTimeMillis();val previous=prefs.getLong(fingerprint,0L)
        val editor=prefs.edit();prefs.all.forEach{(key,value)->val time=value as? Long;if(time==null||now-time>WINDOW)editor.remove(key)};editor.apply()
        return previous>0L&&now-previous<=WINDOW
    }
    fun markProcessed(context:Context,fingerprint:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong(fingerprint,System.currentTimeMillis()).apply()}
}
