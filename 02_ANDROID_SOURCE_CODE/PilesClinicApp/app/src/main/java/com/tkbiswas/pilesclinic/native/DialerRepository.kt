package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONObject

/**
 * 🆕 B464 (05.08.2026, TK-নির্দেশ) — "Dialer": অন্য ব্রাঞ্চের ফরওয়ার্ড-করা
 * এনকোয়ারি নম্বরে ফিরতি কল করলে সেটা অ্যাপে "গোনা" যায় না, কারণ স্টাফ
 * WhatsApp থেকে নম্বর কপি করে ফোনের নিজের ডায়ালারে কল করতেন — অ্যাপ কিছুই
 * জানত না। এই নতুন `DialerActivity`-এর "Call" বোতাম থেকে কল করলে:
 *   ১) `CallChooser.open()` (আগে থেকেই প্রজেক্ট-জোড়া ব্যবহৃত) — ডায়াল করে
 *      **এবং** `call_taps`-এ লগ করে, যেটা Work Notebook-এর Daily Report-এর
 *      "App Calls (auto)"/"Total call (auto)"-তে এমনিতেই যোগ হয়ে যায় —
 *      এখানে নতুন কিছু করতে হয়নি।
 *   ২) এই ফাইলের `logDialedCall()` — নম্বরটা `followups` টেবিলে (যেকোনো
 *      ব্রাঞ্চের Enquiry/Visit/Patient) মিললে সেই রেকর্ডেই কল-গোনা বাড়ে
 *      (প্রমাণিত `FollowUpRepository.updateRemark(..., incrementCall=true)`
 *      পুনর্ব্যবহার — এই ফাংশনের ভিতরে এক অক্ষরও ছোঁয়া হয়নি)। না মিললে
 *      নতুন, আলাদা `dialer_calls` টেবিলে জমা থাকে (TK-এর নির্দেশ: "দুটোই —
 *      Enquiry থাকলে তাতে যোগ হবে, না থাকলে আলাদা লগে")।
 * ⛔ কোনো পুরনো টেবিল/কলাম/ফাংশনের ভিতরে হাত দেওয়া হয়নি — সবটাই নতুন,
 *    স্বাধীন সংযোজন। 🔴 `dialer_calls` নতুন টেবিল — TK-কে SQL চালাতে হবে।
 */
object DialerRepository {

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

    /** ডায়াল করার সাথে সাথে (ব্যাকগ্রাউন্ডে) ডাকা হয় — কল করাটা কখনো এর
     *  জন্য অপেক্ষা করে না, ব্যর্থ হলেও নিঃশব্দে বাদ (কল করাই আসল কাজ)। */
    fun logDialedCall(context: Context, dialedNumber: String, staffMobile: String, staffName: String, branch: String) {
        Thread {
            try {
                val digits = dialedNumber.filter { it.isDigit() }.takeLast(10)
                if (digits.length != 10) return@Thread

                val rows = try { SupabaseClient.findByMobile("followups", digits, "id,stage,branch", 10) } catch (_: Throwable) { org.json.JSONArray() }
                var matchedId = ""
                if (rows.length() > 0) {
                    // TK-এর আসল দৃশ্যকল্প — ফরওয়ার্ড হওয়া এনকোয়ারিতে ফিরতি
                    // কল, তাই "Enquiry" ধাপকে অগ্রাধিকার; না পেলে প্রথম যেটা
                    // পাওয়া যায় (Visit/Patient) সেটাই।
                    var pick: JSONObject? = null
                    for (i in 0 until rows.length()) {
                        val r = rows.getJSONObject(i)
                        if (r.optString("stage") == "Enquiry") { pick = r; break }
                    }
                    val finalPick = pick ?: rows.getJSONObject(0)
                    matchedId = finalPick.optString("id")
                }

                if (matchedId.isNotBlank()) {
                    // 🔒 B602 (TK-নির্দেশ 09.08.2026): "Called via Dialer"-এর বদলে
                    // ব্রাঞ্চ-কোড — "Called via JPE/KNE/COB/FLK/BIR" (যে স্টাফ যে
                    // ব্রাঞ্চ থেকে কল করেছে)। বিদ্যমান PatientIdGenerator.branchCode() রি-ইউজ।
                    val viaCode = PatientIdGenerator.branchCode(branch)
                    FollowUpRepository(context).updateRemark(
                        matchedId, "Called via $viaCode", staffName.ifBlank { staffMobile }, incrementCall = true
                    )
                } else {
                    val row = JSONObject()
                        .put("id", "dc_" + System.currentTimeMillis() + "_" + (0..999).random())
                        .put("staffMobile", staffMobile.filter { it.isDigit() }.takeLast(10))
                        .put("staffName", staffName)
                        .put("branch", branch)
                        .put("dialedNumber", digits)
                        .put("calledAt", isoNow())
                    SupabaseClient.upsert("dialer_calls", row)
                }
            } catch (_: Throwable) { /* কল করাটা কখনো এর জন্য থামবে না */ }
        }.start()
    }

