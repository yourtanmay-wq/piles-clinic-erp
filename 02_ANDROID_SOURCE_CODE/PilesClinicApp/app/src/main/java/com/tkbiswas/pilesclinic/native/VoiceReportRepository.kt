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
    data class ProductSaleSummary(val total: Double, val saleCount: Int)
    data class ProductSaleRow(val productRowId: String, val customer: String, val mobile: String, val product: String, val bill: Double, val deposit: Double, val due: Double, val mode: String, val soldOn: String)
    data class EnquiryRow(val enquiryRowId: String, val name: String, val mobile: String, val disease: String, val enquiryDate: String)
    data class RefundSummary(val total: Double, val refundCount: Int)
    data class RefundRow(val paymentId: String, val patientRowId: String, val name: String, val mobile: String, val amount: Double, val mode: String, val refundedOn: String)

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

    /* 🎤 V1416 (১৩.০৯.২০২৬, TK-নির্দেশ "শুরু করে দিন", তালিকা ৫২২) — মেডিসিন/
     * স্যালাইন বিক্রি (VOICE_QUERY_PLAN আইটেম ৪ ও ২১) — একই `products` টেবিল,
     * শুধু kind আলাদা, তাই একটাই ফাংশন-জোড়া দুই kind দিয়েই ডাকা হয়। */
    fun productSaleSummary(branch: String, from: String, to: String, kind: String): RepoResult<ProductSaleSummary> {
        val rpc = ModuleAuth.rpc("reports", "product_sale_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to).put("p_kind", kind))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, ProductSaleSummary(x.optDouble("total", 0.0), x.optInt("sale_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun productSaleList(branch: String, from: String, to: String, kind: String): RepoResult<List<ProductSaleRow>> {
        val rpc = ModuleAuth.rpc("reports", "product_sale_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to).put("p_kind", kind))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<ProductSaleRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(ProductSaleRow(x.optString("product_row_id"), x.optString("customer"), x.optString("mobile"), x.optString("product"), x.optDouble("bill", 0.0), x.optDouble("deposit", 0.0), x.optDouble("due", 0.0), x.optString("mode"), x.optString("sold_on")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    /* 🎤 V1417 (১৩.০৯.২০২৬, TK-নির্দেশ "চালিয়ে যান", তালিকা ৫২৩) — এনকোয়ারি-
     * সংখ্যা ও Approved রিফান্ডের টাকা (VOICE_QUERY_PLAN আইটেম ১১ ও ১৪-র রিফান্ড
     * অংশ — ডিসকাউন্ট আলাদা জায়গা থেকে আসে বলে এখানে বসানো হয়নি)। */
    fun enquiryCount(branch: String, from: String, to: String): RepoResult<Int> {
        val rpc = ModuleAuth.rpc("reports", "enquiry_count", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        val body = rpc.body.trim()
        if (body == "null") return RepoResult(false, message = "Not allowed for this branch")
        val n = body.toIntOrNull() ?: return RepoResult(false, message = "Invalid response")
        return RepoResult(true, n)
    }

    fun enquiryList(branch: String, from: String, to: String): RepoResult<List<EnquiryRow>> {
        val rpc = ModuleAuth.rpc("reports", "enquiry_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<EnquiryRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(EnquiryRow(x.optString("enquiry_row_id"), x.optString("name"), x.optString("mobile"), x.optString("disease"), x.optString("enquiry_date")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun refundSummary(branch: String, from: String, to: String): RepoResult<RefundSummary> {
        val rpc = ModuleAuth.rpc("reports", "refund_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, RefundSummary(x.optDouble("total", 0.0), x.optInt("refund_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun refundList(branch: String, from: String, to: String): RepoResult<List<RefundRow>> {
        val rpc = ModuleAuth.rpc("reports", "refund_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<RefundRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(RefundRow(x.optString("payment_id"), x.optString("patient_row_id"), x.optString("name"), x.optString("mobile"), x.optDouble("amount", 0.0), x.optString("mode"), x.optString("refunded_on")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }
}
