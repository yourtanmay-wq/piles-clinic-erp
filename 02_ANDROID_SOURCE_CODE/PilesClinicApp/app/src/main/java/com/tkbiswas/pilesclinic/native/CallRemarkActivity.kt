package com.tkbiswas.pilesclinic.native

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 🟢🔒 V605 (২৪.০৮.২০২৬, TK-নির্দেশ) — Incoming ও Outgoing দুই ধরনের
 * কলেই রিমার্কস লেখার ছোট, স্বাধীন পর্দা।
 *
 * TK-এর নির্দেশ (হুবহু): *"কল চলাকালীন ও কল শেষে দুটোতেই Remarks ...
 * লেখার ব্যবস্থা থাকবে ... নাম্বার সেভ না থাকলে New Enquiry Form Fillup
 * এর ব্যবস্থা করতে হবে।"*
 *
 * এই পর্দাটা নোটিফিকেশনের "📝 Add Remark" বোতাম চাপলে খোলে —
 * `CallNotifyManager`-এ যে নম্বর/দিক/রোগীর id সেই মুহূর্তে জানা ছিল,
 * সেটাই সাথে করে আসে (extras)।
 *
 * ⛔ নতুন কোনো XML লেআউট বানানো হয়নি — সম্পূর্ণ কোড থেকে বানানো (প্রজেক্টের
 *    অনেক ছোট utility পর্দার মতোই), ভুল হওয়ার সুযোগ কম।
 * ⛔ `call_remarks` (নতুন, স্বাধীন টেবিল) ছাড়া আর কিছু ছোঁয়া হয়নি।
 */
class CallRemarkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mobile = intent.getStringExtra("mobile").orEmpty()
        val direction = intent.getStringExtra("direction").orEmpty().ifBlank { "incoming" }
        val patientId = intent.getStringExtra("patientId").orEmpty()
        val patientName = intent.getStringExtra("patientName").orEmpty()
        val branch = intent.getStringExtra("branch").orEmpty()
        val calledAt = intent.getStringExtra("calledAt").orEmpty()
        val existingRemark = intent.getStringExtra("existingRemark").orEmpty()
        // 🟢🔒 V634 (২৪.০৮.২০২৬, TK-রিপোর্ট — "২ বার কল করা হয়েছে তাও Wifi
        // signal কেন ১ টা দেখাচ্ছে") — followups সারির আসল id, যাতে রিমার্কস
        // সেভের সময় সিগন্যাল-আইকনের callCount-ও ঠিকভাবে বাড়ে (RMP-মিলে
        // এটা ফাঁকা থাকে, CallNotifyManager নিজেই পাঠায় না)।
        val followupId = intent.getStringExtra("followupId").orEmpty()

        val user = NativeSession.current(this)
        if (mobile.isBlank() || user == null) { finish(); return }

        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(android.graphics.Color.parseColor("#F7F9FC"))
        }

        root.addView(TextView(this).apply {
            text = "📝 Call Remark"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#101828"))
        })
        root.addView(TextView(this).apply {
            text = patientName.ifBlank { mobile } + (if (patientName.isNotBlank()) "  ·  $mobile" else "")
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#5B6B81"))
            setPadding(0, dp(6), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = (if (direction == "outgoing") "Outgoing call" else "Incoming call") +
                (if (branch.isNotBlank()) " · $branch" else "")
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#8A93A0"))
            setPadding(0, 0, 0, dp(16))
        })

        val input = EditText(this).apply {
            hint = NoBengali.s("কল নিয়ে যা বললেন লিখুন…")
            setText(existingRemark)
            minLines = 4
            gravity = android.view.Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.WHITE)
                setStroke(dp(1), android.graphics.Color.parseColor("#DBE2EA"))
            }
        }
        root.addView(input)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        val save = TextView(this).apply {
            text = "Save Remark"
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(10), dp(13), dp(10), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(android.graphics.Color.parseColor("#16A36D"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true; isFocusable = true
            setOnClickListener {
                val text = input.text?.toString().orEmpty().trim()
                if (text.isBlank()) { Toast.makeText(this@CallRemarkActivity, "Write something first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                DialerRepository.saveCallRemark(
                    ctx = applicationContext,
                    mobile = mobile, direction = direction, remark = text, patientId = patientId,
                    staffMobile = user.mobile, staffName = user.name, branch = user.branch,
                    calledAtIso = calledAt, followupId = followupId
                )
                Toast.makeText(this@CallRemarkActivity, "Remark saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        btnRow.addView(save)
        root.addView(btnRow)

        setContentView(root)
    }
}
