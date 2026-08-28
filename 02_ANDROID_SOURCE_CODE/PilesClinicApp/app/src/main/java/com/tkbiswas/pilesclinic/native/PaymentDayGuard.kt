package com.tkbiswas.pilesclinic.native

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/**
 * 🔒 খাতার সারি B52 (TK, 28.07.2026 রাত) — একই দিনে দ্বিতীয়বার টাকা নেওয়ার আগে সতর্কবার্তা
 *
 * TK-এর কথা: *"কোন এক স্টাফ কোন এক পেশেন্টের পেমেন্ট নিতে ভুল করে ৩-৪ বার নিয়ে
 * নিয়েছে... হয়তো তখন ইন্টারনেট স্লো ছিল, বুঝতে পারেনি যে পেমেন্ট হয়ে গেছে...
 * প্রয়োজনে স্টাফকে সেইভাবে ওয়ার্নিং দেবে।"*
 *
 * TK-এর সিদ্ধান্ত (28.07.2026): টাকাটা **সোজা যোগ হবে না** — আগে স্টাফকে
 * জিজ্ঞাসা করা হবে, তিনি "নতুন টাকা" বললে তবেই যোগ হবে।
 *
 * 🔒 V452 (19.08.2026, TK-approved A): **কোনো দ্বিতীয় daily payment row তৈরি হবে না**।
 *    "Add to today's payment" বললে genuine new money ওই দিনের existing Treatment
 *    Payment-এ যোগ হবে; Cash/Online split আলাদা থাকবে। "No" বললে কিছুই হয় না।
 * ⛔ **আজ কিছু নেওয়া না হয়ে থাকলে কোনো পপ-আপই আসে না** — স্টাফের রোজকার
 *    কাজে একটাও বাড়তি ট্যাপ পড়ে না।
 * ⛔ **ক্লাউডে বাড়তি কোনো অনুরোধ নেই** — অঙ্কটা যে তালিকা আগেই নামানো হয়েছে
 *    তা থেকেই আসে (`PaymentRepository.paidOnDateFor`)।
 * ⛔ অ্যাপের নিয়ম মেনে লেখা **শুধু ইংরেজিতে**।
 */
object PaymentDayGuard {

    /**
     * @param alreadyPaid ওই রোগীর নামে আজ ইতিমধ্যে নেওয়া মোট টাকা (০ হলে সোজা [onProceed])।
     * @param patientName পর্দায় দেখানোর নাম।
     * @param todayLabel আজকের দিনটা কোন নম্বরে পড়ছে (`Advance` / `2nd Payment` …)।
     * @param onProceed স্টাফ "Add to today's payment" বললে existing daily payment-এ
     * new money যোগ করার একই save path চলবে।
     */
    fun confirmIfAlreadyPaidToday(
        activity: Activity,
        alreadyPaid: Double,
        patientName: String,
        todayLabel: String,
        onProceed: () -> Unit
    ) {
        if (alreadyPaid <= 0.0) { onProceed(); return }
        if (activity.isFinishing || activity.isDestroyed) return
        val who = patientName.ifBlank { "this patient" }
        // অ্যাপের সব জায়গার মতোই একই ধাঁচ — `₹10,000`।
        val amt = "₹" + "%,.0f".format(alreadyPaid)
        /* 🟡🔒 V814 (২৮.০৮.২০২৬, TK-নির্দেশ, ছবিসহ: *"এত বড় মেসেজ কেন?
           শর্টকাট দিলে ভালো হয়"*) — লেখাটা চার অনুচ্ছেদ থেকে **দুই লাইনে**।
           ⛔ যা জানার দরকার তার একটাও হারায়নি: কত টাকা · কার · কত নম্বর
              পেমেন্ট · আর প্রশ্নটা কী। বাকি ব্যাখ্যা (দ্বিতীয় সারি হবে না,
              CASH/ONLINE আলাদা গোনা) — ওটা নিয়মেই আছে, প্রতিবার পড়ার দরকার নেই।
           ⛔ কাজের নিয়ম এক অক্ষরও বদলায়নি, শুধু লেখা ছোট হলো। */
        val msg = "$amt already taken from $who TODAY ($todayLabel).\n\n" +
            "Add this as NEW money to today's payment?"
        AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "Already paid today"))
            .setMessage(msg)
            .setPositiveButton("Add to today's payment") { _, _ -> onProceed() }
            .setNegativeButton("No, cancel", null)
            .setCancelable(false)
            .show().also { PremiumAlert.paint(it) }
    }
}
