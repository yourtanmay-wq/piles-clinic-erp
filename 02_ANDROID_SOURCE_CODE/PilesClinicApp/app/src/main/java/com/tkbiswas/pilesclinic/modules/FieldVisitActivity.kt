package com.tkbiswas.pilesclinic.modules

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.native.FieldVisit
import com.tkbiswas.pilesclinic.native.NativeSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 🏍️🔒 V968 (০২.০৯.২০২৬, TK-অনুমোদিত ফটো-প্রুফ অনুযায়ী) — **ফিল্ড ভিজিট।**
 *
 * দুটো চেহারা, একই পর্দা:
 *  • **স্টাফের চেহারা** (RUPAM) — আজকের RMP ডাক্তারের তালিকা, "MARK VISIT"
 *    চাপলে সময় বসে যায়, নিচে মাসের হিসাব।
 *  • **মালিকের চেহারা** (TK) — RUPAM এখন কোথায় · কত ঘণ্টা · কত কিমি ·
 *    কজন ডাক্তার, ম্যাপে খোলার বোতাম, আর আগের দিনগুলোর তালিকা।
 *
 * ⛔ পুরনো কোনো পর্দা (Dr. Visit · Work Notebook · Staff Profile) ছোঁয়া হয়নি —
 *    এটা সম্পূর্ণ আলাদা একটা পর্দা।
 * ⛔ RMP ডাক্তারের তালিকা `public.doctor_visits` (অ্যাপের আগের থেকেই থাকা
 *    ডিরেক্টরি) থেকে **শুধু পড়া** হয় — একটাও লেখা হয় না।
 * ⛔ ভিজিটের চিহ্ন লেখা হয় আলাদা `wn.doctor_visits`-এ (আলাদা schema, তাই
 *    উপরের পুরনো টেবিলের সাথে কোনো সম্পর্কই নেই)।
 */
class FieldVisitActivity : AppCompatActivity() {

    private var ownerMode = false
    private var staffCode = ""
    private var staffMobile = ""
    private var branch = ""

