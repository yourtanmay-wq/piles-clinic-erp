package com.tkbiswas.pilesclinic.native

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ⏰🔒 V1277 (০৯.০৯.২০২৬, TK-রিপোর্ট হুবহু: *"এখানে সময় মতো নোটিফিকেশন
 * আসেই না … আরে একই সমস্যার কথা আপনাকে বেশ কয়েকবার আমার বলা হয়ে গেছে
 * তারপরও আপনি কেন ঠিক করেন নাই"* — তালিকা সারি ৪০০; আগেরটা সারি ৩৬১)।
 *
 * 🔴 **আসল কারণ (কোডে মেপে, দোষটা আমার):** V1241-এ ফোনের অ্যালার্ম ঘড়ি
 * (`setAlarmClock`) বসানো হয়েছিল — কিন্তু **Android ফোন রিস্টার্ট হলে
 * বসানো সব অ্যালার্ম মুছে যায়**, আর প্রকল্পে সেগুলো আবার বসানোর
 * কোনো ব্যবস্থাই ছিল না (পুরো প্রজেক্টে `BOOT_COMPLETED` একটাও নেই — গোনা
 * হয়েছে)। ⇒ ডাক্তারের ফোন একবার বন্ধ-চালু হলেই ঠিক-সময়ের ঘণ্টা চিরতরে
 * চলে যেত; শুধু পিছনের ১৫-মিনিটের জালটা থাকত, যেটা দেরিতে বাজে।
 *
 * ⇒ এখন ফোন চালু হওয়ার সঙ্গে সঙ্গেই কাজটা একবার চালানো হয়; সে নিজেই
 *   সবচেয়ে কাছের সময়ের জন্য অ্যালার্মটা আবার বসিয়ে নেয়।
 *
 * ⛔ **নতুন কোনো নিয়ম বা ক্লাউড-কল যোগ হয়নি** — যে কাজটা এমনিতেই প্রতি
 *    ১৫ মিনিটে চলে, ঠিক সেটাই একবার ডাকা হয় (নিয়ম ১৩, ফ্রি প্ল্যান)।
 * ⛔ কিছু ভাঙলেও চুপচাপ সরে যায় (try-catch) — ফোন চালু হওয়া কখনো আটকায় না।
 */
class BootRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val a = intent?.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val ctx = context?.applicationContext ?: return
        // ⏰ অ্যালার্মটা আবার বসানোর জন্য কাজটা একবার চালানো
        try { DoctorReminderScheduler.runNow(ctx) } catch (_: Throwable) { }
        // ⛔ পিছনের ১৫-মিনিটের জালটাও আবার শুরু করা হয় (আগের মতোই)
        try { DoctorReminderScheduler.scheduleNext(ctx) } catch (_: Throwable) { }
    }
}
