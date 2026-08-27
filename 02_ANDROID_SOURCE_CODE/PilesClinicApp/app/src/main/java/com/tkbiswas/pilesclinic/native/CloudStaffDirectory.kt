package com.tkbiswas.pilesclinic.native

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * 👥🔒 V746 (২৭.০৮.২০২৬, TK-অনুমোদিত) — **অ্যাপ থেকে যোগ করা স্টাফ ও ডাক্তার**।
 *
 * TK-এর কথা: *"আপনি তো আর আমার সাথে সারা জীবন থাকবেন না... আমি অ্যাপ্লিকেশন
 * থেকে কোন স্টাফ যোগ বা বিয়োগ করতে পারব কিনা সেটা নিরাপদে বলুন।"*
 *
 * ═════════════════════════════════════════════════════════════════════
 * ⚠️⚠️ **সবচেয়ে জরুরি কথা — লগইনের পুরনো পথে এক অক্ষরও হাত দেওয়া হয়নি।**
 *
 *   `StaffDirectory`-র বাঁধা তালিকাটা (২৩ জন) **আগের মতোই প্রথমে** দেখা হয়।
 *   এই ফাইলটা তখনই ডাকা হয় যখন ওই তালিকায় নম্বরটা **পাওয়াই যায় না** —
 *   অর্থাৎ আজ যেখানে লগইন এমনিতেই "Mobile number not found" বলে থেমে যেত।
 *
 *   ⇒ **আজকের ২৩ জনের লগইনে শূন্য বদল, শূন্য বাড়তি নেট-কল, শূন্য ঝুঁকি।**
 *   ⇒ মেঘ কাজ না করলে সবচেয়ে খারাপ যা হতে পারে: **নতুন** কেউ ঢুকতে পারবেন
 *     না (আজও পারতেন না)। পুরনো কারও কিছু হবে না।
 * ═════════════════════════════════════════════════════════════════════
 *
 * **বাদ দেওয়া কীভাবে কাজ করে**
 *   এই ফাইল শুধু **যোগ** করে, কখনো বাদ দেয় না — দরকারই নেই।
 *   বাদ দেওয়ার প্রমাণিত পাহারা আগে থেকেই আছে (V403 + `suspended_until_for`):
 *   `hr.staff_profiles.active = false` হলে লগইনের সময়েই সার্ভার আটকে দেয়
 *   ("This account has been removed")। সেটা মেঘ থেকে আসা লোকের ক্ষেত্রেও
 *   হুবহু একইভাবে খাটে।
 *
 * ⛔ গোপন চাবি লাগে না — সার্ভারের `public.staff_login_list()` ফাংশনটা
 *    anon-চাবিতেই ডাকা যায়, আর সেটা **শুধু লগইনে যেটুকু দরকার** তাই দেয়
 *    (নাম · মোবাইল · কোড · ব্রাঞ্চ · ভূমিকা)। ঠিকানা · আধার · বেতন — কিছুই নয়।
 * ⛔ ফোনে জমা থাকে, তাই নেট ছাড়াও আগের বার আনা লোকজন ঢুকতে পারেন।
 * ⚡ Egress: শুধু "নম্বর পাওয়া গেল না" হলে একবার, আর ২৪ ঘণ্টায় একবার নবায়ন।
 */
object CloudStaffDirectory {

