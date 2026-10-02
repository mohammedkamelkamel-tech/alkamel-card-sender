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
        if (messages.isEmpty()) return

        // Some Yemen SMS gateways expose a numeric sender instead of the
        // visible name (Jaib/Jawali). Therefore sender-name validation must
        // not block an otherwise valid transfer message.
        val originating = messages.firstOrNull()?.originatingAddress?.trim().orEmpty()
        val displayOriginating = messages.firstOrNull()?.displayOriginatingAddress?.trim().orEmpty()
        val senderHint = when {
            isTrustedSender(originating) -> normalizeSender(originating)
            isTrustedSender(displayOriginating) -> normalizeSender(displayOriginating)
            else -> ""
        }

        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        if (body.isBlank()) return

        val lower = body.lowercase()
        val isJawali = lower.contains("استلمت") && lower.contains("yer")
        val isJaib = lower.contains("اضيف") && lower.contains("تحويل") && lower.contains("من")
        if (!isJawali && !isJaib) return

        // If the gateway gives us a sender name, use it as an extra check.
        // If it gives only a numeric/hidden sender, trust the exact SMS format.
        if (senderHint.isNotBlank()) {
            if (isJaib && senderHint != "jaib") return
            if (isJawali && senderHint != "jawali") return
        }

        val categories = CardStore.categories(context)
        if (categories.isEmpty()) return

        val amountPattern = Pattern.compile(
            """(?<!\d)(\${categories.sortedDescending().joinToString("|")})(?!\d)"""
        )
        val amountMatch = amountPattern.matcher(body)
        if (!amountMatch.find()) {
            Log.d(TAG, "No configured card amount found in SMS: $body")
            return
        }

        val amount = amountMatch.group(1).toInt()

        val phoneMatches = phonePattern.matcher(body)
        val phones = mutableListOf<String>()
        while (phoneMatches.find()) phones.add(phoneMatches.group(1))

        val destination = if (isJaib) {
            phones.lastOrNull() ?: findAlternateDestination(context, body)
        } else {
            phones.firstOrNull()
        }

        if (destination.isNullOrBlank()) {
            Log.d(TAG, "No destination phone found for amount=$amount body=$body")
            return
        }

        val card = CardStore.takeFirstCard(context, amount)
        if (card == null) {
            Log.d(TAG, "No card available for amount=$amount")
            return
        }

        if (sendSms(context, destination, card)) {
            CardStore.recordSale(context, amount, destination, card)
            StockNotification.notifyIfLow(context, amount, CardStore.count(context, amount))
        } else {
            CardStore.returnCard(context, amount, card)
        }
    }

    private fun normalizeSender(sender: String): String {
        return sender.trim().lowercase().replace(" ", "").replace("-", "")
    }

    private fun isTrustedSender(sender: String): Boolean {
        return normalizeSender(sender) == "jaib" || normalizeSender(sender) == "jawali"
    }

    private fun findAlternateDestination(context: Context, body: String): String? {
        // Jaib can put the sender's name between "من" and the alternate number:
        // "من كمال العجاج 164783". Extract the last numeric token after "من"
        // so the customer's name does not prevent the mapping from working.
        val fromIndex = body.lastIndexOf("من")
        if (fromIndex < 0) return null

        val tail = body.substring(fromIndex + 2)
        val numberPattern = Pattern.compile("""(?<!\d)(\d{4,12})(?!\d)""")
        val matcher = numberPattern.matcher(tail)
        var alternate: String? = null
        while (matcher.find()) alternate = matcher.group(1)

        if (alternate.isNullOrBlank()) return null
        val phone = ContactMap.getPhone(context, alternate)
        Log.d(TAG, "Jaib alternate destination: " + alternate + " -> " + (phone ?: "NOT_MAPPED"))
        return phone
    }

    private fun sendSms(context: Context, phone: String, card: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(card)
            if (parts.size == 1) smsManager.sendTextMessage(phone, null, card, null, null)
            else smsManager.sendMultipartTextMessage(phone, null, ArrayList(parts), null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
            false
        }
    }
}
