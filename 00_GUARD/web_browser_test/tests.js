/* 🔵 V1293 — কম্পিউটার-অ্যাপের ব্রাউজার-পরীক্ষা (run.py থেকে চালান)। নকল সার্ভার: Supabase-এ কিছু যায় না। */
const {chromium}=require('playwright');const http=require('http'),fs=require('fs'),path=require('path');
const ROOT=process.env.TK_WEB_ROOT, SDK=fs.readFileSync(process.env.TK_SDK_PATH,'utf8'), EXE=process.env.TK_CHROME||undefined, UD=process.env.TK_UDATA||'udata';
const PORT=8790, BASE='http://localhost:'+PORT;
const srv=http.createServer((q,r)=>{let p=path.join(ROOT,decodeURIComponent(q.url.split('?')[0]));if(p.endsWith('/'))p+='index.html';fs.readFile(p,(e,d)=>{if(e){r.statusCode=404;return r.end()}r.setHeader('content-type',p.endsWith('.js')?'text/javascript':p.endsWith('.css')?'text/css':'text/html');r.end(d)})}).listen(PORT);
const IDS=[];for(let i=0;i<1500;i++)IDS.push('followups|fu_'+i);
const seen=[]; let failures=0;
function ok(name,cond,detail){ console.log((cond?'  ✅ ':'  ❌ ')+name+(cond?'':'  → '+JSON.stringify(detail))); if(!cond)failures++; }
function handler(r){
  const req=r.request(), u=req.url(), m=req.method(), h=req.headers();
  if(u.includes('cdn.jsdelivr.net')) return r.fulfill({status:200,headers:{'content-type':'text/javascript'},body:SDK});
  if(u.startsWith(BASE)) return r.continue();
  if(u.includes('/rest/v1/deleted_records')){
    const kind = m==='HEAD' ? 'COUNT' : (u.includes('order=deletedAt') ? 'HEADROW' : (u.includes('order=id') ? 'PAGE' : 'OTHER'));
    seen.push(kind);
    if(kind==='COUNT') return r.fulfill({status:200,headers:{'content-range':'*/'+IDS.length,'access-control-expose-headers':'content-range','content-type':'application/json'},body:''});
    if(kind==='HEADROW') return r.fulfill({status:200,headers:{'content-type':'application/json'},body:JSON.stringify([{id:IDS[IDS.length-1]}])});
    const q=new URL(u).searchParams; let off=Number(q.get('offset')||0), lim=Number(q.get('limit')||1000); if(h['range']){const rr=h['range'].split('-').map(Number); off=rr[0]; lim=rr[1]-rr[0]+1;}
    const slice=IDS.slice(off,off+lim);
    return r.fulfill({status:200,headers:{'content-type':'application/json','content-range':off+'-'+(off+slice.length-1)+'/'+IDS.length,'access-control-expose-headers':'content-range'},body:JSON.stringify(slice.map(id=>({id})))});
  }
  return r.fulfill({status:200,headers:{'content-type':'application/json'},body:'[]'});   // অন্য সব REST: ফাঁকা
}
const mk=n=>{let rows=[];for(let i=0;i<n;i++){rows.push({id:'p'+i,name:'PATIENT NAME '+i,mobile:'98000'+String(i).padStart(5,'0'),branch:'KNE',address:'Some long address text here for size '.repeat(8),createdAt:new Date(Date.now()-i*1000).toISOString(),updatedAt:new Date(Date.now()-i*1000).toISOString()})}return rows};
async function ctxOf(dir,noIdb){ fs.rmSync(dir,{recursive:true,force:true}); const ctx=await chromium.launchPersistentContext(dir,{headless:true,executablePath:EXE,args:['--no-sandbox']}); if(noIdb) await ctx.addInitScript(()=>{Object.defineProperty(window,'indexedDB',{value:undefined})}); return ctx; }
async function open(ctx){ const page=await ctx.newPage(); await page.route('**/*',handler); const errs=[]; page.on('pageerror',e=>errs.push(String(e.message).slice(0,140))); await page.goto(BASE+'/index.html'); await page.waitForTimeout(1200); return {page,errs}; }
const state=p=>p.evaluate(()=>{let ls=null;try{ls=localStorage.getItem('rk_patients')}catch(e){};return {rows:load('patients').length,lsChars:ls==null?null:ls.length,ramOnly:!!RAM_ONLY.patients,idbOff:__bigOff}});
setTimeout(()=>{console.log('❌ TIMEOUT');process.exit(2)},240000);
(async()=>{
 console.log('— জমা-ঘর (V1287) —');
 let ctx=await ctxOf(path.join(UD,'a')); let {page,errs}=await open(ctx);
 await page.evaluate(`localStorage.setItem('rk_patients',JSON.stringify((${mk.toString()})(4000)))`);
 await page.reload(); await page.waitForTimeout(1500); let s=await state(page);
 ok('A localStorage → IndexedDB সরানো (৪০০০ সারি, localStorage খালি)', s.rows===4000&&s.lsChars===null&&!s.idbOff, s);
 await page.evaluate(`save('patients',(${mk.toString()})(14000),{skipCloud:true})`); await page.waitForTimeout(800); await page.reload(); await page.waitForTimeout(1800); s=await state(page);
 ok('B ১৪০০০ সারি reload-এর পরেও আছে (RAM-only নয়)', s.rows===14000&&!s.ramOnly, s);
 let p2=(await open(ctx)).page; await p2.evaluate(`save('patients',(${mk.toString()})(15000),{skipCloud:true})`); await page.waitForTimeout(1500); s=await state(page);
 ok('D অন্য ট্যাবের লেখা এই ট্যাবে (১৫০০০)', s.rows===15000, s);
 await page.evaluate(()=>{localStorage.setItem('rk_session',JSON.stringify({mobile:'9999999999',name:'TEST MASTER',branch:'All',role:'master'}))});
 const e2=[]; page.on('pageerror',e=>e2.push(String(e.message).slice(0,140))); await page.reload(); await page.waitForTimeout(2500);
 const txt=await page.evaluate(()=>document.body.innerText.slice(0,300).replace(/\s+/g,' '));
 ok('E মাস্টার-সেশনে boot + ড্যাশবোর্ড + page-error ০', /Master Admin/.test(txt)&&e2.length===0, {txt:txt.slice(0,120),e2});
 await ctx.close();
 ctx=await ctxOf(path.join(UD,'c'),true); ({page,errs}=await open(ctx));
 await page.evaluate(`save('patients',(${mk.toString()})(3000),{skipCloud:true})`); await page.reload(); await page.waitForTimeout(1200); s=await state(page);
 ok('C IndexedDB না থাকলে localStorage-পথ (৩০০০)', s.rows===3000&&s.lsChars>0&&s.idbOff, s);
 await ctx.close();
 console.log('— মোছা-চিহ্ন তালিকা (V1292) —');
 ctx=await ctxOf(path.join(UD,'f')); ({page,errs}=await open(ctx));
 const run=async(pre)=>{ seen.length=0; const r=await page.evaluate(async(pre)=>{ try{await initCloudClientOnly()}catch(e){} if(pre==='reset')wlv1WebDeletedSet=new Set(); await wlv1WebSyncDeleted(); return {sz:wlv1WebDeletedSet.size,hasNew:wlv1WebDeletedSet.has('patients|p_new')} },pre); r.PAGE=seen.filter(k=>k==='PAGE').length; return r; };
 let r=await run(); ok('F1 প্রথমবার পুরো তালিকা (PAGE 2, ১৫০০)', r.PAGE===2&&r.sz===1500, r);
 r=await run('reset'); ok('F2 অপরিবর্তিত ⇒ পাতা-টানা বাদ (PAGE 0, ১৫০০)', r.PAGE===0&&r.sz===1500, r);
 IDS.push('patients|p_new'); r=await run(); ok('F3 নতুন চিহ্ন ⇒ আবার পুরো (PAGE 2, নতুনটা আছে)', r.PAGE===2&&r.hasNew, r);
 await page.reload(); await page.waitForTimeout(1500); r=await run(); ok('F4 reload-এর পরে জমা কপি (PAGE 0, নতুনটা আছে)', r.PAGE===0&&r.hasNew, r);
 IDS.pop(); r=await run(); ok('F5 চিহ্ন তোলা ⇒ আবার পুরো (PAGE 2, বাদ)', r.PAGE===2&&!r.hasNew, r);
 await ctx.close(); srv.close();
 console.log(failures?('❌ '+failures+'টা পরীক্ষা ব্যর্থ'):'সব পরীক্ষা পাশ'); process.exit(failures?1:0);
})().catch(e=>{console.error('❌',e);process.exit(1)});
