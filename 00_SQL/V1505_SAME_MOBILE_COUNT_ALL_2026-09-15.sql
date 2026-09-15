-- শুধু SELECT — কিছু মোছে না।
-- একই মোবাইল নম্বরে কতজন রোগী আছে (পেমেন্ট থাকুক বা না থাকুক), সব ব্রাঞ্চ মিলিয়ে।
with dup_mobile as (
  select right(regexp_replace(coalesce(mobile,''),'\D','','g'),10) as m
  from public.patients
  where length(right(regexp_replace(coalesce(mobile,''),'\D','','g'),10)) = 10
  group by m having count(*) > 1
)
select right(regexp_replace(coalesce(p.mobile,''),'\D','','g'),10) as mobile,
       count(*) as total_patients,
       string_agg(p.branch || ': ' || p.name || ' (' || p."patientId" || ')', '  |  '
                  order by p."registrationDate") as patients
  from public.patients p
  join dup_mobile dm
    on right(regexp_replace(coalesce(p.mobile,''),'\D','','g'),10) = dm.m
 group by mobile
 order by total_patients desc, mobile;
