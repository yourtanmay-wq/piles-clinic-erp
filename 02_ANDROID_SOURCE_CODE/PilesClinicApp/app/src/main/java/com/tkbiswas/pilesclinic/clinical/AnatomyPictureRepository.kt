package com.tkbiswas.pilesclinic.clinical

import android.content.Context
import com.tkbiswas.pilesclinic.native.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🔵🔒 V573 (২২.০৮.২০২৬, TK-অনুমোদিত) — রোগের ছবির তালিকায় **যোগ ও বিয়োগ**।
 *
 * TK: *"যেখানে সমস্ত ফটো আছে সেখানে যেন গ্যালারি থেকেও ফটো নেয়া যায় অথবা
 * ক্যামেরা থেকেও ফটো নিয়ে দেখানো যায় ... এর আগে যে সমস্ত ফটো আছে সেগুলো
 * আমরা চাইলে যোগ এবং বিয়োগ যেন করতে পারি"*।
 * TK-এর বাছাই: **সব ডিভাইসে এক** ⇒ তালিকাটা ক্লাউডে (`anatomy_pictures`)।
 *
 * ⛔ **Supabase-এ চাপ বাড়ে না** — তালিকাটা ফোনেই জমা থাকে, আর
 *    **১৫ মিনিটে একবারের বেশি** টানা হয় না (ওষুধের ডিফল্টে একই নিয়ম চলছে)।
 * ⛔ ইন্টারনেট না থাকলেও অ্যাপের নিজের ছবিগুলো আগের মতোই কাজ করে —
 *    ফোনে জমা থাকা শেষ তালিকাটাই ব্যবহার হয়।
 * ⛔ ছবি "সরানো" মানে **মোছা নয়** — শুধু তালিকায় দেখানো বন্ধ। পুরোনো
 *    চেক-আপে ওই ছবির উপরে আঁকা থাকলে সেটা আগের মতোই ঠিক দেখাবে।
 * ⚠️ ওয়েবের `wlv1AnatCloudPull` / `wlv1AnatCloudSave`-এর যমজ, আর তালিকা
 *    মেলানোর নিয়মটা দু'জায়গাতেই `AnatomyModel.mergePictures()`।
 */
object AnatomyPictureRepository {

    const val TABLE = "anatomy_pictures"
    private const val PREFS = "anatomy_pics"
    private const val KEY_ROWS = "rows"
    private const val KEY_PULL = "lastPull"
    private const val PULL_GAP_MS = 15L * 60L * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** ফোনে জমা থাকা তালিকা। ভাঙা লেখাতেও কখনো ক্র্যাশ করে না। */
    fun cachedRows(ctx: Context): List<AnatomyModel.PicRow> = try {
        rowsFromJson(JSONArray(prefs(ctx).getString(KEY_ROWS, "[]") ?: "[]"))
    } catch (_: Throwable) { emptyList() }

