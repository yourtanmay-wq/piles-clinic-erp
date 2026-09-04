package com.tkbiswas.pilesclinic.clinical

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 💰🔒 V971 (০২.০৯.২০২৬, TK-অনুমোদিত) — **এস্টিমেটের ভাঙা হিসাব।**
 *
 * TK-এর লক করা নিয়ম:
 *  • লাইন বাছার পরেও **দর ও সংখ্যা বদলানো যাবে** — *"রেট অটোমেটিক বসে থাকবে,
 *    আমি চেঞ্জ করলে চেঞ্জ হবে"*।
 *  • যে লাইন **কাটা** (strike), তার টাকা কাগজে কাটা অবস্থায় দেখা যাবে কিন্তু
 *    **Subtotal-এ গোনা হবে না** — TK-এর PDF নমুনায় ঠিক তাই।
 *  • Net Payable = Subtotal − Discount; ওটাই চেকআপের "Estimated Cost" ঘরে বসে।
 *
 * ⛔ পুরো হিসাবটা চেকআপের **আগে থেকেই থাকা** JSON-এর (`patients.doctorFullNote`)
 *    ভিতরে একটা চাবিতে জমা হয় ⇒ নতুন কোনো টেবিল · কলাম · SQL লাগে না।
 * ⚠️ চাবির নামগুলো ওয়েবের সাথে **হুবহু** এক রাখতে হবে, নইলে এক পাশে লেখা
 *    অন্য পাশে পড়া যাবে না।
 */
object EstimateModel {

    /** JSON-এ যে চাবিতে পুরো এস্টিমেট বসে (ফোন ও ওয়েবে হুবহু এক)। */
    const val NOTE_KEY = "estimate"

    /**
     * এস্টিমেটের একটা সারি।
     *  · [name]     — কাগজে ছাপা নাম ("Grade II Haemorrhoid Treatment")
     *  · [measure]  — "Grade II" · "2.5 inch" — না থাকলে ফাঁকা
     *  · [position] — "3, 7, 11 o'clock" — না থাকলে ফাঁকা
     *  · [rate]     — এক এককের দর (স্টাফ বদলাতে পারেন)
     *  · [qty]      — কত একক (o'clock-এর সংখ্যা · ইঞ্চি · দিন · পিস)
     *  · [struck]   — কাটা কি না (গোনা হবে না)
     */
    data class Line(
        var name: String = "",
        var measure: String = "",
        var position: String = "",
        var rate: Double = 0.0,
        var qty: Double = 1.0,
        var struck: Boolean = false
    ) {
        val total: Double get() = rate * qty

        fun toJson(): JSONObject = JSONObject()
            .put("name", name).put("measure", measure).put("position", position)
            .put("rate", rate).put("qty", qty).put("struck", struck)
    }

    /* ⛔ ইচ্ছে করে `companion object`-এ নয় — প্রকল্পের পাহারা [৯.১০] ক্লাসের
       নামে ডাকা ফাংশন ধরে ফেলে (নাম-ভুল থেকে বিল্ড ভাঙা আটকাতে)। */
    fun lineFrom(o: JSONObject): Line = Line(
        name = o.optString("name", ""),
        measure = o.optString("measure", ""),
        position = o.optString("position", ""),
        rate = o.optDouble("rate", 0.0),
        qty = o.optDouble("qty", 1.0),
        struck = o.optBoolean("struck", false)
    )

