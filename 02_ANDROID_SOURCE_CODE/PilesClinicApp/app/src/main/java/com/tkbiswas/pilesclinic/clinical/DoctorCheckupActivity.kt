package com.tkbiswas.pilesclinic.clinical

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import com.tkbiswas.pilesclinic.native.s
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.native.NativeSession
import com.tkbiswas.pilesclinic.native.PhotoUtils
import com.tkbiswas.pilesclinic.native.SupabaseClient
import com.tkbiswas.pilesclinic.native.SpinnerPicker
import com.tkbiswas.pilesclinic.native.CallChooser
import com.tkbiswas.pilesclinic.native.PatientTimelineActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tkbiswas.pilesclinic.native.UppercaseInputUtil

/**
 * Doctor Check-up — native rebuild of the WebView doctorCheck() form, faithful
 * to its 6 sections: Basic History, Previous Treatment History, Clinical
 * Findings (Visual + DRE + Grade + Investigation + Other), Counselling,
 * Financial Discussion, and Patient Decision.
 */
class DoctorCheckupActivity : AppCompatActivity() {

    private lateinit var etComplaint: EditText
    private lateinit var etDuration: EditText
    // V455 (TK-নির্দেশ ১৮.০৮.২০২৬): spAcuteChronic ঘর বাদ (UI থেকে সরানো)।
    private lateinit var spOccupation: Spinner
    private lateinit var etPrevTreatment: EditText
    // 🔵 B622 (11.08.2026, TK-নির্দেশ): etPrevResult · etPrevCost · etTreatmentDuration ঘর বাদ।
    // V455 (TK-নির্দেশ ১৮.০৮.২০২৬): etVisualOther · etDreOther · etOtherFindings ঘর বাদ।
    private lateinit var spGrade: Spinner
    private lateinit var etOnProbing: EditText
    // 🆕 TK-নির্দেশ (04.08.2026): Treatment Plan — 6টা Tick-বক্স, ৩টেতে
    // এডিটেবল টাকা।
    private lateinit var cbTxPerPiles: CheckBox
    private lateinit var etAmtPerPiles: EditText
    private lateinit var cbTxFistulaInch: CheckBox
    private lateinit var etAmtFistulaInch: EditText
    private lateinit var cbTxMachine: CheckBox
    private lateinit var cbTxKsharSutra: CheckBox
    private lateinit var etAmtKsharSutra: EditText
    private lateinit var cbTxLis: CheckBox
    private lateinit var cbTxInjection: CheckBox
    private lateinit var etCounselling: EditText
    private lateinit var etEstimatedCost: EditText
    private lateinit var etRecoveryTime: EditText
    // 🔵 B622 (11.08.2026, TK-নির্দেশ): "Advance Payment to be Done" (etAdvanceDiscussed) ঘর বাদ।
    // V455 (TK-নির্দেশ ১৮.০৮.২০২৬): spPatientDecision · etDecisionRemark · etDocuments ঘর বাদ।

    private lateinit var ivBeforePhoto: ImageView
    private lateinit var ivDuringPhoto: ImageView
    private lateinit var ivAfterPhoto: ImageView
    private var beforePhotoData = ""
    private var duringPhotoData = ""
    private var afterPhotoData = ""
    private var cameraPhotoUri: Uri? = null
    private var currentPhotoTarget = 0  // 0=before, 1=during, 2=after

    private val visualChecks = mutableListOf<CheckBox>()
    // 🔵 V556 (TK: "B রাখুন") — V455-এ সরানো DRE-র টিকগুলো ফিরে এল
    private val dreChecks = mutableListOf<CheckBox>()
    // V455 — dreChecks/dreOptions/dreGroup আর ব্যবহার হয় না (B সেকশন UI থেকে বাদ),
    // কিন্তু বাকি বিল্ড-চেইন (buildChecks কল) থেকে সরানো হলো নিচে; পুরনো সেভ
    // করা DRE মান (dreChecks-ভিত্তিক) ছোঁয়া হয়নি, শুধু নতুন টিক করার অপশন নেই।
    private val investigationChecks = mutableListOf<CheckBox>()

    // 🔴🟢 খাতার সারি B436 (TK-নির্দেশ, 05.08.2026, ছবিসহ) — Visual
    // Examination-এ "Internal Piles · অভ্যন্তরীণ অর্শ" নতুন যোগ হলো। ⛔
    // DRE সেকশনের নিজস্ব "Internal Piles" (dreOptions, নিচে) সম্পূর্ণ
    // আলাদা, আলাদা সেভ-হওয়া মান — এটা তার সাথে গুলিয়ে যায় না, দুটোই
    // স্বাধীনভাবে টিক করা যাবে।
    private val visualOptions = listOf("External Piles", "Internal Piles", "Fissure", "Fistula Opening", "Bleeding", "Swelling")
    // 🔒 B549 (08.08.2026, TK-নির্দেশ — স্ক্রিনশটে গোল দাগ দিয়ে: "একই লেখা দুইবার
    // কেন? নিচেরটা থেকে সরিয়ে দিন")। TK-কে আগে বোঝানো হয় Visual ও DRE আলাদা
    // পরীক্ষা, তবু TK নিচের (DRE) সেকশন থেকে ডুপ্লিকেট দুটো — "Internal Piles"
    // ও "Fissure" — সরাতে বলেন। তাই DRE-তে এখন শুধু Tenderness ও Fistula।
    // ⛔ Visual-এর "Internal Piles"/"Fissure" অপরিবর্তিত (উপরের তালিকা)। dreBn/
    // dreIcons-এ ওই দুটোর ম্যাপ-এন্ট্রি রইল (অব্যবহৃত, নিরীহ)। পুরনো কোনো
    // রেকর্ডে DRE-তে ওই মান সেভ থাকলে সেটা রিপোর্টে/ডিসপ্লেতে আগের মতোই দেখাবে
    // (ডিসপ্লে সরাসরি সেভ-করা লেখা থেকে হয়) — শুধু নতুন করে ওই দুটো আর টিক
    // করার অপশন থাকবে না।
    private val dreOptions = listOf("Tenderness", "Fistula")
    private val investigationOptions = listOf("MRI", "USG", "Colonoscopy", "Lab Reports")
    // 🆕 (03.08.2026, TK-অনুমোদিত মকআপ অনুযায়ী) — চেকবক্সের পাশে বাংলা দেখানোর
    // জন্য শুধু ডিসপ্লে-লেবেল ম্যাপ। ⛔ আসল সেভ-হওয়া মান (cb.tag, নিচে
    // buildChecks-এ) এখনো হুবহু আগের ইংরেজি শব্দ — পুরনো রেকর্ডে সেভ করা মান
    // এই বদলে এক অক্ষরও ভাঙে না।
    private val visualBn = mapOf(
        "External Piles" to "বাহ্যিক অর্শ", "Internal Piles" to "অভ্যন্তরীণ অর্শ", "Fissure" to "ফিসার", "Fistula Opening" to "ফিস্টুলার মুখ",
        "Bleeding" to "রক্ত পড়ে", "Swelling" to "ফোলাভাব"
    )
    private val dreBn = mapOf(
        "Tenderness" to "কোমলতা/ব্যথা", "Internal Piles" to "অভ্যন্তরীণ অর্শ", "Fissure" to "ফিসার", "Fistula" to "ফিস্টুলা"
    )
    private val investigationBn = mapOf(
        "MRI" to "এমআরআই", "USG" to "আল্ট্রাসাউন্ড", "Colonoscopy" to "কোলোনোস্কোপি", "Lab Reports" to "ল্যাব রিপোর্ট"
    )
    // TK APPROVED (2026-07-15): one icon + one accent colour per checklist
    // group, for a professional colourful look. Text/values/save logic are
    // completely unchanged -- only how each row is drawn.
    private val visualIcons = mapOf(
        "External Piles" to "🔴", "Internal Piles" to "🟠", "Fissure" to "➖", "Fistula Opening" to "🕳️",
        "Bleeding" to "🩸", "Swelling" to "🔵"
    )
    private val dreIcons = mapOf(
        "Tenderness" to "🤚", "Internal Piles" to "🔴", "Fissure" to "➖", "Fistula" to "🕳️"
    )
    private val investigationIcons = mapOf(
        "MRI" to "🧲", "USG" to "📡", "Colonoscopy" to "🔬", "Lab Reports" to "📋"
    )
    private val gradeOptions = listOf("", "Grade I", "Grade II", "Grade III", "Grade IV")
    // 🆕 (03.08.2026, TK-অনুমোদিত মকআপ, "ওকে লক") — শুধু ডিসপ্লে-লেবেল, আসল
    // সেভ-হওয়া মান (gradeOptions/decisionOptions, উপরে/নিচে) অপরিবর্তিত।
    // ⛔ রোমান সংখ্যা (I/II/III/IV) অপরিবর্তিত রাখা হয়েছে — বাংলা সংখ্যা
    // ব্যবহার নিষেধ (অ্যাপের স্থায়ী নিয়ম ৯.১১)।
    private val gradeBn = mapOf(
        "Grade I" to "গ্রেড I", "Grade II" to "গ্রেড II", "Grade III" to "গ্রেড III", "Grade IV" to "গ্রেড IV"
    )
    private val decisionBn = mapOf(
        "Agree for Treatment" to "চিকিৎসায় রাজি", "Not Agree" to "রাজি নয়", "Will Think" to "ভেবে দেখব",
        "Family Discussion" to "পরিবারের সাথে আলোচনা", "Financial Problem" to "আর্থিক সমস্যা", "Other" to "অন্যান্য"
    )
    private val decisionOptions = listOf(
        "Agree for Treatment", "Not Agree", "Will Think",
        "Family Discussion", "Financial Problem", "Other"
    )
    // 🆕 TK-নির্দেশ (04.08.2026): হঠাৎ হয় নাকি ধীরে ধীরে বাড়ে।
    private val acuteChronicOptions = listOf("", "Acute Onset (Sudden)", "Chronic Onset (Gradual)")
    private val acuteChronicBn = mapOf(
        "Acute Onset (Sudden)" to "হঠাৎ শুরু হয়েছে", "Chronic Onset (Gradual)" to "ধীরে ধীরে বেড়েছে"
    )
    // 🆕 TK-নির্দেশ (04.08.2026): Registration-এর spOccupation-এর হুবহু একই
    // তালিকা (RegistrationActivity.kt) — প্রজেক্টে দুই জায়গায় দুই রকম
    // পেশার তালিকা যেন না হয়ে যায়।
    private val occupationOptions = listOf(
        "Choose Occupation", "Farmer", "Housewife", "Business", "Service", "Student", "Labour", "Retired", "Others"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_doctor_checkup)
        UppercaseInputUtil.applyToAll(window.decorView.findViewById(android.R.id.content))  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically

        // 🔴🎨🔒 TK-নির্দেশ (06.08.2026): টুলবার/হেডার সম্পূর্ণ বাদ (XML
        // থেকেও সরানো হয়েছে) — তাই এই তিন লাইন (toolbar খোঁজা/
        // setSupportActionBar/navigation-click) আর দরকার নেই, সরানো হলো।
        // ফোনের নিজের Back (গেসচার/বোতাম) দিয়েই এই পর্দা থেকে বেরোনো যায়,
        // এখানে আলাদা কোনো onBackPressed override নেই।

        etComplaint = findViewById(R.id.etComplaint)
        etDuration = findViewById(R.id.etDuration)
        // V455 (18.08.2026): spAcuteChronic findViewById বাদ (ঘরটাই নেই)।
        spOccupation = findViewById(R.id.spOccupation)
        etPrevTreatment = findViewById(R.id.etPrevTreatment)
        // V455 (18.08.2026): etVisualOther · etDreOther · etOtherFindings findViewById বাদ।
        spGrade = findViewById(R.id.spGrade)
        etOnProbing = findViewById(R.id.etOnProbing)
        cbTxPerPiles = findViewById(R.id.cbTxPerPiles)
        etAmtPerPiles = findViewById(R.id.etAmtPerPiles)
        cbTxFistulaInch = findViewById(R.id.cbTxFistulaInch)
        etAmtFistulaInch = findViewById(R.id.etAmtFistulaInch)
        cbTxMachine = findViewById(R.id.cbTxMachine)
        cbTxKsharSutra = findViewById(R.id.cbTxKsharSutra)
        etAmtKsharSutra = findViewById(R.id.etAmtKsharSutra)
        cbTxLis = findViewById(R.id.cbTxLis)
        cbTxInjection = findViewById(R.id.cbTxInjection)
        // 🔒 TK-এর নিয়ম (৯.১৪, Kishanganj no-Bengali): visual/dre/investigation
        // checkbox-এর মতোই — .tag-এ স্থির ইংরেজি মান বসানো হলো, যাতে সেভ হওয়া
        // মান (checkedText() যেটা .tag পড়ে) Kishanganj/অন্য সব স্টাফের ফোনে
        // সবসময় হুবহু একই থাকে, বাংলা লেখা NoBengali.kt-এ মুছে গেলেও।
        cbTxPerPiles.tag = "Per Piles"
        /* 🔵🔒 V541 (২২.০৮.২০২৬, TK-নির্দেশ: *"Fistula Per CM (centimetre) করুন"*)
           ⛔ **পুরোনো রেকর্ড ভাঙবে না:** এতদিন সেভ হত `"Fistula Per Inch"`।
              নতুন সেভ হবে `"Fistula Per CM"`, আর ফেরত পড়ার সময় **দুটোই**
              মেনে নেওয়া হয় (নিচে `FISTULA_TAGS`) — তাই আগের প্রতিটা চেকআপে
              টিকটা আগের মতোই বসে থাকবে।
           ⛔ ঘরের নাম (`amtFistulaPerInch`) **বদলানো হয়নি** — ওটা ভিতরের নাম,
              বদলালে ওয়েব ও পুরোনো জমা তথ্যের সাথে মিল নষ্ট হত। */
        cbTxFistulaInch.tag = FISTULA_TAG_NOW
        cbTxMachine.tag = "Machine Treatment"
        cbTxKsharSutra.tag = "Kshar Sutra"
        cbTxLis.tag = "LIS Treatment"
        cbTxInjection.tag = "Injection (Vaccination) Treatment"
        etCounselling = findViewById(R.id.etCounselling)
        etEstimatedCost = findViewById(R.id.etEstimatedCost)
        etRecoveryTime = findViewById(R.id.etRecoveryTime)
        // V455 (18.08.2026): spPatientDecision · etDecisionRemark · etDocuments findViewById বাদ।

        bindPatientHeader()

        ivBeforePhoto = findViewById(R.id.ivBeforePhoto)
        ivDuringPhoto = findViewById(R.id.ivDuringPhoto)
        ivAfterPhoto = findViewById(R.id.ivAfterPhoto)
        findViewById<MaterialButton>(R.id.btnBeforePhoto).setOnClickListener { showPhotoDialog(0) }
        findViewById<MaterialButton>(R.id.btnDuringPhoto).setOnClickListener { showPhotoDialog(1) }
        findViewById<MaterialButton>(R.id.btnAfterPhoto).setOnClickListener { showPhotoDialog(2) }

