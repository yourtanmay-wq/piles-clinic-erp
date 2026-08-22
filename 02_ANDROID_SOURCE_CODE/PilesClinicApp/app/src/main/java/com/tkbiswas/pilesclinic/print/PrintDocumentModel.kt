package com.tkbiswas.pilesclinic.print

/** One labelled block of lines inside a printed document (e.g. "Rx", "Allowed"). */
data class PrintSection(
    val heading: String? = null,
    val lines: List<String>,
    // TK APPROVED (2026-07-15): parallel list to `lines`, same size/order — only
    // used for the Rx (Prescription/Medicine Slip) section to draw a small Type
    // box (Tab/Cap/Syp/...) before each medicine name. Null/blank entries mean
    // no box for that line. Every other document type (Registration, Diet Chart,
    // Blood Test, etc.) leaves this null and renders exactly as before.
    val rxTypes: List<String>? = null,
    // TK APPROVED (2026-07-15): when set (Rx lines only), `lines` holds just the
    // dose/frequency/days part and this holds the medicine name, so the printer
    // can draw the hospital-style row: [TYPE] Name ⋯⋯⋯ dose. Null everywhere else.
    val rxNames: List<String>? = null,
    // TK APPROVED (2026-07-15): SL./MEDICINE NAME/DOSE/WHEN/DURATION table print
    // -- only set for Prescription/Medicine Slip where the real separate
    // dosage/frequency/duration fields exist (a live clinical session, not a
    // re-print of an old flattened cloud record). When these three are non-null
    // ClinicPdfBuilder draws a real bordered table instead of the old dashed
    // single-line row; `lines`/rxNames/rxTypes stay populated too (used as a
    // fallback and by rxNames/rxTypes for the name+type column) so nothing
    // else that reads this model breaks.
    val rxDosage: List<String>? = null,
    val rxFrequency: List<String>? = null,
    val rxDuration: List<String>? = null
)

/**
 * Everything needed to render one printed document. Deliberately has no
 * photo/image field for the patient — see Phase 6 rule "Do not print patient
 * photo". The only image drawn from patient-adjacent data is the QR code
 * (a generated code, not a photo).
 */
data class PrintDocumentModel(
    val documentTitle: String,
    val patientName: String,
    val patientId: String,
    val dateLabel: String,
    val sections: List<PrintSection>,
    val qrPayload: String?,
    val footerNote: String? = null,
    val nextFollowDate: String = "",
    val branchName: String = "",
    val patientAddress: String = "",
    val patientAgeSex: String = "",
    val patientDisease: String = "",
    val complaintHistory: List<String> = emptyList(),
    val prescriptionDiet: String = "",
    /* 🔵 V488 (20.08.2026, TK-নির্দেশ): Prescription-এ "ADVICE: Sitz Bath — 2 Times
       Daily" ছাপা হবে কি না। ডিফল্ট **true** — তাই যে-কোনো পুরনো/অন্য কাগজ
       (Medicine Slip সহ) আগের মতোই ছাপে, কিছুই বদলায় না। */
    val prescriptionSitzBath: Boolean = true,
    // TK-REQUESTED (2026-07-19): the number printed under the footer barcode
    // is now the patient's mobile number instead of the Patient ID. Kept as
    // its own field (rather than reusing patientId) so the Patient ID still
    // prints normally everywhere else (e.g. the Registration details block).
    val patientMobile: String = ""
)

/**
 * In-memory hand-off from a "build this print" screen to PrintPreviewActivity
 * — same pattern as ClinicalRepository in Phase 4 (avoids a large Parcelable
 * surface for an Intent extra).
 */
object PrintDataHolder {
    var pendingModel: PrintDocumentModel? = null
    // TK-REQUESTED (2026-07-25): lets a screen that already built its OWN
    // PDF (e.g. Chamber Register, a custom multi-column table -- nothing
    // like the generic sections/lines model above) reuse this same proven
    // Save PDF / Share PDF / Print screen, instead of duplicating that
    // logic. When set, PrintPreviewActivity shows THIS file directly and
    // skips ClinicPdfBuilder entirely; every existing caller (Prescription,
    // Registration, etc.) leaves this null and is completely unaffected.
    var prebuiltFile: java.io.File? = null
    var prebuiltTitle: String? = null
}
