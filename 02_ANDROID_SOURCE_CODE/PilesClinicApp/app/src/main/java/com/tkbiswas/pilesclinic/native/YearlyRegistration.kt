package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONObject

/**
 * 📊 V824 — বার্ষিক রেজিস্ট্রেশন-হিসাব (শুধু মাস্টার, শুধু বাছা ব্রাঞ্চ)
 *
 * TK-নির্দেশ (২৯.০৮.২০২৬): *"শুধুমাত্র মাস্টারের জন্য এখানে চেম্বারে কতজন
 * এসেছে ০১/০১/২০২৬ থেকে ৩১/১২/২০২৬ পর্যন্ত তার একটা হিসাব রাখতে হবে…
 * সত্যিকারের, কোন ডেমোগুলো ধরা হবে না… এই লিস্টে Refund এবং Visit Return
 * বাদ যাবে… নামে DEMO বা TEST থাকলে বাদ… তাছাড়া ওই লিস্ট থেকে আমি যদি কিছু
 * বাদ দিয়ে দেই… সেটা প্রয়োজনে আমি দেখে দেখে বাদ দিতে পারব, তার ব্যবস্থা
 * রাখবেন।"* + *"সমস্ত ব্রাঞ্চের একসাথে যোগ করতে আপনাকে বলা হয়নি — আমার যে
 * ব্রাঞ্চ সিলেক্ট করা থাকবে শুধুমাত্র সেই ব্রাঞ্চের।"*
 *
 * ⛔ **কোনো নতুন ক্লাউড-পড়া Draft-এর পথে যোগ হয়নি** — গোনাটা Draft ইতিমধ্যে
 *    যে `patients` তালিকা এনেছে ঠিক তার উপরেই হয় (Egress এক বাইটও বাড়ে না)।
 * ⛔ "বাদ দেওয়া" তালিকাটা (`fin.registration_count_excluded`) এই ফোনে জমানো
 *    থাকে; Draft শুধু সেই **জমানো** তালিকাই পড়ে — নেটে যায় না। আসল পড়া/লেখা
 *    হয় শুধু বিস্তারিত পর্দা খুললে (YearlyRegistrationActivity)।
 * ⛔ কোনো রোগীর রেকর্ড · টাকা · Follow-up কিচ্ছু ছোঁয়া হয় না। "বাদ" মানে
 *    শুধু **এই গোনায় ধরা হবে না** — "Undo" চাপলেই আবার ফিরে আসে।
 */
object YearlyRegistration {

    /** মাস্টার হাতে বাদ দিলে DraftEntry.extra-তে এই দাগটাই বসে। */
    const val SKIP_MARK = "SKIPPED"

    /** গোনায় ধরা হয় শুধু দাগ-ছাড়া সারিগুলো। */
    fun countedOf(rows: List<DraftEntry>): Int = rows.count { it.extra != SKIP_MARK }

    /* 🆕🔒 V852 (৩০.০৮.২০২৬, TK-অনুমোদিত) — TK: *"return visit / refund এরকম
       কোনো ব্যাপার থাকলে ওই ব্যক্তির পাশে ফার্স্ট ব্র্যাকেটের মধ্যে মেনশন
       থাকবে"*। ⛔ এঁরা এখন গোনাতেও ধরা হয় — TK চাইলে নিজে Skip করবেন। */
    const val TAG_RETURN = "Return Visit"
    const val TAG_REFUND = "Refund"

    private const val PREFS = "v824_yearly_reg"
    private const val KEY_IDS = "excluded_ids"

    /** চলতি বছর ("2026")। পর্দাতেও ঠিক এই বছরটাই লেখা থাকে, তাই কখনো
     *  ভুল বোঝার সুযোগ নেই। */
    fun currentYear(): String =
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()

    /** TK-নির্দেশ: নামে DEMO বা TEST থাকলে গোনায় ধরা হবে না। */
    fun isDemoName(name: String): Boolean {
        val n = name.trim().uppercase(java.util.Locale.US)
        if (n.isBlank()) return false
        return n.contains("DEMO") || n.contains("TEST")
    }

