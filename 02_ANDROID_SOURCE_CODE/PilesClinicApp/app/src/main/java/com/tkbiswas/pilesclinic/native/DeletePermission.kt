package com.tkbiswas.pilesclinic.native

import android.content.Context

/**
 * 🔒 TK-এর নিয়ম (29.07.2026 রাত ১০.১০ · খাতার সারি B98):
 *
 *   *"মাস্টার ছাড়া কেউ ডিলিট করবে না। staff-রা করতে চাইলে Master admin-এর কাছে
 *    অনুমতি নিতে হবে। Master-এর ঘন্টাতে যাবে। মাস্টার অনুমতি দিলে ডিলিট হবে,
 *    অন্যথায় হবে না।"*
 *
 * ─────────────────────────────────────────────────────────────
 * **এটা যা করে**
 *  • `canDeleteNow()` — এই মানুষটা **এখনই** ডিলিট করতে পারবেন কি না।
 *    শুধু **মাস্টার** পারবেন। স্টাফ/ডাক্তার/ফিল্ড — কেউ না।
 *  • `sendRequest()` — স্টাফ ডিলিট চাইলে **মাস্টারের ঘন্টায়** একটা অনুরোধ যায়।
 *    ⛔ এই মুহূর্তে **কিছুই মোছে না** — মাস্টার নিজে দেখে তবেই মুছবেন।
 *
 * ─────────────────────────────────────────────────────────────
 * **কেন এভাবে (ডেটাবেসে নতুন ঘর ছাড়া)**
 *  অনুরোধটা যায় আগে থেকেই থাকা **`briefings`** টেবিলে, `role = master` লক্ষ্য
 *  করে — অর্থাৎ ঠিক সেই ব্যবস্থাটাই যা দিয়ে মাস্টারের **ঘন্টা** কাজ করে।
 *  ⛔ **কোনো নতুন টেবিল বা ঘর লাগেনি, তাই TK-কে কোনো SQL চালাতে হবে না।**
 *  ⛔ অনুরোধ পাঠাতে না পারলেও কিছু মোছে না — শুধু "পাঠানো গেল না" বলে।
 *
 * ⛔ **পুরনো `TrashHelper.canDelete()` ছোঁয়া হয়নি** — ডাক্তার মডিউল ও অন্য
 *    জায়গা আগের নিয়মেই চলে; এই ফাইলটা শুধু রোগীর রেকর্ড মোছার পথে ব্যবহার হয়।
 */
object DeletePermission {

    /** শুধু মাস্টারই এখনই মুছতে পারেন। */
    fun canDeleteNow(user: NativeUser?): Boolean = user != null && user.role == "master"

    private fun todayIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun yesterdayIso(): String {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(c.time)
    }

