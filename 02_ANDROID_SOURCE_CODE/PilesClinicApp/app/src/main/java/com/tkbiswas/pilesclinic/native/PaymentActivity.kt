package com.tkbiswas.pilesclinic.native

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tkbiswas.pilesclinic.databinding.ActivityPaymentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native rebuild Step 5 (final planned step) -- Payment.
 *
 * Shows Today's Collection Summary (total/cash/UPI) and list, matching
 * paymentHome()'s numbers exactly (same source data: payments + medicine
 * product-payments, same CASH/UPI classification). "Add Treatment Payment"
 * reproduces saveTreatmentPayment()'s exact rules: the Total Bill is only
 * editable the FIRST time it's set for a patient -- after that it requires
 * 3 taps to unlock, same protection added to the WebView earlier this
 * session -- and the payment label auto-increments correctly ("Advance",
 * "2nd Payment", "3rd Payment"...).
 *
 * SCOPED LIMITATIONS for this step (disclosed clearly, same pattern as
 * every step before it):
 * - Medicine Sale Payment (a separate, less-used flow in the WebView) is
 *   not in this native screen -- Today's Collection summary/list still
 *   correctly INCLUDES any medicine payments made elsewhere (WebView),
 *   just doesn't let you START a new one here yet.
 * - "Visit Fee" payments (the mandatory fee collected at Registration) are
 *   not started from here either -- Registration (Step 3) already handles
 *   that at the point of registering a patient.
 * - Editing/correcting a past payment entry (the WebView's
 *   editPaymentEntry(), same-day-only unless Master) is not in this native
 *   screen yet -- only adding new payments.
 * - Branch-based visibility IS applied here (same pattern as Follow-up):
 *   staff only see their own branch's collection, Master sees all.
 */
class PaymentActivity : AppCompatActivity() {

    // TK-REQUESTED PROACTIVE FIX (2026-07-25): the same overlapping.refresh
    // guard already proven on Follow-up and Chamber Attendance. Two loads can
    // overlap (screen reopened, an action refreshing, a slow first fetch
    // finishing late) . without this the OLDER result could land last and
    // overwrite fresh data on screen. Only the newest load may paint now.
    private var loadGuardToken = 0

    // 🔴🔒 V454 (20.08.2026, TK-নির্দেশ): Payment স্ক্রিনে এতদিন কোনো
    // অটো-রিফ্রেশই ছিল না (শুধু স্ক্রিন খোলার সময় একবার লোড হত) — Follow-up/
    // Chamber/Doctor Queue-তে ৩০-সেকেন্ডের যে প্রমাণিত পদ্ধতি আছে, ঠিক সেটাই
    // এখানে যোগ করা হলো। ⛔ টাকার হিসাব/সেভ/`renderCollectionSummary()`-র
    // কোনো লজিক এক অক্ষরও বদলায়নি — শুধু "কখন `loadSummary()` আবার ডাকা
    // হবে" তার নিয়ম।
    private val autoHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScreenFocused = true
    private var autoBusy = false
    private val autoWatch = LiveRefresh.Watch("payments")
    private val autoTick = object : Runnable {
        override fun run() {
            try { autoCheckForChanges() } catch (_: Throwable) { }
            autoHandler.postDelayed(this, LiveRefresh.TICK_MS)
        }
    }

