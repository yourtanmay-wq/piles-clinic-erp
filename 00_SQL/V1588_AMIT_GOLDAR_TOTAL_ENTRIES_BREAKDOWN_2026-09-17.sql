-- 🔍 READ-ONLY — শুধু দেখার জন্য, কোনো টাকা/ডেটা বদলাবে না
-- TK-প্রশ্ন (১৭.০৯.২০২৬): AMIT GOLDAR-এর Referred 56 অথচ Total Entries 82 কেন?
-- এই কোয়েরি অ্যাপ যেভাবে গোনে ঠিক সেই একই তিনটে সংখ্যা আলাদা করে বের করে দেখাবে —
-- Call + Referred Patient + Referral Income (রোগী-ধরে একসাথে করা) = Total Entries
with rmp as (
  select id, "name", "mobile", "callHistory"
  from public.doctor_visits
  where right(regexp_replace(coalesce("mobile",''),'\D','','g'),10) = '9046366596'
  limit 1
), calls as (
  select jsonb_array_length(coalesce(r."callHistory", '[]'::jsonb)) as call_entries
  from rmp r
), referred as (
  select count(*) as referred_patient_rows
  from public.patients p, rmp r
  where lower(trim(coalesce(p."refBy",'')))     = lower(trim(r."name"))
     or lower(trim(coalesce(p."refDoctor",'')))  = lower(trim(r."name"))
     or right(regexp_replace(coalesce(p."refDoctorMobile",''),'\D','','g'),10)
        = right(regexp_replace(coalesce(r."mobile",''),'\D','','g'),10)
), income as (
  select count(*) as referral_income_rows
  from rmp r
  cross join lateral fin.rmp_patient_breakdown(r.id) b
  where b.earned > 0.5 or b.paid > 0.5 or b.due > 0.5
)
select
  (select "name" from rmp)  as rmp_name,
  (select call_entries from calls)              as "1_call_entries",
  (select referred_patient_rows from referred)  as "2_referred_patient_rows",
  (select referral_income_rows from income)     as "3_referral_income_rows",
  (select call_entries from calls) + (select referred_patient_rows from referred) + (select referral_income_rows from income) as "4_total_1_plus_2_plus_3";