    /**
     * 🔒🔒 খাতার সারি B112 (29.07.2026) — **পরবর্তীতে B467-এ (06.08.2026)
     * TK-নির্দেশে পুরোপুরি হালনাগাদ, এটাই এখন ফাইনাল নিয়ম।**
     *
     * TK-এর কথা (06.08.2026, খাতার সারি B467): *"যতক্ষণ অ্যাডভান্স না করে
     * ততক্ষণ সে পেশেন্ট হয় না — যদি কারো এডভান্স বা বিল তৈরি হয়ে যায় এবং
     * কিছু টাকা এডভান্স করে [তবেই অনুমতির নিয়ম প্রযোজ্য]।"* আর টাকা জমা
     * থাকা এন্ট্রির জন্য: *"একই দিন, OUT TIME অথবা চেম্বার বন্ধের আগ পর্যন্ত
     * ফ্রি, তারপর অনুমতি লাগবে।"* (আগের B443-এর "Treatment-stage-এ সবসময়
     * অনুমতি লাগবে, আজ হলেও ছাড় নেই" — এই কড়া নিয়মটা আজ TK নিজেই বদলে
     * দিয়েছেন, এটাই এখন চূড়ান্ত।)
     *
     * ### নিয়ম (এই ক্রমেই যাচাই হয়)
     * ১. **মাস্টার** → সবসময় পারবেন।
     * ২. **টাকা জমা পড়েনি** (`paid = false` — Enquiry বা এখনো-Advance-না-হওয়া
     *    Visit) → স্টাফ **যেকোনো দিন, কোনো অনুমতি ছাড়াই** পারবেন।
     * ৩. টাকা জমা পড়েছে (`paid = true`) কিন্তু এন্ট্রিটা **আজকের নয়** →
     *    পারবেন না (অনুমতি লাগবে)। ⛔ আগে আজ+গতকাল দুদিন চলত, এখন শুধু আজ।
     * ৪. আজকেরই, কিন্তু ওই দিনের **চেম্বার বন্ধ হয়ে গেছে** → পারবেন না।
     * ৫. আজকেরই, চেম্বারও খোলা, কিন্তু স্টাফ নিজে আজকের **OUT TIME সেভ করে
     *    ফেলেছেন** → পারবেন না (নতুন শর্ত, B467)।
     * ৬. উপরের কোনোটাই না হলে → পারবেন।
     *
     * ⛔ ব্রাঞ্চের নিয়ম আগের মতোই — স্টাফ নিজের ব্রাঞ্চের বাইরে এমনিতেই কিছু
     *    করতে পারেন না (খাতার সারি B52-এর টাকার নিয়ম)।
     * ⛔ **কিছু বুঝতে না পারলে "না"** — তারিখ/ব্রাঞ্চ ফাঁকা হলে, বা OUT TIME
     *    সত্যিই আছে কিনা ক্লাউড থেকে জানা না গেলে (নেট ব্যর্থ), অনুমতি
     *    লাগবে ধরে নেওয়া হয় — টাকার ব্যাপারে সন্দেহ হলে আটকানোই নিরাপদ।
     * ⚠️ **এই ফাংশন ক্লাউড ছুঁতে পারে** (চেম্বার বন্ধ কিনা, OUT TIME হয়েছে
     *    কিনা দেখতে) — তাই সবসময় `Dispatchers.IO`-তে ডাকতে হবে।
     *
     * @param paid টাকা (Advance/Bill) জমা পড়া এন্ট্রি কিনা — কল-সাইট নিজে
     *    ঠিক করে পাঠায় (কোনো ডিফল্ট নেই, ভুল করে "ফ্রি" ধরে যাওয়ার ঝুঁকি
     *    এড়াতে); Payment/টাকার সারি ডিলিটে সবসময় `true`, খাঁটি Enquiry
     *    ডিলিটে সবসময় `false`, রোগীর রেকর্ডে `isTreatmentStage` (Advance
     *    হয়েছে কিনা)।
     */
    fun canDeleteEntryNow(
        context: Context?,
        user: NativeUser?,
        entryDate: String,
        branch: String,
        paid: Boolean
    ): Boolean {
        if (user == null) return false
        if (user.role == "master") return true
        // 🆕 B467 (06.08.2026): টাকা জমাই না পড়ে থাকলে (Enquiry/Visit,
        // এখনো Advance হয়নি) — যেকোনো দিন, কোনো অনুমতি ছাড়াই।
        if (!paid) return true
        val d = entryDate.take(10)
        if (d.isBlank() || branch.isBlank()) return false
        // 🆕 B467 (06.08.2026): আগে আজ+গতকাল দুদিন চলত, এখন শুধু আজকের এন্ট্রি।
        if (d != todayIso()) return false
        // চেম্বার বন্ধ হয়ে গেলে মাস্টার হিসাব পেয়ে গেছেন — স্টাফ আর মুছতে পারবেন না।
        if (try { ChamberCloseRepository.isClosed(context, branch, d) } catch (_: Throwable) { false }) return false
        // 🆕 B467 (06.08.2026, TK-নির্দেশ): নিজের আজকের OUT TIME সেভ হয়ে
        // গেলে আর ফ্রি না — এটা বা চেম্বার বন্ধ, যেটা আগে হয়। ক্লাউড থেকে
        // সত্যিই জানা না গেলে (নেট ব্যর্থ) নিরাপদ দিক ধরে "হ্যাঁ, OUT TIME
        // হয়ে গেছে" ধরে নেওয়া হয় (অনুমতি লাগবে) — সন্দেহে আটকানোই নিরাপদ।
        if (hasOutTimeToday(context, user)) return false
        return true
    }

