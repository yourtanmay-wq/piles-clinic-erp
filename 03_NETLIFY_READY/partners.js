/* =====================================================================
   V306 · PARTNER SHARES (অংশীদারি ভাগ) — PHASE 1 · MASTER SIDE (web)
   Additive & isolated: a new module that lives INSIDE Income & Expense.
   Reads fin.collections (net profit) + fin.partners / fin.partner_drawings
   (V306 tables). Never touches finance.js logic. Master-only in this phase.
   Net profit Jan→today = Σ(cash + online − expense) over collections,
   using the SAME expense rule as finance.js (expense_total OR notes sum).
   ===================================================================== */
(function () {
  function M() { return window.MOD; }
  function sb() { return M().client(); }
  function esc(s) { return M().esc(s); }
  function money(n) { return M().money(n); }
  function today() { return M().todayIST(); }
  function yearStart() { return M().todayIST().slice(0, 4) + '-01-01'; }
  function myMobile() { var s = M().session() || {}; return String(s.link_mobile || s.mobile || '').replace(/\D/g, '').slice(-10); }
  function branches() { return ((window.RK_CONFIG || {}).branches || []).map(function (b) { return b.name; }); }

  // finance.js's finSumNumbers, re-implemented (adds every number found in text).
  function sumNumbers(text) {
    if (!text) return 0;
    var m2 = String(text).match(/\d+(\.\d+)?/g);
    if (!m2) return 0;
    return m2.reduce(function (a, b) { return a + Number(b); }, 0);
  }
  function rowExpense(row) {
    return (row.expense_total != null && row.expense_total >= 0)
      ? Number(row.expense_total) : sumNumbers(row.expense_notes || '');
  }
  function n10(x) { return String(x || '').replace(/\D/g, '').slice(-10); }
  function app() { return document.getElementById('app'); }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  function dayAfter(s) { var d = new Date(s + 'T00:00:00'); d.setDate(d.getDate() + 1); return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()); }
  // Net (income − both expense sources) over [start, endExcl) using pre-loaded rows.
  function netInRange(coll, exps, start, endExcl) {
    var inc = 0, exp = 0;
    coll.forEach(function (r) { var d = r.entry_date; if (d >= start && d < endExcl) { inc += Number(r.cash || 0) + Number(r.online || 0); exp += rowExpense(r); } });
    exps.forEach(function (e) { var d = e.entry_date; if (d >= start && d < endExcl) { exp += Number(e.amount || 0); } });
    return inc - exp;
  }
  // Forward-only accrued share: sum over the partner's %-history segments of
  // (pct-in-force × net earned during that segment, within this year). If a
  // partner has no history rows, fall back to current pct × full-year net —
  // identical to the simple launch behaviour, so nothing breaks.
  function accruedFor(hist, coll, exps, currentPct) {
    var yr = yearStart(), endAll = dayAfter(today());
    if (!hist || !hist.length) return (Number(currentPct || 0) / 100) * netInRange(coll, exps, yr, endAll);
    var segs = hist.slice().sort(function (a, b) { return String(a.effective_from) < String(b.effective_from) ? -1 : 1; });
    var total = 0;
    for (var i = 0; i < segs.length; i++) {
      var start = String(segs[i].effective_from) < yr ? yr : String(segs[i].effective_from);
      var end = (i + 1 < segs.length) ? String(segs[i + 1].effective_from) : endAll;
      if (end <= yr || start >= end) continue;
      total += (Number(segs[i].pct || 0) / 100) * netInRange(coll, exps, start, end);
    }
    return total;
  }
  function pill(g) { // g = balance number; returns green/red chip
    var red = g < 0;
    var col = red ? '#B42318' : '#0A7C3F';
    var bg = red ? '#FBEAE8' : '#E7F6EC', bd = red ? '#F0C4BE' : '#B7E3C5';
    /* 🔴 V437 #24 (নিজের অডিটে ধরা) — ফোনে (`PartnerSharesActivity.kt:397`)
       চিপে শুধু `🔴/🟢 + money(bal)` থাকে, কোনো `+`/`−` চিহ্ন নেই; ঋণাত্মক হলে
       `🔴 ₹-1,500` দেখায়। ওয়েবে বাড়তি চিহ্ন বসত (`🟢 +₹1,500`)। এখন ফোনের হুবহু। */
    return '<span style="border-radius:20px;padding:6px 11px;font-weight:800;font-size:12.5px;white-space:nowrap;' +
      'background:' + bg + ';color:' + col + ';border:1px solid ' + bd + '">' +
      (red ? '🔴 ' : '🟢 ') + money(g) + '</span>';
  }

  // ---------- ENTRY ----------
  async function finPartners() { M().gate('Income & Expense', renderBranchList); }

  function guardMaster() {
    if (M().isMasterModule()) return true;
    app().innerHTML = '<div class="wrap anMod anModPt"><div class="page"><div class="card">Master only.' +
      ' <button class="ghost" onclick="incomeExpense()">Back</button></div></div></div>';
    return false;
  }

  function head(title, right) {
    return '<div class="wrap anMod anModPt"><div class="topbar"><b style="color:#0A5C33">' + title + '</b>' +
      '<span>' + (right || '') + '<button class="ghost" onclick="incomeExpense()">Back</button></span></div><div class="page">';
  }

  // ---------- BRANCH LIST ----------
  async function renderBranchList() {
    if (!guardMaster()) return;
    app().innerHTML = head('🤝 Partner Shares') + '<div id="pBody">Loading…</div></div></div>';
    var client = await sb(), rows = [];
    try { rows = (await client.schema('fin').from('partners').select('branch,mobile,name,pct,active')).data || []; }
    catch (e) { document.getElementById('pBody').innerHTML = '<div class="card mut">Could not load (offline?).</div>'; return; }
    var byB = {};
    rows.forEach(function (r) { if (r.active === false) return; (byB[r.branch] = byB[r.branch] || []).push(r); });
    var html = branches().map(function (b) {
      var list = byB[b] || [];
      var sub = list.length ? list.map(function (x) { return esc(x.name || n10(x.mobile)) + ' ' + Number(x.pct || 0) + '%'; }).join(' · ')
        : '<span style="color:#B26A00">not set up</span>';
      return '<div onclick="finPartnerBranch(\'' + esc(b) + '\')" style="cursor:pointer;background:#fff;border:1px solid #E0EAE4;border-radius:12px;padding:12px 14px;margin-bottom:9px;display:flex;justify-content:space-between;align-items:center">' +
        '<div><div style="font-weight:800;color:#0A5C33;font-size:15px">' + esc(b) + '</div>' +
        '<div style="font-size:11.5px;color:#7c8a83;margin-top:2px">' + list.length + ' partner' + (list.length === 1 ? '' : 's') + ' · ' + sub + '</div></div>' +
        '<div style="color:#9fb0a5;font-size:18px;font-weight:800">›</div></div>';
    }).join('');
    document.getElementById('pBody').innerHTML = html;
  }

  // ---------- COMPUTE ----------
  async function computeBranch(branch) {
    var client = await sb();
    var coll = [], parts = [], draws = [];
    coll = (await client.schema('fin').from('collections').select('cash,online,expense_total,expense_notes,entry_date')
      .gte('entry_date', yearStart()).lte('entry_date', today()).eq('branch', branch).eq('ignored', false)).data || [];
    parts = (await client.schema('fin').from('partners').select('*').eq('branch', branch)).data || [];
    draws = (await client.schema('fin').from('partner_drawings').select('*').eq('branch', branch).eq('ignored', false)).data || [];
    // 🔵 খরচ দুই উৎস: collections-এ ঢোকানো (expense_total/notes) + আলাদা fin.expenses টেবিল —
    // ঠিক ফোনের today-কার্ডের মতো, নাহলে নেট বেশি (ভাগ বেশি) দেখাত।
    var exps = (await client.schema('fin').from('expenses').select('amount,entry_date')
      .gte('entry_date', yearStart()).lte('entry_date', today()).eq('branch', branch).eq('ignored', false)).data || [];
    var hist = (await client.schema('fin').from('partner_pct_history').select('mobile,pct,effective_from').eq('branch', branch)).data || [];
    var income = 0, expense = 0;
    coll.forEach(function (r) { income += Number(r.cash || 0) + Number(r.online || 0); expense += rowExpense(r); });
    exps.forEach(function (e) { expense += Number(e.amount || 0); });
    var net = income - expense;
    var drawnBy = {}, histBy = {};
    draws.forEach(function (d) {
      var k = n10(d.mobile); var amt = Number(d.amount || 0);
      drawnBy[k] = (drawnBy[k] || 0) + (d.kind === 'return' ? -amt : amt);
    });
    hist.forEach(function (h) { var k = n10(h.mobile); (histBy[k] = histBy[k] || []).push(h); });
    var list = parts.filter(function (p) { return p.active !== false; }).map(function (p) {
      // Forward-only %: accrued from the partner's %-history segments (falls back
      // to current pct × net when no history — same as the simple launch case).
      var accrued = accruedFor(histBy[n10(p.mobile)], coll, exps, p.pct);
      var due = Number(p.opening || 0) + accrued;
      var drawn = drawnBy[n10(p.mobile)] || 0;
      var bal = due - drawn;
      return { p: p, accrued: accrued, due: due, drawn: drawn, bal: bal };
    });
    return { income: income, expense: expense, net: net, list: list };
  }

  // ---------- OVERVIEW ----------
  async function finPartnerBranch(branch) {
    if (!guardMaster()) return;
    app().innerHTML = head('🤝 Partner Shares', '<span style="background:#EAF6EE;color:#0A5C33;border:1.5px solid #B9E0C9;border-radius:12px;padding:6px 10px;font-weight:700;font-size:12.5px;margin-right:8px">🏥 ' + esc(branch) + '</span>') +
      '<div id="pBody">Loading…</div></div></div>';
    var c;
    try { c = await computeBranch(branch); }
    catch (e) { document.getElementById('pBody').innerHTML = '<div class="card mut">Could not load (offline?).</div>'; return; }
    var netCard = '<div class="card" style="padding:13px"><div style="font-weight:800;color:#0A5C33;font-size:12.5px;border-bottom:1px solid #eef2ef;padding-bottom:8px;margin-bottom:8px">Net Profit · Jan → Today</div>' +
      row2('Total Income', money(c.income), '#123') +
      row2('Total Expense', money(c.expense), '#B42318') +
      row2('Net Profit', money(c.net), c.net < 0 ? '#B42318' : '#0A7C3F', true) + '</div>';
    var rows = c.list.length ? c.list.map(function (x) {
      return '<div style="background:#fff;border:1px solid #E0EAE4;border-radius:12px;padding:10px 12px;margin-bottom:8px;display:flex;justify-content:space-between;align-items:center">' +
        '<div><div style="font-weight:800;color:#123;font-size:14px">' + esc(x.p.name || n10(x.p.mobile)) + '</div>' +
        '<div style="font-size:11px;color:#0A5C33;font-weight:700;margin-top:2px">Due ' + money(x.due) + ' · Withdrawn ' + money(x.drawn) + '</div></div>' +
        pill(x.bal) + '</div>';
    }).join('') : '<div class="card mut">No partners yet. Tap Setup.</div>';
    // 🟢🆕 TK-অনুমোদিত প্রুফ (10.08.2026): ফোনের মতোই কম্প্যাক্ট ২×২ টাইল (আইকন বাঁয়ে, লেখা পাশে)।
    // grid-এ দুই সেল সমান উঁচু; লেখা wrap করে — কখনো কাটে না/ঘেঁষে না (মোবাইল+ডেস্কটপ)।
    function ptile(icon, label, action, iconBg) {
      return '<div onclick="' + action + '" style="background:#fff;border:1px solid #D9E6DD;border-radius:12px;' +
        'padding:9px 10px;display:flex;align-items:center;gap:9px;cursor:pointer">' +
        '<div style="width:30px;height:30px;border-radius:8px;flex:0 0 auto;display:flex;align-items:center;' +
        'justify-content:center;font-size:16px;background:' + iconBg + '">' + icon + '</div>' +
        '<div style="font-size:13px;font-weight:800;color:#123;line-height:1.2">' + label + '</div></div>';
    }
    var btns = '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:6px">' +
      ptile('💵', 'Withdraw / Return', 'finPartnerDraw(\'' + esc(branch) + '\')', '#E7F6EC') +
      ptile('⚙️', 'Setup', 'finPartnerSetup(\'' + esc(branch) + '\')', '#EAF1EC') +
      ptile('✅', 'Settlement', 'finPartnerSettle(\'' + esc(branch) + '\')', '#FBF3E2') +
      ptile('🖨️', 'Print / Export', 'finPartnerExport(\'' + esc(branch) + '\')', '#E8F0FB') +
      '</div>' +
      '<div style="background:#E7F6EC;color:#0A6b38;border:1px dashed #9dd3af;border-radius:10px;padding:9px 11px;margin-top:10px;font-size:11.5px">💵 A withdrawal reduces the branch cash balance — not the Net Profit.</div>';
    document.getElementById('pBody').innerHTML = netCard + rows + btns;
  }

  function row2(l, v, col, bold) {
    return '<div style="display:flex;justify-content:space-between;padding:5px 0;font-size:14px">' +
      '<span style="color:#33463d' + (bold ? ';font-weight:800' : '') + '">' + esc(l) + '</span>' +
      '<span style="font-weight:800;color:' + col + (bold ? ';font-size:17px' : '') + '">' + v + '</span></div>';
  }
  function btn(label, onClick, bg) {
    return '<div onclick="' + onClick + '" style="flex:1;text-align:center;color:#fff;font-weight:800;font-size:13px;border-radius:12px;padding:12px 6px;cursor:pointer;background:' + bg + '">' + label + '</div>';
  }

  // ---------- SETUP ----------
  async function finPartnerSetup(branch) {
    if (!guardMaster()) return;
    app().innerHTML = head('⚙ Partner Setup — ' + esc(branch)) + '<div id="pBody">Loading…</div></div></div>';
    var client = await sb(), parts = [];
    try { parts = (await client.schema('fin').from('partners').select('*').eq('branch', branch).order('created_at', { ascending: true })).data || []; }
    catch (e) { document.getElementById('pBody').innerHTML = '<div class="card mut">Could not load.</div>'; return; }
    // 🔒 B595 (10.08.2026): প্রতি অংশীদারের সবচেয়ে-পুরনো "ভাগ শুরুর তারিখ" বের করি
    // (%-ইতিহাস থেকে), যাতে Setup-এ সেই তারিখ দেখানো/বদলানো যায়।
    var hist595 = [];
    try { hist595 = (await client.schema('fin').from('partner_pct_history').select('mobile,effective_from').eq('branch', branch)).data || []; }
    catch (e) { hist595 = []; }
    var firstBy595 = {};
    hist595.forEach(function (h) { var m = n10(h.mobile), d = String(h.effective_from || ''); if (d && (!firstBy595[m] || d < firstBy595[m])) firstBy595[m] = d; });
    window.__pSetup = parts.map(function (p) {
      var m = n10(p.mobile), hf = firstBy595[m] || null;
      return { id: p.id, mobile: m, name: p.name || '', pct: Number(p.pct || 0), opening: Number(p.opening || 0), can_entry: !!p.can_entry, active: p.active !== false,
               histFirst: hf, hadHistory: !!hf, startDate: hf || yearStart() };
    });
    // 🔵 forward-only %: প্রথম-বার সেট-আপ (কোনো অংশীদার নেই) = লঞ্চ → ডিফল্ট তারিখ জানুয়ারি ১;
    // পরে নতুন যোগ → ডিফল্ট আজ। যেটাই হোক, প্রতি অংশীদারের তারিখ আলাদা করে বদলানো যায় (B595)।
    window.__pIsLaunch = (parts.length === 0);
    window.__pOldPct = {}; parts.forEach(function (p) { if (p.id) window.__pOldPct[p.id] = Number(p.pct || 0); });
    renderSetup(branch);
  }
  function renderSetup(branch) {
    var arr = window.__pSetup || [];
    var rows = arr.map(function (r, i) {
      return '<div style="background:#fff;border:1px solid #E0EAE4;border-radius:12px;padding:10px 12px;margin-bottom:9px">' +
        '<div style="display:flex;gap:8px;margin-bottom:6px">' +
        '<input id="pn' + i + '" value="' + esc(r.name) + '" placeholder="Name" style="flex:1;min-width:0;padding:8px;border:1px solid #cfe0d6;border-radius:8px;font-size:13px">' +
        '<input id="pp' + i + '" value="' + r.pct + '" inputmode="decimal" placeholder="%" style="width:64px;padding:8px;border:1px solid #cfe0d6;border-radius:8px;font-size:13px;text-align:center;font-weight:800">' +
        '</div>' +
        '<div style="display:flex;gap:8px;margin-bottom:6px">' +
        '<input id="pm' + i + '" value="' + esc(r.mobile) + '" inputmode="tel" placeholder="Mobile (10 digit)" style="flex:1;min-width:0;padding:8px;border:1px solid #cfe0d6;border-radius:8px;font-size:13px">' +
        '<input id="po' + i + '" value="' + r.opening + '" inputmode="decimal" placeholder="Opening ₹" style="width:110px;padding:8px;border:1px solid #cfe0d6;border-radius:8px;font-size:13px;text-align:right">' +
        '</div>' +
        // 🔒 B595: প্রতি অংশীদারের "ভাগ শুরুর তারিখ" — নতুন কেউ যোগ হলে সবাইকে
        // আর জানুয়ারি থেকে ধরবে না; এই তারিখ থেকেই তার ভাগ গোনা হবে।
        '<div style="display:flex;align-items:center;gap:8px;background:#EFF7F1;border:1px solid #CDE7D6;border-radius:9px;padding:7px 10px;margin-bottom:6px">' +
        '<span style="font-size:12.5px;color:#0A5C33;font-weight:800;white-space:nowrap">Share from</span>' +
        '<input id="pd' + i + '" type="date" value="' + esc(r.startDate || '') + '" style="flex:1;min-width:0;padding:8px;border:1px solid #9dd3af;border-radius:8px;font-size:13px;text-align:center;font-weight:700;color:#123;background:#fff">' +
        '</div>' +
        '<div style="display:flex;justify-content:space-between;align-items:center;font-size:12px;color:#33463d">' +
        '<label><input type="checkbox" id="pc' + i + '" ' + (r.can_entry ? 'checked' : '') + '> Can add Income/Expense</label>' +
        '<label><input type="checkbox" id="pa' + i + '" ' + (r.active ? 'checked' : '') + '> Active</label>' +
        '</div></div>';
    }).join('');
    var sum = arr.reduce(function (a, r) { return a + (r.active ? Number(r.pct || 0) : 0); }, 0);
    var ok = Math.abs(sum - 100) < 0.001;
    var sumBar = '<div style="border-radius:10px;padding:9px 11px;margin:2px 0 10px;text-align:center;font-weight:800;font-size:12.5px;' +
      (ok ? 'background:#E7F6EC;border:1px solid #B7E3C5;color:#0A7C3F">Total = ' + sum + '% ✓ — can save'
          : 'background:#FDECEC;border:1px solid #F3B9B3;color:#B42318">Total = ' + sum + '% — must be 100% to save') + '</div>';
    var btns = '<div style="display:flex;gap:9px">' +
      btn('＋ Add Partner', 'finPartnerAddRow(\'' + esc(branch) + '\')', '#0A5C33') +
      btn('💾 Save', 'finPartnerSaveSetup(\'' + esc(branch) + '\')', ok ? '#1E7C43' : '#9fb0a5') + '</div>';
    document.getElementById('pBody').innerHTML = rows + sumBar + btns;
  }
  function collectSetup() {
    var arr = window.__pSetup || [];
    return arr.map(function (r, i) {
      function v(p) { var el = document.getElementById(p + i); return el ? el.value : ''; }
      function chk(p) { var el = document.getElementById(p + i); return !!(el && el.checked); }
      var sd = v('pd');
      // 🔒 B595: date-ঘর + পুরনো ইতিহাস-তথ্য (histFirst/hadHistory) index ধরে বহন করা হয়।
      return { id: r.id, name: v('pn').trim(), pct: Number(v('pp') || 0), mobile: n10(v('pm')), opening: Number(v('po') || 0), can_entry: chk('pc'), active: chk('pa'),
               startDate: sd || r.startDate || '', histFirst: r.histFirst || null, hadHistory: !!r.hadHistory };
    });
  }
  function finPartnerAddRow(branch) {
    window.__pSetup = collectSetup();
    // 🔒 B595: নতুন অংশীদারের ডিফল্ট তারিখ — লঞ্চে জানুয়ারি ১, নইলে আজ (বদলানো যায়)।
    window.__pSetup.push({ id: null, mobile: '', name: '', pct: 0, opening: 0, can_entry: false, active: true,
                           startDate: (window.__pIsLaunch ? yearStart() : today()), histFirst: null, hadHistory: false });
    renderSetup(branch);
  }
  function finPartnerRefresh(branch) { window.__pSetup = collectSetup(); renderSetup(branch); }

  async function finPartnerSaveSetup(branch) {
    var arr = collectSetup();
    window.__pSetup = arr;
    var bad = arr.filter(function (r) { return r.active && (!r.mobile || r.mobile.length !== 10); });
    if (bad.length) { alert('Enter a valid 10-digit mobile for each active partner.'); return; }
    var sum = arr.reduce(function (a, r) { return a + (r.active ? Number(r.pct || 0) : 0); }, 0);
    if (Math.abs(sum - 100) >= 0.001) { alert('Total % must be exactly 100 (now ' + sum + '%).'); return; }
    var client = await sb(), me = myMobile();
    var known = knownUsers(); // mobiles already in the app
    for (var i = 0; i < arr.length; i++) {
      var r = arr[i];
      if (!r.mobile) continue;
      var payload = {
        branch: branch, mobile: r.mobile, name: r.name, pct: r.pct, opening: r.opening,
        can_entry: r.can_entry, in_app: known.indexOf(r.mobile) >= 0, active: r.active,
        created_by: me, updated_at: new Date().toISOString()
      };
      try {
        var oldPct = (r.id && window.__pOldPct) ? window.__pOldPct[r.id] : null;
        if (r.id) { await client.schema('fin').from('partners').update(payload).eq('id', r.id); }
        else {
          var ins = (await client.schema('fin').from('partners').insert(payload).select('id')).data;
          if (ins && ins[0]) r.id = ins[0].id;
        }
        // 🔒🔒 B595 (10.08.2026, TK-অনুমোদিত প্রুফ): forward-only %-ইতিহাস +
        // প্রতি অংশীদারের নিজের "ভাগ শুরুর তারিখ"। আর কখনো সবাইকে জোর করে
        // জানুয়ারি থেকে ধরা হয় না।
        var sd = /^\d{4}-\d{2}-\d{2}$/.test(r.startDate) ? r.startDate : (window.__pIsLaunch ? yearStart() : today());
        if (r.id && !r.hadHistory) {
          // নতুন অংশীদার / আগে কোনো ইতিহাস নেই → একটাই সারি, তার বাছা তারিখ থেকে।
          await client.schema('fin').from('partner_pct_history').insert({
            partner_id: r.id, branch: branch, mobile: r.mobile, pct: r.pct,
            effective_from: sd, created_by: me
          });
        } else if (r.id) {
          // পুরনো অংশীদার — (ক) শুরুর তারিখ বদলালে শুধু সবচেয়ে-পুরনো সারির তারিখ ঠিক করা
          //   (অতীতের বাকি সেগমেন্ট অটুট);
          if (r.histFirst && sd !== r.histFirst) {
            await client.schema('fin').from('partner_pct_history')
              .update({ effective_from: sd })
              .eq('branch', branch).eq('mobile', r.mobile).eq('effective_from', r.histFirst);
          }
          // (খ) % বদলালে আগের নিয়মেই আজ থেকে নতুন সেগমেন্ট — পুরনো accrued কখনো বদলায় না।
          var changed = (oldPct == null) || (Number(oldPct) !== Number(r.pct));
          if (changed) {
            await client.schema('fin').from('partner_pct_history').insert({
              partner_id: r.id, branch: branch, mobile: r.mobile, pct: r.pct,
              effective_from: today(), created_by: me
            });
          }
        }
      } catch (e) { alert('Could not save (network?): ' + (e && e.message ? e.message : e)); return; }
    }
    alert('Partner setup saved.');
    finPartnerBranch(branch);
  }

  // mobiles already present as app users (for in_app auto-match flag)
  function knownUsers() {
    try {
      var u = (window.RK_CONFIG || {}).users || {};
      var list = Array.isArray(u) ? u : Object.keys(u).reduce(function (acc, role) { return acc.concat(u[role] || []); }, []);
      return list.map(function (x) { return n10(x.mobile); }).filter(Boolean);
    } catch (e) { return []; }
  }

  // ---------- WITHDRAW / RETURN ----------
  async function finPartnerDraw(branch) {
    if (!guardMaster()) return;
    var c;
    try { c = await computeBranch(branch); } catch (e) { c = { list: [] }; }
    var opts = c.list.map(function (x) { return '<option value="' + esc(n10(x.p.mobile)) + '">' + esc(x.p.name || n10(x.p.mobile)) + '</option>'; }).join('');
    app().innerHTML = head('＋ Withdraw / Return — ' + esc(branch)) +
      '<div class="card" style="padding:14px">' +
      fld('Type', '<select id="dwKind" class="input"><option value="withdraw">Withdraw (money out)</option><option value="return">Return (money back)</option></select>') +
      fld('Partner', '<select id="dwWho" class="input">' + opts + '</select>') +
      fld('Date', '<input id="dwDate" class="input" type="date" value="' + today() + '">') +
      fld('Mode', '<select id="dwMode" class="input"><option value="cash">Cash</option><option value="online">Online</option></select>') +
      fld('Amount ₹', '<input id="dwAmt" class="input" inputmode="decimal" placeholder="0">') +
      fld('Note (optional)', '<input id="dwNote" class="input" placeholder="e.g. cash in hand">') +
      '<div style="display:flex;gap:9px;margin-top:6px">' +
      btn('← Back', 'finPartnerBranch(\'' + esc(branch) + '\')', '#5b6b62') +
      btn('💾 Save', 'finPartnerSaveDraw(\'' + esc(branch) + '\')', '#1E7C43') + '</div></div></div></div>';
  }
  function fld(label, inner) {
    return '<div style="margin-bottom:10px"><div style="font-size:12px;color:#7c8a83;margin-bottom:4px">' + esc(label) + '</div>' + inner + '</div>';
  }
  async function finPartnerSaveDraw(branch) {
    function val(id) { var e = document.getElementById(id); return e ? e.value : ''; }
    var mobile = n10(val('dwWho')), amt = Number(val('dwAmt') || 0);
    if (!mobile) { alert('Select a partner.'); return; }
    if (!(amt > 0)) { alert('Enter an amount.'); return; }
    var client = await sb(), me = myMobile();
    try {
      await client.schema('fin').from('partner_drawings').insert({
        branch: branch, mobile: mobile, entry_date: val('dwDate') || today(),
        amount: amt, kind: val('dwKind') || 'withdraw', mode: val('dwMode') || 'cash',
        note: val('dwNote') || '', created_by: me
      });
    } catch (e) { alert('Could not save (network?): ' + (e && e.message ? e.message : e)); return; }
    alert('Saved.');
    finPartnerBranch(branch);
  }

  // ---------- PRINT / EXPORT (master) — a shareable branch statement ----------
  async function finPartnerExport(branch) {
    if (!guardMaster()) return;
    var c;
    try { c = await computeBranch(branch); } catch (e) { alert('Could not load (network?).'); return; }
    var rows = c.list.map(function (x) {
      var red = x.bal < 0;
      return '<tr>' +
        '<td style="border:1px solid #cfe0d6;padding:6px">' + esc(x.p.name || n10(x.p.mobile)) + '<br><small style="color:#777">+91 ' + esc(n10(x.p.mobile)) + '</small></td>' +
        '<td style="border:1px solid #cfe0d6;padding:6px;text-align:right">' + money(x.due) + '</td>' +
        '<td style="border:1px solid #cfe0d6;padding:6px;text-align:right">' + money(x.drawn) + '</td>' +
        '<td style="border:1px solid #cfe0d6;padding:6px;text-align:right;font-weight:800;color:' + (red ? '#B42318' : '#0A7C3F') + '">' + (red ? '−' : '') + money(Math.abs(x.bal)).replace('₹', '₹') + '</td></tr>';
    }).join('');
    var html = '<div style="font-family:Arial;padding:6px">' +
      '<h2 style="color:#0A5C33;margin:0 0 2px">Partner Shares — ' + esc(branch) + '</h2>' +
      '<div style="color:#555;font-size:12px;margin-bottom:10px">January → ' + esc(today()) + ' · Net Profit: <b>' + money(c.net) + '</b> (Income ' + money(c.income) + ' − Expense ' + money(c.expense) + ')</div>' +
      '<table style="border-collapse:collapse;width:100%;font-size:13px">' +
      '<tr style="background:#EAF6EE;color:#0A5C33"><th style="border:1px solid #cfe0d6;padding:6px;text-align:left">Partner</th>' +
      '<th style="border:1px solid #cfe0d6;padding:6px">Due</th><th style="border:1px solid #cfe0d6;padding:6px">Withdrawn</th><th style="border:1px solid #cfe0d6;padding:6px">Balance</th></tr>' +
      rows + '</table>' +
      '<div style="color:#777;font-size:11px;margin-top:10px">🟢 Balance = still owed to the partner · 🔴 = over-drawn (owes back). Auto-forwards to next year.</div></div>';
    try { M().printHtml('Partner Shares · ' + branch, html); }
    catch (e) { alert('Could not open print.'); }
  }

  // ---------- SETTLEMENT (master) — square everyone to zero ----------
  // For each partner with a non-zero balance we record ONE balancing drawing
  // (green → a 'withdraw' of their +balance = they were paid; red → a 'return'
  // of their over-draw = they paid back) plus a settlement record for history.
  // After this every balance is 0; auto-forward continues cleanly from zero.
  async function finPartnerSettle(branch) {
    if (!guardMaster()) return;
    var c;
    try { c = await computeBranch(branch); } catch (e) { alert('Could not load (network?).'); return; }
    var toDo = c.list.filter(function (x) { return Math.abs(x.bal) >= 0.5; });
    if (!toDo.length) { alert('All balances are already zero — nothing to settle.'); return; }
    var lines = toDo.map(function (x) {
      return (x.p.name || n10(x.p.mobile)) + ': ' + (x.bal > 0 ? 'pay ' : 'collect ') + money(Math.abs(x.bal));
    }).join('\n');
    if (!confirm('Settlement — bring every balance to zero for ' + branch + '?\n\n' + lines + '\n\nThis records the pay-outs / collections. Continue?')) return;
    if (!confirm('Please confirm ONCE MORE — this cannot be undone from here. Proceed with settlement for ' + branch + '?')) return;
    var client = await sb(), me = (M().session() || {}).code || 'master';
    for (var i = 0; i < toDo.length; i++) {
      var x = toDo[i]; var bal = x.bal; var mob10 = n10(x.p.mobile);
      try {
        await client.schema('fin').from('partner_drawings').insert({
          id: M().uuid(), branch: branch, mobile: mob10, entry_date: today(),
          amount: Math.abs(bal), kind: bal > 0 ? 'withdraw' : 'return', mode: 'cash',
          note: 'Settlement', created_by: me, ignored: false
        });
        await client.schema('fin').from('partner_settlements').insert({
          id: M().uuid(), branch: branch, mobile: mob10, settled_on: today(),
          balance_before: bal, note: 'Settlement', created_by: me
        });
      } catch (e) { alert('Settlement failed partway (network?). Please re-check.'); finPartnerBranch(branch); return; }
    }
    alert('Settlement done — all balances are now zero.');
    finPartnerBranch(branch);
  }

  // =====================================================================
  // PARTNER SIDE (V307) — a partner logs in and sees ONLY their own ledger.
  // RLS (V307) guarantees the queries below return only their own rows /
  // their own branches, so no client-side filtering by mobile is needed.
  // % is never shown to the partner. Master's edits are invisible (they see
  // only the final numbers). View-only in this step; entry comes next.
  // =====================================================================
  function myAppMobile() { try { return n10((window.user || {}).mobile); } catch (e) { return ''; } }

  async function partnerHome() { M().gate('My Share Ledger', renderPartnerLedger); }

  function partnerHead() {
    return '<div class="wrap anMod anModPt"><div class="topbar"><b style="color:#0A5C33">📗 My Share Ledger</b>' +
      '<span><button class="ghost" onclick="partnerHome()">↻</button>' +
      '<button class="ghost" onclick="partnerLogout()">Logout</button></span></div><div class="page">';
  }
  function partnerLogout() {
    try { window.user = null; localStorage.removeItem('rk_session'); } catch (e) {}
    try { if (typeof M().signOut === 'function') M().signOut(); } catch (e) {}
    try { location.reload(); } catch (e) { try { location.href = location.pathname; } catch (_e) {} }
  }

  async function renderPartnerLedger() {
    app().innerHTML = partnerHead() + '<div id="pBody">Loading…</div></div></div>';
    var client = await sb();
    var mine = [];
    try { mine = (await client.schema('fin').from('partners').select('*').eq('active', true)).data || []; }
    catch (e) { document.getElementById('pBody').innerHTML = '<div class="card mut">Could not load (offline?).</div>'; return; }
    if (!mine.length) { document.getElementById('pBody').innerHTML = '<div class="card mut">No partner record found for your login yet. Please contact the master.</div>'; return; }
    var html = '';
    for (var i = 0; i < mine.length; i++) {
      html += '<div id="pbr' + i + '"><div class="card mut">Loading ' + esc(mine[i].branch) + '…</div></div>';
    }
    document.getElementById('pBody').innerHTML = html;
    for (var j = 0; j < mine.length; j++) { renderOnePartnerBranch(mine[j], 'pbr' + j); }
  }

  async function renderOnePartnerBranch(pr, hostId) {
    var client = await sb();
    var branch = pr.branch;
    var income = 0, expense = 0, drawn = 0;
    try {
      var coll = (await client.schema('fin').from('collections')
        .select('cash,online,expense_total,expense_notes,entry_date')
        .gte('entry_date', yearStart()).lte('entry_date', today()).eq('branch', branch).eq('ignored', false)).data || [];
      coll.forEach(function (r) { income += Number(r.cash || 0) + Number(r.online || 0); expense += rowExpense(r); });
      var exps = (await client.schema('fin').from('expenses').select('amount,entry_date')
        .gte('entry_date', yearStart()).lte('entry_date', today()).eq('branch', branch).eq('ignored', false)).data || [];
      exps.forEach(function (e) { expense += Number(e.amount || 0); });
      var dr = (await client.schema('fin').from('partner_drawings').select('amount,kind,entry_date').eq('branch', branch).eq('ignored', false)).data || [];
      dr.forEach(function (d) { drawn += (d.kind === 'return' ? -Number(d.amount || 0) : Number(d.amount || 0)); });
      var hist = (await client.schema('fin').from('partner_pct_history').select('mobile,pct,effective_from').eq('branch', branch)).data || [];
      var myHist = hist.filter(function (h) { return n10(h.mobile) === n10(pr.mobile); });
    } catch (e) {
      var h0 = document.getElementById(hostId); if (h0) h0.innerHTML = '<div class="card mut">Could not load ' + esc(branch) + '.</div>'; return;
    }
    var net = income - expense;
    var share = accruedFor(myHist, coll, exps, pr.pct);      // % never shown, only the amount
    var due = Number(pr.opening || 0) + share;
    var bal = due - drawn;
    var red = bal < 0;
    var netCard = '<div class="card" style="padding:13px"><div style="font-weight:800;color:#0A5C33;font-size:13px;margin-bottom:6px">' + esc(branch) + ' · Jan → Today</div>' +
      row2('Total Income', money(income), '#123') +
      row2('Total Expense', money(expense), '#B42318') +
      row2('Net Profit', money(net), net < 0 ? '#B42318' : '#0A7C3F', true) + '</div>';
    var shareCard = '<div class="card" style="padding:13px"><div style="font-weight:800;color:#0A5C33;font-size:13px;margin-bottom:6px">My Share Account</div>' +
      row2('Previous Year Balance (opening)', money(Number(pr.opening || 0)), Number(pr.opening || 0) < 0 ? '#B42318' : '#0A7C3F') +
      row2("This Year's Share", money(share), '#123') +
      row2('Total Due', money(due), '#123', true) +
      row2('Total Withdrawn', money(-drawn), '#B42318') +
      row2('Current Balance', (red ? '🔴 ' : '🟢 ') + money(bal), red ? '#B42318' : '#0A7C3F', true) + '</div>';
    var note = red
      ? '<div style="background:#FBEAE8;color:#8f2a20;border:1px dashed #e0a49c;border-radius:10px;padding:9px 11px;font-size:12px">🔴 You have taken ' + money(-bal) + ' more than your share. You may return it, or it adjusts from your next share.</div>'
      : '<div style="background:#E7F6EC;color:#0A6b38;border:1px dashed #9dd3af;border-radius:10px;padding:9px 11px;font-size:12px">🟢 Your ' + money(bal) + ' share is still held in the business — you may withdraw it.</div>';
    var entryBtn = '';
    if (pr.can_entry) {
      entryBtn = '<div style="display:flex;gap:9px;margin-top:10px">' +
        '<div onclick="partnerAddIncome(\'' + esc(branch) + '\')" style="flex:1;cursor:pointer;text-align:center;color:#fff;font-weight:800;font-size:13px;border-radius:12px;padding:12px;background:#1E7C43">＋ Add Income</div>' +
        '<div onclick="partnerAddExpense(\'' + esc(branch) + '\')" style="flex:1;cursor:pointer;text-align:center;color:#fff;font-weight:800;font-size:13px;border-radius:12px;padding:12px;background:#C0271B">＋ Add Expense</div></div>';
    }
    var host = document.getElementById(hostId);
    if (host) host.innerHTML = netCard + shareCard + note + entryBtn + '<div style="height:10px"></div>';
  }

  // Partner adds Income for their branch (only if master turned their toggle on).
  // created_by = their own mobile → passes the V307 insert policy. The entry is
  // editable by the partner ONLY on the day it is entered (enforced by RLS).
  function partnerAddIncome(branch) {
    app().innerHTML = partnerHead() +
      '<div class="card" style="padding:14px"><div style="font-weight:800;color:#0A5C33;margin-bottom:8px">＋ Add Income · ' + esc(branch) + '</div>' +
      fldP('Date', '<input id="piDate" class="input" type="date" value="' + today() + '">') +
      fldP('Cash ₹', '<input id="piCash" class="input" inputmode="decimal" placeholder="0">') +
      fldP('Online ₹', '<input id="piOnline" class="input" inputmode="decimal" placeholder="0">') +
      '<div style="background:#EAF6EE;color:#0A6b38;border:1px dashed #9dd3af;border-radius:10px;padding:9px 11px;font-size:11.5px;margin-bottom:10px">✎ You can fix this entry today only. From tomorrow it is locked.</div>' +
      '<div style="display:flex;gap:9px">' +
      '<div onclick="partnerHome()" style="flex:1;text-align:center;color:#fff;font-weight:800;border-radius:12px;padding:12px;background:#5b6b62;cursor:pointer">← Back</div>' +
      '<div onclick="partnerSaveIncome(\'' + esc(branch) + '\')" style="flex:1;text-align:center;color:#fff;font-weight:800;border-radius:12px;padding:12px;background:#1E7C43;cursor:pointer">💾 Save</div>' +
      '</div></div></div></div>';
  }
  function fldP(label, inner) {
    return '<div style="margin-bottom:10px"><div style="font-size:12px;color:#7c8a83;margin-bottom:4px">' + esc(label) + '</div>' + inner + '</div>';
  }
  async function partnerSaveIncome(branch) {
    function v(id) { var e = document.getElementById(id); return e ? e.value : ''; }
    var cash = Number(v('piCash') || 0), online = Number(v('piOnline') || 0);
    if (cash <= 0 && online <= 0) { alert('Enter Cash or Online (at least one).'); return; }
    var mob10 = myAppMobile();
    if (mob10.length !== 10) { alert('Your login mobile is missing — please log in again.'); return; }
    var client = await sb();
    var row = { id: M().uuid(), entry_date: v('piDate') || today(), branch: branch, cash: cash, online: online, note: '', created_by: mob10, ignored: false };
    try { await client.schema('fin').from('collections').insert(row); }
    catch (e) { alert('Could not save (network?): ' + (e && e.message ? e.message : e)); return; }
    alert('Saved.');
    partnerHome();
  }

  // Expense categories — same list the master uses (finance.js CATS).
  var PCATS = ['RMP Commission', 'Staff unexpected time Commission', 'Staff Salary', 'Chamber Rent',
    'Bills — Electricity / Water / Internet', 'Medicine / Surgical', 'Advertisement',
    'Office — Printing / Cleaning / Repair / Equipment', 'Transport / Parcel', 'Food',
    'License / Govt Fee', 'Other Expense'];
  function partnerAddExpense(branch) {
    var opts = PCATS.map(function (c) { return '<option value="' + esc(c) + '">' + esc(c) + '</option>'; }).join('');
    app().innerHTML = partnerHead() +
      '<div class="card" style="padding:14px"><div style="font-weight:800;color:#B42318;margin-bottom:8px">＋ Add Expense · ' + esc(branch) + '</div>' +
      fldP('Date', '<input id="peDate" class="input" type="date" value="' + today() + '">') +
      fldP('Category', '<select id="peCat" class="input">' + opts + '</select>') +
      fldP('Amount ₹', '<input id="peAmt" class="input" inputmode="decimal" placeholder="0">') +
      fldP('Mode', '<select id="peMode" class="input"><option value="Cash">Cash</option><option value="Online">Online</option></select>') +
      fldP('Note (optional)', '<input id="peNote" class="input" placeholder="e.g. paid to…">') +
      '<div style="background:#FBEAE8;color:#8f2a20;border:1px dashed #e0a49c;border-radius:10px;padding:9px 11px;font-size:11.5px;margin-bottom:10px">✎ You can fix this entry today only. From tomorrow it is locked.</div>' +
      '<div style="display:flex;gap:9px">' +
      '<div onclick="partnerHome()" style="flex:1;text-align:center;color:#fff;font-weight:800;border-radius:12px;padding:12px;background:#5b6b62;cursor:pointer">← Back</div>' +
      '<div onclick="partnerSaveExpense(\'' + esc(branch) + '\')" style="flex:1;text-align:center;color:#fff;font-weight:800;border-radius:12px;padding:12px;background:#C0271B;cursor:pointer">💾 Save</div>' +
      '</div></div></div></div>';
  }
  async function partnerSaveExpense(branch) {
    function v(id) { var e = document.getElementById(id); return e ? e.value : ''; }
    var amt = Number(v('peAmt') || 0);
    if (!(amt > 0)) { alert('Enter an amount.'); return; }
    var mob10 = myAppMobile();
    if (mob10.length !== 10) { alert('Your login mobile is missing — please log in again.'); return; }
    var client = await sb();
    var row = { id: M().uuid(), entry_date: v('peDate') || today(), branch: branch, category: v('peCat') || 'Other Expense',
      paid_to: '', amount: amt, mode: v('peMode') || 'Cash', note: v('peNote') || '', created_by: mob10, ignored: false };
    try { await client.schema('fin').from('expenses').insert(row); }
    catch (e) { alert('Could not save (network?): ' + (e && e.message ? e.message : e)); return; }
    alert('Saved.');
    partnerHome();
  }

  // expose
  window.finPartners = finPartners;
  window.finPartnerSettle = finPartnerSettle;
  window.finPartnerExport = finPartnerExport;
  window.partnerHome = partnerHome;
  window.partnerLogout = partnerLogout;
  window.partnerAddIncome = partnerAddIncome;
  window.partnerSaveIncome = partnerSaveIncome;
  window.partnerAddExpense = partnerAddExpense;
  window.partnerSaveExpense = partnerSaveExpense;
  window.finPartnerBranch = finPartnerBranch;
  window.finPartnerSetup = finPartnerSetup;
  window.finPartnerAddRow = finPartnerAddRow;
  window.finPartnerRefresh = finPartnerRefresh;
  window.finPartnerSaveSetup = finPartnerSaveSetup;
  window.finPartnerDraw = finPartnerDraw;
  window.finPartnerSaveDraw = finPartnerSaveDraw;
})();
