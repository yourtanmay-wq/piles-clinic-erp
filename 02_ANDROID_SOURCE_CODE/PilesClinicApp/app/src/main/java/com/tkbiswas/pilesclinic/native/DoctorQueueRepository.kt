package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONArray

/**
 * Native rebuild -- Doctor Queue (data access).
 *
 * Reads the same "patients" Supabase table the WebView reads, applies the
 * exact branch-scoping rule the other native screens use (Master / branch=="All"
 * see every branch; a branch staff user sees only their own branch), and then
 * filters/sorts client-side exactly like visitQueueRows() in app.js.
 *
 * Client-side filtering (rather than a complex PostgREST OR filter) is used on
 * purpose so the queue condition stays byte-for-byte identical to the WebView's,
 * which is what keeps the two front-ends showing the same patients.
 */
class DoctorQueueRepository(private val context: Context? = null) {

    companion object {
        private const val PREFS = "doctor_queue_cache"
        // 🔴🔒 V454 (20.08.2026, TK-অনুমোদিত পাইলট): শুধু auto-refresh পথের
        // জন্য delta-fetch-এর state — নিচের fetchQueueDelta() দেখুন।
        private const val DELTA_PREFS = "doctor_queue_delta_state"
        /** নিরাপত্তা-জাল ১: এত সময় পার হলে (বা কোনো since না থাকলে) জোর করে
         *  পূর্ণ-fetch — কিছু ভুলে বাদ পড়লেও নিজে থেকেই ঠিক হয়ে যাবে। */
        private const val FULL_REFRESH_INTERVAL_MS = 2L * 60L * 60L * 1000L
        private const val SAFETY_BACK_MS = 5_000L
    }

