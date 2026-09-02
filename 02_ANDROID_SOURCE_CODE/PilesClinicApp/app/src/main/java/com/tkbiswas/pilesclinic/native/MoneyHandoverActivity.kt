package com.tkbiswas.pilesclinic.native

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 💰🔒 V984 (০২.০৯.২০২৬, TK-এর পাশ-করা ফটো-প্রুফ) —
 * **MONEY HANDOVER — কোন দিনের টাকা কে বুঝে নিলেন, তার পুরো ইতিহাস।**
 *
 * TK-এর কথা:
 *  • *"চেম্বার বন্ধ করার history — কবে কোন চেম্বার বন্ধ করে মাস্টারকে বুঝিয়ে
 *    দেয়া হয়েছে, টাকার পরিমাণ, ডাক্তারকে — সেটাই বা স্টাফ কি করে বুঝতে পারবে"*
 *  • *"সেদিন টাকাটা স্টাফের কাছে থেকে গেল… পরে স্টাফ যেন ডাক্তারকে বা
 *    মাস্টারকে টাকাটা বুঝে দিতে পারে তার ব্যবস্থা"*
 *  • *"কে রিসিভ করল তার নাম এবং তারিখ এবং সময় এক লাইনে রাখুন"* — আর তারিখ
 *    উপরে একবারই (*"তারিখ দুই জায়গায় কেন"*)।
 *
 * ⛔ কোনো টাকা তৈরি বা বদল হয় না — শুধু কে বুঝে নিলেন সেটা লেখা ও দেখা।
 * ⛔ যিনি নিচ্ছেন তিনি নিজের পাসওয়ার্ড দিলে তবেই "বুঝে নেওয়া" ধরা হয়।
 */
class MoneyHandoverActivity : AppCompatActivity() {

