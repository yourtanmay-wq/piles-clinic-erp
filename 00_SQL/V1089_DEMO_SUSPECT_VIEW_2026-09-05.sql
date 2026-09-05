-- ═══════════════════════════════════════════════════════════════════════
-- V1089 (০৫.০৯.২০২৬) — TK: *"এই ধরনের আর কোনো ডেমো নাম্বার সম্পূর্ণ
-- প্রজেক্টে আর কোথাও আছে কিনা দেখার উপায় আছে কি?"*
--
-- একটা স্থায়ী তালিকা (view) — পাঁচটা ঘরেই একসঙ্গে খোঁজে:
--   patients · enquiries · followups · doctor_visits · medical
-- যখন খুশি চালালেই হবে:   select * from tk_demo_suspects;
--
-- ⚠️ এটা **সন্দেহের** তালিকা, প্রমাণ নয় — আসল রোগীও উঠতে পারে (যেমন
--    NONOTA MURMU, নম্বরের প্রথম অঙ্ক ভুল টাইপ)। `reason` ঘরটা বলে দেয়
--    কেন উঠেছে, দেখে তবেই সিদ্ধান্ত।
-- ⛔ শুধু পড়া — কোনো সারি বদলায় না, মোছে না। Egress-এ প্রভাব নেই।
-- ═══════════════════════════════════════════════════════════════════════

create or replace view public.tk_demo_suspects as
with src as (
  select 'patients'::text      as tbl, id, name, mobile, coalesce(address,'') as address, branch, "createdAt" from public.patients
  union all
  select 'enquiries'::text,     id, name, mobile, coalesce(address,''),        branch, "createdAt" from public.enquiries
  union all
  select 'followups'::text,     id, name, mobile, coalesce(address,''),        branch, "createdAt" from public.followups
  union all
  select 'doctor_visits'::text, id, name, mobile, coalesce(area,''),           branch, "createdAt" from public.doctor_visits
  union all
  select 'medical'::text,       id, name, mobile, ''::text,                    branch, "createdAt" from public.medical
), f as (
  select s.*, right(regexp_replace(coalesce(s.mobile,''),'\D','','g'),10) as m from src s
)
select tbl, id, name, mobile, branch, address, "createdAt",
  concat_ws(' · ',
    case when m !~ '^[6-9][0-9]{9}$'                                          then 'number shape wrong' end,
    case when m ~ '^(.)\1{9}$'                                                then 'same digit repeated' end,
    case when coalesce(name,'') ~ '(.)\1\1'                                   then 'name has 3 same letters' end,
    case when address ~ '(.)\1\1'                                             then 'address has 3 same letters' end,
    case when coalesce(name,'')||' '||address ~* '(test|demo|asdf|qwer|xxxx)' then 'test/demo word' end,
    case when length(replace(coalesce(name,''),' ','')) <= 3                  then 'name too short' end
  ) as reason
from f
where m !~ '^[6-9][0-9]{9}$'
   or m ~ '^(.)\1{9}$'
   or coalesce(name,'') ~ '(.)\1\1'
   or address ~ '(.)\1\1'
   or coalesce(name,'')||' '||address ~* '(test|demo|asdf|qwer|xxxx)'
   or length(replace(coalesce(name,''),' ','')) <= 3;
