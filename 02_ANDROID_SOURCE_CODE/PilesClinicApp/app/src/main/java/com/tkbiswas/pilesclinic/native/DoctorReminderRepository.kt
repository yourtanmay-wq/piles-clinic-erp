package com.tkbiswas.pilesclinic.native

import org.json.JSONObject

/**
 * 🔔🔒 V1186 (০৭.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ) — **Doctor Note & Reminder**,
 * এখন নিজের টেবিলে।
 *
 * TK-এর কথা (হুবহু):
 *  · *"এটা প্রত্যেকের হোম স্ক্রিনে ই থাকবে"*
 *  · *"যে কোন staff, যে কোন ডাক্তার এবং মাস্টার এটা ক্রিয়েট করতে পারবে"*
 *  · *"যে ব্রাঞ্চের যে Doctor তাকেই করতে পারবে"* — তারপর পাঁচটা ব্রাঞ্চের তালিকা
 *  · *"পাঠানোর পর KH MANDAL কে Accept করতে হবে"*
 *  · *"পরে যেন History তে ও দেখতে পায়, যাতে কেউ অস্বীকার না করতে পারে"*
 *
 * ⛔ **পুরনো ব্যবস্থা ছোঁয়া হয়নি** — `patients.doctorReminder*` ঘরগুলো ও
 *    `DoctorReminderWorker`-এর পুরনো পথ হুবহু আগের মতোই চলে; এটা তার
 *    **পাশাপাশি** নতুন একটা টেবিল, তাই আগের কোনো রিমাইন্ডার হারায় না।
 * ⚠️ ক্লাউড ছুঁতে পারে — `Thread`/`Dispatchers.IO`-তে ডাকতে হবে।
 */
object DoctorReminderRepository {

    const val TABLE = "doctor_reminders"

    /* 🩺🔒 V1186 — TK-এর পাঁচটা তালিকা প্রকল্পের স্টাফ-তালিকার সঙ্গে মিলিয়ে
       দেখা হয়েছে; পাঁচটাই **একটাই নিয়মে** হুবহু মেলে:
           ওই ব্রাঞ্চের নিজের ডাক্তার + Dr. K.H MANDAL + TK BISWAS
       (Kishanganj → AMIT GOLDAR · Jalpaiguri → JAY BANIK ·
        Cooch Behar → K.H MANDAL · J.H MANDAL · GOKUL ·
        Falakata → SAIKAT ROY · Birpara → PRANAB BISWAS)
       ⇒ তাই পাঁচটা তালিকা হাতে না লিখে নিয়মটাই বসানো হলো — ভবিষ্যতে নতুন
         ডাক্তার এলে নিজে থেকেই ঠিক ব্রাঞ্চে বসবেন, কোড বদলাতে হবে না।
       ⛔ TK নিজে এই নিয়মটা মিলিয়ে দেখে পাশ করেছেন (০৭.০৯.২০২৬)। */
    private const val EVERYWHERE_DOCTOR = "7980993652"   // Dr. K.H MANDAL — সব ব্রাঞ্চে

    private fun digits(v: String) = v.filter { it.isDigit() }.takeLast(10)

