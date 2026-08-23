package com.tkbiswas.pilesclinic.native

import android.app.Activity
import android.app.Dialog
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 🔒🔒 Kishanganj Staff / Branch-number login-এ বাংলা লেখা একেবারে বন্ধ রাখার ব্যবস্থা।
 * ==========================================================================
 * **খাতার সারি B158** · TK-এর নির্দেশ (30.07.2026 সকাল ১১.১০):
 *
 * > *"কিশানগঞ্জের স্টাফ KNE-KISHAN5, মোবাইল +916207841890 — সে একদম বাংলা
 * >  বোঝে না এবং পড়তেও জানে না। সুতরাং তার বক্তব্য: স্যার আমি লগইন করার পরে
 * >  আমার ফোনে যেন কোন বাংলা ফন্ট না থাকে। প্রয়োজনে শুধু ইংলিশ, অথবা তার
 * >  সাথে হিন্দি — কিন্তু বাংলা একদম যেন না থাকে।"*
 *
 * **কীভাবে কাজ করে (তিন স্তরের সুরক্ষা):**
 *  ১. **Branch rule** — Kishanganj branch-এর Staff account লগইন করলেই চালু হয়;
 *     KNE-BRANCH (branch-number login)-ও Staff account, তাই একই নিয়মে পড়ে।
 *     পুরনো KNE-KISHAN5 mobile/code fallback নিরাপত্তা হিসেবে রাখা আছে।
 *     Doctor/Master/Field এবং অন্য branch-এর Staff-এর জন্য কিছুই বদলায় না।
 *  ২. **অনুবাদ** — পর্দার প্রতিটা বাংলা টুকরোর ইংরেজি নিচের তালিকায় আছে।
 *     লম্বা টুকরো আগে বদলানো হয়, তাই ছোট টুকরো বড়টার ভিতরে ঢুকে গোলমাল করে না।
 *  ৩. **শেষ জাল** — বদলানোর পরেও যদি একটাও বাংলা অক্ষর থেকে যায় (যেমন কেউ
 *     ভবিষ্যতে নতুন বাংলা লেখা যোগ করল), সেটা **মুছে দেওয়া হয়** — অর্থাৎ ওই
 *     স্টাফের পর্দায় বাংলা অক্ষর কোনোভাবেই আসতে পারে না। পাহারাদারের যাচাই
 *     ৯.১৪ আবার নিশ্চিত করে যে নতুন কোনো বাংলা লেখা অনুবাদ ছাড়া না থাকে।
 *
 * ⛔ **রোগীর কাছে যাওয়া লেখা ইচ্ছে করে ছোঁয়া হয়নি** — `PatientMessage.kt`,
 *    `DoctorMessage.kt`, `ClinicalRepository.kt` (ডায়েট/পরামর্শ), ছাপার লেখা।
 *    কারণ ওগুলো **রোগীর নিজের ভাষা**, TK-এর লক করা তিন-ভাষার নিয়ম (সারি B17)।
 *    ওই লেখা স্টাফের পর্দার অংশ নয় — WhatsApp/SMS-এ রোগীর কাছে যায়।
 * ⛔ **কোনো ডিজাইন · টাকার হিসাব · ব্রাঞ্চের নিয়ম · ডেটাবেস ছোঁয়া হয়নি।**
 */
object NoBengali {

    /** যাদের ফোনে বাংলা একেবারে বন্ধ — মোবাইলের শেষ ১০ অঙ্ক।
     *  ⛔ TK-এর অনুমতি ছাড়া এই তালিকা বদলানো যাবে না; নতুন কেউ যোগ করতে হলে
     *     TK বলবেন, তখন শুধু একটা নম্বর যোগ হবে। */
    private val NO_BENGALI_MOBILES = setOf(
        "6207841890"   // KNE-KISHAN5 (Kishanganj)
    )

    /** অ্যাকাউন্টের কোড ধরেও মেলানো হয় — নম্বর কখনো বদলে গেলেও যেন কাজ চলে। */
    private val NO_BENGALI_CODES = setOf(
        "KNE-KISHAN5"
    )

    @Volatile private var activeCache: Boolean = false

    /** কোন পর্দা/পপ-আপে পাহারা বসানো হয়ে গেছে — একবারই বসে।
     *  `WeakHashMap` বলে বন্ধ হয়ে যাওয়া পর্দা নিজে থেকেই ছেড়ে যায় (মেমরি ধরে
     *  রাখে না), আর কারও বসানো view-tag-এ হাত পড়ে না। */
    private val hooked = java.util.WeakHashMap<View, Boolean>()

