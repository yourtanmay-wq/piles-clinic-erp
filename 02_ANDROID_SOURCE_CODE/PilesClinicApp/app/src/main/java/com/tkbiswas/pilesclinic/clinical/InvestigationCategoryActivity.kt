package com.tkbiswas.pilesclinic.clinical

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.native.UppercaseInputUtil

/** TK APPROVED (2026-07-15): shows the checklist for exactly one Blood Test
 *  category (Hematology / Bio-Chemistry / etc.), opened by tapping a category
 *  card on InvestigationAdviceActivity. Reads/writes the same shared
 *  ClinicalRepository.currentInvestigations list, so nothing else (Approve,
 *  Save, Print, PrintMappers.investigationAdvice) needs to change. */
class InvestigationCategoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val categoryName = intent.getStringExtra("category_name").orEmpty()
        val category = ClinicalRepository.investigationCategories.find { it.name == categoryName }

        // ROOT-CAUSE FIX (2026-07-15): dp() must be declared BEFORE it's used —
        // it was declared further down before, so every earlier use of dp()
        // failed to compile ("Unresolved reference: dp"). Moved to the top.
        val dens = resources.displayMetrics.density
        fun dp(v: Int) = (v * dens).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundResource(R.drawable.bg_app_gradient)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_login_hero)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val backArrow = TextView(this).apply {
            text = "←"; textSize = 20f; setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, dp(14), 0)
            isClickable = true; isFocusable = true
        }
        backArrow.setOnClickListener { finish() }
        header.addView(backArrow)
        header.addView(TextView(this).apply {
            text = category?.let { "${it.emoji}  ${it.name}" } ?: "Blood Test"
            textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(android.graphics.Color.WHITE)
        })
        root.addView(header)

        val editable = RoleSession.canEditClinical()

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        scroll.addView(list)
        root.addView(scroll)

        category?.tests?.forEach { testName ->
            val existing = ClinicalRepository.currentInvestigations.find { it.name == testName }
            var isChecked = existing?.isSelected == true

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(if (isChecked) "#EAFBF0" else "#FFFFFF", if (isChecked) "#0B4F2A" else "#E1E6ED", dp(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8); layoutParams = lp
                isClickable = editable; isFocusable = editable
            }
            val circle = TextView(this).apply {
                text = if (isChecked) "✓" else ""
                textSize = 13f; gravity = android.view.Gravity.CENTER; setTextColor(android.graphics.Color.WHITE)
                background = roundedBg(if (isChecked) "#0B4F2A" else "#FFFFFF", "#B7C0CE", dp(20))
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val label = TextView(this).apply {
                text = testName; textSize = 13.5f
                setTextColor(android.graphics.Color.parseColor(if (isChecked) "#0B4F2A" else "#10223A"))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(12); layoutParams = lp
            }
            row.addView(circle); row.addView(label)
            if (editable) {
                row.setOnClickListener {
                    isChecked = !isChecked
                    if (existing != null) {
                        existing.isSelected = isChecked
                    } else {
                        ClinicalRepository.currentInvestigations.add(InvestigationEntry(name = testName, isSelected = isChecked))
                    }
                    circle.text = if (isChecked) "✓" else ""
                    circle.background = roundedBg(if (isChecked) "#0B4F2A" else "#FFFFFF", "#B7C0CE", dp(20))
                    label.setTextColor(android.graphics.Color.parseColor(if (isChecked) "#0B4F2A" else "#10223A"))
                    row.background = roundedBg(if (isChecked) "#EAFBF0" else "#FFFFFF", if (isChecked) "#0B4F2A" else "#E1E6ED", dp(12))
                }
            }
            list.addView(row)
        }

        setContentView(root)
        UppercaseInputUtil.applyToAll(root)  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically
    }

    private fun roundedBg(fill: String, stroke: String, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(android.graphics.Color.parseColor(fill))
            setStroke(2, android.graphics.Color.parseColor(stroke))
        }
}
