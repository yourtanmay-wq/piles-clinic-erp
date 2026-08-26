package com.tkbiswas.pilesclinic.native

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Native rebuild -- Briefing / Notice Board (data model).
 *
 * Mirrors app.js's briefings logic: a briefing is a dated message posted to a
 * target audience (all staff / a branch / a role / specific mobiles). A user
 * "sees" it (added to seen[]) and can reply (added to replies[]). Fields match
 * the WebView's briefings table schema:
 *   id,date,title,message,targets,seen,replies,hiddenFor,deletedAt,deletedBy,
 *   branch,createdBy,createdAt,updatedAt
 */
data class Briefing(
    val id: String,
    val date: String,
    val title: String,
    val message: String,
    val createdBy: String,
    val branch: String,
    val targetsSummary: String,
    val seenCount: Int,
    // 🟢🔒 V682 (২৫.০৮.২০২৬, TK-লাইভ-টেস্ট রিপোর্ট — "Seen by 1-এ চাপ দিলে কে
    // দেখেছে বোঝা যায় না") — আসল মোবাইল-তালিকা (raw "seen" ঘর থেকে), যাতে
    // চাপ দিলে নাম দেখানো যায়। ⛔ seenCount আগের মতোই অক্ষত।
    val seenBy: List<String>,
    val replies: List<BriefingReply>,
    val raw: JSONObject
)

data class BriefingReply(val by: String, val text: String, val at: String)

object BriefingModel {

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 🔵 খাতার সারি (TK-নির্দেশ, 09.08.2026): "অনুমতি লাগে এমন" নোটিস কি না।
     *  Refund / Delete / Reopen request — এগুলো Master অনুমোদন/অ্যাকশন না দেওয়া
     *  পর্যন্ত পুরনো হলেও থেকে যায় (নইলে pending অনুমোদন হারিয়ে যেত)। বাকি সব
     *  "সাধারণ নোটিস" — শুধু আজকেরটা দেখায়। ⛔ এক জায়গায় নিয়ম, যাতে তালিকা-
     *  ফিল্টার (visibleForUser) ও অটো-ক্লিয়ার দুটো কখনো আলাদা না হয়। */
    /**
     * 🔴🔒 V682 (২৫.০৮.২০২৬, TK-লাইভ-টেস্ট রিপোর্ট — "Approve করার পরেও এখানে
     * কেন থাকবে?") — আসল কারণ: Approve করলে যে "💬 Reply on: 🗑️ Delete
     * request — ..." নোটিশটা মূল অনুরোধকারীকে পাঠানো হয় (addReply(), নিচে),
     * তার টাইটেলেও "delete request" কথাটা আছে (মূল টাইটেলের ভিতরেই বসানো
     * হয়) — তাই এই সাধারণ, শুধু-জানানোর রিপ্লাই-নোটিশটাও ভুল করে
     * "Approve লাগবে" ধরে নিত, Approve/Reply/Delete বোতাম দেখাত। এখন
     * "reply on:" দিয়ে শুরু হওয়া টাইটেল প্রথমেই বাদ — আসল Refund/Delete/
     * Reopen/Leave-এর অনুরোধ কখনোই এই লেখায় শুরু হয় না, তাই ঝুঁকিহীন।
     */
    fun needsMasterApproval(title: String): Boolean {
        val t = title.lowercase()
        if (t.contains("reply on:")) return false
        return t.contains("refund request") ||
            t.contains("delete request") ||
            t.contains("reopen request") ||
            t.contains("leave request")   // 🔵 B618: ছুটির অনুরোধ — মঞ্জুর/নামঞ্জুরের আগে দেখা হলেও নিজে মুছবে না
    }
    /* ═══════════════════════════════════════════════════════════════════════
       🔔🔒 V490 (20.08.2026, TK-নির্দেশ) — নতুন Enquiry · Registration ·
       Advance-এর **স্বয়ংক্রিয়** নোটিশ।

       TK-এর রিপোর্ট: ফালাকাটা থেকে কিশনগঞ্জের জন্য একটা এনকোয়ারি হয়েছিল —
       ব্রাঞ্চ ফোন না করলে *"হারিয়ে যেত"*।

       এই তিনটে নোটিশ ডেটাবেসেই তৈরি হয় (V490 SQL), তাই ফোন ও কম্পিউটার —
       দুই জায়গার কাজেই আসে। নিচের নামগুলো SQL-এর `auto_notice_titles()`-এর
       হুবহু একই তিনটে নাম।

       ⚠️ সাধারণ নোটিশ শুধু **আজকের**টা দেখায় (রাত ১২টায় নিজে থেকে সরে যায়)।
          কিন্তু TK-এর স্পষ্ট নির্দেশ: *"যতক্ষণ সিন না করবে, সর্বোচ্চ ১ সপ্তাহ"* —
          তাই এই তিনটে নোটিশ **Seen না করা পর্যন্ত ৭ দিন** থাকে। রাত ১১টার
          এনকোয়ারি তাই পরদিন সকালেও চোখে পড়বে, হারিয়ে যাবে না।
       ⛔ বাকি সব নোটিশের নিয়ম এক অক্ষরও বদলায়নি।
       ═══════════════════════════════════════════════════════════════════ */
    const val AUTO_NOTICE_DAYS = 7

