package com.tkbiswas.pilesclinic.native

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class RegistrationDraft(
    val date: String,
    val name: String,
    val mobileDigitsOnly: String,
    // 🔒 V235 (TK verified 01.08.2026): Primary = mobileDigitsOnly। নতুন
    // altMobileDigitsOnly = Alternate/Enquiry নম্বর (default ফাঁকা, তাই পুরনো
    // কোনো caller ভাঙে না)। Enquiry থেকে Registration খুললে পুরনো Enquiry
    // নম্বর এখানে বসে; একই হলে ফাঁকা রাখা হয় (duplicate নয়)।
    val altMobileDigitsOnly: String = "",
    val branch: String,
    val age: String,
    val sex: String,
    val village: String,
    val po: String,
    val ps: String,
    val district: String,
    val pin: String,
    val occupation: String,
    val refBy: String,
    val diseases: List<String>,
    val symptoms: List<String>,
    val complaintNote: String,
    val medicalHistory: List<String>,
    // 🔴🆕🔒 খাতার সারি B452 (TK-নির্দেশ, 05.08.2026) — Registration-এর
    // সময় স্টাফের লেখা "কতদিন থেকে সমস্যা"/"আগের চিকিৎসা"। ⛔ ডিফল্ট
    // ফাঁকা — পুরনো কোনো caller ভাঙে না।
    val durationNote: String = "",
    val prevTreatmentNote: String = "",
    val regFee: Double,
    val payMode: String,
    val photo: String = "",
    val refDoctor: String = "",
    val refDoctorMobile: String = "",
    // TK-REQUESTED ADDITION (2026-07-24): same concept as Enquiry's
    // timeType ("Official Time"/"Unexpected Time") -- default preserves
    // every existing caller of this data class unchanged.
    val timeType: String = "Official Time"
)

object PatientModel {

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun normalizedMobile(digitsOnly: String): String = "+91$digitsOnly"

    /** এক মোবাইল = এক সারি। রোগীর সারির স্থায়ী আইডি, মোবাইল থেকেই তৈরি।
     *  নম্বরটা কোনো কারণে ১০ সংখ্যার না হলে আগের মতোই এলোমেলো আইডি হবে,
     *  যাতে কোনো সেভ কখনো আটকে না যায়। */
    fun stableRowId(mobileDigits: String): String {
        val d = mobileDigits.filter { it.isDigit() }.takeLast(10)
        return if (d.length == 10) "pat_$d"
        else "pat_" + UUID.randomUUID().toString().replace("-", "")
    }

    /** Matches app.js's address-building logic exactly: only non-empty parts,
     * "Label: value" joined by ", ". */
    fun buildAddress(draft: RegistrationDraft): String {
        val parts = listOf(
            "Vill" to draft.village, "PO" to draft.po, "PS" to draft.ps,
            "Dist" to draft.district, "PIN" to draft.pin
        ).filter { it.second.isNotBlank() }.map { "${it.first}: ${it.second}" }
        return parts.joinToString(", ")
    }