    private const val PREFS = "cloud_staff_dir"
    private const val KEY_JSON = "rows"
    private const val KEY_AT = "at"
    private const val FRESH_MS = 24L * 60L * 60L * 1000L   // এক দিন

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** ⛔ `CloudPasswordCheck`-এর হুবহু একই প্রমাণিত ধাঁচ (লগইনের আগেই
     *  anon-চাবিতে RPC ডাকা) — নতুন কিছু আবিষ্কার করা হয়নি। */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** সার্ভারের `public.staff_login_list()` — শুধু লগইনে যেটুকু দরকার।
     *  ⛔ ব্যর্থ হলে null; কখনো ব্যতিক্রম ছোড়ে না। */
    private fun fetchList(): JSONArray? {
        // ⛔ ভিতরে কোনো `return` নেই — `return try { ... return ... }` লেখা
        //    Kotlin-এ চলে না (কম্পাইল-পাহারায় দুবার ধরা পড়েছে)।
        var out: JSONArray? = null
        try {
            val req = Request.Builder()
                .url(SupabaseClient.URL + "/rest/v1/rpc/staff_login_list")
                .addHeader("apikey", SupabaseClient.KEY)
                .addHeader("Authorization", "Bearer " + SupabaseClient.KEY)
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    out = JSONArray(resp.body?.string() ?: "[]")
                }
            }
        } catch (_: Throwable) { out = null }
        return out
    }

    /** ফোনে জমানো তালিকা → অ্যাকাউন্ট। ⛔ কখনো ব্যতিক্রম ছুড়ে না। */
    private fun cached(ctx: Context): List<StaffAccount> {
        return try {
            val txt = prefs(ctx).getString(KEY_JSON, "") ?: ""
            if (txt.isBlank()) emptyList() else parse(JSONArray(txt))
        } catch (_: Throwable) { emptyList() }
    }

    private fun parse(arr: JSONArray): List<StaffAccount> {
        val out = ArrayList<StaffAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mob = StaffDirectory.normalizeMobile(o.optString("mobile", ""))
            if (mob.length != 10) continue
            val role = o.optString("role_kind", "").trim().lowercase()
            // ⛔ master এখানে কখনো আসে না (সার্ভারের ফাংশনই দেয় না) — তবু
            //    দ্বিতীয় পাহারা, যাতে ভুল করেও কেউ মাস্টার হয়ে না যায়।
            if (role != "staff" && role != "doctor" && role != "field") continue
            // 🔴🔒 V748 — **এখানে `full_name` বসানো যাবে না, `person_code` বসাতে হবে।**
            //    কারণ `ModuleAuth.expectedCode()` মডিউলের পরিচয় বার করে
            //    `user.name.uppercase()` থেকে, আর সার্ভারের auth-ইমেল তৈরি হয়
            //    **কোড** থেকে (`kne-kishan9@staff.piles`)। নাম বসালে ইমেল হত
            //    `raju-das@staff.piles` — যা নেই ⇒ প্রতিটা নতুন লোকের মডিউলে
            //    "Sign-in failed" হত (ROHINI-র সঙ্গে ঠিক এটাই হয়েছিল)।
            //    ⛔ বাঁধা তালিকার ২৩ জনেও `name` ঘরে কোডই আছে (KNE-LAXMI …),
            //       তাই এটা নতুন কিছু নয় — ওদের সঙ্গেই হুবহু মিল।
            val code = o.optString("person_code", "").trim().uppercase()
            if (code.isBlank()) continue
            out.add(StaffAccount(mob, code, o.optString("branch", "").trim(), role))
        }
        return out
    }

    /**
     * 🧵 **আলাদা থ্রেড থেকে ডাকুন** — মেঘ থেকে তালিকা এনে ফোনে জমা করে।
     * ⛔ ব্যর্থ হলে পুরনো জমানো তালিকাই থেকে যায়, কিছু মোছে না।
     */
    fun refresh(ctx: Context): Boolean {
        // ⛔ `return try { ... return ... }` লেখা যায় না (কম্পাইল-পাহারায় ধরা
        //    পড়েছিল) — তাই ভিতরের `return` বাদ দিয়ে সরল শর্তে লেখা হলো।
        val arr = fetchList()
        // ⛔ ফাঁকা/ব্যর্থ উত্তরে জমানো তালিকা **মোছা হয় না** — নেটের গোলমালে
        //    কেউ যেন হঠাৎ ঢুকতে না পারা হয়ে না যায়।
        if (arr == null || arr.length() == 0) return false
        return try {
            prefs(ctx).edit()
                .putString(KEY_JSON, arr.toString())
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply()
            true
        } catch (_: Throwable) { false }
    }

    /** জমানো তালিকা পুরনো হয়ে গেছে কি (এক দিনের বেশি)। */
    private fun stale(ctx: Context): Boolean =
        System.currentTimeMillis() - prefs(ctx).getLong(KEY_AT, 0L) > FRESH_MS

    /** 🧵 আলাদা থ্রেড — দিনে একবার নিজে থেকে নবায়ন। ব্যর্থ হলে কিছু হয় না। */
    fun refreshIfStale(ctx: Context) {
        if (stale(ctx)) refresh(ctx)
    }

    /**
     * বাঁধা তালিকায় না পেলে এখানে খোঁজা হয়।
     * ⛔ প্রথমে ফোনে জমানোটা; না পেলে **একবার** মেঘে গিয়ে আবার দেখা।
     * ⛔ 🧵 নেটে যেতে পারে — **আলাদা থ্রেড থেকে ডাকুন।**
     */
    fun findAccount(ctx: Context, mobile: String): StaffAccount? {
        val target = StaffDirectory.normalizeMobile(mobile)
        if (target.length != 10) return null
        cached(ctx).find { StaffDirectory.normalizeMobile(it.mobile) == target }?.let { return it }
        if (!refresh(ctx)) return null
        return cached(ctx).find { StaffDirectory.normalizeMobile(it.mobile) == target }
    }

    // 🧹 V748 — `cachedNameFor()` ও `cachedAccounts()` মুছে দেওয়া হলো।
    //    কোথাও ব্যবহার হচ্ছিল না, আর `cachedNameFor` এখন **নাম নয়, কোড**
    //    ফেরাত (উপরের V748 বদলের পরে) — নামটাই বিভ্রান্তিকর হয়ে যেত।
}
