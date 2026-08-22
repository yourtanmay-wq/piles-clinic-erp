package com.tkbiswas.pilesclinic.native

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles native Registration save: generates the correct Patient ID,
 * checks for an existing Patient with the same mobile (matching
 * savePatient()'s duplicate rule -- an existing Enquiry is NOT a block, only
 * an existing Patient is), then saves the patient + its Visit-stage
 * follow-up + Visit Fee payment, with the same offline pending-queue
 * fallback pattern as EnquiryRepository.
 *
 * SCOPED LIMITATION (by design, for this step): when a patient with this
 * mobile already exists, the WebView asks whether to update that existing
 * record in place ("same patient re-registering"). This native version
 * currently always creates a new record and asks the staff to confirm --
 * merging into the existing record will be added once this becomes a real
 * reported need, to keep this step's scope reviewable.
 */
class RegistrationRepository(private val context: Context) {

    companion object {
        // TK-REQUESTED FIX (2026-07-19): same reasoning as LocalWorkflowStore's
        // new companion LOCK -- a fresh RegistrationRepository(context) is
        // created almost everywhere this is used, so without a shared lock,
        // BottomNav's background flushPending() and a live save() happening
        // at the same moment could race on this class's own pending/close
        // queues and silently drop one of them.
        private val LOCK = Any()
    }

    private val prefs = context.getSharedPreferences("piles_clinic_registration_pending", Context.MODE_PRIVATE)

    /**
     * 🚨 TK'S RULE (28.07.2026, খাতার সারি B30): *"কোন প্রকার রোগীর যেন ডুপ্লিকেট
     * না হয়। সিস্টেমে যদি আগে থেকে থাকে অবশ্যই ওয়ার্নিং দিতে হবে।"*
     * [verified] = যাচাইটা সত্যিই করা গেছে কিনা। লাইন খারাপ থাকলে ক্লাউডে দেখাই
     * যায় না — তখন `found = false` মানে **"নতুন"** নয়, মানে **"জানা যায়নি"**।
     * ওই অবস্থায় স্টাফকে ওয়ার্নিং দেখাতে হবে, চুপচাপ নতুন রোগী বানানো যাবে না।
     */
    data class DuplicatePatient(
        val found: Boolean, val name: String, val branch: String,
        val patientId: String, val rowId: String = "", val verified: Boolean = true
    )

    fun checkDuplicatePatient(mobileDigitsOnly: String): DuplicatePatient {
        val normalized = PatientModel.normalizedMobile(mobileDigitsOnly)
        // খাতার সারি B30: ব্যর্থ হলে `null` — "নতুন" আর "দেখতেই পারলাম না" আর এক নয়।
        val cloud = SupabaseClient.findByMobileOrNull("patients", normalized, "id,name,branch,patientId")
        val rows = cloud ?: org.json.JSONArray()
        if (rows.length() > 0) {
            val row = rows.getJSONObject(0)
            return DuplicatePatient(true, row.s("name"), row.s("branch"), row.s("patientId"), row.s("id"))
        }
        // 🔒 V235 (TK verified 01.08.2026): Duplicate check এখন Alternate নম্বরেও।
        // Primary-তে না মিললে দেখা হয় কোনো রোগীর `altMobile` এই নম্বর কিনা।
        // ⛔ সম্পূর্ণ additive — আগের Primary-match একটুও বদলায়নি। altMobile column
        //    এখনো যোগ না হলে query খালি ফেরে (কিছু ভাঙে না)।
        val altRows = try {
            SupabaseClient.fetchList("patients", "altMobile=eq.$normalized", 1)
        } catch (_: Throwable) { org.json.JSONArray() }
        if (altRows.length() > 0) {
            val row = altRows.getJSONObject(0)
            return DuplicatePatient(true, row.s("name"), row.s("branch"), row.s("patientId"), row.s("id"))
        }
        // TK'S STANDING RULE (restated 2026-07-27): ONE MOBILE = ONE REGISTRATION.
        // The cloud lookup above returns an EMPTY list on any network failure --
        // by design, so a hiccup never blocks a save -- and an empty list was
        // being read as "this number is new". On a slow/dead line that quietly
        // created a SECOND patient row and a SECOND Patient ID for a number that
        // was already registered. The phone's own saved list needs no network,
        // so ask it before concluding the number is new. Nothing is blocked: if
        // it is not there either, the save goes ahead exactly as before.
        val local = LocalWorkflowStore(context).findPatientByMobile(normalized)
        if (local != null) {
            return DuplicatePatient(
                true,
                local.s("name"),
                local.s("branch"),
                local.s("patientId"),
                local.s("id")
            )
        }
        // ক্লাউডে দেখাই গেল না, ফোনেও নেই — তাই "নতুন" বলা যাচ্ছে না, শুধু
        // "জানা যায়নি"। ডাকা পর্দা এটা দেখে স্টাফকে ওয়ার্নিং দেবে।
        return DuplicatePatient(false, "", "", "", "", verified = cloud != null)
    }

