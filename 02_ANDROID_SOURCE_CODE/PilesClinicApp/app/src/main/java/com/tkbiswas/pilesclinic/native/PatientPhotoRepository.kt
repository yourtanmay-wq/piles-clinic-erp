package com.tkbiswas.pilesclinic.native

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native rebuild -- Patient Photo management.
 *
 * Stores the photo as a data-URL string in the live Supabase "patients".photo
 * field. That is the ONE place the photo lives.
 *
 * 🔴 TK-অনুমোদিত পরিবর্তন (02.08.2026, স্ক্রিনশটে দাগ দিয়ে আলোচনা করে):
 * আগে এই ফাংশন patients.photo সেভ করার পর সেই একই ছবি প্রতিটা মিলে-যাওয়া
 * followups সারিতেও কপি করে বসাত ("Mirror onto followups")। TK নিজে
 * নিশ্চিত করেছেন Follow-up-এর কোনো কার্ডেই ছবি দেখানো হয় না (২৭.০৭.২০২৬-এর
 * পর থেকে) — তাই সেই কপিটা কোথাও ব্যবহারই হতো না, শুধু ডেটাবেসের জায়গা
 * (প্রতি রোগীতে একাধিক কপি) ও Supabase-এর Egress অকারণে খরচ করত। মিরর করা
 * বন্ধ করা হলো — patients.photo-ই এখন একমাত্র সত্য উৎস (single source of
 * truth), ঠিক TK-এর "টাকার হিসাব এক জায়গায়" নিয়মের মতোই ছবির বেলাতেও।
 * ⛔ রোগীর নিজের পাতা/Payment/Report Card-এ ছবি দেখানো এক অক্ষরও বদলায়নি —
 * ওগুলো সবসময় patients.photo থেকেই সরাসরি পড়ে, followups থেকে নয়।
 */
class PatientPhotoRepository {

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    // 🆔 TK-এর নিয়ম (28.07.2026): নাম ও মোবাইলের সঙ্গে Patient ID-ও দেখাতে হবে।
    // ডিফল্ট ফাঁকা রাখা হয়েছে, তাই পুরনো কোনো ডাক ভাঙে না।
    data class PatientRef(val id: String, val name: String, val mobile: String, val photo: String, val patientId: String = "")

    fun findByMobile(mobileDigits: String): PatientRef? {
        val rows = SupabaseClient.findByMobile(
            "patients", "+91$mobileDigits", "id,name,mobile,photo,patientId"
        )
        if (rows.length() == 0) return null
        val r = rows.getJSONObject(0)
        return PatientRef(
            id = r.s("id"),
            name = r.s("name"),
            mobile = r.s("mobile"),
            photo = r.s("photo"),
            patientId = r.s("patientId")
        )
    }

    /** Saves (or clears, if photoData is blank) the patient photo on the
     * patients row. Returns true if the patient row updated.
     * (No longer mirrors to followups -- see class doc-comment above.) */
    fun savePhoto(patient: PatientRef, photoData: String, context: android.content.Context? = null): Boolean {
        val now = isoNow()
        val patFields = JSONObject().put("photo", photoData).put("updatedAt", now)
        val ok = SupabaseClient.updateById("patients", patient.id, patFields)
        if (!ok) {
            // TK-REQUESTED (2026-07-27): retry later instead of losing it.
            if (context != null) {
                try { GenericUpdateQueue.queue(context, "patients", patient.id, patFields) } catch (_: Throwable) { }
            }
            return false
        }
        return true
    }
}
