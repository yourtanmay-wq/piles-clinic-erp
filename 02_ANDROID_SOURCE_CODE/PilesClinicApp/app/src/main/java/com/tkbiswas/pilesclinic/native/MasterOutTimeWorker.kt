package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tkbiswas.pilesclinic.R

/**
 * 🔴🔴🔒 V511 (২১.০৮.২০২৬, TK-নির্দেশ) — **রাত ৯টায় মাস্টারকে জানানো: আজ কারা
 * OUT TIME দেননি।**
 *
 * TK-এর কথা: *"চেম্বার তো বন্ধ করে সবাই বাড়ি চলে গেছে"* — অর্থাৎ কেউ OUT TIME
 * না দিলে মাস্টার সেটা জানতেই পারতেন না; নিজে খুঁজে দেখে তবেই ধরা পড়ত।
 *
 * ─── যা করে ───────────────────────────────────────────────────────────────
 * রাত ৯টায় (একবার) আজকের হাজিরার সারিগুলো দেখে — যাঁদের **IN আছে কিন্তু OUT
 * নেই** এবং **ছুটি নয়**, তাঁদের নাম নিয়ে মাস্টারের ফোনে একটা নোটিফিকেশন।
 * কেউ বাকি না থাকলে **একদম চুপ** — অকারণ নোটিফিকেশন কখনো নয়।
 *
 * ─── ⛔ নিরাপত্তা ও খরচ ───────────────────────────────────────────────────
 *  • **শুধু মাস্টারের ফোনে** চলে (`role == "master"`), স্টাফ/ডাক্তারের নয়।
 *  • ডেটাবেসের নিয়ম আগে থেকেই আছে — `wn.notebook_days`-এর RLS নীতি
 *    `nd_all` বলে: `hr.is_master() or staff_code = hr.my_code()`
 *    (V246-এর SQL, ২১৪ নম্বর লাইন)। তাই **নতুন কোনো SQL চালাতে হয় না**,
 *    আর মাস্টার ছাড়া কেউ এই তালিকা পড়তেও পারে না।
 *  • পড়া হয় দিনে **একবার**, শুধু চারটে ঘর (staff_code · check_in ·
 *    check_out · is_leave), আজকের তারিখের সারি — Egress-এ প্রভাব নগণ্য।
 *  • কিছু ভুল হলে চুপচাপ ফিরে যায় — কোনো কিছু ভাঙে না, কোনো তথ্য লেখা হয় না।
 *
 * ⚠️ সৎ সীমা (`AttendanceReminderWorker`-এও একই কথা): কিছু ফোনের
 *    battery-optimization ব্যাকগ্রাউন্ড কাজ দেরি/বাদ দিতে পারে — এটা
 *    best-effort, ১০০% গ্যারান্টিড অ্যালার্ম নয়।
 */
class MasterOutTimeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try {
            val user = NativeSession.current(ctx)
            if (user != null && user.role == "master") {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
                    .format(java.util.Date())
                val missing = pending(ctx, today)
                if (missing.isNotEmpty()) notify(ctx, missing)
            }
        } catch (_: Throwable) {
            // কখনো ক্র্যাশ করবে না
        }
        // ⛔ ফল যাই হোক, কালকের ৯টার স্লট আবার বসিয়ে চেইন চালু রাখা হয়।
        try { MasterOutTimeScheduler.scheduleNext(ctx) } catch (_: Throwable) { }
        return Result.success()
    }

    /** আজ IN দিয়েছেন কিন্তু OUT দেননি, ছুটিও নয় — এমন স্টাফের নাম। */
    private suspend fun pending(ctx: Context, today: String): List<String> {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val ma = com.tkbiswas.pilesclinic.modules.ModuleAuth
                if (!ma.isSignedIn) { try { ma.signInCurrentSession(ctx) } catch (_: Throwable) { } }
                if (!ma.isSignedIn) return@withContext emptyList<String>()
                val res = ma.getRowsChecked(
                    "wn", "notebook_days",
                    "select=staff_code,check_in,check_out,is_leave&work_date=eq.$today&limit=200"
                )
                if (!res.ok) return@withContext emptyList<String>()
                val out = mutableListOf<String>()
                for (i in 0 until res.rows.length()) {
                    val r = res.rows.optJSONObject(i) ?: continue
                    if (r.optBoolean("is_leave", false)) continue
                    val cin = r.optString("check_in", "")
                    val cout = r.optString("check_out", "")
                    val inOk = cin.isNotBlank() && cin != "null"
                    val outOk = cout.isNotBlank() && cout != "null"
                    if (inOk && !outOk) {
                        val code = r.optString("staff_code", "").trim()
                        if (code.isNotBlank()) out.add(code)
                    }
                }
                out.distinct()
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun notify(ctx: Context, codes: List<String>) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = NoticeChannels.ensure(
                ctx, CHANNEL_ID, "OUT TIME missing",
                "Tells the Master which staff did not mark OUT TIME today"
            )
            // ⛔ স্টাফ-কোডই দেখানো হয় (COB-4 · JPE-CRP …) — মাস্টার এই কোডেই
            //    সবাইকে চেনেন, আর এতে কোনো বাড়তি পড়া লাগে না।
            val names = codes.joinToString(", ")
            // ⛔ নোটিফিকেশনে চাপলে Staff Profiles পর্দা — এটা AndroidManifest-এ
            //    আগে থেকেই ঘোষিত (৪০০ নম্বর লাইন), তাই নতুন কিছু যোগ করতে হয়নি।
            val intent = Intent(ctx, com.tkbiswas.pilesclinic.modules.StaffProfileActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = android.app.PendingIntent.getActivity(
                ctx, 9110, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⏰ OUT TIME not marked today (${codes.size})")
                .setContentText(names)
                .setStyle(NotificationCompat.BigTextStyle().bigText(names))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(9110, n)
        } catch (_: Throwable) { }
    }

    companion object {
        private const val CHANNEL_ID = "master_out_time_missing"
    }
}