    /** আজ এই স্টাফের করা Dialer কল যেগুলো কোনো Enquiry/রোগীর রেকর্ডে মেলেনি
     *  (তাই আলাদা লগে গেছে) — স্ক্রিনে ছোট তালিকা দেখানোর জন্য। */
    fun fetchTodayUnmatched(staffMobile: String): List<JSONObject> {
        return try {
            val digits = staffMobile.filter { it.isDigit() }.takeLast(10)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val rows = SupabaseClient.fetchList(
                "dialer_calls", "staffMobile=eq.$digits&calledAt=gte.${today}T00:00:00", 50, order = "calledAt.desc"
            )
            (0 until rows.length()).map { rows.getJSONObject(it) }
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * 🆕🔒 খাতার সারি — Dialer পুনর্গঠন (TK-নির্দেশ, 05.08.2026 — "Android
     * ফোনের নিজস্ব Dialer-এর মতন হবে, Call Log + Contacts")।
     *
     * 🚨 Supabase ফ্রি-প্ল্যান ঝুঁকি (TK নিজে জিজ্ঞাসা করেছেন, "ভালো করে
     * যাচাই করে বলবেন"): আজকের Call Log-এ অনেকগুলো নম্বর থাকতে পারে — একটা
     * একটা করে (`findByMobile`) ক্লাউডে গেলে দিনে বহুবার অনুরোধ যেত। তাই
     * এখানে **সবগুলো নম্বর একসাথে একটাই অনুরোধে** (`or=(mobile.like.*N1,
     * mobile.like.*N2,...)` — প্রজেক্টের আগে থেকে থাকা প্রমাণিত প্যাটার্ন,
     * `DraftRepository.kt`/`ReportsActivity.kt`-এ একই সিনট্যাক্স ব্যবহার
     * হয়) মেলানো হয়, আর `CloudReadCache`-এ ২০ সেকেন্ড মনে থাকে — তাই একই
     * পর্দায় All ও Missed দুই ট্যাব একসাথে খুললেও দ্বিতীয়বার আর ক্লাউডে
     * যেতে হয় না।
     */
    data class MatchedContact(
        val id: String, val name: String, val mobile: String,
        val branch: String, val disease: String, val stage: String, val patientId: String
    )

    /** সর্বোচ্চ এই কয়টা নম্বর একবারে মেলানো হয় (URL খুব লম্বা হওয়া এড়াতে,
     *  বাস্তবে একদিনে এত নম্বর সাধারণত হয় না)। */
    private const val MAX_BATCH = 60

    fun matchNumbersBatch(numbers: List<String>): Map<String, MatchedContact> {
        val digits = numbers.map { it.filter { d -> d.isDigit() }.takeLast(10) }
            .filter { it.length == 10 }.distinct().take(MAX_BATCH)
        if (digits.isEmpty()) return emptyMap()
        return try {
            val filter = "or=(" + digits.joinToString(",") { "mobile.like.*$it" } + ")"
            val cacheKey = "dialer:match:" + digits.sorted().joinToString(",")
            val rows = CloudReadCache.get(cacheKey) {
                SupabaseClient.fetchListSlimOrNull(
                    "followups", filter, digits.size * 3, "id,name,mobile,branch,disease,stage,patientId"
                )
            } ?: return emptyMap()
            // একই নম্বরে একাধিক সারি মিললে (Enquiry+Visit+Patient) — সবচেয়ে
            // এগিয়ে থাকা ধাপটাই দেখানো হয় (Treatment > Patient > Inquiry)।
            fun rank(stage: String): Int = when (stage) {
                "Treatment" -> 3; "Patient" -> 2; "Inquiry" -> 1; else -> 0
            }
            val byNumber = HashMap<String, MatchedContact>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val mobile = r.optString("mobile")
                val key = mobile.filter { it.isDigit() }.takeLast(10)
                if (key.length != 10) continue
                val candidate = MatchedContact(
                    id = r.optString("id"), name = r.optString("name"), mobile = mobile,
                    branch = r.optString("branch"), disease = r.optString("disease"),
                    stage = r.optString("stage"), patientId = r.optString("patientId")
                )
                val existing = byNumber[key]
                if (existing == null || rank(candidate.stage) > rank(existing.stage)) byNumber[key] = candidate
            }
            byNumber
        } catch (_: Throwable) { emptyMap() }
    }

    /**
     * Contacts ট্যাব — Enquiry + Visit + Patient (TK-নির্দেশ)। প্রজেক্টের
     * আগে থেকে থাকা `FollowUpRepository.fetchTab()` তিনবার (Inquiry/
     * Patient/Treatment — এই ফাইলগুলোর ভিতরের আসল নাম, UI-তে যথাক্রমে
     * Enquiry/Visit/Patient) — নতুন কোনো টেবিল/কলাম লাগেনি। ⛔ ব্রাঞ্চ-
     * ছাঁকনি প্রজেক্টের সব জায়গার একই নিয়ম (Master সব, বাকিরা নিজের
     * ব্রাঞ্চ) — এখানেও তাই।
     */
    fun fetchContacts(context: Context, branchFilter: String?): List<FollowUpItem> {
        val repo = FollowUpRepository(context)
        val enquiry = try { repo.fetchTab("Inquiry", branchFilter) } catch (_: Throwable) { emptyList() }
        val visit = try { repo.fetchTab("Patient", branchFilter) } catch (_: Throwable) { emptyList() }
        val patient = try { repo.fetchTab("Treatment", branchFilter) } catch (_: Throwable) { emptyList() }
        return mergeContactStages(enquiry, visit, patient)
    }

    // 🔴🔒 B502 (06.08.2026, TK-নির্দেশ — "সাথে সাথে দেখাতে হবে, সব
    // ব্রাঞ্চে সমানভাবে") — Contacts ট্যাব আগে সরাসরি ক্লাউডের উত্তরের
    // অপেক্ষা করত। এখন `FollowUpActivity.kt`-এর মতোই `loadCachedTab()`
    // (ফোনের জমানো তথ্য, তাৎক্ষণিক) দিয়ে তিনটে ধাপই আনা হয়, তারপর ঠিক
    // উপরের `fetchContacts()`-এর সেই একই মিলানোর নিয়মে (এখন
    // `mergeContactStages()`-এ আলাদা করা হয়েছে, নকল কোড এড়াতে) একত্র
    // করা হয়। ⛔ মিলানোর/সাজানোর নিয়ম একটুও বদলায়নি — শুধু পুনর্ব্যবহার।
    fun fetchCachedContacts(context: Context, branchFilter: String?): List<FollowUpItem> {
        val repo = FollowUpRepository(context)
        val enquiry = try { repo.loadCachedTab("Inquiry", branchFilter) } catch (_: Throwable) { null } ?: emptyList()
        val visit = try { repo.loadCachedTab("Patient", branchFilter) } catch (_: Throwable) { null } ?: emptyList()
        val patient = try { repo.loadCachedTab("Treatment", branchFilter) } catch (_: Throwable) { null } ?: emptyList()
        return mergeContactStages(enquiry, visit, patient)
    }

    private fun mergeContactStages(enquiry: List<FollowUpItem>, visit: List<FollowUpItem>, patient: List<FollowUpItem>): List<FollowUpItem> {
        val byMobile = LinkedHashMap<String, FollowUpItem>()
        // ⛔ ক্রম গুরুত্বপূর্ণ: পরে বসানো এন্ট্রিই জেতে, তাই সবচেয়ে এগিয়ে
        // থাকা ধাপ (Treatment) সবার শেষে বসানো হলো।
        for (item in enquiry) {
            val key = item.mobile.filter { it.isDigit() }.takeLast(10)
            if (key.length == 10) byMobile[key] = item
        }
        for (item in visit) {
            val key = item.mobile.filter { it.isDigit() }.takeLast(10)
            if (key.length == 10) byMobile[key] = item
        }
        for (item in patient) {
            val key = item.mobile.filter { it.isDigit() }.takeLast(10)
            if (key.length == 10) byMobile[key] = item
        }
        return byMobile.values.sortedBy { it.name.uppercase() }
    }
}
