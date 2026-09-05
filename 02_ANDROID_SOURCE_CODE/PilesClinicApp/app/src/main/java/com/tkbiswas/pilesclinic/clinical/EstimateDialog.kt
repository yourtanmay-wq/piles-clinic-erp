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
            /* 💰🔒 V980 (TK-নির্দেশ) — টাকা না শতাংশ, একটাই ছোট বোতামে বদলায়। */
            discRow.addView(TextView(activity).apply {
                text = if (sheet.discountPct) "%" else "₹"
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = box(activity, NAVY, NAVY, 9)
                setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(activity, 7) }
                setOnClickListener { sheet.discountPct = !sheet.discountPct; paintTotals() }
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
            /* 💰 V980 — শতাংশ হলে কত টাকা হলো সেটাও এক নজরে। */
            if (sheet.discountPct && sheet.discount > 0.0) {
                totalBox.addView(totalRow(activity, "Discount amount",
                    EstimateModel.moneyShort(sheet.discountAmount), RED))
            }
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
            setOnClickListener {
                // 💰 V1063 — কাটলেই টাকাটা ছাড়ের ঘরে বসে যায় (TK-নির্দেশ)
                line.struck = !line.struck
                sheet.onStrikeToggled(line, line.struck)
                redraw()
            }
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
        // 🔴 V979 — স্টাফ নিজে দর লিখেছেন কি না; লিখে থাকলে আর মুছে দেওয়া হয় না।
        var rateTouched = false

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
        rateField.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { rateTouched = true }
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        lateinit var paint: () -> Unit

        lateinit var rebuildMeasures: () -> Unit
        rebuildMeasures = {
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
                        /* 🔒 TK: দর নিজে থেকেই বসে; পরে হাতে বদলালে সেটাই থাকে।
                           🔴 V979 (TK-রিপোর্ট ছবিসহ: *"এখানে 4100 ফিক্সড কেন
                           করেছেন"*) — আগে গ্রেড আবার চাপলে হাতে-লেখা দরটা মুছে
                           তালিকার দর ফিরে আসত। এখন **হাতে বদলানো থাকলে আর
                           মুছবে না**; একই গ্রেড আবার চাপলে লেখাটা অটুট থাকে। */
                        if (!rateTouched) rateField.setText(EstimateModel.moneyShort(item.rate))
                        paint()
                    }
                    /* 💰🔒 V979 (TK-নির্দেশ: *"ওখানেই 4100 চেঞ্জ করতে পারবো তার
                       ব্যবস্থা রাখতে হবে"*) — বোতামটা **চেপে ধরলে** তালিকার দরই
                       বদলে যায় (সবসময়ের জন্য)। RATE ঘরে লেখা শুধু ওই রোগীর জন্য,
                       তাই একজনকে ছাড় দিলে সবার দর নষ্ট হয় না। */
                    setOnLongClickListener {
                        editListRate(activity, item) { fresh ->
                            picked = fresh
                            rateTouched = false
                            rateField.setText(EstimateModel.moneyShort(fresh.rate))
                            rebuildMeasures(); paint()
                        }
                        true
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

    /** 💰 V979 — তালিকার দর সবসময়ের জন্য বদলানো (গ্রেড চেপে ধরলে খোলে)। */
    private fun editListRate(
        activity: Activity,
        item: EstimatePrices.Item,
        onSaved: (EstimatePrices.Item) -> Unit
    ) {
        val field = numberField(activity, EstimateModel.moneyShort(item.rate))
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 4))
            addView(label(activity, "NEW RATE FOR EVERY PATIENT", 9.5f))
            addView(field)
        }
        val dlg = AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "✏️ " + item.name))
            .setView(root)
            .setPositiveButton("Save") { _, _ ->
                val v = EstimateModel.num(field.text?.toString())
                if (v <= 0.0) {
                    android.widget.Toast.makeText(activity, "Enter a rate", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val all = EstimatePrices.list(activity).toMutableList()
                    val at = all.indexOfFirst { it.name.equals(item.name, ignoreCase = true) }
                    val fresh = item.copy(rate = v)
                    if (at >= 0) all[at] = fresh else all.add(fresh)
                    EstimatePrices.save(activity, all)
                    onSaved(fresh)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }

    /* ═══════════════════════════════════════════════════════════════════
       🖥️🔒 V982 (TK-নির্দেশ: *"ফুল স্ক্রিন পর্দা খুলবে, আলাদা পপ-আপ লাগবে না"*)
       — নতুন **ফুল-স্ক্রিন কাগজের পর্দা** (`EstimatePaperActivity`) এই তিনটে
       বাছাই-পপ-আপই ব্যবহার করে। তাই ওগুলো এখান থেকে ডাকার তিনটে ছোট দরজা।
       ⛔ ভিতরের কোড এক অক্ষরও বদলায়নি — শুধু বাইরে থেকে ডাকা যায়।
       ═══════════════════════════════════════════════════════════════════ */
    fun pickTreatment(activity: Activity, sheet: EstimateModel.Sheet, redraw: () -> Unit) =
        addTreatment(activity, sheet, redraw)

    fun pickMedicine(activity: Activity, sheet: EstimateModel.Sheet, redraw: () -> Unit) =
        addFromGroup(activity, sheet, EstimatePrices.G_MEDICINE, redraw)

    fun pickOther(activity: Activity, sheet: EstimateModel.Sheet, redraw: () -> Unit) =
        addFromGroup(activity, sheet, EstimatePrices.G_OTHER, redraw)

    /** ওষুধ বা অন্যান্য — তালিকা থেকে বাছা, নয়তো নিজের জিনিস যোগ করা।
     *
     * 💊🔒 V1007 (০৩.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ) — TK: *"Add Medicine ·
     * Add Other — এগুলো যেন আমি আলাদাভাবে যোগ করতে পারি"* · *"এই পপ-আপগুলো
     * আরো প্রফেশনাল বানাতে হবে"*।
     *  · আগে শুধু `setItems()`-এর সাদামাটা তালিকা ছিল — **তালিকার বাইরের কিছু
     *    যোগ করার কোনো উপায়ই ছিল না**। এখন নিচে নাম · দর · সংখ্যা লিখে যোগ
     *    করা যায়।
     *  · উপরে খোঁজার ঘর — তালিকা বড় হলে টাইপ করলেই ছেঁকে দেখায়।
     *  · সারিগুলোর দর ডানদিকে সবুজ পিলে, মাঝে হালকা দাগ।
     * ⛔ V986-এর নিয়ম (একই জিনিস আবার বাছলে নতুন লাইন নয়, সংখ্যা বাড়ে)
     *    এক অক্ষরও বদলায়নি; নিজের লেখা জিনিসেও ঠিক সেই নিয়মই খাটে।
     * ⛔ কম্পিউটারের যমজ: `app.js` → `wlv1EstAddGroup()`।
     */
    private fun addFromGroup(
        activity: Activity,
        sheet: EstimateModel.Sheet,
        group: String,
        redraw: () -> Unit
    ) {
        val items = EstimatePrices.inGroup(activity, group)

        /** একই নাম · দর হলে নতুন লাইন নয়, সংখ্যাই বাড়ে (V986-এর নিয়ম)। */
        fun addLine(name: String, rate: Double, qty: Double) {
            val same = sheet.lines.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    it.measure.isBlank() && it.position.isBlank() &&
                    it.rate == rate && !it.struck
            }
            /* ═══════════════════════════════════════════════════════════
               🚫🔒 V1113 (০৫.০৯.২০২৬, TK-নির্দেশ): *"ট্রিটমেন্টের খরচ ছাড়া
               বাকিগুলো স্ট্রাইক কাট হিসাবে অল টাইম সেট থাকবে। আমি চাইলে
               পরিবর্তন করতে পারবো।"*
               ⇒ Medicine ও Other-এর প্রতিটা লাইন **নিজে থেকেই কাটা অবস্থায়**
                 বসে; শুধু Treatment-এর লাইন আগের মতোই কাটা থাকে না।
               ⛔ কাটা টাকাটা প্রকল্পের **প্রমাণিত সেই একই পথেই** ছাড়ে যায়
                  (`onStrikeToggled`) — নতুন কোনো হিসাব লেখা হয়নি, তাই
                  Subtotal · Discount · Net Payable-এর নিয়ম এক অক্ষরও বদলায়নি।
               ⛔ TK যেকোনো লাইনে চাপ দিয়ে কাটা তুলে/বসিয়ে দিতে পারবেন — আগের
                  মতোই।
               ⚠️ ফল (TK-কে জানানো): কাগজে ওই টাকাটা **ছাড়ের ঘরে** যোগ হয়ে
                  যায়, তাই Treatment-এর লাইন না থাকলে Net Payable ০ দেখাবে —
                  এটাই ঠিক আচরণ (ওষুধ/অন্যান্য চিকিৎসার সঙ্গেই ধরা)।
               ═══════════════════════════════════════════════════════════ */
            val autoStrike = (group == EstimatePrices.G_MEDICINE || group == EstimatePrices.G_OTHER)
            if (same != null) same.qty += qty
            else {
                val line = EstimateModel.Line(name = name, rate = rate, qty = qty)
                sheet.lines.add(line)
                if (autoStrike) {
                    line.struck = true
                    sheet.onStrikeToggled(line, true)
                }
            }
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 4))
        }

        val listCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val rowViews = ArrayList<Pair<View, String>>()

        val empty = TextView(activity).apply {
            text = "No match in the price list."
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(MUT))
            setPadding(0, dp(activity, 14), 0, dp(activity, 14))
            visibility = View.GONE
        }

        if (items.isNotEmpty()) {
            val search = EditText(activity).apply {
                hint = "Search " + group.lowercase() + "…"
                textSize = 13f
                setTextColor(Color.parseColor(INK))
                setHintTextColor(Color.parseColor(MUT))
                inputType = InputType.TYPE_CLASS_TEXT
                background = box(activity, "#F8FBFE", "#D6E1EE", 10)
                /* 📏 V1007 — TK: *"বক্সের উচ্চতা এত বেশি থাকবে না, লেখাগুলো
                   বক্সের একদম মধ্যবর্তী স্থানে থাকতে হবে"* ⇒ উচ্চতা ৩৬dp,
                   উপর-নিচের প্যাডিং শূন্য, আর লেখা ঠিক মাঝখানে। */
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 11), 0, dp(activity, 11), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 36)
                ).apply { bottomMargin = dp(activity, 10) }
            }
            root.addView(search)

            listCol.background = box(activity, "#FFFFFF", "#E3E9EF", 12)
            for ((idx, it) in items.withIndex()) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))   // 📏 V1007
                }
                row.addView(TextView(activity).apply {
                    text = it.name
                    textSize = 13.5f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor(INK))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(activity).apply {
                    text = EstimateModel.moneyShort(it.rate)
                    textSize = 12.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0B6E33"))
                    background = box(activity, "#E9F6EE", "#E9F6EE", 12)
                    setPadding(dp(activity, 10), dp(activity, 3), dp(activity, 10), dp(activity, 3))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(activity, 10) }
                })
                listCol.addView(row)
                rowViews.add(row to it.name.lowercase())
                if (idx < items.size - 1) {
                    listCol.addView(View(activity).apply {
                        setBackgroundColor(Color.parseColor("#EEF2F6"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 1))
                    })
                }
            }
            root.addView(listCol)
            root.addView(empty)

            search.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val q = (s?.toString() ?: "").trim().lowercase()
                    var shown = 0
                    for ((v, name) in rowViews) {
                        val hit = q.isEmpty() || name.contains(q)
                        v.visibility = if (hit) View.VISIBLE else View.GONE
                        if (hit) shown += 1
                    }
                    listCol.visibility = if (shown > 0) View.VISIBLE else View.GONE
                    empty.visibility = if (shown > 0) View.GONE else View.VISIBLE
                }
                override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        } else {
            root.addView(TextView(activity).apply {
                text = "Price list is empty for " + group + "."
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor(MUT))
                setPadding(0, dp(activity, 12), 0, dp(activity, 12))
            })
        }

        /* 🚫 V1007 — TK: *"not in the list add your own — এই ধরনের ডেমি লেখা
           থাকবে না"* ⇒ শিরোনাম ও ছোট লেবেলগুলো তুলে দেওয়া হলো; শুধু একটা
           হালকা দাগ, আর ঘরের ভিতরের লেখাই (Name · Rate · Qty) যথেষ্ট।
           Qty ফাঁকা রাখলে ১ ধরা হয় (নিচের `if (qt <= 0.0) qt = 1.0`)। */
        root.addView(View(activity).apply {
            setBackgroundColor(Color.parseColor("#E7EDF3"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 1)
            ).apply { topMargin = dp(activity, 14); bottomMargin = dp(activity, 12) }
        })

        val nameField = EditText(activity).apply {
            hint = "Name"
            textSize = 13f
            setTextColor(Color.parseColor(INK))
            setHintTextColor(Color.parseColor(MUT))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = box(activity, "#F8FBFE", "#D6E1EE", 9)
            gravity = Gravity.CENTER_VERTICAL   // 📏 V1007
            setPadding(dp(activity, 9), 0, dp(activity, 9), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 36), 1f)
        }
        val rateField = numberField(activity, "").apply {
            hint = "Rate"
            setHintTextColor(Color.parseColor(MUT))
            gravity = Gravity.CENTER   // 📐 V1007 — লেখা ঘরের ঠিক মাঝখানে
            setPadding(dp(activity, 9), 0, dp(activity, 9), 0)
            layoutParams = LinearLayout.LayoutParams(dp(activity, 74), dp(activity, 36))
                .apply { marginStart = dp(activity, 8) }
        }
        val qtyField = numberField(activity, "").apply {
            hint = "Qty"
            setHintTextColor(Color.parseColor(MUT))
            gravity = Gravity.CENTER   // 📐 V1007 — লেখা ঘরের ঠিক মাঝখানে
            setPadding(dp(activity, 9), 0, dp(activity, 9), 0)
            layoutParams = LinearLayout.LayoutParams(dp(activity, 60), dp(activity, 36))
                .apply { marginStart = dp(activity, 8) }
        }
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(nameField); addView(rateField); addView(qtyField)
        })

        val addBtn = TextView(activity).apply {
            text = "＋  Add to estimate"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = box(activity, "#0B7A34", "#0B7A34", 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 38)
            ).apply { topMargin = dp(activity, 10) }
        }
        root.addView(addBtn)

        val scroll = ScrollView(activity).apply { addView(root) }
        val dlg = AlertDialog.Builder(activity)
            .setCustomTitle(PremiumAlert.header(activity, "➕ Add " + group))
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .create()

        for ((v, _) in rowViews) {
            val idx = rowViews.indexOfFirst { it.first === v }
            v.setOnClickListener {
                val chosen = items[idx]
                addLine(chosen.name, chosen.rate, 1.0)
                try { dlg.dismiss() } catch (_: Throwable) { }
                redraw()
            }
        }
        addBtn.setOnClickListener {
            val nm = nameField.text.toString().trim()
            val rt = rateField.text.toString().trim().toDoubleOrNull() ?: 0.0
            var qt = qtyField.text.toString().trim().toDoubleOrNull() ?: 0.0
            if (nm.isBlank()) {
                android.widget.Toast.makeText(activity, "Please write the name", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rt <= 0.0) {
                android.widget.Toast.makeText(activity, "Please write the rate", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (qt <= 0.0) qt = 1.0
            addLine(nm, rt, qt)
            try { dlg.dismiss() } catch (_: Throwable) { }
            redraw()
        }

        dlg.show()
        try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
    }
}
