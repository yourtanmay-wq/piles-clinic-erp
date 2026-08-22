package com.tkbiswas.pilesclinic.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * TK-REQUESTED ADDITION (2026-07-19): a real A4 PDF for Chamber Date's
 * "Close Chamber" — one row per Arrived patient that day, matching TK's
 * own paper attendance register (SL | Patient Details incl. Mobile+ID |
 * New/Old | Fees | Cash | Online). This is a completely SEPARATE file from
 * ClinicPdfBuilder.kt (which stays OWNER LOCKED and untouched) — nothing
 * here can affect Prescription/Medicine Slip/Blood Test/Diet Chart/
 * Registration/Payment Receipt/Doctor Visit printing.
 *
 * The header (logo, clinic name, address) deliberately reuses the exact
 * same branch-specific values ClinicPdfBuilder already uses (BranchInfo:
 * Kishanganj branch -> "TK BISWAS PILES CLINIC", every other branch ->
 * "MAA AYURVED PILES CLINIC").
 *
 * TK-REQUESTED SIZING (2026-07-19): rows are compact, single-line, so
 * exactly ROWS_PER_PAGE (30) fit on one A4 page with at least 3 inches
 * (216pt) of blank space left at the bottom of that page. If a day has
 * MORE than 30 Arrived patients, the extra ones spill onto additional A4
 * pages, each repeating the same header — this only affects a rare very
 * busy day, and every name still ends up on paper, just on page 2 instead
 * of being squeezed onto page 1.
 */
class ChamberRegisterPdfBuilder(private val context: Context) {

    data class RegisterRow(
        val sl: Int,
        val name: String,
        val mobile: String,
        // TK-REQUESTED ADDITION (2026-07-19): Patient ID, its own column now
        // (not stacked under Name/Mobile) so a single-line compact row still
        // fits everything TK asked for.
        val patientId: String,
        // TK-CORRECTED (2026-07-19): "NEW" or "OLD" (matching the paper
        // register's own NEW/OLD column — a first-time-today patient vs a
        // returning one) -- NOT an "Arrived" status label (removed
        // entirely; every row here is already Arrived by definition).
        val newOrOld: String,
        val fees: Double,
        val cash: Double,
        val online: Double,
        // TK-REQUESTED (2026-07-21): today's Treatment Progress (what treatment
        // was given) -- its own column on the printed register.
        val treatment: String = "",
        // TK-REQUESTED (2026-07-21): the patient's visit ordinal ("6th Visit").
        // Shown in the VISIT column; falls back to newOrOld if not computed.
        val visitLabel: String = "",
        // TK-REQUESTED (2026-07-22): the Registration/Doctor-Visit Fee row must
        // NOT say "1st Visit" -- that label conflates "how many times has this
        // patient physically come to chamber" (a real running count, correct
        // for every OTHER row) with "why was this ₹ collected". For the fee
        // row, show the payment MODE (CASH/UPI) it was collected in instead.
        val feesMode: String = ""
    )

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 18f
        private const val GREEN = "#0A5428"
        private const val GOLD = "#C99A19"

