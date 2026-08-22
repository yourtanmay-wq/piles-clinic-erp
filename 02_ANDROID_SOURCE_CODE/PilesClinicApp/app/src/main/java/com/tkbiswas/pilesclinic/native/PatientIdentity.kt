package com.tkbiswas.pilesclinic.native

import org.json.JSONArray
import org.json.JSONObject

/**
 * TK-REQUESTED (2026-07-27), "এক রোগী = এক রেকর্ড" ধাপ ২ (safe first part):
 * ONE shared rule for "which rows belong to this patient", so every screen
 * answers that question the same way.
 *
 * WHY THIS EXISTS
 * A patient's rows are filed under two different identities depending on which
 * app/screen wrote them:
 *   - this native app files a payment under the patients ROW id (pat_...)
 *   - the web app's Chamber screen files it under the human Patient ID code
 *     (e.g. KNE-260727-001)
 *   - older rows and "Marked Arrived" rows carry no identity at all
 * A screen that asked for only ONE of these silently missed the rest -- one of
 * the real ways the same patient's money could look different on two screens.
 *
 * WHAT IT DOES
 * Builds a filter that matches EITHER identity in a single cloud request (the
 * same "or=(...)" form this project already uses for clinical records), so a
 * screen can only ever find MORE of this patient's own rows than before, never
 * fewer, at no extra cloud quota.
 *
 * WHAT IT DELIBERATELY DOES NOT DO
 *  - It does NOT match by mobile. Mobile-matching would pull in another family
 *    member's money when a family shares one number.
 *  - It does NOT hide a row that names a different patient. Hiding money is far
 *    more dangerous than showing an extra row, and that decision needs a look at
 *    TK's live database first -- his own standing rule after the 2026-07-27
 *    column-list mistake.
 */
object PatientIdentity {

    /**
     * Filter for rows belonging to this patient by identity alone.
     * Returns null when neither identity is known -- the caller must then keep
     * doing exactly what it did before.
     */
    fun identityFilter(patientRowId: String, patientCode: String): String? {
        val rowId = patientRowId.trim()
        val code = patientCode.trim()
        return when {
            rowId.isNotBlank() && code.isNotBlank() && code != rowId ->
                "or=(patientId.eq.$rowId,patientId.eq.$code)"
            rowId.isNotBlank() -> "patientId=eq.$rowId"
            code.isNotBlank() -> "patientId=eq.$code"
            else -> null
        }
    }

    /** This patient's payment rows, under either identity. */
    fun paymentsFor(patientRowId: String, patientCode: String, limit: Int = 500): JSONArray {
        val filter = identityFilter(patientRowId, patientCode) ?: return JSONArray()
        return SupabaseClient.fetchList("payments", filter, limit)
    }

