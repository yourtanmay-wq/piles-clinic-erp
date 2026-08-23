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
 *   ২. *"মাংসের উপরে আঙুল দিয়ে টান দিলে যেন মাংস বেড়ে যায়"* → V571 থেকে
 *      ছবির পিক্সেল ঠেলা হয় না; TK-এর পাঠানো ছবির মতো একটা **ফোঁটা-আকৃতির
 *      বেগুনি মাংসপিণ্ড আঁকা হয়** (`drawLump`) — গোড়া সরু, মাথা গোল, উপরে
 *      আলো, গায়ে দানা-দানা। টান যত বড়, মাংস তত বড়, আর যেদিকে টেনেছেন
 *      সেদিকেই বেরোয়।
 *
 * 📐 সব দাগ ছবির **শতকরা** মাপে জমা থাকে (`AnatomyModel`), পিক্সেলে নয় —
 *   তাই ছোট ফোন, বড় ফোন আর ওয়েবে দাগ একই জায়গায় বসে।
 *
 * ⛔ কোনো নতুন কলাম বা SQL লাগেনি — লেখাটা চেকআপের সাথেই জমা হয়।
 */
class AnatomyView(context: Context) : View(context) {

    /** কোন কাজটা এখন চলছে — উপরের বোতাম থেকে বদলায়। */
    /* 🔵 V585 (২৩.০৮.২০২৬, TK-নির্দেশ *"এটাতো অটোমেটিক্যালি হওয়ার কথা"*) —
       নতুন হাতিয়ার **CENTRE**: ছবির পায়ুপথের মাঝখানে একবার ছুঁয়ে দিলে ওই
       ছবির ঘড়ির কেন্দ্র জমা হয়। তারপর প্রতিটা চিহ্নের o'clock নিজে হিসাব হয়।
       ⛔ আগের সাতটা হাতিয়ারের একটাও বদলায়নি, শুধু একটা যোগ হলো। */
    enum class Tool { BULGE, PILE, TRACT, RING, ARROW, PEN, ERASE, CENTRE }

    var tool: Tool = Tool.BULGE
    /* 🔵 V585 — আগে এখানে পপ-আপে বাছা লেখাটা জমা থাকত আর **প্রতিটা** চিহ্নে
       সেটাই বসত (TK: *"যেখানেই থাকবে চারটা কেন বাঁচবে"*)। ঘরটা **মোছা হয়নি** —
       পুরোনো কোনো ডাক থাকলে ভাঙবে না — কিন্তু এখন আর ব্যবহার হয় না। */
    var pileLabel: String = ""                 // ⛔ V585-এর পর ব্যবহার হয় না
    /** এই ছবির ঘড়ির কেন্দ্র (শতাংশে), জানা না থাকলে null — তখন o'clock লেখা পড়ে না। */
    var clockCentre: Pair<Double, Double>? = null
    /** ডাক্তার কেন্দ্র ছুঁয়ে দিলে ডাকা হয় (Activity সেটা জমা করে)। */
    var onCentreSet: ((Double, Double) -> Unit)? = null
    var onChanged: (() -> Unit)? = null        // কিছু আঁকা হলেই ডাকা হয়

