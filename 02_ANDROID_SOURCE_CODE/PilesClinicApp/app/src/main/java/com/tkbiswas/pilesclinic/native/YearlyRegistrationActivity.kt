package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

        bodyCol.addView(monthTable())

        bodyCol.addView(label("Patients", 14f, "#0B5E2A", true).apply {
            setPadding(0, px(14), 0, px(6))
        })

        if (rows.isEmpty()) {
            bodyCol.addView(label("No registration in this year.", 13f, "#7A8794"))
            return
        }
        for (e in rows) bodyCol.addView(patientRow(e))
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

    private fun patientRow(e: DraftEntry): View {
        val skipped = e.extra == YearlyRegistration.SKIP_MARK
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(if (skipped) "#F7F9FC" else "#FFFFFF"))
            setPadding(px(10), px(9), px(10), px(9))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(label(e.name.ifBlank { "UNKNOWN" }, 13.5f,
            if (skipped) "#9AA6B4" else "#101828", true).apply {
            if (skipped) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        texts.addView(label(
            listOf(e.patientId, DateUtil.display(e.recordDate)).filter { it.isNotBlank() }.joinToString(" · "),
            11.5f, "#9AA6B4"))
        row.addView(texts)

        val btn = TextView(this).apply {
            text = if (skipped) "Undo" else "Skip"
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor(if (skipped) "#0B6E33" else "#B3261E"))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f * d()
                setColor(android.graphics.Color.parseColor(if (skipped) "#E9F6EE" else "#FDECEA"))
            }
            setPadding(px(14), px(7), px(14), px(7))
            setOnClickListener { toggle(e, this) }
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