    /** Builds the "patients" row, matching savePatient()'s field-for-field. */
    fun buildPatientRow(draft: RegistrationDraft, patientId: String, createdByMobile: String, existingRowId: String = ""): JSONObject {
        val now = isoNow()
        val diagnosis = draft.diseases.joinToString(", ")
        val symptomsJoined = draft.symptoms.joinToString(", ")
        val complaint = listOf(symptomsJoined, draft.complaintNote).filter { it.isNotBlank() }.joinToString(" | ")
        return JSONObject()
            // 🔒 TK'S ORDER (28.07.2026, খাতার সারি B30): *"একই পেশেন্টের নামে দুটো
            // আইডি যেন চালু না হয় — সেটার ব্যবস্থা করুন।"*
            //
            // আগে নতুন রোগীর সারির আইডি ছিল **এলোমেলো** (`pat_<random>`)। তাই দুই
            // স্টাফ একই নম্বর একই সময়ে ভরলে — বা লাইন খারাপ থাকায় খোঁজাটা ফসকে
            // গেলে — **দুটো আলাদা সারি** তৈরি হয়ে যেত।
            //
            // এখন আইডিটা **মোবাইল নম্বর থেকেই তৈরি হয়** (`pat_<শেষ ১০ সংখ্যা>`)।
            // ক্লাউডে লেখা হয় "একই আইডি হলে মিশিয়ে দাও" নিয়মে — তাই একই নম্বরে
            // যতবারই সারি তৈরির চেষ্টা হোক, **সব একটাই সারিতে গিয়ে মেশে**।
            // দুটো সারি তৈরি হওয়া আর সম্ভব নয়।
            //
            // ⛔ পুরনো সারিগুলো ছোঁয়া হয়নি — "Update Existing"-এ আগের মতোই
            // existingRowId ব্যবহার হয়, তাই কোনো পুরনো রেকর্ড নড়বে না।
            // 🔒 এটা TK-এর নিজের নিয়মেরই সঙ্গে মেলে: "এক মোবাইল একবারই রেজিস্টার
            // হবে; নম্বর না থাকলে স্টাফ ডেমি নম্বর ব্যবহার করবে।"
            // (একই কৌশল আগে থেকেই চালু আছে — "আসার কথা" সারির আইডি `exp_<১০ সংখ্যা>`।)
            .put("id", existingRowId.ifBlank { stableRowId(draft.mobileDigitsOnly) })
            .put("patientId", patientId)
            .put("date", draft.date)
            .put("registrationDate", draft.date)
            .put("visitDate", draft.date)
            .put("name", draft.name)
            .put("mobile", normalizedMobile(draft.mobileDigitsOnly))
            // 🔒 V235 (TK, Primary/Alternate Mobile · safe-fallback): Alternate/Enquiry নম্বর।
            // ⚠️ SQL চালানোর আগেও যেন HTTP 400 না হয়: `altMobile` **শুধু তখনই** JSON-এ যায়
            // যখন সত্যিকারের একটা আলাদা নম্বর আছে (ফাঁকা হলে key-টাই পাঠানো হয় না)। তাই
            // সাধারণ registration-এ (alt নম্বর নেই) column না থাকলেও কোনো 400/queue-আটকা হয় না;
            // এবং merge-duplicates upsert-এ ফাঁকা "" পাঠিয়ে পুরনো altMobile মুছে যাওয়ার
            // ঝুঁকিও নেই। Primary-র সমান হলে বসে না (dedup — RegistrationActivity-তেও করা)।
            .apply {
                val alt = if (draft.altMobileDigitsOnly.isNotBlank()) normalizedMobile(draft.altMobileDigitsOnly) else ""
                if (alt.isNotBlank() && alt != normalizedMobile(draft.mobileDigitsOnly)) put("altMobile", alt)
            }
            .put("branch", draft.branch)
            .put("age", draft.age)
            .put("sex", draft.sex)
            .put("address", buildAddress(draft))
            .put("occupation", draft.occupation)
            .put("refBy", draft.refBy)
            .put("refDoctor", draft.refDoctor)
            .put("refDoctorMobile", draft.refDoctorMobile)
            .put("disease", diagnosis.ifBlank { "Piles" })
            .put("diagnosis", diagnosis)
            .put("sinceWhen", draft.durationNote)
            .put("complaint", complaint)
            .put("medicalHistory", draft.medicalHistory.joinToString(", "))
            // 🔴🆕🔒 B452 — DoctorCheckupActivity.kt-এর "History & Previous"
            // ধাপ `first("previousTreatment", "prevTreatment",
            // "medicalHistory")` দিয়ে প্রথমে এই কলামটাই খোঁজে, তাই এখানে
            // বসালেই ডাক্তার Checkup খোলার সময় স্বয়ংক্রিয়ভাবে প্রি-ফিল
            // পাবেন। ⛔ উপরের `medicalHistory` (চেকবক্স-তালিকা, ভিন্ন
            // জিনিস) ছোঁয়া হয়নি, শুধু নতুন এই একটা কলাম।
            .put("previousTreatment", draft.prevTreatmentNote)
            .put("photo", draft.photo)
            .put("createdBy", createdByMobile)
            .put("registeredBy", createdByMobile)
            .put("stage", "Doctor Queue")
            .put("queue", true)
            .put("doctorComplete", false)
            .put("bill", 0)
            // TK-REQUESTED ADDITION (2026-07-24): same Official/Unexpected
            // Time concept Enquiry already has -- threaded through
            // patientRow the same way disease/address/age/sex already are,
            // so buildVisitFollowUpRow below can read it back.
            .put("timeType", draft.timeType)
            .put("createdAt", now)
            .put("updatedAt", now)
    }

