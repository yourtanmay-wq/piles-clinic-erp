package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONObject

/**
 * 💰🔒 V984 (০২.০৯.২০২৬, TK-এর পাশ-করা ফটো-প্রুফ) —
 * **চেম্বারের টাকা কে বুঝে নিলেন, তার প্রমাণ।**
 *
 * TK-এর কথা: *"চেম্বার বন্ধ করার সময় যে টাকা পয়সার হিসাব… প্রতি ব্রাঞ্চের
 * ক্ষেত্রে আলাদা আলাদা লোক বুঝে নেয়, কিন্তু অ্যাপে তার প্রমাণ নেই"* ·
 * *"যে কোনো doctor নিতে পারে, তবে সেই নির্দিষ্ট ব্রাঞ্চের ডাক্তার হতে হবে,
 * আর অন্যথায় মাস্টার"* · *"ডাক্তার হাজির না থাকলে মাস্টারের কাছে অনুমোদন
 * আসবে"* · *"পরে স্টাফ যেন ডাক্তারকে বা মাস্টারকে টাকাটা বুঝে দিতে পারে"*।
 *
 * ⛔ নতুন কোনো টেবিল নয় — যে সারিটা আগে থেকেই চেম্বার বন্ধের প্রমাণ রাখে
 *    (`chamber_close`), তারই কয়েকটা নতুন ঘরে সব জমা হয়।
 * ⛔ পাসওয়ার্ড যাচাই লগইনের **হুবহু প্রমাণিত পথেই** — নতুন কোনো নিয়ম বানানো
 *    হয়নি (hash থাকলে hash, নইলে plaintext, নেট না পেলে কিছুতেই "ঠিক আছে" নয়)।
 * ⛔ কোনো টাকা তৈরি বা বদল হয় না — শুধু কে বুঝে নিলেন সেটা লেখা হয়।
 */
object MoneyHandover {

    const val TABLE = "chamber_close"

    /** কে বুঝে নিতে পারেন — ওই ব্রাঞ্চের ডাক্তার, আর সবসময় মাস্টার। */
    data class Receiver(val mobile: String, val name: String, val role: String)

    fun receiversFor(branch: String): List<Receiver> {
        val want = branch.trim()
        val out = ArrayList<Receiver>()
        for (a in StaffDirectory.allAccounts()) {
            if (a.role == "doctor" && a.branch.trim().equals(want, ignoreCase = true)) {
                out.add(Receiver(a.mobile, a.name, "Doctor"))
            }
        }
        for (a in StaffDirectory.allAccounts()) {
            if (a.role == "master") out.add(Receiver(a.mobile, a.name, "Master"))
        }
        return out
    }

    // ───────────────────────── পাসওয়ার্ড যাচাই ─────────────────────────
    enum class Verify { OK, WRONG, NO_NETWORK }

    /**
     * 🔒 `LoginActivity`-র যাচাইয়ের হুবহু একই ধাপ:
     *  • নিজের পাসওয়ার্ড বসানো থাকলে → hash থাকলে hash, নইলে plaintext।
     *  • বসানো না থাকলে → ওই role-এর ডিফল্ট।
     *  • সার্ভারে পৌঁছানো না গেলে → **কখনো "ঠিক আছে" নয়** (নিরাপত্তার ফাঁক বন্ধ)।
     */
    fun verifyPassword(mobile: String, role: String, typed: String): Verify {
        if (typed.isBlank()) return Verify.WRONG
        val digits = StaffDirectory.normalizeMobile(mobile)
        return when (val st = CloudPasswordCheck.fetchOverridePasswordState(digits)) {
            is CloudPasswordCheck.PasswordState.HasCustom -> {
                val ok = if (PasswordHasher.isHash(st.passwordHash))
                    PasswordHasher.verify(typed, st.passwordHash)
                else st.password.isNotBlank() && typed == st.password
                if (ok) Verify.OK else Verify.WRONG
            }
            is CloudPasswordCheck.PasswordState.NoCustom ->
                if (typed == StaffDirectory.defaultPasswordFor(role)) Verify.OK else Verify.WRONG
            is CloudPasswordCheck.PasswordState.Failed -> Verify.NO_NETWORK
        }
    }

