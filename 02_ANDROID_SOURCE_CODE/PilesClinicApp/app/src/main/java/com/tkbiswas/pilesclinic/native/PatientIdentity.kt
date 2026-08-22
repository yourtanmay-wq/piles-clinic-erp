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

}
