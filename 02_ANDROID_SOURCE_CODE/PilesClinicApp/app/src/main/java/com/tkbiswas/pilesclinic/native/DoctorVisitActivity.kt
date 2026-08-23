package com.tkbiswas.pilesclinic.native

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tkbiswas.pilesclinic.databinding.ActivityDoctorvisitBinding
import com.tkbiswas.pilesclinic.modules.ModuleAuth
import com.tkbiswas.pilesclinic.modules.ModuleUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume   // 🔵 V530
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native rebuild -- Doctor Visit / RMP.
 *
 * Lists doctor/RMP contacts (branch-scoped, same rule as Follow-up/Payment:
 * staff see only their branch, Master sees all), lets staff add a new
 * contact and log a call (remark + next call date), matching
 * saveVisit()/saveDoctorCall() exactly, including the call-history log.
 *
 * SCOPED LIMITATIONS for this step (same honest-disclosure pattern as
 * every step before it):
 * - Referral patient tracking and referral payment income
 *   (doctorReferralPatients()/doctorReferralTotals()) is not shown here --
 *   this screen covers the day-to-day call-tracking workflow (add contact,
 *   call, log remark, see who's due), which is what staff use most often.
 * - "This Month Called" / "Staff Call Summary" filtered views are not
 *   included -- only the full list, sorted the same way (due-first) the
 *   WebView's default view already sorts it.
 * - The cross-branch "needs approval" confirmation warning
 *   (isOtherBranchRecord() check in the WebView) is not shown here --
 *   since the branch filter already limits non-Master staff to their own
 *   branch's contacts, this specific scenario shouldn't come up in normal
 *   use of this screen; noted for completeness rather than silently
 *   assumed to be unreachable.
 */
class DoctorVisitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorvisitBinding

    /**
     * 🔵🔒 V530 (২২.০৮.২০২৬, TK-নির্দেশ) — **ডাক্তারের পর্দাতেও ঠিক রোগীটিই।**
     *
     * **আগে যা হত:** এই পর্দার পাঁচ জায়গায় নম্বর দিয়ে রোগী খুঁজে **প্রথম
     * সারিটাই** ধরে নেওয়া হত। এক নম্বরে স্বামী-স্ত্রী দু'জন আলাদা রোগী থাকলে
     * ভুল জনের নাম বসে যেত — প্রেসক্রিপশন, রেফারেল-আয়, সবই ভুল নামে।
     *
     * **এখন:** ওই নম্বরে সত্যিই একাধিক আলাদা রোগী থাকলে **তখনই** এক লাইনে
     * জিজ্ঞাসা করা হয়।
     *
     * ⛔ একজন থাকলে (রোজকার ৯৯%) **এই বাক্স কখনো দেখা যায় না** এবং ফল হুবহু
     *    আগের মতোই — `findByMobile` সেই একই প্রথম সারিটাই ফেরায়।
     * ⛔ রোগী না পেলে আগের মতোই `null` — ডাকার জায়গার বার্তা এক অক্ষরও বদলায়নি।
     * ⛔ স্টাফ *Cancel* করলেও `null` — অর্থাৎ "রোগী মেলেনি"-র সেই একই পথ,
     *    ভুল রোগীর নামে কিছু সেভ হওয়ার চেয়ে যা অনেক নিরাপদ।
     */
    private suspend fun findVisitPatient(digits: String): PatientPhotoRepository.PatientRef? {
        val repo = PatientPhotoRepository()
        val people = withContext(Dispatchers.IO) {
            try { repo.identitiesByMobile(digits) } catch (_: Throwable) { emptyList() }
        }
        if (people.size >= 2) {
            val chosen = askWhichVisitPatient(digits, people) ?: return null
            return withContext(Dispatchers.IO) {
                repo.findByMobile(digits, chosen.id, chosen.patientId)
            }
        }
        // ⛔ ০ বা ১ জন ⇒ হুবহু আগের সেই এক ডাক।
        return withContext(Dispatchers.IO) { repo.findByMobile(digits) }
    }

    private suspend fun askWhichVisitPatient(
        mobile: String, people: List<PatientPhotoRepository.PatientRef>
    ): PatientPhotoRepository.PatientRef? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val labels = people.map { p ->
                (p.name.ifBlank { "UNKNOWN" }) + "\n" + (p.patientId.ifBlank { "-" })
            }.toTypedArray()
            var done = false
            fun finishWith(v: PatientPhotoRepository.PatientRef?) {
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
                .show()
        }
    private lateinit var repository: DoctorVisitRepository
    private lateinit var adapter: DoctorVisitAdapter
    private lateinit var user: NativeUser

    private val branches = listOf("Kishanganj", "Jalpaiguri", "Cooch Behar", "Falakata", "Birpara")

    // V450 (19.08.2026, TK-approved): tiny in-memory cache for RMP View All.
    // ⛔ Patient/referral/financial details are NOT written to SharedPreferences/disk.
    // The cache exists only while this Doctor Visit activity is alive and therefore
    // has zero Supabase/storage quota cost. A fresh cloud refresh still runs on open.
    private data class CallLine(val rawDate: String, val date: String, val note: String, val by: String)
    private data class ReferredPatient(val rawDate: String, val name: String, val mobile: String, val bill: Double, val paid: Double, val patientId: String = "", val disease: String = "")
    private data class RefIncomeLine(val rawDate: String, val patient: String, val mobile: String, val amount: Double, val status: String, val date: String, val id: String = "", val mode: String = "", val referenceNo: String = "")
    private data class ViewAllData(
        val calls: List<CallLine>,
        val referred: List<ReferredPatient>,
        val refIncome: List<RefIncomeLine>,
        val refPaid: Double,
        val refDue: Double
    )
    private val doctorViewAllMemoryCache = android.util.LruCache<String, ViewAllData>(24)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorvisitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        BottomNav.wire(this)
        repository = DoctorVisitRepository()

        val session = NativeSession.current(this)
        if (session == null) { finish(); return }
        user = session

        // Only Master, Field Officer, or Staff can use this module, matching
        // doctorVisit()'s role check in app.js.
        // 🔴 (07.08.2026 · রাত ~১২:৩০) **এই মডিউলটা আসলে কী — TK-এর নিজের ভাষায়:**
        // *"Doctor Visit বলতে গ্রামে গিয়ে যে সমস্ত ডাক্তারের কাছে ভিজিট করা হয়,
        // তাঁরা পেশেন্ট পাঠাবেন — সেটা।"* অর্থাৎ এটা বাইরের **রেফারিং ডাক্তারদের**
        // কাছে ভিজিটের কাজ, ক্লিনিকের ডাক্তারের নিজস্ব পাতা নয় (আমি একবার ভুল
        // বুঝেছিলাম, TK ধরিয়ে দিয়েছেন — খাতায় লেখা রইল যাতে ভবিষ্যতে ভুল না হয়)।
        //
        // 🔵 (08.08.2026) — এখানে একবার `"doctor"` যোগ করা হয়েছিল, কারণ ভাবা
        // হয়েছিল ডাক্তার এই পর্দা খুলতে পারেন না। **সেটা ভুল ছিল:** এই অ্যাপে
        // ডাক্তার/ফিল্ডের `role` ভিতরে **"staff"**-ই থাকে (`permissionRole()`),
        // তাই ডাক্তার আগে থেকেই এই গেট পার হতেন — বাড়তি কিছু লাগত না।
        // ⛔ তাই লাইনটা হুবহু আগের অবস্থায় ফিরিয়ে দেওয়া হলো (অকারণে অনুমতির
        //    নিয়ম বদলে রাখা ঠিক নয়)।
        if (!(user.role == "master" || user.role == "field" || user.role == "staff")) {
            Toast.makeText(this, "Staff / Field Officer / Master Admin only", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = DoctorVisitAdapter(
            this, emptyList(), user,
            onCall = { pendingCallItem = it; callNumber(it.mobile) },
            onWhatsApp = { openWhatsApp(it.mobile) },
            onLogCall = { showLogCallDialog(it) },
            onViewAll = { showDoctorViewAll(it) },
            onEdit = { showDoctorEdit(it) },
            onEditRemark = { showRemarkOnlyEdit(it) },
            onDeleteTap = { confirmDeleteDoctor(it) },
            onApproveDelete = { handleApproveDelete(it) },
            onRejectDelete = { handleRejectDelete(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        // TK-REQUESTED (2026-07-27): pull the list down to refresh -- the same
        // gesture as Follow-up, on every section. It runs the screen's OWN
        // existing load, so no new query and no rule changes; the little circle
        // stops on its own when that load has had time to finish.
        binding.swipeRefresh.setColorSchemeColors(
            android.graphics.Color.parseColor("#0EA25F"),
            android.graphics.Color.parseColor("#1167D8")
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadList()
            binding.swipeRefresh.postDelayed({
                try { binding.swipeRefresh.isRefreshing = false } catch (_: Throwable) {}
            }, 2500L)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener { showAddDoctorDialog() }

        binding.statPending.setOnClickListener { currentFilter = "pending"; renderList() }
        binding.statAll.setOnClickListener { currentFilter = "all"; renderList() }
        binding.statCalled.setOnClickListener { currentFilter = "called"; renderList() }
        binding.statCalled.setOnLongClickListener { showCallSummary(); true }
        // 🔒 খাতার সারি B193 (TK, 30.07.2026 রাত): এই বক্সটাই এখন "EXPECTED" —
        // আগে ছিল "today" (নেক্সট কল আজ), এখন expectedPatientDate সেট করা
        // ডাক্তারদের তালিকা দেখায়।
        binding.statToday.setOnClickListener { currentFilter = "expected"; renderList() }
        // 🔒 V233 (TK verified live-date fix, 01.08.2026): "EXPECTED" কার্ডের আগের
        // একলা "⏰" ইমোজি কিছু Android emoji-ফন্টে fixed "Jul 17" এঁকে দেখাত।
        // এখন Follow-up পর্দার হুবহু একই কোডে ২-লাইনের live badge-এ আজকের
        // (device-local = IST) মাস ও দিন বসে — উদাহরণ: "Aug"/"1"। পর্দা খোলার
        // সময়েই বসে, তাই তারিখ প্রতিদিন নিজে থেকে ঠিক থাকে।
        // ⛔ শুধু date display; EXPECTED count/filter/click কিছুই বদলায়নি।
        run {
            val cal = java.util.Calendar.getInstance()
            binding.tvDvCalMonth.text = java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH).format(cal.time)
            binding.tvDvCalDay.text = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
        }
        binding.etSearch.addTextChangedListener { text ->
            searchQuery = text?.toString() ?: ""
            renderList()
        }

        // TK-REQUESTED (2026-07-18): Master Admin must pick ONE branch to see
        // its RMP list — no default combined "All branches mixed together"
        // view. Dropdown starts on a "Select Branch" placeholder; list stays
        // empty until Master actually picks a branch. Staff/Field Officer are
        // already branch-locked server-side (see DoctorVisitRepository.fetchList),
        // so this dropdown stays hidden for them — no change to their screen.
        if (user.role == "master") {
            val branchOptions = listOf("Select Branch") + branches
            binding.spinnerBranchFilter.adapter = android.widget.ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item, branchOptions
            )
            // 🔒 TK-APPROVED (29.07.2026, খাতার সারি B84): ব্রাঞ্চ বাছার ঘর এখন
            // হেডারের ডানের পিলে — সব পর্দার এক মডেল। পুরনো Spinner মোছা হয়নি,
            // লুকানো; নিচের নিয়ম সেটাই চালায়, পিল শুধু তাকে বেছে দেয়।
            // ⛔ মাস্টারকে আগের মতোই একটা ব্রাঞ্চ বাছতে হবে — তার আগে তালিকা
            // ফাঁকাই থাকে (TK-এর 18.07.2026-এর নিয়ম অক্ষত)।
            binding.branchPicker.visibility = View.VISIBLE
            // 🟢🔒 V398 (16.08.2026, TK-অনুমোদিত): TK-এর সিদ্ধান্ত অনুযায়ী এই পর্দায়
            //   "All" যোগ করা হয়নি — আগের মতোই একটা নির্দিষ্ট ব্রাঞ্চ বাছতে হয়।
            //   ⛔ শুধু এটুকু বদলাল: শেষবার বাছা ব্রাঞ্চ মনে থাকে, তাই বারবার
            //   বাছতে হয় না। মনে-রাখা মান "All" বা ফাঁকা হলে আগের মতোই
            //   "Select Branch" ও ফাঁকা তালিকা।
            val __v398Remembered = BranchFilterStore.get(this)
            branchFilter = if (__v398Remembered.isNotBlank() && __v398Remembered != BranchFilterStore.ALL)
                __v398Remembered else BRANCH_NONE
            binding.branchPicker.text =
                "🏥 " + (if (branchFilter == BRANCH_NONE) "Select Branch" else branchFilter) + " ▾"
            if (branchFilter != BRANCH_NONE) {
                val __i = branchOptions.indexOf(branchFilter)
                if (__i > 0) binding.spinnerBranchFilter.setSelection(__i)
            }
            binding.spinnerBranchFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    branchFilter = if (position == 0) BRANCH_NONE else branchOptions[position]
                    BranchFilterStore.set(this@DoctorVisitActivity,
                        if (branchFilter == BRANCH_NONE) "" else branchFilter)   // 🟢 V398
                    binding.branchPicker.text = "🏥 " + branchOptions[position] + " ▾"
                    renderList()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            // 🔒 খাতার সারি B84 — Follow-up-এর হুবহু একই পপ-আপ।
            binding.branchPicker.setOnClickListener {
                val now = binding.spinnerBranchFilter.selectedItemPosition
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setCustomTitle(PremiumAlert.header(this, "Branch"))
                    .setSingleChoiceItems(branchOptions.toTypedArray(), now) { dialog, which ->
                        binding.spinnerBranchFilter.setSelection(which)
                        // 🚨🚨 TK-REPORTED (29.07.2026 সন্ধ্যা ৬.২৩, ছবিসহ · খাতার সারি B126):
                        // *"Branch Open কেন হচ্ছে না?"* — পপ-আপে Kishanganj বাছার পরেও
                        // পিলে "Select Branch" লেখাই থেকে যেত ও তালিকা আসত না।
                        //
                        // **আসল কারণ (কোড ধরে, আন্দাজ নয়):** পুরনো Spinner-টা লেআউটে
                        // `visibility="gone"`। Android-এ **GONE ভিউ কখনো layout হয় না**,
                        // আর Spinner-এর `onItemSelected` **layout-এর সময়েই** ডাকা হয় —
                        // তাই `setSelection(which)` লেখা সত্ত্বেও শোনার কোডটা কোনোদিন
                        // চলত না। অর্থাৎ বাছাইটা হারিয়ে যেত।
                        //
                        // **ওষুধ:** বাছাইয়ের কাজটা এখানেই সরাসরি করা হয় (Follow-up-এর
                        // পপ-আপে যেভাবে হয়) — Spinner-টা আগের মতোই সেট করা থাকে, তাই
                        // পুরনো নিয়মের কোনো কিছু বদলায়নি, শুধু আর তার ভরসায় বসে থাকা হয় না।
                        branchFilter = if (which == 0) BRANCH_NONE else branchOptions[which]
                        BranchFilterStore.set(this@DoctorVisitActivity,
                            if (branchFilter == BRANCH_NONE) "" else branchFilter)   // 🟢 V398
                        binding.branchPicker.text = "🏥 " + branchOptions[which] + " ▾"
                        // V357: Master আগে সব ব্রাঞ্চ একসঙ্গে নামিয়ে পরে এই
                        // ব্রাঞ্চ আলাদা করত। Cloud response মাঝপথে সীমিত হলে
                        // Jalpaiguri-র 700-এর মধ্যে 454-ই আসত। এখন নির্বাচিত
                        // ব্রাঞ্চটাই সরাসরি Cloud থেকে আনা হয়—Staff-এর একই
                        // প্রমাণিত পথ; কম data, Free Plan-এও নিরাপদ।
                        loadList()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show().also { PremiumAlert.paint(it) }
            }
        }

        // 🔒 TK-ORDER (31.07.2026, খাতার সারি B211 — TK: "এটা শুধু মাস্টারই
        // দেখতে পারবে, বাকি কারোর দেখার দরকার নেই, স্টাফ হোক ডাক্তার হোক বা
        // ফিল্ড অফিসার হোক"): নতুন বাটন সম্পূর্ণ Master-only — অন্য কোনো রোলের
        // জন্য এই বাটন কখনো দেখা যাবে না (লেআউটে ডিফল্ট gone, এখানে শুধু
        // মাস্টার হলে VISIBLE করা হয়)।
        if (user.role == "master") {
            binding.btnRmpPerformance.visibility = View.VISIBLE
            binding.btnRmpPerformance.setOnClickListener { showRmpPerformanceReport() }
        }

        // 🟢🔒 B685 (15.08.2026, TK-অনুমোদিত — TK: "কোন আরএমপির কত টাকা ডিউ
        // আছে এটাতো স্টাফ অথবা ব্রাঞ্চ দেখে আমাকে বলবে ... তারা তো দেখে
        // আমাকে বলবে যে স্যার এই আরএমপির এত টাকা বাকি আছে"): নতুন
        // "💰 RMP Due List" — নিজের ব্রাঞ্চের যাদের এখনো টাকা বাকি, শুধু
        // তারাই এক তালিকায়, সবচেয়ে বেশি বাকি সবার উপরে।
        // ⛔ B211 ভাঙা হয়নি — 🏆 RMP Performance Report আগের মতোই কেবল
        //    Master-এর, এটা সম্পূর্ণ আলাদা একটা বোতাম যেটা TK আজ নিজে
        //    staff+doctor-এর জন্য চেয়েছেন (ছবি দেখে "খ" জায়গা বেছেছেন)।
        // ⛔ Field Officer-কে ইচ্ছে করেই বাদ রাখা হলো (B211-এর টাকার
        //    গোপনীয়তার সুরে) — TK চাইলে এক লাইনে যোগ করা যাবে।
        // ⚠️ ফাঁদ (এই ফাইলের ৮৪-৮৮ লাইনেই লেখা আছে): `permissionRole()`
        //    ডাক্তার **এবং ফিল্ড অফিসার** দুজনকেই ভিতরে "staff" বানায়।
        //    তাই শুধু role দেখে ফিল্ড অফিসারকে আটকানো যায় না — প্রজেক্টে
        //    আগে থেকে থাকা `ModuleAuth.personCode == "FIELD-OFFICER"`
        //    পরীক্ষাটাই (এই ফাইলের ৩৭৬১ লাইনে যেমন) ব্যবহার করা হলো।
        val isFieldOfficer = ModuleAuth.personCode == "FIELD-OFFICER"
        if ((user.role == "master" || user.role == "staff") && !isFieldOfficer) {
            binding.btnRmpDueList.visibility = View.VISIBLE
            binding.btnRmpDueList.setOnClickListener { showRmpDueList() }
        }

        // TK-REQUESTED ADDITION (2026-07-18): deep-link from Patient
        // Timeline's "Referring Doctor" action. We don't know which branch
        // the referring doctor belongs to, so this bypasses the
        // "Select Branch first" requirement above (Master only, since
        // Staff was never branch-locked-out to begin with) and searches
        // straight away by mobile — same search box, same renderList(),
        // nothing else about this screen changes.
        // 🔴🔒 V410 (TK-রিপোর্ট, ছবিসহ, ১৭.০৮.২০২৬) — **আসল দোষ ধরা পড়ল।**
        //    TK নোটিফিকেশন থেকে একটা নম্বরে চাপ দিলেন। উপরে লেখা "Falakata",
        //    অথচ ঘরগুলো দেখাল ১০০১ / ৭০৬ / ২৯৫, আর তালিকায় "No doctors found"।
        //    ফালাকাটায় RMP আছেন মোটে ২ জন।
        //
        //    **কারণ (কোড ও লাইভ ডেটা ধরে যাচাই করা, আন্দাজ নয়):**
        //    নিচের পুরনো কোড `branchFilter = ""` বসাত — মানে "সব ব্রাঞ্চে খোঁজো"।
        //    তখন `activeDoctorBranch()` null হয় ⇒ সার্ভার থেকে **সব ব্রাঞ্চের
        //    ১,৯৩৮ জন** চাওয়া হয়। কিন্তু সার্ভার একবারে ~১,০০০ সারির বেশি দেয় না,
        //    আর `fetchListRawOrNull()` `order` পাঠায় না ⇒ ডিফল্ট `updatedAt.desc`।
        //    ফলে **সবচেয়ে সম্প্রতি বদলানো ১,০০০ জনই** আসত, বাকি ৯৩৮ জন অদৃশ্য।
        //    AJIT BARMAN-এর সারি শেষ বদলেছিল ১৮ জুলাই — তাই তিনি বাদ পড়তেন।
        //    (একই সমস্যা খাতায় V357-এও লেখা: "Jalpaiguri-র 700-এর মধ্যে 454-ই আসত"।)
        //    আর পিলে আগের মনে-রাখা ব্রাঞ্চের নামটা রয়ে যেত ⇒ **লেবেল মিথ্যে বলত।**
        //
        //    **সমাধান:** ডাকা পর্দা যদি ডাক্তারের ব্রাঞ্চ জানে (নোটিফিকেশন জানে),
        //    সেটা `searchBranch`-এ পাঠায় ⇒ সরাসরি ওই ব্রাঞ্চ বেছে নেওয়া হয়। তখন
        //    এক ব্রাঞ্চের সারিই নামে (সর্বোচ্চ ৮২১ — সীমার অনেক নিচে), ডাক্তার
        //    পাওয়া যায়, ঘরের সংখ্যা সত্যি হয়, আর ১,৯৩৮ সারি টানার দরকারই পড়ে না
        //    ⇒ **Egress-ও কমে**। ব্রাঞ্চ জানা না থাকলে আগের মতোই সব ব্রাঞ্চে খোঁজে,
        //    তবে পিলে সত্যি কথাটাই লেখে — "All Branches"।
        //    ⛔ Staff/Field Officer-এর কিছুই বদলায়নি (তাঁরা সার্ভারেই ব্রাঞ্চ-বাঁধা)।
        intent.getStringExtra("searchMobile")?.let { sm ->
            val digits = sm.filter { it.isDigit() }.takeLast(10)
            if (digits.isNotBlank()) {
                val wanted = (intent.getStringExtra("searchBranch") ?: "").trim()
                val known = branches.firstOrNull { it.equals(wanted, ignoreCase = true) } ?: ""
                if (user.role == "master" && known.isNotBlank()) {
                    branchFilter = known
                    val __bi = (listOf("Select Branch") + branches).indexOf(known)
                    if (__bi > 0) binding.spinnerBranchFilter.setSelection(__bi)
                    binding.branchPicker.text = "🏥 " + known + " ▾"
                } else {
                    branchFilter = ""
                    if (user.role == "master") binding.branchPicker.text = "🏥 All Branches ▾"
                }
                binding.etSearch.setText(digits)
                searchQuery = digits
            }
        }

        loadList()
    }

    private var allItems: List<DoctorVisitItem> = emptyList()
    private var currentFilter: String = "all"
    private var searchQuery: String = ""
    private var branchFilter: String = ""
    private val BRANCH_NONE = "__NONE__"

    /**
     * Doctor Call Summary — mirrors the web's doctorStaffCallSummary(): for the
     * current month, counts how many distinct doctors each staff member called
     * (from every doctor's callHistory), newest work first.
     */
    private fun showCallSummary() {
        // 🔒 TK-এর নিয়ম (28.07.2026): চাপ দেওয়ামাত্র স্টাফ বুঝবেন কাজ শুরু হয়েছে।
        Toast.makeText(this, "Loading…", Toast.LENGTH_SHORT).show()
        val thisYM = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        lifecycleScope.launch {
            // CRASH-SAFETY FIX (TK-reported, 2026-07-16): this had no error
            // handling -- any problem (e.g. one malformed doctor_visits row)
            // used to crash the WHOLE APP, which Android then relaunches at
            // Login/Dashboard -- this is what "View All crashes to Home"
            // looked like. Same safety-net pattern already used elsewhere.
            try {
                data class StaffCall(val name: String, val branch: String, val count: Int)
                val rows = withContext(Dispatchers.IO) {
                    // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): only the
                    // two fields this count reads are downloaded now -- a doctor row
                    // also carries its whole referral-payment list, which this block
                    // never looks at. ⛔ Same rows, same counting, word for word.
                    val docs = SupabaseClient.fetchListSlim("doctor_visits", null, 5000, "id,callHistory,updatedAt")
                    val byStaff = HashMap<String, HashSet<String>>()
                    for (i in 0 until docs.length()) {
                        val d = docs.optJSONObject(i) ?: continue
                        val docId = d.s("id")
                        val hist = d.optJSONArray("callHistory") ?: org.json.JSONArray()
                        for (j in 0 until hist.length()) {
                            val h = hist.optJSONObject(j) ?: continue
                            if (h.s("date").take(7) != thisYM) continue
                            val who = h.s("by").filter { it.isDigit() }.takeLast(10)
                            if (who.isBlank()) continue
                            byStaff.getOrPut(who) { HashSet() }.add(docId)
                        }
                    }
                    val accounts = StaffDirectory.allAccounts()
                    byStaff.entries.map { (mob, set) ->
                        val acc = accounts.find { it.mobile.filter { c -> c.isDigit() }.takeLast(10) == mob }
                        StaffCall(acc?.name ?: mob, acc?.branch?.ifBlank { "Other" } ?: "Other", set.size)
                    }
                }

                if (rows.isEmpty()) {
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "📞 Doctor Call Summary — This Month"))
                        .setMessage("No staff called any RMP this month.")
                        .setPositiveButton("Close", null).show().also { PremiumAlert.paint(it) }
                    return@launch
                }

                val dp = resources.displayMetrics.density
                fun px(v: Int) = (v * dp).toInt()
                val col = LinearLayout(this@DoctorVisitActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(16), px(12), px(16), px(4))
                }
                // Group by branch, branches ordered by their total calls.
                rows.groupBy { it.branch }
                    .entries
                    .sortedByDescending { e -> e.value.sumOf { it.count } }
                    .forEach { (branch, list) ->
                        val header = TextView(this@DoctorVisitActivity).apply {
                            text = "📍 $branch — Total ${list.sumOf { it.count }} RMP"
                            setTextColor(0xFFFFFFFF.toInt())
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            textSize = 15f
                            setBackgroundColor(0xFF0B5D2A.toInt())
                            setPadding(px(14), px(10), px(14), px(10))
                        }
                        col.addView(header, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = px(12) })

                        list.sortedByDescending { it.count }.forEach { r ->
                            val row = LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                                setPadding(px(14), px(10), px(14), px(10))
                                setBackgroundColor(0xFFFFFFFF.toInt())
                            }
                            row.addView(TextView(this@DoctorVisitActivity).apply {
                                text = "👤 ${r.name}"
                                textSize = 14f
                                setTextColor(0xFF10223A.toInt())
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            row.addView(TextView(this@DoctorVisitActivity).apply {
                                text = "${r.count} RMP"
                                textSize = 15f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(0xFF12A150.toInt())
                            })
                            col.addView(row)
                            col.addView(android.view.View(this@DoctorVisitActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                setBackgroundColor(0xFFF0F3F7.toInt())
                            })
                        }
                    }

                val scroll = android.widget.ScrollView(this@DoctorVisitActivity).apply { addView(col) }
                UppercaseInputUtil.applyToAll(scroll)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
                AlertDialog.Builder(this@DoctorVisitActivity)
                    .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "📞 Doctor Call Summary — This Month"))
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show().also { PremiumAlert.paint(it) }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorVisitActivity, "Could not load call summary — check connection and try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calledThisMonth(item: DoctorVisitItem): Boolean {
        val ym = DoctorVisitModel.today().take(7)
        return item.lastCallDate.isNotBlank() && item.lastCallDate.take(7) == ym
    }

    private fun renderList() {
        val q = searchQuery.trim().lowercase()
        var branchRows = if (branchFilter == BRANCH_NONE) emptyList() else allItems
        if (branchFilter.isNotEmpty() && branchFilter != BRANCH_NONE) {
            branchRows = branchRows.filter { it.branch == branchFilter }
        }
        var rows = branchRows
        rows = when (currentFilter) {
            // 🔒🔒 খাতার সারি B193 (TK, 30.07.2026 রাত — আলোচনায় পাশ): "pending"-এর
            // অর্থ বদলে গেছে। আগে ছিল "Overdue Call" (nextCallDate পার হয়ে
            // গেছে)। এখন TK-এর নতুন সংজ্ঞা: *"প্রতি মাসে ডাক্তারকে একবার কল
            // করতে হয়... যদি কোনো ডাক্তারকে কল করা না হয়, সেই নাম্বার এখানে
            // শো করবে।"* — অর্থাৎ **এই ইংরেজি ক্যালেন্ডার মাসে (১ তারিখ থেকে
            // আজ পর্যন্ত) একবারও কল করা হয়নি** এমন ডাক্তার। ⛔ পুরনো Overdue
            // ধারণাটা এখন আর কোনো বক্সে নেই (TK: "আগেরগুলো বাদ রাখুন")।
            "pending" -> rows.filter { !calledThisMonth(it) }
            "called" -> rows.filter { calledThisMonth(it) }
            // 🔒 খাতার সারি B193: "EXPECTED" — ডাক্তার কল করার সময় যদি একটা
            // তারিখ বলেন যে সেদিন একটা পেশেন্ট পাঠাতে পারেন, সেই তারিখ
            // `expectedPatientDate`-এ বসে (Log Call ফর্ম থেকে)। এই ঘরে
            // সেই সব ডাক্তার — তারিখ অতীত/আজ/ভবিষ্যৎ যাই হোক, TK নিজে
            // থেকে না সরানো পর্যন্ত এখানেই থাকেন (কিছুই হারায় না)।
            "expected" -> rows.filter { it.expectedPatientDate.isNotBlank() }
            else -> rows
        }
        if (q.isNotEmpty()) {
            val qDigits = q.filter { c -> c.isDigit() }
            // TK-REPORTED BUG FIX (2026-07-24): search only matched name/
            // mobile -- a doctor's own area/location (📍 tag, e.g. "TALMA")
            // never matched even though it's clearly visible on every card
            // and is exactly the kind of thing staff would search by.
            rows = rows.filter {
                it.name.lowercase().contains(q) ||
                    it.area.lowercase().contains(q) ||
                    (qDigits.isNotEmpty() && it.mobile.filter { c -> c.isDigit() }.contains(qDigits))
            }
        }
        // 🔒 খাতার সারি B193 (TK, 30.07.2026 রাত): "EXPECTED" ট্যাবে সবচেয়ে
        // কাছের প্রত্যাশিত তারিখ আগে দেখানো হয় (আজ/অতীতেরটা সবার উপরে) —
        // বাকি তিনটে ট্যাবে আগের মতোই নেক্সট-কল-তারিখ ধরে সাজানো, কিছু
        // বদলায়নি।
        val sorted = if (currentFilter == "expected") {
            rows.sortedBy { it.expectedPatientDate.ifBlank { "9999-12-31" } }
        } else {
            rows.sortedWith(
                compareBy({ !DoctorVisitModel.isDue(it.nextCallDate) }, { it.nextCallDate.ifBlank { "9999-12-31" } })
            )
        }
        // 🔒🔒 খাতার সারি B193 (TK, 30.07.2026 রাত — আলোচনায় পাশ): ৪টা বক্সের
        // গোনা এখন নতুন প্ল্যান অনুসারে — EXPECTED (expectedPatientDate সেট
        // করা আছে) · PENDING (এই মাসে একবারও কল হয়নি) · CALLED (এই মাসে
        // অন্তত একবার কল হয়েছে) · ALL RMP (মোট)।
        val expectedCount = branchRows.count { it.expectedPatientDate.isNotBlank() }
        val pendingCount = branchRows.count { !calledThisMonth(it) }
        val monthCount = branchRows.count { calledThisMonth(it) }
        binding.tvTodayCount.text = expectedCount.toString()
        binding.tvPendingCount.text = pendingCount.toString()
        binding.tvCalledCount.text = monthCount.toString()
        binding.tvAllCount.text = branchRows.size.toString()
        // TK-REQUESTED (2026-07-23): hide Expected / Pending / Called boxes
        // when their count is 0 (only when a branch is actually chosen -- a
        // branch not chosen yet legitimately has 0 for everything, and we
        // don't want the whole row to flicker/collapse there). "All RMP"
        // is never hidden per TK. GONE (not INVISIBLE) so the remaining
        // boxes spread to fill the row evenly.
        val branchChosen = branchFilter != BRANCH_NONE
        binding.statToday.visibility = if (branchChosen && expectedCount == 0) View.GONE else View.VISIBLE
        binding.statPending.visibility = if (branchChosen && pendingCount == 0) View.GONE else View.VISIBLE
        binding.statCalled.visibility = if (branchChosen && monthCount == 0) View.GONE else View.VISIBLE
        binding.statAll.visibility = View.VISIBLE
        // TK-REPORTED FIX (2026-07-23): the active filter's box gets a bolder
        // border (in its own colour family) so it's clear which is selected.
        binding.statToday.setBackgroundResource(
            if (currentFilter == "expected") com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_expected_selected
            else com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_expected
        )
        binding.statPending.setBackgroundResource(
            if (currentFilter == "pending") com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_overdue_selected
            else com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_overdue
        )
        binding.statCalled.setBackgroundResource(
            if (currentFilter == "called") com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_month_selected
            else com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_month
        )
        binding.statAll.setBackgroundResource(
            if (currentFilter == "all") com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_all_selected
            else com.tkbiswas.pilesclinic.R.drawable.bg_dv_stat_all
        )
        // TK-REQUESTED ADDITION (2026-07-23): visible "X showing" text in
        // the list area itself when a filter (not "all") is active.
        val filterLabel = when (currentFilter) {
            "pending" -> "Pending"
            "called" -> "Called"
            "expected" -> "Expected"
            else -> null

        }
        if (filterLabel != null) {
            binding.tvFilterCount.text = "🔎 $filterLabel: ${sorted.size} showing"
            binding.tvFilterCount.visibility = View.VISIBLE
        } else {
            binding.tvFilterCount.visibility = View.GONE
        }
        if (branchFilter == BRANCH_NONE) {
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
        } else if (sorted.isEmpty()) {
            binding.tvEmpty.text = "No doctors found"
            binding.progressLoad.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.progressLoad.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.updateItems(sorted)
        }
    }

    // TK-REQUESTED ADDITION (2026-07-20): a separate, remark-ONLY edit --
    // triple-tapping the Last Remark text specifically (not the name/mobile,
    // which still opens the full "Edit Doctor / RMP" dialog via showDoctorEdit
    // above, untouched). Only the "remarks" field is sent to Supabase --
    // name/mobile/branch/area are never touched by this path, and unlike
    // "Log Call" (showLogCallDialog) this does NOT append to callHistory or
    // require/change the Next Call Date -- it only corrects the remark text.
    // 🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B206 — TK ফটো-প্রুফ দেখে
    // "এটা ঠিক আছে" বলেছেন): এই পপ-আপ এখন প্রজেক্টের বাকি প্রিমিয়াম পপ-আপের
    // মতোই প্রফেশনাল দেখায় (সবুজ হেডার + গোলাকার Cancel/Save বোতাম), plain
    // AlertDialog আর নয়। ⛔ নিচের কাজ (logCall() কল করা, B123-এর নিয়ম, সব
    // Toast/loadList()) এক অক্ষরও বদলায়নি -- শুধু চেহারা। ⛔ premiumDialogShell()
    // ব্যবহার করা হয়নি কারণ ওটার ভিতরের ScrollView ফিক্সড ৪২০dp উঁচু --
    // এই ছোট ১-ঘরের ফর্মে সেটা বসালে নিচে অনেকটা ফাঁকা জায়গা থেকে যেত
    // (TK-এর পাশ-করা প্রুফের কমপ্যাক্ট চেহারার সাথে মিলত না); তাই এখানে
    // হুবহু একই রং/গড়ন হাতে বসানো হলো, কিন্তু বক্সের উচ্চতা কনটেন্ট
    // অনুযায়ী (wrap_content) -- অন্য কোনো পপ-আপ/premiumDialogShell()-এর
    // ভিতরে হাত দেওয়া হয়নি।
    private fun showRemarkOnlyEdit(item: DoctorVisitItem) {
        if (item.id.isBlank()) return
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f * d
                setStroke(dp(1), android.graphics.Color.parseColor("#145A32"))
            }
        }
        root.addView(TextView(this).apply {
            text = "📝  Edit Remark"
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#145A32"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        root.addView(body)
        body.addView(TextView(this).apply {
            // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে মোবাইল দুইবার দেখাত।
            text = "${item.name.ifBlank { "UNKNOWN" }} · ${item.mobile}"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
        })
        body.addView(fieldLabel("📝", "Remark"))
        val remarkInput = android.widget.EditText(this).apply { setText(item.remarks); hint = "Remark" }
        styleInput(remarkInput)
        UppercaseInputUtil.applyToAll(remarkInput)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        body.addView(remarkInput)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(actionRow)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.setOnShowListener { try { NoBengali.installDialog(dialog) } catch (_: Throwable) { } }

        actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { dialog.dismiss() }
        })
        actionRow.addView(pillButton("💾 Save", "#0C9E33").apply {
            setOnClickListener {
                val newRemark = remarkInput.text.toString().trim()
                // TK-REQUESTED (2026-08-13): no written call discussion means
                // no call-history row and therefore no call-count increase.
                // The old prefilled text must not be re-saved as a new call.
                if (newRemark.isBlank()) {
                    remarkInput.error = "Remarks required"
                    remarkInput.requestFocus()
                    return@setOnClickListener
                }
                if (newRemark == item.remarks.trim()) {
                    remarkInput.error = "Write the new call discussion"
                    remarkInput.requestFocus()
                    return@setOnClickListener
                }
                dialog.dismiss()
                lifecycleScope.launch {
                    // 🚨🚨 TK-REPORTED, LIVE (29.07.2026 বিকেল ৫.৩৬, ছবিসহ · খাতার সারি B123
                    //     — জলপাইগুড়ির স্টাফ): *"এই ডক্টরকে কল করা হয়েছে, দুবার কল করেছি,
                    //     Remarks-ও লেখা হয়েছে — কিন্তু 0 calls দেখাচ্ছে কেন?"*
                    //
                    // **আসল কারণ (কোড ধরে, আন্দাজ নয়):** রিমার্ক লেখার **দুটো পথ** ছিল, আর
                    // কল গোনা হত মাত্র একটাতে —
                    //   · 📞 চেপে কল → ফিরে এসে "Add Remark" → `logCall()` → কল গোনা হত ✅
                    //   · কার্ডের **রিমার্ক বাক্সে** সরাসরি লেখা → শুধু `remarks` বসত ❌
                    // অথচ ওই বাক্সটা দেখতে **Follow-up কার্ডের রিমার্ক বাক্সের হুবহু একই**,
                    // আর Follow-up-এ ওখানে লিখলে কল গোনা হয়। স্টাফ সেই অভ্যাসেই লিখেছেন —
                    // তাই কার্ডে রিমার্ক দেখাত কিন্তু `0 calls`, আর View All-এ Total Entries 0
                    // (ওই তালিকাটা `callHistory` থেকে বানানো হয়, ওতে কিছু যোগ হত না)।
                    //
                    // **TK-এর সিদ্ধান্ত (29.07.2026): "হ্যাঁ গোনা হবে।"** তাই এখন এই পথেও
                    // ঠিক সেই একই `logCall()` চলে — এক জায়গায় এক নিয়ম।
                    // ⛔ **নতুন কোনো নিয়ম বানানো হয়নি** — Add Remark যে ফাংশন ডাকত,
                    //    হুবহু সেটাই। তাই `callHistory` · `lastCallDate` · `callStatus` ·
                    //    `remarks` সব একসঙ্গে ও একই ধাঁচে বসে, দুই পথে দুই রকম হবে না।
                    // ⛔ **পরের কলের তারিখ ছোঁয়া হয় না** — কার্ডে যা আছে তাই আবার পাঠানো
                    //    হয়, তাই এই পথে তারিখ কখনো মুছে যাবে না।
                    // ⛔ ডেটাবেসে নতুন ঘর/টেবিল লাগেনি — SQL লাগবে না।
                    // 🔒 খাতার সারি B193 (30.07.2026 রাত): একই নিয়ম এখন
                    // `expectedPatientDate`-এর জন্যও — এই পথে সেটাও কখনো
                    // ছোঁয়া হয় না, কার্ডে যা আছে তাই আবার পাঠানো হয়।
                    val user2 = NativeSession.current(this@DoctorVisitActivity)
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            repository.logCall(
                                item.id, newRemark, item.nextCallDate,
                                user2?.mobile ?: "", this@DoctorVisitActivity, item.expectedPatientDate
                            )
                        } catch (_: Throwable) { false }
                    }
                    Toast.makeText(this@DoctorVisitActivity, if (ok) "Remark updated" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) loadList()
                }
            }
        })
        UppercaseInputUtil.applyToAll(root)
        dialog.show()
    }

    // 🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B205 — TK: "স্টাফ তো ভুল করে
    // কিছু লিখতেই পারে, তার ভুল সংশোধনের রাস্তা তো করতে হবে")। এটা
    // showRemarkOnlyEdit()/showLogCallDialog()-এর থেকে ইচ্ছে করেই আলাদা —
    // ওই দুটো B123-এর লক করা নিয়ম মেনে **নতুন কল** হিসেবে গোনা হয় (নতুন
    // callHistory এন্ট্রি, callCount+1) — এখানে তা বদলানো হয়নি।
    // ⛔ এই ফাংশনটা শুধু সবচেয়ে নতুন (callHistory-র সবচেয়ে উপরের) এন্ট্রির
    //    `note`-টাই নিজের জায়গায় বদলায় — কোনো নতুন এন্ট্রি যোগ হয় না, তাই
    //    callCount/lastCallDate/nextCallDate/callStatus/expectedPatientDate
    //    এর একটাও বদলায় না। শুধু ভুল-লেখা সংশোধনের জন্য, নতুন কল নয়।
    private fun editLastCallNote(item: DoctorVisitItem) {
        if (item.id.isBlank()) return
        val hist = item.raw.optJSONArray("callHistory") ?: org.json.JSONArray()
        if (hist.length() == 0) {
            Toast.makeText(this, "No call entry yet to fix", Toast.LENGTH_SHORT).show()
            return
        }
        val topNote = hist.optJSONObject(0)?.s("note") ?: ""
        val noteInput = android.widget.EditText(this).apply { setText(topNote); hint = "Note" }
        UppercaseInputUtil.applyToAll(noteInput)  // TK-REQUESTED GLOBAL RULE (2026-07-24)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Fix Last Note — ${item.name.ifBlank { item.mobile }}"))
            .setView(noteInput)
            .setPositiveButton("Save") { _, _ ->
                val fixedNote = noteInput.text.toString().trim()
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try { repository.editLastCallNote(item.id, hist, fixedNote, this@DoctorVisitActivity) } catch (_: Throwable) { false }
                    }
                    Toast.makeText(this@DoctorVisitActivity, if (ok) "Note fixed" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) loadList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun showDoctorEdit(item: DoctorVisitItem) {
        if (item.id.isBlank()) return
        // TK-APPROVED (2026-07-22): rebuilt to the SAME premium look as the
        // Add Doctor/RMP dialog (gradient/green header, rounded input boxes,
        // pill Save/Cancel/Delete) instead of the old plain AlertDialog --
        // same fields, same data, same actions, only the look is professional.
        val parts = premiumDialogShell("🩺", "Edit Doctor / RMP")
        val container = parts.body

        container.addView(fieldLabel("🧑‍⚕️", "Doctor Name *", 0))
        val name = EditText(this).apply { setText(item.name); hint = "Doctor Name" }
        styleInput(name); clearErrorOnEdit(name); container.addView(name)

        container.addView(fieldLabel("📱", "Mobile *"))
        val mobile = EditText(this).apply { hint = "Mobile (10-digit)" }
        styleInput(mobile)
        MobileInput.attach(mobile)
        mobile.setText(MobileInput.digits(item.mobile))
        clearErrorOnEdit(mobile)
        container.addView(mobile)

        container.addView(fieldLabel("🏥", "Branch *"))
        val branch = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, branches)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val bi = branches.indexOf(item.branch); if (bi >= 0) setSelection(bi)
        }
        container.addView(branch)

        container.addView(fieldLabel("📍", "Area / Address"))
        val area = EditText(this).apply { setText(item.area); hint = "Area / Address" }
        styleInput(area); container.addView(area)

        container.addView(fieldLabel("📝", "Remarks"))
        val remarks = EditText(this).apply { setText(item.remarks); hint = "Remarks" }
        styleInput(remarks); clearErrorOnEdit(remarks); container.addView(remarks)

        // 🟢 B630 (11.08.2026, TK-নির্দেশ): একই ডাক্তারের বাড়তি নম্বর — বিদ্যমান ডাক্তারেও
        //   যোগ/এডিট করা যায় (কমা দিয়ে আলাদা)। ⛔ মূল Mobile অটুট; শুধু বাড়তি নম্বর।
        container.addView(fieldLabel("📱", "Other numbers (comma separated, optional)"))
        val altEdit = EditText(this).apply { setText(item.altMobiles); hint = "e.g. 9800000000, 9811111111" }
        styleInput(altEdit); container.addView(altEdit)

        val u = NativeSession.current(this)
        val canDelete = u != null && (u.role == "master" || u.role == "staff" || u.role == "field")
        if (canDelete) {
            parts.actionRow.addView(pillButton("🗑 Delete", "#FBECEC", android.graphics.Color.parseColor("#C0392B")).apply {
                setOnClickListener { parts.dialog.dismiss(); confirmDeleteDoctor(item) }
            })
        }
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("💾 Save", "#0C9E33").apply {
            setOnClickListener {
                val nm = name.text.toString().trim()
                val mob = MobileInput.digits(mobile)
                val br = branch.selectedItem?.toString() ?: ""
                val rm = remarks.text.toString().trim()
                // TK-DECISION (2026-07-22): highlight the exact wrong field(s).
                styleInput(name); styleInput(mobile); styleInput(remarks)
                var firstMsg: String? = null; var firstBad: View? = null
                fun bad(v: View, msg: String) { setFieldError(v); if (firstMsg == null) { firstMsg = msg; firstBad = v } }
                if (nm.isBlank()) bad(name, "Doctor Name দিন")
                if (mob.length != 10) bad(mobile, "সঠিক 10 ডিজিট মোবাইল দিন")
                if (br.isBlank()) bad(branch, "Branch বাছুন")
                if (firstMsg != null) {
                    Toast.makeText(this@DoctorVisitActivity, firstMsg, Toast.LENGTH_SHORT).show()
                    (firstBad as? EditText)?.requestFocus()
                    return@setOnClickListener
                }
                // 🟢 B630: বাড়তি নম্বরগুলো normalize করে "+91XXXXXXXXXX" CSV-তে; ভুল/ফাঁকা বাদ।
                val altCsv = altEdit.text.toString().split(",").mapNotNull {
                    val t = it.filter { c -> c.isDigit() }.takeLast(10); if (t.length == 10) "+91$t" else null
                }.distinct().joinToString(",")
                val fields = org.json.JSONObject()
                    .put("name", nm)
                    .put("mobile", mob)
                    .put("branch", br)
                    .put("area", area.text.toString().trim())
                    .put("remarks", rm)
                // 🔴🔴🔒 V458 (TK-নির্দেশ ১৯.০৮.২০২৬: "কোন স্টাফ কল করেছিল কবে —
                // সততার সাথে যেন বোঝা যায়")। এই পপ-আপের Remarks ঘরটা সরাসরি লেখে —
                // Log Call/Edit Remark-এর মতো callHistory-তে যায় না, তাই এতদিন
                // কে/কবে বদলেছেন তার কোনো হদিস থাকত না। এখন রিমার্ক সত্যিই বদলালে
                // (আগেরটার থেকে আলাদা) নীরবে কে/কবে জমা থাকে — callCount/
                // callHistory/lastCallDate কিছুই ছোঁয়া হয় না, শুধু এই দুটো ঘর।
                if (rm != item.remarks.trim()) {
                    val whoNow = NativeSession.current(this@DoctorVisitActivity)?.mobile ?: ""
                    fields.put("remarksEditedBy", whoNow)
                    fields.put("remarksEditedAt", java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US
                    ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
                }
                // 🟢 B630: altMobiles তখনই লেখা হয় যখন কিছু আছে বা আগে ছিল (মুছতে) —
                //   নইলে নয়, যাতে SQL কলাম না থাকলেও সাধারণ এডিট ব্যর্থ না হয়।
                if (altCsv.isNotBlank() || item.altMobiles.isNotBlank()) fields.put("altMobiles", altCsv)
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { SupabaseClient.updateById("doctor_visits", item.id, fields) }
                    Toast.makeText(this@DoctorVisitActivity, if (ok) "Updated" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) { parts.dialog.dismiss(); loadList() }
                }
            }
        })
        parts.dialog.show()
    }

    // TK-REQUESTED CHANGE (2026-07-23): Doctor/RMP delete now needs Admin
    // approval unless the person deleting IS the Admin (Master). Previously
    // "master"/"staff"/"field" could all delete directly -- staff/field now
    // only send a request; Master sees it (requestBanner on the card) and
    // must Approve before anything is actually removed. Reject just clears
    // the request, nothing deleted. This is the SAME function called from
    // both the Edit Doctor dialog's Delete button AND the card's own 🗑️ icon,
    // so there is exactly one place this permission rule lives.
    private fun confirmDeleteDoctor(item: DoctorVisitItem) {
        val user = NativeSession.current(this) ?: return
        if (!(user.role == "master" || user.role == "staff" || user.role == "field")) {
            Toast.makeText(this, "You don't have permission to delete", Toast.LENGTH_SHORT).show()
            return
        }
        if (item.id.isBlank()) return
        if (item.deleteRequestedBy.isNotBlank()) {
            Toast.makeText(this, NoBengali.s("ইতিমধ্যে Delete request পাঠানো হয়েছে — Admin অনুমোদনের অপেক্ষায়"), Toast.LENGTH_SHORT).show()
            return
        }
        if (user.role == "master") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Sure?"))
                .setMessage("Delete \"${item.name.ifBlank { item.mobile }}\"? It will move to Trash Bin (Master can restore or permanently remove it).")
                .setPositiveButton("Yes, Delete") { _, _ ->
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val rows = SupabaseClient.fetchList("doctor_visits", "id=eq.${item.id}", 1)
                            if (rows.length() == 0) return@withContext "NOT_FOUND"
                            if (TrashHelper.moveToTrash("doctor_visits", rows.getJSONObject(0), user.mobile)) "OK" else "NETWORK"
                        }
                        val msg = when (result) {
                            "OK" -> "Deleted — moved to Trash Bin"
                            "NOT_FOUND" -> "Record not found — it may already be deleted"
                            else -> "Could not delete — network is too slow/unstable, please retry"
                        }
                        Toast.makeText(this@DoctorVisitActivity, msg, Toast.LENGTH_SHORT).show()
                        if (result == "OK") loadList()
                    }
                }
                .setNegativeButton("No", null)
                .show().also { PremiumAlert.paint(it) }
        } else {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Delete request পাঠাবেন?"))
                .setMessage(NoBengali.s("${item.name.ifBlank { item.mobile }}\n\nAdmin অনুমোদন দিলে তবেই ডিলিট হবে — এখনই কিছু মুছবে না।"))
                .setPositiveButton("Send Request") { _, _ ->
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { repository.requestDelete(item.id, user.mobile) }
                        Toast.makeText(this@DoctorVisitActivity, NoBengali.s(if (ok) "Admin-কে অনুরোধ পাঠানো হয়েছে" else "ব্যর্থ — নেট চেক করুন"), Toast.LENGTH_SHORT).show()
                        if (ok) loadList()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    private fun handleApproveDelete(item: DoctorVisitItem) {
        val user = NativeSession.current(this) ?: return
        val requesterName = StaffDirectory.findAccount(item.deleteRequestedBy)?.name ?: item.deleteRequestedBy
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Approve Delete?"))
            .setMessage(NoBengali.s("${item.name.ifBlank { item.mobile }}\nRequested by: $requesterName\n\nআসলেই ডিলিট হবে (Trash Bin-এ যাবে)।"))
            .setPositiveButton("Approve") { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { repository.approveDelete(item.raw, user.mobile, this@DoctorVisitActivity) }
                    Toast.makeText(this@DoctorVisitActivity, NoBengali.s(if (ok) "Approved — Deleted" else "ব্যর্থ — নেট চেক করুন"), Toast.LENGTH_SHORT).show()
                    if (ok) loadList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun handleRejectDelete(item: DoctorVisitItem) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.rejectDeleteRequest(item.id) }
            Toast.makeText(this@DoctorVisitActivity, NoBengali.s(if (ok) "Request বাতিল করা হলো" else "ব্যর্থ — নেট চেক করুন"), Toast.LENGTH_SHORT).show()
            if (ok) loadList()
        }
    }

        private var firstResume = true
    // TK-REQUESTED (2026-07-18): call-then-remind, same pattern as
    // FollowUpActivity/FollowCalendarActivity -- no permission needed.
    private var pendingCallItem: DoctorVisitItem? = null
    override fun onResume() {
        super.onResume()
        val calledItem = pendingCallItem
        if (calledItem != null) {
            pendingCallItem = null
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Update remark?"))
                .setMessage("Log a call note for ${calledItem.name.ifBlank { calledItem.mobile }} after that call?")
                .setPositiveButton("Add Remark") { _, _ -> showLogCallDialog(calledItem) }
                .setNegativeButton("Not now", null)
                .show().also { PremiumAlert.paint(it) }
        }
        if (firstResume) { firstResume = false; return }
        loadList()
    }

    private fun dvCachePrefs() = getSharedPreferences("doctor_visit_cache", android.content.Context.MODE_PRIVATE)
    private fun activeDoctorBranch(): String? {
        if (user.role != "master") return user.branch
        return if (branchFilter.isNotBlank() && branchFilter != BRANCH_NONE) branchFilter else null
    }

    private fun dvCacheKey() = "cache_" + (activeDoctorBranch()?.ifBlank { "All" } ?: "All")

    // 🔒 TK'S PERMANENT RULE (28.07.2026, khata row B25): what THIS phone saved
    // is always shown on THIS phone. The stored list is what stays on screen on
    // a weak line, so a doctor just added -- or a call remark just written --
    // used to be missing from it until the cloud copy arrived. MyPhoneWrites
    // puts this phone's own writing back on top. Display only: the note retires
    // itself the moment the cloud copy is equally new or newer.
    /** ⛔ BRANCH SAFETY: MyPhoneWrites keeps one note-book per table, so a
     *  doctor this phone added for another branch could otherwise appear in
     *  this branch's list. This applies exactly the same branch rule the
     *  cloud query itself uses, so nothing can cross branches. Master /
     *  "All" sees everything, as always. */
    private fun myDoctorRows(rows: org.json.JSONArray): org.json.JSONArray {
        val merged = MyPhoneWrites.overlay(this, "doctor_visits", rows)
        val branch = user.branch
        if (branch.isBlank() || branch == "All") return merged
        val out = org.json.JSONArray()
        for (i in 0 until merged.length()) {
            val r = merged.optJSONObject(i) ?: continue
            val rb = r.s("branch")
            if (rb.isNotBlank() && !rb.equals(branch, ignoreCase = true)) continue
            out.put(r)
        }
        return out
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🔴🔴🔒 V512 (২১.০৮.২০২৬, TK-এর পুরোনো রিপোর্ট) — **"তালিকা একবার এসে
    //    আবার লাফ দেয়" (flicker)।**
    //
    // ─── কারণ (কোড ধরে প্রমাণিত, আন্দাজে নয়) ─────────────────────────────
    //   এই পর্দা একবার খুললে তালিকা **দুবার** আঁকা হয় — প্রথমে ফোনের জমানো
    //   কপি (তাৎক্ষণিক), তারপর ক্লাউডের কপি। কিন্তু জমানো কপিতে
    //   `referredCount` ঘরটা **কখনোই থাকে না** — `DoctorVisitModel.parse()`
    //   ওটা ভরতে পারে না (মডেলের ৪০–৪৬ নম্বর লাইনে নিজেই লেখা আছে), কারণ
    //   ওটা গোনা হয় গোটা `patients` টেবিল মিলিয়ে, এই পর্দার `loadList()`-এ।
    //   ⇒ প্রথম আঁকায় প্রতিটা কার্ডে `referredCount = 0`, তাই
    //     "👥 N PATIENTS REFERRED" লাইনটা **লুকোনো** থাকে
    //     (DoctorVisitAdapter.kt-এর ২৫০ নম্বর লাইন)। দ্বিতীয় আঁকায় সংখ্যা
    //     বসে আর লাইনটা **হঠাৎ যোগ হয়** — কার্ড লম্বা হয়ে নিচের সব কার্ড
    //     ঠেলে নামিয়ে দেয়। এটাই চোখে-দেখা লাফ।
    //
    // ⇒ সমাধান: সফলভাবে গোনা হয়ে গেলে ঐ সংখ্যাগুলোও (শুধু `id → সংখ্যা`,
    //   একটা ছোট JSON) ফোনে জমা থাকে, আর জমানো কপি দেখানোর সময় সেগুলো
    //   বসিয়ে দেওয়া হয়। তাই প্রথম ও দ্বিতীয় আঁকা দেখতে এক রকম — লাফ নেই।
    //
    // ⛔ **একটাও নতুন ক্লাউড-অনুরোধ নেই** — সংখ্যাটা আগে থেকেই গোনা হচ্ছিল,
    //    শুধু ফেলে দেওয়া হচ্ছিল। Egress-এ প্রভাব শূন্য।
    // ⛔ ক্লাউডের আসল গোনা আগের মতোই চলে ও **শেষ কথা ওটাই** — জমানো সংখ্যা
    //    শুধু ঐ এক-সেকেন্ডের ফাঁকটুকু ভরাট করে, তারপর আসল সংখ্যাই বসে।
    // ⛔ টাকার কোনো ঘর (`referralPaid`/`referralDue`) এখানে জমানো হয় না —
    //    ওগুলো সারিরই নিজের ঘর, আগের মতোই ক্লাউড থেকে আসে।
    // ⚠️ সৎ সীমা: এই আপডেটের **পরে প্রথমবার** পর্দা খুললে জমানো সংখ্যা
    //    এখনো নেই, তাই ঐ একবার আগের মতোই লাফ দেবে। দ্বিতীয়বার থেকে নয়।
    // ══════════════════════════════════════════════════════════════════════
    private fun dvCountsKey() = dvCacheKey() + "_referred_counts"

    private fun loadCachedCounts(): org.json.JSONObject? = try {
        val j = dvCachePrefs().getString(dvCountsKey(), null)
        if (j.isNullOrBlank()) null else org.json.JSONObject(j)
    } catch (_: Throwable) { null }

    /**
     * ⛔ V512 (নিজের কাজ আবার যাচাই করে যোগ করা দুটো পাহারা):
     *
     * ১) **সব সংখ্যা ০ হলে কিছুই জমা হয় না।** কারণ গোনার ভিতরের ধাপটা
     *    (`legacyCardCounts` / `patients` পড়া) নেট খারাপ হলে **চুপচাপ ফাঁকা**
     *    ফেরায় — তখন সবার সংখ্যা ০ হয়ে যেত, আর সেই ০-ই জমা হয়ে পরের বার
     *    ভুল দেখাত। সব ০ হলে জমা না করলে কিছুই হারায় না: ০ মানে ঐ লাইনটা
     *    এমনিতেই লুকানো থাকে, তাই লাফও দেয় না।
     * ২) **যে ব্রাঞ্চের জন্য গোনা শুরু হয়েছিল, সেই ব্রাঞ্চের ঘরেই জমা হয়।**
     *    মাঝপথে ব্রাঞ্চ বদলে গেলে আগের ব্রাঞ্চের সংখ্যা নতুন ব্রাঞ্চের ঘরে
     *    বসে যেত। (`keyAtStart` ডাকার জায়গা থেকে পাঠানো হয়।)
     */
    private fun saveCachedCounts(items: List<DoctorVisitItem>, keyAtStart: String) {
        try {
            if (keyAtStart != dvCountsKey()) return        // ব্রাঞ্চ বদলে গেছে
            if (items.none { it.referredCount > 0 }) return // গোনাটা বিশ্বাসযোগ্য নয়
            val o = org.json.JSONObject()
            for (one in items) if (one.id.isNotBlank()) o.put(one.id, one.referredCount)
            dvCachePrefs().edit().putString(dvCountsKey(), o.toString()).apply()
        } catch (_: Throwable) {}
    }

    private fun loadCachedDoctors(): List<DoctorVisitItem>? {
        return try {
            val json = dvCachePrefs().getString(dvCacheKey(), null) ?: return null
            val arr = myDoctorRows(org.json.JSONArray(json))
            val counts = loadCachedCounts()   // 🔴 V512 — না থাকলে `null`, আচরণ আগের মতোই
            val list = ArrayList<DoctorVisitItem>(arr.length())
            for (i in 0 until arr.length()) {
                val one = DoctorVisitModel.parse(arr.getJSONObject(i))
                list.add(if (counts == null) one else one.copy(referredCount = counts.optInt(one.id, 0)))
            }
            list
        } catch (_: Throwable) { null }
    }

    private fun saveCachedDoctors(raw: org.json.JSONArray) {
        try { dvCachePrefs().edit().putString(dvCacheKey(), raw.toString()).apply() } catch (_: Throwable) {}
    }

    private fun loadList() {
        // Master-এর সাধারণ তালিকা নির্বাচিত branch ছাড়া কখনো সব branch একসঙ্গে
        // নামাবে না। এতে ভুল count এবং অপ্রয়োজনীয় Free Plan data—দুটোই বন্ধ।
        if (user.role == "master" && branchFilter == BRANCH_NONE) {
            allItems = emptyList()
            binding.progressLoad.visibility = View.GONE
            renderList()
            return
        }
        // TK-REQUESTED (2026-07-20): cache-first, same as Follow-up / CHECK-UP.
        // If this screen was opened before, the doctors already on the phone
        // show INSTANTLY (no spinner, no blank) and a fresh copy is fetched
        // quietly in the background. The spinner appears only on the very
        // first-ever open (no cache yet). If the background refresh fails,
        // the cached list stays on screen instead of blanking -- so the
        // person is never left confused by a spinner or an empty screen.
        val countsKeyAtStart = dvCountsKey()   // 🔴 V512 — মাঝপথে ব্রাঞ্চ বদলালে ধরার জন্য
        val cached = loadCachedDoctors()
        val hasCache = !cached.isNullOrEmpty()
        if (hasCache) {
            allItems = cached!!
            binding.progressLoad.visibility = View.GONE
            renderList()
        } else {
            binding.progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
            // TK-REQUESTED (2026-07-24): only reachable on the very first-
            // ever open (no cache yet) -- show plain "Loading..." instead
            // of a blank screen while the first fetch is in flight.
            binding.tvEmpty.text = "Loading..."
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                // TK-REPORTED CRITICAL BUG FIX (2026-07-24): this used to
                // call fetchListRaw() (plain fetchList()), which silently
                // returns an EMPTY array on a network failure -- and this
                // code then wrote that empty result straight into the
                // cache via saveCachedDoctors(), permanently overwriting
                // the real (previously cached) doctor list with "0
                // doctors" the next time this screen loaded too, even
                // after the network recovered. On very slow/weak
                // connections (TK-reported, 2026-07-24) this made real RMP
                // records look "deleted" even though nothing in the cloud
                // ever changed. Now uses fetchListRawOrNull(): a genuine
                // failure (null) leaves whatever was already on screen
                // (the cache shown above, if any) completely untouched --
                // the cache is only ever updated on an ACTUAL successful
                // fetch.
                /* 🔴🔒 V580 — পুরো টেবিলের বদলে **শুধু যেটুকু বদলেছে**।
                   ফোনে আগে থেকে জমা থাকা তালিকাটা সঙ্গে দেওয়া হয়; কিছু না
                   বদলালে সেটাই ফেরত আসে, একটাও সারি নামে না।
                   ⛔ সন্দেহ হলেই আগের মতো পুরোটা নামে (রিপোজিটরির টীকা দেখুন),
                      আর ব্যর্থতায় আগের মতোই `null` — নিচের জমা-তালিকার
                      সুরক্ষাটা এক অক্ষরও বদলায়নি। */
                val cachedRaw = try {
                    dvCachePrefs().getString(dvCacheKey(), null)?.let { org.json.JSONArray(it) }
                } catch (_: Throwable) { null }
                val raw = withContext(Dispatchers.IO) {
                    repository.fetchListRawSmartOrNull(activeDoctorBranch(), cachedRaw)
                }
                binding.progressLoad.visibility = View.GONE
                if (raw == null) {
                    if (!hasCache) {
                        binding.tvEmpty.text = "No doctor/RMP contact yet"
                        binding.tvEmpty.visibility = View.VISIBLE
                        Toast.makeText(this@DoctorVisitActivity, "Could not load — check connection and try again", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                // 🔒 Same permanent rule as loadCachedDoctors() above: this
                // phone's own doctor/remark writes are put back on top, so a
                // just-added doctor is never missing from the fresh list
                // either while its cloud copy is still on the way. The stored
                // cache below is saved from the UNTOUCHED cloud rows (raw), so
                // nothing local is ever written into the cache itself.
                val shown = myDoctorRows(raw)
                val items = ArrayList<DoctorVisitItem>(shown.length())
                for (i in 0 until shown.length()) items.add(DoctorVisitModel.parse(shown.getJSONObject(i)))
                // TK-REQUESTED ADDITION (2026-07-23): referred-patient count
                // per doctor, computed ONCE here for the whole list (not per
                // card) -- same name/mobile match rule "View All" already
                // uses (see showDoctorViewAll's ReferredPatient loop), so
                // the two numbers always agree. Only needs the "patients"
                // table (not "payments" -- that's only needed for View
                // All's paid/due breakdown, not a plain count). Wrapped in
                // its own try/catch: if this extra step fails for any
                // reason, the doctor list itself still loads and shows
                // normally, just with 0 shown for the count.
                val itemsWithCounts = try {
                    withContext(Dispatchers.IO) {
                        // V328 SAFE FAST PATH: the authenticated database function returns
                        // only the few RMP ids whose count is above zero. On any auth,
                        // network, permission, parsing or server failure, the unchanged old
                        // patient-row calculation below runs automatically. The new
                        // commission workflow is separate and untouched.
                        val expectedCode = ModuleAuth.expectedCode(this@DoctorVisitActivity)
                        if (ModuleAuth.isSignedIn && ModuleAuth.personCode != expectedCode) ModuleAuth.signOut()
                        val authReady = if (ModuleAuth.isSignedIn) true
                            else ModuleAuth.signInCurrentSession(applicationContext) == null
                        val serverResult = if (authReady) RmpCommissionRepository.legacyCardCounts()
                            else RmpCommissionRepository.RepoResult<Map<String, Int>>(false)

                        // Cloud rows are known to the server. An item overlaid from this
                        // phone but not yet in cloud is intentionally NOT assigned a guessed
                        // zero; only that rare case uses the old patient read for its count.
                        val cloudIds = HashSet<String>()
                        for (i in 0 until raw.length()) {
                            raw.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { cloudIds.add(it) }
                        }
                        val needsOldForLocalOverlay = items.any { it.id !in cloudIds }

                        if (serverResult.ok && serverResult.value != null && !needsOldForLocalOverlay) {
                            val counts = serverResult.value
                            return@withContext items.map { doc -> doc.copy(referredCount = counts[doc.id] ?: 0) }
                        }

                        // 🔒 SPEED FIX (28.07.2026, khata row B26): this count reads only
                        // refBy and refDoctorMobile -- nothing else is downloaded now.
                        // 🔒 ব্রাঞ্চের নিয়ম (29.07.2026, খাতার সারি B101): এই গোনাটা
                        // এতদিন **সব ব্রাঞ্চের** রোগী গুনত, অথচ উপরের ডাক্তারের
                        // তালিকা (`myDoctorRows`) শুধু নিজের ব্রাঞ্চ দেখায় — তাই
                        // স্টাফের পর্দায় অন্য ব্রাঞ্চের রোগীও সংখ্যায় ঢুকে যেত।
                        // ⛔ মাস্টারের (branch = "All") কিছুই বদলায়নি।
                        // ⛔ ছাঁকনিটা হুবহু `myDoctorRows`-এর নিয়মেই — ব্রাঞ্চ ফাঁকা
                        //    থাকলে সারিটা আগের মতোই রাখা হয়, কোনো সারি হারায় না।
                        val myBranch = user.branch
                        val patients = SupabaseClient.fetchListSlim("patients", null, 5000, "id,refBy,refDoctorMobile,branch,updatedAt")
                        val patientRefs = ArrayList<Pair<String, String>>(patients.length())
                        for (i in 0 until patients.length()) {
                            val pat = patients.optJSONObject(i) ?: continue
                            if (myBranch.isNotBlank() && myBranch != "All") {
                                val pb = pat.s("branch")
                                if (pb.isNotBlank() && !pb.equals(myBranch, ignoreCase = true)) continue
                            }
                            val refBy = pat.s("refBy").trim().lowercase()
                            val refMob = pat.s("refDoctorMobile").filter { it.isDigit() }.takeLast(10)
                            if (refBy.isNotBlank() || refMob.isNotBlank()) patientRefs.add(refBy to refMob)
                        }
                        items.map { doc ->
                            val docName = doc.name.trim().lowercase()
                            val docMobile = doc.mobile.filter { it.isDigit() }.takeLast(10)
                            val oldCount = patientRefs.count { r ->
                                (r.first.isNotBlank() && r.first == docName) || (r.second.isNotBlank() && r.second == docMobile)
                            }
                            // If the server result is valid, retain it for every cloud RMP
                            // and use the old count only for a not-yet-cloud local overlay.
                            val count = if (serverResult.ok && serverResult.value != null && doc.id in cloudIds)
                                serverResult.value[doc.id] ?: 0 else oldCount
                            doc.copy(referredCount = count)
                        }
                    }
                } catch (_: Exception) { items }
                allItems = itemsWithCounts
                saveCachedDoctors(raw)
                saveCachedCounts(itemsWithCounts, countsKeyAtStart)   // 🔴 V512 — পরের বার লাফ ঠেকাতে
                renderList()
            } catch (e: Exception) {
                binding.progressLoad.visibility = View.GONE
                if (!hasCache) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    Toast.makeText(this@DoctorVisitActivity, "Could not load — check connection and try again", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun callNumber(mobile: String) {
        try {
            // TK-REQUESTED (2026-07-24): "everywhere calling is possible in
            // the project" -- shared CallChooser.kt (Phone/Superfone/etc.
            // picker, Truecaller excluded).
            CallChooser.open(this, mobile)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No phone app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsApp(mobile: String) {
        // 🔒 V235 (TK, WhatsApp Chooser project-wide): সরাসরি wa.me না খুলে কেন্দ্রীয়
        // chooser দিয়ে Personal/Business বাছাই (একটি থাকলেও silent default নয়)।
        WhatsAppMessageChooser.send(this, mobile)
    }

    /**
     * 🔒 TK-ORDER (30.07.2026 সকাল ৮.৪৫, ছবিসহ · খাতার সারি B156):
     * *"View all চাপার পরে স্ক্রিনটা আসছে হাফ, সেটাকে আমি বলেছিলাম ফুল ডিসপ্লে
     * রাখতে।"* — ⛔ TK-কে এটা **দ্বিতীয়বার** বলতে হয়েছে।
     *
     * **আগে কেন কাজ করেনি:** `window.setLayout(MATCH_PARENT, MATCH_PARENT)` করা
     * ছিল, আর ভিতরের root-এও MATCH_PARENT বসানো ছিল। কিন্তু `AlertDialog`
     * আমাদের ভিউটাকে **নিজের কয়েকটা কনটেইনারের ভিতরে** ঢোকায় (customPanel →
     * custom FrameLayout), আর ওই কনটেইনারগুলোর উচ্চতা **WRAP_CONTENT** — তাই
     * ভিতরে যতটুকু লেখা, ডায়ালগ ততটুকুই উঁচু হত, বাকি জায়গায় পিছনের তালিকা
     * আধা-দেখা যেত। আমাদের বসানো MATCH_PARENT ওই কনটেইনারের কাছে পৌঁছাতই না।
     *
     * **এখন:** পর্দা দেখানোর **পরে** ভিউ থেকে উপরের দিকে হেঁটে (`android.R.id.content`
     * পর্যন্ত) প্রতিটা কনটেইনারের উচ্চতা MATCH_PARENT করে দেওয়া হয় — তাই
     * ডায়ালগ সত্যিই পুরো পর্দা জুড়ে বসে।
     *
     * ⛔ DecorView-এ হাত দেওয়া হয় না (`android.R.id.content`-এ থেমে যায়), তাই
     *    উইন্ডোর অন্য কোনো আচরণ বদলায় না। পুরোটা `try`-এর ভিতরে, কোনো ফোনে
     *    গঠন আলাদা হলেও অ্যাপ থামবে না — খারাপ ক্ষেত্রে আগের মতোই দেখাবে।
     */
    private fun forceDialogFullScreen(dlg: android.app.Dialog, content: View) {
        try {
            dlg.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            var v: View? = content
            while (v != null) {
                val lp = v.layoutParams
                if (lp != null) {
                    lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    v.layoutParams = lp
                }
                if (v.id == android.R.id.content) break
                v = v.parent as? View
            }
        } catch (_: Throwable) { }
    }

    /**
     * 🔒🔒 খাতার সারি B185 (TK, 30.07.2026 বিকেল ৫.০০): *"ডক্টর ভিজিট অপশনে
     * মেসেজ ১ কাজ করছে, বাকি ২ থেকে ৪ কাজ করছে না।"*
     *
     * **আসল কারণ (কোড ধরে):** বার্তা ২·৩·৪-এ পপ-আপের ভিতরে পপ-আপ **তিন ধাপ**
     * খোলে — ⚡ Action মেনু → Select Patient → বার্তা পাঠানোর পর্দা। আগের
     * পপ-আপটা বন্ধ **হওয়ার মাঝপথেই** পরেরটা খোলা হত, তাই নতুন পপ-আপের
     * উইন্ডোটা ঠিকভাবে সামনে আসত না — Select Patient দেখা যেত, কিন্তু নামের
     * উপর চাপ দিলে কিছুই হত না। বার্তা ১-এ ধাপ মাত্র দুটো, তাই সেটা কাজ করত —
     * TK-এর দেখা ঠিক এই পার্থক্যটাই।
     *
     * **ওষুধ:** আগের পপ-আপটা **আগে পুরোপুরি বন্ধ** করা হয়, তারপর পরেরটা খোলা
     * হয় (`afterUi` — এক পলক পরে, মূল থ্রেডেই)। ⛔ বার্তার লেখা · ভাষা ·
     * তালিকা · কোনো ডিজাইন এক অক্ষরও বদলায়নি; শুধু খোলার ক্রম ঠিক হলো।
     * ⛔ পর্দা বন্ধ হয়ে গেলে কিছুই খোলে না, আর কিছু ভুল হলে নীরব না থেকে
     *    স্টাফ একটা সহজ বার্তা দেখেন।
     */
    private fun afterUi(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (isFinishing || isDestroyed) return@post
            try {
                block()
            } catch (_: Throwable) {
                Toast.makeText(this, "Could not open this message — please try again", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 🔒 TK-ORDER (30.07.2026 সকাল ৮.৪৫ · খাতার সারি B156): *"সেই ডাক্তারকে
     * পেশেন্ট পাঠানোর জন্য ধন্যবাদ বার্তা এখানে নেই... কোন পেসেন্ট কবে পাঠিয়েছে
     * তার কত বিল হয়েছে সে সমস্ত ডিটেইলসও যেন ডাক্তারকে পাঠানো যায়।"*
     *
     * লেখাটা অ্যাপ নিজেই তৈরি করে; স্টাফ শুধু WhatsApp না SMS বেছে Send চাপবেন।
     * ⛔ খরচ শূন্য — কোনো গেটওয়ে/নিবন্ধন লাগে না (রোগীর বার্তার ঠিক একই নিয়ম)।
     */
    // 🔴🟢 খাতার সারি B433 (TK-নির্দেশ, 05.08.2026 — "পেশেন্ট বললাম মানে
    // RMP/ডাক্তারদের ক্ষেত্রে আলাদা নিয়ম হবে না, নিয়ম একই হবে")। রোগীর
    // বার্তার (`PatientMessage.presentSendBox()`) ঠিক একই "আগে পাঠানো
    // হয়েছে কিনা" যাচাই এখানেও — একই শেয়ার্ড `MessageSentLog` টেবিল/
    // ফাংশন ব্যবহার করে (নতুন কিছু বানানো হয়নি, পুনর্ব্যবহার)।
    private fun sendDoctorMessage(mobile: String, doctorName: String, text: String, logKind: String = "") {
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            Toast.makeText(this, "This doctor has no valid 10-digit mobile number", Toast.LENGTH_LONG).show()
            return
        }
        if (logKind.isBlank()) {
            buildAndShowDoctorSendBox(digits, doctorName, text, logKind, null)
            return
        }
        BackgroundWork.run {
            val prior = try { MessageSentLog.checkPrior(digits, logKind, recipientType = "doctor") } catch (_: Throwable) { null }
            try {
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread { buildAndShowDoctorSendBox(digits, doctorName, text, logKind, prior) }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun buildAndShowDoctorSendBox(
        digits: String, doctorName: String, text: String, logKind: String,
        prior: MessageSentLog.PriorSend?
    ) {
        // 🔒 TK-APPROVED (30.07.2026 দুপুর ৩.১০, ফটো-প্রুফে "ওকে পছন্দ হয়েছে"
        //    · খাতার সারি B163): রোগীর বার্তার পপ-আপের **হুবহু একই প্রফেশনাল
        //    চেহারা** — সবুজ হেডার · বার্তার প্রিভিউ · নিচে তিনটে সমান পিল বোতাম।
        //    আগে এটা তালিকা-ধাঁচের (setItems) পপ-আপ ছিল।
        // ⛔ বার্তার লেখা এক অক্ষরও বদলায়নি — শুধু চেহারা।
        val parts = premiumDialogShell("📩", "Send Message")
        parts.body.addView(TextView(this).apply {
            this.text = "To doctor : Dr. " + doctorName.trim().uppercase() + "  ·  " + digits
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#10223A"))
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        })
        val d = resources.displayMetrics.density
        // 🔴🟢 খাতার সারি B433 — আগে এই একই ধরনের বার্তা এই ডাক্তারকে
        // পাঠানো হয়ে থাকলে সতর্কতা (মনে করিয়ে দেয়, আটকায় না)।
        if (prior != null) {
            parts.body.addView(TextView(this).apply {
                this.text = MessageSentLog.warningText(prior)
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#8A5A00"))
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#FFF6E0"))
                    cornerRadius = 10f * d
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 0, 0, (4 * d).toInt())
                layoutParams = lp
            })
        }
        parts.body.addView(TextView(this).apply {
            this.text = text
            textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#1E2A3A"))
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F7FAFC"))
                cornerRadius = 12f * d
                setStroke((1 * d).toInt(), android.graphics.Color.parseColor("#D8E1EC"))
            }
            // 🔒 TK-এর নির্দেশ (02.08.2026, স্ক্রিনশটসহ — একই বাগ রোগীর বার্তার
            // পপ-আপে ধরা পড়েছিল, PatientMessage.kt-এ ঠিক হয়েছে; এখানেও একই
            // কারণ ছিল বলে একসঙ্গে ঠিক করা হলো): MATCH_PARENT চওড়া না থাকলে
            // লম্বা লাইন ডানে কেটে যেত, নিচের লাইনে নামত না।
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        // 🔴🟢 খাতার সারি B433 — সত্যিই পাঠানো হলে (WhatsApp/SMS) ব্যাকগ্রাউন্ডে
        // রেকর্ড হয়।
        fun logSent(channel: String) {
            if (logKind.isBlank()) return
            BackgroundWork.run {
                try {
                    val staff = NativeSession.current(this)
                    MessageSentLog.record(
                        digits, logKind, "", doctorName,
                        staff?.mobile ?: "", staff?.name ?: "", channel, recipientType = "doctor"
                    )
                } catch (_: Throwable) { }
            }
        }
        parts.actionRow.addView(pillButton("💬  WhatsApp", "#0C9E33").apply {
            setOnClickListener {
                parts.dialog.dismiss()
                // 🔒 TK-ORDER (31.07.2026): Personal/Business WhatsApp দুটোই
                // থাকলে চুস করার জন্য chooser দেখায় — `WhatsAppMessageChooser.kt`।
                logSent("whatsapp")
                WhatsAppMessageChooser.send(this@DoctorVisitActivity, digits, text)
            }
        })
        parts.actionRow.addView(pillButton("✉  SMS", "#1E5AB4").apply {
            setOnClickListener {
                parts.dialog.dismiss()
                try {
                    logSent("sms")
                    val i3 = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits"))
                    i3.putExtra("sms_body", text)
                    startActivity(i3)
                } catch (_: Throwable) {
                    Toast.makeText(this@DoctorVisitActivity, "No Message app found on this phone", Toast.LENGTH_SHORT).show()
                }
            }
        })
        parts.actionRow.addView(pillButton("Later", "#E4E8EE", android.graphics.Color.parseColor("#3C4859")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.dialog.show()
    }

    /** TK APPROVED (2026-07-16): premium dialog shell (navy header + rounded
     *  white card + colored action row) — same visual pattern already used
     *  by PaymentActivity's "Add Treatment Payment" dialog, reused here so
     *  the whole app stays visually consistent. Caller adds its own form
     *  fields into the returned body container and its own buttons into the
     *  returned action row, then calls dialog.show(). Nothing about what
     *  data each dialog collects/saves changes — this only replaces the
     *  plain default AlertDialog look with the app's existing premium look. */
    private fun premiumDialogShell(titleIcon: String, titleText: String): PremiumDialogParts {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f * d
                setStroke(dp(1), android.graphics.Color.parseColor("#145A32"))
            }
        }
        root.addView(TextView(this).apply {
            text = "$titleIcon  $titleText"
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#145A32"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(4))
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(420))
        }
        root.addView(scroll)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(actionRow)

        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // 🔒 খাতার সারি B158: বাংলা বন্ধ থাকা স্টাফের জন্য এই পপ-আপের লেখাও
        // বাংলা-মুক্ত করা হয়। পপ-আপ দেখানোর পরেই ডাকা হয় (তখনই ভিউগুলো তৈরি
        // থাকে)। ⛔ বাংলা বন্ধ না থাকলে এটা কিছুই করে না।
        dialog.setOnShowListener { try { NoBengali.installDialog(dialog) } catch (_: Throwable) { } }
        return PremiumDialogParts(dialog, body, actionRow)
    }

    /** Small colored icon+label field header, reused across the premium
     *  dialogs below (e.g. "👨‍⚕️ Doctor Name") instead of a plain textSize-only
     *  label — look-only change, the field it labels is unchanged. */
    private fun fieldLabel(icon: String, text: String, marginTopDp: Int = 16): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = "$icon  $text"
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#B42318"))
            setPadding(0, (marginTopDp * d).toInt(), 0, (4 * d).toInt())
        }
    }

    private fun styleInput(v: EditText) {
        val d = resources.displayMetrics.density
        v.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        val padH = (14 * d).toInt(); val pad = (12 * d).toInt()
        v.setPadding(padH, pad, padH, pad)
        v.setTextColor(android.graphics.Color.parseColor("#10223A"))
    }

    /** TK-DECISION (2026-07-22): mark the exact field the user got wrong with a
     *  red box (not one generic "everything mandatory" message). Cleared back
     *  to the normal style the moment the user edits that field. */
    private fun setFieldError(v: View) {
        val d = resources.displayMetrics.density
        val padH = (14 * d).toInt(); val pad = (12 * d).toInt()
        v.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12f * d
            setColor(android.graphics.Color.parseColor("#FFF4F4"))
            setStroke((1.7f * d).toInt(), android.graphics.Color.parseColor("#E23B3B"))
        }
        v.setPadding(padH, pad, padH, pad)
    }

    /** Reset an EditText's error box back to the normal input style whenever
     *  the user starts fixing it. */
    private fun clearErrorOnEdit(v: EditText) {
        v.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { styleInput(v) }
        })
    }

    /** Pill-style colored button, reused for every premium dialog's action
     *  row below (Save/Cancel/Close/etc.) instead of AlertDialog's plain
     *  default text buttons. */
    private fun pillButton(text: String, bg: String, textColor: Int = android.graphics.Color.WHITE): android.widget.Button {
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
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = (6 * d).toInt(); lp.marginEnd = (6 * d).toInt()
            layoutParams = lp
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
        }
    }

    private data class PremiumDialogParts(val dialog: AlertDialog, val body: LinearLayout, val actionRow: LinearLayout)

    private fun showAddDoctorDialog() {
        var pickedNextDate = ""

        val parts = premiumDialogShell("🧑‍⚕️", "Add Doctor/RMP")
        val container = parts.body

        container.addView(fieldLabel("🧑‍⚕️", "Doctor Name *", 0))
        val nameInput = EditText(this).apply { hint = "Doctor Name" }
        styleInput(nameInput)
        clearErrorOnEdit(nameInput)
        container.addView(nameInput)

        container.addView(fieldLabel("📱", "Mobile *"))
        val mobileInput = EditText(this).apply { hint = "Mobile (10-digit)" }
        styleInput(mobileInput)
        // TK-DECISION (2026-07-22): standard phone field -- MobileInput enforces
        // exactly 10 digits, strips a pasted "+91.."/country code to the last 10,
        // blocks an 11th digit, cleans backspace (no stray junk), blocks
        // autofill/number suggestions. Copy/paste + long-press copy are the
        // EditText defaults, kept. "+91" is added only on save (normalized()).
        MobileInput.attach(mobileInput)
        clearErrorOnEdit(mobileInput)
        container.addView(mobileInput)

        // 🟢 B630 (11.08.2026, TK-নির্দেশ): নম্বর লেখার সাথে সাথেই "আগে সেভ আছে" দেখানো
        //   (এই ফোনের জানা তালিকায় প্রাইমারি বা বাড়তি নম্বরে মিললে) + একই ডাক্তারের
        //   একাধিক নম্বর সেভ। ⛔ ডিজাইন প্লেইন, calendar-emoji নেই; মূল mobile অটুট।
        val mobileStatus = TextView(this).apply {
            textSize = 12.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        container.addView(mobileStatus)
        val altFields = ArrayList<EditText>()
        fun dupNameFor(ten: String): String? {
            if (ten.length != 10) return null
            return allItems.firstOrNull { repository.doctorItemHasNumber(it, ten) }?.name
        }
        fun paintStatus(tv: TextView, ten: String) {
            if (ten.length != 10) { tv.text = ""; return }
            val dn = dupNameFor(ten)
            if (dn != null) { tv.text = "⚠️ Already saved: $dn"; tv.setTextColor(android.graphics.Color.parseColor("#C0392B")) }
            else { tv.text = "✓ New number"; tv.setTextColor(android.graphics.Color.parseColor("#0C9E33")) }
        }
        mobileInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { paintStatus(mobileStatus, MobileInput.digits(mobileInput)) }
        })
        val altBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(altBox)
        fun addAltNumberRow() {
            val d = resources.displayMetrics.density
            val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (6 * d).toInt(), 0, 0) }
            val rowL = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val ime = EditText(this).apply { hint = "Another number (10-digit)" }
            styleInput(ime); MobileInput.attach(ime)
            ime.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val st = TextView(this).apply { textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, (3 * d).toInt(), 0, 0) }
            val rm = TextView(this).apply {
                text = "✕"; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#C0392B"))
                setPadding((14 * d).toInt(), 0, (6 * d).toInt(), 0)
                setOnClickListener { altBox.removeView(wrap); altFields.remove(ime) }
            }
            ime.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { paintStatus(st, MobileInput.digits(ime)) }
            })
            rowL.addView(ime); rowL.addView(rm)
            wrap.addView(rowL); wrap.addView(st)
            altBox.addView(wrap)
            altFields.add(ime)
        }
        container.addView(TextView(this).apply {
            text = "＋ Add another number"
            textSize = 13.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#166534"))
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (2 * resources.displayMetrics.density).toInt())
            setOnClickListener { addAltNumberRow() }
        })

        container.addView(fieldLabel("🏥", "Branch *"))
        val branchSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, branches)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        if (user.branch != "All") {
            val idx = branches.indexOf(user.branch)
            if (idx >= 0) branchSpinner.setSelection(idx)
        }
        container.addView(branchSpinner)

        container.addView(fieldLabel("📍", "Area / Address"))
        val areaInput = EditText(this).apply { hint = "Area / Address" }
        styleInput(areaInput)
        container.addView(areaInput)

        container.addView(fieldLabel("📝", "Remarks (call/visit discussion) *"))
        val remarkInput = EditText(this).apply { hint = "Remarks" }
        styleInput(remarkInput)
        clearErrorOnEdit(remarkInput)
        container.addView(remarkInput)

        container.addView(fieldLabel("⏰", "Next Call Date (tap to pick, optional — auto ${FollowUpModel.displayDate(DoctorVisitModel.defaultNextDate())} if left blank)"))
        val nextDateValue = TextView(this).apply {
            text = "Not set"
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, 0)
            setTextColor(android.graphics.Color.parseColor("#145A32"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        nextDateValue.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                val cal2 = Calendar.getInstance().apply { set(y, m, d) }
                pickedNextDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal2.time)
                nextDateValue.text = FollowUpModel.displayDate(pickedNextDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = cal.timeInMillis
            }.show()
        }
        container.addView(nextDateValue)

        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        // TK-REQUESTED FIX (2026-07-23): a fast double-tap here used to fire
        // Save twice before the first insert landed -- both duplicate-checks
        // ran against the cloud before either row existed, so both said "not
        // found" and both inserts went through (this is how the duplicate
        // "Dr. Jafar" row happened). The button now disables itself the
        // moment it's tapped, and only re-enables if the save didn't
        // actually succeed (duplicate found, or save failed) -- a real
        // retry is still possible, just not an accidental double-fire.
        val saveDoctorBtn = pillButton("💾 Save", "#0C9E33")
        saveDoctorBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val mobile = MobileInput.digits(mobileInput)
            val branch = branchSpinner.selectedItem?.toString() ?: ""
            val remark = remarkInput.text.toString().trim()
            styleInput(nameInput); styleInput(mobileInput); styleInput(remarkInput)
            var firstMsg: String? = null
            var firstBad: View? = null
            fun bad(v: View, msg: String) { setFieldError(v); if (firstMsg == null) { firstMsg = msg; firstBad = v } }
            if (name.isBlank()) bad(nameInput, "Doctor Name দিন")
            if (mobile.length != 10) bad(mobileInput, "সঠিক 10 ডিজিট মোবাইল দিন")
            if (branch.isBlank()) bad(branchSpinner, "Branch বাছুন")
            if (remark.isBlank()) bad(remarkInput, "Remarks দিন")
            if (firstMsg != null) {
                Toast.makeText(this@DoctorVisitActivity, firstMsg, Toast.LENGTH_SHORT).show()
                (firstBad as? EditText)?.requestFocus()
                return@setOnClickListener
            }
            // 🟢 B630: বাড়তি নম্বরগুলো সংগ্রহ ও যাচাই (ফাঁকা বাদ; প্রতিটা ঠিক ১০ ডিজিট; একই নম্বর দুবার নয়)।
            val extras = ArrayList<String>()
            for (f in altFields) {
                val dd = MobileInput.digits(f)
                if (dd.isBlank()) continue
                if (dd.length != 10) { setFieldError(f); Toast.makeText(this@DoctorVisitActivity, "Please enter a valid 10-digit alternate number.", Toast.LENGTH_SHORT).show(); f.requestFocus(); return@setOnClickListener }
                if (dd == mobile || extras.contains(dd)) { setFieldError(f); Toast.makeText(this@DoctorVisitActivity, "The same number cannot be entered twice.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                extras.add(dd)
            }
            val nextDate = pickedNextDate.ifBlank { DoctorVisitModel.defaultNextDate() }
            saveDoctorBtn.isEnabled = false
            lifecycleScope.launch {
                // 🔒 (03.08.2026, খাতার সারি B190/B373-এর গভীর পুনর্বিবেচনা,
                // TK-অনুমোদনে) — এই স্ক্রিনের আগে-থেকে-লোড হওয়া তালিকা
                // (`allItems`, MyPhoneWrites overlay-সহ) পাঠানো হলো — শুধু
                // ক্লাউড সত্যিই ব্যর্থ হলে (নেট) কাজে লাগে, স্বাভাবিক নেটে
                // কিছুই বদলায় না।
                // 🟢 B630 (ক.১): প্রাইমারি + প্রতিটা বাড়তি নম্বর — কোনোটা আগে সেভ থাকলে Save আটকাবে।
                val allNums = ArrayList<String>().apply { add(mobile); addAll(extras) }
                var dupNum = ""; var dupName = ""
                for (num in allNums) {
                    val d = withContext(Dispatchers.IO) { repository.checkDuplicate(num, allItems) }
                    if (d.found) { dupNum = num; dupName = d.name; break }
                }
                if (dupNum.isNotEmpty()) {
                    Toast.makeText(this@DoctorVisitActivity, "Already saved: $dupName ($dupNum)", Toast.LENGTH_LONG).show()
                    saveDoctorBtn.isEnabled = true
                    return@launch
                }
                val altCsv = extras.joinToString(",") { "+91$it" }
                val ok = withContext(Dispatchers.IO) {
                    repository.addNewDoctor(name, mobile, branch, areaInput.text.toString().trim(), remark, nextDate, user.mobile, this@DoctorVisitActivity, altCsv)
                }
                // 🔒 (03.08.2026, খাতার সারি B190/B373, TK-অনুমোদনে) — আসল কারণ:
                // addNewDoctor() ব্যর্থ হলেও (ok=false) MyPhoneWrites.remember()
                // **সবসময়ই** ডাকা হয় (repository-এর কোড দেখে নিশ্চিত), তাই এই
                // ডাক্তার তালিকায় সঙ্গে সঙ্গেই দেখা যায় — আর SupabaseClient.upsert()
                // ব্যর্থ হলে **প্রতিটা** লেখাই (৪১টা upsert-এর সবক'টা, ডাক্তার-সেভ
                // সহ) নিজে থেকেই CloudWriteQueue-তে retry-র জন্য জমা হয়ে যায়
                // (SupabaseClient.kt-এ যাচাই করা)। **আগে যা হত:** "Failed —
                // check connection" দেখিয়ে Save বোতাম আবার চালু করে দেওয়া হত —
                // অথচ ডাক্তার ততক্ষণে ফোনে সেভ ও retry-কিউতে জমা হয়ে গেছে;
                // স্টাফ আবার Save চাপলে **নতুন আইডি নিয়ে দ্বিতীয় একটা এন্ট্রি**
                // তৈরি হত (checkDuplicate()-ও ধীর নেটে "না পাওয়া গেছে" বলত,
                // তাই আটকাত না) — এটাই আসল ডুপ্লিকেট-ডাক্তার তৈরির কারণ।
                // **এখন:** Registration/Enquiry-র প্রমাণিত নিয়মে — ফোনে সেভ
                // হওয়াই "সেভ হয়েছে" ধরা হয়, Save বোতাম আর দ্বিতীয়বার চাপা যায়
                // না (dialog বন্ধ, তালিকা রিলোড) — নেট এলে ব্যাকগ্রাউন্ডে নিজে
                // থেকেই ক্লাউডে পৌঁছে যাবে। ⛔ `ok=true`/`false` অনুযায়ী শুধু
                // বার্তার লেখা আলাদা, বাকি আচরণ (dialog dismiss + loadList())
                // দুটো ক্ষেত্রেই একই।
                Toast.makeText(this@DoctorVisitActivity, if (ok) "Doctor/RMP saved" else "Saved on this phone — will sync when online", Toast.LENGTH_SHORT).show()
                parts.dialog.dismiss(); loadList()
            }
        }
        parts.actionRow.addView(saveDoctorBtn)
        parts.dialog.show()
    }

    private fun showLogCallDialog(item: DoctorVisitItem) {
        var pickedNextDate = ""
        // 🔒🔒 খাতার সারি B193 (TK, 30.07.2026 রাত): "EXPECTED" ঘরের জন্য —
        // ডাক্তার যদি কল করার সময় বলেন "এই তারিখে একটা পেশেন্ট পাঠাতে
        // পারি", স্টাফ এখানে সেই তারিখ লিখবেন। আগে থেকে কিছু সেট করা থাকলে
        // (item.expectedPatientDate) সেটাই প্রি-ফিল থাকে — না ছুঁলে সেটাই
        // আবার সেভ হবে, অজান্তে মুছে যাবে না।
        var pickedExpectedDate = item.expectedPatientDate
        val parts = premiumDialogShell("📞", "Doctor Call Remarks")
        val container = parts.body

        container.addView(TextView(this).apply {
            text = "${item.name} · ${item.mobile}"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            copyOnLongPress("Name/Mobile", "${item.name} · ${item.mobile}")
        })

        // TK-REQUESTED (2026-08-13): a call must have its own discussion note.
        // Keep it separate from the next/expected dates and pass this exact text
        // into callHistory, matching the already-working web call form.
        container.addView(fieldLabel("📝", "Remarks — what was discussed *"))
        val remarkInput = EditText(this).apply {
            hint = "Write what was discussed with this RMP"
            minLines = 3
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val d = resources.displayMetrics.density
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        }
        container.addView(remarkInput)

        // 🔴🔒 V444 (TK-নির্দেশ ১৮.০৮.২০২৬: "প্রথমটা হাইড করে রাখলে কেমন হয়") —
        // এই ঘরটা লুকানো হলো, ফর্মে আর দেখা যাবে না। ⛔ কার্যকারিতা এক
        // অক্ষরও বদলায়নি: `pickedNextDate` খালিই থাকবে, তাই Save-এ আগের
        // মতোই অটো ${FollowUpModel.displayDate(DoctorVisitModel.defaultNextDate())}-এর
        // মতো ডিফল্ট তারিখ বসবে (৩০ দিন পর) — তাই বেল-রিমাইন্ডার ও
        // Pending/Called/All RMP তালিকার সাজানো ক্রম আগের মতোই অক্ষত।
        // ঘর আবার ফিরিয়ে আনতে চাইলে নিচের visibility=GONE-টা সরালেই হবে।
        container.addView(fieldLabel("⏰", "Next Call Date (tap to pick, optional — auto ${FollowUpModel.displayDate(DoctorVisitModel.defaultNextDate())} if left blank)").apply { visibility = View.GONE })
        val nextDateValue = TextView(this).apply {
            text = "⏰  Tap here to set next call date  ▸"
            setTextColor(android.graphics.Color.parseColor("#145A32"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val d = resources.displayMetrics.density
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            visibility = View.GONE
        }
        nextDateValue.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                val cal2 = Calendar.getInstance().apply { set(y, m, d) }
                pickedNextDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal2.time)
                nextDateValue.text = FollowUpModel.displayDate(pickedNextDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = cal.timeInMillis
            }.show()
        }
        container.addView(nextDateValue)

        // 🔒🔒 খাতার সারি B193 (TK, 30.07.2026 রাত — "EXPECTED" ঘর): নতুন
        // ঐচ্ছিক তারিখ-ঘর, ঠিক Next Call Date-এর নিচেই, একই ধাঁচে। TK-এর
        // কথা: *"যদি কোন ডাক্তার তারিখ নির্ণয় করে জবাব দেয় আমার একটা
        // পেশেন্ট আছে সে আসতে পারে, সেই ক্ষেত্রে এই এক্সপেক্টেড এর ঘরে
        // সেই লোকের নাম্বারটা চলে যাবে এবং সেখানে তারিখ মেনশন থাকবে।"*
        // ⛔ সম্পূর্ণ ঐচ্ছিক — ফাঁকা রাখলে EXPECTED তালিকায় এই ডাক্তার
        //    দেখাবেন না, বাকি সব (Next Call/Remarks/callHistory) আগের
        //    মতোই কাজ করবে।
        container.addView(fieldLabel("🤞", "Expected Patient Date (optional — doctor said a patient may come)"))
        val expectedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val expectedDateValue = TextView(this).apply {
            text = if (pickedExpectedDate.isBlank()) "🤞  Tap here to set expected date  ▸" else FollowUpModel.displayDate(pickedExpectedDate)
            setTextColor(android.graphics.Color.parseColor("#BE185D"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val d = resources.displayMetrics.density
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        expectedDateValue.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                val cal2 = Calendar.getInstance().apply { set(y, m, d) }
                pickedExpectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal2.time)
                expectedDateValue.text = FollowUpModel.displayDate(pickedExpectedDate)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            // ⛔ এখানে ইচ্ছে করেই কোনো minDate বসানো হয়নি (Next Call
            // Date-এর মতো) — ডাক্তার অতীতের কোনো তারিখও বলতে পারেন
            // (যেমন "গত সপ্তাহে বলেছিলাম"), তাই আটকানো হয়নি।
        }
        val clearExpected = TextView(this).apply {
            text = "✕ Clear"
            textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#8A97AB"))
            // ⛔ সংশোধন (30.07.2026 রাত): এখানে আগে `dp(10)` ডাকা হচ্ছিল, কিন্তু
            // ওই সহায়ক ফাংশনটা `premiumDialogShell()`-এর **ভিতরের** স্থানীয়
            // ফাংশন — এখান থেকে দেখাই যায় না, তাই Android Studio-তে বিল্ড
            // ভাঙত। এখানে সরাসরি density দিয়ে হিসাব করা হলো, ফল হুবহু একই।
            val padStart = (10 * resources.displayMetrics.density).toInt()
            setPadding(padStart, 0, 0, 0)
            setOnClickListener {
                pickedExpectedDate = ""
                expectedDateValue.text = "🤞  Tap here to set expected date  ▸"
            }
        }
        expectedRow.addView(expectedDateValue)
        expectedRow.addView(clearExpected)
        container.addView(expectedRow)

        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        // 🔴 B424 (05.08.2026, TK-রিপোর্ট, ছবিসহ — Dr Jiyaul Hok-এর কল-হিস্ট্রিতে
        // একই তারিখের একই নোট দুইবার): আসল কারণ — এই "Save Call" বোতামে
        // দ্রুত দুইবার চাপ পড়লে `repository.logCall()` দুইবার চলতে পারত;
        // `logCall()` নিজেই আগে-পুরনো-হিস্ট্রি-পড়ে-তারপর-নতুন-এন্ট্রি-জোড়ার
        // (read-then-write) কাজ করে বলে দ্রুত দুইবার চাপলে দুটো কলই প্রায়
        // একই পুরনো তালিকা পড়ে ফেলত, তাই দুটো লেখাতেই আলাদা করে নতুন এন্ট্রি
        // জুড়ে যেত — ঠিক B334/B414-এর একই ডাবল-চাপ ধরন। **সমাধান:** চাপার
        // সাথে সাথেই বোতাম বন্ধ হয়ে যায় (একবারই কাজ চলতে পারে); ব্যর্থ হলে
        // (নেট সমস্যা) আবার সক্রিয় হয়ে যায়, যাতে স্টাফ আটকে না থাকেন।
        val saveCallBtn = pillButton("💾 Save Call", "#0C9E33")
        saveCallBtn.setOnClickListener {
            val note = remarkInput.text.toString().trim()
            if (note.isBlank()) {
                remarkInput.error = "Remarks required"
                remarkInput.requestFocus()
                return@setOnClickListener
            }
            saveCallBtn.isEnabled = false
            // 🔴🔴🆕🔒 V434 (TK-রিপোর্ট ১৮.০৮.২০২৬, ছবিসহ — *"Remarks লেখার পর
            // Save হচ্ছে না"*; TK: *"staff বললো হচ্ছে না, Save তাড়াতাড়ি"*)।
            // **কোড ধরে পাওয়া আসল কারণ (অনুমান নয়):**
            //  (১) Save চাপলে বোতামটা সঙ্গে সঙ্গে **ধূসর/নিষ্ক্রিয়** হয়ে যেত
            //      (B424-এর ডাবল-চাপ ঠেকানোর নিয়ম) কিন্তু **"কাজ চলছে" এমন
            //      কোনো ইঙ্গিত ছিল না**। ভিতরে দুটো নেট-কাজ চলে — আগে পুরনো
            //      কল-হিস্ট্রি পড়া (`SupabaseClient` read-timeout **২৫ সেকেন্ড**),
            //      তারপর লেখা (write callTimeout **৬০ সেকেন্ড**)। নেট দুর্বল
            //      হলে স্টাফ **দেড় মিনিট পর্যন্ত** একটা মরা ধূসর বোতামের দিকে
            //      তাকিয়ে থাকতেন — ঠিক "Save হচ্ছে না" যেমন মনে হয়।
            //  (২) ওই কাজের মাঝে কোনো অপ্রত্যাশিত গোলমাল (Throwable) হলে নিচের
            //      Toast/`isEnabled = true` লাইন দুটোতে **পৌঁছানোই হত না** ⇒
            //      বোতামটা **চিরতরে নিষ্ক্রিয়** হয়ে বসে থাকত, পপ-আপ বন্ধ করে
            //      আবার না খোলা পর্যন্ত সেভ করার কোনো উপায় থাকত না।
            //
            // **সমাধান (কোনো সেভ-লজিক বদলায়নি — এক অক্ষরও নয়):**
            //  (ক) চাপার সঙ্গে সঙ্গে বোতামে **"⏳ সেভ হচ্ছে…"** — স্টাফ দেখতে
            //      পান কাজ চলছে, তাই বারবার চাপেন না।
            //  (খ) `try/finally` — যা-ই হোক, বোতাম **সবসময়** আবার সক্রিয় হয় ও
            //      লেখা ফিরে আসে; কখনো আটকে থাকে না।
            //  (গ) ব্যর্থ হলে সত্যি কথাটা বলা হয়: লেখাটা **এই ফোনে রাখা হয়ে
            //      গেছে** (`MyPhoneWrites`) আর ক্লাউডে পাঠানোর জন্য **সারিতে
            //      বসানো আছে** (`CloudWriteQueue`) — নেট এলে নিজে থেকেই যাবে।
            //      আগে শুধু "Failed — check connection" লেখায় স্টাফ ভাবতেন সব
            //      হারিয়ে গেল, তাই আবার লিখতে গিয়ে **দুইবার** এন্ট্রি হত।
            // ⛔ সময়সীমা (timeout) **ইচ্ছে করে বসানো হয়নি** — মাঝপথে কেটে দিলে
            //    লেখাটা সার্ভারে বসে যাওয়ার পরেও "ব্যর্থ" দেখাত, স্টাফ আবার
            //    চাপতেন, আর কল-হিস্ট্রিতে **একই নোট দুইবার** ঢুকত (ঠিক B424)।
            val saveCallLabel = saveCallBtn.text?.toString().orEmpty().ifBlank { "💾 Save Call" }
            saveCallBtn.text = NoBengali.s("⏳ সেভ হচ্ছে…")
            val nextDate = pickedNextDate.ifBlank { DoctorVisitModel.defaultNextDate() }
            lifecycleScope.launch {
                var ok = false
                try {
                    ok = withContext(Dispatchers.IO) {
                        repository.logCall(item.id, note, nextDate, user.mobile, this@DoctorVisitActivity, pickedExpectedDate)
                    }
                } catch (_: Throwable) {
                    ok = false
                } finally {
                    // ⛔ যা-ই ঘটুক — বোতাম আর কখনো আটকে থাকবে না।
                    try { saveCallBtn.text = saveCallLabel; saveCallBtn.isEnabled = true } catch (_: Throwable) { }
                }
                if (isFinishing || isDestroyed) return@launch
                if (ok) {
                    Toast.makeText(this@DoctorVisitActivity, "Doctor call updated", Toast.LENGTH_SHORT).show()
                    try { parts.dialog.dismiss() } catch (_: Throwable) { }
                    loadList()
                } else {
                    // ⛔ দুটো আলাদা অবস্থা, তাই দুটো আলাদা সত্যি কথা —
                    //  · লেখার চেষ্টা হয়েছে ⇒ সারিতে বসে আছে, নেট এলে যাবে;
                    //    আবার লিখলে কল-হিস্ট্রিতে **দুইবার** ঢুকবে (B424)।
                    //  · লেখার আগেই থেমেছে (পুরনো হিস্ট্রি পড়া যায়নি) ⇒ কিছুই
                    //    বসেনি, তাই আবার Save চাপতেই হবে।
                    val queued = DoctorVisitRepository.lastCallWriteQueued
                    Toast.makeText(
                        this@DoctorVisitActivity,
                        NoBengali.s(
                            if (queued) "লেখা রাখা হয়েছে — নেট এলে নিজে থেকেই যাবে। আবার লিখবেন না"
                            else "নেট পাওয়া যায়নি — কিছুই সেভ হয়নি, একটু পরে আবার Save চাপুন"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        parts.actionRow.addView(saveCallBtn)
        parts.dialog.show()
    }

    /**
     * Native port of doctorReferralPatients()/doctorReferralTotals(): lists the
     * patients this doctor referred (patients whose "Ref By" matches the doctor's
     * name / mobile) and totals their bill and collected amount as referral income.
     */
    /**
     * "View All" — one combined view for a doctor: full call history (how many
     * times called, each date + note + who), the patients this doctor referred,
     * and the referral-income totals (Paid / Due) with an Add button.
     */
    private fun showDoctorViewAll(item: DoctorVisitItem) {
        // TK-REPORTED (2026-07-24): "View All" tap gave zero feedback while
        // the data loaded in the background (especially slow on a weak
        // connection) -- it felt broken/unresponsive rather than just
        // loading. This shows instantly on tap, before any network call
        // starts.
        // 🔴 TK-REPORTED (30.07.2026 রাত, ছবিসহ): এই টোস্ট নিজের ফিক্সড
        // সময় (Toast.LENGTH_SHORT, ~২ সেকেন্ড) ধরে পড়ে থাকত -- আসল ডেটা
        // (Referred/Ref. Paid/Ref. Due/তালিকা) তার আগেই এসে পর্দায় বসে
        // গেলেও টোস্ট নিজের সময় শেষ না হওয়া পর্যন্ত সরত না, তাই "লোড হয়ে
        // গেছে তবু লোডিং দেখাচ্ছে" মনে হত। এখন টোস্ট রেফারেন্স ধরে রাখা
        // হলো, আসল ডেটা রেন্ডার হওয়ার সঙ্গে সঙ্গেই (নিচে dataJob.await()-এর
        // পরে) এটা cancel() করে দেওয়া হয় -- তাড়াতাড়ি লোড হলে টোস্ট সঙ্গে
        // সঙ্গে সরে যাবে, ধীরে লোড হলে আগের মতোই আচরণ (কোনো পরিবর্তন নেই)।
        // V450 (19.08.2026, TK-approved): this toast is now shown only when
        // this RMP has no previously successful View-All snapshot on this phone.
        // Re-opening the same RMP shows the last successful data instantly and
        // refreshes the SAME cloud reads silently in the background.
        // ⛔ No extra Supabase request is added by this cache-first change.
        var dvLoadingToast: Toast? = null
        lifecycleScope.launch {
            // CRASH-SAFETY FIX (TK-reported, 2026-07-16): CONFIRMED root cause
            // of "View All crashes to Home" -- this function had NO error
            // handling at all. Any problem while reading/combining the call
            // history, referred-patients, and referral-income data used to
            // crash the WHOLE APP, and Android then relaunches at Login/
            // Dashboard -- exactly matching what TK described.
            try {
                // TK-LOCKED DESIGN (2026-07-23): richer per-item data (name/
                // amount/status/date/mobile kept separate, not pre-joined
                // into one string) so the new card layout below can style
                // each piece individually. The underlying queries/logic that
                // BUILD this data are completely unchanged from before.
                // TK-REQUESTED REDESIGN (2026-07-24): "View All" now shows
                // ONE chronological table (Date/Time | Type/By | Note) --
                // matching Patient Timeline's exact table pattern -- instead
                // of three separate card sections. Each entry now also
                // carries a raw sortable date (rawDate) alongside the
                // display-formatted one, so Call/Referred Patient/Referral
                // Income entries can all be merged and sorted together by
                // when they actually happened.
                // 🔒 B155 model fields are unchanged; the classes now live at Activity level
                // only so the same verified snapshot can be reused on repeat-open.
                // 🔵 V450 (19.08.2026, TK-approved): View All cache-first.
                // Memory only; no disk write and no extra Supabase request.
                val viewAllCacheKey = "viewall_v1_${item.id}"
                fun saveViewAllCache(v: ViewAllData) { doctorViewAllMemoryCache.put(viewAllCacheKey, v) }
                val cachedViewAllData = doctorViewAllMemoryCache.get(viewAllCacheKey)
                if (cachedViewAllData == null) {
                    dvLoadingToast = Toast.makeText(this@DoctorVisitActivity, NoBengali.s("লোড হচ্ছে…"), Toast.LENGTH_SHORT)
                    dvLoadingToast?.show()
                }
                // ⚡ TK-এর নির্দেশ (28.07.2026 ৩.৪৫ pm, ফটো-প্রুফে ফাইনাল):
                // **"View All খুলতে এত বেশি সময় কেন"** — কারণ পর্দা খোলার আগেই
                // পুরো রোগীর তালিকা ও পুরো টাকার তালিকা (৫০০০+৫০০০ সারি পর্যন্ত)
                // নামানো হত, শুধু একজন ডাক্তারের তিনটে সংখ্যা বের করতে।
                // এখন ওই কাজটা **পিছনে** চলে — পর্দা সঙ্গে সঙ্গে খোলে, সংখ্যা ও
                // তালিকা এসে নিজে থেকে বসে যায়।
                // ⛔ একটিও নতুন ক্লাউড-কল নয় — ঠিক সেই একই দুটো কাজ, শুধু পরে।
                val dataJob = lifecycleScope.async(Dispatchers.IO) {
                    // V450: null means the refresh genuinely failed. Do not turn a
                    // connection failure into a fake empty/₹0 snapshot and overwrite cache.
                    val docRows = SupabaseClient.fetchListOrNull("doctor_visits", "id=eq.${item.id}", 1)
                        ?: throw IllegalStateException("doctor_visits refresh failed")
                    val doc = if (docRows.length() > 0) (docRows.optJSONObject(0) ?: org.json.JSONObject()) else org.json.JSONObject()
                    val hist = doc.optJSONArray("callHistory") ?: org.json.JSONArray()
                    val calls = (0 until hist.length()).mapNotNull { i ->
                        val h = hist.optJSONObject(i) ?: return@mapNotNull null
                        // TK-REQUESTED FIX (2026-07-23): "by" used to show
                        // the raw staff mobile number -- resolved to the
                        // staff's actual name here (same lookup pattern
                        // already used for delete-request approver names
                        // in handleApproveDelete), falling back to the raw
                        // mobile if that staff account can't be found.
                        val byMobile = h.s("by")
                        val byName = if (byMobile.isNotBlank()) StaffDirectory.findAccount(byMobile)?.name ?: byMobile else ""
                        CallLine(h.s("date"), FollowUpModel.displayDate(h.s("date")), h.s("note").ifBlank { "\u2014" }, byName)
                    }

                    // TK-REPORTED PERFORMANCE FIX (2026-07-24): "View All"
                    // felt slow to open, especially on a weaker connection.
                    // These two fetches are independent (neither depends on
                    // the other's result), but used to run one after the
                    // other -- running them concurrently (async/awaitAll)
                    // instead roughly halves the time spent waiting on
                    // these two full-table fetches. Same data, same
                    // filtering logic below, unchanged -- only HOW the two
                    // network calls are scheduled changes.
                    // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): same tables,
                    // same rows, same limit, same order -- ONLY the columns this
                    // block actually reads are downloaded now. ⛔ Not one figure
                    // can change: every line of the matching and adding below is
                    // left word for word, and if a narrowed read ever fails,
                    // fetchListSlim asks for every column again by itself.
                    // 🔒 ব্রাঞ্চের নিয়ম (29.07.2026, খাতার সারি B101): এই তালিকাটা
                    // এতদিন **সব ব্রাঞ্চের** রোগীর নাম · নম্বর · বিল · জমা স্টাফকে
                    // দেখিয়ে দিত। এখন নিচের লুপে ঠিক `myDoctorRows`-এর নিয়মেই
                    // ছাঁকা হয়, তাই কার্ডের সংখ্যার সঙ্গে এই তালিকা সবসময় মিলবে।
                    // V328 SAFE FAST PATH: normally only this RMP's few verified
                    // patient details return. Any identity/auth/network/server/
                    // parsing failure falls back to the untouched old 5,000 +
                    // 5,000-row calculation below. A valid empty result stays
                    // empty; it is never confused with failure.
                    val expectedCode = ModuleAuth.expectedCode(this@DoctorVisitActivity)
                    if (ModuleAuth.isSignedIn && ModuleAuth.personCode != expectedCode) ModuleAuth.signOut()
                    val authReady = if (ModuleAuth.isSignedIn) true
                        else ModuleAuth.signInCurrentSession(applicationContext) == null
                    val fast = if (authReady) RmpCommissionRepository.legacyViewAll(item.id)
                        else RmpCommissionRepository.RepoResult<List<RmpCommissionRepository.LegacyViewAllPatient>>(false)
                    val fastRows = fast.value

                    val referred = if (fast.ok && fastRows != null) {
                        fastRows.map { p ->
                            ReferredPatient(p.referralDate, p.name, p.mobile, p.bill, p.paid, p.patientCode, p.disease)
                        }
                    } else {
                        val patsDeferred = async { SupabaseClient.fetchListSlimOrNull("patients", null, 5000, "id,name,mobile,bill,branch,refBy,refDoctorMobile,registrationDate,date,disease,diagnosis,updatedAt") }
                        val paysDeferred = async { SupabaseClient.fetchListSlimOrNull("payments", null, 5000, "id,mobile,amount,payType,refundApprovalStatus,updatedAt") }
                        val pats = patsDeferred.await() ?: throw IllegalStateException("patients refresh failed")
                        val pays = paysDeferred.await() ?: throw IllegalStateException("payments refresh failed")
                        val paidByMobile = HashMap<String, Double>()
                        for (i in 0 until pays.length()) {
                            val p = pays.optJSONObject(i) ?: continue
                            val payType = p.optString("payType", "")
                            if (payType == "visit_fee" || payType == "attendance_mark") continue
                            val paidEffect = when {
                                PaymentModel.isApprovedRefund(p) -> -p.optDouble("amount", 0.0)
                                PaymentModel.isRefundRow(p) -> 0.0
                                else -> p.optDouble("amount", 0.0)
                            }
                            val m = p.s("mobile").filter { it.isDigit() }.takeLast(10)
                            paidByMobile[m] = (paidByMobile[m] ?: 0.0) + paidEffect
                        }
                        val docName = item.name.trim().lowercase()
                        val docMobile = item.mobile.filter { it.isDigit() }.takeLast(10)
                        val old = mutableListOf<ReferredPatient>()
                        val myBranch = user.branch
                        for (i in 0 until pats.length()) {
                            val pat = pats.optJSONObject(i) ?: continue
                            if (myBranch.isNotBlank() && myBranch != "All") {
                                val pb = pat.s("branch")
                                if (pb.isNotBlank() && !pb.equals(myBranch, ignoreCase = true)) continue
                            }
                            val refBy = pat.s("refBy").trim().lowercase()
                            val refMob = pat.s("refDoctorMobile").filter { it.isDigit() }.takeLast(10)
                            val hit = (refBy.isNotBlank() && refBy == docName) || (refMob.isNotBlank() && refMob == docMobile)
                            if (!hit) continue
                            val m = pat.s("mobile").filter { it.isDigit() }.takeLast(10)
                            val regDate = pat.s("registrationDate").ifBlank { pat.s("date") }
                            old.add(ReferredPatient(regDate, pat.s("name"), m, pat.optDouble("bill", 0.0), paidByMobile[m] ?: 0.0, pat.s("patientId"), pat.s("disease").ifBlank { pat.s("diagnosis") }))
                        }
                        old
                    }
                    val refPayments = doc.optJSONArray("referralPayments") ?: org.json.JSONArray()
                    // V381: the visible history is the source of truth for these legacy totals.
                    // The old scalar referralPaid/referralDue fields can be stale (for example,
                    // history contains a Paid ₹1,500 row while the scalar still says ₹0).
                    // Read-only calculation: no record, Cloud row or new commission table changes.
                    var verifiedLegacyPaid = 0.0
                    var verifiedLegacyDue = 0.0
                    val refIncome = (0 until refPayments.length()).mapNotNull { i ->
                        val e = refPayments.optJSONObject(i) ?: return@mapNotNull null
                        val entryAmount = e.optDouble("amount", 0.0)
                        if (e.s("status").equals("Paid", ignoreCase = true))
                            verifiedLegacyPaid += entryAmount
                        else
                            verifiedLegacyDue += entryAmount
                        RefIncomeLine(
                            e.s("date"),
                            e.s("patient").ifBlank { "Unnamed patient" },
                            e.s("patientMobile"),
                            e.optDouble("amount", 0.0),
                            e.s("status").ifBlank { "Unpaid" },
                            e.s("date"),
                            e.s("id"),
                            e.s("mode"),
                            e.s("referenceNo")
                        )
                    }
                    // V381: Ref. Paid means all money already handed to this RMP.
                    // Allocated advance is already inside the new payment total;
                    // only the still-unallocated balance is added, preventing double count.
                    if (authReady) {
                        val modern = RmpCommissionRepository.rmpSummary(item.id)
                        val advances = RmpCommissionRepository.advancePayments(item.id)
                        val covered = if (advances.ok) (advances.value ?: emptyList()).sumOf { it.legacyCovered } else 0.0
                        // rmpSummary.paid already includes allocated payments + still-unallocated
                        // direct RMP payments. Subtract only the part already represented by
                        // visible legacy Paid rows, so the same money is never counted twice.
                        if (modern.ok && modern.value != null)
                            verifiedLegacyPaid += kotlin.math.max(0.0, modern.value.paid - covered)
                    }
                    ViewAllData(calls, referred, refIncome, verifiedLegacyPaid, verifiedLegacyDue)
                }
                // V450: repeat-open starts from the last successful snapshot instead
                // of fake zeroes. First-ever open remains unchanged (empty + Loading).
                var data = cachedViewAllData ?: ViewAllData(emptyList(), emptyList(), emptyList(), 0.0, 0.0)

                // TK-REQUESTED REDESIGN (2026-07-23): full-screen (not a
                // half-screen popup) with clean card sections instead of a
                // dense bullet list -- approved via photo-proof. This is a
                // plain android.app.Dialog sized to fill the screen (not a
                // new Activity), so nothing about navigation/back-stack
                // changes elsewhere in the app.
                val dg = resources.displayMetrics.density
                fun dgpx(v: Int) = (v * dg).toInt()
                lateinit var fsDialog: AlertDialog
                val root = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.parseColor("#EEF2F5"))
                    // 🔒 TK-ORDER (30.07.2026, ছবিসহ — "এটা ফুল স্ক্রিন চাই"):
                    // window MATCH_PARENT করা হলেও এই root-এর নিজের height
                    // আগে বসানো ছিল না, তাই ডায়ালগ শুধু নিজের ভিতরের লেখার
                    // যতটুকু উচ্চতা দরকার ততটুকুই নিত — বাকি জায়গায় পিছনের
                    // ডাক্তার-তালিকা আধা-দেখা যেত (dim হয়ে)। এখন root-ও
                    // MATCH_PARENT, তাই ভিতরের scroll (weight 1f) পুরো
                    // স্ক্রিন জুড়েই বসে, পিছনের কিছুই দেখা যাবে না।
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                    )
                }
                val headerBar = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dgpx(18), dgpx(20), dgpx(18), dgpx(18))
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(android.graphics.Color.parseColor("#12386B"), android.graphics.Color.parseColor("#1F8A5B"))
                    )
                }
                headerBar.addView(TextView(this@DoctorVisitActivity).apply {
                    text = "←"; textSize = 22f; setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 0, dgpx(14), 0)
                    setOnClickListener { fsDialog.dismiss() }
                })
                headerBar.addView(android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    // 🔒 TK-ORDER (30.07.2026 সকাল ৮.৪৫, ছবিসহ · খাতার সারি B156):
                    // *"হেডারে দেখুন ডান সাইডে ডিলিট করার বাটন রয়েছে, ওখানে
                    // ওটা থাকবে না।"* — তাই হেডার থেকে 🗑️ বোতামটা সরানো হলো।
                    // ⛔ ডিলিট করার ক্ষমতা হারায়নি: ⚡ Action মেনুতে "🗑️ Delete"
                    // আগে থেকেই আছে, আর সেটা ঠিক একই `confirmDeleteDoctor()`
                    // ডাকে — অর্থাৎ অনুমতির নিয়মও (মাস্টার সরাসরি → Trash Bin,
                    // অন্যরা শুধু অনুরোধ) হুবহু আগের মতোই আছে।
                    // ⛔ TK-কে না জানিয়ে হেডারে ডিলিট আর ফেরানো যাবে না।
                    addView(TextView(this@DoctorVisitActivity).apply {
                        // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে নিচের
                        // মোবাইল-লাইনের সাথে মিলে দুইবার দেখাত।
                        text = item.name.ifBlank { "UNKNOWN" }; textSize = 20f
                        setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(android.graphics.Color.WHITE)
                    })
                    // 🔒 TK-FINAL (28.07.2026 ৩.৪৫ pm, প্রুফ ১৭-তে পাশ):
                    // হেডারের গঠন — নাম → নিচে **শুধু মোবাইল** → নিচে
                    // **ডাক্তারের ঠিকানা আগে, তারপর ব্রাঞ্চ**।
                    // আগে ব্রাঞ্চ মোবাইলের পাশে বসত, ঠিকানা আলাদা লাইনে ছিল।
                    // ⛔ TK-কে না জানিয়ে এই ক্রম আর বদলানো যাবে না।
                    addView(TextView(this@DoctorVisitActivity).apply {
                        text = "📞 ${item.mobile}"; textSize = 12.5f
                        setTextColor(android.graphics.Color.parseColor("#DFEEE6"))
                        setPadding(0, dgpx(3), 0, 0)
                    })
                    val placeLine = listOf(
                        if (item.area.isNotBlank()) "📍 ${item.area}" else "",
                        if (item.branch.isNotBlank()) "🏥 ${item.branch}" else ""
                    ).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (placeLine.isNotBlank()) {
                        addView(TextView(this@DoctorVisitActivity).apply {
                            text = placeLine; textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#DFEEE6"))
                            setPadding(0, dgpx(2), 0, 0)
                        })
                    }
                })
                root.addView(headerBar)

                // TK-REQUESTED ADDITION (2026-07-24): button row right below
                // the header (Doctor Detail), matching Patient Timeline's
                // exact pattern/photo-proof -- Referred Patient / Referral
                // Income / Action.
                val btnRow = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(dgpx(6), dgpx(12), dgpx(6), dgpx(4))
                    setBackgroundColor(android.graphics.Color.WHITE)
                }
                // 🔒 TK-FINAL (28.07.2026 ৩.৪৫ pm, প্রুফ ১৭-তে পাশ):
                // **"তিনটে বক্সের সাইজ একই রকম হবে।"**
                // আগে উচ্চতা WRAP_CONTENT ছিল, তাই যে বোতামের লেখা দু'লাইনে
                // ভাঙত সেটা লম্বা হয়ে যেত আর Action ছোট থেকে যেত।
                // এখন: তিনটেরই **একই উচ্চতা (৪৮dp) ও একই চওড়া (weight 1)**,
                // লেখা এক লাইনেই থাকে এবং জায়গা না হলে নিজে থেকে সামান্য ছোট
                // হয় (৯.৫–১২.৫sp) — **বাক্সের মাপ কখনো বদলায় না**।
                // ⛔ TK-কে না জানিয়ে এই মাপ আর বদলানো যাবে না।
                // 🔒🔒 TK-ORDER (30.07.2026 সকাল ৮.৪০, ছবিসহ · খাতার সারি B154 —
                // **তৃতীয়বার বলতে হয়েছে**): *"এই তিনটে বটম একই ক্যাটাগরি বা একই
                // সাইজের হতে হতো। অনেকবার বলেছি তাও করেন নাই।"*
                //
                // TK-এর ছবি মেপে দেখা গেছে: Referred Patient ও Referral Income
                // ১৪০ px উঁচু, Action ১৬৮ px — অর্থাৎ **২৮ px তফাত**।
                //
                // **আসল কারণ:** আগে `MaterialButton` ব্যবহার হত। কোডে তিনটেরই
                // উচ্চতা ৪৮dp চাওয়া ছিল, কিন্তু MaterialButton **নিজের ভিতরে
                // বাড়তি জায়গা (inset) ও নিজের সর্বনিম্ন উচ্চতা** যোগ করে, আর
                // রঙিন গোল বাক্সটা ওই inset-এর ভিতরে আঁকে — তাই **চাওয়া মাপ আর
                // চোখে দেখা বাক্সের মাপ এক থাকে না**, বিশেষ করে লেখা দু'লাইনে
                // ভাঙলে। এতদিন এই কারণেই বারবার ফিরে আসত।
                //
                // **স্থায়ী সমাধান:** MaterialButton-এর ভরসা ছেড়ে দেওয়া হলো।
                // এখন প্রতিটা বাক্স একটা সাধারণ `TextView`, যার background
                // নিজেরাই আঁকা (গোল কোণা + রঙ) — TextView-এর background
                // **পুরো ভিউ জুড়েই** আঁকা হয়, কোনো inset নেই, কোনো লুকানো
                // সর্বনিম্ন উচ্চতা নেই। তাই তিনটে বাক্স **সবসময় হুবহু সমান**:
                // একই উচ্চতা (৫২dp) · একই চওড়া (weight 1) · একই লেখার মাপ।
                // লেখা দু'লাইনে বসানোই আছে (`\n`), তাই কখনো "..." দিয়ে কাটবে না
                // এবং লাইন-সংখ্যা বদলে বাক্সের মাপও বদলাবে না।
                // ⛔ TK-কে না জানিয়ে এই মাপ বা গঠন আর কখনো বদলানো যাবে না।
                // ⛔ পাহারাদারের যাচাই ৯.১৩ এই নিয়মটা আটকে রেখেছে।
                // 🔒🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B208 — TK ফটো-প্রুফ
                // দেখে "পছন্দ হয়েছে" বলেছেন): তিনটে বাটনের ভিতরের লেখা
                // লাইন-সংখ্যার তফাতে (দুটোতে জোর করে দু'লাইন "\n", একটায়
                // এক লাইন) একটু নিচে-উপরে দেখাচ্ছিল, যদিও বাক্সের বাইরের মাপ
                // (৫২dp) তিনটেতেই আগে থেকেই সমান ছিল (B154)। এখন **তিনটেই
                // বাধ্যতামূলক এক লাইনে, ফিক্সড 10sp** (TK নিজে "10 sp" বলে
                // দিয়েছেন) — কখনো দু'লাইনে ভাঙবে না, "..." দিয়েও কাটবে না।
                // ⛔ TK-কে না জানিয়ে এই মাপ/লেখা আর বদলানো যাবে না।
                fun actionButton(label: String, colorHex: String, onClick: () -> Unit) = TextView(this@DoctorVisitActivity).apply {
                    text = label
                    textSize = 10f
                    setSingleLine(true)
                    maxLines = 1
                    ellipsize = null
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(dgpx(4), 0, dgpx(4), 0)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor(colorHex))
                        cornerRadius = dgpx(10).toFloat()
                    }
                    val lp = android.widget.LinearLayout.LayoutParams(0, dgpx(52), 1f)
                    lp.setMargins(dgpx(4), 0, dgpx(4), 0)
                    layoutParams = lp
                    isClickable = true
                    setOnClickListener { onClick() }
                }
                // ── 💰 টাকার লেখা এক জায়গায় (SMS-এ ₹ চিহ্ন সব ফোনে ঠিক দেখায় না,
                //    তাই রোগীর বার্তার মতো এখানেও "Rs " লেখা হয় — একই নিয়ম) ──
                fun rs(v: Double) = "Rs " + "%,.0f".format(v)

                /** 🔒 TK-এর ফাইনাল করা চারটে বার্তা (খাতার সারি B157 · পুরো লেখা
                 *  `00_TK_DOCTOR_BARTA_LOCKED.md` ও `DoctorMessage.kt`-এ)।
                 *
                 *  ⛔ TK-এর নির্দেশ: **কিশনগঞ্জ ছাড়া বাকি সব ব্রাঞ্চের জন্য** —
                 *  কিশনগঞ্জের লেখা তিনি পরে ঠিক করে দেবেন, তাই ততদিন ওখানে
                 *  কিছুই পাঠানো হয় না (আন্দাজে হিন্দি বানানো হয়নি)। */
                /** 🔒 TK-ORDER (30.07.2026 দুপুর ১২.৩৫ · খাতার সারি B159):
                 *  *"হিন্দি এবং বাংলা দুইরকম ভাষাতেই থাকবে, তবে স্টাফ সেখান
                 *  থেকে পছন্দ করে নেবে যে কোন ভাষায় পাঠাতে চাইছে তারা।"*
                 *
                 *  তাই **কিশানগঞ্জে** বার্তা পাঠানোর আগে ভাষা বাছাই ওঠে
                 *  (Hindi / Bengali); বাকি ব্রাঞ্চে আগের মতোই বাংলা যায়
                 *  (সারি B157-এ TK-এর লক করা লেখা অপরিবর্তিত)।
                 *  ⛔ ব্রাঞ্চ ফাঁকা থাকলে কিছুই পাঠানো হয় না — নইলে ভুল
                 *     ব্রাঞ্চের ক্লিনিকের নাম চলে যেতে পারত। */
                // 🔴🔴 TK-ORDER (01.08.2026): "প্রতিটা চেম্বার থেকেই প্রতিটা বার্তা
                // বাংলা/হিন্দি/English — একটা পছন্দ করে নেবে যেই ভাষায় পাঠাতে
                // চাইছে।" আগে শুধু কিশনগঞ্জে hi/bn পপ-আপ উঠত, বাকি ব্রাঞ্চে
                // সরাসরি বাংলা যেত, ইংরেজি অপশনই ছিল না — এখন সব ব্রাঞ্চে,
                // তিনটে ভাষাতেই বাছাই। ⛔ Msg 1 (withIntroLanguage) এই কাজে
                // হাত পড়েনি, ওটা আগে থেকেই সব ব্রাঞ্চে তিন-ভাষা।
                fun withLanguage(onPicked: (String) -> Unit) {
                    if (item.branch.isBlank()) {
                        Toast.makeText(this@DoctorVisitActivity, "This doctor has no branch saved — set the branch first", Toast.LENGTH_LONG).show()
                        return
                    }
                    val langs = arrayOf("বাংলা  (Bengali)", "हिन्दी  (Hindi)", "English")
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Select Language"))
                        // 🔒 খাতার সারি B185 — একই কারণ, একই ওষুধ।
                        .setItems(langs) { langDlg, which ->
                            val lang = when (which) { 0 -> "bn"; 1 -> "hi"; else -> "en" }
                            langDlg.dismiss()
                            afterUi { onPicked(lang) }
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }

                // 🔒 RMP_POST_CALL_WHATSAPP_INTRO_FINAL_LOCK_2026-07-31_1054_IST
                // শুধু Msg 1 (Intro)-এর জন্য — এখন তিন ভাষা (Bengali/Hindi/
                // English), সব ব্রাঞ্চে (আগে শুধু Kishanganj-এ হিন্দি/বাংলা
                // পপ-আপ উঠত, বাকি ব্রাঞ্চে সরাসরি বাংলা যেত)। ⛔ `withLanguage()`
                // একটুও বদলানো হয়নি — Msg 2/3/4 এখনও আগের মতোই (শুধু Kishanganj-এ
                // হিন্দি/বাংলা পপ-আপ, বাকি ব্রাঞ্চে সরাসরি বাংলা)। একই প্রজেক্টের
                // অনুমোদিত `PremiumAlert` পপ-আপ ধাঁচ পুনর্ব্যবহার হলো, নতুন কোনো
                // ডিজাইন তৈরি হয়নি।
                fun withIntroLanguage(onPicked: (String) -> Unit) {
                    if (item.branch.isBlank()) {
                        Toast.makeText(this@DoctorVisitActivity, "This doctor has no branch saved — set the branch first", Toast.LENGTH_LONG).show()
                        return
                    }
                    if (DoctorMessage.introDoctorNameMissing(item.name)) {
                        Toast.makeText(this@DoctorVisitActivity, "Doctor Name Required", Toast.LENGTH_LONG).show()
                        return
                    }
                    val langs = arrayOf("বাংলা  (Bengali)", "हिन्दी  (Hindi)", "English")
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Select Language"))
                        .setItems(langs) { langDlg, which ->
                            val lang = when (which) { 0 -> "bn"; 1 -> "hi"; else -> "en" }
                            langDlg.dismiss()
                            afterUi { onPicked(lang) }
                        }
                        .setNegativeButton("Cancel", null)
                        .show().also { PremiumAlert.paint(it) }
                }

                /** বার্তা ২ · ৩ · ৪ একজন পেশেন্টকে নিয়ে, তাই আগে পেশেন্ট বাছা।
                 *  ⛔ তালিকাটা ঠিক সেটাই যেটা "Referred Patient"-এ দেখা যায় —
                 *     আলাদা কোনো খোঁজ বা আন্দাজ নেই। */
                fun pickReferredPatient(onPicked: (ReferredPatient) -> Unit) {
                    if (data.referred.isEmpty()) {
                        Toast.makeText(this@DoctorVisitActivity, "No patient is linked to this doctor yet", Toast.LENGTH_LONG).show()
                        return
                    }
                    // 🔒🔒 খাতার সারি B186 (TK, 30.07.2026 বিকেল ৪.৩০ — **দ্বিতীয়বার
                    //    বলতে হয়েছে**): *"মেসেজ ১ কাজ করছে, বাকি ২ থেকে ৪ কাজ
                    //    করছে না।"* সারি B185-এ পপ-আপ খোলার **ক্রম** ঠিক করা
                    //    হয়েছিল (আগে বন্ধ, তারপর পরেরটা) — তাতেও TK-এর ফোনে
                    //    Select Patient-এ নামের উপর চাপ দিলে কিছু হচ্ছিল না।
                    //
                    //    **এবার ভরসাটাই সরিয়ে দেওয়া হলো:** নামের তালিকাটা আর
                    //    `AlertDialog.setItems(...)`-এর ListView নয় — প্রতিটা নাম
                    //    এখন **নিজে একটা ট্যাপযোগ্য সারি** (সাধারণ TextView +
                    //    `setOnClickListener`), প্রজেক্টের নিজের অনুমোদিত পপ-আপ
                    //    খোলসেই (`premiumDialogShell` — সবুজ হেডার · সাদা কার্ড ·
                    //    নিচে পিল বোতাম, ঠিক Msg 3-এর ফর্ম ও Send Message পপ-আপের
                    //    মতো)। TextView-এর ক্লিক কখনো তালিকা-ঘরের মাপ/ফোকাসের
                    //    উপর নির্ভর করে না, তাই এটা আর নীরবে ব্যর্থ হতে পারে না।
                    // ⛔ বার্তার লেখা · ভাষা · কোন রোগী দেখানো হবে সেই তালিকা ·
                    //    পরের ধাপগুলো — কিছুই বদলায়নি।
                    // 🔴🔴 খাতার সারি B187 (TK, 30.07.2026 বিকেল ৪.৪০ — **তৃতীয়বার
                    //    বলতে হয়েছে**): *"ডক্টর ভিজিটে মেসেজ ১ কাজ করছে, বাকি ২
                    //    থেকে ৪ কাজ করছে না।"*
                    //    **আসল কারণ এবার কোড ধরে পাওয়া গেছে:** এই লুপটা এমন একটা
                    //    নামের তালিকা (`list`) ধরে ঘুরত **যেটা এই ফাইলে কোথাও
                    //    তৈরিই হয়নি** — উপরের `if (data.referred.isEmpty())`
                    //    পাহারাটা ঠিক তালিকা দেখত, কিন্তু নিচের লুপটা ভুল নাম।
                    //    ফল: Select Patient খুলত, কিন্তু ভিতরে একটাও ট্যাপযোগ্য
                    //    সারি বসত না — তাই নামের উপর চাপ দিলে কিছুই হত না।
                    //    **ওষুধ:** লুপটাও এখন ঠিক সেই একই তালিকা (`data.referred`)
                    //    ধরেই ঘোরে — যেটা "Referred Patient"-এ দেখা যায়।
                    // ⛔ বার্তার লেখা · ভাষা · কোন রোগী দেখানো হবে · পরের ধাপ —
                    //    কিছুই বদলানো হয়নি, শুধু ভুল নামটা ঠিক হলো।
                    val parts = premiumDialogShell("📋", "Select Patient")
                    for (p in data.referred) {
                        val rowTv = TextView(this@DoctorVisitActivity).apply {
                            text = (p.name.ifBlank { p.mobile }).uppercase() + "  ·  " + FollowUpModel.displayDate(p.rawDate)
                            textSize = 14f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor("#10223A"))
                            setPadding(dgpx(14), dgpx(14), dgpx(14), dgpx(14))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(android.graphics.Color.parseColor("#F4F8FC"))
                                cornerRadius = 12f * dg
                                setStroke(dgpx(1), android.graphics.Color.parseColor("#D8E1EC"))
                            }
                            val lp = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.bottomMargin = dgpx(8)
                            layoutParams = lp
                            setOnClickListener {
                                parts.dialog.dismiss()
                                afterUi { onPicked(p) }
                            }
                        }
                        parts.body.addView(rowTv)
                    }
                    parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
                        setOnClickListener { parts.dialog.dismiss() }
                    })
                    parts.dialog.show()
                }

                /** বার্তা ৩-এর তিনটে ঘর স্টাফ নিজে বেছে দেন — অ্যাপ আন্দাজে
                 *  কিছু বসায় না (TK-এর নিয়ম)। */
                fun openDetailsMessageForm(p: ReferredPatient, lang: String = "bn") {
                    val parts = premiumDialogShell("📤", "Patient Details — ${(p.name.ifBlank { p.mobile }).uppercase()}")
                    parts.body.addView(fieldLabel("💉", "Treatment", 0))
                    val spTreat = Spinner(this@DoctorVisitActivity).apply {
                        adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Done", "Not done"))
                        setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                    }
                    parts.body.addView(spTreat)
                    parts.body.addView(fieldLabel("🧪", "Blood Test"))
                    val spBlood = Spinner(this@DoctorVisitActivity).apply {
                        adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Given", "Not given"))
                        setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
                    }
                    parts.body.addView(spBlood)
                    parts.body.addView(fieldLabel("⏰", "Next Visit Date"))
                    var nextDate = ""
                    val btnDate = pillButton("Not given — tap to pick date", "#E5E8EC", android.graphics.Color.parseColor("#145A32"))
                    btnDate.setOnClickListener {
                        val c = Calendar.getInstance()
                        DatePickerDialog(this@DoctorVisitActivity, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                            // ⛔ Locale.US — নইলে ফোনের ভাষা বাংলা হলে সংখ্যা
                            //    বাংলায় বসে যেত (খাতার সারি B93-এর গ্লোবাল রুল)।
                            nextDate = String.format(Locale.US, "%02d.%02d.%04d", d, m + 1, y)
                            btnDate.text = nextDate
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }
                    parts.body.addView(btnDate)

                    parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
                        setOnClickListener { parts.dialog.dismiss() }
                    })
                    parts.actionRow.addView(pillButton("📤 Send", "#0C9E33").apply {
                        setOnClickListener {
                            val nextTxt = if (nextDate.isBlank()) "" else nextDate
                            val text = DoctorMessage.details(
                                item.branch, item.name, item.area,
                                p.name, p.patientId, FollowUpModel.displayDate(p.rawDate),
                                spTreat.selectedItemPosition == 0, spBlood.selectedItemPosition == 0, nextTxt, lang
                            )
                            // 🔒 খাতার সারি B185 — এই পর্দাটাও আগে পুরো বন্ধ, তারপর
                            //    বার্তা পাঠানোর পর্দা (একই কারণ)।
                            parts.dialog.dismiss()
                            afterUi { sendDoctorMessage(item.mobile, item.name, text, logKind = "DOCTOR_DETAILS") }
                        }
                    })
                    parts.dialog.show()
                }

                /** 🔗 পুরনো (মোবাইলহীন) রেফারেল ইনকামের সারিটা আসল রোগীর সঙ্গে
                 *  জুড়ে দেওয়ার বাক্স — খাতার সারি B155।
                 *  ⛔ আন্দাজে কিছুই হয় না: মোবাইলটা `patients` টেবিলে সত্যিই আছে
                 *     কিনা আগে দেখা হয়, না থাকলে কিছুই বদলায় না। */
                fun openAttachPatient(r: RefIncomeLine) {
                    val parts = premiumDialogShell("🔗", "Attach Patient — ${r.patient}")
                    parts.body.addView(fieldLabel("📱", "Patient Mobile *", 0))
                    val mobileInput = EditText(this@DoctorVisitActivity).apply {
                        hint = "10-digit mobile"
                        inputType = android.text.InputType.TYPE_CLASS_PHONE
                    }
                    styleInput(mobileInput)
                    parts.body.addView(mobileInput)
                    parts.body.addView(fieldLabel("🧑", "Patient Name"))
                    val nameField = EditText(this@DoctorVisitActivity).apply {
                        setText("Enter mobile number above")
                        isFocusable = false; isClickable = false; isCursorVisible = false
                    }
                    styleInput(nameField)
                    parts.body.addView(nameField)
                    var token = 0
                    mobileInput.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            val digits = s?.toString()?.filter { it.isDigit() }?.takeLast(10) ?: ""
                            if (digits.length != 10) {
                                nameField.setText("Enter mobile number above")
                                nameField.setTextColor(android.graphics.Color.parseColor("#7A8699"))
                                return
                            }
                            val my = ++token
                            nameField.setText("Checking…")
                            nameField.setTextColor(android.graphics.Color.parseColor("#7A8699"))
                            lifecycleScope.launch {
                                val ref = findVisitPatient(digits)   // 🔵 V530
                                if (my != token) return@launch
                                if (ref == null) {
                                    nameField.setText("✗ No patient found with this mobile")
                                    nameField.setTextColor(android.graphics.Color.parseColor("#E5484D"))
                                } else {
                                    nameField.setText("✓ ${ref.name.ifBlank { "Unnamed patient" }}")
                                    nameField.setTextColor(android.graphics.Color.parseColor("#0C9E33"))
                                }
                            }
                        }
                    })
                    parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
                        setOnClickListener { parts.dialog.dismiss() }
                    })
                    parts.actionRow.addView(pillButton("🔗 Attach", "#0C9E33").apply {
                        setOnClickListener {
                            val digits = mobileInput.text.toString().filter { it.isDigit() }.takeLast(10)
                            if (digits.length != 10) {
                                Toast.makeText(this@DoctorVisitActivity, "Valid 10-digit mobile required", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            lifecycleScope.launch {
                                val ref = findVisitPatient(digits)   // 🔵 V530
                                if (ref == null) {
                                    Toast.makeText(this@DoctorVisitActivity, "No patient found with this mobile — check the number", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                val ok = withContext(Dispatchers.IO) {
                                    DoctorVisitRepository().attachPatientToReferralEntry(
                                        item.id, r.id, r.rawDate, r.amount, r.patient,
                                        ref.name, digits, applicationContext
                                    )
                                }
                                if (ok) {
                                    withContext(Dispatchers.IO) {
                                        DoctorVisitRepository().linkReferringDoctorIfBlank(ref.id, item.name, item.mobile, applicationContext)
                                    }
                                }
                                Toast.makeText(
                                    this@DoctorVisitActivity,
                                    if (ok) "Patient attached — ${ref.name}" else "Failed — check connection",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) {
                                    parts.dialog.dismiss()
                                    fsDialog.dismiss()
                                    showDoctorViewAll(item)
                                }
                            }
                        }
                    })
                    parts.dialog.show()
                }

                /** 👥 TK-ORDER (30.07.2026 সকাল ৮.৪৫ · খাতার সারি B156):
                 *  *"কোন ডাক্তার কোন পেসেন্ট পাঠিয়েছে সেটা যেন বোঝা যায়... ক্লিক
                 *  করলে ওই পেশেন্টের প্রোফাইল যেন ওপেন হয়।"*
                 *
                 *  আগে এই বোতামে শুধু একটা টোস্ট উঠত ("০ জন পেশেন্ট রেফার
                 *  করেছেন") — কোনো তালিকা খুলত না, তাই মনে হত রোগী হারিয়ে গেছে।
                 *  ⛔ নতুন কোনো ক্লাউড-কল নেই: যে তথ্য View All আগেই নামিয়ে
                 *     রেখেছে, ঠিক সেটাই দেখানো হয়। */
                fun openReferredList() {
                    val listRoot = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setBackgroundColor(android.graphics.Color.parseColor("#EEF2F5"))
                    }
                    lateinit var listDialog: AlertDialog
                    val head = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dgpx(18), dgpx(20), dgpx(18), dgpx(18))
                        background = android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                            intArrayOf(android.graphics.Color.parseColor("#12386B"), android.graphics.Color.parseColor("#1F8A5B"))
                        )
                    }
                    head.addView(TextView(this@DoctorVisitActivity).apply {
                        text = "←"; textSize = 22f; setTextColor(android.graphics.Color.WHITE)
                        setPadding(0, 0, dgpx(14), 0)
                        setOnClickListener { listDialog.dismiss() }
                    })
                    head.addView(android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        addView(TextView(this@DoctorVisitActivity).apply {
                            text = "👥  REFERRED PATIENTS (${data.referred.size})"
                            textSize = 18f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.WHITE)
                        })
                        addView(TextView(this@DoctorVisitActivity).apply {
                            text = "Dr. ${item.name.ifBlank { item.mobile }}"
                            textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#DFEEE6"))
                            setPadding(0, dgpx(3), 0, 0)
                        })
                    })
                    listRoot.addView(head)

                    val lScroll = android.widget.ScrollView(this@DoctorVisitActivity).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                    }
                    val lBody = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(dgpx(12), dgpx(12), dgpx(12), dgpx(12))
                    }
                    lScroll.addView(lBody)
                    listRoot.addView(lScroll)

                    fun sectionTitle(txt: String, colorHex: String) {
                        lBody.addView(TextView(this@DoctorVisitActivity).apply {
                            text = txt; textSize = 12.5f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor(colorHex))
                            setPadding(dgpx(6), dgpx(14), 0, dgpx(6))
                        })
                    }
                    fun infoBox(txt: String) {
                        lBody.addView(TextView(this@DoctorVisitActivity).apply {
                            text = txt; textSize = 13f
                            setTextColor(android.graphics.Color.parseColor("#6B7686"))
                            setPadding(dgpx(14), dgpx(16), dgpx(14), dgpx(16))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(android.graphics.Color.parseColor("#F7FAFC"))
                                cornerRadius = dgpx(12).toFloat()
                                setStroke(dgpx(1), android.graphics.Color.parseColor("#E2E8F0"))
                            }
                        })
                    }

                    sectionTitle("PATIENTS SENT BY THIS DOCTOR (${data.referred.size})", "#145A32")
                    if (data.referred.isEmpty()) {
                        infoBox("No patient record is linked to this doctor yet.")
                    } else {
                        data.referred.forEachIndexed { idx, p ->
                            val due = p.bill - p.paid
                            val card = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setPadding(dgpx(14), dgpx(12), dgpx(14), dgpx(12))
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.WHITE)
                                    cornerRadius = dgpx(12).toFloat()
                                    setStroke(dgpx(1), android.graphics.Color.parseColor("#D8E1EC"))
                                }
                                val lp = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                lp.bottomMargin = dgpx(10)
                                layoutParams = lp
                                isClickable = true
                            }
                            fun copyOnLongPress(view: TextView, value: String, label: String) {
                                view.setOnLongClickListener {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                                    Toast.makeText(this@DoctorVisitActivity, "$label copied", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }
                            val topRow = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                            }
                            val nameText = TextView(this@DoctorVisitActivity).apply {
                                text = "${idx + 1}.  ${p.name.ifBlank { "UNKNOWN" }}"
                                textSize = 15f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor("#10223A"))
                                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                            val mobileText = TextView(this@DoctorVisitActivity).apply {
                                text = p.mobile; textSize = 12.5f; maxLines = 1
                                setTextColor(android.graphics.Color.parseColor("#5A6474"))
                                gravity = android.view.Gravity.END
                            }
                            copyOnLongPress(nameText, p.name.ifBlank { "UNKNOWN" }, "Name")
                            copyOnLongPress(mobileText, p.mobile, "Mobile number")
                            nameText.setOnClickListener { card.performClick() }
                            mobileText.setOnClickListener { card.performClick() }
                            topRow.addView(nameText); topRow.addView(mobileText); card.addView(topRow)

                            val detailRow = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                                setPadding(0, dgpx(6), 0, dgpx(8))
                            }
                            detailRow.addView(TextView(this@DoctorVisitActivity).apply {
                                text = p.disease.ifBlank { "—" }.uppercase(Locale.getDefault())
                                textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                                setTextColor(android.graphics.Color.parseColor("#5A6474"))
                                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            detailRow.addView(TextView(this@DoctorVisitActivity).apply {
                                text = "Sent: ${FollowUpModel.displayDate(p.rawDate)}"
                                textSize = 12f; maxLines = 1; gravity = android.view.Gravity.END
                                setTextColor(android.graphics.Color.parseColor("#5A6474"))
                            })
                            card.addView(detailRow)

                            val moneyRow = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                            }
                            fun moneyChip(label: String, value: Double, bg: String, fg: String): TextView = TextView(this@DoctorVisitActivity).apply {
                                text = "$label ${rs(value)}"; textSize = 11.5f; gravity = android.view.Gravity.CENTER; maxLines = 1
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor(fg))
                                setPadding(dgpx(4), dgpx(7), dgpx(4), dgpx(7))
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.parseColor(bg)); cornerRadius = dgpx(8).toFloat()
                                    setStroke(dgpx(1), android.graphics.Color.parseColor("#D8E1EC"))
                                }
                                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    marginEnd = if (label == "Due") 0 else dgpx(6)
                                }
                            }
                            moneyRow.addView(moneyChip("Bill", p.bill, "#F3F7FB", "#29445F"))
                            moneyRow.addView(moneyChip("Paid", p.paid, "#EAF8EF", "#0C7A36"))
                            moneyRow.addView(moneyChip("Due", if (due > 0) due else 0.0, "#FDEEEE", "#B42318"))
                            card.addView(moneyRow)
                            // ⛔ ঠিক সেই একই পথ যেটা টেবিলের সারিতে চাপলে হয় —
                            //    নতুন কোনো নিয়ম বানানো হয়নি (খাতার সারি B156)।
                            // 🔴 TK-REPORTED (04.08.2026, "বাড়ির গেট/বারান্দা/
                            // বেডরুম" উদাহরণ): এখানে গভীরে ঢোকার আগে দুটো
                            // পপ-আপই (listDialog · fsDialog) বন্ধ করে দেওয়া
                            // হত, তাই Patient Timeline থেকে Back করলে সরাসরি
                            // RMP মূল পাতায় চলে যেত — মাঝের পপ-আপ দুটো আর
                            // দেখা যেত না। ⛔ সমাধান: পপ-আপ বন্ধ না করে খোলা
                            // রাখা — Timeline থেকে Back করলে এখন ঠিক এই
                            // Referred Patient তালিকাতেই ফিরে আসবে, তার থেকে
                            // Back করলে Performance Report-এ, তার থেকে Back
                            // করলে RMP মূল পাতায় — ধাপে ধাপে। কোনো
                            // save/hisab/logic ছোঁয়া হয়নি, শুধু এই দুটো
                            // dismiss() সরানো হয়েছে।
                            card.setOnClickListener {
                                if (p.mobile.length == 10) {
                                    startActivity(
                                        Intent(this@DoctorVisitActivity, PatientTimelineActivity::class.java)
                                            .putExtra("mobile", p.mobile)
                                    )
                                } else {
                                    Toast.makeText(this@DoctorVisitActivity, "This patient record has no mobile number saved", Toast.LENGTH_SHORT).show()
                                }
                            }
                            lBody.addView(card)
                        }
                    }

                    // 🔴 খাতার সারি B155: যে রেফারেল ইনকামের পিছনে কোনো রোগীর
                    // রেকর্ড নেই সেগুলো **লুকানো হয় না** — স্পষ্ট দেখানো হয়,
                    // আর চাপলে আসল রোগীকে জুড়ে দেওয়া যায়।
                    val unlinked = data.refIncome.filter { it.mobile.filter { c -> c.isDigit() }.takeLast(10).length != 10 }
                    if (unlinked.isNotEmpty()) {
                        sectionTitle("REFERRAL INCOME WITH NO PATIENT ATTACHED (${unlinked.size})", "#B42318")
                        unlinked.forEach { r ->
                            val card = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setPadding(dgpx(14), dgpx(12), dgpx(14), dgpx(12))
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.parseColor("#FFF7F7"))
                                    cornerRadius = dgpx(12).toFloat()
                                    setStroke(dgpx(1), android.graphics.Color.parseColor("#E5A0A0"))
                                }
                                val lp = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                lp.bottomMargin = dgpx(10)
                                layoutParams = lp
                                isClickable = true
                            }
                            card.addView(TextView(this@DoctorVisitActivity).apply {
                                text = r.patient
                                textSize = 15.5f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor("#10223A"))
                            })
                            card.addView(TextView(this@DoctorVisitActivity).apply {
                                text = "${FollowUpModel.displayDate(r.date)}     ${rs(r.amount)}     ${if (r.status.equals("Paid", true)) "Paid" else "Due"}"
                                textSize = 12.5f
                                setTextColor(android.graphics.Color.parseColor("#5A6474"))
                                setPadding(0, dgpx(4), 0, dgpx(8))
                            })
                            card.addView(TextView(this@DoctorVisitActivity).apply {
                                text = "⚠ NO PATIENT ATTACHED  ·  TAP TO ATTACH"
                                textSize = 11.5f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor("#B42318"))
                            })
                            card.setOnClickListener { listDialog.dismiss(); openAttachPatient(r) }
                            lBody.addView(card)
                        }
                    }

                    listDialog = AlertDialog.Builder(this@DoctorVisitActivity).setView(listRoot).create()
                    listDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    listDialog.show()
                    forceDialogFullScreen(listDialog, listRoot)
                }

                // 🔒 TK-ORDER (30.07.2026 · খাতার সারি B156): এই বোতামে এখন
                // **আসল তালিকা** খোলে (আগে শুধু একটা টোস্ট উঠত)।
                // 🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B208): আইকন বাদ,
                // "Referred Patient" শুধু লেখা, এক লাইনে।
                btnRow.addView(actionButton("Referred Patient", "#16A36D") {
                    openReferredList()
                })
                // TK-REQUESTED (2026-07-24): "Referral Income" now opens the
                // existing Add Referral Income form directly (same function
                // as the old floating button below, just moved up here).
                // 🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B208): আইকন বাদ,
                // "Referral Income" শুধু লেখা, এক লাইনে।
                btnRow.addView(actionButton("Referral Income", "#C98A1E") {
                    // V381 navigation safety: keep Doctor/RMP Details underneath.
                    // Closing the child Referral Income flow now returns here instead of
                    // silently jumping back to Performance/main RMP list.
                    showAddReferralIncome(item)
                })
                // TK-REQUESTED (2026-07-24): "Action" consolidates what used
                // to be separate buttons/icons (Call/WhatsApp/Log Call/Edit/
                // Delete) into one menu, same reasoning as Patient
                // Timeline's own Action button -- every one of these reuses
                // its EXACT existing, already-working function, nothing
                // about what each does has changed.
                // 🔒 TK-ORDER (30.07.2026 সকাল ৮.৫০ ও ১০.২৫ · খাতার সারি B156 · B157):
                // *"ফলোআপ কার্ডে যেমন Action বোতামের মধ্যে ছিল, ঠিক এখানেও
                // সেরকম জায়গায় রাখবেন"* — তাই বার্তাগুলো আলাদা বোতাম নয়,
                // এই মেনুরই ভিতরে। TK: *"অ্যাকশন বটম চাপার পরে আগে যা যা ছিল
                // সেগুলো তো থাকবে, তার সাথে বার্তা যোগ হবে।"*
                // ⛔ আগের পাঁচটা অপশন (Call · WhatsApp · Log Call · Edit ·
                //    Delete) হুবহু আছে, একটাও সরানো হয়নি।
                // ⛔ চারটে বার্তার লেখা TK নিজে ফাইনাল করেছেন — `DoctorMessage.kt`,
                //    তাঁকে না জানিয়ে একটা শব্দও বদলানো যাবে না।
                btnRow.addView(actionButton("⚡ Action", "#12233F") {
                    // 🔒 TK-ORDER (30.07.2026 রাত, খাতার সারি B205 — TK: "স্টাফ তো
                    // ভুল করে কিছু লিখতেই পারে, তার ভুল সংশোধনের রাস্তা তো করতে
                    // হবে"): নতুন অপশন "🩹 Fix Last Note" — এটা B123 (29.07.2026)-এর
                    // "Log Call"/"Add Remark" নিয়মকে ছোঁয় না (ওটা এখনো নতুন কল
                    // হিসেবেই গোনা হবে, TK-এর লক করা সিদ্ধান্ত অক্ষত)। এটা সম্পূর্ণ
                    // আলাদা, নতুন পথ — শুধু ভুল-লেখা সংশোধনের জন্য।
                    val options = arrayOf(
                        "📞 Call", "💬 WhatsApp", "📝 Log Call",
                        "🩹 Fix Last Note",
                        "📩 Msg 1 · Intro & Request",
                        "📩 Msg 2 · Patient Arrived",
                        "📩 Msg 3 · Patient Details",
                        "📩 Msg 4 · Referral Income Sent",
                        "✏️ Edit", "🗑️ Delete"
                    )
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, item.name.ifBlank { item.mobile }))
                        .setItems(options) { menuDlg, which ->
                            when (which) {
                                0 -> CallChooser.open(this@DoctorVisitActivity, item.mobile)
                                1 -> openWhatsApp(item.mobile)
                                2 -> { fsDialog.dismiss(); showLogCallDialog(item) }
                                3 -> { menuDlg.dismiss(); editLastCallNote(item) }
                                4 -> withIntroLanguage { lang ->
                                    sendDoctorMessage(item.mobile, item.name, DoctorMessage.intro(item.branch, item.name, item.area, lang), logKind = "DOCTOR_INTRO")
                                }
                                // 🔒 খাতার সারি B185 (TK, 30.07.2026 বিকেল ৫.০০): বার্তা
                                //    ২·৩·৪-এ পপ-আপ তিন ধাপে খোলে, তাই মেনুটা **আগে
                                //    বন্ধ** করে তারপর পরের ধাপ। ⛔ বার্তার লেখা, ভাষা
                                //    বাছাই, রোগী বাছাই — কোনো নিয়ম বদলায়নি।
                                //    ⛔ বাকি অপশনগুলো (Call · WhatsApp · Log Call ·
                                //    Msg 1 · Edit · Delete) হুবহু আগের মতোই রাখা হয়েছে।
                                // 🔴🔴 TK-ORDER (01.08.2026): এখন বাছাই করা ভাষাটা (lang)
                                // সত্যিই DoctorMessage-এ পাঠানো হয় — আগে `_` দিয়ে ফেলে
                                // দেওয়া হতো, তাই ভাষা যাই বাছুন বার্তা সবসময় বাংলাই যেত।
                                5 -> { menuDlg.dismiss(); afterUi { withLanguage { lang ->
                                    pickReferredPatient { p ->
                                        sendDoctorMessage(
                                            item.mobile, item.name,
                                            DoctorMessage.arrived(
                                                item.branch, item.name, item.area,
                                                p.name, p.patientId, FollowUpModel.displayDate(p.rawDate), lang
                                            ),
                                            logKind = "DOCTOR_ARRIVED"
                                        )
                                    }
                                } } }
                                6 -> { menuDlg.dismiss(); afterUi { withLanguage { lang ->
                                    pickReferredPatient { p -> openDetailsMessageForm(p, lang) }
                                } } }
                                7 -> { menuDlg.dismiss(); afterUi { withLanguage { lang ->
                                    pickReferredPatient { p ->
                                        // 🔒 STRICT_MESSAGE_ONLY_UPDATE (31.07.2026): Amount/Payment Date
                                        // এখন সত্যিকারের Saved referralPayments রেকর্ড থেকে (সবচেয়ে
                                        // সাম্প্রতিক "Paid" এন্ট্রি এই রোগীর মোবাইল দিয়ে মিলিয়ে) — আন্দাজ নয়।
                                        val match = data.refIncome
                                            .filter { it.mobile == p.mobile && it.status.equals("Paid", true) }
                                            .maxByOrNull { it.rawDate }
                                        sendDoctorMessage(
                                            item.mobile, item.name,
                                            DoctorMessage.referralPaid(
                                                item.branch, item.name, item.area,
                                                p.name, p.patientId, FollowUpModel.displayDate(p.rawDate),
                                                match?.amount ?: 0.0, match?.date ?: "",
                                                match?.mode ?: "", match?.referenceNo ?: "", lang
                                            ),
                                            logKind = "DOCTOR_REFERRAL_PAID"
                                        )
                                    }
                                } } }
                                8 -> { fsDialog.dismiss(); showDoctorEdit(item) }
                                9 -> { fsDialog.dismiss(); confirmDeleteDoctor(item) }
                            }
                        }
                        .show().also { PremiumAlert.paint(it) }
                })
                root.addView(btnRow)

                val scroll = android.widget.ScrollView(this@DoctorVisitActivity).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                }
                val scrollBody = android.widget.LinearLayout(this@DoctorVisitActivity).apply { orientation = android.widget.LinearLayout.VERTICAL }
                scroll.addView(scrollBody)
                root.addView(scroll)

                // TK-REQUESTED REDESIGN (2026-07-24): summary boxes (Referred
                // count / Ref. Paid / Ref. Due) then ONE chronological table
                // (Date/Time | Type/By | Note) merging Call/Referred
                // Patient/Referral Income entries by date -- replaces the
                // old three separate card sections. Same exact table
                // pattern as Patient Timeline (straight-aligned Date/Time
                // and Type/By columns via width-measurement, Note bold+wide
                // +14sp) -- TK-approved photo-proof, TK-locked design.
                // ⚡ সংখ্যা ও তালিকা এখানেই তৈরি হয়। প্রথমবার ফাঁকা মান দিয়ে চলে,
                // পিছনের কাজ শেষ হলে ঠিক এই ফাংশনটাই আবার ডাকা হয় — তাই ভিতরের
                // এক লাইন কোডও বদলাতে হয়নি, চেহারাও হুবহু আগের মতোই থাকে।
                fun renderBody() {
                    scrollBody.removeAllViews()
                    val summaryRow2 = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        setPadding(dgpx(14), dgpx(12), dgpx(14), dgpx(8))
                    }
                    fun sumBox(label: String, value: String, colorHex: String, backgroundHex: String = "#FFFFFF"): android.widget.LinearLayout {
                        val box = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER
                            setPadding(dgpx(8), dgpx(10), dgpx(8), dgpx(10))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(android.graphics.Color.parseColor(backgroundHex)); cornerRadius = dgpx(10).toFloat()
                                setStroke(dgpx(1), android.graphics.Color.parseColor("#E2E8F0"))
                            }
                            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            lp.marginEnd = dgpx(6)
                            layoutParams = lp
                        }
                        box.addView(TextView(this@DoctorVisitActivity).apply { text = label; textSize = 10.5f; gravity = android.view.Gravity.CENTER; setTextColor(android.graphics.Color.parseColor("#94A3B8")) })
                        box.addView(TextView(this@DoctorVisitActivity).apply { text = value; textSize = 15f; gravity = android.view.Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(android.graphics.Color.parseColor(colorHex)) })
                        return box
                    }
                    summaryRow2.addView(sumBox("Referred", data.referred.size.toString(), "#10223A"))
                    lateinit var refPaidBox: android.widget.LinearLayout
                    lateinit var refDueBox: android.widget.LinearLayout
                    refPaidBox = sumBox("Ref. Paid", "\u20B9${"%,.0f".format(data.refPaid)}", "#0C8F3A", "#EAF8EF").apply {
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            showRmpDirectPayment(item) {
                                lifecycleScope.launch {
                                    val (fresh, advances) = withContext(Dispatchers.IO) {
                                        RmpCommissionRepository.rmpSummary(item.id) to RmpCommissionRepository.advancePayments(item.id)
                                    }
                                    val s = fresh.value ?: return@launch
                                    val covered = if (advances.ok) (advances.value ?: emptyList()).sumOf { it.legacyCovered } else 0.0
                                    val legacyPaid = data.refIncome.filter { it.status.equals("Paid", true) }.sumOf { it.amount }
                                    val shownPaid = kotlin.math.max(0.0, legacyPaid + s.paid - covered)
                                    (refPaidBox.getChildAt(1) as? TextView)?.text = "₹${"%,.0f".format(shownPaid)}"
                                    (refDueBox.getChildAt(1) as? TextView)?.text = "₹${"%,.0f".format(s.due)}"
                                    // Keep the repeat-open memory snapshot aligned with the
                                    // just-verified figures; no cloud write/request is added.
                                    data = data.copy(refPaid = shownPaid, refDue = s.due)
                                    saveViewAllCache(data)
                                }
                            }
                        }
                    }
                    refDueBox = sumBox("Ref. Due", "\u20B9${"%,.0f".format(data.refDue)}", "#B42318", "#FDEEEE").apply {
                        (layoutParams as android.widget.LinearLayout.LayoutParams).marginEnd = 0
                        isClickable = true; isFocusable = true
                        setOnClickListener { showRmpCommissionSummary(item) }
                    }
                    summaryRow2.addView(refPaidBox); summaryRow2.addView(refDueBox)
                    scrollBody.addView(summaryRow2)
                    lifecycleScope.launch {
                        val verified = withContext(Dispatchers.IO) { RmpCommissionRepository.rmpSummary(item.id) }
                        verified.value?.let { s ->
                            (refDueBox.getChildAt(1) as? TextView)?.text = "₹${"%,.0f".format(s.due)}"
                            // The final verified due is what the next repeat-open should
                            // show immediately. Memory only; Supabase is untouched.
                            data = data.copy(refDue = s.due)
                            saveViewAllCache(data)
                        }
                    }

                    // Unified, chronologically-sorted entry list.
                    // 🟢 B628: onEdit — শুধু Referral Income সারিতে থাকে; তিনবার চাপলে এডিট খোলে।
                    data class UnifiedRow(val rawDate: String, val dateText: String, val typeText: String, val typeColorHex: String, val byText: String, val noteText: String, val onTap: (() -> Unit)?, val highlightName: String? = null, val onEdit: (() -> Unit)? = null)
                    val unified = mutableListOf<UnifiedRow>()
                    data.calls.forEach { c ->
                        unified.add(UnifiedRow(c.rawDate, c.date, "Call", "#7A3FF2", c.by, c.note, null))
                    }
                    data.referred.forEach { p ->
                        // 🔴 TK-REPORTED (04.08.2026, একই ক্লাসের বাগ): আগে
                        // fsDialog.dismiss() করে দেওয়া হত, তাই Timeline থেকে
                        // Back করলে সরাসরি RMP মূল পাতায় চলে যেত। এখন পপ-আপ
                        // খোলা রাখা হয় — Back করলে এই View All তালিকাতেই ফেরে।
                        val onTap: (() -> Unit)? = if (p.mobile.length == 10) { { startActivity(android.content.Intent(this@DoctorVisitActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        unified.add(UnifiedRow(p.rawDate, FollowUpModel.displayDate(p.rawDate), "Referred Patient", "#16A36D", "", "${p.name.ifBlank { p.mobile }} \u2014 Bill \u20B9${"%,.0f".format(p.bill)} \u00b7 Paid \u20B9${"%,.0f".format(p.paid)}", onTap))
                    }
                    data.refIncome.forEach { r ->
                        // 🔒 14.08.2026, TK live-test correction: Referral Income
                        // is an RMP money record, not a route to the patient's
                        // Report Card. A single tap must therefore do nothing;
                        // three quick taps open only the approved Edit/Delete
                        // dialog below. Referred Patient rows above keep their
                        // existing Patient Timeline action unchanged.
                        unified.add(UnifiedRow(r.rawDate, FollowUpModel.displayDate(r.date), "Referral Income", "#C98A1E", "", "${r.patient} \u2014 \u20B9${"%,.0f".format(r.amount)} \u00b7 ${if (r.status.equals("Paid", true)) "Paid" else "Due"}", null, highlightName = null, onEdit = { openReferralEdit(item, r.id, r.amount, r.status, r.date, r.patient, r.mobile) }))
                    }
                    unified.sortByDescending { it.rawDate }

                    scrollBody.addView(TextView(this@DoctorVisitActivity).apply {
                        text = "\uD83D\uDCCB Total Entries: ${unified.size}"; textSize = 12.5f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#0F5C42"))
                        setBackgroundColor(android.graphics.Color.parseColor("#E6F4EE"))
                        setPadding(dgpx(16), dgpx(8), dgpx(16), dgpx(8))
                    })

                    if (unified.isEmpty()) {
                        scrollBody.addView(TextView(this@DoctorVisitActivity).apply {
                            text = NoBengali.s("কোনো কল/রেফারেল/আয় এখনো লগ হয়নি"); textSize = 12.5f
                            setTextColor(android.graphics.Color.parseColor("#8A97A8"))
                            setPadding(dgpx(16), dgpx(20), dgpx(16), dgpx(20))
                        })
                    } else {
                        // TK-REQUESTED FIX (2026-07-24, same fix as Patient
                        // Timeline's table): Date/Time and Type/By columns use a
                        // width MEASURED from the widest content across every
                        // row (header included), applied identically to every
                        // row, so the vertical grid lines stay straight instead
                        // of drifting when one row's text happens to be wider.
                        val cellPadPx = dgpx(8) * 2
                        val boldTf = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        val measureMain = android.graphics.Paint().apply { typeface = boldTf; textSize = 9.3f * resources.displayMetrics.scaledDensity }
                        val measureSub = android.graphics.Paint().apply { typeface = boldTf; textSize = 8.5f * resources.displayMetrics.scaledDensity }
                        val measureHead = android.graphics.Paint().apply { typeface = boldTf; textSize = 9.5f * resources.displayMetrics.scaledDensity }
                        var dateTimeColPx = measureHead.measureText("Date/Time")
                        var typeByColPx = measureHead.measureText("Type / By")
                        for (u in unified) {
                            dateTimeColPx = maxOf(dateTimeColPx, measureMain.measureText(u.dateText))
                            typeByColPx = maxOf(typeByColPx, measureMain.measureText(u.typeText), measureSub.measureText(u.byText.ifBlank { "\u2014" }))
                        }
                        val dateTimeColWidth = dateTimeColPx.toInt() + cellPadPx
                        val typeByColWidth = typeByColPx.toInt() + cellPadPx

                        fun tableCellBorder() = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.WHITE)
                            setStroke(dgpx(1), android.graphics.Color.parseColor("#E2E8F0"))
                        }
                        val tableBox = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            val lp = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                            lp.setMargins(dgpx(14), dgpx(8), dgpx(14), dgpx(20))
                            layoutParams = lp
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(android.graphics.Color.WHITE); cornerRadius = dgpx(10).toFloat()
                            }
                            clipToOutline = true
                        }
                        val headRow = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setBackgroundColor(android.graphics.Color.parseColor("#0F8F6F"))
                        }
                        fun headCell(t: String, w: Int) = TextView(this@DoctorVisitActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(w, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                            text = t; textSize = 9.5f; setTextColor(android.graphics.Color.WHITE)
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(dgpx(8), dgpx(7), dgpx(8), dgpx(7))
                        }
                        headRow.addView(headCell("Date/Time", dateTimeColWidth))
                        headRow.addView(headCell("Type / By", typeByColWidth))
                        headRow.addView(TextView(this@DoctorVisitActivity).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            text = "Note"; textSize = 9.5f; setTextColor(android.graphics.Color.WHITE)
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(dgpx(8), dgpx(7), dgpx(8), dgpx(7))
                        })
                        tableBox.addView(headRow)
                        // 🔒 TK-LOCKED RULE (খাতার (ঞ) সারি ৮ · V142 · আবার
                        // 28.07.2026): এই টেবিলের সব সারি ও সব বক্সের উচ্চতা এক
                        // হবে। TK-এর নির্দেশ: "যত জায়গায় ভিউ অল চাপলে এই স্ক্রিন
                        // আসে, সমাধান সব জায়গাতেই করতে হবে।" তাই এখানেও সেই
                        // একটাই শেয়ার্ড হিসেব (TableRowEqualizer) ডাকা হয়।
                        val builtRows = ArrayList<android.widget.LinearLayout>()
                        for (u in unified) {
                            val row = android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                // V375, owner live-test proof: this dynamically-built
                                // table recreated/reset the triple-tap state on the phone.
                                // A normal single tap now opens only Referral Edit/Delete.
                                // It never routes to the Patient Report Card.
                                if (u.onEdit != null) {
                                    isClickable = true; isFocusable = true
                                    val rowView = this
                                    setOnClickListener {
                                        rowView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        u.onEdit.invoke()
                                    }
                                } else if (u.onTap != null) { isClickable = true; isFocusable = true; setOnClickListener { u.onTap.invoke() } }
                            }
                            row.addView(android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                layoutParams = android.widget.LinearLayout.LayoutParams(dateTimeColWidth, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                                setPadding(dgpx(8), dgpx(8), dgpx(8), dgpx(8))
                                background = tableCellBorder()
                                addView(TextView(this@DoctorVisitActivity).apply {
                                    text = u.dateText; textSize = 9.3f; setTextColor(android.graphics.Color.parseColor("#334155"))
                                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                                })
                            })
                            row.addView(android.widget.LinearLayout(this@DoctorVisitActivity).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                layoutParams = android.widget.LinearLayout.LayoutParams(typeByColWidth, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                                setPadding(dgpx(8), dgpx(8), dgpx(8), dgpx(8))
                                background = tableCellBorder()
                                addView(TextView(this@DoctorVisitActivity).apply {
                                    text = u.typeText; textSize = 9.3f; setTextColor(android.graphics.Color.parseColor(u.typeColorHex))
                                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                                })
                                if (u.byText.isNotBlank()) {
                                    addView(TextView(this@DoctorVisitActivity).apply {
                                        text = u.byText; textSize = 8.5f; setTextColor(android.graphics.Color.parseColor("#10223A"))
                                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                                        // 🔒 খাতার সারি B63 (TK, 29.07.2026): স্টাফের নাম/কোড কোথাও কাটা
                                        // যাবে না — লম্বা হলে নিচের লাইনে নামবে।
                                        maxLines = 2; ellipsize = null
                                        setPadding(0, dgpx(1), 0, 0)
                                    })
                                }
                            })
                            row.addView(TextView(this@DoctorVisitActivity).apply {
                                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                // TK-REQUESTED (2026-07-24): when this row is
                                // tappable (Report Card opens), the patient
                                // name at the start of the note is colored blue
                                // -- no arrow, just a color hint that it's
                                // tappable, per TK's exact request.
                                if (u.highlightName != null && u.noteText.startsWith(u.highlightName)) {
                                    val sp = android.text.SpannableString(u.noteText)
                                    sp.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#1457B8")), 0, u.highlightName.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, u.highlightName.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    text = sp
                                } else {
                                    text = u.noteText
                                }
                                textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#334155"))
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                                setPadding(dgpx(8), dgpx(8), dgpx(8), dgpx(8))
                                background = tableCellBorder()
                            })
                            // V376 live-device root cause: the three visible
                            // cells fill the row and receive the finger touch;
                            // a listener only on the outer row was therefore
                            // unreliable. Put the same approved Edit action on
                            // every visible cell. No route or design changes.
                            if (u.onEdit != null) {
                                val editAction = u.onEdit
                                for (cellIndex in 0 until row.childCount) {
                                    row.getChildAt(cellIndex).apply {
                                        isClickable = true
                                        isFocusable = true
                                        setOnClickListener {
                                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                            editAction.invoke()
                                        }
                                    }
                                }
                            }
                            tableBox.addView(row)
                            builtRows.add(row)
                        }
                        TableRowEqualizer.equalize(tableBox, builtRows)
                        scrollBody.addView(tableBox)
                    }
                }
                renderBody()

                UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
                fsDialog = AlertDialog.Builder(this@DoctorVisitActivity).setView(root).create()
                fsDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                fsDialog.show()
                // 🔒 TK-ORDER (30.07.2026 · খাতার সারি B156, **দ্বিতীয়বার বলতে
                // হয়েছে**): শুধু window MATCH_PARENT করলে হয় না — AlertDialog-এর
                // নিজের কনটেইনারগুলো WRAP_CONTENT, তাই পর্দা অর্ধেক আসত।
                // এখন দেখানোর পরে ওই কনটেইনারগুলোরও উচ্চতা মিলিয়ে দেওয়া হয়।
                forceDialogFullScreen(fsDialog, root)
                // ⚡ পর্দা খুলে গেছে। এবার পিছনের কাজ শেষ হলে সংখ্যা ও তালিকা বসে যায়।
                // ⛔ কোনো হিসাব বদলায় না — ঠিক আগের কোডই আবার চলে, শুধু আসল তথ্য নিয়ে।
                lifecycleScope.launch {
                    val real = try { dataJob.await() } catch (_: Throwable) { null }
                    // 🔴 TK-REPORTED FIX (30.07.2026 রাত): আসল ডেটা এসে গেলেই
                    // (দ্রুত হোক বা ধীর) "লোড হচ্ছে…" টোস্ট সঙ্গে সঙ্গে বন্ধ --
                    // আর নিজের ফিক্সড সময়ের জন্য পড়ে থাকবে না।
                    dvLoadingToast?.cancel()
                    if (isFinishing || isDestroyed || !fsDialog.isShowing) return@launch
                    if (real == null) {
                        // No cache + failed refresh must never look like a true ₹0/0 result.
                        // With cache, keep the last successful snapshot quietly on screen.
                        if (cachedViewAllData == null) {
                            Toast.makeText(this@DoctorVisitActivity, "Could not load details — check connection and try again", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    data = real
                    saveViewAllCache(real)
                    renderBody()
                }
            } catch (e: Exception) {
                dvLoadingToast?.cancel()
                Toast.makeText(this@DoctorVisitActivity, "Could not load details — check connection and try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔒🔒 TK-ORDER (31.07.2026, খাতার সারি B211 — TK: "মাস্টার এডমিন বুঝবে কি
    // করে কোন আরএমপি ডাক্তার কত পেশেন্ট পাঠিয়েছে... এই মাসে কোন কোন ডাক্তার
    // কত পেসেন্ট পাঠিয়েছে, সর্বমোট কোন কোন ডাক্তার পেসেন্ট পাঠিয়েছে, কোন
    // ডাক্তারের পারফরম্যান্স ভালো... এটা শুধু মাস্টারই দেখতে পারবে")। TK
    // নিজে নিশ্চিত করেছেন: (১) বাটন RMP পর্দাতেই, ৪ বক্সের নিচে (২) তালিকা
    // সাজানো হবে সবচেয়ে সাম্প্রতিক রেফারেলের তারিখ অনুযায়ী (৩) প্রতিটা
    // অংশ (This Month/All-Time/Ref Paid/পুরো কার্ড) চাপলে যেন ডিটেলস
    // বেরিয়ে আসে। ⛔ নতুন কোনো ডিটেল-পপ-আপ বানানো হয়নি — সব ট্যাপ আগে
    // থেকে থাকা, ইতিমধ্যে TK-অনুমোদিত `showDoctorViewAll()`-ই খোলে, যেখানে
    // Referred Patient তালিকা, Referral Income তালিকা (তারিখসহ), আর পুরো
    // কল/রেফারেল-ইতিহাসের টেবিল — সবই একসাথে আছে। এতে একই ডেটার দুই রকম
    // হিসাব কখনো তৈরি হবে না।
    /**
     * 🟢🔒 B685 (15.08.2026, TK-অনুমোদিত · ছবি-প্রুফ পাশ করার পরে)
     *
     * TK-এর কথা হুবহু: *"কোন আরএমপির কত টাকা ডিউ আছে — এটাতো স্টাফ অথবা
     * ব্রাঞ্চ দেখে আমাকে বলবে ... স্যার এই আরএমপির এত টাকা বাকি আছে"* এবং
     * *"যাদের টাকা দিতে এখনো বাকি তাদের একটা লিস্ট staff & doctor দেরকে
     * দেখাক"*।
     *
     * এতদিন Due দেখার একমাত্র পথ ছিল প্রতিটা কার্ডের 👁 (View All) —
     * একজন একজন করে। ৪১৩ জনের ব্রাঞ্চে সেটা কার্যত অসম্ভব।
     *
     * 💰 ফ্রি প্ল্যানের খরচ (TK-কে আগে জানিয়ে অনুমতি নেওয়া হয়েছে):
     *   ⛔ তালিকাটা **একটাও নতুন ক্লাউড-কল ছাড়া** তৈরি হয় — `allItems`-এ
     *     ব্রাঞ্চের যে সারিগুলো এমনিতেই নামানো আছে, তার ভিতরের
     *     `referralPayments` থেকেই হিসাব হয় (হুবহু সেই নিয়ম, যা
     *     showDoctorViewAll-এর "verifiedLegacyDue" আর ওয়েবের
     *     `doctorReferralTotals()` আগে থেকেই ব্যবহার করে — নতুন কোনো
     *     হিসাবের নিয়ম বানানো হয়নি, তাই সংখ্যা কখনো আলাদা হবে না)।
     *   ⛔ তারপর **শুধু যাদের বাকি আছে** তাদেরই (সর্বোচ্চ ২৫ জন) সার্ভারে
     *     মিলিয়ে নেওয়া হয় — ৪১৩ জনের জন্য নয়। মেলানো ব্যর্থ হলে ফোনের
     *     হিসাবটাই থাকে, কোনো ভুল সংখ্যা দেখানো হয় না।
     *
     * ⛔ যা এক অক্ষরও বদলায়নি: PENDING/CALLED/EXPECTED/ALL RMP-এর গোনা,
     *    ফিল্টার, সাজানো (B193) · 🏆 RMP Performance Report Master-only
     *    (B211) · কার্ডের ডিজাইন · কোনো টাকা এখান থেকে সেভ/বদল হয় না
     *    (এটা সম্পূর্ণ **পড়ার** পর্দা)।
     */
    private fun showRmpDueList() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f * d
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#12233F"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        // 🔧 TK-নির্দেশ (15.08.2026, ছবি দেখে): "যেগুলি পাশাপাশি রাখা যায়
        //    সেগুলো পাশাপাশি রাখুন · কোন প্রকার আইকন রাখার দরকার নেই" —
        //    এই পর্দার 💰 / 📞 / 📍 সব আইকন বাদ, আর মোবাইল · ব্রাঞ্চ ·
        //    এলাকা · Paid এক সারিতে এসেছে।
        header.addView(TextView(this).apply {
            text = "RMP Due List"
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 🔒 ব্রাঞ্চ শুধু **দেখানো** হয় (বাছার পিল নয়) — এই তালিকা সবসময়
        //    ঠিক সেই ব্রাঞ্চেরই, যেটা এখন পর্দায় খোলা আছে। এতে অন্য
        //    ব্রাঞ্চের টাকার তথ্য ভুল করেও দেখা যায় না।
        header.addView(TextView(this).apply {
            text = (activeDoctorBranch()?.ifBlank { "All" } ?: "All")
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_branch_pill)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        })
        root.addView(header)

        // উপরের মোট-বাক্স এক সারিতে: বাঁয়ে লেখা, ডানে টাকা (TK: পাশাপাশি)।
        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FDE7E5"))
                cornerRadius = 14f * d
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(12), dp(12), dp(12), dp(4))
            layoutParams = lp
        }
        val totalBox = TextView(this).apply {
            textSize = 10.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#8A2B22"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val totalAmount = TextView(this).apply {
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#C0392B"))
        }
        totalRow.addView(totalBox)
        totalRow.addView(totalAmount)
        root.addView(totalRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val scrollBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
        }
        scroll.addView(scrollBody)
        root.addView(scroll)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(actionRow)

        val fsDialog = AlertDialog.Builder(this).setView(root).create()
        fsDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        fsDialog.setOnShowListener { try { NoBengali.installDialog(fsDialog) } catch (_: Throwable) { } }
        actionRow.addView(pillButton("Close", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { fsDialog.dismiss() }
        })

        // 🔒 Locale.US বাধ্যতামূলক — ফোনের ভাষা বাংলা হলে "%,.0f" বাংলা
        //    সংখ্যা (০-৯) ছাপত, আর NoBengali সেগুলো মুছে দিয়ে শুধু "₹"
        //    রেখে দিত। প্রজেক্টের বাকি টাকার হিসাবেও এই একই নিয়ম।
        // 🔴 V430 (TK-সিদ্ধান্ত ১৮.০৮.২০২৬) — ভারতীয় ভাগে (২,১০,৮৫০), অ্যাপের
        //    বাকি সব পর্দার মতোই। আগে এখানে Locale.US ছিল।
        fun money(v: Double): String = "\u20B9" + com.tkbiswas.pilesclinic.native.MoneyFormat.inr(v)

        fun rowCard(item: DoctorVisitItem, due: Double, paid: Double, serial: Int): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 12f * d
                    setStroke(dp(1), android.graphics.Color.parseColor("#E2E8F0"))
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
                isClickable = true
                // ⛔ চাপলে আগে-থেকে-অনুমোদিত সেই "View All"-ই খোলে — পুরো
                //    হিস্টরি, টাকা দেওয়ার ঘর, সব ওখানেই আগের মতো আছে।
                setOnClickListener { showDoctorViewAll(item) }
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(this).apply {
                text = serial.toString()
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#C0392B"))
                setPadding(dp(7), dp(2), dp(7), dp(2))
            })
            // 🔒 TK-নির্দেশ (15.08.2026): নাম · Paid · Due **একই লাইনে**।
            //    নাম কখনো দু'লাইনে ভাঙবে না, কাটবেও না — জায়গা কম পড়লে
            //    Android-এর নিজস্ব auto-size লেখাটাকে ছোট করে দেয় (8-13sp)।
            //    ⛔ নামে **এক চাপ** → ওই RMP কতজন পেশেন্ট পাঠিয়েছেন তার
            //      ডিটেইলস (আগে-থেকে-অনুমোদিত View All — "Referred Patient"
            //      তালিকা ওখানেই আছে; নতুন কোনো পর্দা বানানো হয়নি)।
            //    ⛔ নামে **চেপে ধরলে** নাম কপি হয় (প্রজেক্টে আগে থেকে থাকা
            //      `copyOnLongPress` — নতুন কপি-কোড লেখা হয়নি)।
            top.addView(TextView(this).apply {
                text = item.name.ifBlank { item.mobile }
                maxLines = 1
                setSingleLine(true)
                ellipsize = null
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#10223A"))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginStart = dp(8)
                layoutParams = p
                try {
                    androidx.core.widget.TextViewCompat
                        .setAutoSizeTextTypeUniformWithConfiguration(
                            this, 8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                } catch (_: Throwable) { textSize = 12.5f }
                isClickable = true
                setOnClickListener { showDoctorViewAll(item) }
                copyOnLongPress("RMP name", item.name.ifBlank { item.mobile })
            })
            top.addView(TextView(this).apply {
                text = "Paid " + money(paid)
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#0B6B34"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#E9F7EE"))
                    cornerRadius = 7f * d
                }
                setPadding(dp(7), dp(4), dp(7), dp(4))
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.marginStart = dp(5)
                layoutParams = p
            })
            top.addView(TextView(this).apply {
                text = "Due " + money(due)
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#C0392B"))
                    cornerRadius = 7f * d
                }
                setPadding(dp(7), dp(4), dp(7), dp(4))
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.marginStart = dp(5)
                layoutParams = p
            })
            card.addView(top)
            // দ্বিতীয় সারি — মোবাইল · ব্রাঞ্চ · এলাকা বাঁয়ে, Paid ডানে
            // (TK: "যেগুলি পাশাপাশি রাখা যায় সেগুলো পাশাপাশি রাখুন")।
            val line2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, 0)
            }
            // মোবাইল আলাদা ঘরে, যাতে চেপে ধরলে **শুধু নম্বরটাই** কপি হয়।
            line2.addView(TextView(this).apply {
                text = item.mobile
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#3D4A5C"))
                copyOnLongPress("Mobile number", item.mobile)
            })
            line2.addView(TextView(this).apply {
                text = listOf(item.branch, item.area)
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginStart = dp(8)
                layoutParams = p
            })
            card.addView(line2)
            return card
        }

        /**
         * ফোনে আগে থেকেই থাকা সারি থেকে (paid, due) — হুবহু সেই নিয়ম যা
         * showDoctorViewAll আর ওয়েবের doctorReferralTotals() ব্যবহার করে:
         * `status == "Paid"` হলে দেওয়া, নইলে বাকি। ⛔ নতুন নিয়ম নয়।
         */
        fun localTotals(item: DoctorVisitItem): Pair<Double, Double> {
            var paid = 0.0
            var due = 0.0
            try {
                val arr = item.raw.optJSONArray("referralPayments") ?: org.json.JSONArray()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val amt = e.optDouble("amount", 0.0)
                    if (e.s("status").equals("Paid", ignoreCase = true)) paid += amt else due += amt
                }
            } catch (_: Throwable) { }
            return Pair(paid, due)
        }

        val noBranchChosen = user.role == "master" &&
            (branchFilter.isBlank() || branchFilter == BRANCH_NONE)

        fun render(list: List<Triple<DoctorVisitItem, Pair<Double, Double>, Boolean>>) {
            scrollBody.removeAllViews()
            val totalDue = list.sumOf { it.second.second }
            // 🔴 Master ব্রাঞ্চ না বাছলে তালিকা এমনিতেই ফাঁকা থাকে — তখন
            //    "কারো বাকি নেই" লেখা সম্পূর্ণ ভুল তথ্য হত। আলাদা বার্তা।
            if (noBranchChosen) {
                totalBox.text = "SELECT A BRANCH FIRST\nChoose a branch on the RMP screen"
                totalAmount.text = ""
                scrollBody.addView(TextView(this).apply {
                    text = "Select a branch above to see the RMP Due List"
                    textSize = 12.5f
                    setTextColor(android.graphics.Color.parseColor("#8A97AB"))
                    setPadding(dp(8), dp(20), dp(8), dp(20))
                    gravity = android.view.Gravity.CENTER
                })
                return
            }
            if (list.isEmpty()) {
                totalBox.text = "NOBODY IS PENDING\nEvery RMP is fully settled"
                totalAmount.text = money(0.0)
            } else {
                totalBox.text = "TOTAL STILL TO PAY\n" + list.size +
                    (if (list.size == 1) " RMP has" else " RMP have") + " money pending · highest first"
                totalAmount.text = money(totalDue)
            }
            if (list.isEmpty()) {
                scrollBody.addView(TextView(this).apply {
                    text = "No RMP of this branch has any pending referral money"
                    textSize = 12.5f
                    setTextColor(android.graphics.Color.parseColor("#8A97AB"))
                    setPadding(dp(8), dp(20), dp(8), dp(20))
                    gravity = android.view.Gravity.CENTER
                })
                return
            }
            list.forEachIndexed { idx, t ->
                scrollBody.addView(rowCard(t.first, t.second.second, t.second.first, idx + 1))
            }
        }

        // ধাপ ১ — ফোনের তথ্য দিয়ে সঙ্গে সঙ্গে দেখানো (খরচ শূন্য)।
        var rows = allItems
            .map { Triple(it, localTotals(it), false) }
            .filter { it.second.second > 0.0 }
            .sortedByDescending { it.second.second }
        render(rows)
        fsDialog.show()
        forceDialogFullScreen(fsDialog, root)

        // ধাপ ২ — শুধু এই তালিকার (সর্বোচ্চ ২৫) RMP-র সংখ্যা ক্লাউডে মিলিয়ে
        //   নেওয়া। ⛔ ব্যর্থ হলে ফোনের সংখ্যাটাই থাকে, কিছু ভাঙে না।
        lifecycleScope.launch {
            try {
                if (noBranchChosen) return@launch
                val check = rows.take(25)
                // 🔴🔒 V411 (TK-রিপোর্ট, ছবিসহ, ১৭.০৮.২০২৬): এখানে আগে
                //    `if (check.isEmpty()) return@launch` লেখা ছিল — অর্থাৎ **পুরনো**
                //    পদ্ধতিতে কারও কিছু বাকি না থাকলে ক্লাউডকে জিজ্ঞেসই করা হত না।
                //    ঠিক এই কারণেই PK-র ₹৪১,৭৫০ কোনোদিন এই তালিকায় উঠত না, আর
                //    পর্দায় লেখা থাকত "NOBODY IS PENDING"। লাইনটা বাদ দেওয়া হলো।
                val fixed = withContext(Dispatchers.IO) {
                    // 🔒 প্রজেক্টের প্রমাণিত সাইন-ইন ধাপ (showDoctorViewAll-এর
                    //    হুবহু একই লাইন) — সাইন-ইন না থাকলে মেলানোর চেষ্টাই
                    //    হয় না, ফোনের হিসাবটাই থাকে।
                    val expectedCode = ModuleAuth.expectedCode(this@DoctorVisitActivity)
                    if (ModuleAuth.isSignedIn && ModuleAuth.personCode != expectedCode) ModuleAuth.signOut()
                    val authReady = if (ModuleAuth.isSignedIn) true
                        else ModuleAuth.signInCurrentSession(applicationContext) == null
                    val empty = emptyList<RmpCommissionRepository.BranchDueRow>()
                    if (!authReady) return@withContext Pair(check, empty)
                    val verified = check.map { t ->
                        val res = try { RmpCommissionRepository.rmpSummary(t.first.id) } catch (_: Throwable) { null }
                        val v = res?.value
                        if (res != null && res.ok && v != null)
                            Triple(t.first, Pair(v.paid, v.due), true)
                        else t
                    }
                    // 🔵 V411: **এক ডাকেই** এই ব্রাঞ্চের সব RMP-র নতুন-পদ্ধতির পাওনা।
                    //    ব্যর্থ/উদ্ভট হলে ফাঁকা তালিকা ⇒ আগের আচরণ হুবহু বজায় থাকে।
                    val modern = try {
                        val br = activeDoctorBranch() ?: ""
                        val res = if (br.isNotBlank()) RmpCommissionRepository.branchDue(br) else null
                        if (res != null && res.ok && res.value != null) res.value else empty
                    } catch (_: Throwable) { empty }
                    Pair(verified, modern)
                }
                if (isFinishing || isDestroyed || !fsDialog.isShowing) return@launch
                // 🔵 V411: পুরনো ও নতুন — দুটো মিলিয়ে একটাই তালিকা।
                //    একই RMP দুই জায়গায় থাকলে **নতুন পদ্ধতির সংখ্যাই** চূড়ান্ত —
                //    কারণ ডাক্তারের নিজের কার্ডেও (Ref. Due) ওটাই দেখানো হয়,
                //    তাই দুই পর্দায় আর কখনো আলাদা সংখ্যা দেখাবে না।
                val byId = allItems.associateBy { it.id }
                val merged = LinkedHashMap<String, Triple<DoctorVisitItem, Pair<Double, Double>, Boolean>>()
                for (t in (fixed.first + rows.drop(25))) merged[t.first.id] = t
                for (m in fixed.second) {
                    val item = byId[m.rmpId] ?: continue
                    merged[m.rmpId] = Triple(item, Pair(m.paid, m.due), true)
                }
                rows = merged.values.toList()
                    .filter { it.second.second > 0.0 }
                    .sortedByDescending { it.second.second }
                render(rows)
            } catch (_: Throwable) { }
        }
    }

    private fun showRmpPerformanceReport() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 20f * d
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#12233F"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "🏆 RMP Performance"
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 🔒 একই ব্রাঞ্চ-পিল মডেল প্রজেক্টের বাকি সব জায়গার মতো (খাতার সারি B84)।
        val branchPill = TextView(this).apply {
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_branch_pill)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
        }
        header.addView(branchPill)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val scrollBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        scroll.addView(scrollBody)
        root.addView(scroll)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(actionRow)

        val fsDialog = AlertDialog.Builder(this).setView(root).create()
        fsDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        fsDialog.setOnShowListener { try { NoBengali.installDialog(fsDialog) } catch (_: Throwable) { } }

        actionRow.addView(pillButton("Close", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { fsDialog.dismiss() }
        })

        // মাস্টার নিজের ব্রাঞ্চ থেকে শুরু করেন যদি একটা বাছা থাকে, নইলে "All"।
        var perfBranch = if (branchFilter.isNotBlank() && branchFilter != BRANCH_NONE) branchFilter else "All"
        branchPill.text = "🏥 $perfBranch  ▾"

        fun rowCard(row: DoctorVisitRepository.RmpPerformanceRow, serial: Int): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 12f * d
                    setStroke(dp(1), android.graphics.Color.parseColor("#E2E8F0"))
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
                isClickable = true
                // ⛔ পুরো কার্ডে চাপলেও একই "View All" খোলে — সব তথ্য একই জায়গায়।
                setOnClickListener { showDoctorViewAll(row.doctor) }
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(this).apply {
                text = serial.toString()
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#7C3AED"))
                setPadding(dp(7), dp(2), dp(7), dp(2))
            })
            top.addView(TextView(this).apply {
                text = row.doctor.name.ifBlank { row.doctor.mobile }
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#10223A"))
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.marginStart = dp(8)
                layoutParams = p
            })
            card.addView(top)
            card.addView(TextView(this).apply {
                text = "${row.doctor.branch} · ${row.doctor.area}".trim(' ', '·')
                textSize = 10.5f
                setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                setPadding(0, dp(2), 0, dp(8))
            })

            fun metricBox(value: String, label: String): LinearLayout {
                return LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#F4F1FF"))
                        cornerRadius = dp(8).toFloat()
                    }
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.marginEnd = dp(6)
                    layoutParams = lp
                    isClickable = true
                    // ⛔ TK-এর নির্দেশ: "This Month/All Time/Ref Paid সবগুলোতেই
                    // চাপ দিলে যেন কাজ হয়" — তিনটেই একই সম্পূর্ণ View All খোলে,
                    // যেখানে এই ডাক্তারের রেফার-করা প্রতিটা পেশেন্ট ও প্রতিটা
                    // রেফারেল-ইনকাম এন্ট্রি তারিখসহ দেখা যায়।
                    setOnClickListener { showDoctorViewAll(row.doctor) }
                    addView(TextView(this@DoctorVisitActivity).apply {
                        text = value; textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#7C3AED"))
                        gravity = android.view.Gravity.CENTER
                    })
                    addView(TextView(this@DoctorVisitActivity).apply {
                        text = label; textSize = 8f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                        gravity = android.view.Gravity.CENTER
                    })
                }
            }
            val metricsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            metricsRow.addView(metricBox(row.thisMonthCount.toString(), "THIS MONTH"))
            metricsRow.addView(metricBox(row.allTimeCount.toString(), "ALL-TIME"))
            metricsRow.addView(metricBox("₹" + "%,.0f".format(row.refPaid), "REF. PAID").apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
            })
            card.addView(metricsRow)
            if (row.mostRecentDate.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = "⏰ Last referral: ${FollowUpModel.displayDate(row.mostRecentDate)}"
                    textSize = 9.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#B45309"))
                    setPadding(0, dp(8), 0, 0)
                })
            }
            return card
        }

        fun loadAndRender() {
            scrollBody.removeAllViews()
            scrollBody.addView(TextView(this).apply {
                text = "Loading…"
                textSize = 12.5f
                setTextColor(android.graphics.Color.parseColor("#8A97AB"))
                setPadding(dp(8), dp(20), dp(8), dp(20))
                gravity = android.view.Gravity.CENTER
            })
            lifecycleScope.launch {
                // 🔴 B422 (05.08.2026) — আগে ব্যর্থ (network/timeout) হলে চুপচাপ
                // emptyList() ধরে "No RMP has referred..." দেখাত — TK-এর কাছে
                // মনে হতো ডেটা নেই, অথচ আসলে লোডই হয়নি। এখন failed=true হলে
                // স্পষ্ট আলাদা বার্তা + "Retry" বোতাম দেখানো হয়।
                var failed = false
                val rows = try {
                    withContext(Dispatchers.IO) {
                        repository.fetchRmpPerformance(if (perfBranch == "All") null else perfBranch)
                    }
                } catch (_: Throwable) { failed = true; emptyList() }
                if (isFinishing || isDestroyed || !fsDialog.isShowing) return@launch
                scrollBody.removeAllViews()
                if (failed) {
                    scrollBody.addView(TextView(this@DoctorVisitActivity).apply {
                        text = "Could not load — check connection and try again"
                        textSize = 12.5f
                        setTextColor(android.graphics.Color.parseColor("#B8324A"))
                        setPadding(dp(8), dp(20), dp(8), dp(6))
                        gravity = android.view.Gravity.CENTER
                    })
                    scrollBody.addView(pillButton("🔄 Retry", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
                        setOnClickListener { loadAndRender() }
                    })
                } else if (rows.isEmpty()) {
                    scrollBody.addView(TextView(this@DoctorVisitActivity).apply {
                        text = "No RMP has referred a patient yet in this selection"
                        textSize = 12.5f
                        setTextColor(android.graphics.Color.parseColor("#8A97AB"))
                        setPadding(dp(8), dp(20), dp(8), dp(20))
                        gravity = android.view.Gravity.CENTER
                    })
                } else {
                    rows.forEachIndexed { idx, row -> scrollBody.addView(rowCard(row, idx + 1)) }
                }
            }
        }

        branchPill.setOnClickListener {
            val branchOptions = listOf("All") + branches
            val now = branchOptions.indexOf(perfBranch).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Branch"))
                .setSingleChoiceItems(branchOptions.toTypedArray(), now) { dlg2, which ->
                    perfBranch = branchOptions[which]
                    // 🟢🔒 V398: এখানে বাছলে সেটাও মনে থাকবে (পুরো অ্যাপে একটাই জায়গা)।
                    BranchFilterStore.set(this@DoctorVisitActivity, perfBranch)
                    branchPill.text = "🏥 $perfBranch  ▾"
                    loadAndRender()
                    dlg2.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }

        fsDialog.show()
        forceDialogFullScreen(fsDialog, root)
        loadAndRender()
    }

    // ⚠️ NOTE (30.07.2026 রাত, খাতার সারি B206): "Doctor Call Remarks" পপ-আপ
    // থেকে "💰 Referral" বোতাম বাদ দেওয়ায় (TK-এর নির্দেশে, Remarks/Referral
    // সরিয়ে শুধু তারিখ রাখা হয়েছে) এই ফাংশনটা আপাতত আর কোথাও থেকে ডাকা হয়
    // না — রেফারেল ইনকাম যোগ করার আসল পথ এখন `showAddReferralIncome()`
    // (View All স্ক্রিনের "💰 Referral Income" বোতাম, আগের মতোই কাজ করছে)।
    // ⛔ ফাংশনটা মুছে ফেলা হয়নি (TK-কে না জানিয়ে কোড বাদ দেওয়া নিষেধ) —
    // শুধু এখন dead code, ভবিষ্যতে দরকার পড়লে আবার ব্যবহার করা যাবে।
    private fun showReferralIncome(item: DoctorVisitItem) {
        // 🔒 TK-এর নিয়ম (28.07.2026): চাপ দেওয়ামাত্র স্টাফ বুঝবেন কাজ শুরু হয়েছে।
        Toast.makeText(this, "Loading…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            // CRASH-SAFETY FIX (TK-reported, 2026-07-16): same fix as
            // showDoctorViewAll/showCallSummary above -- this had no error
            // handling and used unsafe getJSONObject() calls that could
            // crash the WHOLE APP on any malformed row.
            try {
                val res = withContext(Dispatchers.IO) {
                    // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): same tables,
                    // same rows, same limit, same order -- ONLY the columns this
                    // block actually reads are downloaded now. ⛔ Not one figure
                    // can change: every line of the matching and adding below is
                    // left word for word, and if a narrowed read ever fails,
                    // fetchListSlim asks for every column again by itself.
                    val pats = SupabaseClient.fetchListSlim("patients", null, 5000, "id,name,mobile,bill,refBy,refDoctorMobile,registrationDate,date,updatedAt")
                    val pays = SupabaseClient.fetchListSlim("payments", null, 5000, "id,mobile,amount,payType,refundApprovalStatus,updatedAt")
                    val paidByMobile = HashMap<String, Double>()
                    // 🔴🔴 TK-অডিট-অনুরোধ (01.08.2026, প্রজেক্ট-জোড়া যাচাই): Refund
                    // সারিও প্লেইন পজিটিভ Payment হিসেবে যোগ হচ্ছিল — Referral
                    // Income-এর ভিত্তি এই paidByMobile থেকেই, তাই Refund পাওয়া
                    // রোগীর টাকায় ডাক্তারের রেফারেল-আয় ভুলভাবে বেশি দেখাতে পারত।
                    // FollowUpRepository-র V238 paidEffect নিয়ম এখানেও বসানো হলো।
                    for (i in 0 until pays.length()) {
                        val p = pays.optJSONObject(i) ?: continue
                        val payType = p.optString("payType", "")
                        if (payType == "visit_fee" || payType == "attendance_mark") continue
                        val paidEffect = when {
                            PaymentModel.isApprovedRefund(p) -> -p.optDouble("amount", 0.0)
                            PaymentModel.isRefundRow(p) -> 0.0
                            else -> p.optDouble("amount", 0.0)
                        }
                        val m = p.s("mobile").filter { it.isDigit() }.takeLast(10)
                        paidByMobile[m] = (paidByMobile[m] ?: 0.0) + paidEffect
                    }
                    val sb = StringBuilder()
                    var bt = 0.0; var pd = 0.0; var c = 0
                    val docName = item.name.trim().lowercase()
                    val docMobile = item.mobile.filter { it.isDigit() }.takeLast(10)
                    for (i in 0 until pats.length()) {
                        val pat = pats.optJSONObject(i) ?: continue
                        val refBy = pat.s("refBy").trim().lowercase()
                        val refMob = pat.s("refDoctorMobile").filter { it.isDigit() }.takeLast(10)
                        val hit = (refBy.isNotBlank() && refBy == docName) || (refMob.isNotBlank() && refMob == docMobile)
                        if (!hit) continue
                        val m = pat.s("mobile").filter { it.isDigit() }.takeLast(10)
                        val bill = pat.optDouble("bill", 0.0)
                        val paid = paidByMobile[m] ?: 0.0
                        bt += bill; pd += paid; c++
                        sb.append("• ${pat.s("name")} — Bill ₹${"%,.0f".format(bill)}, Paid ₹${"%,.0f".format(paid)}\n")
                    }
                    Quad(sb.toString(), bt, pd, c)
                }
                val msg = if (res.d == 0) "No patients referred by this doctor were found."
                else "Referred patients: ${res.d}\nTotal Bill: ₹${"%,.0f".format(res.b)}\nTotal Collected: ₹${"%,.0f".format(res.c)}\n\n${res.a}"
                AlertDialog.Builder(this@DoctorVisitActivity)
                    .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Referral Income — ${item.name}"))
                    .setMessage(msg)
                    .setNeutralButton("➕ Add Income") { _, _ -> showAddReferralIncome(item) }
                    .setPositiveButton("Close", null)
                    .show().also { PremiumAlert.paint(it) }
            } catch (e: Exception) {
                Toast.makeText(this@DoctorVisitActivity, "Could not load referral income — check connection and try again", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 🟢 B628 (11.08.2026, TK-নির্দেশ): Referral Income এন্ট্রি এডিট (তিনবার-চাপ)।
     *  Add Referral Income ডায়ালগেরই চেহারা (ইংরেজি) — Amount + Status (Unpaid/Paid) +
     *  Delete। মাস্টার বা একই-দিনের স্টাফ/ডাক্তার সরাসরি বদলায়; দিন পেরোলে
     *  স্টাফ/ডাক্তার শুধু মাস্টারকে অনুরোধ পাঠায় (payment edit-এর মতোই)। */
    private fun openReferralEdit(item: DoctorVisitItem, entryId: String, curAmount: Double, curStatus: String, entryDate: String, patient: String, patientMobile: String) {
        val u = NativeSession.current(this)
        if (u == null) { Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show(); return }
        // V374: Open the editor immediately even for an old entry without an
        // id. V373 tried to write an id to cloud before opening this dialog;
        // if that preliminary write stalled/failed, the third tap appeared to
        // do nothing. The exact legacy match is now resolved only after the
        // user actually chooses Save/Delete (see submitReferralChange).
        val isMaster = u.role == "master"
        val sameDay = entryDate == DoctorVisitModel.today()
        val parts = premiumDialogShell("✏️", "Edit Referral Income — ${item.name}")
        val container = parts.body

        container.addView(fieldLabel("💵", "Referral Amount *", 0))
        val amountInput = android.widget.EditText(this).apply {
            setText("%.0f".format(curAmount))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(amountInput)
        container.addView(amountInput)

        container.addView(fieldLabel("📊", "Status"))
        val statusSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Due", "Paid"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            setSelection(if (curStatus.equals("Paid", true)) 1 else 0)
        }
        container.addView(statusSpinner)

        if (!isMaster && !sameDay) {
            container.addView(TextView(this).apply {
                text = "This date has passed. Saving or deleting will require Master approval."
                textSize = 11.5f
                setTextColor(android.graphics.Color.parseColor("#B8860B"))
                setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }

        parts.actionRow.addView(pillButton("🗑 Delete", "#FBECEC", android.graphics.Color.parseColor("#C0392B")).apply {
            setOnClickListener {
                AlertDialog.Builder(this@DoctorVisitActivity)
                    .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Delete this referral entry?"))
                    .setPositiveButton("Yes, Delete") { _, _ ->
                        parts.dialog.dismiss()
                        submitReferralChange(item, entryId, patient, patientMobile, entryDate, curAmount, curStatus, curAmount, curStatus, isDelete = true, isMaster = isMaster, sameDay = sameDay, u = u)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("💾 Save", "#0C9E33").apply {
            setOnClickListener {
                val newAmt = amountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (newAmt <= 0) { Toast.makeText(this@DoctorVisitActivity, "Valid referral amount required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val newStatus = if (statusSpinner.selectedItemPosition == 1) "Paid" else "Unpaid"
                parts.dialog.dismiss()
                submitReferralChange(item, entryId, patient, patientMobile, entryDate, curAmount, curStatus, newAmt, newStatus, isDelete = false, isMaster = isMaster, sameDay = sameDay, u = u)
            }
        })
        // V377: this was the missing final step. The dialog shell and all
        // Edit/Delete controls were built correctly but never displayed.
        parts.dialog.show()
    }

    /** 🟢 B628: এডিট/ডিলিট প্রয়োগ — মাস্টার/একই-দিন সরাসরি; নইলে মাস্টারকে অনুরোধ। */
    private fun submitReferralChange(item: DoctorVisitItem, entryId: String, patient: String, patientMobile: String, entryDate: String, oldAmount: Double, oldStatus: String, newAmount: Double, newStatus: String, isDelete: Boolean, isMaster: Boolean, sameDay: Boolean, u: NativeUser) {
        if (entryId.isBlank() && !isMaster && !sameDay) {
            Toast.makeText(this, "Checking this older entry…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val resolvedId = withContext(Dispatchers.IO) {
                    DoctorVisitRepository().ensureLegacyReferralEntryId(
                        item.id, date = entryDate, amount = oldAmount, status = oldStatus,
                        patient = patient, context = applicationContext
                    )
                }
                if (resolvedId.isBlank()) {
                    Toast.makeText(this@DoctorVisitActivity, "Could not identify one exact entry — nothing changed", Toast.LENGTH_LONG).show()
                } else {
                    submitReferralChange(item, resolvedId, patient, patientMobile, entryDate, oldAmount, oldStatus, newAmount, newStatus, isDelete, isMaster, sameDay, u)
                }
            }
            return
        }
        lifecycleScope.launch {
            if (isMaster || sameDay) {
                val ok = withContext(Dispatchers.IO) {
                    if (isDelete) DoctorVisitRepository().deleteReferralEntry(item.id, entryId, u.mobile, applicationContext, entryDate, oldAmount, oldStatus, patient)
                    else DoctorVisitRepository().editReferralEntry(item.id, entryId, newAmount, newStatus, u.mobile, applicationContext, entryDate, oldAmount, oldStatus, patient)
                }
                Toast.makeText(this@DoctorVisitActivity, NoBengali.s(if (ok) (if (isDelete) "Deleted." else "Saved.") else "ব্যর্থ — নেট চেক করুন"), Toast.LENGTH_SHORT).show()
                if (ok) showDoctorViewAll(item)
            } else {
                val ok = withContext(Dispatchers.IO) {
                    DoctorVisitRepository().requestReferralEdit(
                        item.id, item.name, item.mobile, item.branch,
                        entryId, patient, patientMobile,
                        oldAmount, newAmount, oldStatus, newStatus,
                        isDelete, "", u.mobile, u.name
                    )
                }
                Toast.makeText(this@DoctorVisitActivity, NoBengali.s(if (ok) "Request sent for Master approval." else "ব্যর্থ — নেট চেক করুন"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** V325 owner-approved commission entry point. The visible name remains
     * "Referral Income". Old JSON records are preserved behind "Previous
     * Records" until the new workflow has passed live testing. */
    private fun showAddReferralIncome(item: DoctorVisitItem) {
        ModuleUi.ensureSignedIn(this, "") {
            // The owner allowed the new commission workflow only for Master,
            // Staff and Doctor. Keep the Field Officer's old Referral record
            // path available, but never show the new money controls to Field.
            if (ModuleAuth.personCode == "FIELD-OFFICER") {
                showLegacyReferralIncome(item)
                return@ensureSignedIn
            }
            val choices = mutableListOf("RMP Default Commission", "Patient Commission / Payment", "Commission Summary", "Previous Records")
            if (ModuleAuth.isMaster) {
                choices.add("Advance Payment / Adjust")
                choices.add("Pending Commission Approvals")
            }
            AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Referral Income — ${item.name}"))
                .setItems(choices.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> showRmpDefaultCommission(item)
                        1 -> showPatientCommission(item)
                        2 -> showRmpCommissionSummary(item)
                        3 -> showLegacyReferralIncome(item)
                        4 -> showRmpAdvancePayments(item)
                        else -> showRmpApprovalRequests(item)
                    }
                }
                .setNegativeButton("Close", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    private fun showRmpAdvancePayments(item: DoctorVisitItem) {
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.advancePayments(item.id) }
            if (!got.ok) { Toast.makeText(this@DoctorVisitActivity, got.message, Toast.LENGTH_LONG).show(); return@launch }
            val rows = got.value ?: emptyList()
            val availableRows = rows.filter { it.available > 0.001 }
            val total = rows.sumOf { it.amount }; val allocated = rows.sumOf { it.allocated }; val available = total - allocated
            val labels = mutableListOf("Paid: ₹${"%,.2f".format(total)}  ·  Adjusted: ₹${"%,.2f".format(allocated)}  ·  Available: ₹${"%,.2f".format(available)}")
            labels.addAll(availableRows.map { "${FollowUpModel.displayDate(it.paidOn)} · ₹${"%,.2f".format(it.available)} available · ${it.mode}" })
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Advance Payment — ${item.name}"))
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which > 0) showRmpAdvancePatientPicker(item, availableRows[which - 1])
                    else if (availableRows.isEmpty()) Toast.makeText(this@DoctorVisitActivity, "No unallocated payment", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null).show().also { PremiumAlert.paint(it) }
        }
    }

    private fun showRmpAdvancePatientPicker(item: DoctorVisitItem, advance: RmpCommissionRepository.AdvancePayment) {
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.legacyViewAll(item.id) }
            if (!got.ok) { Toast.makeText(this@DoctorVisitActivity, got.message, Toast.LENGTH_LONG).show(); return@launch }
            val patients = got.value ?: emptyList()
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Select Old Patient"))
                .setItems(patients.map { "${it.name} · ${it.mobile}" }.toTypedArray()) { _, which ->
                    val p = patients[which]
                    val input = EditText(this@DoctorVisitActivity).apply { hint = "Amount"; inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.") }
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Adjust — ${p.name}"))
                        .setMessage("Available Advance: ₹${"%,.2f".format(advance.available)}")
                        .setView(input).setNegativeButton("Cancel", null)
                        .setPositiveButton("Adjust") { _, _ ->
                            val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                            if (amount <= 0 || amount > advance.available) {
                                Toast.makeText(this@DoctorVisitActivity, "Enter an amount within available Advance", Toast.LENGTH_LONG).show()
                            } else lifecycleScope.launch {
                                val ready = withContext(Dispatchers.IO) {
                                    val setting = RmpCommissionRepository.getPatientCommission(p.id)
                                    val current = setting.value
                                    when {
                                        !setting.ok -> RmpCommissionRepository.RepoResult<String>(false, message = setting.message)
                                        current == null -> RmpCommissionRepository.RepoResult<String>(false, message = "Set this patient's commission first")
                                        current.rmpId != item.id -> RmpCommissionRepository.RepoResult<String>(false, message = "This patient belongs to another RMP")
                                        else -> RmpCommissionRepository.RepoResult(true, current.id)
                                    }
                                }
                                if (!ready.ok) { Toast.makeText(this@DoctorVisitActivity, ready.message, Toast.LENGTH_LONG).show(); return@launch }
                                val balance = withContext(Dispatchers.IO) { RmpCommissionRepository.summary(p.id) }
                                if (!balance.ok || balance.value == null) {
                                    Toast.makeText(this@DoctorVisitActivity, "Could not verify patient commission Due", Toast.LENGTH_LONG).show(); return@launch
                                }
                                val verifiedBalance = balance.value ?: return@launch
                                val saveAdjustment: (Boolean) -> Unit = { allowOverDue ->
                                    lifecycleScope.launch {
                                        val saved = withContext(Dispatchers.IO) {
                                            RmpCommissionRepository.allocateAdvance(advance.id, p.id, amount, allowOverDue)
                                        }
                                        Toast.makeText(this@DoctorVisitActivity, if (saved.ok) "Advance adjusted and verified" else saved.message, Toast.LENGTH_LONG).show()
                                        if (saved.ok) showRmpAdvancePayments(item)
                                    }
                                }
                                val due = verifiedBalance.due
                                if (amount > due) {
                                    AlertDialog.Builder(this@DoctorVisitActivity)
                                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Payment is higher than Ref. Due"))
                                        .setMessage("Ref. Due: ₹${"%,.2f".format(due)}\nAdjust: ₹${"%,.2f".format(amount)}\nMore: ₹${"%,.2f".format(amount - due)}")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("Master Approve & Adjust") { _, _ -> saveAdjustment(true) }
                                        .show().also { PremiumAlert.paint(it) }
                                } else saveAdjustment(false)
                            }
                        }.show().also { PremiumAlert.paint(it) }
                }.setNegativeButton("Back", null).show().also { PremiumAlert.paint(it) }
        }
    }

    private fun showRmpCommissionSummary(item: DoctorVisitItem) {
        lifecycleScope.launch {
            val (got, advanceGot) = withContext(Dispatchers.IO) {
                RmpCommissionRepository.rmpSummary(item.id) to RmpCommissionRepository.advancePayments(item.id)
            }
            if (!got.ok || got.value == null) {
                Toast.makeText(this@DoctorVisitActivity, "Could not verify commission summary", Toast.LENGTH_LONG).show(); return@launch
            }
            val s = got.value
            val advanceRows = if (advanceGot.ok) (advanceGot.value ?: emptyList()) else emptyList()
            val advanceAvailable = advanceRows.sumOf { it.available }
            val paidIncludingAdvance = s.paid
            val msg = "Patients: ${s.patientCount}\nCommission Earned: ₹${"%,.2f".format(s.earned)}\n" +
                "Paid to this RMP: ₹${"%,.2f".format(paidIncludingAdvance)}\n" +
                (if (advanceAvailable > 0) "Unallocated Advance: ₹${"%,.2f".format(advanceAvailable)}\n" else "") +
                "Due: ₹${"%,.2f".format(s.due)}" +
                (if (s.previousRmpPaid > 0) "\nPrevious RMP Paid: ₹${"%,.2f".format(s.previousRmpPaid)}" else "") +
                (if (s.overpaid > 0) "\nMore Paid: ₹${"%,.2f".format(s.overpaid)}" else "")
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Referral Income — ${item.name}"))
                .setMessage(msg).setPositiveButton("Close", null).show().also { PremiumAlert.paint(it) }
        }
    }

    /** V380 owner-selected simple payment path: Ref. Paid -> patient -> amount.
     * Never guesses which patient's Due should be reduced. */
    private fun showRmpPaymentPatientPicker(item: DoctorVisitItem) {
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.legacyViewAll(item.id) }
            if (!got.ok) {
                Toast.makeText(this@DoctorVisitActivity, "Could not load referred patients", Toast.LENGTH_LONG).show(); return@launch
            }
            val patients = got.value ?: emptyList()
            if (patients.isEmpty()) {
                Toast.makeText(this@DoctorVisitActivity, "No referred patient found", Toast.LENGTH_LONG).show(); return@launch
            }
            val labels = patients.map { "${it.name} · ${it.mobile}" }.toTypedArray()
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Select Patient — ${item.name}"))
                .setItems(labels) { _, which ->
                    val p = patients[which]
                    val ref = PatientPhotoRepository.PatientRef(p.id, p.name, p.mobile, "", p.patientCode)
                    lifecycleScope.launch {
                        val ready = withContext(Dispatchers.IO) {
                            val existing = RmpCommissionRepository.getPatientCommission(p.id)
                            when {
                                !existing.ok -> RmpCommissionRepository.RepoResult<Unit>(false, message = existing.message)
                                existing.value == null -> {
                                    val saved = RmpCommissionRepository.setPatientCommission(p.id, item.id)
                                    RmpCommissionRepository.RepoResult(saved.ok, Unit, saved.message)
                                }
                                existing.value.rmpId != item.id -> RmpCommissionRepository.RepoResult<Unit>(false, message = "This patient is assigned to another RMP")
                                else -> RmpCommissionRepository.RepoResult(true, Unit)
                            }
                        }
                        if (ready.ok) showCommissionPayment(ref, item.id)
                        else Toast.makeText(this@DoctorVisitActivity, ready.message.ifBlank { "Set the RMP Default Commission first" }, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Close", null).show().also { PremiumAlert.paint(it) }
        }
    }

    /** RED ALERT 14.08.2026: Ref. Paid is an RMP payment action, not a patient picker. */
    private fun showRmpDirectPayment(item: DoctorVisitItem, onSaved: (() -> Unit)? = null) {
        // 🟢🔒 V398 (16.08.2026, TK-অনুমোদিত): Staff/Doctor এখন **নিজের ব্রাঞ্চের**
        //   RMP-কে রোগী না বেছেও টাকা দিতে পারবেন।
        //   ⛔ অন্য ব্রাঞ্চের RMP নয়।
        //   ⛔ Ref. Due-র চেয়ে বেশি দিতে হলে আগের মতোই **শুধু Master** অনুমোদন করতে
        //      পারেন — নিচের ডায়ালগে, এবং ডেটাবেসেও একই নিয়ম বসানো আছে
        //      (V398 SQL — `fin.rmp_record_advance`)।
        //   ⛔ আসল সুরক্ষা ডেটাবেসেই; এই পরীক্ষাটা শুধু ব্যবহারকারীকে আগেই সহজ
        //      বাংলায় জানিয়ে দেওয়ার জন্য।
        if (!ModuleAuth.isMaster && !user.branch.trim().equals(item.branch.trim(), ignoreCase = true)) {
            Toast.makeText(this,
                NoBengali.s("শুধু নিজের ব্রাঞ্চের RMP-কে টাকা দেওয়া যাবে (এই RMP: ${item.branch})"),
                Toast.LENGTH_LONG).show()
            return
        }
        val parts = premiumDialogShell("₹", "RMP Payment — ${item.name}")
        val body = parts.body
        val summary = TextView(this).apply {
            text = "Verifying Ref. Due…"; textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#145A32"))
        }
        body.addView(summary)
        body.addView(fieldLabel("", "Payment Date"))
        var paidOn = DoctorVisitModel.today()
        val dateValue = TextView(this).apply {
            text = FollowUpModel.displayDate(paidOn)
            setTextColor(android.graphics.Color.parseColor("#145A32"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = (12 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@DoctorVisitActivity, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                    paidOn = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    text = FollowUpModel.displayDate(paidOn)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        }
        body.addView(dateValue)
        body.addView(fieldLabel("💵", "Amount to Pay *"))
        val amount = EditText(this).apply {
            hint = "RMP payment amount"; isEnabled = false
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(amount); body.addView(amount)
        body.addView(fieldLabel("💳", "Payment Mode"))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cash", "Online"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field); isEnabled = false
        }
        body.addView(mode)
        body.addView(fieldLabel("🔢", "Transaction / Reference No. (Optional)"))
        val reference = EditText(this).apply { hint = "Optional"; isEnabled = false }
        styleInput(reference); body.addView(reference)
        var verifiedDue: Double? = null
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.rmpSummary(item.id) }
            if (!got.ok || got.value == null) { summary.text = "Could not verify Ref. Due — payment is locked"; return@launch }
            val verified = got.value ?: return@launch
            verifiedDue = verified.due
            summary.text = "Ref. Due ₹${"%,.2f".format(verified.due)}"
            summary.setTextColor(android.graphics.Color.parseColor("#B42318"))
            amount.isEnabled = true; mode.isEnabled = true; reference.isEnabled = true
        }
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("History", "#1F6FE0").apply {
            setOnClickListener { parts.dialog.dismiss(); showRmpAdvancePayments(item) }
        })
        parts.actionRow.addView(pillButton("Save", "#0C9E33").apply {
            setOnClickListener {
                val pay = amount.text.toString().toDoubleOrNull() ?: 0.0
                val due = verifiedDue
                if (due == null || pay <= 0) { Toast.makeText(this@DoctorVisitActivity, "Valid amount and verified Due required", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                fun saveNow() {
                    isEnabled = false
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { RmpCommissionRepository.recordAdvance(item.id, pay, paidOn, mode.selectedItem.toString(), reference.text.toString().trim()) }
                        isEnabled = true
                        if (result.ok) {
                            parts.dialog.dismiss(); Toast.makeText(this@DoctorVisitActivity, "RMP payment saved and verified", Toast.LENGTH_LONG).show()
                            onSaved?.invoke()
                            showRmpAdvancePayments(item)
                        } else Toast.makeText(this@DoctorVisitActivity, result.message.ifBlank { "Payment failed — nothing changed" }, Toast.LENGTH_LONG).show()
                    }
                }
                if (pay > due) {
                    // 🟢🔒 V398: বাড়তি টাকা শুধু Master অনুমোদন করতে পারেন — Staff/Doctor
                    //   পারেন না। ⛔ ডেটাবেসেও একই নিয়ম, তাই এখানে আটকানোটা শুধু
                    //   আগেই সহজ ভাষায় জানানোর জন্য।
                    if (ModuleAuth.isMaster) AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Payment is higher than Ref. Due"))
                        .setMessage("Ref. Due: ₹${"%,.2f".format(due)}\nPayment: ₹${"%,.2f".format(pay)}\nMore: ₹${"%,.2f".format(pay - due)}")
                        .setNegativeButton("Cancel", null).setPositiveButton("Master Approve & Save") { _, _ -> saveNow() }
                        .show().also { PremiumAlert.paint(it) }
                    else AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Payment is higher than Ref. Due"))
                        .setMessage("Ref. Due: ₹${"%,.2f".format(due)}\nPayment: ₹${"%,.2f".format(pay)}\nMore: ₹${"%,.2f".format(pay - due)}\n\nএই বাড়তি টাকা শুধু Master অনুমোদন করতে পারেন।")
                        .setPositiveButton("Close", null)
                        .show().also { PremiumAlert.paint(it) }
                }
                else saveNow()
            }
        })
        parts.dialog.show()
    }

    private fun showRmpApprovalRequests(returnItem: DoctorVisitItem) {
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.pendingRequests() }
            if (!got.ok) {
                Toast.makeText(this@DoctorVisitActivity, got.message, Toast.LENGTH_LONG).show(); return@launch
            }
            val requests = got.value ?: emptyList()
            if (requests.isEmpty()) {
                Toast.makeText(this@DoctorVisitActivity, "No pending commission approval", Toast.LENGTH_SHORT).show(); return@launch
            }
            val labels = requests.map { r ->
                val amount = r.payload.optDouble("amount", 0.0)
                val date = r.payload.optString("paid_on", "")
                when (r.type) {
                    "BACKDATE_PAYMENT" -> "Backdate Payment · ₹${"%,.2f".format(amount)} · ${FollowUpModel.displayDate(date)} · ${r.requestedBy}"
                    "PAYMENT_EDIT" -> "Payment Correction · ₹${"%,.2f".format(amount)} · ${FollowUpModel.displayDate(date)} · ${r.requestedBy}"
                    "PAST_COMMISSION_CHANGE" -> {
                        val mode = if (r.payload.optString("mode") == "PERCENT") "Percent" else "Fixed Amount"
                        "$mode Change · ${r.payload.optDouble("value", 0.0)} · ${r.requestedBy}"
                    }
                    "RMP_REASSIGNMENT" -> "Patient RMP Change · ${r.requestedBy}"
                    else -> "Commission Approval · ${r.requestedBy}"
                }
            }.toTypedArray()
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Pending Commission Approvals"))
                .setItems(labels) { parent, which ->
                    parent.dismiss()
                    val request = requests[which]
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Commission Approval"))
                        .setMessage(labels[which] + if (request.reason.isBlank()) "" else "\nReason: ${request.reason}")
                        .setNegativeButton("Reject") { _, _ -> decideRmpRequest(request.id, false, returnItem) }
                        .setNeutralButton("Back", null)
                        .setPositiveButton("Approve") { _, _ -> decideRmpRequest(request.id, true, returnItem) }
                        .show().also { PremiumAlert.paint(it) }
                }
                .setNegativeButton("Close", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    private fun decideRmpRequest(requestId: String, approve: Boolean, returnItem: DoctorVisitItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RmpCommissionRepository.decideRequest(requestId, approve) }
            Toast.makeText(this@DoctorVisitActivity,
                if (result.ok) (if (approve) "Approved" else "Rejected")
                else result.message.ifBlank { "Could not update request" }, Toast.LENGTH_LONG).show()
            if (result.ok) showRmpApprovalRequests(returnItem)
        }
    }

    private fun showRmpDefaultCommission(item: DoctorVisitItem) {
        val parts = premiumDialogShell("⚙", "RMP Default Commission — ${item.name}")
        val body = parts.body
        body.addView(fieldLabel("📊", "Commission Type", 0))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Percent (%)", "Fixed Amount (₹)"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        body.addView(mode)
        body.addView(fieldLabel("💵", "Default Value"))
        val value = EditText(this).apply {
            hint = "Enter percent or amount"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(value); body.addView(value)
        val info = TextView(this).apply {
            text = "Loading current Default…"; textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B82"))
        }
        body.addView(info)
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.getDefault(item.id) }
            if (!got.ok) info.text = "Could not load Default — nothing has been changed"
            else if (got.value == null) info.text = "No Default set yet"
            else {
                mode.setSelection(if (got.value.mode == RmpCommissionModel.Mode.AMOUNT) 1 else 0)
                value.setText("${got.value.value}")
                info.text = "Current Default loaded"
            }
        }
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("Save Default", "#0C9E33").apply {
            setOnClickListener {
                val amount = value.text.toString().toDoubleOrNull()
                if (amount == null || amount < 0 || (mode.selectedItemPosition == 0 && amount > 100)) {
                    Toast.makeText(this@DoctorVisitActivity, "Enter a valid value", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        RmpCommissionRepository.setDefault(item.id, item.name, item.mobile,
                            if (mode.selectedItemPosition == 0) RmpCommissionModel.Mode.PERCENT else RmpCommissionModel.Mode.AMOUNT,
                            amount)
                    }
                    isEnabled = true
                    if (result.ok) { parts.dialog.dismiss(); Toast.makeText(this@DoctorVisitActivity, "Default commission saved", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(this@DoctorVisitActivity, result.message.ifBlank { "Save failed — nothing changed" }, Toast.LENGTH_LONG).show()
                }
            }
        })

        // =====================================================================
        // 🔴🔒 V470 (20.08.2026, TK-অনুমোদিত — একই RMP বিভিন্ন ব্রাঞ্চে আলাদা
        // % পেতে পারেন): উপরের বৈশ্বিক Default-এর একদম পাশাপাশি, এখানে
        // ঐচ্ছিকভাবে **শুধু একটা নির্দিষ্ট ব্রাঞ্চের জন্য** আলাদা %
        // বসানো যায়। ⛔ উপরের বৈশ্বিক ঘর/বোতাম এক অক্ষরও বদলানো হয়নি —
        // এটা সম্পূর্ণ নতুন, বাড়তি অংশ।
        // =====================================================================
        val branchDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * resources.displayMetrics.density).toInt())
            setBackgroundColor(android.graphics.Color.parseColor("#E5E8EC"))
        }
        body.addView(branchDivider)
        body.addView(fieldLabel("🏥", "Branch-specific % (optional)", 0))
        val myBranch = if (ModuleAuth.isMaster) (activeDoctorBranch()?.ifBlank { item.branch } ?: item.branch) else user.branch
        val branchModeInfo = TextView(this).apply {
            text = "$myBranch — loading…"; textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B82"))
        }
        body.addView(branchModeInfo)
        val branchMode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Percent (%)", "Fixed Amount (₹)"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        body.addView(branchMode)
        val branchValue = EditText(this).apply {
            hint = "Leave blank to use the Default above for $myBranch"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(branchValue); body.addView(branchValue)
        lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { RmpCommissionRepository.getBranchDefault(item.id, myBranch) }
            if (!got.ok) branchModeInfo.text = "$myBranch — could not load"
            else if (got.value == null) branchModeInfo.text = "$myBranch — no Default set at all yet"
            else if (got.value.isBranchSpecific) {
                branchMode.setSelection(if (got.value.mode == RmpCommissionModel.Mode.AMOUNT) 1 else 0)
                branchValue.setText("${got.value.value}")
                branchModeInfo.text = "$myBranch — using its OWN % (below)"
            } else {
                branchModeInfo.text = "$myBranch — currently using the general Default above (${got.value.value})"
            }
        }
        parts.actionRow.addView(pillButton("Save for $myBranch only", "#C98A1E").apply {
            setOnClickListener {
                val amt = branchValue.text.toString().toDoubleOrNull()
                if (amt == null || amt < 0 || (branchMode.selectedItemPosition == 0 && amt > 100)) {
                    Toast.makeText(this@DoctorVisitActivity, "Enter a valid value for $myBranch", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        RmpCommissionRepository.setBranchDefault(item.id, item.name, item.mobile, myBranch,
                            if (branchMode.selectedItemPosition == 0) RmpCommissionModel.Mode.PERCENT else RmpCommissionModel.Mode.AMOUNT,
                            amt)
                    }
                    isEnabled = true
                    if (result.ok) { parts.dialog.dismiss(); Toast.makeText(this@DoctorVisitActivity, "$myBranch's own commission % saved", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(this@DoctorVisitActivity, result.message.ifBlank { "Save failed — nothing changed" }, Toast.LENGTH_LONG).show()
                }
            }
        })
        parts.dialog.show()
    }

    private fun showPatientCommission(item: DoctorVisitItem) {
        val parts = premiumDialogShell("💰", "Patient Commission — ${item.name}")
        val body = parts.body
        body.addView(fieldLabel("📱", "Patient Mobile *", 0))
        val mobile = EditText(this).apply { hint = "10-digit mobile"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        styleInput(mobile); body.addView(mobile)
        val patientStatus = TextView(this).apply { text = "Enter the patient's mobile"; textSize = 13f }
        body.addView(patientStatus)
        body.addView(fieldLabel("📊", "Commission Type"))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Use RMP Default", "Percent (%)", "Fixed Amount (₹)"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        body.addView(mode)
        body.addView(fieldLabel("💵", "Patient-specific Value (leave blank for Default)"))
        val value = EditText(this).apply {
            hint = "Required only when changing Default"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(value); body.addView(value)
        var patientRef: PatientPhotoRepository.PatientRef? = null
        var token = 0
        mobile.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                patientRef = null
                val digits = s?.toString()?.filter { it.isDigit() }?.takeLast(10) ?: ""
                if (digits.length != 10) { patientStatus.text = "Enter the patient's mobile"; return }
                val mine = ++token; patientStatus.text = "Checking patient…"
                lifecycleScope.launch {
                    val found = findVisitPatient(digits)   // 🔵 V530
                    if (mine != token) return@launch
                    patientRef = found
                    patientStatus.text = if (found == null) "No patient found" else "✓ ${found.name}"
                    patientStatus.setTextColor(android.graphics.Color.parseColor(if (found == null) "#C0392B" else "#0C9E33"))
                }
            }
        })
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("Pay", "#C98A1E").apply {
            setOnClickListener {
                val p = patientRef ?: run { Toast.makeText(this@DoctorVisitActivity, "Select a valid patient", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                parts.dialog.dismiss()
                showCommissionPayment(p, item.id)
            }
        })
        parts.actionRow.addView(pillButton("Save Commission", "#0C9E33").apply {
            setOnClickListener {
                val p = patientRef ?: run { Toast.makeText(this@DoctorVisitActivity, "Select a valid patient", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                val customMode = when (mode.selectedItemPosition) {
                    1 -> RmpCommissionModel.Mode.PERCENT
                    2 -> RmpCommissionModel.Mode.AMOUNT
                    else -> null
                }
                val customValue = if (customMode == null) null else value.text.toString().toDoubleOrNull()
                if (customMode != null && (customValue == null || customValue < 0 || (customMode == RmpCommissionModel.Mode.PERCENT && customValue > 100))) {
                    Toast.makeText(this@DoctorVisitActivity, "Enter a valid commission value", Toast.LENGTH_SHORT).show(); return@setOnClickListener
                }
                isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val existing = RmpCommissionRepository.getPatientCommission(p.id)
                        if (!existing.ok) Pair(false, existing.message)
                        else if (existing.value != null && existing.value.rmpId != item.id) {
                            if (ModuleAuth.isMaster) {
                                val action = RmpCommissionRepository.reassignPatient(p.id, item.id)
                                Pair(action.ok, action.message)
                            } else {
                                val action = RmpCommissionRepository.requestReassignment(p.id, existing.value.rmpId, item.id, "")
                                Pair(action.ok, action.message)
                            }
                        } else if (existing.value != null && existing.value.setOn < DoctorVisitModel.today() && !ModuleAuth.isMaster) {
                            val desired = if (customMode != null && customValue != null) {
                                Pair(customMode, customValue)
                            } else {
                                // 🔴🔒 V470 (20.08.2026) — সততার সাথে সীমা: এই নির্দিষ্ট
                                // পথে (পুরনো তারিখ পরিবর্তনের অনুরোধ) রোগীর আসল ব্রাঞ্চ
                                // এখানে সহজে পাওয়া যায় না (`PatientRef`-এ branch ফিল্ড
                                // নেই), তাই এখনো বৈশ্বিক Default-ই ব্যবহার হচ্ছে — এটা
                                // পুরনো, প্রমাণিত আচরণ, রিগ্রেশন নয়। সাধারণ (বেশিরভাগ)
                                // সেভ-পথ ইতিমধ্যেই সার্ভার-স্তরে ব্রাঞ্চ-সচেতন (নিচে দেখুন)।
                                val default = RmpCommissionRepository.getDefault(item.id)
                                if (!default.ok || default.value == null) return@withContext Pair(false, "RMP Default is not set")
                                Pair(default.value.mode, default.value.value)
                            }
                            val action = RmpCommissionRepository.requestPastCommissionChange(
                                p.id, item.id, desired.first, desired.second, existing.value.mode,
                                existing.value.value, existing.value.setOn, "")
                            Pair(action.ok, action.message)
                        } else {
                            val action = RmpCommissionRepository.setPatientCommission(p.id, item.id, customMode, customValue)
                            Pair(action.ok, action.message)
                        }
                    }
                    isEnabled = true
                    if (result.first) {
                        parts.dialog.dismiss()
                        Toast.makeText(this@DoctorVisitActivity,
                            if (!ModuleAuth.isMaster) "Patient commission saved / change request sent when Master approval is required"
                            else "Patient commission saved", Toast.LENGTH_LONG).show()
                    }
                    else Toast.makeText(this@DoctorVisitActivity, result.second.ifBlank { "Save failed — nothing changed" }, Toast.LENGTH_LONG).show()
                }
            }
        })
        parts.dialog.show()
    }

    private fun showCommissionPayment(patient: PatientPhotoRepository.PatientRef, expectedRmpId: String) {
        val parts = premiumDialogShell("₹", "RMP Commission Payment — ${patient.name}")
        val body = parts.body
        val summaryText = TextView(this).apply {
            text = "Loading commission balance…"; textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#145A32"))
        }
        body.addView(summaryText)
        body.addView(fieldLabel("📅", "Payment Date"))
        var paidOn = DoctorVisitModel.today()
        val dateValue = TextView(this).apply {
            text = FollowUpModel.displayDate(paidOn)
            setTextColor(android.graphics.Color.parseColor("#145A32"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@DoctorVisitActivity, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
                    paidOn = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    text = FollowUpModel.displayDate(paidOn)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            }
        }
        body.addView(dateValue)
        body.addView(fieldLabel("💵", "Amount to Pay *"))
        val amount = EditText(this).apply {
            hint = "Commission payment amount"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
            isEnabled = false
        }
        styleInput(amount); body.addView(amount)
        body.addView(fieldLabel("💳", "Payment Mode"))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cash", "Online"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
            isEnabled = false
        }
        body.addView(mode)
        body.addView(fieldLabel("🔢", "Transaction / Reference No. (Optional)"))
        val reference = EditText(this).apply { hint = "Optional"; isEnabled = false }
        styleInput(reference); body.addView(reference)
        var verifiedDue: Double? = null
        lifecycleScope.launch {
            val setting = withContext(Dispatchers.IO) { RmpCommissionRepository.getPatientCommission(patient.id) }
            if (!setting.ok) { summaryText.text = "Could not verify commission — payment is locked"; return@launch }
            if (setting.value == null) { summaryText.text = "Commission is not set for this patient"; return@launch }
            if (setting.value.rmpId != expectedRmpId) {
                summaryText.text = "This patient is assigned to ${setting.value.rmpName}. Change RMP with approval before payment."
                return@launch
            }
            val result = withContext(Dispatchers.IO) { RmpCommissionRepository.summary(patient.id) }
            if (!result.ok || result.value == null) { summaryText.text = "Could not verify balance — payment is locked"; return@launch }
            val s = result.value
            verifiedDue = s.due
            summaryText.text = "Earned ₹${"%,.2f".format(s.earned)}  ·  Paid ₹${"%,.2f".format(s.paid)}  ·  Due ₹${"%,.2f".format(s.due)}" +
                if (s.overpaid > 0) "\nMore paid ₹${"%,.2f".format(s.overpaid)}" else ""
            amount.isEnabled = true; mode.isEnabled = true; reference.isEnabled = true
        }
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("History", "#1F6FE0").apply {
            setOnClickListener { parts.dialog.dismiss(); showCommissionPaymentHistory(patient) }
        })
        parts.actionRow.addView(pillButton("Save Payment", "#0C9E33").apply {
            setOnClickListener {
                val pay = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (verifiedDue == null || pay <= 0) { Toast.makeText(this@DoctorVisitActivity, "Balance is not verified", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (!ModuleAuth.isMaster && pay > (verifiedDue ?: 0.0)) {
                    Toast.makeText(this@DoctorVisitActivity, "Only Master can pay more than Due amount", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                fun saveAfterApproval() {
                    isEnabled = false
                    lifecycleScope.launch {
                        /* 🔴🆕🔒 V439 (TK-রিপোর্ট ১৮.০৮.২০২৬ — *"অনুমতি দিয়েছি তবুও হয় না"*):
                           Master-এর দেওয়া ব্যাকডেট-অনুমতি (`BackdatePaymentGrant`) এতদিন
                           **শুধু Payment পর্দায়** কাজ করত। RMP কমিশনের পুরনো তারিখের
                           পেমেন্টে ওটা দেখাই হত না — তাই অনুমতি থাকা সত্ত্বেও প্রতিবার
                           "Request sent to Master" আসত। এখন এখানেও দেখা হয়।
                           ⛔ অনুমতি না থাকলে আচরণ হুবহু আগের মতোই (অনুরোধ যাবে)। */
                        val needsApproval = withContext(Dispatchers.IO) {
                            if (paidOn == DoctorVisitModel.today() || ModuleAuth.isMaster) false
                            else !(try { BackdatePaymentGrant.isGrantedNow(user.mobile, paidOn) } catch (_: Throwable) { false })
                        }
                        val result = withContext(Dispatchers.IO) {
                            if (needsApproval)
                                RmpCommissionRepository.requestBackdatePayment(patient.id, pay, paidOn,
                                    mode.selectedItem.toString(), reference.text.toString().trim(), "")
                            else RmpCommissionRepository.recordPayment(patient.id, pay, paidOn,
                                mode.selectedItem.toString(), reference.text.toString().trim())
                        }
                        isEnabled = true
                        if (result.ok) {
                            parts.dialog.dismiss()
                            Toast.makeText(this@DoctorVisitActivity,
                                if (needsApproval)
                                    "Request sent to Master — payment is not added until approval"
                                else "Commission payment saved and verified", Toast.LENGTH_LONG).show()
                            if (!needsApproval)
                                showCommissionPaymentHistory(patient)
                        }
                        else Toast.makeText(this@DoctorVisitActivity, result.message.ifBlank { "Payment failed — no record was changed" }, Toast.LENGTH_LONG).show()
                    }
                }
                if (ModuleAuth.isMaster && pay > (verifiedDue ?: 0.0)) {
                    AlertDialog.Builder(this@DoctorVisitActivity)
                        .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Payment is higher than Ref. Due"))
                        .setMessage("Ref. Due: ₹${"%,.2f".format(verifiedDue ?: 0.0)}\nPayment: ₹${"%,.2f".format(pay)}\nMore: ₹${"%,.2f".format(pay - (verifiedDue ?: 0.0))}\n\nSave only if this extra amount is intentionally approved by Master.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Master Approve & Save") { _, _ -> saveAfterApproval() }
                        .show().also { PremiumAlert.paint(it) }
                } else saveAfterApproval()
            }
        })
        parts.dialog.show()
    }

    private fun showCommissionPaymentHistory(patient: PatientPhotoRepository.PatientRef) {
        lifecycleScope.launch {
            val setting = withContext(Dispatchers.IO) { RmpCommissionRepository.getPatientCommission(patient.id) }
            if (!setting.ok || setting.value == null) {
                Toast.makeText(this@DoctorVisitActivity, "Could not load commission setting", Toast.LENGTH_LONG).show(); return@launch
            }
            val history = withContext(Dispatchers.IO) { RmpCommissionRepository.paymentHistory(setting.value.id) }
            if (!history.ok) { Toast.makeText(this@DoctorVisitActivity, history.message, Toast.LENGTH_LONG).show(); return@launch }
            val rows = history.value ?: emptyList()
            val labels = rows.map { p ->
                "${p.rmpName} · ${FollowUpModel.displayDate(p.paidOn)} · ₹${"%,.2f".format(p.amount)} · ${p.mode}" +
                    if (ModuleAuth.isMaster && p.hiddenFromNonMaster) " · Master Private" else ""
            }.toTypedArray()
            AlertDialog.Builder(this@DoctorVisitActivity)
                .setCustomTitle(PremiumAlert.header(this@DoctorVisitActivity, "Commission Payment History — ${patient.name}"))
                .setItems(if (labels.isEmpty()) arrayOf("No commission payment yet") else labels) { _, which ->
                    if (rows.isNotEmpty()) {
                        val payment = rows[which]
                        if (!ModuleAuth.isMaster && payment.paidOn != DoctorVisitModel.today())
                            Toast.makeText(this@DoctorVisitActivity, "Only Master can edit or delete an earlier payment", Toast.LENGTH_LONG).show()
                        else showCommissionPaymentEdit(patient, payment)
                    }
                }
                .setNegativeButton("Close", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    private fun showCommissionPaymentEdit(patient: PatientPhotoRepository.PatientRef, payment: RmpCommissionRepository.CommissionPayment) {
        val parts = premiumDialogShell("✎", "Edit Commission Payment")
        val body = parts.body
        body.addView(fieldLabel("💵", "Amount", 0))
        val amount = EditText(this).apply {
            setText(payment.amount.toString()); inputType = android.text.InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(amount); body.addView(amount)
        body.addView(fieldLabel("💳", "Payment Mode"))
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cash", "Online"))
            setSelection(if (payment.mode.equals("ONLINE", true)) 1 else 0)
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        body.addView(mode)
        body.addView(fieldLabel("🔢", "Transaction / Reference No. (Optional)"))
        val reference = EditText(this).apply { setText(payment.referenceNo) }
        styleInput(reference); body.addView(reference)
        var privateChange = payment.hiddenFromNonMaster
        if (ModuleAuth.isMaster) {
            val privateBox = android.widget.CheckBox(this).apply {
                text = "Master Private Change (history visible only to Master)"
                isChecked = payment.hiddenFromNonMaster
                setOnCheckedChangeListener { _, checked -> privateChange = checked }
            }
            body.addView(privateBox)
        }
        body.addView(fieldLabel("📝", "Reason (Optional)"))
        val reason = EditText(this).apply { hint = "Optional" }
        styleInput(reason); body.addView(reason)
        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("Delete", "#FDECEC", android.graphics.Color.parseColor("#B42318")).apply {
            setOnClickListener {
                if (!ModuleAuth.isMaster && payment.paidOn != DoctorVisitModel.today()) {
                    Toast.makeText(this@DoctorVisitActivity, "Only Master can delete an earlier payment", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@DoctorVisitActivity)
                    .setTitle("Delete Commission Payment?")
                    .setMessage("This will also remove its linked Expense. The audit history will remain.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            val action = withContext(Dispatchers.IO) {
                                RmpCommissionRepository.deletePayment(payment.id, reason.text.toString().trim())
                            }
                            if (action.ok) {
                                parts.dialog.dismiss()
                                Toast.makeText(this@DoctorVisitActivity, "Commission Payment deleted and balance adjusted", Toast.LENGTH_LONG).show()
                                showCommissionPaymentHistory(patient)
                            } else Toast.makeText(this@DoctorVisitActivity, action.message.ifBlank { "Delete failed — nothing changed" }, Toast.LENGTH_LONG).show()
                        }
                    }.show().also { PremiumAlert.paint(it) }
            }
        })
        parts.actionRow.addView(pillButton("Save", "#0C9E33").apply {
            setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull() ?: 0.0
                if (value <= 0) { Toast.makeText(this@DoctorVisitActivity, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val action = RmpCommissionRepository.editPayment(payment.id, value, payment.paidOn,
                            mode.selectedItem.toString(), reference.text.toString().trim(), privateChange,
                            reason.text.toString().trim())
                        Pair(action.ok, action.message)
                    }
                    isEnabled = true
                    if (result.first) {
                        parts.dialog.dismiss()
                        Toast.makeText(this@DoctorVisitActivity,
                            "Commission and Expense updated together", Toast.LENGTH_LONG).show()
                        showCommissionPaymentHistory(patient)
                    }
                    else Toast.makeText(this@DoctorVisitActivity, result.second.ifBlank { "Update failed — nothing changed" }, Toast.LENGTH_LONG).show()
                }
            }
        })
        parts.dialog.show()
    }

    /** WebView parity (saveReferralIncome): records a referral commission entry
     *  for this doctor — patient, amount, Paid/Unpaid — into referralPayments and
     *  recomputes referralPaid / referralDue. */
    private fun showLegacyReferralIncome(item: DoctorVisitItem) {
        if (item.id.isBlank()) return
        val parts = premiumDialogShell("💰", "Add Referral Income — ${item.name}")
        val container = parts.body

        container.addView(fieldLabel("📱", "Patient Mobile *", 0))
        val mobileInput = android.widget.EditText(this).apply {
            hint = "10-digit mobile"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        styleInput(mobileInput)
        container.addView(mobileInput)

        // TK-REQUESTED (2026-07-18): this used to be a free-typed "Patient
        // name" field. TK's exact concern: many patients can share the same
        // name, so a typed name can't reliably be matched back to the real
        // patient record. Now the staff enters the MOBILE number instead;
        // Save looks that mobile up in the real "patients" (Registration)
        // table and uses the confirmed name from there — never a
        // hand-typed one — so the referral entry is genuinely tied to one
        // specific, real patient.

        // TK-REQUESTED FOLLOW-UP (2026-07-19): show it as a proper "Patient
        // Name" field (same style as Mobile/Amount/Status below), not just a
        // small caption line -- still auto-filled from the mobile match
        // (never hand-typed, keeping the 2026-07-18 same-name-collision
        // safety), just clearly visible as its own field now.
        container.addView(fieldLabel("🧑", "Patient Name"))
        val nameField = android.widget.EditText(this).apply {
            setText("Enter mobile number above")
            isFocusable = false
            isClickable = false
            isCursorVisible = false
        }
        styleInput(nameField)
        container.addView(nameField)
        var matchToken = 0
        mobileInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val digits = s?.toString()?.filter { it.isDigit() }?.takeLast(10) ?: ""
                if (digits.length != 10) {
                    nameField.setText("Enter mobile number above")
                    nameField.setTextColor(android.graphics.Color.parseColor("#7A8699"))
                    return
                }
                val myToken = ++matchToken
                nameField.setText("Checking…")
                nameField.setTextColor(android.graphics.Color.parseColor("#7A8699"))
                lifecycleScope.launch {
                    val ref = findVisitPatient(digits)   // 🔵 V530
                    if (myToken != matchToken) return@launch // mobile field changed again while this was loading
                    if (ref == null) {
                        nameField.setText("✗ No patient found with this mobile")
                        nameField.setTextColor(android.graphics.Color.parseColor("#E5484D"))
                    } else {
                        nameField.setText("✓ ${ref.name.ifBlank { "Unnamed patient" }}")
                        nameField.setTextColor(android.graphics.Color.parseColor("#0C9E33"))
                    }
                }
            }
        })

        container.addView(fieldLabel("💵", "Referral Amount *"))
        val amountInput = android.widget.EditText(this).apply {
            hint = "Referral amount ₹"
            inputType = android.text.InputType.TYPE_CLASS_TEXT; keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        }
        styleInput(amountInput)
        container.addView(amountInput)

        container.addView(fieldLabel("📊", "Status"))
        val statusSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Select Status", "Due", "Paid"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        container.addView(statusSpinner)

        // TK-APPROVED ADDITION (31.07.2026): two new OPTIONAL fields so RMP
        // Message 4 (Referral Payment Confirmation) can show the real
        // Payment Mode / Reference No. instead of a blank line. Purely
        // additive — every existing field/behaviour above is untouched.
        container.addView(fieldLabel("💳", "Payment Mode"))
        val modeSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@DoctorVisitActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Cash", "Online"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_input_field)
        }
        container.addView(modeSpinner)

        container.addView(fieldLabel("🔢", "Transaction / Reference No.  (Online only)"))
        val refInput = android.widget.EditText(this).apply {
            hint = "Reference / Transaction number"
        }
        styleInput(refInput)
        container.addView(refInput)

        parts.actionRow.addView(pillButton("Cancel", "#E5E8EC", android.graphics.Color.parseColor("#145A32")).apply {
            setOnClickListener { parts.dialog.dismiss() }
        })
        parts.actionRow.addView(pillButton("💾 Save", "#0C9E33").apply {
            setOnClickListener {
                val mobileDigits = mobileInput.text.toString().filter { it.isDigit() }.takeLast(10)
                val amt = amountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                if (mobileDigits.length != 10 || amt <= 0) {
                    Toast.makeText(this@DoctorVisitActivity, "Valid 10-digit mobile and referral amount required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (statusSpinner.selectedItemPosition == 0) {
                    Toast.makeText(this@DoctorVisitActivity, "Select Paid or Due", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val st = if (statusSpinner.selectedItemPosition == 2) "Paid" else "Unpaid"
                val payMode = modeSpinner.selectedItem.toString()
                val refNo = refInput.text.toString().trim()
                lifecycleScope.launch {
                    val patientRef = findVisitPatient(mobileDigits)   // 🔵 V530
                    if (patientRef == null) {
                        Toast.makeText(this@DoctorVisitActivity, "No patient found with this mobile — check the number", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val ok = withContext(Dispatchers.IO) { DoctorVisitRepository().addReferralEntry(item.id, patientRef.name.ifBlank { mobileDigits }, mobileDigits, amt, st, applicationContext, payMode, refNo) }
                    // TK-REPORTED FIX (2026-07-23): also link this patient's
                    // own record back to this doctor (if not already linked
                    // to someone) so "Referred Patients" count matches the
                    // referral income just saved. Best-effort -- if this
                    // part fails the income entry itself is still saved.
                    if (ok) {
                        withContext(Dispatchers.IO) {
                            DoctorVisitRepository().linkReferringDoctorIfBlank(patientRef.id, item.name, item.mobile, applicationContext)
                        }
                    }
                    Toast.makeText(this@DoctorVisitActivity, if (ok) "Referral income saved — ${patientRef.name}" else "Failed — check connection", Toast.LENGTH_SHORT).show()
                    if (ok) parts.dialog.dismiss()
                }
            }
        })
        parts.dialog.show()
    }

    private data class Quad(val a: String, val b: Double, val c: Double, val d: Int)
}
