package com.tkbiswas.pilesclinic.native

import org.json.JSONArray
import org.json.JSONObject

/**
 * ⏰🔒 V990 (০৩.০৯.২০২৬, TK-এর পাশ-করা ফটো-প্রুফ) —
 * **"আমার অসময়ের এনকোয়ারিগুলো এখন কোন ধাপে?"**
 *
 * TK-এর কথা: *"তারা যদি নাই জানতে পারে যে সেই পেশেন্টটা ট্রিটমেন্ট চালু
 * করেছে কিনা, তাহলে তারা হিসাবটা পাবে কি করে"* ·
 * *"ট্রিটমেন্ট শুরু করালে ৪০০ টাকা… শুধুমাত্র ভিজিট দিলে ১০০ টাকা…
 * দুটোই হলে ৫০০ টাকা"*।
 *
 * ⛔ **টাকার নিয়ম নতুন করে বানানো হয়নি** — ডেটাবেসের চালু নিয়মটাই
 *    (`V418_INCENTIVE_AUTO`) এখানে শুধু **দেখানো** হয়:
 *      অসময়ের এনকোয়ারি → ভিজিট ফি জমা = ₹১০০ → প্রথম চিকিৎসার টাকা = আরও ₹৪০০।
 * ⛔ একটাও সারি লেখা হয় না, শুধু পড়া। টাকার অঙ্ক এক পয়সাও এখান থেকে বদলায় না।
 * ⛔ খরচ কম রাখতে সব মিলিয়ে **দুটো** সরু পড়া — এনকোয়ারি ও পেমেন্ট।
 */
object UnexpectedIncentive {

    const val PAY_REGISTERED = 100.0
    const val PAY_TREATMENT = 400.0

    /** `stage`: "none" = এখনো আসেননি · "visit" = ভিজিট দিয়েছেন · "treatment" = চিকিৎসা শুরু। */
    data class Row(
        val name: String,
        val mobile: String,
        val branch: String,
        val callAt: String,      // এনকোয়ারির তারিখ ও সময় (এটাই টাকার শর্ত)
        val stage: String,
        val stageAt: String,     // ওই ধাপের তারিখ ও সময়
        val earned: Double
    )

    /* ⛔ যে ধরনের সারিগুলো "চিকিৎসার টাকা" নয় — Chamber ও Payment পর্দার
       হুবহু একই তালিকা, তাই কোথাও দুরকম হিসাব হতে পারে না। */
    private val NOT_TREATMENT = setOf(
        "visit_fee", "attendance_mark", "bill_edit", "chamber_expected", "refund"
    )

    private fun digits(v: String) = v.filter { it.isDigit() }.takeLast(10)

    /** স্যালারি পর্দা person-code দিয়ে চলে; সেখান থেকে মোবাইল বের করা।
     *  ⛔ না মিললে ফাঁকা — তখন পর্দা নিজের (লগ-ইন করা) নম্বরই ধরে। */
    fun mobileForCode(code: String): String {
        val want = code.trim()
        if (want.isBlank()) return ""
        for (a in StaffDirectory.allAccounts()) {
            if (a.name.trim().equals(want, ignoreCase = true)) return digits(a.mobile)
        }
        return ""
    }

