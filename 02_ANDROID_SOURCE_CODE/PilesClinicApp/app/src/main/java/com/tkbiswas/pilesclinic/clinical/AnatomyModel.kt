package com.tkbiswas.pilesclinic.clinical

/**
 * 🔵🔒 V558 (২২.০৮.২০২৬, TK-অনুমোদিত) — **রোগের ছবি**
 *
 * TK-এর কথা (হুবহু):
 *   • *"ডাক্তার যখন ডেমো হিসেবে পেসেন্টকে সমস্যার কথা খুলে বলবে, ডাক্তার
 *     চাইলে যে কোন ফটোর উপর যেন সেই কাজটা করতে পারে"*
 *   • *"শুধু দাগ বলে কথা নয় — কোন পেশেন্টের পাইলস কতটা বেড়ে গেছে, সেই
 *     মাংসের উপরে আঙুল দিয়ে টান দিলে যেন মাংস বেড়ে যায়"*
 *
 * তাই এখানে দু'রকম কাজ:
 *   ১. **দাগ** — ফোলার চিহ্ন · নালীর রেখা · গোল দাগ · তীর · হাতের আঁকা
 *   ২. **ফোলানো (bulge)** — ছবির ওই জায়গার পিক্সেলগুলোই বাইরের দিকে ঠেলে
 *      দেওয়া হয়, তাই সত্যিকারের ফোলার মত দেখায় (দাগ আঁকা নয়)
 *
 * 🧷 জমা হওয়ার নিয়ম — V539/V554/V555/V556-এর মতোই **একটাই লেখা**, যা
 * চেকআপের `buildDetails()`-এর ভিতরে চলে যায়। তাই:
 *   ⛔ নতুন কোনো কলাম লাগেনি · ⛔ কোনো SQL চালাতে হয়নি ·
 *   ⛔ Supabase-এ বাড়তি query/egress বাড়েনি।
 *
 * 📐 সব মাপ **ছবির শতকরা** হিসেবে (০–১০০), পিক্সেল নয়। তাই ছোট ফোন,
 * বড় ফোন আর ওয়েব — তিন জায়গাতেই দাগ ঠিক একই জায়গায় বসে।
 *
 * ⚠️ ওয়েবের যমজ কোড: `03_NETLIFY_READY/app.js` → `wlv1Anat*`.
 *    দুটোর লেখা **হুবহু এক** হতে হবে — পরীক্ষায় সেটাই মিলিয়ে দেখা হয়।
 */
object AnatomyModel {

    /** এক-একটা দাগ। শতকরা মাপে। */
    data class Mark(
        val kind: String,                 // pile · tract · pen · ring · arrow · bulge
        val x: Double = 0.0,
        val y: Double = 0.0,
        val x2: Double = 0.0,             // arrow-এর শেষ মাথা
        val y2: Double = 0.0,
        val r: Double = 0.0,              // ring / bulge-এর ব্যাসার্ধ
        val s: Double = 0.0,              // bulge কতটা জোরে (−০.৮৫ … ০.৮৫)
        val label: String = "",           // ফোলার নাম (৩টা / ডান পাশ …)
        val pts: List<Pair<Double, Double>> = emptyList()
    )

    /** পুরো ছবিটার অবস্থা: কোন ছবি, কী কী দাগ, ডাক্তারের নিজের কথা। */
    data class Board(
        val pic: String = "",
        val marks: List<Mark> = emptyList(),
        val note: String = ""
    )

    const val KIND_PILE  = "pile"
    const val KIND_TRACT = "tract"
    const val KIND_PEN   = "pen"
    const val KIND_RING  = "ring"
    const val KIND_ARROW = "arrow"
    const val KIND_BULGE = "bulge"

    /** ফোলানোর সীমা — এর বেশি টানলে ছবি ভেঙে যায়, তাই আটকানো। */
    const val BULGE_MAX = 0.85
    const val RADIUS_MAX = 26.0
    const val RADIUS_MIN = 3.0


    // ─────────── ছবির তালিকা ───────────

    /** এক-একটা ছবি: `key` জমা হয়, `label` ডাক্তার পর্দায় দেখেন। */
    data class Picture(val key: String, val label: String)

