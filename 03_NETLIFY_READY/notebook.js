/* =====================================================================
   V245 MODULE 2 — MY WORK NOTEBOOK + DAILY/MONTHLY REPORT + OUTSIDE CALLS
   Additive, isolated. Automatic counts are READ-ONLY from existing app
   data (localStorage rk_* tables) — nothing is written back, so no
   duplicate Patient/Enquiry/Payment/Follow-up/Call/Attendance is created.
   Notebook data lives in schema `wn` (RLS: staff owns own; Master sees all).

   🎨 (03.08.2026, TK-অনুমোদিত ফটো-প্রুফ পাশ করার পরে) — সম্পূর্ণ নতুন
   এক-ফর্ম ডিজাইনে বসানো হলো, ফোনের B342-এর সাথে মিলিয়ে: IN TIME/OUT TIME
   (auto বোতাম) → Mark as Leave → New Enquiry/Registration/App Calls/
   Total call (ধূসর AUTO বাক্স, এডিট করা যায় না) → Today Patient (auto-
   suggest + এডিটযোগ্য, persist হয় না — শুধু রিপোর্ট-টেক্সটে যায়) →
   Outside Calls Today (এডিটযোগ্য সংখ্যা, persist হয়) → Notes (একটাই
   টেক্সট-বাক্স) → একটাই "✔ Submit Report to Master" বোতাম (সেভ + রিপোর্ট
   তৈরি + Master-কে জমা + WhatsApp/Share একসাথে)।

   🔴 আবিষ্কার (এই সেশনে, ওয়েব-ভার্সন বানাতে গিয়ে): ফোনের is_leave/
   leave_reason/outside_calls_manual/day_note কলাম কখনো Supabase-এ
   ALTER TABLE দিয়ে যোগ করা হয়নি (V246_ONE_RUN_SETUP-এ ছিল না, পরেও
   কোনো patch হয়নি) — তাই ফোনেও এই ফিচারগুলো এতদিন cloud-এ আসলে সেভ
   হচ্ছিল না (upsert ব্যর্থ হয়ে চুপচাপ false ফেরত দিত)। নতুন SQL
   (V256_WORK_NOTEBOOK_MISSING_COLUMNS.sql) এই ফাঁক বন্ধ করেছে — TK-কে
   একবার Supabase-এ চালাতে হবে, নইলে নিচের ফিচারগুলো (Mark as Leave,
   Outside Calls Today, Notes) ওয়েবেও একই কারণে সেভ হবে না।

   ⛔ পুরনো ফিচার (Work Entries লিস্ট, ব্যক্তিগত Outside-Call লগ,
   Personal Notes/Carry-forward/Problem-Help, Calculator, Sheet) — এই
   ফর্মে আর দেখানো হয় না, কিন্তু নিচের পুরনো ফাংশনগুলো (drawEntries,
   nbAddEntry, nbToggleEntry, drawOutside, nbAddOutside, outsideCallCount)
   কোডে অক্ষত রাখা হয়েছে (মোছা হয়নি, প্রজেক্টের নিয়ম অনুযায়ী) — শুধু
   নতুন পর্দা থেকে আর ডাকা হয় না।
   ===================================================================== */
