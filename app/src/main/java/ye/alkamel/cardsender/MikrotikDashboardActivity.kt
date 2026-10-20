package ye.alkamel.cardsender

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

class MikrotikDashboardActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var tlsSwitch: Switch
    private lateinit var resultBox: LinearLayout
    private var connectedConfig: Config? = null

    private data class Config(val host: String, val port: Int, val username: String, val password: String, val tls: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "إدارة MikroTik"
        window.statusBarColor = Color.rgb(14, 67, 111)
        buildScreen()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        root.addView(text("لوحة تحكم MikroTik", 24, true))
        root.addView(text("اتصال محلي مباشر بالراوتر. لا تُحفظ كلمة المرور على الهاتف.", 14, false))
        hostInput = field("عنوان الراوتر، مثل 192.168.88.1")
        portInput = field("منفذ API-SSL: 8729")
        portInput.setText("8729")
        userInput = field("اسم مستخدم MikroTik")
        passInput = field("كلمة مرور MikroTik")
        passInput.inputType = 129
        tlsSwitch = Switch(this).apply {
            text = "استخدام API-SSL المشفّر (موصى به)"
            isChecked = true
            setTextColor(Color.DKGRAY)
        }
        root.addView(tlsSwitch)
        root.addView(button("اتصال وفحص الراوتر", true) { connectAndLoad() })
        root.addView(button("مسح كلمة المرور", false) { passInput.setText("") })
        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(resultBox)
    }

    private fun connectAndLoad() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().trim().toIntOrNull()
        val username = userInput.text.toString().trim()
        val password = passInput.text.toString()
        val tls = tlsSwitch.isChecked
        if (host.isBlank() || port == null || port !in 1..65535 || username.isBlank() || password.isBlank()) {
            message("أدخل عنوان الراوتر والمنفذ واسم المستخدم وكلمة المرور.")
            return
        }
        val cfg = Config(host, port, username, password, tls)
        resultBox.removeAllViews()
        resultBox.addView(text("جارٍ الاتصال بالراوتر…", 16, true))
        executor.execute {
            try {
                val api = client(cfg)
                val identity = api.command("/system/identity/print").firstOrNull()?.attributes.orEmpty()
                val resource = api.command("/system/resource/print").firstOrNull()?.attributes.orEmpty()
                val interfaces = api.command("/interface/print")
                val queryWarnings = mutableListOf<String>()
                val pppActive = safeQuery(api, "/ppp/active/print", queryWarnings)
                val hsActive = safeQuery(api, "/ip/hotspot/active/print", queryWarnings)
                val pppSecrets = safeQuery(api, "/ppp/secret/print", queryWarnings)
                val hsUsers = safeQuery(api, "/ip/hotspot/user/print", queryWarnings)
                connectedConfig = cfg
                runOnUiThread {
                    resultBox.removeAllViews()
                    resultBox.addView(text("تم الاتصال بنجاح", 19, true, Color.rgb(28, 120, 75)))
                    resultBox.addView(text("الراوتر: " + (identity["name"] ?: host), 16, true))
                    resultBox.addView(text("RouterOS: " + (resource["version"] ?: "غير متاح"), 14, false))
                    resultBox.addView(text("مدة التشغيل: " + (resource["uptime"] ?: "غير متاحة"), 14, false))
                    val memory = resource["free-memory"]?.toLongOrNull()?.div(1024)?.div(1024)?.toString() ?: "?"
                    resultBox.addView(text("المعالج: " + (resource["cpu-load"] ?: "?") + "% • الذاكرة الحرة: " + memory + " MB", 14, false))
                    resultBox.addView(text("واجهات الشبكة: " + interfaces.size, 14, false))
                    resultBox.addView(text("جلسات PPP: " + pppActive.size + " • جلسات HotSpot: " + hsActive.size, 14, false))
                    if (queryWarnings.isNotEmpty()) {
                        resultBox.addView(text(
                            "تنبيه: تعذرت قراءة بعض القوائم؛ الأعداد أو الحسابات المعروضة قد تكون غير مكتملة. " +
                                queryWarnings.distinct().joinToString(" • "),
                            14, true, Color.rgb(175, 95, 20)
                        ))
                    }
                    resultBox.addView(button("تحديث البيانات", false) { connectAndLoad() })
                    resultBox.addView(text("المشتركون المتصلون", 20, true))
                    addActiveUsers(pppActive, "PPP")
                    addActiveUsers(hsActive, "HotSpot")
                    resultBox.addView(text("حسابات PPP القابلة للتعديل", 20, true))
                    addAccountUsers(pppSecrets, "PPP")
                    resultBox.addView(text("حسابات HotSpot القابلة للتعديل", 20, true))
                    addAccountUsers(hsUsers, "HotSpot")
                    Toast.makeText(this, "تم تحميل بيانات الراوتر", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    resultBox.removeAllViews()
                    resultBox.addView(text("تعذر الاتصال: " + (e.message ?: "خطأ غير معروف"), 16, true, Color.rgb(175, 45, 45)))
                    resultBox.addView(text("تحقق من عنوان الراوتر، تفعيل خدمة API-SSL، الشهادة، المنفذ، وصلاحيات الحساب. لا تفتح منفذ الإدارة على الإنترنت.", 14, false))
                }
            }
        }
    }

    private fun safeQuery(
        api: MikrotikApiClient,
        path: String,
        warnings: MutableList<String>
    ): List<Map<String, String>> = try {
        api.command(path).map { it.attributes }
    } catch (e: Exception) {
        warnings.add(path.substringAfterLast("/") + ": " + (e.message ?: "تعذرت القراءة"))
        emptyList()
    }

    private fun addActiveUsers(rows: List<Map<String, String>>, kind: String) {
        if (rows.isEmpty()) {
            resultBox.addView(text("لا توجد جلسات " + kind + " أو تعذّر قراءتها.", 14, false))
            return
        }
        rows.forEach { row ->
            val id = row[".id"].orEmpty()
            val name = row["name"] ?: row["user"] ?: "مستخدم غير معروف"
            val detail = listOfNotNull(row["address"], row["caller-id"], row["service"]).joinToString(" • ")
            val card = panel()
            card.addView(text(name + "\n" + detail, 15, true))
            card.addView(button("فصل الجلسة", false) {
                AlertDialog.Builder(this)
                    .setTitle("تأكيد فصل المشترك")
                    .setMessage("هل تريد فصل جلسة " + name + " من " + kind + "؟ لن يُحذف حسابه.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("فصل") { _, _ ->
                        runOperation("فصل الجلسة") { api ->
                            val path = if (kind == "PPP") "/ppp/active/remove" else "/ip/hotspot/active/remove"
                            api.command(path, mapOf(".id" to id))
                        }
                    }
                    .show()
            })
            resultBox.addView(card)
        }
    }

    private fun addAccountUsers(rows: List<Map<String, String>>, kind: String) {
        if (rows.isEmpty()) {
            resultBox.addView(text("لا توجد حسابات " + kind + " أو تعذّر قراءتها.", 14, false))
            return
        }
        rows.forEach { row ->
            val id = row[".id"].orEmpty()
            val name = row["name"] ?: return@forEach
            val detail = listOfNotNull(row["profile"], row["service"], row["disabled"]?.let { if (it == "true") "معطّل" else "مفعّل" }).joinToString(" • ")
            val card = panel()
            card.addView(text(name + "\n" + detail, 15, true))
            card.addView(button("تعديل الحساب", false) { editAccount(kind, id, name, row["profile"].orEmpty()) })
            val disabled = row["disabled"] == "true"
            card.addView(button(if (disabled) "تفعيل الحساب" else "تعطيل الحساب", false) {
                AlertDialog.Builder(this)
                    .setTitle("تأكيد تغيير حالة الحساب")
                    .setMessage("سيتم " + (if (disabled) "تفعيل" else "تعطيل") + " حساب " + name + " على الراوتر.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("تأكيد") { _, _ ->
                        runOperation("تغيير حالة الحساب") { api ->
                            val path = if (kind == "PPP") "/ppp/secret/set" else "/ip/hotspot/user/set"
                            api.command(path, mapOf(".id" to id, "disabled" to if (disabled) "no" else "yes"))
                        }
                    }
                    .show()
            })
            resultBox.addView(card)
        }
    }

    private fun editAccount(kind: String, id: String, oldName: String, oldProfile: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), 0)
        }
        val name = field("اسم المستخدم").apply { setText(oldName) }
        val profile = field("Profile").apply { setText(oldProfile) }
        val password = field("كلمة مرور جديدة (اختياري)").apply { inputType = 129 }
        layout.addView(name); layout.addView(profile); layout.addView(password)
        AlertDialog.Builder(this)
            .setTitle("تعديل حساب " + kind)
            .setView(layout)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                val newName = name.text.toString().trim()
                val newProfile = profile.text.toString().trim()
                if (newName.isBlank()) { message("اسم المستخدم لا يمكن أن يكون فارغًا."); return@setPositiveButton }
                val attrs = linkedMapOf(".id" to id, "name" to newName, "profile" to newProfile)
                if (password.text.isNotEmpty()) attrs["password"] = password.text.toString()
                runOperation("تعديل حساب " + kind) { api ->
                    api.command(if (kind == "PPP") "/ppp/secret/set" else "/ip/hotspot/user/set", attrs)
                }
            }
            .show()
    }

    private fun runOperation(label: String, operation: (MikrotikApiClient) -> Any) {
        val cfg = connectedConfig
        if (cfg == null) { message("اتصل بالراوتر أولًا."); return }
        AlertDialog.Builder(this)
            .setTitle("تأكيد العملية")
            .setMessage("سيتم تنفيذ «" + label + "» على الراوتر مباشرة. تأكد من أن لديك صلاحية الإدارة.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("متابعة") { _, _ ->
                Toast.makeText(this, "جارٍ تنفيذ العملية…", Toast.LENGTH_SHORT).show()
                executor.execute {
                    try {
                        operation(client(cfg))
                        runOnUiThread {
                            message("تم إرسال أمر «" + label + "» إلى الراوتر. أعد تحميل البيانات للتحقق من النتيجة.")
                            connectAndLoad()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { message("فشلت العملية: " + (e.message ?: "خطأ غير معروف")) }
                    }
                }
            }
            .show()
    }

    private fun client(c: Config) = MikrotikApiClient(c.host, c.port, c.username, c.password, c.tls)
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.WHITE)
    }
    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        singleLine = true
        setTextColor(Color.DKGRAY)
        setHintTextColor(Color.GRAY)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(210, 220, 230))
            cornerRadius = dp(8).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) }
    }
    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) Color.WHITE else Color.rgb(20, 83, 130))
        setBackgroundColor(if (primary) Color.rgb(20, 92, 148) else Color.rgb(230, 239, 247))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(5) }
    }
    private fun text(value: String, size: Int, bold: Boolean, color: Int = Color.rgb(35, 45, 55)) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
        gravity = Gravity.START
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun message(value: String) = AlertDialog.Builder(this).setMessage(value).setPositiveButton("حسنًا", null).show()
    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
