/* ============================================================================
   🔔🔒 V1186 (০৭.০৯.২০২৬, TK-নির্দেশ ও ফটো-প্রুফ পাশ) — Doctor Note & Reminder
   (কম্পিউটার), ফোনের `DoctorReminderActivity` / `DoctorReminderRepository`-র যমজ।

   TK-এর কথা (হুবহু):
     · "এটা প্রত্যেকের হোম স্ক্রিনে ই থাকবে"
     · "যে কোন staff, যে কোন ডাক্তার এবং মাস্টার এটা ক্রিয়েট করতে পারবে"
     · "যে ব্রাঞ্চের যে Doctor তাকেই করতে পারবে"
     · "পাঠানোর পর KH MANDAL কে Accept করতে হবে"
     · "পরে যেন History তে ও দেখতে পায়, যাতে কেউ অস্বীকার না করতে পারে"

   ⛔ পুরনো `patients.doctorReminder*` ব্যবস্থা এক অক্ষরও ছোঁয়া হয়নি।
   ⛔ টাকার কোনো হিসাব এখানে নেই।
   ========================================================================== */
(function () {
  var TABLE = 'doctor_reminders';
  /* ওই ব্রাঞ্চের নিজের ডাক্তার + Dr. K.H MANDAL + TK BISWAS — TK-র পাঁচটা
     তালিকা মিলিয়ে বের করা একটাই নিয়ম (ফোনেও হুবহু এটাই)। */
  var EVERYWHERE_DOCTOR = '7980993652';   // Dr. K.H MANDAL

  function d10(v){ return String(v||'').replace(/\D/g,'').slice(-10); }
  function me(){ try{ return d10((typeof user!=='undefined'&&user&&user.mobile)||''); }catch(e){ return ''; } }
  function myBranch(){ try{ return String(((typeof user!=='undefined'&&user)||{}).branch||'').trim(); }catch(e){ return ''; } }
  function myRole(){ try{ return String(((typeof user!=='undefined'&&user)||{}).role||'').toLowerCase(); }catch(e){ return ''; } }
  function myName(){ try{ return (typeof codeName==='function' ? (codeName(user&&user.mobile)||'') : '') || String((user&&user.name)||''); }catch(e){ return ''; } }
  function nowIso(){ return new Date().toISOString(); }
  function todayIso(){ try{ return window.MOD.todayIST(); }catch(e){ return new Date().toISOString().slice(0,10); } }
  function esc2(v){ try{ return esc(String(v==null?'':v)); }catch(e){ return String(v==null?'':v); } }

  function dmy(iso){ try{ var p=String(iso).slice(0,10).split('-'); return p[2]+'/'+p[1]+'/'+p[0]; }catch(e){ return iso||''; } }
  function stamp(raw){
    var t=String(raw||''); if(t.length<10) return '';
    var d=dmy(t); if(t.length<16) return d;
    try{
      var hh=parseInt(t.substr(11,2),10), mm=t.substr(14,2);
      var ap=hh>=12?'PM':'AM', h12=(hh===0)?12:(hh>12?hh-12:hh);
      return d+'  ·  '+h12+'.'+mm+' '+ap;
    }catch(e){ return d; }
  }
  function time12(hm){
    var p=String(hm||'').split(':'); if(p.length<2) return hm||'';
    var h=parseInt(p[0],10); if(isNaN(h)) return hm;
    var ap=h>=12?'PM':'AM', h12=(h===0)?12:(h>12?h-12:h);
    return h12+'.'+p[1]+' '+ap;
  }

  /** ওই ব্রাঞ্চে যাঁদের কাছে পাঠানো যায় (ফোনের `doctorsForBranch`-এর যমজ)। */
  function doctorsForBranch(branch){
    var out=[];
    try{
      var list=(typeof allUsers==='function')?allUsers():[];
      (list||[]).forEach(function(u){
        var role=String(u.role||'').toLowerCase();
        var mob=d10(u.mobile);
        var nm=(typeof codeName==='function'?(codeName(u.mobile)||''):'')||String(u.name||'')||mob;
        if(role==='doctor'){
          var mine=String(u.branch||'').trim().toLowerCase()===String(branch||'').trim().toLowerCase();
          if(mine || mob===EVERYWHERE_DOCTOR) out.push([nm,mob]);
        } else if(role==='master'){ out.push([nm,mob]); }
      });
    }catch(e){}
    var seen={}, uniq=[];
    out.forEach(function(x){ if(!seen[x[1]]){ seen[x[1]]=1; uniq.push(x); } });
    return uniq;
  }

  var DR_ROWS=[];
  async function drRemLoad(historyMode){
    if(typeof sb==='undefined'||!sb) return [];
    var q=sb.from(TABLE).select('*').eq('active',true);
    if(!historyMode) q=q.gte('remindDate',todayIso());
    var r=await q.order('createdAt',{ascending:false}).limit(300);
    var rows=(r&&r.data)||[];
    if(myRole()==='master') return rows;
    var m=me(), br=myBranch().toLowerCase();
    return rows.filter(function(x){
      var f=d10(x.forMobile), b=d10(x.byMobile);
      return f===m || b===m || (!f && String(x.branch||'').trim().toLowerCase()===br);
    });
  }

  /** এই সারিটা এই ব্যবহারকারী Accept করতে পারেন কিনা — ফোনের হুবহু নিয়ম।
      ⛔ স্টাফ কখনো নয়; নির্দিষ্ট একজনকে পাঠানো হলে কেবল তিনিই। */
  function canAccept(x){
    if(String(x.acceptedAt||'')) return false;
    var m=me(); if(!m) return false;
    var f=d10(x.forMobile);
    if(f) return f===m;
    if(myRole()==='master') return true;
    if(myRole()!=='doctor') return false;
    return String(x.branch||'').trim().toLowerCase()===myBranch().toLowerCase();
  }

  function drCard(x, withAccept){
    var accepted=!!String(x.acceptedAt||'');
    var rail=accepted?'#0F766E':'#E0A800';
    var btn = (withAccept && canAccept(x))
      ? '<button style="margin-top:9px" onclick="drRemAccept(\'' + esc2(x.id) + '\')">Accept</button>'
      : (accepted
          ? '<div style="margin-top:8px;color:#0A7C3F;font-weight:700;font-size:13px">Accepted · '+esc2(x.acceptedByName||'')+' · '+esc2(stamp(x.acceptedAt))+'</div>'
          : '<div style="margin-top:8px;color:#8A5A00;font-weight:700;font-size:13px">Not accepted yet</div>');
    return '<div style="display:flex;background:#fff;border:1px solid #E7ECEA;border-radius:16px;overflow:hidden;margin-bottom:10px">'+
      '<div style="width:6px;background:'+rail+'"></div>'+
      '<div style="flex:1;padding:14px 16px">'+
        '<div style="font-size:15px;font-weight:700;color:#0B2B1C">'+esc2(x.patientName||'Patient')+
          ' <span style="font-weight:400;color:#7A8794;font-size:13px">'+esc2(x.patientMobile||'')+'</span></div>'+
        '<div style="margin-top:7px;background:#F6FAF7;border:1px solid #E2EDE6;border-radius:11px;padding:10px 12px;font-size:13.5px;color:#17212B">'+esc2(x.note||'')+'</div>'+
        '<div style="margin-top:8px;font-size:12.5px;color:#123E8C;font-weight:700">For  '+esc2(x.forName||'All doctors')+'</div>'+
        '<div style="font-size:12.5px;color:#8A5A00;font-weight:700">By  '+esc2(x.byName||'')+(x.byBranch?(' · '+esc2(x.byBranch)):'')+'</div>'+
        (x.remindDate?('<div style="margin-top:7px;font-size:12.5px;color:#B45309;font-weight:700">Remind on  '+esc2(dmy(x.remindDate))+(x.remindTime?('  ·  '+esc2(time12(x.remindTime))):'')+'</div>'):'')+
        btn+
      '</div></div>';
  }

  /** হোম পর্দার কার্ড — কিছু না থাকলে কিছুই বসে না (ফোনের মতোই)। */
  function drRemHomeCard(){
    setTimeout(function(){
      drRemLoad(false).then(function(rows){
        var el=document.getElementById('drRemHomeBox'); if(!el) return;
        if(!rows.length){ el.innerHTML=''; return; }
        DR_ROWS=rows;
        el.innerHTML='<div class="card" style="padding:0;overflow:hidden;cursor:pointer" onclick="drRemHome()">'+
          '<div style="background:#0B4F2A;color:#fff;padding:12px 16px;display:flex;align-items:center">'+
            '<b style="flex:1;font-size:14px;letter-spacing:.5px">DOCTOR NOTE &amp; REMINDER</b>'+
            '<span style="background:#fff;color:#0B4F2A;border-radius:20px;padding:2px 12px;font-size:12.5px;font-weight:700">'+rows.length+'</span></div>'+
          '<div style="padding:12px 14px">'+rows.slice(0,3).map(function(x){return drCard(x,false)}).join('')+
          '<div style="text-align:center;color:#0A5C33;font-weight:700;font-size:13.5px;padding:6px 0 2px">View all</div></div></div>';
      }).catch(function(){});
    }, 60);
    return '<div id="drRemHomeBox"></div>';
  }

  async function drRemAccept(id){
    if(typeof sb==='undefined'||!sb) return toast('No internet connection');
    try{
      var r=await sb.from(TABLE).update({acceptedBy:me(),acceptedByName:myName(),acceptedAt:nowIso()}).eq('id',id);
      if(r&&r.error) return toast('করা গেল না — আবার চেষ্টা করুন');
      toast('Accepted');
      drRemHome();
    }catch(e){ toast('করা গেল না — আবার চেষ্টা করুন'); }
  }

  async function drRemHome(){
    var rows=[]; try{ rows=await drRemLoad(false); }catch(e){}
    document.getElementById('app').innerHTML='<div class="wrap"><div class="topbar"><b>Doctor Note &amp; Reminder</b>'+
      '<button class="ghost" onclick="dashboard()">Back</button></div><div class="page">'+
      '<div class="card"><div class="actions" style="justify-content:flex-start;gap:10px">'+
        '<button onclick="drRemNew()">New Reminder</button>'+
        '<button class="ghost" onclick="drRemHistory()">Reminder History</button></div></div>'+
      (rows.length? rows.map(function(x){return drCard(x,true)}).join('')
                  : '<div class="card">No reminder right now.</div>')+
      '</div></div>';
  }

  async function drRemHistory(){
    var rows=[]; try{ rows=await drRemLoad(true); }catch(e){}
    function cell(l,v){ return '<div style="flex:1;background:#FBFDFC;border:1px solid #EDF2EF;border-radius:11px;padding:9px 12px;margin:7px 7px 0 0">'+
      '<div style="font-size:10.5px;color:#8B98A9;letter-spacing:.8px">'+l+'</div>'+
      '<div style="font-size:13px;color:#0B2B1C;font-weight:700;margin-top:3px">'+(v||'—')+'</div></div>'; }
    var body=rows.map(function(x){
      var accepted=!!String(x.acceptedAt||'');
      return '<div style="display:flex;background:#fff;border:1px solid #E7ECEA;border-radius:16px;overflow:hidden;margin-bottom:12px">'+
        '<div style="width:6px;background:'+(accepted?'#0F766E':'#E0A800')+'"></div>'+
        '<div style="flex:1;padding:14px 16px">'+
          '<div style="font-size:15px;font-weight:700;color:#0B2B1C">'+esc2(x.patientName||'Patient')+
            ' <span style="font-weight:400;color:#7A8794;font-size:13px">'+esc2(x.patientMobile||'')+'</span></div>'+
          '<div style="margin-top:7px;background:#F6FAF7;border:1px solid #E2EDE6;border-radius:11px;padding:10px 12px;font-size:13.5px;color:#17212B">'+esc2(x.note||'')+'</div>'+
          '<div style="display:flex">'+cell('SENT BY', esc2(x.byName||'')+(x.byBranch?(' · '+esc2(x.byBranch)):''))+cell('SENT ON', esc2(stamp(x.createdAt)))+'</div>'+
          '<div style="display:flex">'+cell('SENT TO', esc2(x.forName||'All doctors'))+cell('ACCEPTED', accepted?(esc2(x.acceptedByName||'')+' · '+esc2(stamp(x.acceptedAt))):'Not yet')+'</div>'+
          '<div style="margin-top:11px;display:flex;align-items:center;border-top:1px solid #EEF1F5;padding-top:11px">'+
            '<span style="font-size:12.5px;color:#5B6B81">Remind on</span>'+
            '<b style="font-size:13.5px;color:#0B2B1C;margin-left:8px">'+esc2(dmy(x.remindDate))+(x.remindTime?('  ·  '+esc2(time12(x.remindTime))):'')+'</b>'+
            '<span style="margin-left:auto;border-radius:12px;padding:5px 14px;font-size:12px;font-weight:700;'+
              (accepted?'background:#E8F6ED;color:#0A7C3F;border:1.5px solid #BFE3CD':'background:#FFF4E5;color:#8A5A00;border:1.5px solid #F0DCA8')+'">'+
              (accepted?'ACCEPTED':'WAITING')+'</span>'+
          '</div>'+
        '</div></div>';
    }).join('');
    document.getElementById('app').innerHTML='<div class="wrap"><div class="topbar"><b>Reminder History</b>'+
      '<button class="ghost" onclick="drRemHome()">Back</button></div><div class="page">'+
      (rows.length?body:'<div class="card">Nothing yet.</div>')+'</div></div>';
  }

  var DR_PICKED=null, DR_FOR=['',''];
  function drRemNew(){
    DR_PICKED=null; DR_FOR=['',''];
    document.getElementById('app').innerHTML='<div class="wrap"><div class="topbar"><b>New Reminder</b>'+
      '<button class="ghost" onclick="drRemHome()">Back</button></div><div class="page"><div class="card">'+
      '<label>Patient</label><input id="drPat" class="input" type="text" placeholder="Patient name or mobile">'+
      '<div id="drPatOut" style="font-size:13px;color:#8A93A0;margin:6px 0 4px">No patient chosen</div>'+
      '<button class="ghost" onclick="drRemFind()">Find Patient</button>'+
      '<label>Note</label><input id="drNote" class="input" type="text" placeholder="What to give / what to do">'+
      '<label>Send to which doctor?</label><select id="drFor" class="input"><option value="">All doctors</option></select>'+
      '<div style="display:flex;gap:9px">'+
        '<div style="flex:1"><label>Which day — reminds the day before</label><input id="drDate" class="input" type="date"></div>'+
        '<div style="flex:1"><label>Time</label><input id="drTime" class="input" type="time"></div>'+
      '</div>'+
      '<label>Reminder by</label>'+
      '<div style="background:#FFF8E9;border:1.5px solid #F0DCA8;border-radius:12px;padding:11px 14px;font-size:13.5px;color:#8A5A00;font-weight:700">'+
        esc2(myName())+(myBranch()?(' · '+esc2(myBranch())):'')+'  (you)</div>'+
      '<div class="actions"><button class="ghost" onclick="drRemHome()">Cancel</button>'+
      '<button onclick="drRemSend()">Send</button></div></div></div></div>';
    drRemFillDoctors(myBranch());
  }

  function drRemFillDoctors(branch){
    var sel=document.getElementById('drFor'); if(!sel) return;
    var docs=doctorsForBranch(branch);
    sel.innerHTML='<option value="">All doctors</option>'+
      docs.map(function(x){ return '<option value="'+esc2(x[1])+'">'+esc2(x[0])+'</option>'; }).join('');
  }

  function drRemFind(){
    var q=String((document.getElementById('drPat')||{}).value||'').trim();
    if(q.length<3) return toast('Type at least 3 letters or digits');
    var digits=q.replace(/\D/g,'');
    var list=[];
    try{
      list=(load('patients')||[]).filter(function(p){
        if(digits.length>=4) return d10(p.mobile).indexOf(digits.slice(-10))>=0 || String(p.mobile||'').indexOf(digits)>=0;
        return String(p.name||'').toLowerCase().indexOf(q.toLowerCase())>=0;
      }).slice(0,25);
    }catch(e){}
    if(!list.length) return toast('No patient found');
    var rows=list.map(function(p,i){
      return '<button class="menuBtn" onclick="drRemPick('+i+')"><b>'+esc2(p.name||'')+'</b><small>'+esc2(p.mobile||'')+' · '+esc2(p.branch||'')+'</small></button>';
    }).join('');
    window.__drFound=list;
    try{ modal('<h2>Choose patient</h2><div class="grid menuGrid">'+rows+'</div>'); }catch(e){}
  }
  function drRemPick(i){
    var p=(window.__drFound||[])[i]; if(!p) return;
    DR_PICKED=p;
    var out=document.getElementById('drPatOut');
    if(out){ out.textContent=(p.name||'')+'   '+(p.mobile||''); out.style.color='#0B2B1C'; }
    drRemFillDoctors(p.branch||myBranch());
    try{ closeModal(); }catch(e){}
  }

  async function drRemSend(){
    if(!DR_PICKED) return toast('Choose a patient first');
    var note=String((document.getElementById('drNote')||{}).value||'').trim();
    if(!note) return toast('Write the note');
    var date=String((document.getElementById('drDate')||{}).value||'').slice(0,10);
    if(!date) return toast('Pick the date');
    var time=String((document.getElementById('drTime')||{}).value||'').slice(0,5);
    var sel=document.getElementById('drFor');
    var forMob=sel?String(sel.value||''):'';
    var forName=sel&&sel.selectedIndex>0?sel.options[sel.selectedIndex].text:'';
    if(typeof sb==='undefined'||!sb) return toast('No internet connection');
    var row={
      id:'drem_'+Date.now()+'_'+Math.floor(Math.random()*1000),
      patientId:String(DR_PICKED.id||''), patientName:String(DR_PICKED.name||''),
      patientMobile:d10(DR_PICKED.mobile), branch:String(DR_PICKED.branch||myBranch()),
      note:note, forMobile:d10(forMob), forName:forName,
      byMobile:me(), byName:myName(), byBranch:myBranch(),
      remindDate:date, remindTime:time, createdAt:nowIso(),
      acceptedBy:'', acceptedByName:'', acceptedAt:'', active:true
    };
    try{
      var r=await sb.from(TABLE).upsert(row);
      if(r&&r.error) return toast('পাঠানো গেল না — আবার চেষ্টা করুন');
      toast('Sent');
      drRemHome();
    }catch(e){ toast('পাঠানো গেল না — আবার চেষ্টা করুন'); }
  }

  window.drRemHome=drRemHome; window.drRemHistory=drRemHistory; window.drRemNew=drRemNew;
  window.drRemAccept=drRemAccept; window.drRemFind=drRemFind; window.drRemPick=drRemPick;
  window.drRemSend=drRemSend; window.drRemHomeCard=drRemHomeCard;
})();
