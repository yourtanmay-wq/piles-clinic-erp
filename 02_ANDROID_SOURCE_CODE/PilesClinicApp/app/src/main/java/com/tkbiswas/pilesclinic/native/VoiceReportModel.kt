package com.tkbiswas.pilesclinic.native

import java.time.LocalDate
import java.time.ZoneId

/* ═══════════════════════════════════════════════════════════════════════════
   🎤🔒 V1415 (১৩.০৯.২০২৬, TK-নির্দেশ "কাজ শুরু করে দিন", তালিকা ৫২০) —
   Search-এর ঘরে ভয়েসে/টাইপ করে প্রশ্ন করলে (যেমন "গতকাল কোচবিহারে কতজন
   পেশেন্ট এসেছিল") রিপোর্ট-উত্তর দেখানোর প্রথম ধাপ — এখন দুটো বিষয়:
   পেশেন্ট-রেজিস্ট্রেশন সংখ্যা ও টাকার কালেকশন। ৪৮টা সম্ভাব্য প্রশ্নের
   পুরো তালিকা `10_FUTURE_PLANS/VOICE_QUERY_PLAN_2026-09-13.md`-এ লেখা আছে —
   বাকিগুলো TK পরে বেছে দিলে এই একই ছাঁচে যোগ হবে।

   ⛔ এটা AI নয় — নির্দিষ্ট কয়েকটা শব্দ-ছাঁচ চেনে। ব্রাঞ্চ + সময় + বিষয়
   তিনটেই না মিললে `parse()` null দেয়; তখন "বুঝিনি" দেখানো হয়, ভুল সংখ্যা
   কখনো বানানো হয় না (TK-র সততার নিয়ম, ৫)।
   ═══════════════════════════════════════════════════════════════════════ */
object VoiceReportModel {

    enum class Metric {
        REGISTRATION_COUNT, COLLECTION, MEDICINE_SALE, SALINE_SALE, ENQUIRY_COUNT, REFUND, CASH_HANDOVER, RMP_DUE,
        MEDICINE_DUE, CALL_COUNT, TRASH_COUNT, RMP_ADVANCE,
        APPOINTMENT_COUNT, EXPECTED_COUNT, HANDOVER_PENDING, PAYMENT_REQUESTS, REFERRAL_REQUESTS, LEAVE_COUNT,
        DOCTOR_REMINDER, STAFF_REMINDER_OPEN, FEE_RETURN,
        CHAMBER_UNCLOSED, NO_SHOW, OUT_MISSING, WFH_COUNT, DUPLICATE_PATIENTS, FEE_UNPAID, CALLS_PENDING, MESSAGES_SENT,
        NEW_PATIENTS, FOLLOWUP_CALLS_DONE, DISEASE_COUNT, RMP_CALLED, RMP_CALL_DUE, FIELD_VISIT, STAFF_HOURS, STAFF_PRESENT
    }

    // 🩺 V1422 — বলা রোগের নাম → ডেটাবেসে যে বানানে জমা থাকে (RegistrationActivity-র ৬টা নাম)
    private val DISEASE_MAP = listOf(
        "পাইলস" to "Piles", "অর্শ" to "Piles", "piles" to "Piles",
        "ফিশার" to "Fissure", "ফিসার" to "Fissure", "fissure" to "Fissure",
        "ফিস্টুলা" to "Fistula", "ভগন্দর" to "Fistula", "fistula" to "Fistula",
        "হাইড্রোসিল" to "Hydrocele", "একশিরা" to "Hydrocele", "hydrocele" to "Hydrocele",
        "গুপ্ত" to "Gupt Rog", "gupt" to "Gupt Rog"
    )
    private fun findDisease(q: String): String? {
        val lower = q.lowercase()
        for ((k, v) in DISEASE_MAP) if (lower.contains(k)) return v
        return null
    }

    /** যে প্রশ্নগুলো "এখন পর্যন্ত মোট" — কোনো তারিখ/সময়-সীমা লাগে না (V1418–V1421)। */
    private val SNAPSHOT = setOf(Metric.RMP_DUE, Metric.MEDICINE_DUE, Metric.HANDOVER_PENDING,
        Metric.PAYMENT_REQUESTS, Metric.REFERRAL_REQUESTS, Metric.STAFF_REMINDER_OPEN,
        Metric.DUPLICATE_PATIENTS, Metric.FEE_UNPAID, Metric.CALLS_PENDING)

