package com.tkbiswas.pilesclinic.native

import com.tkbiswas.pilesclinic.modules.ModuleAuth
import org.json.JSONObject
import com.tkbiswas.pilesclinic.native.s

/**
 * 👥🔒 V746 (২৭.০৮.২০২৬, TK-অনুমোদিত) — **মাস্টার নিজে স্টাফ ও ডাক্তার
 * যোগ / বাদ / ফেরাতে পারবেন** — SQL ছাড়া, নতুন APK ছাড়া।
 *
 * TK-এর কথা: *"আপনি তো আর আমার সাথে সারা জীবন থাকবেন না।"*
 *
 * ─────────────────────────────────────────────────────────────────────
 * ⛔ **এই ফাইলটা নিজে কোনো নিয়ম যাচাই করে না** — একটাও না।
 *    সব পাহারা **সার্ভারে** (`00_SQL/V745_STAFF_DOCTOR_FROM_APP.sql`):
 *      · শুধু মাস্টার ডাকতে পারেন
 *      · master ভূমিকা এখান থেকে বানানো যায় না
 *      · মাস্টারকে বাদ দেওয়া যায় না
 *      · মোবাইল ১০ অঙ্ক, আর অন্য কারও নামে থাকলে চলবে না
 *      · নাম · কোড · ব্রাঞ্চ — ফাঁকা চলবে না
 *    ⇒ ফোনের অ্যাপ বদলে ফেললেও এই নিয়মগুলো ফাঁকি দেওয়ার পথ নেই।
 *
 * ⛔ **কাউকে কখনো মোছা হয় না** — শুধু "নিষ্ক্রিয়" করা হয়। তাই পুরনো
 *    রেকর্ডে নাম চিরকাল থেকে যায়, আর লগইন নিজে থেকেই বন্ধ হয় (V403)।
 * ⛔ টাকার কোনো হিসাব ছোঁয়া হয় না — সেসব মোবাইল ধরে চলে (V308)।
 * ⚡ প্রতিটা কাজ **একটাই ছোট RPC**, চাপ দিলে তবেই যায়।
 */
object PeopleAdminRepository {

    /** ⚠️ নাম `Result` রাখা যায়নি — Android-এর নিজের `Result` ক্লাসের সঙ্গে
     *  গুলিয়ে গিয়ে গার্ড অন্য ফাইলে ভুয়া ভুল দেখাচ্ছিল (যাচাই করে ধরা)। */
    data class AdminResult(val ok: Boolean, val message: String)

    data class Person(
        val code: String,
        val name: String,
        val mobile: String,
        val branch: String,
        val role: String,
        val active: Boolean
    )

    private const val SCHEMA = "hr"

    /** ⛔ নেট/লগইন ব্যর্থ হলেও অ্যাপ থামে না — সৎ বার্তা নিয়ে ফেরে। */
    private fun call(fn: String, args: JSONObject): AdminResult {
        val r = try { ModuleAuth.rpc(SCHEMA, fn, args) } catch (_: Throwable) { null }
            ?: return AdminResult(false, "Could not reach the server. Check internet.")
        if (!r.ok) {
            return AdminResult(false, r.message.ifBlank { "Could not reach the server. Check internet." })
        }
        val o = try { JSONObject(r.body) } catch (_: Throwable) { JSONObject() }
        val ok = o.optBoolean("ok", false)
        return AdminResult(ok, o.optString("message", if (ok) "Done" else "Could not do it"))
    }

    /** নতুন স্টাফ বা ডাক্তার। `role` = "staff" বা "doctor"। */
    fun add(code: String, mobile: String, name: String, branch: String, role: String): AdminResult =
        call(
            "admin_create_person",
            JSONObject()
                .put("p_code", code)
                .put("p_mobile", mobile)
                .put("p_name", name)
                .put("p_branch", branch)
                .put("p_role", role)
        )

    /** বাদ দেওয়া (`false`) বা ফেরানো (`true`)। */
    fun setActive(code: String, active: Boolean): AdminResult =
        call(
            "admin_set_person_active",
            JSONObject().put("p_code", code).put("p_active", active)
        )

    /** মাস্টারের তালিকা — বাদ-দেওয়া লোকও থাকে, যাতে ফেরানো যায়।
     *  ⛔ মাস্টার ছাড়া কেউ ডাকলে সার্ভারই খালি তালিকা দেয়। */
    fun list(): List<Person> {
        val r = try {
            ModuleAuth.rpc(SCHEMA, "admin_people_list", JSONObject())
        } catch (_: Throwable) { null } ?: return emptyList()
        if (!r.ok) return emptyList()
        val arr = try { org.json.JSONArray(r.body) } catch (_: Throwable) { null }
            ?: return emptyList()
        val out = ArrayList<Person>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            /* 🔴🔒 V760 (২৭.০৮.২০২৬, TK-রিপোর্ট ছবিসহ: তালিকায় **"null"** নাম)
               **আসল কারণ:** ডেটাবেসে নাম ভরা না থাকলে সেটা `null` আসে, আর
               `optString("full_name","")` তখন **"null" লেখাটাই** ফেরত দেয় —
               ফাঁকা নয়। তাই আমার `ifBlank { code }` পাহারা কখনো চলত না, আর
               পর্দায় "null" ও "Remove null" দেখাত।
               ⛔ এখন প্রজেক্টের **নিজের প্রমাণিত** `JsonExt.s()` ব্যবহার করা হলো
                  (`if (isNull(key)) "" else optString(...)`) — নতুন কিছু বানানো হয়নি।
               ⛔ **প্রতিটা ঘরেই** বদলানো হলো, শুধু নামেরটা নয় — একই শ্রেণির ভুল
                  যেন মোবাইল · ব্রাঞ্চ · ভূমিকাতেও না থাকে (TK-এর নিয়ম ৬.২)। */
            val code = o.s("person_code")
            if (code.isBlank()) continue
            out.add(
                Person(
                    code = code,
                    name = o.s("full_name").trim().ifBlank { code },
                    mobile = o.s("link_mobile"),
                    branch = o.s("branch"),
                    role = o.s("role_kind"),
                    active = o.optBoolean("active", true)
                )
            )
        }
        return out
    }

    /** পর্দায় দেখানোর জন্য — "Staff" / "Doctor" / "Field Officer"। */
    fun roleLabel(role: String): String = when (role.trim().lowercase()) {
        "doctor" -> "Doctor"
        "field" -> "Field Officer"
        else -> "Staff"
    }
}
