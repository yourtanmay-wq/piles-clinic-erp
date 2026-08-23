package com.tkbiswas.pilesclinic.clinical

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * 🔵🔒 V587 (২৩.০৮.২০২৬, TK-অনুমোদিত ডেমো-প্রুফের পরে) — **ক্ষারসূত্রের ধাপ
 * রোগীকে দেখানোর ছবি আঁকার একমাত্র জায়গা।**
 *
 * TK-এর কথা (হুবহু): *"টেনে ধরেন পাইলসের মাংস তৈরি করলাম, এবার ক্ষার সূত্র
 * দিয়ে বেঁধে দিব, সেটাও যেন অ্যানিমেশন করে দেখানো যায়"* · *"মাংসতে ইনজেকশন
 * লাগাবো তখন মাংসটা ফুলে যাবে আরো, তারপর সুতো দিয়ে বাঁধবো, বাধাটা পরিষ্কার
 * বোঝা যাবে ... তারপর যখন কেটে পড়বে তখন জায়গাটা ক্লিয়ার হয়ে গেল"* ·
 * *"ফিস্টুলার নালী ... এই নালী বরাবর ক্ষারসূত্র বেঁধে রাখা যাবে"*।
 *
 * TK-এর বাছাই: পুরো পর্দায় চলবে · একটা ফোলা/নালী **ছুঁয়ে বেছে** নেওয়া হয় ·
 * **চাপ দিলে পরের ধাপ** · ইনজেকশনের ধাপটা বাদ দেওয়া যায় · অ্যানিমেশনটা
 * **ডাক্তারের নিজের আঁকা** মাংস/নালীর উপরেই চলে।
 *
 * ⛔ **ঝুঁকিহীন:** এটা সম্পূর্ণ নতুন, আলাদা ফাইল — শুধু আঁকে। কোনো দাগ যোগ
 * হয় না, কিছু সেভ হয় না, A4/প্রিন্টেও যায় না। ফর্ম-ফিল্ড · সেভ-লজিক ·
 * ডেটাবেস কিছুই ছোঁয়া হয়নি। নেটওয়ার্ক কল নেই।
 * ⚠️ ওয়েবের `wlv1Ks*`-এর হুবহু যমজ (একই মাপ, একই ক্রম)।
 */
object KsharSutraAnim {

    // ── মাংসের ধাপ ──
    const val LUMP_DRAWN  = 1   // ডাক্তার যেভাবে এঁকেছেন
    const val LUMP_INJECT = 2   // ইনজেকশন
    const val LUMP_SWELL  = 3   // মাংস আরো ফুলল
    const val LUMP_TIE    = 4   // গোড়ায় সুতো বাঁধা
    const val LUMP_FALL   = 5   // কেটে পড়ল · জায়গা পরিষ্কার
    // ── নালীর ধাপ ──
    const val TRACT_DRAWN = 11
    const val TRACT_LACE  = 12  // নালী বরাবর সুতো পরানো
    const val TRACT_TIE   = 13  // দুই মাথায় গিঁট
    const val TRACT_CUT   = 14  // কেটে গেল · পরিষ্কার

    /** এই ধাপে ডাক্তারকে কী লেখা দেখানো হবে। */
    fun caption(step: Int): String = when (step) {
        LUMP_DRAWN  -> "1) যেভাবে আঁকা হয়েছে"
        LUMP_INJECT -> "2) ইনজেকশন দেওয়া হচ্ছে"
        LUMP_SWELL  -> "3) মাংস আরো ফুলে উঠল"
        LUMP_TIE    -> "4) গোড়ায় ক্ষারসূত্র বাঁধা হলো"
        LUMP_FALL   -> "5) কেটে পড়ল — জায়গা পরিষ্কার"
        TRACT_DRAWN -> "1) ফিস্টুলার নালী"
        TRACT_LACE  -> "2) নালী বরাবর ক্ষারসূত্র পরানো হচ্ছে"
        TRACT_TIE   -> "3) দুই মাথায় গিঁট — বেঁধে রাখা হলো"
        TRACT_CUT   -> "4) নালী কেটে গেল — জায়গা পরিষ্কার"
        else -> ""
    }

