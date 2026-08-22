package com.tkbiswas.pilesclinic.modules

import android.content.Context

/**
 * 🟢 B629 (11.08.2026, TK-নির্দেশ): "Salary Due" মনে করানো।
 *
 * যে স্টাফের Salary **enabled** ও **Salary Date (day of month)** দেওয়া আছে,
 * আজকের দিন সেই দিন-বা-তার-পরে এসে গেছে, অথচ **এই মাসের** স্যালারি এখনো
 * পুরো দেওয়া হয়নি — শুধু তাদেরই তালিকা/সংখ্যা ফেরে। Master ও Doctor-এর
 * ঘণ্টায় (BellCounter) ও Briefing-এ মনে করানোর জন্য।
 *
 * ⛔ বিদ্যমান `hr.salary_config` + `hr.salary_payments` (+ নাম/ব্রাঞ্চের জন্য
 *    `hr.staff_profiles`) পড়েই হিসাব — কোনো নতুন টেবিল/SQL নেই।
 * ⛔ ছোট টেক্সট-টেবিল, ছবি টানে না; পড়া ব্যর্থ হলে খালি ফেরে (কিছু ভাঙে না)।
 */
object SalaryReminder {

    data class Due(
        val code: String,
        val name: String,
        val branch: String,
        val amount: Double,
        val salaryDay: String
    )

    fun dueList(context: Context): List<Due> {
        return try {
            ModuleAuth.signInCurrentSession(context)
            val cfg = ModuleAuth.getRows(
                "hr", "salary_config",
                "select=person_code,salary_enabled,salary_amount,salary_date"
            )
            if (cfg.length() == 0) return emptyList()

            val cal = java.util.Calendar.getInstance()
            val cy = cal.get(java.util.Calendar.YEAR)
            val cm = cal.get(java.util.Calendar.MONTH) + 1
            val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val cur = "%04d-%02d".format(cy, cm)

            val payR = ModuleAuth.getRows(
                "hr", "salary_payments",
                "select=person_code,amount,for_month&for_month=eq.$cur"
            )
            val paid = HashMap<String, Double>()
            for (i in 0 until payR.length()) {
                val p = payR.getJSONObject(i)
                val c = p.optString("person_code")
                paid[c] = (paid[c] ?: 0.0) + p.optDouble("amount", 0.0)
            }

            // 🔴 V404 (16.08.2026): `active` ঘরটাও টানা হচ্ছে — বাদ-দেওয়া কর্মীর
            //    নাম যেন আর কখনো "বেতন বাকি"-তে না ওঠে। আগে উঠত: SWAPNA ADHIKARI
            //    কাজ ছেড়ে দেওয়ার পরেও তাঁর নাম প্রতি মাসে দেখানোর কথা ছিল।
            val prof = ModuleAuth.getRows(
                "hr", "staff_profiles",
                "select=person_code,full_name,branch,active"
            )
            val info = HashMap<String, Pair<String, String>>()
            val removedCodes = HashSet<String>()
            for (i in 0 until prof.length()) {
                val pr = prof.getJSONObject(i)
                info[pr.optString("person_code")] = Pair(pr.optString("full_name"), pr.optString("branch"))
                // ⛔ ঘরটা না থাকলে (পুরনো সারি) ডিফল্ট true ⇒ কেউ ভুলে বাদ পড়বে না।
                if (!pr.optBoolean("active", true)) removedCodes.add(pr.optString("person_code"))
            }

            val out = ArrayList<Due>()
            for (i in 0 until cfg.length()) {
                val c = cfg.getJSONObject(i)
                if (!c.optBoolean("salary_enabled", false)) continue
                val amount = c.optDouble("salary_amount", 0.0)
                if (amount <= 0) continue
                val sday = c.optString("salary_date").trim().toIntOrNull() ?: continue
                if (today < sday) continue                        // স্যালারির দিন এখনো আসেনি
                val code = c.optString("person_code")
                if (removedCodes.contains(code)) continue           // 🔴 V404: বাদ-দেওয়া কর্মী
                if ((paid[code] ?: 0.0) >= amount) continue        // এ মাসে দেওয়া হয়ে গেছে
                val nb = info[code] ?: Pair(code, "")
                out.add(Due(code, nb.first.ifBlank { code }, nb.second, amount, sday.toString()))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun dueCount(context: Context): Int = dueList(context).size
}
