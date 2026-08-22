package com.tkbiswas.pilesclinic.native

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cloud data backup as JSON, ported from the WebView's buildBackupPayload /
 * scheduleAutoBackup. Writes every shared table into a single JSON file under
 * the app's external files dir, and auto-runs once a day.
 */
object CloudBackup {

    private val TABLES = listOf(
        "enquiries", "patients", "payments", "followups", "medical", "doctor_visits", "briefings"
    )

    private fun dir(context: Context): File = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }

    fun latest(context: Context): File? =
        dir(context).listFiles { f -> f.name.startsWith("cloud_backup_") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }

    /** Fetches all tables and writes a JSON backup. Returns the file path or null. */
    suspend fun export(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("generatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            root.put("type", "manual")
            for (t in TABLES) root.put(t, SupabaseClient.fetchList(t, null, 100000))
            val f = File(dir(context), "cloud_backup_${System.currentTimeMillis()}.json")
            f.writeText(root.toString())
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Controlled auto-backup: it NEVER starts a full cloud download on a fresh
     * install. A manual "Backup Now" must create the first local cloud_backup_*.json
     * seed. After that, the existing weekly safety backup may run when stale.
     * Safe to call on every app open because the fresh-install path is file-only
     * and returns before any Supabase read.
     *
     * TK-REPORTED (2026-07-27, "slow internet" list item S2).
     *
     * WHAT WAS WRONG
     * This runs by itself every time the Dashboard is opened, and export()
     * downloads SEVEN whole tables. On a slow line that download sits on the
     * same thin connection the staff is trying to work on, so for as long as
     * it lasts, every screen in the app crawls -- and the staff has no idea
     * why, because nothing on screen says a backup is happening. It also
     * spends Supabase's free-plan quota in the background.
     *
     * WHAT IS DIFFERENT NOW
     *  * The whole check now happens on a background thread. Previously even
     *    the "is there a recent backup?" file lookup ran on the main thread,
     *    which can stutter the Dashboard on its own.
     *  * After a manual seed exists, automatic backup is due at 7 days.
     *  * On Wi-Fi/unmetered data it may run once due; on metered/mobile data it
     *    waits until 14 days before forcing one safety backup.
     *  * "Backup Now" in Settings is always available and unchanged.
     */
    suspend fun exportIfStale(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val dayMs = 24 * 60 * 60 * 1000L
                val newest = latest(context)

                // 🟢 V452 WORKING FIX (19.08.2026, TK-approved, Free Plan safety):
                // Fresh install / reinstall has no app-specific backup file because Android
                // removes getExternalFilesDir() with the old app. Previously `newest == null`
                // became Long.MAX_VALUE, which made BOTH `due` and the 14-day `allowed`
                // fallback true; simply opening Master Dashboard could therefore download
                // all seven cloud tables (including base64 patient photos) immediately.
                //
                // New rule: NO local seed = NO automatic full-cloud backup. The Master can
                // still use Settings → Backup Now exactly as before; that manual action writes
                // cloud_backup_*.json. Once a real manual seed exists, the proven weekly
                // controlled backup below resumes. This adds zero Supabase reads/writes and
                // changes no patient/payment/sync/restore workflow.
                if (newest == null) return@withContext

                val ageMs = System.currentTimeMillis() - newest.lastModified()
                // 🟢 B624 (Egress ফিক্স, 11.08.2026, TK-নির্দেশ): আগে প্রতি ২৪ ঘণ্টায় পুরো
                // ডেটাবেস (ছবি-সহ) আবার নামত — Free-plan egress-এর বড় খরচ। এখন **সপ্তাহে
                // একবার** (৭ দিন)। ক্লাউডই মূল ভাণ্ডার, তাই সপ্তাহে একটা লোকাল ব্যাকআপই যথেষ্ট।
                val due = ageMs > 7 * dayMs
                // চালানোর অনুমতি: WiFi থাকলে, নইলে ১৪ দিন পার হলে যেভাবেই হোক একবার।
                val allowed = ageMs >= 14 * dayMs || connectionCanSpareIt(context)
                if (due && allowed) export(context)
            } catch (_: Throwable) {
                // Auto-backup is a background convenience -- it must never
                // affect anything the staff is doing.
            }
        }
    }

    /**
     * True when the connection can carry a full backup without getting in the
     * staff's way -- i.e. Wi-Fi / any unmetered network. Anything else (mobile
     * data, or if this cannot be determined at all) is treated as "not now",
     * which only ever postpones the backup, never cancels it (see the 7-day
     * rule above).
     */
    private fun connectionCanSpareIt(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Throwable) {
            false
        }
    }
}
