package com.tkbiswas.pilesclinic.native

import android.content.Context

/**
 * 🆕 B419 (04.08.2026, TK-নির্দেশ — "যদি কেউ ভুল করে চেম্বার বন্ধ করে দেয়,
 * তাহলে আবার খোলা যাবে কি করে?"):
 *
 *   *"হ্যাঁ, তবে Master শুধু নয় — চাইলে সবাই খুলতে পারে, তবে Master-এর
 *    অনুমতি নিতে হবে।"*
 *
 * ─────────────────────────────────────────────────────────────
 * **কেন এই ফাইল লাগল:** এতদিন `ChamberCloseRepository.markClosed()` ছিল,
 * কিন্তু সেটা ফেরানোর (reopen) কোনো পথ ছিলই না — একবার বন্ধ হলে ওই দিন
 * চিরস্থায়ীভাবে শুধু-দেখার (read-only) থেকে যেত। এটা `DeletePermission.kt`-এর
 * হুবহু প্রমাণিত ধাঁচ পুনর্ব্যবহার করে — নতুন টেবিল লাগেনি, স্টাফের অনুরোধ
 * সরাসরি Master-এর ঘন্টায় (`briefings`) যায়, Master এক-চাপে Approve/Reject।
 *
 * ⛔ **যা ছোঁয়া হয়নি:** Close Chamber-এর আসল বন্ধ-করার লজিক
 *    (`ChamberCloseRepository.markClosed`), ডিলিট/রিফান্ড-এর অনুরোধ-পথ —
 *    এই ফাইলটা শুধু একটা নতুন, সমান্তরাল অনুরোধ-পথ।
 */
object ChamberReopenPermission {

    /** স্টাফ Reopen চাইলে Master-এর ঘন্টায় অনুরোধ পাঠায়। কিছুই খোলে না এখনই। */
    fun sendRequest(
        context: Context,
        user: NativeUser,
        branch: String,
        date: String
    ): Boolean {
        return try {
            if (branch.isBlank() || date.isBlank()) return false
            val sb = StringBuilder()
            sb.append("Branch : ").append(branch).append("\n")
            /* 🔴🔒 V936 — TK-রিপোর্ট (ছবিসহ): এই লাইনটায় কাঁচা `2026-08-31`
               দেখাত, অথচ প্রজেক্টের বাকি সব জায়গায় `31.08.2026`। এখন দেখার
               লেখাটা ঠিক, আর নিচের `approveAndReopen()` সেটা `DateUtil.iso()`
               দিয়ে ফিরিয়ে পড়ে — তাই Approve আগের মতোই কাজ করে।
               ⛔ V1537 (১৮.০৯.২০২৬, TK-নির্দেশ, ফটো-প্রুফ পাশ, "তারিখের পাশে
               সময় থাকবে") — এখন সময়টা এই একই লাইনে জুড়ে বসে ("·"-এর পরে)।
               `DateUtil.iso()`-এর regex লাইনের **শুরু**র dd.MM.yyyy অংশটুকুই
               পড়ে, পরের যেকোনো লেখা (এই সময়সহ) চুপচাপ বাদ যায় — তাই Approve
               বোতাম আগের মতোই কাজ করে, ভেঙে যায় না। আগে আলাদা "Chamber reopen
               request"/"Requested by"/"Requested at"/Master-বাক্য লাইনগুলো
               ছিল (শিরোনাম ও নিচের "By ..."-এর সাথে ডুপ্লিকেট) — TK বললেন
               সেগুলো লাগবে না। */
            sb.append("Date : ").append(DateUtil.display(date))
                .append(" · ").append(DateUtil.displayWithTime(java.util.Date()).substringAfter(" : "))
            // 🔴🔒 V1604 (১৯.০৯.২০২৬, TK-রিপোর্ট, ছবিসহ) — শিরোনামে তারিখ জোড়া
            // ছিল (নিচের "Date :" লাইন ও কার্ডের উপরের সারির সাথে ডুপ্লিকেট) —
            // সেটা সরানো হলো। TK আবার ধরলেন: ব্রাঞ্চের নামও শিরোনামে + বডির
            // "Branch :" লাইনে + কার্ডের উপরের সারিতে — তিন জায়গায়। কার্ডের
            // উপরের সারিটা (ব্রাঞ্চ+সময়) **প্রতিটা** নোটিশেই এমনিতে বসে
            // (BriefingAdapter-এর সাধারণ নিয়ম, এই একটা নোটিশ-ধরনের নয়), তাই
            // ছোঁয়া হয়নি। বডির "Branch :"/"Date :" লাইন দুটো সরানো যাবে না —
            // Master "Reopen" চাপলে ঠিক এই লাইন দুটো থেকেই approveAndReopen()
            // ব্রাঞ্চ/তারিখ পড়ে (নিচে দেখুন), সরালে Reopen বোতামই ভেঙে যাবে।
            // তাই এখন শিরোনাম সম্পূর্ণ সাধারণ — Refund/Delete/Leave request-এর
            // শিরোনামের হুবহু একই ধাঁচ (ওগুলোতেও ব্রাঞ্চ/তারিখ থাকে না, শুধু
            // "কী অনুরোধ")। ব্রাঞ্চ এখন শুধু উপরের সারি ও বডির "Branch :"
            // লাইনে — একবার প্রদর্শনী, একবার কার্যকরী।
            BriefingRepository().post(
                context,
                "🔓 Chamber reopen request",
                sb.toString(),
                "role",
                branch,
                "master",
                user.mobile
            )
        } catch (_: Throwable) { false }
    }

    /**
     * Master-এর "Approve" চাপার মুহূর্তে চলে। অনুরোধের লেখা থেকেই Branch/Date
     * পড়ে নেওয়া হয় (`sendRequest()`-এর লেখাই এই ফরম্যাট নিশ্চিত করে)।
     * ⛔ Branch/Date পড়া না গেলে **কিছুই খোলে না**, স্পষ্ট বার্তা যায়।
     */
    fun approveAndReopen(context: Context, message: String, masterMobile: String): String {
        return try {
            fun field(key: String): String {
                for (line in message.split("\n")) {
                    val t = line.trim()
                    if (t.startsWith("$key :")) return t.substringAfter("$key :").trim()
                }
                return ""
            }
            val branch = field("Branch")
            /* 🔴🔒 V936 — লেখাটা এখন `31.08.2026`, কিন্তু চেম্বার-বন্ধের চাবি
               `yyyy-MM-dd`। তাই এখানে ফিরিয়ে নেওয়া হয়। পুরনো (কাঁচা ISO লেখা)
               অনুরোধেও `iso()` সেটাই ফেরত দেয় — কিছুই ভাঙে না। */
            val date = DateUtil.iso(field("Date"))
            if (branch.isBlank() || date.isBlank()) return "BAD_REQUEST"
            if (ChamberCloseRepository.reopen(context, branch, date)) "OK" else "NETWORK"
        } catch (_: Throwable) { "NETWORK" }
    }
}
