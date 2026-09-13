package com.tkbiswas.pilesclinic.native

import com.tkbiswas.pilesclinic.modules.ModuleAuth
import org.json.JSONArray
import org.json.JSONObject

/* 🎤🔒 V1415 (১৩.০৯.২০২৬, TK-নির্দেশ "কাজ শুরু করে দিন", তালিকা ৫২০) —
 * Search-এর ভয়েস/টাইপ-প্রশ্নের জবাবের জন্য সার্ভারের `reports` স্কিমার ছোট্ট
 * ফাংশনগুলো (কোনো বাল্ক-ডাউনলোড নয়, প্রতিটা ডাক থেকে মাত্র একটা সংখ্যা/অল্প
 * কয়েকটা সারি আসে — ফ্রি প্ল্যান-নিরাপদ)।
 * ⛔ নামটা ইচ্ছে করে `ReportsRepository` নয় — সেই নামে আগে থেকেই একটা ক্লাস
 * আছে (Master-এর পুরনো Reports পর্দার জন্য, `ReportsRepository.kt`); প্রথমবার
 * ভুল করে সেটার উপরেই লিখে ফেলেছিলাম, git থেকে ফিরিয়ে এই আলাদা নামে আনা হলো। */
object VoiceReportRepository {
    data class RepoResult<T>(val ok: Boolean, val value: T? = null, val message: String = "")

    data class RegisteredPatient(val patientRowId: String, val patientCode: String, val name: String, val mobile: String, val registrationDate: String)
    data class CollectionSummary(val total: Double, val patientCount: Int, val paymentCount: Int)
    data class CollectionRow(val paymentId: String, val patientRowId: String, val name: String, val mobile: String, val amount: Double, val mode: String, val payType: String, val paidOn: String)

    fun patientsRegisteredCount(branch: String, from: String, to: String): RepoResult<Int> {
        val rpc = ModuleAuth.rpc("reports", "patients_registered_count", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        val body = rpc.body.trim()
        if (body == "null") return RepoResult(false, message = "Not allowed for this branch")
        val n = body.toIntOrNull() ?: return RepoResult(false, message = "Invalid response")
        return RepoResult(true, n)
    }

    fun patientsRegisteredList(branch: String, from: String, to: String): RepoResult<List<RegisteredPatient>> {
        val rpc = ModuleAuth.rpc("reports", "patients_registered_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<RegisteredPatient>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(RegisteredPatient(x.optString("patient_row_id"), x.optString("patient_code"), x.optString("name"), x.optString("mobile"), x.optString("registration_date")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun collectionSummary(branch: String, from: String, to: String): RepoResult<CollectionSummary> {
        val rpc = ModuleAuth.rpc("reports", "collection_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, CollectionSummary(x.optDouble("total", 0.0), x.optInt("patient_count", 0), x.optInt("payment_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun collectionList(branch: String, from: String, to: String): RepoResult<List<CollectionRow>> {
        val rpc = ModuleAuth.rpc("reports", "collection_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<CollectionRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(CollectionRow(x.optString("payment_id"), x.optString("patient_row_id"), x.optString("name"), x.optString("mobile"), x.optDouble("amount", 0.0), x.optString("mode"), x.optString("pay_type"), x.optString("paid_on")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }
}
