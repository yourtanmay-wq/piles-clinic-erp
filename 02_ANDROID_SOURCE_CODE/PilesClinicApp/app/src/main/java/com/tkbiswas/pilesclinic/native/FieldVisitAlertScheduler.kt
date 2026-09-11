package com.tkbiswas.pilesclinic.native

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 🛰️🔒 V1345 (১১.০৯.২০২৬, TK-নির্দেশ — "মাস্টার-অ্যালার্ট যোগ করুন") —
 * `FieldVisitAlertWorker`-কে **দুপুর ৩টায়** বসানোর সময়সূচি।
 *
 * TK-র নিজের কথা: ব্যাটারি-সেভার/অনুমতির কারণে কোনো ফিল্ড-স্টাফের লোকেশন
 * কাজ না করলে তিনি এতদিন শুধু রাতে/পরে ধরতে পারতেন, ততক্ষণে ওই দিনটা চলে
 * যেত। ⇒ দুপুর ৩টায় (কাজের দিনের মাঝামাঝি, তখনো ফোন করে ঠিক করানোর সময়
 * থাকে) একবার চেক — `MasterOutTimeScheduler`-এর হুবহু একই প্রমাণিত চেইন।
 * ⛔ `MasterOutTimeScheduler`/`MasterOutTimeWorker`-এ এতটুকুও হাত পড়েনি —
 *    এটা সম্পূর্ণ আলাদা, স্বাধীন চেইন।
 */
object FieldVisitAlertScheduler {

    private const val WORK_NAME = "field_visit_location_alert"
    private const val HOUR = 15   // দুপুর ৩টা

    fun scheduleNext(context: Context) {
        try {
            val now = Calendar.getInstance()
            val at = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!at.after(now)) at.add(Calendar.DAY_OF_YEAR, 1)
            val delay = at.timeInMillis - now.timeInMillis
            val req = OneTimeWorkRequestBuilder<FieldVisitAlertWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        } catch (_: Throwable) { }
    }
}
