package com.tkbiswas.pilesclinic.native

import org.json.JSONArray
import org.json.JSONObject

/**
 * 🟢🔒 V616 (২৪.০৮.২০২৬, TK-নির্দেশ) — "জলপাইগুড়ি থেকে ভুল করে রেজিস্টার
 * হওয়া রোগীর সব তথ্য কিষাণগঞ্জে ট্রান্সফার করা যাক, আর ভবিষ্যতেও Master
 * যেন এটা করতে পারেন।"
 *
 * **প্রেক্ষাপট (সৎভাবে লেখা):** Field Officer/Master রেজিস্ট্রেশনের সময়
 * নিজের ব্রাঞ্চ বাছতে বাধ্য (৩-চাপ লক শুধু Staff/Doctor-এর জন্য, ইচ্ছাকৃত —
 * তাঁরা একাধিক ব্রাঞ্চে কাজ করেন)। ভুল বেছে ফেললে এতদিন সংশোধনের কোনো
 * পথ ছিল না — এই ফাইলটাই সেই প্রথম পথ।
 *
 * **কী করে, কী করে না (স্পষ্ট, ঝুঁকি না লুকিয়ে):**
 *  ✅ `patients`, `followups`, `payments` — এই তিনটে টেবিলে ওই মোবাইলের
 *     **সব সারির `branch`** নতুন ব্রাঞ্চে বদলে দেয়।
 *  ⛔ **`patientId`/`refId` অক্ষুণ্ণ রাখা হয়** (নতুন করে বানানো হয় না) —
 *     ইচ্ছাকৃত সিদ্ধান্ত। আইডি নতুন বানালে আগে ছাপা কাগজ/প্রেসক্রিপশন/
 *     রসিদের সাথে অমিল হয়ে যেত, যেটা আরও বড় বিভ্রান্তি তৈরি করত। তাই
 *     ট্রান্সফারের পরেও পুরনো ID-র শুরুর অক্ষর (যেমন "JPE-") নতুন
 *     ব্রাঞ্চের সাথে না মিলতে পারে — এটা শুধু চোখে দেখতে অসামঞ্জস্যপূর্ণ,
 *     কোনো হিসাব ভুল করে না।
 *  ✅ প্রতিটা সারি **একটা একটা করে**, প্রমাণিত `updateById()` দিয়ে (৬২টা
 *     জায়গায় আগে থেকে ব্যবহৃত, নির্ভরযোগ্য) — নতুন কোনো bulk-write পথ
 *     বানানো হয়নি, তাই আচমকা ভুল হওয়ার নতুন কোনো সুযোগ তৈরি হয়নি।
 *  ✅ Master ছাড়া কেউ ডাকতে পারবেন না (কল করার আগে UI-তেই role-চেক)।
 */
object BranchTransferRepository {

    data class TransferPreview(
        val patientRows: List<JSONObject>,
        val followupRows: List<JSONObject>,
        val paymentRows: List<JSONObject>
    ) {
        val totalCount: Int get() = patientRows.size + followupRows.size + paymentRows.size
        val currentBranches: Set<String> get() =
            (patientRows + followupRows + paymentRows).map { it.optString("branch", "") }.filter { it.isNotBlank() }.toSet()
    }

    /** ট্রান্সফারের আগে — কতগুলো সারি, কোন ব্রাঞ্চে আছে, তা দেখানোর জন্য। */
    fun preview(mobile: String): TransferPreview? {
        val digits = mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return null
        return try {
            val patients = SupabaseClient.findByMobile("patients", digits, "id,branch,patientId,name", 10)
            val followups = SupabaseClient.findByMobile("followups", digits, "id,branch,refId,name", 10)
            val payments = SupabaseClient.findByMobile("payments", digits, "id,branch,amount,payType", 200)
            TransferPreview(
                (0 until patients.length()).map { patients.getJSONObject(it) },
                (0 until followups.length()).map { followups.getJSONObject(it) },
                (0 until payments.length()).map { payments.getJSONObject(it) }
            )
        } catch (_: Throwable) { null }
    }

    data class TransferResult(val moved: Int, val failed: Int)

    /** আসল কাজ — প্রতিটা সারির `branch` ঘর নতুন ব্রাঞ্চে বদলে দেয়। */
    fun transfer(preview: TransferPreview, newBranch: String): TransferResult {
        var moved = 0
        var failed = 0
        fun moveRows(table: String, rows: List<JSONObject>) {
            for (row in rows) {
                val id = row.optString("id")
                if (id.isBlank()) { failed++; continue }
                val ok = try {
                    SupabaseClient.updateById(table, id, JSONObject().put("branch", newBranch))
                } catch (_: Throwable) { false }
                if (ok) moved++ else failed++
            }
        }
        moveRows("patients", preview.patientRows)
        moveRows("followups", preview.followupRows)
        moveRows("payments", preview.paymentRows)
        return TransferResult(moved, failed)
    }
}
