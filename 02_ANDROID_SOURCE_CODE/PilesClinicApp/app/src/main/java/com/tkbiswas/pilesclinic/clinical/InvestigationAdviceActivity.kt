package com.tkbiswas.pilesclinic.clinical

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.native.NativeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tkbiswas.pilesclinic.native.UppercaseInputUtil

/** TK APPROVED (2026-07-15): Blood Test / Investigation landing screen —
 *  category grid (Hematology / Bio-Chemistry / Immunology / Special Test /
 *  Urine / Stool / Semen / Imaging) transcribed test-by-test from TK's lab
 *  reference slip, plus two shortcut boxes above it — "Previous Patient
 *  Blood Test" (re-checks whatever was saved last time) and "Common Blood
 *  Test" (a fixed 7-test set TK defined, 01.08.2026) — both open the same
 *  tick/untick + add-extra dialog. Tapping a category opens
 *  InvestigationCategoryActivity for just that category's checklist. All
 *  selections still live in the same shared ClinicalRepository.currentInvestigations
 *  list as before, so Approve/Save/Print/Share keep working exactly as before. */
class InvestigationAdviceActivity : AppCompatActivity() {

    private lateinit var categoryContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClinicalRepository.attachInvestMemory(this)
        setContentView(R.layout.activity_investigation_advice)
        UppercaseInputUtil.applyToAll(window.decorView.findViewById(android.R.id.content))  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        // TK-DECISION (2026-07-22): show the current patient under the title so
        // a Blood Test can't be made for the wrong patient by mistake.
        run {
            // 🔒 খাতার সারি B175 — মানুষ-পড়া-যায় Patient ID।
            val line = listOf(RoleSession.currentPatientName, RoleSession.displayId(), RoleSession.currentPatientDisease)
                .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) {
                supportActionBar?.subtitle = "👤 $line"
                toolbar.setSubtitleTextColor(android.graphics.Color.parseColor("#DDE7F2"))
            }
        }
        // TK-REPORTED BUG FIX (2026-07-16): the title was cut off with "..."
        // because Toolbar titles are single-line by default. Let it wrap to
        // 2 lines instead so the full "Blood Test / Investigation Advice"
        // is always readable.
        // 🔴 TK-REPORTED (02.08.2026, স্ক্রিনশট — "হেডার ঠিক নেই"): ঠিক এই
        // একই কারণে সাবটাইটেলও (রোগীর নাম · ID · রোগ লাইন) কেটে "..." দেখাত —
        // উপরের ফিক্স শুধু title-এর TextView ধরেছিল, subtitle-এর নিজের
        // TextView আলাদা, সেটা তখন ছোঁয়া হয়নি। এখন দুটোই একই নিয়মে wrap হবে।
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView && (child.text == toolbar.title || child.text == toolbar.subtitle)) {
                child.isSingleLine = false
                child.maxLines = 2
                child.ellipsize = null
            }
        }

        categoryContainer = findViewById(R.id.llCategoryContainer)

        val btnSave = findViewById<MaterialButton>(R.id.btnSaveInvestigations)
        val btnSaveAndPrint = findViewById<MaterialButton>(R.id.btnSaveAndPrintInvestigations)
        // TK-REQUESTED REMOVAL (2026-07-16): "MARK SELECTED AS ADVISED
        // (DOCTOR)" and "SHARE AS TEXT" buttons removed — TK decided staff
        // can do this work without a separate Doctor-approval step, and
        // Save & Print already offers sharing, so a separate Share button
        // was redundant. The role notice banner is no longer needed either.
        findViewById<TextView>(R.id.tvRoleNotice).visibility = android.view.View.GONE

        btnSave.setOnClickListener { saveInvestigations(openPrintAfter = false) }
        btnSaveAndPrint.setOnClickListener { saveInvestigations(openPrintAfter = true) }

        // 🔒 TK-এর নির্দেশ (01.08.2026): Share বোতাম — Medicine Slip/Prescription-এর
        // হুবহু একই প্যাটার্ন (plain-text ACTION_SEND)। শুধু এখন যা টিক করা তাই যায়।
        findViewById<MaterialButton>(R.id.btnShareInvestigations).setOnClickListener { shareInvestigations() }

        buildScreen()
    }

    /** 🔒 B277 (02.08.2026): আগে এই লজিকটা শুধু main-screen-এর Share বোতামের
     *  ভিতরেই লেখা ছিল — এখন আলাদা ফাংশনে বার করা হলো যাতে "Common Blood Test"
     *  পপ-আপের নতুন Share বোতামও (TK-এর নির্দেশে) ঠিক এই একই কোড ব্যবহার করে,
     *  আলাদা করে দ্বিতীয়বার লেখা লাগেনি। ⛔ ভিতরের লজিক এক অক্ষরও বদলায়নি। */
    private fun shareInvestigations() {
        val chosen = ClinicalRepository.currentInvestigations.filter { it.isSelected }
        if (chosen.isEmpty()) {
            Toast.makeText(this, "No tests selected yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val line = listOf(RoleSession.currentPatientName, RoleSession.displayId())
            .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" \u00b7 ")
        val shareText = buildString {
            append("Blood Test / Investigation Advice\n")
            if (line.isNotBlank()) append("$line\n")
            append("\n")
            chosen.forEach { append("\u2022 ${it.name}\n") }
            // 🔴 V430 — লেখা থাকলে Advice / Remarks-ও সঙ্গে যায় (কম্পিউটারের মতোই)।
            val rem = invRemarks()
            if (rem.isNotBlank()) append("\nAdvice / Remarks : $rem\n")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Blood Test Advice - ${RoleSession.currentPatientName}")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(sendIntent, "Share Blood Test Advice"))
    }

    /** TK APPROVED (2026-07-15): printing matters more than just saving — added
     *  a "Save & Print" option (same pattern as Prescription/Diet Chart) so the
     *  advice sheet can go straight to the print preview with one tap. */
    /** 🔴 V430 (TK-সিদ্ধান্ত ১৮.০৮.২০২৬) — পর্দার "Advice / Remarks" ঘরের লেখা।
     *  ঘরটা না থাকলেও (পুরনো লেআউট) কিছুই ভাঙে না — ফাঁকা লেখা ফিরে আসে। */
    private fun invRemarks(): String =
        try { findViewById<EditText>(R.id.etInvRemarks)?.text?.toString()?.trim().orEmpty() }
        catch (_: Throwable) { "" }

    private fun saveInvestigations(openPrintAfter: Boolean) {
        val requested = ClinicalRepository.currentInvestigations.filter { it.isSelected }
        if (requested.isEmpty()) {
            Toast.makeText(this, "No tests selected yet.", Toast.LENGTH_SHORT).show()
            return
        }
        // TK APPROVED (2026-07-15): this exact selection becomes the new
        // "Common Blood Test" default for next time.
        ClinicalRepository.saveCommonBloodTest(requested.map { it.name }.toSet())
        val summary = requested.joinToString(", ") { it.name }.take(80)
        ClinicalRepository.addVisit("Investigation Advice", summary, RoleSession.currentRole)
        val selectedStr = requested.joinToString(", ") { it.name }
        // 🔴 V430 — কম্পিউটারের মতোই লেখা মন্তব্যটাও সেভ হওয়া সারিতে থাকে।
        val invRem = invRemarks()
        val createdBy = NativeSession.current(this)?.mobile ?: ""
        val pid = RoleSession.currentPatientId
        val pname = RoleSession.currentPatientName
        // 🔒 TK-এর নিয়ম (28.07.2026): Save চাপার সঙ্গে সঙ্গে প্রিন্ট পর্দা
        // খুলবে — ক্লাউডে পাঠানো শেষ হওয়ার জন্য স্টাফকে বসিয়ে রাখা যাবে না।
        // প্রিন্টের সব তথ্য ফোনেই আছে, ক্লাউডের কিছু লাগে না। সেভটা আগে
        // ফোনেই লেখা হয়, তারপর পিছনে ক্লাউডে যায়; না গেলে অপেক্ষমাণ
        // তালিকায় জমা থেকে নিজে থেকেই আবার যায়, তাই কিছু হারায় না।
        Toast.makeText(this@InvestigationAdviceActivity, "Saved (${requested.size} test/s).", Toast.LENGTH_SHORT).show()
        val appCtx = applicationContext
        com.tkbiswas.pilesclinic.native.BackgroundWork.run {
            ClinicalCloudRepository.saveMedical(appCtx, pid, pname, "Investigation", selectedStr,
                if (invRem.isNotBlank()) invRem else summary, createdBy)
        }
        if (openPrintAfter) {
            /* 🩸🔒 V596 (২৩.০৮.২০২৬, TK-অনুমোদিত ডেমো-ফটো দেখে): Blood Test এখন
               **অনুমোদিত A4 ডিজাইনে** ছাপে — ওয়েবের (`wlv1InvestigationA4`)
               হুবহু একই চেহারা, WebView + PrintManager দিয়ে। তাই ফোন ও
               কম্পিউটারের কাগজ এক। হুবহু যে পথে V390-তে Diet Chart গিয়েছিল।
               ⛔ `ClinicPdfBuilder` (OWNER LOCKED) ছোঁয়া হয়নি — বাকি সব প্রিন্ট
                  আগের পথেই। ⛔ বাছাই/সেভ/ক্লাউড — উপরের কোডে কিচ্ছু বদলায়নি। */
            com.tkbiswas.pilesclinic.print.InvestigationHtmlPrint.print(
                this@InvestigationAdviceActivity, invRemarks())
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh counts in case selections changed inside a category screen.
        if (::categoryContainer.isInitialized && categoryContainer.childCount > 0) refreshCategoryGrid()
    }

    private fun buildScreen() {
        categoryContainer.removeAllViews()
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()

        // ---- 🔒 TK-এর নির্দেশ (01.08.2026): নাম বদলে "Previous Patient Blood
        // Test" — কাজ এক অক্ষরও বদলায়নি (applyCommonBloodTest(): শেষ সেভ করা
        // রোগীর টেস্ট আগে থেকে টিক করা, আনটিক/যোগ করা যায়)। শুধু উচ্চতা কমানো
        // হয়েছে (padding 16dp → 10dp/14dp)। ----
        val commonBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // TK-REPORTED BUG FIX (2026-07-16): the subtitle line was left-
            // aligned instead of centered (only visible because it's shorter
            // than the title line above it). gravity=CENTER_HORIZONTAL here
            // centers both wrap_content child lines within this card.
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                colors = intArrayOf(android.graphics.Color.parseColor("#0EA25F"), android.graphics.Color.parseColor("#0A3B20"))
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(10); layoutParams = lp
        }
        commonBtn.addView(TextView(this).apply {
            text = "⏰  Previous Patient Blood Test"
            textSize = 13.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE); gravity = android.view.Gravity.CENTER
        })
        commonBtn.addView(TextView(this).apply {
            text = "Re-checks whatever was saved last time"
            textSize = 10f; setTextColor(android.graphics.Color.parseColor("#CDEFDC")); gravity = android.view.Gravity.CENTER
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = dp(2); layoutParams = p
        })
        commonBtn.setOnClickListener { applyCommonBloodTest() }
        categoryContainer.addView(commonBtn)

        // ---- 🔒 TK-এর নির্দেশ (01.08.2026): নতুন দ্বিতীয় বক্স — "Common Blood
        // Test", উপরেরটার সম্পূর্ণ আলাদা — রোগী বদলালেও এই ৭টা টেস্টই সবসময়
        // শুরুতে থাকে (আনটিক/যোগ করা যায়, শুধু এই রোগীর জন্য)। ----
        val fixedBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                colors = intArrayOf(android.graphics.Color.parseColor("#1E88C7"), android.graphics.Color.parseColor("#0D3E5C"))
                orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
            }
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(14); layoutParams = lp
        }
        fixedBtn.addView(TextView(this).apply {
            text = "⭐  Common Blood Test"
            textSize = 13.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE); gravity = android.view.Gravity.CENTER
        })
        fixedBtn.addView(TextView(this).apply {
            text = ClinicalRepository.commonBloodTestFixed.joinToString(" \u00b7 ")
            textSize = 10f; setTextColor(android.graphics.Color.parseColor("#D7ECFB")); gravity = android.view.Gravity.CENTER
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = dp(2); layoutParams = p
        })
        fixedBtn.setOnClickListener { applyFixedCommonBloodTest() }
        categoryContainer.addView(fixedBtn)

        // ---- Category grid, 2 columns ----
        val palette = listOf(
            Triple("#FFE3E3", "#FFC9C9", "#7A1F1F"), Triple("#DCEBFF", "#B8D8FF", "#0F3A66"),
            Triple("#DFFBEA", "#B9F0CE", "#0B4F2A"), Triple("#FFF3D1", "#FCE2A1", "#7A5A0A"),
            Triple("#F0E2FF", "#DCC3FA", "#4B1F7A"), Triple("#D8F5F0", "#B3E9E0", "#0A5D50"),
            Triple("#E9EDF3", "#D2D9E4", "#374151"), Triple("#FDE7D6", "#FBCBA0", "#7A3B0A")
        )
        var row: LinearLayout? = null
        ClinicalRepository.investigationCategories.forEachIndexed { index, cat ->
            if (index % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp(10); layoutParams = lp
                }
                categoryContainer.addView(row)
            }
            val (c1, c2, textColor) = palette[index % palette.size]
            val selectedCount = ClinicalRepository.currentInvestigations.count { it.isSelected && cat.tests.contains(it.name) }
            // 🔒 TK-এর নির্দেশ (01.08.2026): "৮টা বক্সের সাইজ ছোট করুন, লেখা
            // বক্সের সাথে সামঞ্জস্যপূর্ণ" — padding ও লেখার মাপ কমানো হলো;
            // চাপ দিলে যা হতো (category screen খোলা) তা এক অক্ষরও বদলায়নি।
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    colors = intArrayOf(android.graphics.Color.parseColor(c1), android.graphics.Color.parseColor(c2))
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(4), 0, dp(4), 0); layoutParams = lp
                isClickable = true; isFocusable = true
            }
            card.addView(TextView(this).apply {
                text = cat.emoji; textSize = 17f; gravity = android.view.Gravity.CENTER
            })
            card.addView(TextView(this).apply {
                text = cat.name; textSize = 11f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(textColor)); gravity = android.view.Gravity.CENTER
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(4); layoutParams = p
            })
            card.addView(TextView(this).apply {
                text = if (selectedCount > 0) "$selectedCount / ${cat.tests.size} selected" else "${cat.tests.size} tests"
                textSize = 9f; setTextColor(android.graphics.Color.parseColor(textColor)); gravity = android.view.Gravity.CENTER
            })
            card.setOnClickListener {
                startActivity(Intent(this, InvestigationCategoryActivity::class.java).apply {
                    putExtra("category_name", cat.name)
                })
            }
            row?.addView(card)
        }
        // if odd number of categories, balance the last row
        if (ClinicalRepository.investigationCategories.size % 2 == 1) {
            row?.addView(android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
        }
    }

    private fun refreshCategoryGrid() = buildScreen()

    /** TK APPROVED (2026-07-15): "Apply Common Blood Test" (now labelled
     *  "Previous Patient Blood Test" on-screen — 01.08.2026) opens an
     *  interactive dialog instead of silently checking boxes in the background
     *  — shows the remembered test set with tick/untick checkboxes (all
     *  pre-checked, staff can uncheck any), plus a text box to type in extra
     *  tests not in the remembered set. Nothing is applied until "Apply" is
     *  tapped. Blank the first time (nothing saved yet) — never guesses. */
    private fun applyCommonBloodTest() {
        val remembered = ClinicalRepository.getCommonBloodTest()
        if (remembered.isEmpty()) {
            Toast.makeText(this, "No common set saved yet — select tests and Save once first.", Toast.LENGTH_SHORT).show()
            return
        }
        showBloodTestChecklistDialog("⏰ Previous Patient Blood Test", remembered.sorted())
    }

    /** 🔒 TK-এর নির্দেশ (01.08.2026): নতুন "Common Blood Test" বক্স — সবসময়
     *  TK-এর দেওয়া ৭টা ফিক্সড টেস্ট দিয়ে শুরু (রোগী বদলালেও বদলায় না),
     *  আনটিক/যোগ করা যায় — শেয়ার্ড ডায়ালগের হুবহু একই আচরণ (Previous
     *  Patient-এর মতোই), শুধু তালিকার উৎস আলাদা। ফাঁকা থাকার প্রশ্নই নেই
     *  (ফিক্সড লিস্ট), তাই "no set saved" চেক এখানে লাগে না। */
    private fun applyFixedCommonBloodTest() {
        showBloodTestChecklistDialog("⭐ Common Blood Test", ClinicalRepository.commonBloodTestFixed)
    }

    /** টিক/আনটিক + নিচে লিখে নতুন টেস্ট যোগ করার পপ-আপ — দুটো বক্স
     *  (Previous Patient · Common Blood Test) এই একই ফাংশন ডাকে, তাই
     *  আচরণ কখনো আলাদা হতে পারবে না। "Apply" চাপার আগ পর্যন্ত কিছুই সেভ হয় না।
     *  🔒 TK-এর নির্দেশ (02.08.2026, স্ক্রিনশটসহ — "সম্পূর্ণ প্রজেক্টে এই
     *  চেহারা এক থাকতে হবে"): এই তালিকার প্রতিটা সারিও এখন
     *  InvestigationCategoryActivity/PrintCenterActivity-এর হুবহু একই
     *  গোল টিক-বৃত্ত কার্ড ডিজাইনে, প্লেইন CheckBox নয় — একই স্ক্রিনের
     *  ভিতরেও যেন দুই রকম চেহারা না থাকে। */
    private fun showBloodTestChecklistDialog(title: String, names: List<String>) {
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()
        fun roundedBg(fill: String, stroke: String, radius: Int): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = radius.toFloat()
                setColor(android.graphics.Color.parseColor(fill))
                setStroke(2, android.graphics.Color.parseColor(stroke))
            }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(4))
        }
        container.addView(TextView(this).apply {
            text = "Untick anything you don't need this time, or type more tests below."
            textSize = 12.5f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, 0, 0, dp(10))
        })

        // TK-এর নিয়ম মেনে "Apply" চাপার আগ পর্যন্ত কিছুই চূড়ান্ত হয় না —
        // তাই আগের CheckBox-দের বদলে `checkedNow` (স্থানীয় mutable set)
        // দিয়ে ট্র্যাক করা হয়, ঠিক Print Center-এর একই প্যাটার্নে।
        val checkedNow = names.toMutableSet()
        val rowNames = mutableListOf<String>()
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun addRow(name: String) {
            rowNames.add(name)
            var isChecked = checkedNow.contains(name)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(if (isChecked) "#EAFBF0" else "#FFFFFF", if (isChecked) "#0B4F2A" else "#E1E6ED", dp(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8); layoutParams = lp
                isClickable = true; isFocusable = true
            }
            val circle = TextView(this).apply {
                text = if (isChecked) "✓" else ""
                textSize = 13f; gravity = android.view.Gravity.CENTER; setTextColor(android.graphics.Color.WHITE)
                background = roundedBg(if (isChecked) "#0B4F2A" else "#FFFFFF", "#B7C0CE", dp(20))
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val label = TextView(this).apply {
                text = name; textSize = 13.5f
                setTextColor(android.graphics.Color.parseColor(if (isChecked) "#0B4F2A" else "#10223A"))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(12); layoutParams = lp
            }
            row.addView(circle); row.addView(label)
            row.setOnClickListener {
                isChecked = !isChecked
                if (isChecked) checkedNow.add(name) else checkedNow.remove(name)
                circle.text = if (isChecked) "✓" else ""
                circle.background = roundedBg(if (isChecked) "#0B4F2A" else "#FFFFFF", "#B7C0CE", dp(20))
                label.setTextColor(android.graphics.Color.parseColor(if (isChecked) "#0B4F2A" else "#10223A"))
                row.background = roundedBg(if (isChecked) "#EAFBF0" else "#FFFFFF", if (isChecked) "#0B4F2A" else "#E1E6ED", dp(12))
            }
            listContainer.addView(row)
        }
        names.forEach { addRow(it) }
        // Both the remembered checklist AND any newly-typed extra tests live in
        // this SAME bounded scroll area (one scroll region, not nested), so the
        // Add row below always stays reachable no matter how many are added.
        val scroll = android.widget.ScrollView(this).apply {
            addView(listContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)
            )
        }
        container.addView(scroll)

        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = dp(10); layoutParams = p
        }
        val addInput = android.widget.EditText(this).apply {
            hint = "Type another test name"
            // 🔴 TK-REQUESTED (02.08.2026, ছবিতে গোল করে দেখানো): এই বাক্সটা
            // উপরের টেস্ট-সারিগুলোর (HB/SUGAR/HIV ইত্যাদি) হুবহু একই সাইজ/
            // গোল-কোণা/বর্ডারে থাকবে, শুধু ভিতরটা সাদা (আনচেকড সারির রং)।
            // আগে `bg_input_field` (আলাদা ছোট, ভিন্ন প্যাডিং) ব্যবহার হত।
            background = roundedBg("#FFFFFF", "#E1E6ED", dp(12))
            textSize = 13.5f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) }
        }
        val addBtn = MaterialButton(this).apply {
            text = "+ Add"
            setBackgroundResource(R.drawable.bg_btn_navy)
            setTextColor(android.graphics.Color.WHITE)
        }
        addRow.addView(addInput)
        addRow.addView(addBtn)
        container.addView(addRow)

        addBtn.setOnClickListener {
            val name = addInput.text.toString().trim()
            if (name.isBlank()) return@setOnClickListener
            if (rowNames.any { it.equals(name, ignoreCase = true) }) {
                Toast.makeText(this, "Already in the list.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkedNow.add(name)
            addRow(name)
            addInput.setText("")
        }

        UppercaseInputUtil.applyToAll(container)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
        // 🔴 B277 (02.08.2026, TK-এর স্পষ্ট নির্দেশ, ছবিতে গোল করে দেখানো):
        // আগে এখানে শুধু "Cancel"/"Apply" ছিল — এখন মূল পাতার SAVE/SHARE/PRINT-এর
        // মতোই তিনটে বোতাম, প্রতিটা সত্যিকারের কাজ করে:
        //  · Share — টিক করা তালিকা সঙ্গে সঙ্গে Apply হয়ে যায়, তারপর ঠিক
        //    main-screen-এর Share বোতামের মতোই শেয়ার করে (shareInvestigations())।
        //  · Print — একইভাবে Apply হয়ে যায়, তারপর ঠিক main-screen-এর
        //    "Save & Print"-এর মতোই সেভ করে প্রিন্ট পর্দা খোলে (saveInvestigations
        //    (openPrintAfter = true))।
        //  · Cancel — আগের মতোই শুধু বন্ধ, কিছু Apply হয় না, পিছনের পাতায় ফেরে।
        // ⛔ "Apply"-এর ভিতরের আসল লজিক (টিক করা টেস্ট `currentInvestigations`-এ
        //    বসানো) এক অক্ষরও বদলায়নি — শুধু এখন একই লজিক দুইটা বোতাম থেকেই
        //    (Share ও Print) ডাকা হয়, তারপর যার যার নিজের কাজ।
        fun applyChosenNow(): Boolean {
            val chosen = rowNames.filter { checkedNow.contains(it) }
            if (chosen.isEmpty()) {
                Toast.makeText(this, "Nothing selected.", Toast.LENGTH_SHORT).show()
                return false
            }
            chosen.forEach { name ->
                val existing = ClinicalRepository.currentInvestigations.find { it.name == name }
                if (existing != null) {
                    existing.isSelected = true
                } else {
                    ClinicalRepository.currentInvestigations.add(InvestigationEntry(name = name, isSelected = true))
                }
            }
            refreshCategoryGrid()
            return true
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(com.tkbiswas.pilesclinic.native.PremiumAlert.header(this, title))
            .setView(container)
            .setPositiveButton("Print") { _, _ ->
                if (applyChosenNow()) saveInvestigations(openPrintAfter = true)
            }
            .setNeutralButton("Share") { _, _ ->
                if (applyChosenNow()) shareInvestigations()
            }
            .setNegativeButton("Cancel", null)
            .show().also { com.tkbiswas.pilesclinic.native.PremiumAlert.paint(it) }
    }
}
