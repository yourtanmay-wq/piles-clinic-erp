/* =====================================================================
   V245 — ModuleAuth : shared, separate authenticated gateway for the three
   new modules (Profile & Salary · Work Notebook · Income & Expense).

   It gives per-person privacy WITHOUT touching the existing app login and
   WITHOUT any new Gradle dependency — it uses the SAME OkHttp the app ships
   with, calling Supabase Auth (token) + PostgREST (schema hr/wn/fin via the
   Accept-Profile / Content-Profile header). The JWT makes Supabase enforce
   the RLS from V245_MODULES_HR_WN_FIN_2026-08-02.sql: Master sees all, each
   staff sees only their own — exactly like the website.

   No personal email: the login email is synthetic, built from the Staff Code
   (e.g. kne-laxmi@staff.piles). Master sets the passwords.
   All comments/UI are English (Guard rule 9.14). Data access here never uses
   fetchListSlim, so Guard rule 9.7 is unaffected.
   ===================================================================== */
package com.tkbiswas.pilesclinic.modules

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.tkbiswas.pilesclinic.data.remote.SupabaseConfig
import com.tkbiswas.pilesclinic.native.NativeSession
import com.tkbiswas.pilesclinic.native.StaffDirectory

object ModuleAuth {

    private const val EMAIL_DOMAIN = "staff.piles"
    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    @Volatile var accessToken: String? = null; private set
    @Volatile var personCode: String? = null; private set
    @Volatile var isMaster: Boolean = false; private set
    // 🔵 সর্বশেষ সফল সাইন-ইনের applicationContext — টোকেন মেয়াদ শেষ হলে গোপনে আবার
    // লগইন করতে লাগে (নিচের reAuth দেখুন)। শুধু ব্যর্থ-পথে ব্যবহার হয়।
    @Volatile private var appCtx: Context? = null

    // 🔴🔒 V453 (20.08.2026, TK-অনুমোদিত · Free Plan Egress/Session fix —
    // আগের V440 লগে "বাকি — TK-এর সিদ্ধান্ত লাগবে" হিসেবে চিহ্নিত ছিল)।
    //
    // **আসল সমস্যা:** `accessToken` শুধু RAM-এ (@Volatile var) থাকত। Android
    // ব্যাকগ্রাউন্ড অ্যাপ প্রক্রিয়া মেরে দিলে (যা খুবই সাধারণ ঘটনা), এই
    // object আবার শূন্য থেকে শুরু হয় — WorkManager-এর প্রতিটা দফা
    // (BackgroundRefreshWorker · AttendanceReminderWorker · SalaryReminder
    // ইত্যাদি) `signInCurrentSession()` ডাকলেই একটা **সম্পূর্ণ নতুন
    // email+password লগইন** (Supabase Auth-এ নতুন session) হতো — টোকেন
    // তখনও হয়তো মেয়াদ-উত্তীর্ণ হয়ইনি। ১৫ জন স্টাফের জন্য এভাবেই বহু হাজার
    // অপ্রয়োজনীয় session জমেছিল।
    //
    // **সমাধান:** টোকেন + মেয়াদ (expiresAt) এখন SharedPreferences-এ জমা
    // থাকে। প্রক্রিয়া নতুন করে শুরু হলেও, মেয়াদ ফুরোয়নি এমন টোকেন থাকলে
    // — কোনো নতুন নেটওয়ার্ক-কল ছাড়াই সেটা আবার ব্যবহার হয়। মেয়াদ ফুরোলে
    // তবেই আগের মতো লগইন হয়। ⛔ লগইনের নিয়ম/পাসওয়ার্ড/RLS কিছুই বদলায়নি —
    // শুধু অপ্রয়োজনীয় বারবার-লগইন বন্ধ হলো।
    private const val PREF = "module_auth_state"
    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun loadPersisted(context: Context, expectedCode: String) {
        try {
            val p = prefs(context)
            val savedCode = p.getString("code", null) ?: return
            if (savedCode != expectedCode) return
            val expiresAt = p.getLong("expiresAt", 0L)
            if (expiresAt <= System.currentTimeMillis()) return
            val tok = p.getString("token", null) ?: return
            accessToken = tok
            personCode = p.getString("personCode", savedCode)
            isMaster = p.getBoolean("isMaster", false)
        } catch (_: Throwable) { }
    }

