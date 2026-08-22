/* =====================================================================
   V245 — MODULE 1 : PROFESSIONAL PROFILE & SALARY.
   Master: view/edit every profile + salary + record payments.
   Staff/Doctor/Field: view ONLY their own profile + salary history.
   Data in schema `hr` (RLS). Numbers shown masked. English UI.
   ===================================================================== */
package com.tkbiswas.pilesclinic.modules

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.native.NativeSession
import com.tkbiswas.pilesclinic.native.PhotoUtils
import com.tkbiswas.pilesclinic.native.TripleTapEdit
import com.tkbiswas.pilesclinic.native.UserPhotoStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class StaffProfileActivity : AppCompatActivity() {

    // 🔴 বাগ-ফিক্স (02.08.2026, TK-রিপোর্ট Income & Expense-এ, একই কারণ এখানেও
    // ছিল বলে খুঁজে ঠিক করা হলো): সিস্টেম Back আগে সরাসরি হোমে চলে যেত।
    private var backAction: () -> Unit = { finish() }

    /**
     * 🔴🔴🔒 V511 (২১.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ — *"Calls From App"* পর্দা):
     * *"এখান থেকে ব্যাক বাটনে কাজ করে না। মোবাইলের ব্যাক বাটন দিয়ে চাপ দিলেও
     * কোনো কাজ হয় না।"*
     *
     * ─── আসল কারণ (কোড ধরে বার করা, আন্দাজ নয়) ────────────────────────────
     * `perfListScreen()` আগে লিখত:
     *      val prevBack = backAction
     *      backAction = { prevBack() }
     * অর্থাৎ **তখন যা-ই `backAction` থাকুক, সেটাই** ধরে নিত। সাধারণ পথে ঠিকই
     * চলত, কিন্তু এই পথে ভেঙে যেত —
     *   ১. Performance → "Calls from app" → তালিকা খুলল (Back = Performance ✅)
     *   ২. তালিকার একটা সারিতে চাপ → Detail খুলল। `perfDetailScreen` বসাল
     *      `backAction = { perfShowCallsList(...) }` (অর্থাৎ "তালিকায় ফেরো")।
     *   ৩. Detail থেকে Back → তালিকা আবার আঁকা হলো। কিন্তু এইবার
     *      `prevBack` = **"তালিকায় ফেরো"**, তাই `backAction` দাঁড়াল
     *      "তালিকায় ফেরো" — অর্থাৎ Back চাপলে **একই তালিকাই আবার আঁকে**।
     *   ⇒ পর্দায় কিচ্ছু বদলায় না। TK-এর দেখা "ব্যাক কাজ করে না" ঠিক এটাই।
     *      (সিস্টেম Back-ও একই `backAction()` ডাকে, তাই সেটাও আটকে যেত।)
     *
     * ─── সমাধান ───────────────────────────────────────────────────────────
     * তালিকা-পর্দার ফেরার ঠিকানা আর "তখন যা ছিল" থেকে আন্দাজ করা হয় না —
     * `performanceOne()` নিজে একবার এখানে লিখে রাখে "আমার কাছে ফিরবে", আর
     * `perfListScreen()` সেটাই ব্যবহার করে। ডিটেল থেকে যতবারই ফিরুক, ঠিকানাটা
     * বদলায় না, তাই লুপ তৈরি হতে পারে না।
     * ⛔ কোনো তথ্য/হিসাব/ডিজাইন ছোঁয়া হয়নি — শুধু "Back চাপলে কোথায় যাব"।
     */
    private var perfListBack: (() -> Unit)? = null

    /* 🎨 V417গ (মডেল ৩): স্যালারি পর্দার আলাদা দুটো কার্ড চিনে রাখার নাম।
       ⛔ পর্দা আবার আঁকা হলে পুরনোটা এই নাম ধরে সরানো হয়, তাই কার্ড জমে না। */
    private val SAL_TAG_EXTRA = "salExtraCard"
    private val SAL_TAG_CFG = "salCfgCard"
    override fun onBackPressed() { backAction() }

    /* ═══════════════════════════════════════════════════════════════════════
       🔧 V486 (20.08.2026, TK-রিপোর্ট): *"যে স্টাফের Performance খুললাম, Back
       করলে আবার সেখানেই আসার কথা — কিন্তু পুরো উপরে চলে যাচ্ছে"*।

       আসল কারণ: এই পর্দাগুলো আলাদা Activity নয় — একই Activity বারবার নতুন
       করে আঁকা হয় (renderList / performanceOne …)। Back মানে renderList()
       আবার ডাকা, অর্থাৎ **সম্পূর্ণ নতুন ScrollView** — আর নতুন ScrollView
       সবসময় একদম উপর থেকে শুরু হয়। তাই তালিকার নিচে থাকা স্টাফ দেখতে হলে
       প্রতিবার আবার নিচে নামতে হত।

       সমাধান: তালিকায় থাকা অবস্থায় কতটা নিচে নামা হয়েছে সেটা মনে রাখা হয়,
       আর তালিকা আবার আঁকা হলে ঠিক সেই জায়গাতেই ফিরিয়ে দেওয়া হয়।
       ⛔ কোনো হিসাব · ডেটা · ডিজাইন · বোতাম কিছুই বদলায়নি — শুধু তালিকা
          কোথায় দাঁড়াবে সেটুকু।
       ═══════════════════════════════════════════════════════════════════ */
    private var listScrollY = 0
    private var listScroll: android.widget.ScrollView? = null
    private var trackListScroll = true

    /** তালিকা আঁকা শেষে — মনে রাখা জায়গায় ফিরিয়ে দাও। */
    private fun restoreListScroll() {
        val sv = listScroll ?: return
        val want = listScrollY
        if (want <= 0) { trackListScroll = true; return }
        // আঁকার সময় উচ্চতা ক্ষণিকের জন্য ০ হয়ে যায় — তখন ScrollView নিজে
        // ০-তে নেমে আসে। সেই ভুল মানটা যেন মনে না থাকে, তাই মাপা বন্ধ রাখি।
        trackListScroll = false
        sv.post {
            sv.scrollTo(0, want)
            sv.post { trackListScroll = true }
        }
    }

    // ---------- Photo (V252, TK-অনুরোধে) ----------
    // patients.photo-এর হুবহু একই প্রমাণিত পথ (PhotoUtils) — নতুন কোনো Storage
    // bucket/জটিলতা যোগ হয়নি। "Change Photo"-তে ৩-ট্যাপ লক (TripleTapEdit, ঠিক
    // ব্রাঞ্চ-লকের মতোই) — ভুল করে চাপলে ছবি বদলাবে না।
    private var pendingPhotoDataUrl: String? = null   // নতুন বাছা ছবি (এখনো সেভ হয়নি)
    private var currentPhotoDataUrl: String? = null   // আগে থেকে সেভ করা ছবি
    private var photoPreview: ImageView? = null
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val dataUrl = PhotoUtils.encodeResized(this, uri)
            if (dataUrl != null) {
                pendingPhotoDataUrl = dataUrl
                photoPreview?.setImageBitmap(PhotoUtils.decodeDataUrl(dataUrl))
                ModuleUi.toast(this, "Photo picked — press Save to keep it")
            } else ModuleUi.toast(this, "Could not read image")
        }
    }

    private fun todayIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US); f.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return f.format(java.util.Date())
    }
    /* 🔴 V430 (TK-সিদ্ধান্ত ১৮.০৮.২০২৬: "₹2,10,850 — ভারতীয় ভাগ") — এই একটা
       জায়গায় `Locale.US` বসানো ছিল, তাই ২,১০,৮৫০-এর বদলে ২১০,৮৫০ দেখাত —
       অ্যাপের বাকি সব পর্দার (ও কম্পিউটারের) সঙ্গে মিলত না। এখন ভারতীয়
       ভাগেই দেখাবে। ⛔ অঙ্ক একটুও বদলায়নি, শুধু কমা বসার জায়গা। */
    private fun money(n: Double): String = "₹" + com.tkbiswas.pilesclinic.native.MoneyFormat.inr(n)

    // 🔴 বাগ-ফিক্স (02.08.2026): Android-এর org.json-এ কোনো ঘর ডেটাবেসে SQL NULL হলে
    // optString(key) আসলে খালি "" ফেরত দেয় না — সাক্ষাৎ শব্দ "null" ফেরত দেয় (Android-এর
    // JSON.toString(JSONObject.NULL) == "null")। তাই সব জায়গায় সরাসরি optString() না ডেকে
    // এই ns() ব্যবহার করা হচ্ছে, যেটা "null" শব্দটাকেও খালি ধরে।
    private fun ns(o: JSONObject, key: String): String {
        val v = o.optString(key)
        return if (v.isBlank() || v == "null") "" else v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = NativeSession.current(this)?.name ?: ""
        // 🟢 B629 (11.08.2026): "Salary Due" reminder থেকে সরাসরি এই স্টাফের Salary পর্দা
        //   খোলার জন্য (শুধু Master; Master ছাড়া কেউ অন্যের Salary খুলতে পারে না)।
        val salaryFor = intent.getStringExtra("salaryFor")
        /* 🔵🔒 V523 (২২.০৮.২০২৬, TK-নির্দেশ): Reports-এর Staff-wise অংশ থেকে
           সরাসরি **পুরো** Staff Performance পর্দায় আসার পথ — TK-কে আর খুঁজে
           বেড়াতে হবে না, আর দুই জায়গার সংখ্যা নিয়ে বিভ্রান্তিও থাকবে না।
           ⛔ `salaryFor`-এর হুবহু একই প্যাটার্ন। ⛔ শুধু Master (নিচের শর্তেই)।
           ⛔ ঘরটা না এলে (পুরোনো সব ডাক) আচরণ অবিকল আগের মতোই। */
        val openPerf = intent.getBooleanExtra("openPerformance", false)
        ModuleUi.ensureSignedIn(this, code) {
            if (!salaryFor.isNullOrBlank() && ModuleAuth.isMaster) salary(salaryFor)
            else if (openPerf && ModuleAuth.isMaster) performanceList("")
            else if (ModuleAuth.isMaster) renderList() else renderSelf()
        }
    }

    // ---------- MASTER: list all ----------
    private fun renderList() {
        backAction = { finish() }
        val root = ModuleUi.screen(this, "🧑‍💼 Staff Profiles")
        // 🔧 V486: এই তালিকার ScrollView চিনে রাখি + কতটা নিচে নামা হচ্ছে মাপি।
        listScroll = (root.parent as? android.widget.ScrollView)?.also { sv ->
            sv.setOnScrollChangeListener { _, _, y, _, _ -> if (trackListScroll) listScrollY = y }
        }
        // 🔴 B315 (03.08.2026, TK-নির্দেশ): আগে "Loading..."-এর জন্য আলাদা `box`
        // কার্ড ছিল, কিন্তু আসল স্টাফ-কার্ডগুলো সরাসরি `root`-এ যোগ হতো — তাই
        // লোড হওয়ার পর `box` খালি (অপ্রয়োজনীয়) কার্ড হয়ে থেকে যেত, আর Back
        // বোতাম (তখন box-এর ঠিক পরেই যোগ করা হতো) তালিকার মাঝে/আগে দেখাত,
        // শেষে না। এখন একটাই `listBox` কনটেইনার — শুরুতে "Loading...", পরে
        // ঠিক ওই একই জায়গায় আসল কার্ড বা "No profiles." বসে — কোনো খালি বক্স
        // থাকে না। Back বোতাম `listBox`-এর পরে (তাই সবসময় সম্পূর্ণ তালিকার
        // নিচে) একবারই যোগ করা হয়, তালিকা পরে আপডেট হলেও Back-এর অবস্থান
        // বদলায় না (ভেতরের কনটেন্ট বদলায়, `listBox`-এর নিজের অবস্থান না)।
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // 🔴🔒 B505 (06.08.2026, TK-নির্দেশ, সাবধানে করার শর্তে) — এই ফোনে
        // জমানো শেষ সফল স্টাফ-তালিকা থাকলে সাথে সাথেই দেখানো হয় (Loading...
        // এর বদলে), তারপর ক্লাউড থেকে হালনাগাদ তালিকা এলে বদলে যায়। ⛔
        // তালিকা-বাছাই/বেতনের হিসাব-নিয়ম একটুও বদলায়নি — একই রেন্ডার-
        // ফাংশন (renderStaffList) দুইবার (প্রথমে ক্যাশ, পরে আসল) ডাকা হয়।
        /* 🏆🔒 V419 (TK-নির্দেশ, ১৭.০৮.২০২৬): সবার পারফরম্যান্স এক পর্দায় —
           তালিকার উপরে একটাই বোতাম। ⛔ শুধু Master দেখতে পাবেন। */
        if (ModuleAuth.isMaster) {
            root.addView(salOutlineButton("🏆 Staff Performance", "#0A5C33", "#0A5C33") { performanceList("") })
        }
        val cachedNow = loadCachedStaffList()
        if (cachedNow != null) {
            // 🔴 V509 নিজের যাচাইয়ে ধরা পড়েছে (২১.০৮.২০২৬): নিচের ক্লাউড-আঁকায়
            // ঝিলিক-পাহারা বসানো হয়েছিল, কিন্তু **ক্যাশের এই প্রথম আঁকাটা
            // পাহারার ভিতর দিয়ে যেত না** — তাই পাহারার কাছে "আগে কী আঁকা
            // হয়েছিল" তথ্যটাই থাকত না, আর সে কখনোই কাজ করত না (এই পর্দায়
            // ঝিলিক আগের মতোই থাকত)। এখানে একবার ডেকে নেওয়ায় চিহ্নটা জমা
            // থাকে। ⛔ ঘর ফাঁকা বলে এটা কখনো আঁকা আটকায় না — শুধু মনে রাখে।
            com.tkbiswas.pilesclinic.native.RedrawGuard.alreadyShowing(
                listBox, cachedNow.first.toString() + "|" + cachedNow.second.toString())
            renderStaffList(listBox, cachedNow.first, cachedNow.second)
        } else listBox.addView(ModuleUi.body(this, "Loading..."))
        root.addView(listBox)
        root.addView(ModuleUi.button(this, "Back") { finish() })
        restoreListScroll()   // 🔧 V486: আগের জায়গায় ফিরে যাও (নতুন হলে ০ = উপরে)
        Thread {
            // 🔴 বাগ-ফিক্স (02.08.2026, TK-রিপোর্ট Supabase খরচ বেশি দেখে ধরা পড়েছে):
            // এই তালিকায় ছবি দেখানোই হয় না (শুধু Edit-এ একজনের ছবি দেখা যায়), অথচ
            // আগে select=* দিয়ে **সবার ছবিসহ (photo_data, ৫০-১০০ KB প্রতিটা)** পুরো
            // রেকর্ড টানা হতো — তালিকা একবার খুললেই অকারণে অনেক ডেটা খরচ হতো।
            // এখন শুধু তালিকায় সত্যিই যা দেখানো হয় সেই কলামগুলোই টানা হচ্ছে।
            // 🔵 TK-ORDER (07.08.2026): getRowsChecked — পড়া ব্যর্থ ও "সত্যিই খালি"
            // আলাদা। আগে getRows ব্যর্থে খালি ফেরাত → saveCachedStaffList(খালি) ভালো
            // cache মুছত → Master পরের বারও "No profiles." দেখত। এখন ব্যর্থ হলে
            // cache/তালিকা ছোঁব না। ⛔ একই দুটো cloud-read (free-plan-এ বাড়তি নয়)।
            // 🔁 পুরনো: ModuleAuth.getRows("hr","staff_profiles",…) ও ("hr","salary_config","select=*")
            val rowsR = ModuleAuth.getRowsChecked(
                "hr", "staff_profiles",
                // 🔴 V404 (16.08.2026): `active` ঘরটা যোগ করা হলো — বাদ-দেওয়া কর্মী
                //    আলাদা করতে। ⛔ পুরনো cache-এ ঘরটা নেই; optBoolean("active", true)
                //    ⇒ সচল ধরা হয়, তাই পুরনো cache-এ কেউ হঠাৎ উধাও হবে না।
                "select=person_code,designation,role_kind,branch,full_name,link_mobile,active&order=person_code"
            )
            val cfgR = ModuleAuth.getRowsChecked("hr", "salary_config", "select=*")
            if (!rowsR.ok || !cfgR.ok) {
                runOnUiThread {
                    if (cachedNow == null) {
                        listBox.removeAllViews()
                        listBox.addView(ModuleUi.body(this, "Could not load. Please try again."))
                    }
                }
                return@Thread   // ব্যর্থ পড়া — ভালো cache/তালিকা অক্ষত
            }
            val rows = rowsR.rows
            val cfg = cfgR.rows
            saveCachedStaffList(rows, cfg)
            val cfgMap = HashMap<String, JSONObject>()
            for (i in 0 until cfg.length()) cfgMap[cfg.getJSONObject(i).optString("person_code")] = cfg.getJSONObject(i)
            runOnUiThread {
                /* 🔴🔒 V509 (TK-রিপোর্ট ২১.০৮.২০২৬ — "স্ক্রিন কম্পন দিচ্ছে"):
                   এই পর্দা আগে **দুবার** আঁকত — প্রথমে ফোনে জমানো তালিকা, তারপর
                   ক্লাউড থেকে এসে পুরোটা মুছে আবার। বেশিরভাগ সময় দুটো হুবহু
                   এক, তাই দ্বিতীয়বার মুছে-আঁকাটাই চোখে **ঝিলিক** লাগত।
                   এখন হুবহু এক হলে আর আঁকা হয় না।
                   ⛔ এক চুল আলাদা হলেই আগের মতোই পুরো আঁকে — কিছু চাপা পড়ে না।
                   ⛔ ক্লাউড-কল · cache সেভ · তালিকার নিয়ম — কিছুই বদলায়নি। */
                if (!com.tkbiswas.pilesclinic.native.RedrawGuard.alreadyShowing(
                        listBox, rows.toString() + "|" + cfg.toString())) {
                    trackListScroll = false   // 🔧 V486: আঁকার সময়ের ভুল মান মনে রেখো না
                    listBox.removeAllViews()
                    renderStaffList(listBox, rows, cfg)
                    restoreListScroll()       // 🔧 V486: ক্লাউড থেকে আসার পরেও একই জায়গা
                }
            }
        }.start()
    }

    private fun staffListCachePrefs() = getSharedPreferences("staff_profile_cache", MODE_PRIVATE)
    private fun loadCachedStaffList(): Pair<JSONArray, JSONArray>? {
        return try {
            val p = staffListCachePrefs()
            val rowsJson = p.getString("rows", null) ?: return null
            val cfgJson = p.getString("cfg", null) ?: return null
            Pair(JSONArray(rowsJson), JSONArray(cfgJson))
        } catch (_: Throwable) { null }
    }
    private fun saveCachedStaffList(rows: JSONArray, cfg: JSONArray) {
        try { staffListCachePrefs().edit().putString("rows", rows.toString()).putString("cfg", cfg.toString()).apply() } catch (_: Throwable) { }
    }

    private fun renderStaffList(listBox: LinearLayout, rows: JSONArray, cfg: JSONArray) {
        val cfgMap = HashMap<String, JSONObject>()
        for (i in 0 until cfg.length()) cfgMap[cfg.getJSONObject(i).optString("person_code")] = cfg.getJSONObject(i)
        var shown = 0
        // 🔴 V404 (16.08.2026, TK-নির্দেশ): বাদ-দেওয়া কর্মী (active=false) মূল
        //    তালিকায় আসবে না — নিচে আলাদা "Removed Staff" ভাগে গোনা থাকবে,
        //    ভুল হলে Restore করা যাবে। ⛔ চুপচাপ লুকোনো নয়।
        val removedList = ArrayList<JSONObject>()
        for (i in 0 until rows.length()) {
            val p = rows.getJSONObject(i)
            val pc = p.optString("person_code")
            val roleKind = ns(p, "role_kind")
            // ⛔ পুরনো cache-এ `active` ঘরটা নেই ⇒ ডিফল্ট true ⇒ কেউ উধাও হবে না।
            if (roleKind.equals("staff", ignoreCase = true) && !p.optBoolean("active", true)) {
                removedList.add(p); continue
            }
            // 🔴 B306 (03.08.2026, TK-নির্দেশ): এই তালিকায় শুধু স্টাফ থাকবে —
            // ডাক্তার/মাস্টার/ফিল্ড অফিসার এখানে দেখানো হবে না। ডেটা এখনো
            // hr.staff_profiles-এই আছে (কিছু মোছা হয়নি), শুধু এই পর্দায়
            // রেন্ডার-লেভেলে বাদ দেওয়া হচ্ছে।
            if (!roleKind.equals("staff", ignoreCase = true)) continue
            shown++
            val sc = cfgMap[pc]
            val desig = ns(p, "designation").ifBlank { roleKind }
            val fullName = ns(p, "full_name").ifBlank { "(name not set)" }
            val salTxt = if (sc != null && sc.optBoolean("salary_enabled", false))
                "Salary: " + money(sc.optDouble("salary_amount", 0.0)) + " (day " + ns(sc, "salary_date") + ")" else "Salary: disabled"
            listBox.addView(staffCard(pc, desig, roleKind, ns(p, "branch"), fullName, ns(p, "link_mobile"), salTxt,
                onView = { editProfile(pc) }, onSalary = { salary(pc) }))
        }
        if (shown == 0) listBox.addView(ModuleUi.body(this, "No profiles."))
        // 🔴 V404: বাদ-দেওয়া কর্মীদের আলাদা ভাগ — শেষে, ছোট করে।
        if (removedList.isNotEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Removed Staff (" + removedList.size + ")"
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#6B7A72"))
                setPadding(dp(4), dp(18), dp(4), dp(2))
            })
            for (p in removedList) {
                val pc = p.optString("person_code")
                listBox.addView(
                    staffCard(
                        pc,
                        ns(p, "designation").ifBlank { ns(p, "role_kind") },
                        ns(p, "role_kind"),
                        ns(p, "branch"),
                        ns(p, "full_name").ifBlank { "(name not set)" },
                        ns(p, "link_mobile"),
                        "Salary: disabled",
                        onView = { editProfile(pc) },
                        onSalary = { salary(pc) },
                        isRemoved = true
                    )
                )
            }
        }
    }

    // 🔴 লক করা ডিজাইন (03.08.2026, B304 মকআপ অনুমোদন, B307-এ সংশোধিত) —
    // কম্প্যাক্ট কার্ড: নাম+ব্যাজ + View/Salary ছোট বোতাম ডানপাশে। TK পরে
    // অ্যাভাটার-আইকন (রঙিন বাক্স/👤) বাদ দিতে বলেছেন — সরানো হলো, শুধু নাম-ই
    // যথেষ্ট। শুধু এই ফাইলেই ব্যবহৃত, ModuleUi.kt ছোঁয়া হয়নি।
    private fun dp(v: Int) = ModuleUi.dp(this, v)

    /* 🔴🔒 V442 (TK-নির্দেশ ১৮.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত: "প্রফেশনাল বানানো
       যায় কি") — আগে View/Salary/Performance/Suspend/Remove একটার নিচে
       একটা লম্বা কলামে ডানপাশে সরু জায়গায় গাদাগাদি ছিল (কার্ড অনেক লম্বা
       দেখাত)। এখন card উপরে-নিচে (তথ্য পুরো চওড়া জুড়ে, তার নিচে বোতাম দুই
       সারিতে পাশাপাশি — View·Salary·Performance / Suspend·Remove)।
       ⛔ কোনো বোতামের কাজ/রং/লেবেল বদলায়নি — শুধু জায়গা। */
    private fun staffCard(
        pc: String, desig: String, roleKind: String, branch: String, fullName: String,
        mobile: String, salaryText: String, onView: () -> Unit, onSalary: () -> Unit,
        // 🔴 V404 (16.08.2026): বাদ-দেওয়া কর্মীর কার্ডে Suspend/Remove-এর বদলে
        //    শুধু Restore থাকবে। ডিফল্ট false ⇒ পুরনো সব ডাক অবিকল আগের মতোই চলে।
        isRemoved: Boolean = false
    ): LinearLayout {
        val card = ModuleUi.card(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        topRow.addView(TextView(this).apply {
            text = fullName; textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1C2B22"))
        })
        val isDoc = roleKind.equals("doctor", true)
        topRow.addView(TextView(this).apply {
            text = desig.ifBlank { if (isDoc) "Doctor" else "Staff" }; textSize = 9.5f
            setTextColor(android.graphics.Color.parseColor(if (isDoc) "#6A3FCB" else "#0B8A3E"))
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(android.graphics.Color.parseColor(if (isDoc) "#EFEAFB" else "#EAF6EE"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(6) }
        })
        info.addView(topRow)
        info.addView(TextView(this).apply {
            text = pc + " · " + branch + " · " + ModuleUi.fullMobile(mobile)   // 🔵 V521 (TK): পুরো নম্বর
            textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#6B7A72"))
            setPadding(0, dp(2), 0, 0)
        })
        info.addView(TextView(this).apply {
            text = salaryText; textSize = 10.5f
            setTextColor(android.graphics.Color.parseColor(if (salaryText.startsWith("Salary: disabled")) "#A7B0AB" else "#0B8A3E"))
            setPadding(0, dp(3), 0, dp(10))
        })
        // 🔴 V442 — দুই সারির অনুভূমিক বোতাম-বার (প্রতিটা বোতাম সমান চওড়া, `weight=1f`)।
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) }
        }
        fun rowBtnParams(first: Boolean, last: Boolean) =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = if (first) 0 else dp(4)
                marginEnd = if (last) 0 else dp(4)
            }
        fun smallBtn(text: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
            this.text = text; textSize = 11.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(9))
            setTextColor(android.graphics.Color.parseColor(if (filled) "#FFFFFF" else "#0B4F2A"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                if (filled) setColor(android.graphics.Color.parseColor("#0B8A3E"))
                else { setColor(android.graphics.Color.parseColor("#EAF6EE")); setStroke(dp(1), android.graphics.Color.parseColor("#CFE9D8")) }
            }
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        // 🔴 V404: লাল বোতাম বানানোর একটাই জায়গা (Suspend ও Remove একই চেহারার)।
        fun dangerBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 11.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(9))
            setTextColor(android.graphics.Color.parseColor("#B0392B"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(android.graphics.Color.parseColor("#FDECEA")); setStroke(dp(1), android.graphics.Color.parseColor("#F2C6C0"))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        val row1Btns = mutableListOf<TextView>()
        /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — *"এই কার্ডের মধ্যে ভিউ থাকবে না,
           কিন্তু কার্ডে চাপ দিলে ভিউ হবে। তাহলে Salary · Performance ·
           Fix Attendance — এই তিনটা পাশাপাশি থাকবে।"*
           ⇒ "View" বোতামটা সরানো হলো; তার কাজটা এখন **পুরো কার্ডে চাপ** দিলেই হয়।
           ⛔ `onView` ফাংশনটা এক অক্ষরও বদলায়নি — শুধু কোথা থেকে ডাকা হচ্ছে সেটা।
           ⛔ ভিতরের বোতামগুলো নিজের কাজই করে (Android-এ ভিতরের ক্লিক আগে চলে ও
              সেখানেই থেমে যায়), তাই Salary চাপলে ভুল করে View খুলবে না। */
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { onView() }
        row1Btns.add(smallBtn("Salary", true, onSalary))
        // 🏆 V419: এই একজনের পুরো হিসাব (Master ছাড়া বোতামটাই আসে না)।
        if (ModuleAuth.isMaster) row1Btns.add(smallBtn("Performance", false) { performanceOne(pc, "") })
        // 🔴🔴🔒 V477 (20.08.2026, TK-জরুরি নির্দেশ — "সমস্ত স্টাফের একই সমস্যা,
        // OUT TIME দেখাচ্ছে না") — আসল কারণ (যাচাই করা): আজ সকালে JWT/reAuth
        // বাগ (V465-এ ঠিক করা) থাকাকালীন যাদের IN TIME নিঃশব্দে ক্লাউডে সেভ
        // হয়নি, তাদের জন্য এখন (সন্ধ্যা, দুপুর ১২টার সীমা পার) নিজে থেকে আর
        // IN TIME বসানোর উপায় নেই। শুধু Master-এর জন্য — যেকোনো সময়ে সেই
        // স্টাফের আজকের IN/OUT TIME সরাসরি বসানোর/ঠিক করার সুযোগ।
        // ⛔ স্টাফের নিজের Work Notebook স্ক্রিন/নিয়ম এক অক্ষরও বদলায়নি —
        //    এটা সম্পূর্ণ নতুন, আলাদা Master-only পথ, একই টেবিলে লেখে।
        if (ModuleAuth.isMaster) row1Btns.add(smallBtn("Fix Attendance", false) { fixAttendanceDialog(pc, fullName, mobile) })
        row1Btns.forEachIndexed { i, b -> b.layoutParams = rowBtnParams(i == 0, i == row1Btns.size - 1); row1.addView(b) }
        info.addView(row1)
        if (isRemoved) {
            // 🔴 V404: ভুল করে বাদ দিলে ফিরিয়ে আনার পথ।
            val restore = smallBtn("Restore", false) { restoreStaffDialog(pc, fullName) }
            restore.layoutParams = rowBtnParams(true, true)
            row2.addView(restore)
        } else {
            // 🔵🔒 B618 (11.08.2026, TK-নির্দেশ): master স্টাফকে কয়েকদিন Suspend করতে
            // পারবেন — সাসপেন্ড থাকাকালীন সে লগইন করতে পারবে না (LoginActivity গেট)।
            // ⛔ শুধু স্টাফ-তালিকায় (এই পর্দা master-only, role_kind=staff ফিল্টার করা)।
            val suspend = dangerBtn("Suspend") { suspendStaffDialog(pc, fullName) }
            suspend.layoutParams = rowBtnParams(true, false)
            row2.addView(suspend)
            // 🔴 V404 (16.08.2026, TK-নির্দেশ "কর্মী বাদ দিন বোতাম বসান"):
            //    আগে অ্যাপে বাদ দেওয়ার কোনো পথই ছিল না — শুধু Suspend ছিল।
            val remove = dangerBtn("Remove") { removeStaffDialog(pc, fullName) }
            remove.layoutParams = rowBtnParams(false, true)
            row2.addView(remove)
        }
        info.addView(row2)
        card.addView(info)
        return card
    }

    // =====================================================================
    // 🔴 V404 (16.08.2026, TK-নির্দেশ) — কর্মী বাদ দেওয়া ও ফিরিয়ে আনা
    // ---------------------------------------------------------------------
    // বাদ দিলে একটাই বোতামে তিনটে কাজ হয়:
    //   ১) hr.staff_profiles.active = false  ⇒ লগইন বন্ধ
    //      (V404 SQL-এর `suspended_until_for` তখন 2999-12-31 ফেরায় — ওয়েব ও
    //       ফোন দুটোর লগইন-গেটই এই একটা ফাংশনই ডাকে, তাই লগইন-কোডে হাত পড়েনি)
    //   ২) hr.salary_config.salary_enabled = false ⇒ আর "Salary Due"-তে নাম নয়
    //   ৩) fin.entry_permits.can_entry = false ⇒ আয়-খরচের চাবি বন্ধ
    // ⛔ রোগী · ফলোআপ · মাইনের রসিদ · ছুটি — একটাও সারি ছোঁয়া হয় না
    //    (TK-সিদ্ধান্ত: "রেকর্ড অটুট থাক")।
    // ⛔ খাতার ফাঁদ ১: RLS আটকালে update চুপচাপ ০ সারিতে চলে ও true ফেরায় —
    //    তাই এই পর্দা master-only, আর ব্যর্থ হলে সৎ বার্তা দেখানো হয়।
    // =====================================================================
    private fun removeStaffDialog(pc: String, fullName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "Remove — $fullName"))
            .setMessage(
                "$pc — remove this staff?\n\n" +
                "• Login stops\n" +
                "• Removed from the salary list\n" +
                "• Income/Expense key turned off\n\n" +
                "No past record is deleted. Can be restored later."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> doSetActive(pc, false) }
            .show()
    }

    private fun restoreStaffDialog(pc: String, fullName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "Restore — $fullName"))
            .setMessage(
                "$pc — restore this staff?\n\n" +
                "Login will work again.\n" +
                "Salary must be turned on separately from the Salary screen."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ -> doSetActive(pc, true) }
            .show()
    }

    private fun doSetActive(pc: String, active: Boolean) {
        Thread {
            val enc = java.net.URLEncoder.encode(pc, "UTF-8")
            val patch = JSONObject().put("active", active)
            val ok = try { ModuleAuth.update("hr", "staff_profiles", "person_code=eq.$enc", patch) } catch (_: Throwable) { false }
            // বাদ দিলে মাইনে ও চাবিও বন্ধ। ⛔ ফেরানোর সময় মাইনে নিজে থেকে চালু
            //    হয় না — টাকার ব্যাপার, মাস্টার নিজে Salary পর্দায় গিয়ে করবেন।
            var salOk = true; var permitOk = true
            if (ok && !active) {
                salOk = try {
                    ModuleAuth.update("hr", "salary_config", "person_code=eq.$enc",
                        JSONObject().put("salary_enabled", false))
                } catch (_: Throwable) { false }
                permitOk = try {
                    ModuleAuth.update("fin", "entry_permits", "person_code=eq.$enc",
                        JSONObject().put("can_entry", false))
                } catch (_: Throwable) { false }
            }
            runOnUiThread {
                // ⛔ সৎ বার্তা — অর্ধেক হলে "হয়ে গেছে" বলা হয় না।
                val msg = when {
                    !ok -> "Failed — check net"
                    !active && (!salOk || !permitOk) -> "Partly done. Please try again."
                    active -> "$pc restored"
                    else -> "$pc removed"
                }
                ModuleUi.toast(this, msg)
                if (ok) renderList()
            }
        }.start()
    }

    // 🔵🔒 B618: Suspend ডায়ালগ — কত দিন (3/7/নিজে) অথবা Remove।
    private fun suspendStaffDialog(pc: String, fullName: String) {
        val opts = arrayOf("3 days", "7 days", "Custom days", "Remove suspend")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "Suspend — $fullName"))
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> doSuspend(pc, 3)
                    1 -> doSuspend(pc, 7)
                    2 -> askCustomSuspendDays(pc)
                    else -> doSuspend(pc, -1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { try { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }
    private fun askCustomSuspendDays(pc: String) {
        val input = ModuleUi.numberInput(this, "Days")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "Suspend how many days?"))
            .setView(input)
            .setPositiveButton("OK") { _, _ -> val n = input.text.toString().toIntOrNull() ?: 0; if (n > 0) doSuspend(pc, n) else ModuleUi.toast(this, "Enter days") }
            .setNegativeButton("Cancel", null)
            .show().also { try { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }
    // days < 0 = Remove suspend (null বসে)।
    private fun doSuspend(pc: String, days: Int) {
        val tz = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val until: String? = if (days < 0) null else {
            val cal = java.util.Calendar.getInstance(tz); cal.add(java.util.Calendar.DAY_OF_MONTH, days)
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }.format(cal.time)
        }
        ModuleUi.toast(this, "Saving...")
        Thread {
            val patch = JSONObject().put("suspended_until", until ?: JSONObject.NULL)
            val enc = try { java.net.URLEncoder.encode(pc, "UTF-8").replace("+", "%20") } catch (_: Throwable) { pc }
            val ok = try { ModuleAuth.update("hr", "staff_profiles", "person_code=eq.$enc", patch) } catch (_: Throwable) { false }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                ModuleUi.toast(this, if (ok) (if (days < 0) "Suspend removed" else "Suspended till $until") else "Failed — check net")
            }
        }.start()
    }

    // 🔴 লক করা নিয়ম (03.08.2026, TK-নির্দেশ স্পষ্ট): "Edit" বোতাম আর নেই, নাম
    // "View" — ফিল্ড ডিফল্টে শুধু দেখা যাবে (টাইপ করা যাবে না, কীবোর্ড খুলবে
    // না), ৩-বার চাপলে তবেই সেই একটা ফিল্ড এডিটযোগ্য হবে — ঠিক ছবির মতোই
    // (TripleTapEdit পুনর্ব্যবহার, নতুন কিছু আবিষ্কার করা হয়নি)।
    private fun lockField(f: android.widget.EditText) {
        f.isFocusable = false
        f.isFocusableInTouchMode = false
        f.isCursorVisible = false
        TripleTapEdit.attach(f) {
            f.isFocusable = true
            f.isFocusableInTouchMode = true
            f.isCursorVisible = true
            f.requestFocus()
            f.setSelection(f.text.length)
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(f, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun unlockField(f: android.widget.EditText) {
        f.isFocusable = true
        f.isFocusableInTouchMode = true
        f.isCursorVisible = true
    }

    // ---------- MASTER: edit ----------
    private fun editProfile(code: String) {
        backAction = { renderList() }
        pendingPhotoDataUrl = null
        currentPhotoDataUrl = null
        // 🔴 B308 (03.08.2026, TK-অনুমোদিত "মডেল ২" মকআপ) — সবুজ গ্রেডিয়েন্ট হিরো
        // হেডার (নাম+কোড+ফটো) + আইকন-সহ ফ্ল্যাট ফিল্ড-লিস্ট (প্রতিটা সারিতে
        // আইকন+লেবেল+মান+🔒) + নিচে পাশাপাশি Back/Save। ফিল্ড-লক (B304, TripleTapEdit)
        // এক অক্ষরও বদলায়নি, শুধু চেহারা।
        val col = ModuleUi.screen(this, "")
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.parseColor("#0B4F2A"), android.graphics.Color.parseColor("#0B8A3E"))
            ).apply { cornerRadius = dp(16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) }
        }
        hero.addView(TextView(this).apply {
            text = "View $code"; textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
        })
        val photoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val img = ModuleUi.image(this); photoPreview = img
        img.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(android.graphics.Color.parseColor("#26FFFFFF"))
            setStroke(dp(1), android.graphics.Color.parseColor("#4DFFFFFF"))
        }
        // V356: Master-এর জন্য ছবি আর গোপন ৩-ট্যাপ নয়—Profile-এর ভিতরেই
        // সরাসরি Add/Change Photo বোতাম। পুরনো আলাদা Staff Photos menu বাদ।
        val changeLabel = TextView(this).apply {
            text = "📷 Add / Change Photo"; textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#E8F5EC"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#26FFFFFF"))
                setStroke(dp(1), android.graphics.Color.parseColor("#4DFFFFFF"))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { pickPhoto.launch("image/*") }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(12) }
        }
        photoRow.addView(img); photoRow.addView(changeLabel)
        hero.addView(photoRow)
        col.addView(hero)

        val name = ModuleUi.input(this, "Full Name")
        val branch = ModuleUi.input(this, "Branch")
        val designation = ModuleUi.input(this, "Designation (e.g. Staff/Receptionist)")
        val join = ModuleUi.input(this, "Join Date")
        val dob = ModuleUi.input(this, "Date of Birth")
        val gender = ModuleUi.input(this, "Gender")
        val bloodGroup = ModuleUi.input(this, "Blood Group")
        val qualification = ModuleUi.input(this, "Qualification")
        val addr = ModuleUi.input(this, "Address")
        val altMobile = ModuleUi.input(this, "Alternate Mobile")
        val emg = ModuleUi.input(this, "Emergency Contact (Name + Mobile)")
        val emgRel = ModuleUi.input(this, "Emergency Contact Relationship")
        val idType = ModuleUi.input(this, "ID Type (Aadhaar/PAN/Voter)")
        val idNum = ModuleUi.input(this, "ID Number (stored masked)")
        val notes = ModuleUi.input(this, "Notes")
        val sheet = ModuleUi.card(this)
        col.addView(sheet)
        val fieldList = listOf(
            Triple("👤", "Full Name", name), Triple("🏢", "Branch", branch), Triple("💼", "Designation", designation),
            Triple("📌", "Join Date", join), Triple("🎂", "Date of Birth", dob), Triple("⚧", "Gender", gender),
            Triple("🩸", "Blood Group", bloodGroup), Triple("🎓", "Qualification", qualification),
            Triple("🏠", "Address", addr), Triple("📱", "Alternate Mobile", altMobile),
            Triple("🚨", "Emergency Contact", emg), Triple("👪", "Emergency Relationship", emgRel),
            Triple("🪪", "ID Type", idType), Triple("🔢", "ID Number", idNum), Triple("📝", "Notes", notes)
        )
        val editAllBtn = TextView(this).apply {
            text = "Edit Profile"; textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#26FFFFFF"))
                setStroke(dp(1), android.graphics.Color.parseColor("#4DFFFFFF"))
            }
            isClickable = true; isFocusable = true
            setOnClickListener {
                fieldList.forEach { unlockField(it.third) }
                text = "Editing Enabled"
                ModuleUi.toast(this@StaffProfileActivity, "Profile can now be edited — press Save after changes")
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        hero.addView(editAllBtn)
        // 🔴🆕🔒 TK-নির্দেশ (08.08.2026, ফটো-প্রুফে লক) — এক স্ক্রিনে সব দেখাতে
        // দুই-কলাম কম্প্যাক্ট সাজ। কিছু লম্বা ঘর (নাম/ঠিকানা/জরুরি যোগাযোগ/নোট)
        // পুরো চওড়া, বাকিগুলো পাশাপাশি দুটো করে। ⛔ প্রতিটা ঘরে lockField()-এর
        // ৩-ট্যাপ এডিট অক্ষত; শুধু সাজ বদলেছে (আগে প্রতিটা ঘর আলাদা পুরো-চওড়া সারি ছিল)।
        val fullWidth = setOf("Full Name", "Address", "Emergency Contact", "Notes")
        var pendingRow: LinearLayout? = null
        fun freshRow(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        fun spacerCell() = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        for (trip in fieldList) {
            val (icon, label, field) = trip
            lockField(field)
            val cellView = fieldRow(icon, label, field)
            cellView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (label in fullWidth) {
                pendingRow?.let { pr -> pr.addView(spacerCell()); sheet.addView(pr) }
                pendingRow = null
                val r = freshRow(); r.addView(cellView); sheet.addView(r)
            } else if (pendingRow == null) {
                pendingRow = freshRow()
                pendingRow!!.addView(cellView)
            } else {
                pendingRow!!.addView(cellView)
                sheet.addView(pendingRow!!)
                pendingRow = null
            }
        }
        pendingRow?.let { pr -> pr.addView(spacerCell()); sheet.addView(pr) }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) }
        }
        val backBtn = ModuleUi.button(this, "Back") { renderList() }
        backBtn.setTextColor(android.graphics.Color.parseColor("#0B4F2A"))
        backBtn.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(10).toFloat(); setColor(android.graphics.Color.WHITE)
            setStroke(dp(1), android.graphics.Color.parseColor("#CFE9D8"))
        }
        backBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        // 🔴 B316 (03.08.2026, TK-নির্দেশ — "Save ফাঁকা ডেটা দিয়ে প্রোফাইল
        // ওভাররাইট করতে পারে"): আগে Save বোতাম শুরু থেকেই সক্রিয় থাকত, অথচ
        // নিচের প্রিফিল-Thread আলাদাভাবে, ধীরে, নেটওয়ার্কে চলত — কেউ দ্রুত Save
        // চাপলে সব ফিল্ড তখনো ফাঁকা থাকত, আর সেই ফাঁকা মানই আসল প্রোফাইলের
        // উপর সেভ হয়ে যেত। এখন Save ডিফল্টে বন্ধ (ধূসর), প্রোফাইল সফলভাবে
        // লোড হওয়ার পরেই চালু হয়। লোড ব্যর্থ হলে Save বন্ধই থাকে, বদলে
        // "⟳ Retry" দেখায়।
        val saveBtn = ModuleUi.button(this, "Loading...") {}
        saveBtn.isEnabled = false
        saveBtn.alpha = 0.5f
        fun paintSaveReady() {
            saveBtn.text = "Save"; saveBtn.isEnabled = true; saveBtn.alpha = 1f
        }
        fun doSave() {
            val row = JSONObject().put("person_code", code)
                .put("full_name", name.text.toString()).put("branch", branch.text.toString())
                .put("designation", designation.text.toString())
                .put("join_date", join.text.toString()).put("dob", dob.text.toString())
                .put("gender", gender.text.toString()).put("blood_group", bloodGroup.text.toString())
                .put("qualification", qualification.text.toString())
                .put("address", addr.text.toString()).put("alt_mobile", altMobile.text.toString())
                .put("emergency_contact", emg.text.toString()).put("emergency_relationship", emgRel.text.toString())
                .put("gov_id_type", idType.text.toString()).put("notes", notes.text.toString())
                .put("updated_at", nowIso())
            val photoToSave = pendingPhotoDataUrl ?: currentPhotoDataUrl
            if (photoToSave != null) row.put("photo_data", photoToSave)
            val idv = idNum.text.toString().filter { !it.isWhitespace() }
            if (idv.length >= 4) row.put("gov_id_last4", idv.takeLast(4))
            ModuleUi.toast(this, "Saving...")
            Thread {
                val ok = ModuleAuth.upsert("hr", "staff_profiles", row)
                runOnUiThread { ModuleUi.toast(this, if (ok) "Saved" else "Retry"); renderList() }
            }.start()
        }
        saveBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) }
        footer.addView(backBtn); footer.addView(saveBtn)
        col.addView(footer)
        // prefill existing — Save stays disabled until this genuinely succeeds
        fun loadProfile() {
            saveBtn.text = "Loading..."; saveBtn.isEnabled = false; saveBtn.alpha = 0.5f
            Thread {
                val result = ModuleAuth.getRowsChecked("hr", "staff_profiles", "select=*&person_code=eq.$code&limit=1")
                runOnUiThread {
                    if (!result.ok) {
                        // লোড ব্যর্থ — Save কখনো চালু হবে না, শুধু Retry দেখাবে
                        saveBtn.text = "⟳ Retry"; saveBtn.isEnabled = true; saveBtn.alpha = 1f
                        saveBtn.setOnClickListener { loadProfile() }
                        ModuleUi.toast(this, "Could not load profile — tap Retry")
                        return@runOnUiThread
                    }
                    if (result.rows.length() > 0) {
                        val p = result.rows.getJSONObject(0)
                        name.setText(ns(p, "full_name")); branch.setText(ns(p, "branch"))
                        designation.setText(ns(p, "designation"))
                        join.setText(ns(p, "join_date")); dob.setText(ns(p, "dob"))
                        gender.setText(ns(p, "gender")); bloodGroup.setText(ns(p, "blood_group"))
                        qualification.setText(ns(p, "qualification"))
                        addr.setText(ns(p, "address")); altMobile.setText(ns(p, "alt_mobile"))
                        emg.setText(ns(p, "emergency_contact")); emgRel.setText(ns(p, "emergency_relationship"))
                        idType.setText(ns(p, "gov_id_type")); notes.setText(ns(p, "notes"))
                        val cloudPhoto = ns(p, "photo_data")
                        // পুরনো আলাদা Staff Photos-এ এই ফোনে রাখা ছবি থাকলে তা হারাবে না:
                        // cloud profile photo না থাকলেই শুধু mobile-keyed পুরনো ছবি fallback।
                        val oldLocalPhoto = UserPhotoStore.get(this, ns(p, "link_mobile")) ?: ""
                        val ph = cloudPhoto.ifBlank { oldLocalPhoto }
                        if (ph.isNotBlank()) {
                            currentPhotoDataUrl = ph
                            photoPreview?.setImageBitmap(PhotoUtils.decodeDataUrl(ph))
                        }
                    }
                    // result.ok == true মানে সার্ভার সত্যিই উত্তর দিয়েছে — সারি
                    // থাকুক (বিদ্যমান প্রোফাইল) বা না থাকুক (নতুন/প্রথমবার), দুটোই
                    // বৈধ, নিরাপদে Save চালু করা যায়।
                    saveBtn.setOnClickListener { doSave() }
                    paintSaveReady()
                }
            }.start()
        }
        loadProfile()
    }

    // ফ্ল্যাট আইকন-সারি: আইকন-বাক্স + লেবেল/মান + স্থায়ী 🔒 (ফিল্ড সবসময় লক
    // দিয়ে শুরু হয় বলে আইকনও স্থির — B304-এর lockField()-এর সাথে মেলানো)।
    // 🔴🆕🔒 TK-নির্দেশ (08.08.2026, ফটো-প্রুফে লক) — View পর্দা এক স্ক্রিনে সব
    // দেখাতে কম্প্যাক্ট করা হলো: প্রতিটা সারি ছোট (কম প্যাডিং), 🔒 তালা-আইকন বাদ
    // ("যেকোনো তথ্য বদলাতে ৩ বার চাপ" ধরনের নির্দেশ-লেখা TK চাননি; ৩-ট্যাপ এডিট
    // আগের মতোই lockField()-এ বহাল)। ⛔ EditText/এডিট/সেভ লজিক একটুও বদলায়নি।
    private fun fieldRow(icon: String, label: String, field: android.widget.EditText): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(7), dp(6), dp(7))
        }
        val iconBox = TextView(this).apply {
            text = icon; textSize = 13f; gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat(); setColor(android.graphics.Color.parseColor("#EAF6EE"))
            }
        }
        val col2 = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(9); marginEnd = dp(2) }
        }
        col2.addView(TextView(this).apply {
            text = label; textSize = 9.5f; setTextColor(android.graphics.Color.parseColor("#8A9A90"))
        })
        field.apply {
            textSize = 13.5f
            setTextColor(android.graphics.Color.parseColor("#1C2B22"))
            background = null
            setPadding(0, dp(1), 0, 0)
            minHeight = 0
        }
        col2.addView(field)
        row.addView(iconBox); row.addView(col2)
        return row
    }

    private fun nowIso(): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date())
    }

    // ---------- MASTER: salary ----------
    // 🔴🆕🔒 TK-নির্দেশ (08.08.2026, ধাপে ধাপে ফটো-প্রুফে লক) — Salary পর্দা সহজ করা:
    // উপরে মাসিক বেতন (৩-ট্যাপে এডিট), তারপর "কোন মাস পর্যন্ত দেওয়া / এই মাসে বাকি",
    // "এই মাসের বেতন দিন" বোতাম, সাম্প্রতিক পেমেন্ট ও "See all payments"। মাসের নাম
    // ইংরেজিতে; কোনো নির্দেশ-লেখা নয়।
    // ⛔ টাকা সেভ/রেকর্ডের আসল কল আগেরটাই — salary_config upsert · salary_payments
    //    insert (হুবহু), শুধু পেমেন্টে নতুন `for_month` ট্যাগ যোগ। নতুন যা দেখানো হয়
    //    (paid up to / this-month due) তা শুধু **পড়ার হিসাব** — ভুল হলেও টাকা/রেকর্ড ভাঙে না।
    // হিসাব (আন্দাজ নয়): প্রতিটা পেমেন্ট "কোন মাসের" = `for_month` থাকলে সেটা, নইলে
    //    `paid_on`-এর মাস (তাই পুরনো ট্যাগ-বিহীন পেমেন্টও ধরা পড়ে, TK: লাস্ট মাস পর্যন্ত
    //    সবার দেওয়া আছে)। "Paid up to" = পেমেন্টগুলোর সবচেয়ে সাম্প্রতিক ঐ মাস।
    private fun salary(code: String) {
        backAction = { renderList() }
        val col = ModuleUi.screen(this, "Salary — $code")
        val box = ModuleUi.card(this)
        col.addView(box)
        /* 🔵🔒 V521 — জমানো তথ্য থাকলে **সঙ্গে সঙ্গে** পর্দা; "Loading..." নয়।
           ⛔ প্রথমবার (বা ১০ মিনিটের পুরনো হলে) আগের মতোই "Loading..."। */
        val cached = salaryCacheLoad(code)
        if (cached != null) {
            try {
                val cfg = cached.optJSONObject("cfg") ?: JSONObject()
                renderSalary(code, box,
                    cfg.optBoolean("salary_enabled", false),
                    cfg.optDouble("salary_amount", 0.0),
                    ns(cfg, "salary_date"),
                    cached.optJSONArray("pays") ?: JSONArray(),
                    cached.optString("joinDate", ""))
            } catch (_: Throwable) {
                box.removeAllViews()
                box.addView(ModuleUi.body(this, "Loading..."))
            }
        } else {
            box.addView(ModuleUi.body(this, "Loading..."))
        }
        // 🔵 V416 (TK-নির্দেশ, ১৭.০৮.২০২৬): "Back বটম নিচে বসবে"।
        //    পর্দাটা ScrollView-এর ভিতরে, তাই শুধু ওজন দিলে হত না — `fillViewport`
        //    চালু করে দিলে ভিতরের কলাম অন্তত পর্দার সমান উঁচু হয়, তখন ওজনওয়ালা
        //    ফাঁকা জায়গাটা Back-কে একদম নিচে ঠেলে দেয়।
        //    ⛔ শুধু এই পর্দায় — `ModuleUi.screen()` (সব পর্দার শেয়ার করা) ছোঁয়া হয়নি।
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { renderList() })
        loadSalary(code, box, cached != null)
    }

    /** 🔵 V416 (TK-নির্দেশ): তারিখ সবসময় 31/12/2026 ধাঁচে।
     *  ⛔ ডেটাবেসে তারিখ আগের মতোই (YYYY-MM-DD) থাকে — শুধু দেখানোর সময় বদলায়। */
    private fun dmy(iso: String): String {
        val t = iso.trim()
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(t) ?: return t
        return m.groupValues[3] + "/" + m.groupValues[2] + "/" + m.groupValues[1]
    }

    /** একটা সারি এখনো "বাকি" না "দেওয়া হয়েছে"। পুরনো সারিতে ঘরটা নেই ⇒ দেওয়া হয়েছে। */
    private fun payStatus(p: JSONObject): String =
        ns(p, "status").trim().uppercase(Locale.US).ifBlank { "PAID" }

    /** একটা সারি বেতন না বাড়তি টাকা। পুরনো সারিতে ঘরটা নেই ⇒ বেতন ধরা হয়। */
    private fun payKind(p: JSONObject): String =
        ns(p, "kind").trim().uppercase(Locale.US).ifBlank { "SALARY" }

    private fun salaryCurrentMonth(): String {
        val c = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        return String.format(Locale.US, "%04d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }
    private fun salaryMonthLabel(ym: String): String = try {
        val p = ym.split("-"); val m = p[1].toInt()
        arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")[m - 1] + " " + p[0]
    } catch (_: Throwable) { ym }
    /** একটা পেমেন্ট কোন মাসের বেতন: `for_month` থাকলে সেটা, নইলে `paid_on`-এর মাস। */
    private fun salaryPayMonth(p: JSONObject): String {
        val fm = ns(p, "for_month")
        if (fm.isNotBlank()) return fm
        val paid = ns(p, "paid_on")
        return if (paid.length >= 7) paid.substring(0, 7) else salaryCurrentMonth()
    }

    /** 🔵🔒 V418 (TK-অনুমোদিত নিয়ম, ১৭.০৮.২০২৬) — Extra Income আপনা থেকে।
     *  Master স্যালারি পর্দা খুললেই একবার হিসাবটা মিলিয়ে নেওয়া হয়:
     *    • Unexpected Time-এ রেজিস্ট্রেশন + Fee জমা ⇒ ₹১০০ (৫০-৫০ ভাগ)
     *    • ওই রোগীর প্রথম Advance জমা ⇒ আরও ₹৪০০ ⇒ মোট ₹৫০০
     *  ⛔ পুরো হিসাবটা ডেটাবেসের ভিতরে (`hr.incentive_sync`) — অ্যাপ শুধু ডাক দেয়,
     *     তাই ফোন আর ওয়েবে নিয়ম আলাদা হয়ে যাওয়ার সুযোগ নেই।
     *  ⛔ রোগী/এনকোয়ারি/পেমেন্ট — একটাও টেবিলে লেখা হয় না, শুধু পড়া হয়।
     *  ⛔ একবার "দেওয়া হয়েছে" হয়ে গেলে সেই সারি আর কখনো বদলায় না।
     *  ⛔ ডাকটা ব্যর্থ হলেও পর্দা আগের মতোই খোলে — কিছুই ভাঙে না। */
    private fun incentiveSync() {
        try {
            if (!ModuleAuth.isMaster) return
            ModuleAuth.rpc("hr", "incentive_sync", JSONObject())
        } catch (_: Throwable) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    // 🔴🔴🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — **"একই স্টাফের বারবার কেন লোডিং
    //    দেখাবে? যতবারই ওপেন করি ততবারই লোডিং কেন দেখাবে?"**
    //
    // **আসল কারণ (কোডে যাচাই করা, আন্দাজ নয়):** `loadSalary()` প্রতিবার
    // পর্দা খোলার সময় **চারটে** নেট-কাজ করত —
    //   ১. `incentive_sync` RPC (শুধু Master)
    //   ২. `salary_config` পড়া
    //   ৩. `salary_payments` পড়া
    //   ৪. `staff_profiles` পড়া
    // আর **কোথাও কিছু জমা রাখা হত না**। তাই একই স্টাফ দশবার খুললে দশবারই
    // "Loading..." আর দশবারই চারটে করে নেট-কাজ।
    //
    // **সমাধান — cache-first**, ঠিক যে পদ্ধতি এই প্রজেক্টে আগেই পাশ হয়েছে
    // (`TimelineCache`, V216 §10 — "Report খুললে আলাদা Loading Screen"):
    //   • আগে দেখা তথ্য থাকলে **সঙ্গে সঙ্গে** পর্দা আঁকা হয় — "Loading..." নয়।
    //   • পিছনে আসল পড়া চলে; নতুন তথ্য এলে পর্দা নিজে থেকেই হালনাগাদ হয়।
    //   • জমানো তথ্য না থাকলে (প্রথমবার) আগের মতোই "Loading..."।
    //
    // ⛔ **টাকার হিসাব এক পয়সাও বদলায়নি** — জমানো তথ্য শুধু **দেখানোর** জন্য;
    //    Add/Cancel/সেভ সব আগের মতোই আসল পড়ার উপরেই চলে।
    // ⛔ **বাসি তথ্য দেখানোর ভয় নেই**: জমানো তথ্য সর্বোচ্চ ১০ মিনিট পুরনো
    //    হতে পারে, আর আসল পড়া শেষ হলেই সেটা মুছে গিয়ে নতুনটা বসে।
    // ⛔ পড়া **ব্যর্থ** হলে জমানো তথ্যই থাকে (আগে পুরো পর্দা ফাঁকা হয়ে
    //    "weak internet" দেখাত) — TK-র কাজ থামে না।
    // ⛔ Supabase-এ **বাড়তি একটাও query নেই** — বরং কম, কারণ `incentive_sync`
    //    RPC এখন প্রতি ৫ মিনিটে একবারের বেশি চলে না।
    // ══════════════════════════════════════════════════════════════════════
    private fun salaryCachePrefs() =
        getSharedPreferences("piles_clinic_salary_cache_v1", MODE_PRIVATE)

    private fun salaryCacheSave(code: String, cfg: JSONObject, pays: JSONArray, joinDate: String) {
        try {
            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("cfg", cfg)
                .put("pays", pays)
                .put("joinDate", joinDate)
            salaryCachePrefs().edit().putString("sal_$code", root.toString()).apply()
        } catch (_: Throwable) { /* জমানো শুধু সুবিধার জন্য — ব্যর্থ হলে কিছুই যায় আসে না */ }
    }

    /** জমানো তথ্য, নইলে null। ১০ মিনিটের পুরনো হলে ব্যবহার করা হয় না। */
    private fun salaryCacheLoad(code: String): JSONObject? = try {
        val raw = salaryCachePrefs().getString("sal_$code", null)
        val root = if (raw.isNullOrBlank()) null else JSONObject(raw)
        val savedAt = root?.optLong("savedAt", 0L) ?: 0L
        if (root == null || savedAt <= 0L ||
            System.currentTimeMillis() - savedAt > 10L * 60L * 1000L) null else root
    } catch (_: Throwable) { null }

    /** 🔵🔒 V521: এই স্টাফের কিছু লেখা হলো (বেতন যোগ / সেটিং বদল / Extra) —
     *  জমানো তথ্য এখনই মুছে ফেলা হয়, নইলে পরেরবার পর্দা খুললে **পুরনো**
     *  তথ্য দেখিয়ে দিত আর TK ভাবতেন কাজটা হয়নি।
     *  ⛔ মুছে দিলে পরের বার শুধু একবার "Loading..." দেখায় — কিন্তু তথ্য
     *     সবসময় ঠিক থাকে। সঠিকতা আগে, গতি তার পরে। */
    private fun salaryCacheClear(code: String) {
        try { salaryCachePrefs().edit().remove("sal_$code").apply() } catch (_: Throwable) {}
    }

    /** `incentive_sync` একটা **লেখার** RPC — প্রতিবার পর্দা খুললে চালানোর
     *  দরকার নেই। ৫ মিনিটে একবারই যথেষ্ট (পাওনা টাকা এর মধ্যে বদলায় না)।
     *  ⛔ টাকার নিয়ম বদলায়নি — শুধু কত ঘন ঘন হিসাব মেলানো হয়। */
    private fun incentiveSyncThrottled() {
        try {
            if (!ModuleAuth.isMaster) return
            val prefs = salaryCachePrefs()
            val last = prefs.getLong("incSyncAt", 0L)
            val now = System.currentTimeMillis()
            if (now - last < 5L * 60L * 1000L) return
            prefs.edit().putLong("incSyncAt", now).apply()
            // ⛔ RPC-টা আগের ফাংশনই চালায় — টাকার হিসাব মেলানোর কোড একটাই জায়গায়।
            incentiveSync()
        } catch (_: Throwable) {}
    }

    private fun loadSalary(code: String, box: LinearLayout, hadCache: Boolean = false) {
        Thread {
            incentiveSyncThrottled()
            val cfgR = ModuleAuth.getRowsChecked("hr", "salary_config", "select=*&person_code=eq.$code&limit=1")
            val payR = ModuleAuth.getRowsChecked("hr", "salary_payments", "select=*&person_code=eq.$code&order=paid_on.desc")
            // 🟢 B629: "মাস বেছে স্যালারি যোগ"-এর মাস-তালিকা জয়েনিং ডেট থেকে শুরু হবে।
            //   পড়া ব্যর্থ হলে join_date খালি থাকবে (ফলে ২৪ মাসের ফলব্যাক) — মূল Salary
            //   লোডিং কখনো এর জন্য আটকায় না।
            // 🔴🔒 V496 (TK §৩): একই পড়াতেই `role_kind`-ও আনা হচ্ছে (বাড়তি
            //    cloud-read নয়) — ডাক্তার/ফিল্ড-এর বেতনের পর্দা খুলবেই না।
            val profR = ModuleAuth.getRowsChecked("hr", "staff_profiles", "select=join_date,role_kind&person_code=eq.$code&limit=1")
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                /* 🔴🔒 V496 (TK §৩) — শেষ পাহারা। তালিকায় আগে থেকেই শুধু staff
                   দেখানো হয়, তবু অন্য কোনো পথ (যেমন "Salary Due" reminder-এর
                   `salaryFor`) দিয়ে ডাক্তার/ফিল্ড-এর কোড এলে এখানেই থেমে যাবে।
                   ⛔ পড়া ব্যর্থ হলে আটকানো হয় না (নইলে দুর্বল ইন্টারনেটে
                      আসল staff-ও বেতন দেখতে পেতেন না) — তখন উপরের
                      "weak internet" বার্তাই দেখা যাবে। */
                if (profR.ok && profR.rows.length() > 0 &&
                    !com.tkbiswas.pilesclinic.native.RoleRules.salaryAppliesToRoleKind(
                        ns(profR.rows.getJSONObject(0), "role_kind"))) {
                    box.removeAllViews()
                    box.addView(ModuleUi.body(this, com.tkbiswas.pilesclinic.native.NoBengali.s(
                        com.tkbiswas.pilesclinic.native.RoleRules.NO_SALARY_MSG)))
                    return@runOnUiThread
                }
                if (!cfgR.ok || !payR.ok) {
                    /* 🔵🔒 V521: আগে পর্দায় কিছু দেখানো থাকলে সেটা **মোছা হবে না** —
                       নইলে জমানো ঠিক তথ্যটা মুছে গিয়ে শুধু ভুলের বার্তা থাকত।
                       ⛔ কিছুই দেখানো না থাকলে আগের মতোই বার্তাটাই দেখায়। */
                    if (hadCache) {
                        ModuleUi.toast(this, "Could not refresh now — showing last known.")
                        return@runOnUiThread
                    }
                    box.removeAllViews()
                    box.addView(ModuleUi.body(this, "⚠️ Could not load now — weak internet. Data is safe; open again when online."))
                    return@runOnUiThread
                }
                val cfg = if (cfgR.rows.length() > 0) cfgR.rows.getJSONObject(0) else JSONObject()
                val joinDate = if (profR.ok && profR.rows.length() > 0) ns(profR.rows.getJSONObject(0), "join_date") else ""
                // 🔵 V521: পরেরবার যেন সঙ্গে সঙ্গে দেখানো যায়
                salaryCacheSave(code, cfg, payR.rows, joinDate)
                renderSalary(code, box,
                    cfg.optBoolean("salary_enabled", false),
                    cfg.optDouble("salary_amount", 0.0),
                    ns(cfg, "salary_date"),
                    payR.rows, joinDate)
            }
        }.start()
    }

    private fun renderSalary(code: String, box: LinearLayout, enabled: Boolean, amount: Double, salaryDate: String, pays: JSONArray, joinDate: String) {
        box.removeAllViews()
        val cur = salaryCurrentMonth()
        var latestMonth = ""
        var paidThisMonth = 0.0
        var extraTotal = 0.0
        var extraDue = 0.0
        var salaryTotal = 0.0
        for (i in 0 until pays.length()) {
            val p = pays.getJSONObject(i)
            // 🔵 V416: বাড়তি টাকা (EXTRA) বেতনের হিসাবে ধরা হয় না — নইলে
            //    "এই মাসে বাকি কত" ভুল দেখাত। TK-কে আগেই জানানো হয়েছিল।
            if (payKind(p) == "EXTRA") {
                // 🔵 V417: "বাকি" (DUE) টাকা এখনো দেওয়া হয়নি — তাই "দেওয়া হয়েছে"-তে ধরা যাবে না।
                if (payStatus(p) == "DUE") extraDue += p.optDouble("amount", 0.0)
                else extraTotal += p.optDouble("amount", 0.0)
                continue
            }
            // ⛔ বেতনের সারি সবসময় দেওয়া-হয়েছে ধরা হয় (DUE শুধু Extra-তেই ব্যবহার হয়)।
            salaryTotal += p.optDouble("amount", 0.0)
            val mth = salaryPayMonth(p)
            if (mth > latestMonth) latestMonth = mth
            if (mth == cur) paidThisMonth += p.optDouble("amount", 0.0)
        }
        val active = enabled && amount > 0
        val due = if (active) maxOf(0.0, amount - paidThisMonth) else 0.0

        /* 🎨 V417গ (TK-অনুমোদিত "মডেল ৩", ১৭.০৮.২০২৬) — *"৩ নম্বর বসান · সব ইংরেজিতে
           হবে বাংলা লেখা থাকবে না"*। পর্দাটা এখন তিনটে বাক্সে:
             বাক্স ১ = Salary          (মাসিক · এই মাস · মোট দেওয়া · দুটো বোতাম)
             বাক্স ২ = Extra Income    (দেওয়া · বাকি · দুটো বোতাম)
             বাক্স ৩ = Salary Settings (একটাই বোতাম)
           ⛔ একটাও হিসাব বদলায়নি — উপরের গণনা হুবহু আগেরটাই।
           ⛔ পর্দায় বাংলা লেখা নেই, নির্দেশ/সাহায্য-লাইনও নেই (TK-এর স্থায়ী নিয়ম)।
           ⛔ ওয়েবেও হুবহু একই সাজ। */

        // ───────── বাক্স ১ · Salary ─────────
        box.addView(salSectionTitle("Salary", "#0A5C33"))
        if (active) {
            box.addView(salaryStatusRow("Monthly", money(amount) + (if (salaryDate.isNotBlank()) " · day $salaryDate" else ""), "#0A5C33"))
            box.addView(salaryStatusRow(salaryMonthLabel(cur),
                if (due <= 0.0) "Paid" else "Due " + money(due),
                if (due <= 0.0) "#0A7C3F" else "#B42318"))
            box.addView(salaryStatusRow("Paid up to", if (latestMonth.isNotBlank()) salaryMonthLabel(latestMonth) else "—", "#0A7C3F"))
        } else {
            box.addView(salaryStatusRow("Monthly", "Not set", "#B42318"))
        }
        box.addView(salaryStatusRow("Total paid", money(salaryTotal), "#123A26"))
        box.addView(salaryStatusRow("Joining date", if (joinDate.isBlank()) "Not recorded" else dmy(joinDate), "#5B6B81"))

        /* 🔵 V417খ (TK-নির্দেশ): *"Add Salary & Payment History এক লাইনে থাকবে পাশাপাশি
           বক্স"* ⇒ দুটো সমান বাক্স এক লাইনে।
           ⛔ স্যালারি চালু না থাকলে "Add Salary" আসে না — তখন "Payment History"
              নিজেই পুরো লাইন নেয়, ফাঁকা বাক্স বসে না।
           🟢 B629: যেকোনো (পুরনো) মাসের স্যালারি যোগ — Master নিজে জয়েনিং ডেট থেকে
              History ভরতে পারবেন।
           🔵 V417: Payment History নিজে থেকে খোলা থাকে না — চাপলে Statement খোলে। */
        val btnAddSalary = if (active) salOutlineButton("Add Salary", "#0A5C33", "#0A5C33") {
            addSalaryAnyMonth(code, amount, joinDate, pays)
        } else null
        val btnHistory = salOutlineButton("Payment History (" + pays.length() + ")", "#0A5C33", "#0A5C33") {
            showAllPayments(code, pays)
        }
        box.addView(if (btnAddSalary != null) salPairRow(btnAddSalary, btnHistory) else salPairRow(btnHistory, null))
        if (active && due > 0.0) {
            box.addView(ModuleUi.button(this, "Pay " + salaryMonthLabel(cur) + " Salary (" + money(due) + ")") {
                payForMonth(code, cur, due)
            })
        }

        // ───────── বাক্স ২ ও ৩ · আলাদা সাদা কার্ডে (মডেল ৩) ─────────
        // ⛔ আগের কার্ডগুলো tag দিয়ে চিনে সরিয়ে তবেই নতুন বসে ⇒ বারবার আঁকলেও
        //    কার্ড জমতে থাকে না। col না পেলে সবটা এই বাক্সেই বসে (কিছু হারায় না)।
        val col = box.parent as? LinearLayout
        val extraBox: LinearLayout
        val cfgBox: LinearLayout?
        if (col != null) {
            col.findViewWithTag<android.view.View>(SAL_TAG_EXTRA)?.let { col.removeView(it) }
            col.findViewWithTag<android.view.View>(SAL_TAG_CFG)?.let { col.removeView(it) }
            extraBox = ModuleUi.card(this).apply { tag = SAL_TAG_EXTRA }
            col.addView(extraBox, col.indexOfChild(box) + 1)
            cfgBox = ModuleUi.card(this).apply { tag = SAL_TAG_CFG }
            col.addView(cfgBox, col.indexOfChild(extraBox) + 1)
        } else {
            extraBox = box
            cfgBox = null
        }

        // 🔵 V416: বেতন ছাড়াও বাড়তি টাকা। ⛔ `kind='EXTRA'` হয়ে জমা হয়, তাই বেতনের
        //    বাকি-হিসাব ছোঁয় না। 🔵 V417: এখনো না-দেওয়া টাকা লাল "Due" হয়ে থাকে।
        extraBox.addView(salSectionTitle("Extra Income", "#B45309"))
        extraBox.addView(salaryStatusRow("Paid", money(extraTotal), "#123A26"))
        extraBox.addView(salaryStatusRow("Due", money(extraDue), if (extraDue > 0.0) "#B42318" else "#5B6B81"))
        val btnAddExtra = salOutlineButton("Add Extra", "#B45309", "#E0A800") { addExtraIncome(code) }
        val btnPayExtra = if (extraDue > 0.0) salOutlineButton("Pay " + money(extraDue), "#0A5C33", "#0A5C33") {
            payExtraDue(code, pays)
        } else null
        extraBox.addView(salPairRow(btnAddExtra, btnPayExtra))

        (cfgBox ?: box).addView(salOutlineButton("Salary Settings", "#0A5C33", "#0A5C33") {
            editSalaryConfig(code, enabled, amount, salaryDate)
        })
    }

    /* =====================================================================
       🏆🔒 V419 — STAFF PERFORMANCE (TK-নির্দেশ, ১৭.০৮.২০২৬)
       TK চারটেই দেখতে চেয়েছেন — রোগী আনার কাজ · ফলোআপ ও কল · টাকা আদায় ·
       হাজিরা ও রিপোর্ট। পর্দাও "দুটোই" — সবার তালিকা, আর একজনের পুরো হিসাব।
       ⛔ পুরো গণনা ডেটাবেসের ভিতরে (`hr.staff_performance`) ⇒ ফোন ও ওয়েবে
          সংখ্যা আলাদা হওয়ার সুযোগ নেই, আর এক ডাকে ছোট্ট উত্তর আসে।
       ⛔ একটাও সারি লেখা/বদলানো হয় না — কেবল পড়া। ⛔ শুধু Master।
       ⛔ পর্দায় বাংলা লেখা নেই, নির্দেশ/সাহায্য-লাইনও নেই। */
    private fun perfMonthNow(): String {
        val c = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        return String.format(Locale.US, "%04d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
    }

    /* 🔵 V420 (TK-নির্দেশ: *"daily performance দেখার ব্যবস্থা রাখতে হবে"*) —
       একই পর্দায় দুটোই: `2026-08` = গোটা মাস · `2026-08-17` = শুধু ওই দিন।
       ⛔ ডেটাবেসের একই ফাংশন, একই সংখ্যা-নিয়ম — শুধু কতটুকু সময় সেটা বদলায়। */
    private fun perfIsDay(k: String): Boolean = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(k.trim())

    private fun perfDayNow(): String = todayIso()

    /** দেখানোর জন্য: দিন হলে 17/08/2026, মাস হলে August 2026। */
    private fun perfLabel(k: String): String =
        if (perfIsDay(k)) dmy(k) else salaryMonthLabel(k)

    /** এক ডাকে সবার হিসাব। ব্যর্থ হলে `null` — তখন পর্দা সৎ বার্তা দেখায়।
     *  🔴 TK-নির্দেশ (১৭.০৮.২০২৬): *"ডাক্তারদের বাদ দিয়ে দিন"* — এই তালিকা শুধু
     *  কর্মীদের। ডেটাবেসেও একই ছাঁকনি বসানো আছে; এটা দ্বিতীয় স্তর, যাতে পুরনো
     *  ডেটাবেসেও (নতুন SQL না চালালেও) ডাক্তার আর তালিকায় না ওঠেন। */
    private fun perfFetch(month: String): JSONArray? {
        return try {
            val r = ModuleAuth.rpc("hr", "staff_performance", JSONObject().put("p_month", month))
            if (!r.ok) return null
            val all = JSONArray(r.body)
            val out = JSONArray()
            for (i in 0 until all.length()) {
                val x = all.optJSONObject(i) ?: continue
                if (ns(x, "person_code").uppercase(Locale.US).startsWith("DR-")) continue
                out.put(x)
            }
            /* 🔴🔒 V428 (TK-নির্দেশ ১৭.০৮.২০২৬: *"8514002200 — ওটা ব্রাঞ্চ হিসাবেই
               গন্য হোক"*) — চেম্বারের সাধারণ নম্বরে করা কাজ কোনো staff-এর নামে ওঠে
               না, তাই Collection কম দেখাত (আজ কোচবিহারে ₹৭,৫০০ বাদ পড়ছিল)। এখন
               সেগুলো **ব্রাঞ্চের নিজের সারি** হয়ে তালিকার শেষে আসে, ফলে যোগফল মেলে।
               ⛔ পুরনো `staff_performance`-এ হাত দেওয়া হয়নি — এটা আলাদা ফাংশন।
               ⛔ ডাক ব্যর্থ হলে বা সব শূন্য হলে কিছুই যোগ হয় না, তালিকা আগের মতোই। */
            try {
                val rb = ModuleAuth.rpc("hr", "branch_performance", JSONObject().put("p_month", month))
                if (rb.ok) {
                    val bAll = JSONArray(rb.body)
                    for (i in 0 until bAll.length()) {
                        val b = bAll.optJSONObject(i) ?: continue
                        val busy = perfInt(b, "enquiry_count") + perfInt(b, "registration_count") +
                            perfInt(b, "treatment_count") + perfInt(b, "rmp_added")
                        val money = perfDbl(b, "cash_collected") + perfDbl(b, "online_collected")
                        if (busy > 0 || money > 0.0) out.put(b)
                    }
                }
            } catch (_: Throwable) { }
            out
        } catch (_: Throwable) { null }
    }

    private fun perfInt(o: JSONObject, k: String): Int = try { o.optInt(k, 0) } catch (_: Throwable) { 0 }
    private fun perfDbl(o: JSONObject, k: String): Double = try { o.optDouble(k, 0.0) } catch (_: Throwable) { 0.0 }

    /* 🔧 V421খ (TK-নির্দেশ, ১৭.০৮.২০২৬): *"Day month আবার ক্যালেন্ডার — তিনটে
       রাখার দরকার নেই · Month & calendar থাকবে · ক্যালেন্ডারে চাপ দিলে pop up
       ক্যালেন্ডার খুলবে · তারিখ পছন্দ করলে অটোমেটিক সেই তারিখের পারফরম্যান্স ·
       অন্যথায় ডিফল্ট আজকের · আর এগুলো হেডারে থাকবে"*
       ⇒ শিরোনামের **একই লাইনে** দুটোই — [Month] আর তারিখের বাক্স। তারিখে চাপলে
         ফোনের নিজের পপ-আপ ক্যালেন্ডার (DatePickerDialog) খোলে, তারিখ বাছলেই
         সঙ্গে সঙ্গে ওই দিনের হিসাব।
       ⛔ ক্যালেন্ডার-ইমোজি কোথাও নেই (TK-এর স্থায়ী নিয়ম)। ⛔ ডিফল্ট = আজকের দিন। */
    private fun perfHeader(title: String, curKey: String, onPick: (String) -> Unit): LinearLayout {
        val isDay = perfIsDay(curKey)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(android.graphics.Color.parseColor("#0B4F2A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fun chip(text: String, on: Boolean, onClick: () -> Unit) = TextView(this).apply {
            this.text = text; textSize = 12.5f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(11), dp(7), dp(11), dp(7))
            setTextColor(android.graphics.Color.parseColor(if (on) "#FFFFFF" else "#0A5C33"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(android.graphics.Color.parseColor(if (on) "#0A5C33" else "#FFFFFF"))
                setStroke(dp(1), android.graphics.Color.parseColor("#0A5C33"))
            }
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        }
        row.addView(chip("Month", !isDay) { onPick(perfMonthNow()) })
        row.addView(chip(perfLabel(curKey), isDay) {
            // ফোনের নিজের পপ-আপ ক্যালেন্ডার — অ্যাপে আগে থেকেই এই প্রমাণিত পথ ব্যবহার হয়।
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
            try {
                val d = if (isDay) curKey else todayIso()
                val q = d.split("-")
                cal.set(q[0].toInt(), q[1].toInt() - 1, q[2].toInt())
            } catch (_: Throwable) {}
            android.app.DatePickerDialog(this, { _, y, mth, day ->
                onPick(String.format(Locale.US, "%04d-%02d-%02d", y, mth + 1, day))
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
               cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        })
        return row
    }

    /** ---- ১) সবার তালিকা ---- */
    private fun performanceList(month: String) {
        backAction = { renderList() }
        // 🔧 V421খ: ডিফল্ট **আজকের দিন** (আগে মাস ছিল)।
        val ym = month.ifBlank { perfDayNow() }
        val col = ModuleUi.screen(this, "")
        col.addView(perfHeader("Performance", ym) { k -> performanceList(k) })
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listBox.addView(ModuleUi.body(this, "Loading..."))
        col.addView(listBox)
        col.addView(ModuleUi.button(this, "Back") { renderList() })
        Thread {
            val rows = perfFetch(ym)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                listBox.removeAllViews()
                if (rows == null) { listBox.addView(ModuleUi.body(this, "Could not load. Please try again.")); return@runOnUiThread }
                if (rows.length() == 0) { listBox.addView(ModuleUi.body(this, "No staff yet.")); return@runOnUiThread }
                // 🔴 V427 (TK-নির্দেশ ১৭.০৮.২০২৬: *"কোচবিহার এর সমস্ত staff &
                //    Branch এর নম্বর একের পর এক থাকতে হবে"*) — তালিকা এখন
                //    **ব্রাঞ্চ ধরে সাজানো**, এক ব্রাঞ্চের সবাই পরপর; ভিতরে নাম
                //    অনুসারে। ⛔ শুধু ক্রম — একটাও সংখ্যা বদলায় না। ওয়েবেও একই ক্রম।
                val perfSorted = ArrayList<org.json.JSONObject>(rows.length())
                for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { perfSorted.add(it) }
                // ক্রম: আগে ব্রাঞ্চ, ভিতরে staff-রা নাম অনুসারে, আর ওই ব্রাঞ্চের
                // নিজের সারিটা সবার **শেষে** — ওয়েবেও হুবহু একই ক্রম।
                perfSorted.sortWith(compareBy(
                    { ns(it, "branch").uppercase() },
                    { if (ns(it, "person_code").uppercase(Locale.US).startsWith("BRANCH-")) 1 else 0 },
                    { ns(it, "full_name").ifBlank { ns(it, "person_code") }.uppercase() }
                ))
                for (i in perfSorted.indices) {
                    val x = perfSorted[i]
                    val card = ModuleUi.card(this)
                    card.addView(TextView(this).apply {
                        text = ns(x, "full_name").ifBlank { ns(x, "person_code") }
                        textSize = 15f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#0A5C33"))
                    })
                    card.addView(TextView(this).apply {
                        text = ns(x, "person_code") + " · " + ns(x, "branch")
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                        setPadding(0, dp(2), 0, dp(2))
                    })
                    val tiles = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(7), 0, dp(2))
                    }
                    tiles.addView(perfTile("Enquiry", perfInt(x, "enquiry_count").toString(), false))
                    tiles.addView(perfTile("Regist.", perfInt(x, "registration_count").toString(), false))
                    tiles.addView(perfTile("Treat.", perfInt(x, "treatment_count").toString(), false))
                    tiles.addView(perfTile("Collected",
                        money(perfDbl(x, "cash_collected") + perfDbl(x, "online_collected")), true))
                    card.addView(tiles)
                    val pc = ns(x, "person_code")
                    card.isClickable = true
                    card.setOnClickListener { performanceOne(pc, ym) }
                    listBox.addView(card)
                }
            }
        }.start()
    }

    /** তালিকার ছোট বাক্স — Salary পর্দার টাইলের মতোই। */
    private fun perfTile(caption: String, value: String, last: Boolean): LinearLayout {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#F2FBF5"))
                setStroke(dp(1), android.graphics.Color.parseColor("#D8ECDF"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { if (!last) rightMargin = dp(7) }
        }
        t.addView(TextView(this@StaffProfileActivity).apply {
            text = caption; textSize = 10.5f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
        })
        t.addView(TextView(this@StaffProfileActivity).apply {
            text = value; textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#0A5C33"))
            setPadding(0, dp(2), 0, 0)
        })
        return t
    }

    /** ---- ২) একজনের পুরো হিসাব ---- */
    /* 🔧 V421 (TK-রিপোর্ট: *"staff এর Daily Performance কেন দেখা যাচ্ছে না"*) —
       Day/Month বোতাম দুটো আগে **শুধু তালিকার** পর্দায় ছিল। Staff Profiles থেকে
       সরাসরি কারও Performance-এ ঢুকলে দিনের হিসাব দেখার উপায়ই ছিল না।
       এখন এই পর্দাতেও একই দুটো বোতাম। ⛔ গোনার নিয়ম কিছুই বদলায়নি।
       ⛔ `fromList` দিয়ে মনে রাখা হয় Back কোথায় ফিরবে — তালিকা থেকে এলে
          তালিকায়, Staff Profiles থেকে এলে Staff Profiles-এ। */
    // =====================================================================
    // 🔴🔴🔒 V477 (20.08.2026, TK-জরুরি নির্দেশ) — Master-only: যেকোনো
    // স্টাফের আজকের IN/OUT TIME সরাসরি বসানো/ঠিক করা — দুপুর ১২টার সীমা
    // ছাড়াই। আজ সকালের JWT বাগে (V465-এ ঠিক করা) যাদের IN TIME নিঃশব্দে
    // ক্লাউডে সেভ হয়নি, সন্ধ্যায় তাদের হাজিরা হারিয়ে যাওয়া ঠেকাতে।
    // ⛔ একই টেবিলে (wn.notebook_days), স্টাফের নিজের Work Notebook যে
    //    upsert পথ ব্যবহার করে ঠিক সেই একই প্যাটার্নে — নতুন কোনো নিয়ম নয়।
    // =====================================================================
    private fun fixAttendanceDialog(pc: String, fullName: String, mobile: String) {
        val d = resources.displayMetrics.density
        fun dp2(v: Int) = (v * d).toInt()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(java.util.Date())
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2(20), dp2(10), dp2(20), dp2(4))
        }
        box.addView(TextView(this).apply {
            text = "$fullName ($pc) — $today"
            textSize = 13f; setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, 0, 0, dp2(10))
        })
        box.addView(TextView(this).apply { text = "IN TIME (e.g. 09:15 AM)"; textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#5B6B81")) })
        val inInput = android.widget.EditText(this).apply {
            hint = "hh:mm AM/PM — leave blank if not changing"
            val pad = dp2(11); setPadding(pad, pad, pad, pad)
        }
        box.addView(inInput)
        box.addView(TextView(this).apply { text = "OUT TIME (e.g. 06:30 PM)"; textSize = 11.5f; setTextColor(android.graphics.Color.parseColor("#5B6B81")); setPadding(0, dp2(10), 0, 0) })
        val outInput = android.widget.EditText(this).apply {
            hint = "hh:mm AM/PM — leave blank if not changing"
            val pad = dp2(11); setPadding(pad, pad, pad, pad)
        }
        box.addView(outInput)
        // hh:mm AM/PM → "HH:mm:ss" (24-ঘণ্টা, notebook_days-এ যেভাবে জমা থাকে)।
        fun to24(raw: String): String? {
            val t = raw.trim().uppercase(Locale.US)
            if (t.isBlank()) return null
            return try {
                val fmt = SimpleDateFormat("hh:mm a", Locale.US)
                val parsed = fmt.parse(t) ?: return null
                SimpleDateFormat("HH:mm:ss", Locale.US).format(parsed)
            } catch (_: Throwable) { null }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, "🕐 Fix Attendance"))
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val inTxt = inInput.text.toString().trim()
                val outTxt = outInput.text.toString().trim()
                if (inTxt.isBlank() && outTxt.isBlank()) {
                    android.widget.Toast.makeText(this, "Enter at least one time", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val in24 = if (inTxt.isNotBlank()) to24(inTxt) else null
                val out24 = if (outTxt.isNotBlank()) to24(outTxt) else null
                if ((inTxt.isNotBlank() && in24 == null) || (outTxt.isNotBlank() && out24 == null)) {
                    android.widget.Toast.makeText(this, "Use format hh:mm AM/PM, e.g. 09:15 AM", android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Thread {
                    val existing = try {
                        val r = ModuleAuth.getRowsChecked("wn", "notebook_days", "select=*&staff_code=eq.$pc&work_date=eq.$today&limit=1")
                        if (r.ok && r.rows.length() > 0) r.rows.getJSONObject(0) else null
                    } catch (_: Throwable) { null }
                    val row = existing ?: JSONObject()
                        .put("staff_code", pc).put("staff_mobile", mobile).put("work_date", today)
                        .put("manual_entries", JSONArray())
                    if (!row.has("manual_entries") || row.isNull("manual_entries")) row.put("manual_entries", JSONArray())
                    if (in24 != null) row.put("check_in", in24)
                    if (out24 != null) row.put("check_out", out24)
                    row.put("updated_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
                    var ok = try { ModuleAuth.upsertOnConflict("wn", "notebook_days", row, "staff_code,work_date") } catch (_: Throwable) { false }
                    if (!ok) ok = try { ModuleAuth.upsert("wn", "notebook_days", row) } catch (_: Throwable) { false }
                    runOnUiThread {
                        android.widget.Toast.makeText(this, if (ok) "Attendance updated for $fullName" else "Could not save — check connection and try again", android.widget.Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show().also { try { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) } catch (_: Throwable) {} }
    }

    private fun performanceOne(code: String, month: String, fromList: Boolean = month.isNotBlank()) {
        // 🔧 V421খ: ডিফল্ট **আজকের দিন**।
        val ym = month.ifBlank { perfDayNow() }
        backAction = { if (fromList) performanceList(ym) else renderList() }
        // 🔴 V511 (উপরের `perfListBack`-এর বড় নোট দ্রষ্টব্য): এই পর্দা থেকে যত
        //    তালিকা খোলা হয় (Enquiry · Calls · Collection · Reports · RMP),
        //    সবগুলোর Back-এর ঠিকানা **এখানেই** — ডিটেল থেকে যতবারই ফিরুক।
        perfListBack = { performanceOne(code, ym, fromList) }
        val col = ModuleUi.screen(this, "")
        col.addView(perfHeader(code, ym) { k -> performanceOne(code, k, fromList) })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = ModuleUi.card(this)
        first.addView(ModuleUi.body(this, "Loading..."))
        box.addView(first)
        col.addView(box)
        col.addView(ModuleUi.button(this, "Back") { backAction() })
        Thread {
            val rows = perfFetch(ym)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                box.removeAllViews()
                if (rows == null) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "Could not load. Please try again.")) })
                    return@runOnUiThread
                }
                var x: JSONObject? = null
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    if (ns(r, "person_code") == code) { x = r; break }
                }
                val row = x
                if (row == null) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "No record for this month.")) })
                    return@runOnUiThread
                }
                val head = ModuleUi.card(this)
                head.addView(TextView(this).apply {
                    text = ns(row, "full_name").ifBlank { code }
                    textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#0A5C33"))
                })
                head.addView(TextView(this).apply {
                    text = code + " · " + ns(row, "branch") + " · " + perfLabel(ym)
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    setPadding(0, dp(2), 0, 0)
                })
                box.addView(head)

                val c1 = ModuleUi.card(this)
                c1.addView(salSectionTitle("Patient Work", "#0A5C33"))
                c1.addView(salaryStatusRow("Enquiry forms", perfInt(row, "enquiry_count").toString(), "#123A26") { perfShowEnquiryList(code, ym) })
                c1.addView(salaryStatusRow("Registrations", perfInt(row, "registration_count").toString(), "#123A26") { perfShowRegistrationList(code, ym, treatmentOnly = false) })
                c1.addView(salaryStatusRow("Started treatment", perfInt(row, "treatment_count").toString(), "#0A7C3F") { perfShowRegistrationList(code, ym, treatmentOnly = true) })
                box.addView(c1)

                val c2 = ModuleUi.card(this)
                c2.addView(salSectionTitle("Calls", "#0A5C33"))
                c2.addView(salaryStatusRow("Calls from app", perfInt(row, "app_calls").toString(), "#123A26") { perfShowCallsList(code, ym, outside = false) })
                c2.addView(salaryStatusRow("Outside calls", perfInt(row, "outside_calls").toString(), "#123A26") { perfShowCallsList(code, ym, outside = true) })
                c2.addView(salaryStatusRow("RMP added", perfInt(row, "rmp_added").toString(), "#123A26") { perfShowRmpList(code, ym) })
                box.addView(c2)

                val c3 = ModuleUi.card(this)
                c3.addView(salSectionTitle("Money Collected", "#0A5C33"))
                c3.addView(salaryStatusRow("Cash", money(perfDbl(row, "cash_collected")), "#123A26") { perfShowPaymentList(code, ym, online = false) })
                c3.addView(salaryStatusRow("Online", money(perfDbl(row, "online_collected")), "#123A26") { perfShowPaymentList(code, ym, online = true) })
                c3.addView(salaryStatusRow("Total", money(perfDbl(row, "cash_collected") + perfDbl(row, "online_collected")), "#0A7C3F"))
                box.addView(c3)

                val c4 = ModuleUi.card(this)
                c4.addView(salSectionTitle("Attendance & Reports", "#B45309"))
                c4.addView(salaryStatusRow("Days present", perfInt(row, "present_days").toString(), "#123A26") { perfShowAttendanceSheet(code, ns(row, "full_name").ifBlank { code }, ym, fromPerf = true) })
                c4.addView(salaryStatusRow("Daily reports sent", perfInt(row, "reports_sent").toString(), "#123A26") { perfShowReportsList(code, ym) })
                val lv = perfInt(row, "leave_days")
                c4.addView(salaryStatusRow("Leave days", lv.toString(), if (lv > 0) "#B42318" else "#5B6B81") { perfShowAttendanceSheet(code, ns(row, "full_name").ifBlank { code }, ym, fromPerf = true) })
                box.addView(c4)
            }
        }.start()
    }

    /** মডেল ৩-এর কার্ড-শিরোনাম (Salary / Extra Income)। */
    private fun salSectionTitle(text: String, hex: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.parseColor(hex))
        setPadding(dp(2), dp(2), dp(2), dp(6))
    }

    /** 🔵 V417খ (TK-নির্দেশ): দুটো বোতাম **এক লাইনে পাশাপাশি**, দুটোই সমান চওড়া।
     *  ডান দিকেরটা না থাকলে বাঁ দিকেরটাই পুরো লাইন নেয় — ফাঁকা বাক্স বসে না।
     *  ⛔ বোতামের কাজ · রং · লেখা কিছুই এখানে বদলায় না, শুধু বসার জায়গা। */
    private fun salPairRow(left: android.view.View, right: android.view.View?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        left.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            .apply { if (right != null) rightMargin = dp(9) }
        row.addView(left)
        if (right != null) {
            right.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            row.addView(right)
        }
        return row
    }

    /* 🎨 V416 (TK-অনুমোদিত মডেল ২ + বোতাম মডেল ৩, ১৭.০৮.২০২৬) — শুধু **সাজ**।
       ⛔ কোনো হিসাব · সংখ্যা · সেভ/পড়ার নিয়ম এক অক্ষরও বদলায়নি।
       ⛔ নেভি ব্লু কোথাও নেই (TK-নির্দেশ)। */
    /** 🔵 V417 (TK-অনুমোদিত): ঠিক করে রাখা বাড়তি টাকা এখন দেওয়া হলো।
     *  ⛔ নতুন সারি বানানো হয় না — যে সারিটা "বাকি" ছিল সেটাই "দেওয়া হয়েছে" হয়,
     *     তাই একই টাকা দুবার গোনা হওয়ার সুযোগ নেই।
     *  ⛔ শুধু `status` · `mode` · `paid_on` বদলায়; টাকার অঙ্ক ও কারণ অটুট। */
    private fun payExtraDue(code: String, pays: JSONArray) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "Pay Extra Income")
        val dueRows = ArrayList<JSONObject>()
        var dueSum = 0.0
        for (i in 0 until pays.length()) {
            val p = pays.getJSONObject(i)
            if (payKind(p) == "EXTRA" && payStatus(p) == "DUE") {
                dueRows.add(p); dueSum += p.optDouble("amount", 0.0)
            }
        }
        val box = ModuleUi.card(this)
        col.addView(box)
        box.addView(salaryStatusRow("Total to pay now", money(dueSum), "#B42318"))
        for (p in dueRows) {
            box.addView(ModuleUi.body(this, money(p.optDouble("amount", 0.0)) + "  ·  " + ns(p, "extra_reason")))
        }
        val md = spinner(listOf("Cash", "Online"))
        col.addView(ModuleUi.label(this, "Mode")); col.addView(md)
        col.addView(ModuleUi.button(this, "✅ Mark as Paid") {
            if (dueRows.isEmpty()) { ModuleUi.toast(this, "Nothing due"); return@button }
            val mode = md.selectedItem.toString()
            Thread {
                var okAll = true
                for (p in dueRows) {
                    val id = ns(p, "id")
                    if (id.isBlank()) { okAll = false; continue }
                    val patch = JSONObject().put("status", "PAID").put("mode", mode).put("paid_on", todayIso())
                    if (!ModuleAuth.update("hr", "salary_payments", "id=eq.$id", patch)) okAll = false
                }
                runOnUiThread {
                    ModuleUi.toast(this, if (okAll) "Paid" else "Some entries did not save — try again")
                    salary(code)
                }
            }.start()
        })
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { salary(code) })
    }

    private fun salOutlineButton(text: String, textHex: String, borderHex: String, onClick: () -> Unit): android.widget.Button =
        android.widget.Button(this).apply {
            this.text = text
            isAllCaps = false
            // 🔵 V417খ: এখন দুটো বোতাম পাশাপাশি বসে (অর্ধেক চওড়া), তাই লেখা একটু
            //    ছোট আর দু'লাইনে ভেঙে বসার ব্যবস্থা — নইলে লেখা কেটে যেত।
            //    ⛔ Button-এর নিজের সবচেয়ে-কম চওড়া ০ করা হলো, নইলে weight মানত না।
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), dp(13), dp(8), dp(13))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(textHex))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(android.graphics.Color.WHITE)
                setStroke(dp(2), android.graphics.Color.parseColor(borderHex))
            }
            setOnClickListener { onClick() }
        }

    /** উপরের তিনটে ছোট বাক্স — বেতন · বাড়তি · সব মিলিয়ে (TK-বাছা মডেল ২)। */
    private fun salTotalTiles(paidSalary: Double, extra: Double): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(4))
        }
        fun tile(caption: String, value: String, last: Boolean): LinearLayout {
            val t = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dp(6), dp(9), dp(6), dp(9))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(android.graphics.Color.parseColor("#F2FBF5"))
                    setStroke(dp(1), android.graphics.Color.parseColor("#D8ECDF"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (!last) rightMargin = dp(8) }
            }
            t.addView(TextView(this@StaffProfileActivity).apply {
                text = caption; textSize = 11f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            })
            t.addView(TextView(this@StaffProfileActivity).apply {
                text = value; textSize = 16f
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#0A5C33"))
                setPadding(0, dp(3), 0, 0)
            })
            return t
        }
        row.addView(tile("Salary paid", money(paidSalary), false))
        row.addView(tile("Extra", money(extra), false))
        row.addView(tile("Total", money(paidSalary + extra), true))
        return row
    }

    /* 🔴🔴🔒 V440 (TK-নির্দেশ ১৮.০৮.২০২৬) — `onClick` দিলে সারিটা চাপ-যোগ্য হয়
       (হালকা ripple + ডান পাশে ">") — নাহলে আগের মতোই স্থির। ⛔ পুরনো সব ডাক
       (Salary ইত্যাদি, onClick ছাড়া) হুবহু আগের মতোই দেখাবে/আচরণ করবে। */
    private fun salaryStatusRow(label: String, value: String, valueColor: String, onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 13.5f; setTextColor(android.graphics.Color.parseColor("#3B5A49"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(valueColor))
            gravity = android.view.Gravity.END
        })
        if (onClick != null) {
            row.addView(TextView(this).apply {
                text = "  \u203A"; textSize = 15f
                setTextColor(android.graphics.Color.parseColor("#8AA79A"))
            })
            row.isClickable = true; row.isFocusable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            row.setBackgroundResource(outValue.resourceId)
            row.setOnClickListener { onClick() }
        }
        return row
    }

    /* ── V440: এক-একটা সংখ্যায় চাপ দিলে আসল তালিকা/হাজিরা-খাতা ───────────── */

    /** সাধারণ তালিকা-পর্দা — শিরোনাম + লোডিং + সারিগুলো নিজের মতো করে বসানো
     *  (renderRow), না পেলে সৎ বার্তা। কোনো লেখা/বদল নেই — শুধু দেখা। */
    private fun perfListScreen(
        title: String, code: String, month: String,
        fetch: () -> org.json.JSONArray?,
        emptyMsg: String,
        cachedRows: org.json.JSONArray? = null,
        onRowClick: ((org.json.JSONObject, org.json.JSONArray) -> Unit)? = null,
        renderRow: (LinearLayout, org.json.JSONObject) -> Unit
    ) {
        // 🔴🔴 V511 (২১.০৮.২০২৬, TK-রিপোর্ট — "Calls From App"-এ Back কাজ করে না)।
        //   আগে এখানে `backAction`-এর তখনকার মানটাই ধরে নেওয়া হত, আর ডিটেল
        //   থেকে ফিরলে সেই মানটা হয়ে যেত **"এই তালিকাটাই আবার খোলো"** —
        //   ফলে Back চাপলে একই পর্দা আবার আঁকা হত, কিছুই বদলাত না।
        //   এখন ফেরার ঠিকানা `performanceOne()` আগেই লিখে রাখে (`perfListBack`),
        //   তাই লুপ আর সম্ভব নয়। ⛔ `perfListBack` কোনো কারণে ফাঁকা থাকলে
        //   আগের আচরণই চলে — কিছু ভাঙে না।
        val prevBack = perfListBack ?: backAction
        backAction = { prevBack() }
        val col = ModuleUi.screen(this, title)
        col.addView(TextView(this).apply {
            text = perfLabel(month); textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val loading = ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "Loading...")) }
        box.addView(loading)
        col.addView(box)
        col.addView(ModuleUi.button(this, "Back") { backAction() })
        Thread {
            // 🔴 V452: detail থেকে Back করলে একই already-loaded list আবার দেখাই;
            // Cloud-এ অকারণে দ্বিতীয় read করি না। Fresh open-এ আগের fetch-ই চলে।
            val rows = cachedRows ?: try { fetch() } catch (_: Throwable) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                box.removeAllViews()
                if (rows == null) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "Could not load. Please try again.")) })
                    return@runOnUiThread
                }
                if (rows.length() == 0) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, emptyMsg)) })
                    return@runOnUiThread
                }
                val card = ModuleUi.card(this)
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    val line = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(2), dp(8), dp(2), dp(8))
                    }
                    renderRow(line, r)
                    if (onRowClick != null) {
                        line.isClickable = true
                        line.isFocusable = true
                        val outValue = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                        if (outValue.resourceId != 0) line.setBackgroundResource(outValue.resourceId)
                        line.setOnClickListener { onRowClick(r, rows) }
                    }
                    card.addView(line)
                    if (i < rows.length() - 1) card.addView(android.view.View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                        setBackgroundColor(android.graphics.Color.parseColor("#E3ECE7"))
                    })
                }
                box.addView(card)
            }
        }.start()
    }

    /**
     * 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — **নম্বরে/সারিতে চাপ দিলে সেই
     * নম্বরের রেকর্ডে চলে যাওয়া।**
     *
     * TK-এর কথা: *"এই নাম্বারের উপর চাপ দিলে যেন রিডাইরেক্ট হয় — এই
     * নাম্বারের অবস্থান যেখানে সেখানে চলে যেতে হবে।"* এবং *"সেই ব্যক্তি
     * রেজিস্ট্রেশন যেখানে হয়েছে সেখানে যেন রিডাইরেক্ট হয়ে যায়।"*
     *
     * ⛔ **বাড়তি কোনো cloud-read নেই** — নম্বরটা তালিকার সারিতেই আছে।
     * ⛔ `PatientTimelineActivity` **শুধু মোবাইল নম্বর** নেয় (ঐ ফাইলের
     *    ১৫৪ নম্বর লাইন), তাই সেটাই পাঠানো হয় — এই ফাইলের
     *    `openPatientHistory()`-র হুবহু একই পথ।
     * ⛔ নম্বরটা পুরো না হলে (নিচের নোট দেখুন) কিছুই খোলে না, স্পষ্ট বার্তা যায়।
     */
    private fun perfOpenNumber(rawMobile: String): Boolean {
        val digits = rawMobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            ModuleUi.toast(this, "Full number not available for this row")
            return false
        }
        return try {
            startActivity(android.content.Intent(
                this, com.tkbiswas.pilesclinic.native.PatientTimelineActivity::class.java)
                .putExtra("mobile", digits))
            true
        } catch (_: Throwable) { false }
    }

    private fun perfRowTitle(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.parseColor("#123A26"))
    }
    private fun perfRowSub(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 12.5f
        setTextColor(android.graphics.Color.parseColor("#5B6B81"))
        setPadding(0, dp(2), 0, 0)
    }

    /* 🔴 V452 (19.08.2026, TK-অনুমোদিত): Staff Performance-এর দ্বিতীয় স্তরের
       Enquiry / Calls / Collection row চাপলে exact read-only detail। নতুন Cloud
       read হয় না — list RPC-তেই detail fields এসেছে; Back-এ cached rows ফেরে। */
    private fun perfDetailField(card: LinearLayout, label: String, value: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(7), dp(2), dp(7))
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 11.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
        })
        row.addView(TextView(this).apply {
            text = value; textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#123A26"))
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(row)
    }

    private fun perfDetailScreen(
        title: String,
        period: String,
        fields: List<Pair<String, String>>,
        onBack: () -> Unit
    ) {
        backAction = onBack
        val col = ModuleUi.screen(this, title)
        if (period.isNotBlank()) col.addView(TextView(this).apply {
            text = period; textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        val card = ModuleUi.card(this)
        var shown = 0
        for ((label, value) in fields) {
            if (value.isBlank()) continue
            if (shown > 0) card.addView(android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(android.graphics.Color.parseColor("#E3ECE7"))
            })
            perfDetailField(card, label, value)
            shown++
        }
        if (shown == 0) card.addView(ModuleUi.body(this, "No detail available."))
        col.addView(card)
        col.addView(ModuleUi.button(this, "Back") { onBack() })
    }

    private fun perfShowEnquiryList(code: String, month: String, cachedRows: JSONArray? = null) {
        perfListScreen("Enquiry Forms", code, month,
            fetch = {
                val r = ModuleAuth.rpc("hr", "perf_enquiry_list_v2", JSONObject().put("p_month", month).put("p_code", code))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "No enquiry forms this period.", cachedRows = cachedRows,
            onRowClick = { r, rows ->
                perfDetailScreen(
                    "Enquiry Detail", perfLabel(month),
                    listOf(
                        "Name" to ns(r, "name").ifBlank { "Unknown" },
                        "Mobile" to ns(r, "mobile"),
                        "Date" to dmy(ns(r, "enq_date")),
                        "Branch" to ns(r, "branch"),
                        "Disease" to ns(r, "disease"),
                        "Address" to ns(r, "address"),
                        "Remarks" to ns(r, "remarks"),
                        "Status" to ns(r, "status"),
                        "Stage" to ns(r, "stage"),
                        "Received By" to ns(r, "received_by"),
                        "Created By" to ns(r, "created_by"),
                        "Created At" to ns(r, "created_at"),
                        "Record ID" to ns(r, "id")
                    )
                ) { perfShowEnquiryList(code, month, rows) }
            }) { line, r ->
            line.addView(perfRowTitle(ns(r, "name").ifBlank { "Unknown" }))
            line.addView(perfRowSub(dmy(ns(r, "enq_date")) + "  ·  " + ns(r, "mobile") + "  ·  " + ns(r, "branch")))
            if (ns(r, "disease").isNotBlank()) line.addView(perfRowSub(ns(r, "disease")))
        }
    }

    private fun perfShowRegistrationList(code: String, month: String, treatmentOnly: Boolean) {
        val title = if (treatmentOnly) "Started Treatment" else "Registrations"
        perfListScreen(title, code, month,
            fetch = {
                val fn = if (treatmentOnly) "perf_treatment_list" else "perf_registration_list"
                val r = ModuleAuth.rpc("hr", fn, JSONObject().put("p_month", month).put("p_code", code))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "None this period.",
            /* 🔵🔒 V521 (TK-নির্দেশ): সারিতে চাপ দিলে **ঐ রোগীর রেকর্ডেই**
               চলে যাওয়া হয় — আগে চাপ দিলে কিছুই হত না।
               ⛔ নম্বর পুরো না থাকলে কিছুই খোলে না, স্পষ্ট বার্তা যায়। */
            onRowClick = { r, _ -> perfOpenNumber(ns(r, "mobile")) }) { line, r ->
            line.addView(perfRowTitle(ns(r, "name").ifBlank { "Unknown" }))
            line.addView(perfRowSub(dmy(ns(r, "reg_date")) + "  \u00b7  " + ns(r, "mobile") + "  \u00b7  " + ns(r, "branch")))
            if (ns(r, "patient_id").isNotBlank()) line.addView(perfRowSub(ns(r, "patient_id")))
        }
    }

    private fun perfShowRmpList(code: String, month: String) {
        perfListScreen("RMP Added", code, month,
            fetch = {
                val r = ModuleAuth.rpc("hr", "perf_rmp_list", JSONObject().put("p_month", month).put("p_code", code))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "None this period.",
            // 🔵 V521 (TK-নির্দেশ): সারিতে চাপ = ঐ নম্বরের রেকর্ড।
            onRowClick = { r, _ -> perfOpenNumber(ns(r, "mobile")) }) { line, r ->
            line.addView(perfRowTitle(ns(r, "name").ifBlank { "Unknown" }))
            line.addView(perfRowSub(dmy(ns(r, "added_date").take(10)) + "  \u00b7  " + ns(r, "mobile") + "  \u00b7  " + ns(r, "area")))
        }
    }

    private fun perfShowCallsList(code: String, month: String, outside: Boolean, cachedRows: JSONArray? = null) {
        perfListScreen(if (outside) "Outside Calls" else "Calls From App", code, month,
            fetch = {
                val r = ModuleAuth.rpc("hr", "perf_calls_list_v2", JSONObject().put("p_month", month).put("p_code", code).put("p_kind", if (outside) "outside" else "app"))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "No calls this period.", cachedRows = cachedRows,
            onRowClick = { r, rows ->
                val fullOk = try { r.optBoolean("full_number_available", false) } catch (_: Throwable) { false }
                /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ — *"এই নাম্বারের উপর চাপ দিলে
                   যেন রিডাইরেক্ট হয়"*): নম্বরটা **পুরো জানা থাকলে** সোজা ঐ
                   নম্বরের রেকর্ডে চলে যায়।
                   ⚠️ **সৎ কথা:** পুরনো কিছু কলের পুরো নম্বর **কখনো সেভই হয়নি**
                      (সার্ভারে `target_mobile` ফাঁকা, তাই মুখোশটাই ফেরে —
                      `V452_STAFF_PERFORMANCE_EXACT_DETAIL` SQL, লাইন ১০২)।
                      অ্যাপ সেটা লুকোচ্ছে না — নেই বলেই দেখাতে পারে না।
                      ঐ সারিগুলোয় চাপ দিলে আগের মতোই ডিটেল পর্দা খোলে, যেখানে
                      কারণটা লেখা আছে। নতুন কলে পুরো নম্বরই সেভ হয় ও দেখায়। */
                val opened = fullOk && perfOpenNumber(ns(r, "target"))
                val note = if (!outside && !fullOk) "This is an older call. The full number was not stored at that time." else ""
                if (!opened) perfDetailScreen(
                    if (outside) "Outside Call Detail" else "App Call Detail", perfLabel(month),
                    listOf(
                        "Number" to ns(r, "target").ifBlank { "—" },
                        "Date" to dmy(ns(r, "call_date")),
                        "Time" to ns(r, "call_time"),
                        "Call Type" to if (outside) "Outside call" else "Call from app",
                        "Remark" to ns(r, "remark"),
                        "Note" to note,
                        "Record ID" to ns(r, "id")
                    )
                ) { perfShowCallsList(code, month, outside, rows) }
            }) { line, r ->
            line.addView(perfRowTitle(ns(r, "target").ifBlank { "—" }))
            line.addView(perfRowSub(dmy(ns(r, "call_date")) + "  ·  " + ns(r, "call_time")))
            if (ns(r, "remark").isNotBlank()) line.addView(perfRowSub(ns(r, "remark")))
        }
    }

    private fun perfShowPaymentList(code: String, month: String, online: Boolean, cachedRows: JSONArray? = null) {
        perfListScreen(if (online) "Online Collection" else "Cash Collection", code, month,
            fetch = {
                val r = ModuleAuth.rpc("hr", "perf_payment_list_v2", JSONObject().put("p_month", month).put("p_code", code).put("p_mode", if (online) "online" else "cash"))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "No payments this period.", cachedRows = cachedRows,
            onRowClick = { r, rows ->
                perfDetailScreen(
                    if (online) "Online Collection Detail" else "Cash Collection Detail", perfLabel(month),
                    listOf(
                        "Patient" to ns(r, "name").ifBlank { "Unknown" },
                        "Mobile" to ns(r, "mobile"),
                        "Amount" to money(perfDbl(r, "amount")),
                        "Date" to dmy(ns(r, "pay_date")),
                        "Mode" to ns(r, "mode").ifBlank { if (online) "Online" else "Cash" },
                        "Payment" to ns(r, "pay_label"),
                        "Payment Type" to ns(r, "pay_type"),
                        "Branch" to ns(r, "branch"),
                        "Remarks" to ns(r, "remarks"),
                        "Patient ID" to ns(r, "patient_id"),
                        "Patient Code" to ns(r, "patient_code"),
                        "Received By" to ns(r, "received_by"),
                        "Created By" to ns(r, "created_by"),
                        "Created At" to ns(r, "created_at"),
                        "Status" to ns(r, "status"),
                        "Record ID" to ns(r, "id")
                    )
                ) { perfShowPaymentList(code, month, online, rows) }
            }) { line, r ->
            line.addView(perfRowTitle(ns(r, "name").ifBlank { "Unknown" } + "  —  " + money(perfDbl(r, "amount"))))
            line.addView(perfRowSub(dmy(ns(r, "pay_date")) + "  ·  " + ns(r, "mobile") + "  ·  " + ns(r, "branch")))
            if (ns(r, "pay_label").isNotBlank()) line.addView(perfRowSub(ns(r, "pay_label")))
            else if (ns(r, "remarks").isNotBlank()) line.addView(perfRowSub(ns(r, "remarks")))
        }
    }

    private fun perfShowReportsList(code: String, month: String) {
        perfListScreen("Daily Reports Sent", code, month,
            fetch = {
                val r = ModuleAuth.rpc("hr", "perf_reports_list", JSONObject().put("p_month", month).put("p_code", code))
                if (r.ok) JSONArray(r.body) else null
            }, emptyMsg = "No reports sent this period.") { line, r ->
            line.addView(perfRowTitle(dmy(ns(r, "report_date"))))
            val acc = try { r.optBoolean("accepted", false) } catch (_: Throwable) { false }
            line.addView(perfRowSub(ns(r, "status").ifBlank { "sent" } + if (acc) "  \u00b7  seen \u2705" else ""))
        }
    }

    /** 🗓️ Google Sheet-এর মতো হাজিরা-খাতা — Date · IN · OUT · Leave, একটা
     *  টেবিলে (TK-নির্দেশ ১৮.০৮.২০২৬)। "Days present"/"Leave days" দুটো
     *  থেকেই এটাই খোলে। */
    private fun perfShowAttendanceSheet(code: String, fullName: String, month: String, fromPerf: Boolean = false) {
        // 🔴 V511: মাস্টারের Performance পর্দা থেকে খুললে (`fromPerf`) Back-এ
        //   **ঐ পর্দাতেই** ফিরবে — আগে এক ধাপ বেশি পিছিয়ে যেত।
        //   ⛔ স্টাফ নিজের পর্দা থেকে খুললে `fromPerf` false, তাই আগের আচরণ
        //      এক অক্ষরও বদলায়নি (V509-এ যেভাবে ছিল, ঠিক সেভাবেই)।
        val prevBack = (if (fromPerf) perfListBack else null) ?: backAction
        backAction = { prevBack() }
        val col = ModuleUi.screen(this, "Attendance Sheet")
        col.addView(TextView(this).apply {
            text = fullName + "  \u00b7  " + perfLabel(month); textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(dp(2), 0, dp(2), dp(8))
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "Loading...")) })
        col.addView(box)
        col.addView(ModuleUi.button(this, "Back") { backAction() })
        Thread {
            val r = try { ModuleAuth.rpc("hr", "perf_attendance_sheet", JSONObject().put("p_month", month).put("p_code", code)) } catch (_: Throwable) { null }
            val rows = if (r != null && r.ok) try { JSONArray(r.body) } catch (_: Throwable) { null } else null
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                box.removeAllViews()
                if (rows == null) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "Could not load. Please try again.")) })
                    return@runOnUiThread
                }
                if (rows.length() == 0) {
                    box.addView(ModuleUi.card(this).apply { addView(ModuleUi.body(this@StaffProfileActivity, "No attendance record this period.")) })
                    return@runOnUiThread
                }
                val sheet = ModuleUi.card(this)
                val head = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dp(2), dp(4), dp(2), dp(8))
                }
                fun headCell(t: String, w: Float) = TextView(this).apply {
                    text = t; textSize = 11.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#5B6B81"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
                }
                head.addView(headCell("DATE", 1.1f))
                head.addView(headCell("IN", 1f))
                head.addView(headCell("OUT", 1f))
                head.addView(headCell("LEAVE", 0.8f))
                sheet.addView(head)
                sheet.addView(android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                    setBackgroundColor(android.graphics.Color.parseColor("#D8ECDF"))
                })
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val isLeave = try { row.optBoolean("is_leave", false) } catch (_: Throwable) { false }
                    val tr = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(dp(2), dp(7), dp(2), dp(7))
                        if (isLeave) setBackgroundColor(android.graphics.Color.parseColor("#FDF1E7"))
                    }
                    fun cell(t: String, w: Float, color: String) = TextView(this).apply {
                        text = t; textSize = 12.5f; setTextColor(android.graphics.Color.parseColor(color))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
                    }
                    tr.addView(cell(dmy(ns(row, "work_date")), 1.1f, "#123A26"))
                    tr.addView(cell(ns(row, "check_in").ifBlank { "\u2014" }, 1f, "#0A7C3F"))
                    tr.addView(cell(ns(row, "check_out").ifBlank { "\u2014" }, 1f, "#B42318"))
                    tr.addView(cell(if (isLeave) "\u2713" else "\u2014", 0.8f, if (isLeave) "#B45309" else "#5B6B81"))
                    sheet.addView(tr)
                    if (i < rows.length() - 1) sheet.addView(android.view.View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                        setBackgroundColor(android.graphics.Color.parseColor("#EEF4F1"))
                    })
                }
                box.addView(sheet)
            }
        }.start()
    }

    /** "এই মাসের বেতন দিন" — ছোট পূর্ণ-স্ক্রিন ফর্ম: Amount (prefilled=due) · Mode; for_month=এই মাস।
     *  ⛔ salary_payments-এ insert-এর কল আগের মতোই, শুধু `for_month` যোগ। */
    private fun payForMonth(code: String, monthYm: String, due: Double) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "Pay " + salaryMonthLabel(monthYm))
        val pamt = ModuleUi.numberInput(this, "Amount", allowDecimal = true).apply { if (due > 0) setText(due.toLong().toString()) }
        val pmode = spinner(listOf("Cash", "Online"))
        col.addView(ModuleUi.label(this, "Amount")); col.addView(pamt)
        col.addView(ModuleUi.label(this, "Mode")); col.addView(pmode)
        /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — *"Add Payment · Cancel এই দুইটা
           পাশাপাশি থাকবে, একটা যেন আরেকটার গায়ে ঘেঁষে না যায়। 'Add Payment'
           লেখা থাকবে না — 'Salary Payment' হবে।"*
           আগে দুটো বোতাম একটার নিচে একটা, গায়ে-গায়ে লেগে ছিল (দুটোই সবুজ বলে
           একটাই মোটা সবুজ চাকতির মতো দেখাত — ভুল বোতামে চাপ পড়ার ভয়)।
           এখন এক সারিতে সমান দুই ভাগ, মাঝে ফাঁক — ঠিক যে সাজ `editSalaryConfig`-এ
           TK আগেই পাশ করেছেন (Cancel · Save), হুবহু সেই একই নিয়ম।
           ⛔ সেভের নিয়ম · টাকার হিসাব · database — এক অক্ষরও বদলায়নি। */
        val payRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val payCancel = ModuleUi.button(this, "Cancel") { salary(code) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val paySave = ModuleUi.button(this, "Salary Payment") {
            val row = JSONObject().put("person_code", code).put("paid_on", todayIso())
                .put("amount", pamt.text.toString().toDoubleOrNull() ?: 0.0)
                .put("mode", pmode.selectedItem.toString()).put("paid_by", ModuleAuth.personCode)
                .put("remark", "").put("for_month", monthYm)
            Thread {
                val ok = ModuleAuth.insert("hr", "salary_payments", row)
                runOnUiThread { salaryCacheClear(code); ModuleUi.toast(this, if (ok) "Payment added" else "Retry"); salary(code) }
            }.start()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        payRow.addView(payCancel)
        payRow.addView(paySave)
        col.addView(payRow)
    }

    /** 🟢 B629 (11.08.2026, TK-নির্দেশ): যেকোনো মাসের স্যালারি রেকর্ড — Master নিজে
     *  জয়েনিং ডেট থেকে History ভরতে পারবেন। ⛔ salary_payments-এ insert-এর কল
     *  payForMonth-এর হুবহু একই, শুধু মাসটা spinner থেকে বেছে নেওয়া। ডিজাইন/লেবেল
     *  বিদ্যমান ফর্মের মতোই (প্লেইন, emoji ছাড়া)। */
    private fun addSalaryAnyMonth(code: String, amount: Double, joinDate: String, pays: JSONArray) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "Add Salary — $code")
        val months = monthsFromJoin(joinDate)
        val paidSet = HashSet<String>()
        for (i in 0 until pays.length()) paidSet.add(salaryPayMonth(pays.getJSONObject(i)))
        val labels = months.map { salaryMonthLabel(it) + (if (paidSet.contains(it)) "  (Paid)" else "  (Due)") }
        val monthSpinner = spinner(labels)
        val pamt = ModuleUi.numberInput(this, "Amount", allowDecimal = true).apply { if (amount > 0) setText(amount.toLong().toString()) }
        val pmode = spinner(listOf("Cash", "Online"))
        col.addView(ModuleUi.label(this, "Month")); col.addView(monthSpinner)
        col.addView(ModuleUi.label(this, "Amount")); col.addView(pamt)
        col.addView(ModuleUi.label(this, "Mode")); col.addView(pmode)
        /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — *"Add Payment · Cancel এই দুইটা
           পাশাপাশি থাকবে, একটা যেন আরেকটার গায়ে ঘেঁষে না যায়। 'Add Payment'
           লেখা থাকবে না — 'Salary Payment' হবে।"*
           আগে দুটো বোতাম একটার নিচে একটা, গায়ে-গায়ে লেগে ছিল (দুটোই সবুজ বলে
           একটাই মোটা সবুজ চাকতির মতো দেখাত — ভুল বোতামে চাপ পড়ার ভয়)।
           এখন এক সারিতে সমান দুই ভাগ, মাঝে ফাঁক — ঠিক যে সাজ `editSalaryConfig`-এ
           TK আগেই পাশ করেছেন (Cancel · Save), হুবহু সেই একই নিয়ম।
           ⛔ সেভের নিয়ম · টাকার হিসাব · database — এক অক্ষরও বদলায়নি। */
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val addCancel = ModuleUi.button(this, "Cancel") { salary(code) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val addSave = ModuleUi.button(this, "Salary Payment") {
            val idx = monthSpinner.selectedItemPosition
            if (idx < 0 || idx >= months.size) { ModuleUi.toast(this, "Choose a month"); return@button }
            val amt = pamt.text.toString().toDoubleOrNull() ?: 0.0
            if (amt <= 0) { ModuleUi.toast(this, "Enter amount"); return@button }
            val ym = months[idx]
            val row = JSONObject().put("person_code", code).put("paid_on", todayIso())
                .put("amount", amt).put("mode", pmode.selectedItem.toString())
                .put("paid_by", ModuleAuth.personCode).put("remark", "").put("for_month", ym)
            Thread {
                val ok = ModuleAuth.insert("hr", "salary_payments", row)
                runOnUiThread { salaryCacheClear(code); ModuleUi.toast(this, if (ok) "Payment added" else "Retry"); salary(code) }
            }.start()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        addRow.addView(addCancel)
        addRow.addView(addSave)
        col.addView(addRow)
    }

    /** জয়েনিং মাস থেকে চলতি মাস পর্যন্ত YYYY-MM তালিকা (নতুন-আগে)। join_date না
     *  থাকলে/ভুল হলে শেষ ২৪ মাস ধরা হয় — কখনো ফাঁকা রাখে না। */
    private fun monthsFromJoin(joinIso: String): List<String> {
        val out = ArrayList<String>()
        try {
            val cal = java.util.Calendar.getInstance()
            val cy = cal.get(java.util.Calendar.YEAR)
            val cm = cal.get(java.util.Calendar.MONTH) + 1
            val curIdx = cy * 12 + (cm - 1)
            val startIdx = try {
                val jy = joinIso.substring(0, 4).toInt()
                val jm = joinIso.substring(5, 7).toInt()
                (jy * 12 + (jm - 1)).coerceIn(curIdx - 240, curIdx)
            } catch (_: Exception) { curIdx - 23 }
            var idx = curIdx
            while (idx >= startIdx) {
                val y = idx / 12; val m = (idx % 12) + 1
                out.add("%04d-%02d".format(y, m))
                idx--
            }
        } catch (_: Exception) {}
        return out
    }

    /** ৩-ট্যাপে মাসিক বেতন/তারিখ এডিট — পূর্ণ-স্ক্রিন ফর্ম। ⛔ config upsert আগের মতোই। */
    private fun editSalaryConfig(code: String, enabled: Boolean, amount: Double, salaryDate: String) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "Edit Salary — $code")
        val en = spinner(listOf("disabled", "enabled")).apply { setSelection(if (enabled) 1 else 0) }
        val amt = ModuleUi.numberInput(this, "Salary Amount", allowDecimal = true).apply { if (amount > 0) setText(amount.toLong().toString()) }
        val sd = ModuleUi.input(this, "Salary Date (day of month)").apply { setText(salaryDate) }
        col.addView(ModuleUi.label(this, "Salary")); col.addView(en)
        col.addView(ModuleUi.label(this, "Amount")); col.addView(amt)
        col.addView(ModuleUi.label(this, "Salary Date")); col.addView(sd)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val cancelButton = ModuleUi.button(this, "Cancel") { salary(code) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        val saveButton = ModuleUi.button(this, "Save") {
            val row = JSONObject().put("person_code", code)
                .put("salary_enabled", en.selectedItem.toString() == "enabled")
                .put("salary_amount", amt.text.toString().toDoubleOrNull() ?: 0.0)
                .put("salary_date", sd.text.toString())
                .put("updated_by", ModuleAuth.personCode).put("updated_at", nowIso())
            Thread {
                val ok = ModuleAuth.upsert("hr", "salary_config", row)
                runOnUiThread { salaryCacheClear(code); ModuleUi.toast(this, if (ok) "Saved" else "Retry"); salary(code) }
            }.start()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        actionRow.addView(cancelButton)
        actionRow.addView(saveButton)
        col.addView(actionRow)
    }

    /** See all payments — পূর্ণ তালিকা (পূর্ণ-স্ক্রিন)। */
    /** 🔵 V416 (TK-নির্দেশ): "কার কবে কত বেতন দেওয়া হয়েছে" দেখার পূর্ণ Statement।
     *  উপরে দুটো মোট (বেতন · বাড়তি), তারপর সব সারি নতুন থেকে পুরনো।
     *  ⛔ কোনো সারি বদলানো/মোছার পথ এখানে নেই — শুধু দেখা। */
    /* 🎨🔒 V443 (TK-অনুমোদিত 19.08.2026) — Salary Statement professional UI.
     *  ⛔ শুধু presentation বদলেছে: salary/extra/due হিসাব, sort order, database,
     *     save/edit/payment rule একটুও বদলায়নি।
     *  ✅ TK-র শেষ photo-proof অনুযায়ী Mode/HISTORICAL + Date ডানদিকে fixed
     *     column — সব মাসে একই সোজা রেখা বরাবর। */
    /**
     * 🔴🔴🔒 V511 (২১.০৮.২০২৬, TK-নির্দেশ) — Extra সারির পিছনের **রোগীর আইডি**।
     *
     * V418-এর SQL প্রতিটা স্বয়ংক্রিয় Extra সারিতে `src_key` লিখে রাখে —
     *      INC:<REG|TRT>:<patients.id>:<staff-code>
     * অর্থাৎ রোগীর আসল সারির আইডি ওখানেই আছে। তাই কোড/নাম মিলিয়ে আন্দাজ
     * করার দরকার নেই — সরাসরি ঠিক রোগীতে যাওয়া যায়।
     *
     * ⛔ শেষ `:`-এর পরেরটা staff-code, আর `INC:` ও stage বাদ দিলে মাঝেরটাই
     *    রোগীর আইডি — তাই আইডিতে `:` থাকলেও ভুল হয় না।
     * ⛔ হাতে বসানো Extra (`src_key` ফাঁকা) হলে ফাঁকা ফেরে — তখন কার্ডে চাপ
     *    দিলে কিছুই হয় না, আগের মতোই।
     */
    private fun extraPatientId(p: JSONObject): String {
        val key = ns(p, "src_key").trim()
        if (!key.startsWith("INC:")) return ""
        val rest = key.removePrefix("INC:")
        val firstColon = rest.indexOf(':')
        val lastColon = rest.lastIndexOf(':')
        if (firstColon < 0 || lastColon <= firstColon) return ""
        return rest.substring(firstColon + 1, lastColon).trim()
    }

    /**
     * 🔴 V511 — Extra সারিগুলোর রোগীর **নাম** এনে লাইনে বসানো (TK: *"হ্যাঁ, নাম
     * দেখান"*)। একটাই ছোট পড়া — যতগুলো আলাদা রোগী, তাদের `id,name,mobile`।
     * ⛔ ব্যর্থ হলে কিছুই বদলায় না — আগের মতো শুধু কোডই থাকে।
     * ⛔ নাম/মোবাইল পরে পপ-আপেও ব্যবহার হয়, তাই এখানেই জমা রাখা হয়।
     */
    private val extraPatientCache = HashMap<String, Pair<String, String>>()   // id → (name, mobile)

    /**
     * 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — **"কী কারণে টাকা দিচ্ছি, সেটা তো
     * বোঝা যাচ্ছে না।"**
     *
     * **আসল নিয়মটা (কোডে প্রমাণিত — `V418_INCENTIVE_AUTO_2026-08-17.sql`):**
     * Extra income তখনই তৈরি হয় যখন রোগীর সারিতে
     * `timeType = 'Unexpected Time'` — অর্থাৎ **অসময়ে আসা এনকোয়ারি**।
     * তারপর Registration Fee জমা পড়লে ₹১০০, আর প্রথম Advance/Treatment টাকা
     * জমা পড়লে আরও ₹৪০০।
     *
     * **সমস্যা যেটা ছিল:** SQL শুধু `"Registration · <কোড>"` লিখত — *Unexpected
     * Time* কথাটা **কোথাও লেখাই হত না**। তাই পপ-আপ খুলেও TK বুঝতে পারতেন না
     * কেন টাকাটা পাওনা।
     *
     * **এখন:** রোগীর `timeType` ঘরটা এখানে জমা রাখা হয় ও পপ-আপে দেখানো হয়।
     * ⛔ **একটাও বাড়তি cloud-read নয়** — উপরের একই ব্যাচ-পড়াতেই শুধু একটা
     *    সরু কলাম যোগ হয়েছে (`timeType`)। Free Plan-এ egress প্রায় শূন্য বাড়ে।
     * ⛔ কোনো SQL চালাতে হবে না · database-এ কিছু বদলায় না · টাকার অঙ্ক
     *    এক পয়সাও বদলায় না — এটা শুধু **দেখানোর** কাজ।
     */
    private val extraPatientTiming = HashMap<String, String>()               // id → timeType

    private fun fillExtraPatientNames(rows: List<Triple<String, TextView?, JSONObject>>) {
        if (rows.isEmpty()) return
        val ids = rows.map { it.first }.filter { it.isNotBlank() }.distinct()
        val need = ids.filter { !extraPatientCache.containsKey(it) }
        /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ): লাইনটা এখন **প্রতিবার নতুন করে
           বানানো হয়** (আগে শেষে জুড়ে দেওয়া হত)। কারণ এখন সামনে ⏰ চিহ্নও বসে,
           আর একই লাইন দুবার আঁকা হলে চিহ্ন/নাম দুবার বসে যেত।
           ফল: `⏰ UNEXPECTED  ·  Registration · COB-…  ·  NUR ALAM MIYA`
           ⛔ তথ্য সবই আগে থেকেই আনা — নতুন কোনো cloud-read নেই। */
        fun paint() {
            for ((pid, view, row) in rows) {
                if (view == null) continue
                val nm = extraPatientCache[pid]?.first.orEmpty().trim()
                val tt = extraPatientTiming[pid].orEmpty().trim()
                val why = ns(row, "extra_reason").trim()
                if (nm.isBlank() && tt.isBlank()) continue          // এখনো কিছুই আসেনি
                val parts = mutableListOf<String>()
                if (tt.isNotBlank()) parts.add(
                    if (tt.equals("Unexpected Time", ignoreCase = true)) "⏰ UNEXPECTED"
                    else "🕐 " + tt.uppercase()
                )
                if (why.isNotBlank()) parts.add(why)
                // ⛔ হাতে-লেখা মন্তব্য থাকলে সেটাও যেন হারিয়ে না যায় (আগের লাইনে ছিল)
                ns(row, "remark").trim().takeIf { it.isNotBlank() }?.let { parts.add(it) }
                if (nm.isNotBlank()) parts.add(nm)
                val line = parts.joinToString("  ·  ")
                if (line.isNotBlank() && view.text?.toString() != line) view.text = line
            }
        }
        if (need.isEmpty()) { paint(); return }
        Thread {
            try {
                val list = need.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
                val rows2 = com.tkbiswas.pilesclinic.native.SupabaseClient.fetchListSlimOrNull(
                    "patients", "id=in.($list)", 500, "id,name,mobile,timeType", order = "id.asc"
                )
                if (rows2 != null) {
                    for (i in 0 until rows2.length()) {
                        val o = rows2.optJSONObject(i) ?: continue
                        val id = o.optString("id", "")
                        if (id.isBlank()) continue
                        extraPatientTiming[id] = o.optString("timeType", "").trim()
                        extraPatientCache[id] = Pair(
                            o.optString("name", "").trim(),
                            o.optString("mobile", "").trim()
                        )
                    }
                }
            } catch (_: Throwable) { }
            runOnUiThread { if (!isFinishing && !isDestroyed) paint() }
        }.start()
    }

    /**
     * 🔴 V511 — Extra সারিতে চাপ দিলে ছোট পপ-আপ (TK-এর বাছা পথ)।
     * দেখায় — রোগীর নাম · মোবাইল · কেন এই টাকা · কত · কবে · অবস্থা।
     * নিচে **Open History** — চাপলে ঐ রোগীর পুরো Timeline খোলে।
     * ⛔ নাম এখনো না এলে (নেট) শুধু কোড দেখায়, তবু History খোলা যায়।
     */
    /**
     * 🔴 V511 — ঐ রোগীর পুরো History খোলা।
     * ⛔ `PatientTimelineActivity` **শুধু মোবাইল নম্বর** নেয় (ঐ ফাইলের ১৫৪ নম্বর
     *    লাইন — `patientRowId` বলে কিছু নেই)। তাই নম্বর জানা না থাকলে আগে
     *    একটাই ছোট পড়ায় নম্বরটা এনে **তারপর** খোলা হয় — নইলে ফাঁকা পর্দা খুলত।
     * ⛔ নম্বর না পাওয়া গেলে পরিষ্কার করে বলা হয়, চুপচাপ ফাঁকা পর্দা নয়।
     */
    private fun openPatientHistory(pid: String, knownMobile: String) {
        fun go(mobile: String) {
            if (mobile.isBlank()) {
                ModuleUi.toast(this, "Patient mobile not found")
                return
            }
            try {
                startActivity(android.content.Intent(
                    this, com.tkbiswas.pilesclinic.native.PatientTimelineActivity::class.java)
                    .putExtra("mobile", mobile))
            } catch (_: Throwable) { }
        }
        if (knownMobile.isNotBlank()) { go(knownMobile); return }
        Thread {
            var m = ""
            try {
                val enc = java.net.URLEncoder.encode(pid, "UTF-8")
                val rows = com.tkbiswas.pilesclinic.native.SupabaseClient.fetchListSlimOrNull(
                    "patients", "id=eq.$enc", 1, "id,name,mobile,timeType", order = "id.asc")
                val o = if (rows != null && rows.length() > 0) rows.optJSONObject(0) else null
                if (o != null) {
                    m = o.optString("mobile", "").trim()
                    extraPatientTiming[pid] = o.optString("timeType", "").trim()
                    extraPatientCache[pid] = Pair(o.optString("name", "").trim(), m)
                }
            } catch (_: Throwable) { }
            val mm = m
            runOnUiThread { if (!isFinishing && !isDestroyed) go(mm) }
        }.start()
    }

    private fun showExtraPatientPopup(p: JSONObject, pid: String) {
        val cached = extraPatientCache[pid]
        val name = cached?.first ?: ""
        val mob = cached?.second ?: ""
        val why = ns(p, "extra_reason")
        val amt = money(p.optDouble("amount", 0.0))
        val on = dmy(ns(p, "paid_on"))
        val status = if (payStatus(p) == "DUE") "DUE (not paid yet)" else "PAID"

        /* 🔵🔒 V521 (২২.০৮.২০২৬, TK-নির্দেশ) — *"কী কারণে দিচ্ছি সেটা তো বোঝা
           যাচ্ছে না… আমি বুঝবো কী করে যে স্টাফটা কী কারণে টাকা নিচ্ছে।"*
           নিচের তিনটে নতুন লাইন সেটাই বলে দেয় — Timing · কোন ধাপ · নিয়মটা।
           ⛔ সবই ইতিমধ্যেই আনা তথ্য থেকে; নতুন কোনো cloud-read নেই। */
        val timing = extraPatientTiming[pid].orEmpty().trim()
        val isUnexpected = timing.equals("Unexpected Time", ignoreCase = true)
        // `extra_reason` SQL-এ লেখা হয় `Registration · <কোড>` বা `Treatment · <কোড>`
        val stage = why.substringBefore("·").trim()
        val stageLine = when {
            stage.equals("Registration", true) ->
                "Registration Fee received  →  ₹100"
            stage.equals("Treatment", true) ->
                "First Advance / Treatment payment received  →  ₹400"
            else -> ""
        }

        val sb = StringBuilder()
        if (name.isNotBlank()) sb.append("Patient:  ").append(name).append("\n\n")
        if (mob.isNotBlank()) sb.append("Mobile:  ").append(mob).append("\n\n")
        // ⏰ সবচেয়ে জরুরি লাইন — এটাই না থাকায় TK কিছু বুঝতে পারতেন না
        if (timing.isNotBlank()) {
            val shown = if (isUnexpected) "⏰ UNEXPECTED TIME" else "🕐 " + timing.uppercase()
            sb.append("Timing:  ").append(shown).append("\n\n")
        }
        if (why.isNotBlank()) sb.append("For:  ").append(why).append("\n\n")
        if (stageLine.isNotBlank()) sb.append("Step:  ").append(stageLine).append("\n\n")
        sb.append("Amount:  ").append(amt).append("\n\n")
        sb.append("Date:  ").append(on).append("\n\n")
        sb.append("Status:  ").append(status)
        // নিয়মটা এক নজরে — TK যেন প্রতিবার মনে করার চেষ্টা না করেন
        if (isUnexpected) {
            sb.append("\n\n────────────\n")
            sb.append("Rule: only an UNEXPECTED TIME enquiry earns extra.\n")
            sb.append("₹100 when that number registers and pays the fee,\n")
            sb.append("₹400 more when the same patient pays an advance.\n")
            sb.append("Shared 50-50 with the staff who took the enquiry.")
        } else if (timing.isNotBlank()) {
            // এটা কখনো হওয়ার কথা নয় — হলে TK-কে জানানোই ঠিক, চুপ করে থাকা নয়
            sb.append("\n\n⚠️ This patient is not marked UNEXPECTED TIME.")
            sb.append("\nExtra income is only for unexpected-time enquiries —")
            sb.append("\nplease check this entry.")
        }
        val d = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(
                this, "Extra income - why?"))
            .setMessage(sb.toString())
            .setNegativeButton("Close", null)
        d.setPositiveButton("Open History") { _, _ -> openPatientHistory(pid, mob) }
        d.show().also { try { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    private fun showAllPayments(code: String, pays: JSONArray) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "")
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true

        val green = android.graphics.Color.parseColor("#075B32")
        val green2 = android.graphics.Color.parseColor("#0A7C3F")
        val ink = android.graphics.Color.parseColor("#17212B")
        val muted = android.graphics.Color.parseColor("#5B6B81")
        val line = android.graphics.Color.parseColor("#DDE8E1")
        val danger = android.graphics.Color.parseColor("#C62828")

        fun bg(fill: String, stroke: String? = null, radius: Int = 14): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(radius).toFloat()
                setColor(android.graphics.Color.parseColor(fill))
                if (stroke != null) setStroke(dp(1), android.graphics.Color.parseColor(stroke))
            }

        fun tv(
            textValue: String, size: Float, color: Int = ink,
            bold: Boolean = false, gravityValue: Int = android.view.Gravity.START
        ) = TextView(this@StaffProfileActivity).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            gravity = gravityValue
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
        }

        fun divider(vertical: Boolean = false): android.view.View = android.view.View(this).apply {
            setBackgroundColor(line)
            layoutParams = if (vertical)
                LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    topMargin = dp(8); bottomMargin = dp(8)
                }
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8); bottomMargin = dp(8)
            }
        }

        fun pill(textValue: String, kind: String): TextView {
            val fill: String
            val color: Int
            when (kind) {
                "DUE" -> { fill = "#FDE9EA"; color = danger }
                "PAID" -> { fill = "#E8F6ED"; color = green2 }
                else -> { fill = "#F0F2F4"; color = android.graphics.Color.parseColor("#49545E") }
            }
            return tv(textValue, 10.5f, color, bold = true, gravityValue = android.view.Gravity.CENTER).apply {
                setPadding(dp(7), dp(4), dp(7), dp(4))
                background = bg(fill, null, 6)
            }
        }

        fun friendlyDate(iso: String): String {
            return try {
                val src = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }
                val out = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }
                out.format(src.parse(iso.take(10)) ?: return dmy(iso))
            } catch (_: Throwable) { dmy(iso) }
        }

        fun shortMonth(ym: String): String {
            return try {
                val p = ym.split("-")
                val names = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                names[p[1].toInt() - 1] + " " + p[0]
            } catch (_: Throwable) { ym }
        }

        // ── totals: exact same rule as V442 ────────────────────────────────
        var totSalary = 0.0
        var totExtra = 0.0
        var totDue = 0.0
        var latestPaidOn = ""
        var newestSalaryMonth = ""
        var oldestSalaryMonth = ""
        for (i in 0 until pays.length()) {
            val p = pays.getJSONObject(i)
            val paidOn = ns(p, "paid_on").take(10)
            if (paidOn > latestPaidOn) latestPaidOn = paidOn
            if (payKind(p) == "EXTRA") {
                if (payStatus(p) == "DUE") totDue += p.optDouble("amount", 0.0)
                else totExtra += p.optDouble("amount", 0.0)
            } else {
                totSalary += p.optDouble("amount", 0.0)
                val ym = salaryPayMonth(p)
                if (newestSalaryMonth.isBlank() || ym > newestSalaryMonth) newestSalaryMonth = ym
                if (oldestSalaryMonth.isBlank() || ym < oldestSalaryMonth) oldestSalaryMonth = ym
            }
        }

        // ── Header ──────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(10))
        }
        val back = tv("‹", 34f, green, bold = false, gravityValue = android.view.Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(44))
            isClickable = true; isFocusable = true
            setOnClickListener { salary(code) }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(tv("Salary Statement", 21f, green, bold = true))
        titleCol.addView(tv(code, 17f, green, bold = true).apply { setPadding(0, dp(2), 0, 0) })
        header.addView(back)
        header.addView(titleCol)
        col.addView(header)

        // ── Summary card ────────────────────────────────────────────────────
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#FFFFFF", "#D9E8DF", 15)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        val summaryHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(11), dp(14), dp(11))
            background = bg("#075B32", null, 12)
        }
        summaryHead.addView(tv("Summary", 17f, android.graphics.Color.WHITE, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val upto = if (latestPaidOn.isNotBlank()) "Up to " + friendlyDate(latestPaidOn) else "Current statement"
        summaryHead.addView(tv(upto, 10.5f, android.graphics.Color.WHITE, false, android.view.Gravity.CENTER).apply {
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = bg("#0B7040", "#33A36A", 8)
        })
        summary.addView(summaryHead)

        fun metric(label: String, value: String, valueColor: Int): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(8), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(tv(label, 10.3f, ink))
            addView(tv(value, 16.5f, valueColor, bold = true).apply { setPadding(0, dp(5), 0, 0) })
        }
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), 0)
        }
        metrics.addView(metric("Salary paid (total)", money(totSalary), green2))
        metrics.addView(divider(vertical = true))
        metrics.addView(metric("Extra income paid", money(totExtra), green2))
        metrics.addView(divider(vertical = true))
        metrics.addView(metric("Extra income due", money(totDue), if (totDue > 0.0) danger else muted))
        summary.addView(metrics)
        summary.addView(divider().apply {
            (layoutParams as LinearLayout.LayoutParams).apply { leftMargin = dp(12); rightMargin = dp(12) }
        })
        val grand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
        }
        grand.addView(tv("Grand total paid", 14f, ink, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        grand.addView(tv(money(totSalary + totExtra), 21f, green, bold = true, gravityValue = android.view.Gravity.END))
        summary.addView(grand)
        col.addView(summary)

        // ── All entries title ────────────────────────────────────────────────
        val entriesHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(7))
        }
        entriesHead.addView(tv("All Entries  (${pays.length()})", 16f, ink, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        entriesHead.addView(tv("Most recent", 12f, green, false, android.view.Gravity.END))
        col.addView(entriesHead)

        if (pays.length() == 0) {
            col.addView(tv("No payments.", 14f, muted).apply { setPadding(dp(4), dp(18), dp(4), dp(18)) })
        }

        // 🔴 V511 — কোন সারিতে কোন রোগী; নাম এলে ঐ লাইনগুলোই হালনাগাদ হয়।
        val extraRows = mutableListOf<Triple<String, TextView?, JSONObject>>()

        // ── Entry cards. Fixed MODE + DATE columns = one straight line. ────
        for (i in 0 until pays.length()) {
            val p = pays.getJSONObject(i)
            val isExtra = payKind(p) == "EXTRA"
            val isDue = isExtra && payStatus(p) == "DUE"
            val amountText = money(p.optDouble("amount", 0.0))
            val modeRaw = ns(p, "mode").trim()
            val modeText = when {
                isDue -> "DUE"
                modeRaw.isBlank() -> "—"
                else -> modeRaw
            }
            val dateText = dmy(ns(p, "paid_on"))
            val leftTitle = if (isExtra) "Extra" else salaryMonthLabel(salaryPayMonth(p))
            val why = if (isExtra) ns(p, "extra_reason") else ns(p, "remark")

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(9))
                background = bg(if (isDue) "#FFF7F7" else "#FFFFFF", if (isDue) "#F2C8C8" else "#E0E8E3", 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            // slim professional accent — no emoji/icon, keeps more width for aligned columns
            top.addView(android.view.View(this).apply {
                background = bg(if (isDue) "#D83A3A" else "#1B8A50", null, 2)
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(34)).apply { rightMargin = dp(9) }
            })

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            left.addView(tv(leftTitle, 12.7f, ink, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
            })
            left.addView(tv(amountText, 12.5f, if (isDue) danger else ink, bold = isDue, gravityValue = android.view.Gravity.END).apply {
                setPadding(dp(5), 0, dp(4), 0)
            })
            top.addView(left)

            // Fixed MODE column — all rows start/end at the same x position.
            val modeBox = LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(2)
                }
            }
            modeBox.addView(pill(
                modeText,
                when {
                    isDue -> "DUE"
                    modeText.equals("Cash", true) || modeText.equals("Online", true) -> "PAID"
                    else -> "HIST"
                }
            ))
            top.addView(modeBox)

            // Fixed DATE column — exactly aligned in one straight vertical line.
            top.addView(tv(dateText, 11.7f, muted, false, android.view.Gravity.END).apply {
                layoutParams = LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT)
                maxLines = 1
            })
            card.addView(top)

            val detail = when {
                isExtra && why.isNotBlank() -> why + (ns(p, "remark").takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                !isExtra && why.isNotBlank() -> why
                !isExtra && modeText.equals("HISTORICAL", true) -> "Salary paid - confirmed by Master"
                else -> ""
            }
            var detailView: TextView? = null
            if (detail.isNotBlank()) {
                detailView = tv(detail, 10.8f, muted).apply {
                    setPadding(dp(13), dp(5), dp(2), 0)
                    maxLines = 2
                }
                card.addView(detailView)
            }
            /* 🔴🔴🔒 V511 (২১.০৮.২০২৬, TK-নির্দেশ) — **কোন রোগীর জন্য এই টাকা।**
               TK-এর কথা: *"staff কিসের জন্য পেমেন্ট পাবে আমি কেন বুঝতে পারছি না।
               যেখানে ডিউ লেখা রয়েছে সেখানে চাপ দিলে যেন আমি বুঝতে পারি, এটা কোন
               পেশেন্টের জন্য সে এক্সট্রা পেমেন্ট পাবে। তার হিস্টরি যেন আমি সেখান
               থেকে একবারেই ক্লিক করলে রিডাইরেক্ট হতে পারি।"*
               ⛔ রোগীর আসল আইডি **আগে থেকেই জমা আছে** — `src_key` ঘরে
                  (`INC:REG:<patients.id>:<staff-code>`, V418-এর SQL)। তাই আন্দাজ
                  করতে হয় না, ঠিক ঐ রোগীতেই যাওয়া যায়।
               ⛔ TK-এর বাছা পথ (২১.০৮.২০২৬): আগে ছোট পপ-আপ (নাম · মোবাইল · কেন ·
                  কত · কবে), তারপর "Open History" বোতাম — ভুল রোগীতে চলে যাওয়ার ভয় নেই।
               ⛔ নাম দেখানোর জন্য একটাই ছোট পড়া (নিচে `fillExtraPatientNames`),
                  Egress-এ প্রভাব নগণ্য। */
            val pid = extraPatientId(p)
            if (isExtra && pid.isNotBlank()) {
                extraRows.add(Triple(pid, detailView, p))
                card.isClickable = true
                card.isFocusable = true
                card.setOnClickListener { showExtraPatientPopup(p, pid) }
            }
            col.addView(card)
        }

        // 🔴 V511 — সব সারি আঁকা হয়ে গেছে; এবার রোগীর নামগুলো এনে বসানো হয়
        //   (একটাই ছোট পড়া, ব্যর্থ হলে আগের মতোই শুধু কোড থাকে)।
        fillExtraPatientNames(extraRows)

        // ── Footer summary ──────────────────────────────────────────────────
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = bg("#075B32", null, 13)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6); bottomMargin = dp(8) }
        }
        fun footerTile(label: String, value: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(tv(label, 10.5f, android.graphics.Color.parseColor("#D5EEE0")))
            addView(tv(value, 14.5f, android.graphics.Color.WHITE, bold = true).apply { setPadding(0, dp(4), 0, 0) })
        }
        footer.addView(footerTile("Total Entries", pays.length().toString()))
        footer.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#75A98B"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(42)).apply { leftMargin = dp(7); rightMargin = dp(7) }
        })
        val period = if (oldestSalaryMonth.isNotBlank() && newestSalaryMonth.isNotBlank())
            shortMonth(oldestSalaryMonth) + " – " + shortMonth(newestSalaryMonth) else "—"
        footer.addView(footerTile("Period", period))
        footer.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#75A98B"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(42)).apply { leftMargin = dp(7); rightMargin = dp(7) }
        })
        footer.addView(footerTile("Net Paid", money(totSalary + totExtra)))
        col.addView(footer)

        col.addView(ModuleUi.button(this, "Back") { salary(code) }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        })
    }

    /** 🔵 V416 (TK-নির্দেশ): বেতনের বাইরে দেওয়া বাড়তি টাকা।
     *  ⛔ `kind='EXTRA'` হয়ে জমা হয় ⇒ বেতনের "বাকি কত" হিসাবে কখনো ঢোকে না।
     *  ⛔ `for_month` ফাঁকা রাখা হয় — বাড়তি টাকা কোনো মাসের বেতন নয়। */
    private fun addExtraIncome(code: String) {
        backAction = { salary(code) }
        val col = ModuleUi.screen(this, "Extra Income — $code")
        val amt = ModuleUi.numberInput(this, "Amount", allowDecimal = true)
        val why = ModuleUi.input(this, "Reason (Bonus / Festival / Overtime)")
        val md = spinner(listOf("Cash", "Online"))
        // 🔵 V417 (TK-অনুমোদিত): এখনই দিচ্ছি, নাকি ঠিক করে রাখছি (পরে দেব)।
        val whenSpin = spinner(listOf("Paying now", "Pay later (Due)"))
        col.addView(ModuleUi.label(this, "Amount")); col.addView(amt)
        col.addView(ModuleUi.label(this, "Reason")); col.addView(why)
        col.addView(ModuleUi.label(this, "When")); col.addView(whenSpin)
        col.addView(ModuleUi.label(this, "Mode")); col.addView(md)
        col.addView(ModuleUi.button(this, "Save Extra Income") {
            val v = amt.text.toString().toDoubleOrNull() ?: 0.0
            if (v <= 0.0) { ModuleUi.toast(this, "Enter an amount"); return@button }
            val r = why.text.toString().trim()
            if (r.isEmpty()) { ModuleUi.toast(this, "Enter a reason"); return@button }
            val payingNow = whenSpin.selectedItemPosition == 0
            val row = JSONObject().put("person_code", code).put("paid_on", todayIso())
                .put("amount", v).put("mode", if (payingNow) md.selectedItem.toString() else "")
                .put("paid_by", ModuleAuth.personCode).put("remark", "")
                .put("for_month", "").put("kind", "EXTRA").put("extra_reason", r)
                .put("status", if (payingNow) "PAID" else "DUE")
            Thread {
                val ok = ModuleAuth.insert("hr", "salary_payments", row)
                runOnUiThread { salaryCacheClear(code); ModuleUi.toast(this, if (ok) "Extra income added" else "Retry"); salary(code) }
            }.start()
        })
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { salary(code) })
    }

    private fun loadConfig(code: String, enabled: Spinner, amount: android.widget.EditText, sdate: android.widget.EditText) {
        Thread {
            val r = ModuleAuth.getRows("hr", "salary_config", "select=*&person_code=eq.$code&limit=1")
            if (r.length() > 0) {
                val c = r.getJSONObject(0)
                runOnUiThread {
                    enabled.setSelection(if (c.optBoolean("salary_enabled", false)) 1 else 0)
                    if (c.has("salary_amount")) amount.setText(c.optDouble("salary_amount", 0.0).toInt().toString())
                    sdate.setText(ns(c, "salary_date"))
                }
            }
        }.start()
    }

    private fun loadHistory(code: String, hist: LinearLayout) {
        Thread {
            val r = ModuleAuth.getRows("hr", "salary_payments", "select=*&person_code=eq.$code&order=paid_on.desc")
            runOnUiThread {
                hist.removeAllViews()
                if (r.length() == 0) hist.addView(ModuleUi.body(this, "No payments."))
                for (i in 0 until r.length()) {
                    val p = r.getJSONObject(i)
                    hist.addView(ModuleUi.body(this, ns(p, "paid_on") + " · " + money(p.optDouble("amount", 0.0)) + " · " + ns(p, "mode") + " · " + ns(p, "paid_by")))
                }
            }
        }.start()
    }

    // ---------- SELF ----------
    private fun renderSelf() {
        backAction = { finish() }
        val col = ModuleUi.screen(this, "My Profile")
        val box = ModuleUi.card(this); col.addView(box); box.addView(ModuleUi.body(this, "Loading..."))
        col.addView(ModuleUi.button(this, "Back") { finish() })
        Thread {
            val r = ModuleAuth.getRows("hr", "staff_profiles", "select=*&limit=1")
            /* 🔴🔒 V496 (২১.০৮.২০২৬, TK §৩) — **বেতন শুধু আসল staff-এর।**
               সার্ভারের `role_kind` দেখেই ঠিক হয় (ফোনের কথা নয়)। ডাক্তার ও
               ফিল্ড অফিসারের পর্দায় "My Salary" ভাগটাই আর আসবে না, এবং
               বেতনের দুটো cloud-read-ও করা হয় না (Egress-ও বাঁচে)।
               ⛔ ডেটাবেসের পুরোনো কোনো সারি মোছা হয়নি — শুধু দেখানো বন্ধ। */
            val myRoleKind = if (r.length() > 0) ns(r.getJSONObject(0), "role_kind") else ""
            val salaryAllowed = com.tkbiswas.pilesclinic.native.RoleRules.salaryAppliesToRoleKind(myRoleKind)
            val sc = if (salaryAllowed) ModuleAuth.getRows("hr", "salary_config", "select=*&limit=1") else JSONArray()
            val pays = if (salaryAllowed) ModuleAuth.getRows("hr", "salary_payments", "select=*&order=paid_on.desc") else JSONArray()
            runOnUiThread {
                box.removeAllViews()
                val p = if (r.length() > 0) r.getJSONObject(0) else JSONObject()
                val desig = ns(p, "designation").ifBlank { ns(p, "role_kind") }
                val ph = ns(p, "photo_data")
                if (ph.isNotBlank()) {
                    val img = ModuleUi.image(this)
                    img.setImageBitmap(PhotoUtils.decodeDataUrl(ph))
                    box.addView(img)
                }
                box.addView(ModuleUi.body(this, ns(p, "person_code") + " · " + desig + " · " + ns(p, "branch")))
                box.addView(ModuleUi.body(this, ns(p, "full_name").ifBlank { "(name not set by Master yet)" }))
                box.addView(ModuleUi.body(this, "Mobile: " + ModuleUi.fullMobile(ns(p, "link_mobile"))))   // 🔵 V521 (TK)
                val altM = ns(p, "alt_mobile")
                if (altM.isNotBlank())
                    box.addView(ModuleUi.body(this, "Alternate Mobile: " + (if (altM.any { it.isDigit() }) ModuleUi.fullMobile(altM) else altM)))   // 🔵 V521 (TK)
                if (ns(p, "gender").isNotBlank() || ns(p, "blood_group").isNotBlank())
                    box.addView(ModuleUi.body(this, listOf(ns(p, "gender"), ns(p, "blood_group")).filter { it.isNotBlank() }.joinToString(" · ")))
                if (ns(p, "qualification").isNotBlank())
                    box.addView(ModuleUi.body(this, "Qualification: " + ns(p, "qualification")))
                if (ns(p, "gov_id_type").isNotBlank())
                    box.addView(ModuleUi.body(this, ns(p, "gov_id_type") + ": " + ModuleUi.maskIdLast4(ns(p, "gov_id_last4"))))

                /* 🗓️🔴 V509 (২১.০৮.২০২৬, TK-নির্দেশ — কাগজের হাজিরা-খাতার ছবিসহ):
                 *   *"staff এর এখানে attendance sheet এরকম থাকবে, যাতে সে দেখতে
                 *    পারে সারা মাসে কোন সময় এসেছে এবং কোন সময় ক্লিনিক থেকে গেছে,
                 *    কবে সে ছুটি নিয়েছিল।"*
                 *
                 * ─── ⛔ নতুন কিছু বানানো হয়নি ───────────────────────────────
                 * হাজিরা-খাতার পর্দাটা (`perfShowAttendanceSheet`) **আগে থেকেই
                 * তৈরি ও পরীক্ষিত** — DATE · IN · OUT · LEAVE, ঠিক TK-এর কাগজের
                 * খাতার মতোই ছক। এতদিন সেটা খুলত **শুধু মাস্টারের** "Staff
                 * Performance" পথ থেকে; স্টাফের নিজের পর্দায় ঢোকার দরজাই ছিল না।
                 * এখানে শুধু **সেই দরজাটা** বসানো হলো।
                 *
                 * ⛔ স্টাফ **শুধু নিজের** খাতা দেখেন — নিজের `person_code` ছাড়া
                 *    কিছু পাঠানোই হয় না, আর সার্ভারের নিয়মও (সঙ্গের SQL প্যাচ
                 *    V509_MY_ATTENDANCE_SHEET) নিজের কোড ছাড়া অন্য কারও সারি
                 *    ফেরত দেয় না। মাস্টারের ক্ষমতা এক অক্ষরও বদলায়নি।
                 * ⛔ বেতন · Fix Attendance · অন্য স্টাফের তথ্য — কিছুই খোলে না।
                 * ⚡ Egress: একটা মাস দেখলে **একটাই ছোট RPC** (সর্বোচ্চ ৩১ সারি,
                 *    ৪টে ঘর) — খরচ নগণ্য, চাপ দিলে তবেই যায়।
                 */
                val myCode = ns(p, "person_code")
                if (myCode.isNotBlank()) {
                    box.addView(ModuleUi.button(this, "🗓️ My Attendance Sheet") {
                        val ym = SimpleDateFormat("yyyy-MM", Locale.US)
                            .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
                            .format(java.util.Date())
                        perfShowAttendanceSheet(myCode, ns(p, "full_name").ifBlank { myCode }, ym)
                    })
                }
                // 🔴🔒 V496 (TK §৩): ডাক্তার/ফিল্ড হলে বেতনের ভাগটাই বসে না।
                if (salaryAllowed) {
                    val sbox = ModuleUi.card(this); col.addView(sbox)
                    sbox.addView(ModuleUi.heading(this, "My Salary"))
                    if (sc.length() > 0 && sc.getJSONObject(0).optBoolean("salary_enabled", false)) {
                        val c = sc.getJSONObject(0)
                        sbox.addView(ModuleUi.body(this, "Salary: " + money(c.optDouble("salary_amount", 0.0)) + " (day " + ns(c, "salary_date") + ")"))
                    } else sbox.addView(ModuleUi.body(this, "Salary not enabled."))
                    sbox.addView(ModuleUi.heading(this, "History"))
                    if (pays.length() == 0) sbox.addView(ModuleUi.body(this, "No payments."))
                    for (i in 0 until pays.length()) {
                        val pp = pays.getJSONObject(i)
                        sbox.addView(ModuleUi.body(this, ns(pp, "paid_on") + " · " + money(pp.optDouble("amount", 0.0)) + " · " + ns(pp, "mode")))
                    }
                }
            }
        }.start()
    }

    private fun spinner(items: List<String>): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        return sp
    }
}
