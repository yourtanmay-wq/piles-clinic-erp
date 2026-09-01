package com.tkbiswas.pilesclinic.native

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tkbiswas.pilesclinic.databinding.ActivityEnquiryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume   // 🔵 V534
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native rebuild Step 2 -- Enquiry.
 *
 * Same fields, same validation rules, and the same duplicate-mobile check
 * as the WebView's enquiryForm()/saveEnq() (see EnquiryModel.kt /
 * EnquiryRepository.kt for exactly which rules were mirrored). A saved
 * enquiry is fully interchangeable with a WebView-saved one -- it appears
 * in the same Follow-up (Enquiry tab) list on any device/screen.
 *
 * SCOPED LIMITATION (by design, for this step): the WebView's duplicate
 * popup offers "Continue to Registration" / "Reject" actions that open
 * other native screens (Registration, Follow-up), which now exist. This
 * native version shows the same duplicate information but only offers
 * "Save Anyway" / "Cancel" -- the fuller duplicate-handling options will
 * naturally become available here as Registration and Follow-up get their
 * own native rebuild steps.
 */
class EnquiryActivity : AppCompatActivity() {

    /**
     * 🔵🔒 V534: প্রজেক্টের সেই একই পর্দা (Payment · Print · Doctor Visit ·
     * Chamber-এ যেটা চলছে) — একাধিক আলাদা রোগী থাকলে তবেই দেখা যায়।
     */
    private suspend fun askWhichRestorePatient(
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
            .show()
    }

    private lateinit var binding: ActivityEnquiryBinding
    private lateinit var repository: EnquiryRepository

    private val branches = listOf("Kishanganj", "Jalpaiguri", "Cooch Behar", "Falakata", "Birpara")
    // TK-REQUESTED (2026-07-24): placeholder-first list for the mandatory,
    // never-auto-selected Branch spinner on this form.
    private val branchesWithPlaceholder = listOf("Select Branch") + branches
    private val diseases = listOf("Choose Disease", "🩸 Piles", "✂️ Fissure", "🔄 Fistula", "💧 Hydrocele", "🛡️ Gupt Rog", "📋 Other")
    private val diseaseValues = listOf("", "Piles", "Fissure", "Fistula", "Hydrocele", "Gupt Rog", "Other")
    private val timings = listOf("Official Time", "Unexpected Time")

    private var selectedDate: String = EnquiryModel.today()
    private var selectedNextFollow: String = ""
    private var lastDupChecked = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnquiryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FormStar.paint(binding.root)  // red '*' on mandatory labels (visual only)
        UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        BottomNav.wire(this)
        repository = EnquiryRepository(this)
        MobileInput.attach(binding.etMobile)

