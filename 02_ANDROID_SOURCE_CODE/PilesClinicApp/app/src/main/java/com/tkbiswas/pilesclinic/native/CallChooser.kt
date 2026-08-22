package com.tkbiswas.pilesclinic.native

import android.app.Activity
import android.content.Intent
import android.net.Uri

// TK-REQUESTED (2026-07-24): "wherever calling is possible anywhere in the
// project" -- this was first built and proven inline in
// PatientTimelineActivity.kt (section 104 of the master note), but only
// applied there. TK pointed out it must be EVERY place in the app that
// starts a phone call, not just one screen. This shared helper is the
// SAME proven logic, extracted so every call site can use the exact same
// behaviour with one line, instead of each screen duplicating (and
// potentially drifting from) the same ~15 lines of chooser-building code.
//
// Always shows the app-chooser (Intent.createChooser), even when the
// phone has a default dialer set, so Phone/Superfone/anything else
// installed all show up as options every time -- Truecaller specifically
// excluded (filtered by its package name, "com.truecaller").
object CallChooser {
    fun open(activity: Activity, mobileDigitsOrPlain: String) {
        // V245 additive — count in-app Call-button presses (owner rule 8). This
        // ONLY records the press; it never changes the call, never claims the
        // call connected, and shows no duration.
        // 🔴 B406 (04.08.2026) — এখন module সেশন না থাকলেও নিজে থেকে সাইন-ইন
        // করে গোনায়, তাই context পাঠানো হচ্ছে (dialer খোলায় কোনো দেরি হয় না)।
        com.tkbiswas.pilesclinic.modules.ModuleAuth.logCallTap(mobileDigitsOrPlain.filter { it.isDigit() }, activity)
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobileDigitsOrPlain"))
        val resolveInfos = activity.packageManager.queryIntentActivities(dialIntent, 0)
            .filterNot { it.activityInfo.packageName == "com.truecaller" }
        if (resolveInfos.isEmpty()) {
            activity.startActivity(Intent.createChooser(dialIntent, "Call with"))
        } else {
            val targeted = resolveInfos.map { ri ->
                Intent(dialIntent).apply { setPackage(ri.activityInfo.packageName) }
            }.toMutableList()
            val chooser = Intent.createChooser(targeted.removeAt(0), "Call with").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, targeted.toTypedArray())
            }
            activity.startActivity(chooser)
        }
    }
}
