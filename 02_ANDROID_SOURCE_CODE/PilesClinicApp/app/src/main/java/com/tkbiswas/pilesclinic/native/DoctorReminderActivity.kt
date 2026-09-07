package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.modules.ModuleUi
import org.json.JSONObject

/**
 * 🔔🔒 V1186 (০৭.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ) — **Doctor Note & Reminder**
 * পর্দা: উপরে চলতি রিমাইন্ডারগুলো (Accept সহ), নিচে নতুন পাঠানোর বোতাম ও
 * **Reminder History**।
 *
 * ⛔ পর্দা আঁকার ধরন প্রকল্পের নিজের `ModuleUi` দিয়েই — নতুন কোনো ছাঁদ নয়।
 * ⛔ টাকার কোনো হিসাব এখানে নেই।
 */
class DoctorReminderActivity : AppCompatActivity() {

    private var user: NativeUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        user = NativeSession.current(this)
        renderList()
    }

    private fun dp(v: Int) = ModuleUi.dp(this, v)

    private fun box(fill: String, stroke: String, radius: Int = 14) =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(android.graphics.Color.parseColor(fill))
            setStroke(dp(1), android.graphics.Color.parseColor(stroke))
        }

    private fun tv(text: String, size: Float, hex: String, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(android.graphics.Color.parseColor(hex))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun dmy(iso: String): String = try {
        val p = iso.take(10).split("-"); p[2] + "/" + p[1] + "/" + p[0]
    } catch (_: Throwable) { iso }

    /** "2026-09-07T18:42:03Z" → "07/09/2026 · 6.42 PM"। চেনা না গেলে যা আছে তাই। */
    private fun stamp(raw: String): String {
        val t = raw.trim()
        if (t.length < 10) return ""
        val d = dmy(t)
        if (t.length < 16) return d
        return try {
            val hh = t.substring(11, 13).toInt()
            val mm = t.substring(14, 16)
            val ap = if (hh >= 12) "PM" else "AM"
            val h12 = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
            "$d  ·  $h12.$mm $ap"
        } catch (_: Throwable) { d }
    }

    private fun time12(hm: String): String {
        val p = hm.trim().split(":")
        if (p.size < 2) return hm
        val h = p[0].toIntOrNull() ?: return hm
        val ap = if (h >= 12) "PM" else "AM"
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "$h12.${p[1]} $ap"
    }

    // ───────────────────────── চলতি তালিকা ─────────────────────────
    private fun renderList() {
        val col = ModuleUi.screen(this, "Doctor Note & Reminder")
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true
        col.addView(ModuleUi.button(this, "New Reminder") { renderCreate() })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(holder)
        holder.addView(ModuleUi.body(this, "Loading..."))
        Thread {
            val rows = DoctorReminderRepository.visibleFor(user)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                holder.removeAllViews()
                if (rows.isEmpty()) {
                    holder.addView(ModuleUi.body(this, "No reminder right now."))
                } else {
                    for (r in rows) holder.addView(card(r, showAccept = true))
                }
            }
        }.start()
        col.addView(ModuleUi.button(this, "Reminder History") { renderHistory() })
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { finish() })
    }

    /** এক রোগী = এক কার্ড (TK-র পাশ-করা প্রুফের হুবহু সাজ)। */
    private fun card(r: JSONObject, showAccept: Boolean): LinearLayout {
        val accepted = r.optString("acceptedAt", "").isNotBlank()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = box("#FFFFFF", "#E7ECEA", 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        wrap.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor(if (accepted) "#0F766E" else "#E0A800"))
            layoutParams = LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT)
        })
        val bd = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        wrap.addView(bd)

        val nm = r.optString("patientName", "").ifBlank { "Patient" }
        val mb = r.optString("patientMobile", "")
        bd.addView(tv(nm + (if (mb.isNotBlank()) "   $mb" else ""), 14.5f, "#0B2B1C", bold = true))
        bd.addView(tv(r.optString("note", ""), 13.5f, "#17212B").apply {
            background = box("#F6FAF7", "#E2EDE6", 11)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        })

        val forName = r.optString("forName", "").ifBlank { "All doctors" }
        val byName = r.optString("byName", "")
        val byBranch = r.optString("byBranch", "")
        bd.addView(tv("For  $forName", 12.5f, "#123E8C", bold = true).apply { setPadding(0, dp(8), 0, 0) })
        bd.addView(tv("By  " + byName + (if (byBranch.isNotBlank()) " · $byBranch" else ""), 12.5f, "#8A5A00", bold = true))

        val rd = r.optString("remindDate", "")
        val rt = r.optString("remindTime", "")
        if (rd.isNotBlank()) {
            bd.addView(tv("Remind on  " + dmy(rd) + (if (rt.isNotBlank()) "  ·  " + time12(rt) else ""),
                12.5f, "#B45309", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        }

        if (accepted) {
            bd.addView(tv("Accepted  ·  " + r.optString("acceptedByName", "") + "  ·  " + stamp(r.optString("acceptedAt", "")),
                12.5f, "#0A7C3F", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        } else if (showAccept && DoctorReminderRepository.canAccept(r, user)) {
            bd.addView(ModuleUi.button(this, "Accept") {
                val id = r.optString("id", "")
                Thread {
                    val ok = DoctorReminderRepository.accept(id, user)
                    runOnUiThread {
                        ModuleUi.toast(this, if (ok) "Accepted" else "Failed — check the network")
                        if (ok) renderList()
                    }
                }.start()
            })
        } else if (showAccept) {
            bd.addView(tv("Not accepted yet", 12.5f, "#8A5A00", bold = true).apply { setPadding(0, dp(7), 0, 0) })
        }
        return wrap
    }

    // ───────────────────────── History ─────────────────────────
    private fun renderHistory() {
        val col = ModuleUi.screen(this, "Reminder History")
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(holder)
        holder.addView(ModuleUi.body(this, "Loading..."))
        Thread {
            val rows = DoctorReminderRepository.visibleFor(user, historyMode = true)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                holder.removeAllViews()
                if (rows.isEmpty()) holder.addView(ModuleUi.body(this, "Nothing yet."))
                else for (r in rows) holder.addView(historyCard(r))
            }
        }.start()
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { renderList() })
    }

    /** TK-র পাশ-করা প্রুফ — কে পাঠাল · কবে · কাকে · কবে Accept, প্রতিটা নিজের ঘরে। */
    private fun historyCard(r: JSONObject): LinearLayout {
        val accepted = r.optString("acceptedAt", "").isNotBlank()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = box("#FFFFFF", "#E7ECEA", 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        wrap.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor(if (accepted) "#0F766E" else "#E0A800"))
            layoutParams = LinearLayout.LayoutParams(dp(5), LinearLayout.LayoutParams.MATCH_PARENT)
        })
        val bd = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        wrap.addView(bd)
        bd.addView(tv(r.optString("patientName", "").ifBlank { "Patient" } + "   " + r.optString("patientMobile", ""),
            14f, "#0B2B1C", bold = true))
        bd.addView(tv(r.optString("note", ""), 13f, "#17212B").apply {
            background = box("#F6FAF7", "#E2EDE6", 11)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        })
        fun cell(label: String, value: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = box("#FBFDFC", "#EDF2EF", 11)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { topMargin = dp(7); rightMargin = dp(7) }
            addView(tv(label, 10.5f, "#8B98A9"))
            addView(tv(value.ifBlank { "—" }, 12.5f, "#0B2B1C", bold = true))
        }
        fun pair(l1: String, v1: String, l2: String, v2: String) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(cell(l1, v1)); row.addView(cell(l2, v2))
            bd.addView(row)
        }
        val byName = r.optString("byName", "")
        val byBranch = r.optString("byBranch", "")
        pair("SENT BY", byName + (if (byBranch.isNotBlank()) " · $byBranch" else ""),
            "SENT ON", stamp(r.optString("createdAt", "")))
        pair("SENT TO", r.optString("forName", "").ifBlank { "All doctors" },
            "ACCEPTED", if (accepted) (r.optString("acceptedByName", "") + " · " + stamp(r.optString("acceptedAt", ""))) else "Not yet")
        val rd = r.optString("remindDate", "")
        val rt = r.optString("remindTime", "")
        bd.addView(tv("Remind on  " + dmy(rd) + (if (rt.isNotBlank()) "  ·  " + time12(rt) else "") +
            "        " + (if (accepted) "ACCEPTED" else "WAITING"),
            12.5f, if (accepted) "#0A7C3F" else "#8A5A00", bold = true).apply { setPadding(0, dp(9), 0, 0) })
        return wrap
    }

    // ───────────────────────── নতুন পাঠানো ─────────────────────────
    private var pickedPatient: JSONObject? = null
    private var forMobile = ""
    private var forName = ""
    private var remindDate = ""
    private var remindTime = ""

    private fun renderCreate() {
        pickedPatient = null; forMobile = ""; forName = ""; remindDate = ""; remindTime = ""
        val col = ModuleUi.screen(this, "New Reminder")
        (col.parent as? android.widget.ScrollView)?.isFillViewport = true

        val search = ModuleUi.input(this, "Patient name or mobile")
        col.addView(ModuleUi.label(this, "Patient")); col.addView(search)
        val patLine = tv("No patient chosen", 13.5f, "#8A93A0")
        col.addView(patLine)
        col.addView(ModuleUi.button(this, "Find Patient") {
            val q = search.text.toString().trim()
            if (q.length < 3) { ModuleUi.toast(this, "Type at least 3 letters or digits"); return@button }
            findPatient(q) { p ->
                pickedPatient = p
                patLine.text = p.optString("name", "") + "   " + p.optString("mobile", "")
                patLine.setTextColor(android.graphics.Color.parseColor("#0B2B1C"))
            }
        })

        val note = ModuleUi.input(this, "What to give / what to do")
        col.addView(ModuleUi.label(this, "Note")); col.addView(note)

        val whoLine = tv("All doctors", 13.5f, "#0B2B1C")
        col.addView(ModuleUi.label(this, "Send to which doctor?")); col.addView(whoLine)
        col.addView(ModuleUi.button(this, "Choose Doctor") {
            val branch = (pickedPatient?.optString("branch", "") ?: "").ifBlank { user?.branch ?: "" }
            val docs = DoctorReminderRepository.doctorsForBranch(branch)
            val labels = (listOf("All doctors") + docs.map { it.first }).toTypedArray()
            val mobiles = listOf("") + docs.map { it.second }
            val names = listOf("") + docs.map { it.first }
            val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                .setCustomTitle(PremiumAlert.header(this, "Send to which doctor?"))
                .setItems(labels) { _, which ->
                    forMobile = mobiles[which]; forName = names[which]
                    whoLine.text = labels[which]
                }
                .setNegativeButton("Close", null)
                .create()
            dlg.show()
            try { PremiumAlert.paint(dlg) } catch (_: Throwable) { }
        })

        val dateLine = tv("Not chosen", 13.5f, "#8A93A0")
        col.addView(ModuleUi.label(this, "Which day — reminds the day before")); col.addView(dateLine)
        col.addView(ModuleUi.button(this, "Pick Date") {
            val c = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, y, m, d ->
                remindDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                dateLine.text = dmy(remindDate)
                dateLine.setTextColor(android.graphics.Color.parseColor("#0B2B1C"))
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH),
                c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        })
        val timeLine = tv("Not chosen", 13.5f, "#8A93A0")
        col.addView(ModuleUi.label(this, "Time")); col.addView(timeLine)
        col.addView(ModuleUi.button(this, "Pick Time") {
            val c = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(this, { _, h, mi ->
                remindTime = String.format(java.util.Locale.US, "%02d:%02d", h, mi)
                timeLine.text = time12(remindTime)
                timeLine.setTextColor(android.graphics.Color.parseColor("#0B2B1C"))
            }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), false).show()
        })

        col.addView(ModuleUi.label(this, "Reminder by"))
        col.addView(tv((user?.name ?: "") + (if ((user?.branch ?: "").isNotBlank()) " · " + user?.branch else "") + "   (you)",
            13.5f, "#8A5A00", bold = true))

        col.addView(ModuleUi.button(this, "Send") {
            val p = pickedPatient
            if (p == null) { ModuleUi.toast(this, "Choose a patient first"); return@button }
            val n = note.text.toString().trim()
            if (n.isBlank()) { ModuleUi.toast(this, "Write the note"); return@button }
            if (remindDate.isBlank()) { ModuleUi.toast(this, "Pick the date"); return@button }
            ModuleUi.toast(this, "Sending...")
            Thread {
                val ok = DoctorReminderRepository.send(
                    p.optString("id", ""), p.optString("name", ""), p.optString("mobile", ""),
                    p.optString("branch", "").ifBlank { user?.branch ?: "" },
                    n, forMobile, forName, remindDate, remindTime, user
                )
                runOnUiThread {
                    ModuleUi.toast(this, if (ok) "Sent" else "Failed — check the network")
                    if (ok) renderList()
                }
            }.start()
        })
        col.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(ModuleUi.button(this, "Back") { renderList() })
    }

    /** নাম বা নম্বর ধরে রোগী খোঁজা — একটাই সরু পড়া, তারপর বেছে নেওয়া। */
    private fun findPatient(q: String, then: (JSONObject) -> Unit) {
        ModuleUi.toast(this, "Searching...")
        Thread {
            val digitsOnly = q.filter { it.isDigit() }
            val enc = java.net.URLEncoder.encode("*$q*", "UTF-8")
            val filter = if (digitsOnly.length >= 4) "mobile=like.*$digitsOnly*" else "name=ilike.$enc"
            val rows = try {
                SupabaseClient.fetchListSlimOrNull(
                    "patients", filter, 25, "id,name,mobile,branch", order = "name.asc"
                )
            } catch (_: Throwable) { null }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (rows == null || rows.length() == 0) { ModuleUi.toast(this, "No patient found"); return@runOnUiThread }
                val list = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                val labels = list.map {
                    it.optString("name", "") + "  ·  " + it.optString("mobile", "") + "  ·  " + it.optString("branch", "")
                }.toTypedArray()
                val pick = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setCustomTitle(PremiumAlert.header(this, "Choose patient"))
                    .setItems(labels) { _, which -> then(list[which]) }
                    .setNegativeButton("Close", null)
                    .create()
                pick.show()
                try { PremiumAlert.paint(pick) } catch (_: Throwable) { }
            }
        }.start()
    }
}