        // Live duplicate check (web's eMobDupBox): as soon as a full 10-digit
        // number is typed, show whether it already exists in enquiries/patients.
        binding.etMobile.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val digits = (s?.toString() ?: "").filter { it.isDigit() }.takeLast(10)
                if (digits.length != 10) { binding.tvDupWarning.visibility = View.GONE; lastDupChecked = ""; return }
                if (digits == lastDupChecked) return
                lastDupChecked = digits
                refreshTimingForMobile(digits)   // ☎️ V963
                lifecycleScope.launch {
                    val dup = withContext(Dispatchers.IO) { repository.checkDuplicate(StaffDirectory.normalizeMobile(digits)) }
                    binding.tvDupWarning.visibility = View.GONE
                    // As soon as a full number that already exists is entered, show
                    // an immediate popup (which branch / section) with View / Cancel.
                    if (dup.found) showDuplicatePopup(dup, digits)
                }
            }
        })
        // 🆕 B467 (05.08.2026, TK-নির্দেশ) — Briefing-এ পাঠানো নম্বরে কল করে
        // ফেরত এলে, সেই নম্বর কোনো Enquiry/Visit/Patient-এ না মিললে এই
        // পর্দা "prefillMobile" extra দিয়ে খোলে — ঘরটা আগে থেকেই ভরা
        // থাকে, স্টাফকে আবার টাইপ করতে হয় না। ⛔ extra না থাকলে (স্বাভাবিক
        // Enquiry) এক অক্ষরও বদলায় না। **TextWatcher বসানোর পরে বসানো
        // হলো** (যাচাইয়ের সময় নিজে ধরা — আগে বসালে prefill করা নম্বরে
        // ডুপ্লিকেট-চেক স্বয়ংক্রিয়ভাবে চলত না, staff-কে আবার একবার লিখে
        // মুছে টাইপ করতে হতো)।
        intent.getStringExtra("prefillMobile")?.let { if (it.isNotBlank()) binding.etMobile.setText(it) }

        val user = NativeSession.current(this)
        if (user == null) {
            finish()
            return
        }

        setupSpinners(user)
        // TK-REQUESTED (2026-07-24): Branch on this All-Branch Enquiry form
        // must NEVER auto-select or stay locked -- every save requires an
        // active, deliberate choice, every single time. 3-tap lock removed
        // for Branch only; "Call Received By" lock below is untouched.
        SpinnerLock.attach(binding.spReceivedBy, "Call Received By")
        setupTimingButtons()
        setupDiseaseButtons()
        binding.tvDate.text = displayDate(selectedDate)
        binding.tvDate.setOnClickListener { pickDate() }
        binding.tvNextFollow.setOnClickListener { pickNextFollow() }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { validateAndSave(user) }
        // TK-REQUESTED (2026-07-18): Save Enquiry button color -> green.
        // Only this button on this screen; the shared bg_gradient_button
        // drawable (used by Registration/Follow-Calendar/Login) is untouched.
        binding.btnSave.backgroundTintList =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#16A36D"))

        // Retry any enquiry that was queued locally because the device was
        // offline at the time, quietly, in the background.
        lifecycleScope.launch(Dispatchers.IO) { repository.flushPending() }
    }

    private fun setupSpinners(user: NativeUser) {
        // TK-REQUESTED (2026-07-24): no auto-select, no default -- Branch
        // starts on this placeholder ("Select Branch") every single time,
        // so Save's existing "Branch mandatory" check (validateAndSave
        // below) always forces an active choice.
        // TK RULE (2026-07-28): in the ENQUIRY form this is the same for every
        // role -- Master, Staff, Doctor -- everybody picks the Branch each time
        // and nothing is locked here. (The 3-tap lock belongs to Registration.)
        binding.spBranch.adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, branchesWithPlaceholder
        ) {
            // TK-REPORTED (2026-07-28): an unanswered box must not look filled
            // in, so the placeholder row is drawn in the light hint grey.
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? android.widget.TextView)?.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        this@EnquiryActivity,
                        if (position == 0) com.tkbiswas.pilesclinic.R.color.field_hint
                        else com.tkbiswas.pilesclinic.R.color.clinic_text_primary
                    )
                )
                return v
            }
        }
        binding.spBranch.setSelection(0)
        SpinnerPicker.attach(binding.spBranch, "SELECT BRANCH", hidePlaceholder = true)

        // Call Received By (locked with TK): ALL branches' ALL staff + master.
        // The dropdown shows only the code (name); the mobile stays in the system.
        val eligible = StaffDirectory.allAccounts()
            .filter { it.role == "master" || it.role == "staff" }
            .toMutableList()
        if (eligible.none { it.mobile == user.mobile }) {
            StaffDirectory.findAccount(user.mobile)?.let { eligible.add(0, it) }
        }
        receivedByMobiles = eligible.map { it.mobile }
        val display = eligible.map { if (it.role == "master") "Dr. ${it.name}" else it.name }
        binding.spReceivedBy.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, display)
        val meIdx = receivedByMobiles.indexOf(user.mobile)
        if (meIdx >= 0) binding.spReceivedBy.setSelection(meIdx)
    }

    private var receivedByMobiles: List<String> = emptyList()

    private var selectedDisease = ""

    private fun setupDiseaseButtons() {
        val map = listOf(
            binding.btnDisPiles to "Piles",
            binding.btnDisFissure to "Fissure",
            binding.btnDisFistula to "Fistula",
            binding.btnDisHydrocele to "Hydrocele",
            binding.btnDisGupt to "Gupt Rog",
            binding.btnDisOther to "Other"
        )
        map.forEach { (btn, value) -> btn.setOnClickListener { selectDisease(value, map) } }

        // TK-REQUESTED (2026-07-18): default (nothing tapped yet) look for these
        // 6 disease buttons was the app's dark-navy theme color, which TK does
        // not want here. Default/unselected is now grey; tapping a button turns
        // it green (see selectDisease below). Nothing else on this form changed.
        val grey = android.graphics.Color.parseColor("#9AA5B1")
        map.forEach { (btn, _) ->
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(grey)
            btn.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun selectDisease(value: String, map: List<Pair<android.widget.Button, String>>) {
        selectedDisease = value
        val green = android.graphics.Color.parseColor("#16A36D")
        val grey = android.graphics.Color.parseColor("#9AA5B1")
        map.forEach { (btn, v) ->
            val on = v == value
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(if (on) green else grey)
            btn.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private var selectedTiming = "Official Time"

    /* ☎️🕘🔒 V963 (০১.০৯.২০২৬, TK-এর লক করা নিয়ম, ফটো-প্রুফ পাশ) —
       TK-এর নিয়ম হুবহু:
         · ভারতীয় সময়ে সকাল ৯.০০ – সন্ধ্যা ৬.০০ = Official, বাইরে = Unexpected
         · নম্বর লেখামাত্রই অ্যাপ **ব্রাঞ্চের ফোনের কল-তালিকায়** ওই নম্বরের
           **প্রথম কল** খোঁজে (গত ৩ দিনের মধ্যে)
         · কল পাওয়া গেল ও সেটা **অফিসিয়াল** সময়ের ⇒ Unexpected বোতামটা
           **লুকিয়ে যায়** — স্টাফ চাইলেও বাছতে পারবে না
         · কল পাওয়া গেল ও সেটা **অসময়ের** ⇒ দুটো বোতামই থাকে, Unexpected
           বাছা থাকে (TK: *"অ্যাপ তো অনেক সময় ভুল করতেই পারে"*)
         · কল পাওয়া গেল না ⇒ দুটোই থাকে, Official বাছা; Unexpected চাপলে
           শুধু একটা ফ্ল্যাশ বার্তা — TK যেকোনো সময় যাচাই করতে পারেন
       ⛔ ক্লাউডে একটাও অনুরোধ নেই — ফোনের নিজের কল-তালিকা থেকেই।
       ⛔ V962-এর "কোনো বোতামই চাপা যাবে না" নিয়মটা TK-এর নতুন নির্দেশে
          তুলে নেওয়া হলো। */
    private fun timingOf(whenMs: Long): String {
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
        c.timeInMillis = whenMs
        val mins = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        // সকাল ৯.০০ (৫৪০) থেকে সন্ধ্যা ৬.০০ (১০৮০) পর্যন্ত — দুটোই ধরা
        return if (mins in 540..1080) "Official Time" else "Unexpected Time"
    }

    private fun autoTimingNow(): String = timingOf(System.currentTimeMillis())

    /** কল-তালিকায় ওই নম্বরের প্রথম কল পাওয়া গেছে কি না (পেলে তার সময়)। */
    private var branchCallMs: Long? = null

    private fun setupTimingButtons() {
        binding.btnTimingOfficial.setOnClickListener { selectTiming("Official Time") }
        binding.btnTimingUnexpected.setOnClickListener {
            selectTiming("Unexpected Time")
            // কল পাওয়া যায়নি ⇒ স্টাফের নিজের বাছাই ⇒ ফ্ল্যাশ বার্তা
            if (branchCallMs == null) showVerifyFlash()
        }
        applyTimingFromCall(null)
    }

    /** কল-তালিকার ফল অনুযায়ী বোতাম দুটো সাজায়। */
    private fun applyTimingFromCall(callMs: Long?) {
        branchCallMs = callMs
        if (callMs == null) {
            binding.btnTimingUnexpected.visibility = View.VISIBLE
            selectTiming("Official Time")
            return
        }
        val fromCall = timingOf(callMs)
        if (fromCall == "Official Time") {
            // অফিসিয়াল সময়ের কল ⇒ Unexpected বাছার সুযোগই থাকবে না
            binding.btnTimingUnexpected.visibility = View.GONE
            selectTiming("Official Time")
        } else {
            binding.btnTimingUnexpected.visibility = View.VISIBLE
            selectTiming("Unexpected Time")
        }
    }

    /** নম্বর লেখা হলে ব্রাঞ্চের কল-তালিকায় প্রথম কলটা খুঁজে বোতাম সাজানো। */
    private fun refreshTimingForMobile(digits: String) {
        lifecycleScope.launch {
            val ms = withContext(Dispatchers.IO) {
                try { BranchSimHelper.firstCallTimeMs(this@EnquiryActivity, digits) }
                catch (_: Throwable) { null }
            }
            if (!isFinishing && !isDestroyed) applyTimingFromCall(ms)
        }
    }

    /* 🔎 ফ্ল্যাশ বার্তা — কোথাও কিছু পাঠানো হয় না, শুধু পর্দায় দেখায়।
       TK: *"শুধুমাত্র বার্তা হিসেবেই দেখাবে … একটু বড় করে দেখাবে যাতে বুঝতে
       পারে"*। কয়েক সেকেন্ড পরে নিজেই বন্ধ হয়ে যায়। */
    private fun showVerifyFlash() {
        try {
            val d = resources.displayMetrics.density
            fun px(v: Int) = (v * d).toInt()
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(px(20), px(22), px(20), px(22))
                gravity = android.view.Gravity.CENTER
            }
            box.addView(android.widget.TextView(this).apply {
                text = "TK BISWAS can verify this at any time"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#0B4F2A"))
            })
            box.addView(android.widget.TextView(this).apply {
                text = "You marked this call as UNEXPECTED TIME"
                textSize = 13.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor("#33404F"))
                setPadding(0, px(12), 0, 0)
            })
            val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Recorded"))
                .setView(box)
                .setCancelable(true)
                .show().also { try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
            binding.root.postDelayed({ try { dlg.dismiss() } catch (_: Throwable) { } }, 4000L)
        } catch (_: Throwable) { }
    }

    private fun selectTiming(value: String) {
        selectedTiming = value
        val blue = android.graphics.Color.parseColor("#1167D8")
        val lightBlue = android.graphics.Color.parseColor("#E8F2FF")
        val official = value == "Official Time"
        binding.btnTimingOfficial.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (official) blue else lightBlue)
        binding.btnTimingOfficial.setTextColor(if (official) android.graphics.Color.WHITE else blue)
        binding.btnTimingUnexpected.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (!official) blue else lightBlue)
        binding.btnTimingUnexpected.setTextColor(if (!official) android.graphics.Color.WHITE else blue)
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        DatePickerDialog(this, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
            val cal2 = Calendar.getInstance().apply { set(y, m, d) }
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal2.time)
            selectedDate = iso
            binding.tvDate.text = displayDate(iso)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            // Future date not allowed, matching saveEnq()'s check.
            datePicker.maxDate = today.timeInMillis
        }.show()
    }

    private fun pickNextFollow() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, com.tkbiswas.pilesclinic.R.style.PilesDatePicker, { _, y, m, d ->
            val cal2 = Calendar.getInstance().apply { set(y, m, d) }
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal2.time)
            selectedNextFollow = iso
            binding.tvNextFollow.text = displayDate(iso)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            // Past next-follow-up date not allowed, matching saveEnq()'s check.
            datePicker.minDate = cal.timeInMillis
        }.show()
    }

    private fun displayDate(iso: String): String = try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
        SimpleDateFormat("dd.MM.yyyy", Locale.US).format(parsed!!)
    } catch (e: Exception) {
        iso
    }

    private fun validateAndSave(user: NativeUser) {
        hideError()
        val mobile = StaffDirectory.normalizeMobile(binding.etMobile.text.toString())
        val branch = binding.spBranch.selectedItem?.toString() ?: ""
        val name = binding.etName.text.toString().trim()
val disease = selectedDisease
        val address = binding.etAddress.text.toString().trim()
        val remarks = binding.etRemarks.text.toString().trim()
        // ☎️ V963 — বোতামে যা দেখাচ্ছে সেটাই; কল পাওয়া গেলে অ্যাপ আগেই বসিয়ে
        //   রেখেছে, না পেলে স্টাফের বাছাই (Unexpected হলে ফ্ল্যাশ বার্তা দেখানো হয়েছে)।
        val timing = selectedTiming

        // Same validation order as saveEnq(); on failure, jump to that field.
        if (mobile.length != 10) { focusError(binding.etMobile, "Valid mobile number required"); return }
        if (branch.isBlank() || branch == "Select Branch") { focusError(binding.spBranch, "Branch mandatory"); return }
        if (disease.isBlank()) { focusError(binding.btnDisPiles, "Please select a disease"); return }
        if (remarks.isBlank()) { focusError(binding.etRemarks, "Remarks mandatory"); return }
        if (selectedNextFollow.isBlank()) { focusError(binding.tvNextFollow, "Next Follow-up Call date mandatory"); return }

        /* 🛡️🔒 V863 (৩০.০৮.২০২৬, TK-অনুমোদিত) — নম্বরটা আমাদের নিজেদের কারো
           (স্টাফ · ক্লিনিক · ডাক্তার) হলে সেভের আগে একবার সতর্কবার্তা।
           ⛔ আমাদের নম্বর না হলে **এক মুহূর্তও দেরি নয়** — নিচের সবটা হুবহু
              আগের মতোই চলে। ⛔ আটকায় না, শুধু জিজ্ঞেস করে (আসল রোগীও
              স্টাফ/ডাক্তারের নম্বর দিতে পারেন — যাচাই করে দেখা)। */
        OwnNumberGuard.confirmIfOwn(this, mobile) { continueSave(user, mobile, branch, name, disease, address, remarks, timing) }
    }

    private fun continueSave(
        user: NativeUser, mobile: String, branch: String, name: String,
        disease: String, address: String, remarks: String, timing: String
    ) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val duplicate = withContext(Dispatchers.IO) { repository.checkDuplicate(mobile) }
                // 🚨 খাতার সারি B132 (TK প্রুফে পাশ, 29.07.2026 রাত ৮.২০):
                // নম্বরটা আগে Reject/Incomplete করা ছিল কিনা — এটা জানা থাকলে
                // বাক্সে লাল সতর্কবার্তা দেখানো হয়, নইলে বাক্সটা হুবহু আগের মতোই।
                // ⛔ শুধু ডুপ্লিকেট ধরা পড়লেই এই পড়াটা হয়, রোজকার সেভে নয়।
                val closed = if (duplicate.found)
                    withContext(Dispatchers.IO) { repository.closedInfo(mobile) }
                else EnquiryRepository.ClosedInfo(false)
                setLoading(false)
                if (duplicate.found) {
                    showRestoreDialog(user, duplicate, mobile, branch, disease, remarks, closed)
                } else {
                    performSave(user, mobile, branch, name, disease, address, remarks, timing)
                }
            } catch (e: Exception) {
                // CRASH-SAFETY FIX (TK-reported, 2026-07-16): this used to have
                // no error handling -- a problem during the duplicate-check
                // network call could crash the whole app instead of just
                // failing this one save attempt.
                setLoading(false)
                android.widget.Toast.makeText(this@EnquiryActivity, "Could not check — check connection and try again", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 🔴 V431 — ভাগের নাম ব্যবহারকারীর ভাষায় (ভিতরের কাঁচা নাম নয়)। */
    private fun dupSectionLabel(stage: String): String = when (stage.trim().lowercase()) {
        "inquiry", "enquiry" -> "Enquiry"
        "patient" -> "Visit"
        "treatment" -> "Patient"
        else -> stage.ifBlank { "-" }
    }

    /** Immediate popup shown the moment a full 10-digit number that already
     *  exists is entered: shows which branch / section it is in, with a View
     *  button (opens that patient's timeline) and Cancel (clears the field). */
    private fun showDuplicatePopup(dup: EnquiryRepository.DuplicateResult, digits: String) {
        val view = layoutInflater.inflate(com.tkbiswas.pilesclinic.R.layout.dialog_duplicate, null)
        val tvDupName = view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.tvDupName)
        val tvDupMobile = view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.tvDupMobile)
        tvDupName.text = dup.name.ifBlank { "-" }
        tvDupMobile.text = digits
        tvDupName.copyOnLongPress("Name", dup.name)
        tvDupMobile.copyOnLongPress("Mobile", digits)
        view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.tvDupBranch).text = dup.branch.ifBlank { "-" }
        // 🔴 V431 (TK-রিপোর্ট ১৮.০৮.২০২৬, ছবিসহ — "বানান ঠিক আছে কি?"):
        //    এখানে **ভিতরের নামটাই** কাঁচা দেখানো হচ্ছিল ("Inquiry"), অথচ
        //    ব্যবহারকারী সারা অ্যাপে ওই ভাগটাকে **"Enquiry"** নামেই চেনেন
        //    (এই পর্দারই নাম "New Enquiry", ফলো-আপের ট্যাব "👥 Enquiry",
        //    Draft-এ "Enquiry Reject")। তাই অ্যাপের নিজের নিয়মেই বদলে
        //    দেখানো হলো: Inquiry → Enquiry · Patient → Visit ·
        //    Treatment → Patient (DialerActivity.kt:431-435-এর একই নিয়ম)।
        //    ⛔ ডেটাবেসে `stage` আগের মতোই "Inquiry" থাকে — শুধু দেখানোর লেখা।
        view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.tvDupSection).text =
            dupSectionLabel(dup.stage)

        UppercaseInputUtil.applyToAll(view)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.btnDupView).setOnClickListener {
            // 🔴 TK-REPORTED (04.08.2026, একই ক্লাসের বাগ): dialog.dismiss()
            // ডাকা হত, তাই Timeline থেকে Back করলে এই পপ-আপ আর দেখা যেত না।
            val intent = Intent(this, PatientTimelineActivity::class.java)
            intent.putExtra("mobile", digits)
            startActivity(intent)
        }
        view.findViewById<android.widget.TextView>(com.tkbiswas.pilesclinic.R.id.btnDupClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
        try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(dialog) } catch (_: Throwable) { }   // 🤫 V774
        // 🔒 খাতার সারি B181 (TK, 30.07.2026): এই পপ-আপে এখন সরাসরি বাংলা লেখা
        // পাওয়া যায়নি, তবু ধারাবাহিকতার জন্য পাহারা বসানো হলো (ভবিষ্যতে কেউ
        // বাংলা যোগ করলেও যেন আটকায়)।
        PremiumAlert.paint(dialog)
    }

    /**
     * TK APPROVED (2026-07-27, full-screen photo proof, and LOCKED by TK):
     * the "this number already exists" popup used to be a plain message box --
     * TK's words: "সাদা মিঠা লাগছে... উপরে যে লেখার ধরন আমার পছন্দ হচ্ছে না"।
     *
     * The look is now: one clean header line, then Name / Branch / Section on
     * three separate lines (Section shows the same word the rest of the app
     * uses -- Enquiry / Visit / Patient -- not the internal one), the
     * explanation inside a soft note box, then a wide "Restore & Move" button
     * with "পুরনো History" and "Cancel" side by side under it.
     *
     * ⛔ NOTHING ABOUT THE BEHAVIOUR CHANGED: the same three actions do exactly
     * the same thing as before (restore-and-move / open the old timeline /
     * close). No new record is ever created here.
     */
    private fun showRestoreDialog(
        user: NativeUser, dup: EnquiryRepository.DuplicateResult,
        mobile: String, branch: String, disease: String, remarks: String,
        // 🔒 খাতার সারি B132 — রেকর্ডটা আগে Reject/Incomplete করা ছিল কিনা।
        //    ফাঁকা (closed = false) হলে বাক্সটা হুবহু আগের মতোই দেখায়।
        closed: EnquiryRepository.ClosedInfo = EnquiryRepository.ClosedInfo(false)
    ) {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val sectionLabel = when (dup.stage) {
            "Treatment" -> "Patient"
            "Patient" -> "Visit"
            "" -> "-"
            else -> "Enquiry"
        }

        val body = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        fun infoRow(label: String, value: String, withDivider: Boolean) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(android.widget.TextView(this).apply {
                text = label; textSize = 13.5f
                setTextColor(android.graphics.Color.parseColor("#6B7280"))
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(96), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(android.widget.TextView(this).apply {
                text = value.ifBlank { "-" }; textSize = 15.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#1F2937"))
            })
            row.setPadding(0, dp(8), 0, dp(8))
            body.addView(row)
            if (withDivider) {
                body.addView(android.view.View(this).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#EEF0F4"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    )
                })
            }
        }
        infoRow("Name", dup.name, true)
        infoRow("Branch", dup.branch, true)
        infoRow("Section", sectionLabel, false)

        // 🚨🚨 TK-APPROVED (ফটো-প্রুফ, 29.07.2026 রাত ৮.২০ · খাতার সারি B132):
        // TK: *"সমস্ত staff-কে জিজ্ঞাসা করলাম, কেউ Restore করেনি"* — অথচ Reject
        // করা নম্বর চালু তালিকায় ফিরে আসত। কারণ এই বাক্সটা কোথাও বলত না যে
        // রেকর্ডটা **আগে Reject/Incomplete করা হয়েছিল**, তাই স্টাফ `Restore &
        // Move`-কে "নতুন এনকোয়ারি সেভ করলাম" ভেবে চাপতেন।
        //
        // ⛔ **শুধু এই লাল বাক্সটাই নতুন** — বাকি সব লেখা · বোতাম · রং · ক্রম
        //    হুবহু আগের মতোই, একটাও বদলায়নি (TK প্রুফে পাশ করেছেন)।
        // ⛔ রেকর্ডটা বন্ধ না থাকলে (স্বাভাবিক ডুপ্লিকেট) এটা **দেখানোই হয় না**।
        // ⛔ কোনো বোতাম আটকানো হয়নি — স্টাফ জেনেবুঝে চাপলে আগের মতোই কাজ হবে।
        if (closed.closed) {
            val warn = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(android.graphics.Color.parseColor("#FDECEC"))
                    setStroke(dp(2), android.graphics.Color.parseColor("#D32F2F"))
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(14)
                layoutParams = lp
            }
            warn.addView(android.widget.TextView(this).apply {
                text = "\uD83D\uDEAB  THIS NUMBER WAS " + closed.what
                textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#B3261E"))
            })
            // কে ও কবে — যেটুকু সত্যিই জানা আছে শুধু সেটুকুই লেখা হয়,
            // কোনো কিছু আন্দাজে বসানো হয় না (TK-এর স্থায়ী নিয়ম)।
            val whoWhen = buildString {
                if (closed.whenText.isNotBlank()) append("On ").append(closed.whenText)
                if (closed.byName.isNotBlank()) {
                    if (isNotEmpty()) append("  \u00B7  ")
                    append("by ").append(closed.byName)
                }
            }
            if (whoWhen.isNotBlank()) {
                warn.addView(android.widget.TextView(this).apply {
                    text = whoWhen
                    textSize = 12.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#7A1F1F"))
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(6)
                    layoutParams = lp
                })
            }
            warn.addView(android.widget.TextView(this).apply {
                text = "It is now in " + closed.listName + ".\n" +
                    "Restore & Move will bring it back to the live list."
                textSize = 12.5f
                setTextColor(android.graphics.Color.parseColor("#7A1F1F"))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(6)
                layoutParams = lp
            })
            body.addView(warn)
        }

        body.addView(android.widget.TextView(this).apply {
            text = NoBengali.s("নতুন রেকর্ড হবে না — পুরনো রেকর্ডটাই Restore হয়ে '$branch' ব্রাঞ্চে আসবে। " +
                "যে সেকশনে ছিল সেখানেই থাকবে, পুরনো history অটুট।")
            textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#7A5A0F"))
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_dup_note)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(14)
            layoutParams = lp
        })

        val btnRestore = android.widget.TextView(this).apply {
            text = "Restore & Move"; textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_dup_amber_pill)
            setPadding(dp(10), dp(14), dp(10), dp(14))
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(18)
            layoutParams = lp
        }
        body.addView(btnRestore)

        val pair = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        fun sideButton(label: String, marginEnd: Int): android.widget.TextView =
            android.widget.TextView(this).apply {
                text = label; textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#1F2937"))
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_dup_outline_pill)
                setPadding(dp(6), dp(12), dp(6), dp(12))
                val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = marginEnd
                layoutParams = lp
            }
        val btnHistory = sideButton("Old History", dp(8))
        val btnCancel = sideButton("Cancel", 0)
        pair.addView(btnHistory)
        pair.addView(btnCancel)
        body.addView(pair)

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "⚠️ This number already exists"))
            .setView(body)
            .setCancelable(true)
            .create()

        btnRestore.setOnClickListener {
            dialog.dismiss()
            setLoading(true)
            lifecycleScope.launch {
                /* 🔵🔒 V534 (২২.০৮.২০২৬, TK-নির্দেশ) — এক নম্বরে সত্যিই একাধিক
                   আলাদা রোগী থাকলে **কার Restore** সেটা জিজ্ঞাসা করা হয়, নইলে
                   অন্যজনের রোগ/ব্রাঞ্চও বদলে যেত।
                   ⛔ একজন থাকলে (রোজকার ৯৯%) কিছুই জিজ্ঞাসা করা হয় না এবং
                      নিচের ডাকটা **হুবহু আগের মতোই** (দুটো ঘর ফাঁকা)। */
                var pickCode = ""
                var pickRowId = ""
                val people = withContext(Dispatchers.IO) {
                    try {
                        val rows = SupabaseClient.findByMobile(
                            "patients", EnquiryModel.normalizedMobile(mobile),
                            "id,name,mobile,branch,patientId,bill", 20
                        )
                        PatientIdentity.separateIdentities(rows, mobile)
                    } catch (_: Throwable) { emptyList() }
                }
                if (people.size >= 2) {
                    val chosen = askWhichRestorePatient(mobile, people)
                    if (chosen == null) { setLoading(false); return@launch }
                    pickRowId = chosen.s("id")
                    pickCode = chosen.s("patientId")
                }
                val ok = withContext(Dispatchers.IO) {
                    repository.restoreAndMove(
                        mobile, branch, disease, remarks, selectedNextFollow, user.name,
                        preferPatientCode = pickCode, preferRowId = pickRowId
                    )
                }
                setLoading(false)
                toastAndFinish(if (ok) "Restored into the '$branch' branch" else "Restore failed — check the network")
            }
        }
        // TK-REQUESTED ADDITION (2026-07-23), unchanged: staff can look at the
        // old full history before deciding. Opens the same PatientTimelineActivity
        // used everywhere else; the form data is untouched.
        // 🔴 TK-REPORTED (04.08.2026, একই ক্লাসের বাগ): আগে dialog.dismiss()
        // করে দেওয়া হত, তাই Timeline থেকে Back করলে এই ডুপ্লিকেট-পপ-আপ আর
        // দেখা যেত না — সরাসরি ফাঁকা ফর্মে থাকতে হত। এখন পপ-আপ খোলা রাখা হয়।
        btnHistory.setOnClickListener {
            startActivity(Intent(this, PatientTimelineActivity::class.java).putExtra("mobile", mobile))
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
        PremiumAlert.paint(dialog)
    }

    private fun showDuplicateDialog(
        duplicate: EnquiryRepository.DuplicateResult,
        user: NativeUser, mobile: String, branch: String, name: String,
        disease: String, address: String, remarks: String, timing: String
    ) {
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "⚠️ This number is already in the system"))
            .setMessage("Name: ${duplicate.name}\nBranch: ${duplicate.branch}\nStatus: ${duplicate.stage}")
            .setPositiveButton("Save Anyway") { _, _ ->
                performSave(user, mobile, branch, name, disease, address, remarks, timing)
            }
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
    }

    private fun performSave(
        user: NativeUser, mobile: String, branch: String, name: String,
        disease: String, address: String, remarks: String, timing: String
    ) {
        val draft = EnquiryDraft(
            date = selectedDate,
            branch = branch,
            name = name,
            mobileDigitsOnly = mobile,
            disease = disease,
            address = address,
            remarks = remarks,
            nextFollow = selectedNextFollow,
            timeType = timing,
            receivedByMobile = receivedByMobiles.getOrNull(binding.spReceivedBy.selectedItemPosition) ?: user.mobile
        )
        setLoading(true)
        lifecycleScope.launch {
            try {
                val savedOnline = withContext(Dispatchers.IO) {
                    repository.save(draft, user.mobile, user.name)
                }
                setLoading(false)
                // 🔒 TK-APPROVED (28.07.2026): এনকোয়ারি নেওয়ার পরে রোগীকে
                // ক্লিনিকের খবর পাঠানোর বাক্স। "পরে পাঠাব" চাপলেও পর্দা আগের
                // মতোই বন্ধ হয়, তাই স্টাফের চলতি কাজ কখনো আটকায় না।
                android.widget.Toast.makeText(
                    this@EnquiryActivity,
                    if (savedOnline) "Enquiry saved" else "Saved — will sync when online",
                    android.widget.Toast.LENGTH_LONG
                ).show().also { try { com.tkbiswas.pilesclinic.native.NoAutofill.scrubAnyDialog(it) } catch (_: Throwable) { } }   // 🤫 V774
                // 🔒 ENQUIRY_WHATSAPP_MESSAGE_FINAL_LOCK_2026-07-31_0950_IST —
                // পুরনো তিন-ভাষা-একসাথে ENQUIRY বার্তার বদলে এখন আগে ভাষা
                // বাছাই (বাংলা/হিন্দি/English), তারপর সেই ভাষার লকড টেমপ্লেট।
                // 🔴🟢 খাতার সারি B430 (TK-নির্দেশ, 05.08.2026 — "সেভ করার পর
                // ফর্ম বন্ধ না হয়ে, একই পর্দায় ঘরগুলো খালি হয়ে যাক, যাতে
                // পরের রোগী সাথে সাথে লেখা যায়")। আগে এখানে `finish()` —
                // পর্দা বন্ধ হয়ে Dashboard-এ ফিরে যেত। এখন `clearForm()` —
                // পর্দা খোলাই থাকে, শুধু সব ঘর খালি/ডিফল্ট হয়ে যায়। ⛔
                // সেভ/ডুপ্লিকেট-চেক/বার্তা-পাঠানোর লজিক এক অক্ষরও বদলায়নি।
                PatientMessage.showEnquiryMessage(
                    activity = this@EnquiryActivity,
                    branch = draft.branch,
                    name = draft.name,
                    mobile = draft.mobileDigitsOnly,
                    disease = draft.disease
                ) { clearForm() }
            } catch (e: Exception) {
                // CRASH-SAFETY FIX (TK-reported, 2026-07-16): same fix as
                // validateAndSave above.
                setLoading(false)
                android.widget.Toast.makeText(this@EnquiryActivity, "Could not save — check connection and try again", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toastAndFinish(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }

    // 🔴🟢 খাতার সারি B430 — Save-এর পরে ফর্ম খালি করার ফাংশন। প্রতিটা ঘর/
    // বোতাম ঠিক সেই ডিফল্ট অবস্থায় ফেরে যা `onCreate()`-এ প্রথমবার থাকে।
    // ⛔ "Call Received By" স্পিনার (কে কল রিসিভ করছেন) ইচ্ছাকৃতভাবে ছোঁয়া
    // হয়নি — এটা রোগীর তথ্য না, পুরো সেশন জুড়ে একই স্টাফ থাকেন।
    private fun clearForm() {
        binding.etMobile.setText("")
        binding.etName.setText("")
        binding.etAddress.setText("")
        binding.etRemarks.setText("")
        binding.tvDupWarning.visibility = View.GONE
        lastDupChecked = ""
        binding.spBranch.setSelection(0)
        selectedDate = EnquiryModel.today()
        binding.tvDate.text = displayDate(selectedDate)
        selectedNextFollow = ""
        binding.tvNextFollow.text = "Tap to select (optional)"
        setupDiseaseButtons()
        selectedDisease = ""
        setupTimingButtons()
        binding.etMobile.requestFocus()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSave.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnSave.alpha = if (loading) 0.6f else 1f
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    /** Shows the error and scrolls/focuses to the first empty mandatory field. */
    private fun focusError(target: View, msg: String) {
        showError(msg)
        FieldError.mark(target)
        binding.scrollForm.post {
            var top = 0
            var v: View = target
            while (v.parent is View && v.parent !== binding.scrollForm) {
                top += v.top
                v = v.parent as View
            }
            binding.scrollForm.smoothScrollTo(0, (top - 40).coerceAtLeast(0))
            target.requestFocus()
        }
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }
}