    data class Parsed(
        val metric: Metric,
        val branch: String,
        val from: String,   // yyyy-MM-dd
        val to: String,     // yyyy-MM-dd
        val branchLabel: String,
        val periodLabel: String,
        val extra: String = ""   // V1422 — যেমন রোগের নাম (DISEASE_COUNT)
    )

    // ব্রাঞ্চের বাংলা/ইংরেজি বানান — ডেটাবেসে যে বানানে আছে সেটাই ডানদিকে।
    private val BRANCH_MAP = linkedMapOf(
        "কিশানগঞ্জ" to "Kishanganj", "kishanganj" to "Kishanganj",
        "জলপাইগুড়ি" to "Jalpaiguri", "jalpaiguri" to "Jalpaiguri",
        "কোচবিহার" to "Cooch Behar", "কুচবিহার" to "Cooch Behar",
        "cooch behar" to "Cooch Behar", "coochbehar" to "Cooch Behar",
        "ফালাকাটা" to "Falakata", "falakata" to "Falakata",
        "বীরপাড়া" to "Birpara", "birpara" to "Birpara"
    )

    private fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Kolkata"))
    private fun iso(d: LocalDate): String = d.toString()

    private fun findBranch(q: String): Pair<String, String>? {
        val lower = q.lowercase()
        for ((k, v) in BRANCH_MAP) if (lower.contains(k.lowercase())) return v to v
        return null
    }

    private data class Range(val from: String, val to: String, val label: String)

    /* 🇬🇧 নিয়ম ৯ (স্টাফের পর্দায় সব লেখা ইংরেজি): এখানে .contains() শর্তে বাংলা
       শব্দ থাকে (ব্যবহারকারীর বাংলায় লেখা/বলা প্রশ্ন চেনার জন্য — অপরিহার্য),
       কিন্তু ফেরত-আসা `label`-টা সবসময় **ইংরেজি**, কারণ সেটাই পর্দায় দেখানো
       হয়। এই সব বাংলা মিলানো-শব্দ `NoBengali.kt`-এর MAP-এও যোগ করা আছে
       (পাহারা ৯.১৪ ধরে, তাই বাদ দেওয়ার সুযোগ নেই)। */
    private fun findDateRange(q: String): Range? {
        val t = today()
        return when {
            // V1420 — "আগামীকাল" (আসার কথা / অ্যাপয়েন্টমেন্ট) — "গতকাল"-এর আগে দেখা হয়, শব্দ দুটো আলাদা
            q.contains("আগামীকাল") || q.contains("tomorrow") -> Range(iso(t.plusDays(1)), iso(t.plusDays(1)), "Tomorrow")
            q.contains("গতকাল") -> Range(iso(t.minusDays(1)), iso(t.minusDays(1)), "Yesterday")
            q.contains("আজ") -> Range(iso(t), iso(t), "Today")
            q.contains("সাত দিন") || q.contains("7 din") || q.contains("last 7") || q.contains("সপ্তাহ") || q.contains("week") ->
                Range(iso(t.minusDays(6)), iso(t), "Last 7 days")
            q.contains("এক মাস") || q.contains("1 mash") ->
                Range(iso(t.minusMonths(1).plusDays(1)), iso(t), "Last 1 month")
            // V1419 — "এই মাসে" = চলতি মাসের ১ তারিখ থেকে আজ পর্যন্ত
            q.contains("এই মাস") || q.contains("this month") ->
                Range(iso(t.withDayOfMonth(1)), iso(t), "This month")
            else -> null
        }
    }

    /** স্ক্রিনে দেখানোর জন্য বাংলা তারিখ-লেবেল (দুটো ভিন্ন হলে "থেকে" দিয়ে)। */
    fun displayPeriod(from: String, to: String, label: String): String = when {
        from.isBlank() -> label
        from == to -> "${FollowUpModel.displayDate(from)} ($label)"
        else -> "${FollowUpModel.displayDate(from)} – ${FollowUpModel.displayDate(to)} ($label)"
    }