    private val density = context.resources.displayMetrics.density
    private var base: Bitmap? = null           // আসল ছবি — কখনো বদলায় না

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
            if (undoable) { marks.removeAt(marks.size - 1) }
        }
        startPct = null
        livePts.clear()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        /* 🔵 V571 — মাংসের নিচের নরম ছায়াটা (`setShadowLayer`) হার্ডওয়্যার
           আঁকায় পথের উপরে কাজ করে না, তাই এই পর্দাটা সফটওয়্যারে আঁকা হয়।
           ⚡ ভারী কিছু নয় — আগের পিক্সেল-ঠেলার হিসাবটাই বাদ গেছে, তাই
              সব মিলিয়ে এখন আগের চেয়ে হালকা। */
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // ───────── বাইরে থেকে বসানো ও নেওয়া ─────────

    /** নতুন ছবি বসানো। আগের দাগ মুছে যায় — এক ছবির দাগ অন্য ছবিতে বসলে ভুল হত। */
    fun setPicture(key: String, resId: Int) {
        if (key == picKey && base != null) return
        picKey = key
        clockCentre = AnatomyClock.centreOf(context, key)   // 🔵 V585
        marks.clear()
        resetZoom()
        base = try {
            val o = BitmapFactory.Options()
            o.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeResource(resources, resId, o)
        } catch (_: Throwable) { null } catch (_: OutOfMemoryError) { null }
        invalidate()
        onChanged?.invoke()
    }

    /**
     * 🔵 V573 — ডাক্তারের নিজের যোগ করা ছবি (ক্যামেরা/গ্যালারি) বসানো।
     * অ্যাপের ছবির মতোই কাজ করে; শুধু ছবিটা resource নয়, তৈরি করা bitmap।
     */
    fun setPictureBitmap(key: String, bmp: Bitmap?) {
        if (key == picKey && base != null) return
        picKey = key
        clockCentre = AnatomyClock.centreOf(context, key)   // 🔵 V585
        marks.clear()
        resetZoom()
        base = bmp
        invalidate()
        onChanged?.invoke()
    }

    /** ছবি বদলানো ছাড়াই শুধু ছবিটা বসানো — দাগ অক্ষত থাকে (রেকর্ড ফেরানোর সময়)। */
    fun setBaseBitmap(bmp: Bitmap?) {
        base = bmp
        invalidate()
    }

    fun load(saved: String?, resolve: (String) -> Int) {
        val b = AnatomyModel.parse(saved)
        note = b.note
        if (b.pic.isNotBlank()) {
            // ছবিটা ফোনে না থাকলেও নামটা ধরে রাখা হয় — নইলে পরের বার সেভ
            // করলে "কোন ছবির উপরে আঁকা হয়েছিল" সেই তথ্যটাই হারিয়ে যেত।
            picKey = b.pic
            clockCentre = AnatomyClock.centreOf(context, b.pic)   // 🔵 V585
            val id = resolve(b.pic)
            if (id != 0) {
                base = try { BitmapFactory.decodeResource(resources, id) } catch (_: Throwable) { null }
            }
        }
        marks.clear()
        marks.addAll(b.marks)
        invalidate()
    }

    /** 🔵 V585 — এখন কোন ছবির উপরে আঁকা হচ্ছে (কেন্দ্র জমা/পড়ার চাবি)। */
    fun picKeyNow(): String = picKey

    fun save(): String = AnatomyModel.format(AnatomyModel.Board(picKey, marks.toList(), note))

    fun setNote(t: String) { note = t }

    fun undo() {
        if (marks.isNotEmpty()) {
            marks.removeAt(marks.size - 1)
            invalidate(); onChanged?.invoke()
        }
    }

    fun clearMarks() {
        if (marks.isEmpty()) return
        marks.clear(); invalidate(); onChanged?.invoke()
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
                    Tool.PILE, Tool.CENTRE -> { }   // 🔵 V585 — দুটোই শুধু ছোঁয়ায় কাজ করে
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val s = startPct
                if (s != null) {
                    when (tool) {
                        /* 🔵 V585 — লেখাটা এখানেই হিসাব হয়ে `label`-এ বসে, অর্থাৎ
                           ঠিক সেই ঘরেই যেখানে আগে পপ-আপের বাছাই বসত। তাই A4
                           রিপোর্ট · 📜 History · প্রিন্ট · সেভ — নিচের কিছুই
                           বদলাতে হয়নি। */
                        Tool.PILE -> {
                            val c = clockCentre
                            val lab = if (c == null) "" else {
                                val h = AnatomyClock.hourAt(s[0].toDouble(), s[1].toDouble(), c.first, c.second)
                                if (h == 0) "" else "${h}টা"
                            }
                            marks.add(AnatomyModel.Mark(AnatomyModel.KIND_PILE,
                                x = s[0].toDouble(), y = s[1].toDouble(), label = lab))
                        }
                        /* কেন্দ্র বসানো — কোনো দাগ যোগ হয় না, শুধু মনে রাখা হয়। */
                        Tool.CENTRE -> {
                            clockCentre = Pair(s[0].toDouble(), s[1].toDouble())
                            onCentreSet?.invoke(s[0].toDouble(), s[1].toDouble())
                        }
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
        val img = b

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

    /* 🔵🔒 V571 — আগে এখানে `rebuild()` ও `applyBulge()` ছিল: আসল ছবির পিক্সেল
       বাইরের দিকে ঠেলে "ফোলা" বানানো হত। TK ছবি পাঠিয়ে বললেন সেটা *"যথাযথ
       মিল খাচ্ছে না"* — এখন মাংসপিণ্ডটা `drawLump()` দিয়ে **আঁকা** হয়।
       ⚡ পাশাপাশি ফোনও হালকা হলো — আর কোনো বড় bitmap কপি বা পিক্সেল-লুপ নেই,
          তাই টানার সময় আটকায় না আর মেমরি-শেষ হওয়ার ভয়ও থাকে না। */

    /**
     * 🔵🔒 V571 — **পাইলসের মাংসপিণ্ড আঁকা** (TK-এর পাঠানো ছবির মতো)।
     * ⚠️ ওয়েবের `bulge()`/`lumpPath()`-এর হুবহু যমজ — একই আকার, একই রং, একই
     *    দানার হিসাব। তাই ফোন আর কম্পিউটারে মাংসটা এক দেখায়।
     */
    private val lumpPathObj = Path()

    /** ফোঁটার আকার — গোড়ায় (০,০) সরু ডগা, মাথায় গোল বল। */
    private fun buildLumpPath(len: Float, wide: Float) {
        val hw = wide / 2f
        val cx = len - hw
        lumpPathObj.reset()
        if (cx <= hw * 0.30f) {
            lumpPathObj.addCircle(len - hw, 0f, hw, Path.Direction.CW)
            return
        }
        val beta = Math.acos(Math.max(-1.0, Math.min(1.0, (hw / cx).toDouble())))
        val a1 = -(Math.PI - beta)
        val a2 = (Math.PI - beta)
        val p1x = (cx + hw * Math.cos(a1)).toFloat(); val p1y = (hw * Math.sin(a1)).toFloat()
        val p2x = (cx + hw * Math.cos(a2)).toFloat(); val p2y = (hw * Math.sin(a2)).toFloat()
        val tip = Math.min(hw * 0.22f, cx * 0.10f)
        lumpPathObj.moveTo(tip * 0.25f, -tip)
        lumpPathObj.quadTo(cx * 0.40f, p1y * 0.70f, p1x, p1y)
        // গোল মাথাটা — a1 থেকে a2 পর্যন্ত, দূরের দিক দিয়ে
        val oval = RectF(cx - hw, -hw, cx + hw, hw)
        val start = Math.toDegrees(a1).toFloat()
        val sweep = Math.toDegrees(a2 - a1).toFloat()
        lumpPathObj.arcTo(oval, start, sweep, false)
        lumpPathObj.quadTo(cx * 0.40f, p2y * 0.70f, tip * 0.25f, tip)
        lumpPathObj.quadTo(-tip * 0.45f, 0f, tip * 0.25f, -tip)
        lumpPathObj.close()
    }

    private fun drawLump(canvas: Canvas, m: AnatomyModel.Mark) {
        val g = AnatomyModel.lumpGeom(m)
        val sc = Math.min(dst.width(), dst.height()) / 100f
        val len = (g.len * sc).toFloat()
        val wide = (g.wide * sc).toFloat()
        if (len < 3f) return

        val save = canvas.save()
        canvas.translate(px(m.x), py(m.y))
        canvas.rotate(Math.toDegrees(g.ang).toFloat())

        buildLumpPath(len, wide)

        // মাটিতে পড়া ছায়া — মাংসটা ছবির উপরে বসে আছে মনে হয়
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#B3766C")
        paint.setShadowLayer(wide * 0.42f, 0f, wide * 0.10f, Color.parseColor("#66461E1A"))
        canvas.drawPath(lumpPathObj, paint)
        paint.clearShadowLayer()

        val save2 = canvas.save()
        canvas.clipPath(lumpPathObj)

        // গায়ের রং — মাথার উপর-বাঁয়ে আলো, কিনারায় গাঢ়
        val hx = len - wide * 0.5f; val hy = -wide * 0.26f
        paint.shader = android.graphics.RadialGradient(
            hx - wide * 0.22f, hy, Math.max(1f, wide * 1.25f),
            intArrayOf(Color.parseColor("#F7DCD4"), Color.parseColor("#EEC1B6"),
                       Color.parseColor("#DFA294"), Color.parseColor("#C8837A"),
                       Color.parseColor("#A75F58")),
            floatArrayOf(0f, 0.30f, 0.62f, 0.86f, 1f),
            android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(-len * 0.3f, -wide, len * 1.5f, wide, paint)
        paint.shader = null

        /* 🔵🔒 V582 (TK-নির্দেশ ২৩.০৮.২০২৬, আসল অপারেশনের ছবি দেখিয়ে):
           *"real লাগতে হবে অথবা AI animation-এর মতো হতে হবে"*।
           আগে সমান মাপের শক্ত-কিনারা গোল দাগ ছিল — কার্টুনের মতো লাগত।
           এখন দুই স্তর: বড় নরম **ছোপ**, তার উপরে ছোট **রক্তের ছিটে** —
           দুটোরই কিনারা মিলিয়ে যায়, তাই মাংসের মতো দেখায়।
           ⛔ এলোমেলো সংখ্যাগুলোর ক্রম **কম্পিউটারের অ্যাপের হুবহু এক**
              (`wlv1` bulge()-এ একই ক্রমে), তাই একই ফোলা দুই যন্ত্রে একই। */
        val state = longArrayOf(AnatomyModel.lumpSeed(m.x, m.y, g.len))
        paint.style = Paint.Style.FILL
        // ১. গায়ের ছোপ
        for (k in 0 until 4) {
            val mu = 0.18 + AnatomyModel.lumpNext(state) * 0.70
            val mv = (AnatomyModel.lumpNext(state) * 2 - 1) * 0.62
            val mr = (wide * (0.26 + AnatomyModel.lumpNext(state) * 0.24)).toFloat()
            val mx = (len * mu).toFloat()
            val my = ((wide / 2f) * mv).toFloat()
            if (mr <= 0f) continue
            paint.shader = android.graphics.RadialGradient(
                mx, my, mr,
                intArrayOf(Color.parseColor("#61CE7C74"), Color.parseColor("#33D6928A"),
                           Color.parseColor("#00DCA098")),
                floatArrayOf(0f, 0.60f, 1f), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(mx, my, mr, paint)
            paint.shader = null
        }
        /* ২. সরু শিরা — TK: *"আরো রিয়েল মনে হয়"*। খুব হালকা তিনটে বাঁকা দাগ,
           গায়ের সমান-সমান ভাবটা ভেঙে দেয়। ⛔ এলোমেলো সংখ্যার ক্রম
           কম্পিউটারের অ্যাপের হুবহু এক (৪টে করে, তিনবার)। */
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (q in 0 until 3) {
            val au = 0.24 + AnatomyModel.lumpNext(state) * 0.48
            val av = (AnatomyModel.lumpNext(state) * 2 - 1) * 0.48
            val bu = Math.min(0.95, au + 0.10 + AnatomyModel.lumpNext(state) * 0.22)
            val bv = Math.max(-0.9, Math.min(0.9, av + (AnatomyModel.lumpNext(state) * 2 - 1) * 0.45))
            path.reset()
            path.moveTo((len * au).toFloat(), ((wide / 2f) * av).toFloat())
            path.quadTo((len * (au + bu) / 2).toFloat(),
                        ((wide / 2f) * (av + bv) / 2 - wide * 0.10f).toFloat(),
                        (len * bu).toFloat(), ((wide / 2f) * bv).toFloat())
            paint.color = Color.parseColor("#579E2226")
            paint.strokeWidth = Math.max(0.5f, wide * 0.032f)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        // ৩. রক্তের ছিটে
        val n = Math.max(10, Math.min(46, Math.round(g.len * 1.9).toInt()))
        for (i in 0 until n) {
            val u = 0.10 + AnatomyModel.lumpNext(state) * 0.88
            val spread = Math.sin(Math.PI * Math.min(1.0, u * 1.02)) * 0.92
            val v = (AnatomyModel.lumpNext(state) * 2 - 1) * spread
            val ccx = (len * u).toFloat()
            val ccy = ((wide / 2f) * v).toFloat()
            val rr = (wide * (0.014 + AnatomyModel.lumpNext(state) * 0.050)).toFloat()
            if (rr <= 0f) continue
            paint.shader = android.graphics.RadialGradient(
                ccx, ccy, rr,
                intArrayOf(Color.parseColor("#EBA80C16"), Color.parseColor("#A8C41E26"),
                           Color.parseColor("#00D64044")),
                floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(ccx, ccy, rr, paint)
            paint.shader = null
        }

        // কিনারার দিকে ভিতরে ছায়া — গোল ভাবটা বাড়ে
        paint.style = Paint.Style.FILL
        paint.shader = android.graphics.RadialGradient(
            len - wide * 0.5f, 0f, Math.max(1f, wide * 1.05f),
            intArrayOf(Color.parseColor("#0078342E"), Color.parseColor("#8078342E")),
            floatArrayOf(0.286f, 1f), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(-len * 0.3f, -wide, len * 1.5f, wide, paint)
        paint.shader = null

        // ভেজা-চকচকে আলো
        paint.shader = android.graphics.RadialGradient(
            hx - wide * 0.26f, hy, Math.max(1f, wide * 0.52f),
            Color.parseColor("#6BFFF4F0"), Color.parseColor("#00FFF4F0"),
            android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(-len * 0.3f, -wide, len * 1.5f, wide, paint)
        paint.shader = null
        canvas.restoreToCount(save2)

        // চারদিকের গাঢ় কিনারা
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#C78E4A44")
        paint.strokeWidth = Math.max(0.8f, len * 0.030f)
        canvas.drawPath(lumpPathObj, paint)
        canvas.restoreToCount(save)
    }

    private fun drawMarks(canvas: Canvas) {
        val s = Math.min(dst.width(), dst.height()) / 100f
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND

        /* 🔵 V585 — কেন্দ্র জানা থাকলে হালকা সবুজ ঘড়িটা দেখানো হয়, যাতে ডাক্তার
           চোখেই মিলিয়ে নিতে পারেন কোন দিক কত o'clock। ⛔ এটা শুধু দেখার জিনিস —
           কোনো দাগ হিসেবে সেভ হয় না, প্রিন্টেও যায় না। */
        clockCentre?.let { c ->
            val cx = px(c.first); val cy = py(c.second)
            val rr = Math.min(dst.width(), dst.height()) * 0.34f
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#660F5132")
            paint.strokeWidth = Math.max(1f, s * 0.5f)
            canvas.drawCircle(cx, cy, rr, paint)
            for (h in 1..12) {
                val a = Math.toRadians(h * 30.0)
                val sn = Math.sin(a).toFloat(); val cs = Math.cos(a).toFloat()
                canvas.drawLine(cx + sn * rr * 0.90f, cy - cs * rr * 0.90f,
                                cx + sn * rr, cy - cs * rr, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F5132")
            canvas.drawCircle(cx, cy, Math.max(2f, s * 1.4f), paint)
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, Math.max(4f, s * 3.0f), paint)
        }
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND

        // এখন আঙুল যেটা টানছে সেটাও দেখা যাবে, ছাড়ার অপেক্ষা করতে হবে না
        if (livePts.size > 1 && (tool == Tool.TRACT || tool == Tool.PEN)) {
            strokePts(canvas, livePts, s, if (tool == Tool.TRACT) "#F0A400" else "#111111",
                      if (tool == Tool.TRACT) 1.35f else 1.4f, tool == Tool.TRACT)
            // আঙুল টানার সময়েই মাপটা দেখা যায় — ছাড়ার অপেক্ষা করতে হয় না
            if (tool == Tool.TRACT) drawTractCm(canvas, livePts, s)
        }
        // মাংস আগে, দাগ পরে — ওয়েবের `draw()`-এর মতোই ক্রম
        for (m in marks) if (m.kind == AnatomyModel.KIND_BULGE) drawLump(canvas, m)
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for (m in marks) {
            when (m.kind) {
                AnatomyModel.KIND_TRACT -> {
                    strokePts(canvas, m.pts, s, "#F0A400", 1.35f, true)
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
        /* 🔵 V583 (TK-নির্দেশ): ফিস্টুলার দাগ সামান্য পাতলা — কালো ছায়াটাও
           সেই অনুপাতে, নইলে সরু দাগের চারপাশে মোটা ছায়া বেমানান লাগত।
           ⛔ রং · কাটা-কাটা ধরন কিছুই বদলায়নি। */
        paint.color = Color.argb(if (dashed) 87 else 115, 0, 0, 0)
        paint.strokeWidth = (w + (if (dashed) 0.7f else 1.6f)) * s
        canvas.drawPath(path, paint)
        paint.color = Color.parseColor(color); paint.strokeWidth = w * s
        if (dashed) paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.8f * s, 2.0f * s), 0f)
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
