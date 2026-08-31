package com.tkbiswas.pilesclinic.native

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tkbiswas.pilesclinic.databinding.ActivityChamberAttendanceBinding
import com.tkbiswas.pilesclinic.print.ChamberRegisterPdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume   // 🔵 V531
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * TK APPROVED FEATURE, staged build (2026-07-16) -- "Chamber Attendance":
 * see 00_PROJECT_STATE_MASTER_NOTE.md for the full agreed plan from the
 * discussion with TK before changing anything here.
 *
 * This screen only READS from Registration/Payment/Follow-up data (via
 * ChamberAttendanceRepository) and only ever WRITES by opening the SAME
 * existing screens (PaymentActivity, RegistrationActivity) or the SAME
 * existing functions (FollowUpRepository.updateRemark,
 * ChamberAttendanceRepository.markArrived -- itself just a normal
 * SupabaseClient.upsert("payments", ...) call, same table/mechanism every
 * other payment already uses) every other screen already uses -- nothing
 * here is a second, separate data-entry path.
 *
 * TK-REQUESTED CHANGE (2026-07-16), a big session of decisions, all
 * reflected below:
 *  - Branch dropdown + date chip combined into one colored row (was a plain
 *    white spinner + separate date text).
 *  - The three counts (Expected/Arrived/No-show) are now tappable cards
 *    that show WHO is in that count.
 *  - Enquiry removed from this screen entirely; "Add Walk-in" replaced with
 *    two side-by-side actions: "Add New (Registration)" and "Search
 *    (existing patient)" -- the Search flow lets staff mark an existing
 *    patient Arrived with one tap, without needing a Payment/Registration
 *    that day (e.g. a free follow-up visit).
 *  - Row color now reflects status: Arrived = light green, No-show = plain
 *    light grey (see ChamberAttendanceAdapter / bg_chamber_row_*.xml).
 *  - Picking a FUTURE date shows that date's Expected list with a CALL
 *    button (no Payment/Remark -- backdating those still makes no sense),
 *    so staff can ring tomorrow's expected patients today to confirm.
 *  - "Today's Collection" totals moved to the bottom of the screen.
 *  - New "Close Chamber (Save & Print Arrived)" button at the very bottom:
 *    if any Arrived patient still has no Treatment/Remark, the 1st and 2nd
 *    tap show a warning and open that patient's Treatment box instead of
 *    closing; a 3rd tap proceeds anyway (TK's explicit instruction -- a
 *    busy day with many patients must not be blocked forever). "Print"
 *    here means a share-as-text summary of today's Arrived list (the
 *    existing "Share as Text" pattern already used elsewhere in the app) --
 *    NOT a change to ClinicPdfBuilder.kt, which stays OWNER LOCKED and
 *    untouched.
 *
 * Past dates are shown VIEW-ONLY (no Payment/Remark quick actions) -- this
 * matches the app's existing rule that a payment/remark edit is only
 * allowed same-day (Master aside), see PatientTimelineActivity's 3-tap
 * payment edit for the same standing rule, reused here rather than
 * inventing a new one.
 */
class ChamberAttendanceActivity : AppCompatActivity() {