    // ───────────────────────── একদিনের সারি ─────────────────────────
    /** `status`: "" = কিছু হয়নি · "received" = বুঝে নেওয়া হয়েছে ·
     *  "pending" = স্টাফের কাছেই আছে · "waiting" = দেওয়া হয়েছে, স্বীকার বাকি। */
    data class Day(
        val id: String,
        val branch: String,
        val date: String,
        val total: Double,
        val fees: Double,
        val cash: Double,
        val online: Double,
        val closedByName: String,
        val status: String,
        val receiverName: String,
        val receiverMobile: String,
        val receivedAt: String
    ) {
        val stillWithStaff: Boolean get() = status.isBlank() || status == "pending"
    }

    private fun d(o: JSONObject, k: String) = try { o.optDouble(k, 0.0) } catch (_: Throwable) { 0.0 }

    fun dayFrom(o: JSONObject): Day = Day(
        id = o.s("id"),
        branch = o.s("branch"),
        date = o.s("date"),
        total = d(o, "grandTotal"),
        fees = d(o, "feesTotal"),
        cash = d(o, "cashTotal"),
        online = d(o, "onlineTotal"),
        closedByName = o.s("closedByName"),
        status = o.s("handoverStatus"),
        receiverName = o.s("receivedByName"),
        receiverMobile = o.s("receivedBy"),
        receivedAt = o.s("receivedAt")
    )

    /** সময়টা "5:10 PM" চেহারায় — তারিখ আলাদা করে উপরে থাকে (TK-নির্দেশ)। */
    fun timeOf(raw: String): String {
        val t = raw.trim()
        if (t.length < 16) return ""
        return try {
            val hh = t.substring(11, 13).toInt()
            val mm = t.substring(14, 16)
            val ap = if (hh >= 12) "PM" else "AM"
            val h12 = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
            "$h12:$mm $ap"
        } catch (_: Throwable) { "" }
    }

    fun money(v: Double): String = "₹" + "%,.0f".format(v)

    fun dotDate(iso: String): String = try {
        val p = iso.trim().substring(0, 10).split("-")
        p[2] + "." + p[1] + "." + p[0]
    } catch (_: Throwable) { iso }

    private fun isoNow(): String = try {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
    } catch (_: Throwable) { "" }

