package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.databinding.ItemTimelineBinding

/** TK-REQUESTED REDESIGN (2026-07-16): renders the register-style table row
 *  (Visit# / Date / Progress / Paid / Due) TK approved, replacing the old
 *  spacious card. Edit behaviour is unchanged: 3-tap still only fires for
 *  entries that carry a real payment id (see onBindViewHolder). */
class TimelineAdapter(
    private val context: Context,
    private var items: List<TimelineEntry>,
    // TK APPROVED (2026-07-15): standing rule — 3-tap must edit everything.
    // Only fires for entries that carry a real payment id (see onBindViewHolder).
    private val onPaymentEdit: (TimelineEntry) -> Unit = {}
) : RecyclerView.Adapter<TimelineAdapter.VH>() {

    fun update(newItems: List<TimelineEntry>) {
        /* 🔴🔒 V509 (TK-রিপোর্ট ২১.০৮.২০২৬ — "স্ক্রিন কম্পন দিচ্ছে"):
           এই তালিকা আগে **দুবার** বসত (প্রথমে ফোনে জমানো, তারপর ক্লাউড
           থেকে এসে আবার)। `notifyDataSetChanged()` প্রতিবার দেখা-যাওয়া
           সব সারি নতুন করে আঁকে — বেশিরভাগ সময় তথ্য হুবহু এক হলেও।
           চোখে সেটাই **ঝিলিক**। এখন হুবহু এক হলে আর বসানো হয় না।
           ⛔ এক চুল আলাদা হলেই আগের মতোই পুরো বসে — কিছু চাপা পড়ে না।
           ⛔ `data class` বলে তুলনাটা তথ্যের ভিত্তিতেই হয়, ঠিকানার নয়। */
        if (items.size == newItems.size && items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemTimelineBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemTimelineBinding.inflate(LayoutInflater.from(context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    private fun displayDate(raw: String): String =
        if (raw.length >= 10) FollowUpModel.displayDate(raw.take(10)) else raw

    private fun money(v: Double): String = "\u20B9" + "%,.0f".format(v)

    override fun onBindViewHolder(holder: VH, position: Int) {
        // CRASH-SAFETY FIX (TK-reported via video, 2026-07-16): RecyclerView
        // renders each card on its own pass, outside the Activity's
        // try-catch -- so a bad entry here could still crash the app even
        // with PatientTimelineActivity's own safety net. A failed row is now
        // just skipped (blank), the rest of the list still shows.
        try {
            val e = items[position]
            val b = holder.binding

            b.rowRoot.setBackgroundColor(Color.parseColor(if (position % 2 == 1) "#F6FAF7" else "#FFFFFF"))

            b.tvVisitNo.text = e.visitNo.toString()
            b.tvDate.text = displayDate(e.date)
            b.tvTitle.text = e.title
            if (e.note.isNotBlank()) {
                b.tvNote.text = e.note
                b.tvNote.visibility = View.VISIBLE
            } else {
                b.tvNote.visibility = View.GONE
            }

            if (e.paymentId != null) {
                b.tvPaid.text = money(e.paymentAmount)
                b.tvDue.text = if (e.runningDue < 0) "\u2014" else money(e.runningDue)
                TripleTapEdit.attach(b.rowRoot) { onPaymentEdit(e) }
            } else {
                b.tvPaid.text = "\u2014"
                b.tvDue.text = "\u2014"
                b.rowRoot.setOnClickListener(null)
            }
        } catch (_: Throwable) { }
    }
}