    /** লগইন/পর্দা খোলার সময় ডাকা হয় — কে লগইন করা আছে দেখে নেয়। */
    fun refresh(context: Context?) {
        activeCache = try {
            val u = context?.let { NativeSession.current(it) }
            if (u == null) false else {
                val ten = u.mobile.filter { it.isDigit() }.takeLast(10)
                val account = try { StaffDirectory.findAccount(u.mobile) } catch (_: Throwable) { null }
                val code = account?.name ?: ""

                // 🔒 V451 (19.08.2026, TK-approved): Kishanganj-এর বাংলা-বন্ধ
                // নিয়ম আর কোনো নির্দিষ্ট ব্যক্তির নম্বরের ওপর নির্ভর করবে না।
                // Kishanganj branch-এর যেকোনো Staff account — এর মধ্যে KNE-BRANCH
                // (branch-number login)-ও পড়ে — English-only display পাবে।
                // ⛔ Doctor/Master/Field বা অন্য branch-এর Staff-এর display বদলাবে না।
                val role = (account?.role ?: u.role).trim()
                val branch = (account?.branch ?: u.branch).trim()
                val isKishanganjStaff = role.equals("staff", ignoreCase = true) &&
                    branch.equals("Kishanganj", ignoreCase = true)

                isKishanganjStaff ||
                    NO_BENGALI_MOBILES.contains(ten) ||
                    NO_BENGALI_CODES.contains(code.trim().uppercase())
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** এখন যিনি লগইন করা, তাঁর জন্য বাংলা বন্ধ কি না। */
    fun active(): Boolean = activeCache

    // 🔴 B413 (04.08.2026, TK-নির্দেশে "সম্পূর্ণ প্রজেক্ট স্ক্রিন-ধরে-স্ক্রিন
    // যাচাই করুন" — Login স্ক্রিন অডিটে ধরা পড়েছে): `active()`/`refresh()`
    // দুটোই `NativeSession.current()` (মানে **লগইন হয়ে যাওয়ার পরের**
    // সেশন) লাগে। কিন্তু `LoginActivity`-র "Forgot Password" বার্তা
    // **লগইন হওয়ার আগেই** (পাসওয়ার্ড দেওয়ার আগে) দেখায় — তখন কোনো সেশনই
    // নেই, তাই KNE-KISHAN5 (মোবাইল নম্বর টাইপ করার পরেও) ওই বার্তায় কাঁচা
    // বাংলা দেখতেন, B158-এর লক করা নিয়ম ("তার ফোনে যেন কোনো বাংলা ফন্ট না
    // থাকে") ভেঙে যেত। এই নতুন ফাংশন **সেশন ছাড়াই**, সরাসরি মোবাইল নম্বর
    // দিয়ে যাচাই করে — Login স্ক্রিনের মতো pre-login জায়গায় ব্যবহারের জন্য।
    fun isNoBengaliMobile(mobile: String): Boolean {
        val ten = mobile.filter { it.isDigit() }.takeLast(10)
        val account = try { StaffDirectory.findAccount(mobile) } catch (_: Throwable) { null }

        // Login-এর আগেও branch জানা যায় StaffDirectory থেকে। তাই নতুন/অন্য
        // Kishanganj Staff ও KNE-BRANCH Forgot Password-এ বাংলা দেখবে না।
        if (account != null &&
            account.role.equals("staff", ignoreCase = true) &&
            account.branch.equals("Kishanganj", ignoreCase = true)
        ) return true

        if (NO_BENGALI_MOBILES.contains(ten)) return true
        val code = account?.name ?: ""
        return NO_BENGALI_CODES.contains(code.trim().uppercase())
    }

    private fun hasBengali(s: CharSequence): Boolean {
        for (ch in s) {
            if (ch.code in 0x0980..0x09FF) return true
        }
        return false
    }

    /** সবচেয়ে লম্বা টুকরো আগে — তাই একবারই সাজানো হয়। */
    private val ordered: List<Pair<String, String>> by lazy {
        MAP.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
    }

    /** সবচেয়ে লম্বা টুকরো আগে — ইংরেজির তালিকার মতোই একই নিয়ম। */
    private val hindiOrdered: List<Pair<String, String>> by lazy {
        HINDI.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
    }

    /**
     * একটা লেখাকে বাংলা-মুক্ত করে দেয়। বাংলা বন্ধ না থাকলে লেখাটা **হুবহু
     * অপরিবর্তিত** ফেরত যায় — তাই বাকি সব স্টাফের কিছুই বদলায় না।
     */
    fun fix(text: CharSequence?): CharSequence? {
        if (text == null) return null
        if (!activeCache) return text
        if (!hasBengali(text)) return text
        WHOLE[text.toString().trim()]?.let { return it }
        // 🔒 শুরুর ও শেষের ফাঁকা/নতুন-লাইন হুবহু রেখে দিতে হয় (30.07.2026-এ
        // নিজের কাজ আবার যাচাই করতে গিয়ে ধরা পড়েছে): অনেক বার্তায় লেখাটা
        // `"\n\n⏰ তারিখ: …"` ধাঁচে জোড়া হয়। শুধু `trim()` করলে ওই নতুন-লাইনটা
        // চলে যেত আর দুটো লাইন এক লাইনে জুড়ে যেত — চোখে দেখতে খারাপ লাগত।
        val whole = text.toString()
        val lead = whole.takeWhile { it.isWhitespace() }
        val tail = whole.takeLastWhile { it.isWhitespace() }
        var s = whole.trim()
        // 🔵 V575 (TK-নির্দেশ, ২৩.০৮.২০২৬): *"কিষানগঞ্জ all Staff এর কাছে যেন
        //    হিন্দি তে থাকে … আমি তো আপনাকে ডাক্তার চেকাপে হিন্দিতে রাখতে বললাম"*।
        //    তাই **আগে** হিন্দির তালিকা বসে, তারপর আগের ইংরেজির তালিকা।
        //    ⛔ হিন্দির তালিকায় শুধু ডাক্তার-চেক-আপের নিজস্ব লেখা আছে, তাই
        //       অন্য পর্দার একটাও লেখা বদলায় না — আগের মতোই ইংরেজি থাকে।
        for ((bn, hi) in hindiOrdered) {
            if (s.contains(bn)) s = s.replace(bn, hi)
        }
        for ((bn, en) in ordered) {
            if (s.contains(bn)) s = s.replace(bn, en)
        }
        if (hasBengali(s)) {
            // 🔒 শেষ জাল — অনুবাদে না থাকা বাংলা অক্ষর মুছে ফেলা হয়।
            val sb = StringBuilder(s.length)
            for (ch in s) {
                if (ch.code in 0x0980..0x09FF) continue
                sb.append(ch)
            }
            s = sb.toString()
        }
        // দুটো ফাঁকা জায়গা বা ঝুলে থাকা যোগচিহ্ন পরিষ্কার করা
        // বাংলা দাঁড়ি (।) বাংলা অক্ষরের ঘরে পড়ে না, তাই আলাদা করে সরাতে হয়
        s = s.replace("\u0964", ".")
        while (s.contains("  ")) s = s.replace("  ", " ")
        s = s.replace(" ·  ", " · ").replace("· ·", "·").replace(" .", ".").trim()
        while (s.endsWith("—") || s.endsWith("·") || s.endsWith(":")) s = s.dropLast(1).trim()
        return lead + s + tail
    }

    /** String দরকার হলে (যেমন Toast-এ) — একই কাজ। */
    fun s(text: String?): String = if (text == null) "" else (fix(text)?.toString() ?: "")

    /** একটা ভিউ-গাছের সব লেখা বাংলা-মুক্ত করা। */
    fun sweep(root: View?) {
        if (!activeCache || root == null) return
        try {
            walk(root)
        } catch (_: Throwable) { }
    }

    private fun walk(v: View) {
        if (v is TextView) {
            // ⛔⛔ **লেখার ঘরে (EditText) কখনো হাত দেওয়া হয় না।**
            // কারণ (নিজের কাজ আবার যাচাই করতে গিয়ে ধরা পড়েছে, 30.07.2026):
            // পুরনো কোনো রেকর্ডের Remark/নাম যদি বাংলায় লেখা থাকে, আর স্টাফ
            // সেটা খুলে Save চাপেন, তাহলে ঘরের বদলে যাওয়া লেখাটাই ডেটাবেসে
            // চলে যেত — অর্থাৎ **রোগীর আসল বাংলা তথ্য চিরতরে নষ্ট** হয়ে যেত।
            // তাই ইনপুট ঘরের **text ছোঁয়া হয় না**; শুধু hint (যেটা কোথাও
            // সেভ হয় না) বাংলা-মুক্ত করা হয়।
            val isInput = v is android.widget.EditText
            if (!isInput) {
                val t = v.text
                if (t != null && hasBengali(t)) {
                    val fixed = fix(t)
                    if (fixed != null && fixed.toString() != t.toString()) v.text = fixed
                }
            }
            val h = v.hint
            if (h != null && hasBengali(h)) {
                val fixed = fix(h)
                if (fixed != null && fixed.toString() != h.toString()) v.hint = fixed
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
    }

    /** পর্দা (Activity) — প্রতিটা layout-এর পরে আবার মিলিয়ে দেখা হয়, তাই পরে
     *  বসানো লেখাও ধরা পড়ে (তালিকার সারি, টোস্ট ছাড়া সবকিছু)। */
    fun install(activity: Activity?) {
        if (!activeCache || activity == null) return
        try {
            val decor = activity.window?.decorView ?: return
            if (hooked.containsKey(decor)) {
                sweep(decor); return
            }
            hooked[decor] = true
            decor.viewTreeObserver.addOnGlobalLayoutListener { sweep(decor) }
            sweep(decor)
        } catch (_: Throwable) { }
    }

    /** পপ-আপ (Dialog) — এদের নিজের আলাদা উইন্ডো, তাই আলাদা করে দেখা হয়। */
    fun installDialog(dialog: Dialog?) {
        if (!activeCache || dialog == null) return
        try {
            val decor = dialog.window?.decorView ?: return
            if (!hooked.containsKey(decor)) {
                hooked[decor] = true
                decor.viewTreeObserver.addOnGlobalLayoutListener { sweep(decor) }
            }
            sweep(decor)
        } catch (_: Throwable) { }
    }

    /** পুরো অ্যাপে একবারই বসানো হয় (`PilesClinicApplication`) — তাই ৩৪টা
     *  পর্দার একটাতেও আলাদা করে কিছু লিখতে হয় না, আর ভবিষ্যতের নতুন পর্দাও
     *  নিজে থেকেই ঢেকে যায়। */
    fun hookApp(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) { refresh(a); install(a) }
            override fun onActivityStarted(a: Activity) { refresh(a); install(a) }
            override fun onActivityResumed(a: Activity) { refresh(a); install(a) }
            override fun onActivityPaused(a: Activity) { }
            override fun onActivityStopped(a: Activity) { }
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) { }
            override fun onActivityDestroyed(a: Activity) { }
        })
    }


    /** 🔒 কয়েকটা লেখার হুবহু ইংরেজি — টুকরো ধরে বদলালে বাক্যের ক্রম উল্টো
     *  শোনাত (যেমন "Name দিন" → "Name day"), তাই এগুলো **আগে** মেলানো হয়।
     *  ⛔ TK-এর অনুমতি ছাড়া বদলানো যাবে না। */
    private val WHOLE: Map<String, String> = mapOf(
        // 🔒 B568 (08.08.2026): Patient Timeline-এর নীল-কার্ড Note-এর সেকশন হেডার।
        // শুধু বাংলা-বন্ধ (Kishanganj) Staff-এর display translation; data/logic ছোঁয়া হয়নি।
        "📌 রিমার্ক" to "📌 Remark",
        "🧾 স্ট্যাটাস" to "🧾 Status",
        "🩺 ডাক্তার চেক-আপ" to "🩺 Doctor Check-up",
        "💰 পেমেন্ট" to "💰 Payment",
        // 🔒 B527 (07.08.2026, TK-এর অনুমোদন): Guard 9.14-এ ধরা পড়া
        // 07.08-এর নতুন failure/save বার্তাগুলোর English equivalent। শুধু
        // বাংলা-বন্ধ Staff-এর display translation; save/data/payment logic ছোঁয়া হয়নি।
        "লোড করা গেল না — একটু পরে আবার দেখুন" to "Could not load — please try again shortly",
        "⚠️ আগের Paid টাকা এখন লোড হয়নি — Paid/Due ভুল দেখাতে পারে। একটু পরে আবার খুলে দেখুন।" to "⚠️ Previous Paid amount could not be loaded — Paid/Due may be incorrect. Please reopen shortly.",
        "সেভ হয়নি — একটু পরে আবার Submit চাপুন" to "Not saved — please tap Submit again shortly",
        // 🆕 B438 (05.08.2026) — Work Notebook-এর নতুন multi-option quick-mark
        // ডায়ালগ (IN/OUT TIME রিমাইন্ডার থেকে খোলে); KNE-KISHAN5-এর ফোনে
        // ইংরেজি। ⛔ ইমোজি-প্রিফিক্স অক্ষত রাখা হলো (কোডে .startsWith() দিয়ে
        // চেনা হয়)।
        "আপনি কি চেম্বারে পৌঁছেছেন?" to "Have you reached the chamber?",
        "আপনি বাড়ি যেতে চান না" to "Don't you want to go home?",
        "✅ হ্যাঁ, এখনই IN TIME মার্ক করুন" to "✅ Yes, mark IN TIME now",
        "⏰ এখনো আসিনি — কখন আসব বলছি" to "⏰ Not there yet — tell when I'll arrive",
        // 🆕 (06.08.2026, B519) — চেম্বার-রিমাইন্ডার পুশ-নোটিফিকেশনের নতুন
        // ছোট বাংলা লেখা।
        "⏰ চেম্বারে পৌঁছেছেন?" to "⏰ Have you reached the chamber?",
        "চাপুন — IN TIME বসান।" to "Tap — mark IN TIME.",
        "⏰ চেম্বার থেকে বেরিয়েছেন?" to "⏰ Have you left the chamber?",
        "চাপুন — OUT TIME বসান।" to "Tap — mark OUT TIME.",
        // 🆕 (06.08.2026, B520) — Notes বক্সের নতুন হিন্ট-টেক্সট।
        "আজকের সারাদিনে কি কি কাজ করেছেন লিখুন (ঐচ্ছিক)..." to "What did you do today? (optional)",
        // 🆕 (06.08.2026, B523) — My Reports কার্ড-ডিজাইনের বাংলা লেখা
        // (ইতিমধ্যে isKishanganjStaff দিয়ে কোডে branch করা আছে, এখানে
        // পাহারাদারের জন্য বাড়তি নিরাপত্তা)।
        " — দৈনিক রিপোর্ট" to " — Daily Report",
        "— দৈনিক রিপোর্ট" to "— Daily Report",
        "N বার আপডেট হয়েছে (সর্বশেষ পাঠানো)" to "Updated N times (latest sent)",
        "একবার পাঠানো হয়েছে" to "Sent once",
        "জমা হয়েছে" to "Submitted",
        "🏖️ আজকে আমার ছুটি" to "🏖️ Today is my leave",
        "এই ফোনে ব্রাঞ্চের নম্বর কোন SIM?" to "Which SIM is the branch number on this phone?",
        "কতদিন থেকে সমস্যা" to "How long has the problem been there",
        "যেমন: 6 মাস / 2 বছর" to "e.g. 6 months / 2 years",
        "আগে কোথাও চিকিৎসা নিয়েছিলেন কিনা" to "Any treatment taken elsewhere before",
        "কোথায়/কী চিকিৎসা নিয়েছিলেন লিখুন, না নিয়ে থাকলে ফাঁকা রাখুন" to "Write where/what treatment was taken; leave blank if none",
        "✅ হ্যাঁ, এখনই OUT TIME মার্ক করুন" to "✅ Yes, mark OUT TIME now",
        "⏰ এখনো যাব না — কখন যাব বলছি" to "⏰ Not leaving yet — tell when I'll leave",
        "কিছু ঘর ফাঁকা আছে" to "Some fields are empty",
        // 🆕 (06.08.2026, B512) — প্রফেশনাল কার্ড-ডিজাইনে বুলেট (•) সরানো।
        "⚠️ কিছু ঘর ফাঁকা আছে" to "⚠️ Some fields are empty",
        "আজকে কতগুলি ফোন রিসিভ করেছেন — লেখা হয়নি" to "Today's calls received — not filled in",
        "Notes — কিছু লেখা হয়নি" to "Notes — nothing written",
        "অথবা নিজের কারণ লিখুন" to "Or write your own reason",
        "ভরে OUT TIME বসান" to "Fill & mark OUT TIME",
        "এড়িয়ে যান" to "Skip",
        "• আজকে কতগুলি ফোন রিসিভ করেছেন — লেখা হয়নি" to "• Today's calls received — not filled in",
        "• Notes — কিছু লেখা হয়নি" to "• Notes — nothing written",
        // 🆕 B419 (04.08.2026) — Chamber Reopen request feature-এর স্ট্যাটিক
        // (কোনো ভ্যারিয়েবল-বসানো নেই) বাক্যগুলো; KNE-KISHAN5-এর ফোনে ইংরেজি।
        "হ্যাঁ, পাঠান" to "Yes, Send",
        "অনুরোধ পাঠানো হয়েছে — Master অনুমোদন দিলে দিনটা আবার খুলবে" to "Request sent — the day will reopen once Master approves",
        "পাঠানো গেল না — নেট চেক করে আবার চেষ্টা করুন" to "Could not send — check your network and try again",
        "না" to "No",
        "এই অনুরোধ থেকে Branch/Date চেনা গেল না" to "Could not read Branch/Date from this request",
        // 🆕 B607 (10.08.2026, TK-নির্দেশ) — কেউ না এলে চেম্বার বন্ধের নিশ্চিতকরণ।
        "আজ কেউ আসেননি (Arrived 0)। তবুও চেম্বার বন্ধ করবেন?" to "Nobody arrived today (Arrived 0). Still close the chamber?",
        "হ্যাঁ, বন্ধ করুন" to "Yes, Close",
        // 🆕 B608 (10.08.2026, TK-নির্দেশ) — ভুল করে ছুটি দিলে বাতিলের নিশ্চিতকরণ।
        "ভুল করে ছুটি দিয়েছিলেন? ছুটি বাতিল করে আজকের হাজিরা আবার চালু করবেন?" to "Marked leave by mistake? Cancel the leave and resume today's attendance?",
        "হ্যাঁ, বাতিল করুন" to "Yes, Cancel",
        // 🆕 B615 (11.08.2026, TK-নির্দেশ) — দুপুর ১২টা পার হলে IN TIME বন্ধ।
        "আজকের IN TIME-এর সময় শেষ, না এলে ছুটি দিন" to "IN TIME window is over for today. Mark leave if absent.",
        // 🆕 B618 (11.08.2026, TK-নির্দেশ) — ছুটির আবেদন (আজ/অগ্রিম + মঞ্জুরি)।
        "🏖️ ছুটির আবেদন" to "🏖️ Leave Request",
        "কোন তারিখে ছুটি" to "Which date for leave",
        "ছুটির আবেদন করুন" to "Apply for Leave",
        "ছুটির অনুরোধ পাঠানো হয়েছে — Pending" to "Leave request sent — Pending",
        // 🆕 B419 (04.08.2026) — ChamberAttendanceActivity-র নতুন "Reopen"
        // অনুরোধ-বোতাম; KNE-KISHAN5-এর ফোনে ইংরেজি।
        "🔓 Reopen-এর অনুরোধ পাঠান (Master-এর অনুমতি লাগবে)" to "🔓 Request Reopen (needs Master's approval)",
        // 🔴 V425 (TK-নির্দেশ ১৭.০৮.২০২৬: *"আমি মাস্টার আবার আমাকে কেন অনুমতি নিতে হবে"*)
        "🔓 চেম্বার আবার খুলুন" to "🔓 Reopen Chamber",
        "এই দিনের চেম্বার আবার খুলবেন?" to "Reopen this day's chamber?",
        "হ্যাঁ, খুলুন" to "Yes, reopen",
        "চেম্বার আবার খোলা হলো" to "Chamber reopened",
        "খোলা গেল না — নেট চেক করে আবার চেষ্টা করুন" to "Could not reopen — check the network and try again",
        // 🔴 B407 (04.08.2026) — WorkNotebookActivity.kt-এর "Outside Calls
        // Today" লেবেল এখন সাধারণত বাংলা, কিশানগঞ্জ ব্রাঞ্চে হিন্দি (কোডেই
        // branch-চেক দিয়ে ঠিক হয়) — এই এন্ট্রি শুধু বাড়তি সুরক্ষা-জাল।
        "আজকে কতগুলি ফোন আপনি রিসিভ করেছেন" to "आज आपने कितने फोन रिसीव किए",
        // 🔴 B405 (04.08.2026) — WorkNotebookActivity.kt-এর "Mark as Leave"
        // বোতামের পাশে নতুন বাংলা ব্যাখ্যা-লাইন; KNE-KISHAN5-এর ফোনে ইংরেজি।
        "(আজকে আমার ছুটি — leave নেওয়ার জন্য চাপুন)" to "(Tap to mark today as leave)",
        // 🔒 ভাষা-বাছাইয়ের বোতাম: বাংলা বন্ধ থাকা স্টাফের কাছে যেন
        //    "English (Bengali)" গোছের উদ্ভট লেখা না দেখায় (30.07.2026-এ
        //    নিজের কাজ আবার যাচাই করতে গিয়ে ধরা পড়েছে)।
        "বাংলা  (Bengali)" to "Bengali",
        "বাংলা (Bengali)" to "Bengali",
        "Name দিন" to "Enter Name",
        "Remarks দিন" to "Enter Remarks",
        "Title দিন" to "Enter Title",
        "Message দিন" to "Enter Message",
        "Product দিন" to "Enter Product",
        "Doctor Name দিন" to "Enter Doctor Name",
        "Doctor নাম দিন" to "Enter Doctor Name",
        "Patient নাম দিন" to "Enter Patient Name",
        "সঠিক Bill দিন" to "Enter a correct Bill",
        "সঠিক Deposit দিন" to "Enter a correct Deposit",
        "সঠিক Amount দিন" to "Enter a correct Amount",
        "সঠিক 10 ডিজিট মোবাইল দিন" to "Enter a correct 10-digit mobile number",
        "Branch বাছুন" to "Select Branch",
        "তারিখ বাছুন" to "Pick a date",
        "Registration করুন" to "Do Registration",
        "এখন পরের Follow-up Call তারিখ দিন" to "Now enter the next Follow-up Call date",
        "পাইলস (অর্শ)" to "Piles",
        "ভগন্দর (ফিস্টুলা)" to "Fistula",
        "হাইড্রোসিল (একশিরা)" to "Hydrocele",
        "Approved — Deleted (Trash Bin-এ আছে)" to "Approved — Deleted (in Trash Bin)",
        "📋 সম্পূর্ণ ইতিহাস (Full History)" to "📋 Full History",
        "কোন পেমেন্ট Edit করবেন?" to "Which payment do you want to Edit?",
        "Complete Approve করবেন?" to "Approve Complete?",
        "Complete করার অনুরোধ পাঠাবেন?" to "Send the request to Complete?",
        "Delete request পাঠাবেন?" to "Send the Delete request?",
        "উপরে ব্রাঞ্চ বাছাই করুন RMP লিস্ট দেখতে" to "Select a branch above to see the RMP list",
        "🌿 উপসর্গ · Symptoms" to "🌿 Symptoms",
        "ℹ️ কারণ · Causes" to "ℹ️ Causes",
        "🛡️ সেরা চিকিৎসা · Best Treatment" to "🛡️ Best Treatment",
        "রোগের বিস্তারিত · Disease Info" to "Disease Info",
        "বিস্তারিত দেখুন › Learn more" to "Learn more ›",
        "এসেছেন Arrived" to "Arrived",
        "আসার কথা Expected" to "Expected",
        "আজকের তথ্য এখন আনা গেল না। OUT TIME নিরাপদে বসাতে আজকের তথ্যটা দরকার (নইলে আগের IN TIME মুছে যেতে পারত)। একবার আবার চেষ্টা করুন।" to "Today's information could not be loaded. It is required to save OUT TIME safely; otherwise the previous IN TIME could be lost. Please try again.",
        "থানা" to "PS",
        // ══════════════════════════════════════════════════════════════════
        // 🔴🔴🔒 V512 (২১.০৮.২০২৬) — **আগে এখানে লেখা ছিল `"বাংলা" to "English"`,
        //    সেটা ভুল ছিল।**
        //
        // ─── কারণ (প্রমাণ করে দেখা, আন্দাজে নয়) ──────────────────────────
        //   `res/layout/activity_disease_detail.xml`-এর ৪৩ নম্বর লাইনে ভাষা
        //   বাছার তিনটে বোতাম পাশাপাশি: **বাংলা · हिन्दी · English**।
        //   প্রথম বোতামটার লেখা হুবহু "বাংলা", তাই WHOLE-এর এই সারিটাই
        //   মিলত ⇒ বাংলা-বন্ধ স্টাফের পর্দায় বসত **English · हिन्दी ·
        //   English** — একই নামে দুটো বোতাম, আর "English"-এ চাপ দিলে
        //   বাংলা লেখা খুলত। ভাষা বাছাই কার্যত ভেঙে পড়েছিল।
        //   (একই চাবি MAP-এর ১০১০ নম্বর লাইনে ঠিকই "Bengali" ছিল — অর্থাৎ
        //    দুই জায়গায় দুই মানে, তাই লেখাটা কোথায় বসেছে তার উপর ফল
        //    বদলে যেত।)
        //
        // ⇒ এখন দুই জায়গাতেই এক — "Bengali"। বোতাম তিনটে হয়:
        //   **Bengali · हिन्दी · English**।
        // ⛔ কোনো সারি মোছা হয়নি (TK-এর নিয়ম), শুধু ভুল ইংরেজিটা ঠিক হলো।
        // ⛔ এটা **শুধু দেখানোর লেখা** — কোন ভাষার লেখা দেখাবে সেই কাজটা
        //    বোতামের `id` (`langBn`) ধরে হয়, লেখা ধরে নয়। তাই ভিতরের
        //    কাজে এক অক্ষরও বদলায়নি।
        // ⛔ বাংলা-বন্ধ **ছাড়া** সব স্টাফের কিছুই বদলায়নি (`fix()` তখন
        //    লেখাটা হুবহু ফেরত দেয়)।
        // ══════════════════════════════════════════════════════════════════
        "বাংলা" to "Bengali",
        // 🔴 V512 — ভাষা বাছার তালিকায় (DoctorVisitActivity) লেখাটা
        //    "বাংলা  (Bengali)" ধাঁচে থাকে। টুকরো ধরে বদলালে হতো
        //    "Bengali  (Bengali)" — তাই হুবহু-মিলের সারি। ওয়েব অ্যাপে
        //    (app.js — WLV1_NOBN_WHOLE) ঠিক এই দুটো সারিই আগে থেকেই আছে,
        //    এখন Android-ও এক হলো।
        "বাংলা  (Bengali)" to "Bengali",
        "বাংলা (Bengali)" to "Bengali",
        // 🔴 V512 — clinical/PrescriptionActivity.kt-এর দুটো পপ-আপের লেখা।
        //    এগুলো `native/`+`modules/`-এর বাইরে বলে পাহারাদার ৯.১৪ কখনো
        //    দেখেনি, আর পপ-আপ দুটো PremiumAlert দিয়ে আঁকা হয় না বলে
        //    বাংলা-বন্ধ স্টাফের পর্দাতেও বাংলাতেই থাকত।
        "এই রোগীর আজ একটি Prescription সেভ হয়েছে। আপনি কি আবার Prescription করতে চান?" to "A Prescription has already been saved for this patient today. Do you want to make another one?",
        "Prescription যাচাই করা যায়নি" to "Prescription could not be verified",
        "ইন্টারনেট সংযোগ পরীক্ষা করে আবার Save করুন। কোনো Prescription সেভ হয়নি।" to "Check the internet connection and Save again. No Prescription was saved.",
    )

    /** পর্দার বাংলা লেখা → ইংরেজি।
     *  ⛔ TK-এর অনুমতি ছাড়া কোনো সারি মোছা যাবে না; নতুন বাংলা লেখা যোগ হলে
     *     এখানে তার ইংরেজিও যোগ করতে হবে (পাহারাদারের যাচাই ৯.১৪ ধরবে)। */
    private val MAP: Map<String, String> = mapOf(

        // 🆕🔒 V496 (২১.০৮.২০২৬) — হাজিরা · আঙুলের ছাপ · অবস্থান · সেশনের নতুন লেখা।
        // খাতার সারি B158: বাংলা-বন্ধ স্টাফের পর্দায় যেন একটাও বাংলা না থাকে,
        // তাই প্রতিটা নতুন লেখার ইংরেজি এখানে যোগ করা হলো।
        // 🆕 V496 সংশোধন (TK §২ ও §৩, ২১.০৮.২০২৬) — বেতন শুধু staff-এর।
        "এই অ্যাকাউন্টের জন্য বেতনের হিসাব নেই। বেতন শুধু স্টাফদের জন্য।" to "This account has no salary record. Salary is only for staff.",
        "এই ফোনে আঙুলের ছাপের ব্যবস্থা নেই। মাস্টারকে জানান।" to "This phone has no fingerprint sensor. Please tell the master.",
        "আঙুলের ছাপের যন্ত্রটা এই মুহূর্তে কাজ করছে না। একটু পরে আবার চেষ্টা করুন।" to "The fingerprint sensor is not working right now. Please try again shortly.",
        "ফোনে আঙুলের ছাপ যোগ করা নেই। Settings → Security → Fingerprint-এ গিয়ে নিজের আঙুল যোগ করুন, তারপর আবার চেষ্টা করুন।" to "No fingerprint is set up on this phone. Go to Settings > Security > Fingerprint, add your finger, then try again.",
        "ফোনের নিরাপত্তা আপডেট বাকি আছে, তাই আঙুলের ছাপ ব্যবহার করা যাচ্ছে না। মাস্টারকে জানান।" to "A phone security update is pending, so fingerprint cannot be used. Please tell the master.",
        "এই ফোনে নিরাপদ আঙুলের ছাপের ব্যবস্থা নেই। মাস্টারকে জানান।" to "This phone does not support secure fingerprint. Please tell the master.",
        "কয়েকবার ভুল হয়েছে, তাই কিছুক্ষণের জন্য বন্ধ। একটু পরে আবার চেষ্টা করুন।" to "Too many wrong tries, so it is locked for a short while. Please try again shortly.",
        "আঙুলের ছাপ বন্ধ হয়ে গেছে। ফোনের স্ক্রিন-লক (PIN/প্যাটার্ন) দিয়ে একবার ফোন খুলুন, তারপর আবার চেষ্টা করুন।" to "Fingerprint is locked. Unlock the phone once with its screen lock, then try again.",
        "আঙুলের ছাপ মিলল না। আঙুল একটু মুছে আবার চেষ্টা করুন।" to "Fingerprint did not match. Wipe your finger and try again.",
        "আপনি বাতিল করেছেন।" to "You cancelled.",
        "আঙুলের ছাপ যাচাই করা গেল না। একটু পরে আবার চেষ্টা করুন।" to "Could not check the fingerprint. Please try again shortly.",
        "আপনি ক্লিনিকে নেই" to "You are not at the clinic",
        // 🔴 V497 বিল্ড-ফিক্স (২১.০৮.২০২৬): আগে এখানে `$distance` লেখা ছিল।
        //    Kotlin-এ `"…$নাম…"` মানে **চলকের মান বসানো** — লেখা নয়। তাই
        //    "unresolved reference: distance" এসে বিল্ড ভাঙত। তাছাড়া চলতি
        //    অ্যাপে ওখানে আসল সংখ্যা বসে (যেমন "৪২"), তাই এই সারি কখনো
        //    মিলতই না। এখন দু-টুকরো করে দেওয়া হলো — দুটোই মেলে।
        " (প্রায় " to " (about ",
        " মিটার দূরে)" to " metres away)",
        "। হাজিরা শুধু চেম্বারে এসে দেওয়া যায়।" to ". Attendance can only be marked at the chamber.",
        "}-এর অবস্থান এখনো অ্যাপে বসানো হয়নি। মাস্টারকে জানান — তিনি Fix Attendance দিয়ে হাজিরা বসিয়ে দিতে পারবেন।" to "} location is not set in the app yet. Tell the master - he can mark it with Fix Attendance.",
        "হাজিরার জন্য Location-এর অনুমতি দরকার। অনুমতি দিয়ে আবার চেষ্টা করুন।" to "Location permission is needed for attendance. Allow it and try again.",
        "ফোনের Location বন্ধ আছে। চালু করে আবার চেষ্টা করুন।" to "Phone Location is off. Turn it on and try again.",
        "অবস্থান পাওয়া গেল না। খোলা জায়গায় গিয়ে বা জানালার কাছে দাঁড়িয়ে আবার চেষ্টা করুন।" to "Could not get your location. Stand near a window or in the open and try again.",
        "অবস্থানটা যথেষ্ট নিশ্চিত নয়। একটু অপেক্ষা করে বা জানালার কাছে গিয়ে আবার চেষ্টা করুন।" to "The location is not accurate enough. Wait a moment or move near a window and try again.",
        "নকল অবস্থান ধরা পড়েছে, তাই হাজিরা নেওয়া হয়নি। মাস্টারকে জানানো হবে।" to "A fake location was detected, so attendance was not taken. The master will be informed.",
        "আপনার ব্রাঞ্চ চেনা গেল না। মাস্টারকে জানান।" to "Your branch could not be identified. Please tell the master.",
        "অবস্থান যাচাই করা গেল না। একটু পরে আবার চেষ্টা করুন।" to "Could not check your location. Please try again shortly.",
        "পুরনো Android-এ দরকার" to "Needed on older Android",
        "লগইন যাচাই করা গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।" to "Could not verify your sign-in. Check the internet and try again.",
        "হাজিরা পাঠানো গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।" to "Could not send the attendance. Check the internet and try again.",
        "হাজিরা এখন সেভ করা গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।" to "Attendance could not be saved right now. Check the internet and try again.",
        "সার্ভার কিছু জানায়নি। আবার চেষ্টা করুন।" to "The server did not reply. Please try again.",
        "সার্ভারের উত্তর বোঝা গেল না। আবার চেষ্টা করুন।" to "Could not read the server reply. Please try again.",
        "ডাক্তারদের জন্য হাজিরার ব্যবস্থা নেই — আপনি যেকোনো সময় আসতে ও যেতে পারেন।" to "Doctors have no attendance - you may come and go at any time.",
        "অ্যাপ খুলুন" to "Open app",
        // 🆕 V499 (২১.০৮.২০২৬) — অ্যাপ খোলার তালা: আঙুল অথবা ফোনের পাসওয়ার্ড।
        "আঙুলের ছাপ দিন, নয়তো ফোনের পাসওয়ার্ড দিন" to "Give your fingerprint, or your phone password",
        "অ্যাপের তালা বন্ধ" to "App lock is off",
        "এই ফোনে আঙুলের ছাপ বা স্ক্রিন-লক কিছুই নেই, তাই অ্যাপ সরাসরি খুলল। নিরাপত্তার জন্য ফোনের Settings-এ গিয়ে স্ক্রিন-লক চালু করুন।" to "This phone has no fingerprint and no screen lock, so the app opened directly. For safety, turn on a screen lock in phone Settings.",
        "ঠিক আছে" to "OK",
        "নিরাপত্তার জন্য আঙুলের ছাপ দিন" to "Give your fingerprint for security",
        "অ্যাপ বন্ধ করুন" to "Close app",
        "Settings খুলুন" to "Open Settings",
        "আপনার অ্যাকাউন্ট বন্ধ করা হয়েছে। মাস্টারকে জানান।" to "Your account has been closed. Please tell the master.",
        // 🔴 V497 বিল্ড-ফিক্স: এখানেও `$until` ছিল — একই কারণে বাদ। মাঝখানে
        //    আসল তারিখ বসে, তাই শুধু স্থির অংশটুকু অনুবাদ করা হলো।
        //    ⛔ শুধু "আপনি " বদলানো হয়নি — ওটা আরও অনেক বার্তার শুরুতে আছে,
        //       বদলালে সেগুলোর মানে উল্টে যেত। শেষ জাল বাকি বাংলা মুছে দেবে।
        " পর্যন্ত বন্ধ আছেন। মাস্টারকে জানান।" to " - you are suspended until this date. Please tell the master.",
        "অনেকদিন অ্যাপ ব্যবহার হয়নি, তাই নিরাপত্তার জন্য লগআউট করা হলো। মোবাইল ও পাসওয়ার্ড দিয়ে আবার লগইন করুন।" to "The app was unused for a long time, so you were logged out for safety. Log in again with mobile and password.",
        "ক্লিনিকে আছেন কিনা দেখা হচ্ছে…" to "Checking whether you are at the clinic...",
        "ক্লিনিকে আছেন কিনা" to "At the clinic?",
        "হাজিরা দিতে আঙুলের ছাপ দিন" to "Give your fingerprint to mark attendance",
        "হাজিরা সেভ হচ্ছে…" to "Saving attendance...",
        "হাজিরা হয়ে গেছে।" to "Attendance done.",
        "আঙুলের ছাপ" to "Fingerprint",
        "অ্যাকাউন্ট বন্ধ" to "Account closed",
        "এখন সেভ করা গেল না।" to "Could not save right now.",
        "আজ ছুটি" to "Leave today",
        "হাজিরা" to "Attendance",
        // 🔵🆕 V488 (২০.০৮.২০২৬, TK-নির্দেশ): ওষুধ খোঁজার ঘরের নতুন লেখা
        // ("🔍 Search" → "🔍 মেডিসিন")। KNE-KISHAN5-এর ফোনে বাংলা বন্ধ, তাই
        // অনুবাদ না থাকলে শব্দটা **মুছে** যেত (শেষ-জাল নিয়ম) আর ঘরটা ফাঁকা
        // দেখাত। এখন ওই ফোনে সেখানে "Medicine" বসবে।
        "মেডিসিন" to "Medicine",
        // 🔴🆕🔒 V438 (১৮.০৮.২০২৬, TK-নির্দেশ "৩ নম্বরটা করুন") — পাহারাদার ৯.১৪-এ
        //    ধরা পড়া **সব** বাকি বাংলা লেখার ইংরেজি। এতদিন এগুলোর অনুবাদ ছিল না,
        //    তাই বাংলা-বন্ধ স্টাফের (KNE-KISHAN5) পর্দায় ওগুলো হয় বাংলাতেই থাকত,
        //    নয়তো "শেষ জাল"-এ মুছে গিয়ে **ফাঁকা** দেখাত — দুটোই খারাপ।
        //    ⛔ বাংলা চালু থাকা বাকি সবার পর্দা এক চুলও বদলায় না।

        // — Branch বাছাইয়ের ফাঁকা-পর্দার বার্তা (BranchFilterStore.kt)
        "উপরে ডান দিকের Branch বাক্স থেকে একটি ব্রাঞ্চ বাছুন" to
            "Choose a branch from the Branch box at the top right",
        "একবার বাছলেই সব পর্দায় এটাই মনে থাকবে — বারবার বাছতে হবে না" to
            "Once you choose, every screen remembers it — no need to choose again",
        "সব ব্রাঞ্চ একসঙ্গে দেখতে চাইলে All বাছুন" to
            "Choose All to see every branch together",
        "উপরে ডান দিকে Branch বাছুন" to "Choose Branch at the top right",

        // — Chamber আবার খোলার প্রশ্ন (ChamberAttendanceActivity.kt)
        //   ⛔ "এই দিনের (" আগে থেকেই তালিকায় আছে — শুধু শেষ টুকরোটা ছিল না
        ") চেম্বার আবার খুলবেন?" to ")?",

        // — RMP পেমেন্ট (DoctorVisitActivity.kt)
        "শুধু নিজের ব্রাঞ্চের RMP-কে টাকা দেওয়া যাবে (এই RMP: " to
            "Payment is allowed only to an RMP of your own branch (this RMP: ",
        "এই বাড়তি টাকা শুধু Master অনুমোদন করতে পারেন" to
            "Only Master can approve this extra amount",

        // — Export Data-র সতর্কবার্তা (ExportDataActivity.kt)
        "এটা চাপলে বাছাই করা টেবিলের **সব তথ্য** ইন্টারনেট থেকে নামবে " to
            "Tapping this downloads **all the data** of the selected tables from the internet ",
        "(রোগীর ছবিসহ) — প্রায় পুরো ডেটাবেস" to
            "(with patient photos) — almost the whole database",
        "এতে মাসিক ডেটার হিসাব থেকে অনেকটা খরচ হয়। সত্যিই দরকার হলে " to
            "This uses up a large part of the monthly data allowance. Only if it is truly needed ",
        "তবেই চালান, বারবার নয়" to "should you run it — not again and again",
        "এখন নামাব?" to "Download now?",

        // — Income / Expense পর্দা (IncomeExpenseActivity.kt)
        "এই দিনে শুধু খরচ আছে — Add Expense পর্দা থেকে দেখুন" to
            "This day has expenses only — view them from the Add Expense screen",
        " খাতার সারিতেই লেখা — বদলাতে হলে ওই সারির তারিখ / Cash / Online ঘরে 3 বার চাপুন" to
            " below is written in the notebook row itself — to change it, tap 3 times on that row's Date / Cash / Online box",
        "খাতার সারিতে লেখা খরচ" to "Expense written in the notebook row",
        "এই খরচটি এখন বদলানো যাচ্ছে না — একবার ↻ চেপে আবার দেখুন" to
            "This expense cannot be edited right now — press ↻ once and try again",
        "✏️ খরচ বদলান" to "✏️ Edit Expense",
        "নাম লিখুন (শুধু সংখ্যা চলবে না)" to "enter a name (numbers alone will not do)",
        "খরচ বদলানো হয়েছে" to "Expense updated",
        "বদলানো গেল না (নেট?) — আবার চেষ্টা করুন" to
            "Could not update (internet?) — please try again",
        "এই খরচটি মুছবেন?" to "Delete this expense?",
        "এটি হিসাব থেকে বাদ যাবে" to "It will be taken out of the totals",
        "চিরতরে মুছবে না — দরকারে ফেরানো যাবে" to
            "It is not erased forever — it can be brought back if needed",
        "খরচটি মুছে ফেলা হয়েছে" to "The expense has been deleted",
        "মোছা গেল না (নেট?) — আবার চেষ্টা করুন" to
            "Could not delete (internet?) — please try again",
        "এই দিনে এই অঙ্ক আগেই জমা আছে — আর যোগ হয়নি" to
            "This amount is already recorded for this day — it was not added again",
        "টাকা: " to "Amount: ",
        "নিচের " to "The ",

        // V372: KNE-KISHAN5-এর English-only display-এর জন্য অনুমোদিত
        // Calendar/Follow-up/Doctor Check-up অনুবাদ। অন্য Staff-এর লেখা বদলায় না।
        "কাল আসার কথা" to "Expected Tomorrow",
        "আসবেন" to "will come",
        "ফোন" to "Call",
        "পরের ফোন কবে?" to "Next Call Date?",
        "পরের আসার দিন" to "Next Expected Date",
        "রোগী ফোনে কী বলল?" to "What did the patient say on the call?",
        "আসবে (চেম্বারে)" to "Will come (to chamber)",
        "চেম্বার-দিন বাছুন → একদিন আগে ফোন-রিমাইন্ডার" to "Choose chamber date → call reminder one day earlier",
        "শুধু ফোন করব" to "Call only",
        "যেকোনো দিন → ওইদিনই ফোনের তারিখ" to "Any date → same date for the call",
        "Clinical পরীক্ষা" to "Clinical Examination",
        "চোখে দেখা পরীক্ষা" to "Visual Examination",
        "চিকিৎসা পরিকল্পনা" to "Treatment Plan",
        "এখানে কিছু লিখুন…" to "Write here…",
        "আয় ও ব্যয়" to "Income & Expense",
        "প্রকৃত জমার তারিখ" to "Actual Deposit Date",
        "টাকার খাতা" to "Cash Ledger",
        "ব্রাঞ্চ বাছুন" to "Select Branch",
        "মাস বাছুন" to "Select Month",
        "খরচ" to "Expense",
        "অবশিষ্ট টাকা" to "Remaining Balance",
        "এই দিনের খরচের কোনো বিবরণ লেখা নেই।" to "No expense details recorded for this day.",
        "মোট খরচ" to "Total Expense",
        "খরচের বিবরণ" to "Expense Details",
        "ব্যায়" to "Expense",
        "টাকার হিসাব" to "Accounts",
        "আজকের হিসাব" to "Today's Accounts",
        "আয়" to "Income",
        "ব্যয়" to "Expense",
        "অবশিষ্ট" to "Balance",
        "লোড হয়নি" to "Not Loaded",
        "এই মাসের হিসাব" to "This Month's Accounts",
        "পুরো খাতা" to "Full Ledger",
        "অংশীদারি ভাগ" to "Partner Share",
        "উপরে ডানে ব্রাঞ্চ বাছুন" to "Select a branch at the top right",
        "নগদ বা অনলাইন — অন্তত একটা লিখুন" to "Enter at least one: Cash or Online",
        "আরেকটা যোগ করতে পারেন" to "You may add another",
        "Amount লিখুন" to "Enter Amount",
        "খরচের সংখ্যায় চাপ দিলে ওই দিন কিসে খরচ হয়েছিল দেখাবে" to "Tap the expense amount to view that day's expense details",
        "এই মাসে এখনো কোনো এন্ট্রি নেই।" to "No entries for this month yet.",
        "সব ব্রাঞ্চ" to "All Branches",
        "গত মাসের ব্যালেন্স" to "Previous Month's Balance",
        "WhatsApp-এ শেয়ার" to "Share on WhatsApp",
        "শেয়ার করা গেল না" to "Could not share",
        "হালনাগাদ হচ্ছে…" to "Updating…",
        "খুলছি... একটু অপেক্ষা করুন" to "Opening... please wait",
        "আজকের তথ্য আনছি..." to "Loading today's information...",
        "আবার চেষ্টা করুন" to "Try Again",
        "আজকের তথ্য এখন আনা গেল না। OUT TIME নিরাপদে বসাতে আজকের তথ্যটা দরকার" to "Today's information could not be loaded. It is required to save OUT TIME safely",
        "আবার চেষ্টা" to "Retry",
        "Net সমস্যা" to "Network problem",
        // 🆕 B438 (05.08.2026) — Work Notebook postpone-time toast-এ স্টাফের
        // বাছা সময় ইন্টারপোলেট থাকে বলে WHOLE-এ মিলবে না।
        "ঠিক আছে, " to "OK, ", "-এ আবার মনে করানো হবে" to " you'll be reminded again",
        // 🆕 B433 (05.08.2026) — MessageSentLog.warningText()-এর সতর্কতা-
        // লাইনে স্টাফের নাম/তারিখ ইন্টারপোলেট করা থাকে বলে WHOLE-এ পুরো
        // বাক্য মিলবে না — তাই এখানে Bengali অংশটুকু আলাদা করে অনুবাদ।
        "⚠️ এই বার্তা আগে " to "⚠️ Already sent by ",
        "-এ পাঠিয়েছেন।" to ".",
        "অন্য স্টাফ" to "another staff",
        // 🆕 B419 (04.08.2026) — Chamber Reopen-এর বাক্যগুলোতে তারিখ/ব্র্যাঞ্চ
        // ইন্টারপোলেট করা থাকে বলে WHOLE-এ পুরো বাক্য মিলবে না — তাই এখানে
        // Bengali অংশটুকু আলাদা করে (ইন্টারপোলেশনের আগে/পরে) অনুবাদ।
        "এই দিনের (" to "Reopen chamber for (",
        ") চেম্বার আবার খোলার অনুরোধ Master-এর কাছে পাঠাবেন?" to ") — send request to Master?",
        " আবার এডিটযোগ্য" to " editable again",
        "\nMaster: অনুমোদন দিলে এই দিনের চেম্বার আবার এডিটযোগ্য হয়ে যাবে।" to "\nMaster: approving will make this day's chamber editable again.",
        // 🆕 (03.08.2026, TK-অনুমোদিত) — Doctor Note স্ক্রিনে (activity_doctor_
        // checkup.xml + DoctorCheckupActivity.kt) ইংরেজির পাশে বাংলা যোগ হয়েছে।
        // Kishanganj-এ শুধু ইংরেজিটাই থাকুক, তাই "\nবাংলা" অংশটা পুরোপুরি বাদ।
        "\nমৌলিক বৃত্তান্ত" to "", "\nপ্রধান অভিযোগ" to "", "\nকতদিন থেকে" to "",
        "\nপেশা" to "", "\nপূর্ববর্তী চিকিৎসার বৃত্তান্ত" to "", "\nপূর্বে নেওয়া চিকিৎসা" to "",
        "\nপূর্বের ফলাফল" to "", "\nআগের খরচ" to "", "\nচিকিৎসার মেয়াদ" to "",
        "\nক্লিনিক্যাল ফলাফল" to "", "\nদৃশ্যমান পরীক্ষা" to "", "\nঅন্যান্য" to "",
        "\nডিআরই পরীক্ষা" to "", "\nপ্রক্টোস্কোপি গ্রেড" to "", "\nঅন্যান্য ফলাফল" to "",
        "\nপরীক্ষা-নিরীক্ষা" to "", "\nপরামর্শ ও উপদেশ" to "", "\nরোগের ব্যাখ্যা দেওয়া হয়েছে" to "",
        "\nআর্থিক আলোচনা" to "", "\nআনুমানিক খরচ" to "", "\nআনুমানিক সুস্থ হওয়ার সময়" to "",
        "\nঅগ্রিম আলোচনা" to "", "\nরোগীর সিদ্ধান্ত" to "", "\nঅন্যান্য / মন্তব্য" to "",
        "\nছবি ও নথি" to "", "\nআগের ছবি" to "", "\nচলাকালীন ছবি" to "", "\nপরের ছবি" to "",
        "\nনথি / রিপোর্ট নোট" to "", "\nদ্রুত কাজ" to "",
        // 🆕 (05.08.2026, TK-নির্দেশ, স্টেপ ৫ Photo & Video) — "Documents /
        // Reports Note" ও "Quick Actions" এখন " · " দিয়ে পাশাপাশি (আগে "\n" ছিল)।
        " · নথি / রিপোর্ট নোট" to "", " · দ্রুত কাজ" to "",
        // 🆕 (04.08.2026, TK-অনুমোদিত, "লক করুন") — Doctor Note স্ক্রিন নতুন
        // ডিজাইনে (হেডার + ৫ ধাপ + Treatment Plan)। নতুন/বদলানো বাংলা টুকরো।
        " · রোগীর বিবরণ" to "", "\nবর্তমান রোগের ইতিহাস ও পূর্ববর্তী চিকিৎসার বিবরণ" to "",
        "\nপ্রধান সমস্যা" to "", "\nহঠাৎ হয় নাকি ধীরে ধীরে বাড়ে" to "",
        "\nআগের চিকিৎসার বিবরণ" to "", "\nআগের চিকিৎসার ফলাফল" to "",
        "\nআগের চিকিৎসার খরচ" to "", "\nচিকিৎসা কত দিন চলেছিল" to "",
        // 🆕 (05.08.2026, TK-নির্দেশ) — স্টেপ ১-এর সেকশন-হেডার বাদ + ৬টা
        // লেবেল এখন "\nবাংলা"-র বদলে " · বাংলা" (এক লাইনে, পাশাপাশি) — তাই
        // এই নতুন ফরম্যাটের জন্য আলাদা এন্ট্রি; পুরনো "\n..." এন্ট্রিগুলো
        // (উপরে ও ৩৩২-৩৩৪ লাইনে) মোছা হয়নি, অন্য স্ক্রিনে/জায়গায় এখনো লাগে।
        " · প্রধান সমস্যা" to "", " · কতদিন থেকে" to "",
        " · হঠাৎ হয় নাকি ধীরে ধীরে বাড়ে" to "", " · পেশা" to "",
        " · আগের চিকিৎসার বিবরণ" to "", " · আগের চিকিৎসার ফলাফল" to "",
        // 🆕 (06.08.2026, TK-নির্দেশ, B482) — Grade/Other Findings এখন
        // " · " ফরম্যাটে (আগে "\n" ছিল, উপর-নিচে থেকে বক্স-ডিজাইনে টেক্সট
        // একলাইনে আনতে বদল)।
        " · প্রক্টোস্কোপি গ্রেড" to "", " · অন্যান্য ফলাফল" to "",
        // 🆕 (06.08.2026, TK-নির্দেশ, B484) — Dialer-এর নতুন "চেম্বার নম্বর
        // আছে কিনা" প্রশ্ন-পপ-আপ ও ফাঁকা-তালিকার বার্তা।
        "এই ফোনে কি চেম্বার/ব্রাঞ্চের নম্বর আছে?" to "Does this phone have the chamber/branch number?",
        "এই ফোনের কোনো সিমে কি ক্লিনিকের চেম্বার/ব্রাঞ্চের নম্বরটা আছে? ব্যক্তিগত নম্বর হলে \"না\" বলুন — তাহলে Dialer-এ কোনো কল দেখানো হবে না।" to "Does any SIM on this phone have the clinic's chamber/branch number? If it's a personal number, choose \"No\" — Dialer will then show no calls.",
        // 🆕 (B488) — Work Notebook-এর একই প্রশ্ন, বার্তা সামান্য ছোট।
        "এই ফোনের কোনো সিমে কি ক্লিনিকের চেম্বার/ব্রাঞ্চের নম্বরটা আছে? ব্যক্তিগত নম্বর হলে \"না\" বলুন।" to "Does any SIM on this phone have the clinic's chamber/branch number? If it's a personal number, choose \"No\".",
        "হ্যাঁ" to "Yes",
        "এই ফোনে চেম্বারের নম্বর নেই বলে জানানো আছে, তাই কল দেখানো হচ্ছে না।" to "This phone is marked as not having the chamber number, so no calls are shown.",
        // 🔴🔒 V474 (20.08.2026) — "কাল আসার কথা" পাতায় নাম/নম্বর long-press
        // করলে কপি হওয়ার নতুন Toast দুটো।
        "নাম কপি হয়েছে" to "Name copied",
        "নম্বর কপি হয়েছে" to "Number copied",
        // 🔴🔒 V481 (20.08.2026) — Chamber বন্ধ থাকা দিনে সম্পাদনা আটকানোর
        // নতুন সতর্কবার্তা।
        "এই দিনের চেম্বার বন্ধ (Close) করা হয়ে গেছে — আর কোনো পরিবর্তন করা যাবে না।" to
            "This day's chamber has already been closed — no more changes can be made.",
        // 🔴🔒 V478 (20.08.2026) — saveDay()-এর সৎ (silent-fail-বন্ধ) সতর্কবার্তা।
        "⚠️ এখনই ক্লাউডে সেভ হয়নি (ফোনে জমা আছে, নেট এলে নিজে বসে যাবে)। এখনই ইন্টারনেট/ওয়াইফাই চেক করে আবার বোতাম চাপুন।" to
            "⚠️ Not saved to the cloud yet (kept queued on this phone, will sync automatically once online). Please check internet/WiFi now and tap the button again.",
        // 🔴🔒 V468 (20.08.2026) — উপরের বাক্যের সাথে "আবার জিজ্ঞাসা করুন"
        // যোগ হওয়ায় নতুন সম্পূর্ণ বাক্যটার জন্য আলাদা এন্ট্রি — পুরনোটা
        // (উপরে) অন্য কোথাও ব্যবহৃত থাকতে পারে বলে অক্ষত রাখা হলো।
        "এই ফোনে চেম্বারের নম্বর নেই বলে জানানো আছে, তাই কল দেখানো হচ্ছে না।\n\n👉 এটা ভুল হলে — এখানে চাপুন আবার জিজ্ঞাসা করতে" to "This phone is marked as not having the chamber number, so no calls are shown.\n\n👉 If this is wrong — tap here to ask again",
        // 🆕 (06.08.2026, B504) — Income-Expense দিনের সারাংশ ডায়ালগ।
        "(শেষ জানা তথ্য — হালনাগাদ হচ্ছে…)" to "(Last known data — updating…)",
        // 🆕 (06.08.2026, TK-নির্দেশ, B473) — Cost/Duration বক্স এখন পূর্ণ-
        // চওড়া (উপর-নিচে), তাই আগের "\n" ফরম্যাটের বদলে বাকি লেবেলগুলোর
        // মতোই " · " দিয়ে এক লাইনে পাশাপাশি। পুরনো "\nআগের চিকিৎসার খরচ"
        // এন্ট্রি (৩৫০ লাইনে) মোছা হয়নি।
        " · আগের চিকিৎসার খরচ" to "", " · চিকিৎসা কত দিন চলেছিল" to "",
        // 🆕 (05.08.2026, TK-নির্দেশ, স্টেপ ২ Clinical Findings) — "Other" ·
        // "B. DRE..." · "D. On Probing" · "E. Investigations" এখন " · "
        // দিয়ে পাশাপাশি (আগে "\n" ছিল); আর "Fistula Opening"/"Bleeding"
        // চেকবক্সে বাংলা এখন নতুন লাইনে (আগে " · " ছিল) — তাই দুই দিকেরই
        // নতুন এন্ট্রি। পুরনো "\nঅন্যান্য"/" · ফিস্টুলার মুখ" ইত্যাদি এন্ট্রি
        // মোছা হয়নি (ক্ষতিহীন, আর কোথাও লাগলে কাজ করবে)।
        " · অন্যান্য" to "", " · ডিআরই পরীক্ষা" to "",
        " · প্রোব পরীক্ষা" to "", " · পরীক্ষা-নিরীক্ষা" to "",
        "\nফিস্টুলার মুখ" to "", "\nরক্ত পড়ে" to "",
        // 🆕 (06.08.2026, TK-নির্দেশ, B478) — Visual/DRE-এর বাকি সব
        // চেকবক্সও এখন "\n" ফরম্যাটে (আগে " · " ছিল, TK-এর ধরিয়ে দেওয়া
        // "প্রতিবার পাইলস লেখার পাশে ডট কেন" এর পরে সবগুলোতেই একই ফিক্স)।
        // পুরনো " · ..." এন্ট্রি (নিচে) মোছা হয়নি।
        "\nবাহ্যিক অর্শ" to "", "\nঅভ্যন্তরীণ অর্শ" to "", "\nফিসার" to "",
        "\nফোলাভাব" to "", "\nকোমলতা/ব্যথা" to "", "\nফিস্টুলা" to "",
        // 🆕 (06.08.2026, TK-নির্দেশ, B479) — Step 3 (Counsel)-এর চেকবক্স
        // লেবেল নতুন "English/ বাংলা" ফরম্যাটে (TK-এর নিজের দেওয়া লেখা)।
        "/ প্রতি অর্শ" to "", "/ ফিস্টুলা প্রতি ইঞ্চি" to "",
        "/ প্রতি ক্ষারসূত্র" to "", "/ মেশিনের মাধ্যমে চিকিৎসা" to "",
        "উপরে টিক মারার বাইরেও যদি রোগীকে অন্য কিছু বলে থাকেন তাহলে সেই কথা এখানে লিখুন, অন্যথায় " to "",
        // 🆕 (05.08.2026, TK-নির্দেশ, B465 — Work Notebook OUT TIME) —
        // "Why are you leaving?"-এর সাবটেক্সট ও "Office time over"-এর
        // পাশে বাংলা, আর নতুন কাস্টম-লেখার বক্সের hint।
        "আমি ক্লিনিক থেকে বেরোলাম" to "",
        " · অফিস টাইম শেষ" to "",
        // 🆕 (06.08.2026, B510) — "Why are you leaving?" কার্ড-ডিজাইনে
        // এখন আলাদা লাইনে (আগে " · " দিয়ে জোড়া ছিল)।
        "অফিস টাইম শেষ" to "Office time over",
        "ব্যক্তিগত কাজ — আগে চলে যাচ্ছেন" to "Personal work — leaving early",
        "কেন যাচ্ছেন এই সম্পর্কে কিছু লিখুন" to "Write something about why you're leaving",
        // 🆕 (05.08.2026, TK-নির্দেশ, B466 — Work Notebook OUT TIME redesign)
        "তুমি কেন চলে যেতে চাইছ" to "",
        "কেন বাড়ি যাচ্ছেন লিখুন" to "Write why you're going home",
        "কেন ব্যক্তিগত কাজে যাচ্ছেন?" to "Why are you leaving for personal work?",
        // 🔴 B466 — Work Notebook-এর নতুন মেশানো (English / বাংলা) লেবেলের
        // বাংলা অংশ — কোডে ইতিমধ্যেই KNE-KISHAN5-এর জন্য আলাদা করে শুধু
        // ইংরেজি টেক্সট বসানো আছে, তবু পাহারাদার (tk_guard.py 9.14) প্রতিটা
        // .kt লিটারেল স্ট্রিং স্ক্যান করে বলে এই এন্ট্রিগুলোও লাগে, নইলে
        // "অনুবাদ নেই" ধরে আটকে যায়।
        " / আজকের কাজ" to "",
        " / আজকের সারাদিনে কি কি কাজ করেছেন" to "",
        " / আজকে কি আপনার ছুটি" to "",
        // 🆕 (06.08.2026, B506) — Leave বোতামের হালনাগাদ লেখা।
        " / আজকে আমার ছুটি" to "",
        "\nপ্রোব পরীক্ষা" to "", "\nকীভাবে চিকিৎসা করা হবে" to "", " · কীভাবে চিকিৎসা করা হবে" to "",
        "\nআনুমানিক খরচ ও রোগীর সিদ্ধান্ত" to "", "\nআনুমানিক কতদিন বলা হল" to "",
        "\nঅগ্রিম কত টাকা জমা করতে চাইছে" to "", "\nরোগীর বা রোগী পক্ষের সিদ্ধান্ত" to "",
        // 🆕 (05.08.2026, TK-নির্দেশ, স্টেপ ৪ Estimate & Decision) — চারটা
        // লেবেল + Other/Remarks এখন " · " দিয়ে পাশাপাশি (আগে "\n" ছিল)।
        " · আনুমানিক খরচ" to "", " · আনুমানিক কতদিন বলা হল" to "",
        " · অগ্রিম কত টাকা জমা করতে চাইছে" to "", " · রোগীর বা রোগী পক্ষের সিদ্ধান্ত" to "",
        " · অন্যান্য / মন্তব্য" to "",
        "\nছবি ও ভিডিও" to "", "\nঅন্যান্য চিকিৎসার কথা (টাইপ করুন)" to "",
        // Treatment Plan-এর ৬টা Tick-বক্স — এগুলোয় বাংলা আগে/মাঝে থাকায়
        // শুধু সাফিক্স বাদ দিলে চলবে না, পুরো লেখাটাই পরিষ্কার ইংরেজিতে
        // বদলানো হলো (ঠিক DoctorCheckupActivity.kt-এর নতুন cb.tag-এর
        // মানের সাথে মিলিয়ে, যাতে সেভ-হওয়া মান ও দেখানো লেখা একই অর্থ বহন করে)।
        "Fistula Per ইঞ্চি" to "Fistula Per Inch",
        // 🔵 V541 (২২.০৮.২০২৬, TK-নির্দেশ): ইঞ্চি → সেমি। ⛔ উপরের পুরোনো
        //    লাইনটা **রাখা হলো**, নইলে পুরোনো রেকর্ডের লেখা ইংরেজিতে যেত না।
        "Fistula Per সেমি" to "Fistula Per CM",
        "ফিস্টুলা প্রতি সেমি" to "Fistula Per CM",
        // 🆕 (05.08.2026, TK-নির্দেশ) — "মেশিন দিয়ে কাজ হবে" ও "Machine
        // Treatment" এখন "\n"-এর বদলে " · " দিয়ে জোড়া (পাশাপাশি)।
        "মেশিন দিয়ে কাজ হবে · Machine Treatment" to "Machine Treatment",
        "Per ক্ষারসূত্র হিসাবে চিকিৎসা\nKshar Sutra" to "Kshar Sutra",
        "LIS-এর মাধ্যমে চিকিৎসা করা হবে" to "LIS Treatment",
        "Injection (Vaccination)-এর মাধ্যমে চিকিৎসা" to "Injection (Vaccination) Treatment",
        // 🆕 V501 (২১.০৮.২০২৬) — Treatment Plan-এর তিনটে লেখা এক লাইনে ধরাতে
        //    ছোট করা হয়েছে (TK-অনুমোদিত), তাই নতুন ইংরেজি এখানে যোগ করা হলো।
        //    ⛔ পুরনো সারি দুটো রেখে দেওয়া হলো — পুরোনো রেকর্ড/রিপোর্টে ওই
        //       লেখাগুলো এখনো থাকতে পারে।
        "Machine Treatment/ মেশিনে চিকিৎসা" to "Machine Treatment",
        "LIS/ এলআইএস চিকিৎসা" to "LIS Treatment",
        "Injection/ ইনজেকশন চিকিৎসা" to "Injection (Vaccination) Treatment",
        // 🆕 (05.08.2026, TK-নির্দেশ) — hint-এ "আপনি" যোগ হয়েছে, তাই এই
        // এন্ট্রিও নতুন লেখার সাথে মেলানো হলো (পুরনো লেখাটা এখন কোথাও
        // ব্যবহৃত নয়, তাও রাখা হলো, ক্ষতিহীন)।
        "রোগীকে কিভাবে চিকিৎসা করবেন বলেছেন সেই কথা এখানে লিখুন" to "Write here how the doctor will treat the patient",
        "রোগীকে আপনি কিভাবে চিকিৎসা করবেন বলেছেন সেই কথা এখানে লিখুন" to "Write here how you will treat the patient",
        // 🆕 (04.08.2026, Chamber Date-এর Treatment Progress কলাম) — ডিফল্ট
        // স্টাব রিমার্ককে "কিছু লেখা হয়নি" হিসেবে দেখানোর নতুন লেখা।
        "কিছু লেখা হয়নি — চাপুন" to "Nothing written — tap to add",
        // 🔴 V432 (TK-রিপোর্ট ১৮.০৮.২০২৬) — IN TIME/রিপোর্ট আবার পাঠানোর বোতাম
        "📤 IN TIME আবার WhatsApp-এ পাঠান" to "📤 Send IN TIME to WhatsApp again",
        "📤 রিপোর্ট আবার WhatsApp-এ পাঠান" to "📤 Send the report to WhatsApp again",
        // 🔴 V433 (TK-নির্দেশ ১৮.০৮.২০২৬) — পাঠানো হয়ে গেলে বোতাম লুকানোর ছোট প্রশ্ন
        "পাঠানো হয়েছে?" to "Sent?",
        "WhatsApp-এ পাঠানো হয়ে গেছে?" to "Has it been sent on WhatsApp?",
        "হ্যাঁ, পাঠানো হয়েছে" to "Yes, sent",
        "না, পাঠানো হয়নি" to "No, not sent",
        // 🔴 V434 (TK-রিপোর্ট ১৮.০৮.২০২৬ — "Remarks লেখার পর Save হচ্ছে না")
        "⏳ সেভ হচ্ছে…" to "⏳ Saving…",
        "লেখা রাখা হয়েছে — নেট এলে নিজে থেকেই যাবে। আবার লিখবেন না" to
            "Saved on this phone — it will go automatically when internet returns. Do not write it again",
        "নেট পাওয়া যায়নি — কিছুই সেভ হয়নি, একটু পরে আবার Save চাপুন" to
            "No internet — nothing was saved. Please press Save again in a moment",
        // 🔴 V436 (TK-রিপোর্ট ১৮.০৮.২০২৬ — পুরনো APK চুপচাপ চলছিল)
        "আপনার অ্যাপ পুরনো — নতুন ভার্সনটা বসান" to
            "Your app is out of date — please install the new version",
        // stepTitles (step-bar চিপ)
        " · বৃত্তান্ত" to "", " · পূর্ববর্তী" to "", " · ক্লিনিক্যাল" to "",
        " · পরামর্শ" to "", " · আর্থিক" to "", " · সিদ্ধান্ত" to "", " · ছবি" to "",
        // চেকবক্সের ডিসপ্লে-লেবেল (visualBn/dreBn/investigationBn, cb.text-এ)
        " · বাহ্যিক অর্শ" to "", " · ফিসার" to "", " · ফিস্টুলার মুখ" to "",
        " · রক্ত পড়ে" to "", " · ফোলাভাব" to "", " · কোমলতা/ব্যথা" to "",
        " · অভ্যন্তরীণ অর্শ" to "", " · ফিস্টুলা" to "", " · এমআরআই" to "",
        " · আল্ট্রাসাউন্ড" to "", " · কোলোনোস্কোপি" to "", " · ল্যাব রিপোর্ট" to "",
        // Grade/Patient Decision ড্রপডাউন (03.08.2026)
        " · গ্রেড I" to "", " · গ্রেড II" to "", " · গ্রেড III" to "", " · গ্রেড IV" to "",
        " · চিকিৎসায় রাজি" to "", " · রাজি নয়" to "", " · ভেবে দেখব" to "",
        " · পরিবারের সাথে আলোচনা" to "", " · আর্থিক সমস্যা" to "",
        // 🔒 B281 (02.08.2026): Refund request নোটিশ থেকে সরাসরি Approve/Reject।
        "এই নোটিশ থেকে Patient ID চেনা গেল না" to "Could not identify the Patient ID from this notice",
        "-এর জন্য এখন আর কোনো Pending Refund নেই — হয়তো আগেই Approve/Reject হয়ে গেছে, বা অন্য ফোন থেকে এখনো ক্লাউডে পৌঁছায়নি।" to
            " has no Pending Refund now — it may already be Approved/Rejected, or hasn't reached the cloud yet from another phone.",
        // 🔒 B274 (02.08.2026): "যায়নি" ঘরের এন্ট্রি স্থায়ীভাবে ছেড়ে দেওয়ার
        // নিশ্চিতকরণ পপ-আপ + Toast — নতুন যোগ, তাই অনুবাদ এখানে যোগ করা হলো।
        "স্থায়ীভাবে ছেড়ে দেবেন?" to "Give up permanently?",
        "টা তথ্য সার্ভারে খুঁজে পাওয়া যাচ্ছে না বলে কখনোই পাঠানো সম্ভব না। এগুলো আর দেখানো/চেষ্টা হবে না। রেকর্ড/টাকার হিসাব বদলাবে না।" to
            " item(s) can never be sent because the server can't find them anymore. They won't be shown or retried again. Records/money totals will not change.",
        "হ্যাঁ, ছেড়ে দিন" to "Yes, give up",
        "টা ছেড়ে দেওয়া হলো" to " item(s) given up",
        // 🔒 V221 (§1, 31.07.2026): খাতার সারি B158 — sync-স্ট্যাটাস/লাল সতর্কবার্তার
        // এই দুটো টুকরো (CloudWriteQueue.stuckDetail-এর "আরও N" ও
        // PendingSyncStatus-এর "আটকে: …") V219 §4-এ যোগ হয়েছিল কিন্তু অনুবাদ ছিল না,
        // তাই পাহারাদার ৯.১৪ ফেল করত (Bengali-off স্টাফের পর্দায় বাংলা থেকে যেত)।
        // ⛔ বাংলা চালু থাকা ব্যবহারকারীর কিছুই বদলায় না (fix() active না হলে হুবহু ফেরত)।
        "আটকে" to "stuck",
        "আরও" to "more",
        "পরে পাঠাব" to "Send later",
        "-এর অনুমতি লাগবে" to " permission is needed",
        "-এর অনুমোদন লাগবে" to " approval is needed",
        "-এর অনুমোদনের অপেক্ষায়" to " approval is awaited",
        "-এর কাছে অনুরোধ পাঠানো হয়েছে" to " has been sent the request",
        "-কে অনুরোধ পাঠানো হয়েছে" to " has been sent the request",
        "-এর ঘন্টায় অনুরোধ যাবে" to "'s bell will get the request",
        "-এর ঘন্টায় যাবে" to "'s bell will get it",
        "-এর থেকে বেশি হয়ে যাচ্ছে" to " limit is being exceeded",
        "-এ ঠিকই দেখাবে" to " will still show correctly",
        "-এ যাবে" to " will get it",
        "-এ চলে যাবে" to " it will move to",
        "-এ আছে" to " — inside",
        "-এ আছেন" to " branch",
        "নেট যাচাই করা যায়নি — এই নম্বর আগে থেকে রেজিস্টার আছে কিনা দেখা যাচ্ছে না। ডুপ্লিকেট রোগী এড়াতে একটু পরে আবার চেষ্টা করুন" to "Could not check the net — cannot tell if this number is already registered. Please try again in a moment so a duplicate patient is not created",
        "এ আছেন। একটা ব্রাঞ্চ বেছে নিন — প্রিন্টের উপরে ওই ব্রাঞ্চের ক্লিনিকের নাম বসবে এবং নিচে শুধু ওই ব্রাঞ্চের রোগীই থাকবে" to "branch. Pick one branch — that branch's clinic name will print on top and only that branch's patients will be listed below",
        "নেট ঠিকমতো কাজ করছে না, তাই এই নম্বরটা আগে থেকে রেজিস্টার করা আছে কিনা দেখা গেল না" to "The net is not working properly, so it could not be checked whether this number is already registered",
        "নেট যাচাই করা যায়নি — এই নম্বর আগে থেকে রেজিস্টার আছে কিনা দেখা যাচ্ছে না" to "Could not check the net — cannot tell if this number is already registered",
        "ডুপ্লিকেট রোগী তৈরি এড়াতে টাকা নেওয়া হয়নি। একটু পরে আবার চেষ্টা করুন" to "Money was not taken, to avoid creating a duplicate patient. Please try again in a moment",
        "এখনই সেভ করলে একই রোগীর দ্বিতীয় রেকর্ড তৈরি হয়ে যেতে পারে" to "Saving now may create a second record for the same patient",
        "উপরের ক্যালেন্ডারে চাপলে আজকের তালিকা আবার দেখতে পাবেন" to "Tap the calendar above to see today's list again",
        "এই মোবাইল নম্বরটি রেজিস্টার্ড নয়। সঠিক নম্বর দিন" to "This mobile number is not registered. Enter the correct number",
        "অনুমোদন দিলে তবেই ডিলিট হবে — এখনই কিছু মুছবে না" to "It will be deleted only after approval — nothing is removed now",
        "নিতে চাইছেন — এটা বিলের থেকে বেশি। তবুও এগোবেন" to "is being taken — this is more than the bill. Continue anyway",
        "হিসাব আনা গেল না — লাইন দেখে আবার চেষ্টা করুন" to "Could not load the figures — check the line and try again",
        "সব দিনের চেম্বার বন্ধ করা আছে — কিছু বাকি নেই" to "Every day's chamber is closed — nothing is pending",
        "এর ঘন্টায় যাবে — তিনি অনুমতি দিলে তবেই মুছবে" to "will go to the bell — it will be removed only after approval",
        "বিগত দিনের সেভ করা হিসাব — শুধু দেখা যাবে" to "Saved figures of a past day — view only",
        "নেওয়া যাবে — চাইলে নিচে বিল-ও বসিয়ে দিন" to "can be taken — you may also enter the bill below",
        "আজ কী হলো — নিজে লিখুন বা নিচের চিপ চাপুন" to "What happened today — write it yourself or tap a chip below",
        "নাম অথবা মোবাইল নম্বর — যেকোনো একটা লিখুন" to "Name or mobile number — write any one",
        "এই রোগীর আসার কথা ইতিমধ্যে দেওয়া হয়েছে" to "This patient already has an appointment date",
        "আজকের তারিখ (ডিফল্ট) — বদলাতে ট্যাপ করুন" to "Today's date (default) — tap to change",
        "অ্যাডমিন পাসওয়ার্ড রিসেটের জন্য ডিফল্ট" to "Default for admin password reset",
        "পেশেন্ট এসে আরো কি কি সমস্যার কথা বললেন" to "What other problems did the patient mention",
        "বিগত দিন — এই চেম্বার এখনো বন্ধ হয়নি" to "Past day — this chamber is not closed yet",
        "নতুন রেকর্ড হবে না — পুরনো রেকর্ডটাই" to "No new record will be created — the old record",
        "এই দিনে কেউ আসেননি — ছাপার কিছু নেই" to "Nobody came on this day — nothing to print",
        "কোন ব্রাঞ্চের রেজিস্টার প্রিন্ট হবে" to "Which branch's register will be printed",
        "এর অনুমতি লাগবে। এখানে অনুরোধ পাঠান" to "permission is needed. Send the request here",
        "এই দিনগুলোর চেম্বার এখনো বন্ধ হয়নি" to "The chamber of these days is not closed yet",
        "এ ঠিকই দেখাবে) — শুধু এই পেশেন্ট আর" to "will still show correctly) — only this patient and",
        "যে সেকশনে ছিল সেখানেই থাকবে, পুরনো" to "will stay in the same section, the old",
        "এই অনুরোধ থেকে রেকর্ড চেনা গেল না" to "The record could not be identified from this request",
        "কোনো কল/রেফারেল/আয় এখনো লগ হয়নি" to "No call / referral / income logged yet",
        "প্রকৃত জমার তারিখ (যদি আজ না হয়" to "Actual payment date (if not today",
        "তিনি অনুমোদন দিলে তবেই ডিলিট হবে" to "It will be deleted only after approval",
        "পাঠানো যায়নি — আবার চেষ্টা করুন" to "Could not send — try again",
        "অনুমোদন দিলে শুধু এই পেশেন্ট আর" to "After approval, only this patient and",
        "চেম্বার বন্ধ করার আগে সেভ করুন" to "Save before closing the chamber",
        "বার ট্যাপ করুন — সম্পাদনা করতে" to "taps to edit",
        "আজকের চেম্বার বন্ধ করা হয়েছে" to "Today's chamber has been closed",
        "হিসাব এখনো আসেনি — এক মুহূর্ত" to "Figures have not arrived yet — one moment",
        "থেকে নতুন পাসওয়ার্ড সেট করুন" to "set a new password from",
        "পাসওয়ার্ড পাল্টে দিতে পারবেন" to "can change the password",
        "বিগত দিনের চেম্বার বন্ধ করুন" to "Close a past day's chamber",
        "এর কাছে অনুরোধ পাঠানো হয়েছে" to "request has been sent to",
        "পাঠানো গেল না — নেট চেক করুন" to "Could not send — check the net",
        "এ চলে যাবে (পরে ফেরানো যাবে" to "will move to (can be restored later",
        "কী কারণে বাতিল করছেন, লিখুন" to "Write why you are cancelling",
        "পেমেন্টের দিন পার হয়ে গেছে" to "The payment day has passed",
        "টি তথ্য এখনো ক্লাউডে যায়নি" to "items have not reached the cloud yet",
        "আপনার পাসওয়ার্ড রিসেট করতে" to "to reset your password",
        "কোনো আসার তারিখ ঠিক করা নেই" to "No appointment date is set",
        // 🟢 B631 (11.08.2026): বাল্ক Delete/Restore অগ্রগতি-ইঙ্গিতের নতুন লেখা
        // (DraftListActivity)। বাংলা-বন্ধ স্টাফের পর্দায় যাতে "Deleting 3/9" / "Restoring 3/9" দেখায়।
        "মুছে ফেলা হচ্ছে" to "Deleting",
        "ফেরানো হচ্ছে" to "Restoring",
        // 🔵 B620 (11.08.2026): Full Journey-তে "আসবে বলেছে" সারির নতুন লেখা
        // ("আসার তারিখ: 11.08.2026")। longest-first বলে উপরের বড় বাক্যগুলো আগেই
        // অনুবাদ হয়, তাই এই ছোট চাবি শুধু একা "আসার তারিখ"-এ লাগে (no-Bengali → "Appointment date")।
        "আসার তারিখ" to "Appointment date",
        // 🔴 TK-REPORTED FIX (01.08.2026), পার-হয়ে-যাওয়া তারিখের নতুন Toast
        // (PatientTimelineActivity.kt) — দুটো টুকরোয় ভাঙা, মাঝে আসল তারিখ বসে।
        "এই আসার তারিখ (" to "This appointment date (",
        ") পার হয়ে গেছে — আগে নতুন তারিখ ঠিক করুন" to ") has passed — please set a new date first",
        "আসার তারিখ পরিবর্তন করেছেন" to "has changed the appointment date",
        "বসানো হয়নি। বিল না বসালেও" to "is not entered. Even without a bill",
        "পাসওয়ার্ড দিয়ে লগইন করে" to "log in with the password and",
        "আসার তারিখ মনে করিয়ে দিন" to "Remind about the appointment date",
        "এর থেকে বেশি হয়ে যাচ্ছে" to "is becoming more than",
        "দ্রুত (চাপলে লেখায় বসবে" to "Quick (tap to put it in the text",
        "নতুন পেশেন্টের নাম লিখুন" to "Write the new patient's name",
        "কে অনুরোধ পাঠানো হয়েছে" to "request has been sent to",
        "উপরে ব্রাঞ্চ বাছাই করুন" to "Select a branch above",
        "চেম্বার বন্ধ হয়ে গেছে" to "The chamber is closed",
        "এর ঘন্টায় অনুরোধ যাবে" to "the request will go to the bell of",
        "অ্যাডমিনকে বলুন — তিনি" to "Tell the admin — he",
        "এর অনুমোদনের অপেক্ষায়" to "waiting for approval of",
        "কল/রিমাইন্ডারে আসবে না" to "will not come in calls / reminders",
        "মাস্টারের অনুমতি লাগবে" to "Master's permission is needed",
        "দিনের বেশি) — এখন থেকে" to "days) — from now",
        "ব্যর্থ — নেট চেক করুন" to "Failed — check the net",
        "অনুমোদন দিলে রেকর্ডটা" to "after approval the record",
        "ডিলিট করতে পারেন শুধু" to "can be deleted only by",
        "সব কাজ / পরবর্তী কাজ" to "All work / next work",
        "অনুরোধ পাঠানো হয়েছে" to "The request has been sent",
        "আসার কথা দেওয়া আছে" to "Appointment date is given",
        "এখনই কিছুই মুছবে না" to "Nothing will be deleted now",
        "পাঠানোর চেষ্টা চলছে" to "Trying to send",
        "অনুমোদনের অপেক্ষায়" to "Waiting for approval",
        "করার অনুরোধ পাঠাবেন" to "send the request to",
        "কোনো ঘরে ভুল থাকলে" to "if any field is wrong",
        "হাইড্রোসিল (একশিরা" to "Hydrocele",
        "অনুরোধ পাঠালে সেটা" to "if the request is sent then it",
        "আসার কথায় যোগ হলো" to "added to the appointment",
        "প্রিন্ট তৈরি হচ্ছে" to "The print is being made",
        "আজ কোনো লেনদেন নেই" to "No transaction today",
        "এই মাস বনাম গত মাস" to "This month vs last month",
        "এখনো এই পেশেন্টের" to "this patient still",
        "দিনের বেশি) — এখন" to "days) — now",
        "চেম্বার বন্ধ করুন" to "Close the chamber",
        "ডিজিট মোবাইল দিন" to "digit mobile number",
        "এর অনুমোদন লাগবে" to "approval is needed",
        "কেন বদলাতে হচ্ছে" to "Why it has to be changed",
        "এই রেকর্ডটা খুলে" to "opening this record",
        "ভগন্দর (ফিস্টুলা" to "Fistula",
        "ব্রাঞ্চে আনা হলো" to "moved to branch",
        "অপরিবর্তিত থাকবে" to "will stay unchanged",
        "লিস্টে দেখা যাবে" to "will be seen in the list",
        "বেশি হয়ে যাচ্ছে" to "is becoming more",
        "যাচাই করা গেল না" to "could not be checked",
        "কোনো এন্ট্রি নেই" to "No entry",
        "আজ কল বাকি দেখুন" to "See today's pending calls",
        "সম্পূর্ণ ইতিহাস" to "Full history",
        "ট্যাপ করে বদলান" to "tap to change",
        "ক্লিয়ার করা হল" to "cleared",
        "ঠিক করুন। তারপর" to "Fix it. Then",
        "এর অনুমতি লাগবে" to "permission is needed",
        "তৈরি করা যায়নি" to "could not be created",
        "জন দেখানো হচ্ছে" to "people shown",
        "আসলেই ডিলিট হবে" to "It will really be deleted",
        "লিস্টে চলে যাবে" to "will move to the list",
        "বিস্তারিত দেখুন" to "See details",
        "রোগের বিস্তারিত" to "Disease details",
        "আসতে পারবেন না" to "Cannot come",
        "আসার কথা বাতিল" to "Appointment cancelled",
        "করতে পাঠানো হল" to "sent for",
        "এনকোয়ারি বন্ধ" to "Enquiry closed",
        "পেশেন্ট খুঁজুন" to "Search patient",
        "এর কাজ করা হল" to "work done",
        "চেপে মুছে দিন" to "press to delete",
        "পাঠানো হয়েছে" to "has been sent",
        "বাতিল করা হলো" to "has been cancelled",
        "দেওয়া হয়েছে" to "has been given",
        "ব্রাঞ্চে আসবে" to "will come to branch",
        "একটি পেমেন্টে" to "one payment",
        "পাঠানো যায়নি" to "could not be sent",
        "তবুও সেভ করুন" to "Save anyway",
        "চেম্বারের দিন" to "Chamber day",
        "তালিকা ফাঁকা" to "The list is empty",
        "অনুরোধ পাঠান" to "Send request",
        "করানো হয়েছে" to "done",
        "দেওয়া হয়নি" to "not given",
        "লেখা হয়েছিল" to "was written",
        "বার্তা পাঠান" to "Send message",
        "রেজিস্ট্রেশন" to "Registration",
        "প্রেসক্রিপশন" to "Prescription",
        "লাইন ঠিক হলে" to "when the line is fine",
        "সেরা চিকিৎসা" to "best treatment",
        "তারিখ বাছুন" to "Pick a date",
        "জন এসেছিলেন" to "people came",
        "তারিখ বদলান" to "Change the date",
        "এখানে লিখুন" to "Write here",
        "হ্যাঁ, এগোন" to "Yes, continue",
        "পাইলস (অর্শ" to "Piles",
        "লিস্ট দেখতে" to "to see the list",
        "করানো হয়নি" to "not done",
        "বাধ্যতামূলক" to "Mandatory",
        "লিস্টে যাবে" to "will go to the list",
        "কোন পেমেন্ট" to "Which payment",
        "প্রকৃত জমা" to "Actual collection",
        "নতুন তারিখ" to "New date",
        "কারণ লিখুন" to "Write the reason",
        "হিসাব আসছে" to "Figures are coming",
        "থেকে আপনার" to "from your",
        "বাতিল করুন" to "Cancel",
        "টি পেমেন্ট" to "payments",
        "আবার দেখুন" to "See again",
        "মোট বকেয়া" to "Total due",
        "বসাতে চান" to "want to enter",
        "অন্য কারণ" to "Other reason",
        "বাতিল হলো" to "cancelled",
        "এখনো বাকি" to "still pending",
        "দেওয়া হল" to "given",
        "বার চাপুন" to "taps",
        "বন্ধ করুন" to "Close",
        "লোড হচ্ছে" to "Loading",
        "তারিখ দিন" to "Enter the date",
        "থেকে যাবে" to "will remain",
        "এনকোয়ারি" to "Enquiry",
        "গুপ্ত রোগ" to "Gupt Rog",
        "সেভ করুন" to "Save",
        "আপনি এখন" to "You are now",
        "ইতিমধ্যে" to "already",
        "এখন পরের" to "now next",
        "আসার কথা" to "Appointment",
        "অন্য কাজ" to "other work",
        // 🔒 খাতার সারি B170 (30.07.2026): PendingSyncStatus-এর নতুন লেবেল।
        "চেম্বার ক্লোজ" to "Chamber Close",
        "করা হলো" to "done",
        "পাঠাবেন" to "will you send",
        "নাম দিন" to "Enter the name",
        "পেমেন্ট" to "Payment",
        "চেম্বার" to "Chamber",
        "ব্রিফিং" to "Briefing",
        "গত মাসে" to "last month",
        "অনুরোধ" to "request",
        "এসেছেন" to "arrived",
        "করা হল" to "done",
        "করেছেন" to "has done",
        "বদলাতে" to "to change",
        "এ যাবে" to "will go to",
        "অনুমতি" to "permission",
        "বার্তা" to "message",
        "ফলো-আপ" to "Follow-up",
        "সংশোধন" to "correction",
        "ভগন্দর" to "Fistula",
        "একশিরা" to "Hydrocele",
        "উপসর্গ" to "Symptoms",
        "বাছুন" to "select",
        "তারিখ" to "date",
        "এ আছে" to "is in",
        "ফিশার" to "Fissure",
        "পুরনো" to "old",
        "করবেন" to "will do",
        "ধ্বংস" to "destroy",
        "বাতিল" to "cancel",
        "চাপুন" to "tap",
        "পাঠান" to "send",
        "বাংলা" to "Bengali",
        "সঠিক" to "correct",
        "কারণ" to "reason",
        "আপনি" to "you",
        "থেকে" to "from",
        "বন্ধ" to "closed",
        "হয়ে" to "being",
        "অটুট" to "intact",
        "করুন" to "do",
        "বাকি" to "pending",
        "অর্শ" to "Piles",
        "দিন" to "day",
        "জমা" to "collected",
        "মোট" to "total",
        "মুছ" to "delete",
        "জন" to "people",
        "টি" to "items",
        "এর" to "of",
        "ভগন্দর (ফিস্টুলা) হলো মলদ্বারের ভেতরের অংশ ও তার পাশের চামড়ার মধ্যে তৈরি হওয়া একটি অস্বাভাবিক নালি। এটি সাধারণত মলদ্বারের কোনো ফোঁড়া (সংক্রমণের পকেট) সম্পূর্ণ না সারলে তৈরি হয়, যার ফলে একটি নালি থেকে যায় যার মধ্য দিয়ে পুঁজ বা তরল ক্রমাগত বের হতে থাকে। সময়ের সাথে সাথে এই নালি শাখা-প্রশাখায় জটিল হয়ে উঠতে পারে বলে, অর্শ বা ফিশারের চেয়ে ভগন্দরের চিকিৎসায় বেশি পরিকল্পিত পদ্ধতি লাগে — উপযুক্ত ক্ষেত্রে ঐতিহ্যবাহী আয়ুর্বেদিক ক্ষারসূত্র (ওষুধযুক্ত সুতো) পদ্ধতি ব্যবহার করা হয়" to "",
        "ফিশার হলো মলদ্বারের পাতলা, নরম আবরণে হওয়া একটি ছোট ছিঁড়ে যাওয়া বা ফাটল। এটি সাধারণত শক্ত বা বড় মল ত্যাগের কারণে হয়, যা মলদ্বারের নালিকে তার স্বাভাবিক ক্ষমতার চেয়ে বেশি টেনে ধরে। এই ফাটলের কারণে ভেতরের সংবেদনশীল টিস্যু ও মাংসপেশি উন্মুক্ত হয়ে যায়, ফলে মলত্যাগের সময় ও পরে তীব্র কাটা কাটা ব্যথা হয় যা কয়েক মিনিট থেকে কয়েক ঘণ্টা পর্যন্ত থাকতে পারে। দীর্ঘদিনের ফিশারে চারপাশের পেশিতে খিঁচুনি হতে পারে, যার ফলে সঠিক যত্ন ছাড়া সারতে দেরি হয়" to "",
        "অর্শ (পাইলস) হলো মলদ্বার ও মলদ্বারের নিচের অংশের শিরাগুলো ফুলে যাওয়া একটি সমস্যা। বারবার চাপ পড়া বা কোষ্ঠকাঠিন্যের কারণে এই শিরাগুলো ধীরে ধীরে ফুলে ওঠে। অর্শ দুই রকম হতে পারে — অভ্যন্তরীণ (ভেতরের দিকে, সাধারণত ব্যথাহীন কিন্তু রক্ত পড়তে পারে) এবং বাহ্যিক (মলদ্বারের চারপাশে চামড়ার নিচে, প্রায়ই ব্যথা ও চুলকানি সহ)। অবহেলা করলে ধীরে ধীরে সমস্যা বাড়তে থাকে, তাই প্রথম দিকেই আয়ুর্বেদিক চিকিৎসা নেওয়া জরুরি" to "",
        /* 🆕🔒 V566 (২২.০৮.২০২৬) — পাহারাদারের যাচাই ৯.১৪-এ ধরা পড়া বাকি সব
           বাংলা লেখার ইংরেজি। এর মধ্যে আছে V554–V558-এ যোগ হওয়া চেক-আপ পর্দার
           (কাগজের ভাগ ২–৬) লেখা, V562-এর RMP কমিশনের লাল লাইন, V566-এর "অসময়"
           চিহ্ন, আর হাজিরা/রেজিস্ট্রেশনের কয়েকটা পুরোনো বার্তা।
           ⛔ শুধু বাংলা-বন্ধ (Kishanganj) স্টাফের **পর্দায় কী দেখাবে** সেটুকুই —
              জমা হওয়া তথ্য, টাকার হিসাব, কোনো নিয়ম একটুও বদলায়নি (সারি B158)। */
        // — হাজিরা (AttendanceRepository-তে নিজস্ব তালিকাও আছে, এখানে একই ইংরেজি) —
        "আপনার প্রোফাইল পাওয়া যায়নি। মাস্টারকে জানান।" to "Your profile was not found. Please inform the Master.",
        "আজ আপনার ছুটি অনুমোদিত — হাজিরা লাগবে না।" to "Your leave for today is approved - no attendance needed.",
        "হাজিরা বসানো গেল না। আবার চেষ্টা করুন।" to "Attendance could not be marked. Please try again.",
        "এই অ্যাকাউন্টে হাজিরার ব্যবস্থা নেই।" to "Attendance is not used for this account.",
        "আজ আগেই হাজিরা হয়েছে — " to "Attendance was already marked today - ",
        "। দিনে একবারই দেওয়া যায়।" to ". It can only be given once a day.",
        // — রেজিস্ট্রেশনের একই-নম্বর সতর্কতা (মাঝখানে সংখ্যা/নাম বসে, তাই টুকরো ধরে) —
        "এই মোবাইল নম্বরটা ইতিমধ্যে " to "This mobile number is already registered to ",
        " জন রোগী আছেন:\n" to " more patients:\n",
        "নতুন আলাদা রোগী?" to "A new, separate patient?",
        "এই নম্বরে আরও " to "This number has ",
        "হ্যাঁ চাপলে সম্পূর্ণ নতুন একজন রোগী তৈরি হবে — পুরোনো রোগীর " to "Pressing Yes will create a completely new patient - the old patient's ",
        "কোনো তথ্য বদলাবে না, আর নতুন রোগীর নিজের Visit Fee কাটবে।" to "data will not change, and the new patient's own Visit Fee will apply.",
        "\" কি সত্যিই অন্য একজন রোগী (যেমন স্বামী / স্ত্রী / পরিবারের অন্য কেউ)?" to "\" - is this really a different patient (husband / wife / another family member)?",
        "-এর নামে আছে।\n\n" to " .\n\n",
        "হ্যাঁ, আলাদা রোগী" to "Yes, separate patient",
        "একজন রোগীর" to "another patient",
        // — V562: RMP কমিশন আলাদা লাল লাইনে · V566: অসময়ের রোগীর চিহ্ন —
        "দিতে হবে (মোট)" to "To be paid (total)",
        "অসময়" to "Off-hours",
        // — V554–V558: চেক-আপ পর্দা, কাগজের ভাগ ২–৬ —
        "ছবি দেখিয়ে রোগীকে যা বোঝালেন, দরকার হলে এখানে লিখুন" to "What you explained to the patient with the picture - write it here if needed",
        "এই ইতিহাস নিয়ে আর কিছু লেখার থাকলে এখানে লিখুন" to "If there is more to note about this history, write it here",
        "রোগের ছবি · রোগীকে দেখিয়ে বোঝানোর জন্য" to "Disease picture - to show and explain to the patient",
        "রোগী নিজে যা যা বললেন — এখানে টাইপ করুন" to "What the patient said in their own words - type here",
        "এছাড়া অন্য কিছু থাকলে এখানে লিখুন" to "If there is anything else, write it here",
        "আঙুল দিয়ে দেখে আর যা পাওয়া গেল" to "Other findings from the finger examination",
        "রোগী এসে প্রথমে কি কি বললেন?" to "What did the patient say first?",
        "Patient Said · রোগী যা বললেন" to "Patient Said",
        "অন্যান্য থাকলে এখানে লিখুন" to "Write anything else here",
        "B. আঙুল দিয়ে দেখে · DRE" to "B. Finger examination - DRE",
        "দৈনিক জল পানের পরিমাণ" to "Daily water intake",
        "কতদিন সময় চাওয়া হল?" to "How much time was asked for?",
        "রোগীর বলা ইতিহাস" to "History as told by the patient",
        "সম্ভাব্য কি রোগ?" to "Probable disease?",
        "এখানে টাইপ করুন" to "Type here",
        "রোগ ও অভ্যাস" to "Conditions and habits",
        "লিটার" to "litre",
        "হাইড্রোসিল হলো অণ্ডকোষের চারপাশের পাতলা থলিতে স্বচ্ছ তরল জমে ফোলা তৈরি হওয়ার একটি সমস্যা। প্রাপ্তবয়স্কদের ক্ষেত্রে এটি সাধারণত ব্যথাহীন হয়, তবে দিনের শেষে বা দীর্ঘক্ষণ দাঁড়িয়ে থাকার পরে ভারী লাগা বা টানটান অস্বস্তি অনুভূত হতে পারে। হাইড্রোসিল কোনো স্পষ্ট কারণ ছাড়াই হতে পারে, অথবা সামান্য আঘাত, সংক্রমণ বা প্রদাহের পরে দেখা দিতে পারে — সঠিক পরীক্ষা করলে অণ্ডকোষ ফোলার অন্য কারণগুলো বাদ দেওয়া যায়" to "",
    )

    /**
     * 🔵 V575 — **ডাক্তার চেক-আপের লেখা হিন্দিতে** (TK-নির্দেশ ২৩.০৮.২০২৬)।
     * ------------------------------------------------------------------
     * TK: *"কিষানগঞ্জ all Staff এর কাছে যেন হিন্দি তে থাকে … আমি তো আপনাকে
     * ডাক্তার চেকাপে হিন্দিতে রাখতে বললাম"*। TK-এর বাছাই: **শুধু কিষানগঞ্জের
     * Staff-এর কাছে**, এবং **শুধু পর্দা** — ছাপার A4 কাগজ আগের মতোই থাকবে।
     *
     * কী বদলাল: চেক-আপের লেবেলগুলো কোডে লেখা আছে "English · বাংলা" ধাঁচে।
     * আগে কিষানগঞ্জ Staff-এর পর্দায় " · বাংলা" অংশটা **মুছে** দেওয়া হত
     * (নিচের MAP-এ ওগুলোর মান ফাঁকা "" ছিল)। এখন মোছার বদলে ওখানে **হিন্দি**
     * বসে — অর্থাৎ "Chief Complaint · मुख्य समस्या"।
     *
     * ⛔ এই তালিকায় **শুধু চেক-আপের নিজস্ব লেখা** — তাই অন্য কোনো পর্দার
     *    লেখা বদলায় না, আগের মতোই ইংরেজি থাকে (TK-এর বাছাই অনুযায়ী)।
     * ⛔ কে দেখবে তার নিয়ম (`refresh()`) এক অক্ষরও বদলায়নি।
     * ⛔ ডেটাবেস · টাকার হিসাব · ডিজাইন কিছুই ছোঁয়া হয়নি।
     * ⚠️ ওয়েবের `app.js` (WLV1_NOBN_HI) এই একই তালিকার যমজ — একটাই উৎস
     *    থেকে বানানো, তাই দুই জায়গায় দু-রকম হতে পারে না।
     */
    private val HINDI: Map<String, String> = mapOf(
        "\nমৌলিক বৃত্তান্ত" to "\nबुनियादी विवरण",
        "\nপ্রধান অভিযোগ" to "\nमुख्य शिकायत",
        "\nকতদিন থেকে" to "\nकब से",
        "\nপেশা" to "\nपेशा",
        "\nপূর্ববর্তী চিকিৎসার বৃত্তান্ত" to "\nपिछले इलाज का विवरण",
        "\nপূর্বে নেওয়া চিকিৎসা" to "\nपहले लिया गया इलाज",
        "\nপূর্বের ফলাফল" to "\nपिछला नतीजा",
        "\nআগের খরচ" to "\nपिछला ख़र्च",
        "\nচিকিৎসার মেয়াদ" to "\nइलाज की अवधि",
        "\nক্লিনিক্যাল ফলাফল" to "\nक्लिनिकल नतीजे",
        "\nদৃশ্যমান পরীক্ষা" to "\nआँखों से जाँच",
        "\nঅন্যান্য" to "\nअन्य",
        "\nডিআরই পরীক্ষা" to "\nडीआरई जाँच",
        "\nপ্রক্টোস্কোপি গ্রেড" to "\nप्रोक्टोस्कोपी ग्रेड",
        "\nঅন্যান্য ফলাফল" to "\nअन्य नतीजे",
        "\nপরীক্ষা-নিরীক্ষা" to "\nजाँच-पड़ताल",
        "\nপরামর্শ ও উপদেশ" to "\nसलाह और परामर्श",
        "\nরোগের ব্যাখ্যা দেওয়া হয়েছে" to "\nरोग की जानकारी दी गई",
        "\nআর্থিক আলোচনা" to "\nख़र्च की बातचीत",
        "\nআনুমানিক খরচ" to "\nअनुमानित ख़र्च",
        "\nআনুমানিক সুস্থ হওয়ার সময়" to "\nठीक होने में अनुमानित समय",
        "\nঅগ্রিম আলোচনা" to "\nअग्रिम की बातचीत",
        "\nরোগীর সিদ্ধান্ত" to "\nमरीज़ का फ़ैसला",
        "\nঅন্যান্য / মন্তব্য" to "\nअन्य / टिप्पणी",
        "\nছবি ও নথি" to "\nतस्वीर और काग़ज़ात",
        "\nআগের ছবি" to "\nपहले की तस्वीर",
        "\nচলাকালীন ছবি" to "\nइलाज के दौरान की तस्वीर",
        "\nপরের ছবি" to "\nबाद की तस्वीर",
        "\nনথি / রিপোর্ট নোট" to "\nकाग़ज़ात / रिपोर्ट नोट",
        "\nদ্রুত কাজ" to "\nतुरंत काम",
        " · নথি / রিপোর্ট নোট" to " · काग़ज़ात / रिपोर्ट नोट",
        " · দ্রুত কাজ" to " · तुरंत काम",
        " · রোগীর বিবরণ" to " · मरीज़ का विवरण",
        "\nবর্তমান রোগের ইতিহাস ও পূর্ববর্তী চিকিৎসার বিবরণ" to "\nमौजूदा रोग का इतिहास और पिछले इलाज का विवरण",
        "\nপ্রধান সমস্যা" to "\nमुख्य समस्या",
        "\nহঠাৎ হয় নাকি ধীরে ধীরে বাড়ে" to "\nअचानक हुआ या धीरे-धीरे बढ़ा",
        "\nআগের চিকিৎসার বিবরণ" to "\nपिछले इलाज का विवरण",
        "\nআগের চিকিৎসার ফলাফল" to "\nपिछले इलाज का नतीजा",
        "\nআগের চিকিৎসার খরচ" to "\nपिछले इलाज का ख़र्च",
        "\nচিকিৎসা কত দিন চলেছিল" to "\nइलाज कितने दिन चला",
        " · প্রধান সমস্যা" to " · मुख्य समस्या",
        " · কতদিন থেকে" to " · कब से",
        " · হঠাৎ হয় নাকি ধীরে ধীরে বাড়ে" to " · अचानक हुआ या धीरे-धीरे बढ़ा",
        " · পেশা" to " · पेशा",
        " · আগের চিকিৎসার বিবরণ" to " · पिछले इलाज का विवरण",
        " · আগের চিকিৎসার ফলাফল" to " · पिछले इलाज का नतीजा",
        " · প্রক্টোস্কোপি গ্রেড" to " · प्रोक्टोस्कोपी ग्रेड",
        " · অন্যান্য ফলাফল" to " · अन्य नतीजे",
        " · আগের চিকিৎসার খরচ" to " · पिछले इलाज का ख़र्च",
        " · চিকিৎসা কত দিন চলেছিল" to " · इलाज कितने दिन चला",
        " · অন্যান্য" to " · अन्य",
        " · ডিআরই পরীক্ষা" to " · डीआरई जाँच",
        " · প্রোব পরীক্ষা" to " · प्रोब जाँच",
        " · পরীক্ষা-নিরীক্ষা" to " · जाँच-पड़ताल",
        "\nফিস্টুলার মুখ" to "\nफ़िस्टुला का मुँह",
        "\nরক্ত পড়ে" to "\nख़ून आता है",
        "\nবাহ্যিক অর্শ" to "\nबाहरी बवासीर",
        "\nঅভ্যন্তরীণ অর্শ" to "\nभीतरी बवासीर",
        "\nফিসার" to "\nफ़िशर",
        "\nফোলাভাব" to "\nसूजन",
        "\nকোমলতা/ব্যথা" to "\nछूने पर दर्द",
        "\nফিস্টুলা" to "\nफ़िस्टुला",
        "/ প্রতি অর্শ" to "/ प्रति बवासीर",
        "/ ফিস্টুলা প্রতি ইঞ্চি" to "/ फ़िस्टुला प्रति इंच",
        "/ প্রতি ক্ষারসূত্র" to "/ प्रति क्षारसूत्र",
        "/ মেশিনের মাধ্যমে চিকিৎসা" to "/ मशीन से इलाज",
        "\nপ্রোব পরীক্ষা" to "\nप्रोब जाँच",
        "\nকীভাবে চিকিৎসা করা হবে" to "\nइलाज कैसे किया जाएगा",
        " · কীভাবে চিকিৎসা করা হবে" to " · इलाज कैसे किया जाएगा",
        "\nআনুমানিক খরচ ও রোগীর সিদ্ধান্ত" to "\nअनुमानित ख़र्च और मरीज़ का फ़ैसला",
        "\nআনুমানিক কতদিন বলা হল" to "\nअनुमानित कितने दिन बताया गया",
        "\nঅগ্রিম কত টাকা জমা করতে চাইছে" to "\nअग्रिम कितने रुपये जमा करना चाहते हैं",
        "\nরোগীর বা রোগী পক্ষের সিদ্ধান্ত" to "\nमरीज़ या परिजन का फ़ैसला",
        " · আনুমানিক খরচ" to " · अनुमानित ख़र्च",
        " · আনুমানিক কতদিন বলা হল" to " · अनुमानित कितने दिन बताया गया",
        " · অগ্রিম কত টাকা জমা করতে চাইছে" to " · अग्रिम कितने रुपये जमा करना चाहते हैं",
        " · রোগীর বা রোগী পক্ষের সিদ্ধান্ত" to " · मरीज़ या परिजन का फ़ैसला",
        " · অন্যান্য / মন্তব্য" to " · अन्य / टिप्पणी",
        "\nছবি ও ভিডিও" to "\nतस्वीर और वीडियो",
        "\nঅন্যান্য চিকিৎসার কথা (টাইপ করুন)" to "\nअन्य इलाज की बात (टाइप करें)",
        " · বৃত্তান্ত" to " · विवरण",
        " · পূর্ববর্তী" to " · पिछला",
        " · ক্লিনিক্যাল" to " · क्लिनिकल",
        " · পরামর্শ" to " · सलाह",
        " · আর্থিক" to " · ख़र्च",
        " · সিদ্ধান্ত" to " · फ़ैसला",
        " · ছবি" to " · तस्वीर",
        " · বাহ্যিক অর্শ" to " · बाहरी बवासीर",
        " · ফিসার" to " · फ़िशर",
        " · ফিস্টুলার মুখ" to " · फ़िस्टुला का मुँह",
        " · রক্ত পড়ে" to " · ख़ून आता है",
        " · ফোলাভাব" to " · सूजन",
        " · কোমলতা/ব্যথা" to " · छूने पर दर्द",
        " · অভ্যন্তরীণ অর্শ" to " · भीतरी बवासीर",
        " · ফিস্টুলা" to " · फ़िस्टुला",
        " · এমআরআই" to " · एमआरआई",
        " · আল্ট্রাসাউন্ড" to " · अल्ट्रासाउंड",
        " · কোলোনোস্কোপি" to " · कोलोनोस्कोपी",
        " · ল্যাব রিপোর্ট" to " · लैब रिपोर्ट",
        " · গ্রেড I" to " · ग्रेड I",
        " · গ্রেড II" to " · ग्रेड II",
        " · গ্রেড III" to " · ग्रेड III",
        " · গ্রেড IV" to " · ग्रेड IV",
        " · চিকিৎসায় রাজি" to " · इलाज के लिए राज़ी",
        " · রাজি নয়" to " · राज़ी नहीं",
        " · ভেবে দেখব" to " · सोचकर बताएँगे",
        " · পরিবারের সাথে আলোচনা" to " · परिवार से बात करेंगे",
        " · আর্থিক সমস্যা" to " · पैसों की दिक़्क़त",
        "চোখে দেখা পরীক্ষা" to "आँखों से जाँच",
        "চিকিৎসা পরিকল্পনা" to "इलाज की योजना",
        "রোগের ছবি · রোগীকে দেখিয়ে বোঝানোর জন্য" to "रोग की तस्वीर · मरीज़ को दिखाकर समझाने के लिए",
        "রোগী নিজে যা যা বললেন — এখানে টাইপ করুন" to "मरीज़ ने ख़ुद जो कुछ कहा — यहाँ टाइप करें",
        "আঙুল দিয়ে দেখে আর যা পাওয়া গেল" to "उँगली से जाँच में और जो मिला",
        "রোগী এসে প্রথমে কি কি বললেন?" to "मरीज़ ने आकर पहले क्या-क्या कहा?",
        "Patient Said · রোগী যা বললেন" to "Patient Said · मरीज़ ने जो कहा",
        "B. আঙুল দিয়ে দেখে · DRE" to "B. उँगली से जाँच · DRE",
        "দৈনিক জল পানের পরিমাণ" to "रोज़ाना पानी पीने की मात्रा",
        "কতদিন সময় চাওয়া হল?" to "कितने दिन का समय माँगा गया?",
        "রোগীর বলা ইতিহাস" to "मरीज़ का बताया इतिहास",
        "সম্ভাব্য কি রোগ?" to "संभावित रोग क्या है?",
        "রোগ ও অভ্যাস" to "रोग और आदतें",
        "এই ইতিহাস নিয়ে আর কিছু লেখার থাকলে এখানে লিখুন" to "इस इतिहास के बारे में और कुछ लिखना हो तो यहाँ लिखें",
        "এছাড়া অন্য কিছু থাকলে এখানে লিখুন" to "इसके अलावा और कुछ हो तो यहाँ लिखें",
        "অন্যান্য থাকলে এখানে লিখুন" to "और कुछ हो तो यहाँ लिखें",
        "Clinical পরীক্ষা" to "Clinical जाँच",
        "Fistula Per ইঞ্চি" to "Fistula Per इंच",
        "ফিস্টুলা প্রতি সেমি" to "फ़िस्टुला प्रति सेमी",
        "মেশিন দিয়ে কাজ হবে · Machine Treatment" to "मशीन से काम होगा · Machine Treatment",
        "Machine Treatment/ মেশিনে চিকিৎসা" to "Machine Treatment / मशीन से इलाज",
        "LIS/ এলআইএস চিকিৎসা" to "LIS / एलआईएस इलाज",
        "Injection/ ইনজেকশন চিকিৎসা" to "Injection / इंजेक्शन इलाज",
        "উপরে টিক মারার বাইরেও যদি রোগীকে অন্য কিছু বলে থাকেন তাহলে সেই কথা এখানে লিখুন, অন্যথায় " to "ऊपर टिक लगाने के अलावा भी अगर मरीज़ को कुछ और बताया हो तो वह बात यहाँ लिखें, वरना ",
        "ভগন্দর (ফিস্টুলা) হলো মলদ্বারের ভেতরের অংশ ও তার পাশের চামড়ার মধ্যে তৈরি হওয়া একটি অস্বাভাবিক নালি। এটি সাধারণত মলদ্বারের কোনো ফোঁড়া (সংক্রমণের পকেট) সম্পূর্ণ না সারলে তৈরি হয়, যার ফলে একটি নালি থেকে যায় যার মধ্য দিয়ে পুঁজ বা তরল ক্রমাগত বের হতে থাকে। সময়ের সাথে সাথে এই নালি শাখা-প্রশাখায় জটিল হয়ে উঠতে পারে বলে, অর্শ বা ফিশারের চেয়ে ভগন্দরের চিকিৎসায় বেশি পরিকল্পিত পদ্ধতি লাগে — উপযুক্ত ক্ষেত্রে ঐতিহ্যবাহী আয়ুর্বেদিক ক্ষারসূত্র (ওষুধযুক্ত সুতো) পদ্ধতি ব্যবহার করা হয়" to "भगंदर (फ़िस्टुला) मलद्वार के भीतरी हिस्से और उसके पास की चमड़ी के बीच बन जाने वाली एक असामान्य नली है। यह आमतौर पर मलद्वार के किसी फोड़े (संक्रमण की थैली) के पूरी तरह न भरने पर बनती है, जिससे एक नली रह जाती है और उसमें से लगातार पीप या पानी निकलता रहता है। समय के साथ यह नली शाखाओं में बँटकर जटिल हो सकती है, इसलिए बवासीर या फ़िशर के मुक़ाबले भगंदर के इलाज में ज़्यादा सोची-समझी योजना लगती है — उपयुक्त मामलों में पारंपरिक आयुर्वेदिक क्षारसूत्र (औषधियुक्त धागा) पद्धति इस्तेमाल की जाती है",
        "ফিশার হলো মলদ্বারের পাতলা, নরম আবরণে হওয়া একটি ছোট ছিঁড়ে যাওয়া বা ফাটল। এটি সাধারণত শক্ত বা বড় মল ত্যাগের কারণে হয়, যা মলদ্বারের নালিকে তার স্বাভাবিক ক্ষমতার চেয়ে বেশি টেনে ধরে। এই ফাটলের কারণে ভেতরের সংবেদনশীল টিস্যু ও মাংসপেশি উন্মুক্ত হয়ে যায়, ফলে মলত্যাগের সময় ও পরে তীব্র কাটা কাটা ব্যথা হয় যা কয়েক মিনিট থেকে কয়েক ঘণ্টা পর্যন্ত থাকতে পারে। দীর্ঘদিনের ফিশারে চারপাশের পেশিতে খিঁচুনি হতে পারে, যার ফলে সঠিক যত্ন ছাড়া সারতে দেরি হয়" to "फ़िशर मलद्वार की पतली, नरम परत में हुआ एक छोटा चीरा या दरार है। यह आमतौर पर सख़्त या बड़े मल के निकलने से होता है, जो मलद्वार की नली को उसकी सामान्य क्षमता से ज़्यादा खींच देता है। इस दरार से भीतर के संवेदनशील ऊतक और मांसपेशी खुल जाते हैं, जिससे मलत्याग के समय और उसके बाद तेज़ कटने जैसा दर्द होता है जो कुछ मिनट से कुछ घंटों तक रह सकता है। पुराने फ़िशर में आसपास की मांसपेशी में ऐंठन आ सकती है, जिससे सही देखभाल के बिना भरने में देर लगती है",
        "অর্শ (পাইলস) হলো মলদ্বার ও মলদ্বারের নিচের অংশের শিরাগুলো ফুলে যাওয়া একটি সমস্যা। বারবার চাপ পড়া বা কোষ্ঠকাঠিন্যের কারণে এই শিরাগুলো ধীরে ধীরে ফুলে ওঠে। অর্শ দুই রকম হতে পারে — অভ্যন্তরীণ (ভেতরের দিকে, সাধারণত ব্যথাহীন কিন্তু রক্ত পড়তে পারে) এবং বাহ্যিক (মলদ্বারের চারপাশে চামড়ার নিচে, প্রায়ই ব্যথা ও চুলকানি সহ)। অবহেলা করলে ধীরে ধীরে সমস্যা বাড়তে থাকে, তাই প্রথম দিকেই আয়ুর্বেদিক চিকিৎসা নেওয়া জরুরি" to "बवासीर (पाइल्स) मलद्वार और मलाशय के निचले हिस्से की नसों के फूल जाने की समस्या है। बार-बार ज़ोर लगाने या क़ब्ज़ के कारण ये नसें धीरे-धीरे फूलती जाती हैं। बवासीर दो तरह की होती है — भीतरी (अंदर की ओर, आमतौर पर बिना दर्द के पर ख़ून आ सकता है) और बाहरी (मलद्वार के चारों ओर चमड़ी के नीचे, अक्सर दर्द और खुजली के साथ)। ध्यान न देने पर समस्या धीरे-धीरे बढ़ती जाती है, इसलिए शुरू में ही आयुर्वेदिक इलाज लेना ज़रूरी है",
        "হাইড্রোসিল হলো অণ্ডকোষের চারপাশের পাতলা থলিতে স্বচ্ছ তরল জমে ফোলা তৈরি হওয়ার একটি সমস্যা। প্রাপ্তবয়স্কদের ক্ষেত্রে এটি সাধারণত ব্যথাহীন হয়, তবে দিনের শেষে বা দীর্ঘক্ষণ দাঁড়িয়ে থাকার পরে ভারী লাগা বা টানটান অস্বস্তি অনুভূত হতে পারে। হাইড্রোসিল কোনো স্পষ্ট কারণ ছাড়াই হতে পারে, অথবা সামান্য আঘাত, সংক্রমণ বা প্রদাহের পরে দেখা দিতে পারে — সঠিক পরীক্ষা করলে অণ্ডকোষ ফোলার অন্য কারণগুলো বাদ দেওয়া যায়" to "हाइड्रोसील अंडकोष के चारों ओर की पतली थैली में साफ़ पानी जमा होकर सूजन बन जाने की समस्या है। बड़ों में यह आमतौर पर बिना दर्द के होती है, पर दिन के आख़िर में या देर तक खड़े रहने के बाद भारीपन या तनाव जैसी बेचैनी महसूस हो सकती है। हाइड्रोसील बिना किसी स्पष्ट कारण के भी हो सकता है, या हल्की चोट, संक्रमण या सूजन के बाद दिख सकता है — सही जाँच करने पर अंडकोष की सूजन के दूसरे कारण अलग किए जा सकते हैं",
    )

}
