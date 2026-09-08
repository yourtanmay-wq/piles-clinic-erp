package com.tkbiswas.pilesclinic.native

import android.content.Context
import java.io.File

/**
 * 📊🔒 V1208 (০৮.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ) — **Yearly Registration
 * শিট (CSV)**।
 *
 * TK: *"এটা আমি গুগল শিট ডাউনলোড করতে চাই, কেন অপশন নেই?"*
 *
 * ⛔ `DraftSheetExporter.kt`-এর **হুবহু একই প্রমাণিত পথ** — একই ফোল্ডার
 *    (`cacheDir/exports`), একই UTF-8 BOM (নইলে Excel/Sheets-এ বাংলা ভাঙে),
 *    একই নিরাপদ ফাইলনাম। নতুন কোনো ধরন বানানো হয়নি।
 * ⛔ এখানে কোনো হিসাব কষা হয় না — পর্দায় যে সারিগুলো দেখানো হচ্ছে (চালু
 *    ফিল্টার সহ), হুবহু সেগুলোই কাগজে যায়, তাই পর্দা ও শিট কখনো আলাদা হয় না।
 */
object YearlyRegistrationSheetExporter {

    private fun cell(raw: String): String {
        val v = raw.replace("\r", " ").replace("\n", " ").trim()
        return if (v.contains(',') || v.contains('"')) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /** পর্দার সারির অবস্থা — ঠিক যা কার্ডে দেখানো হয়। */
    private fun statusOf(e: DraftEntry): String = when {
        e.extra == YearlyRegistration.SKIP_MARK -> "Removed"
        e.regTag.isNotBlank() -> e.regTag
        else -> "Counted"
    }

    fun write(context: Context, branch: String, year: String, items: List<DraftEntry>): File {
        val header = listOf(
            "SL", "DATE", "NAME", "MOBILE", "BRANCH", "DISEASE",
            "PATIENT ID", "STATUS", "REGISTERED BY"
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { cell(it) }).append("\r\n")
        items.forEachIndexed { idx, e ->
            val mob = e.mobile.filter { it.isDigit() }.takeLast(10)
            val line = listOf(
                (idx + 1).toString(),
                if (e.recordDate.isBlank()) "" else DateUtil.display(e.recordDate),
                e.name,
                mob,
                e.branch.ifBlank { branch },
                e.disease,
                e.patientId,
                statusOf(e),
                e.regBy
            )
            sb.append(line.joinToString(",") { cell(it) }).append("\r\n")
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = FollowUpModel.displayDate(FollowUpModel.today()).replace(".", "-")
        val safeBranch = branch.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifBlank { "ALL" }
        val file = File(dir, "YEARLY_REGISTRATION_${safeBranch}_${year}_$stamp.csv")
        // Excel/Sheets যাতে বাংলা ঠিক দেখায় — শুরুতে UTF-8 চিহ্ন (BOM)।
        file.writeText("﻿" + sb.toString(), Charsets.UTF_8)
        return file
    }
}