    /** Builds the matching "followups" row (stage=Patient), matching
     * canonicalVisitFollowRow() exactly, so the patient shows up in the
     * Visit tab immediately, same as a WebView registration does. */
    /**
     * 🔴🔒 V399 (16.08.2026, TK-রিপোর্ট ছবিসহ: "অনেক জিনিস ২ বার ৩ বার হয়ে যাচ্ছে")।
     *
     * **আসল কারণ (কোড পড়ে নিশ্চিত):** এখানে প্রতিবার `"fu_" + UUID.randomUUID()`
     * বসত — অর্থাৎ একই রোগীকে দ্বিতীয়বার সেভ করলে (বা "Update Existing" চাপলে)
     * ক্লাউডে **নতুন একটা Follow-up সারি ঢুকে যেত**, পুরোনোটা আপডেট হত না।
     * প্রতিটা নতুন সারির নিজস্ব `history[0] = "Registered patient / Visit created"`
     * থাকায় রোগীর টাইমলাইনে ওই লেখাটা ২-৩ বার (আলাদা তারিখে) দেখা যেত।
     *
     * **অসমতাই প্রমাণ:** একই ফাংশনে রোগীর সারির আইডি স্থায়ী (`stableRowId`,
     * লাইন ৬১-৬৫) আর Visit Fee-তে রক্ষা আছে (`RegistrationRepository.kt:140`,
     * TK নিজে ২৫.০৭.২০২৬-এ ধরিয়েছিলেন) — শুধু Follow-up সারিটাই বাদ পড়েছিল।
     *
     * **সমাধান:** পুরোনো Follow-up সারি থাকলে তার **নিজের আইডিই** ব্যবহার হয়,
     * তাই নতুন সারি তৈরি হয় না — আপডেট হয়।
     * ⛔ **সবচেয়ে জরুরি সুরক্ষা:** পুরোনো সারি ব্যবহার করার সময় `history` ও
     *    `lastRemark` **পাঠানোই হয় না** — নইলে upsert পুরোনো সব কল-ইতিহাস
     *    মুছে দিত। ঘর না পাঠালে ওই ঘর অপরিবর্তিত থাকে (স্থানীয় স্টোরও
     *    `LocalWorkflowStore.upsertFollowUp` ঘর-ধরে-ঘর মেশায়, লাইন ৭২-৭৩)।
     * ⛔ পুরোনো সারি না পেলে (নতুন রোগী · নেট নেই) আগের হুবহু আচরণ — নতুন আইডি
     *    ও "Registered patient / Visit created" লেখা।
     */
    fun buildVisitFollowUpRow(
        patientRow: JSONObject,
        staffMobile: String,
        existingFollowUpRowId: String = ""
    ): JSONObject {
        val now = isoNow()
        val visitDate = patientRow.getString("visitDate")
        val reuse = existingFollowUpRowId.isNotBlank()
        val history = JSONArray().put(
            JSONObject()
                .put("date", patientRow.getString("date"))
                .put("remark", "Registered patient / Visit created")
                .put("staff", staffMobile)
        )
        val out = JSONObject()
            .put("id", if (reuse) existingFollowUpRowId else "fu_" + UUID.randomUUID().toString().replace("-", ""))
            .put("refId", patientRow.getString("id"))
            .put("patientId", patientRow.getString("patientId"))
            .put("mobile", patientRow.getString("mobile"))
            .put("name", patientRow.getString("name"))
            .put("branch", patientRow.getString("branch"))
            .put("disease", patientRow.getString("disease"))
            .put("address", patientRow.getString("address"))
            .put("age", patientRow.s("age"))
            .put("sex", patientRow.s("sex"))
            .put("stage", "Patient")
            .put("date", visitDate)
            .put("registrationDate", patientRow.getString("registrationDate"))
            .put("visitDate", visitDate)
            .put("nextFollow", "")
            .put("status", "Active")
            // TK-REQUESTED ADDITION (2026-07-24): same Official/Unexpected
            // Time badge concept the Enquiry-created followups row already
            // carries -- read back from patientRow (set in buildPatientRow
            // above). optString default keeps this safe even for any
            // existing/older patientRow that doesn't have it.
            .put("timeType", patientRow.s("timeType").ifBlank { "Official Time" })
            .put("createdBy", staffMobile)
            .put("createdAt", now)
            .put("updatedAt", now)
        if (!reuse) {
            /* নতুন সারি — আগের মতোই ইতিহাস, লেখা ও গণনা বসে। */
            out.put("history", history)
            out.put("lastRemark", "Registered patient / Visit created")
            out.put("callCount", 0)
        }
        return out
    }

    /** Builds the Visit Fee payment row, matching savePatient()'s payment
     * add() call. Only called when regFee > 0 (always true here since the
     * fee is mandatory to reach Save at all). */
    fun buildVisitFeePaymentRow(patientRow: JSONObject, draft: RegistrationDraft, staffMobile: String): JSONObject {
        val now = isoNow()
        return JSONObject()
            .put("id", "pay_" + UUID.randomUUID().toString().replace("-", ""))
            .put("payType", "visit_fee")
            .put("payLabel", "Visit Fee")
            .put("paymentLabel", "Visit Fee")
            .put("patientId", patientRow.getString("id"))
            // 🆔 TK-এর নিয়ম (28.07.2026): মানুষের পড়ার Patient ID-ও সঙ্গে থাকে,
            // যাতে টাকার তালিকায় নাম-মোবাইলের পাশে ID দেখানো যায়।
            // ⛔ বাড়তি কোনো ক্লাউড-কল নয় — একই সারিতে একটা ঘর বেশি।
            .put("patientCode", patientRow.s("patientId"))
            .put("mobile", patientRow.getString("mobile"))
            .put("branch", patientRow.getString("branch"))
            .put("name", patientRow.getString("name"))
            .put("date", draft.date)
            .put("amount", draft.regFee)
            .put("mode", draft.payMode)
            .put("remarks", "Visit Fee")
            .put("receivedBy", staffMobile)
            .put("createdBy", staffMobile)
            .put("createdAt", now)
            .put("updatedAt", now)
    }
}
