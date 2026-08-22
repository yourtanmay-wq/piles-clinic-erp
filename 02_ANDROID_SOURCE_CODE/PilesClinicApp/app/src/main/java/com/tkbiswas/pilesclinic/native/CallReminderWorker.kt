package com.tkbiswas.pilesclinic.native

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tkbiswas.pilesclinic.R

/**
 * Fires at the scheduled times (11 AM / 2 PM / 4 PM). Counts how many follow-up
 * calls are DUE TODAY for the logged-in staff (their branch), and — if any — posts
 * a phone notification "আজ X টা কল বাকি" so the staff is reminded even when the app
 * is closed. After running it re-schedules the next slot.
 *
 * Reliability note: Android battery optimisation on some phones can delay/skip
 * background work; this is a best-effort reminder, not a guaranteed alarm.
 */
class CallReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val ctx = applicationContext
            val user = NativeSession.current(ctx)
            if (user != null) {
                // 🔒 TK-এর নিয়ম (29.07.2026 সকাল ১১.৩০, খাতার সারি B77 — TK-এর
                // ফটো-প্রুফসহ): *"মাস্টার তো কোনো ব্যক্তিকে কল করবে না। তাহলে
                // মাস্টারের নোটিফিকেশন এগুলো কেন আসে?"*
                //
                // ৫ বারের ফলো-আপ কল **ওই ব্রাঞ্চের স্টাফরাই** করেন — মাস্টার
                // করেন না। অথচ এতদিন এই মনে-করানোটা রোলের দিকে তাকাতই না, আর
                // মাস্টার সব ব্রাঞ্চ দেখেন বলে তাঁর ফোনে **পাঁচ ব্রাঞ্চের সব
                // নাম** একসঙ্গে চলে আসত।
                //
                // ⛔ শুধু **কলের মনে-করানোটা** বন্ধ হলো। নিচের ঘন্টার খবর
                // (`BellNotifier` — অনুমোদনের অনুরোধ ইত্যাদি) মাস্টারের জন্য
                // আগের মতোই চালু আছে, কারণ ওগুলো মাস্টারকেই দেখতে হয়।
                if (user.role != "master") {
                    val due = dueToday(user)
                    if (due.isNotEmpty()) notify(ctx, due)
                }
                // TK-REQUESTED (2026-07-27): a new notice / pending approval
                // used to arrive in the bell with no sound at all, and if the
                // app was closed nobody knew until it was opened. This reuses
                // the very same count the Dashboard shows (BellCounter) and
                // rings only when that number has gone UP since the last ring.
                // It rides on the reminder slots that already run (11 AM /
                // 2 PM / 4 PM), so no new background schedule and no extra
                // cloud traffic beyond those three checks a day.
                try { BellNotifier.onCount(ctx, user, BellCounter.count(ctx, user)) } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // never crash the worker
        } finally {
            // Chain the next slot regardless of outcome.
            CallReminderScheduler.scheduleNext(applicationContext)
        }
        return Result.success()
    }

    /** CHANGE-4 (TK, safe patch): tapping this notification opens
     *  FollowUpActivity with todayOnly=true (Inquiry tab, Today filter) —
     *  same as the dashboard banner. So this list must come from the exact
     *  same repository query, not a separate all-stage query, or the
     *  names/count in the notification would not match the list that
     *  opens. TK-REQUESTED CHANGE (2026-07-16): returns the actual
     *  FollowUpItem list now (was just a count) so notify() can name each
     *  patient, not only say how many. */
    private fun dueToday(user: NativeUser): List<FollowUpItem> {
        // 🔒 খাতার সারি B61 (TK, 29.07.2026): আজকের কল **ফলো-আপের তিন জায়গা
        // থেকেই** ধরা হয় — 👥 Enquiry (`Inquiry`) · 👣 Visit (`Patient`) ·
        // 👤 Patient (`Treatment`)। ড্যাশবোর্ডের ব্যানারেও ঠিক এই তিনটেই গোনা
        // হয়, তাই ব্যানারের সংখ্যা আর এই মনে-করানোর সংখ্যা কখনো আলাদা হবে না।
        val today = FollowUpModel.today()
        val repo = FollowUpRepository(applicationContext)
        val all = mutableListOf<FollowUpItem>()
        for (stage in listOf("Inquiry", "Patient", "Treatment")) {
            // 🔵🔒 V493 (20.08.2026, TK-নির্দেশ ৩ — Supabase Egress): আগে এখানে
            // পূর্ণ `fetchTab()` ছিল, অর্থাৎ দিনে ৩ বার (১১টা · ২টা · ৪টা)
            // প্রতিটা ফোনে তিন ট্যাবের **পুরো তালিকা** নামত — অথচ এই কাজটার
            // দরকার শুধু "আজ কার কল আছে" গোনা।
            // এখন `fetchTabDelta()` — ঠিক যেটা `BackgroundRefreshWorker` V457
            // থেকে ব্যবহার করছে (প্রমাণিত পথ)। ⛔ নিরাপত্তা-জাল অটুট: delta
            // প্রথমবার বা ব্যর্থ হলে সে নিজে থেকেই পূর্ণ `fetchTab()`-এ ফিরে
            // যায়, তাই মনে-করানোর সংখ্যা কখনো কম দেখাবে না।
            try { all.addAll(repo.fetchTabDelta(stage, user.branch, user.name, user.mobile)) } catch (_: Throwable) { }
        }
        return all.filter { it.nextFollow == today }
    }

    private fun notify(ctx: Context, due: List<FollowUpItem>) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 🔊 খাতার সারি B28 — একই কারণ, একই সমাধান (NoticeChannels দেখুন)।
        val channel = NoticeChannels.ensure(
            ctx, CHANNEL_ID, "Call Reminders", "Reminders for today's pending follow-up calls"
        )
        // Tapping opens the Follow-up screen filtered to today's due calls.
        val open = Intent(ctx, FollowUpActivity::class.java)
            .putExtra("todayOnly", true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        else android.app.PendingIntent.FLAG_UPDATE_CURRENT
        val pi = android.app.PendingIntent.getActivity(ctx, 0, open, flags)

        val count = due.size

        // TK-REQUESTED CHANGE (2026-07-16): names now shown in the full
        // notification -- capped at 5 lines so it stays readable, with a
        // "+N more" line if there are extra ones.
        val shown = due.take(5).joinToString("\n") { "• ${it.name.ifBlank { it.mobile }}" }
        val extra = (count - 5).let { if (it > 0) "\n+$it more" else "" }
        val bigText = shown + extra

        // TK APPROVED (2026-07-16): the lock screen must NOT show patient
        // names (privacy) -- only this count-only "public" version shows
        // there. The full version (names, built below) only appears once
        // the phone is unlocked and the notification shade is open.
        val publicVersion = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setColor(android.graphics.Color.parseColor("#0B3B73"))
            .setContentTitle("📞 Today's Pending Calls")
            .setContentText("$count calls pending today — tap to view.")
            .build()

        val n = NotificationCompat.Builder(ctx, channel)
            // 🎨 TK-APPROVED (2026-08-06): clean bell icon + brand accent (BigText already present).
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setColor(android.graphics.Color.parseColor("#0B3B73"))
            .setContentTitle("📞 Today's Pending Calls ($count)")
            .setContentText("Tap to view — " + due.take(2).joinToString(", ") { it.name.ifBlank { it.mobile } } + if (count > 2) "…" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "call_reminders"
        const val NOTIF_ID = 4201
    }
}