    private fun autoCheckForChanges() {
        if (!::repository.isInitialized || !::user.isInitialized) return
        if (!autoScreenFocused || autoBusy) return
        if (!LiveRefresh.awake()) return
        // শুধু "আজ" দেখা অবস্থাতেই auto-refresh — পুরোনো তারিখ ব্রাউজ করার
        // সময় অকারণে বদলাবে না (সেটা মূলত স্থির থাকে)।
        if (selectedCollectionDate != PaymentModel.today()) return
        val br = currentBranch()
        autoBusy = true
        lifecycleScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) {
                    autoWatch.changed("payment|$br", br)
                }
                if (isFinishing || isDestroyed) return@launch
                if (!changed) return@launch
                if (selectedCollectionDate == PaymentModel.today() && autoScreenFocused) loadSummary()
            } catch (_: Throwable) {
            } finally {
                autoBusy = false
            }
        }
    }

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var repository: PaymentRepository
    private lateinit var adapter: CollectionAdapter
    private lateinit var user: NativeUser

    // 3-tap-to-unlock state for the Bill field inside the Add Payment dialog,
    // matching __visitBillTapState's pattern in app.js.
    private var billTapCount = 0
    private var billTapAt = 0L
    private var amtTapCount = 0
    private var amtTapAt = 0L

    // 🔒 B564 (08.08.2026, TK-অনুমোদিত প্রুফ) — Add Treatment Payment-এ ৪ বোতাম
    // (Cancel/Share/Print/Save)। Share/Print চাপলে Save-বোতামের হুবহু একই সেভ-পথ
    // চলে (performClick); সেভ *নিশ্চিত* হলে তবেই WhatsApp/প্রিন্ট রসিদ যায়
    // (doDirectSave-এর ok-ব্লকে) — অনুমোদন-অপেক্ষা/ব্যর্থ হলে রসিদ যায় না।
    private var pendingReceiptMode = "none"   // none | share | print
    private var payPrintWebView: android.webkit.WebView? = null
    private var payDeferFinish = false        // print handoff-এর আগে directForm-এ finish আটকাতে

    // TK-REQUESTED (2026-07-27): when Patient Details' "💵 Advance" opens this
    // screen, the staff must land STRAIGHT on the payment form -- the Payment
    // Collection screen behind it (Add Treatment Payment / Medicine Payment /
    // Monthly Collection / Today Collection Summary) is not wanted there, and
    // closing the form must go straight back to Patient Details instead of
    // stranding the staff on a screen they never asked for. This flag is set
    // ONLY by that one caller; every other way into this screen (Dashboard,
    // Follow-up card, bottom nav) leaves it false and behaves exactly as
    // before.
    private var directFormOnly = false
    private var selectedCollectionDate = PaymentModel.today()
    private var dateChosenFromHeader = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        BottomNav.wire(this)
        repository = PaymentRepository(this)

        val session = NativeSession.current(this)
        if (session == null) { finish(); return }
        user = session

        // 🔒 B572 (TK-নির্দেশ): সারিতে চাপলে আগের মতোই পেমেন্ট-ডিটেলস; নামে ট্যাপ
        // করলে ওই রোগীর History (Full Journey / Patient Timeline) খোলে।
        adapter = CollectionAdapter(this, emptyList(),
            onRowClick = { row -> showCollectionDetails(row) },
            onNameClick = { row ->
                val d = row.mobile.filter { it.isDigit() }.takeLast(10)
                if (d.length == 10) startActivity(android.content.Intent(this, PatientTimelineActivity::class.java).putExtra("mobile", d))
            })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddPayment.setOnClickListener { showSearchPatientDialog() }
        binding.btnMedicinePayment.setOnClickListener { startActivity(Intent(this, MedicinePaymentActivity::class.java)) }
        setupDatePick()

        // TK-APPROVED (2026-07-25, photo proof): Monthly Collection and
        // Collection History . MASTER ADMIN ONLY, so the row stays hidden for
        // Staff/Doctor/Field exactly as it is today. Each opens the same list
        // screen, which has a BRANCH selector on top.
        if (user.role == "master") {
            binding.rowMasterCollection.visibility = View.VISIBLE
            binding.btnMonthlyCollection.setOnClickListener {
                startActivity(Intent(this, CollectionListActivity::class.java).putExtra("mode", "monthly"))
            }
            binding.btnCollectionHistory.setOnClickListener {
                startActivity(Intent(this, CollectionListActivity::class.java).putExtra("mode", "history"))
            }
        } else {
            binding.rowMasterCollection.visibility = View.GONE
        }

        directFormOnly = intent.getBooleanExtra("directForm", false)
        if (directFormOnly) {
            // Nothing of the Collection screen is wanted behind the form, so
            // it is neither drawn nor loaded (which also saves the whole
            // Today-Collection fetch on a slow line).
            binding.btnAddPayment.visibility = View.GONE
            binding.btnMedicinePayment.visibility = View.GONE
            binding.rowMasterCollection.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
            binding.progressLoad.visibility = View.GONE
        } else {
            setupBranchPick()
            loadSummary()
        }

        // Opened from a Follow-up Patient/Visit card: go straight to that
        // patient's payment form (Bill stays locked, 3 taps to edit).
        val preMobile = intent.getStringExtra("mobile")?.filter { it.isDigit() }?.takeLast(10)
        if (!preMobile.isNullOrBlank() && preMobile.length == 10) {
            searchAndOpenPaymentForm(preMobile)
        }
    }

    // 🔴🔒 V454 (20.08.2026): DoctorQueueActivity-র প্রমাণিত একই lifecycle
    // wiring — পর্দা সামনে থাকলেই শুধু ৩০-সেকেন্ডের চেক চলবে, আড়ালে গেলে
    // থেমে যাবে (ব্যাটারি/ডেটা অপচয় নেই)। `loadSummary()` ইতিমধ্যেই এই
    // ফাইলে বহু জায়গায় ডাকা হয় (Save-এর পরেও) — সেগুলো এক অক্ষরও বদলায়নি।
    override fun onResume() {
        super.onResume()
        autoHandler.removeCallbacks(autoTick)
        autoHandler.postDelayed(autoTick, LiveRefresh.TICK_MS)
    }

    override fun onPause() {
        super.onPause()
        autoHandler.removeCallbacks(autoTick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 🔒 Add Payment-এর মতো পপ-আপ/ডায়ালগ খোলা থাকলে অটো-রিফ্রেশ বন্ধ —
        // টাইপ করার মাঝে ফর্ম পালটে যাওয়ার ঝুঁকি নেই।
        autoScreenFocused = hasFocus
    }

    // TK-REQUESTED (2026-07-28, photo proof approved): a small branch box sits on
    // the right of the header for MASTER ADMIN only. Master can look at one
    // branch's collection at a time, or "All Branch" as before. For Staff and
    // Doctor the box is never shown and this always stays their own branch, so
    // their screen behaves exactly as it did.
    private var viewBranch: String = ""

    private fun setupBranchPick() {
        if (user.role != "master") {
            binding.tvBranchPick.visibility = View.GONE
            return
        }
        val options = listOf("All Branch", "Kishanganj", "Jalpaiguri", "Cooch Behar", "Falakata", "Birpara")
        // 🟢🔒 V398 (16.08.2026, TK-অনুমোদিত): আগে প্রতিবার জোর করে "All Branch"
        //   বসত; এখন শেষবার বাছা ব্রাঞ্চটাই বসে (পুরো অ্যাপে একটাই জায়গা)।
        viewBranch = BranchFilterStore.get(this)
        binding.tvBranchPick.visibility = View.VISIBLE
        binding.tvBranchPick.text = when {
            viewBranch.isBlank() -> "Select Branch"
            viewBranch == BranchFilterStore.ALL -> "All Branch"
            else -> viewBranch
        }  // 🔒 B576: ▾ তীর সরানো (TK-নির্দেশ)
        binding.tvBranchPick.setOnClickListener {
            // 🔒 খাতার সারি B84 — Follow-up-এর হুবহু একই পপ-আপ।
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Branch"))
                .setSingleChoiceItems(options.toTypedArray(), if (viewBranch.isBlank()) -1 else options.indexOf(if (viewBranch == "All") "All Branch" else viewBranch).coerceAtLeast(0)) { dialog, which ->
                    val pick = options[which]
                    viewBranch = BranchFilterStore.set(this@PaymentActivity, if (which == 0) "All" else pick)   // 🟢 V398
                    binding.tvBranchPick.text = pick  // 🔒 B576: ▾ তীর সরানো
                    loadSummary()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .create().also { d -> d.show(); PremiumAlert.paint(d) }
        }
    }

    /** The branch whose collection is on screen right now. */
    private fun currentBranch(): String =
        if (user.role == "master") viewBranch else user.branch

    private fun setupDatePick() {
        fun refreshDateText() {
            // 🔒 B576 (TK-নির্দেশ): তারিখ 08/08/2026 রূপে (স্ল্যাশ), ▾ তীর ছাড়া।
            // DateUtil.display দেয় dd.MM.yyyy — শুধু ডট→স্ল্যাশ, অন্য পর্দা অপরিবর্তিত।
            binding.tvDatePick.text = DateUtil.display(selectedCollectionDate).replace('.', '/')
            val isToday = selectedCollectionDate == PaymentModel.today()
            // 🔵 B611 (10.08.2026, TK-অনুমোদিত ফুল-স্ক্রিন প্রুফ): তারিখ উপরে
            // ব্যাজেই একবার আছে — তাই summary কার্ডের "COLLECTION SUMMARY · তারিখ"
            // লেখা পুরো বাদ (কার্ড শুরু সরাসরি টাকার অঙ্ক দিয়ে), আর নিচের সেকশনে
            // তারিখ নয়: আজ হলে "Today's Collection", অন্য দিন হলে শুধু "Collection"।
            // ⛔ শুধু লেবেল — টাকার হিসাব/তারিখ-বাছাই কিছুই বদলায়নি।
            binding.tvSummaryLabel.visibility = View.GONE
            binding.tvCollectionLabel.text = if (isToday) "Today's Collection" else "Collection"
        }
        refreshDateText()
        binding.tvDatePick.setOnClickListener {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val base = java.util.Calendar.getInstance()
            try { base.time = fmt.parse(selectedCollectionDate) ?: java.util.Date() } catch (_: Throwable) { }
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedCollectionDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    dateChosenFromHeader = true
                    refreshDateText()
                    loadSummary()
                },
                base.get(java.util.Calendar.YEAR),
                base.get(java.util.Calendar.MONTH),
                base.get(java.util.Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
        }
    }

    private fun loadSummary() {
        val myLoadToken = ++loadGuardToken
        // TK-REQUESTED ADDITION (2026-07-20): same "show what was already on
        // the phone instantly" pattern added to Doctor Queue/Follow-up/
        // Chamber Attendance.
        var hadCache = false
        val isToday = selectedCollectionDate == PaymentModel.today()
        val cached = if (isToday) repository.loadCachedTodayCollection(currentBranch()) else null
        if (!cached.isNullOrEmpty()) {
            hadCache = true
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
            renderCollectionSummary(cached)
        } else {
            binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
            // TK-REQUESTED (2026-07-24): only reachable on the very first-
            // ever open (no cache yet) -- plain "Loading..." instead of a
            // blank screen while the first fetch is in flight.
            binding.tvEmpty.text = "Loading..."
            binding.tvEmpty.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            val guardAtStart = myLoadToken
            // TK-REQUESTED FIX (2026-07-20): this had no error handling at all --
            // needed so a fetch failure after cached data was shown can leave
            // that cached data on screen instead of crashing/going blank.
            val rows = try {
                withContext(Dispatchers.IO) {
                    if (isToday) repository.fetchTodayCollection(currentBranch())
                    else repository.fetchCollectionRange(currentBranch(), selectedCollectionDate, selectedCollectionDate)
                }
            } catch (t: Throwable) {
                null
            }
            if (guardAtStart != loadGuardToken) return@launch
            binding.progressLoad.visibility = View.GONE
            if (rows == null) {
                if (!hadCache) {
                    binding.tvEmpty.text = if (isToday) "No collection today" else "No collection on selected date"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                return@launch
            }
            renderCollectionSummary(rows)
        }
    }

    private fun renderCollectionSummary(rows: List<CollectionRow>) {
        val total = rows.sumOf { it.amount }
        // 🔒 V452 (19.08.2026, TK-approved A): a same-day Treatment Payment may
        // contain both CASH + ONLINE. Never classify the whole combined row as
        // one mode; use the preserved split so collection totals stay exact.
        val cash = rows.sumOf { it.cashAmount }
        val upi = rows.sumOf { it.onlineAmount }
        binding.tvTotal.text = "₹${"%,.0f".format(total)}"
        binding.tvTransCount.text = "${rows.size} Transactions"
        binding.tvCash.text = "₹${"%,.0f".format(cash)}"
        binding.tvUpi.text = "₹${"%,.0f".format(upi)}"
        /* 🔴 V487 (20.08.2026, TK-রিপোর্ট): ফেরতের কারণে যোগফল মাইনাস হলে
           সংখ্যাটাও লাল দেখাবে — সারির বাক্সের মতোই। প্লাস হলে আগের রঙই।
           উপরের বড় সংখ্যাটা গাঢ় কার্ডের উপরে, তাই সেখানে হালকা লাল। */
        binding.tvTotal.setTextColor(android.graphics.Color.parseColor(if (total < 0.0) "#FFB4AB" else "#FFFFFF"))
        binding.tvCash.setTextColor(android.graphics.Color.parseColor(if (cash < 0.0) "#B3261E" else "#0B7A34"))
        binding.tvUpi.setTextColor(android.graphics.Color.parseColor(if (upi < 0.0) "#B3261E" else "#1457B8"))
        val patientCount = rows.map { it.mobile.ifBlank { it.name } }.filter { it.isNotBlank() }.distinct().size
        binding.tvPatients.text = patientCount.toString()

        if (rows.isEmpty()) {
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.text = if (selectedCollectionDate == PaymentModel.today()) "No collection today" else "No collection on selected date"
            binding.tvEmpty.visibility = View.VISIBLE
            // 🔴🔴🔒 খাতার সারি B446 (TK-রিপোর্ট, ছবিসহ) — আগে এখানে
            // তালিকা (RecyclerView) কখনো খালি করা হতো না, তাই আগের
            // তারিখ/ব্রাঞ্চের পুরনো এন্ট্রি "No collection" লেখার ঠিক
            // নিচেই দেখা যেত — বিভ্রান্তিকর, মনে হতো টাকা আছে অথচ Summary
            // ₹0। এখন তালিকাও একইসাথে ফাঁকা করা হয়।
            adapter.updateItems(emptyList())
        } else {
            binding.tvEmpty.visibility = View.GONE
            // V227 (item 26): keep the same scroll position on an in-place
            // refresh (add/edit/delete), same proven pattern as Doctor Queue /
            // Follow-up. Back navigation already retains position; this covers
            // the reload case so the list no longer jumps to the top.
            val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
            val firstPos = lm?.findFirstVisibleItemPosition() ?: -1
            val firstOffset = if (firstPos >= 0) (lm?.findViewByPosition(firstPos)?.top ?: 0) else 0
            val sorted = rows.sortedByDescending { it.date }
            adapter.updateItems(sorted)
            if (firstPos in 0 until sorted.size) lm?.scrollToPositionWithOffset(firstPos, firstOffset)
        }
    }

    private fun showCollectionDetails(row: CollectionRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            Toast.makeText(this, "This entry has no mobile number", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val payments = withContext(Dispatchers.IO) {
                // ROOT-CAUSE FIX (2026-07-15): this used to silently get only 1
                // payment back (findByMobile's old hidden default), showing just
                // one transaction no matter which payment row was tapped. Now
                // asks for up to 500 -- effectively "all" of this patient's
                // payments -- so every transaction they've ever made shows up.
                SupabaseClient.findByMobile("payments", "+91$digits", limit = 500)
            }
            val list = mutableListOf<org.json.JSONObject>()
            for (i in 0 until payments.length()) {
                val payment = payments.getJSONObject(i)
                if (!dateChosenFromHeader || payment.s("date") == selectedCollectionDate) list.add(payment)
            }
            list.sortByDescending { it.s("date") }
            // 🔒 V452 (19.08.2026, TK-approved A): legacy same-day Treatment
            // rows are one daily payment in this detail view too. Raw Cloud rows
            // remain untouched; Refund/Visit Fee/other events stay separate.
            val displayList = PaymentModel.mergeDailyTreatmentJsonForDisplay(list)
            // 🔒 খাতার সারি B55 (TK, 28.07.2026 রাত): এই তালিকাটাও নম্বর (Advance /
            // 2nd Payment …) দেখায় — তাই এখানেও **দিন ধরে** হিসাব, বাকি সব
            // পর্দার সেই একই ফাংশনে। নইলে এক রোগীর নাম এক পর্দায় এক রকম,
            // আরেক পর্দায় আরেক রকম দেখাত।
            // ⛔ ক্লাউডে বাড়তি অনুরোধ নেই — উপরের ওই তালিকাটাই ব্যবহার হচ্ছে।
            val dayLabels = PaymentModel.dayBasedLabelById(displayList)

            val total = displayList.sumOf {
                // 🔴 TK-অডিট-অনুরোধ (01.08.2026): Refund সারিও প্লেইন যোগ হচ্ছিল।
                when {
                    PaymentModel.isApprovedRefund(it) -> -it.optDouble("amount", 0.0)
                    PaymentModel.isRefundRow(it) -> 0.0
                    else -> it.optDouble("amount", 0.0)
                }
            }
            // TK APPROVED (2026-07-16): Chamber Attendance's "Marked
            // Arrived" (₹0, payType="attendance_mark") entries still show
            // in the list below (TK wanted that), but they are NOT a real
            // payment, so they should not inflate the "X টি পেমেন্ট" count.
            val realPaymentCount = displayList.count { it.optString("payType", "") !in listOf("attendance_mark", "bill_edit", "chamber_expected") }
            val name = displayList.firstOrNull()?.s("name")?.ifBlank { row.name } ?: row.name
            val branch = displayList.firstOrNull()?.s("branch")?.ifBlank { row.branch } ?: row.branch
            // 🆔 TK-এর নিয়ম (28.07.2026): হেডারে Patient ID-ও দেখাতে হবে।
            // আগে কার্ড থেকে আসা ID, না থাকলে পেমেন্ট সারির patientCode।
            // ⛔ বাড়তি কোনো ক্লাউড-কল নয় — দুটোই আগে থেকেই হাতে আছে।
            val patientCode = row.patientId.ifBlank {
                displayList.asSequence().map { it.s("patientCode") }
                    .firstOrNull { it.isNotBlank() } ?: ""
            }

            // TK APPROVED (2026-07-15): premium shell (navy header, rounded
            // entry cards, styled Close button) — same visual language as the
            // rest of the app. Data/edit logic (TripleTapEdit -> tryEditPayment)
            // is completely unchanged, only the look was plain before.
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val root = LinearLayout(this@PaymentActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = dp(20).toFloat()
                }
            }
            val header = LinearLayout(this@PaymentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_header_navy_top_round)
            }
            header.addView(TextView(this@PaymentActivity).apply {
                text = "💰 ${name.ifBlank { "Walk-in" }}"
                textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
            })
            header.addView(TextView(this@PaymentActivity).apply {
                text = "📞 +91$digits" +
                    (if (patientCode.isNotBlank()) "  ·  \uD83C\uDD94 $patientCode" else "") +
                    (if (branch.isNotBlank()) "  ·  $branch" else "")
                textSize = 12f; setTextColor(android.graphics.Color.parseColor("#B8C6D8"))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(3); layoutParams = p
            })
            header.addView(TextView(this@PaymentActivity).apply {
                // 🔴 TK-REPORTED (30.07.2026 রাত, Kishanganj স্টাফের বার্তা —
                // হিন্দিতে: "मुझे बंगला समझ में नहीं आती"): এই পপ-আপ আলাদা
                // Dialog (নিজের window), তাই Activity-স্তরের NoBengali কভার
                // করত না — বাংলা টেক্সটও সরাসরি হার্ডকোড করা ছিল, `NoBengali.s()`
                // দিয়ে মোড়ানো হয়নি। এখন মোড়ানো হলো (dictionary-তে "মোট"→
                // "total", "টি পেমেন্ট"→"payments" আগে থেকেই ছিল, শুধু
                // ব্যবহার হচ্ছিল না)। ⛔ বাংলা-না-বন্ধ থাকা সব স্টাফের জন্য
                // NoBengali.s() নিজের মতোই লেখা অপরিবর্তিত রাখে — কারো
                // ক্ষতি হয় না।
                text = NoBengali.s("মোট ₹${"%,.0f".format(total)}  ·  $realPaymentCount টি পেমেন্ট")
                textSize = 12.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(6); layoutParams = p
            })
            root.addView(header)

            val col = LinearLayout(this@PaymentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(4))
            }
            if (displayList.isEmpty()) {
                col.addView(TextView(this@PaymentActivity).apply {
                    text = "No payment found"
                    setTextColor(android.graphics.Color.parseColor("#8A93A6")); textSize = 14f
                    setPadding(0, dp(10), 0, dp(10))
                })
            } else {
                col.addView(TextView(this@PaymentActivity).apply {
                    text = if (dateChosenFromHeader && user.role != "master") "READ ONLY" else NoBengali.s("একটি পেমেন্টে 3 বার ট্যাপ করুন — সম্পাদনা করতে")
                    textSize = 11.5f
                    setTextColor(android.graphics.Color.parseColor("#8A93A6"))
                    setPadding(0, 0, 0, dp(10))
                })
                displayList.forEach { p ->
                    val label = dayLabels[p.s("id")]
                        ?: p.s("payLabel").ifBlank { p.s("paymentLabel").ifBlank { p.s("payType").ifBlank { "Payment" } } }
                    val rem = p.s("remarks")
                    val row2 = LinearLayout(this@PaymentActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                        setPadding(dp(14), dp(10), dp(14), dp(10))
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.bottomMargin = dp(8); layoutParams = lp
                        isClickable = true; isFocusable = true
                    }
                    val left = LinearLayout(this@PaymentActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    left.addView(TextView(this@PaymentActivity).apply {
                        text = label; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#10223A"))
                    })
                    left.addView(TextView(this@PaymentActivity).apply {
                        val sp = if (p.s("payType").equals("treatment", true)) PaymentModel.paymentSplit(p) else 0.0 to 0.0
                        val modeText = if (p.s("payType").equals("treatment", true) && sp.first > 0.0 && sp.second > 0.0)
                            "CASH + ONLINE" else p.s("mode").ifBlank { "CASH" }
                        text = "${DateUtil.display(p.s("date"))} · $modeText"
                        textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#8A93A6"))
                    })
                    if (rem.isNotBlank()) {
                        left.addView(TextView(this@PaymentActivity).apply {
                            text = "📝 $rem"; textSize = 11f
                            setTextColor(android.graphics.Color.parseColor("#8A93A6"))
                            maxLines = 2
                        })
                    }
                    row2.addView(left)
                    row2.addView(TextView(this@PaymentActivity).apply {
                        text = "₹${"%,.0f".format(p.optDouble("amount", 0.0))}"
                        textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#16A36D"))
                    })
                    if (!dateChosenFromHeader || user.role == "master") {
                        val eventCount = p.optInt("_displayEventCount", p.optJSONArray("dailyEvents")?.length() ?: 1).coerceAtLeast(1)
                        if (p.s("payType").equals("treatment", true) && eventCount > 1) {
                            // 🔴🔒 V471 (20.08.2026, TK-অনুমোদিত ফটো-প্রুফ): শুধু
                            // Master-এর জন্য এখন ভেঙে দেখানো (breakdown) খোলে —
                            // স্টাফের জন্য নিচের সতর্কবার্তাই আগের মতো অপরিবর্তিত।
                            TripleTapEdit.attach(row2) {
                                if (user.role == "master") showDailyEventsBreakdown(p)
                                else Toast.makeText(this@PaymentActivity, "This day's payment combines $eventCount entries. Split-safe correction is required.", Toast.LENGTH_LONG).show()
                            }
                        } else TripleTapEdit.attach(row2) { tryEditPayment(p) }
                    }
                    col.addView(row2)
                }
            }
            val scroll = android.widget.ScrollView(this@PaymentActivity).apply {
                addView(col)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(420))
            }
            root.addView(scroll)

            val close = TextView(this@PaymentActivity).apply {
                text = "CLOSE"; gravity = android.view.Gravity.CENTER; textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                setPadding(0, dp(14), 0, dp(16))
                isClickable = true; isFocusable = true
            }
            root.addView(close)

            UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
            val dlg = AlertDialog.Builder(this@PaymentActivity).setView(root).setCancelable(true).create()
            dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            close.setOnClickListener { dlg.dismiss() }
            dlg.show()
        }
    }

    /** TK-REQUESTED (2026-07-22): true if the yyyy-MM-dd date string is today
     *  or yesterday -- the edit window within which non-Master staff may still
     *  correct their own branch's entry. */
    private fun isTodayOrYesterday(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val today = fmt.format(java.util.Date())
        val yCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val yesterday = fmt.format(yCal.time)
        return dateStr == today || dateStr == yesterday
    }

    /** Faithful to canEditPaymentEntry(): master may edit any; others only a same-day, same-branch entry.
     *  SECURITY FIX (2026-07-15): a payment row with a blank branch used to be
     *  editable by ANY branch's staff (same leak pattern as the Follow-up bug) —
     *  now only Master, or a staff whose own branch exactly matches the row's
     *  branch, can edit it. */
    // =========================================================================
    // 🔴🔒 V471 (20.08.2026, TK-অনুমোদিত ফটো-প্রুফ): মিশ্র পেমেন্টের ভেতরের
    // প্রতিটা আলাদা এন্ট্রি দেখানো (Master-only) — প্রতিটার পাশে Edit/Delete।
    // ⛔ সাধারণ (মিশ্র নয়) পেমেন্টের Edit/Delete — উপরের/নিচের কোড —
    //    এক অক্ষরও বদলানো হয়নি।
    // =========================================================================
    private fun showDailyEventsBreakdown(p: org.json.JSONObject) {
        val events = p.optJSONArray("dailyEvents") ?: return
        val label = p.s("payLabel").ifBlank { p.s("paymentLabel").ifBlank { "Payment" } }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), android.graphics.Color.parseColor("#12233F"))
            }
        }
        root.addView(TextView(this).apply {
            text = "${p.s("name").ifBlank { p.s("mobile") }} — $label"
            textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#12233F"))
        })
        root.addView(TextView(this).apply {
            text = "${DateUtil.display(p.s("date"))} · ${events.length()} entries combined"
            textSize = 12f; setTextColor(android.graphics.Color.parseColor("#5B6B82"))
            setPadding(0, dp(2), 0, dp(10))
        })
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = android.widget.ScrollView(this).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360))
        }
        root.addView(scroll)
        lateinit var dialog: AlertDialog
        fun refreshList(freshRow: org.json.JSONObject) {
            listBox.removeAllViews()
            val evs = freshRow.optJSONArray("dailyEvents") ?: org.json.JSONArray()
            for (i in 0 until evs.length()) {
                val e = evs.optJSONObject(i) ?: continue
                val eventId = e.optString("eventId")
                val amt = e.optDouble("amount", 0.0)
                val mode = e.s("mode").ifBlank { "CASH" }
                val timeTxt = try {
                    val ts = e.s("createdAt")
                    if (ts.isBlank()) "" else android.text.format.DateFormat.format("h:mm a", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(ts) ?: java.util.Date()).toString()
                } catch (_: Throwable) { "" }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = "₹${"%,.0f".format(amt)}"
                    textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#16A36D"))
                })
                info.addView(TextView(this).apply {
                    text = listOf(timeTxt, mode).filter { it.isNotBlank() }.joinToString(" · ")
                    textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#8A93A6"))
                })
                row.addView(info)
                val editIcn = TextView(this).apply {
                    text = "✏️"; textSize = 16f; gravity = android.view.Gravity.CENTER
                    setPadding(dp(9), dp(6), dp(9), dp(6))
                    setBackgroundColor(android.graphics.Color.parseColor("#EAF1FB"))
                }
                val delIcn = TextView(this).apply {
                    text = "🗑"; textSize = 16f; gravity = android.view.Gravity.CENTER
                    setPadding(dp(9), dp(6), dp(9), dp(6))
                    setBackgroundColor(android.graphics.Color.parseColor("#FDEEEE"))
                    (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8)
                }
                val editLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val delLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) }
                row.addView(editIcn, editLp)
                row.addView(delIcn, delLp)
                editIcn.setOnClickListener {
                    val amtInput = EditText(this).apply {
                        setText(amt.toInt().toString())
                        inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
                        val pad = dp(10); setPadding(pad, pad, pad, pad)
                        setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                    }
                    val modeSpin = Spinner(this).apply {
                        adapter = android.widget.ArrayAdapter(this@PaymentActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "ONLINE"))
                        setSelection(if (mode.equals("CASH", true)) 0 else 1)
                    }
                    val editBox = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(4), dp(4), dp(4), dp(4))
                        addView(amtInput); addView(modeSpin)
                    }
                    AlertDialog.Builder(this@PaymentActivity)
                        .setCustomTitle(PremiumAlert.header(this@PaymentActivity, "✏️ Edit this entry"))
                        .setView(editBox)
                        .setPositiveButton("Save this entry") { _, _ ->
                            val newAmt = amtInput.text.toString().toDoubleOrNull()
                            if (newAmt == null || newAmt <= 0) {
                                Toast.makeText(this@PaymentActivity, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            } else {
                                val newMode = if (modeSpin.selectedItemPosition == 0) "CASH" else "ONLINE"
                                lifecycleScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        try { repository.editOneDailyEvent(freshRow, eventId, newAmt, newMode, user.mobile, user.name.ifBlank { user.mobile }) } catch (_: Throwable) { false }
                                    }
                                    Toast.makeText(this@PaymentActivity, if (ok) "Entry updated — Master informed" else "Could not update — check connection", Toast.LENGTH_LONG).show()
                                    if (ok) {
                                        val fresh = withContext(Dispatchers.IO) { try { repository.findPaymentById(p.s("id")) } catch (_: Throwable) { null } }
                                        if (fresh != null) refreshList(fresh) else dialog.dismiss()
                                        loadSummary()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }
                delIcn.setOnClickListener {
                    AlertDialog.Builder(this@PaymentActivity)
                        .setCustomTitle(PremiumAlert.header(this@PaymentActivity, "🗑️ Delete this entry?"))
                        .setMessage("₹${"%,.0f".format(amt)} ($mode${if (timeTxt.isNotBlank()) ", $timeTxt" else ""}) will be removed. The other ${evs.length() - 1} entries stay unchanged. Master is informed.")
                        .setPositiveButton("Yes, delete this entry") { _, _ ->
                            lifecycleScope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try { repository.removeOneDailyEvent(freshRow, eventId, user.mobile, user.name.ifBlank { user.mobile }) } catch (_: Throwable) { false }
                                }
                                Toast.makeText(this@PaymentActivity, if (ok) "Entry deleted — Master informed" else "Could not delete — check connection", Toast.LENGTH_LONG).show()
                                if (ok) {
                                    val fresh = withContext(Dispatchers.IO) { try { repository.findPaymentById(p.s("id")) } catch (_: Throwable) { null } }
                                    if (fresh != null && (fresh.optJSONArray("dailyEvents")?.length() ?: 0) > 0) refreshList(fresh)
                                    else dialog.dismiss()
                                    loadSummary()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }
                listBox.addView(row)
                listBox.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                    setBackgroundColor(android.graphics.Color.parseColor("#EEF0F2"))
                })
            }
        }
        refreshList(p)
        dialog = AlertDialog.Builder(this)
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
        try { PremiumAlert.paint(dialog) } catch (_: Throwable) { }
    }

    private fun tryEditPayment(p: org.json.JSONObject) {
        val eventCount = p.optInt("_displayEventCount", p.optJSONArray("dailyEvents")?.length() ?: 1).coerceAtLeast(1)
        if (p.s("payType").equals("treatment", true) && eventCount > 1) {
            Toast.makeText(this, "This day's payment combines $eventCount entries. Cash/Online split will not be guessed.", Toast.LENGTH_LONG).show()
            return
        }
        val user = NativeSession.current(this)
        val isMaster = user?.role == "master"
        val sameBranch = p.s("branch").isNotBlank() && p.s("branch") == user?.branch
        // TK-REQUESTED (2026-07-22): the edit window for non-Master staff is now
        // TODAY or YESTERDAY (was same-day only). Older entries -> Master only.
        val canEdit = isMaster || (sameBranch && isTodayOrYesterday(p.s("date")))
        // 🆕 B337 ফিক্স (03.08.2026, TK-অনুমোদনে) — বাকি ডায়ালগ-কোড এক অক্ষরও না বদলে,
        // শুধু local `proceed()`-এ ভরা হলো (doDirectSave()-এর একই প্রমাণিত
        // প্যাটার্ন) — যাতে সাধারণ নিয়মে অনুমতি না থাকলেও Master-এর দেওয়া
        // ব্যাকডেট-Grant থাকলে (Delete-এর মতোই) এই Edit ডায়ালগও সরাসরি খোলে।
        fun proceed() {
        val id = p.s("id")
        if (id.isBlank()) { Toast.makeText(this, "This entry is not editable", Toast.LENGTH_SHORT).show(); return }

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), android.graphics.Color.parseColor("#6B1E2B"))
            }
        }
        val scrollContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(TextView(this).apply {
            text = "✏️ Edit Payment"
            textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#6B1E2B"))
            setPadding(0, 0, 0, dp(14))
        })
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val amtInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
            setText(p.optDouble("amount", 0.0).toInt().toString()); hint = "Amount"
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = dp(12); setPadding(pad, pad, pad, pad)
        }
        val modeInput = Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@PaymentActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "ONLINE"))
            setSelection(if (p.s("mode").equals("CASH", true)) 0 else 1)
        }
        // 🔒 TK'S DECISION (27.07.2026): the Remarks box is removed from this
        // correction popup too -- same reason as the payment form: Treatment
        // Progress belongs only to Chamber Date's own "Treatment Progress" box
        // (written AFTER the treatment) or the Report Card's own 3-tap edit.
        // This popup exists to correct the AMOUNT and the MODE, nothing else.
        //
        // IMPORTANT SAFETY: the note already saved on this payment is NOT
        // touched. This field is still read when saving, and it is pre-filled
        // with the row's existing remark -- exactly what used to happen when a
        // staff opened this popup and did not type in the box. So correcting an
        // amount can never wipe a treatment note that was written earlier.
        val remarkInput = EditText(this).apply { setText(p.s("remarks")) }
        fun sectionLabel(t: String) = TextView(this).apply {
            text = t; textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, dp(10), 0, dp(4))
        }
        container.addView(sectionLabel("Amount")); container.addView(amtInput)
        container.addView(sectionLabel("Mode")); container.addView(modeInput)
        scrollContent.addView(container)
        // TK-REPORTED BUG FIX (2026-07-20): same class of bug as "Edit Record"
        // (section 88) -- this popup's Save/Cancel buttons are custom, baked
        // directly into `root` rather than AlertDialog's own button bar, so a
        // plain ScrollView wrap (with no height cap) wouldn't actually
        // enforce scrolling -- the whole thing would just grow taller like
        // before. A capped-height ScrollView here (same dp(420) pattern this
        // file already uses for its Payment History list above) makes the
        // title+fields scrollable while Cancel/Save/Delete stay fixed and
        // visible below -- normal-length content (the common case) looks and
        // behaves exactly as before.
        val scroll = MaxHeightScrollView(this).apply {
            addView(scrollContent)
            maxHeightPx = dp(320)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(scroll)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, dp(10))
        }
        val cancelBtn = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "CANCEL"; textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#17304F"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#6B1E2B"))
            cornerRadius = dp(12)
        }
        val saveBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "SAVE"; textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#16A36D"))
            cornerRadius = dp(12)
        }
        actionRow.addView(cancelBtn, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.marginEnd = dp(6) })
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.marginStart = dp(6) })
        root.addView(actionRow)

        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this).setView(root).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // TK APPROVED (2026-07-16): "Delete" for a Chamber Attendance
        // "Marked Arrived" entry (payType="attendance_mark") -- TK's
        // explicit instruction: reuse the SAME 3-tap-to-edit that already
        // opens this exact dialog for every payment row, rather than a new
        // separate button/mechanism next to the row. SAFETY: this delete
        // button only ever appears for payType="attendance_mark" -- a real
        // Payment/Registration Fee row never gets a delete option here.
        if (p.optString("payType", "") == "attendance_mark") {
            val deleteBtn = TextView(this).apply {
                text = "🗑 Delete this \"Marked Arrived\" entry"
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor("#B03030"))
                setPadding(0, dp(4), 0, dp(4))
                isClickable = true; isFocusable = true
            }
            root.addView(deleteBtn)
            deleteBtn.setOnClickListener {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { ChamberAttendanceRepository.undoAttendanceMark(id) }
                    Toast.makeText(this@PaymentActivity, if (ok) "Entry deleted" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                }
            }
        }

        // 🔒 খাতার সারি B52 (TK, 28.07.2026 রাত): *"তৎক্ষণাৎ সেই স্টাফ কেন সেই
        // পেমেন্ট ডিলিট করতে পারবে না?"* — ধীর ইন্টারনেটে ভুল করে দু-তিনবার
        // নেওয়া টাকা এখন স্টাফ নিজেই মুছতে পারেন।
        //
        // কে পারবে (TK-এর সিদ্ধান্ত): **নিজের ব্রাঞ্চের আজ ও গতকালের এন্ট্রি**;
        // তার চেয়ে পুরনো হলে শুধু মাস্টার। এটা ঠিক উপরের `canEdit`-এর সেই একই
        // নিয়ম, তাই দুটো আলাদা হয়ে যাওয়ার ভয় নেই।
        //
        // ⛔ "Marked Arrived" সারির নিজের ডিলিট আগে থেকেই আছে (উপরে) — সেটায়
        //    হাত পড়েনি, তাই এখানে ওই ধরনের সারি বাদ।
        // ⛔ মোছা মানে হারিয়ে যাওয়া নয় — সারিটা Trash-এ যায়, আর মাস্টারের
        //    ঘন্টায় খবর পৌঁছয়।
        if (p.optString("payType", "") != "attendance_mark") {
            val delBtn = TextView(this).apply {
                text = "🗑 DELETE THIS PAYMENT"
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#B03030"))
                setPadding(0, dp(6), 0, dp(6))
                isClickable = true; isFocusable = true
            }
            root.addView(delBtn)
            delBtn.setOnClickListener {
                val amtText = "₹${"%,.0f".format(p.optDouble("amount", 0.0))}"
                val labelText = p.s("payLabel").ifBlank { p.s("paymentLabel").ifBlank { "Payment" } }
                // 🔒🔒 খাতার সারি B111 (TK, 29.07.2026 বিকেল ৫.৪০): *"staff ডিলিট
                // করতে পারবে না। সে ক্ষেত্রে Master-এর কাছে ঘন্টাতে notification
                // আসবে। মাস্টার অনুমতি দিলে তবেই ডিলিট হবে।"*
                //
                // 🚨 **এটা খাতার সারি B98-এর বাকি থেকে যাওয়া অংশ** — ওখানে
                // রেকর্ডের ডিলিট মাস্টার-only করা হয়েছিল, কিন্তু **টাকার এই
                // বোতামটা তখন বাদ পড়ে গিয়েছিল**, তাই স্টাফ এখান দিয়ে টাকা মুছে
                // ফেলতে পারতেন। এখন এটাও একই নিয়মে এল।
                // ⛔ মাস্টারের জন্য কিছুই বদলায়নি — তিনি আগের মতোই সঙ্গে সঙ্গে মুছতে পারেন।
                // ⛔ স্টাফের ক্ষেত্রে **এখানে কিছুই মোছে না** — শুধু অনুরোধ যায়।
                // 🔒 খাতার সারি B112 (TK, বিকেল ৬.১০): স্টাফ **আজ ও গতকালের** এন্ট্রি
                // নিজে মুছতে পারবেন, **তবে ওই দিনের চেম্বার বন্ধ হয়ে গেলে নয়** —
                // চেম্বার বন্ধ মানে মাস্টার হিসাব পেয়ে গেছেন।
                // ⚠️ যাচাইটা ক্লাউড ছুঁতে পারে, তাই **ব্যাকগ্রাউন্ডে** করা হয় —
                //    মূল থ্রেডে নেট ছোঁয়া যাবে না (সতর্কবার্তার ৭ নম্বর নিয়ম)।
                val u = user
                lifecycleScope.launch {
                val allowedNow = withContext(Dispatchers.IO) {
                    try { DeletePermission.canDeleteEntryNow(this@PaymentActivity, u, p.s("date"), p.s("branch"), paid = true) }
                    catch (_: Throwable) { false }
                }
                if (u != null && !allowedNow) {
                    AlertDialog.Builder(this@PaymentActivity)
                        .setCustomTitle(PremiumAlert.header(this@PaymentActivity, "Master-এর অনুমতি লাগবে"))
                        .setMessage(
                            "$amtText ($labelText)\n\n⛔ এখনই কিছুই মুছবে না। " +
                            "Master-এর ঘন্টায় অনুরোধ যাবে; তিনি অনুমোদন দিলে তবেই ডিলিট হবে।"
                        )
                        .setPositiveButton("অনুরোধ পাঠান") { _, _ ->
                            lifecycleScope.launch {
                                val sent = withContext(Dispatchers.IO) {
                                    try {
                                        DeletePermission.sendRequest(
                                            this@PaymentActivity, u, "Payment",
                                            p.s("name"), p.s("mobile"), p.s("patientCode"),
                                            p.s("branch"), "$amtText ($labelText)", p.s("id")
                                        )
                                    } catch (_: Throwable) { false }
                                }
                                Toast.makeText(
                                    this@PaymentActivity, NoBengali.s(if (sent) "Master-কে অনুরোধ পাঠানো হয়েছে" else "ব্যর্থ — নেট চেক করুন"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .setNegativeButton("বন্ধ করুন", null)
                        .show().also { PremiumAlert.paint(it) }
                    return@launch
                }
                // 🔴🔴 B334 (03.08.2026, TK-রিপোর্ট — একই পেমেন্ট-ডিলিটের ৩টা
                // আলাদা "Payment deleted" নোটিশ Briefing-এ চলে গিয়েছিল):
                // **আসল কারণ:** "Yes, delete" বোতাম চাপার পরেও বোতামটা সক্রিয়ই
                // থেকে যেত — নেটওয়ার্ক একটু ধীরে হলে কেউ তাড়াহুড়োয় ২-৩ বার
                // চেপে ফেললে প্রতিটা চাপ আলাদাভাবে `deletePaymentEntry()` ডেকে
                // ফেলত (Kotlin coroutine চলাকালীন বোতাম আপনা-আপনি বন্ধ হয় না)।
                // প্রতিটা কল সফল হয়ে নিজের মতো Trash-এ পাঠাত ও নিজের মতো
                // Briefing পোস্ট করত — তাই একই ঘটনার একাধিক নোটিশ। **সমাধান:**
                // প্রথম চাপেই Yes/No দুটো বোতাম সাথে সাথে বন্ধ (disable) করে
                // দেওয়া হয়, তাই দ্বিতীয়/তৃতীয় চাপ কখনো নতুন করে কিছু ডাকতেই
                // পারে না। ⛔ ডিলিট/Trash/Briefing-পোস্ট করার আসল লজিক এক
                // অক্ষরও বদলায়নি।
                val confirmDialog = AlertDialog.Builder(this@PaymentActivity)
                    .setCustomTitle(PremiumAlert.header(this@PaymentActivity, "Delete this payment?"))
                    .setMessage(
                        "$amtText ($labelText) taken on ${DateUtil.display(p.s("date"))} will be removed " +
                        "from this patient's account.\n\nThe entry goes to Trash and Master is informed. " +
                        "Delete it?"
                    )
                    .setPositiveButton("Yes, delete", null)
                    .setNegativeButton("No", null)
                    .setCancelable(false)
                    .show().also { PremiumAlert.paint(it) }
                confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                repository.deletePaymentEntry(
                                    p, user?.mobile ?: "", user?.name?.ifBlank { user?.mobile ?: "" } ?: ""
                                )
                            } catch (_: Throwable) { false }
                        }
                        Toast.makeText(
                            this@PaymentActivity,
                            if (ok) "Payment deleted — Master informed" else "Could not delete — check connection and try again",
                            Toast.LENGTH_LONG
                        ).show()
                        confirmDialog.dismiss()
                        if (ok) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                    }
                }
                }
            }
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        saveBtn.setOnClickListener {
                val amt = amtInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (amt <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val newMode = modeInput.selectedItem.toString()
                val typedRemark = remarkInput.text.toString().trim()
                // Build an audit note so an admin can see exactly what was changed
                // (amount / mode), by whom and when — matching the web's paymentAuditLine.
                val oldAmt = p.optDouble("amount", 0.0)
                val oldMode = p.s("mode").ifBlank { "CASH" }
                val by = user?.name?.ifBlank { user?.mobile } ?: (user?.mobile ?: "User")
                val whenStr = DateUtil.displayWithTime(java.util.Date())
                val changes = mutableListOf<String>()
                // 🔴 TK-REPORTED (30.07.2026 রাত, Kishanganj স্টাফের হিন্দি
                // বার্তা — "मुझे बंगला समझ में नहीं आती"): এই অডিট-স্ট্রিং
                // ডেটাবেসে remarks হিসেবে সেভ হয় ও পরে Payment/Timeline/
                // Report Card/Chamber/Follow-up — সব জায়গায় হুবহু দেখা যায়।
                // "থেকে"/"করেছেন" বাংলা শব্দ ছিল — এখন Mode-লাইনের মতোই
                // ইংরেজি "→"/"by" (প্রজেক্টের লক করা নিয়ম: সব সিস্টেম-লেখা
                // টেক্সট ইংরেজি-only)। ⛔ টাকার অঙ্ক/হিসাব/লজিক কিছুই বদলায়নি
                // — শুধু এই দুটো শব্দ।
                if (amt != oldAmt) changes.add("Amount ₹${"%,.0f".format(oldAmt)} → ₹${"%,.0f".format(amt)}")
                if (!newMode.equals(oldMode, true)) changes.add("Mode $oldMode → $newMode")
                val remarks = if (changes.isNotEmpty()) {
                    val audit = "Audit: ${changes.joinToString(", ")} by $by | Date: $whenStr"
                    if (typedRemark.isNotBlank()) "$typedRemark | $audit" else audit
                } else typedRemark
                val fields = org.json.JSONObject()
                    .put("amount", amt)
                    .put("mode", newMode)
                    .put("remarks", remarks)
                    .put("editedBy", user?.mobile ?: "")
                    .put("editedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { SupabaseClient.updateById("payments", id, fields) }
                    Toast.makeText(this@PaymentActivity, if (ok) "Payment updated" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                }
        }
        dialog.show()
        }
        if (!canEdit) {
            lifecycleScope.launch {
                val granted = withContext(Dispatchers.IO) {
                    try { BackdatePaymentGrant.isGrantedNow(user?.mobile ?: "", p.s("date")) } catch (_: Throwable) { false }
                }
                if (granted) proceed()
                else Toast.makeText(this@PaymentActivity, "Only Master can edit entries older than yesterday", Toast.LENGTH_SHORT).show()
            }
            return
        }
        proceed()
    }

    // TK-REQUESTED REDESIGN (2026-07-20): the old dialog only took an exact
    // 10-digit mobile number and had no visual design (plain system dialog).
    // Now: a single field searches by NAME or MOBILE (whichever matches),
    // green-gradient title matching this module's own header color
    // (bg_payment_header -- the same drawable "Payment Collection" already
    // uses, so nothing new/mismatched is introduced), and a live results
    // list to tap the right patient from when a name matches more than one
    // person. Selecting a result reuses the EXACT same
    // searchAndOpenPaymentForm(mobile) flow as before -- bill/paid
    // computation and the payment form itself are completely unchanged.
    private fun showSearchPatientDialog() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val titleBar = TextView(this).apply {
            text = "🔍  Search Patient"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_payment_header)
        }
        root.addView(titleBar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        root.addView(body)

        val input = EditText(this).apply {
            hint = "Name or Mobile Number"
        }
        body.addView(input)

        val hintText = TextView(this).apply {
            // 🔴 TK-REPORTED (30.07.2026 রাত, Kishanganj স্টাফের হিন্দি বার্তা):
            // এই পপ-আপও আলাদা Dialog, তাই Activity-স্তরের NoBengali কভার করত
            // না। NoBengali.s() দিয়ে মোড়ানো হলো।
            text = NoBengali.s("নাম অথবা মোবাইল নম্বর — যেকোনো একটা লিখুন")
            textSize = 11f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            setPadding(0, dp(4), 0, 0)
        }
        body.addView(hintText)

        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        body.addView(resultsContainer)

        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this)
            .setView(android.widget.ScrollView(this).apply { addView(root) })
            .setNegativeButton("Cancel", null)
            .create()

        fun renderResults(matches: List<PatientBillInfo>) {
            resultsContainer.removeAllViews()
            for (m in matches) {
                val mobileDigits = m.mobile.filter { it.isDigit() }.takeLast(10)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setBackgroundColor(android.graphics.Color.parseColor("#F2F8F4"))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp(8)
                    layoutParams = lp
                    isClickable = true
                    isFocusable = true
                }
                val avatar = TextView(this).apply {
                    text = m.name.trim().firstOrNull()?.uppercase() ?: "?"
                    setTextColor(android.graphics.Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_circle_gradient)
                    val lp = LinearLayout.LayoutParams(dp(34), dp(34))
                    lp.marginEnd = dp(10)
                    layoutParams = lp
                }
                row.addView(avatar)
                val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val nameView = TextView(this).apply {
                    // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে নিচের 📞 লাইনের সাথে মিলে মোবাইল দুইবার দেখাত।
                    text = m.name.ifBlank { "UNKNOWN" }
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                val metaView = TextView(this).apply {
                    text = "📞 $mobileDigits · ${m.patientId.ifBlank { "-" }} · ${m.branch.ifBlank { "-" }}"
                    textSize = 11f
                    setTextColor(android.graphics.Color.parseColor("#666666"))
                }
                textCol.addView(nameView)
                textCol.addView(metaView)
                row.addView(textCol)
                row.setOnClickListener {
                    dialog.dismiss()
                    searchAndOpenPaymentForm(mobileDigits)
                }
                resultsContainer.addView(row)
            }
        }

        var searchJob: kotlinx.coroutines.Job? = null
        fun runSearch() {
            val q = input.text.toString()
            if (q.trim().length < 2) { resultsContainer.removeAllViews(); return }
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                kotlinx.coroutines.delay(250)
                val matches = try {
                    withContext(Dispatchers.IO) { repository.searchPatients(q) }
                } catch (t: Throwable) { emptyList() }
                if (matches.isEmpty()) {
                    resultsContainer.removeAllViews()
                    val empty = TextView(this@PaymentActivity).apply {
                        text = "No match found"
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#999999"))
                        setPadding(dp(4), dp(6), 0, 0)
                    }
                    resultsContainer.addView(empty)
                } else {
                    renderResults(matches)
                }
            }
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { runSearch() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Search") { _, _ -> runSearch() }
        dialog.show()
    }

    private fun searchAndOpenPaymentForm(mobile: String) {
        // 🔒 TK-এর নিয়ম (28.07.2026, খাতার সারি B26): চাপ দেওয়ামাত্র স্টাফ বুঝবেন
        // কাজ শুরু হয়েছে — পর্দা যেন মরা মনে না হয়।
        // Tapping a name in Search Patient closes that box and then waits for
        // the patient's bill/paid figures before the payment form can open. On
        // a weak line that gap felt like the tap had done nothing at all.
        // ⛔ MONEY IS UNTOUCHED: the form still opens only AFTER the real
        // bill/paid figures have arrived (the same rule TK already approved) --
        // this only adds the same instant "work has started" message the
        // Chamber and Doctor screens already show. Not one figure or rule
        // changes, and this is the same pattern used elsewhere in the app.
        Toast.makeText(this, "Opening…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            // TK-REPORTED BUG FIX (2026-07-26): when the same mobile exists at
            // more than one branch, prefer THIS staff's own branch record --
            // see findPatientByMobile()'s note. Master (blank/All branch)
            // falls back to the same deterministic choice as before.
            val ownBranch = NativeSession.current(this@PaymentActivity)?.branch.orEmpty()
            val patient = withContext(Dispatchers.IO) { repository.findPatientByMobile(mobile, ownBranch) }
            if (patient == null) {
                Toast.makeText(this@PaymentActivity, "No registered patient found with this number", Toast.LENGTH_SHORT).show()
                // Direct mode has nothing else on screen, so go straight back.
                if (directFormOnly) finish()
            } else {
                // 🔵 TK-ORDER (07.08.2026): আগের Paid টাকা লোড না হলে (payments পড়া
                // ব্যর্থ) Paid/Due ভুল (₹0 / পুরো Due) দেখাতে পারে — তাই ফর্ম খোলার
                // আগে স্পষ্ট সতর্কবার্তা। ⛔ ফর্ম/সেভ কিছু বদলায়নি, শুধু সতর্কবার্তা।
                if (patient.paymentsUnverified) {
                    Toast.makeText(this@PaymentActivity, NoBengali.s("⚠️ আগের Paid টাকা এখন লোড হয়নি — Paid/Due ভুল দেখাতে পারে। একটু পরে আবার খুলে দেখুন।"), Toast.LENGTH_LONG).show()
                }
                showPaymentFormDialog(patient)
            }
        }
    }

    // 💸 V216 (§13, 31.07.2026): Refund / টাকা ফেরত form. Amount + Cash/Online +
    // Reason নিয়ে repository.saveRefund ডাকে। Master → সরাসরি approved; Staff →
    // pending request (Master-এর ঘন্টায়)। ⛔ পুরোনো payment row ছোঁয়া হয় না।
    private var refundSaving = false
    private fun showRefundDialog(patient: PatientBillInfo) {
        val d = resources.displayMetrics.density
        // 🔒 V220 (§4, 31.07.2026): এই ফর্ম **একবার খোলা**র জন্য একটা nonce। cloud
        // fail হলে dialog খোলা থাকে → আবার চাপলে একই nonce = একই Refund (double নয়)।
        // নতুন করে ফর্ম খুললে নতুন nonce = একই দিনে একই পরিমাণের বৈধ দ্বিতীয় Refund আলাদা।
        val refundNonce = java.util.UUID.randomUUID().toString().replace("-", "").take(10)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (14 * d).toInt(), (22 * d).toInt(), 0)
        }
        box.addView(TextView(this).apply {
            val paidTxt = if (patient.paid > 0.0) "  ·  Paid ₹${"%,.0f".format(patient.paid)}" else ""
            text = "${patient.name} · ${patient.patientId}$paidTxt"
            textSize = 12.5f
        })
        // 🔴 V509 (২১.০৮.২০২৬, TK-সিদ্ধান্ত — "Visit Fee-ও ফেরতের সীমায় ধরা হবে"):
        // ঠিক কতটা ফেরত দেওয়া যাবে সেটা পর্দাতেই দেখানো হয়, আর Visit Fee আলাদা
        // থাকলে সেটাও ভেঙে দেখানো হয় — নইলে "Paid ₹0" দেখে মনে হত ফেরত দেওয়াই যাবে না।
        box.addView(TextView(this).apply {
            val vf = if (patient.visitFeePaid > 0.0) "  (Visit Fee ₹${"%,.0f".format(patient.visitFeePaid)} included)" else ""
            text = "Refundable now: ₹${"%,.0f".format(patient.refundableTotal)}$vf"
            textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#0A7A45"))
            setPadding(0, (4 * d).toInt(), 0, 0)
        })
        box.addView(TextView(this).apply { text = "Refund Amount (₹)"; textSize = 12f; setPadding(0, (14 * d).toInt(), 0, 4) })
        val amtInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789."); hint = "0" }
        box.addView(amtInput)
        box.addView(TextView(this).apply { text = "Mode"; textSize = 12f; setPadding(0, (12 * d).toInt(), 0, 4) })
        val modeSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@PaymentActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "ONLINE")
            )
        }
        box.addView(modeSpinner)
        box.addView(TextView(this).apply { text = "Reason (কারণ)"; textSize = 12f; setPadding(0, (12 * d).toInt(), 0, 4) })
        val reasonInput = EditText(this).apply { hint = "Refund reason" }
        box.addView(reasonInput)
        UppercaseInputUtil.applyToAll(box)

        val isMaster = user.role.equals("master", ignoreCase = true)
        val autoApprove = isMaster || repository.chamberOpenToday(patient.branch)
        val posLabel = if (autoApprove) "Refund now" else "Send refund request"
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "💸 Refund"))
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(posLabel, null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show().also { PremiumAlert.paint(dialog) }
        // duplicate-tap guard-সহ (Payment save-এর মতোই), তাই দ্বিতীয় tap-এ দ্বিতীয় refund হয় না।
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (refundSaving) return@setOnClickListener
            val amt = amtInput.text.toString().toDoubleOrNull() ?: 0.0
            if (amt <= 0.0) { Toast.makeText(this, "Enter a valid refund amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            // 🔒 V217 (§B216): Screen-স্তরেই প্রথম পাহারা — জমার চেয়ে বেশি লিখলে
            // সঙ্গে সঙ্গে আটকানো, ক্লাউড পর্যন্ত পাঠাতেই হয় না। আসল/চূড়ান্ত
            // পাহারা `saveRefund`-এর ভিতরেই (pending-ও ধরে), এটা শুধু দ্রুত UX।
            // 🔴 V509 (২১.০৮.২০২৬, TK-সিদ্ধান্ত): সীমা এখন চিকিৎসার জমা + Visit Fee।
            if (amt > patient.refundableTotal + 0.5) {
                Toast.makeText(this, "Refund can't be more than the refundable amount (₹${"%,.0f".format(patient.refundableTotal)})", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val reason = reasonInput.text.toString().trim()
            refundSaving = true
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        repository.saveRefund(patient, amt, modeSpinner.selectedItem.toString(), reason, user, refundNonce)
                    }
                    // সৎ বার্তা: cloud-এ সত্যিই বসলে তবেই Success — নইলে আসল কারণ
                    // (নেট সমস্যা / জমার চেয়ে বেশি) দেখানো হয়, কখনো ভুয়ো "Success" নয়।
                    val msg = when {
                        !result.success -> result.message.ifBlank { "Failed — check connection" }
                        autoApprove -> "Refund saved ✓ — ₹${"%,.0f".format(amt)} reduced from collection"
                        else -> "Refund request sent — will reduce the total once Master approves"
                    }
                    Toast.makeText(this@PaymentActivity, msg, Toast.LENGTH_LONG).show()
                    if (result.success) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                } catch (_: Exception) {
                    Toast.makeText(this@PaymentActivity, "Could not save refund — try again", Toast.LENGTH_SHORT).show()
                } finally { refundSaving = false }
            }
        }
    }

    // 🔒 B563 (08.08.2026, TK-নির্দেশ) — ঠিকানা দু'লাইনে, গ্লোবাল রুলস (B554)-এর
    // হুবহু একই প্রমাণিত নিয়ম: থানা-চিহ্নের ঠিক আগে ভাঙা হয় (raw ঠিকানায়), তাই
    // ১ম লাইনে গ্রাম+পোস্ট, ২য় লাইনে থানা+জেলা; তারপর প্রতি লাইন থেকে লেবেল
    // (Vill:/PO:/PS:/Dist:) কাটা হয়। চিহ্ন না পেলে এক লাইন। সেভ-হওয়া ঠিকানা
    // বদলায় না — শুধু দেখানোর সময়।
    private fun addressTwoLines(address: String): String {
        if (address.isBlank()) return address
        fun strip(a: String): String = a.split(",").joinToString(",") { part ->
            val trimmed = part.trim()
            val colonIdx = trimmed.indexOf(':')
            (if (colonIdx >= 0) trimmed.substring(colonIdx + 1) else trimmed).trim()
        }
        val markers = listOf("PS:", "P.S", "P/S", "Thana", "থানা", "Police Station")
        var idx = -1
        for (m in markers) { val i = address.indexOf(m, ignoreCase = true); if (i > 0 && (idx == -1 || i < idx)) idx = i }
        if (idx <= 0) return strip(address)
        val first = strip(address.substring(0, idx).trim().trimEnd(',').trim())
        val second = strip(address.substring(idx).trim())
        return if (first.isBlank() || second.isBlank()) strip(address) else "$first\n$second"
    }

    // 🔒 B564 — Share/Print রসিদ (শুধু সেভ নিশ্চিত হলে ডাকা হয়)। এই পেমেন্টের
    // পরে মোট জমা = আগের paid + এইবারের amt; বাকি = বিল − সেই মোট (বিল থাকলে)।
    private fun payFmt(v: Double): String = "%,.0f".format(v)
    private fun payEsc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun treatmentReceiptText(patient: PatientBillInfo, amt: Double, mode: String, staffMobile: String, dateIso: String): String {
        val b = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(patient.branch)
        val newPaid = patient.paid + amt
        val due = if (patient.bill > 0) (patient.bill - newPaid).coerceAtLeast(0.0) else -1.0
        val staffName = StaffDirectory.findAccount(staffMobile)?.name?.takeIf { it.isNotBlank() } ?: staffMobile
        return buildString {
            append("*${b.clinicName}*\n")
            append("${b.addressLine}\n")
            append("Ph: ${b.phoneLine}\n")
            append("--------------------------------\n")
            append("*TREATMENT PAYMENT RECEIPT*\n\n")
            append("Patient : ${patient.name}\n")
            if (patient.patientId.isNotBlank()) append("ID      : ${patient.patientId}\n")
            if (patient.disease.isNotBlank()) append("Disease : ${patient.disease}\n")
            append("--------------------------------\n")
            if (patient.bill > 0) append("Total Bill  : ₹${payFmt(patient.bill)}\n")
            append("This Payment: ₹${payFmt(amt)}  ($mode)\n")
            append("Total Paid  : ₹${payFmt(newPaid)}\n")
            if (due >= 0) append("Due         : ₹${payFmt(due)}\n")
            append("--------------------------------\n")
            append("Date    : ${DateUtil.display(dateIso)}\n")
            append("Branch  : ${b.displayName}\n")
            append("Received by: $staffName\n\n")
            append("Thank you.")
        }
    }

    private fun treatmentReceiptHtml(patient: PatientBillInfo, amt: Double, mode: String, staffMobile: String, dateIso: String): String {
        val b = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(patient.branch)
        val newPaid = patient.paid + amt
        val due = if (patient.bill > 0) (patient.bill - newPaid).coerceAtLeast(0.0) else -1.0
        val staffName = payEsc(StaffDirectory.findAccount(staffMobile)?.name?.takeIf { it.isNotBlank() } ?: staffMobile)
        val billRow = if (patient.bill > 0) "<tr><td class=\"k\">Total Bill</td><td class=\"v\">₹ ${payFmt(patient.bill)}</td></tr>" else ""
        val dueRow = if (due >= 0) "<tr><td class=\"k\">Due</td><td class=\"v due\">₹ ${payFmt(due)}</td></tr>" else ""
        val idRow = if (patient.patientId.isNotBlank()) "<tr><td class=\"k\">Patient ID</td><td class=\"v\">${payEsc(patient.patientId)}</td></tr>" else ""
        val disRow = if (patient.disease.isNotBlank()) "<tr><td class=\"k\">Disease</td><td class=\"v\">${payEsc(patient.disease)}</td></tr>" else ""
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
td.k{color:#555;width:40%}
td.v{font-weight:700;color:#10223A}
.amt{font-size:15px}
.due{color:#b0392b}
.ty{text-align:center;margin-top:26px;font-size:13px;color:#0B7A34;font-weight:700}
</style></head><body>
<div class="h"><div class="cn">${payEsc(b.clinicName)}</div><div class="ad">${payEsc(b.addressLine)} &nbsp;·&nbsp; Ph: ${payEsc(b.phoneLine)}</div></div>
<div class="tt">TREATMENT PAYMENT RECEIPT</div>
<table>
<tr><td class="k">Patient</td><td class="v">${payEsc(patient.name)}</td></tr>
$idRow
$disRow
$billRow
<tr><td class="k">This Payment ($mode)</td><td class="v amt">₹ ${payFmt(amt)}</td></tr>
<tr><td class="k">Total Paid</td><td class="v amt">₹ ${payFmt(newPaid)}</td></tr>
$dueRow
<tr><td class="k">Date</td><td class="v">${payEsc(DateUtil.display(dateIso))}</td></tr>
<tr><td class="k">Branch</td><td class="v">${payEsc(b.displayName)}</td></tr>
<tr><td class="k">Received by</td><td class="v">$staffName</td></tr>
</table>
<div class="ty">Thank you.</div>
</body></html>
""".trimIndent()
    }

    private fun printTreatmentReceipt(patient: PatientBillInfo, amt: Double, mode: String, staffMobile: String, dateIso: String, finishAfter: Boolean) {
        val html = treatmentReceiptHtml(patient, amt, mode, staffMobile, dateIso)
        try {
            val wv = android.webkit.WebView(this)
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                    try {
                        val pm = getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                        val adapter = view.createPrintDocumentAdapter("TreatmentPayment")
                        pm.print("Treatment Payment", adapter, android.print.PrintAttributes.Builder().build())
                    } catch (_: Throwable) {
                        Toast.makeText(this@PaymentActivity, "Print not available", Toast.LENGTH_SHORT).show()
                    }
                    // প্রিন্ট সিস্টেমের হাতে চলে গেছে — এবার নিরাপদে directForm finish।
                    payDeferFinish = false
                    if (finishAfter && !isFinishing) finish()
                }
            }
            payPrintWebView = wv
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        } catch (_: Throwable) {
            Toast.makeText(this, "Print not available", Toast.LENGTH_SHORT).show()
            payDeferFinish = false
            if (finishAfter && !isFinishing) finish()
        }
    }

    private fun showPaymentFormDialog(patient: PatientBillInfo) {
        // 🔒 B619 (11.08.2026, TK-নির্দেশ): "যে ব্রাঞ্চের স্টাফ শুধু সেই ব্রাঞ্চের রোগীর
        // টাকা নিতে পারবে।" তাই অন্য ব্রাঞ্চের রোগী হলে টাকার ঘর খোলার **আগেই** আটকাই —
        // আগে শুধু SAVE-এ আটকাত (সেই guard backstop হিসেবে অটুট)। master / নিজের ব্রাঞ্চ /
        // সেই ব্রাঞ্চের ডাক্তার আগের মতোই পারবেন (MoneyBranchGuard-এর হুবহু নিয়ম)।
        if (!MoneyBranchGuard.canTakeMoney(this, patient.branch, patient.patientId)) {
            Toast.makeText(this, MoneyBranchGuard.blockMessage(patient.branch), Toast.LENGTH_LONG).show()
            return
        }
        billTapCount = 0
        val due = (patient.bill - patient.paid).coerceAtLeast(0.0)
        val nextLabel = repository.nextLabelFor(patient.id)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        // 🔒 B562 (08.08.2026, TK-অনুমোদিত প্রুফ) — পরিষ্কার নতুন কার্ড। TK-এর
        // নির্দেশ: আলাদা "View/Edit Past Payments" ও "Refund" বোতাম এবং নিচের
        // আলাদা "Total Bill Amount" ঘর — তিনটেই তুলে দেওয়া হলো; কাজগুলো এখন
        // উপরের তিনটে সংখ্যার উপরেই:
        //   • নাম / ID চাপলে → রোগীর Full Journey (Patient Timeline)
        //   • BILL ৩ বার চাপলে → টোটাল বিল এডিট (ছোট পপ-আপ; আগের ৩-ট্যাপ নিরাপত্তাই)
        //   • PAID চাপলে → বিগত পেমেন্ট View/Edit  • DUE চাপলে → Refund
        // ⛔ টাকা সেভ / বিল-লক / refund / history-র লজিক এক অক্ষরও বদলায়নি —
        //   বিলের মান আগের মতোই একটা (লুকানো) billInput-এ থাকে, সেভ সেটাই পড়ে।
        val dens = resources.displayMetrics.density
        fun cpx(v: Int) = (v * dens).toInt()
        val mobileDigits = patient.mobile.filter { it.isDigit() }.takeLast(10)

        // বিলের মান ধরে রাখা লুকানো ঘর (view-tree-তে যোগ করা হয় না; সেভ শুধু .text পড়ে)।
        val billInput = EditText(this).apply {
            // B411: TYPE_CLASS_NUMBER একা নয় — প্রজেক্টের প্রমাণিত নিয়মে TEXT +
            // DigitsKeyListener (সব ফোনে কীবোর্ড খোলে)।
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
            setText(if (patient.bill > 0.0) patient.bill.toInt().toString() else "")
        }

        val cBlue = android.graphics.Color.parseColor("#1F6FE0")
        val cNavy = android.graphics.Color.parseColor("#0B2B59")
        val cGreen = android.graphics.Color.parseColor("#0B7A34")
        val cRed = android.graphics.Color.parseColor("#B0392B")
        val cFaint = android.graphics.Color.parseColor("#667085")

        fun openJourney() {
            if (mobileDigits.length == 10) {
                startActivity(android.content.Intent(this, PatientTimelineActivity::class.java).putExtra("mobile", patient.mobile))
            }
        }

        val billValueRef = arrayOfNulls<TextView>(1)
        fun openBillEdit() {
            val inp = EditText(this).apply {
                // B411: TEXT + DigitsKeyListener (সব ফোনে কীবোর্ড খোলে)।
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
                setText(billInput.text?.toString().orEmpty())
                setSelection(text?.length ?: 0)
            }
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Edit Total Bill"))
                .setView(inp)
                .setPositiveButton("Save") { _, _ ->
                    val v = inp.text?.toString()?.trim().orEmpty()
                    billInput.setText(v)
                    val bv = v.toDoubleOrNull() ?: 0.0
                    billValueRef[0]?.text = "₹${"%,.0f".format(bv)}"
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }

        // পিল বানানোর সাধারণ ফাংশন (BILL/PAID/DUE)
        fun pill(k: String, valueText: String, color: Int, bgHex: String, hint: String, onTap: (() -> Unit)?): LinearLayout {
            // 🔒 B570c (08.08.2026, TK-অনুমোদিত প্রুফ): ওয়েবের মতো integrated পিল —
            // ফ্ল্যাট (রাউন্ড কোণা নয়), হালকা রঙ, মাঝে ডিভাইডার (নিচের সারিতে), hint
            // faint-grey। ⛔ পিলের কাজ/ট্যাপ/টাকার লজিক এক অক্ষরও বদলায়নি — শুধু চেহারা।
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
                setBackgroundColor(android.graphics.Color.parseColor(bgHex))
                setPadding(cpx(4), cpx(9), cpx(4), cpx(9))
                if (onTap != null) { isClickable = true; setOnClickListener { onTap() } }
            }
            col.addView(TextView(this).apply { text = k; textSize = 8f; setTextColor(cFaint) })
            val vtv = TextView(this).apply {
                text = valueText; textSize = 13f; setTextColor(color)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            col.addView(vtv)
            if (k == "BILL") billValueRef[0] = vtv
            if (hint.isNotBlank()) col.addView(TextView(this).apply {
                text = hint; textSize = 7.5f; setTextColor(cFaint)
                setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, cpx(2), 0, 0)
            })
            return col
        }

        // ── পেশেন্ট কার্ড ──
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = cpx(10).toFloat(); setColor(android.graphics.Color.parseColor("#FAFBFC"))
                setStroke(cpx(1), android.graphics.Color.parseColor("#E2E8F0"))
            }
            setPadding(cpx(10), cpx(9), cpx(10), cpx(9))
        }
        // সারি ১: নাম (নীল-আন্ডারলাইন, চাপলে Full Journey) + রোগ
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row1.addView(TextView(this).apply {
            text = patient.name; textSize = 14f; setTextColor(cBlue)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { openJourney() }
        })
        if (patient.disease.isNotBlank()) row1.addView(TextView(this).apply {
            text = "🩸 ${patient.disease}"; textSize = 9f; setTextColor(cRed)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(cpx(6), cpx(1), cpx(6), cpx(1))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = cpx(9).toFloat(); setColor(android.graphics.Color.parseColor("#FDE7EA")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginStart = cpx(6) }
        })
        card.addView(row1)
        // সারি ২: ID (চাপলে Full Journey) + মোবাইল
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, cpx(3), 0, 0) }
        row2.addView(TextView(this).apply {
            text = patient.patientId; textSize = 9f; setTextColor(cNavy)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(cpx(6), cpx(1), cpx(6), cpx(1))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = cpx(9).toFloat(); setColor(android.graphics.Color.parseColor("#EAEFF5")) }
            setOnClickListener { openJourney() }
        })
        row2.addView(TextView(this).apply { text = "  📞 $mobileDigits"; textSize = 10.5f; setTextColor(cFaint) })
        card.addView(row2)
        // সারি ৩: ঠিকানা (ID+মোবাইলের নিচে) — গ্লোবাল দুই-লাইন নিয়মে; ফাঁকা হলে দেখায় না।
        if (patient.address.isNotBlank()) card.addView(TextView(this).apply {
            text = "📍 " + addressTwoLines(patient.address)
            textSize = 10f; setTextColor(android.graphics.Color.parseColor("#475467"))
            setLineSpacing(0f, 1.15f); setPadding(0, cpx(4), 0, 0)
        })

        // পিল সারি: BILL(৩-ট্যাপ এডিট) / PAID(ট্যাপ history) / DUE(ট্যাপ refund)
        val hasPaid = patient.paid > 0.0
        val paidTap: (() -> Unit)? = if (hasPaid) { { showCollectionDetails(CollectionRow("", "", patient.name, patient.mobile, patient.branch, "", 0.0, patient.patientId)) } } else null
        // 🔴🔴 V509 (২১.০৮.২০২৬, TK-রিপোর্ট: *"Registration করেছি, Visit fee দিয়েছে —
        // কিন্তু তাকে Visit Fee ফেরত দিতে পারছি না কেন?"*)। **আসল কারণ এখানেই ছিল**:
        // Refund-এর দরজাটা (DUE-তে চাপ) খুলত শুধু `patient.paid > 0` হলে, আর
        // Visit Fee কখনোই `paid`-এ ধরা হয় না — তাই যে রোগী শুধু Visit Fee
        // দিয়েছেন, তাঁর DUE চাপলে কিছুই হত না, ফর্মটাই খুলত না।
        // এখন দরজা খোলে `refundableTotal > 0` হলে (চিকিৎসার জমা **অথবা** Visit Fee)।
        // ⛔ PAID-এ চাপ (পুরনো পেমেন্টের তালিকা) আগের ঠিক একই শর্তে — বদলায়নি।
        val dueTap: (() -> Unit)? = if (patient.refundableTotal > 0.0) { { showRefundDialog(patient) } } else null
        // 🔒 B570c: হালকা integrated রঙ (ওয়েবের হুবহু): BILL #F7FAFF / PAID #F6FDF9 / DUE #FFF8F7।
        val billPill = pill("BILL", "₹${"%,.0f".format(patient.bill)}", cBlue, "#F7FAFF", "", null)
        billPill.isClickable = true
        billPill.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - billTapAt > 1400) billTapCount = 0
            billTapCount++; billTapAt = now
            if (billTapCount == 2) Toast.makeText(this, "Tap 1 more time to edit", Toast.LENGTH_SHORT).show()
            if (billTapCount >= 3) { billTapCount = 0; openBillEdit() }
        }
        val paidPill = pill("PAID", "₹${"%,.0f".format(patient.paid)}", cGreen, "#F6FDF9", "", paidTap)
        val duePill = pill("DUE", "₹${"%,.0f".format(due)}", cRed, "#FFF8F7", "", dueTap)
        // 🔒 B570c (TK-অনুমোদিত প্রুফ): পিলগুলো এখন এক টানা (integrated) — মাঝে সরু
        // ডিভাইডার, ফাঁক নেই, উপরে একটা লাইন (কার্ডের বাকি অংশ থেকে আলাদা)। ⛔ পিলের
        // ট্যাপ/৩-ট্যাপ-এডিট/history/refund সব আগের মতোই।
        fun pillDivider() = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#EEF2F7"))
            layoutParams = LinearLayout.LayoutParams(cpx(1), LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val pillTopLine = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#EEF2F7"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cpx(1)).also { it.topMargin = cpx(8) }
        }
        card.addView(pillTopLine)
        val pills = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pills.addView(billPill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pills.addView(pillDivider())
        pills.addView(paidPill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pills.addView(pillDivider())
        pills.addView(duePill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(pills)

        container.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = cpx(2) })

        // 🔒 B562 — আগের আলাদা "📜 View / Edit Past Payments" ও "💸 Refund"
        // বোতাম দুটো এখান থেকে তুলে দেওয়া হলো (TK-নির্দেশ, প্রুফ-অনুমোদিত)।
        // একই কাজ এখন উপরের কার্ডে: PAID চাপলে `showCollectionDetails(...)`
        // (বিগত পেমেন্ট View/Edit), DUE চাপলে `showRefundDialog(patient)` —
        // দুটোই শুধু patient.paid > 0 হলে সক্রিয় (আগের ঠিক একই শর্ত)। ডাকা
        // ফাংশন/লজিক এক অক্ষরও বদলায়নি, শুধু বোতামের বদলে সংখ্যায় ট্যাপ।

        // (পুরনো দৃশ্যমান "Total Bill Amount" ঘরটি সরানো হয়েছে — বিল এখন উপরের
        //  BILL পিলে; ৩-ট্যাপে openBillEdit() ছোট পপ-আপে এডিট হয়, আর মান আগের
        //  মতোই উপরের লুকানো billInput-এ থাকে যেটা সেভ-লজিক পড়ে।)

        val amtLabel = TextView(this).apply { text = "$nextLabel Amount"; textSize = 12f; setPadding(0, 16, 0, 4) }
        container.addView(amtLabel)
        val amtInput = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.") }
        container.addView(amtInput)
        // Advance amount 3-tap protection: once entered and focus leaves, it
        // becomes read-only so a wrong amount can't change by accident; the
        // field stays clickable and 3 taps re-enable editing.
        amtTapCount = 0
        amtInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && amtInput.text.toString().isNotBlank()) {
                amtInput.isFocusable = false
                amtInput.isFocusableInTouchMode = false
                amtInput.isCursorVisible = false
            }
        }
        amtInput.setOnClickListener {
            if (amtInput.isFocusable) return@setOnClickListener  // already editable
            val now = System.currentTimeMillis()
            if (now - amtTapAt > 1400) amtTapCount = 0
            amtTapCount++; amtTapAt = now
            if (amtTapCount == 2) Toast.makeText(this, "Tap 1 more time to edit", Toast.LENGTH_SHORT).show()
            if (amtTapCount >= 3) {
                amtInput.isFocusableInTouchMode = true; amtInput.isFocusable = true; amtInput.isCursorVisible = true
                amtInput.requestFocus()
                Toast.makeText(this, "Advance unlocked for editing", Toast.LENGTH_SHORT).show()
            }
        }

        val modeLabel = TextView(this).apply { text = "Payment Mode"; textSize = 12f; setPadding(0, 16, 0, 4) }
        container.addView(modeLabel)
        // 🔒 B570 (08.08.2026, TK-অনুমোদিত প্রুফ): CASH/ONLINE পাশাপাশি টগল বক্স
        // (আগের dropdown/Spinner নয়)। চাপলে সবুজ হাইলাইট (✓); নির্বাচিত মান
        // `selectedPayMode`-এ থাকে, সেভ সেটাই পড়ে (আগের modeSpinner.selectedItem-এর
        // বদলে)। ⛔ টাকার লজিক অপরিবর্তিত — শুধু বাছার চেহারা বদলেছে।
        var selectedPayMode = "CASH"
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun makeModeBox(label: String): TextView = TextView(this).apply {
            text = label; gravity = android.view.Gravity.CENTER
            textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(cpx(13), cpx(13), cpx(13), cpx(13))
            isClickable = true; isFocusable = true
        }
        val cashBox = makeModeBox("💵 CASH")
        val onlineBox = makeModeBox("🏦 ONLINE")
        fun styleModeBox(box: TextView, on: Boolean) {
            box.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = cpx(12).toFloat()
                setColor(android.graphics.Color.parseColor(if (on) "#E9F7EE" else "#F8FBFF"))
                setStroke(cpx(2), android.graphics.Color.parseColor(if (on) "#0B7A34" else "#D8E4F2"))
            }
            box.setTextColor(android.graphics.Color.parseColor(if (on) "#0B7A34" else "#5B6B81"))
        }
        fun paintModes() { styleModeBox(cashBox, selectedPayMode == "CASH"); styleModeBox(onlineBox, selectedPayMode == "ONLINE") }
        cashBox.setOnClickListener { selectedPayMode = "CASH"; paintModes() }
        onlineBox.setOnClickListener { selectedPayMode = "ONLINE"; paintModes() }
        paintModes()
        modeRow.addView(cashBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = cpx(10) })
        modeRow.addView(onlineBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(modeRow)

        // TK-DECISION (2026-07-27): Remarks was never mandatory here, and TK
        // asked for it to be dropped from the View All -> Advance path only
        // ("শুধু View All-এর Advance-এ বাদ, বাকি দুই জায়গায় থাকুক"). The field
        // itself still EXISTS and is still read on save -- it is simply not
        // shown in that one path, so it saves blank, which the app already
        // handles (a blank remark is stored as the payment label and Timeline
        // treats it as "no remark"). Payment Collection's own ADD TREATMENT
        // PAYMENT and the Follow-up card's ADVANCE HERE are untouched.
        val remarkInput = EditText(this)
        // 🔒 TK'S DECISION (27.07.2026, ফটো-প্রুফ দেখে পাশ): the Remarks box is
        // now removed from this dialog ENTIRELY (it used to be hidden only in the
        // View All -> Advance path).
        //
        // TK's reason, in his own words: "পেমেন্ট নেওয়ার সময় সেই পেশেন্টের কোন
        // ধরনের ট্রিটমেন্ট হবে সেটা তো আগে থেকে জানা যায় না। ট্রিটমেন্ট হওয়ার পরই
        // না ট্রিটমেন্ট প্রোগ্রেস কি হবে সেটা জানা যাবে।" Money is taken BEFORE the
        // treatment happens, so whatever is typed here can never be that day's real
        // Treatment Progress -- yet the Report Card's Progress column reads exactly
        // this text. Progress is now written only AFTER the treatment, from Chamber
        // Attendance's own "Treatment Progress" box (or the Report Card's own
        // 3-tap edit).
        //
        // The field OBJECT is deliberately kept, because the save code below still
        // reads it -- it is simply never shown, so it always saves blank. A blank
        // remark is stored as the payment's own label
        // (PaymentModel.buildTreatmentPaymentRow), which the Timeline already
        // treats as "no remark written" and the Report Card already keeps out of
        // Progress. So no old data changes and no other screen is affected.
        //
        // NOTHING ELSE MOVES: Total Bill (with its locked grey look and 3-tap
        // unlock), Amount, Payment Mode, the backdate row and the Cancel/Save
        // buttons all keep their exact places and design.

        // TK-REQUESTED ADDITION (2026-07-24): "Backdate Payment" workflow.
        // Defaults to today (== no backdate, nothing changes for the normal
        // case). Picking any other date does NOT save anything by itself --
        // the Save button below decides what to do based on this value.
        // Only past dates allowed (datePicker.maxDate = now), matching the
        // whole point of this feature (recording when money was ACTUALLY
        // already collected, never a future promise).
        // 🔴 TK-REPORTED (30.07.2026 রাত, Kishanganj স্টাফের হিন্দি বার্তা):
        // এই তিনটে টেক্সটও আলাদা Dialog-এর ভিতরে ছিল, NoBengali.s() দিয়ে
        // মোড়ানো হয়নি। এখন মোড়ানো হলো (দুটোরই আগে থেকে অনুবাদ প্রস্তুত ছিল,
        // শুধু ব্যবহার হচ্ছিল না)।
        // 🔒 B562 (TK-নির্দেশ): লেবেলের শুরুর ⏰ তুলে দেওয়া হলো — ওই ইমোজি
        // ফোনের ফন্টে "17 July" এঁকে বিভ্রান্ত করত (B313/B202)। NoBengali চাবি
        // ⏰ ছাড়াই আছে, তাই ইংরেজি অনুবাদ আগের মতোই ঠিক থাকে।
        // 🔒 B570 (TK-নির্দেশ): আলাদা লেবেল-লাইন বাদ; বক্সের ভেতরেই ধূসর রঙে
        // "প্রকৃত জমার তারিখ" (placeholder-এর মতো)। তারিখ বাছলে গাঢ় রঙে সেই তারিখ।
        // ⛔ ব্যাকডেট-লজিক অপরিবর্তিত — না বাছলে আজকের তারিখই ধরা হয়।
        val payDateGrey = android.graphics.Color.parseColor("#9AA4B0")
        val payDateDark = android.graphics.Color.parseColor("#10223A")
        var pickedActualDate = PaymentModel.today()
        val dateValue = TextView(this).apply {
            text = NoBengali.s("প্রকৃত জমার তারিখ")
            setTextColor(payDateGrey)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val d = resources.displayMetrics.density
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            setOnClickListener {
                val cal = java.util.Calendar.getInstance()
                android.app.DatePickerDialog(this@PaymentActivity, { _, y, m, d ->
                    val cal2 = java.util.Calendar.getInstance().apply { set(y, m, d) }
                    val iso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal2.time)
                    pickedActualDate = iso
                    if (iso == PaymentModel.today()) {
                        text = NoBengali.s("প্রকৃত জমার তারিখ"); setTextColor(payDateGrey)
                    } else {
                        text = NoBengali.s("প্রকৃত জমা: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(cal2.time)}"); setTextColor(payDateDark)
                    }
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        }
        container.addView(dateValue)

        // Premium styling (look only — no field/logic change): rounded inputs +
        // navy bold labels, matching the Registration screen.
        val d = resources.displayMetrics.density
        val pad = (12 * d).toInt(); val padH = (14 * d).toInt(); val gap = (6 * d).toInt()
        for (i in 0 until container.childCount) {
            val v = container.getChildAt(i)
            when (v) {
                is EditText -> {
                    // billInput already got its grey "locked" background above
                    // when patient.billLocked is true — don't overwrite it here.
                    if (!(v === billInput && patient.billLocked)) {
                        v.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                    }
                    v.setPadding(padH, pad, padH, pad)
                    (v.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { v.layoutParams = it }).topMargin = gap
                }
                is TextView -> {
                    // 🔒 B562: infoText আর নেই (রোগীর তথ্য এখন উপরের কার্ডে,
                    // যেটা container-এর সরাসরি TextView-সন্তান নয়, তাই এই লুপ
                    // ছোঁয় না)। বাকি লেবেলগুলো (Amount / Payment Mode) maroon-bold।
                    // 🔒 B570: তারিখ-বক্স (dateValue) বাদ — ওটা ধূসর placeholder
                    // "প্রকৃত জমার তারিখ", তাই maroon-bold করা হয় না।
                    if (v !== dateValue) {
                        v.setTextColor(android.graphics.Color.parseColor("#6B1E2B")); v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
                    }
                }
            }
        }

        val scroll = android.widget.ScrollView(this).apply { addView(container) }

        // TK APPROVED (2026-07-15): premium shell — navy header + rounded card
        // + colored Cancel/Save buttons, same pattern already used for Edit
        // Payment in this exact file. Every field/label/input above is
        // unchanged; only the plain default AlertDialog wrapper is replaced.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = (20 * d).toFloat()
                // 🔒 B562 (TK-নির্দেশ): হেডার সবুজ হওয়ায় কার্ডের বর্ডারও সবুজ।
                setStroke((1 * d).toInt(), android.graphics.Color.parseColor("#0B7A34"))
            }
        }
        root.addView(TextView(this).apply {
            text = "💰 Add Treatment Payment"
            textSize = 16.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            // 🔒 B562 (TK-অনুমোদিত): হেডার মেরুন → সবুজ gradient (Payment
            // Collection পর্দার সবুজের সাথে মেলে)। শুধু রঙ; কিছুই বদলায়নি।
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(android.graphics.Color.parseColor("#0B5F2E"), android.graphics.Color.parseColor("#12A04A"))
            )
            val p = (16 * d).toInt(); setPadding(p, p, p, p)
        })
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * d).toInt(); setPadding(p, p, p, (4 * d).toInt())
            // 🔒 B562: আগে ফিক্সড 380dp ছিল (২ বোতাম + Total Bill ঘর মিলিয়ে
            // লম্বা কনটেন্টের জন্য)। ওগুলো সরে যাওয়ায় এখন content-অনুযায়ী উচ্চতা
            // (WRAP) — বেশি লম্বা হলে ScrollView নিজেই স্ক্রল করবে, তাই কম্প্যাক্ট।
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(body)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = (16 * d).toInt(); setPadding(p, (8 * d).toInt(), p, p)
        }
        // 🔒 B564 (TK-অনুমোদিত প্রুফ) — ৪ বোতাম: Cancel · Share · Print · Save।
        // 🔒 B570 (TK-নির্দেশ): বোতামের লেখা যেন এক লাইনে থাকে ("CANC EL"-এর মতো
        // ভেঙে না যায়) — letterSpacing=0, maxLines=1, ও ছোট ভেতরের প্যাডিং।
        fun payActionBtn(label: String, tintHex: String): com.google.android.material.button.MaterialButton =
            com.google.android.material.button.MaterialButton(this).apply {
                text = label; textSize = 10.5f
                setTextColor(android.graphics.Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(tintHex))
                cornerRadius = (11 * d).toInt()
                insetTop = 0; insetBottom = 0
                minWidth = 0; minimumWidth = 0
                letterSpacing = 0f; maxLines = 1
                setPadding((3 * d).toInt(), 0, (3 * d).toInt(), 0)
            }
        val cancelBtn = com.google.android.material.button.MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "CANCEL"; textSize = 10.5f
            setTextColor(android.graphics.Color.parseColor("#17304F"))
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#0B7A34"))
            cornerRadius = (11 * d).toInt()
            insetTop = 0; insetBottom = 0; minWidth = 0; minimumWidth = 0
            letterSpacing = 0f; maxLines = 1
            setPadding((3 * d).toInt(), 0, (3 * d).toInt(), 0)
        }
        val shareBtn = payActionBtn("🟢 SHARE", "#12A04A")
        val printBtn = payActionBtn("🖨 PRINT", "#1F6FE0")
        val saveBtn = payActionBtn("💾 SAVE", "#0B7A34")
        actionRow.addView(cancelBtn, LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f).also { it.marginEnd = (5 * d).toInt() })
        actionRow.addView(shareBtn, LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f).also { it.marginEnd = (5 * d).toInt() })
        actionRow.addView(printBtn, LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f).also { it.marginEnd = (5 * d).toInt() })
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f))
        root.addView(actionRow)

        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this).setView(root).setCancelable(true).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        cancelBtn.setOnClickListener { dialog.dismiss() }
        // 🔒 B564: Share/Print চাপলে Save-এর হুবহু একই সেভ-পথ চলে; শুধু আগে থেকে
        // pendingReceiptMode বসানো থাকে, যেটা সেভ *নিশ্চিত* হলে doDirectSave পড়ে
        // WhatsApp/প্রিন্ট করে। (performClick = Save বোতামের অবিকল আচরণ।)
        shareBtn.setOnClickListener { pendingReceiptMode = "share"; saveBtn.performClick() }
        printBtn.setOnClickListener { pendingReceiptMode = "print"; saveBtn.performClick() }
        // TK-REQUESTED (2026-07-27): opened straight from Patient Details, so
        // closing it (CANCEL, Back, tap outside, or a finished save) must go
        // straight back there rather than leaving the staff on the Payment
        // Collection screen. Only applies to that one caller.
        // 🔒 B564: print রসিদ সিস্টেমের হাতে যাওয়ার আগে যেন finish না হয়
        // (payDeferFinish) — নাহলে WebView মরে গিয়ে প্রিন্ট হবে না।
        if (directFormOnly) {
            dialog.setOnDismissListener { if (!isFinishing && !payDeferFinish) finish() }
        }
        // TK-REPORTED CRITICAL BUG FIX (2026-07-26): identical duplicate-payment
        // guard to the Follow-up Advance / Nth Payment dialogs. Saving takes a
        // few seconds on a slow connection and this button stayed tappable, so
        // an impatient second tap wrote a SECOND real payment row for the same
        // money. Set only after validation passes, and always cleared in a
        // finally block, so a rejected or failed tap never leaves it stuck.
        var paySaving = false
        // 🆕 B337 (03.08.2026) — Master নিজে ব্যাকডেট করলে, বা কোনো স্টাফের
        // সক্রিয় Grant থাকলে — দুটো ক্ষেত্রেই এই একই সরাসরি-সেভ পথ ব্যবহার
        // হয় (আগে এই লজিক শুধু Master-এর জন্য ইনলাইন ছিল; এখন একটা স্থানীয়
        // (local) ফাংশনে বার করা হলো যাতে Grant-থাকা স্টাফও একই পথ
        // পুনর্ব্যবহার করতে পারেন — dialog/paySaving/directFormOnly-এর
        // মতো এই স্ক্রিনের নিজস্ব ভেরিয়েবলগুলো ব্যবহার করতে হয় বলে এটা
        // ক্লাসের আলাদা ফাংশন না করে এখানেই (local) রাখা হলো। সেভ করার
        // আসল লজিক এক অক্ষরও বদলায়নি।)
        fun doDirectSave(
            isBackdated: Boolean, pickedActualDate: String, patient: PatientBillInfo,
            billVal: Double, amtVal: Double, user: NativeUser, autoApprovedByGrant: Boolean,
            receiptMode: String   // 🔒 B564: none | share | print — সেভ নিশ্চিত হলে তবেই
        ) {
            paySaving = true
            lifecycleScope.launch {
                try {
                    val ok = withContext(Dispatchers.IO) {
                        repository.saveTreatmentPayment(
                            patient, billVal, amtVal,
                            selectedPayMode, remarkInput.text.toString().trim(), user.mobile,
                            overrideDate = if (isBackdated) pickedActualDate else null,
                            backdateRequestedBy = if (isBackdated) user.mobile else null,
                            // 🆕 B337: Grant দিয়ে সেভ হলে অডিট-ট্রেইলে স্পষ্ট
                            // থাকে এটা লাইভ Master-অনুমোদন না, আগে থেকে দেওয়া
                            // সাময়িক অনুমতি দিয়ে হয়েছে।
                            backdateApprovedBy = if (isBackdated) (if (autoApprovedByGrant) "GRANT:${user.mobile}" else user.mobile) else null
                        )
                    }
                    val msg = when {
                        !ok -> "Failed — check connection"
                        billVal <= 0.0 && !patient.billLocked -> "Advance saved — Total Bill not set yet, please add it later"
                        else -> "Treatment payment saved"
                    }
                    Toast.makeText(this@PaymentActivity, msg, if (ok && billVal <= 0.0 && !patient.billLocked) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                    if (ok) {
                        if (!directFormOnly) loadSummary()
                        // 🔒 B564: টাকা *নিশ্চিত* সেভ হয়েছে — এবার (চাইলে) রসিদ।
                        val modeStr = selectedPayMode
                        val dateForReceipt = if (isBackdated) pickedActualDate else PaymentModel.today()
                        when (receiptMode) {
                            "share" -> {
                                // WhatsApp বাছাই-পপ খোলা থাকবে; বাছাই/বাতিলের পরেই dialog বন্ধ
                                // (directForm হলে তখনই finish, নাহলে শুধু dialog বন্ধ)।
                                payDeferFinish = directFormOnly
                                WhatsAppMessageChooser.sendGeneric(
                                    this@PaymentActivity,
                                    treatmentReceiptText(patient, amtVal, modeStr, user.mobile, dateForReceipt)
                                ) {
                                    payDeferFinish = false
                                    dialog.dismiss()
                                    if (directFormOnly && !isFinishing) finish()
                                }
                            }
                            "print" -> {
                                if (directFormOnly) payDeferFinish = true
                                printTreatmentReceipt(patient, amtVal, modeStr, user.mobile, dateForReceipt, finishAfter = directFormOnly)
                                dialog.dismiss()
                            }
                            else -> dialog.dismiss()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@PaymentActivity, "Could not save — check connection and try again", Toast.LENGTH_SHORT).show()
                } finally { paySaving = false }
            }
        }
        saveBtn.setOnClickListener {
            if (paySaving) return@setOnClickListener
            // 🔒 B564: Share/Print চাপলে ওরা pendingReceiptMode বসিয়ে এই বোতামই
            // performClick করে। এখানে সেটা ধরে নিয়ে সঙ্গে সঙ্গে "none" করে দিই —
            // যাতে সেভ ব্যর্থ/আটকে গেলেও পরের সাধারণ Save-এ রসিদ লিক না করে।
            val receiptMode = pendingReceiptMode
            pendingReceiptMode = "none"
            val billVal = billInput.text.toString().toDoubleOrNull() ?: 0.0
            val amtVal = amtInput.text.toString().toDoubleOrNull() ?: 0.0
            // TK-REQUESTED (2026-07-22): if the (locked) bill was unlocked and
            // changed and NO new payment amount is entered, this is a bill-only
            // correction. Anyone may fix the bill on its own -- no forced
            // payment; correctBill() writes a who/when/old→new audit row.
            val billOnlyCorrection = patient.billLocked && amtVal <= 0 && billVal > 0 && billVal != patient.bill
            if (billOnlyCorrection) {
                FieldError.clear(billInput); FieldError.clear(amtInput)
                lifecycleScope.launch {
                    try {
                        val ok = withContext(Dispatchers.IO) { repository.correctBill(patient, billVal, user.mobile) }
                        Toast.makeText(this@PaymentActivity, if (ok) "Bill corrected" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                        if (ok) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                    } catch (e: Exception) {
                        Toast.makeText(this@PaymentActivity, "Could not save — check connection and try again", Toast.LENGTH_SHORT).show()
                    }
                }
                return@setOnClickListener
            }
            // TK-DECISION (2026-07-27, in his own words): "অ্যাডভান্স এখন নিয়ে
            // নিক, বিল প্রয়োজনে পরে বসাতে পারবেন... যদি বিল নাও বসায় তাও যেন
            // এডভান্স পেমেন্ট নেওয়া যায়" -- staff are busy at the counter, so
            // a FIRST payment (Advance) must never be blocked for a missing
            // Total. The Follow-up card already worked this way since
            // 2026-07-26; this screen (and View All -> Advance, which opens
            // this same form) still demanded a Total, so the two disagreed.
            // The 2nd payment onwards still REQUIRES the bill, exactly as
            // TK locked it -- that is what stops the "Bill ₹0 / Report Card
            // hidden" case (Raj Routh).
            val isFirstPayment = patient.paid <= 0.0
            val billOk = when {
                patient.billLocked -> patient.bill > 0
                billVal > 0 -> true
                isFirstPayment -> true
                else -> false
            }
            if (!billOk && !isFirstPayment && !patient.billLocked && amtVal > 0) {
                FieldError.validate(listOf<Triple<View, Boolean, String>>(
                    Triple(billInput, false, "This patient's Bill has not been created yet — please create the Bill first")
                ))
                Toast.makeText(this, "This patient's Bill has not been created yet — please create the Bill first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // TK-REQUESTED (2026-07-22): allow a BILL-ONLY correction. If the
            // locked bill was unlocked (3-tap) and changed, and no new payment
            // amount is entered, just update the bill (anyone may) and log WHO
            // edited it -- instead of forcing a new payment.
            val billChanged = patient.billLocked && billVal > 0 && billVal != patient.bill
            if (billChanged && amtVal <= 0) {
                val vBill = FieldError.validate(listOf<Triple<View, Boolean, String>>(
                    Triple(billInput, billVal > 0, "Enter a valid Total cost")
                ))
                if (vBill != null) { Toast.makeText(this, vBill, Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val oldBill = patient.bill
                lifecycleScope.launch {
                    try {
                        val ok = withContext(Dispatchers.IO) {
                            repository.updateBillOnly(patient, billVal, oldBill, user.mobile, user.name)
                        }
                        Toast.makeText(this@PaymentActivity, if (ok) "Bill corrected by ${user.name}" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                        if (ok) { if (!directFormOnly) loadSummary(); dialog.dismiss() }
                    } catch (e: Exception) {
                        Toast.makeText(this@PaymentActivity, "Could not save — check connection and try again", Toast.LENGTH_SHORT).show()
                    }
                }
                return@setOnClickListener
            }
            val vmsg = FieldError.validate(listOf<Triple<View, Boolean, String>>(
                Triple(billInput, billOk, "Enter a valid Total cost"),
                Triple(amtInput, amtVal > 0, "Enter a valid Advance amount")
            ))
            if (vmsg != null) {
                Toast.makeText(this, vmsg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TK-REQUESTED ADDITION (2026-07-24): "Backdate Payment"
            // workflow. pickedActualDate == today() is the normal/default
            // case -- exactly the old direct-save behaviour, untouched.
            // Only a genuinely different (past) date changes anything.
            val isBackdated = pickedActualDate != PaymentModel.today()
            // 🔒 TK'S LOCKED RULE (27.07.2026): Bill / Advance / any payment may
            // only be taken by the PATIENT'S OWN branch (its staff or its
            // doctor) or by Master. Checked here first so the staff sees the
            // reason plainly; PaymentRepository refuses it again as a backstop.
            if (!MoneyBranchGuard.canTakeMoney(this@PaymentActivity, patient.branch, patient.patientId)) {
                Toast.makeText(this@PaymentActivity, MoneyBranchGuard.blockMessage(patient.branch), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isBackdated && user.role != "master") {
                // 🆕 B337 (03.08.2026, TK-নির্দেশ) — Master যদি এই স্টাফকে এই
                // তারিখের জন্য আগে থেকেই সাময়িক অনুমতি দিয়ে থাকেন
                // (`BackdatePaymentGrant`), তাহলে প্রতিবার আলাদা অনুরোধ
                // পাঠানোর দরকার নেই — সরাসরি সেভ হয়ে যাবে। ⛔ পুরনো
                // request-to-master পথ (নিচে) এক অক্ষরও বদলায়নি — অনুমতি
                // না থাকলে ঠিক আগের মতোই আচরণ করে।
                lifecycleScope.launch {
                    val hasGrant = withContext(Dispatchers.IO) {
                        try { BackdatePaymentGrant.isGrantedNow(user.mobile, pickedActualDate) } catch (_: Throwable) { false }
                    }
                    if (hasGrant) {
                        doDirectSave(isBackdated, pickedActualDate, patient, billVal, amtVal, user, autoApprovedByGrant = true, receiptMode = receiptMode)
                        return@launch
                    }
                    // Staff: never saves a real payment directly -- goes to
                    // Master for approval first (payment_backdate_requests
                    // table only, "payments" table untouched until approved).
                    try {
                        val ok = withContext(Dispatchers.IO) {
                            repository.requestBackdatePayment(
                                patient, billVal, amtVal, selectedPayMode,
                                remarkInput.text.toString().trim(), pickedActualDate, user.mobile, user.name.ifBlank { user.mobile }
                            )
                        }
                        Toast.makeText(
                            this@PaymentActivity,
                            if (ok) "Request sent to Master — it will be added as a payment after approval" else "Failed — check your connection",
                            Toast.LENGTH_LONG
                        ).show()
                        if (ok) dialog.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(this@PaymentActivity, "Could not send request — check connection and try again", Toast.LENGTH_SHORT).show()
                    }
                }
                return@setOnClickListener
            }
            // 🔒 খাতার সারি B52 (TK, 28.07.2026 রাত): আজ এই রোগীর নামে ইতিমধ্যে
            // টাকা নেওয়া হয়ে থাকলে সেভ করার আগে একবার জিজ্ঞাসা করা হয় (ধীর
            // ইন্টারনেটে না বুঝে দ্বিতীয়বার সেভ করা আটকাতে)। আজ কিছু নেওয়া না
            // হলে কোনো পপ-আপ আসে না, তখন নিচের সেভ হুবহু আগের মতোই চলে।
            // ব্যাকডেট পেমেন্টে এই প্রশ্ন আসে না (ওটা মাস্টারের নিজের সিদ্ধান্ত)।
            PaymentDayGuard.confirmIfAlreadyPaidToday(
                this@PaymentActivity,
                if (isBackdated) 0.0 else repository.paidOnDateFor(patient.id),
                patient.name,
                repository.nextLabelFor(patient.id, if (isBackdated) pickedActualDate else "")
            ) {
                doDirectSave(isBackdated, pickedActualDate, patient, billVal, amtVal, user, autoApprovedByGrant = false, receiptMode = receiptMode)
            }
        }
        dialog.show()
    }
}
