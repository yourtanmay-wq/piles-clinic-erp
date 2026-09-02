package com.tkbiswas.pilesclinic.clinical

import com.tkbiswas.pilesclinic.print.BranchCatalog

/**
 * 💰🔒 V971 (০২.০৯.২০২৬, TK-এর PDF নমুনা ও পাশ-করা প্রুফ অনুযায়ী) —
 * **এস্টিমেটের A4 কাগজ।**
 *
 * ⛔ প্রকল্পের **মাস্টার প্রিন্ট ডিজাইনেই** — সোনালি দাগ · লোগো-সারি · সবুজ
 *    শিরোনাম-বার · রোগীর ঘর · নিচে TK BISWAS ও ডাক্তারের সই-সারি
 *    (`CheckupA4Report`-এর হুবহু একই ধাঁচ)। নতুন কোনো সাজ বানানো হয়নি।
 * ⛔ **কাটা লাইনের টাকা কাটা অবস্থাতেই ছাপে**, আর Subtotal-এ গোনা হয় না —
 *    TK-এর নমুনা PDF-এ ঠিক তাই।
 * ⛔ কাগজে বাড়তি কোনো লেখা নেই (TK: *"Free লিখতে কে বলেছে আপনাকে"*)।
 */
object EstimateHtmlPrint {

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun build(
        sheet: EstimateModel.Sheet,
        branch: String,
        name: String,
        patientId: String,
        ageSex: String,
        mobile: String,
        address: String,
        date: String
    ): String {
        val b = BranchCatalog.byName(branch)
        val rows = StringBuilder()
        for (l in sheet.lines) {
            val cls = if (l.struck) " class=\"free\"" else ""
            val amtCls = if (l.struck) " class=\"r amt\"" else " class=\"r\""
            val label = if (l.measure.isBlank()) esc(l.name)
                        else esc(l.name) + " (" + esc(l.measure) + ")"
            rows.append("<tr").append(cls).append(">")
                .append("<td>").append(label).append("</td>")
                .append("<td class=\"k\">").append(if (l.position.isBlank()) "&mdash;" else esc(l.position)).append("</td>")
                .append("<td class=\"r\">").append(EstimateModel.money(l.rate)).append("</td>")
                .append("<td class=\"r\">").append(EstimateModel.moneyShort(l.qty)).append("</td>")
                .append("<td").append(amtCls).append(">").append(EstimateModel.money(l.total)).append("</td>")
                .append("</tr>")
        }
        val findingBlock = if (sheet.finding.isBlank()) "" else
            """<div class="sec"><div class="sh">CLINICAL FINDING</div>
<div class="note">${esc(sheet.finding)}</div></div>"""
        val discountRow = if (sheet.discount <= 0.0) "" else
            """<div><span class="lbl">Total Discount</span><span class="disc">&minus; ${EstimateModel.money(sheet.discount)}</span></div>"""

        return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=794">
<style>
*{margin:0;padding:0;box-sizing:border-box;font-family:Georgia,'Noto Serif',serif;}
body{background:#fff;color:#111;min-height:1123px;display:flex;flex-direction:column;}
.gold{height:5px;background:linear-gradient(90deg,#b8912f,#e6c65c,#b8912f);}
.gbar{height:3px;background:#0f5132;}
.lh{display:flex;align-items:center;gap:16px;padding:10px 20px 8px;}
.lh img{width:78px;height:78px;object-fit:contain;flex:0 0 auto;}
.cn{font-size:23px;font-weight:800;color:#0f5132;line-height:1;}
.tag{font-size:11px;font-weight:700;color:#b8912f;letter-spacing:2px;margin-top:2px;text-transform:uppercase;font-family:Arial;}
.addr{font-size:11.5px;color:#3b4650;margin-top:3px;font-family:Arial;}
.addr b{color:#0f5132;}
.tb{background:#0f5132;color:#fff;display:flex;justify-content:space-between;align-items:center;padding:7px 20px;font-family:Arial;}
.tb .t{font-size:13px;font-weight:800;letter-spacing:2px;}
.tb .r{font-size:10.5px;color:#cfe6d8;}
.pi{display:flex;gap:18px;padding:9px 20px;font-size:12px;font-family:Arial;background:#f7faf8;border-bottom:1.5px solid #e4ebe6;}
.pi .c{flex:1;}
.pi .r{padding:2.5px 0;}
.pi .r b{color:#0f5132;display:inline-block;min-width:74px;}
.wrap{padding:8px 20px 10px;font-family:Arial;flex:1;}
.sec{margin-top:7px;border:1px solid #d5ddd7;border-radius:4px;overflow:hidden;}
.sh{background:#eef5f0;color:#0f5132;font-size:11px;font-weight:800;letter-spacing:1px;padding:5.5px 12px;border-left:4px solid #b8912f;}
.note{padding:7px 12px;font-size:11.3px;line-height:1.5;color:#222;}
table{width:100%;border-collapse:collapse;font-size:11.3px;}
thead th{background:#eef5f0;color:#0f5132;text-align:left;padding:6px 10px;font-size:10px;letter-spacing:.8px;border-bottom:1px solid #d5ddd7;}
td{padding:6px 10px;border-bottom:1px solid #f0f3f1;color:#111;}
td.k{color:#6b7680;}
td.r,th.r{text-align:right;}
tbody tr:last-child td{border-bottom:0;}
.free td{color:#6b7680;}
.free .amt{text-decoration:line-through;color:#8a949e;}
.sum{margin-top:8px;display:flex;justify-content:flex-end;}
.sumbox{width:300px;border:1px solid #d5ddd7;border-radius:4px;overflow:hidden;}
.sumbox div{display:flex;justify-content:space-between;padding:6px 12px;font-size:11.5px;font-family:Arial;border-bottom:1px solid #f0f3f1;}
.sumbox div:last-child{border-bottom:0;}
.sumbox .lbl{color:#6b7680;}
.sumbox .net{background:#0f5132;color:#fff;font-weight:800;font-size:13px;padding:9px 12px;}
.disc{color:#B42318;font-weight:700;}
.small{font-size:10px;color:#6b7680;font-style:italic;padding:8px 2px 0;font-family:Arial;}
.foot{margin-top:auto;padding:9px 20px 4px;font-family:Arial;}
.sign{display:grid;grid-template-columns:1fr auto 1fr;align-items:start;gap:22px;}
.sign .ln{border-top:.9px solid #15231C;text-align:center;padding-top:5px;}
.sign .ln b{display:block;font-size:11.2px;font-weight:900;color:#15231C;}
.sign .ln small{display:block;font-size:9px;color:#54615A;margin-top:1px;}
.vfy{text-align:center;border-top:.9px solid #15231C;padding-top:5px;}
.vfy .bar{height:24px;width:121px;margin:0 auto 1.5px;background:repeating-linear-gradient(90deg,#15231C 0 1.9px,#fff 1.9px 4px);}
.vfy .vl{margin-top:2px;white-space:nowrap;}
.vfy b{font-size:8.6px;color:#0A5428;}.vfy small{font-size:8.2px;color:#54615A;}
.fn{border-top:1px solid #e4ebe6;text-align:center;font-size:9.5px;color:#8a949e;padding:5px 0 6px;font-family:Arial;}
</style></head><body>
<div class="gold"></div>
<div class="lh"><img src="${b.logoAssetPath}">
<div><div class="cn">${esc(b.clinicName)}</div><div class="tag">Ayurveda &amp; Anorectal Diseases</div>
<div class="addr"><b>${esc(b.displayName)}:</b> ${esc(b.addressLine)} &nbsp;|&nbsp; <b>&#9742;</b> ${esc(b.phoneLine)} &nbsp;|&nbsp; <b>&#9742;</b> ${esc(BranchCatalog.HELPLINE)}</div></div></div>
<div class="gbar"></div>
<div class="tb"><span class="t">TREATMENT COST ESTIMATE</span><span class="r">Rec. No: ${esc(patientId)} &nbsp;&middot;&nbsp; Date: ${esc(date)}</span></div>
<div class="pi">
<div class="c"><div class="r"><b>Name</b> : ${esc(name)}</div><div class="r"><b>Patient ID</b> : ${esc(patientId)}</div><div class="r"><b>Age / Sex</b> : ${esc(ageSex)}</div></div>
<div class="c"><div class="r"><b>Mobile</b> : ${esc(mobile)}</div><div class="r"><b>Branch</b> : ${esc(b.displayName)}</div><div class="r"><b>Address</b> : ${esc(address)}</div></div>
</div>
<div class="wrap">
$findingBlock
<div class="sec"><div class="sh">COST BREAKDOWN</div>
<table><thead><tr><th>Treatment / Item</th><th>Position</th><th class="r">Rate (&#8377;)</th><th class="r">Qty</th><th class="r">Total (&#8377;)</th></tr></thead>
<tbody>$rows</tbody></table></div>
<div class="sum"><div class="sumbox">
<div><span class="lbl">Subtotal</span><span>${EstimateModel.money(sheet.subtotal)}</span></div>
$discountRow
<div class="net"><span>Net Payable Amount</span><span>${EstimateModel.money(sheet.netPayable)}</span></div>
</div></div>
<div class="small">* This estimate is indicative and based on the initial clinical presentation. The net payable amount may vary.</div>
</div>
<div class="foot"><div class="sign">
<div class="ln"><b>TK BISWAS</b><small>Founder &amp; Consultant</small></div>
<div class="vfy"><div class="bar"></div>
<div class="vl"><b>Document Digitally Verified</b> &middot; <small>No Physical Signature Required</small></div></div>
<div class="ln"><b>Dr. K.H MANDAL</b><small>(B.A.M.S) Regd 12386</small></div>
</div></div>
<div class="fn">Computer-generated cost estimate &middot; ${esc(b.clinicName)} &middot; Ayurveda &amp; Anorectal Diseases</div>
</body></html>"""
    }
}
