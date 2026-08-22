package com.tkbiswas.pilesclinic.native

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 📍🔒 V496 (২১.০৮.২০২৬, TK-এর চূড়ান্ত নির্দেশ §৫) — **ক্লিনিকে আছেন কিনা।**
 *
 * TK-এর সিদ্ধান্ত: **GPS** (ওয়াই-ফাই নয়)। ব্রাঞ্চের অবস্থান অ্যাপের ভিতরেই
 * (`ClinicLocations.kt`) — নতুন কোনো ডেটাবেস টেবিল বানানো হয়নি।
 *
 * ─── নিয়ম ─────────────────────────────────────────────────────────────────
 *  • GPS **সারাক্ষণ চলে না** — শুধু Staff IN TIME চাপার মুহূর্তে চালু হয়,
 *    ফল পাওয়ামাত্র বা সময় শেষে সঙ্গে সঙ্গে বন্ধ। ব্যাটারি খরচ নেই বললেই চলে।
 *  • দূরত্ব **ও** নির্ভুলতা — দুটোই দেখা হয়। খুব অনিশ্চিত অবস্থান (যেমন
 *    ৳±৫০০ মিটার) দিয়ে সিদ্ধান্ত নেওয়া হয় না।
 *  • **নকল অবস্থান** ধরা পড়লে গ্রহণ করা হয় না।
 *  • অনুমতি নেই · Location বন্ধ · সময় শেষ · দুর্বল নির্ভুলতা — প্রতিটার
 *    আলাদা বার্তা। কোনো অবস্থাতেই crash বা আটকে যাওয়া নয়।
 *  • ব্রাঞ্চের সংখ্যা বসানো না থাকলে **নীরবে গ্রহণ নয়** — স্পষ্ট বার্তা।
 *
 * ⚠️ **সীমাবদ্ধতা (সৎভাবে):** রুট-করা ফোনে বা কিছু পুরনো Android-এ নকল
 *    অবস্থান পুরোপুরি ধরা যায় না। এটা ফাঁকি **কঠিন করে**, একেবারে বন্ধ করে না।
 *    (`isFromMockProvider` Android ৮+ থেকে, `isMock` Android ১২+ থেকে।)
 */
object ClinicPresence {

    enum class Reason {
        INSIDE,
        OUTSIDE,
        NOT_CONFIGURED,     // ওই ব্রাঞ্চের সংখ্যা এখনো বসানো হয়নি
        NO_PERMISSION,
        LOCATION_OFF,
        TIMEOUT,
        LOW_ACCURACY,
        MOCK_DETECTED,
        UNKNOWN_BRANCH,
        ERROR
    }

    data class Outcome(
        val ok: Boolean,
        val reason: Reason,
        val message: String,
        val distanceMeters: Int? = null
    )

    /** কত সেকেন্ড পর্যন্ত অবস্থানের জন্য অপেক্ষা করা হবে। */
    private const val TIMEOUT_MS = 12_000L

    /** এর চেয়ে বেশি অনিশ্চিত অবস্থান দিয়ে সিদ্ধান্ত নেওয়া হয় না (মিটার)। */
    private const val MAX_ACCURACY_M = 100f

    fun messageFor(reason: Reason, point: ClinicLocations.ClinicPoint?, distance: Int?): String =
        when (reason) {
            Reason.INSIDE -> ""
            Reason.OUTSIDE ->
                "আপনি ক্লিনিকে নেই" +
                    (if (distance != null) " (প্রায় $distance মিটার দূরে)" else "") +
                    "। হাজিরা শুধু চেম্বারে এসে দেওয়া যায়।"
            Reason.NOT_CONFIGURED ->
                "${point?.displayName ?: "এই ব্রাঞ্চ"}-এর অবস্থান এখনো অ্যাপে বসানো হয়নি। মাস্টারকে জানান — তিনি Fix Attendance দিয়ে হাজিরা বসিয়ে দিতে পারবেন।"
            Reason.NO_PERMISSION ->
                "হাজিরার জন্য Location-এর অনুমতি দরকার। অনুমতি দিয়ে আবার চেষ্টা করুন।"
            Reason.LOCATION_OFF ->
                "ফোনের Location বন্ধ আছে। চালু করে আবার চেষ্টা করুন।"
            Reason.TIMEOUT ->
                "অবস্থান পাওয়া গেল না। খোলা জায়গায় গিয়ে বা জানালার কাছে দাঁড়িয়ে আবার চেষ্টা করুন।"
            Reason.LOW_ACCURACY ->
                "অবস্থানটা যথেষ্ট নিশ্চিত নয়। একটু অপেক্ষা করে বা জানালার কাছে গিয়ে আবার চেষ্টা করুন।"
            Reason.MOCK_DETECTED ->
                "নকল অবস্থান ধরা পড়েছে, তাই হাজিরা নেওয়া হয়নি। মাস্টারকে জানানো হবে।"
            Reason.UNKNOWN_BRANCH ->
                "আপনার ব্রাঞ্চ চেনা গেল না। মাস্টারকে জানান।"
            Reason.ERROR ->
                "অবস্থান যাচাই করা গেল না। একটু পরে আবার চেষ্টা করুন।"
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(lm: LocationManager): Boolean = try {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Throwable) { false }

    /** Android যেটুকু বলতে পারে — নকল অবস্থান কিনা। */
    private fun isMock(loc: Location): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.isMock
        else @Suppress("DEPRECATION") loc.isFromMockProvider
    } catch (_: Throwable) { false }

