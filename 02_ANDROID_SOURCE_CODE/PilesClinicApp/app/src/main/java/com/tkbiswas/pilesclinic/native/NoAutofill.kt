package com.tkbiswas.pilesclinic.native

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View

/**
 * 🔵🔒 V418 (TK-নির্দেশ, ১৭.০৮.২০২৬) — *"এখানে অটো সাজেশ কেন থাকবে"*
 *
 * সমস্যা: "Add Collection"-এর Cash ঘরে চাপ দিলে ফোনের নিজের **Autofill**
 * পুরনো সেভ করা লেখা (যেমন মোবাইল নম্বর 9002003540 / 8001080080) সাজেশন
 * হিসেবে দেখাচ্ছিল। ওটা অ্যাপের নিজের সাজেশন নয় — Android-এর/Google-এর
 * Autofill সেবা। টাকার ঘরে ভুল করে ফোন নম্বর বসে যাওয়ার আসল ঝুঁকি ছিল।
 *
 * সমাধান: প্রতিটা পর্দার **মূল বাক্সে** একবার বলে দেওয়া হয় — "এখানে Autofill
 * লাগবে না"। `NO_EXCLUDE_DESCENDANTS` মানে ওই পর্দার **ভিতরের সব ঘরেই**
 * নিয়মটা খাটে, তাই ৩৪টা পর্দার একটাতেও আলাদা কোড লাগেনি, আর ভবিষ্যতে নতুন
 * পর্দা যোগ হলেও নিজে থেকেই ঢেকে যাবে।
 * (`NoBengali.hookApp` ঠিক এই প্রমাণিত পথেই বসানো আছে।)
 *
 * ⛔ কীবোর্ড, টাইপ করা, সেভ করা — কিছুই বদলায় না। শুধু ফোনের নিজের পুরনো
 *    লেখা আর ভেসে ওঠে না।
 * ⛔ Android 8 (API 26)-এর আগে Autofill নেই, তাই সেখানে কিছুই করা হয় না।
 * ⛔ পুরোটা try/catch-এ মোড়া — এখানে কিছু ভুল হলেও কোনো পর্দা ভাঙবে না।
 */
object NoAutofill {

    fun hookApp(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { apply(activity) }
            override fun onActivityResumed(activity: Activity) { apply(activity) }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** পর্দার মূল বাক্স খুঁজে নিয়ে Autofill বন্ধ করে দেয়। */
    private fun apply(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val root = activity.findViewById<View>(android.R.id.content) ?: return
            if (root.importantForAutofill != View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) {
                root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        } catch (_: Throwable) {}
    }
}
