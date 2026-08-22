package com.tkbiswas.pilesclinic.clinical

import com.tkbiswas.pilesclinic.print.BranchCatalog

/**
 * 🆕🔒 (07.08.2026, TK-অনুমোদিত ফটো-প্রুফের পরে) — সেভ-করা Doctor Check-up-এর
 * **A4 রিপোর্ট** তৈরির একমাত্র জায়গা (ক্লিনিক-লেটারহেড + রোগীর তথ্য + ৫টা
 * সেকশন + Before/During/After ছবি + সিল/সই)।
 *
 * দুই জায়গা থেকেই এই একই টেমপ্লেট ব্যবহার হয়, তাই চেহারা কখনো আলাদা হবে না:
 *   ১) `DoctorCheckupActivity` — Save চাপার পর পর্দায় (ও 🖨 Print-এ)।
 *   ২) `PatientTimelineActivity` — 📜 History-তে পুরনো "Doctor Checkup"
 *      সারিতে চাপলে (পুরনো রেকর্ড দেখা/ছাপা)।
 *
 * ⛔ **ঝুঁকিহীন নকশা:** এটা সম্পূর্ণ নতুন, আলাদা ফাইল — কোনো ফর্ম-ফিল্ড,
 * সেভ-লজিক, ডেটাবেস-কলাম বা পুরনো ক্লাসে এক অক্ষরও বদলায় না। শুধু টেক্সট
 * থেকে HTML বানায় (কোনো নেটওয়ার্ক/ক্লাউড কল নেই — Supabase free-plan নিরাপদ)।
 */
object CheckupA4Report {

    /** রিপোর্টের উপরের অংশে যা বসে (রোগী + ব্রাঞ্চ + তারিখ)। */
    data class Info(
        val name: String = "",
        val patientId: String = "",
        val age: String = "",
        val sex: String = "",
        val mobile: String = "",
        val disease: String = "",
        val address: String = "",
        val branch: String = "",
        val date: String = "",
        val photo: String = ""   // 🔒 B551 — রোগীর ছবি (data URL), থাকলে ডিটেলসের বাঁ পাশে বসে
    )

    /** ভিতরের সব লেখা — কোনো ঘর ফাঁকা থাকলে রিপোর্টে "—" বসে। */
    data class Fields(
        val complaint: String = "", val duration: String = "", val onset: String = "",
        val occupation: String = "", val prevTreatment: String = "", val prevResult: String = "",
        val prevCost: String = "", val treatmentDuration: String = "",
        val visual: String = "", val dre: String = "", val grade: String = "",
        val onProbing: String = "", val investigation: String = "", val otherFindings: String = "",
        val treatmentPlan: String = "", val rate: String = "", val counselling: String = "",
        val estCost: String = "", val recovery: String = "", val advance: String = "",
        val decision: String = "", val remarks: String = "",
        val beforePhoto: String = "", val duringPhoto: String = "", val afterPhoto: String = ""
    )

