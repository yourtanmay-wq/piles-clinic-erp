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
        var struck: Boolean = false,
        /* 🔒 V1068 — কাটার সময় ছাড়ে **ঠিক কত টাকা** যোগ হয়েছিল। ফেরানোর সময়
           ঠিক ততটুকুই কমে, তাই মাঝখানে দাম/সংখ্যা বদলে গেলেও ছাড়ে ভুল টাকা
           পড়ে থাকে না (নিজে যাচাই করতে গিয়ে ধরা)। */
        var struckAmt: Double = 0.0
    ) {
        val total: Double get() = rate * qty

        fun toJson(): JSONObject = JSONObject()
            .put("name", name).put("measure", measure).put("position", position)
            .put("rate", rate).put("qty", qty).put("struck", struck)
            .put("struckAmt", struckAmt)
    }

    /* ⛔ ইচ্ছে করে `companion object`-এ নয় — প্রকল্পের পাহারা [৯.১০] ক্লাসের
       নামে ডাকা ফাংশন ধরে ফেলে (নাম-ভুল থেকে বিল্ড ভাঙা আটকাতে)। */
    fun lineFrom(o: JSONObject): Line = Line(
        name = o.optString("name", ""),
        measure = o.optString("measure", ""),
        position = o.optString("position", ""),
        rate = o.optDouble("rate", 0.0),
        qty = o.optDouble("qty", 1.0),
        struck = o.optBoolean("struck", false),
        struckAmt = o.optDouble("struckAmt", 0.0)   // 🔒 V1068
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

        /* 💰🔒 V1063 (০৪.০৯.২০২৬, TK-নির্দেশ: *"কাটলেই টাকাটা নিজে থেকে
           Discount-এ বসে যাক"*) — লাইন কাটলে/ফেরালে ছাড়ের **ঘরের অঙ্কটাই**
           বদলায়, তাই TK চোখে দেখতে পান কত ছাড় হলো, আর টাকা **একবারই** বাদ যায়।
           ⛔ শতাংশে থাকলে আগে ওই শতাংশটা টাকায় বদলে নেওয়া হয় (অঙ্ক এক থাকে),
              তারপর যোগ — নইলে টাকা ও শতাংশ মিশে ভুল হত।
           ⛔ ছাড় কখনো ঋণাত্মক হয় না; ফেরালে ঠিক ওই অঙ্কটুকুই কমে। */
        fun onStrikeToggled(line: Line, nowStruck: Boolean) {
            if (discountPct) {
                discount = (subtotal * discount / 100.0).coerceIn(0.0, subtotal)
                discountPct = false
            }
            /* 🔒 V1068 — যোগ হয় **এখনকার** দাম, আর কমে **যা যোগ হয়েছিল ঠিক
               সেটুকুই** — মাঝে দাম বদলালেও ছাড়ে বাড়তি টাকা থেকে যায় না। */
            if (nowStruck) {
                line.struckAmt = line.total
                discount = (discount + line.struckAmt).coerceAtLeast(0.0)
            } else {
                val back = if (line.struckAmt > 0.0) line.struckAmt else line.total
                line.struckAmt = 0.0
                discount = (discount - back).coerceAtLeast(0.0)
            }
        }
        val isEmpty: Boolean get() = lines.isEmpty()

        fun toJson(): JSONObject {
            val arr = JSONArray()
            for (l in lines) arr.put(l.toJson())
            return JSONObject()
                .put("lines", arr)
                .put("discount", discount)
                .put("discountPct", discountPct)
                /* 🔒 V1064 — এই কাগজটা **নতুন নিয়মে** তৈরি, তাই খোলার সময় আর
                   পুরনো-হিসাব-মেলানো লাগবে না (নিচের `parse()` দেখুন)। */
                .put("strikeInDiscount", true)
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
            /* 💰🔴🔒 V1064 (০৪.০৯.২০২৬ — নিজে যাচাই করতে গিয়ে ধরা) — **পুরনো
               সেভ করা এস্টিমেটের টাকা যেন এক পয়সাও না বদলায়।**
               পুরনো নিয়মে কাটা লাইনের টাকা **নিজে থেকেই** বাদ যেত, তাই তখন
               Discount ঘরে কিছু লেখা না-ও থাকতে পারে। V1062-এর পরে ওই কাগজ
               খুললে Subtotal-এ কাটা লাইনও ধরা হত, অথচ ছাড় বাড়ত না ⇒ **রোগীর
               পুরনো কাগজের Net Payable বেড়ে যেত** — এটা হতে দেওয়া যায় না।
               ⇒ যে কাগজে `strikeInDiscount` চিহ্নটা **নেই** (মানে পুরনো নিয়মে
                 তৈরি), সেটায় খোলার সময় কাটা লাইনগুলোর টাকা **একবার** ছাড়ে
                 যোগ করে নেওয়া হয়। ফল: Net Payable **হুবহু আগের মতোই**।
               ⛔ শতাংশে থাকলে আগে টাকায় বদলে নেওয়া হয় (অঙ্ক এক থাকে)।
               ⛔ নতুন কাগজে চিহ্নটা থাকে, তাই দুবার যোগ হওয়ার পথ নেই। */
            if (!o.optBoolean("strikeInDiscount", false)) {
                val struckTotal = s.lines.filter { it.struck }.sumOf { it.total }
                if (struckTotal > 0.0) {
                    if (s.discountPct) {
                        /* 🔴 V1064খ (নিজে চালিয়ে ধরা) — পুরনো নিয়মে শতাংশটা
                           **কাটা-বাদ-দেওয়া** যোগফলের উপর কষা হত, পুরো দামের
                           উপর নয়। তাই পুরনো অঙ্ক ফেরাতে হলে ওই ভিত্তিটাই
                           নিতে হবে — নইলে ছাড় বেশি হয়ে টাকা কমে যেত। */
                        val base = s.lines.filter { !it.struck }.sumOf { it.total }
                        s.discount = (base * s.discount / 100.0).coerceIn(0.0, base)
                        s.discountPct = false
                    }
                    s.discount = (s.discount + struckTotal).coerceAtLeast(0.0)
                }
            }
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