    /** B467-এর OUT TIME-চেক — `wn.notebook_days`-এ আজকের সারিতে `check_out`
     *  ভরা আছে কিনা দেখে। নেট ব্যর্থ হলে বা সারি না পেলে-সঠিক-জানা-না-গেলে
     *  নিরাপদ দিক ধরে `true` (OUT TIME হয়ে গেছে ধরে নেওয়া, অনুমতি লাগবে)। */
    private fun hasOutTimeToday(context: Context?, user: NativeUser): Boolean {
        if (context == null) return true
        return try {
            val staffCode = user.name.ifBlank { user.mobile }
            val result = com.tkbiswas.pilesclinic.modules.ModuleAuth.getRowsChecked(
                "wn", "notebook_days",
                "select=check_out&staff_code=eq.${java.net.URLEncoder.encode(staffCode, "UTF-8")}&work_date=eq.${todayIso()}&limit=1"
            )
            if (!result.ok) return true // নেট ব্যর্থ — নিরাপদ দিক
            if (result.rows.length() == 0) return false // আজ এখনো কোনো সারিই নেই — OUT TIME হয়নি
            result.rows.getJSONObject(0).optString("check_out").isNotBlank()
        } catch (_: Throwable) { true }
    }

    /**
     * মাস্টারের ঘন্টায় অনুরোধ পাঠায়। কিছুই মোছে না।
     * @param what কী মোছার কথা — যেমন "Enquiry" / "Patient" / "Visit"
     */
    fun sendRequest(
        context: Context,
        user: NativeUser,
        what: String,
        name: String,
        mobile: String,
        patientId: String,
        branch: String,
        reason: String = "",
        // 🔒 খাতার সারি B111: টাকার সারির ক্ষেত্রে **ঠিক কোন সারিটা** মুছতে হবে
        // সেটা নম্বর দিয়ে বোঝা যায় না (একজনের অনেক পেমেন্ট থাকে), তাই সারির
        // নিজের আইডিটাও অনুরোধে পাঠানো হয়। ⛔ অন্য সব ক্ষেত্রে এটা ফাঁকা থাকে,
        // তাই আগের কোনো অনুরোধের চেহারা বদলায় না।
        rowId: String = "",
        // 🟢🔒 V641 (২৪.০৮.২০২৬, TK-রিপোর্ট — "আমি কেন বুঝব না এটা কিসের
        // পেশেন্ট ছিল, Patient ID দেখে কি বুঝব?") — এখন রোগ (Disease)-ও
        // অনুরোধে যায়, যাতে Master এক নজরেই বুঝতে পারেন কোন রোগী। ডিফল্ট
        // ফাঁকা, তাই disease না পাঠানো পুরনো caller-দের কোনো ক্ষতি হয় না —
        // ফাঁকা হলে সেই লাইনটা শুধু বসেই না।
        disease: String = ""
    ): Boolean {
        return try {
            val who = StaffDirectory.findAccount(user.mobile)?.name ?: user.mobile
            val sb = StringBuilder()
            sb.append("Delete permission request\n")
            sb.append("Type : ").append(what).append("\n")
            sb.append("Name : ").append(name.ifBlank { mobile }).append("\n")
            if (disease.isNotBlank()) sb.append("Disease : ").append(disease).append("\n")
            sb.append("Mobile : ").append(mobile).append("\n")
            if (patientId.isNotBlank()) sb.append("Patient ID : ").append(patientId).append("\n")
            if (branch.isNotBlank()) sb.append("Branch : ").append(branch).append("\n")
            if (rowId.isNotBlank()) sb.append("Row ID : ").append(rowId).append("\n")
            sb.append("Requested by : ").append(who).append("\n")
            if (reason.isNotBlank()) sb.append("Reason : ").append(reason).append("\n")
            BriefingRepository().post(
                context,
                "🗑️ Delete request — " + name.ifBlank { mobile },
                sb.toString(),
                "role",
                branch,
                "master",
                user.mobile
            )
        } catch (_: Throwable) { false }
    }

