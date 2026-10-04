package ye.alkamel.cardsender

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Operation(
    val id: String,
    val time: String,
    val status: String,
    val amount: Int,
    val phone: String,
    val card: String,
    val error: String
)

object OperationLog {
    private const val FILE_NAME = "operations.log"
    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun start(context: Context, amount: Int, phone: String, card: String): String {
        val id = System.currentTimeMillis().toString() + "-" + System.nanoTime().toString().takeLast(6)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        append(context, "${id}|${time}|PROCESSING|${amount}|${clean(phone)}|${clean(card)}|")
        return id
    }

    @Synchronized
    fun finish(context: Context, id: String, success: Boolean, error: String = "") {
        val rows = read(context).toMutableList()
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return
        rows[index] = rows[index].copy(
            status = if (success) "SUCCESS" else "FAILED",
            error = clean(error)
        )
        write(context, rows)
    }

    @Synchronized
    fun recent(context: Context, limit: Int = 300): List<Operation> =
        read(context).asReversed().take(limit)

    @Synchronized
    fun pending(context: Context): List<Operation> =
        read(context).filter { it.status == "PROCESSING" }.asReversed()

    fun backupFile(context: Context): File = file(context)

    private fun append(context: Context, line: String) {
        val f = file(context)
        f.parentFile?.mkdirs()
        f.appendText(line + "\n")
    }

    private fun read(context: Context): List<Operation> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split("|", limit = 7)
            if (p.size < 7) null else Operation(
                p[0], p[1], p[2],
                p[3].toIntOrNull() ?: return@mapNotNull null,
                p[4], p[5], p[6]
            )
        }
    }

    private fun write(context: Context, rows: List<Operation>) {
        val f = file(context)
        f.writeText(rows.joinToString("\n") {
            "${it.id}|${it.time}|${it.status}|${it.amount}|${clean(it.phone)}|${clean(it.card)}|${clean(it.error)}"
        } + if (rows.isNotEmpty()) "\n" else "")
    }

    private fun clean(value: String): String =
        value.replace("|", " ").replace("\n", " ").replace("\r", " ")
}
