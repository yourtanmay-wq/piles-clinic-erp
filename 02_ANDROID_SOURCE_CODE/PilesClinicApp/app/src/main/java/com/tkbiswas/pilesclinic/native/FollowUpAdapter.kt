package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.databinding.ItemFollowupCardBinding

class FollowUpAdapter(
    private val context: Context,
    private var items: List<FollowUpItem>,
    private val onCall: (FollowUpItem) -> Unit,
    private val onWhatsApp: (FollowUpItem) -> Unit,
    private val onRemark: (FollowUpItem) -> Unit,
    private val onNextFollow: (FollowUpItem) -> Unit,
    private val onPayment: (FollowUpItem) -> Unit = {},
    private val onPrescription: (FollowUpItem) -> Unit = {},
    private val onView: (FollowUpItem) -> Unit = {},
    private val onStatusMenu: (FollowUpItem) -> Unit = {},
    private val onCallSignal: (FollowUpItem) -> Unit = {},
    private val onEdit: (FollowUpItem) -> Unit = {},
    private val onPhotoEdit: (FollowUpItem) -> Unit = {}
) : RecyclerView.Adapter<FollowUpAdapter.ViewHolder>() {

    private var sigTapTime = 0L
    private var sigTapCount = 0
    private var sigTapItemId = ""

    private val density = context.resources.displayMetrics.density

    fun updateItems(newItems: List<FollowUpItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemFollowupCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFollowupCardBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    /** Rounded signal bar, matching the WebView .signalBars pills. */
    private fun barDrawable(on: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 3f * density
        setColor(Color.parseColor(if (on) "#0FA83B" else "#D8E4F2"))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        try {

        // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে মোবাইল দুইবার দেখাত।
        b.tvName.text = "👤 " + item.name.ifBlank { "UNKNOWN" }
        b.tvMobile.text = "📞 ${formatMobileForDisplay(item.mobile)}"
        b.tvMobile.setOnLongClickListener {
            val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("mobile", item.mobile))
            android.widget.Toast.makeText(it.context, "Mobile copied", android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        b.tvBranch.text = item.branch.ifBlank { "-" }.uppercase()
        b.tvDisease.text = item.disease.ifBlank { "-" }.uppercase()
        // TK-APPROVED (2026-07-25, via photo proof): label removed -- the
        // box's own light-green color now makes it recognizable at a
        // glance, applied consistently project-wide.
        // TK-REPORTED (2026-07-26): Visit/Patient cards were still showing the
        // internal placeholder "Registered (syncing…)". That text was only ever
        // meant to be a few-seconds transient state, but rows saved with it in
        // an older build keep showing it forever. It is now replaced at DISPLAY
        // time with the plain sentence the app itself writes for such a row, so
        // nothing in the database is touched and no other remark is affected.
        val shownRemark = item.lastRemark.trim()
        val syncingPlaceholder = shownRemark.startsWith("Registered (syncing", ignoreCase = true)
        b.tvRemark.text = when {
            syncingPlaceholder -> "Registered patient / Visit created"
            shownRemark.isBlank() -> "No remark"
            else -> shownRemark
        }

        val regDate = if (item.recordDate.isNotBlank()) FollowUpModel.displayDate(item.recordDate) else ""

        // Left side + right slot, mapped exactly like the WebView fuCard:
        //   Enquiry (Inquiry)  -> LEFT call-signal      | RIGHT Today/Overdue/Xd badge
        //   Visit   (Patient)  -> LEFT avatar+VISITED   | RIGHT 👣 Visit / Advance pill
        //   Patient (Treatment)-> LEFT avatar+PATIENT   | RIGHT Prescription + payment ring
        when (item.stage) {
            "Inquiry" -> {
                b.llCallSignal.visibility = View.VISIBLE
                b.llPhoto.visibility = View.GONE
                b.tvVisitAdvance.visibility = View.GONE
                b.llPayment.visibility = View.GONE

                val n = item.callCount.coerceIn(0, 5)
                val wifiDrawable = when (n) {
                    0 -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_0
                    1 -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_1
                    2 -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_2
                    3 -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_3
                    4 -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_4
                    else -> com.tkbiswas.pilesclinic.R.drawable.ic_wifi_calls_5
                }
                b.ivWifiSignal.setImageResource(wifiDrawable)
                b.ivWifiSignal.contentDescription = "Enquiry calls: $n of 5"
                b.tvSignalDate.text = regDate
                // TK-REQUESTED ADDITION (2026-07-23): small, clear time-type
                // badge on the Enquiry card -- green "Official Time" or purple
                // "Unexpected Time". Hidden when the row has no timeType (older
                // rows not yet backfilled), so nothing looks broken.
                val tt = item.timeType.trim()
                when {
                    tt.equals("Unexpected Time", ignoreCase = true) -> {
                        b.tvTimeType.text = "🌙 Unexpected Time"
                        b.tvTimeType.setBackgroundColor(android.graphics.Color.parseColor("#6A4C93"))
                        b.tvTimeType.visibility = View.VISIBLE
                    }
                    tt.equals("Official Time", ignoreCase = true) -> {
                        b.tvTimeType.text = "🕘 Official Time"
                        b.tvTimeType.setBackgroundColor(android.graphics.Color.parseColor("#0C9E33"))
                        b.tvTimeType.visibility = View.VISIBLE
                    }
                    else -> b.tvTimeType.visibility = View.GONE
                }
                // Final lock: triple-tap the "Enquiry" status text for Continue / Reject.
                TripleTapEdit.attach(b.tvSignalLabel) { onStatusMenu(item) }
            }
            "Patient" -> { // Visit tab
                b.llCallSignal.visibility = View.GONE
                b.llPhoto.visibility = View.VISIBLE
                b.tvVisitAdvance.visibility = View.VISIBLE
                b.llPayment.visibility = View.GONE
                // TK-REQUESTED (2026-07-23): time-type badge is Enquiry-only;
                // hide it here so a recycled view can't carry it over.
                b.tvTimeType.visibility = View.GONE

                b.tvVisitedPill.visibility = View.VISIBLE
                b.tvVisitedPill.text = "VISITED"
                b.tvRegDate.text = item.patientId.ifBlank { regDate }
                b.tvVisitAdvance.setOnClickListener { onPayment(item) }
                TripleTapEdit.attach(b.tvAvatar) { onPhotoEdit(item) }
                TripleTapEdit.attach(b.tvVisitedPill) { onStatusMenu(item) }
                TripleTapEdit.attach(b.tvRegDate) { onEdit(item) }
            }
            else -> { // Treatment -> Patient tab
                b.llCallSignal.visibility = View.GONE
                b.llPhoto.visibility = View.VISIBLE
                b.tvVisitAdvance.visibility = View.GONE
                b.llPayment.visibility = View.VISIBLE
                // TK-REQUESTED (2026-07-23): time-type badge is Enquiry-only.
                b.tvTimeType.visibility = View.GONE

                b.tvVisitedPill.visibility = View.VISIBLE
                b.tvVisitedPill.text = "PATIENT"
                b.tvRegDate.text = item.patientId.ifBlank { regDate }
                TripleTapEdit.attach(b.tvAvatar) { onPhotoEdit(item) }
                TripleTapEdit.attach(b.tvVisitedPill) { onStatusMenu(item) }
                TripleTapEdit.attach(b.tvRegDate) { onEdit(item) }

                val pct = if (item.bill > 0) Math.min(100.0, Math.round(item.paid / item.bill * 100.0).toDouble()).toInt() else 0
                b.tvPayPct.text = "$pct%"
                val due = Math.max(0.0, item.bill - item.paid)
                b.tvBillDue.text = if (item.bill > 0)
                    "₹${"%,.0f".format(item.bill)}\n/ ₹${"%,.0f".format(due)}"
                else "0 / 0"
                b.tvPayPct.setOnClickListener { onPayment(item) }
                b.tvPrescription.setOnClickListener { onPrescription(item) }
                TripleTapEdit.attach(b.tvBillDue) { onPayment(item) }
            }
        }

        // Badge: only on the Enquiry (Inquiry) tab — Today Due / Overdue / X-Days,
        // matching the WebView badge logic.
        val days = FollowUpModel.daysUntil(item.nextFollow)
        if (item.stage != "Inquiry" || days == null) {
            b.tvBadge.visibility = View.GONE
        } else {
            val d: Int = days
            when {
                d < 0 -> setBadge(b, "⏰ Overdue", "#E5484D")
                // 🔒 খাতার সারি B202 (30.07.2026 রাত): FollowUpActivity.kt-এর
                // হুবহু একই ফিক্স — ⏰ ইমোজির ভিতরে ফোনের ফন্ট নিজে থেকে "১৭"
                // এঁকে রাখে (আসল তথ্য নয়), তাই ⏰-তে বদলানো হলো। এই ফাইলটা এই
                // মুহূর্তে প্রজেক্টের কোথাও ব্যবহার হয় না (dead code), তবু
                // ভবিষ্যতের নিরাপত্তার জন্য একইসঙ্গে ঠিক করা হলো।
                d == 0 -> setBadge(b, "⏰ Today Due", "#E5484D")
                else -> setBadge(b, "⏰ ${d}d Due", "#F79009")
            }
        }

        val nextLabel = if (item.stage == "Inquiry") "Next Follow up Call" else "Next Follow-up"
        b.tvNextFollow.text = if (item.nextFollow.isNotBlank())
            "$nextLabel: ${FollowUpModel.displayDate(item.nextFollow)}" else ""

        b.btnCall.setOnClickListener { onCall(item) }
        b.btnWhatsApp.setOnClickListener { onWhatsApp(item) }
        b.btnRemark.setOnClickListener { onView(item) }
        b.tvRemark.setOnClickListener { onRemark(item) }
        b.btnNextFollow.setOnClickListener { onNextFollow(item) }
        // Final correction rule: any displayed patient detail can be corrected by 3 quick taps.
        TripleTapEdit.attach(b.tvName) { onEdit(item) }
        TripleTapEdit.attach(b.tvMobile) { onEdit(item) }
        TripleTapEdit.attach(b.tvBranch) { onEdit(item) }
        TripleTapEdit.attach(b.tvDisease) { onEdit(item) }
        } catch (e: Exception) { }
    
        // 🔴🔴🔒 V449 (TK-রিপোর্ট ১৮.০৮.২০২৬ — "লগইন হয়ে যায় কিন্তু কার্যকরী কিছু হয় না")।
        // আসল কারণ: NoBengali-এর অটো-সুইপ শুধু পর্দার layout-পাসে চলে; RecyclerView
        // rebind/scroll নতুন layout-পাস তৈরি না করেই লেখা বসিয়ে দেয়, তাই তালিকার
        // সারির বাংলা কখনো ঢাকাই পড়ত না। এখন প্রতিটা বাইন্ডের শেষেই সরাসরি সুইপ —
        // বাংলা-বন্ধ না থাকলে কিছুই করে না (activeCache false ⇒ সাথে সাথে ফেরত)।
        try { NoBengali.sweep(holder.itemView) } catch (_: Throwable) { }
    }

    /** Display-only formatting. Stored number and Call/WhatsApp logic remain unchanged. */
    private fun formatMobileForDisplay(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            raw.trim().startsWith("+") -> raw.trim()
            else -> raw.trim()
        }
    }

    private fun setBadge(b: ItemFollowupCardBinding, text: String, colorHex: String) {
        b.tvBadge.visibility = View.VISIBLE
        b.tvBadge.text = text
        val drawable = GradientDrawable().apply {
            cornerRadius = 8f * density
            setColor(Color.parseColor(colorHex))
        }
        b.tvBadge.background = drawable
    }
}
