package com.tkbiswas.pilesclinic.native

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast

/**
 * TK-REQUESTED (2026-07-18): "long-press to copy" for name/mobile text shown
 * anywhere in the app — Enquiry/Visit/Patient cards, popups, Chamber
 * Attendance, etc. Centralised here so every screen copies the exact same
 * way (same toast wording, same clipboard label) instead of each file
 * re-implementing it slightly differently.
 *
 * Usage: someTextView.copyOnLongPress("Name", item.name)
 */
fun View.copyOnLongPress(label: String, value: String) {
    setOnLongClickListener {
        if (value.isBlank()) return@setOnLongClickListener true
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        true
    }
}
