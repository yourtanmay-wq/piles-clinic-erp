package com.tkbiswas.pilesclinic.clinical

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.tkbiswas.pilesclinic.native.PremiumAlert

/**
 * 💰🔒 V971 (০২.০৯.২০২৬, TK-এর পাশ-করা ফটো-প্রুফ অনুযায়ী) —
 * **এস্টিমেট বানানোর পপ-আপ।**
 *
 * TK-এর লক করা নিয়ম (সবগুলোই তাঁর নিজের কথা):
 *  • রোগ বাছার সারিতে **শুধু রোগ** — Piles · Fistula · Fissure · Hydrocele।
 *    ওষুধ ও অন্যান্য আলাদা বোতাম থেকে আসে।
 *  • পাইলসে **চারটে গ্রেড**, ফিস্টুলায় **ইঞ্চি**।
 *  • Grade ও Position **চাপ দিলে তবেই খোলে**, এমনিতে গুটানো।
 *  • দর **নিজে থেকে বসে**, হাতে বদলালে বদলানোটাই থাকে।
 *  • লাইন **কাটা** যায় — কাটা লাইন Subtotal-এ গোনা হয় না।
 *
 * ⛔ কোনো নতুন টেবিল/কলাম/SQL নয় — পুরো হিসাব চেকআপের JSON-এই জমা।
 * ⛔ পুরনো কোনো পর্দা বা হিসাব ছোঁয়া হয়নি; ব্যর্থ হলেও চেকআপ আটকায় না।
 */
object EstimateDialog {

