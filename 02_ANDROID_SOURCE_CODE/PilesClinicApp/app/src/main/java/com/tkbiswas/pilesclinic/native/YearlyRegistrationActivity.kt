package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 📊 V824 — বার্ষিক রেজিস্ট্রেশনের বিস্তারিত (শুধু মাস্টার)।
 *
 * TK-নির্দেশ (২৯.০৮.২০২৬): মাসভিত্তিক হিসাব + রোগীর তালিকা, আর
 * *"ওই লিস্ট থেকে আমি যদি কিছু বাদ দিয়ে দেই… সেটা প্রয়োজনে আমি দেখে দেখে
 * বাদ দিতে পারব, তার ব্যবস্থা রাখবেন"* — তাই প্রতিটি নামের পাশে **Skip**,
 * আর ভুল হলে **Undo**।
 *
 * ⛔ তালিকাটা Draft পর্দা থেকেই তৈরি হয়ে আসে — এই পর্দা রোগীর কোনো টেবিল
 *    নতুন করে পড়ে না (Egress বাড়ে না)। শুধু "বাদ-দেওয়া" ছোট্ট তালিকাটা
 *    (`fin.registration_count_excluded`, কয়েকটা সারি) ক্লাউড থেকে মিলিয়ে
 *    নেওয়া হয়, যাতে অন্য ফোনে করা বাদও এখানে দেখা যায়।
 * ⛔ কোনো রোগীর রেকর্ড · টাকা · Follow-up কিচ্ছু ছোঁয়া হয় না। "Skip" মানে
 *    শুধু **এই গোনায় ধরা হবে না**।
 */
class YearlyRegistrationActivity : AppCompatActivity() {

    private var branch: String = ""
    private var year: String = ""
    private var rows: MutableList<DraftEntry> = mutableListOf()

    /* 🆕🔒 V852 (৩০.০৮.২০২৬, TK-অনুমোদিত ডেমো প্রুফ) — ছাঁকনি · টিক-মার্ক ·
       "কতজন বাদ পড়ল"। ⛔ সবই এই পর্দার ভিতরে, কোনো নতুন ক্লাউড-পড়া নেই। */
    private var outDemo: Int = 0
    private var outNoDate: Int = 0
    private var filter: String = "all"   // all | counted | skipped | return | refund
    private val picked = HashSet<String>()

    private lateinit var bodyCol: LinearLayout
    private lateinit var totalView: TextView

