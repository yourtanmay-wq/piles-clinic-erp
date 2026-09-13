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
    data class HandoverSummary(val total: Double, val dayCount: Int)
    data class HandoverRow(val handoverDate: String, val cash: Double, val receiverName: String, val receivedAt: String)
    data class RmpDueSummary(val totalDue: Double, val rmpCount: Int)
    data class RmpDueRow(val rmpId: String, val rmpName: String, val rmpMobile: String, val due: Double)
    data class ProductDueSummary(val total: Double, val rowCount: Int)
    data class ProductDueRow(val productRowId: String, val customer: String, val mobile: String, val product: String, val due: Double, val soldOn: String)
    data class CallRow(val callRowId: String, val staffCode: String, val targetMobileMask: String, val callDate: String)
    data class TrashRow(val trashRowId: String, val tableName: String, val deletedAt: String, val deletedBy: String)
    data class RmpAdvanceSummary(val total: Double, val advanceCount: Int)
    data class RmpAdvanceRow(val advanceId: String, val rmpName: String, val amount: Double, val mode: String, val paidOn: String)

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

    /* 🎤 V1418 (১৩.০৯.২০২৬, TK-নির্দেশ "চালিয়ে যান", তালিকা ৫২৪) — ক্যাশ
     * হ্যান্ডওভার (VOICE_QUERY_PLAN আইটেম ১৫) ও RMP-দের ব্রাঞ্চ-বাকি (আইটেম ২৫)।
     * ⛔ RMP-বাকি এখানেও কোনো নতুন হিসাব করে না — শুধু আজই বানানো
     * `fin.rmp_branch_due` ডাকা হয় (CLAUDE.md ৭গ-র "একটাই সার্ভার-নিয়ম")। */
    fun cashHandoverSummary(branch: String, from: String, to: String): RepoResult<HandoverSummary> {
        val rpc = ModuleAuth.rpc("reports", "cash_handover_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, HandoverSummary(x.optDouble("total", 0.0), x.optInt("day_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun cashHandoverList(branch: String, from: String, to: String): RepoResult<List<HandoverRow>> {
        val rpc = ModuleAuth.rpc("reports", "cash_handover_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<HandoverRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(HandoverRow(x.optString("handover_date"), x.optDouble("cash", 0.0), x.optString("receiver_name"), x.optString("received_at")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun rmpDueSummary(branch: String): RepoResult<RmpDueSummary> {
        val rpc = ModuleAuth.rpc("reports", "rmp_due_summary", JSONObject().put("p_branch", branch))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, RmpDueSummary(x.optDouble("total_due", 0.0), x.optInt("rmp_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun rmpDueList(branch: String): RepoResult<List<RmpDueRow>> {
        val rpc = ModuleAuth.rpc("reports", "rmp_due_list", JSONObject().put("p_branch", branch))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<RmpDueRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(RmpDueRow(x.optString("rmp_id"), x.optString("rmp_name"), x.optString("rmp_mobile"), x.optDouble("due", 0.0)))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    /* 🎤 V1419 (১৩.০৯.২০২৬, TK-নির্দেশ "একসাথে যতগুলো সম্ভব") — মেডিসিন/স্যালাইনের
     * বর্তমান বাকি (settlement-সারি বাদ দিয়ে সার্ভারেই আসল হিসাব), অ্যাপ-কল সংখ্যা,
     * ট্র্যাশে-যাওয়া রেকর্ড, RMP-অগ্রিম — সবই reports.* ছোট্ট ফাংশন, বাল্ক নয়। */
    fun productDueSummary(branch: String): RepoResult<ProductDueSummary> {
        val rpc = ModuleAuth.rpc("reports", "product_due_summary", JSONObject().put("p_branch", branch))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, ProductDueSummary(x.optDouble("total", 0.0), x.optInt("row_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun productDueList(branch: String): RepoResult<List<ProductDueRow>> {
        val rpc = ModuleAuth.rpc("reports", "product_due_list", JSONObject().put("p_branch", branch))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<ProductDueRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(ProductDueRow(x.optString("product_row_id"), x.optString("customer"), x.optString("mobile"), x.optString("product"), x.optDouble("due", 0.0), x.optString("sold_on")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun callCount(branch: String, from: String, to: String): RepoResult<Int> {
        val rpc = ModuleAuth.rpc("reports", "call_count", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        val body = rpc.body.trim()
        if (body == "null") return RepoResult(false, message = "Not allowed for this branch")
        val n = body.toIntOrNull() ?: return RepoResult(false, message = "Invalid response")
        return RepoResult(true, n)
    }

    fun callList(branch: String, from: String, to: String): RepoResult<List<CallRow>> {
        val rpc = ModuleAuth.rpc("reports", "call_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<CallRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(CallRow(x.optString("call_row_id"), x.optString("staff_code"), x.optString("target_mobile_mask"), x.optString("call_date")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun trashSummary(branch: String, from: String, to: String): RepoResult<Int> {
        val rpc = ModuleAuth.rpc("reports", "trash_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            RepoResult(true, arr.getJSONObject(0).optInt("total", 0))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun trashList(branch: String, from: String, to: String): RepoResult<List<TrashRow>> {
        val rpc = ModuleAuth.rpc("reports", "trash_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<TrashRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(TrashRow(x.optString("trash_row_id"), x.optString("table_name"), x.optString("deleted_at"), x.optString("deleted_by")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun rmpAdvanceSummary(branch: String, from: String, to: String): RepoResult<RmpAdvanceSummary> {
        val rpc = ModuleAuth.rpc("reports", "rmp_advance_summary", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) return RepoResult(false, message = "Not allowed for this branch")
            val x = arr.getJSONObject(0)
            RepoResult(true, RmpAdvanceSummary(x.optDouble("total", 0.0), x.optInt("advance_count", 0)))
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }

    fun rmpAdvanceList(branch: String, from: String, to: String): RepoResult<List<RmpAdvanceRow>> {
        val rpc = ModuleAuth.rpc("reports", "rmp_advance_list", JSONObject().put("p_branch", branch).put("p_from", from).put("p_to", to))
        if (!rpc.ok) return RepoResult(false, message = rpc.message)
        return try {
            val arr = JSONArray(rpc.body)
            val out = ArrayList<RmpAdvanceRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                out.add(RmpAdvanceRow(x.optString("advance_id"), x.optString("rmp_name"), x.optDouble("amount", 0.0), x.optString("mode"), x.optString("paid_on")))
            }
            RepoResult(true, out)
        } catch (_: Exception) { RepoResult(false, message = "Invalid response") }
    }
}
