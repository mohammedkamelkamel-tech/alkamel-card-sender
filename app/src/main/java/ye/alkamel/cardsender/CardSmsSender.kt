package ye.alkamel.cardsender

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

data class CardSendResult(val success:Boolean,val error:String="")

object CardSmsSender {
    fun send(context:Context,phone:String,card:String,amount:Int,subscriptionId:Int):CardSendResult=try{
        val prefs=context.getSharedPreferences("message_settings",Context.MODE_PRIVATE)
        val savedTemplate=prefs.getString("template","شبكة الكامل - كرت {السعر} ريال - رقم الكرت👇\n").orEmpty()
        val template=savedTemplate.replace("\\r\\n","\n").replace("\\n","\n").replace("{السعر}",amount.toString()).take(44)
        val message=template.take((54-card.length).coerceAtLeast(0))+card
        val smsManager=SmsManager.getSmsManagerForSubscriptionId(subscriptionId);val parts=smsManager.divideMessage(message)
        if(parts.size==1)smsManager.sendTextMessage(phone,null,message,null,null)else smsManager.sendMultipartTextMessage(phone,null,ArrayList(parts),null,null)
        CardSendResult(true)
    }catch(e:Exception){Log.e("AlKamelCardSender","SMS send failed",e);CardSendResult(false,e.message?:"تعذر إرسال الرسالة")}
}