    /** একজন স্টাফের অসময়ে নেওয়া সব এনকোয়ারি, প্রতিটার এখনকার ধাপ সহ। */
    fun forStaff(staffMobile: String): List<Row> {
        val me = digits(staffMobile)
        if (me.length != 10) return emptyList()

        val enc = java.net.URLEncoder.encode("Unexpected Time", "UTF-8")
        val enq = try {
            SupabaseClient.fetchListSlimOrNull(
                "enquiries", "receivedBy=eq.$me&timeType=eq.$enc", 500,
                "id,name,mobile,branch,date,timeType,receivedBy,createdAt",
                order = "createdAt.desc.nullslast"
            )
        } catch (_: Throwable) { null } ?: return emptyList()
        if (enq.length() == 0) return emptyList()

        val mobiles = ArrayList<String>()
        for (i in 0 until enq.length()) {
            val m = digits(enq.optJSONObject(i)?.s("mobile").orEmpty())
            if (m.length == 10 && m !in mobiles) mobiles.add(m)
        }
        val pays = payFor(mobiles)

        // মোবাইল ধরে — প্রথম ভিজিট ফি, আর প্রথম চিকিৎসার টাকা
        val firstVisit = HashMap<String, String>()
        val firstTreat = HashMap<String, String>()
        for (i in 0 until pays.length()) {
            val p = pays.optJSONObject(i) ?: continue
            val m = digits(p.s("mobile"))
            if (m.length != 10) continue
            val t = p.s("payType").lowercase()
            val amt = p.optDouble("amount", 0.0)
            val at = p.s("createdAt").ifBlank { p.s("date") }
            if (at.isBlank()) continue
            if (t == "visit_fee") {
                val old = firstVisit[m]
                if (old == null || at < old) firstVisit[m] = at
            } else if (t !in NOT_TREATMENT && amt > 0.0) {
                val old = firstTreat[m]
                if (old == null || at < old) firstTreat[m] = at
            }
        }

        val out = ArrayList<Row>()
        val seen = HashSet<String>()
        for (i in 0 until enq.length()) {
            val e = enq.optJSONObject(i) ?: continue
            val m = digits(e.s("mobile"))
            if (m.length != 10 || !seen.add(m)) continue      // এক নম্বর = এক কার্ড
            val treat = firstTreat[m]
            val visit = firstVisit[m]
            val stage = when {
                treat != null -> "treatment"
                visit != null -> "visit"
                else -> "none"
            }
            val earned = when (stage) {
                "treatment" -> PAY_REGISTERED + PAY_TREATMENT
                "visit" -> PAY_REGISTERED
                else -> 0.0
            }
            out.add(
                Row(
                    name = e.s("name").ifBlank { "(no name)" },
                    mobile = m,
                    branch = e.s("branch"),
                    callAt = e.s("createdAt").ifBlank { e.s("date") },
                    stage = stage,
                    stageAt = treat ?: visit.orEmpty(),
                    earned = earned
                )
            )
        }
        return out
    }

    private fun payFor(mobiles: List<String>): JSONArray {
        val all = JSONArray()
        for (part in mobiles.chunked(25)) {
            val list = part.joinToString(",")
            val rows = try {
                SupabaseClient.fetchListSlimOrNull(
                    "payments", "mobile=in.($list)", 2000,
                    "id,mobile,amount,payType,date,createdAt", order = "createdAt.asc.nullslast"
                )
            } catch (_: Throwable) { null } ?: continue
            for (i in 0 until rows.length()) all.put(rows.optJSONObject(i) ?: continue)
        }
        return all
    }

    /** "02.09.2026 · 11.40 PM" — তারিখ আর সময় দুটোই (TK-নির্দেশ)। */
    fun dateTime(raw: String): String {
        val t = raw.trim()
        if (t.length < 10) return ""
        val d = try {
            val p = t.substring(0, 10).split("-")
            p[2] + "." + p[1] + "." + p[0]
        } catch (_: Throwable) { return "" }
        if (t.length < 16) return d
        return try {
            val hh = t.substring(11, 13).toInt()
            val mm = t.substring(14, 16)
            val ap = if (hh >= 12) "PM" else "AM"
            val h12 = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
            "$d  ·  $h12.$mm $ap"
        } catch (_: Throwable) { d }
    }

    fun money(v: Double): String = "₹" + "%,.0f".format(v)

    /** চলতি মাসে এখন পর্যন্ত কত পাওনা হয়েছে। */
    fun thisMonthTotal(rows: List<Row>): Double {
        val ym = try {
            java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        } catch (_: Throwable) { "" }
        return rows.filter { it.stageAt.length >= 7 && it.stageAt.substring(0, 7) == ym }
            .sumOf { it.earned }
    }
}
