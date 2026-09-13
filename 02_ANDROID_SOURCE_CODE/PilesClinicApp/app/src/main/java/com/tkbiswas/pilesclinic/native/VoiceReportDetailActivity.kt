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
                "MEDICINE_DUE" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.productDueSummary(branch) to VoiceReportRepository.productDueList(branch)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total due: ₹${"%,.0f".format(s.total)} · ${s.rowCount} bills" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No medicine/saline due found.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.customer.ifBlank { p.mobile }, "${p.product} · ${FollowUpModel.displayDate(p.soldOn)}", "₹${"%,.0f".format(p.due)}", "#B42318", onTap))
                    }
                }
                "CALL_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.callList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} calls from app"
                    if (rows.isEmpty()) empty("No app calls found for this period.")
                    rows.forEach { p ->
                        binding.rowsHost.addView(row(p.staffCode, "${p.targetMobileMask} · ${FollowUpModel.displayDate(p.callDate)}", "›", "#94A3B8", null))
                    }
                }
                "TRASH_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.trashList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} records in Trash"
                    if (rows.isEmpty()) empty("No deleted records found for this period.")
                    rows.forEach { p ->
                        binding.rowsHost.addView(row(p.tableName.ifBlank { "record" }, "${FollowUpModel.displayDate(p.deletedAt.take(10))} · by ${p.deletedBy.ifBlank { "—" }}", "›", "#94A3B8", null))
                    }
                }
                "RMP_ADVANCE" -> {
                    val (sum, list) = withContext(Dispatchers.IO) {
                        VoiceReportRepository.rmpAdvanceSummary(branch, from, to) to VoiceReportRepository.rmpAdvanceList(branch, from, to)
                    }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Total: ₹${"%,.0f".format(s.total)} · ${s.advanceCount} advance payments" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No RMP advance found for this period.")
                    rows.forEach { p ->
                        binding.rowsHost.addView(row(p.rmpName, "${p.mode} · ${FollowUpModel.displayDate(p.paidOn)}", "₹${"%,.0f".format(p.amount)}", "#0C8F3A", null))
                    }
                }
                // ── V1420 ──
                "APPOINTMENT_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.appointmentList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} appointments"
                    if (rows.isEmpty()) empty("No appointments found for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.disease} · ${FollowUpModel.displayDate(p.appointmentDate)}${if (p.registered) " · already registered" else ""}", "›", "#94A3B8", onTap))
                    }
                }
                "EXPECTED_COUNT" -> {
                    val got = withContext(Dispatchers.IO) { VoiceReportRepository.expectedList(branch, from, to) }
                    if (!got.ok) { fail(got.message); return@launch }
                    val rows = got.value ?: emptyList()
                    binding.tvSummary.text = "Total: ${rows.size} marked expected"
                    if (rows.isEmpty()) empty("Nobody marked expected for this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.mobile} · ${FollowUpModel.displayDate(p.expectedOn)}", "›", "#94A3B8", onTap))
                    }
                }
                "HANDOVER_PENDING" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.handoverPendingSummary(branch) to VoiceReportRepository.handoverPendingList(branch) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Not handed over: ₹${"%,.0f".format(s.total)} · ${s.dayCount} days" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No pending handover.")
                    rows.forEach { p -> binding.rowsHost.addView(row(FollowUpModel.displayDate(p.handoverDate), p.status, "₹${"%,.0f".format(p.cash)}", "#B42318", null)) }
                }
                "PAYMENT_REQUESTS" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.paymentRequestsSummary(branch) to VoiceReportRepository.paymentRequestsList(branch) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Pending: ${s.total} (${s.backdate} backdate · ${s.edit} edit · ${s.refund} refund)" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No pending payment requests.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, "${p.requestType} · ${FollowUpModel.displayDate(p.requestedOn)}", "₹${"%,.0f".format(p.amount)}", "#B45309", onTap))
                    }
                }
                "REFERRAL_REQUESTS" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.referralRequestsSummary(branch) to VoiceReportRepository.referralRequestsList(branch) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Pending: ${s.total} (${s.deleteCount} delete)" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No pending referral requests.")
                    rows.forEach { p -> binding.rowsHost.addView(row(p.requestType, FollowUpModel.displayDate(p.requestedOn), if (p.requestType == "Delete") "—" else "₹${"%,.0f".format(p.newAmount)}", "#B45309", null)) }
                }
                "LEAVE_COUNT" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.leaveSummary(branch, from, to) to VoiceReportRepository.leaveList(branch, from, to) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Leave-days applied: ${s.total} · ${s.confirmed} confirmed · ${s.pending} pending · ${s.rejected} rejected" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No leave applications for this period.")
                    rows.forEach { p -> binding.rowsHost.addView(row(p.staffCode, "Leave ${FollowUpModel.displayDate(p.leaveDate)} · applied ${FollowUpModel.displayDate(p.appliedOn)}", p.status, if (p.status == "confirmed") "#0C8F3A" else if (p.status == "rejected") "#B42318" else "#B45309", null)) }
                }
                "DOCTOR_REMINDER" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.doctorReminderSummary(branch, from, to) to VoiceReportRepository.doctorReminderList(branch, from, to) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Sent: ${s.total} · ${s.notAccepted} not accepted yet" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No doctor reminders for this period.")
                    rows.forEach { p -> binding.rowsHost.addView(row("Remind on ${FollowUpModel.displayDate(p.remindDate)}", "sent ${FollowUpModel.displayDate(p.createdOn)}", if (p.cancelled) "cancelled" else if (p.accepted) "accepted" else "open", if (p.cancelled) "#94A3B8" else if (p.accepted) "#0C8F3A" else "#B45309", null)) }
                }
                "STAFF_REMINDER_OPEN" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.staffReminderOpenSummary(branch) to VoiceReportRepository.staffReminderOpenList(branch) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    binding.tvSummary.text = "Open: ${sum.value ?: 0}"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No open staff reminders.")
                    rows.forEach { p -> binding.rowsHost.addView(row(p.toName.ifBlank { p.toCode }, "${p.reminderType} · ${FollowUpModel.displayDate(p.remindOn)}", p.status, "#B45309", null)) }
                }
                "FEE_RETURN" -> {
                    val (sum, list) = withContext(Dispatchers.IO) { VoiceReportRepository.feeReturnSummary(branch, from, to) to VoiceReportRepository.feeReturnList(branch, from, to) }
                    if (!sum.ok) { fail(sum.message); return@launch }
                    val s = sum.value
                    binding.tvSummary.text = if (s != null) "Visit fee returned: ₹${"%,.0f".format(s.total)} · ${s.patientCount} patients" else "Total: —"
                    val rows = if (list.ok) list.value ?: emptyList() else emptyList()
                    if (rows.isEmpty()) empty("No visit fee returned in this period.")
                    rows.forEach { p ->
                        val onTap: (() -> Unit)? = if (p.mobile.filter { it.isDigit() }.takeLast(10).length == 10) { { startActivity(android.content.Intent(this@VoiceReportDetailActivity, PatientTimelineActivity::class.java).putExtra("mobile", p.mobile)) } } else null
                        binding.rowsHost.addView(row(p.name.ifBlank { p.mobile }, FollowUpModel.displayDate(p.returnedOn), "₹${"%,.0f".format(p.amount)}", "#B42318", onTap))
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
