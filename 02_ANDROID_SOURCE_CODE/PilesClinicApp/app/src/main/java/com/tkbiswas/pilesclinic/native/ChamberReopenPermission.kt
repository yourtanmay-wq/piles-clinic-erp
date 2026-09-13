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
            val who = StaffDirectory.findAccount(user.mobile)?.name ?: user.mobile
            val sb = StringBuilder()
            sb.append("Chamber reopen request\n")
            sb.append("Branch : ").append(branch).append("\n")
            /* 🔴🔒 V936 — TK-রিপোর্ট (ছবিসহ): এই লাইনটায় কাঁচা `2026-08-31`
               দেখাত, অথচ প্রজেক্টের বাকি সব জায়গায় `31.08.2026`। এখন দেখার
               লেখাটা ঠিক, আর নিচের `approveAndReopen()` সেটা `DateUtil.iso()`
               দিয়ে ফিরিয়ে পড়ে — তাই Approve আগের মতোই কাজ করে। */
            sb.append("Date : ").append(DateUtil.display(date)).append("\n")
            sb.append("Requested by : ").append(who).append("\n")
            /* 🔴🔒 V936 — TK: *"তারিখের পাশে সময় থাকা জরুরী"*। অনুরোধটা **কখন**
               এলো সেটা এই লাইনেই। ⛔ কোনো কোড এই লাইন পড়ে না, তাই ঝুঁকি নেই। */
            sb.append("Requested at : ").append(DateUtil.displayWithTime(java.util.Date())).append("\n")
            sb.append("\nMaster: অনুমোদন দিলে এই দিনের চেম্বার আবার এডিটযোগ্য হয়ে যাবে।")
            BriefingRepository().post(
                context,
                "🔓 Chamber reopen request — $branch " + FollowUpModel.displayDate(date),
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
