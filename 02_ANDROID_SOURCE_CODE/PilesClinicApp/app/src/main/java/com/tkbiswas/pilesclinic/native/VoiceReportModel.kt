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

    enum class Metric { REGISTRATION_COUNT, COLLECTION, MEDICINE_SALE, SALINE_SALE, ENQUIRY_COUNT, REFUND, CASH_HANDOVER, RMP_DUE }

    data class Parsed(
        val metric: Metric,
        val branch: String,
        val from: String,   // yyyy-MM-dd
        val to: String,     // yyyy-MM-dd
        val branchLabel: String,
        val periodLabel: String
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
            q.contains("গতকাল") -> Range(iso(t.minusDays(1)), iso(t.minusDays(1)), "Yesterday")
            q.contains("আজ") -> Range(iso(t), iso(t), "Today")
            q.contains("সাত দিন") || q.contains("7 din") || q.contains("last 7") ->
                Range(iso(t.minusDays(6)), iso(t), "Last 7 days")
            q.contains("এক মাস") || q.contains("1 mash") ->
                Range(iso(t.minusMonths(1).plusDays(1)), iso(t), "Last 1 month")
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
        val hasRmpDue = q.contains("কমিশন") && (q.contains("বাকি") || q.contains("বাকী"))
        val hasMoney = q.contains("কালেকশন") || q.contains("জমা") || (q.contains("টাকা") && !q.contains("পেশেন্ট"))
        val hasPatientCount = (q.contains("পেশেন্ট") || q.contains("রোগী")) &&
            (q.contains("কতজন") || q.contains("এসেছিল") || q.contains("এসেছে"))
        return when {
            hasSale && hasMedicine -> Metric.MEDICINE_SALE
            hasSale && hasSaline -> Metric.SALINE_SALE
            hasHandover -> Metric.CASH_HANDOVER
            hasRmpDue -> Metric.RMP_DUE
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
        q.contains("কত") || q.contains("কালেকশন") || q.contains("বিক্রি")

    fun parse(q: String): Parsed? {
        val (branch, branchLabel) = findBranch(q) ?: return null
        val metric = findMetric(q) ?: return null
        // 🔒 V1418 (১৩.০৯.২০২৬) — RMP-বাকি কোনো সময়-সীমার প্রশ্ন নয় (fin.rmp_branch_due
        // "এখন পর্যন্ত মোট বাকি" দেখায়, তারিখ নেয় না), তাই এখানেই একমাত্র ব্যতিক্রম —
        // "গতকাল/আজ/সাত দিন/এক মাস" কিছু না বললেও চলবে।
        if (metric == Metric.RMP_DUE) return Parsed(metric, branch, "", "", branchLabel, "Right now")
        val range = findDateRange(q) ?: return null
        return Parsed(metric, branch, range.from, range.to, branchLabel, range.label)
    }
}
