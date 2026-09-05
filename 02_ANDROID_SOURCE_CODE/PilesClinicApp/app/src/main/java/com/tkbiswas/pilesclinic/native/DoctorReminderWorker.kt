package com.tkbiswas.pilesclinic.native

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tkbiswas.pilesclinic.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 🟢🔒🔒 V656 (২৫.০৮.২০২৬, TK-নির্দেশ, তিনটে প্রশ্ন করে নিশ্চিত হয়ে) →
 * 🔴🔒 V671 (২৫.০৮.২০২৬, TK-নির্দেশ) — "শুধুমাত্র তারিখ বাঁচলে হবে না,
 * সময়টাও তো বাঁচতে হবে তবেই তো নোটিফিকেশন সাউন্ড হবে"।
 *
 * `BriefingReminderScheduler`-এর হুবহু একই প্রমাণিত প্রতি-১৫-মিনিট
 * WorkManager one-time-chain (exact-alarm অনুমতি লাগে না)। প্রতিবার
 * চালু হয়ে দেখে — ডাক্তারের বাছা **সময়টা** এখনকার ১৫-মিনিটের জানালার
 * ভিতরে পড়ছে কিনা। সময় না বাছা থাকলে (ঐচ্ছিক — TK: "কোনোটাই
 * বাধ্যতামূলক নয়") পুরনো ডিফল্ট (সন্ধ্যা ৫টা) স্লটেই একবার বাজে।
 */
class DoctorReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try { NoBengali.refresh(applicationContext) } catch (_: Throwable) { }
        try {
            val ctx = applicationContext
            val user = NativeSession.current(ctx)
            // ⛔ শুধু ডাক্তার — TK-এর স্পষ্ট নির্দেশ ("শুধু ডাক্তার")।
            if (user != null && user.role == "doctor") {
                /* 🔔🔒 V839 (TK-নির্দেশ) — NEXT VISIT PLAN-এর দুটো নোটিফিকেশন
                   (কাল কী আছে · রোগী এসেছেন) **এই চলতি কাজের ভিতরেই** —
                   নতুন কোনো WorkManager কাজ নয়, তাই ব্যাটারিতে বাড়তি চাপ নেই।
                   ⛔ নিজের try/catch-এ; ব্যর্থ হলেও নিচের পুরনো রিমাইন্ডার
                      এক অক্ষরও প্রভাবিত হয় না। */
                try { NextVisitPlanNotifier.run(ctx) } catch (_: Throwable) { }
                val now = Calendar.getInstance()
                val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
                val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val alreadyFiredIds = prefs.getStringSet(firedKeyFor(todayKey), emptySet()) ?: emptySet()
                val nowHM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val defaultSlotHM = DEFAULT_SLOT_HOUR * 60
                /* 🩺🔒 V1109 (০৫.০৯.২০২৬, TK-নির্দেশ: *"কোন ডাক্তারকে মনে করিয়ে
                   দিতে হবে সেই ডাক্তারের নাম যেন চুস করা যায়, এবং এটা যেন
                   কার্যকরী হয়"*)।
                   🔴 আগে কী হত (মেপে দেখা): উপরের `role == "doctor"` ছাড়া আর
                      কোনো ছাঁকনি ছিল না ⇒ ওই দিনের **প্রতিটা** রিমাইন্ডার
                      **সব ডাক্তারের** ফোনে বাজত, যদিও পর্দার লেখায় বলা ছিল
                      *"শুধু আপনাকেই"*।
                   ⇒ এখন সারিতে ডাক্তার বাছা থাকলে **শুধু তাঁর ফোনেই** বাজে।
                   ⛔ পুরনো সারিতে ঘরটা ফাঁকা ⇒ আচরণ হুবহু আগের মতোই (সবার কাছে),
                      তাই আগের কোনো রিমাইন্ডার হারায় না।
                   ⛔ নম্বর মেলানো হয় **শেষ ১০ অঙ্ক** ধরে (+91 থাকুক বা না থাকুক) —
                      প্রকল্পের সব জায়গার একই নিয়ম। */
                val meDigits = (user.mobile).filter { it.isDigit() }.takeLast(10)
                val due = dueTomorrow().filter { d ->
                    val want = d.forMobile.filter { it.isDigit() }.takeLast(10)
                    if (want.isNotEmpty() && want != meDigits) return@filter false
                    true
                }.filter { d ->
                    if (d.id in alreadyFiredIds) return@filter false
                    if (d.timeHM != null) {
                        // 🔴🔒 V671 — সময় বাছা থাকলে সেই ১৫-মিনিটের জানালাতেই বাজে।
                        kotlin.math.abs(d.timeHM - nowHM) <= REPEAT_GAP_MINUTES / 2
                    } else {
                        // সময় না বাছা থাকলে পুরনো ডিফল্ট স্লট (৫টা)।
                        kotlin.math.abs(defaultSlotHM - nowHM) <= REPEAT_GAP_MINUTES / 2
                    }
                }
                if (due.isNotEmpty()) {
                    notify(ctx, due)
                    val updated = (alreadyFiredIds + due.map { it.id }).toMutableSet()
                    prefs.edit().putStringSet(firedKeyFor(todayKey), updated).commit()
                }
            }
        } catch (_: Exception) {
            // worker কখনো ক্র্যাশ করবে না
        } finally {
            DoctorReminderScheduler.scheduleNext(applicationContext)
        }
        return Result.success()
    }

    private data class Due(
        val id: String, val name: String, val note: String, val timeHM: Int?,
        /* 🩺 V1109 — কোন ডাক্তারের জন্য। ফাঁকা = সব ডাক্তার (পুরনো আচরণ)। */
        val forMobile: String = ""
    )

    /** patients টেবিলে যাদের doctorReminderDate = আগামীকাল। */
    private fun dueTomorrow(): List<Due> {
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(tomorrow.time)
        val rows = try {
            SupabaseClient.fetchListSlimOrNull(
                "patients", "doctorReminderDate=eq.$key", 200,
                // 🔴🔒 V671 — doctorReminderTime-ও এখন পড়া হয়।
                // 🩺 V1109 — `doctorReminderFor`-ও পড়া হয় (কোন ডাক্তারের জন্য)।
                "id,name,doctorReminderNote,doctorReminderDate,doctorReminderTime,doctorReminderFor"
            )
        } catch (_: Throwable) { null } ?: return emptyList()
        val out = ArrayList<Due>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val note = row.optString("doctorReminderNote", "").trim()
            if (note.isBlank()) continue
            val timeStr = row.optString("doctorReminderTime", "").trim()
            val timeHM = if (timeStr.isNotBlank()) try {
                val parts = timeStr.split(":").map { it.toInt() }
                parts[0] * 60 + parts[1]
            } catch (_: Throwable) { null } else null
            out.add(Due(
                row.optString("id", "").trim(), row.optString("name", "").trim(), note, timeHM,
                row.optString("doctorReminderFor", "").trim()   // 🩺 V1109
            ))
        }
        return out
    }

    private fun notify(ctx: Context, due: List<Due>) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NoticeChannels.ensure(
            ctx, CHANNEL_ID, "Doctor Reminder", "One-day-ahead reminder for patient notes the doctor wrote"
        )
        val open = Intent(ctx, DashboardActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        else android.app.PendingIntent.FLAG_UPDATE_CURRENT
        val pi = android.app.PendingIntent.getActivity(ctx, 0, open, flags)

        val count = due.size
        val shown = due.take(5).joinToString("\n") { "• ${it.name.ifBlank { "Patient" }}: ${it.note}" }
        val extra = (count - 5).let { if (it > 0) "\n+$it more" else "" }

        // লক-স্ক্রিনে শুধু সংখ্যা (গোপনীয়তা) — বাকি সব রিমাইন্ডারের একই নিয়ম।
        val publicVersion = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setColor(android.graphics.Color.parseColor("#0F766E"))
            .setContentTitle(NoBengali.s("🔔 আগামীকালের রোগী-নোট"))
            .setContentText("$count reminder(s) for tomorrow.")
            .build()

        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setColor(android.graphics.Color.parseColor("#0F766E"))
            .setContentTitle(NoBengali.s("🔔 আগামীকালের রোগী-নোট") + " ($count)")
            .setContentText(due.first().name.ifBlank { "Patient" } + ": " + due.first().note)
            .setStyle(NotificationCompat.BigTextStyle().bigText(shown + extra))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "doctor_reminder"
        const val NOTIF_ID = 4209
        private const val PREFS = "piles_clinic_doctor_reminder"
        // 🔴🔒 V671 — এখন প্রতি ১৫ মিনিটে চেক (আগে দিনে একবার, ৫টায়)।
        const val REPEAT_GAP_MINUTES = 15
        const val DEFAULT_SLOT_HOUR = 17
        // 🔴🔒 V671 — একই রোগীর জন্য একই দিনে দুবার না বাজে, তাই প্রতিদিনের
        // "কাদের বাজানো হয়েছে" তালিকা (আগে শুধু একটা date-flag ছিল)।
        private fun firedKeyFor(dateKey: String) = "fired_$dateKey"
    }
}
