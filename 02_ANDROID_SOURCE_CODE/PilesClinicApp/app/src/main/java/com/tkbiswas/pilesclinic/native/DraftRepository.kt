package com.tkbiswas.pilesclinic.native

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native rebuild -- Draft (Master/Staff). Mirrors web draffHome()'s five
 * categories, computed from live Supabase data:
 *   received     : all enquiries (My Enquiry, All Branch)
 *   enqReject    : Inquiry-stage follow-ups marked Cancelled (Reject)
 *   visitReject  : Visit-stage (stage "Patient") follow-ups marked Cancelled
 *   notComplete  : Patient-stage (stage "Treatment") follow-ups marked Incomplete
 *   complete     : patients with a bill fully paid (bill>0, due==0)
 *
 * Each entry carries its follow-up id + origin tab so a Restore returns it to
 * the exact section it came from (web restoreDraftEntry), and its date so the
 * Custom-date / Monthly filter can narrow the list.
 */
data class DraftEntry(
    val name: String,
    val mobile: String,
    val extra: String,
    val branch: String = "",
    val disease: String = "",
    val stage: String = "",
    val patientId: String = "",
    val id: String = "",
    val tab: String = "",
    val recordDate: String = "",
    val nextFollow: String = "",
    val lastRemark: String = "",
    // TK-REQUESTED ADDITION (2026-07-18): who received/created this record —
    // needed for the same-day self-delete permission check (TrashHelper).
    // Blank for any entry type that doesn't need it; nothing else affected.
    val createdByMobile: String = ""
) : java.io.Serializable

data class DraftBuckets(
    val received: List<DraftEntry>,
    val enqReject: List<DraftEntry>,
    val visitReject: List<DraftEntry>,
    val notComplete: List<DraftEntry>,
    val complete: List<DraftEntry>,
    // TK-REQUESTED ADDITION (2026-07-18): every Enquiry saved with Call
    // Timing = "Unexpected Time" (staff took the call outside official
    // hours), so TK can see at month-end how many such calls came in and
    // how many of those numbers actually converted to a real patient/
    // treatment -- to decide staff incentive pay. Purely additive; the
    // other 5 buckets and their screens are untouched.
    val unexpectedTime: List<DraftEntry>,
    // 🔴 TK-নির্দেশ (02.08.2026): Advance/Treatment টাকা সম্পূর্ণ Refund হয়ে
    // গেলে (Approved Refund আছে ও নেট জমা ঠিক ₹0) সেই রোগী আর Enquiry/Visit/
    // Patient কোনো কার্ডেই থাকে না — এই নতুন ঘরে (রেকর্ড অক্ষত, শুধু কার্ড
    // সরে)। পরে আবার টাকা জমা দিলে (নেট জমা আর ০ থাকবে না) স্বয়ংক্রিয়ভাবেই
    // এই ঘর থেকে সরে আবার Patient কার্ডে ফিরে যাবে (স্থির-সংরক্ষিত কোনো
    // status নয়, প্রতিবার লাইভ হিসাব করে বার করা হয়)।
    val refunded: List<DraftEntry> = emptyList()
)

class DraftRepository(private val context: Context? = null) {

    // TK-REQUESTED ADDITION (2026-07-20): same "show what was already on the
    // phone instantly" pattern added to the other screens today. load()
    // below (fetch/merge/bucket logic) is completely unchanged except for
    // saving into this cache right before it returns.
    private val CACHE_PREFS = "draft_buckets_cache"

    private fun cacheKey(branchFilter: String?, from: String?, to: String?) =
        "cache_${branchFilter ?: "All"}_${from ?: "-"}_${to ?: "-"}"

    private fun serializeEntries(list: List<DraftEntry>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        for (e in list) {
            arr.put(
                org.json.JSONObject()
                    .put("name", e.name).put("mobile", e.mobile).put("extra", e.extra)
                    .put("branch", e.branch).put("disease", e.disease).put("stage", e.stage)
                    .put("patientId", e.patientId).put("id", e.id).put("tab", e.tab)
                    .put("recordDate", e.recordDate).put("nextFollow", e.nextFollow)
                    .put("lastRemark", e.lastRemark).put("createdByMobile", e.createdByMobile)
            )
        }
        return arr
    }

