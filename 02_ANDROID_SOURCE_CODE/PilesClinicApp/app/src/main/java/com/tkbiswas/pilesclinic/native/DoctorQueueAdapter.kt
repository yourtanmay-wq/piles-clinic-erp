package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
/* 🔴🔒 V855 (৩০.০৮.২০২৬, TK-এর Android Studio-তে ধরা পড়া বিল্ড-এরর —
   `Unresolved reference: R` :118 :122 :126)। **আমার V842-এর ভুল:** এই ফাইলটা
   `…pilesclinic.native` প্যাকেজে, তাই খালি `R` লিখলে কম্পাইলার খুঁজে পায় না —
   `com.tkbiswas.pilesclinic.R` আলাদা করে import করতে হয়।
   ⛔ পুরো প্রজেক্ট স্ক্যান করে দেখা হয়েছে — একই দোষ আর কোথাও নেই (নিয়ম ৬.২),
      আর এখন পাহারাদারও (§৯.৩৮) এটা ধরবে, তাই আর কখনো পার হতে পারবে না। */
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.databinding.ItemQueueCardBinding
import com.tkbiswas.pilesclinic.databinding.ItemQueueSectionHeaderBinding

/** TK-REQUESTED ADDITION (2026-07-20): a queue row is either a section
 *  header ("Today" / "Pending / Overdue") or an actual patient card.
 *  TK-REQUESTED (2026-07-20, follow-up): "Pending / Overdue" is collapsed
 *  by default and only its patient rows open on tapping the header --
 *  "Today" always stays open, so `collapsible` is false for it. */
sealed class QueueRow {
    data class Header(val title: String, val collapsible: Boolean = false) : QueueRow()
    data class Item(val patient: QueuePatient) : QueueRow()
}

