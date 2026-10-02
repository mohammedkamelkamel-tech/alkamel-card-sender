package ye.alkamel.cardsender
import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private var dashboardStockText: TextView? = null
    private var dashboardSoldText: TextView? = null
    private val dashboardCategoryViews = mutableMapOf<Int, TextView>()

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val stockRefreshRunnable = object : Runnable {
        override fun run() {
            if (currentScreen == "dashboard") refreshDashboardStock()
            refreshHandler.postDelayed(this, 1000)
        }
    }

    override fun onBackPressed() {
        if (currentScreen != "dashboard") showDashboard() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (!LicenseManager.isActivated(this)) {
            showActivationScreen()
            return
        }
        refreshHandler.removeCallbacks(stockRefreshRunnable)
        refreshHandler.post(stockRefreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(stockRefreshRunnable)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!LicenseManager.isActivated(this)) {
            showActivationScreen()
            return
        }
        startApp()
    }

    private fun startApp() {
        setupBackupSchedule()
        requestPermissions()
        CardStore.initializeFiles(this)
        buildUi()
        showDashboard()
    }

    private fun showActivationScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(248,249,252))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(TextView(this).apply {
            text = "الكامل أونلاين"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20,91,150))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "أدخل كود الشراء لتفعيل التطبيق"
            textSize = 18f
            setTextColor(Color.rgb(45,45,45))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 18)
        }, LinearLayout.LayoutParams(-1, -2))

        val codeInput = EditText(this).apply {
            hint = "مثال: D1-XXXXXXXXXXXX-XXXXXXXXXXXX"
            textSize = 17f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(Color.rgb(25,25,25))
            setHintTextColor(Color.rgb(110,110,110))
        }
        root.addView(codeInput, LinearLayout.LayoutParams(-1, -2))

        val activateButton = Button(this).apply {
            text = "تفعيل التطبيق"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35,95,150))
        }
        root.addView(activateButton, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 16, 0, 8) })

        root.addView(TextView(this).apply {
            text = "يوجد نوعان من الأكواد:\n• كود يوم واحد: يعمل 24 ساعة من أول تفعيل\n• كود مدى الحياة: لا تنتهي صلاحيته"
            textSize = 14f
            setTextColor(Color.rgb(70,70,70))
            setPadding(0, 12, 0, 0)
            gravity = Gravity.RIGHT
        }, LinearLayout.LayoutParams(-1, -2))

        activateButton.setOnClickListener {
            when (LicenseManager.activate(this, codeInput.text.toString())) {
                "DAY" -> {
                    Toast.makeText(this, "تم تفعيل التطبيق لمدة 24 ساعة", Toast.LENGTH_LONG).show()
                    startApp()
                }
                "LIFE" -> {
                    Toast.makeText(this, "تم تفعيل التطبيق مدى الحياة", Toast.LENGTH_LONG).show()
                    startApp()
                }
                else -> Toast.makeText(this, "كود التفعيل غير صحيح", Toast.LENGTH_LONG).show()
            }
        }

        setContentView(root)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(248,249,252)); layoutDirection=View.LAYOUT_DIRECTION_RTL }
        val header = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,20); setBackgroundColor(Color.rgb(20,91,150)) }
        header.addView(TextView(this).apply { text="الكامل أونلاين"; textSize=27f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text="إدارة مخزون الكروت والمبيعات والإرسال التلقائي"; textSize=14f; setTextColor(Color.WHITE) })
        header.addView(TextView(this).apply { text=LicenseManager.remainingText(this@MainActivity); textSize=12f; setTextColor(Color.WHITE); setPadding(0,6,0,0) })
        // شريط القوائم مرتب من اليمين إلى اليسار، مع تمرير أفقي حتى لا
        // تتكسر أسماء الأزرار إلى سطرين بسبب ضيق الشاشة.
        val navScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isHorizontalScrollBarEnabled = false
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8,8,8,8)
        }
        nav.addView(navButton("الرئيسية"){showDashboard()})
        nav.addView(navButton("إضافة"){showAddCards()})
        nav.addView(navButton("الفئات"){showCategories()})
        nav.addView(navButton("الربط"){showAlternateNumbers()})
        nav.addView(navButton("المخزون"){showStock()})
        nav.addView(navButton("المبيعات"){showSales()})
        nav.addView(navButton("النسخ"){showBackup()})
        navScroll.addView(nav, LinearLayout.LayoutParams(-2, -1))
        
        val scroll=ScrollView(this)
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,8,18,30)}
        scroll.addView(content)
        val footer = TextView(this).apply {
            text = "حقوق محمد كامل - 772072056"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90,90,90))
            setPadding(8,10,8,10)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(header)
        root.addView(navScroll, LinearLayout.LayoutParams(-1,62.dp()))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(footer, LinearLayout.LayoutParams(-1,-2))
        setContentView(root)
    }

    private fun navButton(text:String,action:()->Unit)=Button(this).apply{
        this.text=text
        textSize=13f
        minWidth=88.dp()
        minimumWidth=88.dp()
        minHeight=48.dp()
        minimumHeight=48.dp()
        setSingleLine(true)
        maxLines=1
        setPadding(14,0,14,0)
        setTextColor(Color.rgb(35,35,35))
        setOnClickListener{action()}
        layoutParams=LinearLayout.LayoutParams(-2,50.dp()).apply{setMargins(3,0,3,0)}
    }

    private fun showDashboard(){
        currentScreen = "dashboard"
        content.removeAllViews()
        dashboardCategoryViews.clear()
        addTitle("لوحة التحكم")

        dashboardStockText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35,35,35))
            setPadding(0,8,0,4)
        }
        content.addView(dashboardStockText)

        dashboardSoldText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35,35,35))
            setPadding(0,4,0,10)
        }
        content.addView(dashboardSoldText)

        addSectionTitle("مخزون كل فئة بشكل مستقل")
        CardStore.categories(this).forEach { amount ->
            val row = cardRow("$amount ريال", "0 كرت")
            val valueView = row.findViewWithTag<TextView>("stock_value")
            if (valueView != null) dashboardCategoryViews[amount] = valueView
            content.addView(row)
        }

        addSectionTitle("إدارة الفئات")
        addButton("إضافة فئة كروت جديدة") { showCategories() }
        addButton("ربط رقم بديل برقم جوال") { showAlternateNumbers() }
        addSectionTitle("إدارة المخزون")
        addButton("عرض أرقام الكروت المتبقية لكل فئة") { showStock() }

        addSectionTitle("اختصارات")
        addButton("إضافة كروت جديدة") { showAddCards() }
        addButton("معرفة الكروت التي تم بيعها") { showSales() }
        addButton("إعدادات تنبيه نقص المخزون") { showStockAlertSettings() }
        addButton("إنشاء نسخة احتياطية الآن") {
            val name = BackupManager.createBackup(this)
            Toast.makeText(this, if(name != null) "تم حفظ النسخة في التنزيلات" else "تعذر إنشاء النسخة", Toast.LENGTH_LONG).show()
        }

        refreshDashboardStock()
    }

    private fun refreshDashboardStock() {
        dashboardStockText?.text = "إجمالي الكروت المتبقية: ${CardStore.totalStock(this)} كرت"
        dashboardSoldText?.text = "إجمالي الكروت المباعة: ${CardStore.salesCount(this)} كرت"
        CardStore.categories(this).forEach { amount ->
            dashboardCategoryViews[amount]?.text = "${CardStore.count(this, amount)} كرت متبقي"
        }
    }

    private fun showAddCards(){
        currentScreen = "add_cards"
        content.removeAllViews();addBackButton();addTitle("إضافة الكروت");addText("اختر فئة الكرت، ثم الصق أرقام الكروت. كل رقم في سطر مستقل.")
        val categories = CardStore.categories(this)
        if (categories.isEmpty()) { addText("أضف فئة أولًا من قسم الفئات."); return }
        val spinner=Spinner(this);spinner.setBackgroundColor(Color.WHITE);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,categories.map{"$it ريال"});content.addView(spinner)
        val input=EditText(this).apply{hint="مثال:\n18466933\n10356433\n...";setTextColor(Color.rgb(25,25,25));setHintTextColor(Color.rgb(110,110,110));textSize=17f;minLines=10;gravity=Gravity.TOP or Gravity.RIGHT;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE}
        content.addView(input,LinearLayout.LayoutParams(-1,0,1f))
        addButton("حفظ الكروت"){val amount=categories[spinner.selectedItemPosition];val added=CardStore.addCards(this,amount,input.text.toString());Toast.makeText(this,if(added>0)"تم حفظ $added كرت من فئة $amount ريال" else "لم يتم العثور على أرقام كروت صحيحة",Toast.LENGTH_LONG).show();if(added>0)input.setText("")}
        addButton("تصحيح فئة الكروت / نقل الكروت") { showMoveCards() }
        addText("المخزون الحالي: "+categories.joinToString(" | "){"$it=${CardStore.count(this,it)}"})
    }

    private fun showMoveCards() {
        currentScreen = "move_cards"
        content.removeAllViews()
        addBackButton()
        addTitle("تصحيح فئة الكروت")
        addText("إذا أضفت كروت لفئة بالخطأ، يمكنك نقلها إلى الفئة الصحيحة بدون حذفها. مثال: كروت 250 أضيفت بالخطأ إلى فئة 100، يمكنك نقلها إلى فئة 250.")

        val categories = CardStore.categories(this)
        if (categories.size < 2) {
            addText("تحتاج إلى وجود فئتين على الأقل لاستخدام التصحيح.")
            return
        }

        val fromSpinner = Spinner(this).apply {
            setBackgroundColor(Color.WHITE)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                categories.map { it.toString() + " ريال — " + CardStore.count(this@MainActivity, it) + " كرت" })
        }
        val toSpinner = Spinner(this).apply {
            setBackgroundColor(Color.WHITE)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                categories.map { it.toString() + " ريال — " + CardStore.count(this@MainActivity, it) + " كرت" })
        }

        addText("الفئة الخاطئة (من):")
        content.addView(fromSpinner)
        addText("الفئة الصحيحة (إلى):")
        content.addView(toSpinner)

        val input = EditText(this).apply {
            hint = "للنقل المحدد: الصق أرقام الكروت هنا، كل كرت في سطر"
            setTextColor(Color.rgb(25,25,25))
            setHintTextColor(Color.rgb(110,110,110))
            textSize = 17f
            minLines = 8
            gravity = Gravity.TOP or Gravity.RIGHT
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        content.addView(input)

        addButton("نقل الكروت المحددة") {
            val from = categories[fromSpinner.selectedItemPosition]
            val to = categories[toSpinner.selectedItemPosition]
            if (from == to) {
                Toast.makeText(this, "اختر فئتين مختلفتين", Toast.LENGTH_LONG).show()
                return@addButton
            }
            val moved = CardStore.moveCards(this, from, to, input.text.toString())
            Toast.makeText(
                this,
                if (moved > 0) "تم نقل " + moved + " كرت من " + from + " إلى " + to + " ريال" else "لم يتم نقل أي كرت. تأكد أن الأرقام موجودة في الفئة " + from,
                Toast.LENGTH_LONG
            ).show()
            if (moved > 0) {
                input.setText("")
                showMoveCards()
            }
        }

        addButton("نقل جميع كروت الفئة") {
            val from = categories[fromSpinner.selectedItemPosition]
            val to = categories[toSpinner.selectedItemPosition]
            if (from == to) {
                Toast.makeText(this, "اختر فئتين مختلفتين", Toast.LENGTH_LONG).show()
                return@addButton
            }

            val available = CardStore.count(this, from)
            if (available <= 0) {
                Toast.makeText(this, "لا توجد كروت متبقية في فئة " + from + " ريال", Toast.LENGTH_LONG).show()
                return@addButton
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("تأكيد نقل الكروت")
                .setMessage("سيتم نقل جميع الكروت المتبقية من فئة " + from + " ريال إلى فئة " + to + " ريال.\n\nعدد الكروت: " + available + "\n\nاستخدم هذا الخيار فقط إذا كنت متأكدًا أن الكروت أضيفت للفئة الخطأ.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("نقل الكل") { _, _ ->
                    val cards = CardStore.cards(this, from)
                    val moved = CardStore.moveCards(this, from, to, cards.joinToString("\n"))
                    Toast.makeText(this, "تم نقل " + moved + " كرت من " + from + " إلى " + to + " ريال", Toast.LENGTH_LONG).show()
                    showMoveCards()
                }
                .show()
        }

        addText("ملاحظة: الكروت التي تم بيعها لا تظهر في المخزون، والتصحيح يخص الكروت المتبقية فقط.")
    }

    private fun showStock() {
        currentScreen = "stock"
        content.removeAllViews()
        addBackButton()
        addTitle("مخزون الكروت")
        val categories = CardStore.categories(this)
        if (categories.isEmpty()) { addText("لا توجد فئات. أضف فئة من قسم الفئات."); return }
        addText("اختر الفئة لعرض أرقام الكروت المتبقية. الأرقام الموجودة هنا هي التي لم تُبع بعد.")

        val spinner = Spinner(this)
        spinner.setBackgroundColor(Color.WHITE)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            categories.map { amount -> "$amount ريال — ${CardStore.count(this, amount)} كرت" })
        content.addView(spinner)

        val cardsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(cardsContainer)

        fun renderCards() {
            cardsContainer.removeAllViews()
            val amount = categories[spinner.selectedItemPosition]
            val cards = CardStore.cards(this, amount)

            cardsContainer.addView(TextView(this).apply {
                text = "فئة $amount ريال — ${cards.size} كرت متبقي"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(30,30,30))
                setPadding(0,16,0,10)
            })

            if (cards.isEmpty()) {
                cardsContainer.addView(TextView(this).apply {
                    text = "لا يوجد كروت متبقية في هذه الفئة."
                    textSize = 15f
                    setTextColor(Color.rgb(45,45,45))
                    setPadding(0,8,0,10)
                })
                return
            }

            cards.forEachIndexed { index, card ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14,12,14,12)
                    setBackgroundColor(Color.WHITE)
                }
                box.addView(TextView(this@MainActivity).apply {
                    text = "${index + 1}."
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(20,90,145))
                }, LinearLayout.LayoutParams(42.dp(), -2))
                box.addView(TextView(this@MainActivity).apply {
                    text = card
                    textSize = 17f
                    setTextColor(Color.rgb(25,25,25))
                }, LinearLayout.LayoutParams(0,-2,1f))
                cardsContainer.addView(box, LinearLayout.LayoutParams(-1,-2).apply {
                    setMargins(0,0,0,6)
                })
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { renderCards() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        addButton("تحديث المخزون والأرقام") {
            val position = spinner.selectedItemPosition
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                CardStore.categories(this).map { amount -> "$amount ريال — ${CardStore.count(this, amount)} كرت" })
            spinner.setSelection(position.coerceAtMost(CardStore.categories(this).lastIndex))
            renderCards()
        }

        renderCards()
    }

    private fun showCategories() {
        currentScreen = "categories"
        content.removeAllViews()
        addBackButton()
        addTitle("إدارة فئات الكروت")
        addText("أضف أي فئة تريدها يدويًا. بعد الحفظ ستظهر تلقائيًا في الإضافة والمخزون والإرسال والتنبيهات والنسخ الاحتياطي.")
        val input = EditText(this).apply {
            hint = "اكتب قيمة الفئة مثل 300"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            setTextColor(Color.rgb(25,25,25))
            setHintTextColor(Color.rgb(110,110,110))
            gravity = Gravity.RIGHT
        }
        content.addView(input)
        addButton("حفظ وإضافة الفئة") {
            val amount = input.text.toString().trim().toIntOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "أدخل رقم فئة صحيح أكبر من صفر", Toast.LENGTH_LONG).show()
            } else if (CardStore.categories(this).contains(amount)) {
                Toast.makeText(this, "هذه الفئة موجودة بالفعل", Toast.LENGTH_LONG).show()
            } else {
                CardStore.addCategory(this, amount)
                StockNotification.setThreshold(this, amount, 20)
                Toast.makeText(this, "تمت إضافة فئة " + amount + " ريال", Toast.LENGTH_SHORT).show()
                input.setText("")
                showCategories()
            }
        }
        addSectionTitle("الفئات الحالية")
        CardStore.categories(this).forEach { amount ->
            addText("• " + amount + " ريال — " + CardStore.count(this, amount) + " كرت")
        }
    }

    private fun showAlternateNumbers() {
        currentScreen = "alternate_numbers"
        content.removeAllViews()
        addBackButton()
        addTitle("ربط الأرقام البديلة")
        addText("إذا وصلت رسالة جيب برقم بديل مثل 164783، اربطه برقم الجوال الحقيقي. سيُستخدم نفس الربط لجميع فئات الكروت.")
        val alternate = EditText(this).apply {
            hint = "الرقم البديل مثل 164783"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            setTextColor(Color.rgb(25,25,25))
            gravity = Gravity.RIGHT
        }
        val phone = EditText(this).apply {
            hint = "رقم الجوال مثل 772072056"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            textSize = 18f
            setTextColor(Color.rgb(25,25,25))
            gravity = Gravity.RIGHT
        }
        content.addView(alternate)
        content.addView(phone)
        addButton("حفظ الربط") {
            val a = alternate.text.toString().trim()
            val p = phone.text.toString().trim()
            if (a.length < 4 || p.length != 9 || !p.startsWith("7")) {
                Toast.makeText(this, "تأكد من الرقم البديل ورقم الجوال", Toast.LENGTH_LONG).show()
            } else {
                ContactMap.setPhone(this, a, p)
                Toast.makeText(this, "تم ربط $a بالرقم $p لجميع الفئات", Toast.LENGTH_LONG).show()
                alternate.setText("")
                phone.setText("")
                renderAlternateNumbers()
            }
        }
        addSectionTitle("الروابط المحفوظة")
        renderAlternateNumbers()
    }

    private fun renderAlternateNumbers() {
        val markerView = content.findViewWithTag<View>("alternate_list")
        if (markerView != null) content.removeView(markerView)
        val list = LinearLayout(this).apply {
            tag = "alternate_list"
            orientation = LinearLayout.VERTICAL
        }
        ContactMap.all(this).forEach { (a, p) ->
            list.addView(TextView(this).apply {
                text = "البديل: $a  ←  الجوال: $p"
                textSize = 16f
                setTextColor(Color.rgb(35,35,35))
                setPadding(12,10,12,10)
                setBackgroundColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,6) })
        }
        content.addView(list)
    }

    private fun showStockAlertSettings() {
        currentScreen = "stock_alert_settings"
        content.removeAllViews()
        addBackButton()
        addTitle("تنبيه نقص المخزون")
        addText("حدد الحد لكل فئة. عندما يصل المخزون إلى هذا العدد أو أقل، يظهر تنبيه جديد مع كل عملية بيع من نفس الفئة فقط. اكتب 0 لإيقاف التنبيه لهذه الفئة.")

        CardStore.categories(this).forEach { amount ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14, 12, 14, 12)
                setBackgroundColor(Color.WHITE)
            }
            row.addView(TextView(this).apply {
                text = "$amount ريال"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(30,30,30))
            })
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(StockNotification.getThreshold(this@MainActivity, amount).toString())
                hint = "مثال: 20"
                textSize = 17f
                setTextColor(Color.rgb(25,25,25))
                setHintTextColor(Color.rgb(110,110,110))
                gravity = Gravity.RIGHT
            }
            row.addView(input, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0,8,0,4) })
            row.addView(TextView(this).apply {
                text = "0 = إيقاف التنبيه لهذه الفئة"
                textSize = 13f
                setTextColor(Color.rgb(80,80,80))
            })
            row.addView(Button(this).apply {
                text = "حفظ حد $amount ريال"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(35,95,150))
                setOnClickListener {
                    val value = input.text.toString().trim().toIntOrNull()
                    if (value == null || value < 0 || value > 9999) {
                        Toast.makeText(this@MainActivity, "أدخل رقماً من 0 إلى 9999", Toast.LENGTH_LONG).show()
                    } else {
                        StockNotification.setThreshold(this@MainActivity, amount, value)
                        Toast.makeText(this@MainActivity, "تم حفظ حد التنبيه لفئة $amount ريال", Toast.LENGTH_SHORT).show()
                    }
                }
            }, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,6,0,8) })
            content.addView(row, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,0,0,10) })
        }
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
    private fun cardRow(title:String,value:String):View{return LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(18,14,18,14);setBackgroundColor(Color.WHITE);addView(TextView(this@MainActivity).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(30,30,30))},LinearLayout.LayoutParams(0,-2,1f));addView(TextView(this@MainActivity).apply{text=value;tag="stock_value";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(20,90,145))});layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,6)}}}
    private fun setupBackupSchedule(){val request=PeriodicWorkRequestBuilder<BackupWorker>(1,TimeUnit.DAYS).build();WorkManager.getInstance(this).enqueueUniquePeriodicWork("alkamel_daily_backup",ExistingPeriodicWorkPolicy.KEEP,request)}
    private fun requestPermissions(){val needed=mutableListOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS);if(android.os.Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed.add(Manifest.permission.POST_NOTIFICATIONS);val missing=needed.filter{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED};if(missing.isNotEmpty())ActivityCompat.requestPermissions(this,missing.toTypedArray(),requestCode)}
    private fun Int.dp():Int=(this*resources.displayMetrics.density).toInt()
}