    /** Returns the generated Patient ID on success (network reachable and
     * all 3 rows saved), or null if it had to be queued for later -- the
     * caller should still treat null as a successful save from the staff's
     * point of view (see RegistrationActivity), just not yet synced. */
    fun save(draft: RegistrationDraft, staffMobile: String, existingPatientId: String = "", existingRowId: String = ""): String? {
        // 🔴🔴🔴 খাতার সারি B455 (TK-রিপোর্ট, ছবিসহ — একই রোগীর (GOURANGO
        // BARMAN) দুইবার Registration, দুইবার Visit Fee কাটা)। **আসল
        // কারণ যতটা কোড পড়ে বোঝা গেছে:** Visit Fee কাটার সিদ্ধান্ত
        // সম্পূর্ণ নির্ভর করে `existingRowId`-এর উপর, আর `existingRowId`
        // আসে শুধু উপরের স্ক্রিনের মোবাইল-দিয়ে-খোঁজা ডুপ্লিকেট-চেক থেকে
        // (checkDuplicatePatient) — সেই চেক ব্যর্থ হলে (পুরনো রেকর্ডের
        // মোবাইল অন্যভাবে লেখা ছিল, নেট সমস্যা, বা স্টাফ "তবুও সেভ করুন"
        // চাপলে) `existingRowId` ফাঁকাই থেকে যায়, আর দ্বিতীয়বার Visit Fee
        // কাটে। **অতিরিক্ত সুরক্ষা (এখন যোগ করা হলো):** এখানে, লেখার
        // ঠিক আগে, যে id-তে আসল সেভ (upsert) হতে চলেছে (মোবাইল থেকে
        // তৈরি স্থায়ী id) সেই id-তে ইতিমধ্যে কোনো সারি আছে কিনা সরাসরি
        // আরেকবার যাচাই করা হয় — উপরের স্ক্রিনের মোবাইল-টেক্সট-খোঁজার
        // উপর নির্ভর না করে, সরাসরি সেই id ধরে। থাকলে সেটাকেই
        // "existingRowId" ধরে নেওয়া হয় (Visit Fee কাটে না, patientId-ও
        // পুরনোটাই থাকে) — এমনকি প্রথম চেক মিস করলেও। ⛔ এই নতুন চেক
        // ব্যর্থ হলে (নেট সমস্যা) আগের আচরণই চলে — নতুন কোনো ব্লক নেই।
        var effectiveRowId = existingRowId
        var effectivePatientId = existingPatientId
        if (effectiveRowId.isBlank()) {
            try {
                val stableId = PatientModel.stableRowId(draft.mobileDigitsOnly)
                val existing = SupabaseClient.fetchListOrNull("patients", "id=eq.$stableId", 1, select = "id,patientId")
                if (existing != null && existing.length() > 0) {
                    val row = existing.getJSONObject(0)
                    effectiveRowId = row.s("id")
                    effectivePatientId = row.s("patientId").ifBlank { existingPatientId }
                }
            } catch (_: Throwable) { }
        }
        val existingRowIdSafe = effectiveRowId
        val existingPatientIdSafe = effectivePatientId
        val patientId = existingPatientIdSafe.ifBlank { PatientIdGenerator.generate(draft.branch, draft.date, context) }
        val patientRow = PatientModel.buildPatientRow(draft, patientId, staffMobile, existingRowIdSafe)
        /* 🔴🔒 V399 (16.08.2026, TK-রিপোর্ট ছবিসহ — "২ বার ৩ বার হয়ে যাচ্ছে"):
           এই রোগীর Follow-up (Visit) সারি ক্লাউডে আগে থেকেই আছে কিনা দেখা হয় —
           থাকলে **সেটার আইডিই** ব্যবহার হয়, তাই নতুন সারি আর তৈরি হয় না।
           ⛔ ঠিক B455-এর (Visit Fee) প্রমাণিত প্যাটার্ন — লেখার ঠিক আগে একবার যাচাই।
           ⛔ যাচাই ব্যর্থ হলে (নেট নেই) আগের হুবহু আচরণ — কোনো নতুন বাধা নেই।
           ⛔ স্থানীয় স্টোরে আগে থেকেই (মোবাইল+stage) মিলিয়ে আপডেট হয়
              (`LocalWorkflowStore.upsertFollowUp`), তাই সমস্যাটা শুধু ক্লাউডেই ছিল। */
        var existingFollowUpRowId = ""
        try {
            val refForFu = patientRow.s("id")
            if (refForFu.isNotBlank()) {
                val fu = SupabaseClient.fetchListOrNull(
                    "followups", "refId=eq.$refForFu&stage=eq.Patient", 1, select = "id")
                if (fu != null && fu.length() > 0) existingFollowUpRowId = fu.getJSONObject(0).s("id")
            }
        } catch (_: Throwable) { }
        val visitFollowUpRow = PatientModel.buildVisitFollowUpRow(patientRow, staffMobile, existingFollowUpRowId)
        // TK-REPORTED BUG FIX (2026-07-25): this used to build+queue a brand
        // new Visit Fee payment row EVERY time save() ran, even via "Update
        // Existing" on the duplicate-mobile popup -- so re-saving an
        // already-registered patient (e.g. correcting a typo, or the staff
        // choosing Update Existing again) silently charged ANOTHER ₹Visit
        // Fee each time, with no dedupe, exactly the repeated "Fees-400/-
        // Cash" rows TK's photo-proof caught. A Visit Fee is only real for
        // a genuinely NEW registration (existingRowId blank); "Update
        // Existing" (existingRowId set) now only updates the patient/
        // followup record, no new fee.
        val paymentRow = if (existingRowIdSafe.isBlank()) PatientModel.buildVisitFeePaymentRow(patientRow, draft, staffMobile) else null

        // OWNER-LOCK: Registration and Registration Fee are one action.
        // Move Enquiry -> Visit locally first and return without waiting for network.
        val localStore = LocalWorkflowStore(context)
        localStore.upsertFollowUp(visitFollowUpRow)
        // TK-REQUESTED ADDITION (2026-07-16): also cache the patient row
        // locally (same pattern as visitFollowUpRow above) so Doctor Queue
        // can show this patient immediately, before the cloud sync below
        // finishes.
        localStore.upsertPatient(patientRow)
        // TK-REQUESTED (2026-07-27), same step: the Visit Fee row was the ONE
        // row of a registration that was not also kept on the phone -- so an
        // offline registration's fee was invisible in Today's Collection until
        // the line came back. Every other payment in the app (Chamber, Advance,
        // Treatment) already caches its row exactly like this, and the readers
        // skip a local row once the cloud row with the same id is seen, so a
        // fee can never be counted twice.
        if (paymentRow != null) localStore.upsertPayment(paymentRow)
        localStore.closeInquiry(draft.mobileDigitsOnly, patientId)
        // TK-REPORTED (2026-07-27): these three rows go to the cloud one after
        // another, and the retry sends them in exactly the order they are
        // queued here. If the line dies half-way, whichever rows were sent
        // first are the ones that exist.
        //
        // The order used to be patients -> followups -> payments. The Visit
        // tab reads the FOLLOWUPS row, while Chamber Attendance reads the
        // PATIENTS row -- so a half-finished send produced exactly the
        // complaint TK keeps getting: "the patient is in Chamber but missing
        // from the Visit card". Sending the followups row FIRST makes the
        // patient appear where the staff actually looks for them, and the
        // remaining rows follow on the next retry.
        //
        // Nothing about WHAT is saved changes -- same three rows, same
        // contents, same retry. Only which one leaves the phone first.
        // TK-REQUESTED (2026-07-27), "এক রোগী = এক রেকর্ড" ধাপ ১-এর শেষ অংশ:
        // these rows belong to ONE registration, so they now carry one shared
        // batch tag. flushPending() below uses it for a single purpose: if the
        // record is deleted while part of it is still waiting here, the WHOLE
        // group is dropped together. Before this, only the row whose own id was
        // marked deleted was dropped and the rest were still pushed -- leaving
        // a follow-up card or a payment in the cloud with no patient behind it
        // (exactly "চেম্বারে আছে, ভিজিট কার্ডে নেই"). Nothing else changes:
        // same three rows, same contents, same order, same retry.
        val batchId = "reg_" + java.util.UUID.randomUUID().toString().replace("-", "")
        queuePending("followups", visitFollowUpRow, batchId)
        queuePending("patients", patientRow, batchId)
        if (paymentRow != null) {
            queuePending("payments", paymentRow, batchId)
        }
        // TK-REPORTED BUG FIX (2026-07-16): closeSourceEnquiry() (below) used
        // to run ONLY inline here, once, and ONLY if flushPending() emptied
        // the queue on this very first attempt. If that first attempt
        // failed, the OLD Enquiry-stage row on the cloud stayed "Active"
        // forever -- not because anyone could still see it (the Enquiry tab
        // already hides it once the Visit-stage record exists, so this
        // wasn't a visible duplicate-tab bug), but the raw enquiry/followup
        // row itself never got cleaned up on the cloud, which could throw
        // off anything else that reads "enquiries" directly (Reports,
        // Global Search, Draft, CSV Export). Queuing this the same way as
        // the rows above means BottomNav's retry (V77 fix) now finishes
        // this step too, not just the Patient/Visit/Payment rows.
        queueCloseIntent(draft.mobileDigitsOnly, patientId)

        Thread {
            try {
                flushPending()
                if (loadPendingQueue().length() == 0) {
                    localStore.upsertFollowUp(visitFollowUpRow, "SYNCED")
                    localStore.upsertPatient(patientRow, "SYNCED")
                }
            } catch (_: Throwable) { }
        }.start()
        return patientId
    }

