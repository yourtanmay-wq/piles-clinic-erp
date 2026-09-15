-- WARNING: SAMPLE ONLY -- Step 2: first 10 patients (Kishanganj, 2017
-- historical Google Sheet). Only after TK reviews and approves this in the
-- app will the rest of the patients be imported.
-- NOTE: createdBy/registeredBy = 'HISTORICAL IMPORT' -- honestly marked;
-- the real original staff name is not known, so no one's name was guessed.
-- NOTE: sex (Male/Female) was not in the sheet -- guessed from name
-- (TK's instruction); if wrong it will show up in these 10 and be fixed.
-- NOTE: stage='Treatment Running' but queue='false' and doctorComplete=
-- 'true' -- so these will NOT show up in today's live Doctor Queue or
-- Follow-up call list.

insert into public.patients (
  id, "patientId", date, "registrationDate", "visitDate",
  name, mobile, branch, age, sex,
  address, disease, bill,
  stage, queue, "doctorComplete",
  "createdBy", "registeredBy", "createdAt", "updatedAt"
) values
(
  'hist_03a36d699ab568ea', 'KNE-06042017-001', '2017-04-06', '2017-04-06', '2017-04-06',
  'MD ARBAJ', '7763926891', 'Kishanganj', '', 'Male',
  'RATUA, PURNIA', 'Hydrocele', '3000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-06T10:00:00.000Z', '2017-04-06T10:00:00.000Z'
),
(
  'hist_63a05c723ce4f2aa', 'KNE-12042017-001', '2017-04-12', '2017-04-12', '2017-04-12',
  'ALADIN ANSARI', '9006717887', 'Kishanganj', '39', 'Male',
  'KHAGRA, KHAGRA, KISHANGANJ, KISHANGANJ', 'Piles', '5500',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:00:00.000Z', '2017-04-12T10:00:00.000Z'
),
(
  'hist_76d9ea5e692703a1', 'KNE-17042017-001', '2017-04-17', '2017-04-17', '2017-04-17',
  'MAJRUL ISLAM', '9732900921', 'Kishanganj', '25', 'Male',
  'FULBARI, CHAKULIYA, CHAKULIYA, UTTAR DINAJPUR', 'Hydrocele', '4000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:00:00.000Z', '2017-04-17T10:00:00.000Z'
),
(
  'hist_02472e099b6fe404', 'KNE-18042017-001', '2017-04-18', '2017-04-18', '2017-04-18',
  'RAHMAT ALAM', '9934252301', 'Kishanganj', '26', 'Male',
  'SHIRSI, RATUA, RATUA, PURNIA', 'Piles', '5500',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:00:00.000Z', '2017-04-18T10:00:00.000Z'
),
(
  'hist_228bd6c77b79636b', 'KNE-18042017-002', '2017-04-18', '2017-04-18', '2017-04-18',
  'MD IQBAL', '9608699408', 'Kishanganj', '', 'Male',
  'PANE, PANE, KISHANGANJ, KISHANGANJ', 'Hydrocele', '4000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:00:00.000Z', '2017-04-18T10:00:00.000Z'
),
(
  'hist_883f795ee7ea5880', 'KNE-26042017-001', '2017-04-26', '2017-04-26', '2017-04-26',
  'TAUSIF REJA', '7859431634', 'Kishanganj', '17', 'Male',
  'UTTAR DINAJPUR', 'Piles', '5500',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:00:00.000Z', '2017-04-26T10:00:00.000Z'
),
(
  'hist_bf0f62ee77eb740f', 'KNE-30042017-001', '2017-04-30', '2017-04-30', '2017-04-30',
  'SURESH KUMAR', '9769742799', 'Kishanganj', '26', 'Male',
  'DUMURIA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Hydrocele', '4000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-30T10:00:00.000Z', '2017-04-30T10:00:00.000Z'
),
(
  'hist_9a63e52bd8aedcd9', 'KNE-10052017-001', '2017-05-10', '2017-05-10', '2017-05-10',
  'MUNNA ALAM', '9661842124', 'Kishanganj', '40', 'Male',
  'KISHANGANJ, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '3500',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:00:00.000Z', '2017-05-10T10:00:00.000Z'
),
(
  'hist_795f6e9ee754f992', 'KNE-13052017-001', '2017-05-13', '2017-05-13', '2017-05-13',
  'TAPAN KUMAR DAS', '9775961893', 'Kishanganj', '42', 'Male',
  'RUIDASHA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '4000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:00:00.000Z', '2017-05-13T10:00:00.000Z'
),
(
  'hist_db6319096da7e065', 'KNE-13052017-002', '2017-05-13', '2017-05-13', '2017-05-13',
  'AHMAD HOSSAIN', '9470853827', 'Kishanganj', '55', 'Male',
  'TAUSA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Hydrocele', '4200',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:00:00.000Z', '2017-05-13T10:00:00.000Z'
);

insert into public.payments (
  id, "payType", "payLabel", "paymentLabel", "patientId", mobile, branch, name,
  date, amount, mode, remarks,
  "createdBy", "receivedBy", "createdAt", "updatedAt"
) values
(
  'hist_pay_92dc195d781bc204', 'treatment', 'Advance', 'Advance', 'hist_03a36d699ab568ea', '7763926891', 'Kishanganj', 'MD ARBAJ',
  '2017-04-06', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-06T10:15:00.000Z', '2017-04-06T10:15:00.000Z'
),
(
  'hist_pay_fb929a3e519c5af1', 'treatment', '2nd Payment', '2nd Payment', 'hist_03a36d699ab568ea', '7763926891', 'Kishanganj', 'MD ARBAJ',
  '2017-04-06', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-06T10:16:00.000Z', '2017-04-06T10:16:00.000Z'
),
(
  'hist_pay_ebfaac371cea5bbb', 'treatment', '3rd Payment', '3rd Payment', 'hist_03a36d699ab568ea', '7763926891', 'Kishanganj', 'MD ARBAJ',
  '2017-04-06', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-06T10:17:00.000Z', '2017-04-06T10:17:00.000Z'
),
(
  'hist_pay_3f0730d4c14b4893', 'treatment', 'Advance', 'Advance', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:15:00.000Z', '2017-04-12T10:15:00.000Z'
),
(
  'hist_pay_6cd154a77527b5a6', 'treatment', '2nd Payment', '2nd Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '2000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:16:00.000Z', '2017-04-12T10:16:00.000Z'
),
(
  'hist_pay_f205b437c4a0cbdc', 'treatment', '3rd Payment', '3rd Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:17:00.000Z', '2017-04-12T10:17:00.000Z'
),
(
  'hist_pay_167389b5dfd9aac4', 'treatment', '4th Payment', '4th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:18:00.000Z', '2017-04-12T10:18:00.000Z'
),
(
  'hist_pay_65a66af870cadd68', 'treatment', '5th Payment', '5th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:19:00.000Z', '2017-04-12T10:19:00.000Z'
),
(
  'hist_pay_7bdc466dc638cbd2', 'treatment', '6th Payment', '6th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:20:00.000Z', '2017-04-12T10:20:00.000Z'
),
(
  'hist_pay_9f082b7e25d6c778', 'treatment', '7th Payment', '7th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '300', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:21:00.000Z', '2017-04-12T10:21:00.000Z'
),
(
  'hist_pay_4c72c57c3eff860d', 'treatment', '8th Payment', '8th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:22:00.000Z', '2017-04-12T10:22:00.000Z'
),
(
  'hist_pay_6a57cd3d61e6cdf7', 'treatment', '9th Payment', '9th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:23:00.000Z', '2017-04-12T10:23:00.000Z'
),
(
  'hist_pay_bb4ec3fd85dbd157', 'treatment', '10th Payment', '10th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '60', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:24:00.000Z', '2017-04-12T10:24:00.000Z'
),
(
  'hist_pay_d77a04059f343e69', 'treatment', '11th Payment', '11th Payment', 'hist_63a05c723ce4f2aa', '9006717887', 'Kishanganj', 'ALADIN ANSARI',
  '2017-04-12', '50', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-12T10:25:00.000Z', '2017-04-12T10:25:00.000Z'
),
(
  'hist_pay_62732510b0c89748', 'treatment', 'Advance', 'Advance', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:15:00.000Z', '2017-04-17T10:15:00.000Z'
),
(
  'hist_pay_88304589a3f46e45', 'treatment', '2nd Payment', '2nd Payment', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:16:00.000Z', '2017-04-17T10:16:00.000Z'
),
(
  'hist_pay_d266fbd494088490', 'treatment', '3rd Payment', '3rd Payment', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:17:00.000Z', '2017-04-17T10:17:00.000Z'
),
(
  'hist_pay_14a26113659a9062', 'treatment', '4th Payment', '4th Payment', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '300', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:18:00.000Z', '2017-04-17T10:18:00.000Z'
),
(
  'hist_pay_74e4a55d3b4edf76', 'treatment', '5th Payment', '5th Payment', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:19:00.000Z', '2017-04-17T10:19:00.000Z'
),
(
  'hist_pay_11bc05046b59408e', 'treatment', '6th Payment', '6th Payment', 'hist_76d9ea5e692703a1', '9732900921', 'Kishanganj', 'MAJRUL ISLAM',
  '2017-04-17', '700', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-17T10:20:00.000Z', '2017-04-17T10:20:00.000Z'
),
(
  'hist_pay_0b43f23b42344bb6', 'treatment', 'Advance', 'Advance', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:15:00.000Z', '2017-04-18T10:15:00.000Z'
),
(
  'hist_pay_4b99afde1d9024e0', 'treatment', '2nd Payment', '2nd Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:16:00.000Z', '2017-04-18T10:16:00.000Z'
),
(
  'hist_pay_6b9e7c48bf2ca280', 'treatment', '3rd Payment', '3rd Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:17:00.000Z', '2017-04-18T10:17:00.000Z'
),
(
  'hist_pay_d5d39a877ed599ac', 'treatment', '4th Payment', '4th Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:18:00.000Z', '2017-04-18T10:18:00.000Z'
),
(
  'hist_pay_0ca7be6c4d6ee132', 'treatment', '5th Payment', '5th Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:19:00.000Z', '2017-04-18T10:19:00.000Z'
),
(
  'hist_pay_d0afb84bb7130dca', 'treatment', '6th Payment', '6th Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:20:00.000Z', '2017-04-18T10:20:00.000Z'
),
(
  'hist_pay_35b38e69be1c0c65', 'treatment', '7th Payment', '7th Payment', 'hist_02472e099b6fe404', '9934252301', 'Kishanganj', 'RAHMAT ALAM',
  '2017-04-18', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:21:00.000Z', '2017-04-18T10:21:00.000Z'
),
(
  'hist_pay_d91e03fa3ba24f0a', 'treatment', 'Advance', 'Advance', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:15:00.000Z', '2017-04-18T10:15:00.000Z'
),
(
  'hist_pay_874bbe1c9177c25b', 'treatment', '2nd Payment', '2nd Payment', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:16:00.000Z', '2017-04-18T10:16:00.000Z'
),
(
  'hist_pay_a4ae1131ba1bb0a0', 'treatment', '3rd Payment', '3rd Payment', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:17:00.000Z', '2017-04-18T10:17:00.000Z'
),
(
  'hist_pay_31630ae6427cae70', 'treatment', '4th Payment', '4th Payment', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:18:00.000Z', '2017-04-18T10:18:00.000Z'
),
(
  'hist_pay_85fedcaa5264bfb3', 'treatment', '5th Payment', '5th Payment', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:19:00.000Z', '2017-04-18T10:19:00.000Z'
),
(
  'hist_pay_13696408b469d9e0', 'treatment', '6th Payment', '6th Payment', 'hist_228bd6c77b79636b', '9608699408', 'Kishanganj', 'MD IQBAL',
  '2017-04-18', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-18T10:20:00.000Z', '2017-04-18T10:20:00.000Z'
),
(
  'hist_pay_6e2aff280df69e46', 'treatment', 'Advance', 'Advance', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:15:00.000Z', '2017-04-26T10:15:00.000Z'
),
(
  'hist_pay_ed2356504ecb9a32', 'treatment', '2nd Payment', '2nd Payment', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '1800', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:16:00.000Z', '2017-04-26T10:16:00.000Z'
),
(
  'hist_pay_a07936e6ef00e358', 'treatment', '3rd Payment', '3rd Payment', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:17:00.000Z', '2017-04-26T10:17:00.000Z'
),
(
  'hist_pay_07f779a04fdb5f30', 'treatment', '4th Payment', '4th Payment', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:18:00.000Z', '2017-04-26T10:18:00.000Z'
),
(
  'hist_pay_b3d4e0707e5ee110', 'treatment', '5th Payment', '5th Payment', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '300', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:19:00.000Z', '2017-04-26T10:19:00.000Z'
),
(
  'hist_pay_8458671264e50b83', 'treatment', '6th Payment', '6th Payment', 'hist_883f795ee7ea5880', '7859431634', 'Kishanganj', 'TAUSIF REJA',
  '2017-04-26', '1300', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-26T10:20:00.000Z', '2017-04-26T10:20:00.000Z'
),
(
  'hist_pay_8ac07bddd825d273', 'treatment', 'Advance', 'Advance', 'hist_bf0f62ee77eb740f', '9769742799', 'Kishanganj', 'SURESH KUMAR',
  '2017-04-30', '2000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-30T10:15:00.000Z', '2017-04-30T10:15:00.000Z'
),
(
  'hist_pay_f073f1da386b20ca', 'treatment', '2nd Payment', '2nd Payment', 'hist_bf0f62ee77eb740f', '9769742799', 'Kishanganj', 'SURESH KUMAR',
  '2017-04-30', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-30T10:16:00.000Z', '2017-04-30T10:16:00.000Z'
),
(
  'hist_pay_11d1ede81360f8e7', 'treatment', '3rd Payment', '3rd Payment', 'hist_bf0f62ee77eb740f', '9769742799', 'Kishanganj', 'SURESH KUMAR',
  '2017-04-30', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-30T10:17:00.000Z', '2017-04-30T10:17:00.000Z'
),
(
  'hist_pay_3002751f28c61f56', 'treatment', '4th Payment', '4th Payment', 'hist_bf0f62ee77eb740f', '9769742799', 'Kishanganj', 'SURESH KUMAR',
  '2017-04-30', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-04-30T10:18:00.000Z', '2017-04-30T10:18:00.000Z'
),
(
  'hist_pay_04a9e0eca9afff5e', 'treatment', 'Advance', 'Advance', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '1500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:15:00.000Z', '2017-05-10T10:15:00.000Z'
),
(
  'hist_pay_b8f8587037cdc4e0', 'treatment', '2nd Payment', '2nd Payment', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:16:00.000Z', '2017-05-10T10:16:00.000Z'
),
(
  'hist_pay_5524ad0e8f9b7623', 'treatment', '3rd Payment', '3rd Payment', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:17:00.000Z', '2017-05-10T10:17:00.000Z'
),
(
  'hist_pay_d4705c8674451b66', 'treatment', '4th Payment', '4th Payment', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:18:00.000Z', '2017-05-10T10:18:00.000Z'
),
(
  'hist_pay_be43c4e89ec5d913', 'treatment', '5th Payment', '5th Payment', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:19:00.000Z', '2017-05-10T10:19:00.000Z'
),
(
  'hist_pay_73e5394c4a54fd45', 'treatment', '6th Payment', '6th Payment', 'hist_9a63e52bd8aedcd9', '9661842124', 'Kishanganj', 'MUNNA ALAM',
  '2017-05-10', '100', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-10T10:20:00.000Z', '2017-05-10T10:20:00.000Z'
),
(
  'hist_pay_02efdff438441f5e', 'treatment', 'Advance', 'Advance', 'hist_795f6e9ee754f992', '9775961893', 'Kishanganj', 'TAPAN KUMAR DAS',
  '2017-05-13', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:15:00.000Z', '2017-05-13T10:15:00.000Z'
),
(
  'hist_pay_44a402bd93ae111b', 'treatment', '2nd Payment', '2nd Payment', 'hist_795f6e9ee754f992', '9775961893', 'Kishanganj', 'TAPAN KUMAR DAS',
  '2017-05-13', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:16:00.000Z', '2017-05-13T10:16:00.000Z'
),
(
  'hist_pay_2542e07fd500f345', 'treatment', 'Advance', 'Advance', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:15:00.000Z', '2017-05-13T10:15:00.000Z'
),
(
  'hist_pay_86ccc96c61de4d0d', 'treatment', '2nd Payment', '2nd Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:16:00.000Z', '2017-05-13T10:16:00.000Z'
),
(
  'hist_pay_d848932cd9a03b81', 'treatment', '3rd Payment', '3rd Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '1000', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:17:00.000Z', '2017-05-13T10:17:00.000Z'
),
(
  'hist_pay_369eb84c078eb3a9', 'treatment', '4th Payment', '4th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:18:00.000Z', '2017-05-13T10:18:00.000Z'
),
(
  'hist_pay_b210b5b11cfc7744', 'treatment', '5th Payment', '5th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '500', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:19:00.000Z', '2017-05-13T10:19:00.000Z'
),
(
  'hist_pay_a3b2cbbaddcd42d6', 'treatment', '6th Payment', '6th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:20:00.000Z', '2017-05-13T10:20:00.000Z'
),
(
  'hist_pay_c9cd1d8cc02532a7', 'treatment', '7th Payment', '7th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '400', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:21:00.000Z', '2017-05-13T10:21:00.000Z'
),
(
  'hist_pay_57ba0dc667ae6036', 'treatment', '8th Payment', '8th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '50', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:22:00.000Z', '2017-05-13T10:22:00.000Z'
),
(
  'hist_pay_5a10e9e32c197024', 'treatment', '9th Payment', '9th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '150', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:23:00.000Z', '2017-05-13T10:23:00.000Z'
),
(
  'hist_pay_0d8d8335d9749cf7', 'treatment', '10th Payment', '10th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:24:00.000Z', '2017-05-13T10:24:00.000Z'
),
(
  'hist_pay_37648c9dd6c07d50', 'treatment', '11th Payment', '11th Payment', 'hist_db6319096da7e065', '9470853827', 'Kishanganj', 'AHMAD HOSSAIN',
  '2017-05-13', '200', 'CASH', 'Historical import (Google Sheet, pre-app)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2017-05-13T10:25:00.000Z', '2017-05-13T10:25:00.000Z'
);
