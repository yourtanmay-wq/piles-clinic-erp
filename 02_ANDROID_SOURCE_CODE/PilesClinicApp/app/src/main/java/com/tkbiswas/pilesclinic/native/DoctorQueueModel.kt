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
    val nvpNote: String = ""                    // ডাক্তারের নিজের লেখা
)

object DoctorQueueModel {

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
    fun isInQueue(row: JSONObject): Boolean {
        if (isSeed(row)) return false
        val doctorComplete = row.optBoolean("doctorComplete", false)
        if (doctorComplete) return false
        val queueFlag = row.optBoolean("queue", false)
        val stage = row.s("stage")
        return queueFlag || stage == "Doctor Queue" || stage == "Visit"
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
