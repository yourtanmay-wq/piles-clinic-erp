package com.tkbiswas.pilesclinic.clinical

import java.util.UUID

/**
 * Phase 4 — Clinical Modules data models.
 *
 * These are plain in-memory data classes. They intentionally do not talk to any
 * database / Supabase table yet — persistence + real patient linkage is pending
 * backend wiring (tracked as a Phase 6 item, see ClinicalRepository header note).
 */

enum class UserRole { DOCTOR, STAFF }

/** One row inside a Prescription. */
data class MedicineEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var dosage: String = "",
    var frequency: String = "",
    var duration: String = "",
    var instructions: String = "",
    var medicineType: String = ""
)

/** One requested / advised investigation (blood test etc.). */
data class InvestigationEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var isSelected: Boolean = false,
    var isDoctorApproved: Boolean = false,
    val isCustom: Boolean = false
)

/** One diet-chart guideline line (either "Allowed" or "Avoid" category). */
data class DietEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var category: String = "Allowed", // "Allowed" or "Avoid"
    var isSelected: Boolean = false
)

/** Doctor Check-up form contents. */
data class CheckupRecord(
    // 1. History & Previous Treatment (merged, TK-নির্দেশ 04.08.2026)
    var complaint: String = "",
    var duration: String = "",
    // 🆕 TK-নির্দেশ (04.08.2026): হঠাৎ হয় নাকি ধীরে ধীরে বাড়ে।
    var acuteChronic: String = "",
    var occupation: String = "",
    var prevTreatment: String = "",
    var prevResult: String = "",
    var prevCost: String = "",
    var treatmentDuration: String = "",
    // 2. Clinical Findings
    var visual: String = "",         // comma-joined checkboxes
    var visualOther: String = "",
    var dre: String = "",            // comma-joined checkboxes
    var dreOther: String = "",
    /* 🔵🔒 V539 (২২.০৮.২০২৬, TK-নির্দেশ) — TK-এর নিজের উত্তর: *"হ্যাঁ, একই —
       পুরোনো ঘরটাই ব্যবহার করুন।"* ⇒ `grade` এখন **Internal Piles-এর Grade**
       ধরে রাখে, আর মানগুলো হুবহু আগের ("Grade I"…"Grade IV")।
       ⛔ তাই **পুরোনো প্রতিটা রেকর্ড আগের মতোই ঠিক দেখাবে** — কিছু হারায় না। */
    var grade: String = "",
    /* 🔵🔒 V539: Proctoscopy-র ঘরটা এখন **টাইপ করার বক্স** (তালিকা নয়) —
       তাই তার লেখা এই নতুন ঘরে। ⛔ নতুন কোনো ডেটাবেস-কলাম লাগে না: এই
       পর্দার পুরো রেকর্ডটা `buildDetails()` দিয়ে **একটাই লেখা** হিসেবে
       `medical` টেবিলে জমা হয় (কোড ধরে যাচাই করা)। */
    var proctoscopy: String = "",
    /* 🔵🔒 V539 (TK-নির্দেশ): *"Previous Treatment-এর নিচে আরেকটা বক্স থাকবে,
       রোগী এসে তার সমস্যার কথা আর কী কী বললেন সেটা টাইপ করব।"* */
    var patientSaid: String = "",
    /* 🔵🔒 V554 (২২.০৮.২০২৬, TK-অনুমোদিত): কাগজের *"রোগী এসে প্রথমে কি কি সমস্যার
       কথা বললেন?"* — ছ'টা টিক, প্রতিটার নিজের "কবে থেকে?" (সংখ্যা + Days/Months/Years),
       ব্যথার তীব্র/মৃদু, আর শেষে "এছাড়া অন্য কিছু"।
       সবটা **একটাই লেখায়** জমা থাকে (`SymptomHistoryModel.format`), তাই
       V539-এর মতোই **নতুন কোনো কলাম বা SQL লাগেনি**। */
    var symptomHistory: String = "",
    /* 🔵🔒 V555 (TK-অনুমোদিত): কাগজের ভাগ ৩ — চারটে "ইতিহাস"-এর বাছাই ও
       ডাক্তারের নিজের লেখা, সবটা একটাই লেখায় (`HistoryDetailModel.format`)।
       ⛔ নতুন কলাম বা SQL লাগেনি। */
    var historyDetail: String = "",
    // 🆕 TK-নির্দেশ (04.08.2026): নতুন সেকশন — C (Proctoscopy Grade) আর
    // পুরনো D (Investigations, এখন E) এর মাঝে। প্রোব ঢোকানোর পরে নালি
    // কোন দিকে গেল, কতটা গেল — ডাক্তার টাইপ করে লেখেন।
    var onProbing: String = "",
    var investigation: String = "",  // comma-joined checkboxes
    var otherFindings: String = "",
    // 3. Counselling & Advice
    // 🆕 TK-নির্দেশ (04.08.2026): "কীভাবে চিকিৎসা করা হবে" — Tick-বক্স
    // (comma-joined, visual/dre/investigation-এর হুবহু একই প্যাটার্ন) +
    // তিনটে অপশনের পাশে এডিটেবল টাকা।
    var treatmentPlan: String = "",
    var amtPerPiles: String = "8000",
    var amtFistulaPerInch: String = "11000",
    var amtKsharSutra: String = "6000",
    // ⛔ TK-নির্দেশ (04.08.2026): "Disease Explanation Given" বক্সটাই থেকে
    // যাচ্ছে (কোনো নতুন ঘর/কলাম না) — শুধু লেবেল/হিন্ট বদলে "অন্যান্য
    // চিকিৎসার কথা" হলো। ভ্যারিয়েবলের নাম (counselling) ইচ্ছাকৃতভাবে
    // অপরিবর্তিত রাখা হলো, যাতে পুরনো কোনো রেকর্ড/প্রিন্ট/প্রিন্টম্যাপার
    // এই ঘর খুঁজতে ব্যর্থ না হয়।
    var counselling: String = "",
    // 4. Estimate & Decision (merged, TK-নির্দেশ 04.08.2026)
    var estimatedCost: String = "",
    var recoveryTime: String = "",
    var advanceDiscussed: String = "",
    var patientDecision: String = "",
    var decisionRemark: String = "",
    // 5. Photo & Video (treatment photos, base64 data URLs)
    var beforePhoto: String = "",
    var duringPhoto: String = "",
    var afterPhoto: String = "",
    var documents: String = "",
    var savedAt: Long = 0L,
    var savedByRole: UserRole = UserRole.DOCTOR
)

/** One entry in the patient's clinical timeline / history view. */
data class ClinicalVisit(
    val id: String = UUID.randomUUID().toString(),
    val patientName: String,
    val type: String, // "Check-up" | "Prescription" | "Investigation Advice" | "Diet Chart"
    val summary: String,
    val timestamp: Long,
    val doneByRole: UserRole
)
