package com.tkbiswas.pilesclinic.clinical

/**
 * Placeholder role/session holder for Phase 4.
 *
 * The existing Login/Dashboard (Phase 1-2) live inside the WebView JS app, so there
 * is currently no native session object to read the real logged-in role/patient from.
 * Until that wiring is done, screens read the role from:
 *   1) an Intent extra (EXTRA_ROLE / EXTRA_PATIENT_NAME / EXTRA_PATIENT_ID) if the
 *      caller (e.g. a future native Dashboard) supplied one, otherwise
 *   2) this in-memory default (DOCTOR), so the modules are always usable/testable
 *      standalone and never show a blank/broken screen.
 *
 * When Phase 1-2 native login exists, replace `currentRole` assignment below with
 * the real authenticated role and delete the "default" fallback.
 */
object RoleSession {

    const val EXTRA_ROLE = "extra_role"
    const val EXTRA_PATIENT_NAME = "extra_patient_name"
    const val EXTRA_PATIENT_ID = "extra_patient_id"
    // 🔒🔒 খাতার সারি B175 (TK, 30.07.2026 — "Patient ID তো প্রজেক্টের শুরু
    // থেকেই ফাইনাল ছিল, তাহলে প্রেসক্রিপশনে নেই কেন?")। **আসল কারণ (কোড ধরে,
    // আন্দাজ নয়):** `EXTRA_PATIENT_ID`/`currentPatientId` — এই একটাই ঘর **দুটো
    // আলাদা কাজে** ব্যবহার হত: (১) ছাপার কাগজে দেখানো মানুষ-পড়া-যায় এমন আইডি
    // (যেমন "KNE-30072026-001"), (২) `medical` টেবিলে প্রেসক্রিপশন সেভ/খোঁজার
    // **আসল চাবি** (রোগীর সারির ভিতরের raw আইডি, যেমন "pat_9711468691")।
    // এই দুটো মান **আলাদা**, কিন্তু একই ঘরে রাখা হত — তাই যেখান থেকেই raw আইডি
    // আগে বসত (বেশিরভাগ জায়গায়), ছাপায় সেটাই "Patient ID" হিসেবে দেখাত।
    // **সমাধান:** এই নতুন ঘরটা **শুধু দেখানোর জন্য** — `currentPatientId`
    // (raw আইডি) এক অক্ষরও বদলায়নি, তাই `medical` টেবিলের সেভ/খোঁজা আগের
    // মতোই ঠিক থাকে (এটাই সবচেয়ে জরুরি — এটা ভাঙলে প্রেসক্রিপশন হারিয়ে যেত)।
    const val EXTRA_PATIENT_DISPLAY_ID = "extra_patient_display_id"
    const val EXTRA_PATIENT_BRANCH = "extra_patient_branch"
    const val EXTRA_PATIENT_MOBILE = "extra_patient_mobile"
    const val EXTRA_PATIENT_ADDRESS = "extra_patient_address"
    const val EXTRA_PATIENT_AGE = "extra_patient_age"
    const val EXTRA_PATIENT_SEX = "extra_patient_sex"
    const val EXTRA_PATIENT_DISEASE = "extra_patient_disease"

    var currentRole: UserRole = UserRole.STAFF
    var currentPatientName: String = ""
    // ⛔ এটা `medical` টেবিলের সত্যিকারের চাবি (raw row id) — শুধু ছাপার
    //    জন্য এখান থেকে সরাসরি না পড়ে, নিচের `displayId()` ব্যবহার করা হয়।
    var currentPatientId: String = ""
    // 🔒 খাতার সারি B175 — মানুষ-পড়া-যায় Patient ID (যেমন "KNE-30072026-001"),
    // শুধু ছাপার কাগজ/স্ক্রিনে দেখানোর জন্য। ফাঁকা থাকলে `displayId()`
    // নিজে থেকেই raw আইডিতে ফিরে যায় — তাই পুরনো কোনো পথ কখনো ভাঙে না।
    var currentPatientDisplayId: String = ""
    var currentPatientBranch: String = ""
    var currentPatientMobile: String = ""
    var currentPatientAddress: String = ""
    var currentPatientAge: String = ""
    var currentPatientSex: String = ""
    var currentPatientDisease: String = ""

    /** ছাপা/পর্দায় দেখানোর জন্য — মানুষ-পড়া-যায় আইডি থাকলে সেটাই, নইলে
     *  raw আইডি (আগের আচরণ, কিছু ভাঙে না)। ⛔ `medical` টেবিলের সেভ/খোঁজায়
     *  এটা ব্যবহার করা যাবে না — সেখানে সবসময় `currentPatientId`। */
    fun displayId(): String = currentPatientDisplayId.ifBlank { currentPatientId }

    fun isDoctor(): Boolean = currentRole == UserRole.DOCTOR

