package ye.alkamel.cardsender
import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val requestCode = 700
    private lateinit var content: LinearLayout
    private var currentScreen = "dashboard"

    override fun onBackPressed() {
        if (currentScreen != "dashboard") showDashboard() else super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackupSchedule()
        requestPermissions()
        CardStore.initializeFiles(this)
        buildUi()
        showDashboard()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(248,249,252)); layoutDirection=View.LAYOUT_DIRECTION_RTL }
        val header = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,20); setBackgroundColor(Color.rgb(20,91,150)) }
        header.addView(TextView(this).apply { text="الكامل أونلاين"; textSize=27f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text="إدارة مخزون الكروت والمبيعات والإرسال التلقائي"; textSize=14f; setTextColor(Color.WHITE) })
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(8,8,8,8);setBackgroundColor(Color.WHITE)}
        nav.addView(navButton("الرئيسية"){showDashboard()}); nav.addView(navButton("إضافة كروت"){showAddCards()}); nav.addView(navButton("المبيعات"){showSales()}); nav.addView(navButton("النسخ الاحتياطية"){showBackup()})
        val scroll=ScrollView(this)
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,8,18,30)}
        scroll.addView(content)
        root.addView(header);root.addView(nav);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
    }

    private fun navButton(text:String,action:()->Unit)=Button(this).apply{this.text=text;textSize=12f;setTextColor(Color.rgb(35,35,35));setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(0,54.dp(),1f).apply{setMargins(3,0,3,0)}}

    private fun showDashboard(){
        currentScreen = "dashboard"
        content.removeAllViews();addTitle("لوحة التحكم")
        addText("إجمالي الكروت المتبقية: ${CardStore.totalStock(this)}\nإجمالي الكروت المباعة: ${CardStore.salesCount(this)}")
        addSectionTitle("المخزون حسب الفئة")
        CardStore.supportedAmounts.forEach{amount->content.addView(cardRow("$amount ريال","${CardStore.count(this,amount)} كرت"))}
        addSectionTitle("اختصارات");addButton("إضافة كروت جديدة"){showAddCards()};addButton("معرفة الكروت التي تم بيعها"){showSales()}
        addButton("إنشاء نسخة احتياطية الآن"){val name=BackupManager.createBackup(this);Toast.makeText(this,if(name!=null)"تم حفظ النسخة في التنزيلات" else "تعذر إنشاء النسخة",Toast.LENGTH_LONG).show()}
    }

    private fun showAddCards(){
        currentScreen = "add_cards"
        content.removeAllViews();addBackButton();addTitle("إضافة الكروت");addText("اختر فئة الكرت، ثم الصق أرقام الكروت. كل رقم في سطر مستقل.")
        val spinner=Spinner(this);spinner.setBackgroundColor(Color.WHITE);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,CardStore.supportedAmounts.map{"$it ريال"});content.addView(spinner)
        val input=EditText(this).apply{hint="مثال:\n18466933\n10356433\n...";setTextColor(Color.rgb(25,25,25));setHintTextColor(Color.rgb(110,110,110));textSize=17f;minLines=10;gravity=Gravity.TOP or Gravity.RIGHT;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE}
        content.addView(input,LinearLayout.LayoutParams(-1,0,1f))
        addButton("حفظ الكروت"){val amount=CardStore.supportedAmounts[spinner.selectedItemPosition];val added=CardStore.addCards(this,amount,input.text.toString());Toast.makeText(this,if(added>0)"تم حفظ $added كرت من فئة $amount ريال" else "لم يتم العثور على أرقام كروت صحيحة",Toast.LENGTH_LONG).show();if(added>0)input.setText("")}
        addText("المخزون الحالي: "+CardStore.supportedAmounts.joinToString(" | "){"$it=${CardStore.count(this,it)}"})
    }

    private fun showSales(){
        currentScreen = "sales"
        content.removeAllViews();addBackButton();addTitle("الكروت التي تم بيعها");val sales=CardStore.sales(this);addText("عدد المبيعات المسجلة: ${sales.size}")
        if(sales.isEmpty()){addText("لا توجد مبيعات مسجلة حتى الآن.");return}
        sales.take(300).forEach{sale->
            val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,12);setBackgroundColor(Color.WHITE)}
            box.addView(TextView(this).apply{text="كرت ${sale.amount} ريال  •  ${sale.time}";textSize=15f;typeface=Typeface.DEFAULT_BOLD})
            box.addView(TextView(this).apply{text="الرقم: ${sale.phone}\nالكرت: ${sale.card}";textSize=14f})
            content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,8)})
        }
    }

    private fun showBackup(){
        currentScreen = "backup"
        content.removeAllViews();addBackButton();addTitle("النسخ الاحتياطية")
        addText("النسخة اليومية تشمل الكروت المتبقية، الكروت المباعة، أرقام المستلمين، وتاريخ المبيعات وإعدادات التطبيق.")
        val last=getSharedPreferences("settings",MODE_PRIVATE).getString("last_backup","لم يتم إنشاء نسخة بعد");addText("آخر نسخة: $last")
        addButton("إنشاء نسخة احتياطية الآن"){val name=BackupManager.createBackup(this);Toast.makeText(this,if(name!=null)"تم حفظ النسخة داخل التنزيلات" else "فشل إنشاء النسخة",Toast.LENGTH_LONG).show();showBackup()}
    }

    private fun addBackButton(){ addButton("رجوع إلى الرئيسية"){ showDashboard() } }

    private fun addTitle(text:String){content.addView(TextView(this).apply{this.text=text;textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(35,35,35));setPadding(0,12,0,10)})}
    private fun addSectionTitle(text:String){content.addView(TextView(this).apply{this.text=text;textSize=18f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(45,45,45));setPadding(0,16,0,8)})}
    private fun addText(text:String){content.addView(TextView(this).apply{this.text=text;textSize=15f;setTextColor(Color.rgb(45,45,45));setPadding(0,8,0,10)})}
    private fun addButton(text:String,action:()->Unit){content.addView(Button(this).apply{this.text=text;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(35,95,150));setOnClickListener{action()}})}
    private fun cardRow(title:String,value:String):View{return LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(18,14,18,14);setBackgroundColor(Color.WHITE);addView(TextView(this@MainActivity).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(30,30,30))},LinearLayout.LayoutParams(0,-2,1f));addView(TextView(this@MainActivity).apply{text=value;textSize=16f;setTextColor(Color.rgb(20,90,145))});layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,6)}}}
    private fun setupBackupSchedule(){val request=PeriodicWorkRequestBuilder<BackupWorker>(1,TimeUnit.DAYS).build();WorkManager.getInstance(this).enqueueUniquePeriodicWork("alkamel_daily_backup",ExistingPeriodicWorkPolicy.KEEP,request)}
    private fun requestPermissions(){val needed=mutableListOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS);if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);val missing=needed.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED};if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),requestCode)}
    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()
}