    /**
     * TK-এর পাঠানো ছবি — ২২টা চেম্বারের আসল ছবি ও ৩টা বইয়ের ছবি।
     * ডাক্তার রোগী দেখে যেটার সাথে মেলে সেটাই বেছে নেবেন।
     * ⚠️ `key` কখনো বদলাবেন না — পুরনো রোগীর জমা লেখায় ওই নামই আছে,
     *    বদলালে তাদের ছবি আর খুঁজে পাওয়া যাবে না।
     * ফোনে ছবিটা `<key>` (যেমন anat01), ওয়েবে `img/anatomy/<key>.jpg`.
     */
    val PICTURES: List<Picture> = listOf(
        Picture("anat01", "বই · পায়ুনালীর কাটা ছবি"),
        Picture("anat02", "বই · ফিস্টুলার ৪ ধরন"),
        Picture("anat03", "বই · ফিস্টুলার নকশা"),
        Picture("anat04", "ফোলা · কাছ থেকে"),
        Picture("anat05", "একটা ফোলা"),
        Picture("anat06", "একটা ঢিবি"),
        Picture("anat07", "ছোট ফোলা"),
        Picture("anat08", "ফোলা · চওড়া ছবি"),
        Picture("anat09", "লাল · বেরিয়ে আসা"),
        Picture("anat10", "বেরিয়ে আসা · বেগুনি"),
        Picture("anat11", "চারপাশ জুড়ে বেরিয়ে আসা"),
        Picture("anat12", "চারপাশ জুড়ে · চওড়া ছবি"),
        Picture("anat13", "ভিতরের পর্দা বেরিয়ে আসা"),
        Picture("anat14", "অনেকটা বেরিয়ে আসা"),
        Picture("anat15", "খুব বড় মাংস"),
        Picture("anat16", "ঘা হয়ে যাওয়া মাংস"),
        Picture("anat17", "আঁচিলের মত মাংস"),
        Picture("anat18", "অনেকগুলো আঁচিল"),
        Picture("anat19", "কয়েকটা ফোলা"),
        Picture("anat20", "ফিস্টুলার মুখ"),
        Picture("anat21", "ফিস্টুলা · নালী"),
        Picture("anat22", "ছোট মুখ"),
        Picture("anat23", "রস গড়াচ্ছে"),
        Picture("anat24", "চিকিৎসার পরে"),
        Picture("anat25", "অপারেশনের সময়"),
    )

    fun labelOf(key: String): String = PICTURES.firstOrNull { it.key == key }?.label ?: key

    // ─────────── জমা করা ও পড়া ───────────

    fun format(board: Board): String {
        val out = StringBuilder()
        if (board.pic.isNotBlank()) out.append("pic=").append(board.pic)
        for (m in board.marks) {
            if (out.isNotEmpty()) out.append('|')
            when (m.kind) {
                KIND_TRACT, KIND_PEN -> {
                    out.append(m.kind).append(':')
                    out.append(m.pts.joinToString(";") { "${n1(it.first)},${n1(it.second)}" })
                }
                KIND_RING  -> out.append("ring:").append(n1(m.x)).append(',').append(n1(m.y)).append(',').append(n1(m.r))
                KIND_ARROW -> out.append("arrow:").append(n1(m.x)).append(',').append(n1(m.y))
                    .append(',').append(n1(m.x2)).append(',').append(n1(m.y2))
                KIND_BULGE -> out.append("bulge:").append(n1(m.x)).append(',').append(n1(m.y))
                    .append(',').append(n1(m.r)).append(',').append(n2(m.s))
                else -> out.append("pile:").append(n1(m.x)).append(',').append(n1(m.y)).append(',').append(m.label)
            }
        }
        if (board.note.isNotBlank()) {
            if (out.isNotEmpty()) out.append('|')
            out.append("note=").append(board.note.replace("|", "/"))
        }
        return out.toString()
    }