    fun today(): String {
        val f = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        return f.format(java.util.Date())
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // 🔒 B552 (08.08.2026, TK-নির্দেশ — "গ্লোবাল রুলস"): ঠিকানা দু'লাইনে —
    // থানা-চিহ্নের (PS/P.S/P/S/Thana/থানা/Police Station) ঠিক আগে লাইন-ব্রেক,
    // তাই ১ম লাইনে গ্রাম+পোস্ট, ২য় লাইনে থানা+জেলা। চিহ্ন না পেলে এক লাইনেই
    // থাকে (কিছু ভাঙে না)। DoctorCheckupActivity.formatAddressTwoLines-এর
    // হুবহু একই প্রমাণিত নিয়ম, শুধু এখানে HTML `<br>` দিয়ে। সেভ-হওয়া মান
    // বদলায় না — শুধু দেখানোর সময় ভাঙা হয়।
    private fun addrTwoLines(raw: String): String {
        val markers = listOf("PS:", "P.S", "P/S", "Thana", "থানা", "Police Station")
        var idx = -1
        for (m in markers) {
            val i = raw.indexOf(m, ignoreCase = true)
            if (i > 0 && (idx == -1 || i < idx)) idx = i
        }
        if (idx <= 0) return esc(raw)
        val first = raw.substring(0, idx).trim().trimEnd(',').trim()
        val second = raw.substring(idx).trim()
        if (first.isBlank() || second.isBlank()) return esc(raw)
        return esc(first) + "<br>" + esc(second)
    }

    /**
     * 📜 History-র জন্য: `DoctorCheckupActivity.buildDetails()` যে টেক্সট
     * সেভ করে ("Complaint: x; Duration: y; ...") সেটাকেই আবার ঘরে ভাগ করে।
     * ⛔ লেবেলগুলো হুবহু buildDetails()-এর সাথে মিলিয়ে রাখা হয়েছে।
     * ⚠️ সৎ সীমাবদ্ধতা: History-র সারিতে ছবি (before/during/after) থাকে না
     * (ওগুলো আলাদা কলামে সেভ হয়), তাই পুরনো রেকর্ডের রিপোর্টে ছবির ঘর ফাঁকা।
     */
    fun parseDetails(note: String): Fields {
        fun field(label: String): String {
            val marker = "$label: "
            var searchFrom = 0
            var idx = -1
            while (true) {
                val found = note.indexOf(marker, searchFrom)
                if (found < 0) break
                // শুরুতে, নয়তো ঠিক আগে "; " থাকলে তবেই আসল লেবেল ধরা হয় —
                // নইলে "Treatment Duration"-এর ভিতরের "Duration"-ও মিলে যেত
                // (PatientTimelineActivity-তে ধরা পড়া পুরনো বাগ, একই সুরক্ষা)।
                if (found == 0 || (found >= 2 && note.startsWith("; ", found - 2))) { idx = found; break }
                searchFrom = found + 1
            }
            if (idx < 0) return ""
            val from = idx + marker.length
            val end = note.indexOf("; ", from).let { if (it < 0) note.length else it }
            return note.substring(from, end).trim()
        }
        val plan = field("Treatment Plan")
        // "Per Piles (Per Piles ₹8000)" — বন্ধনীর ভিতরের টাকাটাই "হার"।
        val rate = plan.substringAfter('(', "").substringBeforeLast(')', "").trim()
        val planOnly = if (plan.contains('(')) plan.substringBefore('(').trim() else plan
        return Fields(
            complaint = field("Complaint"), duration = field("Duration"), onset = field("Onset"),
            occupation = field("Occupation"), prevTreatment = field("Prev Treatment"),
            prevResult = field("Prev Result"), prevCost = field("Prev Cost"),
            treatmentDuration = field("Treatment Duration"),
            visual = field("Visual"), dre = field("DRE"), grade = field("Grade"),
            onProbing = field("On Probing"), investigation = field("Investigation"),
            otherFindings = field("Other Findings"),
            treatmentPlan = planOnly, rate = rate, counselling = field("Other Treatment Note"),
            estCost = field("Est Cost"), recovery = field("Recovery"), advance = field("Advance"),
            decision = field("Decision"), remarks = field("Remarks")
        )
    }

    /** সম্পূর্ণ A4 রিপোর্টের HTML। ছবি দেখাতে WebView-এ baseURL
     *  `file:///android_asset/` দিতে হয় (ক্লিনিক-লোগো assets-এ থাকে)। */
    fun html(info: Info, f: Fields): String {
        val b = BranchCatalog.byName(info.branch)
        val date = info.date.ifBlank { today() }
        val name = esc(info.name.ifBlank { "-" })
        val pid = esc(info.patientId.ifBlank { "-" })
        val ageSex = esc(listOf(info.age, info.sex).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" })
        val disease = esc(info.disease.ifBlank { "-" })
        val addr2 = addrTwoLines(info.address.ifBlank { "-" })
        val mobile = esc(info.mobile.ifBlank { "-" })
        fun v(x: String) = esc(x.ifBlank { "—" })
        fun photoBox(label: String, data: String): String =
            if (data.isBlank())
                """<div class="pcell"><div class="pimg empty">—</div><div class="pl">$label</div></div>"""
            else
                """<div class="pcell"><div class="pimg" style="background-image:url('$data')"></div><div class="pl">$label</div></div>"""
        val photos = photoBox("BEFORE", f.beforePhoto) +
            photoBox("DURING", f.duringPhoto) + photoBox("AFTER", f.afterPhoto)
        // 🔒 B551 (08.08.2026, TK-অনুমোদিত) — রোগীর ছবি থাকলে পেশেন্ট ডিটেলসের
        // বাঁ পাশে বসে; ছবি না থাকলে কিছুই বসে না (ডিটেলস পুরো জায়গা নেয়)।
        val photoCell = if (info.photo.isNotBlank())
            """<div class="pphoto" style="background-image:url('${info.photo}')"></div>""" else ""

        return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=794">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Georgia,'Noto Serif',serif;}
body{background:#fff;color:#111;}
.gold{height:6px;background:linear-gradient(90deg,#b8912f,#e6c65c,#b8912f);}
.gbar{height:3px;background:#0f5132;}
.lh{display:flex;align-items:center;gap:14px;padding:14px 22px 10px;}
.lh img{width:74px;height:74px;}
.cn{font-size:23px;font-weight:800;color:#0f5132;line-height:1;}
.tag{font-size:11px;font-weight:700;color:#b8912f;letter-spacing:2px;margin-top:3px;text-transform:uppercase;font-family:Arial;}
.addr{font-size:11.5px;color:#3b4650;margin-top:4px;font-family:Arial;}
.addr b{color:#0f5132;}
.rx{margin-left:auto;color:#0f5132;font-size:26px;font-weight:800;}
.tb{background:#0f5132;color:#fff;display:flex;justify-content:space-between;align-items:center;padding:6px 22px;font-family:Arial;}
.tb .t{font-size:13px;font-weight:800;letter-spacing:2px;}
.tb .r{font-size:10.5px;color:#cfe6d8;}
.pi{display:flex;align-items:center;gap:16px;padding:10px 22px;font-size:12px;font-family:Arial;background:#f7faf8;border-bottom:1.5px solid #e4ebe6;}
.pphoto{width:84px;height:100px;border:2px solid #b8912f;border-radius:5px;background-size:cover;background-position:center;background-color:#eaf0f6;flex:0 0 auto;}
.pi .c{flex:1;}
.pi .r{padding:2px 0;}
.pi .r b{color:#0f5132;display:inline-block;min-width:74px;}
.wrap{padding:4px 22px 14px;font-family:Arial;}
.sec{margin-top:10px;border:1px solid #d5ddd7;border-radius:5px;overflow:hidden;}
.sh{background:#eef5f0;color:#0f5132;font-size:11px;font-weight:800;letter-spacing:1px;padding:6px 12px;border-left:4px solid #b8912f;}
.g{display:flex;flex-wrap:wrap;}
.cell{width:50%;padding:6px 12px;font-size:12px;border-bottom:1px solid #f0f3f1;display:flex;gap:8px;}
.cell.full{width:100%;}
.cell .k{color:#6b7680;min-width:110px;}
.cell .v{color:#111;font-weight:700;}
.cell:nth-child(odd){border-right:1px solid #f0f3f1;}
.photos{display:flex;gap:10px;padding:10px 12px;}
.pcell{flex:1;text-align:center;}
.pimg{height:120px;border:1.2px solid #d5ddd7;border-radius:5px;background-size:cover;background-position:center;background-color:#f4f6f5;}
.pimg.empty{display:flex;align-items:center;justify-content:center;color:#b7c1ba;font-size:26px;}
.pl{margin-top:5px;font-size:10px;font-weight:800;color:#0f5132;letter-spacing:1px;}
.foot{display:flex;justify-content:space-between;align-items:flex-end;padding:26px 22px 10px;font-family:Arial;}
.stamp{width:104px;height:104px;border:1.4px dashed #c3ccd6;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#aeb8c2;font-size:10px;}
.sign{text-align:center;font-size:11px;}
.sign .ln{width:190px;border-top:1.4px solid #333;margin-bottom:4px;}
.sign .dn{font-weight:800;color:#0f5132;}
.fn{border-top:1px solid #e4ebe6;text-align:center;font-size:9.5px;color:#8a949e;padding:7px 0 10px;font-family:Arial;}
</style></head><body>
<div class="gold"></div>
<div class="lh"><img src="${b.logoAssetPath}">
<div><div class="cn">${esc(b.clinicName)}</div><div class="tag">Ayurveda &amp; Anorectal Diseases</div>
<div class="addr"><b>${esc(b.displayName)}:</b> ${esc(b.addressLine)} &nbsp;|&nbsp; <b>&#9742;</b> ${esc(b.phoneLine)}</div></div></div>
<div class="gbar"></div>
<div class="tb"><span class="t">DOCTOR CHECK-UP RECORD</span><span class="r">Rec. No: $pid &nbsp;&middot;&nbsp; Date: $date</span></div>
<div class="pi">
$photoCell<div class="c"><div class="r"><b>Name</b> : $name</div><div class="r"><b>Patient ID</b> : $pid</div><div class="r"><b>Age / Sex</b> : $ageSex</div><div class="r"><b>Mobile</b> : $mobile</div></div>
<div class="c"><div class="r"><b>Disease</b> : $disease</div><div class="r"><b>Branch</b> : ${esc(b.displayName)}</div><div class="r"><b>Visit Date</b> : $date</div><div class="r"><b>Address</b> : <span style="display:inline-block;vertical-align:top">$addr2</span></div></div>
</div>
<div class="wrap">
<div class="sec"><div class="sh">HISTORY &amp; PREVIOUS TREATMENT</div><div class="g">
<div class="cell"><span class="k">Chief Complaint</span><span class="v">${v(f.complaint)}</span></div>
<div class="cell"><span class="k">Duration</span><span class="v">${v(f.duration)}</span></div>
<div class="cell"><span class="k">Onset</span><span class="v">${v(f.onset)}</span></div>
<div class="cell"><span class="k">Occupation</span><span class="v">${v(f.occupation)}</span></div>
<div class="cell"><span class="k">Prev. Treatment</span><span class="v">${v(f.prevTreatment)}</span></div>
${""/* 🔵 B622 (11.08.2026, TK-নির্দেশ): Result · Prev. Cost · Treatment Duration ঘর ফর্ম থেকে বাদ, তাই প্রিন্টেও বাদ (প্রিন্ট ও ফর্ম মেলে)। */}
${""/* V455 (18.08.2026, TK-নির্দেশ): Onset ঘর ফর্ম থেকে বাদ, তাই প্রিন্টেও বাদ। */}
</div></div>
<div class="sec"><div class="sh">CLINICAL FINDINGS</div><div class="g">
<div class="cell"><span class="k">Visual Exam</span><span class="v">${v(f.visual)}</span></div>
${""/* V455 (18.08.2026, TK-নির্দেশ): DRE ঘর (পুরো B সেকশন) ফর্ম থেকে বাদ, তাই প্রিন্টেও বাদ। */}
<div class="cell"><span class="k">Proctoscopy Grade</span><span class="v">${v(f.grade)}</span></div>
<div class="cell"><span class="k">On Probing</span><span class="v">${v(f.onProbing)}</span></div>
<div class="cell"><span class="k">Investigations</span><span class="v">${v(f.investigation)}</span></div>
${""/* V455 (18.08.2026, TK-নির্দেশ): Other Findings ঘর ফর্ম থেকে বাদ, তাই প্রিন্টেও বাদ। */}
</div></div>
<div class="sec"><div class="sh">TREATMENT PLAN &amp; COUNSELLING</div><div class="g">
<div class="cell full"><span class="k">Treatment Plan</span><span class="v">${v(f.treatmentPlan)}</span></div>
<div class="cell full"><span class="k">Rate</span><span class="v">${v(f.rate)}</span></div>
<div class="cell full"><span class="k">Counselling</span><span class="v">${v(f.counselling)}</span></div>
</div></div>
<div class="sec"><div class="sh">ESTIMATE &amp; DECISION</div><div class="g">
<div class="cell"><span class="k">Estimated Cost</span><span class="v">${v(f.estCost)}</span></div>
<div class="cell"><span class="k">Recovery Time</span><span class="v">${v(f.recovery)}</span></div>
<div class="cell"><span class="k">Advance Paid</span><span class="v">${v(f.advance)}</span></div>
${""/* V455 (18.08.2026, TK-নির্দেশ): Decision ও Remarks ঘর ফর্ম থেকে বাদ, তাই প্রিন্টেও বাদ। */}
</div></div>
<div class="sec"><div class="sh">CLINICAL PHOTOGRAPHS &nbsp;&middot;&nbsp; BEFORE / DURING / AFTER</div>
<div class="photos">$photos</div></div>
</div>
<div class="foot"><div class="stamp">Clinic Stamp</div>
<div class="sign"><div class="ln"></div><div class="dn">Doctor's Signature</div><div style="font-size:9.5px;color:#5a6570;">${esc(b.clinicName)}</div></div></div>
<div class="fn">Computer-generated check-up record &middot; ${esc(b.clinicName)} &middot; Ayurveda &amp; Anorectal Diseases</div>
</body></html>"""
    }
}