    /**
     * এখনকার অবস্থান নিয়ে ক্লিনিকে আছেন কিনা দেখে। **ঠিক একবার** [onResult] ডাকে।
     * ফল পাওয়ামাত্র GPS বন্ধ করে দেওয়া হয়।
     */
    fun check(activity: AppCompatActivity, branchName: String?, onResult: (Outcome) -> Unit) {
        val point = ClinicLocations.forBranchName(branchName)
        if (point == null) {
            onResult(Outcome(false, Reason.UNKNOWN_BRANCH, messageFor(Reason.UNKNOWN_BRANCH, null, null)))
            return
        }
        if (!point.isConfigured) {
            onResult(Outcome(false, Reason.NOT_CONFIGURED, messageFor(Reason.NOT_CONFIGURED, point, null)))
            return
        }
        if (!hasPermission(activity)) {
            onResult(Outcome(false, Reason.NO_PERMISSION, messageFor(Reason.NO_PERMISSION, point, null)))
            return
        }

        val lm = try {
            activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (_: Throwable) { null }
        if (lm == null) {
            onResult(Outcome(false, Reason.ERROR, messageFor(Reason.ERROR, point, null)))
            return
        }
        if (!locationEnabled(lm)) {
            onResult(Outcome(false, Reason.LOCATION_OFF, messageFor(Reason.LOCATION_OFF, point, null)))
            return
        }

        var done = false
        val handler = Handler(Looper.getMainLooper())
        var listener: LocationListener? = null

        fun stop() {
            try { listener?.let { lm.removeUpdates(it) } } catch (_: Throwable) { }
        }
        fun finish(r: Outcome) {
            if (done) return
            done = true
            stop()
            handler.removeCallbacksAndMessages(null)
            try { activity.runOnUiThread { onResult(r) } } catch (_: Throwable) { onResult(r) }
        }

        fun judge(loc: Location?) {
            if (loc == null) return
            if (isMock(loc)) { finish(Outcome(false, Reason.MOCK_DETECTED, messageFor(Reason.MOCK_DETECTED, point, null))); return }
            val acc = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE
            if (acc > MAX_ACCURACY_M) return          // আরও ভালো ফলের অপেক্ষা
            val target = Location("clinic").apply {
                latitude = point.lat!!; longitude = point.lng!!
            }
            val d = loc.distanceTo(target).toInt()
            if (d <= point.radiusMeters) {
                finish(Outcome(true, Reason.INSIDE, "", d))
            } else {
                finish(Outcome(false, Reason.OUTSIDE, messageFor(Reason.OUTSIDE, point, d), d))
            }
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { judge(location) }
            @Deprecated("পুরনো Android-এ দরকার")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
            override fun onProviderEnabled(provider: String) { }
            override fun onProviderDisabled(provider: String) { }
        }

        try {
            // দুটো উৎসেই চাওয়া হয় — ঘরের ভিতরে NETWORK আগে আসতে পারে।
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
            }
            // সদ্য জানা অবস্থান থাকলে সেটাও দেখা হয় (দ্রুত ফল) — তবে
            // ২ মিনিটের বেশি পুরনো হলে নয়, নইলে বাড়ির অবস্থান কাজে লেগে যেত।
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                val last = try {
                    if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null
                } catch (_: SecurityException) { null }
                if (last != null && System.currentTimeMillis() - last.time <= 120_000L) judge(last)
                if (done) return
            }
        } catch (_: SecurityException) {
            finish(Outcome(false, Reason.NO_PERMISSION, messageFor(Reason.NO_PERMISSION, point, null))); return
        } catch (_: Throwable) {
            finish(Outcome(false, Reason.ERROR, messageFor(Reason.ERROR, point, null))); return
        }

        handler.postDelayed({
            // সময় শেষ — কিছুই আসেনি, নাকি এসেছে কিন্তু খুব অনিশ্চিত ছিল।
            finish(Outcome(false, Reason.TIMEOUT, messageFor(Reason.TIMEOUT, point, null)))
        }, TIMEOUT_MS)
    }
}