        buildSymptomRows()   // 🔵 V554
        buildHistoryDetailRows()   // 🔵 V555
        buildLifestyleRows()       // 🔵 V556
        buildAnatomyBoard()        // 🔵 V558 — রোগের ছবি
        // 🔵 V556: B. আঙুল দিয়ে দেখে (DRE) — পুরোনো সেই একই তালিকা ও একই রং
        buildChecks(findViewById(R.id.dreGroup), dreOptions, dreChecks, dreIcons, "#0B4F2A", dreBn)
        buildChecks(findViewById(R.id.visualGroup), visualOptions, visualChecks, visualIcons, "#D64545", visualBn)
        /* 🔵 V539: Internal Piles-এ চাপ দিলেই Grade বাছার তালিকা। ⛔ বাকি
           চেকবক্সগুলো এক অক্ষরও বদলায়নি। */
        internalPilesBox()?.setOnClickListener { askInternalGrade() }
        /* 🔵 V540: Grade বাছা হলে চেকবক্স নিজে থেকেই টিক পড়ে ও পাশে Grade দেখায়।
           ⛔ শোনার কাজটা **একবারই** বসে (পপ-আপ খোলার সময় বারবার নয়)। */
        spGrade.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                internalPilesBox()?.isChecked = gradeOptions.getOrElse(p2) { "" }.isNotBlank()
                refreshInternalGradeLabel()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        })
        refreshInternalGradeLabel()
        // V455 (18.08.2026): dreGroup buildChecks বাদ — পুরো "B. DRE" সেকশন UI-তে নেই।
        // 🔴 TK-নির্দেশ (05.08.2026): E. Investigations-এর চারটে চেকবক্সে
        // (MRI/USG/Colonoscopy/Lab Reports) এখন শুধু ইংরেজি — বাংলা বাদ,
        // তাই এখানে investigationBn পাঠানো হচ্ছে না (buildChecks-এর ডিফল্ট
        // emptyMap())। ⛔ investigationBn ম্যাপটা মোছা হয়নি (ভবিষ্যতে
        // লাগলে ফেরানো যাবে), শুধু এই কলে ব্যবহার বন্ধ।
        // 🔴 V501: Investigation এখন Quick Action-এর নিচে ও তাদের সমান মাপে।
        buildChecks(findViewById(R.id.investigationGroup), investigationOptions,
            investigationChecks, investigationIcons, "#6F42C1", compact = true)
        spGrade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            gradeOptions.map { it.ifBlank { "— Select Grade —" } }.let { list ->
                list.mapIndexed { i, s -> gradeBn[gradeOptions[i]]?.let { "$s · $it" } ?: s }
            })
        // 🔴🎨🔒 TK-নির্দেশ (05.08.2026, "প্রফেশনাল লোক খুঁজে পাচ্ছিলাম না...
        // ডানে ভেঙে যায়") — Android-এর নিজের নেটিভ ড্রপডাউন লম্বা ইংরেজি-
        // বাংলা মেশানো লেখা ডান দিকে কেটে ("...") দেখাত। spOccupation-এ
        // আগে থেকেই TK-অনুমোদিত (28.07.2026, ফটো-প্রুফ ২) সমাধান আছে —
        // `SpinnerPicker` (কেন্দ্রে খোলা প্রিমিয়াম পপ-আপ, পুরো লেখা দেখা
        // যায়, কাটে না) — এই তিনটে Spinner-এও (Grade/Patient Decision/
        // Acute-Chronic) হুবহু একই প্রমাণিত ব্যবস্থা বসানো হলো। ⛔ Spinner/
        // adapter/positions/সেভ-হওয়া মান (selectedItemPosition-ভিত্তিক)
        // এক অক্ষরও বদলায়নি — শুধু চাপলে কোন পপ-আপ খোলে সেটাই বদলাল।
        SpinnerPicker.attach(spGrade, "SELECT GRADE", hidePlaceholder = true)
        // V455 (18.08.2026): spPatientDecision · spAcuteChronic adapter/SpinnerPicker সেটআপ বাদ (ঘর নেই)।
        // 🔴🔒 V501 (TK-নির্দেশ ২১.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত) — Occupation-এর
        //    বাক্স বাকি ঘরগুলোর সমান। আগে Android-এর `simple_spinner_dropdown_item`
        //    ব্যবহার হতো, যার ভিতরে ≈৪৮dp বাধ্যতামূলক উচ্চতা বসানো — তাই বাক্সটা
        //    প্রায় দ্বিগুণ লম্বা দেখাত। এখন প্রকল্পের নিজের ছোট item
        //    (`item_docnote_spinner`, `item_branch_spinner`-এর প্রমাণিত ধাঁচ)।
        //    ⛔ বাছাইয়ের তালিকা · মান · সেভ — কিছুই বদলায়নি।
        /* 🔵 V557: রোগের তালিকা রেজিস্ট্রেশনের হুবহু একই, আর সময়ের একক
           সেই একই তিনটে — দুটোই প্রজেক্টের নিজের প্রিমিয়াম পিকারে খোলে। */
        val spDisease = findViewById<android.widget.Spinner>(R.id.spProbableDisease)
        spDisease.adapter = ArrayAdapter(this, R.layout.item_docnote_spinner, CounselModel.DISEASES)
        SpinnerPicker.attach(spDisease, "সম্ভাব্য কি রোগ?", hidePlaceholder = true)
        val spTimeUnit = findViewById<android.widget.Spinner>(R.id.spTimeAskedUnit)
        spTimeUnit.adapter = ArrayAdapter(this, R.layout.item_docnote_spinner, CounselModel.UNITS)
        SpinnerPicker.attach(spTimeUnit, "কতদিন সময় চাওয়া হল?")

        spOccupation.adapter = ArrayAdapter(this, R.layout.item_docnote_spinner, occupationOptions)
        SpinnerPicker.attach(spOccupation, "CHOOSE OCCUPATION", hidePlaceholder = true)

        wireSteps()
        setupKeyboardVisibilityToggle()

        val btnSave = findViewById<MaterialButton>(R.id.btnSaveCheckup)
        val btnContinue = findViewById<MaterialButton>(R.id.btnGoToPrescription)

        // 🔴🔴🔴 খাতার সারি B437 (TK-নির্দেশ, 05.08.2026 — "আগের সেভ করাগুলো
        // লক থাকতে হবে")। **গভীরে যাচাই করে গুরুতর বাগ পাওয়া গেছে (TK যা
        // বলেননি, কিন্তু ঠিক এই আশঙ্কারই আসল কারণ):** `ClinicalRepository.
        // lastCheckup` কোনো নির্দিষ্ট রোগীর সাথে বাঁধা ছিল না, আর কখনো
        // রিসেট হতো না (`resetForNewPatient()` ফাংশন লেখা ছিল কিন্তু
        // **কোথাও ডাকাই হতো না**)। ফলে: একজন ডাক্তার এক রোগীর Checkup
        // Save করার পরে, অ্যাপ বন্ধ না করেই (স্বাভাবিক দিনের কাজে যা হয়েই
        // থাকে) **অন্য যেকোনো রোগীর** Checkup পর্দা খুললেই সেই **আগের
        // রোগীর** তথ্য চুপচাপ ফর্মে ভরে যেত — রোগী ভুল হয়ে যাওয়ার আসল,
        // গুরুতর ঝুঁকি ছিল। **সমাধান:** এই স্বয়ংক্রিয়-ভরাট সম্পূর্ণ
        // সরানো হলো — প্রতিটা নতুন Checkup এখন সবসময় ফাঁকা ফর্ম দিয়ে শুরু
        // হয়, কখনো অন্য (বা এমনকি একই) রোগীর আগের তথ্য থেকে ভরে না।
        // পুরনো তথ্য এখন শুধু "📜 History"-তেই দেখা যায় (নিচে নতুন বোতাম),
        // **সম্পাদনাযোগ্য ফর্মে কখনো আসবে না** — এটাই TK-এর চাওয়া "লক"।

        val readOnly = !RoleSession.canEditClinical()
        if (readOnly) {
            findViewById<TextView>(R.id.tvReadOnlyNotice).visibility = android.view.View.VISIBLE
            setFieldsEnabled(false)
            btnSave.isEnabled = false
            btnSave.text = "Doctor-only"
        }

        btnSave.setOnClickListener {
            val record = collect()
            PrescriptionOptionsStore.captureCheckup(applicationContext, record)
            // 🔴 B437 — আগে এখানে `ClinicalRepository.lastCheckup = record`
            // বসত, যেটাই উপরের ক্রস-রোগী বাগের উৎস ছিল। যেহেতু এখন কেউ এই
            // ভ্যারিয়েবলটা পড়েই না (populate() ডাক সরানো হয়েছে), এই লাইনটাও
            // সরানো হলো — নইলে অকারণে মেমোরিতে আগের রোগীর তথ্য থেকে যেত।
            ClinicalRepository.addVisit(
                type = "Check-up",
                summary = record.complaint.ifBlank { record.patientDecision }.take(80),
                role = RoleSession.currentRole
            )
            val details = buildDetails(record)
            val createdBy = NativeSession.current(this)?.mobile ?: ""
            val pid = RoleSession.currentPatientId
            val pname = RoleSession.currentPatientName
            val photosJson = org.json.JSONObject().apply {
                if (record.beforePhoto.isNotBlank()) put("before", record.beforePhoto)
                if (record.duringPhoto.isNotBlank()) put("during", record.duringPhoto)
                if (record.afterPhoto.isNotBlank()) put("after", record.afterPhoto)
            }
            val photosStr = if (photosJson.length() > 0) photosJson.toString() else ""
            // 🔒🔒 খাতার সারি B188 (TK, 30.07.2026 সন্ধ্যা — "ডক্টর ভিজিটের নাম
            // এন্ট্রি ও মেডিসিন প্রেসক্রাইবের পরে লোডিং" রিপোর্টের অংশ):
            // TK-এর ২৮.০৭.২০২৬-এর নিয়ম — "Save চাপার সঙ্গে সঙ্গে পরের পর্দা
            // খুলবে, ক্লাউডে পাঠানো শেষ হওয়ার জন্য স্টাফকে বসিয়ে রাখা যাবে না"
            // — ইতিমধ্যে Prescription · Investigation Advice · Diet Chart-এ
            // (এই একই ফোল্ডারে) বসানো আছে; শুধু **এই একটা স্ক্রিন (Doctor
            // Checkup) বাদ পড়ে গিয়েছিল** — এখানে Save চাপলে `saveMedical()`
            // (ক্লাউডে আসল আপলোড, নেটওয়ার্ক শেষ না হওয়া পর্যন্ত আটকে থাকে)
            // আর তার ঠিক পরে `markDoctorComplete()` (আরেকটা নেটওয়ার্ক কল) —
            // দুটোই **একের পর এক অপেক্ষা করে**, তারপর Toast দেখাত। এটাই
            // "Doctor Visit-এ নাম এন্ট্রির পরে লোডিং"-এর একটা কারণ।
            //
            // ⛔ **কী বদলাল:** এখন বাকি তিনটে স্ক্রিনের **হুবহু একই প্যাটার্ন**
            // (Investigation Advice-এর সঙ্গে মিলিয়ে দেখা হয়েছে) — Toast
            // **সঙ্গে সঙ্গে** দেখানো হয়, আর `saveMedical()` (যেটা নিজে
            // ফোনে-আগে-সেভ + retry-queue-সহ, কিন্তু ভিতরে ব্লকিং নেটওয়ার্ক
            // কল আছে) এবং `markDoctorComplete()` দুটোই **`BackgroundWork.run{}`
            // -এর ভিতরে** পিছনে চলে — মূল থ্রেডে (UI) কোনো অপেক্ষা নেই।
            // ⛔ **কিছুই হারায় না** — `saveMedical()`-এর ভিতরে ফোনে-আগে-সেভ
            // + ব্যর্থ হলে retry-queue আগে থেকেই ছিল, অক্ষত। ⛔ ৭-ধাপের ফর্ম,
            // checkbox, autofill, ছবি (before/during/after) — কিছুই ছোঁয়া
            // হয়নি, শুধু Toast/ক্লাউড-কলের সময়টা।
            Toast.makeText(this@DoctorCheckupActivity, "Check-up saved.", Toast.LENGTH_SHORT).show()
            val appCtx = applicationContext
            val branch = RoleSession.currentPatientBranch
            val displayId = RoleSession.currentPatientDisplayId.ifBlank { pid }
            // 🔵 V557: পিছনের কাজে যাওয়ার আগেই দরকারি মানগুলো ধরে রাখা
            val costBefore = lastSavedCost
            val byName = NativeSession.current(this)?.name.orEmpty().ifBlank { createdBy }
            lastSavedCost = record.estimatedCost
            // 🔵 V559: এই রোগীর যে লেখাটা রোগীর সারিতে বসবে
            pendingNote = record
            com.tkbiswas.pilesclinic.native.BackgroundWork.run {
                ClinicalCloudRepository.saveMedical(appCtx, pid, pname, "Doctor Checkup", "", details, createdBy, photosStr)
                // 🔴 TK-নির্দেশ (04.08.2026): আগে শুধু "Agree for Treatment"
                // বাছলেই doctorComplete=true হত — বাকি পাঁচটা সিদ্ধান্তে
                // (Not Agree/Will Think/Family Discussion/Financial Problem/
                // Other) ডাক্তার সত্যিই চেক-আপ শেষ করেও রোগী CHECK-UP Queue-তে
                // চিরকাল আটকে থাকতেন। এখন যেকোনো সিদ্ধান্তেই checkup শেষ ধরা
                // হয় — Save-এর সাথে সাথেই doctorComplete=true, Queue থেকে সরে
                // যায়। Best-effort; কখনো checkup সেভ আটকায় না।
                markDoctorComplete(pid)
                // 🔒 কাজ শেষ — সাথে সাথেই মুছে ফেলা হয়, যাতে পরের রোগীর
                // ঘরে আগেরজনের লেখা বসার কোনো পথ না থাকে (B437-এর শিক্ষা)।
                pendingNote = null
                // 🔔 TK-নির্দেশ (04.08.2026): "Agree for Treatment" ছাড়া অন্য
                // যেকোনো সিদ্ধান্তে সেই রোগীর ব্রাঞ্চের স্টাফের ঘন্টায় নোটিশ
                // যাবে (কে ফলো-আপ করবেন বোঝার জন্য)। ⛔ "Agree for
                // Treatment"-এ কোনো নোটিশ নেই (এটাই স্বাভাবিক পথ, খবর দেওয়ার
                // দরকার নেই)। ⛔ নোটিশ ব্যর্থ হলেও checkup সেভ/doctorComplete
                // কিছুই আটকায় না (আলাদা try-catch)।
                /* ═══ 🔵🔒 V557 (২২.০৮.২০২৬, TK-এর সরাসরি নির্দেশ) ═══
                   ১. **সম্ভাব্য রোগ বদলালে রোগীর সারিতেও লেখা হয়** — তাহলেই হেডার ·
                      তালিকা · ছাপা সব জায়গায় নতুন নামটা দেখায়।
                      ⛔ ডাক্তার হাত না দিলে (বা যা আছে তাই বাছলে) **এক অক্ষরও লেখা হয় না**
                         (`CounselModel.diseaseChanged` — চালিয়ে যাচাই করা)।
                      ⛔ শুধু `disease` ঘরটাই; রোগীর আর কিছু ছোঁয়া হয় না।
                   ২. **আনুমানিক খরচ বলা হলে সেই ব্রাঞ্চের সব স্টাফের ঘণ্টায় নোটিশ**
                      (TK: *"নোটিফিকেশন সেই ব্রাঞ্চের সবার কাছে যাবে"*)।
                      ⛔ খরচ ফাঁকা/শূন্য হলে বা আগের বারের সমান হলে **নোটিশ যায় না** —
                         নইলে প্রতিবার Save-এ অকারণে ঘণ্টা বাজত।
                      ⛔ নোটিশের পথটা নতুন নয় — নিচের "Patient Decision"-এর হুবহু একই।
                   ⛔ দুটোই আলাদা try-catch — ব্যর্থ হলেও চেক-আপ সেভ কখনো আটকায় না। */
                try {
                    if (CounselModel.diseaseChanged(patDisease, record.probableDisease)) {
                        SupabaseClient.updateById(
                            "patients", pid,
                            org.json.JSONObject().put("disease", record.probableDisease)
                        )
                    }
                } catch (_: Throwable) { }
                try {
                    if (CounselModel.shouldNotifyCost(costBefore, record.estimatedCost)) {
                        val shownDisease = if (record.probableDisease.isNotBlank() &&
                            record.probableDisease != CounselModel.PICK_NONE) record.probableDisease else patDisease
                        com.tkbiswas.pilesclinic.native.BriefingRepository().post(
                            appCtx, CounselModel.COST_TITLE,
                            CounselModel.costMessage(pname, displayId, branch, record.estimatedCost,
                                shownDisease, record.timeAsked, byName),
                            "branch", branch, "", createdBy
                        )
                    }
                } catch (_: Throwable) { }

                if (record.patientDecision.isNotBlank() && record.patientDecision != "Agree for Treatment") {
                    try {
                        val msg = "$pname ($displayId) — Doctor checkup done. " +
                            "Patient decision: ${record.patientDecision}. Please follow up."
                        com.tkbiswas.pilesclinic.native.BriefingRepository().post(
                            appCtx, "Patient Decision", msg, "branch",
                            branch, "", createdBy
                        )
                    } catch (_: Throwable) { }
                }
            }
            // 🔴 (07.08.2026, scroll_C মকআপ) — সেভ হওয়ামাত্র সব সেকশন লক
            // (read-only summary), Estimate লুকানো, Quick Actions বাদ, নিচে
            // Share/Print। ⛔ উপরের সেভ/ক্লাউড-লজিক অপরিবর্তিত — শুধু দেখার লক।
            enterLockedMode(record)
        }

        btnContinue.setOnClickListener {
            startActivity(Intent(this, PrescriptionActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnMedicineSlip).setOnClickListener {
            startActivity(Intent(this, MedicineSlipActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnBloodTest).setOnClickListener {
            startActivity(Intent(this, InvestigationAdviceActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnDiet).setOnClickListener {
            startActivity(Intent(this, DietChartActivity::class.java))
        }
    }

    /** Patient header card (web: queueRow card). Name/Reg/Branch are shown
     *  instantly from the session; mobile/age/sex/photo are filled best-effort
     *  from Supabase and never block the screen. */
    /** Professional step form (web parity content, minimal scrolling): the
     *  sections show one at a time via a ViewFlipper, with a tappable step bar
     *  on top and a sticky Back / Save / Next bar at the bottom.
     *  🔒 TK-নির্দেশ (04.08.2026, ফটো-প্রুফে "লক করুন"): আগে ৭টা ধাপ ছিল,
     *  এখন History+Previous এক করে ও Finance+Decision এক করে ৫টা ধাপ। */
    private val stepTitles = listOf(
        "1 History & Previous", "2 Clinical", "3 Counsel",
        "4 Estimate & Decision", "5 Photo & Video"
    )
    private var stepChips = mutableListOf<TextView>()   // 🎨 এখন এগুলো নম্বর-বৃত্ত (TextView)
    // 🎨 (07.08.2026, প্রুফ-চেহারা) — প্রতি চিপের নিচের ছোট লেবেল (History/Clinical…)
    private var stepChipLabels = mutableListOf<TextView>()
    private val stepShort = listOf("History", "Clinical", "Counsel", "Estimate", "Photo")
    private var currentStep = 0

    // 🆕 (07.08.2026, TK-অনুমোদিত) — সেভ-করা রেকর্ড ও A4 রিপোর্টের পেশেন্ট-তথ্য
    // (bindPatientHeader-এর ক্লাউড-খোঁজায় ভরে; কোনো নতুন কল নয়)।
    private var lastSavedRecord: CheckupRecord? = null
    private var patMobile = ""
    private var patAge = ""
    private var patSex = ""
    private var patDisease = ""
    private var patAddress = ""
    // 🔒 B551 (08.08.2026, TK-অনুমোদিত) — A4 রেকর্ডে রোগীর ছবি দেখানোর জন্য
    // মনে রাখা (checkup খোলার সময় `p.s("photo")` আগে থেকেই আসে, নতুন কল নয়)।
    private var patPhoto = ""

    // 🎨 (07.08.2026) — ধাপ-নম্বরের গোল বৃত্ত (active/done হলে সবুজ, নইলে ধূসর)।
    private fun stepCircleBg(green: Boolean): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor(if (green) "#12805C" else "#DFE5EC"))
        }

    private fun wireSteps() {
        val bar = findViewById<LinearLayout>(R.id.stepBar)
        // 🎨🔒 (07.08.2026, প্রুফ-চেহারা, TK-অনুমোদিত) — আগে প্রতিটা চিপ ছিল
        // বড় পিল (padding 16/8, marginEnd 6), scroll-view-এ পরপর — ৩টার পর
        // ডান দিকে বেরিয়ে যেত। এখন ৫টা "নম্বর-বৃত্ত + নিচে ছোট লেবেল" সমান
        // ভাগে (weight 1) এক লাইনে বসে। চাপলে আগের মতোই showStep(i)।
        // আগের কোড:
        //   stepTitles.forEachIndexed { i, title ->
        //     val chip = TextView(this).apply { text=title; textSize=12.5f
        //       setPadding(dp(16),dp(8),dp(16),dp(8)); marginEnd=dp(6)
        //       setOnClickListener { showStep(i) } }
        //     bar.addView(chip); stepChips.add(chip) }
        bar.removeAllViews(); stepChips.clear(); stepChipLabels.clear()
        stepShort.forEachIndexed { i, short ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true; isFocusable = true
                setOnClickListener { showStep(i) }
            }
            val circle = TextView(this).apply {
                text = (i + 1).toString()
                textSize = 11.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor("#9AA6B4"))
                background = stepCircleBg(false)
                val s = dp(24)
                layoutParams = LinearLayout.LayoutParams(s, s)
            }
            val lbl = TextView(this).apply {
                text = short
                textSize = 8.5f
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                setTextColor(android.graphics.Color.parseColor("#9AA6B4"))
                setPadding(0, dp(3), 0, 0)
            }
            cell.addView(circle); cell.addView(lbl)
            bar.addView(cell)
            stepChips.add(circle); stepChipLabels.add(lbl)
        }
        // 🔴 (07.08.2026, scroll_A মকআপ) — এক-পেজ স্ক্রলে "Next" ধাপ নেই;
        // btnNext লুকানো, btnBack এখন পর্দা থেকে বেরোয় (finish)। রোগীর পূর্ণ
        // কার্ড সবসময় উপরে থাকে, সংকুচিত-কার্ড লুকানো।
        findViewById<MaterialButton>(R.id.btnNext).visibility = android.view.View.GONE
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.patientDetailsFull).visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.patientDetailsCollapsed).visibility = android.view.View.GONE
        // Share / Print — সব সেভ হয়ে লক হলে দেখা যায় (enterLockedMode)।
        findViewById<MaterialButton>(R.id.btnShareCheckup).setOnClickListener { shareCheckup() }
        // 🔴 V501 — Back ও Save-এর মাঝের নতুন Share বোতাম (TK-নির্দেশ)।
        findViewById<MaterialButton>(R.id.btnShareNow).setOnClickListener { saveThenShare() }
        findViewById<MaterialButton>(R.id.btnPrintCheckup).setOnClickListener { printCheckup() }
        // 🆕 (07.08.2026) — A4 রিপোর্ট থেকে ✎ Edit চাপলে আবার ফর্মে ফেরে।
        findViewById<MaterialButton>(R.id.btnEditCheckup).setOnClickListener { showEditForm() }
        showStep(0)
    }

    // 🔴🎨🔒 খাতার সারি B432 (TK-নির্দেশ, 05.08.2026 — "কীবোর্ড খোলা থাকলে
    // BACK/SAVE/NEXT বার কীবোর্ডের ঠিক উপরে ভেসে থাকা প্রফেশনাল লাগছে
    // না")। **পদ্ধতি:** root content view-এর দৃশ্যমান উচ্চতা মাপা হয় —
    // কীবোর্ড খুললে সেই উচ্চতা হঠাৎ অনেকখানি কমে যায় (স্ক্রিনের অন্তত
    // ১৫%), তখনই বোঝা যায় কীবোর্ড খোলা। এটাই Android-এ কীবোর্ড দেখা/না-
    // দেখা বোঝার সবচেয়ে প্রচলিত, নির্ভরযোগ্য পদ্ধতি (নতুন কোনো বিশেষ
    // permission/API লাগে না) — Manifest-এ এই স্ক্রিনের জন্য
    // `windowSoftInputMode="adjustResize"` বসানো হয়েছে যাতে এই মাপাটা
    // সব ফোনে (Xiaomi/Vivo/Oppo-ঘরানার ফোনেও) একইভাবে কাজ করে।
    // ⛔ **ঝুঁকি/সীমা (TK-কে জানানো হলো):** এটা উচ্চতা-মাপার উপর ভিত্তি
    // করে, তাই অত্যন্ত বিরল/অস্বাভাবিক স্ক্রিন-মোডে (split-screen, ভাঁজ-
    // করা ফোন) সামান্য ভুল হতে পারে — সেক্ষেত্রে সবচেয়ে খারাপ ফল শুধু
    // এই যে বারটা মাঝে মাঝে ভুল সময়ে দেখা/লুকানো হবে, অ্যাপ ক্র্যাশ
    // করবে না, কোনো ডেটা/সেভ-লজিক এতে ছোঁয়া হয় না।
    private fun setupKeyboardVisibilityToggle() {
        try {
            val rootView = findViewById<android.view.View>(android.R.id.content)
            val bottomBar = findViewById<android.view.View>(R.id.bottomActionBar)
            rootView.viewTreeObserver.addOnGlobalLayoutListener {
                try {
                    val r = android.graphics.Rect()
                    rootView.getWindowVisibleDisplayFrame(r)
                    val screenHeight = rootView.rootView.height
                    if (screenHeight <= 0) return@addOnGlobalLayoutListener
                    val keypadHeight = screenHeight - r.bottom
                    val keyboardVisible = keypadHeight > screenHeight * 0.15
                    bottomBar.visibility =
                        if (keyboardVisible) android.view.View.GONE else android.view.View.VISIBLE
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    // 🔴🎨🔒 TK-নির্দেশ (07.08.2026, scroll_A/B/C মকআপ) — এক-পেজ স্ক্রল।
    // flipper এখন LinearLayout; চিপ চাপলে সেই সেকশনে স্ক্রল হয় (আগের
    // displayedChild নেই)। রোগীর পূর্ণ কার্ড সবসময় উপরে থাকে (সংকুচিত-কার্ড
    // টগল বাদ)। ⛔ সব ফিল্ড/ডেটা-সেভ অপরিবর্তিত।
    private val sectionIds = intArrayOf(
        R.id.secHistory, R.id.secClinical, R.id.secCounsel, R.id.secEstimate, R.id.secPhoto
    )
    private fun showStep(i: Int) {
        currentStep = i
        val scroll = findViewById<ScrollView>(R.id.stepScroll)
        val target = findViewById<android.view.View>(sectionIds.getOrElse(i) { R.id.secHistory })
        if (target != null) scroll.post { scroll.smoothScrollTo(0, target.top) }
        // 🎨 (07.08.2026, প্রুফ-চেহারা) — নম্বর-বৃত্ত: চলতি ধাপ সবুজ+সাদা নম্বর,
        // বাকিগুলো ধূসর। নিচের লেবেলও সেই অনুযায়ী রঙ। (আগে বড় পিলে
        // bg_chip_seg_on_green / bg_chip_seg ব্যবহার হতো।)
        stepChips.forEachIndexed { idx, circle ->
            val active = idx == i
            circle.background = stepCircleBg(active)
            circle.text = (idx + 1).toString()
            circle.setTextColor(if (active) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#9AA6B4"))
            circle.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            stepChipLabels.getOrNull(idx)?.setTextColor(
                if (active) android.graphics.Color.parseColor("#0F766E") else android.graphics.Color.parseColor("#9AA6B4")
            )
        }
        // (এক-পেজ স্ক্রলে btnBack/btnNext isEnabled + smoothScrollTo(0,0)
        //  আর দরকার নেই — স্ক্রল উপরে target.top-এ হয়।)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // 🔵 TK-নির্দেশ (07.08.2026): ঠিকানা দু'লাইনে সাজানো — থানা-চিহ্নের
    // (PS: / P.S / P/S / Thana / থানা / Police Station) ঠিক আগে একটা লাইন-ব্রেক
    // বসে, তাই ১ম লাইনে গ্রাম/পোস্ট, ২য় লাইনে থানা/জেলা। চিহ্ন না পেলে ঠিকানা
    // আগের মতোই এক লাইনে থাকে (কিছু ভাঙে না)। ⛔ সেভ-হওয়া ঠিকানার আসল মান
    // বদলায় না — শুধু পর্দায় দেখানোর সময় ভাঙা হয়।
    private fun formatAddressTwoLines(addr: String): String {
        val markers = listOf("PS:", "P.S", "P/S", "Thana", "থানা", "Police Station")
        var idx = -1
        for (m in markers) {
            val i = addr.indexOf(m, ignoreCase = true)
            if (i > 0 && (idx == -1 || i < idx)) idx = i
        }
        if (idx <= 0) return addr
        val first = addr.substring(0, idx).trim().trimEnd(',').trim()
        val second = addr.substring(idx).trim()
        if (first.isBlank() || second.isBlank()) return addr
        return "$first\n$second"
    }

    private fun bindPatientHeader() {
        val name = RoleSession.currentPatientName.ifBlank { "Patient" }
        // 🔒 খাতার সারি B175 (TK, 30.07.2026): "Reg ID:"-এ raw আইডি (pat_...)
        // দেখাত। এখন `displayId()` — মানুষ-পড়া-যায় কোড থাকলে সেটাই দেখাবে।
        // ⛔ নিচের ক্লাউড-খোঁজাও অক্ষত থাকল — ওটা আগে থেকেই মানুষ-পড়া-যায়
        // কোড (`patientId=eq.`) প্রথমে খোঁজে, তারপর raw আইডি (`id=eq.`) —
        // তাই এই বদলে খোঁজাটা বরং আরও সঠিক হলো, কিছু ভাঙল না।
        val pid = RoleSession.displayId()
        val branch = RoleSession.currentPatientBranch
        // 🔒 TK-LOCKED DESIGN (04.08.2026, ফটো-প্রুফে "লক করুন") — Patient
        // Timeline-এর হেডারের হুবহু একই তথ্য-ক্রম: মোবাইল (কল-বোতাম সহ),
        // নাম, Branch-Disease, Sex-Age, Address, ID।
        // 🔴🎨🔒 B431 — নাম কখনো দু'লাইনে/ব্রেক না হওয়ার জন্য Patient ID-র
        // (FollowUpActivity) প্রমাণিত একই autosize কৌশল, পূর্ণ কার্ড ও
        // সংকুচিত বার — দুই জায়গার নামেই।
        val tvName = findViewById<TextView>(R.id.tvPatientName)
        tvName.text = name
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tvName, 11, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP
        )
        val tvNameMini = findViewById<TextView>(R.id.tvPatientNameMini)
        tvNameMini.text = name
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tvNameMini, 9, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP
        )
        findViewById<TextView>(R.id.tvPatientReg).text = pid.ifBlank { "-" }
        findViewById<TextView>(R.id.tvPatientRegMini).text = pid.ifBlank { "-" }
        findViewById<TextView>(R.id.tvPatientBranchDisease).text = branch.ifBlank { "-" }
        if (pid.isBlank()) return
        lifecycleScope.launch {
            val p = withContext(Dispatchers.IO) {
                try {
                    val enc = java.net.URLEncoder.encode(pid, "UTF-8")
                    var rows = SupabaseClient.fetchList("patients", "patientId=eq.$enc")
                    if (rows.length() == 0) rows = SupabaseClient.fetchList("patients", "id=eq.$enc")
                    if (rows.length() > 0) rows.getJSONObject(0) else null
                } catch (_: Exception) { null }
            } ?: return@launch
            val mobile = p.s("mobile")
            val age = p.s("age")
            val sex = p.s("sex")
            val disease = p.s("disease")
            val address = p.s("address")
            val photo = p.s("photo")
            // 🆕 (07.08.2026) — A4 রিপোর্টের জন্য একই তথ্য মনে রাখা (কোনো নতুন কল নয়)।
            patMobile = mobile; patAge = age; patSex = sex; patDisease = disease; patAddress = address; patPhoto = photo
            val tvMobile = findViewById<TextView>(R.id.tvPatientMobile)
            tvMobile.text = mobile.ifBlank { "—" }
            findViewById<TextView>(R.id.tvPatientMobileMini).text = mobile.ifBlank { "—" }
            findViewById<TextView>(R.id.btnPatientCall).setOnClickListener {
                if (mobile.filter { it.isDigit() }.takeLast(10).length == 10) {
                    CallChooser.open(this@DoctorCheckupActivity, mobile)
                }
            }
            // 🔴🆕🔒 B437 — Doctor Queue-র "📜 History"-র হুবহু একই পথ
            // পুনর্ব্যবহার (fullJourney=true) — নতুন কোনো লজিক না।
            findViewById<TextView>(R.id.btnPatientHistory).setOnClickListener {
                if (mobile.filter { it.isDigit() }.takeLast(10).length == 10) {
                    startActivity(
                        Intent(this@DoctorCheckupActivity, PatientTimelineActivity::class.java)
                            .putExtra("mobile", mobile)
                            .putExtra("fullJourney", true)
                    )
                }
            }
            /* 🔵🔒 V559 (২২.০৮.২০২৬, TK-অনুমোদিত: *"হ্যাঁ"*) — আগে সেভ করা
               চেকআপ ফর্মে ফিরিয়ে আনা।

               এতদিন ফোনে ফিরত না (B437 সারানোর সময় `populate()`-এর ডাক তুলে
               দেওয়া হয়েছিল), অথচ ওয়েবে ফিরত। এখন ওয়েব যেখানে রাখে ঠিক
               সেখান থেকেই পড়া হয় — `patients.doctorFullNote`।

               🔒 B437 যেন না ফেরে: সারিটা এইমাত্র **এই রোগীরই** id দিয়ে আনা
                  হয়েছে (উপরে `patientId=eq.$enc`), তাই অন্য রোগীর তথ্য এখানে
                  আসার পথ নেই। তবু নিচে আরেকবার id মিলিয়ে দেখা হয়।
               ⛔ বাড়তি কোনো query লাগেনি — সারিটা এমনিতেই আনা হচ্ছিল।
               ⛔ ডাক্তার কিছু টাইপ করে ফেলে থাকলে হাত দেওয়া হয় না। */
            try {
                val rowKey = p.s("patientId").ifBlank { p.s("id") }
                val samePatient = rowKey.isNotBlank() &&
                    (rowKey.equals(pid, true) || p.s("id").equals(pid, true))
                // সারিতে ঘরটা সাধারণত JSON হয়ে আসে; কোনো পুরনো সারিতে যদি
                // লেখা (string) হয়ে থাকে, সেটাও পড়ে নেওয়া হয় — নইলে সেই
                // রোগীর তথ্য চুপচাপ ফিরত না।
                val noteObj = p.optJSONObject("doctorFullNote")
                    ?: try {
                        val raw = p.s("doctorFullNote")
                        if (raw.trimStart().startsWith("{")) org.json.JSONObject(raw) else null
                    } catch (_: Throwable) { null }
                if (samePatient && noteObj != null && noteObj.length() > 0 &&
                    etComplaint.text.isNullOrBlank() && !restoredOnce) {
                    restoredOnce = true
                    populate(CheckupNoteJson.fromMap(jsonToMap(noteObj)))
                }
            } catch (_: Throwable) { }

            val branchDisease = listOf(branch, disease).filter { it.isNotBlank() }.joinToString(" - ")
            if (branchDisease.isNotBlank()) findViewById<TextView>(R.id.tvPatientBranchDisease).text = branchDisease
            val tvSexAge = findViewById<TextView>(R.id.tvPatientSexAge)
            val sexAge = listOf(sex, age).filter { it.isNotBlank() }.joinToString("-")
            if (sexAge.isNotBlank()) {
                tvSexAge.text = sexAge
                tvSexAge.visibility = android.view.View.VISIBLE
            } else {
                tvSexAge.visibility = android.view.View.GONE
            }
            /* 🔵 V553 (২২.০৮.২০২৬, TK-নির্দেশ): কাগজের **"Refd. by"** ঘরটা হেডারে।
               ⛔ তথ্যটা **এই একই সারিতেই** আগে থেকে আছে (রেজিস্ট্রেশনের `refBy`
                  ও `refDoctor` — RegistrationActivity.kt:1014,1050) — তাই
                  **Supabase-এ একটাও বাড়তি query নেই**, নতুন কলাম/SQL-ও লাগেনি।
               ⛔ "Dr. Visit" হলে সঙ্গে ডাক্তারের নামটাও দেখায় (দুটোই সেভ করা তথ্য,
                  কিছু বানানো হয়নি)। কিছুই না থাকলে **লাইনটাই দেখায় না** — তাই
                  পুরোনো রোগীর কার্ড আগের মতোই থাকে।
               ⛔ উপরের কোনো লাইন সরানো হয়নি। */
            val refBy = p.s("refBy")
            val refDoctor = p.s("refDoctor")
            val refText = listOf(refBy, refDoctor).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")
            val tvRefBy = findViewById<TextView>(R.id.tvPatientRefBy)
            if (refText.isNotBlank()) {
                tvRefBy.text = "Ref By: $refText"
                tvRefBy.visibility = android.view.View.VISIBLE
            } else {
                tvRefBy.visibility = android.view.View.GONE
            }

            val tvAddress = findViewById<TextView>(R.id.tvPatientAddress)
            if (address.isNotBlank()) {
                // 🔵 TK-নির্দেশ (07.08.2026): ঠিকানা দু'লাইনে — ১ম লাইনে গ্রাম/
                // পোস্ট, ২য় লাইনে থানা/জেলা। (আগে: এক লাইনেই বসত, tvAddress.text = address)
                tvAddress.text = formatAddressTwoLines(address)
                tvAddress.visibility = android.view.View.VISIBLE
            } else {
                tvAddress.visibility = android.view.View.GONE
            }
            /* 🔵🔒 V539 (২২.০৮.২০২৬, TK-নির্দেশ): *"occupation ফর্ম থেকে সরিয়ে উপরে
               বয়সের পাশে রাখুন।"* ⇒ "Male-55 · Business"। ওই লেখাটায় চাপ দিলেই
               পেশা বাছার তালিকা খোলে, আর সেটা ফর্মের সেই **পুরোনো ঘরেই** বসে
               (`spOccupation`, শুধু লুকানো) — তাই সেভ/পড়া কিছুই বদলায়নি। */
            refreshSexAgeOccupation()
            /* ⛔ লুকানো (GONE) Spinner-এ `performClick()` ভরসাযোগ্য নয় — তালিকা
               নাও খুলতে পারে। তাই নিজেরই একটা তালিকা, আর বাছাইটা সেই
               পুরোনো Spinner-এই বসে (সেভের পথ এক অক্ষরও বদলায়নি)। */
            tvSexAge.setOnClickListener { askOccupation() }
            spOccupation.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                    refreshSexAgeOccupation()
                }
                override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
            })
            if (photo.isNotBlank()) {
                val bmp = PhotoUtils.decodeDataUrl(photo)
                if (bmp != null) {
                    val iv = findViewById<ImageView>(R.id.ivPatientPhoto)
                    iv.setImageBitmap(bmp)
                    iv.visibility = android.view.View.VISIBLE
                    findViewById<TextView>(R.id.ivPatientPhotoBlank).visibility = android.view.View.GONE
                }
            }
            wireCheckupPhotoRotate()   // 🔵 V539: ছবিতে তিনবার চাপ = ঘুরবে ও সেভ হবে
            autofillFromRegistration(p)
        }
    }

    /** Web parity (doctorCheck): pre-fill Basic History + Previous Treatment
     *  from the Registration record. Only fills fields still empty, so a prior
     *  saved check-up or the doctor's own edits are never overwritten. */
    private fun autofillFromRegistration(p: org.json.JSONObject) {
        fun first(vararg keys: String): String {
            for (k in keys) {
                val v = p.optString(k)
                if (v.isNotBlank() && v != "null") return v
            }
            return ""
        }
        fun fill(et: EditText, value: String) {
            if (et.text.isNullOrBlank() && value.isNotBlank()) et.setText(value)
        }
        // 🔴 TK-REPORTED (04.08.2026, ছবিসহ — Chief Complaint/Duration/
        // Occupation Registration সেভ হওয়ার সময় অটোফিল হওয়ার কথা ছিল,
        // হয়নি): Occupation আগে EditText ছিল, এখন Spinner — তাই এখানে
        // পুরনো fill(etOccupation,...) কাজ করত না, কম্পাইলই হতো না।
        // এখন নতুন fillSpinner() — শুধু তখনই বসে যখন Spinner এখনো
        // ফাঁকা/প্রথম-অপশনে আছে (ডাক্তার নিজে কিছু বেছে না থাকলে),
        // Registration-এর সেভ করা মান তালিকায় থাকলে তবেই। ⛔ ডাক্তার
        // নিজে কিছু বেছে থাকলে (বা তালিকায় নেই এমন মান হলে) কিছুই
        // বদলায় না।
        fun fillSpinner(sp: Spinner, options: List<String>, value: String) {
            if (value.isBlank()) return
            if (sp.selectedItemPosition > 0) return
            val idx = options.indexOfFirst { it.equals(value, ignoreCase = true) }
            if (idx > 0) sp.setSelection(idx)
        }
        fill(etComplaint, first("complaint", "diagnosis", "disease"))
        fill(etDuration, first("sinceWhen", "duration"))
        fillSpinner(spOccupation, occupationOptions, first("occupation"))
        fill(etPrevTreatment, first("previousTreatment", "prevTreatment", "medicalHistory"))
        // 🔵 B622: Result/Spent/Treatment Duration ঘর বাদ — এদের আর লোড করা হয় না।
    }

    private fun showPhotoDialog(target: Int) {
        currentPhotoTarget = target
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "Treatment Photo"))
            .setItems(arrayOf("📷 Camera", "🖼️ Gallery")) { _, which ->
                if (which == 0) launchCameraWithPermission() else pickImage.launch("image/*")
            }
            .setNegativeButton("Cancel", null)
            .show().also { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) }
    }

    // BUG FIX (audit): CAMERA is a runtime-dangerous permission. Since the app
    // manifest declares android.permission.CAMERA, launching TakePicture()
    // without first holding the granted permission throws a SecurityException
    // and crashes this screen on first use. Registration already had this
    // check (launchCameraWithPermission); Doctor Check-up was missing it.
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }

    private fun launchCameraWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) openCamera() else requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        try {
            val dir = java.io.File(cacheDir, "images").apply { mkdirs() }
            val file = java.io.File(dir, "checkup_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraPhotoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera could not open", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraPhotoUri
            if (success && uri != null) applyPhoto(uri)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) applyPhoto(uri)
        }

    private fun applyPhoto(uri: Uri) {
        lifecycleScope.launch {
            val dataUrl = withContext(Dispatchers.IO) { PhotoUtils.encodeResized(this@DoctorCheckupActivity, uri) }
            if (dataUrl == null) {
                Toast.makeText(this@DoctorCheckupActivity, "Could not get the image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            when (currentPhotoTarget) {
                0 -> { beforePhotoData = dataUrl; showThumb(ivBeforePhoto, dataUrl) }
                1 -> { duringPhotoData = dataUrl; showThumb(ivDuringPhoto, dataUrl) }
                else -> { afterPhotoData = dataUrl; showThumb(ivAfterPhoto, dataUrl) }
            }
        }
    }

    private fun showThumb(iv: ImageView, dataUrl: String) {
        val bmp = PhotoUtils.decodeDataUrl(dataUrl)
        if (bmp != null) iv.setImageBitmap(bmp)
    }

    /** TK APPROVED (2026-07-15): each option now renders as a colourful
     *  rounded card (icon + label + tinted checkbox) instead of a plain
     *  default CheckBox, with a light press animation on tap. The CheckBox
     *  objects themselves (added to `into`) are completely unchanged --
     *  checkedText(), enable/disable, and restore-on-load all keep working
     *  exactly as before, since they only ever read isChecked/text. */
    /**
     * @param compact 🔴 V501 (TK-নির্দেশ ২১.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত) —
     *        `true` হলে চিপগুলো Quick Action-এর বোতামের হুবহু এক মাপে বসে
     *        (উচ্চতা ৩২dp · লেখা ১১.৫sp · কোণ ৭dp)। শুধু Investigation-এ
     *        ব্যবহার হয়; Visual/DRE আগের মতোই (ডিফল্ট `false`)।
     *        ⛔ টিকের মান · `tag` · সেভ-লজিক কিছুই বদলায় না — শুধু মাপ ও রং।
     */
    private fun buildChecks(
        group: LinearLayout, options: List<String>, into: MutableList<CheckBox>,
        icons: Map<String, String> = emptyMap(), accentHex: String = "#0B4F2A",
        bnLabels: Map<String, String> = emptyMap(),
        compact: Boolean = false
    ) {
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        val accent = android.graphics.Color.parseColor(accentHex)
        val lightBg = android.graphics.Color.argb(28, android.graphics.Color.red(accent), android.graphics.Color.green(accent), android.graphics.Color.blue(accent))
        var row: LinearLayout? = null
        options.forEachIndexed { idx, label ->
            if (idx % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = dp(if (compact) 4 else 6) }
                }
                group.addView(row)
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                // 🎨 (07.08.2026, প্রুফ-হুবহু) — পিলের রং নিচে paintChip()-এ বসে
                // (বাছাই-অবস্থা অনুযায়ী ধূসর/সবুজ), তাই এখানে স্থির background নেই।
                if (compact) setPadding(dp(2), 0, dp(2), 0)
                else setPadding(dp(10), dp(8), dp(10), dp(8))
                val lp = LinearLayout.LayoutParams(
                    0,
                    if (compact) dp(32) else LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                lp.setMargins(dp(if (compact) 3 else 4), 0, dp(if (compact) 3 else 4), 0)
                layoutParams = lp
                if (compact) gravity = android.view.Gravity.CENTER
                isClickable = true; isFocusable = true
            }
            // 🔴🔒 TK-নির্দেশ (06.08.2026): প্রতিটা চেকবক্সের পাশের ছোট
            // ইমোজি/ডট (রঙিন গোল আইকন, বা আইকন না থাকলে "•") সম্পূর্ণ বাদ
            // — "এগুলো কেন থাকবে" বলার পরে। ⛔ `icons`/`visualIcons`/
            // `dreIcons`/`investigationIcons` ম্যাপ ও প্যারামিটার মোছা হয়নি
            // (dead code হিসেবে থাকল, পরে দরকার হলে ফেরানো যাবে) — শুধু
            // এখানে ব্যবহার বন্ধ করা হলো, buildChecks তিনটে গ্রুপেই
            // (Visual/DRE/Investigation) একই পরিবর্তন প্রযোজ্য।
            val cb = CheckBox(this).apply {
                // 🔴🔴🔒 TK-নির্দেশ (06.08.2026, দ্বিতীয়বার স্পষ্ট করে বলার
                // পরে — "প্রতিবার পাইলস লেখার পাশে একটা ডট কেন") — এতদিন
                // ধরে থাকা " · " ফুটকি-চিহ্নটাই আসল সমস্যা ছিল (05.08.2026-এ
                // ভুল করে শুধু "Fistula Opening"/"Bleeding"-এ বাদ দেওয়া
                // হয়েছিল, বাকি সবগুলোতে থেকে গিয়েছিল)। এখন Visual/DRE/
                // Investigation — তিনটে গ্রুপের **সব** চেকবক্সেই ইংরেজি ও
                // বাংলা নতুন লাইনে (উপর-নিচে), " · " আর কোথাও নেই।
                text = if (compact) label
                       else bnLabels[label]?.let { bn -> "$label\n$bn" } ?: label
                tag = label
                textSize = if (compact) 11.5f else 12.5f
                if (compact) {
                    gravity = android.view.Gravity.CENTER
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                buttonDrawable = null   // 🎨 (07.08.2026, প্রুফ-হুবহু) চেকবক্সের বর্গ-চিহ্ন লুকানো
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            // 🎨 (07.08.2026, প্রুফ-হুবহু, TK-অনুমোদিত) — প্রুফের ধূসর পিল: বাছলে সবুজ
            // ভরাট+সবুজ লেখা, নইলে ধূসর বর্ডার+ধূসর লেখা (লোড ও টগল দুটোতেই চলে)।
            // ⛔ cb.tag / isChecked / checkedText সেভ-লজিক অপরিবর্তিত।
            run {
                val greyStroke = android.graphics.Color.parseColor("#D0D5DD")
                val greyText = android.graphics.Color.parseColor("#475467")
                val greenLine = android.graphics.Color.parseColor("#12805C")
                val greenFill = android.graphics.Color.parseColor("#EAF7F0")
                fun paintChip(on: Boolean) {
                    card.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (on) greenFill else android.graphics.Color.TRANSPARENT)
                        cornerRadius = dp(if (compact) 7 else 16).toFloat()
                        setStroke(dp(1), if (on) greenLine else greyStroke)
                    }
                    cb.setTextColor(if (on) greenLine else greyText)
                    cb.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
                paintChip(cb.isChecked)
                cb.setOnCheckedChangeListener { _, isC -> paintChip(isC) }
            }
            card.addView(cb)
            // Light press-bounce animation on tap (same proven pattern already
            // used for the Clinical Document grid elsewhere in the app) --
            // the CheckBox's own default toggle behaviour is untouched.
            card.setOnClickListener {
                card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }.start()
                cb.toggle()
            }
            row!!.addView(card)
            into.add(cb)
        }
    }

    /** 🔵 V541: এখন যেটা সেভ হয়, আর পুরোনো যেগুলোও মেনে নিতে হবে। */
    private val FISTULA_TAG_NOW = "Fistula Per CM"
    private val FISTULA_TAGS = listOf("Fistula Per CM", "Fistula Per Inch", "Fistula Per ইঞ্চি")

    private fun checkedText(list: List<CheckBox>): String =
        list.filter { it.isChecked }.joinToString(", ") { (it.tag as? String) ?: it.text.toString() }

    /** Best-effort: finds this patient's Supabase row (by patientId, falling
     *  back to the row id) and sets doctorComplete=true so they drop out of the
     *  Doctor Queue. Runs on an IO thread; swallows all errors so it can never
     *  break the check-up save. */
    private fun markDoctorComplete(patientKey: String) {
        if (patientKey.isBlank()) return
        try {
            val enc = java.net.URLEncoder.encode(patientKey, "UTF-8")
            var rows = SupabaseClient.fetchList("patients", "patientId=eq.$enc")
            if (rows.length() == 0) rows = SupabaseClient.fetchList("patients", "id=eq.$enc")
            if (rows.length() == 0) return
            val id = rows.getJSONObject(0).optString("id")
            if (id.isBlank()) return
            /* 🔵 V559: doctorComplete-এর সাথে একই কলে চেকআপের পুরো লেখাটাও
               বসে যায় — বাড়তি কোনো নেটওয়ার্ক কল নয়। ওয়েব যা লিখে রেখেছিল
               তার উপরে বসানো হয় (merge), তাই ওয়েবের কোনো ঘর হারায় না। */
            val body = org.json.JSONObject().put("doctorComplete", true)
            try {
                val row0 = rows.getJSONObject(0)
                val oldNote = row0.optJSONObject("doctorFullNote")
                    ?: try {
                        val raw = row0.optString("doctorFullNote", "")
                        if (raw.trimStart().startsWith("{")) org.json.JSONObject(raw) else null
                    } catch (_: Throwable) { null }
                val rec = pendingNote
                if (rec != null) body.put("doctorFullNote",
                    mapToJson(CheckupNoteJson.merge(jsonToMap(oldNote), rec)))
            } catch (_: Throwable) { }
            SupabaseClient.updateById("patients", id, body)
        } catch (_: Exception) {
        }
    }

    private fun collect(): CheckupRecord = CheckupRecord(
        complaint = etComplaint.text?.toString().orEmpty(),
        duration = etDuration.text?.toString().orEmpty(),
        // V455 (18.08.2026): acuteChronic ঘর বাদ — মডেলের ডিফল্ট "" থাকে।
        occupation = occupationOptions.getOrElse(spOccupation.selectedItemPosition) { "" }
            .let { if (it == "Choose Occupation") "" else it },
        prevTreatment = etPrevTreatment.text?.toString().orEmpty(),
        patientSaid = findViewById<android.widget.EditText>(R.id.etPatientSaid).text?.toString().orEmpty(),   // 🔵 V539
        symptomHistory = collectSymptomHistory(),   // 🔵 V554
        historyDetail = collectHistoryDetail(),     // 🔵 V555
        lifestyle = collectLifestyle(),             // 🔵 V556
        probableDisease = CounselModel.DISEASES.getOrElse(   // 🔵 V557
            findViewById<android.widget.Spinner>(R.id.spProbableDisease).selectedItemPosition) { CounselModel.PICK_NONE },
        timeAsked = CounselModel.timeAsked(
            findViewById<android.widget.EditText>(R.id.etTimeAsked).text?.toString().orEmpty(),
            CounselModel.UNITS.getOrElse(findViewById<android.widget.Spinner>(R.id.spTimeAskedUnit).selectedItemPosition) { "Days" }),
        anatomy = collectAnatomy(),                 // 🔵 V558
        dre = checkedText(dreChecks),               // 🔵 V556 (ফেরানো)
        dreOther = findViewById<android.widget.EditText>(R.id.etDreOther).text?.toString().orEmpty(),
        // 🔵 B622 (11.08.2026): Result/Spent/Treatment Duration ঘর বাদ — মডেলের ডিফল্ট "" থাকে।
        visual = checkedText(visualChecks),
        // V455 (18.08.2026): visualOther · dre · dreOther · otherFindings ঘর বাদ — মডেলের ডিফল্ট থাকে।
        grade = gradeOptions.getOrElse(spGrade.selectedItemPosition) { "" },   // 🔵 V539: এখন Internal Piles-এর Grade
        proctoscopy = findViewById<android.widget.EditText>(R.id.etProctoscopy).text?.toString().orEmpty(),   // 🔵 V539
        onProbing = etOnProbing.text?.toString().orEmpty(),
        investigation = checkedText(investigationChecks),
        treatmentPlan = checkedText(treatmentChecks()),
        amtPerPiles = etAmtPerPiles.text?.toString().orEmpty(),
        amtFistulaPerInch = etAmtFistulaInch.text?.toString().orEmpty(),
        amtKsharSutra = etAmtKsharSutra.text?.toString().orEmpty(),
        counselling = etCounselling.text?.toString().orEmpty(),
        estimatedCost = etEstimatedCost.text?.toString().orEmpty(),
        recoveryTime = etRecoveryTime.text?.toString().orEmpty(),
        // 🔵 B622 (11.08.2026): "Advance Payment to be Done" ঘর বাদ — মডেলের ডিফল্ট "" থাকে।
        // V455 (18.08.2026): patientDecision · decisionRemark ঘর বাদ — মডেলের ডিফল্ট "" থাকে।
        beforePhoto = beforePhotoData,
        duringPhoto = duringPhotoData,
        afterPhoto = afterPhotoData,
        // V455 (18.08.2026): documents ঘর বাদ — মডেলের ডিফল্ট "" থাকে।
        savedAt = System.currentTimeMillis(),
        savedByRole = RoleSession.currentRole
    )

    // 🆕 TK-নির্দেশ (04.08.2026): Treatment Plan-এর ৬টা Tick-বক্স একসাথে —
    // visualChecks/dreChecks-এর মতোই checkedText()-এ ব্যবহারের জন্য।
    private fun treatmentChecks(): List<CheckBox> =
        listOf(cbTxPerPiles, cbTxFistulaInch, cbTxMachine, cbTxKsharSutra, cbTxLis, cbTxInjection)

    private fun populate(r: CheckupRecord) {
        etComplaint.setText(r.complaint)
        etDuration.setText(r.duration)
        // V455 (18.08.2026): acuteChronic populate বাদ (ঘর নেই); পুরনো রেকর্ডে
        // মান থাকলেও এখন আর কোথাও দেখানো হয় না, সেভ-করা ডেটা ছোঁয়া হয়নি।
        val oi = occupationOptions.indexOf(r.occupation)
        if (oi >= 0) spOccupation.setSelection(oi)
        etPrevTreatment.setText(r.prevTreatment)
        findViewById<android.widget.EditText>(R.id.etPatientSaid).setText(r.patientSaid)   // 🔵 V539
        applySymptomHistory(r.symptomHistory)   // 🔵 V554
        applyHistoryDetail(r.historyDetail)     // 🔵 V555
        applyLifestyle(r.lifestyle)             // 🔵 V556
        // 🔵 V557
        val di = CounselModel.DISEASES.indexOf(r.probableDisease)
        findViewById<android.widget.Spinner>(R.id.spProbableDisease).setSelection(if (di >= 0) di else 0)
        val (tAmt, tUnit) = CounselModel.splitTimeAsked(r.timeAsked)
        findViewById<android.widget.EditText>(R.id.etTimeAsked).setText(tAmt)
        val ui = CounselModel.UNITS.indexOf(tUnit)
        if (ui >= 0) findViewById<android.widget.Spinner>(R.id.spTimeAskedUnit).setSelection(ui)
        lastSavedCost = r.estimatedCost   // 🔔 V557: এই খরচেই আগে নোটিশ গেছে
        applyAnatomy(r.anatomy)                 // 🔵 V558
        val dreSel = r.dre.split(", ").map { it.trim() }   // 🔵 V556
        dreChecks.forEach { it.isChecked = dreSel.contains((it.tag as? String) ?: it.text.toString()) }
        findViewById<android.widget.EditText>(R.id.etDreOther).setText(r.dreOther)
        // 🔵 B622: Result/Spent/Treatment Duration ঘর বাদ।
        val vis = r.visual.split(", ").map { it.trim() }
        visualChecks.forEach { it.isChecked = vis.contains((it.tag as? String) ?: it.text.toString()) }
        // V455 (18.08.2026): visualOther/dre*/otherFindings populate বাদ (ঘর নেই)।
        val gi = gradeOptions.indexOf(r.grade)
        if (gi >= 0) spGrade.setSelection(gi)
        findViewById<android.widget.EditText>(R.id.etProctoscopy).setText(r.proctoscopy)   // 🔵 V539
        refreshInternalGradeLabel()   // 🔵 V539: Internal Piles-এর পাশে Grade দেখানো
        etOnProbing.setText(r.onProbing)
        val inv = r.investigation.split(", ").map { it.trim() }
        investigationChecks.forEach { it.isChecked = inv.contains((it.tag as? String) ?: it.text.toString()) }
        val tx = r.treatmentPlan.split(", ").map { it.trim() }
        /* 🔵 V541: Fistula-র বেলায় পুরোনো লেখাগুলোও মেনে নেওয়া হয়, তাই
           আগে সেভ হওয়া চেকআপে টিকটা আগের মতোই থাকে। ⛔ বাকি প্রতিটা
           চেকবক্সে মিলের নিয়ম **হুবহু আগের**। */
        treatmentChecks().forEach { cb ->
            val tag = (cb.tag as? String) ?: cb.text.toString()
            cb.isChecked = if (tag == FISTULA_TAG_NOW) tx.any { it in FISTULA_TAGS } else tx.contains(tag)
        }
        if (r.amtPerPiles.isNotBlank()) etAmtPerPiles.setText(r.amtPerPiles)
        if (r.amtFistulaPerInch.isNotBlank()) etAmtFistulaInch.setText(r.amtFistulaPerInch)
        if (r.amtKsharSutra.isNotBlank()) etAmtKsharSutra.setText(r.amtKsharSutra)
        etCounselling.setText(r.counselling)
        etEstimatedCost.setText(r.estimatedCost)
        etRecoveryTime.setText(r.recoveryTime)
        // 🔵 B622: "Advance Payment to be Done" ঘর বাদ।
        // V455 (18.08.2026): patientDecision/decisionRemark/documents populate বাদ (ঘর নেই)।
        beforePhotoData = r.beforePhoto
        duringPhotoData = r.duringPhoto
        afterPhotoData = r.afterPhoto
        if (r.beforePhoto.isNotBlank()) showThumb(ivBeforePhoto, r.beforePhoto)
        if (r.duringPhoto.isNotBlank()) showThumb(ivDuringPhoto, r.duringPhoto)
        if (r.afterPhoto.isNotBlank()) showThumb(ivAfterPhoto, r.afterPhoto)
    }

    /**
     * 🔵🔒 V539 (২২.০৮.২০২৬, TK-নির্দেশ): *"Internal Piles — এখানে চাপ দিলে
     * Grade 1/2/3/4 আসবে, যেটা সিলেক্ট করব সেটাই থাকবে।"*
     *
     * ⛔ Grade জমা হয় **পুরোনো সেই `grade` ঘরেই** (TK-এর নিজের উত্তর: *"হ্যাঁ,
     *    একই — পুরোনো ঘরটাই ব্যবহার করুন"*), মানও হুবহু আগের ("Grade I"…)।
     *    ⇒ **পুরোনো প্রতিটা রেকর্ড আগের মতোই ঠিক দেখাবে।**
     * ⛔ চেকবক্সের **সেভ-হওয়া লেখা বদলায়নি** — `visual`-এ আগের মতোই শুধু
     *    "Internal Piles" যায়, Grade আলাদা ঘরে। তাই ছাপা/পুরোনো পড়া কিছুই ভাঙে না।
     */
    /** 🔵 V539: হেডারের "Male-55" লাইনে পেশাও যোগ করে। ⛔ পেশা ফাঁকা হলে
     *  লাইনটা **হুবহু আগের মতোই** থাকে। */
    /** 🔵 V539: পেশা বাছার তালিকা — বাছাই বসে পুরোনো `spOccupation`-এই। */
    private fun askOccupation() {
        // 🔵 V540: এখানেও প্রজেক্টের সেই একই প্রিমিয়াম পিকার।
        SpinnerPicker.open(spOccupation, "CHOOSE OCCUPATION", hidePlaceholder = true)
    }

    private fun refreshSexAgeOccupation() {
        val tv = findViewById<TextView>(R.id.tvPatientSexAge) ?: return
        val sexAge = listOf(patSex, patAge).filter { it.isNotBlank() }.joinToString("-")
        val occ = occupationOptions.getOrElse(spOccupation.selectedItemPosition) { "" }
            .let { if (it == "Choose Occupation") "" else it }
        val text = listOf(sexAge, occ).filter { it.isNotBlank() }.joinToString(" · ")
        if (text.isNotBlank()) { tv.text = text; tv.visibility = android.view.View.VISIBLE }
        else tv.visibility = android.view.View.GONE
    }

    /* ═══════════════════════════════════════════════════════════════════
       🔵🔒 V554 (২২.০৮.২০২৬) — কাগজের *"রোগী এসে প্রথমে কি কি সমস্যার কথা বললেন?"*
       TK-অনুমোদিত ডেমো: **সাজ "গ"** (কাগজের মতো সারি) · **শুরুতে টিক** ·
       ব্যথার **তীব্র/মৃদু এক লাইনেই** · শেষে **"এছাড়া অন্য কিছু"** বাক্স।

       ⛔ সারির নাম/ক্রম/একক — সব `SymptomHistoryModel`-এ, **এক জায়গাতেই**।
       ⛔ "কবে থেকে?"-র ঘর দুটো **রেজিস্ট্রেশনের হুবহু একই জোড়া** — সংখ্যার ঘর
          (`inputType=number`) + Days/Months/Years, আর তালিকাটা প্রজেক্টের নিজের
          প্রিমিয়াম পিকারেই খোলে (`SpinnerPicker.attach`)। নতুন নকশা বানানো হয়নি।
       ⛔ উপরের Chief Complaint ও Duration ঘর দুটো **সরানো হয়নি**।
       ═══════════════════════════════════════════════════════════════ */
    private val symptomTicks = LinkedHashMap<String, android.widget.CheckBox>()
    private val symptomAmounts = LinkedHashMap<String, android.widget.EditText>()
    private val symptomUnits = LinkedHashMap<String, android.widget.Spinner>()
    private val symptomSeverity = LinkedHashMap<String, MutableList<TextView>>()

    private fun symDp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun symChipBg(on: Boolean): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = symDp(20).toFloat()
            setColor(android.graphics.Color.parseColor(if (on) "#EAFBF0" else "#FFFFFF"))
            setStroke(symDp(1), android.graphics.Color.parseColor(if (on) "#BFE9CE" else "#DBE2EA"))
        }

    private fun paintSeverity(key: String) {
        val chips = symptomSeverity[key] ?: return
        chips.forEach { chip ->
            val on = (chip.tag as? String) == (chip.getTag(R.id.symptomGroup) as? String)
            chip.background = symChipBg(on)
            chip.setTextColor(android.graphics.Color.parseColor(if (on) "#0B4F2A" else "#93A1B2"))
        }
    }

    private fun currentSeverity(key: String): String {
        val chips = symptomSeverity[key] ?: return ""
        val chosen = chips.firstOrNull()?.getTag(R.id.symptomGroup) as? String
        return chosen.orEmpty()
    }

    private fun setSeverity(key: String, value: String) {
        symptomSeverity[key]?.forEach { it.setTag(R.id.symptomGroup, value) }
        paintSeverity(key)
    }

    private fun buildSymptomRows() {
        val box = findViewById<android.widget.LinearLayout>(R.id.symptomGroup) ?: return
        box.removeAllViews()
        symptomTicks.clear(); symptomAmounts.clear(); symptomUnits.clear(); symptomSeverity.clear()
        for (line in SymptomHistoryModel.LINES) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, symDp(3), 0, symDp(3))
            }
            val cb = android.widget.CheckBox(this)
            row.addView(cb)
            symptomTicks[line.key] = cb

            row.addView(TextView(this).apply {
                text = line.label
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#10223A"))
                maxLines = 2
                val lp = android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginEnd = symDp(6)
                layoutParams = lp
            })

            if (line.severity) {
                val chips = ArrayList<TextView>()
                for (sev in SymptomHistoryModel.SEVERITY) {
                    val chip = TextView(this).apply {
                        text = sev
                        textSize = 11.5f
                        tag = sev
                        setPadding(symDp(10), symDp(3), symDp(10), symDp(3))
                        isClickable = true
                        val lp = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.marginStart = symDp(6)
                        layoutParams = lp
                    }
                    chip.setOnClickListener {
                        // একই চিপে আবার চাপ দিলে বাছাইটা উঠে যায়
                        setSeverity(line.key, if (currentSeverity(line.key) == sev) "" else sev)
                    }
                    chips.add(chip)
                    row.addView(chip)
                }
                symptomSeverity[line.key] = chips
                setSeverity(line.key, "")
            }

            // ২. TK-এর নির্দেশ: *"রেজিস্ট্রেশন ফর্মে যেমন ২, ৩, ৪ তার পাশে বক্স
            //    থাকবে ... ঠিক এখানেও তেমনি রাখবেন"* — তাই সংখ্যার ঘরটা
            //    রেজিস্ট্রেশনের মতোই বক্স, শুধু নিচে একটা দাগ নয়।
            val amount = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setSingleLine(true)
                setBackgroundResource(R.drawable.bg_input_field)
                setPadding(symDp(6), symDp(6), symDp(6), symDp(6))
                layoutParams = android.widget.LinearLayout.LayoutParams(symDp(56), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            row.addView(amount)
            symptomAmounts[line.key] = amount

            val unit = android.widget.Spinner(this).apply {
                adapter = ArrayAdapter(this@DoctorCheckupActivity, R.layout.item_docnote_spinner, SymptomHistoryModel.UNITS)
                setBackgroundResource(R.drawable.bg_input_field_picker)
                setPadding(paddingStart, paddingTop, symDp(28), paddingBottom)
                val lp = android.widget.LinearLayout.LayoutParams(symDp(104), android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginStart = symDp(6)
                layoutParams = lp
            }
            row.addView(unit)
            symptomUnits[line.key] = unit
            /* ⛔ এখানে `hidePlaceholder` **নয়** — "Days" একটা সত্যিকারের বাছাই,
                  লুকিয়ে দিলে ওটা আর বাছাই করা যেত না। */
            SpinnerPicker.attach(unit, "কবে থেকে?")

            box.addView(row)
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
       🔵🔒 V555 (২২.০৮.২০২৬, TK-অনুমোদিত ডেমো) — কাগজের **ভাগ ৩**: চারটে "ইতিহাস"।
       TK-এর নির্দেশ হুবহু: **টিকের জিনিস পাশাপাশি চিপ**, **লেখার জিনিস বক্স**,
       আর **প্রতিটা প্রশ্নে একাধিক উত্তর** বাছা যাবে।
       ⛔ চিপ পাশাপাশি বসানোর নিয়ম প্রজেক্টের নিজের `buildChecks`-এর ধরনেই
          (দুটো করে এক সারিতে); শুধু লম্বা লেখা একাই পুরো সারি পায় — নইলে ফোনের
          পর্দায় কেটে যেত। নিয়মটা `HistoryDetailModel.rowsFor()`-এ, তাই চালিয়ে যাচাই করা।
       ⛔ কাগজের একটাও শব্দ বদলানো হয়নি।
       ═══════════════════════════════════════════════════════════════ */
    private val historyChips = LinkedHashMap<String, MutableList<TextView>>()

    private fun histChipBg(on: Boolean): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = symDp(20).toFloat()
            setColor(android.graphics.Color.parseColor(if (on) "#EAFBF0" else "#FFFFFF"))
            setStroke(symDp(1), android.graphics.Color.parseColor(if (on) "#0B4F2A" else "#DBE2EA"))
        }

    private fun paintHistoryChip(chip: TextView) {
        val on = chip.getTag(R.id.historyDetailGroup) == true
        chip.background = histChipBg(on)
        chip.setTextColor(android.graphics.Color.parseColor(if (on) "#0B4F2A" else "#7C8A9C"))
        chip.setTypeface(chip.typeface, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun buildHistoryDetailRows() {
        val box = findViewById<android.widget.LinearLayout>(R.id.historyDetailGroup) ?: return
        box.removeAllViews()
        historyChips.clear()
        for (group in HistoryDetailModel.GROUPS) {
            box.addView(TextView(this).apply {
                text = group.title
                textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#0B2B59"))
                setPadding(0, symDp(10), 0, symDp(4))
            })
            for (q in group.questions) {
                if (q.label.isNotBlank()) box.addView(TextView(this).apply {
                    text = q.label
                    textSize = 11.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    setPadding(0, symDp(5), 0, symDp(3))
                })
                val field = HistoryDetailModel.fieldKey(group, q)
                val chips = ArrayList<TextView>()
                for (rowOptions in HistoryDetailModel.rowsFor(q.options)) {
                    val row = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        val lp = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.topMargin = symDp(4)
                        layoutParams = lp
                    }
                    for ((i, opt) in rowOptions.withIndex()) {
                        val chip = TextView(this).apply {
                            text = opt
                            textSize = 12f
                            gravity = android.view.Gravity.CENTER
                            setPadding(symDp(10), symDp(6), symDp(10), symDp(6))
                            isClickable = true
                            setTag(R.id.historyDetailGroup, false)
                            tag = opt
                            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            if (i > 0) lp.marginStart = symDp(6)
                            layoutParams = lp
                        }
                        // TK: প্রতিটা প্রশ্নে একাধিক উত্তর — তাই চাপে শুধু নিজেরটাই বদলায়
                        chip.setOnClickListener {
                            chip.setTag(R.id.historyDetailGroup, chip.getTag(R.id.historyDetailGroup) != true)
                            paintHistoryChip(chip)
                        }
                        paintHistoryChip(chip)
                        chips.add(chip)
                        row.addView(chip)
                    }
                    box.addView(row)
                }
                historyChips[field] = chips
            }
        }
    }

    /* ═══ 🔵🔒 V556 (TK-অনুমোদিত ডেমো) — কাগজের ভাগ ৪-এর নতুন অংশ।
       ⛔ চিপ বসানো, একাধিক বাছাই, লেখার বক্স — সবই ভাগ ৩-এর হুবহু একই নিয়মে।
       ⛔ জল-এর ঘরটা সংখ্যার, পাশে "লিটার" — কাগজে যেমন "—— লিটার"। ═══ */
    private val lifestyleChips = LinkedHashMap<String, MutableList<TextView>>()

    /* 🔔 V557: গতবার যে খরচে নোটিশ গিয়েছিল। এটা ধরেই ঠিক হয় নতুন নোটিশ যাবে
       কি না — নইলে প্রতিবার Save-এ স্টাফের ঘণ্টা অকারণে বাজত। */
    private var lastSavedCost: String = ""

    // ══════════════════════════════════════════════════════════════════
    // 🔵🔒 V558 (২২.০৮.২০২৬, TK-অনুমোদিত) — রোগের ছবি
    //
    // TK-এর কথা: *"ডাক্তার চাইলে যে কোন ফটোর উপর যেন সেই কাজটা করতে
    // পারে"* এবং *"মাংসের উপরে আঙুল দিয়ে টান দিলে যেন মাংস বেড়ে যায়"*।
    //
    // উপরে ছবি বাছার সারি · মাঝে আঁকার পর্দা · নিচে কাজের বোতাম।
    // ⛔ নতুন কলাম বা SQL লাগেনি — লেখাটা চেকআপের সাথেই জমা হয়।
    // ══════════════════════════════════════════════════════════════════

    private var anatomyView: AnatomyView? = null
    private val anatomyThumbs = mutableListOf<android.widget.ImageView>()
    private val anatomyToolChips = mutableListOf<TextView>()

    /** ছবির নাম → ফোনের ভিতরের ছবি। না পেলে ০, তখন ছবিটা বাদ যায়। */
    private fun anatomyResId(key: String): Int = try {
        resources.getIdentifier(key, "drawable", packageName)
    } catch (_: Throwable) { 0 }

    private fun buildAnatomyBoard() {
        val holder = findViewById<android.widget.FrameLayout>(R.id.anatomyHolder) ?: return
        val strip = findViewById<android.widget.LinearLayout>(R.id.anatomyStrip) ?: return
        val tools = findViewById<android.widget.LinearLayout>(R.id.anatomyTools) ?: return

        val view = AnatomyView(this)
        anatomyView = view
        holder.removeAllViews()
        holder.addView(view, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

        // ── ছবি বাছার সারি ──
        strip.removeAllViews(); anatomyThumbs.clear()
        for (pic in AnatomyModel.PICTURES) {
            val resId = anatomyResId(pic.key)
            if (resId == 0) continue                     // ছবিটা নেই — চুপচাপ বাদ
            val img = android.widget.ImageView(this).apply {
                setImageResource(resId)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                tag = pic.key
                contentDescription = pic.label
                val lp = android.widget.LinearLayout.LayoutParams(symDp(62), symDp(62))
                lp.setMargins(0, 0, symDp(6), 0)
                layoutParams = lp
                setPadding(symDp(2), symDp(2), symDp(2), symDp(2))
                setOnClickListener {
                    view.setPicture(pic.key, resId)
                    paintAnatomyThumbs(pic.key)
                }
            }
            strip.addView(img)
            anatomyThumbs.add(img)
        }
        paintAnatomyThumbs("")

        // ── কাজের বোতাম ──
        tools.removeAllViews(); anatomyToolChips.clear()
        val toolList = listOf(
            Triple("✋ ফোলান", AnatomyView.Tool.BULGE, ""),
            Triple("📍 চিহ্ন", AnatomyView.Tool.PILE, ""),
            Triple("〰️ নালী", AnatomyView.Tool.TRACT, ""),
            Triple("⭕ গোল", AnatomyView.Tool.RING, ""),
            Triple("➡️ তীর", AnatomyView.Tool.ARROW, ""),
            Triple("🩹 মুছুন", AnatomyView.Tool.ERASE, "")
        )
        for ((label, tool, _) in toolList) {
            val chip = TextView(this).apply {
                text = label
                textSize = 12.5f
                setPadding(symDp(10), symDp(6), symDp(10), symDp(6))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, symDp(6), 0)
                layoutParams = lp
                tag = tool
                setOnClickListener {
                    view.tool = tool
                    if (tool == AnatomyView.Tool.PILE) askPileLabel()
                    paintAnatomyTools(tool)
                }
            }
            tools.addView(chip)
            anatomyToolChips.add(chip)
        }
        // ↺ একধাপ পিছনে
        tools.addView(TextView(this).apply {
            text = "↺ একটা পিছনে"
            textSize = 12.5f
            background = histChipBg(false)
            setTextColor(android.graphics.Color.parseColor("#7C8A9C"))
            setPadding(symDp(10), symDp(6), symDp(10), symDp(6))
            setOnClickListener { view.undo() }
        })
        paintAnatomyTools(AnatomyView.Tool.BULGE)
    }

    private fun paintAnatomyThumbs(chosen: String) {
        for (img in anatomyThumbs) {
            val on = (img.tag as? String) == chosen
            img.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = symDp(8).toFloat()
                setColor(android.graphics.Color.parseColor("#FFFFFF"))
                setStroke(symDp(if (on) 3 else 1),
                    android.graphics.Color.parseColor(if (on) "#D81E3F" else "#DBE2EA"))
            }
        }
    }

    private fun paintAnatomyTools(chosen: AnatomyView.Tool) {
        for (chip in anatomyToolChips) {
            val on = (chip.tag as? AnatomyView.Tool) == chosen
            chip.background = histChipBg(on)
            chip.setTextColor(android.graphics.Color.parseColor(if (on) "#0B4F2A" else "#7C8A9C"))
            chip.setTypeface(chip.typeface, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    /**
     * চিহ্নের নাম — ঘড়ির কাঁটা (৩টা/৭টা/১১টা) না সোজা বাংলা (ডান পাশ …),
     * সেটা ডাক্তার এখানেই বেছে নেন। TK-কে জিজ্ঞাসা করা হয়েছিল কোনটা রাখব;
     * উত্তর না আসা পর্যন্ত **দুটোই** রাখা হল, কোনোটাই নিজে থেকে ঠিক করা হয়নি।
     */
    private fun askPileLabel() {
        val view = anatomyView ?: return
        val clock = (1..12).map { "${it}টা" }
        val bangla = listOf("সামনে", "সামনে-ডান", "ডান পাশ", "পিছন-ডান",
                            "পিছনে", "পিছন-বাঁ", "বাঁ পাশ", "সামনে-বাঁ")
        val all = listOf("নাম ছাড়াই") + clock + bangla
        android.app.AlertDialog.Builder(this)
            .setTitle("চিহ্নের নাম")
            .setItems(all.toTypedArray()) { d, which ->
                view.pileLabel = if (which == 0) "" else all[which]
                d.dismiss()
            }
            .show()
    }

    /** শেষবার যা জমা ছিল — পর্দা তৈরি না থাকলে এটাই ফেরত যায়, নইলে
        সেভ করতে গিয়ে আগের আঁকা ছবিটা মুছে যেত। */
    /** V559: এইমাত্র যে চেকআপটা সেভ হচ্ছে — `markDoctorComplete()` ওটাই
        রোগীর সারিতে বসায়। ⛔ এক রোগীর সেভ শেষ হলেই মুছে ফেলা হয়, যাতে
        পরের রোগীর ঘরে আগেরজনের তথ্য বসার পথ না থাকে (B437-এর শিক্ষা)। */
    @Volatile private var pendingNote: CheckupRecord? = null

    /** পর্দা খোলার পর একবারই ফর্ম ভরানো হয়। */
    private var restoredOnce: Boolean = false

    /** JSON → সাধারণ ম্যাপ। তালিকার ঘরগুলো কমা দিয়ে জোড়া লেখা হয়ে যায়। */
    private fun jsonToMap(o: org.json.JSONObject?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (o == null) return out
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.opt(k)
            out[k] = when (v) {
                null, org.json.JSONObject.NULL -> ""
                is org.json.JSONArray -> {
                    val parts = ArrayList<String>()
                    for (i in 0 until v.length()) {
                        val e = v.optString(i, "")
                        if (e.isNotBlank()) parts.add(e)
                    }
                    CheckupNoteJson.joinList(parts)
                }
                is org.json.JSONObject -> ""      // ভিতরে আরেকটা বাক্স — ফোনে পড়ার দরকার নেই
                else -> v.toString()
            }
        }
        return out
    }

    /** ম্যাপ → JSON। ওয়েব যে ঘরগুলো **তালিকা** হিসেবে পড়ে, সেগুলো তালিকা
        হিসেবেই লেখা হয় — নইলে ওয়েবের টিকগুলো ভেঙে যেত। */
    private fun mapToJson(m: Map<String, String>): org.json.JSONObject {
        val o = org.json.JSONObject()
        for ((k, v) in m) {
            if (CheckupNoteJson.LIST_KEYS.contains(k)) {
                val arr = org.json.JSONArray()
                CheckupNoteJson.splitList(v).forEach { arr.put(it) }
                o.put(k, arr)
            } else o.put(k, v)
        }
        return o
    }

    private var lastAnatomySaved: String = ""

    private fun collectAnatomy(): String {
        val view = anatomyView ?: return lastAnatomySaved
        view.setNote(findViewById<android.widget.EditText>(R.id.etAnatomyNote)?.text?.toString().orEmpty())
        return view.save()
    }

    private fun applyAnatomy(saved: String) {
        lastAnatomySaved = saved
        val view = anatomyView ?: return
        view.load(saved) { key -> anatomyResId(key) }
        paintAnatomyThumbs(AnatomyModel.parse(saved).pic)
        findViewById<android.widget.EditText>(R.id.etAnatomyNote)?.setText(AnatomyModel.parse(saved).note)
    }

    private fun buildLifestyleRows() {
        val box = findViewById<android.widget.LinearLayout>(R.id.lifestyleGroup) ?: return
        box.removeAllViews()
        lifestyleChips.clear()
        for (q in LifestyleModel.QUESTIONS) {
            box.addView(TextView(this).apply {
                text = q.label
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                setPadding(0, symDp(8), 0, symDp(3))
            })
            val chips = ArrayList<TextView>()
            for (rowOptions in HistoryDetailModel.rowsFor(q.options)) {
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = symDp(4)
                    layoutParams = lp
                }
                for ((i, opt) in rowOptions.withIndex()) {
                    val chip = TextView(this).apply {
                        text = opt
                        textSize = 12f
                        gravity = android.view.Gravity.CENTER
                        setPadding(symDp(10), symDp(6), symDp(10), symDp(6))
                        isClickable = true
                        setTag(R.id.historyDetailGroup, false)
                        tag = opt
                        val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        if (i > 0) lp.marginStart = symDp(6)
                        layoutParams = lp
                    }
                    chip.setOnClickListener {
                        chip.setTag(R.id.historyDetailGroup, chip.getTag(R.id.historyDetailGroup) != true)
                        paintHistoryChip(chip)
                    }
                    paintHistoryChip(chip)
                    chips.add(chip)
                    row.addView(chip)
                }
                box.addView(row)
            }
            lifestyleChips[q.key] = chips
        }
    }

    private fun collectLifestyle(): String {
        val picked = LinkedHashMap<String, List<String>>()
        for ((key, chips) in lifestyleChips) {
            picked[key] = chips.filter { it.getTag(R.id.historyDetailGroup) == true }
                .map { (it.tag as? String).orEmpty() }
        }
        val water = findViewById<android.widget.EditText>(R.id.etWaterLitre)?.text?.toString().orEmpty()
        val other = findViewById<android.widget.EditText>(R.id.etLifestyleOther)?.text?.toString().orEmpty()
        return LifestyleModel.format(picked, water, other)
    }

    private fun applyLifestyle(saved: String) {
        val (map, water, other) = LifestyleModel.parse(saved)
        for ((key, chips) in lifestyleChips) {
            val chosen = map[key] ?: emptyList<String>()
            chips.forEach { chip ->
                chip.setTag(R.id.historyDetailGroup, chosen.contains((chip.tag as? String).orEmpty()))
                paintHistoryChip(chip)
            }
        }
        findViewById<android.widget.EditText>(R.id.etWaterLitre)?.setText(water)
        findViewById<android.widget.EditText>(R.id.etLifestyleOther)?.setText(other)
    }

    private fun collectHistoryDetail(): String {
        val picked = LinkedHashMap<String, List<String>>()
        for ((field, chips) in historyChips) {
            picked[field] = chips.filter { it.getTag(R.id.historyDetailGroup) == true }
                .map { (it.tag as? String).orEmpty() }
        }
        val note = findViewById<android.widget.EditText>(R.id.etHistoryNote)?.text?.toString().orEmpty()
        return HistoryDetailModel.format(picked, note)
    }

    private fun applyHistoryDetail(saved: String) {
        val (map, note) = HistoryDetailModel.parse(saved)
        for ((field, chips) in historyChips) {
            val chosen = map[field] ?: emptyList<String>()
            chips.forEach { chip ->
                chip.setTag(R.id.historyDetailGroup, chosen.contains((chip.tag as? String).orEmpty()))
                paintHistoryChip(chip)
            }
        }
        findViewById<android.widget.EditText>(R.id.etHistoryNote)?.setText(note)
    }

    private fun collectSymptomHistory(): String {
        val map = LinkedHashMap<String, SymptomHistoryModel.Entry>()
        for (line in SymptomHistoryModel.LINES) {
            val unitPos = symptomUnits[line.key]?.selectedItemPosition ?: 0
            map[line.key] = SymptomHistoryModel.Entry(
                ticked = symptomTicks[line.key]?.isChecked == true,
                amount = symptomAmounts[line.key]?.text?.toString().orEmpty(),
                unit = SymptomHistoryModel.UNITS.getOrElse(unitPos) { "Days" },
                severity = if (line.severity) currentSeverity(line.key) else ""
            )
        }
        val other = findViewById<android.widget.EditText>(R.id.etSymptomOther)?.text?.toString().orEmpty()
        return SymptomHistoryModel.format(map, other)
    }

    private fun applySymptomHistory(saved: String) {
        val (map, other) = SymptomHistoryModel.parse(saved)
        /* ⛔ আগে কিছু জমা না থাকলে রেজিস্ট্রেশনে যে উপসর্গে টিক দেওয়া ছিল
              সেগুলো **নিজে থেকেই** টিক হয়ে আসে — ডাক্তারকে দুবার একই কাজ
              করতে হয় না। জমা থাকলে জমাটাই আগে (কিছু বদলানো হয় না)। */
        if (saved.isBlank()) {
            val pre = SymptomHistoryModel.ticksFromComplaint(etComplaint.text?.toString().orEmpty())
            pre.forEach { key -> map[key]?.ticked = true }
        }
        for (line in SymptomHistoryModel.LINES) {
            val e = map[line.key] ?: continue
            symptomTicks[line.key]?.isChecked = e.ticked
            symptomAmounts[line.key]?.setText(e.amount)
            val pos = SymptomHistoryModel.UNITS.indexOf(e.unit)
            if (pos >= 0) symptomUnits[line.key]?.setSelection(pos)
            if (line.severity) setSeverity(line.key, e.severity)
        }
        findViewById<android.widget.EditText>(R.id.etSymptomOther)?.setText(other)
    }

    private fun internalPilesBox(): android.widget.CheckBox? =
        visualChecks.firstOrNull { ((it.tag as? String) ?: it.text.toString()).startsWith("Internal Piles") }

    private fun refreshInternalGradeLabel() {
        val box = internalPilesBox() ?: return
        val g = gradeOptions.getOrElse(spGrade.selectedItemPosition) { "" }
        val base = "Internal Piles · অভ্যন্তরীণ অর্শ"
        box.text = if (box.isChecked && g.isNotBlank()) "$base — $g" else base
    }

    private fun askInternalGrade() {
        if (internalPilesBox() == null) return
        /* 🔵🔒 V540 (TK: *"gradation — প্রফেশনাল লুক তৈরি করুন"*): প্রজেক্টের
           নিজের প্রমাণিত পিকার — প্রিমিয়াম হেডার, গোল-বোতামের তালিকা,
           বাছাইটা টিক দেওয়া অবস্থায় দেখা যায়। ⛔ নতুন নকশা বানানো হয়নি। */
        SpinnerPicker.open(spGrade, "INTERNAL PILES — GRADE")
    }

    /**
     * 🔵🔒 V539 (TK-নির্দেশ): *"পেশেন্টের ফটো রোটেট করা যাচ্ছে না"* — এই
     * CHECK-UP পর্দায় Rotate কখনোই ছিল না (TK নিজে নিশ্চিত করেছেন)।
     * এখন উপরের ছবিতে **তিনবার চাপ** দিলে ৯০° ঘুরে যায় ও **সঙ্গে সঙ্গে সেভ**
     * হয় — তাই সব পর্দাতেই সোজা দেখাবে।
     *
     * ⛔ প্রজেক্টের প্রমাণিত জিনিসই ব্যবহার করা হলো — `TripleTapEdit` (ভুল
     *    ছোঁয়ায় যেন না ঘোরে), `PhotoUtils.rotated/encodeBitmap` (V524-এর সেই
     *    একই মাপ ও মান), আর `PatientPhotoRepository.savePhoto` (সেভের সেই
     *    একই পথ, আইডি ধরে)। **নতুন কোনো নিয়ম বানানো হয়নি।**
     * ⛔ ছবি না থাকলে কিছুই হয় না।
     */
    private fun wireCheckupPhotoRotate() {
        val iv = findViewById<ImageView>(R.id.ivPatientPhoto) ?: return
        com.tkbiswas.pilesclinic.native.TripleTapEdit.attach(iv) {
            val digits = patMobile.filter { it.isDigit() }.takeLast(10)
            if (digits.length != 10) {
                Toast.makeText(this, "No valid 10-digit mobile", Toast.LENGTH_SHORT).show()
                return@attach
            }
            val bmp = com.tkbiswas.pilesclinic.native.PhotoUtils.decodeDataUrl(patPhoto)
            if (bmp == null) {
                Toast.makeText(this, "No photo to rotate", Toast.LENGTH_SHORT).show()
                return@attach
            }
            val turned = com.tkbiswas.pilesclinic.native.PhotoUtils.rotated(bmp, 90)
            val dataUrl = com.tkbiswas.pilesclinic.native.PhotoUtils.encodeBitmap(turned)
            if (dataUrl == null) {
                Toast.makeText(this, "Could not rotate", Toast.LENGTH_SHORT).show()
                return@attach
            }
            iv.setImageBitmap(turned)
            patPhoto = dataUrl
            Toast.makeText(this, "Rotated — saving…", Toast.LENGTH_SHORT).show()
            val appCtx = applicationContext
            com.tkbiswas.pilesclinic.native.BackgroundWork.run {
                val repo = com.tkbiswas.pilesclinic.native.PatientPhotoRepository()
                val ref = repo.findByMobile(digits, RoleSession.currentPatientId, RoleSession.currentPatientDisplayId)
                if (ref != null) repo.savePhoto(ref, dataUrl, appCtx)
            }
        }
    }

    private fun buildDetails(r: CheckupRecord): String = buildString {
        if (r.complaint.isNotBlank()) append("Complaint: ${r.complaint}; ")
        if (r.duration.isNotBlank()) append("Duration: ${r.duration}; ")
        if (r.acuteChronic.isNotBlank()) append("Onset: ${r.acuteChronic}; ")
        if (r.occupation.isNotBlank()) append("Occupation: ${r.occupation}; ")
        if (r.prevTreatment.isNotBlank()) append("Prev Treatment: ${r.prevTreatment}; ")
        if (r.patientSaid.isNotBlank()) append("Patient Said: ${r.patientSaid}; ")   // 🔵 V539
        // 🔵 V554: টিক দেওয়া উপসর্গ ও তাদের "কবে থেকে?" — মানুষ-পড়া-যায় লেখায়
        if (r.symptomHistory.isNotBlank()) append("Patient Reported: ${SymptomHistoryModel.readable(r.symptomHistory)}; ")
        // 🔵 V555: কাগজের ভাগ ৩ — চারটে ইতিহাসের বাছাই
        if (r.historyDetail.isNotBlank()) append("History Detail: ${HistoryDetailModel.readable(r.historyDetail)}; ")
        // 🔵 V556: রোগ ও অভ্যাস
        if (r.lifestyle.isNotBlank()) append("Habits: ${LifestyleModel.readable(r.lifestyle)}; ")
        // 🔵 V557
        if (r.probableDisease.isNotBlank() && r.probableDisease != CounselModel.PICK_NONE)
            append("Probable Disease: ${r.probableDisease}; ")
        if (r.timeAsked.isNotBlank()) append("Time Asked: ${r.timeAsked}; ")
        // 🔵 V558: ছবিতে যা দেখানো হয়েছে — কিছু না আঁকলে কিছুই লেখা হয় না
        if (AnatomyModel.readable(r.anatomy).isNotBlank())
            append("Disease Picture: ${AnatomyModel.readable(r.anatomy)}; ")
        if (r.prevResult.isNotBlank()) append("Prev Result: ${r.prevResult}; ")
        if (r.prevCost.isNotBlank()) append("Prev Cost: ${r.prevCost}; ")
        if (r.treatmentDuration.isNotBlank()) append("Treatment Duration: ${r.treatmentDuration}; ")
        val visAll = listOf(r.visual, r.visualOther).filter { it.isNotBlank() }.joinToString(", ")
        if (visAll.isNotBlank()) append("Visual: $visAll; ")
        val dreAll = listOf(r.dre, r.dreOther).filter { it.isNotBlank() }.joinToString(", ")
        if (dreAll.isNotBlank()) append("DRE: $dreAll; ")
        if (r.grade.isNotBlank()) append("Internal Piles Grade: ${r.grade}; ")   // 🔵 V539
        if (r.proctoscopy.isNotBlank()) append("Proctoscopy: ${r.proctoscopy}; ")   // 🔵 V539
        if (r.onProbing.isNotBlank()) append("On Probing: ${r.onProbing}; ")
        if (r.investigation.isNotBlank()) append("Investigation: ${r.investigation}; ")
        if (r.otherFindings.isNotBlank()) append("Other Findings: ${r.otherFindings}; ")
        // 🆕 TK-নির্দেশ (04.08.2026): Treatment Plan + টাকা — শুধু যেগুলো
        // সত্যিই টিক দেওয়া, সেগুলোরই টাকা দেখানো হয় (ফাঁকা/না-টিক করা
        // অপশনের টাকা প্রিন্ট/হিস্ট্রিতে বিভ্রান্তি তৈরি করত না, তাও
        // সাবধানতার জন্য)।
        if (r.treatmentPlan.isNotBlank()) {
            val amtParts = mutableListOf<String>()
            if (cbTxPerPiles.isChecked && r.amtPerPiles.isNotBlank()) amtParts.add("Per Piles ₹${r.amtPerPiles}")
            if (cbTxFistulaInch.isChecked && r.amtFistulaPerInch.isNotBlank()) amtParts.add("Fistula Per CM ₹${r.amtFistulaPerInch}")   // 🔵 V541
            if (cbTxKsharSutra.isChecked && r.amtKsharSutra.isNotBlank()) amtParts.add("Kshar Sutra ₹${r.amtKsharSutra}")
            val amtText = if (amtParts.isNotEmpty()) " (${amtParts.joinToString(", ")})" else ""
            append("Treatment Plan: ${r.treatmentPlan}$amtText; ")
        }
        if (r.counselling.isNotBlank()) append("Other Treatment Note: ${r.counselling}; ")
        if (r.estimatedCost.isNotBlank()) append("Est Cost: ${r.estimatedCost}; ")
        if (r.recoveryTime.isNotBlank()) append("Recovery: ${r.recoveryTime}; ")
        if (r.advanceDiscussed.isNotBlank()) append("Advance: ${r.advanceDiscussed}; ")
        if (r.patientDecision.isNotBlank()) append("Decision: ${r.patientDecision}; ")
        if (r.decisionRemark.isNotBlank()) append("Remarks: ${r.decisionRemark}; ")
        if (r.documents.isNotBlank()) append("Documents: ${r.documents}")
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        listOf(
            // 🔵 B622: etPrevResult · etPrevCost · etTreatmentDuration · etAdvanceDiscussed ঘর বাদ।
            // V455 (18.08.2026): etVisualOther · etDreOther · etOtherFindings · etDecisionRemark · etDocuments ঘর বাদ।
            etComplaint, etDuration, etPrevTreatment,
            etOnProbing,
            etAmtPerPiles, etAmtFistulaInch, etAmtKsharSutra,
            etCounselling, etEstimatedCost, etRecoveryTime
        ).forEach { it.isEnabled = enabled }
        (visualChecks + investigationChecks + treatmentChecks()).forEach { it.isEnabled = enabled }
        spGrade.isEnabled = enabled
        // V455 (18.08.2026): spPatientDecision · spAcuteChronic isEnabled বাদ (ঘর নেই)।
        spOccupation.isEnabled = enabled
    }

    // ─────────────────────────────────────────────────────────────────────
    // 🔴 NEW (07.08.2026, scroll_B/scroll_C মকআপ) — সেভ করলে "লক"।
    // এটা সম্পূর্ণ দেখার (UI) স্তর — উপরের collect()/saveMedical()/
    // markDoctorComplete()/ছবি — কোনো ডেটা-লজিক ছোঁয়া হয়নি।
    //   • History / Clinical / Counsel → সবুজ-টিক read-only summary।
    //   • Estimate & Decision → লুকানো (TK: সেভের পর আর দেখানোর দরকার নেই)।
    //   • Photo → ছবি থাকে, ক্যামেরা-বোতাম ও Quick Actions লুকানো।
    //   • নিচে Back/Save লুকিয়ে Share/Print।
    //   • কোনো সেকশনে তিনবার চাপলে (বা ✎) আবার এডিট করা যায়, Save করলে
    //     আবার লক হয়।
    // ─────────────────────────────────────────────────────────────────────
    private fun enterLockedMode(r: CheckupRecord) {
        // 🆕🔒 (07.08.2026, TK-অনুমোদিত) — সেভ হওয়ার পর সবুজ-summary-র বদলে
        // এখন পুরো A4 চেকআপ-রিপোর্ট (WebView) দেখাই, নিচে ✎ Edit · 🔗 Share ·
        // 🖨 Print। Edit চাপলে ফর্মে ফেরে (তথ্য অক্ষত), Save করলে আবার A4।
        // ⛔ উপরের সেভ/ক্লাউড-লজিক অপরিবর্তিত — এটা শুধু "দেখা"র রূপ।
        lastSavedRecord = r
        showSavedA4(buildA4Html(r))
    }

    // ⛔ পুরনো green-check summary lock (A4 রিপোর্ট আসার আগে ব্যবহৃত) — এখন আর
    // ডাকা হয় না, শুধু রেফারেন্স/দরকারে-ফেরানোর জন্য রইল।
    private fun enterLockedModeOld(r: CheckupRecord) {
        lockSection(R.id.secHistory, buildSectionSummary("History & Previous", listOf(
            "প্রধান সমস্যা" to r.complaint,
            "কতদিন থেকে" to r.duration,
            "পেশা" to r.occupation,
            "আগের চিকিৎসা" to r.prevTreatment,
            "Onset" to r.acuteChronic
        )) { unlockSection(R.id.secHistory) })

        val visAll = listOf(r.visual, r.visualOther).filter { it.isNotBlank() }.joinToString(", ")
        val dreAll = listOf(r.dre, r.dreOther).filter { it.isNotBlank() }.joinToString(", ")
        /* 🔴🔒 V542 (২২.০৮.২০২৬, TK-নির্দেশ): "E. Investigations" পর্দা থেকে
           সরানো হয়েছে, তাই নতুন চেকআপে ঘরটা ফাঁকা — ফাঁকা সারি দেখানোর
           মানে হয় না। ⛔ **পুরোনো চেকআপে লেখা থাকলে সারিটা আগের মতোই
           দেখাবে** (তখন কোন পরীক্ষা বলা হয়েছিল সেটা হারায় না)।
           ⛔ Grade-এর লেবেলটাও এখন "Internal Piles Grade" (V539), আর
              Proctoscopy-র লেখাটা আলাদা সারিতে। */
        lockSection(R.id.secClinical, buildSectionSummary("Clinical পরীক্ষা", listOf(
            "Visual" to visAll,
            "DRE" to dreAll,
            "Internal Piles Grade" to r.grade,
            "Proctoscopy" to r.proctoscopy,
            "On Probing" to r.onProbing,
            "Investigations" to r.investigation
        ).filter { it.second.isNotBlank() }) { unlockSection(R.id.secClinical) })

        lockSection(R.id.secCounsel, buildSectionSummary("Counsel · চিকিৎসা পরিকল্পনা", listOf(
            "চিকিৎসা" to r.treatmentPlan,
            "হার" to buildRateSummary(r),
            "পরামর্শ" to r.counselling
        )) { unlockSection(R.id.secCounsel) })

        // Estimate & Decision — সেভের পর লুকানো।
        findViewById<android.view.View>(R.id.secEstimate).visibility = android.view.View.GONE

        // Photo — ছবি থাকে, শুধু ক্যামেরা-বোতাম + Quick Actions লুকানো।
        findViewById<android.view.View>(R.id.btnBeforePhoto).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.btnDuringPhoto).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.btnAfterPhoto).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.quickActionsLabel).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.quickActionsRow1).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.quickActionsRow2).visibility = android.view.View.GONE
        // V455 (18.08.2026): etDocuments.isEnabled=false বাদ (ঘরটাই আর নেই)।

        lockedFooter(true)
        // 🎨 (07.08.2026, প্রুফ-চেহারা) — সব ধাপ সম্পন্ন: প্রতিটা নম্বর-বৃত্ত এখন
        // সবুজ ✓। (আগে: chip.text = "✓ " + stepTitles[idx].substringAfter(" "))
        stepChips.forEachIndexed { idx, circle ->
            circle.background = stepCircleBg(true)
            circle.text = "✓"
            circle.setTextColor(android.graphics.Color.WHITE)
            circle.setTypeface(null, android.graphics.Typeface.BOLD)
            stepChipLabels.getOrNull(idx)?.setTextColor(android.graphics.Color.parseColor("#0F766E"))
        }
        findViewById<ScrollView>(R.id.stepScroll).post {
            findViewById<ScrollView>(R.id.stepScroll).smoothScrollTo(0, 0)
        }
    }

    private fun lockSection(cardId: Int, summary: android.view.View) {
        val card = findViewById<android.view.ViewGroup>(cardId)
        if (card.childCount > 1) card.removeViewAt(1)   // re-lock: drop old summary
        card.getChildAt(0).visibility = android.view.View.GONE
        card.addView(
            summary,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun unlockSection(cardId: Int) {
        val card = findViewById<android.view.ViewGroup>(cardId)
        if (card.childCount > 1) card.removeViewAt(1)
        card.getChildAt(0).visibility = android.view.View.VISIBLE
        lockedFooter(false)  // show Back|Save so a re-edit can be re-saved
        findViewById<ScrollView>(R.id.stepScroll).post {
            findViewById<ScrollView>(R.id.stepScroll).smoothScrollTo(0, card.top)
        }
    }

    private fun lockedFooter(lock: Boolean) {
        fun vis(b: Boolean) = if (b) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<MaterialButton>(R.id.btnBack).visibility = vis(!lock)
        findViewById<MaterialButton>(R.id.btnSaveCheckup).visibility = vis(!lock)
        // 🔴 V501 — নতুন Share বোতামও Save-এর সঙ্গেই দেখা যায়/লুকায়।
        findViewById<MaterialButton>(R.id.btnShareNow).visibility = vis(!lock)
        findViewById<MaterialButton>(R.id.btnShareCheckup).visibility = vis(lock)
        findViewById<MaterialButton>(R.id.btnPrintCheckup).visibility = vis(lock)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 🆕🔒 (07.08.2026, TK-অনুমোদিত) — সেভ হওয়ার পর A4 চেকআপ-রিপোর্ট (WebView)।
    //   • showSavedA4(): ফর্ম লুকিয়ে A4 রিপোর্ট দেখায়, নিচে ✎ Edit · 🔗 Share · 🖨 Print।
    //   • showEditForm(): আবার ফর্ম ফেরে (তথ্য অক্ষত), নিচে ◀ Back · 💾 Save।
    //   • buildA4Html(): সেভ-করা রেকর্ড থেকে ক্লিনিক-হেডারসহ A4 HTML বানায়।
    // ⛔ কোনো ফিল্ড/সেভ-লজিক ছোঁয়া হয়নি — শুধু "দেখা"র রূপ।
    // ─────────────────────────────────────────────────────────────────────
    private fun showSavedA4(html: String) {
        val wv = findViewById<android.webkit.WebView>(R.id.savedA4View)
        wv.settings.javaScriptEnabled = false
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        findViewById<android.view.View>(R.id.stepBar).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.stepScroll).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.patientDetailsFull).visibility = android.view.View.GONE
        wv.visibility = android.view.View.VISIBLE
        fun vis(b: Boolean) = if (b) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<MaterialButton>(R.id.btnBack).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnSaveCheckup).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnShareNow).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnNext).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnEditCheckup).visibility = vis(true)
        findViewById<MaterialButton>(R.id.btnShareCheckup).visibility = vis(true)
        findViewById<MaterialButton>(R.id.btnPrintCheckup).visibility = vis(true)
    }

    private fun showEditForm() {
        findViewById<android.view.View>(R.id.savedA4View).visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.stepBar).visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.stepScroll).visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.patientDetailsFull).visibility = android.view.View.VISIBLE
        fun vis(b: Boolean) = if (b) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<MaterialButton>(R.id.btnBack).visibility = vis(true)
        findViewById<MaterialButton>(R.id.btnSaveCheckup).visibility = vis(true)
        findViewById<MaterialButton>(R.id.btnShareNow).visibility = vis(true)   // 🔴 V501
        findViewById<MaterialButton>(R.id.btnNext).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnEditCheckup).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnShareCheckup).visibility = vis(false)
        findViewById<MaterialButton>(R.id.btnPrintCheckup).visibility = vis(false)
        showStep(currentStep)
    }

    private fun buildA4Html(r: CheckupRecord): String {
        // 🆕 (07.08.2026) — A4 রিপোর্টের চেহারা এখন একটাই জায়গায়:
        // `CheckupA4Report` (History-তেও ঠিক এই টেমপ্লেটই ব্যবহার হয়, তাই
        // দুই জায়গার রিপোর্ট কখনো আলাদা দেখাবে না)।
        val visAll = listOf(r.visual, r.visualOther).filter { it.isNotBlank() }.joinToString(", ")
        val dreAll = listOf(r.dre, r.dreOther).filter { it.isNotBlank() }.joinToString(", ")
        return CheckupA4Report.html(
            CheckupA4Report.Info(
                name = RoleSession.currentPatientName,
                patientId = RoleSession.displayId(),
                age = patAge, sex = patSex, mobile = patMobile,
                disease = patDisease.ifBlank { "Piles" },
                address = patAddress,
                branch = RoleSession.currentPatientBranch,
                date = CheckupA4Report.today(),
                photo = patPhoto
            ),
            CheckupA4Report.Fields(
                complaint = r.complaint, duration = r.duration, onset = r.acuteChronic,
                occupation = r.occupation, prevTreatment = r.prevTreatment,
                prevResult = r.prevResult, prevCost = r.prevCost,
                treatmentDuration = r.treatmentDuration,
                visual = visAll, dre = dreAll, grade = r.grade,
                onProbing = r.onProbing, investigation = r.investigation,
                otherFindings = r.otherFindings,
                treatmentPlan = r.treatmentPlan, rate = buildRateSummary(r),
                counselling = r.counselling,
                estCost = r.estimatedCost, recovery = r.recoveryTime,
                advance = r.advanceDiscussed, decision = r.patientDecision,
                remarks = r.decisionRemark,
                beforePhoto = r.beforePhoto, duringPhoto = r.duringPhoto, afterPhoto = r.afterPhoto
            )
        )
    }

    // 🔒 B550 (08.08.2026, TK-অনুমোদিত প্রুফ) — Rate-এ এখন শুধু যে চিকিৎসা
    // **টিক করা হয়েছে** তার দাম দেখাবে। আগে দাম-ঘরে ডিফল্ট মান সবসময় বসানো
    // থাকায় (৮০০০/১১০০০/৬০০০) তিনটে দামই আসত — যদিও একটাই টিক ছিল (TK ধরেছেন)।
    // এখন `treatmentPlan`-এ ওই চিকিৎসার নাম আছে কিনা মিলিয়ে তবেই দাম যোগ হয়।
    // ⛔ সেভ-হওয়া লেখা (buildDetails) আগে থেকেই টিক দেখে ঠিক ছিল — এটা শুধু
    // লাইভ রেকর্ডটাকে তার সাথে মিলিয়ে দিল।
    private fun buildRateSummary(r: CheckupRecord): String {
        val plan = r.treatmentPlan
        val parts = mutableListOf<String>()
        if (plan.contains("Per Piles") && r.amtPerPiles.isNotBlank()) parts.add("₹${r.amtPerPiles} / অর্শ")
        // 🔵 V541: পুরোনো "Inch" লেখা রেকর্ডও ধরা পড়ে, নইলে পুরোনো চেকআপে হার দেখাত না।
        if (FISTULA_TAGS.any { plan.contains(it) } && r.amtFistulaPerInch.isNotBlank()) parts.add("₹${r.amtFistulaPerInch} / সেমি")
        if (plan.contains("Kshar Sutra") && r.amtKsharSutra.isNotBlank()) parts.add("₹${r.amtKsharSutra} / ক্ষার সূত্র")
        return parts.joinToString(" · ")
    }

    private fun buildSectionSummary(
        title: String, rows: List<Pair<String, String>>, onEdit: () -> Unit
    ): android.view.View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        outer.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.parseColor("#12805C"))
        })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "✓"; setTextColor(android.graphics.Color.WHITE); textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#12805C"))
            }
            val lp = LinearLayout.LayoutParams(dp(22), dp(22)); lp.marginEnd = dp(8); layoutParams = lp
        })
        header.addView(TextView(this).apply {
            text = title; setTextColor(android.graphics.Color.parseColor("#101828")); textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "✎"; setTextColor(android.graphics.Color.parseColor("#0B5E8A")); textSize = 15f
            setPadding(dp(10), dp(2), dp(4), dp(2))
            isClickable = true; isFocusable = true
            setOnClickListener { onEdit() }
        })
        content.addView(header)
        rows.forEach { (label, value) ->
            val rowLl = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(7), 0, dp(7))
            }
            rowLl.addView(TextView(this).apply {
                text = label; setTextColor(android.graphics.Color.parseColor("#5A6B76")); textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.42f)
            })
            rowLl.addView(TextView(this).apply {
                text = value.ifBlank { "—" }
                setTextColor(android.graphics.Color.parseColor("#101828")); textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.58f)
            })
            content.addView(rowLl)
            content.addView(android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#EEF1F5"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
        }
        // তিনবার চাপলে এডিট।
        var taps = 0; var last = 0L
        content.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            taps = if (now - last < 600) taps + 1 else 1
            last = now
            if (taps >= 3) { taps = 0; onEdit() }
        }
        outer.addView(content)
        return outer
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * 🔴🔒 V501 (TK-নির্দেশ ২১.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত)
     *
     * TK-এর কথা: *"সেখানে চাপ দিলে অটোমেটিক জোর করে WhatsApp / business
     * WhatsApp ওপেন হবে… A4 size pdf চাইলে প্রিন্টও করা যাবে।"*
     *
     * ─── আগে যা হতো (এবং যা TK চাননি) ───────────────────────────────────
     * শুধু **সাদামাটা লেখা** (`text/plain`) Android-এর সাধারণ chooser-এ যেত —
     * ফোনের সব অ্যাপের লম্বা তালিকা আসত, কোনো A4 PDF যেত না, ছাপাও যেত না।
     *
     * ─── এখন ────────────────────────────────────────────────────────────
     * পর্দায় দেখা **হুবহু সেই A4 রিপোর্টই** (`buildA4Html`) PDF হয়ে যায়,
     * আর শুধু তিনটে পথ দেখানো হয়: WhatsApp · WhatsApp Business · Print/PDF।
     *
     * ⛔ নতুন কোনো ব্যবস্থা বানানো হয়নি — প্রকল্পের প্রমাণিত
     *    `PrescriptionWhatsAppShare` (Prescription-এ চলছে) ও
     *    `PdfPrintDocumentAdapter` (Print-এ চলছে) পুনর্ব্যবহার করা হলো।
     */
    private fun shareCheckup() {
        val r = lastSavedRecord ?: collect()
        try {
            com.tkbiswas.pilesclinic.print.PrescriptionWhatsAppShare.shareHtml(
                activity = this,
                html = buildA4Html(r),
                documentTitle = "Check-up",
                patientName = RoleSession.currentPatientName,
                allowPrint = true
            )
        } catch (e: Throwable) {
            Toast.makeText(this, "Share not available: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 🔴 V501 — নিচের নতুন "📤 Share" বোতাম (Back ও Save-এর মাঝে)।
     * TK-এর সিদ্ধান্ত: **আগে নিজে থেকে সেভ হবে, তারপর শেয়ার** — যাতে কখনো
     * পুরোনো বা অসম্পূর্ণ রিপোর্ট রোগীর কাছে চলে না যায়।
     */
    private fun saveThenShare() {
        try {
            findViewById<MaterialButton>(R.id.btnSaveCheckup).performClick()
        } catch (_: Throwable) { }
        // সেভের কাজ শেষ হতে একটু সময় দিয়ে তবেই শেয়ার (সেভ ব্যর্থ হলেও
        // পর্দার নিজের বার্তা দেখা যাবে; শেয়ারে তখন বর্তমান ফর্মই যাবে)।
        findViewById<MaterialButton>(R.id.btnShareNow).postDelayed({ shareCheckup() }, 700L)
    }

    private var printWebView: android.webkit.WebView? = null
    private fun printCheckup() {
        // 🆕 (07.08.2026) — অন-স্ক্রিন A4 রিপোর্টের হুবহু একই HTML ছাপে (আগে সরল
        // list-HTML ছিল)। সেভ-করা রেকর্ড থাকলে সেটাই, নইলে বর্তমান ফর্ম।
        val r = lastSavedRecord ?: collect()
        val html = buildA4Html(r)
        try {
            val wv = android.webkit.WebView(this)
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                    try {
                        val pm = getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                        val adapter = view.createPrintDocumentAdapter("DoctorCheckup")
                        pm.print("Doctor Checkup", adapter, android.print.PrintAttributes.Builder().build())
                    } catch (_: Throwable) {
                        Toast.makeText(this@DoctorCheckupActivity, "Print not available", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            printWebView = wv
            // 🆕 baseURL = asset — যাতে ক্লিনিক-লোগো (www/assets/...) ছাপায় দেখা যায়।
            wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        } catch (_: Throwable) {
            Toast.makeText(this, "Print not available", Toast.LENGTH_SHORT).show()
        }
    }
}
