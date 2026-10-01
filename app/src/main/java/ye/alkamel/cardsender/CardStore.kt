package ye.alkamel.cardsender

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object CardStore {
    val supportedAmounts = listOf(99, 100, 200, 245, 250, 500)
    private val lock = ReentrantLock()

    private fun folder(context: Context): File =
        File(context.getExternalFilesDir(null), "cards").apply { mkdirs() }

    private fun file(context: Context, amount: Int): File =
        File(folder(context), "$amount.txt")

    fun initializeFiles(context: Context) {
        supportedAmounts.forEach { amount ->
            val f = file(context, amount)
            if (!f.exists()) f.writeText("")
        }
    }

    fun count(context: Context, amount: Int): Int = lock.withLock {
        val f = file(context, amount)
        if (!f.exists()) return 0
        f.readLines().count { it.trim().isNotEmpty() }
    }

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
}
