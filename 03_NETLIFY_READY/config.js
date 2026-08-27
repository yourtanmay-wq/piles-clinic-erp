window.RK_CONFIG = {
  clinicName: 'MAA AYURVED PILES CLINIC',
  publicName: 'PILES CLINIC',
  defaultBranch: 'Jalpaiguri',
  supabaseUrl: 'https://bcyeogjqtupbdyciqfmz.supabase.co',
  supabaseKey: 'sb_publishable_k_170-JGrdxmZ7rBrjCyTA_-ElK2XdZ',
  branches: [
    {name:'Kishanganj', code:'KNE', mobile:'8676002200', address:'Caltex Chowk, Modi Gola, Kishanganj', map:'https://www.google.com/maps/search/?api=1&query=Biswas+Piles+Clinic+Kishanganj', facebook:''},
    {name:'Jalpaiguri', code:'JPE', mobile:'8436002200', address:'Raikatpara, Opp. Sports Complex, Jalpaiguri', map:'https://maps.app.goo.gl/fRjsuxhoXq9efBtv9?g_st=ac', facebook:''},
    {name:'Cooch Behar', code:'COB', mobile:'8514002200',   /* 🔒 খাতার সারি B33: আগে ভুল করে ফালাকাটার নম্বর ছিল */ address:'Opp. Mini Bus Stand, Sengupta Complex 2nd Floor, Cooch Behar', map:'https://www.google.com/maps/search/?api=1&query=Maa+Ayurved+Piles+Clinic+Cooch+Behar', facebook:''},
    {name:'Falakata', code:'FLK', mobile:'8514001100', address:'BDO Office Road, near Hotel Nandonik, Falakata', map:'https://www.google.com/maps/search/?api=1&query=Maa+Ayurved+Piles+Clinic+Falakata', facebook:''},
    {name:'Birpara', code:'BIR', mobile:'8538002200', address:'MG Road, near Axis Bank, Birpara', map:'https://www.google.com/maps/search/?api=1&query=Maa+Ayurved+Piles+Clinic+Birpara', facebook:''}
  ],
  users: {
    master: [{mobile:'8001080080', name:'TK BISWAS', branch:'All'}],
    staff: [
      {mobile:'9883605917', name:'KNE-LAXMI', branch:'Kishanganj'},
      {mobile:'8676002200', name:'KNE-BRANCH', branch:'Kishanganj'},
      /* V453 (20.08.2026, TK-approved): KNE-KISHAN5 (6207841890) কাজ ছেড়ে দিয়েছে,
         আর এখানে নেই — পুরনো নম্বর দিয়ে আর লগইন হবে না। পুরনো রেকর্ড অক্ষত। */
      /* 🔴 V734 (27.08.2026, TK-এর সরাসরি নির্দেশ): কিশানগঞ্জের KNE-KISHAN6
         ঘরের স্টাফ কাজ থেকে বাদ। TK-এর নির্দেশে তাঁর নাম ও মোবাইল নম্বর
         কোথাও রাখা হয়নি — কমেন্টেও নয়। ⇒ ওই নম্বরে আর লগইন হবে না।
         V453-এ KNE-KISHAN5-এর হুবহু একই নিয়ম।
         ⛔ পুরোনো রেকর্ড কিছুই মোছা হয়নি — শুধু ঢোকার পথ বন্ধ। */
      /* 🟢 V735 (27.08.2026, TK-এর সরাসরি নির্দেশ): কিশানগঞ্জের নতুন স্টাফ। */
      {mobile:'7321960416', name:'KNE-KISHAN8', branch:'Kishanganj'},
      {mobile:'9647840067', name:'JPE-CRP', branch:'Jalpaiguri'},
      {mobile:'8101397763', name:'JPE-JALPAI-13', branch:'Jalpaiguri'},
      {mobile:'8167096595', name:'JPE-RUPAM', branch:'Jalpaiguri'},
      {mobile:'8436002200', name:'JPE-BRANCH', branch:'Jalpaiguri'},
      {mobile:'7679751521', name:'COB-UTTAMA', branch:'Cooch Behar'},
      {mobile:'7501256248', name:'COB-4', branch:'Cooch Behar'},
      {mobile:'8514002200', name:'COB-BRANCH', branch:'Cooch Behar'},
      {mobile:'9883623823', name:'FLK-1', branch:'Falakata'},
      {mobile:'8514001100', name:'FLK-BRANCH', branch:'Falakata'},
      {mobile:'8538002200', name:'BIR-BRANCH', branch:'Birpara'}
      // 🔴 V404 (16.08.2026, TK-নির্দেশ "ওই নাম্বারের কোনো অংশ যেন না থাকে"):
      //    FALA-15 (SWAPNA ADHIKARI) কাজ ছেড়ে দিয়েছেন — লগইন তালিকা থেকে বাদ।
      //    ⛔ পুরনো কাজের রেকর্ড কিছুই ছোঁয়া হয়নি (TK-সিদ্ধান্ত: "রেকর্ড অটুট থাক")।
    ],
    doctor: [
      {mobile:'7980993652', name:'Dr. K.H MANDAL', branch:'Cooch Behar'},
      {mobile:'8001800148', name:'Dr. JAY BANIK', branch:'Jalpaiguri'},
      {mobile:'9046366596', name:'AMIT GOLDAR', branch:'Kishanganj'},
      // 🔴 V450 (18.08.2026, TK-নির্দেশ): PK ROY (6297625447) ভুল করে তোলা
      //    হয়েছিল — লগইন তালিকা থেকে বাদ, যাতে এই নম্বর দিয়ে কিশানগঞ্জে
      //    আর লগইন না হয়। পুরনো কাজের রেকর্ড কিছুই ছোঁয়া হয়নি (FALA-15-এর
      //    ঠিক একই নিয়ম)।
      // 🔵 V308 (১০.০৮): এই ৪ অংশীদারও ডাক্তার (TK-নির্দেশ) — পুরো ডাক্তার-অ্যাক্সেস + নিজের Share Ledger।
      // Saikat-এর ডাক্তার-ব্রাঞ্চ Falakata (TK)। ভাগ দুই ব্রাঞ্চেই fin.partners-এ আলাদা থাকে।
      {mobile:'7479173399', name:'J.H MANDAL', branch:'Cooch Behar'},
      {mobile:'9002610352', name:'GOKUL', branch:'Cooch Behar'},
      {mobile:'7810907954', name:'Dr. SAIKAT ROY', branch:'Falakata'},
      {mobile:'9242009205', name:'Dr. PRANAB BISWAS', branch:'Birpara'}
    ],
    field: [{mobile:'9002003540', name:'Field Officer', branch:'All'}]
    // 🔵 V308 (১০.০৮): আগের "partner" role বাদ — ঐ ৪ জন এখন উপরের doctor তালিকায় (TK-নির্দেশ:
    // সব অংশীদারই ডাক্তার)। ভাগ দেখা হয় মোবাইল-ম্যাচে (My Share Ledger), role দিয়ে নয়।
  },
  passwords: {master:'admin123', staff:'staff123', doctor:'doctor123', field:'field123'},
  productionCloudAuthoritative: false
};
