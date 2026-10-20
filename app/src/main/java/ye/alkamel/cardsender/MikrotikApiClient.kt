package ye.alkamel.cardsender

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal RouterOS v6 native API client. Call only from a background thread.
 * Passwords are supplied per connection and are never persisted by this class.
 */
class MikrotikApiClient(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val useTls: Boolean,
    private val timeoutMs: Int = 5000
) {
    data class Reply(val attributes: Map<String, String>, val type: String, val message: String = "")

    fun command(path: String, attributes: Map<String, String> = emptyMap()): List<Reply> {
        val raw = Socket()
        val socket: Socket = if (useTls) {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket()
        } else raw
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.soTimeout = timeoutMs
        socket.use { s ->
            val out = BufferedOutputStream(s.getOutputStream())
            val input = BufferedInputStream(s.getInputStream())
            writeSentence(out, listOf("/login", "=name=$username", "=password=$password"))
            var loginDone = false
            while (!loginDone) {
                val login = readSentence(input)
                if (login.any { it.startsWith("!trap") }) {
                    val msg = login.firstOrNull { it.startsWith("=message=") }?.substringAfter("=message=").orEmpty()
                    throw IllegalStateException(msg.ifBlank { "فشل تسجيل الدخول إلى MikroTik" })
                }
                loginDone = login.any { it == "!done" }
                if (login.isEmpty()) throw IllegalStateException("لم يرد الراوتر على تسجيل الدخول")
            }
            val words = mutableListOf(path)
            attributes.forEach { (key, value) -> words += "=$key=$value" }
            writeSentence(out, words)
            val result = mutableListOf<Reply>()
            while (true) {
                val sentence = readSentence(input)
                if (sentence.isEmpty()) throw IllegalStateException("انقطع الاتصال أثناء قراءة رد الراوتر")
                val type = sentence.first()
                val values = linkedMapOf<String, String>()
                var message = ""
                sentence.drop(1).forEach { word ->
                    if (word.startsWith("=")) {
                        val body = word.substring(1)
                        val split = body.indexOf('=')
                        if (split > 0) {
                            val key = body.substring(0, split)
                            val value = body.substring(split + 1)
                            values[key] = value
                            if (key == "message") message = value
                        }
                    }
                }
                result += Reply(values, type, message)
                if (type == "!done") break
                if (type == "!trap") throw IllegalStateException(message.ifBlank { "رفض الراوتر العملية المطلوبة" })
            }
            return result.filter { it.type == "!re" }
        }
    }

    private fun writeSentence(out: BufferedOutputStream, words: List<String>) {
        words.forEach { word ->
            val bytes = word.toByteArray(Charsets.UTF_8)
            writeLength(out, bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.flush()
    }

    private fun readSentence(input: BufferedInputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val length = readLength(input)
            if (length == 0) return words
            if (length < 0 || length > 1024 * 1024) throw IllegalStateException("رد غير صالح من الراوتر")
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val n = input.read(bytes, offset, length - offset)
                if (n < 0) throw IllegalStateException("انقطع الاتصال بالراوتر")
                offset += n
            }
            words += String(bytes, Charsets.UTF_8)
        }
    }

    private fun readLength(input: BufferedInputStream): Int {
        val c = input.read()
        if (c < 0) throw IllegalStateException("انتهى اتصال الراوتر")
        if ((c and 0x80) == 0) return c
        if ((c and 0xC0) == 0x80) return ((c and 0x3F) shl 8) or input.read()
        if ((c and 0xE0) == 0xC0) return ((c and 0x1F) shl 16) or (input.read() shl 8) or input.read()
        if ((c and 0xF0) == 0xE0) return ((c and 0x0F) shl 24) or (input.read() shl 16) or (input.read() shl 8) or input.read()
        return ((input.read() shl 24) or (input.read() shl 16) or (input.read() shl 8) or input.read())
    }

    private fun writeLength(out: BufferedOutputStream, length: Int) {
        when {
            length < 0x80 -> out.write(length)
            length < 0x4000 -> { out.write((length shr 8) or 0x80); out.write(length and 0xFF) }
            length < 0x200000 -> { out.write((length shr 16) or 0xC0); out.write((length shr 8) and 0xFF); out.write(length and 0xFF) }
            length < 0x10000000 -> { out.write((length shr 24) or 0xE0); out.write((length shr 16) and 0xFF); out.write((length shr 8) and 0xFF); out.write(length and 0xFF) }
            else -> { out.write(0xF0); out.write((length shr 24) and 0xFF); out.write((length shr 16) and 0xFF); out.write((length shr 8) and 0xFF); out.write(length and 0xFF) }
        }
    }
}
