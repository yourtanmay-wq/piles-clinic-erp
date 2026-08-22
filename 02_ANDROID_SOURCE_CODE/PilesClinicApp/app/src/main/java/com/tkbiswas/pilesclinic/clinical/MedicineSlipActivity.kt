package com.tkbiswas.pilesclinic.clinical

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.tkbiswas.pilesclinic.R
import com.tkbiswas.pilesclinic.native.DateUtil
import java.util.Date
import com.tkbiswas.pilesclinic.native.UppercaseInputUtil

class MedicineSlipActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.tkbiswas.pilesclinic.clinical.ClinicalRepository.attachDoseMemory(this)
        setContentView(R.layout.activity_medicine_slip)
        UppercaseInputUtil.applyToAll(window.decorView.findViewById(android.R.id.content))  // TK-REQUESTED GLOBAL RULE (2026-07-24): English text auto-CAPITAL, Password fields excluded automatically

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // 🔒 খাতার সারি B72 (29.07.2026): পর্দার উপরের ক্লিনিকের নামও ব্রাঞ্চ
        // অনুযায়ী — লেআউটে বসানো নামটা আর ব্যবহার হয় না।
        findViewById<TextView>(R.id.tvSlipClinicName).text =
            com.tkbiswas.pilesclinic.print.BranchCatalog.byName(RoleSession.currentPatientBranch).clinicName

        findViewById<TextView>(R.id.tvPatientLine).text =
            // 🔒 খাতার সারি B175 — মানুষ-পড়া-যায় Patient ID।
            "Patient: ${RoleSession.currentPatientName} (${RoleSession.displayId()})"

        // 🔒 খাতার সারি B75/B76 (TK, 29.07.2026): এখানে একটা `dateFormat` তৈরি হত
        // `Locale.getDefault()` দিয়ে (ফোনের ভাষা অনুযায়ী) — কিন্তু সেটা আসলে
        // কোথাও ব্যবহারই হত না; তারিখ-সময় দুই জায়গাতেই `DateUtil` থেকে আসে।
        // মৃত লাইনটা তুলে দেওয়া হলো, যাতে ভবিষ্যতে কেউ ভুল করে ওটা ব্যবহার না করে।
        findViewById<TextView>(R.id.tvDateLine).text = "Date: ${DateUtil.displayWithTime(Date())}"

        val medicines = ClinicalRepository.currentSlip
        refreshMedicineList()

        findViewById<MaterialButton>(R.id.btnAddSlipList).setOnClickListener { showSlipMedicinePicker() }
        findViewById<MaterialButton>(R.id.btnAddSlipCustom).setOnClickListener { showCustomSlipMedicineDialog() }

        findViewById<MaterialButton>(R.id.btnShareText).setOnClickListener {
            val shareText = buildSlipText(medicines)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Medicine Slip - ${RoleSession.currentPatientName}")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(sendIntent, "Share Medicine Slip"))
        }

        findViewById<MaterialButton>(R.id.btnPrint).setOnClickListener {
            // Generates a real A4 PDF and opens the native preview
            
            // (Save PDF / Share PDF / Print). Screen/UI unchanged.
            // TK APPROVED (2026-07-15): this exact medicine set becomes the new
            // "Common Medicine Slip" default for next time (same as Prescription).
            ClinicalRepository.saveCommonMedicineSlip(ClinicalRepository.currentSlip.map { it.name }.toSet())
            persistSlipToHistory()  // AUDIT FIX 2026-08-06: keep a record in patient history
            com.tkbiswas.pilesclinic.print.PrintDataHolder.pendingModel =
                com.tkbiswas.pilesclinic.print.PrintMappers.medicineSlip()
            startActivity(Intent(this, com.tkbiswas.pilesclinic.print.PrintPreviewActivity::class.java))
        }

        // TK-REQUESTED CHANGE (2026-07-19): always jump straight into the
        // medicine-list picker — used to only happen on first (empty-list)
        // open; now also happens when adding more medicines to an
        // already-started Slip, so the middle preview screen is skipped for
        // both the new and the edit case.
        // 🚨 TK-REPORTED (28.07.2026, ফটো-প্রুফসহ · খাতার সারি B38):
        // *"যেখান থেকে ঢুকেছিলাম, ব্যাক করলে আবার সেখানেই থাকতে হবে। মাঝপথে এই
        // দ্বিতীয় পর্দাটা কোথা থেকে আসছে? এটা আসা বন্ধ করুন।"*
        //
        // **আসল কারণ:** এই পর্দাটা খোলার সঙ্গে সঙ্গেই ওষুধের তালিকা দেখায়, তাই
        // ঢোকার সময় পর্দাটা চোখেই পড়ে না। কিন্তু তালিকা থেকে ব্যাক করলে **নিচের
        // ফাঁকা পর্দাটা বেরিয়ে পড়ত** — সেটাই TK-এর দ্বিতীয় ফটো।
        //
        // **এখন:** ঢোকার সময় নিজে থেকে খোলা তালিকা থেকে **কিছু না নিয়ে ব্যাক
        // করলে এই পর্দাটাও বন্ধ হয়ে যায়** — তাই যেখান থেকে এসেছিলেন সোজা
        // সেখানেই ফিরবেন।
        // ⛔ বোতাম চেপে নিজে তালিকা খুললে আগের মতোই — ব্যাক করলে এই পর্দাতেই
        // থাকবেন, কিছু হারাবে না।
        openedFromEntry = true
        showSlipMedicinePicker()
    }

    /** খাতার সারি B38: ঢোকার সময় নিজে থেকে তালিকা খোলা হয়েছিল কি না। */
    private var openedFromEntry = false

    /** TK APPROVED (2026-07-15): shared gentle "premium pulse" -- one soft
     *  scale-up-and-back, not a repeating loop, used only on the new Apply
     *  Common button. */
    private fun animatePulse(view: android.view.View) {
        val anim = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            view,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.045f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.045f)
        )
        anim.duration = 650
        anim.repeatMode = android.animation.ValueAnimator.REVERSE
        anim.repeatCount = 1
        anim.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        anim.start()
    }

    /**
     * AUDIT FIX (2026-08-06, TK approved): a Medicine Slip is now ALSO saved
     * permanently to the Supabase "medical" table — exactly like Prescription /
     * Diet Chart / Investigation — so it shows up later in Patient Clinical
     * History and Patient Timeline. Previously the Slip was only printed/shared
     * and no record was kept.
     *
     * SAFE BY DESIGN: this only ADDS a background save (local-first + retry
     * queue, same helper the other three screens use). Print / share / screen
     * behaviour is completely unchanged, so nothing can slow the staff down or
     * be lost if the network is down. A content-signature guard means the same
     * unchanged slip is never written twice.
     */
    private var lastSavedSlipSig: String? = null
    private fun persistSlipToHistory() {
        val medicines = ClinicalRepository.currentSlip
        if (medicines.isEmpty()) return
        medicines.forEach { medicine ->
            ClinicalRepository.rememberPermanentDefault(
                applicationContext,
                medicine.name,
                medicine.medicineType,
                medicine.dosage,
                medicine.frequency,
                medicine.duration
            )
        }
        val names = medicines.joinToString(", ") { it.name.ifBlank { "(unnamed)" } }
        val details = medicines.joinToString("; ") {
            listOf(it.name.ifBlank { "(unnamed)" }, it.dosage, it.frequency, it.duration)
                .filter { s -> s.isNotBlank() }.joinToString(" · ")
        }
        val sig = "$names||$details"
        if (sig == lastSavedSlipSig) return   // never write the same slip twice
        lastSavedSlipSig = sig
        val createdBy = com.tkbiswas.pilesclinic.native.NativeSession.current(this)?.mobile ?: ""
        val pid = RoleSession.currentPatientId
        val pname = RoleSession.currentPatientName
        val appCtx = applicationContext
        com.tkbiswas.pilesclinic.native.BackgroundWork.run {
            ClinicalCloudRepository.saveMedical(appCtx, pid, pname, "Medicine Slip", names, details, createdBy)
        }
    }

    private fun refreshMedicineList() {
        val medicines = ClinicalRepository.currentSlip
        findViewById<TextView>(R.id.tvMedicineList).text = if (medicines.isEmpty()) {
            "No medicine added yet"
        } else {
            medicines.mapIndexed { index, m ->
                val parts = listOfNotNull(
                    m.dosage.takeIf { it.isNotBlank() },
                    m.frequency.takeIf { it.isNotBlank() },
                    m.duration.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                buildString {
                    append("${index + 1}. ${m.name.ifBlank { "(unnamed medicine)" }}")
                    if (parts.isNotBlank()) append("\n    $parts")
                    if (m.instructions.isNotBlank()) append("\n    Note: ${m.instructions}")
                }
            }.joinToString("\n\n")
        }
    }

    /** TK-REQUESTED CHANGE (2026-07-19): Days and Instructions are now set
     *  right inside the Add Medicine dialog (showExtraFields = true), and
     *  this now always finishes the screen after printing — previously it
     *  opened Print Preview but left this screen behind underneath, which
     *  is why the old list screen kept reappearing after Slip printing. */
    private fun showSlipMedicinePicker() {
        MedicinePickerDialog.showPicker(
            activity = this,
            listType = "allopathic",
            baseList = ClinicalRepository.slipMedicines,
            title = "Add from Medicine List",
            subtitle = "Allopathic medicines · Medicine Slip",
            accent = MedicinePickerDialog.BLUE_ALLOPATHIC,
            showExtraFields = true,
            commonProvider = { ClinicalRepository.getCommonMedicineSlip() },
            onCancelled = {
                // 🔒🔒 খাতার সারি B177 (TK, 30.07.2026) — Prescription-এর সেই
                // একই ভুল (`currentSlip.isEmpty()` শর্ত) এখানেও ছিল, একই
                // কারণে (আগে থেকে ওষুধ থাকা তালিকা কখনো "ফাঁকা" হয় না, তাই
                // finish() হতই না)। TK-এর সিদ্ধান্ত অনুযায়ী শর্তটা তুলে দেওয়া
                // হলো — তালিকা যা-ই থাক, কিছু না করে বেরোলে সরাসরি ফিরবে।
                if (openedFromEntry) finish()
                openedFromEntry = false
            }
        ) {
            refreshMedicineList()
            // TK APPROVED (2026-07-15): printing matters more than just having it
            // saved on screen — the fast-add path now opens print preview too.
            if (ClinicalRepository.currentSlip.isNotEmpty()) {
                ClinicalRepository.saveCommonMedicineSlip(ClinicalRepository.currentSlip.map { it.name }.toSet())
                persistSlipToHistory()  // AUDIT FIX 2026-08-06: keep a record in patient history
                com.tkbiswas.pilesclinic.print.PrintDataHolder.pendingModel =
                    com.tkbiswas.pilesclinic.print.PrintMappers.medicineSlip()
                startActivity(Intent(this, com.tkbiswas.pilesclinic.print.PrintPreviewActivity::class.java))
                finish()
            }
        }
    }

    private fun showCustomSlipMedicineDialog() {
        MedicinePickerDialog.showOutsideDialog(this, "allopathic", MedicinePickerDialog.BLUE_ALLOPATHIC) {
            refreshMedicineList()
            // TK-REQUESTED CHANGE (2026-07-19): match the reference-list
            // picker's flow -- adding via Outside List also directly saves,
            // prints, and closes the screen instead of leaving it behind.
            if (ClinicalRepository.currentSlip.isNotEmpty()) {
                ClinicalRepository.saveCommonMedicineSlip(ClinicalRepository.currentSlip.map { it.name }.toSet())
                persistSlipToHistory()  // AUDIT FIX 2026-08-06: keep a record in patient history
                com.tkbiswas.pilesclinic.print.PrintDataHolder.pendingModel =
                    com.tkbiswas.pilesclinic.print.PrintMappers.medicineSlip()
                startActivity(Intent(this, com.tkbiswas.pilesclinic.print.PrintPreviewActivity::class.java))
                finish()
            }
        }
    }

    private fun buildSlipText(
        medicines: List<MedicineEntry>
    ): String {
        val sb = StringBuilder()
        // 🔒 TK-এর লক করা নিয়ম (খাতার সারি B72, 29.07.2026) — নাম BranchCatalog
        // থেকেই আসবে, হাতে লেখা নয়। আগে সব ব্রাঞ্চেই "TK Biswas Piles Clinic"
        // যেত, তাই বাকি চার ব্রাঞ্চের রোগী ভুল নাম পেতেন।
        sb.append(com.tkbiswas.pilesclinic.print.BranchCatalog.byName(RoleSession.currentPatientBranch).clinicName).append("\n")
        // 🔒 খাতার সারি B175 — শেয়ার টেক্সটেও মানুষ-পড়া-যায় আইডি।
        sb.append("Patient: ${RoleSession.currentPatientName} (${RoleSession.displayId()})\n")
        sb.append("Date: ${DateUtil.displayWithTime(Date())}\n\n")
        sb.append("Rx\n")
        if (medicines.isEmpty()) {
            sb.append("No medicines in prescription yet.\n")
        } else {
            medicines.forEachIndexed { index, m ->
                val parts = listOfNotNull(
                    m.dosage.takeIf { it.isNotBlank() },
                    m.frequency.takeIf { it.isNotBlank() },
                    m.duration.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                sb.append("${index + 1}. ${m.name.ifBlank { "(unnamed medicine)" }}\n")
                if (parts.isNotBlank()) sb.append("   $parts\n")
                if (m.instructions.isNotBlank()) sb.append("   Note: ${m.instructions}\n")
            }
        }
        return sb.toString()
    }
}