    private fun savePersisted(context: Context, code: String, expiresInSeconds: Int) {
        try {
            val safeSeconds = if (expiresInSeconds > 0) expiresInSeconds else 3600
            // ৬০ সেকেন্ড আগেই "মেয়াদ শেষ" ধরা হয় — ঘড়ির সামান্য গরমিল ঢাকতে,
            // যাতে কখনো মেয়াদ-উত্তীর্ণ টোকেন দিয়ে read না যায়।
            val expiresAt = System.currentTimeMillis() + (safeSeconds - 60L).coerceAtLeast(60L) * 1000L
            prefs(context).edit()
                .putString("code", code)
                .putString("token", accessToken)
                .putString("personCode", personCode)
                .putBoolean("isMaster", isMaster)
                .putLong("expiresAt", expiresAt)
                .apply()
        } catch (_: Throwable) { }
    }

    private fun clearPersisted(context: Context?) {
        try { context?.let { prefs(it).edit().clear().apply() } } catch (_: Throwable) { }
    }

    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank()

    /** 🔴🔒 V429 (TK-নির্দেশ ১৭.০৮.২০২৬ — *"আমি সাধারণ ব্যবহারকারী, আমার সামনে
     *  যেন কোনো সমস্যা না আসে; এই সিদ্ধান্ত আপনাকে নিতে হবে"*)।
     *
     *  **যে সমস্যাটা হচ্ছিল:** কোনো পর্দা ক্লাউডে ডাক পাঠালে টোকেন লাগে। টোকেন
     *  না থাকলে নিচের `reAuth()` গোপনে আবার লগইন করে নেয় — কিন্তু তার জন্য
     *  `appCtx` দরকার, আর সেটা বসত **প্রথম সফল লগইনের পরে**। ফলে যে পর্দা
     *  সবার আগে ডাক পাঠাত, তার ডাকটা চুপচাপ ব্যর্থ হত, কোনো এররও দেখাত না —
     *  ব্যবহারকারীর মনে হতো জিনিসটা "নেই" (চেম্বারের RMP কমিশন ঠিক তাই হয়েছিল)।
     *
     *  **সমাধান:** অ্যাপ চালু হওয়ার সময়েই একবার context ধরে রাখা হয়। তাতে
     *  **যে কোনো পর্দার** প্রথম ডাকেও গোপন লগইনটা কাজ করে। এক জায়গায় ঠিক করায়
     *  ভবিষ্যতে নতুন পর্দা যোগ হলেও এই ভুল আর ফিরবে না।
     *  ⛔ লগইনের নিয়ম/পাসওয়ার্ড কিছুই বদলায়নি — শুধু context আগে থেকে রাখা। */
    fun attachContext(context: Context) {
        try { if (appCtx == null) appCtx = context.applicationContext } catch (_: Throwable) { }
    }

    // 🔵🔒 (09.08.2026, TK-রিপোর্ট — নেট ফাস্ট তবু "weak internet"): Supabase লগইন-টোকেন
    // ~১ ঘণ্টা পর মেয়াদ শেষ; isSignedIn শুধু টোকেন **আছে** কিনা দেখে, মেয়াদ নয়। তাই
    // মেয়াদ-শেষ টোকেন দিয়ে read গেলে 401 আসত, UI ভুল করে "দুর্বল নেট" বলত। এই ফাংশন
    // একবার গোপনে (কোনো পপ-আপ নেই) আবার লগইন করে নতুন টোকেন আনে। সফল হলে true।
    private fun reAuth(): Boolean {
        val c = appCtx ?: return false
        return try {
            // 🔴🔒 V464 (20.08.2026, TK-রিপোর্ট ছবিসহ — "JWT expired" আটকে
            // যাচ্ছিল, RMP Default Commission স্ক্রিনে)।
            //
            // **আসল কারণ (নিজের আজকের ভুল, খুঁজে বার করা হয়েছে):** `reAuth()`
            // ঠিক তখনই ডাকা হয় যখন বর্তমান টোকেন ইতিমধ্যে 401 দিয়েছে। কিন্তু
            // V453-এ যোগ করা `signInCurrentSession()`-এর "ইতিমধ্যে isSignedIn
            // থাকলে আবার লগইন না করা" শর্টকাট এখানে ভুল প্রয়োগ হচ্ছিল —
            // `isSignedIn` তখনও `true` থাকত (পুরনো, ব্যর্থ-হওয়া টোকেনটাই
            // মেমোরিতে বসা), তাই `reAuth()` **আসলে নতুন কোনো লগইন না করেই**
            // "সফল" বলে ফিরে যেত। ফলে retry-ও সেই একই খারাপ টোকেন দিয়ে আবার
            // ব্যর্থ হত — এটাই ব্যবহারকারীর দেখা "JWT expired" আটকে থাকার
            // আসল কারণ।
            //
            // **সমাধান:** এখানে আগে `signOut()` (মেমোরি + SharedPreferences
            // দুটোই সাফ) করে, তারপর `signInCurrentSession()` ডাকা হয় — তাই
            // এখন নিশ্চিতভাবে **সত্যিকারের নতুন লগইন** হয়, পুরনো ব্যর্থ
            // টোকেন কখনো পুনর্ব্যবহার হয় না। ⛔ V453-এর মূল সাশ্রয় (প্রক্রিয়া
            // নতুন শুরু হলে valid persisted token থাকলে পুনর্ব্যবহার) অক্ষত —
            // এই ফিক্স শুধু "টোকেন ইতিমধ্যে ব্যর্থ হয়েছে" এই একটা নির্দিষ্ট
            // পথেই প্রযোজ্য।
            signOut(c)
            signInCurrentSession(c) == null
        } catch (_: Exception) { false }
    }

