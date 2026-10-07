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
    val card: String,
    val sim: Int = 1
)

object CardStore {
    private const val PREFS = "card_categories"
    private const val KEY_CATEGORIES = "categories"
    private val defaultAmounts = listOf(99, 100, 200, 245, 250, 500)
    private val lock = ReentrantLock()

    fun categories(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_CATEGORIES, null)
        if (stored == null) {
            prefs.edit().putStringSet(KEY_CATEGORIES, defaultAmounts.map { it.toString() }.toSet()).apply()
            return defaultAmounts
        }
        return stored.mapNotNull { it.toIntOrNull() }.filter { it > 0 }.distinct().sorted()
    }

    fun addCategory(context: Context, amount: Int): Boolean = lock.withLock {
        if (amount <= 0) return false
        val current = categories(context).toMutableSet()
        if (!current.add(amount)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CATEGORIES, current.map { it.toString() }.toSet()).apply()
        val f = file(context, amount)
        if (!f.exists()) f.writeText("")
        true
    }

    private fun root(context: Context): File =
        File(context.filesDir, "cards").apply { mkdirs() }

    private fun simRoot(context: Context, sim: Int): File =
        File(root(context), if (sim == 2) "sim2" else "sim1").apply { mkdirs() }

    private fun legacyFile(context: Context, amount: Int): File =
        File(root(context), "$amount.txt")

    private fun file(context: Context, amount: Int, sim: Int = 1): File {
        migrateLegacySim1(context, amount)
        return File(simRoot(context, sim), "$amount.txt")
    }

    private fun salesFile(context: Context, sim: Int = 1): File {
        migrateLegacySales(context)
        return File(simRoot(context, sim), "sales.csv")
    }

    private fun migrateLegacySim1(context: Context, amount: Int) {
        val target = File(simRoot(context, 1), "$amount.txt")
        val legacy = legacyFile(context, amount)
        if (!target.exists() && legacy.exists()) {
            target.writeText(legacy.readText())
            legacy.delete()
        }
    }

    private fun migrateLegacySales(context: Context) {
        val target = File(simRoot(context, 1), "sales.csv")
        val legacy = File(context.filesDir, "sales.csv")
        if (!target.exists() && legacy.exists()) {
            target.writeText(legacy.readText())
            legacy.delete()
        }
    }

    fun initializeFiles(context: Context) {
        categories(context).forEach { amount ->
            for (sim in 1..2) {
                val f = file(context, amount, sim)
                if (!f.exists()) f.writeText("")
            }
        }
        for (sim in 1..2) {
            val sf = salesFile(context, sim)
            if (!sf.exists()) sf.writeText("time,amount,phone,card,sim\n")
        }
    }

    fun addCards(context: Context, amount: Int, raw: String): Int = addCards(context, amount, raw, 1)

    fun addCards(context: Context, amount: Int, raw: String, sim: Int): Int = lock.withLock {
        initializeFiles(context)
        if (!categories(context).contains(amount)) return 0
        val cards = raw.lines().map { it.trim() }
            .filter { it.isNotEmpty() && it.all(Char::isDigit) }
            .distinct()
        if (cards.isEmpty()) return 0

        // منع تكرار الكرت داخل المخزون أو إدخاله مرة أخرى بعد بيعه.
        val existing = categories(context)
            .flatMap { amountValue -> (1..2).flatMap { simValue -> cards(context, amountValue, simValue) } }
            .toMutableSet()
        existing += sales(context).map { it.card }
        val uniqueCards = cards.filterNot { existing.contains(it) }
        if (uniqueCards.isEmpty()) return 0

        val f = file(context, amount, sim)
        val old = if (f.exists()) f.readText().trimEnd() else ""
        val block = uniqueCards.joinToString("\n")
        f.writeText(if (old.isBlank()) "$block\n" else "$old\n$block\n")
        uniqueCards.size
    }

    fun moveCards(context: Context, fromAmount: Int, toAmount: Int, raw: String): Int = lock.withLock {
        initializeFiles(context)
        if (fromAmount == toAmount) return 0
        if (!categories(context).contains(fromAmount) || !categories(context).contains(toAmount)) return 0

        val requested = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.all(Char::isDigit) }
        if (requested.isEmpty()) return 0

        val sourceFile = file(context, fromAmount)
        val destinationFile = file(context, toAmount)
        if (!sourceFile.exists()) return 0

        val sourceCards = sourceFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val destinationExisting = if (destinationFile.exists()) {
            destinationFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else emptySet()
        val movedCards = mutableListOf<String>()

        requested.forEach { card ->
            val index = sourceCards.indexOf(card)
            if (index >= 0 && !destinationExisting.contains(card) && !movedCards.contains(card)) {
                sourceCards.removeAt(index)
                movedCards.add(card)
            }
        }

        if (movedCards.isEmpty()) return 0

        sourceFile.writeText(if (sourceCards.isEmpty()) "" else sourceCards.joinToString("\n") + "\n")

        val oldDestination = if (destinationFile.exists()) destinationFile.readText().trimEnd() else ""
        val block = movedCards.joinToString("\n")
        destinationFile.writeText(
            if (oldDestination.isBlank()) "$block\n" else "$oldDestination\n$block\n"
        )

        movedCards.size
    }

    fun count(context: Context, amount: Int): Int = count(context, amount, 1)

    fun count(context: Context, amount: Int, sim: Int): Int = lock.withLock {
        val f = file(context, amount, sim)
        if (!f.exists()) return 0
        f.readLines().count { it.trim().isNotEmpty() }
    }

    fun cards(context: Context, amount: Int): List<String> = cards(context, amount, 1)

    fun cards(context: Context, amount: Int, sim: Int): List<String> = lock.withLock {
        val f = file(context, amount, sim)
        if (!f.exists()) return emptyList()
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun deleteCard(context: Context, amount: Int, card: String, sim: Int = 1): Boolean = lock.withLock {
        val cleanCard = card.trim()
        if (cleanCard.isBlank()) return false
        val f = file(context, amount, sim)
        if (!f.exists()) return false
        val lines = f.readLines()
        val index = lines.indexOfFirst { it.trim() == cleanCard }
        if (index < 0) return false
        val remaining = lines.filterIndexed { i, _ -> i != index }
        f.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n") + "\n")
        true
    }

    fun removeFirstCards(context: Context, amount: Int, quantity: Int, sim: Int = 1): Int = lock.withLock {
        if (quantity <= 0) return 0
        val f = file(context, amount, sim)
        if (!f.exists()) return 0
        val lines = f.readLines().filter { it.trim().isNotEmpty() }
        val removed = minOf(quantity, lines.size)
        if (removed <= 0) return 0
        val remaining = lines.drop(removed)
        f.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n") + "\n")
        removed
    }

    fun totalStock(context: Context): Int = categories(context).sumOf { count(context, it) }

    fun takeFirstCard(context: Context, amount: Int): String? = takeFirstCard(context, amount, 1)

    fun takeFirstCard(context: Context, amount: Int, sim: Int): String? = lock.withLock {
        val f = file(context, amount, sim)
        if (!f.exists()) f.createNewFile()
        val lines = f.readLines()
        val index = lines.indexOfFirst { it.trim().isNotEmpty() }
        if (index < 0) return null
        val card = lines[index].trim()
        val remaining = lines.filterIndexed { i, _ -> i != index }
        f.writeText(if (remaining.isEmpty()) "" else remaining.joinToString("\n") + "\n")

        // Defensive cleanup: if the same card was accidentally duplicated in the other SIM,
        // remove it there under the same lock so it cannot be sent twice.
        val otherSim = if (sim == 2) 1 else 2
        categories(context).forEach { amountValue ->
            val otherFile = file(context, amountValue, otherSim)
            if (otherFile.exists()) {
                val otherLines = otherFile.readLines()
                val filtered = otherLines.filter { it.trim() != card }
                if (filtered.size != otherLines.size) {
                    otherFile.writeText(if (filtered.isEmpty()) "" else filtered.joinToString("\n") + "\n")
                }
            }
        }
        card
    }

    fun returnCard(context: Context, amount: Int, card: String) = returnCard(context, amount, card, 1)

    fun returnCard(context: Context, amount: Int, card: String, sim: Int) = lock.withLock {
        val cleanCard = card.trim()
        if (cleanCard.isBlank()) return
        val f = file(context, amount, sim)
        val existing = if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        if (existing.contains(cleanCard)) return
        f.writeText(cleanCard + "\n" + if (existing.isEmpty()) "" else existing.joinToString("\n") + "\n")
    }

    fun recordSale(context: Context, amount: Int, phone: String, card: String) = recordSale(context, amount, phone, card, 1)

    fun recordSale(context: Context, amount: Int, phone: String, card: String, sim: Int) = lock.withLock {
        initializeFiles(context)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        salesFile(context, sim).appendText("$time,$amount,$phone,$card,$sim\n")
    }

    fun sales(context: Context): List<Sale> = lock.withLock {
        val f = salesFile(context)
        if (!f.exists()) return emptyList()
        f.readLines().drop(1).mapNotNull { line ->
            val p = line.split(",", limit = 5)
            if (p.size >= 4) Sale(
                p[0],
                p[1].toIntOrNull() ?: return@mapNotNull null,
                p[2],
                p[3],
                p.getOrNull(4)?.toIntOrNull() ?: 1
            ) else null
        }.reversed()
    }

    fun sales(context: Context, sim: Int): List<Sale> = sales(context).filter { it.sim == sim }

    fun salesCount(context: Context): Int = sales(context).size
    fun salesCount(context: Context, sim: Int): Int = sales(context, sim).size

    fun backupFiles(context: Context): List<File> {
        initializeFiles(context)
        return buildList {
            for (sim in 1..2) {
                addAll(categories(context).map { file(context, it, sim) })
                add(salesFile(context, sim))
            }
            OperationLog.backupFile(context).takeIf { it.exists() }?.let { add(it) }
        }
    }
}
