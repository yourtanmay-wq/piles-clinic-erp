package com.tkbiswas.pilesclinic.native

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tkbiswas.pilesclinic.databinding.ActivityDraftListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One Draft category as a scrollable list of follow-up-style cards, each with
 * Call / WhatsApp / View / Restore. Restore hands the record back to the exact
 * section it came from (web restoreDraftEntry) and removes it from this list.
 */
class DraftListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDraftListBinding
    private val repository = DraftRepository(this)
    private val entries = ArrayList<DraftEntry>()
    private lateinit var user: NativeUser
    // 🔒 খাতার সারি B192: হেডারে দেখানো টাইটেলটাই Sheet-ফাইলের নামেও ব্যবহার
    // হয় (যেমন "Incomplete Patient"), তাই ক্লাস-লেভেলে ধরে রাখা হলো।
    private var screenTitle = "Draft"
    // ⛔ দুবার চাপ পড়লেও একবারই চলে — Follow-up-এর `sheetBusy`-র হুবহু একই নিয়ম।
    private var sheetBusy = false
    // V215 (§18): কোন bucket ও কোন branch/date দিয়ে খোলা হয়েছে — pull-to-refresh-এ
    // সত্যিকারের নতুন data আনতে লাগে। না থাকলে (পুরোনো caller) আগের আচরণ অক্ষত।
    private var bucketKey: String = ""
    private var branchArg: String? = null
    private var fromArg: String? = null
    private var toArg: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TK-REPORTED (2026-07-27): this screen has no bottom bar, so until
        // now opening it never retried a save that was still stuck on this
        // phone. A staff member could sit here while a registration or a
        // payment stayed unsent. Same retry every other screen already does.
        try { BottomNav.retryStuckSaves(this) } catch (_: Throwable) { }
        binding = ActivityDraftListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UppercaseInputUtil.applyToAll(binding.root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically

        val session = NativeSession.current(this)
        if (session == null) { finish(); return }
        user = session

        val title = intent.getStringExtra("title") ?: "Draft"
        screenTitle = title
        bucketKey = intent.getStringExtra("bucket") ?: ""
        branchArg = intent.getStringExtra("branch")
        fromArg = intent.getStringExtra("from")
        toArg = intent.getStringExtra("to")
        val incoming = (intent.getSerializableExtra("entries") as? ArrayList<DraftEntry>) ?: arrayListOf()
        entries.addAll(incoming)

        binding.tvTitle.text = "$title (${entries.size})"
        binding.btnBack.setOnClickListener { finish() }
        // 🔒 TK-APPROVED (30.07.2026 সন্ধ্যা, ফটো-প্রুফে "ঠিক আছে" · খাতার
        // সারি B192): ছয়টা Draft সেকশনের সবগুলোতেই এই একটা বোতাম কাজ করে,
        // কারণ ছয়টাই এই একটা পর্দা দিয়ে খোলে। Follow-up-এর "⬇ Sheet"
        // বোতামের (খাতার সারি B69) হুবহু একই আচরণ — চাপলে এই স্ক্রিনে তখন
        // যা দেখা যাচ্ছে ঠিক তাই CSV হয়ে নামে, তারপর ফোনের চেনা Share/Save
        // পর্দা খোলে।
        binding.btnSheet.setOnClickListener { downloadSheet() }

        setupBranchPicker()
        wirePullToRefresh()
        renderList()
    }

    /** 🔒 TK-APPROVED (01.08.2026, ফটো-প্রুফে "ওকে"): Master শুধু এই তালিকাতেই
     *  ব্রাঞ্চ বদলাতে পারেন — DraftActivity-র branchPicker-এর হুবহু একই বাক্স
     *  ও নিয়ম। স্টাফ/ডাক্তারের জন্য বোতামটাই দেখা যায় না, কিছুই বদলায়নি।
     *  ব্রাঞ্চ বদলালে reloadFromCloud() দিয়ে **এই bucket-টাই** নতুন করে
     *  আনা হয় — bucketKey জানা না থাকলে (পুরনো caller) বোতাম দেখানো হয় না। */
    private fun setupBranchPicker() {
        if (user.role != "master" || bucketKey.isBlank()) return
        binding.branchPicker.visibility = android.view.View.VISIBLE
        // 🟢🔒 V398: Draft পর্দা থেকে যে ব্রাঞ্চ নিয়ে আসা হয়েছে সেটাই মনে রাখা
        //   ব্রাঞ্চ; না-থাকলে সরাসরি BranchFilterStore থেকে।
        branchArg = (branchArg?.takeIf { it.isNotBlank() }) ?: BranchFilterStore.get(this)
        binding.branchPicker.text = BranchFilterStore.pillText(this)
        binding.branchPicker.setOnClickListener {
            val branches = BranchFilterStore.choices()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Branch"))
                .setSingleChoiceItems(branches.toTypedArray(), BranchFilterStore.indexInChoices(this)) { dialog, which ->
                    val picked = branches[which]
                    branchArg = BranchFilterStore.set(this@DraftListActivity, picked)   // 🟢 V398
                    binding.branchPicker.text = BranchFilterStore.pillText(this@DraftListActivity)
                    dialog.dismiss()
                    reloadFromCloud()
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
        }
    }

    private fun wirePullToRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            android.graphics.Color.parseColor("#0EA25F"),
            android.graphics.Color.parseColor("#1167D8")
        )
        // V215 (§18.6, 31.07.2026): আগে pull-to-refresh শুধু পুরোনো memory তালিকা
        // আবার আঁকত — Delete/Restore অন্য পর্দায় হলে এখানে পুরোনো কার্ড থেকে যেত।
        // এখন সত্যিকারের নতুন data আনা হয় (repository.load) — যদি bucket/branch
        // জানা থাকে; না জানলে অন্তত tombstone-বাদ-দিয়ে redraw (deleted কার্ড আর
        // ফিরে আসে না)। ⛔ Free-plan: শুধু pull করলে বা onResume-এই fetch, ঘনঘন নয়।
        binding.swipeRefresh.setOnRefreshListener { reloadFromCloud() }
    }

    /** V215 (§18): এই bucket-এর সত্যিকারের নতুন তালিকা cloud থেকে এনে দেখায়। */
    private fun reloadFromCloud() {
        if (bucketKey.isBlank()) {
            // পুরোনো caller — bucket জানা নেই; অন্তত deleted কার্ড বাদ দিয়ে redraw।
            dropDeletedFromEntries()
            renderList()
            binding.swipeRefresh.postDelayed({
                try { binding.swipeRefresh.isRefreshing = false } catch (_: Throwable) {}
            }, 400L)
            return
        }
        lifecycleScope.launch {
            val fresh = try {
                withContext(Dispatchers.IO) {
                    val b = repository.load(branchArg, fromArg, toArg)
                    when (bucketKey) {
                        "received" -> b.received
                        "enqReject" -> b.enqReject
                        "visitReject" -> b.visitReject
                        "notComplete" -> b.notComplete
                        "complete" -> b.complete
                        "unexpectedTime" -> b.unexpectedTime
                        "refunded" -> b.refunded
                        else -> null
                    }
                }
            } catch (_: Throwable) { null }
            if (fresh != null) {
                entries.clear()
                entries.addAll(fresh)   // repository.load ইতিমধ্যে DeletedGuard মেনে বাদ দিয়েছে
            } else {
                // নেট না পেলে অন্তত locally-known deleted কার্ড সরিয়ে দেওয়া হয়।
                dropDeletedFromEntries()
            }
            renderList()
            try { binding.swipeRefresh.isRefreshing = false } catch (_: Throwable) {}
        }
    }

    /** locally tombstoned (এই ফোনেই Delete হওয়া) কার্ড বর্তমান তালিকা থেকে সরায়। */
    private fun dropDeletedFromEntries() {
        try {
            val it = entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                // 🔒 খাতার সারি B224: DeletedGuard tombstone (এই ফোনে সত্যি Delete)
                // ছাড়াও, Detail Screen-এ "not found" পাওয়া ghost সারিও এখানে সরে —
                // তাই Detail থেকে সরিয়ে Back এলে Incomplete তালিকাতেও সঙ্গে সঙ্গে যায়।
                if (DeletedGuard.isDeleted("followups", e.id, this) ||
                    DraftRepository.GhostHide.isHidden(e.id, e.mobile)) it.remove()
            }
        } catch (_: Throwable) { }
    }

    // V215 (§18): এই পর্দায় ফিরলেই (যেমন Timeline থেকে Delete করে Back) সদ্য-Delete
    // হওয়া কার্ড সঙ্গে সঙ্গে সরে যায় ও count ঠিক হয় — cloud call ছাড়াই (Free-plan)।
    override fun onResume() {
        super.onResume()
        dropDeletedFromEntries()
        renderList()
    }

    // 🔴🔴 TK-রিপোর্ট (07.08.2026): "নিচে স্ক্রল করে কোনো অ্যাকশন নিলে আবার একদম
    // প্রথমের দিকে চলে আসে কেন? আমি চাই যেখানে ছিলাম ঠিক সেই জায়গাতেই থাকব।"
    // **আসল কারণ (কোড ধরে যাচাই):** নিচের `renderList()` **প্রতিবার নতুন
    // `DraftCardAdapter` বানিয়ে** `recyclerDraft.adapter`-এ বসাত — নতুন adapter
    // বসানো মানেই RecyclerView-এর স্ক্রল আবার একদম শুরু থেকে। **সমাধান:**
    // Adapter একবারই তৈরি হয় (নিচের `draftAdapter`), পরে শুধু `updateItems()` —
    // তাই তালিকা হালনাগাদ হলেও স্ক্রলের জায়গা অক্ষত থাকে।
    // ⛔ কোনো ডেটা/অনুমতি/মোছার লজিক বদলায়নি — শুধু adapter পুনর্ব্যবহার।
    private var draftAdapter: DraftCardAdapter? = null

    // 🟢 B631 (11.08.2026): বাল্ক Delete/Restore চলাকালীন ছোট ভাসমান "মুছে ফেলা হচ্ছে x/N" ইঙ্গিত।
    private var bulkProgressView: android.widget.TextView? = null

    private fun renderList() {
        binding.tvTitle.text = binding.tvTitle.text.toString().substringBefore(" (") + " (${entries.size})"
        if (entries.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
            binding.recyclerDraft.visibility = android.view.View.GONE
            binding.pickBar.visibility = android.view.View.GONE
            return
        }
        binding.tvEmpty.visibility = android.view.View.GONE
        binding.recyclerDraft.visibility = android.view.View.VISIBLE
        val existing = draftAdapter
        if (existing == null) {
            binding.recyclerDraft.layoutManager = LinearLayoutManager(this)
            val a = DraftCardAdapter(
                entries,
                currentUser = user,
                onCall = { e -> dial(e.mobile) },
                onWhatsApp = { e -> whatsApp(e.mobile) },
                onView = { e -> viewTimeline(e.mobile) },
                onRestore = { e -> restore(e) },
                onDelete = { e -> confirmDelete(e) },
                onPickChanged = { refreshPickBar() }
            )
            draftAdapter = a
            binding.recyclerDraft.adapter = a
            binding.btnPickCancel.setOnClickListener { a.clearPicks(); refreshPickBar() }
            binding.btnPickRestore.setOnClickListener { confirmBulk(restoreMode = true) }
            binding.btnPickDelete.setOnClickListener { confirmBulk(restoreMode = false) }
        } else {
            existing.updateItems(entries)
        }
        refreshPickBar()
    }

    // ─────────────────────────────────────────────────────────────────────
    // 🆕🔒 TK-নির্দেশ (07.08.2026, ফটো-প্রুফ অনুমোদিত) — "অনেকগুলো একসাথে মার্ক
    // করেও ডিলিট করতে পারি, অথবা অনেকগুলো একসাথে মার্ক করেও Restore করতে পারি"।
    // ⛔ **ঝুঁকি-নিয়ন্ত্রণ:** (১) প্রতিটা কাজ **একই প্রমাণিত পথেই** — Delete-এ
    //    `repository.deleteEnquiry(...)` ও Restore-এ `repository.restore(...)`,
    //    অর্থাৎ এক-এক করে চাপলে যা হতো হুবহু তাই; নতুন কোনো মোছার লজিক নেই।
    //    (২) অনুমতির নিয়ম অক্ষত — যেসব তালিকায় Master-এর অনুমতি লাগে সেখানে
    //    টিক-বক্সই দেখা যায় না (DraftCardAdapter.pickableTabs)। (৩) মোছা সবই
    //    Trash Bin-এ যায়, Master ফেরাতে পারেন। (৪) চাপার আগে "কতগুলো" দেখিয়ে
    //    নিশ্চিত করা হয়, বোতাম একবারেই বন্ধ হয় (দুইবার চাপে দুইবার চলে না)।
    // ─────────────────────────────────────────────────────────────────────
    private fun refreshPickBar() {
        val n = draftAdapter?.pickedIds?.size ?: 0
        binding.pickBar.visibility = if (n > 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvPickCount.text = "$n selected"
        // Restore যেসব তালিকায় প্রযোজ্য নয় (My Enquiry / Unexpected Time) সেখানে
        // বোতামটা লুকানো — নইলে চাপলে কিছুই হতো না, বিভ্রান্তি হতো।
        val restorable = bucketKey == "enqReject" || bucketKey == "visitReject"
        binding.btnPickRestore.visibility = if (restorable) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmBulk(restoreMode: Boolean) {
        val a = draftAdapter ?: return
        val picked = a.pickedEntries()
        if (picked.isEmpty()) return
        val what = if (restoreMode) NoBengali.s("Restore") else NoBengali.s("Delete")
        val msg = if (restoreMode)
            "${picked.size} selected record(s) will be restored to the previous section."
        else
            "${picked.size} selected record(s) will be moved to Trash Bin. Master can restore them later."
        val bulkTitle = if (restoreMode) "Restore Selected Records?" else "Delete Selected Records?"
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, bulkTitle))
            .setMessage(msg + "\n\n" + picked.joinToString("\n") { "• " + it.name.ifBlank { it.mobile } })
            .setPositiveButton(what, null)
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // 🟢 B631 (11.08.2026, TK-রিপোর্ট "একবার চাপলে হচ্ছে না, ২০ বার চাপতে হয়"):
            // আসল সমস্যা চাপ নয় — ধীর নেটে অনেকগুলো নাম একটার পর একটা Trash-cascade
            // হতে ১৫-২৫ সেকেন্ড লাগত, আর তখন Delete বোতাম শুধু নিষ্ক্রিয় থাকত (কোনো
            // "মুছছে…" ইঙ্গিত নেই) — তাই জমে থাকা মনে হত, বারবার চাপা হত।
            // এখন: সঙ্গে সঙ্গে ডায়ালগ বন্ধ + বাছাই করা কার্ড তালিকা থেকে সরিয়ে দিই
            // (instant), পেছনে cascade চলে, ছোট "মুছে ফেলা হচ্ছে x/N" ভাসমান ইঙ্গিত;
            // কোনোটা পেছনে ব্যর্থ হলে সেই কার্ড আবার ফিরিয়ে আনি — কিছুই হারায় না।
            // ⛔ মোছার/ফেরানোর আসল ডাক (deleteEnquiry / restore) এক অক্ষরও বদলায়নি;
            //    ক্লাউড-কল সংখ্যা হুবহু আগের মতোই (Free-plan egress বাড়ে না)।
            try { dlg.dismiss() } catch (_: Throwable) { }
            val items = picked.toList()
            entries.removeAll(items)   // instant: কার্ড সঙ্গে সঙ্গে সরে
            a.clearPicks()
            renderList()
            showBulkProgress(items.size, restoreMode)
            lifecycleScope.launch {
                var done = 0
                var failed = 0
                val failedItems = mutableListOf<DraftEntry>()
                for (e in items) {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            // ⛔ একক Restore/Delete-এর হুবহু একই ডাক (আচরণ/হিসাব অপরিবর্তিত)।
                            if (restoreMode) repository.restore(e, user)
                            else repository.deleteEnquiry(e, user, isFreeDeleteTab(e.tab)) == "OK"
                        } catch (_: Throwable) { false }
                    }
                    if (ok) done++ else { failed++; failedItems.add(e) }
                    updateBulkProgress(done + failed, items.size, restoreMode)
                }
                // ব্যর্থগুলো তালিকায় নিরাপদে ফিরিয়ে আনি (কিছু হারায় না, TK আবার চেষ্টা করতে পারেন)
                if (failedItems.isNotEmpty()) {
                    for (fe in failedItems) if (entries.none { it.id == fe.id }) entries.add(fe)
                    renderList()
                }
                removeBulkProgress()
                val completedAction = if (restoreMode) "restored" else "deleted"
                Toast.makeText(
                    this@DraftListActivity,
                    "$done record(s) $completedAction." + (if (failed > 0) " · $failed record(s) failed — please try again." else ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 🟢 B631: বাল্ক Delete/Restore চলাকালীন ছোট ভাসমান ইঙ্গিত — কোনো নতুন XML/লেআউট নয়,
    // content-রুটে একটা TextView বসিয়ে-সরিয়ে দেখানো; অন্য পর্দা/ডিজাইন কিছুই ছোঁয় না।
    private fun bulkProgressLabel(count: Int, total: Int, restoreMode: Boolean): String =
        NoBengali.s((if (restoreMode) "ফেরানো হচ্ছে" else "মুছে ফেলা হচ্ছে") + " $count/$total")
    private fun showBulkProgress(total: Int, restoreMode: Boolean) {
        try {
            removeBulkProgress()
            val root = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
            val d = resources.displayMetrics.density
            val tv = android.widget.TextView(this).apply {
                text = bulkProgressLabel(0, total, restoreMode)
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                val padH = (20 * d).toInt(); val padV = (13 * d).toInt()
                setPadding(padH, padV, padH, padV)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16 * d
                    setColor(android.graphics.Color.parseColor("#EE12233F"))
                }
            }
            val lp = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM
                bottomMargin = (100 * d).toInt()
            }
            root.addView(tv, lp)
            bulkProgressView = tv
        } catch (_: Throwable) { }
    }
    private fun updateBulkProgress(count: Int, total: Int, restoreMode: Boolean) {
        try { bulkProgressView?.text = bulkProgressLabel(count, total, restoreMode) } catch (_: Throwable) { }
    }
    private fun removeBulkProgress() {
        try {
            bulkProgressView?.let { v -> (v.parent as? android.view.ViewGroup)?.removeView(v) }
            bulkProgressView = null
        } catch (_: Throwable) { }
    }

    // TK-REQUESTED ADDITION (2026-07-18), Phase 1: same-day self-delete for
    // My Enquiry entries. Always confirms first (destructive-looking action,
    // even though it's recoverable via Trash Bin) and never silently fails.
    // 🔴 B302.1 (02.08.2026): "Refunded" ঘর থেকে Delete চাপলে এটা রোগীর
    // রেকর্ড মোছে (Enquiry নয়) — তাই এখানে আলাদা পথে পাঠানো হয়, নিচের
    // Enquiry-নির্দিষ্ট অংশ একটুও বদলায়নি।
    /** 🆕🔴 TK-নির্দেশ (07.08.2026) — যেসব তালিকা থেকে **মাস্টারের অনুমতি ছাড়াই**
     *  ডিলিট করা যাবে (সবই এনকোয়ারির তালিকা, কোনো টাকা জড়িত নয়)। পেশেন্ট
     *  সেকশনের তালিকা (notcomplete / complete / refunded) ইচ্ছাকৃতভাবে বাদ —
     *  সেখানে আগের মতোই Master-এর অনুমতি লাগবে। */
    private fun isFreeDeleteTab(tab: String): Boolean =
        tab == "received" || tab == "enqreject" || tab == "visitreject" || tab == "unexpected"

    private fun confirmDelete(e: DraftEntry) {
        if (e.tab == "refunded") { confirmDeleteRefundedPatient(e); return }
        // 🆕 TK-নির্দেশ (03.08.2026) — "Enquiry reject, Visit card reject —
        // পেমেন্ট সংক্রান্ত কোনো ব্যাপার না থাকলে স্টাফ নিজেই করতে পারবে,
        // Master-এর অনুমতি লাগবে না।" এই দুই তালিকায় (enqreject/visitreject)
        // কোনো টাকা জমা নেই বলে নিশ্চিত (Patient/Treatment ধাপেই শুধু টাকা
        // থাকে — notcomplete/complete তালিকা, ওখানে এই ছাড় নেই, আগের নিয়মই)।
        // তাই এই দুই তালিকায় সরাসরি Delete ডায়ালগ, Master-চেক ছাড়াই।
        // 🆕🔴 TK-নির্দেশ (07.08.2026, স্ক্রিনশটসহ — "ইনকয়ারি ডিলিট করার জন্য
        // মাস্টারের অনুমতি লাগবে না; ভিজিট সেকশন থেকে রিজেক্ট করতেও লাগবে না;
        // শুধুমাত্র পেশেন্ট সেকশন থেকে ডিলিট করতে গেলে অনুমতি লাগবে")।
        // TK নিজে বেছে দিয়েছেন কোন কোন তালিকায় ছাড় থাকবে:
        //   ছাড় (অনুমতি লাগবে না) — এগুলো সবই এনকোয়ারির তালিকা, টাকা জড়িত নয়:
        //     · received   = "My Enquiry (All Branch)"      🆕 (আগে অনুমতি লাগত)
        //     · enqreject  = "Enquiry Reject List"           (আগেও ছাড় ছিল)
        //     · visitreject= "Visit Reject List"             (আগেও ছাড় ছিল)
        //     · unexpected = "Unexpected Time Calls"        🆕 (আগে অনুমতি লাগত)
        //   অনুমতি লাগবে (আগের মতোই) — পেশেন্ট সেকশন:
        //     · notcomplete = "Incomplete Patient" · complete = "Complete Patient"
        //     · refunded    = উপরের আলাদা পথে (confirmDeleteRefundedPatient)
        // ⛔ মোছার আসল পথ (Trash Bin + followup cascade) এক অক্ষরও বদলায়নি —
        //    ডিলিট আগের মতোই ফেরানো যায় (Master → Trash Bin → Restore)।
        // (আগের কোড ছিল: if (e.tab == "enqreject" || e.tab == "visitreject"))
        if (isFreeDeleteTab(e.tab)) {
            showDeleteEnquiryDialogDirect(e)
            return
        }
        // 🔒 খাতার সারি B98 (TK, 29.07.2026 রাত ১০.১০): *"মাস্টার ছাড়া কেউ ডিলিট
        // করবে না। staff-রা করতে চাইলে Master admin-এর কাছে অনুমতি নিতে হবে —
        // Master-এর ঘন্টাতে যাবে।"*
        // ⛔ Timeline পর্দার সঙ্গে **হুবহু একই নিয়ম** — দুই জায়গায় আলাদা আচরণ
        //    যেন কখনো না হয়।
        if (!DeletePermission.canDeleteNow(user)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Master-এর অনুমতি লাগবে"))
                .setMessage(
                    "${e.name.ifBlank { e.mobile }}\n\n" +
                    "ডিলিট করতে পারেন শুধু Master Admin।\n" +
                    "অনুরোধ পাঠালে সেটা Master-এর ঘন্টায় যাবে — তিনি অনুমতি দিলে তবেই মুছবে।\n\n" +
                    "⛔ এখনই কিছুই মুছবে না।"
                )
                .setPositiveButton("অনুরোধ পাঠান") { _, _ ->
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            DeletePermission.sendRequest(
                                this@DraftListActivity, user, "Enquiry",
                                e.name, e.mobile, e.patientId, e.branch
                            )
                        }
                        Toast.makeText(
                            this@DraftListActivity, NoBengali.s(if (ok) "Master-কে অনুরোধ পাঠানো হয়েছে" else "পাঠানো গেল না — নেট চেক করুন"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show().also { PremiumAlert.paint(it) }
            return
        }
        showDeleteEnquiryDialogDirect(e)
    }

    /** আসল Delete-ডায়ালগ — Master-চেক আগেই পাশ হয়ে গেছে (বা লাগেই না)
     *  এমন অবস্থায় ডাকা হয়, দুই জায়গা থেকে (উপরের দুটো পথ থেকেই) পুনর্ব্যবহার। */
    private fun showDeleteEnquiryDialogDirect(e: DraftEntry) {
        // 🔴 B334 (03.08.2026, একই ক্লাসের বাগ — PaymentActivity.kt-এ TK
        // যেটা ধরেছিলেন): "Delete" বোতাম চাপার পরেও সক্রিয় থাকত, একাধিকবার
        // চাপলে একাধিকবার ডিলিট-কল যেতে পারত। প্রথম চাপেই বোতাম বন্ধ।
        val delEnqDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Delete Enquiry?"))
            .setMessage("${e.name.ifBlank { e.mobile }} — this will move to Trash Bin (Master can restore it from there).")
            .setPositiveButton("Delete", null)
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
        delEnqDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            delEnqDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
            delEnqDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                lifecycleScope.launch {
                    // 🆕 (07.08.2026) — উপরের একই তালিকা-নিয়ম এখানেও (ভেতরের
                    // দ্বিতীয় গেট `TrashHelper.canDelete`-ও পাশ কাটে), নইলে
                    // পর্দায় ছাড় দিলেও "PERMISSION" ফেরত আসত।
                    // (আগে: e.tab == "enqreject" || e.tab == "visitreject")
                    val bypass = isFreeDeleteTab(e.tab)
                    val result = withContext(Dispatchers.IO) { repository.deleteEnquiry(e, user, bypass) }
                    if (result == "OK") {
                        entries.remove(e)
                        Toast.makeText(this@DraftListActivity, "Deleted — moved to Trash Bin", Toast.LENGTH_SHORT).show()
                        renderList()
                    } else if (result == "NOT_FOUND") {
                        // 🔒🔒 খাতার সারি B224 (TK verified live-test, 01.08.2026): এই সারি
                        // database-এ সত্যিই আর নেই। আগে শুধু "Record not found" দেখিয়ে
                        // সারিটা তালিকায় রেখে দিত (Action-ও খোলা যেত)। এখন এই ফোনের
                        // চোখে-দেখা তালিকা/জমানো cache থেকে সঙ্গে সঙ্গে সরিয়ে count ঠিক
                        // করা হয়। ⛔ কোনো cloud/tombstone নয় (purgeGhostFromCache-এর
                        // নোট দ্রষ্টব্য) — অন্য ফোনের এখনো sync না-হওয়া আসল রেকর্ড
                        // "not found" পাওয়ার কারণে স্থায়ীভাবে মুছবে না।
                        entries.remove(e)
                        withContext(Dispatchers.IO) { repository.purgeGhostFromCache(e.id, e.mobile) }
                        Toast.makeText(this@DraftListActivity, "Already deleted — removed from the list", Toast.LENGTH_SHORT).show()
                        renderList()
                    } else {
                        val msg = when (result) {
                            "PERMISSION" -> "You don't have permission to delete this"
                            else -> "Could not delete — network is too slow/unstable, please retry"
                        }
                        Toast.makeText(this@DraftListActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                    delEnqDialog.dismiss()
                }
        }
    }

    /** 🔴 B302.1 (02.08.2026): "Refunded" ঘরের Delete — উপরের Enquiry-পথের
     *  সমান্তরাল, শুধু রোগীর রেকর্ডের জন্য। `deletePatientRecord()` অনুমতি
     *  না থাকলে "PERMISSION" ফেরায়, তখন Master-এর ঘন্টায় অনুরোধ পাঠানোর
     *  একই বার্তা দেখানো হয় (উপরের Enquiry পপ-আপের হুবহু একই ভাষা)। */
    private fun confirmDeleteRefundedPatient(e: DraftEntry) {
        // 🔴 B334 (03.08.2026, একই ক্লাসের বাগ) — প্রথম চাপেই বোতাম বন্ধ,
        // একাধিক ডিলিট-কল আটকাতে।
        val delPatDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(PremiumAlert.header(this, "Delete Patient?"))
            .setMessage("${e.name.ifBlank { e.mobile }} — this will move to Trash Bin (Master can restore it from there). Their Follow-up/Payment history stays intact and returns with them if restored.")
            .setPositiveButton("Delete", null)
            .setNegativeButton("Cancel", null)
            .show().also { PremiumAlert.paint(it) }
        delPatDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            delPatDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
            delPatDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { repository.deletePatientRecord(e, user) }
                    when (result) {
                        "OK" -> {
                            entries.remove(e)
                            Toast.makeText(this@DraftListActivity, "Deleted — moved to Trash Bin", Toast.LENGTH_SHORT).show()
                            renderList()
                        }
                        "NOT_FOUND" -> {
                            entries.remove(e)
                            withContext(Dispatchers.IO) { repository.purgeGhostFromCache(e.id, e.mobile) }
                            Toast.makeText(this@DraftListActivity, "Already deleted — removed from the list", Toast.LENGTH_SHORT).show()
                            renderList()
                        }
                        "PERMISSION" -> {
                            androidx.appcompat.app.AlertDialog.Builder(this@DraftListActivity)
                                .setCustomTitle(PremiumAlert.header(this@DraftListActivity, "Master-এর অনুমতি লাগবে"))
                                .setMessage(
                                    "${e.name.ifBlank { e.mobile }}\n\n" +
                                    "ডিলিট করতে পারেন শুধু Master Admin।\n" +
                                    "অনুরোধ পাঠালে সেটা Master-এর ঘন্টায় যাবে — তিনি অনুমতি দিলে তবেই মুছবে।\n\n" +
                                    "⛔ এখনই কিছুই মুছবে না।"
                                )
                                .setPositiveButton("অনুরোধ পাঠান") { _, _ ->
                                    lifecycleScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            DeletePermission.sendRequest(
                                                this@DraftListActivity, user, "Patient",
                                                e.name, e.mobile, e.patientId, e.branch
                                            )
                                        }
                                        Toast.makeText(
                                            this@DraftListActivity, NoBengali.s(if (ok) "Master-কে অনুরোধ পাঠানো হয়েছে" else "পাঠানো গেল না — নেট চেক করুন"),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show().also { PremiumAlert.paint(it) }
                        }
                        else -> Toast.makeText(this@DraftListActivity, "Could not delete — network is too slow/unstable, please retry", Toast.LENGTH_SHORT).show()
                    }
                    delPatDialog.dismiss()
                }
        }
    }

    private fun dial(mobile: String) {
        // TK-REQUESTED (2026-07-24): "everywhere calling is possible in the
        // project" -- shared CallChooser.kt (Phone/Superfone/etc. picker,
        // Truecaller excluded).
        try { CallChooser.open(this, mobile) }
        catch (e: ActivityNotFoundException) { Toast.makeText(this, "No phone app", Toast.LENGTH_SHORT).show() }
    }

    private fun whatsApp(mobile: String) {
        // 🔒 V235 (TK, WhatsApp Chooser project-wide): কেন্দ্রীয় chooser (Personal/Business)।
        WhatsAppMessageChooser.send(this, mobile)
    }

    private fun viewTimeline(mobile: String) {
        val d = mobile.filter { it.isDigit() }.takeLast(10)
        // 🚨 TK-REPORTED (29.07.2026 সন্ধ্যা ৬.১৮, ছবিসহ · খাতার সারি B124):
        // *"মেইন কার্ড থেকে আমাকে ডিলিট অপশন কেন দেখাবে? সেখানে তো আগে
        //   রিজেক্ট আসতে হতো।"*
        // ⛔ আগে Timeline ঠিক করত **রেকর্ডের status দেখে** — status যদি কোনো
        //    কারণে Cancelled/Incomplete থাকে অথচ কার্ডটা চালু তালিকায় থাকে,
        //    তখন চালু কার্ডেও Delete উঠে যেত। এখন ঠিক হয় **কোথা থেকে খোলা
        //    হলো** তা দেখে: ডিলিট শুধু Draft-এর বাতিল তালিকা থেকে খুললে।
        // ⛔ "My Enquiry (All Branch)" চালু তালিকা — সেখান থেকে ডিলিট নয়।
        val t = intent.getStringExtra("title") ?: ""
        val closedBucket = t.contains("Reject", true) ||
            t.contains("Complete", true) ||     // Incomplete Patient · Complete Patient
            t.contains("Unexpected", true)
        startActivity(
            Intent(this, PatientTimelineActivity::class.java)
                .putExtra("mobile", d)
                .putExtra("fromDraftClosed", closedBucket)
        )
    }

    private fun restore(e: DraftEntry) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.restore(e, user) }
            if (ok) {
                entries.remove(e)
                Toast.makeText(this@DraftListActivity, "Restored — moved back to its section", Toast.LENGTH_SHORT).show()
                renderList()
            } else {
                Toast.makeText(this@DraftListActivity, "Restore failed — check connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 🔒 TK-APPROVED (30.07.2026 সন্ধ্যা, ফটো-প্রুফে "ঠিক আছে" · খাতার সারি
     * B192): TK: *"শুধুমাত্র ইনকমপ্লিট পেশেন্ট এর ক্ষেত্রেই নয়, সমস্ত
     * সেকশনের ক্ষেত্রেই Google Sheets এ ডাউনলোড করার ব্যবস্থা রাখবেন।"*
     *
     * এই স্ক্রিনে **তখন যা দেখা যাচ্ছে ঠিক তা-ই** (Restore/Delete হয়ে যাওয়া
     * সারি বাদ দিয়ে, একই ক্রমে) CSV হয়ে নামে। তারপর ফোনের চেনা Share/Save
     * পর্দা খোলে — সেখান থেকে Drive/Downloads-এ রাখা যায় বা Gmail/WhatsApp-এ
     * পাঠানো যায়। Google Sheets/Excel দুটোতেই সরাসরি খোলে।
     *
     * ⛔ ছয়টা Draft সেকশনই এই একটা পর্দা দিয়ে খোলে, তাই এই একটা ফাংশনেই
     *    সবকটাতে কাজ করে — আলাদা করে কিছু বসাতে হয়নি।
     * ⛔ ডেটাবেসে কিছু লেখা হয় না, ক্লাউডে একটাও বাড়তি অনুরোধ নেই — আগে
     *    থেকেই স্ক্রিনে-লোড-করা তালিকা থেকেই ফাইল বানানো হয়।
     */
    private fun downloadSheet() {
        if (sheetBusy) return
        if (entries.isEmpty()) {
            Toast.makeText(this, "Nothing to download — the list is empty", Toast.LENGTH_SHORT).show()
            return
        }
        sheetBusy = true
        Toast.makeText(this, "Preparing sheet…", Toast.LENGTH_SHORT).show()
        val rows = ArrayList(entries)
        val title = screenTitle
        lifecycleScope.launch {
            val file = try {
                withContext(Dispatchers.IO) { DraftSheetExporter.write(this@DraftListActivity, title, rows) }
            } catch (_: Throwable) { null }
            sheetBusy = false
            if (file == null || !file.exists()) {
                Toast.makeText(this@DraftListActivity, "Could not make the sheet — please try again", Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@DraftListActivity, "$packageName.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Save / Send sheet"))
            } catch (_: Throwable) {
                Toast.makeText(this@DraftListActivity, "Sheet made, but this phone could not open the share window", Toast.LENGTH_LONG).show()
            }
        }
    }
}