    /** একটা রোগীর পুরো এস্টিমেট। */
    data class Sheet(
        val lines: MutableList<Line> = mutableListOf(),
        var discount: Double = 0.0,
        /* 💰🔒 V980 (০২.০৯.২০২৬, TK-নির্দেশ) — *"ডিসকাউন্ট আমি চাইলে ফিক্সড
           এমাউন্ট দিতে পারি, আমি চাইলে পার্সেন্টেজ হিসাবেও দিতে পারি"*।
           `false` = টাকা (আগের আচরণ), `true` = শতাংশ।
           ⛔ পুরনো সেভ করা হিসাবে এই ঘরটা নেই ⇒ `false` ⇒ হুবহু আগের মতোই। */
        var discountPct: Boolean = false,
        var finding: String = ""
    ) {
        /* 💰🔴🔒 V1062 (০৪.০৯.২০২৬, TK-নির্দেশ ছবিসহ: *"আমি চাইছিলাম Sub Total-এ
           without discount amount বসবে। তাহলে এক্ষেত্রে Sub Total হয় ₹17,038,
           এটা থেকে ₹2,350 বিয়োগ হয়ে Payable Amount ₹14,688 হবে"*)।
           🔴 **এটা শুধু সাজ ছিল না, টাকার ভুল ছিল** — কাটা লাইনগুলো Subtotal-এ
              ধরাই হত না, অথচ নিচে **আবার** ছাড় বিয়োগ হত ⇒ একই ছাড় **দুবার**,
              কাগজে Net Payable ঠিক ওই ছাড়ের পরিমাণ কম ছাপা হত (TK-এর কাগজে
              ₹১২,৩৩৮ — আসলে হওয়ার কথা ₹১৪,৬৮৮)।
           ⇒ এখন Subtotal = **সব লাইনের পুরো দাম** (কাটা লাইনসুদ্ধ), আর ছাড়
             একবারই বাদ যায়। কাটা দাগ আগের মতোই থাকে — সেটা শুধু দেখানোর।
           ⚠️ ফল: কোনো লাইন কাটা হলেও তার দাম যোগ হবে; টাকা কমাতে হলে
              **Discount ঘরে অঙ্কটা লিখতে হবে** — TK-এর বলা নিয়মই। */
        val subtotal: Double get() = lines.sumOf { it.total }

        /** শতাংশ হলে Subtotal-এর উপর হিসাব; কখনো Subtotal-এর বেশি নয়। */
        val discountAmount: Double get() =
            if (discountPct) (subtotal * discount / 100.0).coerceIn(0.0, subtotal)
            else discount.coerceIn(0.0, subtotal)

        val netPayable: Double get() = (subtotal - discountAmount).coerceAtLeast(0.0)
        val isEmpty: Boolean get() = lines.isEmpty()

        fun toJson(): JSONObject {
            val arr = JSONArray()
            for (l in lines) arr.put(l.toJson())
            return JSONObject()
                .put("lines", arr)
                .put("discount", discount)
                .put("discountPct", discountPct)
                .put("finding", finding)
        }
    }

    fun parse(raw: String?): Sheet {
        val s = Sheet()
        if (raw.isNullOrBlank()) return s
        return try { parse(JSONObject(raw)) } catch (_: Throwable) { s }
    }

    fun parse(o: JSONObject?): Sheet {
        val s = Sheet()
        if (o == null) return s
        try {
            val arr = o.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val li = arr.optJSONObject(i) ?: continue
                val line = lineFrom(li)
                if (line.name.isNotBlank()) s.lines.add(line)
            }
            s.discount = o.optDouble("discount", 0.0)
            s.discountPct = o.optBoolean("discountPct", false)
            s.finding = o.optString("finding", "")
        } catch (_: Throwable) { }
        return s
    }

    /** ১২,৩১২.০০ — কাগজের জন্য। */
    fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

    /** ১২,৩১২ — পর্দার জন্য (পয়সা না থাকলে দেখায় না)। */
    fun moneyShort(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) String.format(Locale.US, "%,.0f", v)
        else String.format(Locale.US, "%,.2f", v)

    /** "8210" · "8,210.00" · "৳ 8210" — সব থেকেই সংখ্যা বার করে। */
    fun num(text: String?): Double = try {
        val cleaned = text.orEmpty().replace(Regex("[^0-9.]"), "")
        if (cleaned.isBlank()) 0.0 else cleaned.toDouble()
    } catch (_: Throwable) { 0.0 }
}