    /**
     * TK-REPORTED CLASS OF BUG: when the same person has TWO "patients" rows
     * (a duplicate registration), different screens picked a different one --
     * the money screen picked the row with a real bill, Patient Details and
     * the Report Card took whichever row the cloud happened to return first.
     * The same patient then showed a different Bill / Patient ID / address on
     * two screens. This is that ONE rule, taken exactly as it already works in
     * the payment screen (PaymentRepository.findPatientByMobile):
     *   1. a row in the branch being worked in, if a branch is given;
     *   2. otherwise the row that carries a real bill (the live treatment
     *      record) rather than an empty duplicate;
     *   3. otherwise the first row, exactly as before.
     * With only one row (the normal case) this returns that row and nothing
     * about any screen changes.
     */
    fun pickPatientRow(rows: JSONArray, preferBranch: String = ""): JSONObject? {
        if (rows.length() == 0) return null
        val first = rows.optJSONObject(0)
        if (rows.length() == 1) return first
        var branchMatch: JSONObject? = null
        var billed: JSONObject? = null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (branchMatch == null && preferBranch.isNotBlank() &&
                row.s("branch").equals(preferBranch, ignoreCase = true)
            ) branchMatch = row
            if (billed == null && row.optDouble("bill", 0.0) > 0.0) billed = row
        }
        return branchMatch ?: billed ?: first
    }

    /**
     * 🔵🔒 V530 (২২.০৮.২০২৬, TK-নির্দেশ: *"১-২-৩ একসাথে ধরুন, কিন্তু খুব সাবধানে"*)
     *
     * **এক নম্বরে সত্যিই ক'জন আলাদা রোগী আছেন** — এক জায়গায় লেখা সেই একটাই নিয়ম।
     *
     * ⛔ এটা **নতুন নিয়ম নয়** — `PaymentRepository.identitiesOnMobile()`-এ V520
     *    থেকে যে নিয়মটা চলছে ও পরীক্ষায় পাশ করেছে, হুবহু সেটাই এখানে তোলা হলো,
     *    যাতে ছবি · প্রিন্ট · ডাক্তারের পর্দাও **একই উত্তর** পায়।
     *
     * নিয়ম দুটো ভাগে:
     *   ১) ভুল করে দু'বার রেজিস্ট্রেশন হয়ে যাওয়া সারিগুলো **একজন ধরেই** গোনা হয়
     *      (`pickPatientRow` — V143, খাতার সারি B30)।
     *   ২) স্টাফ নিজে *"Different Patient — Same Mobile"* চেপে যাঁদের ঘোষণা
     *      করেছেন, তাঁরা **প্রত্যেকে আলাদা** (`isDeclaredSeparateRowId`)।
     *
     * ⛔ **রোজকার ৯৯% নম্বরে তালিকার আকার = ১** — তখন ডাকা জায়গাগুলো আগের মতোই
     *    সোজা পথে চলে, একটাও বাড়তি প্রশ্ন বা বাড়তি ক্লাউড-অনুরোধ নয়।
     * ⛔ ইতিমধ্যে **আনা সারিগুলোর উপরেই** কাজ করে — নতুন কোনো query করে না।
     */
    fun separateIdentities(
        rows: JSONArray,
        mobileDigits: String,
        preferBranch: String = ""
    ): List<JSONObject> {
        val d = mobileDigits.filter { it.isDigit() }.takeLast(10)
        val out = mutableListOf<JSONObject>()
        if (rows.length() == 0) return out
        val ordinary = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (!PatientModel.isDeclaredSeparateRowId(row.s("id"), d)) ordinary.put(row)
        }
        pickPatientRow(ordinary, preferBranch)?.let { out.add(it) }
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (PatientModel.isDeclaredSeparateRowId(row.s("id"), d)) out.add(row)
        }
        return out
    }

    /**
     * 🔵🔒 V530: ডাকার জায়গা যদি **কোন রোগী** তা জানে, ঠিক সেই সারিটাই।
     *
     * দুই ধাপে খোঁজা — **আগে row id, তারপর Patient ID কোড**। (এই ক্রমটা
     * ইচ্ছাকৃত: V522-এ আমার নিজের পরীক্ষায় ধরা পড়েছিল যে এক লুপে দুটো একসাথে
     * মেলালে দুটোয় অমিল হলে **যে সারিটা আগে আসে সেটাই জিতে যায়** — কোড
     * row id-কে হারিয়ে দিত। তাই row id-ই সবসময় আগে।)
     *
     * ⛔ দুটোই ফাঁকা ⇒ `pickPatientRow` — অর্থাৎ **হুবহু আগের নিয়ম**।
     */
    fun chooseRow(
        rows: JSONArray,
        preferBranch: String = "",
        preferRowId: String = "",
        preferPatientCode: String = ""
    ): JSONObject? {
        if (rows.length() == 0) return null
        if (preferRowId.isNotBlank()) {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (r.s("id") == preferRowId) return r
            }
        }
        if (preferPatientCode.isNotBlank()) {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (r.s("patientId") == preferPatientCode) return r
            }
        }
        return pickPatientRow(rows, preferBranch)
    }

}
