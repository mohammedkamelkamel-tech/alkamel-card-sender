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
import java.security.MessageDigest
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

        val originating = messages.firstOrNull()?.originatingAddress?.trim().orEmpty()
        val displayOriginating = messages.firstOrNull()?.displayOriginatingAddress?.trim().orEmpty()
        val senderHint = when {
            isTrustedSender(originating) -> normalizeSender(originating)
            isTrustedSender(displayOriginating) -> normalizeSender(displayOriginating)
            else -> return
        }

        val body = messages.joinToString("") { it.messageBody ?: "" }.trim()
        if (body.isBlank()) return

        // منع معالجة نفس رسالة SMS مرتين، وهو أهم إجراء لمنع إرسال كرتين بسبب
        // تكرار بث الرسالة من النظام أو إعادة تسليم الـ Intent.
        val smsTimestamp = messages.firstOrNull()?.timestampMillis ?: 0L
        val fingerprint = fingerprint(originating, displayOriginating, smsTimestamp, body)
        if (isAlreadyProcessed(context, fingerprint)) {
            Log.w(TAG, "Duplicate SMS ignored: $fingerprint")
            return
        }

        val lower = body.lowercase()
        val isJawali = lower.contains("استلمت") && lower.contains("yer")
        val isJaib = lower.contains("اضيف") && lower.contains("تحويل") && lower.contains("من")
        if (!isJawali && !isJaib) return

        if (isJaib && senderHint != "jaib") return
        if (isJawali && senderHint != "jawali") return

        val categories = CardStore.categories(context)
        if (categories.isEmpty()) return

        val configuredAmounts = categories.sortedDescending().joinToString("|") { Pattern.quote(it.toString()) }
        val amountPattern = Pattern.compile(
            """(?<!\d)($configuredAmounts)(?!\d)"""
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

        val operationId = OperationLog.start(context, amount, destination, card)
        val sendResult = sendSms(context, destination, card, amount)

        if (sendResult.success) {
            CardStore.recordSale(context, amount, destination, card)
            OperationLog.finish(context, operationId, true)
            StockNotification.notifyIfLow(context, amount, CardStore.count(context, amount))
            Log.i(TAG, "Card sent successfully: amount=$amount phone=$destination")
        } else {
            CardStore.returnCard(context, amount, card)
            OperationLog.finish(context, operationId, false, sendResult.error)
            Log.e(TAG, "Card send failed: amount=$amount phone=$destination reason=${sendResult.error}")
        }
    }

    private fun fingerprint(originating: String, displayOriginating: String, timestamp: Long, body: String): String {
        val raw = "$originating|$displayOriginating|$timestamp|$body"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isAlreadyProcessed(context: Context, fingerprint: String): Boolean {
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val window = 7L * 24L * 60L * 60L * 1000L
        val all = prefs.all
        val editor = prefs.edit()
        all.forEach { (key, value) ->
            val time = value as? Long
            if (time == null || now - time > window) editor.remove(key)
        }
        val previous = prefs.getLong(fingerprint, 0L)
        if (previous > 0L && now - previous <= window) {
            editor.apply()
            return true
        }
        editor.putLong(fingerprint, now)
        editor.apply()
        return false
    }

    private fun normalizeSender(sender: String): String {
        return sender.trim().lowercase().replace(" ", "").replace("-", "")
    }

    private fun isTrustedSender(sender: String): Boolean {
        return normalizeSender(sender) == "jaib" || normalizeSender(sender) == "jawali"
    }

    private fun findAlternateDestination(context: Context, body: String): String? {
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

    private data class SendResult(val success: Boolean, val error: String = "")

    private fun sendSms(context: Context, phone: String, card: String, amount: Int): SendResult {
        return try {
            val prefs = context.getSharedPreferences("message_settings", Context.MODE_PRIVATE)
            val savedTemplate = prefs.getString(
                "template",
                "شبكة الكامل - كرت {السعر} ريال - رقم الكرت👇\n"
            ).orEmpty()

            // Convert a user-entered literal "\\n" into a real line break.
            // This prevents the SMS from displaying the characters "\\n".
            val template = savedTemplate
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("{السعر}", amount.toString())
                .take(44)

            val availableForTemplate = (54 - card.length).coerceAtLeast(0)
            val safeTemplate = template.take(availableForTemplate)
            val message = safeTemplate + card

            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) smsManager.sendTextMessage(phone, null, message, null, null)
            else smsManager.sendMultipartTextMessage(phone, null, ArrayList(parts), null, null)
            SendResult(true)
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
            SendResult(false, e.message ?: "تعذر إرسال الرسالة")
        }
    }
}
