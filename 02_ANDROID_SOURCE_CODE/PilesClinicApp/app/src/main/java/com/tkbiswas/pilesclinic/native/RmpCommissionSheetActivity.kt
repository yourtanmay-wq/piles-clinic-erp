package com.tkbiswas.pilesclinic.native

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.modules.ModuleUi
import com.tkbiswas.pilesclinic.print.BranchCatalog
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 📒🔒 V1252 (০৯.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ — খাতার সারি ৩৭৩) —
 * **RMP COMMISSION SHEET — কত তারিখে কোন RMP-কে কোন রোগীর জন্য কত দেওয়া হলো।**
 *
 * TK-এর কথা (ধাপে ধাপে, প্রতিটাই প্রুফ দেখে পাশ করা):
 *  · *"কত তারিখে কোন RMP কে কত কমিশন দেওয়া হল সেটা আমি Google Sheet-এর মতো
 *    দেখতে চাই"*
 *  · *"কোন রোগীর জন্য দিলাম"* ⇒ PATIENT কলাম
 *  · *"উপরে ডান সাইডে ⋮-এর মধ্যে থাকবে share · print"*
 *  · *"ব্রাঞ্চ সিলেক্ট হেডারে থাকবে · RMP Commission Sheet লেখাটা ৫০% কম করুন"*
 *  · *"Total Paid নিচে থাকবে কলামের শেষে, উপরে থাকবে না · Cash & Online
 *    দেখতে হলে উপরের ডান দিকের ⋮-এর মধ্যে থাকবে"*
 *  · *"পেশেন্ট Id থাকবে না"*
 *
 * ⛔ **নতুন কোনো টেবিল · কলাম · SQL নেই** — সব সারি আগে থেকেই জমা ছিল
 *    (`RmpCommissionRepository.commissionSheet()`-এ পুরো হিসাব লেখা আছে)।
 * ⛔ **শুধু পড়া** — এই পর্দা থেকে একটাও সারি লেখা/বদলানো/মোছা যায় না।
 * ⛔ RMP বাছাই **পর্দাতেই** ছেঁকে দেখায় — এর জন্য আর একটাও ক্লাউড-ডাক যায় না
 *    (ফ্রি প্ল্যানে বাড়তি চাপ নেই)।
 * ⛔ পর্দায় কোনো বাংলা লেখা নেই (TK-এর স্থায়ী নিয়ম ৯)।
 * 🔒 শুধু Master — টাকার এই তালিকা B211-এর সুরেই মাস্টারের। TK চাইলে
 *    এক লাইনে স্টাফের জন্যও খোলা যাবে।
 */
class RmpCommissionSheetActivity : AppCompatActivity() {

    private var month: String = ""          // "2026-09"
    private var branch: String = ""         // "" = সব ব্রাঞ্চ
    private var rmpPick: String = ""        // "" = সব RMP
    private var all: List<RmpCommissionRepository.SheetRow> = emptyList()
    private var state: String = "loading"   // loading · ok · fail