    private lateinit var listBox: LinearLayout
    private lateinit var sumBar: TextView
    private var days: List<MoneyHandover.Day> = emptyList()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(fill: String, stroke: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        setStroke(dp(1), Color.parseColor(stroke))
        cornerRadius = dp(radius).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEF3F1"))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#123E8C"))
            setPadding(dp(14), dp(13), dp(16), dp(13))
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = "◀"; textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(dp(2), 0, dp(12), 0)
                setOnClickListener { finish() }
            })
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = "MONEY HANDOVER"; textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
        })
        sumBar = TextView(this).apply {
            text = "Loading…"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#8A1810"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(sumBar)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        setContentView(root)
        load()
    }

    private fun branchOfMe(): String {
        val u = NativeSession.current(this) ?: return ""
        return if (u.role == "master") BranchFilterStore.get(this) else u.branch
    }

    private fun load() {
        Thread {
            val me = NativeSession.current(this)
            val mine = branchOfMe()
            val rows = if (me?.role == "doctor")
                /* ডাক্তারের পর্দায় শুধু তাঁর নিজের বুঝে নেওয়ার কাজগুলো ও
                   তাঁর ব্রাঞ্চের ইতিহাস। */
                (MoneyHandover.waitingFor(me.mobile) + MoneyHandover.fetchDays(mine))
                    .distinctBy { it.id }
            else MoneyHandover.fetchDays(mine)
            runOnUiThread { days = rows.sortedByDescending { it.date }; render() }
        }.start()
    }

    private fun render() {
        listBox.removeAllViews()
        val pending = days.filter { it.stillWithStaff }.sumOf { it.total }
        sumBar.text = "STILL WITH YOU        " + MoneyHandover.money(pending)
        sumBar.visibility = if (days.isEmpty()) View.GONE else View.VISIBLE
        if (days.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "No closed chamber found yet."
                textSize = 13f
                setTextColor(Color.parseColor("#7A8794"))
                setPadding(dp(18), dp(20), dp(18), dp(20))
            })
            return
        }
        for (d in days) listBox.addView(cardFor(d))
    }

    private fun cardFor(d: MoneyHandover.Day): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box("#FFFFFF", "#FFFFFF", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(10), dp(8), dp(10), 0) }
        }
        // ── উপরের সারি: তারিখ · ব্রাঞ্চ · টাকা (তারিখ শুধু এখানেই — TK-নির্দেশ) ──
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = MoneyHandover.dotDate(d.date); textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#16232E"))
            })
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = "  " + d.branch; textSize = 12f
                setTextColor(Color.parseColor("#7A8794"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = MoneyHandover.money(d.total); textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F5132"))
            })
        })

        // ── এক লাইনের অবস্থা: নাম · সময় (TK-নির্দেশ) ──
        val time = MoneyHandover.timeOf(d.receivedAt)
        val (line, ink, fill) = when (d.status) {
            "received" -> Triple("✓  " + d.receiverName + (if (time.isBlank()) "" else "  ·  $time"),
                "#0B5B2F", "#EAF7F0")
            "waiting" -> Triple("⌛  " + d.receiverName + (if (time.isBlank()) "" else "  ·  $time") + "  —  waiting",
                "#8A5A00", "#FFF6E6")
            else -> Triple("⚠️  Money is still with you — nobody has received it",
                "#8A1810", "#FDEDEC")
        }
        card.addView(TextView(this).apply {
            text = line; textSize = 12.5f
            setTextColor(Color.parseColor(ink))
            background = box(fill, fill, 8)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        })

        val me = NativeSession.current(this)
        val meMobile = StaffDirectory.normalizeMobile(me?.mobile.orEmpty())

        // ── স্টাফের কাছে থেকে গেলে পরে যেকোনো দিন বুঝিয়ে দেওয়া যায় ──
        if (d.stillWithStaff) {
            card.addView(actionButton("💰  HAND OVER NOW", "#0B4F2A") { handOver(d) })
        }
        // ── যাঁকে দেওয়া হয়েছে, তিনি নিজের ফোনে স্বীকার করবেন ──
        if (d.status == "waiting" && meMobile.isNotBlank() && meMobile == d.receiverMobile) {
            card.addView(actionButton("✅  I RECEIVED THIS MONEY", "#0F3D6B") { acknowledge(d) })
        }
        return card
    }

    private fun actionButton(label: String, colour: String, run: () -> Unit) =
        TextView(this).apply {
            text = label; textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = box(colour, colour, 10)
            setPadding(dp(6), dp(13), dp(6), dp(13))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
            setOnClickListener { run() }
        }

    private fun passwordField(): EditText = EditText(this).apply {
        hint = "Password"
        textSize = 13.5f
        setTextColor(Color.parseColor("#101C2E"))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = box("#F8FBFE", "#D6E1EE", 9)
        setPadding(dp(12), dp(11), dp(12), dp(11))
        tag = "nocaps"
    }

    /** স্টাফ পরে টাকাটা বুঝিয়ে দিচ্ছে — কে নিচ্ছেন, তাঁর পাসওয়ার্ড। */
    private fun handOver(d: MoneyHandover.Day) {
        val receivers = MoneyHandover.receiversFor(d.branch)
        if (receivers.isEmpty()) {
            Toast.makeText(this, "No doctor is listed for this branch", Toast.LENGTH_LONG).show(); return
        }
        val names = receivers.map { it.name + "   ·   " + it.role }.toTypedArray()
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "💰 Hand over " + MoneyHandover.money(d.total)))
            .setItems(names) { _, which -> askPassword(d, receivers[which]) }
            .setNegativeButton("Cancel", null)
            .create().also { it.show(); try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    private fun askPassword(d: MoneyHandover.Day, who: MoneyHandover.Receiver) {
        val field = passwordField()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = "PASSWORD OF " + who.name
                textSize = 9.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#8B98A9"))
                letterSpacing = 0.09f
                setPadding(0, 0, 0, dp(5))
            })
            addView(field)
        }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "💰 " + who.name))
            .setView(body)
            .setPositiveButton("Hand over") { _, _ -> doHandOver(d, who, field.text?.toString().orEmpty()) }
            .setNegativeButton("Cancel", null)
            .create().also { it.show(); try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }

    private fun doHandOver(d: MoneyHandover.Day, who: MoneyHandover.Receiver, typed: String) {
        val me = NativeSession.current(this)
        val myName = me?.name.orEmpty().ifBlank { me?.mobile.orEmpty() }
        Thread {
            val role = StaffDirectory.findAccount(who.mobile)?.role ?: "doctor"
            val v = MoneyHandover.verifyPassword(who.mobile, role, typed)
            val ok = v == MoneyHandover.Verify.OK &&
                MoneyHandover.saveHandover(this, d.branch, d.date, d.total, who, true, myName)
            runOnUiThread {
                when {
                    v == MoneyHandover.Verify.NO_NETWORK ->
                        Toast.makeText(this, "Network problem — could not verify. Please try again.", Toast.LENGTH_LONG).show()
                    v == MoneyHandover.Verify.WRONG ->
                        Toast.makeText(this, "Wrong password", Toast.LENGTH_LONG).show()
                    ok -> { Toast.makeText(this, "Handed over to " + who.name, Toast.LENGTH_LONG).show(); load() }
                    else -> Toast.makeText(this, "Could not save — please try again", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** ডাক্তার/মাস্টার নিজের ফোনে স্বীকার করছেন। */
    private fun acknowledge(d: MoneyHandover.Day) {
        val me = NativeSession.current(this) ?: return
        val field = passwordField()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(TextView(this@MoneyHandoverActivity).apply {
                text = "YOUR PASSWORD"
                textSize = 9.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#8B98A9"))
                letterSpacing = 0.09f
                setPadding(0, 0, 0, dp(5))
            })
            addView(field)
        }
        AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "✅ " + MoneyHandover.money(d.total)))
            .setView(body)
            .setPositiveButton("I received this money") { _, _ ->
                val typed = field.text?.toString().orEmpty()
                Thread {
                    val v = MoneyHandover.verifyPassword(me.mobile, me.role, typed)
                    val ok = v == MoneyHandover.Verify.OK &&
                        MoneyHandover.acknowledge(this, d, me.name.ifBlank { me.mobile })
                    runOnUiThread {
                        when {
                            v == MoneyHandover.Verify.NO_NETWORK ->
                                Toast.makeText(this, "Network problem — could not verify. Please try again.", Toast.LENGTH_LONG).show()
                            v == MoneyHandover.Verify.WRONG ->
                                Toast.makeText(this, "Wrong password", Toast.LENGTH_LONG).show()
                            ok -> { Toast.makeText(this, "Confirmed", Toast.LENGTH_LONG).show(); load() }
                            else -> Toast.makeText(this, "Could not save — please try again", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .create().also { it.show(); try { PremiumAlert.paint(it) } catch (_: Throwable) { } }
    }
}
