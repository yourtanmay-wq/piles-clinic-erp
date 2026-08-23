-- ═══════════════════════════════════════════════════════════════════════
--  V592 · App Call গোনা কেন 0 — সত্যিটা দেখার SQL
--  তারিখ: ২৩.০৮.২০২৬
--
--  ⛔⛔ এটা শুধু **দেখার** SQL। একটাও সারি লেখে না, বদলায় না, মোছে না।
--     (কোথাও INSERT · UPDATE · DELETE · ALTER · DROP নেই — মিলিয়ে দেখুন।)
--
--  Supabase → SQL Editor → পুরোটা পেস্ট করে Run → ফলাফলের ছবি পাঠান।
-- ═══════════════════════════════════════════════════════════════════════

-- ── ১ · call_date ঘরটার ডিফল্ট আছে কি না ──────────────────────────────
--    ফাঁকা (NULL) হলে বোঝা যাবে — ফোনের পুরনো কলগুলোর তারিখই বসেনি।
SELECT '১· call_date-এর ডিফল্ট' AS dekha,
       column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'wn' AND table_name = 'call_taps'
ORDER BY ordinal_position;

-- ── ২ · শেষ ৭ দিনে কোন নামে কতগুলো কল জমা আছে ─────────────────────────
--    এখানেই দেখা যাবে Staff/Branch ID-র কল ঢুকছে কি না।
SELECT '২· কোন নামে কত কল' AS dekha,
       staff_code,
       count(*)                                   AS mot_call,
       count(*) FILTER (WHERE call_date IS NULL)  AS tarikh_faka,
       min(call_date)                             AS sobcheye_purono,
       max(call_date)                             AS sobcheye_notun
FROM wn.call_taps
WHERE created_at >= now() - interval '7 days'
GROUP BY staff_code
ORDER BY mot_call DESC;

-- ── ৩ · আজকের কল — দিনে দিনে ──────────────────────────────────────────
SELECT '৩· দিনে দিনে' AS dekha,
       call_date, count(*) AS koyta
FROM wn.call_taps
WHERE created_at >= now() - interval '7 days'
GROUP BY call_date
ORDER BY call_date DESC;

-- ── ৪ · একদম শেষ ১০টা সারি হুবহু ──────────────────────────────────────
SELECT '৪· শেষ ১০টা' AS dekha,
       staff_code, call_date, created_at
FROM wn.call_taps
ORDER BY created_at DESC
LIMIT 10;
