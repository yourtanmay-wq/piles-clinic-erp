package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import com.tkbiswas.pilesclinic.print.BranchCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TK-APPROVED (2026-07-20): prints the Report Card exactly like the paper
 * register — branch-specific clinic header, patient details, Bill/Paid/Due,
 * and the Visit/Date/Progress/Paid/Due table with a faint branch logo
 * watermark. Uses the same data source as the on-screen Report Card, so the
 * print always matches what is shown.
 *
 * Rendered as HTML and printed through Android's own PrintManager (via a
 * WebView), so it can go to any printer or "Save as PDF".
 */
object ReportCardPrinter {

    // Keep a strong reference while the print job spins up (else the WebView is GC'd).
    private val liveWebViews = mutableListOf<WebView>()

    /**
     * 🔵🔒 V522 (২২.০৮.২০২৬, TK-নির্দেশ) — `preferRowId`
     * এক নম্বরে দুজন আলাদা রোগী থাকলে **কার রিপোর্ট ছাপা হবে** তা আর
     * আন্দাজ করা হয় না — যে পর্দা ছাপতে বলেছে, সে-ই বলে দেয়।
     * ⛔ ফাঁকা রাখলে হুবহু আগের আচরণ, তাই পুরোনো ডাক অপরিবর্তিত।
     * ⛔ বাড়তি কোনো cloud-read নেই।
     */
    fun print(activity: androidx.appcompat.app.AppCompatActivity, mobile: String, user: NativeUser, preferRowId: String = "", preferPatientCode: String = "") {
        activity.lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { PatientTimelineRepository.build(mobile, null, activity, preferRowId = preferRowId, preferPatientCode = preferPatientCode) }
                val patientRow = withContext(Dispatchers.IO) {
                    try {
                        // TK-REQUESTED (2026-07-27), ধাপ ৩: this used to ask for
                        // ONE row by mobile and take whatever came back -- with a
                        // duplicate registration that could be the empty row, so
                        // Age/Address/Sex went blank while the rest of the app
                        // used the real row. The Timeline just above has ALREADY
                        // resolved this patient's real row, so we simply read
                        // that same row by its id -- one row, one request, and
                        // the screen, the print and the Timeline can never
                        // disagree. Falls back to the old lookup when there is no
                        // patients row yet (enquiry-only), exactly as before.
                        // TK-REQUESTED (2026-07-27), ধাপ ৩খ: the fallback below
                        // (used only when the Timeline found no patients row of
                        // its own) still asked for ONE row and took whatever came
                        // back, so on a duplicate it could pick a different row
                        // than every other screen. It now reads the same way and
                        // applies the ONE shared rule.
                        val rid = data.rowId
                        /* 🔴🔒 V794 — এই সারিটা থেকে শুধু বয়স/ঠিকানা/লিঙ্গ নেওয়া হয়
                           (যাচাই করা); ছবিটা আসে `PatientTimelineRepository.build`
                           থেকে। তাই এখানে ছবি ছাড়া পড়া হয়। */
                        val arr = if (rid.isNotBlank()) SupabaseClient.fetchListSlim("patients",
                                "id=eq.$rid", 1, SupabaseClient.PATIENT_NO_PHOTO_COLS)
                            else SupabaseClient.findByMobile("patients", "+91$mobile",
                                SupabaseClient.PATIENT_NO_PHOTO_COLS, 50)
                        val ownBranch = user.branch
                        PatientIdentity.pickPatientRow(arr, ownBranch)
                    } catch (_: Throwable) { null }
                }
                // 🆕 (03.08.2026, TK-অনুমোদনে) — গভীর অডিট: optString(key, "")-ও কলাম
                // সত্যিই NULL হলে "null" শব্দ ফেরত দেয় (B286/B327-এর একই বাগ) —
                // ReportCardActivity.kt-এর সাথে মিলিয়ে এখানেও `.s()` বসানো হলো,
                // যাতে ছাপা রিপোর্ট কার্ডে ভুল করে "null" না বেরোয়।
                val age = patientRow?.s("age") ?: ""
                val address = patientRow?.s("address") ?: ""
                // TK-APPROVED (2026-07-22): Sex fetched from the same patients
                // row already being queried above (no extra network call) so
                // print matches the on-screen Report Card's new field order.
                val sex = patientRow?.s("sex") ?: ""
                val branch = BranchCatalog.byName(data.branch)
                val logoB64 = withContext(Dispatchers.IO) {
                    try {
                        activity.assets.open(branch.logoAssetPath).use { ins ->
                            android.util.Base64.encodeToString(ins.readBytes(), android.util.Base64.NO_WRAP)
                        }
                    } catch (_: Throwable) { "" }
                }
                val html = buildHtml(data, age, address, sex, branch, logoB64)
                startPrint(activity, html)
            } catch (e: Exception) {
                android.widget.Toast.makeText(activity, "Print failed — retry", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun money(v: Double) = "\u20B9" + "%,.0f".format(v)

    private fun ordinal(n: Int): String {
        val s = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th"
        }
        return "$n$s"
    }

    // BUG FIX (2026-07-26, full-project audit): printed Report Card dates
    // must follow the locked 2026-07-24 global rule -- 31.12.2026 (dots),
    // never 31/12/2026. Display-only; stored data is untouched, and this
    // stays byte-for-byte identical to ReportCardActivity's own helper so
    // the screen and the paper can never disagree.
    private fun formatDisplayDate(iso: String): String {
        val parts = iso.take(10).split("-")
        return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else iso
    }

    // TK-APPROVED (2026-07-22): "report generated" date for the new patient
    // header field (Date), same source format ReportCardActivity uses.
    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // 🔒 B554 (08.08.2026, TK-অনুমোদিত প্রুফ "গ্লোবাল রুলস") — ঠিকানা দু'লাইনে:
    // থানা-চিহ্নের (PS/P.S/P/S/Thana/থানা/Police Station) আগে HTML `<br>`, তাই
    // ১ম লাইন গ্রাম+পোস্ট, ২য় লাইন থানা+জেলা। চিহ্ন না পেলে এক লাইনেই থাকে।
    // CheckupA4Report.addrTwoLines/formatAddressTwoLines-এর হুবহু একই নিয়ম।
    // সেভ-হওয়া ঠিকানা বদলায় না — শুধু ছাপার সময় ভাঙা।
    private fun addrTwoLines(raw: String): String {
        val markers = listOf("PS:", "P.S", "P/S", "Thana", "থানা", "Police Station")
        var idx = -1
        for (m in markers) { val i = raw.indexOf(m, ignoreCase = true); if (i > 0 && (idx == -1 || i < idx)) idx = i }
        if (idx <= 0) return esc(raw)
        val first = raw.substring(0, idx).trim().trimEnd(',').trim()
        val second = raw.substring(idx).trim()
        if (first.isBlank() || second.isBlank()) return esc(raw)
        return esc(first) + "<br>" + esc(second)
    }

    private fun buildHtml(
        data: TimelineData, age: String, address: String, sex: String,
        branch: com.tkbiswas.pilesclinic.print.BranchInfo, logoB64: String
    ): String {
        val bill = data.billTotal
        // TK-REQUESTED (2026-07-24): matches ReportCardActivity's on-screen
        // grouping (section 145) -- an attendance-only day (no money that
        // day) now gets its own printed row too, so screen and print always
        // agree. attendance_mark rows always carry amount=0.0, so paidTotal
        // below is unaffected either way.
        val payEntries = data.entries
            .filter { it.paymentId != null && it.payType != "visit_fee" && it.payType != "bill_edit" && it.payType != "chamber_expected" }
            .sortedBy { it.sortKey }
        // one row per visit (date); a day's cash + online are summed into Paid.
        val byDate = LinkedHashMap<String, MutableList<TimelineEntry>>()
        for (e in payEntries) byDate.getOrPut(e.date) { mutableListOf() }.add(e)
        // 🔴 V217 self-audit fix (31.07.2026, দ্বিতীয় দফা অডিট): এই ফাইলটা
        // ReportCardActivity.kt-এর হুবহু আলাদা কপি (Print/PDF-এর জন্য), তাই
        // ওখানকার একই Refund-বাগ এখানেও ছিল — `paymentAmount` (unsigned)
        // দিয়ে যোগ হত, approved refund বিয়োগ হত না। ফলে **স্ক্রিন আর ছাপা
        // কাগজে ভিন্ন PAID/DUE** দেখাতে পারত (স্ক্রিন ঠিক, ছাপা ভুল) —
        // এটা রোগীর হাতে যাওয়া কাগজ বলে বিশেষ গুরুত্বপূর্ণ। এখন `paidEffect`
        // (§B216) ব্যবহার — ঠিক ReportCardActivity.kt-এর নিয়মে।
        val paidTotal = payEntries.sumOf { it.paidEffect }
        val dueTotal = if (bill > 0.0) (bill - paidTotal).coerceAtLeast(0.0) else 0.0

        val rows = StringBuilder()
        var run = 0.0
        var idx = 0
        for ((visitDate, dayList) in byDate) {
            idx++
            val paidThisDay = dayList.sumOf { it.paidEffect }
            run += paidThisDay
            val due = if (bill > 0.0) (bill - run).coerceAtLeast(0.0) else 0.0
            // 🚨 TK-REPORTED, LIVE (27.07.2026, SADDAM): the PRINTED Report Card
            // had the same fault as the screen -- worse, it printed the raw note,
            // so the app's own payment text and any audit line went onto the paper
            // the patient keeps. Both now read the ONE value the Timeline marks as
            // genuinely typed by a person, so screen and print always agree.
            // 🔒 TK-এর স্থায়ী নিয়ম (29.07.2026, খাতার সারি B74): ছাপা কাগজে
            // কখনো বাংলা যাবে না। `PrintTextEnglish.forPrint()` শুধু জানা
            // দ্রুত-চিপগুলোর বাংলা লেখা ইংরেজি করে; স্টাফের নিজের হাতে লেখা
            // কথা হুবহু থাকে। ⛔ ডেটাবেসে কিছু বদলায় না, পর্দাও বদলায় না —
            // Chamber Register-এর ছাপাও ঠিক এই একই ফাংশন ডাকে, তাই দুই
            // কাগজে কখনো দুই রকম হবে না।
            val progress = dayList.map { it.typedRemark }.filter { it.isNotBlank() }.distinct()
                .joinToString(" · ") { com.tkbiswas.pilesclinic.print.PrintTextEnglish.forPrint(it) }
            // 🔒 V235: table থেকে DUE column বাদ (উপরের লাল DUE Summary Box বহাল)।
            // `due` হিসাব বহাল আছে যাতে ভবিষ্যতে দরকার হলে বদল সহজ হয়, কিন্তু
            // এখন আর cell-এ যায় না।
            rows.append(
                "<tr><td class='v'>${ordinal(idx).uppercase()}</td><td>${esc(formatDisplayDate(visitDate))}</td>" +
                "<td class='pr'>${esc(progress)}</td><td class='pd'>${money(paidThisDay)}</td></tr>"
            )
        }
        // pad up to 20 rows like the paper (৪ column)
        var pad = idx
        while (pad < 20) { pad++; rows.append("<tr><td class='v'>${ordinal(pad).uppercase()}</td><td></td><td class='pr'></td><td></td></tr>") }

        val watermark = if (logoB64.isNotBlank())
            "<img class='wm' src='data:image/jpeg;base64,$logoB64'/>" else ""

        return """
<!DOCTYPE html><html><head><meta charset="utf-8"><style>
*{box-sizing:border-box;margin:0;padding:0;text-transform:uppercase;font-family:sans-serif}
body{padding:10px;position:relative}
.wm{position:fixed;top:55%;left:50%;width:55%;transform:translate(-50%,-50%);opacity:.07;z-index:-1}
.clinic{text-align:center;color:#fff;background:linear-gradient(90deg,#0B2B59,#0e7c7b);padding:7px;border-radius:6px 6px 0 0}
.cn{font-size:15px;font-weight:800}.ca{font-size:9px}
/* 🔒 V235 (TK verified + demo-approved 01.08.2026): Patient Details compact +
   photo big SQUARE (was গোল border-radius:50%)। design/রঙ অপরিবর্তিত। */
.pd2{display:flex;border:1.5px solid #0B2B59;border-top:none;padding:6px 9px;align-items:center}
.photo{width:74px;height:74px;border-radius:4px;border:2px solid #0e7c7b;object-fit:cover;margin-right:22px;flex:none}
.col{flex:1;font-size:9.5px;line-height:1.4}.col b{color:#0e7c7b}
.pname{font-size:12.5px;font-weight:800;color:#0B2B59}
/* Summary box (BILL/PAID/লাল DUE) — অপরিবর্তিত; নিচের table-এর সাথে gap কমানো */
.totals{display:flex;gap:14px;margin:7px 0 4px}
.tb{flex:1;border:1.5px solid;border-radius:8px;padding:6px;text-align:center;font-weight:800}
.tb .l{font-size:9px}.tb .n{font-size:14px}
.bill{border-color:#3f6fb0;color:#1c3d6e;background:#EEF3FB}
.paid{border-color:#16a36d;color:#0c7a45;background:#E9F8F0}
.due{border-color:#e5484d;color:#b02525;background:#FDECEC}
.rtitle{text-align:center;font-weight:800;margin:2px 0 4px}
table{width:100%;border-collapse:collapse;font-size:11px;table-layout:fixed}
th,td{border:1px solid #0B2B59;height:24px;text-align:center;padding:1px 4px;overflow:hidden}
/* 🔴🔒 V686 (২৫.০৮.২০২৬, TK-নির্দেশ — কাগজটা আরও প্রফেশনাল দেখাতে হবে) — আসল কারণ
   (ছবি+PDF মিলিয়ে ধরা): VISIT ("VISI" কাটা যাচ্ছিল) ও DATE (শেষ অঙ্ক
   কাটা, "28.07.202") কলাম দুটো বড়-হাতের-অক্ষর+bold লেখার তুলনায় অনেক
   সরু ছিল, table-layout:fixed + overflow:hidden থাকায় লেখা নীরবে কেটে
   যেত। এখন চওড়া করা হলো, PROGRESS কলাম একই অনুপাতে কমানো হয়েছে (মোট
   ঠিক ১০০%)। ⛔ রং/বর্ডার/ফন্ট-সাইজ কিছু বদলায়নি, শুধু প্রস্থ+wrap। */
th:nth-child(1),td:nth-child(1){width:10%}
th:nth-child(2),td:nth-child(2){width:18%}
th:nth-child(3),td:nth-child(3){width:57%}
th:nth-child(4),td:nth-child(4){width:15%}
th{background:#0e7c7b;color:#fff;white-space:nowrap;font-size:10.5px}
td.v,td:nth-child(2){white-space:nowrap}
/* 🔒 V235 (TK, Report Card—Single A4): Treatment Progress লেখা আর কখনো কাটা/লুকানো
   হবে না — আগের ২-লাইন clamp (line-clamp:2 · overflow:hidden) সরানো হলো। এখন
   লম্বা note প্রয়োজনীয় সংখ্যক লাইনে পুরো দেখায় (word-break সহ), cell/row নিজে
   থেকে বড় হয়। single A4 নিশ্চিত হয় নিচের buildPdfAndPreview()-এর fit-to-one-page
   scaling দিয়ে (পুরো পাতাটা এক A4-তে আঁটে, দরকারে font সামান্য ছোট হয় — কোনো
   তথ্য বাদ যায় না)। ⛔ কোনো ellipsis/`...`/overflow:hidden নেই। */
td.pr{text-align:left;padding-left:6px;white-space:normal;overflow:visible;vertical-align:top;height:auto;line-height:1.2;word-break:break-word}
td.v{font-weight:700;color:#0e7c7b;background:#F2FAF8}
td.pd{color:#0c8a4e;font-weight:700}
</style></head><body>
$watermark
<div class="clinic"><div class="cn">${esc(branch.clinicName)}</div><div class="ca">${esc(branch.addressLine)} · ${esc(branch.phoneLine)} · Helpline: ${esc(com.tkbiswas.pilesclinic.print.BranchInfo.HELPLINE)}</div></div>
<div class="pd2">
  ${if (data.photo.isNotBlank()) "<img class='photo' src='${esc(data.photo)}'/>" else "<div class='photo'></div>"}
  <div class="col"><div class="pname">${esc(data.name)}</div><div><b>AGE:</b> ${esc(age)}${if (sex.isNotBlank()) "&nbsp;&nbsp;${esc(sex)}" else ""}</div><div><b>ID:</b> ${esc(data.patientId)}</div><div><b>MOB:</b> +91${esc(data.mobile)}</div></div>
  <div class="col"><div><b>DATE:</b> ${esc(formatDisplayDate(today()))}</div><div><b>DISEASE:</b> ${esc(data.disease)}</div><div><b>ADDRESS:</b> ${addrTwoLines(address)}</div></div>
</div>
<div class="totals">
  <div class="tb bill"><div class="l">TOTAL BILL</div><div class="n">${money(bill)}</div></div>
  <div class="tb paid"><div class="l">PAID</div><div class="n">${money(paidTotal)}</div></div>
  <div class="tb due"><div class="l">DUE</div><div class="n">${money(dueTotal)}</div></div>
</div>
<div class="rtitle">PATIENT PROGRESS REPORT OF MOVEMENT HISTORY</div>
<table>
  <tr><th>VISIT</th><th>DATE</th><th>TREATMENT PROGRESS</th><th>PAID</th></tr>
  $rows
</table>
</body></html>
""".trimIndent()
    }

    // TK-REQUESTED (2026-07-26): "যা যা প্রিন্ট হয় সব যেন WhatsApp-এ শেয়ার
    // করা যায়". Report Card was the ONLY printable document in the whole app
    // that went straight to the printer without producing a file, so it had
    // no Share. It now builds the very same HTML into an A4 PDF and opens
    // the same SAVE / SHARE / PRINT preview screen every other document
    // already uses. The HTML, the layout, the watermark, the columns . every
    // visible thing . is completely untouched.
    // SAFETY: if anything at all goes wrong while building the PDF (odd page
    // size, empty measure, out of memory), it silently falls back to exactly
    // the old direct-print behaviour, so printing can never stop working.
    private const val PAGE_W_PT = 595   // A4 width in points
    private const val PAGE_H_PT = 842   // A4 height in points
    private const val RENDER_W_PX = 1240 // A4 width at ~150 dpi (crisp text)
    private const val RENDER_H_PX = 1754 // A4 height at the same scale

    private fun startPrint(context: Context, html: String) {
        // 🚨 TK-REPORTED, LIVE (27.07.2026): "Report Card-এ Print চাপলে প্রিন্ট
        // আউটের কোনো অপশনই আসে না" -- the toast said "প্রিন্ট তৈরি হচ্ছে…" and then
        // nothing happened at all. TK's standing global rule is that EVERYTHING
        // printable must also be shareable on WhatsApp, so a silent dead end here
        // breaks that rule.
        //
        // WHY IT DIED SILENTLY: this built the page in a WebView that was never
        // attached to the screen. A detached WebView is not laid out by the system,
        // so on many phones it either never finishes loading (onPageFinished never
        // fires -> nothing runs, nothing is shown) or measures/draws empty. And if
        // that happened, the old code had nowhere left to go: the fallback
        // (createPrintDocumentAdapter on that same dead view) fails just as
        // quietly. Result: a tap that does nothing, forever.
        //
        // THE FIX, in three parts -- none of them touches the page itself, so the
        // printed Report Card looks exactly as TK approved:
        //  1. the WebView is added to the screen as a 1x1 invisible view, so
        //     Android lays it out properly and it renders reliably;
        //  2. a watchdog: if the page has not finished within 9 seconds (dead
        //     WebView, huge logo image, very slow phone), we stop waiting and take
        //     the fallback path instead of hanging forever;
        //  3. if even the fallback cannot run, the staff is TOLD, instead of being
        //     left staring at a screen that did nothing.
        // 🚨🚨 TK-REPORTED, LIVE (29.07.2026 সকাল ৯.২৪ — TK-এর তিনটে ছবি):
        //   Report Card → Print → অনেকক্ষণ ঘোরে → "Could not prepare the Report
        //   Card" → তারপর প্রিভিউ খুললেও **"Page 1 of 3" আর পাতা পুরো ফাঁকা**।
        //   খাতার সারি B71।
        //
        // 🔎 **আসল কারণ (কোড দেখে, আন্দাজে নয়):** ২৭.০৭-এ ঠিক করতে গিয়ে WebView-টা
        //   পর্দায় বসানো হয়েছিল ঠিকই, কিন্তু **মাপ দেওয়া হয়েছিল ১×১ পিক্সেল**।
        //   WebView যত বড় মাপে বসে, ভিতরে ঠিক ততটুকুই আঁকে। তাই ভিতরের আঁকার
        //   জায়গা ছিল ১×১ — পরে হাতে করে বড় মাপে `measure/layout` করলেও **আঁকার
        //   মতো কিছুই ছিল না**, ফলে PDF-এর পাতাগুলো সাদা বেরোত। উচ্চতার হিসাব
        //   (`contentHeight`) ঠিকই আসত বলে "৩ পাতা" লেখা উঠত — শুধু ভিতরটা ফাঁকা।
        //
        // ✅ **স্থায়ী সমাধান:** WebView এখন **আসল ছাপার মাপে** (A4 চওড়া/উঁচু)
        //   বসে, আর **পর্দার বাইরে সরিয়ে** রাখা হয় — তাই কেউ দেখতে পায় না,
        //   অথচ ভিতরে পুরো পাতাটা সত্যিই আঁকা হয়।
        //   সঙ্গে সফটওয়্যার লেয়ার **পাতা লোড করার আগেই** বসানো হয় (হার্ডওয়্যার
        //   লেয়ারে আঁকলে অনেক ফোনে কাগজ ফাঁকা আসে)।
        //
        // ⛔ **স্থায়ী নিয়ম — কখনো ভাঙা যাবে না:** ছাপার জন্য বানানো WebView
        //   **কখনো ১×১ বা লুকানো মাপে বসানো যাবে না**; সবসময় আসল ছাপার মাপে,
        //   পর্দার বাইরে। (এই ভুলেই ২৭.০৭ ও ২৯.০৭ — দু'বার সমস্যা হয়েছে।)
        val activity = context as? android.app.Activity
        val webView = WebView(context)
        // সফটওয়্যার লেয়ার আগে — পরে নয়।
        try { webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null) } catch (_: Throwable) { }
        val root = activity?.findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root != null) {
            webView.alpha = 0f
            webView.translationX = -(RENDER_W_PX * 2).toFloat()
            try {
                root.addView(
                    webView,
                    android.view.ViewGroup.LayoutParams(RENDER_W_PX, RENDER_H_PX)
                )
            } catch (_: Throwable) { }
        }
        var finished = false
        fun detach() {
            try { (webView.parent as? android.view.ViewGroup)?.removeView(webView) } catch (_: Throwable) { }
            liveWebViews.remove(webView)
        }
        fun finishOnce(fromWatchdog: Boolean) {
            if (finished) return
            finished = true
            // Guard against a blank document: if the page never really loaded,
            // the WebView has no content height, and building a PDF from it would
            // hand the staff an empty Report Card to print. Better to say so.
            val hasContent = try { webView.contentHeight > 0 } catch (_: Throwable) { false }
            val ok = if (!hasContent) false else
                try { buildPdfAndPreview(context, webView) } catch (_: Throwable) { false }
            if (!ok) {
                if (fromWatchdog || !hasContent) {
                    android.widget.Toast.makeText(
                        context,
                        "Could not prepare the Report Card — please try once more",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    startPrintDirect(context, webView)
                }
            }
            detach()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // small delay so the logo/photo images are laid out before measuring
                view.postDelayed({ finishOnce(false) }, 400L)
            }
        }
        liveWebViews.add(webView)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        webView.postDelayed({ finishOnce(true) }, 9000L)
    }

    private fun buildPdfAndPreview(context: Context, view: WebView): Boolean {
        // Software layer: a hardware-accelerated view draws nothing onto a
        // PDF canvas. This is only for this off-screen copy.
        try { view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null) } catch (_: Throwable) { }
        val wSpec = android.view.View.MeasureSpec.makeMeasureSpec(RENDER_W_PX, android.view.View.MeasureSpec.EXACTLY)
        val hSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        val contentH = view.measuredHeight
        if (contentH <= 0) return false
        view.layout(0, 0, RENDER_W_PX, contentH)
        // 🔒 খাতার সারি B71: **ফাঁকা কাগজ যেন কখনো বেরোতে না পারে।**
        // পাতা বানানোর *আগেই* ছোট করে এঁকে দেখা হয় সত্যিই কিছু আঁকা হচ্ছে কিনা।
        // না হলে PDF বানানোই হয় না — তখন Android-এর নিজের ছাপার পর্দা খোলে,
        // অর্থাৎ স্টাফ ফাঁকা কাগজ হাতে পান না।
        if (!drewSomething(view)) return false
        // 🔒 V235 (TK, Report Card—Single A4): Report Card সবসময় একটিমাত্র A4 পাতা।
        // আগে content-উচ্চতা RENDER_H_PX (1754px) ছাড়ালে Math.ceil ২য় পৃষ্ঠা বানাত,
        // তাই Treatment Progress-এ ২-লাইন clamp রাখতে হত (লেখা কাটা যেত)। clamp এখন
        // সরানো হয়েছে; বদলে পুরো rendered content-কে এক A4-তে fit করাতে uniform
        // scale বসানো হলো:
        //   • ছোট/মাঝারি কার্ড (উচ্চতা ≤ RENDER_H_PX) → widthScale, অর্থাৎ হুবহু আগের
        //     মতোই — কিছুই ছোট হয় না।
        //   • লম্বা কার্ড (উচ্চতা > RENDER_H_PX) → heightScale (< widthScale), font
        //     সামান্য ছোট হয়ে সব Treatment Progress লাইনসহ পুরো কার্ড এক পাতায় আঁটে।
        // ⛔ কোনো তথ্য/লাইন কাটা যায় না, কোনো `...` নেই, কোনো ২য় পৃষ্ঠা নয়।
        val widthScale = PAGE_W_PT.toFloat() / RENDER_W_PX.toFloat()
        val heightScale = PAGE_H_PT.toFloat() / contentH.toFloat()
        val fitScale = Math.min(widthScale, heightScale)
        // লম্বা কার্ডে width কম হলে পাতার মাঝে বসাও (দুই পাশে সমান সাদা মার্জিন)।
        val offsetX = ((PAGE_W_PT.toFloat() - RENDER_W_PX.toFloat() * fitScale) / 2f).coerceAtLeast(0f)
        val doc = android.graphics.pdf.PdfDocument()
        try {
            run {
                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, 1).create()
                val page = doc.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.save()
                canvas.translate(offsetX, 0f)
                canvas.scale(fitScale, fitScale)
                view.draw(canvas)
                canvas.restore()
                doc.finishPage(page)
            }
            val dir = java.io.File(context.cacheDir, "pdfs").apply { mkdirs() }
            val out = java.io.File(dir, "report_card_${System.currentTimeMillis()}.pdf")
            java.io.FileOutputStream(out).use { stream -> doc.writeTo(stream) }
            if (!out.exists() || out.length() <= 0L) return false
            com.tkbiswas.pilesclinic.print.PrintDataHolder.prebuiltFile = out
            com.tkbiswas.pilesclinic.print.PrintDataHolder.prebuiltTitle = "Report Card"
            val intent = android.content.Intent(context, com.tkbiswas.pilesclinic.print.PrintPreviewActivity::class.java)
            if (context !is android.app.Activity) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        } finally {
            doc.close()
        }
    }

    /**
     * 🔒 খাতার সারি B71 (TK, 29.07.2026): কাগজ ফাঁকা কিনা তা **আগেই** ধরে ফেলার
     * ছোট পরীক্ষা — পাতাটা ২০০ পিক্সেল চওড়া করে এঁকে দেখা হয়, একটাও সাদা-ছাড়া
     * বিন্দু আছে কিনা। একটাও না থাকলে বুঝতে হবে কিছুই আঁকা হয়নি।
     * ⛔ খুব হালকা কাজ (ছোট ছবি), তাই ছাপা ধীর হয় না।
     */
    private fun drewSomething(view: WebView): Boolean {
        return try {
            val w = 200
            val h = 280
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(android.graphics.Color.WHITE)
            val s = w.toFloat() / RENDER_W_PX.toFloat()
            c.scale(s, s)
            view.draw(c)
            var painted = 0
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    if (bmp.getPixel(x, y) != android.graphics.Color.WHITE) painted++
                    if (painted > 30) break
                    x += 2
                }
                if (painted > 30) break
                y += 2
            }
            bmp.recycle()
            painted > 30
        } catch (_: Throwable) {
            // পরীক্ষাটাই করা না গেলে আগের মতোই চলুক — কাজ আটকানো যাবে না।
            true
        }
    }

    /** The original behaviour, kept untouched as the safety fallback. */
    private fun startPrintDirect(context: Context, view: WebView) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter("ReportCard")
            printManager.print(
                "Report Card",
                adapter,
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        } catch (_: Throwable) {
            android.widget.Toast.makeText(context, "Printing not available on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
