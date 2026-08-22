/* Introduced in V325; V328 web parity update 2 (fresh cache identity).
   Existing app.js referral records/design are untouched. All money writes use
   authenticated fin RPCs, matching Android and the database backstop. */
(function () {
  'use strict';
  async function fin() {
    try {
      if (!window.MOD) return null;
      await window.MOD.autoSignIn();
      var c = await window.MOD.client();
      return c ? c.schema('fin') : null;
    } catch (_) { return null; }
  }
  /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬) — কমিশনের টাকা ফোনের মতোই **পয়সাসহ**
     (DoctorVisitActivity.kt-এর `"%,.2f"`): ₹41,750.00। কমিশন শতাংশে হিসাব হয়,
     তাই পয়সা সত্যিই আসতে পারে — গোল করে দেখালে ফোনের সঙ্গে অঙ্ক মিলত না।
     কমা বসে ভারতীয় নিয়মে (TK-সিদ্ধান্ত ১৮.০৮.২০২৬)। */
  function rmpMoney(v) {
    try {
      return '₹' + Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    } catch (_) { return '₹' + Number(v || 0).toFixed(2); }
  }
  function doctors() { try { return load('doctor_visits') || []; } catch (_) { return []; } }
  function patients() { try { return load('patients') || []; } catch (_) { return []; } }
  function doctor(id) { return doctors().find(function (x) { return String(x.id) === String(id); }); }
  function pByMobile(v) {
    var m = String(v || '').replace(/\D/g, '').slice(-10);
    return patients().find(function (p) {
      return String(p.mobile || '').replace(/\D/g, '').slice(-10) === m ||
        String(p.altMobile || '').replace(/\D/g, '').slice(-10) === m;
    }) || null;
  }
  var readCache = { counts: null, countsAt: 0, views: {}, performance: {}, patients: {} };
  var READ_TTL = 120000;
  async function baseClient() {
    try {
      if (!window.MOD) return null;
      await window.MOD.autoSignIn();
      return await window.MOD.client();
    } catch (_) { return null; }
  }
  async function webRmpFindPatient(v) {
    var m = String(v || '').replace(/\D/g, '').slice(-10), local = pByMobile(m);
    if (local || m.length !== 10) return local;
    var cached = readCache.patients[m];
    if (cached && Date.now() - cached.at < READ_TTL) return cached.value;
    var c = await baseClient(); if (!c) return null;
    var r = await c.from('patients').select('id,name,mobile,altMobile,patientId,branch').eq('mobile', '+91' + m).limit(1).maybeSingle();
    var value = (!r.error && r.data) ? r.data : null;
    readCache.patients[m] = { at: Date.now(), value: value };
    return value;
  }
  async function smallRpc(name, args) {
    var c = await fin(); if (!c) return null;
    var r = await c.rpc(name, args || {});
    return (!r.error && Array.isArray(r.data)) ? r.data : null;
  }
  async function webRmpCardCounts() {
    if (readCache.counts && Date.now() - readCache.countsAt < READ_TTL) return readCache.counts;
    var rows = await smallRpc('rmp_legacy_card_counts', {}); if (!rows) return null;
    var out = {}, valid = rows.every(function (x) {
      var id = String(x.rmp_id || ''), n = Number(x.referred_count);
      if (!id || !Number.isInteger(n) || n < 0 || Object.prototype.hasOwnProperty.call(out, id)) return false;
      out[id] = n; return true;
    });
    if (!valid) return null; readCache.counts = out; readCache.countsAt = Date.now(); return out;
  }
  async function webRmpViewAll(rmpId) {
    var old = readCache.views[rmpId]; if (old && Date.now() - old.at < READ_TTL) return old.value;
    var rows = await smallRpc('rmp_legacy_view_all_v2', { p_rmp_id: rmpId }); if (!rows) return null;
    var seen = {}, valid = rows.every(function (x) {
      var id = String(x.patient_row_id || ''), bill = Number(x.bill), paid = Number(x.paid);
      if (!id || seen[id] || !isFinite(bill) || bill < 0 || !isFinite(paid)) return false;
      seen[id] = true; return true;
    });
    if (!valid) return null; readCache.views[rmpId] = { at: Date.now(), value: rows }; return rows;
  }
  async function webRmpPerformance(branch) {
    var key = String(branch || 'All'), old = readCache.performance[key];
    if (old && Date.now() - old.at < READ_TTL) return old.value;
    var rows = await smallRpc('rmp_legacy_performance', { p_branch: key === 'All' ? null : key }); if (!rows) return null;
    var seen = {}, valid = rows.every(function (x) {
      var id = String(x.rmp_id || ''), month = Number(x.this_month_count), all = Number(x.all_time_count), paid = Number(x.referral_paid);
      if (!id || seen[id] || !Number.isInteger(month) || !Number.isInteger(all) || month < 0 || all < 1 || month > all || all > 5000 || !isFinite(paid)) return false;
      seen[id] = true; return true;
    });
    if (!valid) return null; readCache.performance[key] = { at: Date.now(), value: rows }; return rows;
  }
  function errText(error) { return (error && (error.message || error.details)) || 'Could not save — nothing changed'; }

  async function webRmpActivateAfterPayment(patientId) {
    try {
      var p = patients().find(function (x) { return String(x.id) === String(patientId); }); if (!p) return;
      var refMob = String(p.refDoctorMobile || '').replace(/\D/g, '').slice(-10), refName = String(p.refDoctor || '').trim().toLowerCase();
      if (!refMob && !refName) return;
      var d = doctors().find(function (x) {
        return (refMob && String(x.mobile || '').replace(/\D/g, '').slice(-10) === refMob) ||
          (refName && String(x.name || '').trim().toLowerCase() === refName);
      }); if (!d) return;
      var c = await fin(); if (!c) return;
      var current = await c.from('rmp_patient_commissions').select('id').eq('patient_row_id', p.id).limit(1).maybeSingle();
      if (current && current.data) return;
      var def = await c.from('rmp_commission_defaults').select('rmp_id').eq('rmp_id', d.id).limit(1).maybeSingle();
      if (!def || !def.data) {
        var key = 'rmp_missing_' + p.id + '_' + today();
        if (!localStorage.getItem(key)) { localStorage.setItem(key, '1'); toast('Payment saved. RMP commission is not set — please set it from Referral Income.'); }
        return;
      }
      await c.rpc('rmp_set_patient_commission', { p_patient_row_id: p.id, p_rmp_id: d.id, p_mode: null, p_value: null, p_set_on: null });
    } catch (_) { /* commission must never affect payment */ }
  }

  async function openWebRmpCommission(docId) {
    var d = doctor(docId); if (!d) return toast('RMP not found');
    if (user && user.role === 'field') { closeModal(); return viewDoctorVisit(docId); }
    var c = await fin(); if (!c) return toast('Could not verify login — try again');
    modal('<h2 class="anRmp">Referral Income — ' + esc(d.name || '') + '</h2><div class="card">' +
      '<button onclick="webRmpDefaultForm(\'' + esc(docId) + '\')">RMP Default Commission</button>' +
      '<button onclick="webRmpPatientForm(\'' + esc(docId) + '\')">Patient Commission / Payment</button>' +
      '<button class="ghost" onclick="webRmpSummary(\'' + esc(docId) + '\')">Commission Summary</button>' +
      '<button class="ghost" onclick="closeModal();viewDoctorVisit(\'' + esc(docId) + '\')">Previous Records</button>' +
      (isMaster() ? '<button class="ghost" onclick="webRmpAdvance(\'' + esc(docId) + '\')">Advance Payment / Adjust</button>' : '') +
      (isMaster() ? '<button class="ghost" onclick="webRmpPending()">Pending Commission Approvals</button>' : '') +
      '</div>');
  }

  async function webRmpSummary(docId) {
    var d = doctor(docId), c = await fin(); if (!d || !c) return toast('Could not verify login');
    var r = await c.rpc('rmp_rmp_summary', { p_rmp_id: docId });
    if (r.error || !r.data || !r.data.length) return toast('Could not verify commission summary');
    var a = await c.from('rmp_advance_payments').select('amount,allocated_amount,legacy_covered_amount').eq('rmp_id', docId);
    if (a.error) return toast('Could not verify RMP advance');
    var s = r.data[0], advanceAvailable = (a.data || []).reduce(function(n,x){return n+Number(x.amount||0)-Number(x.allocated_amount||0);},0);
    var paidIncludingAdvance = Number(s.paid_to_this_rmp || 0);
    /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬: "সব কিছু Android এর মত হোক") — দুটো বদল:
       ১) সারির **ক্রম** ফোনের মতোই (DoctorVisitActivity.kt:4022-4027):
          Patients → Commission Earned → Paid to this RMP → [Unallocated Advance]
          → **Due** → [Previous RMP Paid] → [More Paid]।
          ওয়েবে "Previous RMP Paid" ভুল করে Due-র আগে বসত।
       ২) কমিশনের টাকায় ফোনের মতোই **পয়সাসহ** (₹41,750.00) — কমিশন শতাংশে
          হিসাব হয় বলে পয়সা সত্যিই আসে; ওয়েবে গোল করে দেখাত, তাই ফোনের
          সঙ্গে অঙ্ক মিলছে না মনে হত। ⛔ হিসাব বদলায়নি, শুধু দেখানো। */
    modal('<h2 class="anRmp">Referral Income — ' + esc(d.name || '') + '</h2><div class="card"><b>Patients: ' + Number(s.patient_count || 0) + '</b><br>' +
      'Commission Earned: <b>' + rmpMoney(s.earned || 0) + '</b><br>Paid to this RMP: <b>' + rmpMoney(paidIncludingAdvance) + '</b>' +
      (advanceAvailable > 0 ? '<br>Unallocated Advance: <b>' + rmpMoney(advanceAvailable) + '</b>' : '') +
      '<br>Due: <b>' + rmpMoney(s.due || 0) + '</b>' +
      (Number(s.previous_rmp_paid) > 0 ? '<br>Previous RMP Paid: <b>' + rmpMoney(s.previous_rmp_paid) + '</b>' : '') +
      (Number(s.overpaid) > 0 ? '<br><b style="color:#b42318">More Paid: ' + rmpMoney(s.overpaid) + '</b>' : '') + '</div>');
  }

  async function webRmpDefaultForm(docId) {
    var d = doctor(docId), c = await fin(); if (!d || !c) return toast('Could not open');
    var r = await c.from('rmp_commission_defaults').select('commission_mode,commission_value').eq('rmp_id', docId).limit(1).maybeSingle();
    var old = r && r.data || {};
    modal('<h2 class="anRmp">RMP Default Commission</h2><div class="card"><b>' + esc(d.name || '') + '</b>' +
      '<label>Commission Type</label><select id="rmpDefMode" class="input"><option value="PERCENT"' + (old.commission_mode === 'AMOUNT' ? '' : ' selected') + '>Percent (%)</option><option value="AMOUNT"' + (old.commission_mode === 'AMOUNT' ? ' selected' : '') + '>Fixed Amount (₹)</option></select>' +
      '<label>Default Value</label><input id="rmpDefValue" class="input" inputmode="decimal" value="' + esc(old.commission_value == null ? '' : old.commission_value) + '">' +
      '<button onclick="webRmpSaveDefault(\'' + esc(docId) + '\')">Save Default</button><button class="ghost" onclick="openWebRmpCommission(\'' + esc(docId) + '\')">Back</button></div>');
  }

  async function webRmpSaveDefault(docId) {
    var d = doctor(docId), mode = document.getElementById('rmpDefMode').value;
    var value = Number(document.getElementById('rmpDefValue').value);
    if (!d || value < 0 || !isFinite(value) || (mode === 'PERCENT' && value > 100)) return toast('Enter a valid value');
    var c = await fin(); if (!c) return toast('Could not verify login');
    var r = await c.rpc('rmp_set_default', { p_rmp_id: docId, p_rmp_name: d.name || '', p_rmp_mobile: d.mobile || '', p_mode: mode, p_value: value });
    if (r.error) return toast(errText(r.error)); toast('Default commission saved'); closeModal();
  }

  function webRmpPatientForm(docId) {
    var d = doctor(docId); if (!d) return toast('RMP not found');
    modal('<h2 class="anRmp">Patient Commission — ' + esc(d.name || '') + '</h2><div class="card">' +
      '<label>Patient Mobile</label><input id="rmpPatMob" class="input" inputmode="numeric" maxlength="10" oninput="webRmpPatientStatus(this.value)"><div id="rmpPatStatus" class="tiny mut">Enter 10-digit mobile</div>' +
      '<label>Commission Type</label><select id="rmpPatMode" class="input"><option value="DEFAULT">Use RMP Default</option><option value="PERCENT">Percent (%)</option><option value="AMOUNT">Fixed Amount (₹)</option></select>' +
      /* 🔴 V430 — লেবেলটাই ফোনের হুবহু (DoctorVisitActivity.kt:4306-4308), তাই
         আলাদা করে ঘরের ভিতরে সাহায্য-লেখা রাখার দরকার নেই (TK-এর নিয়ম)। */
      '<label>Patient-specific Value (leave blank for Default)</label><input id="rmpPatValue" class="input" inputmode="decimal">' +
      '<div class="actions"><button onclick="webRmpSavePatient(\'' + esc(docId) + '\')">Save Commission</button><button onclick="webRmpPayForm(\'' + esc(docId) + '\')">Pay</button><button class="ghost" onclick="closeModal()">Cancel</button></div></div>');
  }

  var patientStatusToken = 0;
  async function webRmpPatientStatus(v) {
    var m = String(v || '').replace(/\D/g, '').slice(-10), el = document.getElementById('rmpPatStatus'), mine = ++patientStatusToken;
    if (!el) return; if (m.length !== 10) { el.textContent = 'Enter 10-digit mobile'; return; }
    el.textContent = 'Checking patient…'; var p = await webRmpFindPatient(m); if (mine !== patientStatusToken || !el) return;
    el.textContent = p ? '✓ ' + (p.name || 'Patient found') : 'No patient found'; el.style.color = p ? '#0C9E33' : '#C0392B';
  }

  async function webRmpSavePatient(docId) {
    var p = await webRmpFindPatient(document.getElementById('rmpPatMob').value); if (!p) return toast('No patient found with this mobile');
    var mode = document.getElementById('rmpPatMode').value, valText = document.getElementById('rmpPatValue').value;
    var value = mode === 'DEFAULT' ? null : Number(valText);
    if (mode !== 'DEFAULT' && (!isFinite(value) || value < 0 || (mode === 'PERCENT' && value > 100))) return toast('Enter a valid commission value');
    var c = await fin(); if (!c) return toast('Could not verify login');
    var current = await c.from('rmp_patient_commissions').select('rmp_id,set_on,commission_mode,commission_value').eq('patient_row_id', p.id).limit(1).maybeSingle();
    var changingRmp = current && current.data && String(current.data.rmp_id) !== String(docId), r;
    if (changingRmp && isMaster()) r = await c.rpc('rmp_reassign_patient', { p_patient_row_id: p.id, p_new_rmp_id: docId });
    else if (changingRmp) r = await c.rpc('rmp_request_approval', { p_request_type: 'RMP_REASSIGNMENT', p_patient_row_id: p.id, p_payload: { old_rmp_id: current.data.rmp_id, new_rmp_id: docId }, p_reason: null });
    else if (current && current.data && String(current.data.set_on || '') < today() && !isMaster()) {
      var requestedMode = mode, requestedValue = value;
      if (mode === 'DEFAULT') {
        var def = await c.from('rmp_commission_defaults').select('commission_mode,commission_value').eq('rmp_id', docId).limit(1).maybeSingle();
        if (!def || def.error || !def.data) return toast('RMP Default is not set');
        requestedMode = def.data.commission_mode; requestedValue = Number(def.data.commission_value || 0);
      }
      r = await c.rpc('rmp_request_approval', { p_request_type: 'PAST_COMMISSION_CHANGE', p_patient_row_id: p.id,
        p_payload: { rmp_id: docId, mode: requestedMode, value: requestedValue, set_on: current.data.set_on,
          old_mode: current.data.commission_mode, old_value: Number(current.data.commission_value || 0) }, p_reason: null });
    }
    else r = await c.rpc('rmp_set_patient_commission', { p_patient_row_id: p.id, p_rmp_id: docId, p_mode: mode === 'DEFAULT' ? null : mode, p_value: value, p_set_on: null });
    if (r.error) return toast(errText(r.error));
    toast((changingRmp || (current && current.data && String(current.data.set_on || '') < today() && !isMaster())) ?
      'Request sent to Master — no commission changed before approval' : 'Patient commission saved'); closeModal();
  }

  async function webRmpPayForm(docId) {
    var p = await webRmpFindPatient(document.getElementById('rmpPatMob').value); if (!p) return toast('No patient found with this mobile');
    var c = await fin(); if (!c) return toast('Could not verify login');
    var setting = await c.from('rmp_patient_commissions').select('id,rmp_id,rmp_name').eq('patient_row_id', p.id).limit(1).maybeSingle();
    if (!setting || setting.error || !setting.data) return toast('Commission is not set for this patient');
    if (String(setting.data.rmp_id) !== String(docId)) return toast('This patient is assigned to ' + (setting.data.rmp_name || 'another RMP') + '. Change RMP with approval before payment.');
    var r = await c.rpc('rmp_summary', { p_patient_row_id: p.id });
    if (r.error || !r.data || !r.data.length) return toast('Could not verify commission balance');
    var s = r.data[0]; window.__rmpPayPatient = p; window.__rmpPatientCommissionId = setting.data.id; window.__rmpVerifiedDue = Number(s.due || 0);
    modal('<h2 class="anRmp">RMP Commission Payment — ' + esc(p.name || '') + '</h2><div class="card">' +
      '<b>Earned ' + rmpMoney(s.earned) + ' · Paid ' + rmpMoney(s.paid) + ' · Due ' + rmpMoney(s.due) + '</b>' + (Number(s.overpaid) > 0 ? '<br><b style="color:#b42318">More paid ' + rmpMoney(s.overpaid) + '</b>' : '') +
      '<label>Payment Date</label><input id="rmpPayDate" type="date" max="' + today() + '" value="' + today() + '" class="input">' +
      '<label>Amount to Pay *</label><input id="rmpPayAmount" class="input" inputmode="decimal" placeholder="Commission payment amount"><label>Payment Mode</label><select id="rmpPayMode" class="input"><option>Cash</option><option>Online</option></select>' +
      '<label>Transaction / Reference No. (Optional)</label><input id="rmpPayRef" class="input">' +
      /* 🔴 V437 #22 — ফোনে বোতামের ক্রম Cancel · History · Save
         (`DoctorVisitActivity.kt:4197-4203`); ওয়েবে উল্টো ছিল। ⛔ শুধু ক্রম। */
      '<div class="actions"><button class="ghost" onclick="closeModal()">Cancel</button><button class="ghost" onclick="webRmpPaymentHistory()">History</button><button onclick="webRmpSavePayment()">Save Payment</button></div></div>');
  }

  async function webRmpSavePayment() {
    var p = window.__rmpPayPatient, amount = Number(document.getElementById('rmpPayAmount').value), date = document.getElementById('rmpPayDate').value;
    if (!p || !(amount > 0) || !date) return toast('Enter a valid amount and date');
    var mode = document.getElementById('rmpPayMode').value.toUpperCase(), ref = document.getElementById('rmpPayRef').value || '';
    var due = Number(window.__rmpVerifiedDue);
    if (!isFinite(due)) return toast('Commission balance is not verified');
    /* 🔴 V430 — ফোনের হুবহু লেখা (DoctorVisitActivity.kt:4471, 4499) — সঙ্গে
       ফোনের সেই সতর্ক-লাইনটাও, যাতে ভুল করে বেশি টাকা পাশ না হয়ে যায়। */
    if (amount > due && !isMaster()) return toast('Only Master can pay more than Due amount');
    if (amount > due && isMaster() && !confirm('Payment is higher than Ref. Due.\n\nRef. Due: ' + rmpMoney(due) + '\nPayment: ' + rmpMoney(amount) + '\nMore: ' + rmpMoney(amount - due) + '\n\nSave only if this extra amount is intentionally approved by Master.\n\nMaster Approve & Save?')) return;
    var c = await fin(); if (!c) return toast('Could not verify login');
    var r;
    if (date !== today() && !isMaster()) r = await c.rpc('rmp_request_approval', { p_request_type: 'BACKDATE_PAYMENT', p_patient_row_id: p.id, p_payload: { amount: amount, paid_on: date, mode: mode, reference_no: ref }, p_reason: null });
    else r = await c.rpc('rmp_record_payment', { p_patient_row_id: p.id, p_amount: amount, p_paid_on: date, p_mode: mode, p_reference_no: ref || null });
    if (r.error) return toast(errText(r.error));
    if (date !== today() && !isMaster()) { toast('Request sent to Master — not added until approval'); return closeModal(); }
    var paymentId = String(r.data || '').replace(/^"|"$/g, '');
    if (!paymentId) return toast('Cloud response was not verified — do not enter the payment again; open History');
    var verify = await c.from('rmp_commission_payments').select('id,paid_on,amount,mode').eq('id', paymentId).limit(1).maybeSingle();
    if (verify.error || !verify.data) return toast('Cloud accepted the request but verification is unavailable — do not enter it again; open History');
    var landed = String(verify.data.id) === paymentId && String(verify.data.paid_on) === date &&
      String(verify.data.mode).toUpperCase() === mode && Math.abs(Number(verify.data.amount) - amount) < 0.001;
    if (!landed) return toast('Cloud payment did not match — do not enter it again; open History');
    toast('Commission payment saved and verified'); webRmpPaymentHistory();
  }

  async function webRmpPaymentHistory() {
    var p = window.__rmpPayPatient, commissionId = window.__rmpPatientCommissionId;
    if (!p || !commissionId) return toast('Patient commission is not selected');
    var c = await fin(); if (!c) return toast('Could not verify login');
    var r = await c.from('rmp_commission_payments')
      .select('id,rmp_name,paid_on,amount,mode,reference_no,hidden_from_non_master,recorded_by')
      .eq('patient_commission_id', commissionId).order('paid_on', { ascending: false }).limit(500);
    if (r.error) return toast(errText(r.error));
    var rows = r.data || []; window.__rmpHistoryRows = rows;
    modal('<h2 class="anRmp">Commission Payment History — ' + esc(p.name || '') + '</h2>' +
      (rows.map(function (x, i) { return '<div class="card"><b>' + esc(x.rmp_name || '') + '</b><br>' +
        esc(wlv1Dot(x.paid_on || '')) + ' · <b>' + rmpMoney(x.amount || 0) + '</b> · ' + esc(x.mode || '') +
        (isMaster() && x.hidden_from_non_master ? '<br><b style="color:#b42318">Master Private</b>' : '') +
        '<div class="actions">' + ((isMaster() || String(x.paid_on) === today()) ? '<button onclick="webRmpEditPaymentForm(' + i + ')">Edit / Delete</button>' : '') + '</div></div>'; }).join('') || '<div class="card mut">No commission payment yet</div>') +
      '<button class="ghost" onclick="closeModal()">Close</button>');
  }

  function webRmpEditPaymentForm(index) {
    var x = (window.__rmpHistoryRows || [])[Number(index)]; if (!x) return toast('Payment record not found');
    window.__rmpEditPayment = x;
    modal('<h2 class="anRmp">Edit Commission Payment</h2><div class="card">' +
      /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬) — ফোনে সংশোধনের সময় **তারিখ বদলানো
         যায় না** (DoctorVisitActivity.kt:4540-4567, 4602 — আগের তারিখটাই
         আবার পাঠায়)। ওয়েবে ঘরটা খোলা ছিল, তাই একটা পেমেন্ট ভুল করে অন্য
         দিনে সরে যেতে পারত — সেদিনের হিসাবও তখন মিলত না।
         এখন তারিখটা শুধু দেখা যায়, বদলানো যায় না। */
      '<label>Payment Date</label><input id="rmpEditDate" type="date" value="' + esc(x.paid_on || '') + '" class="input" readonly disabled>' +
      '<input id="rmpEditDateKeep" type="hidden" value="' + esc(x.paid_on || '') + '">' +
      '<label>Amount to Pay *</label><input id="rmpEditAmount" class="input" inputmode="decimal" value="' + esc(x.amount == null ? '' : x.amount) + '">' +
      '<label>Payment Mode</label><select id="rmpEditMode" class="input"><option' + (String(x.mode).toUpperCase() === 'CASH' ? ' selected' : '') + '>Cash</option><option' + (String(x.mode).toUpperCase() === 'ONLINE' ? ' selected' : '') + '>Online</option></select>' +
      '<label>Transaction / Reference No. (Optional)</label><input id="rmpEditRef" class="input" value="' + esc(x.reference_no || '') + '">' +
      (isMaster() ? '<label><input id="rmpEditPrivate" type="checkbox"' + (x.hidden_from_non_master ? ' checked' : '') + '> Master Private Change (history visible only to Master)</label>' : '') +
      '<label>Reason (Optional)</label><input id="rmpEditReason" class="input">' +
      '<div class="actions"><button onclick="webRmpSavePaymentEdit()">Save</button><button class="danger" onclick="webRmpDeletePayment()">Delete</button><button class="ghost" onclick="webRmpPaymentHistory()">Back</button></div></div>');
  }

  async function webRmpSavePaymentEdit() {
    var p = window.__rmpPayPatient, x = window.__rmpEditPayment;
    /* 🔴 V430 — তারিখ আর বদলানো যায় না; মূল সারির তারিখটাই আবার পাঠানো হয়। */
    var amount = Number(document.getElementById('rmpEditAmount').value),
        date = (document.getElementById('rmpEditDateKeep') || {}).value || x.paid_on;
    if (!p || !x || !(amount > 0) || !date) return toast('Enter a valid amount and date');
    var mode = document.getElementById('rmpEditMode').value.toUpperCase();
    var ref = document.getElementById('rmpEditRef').value || '', reason = document.getElementById('rmpEditReason').value || '';
    var privateChange = isMaster() && !!document.getElementById('rmpEditPrivate')?.checked;
    var c = await fin(); if (!c) return toast('Could not verify login'); var r;
    if (!isMaster() && (String(x.paid_on) !== today() || date !== today())) return toast('Only Master can edit an earlier payment');
    r = await c.rpc('rmp_edit_payment', { p_payment_id: x.id, p_amount: amount, p_paid_on: date,
      p_mode: mode, p_reference_no: ref || null, p_master_private: privateChange, p_reason: reason || null });
    if (r.error) return toast(errText(r.error));
    toast('Commission and Expense updated together');
    webRmpPaymentHistory();
  }

  async function webRmpDeletePayment() {
    var x = window.__rmpEditPayment; if (!x) return toast('Payment record not found');
    if (!isMaster() && String(x.paid_on) !== today()) return toast('Only Master can delete an earlier payment');
    if (!confirm('Delete Commission Payment?\n\nThis will also remove its linked Expense. The audit history will remain.')) return;
    var c = await fin(); if (!c) return toast('Could not verify login');
    var reason = document.getElementById('rmpEditReason').value || '';
    var r = await c.rpc('rmp_delete_payment', { p_payment_id: x.id, p_reason: reason || null });
    if (r.error) return toast(errText(r.error));
    toast('Commission Payment deleted and balance adjusted'); webRmpPaymentHistory();
  }

  async function webRmpDirectPaymentForm(docId) {
    /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬) — ফোনে এই কাজটা **নিজের ব্রাঞ্চের**
       স্টাফ/ডাক্তারও করতে পারেন; শুধু অন্য ব্রাঞ্চের RMP হলে আটকায়
       (DoctorVisitActivity.kt:4083-4088)। ওয়েবে শুধু Master পারতেন, তাই
       ব্রাঞ্চের লোক RMP-কে টাকা দিলে অ্যাপে বসাতে পারতেন না।
       ⛔ আসল সুরক্ষা ডেটাবেসেই (fin.rmp_record_advance) — সেটা ছোঁয়া হয়নি;
          এটা শুধু আগেভাগে সহজ ভাষায় জানিয়ে দেওয়া। */
    var __d = doctor(docId) || {};
    var __ub = String((typeof user !== 'undefined' && user && user.branch) || '').trim();
    var __rb = String(__d.branch || '').trim();
    if (!isMaster() && __rb && __ub && __ub.toLowerCase() !== __rb.toLowerCase())
      return toast('You can pay only an RMP of your own branch (this RMP: ' + __rb + ')');
    var d=doctor(docId),c=await fin(); if(!d||!c)return toast('Could not verify RMP');
    var s=await c.rpc('rmp_rmp_summary',{p_rmp_id:docId});
    if(s.error||!s.data||!s.data.length)return toast('Could not verify Ref. Due — payment is locked');
    var due=Number(s.data[0].due||0); window.__rmpDirectDoc=docId; window.__rmpDirectDue=due;
    modal('<h2 class="anRmp">RMP Payment — '+esc(d.name||'')+'</h2><div class="card"><b style="color:#B42318">Ref. Due '+rmpMoney(due)+'</b>'+
      '<label>Payment Date</label><input id="rmpDirectDate" type="date" max="'+today()+'" value="'+today()+'" class="input">'+
      '<label>Amount to Pay *</label><input id="rmpDirectAmount" class="input" inputmode="decimal">'+
      '<label>Payment Mode</label><select id="rmpDirectMode" class="input"><option>Cash</option><option>Online</option></select>'+
      '<label>Transaction / Reference No. (Optional)</label><input id="rmpDirectRef" class="input">'+
      /* 🔴 V437 #22 — একই ক্রম এখানেও। */
      '<div class="actions"><button class="ghost" onclick="closeModal()">Cancel</button><button class="ghost" onclick="webRmpAdvance(\''+esc(docId)+'\')">History</button><button onclick="webRmpDirectPaymentSave()">Save</button></div></div>');
  }
  /* 🔴🔒 V426/V427 (TK-নির্দেশ ১৭.০৮.২০২৬) — Chamber Review পর্দার জন্য দুটো
     ছোট্ট read-only ডাক। হিসাব পুরোটাই সার্ভারে (ফোনের সঙ্গে হুবহু এক), আর
     রোগীপ্রতি আলাদা ডাক নয় — **এক ডাকেই** পুরো দিনের তালিকা, তাই Egress বাড়ে না।
     ⛔ ব্যর্থ হলে খালি তালিকা ফেরে; ডাকা জায়গায় তখন ওই অংশটুকু দেখানো হয় না,
        বাকি পর্দা আগের মতোই চলে। */
  window.wlv1RmpDayCommission = async function (branch, date) {
    try { return (await smallRpc('rmp_day_commission', { p_branch: branch, p_date: date })) || []; }
    catch (_) { return []; }
  };
  window.wlv1RmpDayPaid = async function (branch, date) {
    try { return (await smallRpc('rmp_day_paid', { p_branch: branch, p_date: date })) || []; }
    catch (_) { return []; }
  };
  window.webRmpDirectPaymentForm=webRmpDirectPaymentForm;

  async function webRmpDirectPaymentSave(){
    var docId=window.__rmpDirectDoc,due=Number(window.__rmpDirectDue||0),amount=Number(document.getElementById('rmpDirectAmount')?.value||0);
    var paidOn=document.getElementById('rmpDirectDate')?.value||'',mode=String(document.getElementById('rmpDirectMode')?.value||'').toUpperCase();
    var ref=document.getElementById('rmpDirectRef')?.value||'';
    if(!docId||!(amount>0)||!paidOn)return toast('Enter a valid amount and date');
    /* 🔴🔴🔴🆕🔒 V437 (নিজের অডিটে ধরা — ফোন-বনাম-ওয়েব তালিকার #২১, **অনুমতির
       ফাঁক**)। ফোনে (`DoctorVisitActivity.kt:4220-4234`) Due-র চেয়ে বেশি টাকা
       **শুধু Master** পাশ করতে পারেন; অন্য কেউ চাপলে শুধু একটা বার্তা ওঠে ও
       সেভ হয় **না**। ওয়েবের এই লাইনে `isMaster()` পরীক্ষাটাই ছিল না — যেকোনো
       স্টাফ OK চেপে বাড়তি টাকা বসিয়ে দিতে পারতেন।
       ⛔ এই ফাইলেরই ২৪১ নম্বর লাইনে ঠিক এই পরীক্ষাটা আছে — অর্থাৎ এখানে বাদ
          পড়াটা ভুলবশত, ইচ্ছাকৃত নয়। এখন দুটো পথ এক নিয়মে চলে।
       ⛔ Due-র সমান বা কম টাকায় কিছুই বদলায়নি — আগের মতোই সবাই পারেন। */
    if(amount>due&&!isMaster())return toast('Only Master can pay more than Due amount');
    if(amount>due&&isMaster()&&!confirm('Payment is higher than Ref. Due.\n\nRef. Due: '+rmpMoney(due)+'\nPayment: '+rmpMoney(amount)+'\nMore: '+rmpMoney(amount-due)+'\n\nSave only if this extra amount is intentionally approved by Master.\n\nMaster Approve & Save?'))return;
    var c=await fin();if(!c)return toast('Could not verify login');
    var r=await c.rpc('rmp_record_advance',{p_rmp_id:docId,p_amount:amount,p_paid_on:paidOn,p_mode:mode,p_reference_no:ref||null});
    if(r.error)return toast(errText(r.error));
    var id=String(r.data||'').replace(/^"|"$/g,'');
    var v=await c.from('rmp_advance_payments').select('id,rmp_id,paid_on,amount,mode').eq('id',id).limit(1).maybeSingle();
    if(v.error||!v.data||String(v.data.rmp_id)!==String(docId)||String(v.data.paid_on)!==String(paidOn)||String(v.data.mode).toUpperCase()!==mode||Math.abs(Number(v.data.amount)-amount)>=0.001)
      return toast('Cloud payment was not verified — do not enter again; open History');
    toast('RMP payment saved and verified');webRmpAdvance(docId);
  }
  window.webRmpDirectPaymentSave=webRmpDirectPaymentSave;

  async function webRmpAdvance(docId) {
    if (!isMaster()) return toast('Only Master can adjust an RMP advance');
    var d = doctor(docId), c = await fin(); if (!d || !c) return toast('Could not verify RMP');
    var r = await c.from('rmp_advance_payments').select('id,paid_on,amount,allocated_amount,legacy_covered_amount,mode,reference_no')
      .eq('rmp_id', docId).order('paid_on', { ascending: false }).limit(200);
    if (r.error) return toast(errText(r.error));
    var rows = r.data || [], total = rows.reduce(function(a,x){return a+Number(x.amount||0);},0),
      allocated = rows.reduce(function(a,x){return a+Number(x.allocated_amount||0);},0);
    window.__rmpAdvanceDoc = docId; window.__rmpAdvanceRows = rows;
    modal('<h2 class="anRmp">Advance Payment — ' + esc(d.name || '') + '</h2><div class="card">' +
      /* 🔴 V430 (TK-নির্দেশ ১৮.০৮.২০২৬) — ফোনে (DoctorVisitActivity.kt:3935-3940)
         **শুধু সেই advance-গুলোই দেখানো হয় যেগুলোর টাকা এখনো বাকি আছে**, আর
         সারিতে লেখা থাকে কত **বাকি** (কত দেওয়া হয়েছিল তা নয়)। ওয়েবে সব
         ২০০টা সারিই পড়ে থাকত — শেষ হয়ে যাওয়াগুলোও, তাই তালিকা লম্বা ও
         বিভ্রান্তিকর হত। ⛔ কোনো সারি মোছা হয় না; মোট তিনটে অঙ্ক আগের মতোই
         পুরো তালিকা ধরেই হিসাব হয়। */
      'Paid: <b>' + rmpMoney(total) + '</b><br>Adjusted: <b>' + rmpMoney(allocated) + '</b><br>Available: <b>' + rmpMoney(total-allocated) + '</b></div>' +
      (rows.map(function(x,i){return {x:x,i:i,left:Number(x.amount||0)-Number(x.allocated_amount||0)};})
        .filter(function(o){return o.left>0.001;})
        .map(function(o){ return '<div class="card">' +
        esc(wlv1Dot(o.x.paid_on||'')) + ' · <b>' + rmpMoney(o.left) + '</b> available · ' + esc(o.x.mode||'') +
        '<div class="actions"><button onclick="webRmpAdvanceAdjustForm('+o.i+')">Adjust to Patient</button></div></div>';}).join('') || '<div class="card mut">No unallocated payment</div>') +
      '<button class="ghost" onclick="openWebRmpCommission(\'' + esc(docId) + '\')">Back</button>');
  }

  function webRmpAdvanceAdjustForm(index) {
    var x=(window.__rmpAdvanceRows||[])[Number(index)]; if(!x) return toast('Advance payment not found');
    var left=Number(x.amount||0)-Number(x.allocated_amount||0); window.__rmpAdvanceSelected=x;
    modal('<h2 class="anRmp">Adjust — ' + esc((doctor(window.__rmpAdvanceDoc)||{}).name || '') + '</h2><div class="card"><b>Available Advance: ' + rmpMoney(left) + '</b>' +
      '<label>Old Patient Mobile</label><input id="rmpAdvanceMobile" class="input" inputmode="tel">' +
      '<label>Amount</label><input id="rmpAdvanceAmount" class="input" inputmode="decimal">' +
      '<div class="actions"><button onclick="webRmpAdvanceAdjustSave()">Adjust</button><button class="ghost" onclick="webRmpAdvance(window.__rmpAdvanceDoc)">Back</button></div></div>');
  }

  async function webRmpAdvanceAdjustSave() {
    var x=window.__rmpAdvanceSelected, docId=window.__rmpAdvanceDoc;
    var amount=Number(document.getElementById('rmpAdvanceAmount').value), mobile=document.getElementById('rmpAdvanceMobile').value||'';
    var left=x ? Number(x.amount||0)-Number(x.allocated_amount||0) : 0;
    if(!x || !(amount>0) || amount>left) return toast('Enter an amount within available Advance');
    var p=await webRmpFindPatient(mobile); if(!p) return toast('No patient found with this mobile');
    var c=await fin(); if(!c) return toast('Could not verify login');
    var setting=await c.from('rmp_patient_commissions').select('id,rmp_id').eq('patient_row_id',p.id).limit(1).maybeSingle();
    if(setting.error) return toast(errText(setting.error));
    if(!setting.data) return toast('Set this patient\'s commission first');
    else if(String(setting.data.rmp_id)!==String(docId)) return toast('This patient belongs to another RMP');
    var summary=await c.rpc('rmp_summary',{p_patient_row_id:p.id});
    if(summary.error||!summary.data||!summary.data.length)return toast('Could not verify patient commission Due');
    var due=Number(summary.data[0].due||0), allowOverDue=false;
    if(amount>due){allowOverDue=confirm('Payment is higher than this patient Ref. Due.\n\nRef. Due: '+rmpMoney(due)+'\nAdjust: '+rmpMoney(amount)+'\nMore: '+rmpMoney(amount-due)+'\n\nMaster Approve & Adjust?');if(!allowOverDue)return;}
    var r=await c.rpc('rmp_allocate_advance',{p_advance_id:x.id,p_patient_row_id:p.id,p_amount:amount,p_allow_over_due:allowOverDue});
    if(r.error)return toast(errText(r.error));
    var paymentId=String(r.data||'').replace(/^"|"$/g,'');
    var verify=await c.from('rmp_commission_payments').select('id,amount').eq('id',paymentId).limit(1).maybeSingle();
    if(verify.error||!verify.data||Math.abs(Number(verify.data.amount)-amount)>=0.001) return toast('Adjustment was not verified — do not repeat; reopen Advance History');
    toast('Advance adjusted and verified'); webRmpAdvance(docId);
  }

  async function webRmpPending() {
    if (!isMaster()) return toast('Only Master Admin'); var c = await fin(); if (!c) return toast('Could not verify login');
    var r = await c.from('rmp_commission_requests').select('id,request_type,patient_row_id,payload,requested_by,requested_at').eq('status', 'PENDING').order('requested_at').limit(200);
    if (r.error) return toast(errText(r.error)); var rows = r.data || [];
    modal('<h2 class="anRmp">Pending Commission Approvals</h2>' + (rows.map(function (x) { var a = x.payload || {}, detail;
      /* 🔴 V430 — তারিখ ফোনের মতোই ০৬.০৮.২০২৬ ধাঁচে (কাঁচা 2026-08-06 নয়),
         আর টাকা পয়সাসহ (DoctorVisitActivity.kt:4196-4197)। */
      if (x.request_type === 'BACKDATE_PAYMENT') detail = 'Backdate Payment · ' + rmpMoney(a.amount || 0) + ' · ' + esc(wlv1Dot(a.paid_on || ''));
      else if (x.request_type === 'PAYMENT_EDIT') detail = 'Payment Correction · ' + rmpMoney(a.amount || 0) + ' · ' + esc(wlv1Dot(a.paid_on || ''));
      else if (x.request_type === 'PAST_COMMISSION_CHANGE') detail = (a.mode === 'PERCENT' ? 'Percent' : 'Fixed Amount') + ' Change<br>' + esc(a.value == null ? '' : a.value);
      else if (x.request_type === 'RMP_REASSIGNMENT') detail = 'Patient RMP Change';
      else detail = 'Commission Approval';
      return '<div class="card"><b>' + detail + '</b><br>' + esc(x.requested_by || '') + '<div class="actions"><button onclick="webRmpDecide(\'' + esc(x.id) + '\',true)">Approve</button><button class="ghost" onclick="webRmpDecide(\'' + esc(x.id) + '\',false)">Reject</button></div></div>'; }).join('') || '<div class="card mut">No pending commission approval</div>'));
  }
  async function webRmpDecide(id, approve) { var c = await fin(); if (!c) return toast('Could not verify login'); var r = await c.rpc('rmp_decide_request', { p_request_id: id, p_approve: !!approve }); if (r.error) return toast(errText(r.error)); toast(approve ? 'Approved' : 'Rejected'); webRmpPending(); }

  Object.assign(window, { webRmpActivateAfterPayment: webRmpActivateAfterPayment, openWebRmpCommission: openWebRmpCommission, webRmpSummary: webRmpSummary, webRmpDefaultForm: webRmpDefaultForm,
    webRmpSaveDefault: webRmpSaveDefault, webRmpPatientForm: webRmpPatientForm, webRmpSavePatient: webRmpSavePatient,
    webRmpPayForm: webRmpPayForm, webRmpSavePayment: webRmpSavePayment, webRmpPaymentHistory: webRmpPaymentHistory,
    webRmpEditPaymentForm: webRmpEditPaymentForm, webRmpSavePaymentEdit: webRmpSavePaymentEdit,
    webRmpPending: webRmpPending, webRmpDecide: webRmpDecide, webRmpPatientStatus: webRmpPatientStatus,
    webRmpAdvance: webRmpAdvance, webRmpAdvanceAdjustForm: webRmpAdvanceAdjustForm, webRmpAdvanceAdjustSave: webRmpAdvanceAdjustSave,
    webRmpCardCounts: webRmpCardCounts, webRmpViewAll: webRmpViewAll, webRmpPerformance: webRmpPerformance });
})();
