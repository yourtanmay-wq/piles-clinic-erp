package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.tkbiswas.pilesclinic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/**
 * Native rebuild of the WebView medicinePaymentHome() / saveMedicinePayment():
 * records a medicine/product sale into the "products" table with the same
 * fields and formula (due = max(0, bill - deposit), mode CASH/ONLINE), and lists
 * the medicine payment history below, scoped to the user's branch.
 */
class MedicinePaymentActivity : AppCompatActivity() {

    private lateinit var etCustomer: EditText
    private lateinit var btnBranchChip: TextView
    private lateinit var customerSuggestions: LinearLayout
    private lateinit var etProduct: EditText
    private lateinit var etBill: EditText
    private lateinit var etDeposit: EditText
    private lateinit var etRemarks: EditText
    private lateinit var historyContainer: LinearLayout
    private lateinit var progressLoad: ProgressBar
    private lateinit var tvEmpty: TextView

    // 🔒 B558-B560 (08.08.2026, TK-অনুমোদিত প্রুফ) — একাধিক মেডিসিন,
    // Save/Share/Print, প্রফেশনাল History-র নতুন ঘরগুলো।
    private lateinit var medicineContainer: LinearLayout
    private lateinit var btnAddMedicine: TextView
    private lateinit var etHistorySearch: EditText
    private lateinit var chipToday: TextView
    private lateinit var chip7: TextView
    private lateinit var chip30: TextView
    private lateinit var chipPick: TextView
    private lateinit var tvHistoryTotal: TextView

    // History ছাঁকা (সব client-side, কোনো বাড়তি সার্ভার-কল নয়): সর্বশেষ
    // ক্লাউড/ক্যাশ থেকে নামানো পূর্ণ তালিকা এখানে রাখি, তারপর ব্রাঞ্চ + তারিখ +
    // সার্চ প্রয়োগ করে দেখাই।
    private var loadedRows: org.json.JSONArray = org.json.JSONArray()
    private var userBranchFilter: String = "All"
    private var activeDateFilter: String = "today"   // today | 7 | 30 | pick
    private var pickedDate: String? = null            // yyyy-MM-dd
    private var searchQuery: String = ""

    private val branches = listOf("Kishanganj", "Jalpaiguri", "Cooch Behar", "Falakata", "Birpara")
    private val modes = listOf("CASH", "ONLINE")

    // TK-REQUESTED REDESIGN (2026-07-20): Branch is now a compact tappable
    // chip (see activity_medicine_payment.xml) instead of a Spinner --
    // selection is tracked here the same way spBranch.selectedItem was read.
    private var selectedBranch = "Kishanganj"
    private var selectedMpMode = "CASH"

    // 🔒 B619 (11.08.2026, TK-নির্দেশ): ওষুধ বিক্রিও টাকা — তাই নিজের ব্রাঞ্চ ছাড়া
    // অন্য ব্রাঞ্চ দেখা/বেছে নেওয়া যাবে না (master সব ব্রাঞ্চ পারবেন)। MoneyBranchGuard-এর
    // হুবহু একই নিয়ম, শুধু এখানে UI-তেই লক করা হয় যাতে অন্য ব্রাঞ্চ চোখেই না পড়ে।
    private var isMasterUser = false

    // TK-REPORTED CRITICAL BUG FIX (2026-07-26): true only while a Medicine
    // Payment save is actually in flight, so a second tap on Save during a
    // slow upload can never write a second "products" row for the same money.
    private var mpSaving = false