    /**
     * 🔵🔒 V531 (২২.০৮.২০২৬, TK-নির্দেশ) — *"Mark Expected"* বাক্সে এক নম্বরে
     * **সত্যিই একাধিক আলাদা রোগী** থাকলে তবেই এক লাইনে জিজ্ঞাসা।
     * ⛔ একজন থাকলে (রোজকার ৯৯%) এই বাক্স কখনো দেখা যায় না।
     * (হুবহু সেই পর্দা যেটা Payment · Print · Doctor Visit-এ চলছে।)
     */
    private suspend fun askWhichChamberPatient(
        mobile: String, people: List<org.json.JSONObject>
    ): org.json.JSONObject? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val labels = people.map { r ->
            (r.s("name").ifBlank { "UNKNOWN" }) + "\n" + (r.s("patientId").ifBlank { "-" })
        }.toTypedArray()
        var done = false
        fun finishWith(v: org.json.JSONObject?) {
            if (done) return
            done = true
            if (cont.isActive) cont.resume(v)
        }
        if (isFinishing || isDestroyed) { finishWith(null); return@suspendCancellableCoroutine }
        AlertDialog.Builder(this)
            .setTitle("\uD83D\uDCDE $mobile \u2014 which patient?")
            .setItems(labels) { _, which -> finishWith(people.getOrNull(which)) }
            .setNegativeButton("Cancel") { _, _ -> finishWith(null) }
            .setOnCancelListener { finishWith(null) }
            .show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
    }

    // V450: a live mobile lookup can prove that follow-up rows exist but every
    // one is terminal. Keep that state separate from "no row exists" so the
    // self-heal paths below can never recreate a rejected row as Active.
    private val terminalFollowUpSentinel = "__TERMINAL_FOLLOWUP__"

    private lateinit var binding: ActivityChamberAttendanceBinding
    private lateinit var adapter: ChamberAttendanceAdapter
    // 🔴🎨🔒 B448 — নতুন, আলাদা, ছোট Adapter (উপরের ৫টা বক্স), শুধু
    // ConcatAdapter-এর মাধ্যমে জুড়ে দেওয়া হয় — `adapter`-এর একটা লাইনও
    // ছোঁয়া হয়নি।
    private val headerAdapter = ChamberHeaderAdapter()
    // 🔒🎨 B473 (05.08.2026, TK-নির্দেশ) — কলাম-হেডার (PATIENT/TREATMENT
    // PROGRESS/FEES/CASH/ONLINE) এখন এই আলাদা ছোট Adapter দিয়ে ৫টা
    // বক্সের ঠিক পরের (দ্বিতীয়) আইটেম — দেখুন ChamberColumnHeaderAdapter.kt।
    private val columnHeaderAdapter = ChamberColumnHeaderAdapter()
    // 🔴🎨🔒 B448 — Expected/Arrived সংখ্যা এখন header-এর View-এ বসে, যেটা
    // RecyclerView লেট-বাউন্ড (দেরিতে তৈরি হতে পারে) — তাই মানটা এখানেও
    // মনে রাখা হয়, যাতে View তৈরি হওয়ার আগে সংখ্যা এলেও হারিয়ে না যায়,
    // পরে View তৈরি হলেই সঙ্গে সঙ্গে বসে যায় (উপরের bindViews দেখুন)।
    private var lastKnownExpectedCount = "0"
    private var lastKnownArrivedCount = "0"
    // 🆕 (07.08.2026) — কাল যাদের আসার কথা তার সংখ্যা (মাঝের নতুন বোতামে)।
    private var lastKnownKalAsarCount = "0"
    // 🔴 B448 — একইভাবে, ৩টা বোতামের enabled/alpha (readOnly অবস্থা) মনে
    // রাখা হয়, যাতে View তৈরি হওয়ার আগে applyDayState() চললেও অবস্থাটা
    // হারিয়ে না যায় — bindViews-এ আবার বসে।
    private var lastKnownReadOnly = false
    private fun setExpectedArrivedCounts(expected: String, arrived: String) {
        lastKnownExpectedCount = expected
        lastKnownArrivedCount = arrived
        headerAdapter.currentHolder()?.let { vh ->
            vh.tvExpectedCount.text = expected
            vh.tvArrivedCount.text = arrived
        }
    }

    // 🆕 (07.08.2026) — মাঝের "কাল আসার কথা" বোতামের সংখ্যা বসায়। View দেরিতে
    // তৈরি হলেও মান হারায় না (lastKnownKalAsarCount মনে রাখা, bindViews-এ আবার বসে)।
    private fun setKalAsarCount(count: String) {
        lastKnownKalAsarCount = count
        headerAdapter.currentHolder()?.let { vh -> vh.tvKalAsarCount.text = count }
    }

    /* 🆕 (07.08.2026) — কাল ওই ব্রাঞ্চে কতজন আসার কথা (chamber_expected, তারিখ =
     * কাল), সেই সংখ্যা একবার হালকা রিড করে বোতামে বসায়। ⛔ একটাই ছোট query
     * (তারিখ-ফিল্টার করা), Supabase ফ্রি-প্ল্যান নিরাপদ; ব্যর্থ হলে চুপচাপ ছেড়ে
     * দেয় (কখনো ০ দেখিয়ে বিভ্রান্ত করে না — আগের মানই থাকে)।
     *
     * 🟢🔒🔒 V635 (২৪.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ — "ব্রাঞ্চ সিলেক্ট করা আছে
     * জলপাইগুড়ি, কিন্তু 'আসার কথা'-তে চাপ দিলে কিষানগঞ্জের এনকোয়ারি কেন?")
     * — **আসল কারণ (কোড ধরে যাচাই):** এই ফাংশন ব্রাঞ্চ ঠিক করত সরাসরি
     * `user.branch` (যিনি লগইন করেছেন, তাঁর নিজস্ব/হোম ব্রাঞ্চ) দিয়ে —
     * এই পর্দার উপরের ব্রাঞ্চ-পিকারে (Master-এর জন্য) যা **বাছা আছে**
     * (`selectedBranch`) সেটা কখনো দেখতই না। এই একই ফাইলের বাকি প্রায়
     * প্রতিটা জায়গায় (৬৩৪, ৯১২, ২৪৬২, ২৪৯২, ৩০১৯ নং লাইন — প্রমাণিত,
     * বহু-জায়গায়-ব্যবহৃত নিয়ম) ঠিক এর উল্টো — `selectedBranch != "All"`
     * হলে সেটাই, নইলে `user.branch` — শুধু এই একটা ফাংশনেই (পরে যোগ
     * হয়েছিল বলে) সেই নিয়মটা মানা হয়নি। তাই Master যখন ব্রাঞ্চ-পিকারে
     * অন্য ব্রাঞ্চে সুইচ করেন, বোতামের সংখ্যা ও তালিকা দুটোই **লগইনের
     * নিজের ব্রাঞ্চ** দেখাত, স্ক্রিনে যা দেখানো হচ্ছে তা না।
     * ⛔ Staff-এর জন্য কিছু বদলায় না — তাঁদের `selectedBranch` সবসময়
     *    `user.branch`-ই থাকে (৩০৪ নং লাইন, ব্রাঞ্চ-পিকার তাঁরা দেখেনও
     *    না), তাই এই সংশোধন শুধু Master/একাধিক-ব্রাঞ্চ-দেখা অ্যাকাউন্টে
     *    প্রভাব ফেলে।
     */
    private fun loadKalAsarCount() {
        if (!this::user.isInitialized || user.branch.isBlank()) return
        val branch = if (selectedBranch != "All") selectedBranch else user.branch
        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(tomorrow.time)
        BackgroundWork.run {
            val rows = try {
                SupabaseClient.fetchListOrNull("payments", "payType=eq.chamber_expected&date=eq.$key", 200)
            } catch (_: Throwable) { null } ?: return@run
            val myId = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(branch).id
            val seen = HashSet<String>()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                if (row.optString("payType", "") != "chamber_expected") continue
                if (com.tkbiswas.pilesclinic.print.BranchCatalog.byName(row.optString("branch", "")).id != myId) continue
                val m = row.optString("mobile", "")
                seen.add(m.ifBlank { row.optString("id", "") })
            }
            val cnt = seen.size
            runOnUiThread { if (!isFinishing && !isDestroyed) setKalAsarCount(cnt.toString()) }
        }
    }
    private lateinit var user: NativeUser
    private var selectedDate: String = ""
    // TK-REQUESTED ADDITION (2026-07-16): Master can now pick a single
    // branch (defaults to "All", same as every other screen) instead of
    // always seeing all 5 branches mixed together with no way to narrow it
    // down. Staff never sees this spinner and always stays scoped to their
    // own branch, exactly as before -- unchanged.
    private var selectedBranch: String = "All"
    private val branches = BranchFilterStore.choices()   // 🟢🔒 V398: একটাই তালিকা

    // ─────────────────────────────────────────────────────────────────
    // 🔔 খাতার সারি B150 · B151 (TK, 30.07.2026): *"আমরা ভেতরে পেছনে দেখছি, তখন
    //    বাইরে রিসেপশনে অন্য কোন নতুন পেশেন্ট এসেছে আমাকে যেন সেকেন্ডের মধ্যে
    //    দেখায়।"*
    //
    // আগে এই বোর্ড **নিজে থেকে কখনো নতুন হত না** — নিচে টেনে রিফ্রেশ করলে বা
    // পর্দা আবার খুললে তবেই নতুন নাম আসত।
    //
    // ⚡ নিয়মটা এখন **এক জায়গায়** — `LiveRefresh` (চারটে পর্দাই ওটাই ব্যবহার করে,
    //    তাই ভবিষ্যতে একটা বদলালে বাকিগুলো পিছিয়ে থাকবে না)। ৩০ সেকেন্ডে শুধু
    //    একটা ছোট প্রশ্ন যায়; **সংখ্যা বদলালে তবেই** আসল তালিকা নামে।
    //
    // ⛔ যেসব অবস্থায় ছোঁয়াই হয় না (কোনো চালু কাজ যেন নষ্ট না হয়):
    //    · পর্দা সামনে না থাকলে (onPause-এ থেমে যায়)
    //    · **কোনো পপ-আপ/বাক্স খোলা থাকলে** — তখন পর্দা focus হারায়, তাই স্টাফের
    //      লেখা মাঝপথে হারানোর কোনো আশঙ্কা নেই
    //    · আজকের দিন ছাড়া অন্য তারিখে (পুরনো দিন কখনো বদলায় না)
    //    · রাত ১০টা – সকাল ৬টা (TK-এর নিয়ম: ওই সময়ে কেউ কাজ করেন না)
    //    · প্রশ্নের উত্তর না এলে (তখন কিছুই করা হয় না)
    // ⛔ রিফ্রেশটা `loadBoard(silent = true)` — পর্দার সেই একই পুরনো কাজ, কোনো
    //    চাকতি ঘোরে না, কোনো ডিজাইন বদলায় না।
    // ─────────────────────────────────────────────────────────────────
    private val autoHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScreenFocused = true
    private var autoBusy = false
    // 🔒 খাতার সারি B171 (TK-এর ৮ নম্বর সন্দেহ): এই বোর্ড আসলে চারটে টেবিল
    // থেকে তথ্য জোড়া দেয় (`ChamberAttendanceRepository.loadBoard` মিলিয়ে
    // দেখা হয়েছে) — আগে শুধু `payments` দেখা হত, তাই অন্য তিনটেতে বদল হলে
    // পর্দা জানতই না।
    private val autoWatch = LiveRefresh.Watch("payments", "patients", "enquiries", "followups")
    private val autoTick = object : Runnable {
        override fun run() {
            try { autoCheckForNewArrivals() } catch (_: Throwable) { }
            autoHandler.postDelayed(this, LiveRefresh.TICK_MS)
        }
    }

    // TK-REQUESTED CHANGE (2026-07-19): tapping Expected/Arrived/No-show used
    // to open a separate plain-text popup with just names. Now it filters
    // THIS SAME on-screen list instead (same pattern as Follow-up's All/
    // Today/Overdue tabs) -- tapping the same card again clears the filter
    // back to showing everyone.
    private var statFilter: String? = "arrived" // TK-APPROVED (2026-07-20): default view shows only Arrived; null = "All", else "expected" | "arrived" | "noshow"

    // Last board loaded, kept so the tappable stat cards and Close Chamber
    // can work off the same data already on screen without re-fetching.
    private var lastBoard: ChamberAttendanceBoard? = null
    // TK-REPORTED BUG FIX (2026-07-25, from TK's live report -- editing a
    // payment amount briefly showed correctly then silently reverted to
    // the old value): loadBoard() had NO protection against overlapping
    // calls -- if a board-fetch was already in flight (e.g. a routine
    // refresh) when a payment got edited (which also calls loadBoard() to
    // show the correction), and that OLDER fetch happened to finish AFTER
    // the edit's newer one, its stale result silently overwrote the just-
    // corrected screen with the pre-edit amount. Same exact token-guard
    // pattern already proven elsewhere in this file (see matchToken in the
    // name-autofill lookup below) -- only the MOST RECENT loadBoard() call
    // is ever allowed to update the screen.
    private var boardLoadToken = 0
    // TK-REQUESTED (2026-07-20): the "Review before Print" dialog, kept here
    // so an edit can dismiss + reopen it with freshly reloaded data.
    private var currentReviewDialog: android.app.Dialog? = null

    // TK APPROVED (2026-07-16): Close Chamber's "3-tap to force past a
    // warning" -- same standing pattern already used elsewhere in the app
    // (e.g. PaymentActivity's locked Bill field), reused here rather than
    // inventing a new interaction.
    private var closeTapCount = 0
    private var closeTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ULTIMATE CRASH-SAFETY FIX (TK-reported via video, 2026-07-16): the
        // whole screen-setup is now wrapped, same reasoning as
        // PatientTimelineActivity -- TK's video shows this screen crashing
        // almost instantly on almost any tap, too fast to be the network
        // data-load (which was already protected). Wrapping the entire
        // setup guarantees nothing here can crash the app anymore.
        try {
            binding = ActivityChamberAttendanceBinding.inflate(layoutInflater)
            setContentView(binding.root)
            UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
            BottomNav.wire(this)

            // TK-REQUESTED ADDITION (2026-07-19): same "Live Colour" gentle
            // shifting gradient as the Dashboard header — see
            // DashboardActivity.kt for the identical pattern this copies.
            // Only this top header banner animates; everything else on the
            // screen (stat boxes, buttons, cards) stays static. Uses its own
            // drawable pair defined here, not the shared bg_login_hero.xml,
            // so Login/other screens using that same drawable are untouched.
            run {
                fun gradient(orient: android.graphics.drawable.GradientDrawable.Orientation): android.graphics.drawable.GradientDrawable {
                    val navy = androidx.core.content.ContextCompat.getColor(this, com.tkbiswas.pilesclinic.R.color.brand_navy)
                    val blue = androidx.core.content.ContextCompat.getColor(this, com.tkbiswas.pilesclinic.R.color.brand_blue)
                    val green = androidx.core.content.ContextCompat.getColor(this, com.tkbiswas.pilesclinic.R.color.brand_green)
                    return android.graphics.drawable.GradientDrawable(orient, intArrayOf(navy, blue, green)).apply {
                        gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
                    }
                }
                val frameA = gradient(android.graphics.drawable.GradientDrawable.Orientation.TL_BR)
                val frameB = gradient(android.graphics.drawable.GradientDrawable.Orientation.BL_TR)
                val transition = android.graphics.drawable.TransitionDrawable(arrayOf(frameA, frameB))
                binding.chamberHero.background = transition
                var reversed = false
                val handler = android.os.Handler(mainLooper)
                val cycle = object : Runnable {
                    override fun run() {
                        if (isFinishing || isDestroyed) return
                        if (reversed) transition.reverseTransition(2600) else transition.startTransition(2600)
                        reversed = !reversed
                        handler.postDelayed(this, 2600)
                    }
                }
                handler.post(cycle)
            }

            val session = NativeSession.current(this)
            if (session == null) {
                startActivity(android.content.Intent(this, LoginActivity::class.java))
                finish(); return
            }
            user = session
            // 🟢🔒 V398 (16.08.2026, TK-অনুমোদিত): আগে জোর করে "All" বসত; এখন
            //   শেষবার বাছা ব্রাঞ্চটাই বসে (পুরো অ্যাপে একটাই জায়গা)।
            selectedBranch = if (user.role == "master") BranchFilterStore.get(this) else user.branch

            selectedDate = todayIso()
            // 🔒 TK-APPROVED (28.07.2026, ফটো-প্রুফে লক · খাতার সারি B36):
            // মাস্টারের ড্যাশবোর্ডে "চেম্বার বন্ধ হয়নি" সারিতে চাপ দিলে সোজা ওই
            // দিনের ও ওই ব্রাঞ্চের চেম্বার খোলে। ⛔ অন্য কোনোভাবে খুললে আগের
            // মতোই আজকের তারিখ ও নিজের ব্রাঞ্চ — কিছুই বদলায়নি।
            intent?.getStringExtra("openDate")?.takeIf { it.length == 10 }?.let { selectedDate = it }
            if (user.role == "master") {
                intent?.getStringExtra("openBranch")?.takeIf { it.isNotBlank() }?.let {
                    // 🟢🔒 V398: এখান থেকে যে ব্রাঞ্চে ঢোকা হচ্ছে সেটাই মনে রাখা ব্রাঞ্চ হবে।
                    selectedBranch = BranchFilterStore.set(this, it).ifBlank { it }
                }
            }
            // 🔒 খাতার সারি B46 (28.07.2026): "চেম্বার বন্ধ করুন" পর্দার **বন্ধ
            // করুন** বোতাম থেকে এলে, তালিকা তৈরি হওয়ার পরে আগের সেই একই বন্ধ
            // করার নিয়মটাই নিজে থেকে শুরু হয়। ⛔ অন্য কোনোভাবে খুললে কিছুই
            // নিজে থেকে হয় না — আগের মতোই।
            pendingAutoClose = intent?.getBooleanExtra("autoClose", false) == true

            // 🔴🔴🔒🔴 V481 (20.08.2026, TK-নির্দেশ — "Chamber closed-day workflow
            // ঝুঁকিহীনভাবে ঠিক করুন") — **আসল ফাঁক (যাচাই করা):** চেম্বার "Close"
            // হলে আগে শুধু উপরের ৩টা বোতাম (Registration/Mark Expected/Search)
            // বন্ধ হতো — কিন্তু নিচের তালিকার প্রতিটা রোগীর Cash/Online/
            // Treatment Progress/Arrived/Remark/Cancel Expected **সবই তখনো
            // সম্পূর্ণ খোলা** থাকত, বন্ধ হওয়ার পরেও বদলানো যেত। এখন এই একই
            // ৬টা কাজের প্রতিটাকে একটা কেন্দ্রীয় guard দিয়ে ঢাকা হলো।
            // ⛔ দিন খোলা থাকলে (readOnly=false) — এক অক্ষরও বদলায়নি, আগের
            //    মতোই সরাসরি কাজ করে। ⛔ Navigation-জাতীয় কাজ (Timeline খোলা,
            //    Call করা, Patient Chooser) — এগুলো ডেটা বদলায় না, তাই
            //    touched হয়নি, guard ছাড়াই আগের মতো কাজ করবে।
            fun guardedEdit(action: (ChamberAttendanceRow) -> Unit): (ChamberAttendanceRow) -> Unit = { row ->
                if (lastKnownReadOnly) {
                    android.widget.Toast.makeText(
                        this,
                        NoBengali.s("এই দিনের চেম্বার বন্ধ (Close) করা হয়ে গেছে — আর কোনো পরিবর্তন করা যাবে না।"),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    action(row)
                }
            }

            adapter = ChamberAttendanceAdapter(
                items = emptyList(),
                showQuickActions = isToday(),
                showCallAhead = isFuture(),
                onOpenTimeline = { row -> openTimeline(row) },
                onAddPayment = guardedEdit { row ->
                    // TK-REQUESTED (2026-07-24): the merged "Payment" box's
                    // tap target -- if this patient already has a payment
                    // today, review/edit/delete it (existing fixPaymentInReview,
                    // unchanged); otherwise open the same shared Payment
                    // window every other screen uses (existing openPayment/
                    // PaymentActivity, unchanged) to add a new Bill/Cash/
                    // Online entry. No new save logic -- just routes to the
                    // two already-proven flows based on whether anything's
                    // been paid yet.
                    val hasAny = (row.feesCash + row.feesOnline + row.paymentCash + row.paymentOnline) > 0.0
                    if (hasAny) fixPaymentInReview(row) else openPayment(row)
                },
                onAddRemark = guardedEdit { row -> showRemarkDialog(row) },
                onCall = { row -> callPatient(row) },
                // TK-APPROVED via photo-proof (2026-07-25): the Patient box
                // (Name/Mobile/ID) now opens Report Card directly on tap,
                // instead of the old 7-option clinical menu. Same target
                // ReportCardActivity already used elsewhere (see
                // showClinicalMenu below, kept unused/untouched in case it's
                // needed again -- nothing deleted, just this one wire moved).
                onClinical = { row -> showPatientChooser(row) },
                onCashTap = guardedEdit { row -> takeOrEditPayment(row, "CASH") },
                onOnlineTap = guardedEdit { row -> takeOrEditPayment(row, "ONLINE") },
                onTreatmentTap = guardedEdit { row -> writeTreatment(row) },
                onMarkArrived = guardedEdit { row -> markArrivedFromRow(row) },
                onCancelExpected = guardedEdit { row -> showCancelExpectedDialog(row) }
            )
            binding.recyclerBoard.layoutManager = LinearLayoutManager(this)
            // 🔴🎨🔒 B448 (TK-নির্দেশ, 05.08.2026) — উপরের ৫টা বক্স এখন
            // ConcatAdapter দিয়ে তালিকার **প্রথম আইটেম**, তাই স্ক্রল করলে
            // ওগুলোও তালিকার সাথেই উপরে সরে যায়। ⛔ `adapter`
            // (ChamberAttendanceAdapter, পেমেন্ট/ট্রিটমেন্ট-সহ জটিল অংশ)
            // অক্ষত/অপরিবর্তিত — শুধু পাশে জুড়ে দেওয়া হলো।
            binding.recyclerBoard.adapter = androidx.recyclerview.widget.ConcatAdapter(headerAdapter, columnHeaderAdapter, adapter)
            // 🔒🎨 B473 (05.08.2026, TK-নির্দেশ) — বক্স ও কলাম-হেডার (এখন
            // দুটোই RecyclerView-এর প্রথম দুই আইটেম) স্ক্রল করে সরে গেলে
            // উপরের পিন-করা ডুপ্লিকেট (`pinnedTableHeaderRow`) দেখা যায়,
            // স্ক্রিনের উপরে আটকে থাকে — শুধু বক্স হাইড হয়, হেডার থেকেই যায়
            // (TK-এর স্পষ্ট নির্দেশ)। ⛔ তালিকার নিজের স্ক্রল/পেজিং/রিফ্রেশ
            // কিছুই বদলায়নি — শুধু একটা দেখা-না-দেখার সিদ্ধান্ত।
            binding.recyclerBoard.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    updatePinnedHeaderVisibility()
                }
            })
            // 🔴🎨🔒 B448 — উপরের ৫টা বক্সের ক্লিক-লিসেনার ও প্রাথমিক অবস্থা
            // এখন এখানে বসে (আগে সরাসরি `binding.btnAddRegistration` ইত্যাদি
            // দিয়ে হতো) — headerAdapter নিজের View তৈরি করার সাথে সাথেই এই
            // কোড চলে (নিচে `bindViews`)।
            headerAdapter.bindViews { vh ->
                vh.btnAddRegistration.setOnClickListener {
                    startActivity(android.content.Intent(this, RegistrationActivity::class.java))
                }
                vh.btnMarkExpected.setOnClickListener { showMarkExpectedDialog() }
                vh.btnSearchPatient.setOnClickListener { showSearchDialog() }
                vh.cardExpected.setOnClickListener { toggleStatFilter("expected") }
                vh.cardArrived.setOnClickListener { toggleStatFilter("arrived") }
                // 🆕 (07.08.2026) — মাঝের বোতাম চাপলে কাল আসার কথা তালিকা খোলে।
                // 🟢🔒 V635 (২৪.০৮.২০২৬) — এখন এই স্ক্রিনে বাছা ব্রাঞ্চটাই
                // (selectedBranch != "All" হলে সেটা, নইলে লগইনের নিজের
                // ব্রাঞ্চ) সাথে করে পাঠানো হয় — নইলে ওই পর্দা লগইনের নিজের
                // ব্রাঞ্চেই ফিরে যেত, স্ক্রিনে যা দেখানো হচ্ছিল তা না।
                vh.cardKalAsar.setOnClickListener {
                    val br = if (selectedBranch != "All") selectedBranch else user.branch
                    startActivity(android.content.Intent(this, ExpectedTomorrowActivity::class.java)
                        .putExtra("branchOverride", br))
                }
                vh.btnAddRegistration.visibility = if (isToday()) View.VISIBLE else View.GONE
                vh.btnSearchPatient.visibility = if (isToday()) View.VISIBLE else View.GONE
                // পর্দা প্রথম খোলার সময় যদি loadBoard() ইতিমধ্যে সংখ্যা বসিয়ে
                // ফেলে থাকে (এই ব্লক দেরিতে চললে), সেই মান হারায় না।
                vh.tvExpectedCount.text = lastKnownExpectedCount
                vh.tvArrivedCount.text = lastKnownArrivedCount
                vh.tvKalAsarCount.text = lastKnownKalAsarCount
                // 🔴 B448 — readOnly (বোতাম enabled/alpha) অবস্থাও একইভাবে
                // পুনরায় বসানো হয়, applyDayState() View তৈরির আগে চললেও যেন
                // না হারায়।
                for (b in listOf(vh.btnAddRegistration, vh.btnMarkExpected, vh.btnSearchPatient)) {
                    b.isEnabled = !lastKnownReadOnly
                    b.alpha = if (lastKnownReadOnly) 0.45f else 1f
                }
            }
            // TK-REQUESTED (2026-07-27): pull the list down to refresh -- the same
            // gesture as Follow-up, on every section. It runs the screen's OWN
            // existing load, so no new query and no rule changes; the little circle
            // stops on its own when that load has had time to finish.
            binding.swipeRefresh.setColorSchemeColors(
            android.graphics.Color.parseColor("#0EA25F"),
            android.graphics.Color.parseColor("#1167D8")
        )
            binding.swipeRefresh.setOnRefreshListener {
            // 🆕 (07.08.2026) — হাতে-টানা refresh মানেই "সর্বশেষ অবস্থা দাও":
            // বন্ধ/খোলা-র চিহ্নটাও এবার ক্লাউড থেকে মিলিয়ে নেওয়া হয়।
            forceClosedCheck = true
            loadBoard(silent = true)
                    binding.swipeRefresh.postDelayed({
                try { binding.swipeRefresh.isRefreshing = false } catch (_: Throwable) {}
            }, 2500L)
        }

            binding.btnBack.setOnClickListener { finish() }
            binding.btnCalendar.setOnClickListener { pickDate() }
            binding.tvDateChip.setOnClickListener { pickDate() }
            binding.btnCloseChamber.setOnClickListener { attemptCloseChamber() }
            // 🔒 খাতার সারি B35: বিগত দিনের সেভ করা রেজিস্টার — শেয়ার ও প্রিন্ট।
            // ⛔ নতুন কোনো ছাপা তৈরি করা হয়নি — Close Chamber-এর সময় যে
            // রেজিস্টারটা তৈরি হয়, হুবহু সেটাই (finalizeAndShare), শুধু ওই
            // তারিখের হিসাব নিয়ে। তাই ছাপার চেহারা এক চুলও বদলায় না।
            val sharePast = View.OnClickListener {
                val b = lastBoard
                if (b == null || b.rows.none { r -> r.arrived }) {
                    android.widget.Toast.makeText(this, NoBengali.s("এই দিনে কেউ আসেননি — ছাপার কিছু নেই"), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this, "Loading…", android.widget.Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        val labels = try { withContext(Dispatchers.IO) { computeVisitLabels(b) } } catch (_: Throwable) { emptyMap() }
                        // 🆕 V805 — ওষুধ/স্যালাইনের মোট, ব্যাকগ্রাউন্ডেই (ব্যর্থ হলে শূন্য)
                        val st = try { withContext(Dispatchers.IO) {
                            ChamberAttendanceRepository.saleTotals(selectedDate, printBranchOverride.ifBlank {
                                if (selectedBranch != "All") selectedBranch else user.branch })
                        } } catch (_: Throwable) { ChamberRegisterPdfBuilder.SaleTotals() }
                        if (!isFinishing && !isDestroyed) finalizeAndShare(b, labels, st)
                    }
                }
            }
        /* 🎨🔒 V829 (২৯.০৮.২০২৬, TK-অনুমোদিত ফটো-প্রুফ: *"হ্যাঁ করুন, তবে সাবধানে"*)
           — অ্যাপের থিমে XML-এর সাদামাটা `<Button>` আপনা-আপনি **MaterialButton**
           হয়ে যায়, আর সেটা `android:background` **অগ্রাহ্য করে** নিজের গাঢ় নীল
           `backgroundTint` বসিয়ে দেয়। ফলে XML-এ লেখা রংটা ফোনে কখনো দেখা যেত না
           (কম্পিউটারে ঠিকই দেখা যেত)। `backgroundTintList = null` বসালে তবেই
           XML-এর drawable-টা দেখা যায় — প্রজেক্টের নিজেরই প্রমাণিত ওষুধ
           (`DoctorQueueAdapter` · `DraftCardAdapter`-এ আগে থেকেই চলছে, পাহারা ৯.৩২)।
           ⛔ শুধু চেহারা — বোতামের কাজ · জায়গা · লেখা কিচ্ছু বদলায়নি। */
        binding.btnSharePastPdf.backgroundTintList = null
        binding.btnPrintPast.backgroundTintList = null
            binding.btnSharePastPdf.setOnClickListener(sharePast)
            binding.btnPrintPast.setOnClickListener(sharePast)

            // TK-REQUESTED FIX (2026-07-24): third stat box ("Action"/
            // cardNoShow) removed from the layout entirely -- Expected and
            // Arrived now split the full row width evenly. showActionMenu()
            // itself is left defined (unused, harmless) in case this is
            // wanted from elsewhere later.

            if (user.role == "master") {
                // 🔒 TK-APPROVED (29.07.2026, খাতার সারি B84): ব্রাঞ্চ বাছার ঘর
                // এখন বাকি সব পর্দার হুবহু একই পিল, ক্যালেন্ডারের বাঁয়ে।
                // পুরনো Spinner লুকানো আছে, মোছা হয়নি — দিনের বোর্ড · তারিখ ·
                // Close Chamber-এর সব নিয়ম সেটাই চালায়, পিল কেবল বেছে দেয়।
                // ⛔ তাই ব্রাঞ্চ বদলালে আগের মতোই `dateClosedFlag` মুছে বোর্ড
                //    নতুন করে আসে — একটি নিয়মও বদলায়নি।
                binding.branchPicker.visibility = View.VISIBLE
                binding.spBranch.adapter = android.widget.ArrayAdapter(
                    this, com.tkbiswas.pilesclinic.R.layout.item_branch_spinner, branches
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                binding.spBranch.setSelection(branches.indexOf(selectedBranch).coerceAtLeast(0))
                binding.branchPicker.text = BranchFilterStore.pillText(this)
                binding.spBranch.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val picked = branches.getOrNull(position) ?: BranchFilterStore.ALL
                        if (picked != selectedBranch) {
                            selectedBranch = BranchFilterStore.set(this@ChamberAttendanceActivity, picked)   // 🟢 V398
                            dateClosedFlag = false; loadBoard()
                            loadKalAsarCount()   // 🟢🔒 V635 — ব্রাঞ্চ বদলালে বোতামের সংখ্যাও নতুন ব্রাঞ্চের
                        }
                        binding.branchPicker.text = BranchFilterStore.pillText(this@ChamberAttendanceActivity)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
                // Follow-up-এর হুবহু একই পপ-আপ — শিরোনাম "Branch" · গোল বোতাম ·
                // "Cancel"।
                binding.branchPicker.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setCustomTitle(PremiumAlert.header(this, "Branch"))
                        .setSingleChoiceItems(branches.toTypedArray(), BranchFilterStore.indexInChoices(this)) { dialog, which ->
                            binding.spBranch.setSelection(which)
                            // 🚨 খাতার সারি B126 (TK, 29.07.2026 সন্ধ্যা ৬.২৩): লুকানো
                            // (`gone`) Spinner কখনো layout হয় না, তাই তার
                            // `onItemSelected` চলত না — বাছাই হারিয়ে যেত। কাজটা এখন
                            // এখানেই সরাসরি হয় (Follow-up-এর পপ-আপের মতো)।
                            val picked = branches.getOrNull(which) ?: BranchFilterStore.ALL
                            if (picked != selectedBranch) {
                                selectedBranch = BranchFilterStore.set(this@ChamberAttendanceActivity, picked)   // 🟢 V398
                                dateClosedFlag = false; loadBoard()
                                loadKalAsarCount()   // 🟢🔒 V635 — ব্রাঞ্চ বদলালে বোতামের সংখ্যাও নতুন ব্রাঞ্চের
                            }
                            binding.branchPicker.text = BranchFilterStore.pillText(this@ChamberAttendanceActivity)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }
            } else {
                binding.branchPicker.visibility = View.GONE
            }

            updateDateLabel()
            loadBoard()
            // 🆕 (07.08.2026) — কাল আসার কথা বোতামের সংখ্যা একবার লোড।
            loadKalAsarCount()
        } catch (e: Throwable) {
            android.widget.Toast.makeText(this, "Could not open Chamber Attendance (${e.javaClass.simpleName}) — please try again", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Payment/Registration/Remark -- refresh so the
        // board reflects whatever was just saved in the real screens.
        // Silent: no spinner overlay, since the adapter now updates in
        // place (see loadBoard() below) instead of being recreated.
        if (::adapter.isInitialized) loadBoard(silent = true)
        // 🔔 খাতার সারি B150: পর্দা সামনে থাকলে ৩০ সেকেন্ডের ছোট প্রশ্ন চালু।
        autoHandler.removeCallbacks(autoTick)
        autoHandler.postDelayed(autoTick, LiveRefresh.TICK_MS)
    }

    override fun onPause() {
        super.onPause()
        // ⛔ পর্দা সামনে না থাকলে একটাও প্রশ্ন যাবে না।
        autoHandler.removeCallbacks(autoTick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 🔒 খাতার সারি B150: কোনো পপ-আপ/বাক্স খুললে পর্দা focus হারায় — তখন
        // রিফ্রেশ বন্ধ, তাই স্টাফের লেখা মাঝপথে হারাতে পারে না।
        autoScreenFocused = hasFocus
    }

    /**
     * 🔔 খাতার সারি B150 · B151 — সস্তা প্রশ্ন: "গতবারের পরে আজকের টাকার সারিতে
     * কিছু বদলেছে?" ⛔ একটাও সারি নামে না; বদলালে তবেই আসল তালিকা নামে।
     */
    private fun autoCheckForNewArrivals() {
        if (!::adapter.isInitialized) return
        if (!autoScreenFocused) return          // পপ-আপ খোলা
        if (autoBusy) return
        if (!isToday()) return                  // পুরনো/পরের দিন কখনো বদলায় না
        if (!LiveRefresh.awake()) return         // রাত ১০টা – সকাল ৬টা
        val date = selectedDate
        val br = selectedBranch
        autoBusy = true
        lifecycleScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) {
                    autoWatch.changed("chamber|$date|$br", br)
                }
                if (isFinishing || isDestroyed) return@launch
                if (!changed) return@launch
                // তালিকা/তারিখ/ব্রাঞ্চ এর মধ্যে বদলে গেলে বা পপ-আপ খুলে গেলে কিছু করা হয় না
                if (date == selectedDate && br == selectedBranch && autoScreenFocused) {
                    loadBoard(silent = true, fromAutoRefresh = true)
                }
            } catch (_: Throwable) {
            } finally {
                autoBusy = false
            }
        }
    }

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    /**
     * 🔒 TK-APPROVED (28.07.2026, ফটো-প্রুফে লক · খাতার সারি B35).
     *
     * TK-এর কথা: *"যখন সেভ ও ক্লোজ করবো তাহলে তো এখানে জিরো হয়ে যেতে হবে বা
     * ব্ল্যাঙ্ক হয়ে যেতে হবে।"* আর *"ক্যালেন্ডারে তারিখে চাপ দিলে সেই দিনে কতজন
     * পেশেন্ট এসেছিল, কত টাকা জমা করলো — সেই তালিকাটা শো করবে, এবং সেটাও যেন
     * আমরা শেয়ার পিডিএফ করতে পারি অথবা প্রিন্ট আউট করতে পারি।"*
     *
     * তাই পর্দার তিনটে অবস্থা, আর সিদ্ধান্তটা **একটাই জায়গা** থেকে হয়:
     *
     *  ১. **আজ, এখনো বন্ধ করা হয়নি** → একদম আগের মতোই, কিছুই বদলায় না।
     *  ২. **আজ, বন্ধ করার পরে** → তালিকা ফাঁকা · দুটো সংখ্যাই 0 · উপরের তিনটে
     *     বোতাম বন্ধ · একটা লাইনে দিনের ফল ও কোথায় গেলে তালিকা দেখা যাবে।
     *  ৩. **ক্যালেন্ডারে বিগত তারিখ** → ওই দিনের পুরো তালিকা, শুধু দেখার জন্য,
     *     নিচে **Share PDF** ও **Print**।
     *
     * ⛔ [viewingPast] সত্যি হয় শুধু তখনই যখন তারিখটা আজকের নয় — তাই আজকের
     * চালু চেম্বারের কাজ এক অক্ষরও বদলায় না।
     */
    private var closedToday = false

    /**
     * 🔒 খাতার সারি B46 (28.07.2026, TK ফটো-প্রুফে পাশ করেছেন):
     * ওই তারিখের চেম্বার সত্যিই বন্ধ কি না — ক্লাউডের উত্তর ধরে, কিন্তু
     * **পিছনের সুতোয়** (loadBoard-এ) দেখা হয়। মেইন থ্রেডে কখনো নেট নয়।
     */
    private var dateClosedFlag = false
    // 🆕🔴 (07.08.2026, Reopen-এর কাজের সাথে) — ব্যবহারকারী নিজে তালিকা নিচে টেনে
    // refresh করলে এটা `true` হয়; তখন ফোনের "বন্ধ" চিহ্ন থাকলেও একবার ক্লাউডে
    // যাচাই হয়, যাতে Master অনুমোদন দেওয়ার সাথে সাথেই দিনটা খুলে যায়
    // (নইলে লোকাল ক্যাশের কারণে ৩০ মিনিট পর্যন্ত "বন্ধ" দেখাত)।
    // ⛔ নিজে থেকে কখনো `true` হয় না — তাই বাড়তি কোনো ক্লাউড-খরচ নেই।
    private var forceClosedCheck = false

    /** "চেম্বার বন্ধ করুন" পর্দা থেকে এসেছে কি না (একবারই কাজ করে)। */
    private var pendingAutoClose = false

    private fun viewingPast(): Boolean = selectedDate < todayIso()

    /** এই তারিখের চেম্বার বন্ধ করা হয়েছে কি না (ফোনেই জমা থাকে, নেট লাগে না)। */
    private fun dateIsClosed(): Boolean = dateClosedFlag || try {
        val br = if (selectedBranch != "All") selectedBranch else user.branch
        ChamberCloseRepository.isClosedLocally(this, br, selectedDate)
    } catch (_: Throwable) { false }

    private fun applyDayState() {
        val past = viewingPast()
        val closed = closedToday || dateIsClosed()
        val readOnly = past || closed

        // উপরের তিনটে কাজের বোতাম — বন্ধ দিনে বা বিগত দিনে আর এন্ট্রি নয়।
        // 🔴 B448 — এই তিনটে বোতাম এখন headerAdapter-এর View-তে (ConcatAdapter-
        // এর প্রথম আইটেম), `binding.*`-এ আর নেই। View তৈরি না হয়ে থাকলেও
        // (bindViews তখনো চলেনি) মান হারায় না — lastKnownReadOnly মনে রাখা হয়,
        // bindViews-এ আবার বসে।
        lastKnownReadOnly = readOnly
        headerAdapter.currentHolder()?.let { vh ->
            for (b in listOf(vh.btnAddRegistration, vh.btnMarkExpected, vh.btnSearchPatient)) {
                b.isEnabled = !readOnly
                b.alpha = if (readOnly) 0.45f else 1f
            }
        }

        // বিগত দিন → তালিকা দেখাও + Share/Print।
        binding.closedActionRow.visibility = if (past) View.VISIBLE else View.GONE
        // 🔒 খাতার সারি B46 — TK-এর কথা: *"তাহলে তো সারা জীবন এইভাবেই ওপেন
        // থাকবে... ওখানে বন্ধ করার ব্যবস্থা করুন।"* তাই বিগত দিনের চেম্বার
        // **যতক্ষণ বন্ধ করা হয়নি ততক্ষণ** বোতামটা চালু থাকে। বন্ধ হয়ে গেলে
        // আগের মতোই শুধু-দেখার দিন (Share PDF · Print), আর কিছু বদলায় না।
        binding.btnCloseChamber.isEnabled = !closed
        binding.btnCloseChamber.alpha = if (closed) 0.5f else 1f
        // প্রুফ অনুযায়ী: বন্ধ হয়ে গেলে বোতামের নিচের লেখাটাও বদলায়, যাতে
        // স্টাফ এক নজরে বোঝেন দিনটা শেষ হয়ে গেছে।
        binding.btnCloseChamber.text =
            if (closed) "✅ Close Chamber (Save & Print Arrived)\nচেম্বার বন্ধ হয়ে গেছে"
            else if (past) "✅ Close Chamber (Save & Print Arrived)\nবিগত দিনের চেম্বার বন্ধ করুন — " + FollowUpModel.displayDate(selectedDate)
            else "✅ Close Chamber (Save & Print Arrived)\nচেম্বার বন্ধ করার আগে সেভ করুন"

        // 🆕 B419 (04.08.2026, TK-নির্দেশ) — বন্ধ হয়ে যাওয়া দিনে "Reopen-এর
        // অনুরোধ পাঠান" বোতাম। ⛔ এখনো-বন্ধ-না-হওয়া দিনে এর কোনো মানেই নেই — লুকানো।
        // 🔴🆕 TK-প্রশ্ন (07.08.2026, স্ক্রিনশটসহ — "চেম্বার বন্ধ করার পরেও যদি
        // কোনো পেশেন্ট আসে তাহলে এখানে কীভাবে Reopen করা যাবে?")। **আসল ফাঁক:**
        // শর্ত ছিল `past && closed` — অর্থাৎ বোতামটা **শুধু আগের দিনের** বন্ধ
        // চেম্বারে দেখা যেত; **আজকের দিন বন্ধ করে ফেললে কোনো উপায়ই থাকত না**,
        // অথচ ঠিক তখনই দরকার। **সমাধান:** `past` শর্তটা তুলে দেওয়া হলো — বন্ধ
        // হলেই (আজ হোক বা আগের দিন) বোতামটা দেখা যায়।
        // ⛔ অনুরোধ→অনুমোদনের পুরো ব্যবস্থা আগে থেকেই তৈরি ও প্রমাণিত
        //    (ChamberReopenPermission → Master-এর ঘন্টা → approveAndReopen) —
        //    এক অক্ষরও বদলানো হয়নি, শুধু বোতামটা এখন দেখা যায়।
        // ⛔ TK-এর সিদ্ধান্ত (07.08.2026): "স্টাফ অনুরোধ পাঠাবেন, মাস্টার অনুমোদন
        //    দেবেন" — কেউ নিজে থেকে খুলতে পারবে না, তাই হিসাব সুরক্ষিত থাকে।
        // (আগের কোড: if (past && closed) View.VISIBLE else View.GONE)
        binding.btnRequestReopen.visibility = if (closed) View.VISIBLE else View.GONE
        // 🔴🔒 V425 (TK-নির্দেশ ১৭.০৮.২০২৬) — *"আমি মাস্টার আবার আমাকে কেন
        //    অনুমতি নিতে হবে"*। **আসল ফাঁক:** বোতামটা সবাইকে একই রকম দেখাত,
        //    তাই Master-কেও নিজের কাছেই অনুরোধ পাঠাতে হত — অনুরোধটা তাঁরই
        //    ঘন্টায় গিয়ে বসত, তারপর তাঁকেই Approve করতে হত। অর্থহীন দুই ধাপ।
        //    **এখন:** Master হলে বোতামটা সরাসরি চেম্বার খুলে দেয়।
        // ⛔ স্টাফ/ডাক্তারের জন্য আগের নিয়ম **হুবহু অটুট** — অনুরোধ পাঠাবেন,
        //    Master অনুমোদন দিলে তবেই খুলবে (TK-এর সিদ্ধান্ত 07.08.2026)।
        val isMasterUser = user.role == "master"
        if (isMasterUser) binding.btnRequestReopen.text = NoBengali.s("🔓 চেম্বার আবার খুলুন")
        binding.btnRequestReopen.setOnClickListener {
            if (isMasterUser) confirmMasterReopen() else confirmRequestReopen()
        }

        when {
            past -> {
                binding.tvClosedNote.visibility = View.VISIBLE
                if (closed) {
                    binding.tvClosedNote.text = "🔒 Past day's saved record — view only"
                    binding.tvClosedNote.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_closed_note)
                    binding.tvClosedNote.setTextColor(android.graphics.Color.parseColor("#0F5C4A"))
                } else {
                    binding.tvClosedNote.text = "⚠️ Past day — this chamber is not closed yet"
                    binding.tvClosedNote.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_unclosed_note)
                    binding.tvClosedNote.setTextColor(android.graphics.Color.parseColor("#8E2A1E"))
                }
            }
            closed -> {
                // আজ, বন্ধ করার পরে — তালিকা ফাঁকা, সংখ্যা 0।
                val t = lastBoard?.totals
                val n = t?.arrivedCount ?: 0
                val money = (t?.feesCash ?: 0.0) + (t?.feesOnline ?: 0.0) +
                    (t?.paymentCash ?: 0.0) + (t?.paymentOnline ?: 0.0)
                binding.tvClosedNote.visibility = View.VISIBLE
                binding.tvClosedNote.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_closed_note)
                binding.tvClosedNote.setTextColor(android.graphics.Color.parseColor("#0F5C4A"))
                binding.tvClosedNote.text =
                    "✅ Today's chamber has been closed\n" +
                    "$n patients arrived · Collected ₹" + "%,.0f".format(money) + "\n" +
                    "Tap the calendar above to view today's list again"
                setExpectedArrivedCounts("0", "0")
                adapter.update(emptyList(), quickActions = false, callAhead = false)
                binding.tvEmpty.text = "List is empty"
                binding.tvEmpty.visibility = View.VISIBLE
            }
            else -> binding.tvClosedNote.visibility = View.GONE
        }
    }

    private fun isToday(): Boolean = selectedDate == todayIso()
    private fun isFuture(): Boolean = selectedDate > todayIso()

    private fun pickDate() {
        val cal = Calendar.getInstance()
        try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) { }
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            updateDateLabel()
            closeTapCount = 0
            // খাতার সারি B35: নতুন তারিখ বাছা হলো — আজকের "বন্ধ করা হয়েছে"
            // চিহ্নটা এই তারিখের জন্য প্রযোজ্য নয়, তাই মুছে ফেলা হয়।
            closedToday = false
            // খাতার সারি B46: নতুন তারিখের জন্য "বন্ধ কি না" আবার নতুন করে দেখা হবে।
            dateClosedFlag = false
            loadBoard()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateLabel() {
        binding.tvDateChip.text = "⏰ " + (if (isToday()) "Today · " else "") + FollowUpModel.displayDate(selectedDate)
        try {
            val cal = Calendar.getInstance()
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
            if (parsed != null) cal.time = parsed
            binding.tvCalMonth.text = SimpleDateFormat("MMM", Locale.ENGLISH).format(cal.time)
            binding.tvCalDay.text = cal.get(Calendar.DAY_OF_MONTH).toString()
        } catch (_: Exception) { }
        headerAdapter.currentHolder()?.let { vh ->
            vh.btnAddRegistration.visibility = if (isToday()) View.VISIBLE else View.GONE
            vh.btnSearchPatient.visibility = if (isToday()) View.VISIBLE else View.GONE
        }
        updateCloseChamberVisibility()
    }

    // 🔴🔒 V462 (20.08.2026): `silent` অনেক জায়গা থেকেই আসে (pull-to-refresh,
    // onResume, auto-refresh) — সেগুলোর মধ্যে **শুধু** ৩০-সেকেন্ডের auto-
    // refresh timer-এর জন্যই delta নিরাপদ (বাকিগুলোয় ব্যবহারকারী স্পষ্ট
    // "এখনই পূর্ণ/সঠিক তথ্য দাও" চাইছেন)। তাই `silent` পুনর্ব্যবহার না করে
    // আলাদা প্যারামিটার — ডিফল্ট `false`, তাই বাকি সব কল-সাইট অপরিবর্তিত।
    private fun loadBoard(silent: Boolean = false, onRendered: (() -> Unit)? = null, fromAutoRefresh: Boolean = false) {
        // 🟢🔒 V398: ব্রাঞ্চ না-বাছা থাকলে কোনো অনুরোধ যাবে না — শুধু বার্তা।
        if (BranchFilterStore.notChosen(this, user)) {
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.text = BranchFilterStore.ASK_TEXT
            binding.tvEmpty.visibility = View.VISIBLE
            onRendered?.invoke()
            return
        }
        val date = selectedDate
        // TK-REQUESTED ADDITION (2026-07-20): same "show what was already on
        // the phone instantly" pattern added to Doctor Queue and Follow-up.
        var hadCache = false
        if (!silent) {
            val cached = ChamberAttendanceRepository.loadCachedBoard(this, date, selectedBranch)
            if (cached != null && (cached.rows.isNotEmpty() || cached.totals.expectedCount > 0)) {
                hadCache = true
                binding.progressLoad.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
                lastBoard = cached
                val t = cached.totals
                setExpectedArrivedCounts(t.expectedCount.toString(), t.arrivedCount.toString())
                fun money(v: Double) = "\u20B9" + "%,.0f".format(v)
                binding.tvTotals.text = "Fees: Cash ${money(t.feesCash)} · Online ${money(t.feesOnline)}    " +
                    "Payment: Cash ${money(t.paymentCash)} · Online ${money(t.paymentOnline)}"
                val allZero = t.feesCash == 0.0 && t.feesOnline == 0.0 && t.paymentCash == 0.0 && t.paymentOnline == 0.0
                binding.collectionBox.visibility = View.GONE // TK-REQUESTED (2026-07-20): collection moved to the printed register (Review after Close Chamber); box removed from above Save.
                adapter.update(filteredRows(cached.rows), quickActions = isToday(), callAhead = isFuture())
                updateStatCardHighlight()
                updateTableHeaderVisibility()
                applyDayState()   // খাতার সারি B35
            } else {
                binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
                // TK-REQUESTED (2026-07-24): only reachable on first-ever
                // open of this date/branch (no cache yet) -- plain
                // "Loading..." instead of blank while the first fetch runs.
                binding.tvEmpty.text = "Loading..."
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
        val myBoardToken = ++boardLoadToken
        lifecycleScope.launch {
            // CRASH-SAFETY FIX (TK-reported, 2026-07-16): this had no error
            // handling at all -- any problem while reading/combining
            // Registration/Payment/Follow-up data for the board used to
            // crash the WHOLE APP (which Android then relaunches at
            // Login/Dashboard -- exactly what looked like "wherever I tap in
            // Chamber Attendance it goes back to Home"). Same safety-net
            // pattern already used in PatientTimelineActivity's "View All".
            try {
                val board = withContext(Dispatchers.IO) {
                    // 🔴🔒 V462 (20.08.2026, TK-অনুমোদিত পাইলট): শুধু auto-
                    // refresh timer পথে delta — pull-to-refresh/onResume/
                    // প্রথম খোলা সবসময়ই আগের নিরাপদ পূর্ণ loadBoard()।
                    if (fromAutoRefresh) ChamberAttendanceRepository.fetchBoardDelta(date, selectedBranch, this@ChamberAttendanceActivity)
                    else ChamberAttendanceRepository.loadBoard(date, selectedBranch, this@ChamberAttendanceActivity)
                }
                if (date != selectedDate) return@launch // a newer date was picked while this was loading
                // TK-REPORTED BUG FIX (2026-07-25): a newer loadBoard() call
                // (e.g. right after a payment edit) may have already
                // started -- if so, THIS is now the stale one; drop it
                // instead of overwriting the screen with old data.
                if (myBoardToken != boardLoadToken) return@launch
                binding.progressLoad.visibility = View.GONE
                lastBoard = board

                val t = board.totals
                setExpectedArrivedCounts(t.expectedCount.toString(), t.arrivedCount.toString())
                fun money(v: Double) = "\u20B9" + "%,.0f".format(v)
                binding.tvTotals.text = "Fees: Cash ${money(t.feesCash)} · Online ${money(t.feesOnline)}    " +
                    "Payment: Cash ${money(t.paymentCash)} · Online ${money(t.paymentOnline)}"
                val allZero = t.feesCash == 0.0 && t.feesOnline == 0.0 && t.paymentCash == 0.0 && t.paymentOnline == 0.0
                binding.collectionBox.visibility = View.GONE // TK-REQUESTED (2026-07-20): collection moved to the printed register (Review after Close Chamber); box removed from above Save.

                adapter.update(filteredRows(board.rows), quickActions = isToday(), callAhead = isFuture())
                updateStatCardHighlight()
                applyDayState()   // খাতার সারি B35
                updateTableHeaderVisibility()

                // 🔒 V236 (TK, 01.08.2026 — সমস্যা-১): চেম্বার বন্ধ করার পরে (B35)
                // applyDayState() উপরে ইতিমধ্যে সঠিক সবুজ summary ("N জন এসেছিলেন ·
                // জমা ₹X · উপরের ক্যালেন্ডারে চাপলে আজকের তালিকা আবার দেখতে পাবেন")
                // ও tvEmpty="তালিকা ফাঁকা" বসিয়ে দেয়। আগে ঠিক নিচের ব্লকটা তার
                // পরে চলে ওই লেখা মুছে statFilter-এর "Nobody has arrived yet."
                // বসিয়ে দিত — Close Chamber → Print থেকে ফিরে "Arrived 0 / Nobody
                // arrived" দেখাত, যদিও রোগীরা এসেছিলেন (তথ্য নিরাপদ)। এখন বন্ধ-
                // করা আজকের দিনে ওই override আর চলবে না, তাই বিভ্রান্তিকর লেখাটা যায়।
                // ⛔ B35-এর 0/ফাঁকা নিয়ম এক অক্ষরও বদলায়নি — শুধু ভুল বার্তাটা গেল।
                val dayClosedBlank = (closedToday || dateIsClosed()) && !viewingPast()
                if (!dayClosedBlank) {
                    if (adapter.itemCount == 0) {
                        binding.progressLoad.visibility = View.GONE
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = when (statFilter) {
                            "expected" -> "Nobody is expected today."
                            "arrived" -> "Nobody has arrived yet."
                            "noshow" -> "No no-shows — everyone expected has arrived."
                            else -> "No entries for this date yet."
                        }
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                    }
                }
                onRendered?.invoke()

                // 🔒 খাতার সারি B46 — এই তারিখটা সত্যিই বন্ধ কি না সেটা
                // ক্লাউডে দেখা হয় **পিছনের সুতোয়** (মেইন থ্রেডে কখনো নেট নয়,
                // সারি B27-এর শিক্ষা), তারপর পর্দার অবস্থা ঠিক করা হয়।
                // 🔴 B412 (04.08.2026, TK-রিপোর্ট — "স্টাফ চেম্বার বন্ধ করে বাড়ি
                // চলে গেছে, তার ফোনে বন্ধ দেখাচ্ছে, কিন্তু Master-এর ফোনে এখনো
                // খোলা দেখাচ্ছে"): আসল কারণ — নিচের ক্লাউড-যাচাইটা আগে **শুধু
                // বিগত দিনের জন্য** (`!viewingPast()` হলে সরাসরি `return@run`)
                // চলত। আজকের তারিখে (সবচেয়ে সাধারণ ব্যবহার) কখনোই ক্লাউডে
                // জিজ্ঞাসা করা হতো না — যে ফোন নিজে বন্ধ করেনি (যেমন Master),
                // তার কাছে খবরটা পৌঁছানোর কোনো পথই ছিল না, তাই চিরকাল "খোলা"
                // দেখাত। **সমাধান:** আজ/বিগত দুটোতেই — যদি এই ফোন এখনো
                // (লোকাল ক্যাশ অনুযায়ী) "খোলা" জানে, তবেই একবার ক্লাউডে
                // যাচাই হয়; ফোন যদি আগে থেকেই "বন্ধ" জানে (নিজে বন্ধ করেছে
                // বা আগে একবার ক্লাউড-যাচাইয়ে জেনে গেছে), তাহলে আর নতুন
                // কোনো কল হয় না — Supabase-এর খরচ বাড়েনি, শুধু "এখনো খোলা"
                // দেখানো অবস্থাতেই যাচাই হয়।
                // 🔴🆕 TK-নির্দেশ (07.08.2026, Reopen-এর কাজের সাথে) — **উল্টো
                // দিকটাও দরকার:** Master অনুমোদন দিয়ে দিনটা আবার খুলে দিলে
                // স্টাফের ফোনে সেটা সাথে সাথে জানার কোনো পথ ছিল না — উপরের
                // নিয়ম অনুযায়ী ফোন "বন্ধ" জানলে আর ক্লাউডে জিজ্ঞাসাই করত না,
                // আর লোকাল "বন্ধ" চিহ্নটা ৩০ মিনিট (CACHE_TTL_MS) জমা থাকে।
                // ফলে অনুমোদনের পরেও স্টাফকে আধ ঘণ্টা অপেক্ষা করতে হতো —
                // অথচ রোগী তখন সামনে দাঁড়িয়ে। **সমাধান:** ব্যবহারকারী নিজে
                // তালিকা নিচে টেনে refresh করলে (`forceClosedCheck`) এই একটাবার
                // ক্লাউডে যাচাই হয়, বন্ধ-জানা অবস্থাতেও। ⛔ খরচ বাড়ে না —
                // শুধু হাতে-টানা refresh-এই, নিজে থেকে কখনো নয়।
                run {
                    val forced = forceClosedCheck
                    forceClosedCheck = false
                    if (!pendingAutoClose && !forced && dateIsClosed()) return@run
                    val br = if (selectedBranch != "All") selectedBranch else user.branch
                    val wasClosed = dateIsClosed()
                    val realClosed = withContext(Dispatchers.IO) {
                        try {
                            if (forced) {
                                // লোকাল ক্যাশ উপেক্ষা করে সরাসরি ক্লাউডে; নেট ব্যর্থ
                                // হলে (null) আগের অবস্থাই ধরে রাখা হয় — ভুল করে
                                // "খোলা" দেখিয়ে দেওয়া হয় না।
                                ChamberCloseRepository.isClosedFromCloud(this@ChamberAttendanceActivity, br, date) ?: wasClosed
                            } else {
                                ChamberCloseRepository.isClosed(this@ChamberAttendanceActivity, br, date)
                            }
                        } catch (_: Throwable) { false }
                    }
                    if (isFinishing || isDestroyed) return@launch
                    if (date != selectedDate) return@launch
                    // 🔵 (07.08.2026, নিজের যাচাইয়ে ধরা) — শুধু `dateClosedFlag`
                    // বদলালে পর্দা আঁকা হত। কিন্তু "বন্ধ" অবস্থাটা অনেক সময়
                    // **ফোনের লোকাল চিহ্ন** থেকে আসে (dateClosedFlag তখনো false)
                    // — তাই Master খুলে দেওয়ার পরে refresh করলেও শর্তটা মিলত না
                    // ও পর্দা "বন্ধ"-ই দেখাত। এখন হাতে-টানা refresh (forced)
                    // হলে অবস্থা বদলালেই পর্দা নতুন করে আঁকা হয়।
                    if (forced && realClosed != wasClosed) {
                        dateClosedFlag = realClosed
                        // 🔴🔵 (07.08.2026, নিজের অডিটে ধরা — এটা না থাকলে পুরো
                        // Reopen-এর কাজটাই বৃথা যেত): এই পর্দায় "বন্ধ" হিসাব হয়
                        // `closedToday || dateIsClosed()` দিয়ে। `closedToday`
                        // এই ফোনেই চেম্বার বন্ধ করার মুহূর্তে `true` হয় ও শুধু
                        // ক্যালেন্ডার থেকে অন্য তারিখ বাছলে (`pickDate`) রিসেট
                        // হত। ফলে **যে ফোন বন্ধ করেছিল সেখানেই** Master অনুমোদন
                        // দেওয়ার পরে refresh করলেও পর্দা বন্ধই থাকত — অথচ ঠিক
                        // সেই ফোনেই কাজটা দরকার। এখন ক্লাউড "খোলা" বললে এটাও
                        // সাথে সাথে রিসেট হয়। ⛔ শুধু forced (হাতে-টানা refresh)
                        // পথেই — নিজে থেকে কখনো নয়, তাই ভুল করে খুলে যাওয়ার ঝুঁকি নেই।
                        if (!realClosed) closedToday = false
                        applyDayState()
                    }
                    if (realClosed != dateClosedFlag) {
                        dateClosedFlag = realClosed
                        applyDayState()
                    }
                    // "চেম্বার বন্ধ করুন" পর্দার **বন্ধ করুন** বোতাম থেকে এলে
                    // আগের সেই একই নিয়মটাই নিজে থেকে চালু হয় (রিভিউ · ৩-ট্যাপ ·
                    // Confirm & Print)। ⛔ নতুন কোনো বন্ধ করার পথ তৈরি হয়নি।
                    if (pendingAutoClose && !realClosed) {
                        pendingAutoClose = false
                        attemptCloseChamber()
                    } else if (realClosed) {
                        pendingAutoClose = false
                    }
                }
            } catch (e: Exception) {
                if (silent) return@launch // background refresh: keep showing whatever is already on screen
                binding.progressLoad.visibility = View.GONE
                // TK-REQUESTED (2026-07-20): same as Doctor Queue/Follow-up -- a
                // fetch failure after cached data was already shown should NOT
                // wipe it with an error message.
                if (!hadCache) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Could not load this date's board — check connection and try again"
                    android.widget.Toast.makeText(
                        this@ChamberAttendanceActivity,
                        "Could not load — check connection and try again",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                onRendered?.invoke()
            }
        }
    }

    // TK-REQUESTED (2026-07-25): Patient cell in the Close Chamber review
    // -> small chooser between the two things TK actually wants to check
    // (Full Journey / Report Card), instead of guessing which one to open
    // directly. 3-tap-edit inside either screen is already unchanged.
    private fun showFullJourneyOrReportCardChooser(row: ChamberAttendanceRow) {
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, row.name.ifBlank { row.mobile }))
            .setItems(arrayOf("🧭 Full Journey", "📋 Report Card")) { _, which ->
                if (which == 0) openTimeline(row) else openReportCard(row)
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun openTimeline(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        /* 🔵🔒 V526: এক নম্বরে দুজন আলাদা রোগী থাকলে বোর্ডে এখন দুটো সারি —
           তাই History-ও ঠিক **এই** সারির রোগীরই খুলবে।
           ⛔ আইডি ফাঁকা হলে হুবহু আগের আচরণ। */
        startActivity(
            android.content.Intent(this, PatientTimelineActivity::class.java)
                .putExtra("mobile", digits)
                .putExtra("patientRowId", row.patientRowId)
        )
    }

    // TK-APPROVED via photo-proof (2026-07-25): Patient box tap -> Report
    // Card directly, same target/extra ReportCardActivity already expects.
    /** TK-LOCKED (2026-07-25): tapping the Patient box (Name / Mobile / ID)
     *  first asks WHICH view is wanted . Patient Details (the full Timeline)
     *  or Report Card . instead of jumping straight into one of them. Both
     *  targets are the same screens already used everywhere else. */
    private fun showPatientChooser(row: ChamberAttendanceRow) {
        val who = row.name.ifBlank { row.mobile }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, who))
            .setItems(arrayOf("👤  Patient Details", "📋  Report Card")) { _, which ->
                if (which == 0) openTimeline(row) else openReportCard(row)
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun openReportCard(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        /* 🔵🔒 V522 (২২.০৮.২০২৬, TK-নির্দেশ): এক নম্বরে দুজন আলাদা রোগী থাকলে
           Report Card যেন **এই** রোগীরই খোলে ও ছাপে। বোর্ডের সারিতে রোগীর
           **Official Patient ID** থাকে (রোগী-প্রতি অনন্য), তাই সেটাই পাঠানো হয়।
           ⛔ আইডি ফাঁকা বা না মিললে হুবহু আগের আচরণ। */
        startActivity(android.content.Intent(this, ReportCardActivity::class.java)
            .putExtra("mobile", digits)
            .putExtra("patientCode", row.patientId)
            .putExtra("patientRowId", row.patientRowId))   // 🔵 V526
    }

    /** Opens the SAME PaymentActivity every other screen uses, pre-filled
     *  with this patient's mobile -- this is the entire "Add Payment"
     *  action; nothing about saving a payment is duplicated here. */
    private fun openPayment(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        /* 🔵🔒 V520 (২২.০৮.২০২৬): এক নম্বরে দুজন আলাদা রোগী থাকলে **এই কার্ডটা
           কার** সেটা সাথে পাঠানো হয়, তাই Payment ঠিক এই রোগীরই ফর্ম খোলে।
           ⛔ ফাঁকা থাকলে আচরণ হুবহু আগের মতোই। */
        startActivity(
            android.content.Intent(this, PaymentActivity::class.java)
                .putExtra("mobile", digits)
                .putExtra("patientCode", row.patientId)
        )
    }

    /** Call-ahead: dials the same number, no data written here at all. */
    private fun callPatient(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        try {
            // TK-REQUESTED (2026-07-24): "everywhere calling is possible in
            // the project" -- now uses the shared CallChooser.kt (Phone/
            // Superfone/etc. picker, Truecaller excluded) instead of
            // opening the OS default dialer directly.
            CallChooser.open(this, "+91$digits")
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No phone app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** TK-REQUESTED ADDITION (2026-07-19): Doctor Checkup / Prescription /
     *  Medicine Slip / Blood Test / Diet Chart, reachable directly from this
     *  board -- a patient who showed up in chamber often needs one of these
     *  the same visit. Same RoleSession hand-off + menu pattern already used
     *  from PatientTimelineActivity's Take Action; same real screens, no new
     *  clinical-save logic introduced here. */
    private fun showClinicalMenu(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        // 🔒 TK-এর নিয়ম (28.07.2026): চাপ দেওয়ামাত্র স্টাফ বুঝবেন কাজ শুরু হয়েছে।
        android.widget.Toast.makeText(this, "Opening…", android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            // 🔒🔒 খাতার সারি B179 (TK, 30.07.2026 — TK-এর স্পষ্ট অনুমতি: "জায়গাতেও
            // ঠিক করতে চাই")। patientId-এর সঙ্গেই একই ছোট অনুরোধে address/age/sex
            // ঘরগুলোও আনা হচ্ছে — বাড়তি কোনো দ্বিতীয় ক্লাউড-কল লাগেনি, একটাই
            // অনুরোধে সবকটা ঘর একসাথে (select কলাম বাড়ানো হয়েছে মাত্র)।
            val patientRow = withContext(Dispatchers.IO) {
                try {
                    /* 🔵🔒 V531 (২২.০৮.২০২৬, TK-নির্দেশ) — **ঠিক এই কার্ডের রোগীটিই।**
                       বোর্ডের সারিটা V526 থেকেই জানে সে কোন রোগীর
                       (`ChamberAttendanceRow.patientRowId`)। আগে সেটা এখানে
                       ব্যবহার হত না — শুধু মোবাইল ধরে **প্রথম সারি** নেওয়া হত,
                       তাই এক নম্বরে দু'জন থাকলে অন্যজনের ঠিকানা/বয়স/লিঙ্গ
                       ক্লিনিক্যাল পর্দায় বসে যেতে পারত।
                       ⛔ `patientRowId` ফাঁকা (পুরোনো জমানো সারি) বা সার্ভারে
                          না মিললে — **হুবহু আগের সেই মোবাইল-পথ**। */
                    val wanted = row.patientRowId.trim()
                    var rows = if (wanted.isNotBlank())
                        SupabaseClient.fetchList("patients", "id=eq.$wanted", 1, select = "id,address,age,sex")
                    else SupabaseClient.findByMobile("patients", "+91$digits", "id,address,age,sex")
                    if (wanted.isNotBlank() && rows.length() == 0)
                        rows = SupabaseClient.findByMobile("patients", "+91$digits", "id,address,age,sex")
                    if (rows.length() > 0) rows.getJSONObject(0) else null
                } catch (_: Throwable) { null }
            }
            val patientId = patientRow?.optString("id", "") ?: ""
            if (patientId.isBlank()) {
                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "No Registration found for this mobile yet — register first.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val roleStr = if (user.role.equals("doctor", true)) "DOCTOR" else "STAFF"
            com.tkbiswas.pilesclinic.clinical.RoleSession.applyFrom(
                roleStr,
                row.name,
                patientId,
                row.branch,
                digits,
                patientRow?.s("address") ?: "",
                patientRow?.s("age") ?: "",
                patientRow?.s("sex") ?: "",
                row.disease,
                // 🔒 খাতার সারি B175: `row.patientId` (মানুষ-পড়া-যায় কোড)
                // এমনিতেই এই বোর্ডে লোড হয়ে আছে (A4 রেজিস্টার ছাপাতেও ব্যবহার
                // হয়), শুধু এখানে পাঠানো হচ্ছিল না।
                patientDisplayId = row.patientId
            )
            val options = arrayOf("🩺 Doctor Checkup", "📝 Prescription", "💊 Medicine Slip", "🩸 Blood Test", "🥗 Diet Chart", "📋 সম্পূর্ণ ইতিহাস (Full History)", "🗂️ Report Card")
            val targets = arrayOf(
                com.tkbiswas.pilesclinic.clinical.DoctorCheckupActivity::class.java,
                com.tkbiswas.pilesclinic.clinical.PrescriptionActivity::class.java,
                com.tkbiswas.pilesclinic.clinical.MedicineSlipActivity::class.java,
                com.tkbiswas.pilesclinic.clinical.InvestigationAdviceActivity::class.java,
                com.tkbiswas.pilesclinic.clinical.DietChartActivity::class.java
            )
            AlertDialog.Builder(this@ChamberAttendanceActivity)
                .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, "Clinical Document — ${row.name.ifBlank { row.mobile }}"))
                .setItems(options) { _, which ->
                    when (which) {
                        targets.size -> openTimeline(row)
                        targets.size + 1 -> startActivity(android.content.Intent(this@ChamberAttendanceActivity, ReportCardActivity::class.java)
                            .putExtra("mobile", digits)
                            .putExtra("patientCode", row.patientId)
                            .putExtra("patientRowId", row.patientRowId))   // 🔵 V522/V526
                        else -> startActivity(android.content.Intent(this@ChamberAttendanceActivity, targets[which]))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    /** TK-REQUESTED CHANGE (2026-07-19): tapping a stat card now filters the
     *  on-screen list directly instead of opening a separate popup (same
     *  pattern as Follow-up's All/Today/Overdue tabs). Tapping the same
     *  card again clears back to "All". */
    private fun toggleStatFilter(key: String) {
        // TK-REQUESTED (2026-07-22): "আসার কথা" (Expected) must stay blank no
        // matter how many times it is tapped -- it must NOT toggle back to the
        // "All" view and re-show arrived patients. Only Expected loses the
        // toggle-off; Arrived / No-show keep their tap-again-clears behaviour.
        statFilter = if (statFilter == key && key != "expected") null else key
        val board = lastBoard ?: return
        adapter.update(filteredRows(board.rows), quickActions = isToday(), callAhead = isFuture())
        updateStatCardHighlight()
        updateTableHeaderVisibility()
        updateCloseChamberVisibility()
        if (adapter.itemCount == 0) {
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = when (statFilter) {
                "expected" -> "Nobody is expected today."
                "arrived" -> "Nobody has arrived yet."
                "noshow" -> "No no-shows — everyone expected has arrived."
                else -> "No entries for this date yet."
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun filteredRows(rows: List<ChamberAttendanceRow>): List<ChamberAttendanceRow> = when (statFilter) {
        "expected" -> rows.filter { it.expected && !it.arrived }
        "arrived" -> rows.filter { it.arrived }
        "noshow" -> rows.filter { it.expected && !it.arrived }
        else -> rows
    }

    /** Dims whichever stat card is NOT the active filter, so it's obvious
     *  which one is currently narrowing the list (same idea as Follow-up's
     *  highlighted selected tab). Full brightness for all three when no
     *  filter is active. */
    private fun updateStatCardHighlight() {
        // TK-REQUESTED FIX (2026-07-24): "noshow"/cardNoShow removed --
        // that box no longer exists in the layout (was repurposed as
        // "Action" and has now been removed entirely).
        headerAdapter.currentHolder()?.let { vh ->
            val cards = mapOf("expected" to vh.cardExpected, "arrived" to vh.cardArrived)
            cards.forEach { (key, view) -> view.alpha = if (statFilter == null || statFilter == key) 1f else 0.4f }
        }
    }

    // TK-REPORTED (2026-07-24): viewing ONLY "Expected" (আসার কথা) patients
    // showed a "PAYMENT" column title above rows that never have a payment
    // yet (a patient who hasn't arrived hasn't paid). PATIENT and TREATMENT
    // PROGRESS stay visible either way (TK-approved mockup) -- only the
    // PAYMENT header cell hides for Expected; unchanged (still shows) for
    // Arrived / All, where it's actually in use.
    private fun updateTableHeaderVisibility() {
        // TK-LOCKED (2026-07-25): three money columns again . hide/show all three together.
        val moneyHeaderVis = if (statFilter == "expected") View.GONE else View.VISIBLE
        // 🔒🎨 B473 (05.08.2026) — এখন দুই জায়গায় বসাতে হয়: RecyclerView-এর
        // ভিতরের স্বাভাবিক হেডার আইটেম (columnHeaderAdapter) আর উপরে
        // পিন-করা ডুপ্লিকেট (pinnedTableHeaderRow) — দুটো সবসময় একই
        // দেখাবে, তাই কোনটা এই মুহূর্তে চোখে দেখা যাচ্ছে তার উপর নির্ভর
        // করে না।
        columnHeaderAdapter.setMoneyColumnsVisibility(moneyHeaderVis)
        binding.pinnedTableHeaderRow.tvHeaderPayment.visibility = moneyHeaderVis
        binding.pinnedTableHeaderRow.tvHeaderCash.visibility = moneyHeaderVis
        binding.pinnedTableHeaderRow.tvHeaderOnline.visibility = moneyHeaderVis
    }

    /** 🔒🎨 B473 (05.08.2026, TK-নির্দেশ — "শুধু বক্স হাইড হবে, হেডার
     *  আটকে থাকবে")। বক্স (আইটেম ০) ও আসল কলাম-হেডার (আইটেম ১) —
     *  RecyclerView-এর প্রথম দুই আইটেম — দুটোই স্ক্রল করে পুরোপুরি সরে
     *  গেলে তবেই উপরের পিন-করা ডুপ্লিকেট দেখা যায়; নইলে লুকানো থাকে
     *  (নইলে আসল হেডার আর ডুপ্লিকেট — দুটোই একসাথে দেখাত)। */
    private fun updatePinnedHeaderVisibility() {
        try {
            val lm = binding.recyclerBoard.layoutManager as? LinearLayoutManager ?: return
            val firstVisible = lm.findFirstVisibleItemPosition()
            val showPinned = when {
                firstVisible >= 2 -> true
                firstVisible == 1 -> {
                    val headerView = lm.findViewByPosition(1)
                    headerView == null || headerView.top <= 0
                }
                else -> false
            }
            binding.pinnedTableHeaderRow.root.visibility = if (showPinned) View.VISIBLE else View.GONE
        } catch (_: Throwable) { }
    }

    /** TK-REQUESTED (2026-07-22): the bottom "Close Chamber (Save & Print
     *  Arrived)" button is only meaningful for the Arrived list. It must NOT
     *  show while the "আসার কথা" (Expected) filter is active -- there is
     *  nothing to save/close from the Expected view.
     *
     *  🔒 খাতার সারি B46 (28.07.2026, TK ফটো-প্রুফে পাশ): আজকের দিনের পাশাপাশি
     *  **বিগত দিনেও** বোতামটা দেখাবে, নইলে ভুলে যাওয়া দিনগুলো সারা জীবন খোলা
     *  থেকে যেত। বন্ধ হয়ে গেলে applyDayState() ওটাকে নিষ্ক্রিয় করে দেয়। */
    private fun updateCloseChamberVisibility() {
        val canClose = isToday() || viewingPast()
        binding.btnCloseChamber.visibility =
            if (canClose && statFilter != "expected") View.VISIBLE else View.GONE
    }

    /** TK APPROVED (2026-07-16): "Search & Add existing patient" -- Name /
     *  Mobile / Patient ID. Tapping "✅ Arrived" writes ONE zero-amount row
     *  via ChamberAttendanceRepository.markArrived (same "payments" table,
     *  same SupabaseClient.upsert every other payment save already uses) --
     *  nothing typed here, per TK's explicit instruction (treatment/remark
     *  comes later, separately, via the Treatment button on the board). */
    /** TK-REQUESTED ADDITION (2026-07-19): "Mark Expected (Future Date)" --
     *  works whether the mobile matches an existing Enquiry, an existing
     *  Patient, or has no record at all yet (pure walk-in mention, e.g.
     *  "he said he'll come back Tuesday"). Auto-fills name/branch if a
     *  match is found (best-effort, never blocks); otherwise the staff can
     *  type the name by hand. Saves via ChamberAttendanceRepository.
     *  markExpected(), the SAME local-first + retry-queue pattern as Mark
     *  Arrived -- never blocks on network. */
    private fun showMarkExpectedDialog() {
        val parts = premiumDialogShellChamber("⏰ Mark Expected")
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        parts.body.addView(android.widget.TextView(this).apply {
            text = "Works for an Enquiry, a Patient, or someone with no record yet."
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, 0, 0, dp(10))
        })

        val mobileInput = android.widget.EditText(this).apply {
            hint = "Mobile (10 digits) *"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        parts.body.addView(mobileInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); bottomMargin = dp(10) })

        val nameInput = android.widget.EditText(this).apply {
            hint = "Name (auto-fills if found, or type it)"
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        parts.body.addView(nameInput, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        var matchedBranch = if (user.role == "master") "" else user.branch
        var matchToken = 0
        mobileInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val digits = s?.toString()?.filter { it.isDigit() }?.takeLast(10) ?: ""
                if (digits.length != 10) return
                val myToken = ++matchToken
                lifecycleScope.launch {
                    /* 🔵🔒 V531: আগে এখানেও **প্রথম সারিটাই** নেওয়া হত, তাই এক
                       নম্বরে দু'জন আলাদা রোগী থাকলে অন্যজনের নাম বসে যেতে
                       পারত। এখন একাধিক হলে তবেই জিজ্ঞাসা।
                       ⛔ একজন হলে আচরণ হুবহু আগের মতোই। ⛔ ক্লাউড-অনুরোধ
                          বাড়েনি — সেই একটাই ডাক, শুধু `id`/`patientId` ঘর
                          দুটোও আনা হচ্ছে (বাছাই করতে ওগুলো লাগে)। */
                    val pats = withContext(Dispatchers.IO) {
                        try { SupabaseClient.findByMobile("patients", "+91$digits", "id,name,branch,patientId,bill", 20) }
                        catch (_: Throwable) { org.json.JSONArray() }
                    }
                    if (myToken != matchToken) return@launch
                    val people = PatientIdentity.separateIdentities(pats, digits)
                    val chosen: org.json.JSONObject? = if (people.size >= 2) {
                        // স্টাফ Cancel করলে কিছুই বসানো হয় না — ভুল নাম বসার চেয়ে নিরাপদ।
                        askWhichChamberPatient(digits, people) ?: return@launch
                    } else if (pats.length() > 0) {
                        pats.getJSONObject(0)   // ⛔ হুবহু আগের লাইন
                    } else null
                    if (myToken != matchToken) return@launch
                    val found = chosen ?: withContext(Dispatchers.IO) {
                        try {
                            val enq = SupabaseClient.findByMobile("enquiries", "+91$digits", "name,branch")
                            if (enq.length() > 0) enq.getJSONObject(0) else null
                        } catch (_: Throwable) { null }
                    }
                    if (myToken != matchToken) return@launch
                    if (found != null) {
                        val fname = found.s("name")
                        if (fname.isNotBlank() && nameInput.text.isNullOrBlank()) nameInput.setText(fname)
                        val fbranch = found.s("branch")
                        if (fbranch.isNotBlank()) matchedBranch = fbranch
                    }
                }
            }
        })

        parts.body.addView(android.widget.TextView(this).apply {
            text = "Expected Date"
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#145A32"))
            setPadding(0, 0, 0, dp(4))
        })
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) } // defaults to tomorrow
        var chosenDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val dateBtn = android.widget.TextView(this).apply {
            text = "⏰ " + FollowUpModel.displayDate(chosenDate)
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#10223A"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true; isFocusable = true
        }
        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, m, dd ->
                chosenDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, dd)
                dateBtn.text = "⏰ " + FollowUpModel.displayDate(chosenDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000L
            }.show()
        }
        parts.body.addView(dateBtn, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        parts.actionRow.addView(pillButtonChamber("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#0F5C5C")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButtonChamber("💾 Save", "#0C9E33").apply {
            setOnClickListener {
                val digits = mobileInput.text.toString().filter { it.isDigit() }.takeLast(10)
                if (digits.length != 10) { mobileInput.error = "Enter a valid 10-digit mobile"; return@setOnClickListener }
                val name = nameInput.text.toString().trim()
                val branch = matchedBranch.ifBlank { if (user.role == "master") "" else user.branch }
                // 🔒 TK-এর নিয়ম (28.07.2026, খাতার সারি B26): চাপ দেওয়ামাত্র স্টাফ
                // বুঝবেন কাজ শুরু হয়েছে — পর্দা যেন মরা মনে না হয়। এই বোতামটা
                // সেভ করার আগে ক্লাউডে দেখে নেয় আগে থেকে আসার কথা দেওয়া আছে
                // কিনা; ধীর লাইনে ওই অপেক্ষাটায় মনে হত কিছুই হয়নি।
                // ⛔ সেভের নিয়ম বা যাচাই এক অক্ষরও বদলানো হয়নি — শুধু এই
                // পর্দাতেই আগে থেকে ব্যবহৃত একই বার্তা যোগ করা হলো।
                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Saving…", android.widget.Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    // TK-REQUESTED (2026-07-27): if this person ALREADY has an
                    // আসার কথা date, say so first instead of silently
                    // overwriting it. If the existing date cannot be read
                    // (bad line) the answer is null and the save goes ahead
                    // exactly as it does today, so nothing is ever blocked.
                    val existing = withContext(Dispatchers.IO) {
                        try { ChamberAttendanceRepository.findExpectedDate(this@ChamberAttendanceActivity, digits) }
                        catch (_: Throwable) { null }
                    }
                    if (!existing.isNullOrBlank() && existing != chosenDate) {
                        AlertDialog.Builder(this@ChamberAttendanceActivity)
                            .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, "⏰ Expected visit already set"))
                            .setMessage(NoBengali.s("এই রোগীর আসার কথা ইতিমধ্যে দেওয়া হয়েছে — ${FollowUpModel.displayDate(existing)}\n\nনতুন তারিখ ${FollowUpModel.displayDate(chosenDate)} বসাতে চান?"))
                            .setPositiveButton(NoBengali.s("Change the date")) { _, _ ->
                                lifecycleScope.launch { saveExpectedFromChamber(digits, name, branch, chosenDate, parts.dialog) }
                            }
                            .setNegativeButton("Close", null)
                            .show().also { PremiumAlert.paint(it) }
                    } else {
                        saveExpectedFromChamber(digits, name, branch, chosenDate, parts.dialog)
                    }
                }
            }
        })
        parts.dialog.show()
        try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(parts.dialog) } catch (_: Throwable) { }   // 🤫 V774
    }

    /** TK-REQUESTED (2026-07-27): the actual save, kept in one place so the
     *  normal path and the "already has a date, confirmed to change it" path
     *  behave identically. Nothing about what is saved changed. */
    private suspend fun saveExpectedFromChamber(
        digits: String, name: String, branch: String, chosenDate: String,
        hostDialog: android.app.Dialog
    ) {
        withContext(Dispatchers.IO) {
            ChamberAttendanceRepository.markExpected(this@ChamberAttendanceActivity, digits, name, branch, chosenDate, user.mobile)
        }
        android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Marked Expected for ${FollowUpModel.displayDate(chosenDate)}", android.widget.Toast.LENGTH_SHORT).show()
        try { hostDialog.dismiss() } catch (_: Throwable) { }
        if (chosenDate == selectedDate) loadBoard()
    }

    /**
     * 🩺 V763 — রোগের নামের রঙিন চিপ (TK-অনুমোদিত ডিজাইন A)।
     * ⛔ অচেনা রোগের জন্যও রং আছে (ধূসর-সবুজ), তাই কখনো ফাঁকা/সাদা দেখায় না।
     */
    private fun diseaseChip(disease: String): android.widget.TextView {
        val d = resources.displayMetrics.density
        val t = disease.trim().uppercase()
        val (bg, fg) = when {
            t.contains("PILES") -> "#FDECEA" to "#B4231A"
            t.contains("FISSURE") -> "#FFF3E0" to "#A05A00"
            t.contains("FISTULA") -> "#EEF0FD" to "#3B3FA8"
            t.contains("HYDROCELE") -> "#E8F2FF" to "#15549B"
            else -> "#EAF6EE" to "#0B8A3E"
        }
        return android.widget.TextView(this).apply {
            text = t
            textSize = 10.5f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(fg))
            setPadding((11 * d).toInt(), (4 * d).toInt(), (11 * d).toInt(), (4 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 18f * d
                setColor(android.graphics.Color.parseColor(bg))
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() }
        }
    }

    private fun showSearchDialog() {
        val parts = premiumDialogShellChamber("🔍 Search Patient")
        val d = resources.displayMetrics.density
        val queryInput = android.widget.EditText(this).apply {
            hint = "Name / Mobile / Patient ID"
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val padH = (14 * d).toInt(); val pad = (12 * d).toInt()
            setPadding(padH, pad, padH, pad)
        }
        parts.body.addView(queryInput)
        /* 🩺🎨🔒 V763 (২৭.০৮.২০২৬, TK-অনুমোদিত **ডিজাইন A**, ডেমো ফটো দেখে বাছা)
           TK-এর কথা: *"এখানে নাম Type করার পরেও দেখায় না, নীচে search করার পরে
           দেখায় কেন? আমি চাই কয়েকটা Type করলে যেন সাজেস্ট করে। তাছাড়া পেশেন্টের
           নাম, রোগের নামও দেখাক। একটু প্রফেশনাল বানাতে হবে।"*

           ⚡ **খরচের পাহারা (সবচেয়ে জরুরি):** প্রতিটা অক্ষরে খোঁজা হয় **না**।
              টাইপ থামার **০.৫ সেকেন্ড পরে** একবারই যায় (debounce), আর আগের
              অপেক্ষমাণ খোঁজাটা বাতিল হয়ে যায়। তাই "namita" লিখলে ৬টা নয়,
              **একটাই** নেট-কল। ৩ অক্ষরের কম হলে কিছুই যায় না (পুরনো নিয়ম)।
           ⛔ "Arrived" ও ৩-চাপে "Undo"-র কোড **এক অক্ষরও বদলায়নি**। */
        val countLine = android.widget.TextView(this).apply {
            textSize = 12.5f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#147A45"))
            setPadding((4 * d).toInt(), (12 * d).toInt(), 0, 0)
            visibility = View.GONE
        }
        parts.body.addView(countLine)
        val resultsBox = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, (6 * d).toInt(), 0, 0)
        }
        parts.body.addView(resultsBox)

        fun runSearch() {
            val q = queryInput.text.toString().trim()
            resultsBox.removeAllViews()
            countLine.visibility = View.GONE
            if (q.isBlank()) return
            // TK-REQUESTED OPTIMIZATION (2026-07-16): at least 3 characters
            // before a search request goes out at all, to keep Supabase
            // free-quota usage low.
            if (q.length < 3) {
                resultsBox.addView(android.widget.TextView(this).apply {
                    text = "Type at least 3 characters to search."
                    setTextColor(android.graphics.Color.parseColor("#B8860B"))
                })
                return
            }
            resultsBox.addView(android.widget.TextView(this).apply {
                text = "Searching…"; setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            })
            lifecycleScope.launch {
                val results = withContext(Dispatchers.IO) {
                    ChamberAttendanceRepository.searchPatients(q, selectedBranch)
                }
                resultsBox.removeAllViews()
                if (results.isEmpty()) {
                    resultsBox.addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                        text = "No matching patient found."
                        setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    })
                    return@launch
                }
                countLine.text = if (results.size == 1) "1 patient found"
                    else "${results.size} patients found"
                countLine.visibility = View.VISIBLE
                results.forEach { res ->
                    // 🎨 ডিজাইন A — বাঁয়ে সবুজ দাগ · নাম + রোগের চিপ · নিচে মোবাইল ও আইডি
                    val card = android.widget.LinearLayout(this@ChamberAttendanceActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = 14f * d
                            setColor(android.graphics.Color.WHITE)
                            setStroke((1 * d).toInt(), android.graphics.Color.parseColor("#DBE8E2"))
                        }
                        elevation = 2f * d
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = (9 * d).toInt(); bottomMargin = (2 * d).toInt() }
                    }
                    card.addView(View(this@ChamberAttendanceActivity).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#0E7A72"))
                    }, android.widget.LinearLayout.LayoutParams(
                        (5 * d).toInt(), android.widget.LinearLayout.LayoutParams.MATCH_PARENT))
                    val row = android.widget.LinearLayout(this@ChamberAttendanceActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding((12 * d).toInt(), (11 * d).toInt(), (11 * d).toInt(), (11 * d).toInt())
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    card.addView(row)
                    val info = android.widget.LinearLayout(this@ChamberAttendanceActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(android.widget.LinearLayout(this@ChamberAttendanceActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                                text = res.name.trim().uppercase()
                                textSize = 15.5f
                                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor("#17312A"))
                                maxLines = 2
                                copyOnLongPress("Name/Mobile", "${res.name} · ${res.mobile}")
                            }, android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                            // 🩺 রোগের চিপ — না থাকলে বসেই না (ফাঁকা চিপ দেখায় না)
                            if (res.disease.isNotBlank()) addView(diseaseChip(res.disease))
                        })
                        addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                            text = res.mobile + (if (res.patientId.isNotBlank()) "   ·   ${res.patientId}" else "")
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#5C6B64"))
                            setPadding(0, (5 * d).toInt(), 0, 0)
                        })
                    }
                    val arrivedBtn = pillButtonChamber("✅ Arrived", "#0C9E33").apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                        setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
                    }
                    // TK APPROVED (2026-07-16): "Undo" for an accidental
                    // Arrived mark -- 3 taps required (same standing 3-tap
                    // pattern used everywhere else in the app) before it
                    // actually deletes anything. This button switches
                    // between two modes: "✅ Arrived" (tap to mark) and,
                    // once marked, "↩️ Undo" (V773: এক চাপ + "Are you sure?" পপ-আপ)।
                    var markedRowId: String? = null

                    fun setArrivedMode() {
                        arrivedBtn.text = "✅ Arrived"
                        arrivedBtn.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#0C9E33"))
                            cornerRadius = 12f * d
                        }
                    }
                    fun setUndoMode() {
                        arrivedBtn.text = "↩️ Undo"
                        arrivedBtn.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#C88C14"))
                            cornerRadius = 12f * d
                        }
                    }

                    arrivedBtn.setOnClickListener {
                        val id = markedRowId
                        if (id == null) {
                            // Mode 1: not yet marked -- mark Arrived (1 tap).
                            arrivedBtn.isEnabled = false
                            lifecycleScope.launch {
                                val newId = withContext(Dispatchers.IO) {
                                    ChamberAttendanceRepository.markArrived(this@ChamberAttendanceActivity, res.mobile, res.name, res.branch, user.mobile)
                                }
                                arrivedBtn.isEnabled = true
                                markedRowId = newId
                                setUndoMode()
                                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "${res.name} marked Arrived", android.widget.Toast.LENGTH_SHORT).show()
                                loadBoard()
                            }
                        } else {
                            /* 🛡️🔴🔒 V773 (২৮.০৮.২০২৬) — **TK-এর আসল অভিযোগটা এখানেই।**
                               TK: *"আমি তো বললাম পেমেন্ট মুছে গেছিল… সেখানে চাপ দিলে
                               অটোমেটিক ডিলিট হয়ে যায় — আমি চাইছি সতর্কবার্তা দিক,
                               Are you sure"*।

                               **আগে কী হত:** পরপর **৩ বার চাপ** দিলেই সারিটা মুছে যেত
                               (`undoAttendanceMark` → payments সারি সত্যিই ডিলিট)।
                               মাঝের দুটো চাপে শুধু একটা টোস্ট ভেসে উঠত — তাড়াহুড়োয়
                               সেটা চোখেই পড়ে না। তাই ভুল করে পেমেন্ট মুছে গিয়েছিল।

                               **এখন:** এক চাপেই **"Are you sure?" পপ-আপ**, আর "Yes"
                               প্রথম ১ সেকেন্ড নিষ্ক্রিয় — অর্থাৎ তাড়াহুড়োর চাপ কখনো
                               ওটায় লাগতে পারে না। ⛔ Cancel প্রথম থেকেই সচল।
                               ⛔ গোনার পুরনো নিয়ম (৩ চাপ) তুলে দেওয়া হলো — ওটাই
                                  বিপদটা তৈরি করেছিল; সতর্কবার্তা তার চেয়ে অনেক জোরালো।
                               ⛔ Arrived করার পথ ও ডিলিটের আসল কাজ এক অক্ষরও বদলায়নি। */
                            val ask = androidx.appcompat.app.AlertDialog.Builder(this@ChamberAttendanceActivity)
                                .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, "↩️ Undo Arrived?"))
                                .setMessage(res.name + " will be removed from today's board and this entry will be deleted.")
                                .setPositiveButton("Yes, undo") { _, _ ->
                                    arrivedBtn.isEnabled = false
                                    lifecycleScope.launch {
                                        val ok = withContext(Dispatchers.IO) { ChamberAttendanceRepository.undoAttendanceMark(this@ChamberAttendanceActivity, id) }
                                        arrivedBtn.isEnabled = true
                                        android.widget.Toast.makeText(
                                            this@ChamberAttendanceActivity,
                                            if (ok) "Undone — ${res.name} removed from today's board" else "Could not undo — check connection",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                                        if (ok) {
                                            markedRowId = null
                                            setArrivedMode()
                                            loadBoard()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .setCancelable(false)
                                .show().also { PremiumAlert.paint(it) }
                            ask.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let { yes ->
                                yes.isEnabled = false
                                yes.alpha = 0.45f
                                yes.postDelayed({
                                    try { yes.isEnabled = true; yes.alpha = 1f } catch (_: Throwable) {}
                                }, 1000L)
                            }
                        }
                    }
                    row.addView(info)
                    row.addView(arrivedBtn)
                    resultsBox.addView(card)
                }
            }
        }
        queryInput.setOnEditorActionListener { _, _, _ -> runSearch(); true }

        /* ⚡🔒 V763 — **টাইপ থামলে তবেই খোঁজা** (TK: "কয়েকটা Type করলে যেন সাজেস্ট করে")।
           ⛔ প্রতিটা অক্ষরে নয় — প্রতিবার নতুন অক্ষর পড়লে আগের অপেক্ষমাণ খোঁজাটা
              **বাতিল** হয়ে যায়, আর শেষ অক্ষরের ০.৫ সেকেন্ড পরে একবারই যায়।
              ⇒ "namita" লিখলে ৬টা নয়, **১টা** নেট-কল। Supabase-এ বাড়তি চাপ নেই।
           ⛔ ৩ অক্ষরের কম হলে কিছুই যায় না — পুরনো নিয়মটাই (runSearch-এর ভিতরে)।
           ⛔ পর্দা বন্ধ হলে অপেক্ষমাণ কাজটাও মুছে ফেলা হয় (নইলে বন্ধ পর্দায় কাজ চলত)। */
        val typeHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val typeJob = Runnable { runSearch() }
        queryInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, x: Int) {}
            override fun onTextChanged(c: CharSequence?, a: Int, b: Int, x: Int) {}
            override fun afterTextChanged(e: android.text.Editable) {
                typeHandler.removeCallbacks(typeJob)
                if (e.toString().trim().length >= 3) typeHandler.postDelayed(typeJob, 500L)
                else { resultsBox.removeAllViews(); countLine.visibility = View.GONE }
            }
        })
        parts.dialog.setOnDismissListener { typeHandler.removeCallbacks(typeJob) }

        // 🔍 V763 — টাইপ করলেই খোঁজা হয় বলে আলাদা "Search" বোতামের আর দরকার নেই
        //    (TK: *"নীচে search করার পরে দেখায় কেন?"*)। কীবোর্ডের নিজের Search
        //    বোতামটা আগের মতোই কাজ করে (উপরের setOnEditorActionListener)।
        parts.actionRow.addView(pillButtonChamber("Close", "#0A8C8C").apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.dialog.show()
        try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(parts.dialog) } catch (_: Throwable) { }   // 🤫 V774
    }

    /** Updates the SAME Follow-up "Last Remark" field the Visit/Patient
     *  cards already show, via the SAME repository function
     *  (FollowUpRepository.updateRemark) those cards' own remark-edit uses
     *  -- no separate remark storage. Relabelled "Treatment" on screen per
     *  TK's naming, same underlying field/save path, unchanged. */
    // ============================================================
    // TK-APPROVED (2026-07-20): Chamber grid cell actions.
    //  - "✅ এসেছেন" button (Expected rows) -> mark Arrived.
    //  - Cash / Online cell -> take a cash-only / online-only Treatment
    //    payment when empty; when already paid the cell is 3-tap-locked and
    //    opens the Review-style "pick which payment to fix" editor.
    //  - Treatment cell -> write today's treatment (free text + quick chips).
    // Everything writes to the SAME source (payments / follow-up remark), so
    // every other screen updates automatically.
    // ============================================================
    private fun markArrivedFromRow(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) { android.widget.Toast.makeText(this, "No valid mobile", android.widget.Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { ChamberAttendanceRepository.markArrived(this@ChamberAttendanceActivity, "+91$digits", row.name, row.branch, user.mobile); true }
                catch (_: Throwable) { false }
            }
            android.widget.Toast.makeText(this@ChamberAttendanceActivity, NoBengali.s(if (ok) "${row.name.ifBlank { digits }} — এসেছেন ✅" else "Failed — retry"), android.widget.Toast.LENGTH_SHORT).show()
            if (ok) loadBoard()
        }
    }

    /** TK-DECISION (2026-07-22): cancel an "আসার কথা" (Expected) entry with a
     *  reason (long-press the waiting row). Removes the person's single
     *  chamber_expected row (deterministic id) and, if they have a Follow-up
     *  record, records the chosen reason on their Last Remark so there is a
     *  trail of why the আসার কথা was cancelled. */
    private fun showCancelExpectedDialog(row: ChamberAttendanceRow) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) { android.widget.Toast.makeText(this, "No valid mobile", android.widget.Toast.LENGTH_SHORT).show(); return }
        // TK-REQUESTED (2026-07-27): the SAME wording and the SAME
        // type-your-own rule as Patient Details, so the two screens never
        // disagree. Second reason reworded; "অন্য কারণ" now makes the staff
        // type why, and a blank reason is refused.
        val reasons = arrayOf("আসতে পারবেন না", "আসার তারিখ পরিবর্তন করেছেন", "অন্য কারণ")
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "আসার কথা বাতিল — ${row.name.ifBlank { digits }}"))
            .setItems(reasons) { _, which ->
                if (which == reasons.size - 1) askOtherCancelReasonChamber(row, digits)
                else doCancelExpectedChamber(row, digits, reasons[which])
            }
            .setNegativeButton("Close", null)
            .show().also { PremiumAlert.paint(it) }
    }

    /** TK-REQUESTED (2026-07-27): "অন্য কারণ" must be typed out by the staff,
     *  so there is always a real trail of why an আসার কথা was cancelled. */
    private fun askOtherCancelReasonChamber(row: ChamberAttendanceRow, digits: String) {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val input = android.widget.EditText(this).apply {
            hint = NoBengali.s("এখানে লিখুন…")
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val p = dp(12); setPadding(p, p, p, p)
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        val wrap = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), 0)
            addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                text = NoBengali.s("কী কারণে বাতিল করছেন, লিখুন")
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#1D2939"))
            })
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "অন্য কারণ"))
            .setView(wrap)
            .setPositiveButton("Save", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val typed = input.text.toString().trim()
                if (typed.isEmpty()) {
                    android.widget.Toast.makeText(this, NoBengali.s("কারণ লিখুন"), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                    doCancelExpectedChamber(row, digits, typed)
                }
            }
        }
        dialog.show()
        PremiumAlert.paint(dialog)
    }

    private fun doCancelExpectedChamber(row: ChamberAttendanceRow, digits: String, reason: String) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ChamberAttendanceRepository.cancelExpected(this@ChamberAttendanceActivity, "+91$digits")
                    if (row.followUpId.isNotBlank()) {
                        FollowUpRepository(this@ChamberAttendanceActivity)
                            .updateRemark(row.followUpId, "আসার কথা বাতিল: $reason", user.name.ifBlank { user.mobile })
                    }
                    true
                } catch (_: Throwable) { false }
            }
            android.widget.Toast.makeText(this@ChamberAttendanceActivity, NoBengali.s(if (ok) "বাতিল হলো ✅" else "Failed — retry"), android.widget.Toast.LENGTH_SHORT).show()
            if (ok) loadBoard()
        }
    }

    private fun takeOrEditPayment(row: ChamberAttendanceRow, mode: String) {
        val current = if (mode == "CASH") row.paymentCash else row.paymentOnline
        if (current > 0.0) fixPaymentInReview(row) else takePaymentPopup(row, mode)
    }

    // TK-REQUESTED (2026-07-25): Cash/Online payment can now be taken even
    // when this patient has NO Bill yet -- previously this silently failed
    // ("Failed — retry", no real reason given) because saveTreatmentPayment
    // required a Bill > 0. Now: (1) if there's no Bill, a reminder shows
    // and Bill can optionally be entered right here, in the SAME popup, at
    // the same time as the payment; (2) if a Bill DOES exist, entering a
    // payment that would push total Paid past it warns first and needs an
    // explicit confirm, so nobody can accidentally over-collect without at
    // least seeing it happen.
    private fun takePaymentPopup(row: ChamberAttendanceRow, mode: String) {
        val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) { android.widget.Toast.makeText(this, "No valid mobile", android.widget.Toast.LENGTH_SHORT).show(); return }
        // ⚡ TK (28.07.2026 ৩.০০ pm): আগে রোগীর হিসাব ক্লাউড থেকে আসা পর্যন্ত
        // অপেক্ষা করে তবেই বক্সটা খুলত — ধীর লাইনে তাই মনে হত চাপ দিয়ে কিছুই
        // হচ্ছে না। এখন বক্স **সঙ্গে সঙ্গে** খোলে, হিসাব পিছনে আসে।
        // 🔒 টাকার নিরাপত্তা: **হিসাব না আসা পর্যন্ত Save বোতাম কাজ করবে না** —
        // ঠিক যে নিয়ম Follow-up-এর Advance ও কততম-পেমেন্ট পপ-আপে TK আগেই
        // পাশ করেছেন (খাতার সারি B16)। তাই ভুল টাকা কখনো সেভ হতে পারে না।
        val patientJob = lifecycleScope.async(Dispatchers.IO) {
            try {
                val repo = PaymentRepository(this@ChamberAttendanceActivity)
                /* 🔵🔒 V520 (২২.০৮.২০২৬): এক মোবাইলে দুজন আলাদা রোগী থাকলে
                   `row.patientId` (রোগী-প্রতি **অনন্য** Official Patient ID) ধরে
                   ঠিক রোগীটাই বেছে নেওয়া হয় — টাকা কখনো অন্যজনের নামে যাবে না।
                   ⛔ আইডি ফাঁকা থাকলে বা ওই নম্বরে না মিললে হুবহু আগের পথ। */
                repo.findPatientByMobile(digits, row.branch, preferPatientCode = row.patientId, preferRowId = row.patientRowId)
                    ?: repo.findOrMakePatient(row.name, digits, row.branch, row.patientId)
            } catch (_: Throwable) { null }
        }
        showTakePaymentDialog(row, mode, digits, patientJob)
    }

    private fun showTakePaymentDialog(
        row: ChamberAttendanceRow, mode: String, digits: String,
        patientJob: kotlinx.coroutines.Deferred<PatientBillInfo?>
    ) {
        // 🔒 B619 (11.08.2026, TK-নির্দেশ "ক"): যে ব্রাঞ্চের স্টাফ শুধু সেই ব্রাঞ্চের রোগীর
        // টাকা নিতে পারবে — অন্য ব্রাঞ্চের রোগী হলে টাকার ঘর খোলার আগেই আটকাই (সেভের
        // saveTreatmentPayment-এর guard backstop হিসেবে অটুট)। row-এই branch/patientId আছে।
        if (!MoneyBranchGuard.canTakeMoney(this, row.branch, row.patientId)) {
            android.widget.Toast.makeText(this, MoneyBranchGuard.blockMessage(row.branch), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val d = resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
        // হিসাব আসার আগে এগুলো ফাঁকা; আসামাত্র বসে যায় এবং Save চালু হয়।
        var patient: PatientBillInfo? = null
        var hasBill = false
        /* 🎨🔒🔒 V876 (৩০.০৮.২০২৬, TK-নির্দেশ, ডেমো-প্রুফে অনুমোদিত):
           TK: *"২ যায়গায় ২ রকম কেন? Follow Up card এ যে রকম সেরকম ই থাকতে হবে"*
           ⇒ এই বাক্সটা এখন Follow-up কার্ডের Advance Payment বাক্সের
             (`dialog_advance.xml`) হুবহু একই চেহারায় — নেভি মাথা, গোল ঘর,
             বাঁয়ে লেখা · ডানে টাকা, নিচে Close/Save পিল-বোতাম।
           TK-এর আরও তিনটে নির্দেশ এখানে মানা হয়েছে:
             · কোনো বাংলা লেখা নেই
             · "No bill set…" ধরনের সতর্কবার্তা নেই
             · প্রকৃত জমার তারিখের ঘরটা **হালকা/হাইড-টাইপ**, আর মাথার আইকন বাদ
           ⛔ টাকার সেভ · যাচাই · কনফার্ম · day-guard — একটা অক্ষরও বদলায়নি,
              শুধু চেহারা। */
        fun rowField(label: String, dim: Boolean = false): Pair<android.widget.LinearLayout, android.widget.EditText> {
            val et = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
                hint = "Enter amount"
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                gravity = android.view.Gravity.END
                textSize = if (dim) 17f else 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(if (dim) "#B26A00" else "#16A36D"))
                setHintTextColor(android.graphics.Color.parseColor("#B7C0CE"))
                minWidth = dp(96)
                setPadding(0, 0, 0, 0)
            }
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = label; textSize = 12.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(et)
            }
            return row to et
        }
        val (rowBill, billInput) = rowField("Total Bill", dim = true)
        val (rowAmt, amt) = rowField("Amount")
        // TK-REQUESTED (2026-07-25): same backdate date-picker pattern as
        // PaymentActivity's own Add-Treatment-Payment dialog (dateLabel/
        // dateValue/pickedActualDate there) -- reused here verbatim so
        // both payment-entry points behave identically.
        var pickedActualDate = PaymentModel.today()
        /* 🎨 V876 — TK: *"এই ঘরটা উজ্জ্বলতা কম থাকবে, হাইড টাইপের থাকবে"*
           ⇒ হালকা ধূসর লেখা, বাকি ঘরের মতো একই গোল ঘরে। ⛔ কাজ অপরিবর্তিত। */
        val dateValue = android.widget.TextView(this).apply {
            text = "Today \u2014 tap to change"
            textSize = 13f
            gravity = android.view.Gravity.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#AEB8C4"))
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                val cal = java.util.Calendar.getInstance()
                android.app.DatePickerDialog(this@ChamberAttendanceActivity, { _, y, m, dd ->
                    val cal2 = java.util.Calendar.getInstance().apply { set(y, m, dd) }
                    val iso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal2.time)
                    pickedActualDate = iso
                    text = if (iso == PaymentModel.today()) "Today \u2014 tap to change"
                        else DateUtil.display(iso)
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        }
        // দুটো ঘরই আগেই তৈরি — হিসাব এলে কেবল লেখা ও দেখা/না-দেখা ঠিক হয়।
        val infoLine = android.widget.TextView(this).apply {
            text = "Loading\u2026"
            textSize = 11f; setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, 0, 0, dp(8))
        }
        rowBill.visibility = android.view.View.GONE
        fun gapView() = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(4))
            addView(infoLine)
            addView(rowBill)
            addView(gapView())
            addView(rowAmt)
            addView(gapView())
            // TK-REQUESTED (2026-07-25, fixes TK's live-reported "can't take
            // payment for past days"): this dialog was completely missing
            // the "actual deposit date" picker PaymentActivity's own
            // Add-Treatment-Payment dialog already has -- staff using
            // Chamber Attendance's Cash/Online cell (the most-used payment
            // entry point) had NO way to backdate at all. Same exact
            // pattern/wording as PaymentActivity: defaults to today (no
            // behaviour change for the normal case); past dates only.
            addView(android.widget.LinearLayout(this@ChamberAttendanceActivity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = "Actual Date"; textSize = 12.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#AEB8C4"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(dateValue)
                isClickable = true
                setOnClickListener { dateValue.performClick() }
            })
        }
        UppercaseInputUtil.applyToAll(box)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            /* 🎨 V876 — TK: *"উপরে ক্যাশ পেমেন্টের বাঁ পাশে আইকন থাকবে না"*
               ⇒ আইকন বাদ, আর সবুজ মাথার বদলে Follow-up কার্ডের নেভি মাথা
                 (`dialog_advance.xml`-এর হুবহু একই রং ও মাপ)। */
            .setCustomTitle(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_header_navy_top_round)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = "$mode Payment"; textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.WHITE)
                })
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = listOf(row.name.ifBlank { digits }, digits, row.branch)
                        .filter { it.isNotBlank() }.joinToString(" \u00b7 ")
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#B8C6D8"))
                    setPadding(0, dp(3), 0, 0)
                })
            })
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val value = amt.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (value <= 0) { android.widget.Toast.makeText(this, "Enter a valid amount", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val p = patient
                if (p == null) { android.widget.Toast.makeText(this, NoBengali.s("Figures have not arrived yet — one moment"), android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val paidSoFar = p.paid
                val enteredBill = if (!hasBill) (billInput.text.toString().trim().toDoubleOrNull() ?: 0.0) else p.bill
                // TK-DECISION (2026-07-26): the Total Bill is NOT forced here.
                // A bill-check was tried in the Chamber too, but TK removed it
                // on purpose: the chamber counter is the busiest place in the
                // clinic and staff must never be blocked from taking money
                // that a patient is handing over. The bill stays mandatory
                // where there IS time for it . Follow-up's Advance and the
                // 2nd/3rd Payment dialogs.
                // TK-REQUESTED (2026-07-25): Bill যা আছে (বা এইমাত্র বসানো
                // হলো) তার থেকে বেশি Paid হয়ে যাচ্ছে কিনা যাচাই -- হলে
                // সতর্কবার্তা, তারপর নিশ্চিত করলে তবেই এগোবে।
                val wouldExceed = enteredBill > 0.0 && (paidSoFar + value) > enteredBill
                val proceed = {
                    // TK-REQUESTED (2026-07-24): confirm before actually saving --
                    // this box had no confirm step at all before, so a mistyped
                    // amount (or a wrong tap) went straight to the database.
                    val isBackdated = pickedActualDate != PaymentModel.today()
                    val msg = "₹${"%,.0f".format(value)} ($mode) for ${row.name.ifBlank { digits }}" +
                        (if (isBackdated) "\n⏰ তারিখ: ${DateUtil.display(pickedActualDate)}" + (if (user.role != "master") " (Master-এর অনুমোদন লাগবে)" else "") else "") +
                        " — Save?"
                    AlertDialog.Builder(this)
                        .setCustomTitle(PremiumAlert.header(this, "Confirm Payment"))
                        .setMessage(msg)
                        .setPositiveButton("Yes, Save") { _, _ ->
                            // 🔒🔒 খাতার সারি B200 (TK, 30.07.2026 রাত — "সাবধানে করুন,
                            // রাস্তা যেন ভালো হয়"): Payment-এর গভীরে গিয়ে দেখা গেছে
                            // এখানে ঝুঁকি ছাড়া বড় কোনো গতি বাড়ানোর সুযোগ নেই —
                            // `confirmedTakePayment()`-এর ভিতরের দ্বিতীয়বার রোগী-খোঁজা
                            // (যেটা প্রথম নজরে অপ্রয়োজনীয়/সদৃশ মনে হচ্ছিল) আসলে
                            // **অপরিহার্য**: `PaymentRepository`-র "আজ ইতিমধ্যে কত
                            // টাকা নেওয়া হয়েছে" (day-guard) হিসাবটা প্রতিটা
                            // `PaymentRepository()`-এর নিজের আলাদা মেমোরিতে থাকে
                            // (companion/global নয়) — তাই আগে থেকে আনা তথ্য ব্যবহার
                            // করলে এই যাচাইটাই নিঃশব্দে ভুল ফল দিত (দিনে দুবার টাকা
                            // নেওয়া আটকানোর সুরক্ষা ফাঁকা থেকে যেত) — এটাই TK-এর
                            // সবচেয়ে বড় দুশ্চিন্তার জায়গা, তাই এই লুকআপ **ছোঁয়া
                            // হয়নি**।
                            //
                            // যেটুকু ঝুঁকিহীনভাবে করা গেল: "Yes, Save" চাপার
                            // **সঙ্গে সঙ্গেই** একটা স্বীকৃতি-বার্তা দেখানো হয় (তাপটা
                            // যে কাজ করেছে সেটা তখনই বোঝা যায়) — এর নিচের আসল
                            // যাচাই/সেভ প্রক্রিয়ার (`confirmedTakePayment`) একটা
                            // অক্ষরও বদলায়নি, শুধু এই একটা Toast যোগ হলো।
                            android.widget.Toast.makeText(this, "Saving…", android.widget.Toast.LENGTH_SHORT).show()
                            confirmedTakePayment(row, mode, digits, value, enteredBill, pickedActualDate)
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }
                if (wouldExceed) {
                    val due = (enteredBill - paidSoFar).coerceAtLeast(0.0)
                    AlertDialog.Builder(this)
                        .setCustomTitle(PremiumAlert.header(this, NoBengali.s("⚠️ Going higher than the Bill")))
                        .setMessage(NoBengali.s("Bill ₹${"%,.0f".format(enteredBill)} · still due ₹${"%,.0f".format(due)}. You are taking ₹${"%,.0f".format(value)} — this is more than the bill. Continue anyway?"))   /* 🔤 V726 */
                        .setPositiveButton(NoBengali.s("Yes, continue")) { _, _ -> proceed() }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                } else proceed()
            }
            .setNegativeButton("Close", null)
            .show().also { dlg ->   // ⛔ V774 — এখানে নিচে PremiumAlert.paint(dlg) আগে থেকেই আছে, তাই আলাদা কিছু লাগেনি
                // 🔒🔒 খাতার সারি B181 (TK, 30.07.2026): এই বাইরের ডায়ালগটার
                // (নিজের টাইটেল/লেবেল) নিজে থেকে কোনো পাহারা ছিল না — ভিতরের
                // দুটো নেস্টেড কনফার্ম-ডায়ালগ (উপরে) আগে থেকেই ঢাকা ছিল।
                PremiumAlert.paint(dlg)
                /* 🎨 V876 — Follow-up কার্ডের Close / Save Advance বোতামের
                   হুবহু একই পিল-চেহারা (`bg_pill_ghost` ও `bg_btn_green`)।
                   ⛔ বোতামের কাজ এক অক্ষরও বদলায়নি — শুধু চেহারা। */
                try {
                    val negBtn: android.widget.Button? = dlg.getButton(AlertDialog.BUTTON_NEGATIVE)
                    if (negBtn != null) {
                        negBtn.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_pill_ghost)
                        negBtn.setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                        negBtn.isAllCaps = false
                        negBtn.setPadding(dp(22), dp(11), dp(22), dp(11))
                    }
                    val posBtn: android.widget.Button? = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                    if (posBtn != null) {
                        posBtn.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_btn_green)
                        posBtn.setTextColor(android.graphics.Color.WHITE)
                        posBtn.isAllCaps = false
                        posBtn.setPadding(dp(26), dp(11), dp(26), dp(11))
                    }
                } catch (_: Throwable) { }
                // 🔒 হিসাব না আসা পর্যন্ত Save বন্ধ — টাকার জায়গায় কোনো আন্দাজ নয়।
                val save = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                save?.isEnabled = false
                lifecycleScope.launch {
                    val p = try { patientJob.await() } catch (_: Throwable) { null }
                    if (isFinishing || isDestroyed) return@launch
                    if (p == null) {
                        infoLine.text = "Could not load the figures \u2014 check the line and try again"
                        infoLine.setTextColor(android.graphics.Color.parseColor("#B42318"))
                        return@launch
                    }
                    patient = p
                    hasBill = p.bill > 0.0
                    /* 🎨 V876 — TK: *"no bill set for this payment — এই ধরনের
                       লেখাও থাকবে না"* ⇒ সতর্কবার্তাটা বাদ। বিল বসানো না থাকলে
                       শুধু Total Bill-এর ঘরটা দেখায়, কোনো লেখা নয়।
                       ⛔ বিল থাকলে হিসাবের লাইনটা আগের মতোই (ওটা সতর্কবার্তা নয়)। */
                    if (hasBill) {
                        infoLine.text = "Bill ₹${"%,.0f".format(p.bill)} · Paid so far ₹${"%,.0f".format(p.paid)}"
                        infoLine.setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                        infoLine.visibility = android.view.View.VISIBLE
                        rowBill.visibility = android.view.View.GONE
                    } else {
                        infoLine.visibility = android.view.View.GONE
                        rowBill.visibility = android.view.View.VISIBLE
                    }
                    save?.isEnabled = true
                }
            }
    }

    /** Actually saves the Chamber Cash/Online payment -- split out from
     *  takePaymentPopup so the Save button can show a confirm step first
     *  (TK-REQUESTED 2026-07-24) without duplicating the save logic. */
    // 🔒 খাতার সারি B52 (TK, 28.07.2026 রাত): `skipDayGuard` শুধু তখনই `true`
    // হয়, যখন স্টাফ সতর্কবার্তা দেখে নিজে "Yes, add it" বলেছেন। বাকি সব পুরনো
    // ডাক আগের মতোই (ডিফল্ট `false`), তাই কোনো কিছু বদলায়নি।
    private fun confirmedTakePayment(row: ChamberAttendanceRow, mode: String, digits: String, value: Double, enteredBill: Double = 0.0, pickedDate: String = PaymentModel.today(), skipDayGuard: Boolean = false) {
        val isBackdated = pickedDate != PaymentModel.today()
        // 🚨 TK-এর নিয়ম (28.07.2026, খাতার সারি B30): নেট যাচাই না হলে নতুন রোগী
        // তৈরি হবে না — তখন এই চিহ্নটা ওঠে আর স্টাফকে স্পষ্ট ওয়ার্নিং দেখানো হয়।
        var patientNotVerified = false
        // 🔒 খাতার সারি B52: আজ এই রোগীর নামে ইতিমধ্যে টাকা নেওয়া হয়ে থাকলে
        // এখানে (অঙ্ক · নাম · আজকের নম্বর) জমা হয়, টাকা সেভ না করেই ফেরা হয়,
        // আর স্টাফকে প্রশ্নটা দেখানো হয়।
        var dayGuardAmount = 0.0
        var dayGuardName = ""
        var dayGuardLabel = ""
        // 🔴🆕🔒 V439 (TK-রিপোর্ট ১৮.০৮.২০২৬ — *"অনুমতি দিয়েছি তবুও হয় না"*):
        //    Master-এর দেওয়া ব্যাকডেট-অনুমতি (`BackdatePaymentGrant`) এতদিন
        //    **শুধু Payment পর্দায়** কাজ করত। চেম্বারের Cash/Online ঘর থেকে
        //    টাকা নিলে ওটা দেখাই হত না — তাই অনুমতি থাকা সত্ত্বেও প্রতিবার
        //    "Master-এর কাছে অনুরোধ পাঠানো হয়েছে" আসত। এখন এখানেও দেখা হয়।
        //    ⛔ অনুমতি না থাকলে আচরণ হুবহু আগের মতোই (অনুরোধ যাবে)।
        var backdateOkByGrant = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    // TK-REQUESTED (2026-07-24): a payment taken directly
                    // here (Chamber Attendance's Cash/Online cell) now
                    // goes through the EXACT same shared pipeline as
                    // Patient Card / Visit Card ("Advance Here") --
                    // PaymentRepository.findPatientByMobile() (real bill/
                    // id/paid-count) + saveTreatmentPayment() (correct
                    // Advance/2nd/3rd... label, bill/stage update,
                    // Report Card visibility) -- instead of the old
                    // direct-insert that skipped all of that (always
                    // said "Chamber CASH Payment", never updated bill/
                    // stage, so it wasn't counted the same way a
                    // payment from any other screen was).
                    // 🚨 TK-এর নিয়ম (28.07.2026, খাতার সারি B30): রোগীর ডুপ্লিকেট কখনোই নয়।
                    // নেট ঠিকমতো কাজ না করলে এখন আর নতুন রোগী তৈরি হয় না
                    // (`findOrMakePatient` তখন কিছুই বানায় না) — তাই এখানে
                    // রোগী না পেলে টাকা নেওয়া যাবে না, স্টাফকে জানাতে হবে।
                    val repo = PaymentRepository(this@ChamberAttendanceActivity)
                    /* 🔵🔒 V520: রোগী-প্রতি অনন্য Official Patient ID ধরে ঠিক রোগী। */
                    val patient = repo.findPatientByMobile(digits, row.branch, preferPatientCode = row.patientId, preferRowId = row.patientRowId)
                        ?: repo.findOrMakePatient(row.name, digits, row.branch, row.patientId)
                        ?: run { patientNotVerified = true; return@withContext false }
                    // TK-REQUESTED (2026-07-25): backdated entry -- Master
                    // saves it directly with the picked date (same as
                    // PaymentActivity already does); Staff instead sends it
                    // through the EXISTING Backdate-request approval queue
                    // (Dashboard bell -> Briefing), never writing the real
                    // payment until Master approves. Same-day (the normal
                    // case) is completely unchanged either way.
                    if (isBackdated && user.role != "master") {
                        backdateOkByGrant = try {
                            BackdatePaymentGrant.isGrantedNow(user.mobile, pickedDate)
                        } catch (_: Throwable) { false }
                    }
                    if (isBackdated && user.role != "master" && !backdateOkByGrant) {
                        repo.requestBackdatePayment(
                            patient, enteredBill, value, mode, "Chamber $mode payment", pickedDate, user.mobile, user.name.ifBlank { user.mobile }
                        )
                    } else if (!skipDayGuard && !isBackdated && repo.paidOnDateFor(patient.id) > 0.0) {
                        // 🔒 খাতার সারি B52: আজ এই রোগীর নামে টাকা নেওয়া হয়ে গেছে —
                        // ⛔ এখানে কিছুই সেভ হয় না; স্টাফকে আগে জিজ্ঞাসা করা হয়।
                        dayGuardAmount = repo.paidOnDateFor(patient.id)
                        dayGuardName = patient.name
                        dayGuardLabel = repo.nextLabelFor(patient.id)
                        false
                    } else {
                        repo.saveTreatmentPayment(
                            patient, enteredBill, value, mode, "Chamber $mode payment", user.mobile,
                            overrideDate = if (isBackdated) pickedDate else null,
                            backdateRequestedBy = if (isBackdated) user.mobile else null,
                            backdateApprovedBy = if (isBackdated) user.mobile else null
                        )
                    }
                } catch (_: Throwable) { false }
            }
            // 🔒 খাতার সারি B52: টাকা নেওয়া হয়নি, শুধু প্রশ্নটা বাকি — স্টাফ
            // "Yes, add it" বললে ঠিক আগের সেই সেভটাই আবার চলে (এবার প্রশ্ন ছাড়া)।
            if (dayGuardAmount > 0.0) {
                PaymentDayGuard.confirmIfAlreadyPaidToday(
                    this@ChamberAttendanceActivity, dayGuardAmount, dayGuardName, dayGuardLabel
                ) {
                    confirmedTakePayment(row, mode, digits, value, enteredBill, pickedDate, skipDayGuard = true)
                }
                return@launch
            }
            val msg = when {
                patientNotVerified ->
                    "নেট যাচাই করা যায়নি — এই নম্বর আগে থেকে রেজিস্টার আছে কিনা দেখা যাচ্ছে না। " +
                    "ডুপ্লিকেট রোগী তৈরি এড়াতে টাকা নেওয়া হয়নি। একটু পরে আবার চেষ্টা করুন।"
                !ok -> "Failed — retry"
                isBackdated && user.role != "master" && !backdateOkByGrant -> "Master-এর কাছে অনুরোধ পাঠানো হয়েছে ✅"
                else -> "$mode payment saved ✅"
            }
            android.widget.Toast.makeText(
                this@ChamberAttendanceActivity, msg,
                if (patientNotVerified) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
            ).show()
            if (ok) loadBoard()
            // 🔒 TK-APPROVED (28.07.2026): চেম্বার থেকে টাকা নিলেও রোগীর কাছে
            // খবর পাঠানোর বাক্স — TK-এর নিয়ম "প্রতিটা সেকশন থেকেই"।
            // ⛔ ব্যাকডেট করা টাকা যখন শুধু Master-এর অনুমোদনের জন্য পাঠানো হয়,
            // তখন কোনো বার্তা যায় না — টাকা তখনো সত্যিই জমা হয়নি, তাই রোগীকে
            // ভুল খবর দেওয়া যাবে না।
            val onlyRequested = isBackdated && user.role != "master" && !backdateOkByGrant
            if (ok && !onlyRequested) {
                // ⚠️ এই খোঁজাটা অবশ্যই ব্যাকগ্রাউন্ডে — মেইন থ্রেডে ক্লাউড ছোঁয়া
                // যাবে না (Android সঙ্গে সঙ্গে অ্যাপ বন্ধ করে দেয়)।
                val paidNow = withContext(Dispatchers.IO) {
                    try {
                        val repo = PaymentRepository(this@ChamberAttendanceActivity)
                        /* 🔵🔒 V520: রোগী-প্রতি অনন্য Official Patient ID ধরে ঠিক রোগী। */
                        val p2 = repo.findPatientByMobile(digits, row.branch, preferPatientCode = row.patientId, preferRowId = row.patientRowId)
                        if (p2 != null) p2.paid else 0.0
                    } catch (_: Throwable) { 0.0 }
                }
                if (paidNow > 0.0) {
                    PatientMessage.show(
                        this@ChamberAttendanceActivity, row.branch, row.name, digits, row.patientId,
                        PatientMessage.Kind.PAYMENT,
                        amount = value, mode = mode,
                        bill = enteredBill, paid = paidNow,
                        dateText = DateUtil.display(pickedDate)
                    )
                }
            }
        }
    }

    // TK-REQUESTED ADDITION (2026-07-25, fixes TK's live-reported "remark
    // written in one place doesn't auto-update everywhere"): shared by
    // writeTreatment/showRemarkDialog/editRemarkInReview below -- all three
    // used to independently pick the FIRST active followups row for a
    // mobile, which could each land on a DIFFERENT row than the others (or
    // than Report Card/Full Journey) when a patient has more than one
    // (Inquiry/Patient/Treatment). Now all three call this single function,
    // which always picks the row for the patient's actual CURRENT
    // (most-progressed) stage -- so a remark written from any one screen is
    // guaranteed to land on the same row every other screen reads from.
    /* 🔴🔴🔒 V439 (TK-রিপোর্ট ১৮.০৮.২০২৬ — "Treatment Progress লিখেও আবার ফাঁকা
       দেখাচ্ছে, দ্বিতীয়বার লিখতে হচ্ছে")।
       **আসল কারণ (কোড ধরে যাচাই):** এই ফাংশন আগে `SupabaseClient.findByMobile`
       (ambiguous — নেট-বিলম্ব/ব্যর্থতাতেও খালি লিস্ট ফেরায়, "সত্যিই এই রোগীর কোনো
       সারি নেই" আর "খুঁজে দেখতেই পারলাম না" — দুটো একই রকম দেখাত) ব্যবহার করত।
       তাই সামান্য নেট-বিলম্বেও `writeTreatment`/`showRemarkDialog` মনে করত এই
       রোগীর কোনো Follow-up সারিই নেই, আর **নতুন একটা ডুপ্লিকেট সারি বানিয়ে**
       (stage="Patient") সেখানেই লেখাটা সেভ করত — অথচ রোগীর আসল সারিটা তখনও
       stage="Treatment"-এ (উঁচু priority) বেঁচে ছিল। বোর্ড আবার লোড হলে উঁচু
       priority-র (আসল, কিন্তু পুরনো/ফাঁকা) সারিটাই জেতে, তাই এইমাত্র লেখা
       কথাটা লুকিয়ে যেত — আবার লিখতে হতো।
       **সমাধান:** প্রজেক্টেরই প্রমাণিত `findByMobileOrNull` (B30-এ ঠিক এই
       কারণেই বানানো — null মানে "সত্যিই খুঁজে দেখতে পারলাম না", খালি লিস্ট
       মানে "সত্যিই কোনো সারি নেই")। এখন রিটার্ন-টাইপ `String?` —
         · null = লুকআপ ব্যর্থ (নেট-সমস্যা) → ⛔ নতুন সারি বানানো হবে না
         · ""   = সত্যিই কোনো সারি নেই → নতুন সারি বানানো নিরাপদ (আগের মতোই)
         · id   = পাওয়া গেছে
       ⛔ stage-priority বাছার নিয়ম এক অক্ষরও বদলায়নি। */
    /**
     * 🟢🔒🔒 V638 (২৪.০৮.২০২৬, TK-রিপোর্ট — "KAPIL DAS ৩য় ভিজিট, তাও Treatment
     * Progress আবার ফাঁকা দেখাচ্ছে") — **আসল কারণ (কোড ধরে যাচাই):** যে রোগী
     * একাধিকবার এসেছেন (একাধিক ভিজিট), তাঁর নামে একাধিক `followups` সারি
     * একই stage-এ (যেমন "Patient") থাকতে পারে। আগে এখানে `maxByOrNull` শুধু
     * stage-priority দেখত — সমান priority-র একাধিক সারি থাকলে **যেটা
     * Supabase থেকে প্রথমে ফেরত আসে সেটাই** (কোনো নির্দিষ্ট ক্রম ছাড়া)
     * বেছে নিত। বোর্ডের নিজের রেজলিউশন (`ChamberAttendanceRepository`)
     * আলাদা query থেকে, তাই **আলাদা সারি** বেছে ফেলতে পারত — লেখা একটা
     * সারিতে সেভ হতো, বোর্ড আরেকটা সারি দেখে "ফাঁকা" বলত।
     * **সমাধান:** সমান stage হলে **সবচেয়ে সাম্প্রতিক** (updatedAt/createdAt
     * ধরে) সারিটাই জেতে — অর্থাৎ আজকের ভিজিটের সারিটাই, ঠিক TK যেটাতে লেখেন।
     * ⛔ Active/Cancelled/Rejected ছাঁকনি এক অক্ষরও বদলায়নি।
     */
    private suspend fun resolveBestFollowUpId(digits: String): String? = withContext(Dispatchers.IO) {
        try {
            fun stagePriority(s: String): Int = when {
                s.equals("Treatment", true) || s.equals("Treatment Running", true) -> 3
                s.equals("Patient", true) -> 2
                s.equals("Inquiry", true) -> 1
                else -> 0
            }
            val rows = SupabaseClient.findByMobileOrNull("followups", "+91$digits", "id,status,stage,updatedAt,createdAt", 50) ?: return@withContext null
            val active = (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .filter {
                    val st = it.optString("status", "Active")
                    !st.equals("Cancelled", true) && !st.equals("Incomplete", true) &&
                        !st.equals("Rejected", true) && !st.equals("Closed", true)
                }
                .maxWithOrNull(
                    compareBy<org.json.JSONObject> { stagePriority(it.optString("stage", "")) }
                        .thenBy { it.optString("updatedAt").ifBlank { it.optString("createdAt") } }
                )
            when {
                active != null -> active.optString("id", "")
                rows.length() > 0 -> terminalFollowUpSentinel
                else -> ""
            }
        } catch (_: Throwable) { null }
    }

    /**
     * রোগীর Follow-up সারির আইডি বের করে; না পেলে সারিটা তৈরি করে দেয়।
     * (আগে এই কাজটা `writeTreatment`-এর ভিতরেই ছিল — এক অক্ষরও বদলানো হয়নি,
     * শুধু আলাদা করে সরানো হয়েছে যাতে বক্স খোলার পরে পিছনে চালানো যায়।)
     */
    private suspend fun resolveOrHealFollowUpId(row: ChamberAttendanceRow, digits: String): String {
        val resolvedId = resolveBestFollowUpId(digits)
        if (resolvedId == terminalFollowUpSentinel) return terminalFollowUpSentinel
        if (!resolvedId.isNullOrBlank()) return resolvedId
        // 🔴 V439 — লুকআপ নিজেই ব্যর্থ হলে (null — নেট-সমস্যা) ডুপ্লিকেট সারি
        // বানানো হবে না (এটাই আসল রিমার্ক-হারানোর কারণ ছিল); খালি হাতে ফেরত,
        // caller "চেক করুন সংযোগ" বার্তা দেখাবে — এমনি Save চাপলে আবার চেষ্টা হয়।
        if (resolvedId == null) return ""
        // TK-REPORTED BUG FIX (2026-07-24): a fresh Registration whose
        // Visit-stage followups row hasn't reached the cloud yet used to
        // block the Treatment box for good. Same self-heal as before —
        // only reached now when the lookup genuinely found zero rows.
        val newId = "fu_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val healRow = org.json.JSONObject()
            .put("id", newId)
            .put("mobile", row.mobile)
            .put("name", row.name)
            .put("branch", row.branch)
            .put("disease", row.disease)
            .put("patientId", row.patientId)
            .put("stage", "Patient")
            .put("date", FollowUpModel.today())
            .put("registrationDate", FollowUpModel.today())
            .put("visitDate", FollowUpModel.today())
            .put("lastRemark", "Registered patient / Visit created")
            .put("nextFollow", "")
            .put("callCount", 0)
            .put("status", "Active")
            .put("history", org.json.JSONArray())
            .put("createdBy", user.mobile)
            .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
            .put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
        val created = withContext(Dispatchers.IO) {
            try { SupabaseClient.upsert("followups", healRow) } catch (_: Throwable) { false }
        }
        return if (created) newId else ""
    }

    /**
     * ⚡ TK-এর নির্দেশ (28.07.2026 ৩.০০ pm, ফটো-প্রুফসহ): **"Treatment Progress ·
     * Cash · Online — এই ঘরগুলোতে চাপ দিলে সাথে সাথে বক্স ওপেন হয় না।"**
     *
     * **আসল কারণ:** রোগীর Follow-up সারির আইডি হাতে না থাকলে বক্সটা খোলার
     * **আগে** ক্লাউডে খোঁজা হত (দরকারে নতুন সারি তৈরিও)। TK-এর ধীর লাইনে ওই
     * অপেক্ষাটাই কয়েক সেকেন্ড ধরে চলত, তাই মনে হত চাপ দিয়ে কিছুই হচ্ছে না।
     *
     * **এখন:** বক্স **সঙ্গে সঙ্গে** খোলে, আর আইডি খোঁজার কাজটা পিছনে চলতে থাকে।
     * স্টাফ যতক্ষণে লিখছেন ততক্ষণে আইডি চলে আসে। **Save চাপার সময় যদি তখনো না
     * এসে থাকে, তখনই একবার অপেক্ষা করা হয়** — অর্থাৎ ভুল জায়গায় কিছু লেখা
     * হওয়ার আশঙ্কা নেই, শুধু অপেক্ষাটা সামনে থেকে সরে গেছে।
     *
     * ⛔ কোনো নতুন ক্লাউড-কল যোগ হয়নি — ঠিক সেই একটাই কাজ, শুধু পরে।
     */
    // 🆕 TK-নির্দেশ (04.08.2026): "Registered patient / Visit created" —
    // Registration/Visit তৈরির সময় সিস্টেম নিজে থেকে বসানো ডিফল্ট স্টাব,
    // স্টাফ নিজে কিছু লেখেননি — তাই Chamber-এর যেকোনো "লেখা হয়েছে কিনা"
    // যাচাইয়ে এটাকে ফাঁকা রিমার্কের মতোই ধরতে হবে। একটাই ফাংশন, তিন জায়গায়
    // (Treatment Progress কলামের রং/লেখা, Close Chamber-এর "বাকি আছে"
    // সতর্কতা, Report Card-এর ছাপা) পুনর্ব্যবহার — যাতে তিনটে জায়গায় তিন
    // রকম না হয়ে যায়।
    private fun isEffectivelyBlankRemark(remark: String): Boolean =
        remark.trim().isBlank() || remark.trim().equals("Registered patient / Visit created", ignoreCase = true)

    /* 🔴🔴🔒 V810 (২৮.০৮.২০২৬, TK-অনুমোদিত) — **"স্টাফ কিছু লেখেনি, তাহলে চেম্বার
       বন্ধ সেভ হলো কি করে?"** (TK-এর কাগজে TREATMENT PROGRESS-এ "ASBEN")।
       ─── আসল কারণ (কোড ধরে প্রমাণিত) ───────────────────────────────────────
       বোর্ডের `remark` ঘরে রোগীর **সবচেয়ে সাম্প্রতিক** লেখাটা বসে — সেটা আজকের
       হোক বা বহু দিন আগের (`ChamberAttendanceRepository`-তে `followups.lastRemark`)।
       V654-এ **দেখানোর** দিকটা ঠিক করা হয়েছিল (পুরনো লেখা ধূসর, আজকেরটা গাঢ়) —
       কিন্তু নিচের দুটো জায়গা তারিখটা **মেলাতোই না**, শুধু "ঘর ফাঁকা কিনা" দেখত:
         ১) চেম্বার-বন্ধের পাহারা  ২) ছাপা রেজিস্টারের TREATMENT PROGRESS ঘর
       ⇒ পুরনো একটা লেখা ঘরে বসে থাকায় পাহারা "লেখা আছে" ধরে **বন্ধ করতে দিত**,
         আর কাগজে পুরনো লেখাটাই **আজকের চিকিৎসা-নোট** হিসেবে ছাপা হত।
       ─── সারানো ────────────────────────────────────────────────────────────
       এখন ওই দুটো জায়গায় তারিখও মেলানো হয়। V687-এর লেখা নির্দেশটাই
       ("আজকের লেখা যতক্ষণ না লেখা হবে, ততক্ষণ চেম্বার বন্ধ করা যাবে না")
       এবার সত্যিই কার্যকর হলো।
       ⛔ **পুরনো দিনের বোর্ড/কাগজ অক্ষত** — তারিখ মেলানো হয় **শুধু আজকের**
          বোর্ডে (নিচের শর্তটা দেখুন)। নইলে V535-এ ফিরিয়ে আনা পুরনো দিনের
          লেখাগুলো "PENDING" হয়ে যেত — একটা ভালো কাজ নষ্ট হত।
       ⛔ পর্দায় দেখানোর রং/লেখা (V654) এক অক্ষরও বদলায়নি। */
    private fun todaysProgressMissing(remark: String, remarkUpdatedAt: String): Boolean {
        if (isEffectivelyBlankRemark(remark)) return true
        if (selectedDate != FollowUpModel.today()) return false   // পুরনো দিন — আগের মতোই
        return remarkUpdatedAt.take(10) != selectedDate
    }

    private fun writeTreatment(row: ChamberAttendanceRow) {
        val digitsForId = row.mobile.filter { it.isDigit() }.takeLast(10)
        if (row.followUpId.isBlank() && digitsForId.length != 10) {
            android.widget.Toast.makeText(this, "No Follow-up record yet for this patient", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // পিছনে চলা খোঁজা — বক্স খোলা এটার জন্য থামে না
        var liveFollowUpId = row.followUpId
        val idJob: kotlinx.coroutines.Deferred<String>? =
            if (liveFollowUpId.isNotBlank()) null
            else lifecycleScope.async(Dispatchers.IO) {
                try { resolveOrHealFollowUpId(row, digitsForId) } catch (_: Throwable) { "" }
            }
        val d = resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
        val input = android.widget.EditText(this).apply {
            setText(row.remark); hint = NoBengali.s("আজ কী হলো — নিজে লিখুন বা নিচের চিপ চাপুন")
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val p = dp(12); setPadding(p, p, p, p); minLines = 2; gravity = android.view.Gravity.TOP
            // 🔒 B545/B546 (08.08.2026, TK-নির্দেশ, স্ক্রিনশট-প্রুফ) — এই বক্সের
            // ভিতরের নির্দেশ-লেখাটা (hint) আগে কোনো রং সেট করা ছিল না, তাই
            // কিছু ফোনে গাঢ় দেখাত ও মনে হতো কেউ লিখেছে (TK বিভ্রান্ত হয়েছেন)।
            // এখন হালকা জলছাপের মতো — পুরো অ্যাপের একই সোর্স (@color/field_hint,
            // থিমেও সেট) থেকে, তাই সব বক্সে এক রং। ⛔ স্টাফ নিজে লিখলে সেই আসল
            // লেখা আগের মতোই গাঢ়; শুধু নির্দেশ-লেখার রং।
            setHintTextColor(androidx.core.content.ContextCompat.getColor(this@ChamberAttendanceActivity, com.tkbiswas.pilesclinic.R.color.field_hint))
        }
        /* 🟢🔒 V588 (23.08.2026, TK-নির্দেশ) — ৯টা সাজেশন-চিপের তালিকা ও চিপ
           বানানোর কাজটা এখন একটাই জায়গায় (`TreatmentQuickNotes`), যাতে
           চেম্বার-বোর্ড · চেম্বার বন্ধ · Review · Report Card — চারটে বাক্সেই
           হুবহু একই সাজেশন আসে। ⛔ লেখা ও আচরণ এক অক্ষরও বদলায়নি। */
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), 0)
            addView(input)
        }
        TreatmentQuickNotes.attach(this, container, input)
        UppercaseInputUtil.applyToAll(container)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "🩺 Treatment Progress — ${row.name.ifBlank { row.mobile }}"))
            .setView(android.widget.ScrollView(this).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                // 🔒 খাতার সারি B54 (TK, 28.07.2026 রাত): ফাঁকা লেখা দিয়ে Save
                // করলে আগের লেখাটা যেন কোনোভাবেই মুছে না যায়।
                if (text.isBlank()) {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Nothing written — the earlier note is kept", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    // ⚡ বক্সটা আগেই খুলে গেছে; আইডি পিছনে খোঁজা হচ্ছিল।
                    // এখানে শুধু সেটার জন্য অপেক্ষা — সাধারণত ততক্ষণে এসেই গেছে।
                    if (liveFollowUpId.isBlank()) liveFollowUpId = idJob?.await().orEmpty()
                    if (liveFollowUpId == terminalFollowUpSentinel) {
                        android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Entry is Rejected/Incomplete — Restore first", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (liveFollowUpId.isBlank()) {
                        android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Could not create Follow-up record — check connection and try again", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val savedId = liveFollowUpId
                    val ok = withContext(Dispatchers.IO) {
                        FollowUpRepository(this@ChamberAttendanceActivity).updateRemark(savedId, text, user.name.ifBlank { user.mobile })
                    }
                    /* 🟢🔒 V590 (২৩.০৮.২০২৬, TK-নির্দেশ) — Report Card-এ লেখাটা
                       পাঠানোর কাজটা এখন একটাই জায়গায় (`syncProgressToReportCard`),
                       কারণ **চারটে বাক্সের মধ্যে তিনটে এটা করতই না**। বিস্তারিত
                       ওই ফাংশনের মাথায়। */
                    syncProgressToReportCard(row, text, selectedDate)
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, if (ok) "Treatment saved ✅" else "Failed — retry", android.widget.Toast.LENGTH_SHORT).show()
                    if (ok) loadBoard()
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }


    /**
     * 🟢🔒 V590 (২৩.০৮.২০২৬, TK-রিপোর্ট: *"ট্রিটমেন্ট প্রোগ্রেসে যা লেখা হয়
     * রিপোর্ট কার্ডে অটোমেটিক উঠে না কেন"*)।
     *
     * **আসল কারণ (কোড থেকে প্রমাণিত, আন্দাজে নয়):** চিকিৎসার কথা লেখার বাক্স
     * অ্যাপে চারটে জায়গায় খোলে, কিন্তু Report Card-এ লেখাটা পাঠানোর কাজটা
     * **শুধু একটাতেই** ছিল —
     *   1. চেম্বার বোর্ডের ঘরে চাপ (`writeTreatment`)             → পাঠাত
     *   2. চেম্বার বন্ধের "ফাঁকা আছে" পপ-আপ (`showRemarkDialog`)   → পাঠাত না
     *   3. Review পর্দার তিন-চাপ (`editRemarkInReview`)            → পাঠাত না
     *   4. কম্পিউটারের বাক্স (`wlv1ChamberSaveTreatment`)           → পাঠাত না
     * Report Card পড়ে `payments.progress` ঘর থেকে; বাকি তিনটে শুধু ফলো-আপ
     * খাতায় লিখত। তাই বোর্ডে লেখা দেখা যেত, অথচ Report Card-এ "—"।
     *
     * এখন কাজটা **একটাই জায়গায়**, আর চারটে বাক্সই এটা ডাকে।
     *
     * ⛔ ভিতরের নিয়ম এক অক্ষরও বদলায়নি (V533-এর `progress` ঘর · V536-এর
     *    "শুধু এই রোগীরই সারি" মিল · ব্যর্থ হলে retry-queue) — শুধু তারিখটা
     *    এখন বোর্ডের খোলা দিন, আর ডাকার জায়গা চারটে।
     * ⛔ পিছনে চলে — Treatment সেভ এটার জন্য এক মুহূর্তও থামে না।
     */
    private fun syncProgressToReportCard(row: ChamberAttendanceRow, text: String, dateKey: String) {
        val appCtx = applicationContext
Thread {
            try {
                val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
                /* 🟢🔒 V590 — আগে সবসময় **আজকের** তারিখ ধরা হত। কিন্তু পুরোনো কোনো
                   দিনের চেম্বার খুলে লেখা হলে নোটটা ভুল দিনের টাকার সারিতে বসত (বা
                   কোথাও বসত না)। এখন **যে দিনের বোর্ড খোলা আছে সেই দিনটাই** ধরা হয়।
                   ⛔ আজকের দিন হলে (রোজকার ক্ষেত্রে) হুবহু আগের আচরণ। */
                val dayKey = dateKey.ifBlank { FollowUpModel.today() }
                val todaysPayments = SupabaseClient.fetchList("payments", "mobile=like.*$digits&date=eq.$dayKey", 20)
                val targets = ArrayList<String>()
                /* 🔵🔒 V536 (২২.০৮.২০২৬, TK-নির্দেশ) — **নোটটা শুধু এই রোগীরই
                   টাকার সারিতে বসবে।**

                   আগে ওই নম্বরের **আজকের সব** পেমেন্ট সারিতে নোটটা বসত। এক
                   নম্বরে স্বামী-স্ত্রী দু'জন আলাদা রোগী দু'জনেই আজ টাকা দিলে
                   **একজনের চিকিৎসার নোট অন্যজনের Report Card-এ** উঠে যেত।

                   ⛔ নিয়মটা কোড থেকে প্রমাণিত, আন্দাজে নয়:
                      `payments.patientId` = **রোগীর সারির আইডি**
                      (`ChamberAttendanceRepository.kt:897`-এ লেখা ও V520-এ প্রমাণিত)।
                   ⛔ V534-এর `rowBelongsTo()` এখানে **খাটে না** — ওটা সারির নিজের
                      `id` ধরে মেলায়, আর পেমেন্ট সারির `id` হলো পেমেন্টের আইডি,
                      রোগীর নয়। তাই এখানে সঠিক মিলটাই আলাদা করে লেখা হলো।
                   ⛔ **এই নম্বরে ঘোষিত আলাদা রোগী না থাকলে (রোজকার ৯৯%)
                      একটাও সারি বাদ পড়ে না — আচরণ হুবহু আগের মতোই।** */
                val mineRowId = row.patientRowId.trim()
                for (i in 0 until todaysPayments.length()) {
                    val p = todaysPayments.getJSONObject(i)
                    val payType = p.optString("payType", "")
                    if (payType == "bill_edit" || payType == "chamber_expected") continue
                    val owner = p.optString("patientId", "").trim()
                    val keep = if (mineRowId.isNotEmpty())
                        owner == mineRowId          // ঘোষিত আলাদা রোগী ⇒ ঠিক তাঁরটাই
                    else
                        // সাধারণ রোগী ⇒ ঘোষিত-আলাদা কারও টাকা বাদ, বাকি সব আগের মতোই
                        !com.tkbiswas.pilesclinic.native.PatientModel
                            .isDeclaredSeparateRowId(owner, digits)
                    if (!keep) continue
                    val pid = p.optString("id")
                    if (pid.isBlank()) continue
                    targets.add(pid)
                }
                // Same rows, sent together instead of one-by-one.
                ParallelCloud.map(targets) { pid ->
                    /* 🔵🔒 V533 (২২.০৮.২০২৬, TK-সিদ্ধান্ত) — **স্টাফের লেখা Remark আর মুছবে না।**
                       আগে এই লেখাটা payments-এর `remarks` ঘরে বসত। ফল: সেদিন কেউ পেমেন্ট ফর্মে
                       নিজের হাতে যে Remark লিখেছেন — এমনকি **Refund-এর কারণ** — সব মুছে গিয়ে
                       চিকিৎসার নোট বসে যেত (TK-এর ছবিতে ₹1,000 Advance-এ "DRESSING করা হল")।
                       ⇒ এখন নিজের আলাদা ঘর `progress`। ⛔ `remarks` আর ছোঁয়াই হয় না। */
                    val fields = org.json.JSONObject().put("progress", text)
                    val synced = try { SupabaseClient.updateById("payments", pid, fields) } catch (_: Throwable) { false }
                    if (!synced) GenericUpdateQueue.queue(appCtx, "payments", pid, fields)
                    synced
                }
            } catch (_: Throwable) { /* Report Card sync is a best-effort extra -- never blocks Treatment save above */ }
        }.start()
    }

    private fun showRemarkDialog(row: ChamberAttendanceRow) {
        // TK-REPORTED (2026-07-22): the "Treatment box is empty" warning was
        // firing correctly, but tapping through led nowhere for a patient
        // whose board-computed followUpId came back blank (e.g. a same-day
        // fresh Registration) -- it just showed "No Follow-up record yet"
        // and stopped, instead of actually taking TK to that patient's box.
        // Fix: if the board's cached followUpId is blank, do ONE live lookup
        // by mobile right here before giving up -- every Registered/Enquiry
        // patient already has a followups row (created at that time), so
        // this resolves it in the vast majority of cases. Only if a live
        // lookup ALSO finds nothing do we show the "no record" message.
        if (row.followUpId.isBlank()) {
            val digits = row.mobile.filter { it.isDigit() }.takeLast(10)
            if (digits.length != 10) {
                android.widget.Toast.makeText(this, "No Follow-up record yet for this patient", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            lifecycleScope.launch {
                val resolvedId = resolveBestFollowUpId(digits)
                if (resolvedId == terminalFollowUpSentinel) {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Entry is Rejected/Incomplete — Restore first", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (!resolvedId.isNullOrBlank()) {
                    showRemarkDialog(row.copy(followUpId = resolvedId))
                    return@launch
                }
                // 🔴 V439 — null মানে লুকআপ ব্যর্থ (নেট-সমস্যা); ডুপ্লিকেট সারি
                // বানানো হবে না, নাহলে এইমাত্র লেখা রিমার্ক আসল সারির আড়ালে
                // চাপা পড়ে যায় (TK-রিপোর্ট ১৮.০৮.২০২৬)।
                if (resolvedId == null) {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Could not check — please check connection and try again", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // TK-REPORTED BUG FIX (2026-07-24): same dead-end as
                // writeTreatment() above -- create the missing followups
                // row right here instead of blocking with just a Toast.
                // (এখন এই পথে আসে শুধু তখনই যখন লুকআপ সত্যিই ০ সারি পেয়েছে।)
                val newId = "fu_" + java.util.UUID.randomUUID().toString().replace("-", "")
                val healRow = org.json.JSONObject()
                    .put("id", newId)
                    .put("mobile", row.mobile)
                    .put("name", row.name)
                    .put("branch", row.branch)
                    .put("disease", row.disease)
                    .put("patientId", row.patientId)
                    .put("stage", "Patient")
                    .put("date", FollowUpModel.today())
                    .put("registrationDate", FollowUpModel.today())
                    .put("visitDate", FollowUpModel.today())
                    .put("lastRemark", "Registered patient / Visit created")
                    .put("nextFollow", "")
                    .put("callCount", 0)
                    .put("status", "Active")
                    .put("history", org.json.JSONArray())
                    .put("createdBy", user.mobile)
                    .put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
                    .put("updatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date()))
                val created = withContext(Dispatchers.IO) {
                    try { SupabaseClient.upsert("followups", healRow) } catch (_: Throwable) { false }
                }
                if (created) {
                    showRemarkDialog(row.copy(followUpId = newId))
                } else {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Could not create Follow-up record — check connection and try again", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val input = android.widget.EditText(this).apply {
            setText(row.remark)
            // 🟢🔒 V588: চারটে বাক্সেই এক নির্দেশ-লাইন (একটাই উৎস)।
            hint = TreatmentQuickNotes.hint()
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val p = dp(12); setPadding(p, p, p, p); minLines = 2; gravity = android.view.Gravity.TOP
            setHintTextColor(androidx.core.content.ContextCompat.getColor(this@ChamberAttendanceActivity, com.tkbiswas.pilesclinic.R.color.field_hint))
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(0))
            addView(input)
        }
        /* 🟢🔒 V588 (23.08.2026, TK-নির্দেশ, ছবিসহ) — চেম্বার বন্ধ করার সময়
           "Treatment box ফাঁকা" বলে যে বাক্সটা খোলে সেটাতে **সাজেশন ছিল না**,
           শুধু ফাঁকা ঘর। এখন চেম্বার-বোর্ডের সেই একই ৯টা চিপ এখানেও। */
        TreatmentQuickNotes.attach(this, container, input)
        UppercaseInputUtil.applyToAll(container)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, row.name.ifBlank { row.mobile }))
            .setView(android.widget.ScrollView(this).apply { addView(container) })
            .setPositiveButton("Save") { _, _ ->
                val remark = input.text.toString().trim()
                if (remark.isBlank()) return@setPositiveButton
                // 🔒🔒 খাতার সারি B197 (TK, 30.07.2026 রাত — Chamber Attendance
                // লোডিং ফিক্সের ধাপ ২: Remark, TK-এর নির্দেশ "তাড়াতাড়িও হবে
                // সঠিক কাজও হবে"):
                //
                // **কেন Mark Arrived-এর মতো সহজে করা গেল না:** Remark সেভ
                // করতে `FollowUpRepository.updateRemark()`-এর ভিতরে তিনটে
                // ধারাবাহিক ক্লাউড-কল লাগে (আগের ইতিহাস পড়া → নতুন লেখা বসানো →
                // **আবার পড়ে সত্যিই বসেছে কিনা যাচাই**)। ওই যাচাই-ধাপটা
                // ২৮.০৭.২০২৬-এ TK-এর রিপোর্ট করা একটা আসল বাগ ("সেভ হয়েছে
                // দেখাল, পুরনো লেখাই থেকে গেল") ঠিক করতে বসানো হয়েছিল — তাই
                // ওই ফাংশনের **ভিতরের একটা অক্ষরও সরানো/দ্রুত করা হয়নি**,
                // পুরো verify+retry অক্ষত রইল।
                //
                // **তাহলে দ্রুতও হলো কীভাবে:** এই বোর্ড (`lastBoard`) স্ক্রিনেই
                // মেমোরিতে রাখা আছে — স্টাফ এইমাত্র যা টাইপ করলেন সেটাই সরাসরি
                // এই বোর্ডের সেই রোগীর সারিতে বসিয়ে **তখনই** পর্দায় দেখানো হয়
                // (কোনো নেটওয়ার্ক-কল ছাড়াই, তাই কোনো "loading" নেই) — এটা কোনো
                // আন্দাজ নয়, স্টাফ নিজে যা লিখেছেন ঠিক তাই। আসল সেভ (উপরের পুরো
                // ফাংশন, তার ইতিহাস-মেলানো ও যাচাই সহ) এখন পিছনে চলে —
                // ব্যর্থ হলে আগের মতোই নিজে থেকে আবার চেষ্টা হবে (queue অক্ষত)।
                val board0 = lastBoard
                if (board0 != null) {
                    val updatedRows = board0.rows.map { if (it.mobile == row.mobile) it.copy(remark = remark) else it }
                    lastBoard = board0.copy(rows = updatedRows)
                    adapter.update(filteredRows(updatedRows), quickActions = isToday(), callAhead = isFuture())
                }
                closeTapCount = 0
                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Treatment/Remark updated", android.widget.Toast.LENGTH_SHORT).show()
                val appCtx = this@ChamberAttendanceActivity.applicationContext
                val staffName = user.name.ifBlank { user.mobile }
                BackgroundWork.run {
                    try { FollowUpRepository(appCtx).updateRemark(row.followUpId, remark, staffName) } catch (_: Throwable) { }
                }
                // 🟢 V590 — এই বাক্সটা এতদিন Report Card-এ কিছু পাঠাত না।
                syncProgressToReportCard(row, remark, selectedDate)
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    /** TK APPROVED (2026-07-16): "Close Chamber (Save & Print Arrived)".
     *  1st/2nd tap: if any Arrived patient has no Treatment/Remark yet,
     *  warn and open that patient's Treatment box instead of closing.
     *  3rd tap: proceed regardless of empty Treatment boxes (TK's explicit
     *  instruction -- a busy day must not be blocked forever). */
    // 🆕 B419 (04.08.2026, TK-নির্দেশ) — যেকোনো স্টাফ চাইলে বন্ধ হয়ে যাওয়া
    // দিন আবার খোলার অনুরোধ পাঠাতে পারবেন — কিন্তু Master অনুমোদন না দিলে
    // কিছুই খোলে না। ⛔ এই ফাংশন নিজে **কিছুই খোলে না**, শুধু অনুরোধ পাঠায়।
    /** 🔴 V425 — Master নিজেই খোলেন, কোনো অনুরোধ/অনুমোদন লাগে না।
     *  ⛔ ঠিক সেই একই `ChamberCloseRepository.reopen(...)` ডাকা হয় যেটা
     *     Master ঘন্টা থেকে Approve করলে চলত — নতুন কোনো পথ বানানো হয়নি। */
    private fun confirmMasterReopen() {
        val br = if (selectedBranch != "All") selectedBranch else user.branch
        val dateDisplay = FollowUpModel.displayDate(selectedDate)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Reopen Chamber"))
            .setMessage(NoBengali.s("Reopen the chamber for this day ($dateDisplay, $br)?")   /* 🔤 V728 */)
            .setPositiveButton(NoBengali.s("Yes, reopen")) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try { ChamberCloseRepository.reopen(this@ChamberAttendanceActivity, br, selectedDate) }
                        catch (_: Throwable) { false }
                    }
                    android.widget.Toast.makeText(
                        this@ChamberAttendanceActivity,
                        NoBengali.s(
                            if (ok) "চেম্বার আবার খোলা হলো"
                            else "খোলা গেল না — নেট চেক করে আবার চেষ্টা করুন"
                        ),
                        android.widget.Toast.LENGTH_LONG
                    ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                    // দিনটা এখন আর "বন্ধ" নয় — পর্দা নতুন করে পড়ে নেয়
                    // (ব্রাঞ্চ বদলালে যা হয়, ঠিক সেই একই দুটো লাইন)।
                    if (ok) try { dateClosedFlag = false; loadBoard() } catch (_: Throwable) { }
                }
            }
            .setNegativeButton(NoBengali.s("No"), null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun confirmRequestReopen() {
        val u = user
        val br = if (selectedBranch != "All") selectedBranch else u.branch
        val dateDisplay = FollowUpModel.displayDate(selectedDate)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Request Reopen"))
            .setMessage(NoBengali.s("Send a request to the Master to reopen this day ($dateDisplay, $br)?")   /* 🔤 V728 */)
            .setPositiveButton(NoBengali.s("Yes, Send")) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try { ChamberReopenPermission.sendRequest(this@ChamberAttendanceActivity, u, br, selectedDate) }
                        catch (_: Throwable) { false }
                    }
                    android.widget.Toast.makeText(
                        this@ChamberAttendanceActivity,
                        NoBengali.s(
                            if (ok) "অনুরোধ পাঠানো হয়েছে — Master অনুমোদন দিলে দিনটা আবার খুলবে"
                            else "পাঠানো গেল না — নেট চেক করে আবার চেষ্টা করুন"
                        ),
                        android.widget.Toast.LENGTH_LONG
                    ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                }
            }
            .setNegativeButton(NoBengali.s("No"), null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun attemptCloseChamber() {
        val board = lastBoard
        if (board == null) {
            android.widget.Toast.makeText(this, "Board still loading — try again in a moment", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // 🔴🔒 V687 (২৫.০৮.২০২৬, TK-নির্দেশ, স্পষ্ট — "আজকের লেখা যতক্ষণ না
        // লেখা হবে, ততক্ষণ চেম্বার বন্ধ করা যাবে না") — আগে ৩ বার চাপলে
        // ফাঁকা/পুরনো Treatment Progress থাকা সত্ত্বেও বন্ধ করার একটা
        // ছাড় (bypass) ছিল। TK-এর স্পষ্ট নির্দেশে সেই ছাড় পুরোপুরি তুলে
        // নেওয়া হলো — এখন প্রতিটা Arrived রোগীর আজকের (V687-এ ঠিক করা
        // payments.progress-ভিত্তিক) Treatment Progress লেখা **বাধ্যতামূলক**,
        // কোনো bypass নেই। ফাঁকা থাকলেই বক্স খুলে যায়, close হয় না।
        // 🔴 V810 — এখন তারিখও মেলানো হয় (পুরনো লেখা আর পাহারা ফাঁকি দিতে পারবে না)
        val missing = board.rows.firstOrNull { it.arrived && todaysProgressMissing(it.remark, it.remarkUpdatedAt) }
        if (missing != null) {
            android.widget.Toast.makeText(
                this,
                "⚠️ ${missing.name.ifBlank { missing.mobile }} — " + NoBengali.s("আজকের Treatment Progress লেখা হয়নি — না লিখলে চেম্বার বন্ধ করা যাবে না"),
                android.widget.Toast.LENGTH_LONG
            ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
            showRemarkDialog(missing)
            return
        }
        closeTapCount = 0
        // 🔵 B607 (10.08.2026, TK-অনুমোদিত): কেউ না এলে (Arrived 0) ভুলে ফাঁকা
        // চেম্বার বন্ধ/প্রিন্ট আটকাতে একটা নিশ্চিতকরণ — "Yes, Close" দিলে
        // আগের মতোই বন্ধ হয় (রোগীশূন্য দিনও বন্ধ করা যায়), "No" দিলে থামে।
        // ⛔ Arrived>0 হলে এই পপ-আপ আসে না — স্বাভাবিক ফ্লো একটুও বদলায়নি।
        if (board.rows.none { it.arrived }) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Nobody Arrived"))
                .setMessage(NoBengali.s("Nobody arrived today (Arrived 0). Still close the chamber?"))
                .setPositiveButton(NoBengali.s("Yes, Close")) { _, _ -> askPrintBranchThenReview(board) }
                .setNegativeButton(NoBengali.s("No"), null)
                .show().also { PremiumAlert.paint(it) }
            return
        }
        // TK-REQUESTED (2026-07-20): Save no longer prints straight away --
        // it opens a Review first. The person checks every Arrived patient,
        // triple-taps to fix any wrong Payment / Treatment / Remark (which
        // writes back to the source so ALL sections update), and only then
        // taps "Confirm & Print".
        askPrintBranchThenReview(board)
    }

    // TK-APPROVED (2026-07-26, photo proof): the printed register used to mix
    // branches. On "All Branch" (Master only) the rows came from every branch
    // while the printed header carried just ONE clinic name/address, so the
    // paper matched no branch at all and the TOTAL mixed several branches'
    // money. Now, when more than one branch has arrived patients and the
    // filter is on "All Branch", the person first picks WHICH branch's
    // register is being printed; the review and the PDF then contain only
    // that branch, with that branch's own header. Staff never see this popup
    // (they are always on their own branch) and their flow is unchanged.
    private var printBranchOverride: String = ""

    /**
     * TK-CHECKED (2026-07-26): a row's real branch for printing. The branch
     * column can be blank or wrong on old records, so the Patient ID's own
     * branch code (COB / JPE / KNE / FLK / BIR) is used as the second source .
     * exactly the same two-source rule FollowUpRepository.branchAllows() uses,
     * so nobody is filtered out of their own branch's register.
     */
    private fun effectiveBranchOf(r: ChamberAttendanceRow): String {
        val b = r.branch.trim().uppercase()
        if (b.isNotBlank()) return b
        val id = r.patientId.trim().uppercase()
        val code = id.substringBefore("-", "")
        val name = when (code) {
            "COB" -> "COOCH BEHAR"
            "JPE" -> "JALPAIGURI"
            "KNE" -> "KISHANGANJ"
            "FLK" -> "FALAKATA"
            "BIR" -> "BIRPARA"
            else -> ""
        }
        return name
    }

    private fun askPrintBranchThenReview(board: ChamberAttendanceBoard) {
        printBranchOverride = ""
        val arrived = board.rows.filter { it.arrived }
        val branches = arrived.map { effectiveBranchOf(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (selectedBranch != "All" || branches.size <= 1) {
            // Corner case worth being careful about: on "All Branch" with only
            // ONE branch actually present today, no popup is needed . but the
            // header must still be THAT branch's clinic, not the logged in
            // Master's own branch (which is what it used to fall back to).
            if (selectedBranch == "All" && branches.size == 1) printBranchOverride = branches[0]
            showCloseReview(board)
            return
        }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val parts = premiumDialogShellChamber("কোন ব্রাঞ্চের চেম্বার বন্ধ করবেন?")
        parts.body.addView(android.widget.TextView(this).apply {
            text = NoBengali.s("আপনি এখন All Branch-এ আছেন। যে ব্রাঞ্চটি বন্ধ করবেন সেটি বেছে নিন — Review-তে শুধু ওই ব্রাঞ্চের রোগীরা থাকবেন।")
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, 0, 0, dp(10))
        })
        for (b in branches) {
            val count = arrived.count { it.branch.trim().uppercase() == b }
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 12f * d
                    setStroke(dp(1), android.graphics.Color.parseColor("#CBDCDC"))
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(9)
                layoutParams = lp
            }
            row.addView(android.widget.TextView(this).apply {
                text = b
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#10223A"))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(android.widget.TextView(this).apply {
                text = NoBengali.s("$count জন")
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#0F5C5C"))
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#E4F2F1"))
                    cornerRadius = 20f * d
                }
            })
            row.setOnClickListener {
                parts.dialog.dismiss()
                printBranchOverride = b
                // A row whose branch is blank AND whose Patient ID gives no
                // branch either (a walk in with no registration) must never
                // silently disappear from the printed register . it is kept
                // with whichever branch is being printed.
                val onlyThisBranch = board.rows.filter {
                    !it.arrived || effectiveBranchOf(it) == b || effectiveBranchOf(it).isBlank()
                }
                showCloseReview(ChamberAttendanceBoard(onlyThisBranch, board.totals))
            }
            parts.body.addView(row)
        }
        parts.actionRow.addView(pillButtonChamber("Cancel", "#7A8794").apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.dialog.show()
        try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(parts.dialog) } catch (_: Throwable) { }   // 🤫 V774
    }

    // ============================================================
    // TK-REQUESTED (2026-07-20): "Review before Print" for Close Chamber.
    // Save opens this list of Arrived patients; triple-tap a card to fix a
    // wrong Payment / Treatment / Remark. Every fix writes to the SOURCE
    // record (payments row by id with an audit note, or the follow-up
    // remark), so every other section updates automatically. "Confirm &
    // Print" then produces the same register PDF as before. No existing
    // working flow is changed -- only a review step is inserted before the
    // already-existing finalizeAndShare().
    // ============================================================
    /** 🔴 V426: Review পর্দার নামের ঘরগুলো (মোবাইলের শেষ ১০ অঙ্ক ধরে) — কমিশনের
     *  তথ্য আসার পরে ওই সারিতে ছোট্ট "RMP" চিহ্ন বসানোর জন্য। */
    private val cbReviewNameCells = HashMap<String, android.widget.TextView>()

    /** 🔴 V426: Review পর্দায় দেখানো আজকের মোট RMP কমিশন — ছাপা কাগজেও এই একই
     *  সংখ্যাটাই যায়। কমিশন না এলে ০, তখন কাগজ আগের মতোই। */
    private var cbDayCommissionTotal: Double = 0.0

    /** 🔴 V427: আজ RMP-দের হাতে সত্যিই দেওয়া মোট টাকা — শুধু দেখানোর জন্য,
     *  কোনো মোট থেকে বাদ যায় না। */
    private var cbDayPaidTotal: Double = 0.0

    /** 🔴🔒 V685 (২৫.০৮.২০২৬, TK-নির্দেশ — "প্রিন্ট আউটে RMP কমিশন লাল রঙে,
     *  কোন RMP-র কত কমিশন সহ") — প্রতিটা RMP-র নাম + সেদিনের মোট কমিশন
     *  (একাধিক রোগী থাকলে যোগ করে), ছাপার জন্য। ⛔ Review পর্দার
     *  `cbDayCommissionTotal` (সবার যোগফল) অপরিবর্তিত — এটা তারই বিস্তারিত
     *  ভাঙন, নতুন কোনো হিসাব/cloud-কল লাগে না (একই `dayCommission()` ফলাফল
     *  থেকেই bmpName ধরে group করা)। */
    private var cbDayCommissionByRmp: List<Pair<String, Double>> = emptyList()

    private fun showCloseReview(board: ChamberAttendanceBoard) {
        cbReviewNameCells.clear()
        cbDayCommissionTotal = 0.0
        cbDayPaidTotal = 0.0
        cbDayCommissionByRmp = emptyList()
        val arrivedOnly = board.rows.filter { it.arrived }
        /* 🔴🔒 V709 (২৬.০৮.২০২৬, TK-রিপোর্ট, ডেমো-প্রুফে অনুমোদিত) — TK: *"আজকে
           কিষানগঞ্জের চেম্বার থেকে একজন পেশেন্টের টাকা রিফান্ড করা হলো, কিন্তু
           চেম্বারের তারিখে কেন দেখাচ্ছে না"*।
           **আসল কারণ (কোড ধরে যাচাই):** রিফান্ডের সারি কখনো `arrived` গণনা করে
           না (ইচ্ছাকৃত — টাকা ফেরত মানে রোগী আসেননি), কিন্তু এই পর্দা **শুধু
           arrived সারিগুলোই** দেখাত ও যোগ করত। তাই রোগী আজ আর কোনো টাকা না
           দিয়ে থাকলে তাঁর সারিটাই বাদ পড়ত — লাইনও দেখাত না, TOTAL থেকেও
           বিয়োগ হত না।
           ⇒ এখন আজকের **অনুমোদিত** রিফান্ডের সারিগুলোও তালিকায় আসে।
           ⛔ উপরের **"N arrived" সংখ্যাটা `arrivedOnly` থেকেই** গোনা হয় — টাকা
              ফেরত মানে রোগী আসেননি, তাই সংখ্যাটা এক অক্ষরও বাড়বে না। */
        val refundOnly = board.rows.filter { !it.arrived && (it.refundCash + it.refundOnline) > 0.0 }
        val arrived = (arrivedOnly + refundOnly).sortedBy { it.arrivedAt.ifBlank { "9999" } }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun money(v: Double) = if (v > 0.0) "₹" + "%,.0f".format(v) else "—"

        val list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }
        // 🔴🔒 V426 (TK-নির্দেশ ১৭.০৮.২০২৬) — *"review পর্দাতে উপরে যে ডেমি লেখা
        //    আছে সেগুলি থাকবে না · সেখানে কত পরিমান টাকা জমা হয়েছে সেগুলো থাকবে"*
        //    ⇒ নির্দেশ-লাইনটা তুলে দেওয়া হলো (TK-এর স্থায়ী নিয়ম), জায়গায় আজকের
        //    টাকার হিসাব বসল। TOTAL = Fees + Cash + Online (TK-অনুমোদিত)।
        //    ⛔ সংখ্যাগুলো নিচের গ্রিডের **ঠিক সেই একই ঘর** থেকেই যোগ হয়
        //       (FEES = feesCash+feesOnline · CASH = paymentCash · ONLINE =
        //       paymentOnline), তাই পর্দার সারি আর উপরের মোট কখনো আলাদা হবে না।
        val cbFeesTotal = arrived.sumOf { it.feesCash + it.feesOnline }
        /* 🔴🔒 V709 — Cash/Online এখন **রিফান্ড বাদ দেওয়ার আগের** অঙ্ক দেখায়
           (`paymentCash` থেকে রিফান্ড আগেই বিয়োগ হয়ে আছে, তাই আবার যোগ করে
           নেওয়া হলো), আর রিফান্ডটা নিজের আলাদা লাইনে লাল রঙে বিয়োগ হয় —
           TK-এর অনুমোদিত ডেমো ঠিক এটাই। যোগফল আগের মতোই মেলে:
             TOTAL = Fees + Cash + Online − Refund
           ⛔ রিফান্ড না থাকলে (`cbRefundTotal == 0`) প্রতিটা সংখ্যা **হুবহু
              আগের মতোই** — লাইনটাও বসে না। */
        val cbRefundTotal = arrived.sumOf { it.refundCash + it.refundOnline }
        val cbCashTotal = arrived.sumOf { it.paymentCash + it.refundCash }
        val cbOnlineTotal = arrived.sumOf { it.paymentOnline + it.refundOnline }
        val cbGrandTotal = cbFeesTotal + cbCashTotal + cbOnlineTotal - cbRefundTotal
        list.addView(android.widget.TextView(this).apply {
            text = "REVIEW — ${arrivedOnly.size} arrived"   // 🔴🔒 V709 — রিফান্ড এখানে গোনা হয় না
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#10223A"))
            setPadding(0, 0, 0, dp(6))
        })
        // 🔴🔒 V472 (20.08.2026, TK-নির্দেশ — "50% হিসাবে যা আসবে সেটাই দেখাতে
        // হবে, কম/বেশি না") — ঐচ্ছিক `decimals` প্যারামিটার যোগ, ডিফল্ট আগের
        // মতোই `false` (Fees/Cash/Online/TOTAL-এর মতো সবসময়-পূর্ণসংখ্যা
        // লাইনগুলো এক অক্ষরও বদলায়নি)। শুধু commission-নির্ভর লাইনগুলোতেই
        // (নিচে) `true` পাঠানো হবে, যেখানে ভগ্নাংশ (₹2.50-এর মতো) সত্যিই
        // আসতে পারে — গোল করলে যোগফল আর মিলবে না।
        fun cbMoneyLine(label: String, value: Double, colorHex: String, bold: Boolean, decimals: Boolean = false): android.widget.LinearLayout =
            android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = label; textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(70), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                    text = "₹" + (if (decimals) "%,.2f".format(value) else "%,.0f".format(value)); textSize = 13.5f
                    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor(colorHex))
                })
                setPadding(0, dp(2), 0, dp(2))
            }
        fun cbThinLine(): android.view.View = android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#BFE9CE"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).also { it.topMargin = dp(5); it.bottomMargin = dp(5) }
        }
        val cbSumBox = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F3FBF6"))
                setStroke(dp(1), android.graphics.Color.parseColor("#BFE9CE"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ); lp.bottomMargin = dp(10); layoutParams = lp
        }
        // 🔴🔒 V426 — কমিশনের তথ্য আসার আগে/পরে **একই ফাংশন** বাক্সটা আঁকে, তাই
        //    দুই অবস্থায় সাজ আলাদা হয়ে যাওয়ার সুযোগ নেই। কমিশন না এলে (ব্রাঞ্চ
        //    "All", নেট নেই, বা RMP-রোগী নেই) আগের মতোই শুধু ৪টে লাইন থাকে।
        fun cbDrawSum(
            comm: List<RmpCommissionRepository.DayCommissionRow>,
            paid: List<RmpCommissionRepository.DayPaidRow> = emptyList()
        ) {
            cbSumBox.removeAllViews()
            cbSumBox.addView(cbMoneyLine("Fees", cbFeesTotal, "#33404F", false))
            cbSumBox.addView(cbMoneyLine("Cash", cbCashTotal, "#0C9E33", false))
            cbSumBox.addView(cbMoneyLine("Online", cbOnlineTotal, "#123A8C", false))
            // 🔴🔒 V709 — রিফান্ড থাকলে তবেই লাইনটা বসে (নইলে পর্দা হুবহু আগের মতো)।
            if (cbRefundTotal > 0.0) {
                cbSumBox.addView(cbMoneyLine("Refund", -cbRefundTotal, "#C0392B", true))
            }
            cbSumBox.addView(cbThinLine())
            cbSumBox.addView(cbMoneyLine("TOTAL", cbGrandTotal, "#0B4F2A", true))
            val withComm = comm.filter { it.commissionToday > 0.0 }
            if (withComm.isNotEmpty()) {
                cbSumBox.addView(cbThinLine())
                cbSumBox.addView(android.widget.TextView(this).apply {
                    text = "RMP COMMISSION"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#B42318"))
                    setPadding(0, dp(2), 0, dp(2))
                })
                // এক RMP-র একাধিক রোগী থাকলে একসাথে: নাম · কত রোগী · কত টাকা
                withComm.groupBy { it.rmpName.ifBlank { it.rmpMobile } }.forEach { (nm, rows) ->
                    val amt = rows.sumOf { it.commissionToday }
                    cbSumBox.addView(android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                            text = nm + "  (" + rows.size + ")"
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                            text = "₹" + "%,.2f".format(amt)
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#B42318"))
                        })
                        setPadding(0, dp(2), 0, dp(2))
                    })
                }
                /* 🔴🔒 V562 (TK, ২২.০৮.২০২৬): *"আপাতত কমিশনটা লাল কালারের আলাদা
                   জায়গায় রাখুন · যদি আমরা দিয়ে থাকি তবেই আমরা আমাদের মতন
                   মাইনাস করে নেব"* ⇒ আগে এখানে TOTAL থেকে কমিশন বাদ দিয়ে
                   "NET TOTAL" দেখানো হত। কিন্তু এটা **দিতে হবে** এমন টাকা,
                   **দেওয়া টাকা নয়** — না-দেওয়া টাকা আয় থেকে বাদ যাওয়া ভুল।
                   এখন শুধু লাল রঙে "মোট দিতে হবে", উপরের TOTAL অপরিবর্তিত। */
                val commTotal = withComm.sumOf { it.commissionToday }
                cbSumBox.addView(cbThinLine())
                cbSumBox.addView(cbMoneyLine("দিতে হবে (মোট)", commTotal, "#B42318", true, decimals = true))
            }
            /* 🔴 V427 (TK-নির্দেশ: *"আলাদা লাইনে 'আজ কত দিলাম'ও রাখুন"*) —
               আজ RMP-দের হাতে সত্যিই কত টাকা গেছে (কমিশন + অ্যাডভান্স)।
               ⛔ **NET TOTAL-এর পরে** বসে এবং **মোট থেকে বাদ যায় না** — শুধু
                  জানার জন্য। বাদ যায় কেবল উপরের *আজকের প্রাপ্য* কমিশন। */
            val paidRows = paid.filter { it.totalPaid > 0.0 }
            if (paidRows.isNotEmpty()) {
                cbSumBox.addView(cbThinLine())
                cbSumBox.addView(android.widget.TextView(this).apply {
                    text = "PAID TO RMP TODAY"
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    setPadding(0, dp(2), 0, dp(2))
                })
                paidRows.forEach { pr ->
                    cbSumBox.addView(android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                            text = pr.rmpName.ifBlank { pr.rmpId }
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                            text = "₹" + "%,.0f".format(pr.totalPaid)
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#33404F"))
                        })
                        setPadding(0, dp(2), 0, dp(2))
                    })
                }
            }
        }
        cbDrawSum(emptyList())
        list.addView(cbSumBox)
        if (arrived.isEmpty()) {
            list.addView(android.widget.TextView(this).apply {
                text = "Nobody has been marked Arrived yet."
                textSize = 13f; setPadding(0, dp(8), 0, dp(8))
            })
        }
        fun rvDivider() = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(1), android.widget.LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.parseColor("#CFD8E0"))
        }
        fun rvCell(text: String, widthDp: Int, weight: Float, colorHex: String, bold: Boolean): android.widget.TextView {
            return android.widget.TextView(this).apply {
                this.text = text; textSize = if (weight > 0f) 12.5f else 10.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor(colorHex))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                val lp = if (weight > 0f) android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    else android.widget.LinearLayout.LayoutParams(dp(widthDp), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams = lp
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
        }
        if (arrived.isNotEmpty()) {
            // grid header
            list.addView(android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setBackgroundColor(android.graphics.Color.parseColor("#0E7C7B"))
                addView(rvCell("PATIENT", 82, 0f, "#FFFFFF", true).apply { gravity = android.view.Gravity.START })
                addView(rvDivider()); addView(rvCell("TREATMENT PROGRESS", 0, 1f, "#FFFFFF", true))
                addView(rvDivider()); addView(rvCell("FEES", 40, 0f, "#FFFFFF", true))
                addView(rvDivider()); addView(rvCell("CASH", 40, 0f, "#FFFFFF", true))
                addView(rvDivider()); addView(rvCell("ONLINE", 40, 0f, "#FFFFFF", true))
            })
        }
        arrived.forEach { r ->
            val gridRow = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                // 🟢🔒 V654 (২৫.০৮.২০২৬, TK-নির্দেশ, ছবিসহ — "ব্যাকগ্রাউন্ড
                // পরিষ্কার সাদা থাকবে") — হালকা সবুজ (#EAF9F1) থেকে সাদা।
                setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ); layoutParams = lp
            }
            // TK-REQUESTED (2026-07-25): Patient cell now shows Name,
            // Mobile, and ID stacked (was name-only) -- rvCell's TextView
            // already renders embedded newlines fine (no maxLines/
            // singleLine set on it), so this is a pure content change, no
            // new view needed.
            // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে মোবাইল দুইবার দেখাত।
            /* 🟢🔒 V588 (23.08.2026, TK-নির্দেশ, ছবিসহ) — *"মোবাইল নাম্বারের
               উপরে মোবাইলের আইকন রাখার কোনো দরকার নেই · পেশেন্ট আইডি থাকার
               কোনো দরকার নেই · ওখানে তারিখ এবং সময় থাকবে"*
               ⇒ 📞 ও 🆔 চিহ্ন দুটো উঠে গেল, তৃতীয় লাইনে **কখন এসেছেন** (তারিখ
               ও সময়) — চেম্বার বোর্ডের সারির হুবহু একই লেখা, একই উৎস
               (`arrivedAt`), তাই পর্দা দুটো কখনো আলাদা দেখাবে না।
               ⛔ Patient ID মোছা হয়নি — ছাপা রেজিস্টারে ও রোগীর কার্ডে আগের
                  মতোই আছে, শুধু এই পর্দায় দেখানো হয় না।
               ⛔ সময় জানা না থাকলে লাইনটা বসেই না (পুরনো সারি)। */
            val nameLines = mutableListOf(r.name.ifBlank { "UNKNOWN" }, r.mobile)
            val whenR = DateUtil.displayWithTime(r.arrivedAt.ifBlank { null })
            if (whenR.isNotBlank()) nameLines.add(whenR)
            // 🔴🔒 V684 (২৫.০৮.২০২৬, TK-নির্দেশ, স্পষ্ট — "Name/Mobile/Date-Time-এর
            // সাথে Ref By (RMP name)-ও প্রথম থেকেই থাকবে, Close Chamber চাপার
            // অপেক্ষা করতে হবে না") — `r.refDoctor` বোর্ড লোড হওয়ার সময়েই
            // আগে থেকে টেনে আনা থাকে (ChamberAttendanceRepository, বাড়তি কোনো
            // cloud-call লাগে না), শুধু এতদিন এখানে ব্যবহার হতো না। এখন সরাসরি
            // চতুর্থ লাইনে বসে। ⛔ নিচের V667-এর "Close Chamber" review-ধাপের
            // RMP-কমিশন ট্যাগ অক্ষত রইল (ওটা আলাদা, কমিশনের হিসাবের জন্য)।
            /* 🔴🔒 V933 — নিয়মটা এখন একটাই জায়গায় (`refByLabel`), তাই RMP-র
               নাম না লেখা থাকলেও "Ref By: RMP" বসে। ⛔ নাম থাকলে হুবহু আগের লেখা। */
            ChamberAttendanceRepository.refByLabel(r).takeIf { it.isNotBlank() }?.let { nameLines.add(it) }
            // 🔴 V426: RMP-চিহ্ন বসানোর জন্য এই ঘরটা মনে রাখা হয় (মোবাইল ধরে)।
            val nameCell = rvCell(nameLines.joinToString("\n"), 82, 0f, "#10223A", true).apply {
                gravity = android.view.Gravity.START; setPadding(dp(8), dp(10), dp(4), dp(10)); textSize = 11f
                isClickable = true; isFocusable = true
                setOnClickListener { showFullJourneyOrReportCardChooser(r) }
                cbReviewNameCells[r.mobile.filter { c -> c.isDigit() }.takeLast(10)] = this
            }
            // 🆕 TK-নির্দেশ (04.08.2026, ছবিসহ — SHAHARYA-এর কার্ডে "Registered
            // patient / Visit created" আসল লেখার মতোই দেখাচ্ছিল, যদিও এটা
            // Registration/Visit তৈরির সময় সিস্টেম নিজে থেকে বসানো একটা
            // ডিফল্ট স্টাব — স্টাফ নিজে কিছু লেখেননি)। TK চান এটা খালি বক্সের
            // মতোই (হালকা রঙে "কিছু লিখুন" ধাঁচে) দেখাক, স্টাফ সত্যিই কিছু
            // লিখলে তবেই আসল (গাঢ়) লেখা দেখাবে।
            // ⛔ এই একই sentinel টেক্সট প্রজেক্টের অন্য কোথাও (Draft/Follow-up
            // কার্ড, PatientModel.kt) ব্যবহার হয় — সেসব জায়গার প্রদর্শন এতটুকু
            // ছোঁয়া হয়নি, শুধু এই একটা "Treatment Progress" কলামের রঙ/লেখা।
            /* 🔴🔒 V696 (২৬.০৮.২০২৬, TK-এর ছবিতে ধরা) — **শেষ পাহারা।**
               আসল দোষটা উৎসেই সারানো হয়েছে (`ChamberAttendanceRepository`-তে
               `row.s("progress")`), কিন্তু যাঁদের ফোনে আগের বিল্ডের জমানো
               তালিকায় ইতিমধ্যেই "null" লেখাটা ঢুকে গেছে, তাঁদের পর্দাতেও যেন
               ওটা আর না দেখায় — তাই এখানেও ফাঁকা ধরা হয়। */
            val rawRemark = r.remark.trim().let { if (it.equals("null", ignoreCase = true)) "" else it }
            val isAutoStubRemark = rawRemark.equals("Registered patient / Visit created", ignoreCase = true)
            val treatText = if (isAutoStubRemark) NoBengali.s("কিছু লেখা হয়নি — চাপুন") else rawRemark.ifBlank { "—" }
            // 🟢🔒🔒 V654 (২৫.০৮.২০২৬, TK-নির্দেশ, ছবিসহ — "গত দিনের ট্রিটমেন্ট
            // প্রগ্রেস যেন হাইড থাকে... আজকে যেটা লিখব সেটা যেন উজ্জ্বল থাকে")
            // — আসল কারণ (আগের V535-এর একই সমস্যা, আজকের বোর্ডে): `remark`
            // ঘরে রোগীর **সবচেয়ে সাম্প্রতিক** লেখাটাই থাকে, সেটা আজ লেখা
            // হয়েছে না বহু আগে — কোনো পার্থক্য দেখানো হতো না। এখন
            // `remarkUpdatedAt`-এর তারিখ আজকের সাথে মিলিয়ে দেখা হয় —
            // • আজকের লেখা → গাঢ়/বোল্ড, স্পষ্ট উজ্জ্বল রঙে
            // • আগের দিনের লেখা → হালকা ধূসর ("আগের দিনের কাজ" বোঝাতে)
            // ⛔ লেখা/সেভ/এডিট — কিছুই বদলায়নি, শুধু রং।
            val today = FollowUpModel.today()
            val isFromToday = r.remarkUpdatedAt.take(10) == today
            val hasRealRemark = rawRemark.isNotBlank() && !isAutoStubRemark
            val treatColor = when {
                rawRemark.isBlank() || isAutoStubRemark -> "#C47B00"
                hasRealRemark && isFromToday -> "#0B4F2A"       // আজকের — গাঢ়, উজ্জ্বল সবুজ
                else -> "#9AA4B2"                                // আগের দিনের — হালকা ধূসর
            }
            val treatBold = hasRealRemark && isFromToday
            val treatCell = rvCell(treatText, 0, 1f, treatColor, treatBold).apply { textSize = 11f; gravity = android.view.Gravity.CENTER }
            /* 🔴🔒 V709 — `money()` ঋণাত্মক অঙ্ককে "—" দেখাত, তাই রিফান্ডের
               সারিতে টাকাটা উধাও হয়ে যেত। এখন ঋণাত্মক হলে "− ₹x" লাল রঙে।
               ⛔ ধনাত্মক ও শূন্য — দুটোই আগের মতোই (একই `money()`, একই রং)। */
            fun cellText(v: Double) = if (v < 0.0) "− ₹" + "%,.0f".format(-v) else money(v)
            fun cellColor(v: Double, normal: String) = if (v < 0.0) "#C0392B" else normal
            val feesCell = rvCell(cellText(r.feesCash + r.feesOnline), 40, 0f, cellColor(r.feesCash + r.feesOnline, "#33404F"), true)
            val cashCell = rvCell(cellText(r.paymentCash), 40, 0f, cellColor(r.paymentCash, "#0C9E33"), true)
            val onlineCell = rvCell(cellText(r.paymentOnline), 40, 0f, cellColor(r.paymentOnline, "#123A8C"), true)
            gridRow.addView(nameCell)
            gridRow.addView(rvDivider()); gridRow.addView(treatCell)
            gridRow.addView(rvDivider()); gridRow.addView(feesCell)
            gridRow.addView(rvDivider()); gridRow.addView(cashCell)
            gridRow.addView(rvDivider()); gridRow.addView(onlineCell)
            // TK-CORRECTED (2026-07-22): triple-tap used to be on the WHOLE
            // row, opening one combined "Edit Treatment/Fix Payment" chooser
            // no matter which cell was tapped -- confusing (TK's words: "দুটো
            // এক জায়গায় রেখেছেন তার জন্য বিভ্রান্ত হয়ে যাচ্ছি"). Now each
            // column opens ONLY the edit relevant to that column: TREATMENT
            // PROGRESS -> Edit Treatment/Remark directly; FEES/CASH/ONLINE
            // (all three are payment amounts) -> Fix Payment directly. No
            // chooser dialog anymore, so there is nothing to mix up.
            TripleTapEdit.attach(treatCell) { editRemarkInReview(r) }
            TripleTapEdit.attach(feesCell) { fixPaymentInReview(r) }
            TripleTapEdit.attach(cashCell) { fixPaymentInReview(r) }
            TripleTapEdit.attach(onlineCell) { fixPaymentInReview(r) }
            list.addView(gridRow)
            list.addView(android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(android.graphics.Color.parseColor("#CFD8E0"))
            })
        }

        UppercaseInputUtil.applyToAll(list)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this)
            .setView(android.widget.ScrollView(this).apply { addView(list) })
            .setPositiveButton("✅ Confirm Close") { _, _ ->
                currentReviewDialog = null
                lifecycleScope.launch {
                    // Review বাধ্যতামূলক; Confirm হলেই close mark লেখা হয়।
                    // Print আর close-এর শর্ত নয়—নেট ব্যর্থ হলে repository-র
                    // প্রমাণিত pending queue পরে Cloud-এ নিজে পাঠাবে।
                    val cloudSaved = withContext(Dispatchers.IO) {
                        val br = printBranchOverride.ifBlank { selectedBranch }
                        if (br.isNotBlank() && !br.equals("All", ignoreCase = true)) {
                            ChamberCloseRepository.markClosed(
                                this@ChamberAttendanceActivity, br, selectedDate,
                                NativeSession.current(this@ChamberAttendanceActivity)
                            )
                        } else false
                    }
                    // 🔒 TK-APPROVED (28.07.2026, ফটো-প্রুফে লক · খাতার সারি B35):
                    // *"সেভ ও ক্লোজ করলে তো এখানে জিরো হয়ে যেতে হবে বা ব্ল্যাঙ্ক
                    // হয়ে যেতে হবে।"* — ছাপা হয়ে যাওয়ার পরে বোর্ড ফাঁকা হয়ে যায়,
                    // দুটো সংখ্যাই 0, আর উপরের কাজের বোতামগুলো বন্ধ।
                    // ⛔ কোনো তথ্য মোছা হয় না — উপরের ক্যালেন্ডারে ওই তারিখ চাপলে
                    // পুরো তালিকা আবার দেখা যায় (এবং Share PDF / Print-ও)।
                    if (!isFinishing && !isDestroyed) {
                        closedToday = true
                        // 🔒 খাতার সারি B46: বিগত দিনের চেম্বার বন্ধ করলেও ঠিক
                        // এখানেই চিহ্নটা বসে, আর "চেম্বার বন্ধ করুন" তালিকার
                        // পুরনো স্মৃতি ফেলে দেওয়া হয় যাতে ফিরে গেলে দিনটা আর
                        // তালিকায় না থাকে।
                        dateClosedFlag = true
                        try { ChamberUnclosedRepository.clearCache() } catch (_: Throwable) { }
                        applyDayState()
                        offerOptionalRegisterPrint(board, cloudSaved)
                    }
                }
            }
            .setNegativeButton("Back", null)
            .create()
        currentReviewDialog = dialog
        dialog.show()
        try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(dialog) } catch (_: Throwable) { }   // 🤫 V774

        /* 🔴🔒 V426 (TK-নির্দেশ ১৭.০৮.২০২৬) — *"Review পর্দাতে যদি কোন আরএমপির
           পেশেন্ট হয়ে থাকে তাহলে তার কমিশন এখানে মেনশন করতে হবে · এবং কমিশন বাদ
           দিয়ে সর্বমোট টাকার পরিমান থেকে কমে যাবে"* + *"রোগীর পাশে যদি ছোট্ট করে
           RMP লেখা থাকে তাহলে একবারেই বুঝতে পারব"*।
           ⛔ হিসাবটা সার্ভারেই (`fin.rmp_day_commission`) — **এক ডাকে**, রোগীপ্রতি
              আলাদা ডাক নয়, তাই Egress-এ চাপ পড়ে না।
           ⛔ পর্দা আগেই দেখানো হয়ে গেছে; তথ্য এলে শুধু **যোগ** হয়। ডাক ব্যর্থ হলে
              বা RMP-রোগী না থাকলে পর্দা হুবহু আগের মতোই থাকে — কিছুই ভাঙে না।
           ⛔ ব্রাঞ্চ "All" হলে ডাকা হয় না (সার্ভার একটাই ব্রাঞ্চ নেয়)। */
        run {
            val cbBranch = printBranchOverride.ifBlank {
                if (selectedBranch != "All") selectedBranch else user.branch
            }
            if (cbBranch.isNotBlank() && cbBranch != "All") {
                lifecycleScope.launch {
                    // 🔴🔒 V429 (TK-রিপোর্ট: "চেম্বার খুলে Close Chamber চাপলাম,
                    //    কমিশন দেখা যাচ্ছে না") — **আমার ভুল।** এই দুটো ডাক
                    //    `ModuleAuth`-এর মাধ্যমে যায়, কিন্তু এই পর্দা আগে কখনো
                    //    ModuleAuth ব্যবহার করেনি, তাই তার লগইন-টোকেন ফাঁকা থাকত
                    //    ⇒ ডাক ব্যর্থ ⇒ চুপচাপ কিছুই দেখাত না।
                    //    প্রজেক্টের অন্য সব জায়গায় (WorkNotebook · IePermit ·
                    //    SalaryReminder · DoctorVisit) ডাকার আগে ঠিক এই একই
                    //    লাইনটাই আছে — এখানেও বসানো হলো।
                    withContext(Dispatchers.IO) {
                        try {
                            if (!com.tkbiswas.pilesclinic.modules.ModuleAuth.isSignedIn)
                                com.tkbiswas.pilesclinic.modules.ModuleAuth
                                    .signInCurrentSession(applicationContext)
                        } catch (_: Throwable) { }
                    }
                    val res = withContext(Dispatchers.IO) {
                        try { RmpCommissionRepository.dayCommission(cbBranch, selectedDate) }
                        catch (_: Throwable) { null }
                    }
                    val paidRes = withContext(Dispatchers.IO) {
                        try { RmpCommissionRepository.dayPaid(cbBranch, selectedDate) }
                        catch (_: Throwable) { null }
                    }
                    val rows = res?.value.orEmpty()
                    val paidRows = paidRes?.value.orEmpty()
                    cbDayPaidTotal = paidRows.sumOf { it.totalPaid }
                    if ((rows.isNotEmpty() || paidRows.isNotEmpty()) && !isFinishing && !isDestroyed) {
                        cbDayCommissionTotal = rows.sumOf { it.commissionToday }
                        // 🔴🔒 V685 — প্রতিটা RMP-র নাম ধরে group করে যোগফল, প্রিন্টে
                        // দেখানোর জন্য (একই `rows`, নতুন কোনো কল লাগেনি)।
                        cbDayCommissionByRmp = rows.groupBy { it.rmpName.ifBlank { "RMP" } }
                            .map { (name, list) -> name to list.sumOf { it.commissionToday } }
                            .filter { it.second > 0.0 }
                        try { cbDrawSum(rows, paidRows) } catch (_: Throwable) { }
                        rows.forEach { rw ->
                            val key = rw.patientMobile.filter { c -> c.isDigit() }.takeLast(10)
                            val cell = cbReviewNameCells[key] ?: return@forEach
                            try {
                                val base = cell.text.toString()
                                // 🟢🔒🔒 V667 (২৫.০৮.২০২৬, TK-নির্দেশ, স্পষ্ট — "কোন পেশেন্ট যদি
                                // RMP-এর হয়ে থাকে তাহলে যেন সেটা Ref By RMP-এর নাম থাকে") —
                                // আগে শুধু generic "RMP" ট্যাগ বসত (V426), RMP-এর আসল নাম
                                // কখনো দেখানো হতো না — অথচ `rw.rmpName`-এ নামটা **আগে থেকেই**
                                // ছিল (dayCommission() ইতিমধ্যে টেনে আনে), শুধু ব্যবহার হতো না।
                                // এখন "RMP" ট্যাগের বদলে সরাসরি "Ref By: [নাম]" বসে।
                                val tagText = "Ref By: ${rw.rmpName.ifBlank { "RMP" }} "
                                // 🔴🔒 V684 — নিচের প্লেইন "Ref By:" লাইনটা (বোর্ড
                                // লোড হওয়ার সময়েই বসে যায়, উপরে দেখুন) থাকলে এই
                                // রঙিন ব্যাজ আর দ্বিতীয়বার বসানো হয় না (ডুপ্লিকেট
                                // এড়াতে) — তাই "startsWith" থেকে "contains" করা হলো।
                                if (!base.contains("Ref By:")) {
                                    val sp = android.text.SpannableStringBuilder(tagText)
                                    sp.setSpan(android.text.style.BackgroundColorSpan(
                                        android.graphics.Color.parseColor("#B42318")), 0, tagText.length, 0)
                                    sp.setSpan(android.text.style.ForegroundColorSpan(
                                        android.graphics.Color.WHITE), 0, tagText.length, 0)
                                    sp.setSpan(android.text.style.RelativeSizeSpan(0.85f), 0, tagText.length, 0)
                                    sp.append(base)
                                    cell.text = sp
                                }
                            } catch (_: Throwable) { }
                        }
                    }
                }
            }
        }
        // TK-REQUESTED (2026-07-22): this was a small centered popup ("ছোট
        // ডিসপ্লে") -- now expanded to fill the entire screen. Everything
        // else about the dialog (the list, triple-tap edits, the two
        // buttons, Back/Cancel behaviour) is completely unchanged; only the
        // window's size grows to match_parent.
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    /** Reloads the board fresh (once) and reopens the Review, so a just-made
     *  edit is reflected immediately and the underlying screen stays in sync. */
    private fun refreshBoardAndReopenReview() {
        currentReviewDialog?.dismiss(); currentReviewDialog = null
        loadBoard(onRendered = { lastBoard?.let { showCloseReview(it) } })
    }

    private fun editRemarkInReview(r: ChamberAttendanceRow) {
        // TK-REPORTED (2026-07-22): same fix as showRemarkDialog/writeTreatment.
        if (r.followUpId.isBlank()) {
            val digits = r.mobile.filter { it.isDigit() }.takeLast(10)
            if (digits.length != 10) {
                android.widget.Toast.makeText(this, "No Follow-up record yet for this patient", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            lifecycleScope.launch {
                val resolvedId = resolveBestFollowUpId(digits)
                if (resolvedId == terminalFollowUpSentinel) {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Entry is Rejected/Incomplete — Restore first", android.widget.Toast.LENGTH_SHORT).show()
                } else if (resolvedId.isNullOrBlank()) {
                    // null = lookup failed, empty = genuinely no row; neither path creates a row here.
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "No Follow-up record yet for this patient", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    editRemarkInReview(r.copy(followUpId = resolvedId))
                }
            }
            return
        }
        val d = resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
        val input = android.widget.EditText(this).apply {
            setText(r.remark); hint = TreatmentQuickNotes.hint()   // 🟢 V588
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val p = dp(12); setPadding(p, p, p, p); minLines = 2; gravity = android.view.Gravity.TOP
            setHintTextColor(androidx.core.content.ContextCompat.getColor(this@ChamberAttendanceActivity, com.tkbiswas.pilesclinic.R.color.field_hint))
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0); addView(input)
        }
        /* 🟢🔒 V588 — Review পর্দার এই বাক্সেও চেম্বার-বোর্ডের একই ৯টা চিপ। */
        TreatmentQuickNotes.attach(this, box, input)
        UppercaseInputUtil.applyToAll(box)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "🩺 Treatment / Remark — ${r.name.ifBlank { r.mobile }}"))
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val remark = input.text.toString().trim()
                // 🔒 খাতার সারি B54 (TK, 28.07.2026 রাত): ফাঁকা রিমার্ক দিয়ে আগের
                // লেখা মুছে ফেলা যাবে না।
                if (remark.isBlank()) {
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Nothing written — the earlier remark is kept", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // 🔒🔒 খাতার সারি B199 (TK, 30.07.2026 রাত — Chamber Attendance
                // লোডিং ফিক্স, "ঝুঁকিহীন ভাবে কাজটা করুন"): showRemarkDialog()-এর
                // (খাতার সারি B197) হুবহু একই নিরাপদ প্যাটার্ন, এখানে Review
                // পপ-আপের জন্য। ⛔ `FollowUpRepository.updateRemark()`-এর
                // ভিতরে (ইতিহাস-মেলানো ও ২৮.০৭.২০২৬-এর যাচাই-বাগ-ফিক্স সহ)
                // একটা অক্ষরও ছোঁয়া হয়নি — শুধু এই কল-সাইটটাই বদলেছে।
                //
                // আগে: `refreshBoardAndReopenReview()` পুরো বোর্ড **নতুন করে
                // ক্লাউড থেকে নামাত** (`loadBoard()`), তারপর Review পপ-আপ আবার
                // খুলত — তাই এখানেও সেভ শেষ না হওয়া পর্যন্ত "লোডিং"।
                // এখন: স্টাফ যা টাইপ করেছেন তা সরাসরি স্ক্রিনে-থাকা বোর্ডের
                // (`lastBoard`, মেমোরিতে) সেই রোগীর সারিতে বসিয়ে Review পপ-আপ
                // **সঙ্গে সঙ্গে (কোনো নেটওয়ার্ক-কল ছাড়াই)** আবার দেখানো হয়
                // (`showCloseReview(updatedBoard)` — যেটা এমনিতেই একটা বোর্ড
                // অবজেক্ট নিয়ে সরাসরি পপ-আপ বানায়, তাই এখানে নতুন করে ক্লাউড
                // থেকে আনার দরকারই নেই)। আসল সেভ পিছনে চলে।
                val board0 = lastBoard
                if (board0 != null) {
                    val updatedRows = board0.rows.map { if (it.mobile == r.mobile) it.copy(remark = remark) else it }
                    val updatedBoard = board0.copy(rows = updatedRows)
                    lastBoard = updatedBoard
                    currentReviewDialog?.dismiss(); currentReviewDialog = null
                    showCloseReview(updatedBoard)
                }
                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "Treatment/Remark updated", android.widget.Toast.LENGTH_SHORT).show()
                val appCtx = this@ChamberAttendanceActivity.applicationContext
                val staffName = user.name.ifBlank { user.mobile }
                BackgroundWork.run {
                    try { FollowUpRepository(appCtx).updateRemark(r.followUpId, remark, staffName) } catch (_: Throwable) { }
                }
                // 🟢 V590 — Review-এর এই বাক্সটাও এতদিন Report Card-এ কিছু পাঠাত না।
                syncProgressToReportCard(r, remark, selectedDate)
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun fixPaymentInReview(r: ChamberAttendanceRow) {
        val fullMobile = "+91" + r.mobile.filter { it.isDigit() }.takeLast(10)
        // 🔒 TK-এর নিয়ম (28.07.2026): তাপ দেওয়ামাত্র স্টাফ যেন বুঝতে পারেন কাজ
        // শুরু হয়েছে — পর্দা যেন মরা মনে না হয়।
        // ⚠️ এই পপ-আপটা অন্য দুটোর মতো আগে খোলা যায় না: এর ভিতরের তালিকাটাই
        // ওই দিনের আসল পেমেন্টের সারি। আগে খুললে ফাঁকা বা পুরনো তালিকা দেখাত
        // আর স্টাফ ভুল সারিতে চাপ দিয়ে ভুল টাকা সংশোধন করে ফেলতে পারতেন —
        // টাকার জায়গায় সেই ঝুঁকি নেওয়া হয়নি।
        android.widget.Toast.makeText(this, "Loading payments…", android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                try {
                    val arr = SupabaseClient.findByMobile("payments", fullMobile, "*", 50)
                    (0 until arr.length()).map { arr.getJSONObject(it) }.filter {
                        it.optString("date") == selectedDate &&
                        it.optString("payType") != "attendance_mark" &&
                        it.optString("payType") != "chamber_expected"
                    }
                } catch (_: Throwable) { emptyList() }
            }
            if (rows.isEmpty()) {
                android.widget.Toast.makeText(this@ChamberAttendanceActivity, "No editable payment for this patient on this date", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = rows.map { p ->
                val label = p.s("payLabel").ifBlank { p.s("paymentLabel").ifBlank { "Payment" } }
                // TK-REQUESTED (2026-07-21): every payment already records WHO
                // took it (receivedBy) and WHEN (createdAt) -- this data existed
                // all along but was never shown here. Now shown so TK can see
                // exactly who entered any payment and when, right from this list.
                val staff = p.s("editedBy").ifBlank { p.s("receivedBy").ifBlank { p.s("createdBy") } }
                val when_ = if (p.s("createdAt").isNotBlank()) DateUtil.displayWithTime(
                    // FIX (2026-07-25): createdAt is written by isoNow() using the
                    // DEVICE's local clock with a literal 'Z' suffix, so the stored
                    // text is already local wall clock time. Parsing it as UTC added
                    // +5:30 and showed a future time (10.53 am shown as 4.23 pm).
                    // Parse in the default (local) zone so the shown time is exact.
                    try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(p.s("createdAt")) ?: java.util.Date() }
                    catch (_: Exception) { java.util.Date() }
                ) else DateUtil.display(p.s("date"))
                // TK-REQUESTED (2026-07-22): show the STAFF CODE (e.g.
                // "JPE-JALPAI-13"), not the raw mobile number.
                val who = if (staff.isNotBlank()) "\nBy: ${StaffDirectory.findAccount(staff)?.name ?: staff}" else ""
                val whenLine = if (when_.isNotBlank()) "\nOn: $when_" else ""
                "$label — ₹${"%,.0f".format(p.optDouble("amount", 0.0))} (${p.s("mode").ifBlank { "CASH" }})$who$whenLine"
            }.toTypedArray()
            AlertDialog.Builder(this@ChamberAttendanceActivity)
                .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, "💵 Fix Payment — ${r.name.ifBlank { r.mobile }}"))
                .setItems(labels) { _, idx -> editOnePaymentRow(rows[idx]) }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    /** Edits ONE payments row by id -- amount + mode -- with the same audit
     *  note the Payment screen's editor writes. Precise and reversible; never
     *  touches any other row. */
    private fun editOnePaymentRow(p: org.json.JSONObject) {
        val eventCount = p.optJSONArray("dailyEvents")?.length()?.coerceAtLeast(1) ?: 1
        if (p.s("payType").equals("treatment", true) && eventCount > 1) {
            // 🟢🔒 V618 (২৪.০৮.২০২৬, TK-নির্দেশ, সততার সাথে যাচাই করে) —
            // আগে এখানে (Chamber Date-এর "Fix Payment") মিশ্র পেমেন্ট
            // পড়লেই **সবার জন্য** (এমনকি Master-এর জন্যও) দরজা বন্ধ হয়ে
            // যেত — শুধু সতর্কবার্তা, কোনো এডিট/ডিলিট পথই ছিল না। অথচ
            // PaymentActivity-তে ঠিক এই কাজের জন্যই একটা নির্ভুল (আন্দাজ
            // ছাড়া, `dailyEvents`-এর আসল আলাদা এন্ট্রি ধরে) এডিটর
            // আগে থেকেই তৈরি ছিল, শুধু Master-only ছিল।
            //
            // ⇒ এখন দুটো কাজ একসাথে ঠিক হলো:
            //   ১) PaymentActivity-র ওই এডিটর এখন বাকি সব পেমেন্টের মতোই
            //      "আজ/গতকাল-মুক্ত, তার বেশি Master লাগবে" নিয়মে চলে
            //      (এই একই ফাইলের অন্য জায়গার পরিবর্তন, `PaymentActivity.kt`)।
            //   ২) এখানে (Chamber Date) মিশ্র পেমেন্ট পড়লে আর দরজা বন্ধ
            //      না করে, সোজা সেই নির্ভুল এডিটরেই পাঠানো হচ্ছে — নতুন
            //      কোনো এডিট-লজিক এখানে বানানো হয়নি (দ্বিগুণ কোড/দ্বিগুণ
            //      ভুলের ঝুঁকি এড়াতে), শুধু সঠিক জায়গায় পাঠানো।
            val digits = p.s("mobile").filter { it.isDigit() }.takeLast(10)
            android.widget.Toast.makeText(this, NoBengali.s("মিশ্র পেমেন্ট — বিস্তারিত এডিটরে নিয়ে যাওয়া হচ্ছে…"), android.widget.Toast.LENGTH_SHORT).show()
            startActivity(
                android.content.Intent(this, PaymentActivity::class.java)
                    .putExtra("mobile", digits)
                    .putExtra("patientCode", p.s("patientCode"))
            )
            return
        }
        val id = p.optString("id")
        if (id.isBlank()) {
            android.widget.Toast.makeText(this, "This entry is not editable", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // TK-REQUESTED (2026-07-25): this screen had NO day restriction at
        // all before -- anyone could edit any payment's amount any time.
        // Now matches Report Card's rule exactly: free on the payment's
        // own day or the next day; after that, Master edits directly as
        // always, but staff must send a Request that Master approves.
        val payDate = p.optString("date", "")
        if (user.role != "master" && !(payDate.isNotBlank() && PaymentModel.withinFreeEditWindow(payDate))) {
            showRequestPaymentEditDialog(p, id, payDate)
            return
        }
        val d = resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
        val amtInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
            setText(p.optDouble("amount", 0.0).toInt().toString()); hint = "Amount"
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = dp(12); setPadding(pad, pad, pad, pad)
        }
        val modeInput = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@ChamberAttendanceActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "ONLINE"))
            setSelection(if (p.s("mode").ifBlank { "CASH" }.equals("CASH", true)) 0 else 1)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), 0)
            addView(android.widget.TextView(this@ChamberAttendanceActivity).apply { text = "Amount"; textSize = 12.5f; setTextColor(android.graphics.Color.parseColor("#5B6B81")); setPadding(0, dp(6), 0, dp(4)) })
            addView(amtInput)
            addView(android.widget.TextView(this@ChamberAttendanceActivity).apply { text = "Mode"; textSize = 12.5f; setTextColor(android.graphics.Color.parseColor("#5B6B81")); setPadding(0, dp(10), 0, dp(4)) })
            addView(modeInput)
        }
        UppercaseInputUtil.applyToAll(box)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "✏️ Edit Amount"))
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val amt = amtInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (amt <= 0) { android.widget.Toast.makeText(this, "Enter a valid amount", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val newMode = modeInput.selectedItem.toString()
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            val oldAmt = p.optDouble("amount", 0.0)
                            val oldMode = p.s("mode").ifBlank { "CASH" }
                            val by = user.name.ifBlank { user.mobile }
                            val whenStr = DateUtil.displayWithTime(java.util.Date())
                            val changes = mutableListOf<String>()
                            // 🔴 TK-REPORTED (30.07.2026 রাত, Kishanganj স্টাফের হিন্দি
                            // বার্তা): "থেকে"/"করেছেন" বাংলা শব্দ এই অডিট-স্ট্রিং-এ
                            // ছিল, যা ডেটাবেসে সেভ হয়ে সব স্ক্রিনে দেখা যায় —
                            // এখন Mode-লাইনের মতোই ইংরেজি "→"/"by"। ⛔ টাকার
                            // অঙ্ক/হিসাব/লজিক কিছুই বদলায়নি।
                            if (amt != oldAmt) changes.add("Amount ₹${"%,.0f".format(oldAmt)} → ₹${"%,.0f".format(amt)}")
                            if (!newMode.equals(oldMode, true)) changes.add("Mode $oldMode → $newMode")
                            val prevRemarks = p.s("remarks")
                            val remarks = if (changes.isNotEmpty()) {
                                val audit = "Audit: ${changes.joinToString(", ")} by $by | Date: $whenStr (Chamber Review)"
                                if (prevRemarks.isNotBlank()) "$prevRemarks | $audit" else audit
                            } else prevRemarks
                            val fields = org.json.JSONObject()
                                .put("amount", amt).put("mode", newMode).put("remarks", remarks)
                            val saved = SupabaseClient.updateById("payments", id, fields)
                            if (!saved) GenericUpdateQueue.queue(this@ChamberAttendanceActivity, "payments", id, fields)
                            saved
                        } catch (_: Throwable) { false }
                    }
                    android.widget.Toast.makeText(this@ChamberAttendanceActivity, if (ok) "Payment updated" else "Failed — check connection", android.widget.Toast.LENGTH_SHORT).show()
                    if (ok) refreshBoardAndReopenReview()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("🗑️ Delete") { _, _ ->
                // 🔒🔒 খাতার সারি B111: PaymentActivity-র মতোই — স্টাফ টাকা মুছতে
                // পারবেন না, অনুরোধ মাস্টারের ঘন্টায় যাবে। (খাতার সারি B98-এর
                // বাকি থেকে যাওয়া দ্বিতীয় জায়গা।)
                // 🔒 খাতার সারি B112: স্টাফ আজ ও গতকালের এন্ট্রি নিজে মুছতে পারবেন,
                // তবে ওই দিনের চেম্বার বন্ধ হয়ে গেলে নয় (মাস্টার তখন হিসাব পেয়ে গেছেন)।
                // ⚠️ যাচাইটা ক্লাউড ছুঁতে পারে, তাই ব্যাকগ্রাউন্ডে।
                val amtT = "₹${"%,.0f".format(p.optDouble("amount", 0.0))}"
                lifecycleScope.launch {
                val allowedNow = withContext(Dispatchers.IO) {
                    try { DeletePermission.canDeleteEntryNow(this@ChamberAttendanceActivity, user, p.s("date"), p.s("branch"), paid = true) }
                    catch (_: Throwable) { false }
                }
                if (!allowedNow) {
                    AlertDialog.Builder(this@ChamberAttendanceActivity)
                        .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, NoBengali.s("Master's approval needed")))
                        .setMessage(NoBengali.s("⛔ Nothing will be deleted now. A request goes to the Master; it is deleted only after approval."))
                        .setPositiveButton("Send Request") { _, _ ->
                            lifecycleScope.launch {
                                val sent = withContext(Dispatchers.IO) {
                                    try {
                                        DeletePermission.sendRequest(
                                            this@ChamberAttendanceActivity, user, "Payment",
                                            p.s("name"), p.s("mobile"), p.s("patientCode"),
                                            p.s("branch"), amtT, p.s("id")
                                        )
                                    } catch (_: Throwable) { false }
                                }
                                android.widget.Toast.makeText(
                                    this@ChamberAttendanceActivity, NoBengali.s(if (sent) "Master-কে অনুরোধ পাঠানো হয়েছে" else "ব্যর্থ — নেট চেক করুন"),
                                    android.widget.Toast.LENGTH_LONG
                                ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                            }
                        }
                        .setNegativeButton("Close", null)
                        .show().also { PremiumAlert.paint(it) }
                    return@launch
                }
                // TK-REQUESTED (2026-07-24): a wrong payment entry (like a
                // stray ₹1 test) can now be removed entirely -- moved to
                // Trash (same safe, reversible pattern already used for
                // Enquiry/Doctor Visit delete elsewhere), NOT a permanent
                // delete, so it can be Restored from Trash Bin if this was
                // a mistake. A confirm step guards against an accidental tap.
                // 🔴 B334 (03.08.2026, একই ক্লাসের বাগ — PaymentActivity.kt-এ
                // TK যেটা ধরেছিলেন, এখানেও একই ঝুঁকি ছিল): "Delete" বোতাম
                // চাপার পরেও সক্রিয় থাকত, তাড়াহুড়োয় একাধিকবার চাপলে একই
                // পেমেন্ট একাধিকবার Trash-এ চলে যেতে পারত। প্রথম চাপেই বোতাম
                // বন্ধ করে দেওয়া হলো।
                val chDelDialog = AlertDialog.Builder(this@ChamberAttendanceActivity)
                    .setCustomTitle(PremiumAlert.header(this@ChamberAttendanceActivity, "Delete this payment?"))
                    .setMessage("₹${"%,.0f".format(p.optDouble("amount", 0.0))} (${p.s("mode").ifBlank { "CASH" }}) will be moved to Trash. This changes the patient's Paid/Due total.")
                    .setPositiveButton("Delete", null)
                    .setNegativeButton("Cancel", null)
                    .show().also { PremiumAlert.paint(it) }
                chDelDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    chDelDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    chDelDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try { TrashHelper.moveToTrash("payments", p, user.mobile) } catch (_: Throwable) { false }
                        }
                        android.widget.Toast.makeText(this@ChamberAttendanceActivity, if (ok) "Moved to Trash" else "Failed — check connection", android.widget.Toast.LENGTH_SHORT).show()
                        chDelDialog.dismiss()
                        if (ok) refreshBoardAndReopenReview()
                    }
                }
                }
            }
            // 🔒 খাতার সারি B181: বাইরের এই "Edit Amount" ডায়ালগটার নিজের
            // পাহারা ছিল না — ভিতরের নেস্টেড ডিলিট-কনফার্ম আগে থেকেই ঢাকা ছিল।
            .show().also { PremiumAlert.paint(it) }
    }

    // TK-REQUESTED ADDITION (2026-07-25): staff-side request when the free
    // 2-day window has passed for this payment -- same idea as Report
    // Card's own version, built directly from the payments row `p` (which
    // already carries mobile/name/branch/patientId, so no extra network
    // call is needed here).
    private fun showRequestPaymentEditDialog(p: org.json.JSONObject, id: String, payDate: String) {
        val d = resources.displayMetrics.density; fun dp(v: Int) = (v * d).toInt()
        val oldAmount = p.optDouble("amount", 0.0)
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
            hint = "Correct amount"
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = dp(12); setPadding(pad, pad, pad, pad)
        }
        val reason = android.widget.EditText(this).apply {
            hint = NoBengali.s("কেন বদলাতে হচ্ছে (Reason)")
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = dp(12); setPadding(pad, pad, pad, pad)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), 0)
            addView(android.widget.TextView(this@ChamberAttendanceActivity).apply {
                text = NoBengali.s("⚠️ পেমেন্টের দিন পার হয়ে গেছে (2 দিনের বেশি) — এখন Amount বদলাতে Master-এর অনুমতি লাগবে। এখানে অনুরোধ পাঠান।")
                textSize = 11f; setTextColor(android.graphics.Color.parseColor("#B45309"))
                setPadding(0, 0, 0, dp(8))
            })
            addView(input); addView(reason)
        }
        UppercaseInputUtil.applyToAll(box)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "🔒 Request Edit — Master Approval"))
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Send Request") { _, _ ->
                val v = input.text.toString().trim().toDoubleOrNull()
                if (v == null || v <= 0) { android.widget.Toast.makeText(this, "Enter a valid amount", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            val repo = PaymentRepository(this@ChamberAttendanceActivity)
                            val patientUuid = p.s("patientId")
                            val patient = PatientBillInfo(
                                id = patientUuid.ifBlank { id }, name = p.s("name"),
                                mobile = p.s("mobile"), branch = p.s("branch"),
                                patientId = "", bill = 0.0, paid = 0.0, billLocked = false
                            )
                            repo.requestPaymentEdit(
                                id, patient, oldAmount, v, p.s("mode").ifBlank { "CASH" }, payDate,
                                reason.text.toString().trim(), user.mobile, user.name.ifBlank { user.mobile }
                            )
                        } catch (_: Throwable) { false }
                    }
                    android.widget.Toast.makeText(
                        this@ChamberAttendanceActivity, NoBengali.s(if (ok) "Master-এর কাছে অনুরোধ পাঠানো হয়েছে ✅" else "পাঠানো যায়নি — আবার চেষ্টা করুন"),
                        android.widget.Toast.LENGTH_LONG
                    ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                }
            }
            .setNegativeButton("Cancel", null)
            // 🔒 খাতার সারি B181: এই ডায়ালগের নিজের টাইটেল/লেবেলের পাহারা ছিল না।
            .show().also { PremiumAlert.paint(it) }
    }

    /** TK-REQUESTED CHANGE (2026-07-19): "Print" now means a real A4 PDF,
     *  matching TK's own paper attendance register exactly (SL | Patient
     *  Details | Status | Fees | Cash | Online), opened straight into
     *  Android's native Print dialog -- not the old plain-text share. Uses
     *  the SAME branch-specific clinic name/logo/address every other
     *  printout in this app uses (BranchCatalog), so the header matches.
     *  Built in a completely separate file (ChamberRegisterPdfBuilder.kt);
     *  ClinicPdfBuilder.kt (OWNER LOCKED, prescriptions etc.) is untouched. */
    // TK-REQUESTED (2026-07-21): the patient's visit ordinal for the printed
    // register's VISIT column. Built from the SAME timeline the Patient/Report
    // cards use (max visitNo). Runs at print time only (a one-off action, not a
    // refresh loop), and falls back to NEW/OLD if the lookup fails.
    // TK-REPORTED (2026-07-27, "slow internet" list item S1) -- THE WORST ONE.
    //
    // WHAT WAS WRONG (in TK's words: "Confirm & Print চাপার পর ফোন মরার মতো
    // লাগে")
    // This asked the cloud for ONE PATIENT'S ENTIRE TIMELINE, then the next
    // patient's, then the next -- strictly one after another. Each timeline is
    // itself four-plus cloud reads. A normal chamber day of 30 arrived
    // patients therefore meant well over a hundred cloud trips in a queue
    // before the print sheet could even start being built. On a slow line
    // that is minutes with NOTHING happening on screen.
    //
    // WHAT IS DIFFERENT NOW -- and what is NOT
    //  * The visit number is worked out from EXACTLY the same source as
    //    before (PatientTimelineRepository.build -> highest visitNo), so a
    //    printed VISIT cell says exactly what it said before. Nothing about
    //    the register, its columns, its design or its order is touched.
    //  * Those same lookups are simply sent a few at a time instead of one at
    //    a time, so the waiting is a fraction of what it was.
    //  * A total time budget is added. If the connection is so bad that the
    //    lookups still cannot finish, the remaining patients quietly use the
    //    SAME NEW/OLD fallback this function has always used on failure --
    //    so printing ALWAYS goes ahead and the chamber is never held up.
    //    Without this, a bad line could hold the print back indefinitely,
    //    which is the exact complaint being fixed.
    /** Chamber ইতিমধ্যে বন্ধ। Register print এখন সম্পূর্ণ ঐচ্ছিক। */
    private fun offerOptionalRegisterPrint(board: ChamberAttendanceBoard, cloudSaved: Boolean) {
        val syncNote = if (cloudSaved) "" else "\n\nCloud sync pending — it will save automatically when internet returns."
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "✅ Chamber Closed"))
            .setMessage("Review confirmed and chamber closed.$syncNote\n\nPrint the register now?")
            .setPositiveButton("Print") { _, _ ->
                lifecycleScope.launch {
                    val visitLabels = withContext(Dispatchers.IO) { computeVisitLabels(board) }
                    // 🆕 V805 — ওষুধ/স্যালাইনের মোট, ব্যাকগ্রাউন্ডেই (ব্যর্থ হলে শূন্য)
                    val st = try { withContext(Dispatchers.IO) {
                        ChamberAttendanceRepository.saleTotals(selectedDate, printBranchOverride.ifBlank {
                            if (selectedBranch != "All") selectedBranch else user.branch })
                    } } catch (_: Throwable) { ChamberRegisterPdfBuilder.SaleTotals() }
                    if (!isFinishing && !isDestroyed) finalizeAndShare(board, visitLabels, st)
                }
            }
            .setNegativeButton("Not Now", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun computeVisitLabels(board: ChamberAttendanceBoard): Map<String, String> {
        val rows = board.rows.filter { it.arrived }
        val out = HashMap<String, String>()
        // Everyone starts on the long-standing fallback, so no patient can
        // ever end up with a blank VISIT cell.
        for (r in rows) {
            out[r.mobile] = if (r.whatHappened.contains("New Registration")) "NEW" else "OLD"
        }
        if (rows.isEmpty()) return out

        // 60 seconds for the WHOLE group of lookups (not per patient).
        val budgetMs = 60_000L
        val deadline = System.currentTimeMillis() + budgetMs
        val labels = ParallelCloud.map(rows) { r ->
            if (System.currentTimeMillis() > deadline) {
                null   // out of time -- keep this patient's NEW/OLD fallback
            } else {
                try {
                    val n = PatientTimelineRepository.build(r.mobile, null, this).entries
                        .maxOfOrNull { it.visitNo } ?: 0
                    if (n > 0) ordinalVisit(n) else null
                } catch (_: Throwable) { null }
            }
        }
        for (i in rows.indices) {
            val label = labels.getOrNull(i)
            if (label != null) out[rows[i].mobile] = label
        }
        return out
    }

    private fun ordinalVisit(n: Int): String {
        val s = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$s Visit"
    }

    /* 🆕🔒 V805 (২৮.০৮.২০২৬, TK-অনুমোদিত) — কাগজে ওষুধ ও স্যালাইনের টাকা
       **আলাদা দুটো লাইনে শুধু দেখানোর** জন্য (TK-এর নিজের সিদ্ধান্ত: GRAND
       TOTAL-এ যোগ হবে না)।
       ⛔ **নিজের যাচাইয়ে ধরা পড়া ভুল, লেখার সময়ই সারানো:** প্রথমে এই
          ফাংশনের ভিতরেই নেট-পড়াটা বসিয়েছিলাম — কিন্তু এই ফাংশনটা **মূল
          থ্রেডে** চলে (`withContext(Dispatchers.IO)` শুধু `computeVisitLabels`-কে
          ঘিরে আছে, তারপরই মূল থ্রেডে ফিরে আসে)। ওখানে নেট-পড়া দিলে
          `NetworkOnMainThreadException` — অ্যাপ ক্র্যাশ করত। তাই এখন হিসাবটা
          **ডাকার জায়গার IO-ব্লকেই** হয়ে যায়, আর এখানে শুধু তৈরি সংখ্যাটা আসে। */
    private fun finalizeAndShare(
        board: ChamberAttendanceBoard,
        visitLabels: Map<String, String> = emptyMap(),
        saleTotals: ChamberRegisterPdfBuilder.SaleTotals = ChamberRegisterPdfBuilder.SaleTotals()
    ) {
        // TK-REQUESTED CHANGE (2026-07-19): print order is chronological
        // (earliest arrivedAt first), not the on-screen board's alphabetical
        // order -- NEW and OLD patients end up interleaved in the actual
        // sequence they happened, like a real paper register. Blank
        // timestamps (rare edge case) sort last, not first.
        val arrived = board.rows.filter { it.arrived }
            .sortedBy { it.arrivedAt.ifBlank { "9999" } }
        try {
            // TK-APPROVED (2026-07-26): header follows the branch the person
            // actually picked in the popup above; only when nothing was picked
            // does it fall back to the old behaviour.
            val headerBranch = when {
                printBranchOverride.isNotBlank() -> printBranchOverride
                selectedBranch != "All" -> selectedBranch
                else -> user.branch
            }
            val branchInfo = com.tkbiswas.pilesclinic.print.BranchCatalog.byName(headerBranch)
            val cal = java.util.Calendar.getInstance()
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
                if (parsed != null) cal.time = parsed
            } catch (_: Exception) { }
            val dateLabel = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(cal.time)
            val dayLabel = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
            val registerRows = arrived.mapIndexed { i, r ->
                ChamberRegisterPdfBuilder.RegisterRow(
                    sl = i + 1,
                    // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে Mobile কলামেও
                    // একই নম্বর — প্রিন্ট হওয়া রেজিস্টারে দুইবার দেখাত।
                    name = r.name.ifBlank { "UNKNOWN" }.uppercase(),
                    mobile = r.mobile,
                    patientId = r.patientId,
                    newOrOld = if (r.whatHappened.contains("New Registration")) "NEW" else "OLD",
                    fees = r.feesCash + r.feesOnline,
                    feesCash = r.feesCash, feesOnline = r.feesOnline,           // 🟢🔒 V612
                    medicineCash = r.medicineCash, medicineOnline = r.medicineOnline,   // 🟢🔒 V612
                    cash = r.paymentCash,
                    online = r.paymentOnline,
                    // TK-REQUESTED (2026-07-25): at end-of-day Close
                    // Chamber Save & Print, any arrived patient whose
                    // Treatment Progress is STILL blank now gets a visible
                    // pending-marker in the saved register instead of a
                    // silent blank -- so it's obvious at a glance which of
                    // today's patients still need their note written, even
                    // after the day is closed. Completely unrelated to
                    // Payment -- this only looks at whether r.remark itself
                    // is empty, nothing else.
                    // 🔒 TK-এর স্থায়ী নিয়ম (29.07.2026, খাতার সারি B74): **ছাপা
                    // কাগজে কখনো বাংলা যাবে না** (একমাত্র Diet Chart ছাড়া)।
                    // `PrintTextEnglish.forPrint()` শুধু **জানা দ্রুত-চিপ**গুলোর
                    // বাংলা লেখা ইংরেজি করে দেয়; স্টাফের নিজের হাতে লেখা কথা
                    // হুবহু অক্ষত থাকে (TK-এর সিদ্ধান্ত)। ⛔ ডেটাবেসে কিছু
                    // বদলায় না — শুধু কাগজে ছাপার আগে লেখাটা বদলে নেওয়া হয়।
                    // নিচের ফাঁকা-ঘরের লেখাটা শুধু কাগজেই যায়, পর্দায় নয়।
                    // 🔴 V810 — পুরনো দিনের লেখা আর আজকের নোট সেজে ছাপা হবে না
                    treatment = if (todaysProgressMissing(r.remark, r.remarkUpdatedAt)) "⚠️ PROGRESS PENDING"
                        else com.tkbiswas.pilesclinic.print.PrintTextEnglish.forPrint(r.remark).ifBlank { "⚠️ PROGRESS PENDING" },
                    visitLabel = visitLabels[r.mobile] ?: "",
                    // TK-REQUESTED (2026-07-22): mode the registration/doctor-visit
                    // fee itself was paid in, so the print can show CASH/UPI
                    // instead of a misleading "1st Visit" label on that row.
                    /* 🟢🔒 V588 (23.08.2026, TK-নির্দেশ) — TK নিজে দুটো শব্দ বললেন:
                       *"Cash এর পাশে থাকবে amount · Online এর পাশে থাকবে এমাউন্ট"*।
                       কাগজে এতদিন অনলাইনের ঘরে "UPI" ছাপা হত, অথচ কলামের নামই
                       "ONLINE" আর Review পর্দাতেও "Online" — তিন জায়গায় তিন কথা।
                       এখন সবখানে এক: CASH / ONLINE।
                       ⛔ কোন উপায়ে টাকা এসেছে সেই হিসাব একটুও বদলায়নি (আগের মতোই
                          feesCash / feesOnline ধরেই ঠিক হয়) — শুধু ছাপার শব্দটা। */
                    feesMode = if (r.feesCash > 0.0) "CASH" else if (r.feesOnline > 0.0) "ONLINE" else ""
                )
            }
            // TK-REPORTED FIX (2026-07-26): "Share failed: Failed to find
            // configured root that contains ..." . the register PDF was
            // written straight into cacheDir, and res/xml/file_paths.xml only
            // exposes cache/pdfs, cache/images and cache/exports to
            // FileProvider. So Share PDF could never build a content:// URI
            // for it. It now goes into the SAME cache/pdfs folder that every
            // other print document already uses. Save PDF / Print / preview
            // are unaffected (they use the file path directly).
            val pdfDir = File(cacheDir, "pdfs").apply { mkdirs() }
            val outFile = File(pdfDir, "chamber_register_${selectedDate}.pdf")
            // 🔴 V426: Review পর্দায় যে RMP কমিশনটা দেখানো হয়েছিল, ছাপা কাগজের
            //    নিচের লাইনেও **হুবহু সেটাই** যায় (আলাদা করে আবার হিসাব করা হয়
            //    না, তাই পর্দা আর কাগজের সংখ্যা কখনো আলাদা হবে না)। ০ হলে কাগজ
            //    আগের মতোই ছাপে।
            ChamberRegisterPdfBuilder(this).build(
                branchInfo, dateLabel, dayLabel, registerRows, outFile,
                cbDayCommissionTotal, cbDayPaidTotal, cbDayCommissionByRmp, saleTotals
            )
            // TK-REQUESTED (2026-07-25): Save PDF / Share PDF (WhatsApp etc.)
            // / Print -- reusing the SAME already-built, already-working
            // preview screen every other document (Prescription,
            // Registration...) already uses, instead of jumping straight
            // to the system print dialog with no way to save or share.
            com.tkbiswas.pilesclinic.print.PrintDataHolder.prebuiltFile = outFile
            com.tkbiswas.pilesclinic.print.PrintDataHolder.prebuiltTitle = "Chamber Register - $dateLabel"
            startActivity(android.content.Intent(this, com.tkbiswas.pilesclinic.print.PrintPreviewActivity::class.java))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, NoBengali.s("PDF তৈরি করা যায়নি — ${e.javaClass.simpleName}"), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ---- small premium-dialog helpers, same visual pattern as
    // PaymentActivity's "Add Treatment Payment" dialog, reused here for
    // visual consistency across the app. ----

    private data class ChamberDialogParts(val dialog: AlertDialog, val body: android.widget.LinearLayout, val actionRow: android.widget.LinearLayout)

    private fun premiumDialogShellChamber(titleText: String): ChamberDialogParts {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f * d
                setStroke(dp(1), android.graphics.Color.parseColor("#0F5C5C"))
            }
        }
        root.addView(android.widget.TextView(this).apply {
            text = titleText
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#0F5C5C"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
        val body = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(4))
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(body)
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(400))
        }
        root.addView(scroll)
        val actionRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(actionRow)
        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // 🔒 খাতার সারি B158: বাংলা বন্ধ থাকা স্টাফের জন্য এই পপ-আপের লেখাও
        // বাংলা-মুক্ত করা হয়। পপ-আপ দেখানোর পরেই ডাকা হয়, তখনই ভিউগুলো তৈরি
        // থাকে। ⛔ বাংলা বন্ধ না থাকলে এটা কিছুই করে না।
        dialog.setOnShowListener { try { NoBengali.installDialog(dialog) } catch (_: Throwable) { } }
        return ChamberDialogParts(dialog, body, actionRow)
    }

    private fun pillButtonChamber(text: String, bg: String, textColor: Int = android.graphics.Color.WHITE): android.widget.Button {
        val d = resources.displayMetrics.density
        return android.widget.Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13.5f
            setTextColor(textColor)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor(bg))
                cornerRadius = 12f * d
            }
            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = (6 * d).toInt(); lp.marginEnd = (6 * d).toInt()
            layoutParams = lp
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        }
    }

    // TK-REQUESTED (2026-07-21): "Action" card shows all dashboard menu items
    // (Enquiry, Follow-up, Registration, CHECK-UP, Payment, Print Center, Dr. Visit,
    // Draft, Briefing, Global Search, + Master-only: Reports, Export, Backup, Password, Trash Bin).
    // Triggered by tapping the "Action" card in the stat row.
    private fun showActionMenu() {
        val actions = listOfNotNull(
            Pair("📝 Enquiry", EnquiryActivity::class.java),
            Pair("🔁 Follow-up", FollowUpActivity::class.java),
            Pair("🧾 Registration", RegistrationActivity::class.java),
            Pair("🩺 CHECK-UP", DoctorQueueActivity::class.java),
            Pair("💰 Payment", PaymentActivity::class.java),
            Pair("🖨️ Print Center", com.tkbiswas.pilesclinic.print.PrintCenterActivity::class.java),
            Pair("👨‍⚕️ Dr. Visit", DoctorVisitActivity::class.java),
            Pair("📂 Draft", DraftActivity::class.java),
            Pair("💬 Briefing", BriefingActivity::class.java),
            Pair("🔍 Global Search", GlobalSearchActivity::class.java),
            if (user.role == "master") Pair("📊 Reports", ReportsActivity::class.java) else null,
            if (user.role == "master") Pair("📋 Export Data", ExportDataActivity::class.java) else null,
            if (user.role == "master") Pair("☁️ Backup", com.tkbiswas.pilesclinic.security.SettingsActivity::class.java) else null,
            if (user.role == "master") Pair("🔐 Password", PasswordCenterActivity::class.java) else null,
            if (user.role == "master") Pair("🗑️ Trash Bin", TrashBinActivity::class.java) else null
        )
        val actionNames = actions.map { it.first }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this).setCustomTitle(PremiumAlert.header(this, "All work / next work")).setItems(actionNames) { _, which ->
            try {
                startActivity(android.content.Intent(this, actions[which].second))
            } catch (_: Throwable) { }
        }.setNegativeButton("Close", null).show().also { PremiumAlert.paint(it) }
    }

}