    /** এই চিহ্নের জন্য ধাপগুলোর ক্রম। `withInjection=false` হলে ইনজেকশন বাদ। */
    fun stepsFor(kind: String, withInjection: Boolean): List<Int> = when (kind) {
        AnatomyModel.KIND_BULGE, AnatomyModel.KIND_PILE ->
            if (withInjection) listOf(LUMP_DRAWN, LUMP_INJECT, LUMP_SWELL, LUMP_TIE, LUMP_FALL)
            else listOf(LUMP_DRAWN, LUMP_TIE, LUMP_FALL)
        AnatomyModel.KIND_TRACT -> listOf(TRACT_DRAWN, TRACT_LACE, TRACT_TIE, TRACT_CUT)
        else -> emptyList()
    }

    /** এই ধাপে মাংসটা কতটা ফোলা দেখাবে (t = ০…১)। */
    fun lumpStrength(base: Double, step: Int, t: Float): Double = when (step) {
        LUMP_SWELL -> base + (0.85 - base) * t
        LUMP_TIE, LUMP_FALL -> 0.85
        else -> base
    }

    /** এই ধাপে মাংসটা কতটা নিচে নেমেছে (শতাংশে) ও কতটা ফিকে। */
    fun lumpDrop(step: Int, t: Float): Double = if (step == LUMP_FALL) 30.0 * t * t else 0.0
    fun lumpAlpha(step: Int, t: Float): Float = if (step == LUMP_FALL) 1f - t else 1f

    // ─────────────────────────────────────────────────────────────────────
    // আঁকা
    // ─────────────────────────────────────────────────────────────────────

    /**
     * গোড়া থেকে `d` দূরত্বে মাংসটার অর্ধেক-চওড়া কত।
     * ⛔ আন্দাজে নয় — `AnatomyView.buildLumpPath()` যে বাঁকটা আঁকে, ঠিক সেই
     *    quadratic Bezier-এর উপরেই মাপা হয়, তাই সুতোটা গায়ে বসে।
     */
    private fun halfWidthAt(len: Float, wide: Float, d: Float): Float {
        val hw = wide / 2f
        val cx = len - hw
        if (cx <= hw * 0.30f) return hw
        val beta = Math.acos(Math.max(-1.0, Math.min(1.0, (hw / cx).toDouble())))
        val a1 = -(Math.PI - beta)
        val p1x = (cx + hw * Math.cos(a1)).toFloat()
        val p1y = (hw * Math.sin(a1)).toFloat()
        val tip = Math.min(hw * 0.22f, cx * 0.10f)
        val p0x = tip * 0.25f; val p0y = -tip
        val ccx = cx * 0.40f;  val ccy = p1y * 0.70f
        var best = 0f; var bd = Float.MAX_VALUE
        for (i in 0..60) {
            val t = i / 60f; val mt = 1f - t
            val x = mt * mt * p0x + 2f * mt * t * ccx + t * t * p1x
            val y = mt * mt * p0y + 2f * mt * t * ccy + t * t * p1y
            val dd = Math.abs(x - d)
            if (dd < bd) { bd = dd; best = Math.abs(y) }
        }
        return Math.max(best, hw * 0.18f)
    }

