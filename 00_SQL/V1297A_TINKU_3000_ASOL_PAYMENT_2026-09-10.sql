-- (ক) ব্রাঞ্চ বললে "₹3,000 সত্যিই নেওয়া হয়েছিল ০৭.০৮": Bill-Edited চিহ্ন-সারিটা আসল treatment পেমেন্টে বদলানো
--     (একই সারি, একই id — টাকা ₹3,000 · CASH · ০৭.০৮.২০২৬ · JPE-RUPAM থাকে; শুধু ধরন/লেবেল বদলায়; নথি remarks-এ থাকে)
begin;
update payments
set "payType" = 'treatment',
    "payLabel" = '2nd Payment',
    "paymentLabel" = '2nd Payment',
    remarks = coalesce(remarks,'') || ' | V1297: bill_edit চিহ্ন → আসল পেমেন্ট (ব্রাঞ্চ নিশ্চিত করেছে) ' || to_char(now() at time zone 'Asia/Kolkata','DD.MM.YYYY HH24:MI'),
    "updatedAt" = to_char(now() at time zone 'utc','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
where id = 'pay_4a81fb54f47e4ea893875ab2d4ba95e2' and "payType" = 'bill_edit' and amount = '3000';
select id, "payType", amount, "payLabel", date from payments where id = 'pay_4a81fb54f47e4ea893875ab2d4ba95e2';
commit;
