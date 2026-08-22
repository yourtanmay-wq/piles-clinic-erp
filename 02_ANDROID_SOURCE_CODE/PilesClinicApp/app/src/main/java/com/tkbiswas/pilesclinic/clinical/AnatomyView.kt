package com.tkbiswas.pilesclinic.clinical

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 🔵🔒 V558 (TK-অনুমোদিত) — **রোগের ছবির উপরে আঁকার পর্দা**
 *
 * TK-এর দুটো কথা এখানেই বাস্তব হয়:
 *   ১. *"যে কোন ফটোর উপর যেন সেই কাজটা করতে পারে"* → ছবিটা বাইরে থেকে
 *      বসানো হয় (`setPicture`), তাই বইয়ের ছবি বা চেম্বারের আসল ছবি —
 *      যেটাই হোক, একই ভাবে কাজ করে।
 *   ২. *"মাংসের উপরে আঙুল দিয়ে টান দিলে যেন মাংস বেড়ে যায়"* → এটা দাগ
 *      আঁকা নয়; ছবির ওই জায়গার পিক্সেলগুলোকেই বাইরের দিকে ঠেলে দেওয়া হয়
 *      (`applyBulge`), তাই সত্যিকারের ফোলার মত দেখায়।
 *
 * ⚡ গতির জন্য: ফোলানো ছবিটা প্রতিবার আঁকার সময় নতুন করে বানানো হয় না —
 *   দাগ বদলালে তবেই (`dirty`) একবার বানানো হয়। আর যতটুকু জায়গায় ফোলা,
 *   ঠিক ততটুকু চৌকো অংশেরই হিসাব হয়, গোটা ছবির নয়।
 *
 * 📐 সব দাগ ছবির **শতকরা** মাপে জমা থাকে (`AnatomyModel`), পিক্সেলে নয় —
 *   তাই ছোট ফোন, বড় ফোন আর ওয়েবে দাগ একই জায়গায় বসে।
 *
 * ⛔ কোনো নতুন কলাম বা SQL লাগেনি — লেখাটা চেকআপের সাথেই জমা হয়।
 */
class AnatomyView(context: Context) : View(context) {

    /** কোন কাজটা এখন চলছে — উপরের বোতাম থেকে বদলায়। */
    enum class Tool { BULGE, PILE, TRACT, RING, ARROW, PEN, ERASE }

    var tool: Tool = Tool.BULGE
    var pileLabel: String = ""                 // ফোলার নাম (৩টা / ডান পাশ …)
    var onChanged: (() -> Unit)? = null        // কিছু আঁকা হলেই ডাকা হয়

    private val density = context.resources.displayMetrics.density
    private var base: Bitmap? = null           // আসল ছবি — কখনো বদলায় না
    private var shown: Bitmap? = null          // ফোলানোর পরের ছবি
    private var dirty = true

    private val marks = mutableListOf<AnatomyModel.Mark>()
    private var picKey: String = ""
    private var note: String = ""

    /** ছবির কোথায় আঁকা হচ্ছে — পর্দার ভিতরে ছবিটা যতটুকু জায়গা নেয়। */
    private val dst = RectF()

    /**
     * 🔵🔒 V569 (২২.০৮.২০২৬, TK-নির্দেশ) — *"যে ফটোটা আমি সিলেক্ট করব সেটা যেন
     * সম্পূর্ণ স্ক্রিন জুড়ে আসে ... ফটো সাইজ আরো বড় হবে ... বড় করে জুম করে"*।
     *
     * `fillScreen` — পুরো পর্দায় ছবিটা **গোটা পর্দা ভরে** বসে (দুই পাশ একটু
     *   কাটা যায়), ছোট বোর্ডে আগের মতোই পুরো ছবিটা ধরানো হয়।
     * `allowZoom` — দু'আঙুলে ছোট-বড় করা ও সরানো (শুধু পুরো পর্দায়)।
     *
     * ⛔ দাগ জমা থাকে ছবির **শতকরা** হিসেবেই, আর ছোঁয়ার হিসাব হয় `dst` ধরে —
     *    তাই জুম করলে বা সরালে দাগ ছবির ঠিক সেই জায়গাতেই বসে, আর জমা হওয়ার
     *    লেখা এক অক্ষরও বদলায় না।
     */
    var fillScreen: Boolean = false
    var allowZoom: Boolean = false

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var twoFinger = false
    private var lastMidX = 0f
    private var lastMidY = 0f