class DoctorQueueAdapter(
    private val context: Context,
    private var items: List<QueueRow>,
    private val onCheckup: (QueuePatient) -> Unit,
    private val onFullJourney: (QueuePatient) -> Unit,
    private val onAction: (QueuePatient) -> Unit,
    // TK-REQUESTED (2026-07-28, photo proof approved): a fourth button that
    // opens this patient's Report Card. Given a default so nothing else that
    // builds this adapter has to change.
    private val onReportCard: (QueuePatient) -> Unit = {},
    private val onHeaderTap: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1

    fun updateItems(newItems: List<QueueRow>) {
        /* 🔴🔒 V509 (TK-রিপোর্ট ২১.০৮.২০২৬ — "স্ক্রিন কম্পন দিচ্ছে"):
           তালিকা আগে দুবার বসত (ফোনে জমানো, তারপর ক্লাউড)। হুবহু এক হলেও
           `notifyDataSetChanged()` প্রতিবার সব সারি নতুন করে আঁকত — চোখে
           সেটাই ঝিলিক। এখন হুবহু এক হলে আর বসানো হয় না।
           ⛔ এক চুল আলাদা হলেই আগের মতোই পুরো বসে। */
        if (items.size == newItems.size && items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemQueueCardBinding) : RecyclerView.ViewHolder(binding.root)
    inner class HeaderHolder(val binding: ItemQueueSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int =
        if (items[position] is QueueRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemQueueSectionHeaderBinding.inflate(LayoutInflater.from(context), parent, false))
        } else {
            ViewHolder(ItemQueueCardBinding.inflate(LayoutInflater.from(context), parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is QueueRow.Header -> {
                val hb = (holder as HeaderHolder).binding
                hb.tvSectionHeader.text = row.title
                // TK-REQUESTED (2026-07-20): the "Today" and "Pending / Overdue"
                // headers were faint grey text and hard to tell apart. Make them
                // clear coloured bars: Today = green, Pending/Overdue = amber,
                // bold white text. (Tap-to-collapse on the overdue one still
                // works via the click listener below.)
                val ctx = hb.tvSectionHeader.context
                val dens = ctx.resources.displayMetrics.density
                fun dp(v: Int) = (v * dens).toInt()
                val isToday = row.title.contains("Today")
                hb.tvSectionHeader.setTextColor(android.graphics.Color.WHITE)
                hb.tvSectionHeader.textSize = 15f
                hb.tvSectionHeader.setTypeface(hb.tvSectionHeader.typeface, android.graphics.Typeface.BOLD)
                hb.tvSectionHeader.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(android.graphics.Color.parseColor(if (isToday) "#0C9E33" else "#E8890C"))
                }
                hb.tvSectionHeader.setPadding(dp(16), dp(11), dp(16), dp(11))
                if (row.collapsible) {
                    hb.root.isClickable = true
                    hb.root.isFocusable = true
                    hb.root.setOnClickListener { onHeaderTap() }
                } else {
                    hb.root.isClickable = false
                    hb.root.setOnClickListener(null)
                }
            }
            is QueueRow.Item -> {
                val item = row.patient
                val b = (holder as ViewHolder).binding

                // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে মোবাইল দুইবার দেখাত।
                /* 🟢🔒 V839 (TK-নির্দেশ: *"OLD নাকি NEW — এই কথা যেন মেনশন
                   থাকে"*) — নামের পরেই ব্যাজ। নিয়ম: রেজিস্ট্রেশনের তারিখ
                   আজ হলে NEW, নইলে OLD। ⛔ তারিখ জানা না থাকলে **কিছুই**
                   দেখানো হয় না — আন্দাজে বসানো হয় না। */
                val nvpBadge = com.tkbiswas.pilesclinic.clinical.NextVisitPlan
                    .oldOrNew(item.registrationDate)
                /* 🔵🟢🔒 V842 (২৯.০৮.২০২৬, TK-নির্দেশ, ফটো-প্রুফ পাশ) —
                   *"ওয়েটিং যেখানে লেখা আছে সেটাকেই পরিবর্তন করে নিউ অথবা
                   old করুন"*
                   ⇒ V839-এ ব্যাজটা **নামের পাশে** বসত, তাতে লম্বা নাম কেটে
                     যেত (TK-এর লাইভ ছবিতে "POPI GOSWAMI  O…")। এখন লেখাটা
                     ডান দিকের ওই লাল `tvStatus` পিলেই বসে — নাম আর কাটে না।
                   ⛔ পিলের আকার · জায়গা · মাপ কিছুই বদলায়নি, শুধু লেখা ও রং।
                   ⛔ তারিখ জানা না থাকলে আগের মতোই "WAITING" (লাল) থাকে —
                      আন্দাজে NEW/OLD বসানো হয় না। */
                b.tvName.text = item.name.ifBlank { "UNKNOWN" }
                when (nvpBadge) {
                    "NEW" -> {
                        b.tvStatus.text = "NEW"
                        b.tvStatus.setBackgroundResource(R.drawable.bg_badge_new)
                    }
                    "OLD" -> {
                        b.tvStatus.text = "OLD"
                        b.tvStatus.setBackgroundResource(R.drawable.bg_badge_old)
                    }
                    else -> {
                        b.tvStatus.text = "WAITING"
                        b.tvStatus.setBackgroundResource(R.drawable.bg_badge)
                    }
                }
                /* 📞 V842 — TK: *"মোবাইল নাম্বারের আগে মোবাইল আইকন রাখার
                   দরকার নেই"* ⇒ আইকনটা বাদ, শুধু নম্বর। */
                b.tvMeta.text = item.mobile
                b.tvDiseaseBranch.text = "${item.disease.ifBlank { "-" }} · ${item.branch.ifBlank { "-" }} · ${item.patientId.ifBlank { "-" }}"
                /* 🩺🔒 V839 — NEXT VISIT PLAN-এর ট্যাগ।
                   ⛔ প্ল্যান না থাকলে **পুরোপুরি লুকানো** (TK-নির্দেশ:
                      "LAST PLAN না থাকলে যেন card থেকে হাইড হয়ে যায়") —
                      তখন কার্ড হুবহু আগের মতোই দেখায়।
                   ⛔ RecyclerView সারি পুনরায় ব্যবহার করে, তাই **দুই দিকেই**
                      (দেখানো ও লুকানো) স্পষ্ট করে বসানো হয় — নইলে অন্য
                      রোগীর ট্যাগ ভুল কার্ডে থেকে যেত। */
                if (item.nvpLine.isNotBlank()) {
                    val whenBy = listOfNotNull(
                        item.nvpWhen.ifBlank { null },
                        item.nvpBy.ifBlank { null }
                    ).joinToString(" · ")
                    b.tvNvpTag.text = "LAST PLAN: " + item.nvpLine +
                        (if (whenBy.isNotBlank()) "\n" + whenBy else "")
                    b.tvNvpTag.visibility = android.view.View.VISIBLE
                } else {
                    b.tvNvpTag.text = ""
                    b.tvNvpTag.visibility = android.view.View.GONE
                }
                // TK-REQUESTED (2026-07-18): long-press to copy name/mobile.
                b.tvName.copyOnLongPress("Name", item.name)
                b.tvMeta.copyOnLongPress("Mobile", item.mobile)
                // 🔒 B572 (08.08.2026, TK-নির্দেশ): নামে ট্যাপ করলেই ওই রোগীর History
                // (Full Journey) খোলে — 📜 History বোতামের হুবহু একই কাজ। আগে নামে
                // শুধু long-press-এ কপি হত, ট্যাপে কিছুই হত না (তাই "কিছু দেখাচ্ছে না")।
                b.tvName.setOnClickListener { onFullJourney(item) }

                val bmp = PhotoUtils.decodeDataUrl(item.photo)
                if (bmp != null) {
                    val circular = androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(b.root.resources, bmp)
                    circular.isCircular = true
                    b.ivQueuePhoto.setImageDrawable(circular)
                } else {
                    b.ivQueuePhoto.setImageDrawable(null)
                }

                // TK-APPROVED REDESIGN (2026-07-20): three buttons --
                // Full Journey (patient timeline), Check-up (doctor check-up),
                // Action (patient's Take Action menu). Summary & Print removed.
                b.btnFullJourney.setOnClickListener { onFullJourney(item) }
                b.btnCheckup.setOnClickListener { onCheckup(item) }
                b.btnAction.setOnClickListener { onAction(item) }

                // 🔴 TK-নির্দেশ (04.08.2026): "Report Card তো তখনই বানানোর
                // কথা যখন রোগী Advance করেছেন" -- Take Action/Patient
                // Timeline-এর প্রমাণিত একই নিয়ম (bill > 0) এখানেও। bill
                // না থাকলে (নতুন রোগী, এখনো Advance হয়নি) বোতাম ধূসর ও
                // চাপলে সরাসরি না-খুলে বার্তা দেখায় -- ঠিক Patient
                // Timeline-এর "No bill yet..." বার্তার হুবহু একই লেখা,
                // যাতে বোঝাটা একদম এক থাকে। ⛔ বোতাম সরানো/GONE করা হয়নি
                // (More Menu-এর ফাঁকা-গর্ত বাগ, খাতার সারি B375-এর শিক্ষা)
                // -- তাই চারটে বোতামের সারি কখনো বেঢপ হবে না।
                if (item.bill > 0.0) {
                    b.btnReportCard.alpha = 1f
                    b.btnReportCard.setOnClickListener { onReportCard(item) }
                } else {
                    b.btnReportCard.alpha = 0.45f
                    b.btnReportCard.setOnClickListener {
                        android.widget.Toast.makeText(
                            context,
                            "No bill yet for this patient — Report Card needs an Advance Payment first",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Clear Android's default button tint so the XML backgrounds show.
                b.btnFullJourney.backgroundTintList = null
                b.btnReportCard.backgroundTintList = null
                b.btnCheckup.backgroundTintList = null
                b.btnAction.backgroundTintList = null
            }
        }
    
        // 🔴🔴🔒 V449 (TK-রিপোর্ট ১৮.০৮.২০২৬ — "লগইন হয়ে যায় কিন্তু কার্যকরী কিছু হয় না")।
        // আসল কারণ: NoBengali-এর অটো-সুইপ শুধু পর্দার layout-পাসে চলে; RecyclerView
        // rebind/scroll নতুন layout-পাস তৈরি না করেই লেখা বসিয়ে দেয়, তাই তালিকার
        // সারির বাংলা কখনো ঢাকাই পড়ত না। এখন প্রতিটা বাইন্ডের শেষেই সরাসরি সুইপ —
        // বাংলা-বন্ধ না থাকলে কিছুই করে না (activeCache false ⇒ সাথে সাথে ফেরত)।
        try { NoBengali.sweep(holder.itemView) } catch (_: Throwable) { }
    }
}
