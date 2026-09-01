package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONObject

/**
 * 🔴🔴🔒 V938 (৩১.০৮.২০২৬, TK-নির্দেশ · ডেমো-প্রুফে অনুমোদিত: *"হ্যাঁ পাশ"*)
 *
 * TK: *"উক্ত পেশেন্টের আজকে কি চিকিৎসা করা হলো — এটা যদি ডাক্তার চেকআপের সময়
 * করে দেয়, সেক্ষেত্রে সেখান থেকে যেন অটোমেটিক এখানে (চেম্বারে) চলে আসে।"*
 *
 * ### এটা নতুন কোনো পথ নয়
 * চেম্বার বোর্ডে Treatment Progress লেখা হলে ঠিক **দুটো** জায়গায় বসে
 * (`ChamberAttendanceActivity.writeTreatment` + `syncProgressToReportCard`):
 *   ১. রোগীর `followups` সারির `lastRemark` + `lastRemarkAt` (+ history)
 *   ২. ওই দিনের `payments` সারির `progress` ঘর — চেম্বার বোর্ডের **আসল উৎস**
 *      (V687-এ প্রমাণিত)
 * এই ফাইলটা হুবহু সেই দুটো কাজই করে, যাতে ডাক্তারের লেখা চেম্বার Date ·
 * Report Card · ছাপা রেজিস্টার — সব জায়গায় এক লেখা দেখায়।
 *
 * ⛔ লেখা ফাঁকা হলে কিছুই করে না — পুরনো Progress কখনো মুছে যায় না।
 * ⛔ পুরো কাজটা best-effort: ব্যর্থ হলেও চেকআপ সেভ আটকায় না
 *    (ডাকা হয় `BackgroundWork.run{}`-এর ভিতর থেকে)।
 * ⛔ `payments.remarks` ছোঁয়াই হয় না (V533-এর নিয়ম) — শুধু `progress`।
 */
object TodayTreatmentSync {

    /**
     * @param patientRowId রোগীর নিজের সারির আইডি (এক নম্বরে দুজন রোগী থাকলে
     *        ঠিক তাঁরই সারিতে বসানোর জন্য — V536-এর একই নিয়ম)। জানা না থাকলে ফাঁকা।
     */
    fun push(
        context: Context?,
        mobile: String,
        patientRowId: String,
        text: String,
        staffName: String,
        dateKey: String = FollowUpModel.today()
    ) {
        val note = text.trim()
        if (note.isBlank()) return
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        val day = dateKey.ifBlank { FollowUpModel.today() }

        // ── ১. রোগীর Follow-up সারিতে (চেম্বারের `writeTreatment`-এর একই কল) ──
        try {
            val rows = SupabaseClient.findByMobileOrNull(
                "followups", digits, "id,mobile,refId,stage,status", 20
            )
            var bestId = ""
            var bestPr = -1
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val st = r.optString("status", "")
                    if (st.equals("Cancelled", true) || st.equals("Rejected", true) ||
                        st.equals("Closed", true) || st.equals("Incomplete", true)) continue
                    val rid = r.optString("id", "")
                    if (rid.isBlank()) continue
                    // ঘোষিত আলাদা রোগী হলে ঠিক তাঁরই সারি (V536)
                    if (patientRowId.isNotBlank() && r.optString("refId", "") != patientRowId) continue
                    val pr = when (r.optString("stage", "")) {
                        "Treatment" -> 3; "Patient" -> 2; "Visit" -> 1; else -> 0
                    }
                    if (pr > bestPr) { bestPr = pr; bestId = rid }
                }
            }
            if (bestId.isNotBlank()) {
                FollowUpRepository(context).updateRemark(bestId, note, staffName)
            }
        } catch (_: Throwable) { }

        // ── ২. ওই দিনের টাকার সারির `progress` ঘরে (চেম্বার বোর্ডের আসল উৎস) ──
        try {
            val pays = SupabaseClient.fetchList("payments", "mobile=like.*$digits&date=eq.$day", 20)
            for (i in 0 until pays.length()) {
                val p = pays.optJSONObject(i) ?: continue
                val payType = p.optString("payType", "")
                if (payType == "bill_edit" || payType == "chamber_expected") continue
                val owner = p.optString("patientId", "").trim()
                val keep = if (patientRowId.isNotBlank()) owner == patientRowId
                    else !PatientModel.isDeclaredSeparateRowId(owner, digits)
                if (!keep) continue
                val pid = p.optString("id", "")
                if (pid.isBlank()) continue
                val fields = JSONObject().put("progress", note)
                val ok = try { SupabaseClient.updateById("payments", pid, fields) } catch (_: Throwable) { false }
                if (!ok && context != null) GenericUpdateQueue.queue(context, "payments", pid, fields)
            }
        } catch (_: Throwable) { }
    }
}