    fun resetZoom() { zoom = 1f; panX = 0f; panY = 0f; invalidate() }

    /** ➕ / ➖ বোতাম — যাঁরা দু'আঙুল ব্যবহার করতে চান না তাঁদের জন্য। */
    fun zoomBy(k: Float) {
        if (!allowZoom) return
        zoom = (zoom * k).coerceIn(1f, 6f)
        if (zoom <= 1.001f) { panX = 0f; panY = 0f }
        clampPan(); invalidate()
    }

    private val scaleDetector = android.view.ScaleGestureDetector(context,
        object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                if (!allowZoom) return false
                zoom = (zoom * d.scaleFactor).coerceIn(1f, 6f)
                if (zoom <= 1.001f) { panX = 0f; panY = 0f }
                clampPan(); invalidate(); return true
            }
        })

    /** দু'বার ছুঁলে আগের মাপে ফিরে যায় — হারিয়ে যাওয়ার ভয় থাকে না। */
    private val tapDetector = android.view.GestureDetector(context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!allowZoom) return false
                resetZoom(); return true
            }
        })

    /** ছবিটা যেন পর্দা ছেড়ে বেরিয়ে না যায়। */
    private fun clampPan() {
        val b = base ?: return
        val vw = width.toFloat(); val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val sc = baseScale(b, vw, vh) * zoom
        val w = b.width * sc; val h = b.height * sc
        val ox = Math.max(0f, (w - vw) / 2f)
        val oy = Math.max(0f, (h - vh) / 2f)
        panX = panX.coerceIn(-ox, ox)
        panY = panY.coerceIn(-oy, oy)
    }

    private fun baseScale(img: Bitmap, vw: Float, vh: Float): Float =
        if (fillScreen) Math.max(vw / img.width, vh / img.height)
        else Math.min(vw / img.width, vh / img.height)

    /** দু'আঙুল শুরু হলে চলতি আঁকাটা বাতিল — নইলে জুম করতে গিয়ে দাগ পড়ে যেত। */
    private fun cancelDraw() {
        val s = startPct
        if (s != null && marks.isNotEmpty()) {
            val last = marks[marks.size - 1]
            val sameStart = last.x == s[0].toDouble() && last.y == s[1].toDouble()
            val undoable = (tool == Tool.RING && last.kind == AnatomyModel.KIND_RING) ||
                           (tool == Tool.ARROW && last.kind == AnatomyModel.KIND_ARROW) ||
                           (tool == Tool.BULGE && last.kind == AnatomyModel.KIND_BULGE && sameStart)
            if (undoable) { marks.removeAt(marks.size - 1); dirty = true }
        }
        startPct = null
        livePts.clear()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // ───────── বাইরে থেকে বসানো ও নেওয়া ─────────

    /** নতুন ছবি বসানো। আগের দাগ মুছে যায় — এক ছবির দাগ অন্য ছবিতে বসলে ভুল হত। */
    fun setPicture(key: String, resId: Int) {
        if (key == picKey && base != null) return
        picKey = key
        marks.clear()
        base = try {
            val o = BitmapFactory.Options()
            o.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeResource(resources, resId, o)
        } catch (_: Throwable) { null } catch (_: OutOfMemoryError) { null }
        dirty = true
        invalidate()
        onChanged?.invoke()
    }

    fun load(saved: String?, resolve: (String) -> Int) {
        val b = AnatomyModel.parse(saved)
        note = b.note
        if (b.pic.isNotBlank()) {
            // ছবিটা ফোনে না থাকলেও নামটা ধরে রাখা হয় — নইলে পরের বার সেভ
            // করলে "কোন ছবির উপরে আঁকা হয়েছিল" সেই তথ্যটাই হারিয়ে যেত।
            picKey = b.pic
            val id = resolve(b.pic)
            if (id != 0) {
                base = try { BitmapFactory.decodeResource(resources, id) } catch (_: Throwable) { null }
            }
        }
        marks.clear()
        marks.addAll(b.marks)
        dirty = true
        invalidate()
    }

    fun save(): String = AnatomyModel.format(AnatomyModel.Board(picKey, marks.toList(), note))

    fun setNote(t: String) { note = t }

    fun undo() {
        if (marks.isNotEmpty()) {
            marks.removeAt(marks.size - 1)
            dirty = true; invalidate(); onChanged?.invoke()
        }
    }

    fun clearMarks() {
        if (marks.isEmpty()) return
        marks.clear(); dirty = true; invalidate(); onChanged?.invoke()
    }

    fun hasPicture(): Boolean = base != null
    fun markCount(): Int = marks.size

    // ───────── আঙুল ─────────

    private var startPct: FloatArray? = null
    private var livePts = mutableListOf<Pair<Double, Double>>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (base == null) return false

        /* 🔵 V569 — দু'আঙুল হলে ছোট-বড় ও সরানো; তখন কিছু আঁকা হয় না।
           এক আঙুল হলে হুবহু আগের নিয়মে আঁকা হয়। */
        if (allowZoom) {
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    cancelDraw(); twoFinger = true
                    lastMidX = midX(event); lastMidY = midY(event)
                    invalidate(); return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) { twoFinger = false; startPct = null }
                    return true
                }
            }
            if (twoFinger) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!scaleDetector.isInProgress || event.pointerCount >= 2) {
                            val mx = midX(event); val my = midY(event)
                            panX += mx - lastMidX; panY += my - lastMidY
                            lastMidX = mx; lastMidY = my
                            clampPan(); invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        twoFinger = false; startPct = null
                    }
                }
                return true
            }
        }

        val p = toPercent(event.x, event.y) ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startPct = p
                livePts.clear()
                livePts.add(Pair(p[0].toDouble(), p[1].toDouble()))
                if (tool == Tool.ERASE) eraseNear(p[0].toDouble(), p[1].toDouble())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = startPct ?: return true
                when (tool) {
                    Tool.BULGE -> {
                        // টান যত বড়, ফোলা তত বড় — আঙুল নড়লেই আগেরটা সরিয়ে নতুনটা
                        if (marks.isNotEmpty() && marks.last().kind == AnatomyModel.KIND_BULGE &&
                            marks.last().x == s[0].toDouble() && marks.last().y == s[1].toDouble()) {
                            marks.removeAt(marks.size - 1)
                        }
                        marks.add(AnatomyModel.bulgeFromDrag(
                            s[0].toDouble(), s[1].toDouble(), p[0].toDouble(), p[1].toDouble()))
                        dirty = true
                    }
                    Tool.TRACT, Tool.PEN -> {
                        val last = livePts.lastOrNull()
                        if (last == null || Math.abs(last.first - p[0]) + Math.abs(last.second - p[1]) > 0.6) {
                            livePts.add(Pair(p[0].toDouble(), p[1].toDouble()))
                        }
                    }
                    Tool.RING -> {
                        if (marks.isNotEmpty() && marks.last().kind == AnatomyModel.KIND_RING) marks.removeAt(marks.size - 1)
                        val dx = p[0] - s[0]; val dy = p[1] - s[1]
                        val r = Math.sqrt((dx * dx + dy * dy).toDouble()).coerceIn(2.0, 40.0)
                        marks.add(AnatomyModel.Mark(AnatomyModel.KIND_RING, x = s[0].toDouble(), y = s[1].toDouble(), r = r))
                    }
                    Tool.ARROW -> {
                        if (marks.isNotEmpty() && marks.last().kind == AnatomyModel.KIND_ARROW) marks.removeAt(marks.size - 1)
                        marks.add(AnatomyModel.Mark(AnatomyModel.KIND_ARROW,
                            x = s[0].toDouble(), y = s[1].toDouble(), x2 = p[0].toDouble(), y2 = p[1].toDouble()))
                    }
                    Tool.ERASE -> eraseNear(p[0].toDouble(), p[1].toDouble())
                    Tool.PILE -> { }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val s = startPct
                if (s != null) {
                    when (tool) {
                        Tool.PILE -> marks.add(AnatomyModel.Mark(AnatomyModel.KIND_PILE,
                            x = s[0].toDouble(), y = s[1].toDouble(), label = pileLabel))
                        Tool.TRACT, Tool.PEN -> {
                            if (livePts.size > 1) {
                                marks.add(AnatomyModel.Mark(
                                    if (tool == Tool.TRACT) AnatomyModel.KIND_TRACT else AnatomyModel.KIND_PEN,
                                    pts = livePts.toList()))
                            }
                        }
                        Tool.BULGE -> {
                            // শুধু ছুঁয়ে ছেড়ে দিলে ছোট একটা ফোলা — টানার দরকার নেই
                            if (marks.isEmpty() || marks.last().kind != AnatomyModel.KIND_BULGE) {
                                marks.add(AnatomyModel.bulgeFromDrag(
                                    s[0].toDouble(), s[1].toDouble(), s[0].toDouble(), s[1].toDouble()))
                                dirty = true
                            }
                        }
                        else -> { }
                    }
                }
                startPct = null
                livePts.clear()
                invalidate()
                onChanged?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun midX(e: MotionEvent): Float =
        if (e.pointerCount >= 2) (e.getX(0) + e.getX(1)) / 2f else e.x
    private fun midY(e: MotionEvent): Float =
        if (e.pointerCount >= 2) (e.getY(0) + e.getY(1)) / 2f else e.y

    /** ছোঁয়ার জায়গার সবচেয়ে কাছের দাগটা মোছে। */
    private fun eraseNear(x: Double, y: Double) {
        var best = -1; var bestD = 6.0
        for (i in marks.indices) {
            val m = marks[i]
            val d = when (m.kind) {
                AnatomyModel.KIND_TRACT, AnatomyModel.KIND_PEN ->
                    m.pts.minOfOrNull { Math.hypot(it.first - x, it.second - y) } ?: 999.0
                else -> Math.hypot(m.x - x, m.y - y)
            }
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) {
            val gone = marks.removeAt(best)
            if (gone.kind == AnatomyModel.KIND_BULGE) dirty = true
            invalidate(); onChanged?.invoke()
        }
    }

    /** পর্দার ছোঁয়া → ছবির শতকরা জায়গা। ছবির বাইরে ছুঁলে null। */
    private fun toPercent(px: Float, py: Float): FloatArray? {
        if (dst.width() <= 0f || dst.height() <= 0f) return null
        if (px < dst.left || px > dst.right || py < dst.top || py > dst.bottom) return null
        return floatArrayOf(
            (px - dst.left) / dst.width() * 100f,
            (py - dst.top) / dst.height() * 100f
        )
    }

    // ───────── আঁকা ─────────

    override fun onDraw(canvas: Canvas) {
        val b = base
        if (b == null) {
            paint.color = Color.parseColor("#8A93A0")
            paint.textSize = 14f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("উপর থেকে একটা ছবি বাছুন", width / 2f, height / 2f, paint)
            paint.textAlign = Paint.Align.LEFT
            return
        }
        if (dirty || shown == null) { rebuild(); dirty = false }
        val img = shown ?: b

        /* ছোট বোর্ডে ছবিটা পুরোটা ভিতরে ধরানো হয়। পুরো পর্দায় (V569) ছবিটা
           গোটা পর্দা **ভরে** বসে, তার উপরে ডাক্তারের জুম ও সরানো। */
        val vw = width.toFloat(); val vh = height.toFloat()
        val sc = baseScale(img, vw, vh) * (if (allowZoom) zoom else 1f)
        val w = img.width * sc; val h = img.height * sc
        val left = (vw - w) / 2f + (if (allowZoom) panX else 0f)
        val top = (vh - h) / 2f + (if (allowZoom) panY else 0f)
        dst.set(left, top, left + w, top + h)
        canvas.drawBitmap(img, null, dst, null)

        drawMarks(canvas)
    }

    /** ফোলানোর হিসাব — আসল ছবি থেকে নতুন একটা ছবি বানানো হয়। */
    private fun rebuild() {
        val b = base ?: return
        val bulges = marks.filter { it.kind == AnatomyModel.KIND_BULGE }
        if (bulges.isEmpty()) { shown = b; return }
        // আঙুল টানার সময় প্রতি ফ্রেমে নতুন ছবি বানালে ফোন আটকে যেত — তাই
        // একবার বানিয়ে সেটাই বারবার ব্যবহার করা হয়, শুধু আসল ছবিটা আবার
        // উপরে বসিয়ে নেওয়া হয়।
        var work = shown
        if (work == null || work === b || work.width != b.width || work.height != b.height || !work.isMutable) {
            work = try { b.copy(Bitmap.Config.ARGB_8888, true) }
                   catch (_: Throwable) { null } catch (_: OutOfMemoryError) { null }
            if (work == null) { shown = b; return }
        } else {
            try { Canvas(work).drawBitmap(b, 0f, 0f, null) } catch (_: Throwable) { shown = b; return }
        }
        for (g in bulges) applyBulge(work, g)
        shown = work
    }

    /**
     * একটা ফোলা। ছবির ওই গোল অংশের ভিতরের পিক্সেলগুলো কেন্দ্র থেকে বাইরের
     * দিকে ঠেলে দেওয়া হয় — মাঝখানে সবচেয়ে বেশি, কিনারায় শূন্য। তাই ফোলাটা
     * চারপাশের চামড়ার সাথে মিশে থাকে, কাটা-কাটা লাগে না।
     */
    private fun applyBulge(bmp: Bitmap, g: AnatomyModel.Mark) {
        val W = bmp.width; val H = bmp.height
        val cx = (g.x / 100.0 * W); val cy = (g.y / 100.0 * H)
        val R = (g.r / 100.0 * W)
        val st = g.s.coerceIn(-AnatomyModel.BULGE_MAX, AnatomyModel.BULGE_MAX)
        if (R < 2 || st == 0.0) return
        val x0 = Math.max(0, Math.floor(cx - R).toInt())
        val y0 = Math.max(0, Math.floor(cy - R).toInt())
        val x1 = Math.min(W, Math.ceil(cx + R).toInt())
        val y1 = Math.min(H, Math.ceil(cy + R).toInt())
        val w = x1 - x0; val h = y1 - y0
        if (w < 2 || h < 2) return
        val src = IntArray(w * h)
        try { bmp.getPixels(src, 0, w, x0, y0, w, h) } catch (_: Throwable) { return }
        val out = IntArray(w * h)
        for (iy in 0 until h) {
            for (ix in 0 until w) {
                val dx = (x0 + ix) - cx
                val dy = (y0 + iy) - cy
                val d = Math.sqrt(dx * dx + dy * dy)
                val o = iy * w + ix
                if (d >= R) { out[o] = src[o]; continue }
                // V564: ঠেলার অঙ্কটা এখন `AnatomyModel.pushFactor()`-এ — ফোন ও
                // ওয়েব দুই জায়গায় হুবহু একই, আর পরীক্ষাও ওখানেই হয়।
                val f = AnatomyModel.pushFactor(d / R, st)
                out[o] = sample(src, w, h, (cx - x0 + dx * f), (cy - y0 + dy * f))
            }
        }
        try { bmp.setPixels(out, 0, w, x0, y0, w, h) } catch (_: Throwable) { return }

        // রক্ত জমে গাঢ় ভাব ও উপরে ভেজা চকচকে — নইলে ফোলাটা প্লাস্টিকের মত লাগে
        val c = Canvas(bmp)
        paint.reset(); paint.isAntiAlias = true
        paint.shader = android.graphics.RadialGradient(
            cx.toFloat(), cy.toFloat(), R.toFloat(),
            intArrayOf(Color.argb(70, 158, 18, 44), Color.argb(34, 124, 12, 38), Color.argb(0, 124, 12, 38)),
            floatArrayOf(0f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(cx.toFloat(), cy.toFloat(), R.toFloat(), paint)
        val hx = (cx - R * 0.28).toFloat(); val hy = (cy - R * 0.30).toFloat()
        val hr = (R * 0.55).toFloat()
        paint.shader = android.graphics.RadialGradient(
            hx, hy, hr,
            intArrayOf(Color.argb(86, 255, 235, 235), Color.argb(0, 255, 235, 235)),
            floatArrayOf(0f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(hx, hy, hr, paint)
        paint.shader = null
    }

    private fun sample(src: IntArray, w: Int, h: Int, fx: Double, fy: Double): Int {
        val x = fx.coerceIn(0.0, w - 1.001)
        val y = fy.coerceIn(0.0, h - 1.001)
        val xi = x.toInt(); val yi = y.toInt()
        val ax = x - xi; val ay = y - yi
        val i00 = yi * w + xi; val i10 = i00 + 1
        val i01 = i00 + w; val i11 = i01 + 1
        var outCol = 0
        for (sh in intArrayOf(16, 8, 0)) {                    // লাল · সবুজ · নীল
            val top = ((src[i00] shr sh and 255) * (1 - ax) + (src[i10] shr sh and 255) * ax)
            val bot = ((src[i01] shr sh and 255) * (1 - ax) + (src[i11] shr sh and 255) * ax)
            val v = (top * (1 - ay) + bot * ay).toInt().coerceIn(0, 255)
            outCol = outCol or (v shl sh)
        }
        return outCol or (255 shl 24)
    }

    private fun drawMarks(canvas: Canvas) {
        val s = Math.min(dst.width(), dst.height()) / 100f
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND

        // এখন আঙুল যেটা টানছে সেটাও দেখা যাবে, ছাড়ার অপেক্ষা করতে হবে না
        if (livePts.size > 1 && (tool == Tool.TRACT || tool == Tool.PEN)) {
            strokePts(canvas, livePts, s, if (tool == Tool.TRACT) "#F0A400" else "#111111",
                      if (tool == Tool.TRACT) 2.2f else 1.4f, tool == Tool.TRACT)
            // আঙুল টানার সময়েই মাপটা দেখা যায় — ছাড়ার অপেক্ষা করতে হয় না
            if (tool == Tool.TRACT) drawTractCm(canvas, livePts, s)
        }
        for (m in marks) {
            when (m.kind) {
                AnatomyModel.KIND_TRACT -> {
                    strokePts(canvas, m.pts, s, "#F0A400", 2.2f, true)
                    // 🔴 V564 (TK): নালী কত সেন্টিমিটার — শেষ মাথার পাশে
                    drawTractCm(canvas, m.pts, s)
                }
                AnatomyModel.KIND_PEN   -> strokePts(canvas, m.pts, s, "#111111", 1.4f, false)
                AnatomyModel.KIND_RING -> {
                    val cx = px(m.x); val cy = py(m.y); val r = (m.r / 100.0 * dst.width()).toFloat()
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.argb(102, 0, 0, 0); paint.strokeWidth = 2.8f * s
                    canvas.drawOval(RectF(cx - r, cy - r * 0.82f, cx + r, cy + r * 0.82f), paint)
                    paint.color = Color.parseColor("#12A05A"); paint.strokeWidth = 1.6f * s
                    canvas.drawOval(RectF(cx - r, cy - r * 0.82f, cx + r, cy + r * 0.82f), paint)
                }
                AnatomyModel.KIND_ARROW -> drawArrow(canvas, px(m.x), py(m.y), px(m.x2), py(m.y2), s)
                AnatomyModel.KIND_PILE -> {
                    val cx = px(m.x); val cy = py(m.y)
                    paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#D81E3F")
                    canvas.drawCircle(cx, cy, 2.4f * s, paint)
                    paint.style = Paint.Style.STROKE; paint.color = Color.WHITE; paint.strokeWidth = 1.1f * s
                    canvas.drawCircle(cx, cy, 2.4f * s, paint)
                    if (m.label.isNotBlank()) chip(canvas, cx + 3.4f * s, cy, m.label, s)
                }
            }
        }
    }

    /**
     * 🔴 V564 (TK, লাইভ টেস্ট): *"ফিস্টুলার দাগ টানলে যেন কত সেন্টিমিটার সেটা
     * বোঝা যায় না"*। মাপটা ছবির চওড়া কত সেমি ধরে বার করা হয় — আমাদের আঁকা
     * ছবিতে সেটা সঠিক জানা, আসল ফটোয় আন্দাজি, তাই সেখানে "≈" বসে।
     */
    private fun drawTractCm(canvas: Canvas, pts: List<Pair<Double, Double>>, s: Float) {
        if (pts.size < 2) return
        val b = base ?: return
        val pic = AnatomyModel.pictureOf(picKey)
        val cmWide = pic?.cmWide ?: 10.0
        val exact = pic?.exactScale ?: false
        val cm = AnatomyModel.tractCm(pts, cmWide, b.height.toDouble() / b.width.toDouble())
        if (cm <= 0.0) return
        val last = pts[pts.size - 1]
        chip(canvas, px(last.first) + 3.4f * s, py(last.second), AnatomyModel.tractLabel(cm, exact), s)
    }

    private fun strokePts(canvas: Canvas, pts: List<Pair<Double, Double>>, s: Float,
                          color: String, w: Float, dashed: Boolean) {
        if (pts.size < 2) return
        path.reset()
        for (i in pts.indices) {
            val x = px(pts[i].first); val y = py(pts[i].second)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE; paint.pathEffect = null
        paint.color = Color.argb(115, 0, 0, 0); paint.strokeWidth = (w + 1.6f) * s
        canvas.drawPath(path, paint)
        paint.color = Color.parseColor(color); paint.strokeWidth = w * s
        if (dashed) paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3.4f * s, 2.4f * s), 0f)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, s: Float) {
        val a = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val hl = 4.2f * s
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(102, 0, 0, 0); paint.strokeWidth = 3.2f * s
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.color = Color.parseColor("#1B6FD8"); paint.strokeWidth = 1.8f * s
        canvas.drawLine(x1, y1, x2, y2, paint)
        path.reset()
        path.moveTo(x2, y2)
        path.lineTo(x2 - hl * Math.cos(a - 0.42).toFloat(), y2 - hl * Math.sin(a - 0.42).toFloat())
        path.lineTo(x2 - hl * Math.cos(a + 0.42).toFloat(), y2 - hl * Math.sin(a + 0.42).toFloat())
        path.close()
        paint.style = Paint.Style.FILL; paint.color = Color.parseColor("#1B6FD8")
        canvas.drawPath(path, paint)
    }

    private fun chip(canvas: Canvas, x: Float, y: Float, txt: String, s: Float) {
        paint.style = Paint.Style.FILL
        paint.textSize = 3.1f * s
        val w = paint.measureText(txt) + 2.6f * s
        val h = 5.2f * s; val r = 2.6f * s
        val box = RectF(x, y - h / 2, x + w, y + h / 2)
        paint.color = Color.argb(240, 255, 255, 255)
        canvas.drawRoundRect(box, r, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
        paint.color = Color.parseColor("#D81E3F")
        canvas.drawRoundRect(box, r, r, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText(txt, x + 1.3f * s, y + 1.1f * s, paint)
    }

    private fun px(v: Double) = dst.left + (v / 100.0 * dst.width()).toFloat()
    private fun py(v: Double) = dst.top + (v / 100.0 * dst.height()).toFloat()
}
