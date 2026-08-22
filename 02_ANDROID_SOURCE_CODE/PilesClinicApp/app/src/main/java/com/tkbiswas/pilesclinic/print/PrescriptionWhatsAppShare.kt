package com.tkbiswas.pilesclinic.print

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * 📤🔒 V491 (২০.০৮.২০২৬, TK-নির্দেশ) — **প্রেসক্রিপশন WhatsApp-এ PDF হিসেবে পাঠানো।**
 *
 * TK-এর কথা: *"WhatsApp অপশন যেন থাকে, আর সেখানে চাপ দিলে WhatsApp / Business
 * জোর করে ওপেন হতে হবে, হোয়াটসঅ্যাপে যেন পিডিএফ শেয়ারও করতে পারি।"*
 *
 * ─── কেন আলাদা একটা ফাইল লাগল ───────────────────────────────────────────────
 * V409 থেকে Prescription ও Medicine Slip ছাপে **WebView → PrintManager** পথে,
 * যাতে ফোন আর কম্পিউটারের কাগজ হুবহু এক থাকে। ওই পথে Android নিজেই ছাপার পাতা
 * বানায় — অ্যাপের হাতে **কোনো PDF ফাইল আসে না**। তাই PrintPreviewActivity-তে
 * Prescription-এর "Share PDF" বোতামটা ইচ্ছে করেই লুকানো। WhatsApp-এ পাঠাতে
 * গেলে একটা সত্যিকারের ফাইল চাই।
 *
 * ─── 🔴 প্রথম চেষ্টায় যে ভুল হয়েছিল (২০.০৮.২০২৬ রাত ১০:৩১, TK-এর বিল্ড) ────
 * প্রথমে WebView-এর নিজের `PrintDocumentAdapter`-কে সরাসরি ফাইলে লিখতে বলা
 * হয়েছিল (`adapter.onLayout` → `adapter.onWrite`)। কিন্তু ওই দুটো ডাকের জন্য
 * `PrintDocumentAdapter.LayoutResultCallback` / `WriteResultCallback`-এর
 * object বানাতে হয়, আর ওদের constructor **package-private** — `android.print`
 * প্যাকেজের বাইরে থেকে বানানোই যায় না। তাই বিল্ড ভাঙল:
 *     Cannot access '<init>': it is package-private in 'LayoutResultCallback'
 *     Cannot access '<init>': it is package-private in 'WriteResultCallback'
 * ⇒ ওই পথ Android-এ **সোজাসুজি সম্ভবই নয়**। ঘুরপথে (নিজের ক্লাস `android.print`
 *   প্যাকেজে রেখে) করা যায়, কিন্তু সেটা কিছু ফোনে চলার সময় ভেঙে যেতে পারে —
 *   ক্লিনিকের অ্যাপে সেই ঝুঁকি নেওয়া হয়নি।
 *
 * ─── এখন যেভাবে করা হচ্ছে (শুধু স্বীকৃত, নিরাপদ API) ───────────────────────
 * **একই HTML** (`PrescriptionHtml.build` — অর্থাৎ হুবহু একই নকশা, একই উৎস)
 * একটা WebView-তে **A4-এর মাপে** (৭৯৪ × ১১২৩ px — ব্রাউজার A4 ছাপার সময় ঠিক
 * এই মাপই ধরে) বসিয়ে, সেটাকে Android-এর নিজের `PdfDocument`-এর পাতায় এঁকে
 * দেওয়া হয়। পাতা তৈরি হয় A4-এর আসল মাপে (৫৯৫ × ৮৪২ পয়েন্ট), আর আঁকার সময়
 * ঠিক ওই অনুপাতে ছোট করা হয় — ব্রাউজারও ছাপার সময় এই একই অনুপাত ব্যবহার করে।
 * লেখা ভেক্টর হিসেবেই বসে, তাই ঝাপসা হয় না। বড় প্রেসক্রিপশনে একাধিক পাতা
 * নিজে থেকেই হয়।
 *
 * ⛔ ছাপার পুরনো পথ (`PrescriptionHtmlPrint.print`) এক অক্ষরও বদলায়নি।
 * ⛔ কোন ওষুধ বাছা হল · কী সেভ হয় · ক্লাউডে কী যায় — কিছুই ছোঁয়া হয়নি।
 * 🔒 WhatsApp বাছাইয়ের নিয়ম V449-এর হুবহু একই — কখনো নিঃশব্দে একটা খোলে না,
 *    সবসময় দুটো অপশনই দেখানো হয় (TK-এর লক করা নিয়ম)।
 *
 * ⚠️ TK প্রথমবার মিলিয়ে দেখবেন: WhatsApp-এ যাওয়া PDF আর ছাপা কাগজ এক কিনা।
 */
object PrescriptionWhatsAppShare {