    fun parse(saved: String?): Board {
        if (saved.isNullOrBlank()) return Board()
        var pic = ""; var note = ""
        val marks = mutableListOf<Mark>()
        for (raw in saved.split("|")) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("pic=")) { pic = t.substring(4); continue }
            if (t.startsWith("note=")) { note = t.substring(5); continue }
            val c = t.indexOf(':')
            if (c < 0) continue
            val kind = t.substring(0, c)
            val body = t.substring(c + 1)
            when (kind) {
                KIND_TRACT, KIND_PEN -> {
                    val pts = body.split(";").mapNotNull { p ->
                        val a = p.split(",")
                        if (a.size < 2) null else {
                            val x = a[0].trim().toDoubleOrNull(); val y = a[1].trim().toDoubleOrNull()
                            if (x == null || y == null) null else Pair(x, y)
                        }
                    }
                    if (pts.size > 1) marks.add(Mark(kind = kind, pts = pts))
                }
                KIND_RING -> {
                    val a = body.split(",")
                    if (a.size >= 3) marks.add(Mark(KIND_RING, x = d(a[0]), y = d(a[1]), r = d(a[2])))
                }
                KIND_ARROW -> {
                    val a = body.split(",")
                    if (a.size >= 4) marks.add(Mark(KIND_ARROW, x = d(a[0]), y = d(a[1]), x2 = d(a[2]), y2 = d(a[3])))
                }
                KIND_BULGE -> {
                    val a = body.split(",")
                    if (a.size >= 4) marks.add(Mark(KIND_BULGE, x = d(a[0]), y = d(a[1]), r = d(a[2]), s = d(a[3])))
                }
                KIND_PILE -> {
                    val a = body.split(",")
                    if (a.size >= 2) marks.add(
                        Mark(KIND_PILE, x = d(a[0]), y = d(a[1]),
                             label = if (a.size > 2) a.drop(2).joinToString(",") else "")
                    )
                }
            }
        }
        return Board(pic, marks, note)
    }

    // ─────────── আঙুলের টান → ফোলা কত বড় ───────────

    /**
     * ডাক্তার যেখানে আঙুল রাখলেন (startX,startY) আর যেখানে টেনে নিলেন
     * (nowX,nowY) — সেই দূরত্ব থেকেই ফোলার মাপ। যত টান, তত বড়।
     */
    fun bulgeFromDrag(startX: Double, startY: Double, nowX: Double, nowY: Double): Mark {
        val dx = nowX - startX; val dy = nowY - startY
        val pull = Math.sqrt(dx * dx + dy * dy)
        return Mark(
            kind = KIND_BULGE, x = startX, y = startY,
            r = clamp(4.0 + pull * 1.35, RADIUS_MIN, RADIUS_MAX),
            s = clamp(0.16 + pull * 0.055, 0.12, 0.80)
        )
    }

    /** নালীর লম্বা — ছবির গায়ের মাপকাঠি অনুযায়ী সেন্টিমিটারে। */
    fun tractCm(pts: List<Pair<Double, Double>>, cmPerPct: Double): Double {
        var sum = 0.0
        for (i in 1 until pts.size) {
            val dx = pts[i].first - pts[i - 1].first
            val dy = pts[i].second - pts[i - 1].second
            sum += Math.sqrt(dx * dx + dy * dy)
        }
        return Math.round(sum * cmPerPct * 10.0) / 10.0
    }

    // ─────────── মানুষ-পড়া-যায় লেখা (প্রিন্ট ও হিস্ট্রির জন্য) ───────────

    fun readable(saved: String?): String {
        val b = parse(saved)
        if (b.pic.isBlank() && b.marks.isEmpty() && b.note.isBlank()) return ""
        val bits = mutableListOf<String>()
        val piles  = b.marks.count { it.kind == KIND_PILE }
        val bulges = b.marks.count { it.kind == KIND_BULGE }
        val tracts = b.marks.count { it.kind == KIND_TRACT }
        if (piles > 0) {
            val names = b.marks.filter { it.kind == KIND_PILE && it.label.isNotBlank() }.map { it.label }
            bits.add(if (names.isEmpty()) "ফোলা $piles টা" else "ফোলা: ${names.joinToString(", ")}")
        }
        if (bulges > 0) bits.add("মাংস ফোলানো $bulges টা")
        if (tracts > 0) bits.add("নালীর দাগ $tracts টা")
        if (b.note.isNotBlank()) bits.add(b.note)
        return bits.joinToString(" · ")
    }

    /** ছবিতে কিছু আঁকা হয়েছে কি না — খালি হলে প্রিন্টে কিছুই যাবে না। */
    fun isEmpty(saved: String?): Boolean = parse(saved).marks.isEmpty()

    // ─────────── ছোট সাহায্যকারী ───────────

    private fun d(s: String): Double = s.trim().toDoubleOrNull() ?: 0.0
    private fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

    /** ফোলার জোর দুই দশমিক ঘরে — এক ঘরে লিখলে ০.৮৫ হয়ে যেত ০.৯,
        সীমা ছাড়িয়ে যেত আর ডাক্তার যা টেনেছিলেন তার চেয়ে বেশি ফুলত। */
    fun n2(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r) && !r.isInfinite()) r.toLong().toString() else r.toString()
    }

    /** এক দশমিক ঘর — ওয়েবের `n1()`-এর হুবহু একই আচরণ। */
    fun n1(v: Double): String {
        val r = Math.round(v * 10.0) / 10.0
        return if (r == Math.floor(r) && !r.isInfinite()) r.toLong().toString() else r.toString()
    }
}