    /** রেজিস্ট্রেশনের তারিখ — `registrationDate`, না থাকলে `date`. */
    fun regDateOf(row: JSONObject): String =
        row.s("registrationDate").ifBlank { row.s("date") }.take(10)

    // ── মাস্টারের হাতে বাদ-দেওয়া তালিকা (এই ফোনে জমানো) ───────────────────
    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** ⛔ কখনো নেটে যায় না — Draft-এর পথে এটাই ব্যবহার হয়। */
    fun cachedExcludedIds(ctx: Context?): Set<String> {
        if (ctx == null) return emptySet()
        return try {
            prefs(ctx).getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
        } catch (_: Throwable) { emptySet() }
    }

    private fun saveExcludedIds(ctx: Context, ids: Set<String>) {
        try { prefs(ctx).edit().putStringSet(KEY_IDS, ids).apply() } catch (_: Throwable) { }
    }

    /**
     * ক্লাউড থেকে আসল তালিকা (শুধু বিস্তারিত পর্দা খুললে)।
     * ⛔ ব্যর্থ হলে `null` ফেরে — তখন **জমানো তালিকাই** ব্যবহার হয়, তাই
     *    নেট খারাপ থাকলে হঠাৎ বাদ-দেওয়া লোক গোনায় ফিরে আসবে না।
     */
    fun fetchExcluded(ctx: Context): List<JSONObject>? {
        val err = try { ModuleAuthBridge.signIn(ctx) } catch (_: Throwable) { "auth" }
        if (err != null) return null
        return try {
            val r = com.tkbiswas.pilesclinic.modules.ModuleAuth.getRowsChecked(
                "fin", "registration_count_excluded",
                "select=patient_row_id,patient_code,patient_name,excluded_at")
            if (!r.ok) return null
            val out = ArrayList<JSONObject>()
            val ids = HashSet<String>()
            for (i in 0 until r.rows.length()) {
                val o = r.rows.optJSONObject(i) ?: continue
                out.add(o)
                val id = o.s("patient_row_id")
                if (id.isNotBlank()) ids.add(id)
            }
            saveExcludedIds(ctx, ids)
            out
        } catch (_: Throwable) { null }
    }

    /** এক জনকে গোনা থেকে বাদ দিন (Skip)। সফল হলে জমানো তালিকাও বাড়ে। */
    fun exclude(ctx: Context, rowId: String, code: String, name: String, by: String): Boolean {
        if (rowId.isBlank()) return false
        val err = try { ModuleAuthBridge.signIn(ctx) } catch (_: Throwable) { "auth" }
        if (err != null) return false
        val row = JSONObject()
            .put("patient_row_id", rowId)
            .put("patient_code", code)
            .put("patient_name", name)
            .put("excluded_by", by)
        val ok = try {
            com.tkbiswas.pilesclinic.modules.ModuleAuth
                .upsertOnConflict("fin", "registration_count_excluded", row, "patient_row_id")
        } catch (_: Throwable) { false }
        if (ok) saveExcludedIds(ctx, cachedExcludedIds(ctx) + rowId)
        return ok
    }

    /** ভুল হলে ফেরান (Undo) — সারিটা মুছে গেলে রোগী আবার গোনায় ফেরেন। */
    fun include(ctx: Context, rowId: String): Boolean {
        if (rowId.isBlank()) return false
        val err = try { ModuleAuthBridge.signIn(ctx) } catch (_: Throwable) { "auth" }
        if (err != null) return false
        val ok = try {
            com.tkbiswas.pilesclinic.modules.ModuleAuth.deleteRows(
                "fin", "registration_count_excluded",
                "patient_row_id=eq." + java.net.URLEncoder.encode(rowId, "UTF-8"))
        } catch (_: Throwable) { false }
        if (ok) saveExcludedIds(ctx, cachedExcludedIds(ctx) - rowId)
        return ok
    }
}

/** ছোট্ট মোড়ক — মডিউল-লগইন এক জায়গা থেকে (আলাদা কোনো পাসওয়ার্ড নয়)। */
internal object ModuleAuthBridge {
    fun signIn(ctx: Context): String? =
        com.tkbiswas.pilesclinic.modules.ModuleAuth.signInCurrentSession(ctx)
}
