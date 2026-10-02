package ye.alkamel.cardsender

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class Sale(
    val time: String,
    val amount: Int,
    val phone: String,
    val card: String
)

object CardStore {
    val supportedAmounts = listOf(99, 100, 200, 245, 250, 500)
    private val lock = ReentrantLock()

    private fun root(context: Context): File =
        File(context.filesDir, "cards").apply { mkdirs() }

    private fun file(context: Context, amount: Int): File =
        File(root(context), "$amount.txt")

    private fun salesFile(context: Context): File =
        File(context.filesDir, "sales.csv")

    fun initializeFiles(context: Context) {
        supportedAmounts.forEach { amount ->
            val f = file(context, amount)
            if (!f.exists()) f.writeText("")
        }
        if (!salesFile(context).exists()) {
            salesFile(context).writeText("time,amount,phone,card\n")
        }
    }

    fun addCards(context: Context, amount: Int, raw: String): Int = lock.withLock {
        initializeFiles(context)
        val cards = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }
        if (cards.isEmpty()) return 0
        val f = file(context, amount)
        val old = if (f.exists()) f.readText().trimEnd() else ""
        val block = cards.joinToString("\n")
        f.writeText(if (old.isBlank()) "$block\n" else "$old\n$block\n")
        cards.size
    }

    fun count(context: Context, amount: Int): Int = lock.withLock {
        val f = file(context, amount)
        if (!f.exists()) return 0
        f.readLines().count { it.trim().isNotEmpty() }
    }

    fun cards(context: Context, amount: Int): List<String> = lock.withLock {
        val f = file(context, amount)
        if (!f.exists()) return emptyList()
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun totalStock(context: Context): Int = supportedAmounts.sumOf { count(context, it) }

    fun takeFirstCard(context: Context, amount: Int): String? = lock.withLock {
        val f = file(context, amount)
        if (!f.exists()) f.createNewFile()
        val lines = f.readLines()
        val index = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (index < 0) return null
        val card = lines[index].trim()
        val remaining = lines.filterIndexed { i, _ -> i != index }
        f.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n") + "\n")
        card
    }

    fun returnCard(context: Context, amount: Int, card: String) = lock.withLock {
        val f = file(context, amount)
        val old = if (f.exists()) f.readText() else ""
        f.writeText(card.trim() + "\n" + old)
    }

    fun recordSale(context: Context, amount: Int, phone: String, card: String) = lock.withLock {
        initializeFiles(context)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        salesFile(context).appendText("$time,$amount,$phone,$card\n")
    }

    fun sales(context: Context): List<Sale> = lock.withLock {
        val f = salesFile(context)
        if (!f.exists()) return emptyList()
        f.readLines().drop(1).mapNotNull { line ->
            val p = line.split(",", limit = 4)
            if (p.size == 4) Sale(p[0], p[1].toIntOrNull() ?: return@mapNotNull null, p[2], p[3]) else null
        }.reversed()
    }

    fun salesCount(context: Context): Int = sales(context).size

    fun backupFiles(context: Context): List<File> {
        initializeFiles(context)
        return buildList {
            addAll(supportedAmounts.map { file(context, it) })
            add(salesFile(context))
        }
    }
}
