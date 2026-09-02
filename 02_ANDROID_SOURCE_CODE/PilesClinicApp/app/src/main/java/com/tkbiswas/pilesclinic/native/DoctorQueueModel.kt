package com.tkbiswas.pilesclinic.native

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native rebuild -- Doctor Queue (data model).
 *
 * Mirrors the WebView's visitQueueRows() in app.js: a patient belongs in the
 * doctor queue when queue==true OR stage is "Doctor Queue"/"Visit", the patient
 * has NOT yet been marked doctorComplete, and the row is not a seeded/demo row.
 *
 * SCOPED LIMITATION (honest disclosure, same style as the other native steps):
 * - isSeed() here is a conservative approximation of app.js's isSeededRecord().
 *   app.js matches an exact seed WORD in name/patientId/etc plus a set of seed
 *   mobiles with no clinic metadata. Live queue patients created through the
 *   native Registration screen always carry createdAt/createdBy, so they can
 *   never be seed rows; this approximation only filters the obvious demo names
 *   without treating ordinary personal names as demo data. The full seededWords()/seededMobiles()
 *   parity can be ported later if a demo row ever reaches the live queue.
 */
data class QueuePatient(
    val id: String,
    val patientId: String,
    val name: String,
    val mobile: String,
    val disease: String,
    val branch: String,
    val photo: String,
    val updatedAt: String,
    val createdAt: String,
    // 🔴 TK-নির্দেশ (04.08.2026): "Report Card তো তখনই বানানোর কথা যখন
    // রোগী Advance করেছেন" -- Take Action/Patient Timeline-এ আগে থেকেই
    // থাকা এই একই নিয়ম (bill > 0 মানে Advance/বিল বসেছে) এখানেও।
    // এটাই ঘুরপথে "নতুন" (আজ প্রথম এসেছেন, এখনো Advance করেননি) বনাম
    // "পুরনো" (আগে Advance হয়ে গেছে) রোগী বোঝার উপায় -- আলাদা কোনো
    // NEW/REPEAT ব্যাজ ছাড়াই।
    val bill: Double = 0.0,
    /* 🩺🔒 V839 (২৯.০৮.২০২৬, TK-নির্দেশ) — কার্ডে NEXT VISIT PLAN-এর ট্যাগ ও
       OLD/NEW ব্যাজ দেখানোর জন্য। দুটোই **ডিফল্ট ফাঁকা** — তাই পুরনো কোনো
       ডাক ভাঙে না, আর মান না এলে কার্ড হুবহু আগের মতোই দেখায়। */
    val registrationDate: String = "",
    val nvpLine: String = "",      // "Dressing · সুতো চেঞ্জ · ঔষধ"
    val nvpWhen: String = "",      // "29.08.2026"
    val nvpBy: String = "",        // "Dr. A. Sarkar"
    val nvpItems: List<String> = emptyList(),   // পপ-আপে পুরো তালিকা
    val nvpMedicine: String = "",               // কোন ঔষধ
    val nvpNote: String = "",                   // ডাক্তারের নিজের লেখা
    /* 🩺🔒 V951 (০১.০৯.২০২৬, TK-নির্দেশ, ফটো-প্রুফ পাশ) — আজ ট্রিটমেন্টের টাকা
       জমা দেওয়া **পুরনো** রোগীও ডাক্তারের লাইনে আসবেন। কার্ডে দেখাবে:
       কত তম ভিজিট · বিল · আজ জমা · বাকি · গত ট্রিটমেন্ট · প্ল্যান।
       ⛔ সব ঘর **ডিফল্ট ফাঁকা/শূন্য** — তাই পুরনো কোনো ডাক ভাঙে না, আর মান না
          এলে কার্ড হুবহু আগের মতোই দেখায় (নতুন রোগীর কার্ড অপরিবর্তিত)। */
    val visitNo: Int = 0,                       // কত তম ভিজিট (০ = জানা নেই)
    val paidToday: Double = 0.0,                // আজ জমা
    val paidTotal: Double = 0.0,                // এ পর্যন্ত মোট জমা
    val lastTreatment: String = "",             // গত যেদিন যা চিকিৎসা হয়েছিল
    val lastTreatmentDate: String = "",         // সেই তারিখ (yyyy-MM-dd)
    val lastTreatmentTime: String = ""          // 🕐 V976 — সেই সময় ("3.42 PM")
) {
    /** 🩺 V951 — `nvpWhen` দেখানোর তারিখ (dd.MM.yyyy); তুলনার জন্য ISO-তে ফেরানো।
     *  বুঝতে না পারলে ফাঁকা ⇒ লেখাটা আগের মতোই "NEXT PLAN" থাকে। */
    fun nvpWhenIso(): String {
        val t = nvpWhen.trim()
        val p = t.split(".", "/", "-")
        return if (p.size == 3 && p[0].length == 2 && p[2].length == 4)
            "${p[2]}-${p[1]}-${p[0]}" else if (t.length >= 10 && t[4] == '-') t.take(10) else ""
    }
}