    private fun findMetric(q: String): Metric? {
        val hasSale = q.contains("বিক্রি")
        val hasMedicine = q.contains("মেডিসিন") || q.contains("ওষুধ")
        val hasSaline = q.contains("স্যালাইন")
        val hasEnquiry = q.contains("এনকোয়ারি")
        val hasRefund = q.contains("রিফান্ড")
        val hasHandover = q.contains("হ্যান্ডওভার")
        val hasDueWord = q.contains("বাকি") || q.contains("বাকী")
        val hasRmpDue = q.contains("কমিশন") && hasDueWord
        // 🎤 V1419 — মেডিসিন-বাকি · অ্যাপ-কল · ট্র্যাশ · RMP-অগ্রিম
        val hasProductDue = (hasMedicine || hasSaline) && hasDueWord && !hasSale
        val hasCall = q.contains("কল") && (q.contains("অ্যাপ") || q.contains("হয়েছে")) && !q.contains("ফলো")
        val hasTrash = q.contains("ডিলিট") || q.contains("ট্র্যাশ") || q.contains("মোছা") || q.contains("মুছে")
        val hasAdvance = q.contains("অগ্রিম") || q.contains("অ্যাডভান্স") || q.lowercase().contains("advance")
        // 🎤 V1420 — অ্যাপয়েন্টমেন্ট · আসার কথা · হ্যান্ডওভার-বাকি · অনুরোধ · ছুটি · রিমাইন্ডার · ফি ফেরত
        val lower = q.lowercase()
        val hasFeeReturn = q.contains("ফেরত") && (q.contains("ভিজিট") || q.contains("ফি"))
        val hasPayReq = q.contains("অনুরোধ") || q.contains("রিকোয়েস্ট") || lower.contains("request")
        val hasReferralReq = hasPayReq && (q.contains("রেফারেল") || lower.contains("referral"))
        val hasHandoverPending = hasHandover && (q.contains("হয়নি") || hasDueWord)
        val hasAppointment = q.contains("অ্যাপয়েন্টমেন্ট") || lower.contains("appointment")
        val hasExpected = q.contains("আসার কথা") || q.contains("আসবে")
        val hasLeave = q.contains("ছুটি") || lower.contains("leave")
        val hasReminder = q.contains("রিমাইন্ডার") || lower.contains("reminder")
        val hasDoctorReminder = hasReminder && (q.contains("ডাক্তার") || lower.contains("doctor"))
        // 🎤 V1421 — চেম্বার-বন্ধ-হয়নি · no-show · OUT-বাদ · WFH · ডুপ্লিকেট · ফি-জমা-পড়েনি · ফলো-আপ-কল-বাকি · বার্তা
        val hasNoShow = q.contains("আসেননি") || q.contains("আসেনি") || lower.contains("no show") || lower.contains("no-show") || lower.contains("noshow")
        val hasChamberUnclosed = q.contains("চেম্বার") && (q.contains("বন্ধ") || lower.contains("close"))
        val hasOutMissing = (lower.contains("out") || q.contains("আউট")) && (q.contains("হয়নি") || q.contains("দেয়নি"))
        val hasWfh = lower.contains("wfh") || lower.contains("work from home") || q.contains("বাড়ি থেকে")
        val hasDuplicate = q.contains("ডুপ্লিকেট") || lower.contains("duplicate")
        val hasCallsPending = q.contains("ফলো") && q.contains("কল") && (hasDueWord || q.contains("হয়নি"))
        val hasMessages = q.contains("বার্তা") || q.contains("মেসেজ") || q.contains("হোয়াটসঅ্যাপ") || lower.contains("whatsapp") || lower.contains("sms")
        val hasFeeUnpaid = (q.contains("ভিজিট") || q.contains("ফি")) &&
            (q.contains("জমা পড়েনি") || q.contains("জমা হয়নি") || q.contains("দেয়নি") || q.contains("দেননি") || hasDueWord)
        // 🎤 V1422 — নতুন রোগী · ফলো-আপ কল করা · রোগ-ভিত্তিক · RMP কল/কল-করার-কথা · ফিল্ড · ঘণ্টা · হাজির
        val hasNewPatients = q.contains("শুরু হয়নি") || (q.contains("নতুন") && (q.contains("রোগী") || q.contains("পেশেন্ট")))
        val hasFuCallsDone = q.contains("ফলো") && q.contains("কল") && !hasDueWord && !q.contains("হয়নি")
        val hasRmpWord = lower.contains("rmp") || q.contains("আরএমপি") || q.contains("ডাক্তার")
        val hasRmpCallDue = hasRmpWord && q.contains("কল") && (q.contains("কথা") || q.contains("করতে হবে") || hasDueWord)
        val hasRmpCalled = hasRmpWord && q.contains("কল") && !hasRmpCallDue && !hasReminder
        val hasFieldVisit = q.contains("ফিল্ড") || lower.contains("field") || q.contains("কিমি") || lower.contains(" km") || q.contains("ঘুরে")
        val hasStaffHours = q.contains("ঘণ্টা") || q.contains("ঘন্টা") || lower.contains("hour")
        val hasStaffPresent = q.contains("হাজির") || q.contains("উপস্থিত") || lower.contains("present")
        val hasDisease = findDisease(q) != null && (q.contains("রোগী") || q.contains("পেশেন্ট") || q.contains("কতজন"))
        val hasMoney = q.contains("কালেকশন") || q.contains("জমা") || (q.contains("টাকা") && !q.contains("পেশেন্ট"))
        val hasPatientCount = (q.contains("পেশেন্ট") || q.contains("রোগী")) &&
            (q.contains("কতজন") || q.contains("এসেছিল") || q.contains("এসেছে"))
        // ⛔ ক্রমটা ওয়েবের wlv1VoiceParse-এর সাথে হুবহু এক রাখতে হবে (নিয়ম ৮)
        return when {
            hasSale && hasMedicine -> Metric.MEDICINE_SALE
            hasSale && hasSaline -> Metric.SALINE_SALE
            hasNoShow -> Metric.NO_SHOW
            hasChamberUnclosed -> Metric.CHAMBER_UNCLOSED
            hasOutMissing -> Metric.OUT_MISSING
            hasWfh -> Metric.WFH_COUNT
            hasDuplicate -> Metric.DUPLICATE_PATIENTS
            hasCallsPending -> Metric.CALLS_PENDING
            hasFuCallsDone -> Metric.FOLLOWUP_CALLS_DONE
            hasMessages -> Metric.MESSAGES_SENT
            hasFeeUnpaid -> Metric.FEE_UNPAID
            hasFeeReturn -> Metric.FEE_RETURN
            hasReferralReq -> Metric.REFERRAL_REQUESTS
            hasPayReq -> Metric.PAYMENT_REQUESTS
            hasProductDue -> Metric.MEDICINE_DUE
            hasHandoverPending -> Metric.HANDOVER_PENDING
            hasHandover -> Metric.CASH_HANDOVER
            hasAppointment -> Metric.APPOINTMENT_COUNT
            hasExpected -> Metric.EXPECTED_COUNT
            hasLeave -> Metric.LEAVE_COUNT
            hasDoctorReminder -> Metric.DOCTOR_REMINDER
            hasReminder -> Metric.STAFF_REMINDER_OPEN
            hasFieldVisit -> Metric.FIELD_VISIT
            hasStaffHours -> Metric.STAFF_HOURS
            hasStaffPresent -> Metric.STAFF_PRESENT
            hasRmpCallDue -> Metric.RMP_CALL_DUE
            hasRmpCalled -> Metric.RMP_CALLED
            hasNewPatients -> Metric.NEW_PATIENTS
            hasDisease -> Metric.DISEASE_COUNT
            hasAdvance -> Metric.RMP_ADVANCE
            hasRmpDue -> Metric.RMP_DUE
            hasTrash -> Metric.TRASH_COUNT
            hasCall -> Metric.CALL_COUNT
            hasRefund -> Metric.REFUND
            hasEnquiry -> Metric.ENQUIRY_COUNT
            hasMoney -> Metric.COLLECTION
            hasPatientCount -> Metric.REGISTRATION_COUNT
            else -> null
        }
    }