    private fun deserializeEntries(arr: org.json.JSONArray): List<DraftEntry> {
        val list = mutableListOf<DraftEntry>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            list.add(
                DraftEntry(
                    name = r.optString("name", ""), mobile = r.optString("mobile", ""),
                    extra = r.optString("extra", ""), branch = r.optString("branch", ""),
                    disease = r.optString("disease", ""), stage = r.optString("stage", ""),
                    patientId = r.optString("patientId", ""), id = r.optString("id", ""),
                    tab = r.optString("tab", ""), recordDate = r.optString("recordDate", ""),
                    nextFollow = r.optString("nextFollow", ""), lastRemark = r.optString("lastRemark", ""),
                    createdByMobile = r.optString("createdByMobile", "")
                )
            )
        }
        return list
    }

    // myMobile ডিফল্ট ফাঁকা — তাই পুরনো কোনো ডাক ভাঙে না; ফাঁকা হলে
    // মেশানোর ধাপটা এমনিতেই বাদ যায় (আগের ব্যবহার হুবহু এক)।
    fun loadCachedBuckets(branchFilter: String?, from: String?, to: String?, myMobile: String = ""): DraftBuckets? {
        val ctx = context ?: return null
        val json = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).getString(cacheKey(branchFilter, from, to), null) ?: return null
        return try {
            val obj = org.json.JSONObject(json)
            // 🔒 V215 (§18): জমানো (cache) তালিকা আগে থেকেই bucket করা থাকে, তাই
            // সদ্য-Delete-করা রোগীর tombstoned সারি instant-open-এ ক্ষণিকের জন্য
            // ফিরে আসতে পারত। এখানেও DeletedGuard মেনে বাদ দেওয়া হয় — fresh load()
            // যেমন বাদ দেয় ঠিক তেমনই, যাতে flash-back না হয়।
            fun dropDeleted(list: List<DraftEntry>): List<DraftEntry> =
                list.filter { e ->
                    try { !DeletedGuard.isDeleted("followups", e.id, ctx) } catch (_: Throwable) { true }
                }
            DraftBuckets(
                received = deserializeEntries(obj.getJSONArray("received")),
                enqReject = dropDeleted(deserializeEntries(obj.getJSONArray("enqReject"))),
                visitReject = dropDeleted(deserializeEntries(obj.getJSONArray("visitReject"))),
                notComplete = dropDeleted(deserializeEntries(obj.getJSONArray("notComplete"))),
                complete = deserializeEntries(obj.getJSONArray("complete")),
                unexpectedTime = deserializeEntries(obj.getJSONArray("unexpectedTime")),
                // পুরনো cache-এ এই চাবি নাও থাকতে পারে (নতুন ফিচার) — optJSONArray
                // দিয়ে নিরাপদে ফাঁকা তালিকা ধরা হয়, পুরনো cache ভেঙে যায় না।
                refunded = deserializeEntries(obj.optJSONArray("refunded") ?: org.json.JSONArray())
            ).let { if (myMobile.isBlank()) it else mergeOwnPhoneEnquiries(it, myMobile) }
        } catch (t: Throwable) { null }
    }

    /**
     * ⚡ TK-এর স্থায়ী নিয়ম (28.07.2026 ২.৩০ pm): **"আমি আমার ফোনে যা যা কাজ
     * করবো, সেটা যেন সাথে সাথেই দেখায়।"**
     *
     * ক্লাউড থেকে আনার পথে এই ফোনের নিজের এনকোয়ারি আগেই মেশানো হত, কিন্তু
     * **জমানো তালিকা** দেখানোর পথে হত না — তাই ধীর লাইনে নিজের করা এনকোয়ারি
     * "My Enquiry (All Branch)"-তে দেরিতে আসত। এখন দুই পথেই মেশে।
     *
     * ⛔ কোনো নতুন ক্লাউড-কল নেই · কোনো সারি বাদ যায় না — শুধু যোগ হয়।
     * "My Enquiry" ঘরটাই ব্যবহারকারীর নিজের ঘর, তাই এখানে ব্রাঞ্চ দেখা হয় না
     * (আগের নিয়মের সঙ্গে হুবহু মিল — উপরের ক্লাউড-পথেও ঠিক তাই)।
     */
    private fun mergeOwnPhoneEnquiries(cached: DraftBuckets, myMobile: String): DraftBuckets {
        val ctx = context ?: return cached
        return try {
            val myDigits = myMobile.filter { it.isDigit() }.takeLast(10)
            if (myDigits.length != 10) return cached
            val local = LocalWorkflowStore(ctx).pendingEnquiries()
            if (local.length() == 0) return cached
            val seen = HashSet<String>()
            for (e in cached.received) if (e.id.isNotBlank()) seen.add(e.id)
            val extra = mutableListOf<DraftEntry>()
            for (i in 0 until local.length()) {
                val row = local.optJSONObject(i) ?: continue
                // 🔒 খাতার সারি B81 (29.07.2026): মেলানোর নিয়ম উপরের ক্লাউড-পথের
                // সঙ্গে **হুবহু এক** — `createdBy` (যিনি ফর্ম ভরেছেন) **অথবা**
                // `receivedBy` (যিনি কল ধরেছেন)। আগে এখানে শুধু `receivedBy`
                // দেখা হত, তাই তিন-চাপ দিয়ে অন্য কারো নাম বসালে **যিনি ফর্মটা
                // ভরেছেন তাঁর নিজের তালিকা থেকেই এন্ট্রিটা হারিয়ে যেত**।
                val byC = row.s("createdBy").filter { it.isDigit() }.takeLast(10)
                val byR = row.s("receivedBy").filter { it.isDigit() }.takeLast(10)
                if (byC != myDigits && byR != myDigits) continue
                val id = row.s("id")
                if (id.isBlank() || !seen.add(id)) continue
                extra.add(entry(row, "received"))
            }
            if (extra.isEmpty()) cached else cached.copy(received = extra + cached.received)
        } catch (_: Throwable) { cached }
    }

    private fun saveCachedBuckets(branchFilter: String?, from: String?, to: String?, buckets: DraftBuckets) {
        val ctx = context ?: return
        try {
            val obj = org.json.JSONObject()
                .put("received", serializeEntries(buckets.received))
                .put("enqReject", serializeEntries(buckets.enqReject))
                .put("visitReject", serializeEntries(buckets.visitReject))
                .put("notComplete", serializeEntries(buckets.notComplete))
                .put("complete", serializeEntries(buckets.complete))
                .put("unexpectedTime", serializeEntries(buckets.unexpectedTime))
                .put("refunded", serializeEntries(buckets.refunded))
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit().putString(cacheKey(branchFilter, from, to), obj.toString()).apply()
        } catch (_: Throwable) { }
    }

    /**
     * 🔒🔒 খাতার সারি B224 (TK verified live-test, 01.08.2026 — ছবিসহ):
     * *"DEMO TEST ও TK BISWAS-কে Delete করলে 'Record not found' আসছে, অথচ ওই
     *  দুই রেকর্ড এখনও Incomplete Patient তালিকা ও পুরোনো Detail Screen-এ থেকে
     *  যাচ্ছে, Action-ও খোলা যাচ্ছে।"*
     *
     * database-এ **আর নেই** এমন সারি Delete চাপলে (deleteEnquiry / Timeline-এর
     * দুই Delete পথ থেকে "NOT_FOUND") আর error দেখিয়ে রেখে দেওয়া হয় না — এই
     * ফাংশন সেই সারির **এই ফোনের জমানো (display-cache) ছায়া-কপি** মুছে দেয়,
     * যাতে তালিকায় ফিরে এলে সেটা আর দেখা না যায় ও count ঠিক থাকে।
     *
     * ⛔ **শুধু এই ফোনের display-cache** — কোনো cloud লেখা হয় না, `DeletedGuard`
     *    tombstone-ও বসানো হয় না। তাই অন্য ফোনের **এখনো sync না-হওয়া আসল
     *    রেকর্ড** কেবল "not found" পাওয়ার কারণে স্থায়ীভাবে মুছবে না — পরে sync
     *    হলে স্বাভাবিকভাবেই ফিরে আসবে।
     * ⛔ pending-sync queue (`LocalWorkflowStore`) ছোঁয়া হয় না, আর নিজের ফোনের
     *    এখনো না-ওঠা enquiry `mergeOwnPhoneEnquiries` দিয়ে load-এর সময় আবার
     *    মিশে যায় — তাই নিজের আসল কাজও নিরাপদ।
     *
     * id মিললে ঠিক সেই সারিটাই বাদ যায়; নইলে mobile (শেষ ১০ অঙ্ক) মিললে ওই
     * রোগীর draft ছায়া-সারি বাদ যায় (রোগী cloud-এ id ও mobile দুভাবেই সত্যিই
     * না-পাওয়ার পরেই তবে এটা ডাকা হয়, তাই over-remove হয় না)।
     */
    fun purgeGhostFromCache(id: String?, mobile: String? = null) {
        val ctx = context ?: return
        val wantId = id?.trim().orEmpty()
        val wantMob = mobile?.filter { it.isDigit() }?.takeLast(10).orEmpty()
        if (wantId.isBlank() && wantMob.length != 10) return
        // process চলাকালীন সব draft-পর্দাও যেন সঙ্গে সঙ্গে জানে (নিচের GhostHide)।
        GhostHide.hide(wantId, wantMob)
        try {
            val prefs = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val keys = prefs.all.keys.toList()   // সব (branch/date) cache-ঘর
            val editor = prefs.edit()
            var changedAny = false
            val bucketNames = listOf("received", "enqReject", "visitReject", "notComplete", "complete", "unexpectedTime", "refunded")
            fun isGhost(e: DraftEntry): Boolean {
                if (wantId.isNotBlank() && e.id == wantId) return true
                if (wantMob.length == 10 && e.mobile.filter { it.isDigit() }.takeLast(10) == wantMob) return true
                return false
            }
            for (key in keys) {
                val json = prefs.getString(key, null) ?: continue
                val obj = try { org.json.JSONObject(json) } catch (_: Throwable) { continue }
                var keyChanged = false
                for (n in bucketNames) {
                    val arr = obj.optJSONArray(n) ?: continue
                    val kept = deserializeEntries(arr).filterNot { isGhost(it) }
                    if (kept.size != arr.length()) {
                        obj.put(n, serializeEntries(kept))
                        keyChanged = true
                    }
                }
                if (keyChanged) { editor.putString(key, obj.toString()); changedAny = true }
            }
            if (changedAny) editor.apply()
        } catch (_: Throwable) { }
    }

    /**
     * 🔒 খাতার সারি B224 — উপরের ঘটনার in-memory অংশ। এক পর্দায় (Detail Screen)
     * "not found" সারি সরালে অন্য পর্দা (Incomplete তালিকা) যেটা আগেই মেমরিতে
     * লোড হয়ে আছে সেটাও যেন সঙ্গে সঙ্গে জানে — তাই এই process-জোড়া তালিকা।
     *
     * ⛔ **শুধু মেমরি, শুধু এই ফোন** — disk-tombstone নয়, cloud নয়,
     *    DeletedGuard নয়। app বন্ধ হলে মুছে যায় (তখন cloud-ই একমাত্র সত্য),
     *    তাই অন্য ফোনের আসল রেকর্ড কখনো স্থায়ীভাবে হারায় না।
     */
    object GhostHide {
        private val hiddenIds = java.util.Collections.synchronizedSet(HashSet<String>())
        private val hiddenMobiles = java.util.Collections.synchronizedSet(HashSet<String>())

        fun hide(id: String?, mobile: String?) {
            id?.trim()?.takeIf { it.isNotBlank() }?.let { hiddenIds.add(it) }
            mobile?.filter { it.isDigit() }?.takeLast(10)?.takeIf { it.length == 10 }?.let { hiddenMobiles.add(it) }
        }

        fun isHidden(id: String?, mobile: String?): Boolean {
            val i = id?.trim().orEmpty()
            if (i.isNotBlank() && hiddenIds.contains(i)) return true
            val m = mobile?.filter { it.isDigit() }?.takeLast(10).orEmpty()
            return m.length == 10 && hiddenMobiles.contains(m)
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun entry(row: JSONObject, tab: String, extra: String = ""): DraftEntry =
        DraftEntry(
            // 🔴🔴🔴 TK-REPORTED (31.07.2026): "Patient Name-এর জায়গায় Mobile
            // দুবার দেখানো"। আসল কারণ এইখানে পাওয়া গেল — এই সারিতেই আগে থেকে
            // name-এ mobile বসিয়ে দেওয়া হত, তাই DraftCardAdapter.kt-এ পরে
            // "UNKNOWN" ফিক্স বসালেও কখনো কাজ করত না (name কখনো
            // ফাঁকা পেত না, ইতিমধ্যেই mobile দিয়ে ভরা থাকত)। এখন name সত্যিই
            // ফাঁকা থাকলে ফাঁকাই থাকে — "UNKNOWN" adapter-এই বসে।
            name = row.s("name"),
            mobile = row.s("mobile"),
            extra = extra,
            branch = row.s("branch"),
            disease = row.s("disease"),
            stage = row.s("stage"),
            patientId = row.s("patientId"),
            id = row.s("id"),
            tab = tab,
            recordDate = row.s("date").ifBlank { row.s("visitDate").ifBlank { row.s("registrationDate") } }.take(10),
            nextFollow = row.s("nextFollow"),
            lastRemark = row.s("lastRemark").ifBlank { row.s("remarks").ifBlank { extra } },
            createdByMobile = row.s("receivedBy").ifBlank { row.s("createdBy") }
        )

    /** Merges any locally-pending rows (not yet synced) matching this branch
     *  filter into a cloud result set, avoiding duplicates by id.
     *  TK-REQUESTED ADDITION (2026-07-16): same fix as Follow-up/Doctor
     *  Queue/Today's Collection/Global Search/Full Journey -- Draft always
     *  read straight from the cloud with no awareness of a save still
     *  syncing in the background. */
    private fun mergeWithPending(cloud: JSONArray, pending: JSONArray, branchFilter: String?): JSONArray {
        val merged = JSONArray()
        val seenIds = HashSet<String>()
        for (i in 0 until cloud.length()) {
            val row = cloud.getJSONObject(i)
            seenIds.add(row.optString("id"))
            merged.put(row)
        }
        val allBranch = branchFilter == null || branchFilter == "All"
        for (i in 0 until pending.length()) {
            val row = pending.getJSONObject(i)
            val id = row.optString("id")
            val branchOk = allBranch || row.s("branch").equals(branchFilter, ignoreCase = true)
            if (id.isNotBlank() && branchOk && seenIds.add(id)) merged.put(row)
        }
        return merged
    }

    /** from/to are inclusive yyyy-MM-dd bounds; pass null for no date limit. */
    fun load(branchFilter: String?, from: String? = null, to: String? = null): DraftBuckets {
        val branchPart = if (branchFilter != null && branchFilter != "All")
            "branch=eq." + java.net.URLEncoder.encode(branchFilter, "UTF-8") else null

        // TK-REQUESTED FIX (2026-07-23): same root cause/fix as Follow-up's
        // Bill/Due bug and Chamber Attendance's board -- fetchList()
        // swallows a failed request into a silent empty array, which used
        // to get baked straight into the Draft lists (and then CACHED at
        // the end of load()), making a bucket look wrongly empty on a bad
        // connection, possibly stuck that way. Now uses fetchListOrNull():
        // a genuine failure (null) on ANY of these 4 falls back to the
        // last cached buckets instead of computing/caching wrong ones; if
        // there's no cache yet, falls through to empty exactly like before
        // (no behavior change for a first-ever load). A real empty result
        // (successful fetch, genuinely nothing in that bucket) is NOT
        // affected -- only a true failure is.
        // PERFORMANCE FIX (2026-07-25, TK-requested proactive sweep): the four
        // reads the Draft screen needs do not depend on each other, so they are
        // started together instead of one after the other (the screen used to
        // wait for the SUM of four round trips). Same four queries, same
        // filters/limits, same null-checks below . only the waiting is shorter.
        // TK-REQUESTED (2026-07-27): the "My Enquiry (All Branch)" bucket needs
        // THIS staff's own enquiries from every branch (see below). That read is
        // started HERE, together with the other four, so the screen does not wait
        // one extra round trip for it on a slow line -- the total waiting time is
        // unchanged. Master is excluded: he sees exactly the branch he selected.
        val myOwnDigits = run {
            val me = context?.let { NativeSession.current(it) }
            if (branchFilter == null || branchFilter == "All") "" 
            else if (me == null || me.role == "master") ""
            else me.mobile.filter { it.isDigit() }.takeLast(10)
        }
        val preDraft = runBlocking {
            // 🔵🔒 V441 (19.08.2026, TK-অনুমোদিত — Free Plan): Draft এই enquiry
            // তালিকা থেকে শুধু card/bucket বানাতে লাগা ঘরগুলোই পড়ে। Same rows, same
            // branch filter, same 5000 limit; narrow read ব্যর্থ হলে shared safety-net
            // আগের মতো full row দিয়ে retry করে — তালিকা ফাঁকা হয়ে যাবে না।
            val a = async(Dispatchers.IO) { SupabaseClient.fetchListSlimOrNull("enquiries", branchPart, 5000, SupabaseClient.ENQUIRY_COLS_DRAFT) }
            // 🔒 কোটা/গতি (29.07.2026, খাতার সারি B105): এই পর্দার `patients`
            // পড়াটা আগেই ছবি বাদ দিয়ে করা হয়েছিল, কিন্তু `followups`-এও
            // **ঠিক একই `photo` ঘর** আছে — সেটা তখন বাদ পড়েনি, তাই রোগীর ছবি
            // দু'বারের বদলে একবার নামত মাত্র। Draft পর্দায় কোথাও ছবি দেখানো
            // হয় না (কোডে মিলিয়ে দেখা হয়েছে — এই ফাইলে `photo` একবারও পড়া হয় না)।
            // ⛔ শুধু `photo` বাদ — বাকি প্রতিটা ঘর আগের মতোই আসে; ছাঁকনি ·
            //    limit · সাজানো কিছুই বদলায়নি, আর সরু পড়া ব্যর্থ হলে
            //    `fetchListSlimOrNull` নিজেই সব ঘর চেয়ে নেয়, তাই আসল ব্যর্থতা
            //    আগের মতোই `null` হয়ে ফেরে (নিচের ক্যাশ-ফলব্যাক ওটাই আশা করে)।
            val b = async(Dispatchers.IO) { SupabaseClient.fetchListSlimOrNull("followups", branchPart, 5000, SupabaseClient.FOLLOWUP_COLS_NO_PHOTO) }
            // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): this one
            // read used to bring down EVERY patient's PHOTO -- a full image
            // stored inside each row -- on a screen that shows no photo at all.
            // On TK's line that alone was most of the Draft screen's wait.
            // ⛔ NOTHING CHANGES: PATIENT_COLS_NO_PHOTO is every column of the
            // patients table EXCEPT "photo" (the same proven list Global
            // Search, Payment and Doctor already use), the filter/limit/order
            // are untouched, and fetchListSlimOrNull falls back to every column
            // by itself if the narrowed read ever fails -- so a genuine failure
            // is still reported as null exactly as before.
            val c2 = async(Dispatchers.IO) { SupabaseClient.fetchListSlimOrNull("patients", branchPart, 5000, SupabaseClient.PATIENT_COLS_NO_PHOTO) }
            // ⚡ খাতার সারি B135 (TK "হ্যাঁ", 29.07.2026 রাত ৯.৩০): এই পড়াটাও
            // `payments`-এর সব ঘর চাইত। Draft যে ঘরগুলো পড়ে (amount · date ·
            // branch · mobile · payType · receivedBy · createdBy …) সবই নিচের
            // তালিকায় আছে — প্রজেক্টে আগে থেকেই ব্যবহার হওয়া `PAYMENT_COLS_LIST`।
            // ⛔ কোনো হিসাব · ছাঁকনি · নিয়ম বদলায়নি; সরু পড়া ব্যর্থ হলে অ্যাপ
            //    নিজেই সব ঘর চেয়ে নেয়, আসল ব্যর্থতা আগের মতোই `null`।
            val d = async(Dispatchers.IO) { SupabaseClient.fetchListSlimOrNull("payments", branchPart, 5000, SupabaseClient.PAYMENT_COLS_LIST) }
            val e2 = async(Dispatchers.IO) {
                if (myOwnDigits.length == 10)
                    SupabaseClient.fetchListSlimOrNull(
                        "enquiries",
                        "or=(createdBy.like.*$myOwnDigits,receivedBy.like.*$myOwnDigits)",
                        2000,
                        SupabaseClient.ENQUIRY_COLS_DRAFT
                    )
                else null
            }
            listOf(a.await(), b.await(), c2.await(), d.await(), e2.await())
        }
        val myOwnEnquiries = preDraft[4]
        val enqRaw = preDraft[0]
        val followRaw = preDraft[1]
        val patientsRaw = preDraft[2]
        val paymentsRaw = preDraft[3]
        if (enqRaw == null || followRaw == null || patientsRaw == null || paymentsRaw == null) {
            loadCachedBuckets(branchFilter, from, to)?.let { return it }
        }
        var enq = enqRaw ?: org.json.JSONArray()
        var follow = followRaw ?: org.json.JSONArray()
        var patients = patientsRaw ?: org.json.JSONArray()
        var payments = paymentsRaw ?: org.json.JSONArray()
        context?.let { ctx ->
            val store = LocalWorkflowStore(ctx)
            enq = mergeWithPending(enq, store.pendingEnquiries(), branchFilter)
            follow = mergeWithPending(follow, store.pendingFollowUps(), branchFilter)
            patients = mergeWithPending(patients, store.pendingPatients(), branchFilter)
            payments = mergeWithPending(payments, store.pendingPayments(), branchFilter)
        }

        val paidByMobile = HashMap<String, Double>()
        // 🔴 TK-নির্দেশ (02.08.2026): কার Approved Refund আছে সেটাও এই একই
        // লুপে ধরে রাখা হচ্ছে (নিচে "Refunded" ঘর বানাতে লাগবে) — নতুন কোনো
        // নেট-কল লাগেনি, একই payments থেকেই।
        val hasApprovedRefundByMobile = HashSet<String>()
        // 🔴🔴 TK-অডিট-অনুরোধ (01.08.2026, Follow-up-এর Due-ভুল ধরার পরে সম্পূর্ণ
        // প্রজেক্ট যাচাই): এখানে Refund সারিও প্লেইন পজিটিভ Payment হিসেবে
        // যোগ হচ্ছিল — FollowUpRepository-র V238 `paidEffect` নিয়ম (approved
        // refund বিয়োগ, pending/rejected কোনো প্রভাব নেই) এখানে কখনো বসানো হয়নি।
        // ফল: Refund পাওয়া রোগীর "paid" ভুলভাবে বেশি দেখাত, তাই Due ভুলভাবে ০
        // বা কম হয়ে যেত — এই ফাইলেই নিচে Due==0 হলে রোগীকে "Complete Patient"
        // তালিকায় ফেলা হয় ও "✅ Treatment complete" লেখা হয়, তাই বাকি Due থাকা
        // সত্ত্বেও ভুল বাকেটে/ভুল স্ট্যাটাসে চলে যেতে পারত। এখন একই নিয়ম বসানো হলো।
        for (i in 0 until payments.length()) {
            val p = payments.getJSONObject(i)
            val payType = p.optString("payType", "")
            if (payType == "visit_fee" || payType == "attendance_mark") continue
            val paidEffect = when {
                PaymentModel.isApprovedRefund(p) -> -p.optDouble("amount", 0.0)
                PaymentModel.isRefundRow(p) -> 0.0
                else -> p.optDouble("amount", 0.0)
            }
            val pm = p.s("mobile").filter { it.isDigit() }.takeLast(10)
            if (pm.isNotBlank()) paidByMobile[pm] = (paidByMobile[pm] ?: 0.0) + paidEffect
            if (PaymentModel.isApprovedRefund(p) && pm.isNotBlank()) hasApprovedRefundByMobile.add(pm)
        }

        val received = mutableListOf<DraftEntry>()
        // 🚨 TK'S LOCKED RULE (told long before today, restated 27.07.2026):
        //   "জলপাইগুড়ির স্টাফ কিশানগঞ্জের জন্য এনকোয়ারি করলে সেই নম্বর কিশানগঞ্জের
        //    সব স্টাফ ও মাস্টার দেখবে। আর যে স্টাফ এন্ট্রিটা করেছে, সে সেটা দেখবে
        //    Draft-এর 'My Enquiry (All Branch)'-তে।"
        //
        // WHAT WAS BROKEN: this screen's four reads are all filtered to the
        // staff's OWN branch (branchPart, above). So the bucket labelled
        // "All Branch" was in fact showing one branch only -- an enquiry a
        // Jalpaiguri staff took FOR Kishanganj was filtered out by the very
        // first query, and the person who entered it had nowhere left in the
        // app to see it. The rule was right; this screen simply never asked
        // for those rows.
        //
        // FIX: one extra, narrow read -- enquiries whose createdBy/receivedBy is
        // THIS staff, with no branch limit -- merged into this ONE bucket only.
        // Nothing else on the screen changes: the other buckets, and Master's
        // All-Branch view (which already fetches everything), are untouched, and
        // no other staff's cross-branch row is fetched, so this cannot re-open
        // the cross-branch leak TK closed on 25.07.2026.
        val receivedRows = org.json.JSONArray()
        val receivedIds = HashSet<String>()
        // 🚨 TK-REPORTED, LIVE (29.07.2026 দুপুর ১.৪০, খাতার সারি B81 — স্টাফের
        // অভিযোগ: *"এই এন্ট্রিটা তো আমি করেছিলাম, সেখানে অন্যজনের নাম কেন
        // দেখাচ্ছে?"*)
        //
        // **আসল দোষ:** এই বাক্সটার নাম **"My Enquiry (All Branch)"**, অর্থাৎ
        // TK-এর নিয়ম অনুযায়ী এখানে **শুধু নিজের করা এন্ট্রি** থাকার কথা
        // (খাতার \"এক নম্বরে সব কল\" নিয়মের ৩ নম্বর ধারা: *\"যিনি ফর্মটা ফিলাপ
        // করেছিলেন, তিনি নম্বরটা দেখবেন নিজের Draft → My Enquiry-তে\"*)।
        // কিন্তু এখানে **ব্রাঞ্চের প্রতিটা এনকোয়ারি** ঢেলে দেওয়া হত — কে
        // করেছে তা দেখাই হত না। তাই স্টাফ নিজের তালিকায় **অন্য স্টাফের ও
        // মাস্টারের করা এন্ট্রি** দেখতেন, আর তাতে অন্যজনের নামই উঠত।
        //
        // এখন স্টাফ/ডাক্তার/ফিল্ড-এর ক্ষেত্রে **শুধু নিজের সারিই** ঢোকে —
        // মেলানো হয় ঠিক সেই একই নিয়মে যা উপরের নিজস্ব ক্লাউড-খোঁজায় ব্যবহার
        // হয় (`createdBy` **অথবা** `receivedBy`), তাই দুই পথে ফল কখনো আলাদা
        // হবে না, আর কারো প্রাপ্য এন্ট্রি হারাবে না।
        //
        // ⛔ **মাস্টারের দেখায় এক অক্ষরও বদলায়নি** — TK-এর ২৭.০৭.২০২৬-এর
        // নিয়ম: *"মাস্টার যা বেছেছেন ঠিক তাই দেখাবে"*; তাঁর জন্য `myOwnDigits`
        // ফাঁকা থাকে, তাই আগের মতোই সব সারি ঢোকে।
        // ⛔ বাড়তি কোনো ক্লাউড-কল নেই · অন্য কোনো বাক্স ছোঁয়া হয়নি।
        // ⚠️ এখানে `myOwnDigits` ব্যবহার করা হয়নি ইচ্ছে করেই — ওটা ব্রাঞ্চ
        // "All" হলে ফাঁকা থাকে (ওটা শুধু বাড়তি ক্লাউড-খোঁজা চালানোর শর্ত)।
        // ফিল্ড অফিসারের ব্রাঞ্চ "All", তাই ওটা ধরলে তাঁর তালিকাতেও সবার
        // এন্ট্রি ঢুকে পড়ত। তাই পরিচয়টা সরাসরি লগইন থেকে নেওয়া হয়।
        // ⛔ মাস্টার বাদ — তাঁর দেখা আগের মতোই থাকে। বাড়তি কোনো ক্লাউড-কল নেই।
        val myDigitsForBucket = run {
            val me = context?.let { NativeSession.current(it) }
            if (me == null || me.role == "master") "" else me.mobile.filter { c -> c.isDigit() }.takeLast(10)
        }
        for (i in 0 until enq.length()) {
            val row = enq.getJSONObject(i)
            if (myDigitsForBucket.length == 10) {
                val by = row.s("createdBy").filter { c -> c.isDigit() }.takeLast(10)
                val recv = row.s("receivedBy").filter { c -> c.isDigit() }.takeLast(10)
                if (by != myDigitsForBucket && recv != myDigitsForBucket) continue
            }
            if (receivedIds.add(row.s("id"))) receivedRows.put(row)
        }
        // TK'S ANSWER (27.07.2026): Master looks at ONE branch he selects, or All
        // Branch -- what Master sees must follow that choice exactly and must never
        // be widened by his own entries. So this own-rows addition is for
        // staff/doctor only (myOwnDigits is blank for Master, see above).
        run {
            val myDigits = myOwnDigits
            if (myDigits.length == 10) {
                val mineOrNull = myOwnEnquiries
                if (mineOrNull != null) {
                    for (i in 0 until mineOrNull.length()) {
                        val row = mineOrNull.getJSONObject(i)
                        if (receivedIds.add(row.s("id"))) receivedRows.put(row)
                    }
                }
                // The same enquiry may still be waiting on this very phone (not
                // yet uploaded). mergeWithPending() above drops it because its
                // branch is another branch's -- so add this staff's own pending
                // rows here, where branch must not matter.
                context?.let { ctx ->
                    val localEnq = LocalWorkflowStore(ctx).pendingEnquiries()
                    for (i in 0 until localEnq.length()) {
                        val row = localEnq.optJSONObject(i) ?: continue
                        // 🔒 খাতার সারি B81: উপরের ও ক্যাশের পথের সঙ্গে **হুবহু
                        // এক নিয়ম** — `createdBy` অথবা `receivedBy`। আগে শুধু
                        // `receivedBy` দেখা হত, তাই তিন-চাপ দিয়ে অন্য কারো নাম
                        // বসালে যিনি ফর্ম ভরেছেন তাঁর তালিকা থেকে এন্ট্রিটা
                        // হারিয়ে যেত।
                        val byC = row.s("createdBy").filter { c -> c.isDigit() }.takeLast(10)
                        val byR = row.s("receivedBy").filter { c -> c.isDigit() }.takeLast(10)
                        if (byC != myDigits && byR != myDigits) continue
                        if (receivedIds.add(row.s("id"))) receivedRows.put(row)
                    }
                }
            }
        }
        for (i in 0 until receivedRows.length()) received.add(entry(receivedRows.getJSONObject(i), "received"))

        // TK-REQUESTED ADDITION (2026-07-18): Unexpected Time calls, with a
        // one-glance conversion status per number so TK can tell at month-
        // end how many of these actually became a real patient/treatment,
        // for staff incentive decisions. Reuses the same patients+payments
        // data already fetched above -- no new network calls.
        // TK-REQUESTED (2026-07-27), ধাপ ৩খ — Draft পর্দা: this map kept
        // whichever patients row came LAST for a mobile, while the money screen,
        // Patient Details and the Report Card all use the ONE shared rule
        // (current branch -> the row with a real bill -> the first row). On a
        // duplicate registration that meant Draft could read the empty row and
        // report "Enquiry only / no bill yet" for a patient who is in fact under
        // treatment. Same rule everywhere now; with a single row (the normal
        // case) this map is exactly what it was before.
        val patientRowsByMobile = HashMap<String, org.json.JSONArray>()
        for (i in 0 until patients.length()) {
            val row = patients.getJSONObject(i)
            val m = row.s("mobile").filter { it.isDigit() }.takeLast(10)
            if (m.isNotBlank()) patientRowsByMobile.getOrPut(m) { org.json.JSONArray() }.put(row)
        }
        val patientByMobile = HashMap<String, JSONObject>()
        for ((m, rows) in patientRowsByMobile) {
            val chosen = PatientIdentity.pickPatientRow(rows, branchFilter.orEmpty())
            if (chosen != null) patientByMobile[m] = chosen
        }
        val unexpectedTime = mutableListOf<DraftEntry>()
        for (i in 0 until enq.length()) {
            val row = enq.getJSONObject(i)
            if (!row.s("timeType").equals("Unexpected Time", ignoreCase = true)) continue
            val m = row.s("mobile").filter { it.isDigit() }.takeLast(10)
            val pat = patientByMobile[m]
            val status = if (pat == null) {
                "Enquiry only — not registered"
            } else {
                val bill = pat.optDouble("bill", 0.0)
                val paid = paidByMobile[m] ?: 0.0
                val due = (bill - paid).coerceAtLeast(0.0)
                when {
                    bill <= 0.0 -> "Registered — no bill yet"
                    due <= 0.0 -> "✅ Treatment complete"
                    else -> "Registered — treatment ongoing"
                }
            }
            val by = row.s("receivedBy").ifBlank { row.s("createdBy") }
            // 🔒 TK-এর লক করা নিয়ম (খাতার সারি B96-এ আবার ধরা পড়ল, ছবিসহ):
            // *"By: ঘরে সবসময় স্টাফের নাম দেখাবে, কখনো কাঁচা মোবাইল নম্বর নয়।"*
            // এখানে নম্বরটাই সরাসরি দেখানো হত — যেমন `by 9883605917`।
            // এখন অ্যাপের বাকি সব জায়গার সেই একই নিয়মে নাম বসে (`KNE-LAXMI`)।
            // ⛔ নাম না পাওয়া গেলে আগের মতোই যা আছে তাই দেখায় — কিছু ভাঙে না।
            val byName = FollowUpModel.prettyStaff(by)
            unexpectedTime.add(entry(row, "unexpected", "$status${if (byName.isNotBlank()) " · by $byName" else ""}"))
        }

        // 🔴 TK-REPORTED (04.08.2026, ছবিসহ — Enquiry Reject List-এ একই মানুষ
        // দুইবার, যেমন MD SHARIF UDDIN): Patient Timeline-এ "View" চেপে
        // যাচাই করে TK নিশ্চিত হয়েছেন — ওই নম্বরে আসলে ডাটাবেসে **একটাই**
        // followups সারি (একই স্টাফ COB-UTTAMA-র ৩টা কল, সবই একই সারির
        // history-তে জমা)। তাহলে এই তিনটে তালিকায় (Enquiry/Visit Reject,
        // Incomplete) একই মোবাইল একাধিকবার আসছিল কেন — সেটা ডাটাবেসের
        // সমস্যা না, **এই স্ক্রিন বানানোর সময়ই একই নম্বর দুইবার তালিকায়
        // ঢুকে যাচ্ছিল** (ফোনে আটকে-থাকা পুরনো একটা লোকাল কপি + আসল
        // ক্লাউড কপি — দুটোরই id আলাদা বলে আগের mergeWithPending()-এর
        // id-ধরে-dedup সেটা ধরতে পারেনি)।
        //
        // ⛔ সমাধান শুধু এই তিনটে তালিকা **দেখানোর** সময় — ডাটাবেসে কিছু
        // মোছা/বদলানো হয় না, Restore/Delete/Save কোনো লজিক ছোঁয়া হয়নি।
        // একই মোবাইল একই বাকেটে দ্বিতীয়বার এলে, দুটোর মধ্যে যেটার history
        // বড় (মানে বেশি তথ্য জমা আছে) সেটাই রাখা হয়; সমান হলে যেটার
        // updatedAt নতুন সেটা রাখা হয়। ⛔ ভিন্ন মোবাইল/ভিন্ন বাকেট কখনো
        // একসাথে মেলানো হয় না।
        fun dedupByMobile(rows: List<JSONObject>): List<JSONObject> {
            val best = LinkedHashMap<String, JSONObject>()
            for (row in rows) {
                val key = row.s("mobile").filter { it.isDigit() }.takeLast(10)
                if (key.isBlank()) { best[row.s("id").ifBlank { java.util.UUID.randomUUID().toString() }] = row; continue }
                val existing = best[key]
                if (existing == null) { best[key] = row; continue }
                val existingHist = existing.optJSONArray("history")?.length() ?: 0
                val newHist = row.optJSONArray("history")?.length() ?: 0
                if (newHist > existingHist) { best[key] = row; continue }
                if (newHist == existingHist && row.s("updatedAt") > existing.s("updatedAt")) best[key] = row
            }
            return best.values.toList()
        }

        val enqRejectRows = mutableListOf<JSONObject>()
        val visitRejectRows = mutableListOf<JSONObject>()
        val notCompleteRows = mutableListOf<JSONObject>()
        for (i in 0 until follow.length()) {
            val row = follow.getJSONObject(i)
            // 🔒 V215 (§18, 31.07.2026): এই তালিকাগুলো cancelled/incomplete follow-up
            // সারি থেকেই বানানো হয়, তাই পুরো রোগী Delete করলেও (যেখানে ঐ সারিটা
            // tombstone হয় কিন্তু history-র জন্য cloud-এ Cancelled-ই থাকে) আগে সারিটা
            // এখানে ফিরে আসত। এখন DeletedGuard-এ tombstone থাকলে বাদ — ঠিক যেমন
            // FollowUpRepository/RegistrationRepository আগে থেকেই করে। এতে Delete-এর
            // পর নাম সঙ্গে সঙ্গে সরে যায় ও Back/Refresh-এ আর ফিরে আসে না। Restore
            // করলে tombstone সরে বলে আবার দেখা যায়।
            try {
                if (DeletedGuard.isDeleted("followups", row.optString("id", ""), context)) continue
            } catch (_: Throwable) { }
            val stage = row.s("stage")
            val status = row.s("status").lowercase()
            when {
                stage == "Inquiry" && (status in setOf("cancelled", "incomplete", "rejected", "closed") ||
                    FollowUpRepository.inquiryHistoryEndsTerminal(row)) -> enqRejectRows.add(row)
                stage == "Patient" && status == "cancelled" -> visitRejectRows.add(row)
                stage == "Treatment" && status == "incomplete" -> notCompleteRows.add(row)
            }
        }
        val enqReject = dedupByMobile(enqRejectRows).map { entry(it, "enqreject", "Rejected") }.toMutableList()
        val visitReject = dedupByMobile(visitRejectRows).map { entry(it, "visitreject", "Visit Reject") }.toMutableList()
        val notComplete = dedupByMobile(notCompleteRows).map { entry(it, "notcomplete", "Incomplete") }.toMutableList()

        val complete = mutableListOf<DraftEntry>()
        // TK-REQUESTED (2026-07-27), ধাপ ৩খ — Draft পর্দা: this walked EVERY
        // patients row, so a person with a duplicate registration could be
        // listed twice here (and be judged on the wrong row's bill). It now uses
        // the same single row per person the rest of the app uses, chosen by the
        // ONE shared rule above. With a single row nothing changes.
        for ((mobKey, chosenRow) in patientByMobile) {
            val row = chosenRow
            val bill = row.optDouble("bill", 0.0)
            if (bill <= 0.0) continue
            val mob = mobKey
            val paid = paidByMobile[mob] ?: 0.0
            val due = (bill - paid).coerceAtLeast(0.0)
            // TK-REQUESTED ADDITION (2026-07-24): a patient with real Due
            // left CAN still land here if Master has approved a "Complete
            // despite Due" request (completeApprovedBy set) -- the Due
            // itself is NEVER changed/zeroed by this, it's shown honestly
            // in the label so Reports/collections stay accurate; this only
            // affects which Draft bucket the patient is grouped into.
            val approvedDespiteDue = row.s("completeApprovedBy").isNotBlank()
            if (due == 0.0) {
                complete.add(entry(row, "complete", "Paid ${paid.toLong()}"))
            } else if (approvedDespiteDue) {
                complete.add(entry(row, "complete", "Paid ${paid.toLong()} · Due ${due.toLong()} (Master-approved)"))
            }
        }

        // 🔴 TK-নির্দেশ (02.08.2026): Advance/Treatment টাকা সম্পূর্ণ Refund হয়ে
        // গেলে (Approved Refund আছে ও নেট জমা ঠিক ₹0) — এখানে দেখানো হয়, Enquiry/
        // Visit/Patient কোনো কার্ডেই আর না (FollowUpRepository.fetchTab()-এর
        // stage=="Treatment" ফিল্টারে একই শর্তে বাদ)। ⛔ `complete`-এর মতো
        // bill>0 শর্ত এখানে নেই — Bill না বসিয়েই Advance নিয়ে থাকলেও (এখনো
        // Refund হয়নি) এই শর্ত মেলে না (হয় Refund নেই, নয়তো নেট জমা ০ নয়),
        // তাই সেই রোগী ভুল করে এখানে ধরা পড়বে না।
        val refunded = mutableListOf<DraftEntry>()
        for ((mobKey, chosenRow) in patientByMobile) {
            if (!hasApprovedRefundByMobile.contains(mobKey)) continue
            val row = chosenRow
            // 🔴 B302.1 (02.08.2026): TK হাতে করে ফিরিয়ে আনলে (`refundRestoredBy`
            // সেট থাকে, Complete-despite-Due-এর হুবহু একই প্যাটার্ন) — তখন এই
            // Draft ঘরে আর দেখানো হয় না (রোগী ততক্ষণে Patient কার্ডে ফিরে গেছে)।
            if (row.s("refundRestoredBy").isNotBlank()) continue
            val paid = paidByMobile[mobKey] ?: 0.0
            if (paid > 0.5) continue // পুরোপুরি ০ না হলে (এখনো কিছু জমা আছে) — বাদ, রোগী Patient কার্ডেই থাকবে
            refunded.add(entry(row, "refunded", "Refunded"))
        }

        fun filt(list: List<DraftEntry>): List<DraftEntry> {
            if (from == null && to == null) return list
            return list.filter {
                val d = it.recordDate
                if (d.isBlank()) return@filter false
                (from == null || d >= from) && (to == null || d <= to)
            }
        }
        val result = DraftBuckets(filt(received), filt(enqReject), filt(visitReject), filt(notComplete), filt(complete), filt(unexpectedTime), filt(refunded))
        saveCachedBuckets(branchFilter, from, to, result)
        return result
    }

    /** 🔴 TK-নির্দেশ (02.08.2026, B302.1): "Refunded" ঘর থেকে রোগীর রেকর্ড
     *  Delete — `PatientTimelineActivity.showDeletePatientDialog()`-এর হুবহু
     *  একই অনুমতি-নিয়ম (Master সরাসরি; Staff শুধু আজ/গতকালের রেকর্ড, তাও
     *  Chamber বন্ধ না হলে; নইলে Master-এর অনুমতি লাগবে) ও একই মোছার পথ
     *  (Trash Bin-এ, Follow-up cascade সহ) — আলাদা কোনো নতুন লজিক নয়, শুধু
     *  Draft-এর এই তালিকা থেকেও একই কাজটা করা যায়। কল-সাইট (DraftListActivity)
     *  অনুমতি না থাকলে "PERMISSION" ফেরত পেয়ে Master-কে অনুরোধ পাঠানোর
     *  পপ-আপ দেখাবে (deleteEnquiry-র প্যাটার্নেই)। */
    fun deletePatientRecord(e: DraftEntry, user: NativeUser): String {
        if (!DeletePermission.canDeleteEntryNow(context, user, e.recordDate, e.branch, paid = true)) return "PERMISSION"
        if (e.id.isBlank() && e.mobile.isBlank()) return "NOT_FOUND"
        val rows = if (e.id.isNotBlank()) {
            val byId = SupabaseClient.fetchList("patients", "id=eq.${e.id}", 1)
            if (byId.length() > 0) byId else SupabaseClient.findByMobile("patients", e.mobile, "*", 1)
        } else {
            SupabaseClient.findByMobile("patients", e.mobile, "*", 1)
        }
        if (rows.length() == 0) return "NOT_FOUND"
        val ok = TrashHelper.moveToTrashWithFollowupCascade("patients", rows.getJSONObject(0), user.mobile, e.mobile)
        return if (ok) "OK" else "NETWORK"
    }

    /** TK-REQUESTED ADDITION (2026-07-18), Phase 1: same-day self-delete for
     *  the "My Enquiry" list. Permission is re-checked here too (not just in
     *  the adapter) so this can never be called successfully out of turn.
     *  Moves the record into the existing Trash Bin (Master can Restore it
     *  from there exactly like any other trashed record) instead of a hard
     *  delete — nothing is ever unrecoverable.
     *
     *  TK-REQUESTED (2026-07-19): returns a specific reason instead of a
     *  plain true/false, so the screen can tell the user the REAL cause
     *  (no permission / record already gone / network problem) instead of
     *  one generic "check connection or permission" message every time. */
    fun deleteEnquiry(e: DraftEntry, user: NativeUser, bypassPermission: Boolean = false): String {
        // 🆕 TK-নির্দেশ (03.08.2026) — Enquiry/Visit Reject তালিকায় (কোনো টাকা
        // জড়িত নয়) স্টাফ যেকোনো সময় নিজেই ডিলিট করতে পারবেন — কল-সাইট
        // (DraftListActivity) শুধু enqreject/visitreject তালিকাতেই এই ছাড়
        // পাঠায়, অন্য কোনো তালিকার (notcomplete/complete) আচরণ বদলায়নি।
        if (!bypassPermission && !TrashHelper.canDelete(user, e.recordDate, e.createdByMobile)) return "PERMISSION"
        if (e.id.isBlank()) return "NOT_FOUND"
        val rows = SupabaseClient.fetchList("enquiries", "id=eq.${e.id}", 1)
        if (rows.length() > 0) {
            val record = rows.getJSONObject(0)
            // 🔴 TK-REPORTED (01.08.2026, Enquiry Reject List — "ডিলিট করলে
            // পরে আবার ফিরে আসছে"): এই পথটা এতদিন plain moveToTrash()
            // ব্যবহার করত, যা শুধু enquiries সারিটা মুছত — এই নম্বরের
            // Cancelled/Incomplete followups সারি (যেখান থেকে Reject/
            // Visit-Reject/Incomplete তালিকা তৈরি হয়) কখনো ছোঁয়া হত না,
            // তাই নামটা ওই তালিকায় থেকেই যেত। এখন B108/V215-এর প্রমাণিত
            // cascade ফাংশন ব্যবহার হচ্ছে (Timeline-এর Delete যেটা আগে
            // থেকেই ব্যবহার করে) — একই নম্বরের সব followups+enquiries সারি
            // একসাথে Trash-এ যায় ও tombstone হয়, তাই আর কোনো তালিকাতেই
            // ফিরে আসবে না। Restore করলে সব হুবহু আগের অবস্থায় ফেরে।
            val ok = TrashHelper.moveToTrashWithFollowupCascade("enquiries", record, user.mobile, e.mobile)
            // Also clear this device's local cache/pending-queue copy (if
            // any) so a slightly-stale local view can't bring it back.
            if (ok) {
                context?.let { ctx ->
                    LocalWorkflowStore(ctx).removeEnquiry(e.id)
                    EnquiryRepository(ctx).removePendingById(e.id)
                }
            }
            return if (ok) "OK" else "NETWORK"
        }
        // TK-FOUND RISK FIX (2026-07-18): the enquiry hadn't reached the
        // cloud yet (still local-only, e.g. saved offline moments ago) --
        // the cloud fetch above finds nothing, so Delete used to just fail
        // here even though the record genuinely exists on this device.
        // Fall back to the local cache: move that copy to Trash instead,
        // then clear it from both the local cache and the pending-sync
        // queue so BottomNav's next retry can't re-upload something that
        // was just deleted.
        val ctx = context ?: return "NETWORK"
        val local = LocalWorkflowStore(ctx).pendingEnquiries()
        for (i in 0 until local.length()) {
            val row = local.getJSONObject(i)
            if (row.optString("id") == e.id) {
                val ok = TrashHelper.moveToTrashWithFollowupCascade("enquiries", row, user.mobile, e.mobile)
                if (ok) {
                    LocalWorkflowStore(ctx).removeEnquiry(e.id)
                    EnquiryRepository(ctx).removePendingById(e.id)
                }
                return if (ok) "OK" else "NETWORK"
            }
        }
        // 🔵 B609 (10.08.2026, TK-রিপোর্ট "Visit Reject delete করলে ভুতুড়ে ফিরে
        // আসে"): এই তালিকাগুলো (Visit Reject/Reject) followups টেবিলের Cancelled
        // সারি থেকে তৈরি — কার্ডের id হলো followups-এর id, যা enquiries টেবিলে
        // থাকে না (বিশেষত "Registered patient/Visit created", যাদের আলাদা enquiries
        // সারি নেই)। আগে তখন NOT_FOUND হয়ে শুধু এই ফোনে লুকাত (আসল cloud-delete/
        // tombstone নয়), তাই পরের auto-refresh সারিটা আবার টেনে এনে "ফিরিয়ে" দিত।
        // এখন enquiries না পেলে সরাসরি followups সারিটা ধরে **একই প্রমাণিত cascade**
        // (উপরের enquiries/patients-পথের হুবহু একই ফাংশন): ঐ মোবাইলের সব followups+
        // enquiries Trash-এ + DeletedGuard tombstone, আসল সারি snapshot-সহ Trash-এ
        // যায়; Master Restore করলে upsertRestoreSafe দিয়ে হুবহু আগের অবস্থায় ফেরে
        // (table="followups" restore-এ সমর্থিত — যাচাই করা)। ⛔ নতুন কোনো মোছার
        // লজিক নয়; over-delete-এর ঝুঁকিও নতুন নয় (mobile-cascade আগের enquiries-
        // পথেই ছিল, এক-নম্বর-এক-সেকশন নিয়মে সুরক্ষিত)।
        val fRows = try { SupabaseClient.fetchList("followups", "id=eq.${e.id}", 1) }
            catch (_: Throwable) { org.json.JSONArray() }
        if (fRows.length() > 0) {
            val frecord = fRows.getJSONObject(0)
            val ok = TrashHelper.moveToTrashWithFollowupCascade("followups", frecord, user.mobile, e.mobile)
            if (ok) {
                context?.let { c ->
                    LocalWorkflowStore(c).removeEnquiry(e.id)
                    EnquiryRepository(c).removePendingById(e.id)
                }
            }
            return if (ok) "OK" else "NETWORK"
        }
        return "NOT_FOUND"
    }

    /** Restore an entry back to the exact section it came from. For the three
     *  follow-up lists we simply clear the Cancelled/Incomplete status back to
     *  Active, so the record reappears in its Follow-up tab (web
     *  restoreDraftEntry). For a Complete patient we re-activate the matching
     *  Treatment follow-up. Returns true on success. */
    fun restore(e: DraftEntry, user: NativeUser? = null): Boolean {
        return try {
            when (e.tab) {
                "enqreject" -> {
                    // 🔴🔒 V448 (19.08.2026): the Inquiry live-list now also honours
                    // the append-only Reject/Restore history. Therefore a *real* Restore
                    // must leave an equally durable Active marker and must reactivate
                    // every same-mobile Inquiry sibling + the matching enquiry row.
                    // Otherwise one old Cancelled sibling/enquiry could immediately hide
                    // the restored card again. No row is created or deleted here.
                    val d = e.mobile.filter { it.isDigit() }.takeLast(10)
                    if (d.length != 10) return false
                    val rows = SupabaseClient.findByMobileOrNull(
                        "followups", d, "id,mobile,stage,status,history", 50
                    ) ?: return false
                    val now = isoNow()
                    val who = user?.let { it.mobile.ifBlank { it.name } }.orEmpty()
                    var touched = false
                    var allOk = true
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        if (!row.s("stage").equals("Inquiry", true)) continue
                        val id = row.s("id")
                        if (id.isBlank()) continue
                        val history = row.optJSONArray("history") ?: JSONArray()
                        history.put(JSONObject()
                            .put("date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                            .put("time", now)
                            .put("remark", "Restored from Reject List")
                            .put("staff", who)
                            .put("status", "Active")
                            .put("decisionVersion", "V448"))
                        val fields = JSONObject().put("status", "Active").put("history", history).put("updatedAt", now)
                        val ok = SupabaseClient.updateById("followups", id, fields)
                        if (!ok) context?.let { GenericUpdateQueue.queue(it, "followups", id, fields) }
                        touched = true
                        allOk = allOk && ok
                    }
                    // Old Reject logic also marked enquiries.status. Clear that durable
                    // marker only as part of this explicit Restore action.
                    val enqs = SupabaseClient.findByMobileOrNull("enquiries", d, "id,status,stage", 50) ?: return false
                    for (i in 0 until enqs.length()) {
                        val erow = enqs.getJSONObject(i)
                        val id = erow.s("id")
                        if (id.isBlank()) continue
                        val fields = JSONObject().put("status", "Active").put("updatedAt", now)
                        val oldStage = erow.s("stage").trim().lowercase()
                        if (oldStage in setOf("cancelled", "rejected", "closed")) fields.put("stage", "Inquiry")
                        val ok = SupabaseClient.updateById("enquiries", id, fields)
                        if (!ok) context?.let { GenericUpdateQueue.queue(it, "enquiries", id, fields) }
                        allOk = allOk && ok
                    }
                    touched && allOk
                }
                "visitreject", "notcomplete" -> {
                    if (e.id.isBlank()) return false
                    // V452: a real Restore must carry a durable Active decision in
                    // history.  The cloud terminal guard uses this marker to tell an
                    // intentional Restore apart from an old/stale device trying to
                    // overwrite Cancelled/Incomplete back to Active.
                    val now = isoNow()
                    val current = try { SupabaseClient.fetchList("followups", "id=eq.${e.id}", 1) }
                        catch (_: Throwable) { JSONArray() }
                    if (current.length() == 0) return false
                    val row = current.getJSONObject(0)
                    val history = row.optJSONArray("history") ?: JSONArray()
                    val who = user?.let { it.mobile.ifBlank { it.name } }.orEmpty()
                    history.put(JSONObject()
                        .put("date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                        .put("time", now)
                        .put("remark", if (e.tab == "visitreject") "Restored from Visit Reject List" else "Restored from Incomplete List")
                        .put("staff", who)
                        .put("status", "Active")
                        .put("decisionVersion", "V452"))
                    val fields = JSONObject().put("status", "Active").put("history", history).put("updatedAt", now)
                    val ok = SupabaseClient.updateById("followups", e.id, fields)
                    if (!ok) context?.let { GenericUpdateQueue.queue(it, "followups", e.id, fields) }
                    ok
                }
                "complete" -> {
                    val mob = e.mobile.filter { it.isDigit() }.takeLast(10)
                    // TK-REPORTED CLASS OF BUG (fixed 2026-07-27): this asked
                    // for the patient's follow-up rows but findByMobile()
                    // returns only ONE unless a count is given -- and that one
                    // row is often the wrong stage. Restoring a Completed
                    // patient then said "Restored" while the card never came
                    // back. Now every row of this person is re-activated, the
                    // same as the other three Draft sections already do.
                    val rows = SupabaseClient.findByMobile("followups", mob, "id,stage", 50)
                    var ok = false
                    for (i in 0 until rows.length()) {
                        val id = rows.getJSONObject(i).s("id")
                        if (id.isBlank()) continue
                        val fields = JSONObject().put("status", "Active").put("stage", "Treatment").put("updatedAt", isoNow())
                        val rowOk = SupabaseClient.updateById("followups", id, fields)
                        if (!rowOk) context?.let { GenericUpdateQueue.queue(it, "followups", id, fields) }
                        ok = rowOk || ok
                    }
                    ok
                }
                // 🔴 TK-নির্দেশ (02.08.2026, B302.1): "Refunded" ঘর থেকে হাতে
                // ফিরিয়ে আনা — completeApprovedBy-এর হুবহু একই প্যাটার্ন
                // (PatientTimelineActivity.approveCompleteDespiteDue দ্রষ্টব্য)।
                // ⛔ কোনো টাকার হিসাব/Refund রেকর্ড ছোঁয়া হয় না — শুধু এই একটা
                // ফ্ল্যাগ, যেটা FollowUpRepository-র exclusion উল্টে দেয়।
                "refunded" -> {
                    if (e.id.isBlank()) return false
                    val who = user?.mobile?.ifBlank { "unknown" } ?: "unknown"
                    val fields = JSONObject().put("refundRestoredBy", who).put("updatedAt", isoNow())
                    val ok = SupabaseClient.updateById("patients", e.id, fields)
                    if (!ok) context?.let { GenericUpdateQueue.queue(it, "patients", e.id, fields) }
                    ok
                }
                else -> false
            }
        } catch (e: Exception) { false }
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
}
