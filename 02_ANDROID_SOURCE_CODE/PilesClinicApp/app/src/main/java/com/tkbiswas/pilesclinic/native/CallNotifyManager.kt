package com.tkbiswas.pilesclinic.native

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tkbiswas.pilesclinic.R

/**
 * 🟢🔒 V605 (২৪.০৮.২০২৬, TK-নির্দেশ, ছবি-প্রুফ পাশ ৪ বার) — "Truecaller-এর
 * মতো" ইনকামিং/আউটগোয়িং কলে রোগীর পরিচয় + রিমার্কস/New Enquiry।
 *
 * TK-এর নির্দেশ (হুবহু):
 *   ১. "যদি Incoming নম্বর আমাদের app-এ আগে থেকে save না থাকে" — তাহলে
 *      শুধু নম্বর দেখাবে, "New Enquiry" অপশন থাকবে।
 *   ২. "নম্বর যদি save থেকে থাকে — কল কাটার পর Remarks লেখার option।"
 *   ৩. "কল চলাকালীন ও কল কাটার পরে দুটোতেই Remarks/New Enquiry Fillup।"
 *   ৪. "শুধু যেসব ফোনে চেম্বারের SIM আছে বলে জানানো আছে, সেখানেই।"
 *
 * 🔒 নিরাপত্তার সিদ্ধান্ত (TK-কে আগেই জানানো হয়েছে) — সত্যিকারের "কল
 * চলাকালীন ভেসে-থাকা উইন্ডো" SYSTEM_ALERT_WINDOW অনুমতি চায়, যেটা কিছু
 * ফোনে (Xiaomi/Vivo/Oppo) অস্থির/অনির্ভরযোগ্য। তাই এখানে **নোটিফিকেশন**
 * ব্যবহার হয়েছে — একই কার্যকারিতা (দেখা যায়, বোতাম চাপা যায়, কল চলা
 * অবস্থাতেই), কিন্তু প্রতিটা ফোনে নিরাপদ ও প্রমাণিত পথ।
 *
 * ⛔ শুধু একটাই বাড়তি অনুমতি (READ_PHONE_STATE) — এটা এই প্রজেক্টে আগে
 *    থেকেই আছে (B441, দুই-SIম আলাদা করতে)। নতুন কোনো অনুমতি চাওয়া হয়নি।
 * ⛔ আসল কল করা/ধরা/কাটা — সবই ফোনের নিজের সিস্টেমেই থাকে, এই অ্যাপ
 *    সেখানে হাতই দেয় না।
 */
object CallNotifyManager {

    private const val CHANNEL_ID = "call_id_v605"
    const val NOTIF_ID = 90501

    @Volatile private var activeNumber: String = ""
    @Volatile private var activeDirection: String = ""
    @Volatile private var activeCalledAt: String = ""
    @Volatile private var activeMatch: DialerRepository.MatchedContact? = null
    // 🟢🔒🔒 V637 (২৪.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ — "প্রথমবার Remarks-এ
    // 'কাল আসবে' লিখেছিলাম, পরেরবার কল এলে সেটা কেন দেখাচ্ছে না") — আগের
    // লেখা রিমার্কস এখানে মনে রাখা হয়, `post()`-এ নোটিফিকেশনের বোতামের
    // সাথে পাঠানো হয়।
    @Volatile private var activeExistingRemark: String = ""

    private fun isoNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

