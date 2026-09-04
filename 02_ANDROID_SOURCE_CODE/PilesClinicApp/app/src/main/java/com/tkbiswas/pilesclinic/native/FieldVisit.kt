package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.location.Location
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 🏍️🔒 V968 (০২.০৯.২০২৬, TK-এর চূড়ান্ত নির্দেশ) — **ফিল্ড ভিজিট গোনা।**
 *
 * TK: *"সে যখন যায় বাইক নিয়ে যায় … তাকে কিলোমিটার প্রতি টাকা দিতে হয় … আমি
 * চাইছি তার GPS অন রেখে, IN TIME যখন করবে তখন থেকে GPS অটোমেটিক অন থাকবে,
 * যতক্ষণ না সে OUT TIME চাপবে … দিনের কোন নির্দিষ্ট টাইমে আমি যেন দেখতে পাই
 * সে কোথায় আছে এখন।"*
 *
 * ─── লক করা নিয়ম ──────────────────────────────────────────────────────────
 *  • **শুধু বাইরে ঘোরা স্টাফ** — TK: *"শুধু বাইরে ঘোরা স্টাফদের জন্য"*।
 *    এখন সেটা একজনই: RUPAM (JPE-RUPAM)। অন্য কারো পর্দায় এই বোতামই ওঠে না।
 *  • স্টাফ নিজে **Field Visit** বেছে IN TIME করলে তবেই GPS চলে। TK:
 *    *"RUPAM নিজে চাপবে"*।
 *  • না চাপলে অ্যাপ **নিজে থেকে ছুটি বসায় না** — TK: *"আমার পর্দায় লাল
 *    দেখাবে, ছুটির সিদ্ধান্ত আমি নেব"*।
 *  • রাত ৯টার পর বারবার মনে করানো, রাত ১২টায় অ্যাপ নিজেই বন্ধ করে দেয়
 *    (`auto_closed` চিহ্ন বসে, যাতে TK বুঝতে পারেন স্টাফ নিজে চাপেনি)।
 *  • **পুরো পথের দাগ রাখা হয় না** — TK-এর সিদ্ধান্ত (ফ্রি প্ল্যানে ঝুঁকি)।
 *    দিনে একজনের **একটাই সারি**, সেটাই বারবার নতুন করে লেখা হয়।
 *
 * ⚠️ **সীমাবদ্ধতা (সৎভাবে):** নকল-অবস্থান অ্যাপ দিয়ে ফাঁকি পুরোপুরি ঠেকানো
 *    যায় না (`ClinicPresence`-এর একই সীমা), আর GPS-এর কিলোমিটার বাইকের
 *    মিটারের সাথে হুবহু মিলবে না।
 */
object FieldVisit {

    /* 🔒 TK-নির্দেশ: *"এটা যেন শুধুমাত্র RUPAM-এর ক্ষেত্রেই হয়ে থাকে"*।
       নম্বরটা `StaffDirectory`-র JPE-RUPAM-এর সাথে মেলানো (যাচাই করা)।
       ⛔ নতুন কারো জন্য চালু করতে হলে TK বলবেন, তখন এখানেই এক লাইন যোগ। */
    private val FIELD_STAFF_MOBILES = setOf("8167096595")

    const val MODE_CHAMBER = "CHAMBER"
    const val MODE_FIELD = "FIELD"

    private const val PREF = "piles_field_visit"

    /** সবচেয়ে অনিশ্চিত যে অবস্থান গ্রহণ করা হবে (মিটার)। */
    private const val MAX_ACCURACY_M = 60f
    /** এর কম সরলে ধরা হয় না — GPS-এর নিজের কাঁপুনিতে কিমি বেড়ে যাওয়া ঠেকায়। */
    private const val MIN_STEP_M = 20f
    /** এক লাফে এর বেশি হলে ধরা হয় না — লাফিয়ে-যাওয়া ভুল অবস্থান বাদ। */
    private const val MAX_STEP_M = 3000f

    fun isFieldStaff(mobile: String?): Boolean =
        FIELD_STAFF_MOBILES.contains(StaffDirectory.normalizeMobile(mobile.orEmpty()))

    fun isFieldStaff(context: Context): Boolean =
        isFieldStaff(try { NativeSession.current(context)?.mobile } catch (_: Throwable) { null })