    // TK-REQUESTED ADDITION (2026-07-20): "at least show the data that was
    // already on the phone before" -- on a slow connection, the screen used
    // to show nothing but a spinner until the network call finished. Now
    // the last successfully fetched queue for this branch is saved on-device
    // and can be shown INSTANTLY while a fresh fetch happens in the
    // background, same pattern as the app's other silent-refresh screens.
    // This is a read-only display cache -- it never affects what gets
    // fetched/filtered/saved; fetchQueue() below is completely unchanged.
    fun loadCachedQueue(branchFilter: String?): List<QueuePatient>? {
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "cache_" + (branchFilter ?: "All")
        val json = prefs.getString(key, null)
            // ⚡ জমানো তালিকা না থাকলেও ফোনের নিজের রেকর্ড সঙ্গে সঙ্গে দেখাতে হবে।
            ?: return mergeOwnPhonePatients(branchFilter, emptyList()).ifEmpty { null }
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<QueuePatient>()
            for (i in 0 until arr.length()) list.add(DoctorQueueModel.parse(arr.getJSONObject(i)))
            // ⚡ TK (28.07.2026): নিজের ফোনে করা রেজিস্ট্রেশন সঙ্গে সঙ্গে দেখাতে হবে।
            mergeOwnPhonePatients(branchFilter, list)
        } catch (t: Throwable) { null }
    }

    /**
     * ⚡ TK-এর নির্দেশ (28.07.2026 ২.৩০ pm): **"আমি আমার ফোনে যা যা কাজ করবো,
     * সেটা যেন সাথে সাথেই দেখায়।"**
     *
     * ক্লাউড থেকে আনার পথে ফোনের নিজের রোগী আগেই মেশানো হত, কিন্তু **জমানো
     * তালিকা** দেখানোর পথে হত না — তাই ধীর লাইনে সদ্য রেজিস্টার করা রোগী
     * CHECK-UP Queue-তে দেরিতে আসত। এখন দুই পথেই মেশে।
     *
     * ⛔ কোনো নতুন ক্লাউড-কল নেই · কোনো সারি বাদ যায় না — শুধু যোগ হয়।
     */
    private fun mergeOwnPhonePatients(branchFilter: String?, cached: List<QueuePatient>): List<QueuePatient> {
        val ctx = context ?: return cached
        return try {
            val pending = LocalWorkflowStore(ctx).pendingPatients()
            if (pending.length() == 0) return cached
            val seen = HashSet<String>()
            for (c in cached) if (c.id.isNotBlank()) seen.add(c.id)
            val extra = mutableListOf<QueuePatient>()
            for (i in 0 until pending.length()) {
                val p = pending.optJSONObject(i) ?: continue
                val pid = p.optString("id")
                if (pid.isBlank() || !seen.add(pid)) continue
                val branchOk = branchFilter == null || branchFilter == "All" ||
                    p.s("branch").equals(branchFilter, ignoreCase = true)
                if (!branchOk) continue
                extra.add(DoctorQueueModel.parse(p))
            }
            if (extra.isEmpty()) cached else extra + cached
        } catch (_: Throwable) { cached }
    }

    private fun saveCachedQueue(branchFilter: String?, queue: List<QueuePatient>, hadPhotos: Boolean = true) {
        val ctx = context ?: return
        try {
            val key = "cache_" + (branchFilter ?: "All")
            // 🟢 B625 (Egress ফিক্স, 11.08.2026): background pre-warm এখন **ছবি ছাড়া** টানে
            // (includePhoto=false) — তাই সেই ডাকে q.photo ফাঁকা আসে। কিন্তু আগের জমানো
            // তালিকায় ছবি থাকলে সেটা যেন **হারিয়ে না যায়** (অফলাইনে ছবি দেখা বন্ধ হত)।
            // তাই ছবি-ছাড়া ডাকে পুরনো ছবি রেখে দিই; আসল স্ক্রিন-ডাক (hadPhotos=true) হলে
            // যা এসেছে তাই লিখি — কেউ ছবি সরালে সেটাও সঠিকভাবে ফাঁকা হয়।
            val prevPhoto = HashMap<String, String>()
            if (!hadPhotos) {
                try {
                    val old = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
                    if (old != null) {
                        val oldArr = JSONArray(old)
                        for (i in 0 until oldArr.length()) {
                            val o = oldArr.getJSONObject(i)
                            val id = o.optString("id")
                            val ph = o.optString("photo", "")
                            if (id.isNotBlank() && ph.isNotBlank()) prevPhoto[id] = ph
                        }
                    }
                } catch (_: Throwable) {}
            }
            val arr = JSONArray()
            for (q in queue) {
                val photo = if (hadPhotos) q.photo
                            else if (q.photo.isNotBlank()) q.photo
                            else (prevPhoto[q.id] ?: "")
                arr.put(
                    org.json.JSONObject()
                        .put("id", q.id).put("patientId", q.patientId).put("name", q.name)
                        .put("mobile", q.mobile).put("disease", q.disease).put("branch", q.branch)
                        .put("photo", photo).put("updatedAt", q.updatedAt).put("createdAt", q.createdAt)
                        .put("bill", q.bill)
                        /* 🩺🔒 V839 — নেট না থাকলেও কার্ডে ট্যাগ ও OLD/NEW যেন
                           থাকে, তাই জমানো তালিকাতেও ঘরগুলো লেখা হয়।
                           ⛔ এটা শুধু ফোনের ভিতরের জমা — ক্লাউডে কিছু যায় না,
                              Egress-এ কোনো প্রভাব নেই। */
                        .put("registrationDate", q.registrationDate)
                        .put("nvpLine", q.nvpLine).put("nvpWhen", q.nvpWhen)
                        .put("nvpBy", q.nvpBy).put("nvpMedicine", q.nvpMedicine)
                        .put("nvpNote", q.nvpNote)
                        .put("nvpItems", q.nvpItems.joinToString(","))
                        // queue/doctorComplete/stage aren't needed back -- isInQueue() isn't
                        // re-applied to cached data, it's only used for the raw fetch above.
                )
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, arr.toString()).apply()
        } catch (_: Throwable) { }
    }

    fun fetchQueue(branchFilter: String?, includePhoto: Boolean = false): List<QueuePatient> {
        // 🔵🔒 (09.08.2026, TK-নির্দেশ "ডাক্তার রোগী খুঁজে পায় না" ফিক্স): আগে শুধু
        // branch=eq.<branch> ছিল — তাই যে রোগীর ব্রাঞ্চ ঘর এখনো খালি/বসেনি (সদ্য-তৈরি
        // বা পুরনো) সে ডাক্তারের লাইনে আসত না। এখন FollowUpRepository.branchScopeFilter-এর
        // হুবহু প্রমাণিত প্যাটার্ন: or=(branch.eq,branch.is.null) — খালি-ব্রাঞ্চ রোগীও দেখা যায়।
        // ⛔ staff নিজের ব্রাঞ্চ-locked (অন্য ব্রাঞ্চ বাছতেই পারে না), তাই অন্য ব্রাঞ্চ টানার
        // ঝুঁকি নেই; নিচের isInQueue/এক-মোবাইল-এক-কার্ড/সাজানো কিছু বদলায়নি — শুধু ফিল্টার চওড়া।
        val filter = if (branchFilter != null && branchFilter != "All") {
            "or=(branch.eq." + java.net.URLEncoder.encode(branchFilter, "UTF-8") + ",branch.is.null)"
        } else null

        // TK-REQUESTED SAFETY FIX (2026-07-16): this used the SupabaseClient
        // default limit (500), sorted by most-recently-updated. As the total
        // "patients" table grows past 500 rows (very plausible after months
        // of running 5 branches), an older patient still waiting for a
        // doctor checkup but not recently touched could silently fall
        // outside that window and vanish from the queue -- looking exactly
        // like "another staff/doctor can't see an entry". Explicit 5000
        // limit here, matching the same safe limit already used everywhere
        // else in this codebase (Follow-up, Export, Global Search, Backup).
        // TK-REQUESTED FIX (2026-07-23): same root cause/fix as Follow-up's
        // Bill/Due bug -- fetchList() swallows a failed request into a
        // silent empty array, which used to get baked straight into the
        // queue (and then CACHED at the end of fetchQueue()), making the
        // whole queue look wrongly empty on a bad connection, possibly
        // stuck that way. Now uses fetchListOrNull(): a genuine failure
        // (null) falls back to the last cached queue instead of computing/
        // caching a wrong (empty) one; if there's no cache yet, falls
        // through to empty exactly like before (no behavior change for a
        // first-ever load). A real empty result (successful fetch,
        // genuinely nobody waiting) is NOT affected -- only a true
        // failure is.
        // 🔒 SPEED FIX (28.07.2026, TK-approved · khata row B26): same table,
        // same filter, same rows, same limit, same order -- only the columns
        // this queue actually reads are downloaded now. It used to bring all
        // 39 patient columns: the full doctor's note, the medical history, the
        // complaint text, the previous-treatment notes -- none of which this
        // screen ever looks at.
        // ⛔ THE PHOTO STAYS. The queue card shows the patient's photo, so
        // "photo" is deliberately still asked for; dropping it would leave the
        // cards blank.
        // ⛔ NOTHING ELSE CHANGES: the list below is every field
        // DoctorQueueModel.parse and this file read, checked one by one
        // (branch, createdAt, createdBy, disease, doctorComplete, id, mobile,
        // name, patientId, photo, queue, registeredBy, stage, updatedAt), the
        // raw rows are never passed anywhere else, and a failed narrowed read
        // asks for every column again by itself.
        // 🔴 TK-নির্দেশ (04.08.2026): "bill" যোগ করা হলো -- Report Card
        // বোতাম দেখানো/লুকানোর জন্য (bill > 0 মানে Advance/বিল বসেছে,
        // Take Action/Patient Timeline-এর একই প্রমাণিত নিয়ম)। বাকি সব
        // কলাম অপরিবর্তিত।
        // 🟢🔒 B625 (Egress ফিক্স, 11.08.2026, TK-নির্দেশ): background pre-warm এখন
        //   includePhoto=false দিয়ে ডাকে — ছবি (base64) ছাড়া টানে। কারণ background প্রতি
        //   কয়েক মিনিটে প্রতিটা ফোনে এই queue টানত, ফলে **সব রোগীর ছবি বারবার নামত** —
        //   এটাই ছিল Supabase Free-plan egress (14.8GB) শেষ হওয়ার মূল কারণ (DB মাত্র ৮০MB)।
        //   ছবি এখন শুধু তখনই নামে যখন কেউ **সত্যিই Doctor Queue স্ক্রিন খোলে** (includePhoto=
        //   true দিলে) — তবে Doctor Queue screen এখন default slim path ব্যবহার করে; কার্ডের ছবি cache/missing-id batch থেকে পূরণ হয়।
        val cols = if (includePhoto)
            "id,name,mobile,branch,disease,patientId,photo,queue,stage,doctorComplete,createdBy,registeredBy,createdAt,updatedAt,bill,registrationDate"
        else
            "id,name,mobile,branch,disease,patientId,queue,stage,doctorComplete,createdBy,registeredBy,createdAt,updatedAt,bill,registrationDate"
        val rowsRaw = SupabaseClient.fetchListSlimOrNull("patients", filter, 5000, cols)
        if (rowsRaw == null) loadCachedQueue(branchFilter)?.let { return it }
        val rows = rowsRaw ?: JSONArray()

        // TK-REQUESTED BUG FIX (2026-07-16): same fix as Follow-up -- a
        // just-registered patient could be briefly missing from the queue
        // (delaying the doctor seeing them) because this always read straight
        // from the cloud, with no awareness of a registration still syncing
        // in the background. Any locally-pending patient not yet in the
        // cloud result is now merged in too. Filtering/sorting logic below
        // is completely unchanged.
        val merged = JSONArray()
        for (i in 0 until rows.length()) merged.put(rows.getJSONObject(i))
        context?.let { ctx ->
            val pending = LocalWorkflowStore(ctx).pendingPatients()
            val idPosition = HashMap<String, Int>()
            for (i in 0 until merged.length()) {
                val existingId = merged.getJSONObject(i).optString("id")
                if (existingId.isNotBlank()) idPosition[existingId] = i
            }
            for (i in 0 until pending.length()) {
                val p = pending.getJSONObject(i)
                val pid = p.optString("id")
                val branchOk = branchFilter == null || branchFilter == "All" ||
                    p.s("branch").equals(branchFilter, ignoreCase = true)
                if (!branchOk || pid.isBlank()) continue
                // TK-REPORTED BUG FIX (2026-07-20): same root cause as
                // Follow-up's remark bug (00_PROJECT_STATE_MASTER_NOTE.md
                // section 91) -- a locally-pending edit for a patient whose
                // id is already in the cloud result used to be silently
                // dropped instead of overriding the stale cloud row.
                val existingPos = idPosition[pid]
                if (existingPos != null) {
                    merged.put(existingPos, p)
                } else {
                    idPosition[pid] = merged.length()
                    merged.put(p)
                }
            }
        }

        val queue = mutableListOf<QueuePatient>()
        for (i in 0 until merged.length()) {
            val row = merged.getJSONObject(i)
            if (DoctorQueueModel.isInQueue(row)) {
                queue.add(DoctorQueueModel.parse(row))
            }
        }
        // 🚨 TK-REPORTED (28.07.2026, খাতার সারি B30): ভুল করে আগে একই রোগীর
        // একাধিক সারি তৈরি হয়ে গিয়েছিল। নতুন সারি তৈরি হওয়া বন্ধ হয়েছে, কিন্তু
        // পুরনোগুলো ডেটাবেসে রয়ে গেছে — তাই এক মানুষ দু'বার লাইনে উঠতে পারত।
        // ⛔ পুরনো সারি মুছে ফেলা হয়নি — শুধু **এক মোবাইল = এক কার্ড** করে
        // দেখানো হয়, প্রথমটাই রাখা হয় (তালিকা আগে থেকেই নতুন-আগে সাজানো)।
        // মোবাইল না থাকলে আগের মতোই সবই দেখায়, কিছু হারায় না।
        val oncePerMobile = mutableListOf<QueuePatient>()
        val seenMobiles = HashSet<String>()
        for (q in queue) {
            val key = q.mobile.filter { it.isDigit() }.takeLast(10)
            if (key.length == 10 && !seenMobiles.add(key)) continue
            oncePerMobile.add(q)
        }
        val result0 = DoctorQueueModel.sortNewestFirst(oncePerMobile)
        // 🟢🔒 B659 (15.08.2026, TK-অনুমোদিত · Egress-২): ছবি-ছাড়া টানার পরে **পর্দায় যে
        //   তালিকা যায় সেটাতেও** জমানো ছবি বসানো হয়। B632/B625-এ ছবি শুধু জমানো ফাইলে
        //   রাখা হত, ফেরত-দেওয়া তালিকায় নয় — তাই ৩০ সেকেন্ডের অটো-রিফ্রেশ হলেই কার্ড
        //   থেকে ছবি উধাও হয়ে যেত (DoctorQueueAdapter ফাঁকা ছবি পেলে setImageDrawable(null))।
        //   ⛔ ছবিসহ টানার পথ (includePhoto=true) এক অক্ষরও বদলায়নি।
        val result1 = if (includePhoto) result0 else fillPhotosFromCache(branchFilter, result0)
        val result = fillNextVisitPlans(result1)   // 🩺 V839
        saveCachedQueue(branchFilter, result, includePhoto)
        return result
    }

    /**
     * 🩺🔒 V839 (২৯.০৮.২০২৬, TK-নির্দেশ) — কার্ডে দেখানোর জন্য প্রতিটা
     * সারিতে **শেষ NEXT VISIT PLAN**-টা বসানো।
     *
     * ### 🚨 Egress — কেন এভাবে, আন্দাজে নয়
     * উপরের বড় পড়াটা **ব্রাঞ্চের সব রোগীর** সারি টানে (৫০০০ পর্যন্ত)।
     * সেখানে `nextVisitPlan` ঘরটা যোগ করলে **প্রত্যেক রোগীর পুরো
     * প্ল্যান-তালিকা** নামত — দিনে বহুবার, প্রতিটা ফোনে। সেটা করা হয়নি।
     *
     * বদলে: **ছাঁকনির পরে** যে ক'জন সত্যিই আজ তালিকায় আছেন (সাধারণত ৫–৩০ জন)
     * **শুধু তাঁদের** জন্য একটাই ছোট অনুরোধ — `id=in.(…)&select=id,nextVisitPlan`।
     * প্রকল্পের আগে থেকে থাকা প্রমাণিত ধাঁচ (`DraftRepository`/`ReportsActivity`-র
     * মতো একবারে-সবগুলো পড়া)।
     *
     * ⛔ ব্যর্থ হলে (নেট নেই / ঘরটা এখনো নেই) **তালিকা হুবহু আগের মতোই** ফেরে —
     *    ট্যাগ দেখায় না, কিছুই ভাঙে না।
     * ⛔ একসাথে সর্বোচ্চ ৬০টা id (URL খুব লম্বা হওয়া এড়াতে — `MAX_BATCH`-এর
     *    হুবহু একই সীমা)।
     */
    private fun fillNextVisitPlans(list: List<QueuePatient>): List<QueuePatient> {
        if (list.isEmpty()) return list
        return try {
            val ids = list.map { it.id }.filter { it.isNotBlank() }.distinct().take(60)
            if (ids.isEmpty()) return list
            val filter = "id=in.(" + ids.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") } + ")"
            val rows = SupabaseClient.fetchListSlimOrNull(
                "patients", filter, ids.size,
                "id," + com.tkbiswas.pilesclinic.clinical.NextVisitPlan.FIELD
            ) ?: return list
            val byId = HashMap<String, com.tkbiswas.pilesclinic.clinical.NextVisitPlan.Entry>()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val rid = r.optString("id")
                if (rid.isBlank()) continue
                com.tkbiswas.pilesclinic.clinical.NextVisitPlan.latest(r)?.let { byId[rid] = it }
            }
            if (byId.isEmpty()) return list
            list.map { q ->
                val e = byId[q.id] ?: return@map q
                q.copy(
                    nvpLine = e.shortLine(),
                    nvpWhen = if (e.at.length >= 10) FollowUpModel.displayDate(e.at.take(10)) else "",
                    nvpBy = e.byName,
                    nvpItems = e.items,        // 🩺 V839 — পপ-আপের জন্য, একই পড়া
                    nvpMedicine = e.medicine,
                    nvpNote = e.note
                )
            }
        } catch (_: Throwable) { list }
    }

    /**
     * 🟢🔒 B659 (15.08.2026, TK-অনুমোদিত · Egress-২)
     *
     * ছবি-ছাড়া টানা তালিকার প্রতিটা রোগীর ছবি **ফোনে জমানো তালিকা থেকে** বসিয়ে দেয় —
     * কোনো নতুন ক্লাউড-কল ছাড়াই। ফলে কার্ডে ছবি অটুট থাকে।
     *
     * শুধু যাদের ছবি **কোথাওই নেই** (যেমন এইমাত্র রেজিস্টার হওয়া নতুন রোগী), তাদের
     * ক্ষেত্রে **শুধু ওই id-গুলোর `id,photo`** একবার আনা হয় — গোটা ব্রাঞ্চের সব ছবি নয়।
     * ⛔ ৫০ জন করে ভাগে আনা হয় (B660) — লম্বা URL-এর ঝুঁকি নেই; ৫০০ জনের বেশি হলে থামে।
     * ⛔ এখানে যাদের ছবি লাগে তাঁরা **শুধু লাইনে দাঁড়ানো রোগী** — ব্রাঞ্চের সবাই নয়
     *    (fetchQueue-এ isInQueue ছাঁকার **পরে** এই কাজটা হয়)।
     * ⛔ ব্যর্থ হলে কিছুই ভাঙে না — আগের মতোই ওই কার্ডে ছবি ফাঁকা থাকে, পরের ডাকে চলে আসে।
     */
    private fun fillPhotosFromCache(branchFilter: String?, list: List<QueuePatient>): List<QueuePatient> {
        val ctx = context ?: return list
        if (list.isEmpty()) return list
        return try {
            val map = HashMap<String, String>()
            val cachedStamp = HashMap<String, String>()
            val key = "cache_" + (branchFilter ?: "All")
            val old = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            if (old != null) {
                val arr = JSONArray(old)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    val ph = o.optString("photo", "")
                    if (id.isNotBlank()) {
                        cachedStamp[id] = o.optString("updatedAt", "")
                        if (ph.isNotBlank()) map[id] = ph
                    }
                }
            }
            // 🔵🔒 EGRESS-SAFE (19.08.2026): পুরো branch-এর photo আর refresh-এ নামবে না।
            // Queue-তে থাকা যে রোগীর photo cache-এ নেই অথবা slim row-এর updatedAt বদলেছে,
            // শুধু তার id,photo আবার আনা হয়। updatedAt বদল মানেই photo বদল নয়, কিন্তু এই
            // ছোট bounded check-টাই অন্য ফোনে বদলানো/মুছে-ফেলা photo stale না রাখার নিরাপদ উপায়।
            // ব্যর্থ হলে পুরনো cache-ই থাকে; সফল response-এ blank photo এলে পুরনো photo সরানো হয়।
            val needPhoto = list.filter {
                it.photo.isBlank() && it.id.isNotBlank() &&
                it.id.matches(Regex("^[A-Za-z0-9_-]+$")) &&
                (map[it.id].isNullOrBlank() || cachedStamp[it.id].orEmpty() != it.updatedAt)
            }.map { it.id }.distinct()
            // ৫০ জন করে ভাগ; সর্বোচ্চ ১০ ভাগ (৫০০ queue patient) — URL/traffic bounded।
            val confirmedBlank = HashSet<String>()
            if (needPhoto.isNotEmpty()) {
                for ((ci, chunk) in needPhoto.chunked(50).withIndex()) {
                    if (ci >= 10) break
                    val extra = SupabaseClient.fetchListSlimOrNull(
                        "patients", "id=in.(" + chunk.joinToString(",") + ")", chunk.size, "id,photo"
                    )
                    if (extra != null) {
                        for (i in 0 until extra.length()) {
                            val o = extra.getJSONObject(i)
                            val id = o.optString("id")
                            val ph = o.optString("photo", "")
                            if (id.isNotBlank()) {
                                if (ph.isNotBlank()) {
                                    map[id] = ph
                                    confirmedBlank.remove(id)
                                } else {
                                    map.remove(id)
                                    confirmedBlank.add(id)
                                }
                            }
                        }
                    }
                }
            }
            // saveCachedQueue(hadPhotos=false) normally preserves an old cached photo.
            // If cloud explicitly confirmed that a changed queue row now has NO photo,
            // clear only that cached photo first so the old image cannot reappear.
            if (confirmedBlank.isNotEmpty() && old != null) {
                try {
                    val arr = JSONArray(old)
                    var touched = false
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (confirmedBlank.contains(o.optString("id"))) {
                            o.put("photo", "")
                            touched = true
                        }
                    }
                    if (touched) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(key, arr.toString()).apply()
                } catch (_: Throwable) { }
            }
            list.map { if (it.photo.isBlank()) it.copy(photo = map[it.id] ?: "") else it }
        } catch (_: Throwable) { list }
    }

    // =========================================================================
    // 🔴🔒 V454 (20.08.2026, TK-নির্দেশ · পাইলট, শুধু Doctor Queue) — "শুধু
    // বদলানো অংশটুকু নামুক।"
    //
    // ⛔ উপরের `fetchQueue()` **এক অক্ষরও বদলানো হয়নি** — স্ক্রিন প্রথম খোলা/
    //    Resume/ব্রাঞ্চ-বদলের সময় এখনও সেটাই চলে (সবচেয়ে নিরাপদ, পূর্ণ তালিকা)।
    //    এই নতুন ফাংশন **শুধু ৩০-সেকেন্ডের auto-refresh পথে** (DoctorQueueActivity
    //    autoCheckForChanges()) ব্যবহারের জন্য — TK-এর অনুমতি নিয়ে, একটা টেস্ট
    //    ফোনে প্রথমে যাচাইয়ের জন্য।
    //
    // কীভাবে নিরাপদ রাখা হয়েছে (৩ স্তরের সুরক্ষা):
    //  ১. since (শেষ কতদূর দেখা হয়েছে) না থাকলে, বা শেষ পূর্ণ-fetch ২ ঘণ্টার
    //     বেশি আগে হলে — জোর করে পুরনো পূর্ণ fetchQueue()-ই চলে (self-heal)।
    //  ২. delta-র ক্লাউড-কল ব্যর্থ হলে (null) সাথে সাথে পূর্ণ fetchQueue()।
    //  ৩. delta-তে পাওয়া প্রতিটা রোগী isInQueue() দিয়ে যাচাই হয় — এখনও লাইনে
    //     থাকলে জমানো তালিকায় বসে/আপডেট হয়, লাইন থেকে বেরিয়ে গেলে (ডাক্তার
    //     দেখে ফেলেছেন) জমানো তালিকা থেকে **সরিয়ে দেওয়া হয়** — তাই "চেক-আপ
    //     হয়ে যাওয়া রোগী তালিকায় থেকে গেছে" এই ভুল হবে না।
    // ⛔ কোনো রেকর্ড ডেটাবেস থেকে সত্যিকারের DELETE হলে (updatedAt বদলায় না)
    //    সেটা delta-তে ধরা পড়বে না — কিন্তু ২ ঘণ্টার নিয়মিত পূর্ণ-fetch সেটাও
    //    নিজে থেকে ঠিক করে দেবে। এই সীমাবদ্ধতা TK-কে সততার সাথে জানানো হয়েছে।
    // =========================================================================
    fun fetchQueueDelta(branchFilter: String?, includePhoto: Boolean = false): List<QueuePatient> {
        val ctx = context ?: return fetchQueue(branchFilter, includePhoto)
        val key = branchFilter?.trim()?.takeIf { it.isNotEmpty() } ?: "All"
        val sp = ctx.getSharedPreferences(DELTA_PREFS, Context.MODE_PRIVATE)
        val since = sp.getString("since_$key", null)
        val lastFullAt = sp.getLong("fullAt_$key", 0L)
        val now = System.currentTimeMillis()

        if (since.isNullOrBlank() || (now - lastFullAt) > FULL_REFRESH_INTERVAL_MS) {
            val result = fetchQueue(branchFilter, includePhoto)
            markFullDone(sp, key, now)
            return result
        }

        val branchPart = if (branchFilter != null && !branchFilter.equals("All", ignoreCase = true))
            "&or=(branch.eq." + java.net.URLEncoder.encode(branchFilter, "UTF-8") + ",branch.is.null)"
        else ""
        val sinceEnc = try { java.net.URLEncoder.encode(since, "UTF-8") } catch (_: Throwable) { since }
        val filter = "updatedAt=gt.$sinceEnc$branchPart"
        val cols = if (includePhoto)
            "id,name,mobile,branch,disease,patientId,photo,queue,stage,doctorComplete,createdBy,registeredBy,createdAt,updatedAt,bill,registrationDate"
        else
            "id,name,mobile,branch,disease,patientId,queue,stage,doctorComplete,createdBy,registeredBy,createdAt,updatedAt,bill,registrationDate"

        val delta = try {
            SupabaseClient.fetchListSlimOrNull("patients", filter, 2000, cols)
        } catch (_: Throwable) { null }

        if (delta == null) {
            // নিরাপত্তা-জাল ২: ক্লাউড-কল ব্যর্থ হলে সাথে সাথে পূর্ণ fetch।
            val result = fetchQueue(branchFilter, includePhoto)
            markFullDone(sp, key, now)
            return result
        }

        // জমানো তালিকার উপর delta বসানো (id ধরে) — id-এর ক্রম রাখা হয় (LinkedHashMap)।
        val byId = LinkedHashMap<String, QueuePatient>()
        for (c in (loadCachedQueue(branchFilter) ?: emptyList())) if (c.id.isNotBlank()) byId[c.id] = c
        for (i in 0 until delta.length()) {
            val row = delta.getJSONObject(i)
            val id = row.optString("id")
            if (id.isBlank()) continue
            if (DoctorQueueModel.isInQueue(row)) byId[id] = DoctorQueueModel.parse(row)
            else byId.remove(id)   // লাইন থেকে বেরিয়ে গেছে (checkup সম্পন্ন ইত্যাদি)
        }

        var merged = mergeOwnPhonePatients(branchFilter, byId.values.toList())
        val oncePerMobile = mutableListOf<QueuePatient>()
        val seenMobiles = HashSet<String>()
        for (q in merged) {
            val k = q.mobile.filter { it.isDigit() }.takeLast(10)
            if (k.length == 10 && !seenMobiles.add(k)) continue
            oncePerMobile.add(q)
        }
        val sorted = DoctorQueueModel.sortNewestFirst(oncePerMobile)
        val result = if (includePhoto) sorted else fillPhotosFromCache(branchFilter, sorted)
        saveCachedQueue(branchFilter, result, includePhoto)

        sp.edit().putString("since_$key", stampNow()).apply()
        return result
    }

    private fun markFullDone(sp: android.content.SharedPreferences, key: String, now: Long) {
        try {
            sp.edit()
                .putString("since_$key", stampNow())
                .putLong("fullAt_$key", now)
                .apply()
        } catch (_: Throwable) { }
    }

    /** এখনকার সময়, ৫ সেকেন্ড পিছিয়ে (LiveRefresh.kt-এর একই প্রমাণিত মাপ) —
     *  ঘড়ি/নেটওয়ার্কের সামান্য গরমিলে কোনো বদল যেন বাদ না পড়ে। */
    private fun stampNow(): String = try {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(System.currentTimeMillis() - SAFETY_BACK_MS))
    } catch (_: Throwable) { "" }
}
