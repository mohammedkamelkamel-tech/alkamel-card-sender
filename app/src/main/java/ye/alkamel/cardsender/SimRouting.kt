package ye.alkamel.cardsender

import android.content.Intent
import android.os.Build
import android.telephony.SubscriptionManager

object SimRouting {
    const val SIM1 = 1
    const val SIM2 = 2

    fun simFromIntent(intent: Intent): Int? {
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val slot = try { SubscriptionManager.getSlotIndex(subId) } catch (_: Exception) { -1 }
            if (slot == 0) return SIM1
            if (slot == 1) return SIM2
        }
        val slotCandidates = intArrayOf(
            intent.getIntExtra("slotIndex", -1),
            intent.getIntExtra("slot", -1),
            intent.getIntExtra("simSlot", -1),
            intent.getIntExtra("sim_id", -1)
        )
        for (slot in slotCandidates) {
            if (slot == 0) return SIM1
            if (slot == 1) return SIM2
        }
        return null
    }

    fun subscriptionIdFromIntent(intent: Intent): Int? {
        val id = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID) id else null
    }

    fun label(sim: Int): String = if (sim == SIM2) "SIM2" else "SIM1"
}