    /** এই লেখাটা প্রশ্নের মতো মনে হচ্ছে কিনা — এটাই দেখা হয় সাধারণ নাম/নম্বর
     *  খোঁজার (ভারী ক্লাউড-পড়া) আগে, যাতে সাধারণ Search কখনো আটকে না যায়। */
    fun isQuestionLike(q: String): Boolean =
        q.contains("কত") || q.contains("কালেকশন") || q.contains("বিক্রি") || q.contains("হাজির")

    fun parse(q: String): Parsed? {
        val (branch, branchLabel) = findBranch(q) ?: return null
        val metric = findMetric(q) ?: return null
        val extra = if (metric == Metric.DISEASE_COUNT) (findDisease(q) ?: "") else ""   // V1422 — রোগের নাম
        // 🔒 V1418 (১৩.০৯.২০২৬) — RMP-বাকি কোনো সময়-সীমার প্রশ্ন নয় (fin.rmp_branch_due
        // "এখন পর্যন্ত মোট বাকি" দেখায়, তারিখ নেয় না), তাই এখানেই একমাত্র ব্যতিক্রম —
        // "গতকাল/আজ/সাত দিন/এক মাস" কিছু না বললেও চলবে।
        // V1419/V1420 — SNAPSHOT-এর প্রশ্নগুলো "এখন পর্যন্ত মোট", তারিখ লাগে না।
        if (metric in SNAPSHOT) return Parsed(metric, branch, "", "", branchLabel, "Right now", extra)
        val range = findDateRange(q) ?: return null
        return Parsed(metric, branch, range.from, range.to, branchLabel, range.label, extra)
    }
}