    fun rowsFromJson(arr: JSONArray?): List<AnatomyModel.PicRow> {
        val out = ArrayList<AnatomyModel.PicRow>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            out.add(AnatomyModel.PicRow(
                id = id,
                picKey = o.optString("picKey", ""),
                label = o.optString("label", ""),
                photo = o.optString("photo", ""),
                hidden = o.optString("hidden", "") == "1",
                sortOrder = o.optString("sortOrder", "0").filter { it.isDigit() }.toLongOrNull() ?: 0L,
                createdAt = o.optString("createdAt", "")
            ))
        }
        return out
    }

    /** পর্দায় যে তালিকাটা দেখাবে। */
    fun pictures(ctx: Context): List<AnatomyModel.Picture> =
        AnatomyModel.mergePictures(AnatomyModel.PICTURES, cachedRows(ctx))

    /**
     * ক্লাউড থেকে তালিকাটা আনা। ঠিক হলে `true`।
     * ⛔ নেটওয়ার্কের কাজ — কখনোই মূল থ্রেডে ডাকবেন না।
     */
    fun pull(ctx: Context, force: Boolean = false): Boolean {
        return try {
            val last = prefs(ctx).getLong(KEY_PULL, 0L)
            if (!force && System.currentTimeMillis() - last < PULL_GAP_MS) return false
            /* 🔴🔒 V576 (২৩.০৮.২০২৬ — TK-এর Supabase Egress ১১৭% দেখে নিজের কাজ
               আবার যাচাই করে ধরা পড়ল): আগে এখানে **ছবিসহ** পুরো তালিকা প্রতিবার
               নামত। ছবি কম বলে এখনো ক্ষতি হয়নি, কিন্তু ছবি বাড়লে এটাই নতুন
               ফুটো হত (২০টা ছবি ≈ ১.২ MB, দিনে বহুবার, প্রতিটা ফোনে)।
               এখন **দু'ধাপ** — যে নিয়মটা V515-এ ওয়েবের Doctor Queue-তে
               TK-অনুমোদিত হয়েছিল, হুবহু সেটাই:
                 ধাপ ১ — তালিকা আসে **ছবি ছাড়া** (কয়েক KB)
                 ধাপ ২ — ছবি নামে **শুধু তাদেরই**, যেগুলো এই ফোনে নেই বা বদলেছে
               ⛔ পর্দায় যা দেখায় তার একটুও বদলায়নি; ফেরত আসা তালিকাও এক। */
            val arr = SupabaseClient.fetchListOrNull(
                TABLE, null, 300, "updatedAt.desc.nullslast",
                "id,picKey,label,hidden,sortOrder,createdAt,updatedAt") ?: return false

            // ফোনে আগে থেকে যে ছবিগুলো আছে (নতুন করে নামানোর দরকার নেই)
            val oldArr = try {
                JSONArray(prefs(ctx).getString(KEY_ROWS, "[]") ?: "[]")
            } catch (_: Throwable) { JSONArray() }
            val havePhoto = HashMap<String, String>()
            val haveStamp = HashMap<String, String>()
            for (i in 0 until oldArr.length()) {
                val o = oldArr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                val ph = o.optString("photo", "")
                if (id.isNotBlank() && ph.isNotBlank()) {
                    havePhoto[id] = ph
                    haveStamp[id] = o.optString("updatedAt", "")
                }
            }

            // কোন ছবিগুলো সত্যিই নামাতে হবে
            val need = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                if (o.optString("hidden", "") == "1") continue   // লুকানো ছবি পর্দায় আসেই না
                val cached = havePhoto[id]
                if (cached != null && haveStamp[id] == o.optString("updatedAt", "")) {
                    o.put("photo", cached)                        // ফোনেরটাই চলবে
                } else {
                    need.add(id)
                }
            }
            if (need.isNotEmpty()) {
                val got = SupabaseClient.fetchListOrNull(
                    TABLE, "id=in.(" + need.joinToString(",") + ")", 300,
                    "updatedAt.desc.nullslast", "id,photo")
                val fresh = HashMap<String, String>()
                if (got != null) for (i in 0 until got.length()) {
                    val o = got.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    if (id.isNotBlank()) fresh[id] = o.optString("photo", "")
                }
                // 🔒 ছবি আনতে না পারলে তালিকাটাই জমা করা হয় না — নইলে ফোনে
                //    জমা থাকা ভালো ছবিগুলো ফাঁকা হয়ে যেত (তথ্য হারাবে না)।
                if (got == null) return false
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.has("photo")) continue
                    val id = o.optString("id", "")
                    o.put("photo", fresh[id] ?: havePhoto[id] ?: "")
                }
            } else {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.has("photo")) continue
                    o.put("photo", havePhoto[o.optString("id", "")] ?: "")
                }
            }
            prefs(ctx).edit().putString(KEY_ROWS, arr.toString())
                .putLong(KEY_PULL, System.currentTimeMillis()).apply()
            true
        } catch (_: Throwable) { false }
    }

    /**
     * একটা সারি জমা করা — আগে ফোনে (সঙ্গে সঙ্গে দেখা যায়), তারপর ক্লাউডে।
     * ⛔ নেটওয়ার্ক অংশটা মূল থ্রেডে নয়।
     */
    fun saveLocal(ctx: Context, row: JSONObject) {
        try {
            val id = row.optString("id", "")
            if (id.isBlank()) return
            val old = JSONArray(prefs(ctx).getString(KEY_ROWS, "[]") ?: "[]")
            val next = JSONArray()
            for (i in 0 until old.length()) {
                val o = old.optJSONObject(i) ?: continue
                if (o.optString("id", "") != id) next.put(o)
            }
            next.put(row)
            prefs(ctx).edit().putString(KEY_ROWS, next.toString()).apply()
        } catch (_: Throwable) { }
    }

    fun pushCloud(row: JSONObject): Boolean = try {
        SupabaseClient.upsert(TABLE, row)
    } catch (_: Throwable) { false }

    /** নতুন যোগ করা ছবির সারি। */
    fun newPhotoRow(label: String, dataUrl: String, by: String): JSONObject {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val id = "pic_" + System.currentTimeMillis()
        return JSONObject().apply {
            put("id", id); put("picKey", ""); put("label", label); put("photo", dataUrl)
            put("hidden", ""); put("sortOrder", System.currentTimeMillis().toString())
            put("createdBy", by); put("createdAt", now); put("updatedAt", now)
        }
    }

    /** অ্যাপের একটা ছবি তালিকা থেকে সরানোর সারি (ছবিটা মোছে না)। */
    fun hideBuiltInRow(picKey: String, label: String): JSONObject {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        return JSONObject().apply {
            put("id", "hide_$picKey"); put("picKey", picKey); put("label", label)
            put("photo", ""); put("hidden", "1"); put("sortOrder", "0")
            put("createdAt", now); put("updatedAt", now)
        }
    }

    /** যোগ করা একটা ছবি তালিকা থেকে সরানো — সেই সারিটাই লুকানো হয়। */
    fun hideAddedRow(ctx: Context, id: String): JSONObject {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val old = cachedRows(ctx).firstOrNull { it.id == id }
        return JSONObject().apply {
            put("id", id); put("picKey", old?.picKey ?: ""); put("label", old?.label ?: "")
            put("photo", old?.photo ?: ""); put("hidden", "1")
            put("sortOrder", (old?.sortOrder ?: 0L).toString())
            put("createdAt", old?.createdAt ?: now); put("updatedAt", now)
        }
    }
}
