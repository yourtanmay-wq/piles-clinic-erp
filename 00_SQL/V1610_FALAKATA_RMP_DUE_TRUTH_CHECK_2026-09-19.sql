-- পড়া-মাত্র যাচাই — কিছুই বদলায় না।
-- TK-র প্রশ্ন (১৯.০৯.২০২৬): Falakata-র RMP Due List মাস্টারের ফোনে আর
-- ডাক্তারের ফোনে দুই রকম দেখাচ্ছিল (TK BISWAS-এর Paid/Due আলাদা, PRANAB
-- BIRPARA ডাক্তারের ফোনে নেই)। এটা সার্ভারের আসল হিসাব (fin.rmp_branch_due)
-- — এখানে যা আসবে সেটাই সঠিক, যেটা এর থেকে আলাদা দেখাচ্ছে সেই ফোনটাই ভুল/পুরনো।

select
  rmp_name  as "RMP-এর নাম",
  rmp_mobile as "মোবাইল",
  patient_count as "কতজন রোগী",
  earned as "মোট কমিশন",
  paid   as "পাওয়া হয়েছে",
  due    as "এখনো বাকি"
from fin.rmp_branch_due('Falakata')
order by due desc;