    private fun d(): Float = resources.displayMetrics.density
    private fun px(v: Int): Int = (v * d()).toInt()

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        branch = intent.getStringExtra("branch").orEmpty()
        year = intent.getStringExtra("year").orEmpty().ifBlank { YearlyRegistration.currentYear() }
        rows = ((intent.getSerializableExtra("entries") as? ArrayList<DraftEntry>) ?: ArrayList()).toMutableList()
        outDemo = intent.getIntExtra("outDemo", 0)        // 🆕 V852
        outNoDate = intent.getIntExtra("outNoDate", 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F4F7FB"))
        }
        root.addView(header())

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }
        bodyCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(10), px(12), px(20))
        }
        scroll.addView(bodyCol)
        root.addView(scroll)
        setContentView(root)

        render()
        refreshExclusionsFromCloud()
    }

    private fun header(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#0B7A3E"))
            setPadding(px(12), px(12), px(12), px(12))
        }
        bar.addView(TextView(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { finish() }
            setPadding(0, 0, px(12), 0)
        })
        bar.addView(TextView(this).apply {
            text = "Yearly Registration"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
        })
        return bar
    }

    private fun label(text: String, size: Float, color: String, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(android.graphics.Color.parseColor(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun render() {
        bodyCol.removeAllViews()

        bodyCol.addView(label(
            (branch.ifBlank { "All" }) + " · " + year, 13f, "#5B6B81"))

        totalView = label(YearlyRegistration.countedOf(rows).toString(), 30f, "#0B5E2A", true)
        totalView.setPadding(0, px(2), 0, px(8))
        bodyCol.addView(totalView)

        // 🆕 V852 — TK: "কতজন বাদ পড়ল ও কেন" (তালিকাতেও নেই, গোনাতেও নেই)।
        excludedLine()?.let { bodyCol.addView(it) }

        bodyCol.addView(monthTable())

        bodyCol.addView(filterChips())   // 🆕 V852

        /* 🔤🔒 V852 — TK: *"Patients লিখেছেন কেন, এতে বিভ্রান্ত হয়ে যাচ্ছি —
           যত লোক চেম্বারে এসেছে তারা প্রত্যেকে ট্রিটমেন্ট শুরু করেনি"*।
           তাই লেখাটা "Registered"। */
        bodyCol.addView(label("Registered", 14f, "#0B5E2A", true).apply {
            setPadding(0, px(14), 0, px(6))
        })

        if (picked.isNotEmpty()) bodyCol.addView(selectionBar())   // 🆕 V852

        val shown = rows.filter { visibleIn(it) }
        if (shown.isEmpty()) {
            bodyCol.addView(label(
                if (rows.isEmpty()) "No registration in this year." else "Nothing in this filter.",
                13f, "#7A8794"))
            return
        }
        for (e in shown) bodyCol.addView(patientRow(e))
    }

    // ───────────────────── 🆕 V852 — নতুন অংশগুলো ─────────────────────

    private fun excludedLine(): View? {
        if (outDemo <= 0 && outNoDate <= 0) return null
        val parts = ArrayList<String>()
        if (outDemo > 0) parts.add("$outDemo demo/test name")
        if (outNoDate > 0) parts.add("$outNoDate no registration date")
        return label("Not in this list: " + parts.joinToString(" · "), 11.5f, "#7A8794").apply {
            setBackgroundColor(android.graphics.Color.parseColor("#EDF2F8"))
            setPadding(px(9), px(7), px(9), px(7))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = px(10)
            layoutParams = lp
        }
    }

    private fun visibleIn(e: DraftEntry): Boolean = when (filter) {
        "counted" -> e.extra != YearlyRegistration.SKIP_MARK
        "skipped" -> e.extra == YearlyRegistration.SKIP_MARK
        "return" -> e.regTag == YearlyRegistration.TAG_RETURN
        "refund" -> e.regTag == YearlyRegistration.TAG_REFUND
        else -> true
    }

    private fun filterChips(): View {
        val holder = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = px(14)
            layoutParams = lp
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = listOf(
            "all" to "All", "counted" to "Counted", "skipped" to "Removed",   // 🎨 V891 — শুধু লেখা (V884-এর একই নিয়ম)
            "return" to YearlyRegistration.TAG_RETURN, "refund" to YearlyRegistration.TAG_REFUND
        )
        for ((key, text) in items) {
            val on = filter == key
            bar.addView(TextView(this).apply {
                this.text = text
                textSize = 10.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(if (on) "#FFFFFF" else "#0B7A34"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16f * d()
                    setColor(android.graphics.Color.parseColor(if (on) "#0B7A34" else "#EFF7F1"))
                    if (!on) setStroke(px(1), android.graphics.Color.parseColor("#CFE9D8"))
                }
                setPadding(px(12), px(6), px(12), px(6))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = px(5)
                layoutParams = lp
                setOnClickListener { filter = key; render() }
            })
        }
        holder.addView(bar)
        return holder
    }

    /** টিক-মার্ক করা থাকলে উপরে এই বার — একসাথে Skip / Restore। */
    private fun selectionBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * d()
                setColor(android.graphics.Color.parseColor("#0B2B59"))
            }
            setPadding(px(10), px(8), px(10), px(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = px(8)
            layoutParams = lp
        }
        bar.addView(label("${picked.size} selected", 12f, "#FFFFFF").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fun act(text: String, fg: String, bg: String, wantSkip: Boolean): TextView =
            TextView(this).apply {
                this.text = text
                textSize = 11.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(fg))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 7f * d()
                    setColor(android.graphics.Color.parseColor(bg))
                }
                setPadding(px(12), px(6), px(12), px(6))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginStart = px(8)
                layoutParams = lp
                setOnClickListener { confirmBulk(wantSkip) }
            }
        bar.addView(act("Remove", "#B3261E", "#FDECEA", true))   // 🎨 V884 — শুধু লেখা
        bar.addView(act("Restore", "#0B6E33", "#E9F6EE", false))
        return bar
    }

    /** মাসভিত্তিক হিসাব — দুই কলামে (জানু–জুন | জুলাই–ডিসে), শেষে Total। */
    private fun monthTable(): View {
        val names = listOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        val counts = IntArray(12)
        for (e in rows) {
            if (e.extra == YearlyRegistration.SKIP_MARK) continue
            val d = e.recordDate
            if (d.length < 7) continue
            val m = d.substring(5, 7).toIntOrNull() ?: continue
            if (m in 1..12) counts[m - 1]++
        }

        val table = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(px(10), px(8), px(10), px(8))
        }
        fun column(from: Int, to: Int): LinearLayout {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(label("Month", 11f, "#7A8794", true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(label("Count", 11f, "#7A8794", true))
            col.addView(head)
            var sum = 0
            for (i in from..to) {
                sum += counts[i]
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, px(4), 0, px(4))
                }
                r.addView(label(names[i], 12.5f, "#101828").apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                r.addView(label(counts[i].toString(), 12.5f, "#101828"))
                col.addView(r)
            }
            col.addView(View(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#0B7A3E"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
            })
            val tot = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(5), 0, 0)
            }
            tot.addView(label("Total", 12.5f, "#0B5E2A", true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            tot.addView(label(sum.toString(), 12.5f, "#0B5E2A", true))
            col.addView(tot)
            return col
        }
        table.addView(column(0, 5))
        table.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(12), px(1))
        })
        table.addView(column(6, 11))
        return table
    }

    /* 🟢🔒 V878 — নামে চাপ দিলে রোগীর নিজস্ব পাতা।
       ⛔ `FollowUpActivity.openTimelineFor()`-এর হুবহু একই extras — তাই এক
          নম্বরে দুজন রোগী থাকলেও ঠিক এই রোগীরই পাতা খোলে (`prePatientId`)।
       ⛔ `section` পাঠানো হয় না ⇒ ড্যাশবোর্ডের View-এর মতো **সব** ইতিহাস
          দেখায়, কোনো ভাগ বাদ পড়ে না। */
    private fun openPatientPage(e: DraftEntry) {
        val digits = e.mobile.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            android.widget.Toast.makeText(this, "No mobile number saved for this patient",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val i = android.content.Intent(this, PatientTimelineActivity::class.java)
        i.putExtra("mobile", digits)
        i.putExtra("preStage", e.stage)
        i.putExtra("preName", e.name)
        i.putExtra("preBranch", e.branch.ifBlank { branch })
        i.putExtra("preDisease", e.disease)
        i.putExtra("preAge", e.age)
        i.putExtra("preSex", e.sex)
        i.putExtra("preAddress", e.address)
        i.putExtra("prePatientId", e.patientId)
        try { startActivity(i) } catch (_: Throwable) { }
    }

    private fun patientRow(e: DraftEntry): View {
        val skipped = e.extra == YearlyRegistration.SKIP_MARK
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(if (skipped) "#F7F9FC" else "#FFFFFF"))
            setPadding(px(10), px(9), px(10), px(9))
        }
        // 🆕 V852 — টিক-মার্ক (একসাথে অনেককে Skip / Restore করার জন্য)।
        row.addView(TextView(this).apply {
            val on = picked.contains(e.id)
            text = if (on) "✓" else ""
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 4f * d()
                setColor(android.graphics.Color.parseColor(if (on) "#0B7A34" else "#FFFFFF"))
                setStroke(px(2), android.graphics.Color.parseColor(if (on) "#0B7A34" else "#B7C3D1"))
            }
            layoutParams = LinearLayout.LayoutParams(px(19), px(19)).apply { marginEnd = px(10) }
            setOnClickListener {
                if (!picked.remove(e.id)) picked.add(e.id)
                render()
            }
        })

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        /* 🆕 V852 — TK: *"নামের আশে পাশে রোগের নাম চাই"* + *"return visit /
           refund … ফার্স্ট ব্র্যাকেটের মধ্যে মেনশন থাকবে"*। নাম · রোগ · ট্যাগ
           একটাই লাইনে, রঙ আলাদা (Follow-up কার্ডের সবুজ রোগ-পিলের মতোই)। */
        val nameLine = android.text.SpannableStringBuilder(e.name.ifBlank { "UNKNOWN" })
        val nameEnd = nameLine.length
        if (e.disease.isNotBlank()) {
            val a = nameLine.length
            nameLine.append("  ").append(e.disease.uppercase())
            nameLine.setSpan(android.text.style.ForegroundColorSpan(
                android.graphics.Color.parseColor("#0C9E33")), a, nameLine.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            nameLine.setSpan(android.text.style.RelativeSizeSpan(0.82f), a, nameLine.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (e.regTag.isNotBlank()) {
            val a = nameLine.length
            nameLine.append("  (").append(e.regTag).append(")")
            nameLine.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor(
                if (e.regTag == YearlyRegistration.TAG_REFUND) "#B3261E" else "#B54708")),
                a, nameLine.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            nameLine.setSpan(android.text.style.RelativeSizeSpan(0.82f), a, nameLine.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        texts.addView(label("", 13.5f, if (skipped) "#9AA6B4" else "#101828", true).apply {
            text = nameLine
            if (skipped) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            if (skipped) setTextColor(android.graphics.Color.parseColor("#9AA6B4"))
            /* 🟢🔒 V878 (৩০.০৮.২০২৬, TK-নির্দেশ, জিজ্ঞাসা করে নিশ্চিত হয়ে):
               *"এখানে পেশেন্টের নামে চাপ দিলে রি-ডাইরেক্ট হতে হবে যে সেকশনে
               আছে"* ⇒ নামে চাপ দিলে **ওই রোগীর নিজস্ব পাতা** খোলে — কার্ডের
               👁 বোতাম যেখানে নিয়ে যায়, হুবহু সেই পর্দা ও সেই তথ্যগুলোই
               (`FollowUpActivity.openTimelineFor`-এর প্রমাণিত নিয়ম, নতুন কিছু
               বানানো হয়নি)।
               ⛔ বাঁয়ের টিক-বাক্স ও ডানের Skip/Undo বোতাম নিজেরা চাপ ধরে,
                  তাই ওগুলোর কাজ এক অক্ষরও বদলায়নি।
               ⛔ কোনো নতুন ক্লাউড-পড়া নেই — তথ্যগুলো এই সারিতেই ছিল। */
            isClickable = true
            isFocusable = true
            setOnClickListener { openPatientPage(e) }
        })
        texts.addView(label(
            listOf(e.patientId, DateUtil.display(e.recordDate)).filter { it.isNotBlank() }.joinToString(" · "),
            11.5f, "#9AA6B4"))
        row.addView(texts)

        val btn = TextView(this).apply {
            /* 🎨🔒 V884 (৩০.০৮.২০২৬, TK-নির্দেশ — *"নামটা Skip না করে
               Remove করলে কেমন হয়"*, জিজ্ঞাসা করে নিশ্চিত: **শুধু লেখাটা**):
               বোতামের শব্দ Skip → Remove। ⛔ কাজ এক অক্ষরও বদলায়নি — এখনো
               শুধু বছরের গোনা থেকে বাদ, রোগী/টাকা/Follow-up কিচ্ছু মোছে না,
               আর Undo-ও আগের মতোই আছে। */
            text = if (skipped) "Undo" else "Remove"
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(if (skipped) "#0B6E33" else "#B3261E"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * d()
                setColor(android.graphics.Color.parseColor(if (skipped) "#E9F6EE" else "#FDECEA"))
            }
            setPadding(px(14), px(7), px(14), px(7))
            // 🆕 V852 — TK: *"Skip করলে যেন বাধা দেয়, warning দেখাতে হবে
            //    are you sure — yes / no"*। ⛔ Undo-তে বাধা নেই (ওটা ফেরানো)।
            setOnClickListener {
                if (skipped) toggle(e, this)
                else AlertDialog.Builder(this@YearlyRegistrationActivity)
                    .setCustomTitle(PremiumAlert.header(this@YearlyRegistrationActivity, "Remove from count?"))
                    .setMessage("Are you sure? " + e.name.ifBlank { "This person" } +
                        " will not be counted in " + year + ".")
                    .setPositiveButton("Yes") { _, _ -> toggle(e, this) }
                    .setNegativeButton("No", null)
                    .show().also { PremiumAlert.paint(it) }
            }
        }
        row.addView(btn)

        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        wrap.addView(View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#ECF1F6"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1))
        })
        return wrap
    }

    /** 🆕 V852 — টিক-মার্ক করা সবাইকে একসাথে Skip / Restore (আগে সতর্কবার্তা)। */
    private fun confirmBulk(wantSkip: Boolean) {
        val ids = picked.toList()
        if (ids.isEmpty()) return
        val what = if (wantSkip) "Remove" else "Restore"   // 🎨 V884 — শুধু লেখা
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "$what ${ids.size} selected?"))
            .setMessage(
                if (wantSkip) "Are you sure? They will not be counted in $year."
                else "Are you sure? They will be counted again in $year.")
            .setPositiveButton("Yes") { _, _ -> applyBulk(ids, wantSkip) }
            .setNegativeButton("No", null)
            .show().also { PremiumAlert.paint(it) }
    }

    /* ⛔ একটা একটা করে পাঠানো হয় ও প্রতিটার উত্তর দেখে তবেই দাগ বসে — নেট
       খারাপ হলে যেগুলো সত্যিই হয়নি সেগুলো আগের মতোই থাকে, আর কতগুলো ব্যর্থ
       হলো সেটা পর্দায় বলা হয় (চুপচাপ ভুল সংখ্যা দেখাবে না)। */
    private fun applyBulk(ids: List<String>, wantSkip: Boolean) {
        val me = try { NativeSession.current(this)?.name.orEmpty() } catch (_: Throwable) { "" }
        Thread {
            var failed = 0
            for (id in ids) {
                val e = rows.firstOrNull { it.id == id } ?: continue
                val already = (e.extra == YearlyRegistration.SKIP_MARK)
                if (already == wantSkip) continue
                val ok = try {
                    if (wantSkip) YearlyRegistration.exclude(this, e.id, e.patientId, e.name, me)
                    else YearlyRegistration.include(this, e.id)
                } catch (_: Throwable) { false }
                if (!ok) { failed++; continue }
                val idx = rows.indexOfFirst { it.id == e.id }
                if (idx >= 0) rows[idx] = rows[idx].copy(
                    extra = if (wantSkip) YearlyRegistration.SKIP_MARK else "")
            }
            val bad = failed
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                picked.clear()
                if (bad > 0) Toast.makeText(
                    this, "$bad could not be saved — check connection and try again",
                    Toast.LENGTH_LONG).show()
                render()
            }
        }.start()
    }

    private fun toggle(e: DraftEntry, btn: TextView) {
        val wantSkip = e.extra != YearlyRegistration.SKIP_MARK
        btn.isEnabled = false
        val me = try { NativeSession.current(this)?.name.orEmpty() } catch (_: Throwable) { "" }
        Thread {
            val ok = try {
                if (wantSkip) YearlyRegistration.exclude(this, e.id, e.patientId, e.name, me)
                else YearlyRegistration.include(this, e.id)
            } catch (_: Throwable) { false }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                btn.isEnabled = true
                if (!ok) {
                    Toast.makeText(this, "Could not save — check connection and try again",
                        Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val idx = rows.indexOfFirst { it.id == e.id }
                if (idx >= 0) rows[idx] = rows[idx].copy(
                    extra = if (wantSkip) YearlyRegistration.SKIP_MARK else "")
                // ⛔ Draft-এর সংখ্যাটা আলাদা করে ঠিক করতে হয় না — ফিরে গেলে
                //    Draft নিজেই আবার হিসাব করে, আর সেই হিসাব এই ফোনে সদ্য
                //    জমা হওয়া "বাদ" তালিকাটাই পড়ে (নতুন কোনো ক্লাউড-কল নয়)।
                render()
            }
        }.start()
    }

    /**
     * অন্য ফোনে করা "বাদ" এখানেও দেখা যাবে — ছোট্ট তালিকাটা (কয়েকটা সারি)
     * একবার মিলিয়ে নেওয়া হয়। ⛔ ব্যর্থ হলে কিছুই বদলায় না (আগের, জমানো
     * তালিকাই বহাল থাকে) — তাই নেট খারাপ থাকলে ভুল সংখ্যা দেখাবে না।
     */
    private fun refreshExclusionsFromCloud() {
        Thread {
            val fetched = try { YearlyRegistration.fetchExcluded(this) } catch (_: Throwable) { null }
            if (fetched == null) return@Thread
            val ids = HashSet<String>()
            for (o in fetched) {
                val id = o.s("patient_row_id")
                if (id.isNotBlank()) ids.add(id)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                var changed = false
                for (i in rows.indices) {
                    val want = if (ids.contains(rows[i].id)) YearlyRegistration.SKIP_MARK else ""
                    if (rows[i].extra != want) { rows[i] = rows[i].copy(extra = want); changed = true }
                }
                if (changed) render()
            }
        }.start()
    }
}
