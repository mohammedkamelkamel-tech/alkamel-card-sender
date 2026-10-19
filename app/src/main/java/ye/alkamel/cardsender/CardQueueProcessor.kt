package ye.alkamel.cardsender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

object CardQueueProcessor {
    private val executor=Executors.newSingleThreadExecutor()
    fun process(context:Context){val appContext=context.applicationContext;executor.execute{processNow(appContext)}}
    private fun processNow(context:Context){
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)return
        while(true){
            val servicesEnabled=ServiceControl.isEnabled(context)
            val pending=PendingQueue.nextForProcessing(context,servicesEnabled)?:return
            if(SmsDedup.wasProcessed(context,pending.fingerprint)){PendingQueue.remove(context,pending.id);continue}
            // Persist the in-flight state before touching inventory or sending SMS.
            // If Android kills the process, this transaction will require manual review
            // instead of silently consuming a second card on the next launch.
            if(!PendingQueue.markProcessing(context,pending.id)) return
            val card=CardStore.takeFirstCard(context,pending.amount,pending.sim)
            if(card==null){PendingQueue.markRetryable(context,pending.id,"لا يوجد كرت متوفر في مخزون فئة "+pending.amount+" ريال");return}
            val operationId=OperationLog.start(context,pending.amount,pending.phone,card,pending.id)
            val result=CardSmsSender.send(context,pending.phone,card,pending.amount,pending.subscriptionId)
            if(result.success){
                CardStore.recordSale(context,pending.amount,pending.phone,card,pending.sim);SmsDedup.markProcessed(context,pending.fingerprint);OperationLog.finish(context,operationId,true);PendingQueue.remove(context,pending.id)
                StockNotification.notifyIfLow(context,pending.amount,CardStore.count(context,pending.amount,pending.sim))
            }else{
                CardStore.returnCard(context,pending.amount,card,pending.sim);OperationLog.finish(context,operationId,false,result.error);PendingQueue.markRetryable(context,pending.id,result.error);return
            }
        }
    }
}
