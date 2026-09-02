package com.tkbiswas.pilesclinic.clinical

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.modules.ModuleUi
import com.tkbiswas.pilesclinic.native.PremiumAlert
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 🖥️🔒 V982 (০২.০৯.২০২৬, TK-এর পাশ-করা ফটো-প্রুফ) —
 * **এস্টিমেটের ফুল-স্ক্রিন কাগজ।**
 *
 * TK-এর নিজের কথা:
 *  • *"আমি তো চেয়েছিলাম A4 সাইজ প্রিন্ট আউট হওয়ার পর যেমন হবে, ঠিক সে রকম
 *    ভাবেই এখানে থাকবে"*
 *  • *"সেভ করলে সেভাবে, প্রিন্ট করলে প্রিন্ট হবে, শেয়ার করলে শেয়ার হবে"*
 *  • *"তাহলে ফুল স্ক্রিন পর্দা খুলবে, আলাদা পপ-আপ লাগবে না, zoom করা যাবে"*
 *
 * ─── কীভাবে ─────────────────────────────────────────────────────────────
 * পর্দায় যেটা দেখা যায় সেটা **ঠিক ছাপার কাগজটাই** (`EstimateHtmlPrint`)।
 * শুধু পর্দার জন্য হলুদ ঘর ও লিঙ্ক বসে (`editable = true`); ছাপা ও শেয়ারের
 * সময় ওগুলো থাকে না — অর্থাৎ **যা দেখছি হুবহু তাই ছাপে**।
 *
 * ⛔ কোনো JavaScript-সেতু নেই — হলুদ ঘরে চাপ দিলে পাতা `est://…` ঠিকানায়
 *    যেতে চায়, WebView সেটা আটকে দেয় আর অ্যাপ ছোট বাক্স খুলে দেয়। তাই
 *    হিসাব সবসময় একটাই জায়গায় (`EstimateModel`) — পর্দা আর কাগজে কখনো
 *    দুরকম সংখ্যা হতে পারে না।
 * ⛔ ছাপা ও শেয়ার প্রকল্পের **প্রমাণিত পথেই** (`PrescriptionWhatsAppShare`)।
 */
class EstimatePaperActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHEET = "estimate_sheet_json"
        const val RESULT_SHEET = "estimate_sheet_json_out"
    }

    private lateinit var sheet: EstimateModel.Sheet
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sheet = try { EstimateModel.parse(intent?.getStringExtra(EXTRA_SHEET).orEmpty()) }
                catch (_: Throwable) { EstimateModel.Sheet() }
        setContentView(buildScreen())
        render()
    }

    /* 🔴🔒 V786-এর নিয়ম এই পর্দাতেও — কল এলে বা মেমরির চাপে Android পর্দা
       ভেঙে আবার বানালে রোগীর পরিচয় যেন হারিয়ে না যায়।
       ⛔ মেমরিতে রোগী থাকলে `restoreFrom()` কিচ্ছু করে না (RoleSession.kt)। */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        RoleSession.saveTo(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        RoleSession.restoreFrom(savedInstanceState)
    }

    private fun dp(v: Int) = ModuleUi.dp(this, v)

    private fun box(fill: String, stroke: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        setStroke(dp(1), Color.parseColor(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    // ─────────────────────────── পর্দা ───────────────────────────
    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EDF1EE"))
        }

        // উপরের সবুজ পট্টি
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B5B2F"))
            setPadding(dp(14), dp(12), dp(16), dp(12))
            addView(TextView(this@EstimatePaperActivity).apply {
                text = "◀"
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(dp(4), 0, dp(12), 0)
                setOnClickListener { askBeforeLeaving() }
            })
            addView(TextView(this@EstimatePaperActivity).apply {
                text = "COST ESTIMATE  ·  A4"
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                letterSpacing = 0.04f
            })
        })

        // যোগ করার সারি
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }
        fun chip(text: String, weight: Float, colour: String, dashed: Boolean, run: () -> Unit) {
            tools.addView(TextView(this).apply {
                this.text = text
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(colour))
                background = box("#FFFFFF", if (dashed) "#C9D6CD" else "#B9CBE0", 11)
                setPadding(dp(4), dp(9), dp(4), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    .apply { leftMargin = dp(3); rightMargin = dp(3) }
                setOnClickListener { run() }
            })
        }
        chip("+ Treatment", 1f, "#0B66D8", false) { EstimateDialog.pickTreatment(this, sheet) { render() } }
        chip("+ Medicine", 1f, "#0B66D8", false) { EstimateDialog.pickMedicine(this, sheet) { render() } }
        chip("+ Other", 1f, "#0B66D8", false) { EstimateDialog.pickOther(this, sheet) { render() } }
        chip("Price List", 0.9f, "#5B6B61", true) {
            startActivity(android.content.Intent(this, PriceListActivity::class.java))
        }
        root.addView(tools)

        // কাগজ — দুই আঙুলে জুম হয়
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#EDF1EE"))
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isVerticalScrollBarEnabled = true
            webViewClient = object : WebViewClient() {
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    handleTap(url)
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: android.webkit.WebResourceRequest
                ): Boolean = handleTap(request.url?.toString())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(web)

        // নিচের তিনটে বোতাম
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        fun action(text: String, fill: String, ink: String, run: () -> Unit) {
            bottom.addView(TextView(this).apply {
                this.text = text
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(ink))
                background = box(fill, fill, 12)
                setPadding(dp(4), dp(13), dp(4), dp(13))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = dp(4); rightMargin = dp(4) }
                setOnClickListener { run() }
            })
        }
        action("💾  SAVE", "#EEF2F7", "#41506A") { saveAndClose() }
        action("🖨  PRINT", "#0F3D6B", "#FFFFFF") { printPaper() }
        action("📤  SHARE", "#0B4F2A", "#FFFFFF") { sharePaper() }
        root.addView(bottom)
        return root
    }

    // ─────────────────────────── কাগজ আঁকা ───────────────────────────
    /** পর্দার কাগজ — ছাপার কাগজটাই, শুধু হলুদ ঘর ও লিঙ্ক যোগ করা। */
    private fun paperHtml(editable: Boolean): String {
        val age = RoleSession.currentPatientAge
        val sex = RoleSession.currentPatientSex
        val ageSex = listOf(age, sex).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
        return EstimateHtmlPrint.build(
            sheet = sheet,
            branch = RoleSession.currentPatientBranch,
            name = RoleSession.currentPatientName.ifBlank { "-" },
            patientId = RoleSession.currentPatientDisplayId.ifBlank { RoleSession.currentPatientId },
            ageSex = ageSex,
            mobile = RoleSession.currentPatientMobile.ifBlank { "-" },
            address = RoleSession.currentPatientAddress.ifBlank { "-" },
            date = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(java.util.Date()),
            editable = editable
        )
    }

    private fun render() {
        try {
            web.loadDataWithBaseURL(
                "file:///android_asset/", paperHtml(true), "text/html", "UTF-8", null
            )
        } catch (_: Throwable) { }
    }

    // ─────────────────────────── চাপ দিলে ───────────────────────────
    /** `est://line/3` বা `est://discount` — অ্যাপ নিজে ছোট বাক্স খোলে। */
    private fun handleTap(url: String?): Boolean {
        val u = url.orEmpty()
        if (!u.startsWith("est://")) return false
        try {
            if (u.startsWith("est://discount")) { editDiscount(); return true }
            val at = u.removePrefix("est://line/").toIntOrNull() ?: return true
            if (at in sheet.lines.indices) editLine(at)
        } catch (_: Throwable) { }
        return true
    }

    private fun numberField(value: String): EditText = EditText(this).apply {
        setText(value)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#101C2E"))
        /* 🔒 খাতার সারি B411 — শুধু সংখ্যার ধরন দিলে কিছু ফোনে কীবোর্ড খোলে না। */
        inputType = InputType.TYPE_CLASS_TEXT
        keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")
        background = box("#F8FBFE", "#D6E1EE", 9)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        textSize = 9.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#8B98A9"))
        letterSpacing = 0.09f
        setPadding(0, dp(8), 0, dp(3))
    }

    private fun editLine(at: Int) {
        val line = sheet.lines[at]
        val rate = numberField(EstimateModel.moneyShort(line.rate))
        val qty = numberField(EstimateModel.moneyShort(line.qty))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(4))
            addView(caption("RATE (₹)")); addView(rate)
            addView(caption("QUANTITY")); addView(qty)
        }
        val strikeLabel = if (line.struck) "Un-strike" else "Strike out"
        val dlg = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "✏️ " + line.name))
            .setView(body)
            .setPositiveButton("Save") { _, _ ->
                line.rate = EstimateModel.num(rate.text?.toString())
                line.qty = EstimateModel.num(qty.text?.toString())
                render()
            }
            .setNeutralButton(strikeLabel) { _, _ -> line.struck = !line.struck; render() }
            .setNegativeButton("Delete") { _, _ ->
                try { sheet.lines.removeAt(at) } catch (_: Throwable) { }
                render()
            }
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    private fun editDiscount() {
        val field = numberField(if (sheet.discount > 0) EstimateModel.moneyShort(sheet.discount) else "")
        val mode = TextView(this).apply {
            text = if (sheet.discountPct) "Now in  %  — tap to use ₹" else "Now in  ₹  — tap to use %"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F3D6B"))
            background = box("#EEF2F7", "#DCE4EF", 10)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        mode.setOnClickListener {
            sheet.discountPct = !sheet.discountPct
            mode.text = if (sheet.discountPct) "Now in  %  — tap to use ₹" else "Now in  ₹  — tap to use %"
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(4))
            addView(caption("DISCOUNT")); addView(field)
            addView(mode.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            })
        }
        val dlg = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "💸 Discount"))
            .setView(body)
            .setPositiveButton("Save") { _, _ ->
                sheet.discount = EstimateModel.num(field.text?.toString())
                render()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    // ─────────────────────────── তিনটে কাজ ───────────────────────────
    private fun saveAndClose() {
        setResult(RESULT_OK, android.content.Intent().putExtra(RESULT_SHEET, sheet.toJson().toString()))
        finish()
    }

    /** A4 কাগজে ছাপা — প্রকল্পের প্রমাণিত পথেই (WebView → PrintManager)। */
    private var printWeb: WebView? = null

    private fun printPaper() {
        if (sheet.isEmpty) { Toast.makeText(this, "Add at least one item first", Toast.LENGTH_SHORT).show(); return }
        try {
            val wv = WebView(this)
            wv.settings.javaScriptEnabled = false
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    try {
                        val pm = getSystemService(android.content.Context.PRINT_SERVICE)
                            as android.print.PrintManager
                        pm.print(
                            "Estimate", view.createPrintDocumentAdapter("Estimate"),
                            android.print.PrintAttributes.Builder().build()
                        )
                    } catch (_: Throwable) {
                        Toast.makeText(this@EstimatePaperActivity, "Print not available", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            printWeb = wv
            wv.loadDataWithBaseURL("file:///android_asset/", paperHtml(false), "text/html", "UTF-8", null)
        } catch (_: Throwable) {
            Toast.makeText(this, "Print not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePaper() {
        if (sheet.isEmpty) { Toast.makeText(this, "Add at least one item first", Toast.LENGTH_SHORT).show(); return }
        try {
            com.tkbiswas.pilesclinic.print.PrescriptionWhatsAppShare.shareHtml(
                activity = this,
                html = paperHtml(false),
                documentTitle = "Estimate",
                patientName = RoleSession.currentPatientName,
                allowPrint = true
            )
        } catch (e: Throwable) {
            Toast.makeText(this, "Share not available: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** ⛔ ভুল করে বেরিয়ে গেলে হিসাব যেন হারিয়ে না যায়। */
    private fun askBeforeLeaving() {
        if (sheet.isEmpty) { finish(); return }
        val dlg = AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "⚠️ Are you sure?"))
            .setMessage("Leave without saving this estimate?")
            .setPositiveButton("Save") { _, _ -> saveAndClose() }
            .setNegativeButton("Leave") { _, _ -> finish() }
            .setNeutralButton("Stay", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { askBeforeLeaving() }
}
