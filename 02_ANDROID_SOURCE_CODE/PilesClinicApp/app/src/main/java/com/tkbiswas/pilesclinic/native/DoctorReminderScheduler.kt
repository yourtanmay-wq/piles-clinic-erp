package com.tkbiswas.pilesclinic.native

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 🟢🔒🔒 V656 (২৫.০৮.২০২৬) → 🔴🔒 V671 (২৫.০৮.২০২৬, TK-নির্দেশ) —
 * "Doctor Note & Reminder"।
 *
 * `BriefingReminderScheduler`-এর হুবহু একই প্রমাণিত WorkManager
 * one-time-chain প্যাটার্ন — এখন প্রতি ১৫ মিনিটে (আগে দিনে একবার, ৫টায়)।
 * এতে ডাক্তারের বাছা যেকোনো সময়ের কাছাকাছি (১৫ মিনিটের জানালায়)
 * নোটিফিকেশন বাজে — শুধু একটা ফিক্সড স্লট নয়।
 */
object DoctorReminderScheduler {

    private const val WORK_NAME = "piles_clinic_doctor_reminder"

    fun scheduleNext(context: Context) {
        val delay = TimeUnit.MINUTES.toMillis(DoctorReminderWorker.REPEAT_GAP_MINUTES.toLong())
        val request = OneTimeWorkRequestBuilder<DoctorReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