    /**
     * Whether the current user may fill and save clinical forms (Checkup,
     * Prescription, Medicine Slip, Investigation, Diet Chart). The web ERP's
     * saveMedicalRecord() had NO doctor-only lock — staff and the master could
     * both fill and save these — so native matches that: clinical editing is
     * allowed for any logged-in user.
     */
    fun canEditClinical(): Boolean = true

    fun applyFrom(
        roleExtra: String?, patientName: String?, patientId: String?, patientBranch: String? = null,
        patientMobile: String? = null, patientAddress: String? = null, patientAge: String? = null,
        patientSex: String? = null, patientDisease: String? = null, patientDisplayId: String? = null
    ) {
        if (!roleExtra.isNullOrBlank()) {
            currentRole = try {
                UserRole.valueOf(roleExtra.uppercase())
            } catch (e: IllegalArgumentException) {
                currentRole
            }
        }
        /* 🔴🔵🔒 V537 (২২.০৮.২০২৬, TK-রিপোর্ট) — **আগের রোগীর ওষুধ আর সঙ্গে
           যাবে না।** রোগী খোলার প্রতিটা পথ (Doctor Queue · Chamber · Global
           Search · Timeline · Follow-up) এই ফাংশনটা দিয়েই যায় — তাই পাহারাটা
           এখানেই, এক জায়গায়।
           ⛔ **একই রোগী হলে কিচ্ছু হয় না** — চলতি প্রেসক্রিপশন কখনো মুছবে না।
           ⛔ `patientId` ফাঁকা এলেও কিছু হয় না (আগের মতোই)। */
        val incomingPatient = patientId?.trim().orEmpty()
        if (incomingPatient.isNotEmpty() && incomingPatient != currentPatientId) {
            ClinicalRepository.resetForNewPatient()
            ClinicalRepository.ownerPatientId = incomingPatient

            /* 🔴🔵🔒 V538 (২২.০৮.২০২৬, TK-নির্দেশ) — **আগের রোগীর ঠিকানা/বয়স/
               লিঙ্গ/রোগ আর নতুন রোগীর কাগজে যাবে না।**

               নিচের প্রতিটা ঘর বসে `if (!…isNullOrBlank())` শর্তে — অর্থাৎ
               **নতুন রোগীর কোনো ঘর ফাঁকা এলে আগের রোগীরটাই থেকে যেত**।
               এই ঝুঁকিটা কোডেই লেখা ছিল (খাতার সারি B174, ৩০.০৭.২০২৬):
               *"`applyFrom()` `null` পেলে পুরনো (হয়তো **অন্য রোগীর**, বা
               ফাঁকা) মান ধরেই রাখে"* — তখন শুধু Timeline-এর পথটা সারানো
               হয়েছিল, বাকি পথগুলো নয়।

               যাচাই করে পাওয়া বাকি পথগুলো:
                 • Chamber — রোগীর সারি না পেলে ঠিকানা/বয়স/লিঙ্গ `""` যায়
                 • Global Search — ওগুলো লোড না হলে `""`, আর **রোগের নাম
                   সবসময়** `""`
                 • Doctor Queue → Clinical — intent-এ ঘর না থাকলে `null`

               ⇒ **রোগী বদলালে** এই ঘরগুলো আগে খালি করে দেওয়া হয়, তাই ফাঁকা
                 মান আর আগের রোগীর তথ্য টেনে আনতে পারে না।

               ⛔ **একই রোগী হলে এই ব্লক চলেই না** — B174-এর ভাল কাজটা
                  (ফাঁকা এলে আগের মান ধরে রাখা) সেখানে **অটুট**।
               ⛔ `currentRole` **ছোঁয়া হয় না** — ওটা স্টাফের ভূমিকা, রোগীর নয়।
               ⛔ নিচের বসানোর লাইনগুলো এক অক্ষরও বদলায়নি। */
            currentPatientName = ""
            currentPatientDisplayId = ""
            currentPatientBranch = ""
            currentPatientMobile = ""
            currentPatientAddress = ""
            currentPatientAge = ""
            currentPatientSex = ""
            currentPatientDisease = ""
        }
        if (!patientName.isNullOrBlank()) currentPatientName = patientName
        if (!patientId.isNullOrBlank()) currentPatientId = patientId
        // 🔒 খাতার সারি B175 — মানুষ-পড়া-যায় আইডি আলাদা করে রাখা হচ্ছে।
        if (!patientDisplayId.isNullOrBlank()) currentPatientDisplayId = patientDisplayId
        if (!patientBranch.isNullOrBlank()) currentPatientBranch = patientBranch
        if (!patientMobile.isNullOrBlank()) currentPatientMobile = patientMobile
        if (!patientAddress.isNullOrBlank()) currentPatientAddress = patientAddress
        if (!patientAge.isNullOrBlank()) currentPatientAge = patientAge
        if (!patientSex.isNullOrBlank()) currentPatientSex = patientSex
        if (!patientDisease.isNullOrBlank()) currentPatientDisease = patientDisease
    }
}
