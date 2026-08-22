package com.tkbiswas.pilesclinic.native

import android.content.Context
import com.tkbiswas.pilesclinic.modules.ModuleAuth
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🕐🔒 V496 (২১.০৮.২০২৬, TK-এর চূড়ান্ত নির্দেশ §৪ ও §৬) — **IN TIME সেভ করা।**
 *
 * অ্যাপ **কিছুই পাঠায় না** — staff_code নয়, branch নয়, তারিখ নয়, সময় নয়।
 * সার্ভারের `wn.mark_check_in()` নিজে সব ঠিক করে (ফাইল:
 * `04_SUPABASE_DATABASE_SETUP/V496_MARK_CHECK_IN_2026-08-21.sql`)।
 *
 * ⛔ অ্যাপ কখনো নিজে সিদ্ধান্ত নেয় না — শুধু সার্ভারের `status` দেখে বার্তা দেখায়।
 * ⛔ ব্যর্থ হলে ফোনে জমিয়ে পরে পাঠানো হয় **না** — সময়টা সার্ভারের হতেই হবে,
 *    নইলে "পরে পাঠানো" মানেই ভুল সময় বসে যাওয়া।
 */
object AttendanceRepository {

    /** সার্ভার যা যা বলতে পারে। */
    enum class Status {
        SAVED,        // এইমাত্র বসল
        ALREADY,      // আজ আগেই ছিল — পুরনো সময় অপরিবর্তিত
        ON_LEAVE,     // আজ অনুমোদিত ছুটি
        NOT_STAFF,    // ডাক্তার/মাস্টার — হাজিরার ব্যবস্থা নেই
        INACTIVE,     // বাদ দেওয়া হয়েছে
        SUSPENDED,    // নির্দিষ্ট তারিখ পর্যন্ত বন্ধ
        NO_PROFILE,
        NETWORK,      // পৌঁছানোই গেল না
        ERROR
    }

    data class Outcome(
        val status: Status,
        val checkIn: String = "",
        val branch: String = "",
        val message: String = ""
    ) {
        val ok: Boolean get() = status == Status.SAVED
    }

    private fun parseStatus(raw: String): Status = when (raw.trim().lowercase()) {
        "saved" -> Status.SAVED
        "already" -> Status.ALREADY
        "on_leave" -> Status.ON_LEAVE
        "not_staff" -> Status.NOT_STAFF
        "inactive" -> Status.INACTIVE
        "suspended" -> Status.SUSPENDED
        "no_profile" -> Status.NO_PROFILE
        else -> Status.ERROR
    }

    /**
     * IN TIME বসানোর চেষ্টা। **নেটওয়ার্ক থ্রেড থেকে ডাকতে হবে।**
     *
     * ⛔ এখানে সব ভুল একটা বড় `catch`-এ চাপা দেওয়া হয়নি — লগইন-সমস্যা,
     *    নেটওয়ার্ক-সমস্যা ও সার্ভারের উত্তর আলাদা করে ধরা হয়েছে।
     */
    fun markCheckIn(context: Context): Outcome {
        // ১) লগইন নিশ্চিত করা (প্রজেক্টের প্রমাণিত পথ — অন্য RPC-গুলোর মতোই)
        if (!ModuleAuth.isSignedIn) {
            val err = try {
                ModuleAuth.signInCurrentSession(context.applicationContext)
            } catch (t: Throwable) {
                return Outcome(Status.NETWORK, message = "লগইন যাচাই করা গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।")
            }
            if (err != null) {
                return Outcome(Status.NETWORK, message = "লগইন যাচাই করা গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।")
            }
        }

        // ২) সার্ভারকে ডাকা — কোনো তথ্য পাঠানো হয় না
        val rpc = try {
            ModuleAuth.rpc("wn", "mark_check_in", JSONObject())
        } catch (t: Throwable) {
            return Outcome(Status.NETWORK, message = "হাজিরা পাঠানো গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।")
        }
        if (!rpc.ok) {
            return Outcome(
                Status.NETWORK,
                message = "হাজিরা এখন সেভ করা গেল না। ইন্টারনেট দেখে আবার চেষ্টা করুন।"
            )
        }

        // ৩) উত্তর পড়া — একটাই সারি
        return try {
            val arr = JSONArray(rpc.body)
            if (arr.length() == 0) {
                Outcome(Status.ERROR, message = "সার্ভার কিছু জানায়নি। আবার চেষ্টা করুন।")
            } else {
                val o = arr.getJSONObject(0)
                Outcome(
                    status = parseStatus(o.optString("status", "")),
                    checkIn = o.optString("check_in", ""),
                    branch = o.optString("branch", ""),
                    message = o.optString("message", "")
                )
            }
        } catch (t: Throwable) {
            Outcome(Status.ERROR, message = "সার্ভারের উত্তর বোঝা গেল না। আবার চেষ্টা করুন।")
        }
    }
}