(function () {
  function sb() { return window.MOD.client(); }
  function appUser() { try { return window.user || JSON.parse(localStorage.getItem('rk_session') || '{}'); } catch (e) { return {}; } }
  function rawLoad(t) { try { return JSON.parse(localStorage.getItem('rk_' + t) || '[]'); } catch (e) { return []; } }
  function mobEq(a, b) { return String(a || '').replace(/\D/g, '').slice(-10) === String(b || '').replace(/\D/g, '').slice(-10); }
  function onDate(x, date) {
    var c = String(x.createdAt || '').slice(0, 10);
    return c === date || String(x.date || '') === date || String(x.registrationDate || '') === date;
  }
  function inMonth(x, ym) {
    var c = String(x.createdAt || '').slice(0, 7);
    return c === ym || String(x.date || '').slice(0, 7) === ym;
  }
  // "YYYY-MM-DD" -> "DD.MM.YYYY" (প্রজেক্টের DOT-তারিখ নিয়ম, ফোনের dotDate()-এর হুবহু একই লজিক)
  function dotDate(iso) { var p = String(iso || '').split('-'); return p.length === 3 ? (p[2] + '.' + p[1] + '.' + p[0]) : iso; }
  // "HH:mm" (২৪-ঘণ্টা) -> "h.mm AM/PM" — ফোনের displayTime12()-এর হুবহু একই লজিক
  function displayTime12(hhmm) {
    if (!hhmm) return '';
    var p = String(hhmm).split(':'); if (p.length !== 2) return hhmm;
    var h24 = parseInt(p[0], 10); if (isNaN(h24)) return hhmm;
    var ampm = h24 < 12 ? 'AM' : 'PM';
    var h12 = h24 === 0 ? 12 : (h24 > 12 ? h24 - 12 : h24);
    return h12 + '.' + p[1] + ' ' + ampm;
  }
  // এখনকার সময় "৯.০৫am" ফরম্যাটে — ফোনের shareTimeLabel()-এর হুবহু একই লজিক
  function shareTimeLabel() {
    var d = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' }));
    var h24 = d.getHours(), mm = d.getMinutes();
    var ampm = h24 < 12 ? 'am' : 'pm';
    var h12 = h24 === 0 ? 12 : (h24 > 12 ? h24 - 12 : h24);
    return h12 + '.' + String(mm).padStart(2, '0') + ampm;
  }

  // ---- automatic statistics (read-only) ----
  function autoStats(range) { // range = {mode:'day'|'month', key:'YYYY-MM-DD'|'YYYY-MM'}
    var u = appUser(), mob = u.mobile, nm = u.name;
    function mine(x) { return mobEq(x.createdBy, mob) || mobEq(x.receivedBy, mob) || mobEq(x.registeredBy, mob); }
    function inRange(x) { return range.mode === 'day' ? onDate(x, range.key) : inMonth(x, range.key); }
    var enq = rawLoad('enquiries').filter(function (x) { return mine(x) && inRange(x); }).length;
    var reg = rawLoad('patients').filter(function (x) { return mine(x) && inRange(x); }).length;
    return { enquiries: enq, registrations: reg };
  }

  // 🔴🔴🔴 V509 (২১.০৮.২০২৬, TK-রিপোর্ট — "Monthly রিপোর্টে অ্যাপসের কল শূন্য
  // দেখাচ্ছে কেন?"): মাসের শেষ সীমা হিসেবে এতদিন "৩২ তারিখ" বা "৩১ তারিখ" লেখা
  // হত। কিন্তু `call_date`/`work_date` **সত্যিকারের তারিখের ঘর (date)** —
  //   • "2026-08-32" পৃথিবীতে নেই → ডেটাবেস অনুরোধটাই বাতিল করে → গোনা 0।
  //   • "-31" আরও চালাক ভুল: ৩১ দিনের মাসে কাজ করে, কিন্তু ফেব্রুয়ারি ·
  //     এপ্রিল · জুন · সেপ্টেম্বর · নভেম্বরে "2026-09-31" অসম্ভব তারিখ → 0।
  //     তাই এটা মাসে-মাসে লুকিয়ে থাকত, ধরা পড়ত না।
  // ⇒ এখন সবসময় **পরের মাসের ১ তারিখ** (ডিসেম্বর হলে পরের বছরের জানুয়ারি ১)
  //   ব্যবহার করা হয় — সবসময় বৈধ তারিখ, আর মাসের শেষ দিনটাও পুরো ধরা পড়ে।
  // ⛔ ফোনের অ্যাপেও (WorkNotebookActivity.monthEndExclusive) হুবহু একই নিয়ম,
  //    তাই ফোন আর কম্পিউটার আর কখনো আলাদা সংখ্যা দেখাবে না।
  function monthEndExclusive(ym) {
    try {
      var y = parseInt(String(ym).substring(0, 4), 10);
      var m = parseInt(String(ym).substring(5, 7), 10);
      if (!y || !m) return ym + '-32';
      if (m >= 12) return String(y + 1) + '-01-01';
      return String(y) + '-' + (m + 1 < 10 ? '0' : '') + String(m + 1) + '-01';
    } catch (e) { return ym + '-32'; }
  }
  async function appCallCount(range) {
    var client = await sb(); var code = (window.MOD.session() || {}).code;
    try {
      var q = client.schema('wn').from('call_taps').select('id', { count: 'exact', head: true }).eq('staff_code', code);
      if (range.mode === 'day') q = q.eq('call_date', range.key);
      else q = q.gte('call_date', range.key + '-01').lt('call_date', monthEndExclusive(range.key));
      var r = await q;
      /* 🔴🔒 V593 (২৩.০৮.২০২৬) — ব্যর্থ পড়াকে আর "0" বলা হয় না।
         আগে নেট দুর্বল হলে বা পড়া না গেলে এখানে 0 ফিরত, আর সেই মিথ্যা 0
         পর্দাতেও বসত, **স্টাফের পাঠানো WhatsApp রিপোর্টেও** চলে যেত।
         ⇒ এখন `null` ফেরে, আর নিচে সবখানে `null` মানে "…" — ফোনের V593-এর
           হুবহু একই নিয়ম (`WorkNotebookActivity.callTxt()`)।
         ⛔ পড়া সফল হলে সংখ্যা হুবহু আগের মতোই।
         ⛔ নিচের `outsideCallCount()` ছোঁয়া হয়নি — ওটা আর ডাকাই হয় না
            (বাইরের কল এখন `day.outside_calls_manual` থেকে আসে)। */
      if (r && r.error) return null;
      return (r && typeof r.count === 'number') ? r.count : null;
    } catch (e) { return null; }
  }
  /** কল-সংখ্যা লেখার একমাত্র জায়গা — না পড়তে পারলে "…" (ফোনের callTxt-এর যমজ) */
  function callTxt(v) { return (v === null || v === undefined) ? '…' : String(v); }
  function callSum(a, b) { return (a === null || a === undefined) ? null : (Number(a) + Number(b || 0)); }

  // ⛔ পুরনো, আর ডাকা হয় না (B330-এর সাথে মিলিয়ে Outside Calls এখন
  // day.outside_calls_manual থেকে আসে) — মুছে ফেলা হয়নি, ভবিষ্যতে দরকার
  // হলে ফিরিয়ে আনা যাবে।
  async function outsideCallCount(range) {
    var client = await sb(); var code = (window.MOD.session() || {}).code;
    try {
      var q = client.schema('wn').from('outside_calls').select('id', { count: 'exact', head: true }).eq('staff_code', code);
      if (range.mode === 'day') q = q.eq('call_date', range.key);
      else q = q.gte('call_date', range.key + '-01').lt('call_date', monthEndExclusive(range.key));
      var r = await q; return r.count || 0;
    } catch (e) { return 0; }
  }
  // মাসের সব দিনের outside_calls_manual যোগ করে বার করা — ফোনের fetchStats()-এর একই লজিক
  async function monthlyOutsideCalls(ym) {
    var client = await sb(); var code = (window.MOD.session() || {}).code;
    try {
      var r = await client.schema('wn').from('notebook_days').select('outside_calls_manual').eq('staff_code', code).gte('work_date', ym + '-01').lt('work_date', monthEndExclusive(ym));
      return (r.data || []).reduce(function (s, x) { return s + Number(x.outside_calls_manual || 0); }, 0);
    } catch (e) { return 0; }
  }
  // মাসে কয়টা দিন ছুটি মার্ক করা হয়েছে — ফোনের leaveDays-এর একই লজিক
  async function monthlyLeaveDays(ym) {
    var client = await sb(); var code = (window.MOD.session() || {}).code;
    try {
      var r = await client.schema('wn').from('notebook_days').select('id', { count: 'exact', head: true }).eq('staff_code', code).eq('is_leave', true).gte('work_date', ym + '-01').lt('work_date', monthEndExclusive(ym));
      return r.count || 0;
    } catch (e) { return 0; }
  }
  // আজ Chamber Attendance-এ কতজন Arrived মার্ক হয়েছে — এই কম্পিউটারের
  // স্থানীয় জমানো তালিকা থেকে (নতুন কোনো cloud-কল না) — শুধু "auto-suggest",
  // persist হয় না, Today Patient ঘরে প্রি-ফিল হয়ে বসে, staff চাইলে বদলাতে পারেন।
  function todayArrivedCount() {
    var date = window.MOD.todayIST();
    var pays = rawLoad('payments');
    return pays.filter(function (x) { return String(x.payType || '').toLowerCase() === 'attendance_mark' && String(x.date || '') === date; }).length;
  }

  // ---- entry point ----
  async function workNotebook() { window.MOD.gate('My Work Notebook', renderToday); }

  /* ══════════════════════════════════════════════════════════════════════
     🔴🔴🔒 V512 (২১.০৮.২০২৬) — **OUT TIME যেন আর হারিয়ে না যায়** (ফোনের
     V511-এর ওয়েব জোড়া)।

     TK-এর রিপোর্ট: *"staff বলছে আমি যখন চেম্বার থেকে বেরোলাম তখন আমাকে
     Out time show করছিল না, তাই আমি দিতে পারি নাই।"*

     ─── কারণ (কোড ধরে প্রমাণিত, আন্দাজে নয়) ─────────────────────────────
     আগের `loadDay()` ধরে নিত যে পড়া ব্যর্থ হলে **ব্যতিক্রম (throw)** হবে,
     তাই জমানো কপি পড়ার কাজটা `catch`-এ রাখা ছিল। কিন্তু supabase-js ভুল
     হলে throw করে না — সে `{ data: null, error: {...} }` ফেরায়। ফলে
     `catch` কখনো চলত না, `r.data` হত `null`, আর `renderToday()`-র
     `(await loadDay(date)) || { …ফাঁকা… }` একটা **ফাঁকা দিন** বানাত ⇒
     `inSet` / `outSet` দুটোই `false` ⇒ **OUT TIME বোতাম ধূসর, চাপাই যেত না**।
     অর্থাৎ স্টাফের অভিযোগটা সত্যি ছিল, ভুল তাঁর নয়।

     দ্বিতীয় পথও আছে: নেট খারাপ থাকায় IN TIME শুধু **ফোনেই** জমা হয়ে
     `MOD.queueWrite()`-এ অপেক্ষায় বসে থাকে। তখন ক্লাউড সত্যিই বলে "আজকের
     সারি নেই" — কিন্তু এই ফোনে আজকের কপি আছে। আগে ঐ কপিটা দেখা হতো না।

     ─── এখন যা হয় ───────────────────────────────────────────────────────
      · `r.error` **স্পষ্ট করে** দেখা হয় (throw-এর ভরসায় থাকা হয় না)।
      · ক্লাউড থেকে সারি এলে সেটা ফোনেও জমা থাকে (`nb_<তারিখ>`)।
      · পড়া ব্যর্থ হলে জমানো কপি দেখানো হয় → `nbDayFromCache`।
      · ক্লাউড বলল "সারি নেই" অথচ ফোনে আজকের IN/OUT আছে → জমানো কপিই সত্যি।
      · কিছুই না পেলে **ভুল অবস্থা না দেখিয়ে** `nbDayLoadFailed` — পর্দায়
        সৎ বার্তা ও 🔄 Try again (নিচে `renderToday`)।

     ⛔ সেভের নিয়মে (`nbCheck` / `nbSaveDay` / `nbSubmitDaily`) **এক অক্ষরও**
        হাত পড়েনি — শুধু "কী দেখানো হবে" ঠিক হলো।
     ⛔ একটাও নতুন ক্লাউড-অনুরোধ যোগ হয়নি।
     ══════════════════════════════════════════════════════════════════════ */
  var nbDayFromCache = false, nbDayLoadFailed = false;

  /* ⛔⛔ V512 (নিজের কাজ আবার যাচাই করে সংশোধন) — চাবিতে **স্টাফের কোডও**
     থাকতেই হবে। আগে চাবি ছিল শুধু `nb_<তারিখ>`; ক্লিনিকের একই কম্পিউটারে
     একজনের পরে আরেকজন লগইন করলে দ্বিতীয়জন **প্রথমজনের IN/OUT TIME**
     দেখতেন, নিজের হাজিরা দিতেই পারতেন না, আর সেভ করলে প্রথমজনের সারিতে
     লেখা চলে যেত। এখন চাবি `nb_<কোড>_<তারিখ>`, আর ভিতরের `staff_code` ও
     `work_date` **মিলিয়েও** দেখা হয় — না মিললে জমানো কপি ব্যবহারই হয় না। */
  function nbDayCacheKey(code, date) { return 'nb_' + String(code || '') + '_' + date; }
  function nbCacheOk(c, code, date) {
    return !!(c && String(c.staff_code || '') === String(code || '') && String(c.work_date || '') === String(date));
  }
  function nbLoadDayCache(code, date) {
    try {
      var c = window.MOD.localGet(nbDayCacheKey(code, date));
      if (nbCacheOk(c, code, date)) return c;
      /* পুরোনো (V512-এর আগের) চাবি — শুধু তখনই মানা হয় যখন ভিতরের কোড ও
         তারিখ দুটোই মেলে, তাই অন্য স্টাফের কপি কখনো ব্যবহার হবে না। */
      var old = window.MOD.localGet('nb_' + date);
      if (nbCacheOk(old, code, date)) return old;
    } catch (e) {}
    return null;
  }
  function nbSaveDayCache(day, force) {
    try {
      if (!day || !day.work_date || !day.staff_code) return;
      if (!force) {
        /* ⛔ ক্লাউডের পুরোনো কপি দিয়ে ফোনের **নতুন** কপি মুছে ফেলা চলবে না
           (অপেক্ষায় থাকা OUT TIME তখনো ক্লাউডে না পৌঁছাতে পারে)। */
        var have = window.MOD.localGet(nbDayCacheKey(day.staff_code, day.work_date));
        if (have && String(have.updated_at || '') > String(day.updated_at || '')) return;
      }
      window.MOD.localSet(nbDayCacheKey(day.staff_code, day.work_date), day);
    } catch (e) {}
  }
  /* এই সারিটার সেভ কি এখনো পাঠানোর অপেক্ষায়? (`MOD.queueWrite`-এর তালিকা) */
  function nbHasPendingWrite(id) {
    try {
      var q = window.MOD.localGet('pending') || [];
      for (var i = 0; i < q.length; i++) {
        var x = q[i];
        if (x && x.table === 'notebook_days' && x.row && String(x.row.id) === String(id)) return true;
      }
    } catch (e) {}
    return false;
  }

  async function loadDay(date) {
    nbDayFromCache = false; nbDayLoadFailed = false;
    var code = (window.MOD.session() || {}).code;
    var cached = nbLoadDayCache(code, date);
    try {
      var client = await sb();
      if (!client) throw new Error('no cloud client');
      var r = await client.schema('wn').from('notebook_days').select('*').eq('staff_code', code).eq('work_date', date).maybeSingle();
      if (r && r.error) throw r.error;          /* ⛔ supabase-js নিজে throw করে না */
      var row = r ? r.data : null;
      if (row) { nbSaveDayCache(row); return row; }
      /* ক্লাউডে আজকের সারি নেই। জমানো কপিটা তখনই সত্যি ধরা হয় যখন **প্রমাণ
         আছে** যে ওটার সেভ এখনো পাঠানোর অপেক্ষায় (`MOD.queueWrite`)। নইলে
         সারিটা ইচ্ছে করে মুছে ফেলা হয়েছিল — জমানো কপি দেখালে ও পরে সেভ
         করলে সেটা আবার ফিরে আসত। */
      if (cached && (cached.check_in || cached.check_out) && nbHasPendingWrite(cached.id)) {
        nbDayFromCache = true; return cached;
      }
      return null;                               /* সত্যিই আজ কিছু হয়নি */
    } catch (e) {
      if (cached) { nbDayFromCache = true; return cached; }
      nbDayLoadFailed = true;
      return null;
    }
  }

  // আজকের আগের-লেখা Work Entry (পুরনো ফিচার) থাকলে, Notes বাক্স প্রথমবার
  // খালি (day_note না থাকলে) সেই লেখাগুলো দিয়েই প্রি-ফিল হবে — হারাবে না।
  function workEntriesSummary(day) {
    var arr = day.manual_entries || [];
    if (!arr.length) return '';
    return arr.map(function (e) { return '• ' + (e.time || '') + ' ' + (e.text || '') + ' [' + (e.status || 'pending') + ']'; }).join('\n');
  }

/* 👨‍⚕️🔒 V1032 — ওই দিনে ওই স্টাফ কতজন **আলাদা** ডাক্তারের কাছে গেছেন।
   নিয়মটা ফোনের `DoctorVisitDayCount`-এর হুবহু: প্রতিটা ডাক্তারের
   `callHistory`-তে কল/ভিজিট লেখার সময় **তারিখ** ও **কে** বসে; সেটাই গোনা হয়।
   ⛔ কোথাও কিছু লেখা হয় না — শুধু জমা তালিকা থেকে পড়া। */
function nbDoctorVisitCount(dateIso, staffCode){
  try{
    var day = String(dateIso || '').slice(0, 10);
    if (day.length !== 10) return 0;
    var me = '';
    try{
      var acc = (typeof allUsers === 'function' ? allUsers() : []) || [];
      for (var i = 0; i < acc.length; i++){
        if (String(acc[i].name || '').toUpperCase() === String(staffCode || '').toUpperCase()){
          me = String(acc[i].mobile || '').replace(/\D/g, '').slice(-10); break;
        }
      }
    }catch(e){}
    if (!me) me = String((typeof user !== 'undefined' && user && user.mobile) || '').replace(/\D/g, '').slice(-10);
    if (me.length !== 10) return 0;
    var rows = (typeof load === 'function' ? load('doctor_visits') : []) || [];
    var seen = {}, n = 0;
    for (var r = 0; r < rows.length; r++){
      var d = rows[r]; if (!d || !d.id || seen[d.id]) continue;
      var hist = d.callHistory;
      if (typeof hist === 'string'){ try{ hist = JSON.parse(hist); }catch(e){ hist = null; } }
      if (!hist || !hist.length) continue;
      for (var h = 0; h < hist.length; h++){
        var e2 = hist[h]; if (!e2) continue;
        if (String(e2.date || '').slice(0, 10) !== day) continue;
        if (String(e2.by || '').replace(/\D/g, '').slice(-10) !== me) continue;
        seen[d.id] = 1; n++; break;
      }
    }
    return n;
  }catch(e){ return 0; }
}
  async function renderToday() {
    var m = window.MOD, code = (m.session() || {}).code, date = m.todayIST();
    var loadedDay = await loadDay(date);
    var day = loadedDay || { id: m.uuid(), staff_code: code, work_date: date, is_leave: false, leave_reason: '', outside_calls_manual: 0, day_note: '' };
    window._nbDay = day;
    var st = autoStats({ mode: 'day', key: date });
    var apc = await appCallCount({ mode: 'day', key: date });
    var host = document.getElementById('app');
    var isLeave = !!day.is_leave;
    var suggestedPatients = todayArrivedCount();

    function panel(title, rowsHtml) {
      /* V386: ক্লাস যোগ — চেহারা CSS থেকে (ModuleUi.kt)। ⛔ ভিতরের কিছুই বদলায়নি। */
      /* 🔧 V432 (নিজের যাচাইয়ে ধরা পড়েছে) — V430-এ শিরোনামের নির্দেশ-লাইনগুলো
         তুলে দেওয়ার পরে **শিরোনামের ফাঁকা পটিটা** (শুধু একটা সবুজ ফোঁটা) পড়ে
         ছিল। নাম না থাকলে এখন পুরো পটিটাই আর বসে না। */
      var head = title ? ('<div class="nbPanelHead" style="padding:13px 20px;border-bottom:1px solid #EEF1F4;font-size:13px;font-weight:700;color:#101828;display:flex;align-items:center;gap:8px">' +
        '<span class="nbDot" style="width:7px;height:7px;border-radius:50%;background:#0B6B3A;display:inline-block"></span>' + title + '</div>') : '';
      return '<div class="nbPanel" style="background:#fff;border:1px solid #E4E8EE;border-radius:12px;box-shadow:0 1px 3px rgba(16,24,40,0.04);overflow:hidden">' +
        head + rowsHtml + '</div>';
    }
    /* 🔴 V430 — ঐচ্ছিক `id` যোগ করা হলো, যাতে "Total call (auto)" ঘরটা
       বাইরের কল লেখার সঙ্গে সঙ্গে বদলে যেতে পারে (ফোনে ঠিক তাই হয় —
       WorkNotebookActivity.kt:1411 refreshTotal)। */
    function autoRow(label, val, id) {
      return '<div class="nbRow" style="display:flex;justify-content:space-between;padding:11px 20px;border-bottom:1px solid #F5F6F8;font-size:13.5px">' +
        '<span style="color:#667085">' + label + '</span><span' + (id ? ' id="' + id + '"' : '') + ' style="color:#98A2B3;font-style:italic">' + val + '</span></div>';
    }
    function editRow(id, label, val, placeholder) {
      return '<div class="nbRow nbEdit" style="padding:11px 20px;border-bottom:1px solid #F5F6F8">' +
        '<label style="display:block;font-size:11px;color:#667085;margin-bottom:5px;font-weight:600;text-transform:uppercase;letter-spacing:.3px">' + label + '</label>' +
        '<input id="' + id + '" class="input" type="number" value="' + m.esc(val) + '" placeholder="' + (placeholder || '') + '" style="margin:0;padding:8px 10px;font-size:13.5px;border-radius:8px">' +
        '</div>';
    }

    var attendanceHtml;
    if (isLeave) {
      attendanceHtml = '<div class="nbAtt" style="background:#fff;border:1px solid #E4E8EE;border-radius:12px;padding:20px 26px;box-shadow:0 1px 3px rgba(16,24,40,0.04);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px">' +
        '<div><div style="font-size:19px;font-weight:700">' + m.esc(date) + '</div><div style="font-size:13px;color:#667085;margin-top:2px">' + m.esc(code) + '</div></div>' +
        '<div style="font-size:14px;color:#0B6B3A;font-weight:600">🏖️ On Leave' + (day.leave_reason ? (': ' + m.esc(day.leave_reason)) : '') + '</div>' +
        '<button class="ghost" onclick="nbCancelLeave()" style="margin:0">Cancel Leave</button>' +
        '</div>';
    } else {
      var inSet = !!day.check_in, outSet = !!day.check_out;
      // 🔵 B615 (11.08.2026, TK-নির্দেশ, Android-এর মতো): ডিউটি সকাল ৯টা–সন্ধ্যা
      // ৬টা। IN TIME সকালের কাজ — দুপুর ১২টা (৩ ঘণ্টা দেরির ছাড়) পার হলে আর IN
      // দেওয়ার মানে নেই। তাই ১২টার পর IN বোতাম নিষ্ক্রিয় (ধূসর) + "সময় শেষ"
      // নোটিশ; শুধু Mark as Leave থাকে। ⛔ ১২টার আগে সবসময় খোলা (সকালে-আসা কেউ
      // যেন আটকে না যায়)। OUT অপরিবর্তিত। সেভ-লজিক এক অক্ষরও বদলায়নি।
      var nbHourIST = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })).getHours();
      var nbWindowOpen = nbHourIST < 12;
      // 🔴🔒 B536 (08.08.2026, TK-নির্দেশ — "একবার IN TIME হয়ে গেলে সেই দিন
      // আর দরকার নেই, একবার OUT TIME হয়ে গেলে আর দরকার নেই") — আগে বোতাম দুটো
      // মার্ক হওয়ার পরেও চাপা যেত, ভুল করে আবার চাপলে আগের সময় মুছে নতুন
      // (তখনকার) সময় বসে যেত। এখন মার্ক হয়ে গেলে সেই বোতামে আর onclick থাকে না
      // (চাপা যায় না) — শুধু "✓ IN/OUT TIME + সময়" সবুজে দেখায়। ফোনের লক-আচরণের
      // সাথে মিলে গেল। ⛔ সেভ-লজিক (nbCheck/nbSaveDay) এক অক্ষরও বদলায়নি।
      attendanceHtml = '<div class="nbAtt" style="background:#fff;border:1px solid #E4E8EE;border-radius:12px;padding:20px 26px;box-shadow:0 1px 3px rgba(16,24,40,0.04);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:14px">' +
        '<div><div style="font-size:19px;font-weight:700">' + m.esc(date) + '</div><div style="font-size:13px;color:#667085;margin-top:2px">' + m.esc(code) + '</div></div>' +
        '<div style="display:flex;gap:10px">' +
        '<button class="nbTimeBtn' + (inSet ? ' on' : '') + '" ' + ((inSet || !nbWindowOpen) ? '' : 'onclick="nbCheck(\'in\')"') + ' style="min-width:110px;padding:10px 18px;border-radius:9px;font-size:13.5px;font-weight:600;text-align:center;' +
        (inSet ? 'background:#E6F4EA;color:#0B6B3A;border:1px solid #CFE9D8;box-shadow:none;cursor:default' : (nbWindowOpen ? 'background:#fff;color:#344054;border:1.5px solid #D0D5DD;box-shadow:none;cursor:pointer' : 'background:#F2F4F7;color:#98A2B3;border:1.5px solid #E4E7EC;box-shadow:none;cursor:not-allowed')) + '">' +
        (inSet ? ('✓ IN TIME<br>' + m.esc(day.check_in)) : 'IN TIME') + '</button>' +
        // 🔵 B608 parity (Android-এর মতো): IN TIME না হলে OUT TIME-এর মানে নেই —
        // তাই IN না করা পর্যন্ত OUT বোতাম নিষ্ক্রিয় (ধূসর, চাপা যায় না)।
        '<button class="nbTimeBtn' + (outSet ? ' on' : '') + '" ' + ((outSet || !inSet) ? '' : 'onclick="nbCheck(\'out\')"') + ' style="min-width:110px;padding:10px 18px;border-radius:9px;font-size:13.5px;font-weight:600;text-align:center;' +
        (outSet ? 'background:#E6F4EA;color:#0B6B3A;border:1px solid #CFE9D8;box-shadow:none;cursor:default' : (inSet ? 'background:#fff;color:#344054;border:1.5px solid #D0D5DD;box-shadow:none;cursor:pointer' : 'background:#F2F4F7;color:#98A2B3;border:1.5px solid #E4E7EC;box-shadow:none;cursor:not-allowed')) + '">' +
        (outSet ? ('✓ OUT TIME<br>' + m.esc(day.check_out)) : 'OUT TIME') + '</button>' +
        '</div>' +
        /* 🔴 V432 (TK-রিপোর্ট ১৮.০৮.২০২৬) — WhatsApp খুলে ব্যাক করে এলে আগে
           আর পাঠানোর কোনো উপায় ছিল না। এখন এই ছোট বোতামে চাপলেই **সেই একই
           বার্তাটাই** আবার খোলে। ⛔ সময় বা জমা কিছুই বদলায় না। */
        // 🔴 V433 (TK): "WhatsApp এ একবার পাঠানো হয়ে গেলে আর দেখানোর দরকার নেই"
        ((inSet && !nbWaSent('in')) ? '<button class="nbResendBtn" onclick="nbResendInTime()">&#128228; Send IN TIME to WhatsApp again</button>' : '') +
        ((outSet && !nbWaSent('out')) ? '<button class="nbResendBtn" onclick="nbResendDaily()">&#128228; Send the report to WhatsApp again</button>' : '') +
        '<span onclick="nbApplyLeave()" style="font-size:12.5px;color:#98A2B3;text-decoration:underline;cursor:pointer">🏖️ Mark as Leave</span>' +
        ((!inSet && !nbWindowOpen) ? '<div style="flex-basis:100%;font-size:12.5px;color:#B42318;margin-top:2px">⏰ আজকের IN TIME-এর সময় শেষ, না এলে ছুটি দিন</div>' : '') +
        /* 🔴🔒 V512 — এখন যা দেখছেন সেটা এই ফোনে জমানো কপি (ক্লাউড থেকে আসেনি)।
           ফোনের অ্যাপে ঠিক এই একই কথা দেখানো হয়। ⛔ IN/OUT TIME অক্ষত থাকে। */
        (nbDayFromCache ? '<div style="flex-basis:100%;font-size:12.5px;color:#667085;margin-top:2px">📴 Offline - showing this phone\'s saved copy</div>' : '') +
        /* 🔴🔒 V512 (নিজের যাচাইয়ে সংশোধিত) — আজকের তথ্য আনা যায়নি।
           ⛔ **পর্দা আটকে দেওয়া হয় না** — ওয়েবে সেভ ব্যর্থ হলে
              `MOD.queueWrite()` ধরে রাখে ও নেট এলে নিজেই পাঠায়; আটকে দিলে
              স্টাফ অফলাইনে IN TIME **দিতেই পারতেন না** (আগে পারতেন)।
              বদলে সত্যিটা স্পষ্ট লেখা থাকে + 🔄 Try again।
           ⚠️ ফোনের অ্যাপে নিয়মটা আলাদা (সেখানে পর্দা আটকায়) — কারণ ফোনে
              V478 অনুযায়ী ক্লাউডে না বসলে কাজটা এগোতেই দেওয়া হয় না। */
        (nbDayLoadFailed ? '<div style="flex-basis:100%;font-size:12.5px;color:#B42318;margin-top:2px">⚠️ Today\'s data could not be loaded - what you see may be incomplete. <span onclick="workNotebook()" style="text-decoration:underline;cursor:pointer">🔄 Try again</span></div>' : '') +
        '</div>';
    }

    var notesPrefill = day.day_note != null && day.day_note !== '' ? day.day_note : workEntriesSummary(day);

    host.innerHTML = '<div class="wrap anMod anModNb" style="max-width:1180px">' +
      /* 🔴 V430 — ফোনের শিরোনাম: "🗒️ Today Work" (WorkNotebookActivity.kt:1305) */
      '<div class="topbar" style="padding:14px 24px"><b>🗒️ Today Work</b>' +
      '<button class="ghost" onclick="dashboard()">Home</button></div>' +
      '<div class="page" style="padding:20px 24px 40px">' +

      attendanceHtml +

      /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬: "সব কিছু Android এর মত হোক") — তিনটে বদল:
         ১) **Total call** ফোনে = App Calls + বাইরের কল (WorkNotebookActivity.kt:1411);
            ওয়েবে বাইরের কলটা যোগই হত না, তাই মোট সবসময় কম দেখাত।
         ২) বাইরের কলের লেবেল ফোনের হুবহু: "Superfone/Clinic Number Call" (kt:1391)।
         ৩) ঘরের নাম ফোনের মতো — "(auto)" চিহ্ন সহ; আর পর্দার উপরের
            নির্দেশ-ধাঁচের শিরোনামগুলো তুলে দেওয়া হলো (TK-এর স্থায়ী নিয়ম)।
         ⛔ কোনো সংখ্যা নতুন করে হিসাব হয় না — একই ঘর থেকে যোগ হয়। */
      '<div class="wlv1SafeTwoCol">' +
      panel('', autoRow('New Enquiry (auto)', st.enquiries) + autoRow('Registration (auto)', st.registrations) + autoRow('App Calls (auto)', callTxt(apc)) + autoRow('Total call (auto)', callTxt(callSum(apc, day.outside_calls_manual)), 'nbTotalCalls') ) +
      panel('', editRow('nbPatients', 'Today Patient', suggestedPatients, '0') + editRow('nbOC', 'Superfone/Clinic Number Call', day.outside_calls_manual || 0, '0')) +
      '</div>' +

      '<div style="background:#fff;border:1px solid #E4E8EE;border-radius:12px;box-shadow:0 1px 3px rgba(16,24,40,0.04);overflow:hidden;margin-top:14px">' +
      '<div style="padding:11px 20px">' +
      /* 🔴 V430 — ফোনে এই ঘরের উপরে কোনো প্রশ্ন-লাইন নেই, শুধু ঘরটাই থাকে। */
      '<label style="display:block;font-size:11px;color:#667085;margin-bottom:5px;font-weight:600;text-transform:uppercase;letter-spacing:.3px">Notes</label>' +
      '<textarea id="nbNotes" class="input" rows="3" style="margin:0;padding:8px 10px;font-size:13.5px;border-radius:8px">' + m.esc(notesPrefill) + '</textarea>' +
      '</div></div>' +

      '<div style="margin-top:22px;display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap">' +
      '<button class="ghost" onclick="nbMonthly()">📊 Monthly Report</button>' +
      '<button class="ghost" onclick="nbHistory()">🗂️ My Reports</button>' +
      /* 🔴 V430 (TK-সিদ্ধান্ত ১৮.০৮.২০২৬: "তুলে দিন — ফোনের মতো") — ফোনে আলাদা
         Submit বোতাম নেই; **OUT TIME দিলেই** দিনের রিপোর্ট Master-এর কাছে
         চলে যায় (WorkNotebookActivity.kt:817, 1264 — TK-এর নিজেরই নিয়ম)।
         ওয়েবে বাড়তি বোতামটা থাকায় একই কাজ দুবার হত ও বিভ্রান্তি হত।
         ⛔ `nbSubmitDaily` ফাংশনটা মোছা হয়নি — OUT TIME-এর পথ ওটাই ব্যবহার
            করে, শুধু হাতে-চাপার বোতামটা আর দেখানো হয় না। */
      '</div>' +

      '</div></div>';
    // 🔵 B618: pending থেকে Approve হলে স্টাফের ফোনে WhatsApp জোর (আছে থাকলেই চেক)।
    try { nbCheckPendingLeaves(); } catch (e) {}
    /* 🔴 V430 — বাইরের কলের সংখ্যা লেখামাত্র "Total call (auto)" বদলে যায়
       (ফোনের মতোই), তাই স্টাফকে সেভ করে দেখতে হয় না। */
    try {
      var __oc = document.getElementById('nbOC'), __tc = document.getElementById('nbTotalCalls');
      if (__oc && __tc) {
        var __app = Number(apc || 0);
        __oc.addEventListener('input', function () {
          __tc.textContent = String(__app + (parseInt(__oc.value, 10) || 0));
        });
      }
    } catch (e) {}
  }

  // ⛔ পুরনো, নতুন পর্দা থেকে আর ডাকা হয় না — মোছা হয়নি (Work Entries লিস্ট)
  function drawEntries() {
    var m = window.MOD, d = window._nbDay; d.manual_entries = d.manual_entries || [];
    var host = document.getElementById('nbEntries'); if (!host) return;
    host.innerHTML = d.manual_entries.map(function (e, i) {
      return '<div><span class="mut">' + m.esc(e.time) + '</span> ' + m.esc(e.text) +
        ' <button class="small ghost" onclick="nbToggleEntry(' + i + ')">' + (e.status === 'complete' ? '✅' : '⏳') + '</button></div>';
    }).join('') || '<div class="mut">No entries yet.</div>';
  }
  function nbAddEntry() {
    var d = window._nbDay;
    var etEl = document.getElementById('nbET'), exEl = document.getElementById('nbEX');
    if (!etEl || !exEl) return;
    var t = etEl.value || window.MOD.nowTimeIST();
    var x = exEl.value.trim(); if (!x) return;
    d.manual_entries = d.manual_entries || [];
    d.manual_entries.push({ time: t, text: x, status: 'pending' });
    exEl.value = '';
    drawEntries(); nbSaveDay(true);
  }
  function nbToggleEntry(i) { var e = window._nbDay.manual_entries[i]; e.status = e.status === 'complete' ? 'pending' : 'complete'; drawEntries(); nbSaveDay(true); }

  /* 🔴 V432 (TK-রিপোর্ট ১৮.০৮.২০২৬: *"in time এ চাপ দিলে WhatsApp সাথে সাথে
     ওপেন হয়, কিন্তু একবার ব্যাকে আসলে তারপর আর পাঠানোর ব্যবস্থা নেই"*) —
     IN TIME-এর বার্তাটা **একটাই জায়গায়** বানানো হয়, তাই আবার পাঠালেও লেখা
     হুবহু একই থাকে। ফোনের WorkNotebookActivity.afterInTimeMarked-এর একই লেখা। */
  function nbInTimeText() {
    var m = window.MOD, d = window._nbDay || {};
    return 'IN TIME- ' + (d.check_in || '-') +
      '\nStaff: ' + ((m.session() || {}).code || '') +
      '\nDate: ' + (typeof wlv1Dot === 'function' ? wlv1Dot(d.work_date || m.todayIST()) : (d.work_date || m.todayIST()));
  }
  /* 🔴 V432 — মার্ক-করা IN TIME **আবার** WhatsApp-এ পাঠানো।
     ⛔ নিরাপদ: `check_in`-এর সময় · সেভ · Master-কে জমা — কিছুই আবার হয় না,
        শুধু আগের সেই একই লেখা আবার খোলে। তাই বারবার চাপলেও ক্ষতি নেই।
     ⛔ IN TIME বসানোই না থাকলে কিছু হয় না (ফাঁকা বার্তা যাবে না)। */
  /* 🔴🆕 V433 (TK-নির্দেশ ১৮.০৮.২০২৬ — "WhatsApp এ একবার পাঠানো হয়ে গেলে আর
     দেখানোর দরকার নেই / send in time WhatsApp again")। TK-এর বাছা পথ:
     "বোতাম চাপার পরে একবার জিজ্ঞাসা করব"। WhatsApp খোলার পরে একবারই ছোট
     প্রশ্ন — "হ্যাঁ" বললে সেই দিনের জন্য বোতামটা আর দেখাবে না।
     ⛔ চিহ্নটা **শুধু এই ব্রাউজারে** (localStorage), Supabase-এ নতুন কোনো ঘর
        লেখা হয় না — তাই সেভ ভাঙার ঝুঁকি নেই (ফোনের SharedPreferences-এর মতোই)।
     ⛔ স্টাফ-কোড + তারিখ মিলিয়ে — পরের দিন নিজে থেকেই বোতাম ফিরে আসে,
        আর এক স্টাফের চিহ্নে অন্য স্টাফ আটকা পড়ে না।
     ⛔ "না" বললে বোতাম থেকেই যায়। */
  function nbWaKey(kind) {
    var m = window.MOD, d = window._nbDay || {};
    return 'wn_wa_sent_' + kind + '_' + ((m.session() || {}).code || '') + '_' + (d.work_date || m.todayIST());
  }
  function nbWaSent(kind) {
    try { return localStorage.getItem(nbWaKey(kind)) === '1'; } catch (e) { return false; }
  }
  function nbWaAsk(kind) {
    if (nbWaSent(kind)) return;
    try {
      if (confirm('WhatsApp-এ পাঠানো হয়ে গেছে?')) {
        localStorage.setItem(nbWaKey(kind), '1');
        renderToday();
      }
    } catch (e) {}
  }
  window.nbWaSent = nbWaSent;

  function nbResendInTime() {
    var d = window._nbDay || {};
    if (!d.check_in) { try { toast('IN TIME is not marked yet'); } catch (e) {} return; }
    window.MOD.whatsapp(nbInTimeText());
    /* WhatsApp নতুন ট্যাবে খোলার পরে প্রশ্নটা আসে — সঙ্গে সঙ্গে নয়, নইলে
       বার্তা খোলার আগেই জিজ্ঞাসা হয়ে যেত। */
    setTimeout(function () { nbWaAsk('in'); }, 1200);
  }
  window.nbResendInTime = nbResendInTime;
  /* 🔴 V432 — OUT TIME-এর পরে দিনের পুরো রিপোর্ট **আবার** পাঠানো।
     ⛔ নতুন করে জমা দেওয়া হয় না — শুধু শেষ বানানো লেখাটাই আবার খোলে। */
  function nbResendDaily() {
    var t = (window._nbLastDailyText || '').trim();
    if (!t) { try { toast('Report is not ready yet'); } catch (e) {} return; }
    window.MOD.whatsapp(t);
    setTimeout(function () { nbWaAsk('out'); }, 1200);
  }
  window.nbResendDaily = nbResendDaily;

  function nbCheck(which) {
    var d = window._nbDay, t = window.MOD.nowTimeIST();
    // 🔵 B608 parity (Android-এর মতো): IN TIME না হলে OUT TIME মার্ক করা যাবে না।
    if (which === 'out' && !d.check_in) { try { toast('আগে IN TIME দিন'); } catch (e) {} return; }
    // 🔵 B615 parity: দুপুর ১২টা পার হলে IN TIME দেওয়া যাবে না (ডিউটি সকালের)।
    if (which === 'in') {
      var h = new Date(new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' })).getHours();
      if (h >= 12) { try { toast('আজকের IN TIME-এর সময় শেষ'); } catch (e) {} return; }
    }
    if (which === 'in') d.check_in = t; else d.check_out = t;
    /* 🔴 V430 (TK-সিদ্ধান্ত ১৮.০৮.২০২৬) — ফোনে OUT TIME চাপলেই দিনের সব লেখা
       (Today Patient · বাইরের কল · Notes) সেভ হয়ে **রিপোর্টটাও চুপচাপ
       Master-এর কাছে চলে যায়** (WorkNotebookActivity.kt:817-830, B466)।
       ওয়েবে OUT TIME শুধু সময়টা বসাত, রিপোর্ট যেত আলাদা বোতামে। বোতামটা
       TK-এর নির্দেশে তুলে দেওয়া হয়েছে, তাই এখানেই ফোনের নিয়মটা বসানো হলো।
       ⛔ রিপোর্টের লেখা/ফরম্যাট এক অক্ষরও বদলায়নি — শুধু কোথা থেকে ডাকা
          হচ্ছে সেটা বদলেছে, ফোনে যেমনটা হয়েছিল। */
    if (which === 'out') {
      /* ⛔ ঘরগুলো পড়ার কাজটা `nbSaveDay()` নিজেই করে (outside_calls_manual ও
         day_note) — এখানে আলাদা করে কিছু বসানো হয়নি, কারণ ডেটাবেসে ঠিক ওই
         দুটো ঘরই আছে; নতুন কোনো নাম লিখলে সেভ ব্যর্থ হত। */
      nbSaveDay(true).then(function () {
        try { return nbSubmitDaily(); } catch (e) { return null; }
      }).then(renderToday, renderToday);
      return;
    }
    /* 🔴 V432 — ফোনে IN TIME মার্ক হলেই WhatsApp খোলে
       (WorkNotebookActivity.afterInTimeMarked)। ওয়েবে সেটা ছিল না — এখন
       দুই জায়গা এক। ⛔ সেভ শেষ হওয়ার পরেই খোলে, তাই সময় হারানোর ভয় নেই। */
    nbSaveDay(true).then(function () {
      try { window.MOD.whatsapp(nbInTimeText()); } catch (e) {}
      renderToday();
      /* 🔴 V433 — অটোমেটিক খোলার পরেও একই একবারের প্রশ্ন, তাই পাঠানো হয়ে
         গেলে "আবার পাঠান" বোতামটা আর দেখাতেই হয় না। */
      setTimeout(function () { nbWaAsk('in'); }, 1200);
    }, renderToday);
  }

  // 🆕 (B323 parity) — ছুটির কারণ বাধ্যতামূলক (ফাঁকা রাখলে সেভ হবে না)
  function nbMarkLeave() {
    var reason = (prompt('Reason for leave (required):') || '').trim();
    if (!reason) { try { toast('Leave reason is required'); } catch (e) {} return; }
    var d = window._nbDay;
    d.is_leave = true; d.leave_reason = reason;
    nbSaveDay(true).then(renderToday);
  }
  function nbCancelLeave() {
    // 🔵 B608 parity: ছুটি নেওয়া মানে সারাদিন ছুটি — casual toggle যেন মনে না হয়,
    // আগে নিশ্চিত করা (ভুল করে ছুটি / আসলে এসে গেছেন)। ⛔ বাতিল-লজিক অপরিবর্তিত।
    if (!confirm('ভুল করে ছুটি দিয়েছিলেন? ছুটি বাতিল করে আজকের হাজিরা আবার চালু করবেন?')) return;
    var d = window._nbDay;
    d.is_leave = false; d.leave_reason = '';
    nbSaveDay(true).then(renderToday);
  }

  // 🔵🔒 B618 (11.08.2026, TK-নির্দেশ, ধাপে-ধাপে আলোচনা+প্রুফ অনুমোদিত) —
  // ছুটির আবেদন (আজ/অগ্রিম তারিখ)। সরাসরি ছুটি যদি মাসে ৪-এর কম ও ওই দিন ব্রাঞ্চে
  // অন্য স্টাফ confirmed ছুটিতে নেই; নইলে (৫ম+ বা একই-দিন-দ্বন্দ্ব) → pending,
  // ডাক্তার/মাস্টার Approve করলে তবেই। ⛔ Android-এর হুবহু লজিক; পুরনো কিছু ভাঙে না।
  function nbApplyLeave() {
    var m = window.MOD, today = m.todayIST();
    var html = '<h2>🏖️ ছুটির আবেদন</h2>' +
      '<label>কোন তারিখে ছুটি</label><input id="lvDate" class="input" type="date" min="' + today + '" value="' + today + '">' +
      '<label>কারণ</label><input id="lvReason" class="input" placeholder="Sick / Personal / Festival">' +
      '<div class="actions"><button onclick="nbSubmitLeave()">ছুটির আবেদন করুন</button>' +
      '<button class="ghost" onclick="closeModal()">Cancel</button></div>';
    try { window.modal(html); } catch (e) {}
  }
  function nbPostBriefing(title, message, targets, branch, byMobile) {
    try {
      var now = new Date().toISOString();
      var row = { id: 'brief_' + window.MOD.uuid().replace(/-/g, ''), date: window.MOD.todayIST(), title: title, message: message, targets: targets, seen: [], replies: [], hiddenFor: [], branch: branch, createdBy: byMobile, createdAt: now, updatedAt: now };
      if (typeof cloudUpsertBriefing === 'function') return cloudUpsertBriefing(row);
    } catch (e) {}
    return Promise.resolve();
  }
  function nbLeavePendKey() { return 'leave_pend_' + ((window.MOD.session() || {}).code || ''); }
  function nbAddPendingLeave(date) {
    try { var k = nbLeavePendKey(); var a = JSON.parse(localStorage.getItem(k) || '[]'); if (a.indexOf(date) < 0) a.push(date); localStorage.setItem(k, JSON.stringify(a)); } catch (e) {}
  }
  async function nbSubmitLeave() {
    var m = window.MOD;
    var dateEl = document.getElementById('lvDate'), reasonEl = document.getElementById('lvReason');
    var date = (dateEl && dateEl.value) || m.todayIST();
    var reason = ((reasonEl && reasonEl.value) || '').trim();
    if (!reason) { try { toast('Reason required'); } catch (e) {} return; }
    var u = appUser(); var code = (m.session() || {}).code || '';
    var mobile = String(u.mobile || '').replace(/\D/g, '').slice(-10);
    var branch = u.branch || '';
    var client; try { client = await sb(); } catch (e) { try { toast('Net problem'); } catch (_e) {} return; }
    var ym = date.slice(0, 7);
    // ⛔ B618 ঠিক (11.08.2026): আগে উপরের সীমা ym+'-32' ছিল — Postgres date কলামে
    // '2026-08-32' পার্স-এরর দেয় (যাচাই করা), query fail → count 0 → মাসে ৪-এর নিয়ম
    // কখনো চালু হতো না। এখন পরের মাসের ১ তারিখ (বৈধ) দিয়ে সীমা।
    var _y = parseInt(ym.slice(0, 4), 10), _mo = parseInt(ym.slice(5, 7), 10);
    var _ny = _mo === 12 ? _y + 1 : _y, _nm = _mo === 12 ? 1 : _mo + 1;
    var nextMonthFirst = _ny + '-' + ('0' + _nm).slice(-2) + '-01';
    var monthCount = 0, conflict = false;
    try { var cr = await client.schema('wn').from('leave_requests').select('id', { count: 'exact', head: true }).eq('staff_code', code).eq('status', 'confirmed').gte('leave_date', ym + '-01').lt('leave_date', nextMonthFirst); monthCount = cr.count || 0; } catch (e) {}
    try { var cf = await client.schema('wn').from('leave_requests').select('id', { count: 'exact', head: true }).eq('branch', branch).eq('leave_date', date).eq('status', 'confirmed').neq('staff_code', code); conflict = (cf.count || 0) > 0; } catch (e) {}
    var need = []; if (monthCount >= 4) need.push('5th'); if (conflict) need.push('conflict');
    var needReason = need.join('+'); var status = needReason ? 'pending' : 'confirmed';
    // ⛔ id দিই না — leave_requests.id-র DB default; conflict-এ বিদ্যমান id অটুট।
    var row = { staff_code: code, staff_mobile: mobile, staff_name: code, branch: branch, leave_date: date, reason: reason, status: status, need_reason: needReason, created_by: mobile, updated_at: new Date().toISOString() };
    try { await client.schema('wn').from('leave_requests').upsert(row, { onConflict: 'staff_code,leave_date' }); } catch (e) { try { toast('Net problem — try again'); } catch (_e) {} return; }
    try { closeModal(); } catch (e) {}
    if (status === 'confirmed') {
      try {
        var nd = { staff_code: code, staff_mobile: mobile, work_date: date, is_leave: true, leave_reason: reason, updated_at: new Date().toISOString() };
        await client.schema('wn').from('notebook_days').upsert(nd, { onConflict: 'staff_code,work_date' });
      } catch (e) {}
      if (date === m.todayIST() && window._nbDay) { window._nbDay.is_leave = true; window._nbDay.leave_reason = reason; }
      nbPostBriefing('Staff Leave', '👤 Staff : ' + (code || mobile) + '\n🏥 Branch : ' + branch + '\n🏖️ Leave : ' + (window.wlv1Dot ? window.wlv1Dot(date) : date) + '\nReason : ' + reason   /* 🔴🔒 V936 — এক ফরম্যাট */, { branches: [branch] }, branch, mobile);
      try { m.whatsapp('🏖️ Leave\nStaff: ' + code + '\nBranch: ' + branch + '\nDate: ' + date + '\nReason: ' + reason); } catch (e) {}
    } else {
      // ⚠️ ওয়েব approval bell (wlv1NoticeField) ছোট-হাতের "key :" খোঁজে — তাই emoji ছাড়া পরিষ্কার লাইন রাখি।
      nbPostBriefing('Leave request', 'Staff : ' + (code || mobile) + '\nBranch : ' + branch + '\nLeave date : ' + (window.wlv1Dot ? window.wlv1Dot(date) : date) + '\nReason : '   /* 🔴🔒 V936 — Approve `wlv1IsoDate()` দিয়ে ফিরিয়ে পড়ে */ + reason + '\nNeed : ' + needReason, { branches: [branch], roles: ['master'] }, branch, mobile);
      nbAddPendingLeave(date);
      try { toast('ছুটির অনুরোধ পাঠানো হয়েছে — Pending'); } catch (e) {}
    }
    try { renderToday(); } catch (e) {}
  }
  // পাতা খোলায় নিজের pending তারিখগুলোর status দেখি — confirmed হলে WhatsApp জোর,
  // rejected হলে toast, তারপর তালিকা থেকে বাদ। pending না থাকলে কোনো ক্লাউড-কল নেই।
  async function nbCheckPendingLeaves() {
    var k = nbLeavePendKey(); var dates = []; try { dates = JSON.parse(localStorage.getItem(k) || '[]'); } catch (e) {}
    if (!dates.length) return;
    var m = window.MOD, code = (m.session() || {}).code, client;
    try { client = await sb(); } catch (e) { return; }
    var keep = [], approvedShare = null, rejectedDate = null;
    for (var i = 0; i < dates.length; i++) {
      var dt = dates[i];
      try {
        var r = await client.schema('wn').from('leave_requests').select('status,reason,branch').eq('staff_code', code).eq('leave_date', dt).limit(1).maybeSingle();
        var st = (r && r.data) ? r.data.status : '';
        if (st === 'confirmed') approvedShare = '🏖️ Leave Approved\nStaff: ' + code + '\nBranch: ' + (r.data.branch || '') + '\nDate: ' + dt + '\nReason: ' + (r.data.reason || '');
        else if (st === 'rejected') rejectedDate = dt;
        else keep.push(dt);
      } catch (e) { keep.push(dt); }
    }
    try { localStorage.setItem(k, JSON.stringify(keep)); } catch (e) {}
    if (rejectedDate) { try { toast('Leave rejected (' + rejectedDate + ') — please come to work'); } catch (e) {} }
    if (approvedShare) { try { m.whatsapp(approvedShare); } catch (e) {} }
  }

  async function nbSaveDay(silent) {
    var m = window.MOD, d = window._nbDay;
    var ocEl = document.getElementById('nbOC'); if (ocEl) d.outside_calls_manual = parseInt(ocEl.value, 10) || 0;
    var notesEl = document.getElementById('nbNotes'); if (notesEl) d.day_note = notesEl.value;
    d.updated_at = new Date().toISOString();
    /* 🔴 V512 — কোড+তারিখ চাবিতে (উপরে কারণ লেখা)। `force`, কারণ এটাই সবচেয়ে
       নতুন কপি — ক্লাউডের পুরোনো কপি দিয়ে এটা মুছে যাওয়া চলবে না। */
    nbSaveDayCache(d, true); // offline-safe immediate local copy (never lost)
    await m.save('wn', 'notebook_days', d);
    if (!silent) try { toast('Notebook saved'); } catch (e) {}
  }

  // ---- outside calls (পুরনো ব্যক্তিগত-কল-লগ ফিচার, B330-এ বাদ — ফাংশন অক্ষত, ডাকা হয় না) ----
  async function drawOutside() {
    var m = window.MOD, client = await sb(), code = (m.session() || {}).code, date = m.todayIST();
    var rows = [];
    try { rows = (await client.schema('wn').from('outside_calls').select('*').eq('staff_code', code).eq('call_date', date).order('call_time')).data || []; } catch (e) {}
    var host = document.getElementById('ocList'); if (!host) return;
    host.innerHTML = rows.map(function (x) {
      return '<div>' + m.esc(x.call_time) + ' · ' + m.esc(m.fullMobile(x.target_mobile)) + ' · ' + m.esc(x.remark || '') + '</div>';
    }).join('') || '<div class="mut">No outside calls today.</div>';
  }
  async function nbAddOutside() {
    var m = window.MOD, client = await sb(), code = (m.session() || {}).code;
    var mobEl = document.getElementById('ocMob'), timeEl = document.getElementById('ocTime'), remEl = document.getElementById('ocRem');
    if (!mobEl || !timeEl || !remEl) return;
    var mob = mobEl.value.trim(), time = timeEl.value.trim(), rem = remEl.value.trim();
    if (!mob || !time) { try { toast('Mobile and time required'); } catch (e) {} return; }
    var row = { id: m.uuid(), staff_code: code, branch: appUser().branch || '', call_date: m.todayIST(), call_time: time, target_mobile: mob, remark: rem };
    try {
      var r = await client.schema('wn').from('outside_calls').insert(row);
      if (r.error) { try { toast('Same mobile + time already added'); } catch (e) {} }
    } catch (e) {}
    mobEl.value = ''; remEl.value = '';
    drawOutside();
  }

  // ---- Daily report: build text + save + submit + share — সবকিছু এক Submit বোতামে (B342 parity) ----
  async function nbSubmitDaily() {
    var m = window.MOD, code = (m.session() || {}).code, date = m.todayIST();
    var d = window._nbDay;
    var patientsEl = document.getElementById('nbPatients');
    var patientsVal = patientsEl ? (patientsEl.value.trim() || '0') : '0';
    try { toast('Submitting...'); } catch (e) {}
    await nbSaveDay(true);
    var st = autoStats({ mode: 'day', key: date });
    var apc = await appCallCount({ mode: 'day', key: date });
    var occ = d.outside_calls_manual || 0;
    var text = 'Daily Report ' + dotDate(date) + ' Time-' + shareTimeLabel() + '\nStaff: ' + code + '\n';
    if (d.is_leave) {
      text += '🏖️ On Leave: ' + (d.leave_reason || '') + '\n';
    } else {
      text += 'IN TIME- ' + (displayTime12(d.check_in) || '-') + '\n';
      text += 'OUT TIME ' + (displayTime12(d.check_out) || '-') + '\n';
    }
    text += '\nNew Enquiry: ' + st.enquiries + '\nRegistration: ' + st.registrations +
      '\nToday Patient: ' + patientsVal + '\nApp Calls: ' + callTxt(apc) + '\nOutside Calls: ' + occ + '\nTotal call : ' + callTxt(callSum(apc, occ));
    /* 👨‍⚕️🔒 V1032 (TK-নির্দেশ: *"কতজন ডাক্তারের কাছে ভিজিট করেছে তাকে ম্যানুয়ালি
       এন্ট্রি করতে হয়েছে"*) — ফোনের হুবহু একই লাইন, একই নিয়ম।
       ⚡ কম্পিউটারে **একটাও ক্লাউড-অনুরোধ যায় না** — ডাক্তারের তালিকা এমনিতেই
          এখানে জমা থাকে, সেখান থেকেই গোনা হয়।
       ⛔ শূন্য হলে লাইনটা ওঠে না — পুরনো রিপোর্ট হুবহু আগের মতোই। */
    try {
      var __dv = nbDoctorVisitCount(date, d.staff_code || code);
      if (__dv > 0) text += '\nDoctor Visit: ' + __dv;
    } catch (e) {}
    var notesTxt = (d.day_note || '').trim();
    if (notesTxt) text += '\n\nNotes: \n' + notesTxt;
    /* 🔴 V593 — না পড়তে পারলে `null`ই জমা হোক; `apc + occ` করলে JS-এ null যোগ
       হয়ে মিথ্যা সংখ্যা তৈরি হত (null + 5 = 5)। */
    var stats = { enquiries: st.enquiries, registrations: st.registrations, appCalls: apc, outsideCalls: occ, totalCalls: callSum(apc, occ) };
    window._nbReport = { period_type: 'daily', period_key: date, auto_stats: stats, manual_summary: notesTxt, text: text };
    /* 🔴 V432 — শেষ বানানো রিপোর্টের লেখাটা তুলে রাখা হয়, যাতে WhatsApp বন্ধ
       করে ফিরে এলে "📤 রিপোর্ট আবার পাঠান" বোতামে **হুবহু একই লেখা** যায়। */
    window._nbLastDailyText = text;
    await nbSubmit('daily', date, true);
    nbShareWa();
  }

  // ---- monthly report screen (kept as its own view, matching "📊 Monthly Report" link) ----
  async function nbMonthly() {
    var m = window.MOD, code = (m.session() || {}).code, ym = m.todayIST().slice(0, 7);
    await renderMonthly(ym);
  }
  async function renderMonthly(ym) {
    var m = window.MOD, code = (m.session() || {}).code;
    var st = autoStats({ mode: 'month', key: ym });
    var apc = await appCallCount({ mode: 'month', key: ym });
    var occ = await monthlyOutsideCalls(ym);
    var leaveDays = await monthlyLeaveDays(ym);
    var stats = { enquiries: st.enquiries, registrations: st.registrations, appCalls: apc, outsideCalls: occ, totalCalls: callSum(apc, occ), leaveDays: leaveDays };
    var text = 'Monthly Report ' + ym + '\nStaff: ' + code + '\n\n' +
      'New Enquiry: ' + st.enquiries + '\nRegistration: ' + st.registrations +
      '\nApp Calls: ' + callTxt(apc) + ' | Outside Calls: ' + occ + ' | Total: ' + callTxt(callSum(apc, occ)) +
      '\nLeave Days: ' + leaveDays;
    window._nbReport = { period_type: 'monthly', period_key: ym, auto_stats: stats, manual_summary: '', text: text };
    document.getElementById('app').innerHTML = '<div class="wrap anMod anModNb" style="max-width:1180px"><div class="topbar" style="padding:14px 24px"><b>📊 Monthly Report</b><button class="ghost" onclick="workNotebook()">Back</button></div>' +
      '<div class="page" style="padding:20px 24px 40px">' +
      '<div style="background:#fff;border:1px solid #E4E8EE;border-radius:12px;padding:20px 26px;box-shadow:0 1px 3px rgba(16,24,40,0.04)">' +
      '<label style="display:block;font-size:11px;color:#667085;margin-bottom:6px;font-weight:600;text-transform:uppercase">Month</label>' +
      '<input id="nbMonthSel" class="input" type="month" value="' + ym + '" onchange="nbMonthReload(this.value)" style="max-width:200px">' +
      '<pre style="white-space:pre-wrap;margin-top:14px;font-size:13.5px">' + m.esc(text) + '</pre>' +
      '<div style="display:flex;gap:10px;margin-top:14px;flex-wrap:wrap">' +
      '<button onclick="nbSubmit(\'monthly\',\'' + ym + '\')">Submit to Master</button>' +
      '<button class="ghost" onclick="nbSharePdf()">🖨️ PDF</button>' +
      '<button class="ghost" onclick="nbShareWa()">📤 WhatsApp</button>' +
      '</div></div></div></div>';
  }
  async function nbMonthReload(ym) { await renderMonthly(ym); }

  function nbSharePdf() { var r = window._nbReport; window.MOD.printHtml(r.period_type + ' report', '<h1>' + window.MOD.esc(r.text.split('\n')[0]) + '</h1><pre style="white-space:pre-wrap">' + window.MOD.esc(r.text) + '</pre>'); }
  function nbShareWa() { window.MOD.whatsapp((window._nbReport || {}).text || ''); }

  async function nbSubmit(type, key, silentReturn) {
    var m = window.MOD, client = await sb(), code = (m.session() || {}).code, r = window._nbReport;
    // find existing submitted report for this period (to preserve, not overwrite)
    var existing = null;
    try { existing = (await client.schema('wn').from('work_reports').select('*').eq('staff_code', code).eq('period_type', type).eq('period_key', key).order('version', { ascending: false }).limit(1).maybeSingle()).data; } catch (e) {}
    if (existing && existing.status !== 'draft') {
      if (!confirm('A report for this period is already submitted. Submit a correction (new version, old kept in history)?')) return;
    }
    var row = {
      id: m.uuid(), staff_code: code, branch: appUser().branch || '',
      period_type: type, period_key: key, auto_stats: r.auto_stats, manual_summary: r.manual_summary,
      status: 'submitted', version: existing ? (existing.version + 1) : 1,
      superseded_by: null, submitted_at: new Date().toISOString()
    };
    try {
      // 🔵 TK-ORDER (07.08.2026): supabase-js insert **error-এ throw করে না** — {error}
      // ফেরায়। আগে সেটা যাচাই না করে "Report submitted to Master" দেখাত, অথচ সারি
      // বসত না → Master রিপোর্ট পেত না, চুপচাপ হারিয়ে যেত। এখন {error} থাকলে throw করে
      // নিচের **বিদ্যমান** catch-এ যায় — অর্থাৎ queueWrite-এ রেখে নেট ফিরলে আবার পাঠায়
      // ও সৎ "Saved offline" দেখায়। ⛔ সফল-পথ ও queue এক অক্ষরও বদলায়নি।
      var __ins = await client.schema('wn').from('work_reports').insert(row);
      if (__ins && __ins.error) throw __ins.error;
      if (existing) await client.schema('wn').from('work_reports').update({ superseded_by: row.id }).eq('id', existing.id);
      try { toast('Report submitted to Master'); } catch (e) {}
    } catch (e) { m.queueWrite('wn', 'work_reports', row); try { toast('Saved offline — will submit when online'); } catch (e2) {} }
    if (!silentReturn) workNotebook();
  }

  async function nbHistory() {
    var m = window.MOD, client = await sb(), code = (m.session() || {}).code;
    var rows = [];
    /* 🔵 V405 (16.08.2026, TK-অনুমোদিত — Egress) — এই তালিকায় প্রতিটি রিপোর্টের
       **পুরো লেখা** নামত, অথচ পর্দায় দেখানো হয় মাত্র ৬টা ঘর; উপরন্তু
       `select('*')`-এ **কোনো limit ছিল না** — রিপোর্ট যত জমবে, তত বড় হত।
       এখন শুধু দেখানো ঘরগুলোই, আর সাম্প্রতিক ১০০টা।
       ⛔ পর্দায় ব্যবহৃত একটাও ঘর বাদ যায়নি (মিলিয়ে দেখা): period_type ·
          period_key · version · submitted_at · accepted · seen_at।
       ⛔ ছাঁকনি/সাজানো/RLS কিছুই বদলায়নি — শুধু ঘর কম ও একটা সীমা।
       ⛔ সরু পড়া ব্যর্থ হলে আগের পথেই ফেরে — তালিকা কখনো ভুল করে ফাঁকা দেখাবে না। */
    try {
      var __r = await client.schema('wn').from('work_reports')
        .select('id,period_type,period_key,version,submitted_at,accepted,seen_at')
        .order('submitted_at', { ascending: false }).limit(100);
      rows = (__r && !__r.error && Array.isArray(__r.data)) ? __r.data
        : ((await client.schema('wn').from('work_reports').select('*').order('submitted_at', { ascending: false }).limit(100)).data || []);
    } catch (e) {}
    document.getElementById('app').innerHTML = '<div class="wrap anMod anModNb" style="max-width:1180px"><div class="topbar" style="padding:14px 24px"><b>My Reports</b><button class="ghost" onclick="workNotebook()">Back</button></div>' +
      '<div class="page" style="padding:20px 24px 40px">' + (rows.map(function (x) {
        return '<div class="card"><b>' + m.esc(x.period_type) + ' · ' + m.esc(x.period_key) + '</b> v' + x.version +
          '<br><span class="mut">' + m.esc((x.submitted_at || '').slice(0, 16).replace('T', ' ')) + ' · ' +
          (x.accepted ? 'Accepted' : (x.seen_at ? 'Seen' : 'Submitted')) + '</span></div>';
      }).join('') || '<div class="card mut">No reports yet.</div>') + '</div></div>';
  }

  // expose
  window.workNotebook = workNotebook;
  window.nbCheck = nbCheck; window.nbAddEntry = nbAddEntry; window.nbToggleEntry = nbToggleEntry;
  window.nbMarkLeave = nbMarkLeave; window.nbCancelLeave = nbCancelLeave;
  window.nbApplyLeave = nbApplyLeave; window.nbSubmitLeave = nbSubmitLeave;   // 🔵 B618
  window.nbSaveDay = nbSaveDay; window.nbAddOutside = nbAddOutside;
  window.nbSubmitDaily = nbSubmitDaily;
  window.nbMonthly = nbMonthly; window.nbMonthReload = nbMonthReload;
  window.nbSubmit = nbSubmit; window.nbHistory = nbHistory; window.nbSharePdf = nbSharePdf; window.nbShareWa = nbShareWa;
})();