object DoctorQueueModel {

    /** 🩺 V951 — "1st Visit · 2nd Visit · 3rd Visit …" (ওয়েবের
     *  `wlv1VisitOrdinal()`-এর হুবহু একই লেখা, তাই দুই যন্ত্রে এক দেখায়)। */
    fun visitOrdinal(n: Int): String {
        val r = n % 100; val u = n % 10
        val sfx = if (r in 11..13) "th" else when (u) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$sfx Visit"
    }

    /** 🩺 V951 — প্ল্যানের লেখা তারিখ দেখে নিজে থেকেই বদলায় (TK-এর নিয়ম):
     *  আজ ⇒ TODAY'S PLAN · ভবিষ্যৎ ⇒ NEXT PLAN · পেরিয়ে গেলে ⇒ OVERDUE PLAN। */
    fun planLabel(planIsoDate: String): String {
        val d = planIsoDate.trim().take(10)
        if (d.length < 10) return "NEXT PLAN"
        val t = today()
        return when {
            d == t -> "TODAY'S PLAN"
            d > t -> "NEXT PLAN"
            else -> "OVERDUE PLAN"
        }
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // TK-REQUESTED ADDITION (2026-07-20): true if this patient's queue entry
    // was created today. Used only to split the queue display into "Today"
    // and "Pending / Overdue" sections -- does NOT affect isInQueue() above,
    // so a patient whose check-up is still pending never disappears from the
    // app entirely, they just move to the Overdue section.
    fun isToday(patient: QueuePatient): Boolean {
        val stamp = patient.createdAt.ifBlank { patient.updatedAt }
        return stamp.length >= 10 && stamp.substring(0, 10) == today()
    }

    private val seedWords = setOf(
        "demo", "dummy", "sample", "test patient", "test staff"
    )

    /** Conservative seed check -- see class-level note. */
    private fun isSeed(row: JSONObject): Boolean {
        val hasClinicMeta = row.s("createdAt").isNotBlank() ||
            row.s("updatedAt").isNotBlank() ||
            row.s("createdBy").isNotBlank() ||
            row.s("registeredBy").isNotBlank()
        if (hasClinicMeta) return false
        val name = row.s("name").trim().lowercase()
        val pid = row.s("patientId").trim().lowercase()
        return seedWords.contains(name) || seedWords.contains(pid)
    }

    /** True if this patient row belongs in the doctor queue right now. */
    /* 🔴🔒 V842 (২৯.০৮.২০২৬, TK-এর লাইভ রিপোর্ট, ছবিসহ) —
       *"আজকে তো চেম্বারের ডেট ছিল না কোচবিহারের, তাহলে এত পেসেন্ট কোথা
       থেকে আসলো… এই নম্বর গুলি Today তে কেন দেখাবে?"*

       🔬 আসল কারণ (কোড ধরে যাচাই, আন্দাজ নয়): রোগী তালিকায় ঢোকেন
       রেজিস্ট্রেশনে (`queue=true`), আর বেরোন **শুধু** ডাক্তার চেকআপ Save
       করলে (`doctorComplete=true`)। কেউ যদি এসে চেকআপ না করিয়ে চলে যান,
       তাঁর নাম **চিরকাল** তালিকায় থেকে যেত — TK-এর ছবিতে ০৯.০৩ · ২৭.০৪ ·
       ২৬.০৬-এর রোগী আজও "Today"-তে দেখাচ্ছিল।
       ⛔ এটা V839-এর দোষ নয় — নিয়মটা V838-এও হুবহু এই ছিল (git-এ মিলিয়ে দেখা)।

       ✅ TK-অনুমোদিত সমাধান: **৭ দিনের বেশি** চেকআপ ছাড়া পড়ে থাকলে
          তালিকায় আর দেখাবে না।

       🛡️ কেন এটা নিরাপদ:
       · রোগীর **একটাও তথ্য মোছা হয় না** — শুধু আজকের তালিকায় দেখানো বন্ধ।
         Search · Follow-up · Patient Timeline — সব জায়গায় আগের মতোই আছেন।
       · আবার এলে (পেমেন্ট বা চেম্বারে নাম) `NextVisitQueue` তারিখটা আজকের
         করে দেয় ⇒ **সঙ্গে সঙ্গে ফিরে আসেন**।
       · তারিখ জানা না গেলে (পুরনো/ফাঁকা সারি) **সরানো হয় না** — সন্দেহ হলে
         রেখে দেওয়াই নিরাপদ, নইলে ভুল করে আজকের রোগী হারিয়ে যেত।
       · ৭ দিনের ভিতরের সব রোগী **আগের মতোই** থাকেন — এক অক্ষরও বদলায়নি। */
    /* ⏰🔒 V976 (০২.০৯.২০২৬, TK-নির্দেশ) — *"ওভারডিউ বলে কিছু থাকবে না; আজকের
       ২৪ ঘণ্টার মধ্যে চেকআপ না হলে এখানে আর থাকবে না, সরে যাবে"* ও
       *"রাত ১২টা পেরোলেই সরে যাবে"* ⇒ ৭ দিনের বদলে **শুধু আজকের দিনটুকু**।
       🛡️ TK-কে আগেই জানানো হয়েছে: রোগীর একটাও তথ্য মোছে না — Search ·
          Follow-up · Timeline-এ সব থাকে, আর টাকা জমা দিলে বা চেম্বারে নাম
          উঠলে নিজে থেকেই ফিরে আসেন। অসুবিধা: কাল যাঁর চেকআপ হয়নি, তাঁর নাম
          আর এখানে মনে করাবে না — TK জেনেই সিদ্ধান্ত নিয়েছেন। */
    const val QUEUE_STALE_DAYS = 0

    /** সারিটা শেষ কবে ছোঁয়া হয়েছিল — না বোঝা গেলে `null`। */
    private fun ageDaysOrNull(row: JSONObject): Long? {
        val raw = listOf("updatedAt", "visitDate", "registrationDate", "createdAt")
            .map { row.s(it) }.firstOrNull { it.length >= 10 } ?: return null
        return try {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .parse(raw.substring(0, 10)) ?: return null
            val ms = System.currentTimeMillis() - d.time
            if (ms < 0) 0L else ms / (24L * 60 * 60 * 1000)
        } catch (_: Throwable) { null }
    }

    fun isInQueue(row: JSONObject): Boolean {
        if (isSeed(row)) return false
        val doctorComplete = row.optBoolean("doctorComplete", false)
        if (doctorComplete) return false
        val queueFlag = row.optBoolean("queue", false)
        val stage = row.s("stage")
        if (!(queueFlag || stage == "Doctor Queue" || stage == "Visit")) return false
        // 🔴 V842 — বহুদিনের পুরনো, চেকআপ ছাড়া পড়ে থাকা নাম আর দেখাবে না।
        val age = ageDaysOrNull(row) ?: return true   // জানা না থাকলে রেখে দিই
        return age <= QUEUE_STALE_DAYS
    }

    fun parse(row: JSONObject): QueuePatient = QueuePatient(
        id = row.s("id"),
        patientId = row.s("patientId"),
        name = row.s("name"),
        mobile = row.s("mobile"),
        disease = row.s("disease"),
        branch = row.s("branch"),
        photo = row.s("photo"),
        updatedAt = row.s("updatedAt"),
        createdAt = row.s("createdAt"),
        bill = row.optDouble("bill", 0.0),
        registrationDate = row.s("registrationDate"),   // 🩺 V839 — OLD/NEW ঠিক করতে
        /* 🩺 V839 — ঘরগুলো **শুধু জমানো তালিকায়** থাকে (ক্লাউডের সারিতে
           থাকে না), তাই ওখানে না পেলে ফাঁকাই থাকে — কিছুই ভাঙে না। */
        nvpLine = row.s("nvpLine"),
        nvpWhen = row.s("nvpWhen"),
        nvpBy = row.s("nvpBy"),
        nvpItems = row.s("nvpItems").split(",").map { it.trim() }.filter { it.isNotEmpty() },
        nvpMedicine = row.s("nvpMedicine"),
        nvpNote = row.s("nvpNote")
    )

    /** Newest first, matching visitQueueRows()'s sort by
     * updatedAt/createdAt descending. */
    fun sortNewestFirst(list: List<QueuePatient>): List<QueuePatient> =
        list.sortedByDescending { it.updatedAt.ifBlank { it.createdAt } }
}
