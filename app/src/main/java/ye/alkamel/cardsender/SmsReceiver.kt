package ye.alkamel.cardsender

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlKamelCardSender"
        private val executor = Executors.newSingleThreadExecutor()
        private val amountPattern = Pattern.compile("""(?<!\d)(99|100|200|245|250|500)(?!\d)""")
        private val phonePattern = Pattern.compile("""(?<!\d)(7\d{8})(?!\d)""")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        val pendingResult = goAsync()
        executor.execute {
            try { process(context.applicationContext, intent) }
            catch (e: Exception) { Log.e(TAG, "SMS processing error", e) }
            finally { pendingResult.finish() }
        }
    }

    private fun process(context: Context, intent: Intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val bundle: Bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")
        val messages = pdus.mapNotNull {
            try { SmsMessage.createFromPdu(it as ByteArray, format) } catch (_: Exception) { null }
        }
        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        if (body.isBlank()) return

        val lower = body.lowercase()
        val isJawali = lower.contains("استلمت") && lower.contains("yer")
        val isJaib = lower.contains("اضيف") && lower.contains("تحويل") && lower.contains("من")
        if (!isJawali && !isJaib) return

        val amountMatch = amountPattern.matcher(body)
        if (!amountMatch.find()) return
        val amount = amountMatch.group(1).toInt()
        val phoneMatches = phonePattern.matcher(body)
        val phones = mutableListOf<String>()
        while (phoneMatches.find()) phones.add(phoneMatches.group(1))
        if (phones.isEmpty()) return
        val destination = if (isJaib) phones.last() else phones.first()

        val card = CardStore.takeFirstCard(context, amount) ?: return
        if (sendSms(context, destination, card)) {
            CardStore.recordSale(context, amount, destination, card)
            StockNotification.notifyIfLow(context, amount, CardStore.count(context, amount))
        } else {
            CardStore.returnCard(context, amount, card)
        }
    }

    private fun sendSms(context: Context, phone: String, card: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(card)
            if (parts.size == 1) smsManager.sendTextMessage(phone, null, card, null, null)
            else smsManager.sendMultipartTextMessage(phone, null, ArrayList(parts), null, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
            false
        }
    }
}