        // TK-REQUESTED (2026-07-19): exactly 30 patients per A4 page, with
        // at least 3 inches (216pt) of blank space left at the bottom.
        // Everything below is sized backwards from that one requirement --
        // see the comment block above the class for the full math.
        private const val ROWS_PER_PAGE = 21
        private const val ROW_HEIGHT = 16.5f
        private const val HEADER_ROW_HEIGHT = 16f
        private const val TABLE_TOP = 52f
        // TK-REQUESTED (2026-07-21): rows STRETCH to fill down to a consistent
        // bottom blank; height = space / patient-count, clamped.
        private const val ROWS_BOTTOM_LIMIT = 652f
        private const val MIN_ROW_HEIGHT = 27f
        private const val MAX_ROW_HEIGHT = 60f
    }

    /** Builds the register PDF and returns the saved file. More than
     *  ROWS_PER_PAGE (30) patients spill onto additional A4 pages
     *  automatically, each repeating the same header, so a printed stack
     *  is self-contained. */
    /** @param rmpCommission 🔴 V426 (TK-নির্দেশ): ওই দিনের RMP কমিশন।
     *  @param rmpPaidToday  🔴 V427: আজ RMP-দের হাতে দেওয়া টাকা (শুধু দেখানোর জন্য)।
     *  ⛔ দুটোই ঐচ্ছিক — ০ দিলে (বা না দিলে) কাগজ **হুবহু আগের মতোই** ছাপে,
     *     তাই পুরনো কোনো ডাক বদলাতে হয়নি। */
    fun build(branch: BranchInfo, dateLabel: String, dayLabel: String, rows: List<RegisterRow>, outputFile: File, rmpCommission: Double = 0.0, rmpPaidToday: Double = 0.0): File {
        val doc = PdfDocument()
        val logo = loadAssetBitmap(branch.logoAssetPath)
        val pages = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(ROWS_PER_PAGE)
        var pageNum = 0
        val usableTop = TABLE_TOP + HEADER_ROW_HEIGHT
        for (pageRows in pages) {
            pageNum++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, branch, logo, dateLabel, dayLabel)
            // TK-REQUESTED (2026-07-21): only as many rows as patients, each
            // rowH tall so the sheet fills to a consistent bottom blank
            // (taller rows for a short day, compact for a full page).
            val count = pageRows.size.coerceAtLeast(1)
            val rowH = ((ROWS_BOTTOM_LIMIT - usableTop) / count).coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT)
            drawTable(canvas, pageRows, rowH)
            if (pageNum == pages.size) {
                drawTotals(canvas, rows, usableTop + pageRows.size * rowH + 12f, rmpCommission, rmpPaidToday)
            }
            doc.finishPage(page)
        }
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()
        return outputFile
    }

    private fun drawHeader(canvas: Canvas, branch: BranchInfo, logo: Bitmap?, dateLabel: String, dayLabel: String) {
        val green = Color.parseColor(GREEN)
        val gold = Color.parseColor(GOLD)

        // TK-CORRECTED (2026-07-22): logo's bottom edge (was 52f) sat BELOW
        // the header's green divider line (y=46f, drawn further down), so the
        // logo visually overlapped/merged into that line. Shrunk slightly
        // (kept perfectly square, so the artwork isn't stretched) so its
        // bottom now sits clearly above the line with a small gap.
        logo?.let {
            val rect = RectF(MARGIN, 8f, MARGIN + 36f, 44f)
            canvas.drawBitmap(it, null, rect, Paint(Paint.ANTI_ALIAS_FLAG))
        }

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = green; textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            typeface = try { Typeface.create("sans-serif-black", Typeface.BOLD) } catch (_: Exception) { Typeface.DEFAULT_BOLD }
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18251D"); textSize = 8f; textAlign = Paint.Align.CENTER
        }
        val logoRight = MARGIN + 36f
        val headerRight = PAGE_WIDTH - MARGIN
        val centerX = (logoRight + headerRight) / 2f
        val nameMaxWidth = headerRight - logoRight - 16f
        canvas.drawText(fitText(branch.clinicName, title, nameMaxWidth), centerX, 21f, title)
        canvas.drawLine(logoRight + 20f, 27f, headerRight - 20f, 27f, Paint().apply { color = gold; strokeWidth = 1.2f })

        // TK-CORRECTED (2026-07-19): address line is CENTERED, matching the
        // clinic name above it (same centerX) -- it never shifts left for
        // anything else. Date + Day sit small, on their own, in the
        // top-right corner of the page, well clear of this centered block.
        val oneLine = "${branch.addressLine}   |   Mob: ${branch.phoneLine}"
        canvas.drawText(fitText(oneLine, detail, headerRight - logoRight - 20f), centerX, 38f, detail)

        val dateDayText = "$dateLabel  ($dayLabel)"
        val dateDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = green; textSize = 7.5f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateDayText, headerRight, 9f, dateDayPaint)

        canvas.drawLine(MARGIN, 46f, PAGE_WIDTH - MARGIN, 46f, Paint().apply { color = green; strokeWidth = 1.3f })
    }

    /** TK-REQUESTED (2026-07-19): compact SINGLE-LINE rows — Name, Mobile,
     *  and Patient ID each get their own (narrower) column instead of being
     *  stacked across 2-3 lines, which is what makes fitting 30 rows in
     *  one page (with 3 inches still free) possible at all. */
    private fun drawTable(canvas: Canvas, rows: List<RegisterRow>, rowHeight: Float) {
        val colSl = MARGIN; val colSlW = 20f
        val colPat = colSl + colSlW; val colPatW = 148f
        val colTreat = colPat + colPatW; val colTreatW = 150f
        val colVisit = colTreat + colTreatW; val colVisitW = 74f
        val colCash = colVisit + colVisitW; val colCashW = 70f
        val colOnline = colCash + colCashW; val colOnlineW = 68f
        val tableRight = colOnline + colOnlineW

        val headerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F8A6E") }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 6.8f; isFakeBoldText = true }
        canvas.drawRect(RectF(colSl, TABLE_TOP, tableRight, TABLE_TOP + HEADER_ROW_HEIGHT), headerBg)
        canvas.drawText("SL", colSl + 5f, TABLE_TOP + 11f, headerText)
        canvas.drawText("PATIENT", colPat + 4f, TABLE_TOP + 11f, headerText)
        canvas.drawText("TREATMENT PROGRESS", colTreat + 4f, TABLE_TOP + 11f, headerText)
        canvas.drawText("VISIT", colVisit + 5f, TABLE_TOP + 11f, headerText)
        canvas.drawText("CASH", colCash + colCashW - 5f - headerText.measureText("CASH"), TABLE_TOP + 11f, headerText)
        canvas.drawText("ONLINE", colOnline + colOnlineW - 5f - headerText.measureText("ONLINE"), TABLE_TOP + 11f, headerText)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B9C2CC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10223A"); textSize = 8f; isFakeBoldText = true }
        val mobilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1067D8"); textSize = 7.2f }
        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B6B81"); textSize = 6.6f }
        val slPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5B6B81"); textSize = 7f }
        val newOldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33404F"); textSize = 7f; isFakeBoldText = true }
        val moneyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33404F"); textSize = 7.5f }
        val treatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A3D00"); textSize = 6.6f }

        val newFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAF7EE") }
        val oldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EDEFF2") }

        var y = TABLE_TOP + HEADER_ROW_HEIGHT
        // TK-REQUESTED (2026-07-21): only actual patients; Name / Mobile / ID
        // STACKED vertically in one wide PATIENT column for a clean A4 print.
        for (idx in rows.indices) {
            val rowTop = y
            val rowBottom = y + rowHeight
            val midY = rowTop + rowHeight / 2f + 2.5f
            val r = rows[idx]
            val fill = if (r.newOrOld == "NEW") newFill else oldFill
            canvas.drawRect(RectF(colSl, rowTop, tableRight, rowBottom), fill)
            canvas.drawRect(RectF(colSl, rowTop, tableRight, rowBottom), gridPaint)
            canvas.drawLine(colPat, rowTop, colPat, rowBottom, gridPaint)
            canvas.drawLine(colTreat, rowTop, colTreat, rowBottom, gridPaint)
            canvas.drawLine(colVisit, rowTop, colVisit, rowBottom, gridPaint)
            canvas.drawLine(colCash, rowTop, colCash, rowBottom, gridPaint)
            canvas.drawLine(colOnline, rowTop, colOnline, rowBottom, gridPaint)

            canvas.drawText(r.sl.toString(), colSl + 5f, midY, slPaint)
            val blockH = 24f
            val startY = rowTop + ((rowHeight - blockH) / 2f).coerceAtLeast(2f) + 8f
            canvas.drawText(fitText(r.name, namePaint, colPatW - 8f), colPat + 4f, startY, namePaint)
            canvas.drawText(fitText("Mob: " + r.mobile, mobilePaint, colPatW - 8f), colPat + 4f, startY + 9f, mobilePaint)
            canvas.drawText(fitText("ID: " + r.patientId.ifBlank { "-" }, idPaint, colPatW - 8f), colPat + 4f, startY + 17f, idPaint)
            // TK-REQUESTED (2026-07-21): today's Treatment Progress, word-wrapped
            // to up to 3 lines, vertically centred in its (wide) column.
            val treatLines = wrapText(r.treatment.ifBlank { "-" }, treatPaint, colTreatW - 8f, 3)
            val tStartY = rowTop + ((rowHeight - treatLines.size * 8.5f) / 2f).coerceAtLeast(2f) + 7f
            treatLines.forEachIndexed { li, ln -> canvas.drawText(ln, colTreat + 4f, tStartY + li * 8.5f, treatPaint) }
            // TK-REQUESTED (2026-07-21): VISIT column = visit label on top, that
            // visit's fee summed right below it. (Label is NEW/OLD for now, the
            // fallback TK allowed; exact "Nth Visit" ordinal is the next step.)
            // TK-RULE (2026-07-21): Fees are charged ONLY on the 1st visit
            // (registration). So the fee amount shows under the visit label
            // ONLY when there is a fee (> 0); 2nd visit onward shows just the
            // visit ordinal, no amount (treatment cost lives in Cash/Online).
            // TK-CORRECTED (2026-07-22): the fee row is NOT a "visit count" --
            // it is money collected for registration/doctor-visit fee, so it
            // must show the payment MODE (CASH/UPI) instead of "1st Visit".
            // The visit-ordinal count is only meaningful for rows WITHOUT a
            // fee (2nd Visit, 3rd Visit... untouched, exactly as before).
            val visLabel = fitText(r.visitLabel.ifBlank { r.newOrOld }, newOldPaint, colVisitW - 6f)
            if (r.fees > 0.0) {
                val modeLabel = fitText(r.feesMode.ifBlank { "—" }, newOldPaint, colVisitW - 6f)
                canvas.drawText(modeLabel, colVisit + colVisitW / 2f - newOldPaint.measureText(modeLabel) / 2f, rowTop + rowHeight / 2f - 1f, newOldPaint)
                drawMoney(canvas, r.fees, colVisit, colVisitW, rowTop + rowHeight / 2f + 9f, moneyPaint)
            } else {
                canvas.drawText(visLabel, colVisit + colVisitW / 2f - newOldPaint.measureText(visLabel) / 2f, midY, newOldPaint)
            }
            drawMoney(canvas, r.cash, colCash, colCashW, midY, moneyPaint)
            drawMoney(canvas, r.online, colOnline, colOnlineW, midY, moneyPaint)
            y = rowBottom
        }
    }

    private fun drawMoney(canvas: Canvas, amount: Double, colLeft: Float, colWidth: Float, baselineY: Float, paint: Paint) {
        val text = if (amount > 0.0) "₹" + "%,.0f".format(amount) else "—"
        canvas.drawText(text, colLeft + colWidth - 6f - paint.measureText(text), baselineY, paint)
    }

    private fun drawTotals(canvas: Canvas, rows: List<RegisterRow>, y: Float, rmpCommission: Double = 0.0, rmpPaidToday: Double = 0.0) {
        val colVisit = MARGIN + 20f + 148f + 150f
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(GREEN); textSize = 8.5f; isFakeBoldText = true }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(GREEN); textSize = 8.5f; isFakeBoldText = true }
        // TK-CORRECTED (2026-07-22): patient count was already visible from the
        // SL column (1, 2, 3...) -- "(N patients)" was redundant, removed.
        canvas.drawText("TOTAL", MARGIN + 20f, y, label)
        val totalFees = rows.sumOf { it.fees }
        val totalCash = rows.sumOf { it.cash }
        val totalOnline = rows.sumOf { it.online }
        drawMoney(canvas, totalFees, colVisit, 74f, y, value)
        drawMoney(canvas, totalCash, colVisit + 74f, 70f, y, value)
        drawMoney(canvas, totalOnline, colVisit + 74f + 70f, 68f, y, value)
        // TK-CORRECTED (2026-07-22): the separate "TODAY'S COLLECTION —
        // Fees/Cash/Online" line duplicated exactly what the TOTAL row above
        // already shows under those same three columns. Removed; the TOTAL
        // row's Fees/Cash/Online numbers are now the only place this lives.

        // 🔴🔒 V426 (TK-নির্দেশ ১৭.০৮.২০২৬) — *"প্রিন্ট আউট হয়ে যাওয়ার পরে একদম
        //    নিচে থাকবে · সব গুলি একলাইনে থাকতে হবে"* ⇒ কাগজের একদম নিচে
        //    **একটাই লাইনে** পুরো হিসাব। TOTAL = Fees + Cash + Online
        //    (TK-অনুমোদিত)। ⛔ উপরের TOTAL সারি ও কলামের অঙ্ক একটুও বদলায়নি —
        //    এটা শুধু নিচে একটা বাড়তি সারাংশ লাইন।
        val grand = totalFees + totalCash + totalOnline
        fun rs(v: Double) = "₹" + "%,.0f".format(v)
        // 🔴 V426: RMP কমিশন থাকলে সেটাও একই লাইনে, আর TOTAL থেকে বাদ দিয়ে NET।
        val headPart = "Fees " + rs(totalFees) + "   ·   Cash " + rs(totalCash) +
            "   ·   Online " + rs(totalOnline) + "   ·   TOTAL " + rs(grand)
        val oneLine = if (rmpCommission > 0.0) {
            headPart + "   ·   RMP Commission − " + rs(rmpCommission) +
                "   ·   NET " + rs(grand - rmpCommission) + "/-"
        } else {
            headPart + "/-"
        }
        val sum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(GREEN); textSize = 9f; isFakeBoldText = true
        }
        // 🔴 V427: "আজ RMP-দের হাতে কত গেল" — একই লাইনের শেষে, ⛔ কোনো মোট থেকে
        //    বাদ যায় না (TK-নির্দেশ: শুধু জানার জন্য)। ০ হলে লেখাই হয় না।
        val fullLine = if (rmpPaidToday > 0.0) {
            oneLine + "   ·   Paid to RMP today " + rs(rmpPaidToday)
        } else {
            oneLine
        }
        canvas.drawText(fullLine, MARGIN + 20f, (PAGE_HEIGHT - 24).toFloat(), sum)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val t = text.ifBlank { "-" }
        val lines = ArrayList<String>()
        var cur = ""
        for (w in t.split(" ")) {
            val cand = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(cand) <= maxWidth) { cur = cand }
            else { if (cur.isNotEmpty()) lines.add(cur); cur = w }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && cur.isNotEmpty()) lines.add(cur)
        val out = lines.map { fitText(it, paint, maxWidth) }
        return if (out.isEmpty()) listOf("-") else out
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.length > 3 && paint.measureText("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }

    private fun loadAssetBitmap(assetPath: String): Bitmap? = try {
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }
}
