package com.tkbiswas.pilesclinic.native

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Native rebuild of the WebView global search (searchResults()): searches both
 * enquiries and patients by name (contains) or mobile (last-10 match), with the
 * same visibility rule (master/staff see all; doctor limited to own branch).
 * Tapping a result opens that person's timeline.
 */
class GlobalSearchActivity : AppCompatActivity() {

    private lateinit var progressLoad: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private val results = mutableListOf<SearchHit>()
    private lateinit var adapter: SearchAdapter
    private var searchJob: Job? = null

    // 🆔 TK-এর নিয়ম (28.07.2026): নাম ও মোবাইলের সঙ্গে Patient ID-ও দেখাতে হবে।
    // এনকোয়ারিতে ID থাকে না, তাই ডিফল্ট ফাঁকা — তখন কিছুই বাড়তি দেখায় না।
    // ⛔ কোনো বাড়তি ক্লাউড-কল হয়নি: patients সারিটা আগে থেকেই নামানো হত।
    /**
     * 🔵🔒 V517 (২২.০৮.২০২৬, TK-অনুমোদিত): `rowId` — এই ফলটা ঠিক **কোন** রোগীর
     * সারি। এক মোবাইলে একাধিক রোগী থাকলে (স্বামী/স্ত্রী) এটা দিয়েই
     * Full Journey সঠিক রোগীরটাই খোলে।
     * ⛔ ডিফল্ট ফাঁকা — Enquiry-র ফলে সারি-আইডি লাগে না, আচরণ আগের মতোই।
     */
    /* 🔵🔒 V538 (২২.০৮.২০২৬, TK-নির্দেশ): এই খোঁজা এমনিতেই `disease` ঘরটা
       আনে (উপরের `patCloud`/`enqCloud`-এর কলাম তালিকা দেখুন), অথচ কার্ডে
       ধরে রাখা হত না — তাই ক্লিনিক্যাল পর্দায় রোগের নাম ফাঁকা যেত।
       ⛔ **নতুন কোনো ক্লাউড-অনুরোধ নয়** — যে তথ্য আগেই আসছে, সেটাই রাখা হলো।
       ⛔ ডিফল্ট ফাঁকা, তাই পুরোনো কোনো ডাক ভাঙে না। */
    data class SearchHit(val name: String, val mobile: String, val branch: String, val type: String, val patientId: String = "", val rowId: String = "", val disease: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_search)
        UppercaseInputUtil.applyToAll(window.decorView.findViewById(android.R.id.content))  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        BottomNav.wire(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        progressLoad = findViewById(R.id.progressLoad)
        tvEmpty = findViewById(R.id.tvEmpty)
        recycler = findViewById(R.id.recyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = SearchAdapter(
            results,
            onFullJourney = { hit -> openTimeline(hit.mobile, hit.rowId) },
            onCall = { hit -> callHit(hit.mobile) },
            onWhatsApp = { hit -> whatsAppHit(hit.mobile) },
            onPayment = { hit -> openPaymentForHit(hit.mobile, hit.rowId, hit.patientId) },
            onPrescription = { hit -> openClinicalDoc(hit, com.tkbiswas.pilesclinic.clinical.PrescriptionActivity::class.java) },
            onMedicineSlip = { hit -> openClinicalDoc(hit, com.tkbiswas.pilesclinic.clinical.MedicineSlipActivity::class.java) },
            onBloodTest = { hit -> openClinicalDoc(hit, com.tkbiswas.pilesclinic.clinical.InvestigationAdviceActivity::class.java) },
            onDietChart = { hit -> openClinicalDoc(hit, com.tkbiswas.pilesclinic.clinical.DietChartActivity::class.java) },
            onMarkArrived = { hit -> markArrivedHit(hit) }
        )
        recycler.adapter = adapter

        val etQuery = findViewById<EditText>(R.id.etQuery)
        etQuery.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable) {
                val q = s.toString().trim()
                searchJob?.cancel()
                if (q.length < 2) { results.clear(); adapter.notifyDataSetChanged(); tvEmpty.visibility = View.VISIBLE; tvEmpty.text = "Type a name or mobile number to search."; return }
                searchJob = lifecycleScope.launch {
                    delay(250)
                    runSearch(q)
                }
            }
        })
    }

    private fun runSearch(q: String) {
        progressLoad.visibility = View.GONE  // TK-REQUESTED (2026-07-20): spinner must NEVER spin anywhere; cache-first shows old data instantly, content appears when ready.
        tvEmpty.visibility = View.GONE
        val user = NativeSession.current(this)
        // TK FIX (2026-07-15): typing "+919304173739" (with country code) did not
        // match, only the plain 10-digit "9304173739" did. qDigits kept all 12
        // digits (91 + 10), and a 10-digit mobile suffix can never "contain" a
        // 12-digit query. Now trimmed to the last 10 digits like the mobile
        // itself, so both formats match identically.
        val qDigits = q.filter { it.isDigit() }.takeLast(10)
        lifecycleScope.launch {
            val hits = withContext(Dispatchers.IO) {
                // ONE-NUMBER-ONE-SECTION (web formula): a mobile must appear once,
                // in its CURRENT/most-advanced section. We key by last-10 digits and
                // let a Patient row override an Enquiry row for the same number, so
                // the same number never shows twice across sections/branches.
                val byMobile = LinkedHashMap<String, SearchHit>()
                // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): every
                // search used to drag down EVERY column of up to 2,000 enquiries
                // and 2,000 patients -- doctor's notes, medical history, the
                // full complaint text -- to look at a handful of fields.
                // ⛔ NOTHING ABOUT THE SEARCH CHANGES: same tables, same rows,
                // same limit, same order, and every matching rule below is left
                // word for word. Only columns this screen never reads are no
                // longer sent. The lists are exactly what the code below uses:
                //   enquiries -> id, name, mobile, branch, disease, address, date
                //   patients  -> the same, plus patientId, diagnosis,
                //                registrationDate and bill (bill is what
                //                PatientIdentity.pickPatientRow needs)
                // (the enquiries table has no patientId column at all, so that
                // field was always blank here and is not asked for)
                // 🔒 And if a narrowed read ever fails, fetchListSlim asks for
                // every column again by itself -- so a search can never come
                // back wrongly empty because of this.
                val enqCloud = SupabaseClient.fetchListSlim(
                    "enquiries", null, 2000,
                    "id,name,mobile,branch,disease,address,date,updatedAt"
                )
                val patCloud = SupabaseClient.fetchListSlim(
                    "patients", null, 2000,
                    // 🔒 V235: altMobile যোগ — Alternate নম্বর দিয়েও Search মেলে।
                    "id,name,mobile,altMobile,branch,bill,patientId,disease,diagnosis,address,registrationDate,date,updatedAt"
                )
                // TK-REQUESTED BUG FIX (2026-07-16): same fix as Follow-up/
                // Doctor Queue/Today's Collection -- a just-created enquiry
                // or just-registered patient could be briefly missing from
                // search results because this always read straight from the
                // cloud, with no awareness of a save still syncing in the
                // background. Any locally-pending row not yet in the cloud
                // result is merged in too; matching/scoring logic below is
                // completely unchanged.
                val enq = org.json.JSONArray()
                for (i in 0 until enqCloud.length()) enq.put(enqCloud.getJSONObject(i))
                val pat = org.json.JSONArray()
                for (i in 0 until patCloud.length()) pat.put(patCloud.getJSONObject(i))
                run {
                    val pendingEnq = LocalWorkflowStore(this@GlobalSearchActivity).pendingEnquiries()
                    val seenEnqIds = HashSet<String>()
                    for (i in 0 until enq.length()) seenEnqIds.add(enq.getJSONObject(i).optString("id"))
                    for (i in 0 until pendingEnq.length()) {
                        val row = pendingEnq.getJSONObject(i)
                        val id = row.optString("id")
                        if (id.isNotBlank() && seenEnqIds.add(id)) enq.put(row)
                    }
                    val pendingPat = LocalWorkflowStore(this@GlobalSearchActivity).pendingPatients()
                    val seenPatIds = HashSet<String>()
                    for (i in 0 until pat.length()) seenPatIds.add(pat.getJSONObject(i).optString("id"))
                    for (i in 0 until pendingPat.length()) {
                        val row = pendingPat.getJSONObject(i)
                        val id = row.optString("id")
                        if (id.isNotBlank() && seenPatIds.add(id)) pat.put(row)
                    }
                }
                // TK APPROVED (2026-07-15): Dashboard/Global Search by mobile number
                // shows the same way across ALL branches for every role (Master,
                // Doctor, Staff alike) — this is the one deliberate "see everything"
                // lookup point in the app; branch-scoping only applies to the
                // Follow-up tabs and Payment editing, not here.
                fun canSee(branch: String): Boolean {
                    if (user == null) return false
                    return true
                }
                // TK-REQUESTED ADDITION (2026-07-24): search now also
                // matches Disease, Address, Patient ID, and the record's
                // own date (Enquiry date for an Enquiry row, Registration
                // date for a Patient row) -- was name/mobile only before.
                // Same safe "contains" matching pattern as name, just
                // extended to more fields already present in the same
                // fetched row.
                fun match(name: String, mobile: String, disease: String, address: String, patientId: String, dateText: String, altMobile: String = ""): Boolean {
                    val nameHit = q.length >= 2 && name.contains(q, ignoreCase = true)
                    val mobHit = qDigits.length >= 3 && mobile.filter { it.isDigit() }.takeLast(10).contains(qDigits)
                    // 🔒 V235: Alternate নম্বরেও মেলে (additive — আগের মিল অপরিবর্তিত)।
                    val altMobHit = qDigits.length >= 3 && altMobile.filter { it.isDigit() }.takeLast(10).let { it.isNotBlank() && it.contains(qDigits) }
                    val diseaseHit = q.length >= 2 && disease.contains(q, ignoreCase = true)
                    val addressHit = q.length >= 2 && address.contains(q, ignoreCase = true)
                    val patientIdHit = q.length >= 2 && patientId.contains(q, ignoreCase = true)
                    val dateHit = q.length >= 2 && dateText.contains(q, ignoreCase = true)
                    return nameHit || mobHit || altMobHit || diseaseHit || addressHit || patientIdHit || dateHit
                }
                fun key(mobile: String) = mobile.filter { it.isDigit() }.takeLast(10)
                // Enquiries first (lowest stage) …
                for (i in 0 until enq.length()) {
                    val r = enq.getJSONObject(i)
                    val br = r.s("branch")
                    if (!canSee(br)) continue
                    // 🟢🔒 B633 (11.08.2026, TK-রিপোর্ট ছবিসহ: "ডেমো নম্বর ডিলিটের পরেও Search-এ
                    //   থেকে যায়")। আসল কারণ: Search এতদিন ট্র্যাশ/মুছে-ফেলার চিহ্ন **একটুও মেলাত না**
                    //   (grep=0) — তাই ডিলিট-করা (tombstoned/hidden) রেকর্ডও দেখাত, যদিও Follow-up/
                    //   Reject তালিকায় ঠিকই লুকাত। এখন বাকি পর্দার **হুবহু একই নিয়ম**: DeletedGuard
                    //   (cloud-synced, তাই সব ফোনে একই) বা GhostHide-এ মুছে-ফেলা হলে Search-ও লুকায়।
                    //   ⛔ শুধু ফিল্টার — কোনো রেকর্ড মোছে না, কোনো ডিজাইন/ফ্লো বদলায় না। Restore করলে
                    //   DeletedGuard unmark হয়ে আবার দেখাবে (আগের মতোই)।
                    if (DeletedGuard.isDeleted("enquiries", r.s("id"), this@GlobalSearchActivity) ||
                        DraftRepository.GhostHide.isHidden(r.s("id"), r.s("mobile"))) continue
                    if (!match(r.s("name"), r.s("mobile"), r.s("disease"), r.s("address"), r.s("patientId"), r.s("date"))) continue
                    val k = key(r.s("mobile"))
                    if (k.isNotBlank() && !byMobile.containsKey(k))
                        byMobile[k] = SearchHit(r.s("name"), r.s("mobile"), br, "Enquiry", disease = r.s("disease"))
                }
                // … then Patients override the same number (higher stage wins).
                // TK-REQUESTED (2026-07-27), "ছ'টা পর্দা এক নিয়মে" step 1 of 6:
                // when the same person has TWO patients rows (a duplicate
                // registration), this loop simply let whichever row the cloud
                // happened to return LAST win -- so the name/branch shown in
                // Search could be the abandoned duplicate, while the payment
                // screen, Patient Details and the Report Card all showed the
                // real one. Those three were already put on one rule in V143
                // (PatientIdentity.pickPatientRow); Search was still on its own.
                // Now it uses that same rule: current branch -> the row that
                // carries a real bill -> the first row. With only one row (the
                // normal case) nothing about this screen changes at all.
                val patRowsByMobile = LinkedHashMap<String, org.json.JSONArray>()
                for (i in 0 until pat.length()) {
                    val r = pat.getJSONObject(i)
                    val br = r.s("branch")
                    if (!canSee(br)) continue
                    // 🟢🔒 B633: patients-এও একই ট্র্যাশ/মুছে-ফেলা ফিল্টার (উপরের enquiries-এর মতোই)।
                    if (DeletedGuard.isDeleted("patients", r.s("id"), this@GlobalSearchActivity) ||
                        DraftRepository.GhostHide.isHidden(r.s("id"), r.s("mobile"))) continue
                    if (!match(r.s("name"), r.s("mobile"), r.s("disease").ifBlank { r.s("diagnosis") }, r.s("address"), r.s("patientId"), r.s("registrationDate").ifBlank { r.s("date") }, altMobile = r.s("altMobile"))) continue
                    val k = key(r.s("mobile"))
                    if (k.isBlank()) continue
                    patRowsByMobile.getOrPut(k) { org.json.JSONArray() }.put(r)
                }
                /* 🔵🔴🔒 V517 (২২.০৮.২০২৬, TK-অনুমোদিত) — **এক নম্বরে একাধিক রোগী
                   হলে প্রত্যেকে আলাদা ফল।**

                   TK-এর দাবি ৮: *"Search-এ একই mobile লিখলে ওই নম্বরের সঙ্গে যুক্ত
                   সব Patient আলাদা আলাদা card হিসেবে দেখাবে।"*

                   ⛔ কিন্তু উপরের `pickPatientRow` নিয়মটা (V143, "ছ'টা পর্দা এক
                      নিয়মে") একটা **ভালো সুরক্ষা**: ভুল করে একই রোগীর দুটো
                      রেজিস্ট্রেশন হয়ে গেলে শুধু আসলটাই দেখায়, পরিত্যক্ত
                      duplicate-টা লুকায়। সেটা এক অক্ষরও ভাঙা যাবে না।

                   **দুটোকে আলাদা করার প্রমাণিত চিহ্ন:** V516-এ স্টাফ যখন নিজে
                   বেছে *"Different Patient — Same Mobile"* চাপেন, একমাত্র তখনই
                   আইডি হয় `pat_<১০ সংখ্যা>_<...>` ধাঁচের
                   (`PatientModel.newRowIdForSameMobile`)। অন্য কোনো পথে এই ধাঁচ
                   কখনো তৈরি হয় না। তাই —
                     · এই ধাঁচের সারি = **স্টাফের ঘোষিত আলাদা রোগী** ⇒ নিজের card
                     · বাকি সব সারি = আগের মতোই একত্র, `pickPatientRow` বেছে দেয়
                   ⇒ পুরোনো ভুল-duplicate গুলো **আগের মতোই লুকানো** থাকে। */
                fun isDeclaredSeparatePatient(rowId: String, mobileKey: String): Boolean {
                    if (mobileKey.length != 10) return false
                    val prefix = "pat_" + mobileKey + "_"
                    return rowId.startsWith(prefix) && rowId.length > prefix.length
                }
                val extraHits = mutableListOf<SearchHit>()
                for ((k, rows) in patRowsByMobile) {
                    val ordinary = org.json.JSONArray()
                    for (i in 0 until rows.length()) {
                        val r = rows.getJSONObject(i)
                        if (isDeclaredSeparatePatient(r.s("id"), k)) {
                            extraHits.add(
                                SearchHit(r.s("name"), r.s("mobile"), r.s("branch"), "Patient", r.s("patientId"), r.s("id"), r.s("disease"))
                            )
                        } else {
                            ordinary.put(r)
                        }
                    }
                    // পুরোনো পথ — হুবহু আগের মতোই (একটাই সারি থাকলে কিছুই বদলায় না)
                    val chosen = PatientIdentity.pickPatientRow(ordinary, user?.branch ?: "") ?: continue
                    byMobile[k] = SearchHit(chosen.s("name"), chosen.s("mobile"), chosen.s("branch"), "Patient", chosen.s("patientId"), chosen.s("id"), chosen.s("disease"))
                }
                /* ঘোষিত আলাদা রোগীরা মূল ফলের ঠিক পরে বসেন, তাই এক নম্বরের
                   সবাই পাশাপাশি দেখা যায়। ⛔ কেউ কখনো বাদ পড়ে না। */
                val out = byMobile.values.toMutableList()
                out.addAll(extraHits)
                out
            }
            progressLoad.visibility = View.GONE
            results.clear()
            results.addAll(hits)
            adapter.notifyDataSetChanged()
            tvEmpty.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
            if (hits.isEmpty()) tvEmpty.text = "No match found."
        }
    }

    /**
     * 🔵🔒 V517 (TK-অনুমোদিত): মোবাইলের সঙ্গে **কোন রোগী** সেটাও পাঠানো হয়।
     * ⛔ `mobile` extra আগের মতোই যায়, তাই Timeline-এর পুরোনো সব পথ অটুট।
     * ⛔ `patientRowId` ফাঁকা হলে Timeline হুবহু আগের মতোই আচরণ করে।
     */
    private fun openTimeline(mobile: String, patientRowId: String = "") {
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        val i = Intent(this, PatientTimelineActivity::class.java).putExtra("mobile", digits)
        if (patientRowId.isNotBlank()) i.putExtra("patientRowId", patientRowId)
        startActivity(i)
    }

    private fun markArrivedHit(hit: SearchHit) {
        val digits = hit.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            android.widget.Toast.makeText(this, "No valid 10-digit mobile to mark", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val staffMobile = NativeSession.current(this)?.mobile ?: ""
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Mark Arrived?"))
            .setMessage(NoBengali.s("Mark ${hit.name.ifBlank { digits }} as Arrived (এসেছেন) in today's chamber?"))
            .setPositiveButton("Yes, Arrived") { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            ChamberAttendanceRepository.markArrived(this@GlobalSearchActivity, "+91$digits", hit.name, hit.branch, staffMobile)
                            true
                        } catch (_: Throwable) { false }
                    }
                    android.widget.Toast.makeText(this@GlobalSearchActivity, if (ok) "Marked Arrived ✅" else "Could not mark — please retry", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun callHit(mobile: String) {
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        try {
            // TK-REQUESTED (2026-07-24): "everywhere calling is possible in
            // the project" -- shared CallChooser.kt (Phone/Superfone/etc.
            // picker, Truecaller excluded).
            CallChooser.open(this, digits)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "No phone app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun whatsAppHit(mobile: String) {
        // 🔒 V235 (TK, WhatsApp Chooser project-wide): কেন্দ্রীয় chooser (Personal/Business)।
        WhatsAppMessageChooser.send(this, mobile)
    }

    private fun openPaymentForHit(mobile: String, rowId: String = "", patientCode: String = "") {
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        /* 🔵🔒 V520 (২২.০৮.২০২৬): এক নম্বরে দুজন আলাদা রোগী থাকলে **এই কার্ডটা
           কার** সেটা সাথে পাঠানো হয়, তাই Payment ঠিক এই রোগীরই ফর্ম খোলে।
           ⛔ ফাঁকা থাকলে আচরণ হুবহু আগের মতোই। */
        startActivity(
            Intent(this, PaymentActivity::class.java)
                .putExtra("mobile", digits)
                .putExtra("patientRowId", rowId)
                .putExtra("patientCode", patientCode)
        )
    }

    // TK APPROVED (2026-07-15): Search result card redesigned -- the four
    // clinical documents now each have their own direct one-tap button
    // (instead of hiding behind a "Docs" picker dialog), same destination
    // screens as before (PrescriptionActivity / MedicineSlipActivity /
    // InvestigationAdviceActivity / DietChartActivity), same RoleSession
    // setup as the old openClinicalForHit.
    private fun openClinicalDoc(hit: SearchHit, target: Class<*>) {
        val user = NativeSession.current(this) ?: return
        val roleStr = if (user.role.equals("doctor", true)) "DOCTOR" else "STAFF"
        val digits = hit.mobile.filter { it.isDigit() }.takeLast(10)
        // 🔒🔒 খাতার সারি B179 (TK, 30.07.2026 — TK-এর স্পষ্ট অনুমতি: "জায়গাতেও
        // ঠিক করতে চাই")। `SearchHit`-এ address/age/sex নেই, তাই এখানে **একটা
        // নতুন ছোট, সরু (slim) ক্লাউড-কল** — শুধু ওই তিনটে ঘর আনতে। ব্যর্থ
        // হলেও (অফলাইন ইত্যাদি) পর্দা খুলবে, শুধু ওই তিনটে ঘর ফাঁকা থাকবে —
        // আগের মতোই, কিছু ভাঙে না।
        lifecycleScope.launch {
            val (address, age, sex) = withContext(Dispatchers.IO) {
                try { com.tkbiswas.pilesclinic.native.AddressTagRepository.fetchDemographics("+91$digits", hit.rowId)   /* 🔵 V531 */ }
                catch (_: Throwable) { Triple("", "", "") }
            }
            com.tkbiswas.pilesclinic.clinical.RoleSession.applyFrom(
                roleStr, hit.name, digits, hit.branch, digits, address, age, sex, hit.disease,   // 🔵 V538
                // 🔒 খাতার সারি B175: `hit.patientId` (মানুষ-পড়া-যায় কোড) এমনিতেই
                // এই খোঁজার ফলাফলে আছে (SearchHit-এর নিজের ঘর), শুধু পাঠানো হত না।
                patientDisplayId = hit.patientId
            )
            startActivity(Intent(this@GlobalSearchActivity, target))
        }
    }

    private class SearchAdapter(
        val items: List<SearchHit>,
        val onFullJourney: (SearchHit) -> Unit,
        val onCall: (SearchHit) -> Unit,
        val onWhatsApp: (SearchHit) -> Unit,
        val onPayment: (SearchHit) -> Unit,
        val onPrescription: (SearchHit) -> Unit,
        val onMedicineSlip: (SearchHit) -> Unit,
        val onBloodTest: (SearchHit) -> Unit,
        val onDietChart: (SearchHit) -> Unit,
        // TK-REQUESTED (2026-07-20): mark a searched patient Arrived into
        // today's Chamber Attendance directly from Search.
        val onMarkArrived: (SearchHit) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.VH>() {
        // TK APPROVED (2026-07-15): premium dual-green search result card --
        // navy replaced with green (per TK's request), avatar + name/mobile in
        // a green gradient header, action buttons in a 2-per-row grid (icon +
        // label side by side, single line, ellipsis instead of ever breaking
        // mid-word) so everything fits on one screen without scrolling and
        // never visually breaks regardless of name/label length.
        class VH(
            val root: LinearLayout,
            val avatar: TextView,
            val tvName: TextView,
            val tvMeta: TextView,
            val tvTag: TextView,
            val grid: LinearLayout
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val dens = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * dens).toInt()

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(8), dp(6), dp(8), dp(6))
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(android.graphics.Color.WHITE)
                }
                clipToOutline = true
            }

            val header = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                    colors = intArrayOf(android.graphics.Color.parseColor("#0A5428"), android.graphics.Color.parseColor("#0EA25F"))
                }
            }
            val avatar = TextView(ctx).apply {
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                    colors = intArrayOf(android.graphics.Color.parseColor("#F4F6F9"), android.graphics.Color.parseColor("#A7ADB8"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { it.marginEnd = dp(12) }
            }
            header.addView(avatar)
            val nameCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val tvName = TextView(ctx).apply {
                textSize = 15.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val tvMeta = TextView(ctx).apply {
                textSize = 11.5f
                setTextColor(android.graphics.Color.parseColor("#DCF3E6"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(2); layoutParams = p
            }
            val tvTag = TextView(ctx).apply {
                textSize = 9.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(android.graphics.Color.parseColor("#C99A19"))
                }
                setPadding(dp(8), dp(2), dp(8), dp(2))
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(5); layoutParams = p
            }
            nameCol.addView(tvName); nameCol.addView(tvMeta); nameCol.addView(tvTag)
            header.addView(nameCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(header)

            val grid = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            root.addView(grid)

            return VH(root, avatar, tvName, tvMeta, tvTag, grid)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val h = items[position]
            val ctx = holder.root.context
            val dens = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * dens).toInt()

            holder.avatar.text = if (h.type == "Patient") "🧑‍⚕️" else "📞"
            holder.tvName.text = h.name.ifBlank { "(no name)" }
            holder.tvMeta.text = PatientIdText.mobileWithId(h.mobile, h.patientId) + " · " + h.branch
            holder.tvTag.text = h.type.uppercase()

            holder.grid.removeAllViews()

            // One 2-wide row of action buttons; icon+label always on a single
            // line (never breaks mid-word -- truncates with "…" in the rare
            // case a very long label wouldn't fit, but every label used here
            // is short enough to never actually need it).
            fun newRow(): LinearLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(7); layoutParams = p
            }
            fun actionButton(icon: String, label: String, green: Boolean, action: () -> Unit): LinearLayout {
                return LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(9), dp(8), dp(9))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        if (green) {
                            orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                            colors = intArrayOf(android.graphics.Color.parseColor("#0EA25F"), android.graphics.Color.parseColor("#0A5428"))
                        } else {
                            orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                            colors = intArrayOf(android.graphics.Color.parseColor("#F4F6F9"), android.graphics.Color.parseColor("#D6DBE2"))
                        }
                    }
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.marginEnd = dp(4); lp.marginStart = dp(4)
                    layoutParams = lp
                    isClickable = true; isFocusable = true
                    setOnClickListener { action() }
                    addView(TextView(ctx).apply {
                        text = icon; textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(6) }
                    })
                    addView(TextView(ctx).apply {
                        text = label; textSize = 10.5f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(if (green) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#1B2432"))
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }
            fun addPairRow(a: Pair<Triple<String, String, Boolean>, () -> Unit>, b: Pair<Triple<String, String, Boolean>, () -> Unit>) {
                val row = newRow()
                row.addView(actionButton(a.first.first, a.first.second, a.first.third, a.second))
                row.addView(actionButton(b.first.first, b.first.second, b.first.third, b.second))
                holder.grid.addView(row)
            }

            addPairRow(
                Triple("📞", "Call", false) to { onCall(h) },
                Triple("💬", "WhatsApp", false) to { onWhatsApp(h) }
            )
            addPairRow(
                Triple("💳", "Payment", true) to { onPayment(h) },
                Triple("🧭", "Full Journey", true) to { onFullJourney(h) }
            )
            addPairRow(
                Triple("📝", "Prescription", false) to { onPrescription(h) },
                Triple("💊", "Medicine Slip", false) to { onMedicineSlip(h) }
            )
            addPairRow(
                Triple("🩸", "Blood Test", false) to { onBloodTest(h) },
                Triple("🥗", "Diet Chart", false) to { onDietChart(h) }
            )
            // TK-REQUESTED (2026-07-20): Mark Arrived from Search -- one
            // full-width row so it stands out from the paired actions above.
            run {
                val row = newRow()
                row.addView(actionButton("🏥", "Mark Arrived (এসেছেন)", true) { onMarkArrived(h) })
                holder.grid.addView(row)
            }
        }
    }
}