    /**
     * 🔒 খাতার সারি B100 (TK, 29.07.2026 রাত ১১.১০): *"হ্যাঁ করে দিন"* —
     * মাস্টার ঘন্টার নোটিশ থেকেই **এক চাপে** অনুরোধটা অনুমোদন করে ডিলিট করবেন।
     *
     * অনুরোধের লেখাটা এই ফাইলেরই `sendRequest()` বানায়, তাই ধাঁচটা নিশ্চিত —
     * সেখান থেকেই **Type** ও **Mobile** পড়ে নেওয়া হয়।
     *
     * ⛔ ডিলিটটা হয় অ্যাপের **সেই পুরনো পথেই** (`TrashHelper`) — Trash-এ যায়,
     *    ফেরানো যায়, টাকার ইতিহাস অক্ষত থাকে। নতুন কোনো নিয়ম বানানো হয়নি।
     * ⛔ সারি খুঁজে না পেলে বা লেখা পড়া না গেলে **কিছুই মোছে না**, সাফ বার্তা যায়।
     */
    fun approveAndDelete(message: String, masterMobile: String): String {
        return try {
            fun field(key: String): String {
                for (line in message.split("\n")) {
                    val t = line.trim()
                    if (t.startsWith("$key :")) return t.substringAfter("$key :").trim()
                }
                return ""
            }
            val type = field("Type")
            val mobile = field("Mobile").filter { it.isDigit() }.takeLast(10)
            if (mobile.length != 10) return "BAD_REQUEST"

            // 🔒 খাতার সারি B111 (TK, 29.07.2026 বিকেল ৫.৪০): *"staff ডিলিট করতে
            // পারবে না, মাস্টারের ঘন্টায় notification আসবে, মাস্টার অনুমতি দিলে
            // তবেই ডিলিট হবে।"* — টাকার সারির অনুরোধও এখন এখান দিয়েই যায়।
            // ⛔ টাকার সারি **আইডি ধরে** মোছা হয় (নম্বর ধরে নয়) — নইলে ভুল
            //    পেমেন্ট মুছে যেতে পারত। আইডি না থাকলে **কিছুই মোছে না**।
            if (type.equals("Payment", true)) {
                val rowId = field("Row ID")
                if (rowId.isBlank()) return "BAD_REQUEST"
                val payRows = SupabaseClient.fetchList("payments", "id=eq.$rowId", 1)
                if (payRows.length() == 0) return "NOT_FOUND"
                return if (TrashHelper.moveToTrash("payments", payRows.getJSONObject(0), masterMobile)) "OK" else "NETWORK"
            }
            // এনকোয়ারি হলে `enquiries`, রেজিস্ট্রেশন/রোগী হলে `patients`।
            val table = if (type.equals("Enquiry", true)) "enquiries" else "patients"
            val rows = SupabaseClient.findByMobile(table, mobile, "*", 1)
            if (rows.length() == 0) return "NOT_FOUND"
            val ok = TrashHelper.moveToTrashWithFollowupCascade(
                table, rows.getJSONObject(0), masterMobile, mobile
            )
            if (ok) "OK" else "NETWORK"
        } catch (_: Throwable) { "NETWORK" }
    }
}
