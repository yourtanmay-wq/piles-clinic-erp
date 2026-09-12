-- V1391: শুধু দেখার জন্য (read-only) — PKB (জলপাইগুড়ি)-র সাথে লিংক হওয়া
-- প্রতিটা রোগীর কমিশনের হিসাব বিস্তারিত দেখা, যাতে ₹2,700 সংখ্যাটা আন্দাজ না
-- করে সত্যিকারের হিসাব দিয়ে বোঝানো যায়। কিছুই বদলায় না।

select
  c.patient_row_id, c.patient_name, c.patient_code,
  c.commission_mode, c.commission_value,
  p."bill", p."discount",
  greatest(0, fin.rmp_safe_number(p."bill") - fin.rmp_safe_number(p."discount")) as net_bill,
  fin.rmp_net_paid_between(c.patient_row_id, null::date, null::date) as net_treatment_paid,
  s.earned, s.paid as commission_paid_to_rmp, s.due
from fin.rmp_patient_commissions c
join public.patients p on p.id = c.patient_row_id
cross join lateral fin.rmp_summary(c.patient_row_id) s
where c.rmp_id = 'dv_5b5ab2ff39454839a0a4c3f2fe776c15';