    fun codeToEmail(code: String): String =
        code.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-") + "@" + EMAIL_DOMAIN

    /**
     * V247: The owner has already authenticated at the main app login, so the
     * private modules must not ask for a second visible password.  Derive the
     * module identity from that existing session and authenticate silently.
     */
    // 🔴 B317 (03.08.2026, TK-নির্দেশ — এক ব্যবহারকারীর পর অন্য একজন লগইন করলে
    // পুরনো Module পরিচয় যেন কখনো থেকে না যায়): কোড→পাসওয়ার্ড ঠিক করার
    // অংশটা এখানে অক্ষত রইল, শুধু কোড বার করার অংশ `expectedCode()`-এ আলাদা
    // করা হলো — যাতে `ModuleUi.ensureSignedIn()` কল না করেই আগে থেকে জেনে
    // নিতে পারে "এখন আসলে কার Module-এ সাইন-ইন থাকা উচিত", আর সেটার সাথে
    // মিলিয়ে দেখতে পারে বর্তমান cached সেশন সত্যিই ওই একই ব্যক্তির কিনা।
    fun expectedCode(context: Context): String? {
        val user = NativeSession.current(context) ?: return null
        val mobile = user.mobile.filter { it.isDigit() }.takeLast(10)
        return when (mobile) {
            "8001080080" -> "MASTER-TK"
            "7980993652" -> "DR-KH-MANDAL"
            "8001800148" -> "DR-JAY-BANIK"
            "9046366596" -> "DR-AMIT-GOLDAR"
            "6297625447" -> "DR-PK-ROY"
            "9002003540" -> "FIELD-OFFICER"
            // 🔵 V308 (১০.০৮): ৪ অংশীদার-ডাক্তারের মোবাইল→DR-কোড (V308 SQL-এর dr-…@staff.piles-এর মিল)।
            "7479173399" -> "DR-JH-MANDAL"
            "9002610352" -> "DR-GOKUL"
            "7810907954" -> "DR-SAIKAT-ROY"
            "9242009205" -> "DR-PRANAB-BISWAS"
            else -> user.name.trim().uppercase()
        }
    }

    fun signInCurrentSession(context: Context): String? {
        appCtx = context.applicationContext
        val user = NativeSession.current(context) ?: return "Main app login required"
        val mobile = user.mobile.filter { it.isDigit() }.takeLast(10)
        val code = expectedCode(context) ?: return "Main app login required"

        // 🔴🔒 V453: প্রক্রিয়া নতুন করে শুরু হলে RAM-এ কিছু থাকে না, কিন্তু আগের
        // মেয়াদ-অক্ষত টোকেন SharedPreferences-এ থাকতে পারে — থাকলে সেটাই
        // ব্যবহার হয়, নতুন কোনো লগইন-কল লাগে না।
        if (!isSignedIn) loadPersisted(context, code)
        if (isSignedIn && personCode == code) return null

        // Reuse only the project's long-standing role passwords. No new
        // password is created and no second password is shown anywhere.
        val originalRole = StaffDirectory.findAccount(mobile)?.role ?: user.displayRole
        val password = when (originalRole) {
            "master" -> "admin123"
            "doctor" -> "doctor123"
            "field" -> "field123"
            else -> "staff123"
        }
        val err = signIn(code, password)
        if (err == null) savePersisted(context, code, lastExpiresIn)
        return err
    }

    private fun baseUrl(): String = SupabaseConfig.url.trimEnd('/')
    private fun anonKey(): String = SupabaseConfig.anonKey

    // 🔴🔒 V453: শেষ সফল সাইন-ইনের expires_in (সেকেন্ড) — savePersisted()-এ
    // ব্যবহারের জন্য। Supabase সাধারণত 3600 পাঠায়; না পেলে 3600 ধরে নেওয়া হয়
    // (savePersisted()-এর নিজস্ব safe default)।
    @Volatile private var lastExpiresIn: Int = 0

