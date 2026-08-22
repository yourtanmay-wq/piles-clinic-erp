package com.tkbiswas.pilesclinic.clinical

import android.content.Context
import com.tkbiswas.pilesclinic.native.SupabaseClient
import java.net.URLEncoder

/** Patient-bound Prescription print choices. No database/schema change. */
object PrescriptionOptionsStore {
    private const val PREF = "prescription_print_options_v1"
    val defaultFields = linkedSetOf("disease", "symptoms", "since", "complaint")
    val moreFields = linkedMapOf(
        "previousTreatment" to "Previous Treatment",
        "previousResult" to "Previous Result",
        "onset" to "Onset",
        "treatmentDuration" to "Treatment Duration"
    )

    private fun p(context: Context) = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun key(suffix: String) = "${RoleSession.currentPatientId}_$suffix"

    fun selectedFields(context: Context): MutableSet<String> =
        (p(context).getStringSet(key("fields"), defaultFields) ?: defaultFields).toMutableSet()

    fun saveSelectedFields(context: Context, fields: Set<String>) {
        p(context).edit().putStringSet(key("fields"), fields.toSet()).apply()
    }

    fun diet(context: Context): String = p(context).getString(key("diet"), "").orEmpty()
    fun saveDiet(context: Context, value: String) = p(context).edit().putString(key("diet"), value.trim()).apply()

    /* 🔵 V488 (20.08.2026, TK-নির্দেশ): "Sitz Bath — 2 Times" আগে সবসময় টিক দেওয়া
       থাকত, তোলা যেত না (isEnabled = false)। TK-এর সিদ্ধান্ত: তোলা যাবে, আর তুললে
       ছাপা কাগজ থেকেও ADVICE লাইনটা উঠে যাবে।
       ডিফল্ট **true** — অর্থাৎ কেউ হাত না দিলে আগের মতোই টিক দেওয়া, তাই পুরনো
       কোনো রোগীর কাগজ নিজে থেকে বদলাবে না। রোগী ধরে ধরে মনে রাখা হয় (key)। */
    fun sitzBath(context: Context): Boolean = p(context).getBoolean(key("sitzBath"), true)
    fun saveSitzBath(context: Context, on: Boolean) = p(context).edit().putBoolean(key("sitzBath"), on).apply()

    fun captureCheckup(context: Context, r: CheckupRecord) {
        p(context).edit()
            .putString(key("symptoms"), listOf(r.visual, r.visualOther).filter { it.isNotBlank() }.joinToString(", "))
            .putString(key("since"), r.duration.trim())
            .putString(key("complaint"), r.complaint.trim())
            .putString(key("previousTreatment"), r.prevTreatment.trim())
            .putString(key("previousResult"), r.prevResult.trim())
            .putString(key("onset"), r.acuteChronic.trim())
            .putString(key("treatmentDuration"), r.treatmentDuration.trim())
            .apply()
    }

    /** Refresh only this patient's last saved Doctor Check-up. A failed read
     * keeps the patient-bound phone copy unchanged; no guessed value is used. */
    fun refreshFromLatestCheckup(context: Context) {
        val patientId = RoleSession.currentPatientId.trim()
        if (patientId.isBlank()) return
        val encodedId = URLEncoder.encode(patientId, "UTF-8")
        val rows = SupabaseClient.fetchListOrNull(
            table = "medical",
            filter = "patientId=eq.$encodedId&type=eq.Doctor%20Checkup",
            limit = 1,
            order = "createdAt.desc.nullslast",
            select = "details"
        ) ?: return
        if (rows.length() == 0) return
        val details = rows.optJSONObject(0)?.optString("details").orEmpty()
        if (details.isBlank()) return
        val values = linkedMapOf<String, String>()
        details.split(';').forEach { part ->
            val i = part.indexOf(':')
            if (i > 0) values[part.substring(0, i).trim()] = part.substring(i + 1).trim()
        }
        p(context).edit()
            .putString(key("symptoms"), values["Visual"].orEmpty())
            .putString(key("since"), values["Duration"].orEmpty())
            .putString(key("complaint"), values["Complaint"].orEmpty())
            .putString(key("previousTreatment"), values["Prev Treatment"].orEmpty())
            .putString(key("previousResult"), values["Prev Result"].orEmpty())
            .putString(key("onset"), values["Onset"].orEmpty())
            .putString(key("treatmentDuration"), values["Treatment Duration"].orEmpty())
            .apply()
    }

    fun printLines(context: Context): List<String> {
        val prefs = p(context)
        val selected = selectedFields(context)
        fun value(field: String): String = when (field) {
            "disease" -> RoleSession.currentPatientDisease.trim()
            else -> prefs.getString(key(field), "").orEmpty().trim()
        }
        val labels = linkedMapOf(
            "disease" to "DISEASE", "symptoms" to "SYMPTOMS",
            "since" to "DURATION", "complaint" to "CHIEF COMPLAINT",
            "previousTreatment" to "PREVIOUS TREATMENT", "previousResult" to "PREVIOUS RESULT",
            "onset" to "ONSET", "treatmentDuration" to "TREATMENT DURATION"
        )
        return labels.mapNotNull { (field, label) ->
            if (field !in selected) return@mapNotNull null
            // 🔴 V425 (TK-নির্দেশ ১৭.০৮.২০২৬): *"Not Recorded লেখা থাকবে না ·
            //    প্রয়োজনে ফাকা প্রিন্ট হবে · যাতে প্রিন্ট আউট এর পরে লেখাও যায়"*
            //    ⇒ তথ্য না থাকলে ঘরটা ফাঁকাই যায়; শিরোনাম (SYMPTOMS ইত্যাদি)
            //    আগের মতোই থাকে, নিচে হাতে লেখার জায়গা ছাড়া হয় (rx_print.html)।
            val v = value(field)
            "$label\n$v"
        }
    }
}
