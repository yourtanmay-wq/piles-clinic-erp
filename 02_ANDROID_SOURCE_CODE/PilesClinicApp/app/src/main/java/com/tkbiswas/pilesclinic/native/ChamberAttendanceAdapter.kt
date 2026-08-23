package com.tkbiswas.pilesclinic.native

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.databinding.ItemChamberAttendanceBinding

/**
 * TK-REQUESTED REDESIGN (2026-07-19): Chamber Date row, rebuilt to match
 * TK's own paper attendance register — SL | Patient Details (Name+Mobile)
 * | Status | Fees | Cash | Online, one plain table row per patient. The
 * whole row is a single tap to open that patient's Patient Timeline
 * ("Report Card") — Payment/Treatment/Action all live there now (that
 * screen already has Call/WhatsApp/Payment/Action buttons), not duplicated
 * in this list anymore, per TK's explicit instruction ("নোটবুকের মতোই
 * হবে, শুধুমাত্র ডিজিটাল").
 */
class ChamberAttendanceAdapter(
    private var items: List<ChamberAttendanceRow>,
    private var showQuickActions: Boolean,
    // TK APPROVED (2026-07-16): "call-ahead" -- when viewing a FUTURE date,
    // showQuickActions is false (no backdating a Payment/Remark), but the
    // CALL icon should still show for that date's Expected list so staff
    // can ring them today to confirm. Two separate flags rather than
    // overloading showQuickActions, so today/past-view behavior is
    // untouched.
    private var showCallAhead: Boolean = false,
    private val onOpenTimeline: (ChamberAttendanceRow) -> Unit,
    // TK-REQUESTED CHANGE (2026-07-19): Payment/Treatment/Clinical no
    // longer have their own buttons on this row (moved to the Report Card
    // screen itself) -- these three are kept as unused parameters only so
    // every existing call site in ChamberAttendanceActivity.kt still
    // compiles without also having to change there; harmless no-ops here.
    private val onAddPayment: (ChamberAttendanceRow) -> Unit = {},
    private val onAddRemark: (ChamberAttendanceRow) -> Unit = {},
    private val onCall: (ChamberAttendanceRow) -> Unit = {},
    private val onClinical: (ChamberAttendanceRow) -> Unit = {},
    // TK-APPROVED (2026-07-20): tappable Cash / Online / Treatment cells and
    // an "✅ এসেছেন" button on Expected rows.
    private val onCashTap: (ChamberAttendanceRow) -> Unit = {},
    private val onOnlineTap: (ChamberAttendanceRow) -> Unit = {},
    private val onTreatmentTap: (ChamberAttendanceRow) -> Unit = {},
    private val onMarkArrived: (ChamberAttendanceRow) -> Unit = {},
    // TK-DECISION (2026-07-22): long-press an "আসার কথা" (waiting) row to
    // cancel / reschedule it with a reason. Default no-op so existing call
    // sites keep compiling.
    private val onCancelExpected: (ChamberAttendanceRow) -> Unit = {}
) : RecyclerView.Adapter<ChamberAttendanceAdapter.VH>() {

    // TK-REPORTED BUG FIX (2026-07-19): this used to always be thrown away
    // and rebuilt as a brand-new adapter on every refresh (loadBoard()),
    // which resets RecyclerView's scroll position to the very top every
    // time -- so returning from Payment/Registration/Timeline (or even a
    // background refresh) silently jumped the staff back to the top of the
    // list instead of leaving them where they were. update() now also
    // accepts the two flags (they can change when the date-picker changes
    // which day is being viewed) so the SAME adapter instance can be reused
    // for every refresh instead of being recreated.
    fun update(newItems: List<ChamberAttendanceRow>, quickActions: Boolean = showQuickActions, callAhead: Boolean = showCallAhead) {
        /* 🔴🔒 V509 (TK-রিপোর্ট — "স্ক্রিন কম্পন দিচ্ছে"): এই বোর্ড আগে দুবার
           বসত (ফোনে জমানো, তারপর ক্লাউড) — হুবহু এক হলেও প্রতিবার সব সারি
           নতুন করে আঁকা হতো, চোখে ঝিলিক লাগত।
           ⛔ **দুটো সুইচও (quickActions/callAhead) তুলনায় ধরা আছে**, তাই
              তারিখ বদলালে বা view-only হলে আগের মতোই পুরো বসে। */
        if (items.size == newItems.size && items == newItems &&
            showQuickActions == quickActions && showCallAhead == callAhead) return
        items = newItems
        showQuickActions = quickActions
        showCallAhead = callAhead
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemChamberAttendanceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChamberAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    private fun money(v: Double): String = if (v > 0.0) "₹" + "%,.0f".format(v) else "—"

    override fun onBindViewHolder(holder: VH, position: Int) {
        // CRASH-SAFETY FIX (TK-reported via video, 2026-07-16): same as
        // TimelineAdapter -- RecyclerView renders outside the Activity's
        // try-catch. A bad row is now skipped instead of crashing the app.
        try {
            val row = items[position]
            val b = holder.b

            // TK-LOCKED (2026-07-25, photo proof): name in CAPITALS, no call
            // icon before the mobile, no "ID:" prefix before the patient id .
            // the three lines are already unmistakable without them, and the
            // saved space lets the full ID fit.
            // 🔴🔴 TK-REPORTED (31.07.2026): নাম না থাকলে এখানে মোবাইল বসত,
            // ঠিক নিচের লাইনেও (tvMobile) মোবাইল — দুইবার দেখাত। এখন নাম
            // না থাকলে "UNKNOWN"।
            b.tvName.text = row.name.ifBlank { "UNKNOWN" }.uppercase()
            b.tvMobile.text = row.mobile
            // 🔴🔒 V471 (20.08.2026, TK-অনুমোদিত) — রেফারিং RMP-র নাম (থাকলেই)
            // Patient ID-এর নিচে, একই ঘরে নতুন লাইনে — নতুন কোনো XML view
            // যোগ করা হয়নি (ঝুঁকি কমাতে), শুধু এই একটা টেক্সট-ঘরে জুড়ে দেওয়া।
            /* 🟢🔒 V588 (23.08.2026, TK-নির্দেশ, ছবিসহ) — *"পেশেন্টের নাম · তার
               নিচে মোবাইল নাম্বার · তার নিচে আইডি — আমি এখানে টাইমটাও রাখতে
               চাইছি · তারিখ এবং সময় · পেশেন্ট আইডি না থাকলেও চলবে এখানে"*
               ⇒ তৃতীয় লাইনে Patient ID-র জায়গায় এখন **এই রোগী আজ কখন এসেছেন**
               (তারিখ ও সময়)। ⛔ সময়টা নতুন করে বানানো হয়নি — `arrivedAt` ঘরটা
               ১৯.০৭.২০২৬ থেকেই সারিতে আছে (ছাপা কাগজ ও Review ওটা ধরেই সাজে)।
               ⛔ Patient ID মোছা হয়নি — Report Card · Full Journey · ছাপা
                  রেজিস্টার সব জায়গায় আগের মতোই আছে, শুধু এই সারিতে দেখানো হয় না
                  (TK-এর নিজের কথা: "না থাকলেও চলবে")। লম্বা চাপে কপি করার
                  ব্যবস্থাটাও তাই ID-ই কপি করে, আগের মতোই।
               ⛔ RMP-র নাম (V471) আগের মতোই একই ঘরে নিচের লাইনে।
               ⛔ সময় জানা না থাকলে (পুরনো সারি) লাইনটা আগের মতোই লুকিয়ে যায়। */
            val whenV = DateUtil.displayWithTime(row.arrivedAt.ifBlank { null })
            val pidTextV = listOfNotNull(
                whenV.ifBlank { null },
                row.refDoctor.ifBlank { null }?.let { "👨‍⚕️ $it" }
            ).joinToString("\n")
            b.tvPatientId.text = pidTextV
            b.tvPatientId.visibility = if (pidTextV.isNotBlank()) View.VISIBLE else View.GONE
            // TK-LOCKED (2026-07-25): three separate columns again.
            // FEES = the registration fee taken today. A patient who did NOT
            // register today (an old patient coming back . the common case)
            // has no fee, so the box shows "OLD" instead of a dash, exactly
            // as TK asked. CASH / ONLINE = today's treatment payments.
            val feeToday = row.feesCash + row.feesOnline
            b.tvFees.text = if (feeToday > 0.0) money(feeToday) else "OLD"
            b.tvFees.setTextColor(android.graphics.Color.parseColor(if (feeToday > 0.0) "#334155" else "#8A97AB"))
            b.tvCash.text = money(row.paymentCash)
            b.tvOnline.text = money(row.paymentOnline)

            // TK-APPROVED (2026-07-20): Treatment Progress text (what happened
            // today). Tap this cell to write/edit it. Orange "—" when empty.
            val treatment = row.remark.trim()
            // 🔴 V430 (TK-নির্দেশ 04.08.2026, ছবিসহ — "Registered patient / Visit
            // created" আসল লেখার মতোই দেখাচ্ছিল, যদিও ওটা Registration/Visit
            // তৈরির সময় সিস্টেম নিজে বসায়, স্টাফ কিছু লেখেননি)। ওই নির্দেশটা
            // Review পর্দায় বসানো হয়েছিল, কিন্তু **এই বোর্ডের ঘরে বসানো
            // হয়নি** — ওয়েবে বসেছিল, তাই দুই জায়গা আলাদা দেখাচ্ছিল।
            // ⛔ ডেটাবেসে কিছু বদলায় না — শুধু এই একটা ঘরের দেখানোর লেখা ও রং।
            val isAutoStub = treatment.equals("Registered patient / Visit created", ignoreCase = true)
            b.tvTreatment.text = when {
                isAutoStub -> NoBengali.s("কিছু লেখা হয়নি — চাপুন")
                treatment.isNotBlank() -> treatment
                else -> "—"
            }
            b.tvTreatment.setTextColor(android.graphics.Color.parseColor(
                if (treatment.isNotBlank() && !isAutoStub) "#334155" else "#C47B00"))

            // TK-APPROVED (2026-07-20): status shown by ROW FILL COLOUR (no
            // tick, no "এসেছেন" text): green = arrived, yellow = expected
            // (আসার কথা), red = not arrived / enquiry.
            val bg = when {
                row.arrived -> "#E9F8F0"
                row.expected -> "#FFF9E6"
                else -> "#FDEEEE"
            }
            b.rowContent.setBackgroundColor(android.graphics.Color.parseColor(bg))

            // TK-REQUESTED (2026-07-21): a patient who is still "আসার কথা"
            // (expected, NOT arrived yet) has no Payment/Visit/Treatment yet,
            // so those columns are meaningless. Such a row shows ONE compact
            // line -- Name · Mobile · Disease -- with a "→" arrow that marks
            // them Arrived (moves them to the Arrived section). An arrived
            // patient keeps the normal money grid exactly as before.
            val waiting = row.expected && !row.arrived
            val dz = row.disease.trim()

            b.cellPatient.visibility = if (waiting) View.GONE else View.VISIBLE
            b.rowWaiting.visibility = if (waiting) View.VISIBLE else View.GONE
            val moneyVis = if (waiting) View.GONE else View.VISIBLE
            b.tvTreatment.visibility = moneyVis
            b.tvFees.visibility = moneyVis
            b.tvCash.visibility = moneyVis
            b.tvOnline.visibility = moneyVis
            b.tvDisease.visibility = View.GONE

            // "→" arrow only for the still-expected (not yet arrived) rows.
            b.btnArrived.visibility = if (waiting) View.VISIBLE else View.GONE
            b.btnArrived.setOnClickListener { onMarkArrived(row) }

            // TK-LOCKED (2026-07-24): bordered Patient box (Name/Mobile/ID)
            // + bordered Treatment Progress box (last remark, tap-editable).
            // 🔴🔴 TK-REPORTED (31.07.2026): একই ফিক্স — নাম না থাকলে মোবাইল দুইবার দেখাত।
            b.tvNameW.text = row.name.ifBlank { "UNKNOWN" }.uppercase()
            b.tvMobileW.text = row.mobile
            // 🔴🔒 V471 (20.08.2026, TK-অনুমোদিত) — Wide-লেআউটেও একই যোগ
            // (উপরের tvPatientId-এর হুবহু একই যুক্তি)।
            // 🟢🔒 V588 — Wide-লেআউটেও একই (উপরের ঘরটার হুবহু একই যুক্তি)।
            val pidTextVW = listOfNotNull(
                whenV.ifBlank { null },
                row.refDoctor.ifBlank { null }?.let { "👨‍⚕️ $it" }
            ).joinToString("\n")
            b.tvPatientIdW.text = pidTextVW
            b.tvPatientIdW.visibility = if (pidTextVW.isNotBlank()) View.VISIBLE else View.GONE
            val note = row.remark.trim()
            b.tvTreatmentW.text = if (note.isNotBlank()) note else "—"
            b.tvTreatmentW.setTextColor(android.graphics.Color.parseColor(if (note.isNotBlank()) "#334155" else "#C47B00"))
            b.tvDiseaseW.visibility = View.GONE

            // TK-APPROVED (2026-07-20): Cash / Online cell -> take/edit that
            // patient's cash-only / online-only payment; Treatment cell ->
            // write today's treatment. Patient area -> open Report Card;
            // mobile -> call.
            // TK-APPROVED (2026-07-20): empty cell = ONE tap to take payment;
            // once paid the cell is "locked" and needs THREE taps to edit
            // (same 3-tap safety used everywhere for money). The activity
            // handler decides take-vs-edit from the current amount.
            // TK-REQUESTED (2026-07-24): Fees/Cash/Online merged into one
            // "Payment" box (see section 160) -- tap opens the SAME shared
            // Payment window every other screen uses (onAddPayment, already
            // existed as a dormant no-op parameter since 2026-07-19). No new
            // save/edit logic written here -- just reconnected an existing,
            // already-tested action to this box.
            // TK-LOCKED (2026-07-25): CASH box -> take a cash payment,
            // ONLINE box -> take an online payment (both use the same
            // already-proven takeOrEditPayment flow). FEES is display only,
            // so it has no tap at all . it fills itself from Registration.
            b.tvCash.setOnClickListener { onCashTap(row) }
            b.tvOnline.setOnClickListener { onOnlineTap(row) }
            b.tvFees.setOnClickListener(null)
            b.tvFees.isClickable = false
            b.tvTreatment.setOnClickListener { onTreatmentTap(row) }
            b.tvMobile.setOnClickListener { onCall(row) }
            // TK-REQUESTED (2026-07-22): tapping empty space in the Patient
            // details box (Name/Mobile/ID) now opens the "next step" action
            // menu (Prescription / Medicine Slip / Blood Test / Full
            // History / etc.) instead of jumping straight to the Timeline.
            // Mobile itself keeps its own tap=call / long-press=copy above.
            b.cellPatient.setOnClickListener { onClinical(row) }
            // V364: every non-phone part of the Patient box opens the same
            // approved two-choice menu. Name used to keep the touch because
            // it also had long-press Copy; give its normal tap explicitly.
            b.tvName.setOnClickListener { onClinical(row) }
            b.tvPatientId.setOnClickListener { onClinical(row) }

            b.tvName.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("name", row.name))
                android.widget.Toast.makeText(it.context, "Name copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            b.tvMobile.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mobile", row.mobile))
                android.widget.Toast.makeText(it.context, "Mobile copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            b.tvPatientId.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("patient id", row.patientId))
                android.widget.Toast.makeText(it.context, "Patient ID copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            b.cellPatient.setOnLongClickListener {
                val details = listOf(row.name, row.mobile, row.patientId).filter { it.isNotBlank() }.joinToString("\n")
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("patient", details))
                android.widget.Toast.makeText(it.context, "Patient details copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            // TK-REQUESTED (2026-07-21): same behaviour on the compact waiting
            // line -- tap mobile = call, long-press mobile = copy, tap the rest
            // of the row = open Timeline, long-press name = copy name.
            // 🚨 TK-REPORTED (2026-07-28): "আসার কথা" সারির রোগীর ঘরে চাপলে
            // সোজা Full Journey খুলে যেত। TK-এর নিয়ম (25.07.2026-এ লক করা):
            // রোগীর ঘরে চাপলে আগে **দুটো অপশন** আসবে — Patient Details ও
            // Report Card। "এসেছেন" সারিতে ওটা আগে থেকেই ছিল, শুধু এই সারিতে
            // ছিল না। এখন দুই সারিতেই এক নিয়ম।
            // ⛔ লম্বা চাপ দিয়ে "আসার কথা" বাতিল করার ব্যবস্থা আগের মতোই আছে।
            b.rowWaiting.setOnClickListener { onClinical(row) }
            b.tvNameW.setOnClickListener { onClinical(row) }
            b.tvPatientIdW.setOnClickListener { onClinical(row) }
            // TK-DECISION (2026-07-22): long-press this "আসার কথা" row to
            // cancel / reschedule it (with a reason).
            b.rowWaiting.setOnLongClickListener { onCancelExpected(row); true }
            b.tvMobileW.setOnClickListener { onCall(row) }
            // TK-LOCKED (2026-07-24): tapping this box opens the SAME
            // Treatment Progress editor an Arrived row uses -- same
            // row.remark field, so it stays in sync with Report Card and
            // shows again on this patient's next visit.
            b.tvTreatmentW.setOnClickListener { onTreatmentTap(row) }
            b.tvMobileW.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mobile", row.mobile))
                android.widget.Toast.makeText(it.context, "Mobile copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            b.tvNameW.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("name", row.name))
                android.widget.Toast.makeText(it.context, "Name copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            b.tvPatientIdW.setOnLongClickListener {
                val cm = it.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("patient id", row.patientId))
                android.widget.Toast.makeText(it.context, "Patient ID copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        } catch (_: Throwable) { }
    
        // 🔴🔴🔒 V449 (TK-রিপোর্ট ১৮.০৮.২০২৬ — "লগইন হয়ে যায় কিন্তু কার্যকরী কিছু হয় না")।
        // আসল কারণ: NoBengali-এর অটো-সুইপ শুধু পর্দার layout-পাসে চলে; RecyclerView
        // rebind/scroll নতুন layout-পাস তৈরি না করেই লেখা বসিয়ে দেয়, তাই তালিকার
        // সারির বাংলা কখনো ঢাকাই পড়ত না। এখন প্রতিটা বাইন্ডের শেষেই সরাসরি সুইপ —
        // বাংলা-বন্ধ না থাকলে কিছুই করে না (activeCache false ⇒ সাথে সাথে ফেরত)।
        try { NoBengali.sweep(holder.itemView) } catch (_: Throwable) { }
    }
}
