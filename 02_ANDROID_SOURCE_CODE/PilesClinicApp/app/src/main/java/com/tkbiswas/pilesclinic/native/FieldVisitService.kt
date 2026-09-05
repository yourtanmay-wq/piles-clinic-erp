package com.tkbiswas.pilesclinic.native

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tkbiswas.pilesclinic.R

/**
 * 🏍️🔒 V968 (০২.০৯.২০২৬, TK-নির্দেশ) — **ফিল্ড ভিজিটের GPS চালু রাখা।**
 *
 * IN TIME-এ Field Visit বাছা হলে এই সেবা চালু হয়, OUT TIME-এ (বা রাত ১২টায়)
 * বন্ধ। Android-এর নিয়মে **স্থায়ী একটা নোটিফিকেশন** দেখাতেই হয় — TK-কে
 * কাজ শুরুর আগেই এটা জানানো হয়েছে, লুকানোর কোনো উপায় নেই।
 *
 * ⛔ ব্যাকগ্রাউন্ড-লোকেশনের অনুমতি চাওয়া হয়নি; সেবা চালু থাকা অবস্থাতেই
 *    (foreground service) অবস্থান নেওয়া হয় — এটাই Play-এর নিরাপদ পথ।
 * ⛔ পুরো পথের দাগ কোথাও পাঠানো হয় না — শুধু মোট কিমি ও শেষ অবস্থান।
 */
class FieldVisitService : Service() {

    private var lm: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastPushMs = 0L
    private var lastRemindMs = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            FieldVisit.onLocation(this@FieldVisitService, location)
            updateNotice()
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                // রাত ১২টা পেরিয়ে গেছে ⇒ অ্যাপ নিজেই বন্ধ করে দেয় (TK-নির্দেশ)।
                if (FieldVisit.pastMidnight(this@FieldVisitService)) {
                    FieldVisit.endDay(this@FieldVisitService, auto = true)
                    Thread { FieldVisit.push(this@FieldVisitService, ended = true, auto = true) }.start()
                    stopSelf()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastPushMs >= PUSH_EVERY_MS) {
                    lastPushMs = now
                    Thread { FieldVisit.push(this@FieldVisitService, ended = false, auto = false) }.start()
                }
                // রাত ৯টার পর বারবার মনে করানো (TK-নির্দেশ)।
                if (FieldVisit.pastReminderHour() && now - lastRemindMs >= REMIND_EVERY_MS) {
                    lastRemindMs = now
                    remindOutTime()
                }
                updateNotice()
            } catch (_: Throwable) { }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!FieldVisit.isRunning(this)) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIF_ID, notice())
        startUpdates()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        try { handler.removeCallbacks(tick) } catch (_: Throwable) { }
        try { lm?.removeUpdates(listener) } catch (_: Throwable) { }
        super.onDestroy()
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startUpdates() {
        if (!hasPermission()) return
        try {
            lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                try {
                    if (lm?.isProviderEnabled(p) == true)
                        lm?.requestLocationUpdates(p, MIN_TIME_MS, MIN_DIST_M, listener, Looper.getMainLooper())
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
    }

    private fun noticeText(): String {
        val km = FieldVisit.kmText(FieldVisit.distanceMeters(this))
        val hrs = FieldVisit.hoursText(FieldVisit.startedAt(this), System.currentTimeMillis())
        return "$hrs  ·  $km"
    }

    private fun notice(): android.app.Notification {
        val channel = NoticeChannels.ensure(
            this, CHANNEL_ID, "Field Visit",
            "Shows while a field visit is being counted"
        )
        val open = Intent(this, com.tkbiswas.pilesclinic.modules.WorkNotebookActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val piFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        else android.app.PendingIntent.FLAG_UPDATE_CURRENT
        val pi = android.app.PendingIntent.getActivity(this, 9681, open, piFlags)
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notif_bell)
            .setColor(android.graphics.Color.parseColor("#0B3B73"))
            .setContentTitle("Field Visit running")
            .setContentText(noticeText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotice() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIF_ID, notice())
        } catch (_: Throwable) { }
    }

    private fun remindOutTime() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = NoticeChannels.ensure(
                this, CHANNEL_REMIND, "Field Visit Reminder",
                "Reminds you to mark OUT TIME after 9 PM"
            )
            val open = Intent(this, com.tkbiswas.pilesclinic.modules.WorkNotebookActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("quick_mark", "out")
            val piFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            else android.app.PendingIntent.FLAG_UPDATE_CURRENT
            val pi = android.app.PendingIntent.getActivity(this, 9682, open, piFlags)
            val text = "Mark OUT TIME now - the app will close it by itself at 12:00 AM."
            val n = NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_notif_bell)
                .setColor(android.graphics.Color.parseColor("#B42318"))
                .setContentTitle("Field visit still running")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(NOTIF_ID_REMIND, n)
        } catch (_: Throwable) { }
    }

    internal companion object {
        const val CHANNEL_ID = "piles_field_visit"
        private const val CHANNEL_REMIND = "piles_field_visit_remind"
        private const val NOTIF_ID = 9681
        private const val NOTIF_ID_REMIND = 9682
        private const val MIN_TIME_MS = 60_000L      // এক মিনিটে একবারের বেশি নয়
        private const val MIN_DIST_M = 25f           // ২৫ মিটারের কম সরলে ডাকে না
        private const val TICK_MS = 60_000L
        private const val PUSH_EVERY_MS = 180_000L   // ৩ মিনিটে একবার ক্লাউডে
        const val REMIND_EVERY_MS = 1_800_000L // রাত ৯টার পর ৩০ মিনিটে একবার
    }
}

/**
 * 🏍️ V968 — সেবাটা চালু/বন্ধ করার একটাই দরজা।
 * ⛔ ইচ্ছে করে আলাদা `object` (ক্লাসের `companion object`-এ নয়) — তাতে
 *    প্রজেক্টের পাহারা [৯.২৮] পুরো-নামে ডাকা এই দুটো ফাংশন খুঁজে পায়,
 *    আর ভবিষ্যতে নাম ভুল লিখলে সঙ্গে সঙ্গে ধরা পড়ে।
 */
object FieldVisitControl {

    fun start(context: Context) {
        try {
            val i = Intent(context, FieldVisitService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26)
                context.startForegroundService(i) else context.startService(i)
        } catch (_: Throwable) { }
    }

    fun stop(context: Context) {
        try { context.stopService(Intent(context, FieldVisitService::class.java)) } catch (_: Throwable) { }
    }
}
