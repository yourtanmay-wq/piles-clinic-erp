package com.tkbiswas.pilesclinic.native

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tkbiswas.pilesclinic.databinding.ItemBriefingCardBinding

class BriefingAdapter(
    private val context: Context,
    private var items: List<Briefing>,
    private val onSeen: (Briefing) -> Unit,
    private val onReply: (Briefing) -> Unit,
    private val onDelete: (Briefing) -> Unit,
    /** 🔒 খাতার সারি B100 (TK, 29.07.2026 রাত ১১.১০): স্টাফের পাঠানো ডিলিটের
     *  অনুরোধ মাস্টার এক চাপে অনুমোদন করবেন। ডিফল্ট ফাঁকা, তাই পুরনো কোনো
     *  ডাক ভাঙে না। */
    private val onApproveDelete: (Briefing) -> Unit = {},
    /** 🔴 B281 (02.08.2026): "Refund request" নোটিশে Approve/Reject বোতাম চাপলে। */
    private val onApproveRefund: (Briefing) -> Unit = {},
    /** 🆕 B419 (04.08.2026): "Chamber reopen request" নোটিশে Approve চাপলে। */
    private val onApproveReopen: (Briefing) -> Unit = {},
    /** 🆕 B467 (05.08.2026, TK-নির্দেশ) — মেসেজে থাকা ফোন-নম্বরে ট্যাপ করলে
     *  ডাকা হয় (Enquiry/Registration/Payment নোটিশের মতোই — নতুন প্যারামিটার,
     *  ডিফল্ট ফাঁকা, তাই পুরনো কোনো caller ভাঙে না)। */
    private val onCallNumber: (String) -> Unit = {},
    /** 🔴🔒 V501 (TK-নির্দেশ ২১.০৮.২০২৬, ফটো-প্রুফ অনুমোদিত) — নোটিশের
     *  "View" বোতাম: ওই নম্বরের রোগীর পাতা খোলে। ডিফল্ট ফাঁকা, তাই পুরনো
     *  কোনো caller ভাঙে না। */
    private val onViewRecord: (Briefing, String) -> Unit = { _, _ -> },
    /* 🟢🔒 V692 (TK, ২৬.০৮.২০২৬) — ⚠️ Overdue Follow-up Alert-এর "View"।
       ডিফল্ট ফাঁকা, তাই এই অ্যাডাপ্টারের পুরনো ব্যবহারকারীরা অক্ষত। */
    private val onViewOverdue: (Briefing) -> Unit = { },
    private val isMaster: Boolean = false,
    /** 🆕 TK-নির্দেশ (07.08.2026) — একসাথে অনেক অনুমোদন: কোনো কার্ড বাছাই/
     *  বাছাই-বাতিল হলে Activity-কে জানায় (নিচের "একসাথে অনুমোদন" বার দেখাতে)।
     *  ডিফল্ট ফাঁকা, তাই পুরনো কোনো caller ভাঙে না। */
    private val onSelectChanged: () -> Unit = {}
) : RecyclerView.Adapter<BriefingAdapter.ViewHolder>() {

    /** 🔴 V501 — মোবাইল নম্বরের নিয়ম এক জায়গায় (আগে ফাংশনের ভিতরে ছিল),
     *  যাতে "ক্লিকযোগ্য নম্বর" আর "View বোতাম" কখনো আলাদা নিয়মে না চলে।
     *
     * 🔴🔴🔒 V511 (২১.০৮.২০২৬, TK-রিপোর্ট ছবিসহ — *"এখানে ভিউ একটা অপশন থাকার
     * দরকার ছিল... কিন্তু আপনি সেটা না করে রিপ্লাই রেখেছেন"*)।
     *
     * **সত্যি কথা:** View বোতামটা V501-এ TK-এর আগের নির্দেশেই বানানো হয়েছিল
     * (`btnViewRecord`, নিচে দেখুন)। কিন্তু সেটা **কখনো দেখাই যেত না**, কারণ
     * নিচের নিয়মটা ভুল ছিল —
     *
     *      পুরোনো:  (\+91[\-\s]?)?\b[6-9]\d{9}\b
     *
     * এখানে `\b` (শব্দের সীমানা) `+91`-এর ঠিক পরেই বসানো। `+91 7099468221`
     * (মাঝে ফাঁকা) হলে মেলে, কিন্তু **`+917099468221` (গায়ে গায়ে লাগানো)**
     * হলে `1` আর `7`-এর মাঝে কোনো সীমানা নেই, তাই **মেলেই না**।
     * TK-এর ডেটাবেসে নম্বর ঠিক ঐভাবেই জমা — তাই View চিরকাল লুকানো থাকত, আর
     * নম্বরটাও নীল/ক্লিকযোগ্য হত না (TK-এর ছবিতে নম্বরটা কালো — সেটাই প্রমাণ)।
     * ⇒ আমি `+91` ছাড়া নম্বর দিয়ে পরীক্ষা করেছিলাম, গায়ে-লাগানো `+91` দিয়ে
     *   করিনি। **এটা আমার ভুল ছিল।**
     *
     * **নতুন নিয়ম:** `(?<![0-9])(\+?91[\-\s]?)?([6-9][0-9]{9})(?![0-9])`
     *  · `+917099468221` · `+91 7099468221` · `917099468221` · `7099468221`
     *    — চারটেই ধরা পড়ে।
     *  · সামনে বা পিছনে আর কোনো অঙ্ক থাকলে ধরা পড়ে না, তাই বড় সংখ্যার
     *    (বিলের অঙ্ক, আইডি) মাঝ থেকে ভুল করে ১০ অঙ্ক তুলে নেওয়া হয় না।
     */
    private val MOBILE_PATTERN: java.util.regex.Pattern =
        java.util.regex.Pattern.compile("(?<![0-9])(\\+?91[\\-\\s]?)?([6-9][0-9]{9})(?![0-9])")

    /** 🆕 (07.08.2026) — বাছাই-করা অনুমোদন-নোটিশের id (শুধু মাস্টারের পর্দায়)। */
    val selectedIds = linkedSetOf<String>()

    fun clearSelection() { selectedIds.clear(); notifyDataSetChanged() }

    /** 🆕 (07.08.2026) — বর্তমানে দেখানো তালিকা (একসাথে অনুমোদনের সময় বাছাই-করা
     *  id থেকে আসল নোটিশ বার করতে লাগে)। ⛔ শুধু পড়ার জন্য। */
    fun itemsSnapshot(): List<Briefing> = items

    fun updateItems(newItems: List<Briefing>) {
        items = newItems
        // তালিকা বদলালে আর-না-থাকা কার্ডের বাছাই বাদ (নইলে ভুল id থেকে যেত)।
        val alive = newItems.map { it.id }.toSet()
        selectedIds.retainAll(alive)
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemBriefingCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBriefingCardBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    /**
     * V689: "Seen by N"-কে TextView-এর সাধারণ click-এর উপর নির্ভর না রেখে
     * লেখাটির ওই অংশকেই আসল clickable span করা হয়েছে। কিছু কার্ডে message-এর
     * LinkMovementMethod/RecyclerView touch handling-এর কারণে আগের listener
     * বাস্তবে tap পেত না, যদিও সংখ্যা ঠিক দেখা যেত।
     */
    private fun bindSeenBy(view: android.widget.TextView, prefix: String, item: Briefing) {
        val label = "Seen by ${item.seenCount}"
        val fullText = prefix + label
        val text = SpannableString(fullText)
        val openSeenList = {
            // বর্তমান model-list ছাড়াও raw row থেকে আবার পড়া—পুরনো/মিশ্র
            // notice card হলেও নামের তালিকা ফাঁকা হয়ে tap নষ্ট হবে না।
            val rawSeen = item.raw.optJSONArray("seen")
            val mobiles = if (item.seenBy.isNotEmpty()) item.seenBy else
                (0 until (rawSeen?.length() ?: 0))
                    .map { rawSeen?.optString(it, "").orEmpty() }
                    .filter { it.isNotBlank() }
            val names = mobiles.map { mobile ->
                StaffDirectory.findAccount(mobile)?.name ?: mobile
            }.distinct()
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setCustomTitle(PremiumAlert.header(context, "Seen by (${names.size})"))
                .setItems(names.toTypedArray(), null)
                .setPositiveButton("Close", null)
                .show().also { dialog -> PremiumAlert.paint(dialog) }
        }
        val start = fullText.indexOf(label)
        text.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) { openSeenList() }
            override fun updateDrawState(ds: android.text.TextPaint) {
                ds.color = Color.parseColor("#1976B9")
                ds.isUnderlineText = true
            }
        }, start, start + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.text = text
        view.movementMethod = LinkMovementMethod.getInstance()
        view.highlightColor = Color.TRANSPARENT
        view.isClickable = true
        // পুরো লাইন চাপলেও একই ফল—সাধারণ ব্যবহারকারীর জন্য tap target বড় থাকে।
        view.setOnClickListener { openSeenList() }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.tvTitle.text = item.title.ifBlank { "(No title)" }
        // 🔒 TK-নির্দেশ (04.08.2026): এখন তারিখের সাথে সময়ও দেখা যাবে —
        // যাতে নোটিফিকেশনটা আজকের নাকি অনেক পুরনো, তা এক নজরেই বোঝা যায়।
        // 🔒 TK-নির্দেশ (04.08.2026, ছবিসহ — "সময় শো করবে, তাহলে নোটিফিকেশন
        // আজকের নাকি পুরনো তা বোঝার জন্য বারবার জিজ্ঞাসা করতে হবে না"):
        // এখন থেকে তারিখের সাথে সময়ও দেখা যাবে। `date` ঘরে শুধু তারিখ থাকে
        // (সময় নেই — BriefingModel.buildNewBriefing()), তাই আসল সময়ের জন্য
        // `raw` থেকে `createdAt` (পুরো ISO টাইমস্ট্যাম্প, সবসময় থাকে) পড়া হয়।
        // ⛔ ব্যর্থ হলে (createdAt ফাঁকা/পুরনো ফরম্যাট) আগের মতোই শুধু তারিখ।
        // 🔴🆕🔒 V433 (TK-নির্দেশ ১৮.০৮.২০২৬, স্ক্রিনশটসহ — *"সাধারণ নোটিফিকেশন,
        // এত হাইলাইট করে দেখানোর কিছু নেই"* · *"in time submit হয়েছে, তার জন্য
        // আমাকে কেন আবার রিপ্লাই দিতে হবে"* · *"Time ২ বার কেন? Role: master ·
        // Seen by 0 — এর মানে কি? এটা একটা সাধারণ জিনিস, তাহলে এটা নোটিশ কেন হবে"*)।
        // "Staff IN TIME"/"Staff OUT TIME" = শুধু **তথ্য জানানোর** কার্ড, কোনো
        // কাজ করার নেই। তাই এই কার্ডে — NOTICE চিপ নেই · Reply বোতাম নেই ·
        // "Role/Seen by" লাইন নেই · সময় একবারই (উপরে শুধু তারিখ, নিচে 🕐 সময়)।
        // ⛔ বাকি সব ধরনের নোটিশ (Delete/Refund/Reopen অনুরোধ, TK-এর নিজের লেখা
        //    নোটিশ) এক চুলও বদলায়নি — এখানে শুধু এই দুটো শিরোনামের জন্যই আলাদা।
        // ⛔ RecyclerView রিসাইকেল-নিরাপদ: প্রতিটা ঘরেই দুই দিকই (দেখাও/লুকাও)
        //    বসানো হয়েছে, তাই স্ক্রল করলে অন্য কার্ডে ভুল চেহারা যাবে না।
        val isPlainInfo = item.title.equals("Staff IN TIME", ignoreCase = true) ||
            item.title.equals("Staff OUT TIME", ignoreCase = true)
        val createdAtRaw = item.raw.s("createdAt")
        b.tvDate.text = when {
            // সময় দুবার দেখানো হত (উপরে পোস্টের সময়, নিচে 🕐 IN TIME) — এখানে
            // উপরে শুধু তারিখ, নিচে আসল IN TIME-টাই থাকে (কোনো তথ্য হারায় না)।
            isPlainInfo -> DateUtil.display(item.date)
            createdAtRaw.isNotBlank() -> DateUtil.displayWithTime(createdAtRaw)
            else -> DateUtil.display(item.date)
        }
        // 🆕 B467 (05.08.2026, TK-নির্দেশ — "নোটিফিকেশনে যে নাম্বার আসবে, এই
        // নাম্বারের উপর চাপ দিলে যেন কলিং অপশন খুলে যায়") — মেসেজের ভিতরে
        // ১০-অঙ্কের (ঐচ্ছিক +91 সহ) নম্বর খুঁজে সেটাকে ক্লিকযোগ্য করা হয়।
        // ⛔ মেসেজের বাকি লেখা/রং/সাইজ কিছুই বদলায়নি — শুধু নম্বরটুকু নীল ও
        // আন্ডারলাইন করা, বাকি স্বাভাবিক।
        b.tvMessage.text = buildClickableMessage(item.message)
        b.tvMessage.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        b.tvMessage.highlightColor = android.graphics.Color.TRANSPARENT
        bindSeenBy(b.tvTargets, "${item.targetsSummary} · ", item)
        // 🔴🔒 V682 (২৫.০৮.২০২৬, TK-লাইভ-টেস্ট রিপোর্ট — "Seen by 1-এ চাপ
        // দিলে কে দেখেছে বোঝা যায় না") — এখন চাপলে নামের তালিকা দেখায়
        // (স্টাফ কোড → নাম, প্রমাণিত StaffDirectory.findAccount())। ⛔
        // ফাঁকা থাকলে চাপে কিছুই হয় না, বাকি সব আগের মতোই।
        // 🔴 V433 (TK): "Role: master · Seen by 0 — এর মানে কি?" — সাধারণ
        // তথ্য-কার্ডে লাইনটার কোনো কাজ নেই, তাই লুকানো। বাকি নোটিশে আগের মতোই।
        b.tvTargets.visibility = if (isPlainInfo) View.GONE else View.VISIBLE

        // 🎨 DESIGN #4 (2026-08-06, TK approved): sender avatar + line, a
        // priority chip, and the left colour bar. "Urgent" = action-required
        // request notices (delete / refund / reopen) → red; ordinary notices →
        // green. Derived from the title only, so NO new data is needed and no
        // existing logic changes. Both branches set every field, so a recycled
        // card never keeps a previous card's colour/chip.
        val who = item.branch.ifBlank { item.targetsSummary }.ifBlank { "Notice" }
        b.tvWho.text = who
        val initialSrc = who.trim().ifEmpty { item.title.trim() }
        b.tvAvatar.text = (initialSrc.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: 'N').toString()
        // 🔴🆕🔒 TK-নির্দেশ (08.08.2026, ফটো-প্রুফে লক) — স্টাফ-নোটিশের (IN/OUT TIME
        // ইত্যাদি, বার্তায় "Staff : <কোড>" থাকে) হাইলাইটে **ব্রাঞ্চের বদলে স্টাফ কোড +
        // নাম** (কোড দিয়ে জমানো স্টাফ-তালিকা থেকে নাম) — master যেন বিভ্রান্ত না হয়।
        // ⛔ যেসব নোটিশে "Staff :" নেই (Admin briefing/Delete/Refund অনুরোধ) সেগুলো
        // আগের মতোই (ব্রাঞ্চ)। নাম না পেলে শুধু কোড — কিছুই ভাঙে না।
        val staffCode = extractField(item.message, "Staff")
        if (staffCode != null) {
            val nm = staffNameFor(staffCode)
            b.tvWho.text = if (nm != null) "$staffCode · $nm" else staffCode
            b.tvAvatar.text = ((nm ?: staffCode).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: 'S').toString()
            // কম্প্যাক্ট: Staff/Branch এখন হাইলাইটেই আছে, তাই বার্তায় শুধু সময়টুকু।
            val t = extractField(item.message, "Time")
            if (t != null) b.tvMessage.text = "🕐 $t"
        }

        // 🟢🔒 V641 (২৪.০৮.২০২৬, TK-নির্দেশ, ডেমো-প্রুফ পাশ — "ব্রাঞ্চ দুই
        // জায়গায় কেন? এক লাইনে রাখলেই জায়গা কমে") — Delete/Refund/Reopen-
        // ধরনের গঠিত অনুরোধ-নোটিশে ব্রাঞ্চ + অনুরোধকারীর কোড এক লাইনে
        // (হেডারেই), তাই বার্তায় আর আলাদা "Name :"/"Branch :"/"Requested
        // by :" সারি লাগে না (Name শিরোনামেই আছে) — ডুপ্লিকেট বাদ দিয়ে
        // বার্তাটা ছোট হয়।
        // ⛔ শুধু **দেখানো** টেক্সট বদলায় — item.message (ডেটাবেসে সেভ করা
        //    আসল লেখা, Reply/cloud sync-এ যা ব্যবহার হয়) এক অক্ষরও বদলায় না।
        // ⛔ এই গঠন (Requested by :) নেই এমন বার্তায় (সাধারণ Briefing/
        //    auto-notice/Staff IN-OUT) একচুলও প্রভাব পড়ে না।
        val requestedBy = extractField(item.message, "Requested by")
        if (requestedBy != null) {
            val brOnly = item.branch.ifBlank { extractField(item.message, "Branch").orEmpty() }
            b.tvWho.text = if (brOnly.isNotBlank()) "$brOnly · $requestedBy" else requestedBy
            b.tvAvatar.text = (brOnly.trim().ifEmpty { requestedBy }
                .firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: 'N').toString()
            val trimmed = item.message.lines().filterNot { line ->
                val l = line.trim()
                l.startsWith("Name :", true) || l.startsWith("Branch :", true) ||
                    l.startsWith("Requested by :", true) ||
                    (l.endsWith("permission request", true) && !l.contains(":")) ||
                    // 🟢🔒 V641 (২৪.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ — "এটা দেখে কি
                    // প্রফেশনাল লুক মনে হচ্ছে?" → "না") — আসল কারণ: "Master:
                    // রেকর্ডটা খুলে Take Action → Delete চাপুন" এই নির্দেশটা
                    // পুরনো, ম্যানুয়াল একটা ফ্লো বোঝাত। "✔ Approve" বোতাম
                    // এখন **এক-চাপেই** সরাসরি ডিলিট করে দেয় (নিচেই
                    // `onApproveDelete(item)` ডাকা হয়) — তাই এই লাইনটা
                    // এখন সত্যিই অপ্রয়োজনীয়/বিভ্রান্তিকর, শুধু "Take
                    // Action"-ধরনের ম্যানুয়াল-নির্দেশের লাইনগুলোই বাদ
                    // (Reopen-এর মতো তথ্য-জানানো Master-নোট অক্ষত থাকে)।
                    (l.contains("Take Action", true) && l.contains("Master", true))
            }.joinToString("\n").trim()
            b.tvMessage.text = buildClickableMessage(trimmed)
            val dens = holder.itemView.resources.displayMetrics.density
            val pad = (8 * dens).toInt()
            b.tvMessage.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_brief_infobox)
            b.tvMessage.setPadding(pad, pad, pad, pad)
            // 🟢🔒 V641 (২৪.০৮.২০২৬, TK-নির্দেশ, ডেমো-প্রুফ পাশ) — এই
            // ধরনের গঠিত অনুরোধ-নোটিশে "Role: master" অংশটা বাদ (কে
            // পাঠিয়েছেন সেটা হেডারেই আছে) — শুধু "Seen by X" থাকে।
            bindSeenBy(b.tvTargets, "", item)
        } else {
            // 🔒 RecyclerView রিসাইকেল-নিরাপদ: আগের কার্ডে বসানো বক্স-
            // ব্যাকগ্রাউন্ড/প্যাডিং যেন সাধারণ নোটিশে ভুল করে থেকে না যায়।
            b.tvMessage.background = null
            b.tvMessage.setPadding(0, 0, 0, 0)
        }
        val titleLc = item.title.lowercase()
        val urgent = listOf("request", "refund", "reopen", "urgent").any { titleLc.contains(it) }
        // 🔴🎨 TK-নির্দেশ (07.08.2026): "নেভি ব্লু/কালো রঙ ব্যবহার করা যাবে না।
        // ডিলিট একটা নিরাপত্তার জিনিস, তাই সেখানে লাল ধরনের রঙ; বাকিটা সবুজ,
        // যাতে সম্পূর্ণ প্রফেশনাল লাগে।" — তাই অ্যাভাটার/শিরোনাম/অনুমোদন-বোতাম
        // এখন অনুরোধ-নোটিশে লাল, সাধারণ নোটিশে সবুজ (আগে সবগুলোই কালচে
        // নেভি ছিল)। ⛔ শুধু রঙ — কোনো লজিক/ডেটা বদলায়নি।
        val redLine = android.graphics.Color.parseColor("#C0392B")
        val greenLine = android.graphics.Color.parseColor("#0F7A43")
        if (urgent) {
            b.priorityBar.setBackgroundColor(redLine)
            b.tvChip.text = "URGENT"
            b.tvChip.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_brief_chip_urgent)
            b.tvChip.setTextColor(android.graphics.Color.parseColor("#B91C1C"))
            b.tvAvatar.backgroundTintList = android.content.res.ColorStateList.valueOf(redLine)
            b.tvTitle.setTextColor(android.graphics.Color.parseColor("#8E2A20"))
            // 🟢🔒 V641 (২৪.০৮.২০২৬, TK-নির্দেশ, ডেমো-প্রুফ পাশ — কমপ্যাক্ট
            // ডিজাইন) — শিরোনাম এখন ছোট রঙিন পিল (chip), আগের মতো পুরো-
            // চওড়া প্লেইন টেক্সট নয়। ⛔ শুধু দেখানোর সাজ — id/লজিক অক্ষত।
            b.tvTitle.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_brief_title_urgent)
        } else {
            b.priorityBar.setBackgroundColor(greenLine)
            b.tvChip.text = "NOTICE"
            b.tvChip.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_brief_chip_normal)
            b.tvChip.setTextColor(android.graphics.Color.parseColor("#166534"))
            b.tvAvatar.backgroundTintList = android.content.res.ColorStateList.valueOf(greenLine)
            b.tvTitle.setTextColor(android.graphics.Color.parseColor("#14361F"))
            b.tvTitle.setBackgroundResource(com.tkbiswas.pilesclinic.R.drawable.bg_brief_title_normal)
        }
        // 🔴 V433 (TK): "এটা একটা সাধারণ জিনিস, তাহলে এটা নোটিশ কেন হবে" —
        // তথ্য-কার্ডে NOTICE চিপটা তুলে দেওয়া হলো। রং/বার আগের মতোই সবুজ।
        b.tvChip.visibility = if (isPlainInfo) View.GONE else View.VISIBLE
        // অনুমোদন-বোতামের রংও একই নিয়মে (কালো বাদ)।
        b.btnApproveDelete.backgroundTintList =
            android.content.res.ColorStateList.valueOf(if (urgent) redLine else greenLine)

        // 🔴🆕 TK (08.08.2026): কম্প্যাক্ট — রিপ্লাই না থাকলে "No replies yet" বাক্সটাই
        // লুকানো (আগে খালি হলুদ বাক্স জায়গা নিত)। রিপ্লাই থাকলে আগের মতোই।
        if (item.replies.isEmpty()) {
            b.tvReplies.visibility = View.GONE
        } else {
            b.tvReplies.visibility = View.VISIBLE
            b.tvReplies.text = item.replies.joinToString("\n") { r ->
                "• ${r.text}"
            }
        }

        // 🔒 খাতার সারি B100: শুধু **মাস্টার** ও শুধু **ডিলিটের অনুরোধ** নোটিশে
        // "Approve & Delete" দেখা যায়। বাকি সব নোটিশে বোতামটা লুকানো থাকে,
        // তাই পুরনো কার্ডের চেহারা এক চুলও বদলায়নি।
        val isDeleteRequest = item.title.contains("Delete request", ignoreCase = true)
        if (isMaster && isDeleteRequest) {
            b.btnApproveDelete.visibility = View.VISIBLE
            b.btnApproveDelete.text = "\u2714 Approve"      // 🔴 এক-লাইনে আঁটাতে ছোট লেখা (আগে "Approve & Delete"); কাজ একই
            b.btnApproveDelete.setOnClickListener { onApproveDelete(item) }
        } else {
            b.btnApproveDelete.visibility = View.GONE
            b.btnApproveDelete.setOnClickListener(null)
        }

        // 🔴🔴 B281 (02.08.2026, TK-রিপোর্ট — "Refund request" নোটিশ SEEN
        // করেও সরছে না): এই নোটিশটা শুধু তথ্য জানানোর জন্য ছিল, আসল Approve/
        // Reject হতো আলাদা একটা কালেভদ্রে-না-দেখা ড্রপডাউনে (💸 Pending Refund
        // Requests) — যেটা কোনো pending রিফান্ড খুঁজে না পেলে সম্পূর্ণ লুকিয়ে
        // যেত। TK নিজে জানাতে রাজি নন সেটা খুঁজে দেখতে, তাই এখন ঠিক B100-এর
        // Delete-request-এর প্যাটার্নেই এই নোটিশ কার্ডেই সরাসরি Approve/Reject
        // বোতাম বসানো হলো — মাস্টারকে আলাদা কোথাও খুঁজতে হবে না।
        // ⛔ RecyclerView-এর View রিসাইকেল হয় বলে দুটো শাখাতেই (Delete/Refund)
        //    টেক্সট আলাদা করে বসাতে হয়েছে — নইলে স্ক্রল করার সময় ভুল লেখা
        //    রয়ে যেতে পারত (একটা কার্ডের বোতাম অন্য কার্ডে "ভুতুড়ে" দেখাত)।
        val isRefundRequest = item.title.contains("Refund request", ignoreCase = true)
        if (isMaster && isRefundRequest) {
            b.btnApproveDelete.visibility = View.VISIBLE
            b.btnApproveDelete.text = "\u2714 Refund"       // আগে "Approve / Reject Refund" — চাপলে সেই একই Approve/Reject পপ-আপই খোলে
            b.btnApproveDelete.setOnClickListener { onApproveRefund(item) }
        }

        // 🆕 B419 (04.08.2026, TK-নির্দেশ) — "Chamber reopen request" নোটিশে
        // Approve/Reject। ⛔ একই শেয়ার্ড বোতাম (Delete/Refund-এর প্যাটার্নেই),
        // তাই RecyclerView রিসাইকেল হলেও ভুতুড়ে লেখা থাকার ঝুঁকি নেই — প্রতিটা
        // শাখাই নিজের লেখা/ক্লিক আবার বসায়।
        val isReopenRequest = item.title.contains("Chamber reopen request", ignoreCase = true)
        if (isMaster && isReopenRequest) {
            b.btnApproveDelete.visibility = View.VISIBLE
            b.btnApproveDelete.text = "\u2714 Reopen"       // আগে "Approve / Reject Reopen" — চাপলে সেই একই পপ-আপই খোলে
            b.btnApproveDelete.setOnClickListener { onApproveReopen(item) }
        }

        // 🆕 TK-নির্দেশ (07.08.2026) — "অনুমতি অনেকগুলো একসাথে হয়ে গেলে মার্ক
        // করে একবারে অনুমোদন দেওয়া যাবে": শুধু যেসব কার্ডে অনুমোদন-বোতাম
        // দেখা যাচ্ছে (মাস্টারের অনুরোধ-নোটিশ) সেগুলোতেই বাছাই-বক্স।
        // ⛔ RecyclerView রিসাইকেল-নিরাপদ: প্রতিবার listener খুলে, অবস্থা বসিয়ে,
        //    তারপর আবার listener বসানো হয় (নইলে স্ক্রলে ভুল কার্ড বাছা হতো)।
        val canBulk = isMaster && b.btnApproveDelete.visibility == View.VISIBLE
        b.cbSelect.setOnCheckedChangeListener(null)
        b.cbSelect.visibility = if (canBulk) View.VISIBLE else View.GONE
        b.cbSelect.isChecked = canBulk && selectedIds.contains(item.id)
        if (canBulk) {
            b.cbSelect.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIds.add(item.id) else selectedIds.remove(item.id)
                onSelectChanged()
            }
        }

        b.btnSeen.setOnClickListener { onSeen(item) }
        b.btnReply.setOnClickListener { onReply(item) }
        // 🔴 V433 (TK): "in time submit হয়েছে, তার জন্য আমাকে কেন আবার রিপ্লাই
        // দিতে হবে" — তথ্য-কার্ডে Reply নেই। Delete থেকেই যায় (চাইলে সরানো যায়)।
        // 🔴🔴 V511 (২১.০৮.২০২৬, TK-নির্দেশ) — TK-এর কথা: *"একটু ভেবেচিন্তে
        //   বলুন তো, যেখানে রিপ্লাই আমি কি দিব? এটা একটা নতুন এনকোয়ারি হয়েছে,
        //   এখন অ্যাকশন কি হতে পারে? আমার হিসাবে তো অ্যাকশন হতে হয় ভিউ।"*
        //   ⇒ স্বয়ংক্রিয় তিন নোটিশে (New Enquiry · New Registration ·
        //     Advance Received) **Reply নেই** — View · Seen · Delete থাকে।
        //   ⛔ হাতে লেখা Briefing ও অনুমোদনের নোটিশে Reply আগের মতোই আছে।
        // 🟢🔒 V692 (২৬.০৮.২০২৬, TK): *"Overdue Call Alert এ Reply কেন আসবে,
        //   সেখানে View থাকতে হবে"* — ⚠️ Overdue Follow-up Alert-এও Reply নেই।
        b.btnReply.visibility =
            if (isPlainInfo || isAutoNotice(item) || BriefingModel.isOverdueAlert(item.title))
                View.GONE else View.VISIBLE
        b.btnDelete.setOnClickListener { onDelete(item) }

        /* 🔴🔒 V501 (TK-নির্দেশ) — "View" বোতাম।
           নোটিশের লেখায় রোগীর মোবাইল নম্বর থাকলেই দেখা যায়; না থাকলে
           লুকানো (অকেজো বোতাম দেখানো হয় না)। চাপলে ওই নম্বরের রোগীর পাতা।
           ⛔ নম্বর খোঁজা হয় ঠিক সেই একই নিয়মে যেটা দিয়ে নম্বরটা এতদিন
              ক্লিকযোগ্য করা হচ্ছে (`buildClickableMessage`) — নতুন কিছু নয়। */
        val mobileInNotice = firstMobileIn(item.message)
        // 🟢🔒 V692 — Overdue সতর্কতায় কোনো একটা নম্বর থাকে না (ব্রাঞ্চ ধরে
        //   গোনা), তাই নম্বর না থাকলেও View দেখাতে হবে। চাপলে ওই ব্রাঞ্চের
        //   **৩+ দিন দেরি হওয়া** কলগুলোই খোলে — নোটিশে যত জন লেখা, ঠিক তত জন।
        val overdueAlert = BriefingModel.isOverdueAlert(item.title)
        if (overdueAlert) {
            b.btnViewRecord.visibility = View.VISIBLE
            b.btnViewRecord.setOnClickListener { onViewOverdue(item) }
        } else if (mobileInNotice != null) {
            b.btnViewRecord.visibility = View.VISIBLE
            // 🔴 V511: নোটিশটাও পাঠানো হয় — কারণ গন্তব্য নোটিশের **ধরন**
            //    অনুযায়ী বদলায় (Enquiry/Registration → Follow-up-এর ঠিক
            //    সেকশন, Advance → Payment)। নিচের `isAutoNotice` দেখুন।
            b.btnViewRecord.setOnClickListener { onViewRecord(item, mobileInNotice) }
        } else {
            b.btnViewRecord.visibility = View.GONE
            b.btnViewRecord.setOnClickListener(null)
        }
    
        // 🔴🔒 V449 — একই ফিক্স (দেখুন FollowUpAdapter.kt-এর মন্তব্য): তালিকার
        // সারির বাংলা যাতে বাংলা-বন্ধ স্টাফের ফোনে কখনো না দেখা যায়।
        try { NoBengali.sweep(holder.itemView) } catch (_: Throwable) { }
    }

    // 🔴🆕🔒 TK-নির্দেশ (08.08.2026) — নোটিশ-বার্তা থেকে একটা ঘরের মান (যেমন
    // "Staff", "Time") বার করা। বার্তার ধরন: "👤 Staff : X\n🏥 Branch : Y\n🕐 Time : Z"।
    // না পেলে null। ⛔ মূল বার্তা বদলায় না।
    private fun extractField(message: String, label: String): String? {
        return try {
            Regex(Regex.escape(label) + "\\s*:\\s*([^\\n]+)")
                .find(message)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) { null }
    }

    // মাস্টারের জমানো স্টাফ-তালিকা (staff_profile_cache → "rows") থেকে কোড ধরে নাম।
    // একবার পড়ে map-এ রাখা হয় (বারবার নয়)। না পেলে null (তখন শুধু কোড দেখাবে)।
    private var staffNameMap: Map<String, String>? = null
    private fun staffNameFor(code: String): String? {
        val map = staffNameMap ?: run {
            val m = HashMap<String, String>()
            try {
                val s = context.getSharedPreferences("staff_profile_cache", Context.MODE_PRIVATE)
                    .getString("rows", null)
                if (s != null) {
                    val arr = org.json.JSONArray(s)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        // 🔴🔒 V458 (20.08.2026, TK-অনুমোদিত ছোট কাজ — একই
                        // "null" টেক্সট প্যাটার্ন যাচাই): raw `.optString(key)`
                        // (fallback ছাড়াই) key অনুপস্থিত/NULL দুটোতেই literal
                        // "null" টেক্সট ফেরাতে পারে, আর `.isNotBlank()` সেটাকে
                        // ভুল করে "আছে" ধরে নিত — staff-এর নামের জায়গায় "null"
                        // বসে যাওয়ার ঝুঁকি ছিল। `.s()` (JsonExt.kt) দিয়ে ঠিক করা হলো।
                        val pc = o.s("person_code")
                        val nm = o.s("full_name")
                        if (pc.isNotBlank() && nm.isNotBlank()) m[pc] = nm
                    }
                }
            } catch (_: Throwable) { }
            staffNameMap = m
            m
        }
        return map[code]?.takeIf { it.isNotBlank() }
    }

    // 🆕 B467 (05.08.2026, TK-নির্দেশ) — মেসেজের লেখায় ১০-অঙ্কের ভারতীয়
    // মোবাইল নম্বর (ঐচ্ছিক +91 প্রিফিক্স-সহ) খুঁজে বার করে ক্লিকযোগ্য করে।
    // ⛔ মূল `item.message` (যা ডেটাবেসে সেভ আছে) এক অক্ষরও বদলায় না —
    // শুধু দেখানোর সময় স্প্যান বসানো হয়।
    /** 🔴 V501 — বার্তায় প্রথম যে মোবাইল নম্বরটা আছে (না থাকলে `null`)।
     *  `buildClickableMessage`-এর হুবহু একই প্যাটার্ন ব্যবহার করা হয়। */
    /** 🔴 V511 — ডেটাবেসের ট্রিগার যে তিন ধরনের নোটিশ নিজে থেকে বানায়
     *  (V490)। নাম তিনটে সার্ভারের `auto_notice_titles()`-এর হুবহু একই। */
    private fun isAutoNotice(item: Briefing): Boolean {
        val t = item.title.trim()
        return t.equals("New Enquiry", ignoreCase = true) ||
            t.equals("New Registration", ignoreCase = true) ||
            t.equals("Advance Received", ignoreCase = true)
    }

    private fun firstMobileIn(message: String): String? {
        return try {
            val m = MOBILE_PATTERN.matcher(message)
            if (m.find()) m.group().filter { it.isDigit() }.takeLast(10) else null
        } catch (_: Throwable) { null }
    }

    private fun buildClickableMessage(message: String): android.text.SpannableString {
        val spannable = android.text.SpannableString(message)
        val matcher = MOBILE_PATTERN.matcher(message)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val digits = matcher.group().filter { it.isDigit() }.takeLast(10)
            val span = object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) { onCallNumber(digits) }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = android.graphics.Color.parseColor("#1167D8")
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(span, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }
}
