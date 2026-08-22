package com.tkbiswas.pilesclinic.native

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.print.BranchCatalog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * NEW (2026-08-07, TK-approved). The in-app "কাল আসার কথা" list. Opened from
 * the day-before reminder notification (or directly). Shows every person the
 * branch is expecting at the chamber TOMORROW, each with a one-tap dial
 * button so staff can phone and remind them a day ahead.
 *
 * Reads ONE filtered query (chamber_expected rows dated tomorrow) — nothing
 * about money, attendance, or any other record is touched. Built entirely in
 * code (no new layout XML) to stay self-contained and low-risk.
 */
// 🔴🔒 V475 (20.08.2026, TK-অনুমোদিত ফটো-প্রুফ) — name/mobile-এর সাথে এখন
// রোগ/ঠিকানা/Remark-ও (থাকলে) বহন করে, কল করার আগে দেখা যায়।
data class ExpectedItem(val name: String, val mobile: String, val disease: String = "", val address: String = "", val remark: String = "")

class ExpectedTomorrowActivity : AppCompatActivity() {

    private lateinit var listHolder: LinearLayout
    private lateinit var subtitle: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        // Top bar.
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0F766E"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(TextView(this@ExpectedTomorrowActivity).apply {
                text = "←  কাল আসার কথা"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                isClickable = true; isFocusable = true
                setOnClickListener { finish() }
            })
        })

        subtitle = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Color.parseColor("#475467"))
            setPadding(dp(16), dp(12), dp(16), dp(6))
            text = "Loading…"
        }
        root.addView(subtitle)

        listHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(16))
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(listHolder)
        }
        root.addView(scroll)
        setContentView(root)

        load()
    }

    private fun load() {
        val user = NativeSession.current(this)
        if (user == null) { subtitle.text = "Session not found."; return }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        val human = SimpleDateFormat("EEE dd.MM", Locale.US).format(tomorrow.time)
        val myBranch = BranchCatalog.byName(user.branch)
        val myBranchId = myBranch.id
        val branchName = myBranch.displayName
        // 🔵🔒 (09.08.2026, TK-নির্দেশ "লোডিং দেরি ঠিক করুন" — AppointmentActivity-র
        // প্রমাণিত cache-first প্যাটার্নের হুবহু মিরর): শেষবার সফলভাবে আনা তালিকা এই
        // পর্দার নিজের SharedPreferences-এ (কালকের তারিখ+ব্রাঞ্চ ধরে) জমা থাকে; পাতা
        // খুললেই সাথে সাথে দেখায়, তারপর ক্লাউড থেকে হালনাগাদ এলে বদলায়। ⛔ ছাঁকনি/
        // ব্রাঞ্চ/সাজানোর নিয়ম কিছু বদলায়নি — শুধু প্রথম দেখানো এগিয়ে আনা।
        val cacheKey = "exp_${myBranchId}_$key"
        val cached = loadCachedExpected(cacheKey)
        if (cached != null) render(cached, human, branchName)

        BackgroundWork.run {
            // fetchListOrNull → null ONLY on a genuine failed read (bad line /
            // non-200), so a failed load is NEVER shown as "কেউ নেই" (0). An
            // empty (non-null) array is a real "nobody expected tomorrow".
            val rows = try {
                SupabaseClient.fetchListOrNull("payments", "payType=eq.chamber_expected&date=eq.$key", 200)
            } catch (_: Throwable) { null }

            val items = ArrayList<ExpectedItem>()
            if (rows != null) {
                val seen = HashSet<String>()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    if (row.optString("payType", "") != "chamber_expected") continue
                    if (BranchCatalog.byName(row.optString("branch", "")).id != myBranchId) continue
                    val mobile = row.optString("mobile", "")
                    if (!seen.add(mobile.ifBlank { row.optString("id", "") })) continue
                    items.add(ExpectedItem(row.optString("name", ""), mobile))
                }
            }

            // 🔴🔒 V475 (20.08.2026, TK-অনুমোদিত ফটো-প্রুফ) — রোগ/ঠিকানা/Remark
            // ⛔ followups টেবিল থেকে **একবারে** (bulk, N+1 নয়) এই ব্রাঞ্চের সব
            // সারি এনে, মোবাইল মিলিয়ে বসানো হয় — একটাও বাড়তি per-patient কল নেই।
            // ⛔ ব্যর্থ হলে (নেট/অন্য কারণ) এই তিনটা ঘরই শুধু ফাঁকা থাকে —
            //    নাম/নম্বর/ফোন-বোতাম আগের মতোই কাজ করে, কিছু ভাঙে না।
            if (items.isNotEmpty()) {
                try {
                    val fuRows = SupabaseClient.fetchListOrNull(
                        "followups", "branch=eq.${java.net.URLEncoder.encode(myBranch.displayName, "UTF-8")}", 2000,
                        select = "mobile,disease,address,lastRemark,updatedAt"
                    )
                    if (fuRows != null) {
                        val byMobile = HashMap<String, org.json.JSONObject>()
                        for (i in 0 until fuRows.length()) {
                            val fr = fuRows.optJSONObject(i) ?: continue
                            val m = fr.optString("mobile", "").filter { it.isDigit() }.takeLast(10)
                            if (m.isBlank()) continue
                            val existing = byMobile[m]
                            if (existing == null || fr.optString("updatedAt", "") > existing.optString("updatedAt", "")) {
                                byMobile[m] = fr
                            }
                        }
                        for (idx in items.indices) {
                            val m = items[idx].mobile.filter { it.isDigit() }.takeLast(10)
                            val fr = byMobile[m] ?: continue
                            val remark = fr.optString("lastRemark", "")
                            items[idx] = items[idx].copy(
                                disease = fr.optString("disease", ""),
                                address = fr.optString("address", ""),
                                remark = if (remark.isNotBlank() && !isKnownAutoRemark(remark)) remark else ""
                            )
                        }
                    }
                } catch (_: Throwable) { }
            }

            if (!isFinishing && !isDestroyed) runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (rows == null) {
                    // A failed read must NEVER be shown as "কেউ নেই" (0). শেষ-জানা
                    // তালিকা থাকলে সেটাই থাক; না থাকলে সৎভাবে জানাই (retry-র জন্য)।
                    if (cached == null) subtitle.text = "Could not load the list — please open it again shortly."
                    return@runOnUiThread
                }
                saveCachedExpected(cacheKey, items)
                render(items, human, branchName)
            }
        }
    }

    // AppointmentActivity-র মতোই একটাই render — cache ও তাজা দুই পথেই ব্যবহার হয়।
    private fun render(items: List<ExpectedItem>, human: String, branchName: String) {
        subtitle.text = if (items.isEmpty())
            "Nobody is expected tomorrow ($human)."
        else
            "Tomorrow ($human) · $branchName — ${items.size} patient(s)"
        /* 🔴🔒 V509 (TK-রিপোর্ট): ক্যাশ ও ক্লাউডের তালিকা হুবহু এক হলে আর
           মুছে-আঁকা হয় না — ঝিলিক বন্ধ। ⛔ আলাদা হলেই আগের মতোই পুরো আঁকে।

           🔴🔴 নিজের যাচাইয়ে ধরা পড়া ভুল (২১.০৮.২০২৬) — **এটা রোগীর পুরোনো
           তথ্য দেখিয়ে দিতে পারত।** আগে চিহ্নটা বানানো হত শুধু **নাম ও
           মোবাইল** দিয়ে। কিন্তু সারিতে আরও তিনটে জিনিস আঁকা হয় — **রোগ ·
           ঠিকানা · সর্বশেষ রিমার্ক**। কেউ Follow-Up-এ নতুন রিমার্ক লিখলে
           (যেমন "বিকেল ৫টায় আসবেন") নাম-মোবাইল একই থাকত, তাই চিহ্নও একই
           হত — আর **নতুন রিমার্কটা পর্দায় বসতই না**। স্টাফ পুরোনো কথা পড়ে
           ফোন করতেন। এখন সারির **সব ঘর** চিহ্নে ধরা হয় (`toString()`), তাই
           এক অক্ষর বদলালেও আগের মতোই পুরো আঁকা হয়। */
        if (com.tkbiswas.pilesclinic.native.RedrawGuard.alreadyShowing(
                listHolder, items.joinToString("|") { it.toString() })) return
        listHolder.removeAllViews()
        for (it in items) addRow(it, human)
    }

    private fun expCachePrefs() = getSharedPreferences("expected_tomorrow_cache", MODE_PRIVATE)
    private fun loadCachedExpected(cacheKey: String): List<ExpectedItem>? {
        return try {
            val json = expCachePrefs().getString(cacheKey, null) ?: return null
            val arr = org.json.JSONArray(json)
            val list = ArrayList<ExpectedItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ExpectedItem(
                    o.optString("name", ""), o.optString("mobile", ""),
                    o.optString("disease", ""), o.optString("address", ""), o.optString("remark", "")
                ))
            }
            list
        } catch (_: Throwable) { null }
    }
    private fun saveCachedExpected(cacheKey: String, items: List<ExpectedItem>) {
        try {
            val arr = org.json.JSONArray()
            for (it in items) arr.put(
                org.json.JSONObject().put("name", it.name).put("mobile", it.mobile)
                    .put("disease", it.disease).put("address", it.address).put("remark", it.remark)
            )
            expCachePrefs().edit().putString(cacheKey, arr.toString()).apply()
        } catch (_: Throwable) { }
    }

    // 🔴🔒 V475 — Chamber Attendance-এর isAppAutoRemark()-এর হুবহু একই তালিকা,
    // এখানে ছোট আকারে (আলাদা ফাইল, তাই নিজের একটা কপি — আসল ফাইলের কোড ছোঁয়া হয়নি)।
    private fun isKnownAutoRemark(remark: String): Boolean {
        val r = remark.trim()
        val fixed = listOf(
            "Treatment payment / Advance received",
            "Advance Payment received",
            "Converted to Patient / Treatment"
        )
        return fixed.any { r.equals(it, ignoreCase = true) }
    }

    private fun addRow(item: ExpectedItem, human: String) {
        val name = item.name
        val mobile = item.mobile
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(4), 0, dp(4))
            layoutParams = lp
        }
        // 🔴🔒 V474 (20.08.2026, TK-রিপোর্ট) — নাম ফাঁকা থাকলে আগে উপরের
        // লাইনে (নাম-এর জায়গায়) mobile ফলব্যাক হিসেবে বসত, আর নিচের লাইনেও
        // (ঠিকানার লাইনে) একই mobile — তাই মোবাইল নম্বর **দুইবার** দেখাত।
        // এখন নাম সত্যিই ফাঁকা হলে উপরের লাইনটাই বাদ (শুধু নিচের লাইনে
        // একবারই নম্বর)।
        val hasName = name.isNotBlank()
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            // 🔵 TK (10.08.2026): এই কার্ডে শুধু নাম+নম্বর থাকে, তাই ইনি Enquiry
            // না Visit না Patient বোঝা যায় না। নামের/নম্বরের অংশে চাপ দিলে সোজা
            // Follow-Up-এর ঠিক ওই সেকশনে গিয়ে কার্ডটা হাইলাইট হয় (📞 বোতাম আলাদা,
            // তাই ফোন করার আচরণ অক্ষত)।
            isClickable = true; isFocusable = true
            setOnClickListener {
                try {
                    startActivity(
                        android.content.Intent(this@ExpectedTomorrowActivity, FollowUpActivity::class.java)
                            .putExtra("focusCardMobile", mobile)
                    )
                } catch (_: Throwable) { }
            }
            if (hasName) {
                addView(TextView(this@ExpectedTomorrowActivity).apply {
                    text = name
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#101828"))
                    // 🔴🔒 V474 (TK-নির্দেশ) — নাম-এর উপর long-press করলে কপি হবে।
                    isLongClickable = true
                    setOnLongClickListener {
                        try {
                            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("patient name", name))
                            android.widget.Toast.makeText(this@ExpectedTomorrowActivity, NoBengali.s("নাম কপি হয়েছে"), android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) { }
                        true
                    }
                })
            }
            addView(TextView(this@ExpectedTomorrowActivity).apply {
                text = "${formatMobile(mobile)} · $human আসবেন"
                textSize = if (hasName) 12.5f else 15f
                setTypeface(typeface, if (hasName) Typeface.NORMAL else Typeface.BOLD)
                setTextColor(Color.parseColor(if (hasName) "#475467" else "#101828"))
                setPadding(0, dp(2), 0, 0)
                // 🔴🔒 V474 (TK-নির্দেশ) — মোবাইল নম্বরের উপর long-press করলে কপি হবে।
                isLongClickable = true
                setOnLongClickListener {
                    try {
                        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("mobile", formatMobile(mobile)))
                        android.widget.Toast.makeText(this@ExpectedTomorrowActivity, NoBengali.s("নম্বর কপি হয়েছে"), android.widget.Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) { }
                    true
                }
            })
            // 🔴🔒 V475 (20.08.2026, TK-অনুমোদিত ফটো-প্রুফ) — রোগ + ঠিকানা,
            // একই লাইনে (থাকলেই)। বাড়তি কোনো cloud-request নয় (load()-এ
            // bulk করে আনা হয়েছে)।
            val metaText = listOf(
                item.disease.ifBlank { null }?.let { "🩺 $it" },
                item.address.ifBlank { null }?.let { "📍 $it" }
            ).filterNotNull().joinToString("  ·  ")
            if (metaText.isNotBlank()) {
                addView(TextView(this@ExpectedTomorrowActivity).apply {
                    text = metaText
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#7A8699"))
                    setPadding(0, dp(4), 0, 0)
                })
            }
            // Remark (থাকলেই) — কল করার আগে দেখা যাবে।
            if (item.remark.isNotBlank()) {
                addView(TextView(this@ExpectedTomorrowActivity).apply {
                    text = "📝 ${item.remark}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#0C6B3D"))
                    setBackgroundColor(Color.parseColor("#EAFAF1"))
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    val mlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    mlp.topMargin = dp(5)
                    layoutParams = mlp
                })
            }
        })
        card.addView(TextView(this).apply {
            text = "📞 Call"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#12805C")); cornerRadius = dp(10).toFloat()
            }
            isClickable = true; isFocusable = true
            setOnClickListener {
                val digits = mobile.filter { it.isDigit() }
                if (digits.isBlank()) return@setOnClickListener
                // 🔴🔒 V475 (20.08.2026, TK-রিপোর্ট + সঠিক অনুমান — "+91 আসে না
                // বলেই অবৈধ নম্বর বলে"): আগে শুধু digits (কোনো country-code
                // ছাড়া বাড়ি ১০-ডিজিট) পাঠানো হতো — অনেক ডায়ালার/ক্যারিয়ার
                // ভারতেও country-code ছাড়া bare ১০-ডিজিট নম্বরকে ডায়াল করতে
                // অস্বীকার করে ("invalid number")। এখন `formatMobile()`
                // (এই ফাইলেই আগে থেকে ছিল, শুধু ডিসপ্লের জন্য ব্যবহার হতো)
                // দিয়ে +91-সহ পাঠানো হচ্ছে — বাকি সব স্ক্রিনের নিয়মের সাথেই মেলে।
                try {
                    CallChooser.open(this@ExpectedTomorrowActivity, formatMobile(mobile))
                } catch (_: Throwable) {
                    try { startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:${formatMobile(mobile)}"))) } catch (_: Throwable) { }
                }
            }
        })
        listHolder.addView(card)
    }

    private fun formatMobile(raw: String): String {
        val d = raw.filter(Char::isDigit)
        return when {
            d.length == 10 -> "+91$d"
            d.length == 12 && d.startsWith("91") -> "+$d"
            else -> raw
        }
    }
}
