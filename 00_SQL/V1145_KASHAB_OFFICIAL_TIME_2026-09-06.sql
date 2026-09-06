-- ═══════════════════════════════════════════════════════════════════════════
-- V1145 (০৬.০৯.২০২৬) — KASHAB MANDAL: UNEXPECTED → OFFICIAL TIME
-- TK-নির্দেশ: "অফিসিয়াল টাইমে কল এসেছিল, স্টাফ ভুল করে আনএক্সপেক্টেড করেছে"
--
-- কেন তিনটে টেবিলেই: কার্ডের ব্যাজ পড়ে patients.timeType, টাকার হিসেব পড়ে
-- enquiries.timeType, আর ফলো-আপের সারিও নিজের ঘরে ওই লেখাটা রাখে।
--
-- ⛔ timeSource ঘরে হাত পড়ে না — Official Time-এ ওই ঘরটা দেখাই হয় না।
-- ⛔ টাকার কোনো সারি ছোঁয়া হয় না; পাওনা প্রতিবার নতুন করে গোনা হয়, তাই
--    সময়ের ঘর ঠিক হলেই স্টাফের বাড়তি পাওনা নিজে থেকেই কমে যাবে।
-- ═══════════════════════════════════════════════════════════════════════════

-- ─── ১) আগে কী আছে (কিছু বদলায় না) ─────────────────────────────────────
select 'enquiries' as tbl, "id", "name", "branch", "date", "timeType"
  from public.enquiries  where "mobile" = '9832358300'
union all
select 'patients',  "id", "name", "branch", "date", "timeType"
  from public.patients   where "mobile" = '9832358300'
union all
select 'followups', "id", "name", "branch", "date", "timeType"
  from public.followups  where "mobile" = '9832358300';

-- ─── ২) শুধরানো ────────────────────────────────────────────────────────
update public.enquiries set "timeType" = 'Official Time', "updatedAt" = now()::text
 where "mobile" = '9832358300' and coalesce("timeType",'') <> 'Official Time';

update public.patients  set "timeType" = 'Official Time', "updatedAt" = now()::text
 where "mobile" = '9832358300' and coalesce("timeType",'') <> 'Official Time';

update public.followups set "timeType" = 'Official Time', "updatedAt" = now()::text
 where "mobile" = '9832358300' and coalesce("timeType",'') <> 'Official Time';

-- ─── ৩) এখন কী আছে — তিনটেতেই Official Time হওয়ার কথা ──────────────────
select 'enquiries' as tbl, "name", "branch", "timeType"
  from public.enquiries  where "mobile" = '9832358300'
union all
select 'patients',  "name", "branch", "timeType"
  from public.patients   where "mobile" = '9832358300'
union all
select 'followups', "name", "branch", "timeType"
  from public.followups  where "mobile" = '9832358300';

-- ─── ৪) আর কারও নেই তো (আজকের বাকি সারি ঠিক আছে কিনা দেখার জন্য) ───────
select "timeType", count(*) as koyti
  from public.enquiries where "date" >= '2026-09-01' group by "timeType" order by 1;
