package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 🪟🔒🔒 V845 (৩০.০৮.২০২৬, TK-নির্দেশ, ফটো-প্রুফ পাশ) —
 * **কল বাজার সময়** কল-স্ক্রিনের উপরে ভাসমান কার্ড।
 *
 * TK-রিপোর্ট: *"ফোন কল কাটার পরে কেন এটা আসবে… আমি চাইছি ডিউরিং ফোন কলে,
 * অর্থাৎ ফোন কল যখন আসবে তখনই যেন স্ক্রিনের ডিসপ্লেতে দেখা যায়।"*
 *
 * 🔬 আসল কারণ (কোড ধরে যাচাই, আন্দাজ নয়): `CallNotifyManager.onRinging()`
 * নোটিফিকেশনটা **কল বাজার সাথে সাথেই** পাঠায় — ওখানে কোনো ভুল ছিল না।
 * কিন্তু ওই মুহূর্তে ফোনের নিজের কল-স্ক্রিন পুরো পর্দা দখল করে রাখে, তাই
 * সাধারণ নোটিফিকেশন পিছনে চলে যায়। কল কাটলে পর্দা সরে ⇒ তখন চোখে পড়ে।
 * ⇒ উপরে দেখাতে হলে **overlay উইন্ডো** ছাড়া উপায় নেই।
 *
 * ⛔ যা কখনো হবে না:
 * · অনুমতি না থাকলে **চুপচাপ কিছুই করে না** — নোটিফিকেশন আগের মতোই চলে।
 * · কোনো ব্যতিক্রম হলেও **কল বা অ্যাপ কখনো আটকাবে না** (সব try/catch)।
 * · কোনো ক্লাউড-পড়া নেই — যা দেখানো হয় সব `CallNotifyManager`-এর
 *   ইতিমধ্যে আনা তথ্য থেকে। **Egress শূন্য।**
 * · কল কেটে গেলে বা স্টাফ ✕ চাপলে কার্ড সরে যায়; কখনো আটকে থাকে না।
 */
object CallOverlay {

    private var view: View? = null
    private var wm: WindowManager? = null

    /** অনুমতি আছে কি না — না থাকলে কোথাও কিছু করা হয় না। */
    fun allowed(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            android.provider.Settings.canDrawOverlays(ctx) else true
    } catch (_: Throwable) { false }

    /** ফোনের Settings-এ "Display over other apps"-এর পাতা খোলে। */
    fun openPermissionScreen(ctx: Context) {
        try {
            val i = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + ctx.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) { }
    }

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()

    /**
     * কার্ডটা দেখানো (বা আগেরটা থাকলে নতুন তথ্যে বদলে দেওয়া)।
     * ⛔ সবসময় মূল থ্রেড থেকে ডাকতে হবে — WindowManager-এর নিয়ম।
     */
    fun show(
        ctx: Context,
        number: String,
        name: String,
        lines: List<String>,
        lastRemark: String,
        saved: Boolean,
        onOpen: () -> Unit,
        onRemark: () -> Unit
    ) {
        if (!allowed(ctx)) return
        try {
            hide(ctx)   // পুরনোটা থাকলে আগে সরাই — দুটো কার্ড কখনো নয়

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 13), dp(ctx, 12), dp(ctx, 13), dp(ctx, 12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 16).toFloat()
                    setColor(Color.WHITE)
                }
                elevation = dp(ctx, 8).toFloat()
            }

            root.addView(TextView(ctx).apply {
                text = "TK BISWAS PILES CLINIC"
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#0B4F2A"))
            })
            root.addView(TextView(ctx).apply {
                text = name.ifBlank { number }
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1A1A1A"))
                setPadding(0, dp(ctx, 7), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            root.addView(TextView(ctx).apply {
                text = number
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1167D8"))
                setPadding(0, dp(ctx, 2), 0, 0)
            })
            for (ln in lines) {
                if (ln.isBlank()) continue
                root.addView(TextView(ctx).apply {
                    text = ln
                    textSize = 12f
                    setTextColor(Color.parseColor("#5B7089"))
                    setPadding(0, dp(ctx, 3), 0, 0)
                })
            }
            if (lastRemark.isNotBlank()) {
                root.addView(TextView(ctx).apply {
                    text = NoBengali.s("গত রিমার্ক") + " — " + lastRemark
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#0B2545"))
                    setPadding(dp(ctx, 9), dp(ctx, 7), dp(ctx, 9), dp(ctx, 7))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 9).toFloat()
                        setColor(Color.parseColor("#F2F6FA"))
                        setStroke(dp(ctx, 1), Color.parseColor("#DCE4EC"))
                    }
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )).also { it.topMargin = dp(ctx, 8); layoutParams = it }
                })
            }

            fun pill(label: String, bg: String, fg: String, w: Float, click: () -> Unit) =
                TextView(ctx).apply {
                    text = label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(fg))
                    setPadding(dp(ctx, 6), dp(ctx, 8), dp(ctx, 6), dp(ctx, 8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(ctx, 9).toFloat()
                        setColor(Color.parseColor(bg))
                    }
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, w
                    ).apply { marginStart = dp(ctx, 3); marginEnd = dp(ctx, 3) }
                    isClickable = true
                    setOnClickListener { click() }
                }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(ctx, 10) }
            }
            row.addView(pill(if (saved) "Open" else "+ New Enquiry", "#7A3FF2", "#FFFFFF", 1.3f) {
                hide(ctx); onOpen()
            })
            row.addView(pill(NoBengali.s("📝 Remark"), "#1777F2", "#FFFFFF", 1.2f) {
                hide(ctx); onRemark()
            })
            row.addView(pill("✕", "#EEF2F7", "#5B6B81", 0.5f) { hide(ctx) })
            root.addView(row)

            // কার্ডের গায়ে চাপ = Open (TK-নির্দেশ V844-এর একই নিয়ম)
            root.isClickable = true
            root.setOnClickListener { hide(ctx); onOpen() }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                /* ⛔ NOT_FOCUSABLE — কার্ডটা যেন কল ধরার/কাটার বোতাম কেড়ে
                   না নেয়। স্টাফ আগের মতোই কল ধরতে পারবেন। */
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = dp(ctx, 120)
                x = 0
                horizontalMargin = 0.03f
            }

            val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            manager.addView(root, lp)
            wm = manager
            view = root
        } catch (_: Throwable) {
            // ⛔ কোনো কারণে না পারলে চুপচাপ ছেড়ে দিই — নোটিফিকেশন তো আছেই।
            view = null
        }
    }

    /** কার্ড সরানো — বারবার ডাকলেও ক্ষতি নেই। */
    fun hide(ctx: Context) {
        try {
            val v = view ?: return
            (wm ?: ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Throwable) {
        } finally {
            view = null
        }
    }
}