    /**
     * Native port of app.js closeEnquiryAfterRegistration(): flips the source
     * enquiry row to Registered (recording the new patientId) and moves its
     * Inquiry-stage follow-up row to Registered/Closed, so a converted enquiry
     * no longer shows as an open Inquiry.
     * TK-REPORTED BUG FIX (2026-07-16): this used to swallow every network
     * error internally and never tell the caller anything went wrong -- so
     * flushCloseIntents() below (which retries this on failure) could never
     * actually detect a failure and would just drop the retry after one
     * attempt, exactly the same "silently claims success" bug already fixed
     * elsewhere in this file. Now returns whether every update it attempted
     * actually succeeded, so a real failure gets retried and a real success
     * removes it from the queue -- still "best-effort" in the sense that it
     * never throws/blocks the original save either way. */
    private fun closeSourceEnquiry(mobileDigitsOnly: String, patientId: String): Boolean {
        val digits = mobileDigitsOnly.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return true // nothing meaningful to retry
        val now = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
        ).format(java.util.Date())
        var allOk = true

        try {
            // Update every matching Enquiry row, not only the first match.
            // 🔵🔒 V494 (২১.০৮.২০২৬, TK-যাচাই ৫): নিচে এই তালিকা থেকে **শুধু
            // `id`** পড়া হয় (:260), অথচ আগে `select=*` দিয়ে প্রতিটা সারির সব
            // ঘর নামত। ⛔ সারির সংখ্যা · ছাঁকনি · limit কিছুই বদলায়নি।
            // ⛔ সরু পড়া ব্যর্থ হলে fetchListSlim নিজেই পুরনো পথে ফিরে যায়।
            val enquiries = SupabaseClient.fetchListSlim(
                "enquiries", "mobile=like.*$digits", 5000, "id"
            )
            for (i in 0 until enquiries.length()) {
                val id = enquiries.getJSONObject(i).s("id")
                if (id.isBlank()) continue
                val fields = JSONObject()
                    .put("stage", "Registered")
                    .put("status", "Registered")
                    .put("nextFollow", "")
                    .put("convertedPatientId", patientId)
                    .put("convertedAt", now)
                    .put("updatedAt", now)
                if (!SupabaseClient.updateById("enquiries", id, fields)) allOk = false
            }
        } catch (_: Exception) { allOk = false }

