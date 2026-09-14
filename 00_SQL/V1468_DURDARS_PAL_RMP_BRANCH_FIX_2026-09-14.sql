-- ═══════════════════════════════════════════════════════════════════════════
-- V1468 (১৪.০৯.২০২৬) — DURDARS PAL (pat_9635099181, FLK-12092026-001)-এর
-- কমিশন সংশোধন — TK-অনুমোদিত ("হ্যাঁ, দুটোতেই করুন")।
--
-- আসল কারণ (ডেটা দেখে নিশ্চিত করা, আন্দাজ নয়): TK কোচবিহারের "TK BISWAS"
-- (dv_c1fe1542581143e4b5accc02dcb5222a) কার্ড খোলা অবস্থায় ফালাকাটার এই
-- রোগীর কমিশন বসিয়েছিলেন — রোগী সত্যিই ফালাকাটায় চিকিৎসা করিয়েছেন ও
-- ১২.০৯-এ সেখানেই ₹৫০০ জমা দিয়েছেন (V1466-এর ফলে যাচাই হয়েছে)।
--
-- এখন ঠিক ফালাকাটার "TK BISWAS" (dv_8ad730969a2542f2bef8e26b97c6cbc7)-এ
-- সরানো হচ্ছে, আর হার — TK-র বলা ৬০% আন্দাজে না বসিয়ে সরাসরি
-- fin.rmp_commission_branch_defaults থেকে (rmp_id, branch='Falakata')
-- মিলিয়ে আসল জমা-করা হারটাই বসানো হচ্ছে (V1395-এ TAPOSHI BARMAN-এর
-- সংশোধনেও এই একই পদ্ধতি — নিয়মের বাইরে আন্দাজে কিছু বসানো হয়নি)।
--
-- ⛔ শুধু এই একজন রোগীর কমিশন-সারি ছোঁয়া হচ্ছে (patient_row_id দিয়ে বাঁধা)।
-- ⛔ পেমেন্ট/বিল/রোগীর কোনো সারি ছোঁয়া হয় না।
-- ⛔ পুরনো (ভুল) মান audit trail-এ old_value হিসেবে থেকেই যাবে — মোছা হয় না।
-- ═══════════════════════════════════════════════════════════════════════════

begin;

do $$
declare
  v_patient_row_id text := 'pat_9635099181';
  v_correct_rmp_id text := 'dv_8ad730969a2542f2bef8e26b97c6cbc7';   -- TK BISWAS · Falakata
  v_rmp_name text := 'TK BISWAS';
  v_rmp_mobile text := '+918001080080';
  v_branch text := 'Falakata';
  v_rate numeric;
  v_old jsonb;
  v_comm_id uuid;
begin
  -- আসল জমা-করা ব্রাঞ্চ-হার খুঁজে বার করা (আন্দাজ নয়)।
  select commission_value into v_rate
    from fin.rmp_commission_branch_defaults
   where rmp_id = v_correct_rmp_id and branch = v_branch;
  if v_rate is null then
    raise exception 'Falakata branch default rate not found for this RMP — stopping, nothing changed';
  end if;

  select id, to_jsonb(c) into v_comm_id, v_old
    from fin.rmp_patient_commissions c
   where patient_row_id = v_patient_row_id;
  if v_comm_id is null then
    raise exception 'DURDARS PAL commission row not found — stopping, nothing changed';
  end if;

  update fin.rmp_patient_commissions
     set rmp_id = v_correct_rmp_id,
         rmp_name = v_rmp_name,
         rmp_mobile = v_rmp_mobile,
         commission_mode = 'PERCENT',
         commission_value = v_rate,
         set_by = 'MASTER-TK-CORRECTION',
         updated_at = now()
   where id = v_comm_id;

  insert into fin.rmp_commission_audit(action, entity_id, old_value, new_value, reason, changed_by)
  select 'RMP_BRANCH_CORRECTION', c.id::text, v_old, to_jsonb(c),
         'DURDARS PAL was treated at Falakata (₹500 paid there 12.09.2026); commission had been set against the Cooch Behar TK BISWAS card by mistake — corrected to the Falakata TK BISWAS card at its own branch rate.',
         'MASTER-TK-CORRECTION'
    from fin.rmp_patient_commissions c
   where c.id = v_comm_id;

  raise notice 'Corrected: rmp_id=% rate=%', v_correct_rmp_id, v_rate;
end $$;

commit;

-- ফল যাচাই করার জন্য (এই একই query আবার আলাদা করে চালালে বসা মান দেখা যাবে)।
select id, patient_row_id, patient_name, treatment_branch, rmp_id, rmp_name,
       commission_mode, commission_value, set_by, updated_at
from fin.rmp_patient_commissions
where patient_row_id = 'pat_9635099181';