    /** Sign in with Staff Code + module password. Returns null on success, else error text.
     *  Blocking network call — run it on a background thread. */
    fun signIn(code: String, password: String): String? {
        try {
            val body = JSONObject()
                .put("email", codeToEmail(code))
                .put("password", password)
                .toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url(baseUrl() + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey())
                .addHeader("Content-Type", "application/json")
                .post(body).build()
            http.newCall(req).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return try { JSONObject(txt).optString("error_description", "Sign-in failed") }
                    catch (e: Exception) { "Sign-in failed" }
                }
                val json = JSONObject(txt)
                accessToken = json.optString("access_token", "")
                lastExpiresIn = json.optInt("expires_in", 3600)
                if (accessToken.isNullOrBlank()) return "Sign-in failed"
            }
            val id = getRows("hr", "app_identity", "select=person_code,role_kind,is_master&limit=1")
            if (id.length() > 0) {
                personCode = id.getJSONObject(0).optString("person_code", code)
                isMaster = id.getJSONObject(0).optBoolean("is_master", false)
            } else { personCode = code; isMaster = false }
            return null
        } catch (e: Exception) { return e.message ?: "Sign-in error" }
    }

    // 🔴🔒 V453: context ঐচ্ছিক — পুরনো ৫+ ব্যবহারের জায়গা (identity-switch
    // পথ, `signOut()` কোনো আর্গুমেন্ট ছাড়াই) অপরিবর্তিত কাজ করবে, শুধু RAM-এর
    // টোকেন সাফ হবে (আগের মতোই)। যেখানে context দেওয়া হবে (যেমন মূল অ্যাপ
    // Logout) — সেখানে SharedPreferences-এর জমানো টোকেনও মুছে যাবে, যাতে এই
    // ফোনে পরের ব্যক্তি লগইন করলে আগের কারো cached session কখনো ফিরে না আসে।
    fun signOut(context: Context? = null) {
        // 🔐🔒 V494 (TK-যাচাই ২): জোর করে সাইন-আউট হলেও জমানো cloud-পড়া মুছবে।
        try { com.tkbiswas.pilesclinic.native.CloudReadDedupe.clear() } catch (_: Throwable) { }
        clearPersisted(context ?: appCtx)
        accessToken = null; personCode = null; isMaster = false
    }

    private fun authGet(url: String, schema: String): Request =
        Request.Builder().url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
            .addHeader("Accept-Profile", schema)
            .get().build()

    /** SELECT from a schema-qualified table. `query` is a raw PostgREST query. */
    fun getRows(schema: String, table: String, query: String): JSONArray {
        return try {
            http.newCall(authGet(baseUrl() + "/rest/v1/" + table + "?" + query, schema)).execute().use { resp ->
                val txt = resp.body?.string() ?: "[]"
                if (resp.isSuccessful) JSONArray(txt) else JSONArray()
            }
        } catch (e: Exception) { JSONArray() }
    }

    // 🔴 B316 (03.08.2026, TK-নির্দেশ — Staff Profile Save ফাঁকা ডেটা দিয়ে
    // ওভাররাইট করার ঝুঁকি): উপরের `getRows()` সফল-খালি বনাম ব্যর্থ — এই দুটো
    // আলাদা করে না (দুটোতেই খালি JSONArray দেয়), তাই "লোড ব্যর্থ হলে Save
    // বন্ধ রাখা" নিরাপদভাবে করা যাচ্ছিল না। এই নতুন ফাংশনটা **শুধু যোগ**
    // হয়েছে — পুরনো `getRows()` ও তার ৫০+ ব্যবহারের জায়গা এক অক্ষরও বদলায়নি,
    // তাই বাকি প্রজেক্টে কোনো ঝুঁকি নেই। শুধু Staff Profile-এর Save-সুরক্ষায়
    // ব্যবহার হবে।
    data class RowsResult(val ok: Boolean, val rows: JSONArray)
    // একটাই read-চেষ্টা: (সফল?, সারি, HTTP-কোড)। কোড -1 = নেটওয়ার্ক/এক্সসেপশন।
    private fun rawRows(schema: String, table: String, query: String): Triple<Boolean, JSONArray, Int> {
        return try {
            http.newCall(authGet(baseUrl() + "/rest/v1/" + table + "?" + query, schema)).execute().use { resp ->
                val txt = resp.body?.string() ?: "[]"
                if (resp.isSuccessful) Triple(true, JSONArray(txt), resp.code)
                else Triple(false, JSONArray(), resp.code)
            }
        } catch (e: Exception) { Triple(false, JSONArray(), -1) }
    }
    fun getRowsChecked(schema: String, table: String, query: String): RowsResult {
        val first = rawRows(schema, table, query)
        if (first.first) return RowsResult(true, first.second)
        // 🔵 401 = টোকেন মেয়াদ শেষ (নেটের দোষ নয়)। একবার গোপনে re-login করে আবার চেষ্টা।
        // ⛔ additive — সফল read-এ কিছু বদলায় না, শুধু 401-পথে সেল্ফ-হিল।
        if (first.third == 401 && reAuth()) {
            val second = rawRows(schema, table, query)
            if (second.first) return RowsResult(true, second.second)
        }
        return RowsResult(false, JSONArray())
    }

    /** UPSERT one row (id-keyed) into a schema-qualified table. */
    fun upsert(schema: String, table: String, row: JSONObject): Boolean {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Content-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(JSONArray().put(row).toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) { false }
    }

    // 🔵 TK-ORDER (07.08.2026): উপরের upsert() `merge-duplicates` করে **PK (id)**
    // ধরে — কিন্তু IN TIME-এর `day`-তে id থাকে না, তাই একই দিনের সারিতে না বসে
    // (staff_code, work_date) unique-constraint-এ ধাক্কা খেয়ে **ব্যর্থ** হতে পারত।
    // এই মেথড `?on_conflict=<cols>` দিয়ে ঠিক ওই unique-key ধরে বসায় — সারি না
    // থাকলে নতুন বসে, থাকলে আপডেট হয় — কখনো ব্যর্থ হয় না, ডুপ্লিকেটও হয় না।
    // ⛔ পুরনো upsert() এক অক্ষরও বদলায়নি (অন্য জায়গায় অক্ষত)। শুধু payload-এ
    //    থাকা কলামগুলোই আপডেট হয়, বাকি ঘর (যেমন আগের check_out) অক্ষত থাকে।
    fun upsertOnConflict(schema: String, table: String, row: JSONObject, onConflict: String): Boolean {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?on_conflict=" + onConflict)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Content-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(JSONArray().put(row).toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** INSERT one row (outside_calls / call_taps / work_reports / payments). */
    fun insert(schema: String, table: String, row: JSONObject): Boolean =
        insertChecked(schema, table, row).ok

    /** 🔴🔒 V418 (TK-নির্দেশ, ১৭.০৮.২০২৬: *"ভবিষ্যতে ডুপ্লিকেট এন্ট্রির জন্য
     *  আটকে দেয়"*)
     *
     *  পুরনো `insert()` শুধু হ্যাঁ/না বলত। তাতে **"নেট নেই"** আর **"ডেটাবেস
     *  ডুপ্লিকেট বলে আটকে দিয়েছে"** — দুটোই এক দেখাত, আর পর্দায় ভুল করে
     *  "Saved offline / retry" উঠত। টাকার হিসাবে ওটা বিপজ্জনক: জমা হয়নি, অথচ
     *  ব্যবহারকারী ভাবতেন জমা হয়েছে।
     *
     *  এখন সাড়ার সংকেতটাও ফেরে, তাই পর্দায় **সৎ** কথা বলা যায়।
     *  `duplicate = true` তখনই, যখন ডেটাবেস নিজের unique-নিয়মে আটকায়
     *  (HTTP 409 / Postgres কোড 23505)।
     *  ⛔ পুরনো `insert()` আগের মতোই হ্যাঁ/না ফেরায় — কোনো ডাকার জায়গা বদলায়নি। */
    data class InsertResult(val ok: Boolean, val code: Int, val duplicate: Boolean, val message: String)

    fun insertChecked(schema: String, table: String, row: JSONObject): InsertResult {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Content-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(JSONArray().put(row).toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp ->
                val body = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                val dup = !resp.isSuccessful &&
                    (resp.code == 409 || body.contains("23505") ||
                     body.contains("duplicate key", true))
                InsertResult(resp.isSuccessful, resp.code, dup, body)
            }
        } catch (e: Exception) { InsertResult(false, -1, false, "") }
    }

    /** V325: call a schema-scoped, authenticated database function.
     * Commission payment uses this instead of writing Expense directly: the
     * database validates role/date/overpayment and writes commission + expense
     * atomically. A failed call writes neither row. Existing CRUD is untouched. */
    data class RpcResult(val ok: Boolean, val body: String, val message: String)
    fun rpc(schema: String, function: String, args: JSONObject): RpcResult {
        fun once(): Triple<Boolean, String, Int> = try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/rpc/" + function)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Accept-Profile", schema)
                .addHeader("Content-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .post(args.toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp ->
                Triple(resp.isSuccessful, resp.body?.string() ?: "", resp.code)
            }
        } catch (_: Exception) { Triple(false, "", -1) }
        var result = once()
        if (!result.first && result.third == 401 && reAuth()) result = once()
        val message = if (result.first) "" else try {
            JSONObject(result.second).optString("message", "Request failed")
        } catch (_: Exception) { "Request failed" }
        return RpcResult(result.first, result.second, message)
    }

    /** PATCH: update rows in a schema-qualified table matching `filter`. */
    fun update(schema: String, table: String, filter: String, patch: JSONObject): Boolean {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + filter)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Content-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .patch(patch.toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** PATCH করে সত্যিই অন্তত একটি সারি বদলেছে কি না যাচাই করে।
     *  PostgREST `return=minimal`-এ ০ সারিও HTTP-success হওয়ায় Remove-এর মতো
     *  গুরুত্বপূর্ণ কাজে সেই পুরনো Boolean যথেষ্ট নয়। */
    fun updateAtLeastOne(schema: String, table: String, filter: String, patch: JSONObject): Boolean {
        fun once(): Pair<Boolean, Int> = try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + filter)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: ""))
                .addHeader("Content-Profile", schema)
                .addHeader("Accept-Profile", schema)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(patch.toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: "[]"
                Pair(resp.isSuccessful && try { JSONArray(body).length() > 0 } catch (_: Throwable) { false }, resp.code)
            }
        } catch (_: Exception) { Pair(false, -1) }
        var result = once()
        if (!result.first && result.second == 401 && reAuth()) result = once()
        return result.first
    }

    /** Count rows in the EXISTING public tables (read-only; those tables have
     *  no RLS). Used for the Notebook's automatic statistics. Never writes. */
    fun countPublic(table: String, query: String): Int {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + query + "&select=id")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: anonKey()))
                .addHeader("Prefer", "count=exact")
                .get().build()
            http.newCall(req).execute().use { resp ->
                val cr = resp.header("Content-Range") ?: ""
                val total = cr.substringAfter('/', "").toIntOrNull()
                if (total != null) total else JSONArray(resp.body?.string() ?: "[]").length()
            }
        } catch (e: Exception) { 0 }
    }

    // 🔴🔒 B496 (06.08.2026, TK-এর স্টাফের রিপোর্ট — "এনকোয়ারি ছিল, এখন
    // জিরো দেখাচ্ছে") — আসল `countPublic()` নেটওয়ার্ক-ব্যর্থতা আর
    // সত্যিকারের-শূন্য আলাদা করে না (দুটোতেই 0), তাই সাময়িক দুর্বল
    // সিগন্যালেই আজকের আসল সংখ্যা "মুছে" গেছে বলে মনে হতো। পুরনো
    // `countPublic()` (৫+ জায়গায় ব্যবহৃত) এক অক্ষরও বদলানো হয়নি — এই
    // নতুন ফাংশনটাই শুধু যোগ, ব্যর্থ হলে `ok=false` জানায়।
    data class CountResult(val ok: Boolean, val count: Int)
    fun countPublicChecked(table: String, query: String): CountResult {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + query + "&select=id")
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: anonKey()))
                .addHeader("Prefer", "count=exact")
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return CountResult(false, 0)
                val cr = resp.header("Content-Range") ?: ""
                val total = cr.substringAfter('/', "").toIntOrNull()
                CountResult(true, total ?: JSONArray(resp.body?.string() ?: "[]").length())
            }
        } catch (e: Exception) { CountResult(false, 0) }
    }

    /** Sum a numeric column in public.payments for this staff in a range. */
    fun sumPublic(table: String, query: String, col: String): Double {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + query + "&select=" + col)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: anonKey()))
                .get().build()
            http.newCall(req).execute().use { resp ->
                val arr = JSONArray(resp.body?.string() ?: "[]")
                var s = 0.0
                for (i in 0 until arr.length()) s += arr.getJSONObject(i).optDouble(col, 0.0)
                s
            }
        } catch (e: Exception) { 0.0 }
    }

    // 🔴🔒 AUDIT FIX (2026-08-06, TK approved — same family as B496/B316):
    // the original `sumPublic()` returns 0.0 both on genuine-zero AND on a
    // network failure, so a weak signal could make today's real Collection
    // look like ₹0. The old `sumPublic()` (and its existing callers) is left
    // untouched — this is an ADD-ONLY checked variant that reports ok=false on
    // failure, so callers can show "…" instead of a misleading ₹0.
    data class SumResult(val ok: Boolean, val sum: Double)
    fun sumPublicChecked(table: String, query: String, col: String): SumResult {
        return try {
            val req = Request.Builder().url(baseUrl() + "/rest/v1/" + table + "?" + query + "&select=" + col)
                .addHeader("apikey", anonKey())
                .addHeader("Authorization", "Bearer " + (accessToken ?: anonKey()))
                .get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return SumResult(false, 0.0)
                val arr = JSONArray(resp.body?.string() ?: "[]")
                var s = 0.0
                for (i in 0 until arr.length()) s += arr.getJSONObject(i).optDouble(col, 0.0)
                SumResult(true, s)
            }
        } catch (e: Exception) { SumResult(false, 0.0) }
    }

    /** Log an in-app call-button press (owner rule 8). Records ONLY the press —
     *  never claims the call connected, never a duration. Silent on failure.
     *
     *  🔴 B406 (04.08.2026, TK-রিপোর্ট, Laxmi/Kishanganj — "৩ বার কল করলাম,
     *  App Calls-এ ২ দেখাচ্ছে"): আসল কারণ — এই ফাংশন আগে `if (!isSignedIn)
     *  return` করেই থেমে যেত, চুপচাপ, কোনো রেকর্ড না রেখে। ModuleAuth-এর নিজের
     *  সাইন-ইন (Work Notebook/Staff Profile/Income-Expense খুললে তবেই হয়)
     *  আর মূল অ্যাপের কল-বোতাম চাপা — দুটো সম্পূর্ণ স্বাধীন। তাই স্টাফ যদি
     *  আজ এখনো Work Notebook না খুলে থাকেন (বা অ্যাপ সদ্য চালু হয়ে থাকে),
     *  প্রথম কল-চাপাটা লগ-ই হতো না — ঠিক এইভাবেই সংখ্যা কম দেখাত। এখন সাইন-ইন
     *  না থাকলে **প্রথমে নিঃশব্দে সাইন-ইন করার চেষ্টা** হয়, তারপরই লগ করা হয়
     *  (ডায়ালার খোলায় কোনো দেরি হয় না, এটা আলাদা ব্যাকগ্রাউন্ড থ্রেডে)। ⛔
     *  ব্যর্থ হলেও (নেট না থাকলে) আগের মতোই নিঃশব্দে বাদ — কল-করাটা কখনো
     *  আটকায় না, শুধু গোনাটা মিস হতে পারে।
     *
     *  🔴 নিজে-ধরা সংশোধন (একই B406 কমিটে, ডেলিভারির আগে দ্বিতীয়বার
     *  যাচাই করতে গিয়ে ধরা পড়েছে): শুধু `!isSignedIn` চেক করলে B317-এর
     *  ঠিক করা ঝুঁকিই (এক ব্যবহারকারীর পর অন্যজন লগইন করলে আগের ব্যক্তির
     *  Module-পরিচয় থেকে যাওয়া) এই একটা জায়গায় ফিরে আসত — যদি আগের
     *  ব্যবহারকারী A কোনো Module স্ক্রিন খুলে ModuleAuth-এ সাইন-ইন করে
     *  রেখে যান, তারপর B লগইন করে সরাসরি কল-বোতাম চাপেন (কোনো Module
     *  স্ক্রিন না খুলেই), `isSignedIn` তখনো true (A-এর) — কল-ট্যাপ ভুল
     *  করে A-এর নামে লগ হতো। এখন `ModuleUi.ensureSignedIn()`-এর same
     *  identity-check এখানেও — `personCode == expectedCode(context)`
     *  না মিললে আগে `signOut()` করে তারপর বর্তমান ব্যবহারকারী হিসেবে
     *  নতুন করে সাইন-ইন করা হয়। */
    // 🔴🔒 B503 (06.08.2026, TK-নির্দেশ — "সাথে সাথে দেখাতে হবে") — এই
    // ফোনেই কতগুলো কল-বোতাম আজ চাপা হয়েছে তার একটা তাৎক্ষণিক, স্থানীয়
    // গণনা রাখা হয় (নেটওয়ার্ক-ছাড়া) — Work Notebook-এর "App Calls
    // (auto)" ঘরটা এখন প্রথমে এই সংখ্যা দেখাতে পারবে, পরে ক্লাউড থেকে
    // মিলিয়ে/সংশোধন করে নেবে। ⛔ আসল লগ (`call_taps` টেবিলে insert)
    // এতটুকুও বদলায়নি — এটা শুধু একটা বাড়তি, স্থানীয় গোনা।
    private fun localCallTapPrefs(context: Context) = context.getSharedPreferences("wn_local_counts", Context.MODE_PRIVATE)
    fun bumpLocalCallTapCount(context: Context, staffCode: String, dateIso: String) {
        try {
            val key = "calltaps_${staffCode}_$dateIso"
            val p = localCallTapPrefs(context)
            p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
        } catch (_: Throwable) { }
    }
    fun localCallTapCount(context: Context, staffCode: String, dateIso: String): Int {
        return try { localCallTapPrefs(context).getInt("calltaps_${staffCode}_$dateIso", 0) } catch (_: Throwable) { 0 }
    }

    fun logCallTap(mobileDigits: String, context: Context) {
        try {
            // 🔴 V452 (19.08.2026, TK-অনুমোদিত): Staff Performance-এ Master যেন
            // ভবিষ্যতের App Call-এ ঠিক কোন নম্বরে dial করা হয়েছিল সেটা দেখতে পারেন।
            // পুরনো row-তে full number ছিল না — সেগুলো আন্দাজ করে পূরণ করা হবে না।
            // এখানে শুধু caller যে digits-এ চাপ দিয়েছেন সেটাই রাখা হয়; call connected
            // হয়েছে এমন দাবি করা হয় না। Existing masked field-ও backward compatibility-এর
            // জন্য আগের মতোই রাখা হচ্ছে।
            val fullMobile = mobileDigits.filter { it.isDigit() }
            val masked = if (fullMobile.length >= 4) "••••••" + fullMobile.takeLast(4) else "••••"
            // স্থানীয় গোনা সাথে সাথেই বাড়ানো হয় (ক্লাউড-লেখা ব্যর্থ হলেও
            // অন্তত এই ফোনে "আমি চাপলাম" এটা হারায় না)।
            // 🔴🔴🔴🔒 B515 (06.08.2026, TK-এর Android Studio-তে ধরা পড়া
            // বিল্ড-এরর — "Type mismatch: inferred type is String? but
            // String was expected") — `expectedCode(context)` নাল-হতে-পারা
            // (`String?`), কিন্তু `bumpLocalCallTapCount()`-এর `staffCode`
            // প্যারামিটার নন-নাল `String` চায় — সরাসরি পাঠানোয় কম্পাইল
            // ব্যর্থ হতো। এখন নাল হলে (`expectedCode` জানা না থাকলে)
            // স্থানীয় গণনাটাই বাদ দেওয়া হয় (null-check দিয়ে) — বাকি
            // সব যুক্তি অক্ষত।
            try {
                val staffCodeNow = expectedCode(context)
                if (staffCodeNow != null) {
                    // 🔴 V592 — এখানেও ভারতীয় সময়, নইলে ফোনের ঘড়ি অন্য টাইমজোনে
                    //    থাকলে জমা-গোনা আর পড়া-গোনা আলাদা দিনে পড়ে যেত
                    //    (পড়ার দিকে `todayIso()` আগে থেকেই Asia/Kolkata)।
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
                        .format(java.util.Date())
                    bumpLocalCallTapCount(context, staffCodeNow, today)
                }
            } catch (_: Throwable) { }
            Thread {
                try {
                    val expected = expectedCode(context)
                    if (isSignedIn && personCode != expected) signOut()
                    if (!isSignedIn) { try { signInCurrentSession(context) } catch (_: Throwable) {} }
                    if (isSignedIn) {
                        /* 🔵🔒 V592 (২৩.০৮.২০২৬) — **`call_date` এখান থেকে পাঠানো
                           হয় না, ইচ্ছে করেই।**

                           প্রথমে ভেবেছিলাম না-পাঠানোটাই গোনা-০-এর একটা কারণ, কিন্তু
                           TK ডেটাবেসে চালিয়ে দেখালেন — সত্যি নয়। ঘরটার ডিফল্ট
                           আগে থেকেই ঠিক আছে, আর ভারতীয় সময়েই:
                               call_date  date  DEFAULT ((now() AT TIME ZONE
                                                'Asia/Kolkata'::text))::date  NOT NULL
                           ৭২৩টা সারির একটারও তারিখ ফাঁকা নয় (যাচাই করা)।

                           ⇒ তাই তারিখটা **সার্ভারই** বসাবে, ফোন নয়। কারণ সার্ভারের
                             ঘড়ি সবসময় ঠিক, কিন্তু কোনো স্টাফের ফোনের ঘড়ি/টাইমজোন
                             ভুল থাকলে ফোন থেকে পাঠানো তারিখ ভুল দিনে বসিয়ে দিত —
                             অর্থাৎ যা ঠিক আছে তাকে খারাপ করা হত।
                           ⚠️ ওয়েব (`module_core.js`) নিজে থেকে তারিখ পাঠায়, কিন্তু
                              সেটা একই ডিফল্টের সঙ্গেই মেলে, তাই কোনো অমিল হয় না। */
                        val row = JSONObject()
                            .put("staff_code", personCode)
                            .put("target_mobile_mask", masked)
                        if (fullMobile.isNotBlank()) row.put("target_mobile", fullMobile)
                        insert("wn", "call_taps", row)
                    }
                } catch (_: Throwable) { /* logging must never affect the call */ }
            }.start()
        } catch (e: Exception) { /* logging must never affect the call */ }
    }
}
