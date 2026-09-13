package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.databinding.ActivityVoiceReportDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* 🎤 V1415 (১৩.০৯.২০২৬, TK-নির্দেশ) — Search-এর ভয়েস/টাইপ-প্রশ্নের উত্তরে
 * চাপ দিলে এই পর্দা খোলে — আসল রোগী/পেমেন্টের তালিকা, রোগীর নামে চাপলে
 * চেনা Patient Timeline-এই যায় (TK: "চাপ দিলে যেন সেই পেজে রিডাইরেক্ট হয়")।
 * ⛔ এখানে কোনো টাকা/হিসাব নতুন করে গোনা হয় না — সার্ভারের ফাংশনই যা দেয় তাই। */
class VoiceReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceReportDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val metric = intent.getStringExtra("metric").orEmpty()
        val branch = intent.getStringExtra("branch").orEmpty()
        val from = intent.getStringExtra("from").orEmpty()
        val to = intent.getStringExtra("to").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        binding.toolbar.title = title.ifBlank { "Report" }
        binding.progressLoad.visibility = android.view.View.VISIBLE
        binding.tvSummary.text = "Loading…"

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun row(name: String, sub: String, right: String, rightColor: String, onTap: (() -> Unit)?): LinearLayout {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                if (onTap != null) { isClickable = true; isFocusable = true; setOnClickListener { onTap() } }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE); setStroke(dp(1), android.graphics.Color.parseColor("#E2E8F0"))
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6); layoutParams = lp
            }
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(8), 0, dp(8), 0)
            }
            left.addView(TextView(this).apply {
                text = name.ifBlank { "—" }; textSize = 13.5f
                setTextColor(android.graphics.Color.parseColor(if (onTap != null) "#1457B8" else "#10223A"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (sub.isNotBlank()) left.addView(TextView(this).apply {
                text = sub; textSize = 11f; setTextColor(android.graphics.Color.parseColor("#64748B"))
            })
            r.addView(left)
            r.addView(TextView(this).apply {
                text = right; textSize = 13.5f; setTextColor(android.graphics.Color.parseColor(rightColor))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8), 0, dp(8), 0)
            })
            return r
        }

        lifecycleScope.launch {
            when (metric) {
                "REGISTRATION_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.patientsRegisteredList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} patients"
                    if (rows.isEmpty()) empty("No patients found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.mobile} · ${FollowUpModel.displayDate(p.registrationDate)}", "›", "#94A3B8", onTap))
                    }
                }
                "COLLECTION" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.collectionSummary(branch, from, to) to VoiceReportRepository.collectionList(branch, from, to)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total: ₹${"%,.0f".format(s.total)} · ${s.patientCount} patients · ${s.paymentCount} payments" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No payments found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.payType} · ${p.mode} · ${FollowUpModel.displayDate(p.paidOn)}", "₹${"%,.0f".format(p.amount)}", if (p.payType.equals("refund", true)) "#B42318" else "#0C8F3A", onTap))
                    }
                }
                "MEDICINE_SALE", "SALINE_SALE" -> {
                    val kind = if (metric == "MEDICINE_SALE") "medicinePayment" else "salinePayment"
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.productSaleSummary(branch, from, to, kind) to VoiceReportRepository.productSaleList(branch, from, to, kind)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total: ₹${"%,.0f".format(s.total)} · ${s.saleCount} sales" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No sales found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.customer.ifBlank { p.mobile }, "${p.product} · ${p.mode} · ${FollowUpModel.displayDate(p.soldOn)}", "₹${"%,.0f".format(p.bill)}", "#0C8F3A", onTap))
                    }
                }
                "ENQUIRY_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.enquiryList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} enquiries"
                    if (rows.isEmpty()) empty("No enquiries found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.disease} · ${FollowUpModel.displayDate(p.enquiryDate)}", "›", "#94A3B8", onTap))
                    }
                }
                "REFUND" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.refundSummary(branch, from, to) to VoiceReportRepository.refundList(branch, from, to)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total: ₹${"%,.0f".format(s.total)} · ${s.refundCount} refunds" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No refunds found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, FollowUpModel.displayDate(p.refundedOn), "₹${"%,.0f".format(p.amount)}", "#B42318", onTap))
                    }
                }
                "CASH_HANDOVER" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.cashHandoverSummary(branch, from, to) to VoiceReportRepository.cashHandoverList(branch, from, to)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total: ₹${"%,.0f".format(s.total)} · ${s.dayCount} days" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No handover found for this period.")
                    rows.forEach { p ->
                        binding.rowsHost.addView(row(FollowUpModel.displayDate(p.handoverDate), "Received by ${p.receiverName.ifBlank { "—" }}", "₹${"%,.0f".format(p.cash)}", "#0C8F3A", null))
                    }
                }
                "RMP_DUE" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.rmpDueSummary(branch) to VoiceReportRepository.rmpDueList(branch)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total due: ₹${"%,.0f".format(s.totalDue)} · ${s.rmpCount} RMP" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No due commission found.")
                    rows.forEach { p ->
                        binding.rowsHost.addView(row(p.rmpName.ifBlank { p.rmpMobile }, p.rmpMobile, "₹${"%,.0f".format(p.due)}", "#B42318", null))
                    }
                }
                else -> fail("Unknown report")
            }
            binding.progressLoad.visibility = android.view.View.GONE
        }
    }

    private fun fail(message: String) {
        binding.progressLoad.visibility = android.view.View.GONE
        binding.tvSummary.text = "Could not load this report."
        Toast.makeText(this, message.ifBlank { "Could not load this report" }, Toast.LENGTH_LONG).show()
    }

    private fun empty(text: String) {
        binding.rowsHost.addView(TextView(this).apply {
            this.text = text; textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#8A97A8"))
            setPadding(16, 32, 16, 32)
        })
    }
}