        try {
            // Close every Inquiry-stage follow-up for this mobile. Only columns
            // that actually exist in the followups table are patched, so the
            // whole update cannot fail because of unknown fields.
            // 🔵🔒 V494 (২১.০৮.২০২৬, TK-যাচাই ৫): নিচে এই তালিকা থেকে **শুধু
            // `id` ও `history`** পড়া হয় (:282, :284)। আগে `select=*` মানে
            // followups সারির **রোগীর base64 ছবিও** নামত — আর এটা চলে
            // **প্রতিটা রেজিস্ট্রেশনে**। ⛔ সারি · ছাঁকনি · limit অপরিবর্তিত।
            val followups = SupabaseClient.fetchListSlim(
                "followups", "mobile=like.*$digits&stage=eq.Inquiry", 5000, "id,history"
            )
            for (i in 0 until followups.length()) {
                val row = followups.getJSONObject(i)
                val id = row.s("id")
                if (id.isBlank()) continue
                val history = row.optJSONArray("history") ?: JSONArray()
                history.put(
                    JSONObject()
                        .put("date", PatientModel.today())
                        .put("remark", "Converted to Patient Registration")
                        .put("staff", "Registration")
                )
                val fields = JSONObject()
                    .put("stage", "Registered")
                    .put("status", "Closed")
                    .put("nextFollow", "")
                    .put("lastRemark", "Converted to Patient Registration")
                    .put("history", history)
                    .put("updatedAt", now)
                if (!SupabaseClient.updateById("followups", id, fields)) allOk = false
            }
        } catch (_: Exception) { allOk = false }
        return allOk
    }

    fun flushPending() {
        synchronized(LOCK) {
        val queue = loadPendingQueue()
        if (queue.length() > 0) {
            // TK-REQUESTED (2026-07-27): first work out which registration
            // groups have been deleted in the meantime. If ANY row of a group
            // was deleted, none of that group's rows may be pushed -- half a
            // patient in the cloud is worse than none. Rows queued by an older
            // version carry no group tag and behave exactly as before.
            val cancelledBatches = HashSet<String>()
            for (i in 0 until queue.length()) {
                val e = queue.optJSONObject(i) ?: continue
                val b = e.optString("batch", "")
                if (b.isBlank()) continue
                val rid = e.optJSONObject("row")?.optString("id", "") ?: ""
                if (DeletedGuard.isDeleted(e.optString("table"), rid, context)) cancelledBatches.add(b)
            }
            val stillPending = JSONArray()
            for (i in 0 until queue.length()) {
                val entry = queue.getJSONObject(i)
                val table = entry.getString("table")
                val row = entry.getJSONObject("row")
                val batch = entry.optString("batch", "")
                if (batch.isNotBlank() && cancelledBatches.contains(batch)) continue
                // TK-REQUESTED (2026-07-26): a row deleted in the meantime must
                // not be pushed back into the cloud by this retry. Dropped from
                // the queue; every other row is handled exactly as before.
                if (DeletedGuard.isDeleted(table, row.optString("id", ""), context)) continue
                if (SupabaseClient.upsert(table, row)) {
                    // TK-REPORTED BUG FIX (2026-07-16): same fix as
                    // EnquiryRepository.flushPending() -- confirm the local
                    // cache row as SYNCED right when this retry succeeds,
                    // so LocalWorkflowStore's stale-cloud-refresh guard
                    // doesn't keep treating this record as having an
                    // un-synced local change forever after it has, in
                    // fact, already reached the cloud.
                    when (table) {
                        "patients" -> LocalWorkflowStore(context).upsertPatient(row, "SYNCED")
                        "followups" -> LocalWorkflowStore(context).upsertFollowUp(row, "SYNCED")
                        "payments" -> LocalWorkflowStore(context).upsertPayment(row, "SYNCED")
                    }
                } else {
                    stillPending.put(entry)
                }
            }
            savePendingQueue(stillPending)
        }
        // TK-REPORTED BUG FIX (2026-07-16): retry any "close the source
        // Enquiry" step that didn't finish on a previous attempt too --
        // checked independently every time (BottomNav.wire() calls this
        // often), not skipped just because the Patient/Visit/Payment rows
        // above already finished syncing.
        flushCloseIntents()
        }
    }

    /** Queues "close the source Enquiry for this mobile" so it can be
     * retried later if the very first attempt (in save() above) doesn't
     * reach the cloud. Safe to run more than once -- closeSourceEnquiry()
     * just re-writes the same "Registered/Closed" state either way. */
    private fun queueCloseIntent(mobileDigitsOnly: String, patientId: String) {
        synchronized(LOCK) {
        val queue = loadCloseQueue()
        val next = JSONArray()
        for (i in 0 until queue.length()) {
            val e = queue.getJSONObject(i)
            if (e.optString("mobile") != mobileDigitsOnly) next.put(e)
        }
        next.put(JSONObject().put("mobile", mobileDigitsOnly).put("patientId", patientId))
        prefs.edit().putString("closeQueue", next.toString()).commit()
        }
    }

    private fun flushCloseIntents() {
        synchronized(LOCK) {
        val queue = loadCloseQueue()
        if (queue.length() == 0) return
        val stillPending = JSONArray()
        for (i in 0 until queue.length()) {
            val e = queue.optJSONObject(i) ?: continue
            try {
                val ok = closeSourceEnquiry(e.optString("mobile"), e.optString("patientId"))
                if (!ok) stillPending.put(e)
            } catch (_: Throwable) {
                stillPending.put(e)
            }
        }
        prefs.edit().putString("closeQueue", stillPending.toString()).commit()
        }
    }

    private fun loadCloseQueue(): JSONArray {
        val raw = prefs.getString("closeQueue", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    private fun queuePending(table: String, row: JSONObject, batch: String = "") {
        synchronized(LOCK) {
        // 🚨 TK-REPORTED, LIVE (2026-07-27): "কখনো পেমেন্ট হারিয়ে যায়, কখনো
        // পেশেন্ট হারিয়ে যায়..." ROOT CAUSE FOUND HERE.
        //
        // The retry loop that later pushes this row to the cloud SKIPS any row
        // whose id sits in the "deleted" list (DeletedGuard) . that guard
        // exists so a record deleted by staff cannot be resurrected by an old
        // queued save, which is right. BUT an id can legitimately come back:
        // "Update Existing" on the duplicate-mobile popup reuses the same row
        // id, a patient restored from Trash keeps their id, and a person can
        // be registered again after being deleted. In every one of those
        // cases the brand-new save was silently thrown away FOREVER . the
        // staff saw "saved", nothing stayed queued, and nothing ever reached
        // the cloud. That is a patient or a payment simply gone.
        //
        // FIX: a NEW save always beats an OLD delete mark. Clearing the mark
        // here, at the moment of saving, keeps the guard's real purpose
        // intact: if staff delete this record AFTER this save is queued, the
        // delete marks it again and the retry still correctly drops it.
        try { DeletedGuard.unmark(table, row.optString("id", ""), context) } catch (_: Throwable) { }
        val queue = loadPendingQueue()
        val id = row.optString("id")
        val next = JSONArray()
        for (i in 0 until queue.length()) {
            val e = queue.getJSONObject(i)
            val same = e.optString("table") == table && id.isNotBlank() && e.optJSONObject("row")?.optString("id") == id
            if (!same) next.put(e)
        }
        val newEntry = JSONObject().put("table", table).put("row", row)
        if (batch.isNotBlank()) newEntry.put("batch", batch)
        next.put(newEntry)
        savePendingQueue(next)
        // TK-REQUESTED (2026-07-25): the moment anything is queued, ask
        // WorkManager to sync right away. WorkManager runs even when the
        // app is closed or the staff switched to another app, so a save
        // no longer waits for the next screen-open or the 15-minute
        // backstop . it reaches the cloud within seconds of the network
        // being available. Nothing else changes; the same proven
        // flushPending() work runs, just sooner.
        try { com.tkbiswas.pilesclinic.data.sync.SyncScheduler.syncNow(context) } catch (_: Throwable) { }

        }
    }

    private fun loadPendingQueue(): JSONArray {
        val raw = prefs.getString("queue", "[]") ?: "[]"
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    private fun savePendingQueue(queue: JSONArray) {
        prefs.edit().putString("queue", queue.toString()).commit()
    }
}