    private const val PKG_PERSONAL = "com.whatsapp"
    private const val PKG_BUSINESS = "com.whatsapp.w4b"

    /** A4 — ব্রাউজার ছাপার সময় যে CSS-পিক্সেল মাপ ধরে (৯৬ dpi)। */
    private const val A4_WIDTH_PX = 794
    private const val A4_HEIGHT_PX = 1123

    /** A4 — PDF-এর আসল মাপ (পয়েন্ট, ৭২ dpi)। */
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    /** কাজ শেষ না হওয়া পর্যন্ত WebView বাঁচিয়ে রাখতে হয় (নইলে খালি পাতা)। */
    @Suppress("StaticFieldLeak")
    private var keepAlive: WebView? = null

    // 🔴 V501: আগে এই তিনটে তথ্য `PrintDocumentModel`-এর ভিতর থেকে নেওয়া হতো।
    //    এখন যেকোনো HTML চলে বলে আলাদা করে রাখা হয় (একবারে একটাই শেয়ার চলে,
    //    তাই এতে গোলমালের সুযোগ নেই — WebView-ও একটাই `keepAlive`)।
    private var docTitle: String = "Document"
    private var docPatient: String = ""
    private var wantPrint: Boolean = false

    fun share(activity: Activity, model: PrintDocumentModel) {
        val html = try {
            PrescriptionHtml.build(activity, model)
        } catch (e: Throwable) {
            fail(activity, "Could not prepare the paper: ${e.message}"); return
        }
        shareHtml(activity, html, model.documentTitle, model.patientName)
    }