    private lateinit var body: LinearLayout
    private var menuAnchor: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        month = ymNow()
        render()
        load()
    }

    private fun dp(v: Int) = ModuleUi.dp(this, v)

    private fun box(fill: String, stroke: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        setStroke(dp(1), Color.parseColor(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    // ───────────────────────── তারিখ ও টাকা ─────────────────────────
    private fun cal() = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))

    private fun ymNow(): String {
        val c = cal()
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** "2026-09" → মাসের প্রথম ও শেষ দিন। */
    private fun monthFrom(ym: String): String = "$ym-01"

    private fun monthTo(ym: String): String = try {
        val p = ym.split("-"); val y = p[0].toInt(); val m = p[1].toInt()
        val c = cal()
        c.set(y, m - 1, 1)
        String.format(Locale.US, "%04d-%02d-%02d", y, m, c.getActualMaximum(Calendar.DAY_OF_MONTH))
    } catch (_: Throwable) { "$ym-31" }

    private fun monthLabel(ym: String): String = try {
        val p = ym.split("-"); val m = p[1].toInt()
        arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")[m - 1] + " " + p[0]
    } catch (_: Throwable) { ym }

    /** 🔴 V1158-এর লক করা ফরম্যাট — সব পর্দায় তারিখ dd/MM/yyyy। */
    private fun dmy(iso: String): String {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso.trim()) ?: return iso.trim()
        return m.groupValues[3] + "/" + m.groupValues[2] + "/" + m.groupValues[1]
    }

    private fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

    // ───────────────────────── ছেঁকে নেওয়া ─────────────────────────
    private fun shown(): List<RmpCommissionRepository.SheetRow> =
        if (rmpPick.isBlank()) all else all.filter { it.rmpName == rmpPick }

    // ───────────────────────── পর্দা ─────────────────────────
    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEF2F6"))
        }

        // ── হেডার (TK: ব্রাঞ্চ সিলেক্ট হেডারেই, লেখাটা অর্ধেক মাপে) ──
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B5E34"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        head.addView(TextView(this).apply {
            text = "◀"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(8), 0)
            setOnClickListener { finish() }
        })
        /* 🔤 TK-নির্দেশ: *"RMP Commission sheet এই লেখাটা ৫০% কম করুন"*
           ⇒ ১৭sp ছিল, এখন ঠিক অর্ধেক ৮.৫sp। */
        head.addView(TextView(this).apply {
            text = "RMP COMMISSION SHEET"
            textSize = 8.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = (if (branch.isBlank()) "All Branches" else branch) + "  ▾"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#8CC7A6"))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(9), dp(5), dp(9), dp(5))
            setOnClickListener { pickBranch() }
        })
        val dots = TextView(this).apply {
            text = "⋮"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(2), 0)
            setOnClickListener { openMenu() }
        }
        menuAnchor = dots
        head.addView(dots)
        root.addView(head)

        // ── মাস ও RMP বাছাই ──
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }
        chips.addView(chip(monthLabel(month), true) { pickMonth() })
        chips.addView(chip(if (rmpPick.isBlank()) "All RMP" else rmpPick, false) { pickRmp() })
        root.addView(chips)

        // ── শিট ──
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(body)
        }
        root.addView(scroll)

        root.addView(TextView(this).apply {
            text = "Slide sideways for Mode · Branch · Reference No. · Recorded by"
            textSize = 10f
            setTextColor(Color.parseColor("#8B98A9"))
            setPadding(dp(12), dp(4), dp(12), dp(10))
        })

        setContentView(root)
        paintBody()
    }

    private fun chip(label: String, on: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (on) Color.WHITE else Color.parseColor("#41506A"))
            background = box(if (on) "#0F3D6B" else "#FFFFFF", if (on) "#0F3D6B" else "#E2E9F2", 10)
            setPadding(dp(4), dp(9), dp(4), dp(9))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(3); rightMargin = dp(3) }
            setOnClickListener { onClick() }
        }

    // ───────────────────────── শিটের ঘর ─────────────────────────
    /* 📏 TK-এর পাশ-করা প্রুফের মাপ: পর্দায় DATE · RMP · PATIENT · AMOUNT
       ধরে যায়; বাকিগুলো পাশে টানলে আসে (Google Sheet-এর মতোই)। */
    private val wDate = 62
    private val wRmp = 98
    private val wPatient = 100
    private val wAmount = 76
    private val wMode = 56
    private val wBranch = 86
    private val wRef = 92
    private val wBy = 92

    private fun cell(text: String, widthDp: Int, bold: Boolean, color: String,
                     right: Boolean = false, size: Float = 10.5f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Color.parseColor(color))
            gravity = if (right) (Gravity.END or Gravity.CENTER_VERTICAL) else Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(7), dp(6), dp(7))
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.MATCH_PARENT)
        }

    private fun rowBox(fill: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.parseColor(fill))
    }

    private fun paintBody() {
        body.removeAllViews()
        if (state == "loading") {
            body.addView(ModuleUi.body(this, "Loading...")); return
        }
        if (state == "fail") {
            body.addView(ModuleUi.body(this,
                "⚠️ Could not load now — weak internet. Nothing is lost; open again when online."))
            return
        }
        val list = shown()
        if (list.isEmpty()) {
            body.addView(ModuleUi.body(this, "No commission was paid in " + monthLabel(month) + "."))
            return
        }

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box("#FFFFFF", "#D8E0EA", 8)
        }

        // শিরোনামের সারি
        val hr = rowBox("#F2F6FA")
        hr.addView(cell("DATE", wDate, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("RMP", wRmp, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("PATIENT", wPatient, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("AMOUNT", wAmount, true, "#3C4A5C", right = true, size = 9.5f))
        hr.addView(cell("MODE", wMode, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("BRANCH", wBranch, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("REFERENCE", wRef, true, "#3C4A5C", size = 9.5f))
        hr.addView(cell("RECORDED BY", wBy, true, "#3C4A5C", size = 9.5f))
        grid.addView(hr)
        grid.addView(line())

        var total = 0.0
        for ((i, r) in list.withIndex()) {
            total += r.amount
            val bg = if (i % 2 == 0) "#FFFFFF" else "#FAFCFE"
            val row = rowBox(bg)
            row.addView(cell(dmy(r.paidOn), wDate, false, "#1B2733"))
            row.addView(cell(r.rmpName, wRmp, false, "#1B2733"))
            /* 🟠 আগাম দেওয়া টাকার সঙ্গে কোনো রোগী থাকে না — TK-এর পাশ-করা
               প্রুফ অনুযায়ী সেখানে কমলা "Advance" বসে। */
            row.addView(
                if (r.isAdvance) cell("Advance", wPatient, true, "#B45309")
                else cell(r.patientName, wPatient, true, "#101C2E"))
            row.addView(cell(money(r.amount), wAmount, true, "#0B4F2A", right = true))
            row.addView(cell(modeText(r.mode), wMode, false, "#1B2733"))
            row.addView(cell(r.branch, wBranch, false, "#1B2733"))
            row.addView(cell(r.referenceNo, wRef, false, "#5B6B81"))
            row.addView(cell(r.recordedBy, wBy, false, "#5B6B81"))
            grid.addView(row)
            grid.addView(line())
        }

        /* 💰 TK-নির্দেশ: *"Total Paid নিচে থাকবে কলামের শেষে, উপরে থাকবে না"* */
        val tr = rowBox("#0B5E34")
        tr.addView(cell("TOTAL  ·  " + list.size + " payments", wDate + wRmp + wPatient, true, "#FFFFFF", size = 11f))
        tr.addView(cell(money(total), wAmount, true, "#FFFFFF", right = true, size = 11f))
        tr.addView(cell("", wMode + wBranch + wRef + wBy, false, "#FFFFFF"))
        grid.addView(tr)

        body.addView(HorizontalScrollView(this).apply { addView(grid) })
    }

    private fun line() = TextView(this).apply {
        setBackgroundColor(Color.parseColor("#EDF1F6"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun modeText(m: String): String =
        if (m.equals("ONLINE", true)) "Online" else if (m.equals("CASH", true)) "Cash" else m

    // ───────────────────────── ⋮ ─────────────────────────
    private fun openMenu() {
        val items = listOf("Cash & Online", "Share", "Print / PDF")
        try {
            val pm = android.widget.PopupMenu(this, menuAnchor ?: body)
            items.forEachIndexed { i, label -> pm.menu.add(0, i, i, label) }
            pm.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    0 -> showCashOnline()
                    1 -> sharePaper()
                    2 -> printPaper()
                }
                true
            }
            pm.show()
        } catch (_: Throwable) { }
    }

    // ───────────────────────── বাছাই ─────────────────────────
    private fun pickBranch() {
        val names = listOf("All Branches") + BranchCatalog.all.map { it.displayName }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Branch"))
            .setItems(names.toTypedArray()) { _, which ->
                branch = if (which == 0) "" else names[which]
                rmpPick = ""
                state = "loading"
                render()
                load()
            }
            .setNegativeButton("Cancel", null)
            .show().also { try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    private fun pickMonth() {
        val months = ArrayList<String>()
        val c = cal()
        for (i in 0 until 24) {
            months.add(String.format(Locale.US, "%04d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1))
            c.add(Calendar.MONTH, -1)
        }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Month"))
            .setItems(months.map { monthLabel(it) }.toTypedArray()) { _, which ->
                month = months[which]
                rmpPick = ""
                state = "loading"
                render()
                load()
            }
            .setNegativeButton("Cancel", null)
            .show().also { try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    /** ⛔ RMP-র তালিকা **এই মাসের পড়া সারি থেকেই** বানানো — বাড়তি ক্লাউড-ডাক নেই। */
    private fun pickRmp() {
        val names = listOf("All RMP") + all.map { it.rmpName }.filter { it.isNotBlank() }.distinct().sorted()
        if (names.size == 1) { ModuleUi.toast(this, "No RMP in this month"); return }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "RMP"))
            .setItems(names.toTypedArray()) { _, which ->
                rmpPick = if (which == 0) "" else names[which]
                render()
            }
            .setNegativeButton("Cancel", null)
            .show().also { try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    /* 💵 TK-নির্দেশ: *"Cash & Online দেখতে হলে উপরের ডান দিকের ⋮-এর মধ্যে
       থাকবে, সেখানে গিয়ে দেখবে"*। ⛔ হিসাবটা পর্দার ওই সারিগুলোরই যোগফল —
       আলাদা কোনো নিয়ম বা ক্লাউড-ডাক নেই, তাই দুই জায়গায় সংখ্যা আলাদা হতে পারে না। */
    private fun showCashOnline() {
        val list = shown()
        val cash = list.filter { it.mode.equals("CASH", true) }.sumOf { it.amount }
        val online = list.filter { it.mode.equals("ONLINE", true) }.sumOf { it.amount }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Cash & Online"))
            .setMessage(
                monthLabel(month) + "\n\n" +
                    "Cash  " + money(cash) + "\n" +
                    "Online " + money(online) + "\n\n" +
                    "Total  " + money(cash + online))
            .setPositiveButton("OK", null)
            .show().also { try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    // ───────────────────────── কাগজ ─────────────────────────
    /** ⛔ ছাপা ও শেয়ার — একই HTML, তাই দুটো কাগজ কখনো আলাদা হতে পারে না। */
    private fun paperHtml(): String {
        val list = shown()
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        var total = 0.0
        val rows = StringBuilder()
        for (r in list) {
            total += r.amount
            rows.append("<tr><td>").append(esc(dmy(r.paidOn)))
                .append("</td><td>").append(esc(r.rmpName))
                .append("</td><td>").append(if (r.isAdvance) "Advance" else esc(r.patientName))
                .append("</td><td>").append(esc(modeText(r.mode)))
                .append("</td><td>").append(esc(r.branch))
                .append("</td><td>").append(esc(r.referenceNo))
                .append("</td><td>").append(esc(r.recordedBy))
                .append("</td><td class=\"r\">").append(money(r.amount))
                .append("</td></tr>")
        }
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=794\"><style>" +
            "@page{size:A4;margin:0}*{box-sizing:border-box;margin:0;padding:0}" +
            "body{font-family:Arial,sans-serif;color:#111;background:#fff}" +
            ".sheet{width:210mm;min-height:297mm;padding:10mm 9mm}" +
            "h1{font-size:15px;color:#0B5E34;letter-spacing:.5px}" +
            ".sub{font-size:11px;color:#5B6B81;margin-top:3px}" +
            "table{border-collapse:collapse;width:100%;margin-top:7mm;font-size:10.5px}" +
            "th{background:#F2F6FA;color:#3C4A5C;text-align:left;padding:5px 6px;border:1px solid #D8E0EA;font-size:9.5px}" +
            "td{padding:5px 6px;border:1px solid #E6ECF3}" +
            ".r{text-align:right;font-weight:bold;color:#0B4F2A}" +
            "tr:nth-child(even) td{background:#FAFCFE}" +
            ".tot td{background:#0B5E34;color:#fff;font-weight:bold;border-color:#0B5E34}" +
            ".tot td.r{color:#fff}" +
            "</style></head><body><div class=\"sheet\">" +
            "<h1>RMP COMMISSION SHEET</h1><div class=\"sub\">" +
            monthLabel(month) + "  ·  " + (if (branch.isBlank()) "All Branches" else branch) +
            (if (rmpPick.isBlank()) "" else "  ·  " + rmpPick) + "</div>" +
            "<table><tr><th>DATE</th><th>RMP</th><th>PATIENT</th><th>MODE</th><th>BRANCH</th>" +
            "<th>REFERENCE</th><th>RECORDED BY</th><th>AMOUNT</th></tr>" + rows +
            "<tr class=\"tot\"><td colspan=\"7\">TOTAL  ·  " + list.size + " payments</td>" +
            "<td class=\"r\">" + money(total) + "</td></tr></table>" +
            "</div></body></html>"
    }

    private var printWeb: android.webkit.WebView? = null

    private fun printPaper() {
        if (shown().isEmpty()) { ModuleUi.toast(this, "Nothing to print"); return }
        try {
            val wv = android.webkit.WebView(this)
            wv.settings.javaScriptEnabled = false
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView, url: String) {
                    try {
                        val pm = getSystemService(android.content.Context.PRINT_SERVICE)
                            as android.print.PrintManager
                        pm.print("RMP Commission Sheet",
                            view.createPrintDocumentAdapter("RMP Commission Sheet"),
                            android.print.PrintAttributes.Builder().build())
                    } catch (_: Throwable) {
                        ModuleUi.toast(this@RmpCommissionSheetActivity, "Print not available")
                    }
                }
            }
            printWeb = wv
            wv.loadDataWithBaseURL("file:///android_asset/", paperHtml(), "text/html", "UTF-8", null)
        } catch (_: Throwable) { ModuleUi.toast(this, "Print not available") }
    }

    private fun sharePaper() {
        if (shown().isEmpty()) { ModuleUi.toast(this, "Nothing to share"); return }
        try {
            com.tkbiswas.pilesclinic.print.PrescriptionWhatsAppShare.shareHtml(
                activity = this,
                html = paperHtml(),
                documentTitle = "RMP Commission Sheet",
                patientName = monthLabel(month),
                allowPrint = true
            )
        } catch (_: Throwable) { ModuleUi.toast(this, "Could not share now") }
    }

    // ───────────────────────── পড়া ─────────────────────────
    private fun load() {
        val ym = month
        val br = branch
        Thread {
            val res = try {
                RmpCommissionRepository.commissionSheet(monthFrom(ym), monthTo(ym), br)
            } catch (_: Throwable) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                // ⛔ এর মধ্যে TK মাস/ব্রাঞ্চ বদলে ফেললে পুরনো উত্তর বসতে দেওয়া হয় না।
                if (ym != month || br != branch) return@runOnUiThread
                if (res == null || !res.ok) { state = "fail"; paintBody(); return@runOnUiThread }
                all = res.value ?: emptyList()
                state = "ok"
                paintBody()
            }
        }.start()
    }
}
