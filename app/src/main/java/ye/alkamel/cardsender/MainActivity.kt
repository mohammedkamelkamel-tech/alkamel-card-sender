package ye.alkamel.cardsender

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private val requestCode = 700
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        val title = TextView(this).apply {
            text = "إدارة كروت الكامل"
            textSize = 26f
            setPadding(0, 0, 0, 20)
        }

        status = TextView(this).apply { textSize = 16f }

        val info = TextView(this).apply {
            text = """
                التطبيق يراقب رسائل Jawali و Jaib تلقائياً.

                المبالغ المدعومة:
                99 / 100 / 200 / 245 / 250 / 500

                يتم أخذ أول كرت من ملف المبلغ وإرساله إلى رقم الجوال الموجود في الرسالة، ثم حذف الكرت من الملف.

                مجلد الكروت:
                Android/data/ye.alkamel.cardsender/files/cards/

                الملفات:
                100.txt
                200.txt
                250.txt
                500.txt
            """.trimIndent()
            textSize = 15f
            setPadding(0, 10, 0, 20)
        }

        val button = Button(this).apply {
            text = "إنشاء ملفات الكروت"
            setOnClickListener {
                CardStore.initializeFiles(this@MainActivity)
                updateStatus()
                Toast.makeText(this@MainActivity, "تم تجهيز ملفات الكروت", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(info)
        layout.addView(button)
        layout.addView(status)
        setContentView(layout)

        requestSmsPermissions()
        CardStore.initializeFiles(this)
        updateStatus()
    }

    private fun requestSmsPermissions() {
        val needed = mutableListOf<String>()
        listOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), requestCode)
        }
    }

    private fun updateStatus() {
        val files = CardStore.supportedAmounts.joinToString("\n") { amount ->
            val count = CardStore.count(this, amount)
            "$amount.txt : $count كرت"
        }
        status.text = "حالة المخزون:\n$files"
    }
}