    private const val INK = "#101C2E"
    private const val MUT = "#8B98A9"
    private const val LINE = "#EEF2F7"
    private const val GREEN = "#0B4F2A"
    private const val RED = "#B42318"
    private const val NAVY = "#0F3D6B"

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun label(c: Context, text: String, size: Float = 9f, colour: String = MUT) =
        TextView(c).apply {
            this.text = text; textSize = size
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(colour))
            letterSpacing = 0.09f
        }

    private fun box(c: Context, fill: String, stroke: String, radius: Int) =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(dp(c, 1), Color.parseColor(stroke))
            cornerRadius = dp(c, radius).toFloat()
        }

    private fun numberField(c: Context, value: String, decimal: Boolean = true): EditText =
        EditText(c).apply {
            setText(value)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(INK))
            /* 🔒 খাতার সারি B411 — শুধু সংখ্যার ধরন দিলে কিছু ফোনে কীবোর্ডই
               খোলে না। প্রকল্পের প্রমাণিত পথ: TEXT + DigitsKeyListener। */
            inputType = InputType.TYPE_CLASS_TEXT
            keyListener = android.text.method.DigitsKeyListener.getInstance(
                if (decimal) "0123456789." else "0123456789")
            background = box(c, "#F8FBFE", "#D6E1EE", 9)
            setPadding(dp(c, 9), dp(c, 7), dp(c, 9), dp(c, 7))
        }

    /**
     * পপ-আপটা খোলে।
     * @param onDone নতুন হিসাব ও Net Payable ফেরত — চেকআপের ঘরে বসানোর জন্য।
     * @param onPaper "Print / Share" চাপলে — কাগজ বানানোর দায়িত্ব ডাকার জায়গার।
     */
    fun open(
        activity: Activity,
        current: EstimateModel.Sheet,
        onDone: (EstimateModel.Sheet) -> Unit,
        onPaper: (EstimateModel.Sheet) -> Unit
    ) {
        val sheet = EstimateModel.parse(current.toJson())
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 4))
        }
        val listBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val totalBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            background = box(activity, "#F7FAFD", "#E8EEF6", 12)
        }
        root.addView(listBox)

        // ── যোগ করার তিনটে বোতাম (TK-নির্দেশ: রোগ ও ওষুধ আলাদা) ──
        val addRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        }
        root.addView(addRow)
        root.addView(totalBox.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 12) }
        })

        lateinit var redraw: () -> Unit
        lateinit var paintTotals: () -> Unit

        fun addChip(text: String, onClick: () -> Unit) {
            val t = TextView(activity).apply {
                this.text = text
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#0B66D8"))
                background = box(activity, "#FFFFFF", "#B9CBE0", 12)
                setPadding(dp(activity, 6), dp(activity, 10), dp(activity, 6), dp(activity, 10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = dp(activity, 3); rightMargin = dp(activity, 3) }
                setOnClickListener { onClick() }
            }
            addRow.addView(t)
        }
        addChip("+ Treatment") { addTreatment(activity, sheet) { redraw() } }
        addChip("+ Medicine") { addFromGroup(activity, sheet, EstimatePrices.G_MEDICINE) { redraw() } }
        addChip("+ Other") { addFromGroup(activity, sheet, EstimatePrices.G_OTHER) { redraw() } }

        // 🔒 TK: *"যে কেউ বদলাতে পারবে"* — দরের তালিকার পর্দা এখান থেকেই।
        root.addView(TextView(activity).apply {
            text = "Price List"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#41506A"))
            background = box(activity, "#EEF2F7", "#E2E9F2", 12)
            setPadding(dp(activity, 6), dp(activity, 10), dp(activity, 6), dp(activity, 10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 8) }
            setOnClickListener {
                activity.startActivity(android.content.Intent(activity, PriceListActivity::class.java))
            }
        })

        redraw = {
            listBox.removeAllViews()
            if (sheet.lines.isEmpty()) {
                listBox.addView(TextView(activity).apply {
                    text = "No item added yet."
                    textSize = 13f
                    setTextColor(Color.parseColor(MUT))
                    setPadding(0, dp(activity, 10), 0, dp(activity, 10))
                })
            }
            for (line in sheet.lines.toList()) {
                listBox.addView(lineView(activity, line, sheet, { redraw() }, { paintTotals() }))
            }
            paintTotals()
        }

        paintTotals = {
            totalBox.removeAllViews()
            totalBox.addView(totalRow(activity, "Subtotal", EstimateModel.moneyShort(sheet.subtotal), INK))
            val discRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(activity, 6) }
            }
            discRow.addView(TextView(activity).apply {
                text = "Discount"; textSize = 13f
                setTextColor(Color.parseColor("#5B6B81"))
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val discField = numberField(activity, if (sheet.discount > 0) EstimateModel.moneyShort(sheet.discount) else "")
            discField.hint = "0"
            discField.gravity = Gravity.END
            discField.layoutParams = LinearLayout.LayoutParams(dp(activity, 110), LinearLayout.LayoutParams.WRAP_CONTENT)
            discField.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    sheet.discount = EstimateModel.num(s?.toString())
                    netLine(totalBox)?.text = EstimateModel.moneyShort(sheet.netPayable)
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            discRow.addView(discField)
            totalBox.addView(discRow)
            totalBox.addView(totalRow(activity, "Net Payable", EstimateModel.moneyShort(sheet.netPayable), GREEN, true))
        }
        redraw()

        val scroll = ScrollView(activity).apply { addView(root) }
        val dlg = AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "🧮 Cost Estimate"))
            .setView(scroll)
            .setPositiveButton("Save") { _, _ -> onDone(sheet) }
            .setNeutralButton("Print / Share") { _, _ -> onDone(sheet); onPaper(sheet) }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    /** Net Payable-এর সংখ্যাটা — ডিসকাউন্ট লিখলে সঙ্গে সঙ্গে বদলে দিতে। */
    private fun netLine(totalBox: LinearLayout): TextView? = try {
        val last = totalBox.getChildAt(totalBox.childCount - 1) as? LinearLayout
        last?.getChildAt(1) as? TextView
    } catch (_: Throwable) { null }

    private fun totalRow(c: Context, left: String, right: String, colour: String, big: Boolean = false): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(c, 6) }
            addView(TextView(c).apply {
                text = left
                textSize = if (big) 14.5f else 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (big) colour else "#5B6B81"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(c).apply {
                text = right
                textSize = if (big) 15f else 13f
                gravity = Gravity.END
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(colour))
            })
        }

    /** একটা সারি — নাম · মাপ/জায়গা · দর × সংখ্যা · কাটা · মোছা। */
    private fun lineView(
        activity: Activity,
        line: EstimateModel.Line,
        sheet: EstimateModel.Sheet,
        redraw: () -> Unit,
        onMoneyChanged: () -> Unit = {}
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(activity, 10), 0, dp(activity, 10))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(activity, 1), Color.parseColor(LINE))
                cornerRadius = 0f
            }
        }
        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val info = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(activity).apply {
            text = if (line.measure.isBlank()) line.name else line.name + "  (" + line.measure + ")"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (line.struck) MUT else INK))
            if (line.struck) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        if (line.position.isNotBlank()) info.addView(TextView(activity).apply {
            text = line.position
            textSize = 11f
            setTextColor(Color.parseColor(MUT))
        })
        top.addView(info)
        top.addView(TextView(activity).apply {
            text = if (line.struck) "↺" else "✕"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (line.struck) "#0B7A4B" else RED))
            setPadding(dp(activity, 10), 0, dp(activity, 8), 0)
            setOnClickListener { line.struck = !line.struck; redraw() }
        })
        top.addView(TextView(activity).apply {
            text = "🗑"
            textSize = 15f
            setPadding(dp(activity, 4), 0, 0, 0)
            setOnClickListener { sheet.lines.remove(line); redraw() }
        })
        row.addView(top)

        val edits = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 7) }
        }
        val totalText = TextView(activity).apply {
            text = EstimateModel.moneyShort(line.total)
            textSize = 13.5f
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(if (line.struck) MUT else GREEN))
            if (line.struck) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        }
        fun cell(title: String, field: EditText) {
            val c = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(activity, 6) }
            }
            c.addView(label(activity, title))
            c.addView(field)
            edits.addView(c)
        }
        val rateField = numberField(activity, EstimateModel.moneyShort(line.rate))
        val qtyField = numberField(activity, EstimateModel.moneyShort(line.qty))
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                line.rate = EstimateModel.num(rateField.text?.toString())
                line.qty = EstimateModel.num(qtyField.text?.toString())
                totalText.text = EstimateModel.moneyShort(line.total)
                /* 🔎 V973 (নিজে ধরা, TK-কে পাঠানোর আগেই) — আগে শুধু ওই লাইনের
                   টাকাই বদলাত; নিচের **Subtotal ও Net Payable পুরনোই থেকে যেত**,
                   ফলে ভুল টাকা চেকআপের ঘরে বসে যেতে পারত। এখন সঙ্গে সঙ্গে দুটোই
                   নতুন করে বসে। */
                onMoneyChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        rateField.addTextChangedListener(watcher)
        qtyField.addTextChangedListener(watcher)
        cell("RATE", rateField)
        cell("QTY", qtyField)
        val tot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tot.addView(label(activity, "TOTAL"))
        tot.addView(totalText.apply { setPadding(0, dp(activity, 8), 0, 0) })
        edits.addView(tot)
        row.addView(edits)
        return row
    }

    /** রোগ বেছে চিকিৎসার লাইন — গ্রেড/ইঞ্চি ও o'clock সহ। */
    private fun addTreatment(activity: Activity, sheet: EstimateModel.Sheet, redraw: () -> Unit) {
        var group = EstimatePrices.G_PILES
        var picked: EstimatePrices.Item? = null
        val chosen = sortedSetOf<Int>()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 4))
        }
        val diseaseRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val measureBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val clockBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val rateField = numberField(activity, "")
        val qtyField = numberField(activity, "")
        root.addView(diseaseRow)
        root.addView(measureBox.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        })
        root.addView(clockBox.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 8) }
        })
        val rateRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(activity, 10) }
        }
        fun cell(title: String, field: EditText) {
            val c = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { rightMargin = dp(activity, 6) }
            }
            c.addView(label(activity, title)); c.addView(field); rateRow.addView(c)
        }
        cell("RATE", rateField)
        cell("QTY", qtyField)
        root.addView(rateRow)

        lateinit var paint: () -> Unit

        fun rebuildMeasures() {
            measureBox.removeAllViews()
            val items = EstimatePrices.inGroup(activity, group)
            measureBox.addView(label(activity, if (group == EstimatePrices.G_FISTULA) "TRACT LENGTH" else "GRADE / TYPE"))
            val wrap = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(activity, 6) }
            }
            /* ⚠️ নিজে ধরা — লুপের নাম `it` রাখলে `setOnClickListener { }`-এর
               ভিতরে `it` মানে **ক্লিক-করা View**, দরের সারি নয়। তাই স্পষ্ট
               নাম `item` — নইলে ভুল জিনিস বসত। */
            for (item in items) {
                val t = TextView(activity).apply {
                    text = (if (item.measure.isBlank()) item.name else item.measure) +
                        "\n" + EstimateModel.moneyShort(item.rate)
                    textSize = 11.5f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { leftMargin = dp(activity, 3); rightMargin = dp(activity, 3) }
                    setPadding(dp(activity, 4), dp(activity, 9), dp(activity, 4), dp(activity, 9))
                    setOnClickListener {
                        picked = item
                        // 🔒 TK: দর নিজে থেকেই বসে; পরে হাতে বদলালে সেটাই থাকে।
                        rateField.setText(EstimateModel.moneyShort(item.rate))
                        paint()
                    }
                }
                wrap.addView(t)
            }
            measureBox.addView(wrap)
        }

        fun rebuildClock() {
            clockBox.removeAllViews()
            clockBox.addView(label(activity, "POSITION (O'CLOCK)"))
            var row: LinearLayout? = null
            for (h in 1..12) {
                if (row == null || (h - 1) % 6 == 0) {
                    row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(activity, 5) }
                    }
                    clockBox.addView(row)
                }
                val hh = h
                row!!.addView(TextView(activity).apply {
                    text = hh.toString()
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { leftMargin = dp(activity, 2); rightMargin = dp(activity, 2) }
                    setPadding(0, dp(activity, 9), 0, dp(activity, 9))
                    setOnClickListener {
                        if (chosen.contains(hh)) chosen.remove(hh) else chosen.add(hh)
                        // পাইলসে o'clock গুনেই সংখ্যা — TK-এর নিয়ম।
                        if (group != EstimatePrices.G_FISTULA && chosen.isNotEmpty())
                            qtyField.setText(chosen.size.toString())
                        paint()
                    }
                })
            }
        }

        paint = {
            for (i in 0 until diseaseRow.childCount) {
                val t = diseaseRow.getChildAt(i) as TextView
                val on = t.text.toString() == group
                t.background = box(activity, if (on) NAVY else "#EFF3F8", if (on) NAVY else "#E2E9F2", 11)
                t.setTextColor(if (on) Color.WHITE else Color.parseColor("#63748C"))
            }
            for (i in 0 until measureBox.childCount) {
                val wrap = measureBox.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until wrap.childCount) {
                    val t = wrap.getChildAt(j) as? TextView ?: continue
                    val items = EstimatePrices.inGroup(activity, group)
                    val on = j < items.size && picked?.name == items[j].name
                    t.background = box(activity, if (on) NAVY else "#F1F5F9", if (on) NAVY else "#E2E9F2", 11)
                    t.setTextColor(if (on) Color.WHITE else Color.parseColor("#63748C"))
                }
            }
            var h = 0
            for (i in 0 until clockBox.childCount) {
                val r = clockBox.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until r.childCount) {
                    val t = r.getChildAt(j) as? TextView ?: continue
                    h += 1
                    val on = chosen.contains(h)
                    t.background = box(activity, if (on) "#0B7A4B" else "#F1F5F9", if (on) "#0B7A4B" else "#E2E9F2", 9)
                    t.setTextColor(if (on) Color.WHITE else Color.parseColor("#63748C"))
                }
            }
        }

        for (g in EstimatePrices.DISEASE_GROUPS) {
            diseaseRow.addView(TextView(activity).apply {
                text = g
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = dp(activity, 3); rightMargin = dp(activity, 3) }
                setPadding(0, dp(activity, 9), 0, dp(activity, 9))
                setOnClickListener {
                    group = g; picked = null; chosen.clear()
                    rateField.setText(""); qtyField.setText("")
                    rebuildMeasures(); rebuildClock(); paint()
                }
            })
        }
        rebuildMeasures(); rebuildClock(); paint()

        val dlg = AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "➕ Add Treatment"))
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Add") { _, _ ->
                val chosenItem = picked
                if (chosenItem == null) {
                    android.widget.Toast.makeText(activity, "Select a treatment first", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val qty = EstimateModel.num(qtyField.text?.toString()).let { q -> if (q <= 0.0) 1.0 else q }
                    val rate = EstimateModel.num(rateField.text?.toString()).let { r -> if (r <= 0.0) chosenItem.rate else r }
                    val measure = if (group == EstimatePrices.G_FISTULA)
                        EstimateModel.moneyShort(qty) + " inch" else chosenItem.measure
                    sheet.lines.add(
                        EstimateModel.Line(
                            name = chosenItem.name,
                            measure = measure,
                            position = chosen.joinToString(", ") + (if (chosen.isEmpty()) "" else " o'clock"),
                            rate = rate, qty = qty
                        )
                    )
                    redraw()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    /** ওষুধ বা অন্যান্য — তালিকা থেকে বেছে দর ও সংখ্যা। */
    private fun addFromGroup(
        activity: Activity,
        sheet: EstimateModel.Sheet,
        group: String,
        redraw: () -> Unit
    ) {
        val items = EstimatePrices.inGroup(activity, group)
        if (items.isEmpty()) {
            android.widget.Toast.makeText(activity, "Price list is empty for $group", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = items.map { it.name + "   ·   " + EstimateModel.moneyShort(it.rate) }.toTypedArray()
        val dlg = AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "➕ Add $group"))
            .setItems(names) { _, which ->
                val chosenItem = items[which]
                sheet.lines.add(EstimateModel.Line(name = chosenItem.name, rate = chosenItem.rate, qty = 1.0))
                redraw()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }
}