    /** 🔒 TK-নির্দেশ ৪ (২৪.০৮.২০২৬, পরে সংশোধিত V619) — শুধু যে ফোন আগে
     *  সত্যিই "হ্যাঁ, চেম্বারের SIM আছে" বলেছে, সেখানেই এই পুরো ফিচার চলে।
     *  ⛔ প্রথমবার `hasGenuinelyChosenSim()` ব্যবহার করেছিলাম (ভুল —
     *  সেটা Dialer-এর কল-লগ দেখানোর জন্য ইচ্ছাকৃতভাবে নরম, পুরনো এক-সিম
     *  ফোনকে প্রশ্ন ছাড়াই grandfather করে)। TK নিজের/Master-এর ফোনে এই
     *  ফাঁক ধরেছেন — এখন `hasExplicitlyConfirmedChamberSim()` (নতুন,
     *  কড়া, কোনো ছাড় নেই) ব্যবহার হচ্ছে।
     *
     *  🟢🔒🔒 V633 (২৪.০৮.২০২৬, TK-রিপোর্ট + স্পষ্ট নির্দেশ — "ব্রাঞ্চের
     *  নম্বর ছাড়া যেন এই কাজ কারো ফোনে না হয়") — **আসল কারণ ধরা পড়ল:**
     *  `tryAutoDetectChamberNumber()` ফোনের নিজের সিম-নম্বর পড়ে ব্রাঞ্চের
     *  নম্বরের সাথে মিলিয়ে **চুপচাপ, কাউকে না জিজ্ঞাসা করেই** "হ্যাঁ"
     *  ধরে নিত — আর Android-এর নিজের `line1Number` (বিশেষত ভারতীয় সিমে)
     *  প্রায়ই ভুল/পুরনো নম্বর ফেরত দেয় (এটা Android/অপারেটরেরই স্বীকৃত
     *  সীমাবদ্ধতা), তাই ভুল ফোনেও চালু হয়ে যেতে পারত।
     *  **সমাধান (TK-এর স্পষ্ট নির্দেশ অনুযায়ী):** পুরনো SIM-শনাক্তকরণ
     *  নিয়মের **উপরে** এখন একটা কড়া দ্বিতীয় পাহারা — যে মোবাইল দিয়ে
     *  App-এ লগইন করা আছে, সেটা **এই চারটে নির্দিষ্ট নম্বরের একটা** না
     *  হলে কল-শনাক্তকরণ ফিচারটাই সম্পূর্ণ বন্ধ থাকবে, ফোনে যাই থাকুক না
     *  কেন। দুটো শর্তই (পুরনো SIM-নিশ্চিতকরণ + নতুন লগইন-হোয়াইটলিস্ট)
     *  সত্যি হতে হবে — কোনোটা একা যথেষ্ট না।
     *  ⛔ Dialer-এর কল-লগ **দেখানোর** নিয়ম (`hasGenuinelyChosenSim`,
     *     `hasChamberNumber` — WorkNotebook/DialerActivity-তে ব্যবহৃত)
     *     এক অক্ষরও বদলায়নি — এই হোয়াইটলিস্ট শুধু **কল-শনাক্তকরণ
     *     নোটিফিকেশন**-এর (এই ফাইল) জন্য প্রযোজ্য।
     */
    private val ALLOWED_CALLER_ID_LOGINS = setOf(
        "8676002200",  // Kishanganj
        "8436002200",  // Jalpaiguri
        "8514002200",  // Coochbehar
        "8001080080"   // Master
    )

    private fun isWhitelistedLogin(ctx: Context): Boolean {
        val mobile = try {
            NativeSession.current(ctx)?.mobile?.filter { it.isDigit() }?.takeLast(10)
        } catch (_: Throwable) { null }
        return mobile != null && mobile in ALLOWED_CALLER_ID_LOGINS
    }

    private fun allowed(ctx: Context): Boolean =
        BranchSimHelper.hasExplicitlyConfirmedChamberSim(ctx) && isWhitelistedLogin(ctx)

    /** ফোন বাজছে — নম্বর মিলিয়ে সঙ্গে সঙ্গে দেখানো। */
    fun onRinging(ctx: Context, rawNumber: String) {
        if (!allowed(ctx)) return
        val digits = rawNumber.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        activeNumber = digits
        activeDirection = "incoming"
        activeCalledAt = isoNow()
        activeMatch = null
        activeExistingRemark = ""   // 🟢🔒 V637
        post(ctx, ringing = true, ended = false)
        // পিছনে গিয়ে মেলানো — পাওয়া গেলে নোটিফিকেশন আপডেট হবে।
        Thread {
            try {
                val m = DialerRepository.matchNumbersBatch(listOf(digits))[digits]
                // 🟢🔒🔒 V637 (২৪.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ) — আসল কারণ: এই
                // নোটিফিকেশনের "📝 Add Remark" বোতাম আগের কোনো লেখা রিমার্কস
                // কখনো নিয়ে আসতই না — `DialerRepository.fetchLatestRemark()`
                // ফাংশনটা আগে থেকেই ছিল (Dialer-এর নিজের তালিকায় ব্যবহৃত),
                // কিন্তু এই কল-শনাক্তকরণ ফ্লো থেকে কখনো ডাকাই হত না। এখন
                // একই ব্যাকগ্রাউন্ড থ্রেডেই আগের রিমার্কও আনা হয়।
                val remark = try { DialerRepository.fetchLatestRemark(digits) } catch (_: Throwable) { "" }
                if (activeNumber == digits) {
                    activeMatch = m; activeExistingRemark = remark
                    post(ctx, ringing = true, ended = false)
                }
            } catch (_: Throwable) { }
        }.start()
    }

