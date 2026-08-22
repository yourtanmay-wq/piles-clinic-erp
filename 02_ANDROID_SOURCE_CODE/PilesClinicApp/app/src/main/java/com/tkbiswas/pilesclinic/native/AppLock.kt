package com.tkbiswas.pilesclinic.native

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity

/**
 * 🔒 V499 (২১.০৮.২০২৬, TK-এর সিদ্ধান্ত) — **অ্যাপ খোলার তালা।**
 *
 * ─── TK-এর নিয়ম (হুবহু) ───────────────────────────────────────────────────
 *  > *"একদম সিম্পল রুল — ফিঙ্গারপ্রিন্ট খুলবে, আবার পাসওয়ার্ড দিলেও খুলবে।
 *  >  যেটা বর্তমান যুগের সব ফোনেই একই রকম ব্যবস্থা আছে… ফিঙ্গারপ্রিন্টে
 *  >  কাজ না করলে সে পাসওয়ার্ড দিতে পারবে সেরকম ব্যবস্থা রাখুন।"*
 *  > *"এটা শুধু মাস্টারের ক্ষেত্রে নয় — যারা যারা ব্যবহার করে প্রত্যেকের
 *  >  ক্ষেত্রেই একই নিয়ম।"*
 *
 * ⇒ **Master · Doctor · Staff · Field — সবার জন্য একই।**
 * ⇒ Android-এর নিজের চেনা পর্দাটাই খোলে (আঙুল না মিললে নিচে "Use PIN /
 *   Password")। অ্যাপ আলাদা কোনো পাসওয়ার্ড রাখে না, দেখেও না।
 *
 * ─── কখন তালা লাগে ────────────────────────────────────────────────────────
 * অ্যাপ পুরোপুরি পিছনে চলে গেলে (সব পর্দা বন্ধ) তালা লাগে; পরের বার সামনে
 * আনলে চাইবে। অ্যাপের ভিতরে এক পর্দা থেকে আরেক পর্দায় গেলে চায় না।
 *
 * ─── ⚠️ যে ফোনে স্ক্রিন-লকই নেই ───────────────────────────────────────────
 * ফোনে না আছে আঙুল, না আছে PIN/প্যাটার্ন — তখন Android-এর কাছে চাওয়ার
 * মতো কিছুই নেই। তখন **অ্যাপ খোলে, কিন্তু চুপচাপ নয়** — প্রতিবার একটা
 * স্পষ্ট বার্তা দেখায়, আর ফোনের Settings খুলে দেওয়ার বোতাম থাকে।
 * ⛔ এখানে আটকে দিলে ব্যবহারকারী **চিরতরে বাইরে** থেকে যেতেন, কারণ ঢোকার
 *    কোনো পথই থাকত না — সেটা তালা নয়, ফাঁদ হত।
 *
 * ─── ⛔ হাজিরার সঙ্গে গুলিয়ে ফেলবেন না ────────────────────────────────────
 * এই তালা **শুধু অ্যাপ খোলার**। হাজিরা (IN TIME) বসে একমাত্র
 * `wn.mark_check_in()` দিয়ে — সার্ভার নিজে সময় ও ভূমিকা ঠিক করে।
 * V500 থেকে হাজিরার পর্দাতেও **একই নিয়ম** (আঙুল অথবা ফোনের পাসওয়ার্ড),
 * TK-এর স্পষ্ট সিদ্ধান্তে। তবে হাজিরায় বাড়তি একটা শর্ত আছে —
 * **ক্লিনিকে উপস্থিত থাকতে হবে** (`ClinicPresence`, GPS)।
 *
 * ─── 🔓 সৎ সীমাবদ্ধতা ─────────────────────────────────────────────────────
 *  • ফোনের PIN যে জানে, সে অ্যাপও খুলতে পারবে — এটাই TK-এর চাওয়া নিয়ম।
 *  • তালাটা এই অ্যাপের ভিতরে; অ্যাপ মুছে দিলে তালাও যায়।
 */
object AppLock {

    @Volatile private var locked = true          // অ্যাপ চালু হলে প্রথমেই তালা
    @Volatile private var asking = false
    @Volatile private var warnedThisRun = false

    /** অ্যাপ পুরোপুরি পিছনে গেছে — আবার তালা। */
    fun onAppBackgrounded() {
        locked = true
        warnedThisRun = false
    }

    /** লগআউটের সময় — পরের জনের জন্য আবার তালা। */
    fun reset() {
        locked = true
        asking = false
        warnedThisRun = false
    }

    fun isLocked(): Boolean = locked

    /** দরকার হলে আঙুল/পাসওয়ার্ড চায়। **সব ভূমিকার জন্য একই।** */
    fun guard(activity: Activity, user: NativeUser?) {
        if (user == null) return
        if (!locked || asking) return
        val act = activity as? AppCompatActivity ?: return

        val ready = BiometricGate.unlockAvailability(act)
        if (ready != BiometricGate.Reason.SUCCESS) {
            // ফোনে চাওয়ার মতো কিছুই নেই — আটকালে ঢোকার পথই থাকত না।
            locked = false
            if (!warnedThisRun) {
                warnedThisRun = true
                showNoLockNotice(act, BiometricGate.messageFor(ready))
            }
            return
        }

        asking = true
        // 🔤 V509 (২১.০৮.২০২৬, TK-নির্দেশ — ছবিসহ: "এই ধরনের বাংলা থাকবে না"):
        // তালার পর্দার সব লেখা এখন ইংরেজি। ⛔ শুধু লেখা — নিয়ম অপরিবর্তিত।
        BiometricGate.promptUnlock(
            act,
            "Unlock App",
            "Use your fingerprint, or your phone password"
        ) { res ->
            asking = false
            if (res.ok) locked = false else showBlocked(act, res.message)
        }
    }

    /** স্ক্রিন-লক নেই — অ্যাপ খুলল, কিন্তু জানিয়ে দেওয়া হলো। */
    private fun showNoLockNotice(act: AppCompatActivity, why: String) {
        try {
            val b = androidx.appcompat.app.AlertDialog.Builder(act)
                .setCustomTitle(PremiumAlert.header(act, "App Lock Is Off"))
                .setMessage(
                    "This phone has no fingerprint and no screen lock, so the app opened directly. " +
                        "For safety, please turn on a screen lock in the phone Settings." +
                        "\n\n" + why)
                .setPositiveButton("OK", null)
                .setNegativeButton("Open Settings") { _, _ ->
                    BiometricGate.openEnrollSettings(act)
                }
            PremiumAlert.paint(b.show())
        } catch (_: Throwable) { }
    }

    /** মেলেনি বা বাতিল করেছেন — আবার চেষ্টা, নয়তো অ্যাপ বন্ধ। */
    private fun showBlocked(act: AppCompatActivity, message: String) {
        try {
            val b = androidx.appcompat.app.AlertDialog.Builder(act)
                .setCustomTitle(PremiumAlert.header(act, "Unlock App"))
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Try Again") { _, _ ->
                    asking = false
                    guard(act, NativeSession.current(act))
                }
                .setNegativeButton("Close App") { _, _ ->
                    try { act.finishAffinity() } catch (_: Throwable) { act.finish() }
                }
            PremiumAlert.paint(b.show())
        } catch (_: Throwable) {
            asking = false
            try { act.finishAffinity() } catch (_: Throwable) { }
        }
    }
}