    private fun setupBranchChip(defaultBranch: String) {
        selectedBranch = defaultBranch
        // 🔒 B619 (11.08.2026, TK-নির্দেশ): master ছাড়া কেউ অন্য ব্রাঞ্চ দেখতেই পারবে না —
        // চিপ তালাবন্ধ (▾ নেই, ট্যাপে কিছু হয় না), শুধু নিজের ব্রাঞ্চ দেখায়।
        if (!isMasterUser) {
            btnBranchChip.text = "🏥 $selectedBranch"
            btnBranchChip.isClickable = false
            btnBranchChip.setOnClickListener(null)
            return
        }
        btnBranchChip.text = "🏥 $selectedBranch ▾"
        btnBranchChip.setOnClickListener {
            // 🔒 খাতার সারি B84 — Follow-up-এর হুবহু একই পপ-আপ।
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Branch"))
                .setSingleChoiceItems(branches.toTypedArray(), branches.indexOf(selectedBranch)) { dialog, which ->
                    selectedBranch = branches[which]
                    btnBranchChip.text = "🏥 $selectedBranch ▾"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    // TK-REQUESTED ADDITION (2026-07-20): as the person types a name/mobile,
    // show matching registered patients below the field to tap-select --
    // reuses the exact same PaymentRepository.searchPatients() already built
    // (and TK-approved) for Payment Collection's Search Patient dialog, so
    // the matching logic is identical between the two screens.
    private var customerSearchJob: kotlinx.coroutines.Job? = null
    private fun setupCustomerSuggestions() {
        val repo = PaymentRepository(this)
        etCustomer.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val q = s?.toString().orEmpty()
                customerSearchJob?.cancel()
                if (q.trim().length < 2) { customerSuggestions.removeAllViews(); return }
                customerSearchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(250)
                    val matches = try {
                        withContext(Dispatchers.IO) { repo.searchPatients(q) }
                    } catch (t: Throwable) { emptyList() }
                    customerSuggestions.removeAllViews()
                    val density = resources.displayMetrics.density
                    fun dp(v: Int) = (v * density).toInt()
                    for (m in matches.take(5)) {
                        val mobileDigits = m.mobile.filter { it.isDigit() }.takeLast(10)
                        val row = TextView(this@MedicinePaymentActivity).apply {
                            text = PatientIdText.line(m.name, mobileDigits, m.patientId, phoneIcon = true)
                            textSize = 12.5f
                            setPadding(dp(10), dp(8), dp(10), dp(8))
                            setBackgroundColor(android.graphics.Color.parseColor("#F2F8F4"))
                            setTextColor(android.graphics.Color.parseColor("#0A5428"))
                            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.bottomMargin = dp(4)
                            layoutParams = lp
                            setOnClickListener {
                                etCustomer.setText(m.name.ifBlank { mobileDigits })
                                etCustomer.setSelection(etCustomer.text?.length ?: 0)
                                // 🔒 B619: শুধু master-এর ক্ষেত্রে রোগীর ব্রাঞ্চ ধরে চিপ বদলায়;
                                // নন-মাস্টার সবসময় নিজের ব্রাঞ্চে তালাবন্ধ থাকে।
                                if (isMasterUser && m.branch.isNotBlank() && branches.contains(m.branch)) {
                                    selectedBranch = m.branch
                                    btnBranchChip.text = "🏥 $selectedBranch ▾"
                                }
                                customerSuggestions.removeAllViews()
                            }
                        }
                        customerSuggestions.addView(row)
                    }
                }
            }
        })
    }

    private fun setupMpModeButtons() {
        val cash = findViewById<android.widget.Button>(R.id.btnMpCash)
        val upi = findViewById<android.widget.Button>(R.id.btnMpUpi)
        val blue = android.graphics.Color.parseColor("#1167D8")
        val lightBlue = android.graphics.Color.parseColor("#E8F2FF")
        fun render() {
            cash.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedMpMode == "CASH") blue else lightBlue)
            cash.setTextColor(if (selectedMpMode == "CASH") android.graphics.Color.WHITE else blue)
            upi.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedMpMode == "ONLINE") blue else lightBlue)
            upi.setTextColor(if (selectedMpMode == "ONLINE") android.graphics.Color.WHITE else blue)
        }
        cash.setOnClickListener { selectedMpMode = "CASH"; render() }
        upi.setOnClickListener { selectedMpMode = "ONLINE"; render() }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medicine_payment)
        UppercaseInputUtil.applyToAll(window.decorView.findViewById(android.R.id.content))  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        BottomNav.wire(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etCustomer = findViewById(R.id.etCustomer)
        btnBranchChip = findViewById(R.id.btnBranchChip)
        customerSuggestions = findViewById(R.id.customerSuggestions)
        etProduct = findViewById(R.id.etProduct)
        etBill = findViewById(R.id.etBill)
        etDeposit = findViewById(R.id.etDeposit)
        etRemarks = findViewById(R.id.etRemarks)
        historyContainer = findViewById(R.id.historyContainer)
        progressLoad = findViewById(R.id.progressLoad)
        tvEmpty = findViewById(R.id.tvEmpty)
        medicineContainer = findViewById(R.id.medicineContainer)
        btnAddMedicine = findViewById(R.id.btnAddMedicine)
        etHistorySearch = findViewById(R.id.etHistorySearch)
        chipToday = findViewById(R.id.chipToday)
        chip7 = findViewById(R.id.chip7)
        chip30 = findViewById(R.id.chip30)
        chipPick = findViewById(R.id.chipPick)
        tvHistoryTotal = findViewById(R.id.tvHistoryTotal)

        // 🔒 B619: ব্রাঞ্চ-লক করার আগে জেনে নিই এই ইউজার master কিনা (master সব ব্রাঞ্চ পারবেন)।
        isMasterUser = NativeSession.current(this)?.role == "master"

        setupMpModeButtons()
        setupCustomerSuggestions()
        setupAddMedicine()
        setupHistoryControls()

        val user = NativeSession.current(this)
        val defaultBranch = if (user != null && user.branch != "All" && branches.contains(user.branch)) user.branch else branches.first()
        setupBranchChip(defaultBranch)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).setOnClickListener {
            // TK-REPORTED CRITICAL BUG FIX (2026-07-26): same duplicate-money
            // guard as the Advance / Nth Payment / Payment-screen buttons. This
            // one writes a "products" row (Medicine Payment) with a brand new
            // id on every tap, so a second impatient tap during a slow save
            // charged the customer twice. Cleared in commit()'s finally block.
            if (mpSaving) return@setOnClickListener
            save(user?.mobile ?: "", user?.branch ?: "", null)
        }
        // 🔒 B559 (08.08.2026, TK-অনুমোদিত প্রুফ) — Share/Print চাপলে আগে অটো-সেভ,
        // তারপর কাজ। সেভ সফল হলে ফর্ম খালি হয়, তাই দ্বিতীয়বার চাপলে খালি ফর্ম
        // যাচাইয়ে আটকে যায় — এক বিক্রি কখনও দুবার সেভ হয় না (mpSaving গার্ডও আছে)।
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShare).setOnClickListener {
            if (mpSaving) return@setOnClickListener
            save(user?.mobile ?: "", user?.branch ?: "") { row -> shareReceipt(row) }
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPrint).setOnClickListener {
            if (mpSaving) return@setOnClickListener
            save(user?.mobile ?: "", user?.branch ?: "") { row -> printReceipt(row) }
        }

        loadHistory(user?.branch ?: "All")
    }

    // 🔒 B558 — "+ ADD MEDICINE": নতুন ওষুধ-ঘর যোগ, ✕ দিয়ে মোছা।
    private fun setupAddMedicine() {
        btnAddMedicine.setOnClickListener { addMedicineRow(focus = true) }
    }

    private fun addMedicineRow(focus: Boolean) {
        val row = layoutInflater.inflate(R.layout.item_medicine_row, medicineContainer, false)
        val del = row.findViewById<TextView>(R.id.btnMedDel)
        del.setOnClickListener { medicineContainer.removeView(row) }
        medicineContainer.addView(row)
        UppercaseInputUtil.applyToAll(row)  // একই গ্লোবাল নিয়ম: ইংরেজি লেখা বড় হাতের
        if (focus) row.findViewById<EditText>(R.id.etMedItem).requestFocus()
    }

    /** medicineContainer-এর সব ওষুধ-ঘর (প্রথম etProduct + যোগ-করা সব সারি) থেকে
     *  নাম নিয়ে, ফাঁকা বাদ দিয়ে, কমা দিয়ে জুড়ে একটাই স্ট্রিং — পুরনো একক-product
     *  ডেটা-মডেল হুবহু অটুট থাকে। */
    private fun collectMedicines(): String {
        val names = ArrayList<String>()
        fun scan(v: View) {
            when (v) {
                is EditText -> { val t = v.text?.toString()?.trim().orEmpty(); if (t.isNotBlank()) names.add(t) }
                is android.view.ViewGroup -> for (i in 0 until v.childCount) scan(v.getChildAt(i))
            }
        }
        scan(medicineContainer)
        return names.joinToString(", ")
    }

    private fun clearMedicineRows() {
        // প্রথম ঘর (etProduct) রেখে যোগ-করা সব সারি সরাই, তারপর প্রথম ঘর খালি করি।
        for (i in medicineContainer.childCount - 1 downTo 0) {
            if (medicineContainer.getChildAt(i).id != R.id.etProduct) medicineContainer.removeViewAt(i)
        }
        etProduct.setText("")
    }

    private fun save(staffMobile: String, userBranch: String, postAction: ((JSONObject) -> Unit)? = null) {
        // 🔒 B558 — একাধিক ঘরের সব ওষুধ কমা দিয়ে জুড়ে একটাই product।
        val product = collectMedicines()
        val bill = etBill.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val deposit = etDeposit.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
        val vmsg = FieldError.validate(listOf<Triple<View, Boolean, String>>(
            Triple(etProduct, product.isNotBlank(), "Product দিন"),
            Triple(etBill, bill > 0, "সঠিক Bill দিন"),
            Triple(etDeposit, deposit > 0, "সঠিক Deposit দিন")
        ))
        if (vmsg != null) {
            Toast.makeText(this, vmsg, Toast.LENGTH_SHORT).show()
            return
        }
        if (deposit > bill) {
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "⚠️ Overpaying"))
                .setMessage("Deposit is more than bill. Continue?")
                .setPositiveButton("Continue") { _, _ -> commit(staffMobile, product, bill, deposit, postAction) }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
            return
        }
        commit(staffMobile, product, bill, deposit, postAction)
    }

    private fun commit(staffMobile: String, product: String, bill: Double, deposit: Double, postAction: ((JSONObject) -> Unit)? = null) {
        val branch = selectedBranch
        val customer = etCustomer.text?.toString()?.trim().orEmpty().ifBlank { "Walk-in" }
val mode = selectedMpMode
        val row = JSONObject()
            .put("id", "prd_" + UUID.randomUUID().toString().replace("-", ""))
            .put("kind", "medicinePayment")
            .put("customer", customer)
            .put("product", product)
            .put("bill", bill)
            .put("total", bill)
            .put("deposit", deposit)
            .put("due", max(0.0, bill - deposit))
            .put("mode", mode)
            .put("remarks", etRemarks.text?.toString()?.trim().orEmpty())
            .put("date", today())
            .put("branch", branch)
            .put("receivedBy", staffMobile)
            .put("createdBy", staffMobile)
            .put("createdAt", isoNow())
            .put("updatedAt", isoNow())
        progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
        mpSaving = true
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { SupabaseClient.upsert("products", row) }
            } finally { mpSaving = false }
            progressLoad.visibility = View.GONE
            // 🔒 TK'S PERMANENT RULE (28.07.2026, khata row B25): what THIS
            // phone saved must show on THIS phone straight away. Noted down
            // only once the cloud has ACCEPTED the sale -- on a failure the
            // form deliberately stays filled so the staff can save again, and
            // noting a failed row here could then leave two sales on screen.
            // Display only: the sending itself is completely unchanged.
            if (ok) {
                try {
                    MyPhoneWrites.remember(
                        this@MedicinePaymentActivity, MP_TABLE, row.optString("id"), row
                    )
                } catch (_: Throwable) { }
            }
            Toast.makeText(
                this@MedicinePaymentActivity,
                if (ok) "Medicine payment saved" else "Saved failed — check connection",
                Toast.LENGTH_SHORT
            ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
            if (ok) {
                // Share/Print-এর জন্য ফর্ম খালি করার আগেই সেভ-হওয়া row হাতে
                // ধরিয়ে দিই — তখন WhatsApp টেক্সট/প্রিন্ট রসিদ ওই row থেকেই বানে।
                postAction?.invoke(row)
                clearMedicineRows()
                etBill.setText(""); etDeposit.setText(""); etRemarks.setText(""); etCustomer.setText("")
                customerSuggestions.removeAllViews()
                loadHistory(NativeSession.current(this@MedicinePaymentActivity)?.branch ?: "All")
            }
        }
    }

        private var firstResume = true
    override fun onResume() {
        super.onResume()
        if (firstResume) { firstResume = false; return }
        loadHistory(NativeSession.current(this)?.branch ?: "All")
    }

    // TK-REQUESTED ADDITION (2026-07-20): same "show what was already on the
    // phone instantly" pattern added to other screens today. Raw JSONArray
    // is cached directly (no data-class mapping needed here since the rows
    // are already read as plain JSONObject) -- the fetch/filter logic below
    // is otherwise completely unchanged.
    /** Note-book key for this screen's rows. The table is shared ("products"),
     *  so the kind is part of the key -- a medicine sale can never mix with
     *  any other kind of product row. */
    private val MP_TABLE = "products_medicinePayment"

    private fun loadCachedHistory(): org.json.JSONArray? {
        val json = getSharedPreferences("medicine_payment_history_cache", MODE_PRIVATE).getString("cache", null) ?: return null
        // 🔒 TK'S PERMANENT RULE (28.07.2026, khata row B25): the stored list is
        // what stays on screen on a weak line, so a sale just taken on THIS
        // phone used to be missing from it. This phone's own rows are put back
        // on top; each note retires itself once the cloud copy is equally new.
        return try { MyPhoneWrites.overlay(this, MP_TABLE, org.json.JSONArray(json), addNewAtTop = true) } catch (t: Throwable) { null }
    }

    private fun saveCachedHistory(rows: org.json.JSONArray) {
        try {
            getSharedPreferences("medicine_payment_history_cache", MODE_PRIVATE).edit().putString("cache", rows.toString()).apply()
        } catch (_: Throwable) { }
    }

    // 🔒 B560 — নামানো পূর্ণ তালিকা মনে রাখি, তারপর ব্রাঞ্চ + তারিখ + সার্চ
    // (সব client-side) প্রয়োগ করে দেখাই। বাড়তি কোনো সার্ভার-কল নেই।
    private fun showRows(rows: org.json.JSONArray, userBranch: String) {
        loadedRows = rows
        userBranchFilter = userBranch
        applyFilters()
    }

    /** History-র সব ছাঁকা এক জায়গায়: ব্রাঞ্চ → তারিখ-চিপ → সার্চ; তারপর
     *  কার্ড আঁকা ও উপরে মোট বিক্রি বসানো। */
    private fun applyFilters() {
        val today = today()
        val start7 = dateNDaysAgo(6)
        val start30 = dateNDaysAgo(29)
        val q = searchQuery.trim().lowercase()

        /* 🔴🔒 V509 (TK-রিপোর্ট ২১.০৮.২০২৬ — "স্ক্রিন কম্পন দিচ্ছে"):
           এই তালিকা আগে **দুবার** আঁকত (প্রথমে ফোনে জমানো, তারপর ক্লাউড থেকে
           এসে আবার) — বেশিরভাগ সময় দুটো হুবহু এক, তাই দ্বিতীয়বার মুছে-আঁকাটাই
           চোখে ঝিলিক লাগত।
           ⛔ চিহ্নে **ছাঁকনির সব অবস্থাও** ধরা আছে (ব্রাঞ্চ · তারিখ · বাছাই
              তারিখ · খোঁজা লেখা) — তাই ছাঁকনি বদলালে আগের মতোই পুরো আঁকে,
              কিছু আটকে থাকে না। */
        if (com.tkbiswas.pilesclinic.native.RedrawGuard.alreadyShowing(
                historyContainer,
                loadedRows.toString() + "|" + userBranchFilter + "|" +
                    activeDateFilter + "|" + (pickedDate ?: "") + "|" + q)) return

        historyContainer.removeAllViews()
        var shown = 0
        var totalCollected = 0.0
        var cashCollected = 0.0
        var onlineCollected = 0.0

        for (i in 0 until loadedRows.length()) {
            val r = loadedRows.optJSONObject(i) ?: continue
            val br = r.s("branch")
            if (userBranchFilter != "All" && br.isNotBlank() && br != userBranchFilter) continue

            // তারিখ-ঘর: সঞ্চিত "date" (yyyy-MM-dd) দিয়ে তুলনা (স্ট্রিং তুলনা নিরাপদ)।
            val d = r.s("date")
            val dateOk = when (activeDateFilter) {
                "today" -> d == today
                "7" -> d.isNotBlank() && d >= start7
                "30" -> d.isNotBlank() && d >= start30
                "pick" -> pickedDate != null && d == pickedDate
                else -> true
            }
            if (!dateOk) continue

            if (q.isNotEmpty()) {
                val hay = (r.s("customer") + " " + r.s("product") + " " + r.s("mobile") + " " + r.s("receivedBy")).lowercase()
                if (!hay.contains(q)) continue
            }

            historyContainer.addView(historyCard(r))
            shown++
            val dep = r.optDouble("deposit", 0.0)
            totalCollected += dep
            if (r.s("mode") == "ONLINE") onlineCollected += dep else cashCollected += dep
        }

        renderTotal(shown, totalCollected, cashCollected, onlineCollected)
        renderChips()
        if (shown == 0) {
            tvEmpty.text = if (loadedRows.length() == 0) "No medicine payment yet." else "No sale in this filter."
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    private fun loadHistory(userBranch: String) {
        var hadCache = false
        val cached = loadCachedHistory()
        if (cached != null && cached.length() > 0) {
            hadCache = true
            progressLoad.visibility = View.GONE
            showRows(cached, userBranch)
        } else {
            progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
            // TK-REQUESTED (2026-07-24): only reachable on first-ever open
            // (no cache yet) -- plain "Loading..." instead of blank.
            tvEmpty.text = "Loading..."
            tvEmpty.visibility = View.VISIBLE
            historyContainer.removeAllViews()
        }
        lifecycleScope.launch {
            // TK-REQUESTED FIX (2026-07-20): this had no error handling --
            // needed so a fetch failure after cached data was shown leaves
            // that cached data on screen instead of crashing/going blank.
            // TK-REPORTED BUG FIX (2026-07-23): this try/catch never
            // actually caught a network failure -- SupabaseClient.fetchList()
            // swallows its own exceptions internally and returns an empty
            // (non-null) JSONArray, so "rows" was never null on a failed
            // fetch, meaning the code below still ran saveCachedHistory()
            // with a wrongly-empty result, poisoning the cache. Switched to
            // fetchListOrNull(), which actually returns null on a genuine
            // failure, so that case is now correctly detected and the
            // cache is left untouched instead of being overwritten with
            // wrong empty data.
            val rows = try {
                withContext(Dispatchers.IO) {
                    SupabaseClient.fetchListOrNull("products", "kind=eq.medicinePayment", 500)
                }
            } catch (t: Throwable) {
                null
            }
            progressLoad.visibility = View.GONE
            if (rows == null) {
                if (!hadCache) {
                    tvEmpty.text = "No medicine payment yet."
                    tvEmpty.visibility = View.VISIBLE
                }
                return@launch
            }
            saveCachedHistory(rows)
            // The cache above is saved from the UNTOUCHED cloud rows; only what
            // is shown gets this phone's own sales put on top (same rule as
            // loadCachedHistory).
            showRows(
                try { MyPhoneWrites.overlay(this@MedicinePaymentActivity, MP_TABLE, rows, addNewAtTop = true) } catch (_: Throwable) { rows },
                userBranch
            )
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 🔒 B560 (08.08.2026, TK-অনুমোদিত প্রুফ) — প্রফেশনাল History কার্ড:
     *  রোগীর নাম + মোড-ব্যাজ + কালেক্ট টাকা, ওষুধ, 🏥 ব্রাঞ্চ · 👤 কোন স্টাফ নিল
     *  · ⏰ তারিখ-সময়, নিচে Bill/Deposit/Due। কোনো ডেটা বদলায় না — শুধু দেখানো। */
    private fun historyCard(r: JSONObject): View {
        val bill = r.optDouble("bill", r.optDouble("total", 0.0))
        val deposit = r.optDouble("deposit", 0.0)
        val due = max(0.0, bill - deposit)
        val mode = r.s("mode").ifBlank { "CASH" }
        val cust = r.s("customer").ifBlank { r.s("mobile").ifBlank { "Walk-in" } }
        val meds = r.s("product").ifBlank { "—" }
        val branch = r.s("branch").ifBlank { "—" }
        val staffMobile = r.s("receivedBy").ifBlank { r.s("createdBy") }
        val staffName = StaffDirectory.findAccount(staffMobile)?.name?.takeIf { it.isNotBlank() }
            ?: staffMobile.ifBlank { "—" }
        val whenStr = r.s("createdAt").let { if (it.isNotBlank()) DateUtil.displayWithTime(it) else DateUtil.display(r.s("date")) }

        val navy = 0xFF0B2B59.toInt()
        val green = 0xFF0B7A34.toInt()
        val grey = 0xFF475467.toInt()
        val faint = 0xFF8A949E.toInt()

        // বাইরের সবুজ প্রান্ত + ভেতরে সাদা রাউন্ড কার্ড
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(green)
            }
            setPadding(dp(4), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat(); setColor(0xFFFFFFFF.toInt())
                setStroke(dp(1), 0xFFE6ECF3.toInt())
            }
            setPadding(dp(11), dp(9), dp(11), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // সারি ১: নাম + মোড-ব্যাজ  ......  কালেক্ট টাকা
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(this).apply {
            text = cust; textSize = 13.5f; setTextColor(navy)
            setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        left.addView(modeBadge(mode))
        row1.addView(left)
        row1.addView(TextView(this).apply {
            text = "₹ ${fmt(deposit)}"; textSize = 13.5f; setTextColor(green)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(row1)

        // সারি ২: ওষুধ
        card.addView(TextView(this).apply {
            text = "💊 $meds"; textSize = 12f; setTextColor(grey)
            setPadding(0, dp(3), 0, 0)
        })

        // সারি ৩: 🏥 ব্রাঞ্চ · 👤 কোন স্টাফ · ⏰ তারিখ-সময়
        // ⛔ B313/B202 শিক্ষা: ⏰/🕐 ইমোজি ফোনের ফন্ট নিজে "Jul 17"/"1:00" এঁকে দেয়
        //    (আসল ডেটা নয়) — তাই সময়ের আইকনে প্রমাণিত-নিরাপদ ⏰ (কোনো সংখ্যা আঁকা নেই)।
        card.addView(TextView(this).apply {
            text = "🏥 $branch    👤 $staffName    ⏰ $whenStr"
            textSize = 10.5f; setTextColor(faint); setPadding(0, dp(3), 0, 0)
        })

        // বিভাজক
        card.addView(View(this).apply {
            setBackgroundColor(0xFFE6ECF3.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                .apply { setMargins(0, dp(6), 0, dp(5)) }
        })

        // সারি ৪: Bill / Deposit / Due
        card.addView(TextView(this).apply {
            text = "Bill ₹${fmt(bill)}  ·  Deposit ₹${fmt(deposit)}  ·  Due ₹${fmt(due)}"
            textSize = 11.5f; setTextColor(0xFF10223A.toInt())
        })

        outer.addView(card)
        return outer
    }

    private fun modeBadge(mode: String): TextView {
        val online = mode == "ONLINE"
        val bg = if (online) 0xFFE8F0FB.toInt() else 0xFFE6F4EA.toInt()
        val fg = if (online) 0xFF1F6FE0.toInt() else 0xFF0B7A34.toInt()
        return TextView(this).apply {
            text = if (online) "ONLINE" else "CASH"
            textSize = 8.5f; setTextColor(fg)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(6), dp(1), dp(6), dp(1))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(bg)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(5), 0, 0, 0) }
        }
    }

    // ───────────────────── History নিয়ন্ত্রণ (সার্চ + চিপ + মোট) ─────────────────────
    // 🔒 B560 — সব ছাঁকা আগে-নামানো ডেটার উপরেই; Supabase-এ বাড়তি কল নেই।
    private fun setupHistoryControls() {
        etHistorySearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFilters()
            }
        })
        chipToday.setOnClickListener { activeDateFilter = "today"; applyFilters() }
        chip7.setOnClickListener { activeDateFilter = "7"; applyFilters() }
        chip30.setOnClickListener { activeDateFilter = "30"; applyFilters() }
        chipPick.setOnClickListener { openDatePicker() }
        renderChips()
    }

    private fun openDatePicker() {
        val cal = java.util.Calendar.getInstance()
        pickedDate?.let {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)
                if (d != null) cal.time = d
            } catch (_: Exception) {}
        }
        android.app.DatePickerDialog(
            this,
            { _, y, m, day ->
                val c = java.util.Calendar.getInstance().apply { set(y, m, day) }
                pickedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
                activeDateFilter = "pick"
                applyFilters()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun renderChips() {
        val green = 0xFF0B7A34.toInt()
        fun paint(chip: TextView, on: Boolean) {
            if (on) {
                chip.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(green)
                }
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                chip.setBackgroundResource(R.drawable.bg_price_chip)
                chip.setTextColor(green)
            }
        }
        paint(chipToday, activeDateFilter == "today")
        paint(chip7, activeDateFilter == "7")
        paint(chip30, activeDateFilter == "30")
        paint(chipPick, activeDateFilter == "pick")
        // ⛔ B313/B202: ⏰ ইমোজি ফোনের ফন্টে "Jul 17" এঁকে দেয় (বিভ্রান্তিকর) —
        //    তাই এখানে ইমোজি ছাড়া পরিষ্কার লেখা; তারিখ বাছলে আসল তারিখ দেখায়।
        chipPick.text = if (activeDateFilter == "pick" && pickedDate != null)
            DateUtil.display(pickedDate) else "Pick Date"
    }

    private fun renderTotal(count: Int, total: Double, cash: Double, online: Double) {
        val label = when (activeDateFilter) {
            "today" -> "TODAY'S MEDICINE SALE"
            "7" -> "LAST 7 DAYS SALE"
            "30" -> "LAST 30 DAYS SALE"
            "pick" -> "SALE ON ${DateUtil.display(pickedDate)}"
            else -> "MEDICINE SALE"
        }
        val line2 = "₹ ${fmt(total)}   ·   $count ${if (count == 1) "sale" else "sales"}"
        val line3 = "Cash ₹${fmt(cash)} · Online ₹${fmt(online)}"
        val sb = android.text.SpannableStringBuilder()
        sb.append(label).append("\n")
        val s2 = sb.length
        sb.append(line2)
        sb.setSpan(android.text.style.RelativeSizeSpan(1.6f), s2, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s2, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("\n").append(line3)
        tvHistoryTotal.text = sb
    }

    private fun dateNDaysAgo(n: Int): String {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_MONTH, -n)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    // ───────────────────── Share / Print রসিদ ─────────────────────
    // 🔒 B559 — Share/Print চাপার আগে অটো-সেভ হয়ে গেছে; এখানে সেভ-হওয়া row
    // থেকেই রসিদ বানাই। কোনো নতুন ডেটা লেখা হয় না — শুধু পাঠানো/ছাপানো।
    private fun shareReceipt(r: JSONObject) {
        WhatsAppMessageChooser.sendGeneric(this, receiptText(r))
    }

    private var printWebView: android.webkit.WebView? = null
    private fun printReceipt(r: JSONObject) {
        val html = receiptHtml(r)
        try {
            val wv = android.webkit.WebView(this)
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                    try {
                        val pm = getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                        val adapter = view.createPrintDocumentAdapter("MedicinePayment")
                        pm.print("Medicine Payment", adapter, android.print.PrintAttributes.Builder().build())
                    } catch (_: Throwable) {
                        Toast.makeText(this@MedicinePaymentActivity, "Print not available", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            printWebView = wv
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        } catch (_: Throwable) {
            Toast.makeText(this, "Print not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun receiptText(r: JSONObject): String {
        val b = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(r.s("branch"))
        val bill = r.optDouble("bill", r.optDouble("total", 0.0))
        val deposit = r.optDouble("deposit", 0.0)
        val due = max(0.0, bill - deposit)
        val cust = r.s("customer").ifBlank { "Walk-in" }
        val meds = r.s("product").ifBlank { "-" }
        val staffMobile = r.s("receivedBy").ifBlank { r.s("createdBy") }
        val staffName = StaffDirectory.findAccount(staffMobile)?.name?.takeIf { it.isNotBlank() } ?: staffMobile
        val whenStr = r.s("createdAt").let { if (it.isNotBlank()) DateUtil.displayWithTime(it) else DateUtil.display(r.s("date")) }
        val mode = r.s("mode").ifBlank { "CASH" }
        return buildString {
            append("*${b.clinicName}*\n")
            append("${b.addressLine}\n")
            append("Ph: ${b.phoneLine}\n")
            append("--------------------------------\n")
            append("*MEDICINE PAYMENT RECEIPT*\n\n")
            append("Patient  : $cust\n")
            append("Medicine : $meds\n")
            append("Bill     : ₹${fmt(bill)}\n")
            append("Paid     : ₹${fmt(deposit)}  ($mode)\n")
            append("Due      : ₹${fmt(due)}\n\n")
            append("Date     : $whenStr\n")
            append("Branch   : ${b.displayName}\n")
            append("Received by: $staffName\n\n")
            append("Thank you. Get well soon.")
        }
    }

    private fun receiptHtml(r: JSONObject): String {
        val b = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(r.s("branch"))
        val bill = r.optDouble("bill", r.optDouble("total", 0.0))
        val deposit = r.optDouble("deposit", 0.0)
        val due = max(0.0, bill - deposit)
        val cust = esc(r.s("customer").ifBlank { "Walk-in" })
        val meds = esc(r.s("product").ifBlank { "-" })
        val staffMobile = r.s("receivedBy").ifBlank { r.s("createdBy") }
        val staffName = esc(StaffDirectory.findAccount(staffMobile)?.name?.takeIf { it.isNotBlank() } ?: staffMobile)
        val whenStr = esc(r.s("createdAt").let { if (it.isNotBlank()) DateUtil.displayWithTime(it) else DateUtil.display(r.s("date")) })
        val mode = esc(r.s("mode").ifBlank { "CASH" })
        return """
<!DOCTYPE html><html><head><meta charset="utf-8">
<style>
@page{size:A4;margin:14mm}
*{box-sizing:border-box;margin:0;padding:0;font-family:Arial,sans-serif}
body{color:#111;font-size:13px}
.h{text-align:center;border-bottom:2px solid #0B2B59;padding-bottom:8px;margin-bottom:12px}
.cn{font-size:20px;font-weight:800;color:#0B2B59}
.ad{font-size:12px;color:#555;margin-top:3px}
.tt{text-align:center;font-size:14px;font-weight:800;color:#0B7A34;margin:6px 0 14px;letter-spacing:1px}
table{width:100%;border-collapse:collapse}
td{padding:7px 4px;border-bottom:1px solid #eee;font-size:13.5px}
td.k{color:#555;width:38%}
td.v{font-weight:700;color:#10223A}
.amt{font-size:15px}
.due{color:#b0392b}
.ft{margin-top:22px;font-size:12px;color:#555;display:flex;justify-content:space-between}
.ty{text-align:center;margin-top:26px;font-size:13px;color:#0B7A34;font-weight:700}
</style></head><body>
<div class="h"><div class="cn">${esc(b.clinicName)}</div><div class="ad">${esc(b.addressLine)} &nbsp;·&nbsp; Ph: ${esc(b.phoneLine)}</div></div>
<div class="tt">MEDICINE PAYMENT RECEIPT</div>
<table>
<tr><td class="k">Patient</td><td class="v">$cust</td></tr>
<tr><td class="k">Medicine</td><td class="v">$meds</td></tr>
<tr><td class="k">Total Bill</td><td class="v amt">₹ ${fmt(bill)}</td></tr>
<tr><td class="k">Paid ($mode)</td><td class="v amt">₹ ${fmt(deposit)}</td></tr>
<tr><td class="k">Due</td><td class="v amt due">₹ ${fmt(due)}</td></tr>
<tr><td class="k">Date &amp; Time</td><td class="v">$whenStr</td></tr>
<tr><td class="k">Branch</td><td class="v">${esc(b.displayName)}</td></tr>
<tr><td class="k">Received by</td><td class="v">$staffName</td></tr>
</table>
<div class="ty">Thank you. Get well soon.</div>
</body></html>
""".trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun fmt(v: Double): String = "%,.0f".format(v)
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun isoNow(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        return f.format(Date())
    }
}
