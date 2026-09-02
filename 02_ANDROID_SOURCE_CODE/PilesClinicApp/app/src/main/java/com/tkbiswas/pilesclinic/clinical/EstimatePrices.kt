package com.tkbiswas.pilesclinic.clinical

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 💰🔒 V971 (০২.০৯.২০২৬, TK-অনুমোদিত ফটো-প্রুফ ও PDF নমুনা অনুযায়ী) —
 * **এস্টিমেটের দরের তালিকা।**
 *
 * TK: *"একটা রেডিমেড থাকবে, সেটা যেন বদলাতে পারে"* ·
 *     *"হ্যাঁ আমি এবং যে কেউ বদলাতে পারবে"*।
 *
 * ─── লক করা সিদ্ধান্ত ─────────────────────────────────────────────────────
 *  • রেডিমেড দর অ্যাপের ভিতরেই (`DEFAULTS`) — কিছু না করলেও কাজ চলে।
 *  • কেউ বদলালে সেটা **ওই ফোনেই** জমা থাকে (SharedPreferences)।
 *    TK-এর নিজের সিদ্ধান্ত — *"যে ফোনে বদলাবেন শুধু সেই ফোনেই থাকবে"* ⇒
 *    ⛔ কোনো নতুন টেবিল · কলাম · SQL লাগে না, ফ্রি প্ল্যানে চাপও পড়ে না।
 *  • ⚠️ সৎ সীমা: এক ব্রাঞ্চে দর বদলালে অন্য ব্রাঞ্চে বদলায় না, আর অ্যাপ
 *    মুছে ফেললে রেডিমেড দরই ফিরে আসে — TK-কে আগেই জানানো হয়েছে।
 *  • "Reset to default" চাপলে রেডিমেড তালিকাটাই ফিরে আসে।
 */
object EstimatePrices {

    /** কোন দলে পড়ে — পর্দায় এই দল ধরেই ভাগ দেখানো হয়। */
    const val G_PILES = "Piles"
    const val G_FISTULA = "Fistula"
    const val G_FISSURE = "Fissure"
    const val G_HYDROCELE = "Hydrocele"
    const val G_MEDICINE = "Medicine"
    const val G_OTHER = "Other"

    val DISEASE_GROUPS = listOf(G_PILES, G_FISTULA, G_FISSURE, G_HYDROCELE)
    val ALL_GROUPS = DISEASE_GROUPS + listOf(G_MEDICINE, G_OTHER)

    /**
     * একটা দরের সারি।
     *  · [group]  — কোন দলে (উপরের ধ্রুবক)
     *  · [name]   — কাগজে যেভাবে ছাপা হবে
     *  · [rate]   — এক এককের দর
     *  · [unit]   — "per position" · "per inch" · "per day" · "per piece" …
     *  · [measure]— পাইলসে "Grade II", ফিস্টুলায় ইঞ্চি — ফাঁকা হলে মাপ লাগে না
     */
    data class Item(
        val group: String,
        val name: String,
        val rate: Double,
        val unit: String,
        val measure: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("group", group).put("name", name)
            .put("rate", rate).put("unit", unit).put("measure", measure)
    }

    /* ⛔ পাহারা [৯.১০]-এর নিয়মে সোজা ফাংশন, `companion object` নয়। */
    fun itemFrom(o: JSONObject): Item = Item(
        group = o.optString("group", G_OTHER),
        name = o.optString("name", ""),
        rate = o.optDouble("rate", 0.0),
        unit = o.optString("unit", ""),
        measure = o.optString("measure", "")
    )

    /* 🔒 রেডিমেড তালিকা — TK-এর পাঠানো নমুনা PDF-এর জিনিস ও দর ধরে।
       ⚠️ দরগুলো নমুনার, TK যখন খুশি অ্যাপ থেকেই বদলাতে পারবেন। */
    val DEFAULTS: List<Item> = listOf(
        Item(G_PILES, "Grade I Haemorrhoid Treatment", 4100.0, "per position", "Grade I"),
        Item(G_PILES, "Grade II Haemorrhoid Treatment", 8210.0, "per position", "Grade II"),
        Item(G_PILES, "Grade III Haemorrhoid Treatment", 12312.0, "per position", "Grade III"),
        Item(G_PILES, "Grade IV Haemorrhoid Treatment", 16400.0, "per position", "Grade IV"),

        Item(G_FISTULA, "Fistula Treatment", 3800.0, "per inch", "inch"),
        Item(G_FISSURE, "Fissure Treatment", 6500.0, "per position", ""),
        Item(G_HYDROCELE, "Hydrocele Treatment", 11000.0, "per side", ""),

        Item(G_MEDICINE, "Q-Alkali", 5.20, "per piece"),
        Item(G_MEDICINE, "Pow. Laxall", 125.0, "per piece"),
        Item(G_MEDICINE, "Tab. Kankayan (Arsha) Vati", 2.25, "per piece"),
        Item(G_MEDICINE, "Tab. Arshakuthar Ras", 3.24, "per piece"),
        Item(G_MEDICINE, "Jatyadi Ghritam", 225.0, "per piece"),

        Item(G_OTHER, "Dressing Cost", 300.0, "per day"),
        Item(G_OTHER, "Nursing Charges", 2521.0, "per day")
    )

    private const val PREF = "piles_estimate_prices"
    private const val KEY = "items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** এই ফোনের চালু তালিকা — বদলানো না থাকলে রেডিমেডটাই। */
    fun list(context: Context): List<Item> {
        val raw = prefs(context).getString(KEY, null).orEmpty()
        if (raw.isBlank()) return DEFAULTS
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Item>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val item = itemFrom(o)
                if (item.name.isNotBlank()) out.add(item)
            }
            if (out.isEmpty()) DEFAULTS else out
        } catch (_: Throwable) { DEFAULTS }
    }

    fun save(context: Context, items: List<Item>) {
        try {
            val arr = JSONArray()
            for (i in items) if (i.name.isNotBlank()) arr.put(i.toJson())
            prefs(context).edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) { }
    }

    /** রেডিমেড তালিকায় ফিরে যাওয়া। */
    fun reset(context: Context) {
        try { prefs(context).edit().remove(KEY).apply() } catch (_: Throwable) { }
    }

    fun inGroup(context: Context, group: String): List<Item> =
        list(context).filter { it.group == group }

    /** নাম ধরে দর (না পেলে ০)। */
    fun rateOf(context: Context, name: String): Double =
        list(context).firstOrNull { it.name.equals(name, ignoreCase = true) }?.rate ?: 0.0
}