    /** গোড়ায় বাঁধা ক্ষারসূত্র। ক্যানভাস ইতিমধ্যে মাংসের গোড়ায় ও কোণে ঘোরানো। */
    fun drawThread(canvas: Canvas, p: Paint, len: Float, wide: Float, tight: Float) {
        val d = len * 0.20f
        val hw = halfWidthAt(len, wide, d) * (1.10f - 0.22f * tight)
        val rx = wide * 0.075f
        // মাংসের গায়ে বসা চাপের খাঁজ
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE
        p.color = Color.argb(90, 90, 40, 36)
        p.strokeWidth = Math.max(1.6f, wide * 0.13f)
        canvas.drawOval(RectF(d - wide * 0.10f, -hw, d + wide * 0.10f, hw), p)
        // সুতো
        p.color = Color.parseColor("#2F3A45")
        p.strokeWidth = Math.max(1.4f, wide * 0.085f)
        canvas.drawOval(RectF(d - rx, -hw, d + rx, hw), p)
        p.color = Color.parseColor("#5A6874")
        p.strokeWidth = Math.max(0.7f, wide * 0.035f)
        canvas.drawOval(RectF(d - rx, -hw * 0.94f, d + rx, hw * 0.94f), p)
        // গিঁট ও দুটো মাথা
        val kx = d; val ky = hw * 0.92f
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#2F3A45")
        canvas.drawCircle(kx, ky, Math.max(1.6f, wide * 0.075f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = Math.max(1.1f, wide * 0.055f)
        val path = android.graphics.Path()
        path.moveTo(kx, ky)
        path.quadTo(kx - wide * 0.30f, ky + wide * 0.34f, kx - wide * 0.55f, ky + wide * 0.30f)
        path.moveTo(kx, ky)
        path.quadTo(kx + wide * 0.10f, ky + wide * 0.40f, kx + wide * 0.34f, ky + wide * 0.46f)
        canvas.drawPath(path, p)
    }

    /** ইনজেকশনের সুচ ও সিরিঞ্জ (p = ০…১, দূর থেকে এগিয়ে আসে)। */
    fun drawNeedle(canvas: Canvas, p: Paint, len: Float, wide: Float, prog: Float) {
        val u = Math.max(wide * 0.16f, len * 0.05f)
        val tipX = len * 0.55f; val tipY = wide * 0.10f
        val back = u * 4.2f * (1f - prog)
        val ang = -0.70
        val tx = tipX + (Math.cos(ang) * back).toFloat()
        val ty = tipY + (Math.sin(ang) * back).toFloat()
        val save = canvas.save()
        canvas.translate(tx, ty)
        canvas.rotate(Math.toDegrees(ang).toFloat())
        p.reset(); p.isAntiAlias = true
        // সুচ
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.parseColor("#8E9AA6"); p.strokeWidth = Math.max(1.0f, u * 0.16f)
        canvas.drawLine(0f, 0f, u * 3.0f, 0f, p)
        // হাব
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#3E4A57")
        canvas.drawRect(u * 3.0f, -u * 0.32f, u * 3.7f, u * 0.32f, p)
        // নল
        p.color = Color.argb(245, 226, 240, 250)
        canvas.drawRect(u * 3.7f, -u * 0.62f, u * 8.3f, u * 0.62f, p)
        p.style = Paint.Style.STROKE; p.color = Color.parseColor("#6E7C8A")
        p.strokeWidth = Math.max(0.7f, u * 0.09f)
        canvas.drawRect(u * 3.7f, -u * 0.62f, u * 8.3f, u * 0.62f, p)
        // ভিতরের ওষুধ
        p.style = Paint.Style.FILL; p.color = Color.argb(215, 150, 205, 235)
        canvas.drawRect(u * 3.75f, -u * 0.50f, u * 6.35f, u * 0.50f, p)
        // পিস্টন
        p.color = Color.parseColor("#54626F")
        canvas.drawRect(u * 6.4f, -u * 0.52f, u * 6.9f, u * 0.52f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = Math.max(0.8f, u * 0.13f)
        canvas.drawLine(u * 6.9f, 0f, u * 8.6f, 0f, p)
        canvas.drawLine(u * 8.6f, -u * 0.62f, u * 8.6f, u * 0.62f, p)
        canvas.restoreToCount(save)
        // ঢোকার জায়গায় ছোট্ট ফোঁটা
        if (prog > 0.92f) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
            p.color = Color.argb(140, 120, 190, 225)
            canvas.drawCircle(tipX, tipY, u * 0.55f, p)
        }
    }

    /** কেটে পড়ার পরে গোড়ায় যে পরিষ্কার দাগটুকু থাকে। */
    fun drawCleanSpot(canvas: Canvas, p: Paint, len: Float, wide: Float, t: Float) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.color = Color.argb((140 * t).toInt().coerceIn(0, 255), 196, 150, 142)
        canvas.drawOval(RectF(len * 0.16f - wide * 0.10f, -wide * 0.20f,
                              len * 0.16f + wide * 0.10f,  wide * 0.20f), p)
    }
}