    /** স্টাফ-কোড (যেমন "JPE-RUPAM") ধরে — Master-এর পর্দায় বোতাম দেখানোর জন্য। */
    fun isFieldStaffCode(code: String?): Boolean {
        val c = code.orEmpty().trim()
        if (c.isBlank()) return false
        return StaffDirectory.allAccounts().any {
            it.name.equals(c, ignoreCase = true) && isFieldStaff(it.mobile)
        }
    }

    /** ওই কোডের স্টাফের নম্বর (না পেলে ফাঁকা)। */
    fun mobileForCode(code: String?): String {
        val c = code.orEmpty().trim()
        return StaffDirectory.allAccounts().firstOrNull { it.name.equals(c, ignoreCase = true) }?.mobile.orEmpty()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.format(java.util.Date())

    /** এখনকার সময়, ভারতের offset সহ — ডেটাবেসে পাঠানোর জন্য। */
    fun isoNow(): String = iso(System.currentTimeMillis())

    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.format(java.util.Date(ms))

    // ─── আজকের অবস্থা ────────────────────────────────────────────────────
    fun runningDate(context: Context): String = prefs(context).getString("run_date", "").orEmpty()
    fun isRunning(context: Context): Boolean = runningDate(context).isNotBlank()
    fun startedAt(context: Context): Long = prefs(context).getLong("started_at", 0L)
    fun distanceMeters(context: Context): Double =
        java.lang.Double.longBitsToDouble(prefs(context).getLong("dist_m", 0L))
    fun lastSeenAt(context: Context): Long = prefs(context).getLong("seen_at", 0L)
    /** ⚠️ V1032 — আজ একটাও অবস্থান পাওয়া গেছে কি না। না পেলে কিলোমিটার
     *  গোনাই শুরু হয় না, তাই স্টাফের পর্দায় লাল সতর্কতা দেখাতে এটা লাগে। */
    fun hasFix(context: Context): Boolean = prefs(context).getBoolean("has_fix", false)
    fun lastLat(context: Context): Double =
        java.lang.Double.longBitsToDouble(prefs(context).getLong("last_lat", 0L))
    fun lastLng(context: Context): Double =
        java.lang.Double.longBitsToDouble(prefs(context).getLong("last_lng", 0L))

    /** এই ফোনে আজ কোনটা বাছা হয়েছিল — CHAMBER / FIELD / ফাঁকা। */
    fun chosenMode(context: Context): String {
        val p = prefs(context)
        return if (p.getString("mode_date", "") == todayIso()) p.getString("mode", "").orEmpty() else ""
    }

    fun chooseMode(context: Context, mode: String) {
        prefs(context).edit().putString("mode_date", todayIso()).putString("mode", mode).apply()
    }

    /** IN TIME-এর পরে ডাকা হয় — দিনের গোনা শুরু। */
    fun startDay(context: Context, staffCode: String, branch: String) {
        prefs(context).edit()
            .putString("run_date", todayIso())
            .putString("staff_code", staffCode)
            .putString("branch", branch)
            .putLong("started_at", System.currentTimeMillis())
            .putLong("dist_m", java.lang.Double.doubleToRawLongBits(0.0))
            .putLong("seen_at", 0L)
            .putLong("last_lat", 0L).putLong("last_lng", 0L)
            .putInt("last_acc", 0)
            .putBoolean("has_fix", false)
            .apply()
    }

    /** OUT TIME (auto=false) বা রাত ১২টা (auto=true) — গোনা শেষ। */
    fun endDay(context: Context, auto: Boolean) {
        prefs(context).edit()
            .putString("end_date", runningDate(context).ifBlank { todayIso() })
            .putLong("ended_at", System.currentTimeMillis())
            .putBoolean("auto_closed", auto)
            .putString("run_date", "")
            .apply()
    }

    fun staffCode(context: Context): String = prefs(context).getString("staff_code", "").orEmpty()
    fun branch(context: Context): String = prefs(context).getString("branch", "").orEmpty()

    /**
     * নতুন একটা অবস্থান এলে — দূরত্ব জমা করা।
     * ⛔ অনিশ্চিত · খুব ছোট · লাফিয়ে-যাওয়া মাপ গোনায় ঢোকে না।
     */
    fun onLocation(context: Context, loc: Location) {
        try {
            if (!isRunning(context)) return
            if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
            if (isMock(loc)) return
            val p = prefs(context)
            val e = p.edit()
            if (p.getBoolean("has_fix", false)) {
                val prev = Location("prev").apply {
                    latitude = lastLat(context); longitude = lastLng(context)
                }
                val step = prev.distanceTo(loc)
                if (step >= MIN_STEP_M && step <= MAX_STEP_M) {
                    val total = distanceMeters(context) + step
                    e.putLong("dist_m", java.lang.Double.doubleToRawLongBits(total))
                } else if (step < MIN_STEP_M) {
                    // দাঁড়িয়ে আছেন — অবস্থান হালনাগাদ হবে, দূরত্ব নয়।
                }
            }
            e.putLong("last_lat", java.lang.Double.doubleToRawLongBits(loc.latitude))
                .putLong("last_lng", java.lang.Double.doubleToRawLongBits(loc.longitude))
                .putInt("last_acc", if (loc.hasAccuracy()) loc.accuracy.toInt() else 0)
                .putLong("seen_at", System.currentTimeMillis())
                .putBoolean("has_fix", true)
                .apply()
        } catch (_: Throwable) { }
    }

    private fun isMock(loc: Location): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 31) loc.isMock
        else @Suppress("DEPRECATION") loc.isFromMockProvider
    } catch (_: Throwable) { false }

    /** ক্লাউডে আজকের সারিটা লেখা — দিনে একটাই সারি, বারবার হালনাগাদ। */
    fun push(context: Context, ended: Boolean, auto: Boolean): Boolean {
        return try {
            val p = prefs(context)
            val date = (if (ended) p.getString("end_date", "") else runningDate(context)).orEmpty()
                .ifBlank { todayIso() }
            val code = staffCode(context)
            if (code.isBlank()) return false
            val row = JSONObject()
                .put("staff_code", code)
                .put("work_date", date)
                .put("branch", branch(context))
                .put("distance_m", Math.round(distanceMeters(context)))
                .put("updated_at", iso(System.currentTimeMillis()))
            val st = startedAt(context)
            if (st > 0L) row.put("started_at", iso(st))
            if (p.getBoolean("has_fix", false)) {
                row.put("last_lat", lastLat(context))
                    .put("last_lng", lastLng(context))
                    .put("last_acc_m", p.getInt("last_acc", 0))
            }
            val seen = lastSeenAt(context)
            if (seen > 0L) row.put("last_seen_at", iso(seen))
            if (ended) {
                row.put("ended_at", iso(p.getLong("ended_at", System.currentTimeMillis())))
                row.put("auto_closed", auto)
            }
            com.tkbiswas.pilesclinic.modules.ModuleAuth.attachContext(context)
            com.tkbiswas.pilesclinic.modules.ModuleAuth
                .upsertOnConflict("wn", "field_visit_days", row, "staff_code,work_date")
        } catch (_: Throwable) { false }
    }

    /** রাত ৯টা পার হয়েছে কি না — তখন থেকেই OUT TIME মনে করানো শুরু। */
    fun pastReminderHour(): Boolean = hourNow() >= 21

    /** গোনা শুরুর দিনটা পেরিয়ে গেছে কি না — রাত ১২টায় নিজে বন্ধ করার জন্য। */
    fun pastMidnight(context: Context): Boolean {
        val started = runningDate(context)
        return started.isNotBlank() && started != todayIso()
    }

    private fun hourNow(): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        return c.get(Calendar.HOUR_OF_DAY)
    }

    /** "21.4 km" — পর্দায় দেখানোর জন্য। */
    fun kmText(meters: Double): String =
        String.format(Locale.US, "%.1f", meters / 1000.0) + " km"

    /** "3h 12m" — পর্দায় দেখানোর জন্য। */
    fun hoursText(fromMs: Long, toMs: Long): String {
        if (fromMs <= 0L || toMs <= fromMs) return "0h 00m"
        val mins = ((toMs - fromMs) / 60000L).toInt()
        return String.format(Locale.US, "%dh %02dm", mins / 60, mins % 60)
    }
}