    // ───────────────────────── তালিকা আনা ─────────────────────────
    /** এই ব্রাঞ্চের শেষ কয়েক দিনের বন্ধ-করা চেম্বার, নতুন আগে। */
    fun fetchDays(branch: String, limit: Int = 60): List<Day> {
        val filter = if (branch.isBlank() || branch.equals("All", ignoreCase = true)) null
                     else "branch=eq." + java.net.URLEncoder.encode(branch.trim().uppercase(), "UTF-8")
        val rows = try { SupabaseClient.fetchListOrNull(TABLE, filter, limit) } catch (_: Throwable) { null }
            ?: return emptyList()
        val out = ArrayList<Day>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            out.add(dayFrom(o))
        }
        return out.sortedByDescending { it.date }
    }

    // ───────────────────────── লেখা ─────────────────────────
    /** দিনের টাকার অঙ্কগুলো বন্ধের সারিতে বসানো (প্রমাণের ভিত্তি)। */
    fun saveTotals(
        branch: String, date: String,
        fees: Double, cash: Double, online: Double, refund: Double, grand: Double
    ): Boolean {
        val id = ChamberCloseRepository.idOf(branch, date)
        val body = JSONObject()
            .put("feesTotal", fees).put("cashTotal", cash).put("onlineTotal", online)
            .put("refundTotal", refund).put("grandTotal", grand)
        return try { SupabaseClient.updateById(TABLE, id, body) } catch (_: Throwable) { false }
    }

    /**
     * টাকা বুঝিয়ে দেওয়া হলো।
     * @param acknowledged `true` হলে যিনি নিচ্ছেন তিনি নিজেই এই ফোনে পাসওয়ার্ড
     *        দিয়েছেন ⇒ সরাসরি "received"। `false` হলে মাস্টারের অনুমোদনে
     *        দেওয়া হয়েছে, ডাক্তারের স্বীকারোক্তি বাকি ⇒ "waiting"।
     */
    fun saveHandover(
        context: Context?, branch: String, date: String, total: Double,
        receiver: Receiver, acknowledged: Boolean, byName: String
    ): Boolean {
        val id = ChamberCloseRepository.idOf(branch, date)
        val now = isoNow()
        val body = JSONObject()
            .put("receivedBy", StaffDirectory.normalizeMobile(receiver.mobile))
            .put("receivedByName", receiver.name)
            .put("receivedAt", now)
            .put("handoverStatus", if (acknowledged) "received" else "waiting")
            .put("handoverByName", byName)
            .put("updatedAt", now)
        val ok = try { SupabaseClient.updateById(TABLE, id, body) } catch (_: Throwable) { false }
        if (ok && context != null && !acknowledged) {
            /* ⛔ ডাক্তার নিজে স্বীকার না করা পর্যন্ত তাঁর নিজের 🔔 পর্দায় কাজটা
               বসে থাকে — প্রকল্পের প্রমাণিত নোটিশ-ব্যবস্থাতেই। */
            try {
                BriefingRepository().post(
                    context,
                    "Money handover — please confirm",
                    branch.trim() + " · " + dotDate(date) + " · " + money(total) +
                        " handed over by " + byName,
                    "individual", branch, "doctor", "",
                    StaffDirectory.normalizeMobile(receiver.mobile)
                )
            } catch (_: Throwable) { }
        }
        return ok
    }

    /** স্টাফের কাছেই থেকে গেল — মাস্টারের অনুমোদনের জন্য নোটিশ। */
    fun markPending(context: Context?, branch: String, date: String, total: Double, byName: String): Boolean {
        val id = ChamberCloseRepository.idOf(branch, date)
        val now = isoNow()
        val body = JSONObject()
            .put("handoverStatus", "pending")
            .put("handoverByName", byName)
            .put("updatedAt", now)
        val ok = try { SupabaseClient.updateById(TABLE, id, body) } catch (_: Throwable) { false }
        if (ok && context != null) {
            try {
                BriefingRepository().post(
                    context,
                    "Chamber closed without handover",
                    branch.trim() + " · " + dotDate(date) + " · " + money(total) +
                        " is still with " + byName,
                    "role", branch, "master", "", ""
                )
            } catch (_: Throwable) { }
        }
        return ok
    }

    /** ডাক্তার/মাস্টার নিজের ফোনে স্বীকার করলেন। */
    fun acknowledge(context: Context?, day: Day, byName: String): Boolean {
        val now = isoNow()
        val body = JSONObject()
            .put("handoverStatus", "received")
            .put("receivedAt", now)
            .put("updatedAt", now)
        val ok = try { SupabaseClient.updateById(TABLE, day.id, body) } catch (_: Throwable) { false }
        if (ok && context != null) {
            try {
                BriefingRepository().post(
                    context,
                    "Money handover confirmed",
                    day.branch + " · " + dotDate(day.date) + " · " + money(day.cash) +
                        " received by " + byName,   // 💵 V1039 — শুধু ক্যাশ
                    "branch", day.branch, "staff", "", ""
                )
            } catch (_: Throwable) { }
        }
        return ok
    }

    /** যাঁর স্বীকারোক্তি বাকি, তাঁর নিজের তালিকা। */
    fun waitingFor(mobile: String, limit: Int = 60): List<Day> {
        val m = StaffDirectory.normalizeMobile(mobile)
        if (m.length != 10) return emptyList()
        val filter = "receivedBy=eq.$m&handoverStatus=eq.waiting"
        val rows = try { SupabaseClient.fetchListOrNull(TABLE, filter, limit) } catch (_: Throwable) { null }
            ?: return emptyList()
        val out = ArrayList<Day>()
        for (i in 0 until rows.length()) {
            val o = rows.optJSONObject(i) ?: continue
            out.add(dayFrom(o))
        }
        return out.sortedByDescending { it.date }
    }

    /** কাগজে ছাপার এক লাইন — TK: *"স্টাফ প্রমাণ পাবে কি করে"*। */
    fun paperLine(day: Day): String = when {
        day.status == "received" || day.status == "waiting" ->
            "Handed over to " + day.receiverName + " · " + money(day.cash) +   // 💵 V1039 — শুধু ক্যাশ
                " · " + dotDate(day.date) + " " + timeOf(day.receivedAt)
        day.status == "pending" -> "Money not handed over — still with " + day.closedByName
        else -> ""
    }
}