    fun isAutoNotice(title: String): Boolean {
        val t = title.trim()
        return t.equals("New Enquiry", true) ||
            t.equals("New Registration", true) ||
            t.equals("Advance Received", true)
    }

    /** নোটিশের তারিখ আজ থেকে সর্বোচ্চ [days] দিনের পুরনো কি না। */
    fun withinDays(date: String, days: Int): Boolean {
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = f.parse(date.trim()) ?: return false
            val t = f.parse(today()) ?: return false
            val diff = t.time - d.time
            diff >= 0 && diff <= days.toLong() * 24L * 60L * 60L * 1000L
        } catch (_: Throwable) { false }
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    /** last-10-digits normalization, matching app.js mob(). */
    fun mob(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    fun parse(row: JSONObject): Briefing {
        val repliesArr = row.optJSONArray("replies") ?: JSONArray()
        val replies = mutableListOf<BriefingReply>()
        for (i in 0 until repliesArr.length()) {
            val r = repliesArr.optJSONObject(i) ?: continue
            if (r.s("deletedAt").isNotBlank()) continue
            replies.add(BriefingReply(r.s("by"), r.s("text"), r.s("at")))
        }
        val seen = row.optJSONArray("seen") ?: JSONArray()
        val seenList = (0 until seen.length()).map { seen.optString(it, "") }.filter { it.isNotBlank() }
        return Briefing(
            id = row.s("id"),
            date = row.s("date"),
            title = row.s("title"),
            message = row.s("message"),
            createdBy = row.s("createdBy"),
            branch = row.s("branch"),
            targetsSummary = summarizeTargets(row.optJSONObject("targets") ?: JSONObject()),
            seenCount = seen.length(),
            seenBy = seenList,
            replies = replies,
            raw = row
        )
    }

    private fun summarizeTargets(t: JSONObject): String {
        if (t.optBoolean("allStaff") || t.optBoolean("all")) return "All Staff"
        t.optJSONArray("branches")?.let { if (it.length() > 0) return "Branch: " + joinArr(it) }
        t.optJSONArray("roles")?.let { if (it.length() > 0) return "Role: " + joinArr(it) }
        t.optJSONArray("mobiles")?.let { if (it.length() > 0) return "Individual" }
        t.optJSONArray("mobiles")?.let { if (it.length() > 0) return "Selected: ${it.length()}" }
        return "Custom"
    }

    private fun joinArr(a: JSONArray): String =
        (0 until a.length()).joinToString(", ") { a.optString(it) }

    /** Matches briefingTargetsHit(): does this briefing target the given user? */
    fun targetsHit(row: JSONObject, userMobile: String, userRole: String, userBranch: String): Boolean {
        val t = row.optJSONObject("targets") ?: return false
        if (t.optBoolean("all") || t.optBoolean("allStaff")) return true
        val m = mob(userMobile)
        t.optJSONArray("mobiles")?.let { for (i in 0 until it.length()) if (mob(it.optString(i)) == m) return true }
        val role = userRole.lowercase()
        t.optJSONArray("roles")?.let { for (i in 0 until it.length()) if (it.optString(i).lowercase() == role) return true }
        t.optJSONArray("branches")?.let {
            for (i in 0 until it.length()) if (it.optString(i).equals(userBranch, ignoreCase = true)) return true
        }
        return false
    }

    fun hasSeen(row: JSONObject, userMobile: String): Boolean {
        val seen = row.optJSONArray("seen") ?: return false
        val m = mob(userMobile)
        for (i in 0 until seen.length()) if (mob(seen.optString(i)) == m) return true
        return false
    }

    fun isDeletedForMe(row: JSONObject, userMobile: String): Boolean {
        if (row.s("deletedAt").isNotBlank()) return true
        val hidden = row.optJSONArray("hiddenFor") ?: return false
        val m = mob(userMobile)
        for (i in 0 until hidden.length()) if (mob(hidden.optString(i)) == m) return true
        return false
    }

    /** Builds a new briefing row for posting. target is one of the simple
     * options this native screen offers: "allStaff", "branch", "role". */
    fun buildNewBriefing(
        title: String, message: String, target: String,
        branch: String, role: String, createdByMobile: String,
        targetMobile: String = ""
    ): JSONObject {
        val now = isoNow()
        val targets = JSONObject()
        when (target) {
            "branch" -> targets.put("branches", JSONArray().put(branch))
            "role" -> targets.put("roles", JSONArray().put(role))
            "individual" -> targets.put("mobiles", JSONArray().put(targetMobile))
            else -> targets.put("allStaff", true)
        }
        return JSONObject()
            .put("id", "brief_" + UUID.randomUUID().toString().replace("-", ""))
            .put("date", today())
            .put("title", title)
            .put("message", message)
            .put("targets", targets)
            .put("seen", JSONArray())
            .put("replies", JSONArray())
            .put("hiddenFor", JSONArray())
            .put("branch", branch)
            .put("createdBy", createdByMobile)
            .put("createdAt", now)
            .put("updatedAt", now)
    }

    /** Appends a mobile to seen[] (fetch-modify-patch pattern). */
    fun buildSeenUpdate(existingSeen: JSONArray, userMobile: String): JSONObject {
        val seen = JSONArray()
        for (i in 0 until existingSeen.length()) seen.put(existingSeen.get(i))
        seen.put(userMobile)
        return JSONObject().put("seen", seen).put("updatedAt", isoNow())
    }

    /** Appends a reply to replies[]. */
    fun buildReplyUpdate(existingReplies: JSONArray, text: String, userMobile: String): JSONObject {
        val replies = JSONArray()
        for (i in 0 until existingReplies.length()) replies.put(existingReplies.get(i))
        replies.put(
            JSONObject()
                .put("id", "reply_" + UUID.randomUUID().toString().replace("-", ""))
                .put("by", userMobile)
                .put("text", text)
                .put("at", isoNow())
        )
        return JSONObject().put("replies", replies).put("updatedAt", isoNow())
    }

    /** Master delete: soft-deletes for everyone (deletedAt/deletedBy), matching
     * deleteBriefing()'s master branch. */
    fun buildMasterDelete(userMobile: String): JSONObject =
        JSONObject().put("deletedAt", isoNow()).put("deletedBy", userMobile).put("updatedAt", isoNow())

    /** Non-master "delete": hides only for this user (append to hiddenFor). */
    fun buildHideForUser(existingHiddenFor: JSONArray, userMobile: String): JSONObject {
        val hidden = JSONArray()
        val m = mob(userMobile)
        var already = false
        for (i in 0 until existingHiddenFor.length()) {
            val v = existingHiddenFor.optString(i)
            hidden.put(v)
            if (mob(v) == m) already = true
        }
        if (!already) hidden.put(userMobile)
        return JSONObject().put("hiddenFor", hidden).put("updatedAt", isoNow())
    }
}
