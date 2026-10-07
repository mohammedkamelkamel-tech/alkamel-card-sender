package ye.alkamel.cardsender

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class PendingSale(val id:String,val time:String,val fingerprint:String,val amount:Int,val phone:String,val sim:Int,val subscriptionId:Int,val error:String="")

object PendingQueue {
    private const val FILE_NAME="pending_sales.queue"
    private val lock=ReentrantLock()
    private fun file(context:Context)=File(context.filesDir,FILE_NAME)
    fun enqueue(context:Context,fingerprint:String,amount:Int,phone:String,sim:Int,subscriptionId:Int):Boolean=lock.withLock{
        val rows=read(context); if(rows.any{it.fingerprint==fingerprint}) return false
        val id=System.currentTimeMillis().toString()+"-"+System.nanoTime().toString().takeLast(6)
        val time=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())
        append(context,PendingSale(id,time,fingerprint,amount,phone.filterNot{it=='|'},sim,subscriptionId)); true
    }
    fun peek(context:Context):PendingSale?=lock.withLock{read(context).firstOrNull()}
    fun remove(context:Context,id:String)=lock.withLock{write(context,read(context).filterNot{it.id==id})}
    fun setError(context:Context,id:String,error:String)=lock.withLock{
        val clean=error.replace("|"," ").replace("\n"," ").replace("\r"," ")
        write(context,read(context).map{if(it.id==id)it.copy(error=clean)else it})
    }
    fun pending(context:Context): List<PendingSale> =lock.withLock{read(context)}
    private fun append(context:Context,row:PendingSale){val f=file(context);f.parentFile?.mkdirs();f.appendText(serialize(row)+"\n")}
    private fun read(context:Context):List<PendingSale>{
        val f=file(context);if(!f.exists())return emptyList()
        return f.readLines().mapNotNull{line->
            val p=line.split("|",limit=8);if(p.size<8)null else PendingSale(p[0],p[1],p[2],p[3].toIntOrNull()?:return@mapNotNull null,p[4],p[5].toIntOrNull()?:1,p[6].toIntOrNull()?:return@mapNotNull null,p[7])
        }
    }
    private fun write(context:Context,rows:List<PendingSale>){
        val f=file(context)
        f.parentFile?.mkdirs()
        if(rows.isEmpty()){
            if(f.exists()) f.writeText("")
            return
        }
        // كتابة ذرية: نكتب ملفًا مؤقتًا ثم نستبدل الملف القديم، حتى لا تتلف قائمة
        // الحوالات إذا توقف التطبيق أو انقطع الهاتف أثناء الحفظ.
        val temp=File(f.parentFile, f.name+".tmp")
        temp.writeText(rows.joinToString("\n"){serialize(it)}+"\n")
        if(!temp.renameTo(f)){
            f.writeText(rows.joinToString("\n"){serialize(it)}+"\n")
            temp.delete()
        }
    }
    private fun serialize(row:PendingSale)=listOf(row.id,row.time,row.fingerprint,row.amount,row.phone,row.sim,row.subscriptionId,row.error).joinToString("|")
}