    /** কল ধরা হলো (incoming) বা চলছে (outgoing) — একই নোটিফিকেশন, এখন
     *  বোতাম যোগ হয় (কল চলাকালীনও Remark/Enquiry লেখা যাবে, TK-নির্দেশ ৩)। */
    fun onOffhook(ctx: Context) {
        if (!allowed(ctx)) return
        if (activeNumber.isBlank()) return   // outgoing হলে notifyOutgoingDialed() থেকেই শুরু হয়
        post(ctx, ringing = false, ended = false)
    }

    /** কল কেটে গেল — একই তথ্য থাকবে, "Call ended" দেখাবে, বোতাম থাকবে। */
    fun onIdle(ctx: Context) {
        if (!allowed(ctx)) return
        if (activeNumber.isBlank()) return
        post(ctx, ringing = false, ended = true)
        // পরের কলের জন্য প্রস্তুত — কিন্তু নোটিফিকেশনটা (ended অবস্থায়)
        // থেকে যায়, স্টাফ চাপলে Remark/Enquiry খুলবে। নতুন RINGING/
        // notifyOutgoingDialed() এলে এই ভেরিয়েবলগুলো নতুন করে বসবে।
    }

    /** 🟢 TK-নির্দেশ ৩ (Outgoing-ও) — অ্যাপের নিজের Call বোতাম থেকে ডায়াল
     *  করার মুহূর্তেই ডাকা হয় (DialerActivity-তে জোড়া, নম্বর তখনই জানা)। */
    fun notifyOutgoingDialed(ctx: Context, rawNumber: String, matched: DialerRepository.MatchedContact?) {
        if (!allowed(ctx)) return
        val digits = rawNumber.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) return
        activeNumber = digits
        activeDirection = "outgoing"
        activeCalledAt = isoNow()
        activeMatch = matched
        activeExistingRemark = ""   // 🟢🔒 V637 — নিচে ব্যাকগ্রাউন্ডে আনা হচ্ছে
        post(ctx, ringing = false, ended = false)
        // 🟢🔒 V637 — outgoing কলেও আগের রিমার্কস আনা হয় (incoming-এর
        // `onRinging()`-এর হুবহু একই প্যাটার্ন)।
        Thread {
            try {
                val remark = DialerRepository.fetchLatestRemark(digits)
                if (activeNumber == digits) { activeExistingRemark = remark; post(ctx, ringing = false, ended = false) }
            } catch (_: Throwable) { }
        }.start()
    }

    private fun post(ctx: Context, ringing: Boolean, ended: Boolean) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NoticeChannels.ensure(ctx, CHANNEL_ID, "Call Identification", "Shows patient info during calls")

            val number = activeNumber
            val direction = activeDirection
            val calledAt = activeCalledAt
            val match = activeMatch
            val existingRemark = activeExistingRemark   // 🟢🔒 V637

            /* 🔴🔒 V812 (২৮.০৮.২০২৬) — TK-রিপোর্ট (ফটো সহ): কল-নোটিফিকেশনে
               **"null · Kishanganj"** লেখা উঠছিল। TK: *"এটা কি প্রফেশনাল লুক?"*
               কারণ: ঘরটা সত্যিই ফাঁকা নয় — তাতে **"null" শব্দটাই লেখা** থাকে
               (org.json-এর পুরনো ফাঁদ: SQL NULL পড়লে `optString` আক্ষরিক "null"
               ফেরায়)। তাই `ifBlank {}` ছাঁকনি ওটাকে ফাঁকা ধরতে পারত না।
               ⛔ এই সাফাইটা **নোটিফিকেশনের প্রতিটা ঘরেই** বসানো হলো (নিয়ম ৬.২),
                  যাতে অন্য কোনো ঘরেও কোনোদিন "null" ছাপা না হয়। */
            fun cln(v: String?): String {
                val t = v?.trim().orEmpty()
                return if (t.equals("null", ignoreCase = true) || t.equals("undefined", ignoreCase = true)) "" else t
            }
            val title = when {
                ringing -> "📞 Incoming: " + (cln(match?.name).ifBlank { number })
                direction == "outgoing" -> "📞 Calling: " + (cln(match?.name).ifBlank { number })
                else -> "📞 " + (cln(match?.name).ifBlank { number })
            }
            val lines = ArrayList<String>()
            if (match != null) {
                // 🟢🔒 V632 (২৪.০৮.২০২৬) — RMP মিললে স্পষ্ট "🩺 RMP" ট্যাগ +
                // এলাকা (area), যাতে রোগীর সারির সাথে গুলিয়ে না যায়।
                if (match.isRmp) {
                    lines.add(listOfNotNull("🩺 RMP", cln(match.branch).ifBlank { null }).joinToString(" · "))
                    if (cln(match.address).isNotBlank()) lines.add(cln(match.address))
                } else {
                    // ⛔ V812 — দুটো ঘরই ফাঁকা হলে যেন **খালি লাইন** না বসে।
                    val idBr = listOfNotNull(cln(match.patientId).ifBlank { null }, cln(match.branch).ifBlank { null }).joinToString(" · ")
                    if (idBr.isNotBlank()) lines.add(idBr)
                    if (cln(match.disease).isNotBlank()) lines.add(cln(match.disease))
                    if (cln(match.address).isNotBlank()) lines.add(cln(match.address))
                }
            } else {
                lines.add("Not saved anywhere in the app")
            }
            if (ended) lines.add(0, "Call ended")

            val style = NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n"))

            val builder = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(lines.firstOrNull().orEmpty())
                .setStyle(style)
                // 🟢🔒 V619 (২৪.০৮.২০২৬, TK-রিপোর্ট — "ভুল নম্বর হলে Cancel
                // থাকতে হত") — আগে কল চলাকালীন `setOngoing(true)` দিয়ে
                // সোয়াইপ করেও সরানো যেত না, আর কোনো Dismiss বোতামও ছিল
                // না। এখন সবসময় স্বাভাবিকভাবে সরানো যায় (সোয়াইপ), আর
                // নিচে একটা স্পষ্ট "✕ Dismiss" বোতামও থাকছে।
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)

            /* 👆🔒 V844 (৩০.০৮.২০২৬, TK-রিপোর্ট ছবিসহ: *"যে নাম্বার থেকে কল
               এসেছিল সেই কার্ডে চাপ দিলে সেই নাম্বারের পেজে যেতে হবে…
               বারবার চাপা সত্ত্বেও কেন যাচ্ছে না"*)

               ❗ আসল কারণ — **আমারই বাদ পড়া**: এই নোটিফিকেশনে কখনো
               `setContentIntent(...)` বসানোই হয়নি। তাই নিচের বোতাম দুটো
               কাজ করত, কিন্তু **কার্ডের গায়ে চাপ দিলে কিচ্ছু হত না**।

               ✅ এখন চাপ দিলে ওই নম্বরের নিজের পাতা খোলে:
                 · RMP হলে → RMP-র তালিকা, ওই নম্বর খোঁজা অবস্থায়
                   (`searchMobile` — পর্দাটা আগে থেকেই এই extra পড়ে)
                 · রোগী হলে → তার Full Journey (`PatientTimelineActivity`,
                   `mobile` extra — এটাও আগে থেকেই আছে)
                 · কোথাও সেভ না থাকলে → নতুন Enquiry ফর্ম, নম্বর ভরা
                   (নিচের "➕ New Enquiry" বোতামের হুবহু একই কাজ)
               ⛔ নতুন কোনো পর্দা বা extra বানানো হয়নি — সবই প্রমাণিত।
               ⛔ নিচের তিনটে বোতাম · লেখা · রং — কিছুই বদলায়নি। */
            val tapIntent = when {
                match == null -> Intent(ctx, EnquiryActivity::class.java)
                    .putExtra("prefillMobile", number)
                match.isRmp -> Intent(ctx, DoctorVisitActivity::class.java)
                    .putExtra("searchMobile", number)
                    .putExtra("searchBranch", match.branch)
                else -> Intent(ctx, PatientTimelineActivity::class.java)
                    .putExtra("mobile", number)
                    .putExtra("preName", match.name)
                    .putExtra("preBranch", match.branch)
                    .putExtra("prePatientId", match.patientId)
            }.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            builder.setContentIntent(pendingActivity(ctx, 4, tapIntent))

            // ✕ Dismiss — ভুল নম্বর/দরকার নেই হলে সরাসরি সরানোর জন্য।
            val dismissIntent = Intent(ctx, CallDismissReceiver::class.java)
            val dismissPi = android.app.PendingIntent.getBroadcast(
                ctx, 3, dismissIntent,
                (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                else android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            )
            builder.addAction(0, "✕ Dismiss", dismissPi)

            // 📝 Add/Edit Remark — কল চলাকালীন ও কল-শেষে দুটোতেই (TK-নির্দেশ ৩)।
            val remarkIntent = Intent(ctx, CallRemarkActivity::class.java)
                .putExtra("mobile", number).putExtra("direction", direction)
                .putExtra("patientId", match?.patientId.orEmpty())
                .putExtra("patientName", match?.name.orEmpty())
                .putExtra("branch", match?.branch.orEmpty())
                .putExtra("calledAt", calledAt)
                // 🟢🔒 V634 (২৪.০৮.২০২৬) — followups সারির আসল id, যাতে
                // রিমার্কস সেভের সময় Wifi-সিগন্যাল আইকনের callCount ঠিকভাবে
                // বাড়ে। RMP মিললে (match.isRmp) এটা ইচ্ছাকৃতভাবে ফাঁকা —
                // RMP-দের নিজস্ব followups সারি থাকে না।
                .putExtra("followupId", if (match != null && !match.isRmp) match.id else "")
                // 🩺🔒 V836 (২৯.০৮.২০২৬, TK-নির্দেশ) — RMP মিললে তার নিজের
                // `doctor_visits` সারির id, যাতে লেখা রিমার্ক **RMP সেকশনেই**
                // বসে। ⛔ RMP না হলে ফাঁকা, তাই পুরনো আচরণ হুবহু অক্ষত।
                .putExtra("rmpId", if (match != null && match.isRmp) match.id else "")
                // 🟢🔒🔒 V637 (২৪.০৮.২০২৬, TK-রিপোর্ট, ছবিসহ — "প্রথমবার
                // 'কাল আসবে' লিখেছিলাম, পরেরবার কল এলে সেটা দেখাচ্ছে না
                // কেন") — আসল কারণ: এই বোতাম আগে কখনো আগের রিমার্কস আনতই
                // না — `DialerRepository.fetchLatestRemark()` আগে থেকেই
                // ছিল (Dialer-এর নিজের তালিকায় ব্যবহৃত), কিন্তু এই
                // কল-শনাক্তকরণ ফ্লো থেকে কখনো ডাকা হত না। এখন `onRinging()`/
                // `notifyOutgoingDialed()`-এ একই ব্যাকগ্রাউন্ড থ্রেডে আনা
                // আগের রিমার্কস এখানে পাঠানো হয় — `CallRemarkActivity`
                // আগে থেকেই এই extra পড়ে বাক্সে বসায় (existingRemark),
                // শুধু এতদিন কেউ পাঠাতই না।
                .putExtra("existingRemark", existingRemark)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val remarkPi = pendingActivity(ctx, 1, remarkIntent)
            builder.addAction(0, "📝 Add Remark", remarkPi)

            if (match == null) {
                // 🔴 TK-নির্দেশ ১ — সেভ না থাকলে সরাসরি Enquiry ফর্মে (নম্বর ভরা)।
                val enquiryIntent = Intent(ctx, EnquiryActivity::class.java)
                    .putExtra("prefillMobile", number)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val enquiryPi = pendingActivity(ctx, 2, enquiryIntent)
                builder.addAction(0, "➕ New Enquiry", enquiryPi)
            }

            nm.notify(NOTIF_ID, builder.build())
        } catch (_: Throwable) {
            // নোটিফিকেশন ব্যর্থ হলেও কল/অ্যাপ কখনো আটকাবে না।
        }
    }

    private fun pendingActivity(ctx: Context, code: Int, intent: Intent): PendingIntent {
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(ctx, code, intent, flags)
    }
}
