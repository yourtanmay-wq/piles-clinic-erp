package com.tkbiswas.pilesclinic.native

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native rebuild -- Password Center (User management).
 *
 * Master-only. Lists every configured user (from StaffDirectory) and lets the
 * Master view/change each one's login password, saved to the live Supabase
 * "usercredentials" table -- the same table login reads via
 * pullUserPasswordsFromCloud()/cloudPasswordForMobile() in app.js. So a password
 * changed here takes effect at the next login, exactly like the WebView.
 *
 * SCOPED LIMITATION (honest): the WebView also merges a local password cache
 * (rk_user_passwords) as a fallback. This native screen writes the authoritative
 * cloud row (usercredentials); the local fallback is a WebView-only convenience
 * and is intentionally not duplicated.
 */
// 🔴🔒 V1510 (TK-রিপোর্ট ১৬.০৯.২০২৬ — "শুধু স্টাফের কোড, নাম নেই কেন") —
// আসল মানুষের নাম (hr.staff_profiles.full_name) মোবাইল মিলিয়ে যোগ করা হলো।
// ⛔ ফাঁকা থাকলে (যেমন ব্রাঞ্চ-শেয়ার লগইন, যার কোনো নির্দিষ্ট মানুষ নেই)
// আগের মতোই শুধু কোড দেখাবে — কিছু ভাঙবে না।
data class UserCredential(val account: StaffAccount, val password: String, val fullName: String = "")

object PasswordCenterModel {

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    fun mob(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    fun buildCredentialRow(account: StaffAccount, password: String, changedByMobile: String): JSONObject {
        val m = mob(account.mobile)
        val now = isoNow()
        return JSONObject()
            .put("id", "cred_$m")
            .put("mobile", m)
            .put("role", account.role)
            .put("name", account.name)
            .put("branch", account.branch)
            .put("password", password)
            // 🔒 V216 (§4, 31.07.2026): নতুন password সেভ করার সময়ই তার fresh
            // PBKDF2 hash বসানো হয় — তাই login hash দিয়েই যাচাই করতে পারে, আর
            // password বদলালে পুরোনো hash কখনো আটকে থাকে না (নতুন save = নতুন hash)।
            // ⛔ plaintext `password`-ও আপাতত রাখা হয় (পুরোনো App version ও web
            //    এখনো plaintext মেলায়) — সব hash-ready হলে আলাদা ধাপে plaintext মুছবে।
            .put("password_hash", if (password.isBlank()) "" else PasswordHasher.hash(password))
            .put("changedBy", changedByMobile)
            .put("createdAt", now)
            .put("updatedAt", now)
    }
}

class PasswordCenterRepository {

    /** Loads every user's current effective password: the cloud value if one
     * exists in "usercredentials", otherwise the role default. */
    fun loadCredentials(): List<UserCredential> {
        val cloud = SupabaseClient.fetchList("usercredentials", null, 500)
        val byMobile = HashMap<String, String>()
        for (i in 0 until cloud.length()) {
            val row = cloud.getJSONObject(i)
            val m = PasswordCenterModel.mob(row.optString("mobile"))
            val pw = row.s("password")
            if (m.isNotBlank() && pw.isNotBlank()) byMobile[m] = pw
        }
        // 🔴🔒 V1510 — hr.staff_profiles থেকে আসল নাম (link_mobile ধরে) --
        // ব্যর্থ হলে ম্যাপ ফাঁকা থাকে, নাম না দেখালেও তালিকা আগের মতোই চলে।
        val nameByMobile = HashMap<String, String>()
        try {
            val profiles = com.tkbiswas.pilesclinic.modules.ModuleAuth.getRows(
                "hr", "staff_profiles", "select=full_name,link_mobile"
            )
            for (i in 0 until profiles.length()) {
                val row = profiles.optJSONObject(i) ?: continue
                val m = PasswordCenterModel.mob(row.optString("link_mobile"))
                val nm = row.optString("full_name").trim()
                if (m.isNotBlank() && nm.isNotBlank()) nameByMobile[m] = nm
            }
        } catch (_: Throwable) { }
        return StaffDirectory.allAccounts().map { acc ->
            val m = PasswordCenterModel.mob(acc.mobile)
            val pw = byMobile[m] ?: StaffDirectory.defaultPasswordFor(acc.role)
            UserCredential(acc, pw, nameByMobile[m] ?: "")
        }
    }

    fun savePassword(account: StaffAccount, newPassword: String, changedByMobile: String): Boolean {
        val row = PasswordCenterModel.buildCredentialRow(account, newPassword, changedByMobile)
        return SupabaseClient.upsert("usercredentials", row)
    }
}
