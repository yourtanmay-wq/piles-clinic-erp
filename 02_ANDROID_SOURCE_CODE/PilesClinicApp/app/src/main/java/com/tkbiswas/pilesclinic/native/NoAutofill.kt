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
            scrub(root)
        } catch (_: Throwable) {}
    }

    /**
     * 🔴🔒 V752 (২৭.০৮.২০২৬, TK-রিপোর্ট ছবিসহ: *"এরকম যেন সাজেস্ট না করে"*)
     *
     * **আসল কারণ (কোড ধরে যাচাই):** V418-এ শুধু পর্দার মূল বাক্সে
     * `importantForAutofill` বসানো হয়েছিল — সেটা **Google-এর Autofill**
     * থামায়, কিন্তু **কীবোর্ডের নিজের সাজেশন** থামায় না। ওটা থামাতে
     * প্রতিটা ঘরে আলাদা করে `IME_FLAG_NO_PERSONALIZED_LEARNING` লাগে।
     * এতদিন সেটা **শুধু মোবাইলের ঘরে** ছিল (`MobileInput.attach`), তাই
     * নাম · টাকা · অন্য সব ঘরে পুরনো লেখা ভেসে উঠত।
     *
     * এখন পর্দার **প্রতিটা লেখার ঘরে** একবার করে বসিয়ে দেওয়া হয়।
     * ⛔ টাইপ করা · সেভ · কীবোর্ডের ভাষা — কিছুই বদলায় না। `inputType`-এ
     *    হাত দেওয়া হয়নি (নইলে বাংলা লেখার সুবিধা নষ্ট হত)।
     * ⛔ অ্যাপের নিজের সাজেশন (যেমন "Select Saved RMP") অক্ষত — সেগুলো
     *    কীবোর্ডের নয়, অ্যাপের নিজের তালিকা।
     * ⚠️ সৎ কথা: `NO_PERSONALIZED_LEARNING` একটা **অনুরোধ** — প্রায় সব
     *    কীবোর্ড মানে, কিন্তু কোনো কীবোর্ড না মানলে অ্যাপের আর কিছু করার নেই।
     */
    private fun scrub(v: View) {
        if (v is android.widget.EditText) {
            try {
                v.imeOptions = v.imeOptions or IME_FLAG_NO_PERSONALIZED_LEARNING
                v.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                v.setAutofillHints(null as String?)
            } catch (_: Throwable) {}
            return
        }
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) {
                try { scrub(v.getChildAt(i)) } catch (_: Throwable) {}
            }
        }
    }

    /** `android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` */
    private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000

    /**
     * 🪟 V752 — **পপ-আপের নিজের আলাদা উইন্ডো থাকে**, তাই পর্দার পাহারা
     * ওখানে পৌঁছায় না (TK-এর ছবিতে "Cash" ঘরে সাজেশন — ঠিক এই কারণেই)।
     * `PremiumAlert.paint()` প্রজেক্টের **সব** পপ-আপে ডাকা হয়, তাই সেখান
     * থেকে একবার এটা ডাকলেই সবক'টা ঢেকে যায়।
     * ⛔ কখনো ব্যতিক্রম ছোড়ে না — পপ-আপ ভাঙার ঝুঁকি নেই।
     */
    fun scrubDialogWindow(window: android.view.Window?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val root = window?.decorView ?: return
            root.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            scrub(root)
        } catch (_: Throwable) {}
    }
}