    /** ওই ব্রাঞ্চের স্টাফ যাঁদের কাছে পাঠাতে পারবেন — নাম থেকে মোবাইল। */
    fun doctorsForBranch(branch: String): List<Pair<String, String>> {
        val all = try { StaffDirectory.allAccounts() } catch (_: Throwable) { emptyList() }
        val br = branch.trim()
        val out = ArrayList<Pair<String, String>>()
        for (a in all) {
            if (a.role != "doctor") continue
            val mine = a.branch.trim().equals(br, ignoreCase = true)
            val everywhere = digits(a.mobile) == EVERYWHERE_DOCTOR
            if (mine || everywhere) out.add(a.name to a.mobile)
        }
        // মাস্টার (TK BISWAS) সব ব্রাঞ্চেই থাকেন
        for (a in all) if (a.role == "master") out.add(a.name to a.mobile)
        return out.distinctBy { digits(it.second) }
    }

    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date())

    fun todayIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
            .format(java.util.Date())

    /** স্টাফ/ডাক্তার/মাস্টার — যে কেউ পাঠাতে পারেন। */
    fun send(
        patientId: String, patientName: String, patientMobile: String, branch: String,
        note: String, forMobile: String, forName: String, remindDate: String, remindTime: String,
        byUser: NativeUser?
    ): Boolean {
        return try {
            val row = JSONObject()
                .put("id", "drem_" + System.currentTimeMillis() + "_" + (0..999).random())
                .put("patientId", patientId)
                .put("patientName", patientName)
                .put("patientMobile", digits(patientMobile))
                .put("branch", branch)
                .put("note", note)
                .put("forMobile", digits(forMobile))
                .put("forName", forName)
                .put("byMobile", digits(byUser?.mobile ?: ""))
                .put("byName", (byUser?.name ?: "").ifBlank { digits(byUser?.mobile ?: "") })
                .put("byBranch", byUser?.branch ?: "")
                .put("remindDate", remindDate)
                .put("remindTime", remindTime)
                .put("createdAt", nowIso())
                .put("acceptedBy", "")
                .put("acceptedByName", "")
                .put("acceptedAt", "")
                .put("active", true)
            SupabaseClient.upsert(TABLE, row)
        } catch (_: Throwable) { false }
    }

    /** যাঁকে পাঠানো হয়েছে তিনি মেনে নিলেন — কে ও কখন, দুটোই জমা থাকে। */
    fun accept(id: String, user: NativeUser?): Boolean {
        if (id.isBlank()) return false
        return try {
            SupabaseClient.updateById(
                TABLE, id,
                JSONObject()
                    .put("acceptedBy", digits(user?.mobile ?: ""))
                    .put("acceptedByName", (user?.name ?: "").ifBlank { digits(user?.mobile ?: "") })
                    .put("acceptedAt", nowIso())
            )
        } catch (_: Throwable) { false }
    }

    /**
     * এই ব্যবহারকারী যেগুলো দেখবেন। মাস্টার সব দেখেন; বাকিরা —
     * তাঁকে পাঠানো · তাঁর পাঠানো · অথবা তাঁর ব্রাঞ্চের "সব ডাক্তার"-কে পাঠানো।
     * ⛔ অন্য ব্রাঞ্চের কারো ব্যক্তিগত রিমাইন্ডার কখনো দেখা যায় না।
     */
    fun visibleFor(user: NativeUser?, historyMode: Boolean = false): List<JSONObject> {
        val me = digits(user?.mobile ?: "")
        val myBranch = (user?.branch ?: "").trim()
        val isMaster = user?.role == "master"
        return try {
            val filter = if (historyMode) "active=eq.true"
            else "active=eq.true&remindDate=gte.${todayIso()}"
            val rows = SupabaseClient.fetchList(TABLE, filter, 300, order = "createdAt.desc")
            val out = ArrayList<JSONObject>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (isMaster) { out.add(r); continue }
                val forM = digits(r.optString("forMobile", ""))
                val byM = digits(r.optString("byMobile", ""))
                val br = r.optString("branch", "").trim()
                val mine = forM == me || byM == me ||
                    (forM.isEmpty() && br.equals(myBranch, ignoreCase = true))
                if (mine) out.add(r)
            }
            out
        } catch (_: Throwable) { emptyList() }
    }

    /** এই সারিটা এই ব্যবহারকারী Accept করতে পারেন কিনা (যাঁকে পাঠানো হয়েছে)। */
    fun canAccept(row: JSONObject, user: NativeUser?): Boolean {
        if (row.optString("acceptedAt", "").isNotBlank()) return false
        val me = digits(user?.mobile ?: "")
        if (me.isEmpty()) return false
        val forM = digits(row.optString("forMobile", ""))
        // নির্দিষ্ট একজনকে পাঠানো হলে **কেবল তিনিই** মেনে নিতে পারেন
        if (forM.isNotEmpty()) return forM == me
        /* "সব ডাক্তার"-কে পাঠানো হলে ওই ব্রাঞ্চের যেকোনো ডাক্তার মেনে নিতে
           পারেন; মাস্টার সব ব্রাঞ্চেই পারেন। ⛔ স্টাফ কখনো Accept করতে পারেন না —
           তাহলে "কেউ অস্বীকার করতে পারবে না" নিয়মটাই ভেঙে যেত (TK-নির্দেশ)। */
        if (user?.role == "master") return true
        if (user?.role != "doctor") return false
        return row.optString("branch", "").trim()
            .equals((user.branch).trim(), ignoreCase = true)
    }
}