    /**
     * 🔴🔒 V501 (TK-নির্দেশ ২১.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত) —
     * *"Back Save মাঝখানে Share রাখতে হবে… WhatsApp / business WhatsApp ওপেন হবে…
     *  A4 size pdf চাইলে প্রিন্টও করা যাবে।"*
     *
     * আগের `share()` শুধু **Prescription**-এর HTML বানাতে জানত। এখন যেকোনো
     * তৈরি HTML (যেমন `CheckupA4Report.html(...)`) একই প্রমাণিত পথে A4 PDF
     * হয়ে WhatsApp/Business/Print-এ যেতে পারে।
     *
     * ⛔ Prescription-এর পুরনো ডাক (`share(activity, model)`) হুবহু আগের মতোই
     *    চলে — সেটা এখন শুধু এই ফাংশনটাকেই ডাকে। কোনো আচরণ বদলায়নি।
     *
     * @param allowPrint `true` হলে বাছাইয়ের তালিকায় তৃতীয় একটা "Print / PDF"
     *        থাকে (প্রকল্পের নিজের প্রমাণিত `PdfPrintDocumentAdapter` দিয়ে,
     *        ঠিক `PrintPreviewActivity`-র মতোই)।
     */
    fun shareHtml(
        activity: Activity,
        html: String,
        documentTitle: String,
        patientName: String,
        allowPrint: Boolean = false
    ) {
        Toast.makeText(activity, "Preparing PDF…", Toast.LENGTH_SHORT).show()
        docTitle = documentTitle
        docPatient = patientName
        wantPrint = allowPrint

        val wv = WebView(activity)
        // ⚠️ JavaScript শুধু পাতার **উচ্চতা মাপার** জন্য। টেমপ্লেটে নিজের কোনো
        //    স্ক্রিপ্ট নেই ও বাইরের কিছু লোড হয় না, তাই এতে ঝুঁকি নেই।
        wv.settings.javaScriptEnabled = true
        wv.setBackgroundColor(Color.WHITE)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                measureThenBuild(activity, view)
            }
        }
        keepAlive = wv
        layoutAt(wv, A4_HEIGHT_PX)
        // baseURL = file:///android_asset/  → লোগোর ছবি রিজলভ হয় (ছাপার পথের মতোই)।
        wv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
    }

    /** WebView-কে A4-এর চওড়ায় বসানো, যাতে CSS ঠিক ছাপার মতোই সাজে। */
    private fun layoutAt(view: WebView, heightPx: Int) {
        val h = max(heightPx, A4_HEIGHT_PX)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(A4_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, A4_WIDTH_PX, h)
    }

    private fun measureThenBuild(activity: Activity, view: WebView) {
        try {
            view.evaluateJavascript("document.body.scrollHeight") { value ->
                val measured = value?.trim()?.trim('"')?.toFloatOrNull()?.toInt() ?: A4_HEIGHT_PX
                val total = max(measured, A4_HEIGHT_PX)
                layoutAt(view, total)
                // আঁকার আগে একটু সময় — ছবি ও ফন্ট বসে যেতে দিন।
                view.postDelayed({ buildPdfThenShare(activity, view, total) }, 250L)
            }
        } catch (_: Throwable) {
            layoutAt(view, A4_HEIGHT_PX)
            view.postDelayed({ buildPdfThenShare(activity, view, A4_HEIGHT_PX) }, 250L)
        }
    }

    private fun buildPdfThenShare(
        activity: Activity, view: WebView, totalHeightPx: Int
    ) {
        val outFile: File
        try {
            val dir = File(activity.cacheDir, "pdfs").apply { mkdirs() }
            val safeTitle = docTitle.replace(Regex("[^A-Za-z0-9]+"), "_")
            val safeName = docPatient.trim().replace(Regex("[^A-Za-z0-9]+"), "_").take(24)
            // নাম আলাদা রাখা হয়, যাতে পুরনো ফাইল ভুল করে WhatsApp-এ না যায়।
            outFile = File(dir, "${safeTitle}_${safeName}_${System.currentTimeMillis()}.pdf")
        } catch (e: Throwable) {
            keepAlive = null; fail(activity, "Could not create the file: ${e.message}"); return
        }

        val document = PdfDocument()
        var built = false
        try {
            val scale = A4_WIDTH_PT.toFloat() / A4_WIDTH_PX.toFloat()
            val pageHeightPx = A4_HEIGHT_PT / scale           // এক পাতায় কত px ধরে
            val pageCount = max(1, ceil(totalHeightPx / pageHeightPx).toInt())
            for (i in 0 until pageCount) {
                val info = PdfDocument.PageInfo
                    .Builder(A4_WIDTH_PT, A4_HEIGHT_PT, i + 1)
                    .create()
                val page = document.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.scale(scale, scale)
                canvas.translate(0f, -(i * pageHeightPx))
                view.draw(canvas)
                canvas.restore()
                document.finishPage(page)
            }
            FileOutputStream(outFile).use { document.writeTo(it) }
            built = true
        } catch (e: Throwable) {
            fail(activity, "Could not build the PDF: ${e.message}")
        } finally {
            try { document.close() } catch (_: Throwable) { }
            keepAlive = null
        }
        if (!built) return

        if (!outFile.exists() || outFile.length() <= 0L) {
            fail(activity, "The PDF came out empty — please try again")
            return
        }
        chooseWhatsAppAndSend(activity, outFile)
    }

    /**
     * 🔒 V449-এর লক করা নিয়ম এখানেও: কখনো নিঃশব্দে Personal WhatsApp বেছে নেওয়া
     * হয় না — সবসময় দুটো অপশনই দেখানো হয়, ব্যবহারকারী বেছে দিলে তবেই খোলে।
     */
    private fun chooseWhatsAppAndSend(activity: Activity, file: File) {
        val uri = try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } catch (e: Throwable) {
            fail(activity, "Could not share the file: ${e.message}"); return
        }
        val caption = buildString {
            append(docTitle.uppercase(Locale.US))
            if (docPatient.isNotBlank()) append(" — ").append(docPatient.trim())
        }
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, caption)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 🔴 V501: দরকার হলে তৃতীয় পথ — A4 কাগজে ছাপা বা PDF সেভ।
        val options = if (wantPrint) arrayOf("WhatsApp", "WhatsApp Business", "Print / PDF")
                      else arrayOf("WhatsApp", "WhatsApp Business")
        val packages = arrayOf(PKG_PERSONAL, PKG_BUSINESS)
        try {
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Send PDF with")
                .setItems(options) { _, which ->
                    if (which >= packages.size) { printPdf(activity, file); return@setItems }
                    try {
                        activity.startActivity(Intent(base).setPackage(packages[which]))
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(activity, "${options[which]} is not installed on this phone", Toast.LENGTH_LONG).show()
                    } catch (e: Throwable) {
                        Toast.makeText(activity, "Could not open ${options[which]}: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Throwable) {
            fail(activity, "Could not show the WhatsApp choice: ${e.message}")
        }
    }

    /**
     * A4 কাগজে ছাপা / PDF সেভ — প্রকল্পের **নিজের প্রমাণিত** পথেই
     * (`PdfPrintDocumentAdapter`, ঠিক `PrintPreviewActivity.printPdf()`-এর মতো)।
     * ⛔ নতুন কোনো Print-ব্যবস্থা বানানো হয়নি।
     */
    private fun printPdf(activity: Activity, file: File) {
        try {
            val pm = activity.getSystemService(android.content.Context.PRINT_SERVICE)
                as android.print.PrintManager
            val jobName = buildString {
                append(docTitle)
                if (docPatient.isNotBlank()) append(" - ").append(docPatient.trim())
            }
            pm.print(jobName, PdfPrintDocumentAdapter(jobName, file),
                android.print.PrintAttributes.Builder().build())
        } catch (e: Throwable) {
            Toast.makeText(activity, "Print failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fail(activity: Activity, message: String) {
        keepAlive = null
        try { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() } catch (_: Throwable) { }
    }
}