    private var docs = JSONArray()
    private var doneToday = HashSet<String>()
    private var doneTime = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleAuth.attachContext(this)
        val user = NativeSession.current(this)
        ownerMode = intent.getBooleanExtra(EXTRA_OWNER, false)
        staffCode = intent.getStringExtra(EXTRA_STAFF_CODE).orEmpty()
        staffMobile = intent.getStringExtra(EXTRA_STAFF_MOBILE).orEmpty()
        if (!ownerMode) {
            staffCode = user?.name.orEmpty().ifBlank { user?.mobile.orEmpty() }
            staffMobile = user?.mobile.orEmpty()
            branch = user?.branch.orEmpty()
            // 🔒 TK-নির্দেশ: শুধু বাইরে ঘোরা স্টাফ (এখন RUPAM)।
            if (!FieldVisit.isFieldStaff(staffMobile)) {
                ModuleUi.toast(this, "Not available for this staff")
                finish(); return
            }
        }
        render(loading = true)
        load()
    }

    private fun today(): String = FieldVisit.todayIso()

    private fun monthKey(): String = today().take(7)

    private fun load() {
        Thread {
            val d = if (ownerMode) JSONArray() else readDoctors()
            val dv = readVisits(today())
            val month = readVisits(monthKey())
            val days = if (ownerMode) readDays() else JSONArray()
            runOnUiThread {
                docs = d
                doneToday = HashSet()
                doneTime = HashMap()
                for (i in 0 until dv.length()) {
                    val r = dv.optJSONObject(i) ?: continue
                    val key = r.optString("doctor_mobile", "")
                    if (key.isNotBlank()) {
                        doneToday.add(key)
                        doneTime[key] = timeOf(r.optString("visited_at", ""))
                    }
                }
                if (isFinishing || isDestroyed) return@runOnUiThread
                render(loading = false, monthVisits = month.length(), days = days)
            }
        }.start()
    }

    /** RMP ডিরেক্টরি — শুধু পড়া, ব্রাঞ্চ ধরে। */
    private fun readDoctors(): JSONArray = try {
        val f = if (branch.isBlank()) null else "branch=eq." + Uri.encode(branch)
        com.tkbiswas.pilesclinic.native.SupabaseClient.fetchListSlim(
            "doctor_visits", f, 500, "id,name,mobile,area,branch", "name.asc.nullslast"
        )
    } catch (_: Throwable) { JSONArray() }

    /** আজকের (বা মাসের) ভিজিটের চিহ্ন। `key` ১০ অক্ষর = তারিখ, ৭ = মাস। */
    private fun readVisits(key: String): JSONArray = try {
        val code = if (ownerMode) staffCode else staffCode
        val q = if (key.length == 10)
            "select=doctor_mobile,visited_at&staff_code=eq." + Uri.encode(code) + "&work_date=eq." + key
        else
            "select=doctor_mobile,visited_at&staff_code=eq." + Uri.encode(code) +
                "&work_date=gte." + key + "-01&work_date=lt." + nextMonth(key) + "-01"
        ModuleAuth.getRows("wn", "doctor_visits", q)
    } catch (_: Throwable) { JSONArray() }

    private fun readDays(): JSONArray = try {
        ModuleAuth.getRows("wn", "field_visit_days",
            "select=*&staff_code=eq." + Uri.encode(staffCode) + "&order=work_date.desc&limit=30")
    } catch (_: Throwable) { JSONArray() }

    private fun nextMonth(key: String): String = try {
        val y = key.substring(0, 4).toInt(); val m = key.substring(5, 7).toInt()
        if (m >= 12) String.format(Locale.US, "%04d-01", y + 1)
        else String.format(Locale.US, "%04d-%02d", y, m + 1)
    } catch (_: Throwable) { key }

    private fun timeOf(iso: String): String = try {
        if (iso.length < 16) "" else {
            val t = iso.substring(11, 16)
            val h = t.substring(0, 2).toInt(); val mm = t.substring(3, 5)
            val ap = if (h >= 12) "PM" else "AM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            String.format(Locale.US, "%d:%s %s", h12, mm, ap)
        }
    } catch (_: Throwable) { "" }

    private fun dmy(iso: String): String = try {
        val p = iso.take(10).split("-")
        if (p.size == 3) p[2] + "." + p[1] + "." + p[0] else iso.take(10)
    } catch (_: Throwable) { iso.take(10) }

    // ─── পর্দা ────────────────────────────────────────────────────────────
    private fun render(loading: Boolean, monthVisits: Int = 0, days: JSONArray = JSONArray()) {
        val col = ModuleUi.screen(this, "")
        col.addView(ModuleUi.heading(this, if (ownerMode) "Field Visit Tracking" else "RMP Doctors"))
        col.addView(ModuleUi.body(this,
            (if (ownerMode) staffCode.ifBlank { "-" } else branch) + "  ·  " + dmy(today())))
        if (loading) { col.addView(ModuleUi.body(this, "Loading...")); return }
        if (ownerMode) renderOwner(col, days) else renderStaff(col, monthVisits)
    }

    private fun renderStaff(col: LinearLayout, monthVisits: Int) {
        val card = ModuleUi.card(this)
        card.addView(sectionTitle("Today's visits  ·  " + doneToday.size + " done"))
        if (docs.length() == 0) {
            card.addView(ModuleUi.body(this, "No RMP doctors found for this branch."))
        }
        for (i in 0 until docs.length()) {
            val r = docs.optJSONObject(i) ?: continue
            val name = r.optString("name", "").trim().ifBlank { "Unknown" }
            val mob = r.optString("mobile", "").trim()
            val area = r.optString("area", "").trim()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = ModuleUi.dp(this@FieldVisitActivity, 8) }
            }
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            left.addView(TextView(this).apply {
                text = name.uppercase(Locale.US)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#101C2E"))
            })
            left.addView(TextView(this).apply {
                text = listOf(area, mob).filter { it.isNotBlank() }.joinToString("  ·  ")
                textSize = 11.5f
                setTextColor(android.graphics.Color.parseColor("#8B98A9"))
            })
            row.addView(left)
            if (doneToday.contains(mob)) {
                row.addView(TextView(this).apply {
                    text = doneTime[mob].orEmpty().ifBlank { "Visited" }
                    textSize = 11.5f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#0B7A4B"))
                })
            } else {
                row.addView(ModuleUi.buttonSoft(this, "MARK VISIT") { markVisit(name, mob, area) })
            }
            card.addView(row)
        }
        col.addView(card)

        val stat = ModuleUi.card(this)
        stat.addView(sectionTitle("This month"))
        stat.addView(ModuleUi.body(this, "Doctors visited: $monthVisits"))
        val fv = FieldVisit
        if (fv.isRunning(this)) {
            stat.addView(ModuleUi.body(this,
                "Field visit running  ·  " +
                    fv.hoursText(fv.startedAt(this), System.currentTimeMillis()) + "  ·  " +
                    fv.kmText(fv.distanceMeters(this))))
        }
        col.addView(stat)
    }

    private fun renderOwner(col: LinearLayout, days: JSONArray) {
        if (days.length() == 0) {
            col.addView(ModuleUi.card(this).also {
                it.addView(ModuleUi.body(this, "No field visit recorded yet."))
            })
            return
        }
        for (i in 0 until days.length()) {
            val r = days.optJSONObject(i) ?: continue
            val date = r.optString("work_date", "").take(10)
            val started = r.optString("started_at", "")
            val ended = r.optString("ended_at", "")
            val auto = r.optBoolean("auto_closed", false)
            val meters = r.optDouble("distance_m", 0.0)
            val card = ModuleUi.card(this)
            val status = when {
                ended.isBlank() && date == today() -> "RUNNING"
                ended.isBlank() -> "NOT CLOSED"
                auto -> "AUTO CLOSED"
                else -> "COMPLETE"
            }
            val colour = when (status) {
                "RUNNING" -> "#0B7A4B"
                "AUTO CLOSED" -> "#8A5A00"
                "NOT CLOSED" -> "#B42318"
                else -> "#0B7A4B"
            }
            card.addView(TextView(this).apply {
                text = dmy(date) + "   ·   " + status
                textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(colour))
            })
            val hrs = hoursBetween(started, ended)
            card.addView(ModuleUi.body(this,
                "Hours " + hrs + "   ·   Distance " + FieldVisit.kmText(meters)))
            if (auto) card.addView(ModuleUi.body(this, "OUT TIME not marked - closed by app at 12:00 AM"))
            val lat = r.optDouble("last_lat", Double.NaN)
            val lng = r.optDouble("last_lng", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN() && (lat != 0.0 || lng != 0.0)) {
                val seen = timeOf(r.optString("last_seen_at", ""))
                card.addView(ModuleUi.body(this,
                    "Last seen " + (if (seen.isBlank()) "-" else seen) +
                        "  ·  accuracy ±" + r.optInt("last_acc_m", 0) + " m"))
                card.addView(ModuleUi.buttonSoft(this, "OPEN IN GOOGLE MAPS") {
                    openMap(lat, lng)
                })
            }
            col.addView(card)
        }
    }

    private fun hoursBetween(a: String, b: String): String {
        return try {
            if (a.isBlank()) return "-"
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            f.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val s = f.parse(a)?.time ?: return "-"
            val e = if (b.isBlank()) System.currentTimeMillis() else (f.parse(b)?.time ?: return "-")
            FieldVisit.hoursText(s, e)
        } catch (_: Throwable) { "-" }
    }

    private fun openMap(lat: Double, lng: Double) {
        try {
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")))
            } catch (_: Throwable) { ModuleUi.toast(this, "No map app found") }
        }
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14.5f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.parseColor("#101C2E"))
    }

    private fun markVisit(name: String, mobile: String, area: String) {
        if (mobile.isBlank()) { ModuleUi.toast(this, "This doctor has no mobile number"); return }
        val row = JSONObject()
            .put("staff_code", staffCode)
            .put("work_date", today())
            .put("branch", branch)
            .put("doctor_name", name)
            .put("doctor_mobile", mobile)
            .put("area", area)
        Thread {
            val ok = ModuleAuth.insert("wn", "doctor_visits", row)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) { ModuleUi.toast(this, "Visit marked"); load() }
                else ModuleUi.toast(this, "Could not save - check internet")
            }
        }.start()
    }

    companion object {
        const val EXTRA_OWNER = "owner_mode"
        const val EXTRA_STAFF_CODE = "staff_code"
        const val EXTRA_STAFF_MOBILE = "staff_mobile"
    }
}
