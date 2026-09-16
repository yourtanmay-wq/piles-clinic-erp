-- ২০২৫ শিট ধাপ ৪ -- ব্যাচ 1/2 (রোগী 1-54, মোট 54)
-- ১১৬ জন বিল-করা রোগীর মধ্যে: ৩ জন লাইভ প্রোফাইলে মার্জ (আলাদা SQL-এ
-- হয়ে গেছে), ৫ জন বাদ (শিটেই DATE/PAYMENT ঘর উল্টে/দ্বিতীয়বার বসা --
-- BHAVESH KR DAS, WAJIFA KHATUN, BIKASH NATH YOGI, TAPAN KUMAR DAS,
-- SAMSURUDDIN), ১ জন বাদ (MD AZAM -- DATE ঘরে শুধু একটা টিক-চিহ্ন)।
-- বাকি ১০৭ জন এই ব্যাচগুলোয়। sex নাম দেখে আন্দাজ। REF BY এই শিটে
-- রেফারেল-উৎসের ক্যাটেগরি (LOCAL/ONLINE ইত্যাদি), কোনো RMP-র নাম নয়।

insert into public.patients (
  id, "patientId", date, "registrationDate", "visitDate",
  name, mobile, "altMobile", branch, age, sex,
  address, disease, bill,
  stage, queue, "doctorComplete",
  "createdBy", "registeredBy", "createdAt", "updatedAt"
) values
(
  'hist2025_47026da8fe5be4a7', 'KNE-06012025-001', '2025-01-06', '2025-01-06', '2025-01-06',
  'PARESH MANDAL', '9126495421', '', 'Kishanganj', '51', 'Male',
  'KANKI, MAJLISHPUR, CHAKULIYA, U.D', 'Fistula', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-06T10:00:00.000Z', '2025-01-06T10:00:00.000Z'
),
(
  'hist2025_0dcd595e07cc56eb', 'KNE-06012025-002', '2025-01-06', '2025-01-06', '2025-01-06',
  'NASIMA KHATUN', '9064332124', '', 'Kishanganj', '30', 'Female',
  'CHAPRA, CHAPRA, CHAKULIYA, U.D', 'Piles', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-06T10:00:00.000Z', '2025-01-06T10:00:00.000Z'
),
(
  'hist2025_c879971b33c9b390', 'KNE-17012025-001', '2025-01-17', '2025-01-17', '2025-01-17',
  'SABIR ALAM', '9006712520', '', 'Kishanganj', '30', 'Male',
  'DIGAL BANK, PADAMPUR, GANDHARB, KNE', 'Fistula', '55000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-17T10:00:00.000Z', '2025-01-17T10:00:00.000Z'
),
(
  'hist2025_e186a9d641acfbed', 'KNE-31012025-001', '2025-01-31', '2025-01-31', '2025-01-31',
  'BIPUL BARMAN', '8388008655', '', 'Kishanganj', '24', 'Male',
  'SAIDPUR, MAHESH PUR, KARAM, U.D', 'Fistula', '100000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-31T10:00:00.000Z', '2025-01-31T10:00:00.000Z'
),
(
  'hist2025_ff45a89e445c3975', 'KNE-03022025-001', '2025-02-03', '2025-02-03', '2025-02-03',
  'MD TEJIN UDDIN', '8942967494', '', 'Kishanganj', '65', 'Male',
  'BANGAO, GOYAGAO, GOYALPOKHAR, U.D', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-03T10:00:00.000Z', '2025-02-03T10:00:00.000Z'
),
(
  'hist2025_88d8f382014de132', 'KNE-05022025-001', '2025-02-05', '2025-02-05', '2025-02-05',
  'KAMRUL HOSSIN', '9347502740', '', 'Kishanganj', '22', 'Male',
  'MEMGAO, KNE, KNE, KNE', 'Gupt Rog', '19000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:00:00.000Z', '2025-02-05T10:00:00.000Z'
),
(
  'hist2025_7b51be03bcc5e104', 'KNE-05022025-002', '2025-02-05', '2025-02-05', '2025-02-05',
  'ZIDARUL HOWAK', '8825174821', '', 'Kishanganj', '26', 'Male',
  'MEMGAO, KNE, KNE, KNE', 'Gupt Rog', '18000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:00:00.000Z', '2025-02-05T10:00:00.000Z'
),
(
  'hist2025_1e780218698b7151', 'KNE-12022025-001', '2025-02-12', '2025-02-12', '2025-02-12',
  'LALIF PASWAN', '8676073770', '', 'Kishanganj', '26', 'Male',
  'RAMPUR, CHAPOR, CHAKULIYA, U.D', 'Piles', '15000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-12T10:00:00.000Z', '2025-02-12T10:00:00.000Z'
),
(
  'hist2025_0e0faa9efa689c24', 'KNE-19022025-001', '2025-02-19', '2025-02-19', '2025-02-19',
  'RESHMA KHATOON', '9593316409', '', 'Kishanganj', '45', 'Female',
  'DIGALGAO, BETNA, KNE, KNE', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:00:00.000Z', '2025-02-19T10:00:00.000Z'
),
(
  'hist2025_3effe16fc3428e38', 'KNE-19022025-002', '2025-02-19', '2025-02-19', '2025-02-19',
  'MD AHRAR ALAM', '9609023646', '', 'Kishanganj', '26', 'Male',
  'MAKHAN POKHAR, SAHAPUR, CHAKULIYA, U.D', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:00:00.000Z', '2025-02-19T10:00:00.000Z'
),
(
  'hist2025_cf1306f9b745efa4', 'KNE-20022025-001', '2025-02-20', '2025-02-20', '2025-02-20',
  'MD NABI', '8757937444', '', 'Kishanganj', '33', 'Male',
  'JELIVITA, DIGAL BANK, DIGAL BANK, KNE', 'Fistula', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-20T10:00:00.000Z', '2025-02-20T10:00:00.000Z'
),
(
  'hist2025_0197614e2aa43dfc', 'KNE-28022025-001', '2025-02-28', '2025-02-28', '2025-02-28',
  'DEVLAL SINGH', '9733237054', '', 'Kishanganj', '50', 'Male',
  'BANAGHECHH, KONIYAVITA, GOYALPOKHAR, U.D', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-28T10:00:00.000Z', '2025-02-28T10:00:00.000Z'
),
(
  'hist2025_9f9f6396797e0341', 'KNE-03032025-001', '2025-03-03', '2025-03-03', '2025-03-03',
  'NUR SELIM', '6296133324', '', 'Kishanganj', '25', 'Male',
  'DIGHLI, DIGHLI, GOYALPOKHAR, U.D', 'Piles', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-03T10:00:00.000Z', '2025-03-03T10:00:00.000Z'
),
(
  'hist2025_8acaa23ad2c62803', 'KNE-05032025-001', '2025-03-05', '2025-03-05', '2025-03-05',
  'ASIT HALDAR', '9593382924', '', 'Kishanganj', '31', 'Male',
  'ABDULPUR, HASMPUR, DALKHOLA, U.D', 'Fistula', '65000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:00:00.000Z', '2025-03-05T10:00:00.000Z'
),
(
  'hist2025_27733fd60ec9d556', 'KNE-05032025-002', '2025-03-05', '2025-03-05', '2025-03-05',
  'SANTOSH CHOWHAN', '7488369122', '', 'Kishanganj', '47', 'Male',
  'FORUNGOLA, KNE, KNE, KNE', 'Fistula', '52000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:00:00.000Z', '2025-03-05T10:00:00.000Z'
),
(
  'hist2025_388d44e61ca04696', 'KNE-25032025-001', '2025-03-25', '2025-03-25', '2025-03-25',
  'ABU BAKAR', '9064346441', '', 'Kishanganj', '40', 'Male',
  'SEKHPURA, TAIYABUR, POTHIYA, KNE', 'Fistula', '55000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-25T10:00:00.000Z', '2025-03-25T10:00:00.000Z'
),
(
  'hist2025_b039066420864f7a', 'KNE-02042025-001', '2025-04-02', '2025-04-02', '2025-04-02',
  'SAKIL PARVEZ', '9665704592', '', 'Kishanganj', '', 'Male',
  'DHANTOLA', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-02T10:00:00.000Z', '2025-04-02T10:00:00.000Z'
),
(
  'hist2025_306f96d3150e04a2', 'KNE-09042025-001', '2025-04-09', '2025-04-09', '2025-04-09',
  'BABLU HEMRAO', '9229963597', '', 'Kishanganj', '', 'Male',
  '', 'Other', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-09T10:00:00.000Z', '2025-04-09T10:00:00.000Z'
),
(
  'hist2025_c87cdfd13034981f', 'KNE-15042025-001', '2025-04-15', '2025-04-15', '2025-04-15',
  'MANOJ KAMATI', '9798367794', '', 'Kishanganj', '', 'Male',
  'Deramari', 'Piles', '38000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-15T10:00:00.000Z', '2025-04-15T10:00:00.000Z'
),
(
  'hist2025_fabce8c86066732b', 'KNE-16042025-001', '2025-04-16', '2025-04-16', '2025-04-16',
  'HASINA KHATUN', '9933905097', '', 'Kishanganj', '70', 'Female',
  'Chakla', 'Piles', '50000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-16T10:00:00.000Z', '2025-04-16T10:00:00.000Z'
),
(
  'hist2025_648d95fed60e8bae', 'KNE-16042025-002', '2025-04-16', '2025-04-16', '2025-04-16',
  'MD IBRAHIM', '9430655565', '', 'Kishanganj', '33', 'Male',
  'Samda', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-16T10:00:00.000Z', '2025-04-16T10:00:00.000Z'
),
(
  'hist2025_91fb0a2f5913cddf', 'KNE-21042025-001', '2025-04-21', '2025-04-21', '2025-04-21',
  'TANVIR ALAM', '9679788944', '', 'Kishanganj', '28', 'Male',
  'Lohagachi', 'Piles, Fistula', '25000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-21T10:00:00.000Z', '2025-04-21T10:00:00.000Z'
),
(
  'hist2025_ce06c26454644bc9', 'KNE-23042025-001', '2025-04-23', '2025-04-23', '2025-04-23',
  'ANTIMA KUMARI', '9348825003', '', 'Kishanganj', '', 'Female',
  'pipla', 'Piles', '12000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:00:00.000Z', '2025-04-23T10:00:00.000Z'
),
(
  'hist2025_71245e5a159e2bf3', 'KNE-23042025-002', '2025-04-23', '2025-04-23', '2025-04-23',
  'MANOJ KR GHOSH', '8617379288', '', 'Kishanganj', '', 'Male',
  'Bijuliya, THAKUR BARI, CHAKULIYA, U.D', 'Piles, Fistula', '50000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:00:00.000Z', '2025-04-23T10:00:00.000Z'
),
(
  'hist2025_7e5997a5066627e2', 'KNE-23042025-003', '2025-04-23', '2025-04-23', '2025-04-23',
  'MONOHAR CHOUHAN', '9006664849', '', 'Kishanganj', '', 'Male',
  'Ranipatra', 'Piles', '51200',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:00:00.000Z', '2025-04-23T10:00:00.000Z'
),
(
  'hist2025_f3e5a03776207506', 'KNE-02052025-001', '2025-05-02', '2025-05-02', '2025-05-02',
  'SOMIJA KHATOON', '8580418375', '', 'Kishanganj', '40', 'Female',
  '', 'Other', '65000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-02T10:00:00.000Z', '2025-05-02T10:00:00.000Z'
),
(
  'hist2025_32c03339aaf38a5b', 'KNE-03052025-001', '2025-05-03', '2025-05-03', '2025-05-03',
  'RAHMAT ULLAH', '9973867443', '', 'Kishanganj', '36', 'Male',
  'PATHKOI, PATKHOI HAT, POCHADHAMON, KISHANGANJ', 'Hydrocele', '18000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-03T10:00:00.000Z', '2025-05-03T10:00:00.000Z'
),
(
  'hist2025_0b4d6ea829565e33', 'KNE-05052025-001', '2025-05-05', '2025-05-05', '2025-05-05',
  'BIBI FARIDA KHATOON', '8348973365', '', 'Kishanganj', '49', 'Female',
  'SAPATBARI, AMALIA, CHAKULIYA, U.D', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-05T10:00:00.000Z', '2025-05-05T10:00:00.000Z'
),
(
  'hist2025_16df3b3f2c2ecfaa', 'KNE-06052025-001', '2025-05-06', '2025-05-06', '2025-05-06',
  'ALARJAN KHATOON', '8116291683', '7001155804', 'Kishanganj', '25', 'Female',
  'SOLPARA, SOLPARA, GOYALPOKHAR, U.D', 'Piles', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-06T10:00:00.000Z', '2025-05-06T10:00:00.000Z'
),
(
  'hist2025_bc2d80e597952916', 'KNE-07052025-001', '2025-05-07', '2025-05-07', '2025-05-07',
  'TAHERA BIBI', '8509791198', '', 'Kishanganj', '42', 'Female',
  'CHAKULIYA, THAKURBARI, CHAKULIYA, U.D', 'Piles', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:00:00.000Z', '2025-05-07T10:00:00.000Z'
),
(
  'hist2025_ddcbd67d9f7a0b90', 'KNE-08052025-001', '2025-05-08', '2025-05-08', '2025-05-08',
  'SAHALAM', '9971572893', '', 'Kishanganj', '25', 'Male',
  'Bahadurganj, padampur, Dighal bank, Dighal bank', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-08T10:00:00.000Z', '2025-05-08T10:00:00.000Z'
),
(
  'hist2025_39662572e0b76a0d', 'KNE-14052025-001', '2025-05-14', '2025-05-14', '2025-05-14',
  'INFIZ ALAM', '7908907082', '', 'Kishanganj', '', 'Male',
  'Madrasa SAHAPUR, Hat khola sahapur, GOALPOKHAR, UTTAR DINAJPUR', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:00:00.000Z', '2025-05-14T10:00:00.000Z'
),
(
  'hist2025_4dae02a8960e7ab2', 'KNE-16052025-001', '2025-05-16', '2025-05-16', '2025-05-16',
  'ISKAT ALI', '6297977616', '', 'Kishanganj', '26', 'Male',
  'Goulin, Goulin, GOYALPOKHAR, U.d', 'Piles', '15000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-16T10:00:00.000Z', '2025-05-16T10:00:00.000Z'
),
(
  'hist2025_db59ea44d16b434f', 'KNE-18052025-001', '2025-05-18', '2025-05-18', '2025-05-18',
  'SAFALI DAS', '9733190879', '', 'Kishanganj', '33', 'Male',
  'GOYALPOKHAR, GOHORA, GOALPOKHAR, U.D', 'Piles', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-18T10:00:00.000Z', '2025-05-18T10:00:00.000Z'
),
(
  'hist2025_abc89c9aac3990eb', 'KNE-20052025-001', '2025-05-20', '2025-05-20', '2025-05-20',
  'SALMAN KHAN', '6295680491', '', 'Kishanganj', '23', 'Male',
  'Dalkhola, Charaiya, Baisi, Purniya', 'Gupt Rog', '75000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-20T10:00:00.000Z', '2025-05-20T10:00:00.000Z'
),
(
  'hist2025_b296a5837e432655', 'KNE-22052025-001', '2025-05-22', '2025-05-22', '2025-05-22',
  'BINOD KUMAR GHOSE', '7602934215', '', 'Kishanganj', '40', 'Male',
  'Dalkhola, Potnor, Dalkhola, U.d', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-22T10:00:00.000Z', '2025-05-22T10:00:00.000Z'
),
(
  'hist2025_b7b141bc7a2247af', 'KNE-02062025-001', '2025-06-02', '2025-06-02', '2025-06-02',
  'SUFAL SAREN', '6294977796', '', 'Kishanganj', '25', 'Male',
  'Ghardhappa, Chakuliya, chakuliya, u.d', 'Gupt Rog', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-02T10:00:00.000Z', '2025-06-02T10:00:00.000Z'
),
(
  'hist2025_12be63dff54f7c6d', 'KNE-02062025-002', '2025-06-02', '2025-06-02', '2025-06-02',
  'AKHILESH MALAKAR', '8877244718', '', 'Kishanganj', '35', 'Male',
  'Dhobidin, Sunghram, Rajour, Bamka', 'Other', '28000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-02T10:00:00.000Z', '2025-06-02T10:00:00.000Z'
),
(
  'hist2025_32b47085efc7d37a', 'KNE-05062025-001', '2025-06-05', '2025-06-05', '2025-06-05',
  'MANIK SINGHA', '9382409362', '', 'Kishanganj', '40', 'Male',
  'Sathmeri, Hatkhola Sahpur, GOYALPOKHAR, U.d', 'Piles', '28000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-05T10:00:00.000Z', '2025-06-05T10:00:00.000Z'
),
(
  'hist2025_8f481f48e8b4d3b7', 'KNE-06062025-001', '2025-06-06', '2025-06-06', '2025-06-06',
  'MD DULAL', '9934637437', '', 'Kishanganj', '32', 'Male',
  'Dighalbank, Dighalbank, Dighalbank, Kishanganj', 'Piles', '42000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-06T10:00:00.000Z', '2025-06-06T10:00:00.000Z'
),
(
  'hist2025_3da8b1f7abd5303e', 'KNE-12062025-001', '2025-06-12', '2025-06-12', '2025-06-12',
  'NAJIMUL HODA', '8925261679', '', 'Kishanganj', '23', 'Male',
  'Barman, Andhoriya, DALKHOLA, U.d', 'Piles', '100000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-12T10:00:00.000Z', '2025-06-12T10:00:00.000Z'
),
(
  'hist2025_9db0d658b8405373', 'KNE-13062025-001', '2025-06-13', '2025-06-13', '2025-06-13',
  'KRISHNA KARMAKAR', '9631935125', '', 'Kishanganj', '29', 'Male',
  'Railway Colony, Churiptti, Kishanganj, Kishanganj', 'Piles', '64000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-13T10:00:00.000Z', '2025-06-13T10:00:00.000Z'
),
(
  'hist2025_791cbf75a06452fa', 'KNE-19062025-001', '2025-06-19', '2025-06-19', '2025-06-19',
  'SUMAN KUMARI', '8789193154', '', 'Kishanganj', '25', 'Female',
  'Loharpatti, Kishanganj, Kishanganj, KISHANGANJ', 'Piles', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-19T10:00:00.000Z', '2025-06-19T10:00:00.000Z'
),
(
  'hist2025_d157c5df6e0bcd89', 'KNE-20062025-001', '2025-06-20', '2025-06-20', '2025-06-20',
  'SAHAJADI', '8848852354', '', 'Kishanganj', '17', 'Male',
  'Lodhon, GOYALPOKHAR, GOYALPOKHAR, U.d', 'Piles', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-20T10:00:00.000Z', '2025-06-20T10:00:00.000Z'
),
(
  'hist2025_8288043e3779e060', 'KNE-22062025-001', '2025-06-22', '2025-06-22', '2025-06-22',
  'ANBAR HOUSSIN', '9733243745', '', 'Kishanganj', '44', 'Male',
  'Churrakutti, GORRA, GOYALPOKHAR, U.D', 'Piles', '45000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-22T10:00:00.000Z', '2025-06-22T10:00:00.000Z'
),
(
  'hist2025_0db30041f0978ac3', 'KNE-23062025-001', '2025-06-23', '2025-06-23', '2025-06-23',
  'SAHABAZ ALAM', '9391276598', '', 'Kishanganj', '28', 'Male',
  'TULSIYA, SOMESAR, BAHADURGANJ, KISHANGANJ', 'Gupt Rog', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-23T10:00:00.000Z', '2025-06-23T10:00:00.000Z'
),
(
  'hist2025_d5a3c51145e1759c', 'KNE-26062025-001', '2025-06-26', '2025-06-26', '2025-06-26',
  'MAHAMAD MAHFUJ ALAM', '8146078470', '', 'Kishanganj', '50', 'Male',
  'CHROLOYA, DUSMAL, ANGAR, PURNIYA', 'Piles', '45000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-26T10:00:00.000Z', '2025-06-26T10:00:00.000Z'
),
(
  'hist2025_4ddd27499458b346', 'KNE-26062025-002', '2025-06-26', '2025-06-26', '2025-06-26',
  'MOHAMAD CHAND', '8809705409', '', 'Kishanganj', '22', 'Male',
  'RUIDASHA, KISHANGANJ, Kishanganj, Kishanganj', 'Piles', '58000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-26T10:00:00.000Z', '2025-06-26T10:00:00.000Z'
),
(
  'hist2025_10d9644e504705ff', 'KNE-02072025-001', '2025-07-02', '2025-07-02', '2025-07-02',
  'ARSHAD NAIK', '9103252865', '', 'Kishanganj', '27', 'Male',
  'LILIYACHOWK, LILIYACHOWK, LILIYACHOWK, Kishanganj', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:00:00.000Z', '2025-07-02T10:00:00.000Z'
),
(
  'hist2025_f367f41b21d311a4', 'KNE-05072025-001', '2025-07-05', '2025-07-05', '2025-07-05',
  'MD SAHID', '7061321194', '', 'Kishanganj', '26', 'Male',
  'Balrampur, Barsoi, Balrampur, Katihar', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:00:00.000Z', '2025-07-05T10:00:00.000Z'
),
(
  'hist2025_35ae1d6fe56dc53f', 'KNE-07072025-001', '2025-07-07', '2025-07-07', '2025-07-07',
  'SUNIL JAIN', '8822702712', '', 'Kishanganj', '72', 'Male',
  'PURB PALLI, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-07T10:00:00.000Z', '2025-07-07T10:00:00.000Z'
),
(
  'hist2025_9e912a547990c596', 'KNE-07072025-002', '2025-07-07', '2025-07-07', '2025-07-07',
  'LOVELY RANI', '8884841062', '', 'Kishanganj', '33', 'Female',
  'MILLA PALLI, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-07T10:00:00.000Z', '2025-07-07T10:00:00.000Z'
),
(
  'hist2025_7c49eacd0e2ac5a7', 'KNE-09072025-001', '2025-07-09', '2025-07-09', '2025-07-09',
  'MARJINA', '7908231003', '', 'Kishanganj', '20', 'Male',
  'LODHAN, LODHON, GOYALPOKHAR, U.D', 'Fissure', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:00:00.000Z', '2025-07-09T10:00:00.000Z'
),
(
  'hist2025_293c0dff82e9c675', 'KNE-09072025-002', '2025-07-09', '2025-07-09', '2025-07-09',
  'CHADNI', '8016775777', '', 'Kishanganj', '20', 'Male',
  'PANJIPARA, PANJIPARA, GOYALPOKHAR, U.D', 'Piles', '100000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:00:00.000Z', '2025-07-09T10:00:00.000Z'
);

insert into public.payments (
  id, "payType", "payLabel", "paymentLabel", "patientId", mobile, branch, name,
  date, amount, mode, remarks,
  "createdBy", "receivedBy", "createdAt", "updatedAt"
) values
(
  'hist2025_pay_c96745fb12c47e04', 'treatment', 'Advance', 'Advance', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-06T10:15:00.000Z', '2025-01-06T10:15:00.000Z'
),
(
  'hist2025_pay_cc441977fa49979c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-08', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-08T10:16:00.000Z', '2025-01-08T10:16:00.000Z'
),
(
  'hist2025_pay_640ed509ad8defce', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-13', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-13T10:17:00.000Z', '2025-01-13T10:17:00.000Z'
),
(
  'hist2025_pay_e957f7498e1a6368', 'treatment', '4th Payment', '4th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-15T10:18:00.000Z', '2025-01-15T10:18:00.000Z'
),
(
  'hist2025_pay_adf3d7d18a2f82f7', 'treatment', '5th Payment', '5th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-20', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-20T10:19:00.000Z', '2025-01-20T10:19:00.000Z'
),
(
  'hist2025_pay_44cd908de62c4577', 'treatment', '6th Payment', '6th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-22T10:20:00.000Z', '2025-01-22T10:20:00.000Z'
),
(
  'hist2025_pay_e40669eb6f97901a', 'treatment', '7th Payment', '7th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-24', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-24T10:21:00.000Z', '2025-01-24T10:21:00.000Z'
),
(
  'hist2025_pay_3403009f4a7e7adb', 'treatment', '8th Payment', '8th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-01-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-29T10:22:00.000Z', '2025-01-29T10:22:00.000Z'
),
(
  'hist2025_pay_d37399b83f044cb0', 'treatment', '9th Payment', '9th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-02-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:23:00.000Z', '2025-02-05T10:23:00.000Z'
),
(
  'hist2025_pay_9d1ee2cf2aafaf28', 'treatment', '10th Payment', '10th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-02-07', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-07T10:24:00.000Z', '2025-02-07T10:24:00.000Z'
),
(
  'hist2025_pay_b88d2d423b3a398b', 'treatment', '11th Payment', '11th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-02-14', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-14T10:25:00.000Z', '2025-02-14T10:25:00.000Z'
),
(
  'hist2025_pay_b58278548e552ea7', 'treatment', '12th Payment', '12th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-02-28', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-28T10:26:00.000Z', '2025-02-28T10:26:00.000Z'
),
(
  'hist2025_pay_ad02d5f43178153b', 'treatment', '13th Payment', '13th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-03-10', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-10T10:27:00.000Z', '2025-03-10T10:27:00.000Z'
),
(
  'hist2025_pay_d25c63f1f66e5aef', 'treatment', '14th Payment', '14th Payment', 'hist2025_47026da8fe5be4a7', '9126495421', 'Kishanganj', 'PARESH MANDAL',
  '2025-06-02', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-02T10:28:00.000Z', '2025-06-02T10:28:00.000Z'
),
(
  'hist2025_pay_ab5c7e3b52c270d1', 'treatment', 'Advance', 'Advance', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-01-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-06T10:15:00.000Z', '2025-01-06T10:15:00.000Z'
),
(
  'hist2025_pay_85a73d619c416a11', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-01-08', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-08T10:16:00.000Z', '2025-01-08T10:16:00.000Z'
),
(
  'hist2025_pay_a08535ea6b7f5a39', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-01-11', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-11T10:17:00.000Z', '2025-01-11T10:17:00.000Z'
),
(
  'hist2025_pay_1aebbffa215654ec', 'treatment', '4th Payment', '4th Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-01-16', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-16T10:18:00.000Z', '2025-01-16T10:18:00.000Z'
),
(
  'hist2025_pay_28153d45dc6bdef9', 'treatment', '5th Payment', '5th Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-02-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-03T10:19:00.000Z', '2025-02-03T10:19:00.000Z'
),
(
  'hist2025_pay_42e5fe068bc7a85d', 'treatment', '6th Payment', '6th Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-02-05', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:20:00.000Z', '2025-02-05T10:20:00.000Z'
),
(
  'hist2025_pay_4d748137646ba723', 'treatment', '7th Payment', '7th Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-02-08', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-08T10:21:00.000Z', '2025-02-08T10:21:00.000Z'
),
(
  'hist2025_pay_718c8d4365c1a595', 'treatment', '8th Payment', '8th Payment', 'hist2025_0dcd595e07cc56eb', '9064332124', 'Kishanganj', 'NASIMA KHATUN',
  '2025-02-12', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-12T10:22:00.000Z', '2025-02-12T10:22:00.000Z'
),
(
  'hist2025_pay_1a69254c1424b141', 'treatment', 'Advance', 'Advance', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-01-17', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-17T10:15:00.000Z', '2025-01-17T10:15:00.000Z'
),
(
  'hist2025_pay_b926992b6c3d0889', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-01-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-01-29T10:16:00.000Z', '2025-01-29T10:16:00.000Z'
),
(
  'hist2025_pay_2b4ffc205e825ee7', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-02-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:17:00.000Z', '2025-02-05T10:17:00.000Z'
),
(
  'hist2025_pay_f3b4fe2682fb7f42', 'treatment', '4th Payment', '4th Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-02-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-12T10:18:00.000Z', '2025-02-12T10:18:00.000Z'
),
(
  'hist2025_pay_7ca64a437b101658', 'treatment', '5th Payment', '5th Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-02-19', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:19:00.000Z', '2025-02-19T10:19:00.000Z'
),
(
  'hist2025_pay_e09f64decc65725e', 'treatment', '6th Payment', '6th Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-02-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-26T10:20:00.000Z', '2025-02-26T10:20:00.000Z'
),
(
  'hist2025_pay_ee0bbddb32b99199', 'treatment', '7th Payment', '7th Payment', 'hist2025_c879971b33c9b390', '9006712520', 'Kishanganj', 'SABIR ALAM',
  '2025-03-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:21:00.000Z', '2025-03-05T10:21:00.000Z'
),
(
  'hist2025_pay_13922ac004855260', 'treatment', 'Advance', 'Advance', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-05', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:15:00.000Z', '2025-02-05T10:15:00.000Z'
),
(
  'hist2025_pay_11009e8105d503bc', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-08', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-08T10:16:00.000Z', '2025-02-08T10:16:00.000Z'
),
(
  'hist2025_pay_7d470d7c52ce038e', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-15T10:17:00.000Z', '2025-02-15T10:17:00.000Z'
),
(
  'hist2025_pay_54ba3d90cd88ab46', 'treatment', '4th Payment', '4th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:18:00.000Z', '2025-02-19T10:18:00.000Z'
),
(
  'hist2025_pay_4e0b54594f233203', 'treatment', '5th Payment', '5th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-22T10:19:00.000Z', '2025-02-22T10:19:00.000Z'
),
(
  'hist2025_pay_b6ce0e6b0b44cecb', 'treatment', '6th Payment', '6th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-26', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-26T10:20:00.000Z', '2025-02-26T10:20:00.000Z'
),
(
  'hist2025_pay_e7f25cfcda0a24e5', 'treatment', '7th Payment', '7th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-02-28', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-28T10:21:00.000Z', '2025-02-28T10:21:00.000Z'
),
(
  'hist2025_pay_4b7129052ddd9684', 'treatment', '8th Payment', '8th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-03-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-08T10:22:00.000Z', '2025-03-08T10:22:00.000Z'
),
(
  'hist2025_pay_49b019a77bc42c0d', 'treatment', '9th Payment', '9th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-03-17', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-17T10:23:00.000Z', '2025-03-17T10:23:00.000Z'
),
(
  'hist2025_pay_6f12178e2656dba0', 'treatment', '10th Payment', '10th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-04-23', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:24:00.000Z', '2025-04-23T10:24:00.000Z'
),
(
  'hist2025_pay_d9469246f2568199', 'treatment', '11th Payment', '11th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-05-14', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:25:00.000Z', '2025-05-14T10:25:00.000Z'
),
(
  'hist2025_pay_47e861bc3c743993', 'treatment', '12th Payment', '12th Payment', 'hist2025_e186a9d641acfbed', '8388008655', 'Kishanganj', 'BIPUL BARMAN',
  '2025-06-18', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:26:00.000Z', '2025-06-18T10:26:00.000Z'
),
(
  'hist2025_pay_31452823630aa95b', 'treatment', 'Advance', 'Advance', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-03', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-03T10:15:00.000Z', '2025-02-03T10:15:00.000Z'
),
(
  'hist2025_pay_5637bdec22a3c80c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-05', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:16:00.000Z', '2025-02-05T10:16:00.000Z'
),
(
  'hist2025_pay_618b9a78b23bc39e', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-08', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-08T10:17:00.000Z', '2025-02-08T10:17:00.000Z'
),
(
  'hist2025_pay_b857b44f1004411a', 'treatment', '4th Payment', '4th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-12', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-12T10:18:00.000Z', '2025-02-12T10:18:00.000Z'
),
(
  'hist2025_pay_3c4eec34ae4f2ddb', 'treatment', '5th Payment', '5th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-16', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-16T10:19:00.000Z', '2025-02-16T10:19:00.000Z'
),
(
  'hist2025_pay_b30167b2953e4a31', 'treatment', '6th Payment', '6th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:20:00.000Z', '2025-02-19T10:20:00.000Z'
),
(
  'hist2025_pay_1ebac0b95442a0cc', 'treatment', '7th Payment', '7th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-22', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-22T10:21:00.000Z', '2025-02-22T10:21:00.000Z'
),
(
  'hist2025_pay_30ddd343ca33dc4d', 'treatment', '8th Payment', '8th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-25', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-25T10:22:00.000Z', '2025-02-25T10:22:00.000Z'
),
(
  'hist2025_pay_0a759ed072f3de84', 'treatment', '9th Payment', '9th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-02-29', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-29T10:23:00.000Z', '2025-02-29T10:23:00.000Z'
),
(
  'hist2025_pay_9b6e1c6f579d2cc0', 'treatment', '10th Payment', '10th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-03-01', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-01T10:24:00.000Z', '2025-03-01T10:24:00.000Z'
),
(
  'hist2025_pay_1286f8d6decdfb12', 'treatment', '11th Payment', '11th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-03-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-03T10:25:00.000Z', '2025-03-03T10:25:00.000Z'
),
(
  'hist2025_pay_a7c50bc182e7c0ac', 'treatment', '12th Payment', '12th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-03-05', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:26:00.000Z', '2025-03-05T10:26:00.000Z'
),
(
  'hist2025_pay_c0d3ecfaca6d9d03', 'treatment', '13th Payment', '13th Payment', 'hist2025_ff45a89e445c3975', '8942967494', 'Kishanganj', 'MD TEJIN UDDIN',
  '2025-03-10', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-10T10:27:00.000Z', '2025-03-10T10:27:00.000Z'
),
(
  'hist2025_pay_259f3f8d8d2debbd', 'treatment', 'Advance', 'Advance', 'hist2025_88d8f382014de132', '9347502740', 'Kishanganj', 'KAMRUL HOSSIN',
  '2025-02-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:15:00.000Z', '2025-02-05T10:15:00.000Z'
),
(
  'hist2025_pay_2c861320263f8f13', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_88d8f382014de132', '9347502740', 'Kishanganj', 'KAMRUL HOSSIN',
  '2025-02-15', '13000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-15T10:16:00.000Z', '2025-02-15T10:16:00.000Z'
),
(
  'hist2025_pay_0b672babac361fd1', 'treatment', 'Advance', 'Advance', 'hist2025_7b51be03bcc5e104', '8825174821', 'Kishanganj', 'ZIDARUL HOWAK',
  '2025-02-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-05T10:15:00.000Z', '2025-02-05T10:15:00.000Z'
),
(
  'hist2025_pay_de33b5c5d5bca459', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_7b51be03bcc5e104', '8825174821', 'Kishanganj', 'ZIDARUL HOWAK',
  '2025-02-13', '14000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-13T10:16:00.000Z', '2025-02-13T10:16:00.000Z'
),
(
  'hist2025_pay_7d9d46b8cf2f0863', 'treatment', 'Advance', 'Advance', 'hist2025_1e780218698b7151', '8676073770', 'Kishanganj', 'LALIF PASWAN',
  '2025-02-12', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-12T10:15:00.000Z', '2025-02-12T10:15:00.000Z'
),
(
  'hist2025_pay_88417bcbade1c782', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_1e780218698b7151', '8676073770', 'Kishanganj', 'LALIF PASWAN',
  '2025-02-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-15T10:16:00.000Z', '2025-02-15T10:16:00.000Z'
),
(
  'hist2025_pay_c3e9e19a04ad61a4', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_1e780218698b7151', '8676073770', 'Kishanganj', 'LALIF PASWAN',
  '2025-03-08', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-08T10:17:00.000Z', '2025-03-08T10:17:00.000Z'
),
(
  'hist2025_pay_c7fe41b8558f6318', 'treatment', '4th Payment', '4th Payment', 'hist2025_1e780218698b7151', '8676073770', 'Kishanganj', 'LALIF PASWAN',
  '2025-03-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-15T10:18:00.000Z', '2025-03-15T10:18:00.000Z'
),
(
  'hist2025_pay_956d641dc8683dec', 'treatment', '5th Payment', '5th Payment', 'hist2025_1e780218698b7151', '8676073770', 'Kishanganj', 'LALIF PASWAN',
  '2025-03-22', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-22T10:19:00.000Z', '2025-03-22T10:19:00.000Z'
),
(
  'hist2025_pay_55b19c7b0a46e940', 'treatment', 'Advance', 'Advance', 'hist2025_0e0faa9efa689c24', '9593316409', 'Kishanganj', 'RESHMA KHATOON',
  '2025-02-19', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-19T10:15:00.000Z', '2025-02-19T10:15:00.000Z'
),
(
  'hist2025_pay_7163ccca6739b258', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0e0faa9efa689c24', '9593316409', 'Kishanganj', 'RESHMA KHATOON',
  '2025-02-22', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-22T10:16:00.000Z', '2025-02-22T10:16:00.000Z'
),
(
  'hist2025_pay_367bd1135a4f6533', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0e0faa9efa689c24', '9593316409', 'Kishanganj', 'RESHMA KHATOON',
  '2025-03-01', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-01T10:17:00.000Z', '2025-03-01T10:17:00.000Z'
),
(
  'hist2025_pay_0642465f28144d3b', 'treatment', '4th Payment', '4th Payment', 'hist2025_0e0faa9efa689c24', '9593316409', 'Kishanganj', 'RESHMA KHATOON',
  '2025-03-05', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:18:00.000Z', '2025-03-05T10:18:00.000Z'
),
(
  'hist2025_pay_3f91af2afc356161', 'treatment', 'Advance', 'Advance', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-07-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:15:00.000Z', '2025-07-23T10:15:00.000Z'
),
(
  'hist2025_pay_5946ab02ae312c70', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-02', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:16:00.000Z', '2025-08-02T10:16:00.000Z'
),
(
  'hist2025_pay_afd870d363047ae5', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-06', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:17:00.000Z', '2025-08-06T10:17:00.000Z'
),
(
  'hist2025_pay_78b14a36a1864228', 'treatment', '4th Payment', '4th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-13', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:18:00.000Z', '2025-08-13T10:18:00.000Z'
),
(
  'hist2025_pay_d376a8b804c90e8a', 'treatment', '5th Payment', '5th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-16', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-16T10:19:00.000Z', '2025-08-16T10:19:00.000Z'
),
(
  'hist2025_pay_e61752f26963c62f', 'treatment', '6th Payment', '6th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-20', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-20T10:20:00.000Z', '2025-08-20T10:20:00.000Z'
),
(
  'hist2025_pay_85aa203a22ac155b', 'treatment', '7th Payment', '7th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-23', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-23T10:21:00.000Z', '2025-08-23T10:21:00.000Z'
),
(
  'hist2025_pay_dd2a05b79a4a2b39', 'treatment', '8th Payment', '8th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-08-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:22:00.000Z', '2025-08-27T10:22:00.000Z'
),
(
  'hist2025_pay_c4b279a36580c697', 'treatment', '9th Payment', '9th Payment', 'hist2025_3effe16fc3428e38', '9609023646', 'Kishanganj', 'MD AHRAR ALAM',
  '2025-10-25', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:23:00.000Z', '2025-10-25T10:23:00.000Z'
),
(
  'hist2025_pay_dc67f10de783a546', 'treatment', 'Advance', 'Advance', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-07-23', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:15:00.000Z', '2025-07-23T10:15:00.000Z'
),
(
  'hist2025_pay_7db94526ec78bfab', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-07-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-26T10:16:00.000Z', '2025-07-26T10:16:00.000Z'
),
(
  'hist2025_pay_06819917eb7ff1f9', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:17:00.000Z', '2025-07-30T10:17:00.000Z'
),
(
  'hist2025_pay_80d129c028252fa9', 'treatment', '4th Payment', '4th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-08-06', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:18:00.000Z', '2025-08-06T10:18:00.000Z'
),
(
  'hist2025_pay_5b9e40b90197df3f', 'treatment', '5th Payment', '5th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-08-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:19:00.000Z', '2025-08-13T10:19:00.000Z'
),
(
  'hist2025_pay_ae875ade093a5d44', 'treatment', '6th Payment', '6th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-08-20', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-20T10:20:00.000Z', '2025-08-20T10:20:00.000Z'
),
(
  'hist2025_pay_628bdf069a071f66', 'treatment', '7th Payment', '7th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-08-27', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:21:00.000Z', '2025-08-27T10:21:00.000Z'
),
(
  'hist2025_pay_b86cd9e173fa200b', 'treatment', '8th Payment', '8th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-09-03', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:22:00.000Z', '2025-09-03T10:22:00.000Z'
),
(
  'hist2025_pay_6e702cc4115d5333', 'treatment', '9th Payment', '9th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-09-10', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-10T10:23:00.000Z', '2025-09-10T10:23:00.000Z'
),
(
  'hist2025_pay_39df6aab03d4e59e', 'treatment', '10th Payment', '10th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-09-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:24:00.000Z', '2025-09-17T10:24:00.000Z'
),
(
  'hist2025_pay_a262bfd87f86de4b', 'treatment', '11th Payment', '11th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-10-01', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-01T10:25:00.000Z', '2025-10-01T10:25:00.000Z'
),
(
  'hist2025_pay_82e2d259a33edc06', 'treatment', '12th Payment', '12th Payment', 'hist2025_cf1306f9b745efa4', '8757937444', 'Kishanganj', 'MD NABI',
  '2025-10-15', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:26:00.000Z', '2025-10-15T10:26:00.000Z'
),
(
  'hist2025_pay_d5786bbc188b45c6', 'treatment', 'Advance', 'Advance', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-02-28', '1000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-28T10:15:00.000Z', '2025-02-28T10:15:00.000Z'
),
(
  'hist2025_pay_90e286fc2b975389', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-02-28', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-02-28T10:16:00.000Z', '2025-02-28T10:16:00.000Z'
),
(
  'hist2025_pay_e861758ab0e971a6', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-04', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-04T10:17:00.000Z', '2025-03-04T10:17:00.000Z'
),
(
  'hist2025_pay_83961efefe6128fd', 'treatment', '4th Payment', '4th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-05', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:18:00.000Z', '2025-03-05T10:18:00.000Z'
),
(
  'hist2025_pay_7f8e9b3dd27e2fa8', 'treatment', '5th Payment', '5th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-08', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-08T10:19:00.000Z', '2025-03-08T10:19:00.000Z'
),
(
  'hist2025_pay_03085d8d63bb85e1', 'treatment', '6th Payment', '6th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-12', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-12T10:20:00.000Z', '2025-03-12T10:20:00.000Z'
),
(
  'hist2025_pay_448b3b3dff1c9e66', 'treatment', '7th Payment', '7th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-19T10:21:00.000Z', '2025-03-19T10:21:00.000Z'
),
(
  'hist2025_pay_fd3a960b911d8598', 'treatment', '8th Payment', '8th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-03-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-27T10:22:00.000Z', '2025-03-27T10:22:00.000Z'
),
(
  'hist2025_pay_37cfd9cb62f76298', 'treatment', '9th Payment', '9th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-04-06', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-06T10:23:00.000Z', '2025-04-06T10:23:00.000Z'
),
(
  'hist2025_pay_a057e5f4865cba77', 'treatment', '10th Payment', '10th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-05-28', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:24:00.000Z', '2025-05-28T10:24:00.000Z'
),
(
  'hist2025_pay_909ff3eab2949cbe', 'treatment', '11th Payment', '11th Payment', 'hist2025_0197614e2aa43dfc', '9733237054', 'Kishanganj', 'DEVLAL SINGH',
  '2025-06-06', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-06T10:25:00.000Z', '2025-06-06T10:25:00.000Z'
),
(
  'hist2025_pay_8581921afce66baf', 'treatment', 'Advance', 'Advance', 'hist2025_9f9f6396797e0341', '6296133324', 'Kishanganj', 'NUR SELIM',
  '2025-03-03', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-03T10:15:00.000Z', '2025-03-03T10:15:00.000Z'
),
(
  'hist2025_pay_5c7db3b573712791', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_9f9f6396797e0341', '6296133324', 'Kishanganj', 'NUR SELIM',
  '2025-03-05', '12500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:16:00.000Z', '2025-03-05T10:16:00.000Z'
),
(
  'hist2025_pay_c44927c13d1f9f2d', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_9f9f6396797e0341', '6296133324', 'Kishanganj', 'NUR SELIM',
  '2025-03-10', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-10T10:17:00.000Z', '2025-03-10T10:17:00.000Z'
),
(
  'hist2025_pay_8f17284424ebfccf', 'treatment', '4th Payment', '4th Payment', 'hist2025_9f9f6396797e0341', '6296133324', 'Kishanganj', 'NUR SELIM',
  '2025-03-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-20T10:18:00.000Z', '2025-03-20T10:18:00.000Z'
),
(
  'hist2025_pay_b55774a2b30cc3d6', 'treatment', 'Advance', 'Advance', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-05T10:15:00.000Z', '2025-03-05T10:15:00.000Z'
),
(
  'hist2025_pay_e1b44d5c12f09cce', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-08', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-08T10:16:00.000Z', '2025-03-08T10:16:00.000Z'
),
(
  'hist2025_pay_6ca4e062205af22c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-12', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-12T10:17:00.000Z', '2025-03-12T10:17:00.000Z'
),
(
  'hist2025_pay_d35848ae5e42380d', 'treatment', '4th Payment', '4th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-18', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-18T10:18:00.000Z', '2025-03-18T10:18:00.000Z'
),
(
  'hist2025_pay_ae51dd2f454491ee', 'treatment', '5th Payment', '5th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-22', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-22T10:19:00.000Z', '2025-03-22T10:19:00.000Z'
),
(
  'hist2025_pay_c0b96859fc24b846', 'treatment', '6th Payment', '6th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-04-05', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-05T10:20:00.000Z', '2025-04-05T10:20:00.000Z'
),
(
  'hist2025_pay_d19d543d3d4c7762', 'treatment', '7th Payment', '7th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-04-09', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-09T10:21:00.000Z', '2025-04-09T10:21:00.000Z'
),
(
  'hist2025_pay_c3577293bdb8a8fb', 'treatment', '8th Payment', '8th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-15T10:22:00.000Z', '2025-03-15T10:22:00.000Z'
),
(
  'hist2025_pay_2633d0545a3f3172', 'treatment', '9th Payment', '9th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-26T10:23:00.000Z', '2025-03-26T10:23:00.000Z'
),
(
  'hist2025_pay_751e1e0d2f86b134', 'treatment', '10th Payment', '10th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-03-29', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-29T10:24:00.000Z', '2025-03-29T10:24:00.000Z'
),
(
  'hist2025_pay_a7fbab6ca6196727', 'treatment', '11th Payment', '11th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-04-02', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-02T10:25:00.000Z', '2025-04-02T10:25:00.000Z'
),
(
  'hist2025_pay_f1e557f275d8db09', 'treatment', '12th Payment', '12th Payment', 'hist2025_8acaa23ad2c62803', '9593382924', 'Kishanganj', 'ASIT HALDAR',
  '2025-04-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-17T10:26:00.000Z', '2025-04-17T10:26:00.000Z'
),
(
  'hist2025_pay_e7c06825c7e4e1a2', 'treatment', 'Advance', 'Advance', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-06', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-06T10:15:00.000Z', '2025-03-06T10:15:00.000Z'
),
(
  'hist2025_pay_4c03f55ec7d3d1cf', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-08', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-08T10:16:00.000Z', '2025-03-08T10:16:00.000Z'
),
(
  'hist2025_pay_284e0cd2356b7ba9', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-12', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-12T10:17:00.000Z', '2025-03-12T10:17:00.000Z'
),
(
  'hist2025_pay_058567ce89f0224e', 'treatment', '4th Payment', '4th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-15T10:18:00.000Z', '2025-03-15T10:18:00.000Z'
),
(
  'hist2025_pay_c10281f9b82242c3', 'treatment', '5th Payment', '5th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-17T10:19:00.000Z', '2025-03-17T10:19:00.000Z'
),
(
  'hist2025_pay_c5e6fcf98978de34', 'treatment', '6th Payment', '6th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-19T10:20:00.000Z', '2025-03-19T10:20:00.000Z'
),
(
  'hist2025_pay_f008764585b32eb7', 'treatment', '7th Payment', '7th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-21', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-21T10:21:00.000Z', '2025-03-21T10:21:00.000Z'
),
(
  'hist2025_pay_a2dfe7b1dd1ac138', 'treatment', '8th Payment', '8th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-23T10:22:00.000Z', '2025-03-23T10:22:00.000Z'
),
(
  'hist2025_pay_fe45d0d81a9b4874', 'treatment', '9th Payment', '9th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-25', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-25T10:23:00.000Z', '2025-03-25T10:23:00.000Z'
),
(
  'hist2025_pay_fb81e1def1cda2c0', 'treatment', '10th Payment', '10th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-27T10:24:00.000Z', '2025-03-27T10:24:00.000Z'
),
(
  'hist2025_pay_d936d5d551023f11', 'treatment', '11th Payment', '11th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-03-29', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-29T10:25:00.000Z', '2025-03-29T10:25:00.000Z'
),
(
  'hist2025_pay_beb3670e48d3a90c', 'treatment', '12th Payment', '12th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-04-01', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-01T10:26:00.000Z', '2025-04-01T10:26:00.000Z'
),
(
  'hist2025_pay_4945c8a3dea256a7', 'treatment', '13th Payment', '13th Payment', 'hist2025_27733fd60ec9d556', '7488369122', 'Kishanganj', 'SANTOSH CHOWHAN',
  '2025-04-08', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-08T10:27:00.000Z', '2025-04-08T10:27:00.000Z'
),
(
  'hist2025_pay_a017fd1574d2d960', 'treatment', 'Advance', 'Advance', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-03-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-25T10:15:00.000Z', '2025-03-25T10:15:00.000Z'
),
(
  'hist2025_pay_0a2e00c9b183e75d', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-03-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-03-29T10:16:00.000Z', '2025-03-29T10:16:00.000Z'
),
(
  'hist2025_pay_7b3d0ce85a4c605e', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-02', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-02T10:17:00.000Z', '2025-04-02T10:17:00.000Z'
),
(
  'hist2025_pay_895c2d9b878fbbbe', 'treatment', '4th Payment', '4th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-16', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-16T10:18:00.000Z', '2025-04-16T10:18:00.000Z'
),
(
  'hist2025_pay_9115a66e013eac4b', 'treatment', '5th Payment', '5th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:19:00.000Z', '2025-04-23T10:19:00.000Z'
),
(
  'hist2025_pay_4a2fbf89767c27b8', 'treatment', '6th Payment', '6th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-26T10:20:00.000Z', '2025-04-26T10:20:00.000Z'
),
(
  'hist2025_pay_0709060131e036e6', 'treatment', '7th Payment', '7th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-07', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-07T10:21:00.000Z', '2025-04-07T10:21:00.000Z'
),
(
  'hist2025_pay_13fb4529313fdba4', 'treatment', '8th Payment', '8th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-04-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-14T10:22:00.000Z', '2025-04-14T10:22:00.000Z'
),
(
  'hist2025_pay_d8ac3d24727e8b22', 'treatment', '9th Payment', '9th Payment', 'hist2025_388d44e61ca04696', '9064346441', 'Kishanganj', 'ABU BAKAR',
  '2025-06-04', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:23:00.000Z', '2025-06-04T10:23:00.000Z'
),
(
  'hist2025_pay_45222fee62ae1e0c', 'treatment', 'Advance', 'Advance', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-04-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-02T10:15:00.000Z', '2025-04-02T10:15:00.000Z'
),
(
  'hist2025_pay_da8f3c97c04480f5', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-04-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-05T10:16:00.000Z', '2025-04-05T10:16:00.000Z'
),
(
  'hist2025_pay_8b224bbe08b7963f', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-04-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-09T10:17:00.000Z', '2025-04-09T10:17:00.000Z'
),
(
  'hist2025_pay_cd48de02df77175c', 'treatment', '4th Payment', '4th Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-04-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-14T10:18:00.000Z', '2025-04-14T10:18:00.000Z'
),
(
  'hist2025_pay_fddae21d477ab6a9', 'treatment', '5th Payment', '5th Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-04-21', '9000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-21T10:19:00.000Z', '2025-04-21T10:19:00.000Z'
),
(
  'hist2025_pay_acc7487943369460', 'treatment', '6th Payment', '6th Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-05-28', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:20:00.000Z', '2025-05-28T10:20:00.000Z'
),
(
  'hist2025_pay_d031bca3672d62fc', 'treatment', '7th Payment', '7th Payment', 'hist2025_b039066420864f7a', '9665704592', 'Kishanganj', 'SAKIL PARVEZ',
  '2025-06-15', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-15T10:21:00.000Z', '2025-06-15T10:21:00.000Z'
),
(
  'hist2025_pay_b46432a0d31843a6', 'treatment', 'Advance', 'Advance', 'hist2025_306f96d3150e04a2', '9229963597', 'Kishanganj', 'BABLU HEMRAO',
  '2025-04-09', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-09T10:15:00.000Z', '2025-04-09T10:15:00.000Z'
),
(
  'hist2025_pay_fe4f4072dff5280d', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_306f96d3150e04a2', '9229963597', 'Kishanganj', 'BABLU HEMRAO',
  '2025-04-18', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-18T10:16:00.000Z', '2025-04-18T10:16:00.000Z'
),
(
  'hist2025_pay_9307c880ae1012f4', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_306f96d3150e04a2', '9229963597', 'Kishanganj', 'BABLU HEMRAO',
  '2025-04-22', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-22T10:17:00.000Z', '2025-04-22T10:17:00.000Z'
),
(
  'hist2025_pay_73500d278b95c7b5', 'treatment', '4th Payment', '4th Payment', 'hist2025_306f96d3150e04a2', '9229963597', 'Kishanganj', 'BABLU HEMRAO',
  '2025-04-28', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-28T10:18:00.000Z', '2025-04-28T10:18:00.000Z'
),
(
  'hist2025_pay_87d2a5b612b68e48', 'treatment', '5th Payment', '5th Payment', 'hist2025_306f96d3150e04a2', '9229963597', 'Kishanganj', 'BABLU HEMRAO',
  '2025-05-03', '1400', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-03T10:19:00.000Z', '2025-05-03T10:19:00.000Z'
),
(
  'hist2025_pay_52997d7b2ded2019', 'treatment', 'Advance', 'Advance', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-04-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-15T10:15:00.000Z', '2025-04-15T10:15:00.000Z'
),
(
  'hist2025_pay_a95eb7b42c145588', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-04-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-19T10:16:00.000Z', '2025-04-19T10:16:00.000Z'
),
(
  'hist2025_pay_8d7d26cb256db93c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-04-23', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:17:00.000Z', '2025-04-23T10:17:00.000Z'
),
(
  'hist2025_pay_648d89e939304209', 'treatment', '4th Payment', '4th Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-04-24', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-24T10:18:00.000Z', '2025-04-24T10:18:00.000Z'
),
(
  'hist2025_pay_3a66b41a10c9b77e', 'treatment', '5th Payment', '5th Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-04-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-29T10:19:00.000Z', '2025-04-29T10:19:00.000Z'
),
(
  'hist2025_pay_9c1eaf0db02e58d7', 'treatment', '6th Payment', '6th Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-05-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-05T10:20:00.000Z', '2025-05-05T10:20:00.000Z'
),
(
  'hist2025_pay_21b485e34331de51', 'treatment', '7th Payment', '7th Payment', 'hist2025_c87cdfd13034981f', '9798367794', 'Kishanganj', 'MANOJ KAMATI',
  '2025-05-14', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:21:00.000Z', '2025-05-14T10:21:00.000Z'
),
(
  'hist2025_pay_7cc427edb3d6d844', 'treatment', 'Advance', 'Advance', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-04-16', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-16T10:15:00.000Z', '2025-04-16T10:15:00.000Z'
),
(
  'hist2025_pay_dd281d619e28cf3a', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-04-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-19T10:16:00.000Z', '2025-04-19T10:16:00.000Z'
),
(
  'hist2025_pay_012a1c82ddde1a4f', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-04-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:17:00.000Z', '2025-04-23T10:17:00.000Z'
),
(
  'hist2025_pay_d2ec36e50e66a4ac', 'treatment', '4th Payment', '4th Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-04-26', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-26T10:18:00.000Z', '2025-04-26T10:18:00.000Z'
),
(
  'hist2025_pay_032bb78c92910108', 'treatment', '5th Payment', '5th Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-04-29', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-29T10:19:00.000Z', '2025-04-29T10:19:00.000Z'
),
(
  'hist2025_pay_c396b8c790da87d1', 'treatment', '6th Payment', '6th Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-05-07', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:20:00.000Z', '2025-05-07T10:20:00.000Z'
),
(
  'hist2025_pay_bed5b002439789ea', 'treatment', '7th Payment', '7th Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-05-14', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:21:00.000Z', '2025-05-14T10:21:00.000Z'
),
(
  'hist2025_pay_7bff0d5f40695440', 'treatment', '8th Payment', '8th Payment', 'hist2025_fabce8c86066732b', '9933905097', 'Kishanganj', 'HASINA KHATUN',
  '2025-05-21', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:22:00.000Z', '2025-05-21T10:22:00.000Z'
),
(
  'hist2025_pay_8d8023d58affcf14', 'treatment', 'Advance', 'Advance', 'hist2025_648d95fed60e8bae', '9430655565', 'Kishanganj', 'MD IBRAHIM',
  '2025-04-16', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-16T10:15:00.000Z', '2025-04-16T10:15:00.000Z'
),
(
  'hist2025_pay_d59c8d7784150376', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_648d95fed60e8bae', '9430655565', 'Kishanganj', 'MD IBRAHIM',
  '2025-04-19', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-19T10:16:00.000Z', '2025-04-19T10:16:00.000Z'
),
(
  'hist2025_pay_d0c13095c11cf614', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_648d95fed60e8bae', '9430655565', 'Kishanganj', 'MD IBRAHIM',
  '2025-05-07', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:17:00.000Z', '2025-05-07T10:17:00.000Z'
),
(
  'hist2025_pay_3d6b3129b43f409c', 'treatment', '4th Payment', '4th Payment', 'hist2025_648d95fed60e8bae', '9430655565', 'Kishanganj', 'MD IBRAHIM',
  '2025-05-21', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:18:00.000Z', '2025-05-21T10:18:00.000Z'
),
(
  'hist2025_pay_b6f253e000c02e67', 'treatment', 'Advance', 'Advance', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-04-21', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-21T10:15:00.000Z', '2025-04-21T10:15:00.000Z'
),
(
  'hist2025_pay_5345819adbf5a49c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-04-26', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-26T10:16:00.000Z', '2025-04-26T10:16:00.000Z'
),
(
  'hist2025_pay_38f03593edc5aed7', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-04-29', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-29T10:17:00.000Z', '2025-04-29T10:17:00.000Z'
),
(
  'hist2025_pay_6c9af18ce4d73a00', 'treatment', '4th Payment', '4th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-05-07', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:18:00.000Z', '2025-05-07T10:18:00.000Z'
),
(
  'hist2025_pay_ec9edcb08436b07c', 'treatment', '5th Payment', '5th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-05-14', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:19:00.000Z', '2025-05-14T10:19:00.000Z'
),
(
  'hist2025_pay_76a765d4b340279c', 'treatment', '6th Payment', '6th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-05-21', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:20:00.000Z', '2025-05-21T10:20:00.000Z'
),
(
  'hist2025_pay_e557036a3d312e75', 'treatment', '7th Payment', '7th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-05-28', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:21:00.000Z', '2025-05-28T10:21:00.000Z'
),
(
  'hist2025_pay_545489bd8875e159', 'treatment', '8th Payment', '8th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-06-04', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:22:00.000Z', '2025-06-04T10:22:00.000Z'
),
(
  'hist2025_pay_80c1ce10b521e71d', 'treatment', '9th Payment', '9th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-06-11', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:23:00.000Z', '2025-06-11T10:23:00.000Z'
),
(
  'hist2025_pay_948a2974161bb533', 'treatment', '10th Payment', '10th Payment', 'hist2025_91fb0a2f5913cddf', '9679788944', 'Kishanganj', 'TANVIR ALAM',
  '2025-06-18', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:24:00.000Z', '2025-06-18T10:24:00.000Z'
),
(
  'hist2025_pay_bd50611bd83b0a0b', 'treatment', 'Advance', 'Advance', 'hist2025_ce06c26454644bc9', '9348825003', 'Kishanganj', 'ANTIMA KUMARI',
  '2025-04-23', '600', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:15:00.000Z', '2025-04-23T10:15:00.000Z'
),
(
  'hist2025_pay_98c45e7e4b196187', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_ce06c26454644bc9', '9348825003', 'Kishanganj', 'ANTIMA KUMARI',
  '2025-04-26', '700', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-26T10:16:00.000Z', '2025-04-26T10:16:00.000Z'
),
(
  'hist2025_pay_76c5c2c10a7c5a8e', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_ce06c26454644bc9', '9348825003', 'Kishanganj', 'ANTIMA KUMARI',
  '2025-05-01', '5200', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-01T10:17:00.000Z', '2025-05-01T10:17:00.000Z'
),
(
  'hist2025_pay_29ae0a26de133da4', 'treatment', '4th Payment', '4th Payment', 'hist2025_ce06c26454644bc9', '9348825003', 'Kishanganj', 'ANTIMA KUMARI',
  '2025-05-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-03T10:18:00.000Z', '2025-05-03T10:18:00.000Z'
),
(
  'hist2025_pay_b8f76c6fda6976da', 'treatment', '5th Payment', '5th Payment', 'hist2025_ce06c26454644bc9', '9348825003', 'Kishanganj', 'ANTIMA KUMARI',
  '2025-05-07', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:19:00.000Z', '2025-05-07T10:19:00.000Z'
),
(
  'hist2025_pay_7c18ab86ee5c906c', 'treatment', 'Advance', 'Advance', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-04-23', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-23T10:15:00.000Z', '2025-04-23T10:15:00.000Z'
),
(
  'hist2025_pay_3de7473ccb03ae72', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-04-25', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-25T10:16:00.000Z', '2025-04-25T10:16:00.000Z'
),
(
  'hist2025_pay_70f36c73fb3b2259', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-04-30', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-04-30T10:17:00.000Z', '2025-04-30T10:17:00.000Z'
),
(
  'hist2025_pay_1c185ee0bdf2c106', 'treatment', '4th Payment', '4th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-05-07', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:18:00.000Z', '2025-05-07T10:18:00.000Z'
),
(
  'hist2025_pay_1e7ca73ec162467b', 'treatment', '5th Payment', '5th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-05-14', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:19:00.000Z', '2025-05-14T10:19:00.000Z'
),
(
  'hist2025_pay_330676eee36c5b3b', 'treatment', '6th Payment', '6th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-05-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-27T10:20:00.000Z', '2025-05-27T10:20:00.000Z'
),
(
  'hist2025_pay_90d5ae265118b200', 'treatment', '7th Payment', '7th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-06-04', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:21:00.000Z', '2025-06-04T10:21:00.000Z'
),
(
  'hist2025_pay_c4bf521b67e82543', 'treatment', '8th Payment', '8th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-06-11', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:22:00.000Z', '2025-06-11T10:22:00.000Z'
),
(
  'hist2025_pay_a45a52fe0d26080d', 'treatment', '9th Payment', '9th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-06-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-17T10:23:00.000Z', '2025-06-17T10:23:00.000Z'
),
(
  'hist2025_pay_d05f4daa2f5b47df', 'treatment', '10th Payment', '10th Payment', 'hist2025_71245e5a159e2bf3', '8617379288', 'Kishanganj', 'MANOJ KR GHOSH',
  '2025-09-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:24:00.000Z', '2025-09-17T10:24:00.000Z'
),
(
  'hist2025_pay_b3feea69987d22ed', 'treatment', 'Advance', 'Advance', 'hist2025_7e5997a5066627e2', '9006664849', 'Kishanganj', 'MONOHAR CHOUHAN',
  '2025-05-29', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-29T10:15:00.000Z', '2025-05-29T10:15:00.000Z'
),
(
  'hist2025_pay_a91f635381f5f810', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_7e5997a5066627e2', '9006664849', 'Kishanganj', 'MONOHAR CHOUHAN',
  '2025-06-04', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:16:00.000Z', '2025-06-04T10:16:00.000Z'
),
(
  'hist2025_pay_be981cd9fa55a60b', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_7e5997a5066627e2', '9006664849', 'Kishanganj', 'MONOHAR CHOUHAN',
  '2025-06-11', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:17:00.000Z', '2025-06-11T10:17:00.000Z'
),
(
  'hist2025_pay_451b4c10cb9be42f', 'treatment', '4th Payment', '4th Payment', 'hist2025_7e5997a5066627e2', '9006664849', 'Kishanganj', 'MONOHAR CHOUHAN',
  '2025-06-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-23T10:18:00.000Z', '2025-06-23T10:18:00.000Z'
),
(
  'hist2025_pay_e0476e3cc3fc61ed', 'treatment', 'Advance', 'Advance', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-02', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-02T10:15:00.000Z', '2025-05-02T10:15:00.000Z'
),
(
  'hist2025_pay_3957e2e4aa50f662', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-03', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-03T10:16:00.000Z', '2025-05-03T10:16:00.000Z'
),
(
  'hist2025_pay_d54cd64f4c50c669', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-07', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:17:00.000Z', '2025-05-07T10:17:00.000Z'
),
(
  'hist2025_pay_b10216eab73dd603', 'treatment', '4th Payment', '4th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-10', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-10T10:18:00.000Z', '2025-05-10T10:18:00.000Z'
),
(
  'hist2025_pay_78d6db7c79b2837b', 'treatment', '5th Payment', '5th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-14', '3500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:19:00.000Z', '2025-05-14T10:19:00.000Z'
),
(
  'hist2025_pay_8b8c67725e5d72d4', 'treatment', '6th Payment', '6th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:20:00.000Z', '2025-05-15T10:20:00.000Z'
),
(
  'hist2025_pay_14dc169ef0791625', 'treatment', '7th Payment', '7th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-17', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-17T10:21:00.000Z', '2025-05-17T10:21:00.000Z'
),
(
  'hist2025_pay_fc213e2adef7ce31', 'treatment', '8th Payment', '8th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-21', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:22:00.000Z', '2025-05-21T10:22:00.000Z'
),
(
  'hist2025_pay_9b15479b35946a5d', 'treatment', '9th Payment', '9th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-28', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:23:00.000Z', '2025-05-28T10:23:00.000Z'
),
(
  'hist2025_pay_7648ebe6acdb4441', 'treatment', '10th Payment', '10th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-05-31', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-31T10:24:00.000Z', '2025-05-31T10:24:00.000Z'
),
(
  'hist2025_pay_4dd726dd0443191b', 'treatment', '11th Payment', '11th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-06-11', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:25:00.000Z', '2025-06-11T10:25:00.000Z'
),
(
  'hist2025_pay_02966bcd6c77c7d7', 'treatment', '12th Payment', '12th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-06-19', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-19T10:26:00.000Z', '2025-06-19T10:26:00.000Z'
),
(
  'hist2025_pay_19faa0fae9dfd920', 'treatment', '13th Payment', '13th Payment', 'hist2025_f3e5a03776207506', '8580418375', 'Kishanganj', 'SOMIJA KHATOON',
  '2025-06-25', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-25T10:27:00.000Z', '2025-06-25T10:27:00.000Z'
),
(
  'hist2025_pay_c1943f7bf3b2edf5', 'treatment', 'Advance', 'Advance', 'hist2025_32c03339aaf38a5b', '9973867443', 'Kishanganj', 'RAHMAT ULLAH',
  '2025-05-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-05T10:15:00.000Z', '2025-05-05T10:15:00.000Z'
),
(
  'hist2025_pay_ff350cdb01157550', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_32c03339aaf38a5b', '9973867443', 'Kishanganj', 'RAHMAT ULLAH',
  '2025-05-15', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:16:00.000Z', '2025-05-15T10:16:00.000Z'
),
(
  'hist2025_pay_474d1fe196e5b091', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_32c03339aaf38a5b', '9973867443', 'Kishanganj', 'RAHMAT ULLAH',
  '2025-06-10', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-10T10:17:00.000Z', '2025-06-10T10:17:00.000Z'
),
(
  'hist2025_pay_3a1401eda7d6f06c', 'treatment', '4th Payment', '4th Payment', 'hist2025_32c03339aaf38a5b', '9973867443', 'Kishanganj', 'RAHMAT ULLAH',
  '2025-06-11', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:18:00.000Z', '2025-06-11T10:18:00.000Z'
),
(
  'hist2025_pay_91bf65664caf5137', 'treatment', '5th Payment', '5th Payment', 'hist2025_32c03339aaf38a5b', '9973867443', 'Kishanganj', 'RAHMAT ULLAH',
  '2025-06-18', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:19:00.000Z', '2025-06-18T10:19:00.000Z'
),
(
  'hist2025_pay_c373b2042d534d0f', 'treatment', 'Advance', 'Advance', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-05', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-05T10:15:00.000Z', '2025-05-05T10:15:00.000Z'
),
(
  'hist2025_pay_2d765b510afb9b3c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-07', '6500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:16:00.000Z', '2025-05-07T10:16:00.000Z'
),
(
  'hist2025_pay_0d384f3f9c1c025c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-10', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-10T10:17:00.000Z', '2025-05-10T10:17:00.000Z'
),
(
  'hist2025_pay_afeb9620c0206c1e', 'treatment', '4th Payment', '4th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:18:00.000Z', '2025-05-15T10:18:00.000Z'
),
(
  'hist2025_pay_f4c0c615386ca205', 'treatment', '5th Payment', '5th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-21', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:19:00.000Z', '2025-05-21T10:19:00.000Z'
),
(
  'hist2025_pay_4f3ed94055dcfb8f', 'treatment', '6th Payment', '6th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-05-28', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:20:00.000Z', '2025-05-28T10:20:00.000Z'
),
(
  'hist2025_pay_0ed583fef4f61a9f', 'treatment', '7th Payment', '7th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-06-04', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:21:00.000Z', '2025-06-04T10:21:00.000Z'
),
(
  'hist2025_pay_68131c7251c4c8aa', 'treatment', '8th Payment', '8th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-06-11', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:22:00.000Z', '2025-06-11T10:22:00.000Z'
),
(
  'hist2025_pay_6017c58ffe30121e', 'treatment', '9th Payment', '9th Payment', 'hist2025_0b4d6ea829565e33', '8348973365', 'Kishanganj', 'BIBI FARIDA KHATOON',
  '2025-08-26', '3500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-26T10:23:00.000Z', '2025-08-26T10:23:00.000Z'
),
(
  'hist2025_pay_4a3d2e754d085361', 'treatment', 'Advance', 'Advance', 'hist2025_16df3b3f2c2ecfaa', '8116291683', 'Kishanganj', 'ALARJAN KHATOON',
  '2025-05-06', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-06T10:15:00.000Z', '2025-05-06T10:15:00.000Z'
),
(
  'hist2025_pay_aaeb8e7e8ad36a39', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_16df3b3f2c2ecfaa', '8116291683', 'Kishanganj', 'ALARJAN KHATOON',
  '2025-05-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:16:00.000Z', '2025-05-14T10:16:00.000Z'
),
(
  'hist2025_pay_d23e95e74b0cb3e0', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_16df3b3f2c2ecfaa', '8116291683', 'Kishanganj', 'ALARJAN KHATOON',
  '2025-05-15', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:17:00.000Z', '2025-05-15T10:17:00.000Z'
),
(
  'hist2025_pay_d05989b4e828907b', 'treatment', '4th Payment', '4th Payment', 'hist2025_16df3b3f2c2ecfaa', '8116291683', 'Kishanganj', 'ALARJAN KHATOON',
  '2025-06-11', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:18:00.000Z', '2025-06-11T10:18:00.000Z'
),
(
  'hist2025_pay_5cf67c521a4b696d', 'treatment', 'Advance', 'Advance', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-07', '3500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-07T10:15:00.000Z', '2025-05-07T10:15:00.000Z'
),
(
  'hist2025_pay_ec2490d9c912bd41', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-10', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-10T10:16:00.000Z', '2025-05-10T10:16:00.000Z'
),
(
  'hist2025_pay_8394c69e091c6777', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-15', '6500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:17:00.000Z', '2025-05-15T10:17:00.000Z'
),
(
  'hist2025_pay_d2bdf8950129f2d6', 'treatment', '4th Payment', '4th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-17T10:18:00.000Z', '2025-05-17T10:18:00.000Z'
),
(
  'hist2025_pay_cc95426f6f195bf1', 'treatment', '5th Payment', '5th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-20', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-20T10:19:00.000Z', '2025-05-20T10:19:00.000Z'
),
(
  'hist2025_pay_2735e77ad737f636', 'treatment', '6th Payment', '6th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-24', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-24T10:20:00.000Z', '2025-05-24T10:20:00.000Z'
),
(
  'hist2025_pay_d0456689a271032c', 'treatment', '7th Payment', '7th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-05-31', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-31T10:21:00.000Z', '2025-05-31T10:21:00.000Z'
),
(
  'hist2025_pay_41ddcfbc641e8c69', 'treatment', '8th Payment', '8th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-06-04', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:22:00.000Z', '2025-06-04T10:22:00.000Z'
),
(
  'hist2025_pay_6e1cf5b4f61b3e82', 'treatment', '9th Payment', '9th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-06-11', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:23:00.000Z', '2025-06-11T10:23:00.000Z'
),
(
  'hist2025_pay_cfd6e259d53cadef', 'treatment', '10th Payment', '10th Payment', 'hist2025_bc2d80e597952916', '8509791198', 'Kishanganj', 'TAHERA BIBI',
  '2025-06-14', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:24:00.000Z', '2025-06-14T10:24:00.000Z'
),
(
  'hist2025_pay_3a5c248fcb5297bc', 'treatment', 'Advance', 'Advance', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-05-08', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-08T10:15:00.000Z', '2025-05-08T10:15:00.000Z'
),
(
  'hist2025_pay_eb40fef54e6fb020', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-05-10', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-10T10:16:00.000Z', '2025-05-10T10:16:00.000Z'
),
(
  'hist2025_pay_7ea96dbeecfb475a', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-05-14', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:17:00.000Z', '2025-05-14T10:17:00.000Z'
),
(
  'hist2025_pay_8c3040a33c9d8bd0', 'treatment', '4th Payment', '4th Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-05-21', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:18:00.000Z', '2025-05-21T10:18:00.000Z'
),
(
  'hist2025_pay_940cab1a6ec18096', 'treatment', '5th Payment', '5th Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-05-24', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-24T10:19:00.000Z', '2025-05-24T10:19:00.000Z'
),
(
  'hist2025_pay_4bcb17850b6cd360', 'treatment', '6th Payment', '6th Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-06-05', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-05T10:20:00.000Z', '2025-06-05T10:20:00.000Z'
),
(
  'hist2025_pay_46d5232c593959d2', 'treatment', '7th Payment', '7th Payment', 'hist2025_ddcbd67d9f7a0b90', '9971572893', 'Kishanganj', 'SAHALAM',
  '2025-06-14', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:21:00.000Z', '2025-06-14T10:21:00.000Z'
),
(
  'hist2025_pay_177c1f04e7691401', 'treatment', 'Advance', 'Advance', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-05-14', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-14T10:15:00.000Z', '2025-05-14T10:15:00.000Z'
),
(
  'hist2025_pay_00c40fd326151850', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-05-15', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-15T10:16:00.000Z', '2025-05-15T10:16:00.000Z'
),
(
  'hist2025_pay_74084d0869903bed', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-05-21', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-21T10:17:00.000Z', '2025-05-21T10:17:00.000Z'
),
(
  'hist2025_pay_1a59ace9a6e37856', 'treatment', '4th Payment', '4th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-05-24', '3500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-24T10:18:00.000Z', '2025-05-24T10:18:00.000Z'
),
(
  'hist2025_pay_b3cace17fd3f4d1a', 'treatment', '5th Payment', '5th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-05-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-29T10:19:00.000Z', '2025-05-29T10:19:00.000Z'
),
(
  'hist2025_pay_a3be15792f8d267d', 'treatment', '6th Payment', '6th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-06-06', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-06T10:20:00.000Z', '2025-06-06T10:20:00.000Z'
),
(
  'hist2025_pay_a3ecb3da0fe110f6', 'treatment', '7th Payment', '7th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-06-11', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:21:00.000Z', '2025-06-11T10:21:00.000Z'
),
(
  'hist2025_pay_5e93168674881a98', 'treatment', '8th Payment', '8th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-06-18', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:22:00.000Z', '2025-06-18T10:22:00.000Z'
),
(
  'hist2025_pay_c95eb04b8d305865', 'treatment', '9th Payment', '9th Payment', 'hist2025_39662572e0b76a0d', '7908907082', 'Kishanganj', 'INFIZ ALAM',
  '2025-11-15', '11000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:23:00.000Z', '2025-11-15T10:23:00.000Z'
),
(
  'hist2025_pay_06447cbd7191d38e', 'treatment', 'Advance', 'Advance', 'hist2025_4dae02a8960e7ab2', '6297977616', 'Kishanganj', 'ISKAT ALI',
  '2025-05-16', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-16T10:15:00.000Z', '2025-05-16T10:15:00.000Z'
),
(
  'hist2025_pay_003c0371f164f9a6', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4dae02a8960e7ab2', '6297977616', 'Kishanganj', 'ISKAT ALI',
  '2025-05-23', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-23T10:16:00.000Z', '2025-05-23T10:16:00.000Z'
),
(
  'hist2025_pay_0c9491f19640b867', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4dae02a8960e7ab2', '6297977616', 'Kishanganj', 'ISKAT ALI',
  '2025-05-30', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-30T10:17:00.000Z', '2025-05-30T10:17:00.000Z'
),
(
  'hist2025_pay_966370aaa96c6baa', 'treatment', 'Advance', 'Advance', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-05-18', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-18T10:15:00.000Z', '2025-05-18T10:15:00.000Z'
),
(
  'hist2025_pay_7de54c3773158a96', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-06-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:16:00.000Z', '2025-06-18T10:16:00.000Z'
),
(
  'hist2025_pay_3edc3ede2008d605', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-07-03', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-03T10:17:00.000Z', '2025-07-03T10:17:00.000Z'
),
(
  'hist2025_pay_9233159df44886c7', 'treatment', '4th Payment', '4th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-07-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:18:00.000Z', '2025-07-05T10:18:00.000Z'
),
(
  'hist2025_pay_c257a3d5e618160b', 'treatment', '5th Payment', '5th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-07-16', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:19:00.000Z', '2025-07-16T10:19:00.000Z'
),
(
  'hist2025_pay_997814a02f38e315', 'treatment', '6th Payment', '6th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-07-23', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:20:00.000Z', '2025-07-23T10:20:00.000Z'
),
(
  'hist2025_pay_b0a99823f10dd887', 'treatment', '7th Payment', '7th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-08-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:21:00.000Z', '2025-08-13T10:21:00.000Z'
),
(
  'hist2025_pay_041a97f60b38873a', 'treatment', '8th Payment', '8th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-09-24', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:22:00.000Z', '2025-09-24T10:22:00.000Z'
),
(
  'hist2025_pay_a1a472b896f1cd72', 'treatment', '9th Payment', '9th Payment', 'hist2025_db59ea44d16b434f', '9733190879', 'Kishanganj', 'SAFALI DAS',
  '2025-11-05', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:23:00.000Z', '2025-11-05T10:23:00.000Z'
),
(
  'hist2025_pay_c9c7b95c7f4fcaeb', 'treatment', 'Advance', 'Advance', 'hist2025_abc89c9aac3990eb', '6295680491', 'Kishanganj', 'SALMAN KHAN',
  '2025-05-20', '25000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-20T10:15:00.000Z', '2025-05-20T10:15:00.000Z'
),
(
  'hist2025_pay_553af80398bfa304', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_abc89c9aac3990eb', '6295680491', 'Kishanganj', 'SALMAN KHAN',
  '2025-06-19', '15000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-19T10:16:00.000Z', '2025-06-19T10:16:00.000Z'
),
(
  'hist2025_pay_0effd508c211d8c7', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_abc89c9aac3990eb', '6295680491', 'Kishanganj', 'SALMAN KHAN',
  '2025-07-14', '35000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-14T10:17:00.000Z', '2025-07-14T10:17:00.000Z'
),
(
  'hist2025_pay_b28d2fd705b03034', 'treatment', 'Advance', 'Advance', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-05-22', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-22T10:15:00.000Z', '2025-05-22T10:15:00.000Z'
),
(
  'hist2025_pay_4ebb70d673392bcd', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-05-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-25T10:16:00.000Z', '2025-05-25T10:16:00.000Z'
),
(
  'hist2025_pay_7dc4f1a6f88e156b', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-05-28', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-05-28T10:17:00.000Z', '2025-05-28T10:17:00.000Z'
),
(
  'hist2025_pay_92927a8cf893d9e2', 'treatment', '4th Payment', '4th Payment', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-06-11', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:18:00.000Z', '2025-06-11T10:18:00.000Z'
),
(
  'hist2025_pay_14a77d1e9242681e', 'treatment', '5th Payment', '5th Payment', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-06-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:19:00.000Z', '2025-06-18T10:19:00.000Z'
),
(
  'hist2025_pay_feceef147e023d2e', 'treatment', '6th Payment', '6th Payment', 'hist2025_b296a5837e432655', '7602934215', 'Kishanganj', 'BINOD KUMAR GHOSE',
  '2025-07-02', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:20:00.000Z', '2025-07-02T10:20:00.000Z'
),
(
  'hist2025_pay_3aa7618cc7c3e053', 'treatment', 'Advance', 'Advance', 'hist2025_b7b141bc7a2247af', '6294977796', 'Kishanganj', 'SUFAL SAREN',
  '2025-06-02', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-02T10:15:00.000Z', '2025-06-02T10:15:00.000Z'
),
(
  'hist2025_pay_a1b6060a9d82496b', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_b7b141bc7a2247af', '6294977796', 'Kishanganj', 'SUFAL SAREN',
  '2025-06-04', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-04T10:16:00.000Z', '2025-06-04T10:16:00.000Z'
),
(
  'hist2025_pay_01a3b6aeac09b9e6', 'treatment', 'Advance', 'Advance', 'hist2025_12be63dff54f7c6d', '8877244718', 'Kishanganj', 'AKHILESH MALAKAR',
  '2025-06-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-02T10:15:00.000Z', '2025-06-02T10:15:00.000Z'
),
(
  'hist2025_pay_ee9e1b0a5ab803cc', 'treatment', 'Advance', 'Advance', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-05', '15000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-05T10:15:00.000Z', '2025-06-05T10:15:00.000Z'
),
(
  'hist2025_pay_1a2d8b2c9aeadb62', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-11', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:16:00.000Z', '2025-06-11T10:16:00.000Z'
),
(
  'hist2025_pay_dc187374b4be1784', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-14', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:17:00.000Z', '2025-06-14T10:17:00.000Z'
),
(
  'hist2025_pay_b5e75ed13fc017a7', 'treatment', '4th Payment', '4th Payment', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-18', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:18:00.000Z', '2025-06-18T10:18:00.000Z'
),
(
  'hist2025_pay_b233b234bf0c3593', 'treatment', '5th Payment', '5th Payment', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-21', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-21T10:19:00.000Z', '2025-06-21T10:19:00.000Z'
),
(
  'hist2025_pay_38861f65c30d5ed1', 'treatment', '6th Payment', '6th Payment', 'hist2025_32b47085efc7d37a', '9382409362', 'Kishanganj', 'MANIK SINGHA',
  '2025-06-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-23T10:20:00.000Z', '2025-06-23T10:20:00.000Z'
),
(
  'hist2025_pay_b4e8abea20f591ae', 'treatment', 'Advance', 'Advance', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-06', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-06T10:15:00.000Z', '2025-06-06T10:15:00.000Z'
),
(
  'hist2025_pay_2e5458a5659d933c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-11', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-11T10:16:00.000Z', '2025-06-11T10:16:00.000Z'
),
(
  'hist2025_pay_fdcfb36d4562153c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-14', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:17:00.000Z', '2025-06-14T10:17:00.000Z'
),
(
  'hist2025_pay_056d9323e507c52c', 'treatment', '4th Payment', '4th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-18', '6500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:18:00.000Z', '2025-06-18T10:18:00.000Z'
),
(
  'hist2025_pay_1425fb3ddbac4943', 'treatment', '5th Payment', '5th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-21', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-21T10:19:00.000Z', '2025-06-21T10:19:00.000Z'
),
(
  'hist2025_pay_ef532d981715cf09', 'treatment', '6th Payment', '6th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-25', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-25T10:20:00.000Z', '2025-06-25T10:20:00.000Z'
),
(
  'hist2025_pay_e330c55ce3beb912', 'treatment', '7th Payment', '7th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-06-28', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-28T10:21:00.000Z', '2025-06-28T10:21:00.000Z'
),
(
  'hist2025_pay_ceba96ce2d6ad60e', 'treatment', '8th Payment', '8th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-07-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:22:00.000Z', '2025-07-02T10:22:00.000Z'
),
(
  'hist2025_pay_99260382981f1ec7', 'treatment', '9th Payment', '9th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-07-10', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-10T10:23:00.000Z', '2025-07-10T10:23:00.000Z'
),
(
  'hist2025_pay_e35d4905f1675a3e', 'treatment', '10th Payment', '10th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-07-16', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:24:00.000Z', '2025-07-16T10:24:00.000Z'
),
(
  'hist2025_pay_9542fe7e38b27df7', 'treatment', '11th Payment', '11th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-07-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:25:00.000Z', '2025-07-23T10:25:00.000Z'
),
(
  'hist2025_pay_6aa0c736fa5b6076', 'treatment', '12th Payment', '12th Payment', 'hist2025_8f481f48e8b4d3b7', '9934637437', 'Kishanganj', 'MD DULAL',
  '2025-07-31', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-31T10:26:00.000Z', '2025-07-31T10:26:00.000Z'
),
(
  'hist2025_pay_10576935df966749', 'treatment', 'Advance', 'Advance', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-12T10:15:00.000Z', '2025-06-12T10:15:00.000Z'
),
(
  'hist2025_pay_89297204e8754c3c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:16:00.000Z', '2025-06-14T10:16:00.000Z'
),
(
  'hist2025_pay_95e2b33c9d46e00c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-18', '25000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:17:00.000Z', '2025-06-18T10:17:00.000Z'
),
(
  'hist2025_pay_cfd35663e33ccec1', 'treatment', '4th Payment', '4th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-21', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-21T10:18:00.000Z', '2025-06-21T10:18:00.000Z'
),
(
  'hist2025_pay_213aef2b2fe688b6', 'treatment', '5th Payment', '5th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-25', '13000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-25T10:19:00.000Z', '2025-06-25T10:19:00.000Z'
),
(
  'hist2025_pay_08a1c9c0d2df8db8', 'treatment', '6th Payment', '6th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-06-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-27T10:20:00.000Z', '2025-06-27T10:20:00.000Z'
),
(
  'hist2025_pay_32184ed4f959c2d6', 'treatment', '7th Payment', '7th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-07-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:21:00.000Z', '2025-07-05T10:21:00.000Z'
),
(
  'hist2025_pay_5ca7b37c336bc7d5', 'treatment', '8th Payment', '8th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-07-09', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:22:00.000Z', '2025-07-09T10:22:00.000Z'
),
(
  'hist2025_pay_38692a0675ff71cd', 'treatment', '9th Payment', '9th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-07-16', '13000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:23:00.000Z', '2025-07-16T10:23:00.000Z'
),
(
  'hist2025_pay_3124a0353b9ce4c3', 'treatment', '10th Payment', '10th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-07-22', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-22T10:24:00.000Z', '2025-07-22T10:24:00.000Z'
),
(
  'hist2025_pay_4a4c24226b455876', 'treatment', '11th Payment', '11th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:25:00.000Z', '2025-07-30T10:25:00.000Z'
),
(
  'hist2025_pay_db0e5baa7777945f', 'treatment', '12th Payment', '12th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-08-06', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:26:00.000Z', '2025-08-06T10:26:00.000Z'
),
(
  'hist2025_pay_3c0a9a0870eed1bd', 'treatment', '13th Payment', '13th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-08-11', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-11T10:27:00.000Z', '2025-08-11T10:27:00.000Z'
),
(
  'hist2025_pay_f20e2be58c6d70ba', 'treatment', '14th Payment', '14th Payment', 'hist2025_3da8b1f7abd5303e', '8925261679', 'Kishanganj', 'NAJIMUL HODA',
  '2025-09-03', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:28:00.000Z', '2025-09-03T10:28:00.000Z'
),
(
  'hist2025_pay_8bb6e3a8afe2bd5d', 'treatment', 'Advance', 'Advance', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-06-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-14T10:15:00.000Z', '2025-06-14T10:15:00.000Z'
),
(
  'hist2025_pay_7db3671eee87d327', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-06-18', '15000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-18T10:16:00.000Z', '2025-06-18T10:16:00.000Z'
),
(
  'hist2025_pay_9107d6c4470493f0', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-06-21', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-21T10:17:00.000Z', '2025-06-21T10:17:00.000Z'
),
(
  'hist2025_pay_ad857e2db44cec13', 'treatment', '4th Payment', '4th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-06-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-25T10:18:00.000Z', '2025-06-25T10:18:00.000Z'
),
(
  'hist2025_pay_2c2f4a2b2974cefa', 'treatment', '5th Payment', '5th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:19:00.000Z', '2025-07-05T10:19:00.000Z'
),
(
  'hist2025_pay_3a3c63622311a474', 'treatment', '6th Payment', '6th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:20:00.000Z', '2025-07-09T10:20:00.000Z'
),
(
  'hist2025_pay_d22cfcb6409feb65', 'treatment', '7th Payment', '7th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-10', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-10T10:21:00.000Z', '2025-07-10T10:21:00.000Z'
),
(
  'hist2025_pay_117212318d93a431', 'treatment', '8th Payment', '8th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-12', '4500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-12T10:22:00.000Z', '2025-07-12T10:22:00.000Z'
),
(
  'hist2025_pay_03f8529b0c431529', 'treatment', '9th Payment', '9th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-15T10:23:00.000Z', '2025-07-15T10:23:00.000Z'
),
(
  'hist2025_pay_3066b2eb855b7fca', 'treatment', '10th Payment', '10th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-18', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-18T10:24:00.000Z', '2025-07-18T10:24:00.000Z'
),
(
  'hist2025_pay_86dd15e22b663126', 'treatment', '11th Payment', '11th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-23', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:25:00.000Z', '2025-07-23T10:25:00.000Z'
),
(
  'hist2025_pay_e7b88e123409d047', 'treatment', '12th Payment', '12th Payment', 'hist2025_9db0d658b8405373', '9631935125', 'Kishanganj', 'KRISHNA KARMAKAR',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:26:00.000Z', '2025-07-30T10:26:00.000Z'
),
(
  'hist2025_pay_3a6c1b15ac1544f9', 'treatment', 'Advance', 'Advance', 'hist2025_791cbf75a06452fa', '8789193154', 'Kishanganj', 'SUMAN KUMARI',
  '2025-06-20', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-20T10:15:00.000Z', '2025-06-20T10:15:00.000Z'
),
(
  'hist2025_pay_01cbf95aeaa51da0', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_791cbf75a06452fa', '8789193154', 'Kishanganj', 'SUMAN KUMARI',
  '2025-06-25', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-25T10:16:00.000Z', '2025-06-25T10:16:00.000Z'
),
(
  'hist2025_pay_ca7813993326c441', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_791cbf75a06452fa', '8789193154', 'Kishanganj', 'SUMAN KUMARI',
  '2025-07-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:17:00.000Z', '2025-07-02T10:17:00.000Z'
),
(
  'hist2025_pay_6fd81e85e94c1515', 'treatment', '4th Payment', '4th Payment', 'hist2025_791cbf75a06452fa', '8789193154', 'Kishanganj', 'SUMAN KUMARI',
  '2025-07-16', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:18:00.000Z', '2025-07-16T10:18:00.000Z'
),
(
  'hist2025_pay_cb4bbe5ed4f41a02', 'treatment', '5th Payment', '5th Payment', 'hist2025_791cbf75a06452fa', '8789193154', 'Kishanganj', 'SUMAN KUMARI',
  '2025-07-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-20T10:19:00.000Z', '2025-07-20T10:19:00.000Z'
),
(
  'hist2025_pay_f02fa4e78bde530e', 'treatment', 'Advance', 'Advance', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-07-23', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:15:00.000Z', '2025-07-23T10:15:00.000Z'
),
(
  'hist2025_pay_7c33ba82b8fffdd2', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-07-27', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-27T10:16:00.000Z', '2025-07-27T10:16:00.000Z'
),
(
  'hist2025_pay_feebf71f2814c115', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-02', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:17:00.000Z', '2025-08-02T10:17:00.000Z'
),
(
  'hist2025_pay_f4f63a19effc8114', 'treatment', '4th Payment', '4th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-05', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-05T10:18:00.000Z', '2025-08-05T10:18:00.000Z'
),
(
  'hist2025_pay_63f9f4d413eafb8e', 'treatment', '5th Payment', '5th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-09', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-09T10:19:00.000Z', '2025-08-09T10:19:00.000Z'
),
(
  'hist2025_pay_16765c188ca1d42f', 'treatment', '6th Payment', '6th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-12', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-12T10:20:00.000Z', '2025-08-12T10:20:00.000Z'
),
(
  'hist2025_pay_451249c190fd7876', 'treatment', '7th Payment', '7th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-16', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-16T10:21:00.000Z', '2025-08-16T10:21:00.000Z'
),
(
  'hist2025_pay_d7c227d6f1803c41', 'treatment', '8th Payment', '8th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-02', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:22:00.000Z', '2025-08-02T10:22:00.000Z'
),
(
  'hist2025_pay_efb0431edb14dead', 'treatment', '9th Payment', '9th Payment', 'hist2025_d157c5df6e0bcd89', '8848852354', 'Kishanganj', 'SAHAJADI',
  '2025-08-29', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-29T10:23:00.000Z', '2025-08-29T10:23:00.000Z'
),
(
  'hist2025_pay_64ac82cff21d52fe', 'treatment', 'Advance', 'Advance', 'hist2025_8288043e3779e060', '9733243745', 'Kishanganj', 'ANBAR HOUSSIN',
  '2025-06-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-22T10:15:00.000Z', '2025-06-22T10:15:00.000Z'
),
(
  'hist2025_pay_28c6831e912cce94', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_8288043e3779e060', '9733243745', 'Kishanganj', 'ANBAR HOUSSIN',
  '2025-06-24', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-24T10:16:00.000Z', '2025-06-24T10:16:00.000Z'
),
(
  'hist2025_pay_217fbaf573abf4f3', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_8288043e3779e060', '9733243745', 'Kishanganj', 'ANBAR HOUSSIN',
  '2025-07-04', '3900', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-04T10:17:00.000Z', '2025-07-04T10:17:00.000Z'
),
(
  'hist2025_pay_370acf278f9a4c78', 'treatment', 'Advance', 'Advance', 'hist2025_0db30041f0978ac3', '9391276598', 'Kishanganj', 'SAHABAZ ALAM',
  '2025-06-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-23T10:15:00.000Z', '2025-06-23T10:15:00.000Z'
),
(
  'hist2025_pay_80a22b01fd7c460f', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0db30041f0978ac3', '9391276598', 'Kishanganj', 'SAHABAZ ALAM',
  '2025-06-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-05T10:16:00.000Z', '2025-06-05T10:16:00.000Z'
),
(
  'hist2025_pay_3a3d94684a2c3847', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0db30041f0978ac3', '9391276598', 'Kishanganj', 'SAHABAZ ALAM',
  '2025-07-09', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:17:00.000Z', '2025-07-09T10:17:00.000Z'
),
(
  'hist2025_pay_55e2004f61fa79fb', 'treatment', '4th Payment', '4th Payment', 'hist2025_0db30041f0978ac3', '9391276598', 'Kishanganj', 'SAHABAZ ALAM',
  '2025-07-25', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-25T10:18:00.000Z', '2025-07-25T10:18:00.000Z'
),
(
  'hist2025_pay_6c3f29f5d7e7374d', 'treatment', '5th Payment', '5th Payment', 'hist2025_0db30041f0978ac3', '9391276598', 'Kishanganj', 'SAHABAZ ALAM',
  '2025-08-13', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:19:00.000Z', '2025-08-13T10:19:00.000Z'
),
(
  'hist2025_pay_fe61652f107ad0c0', 'treatment', 'Advance', 'Advance', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-06-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-26T10:15:00.000Z', '2025-06-26T10:15:00.000Z'
),
(
  'hist2025_pay_77b4c14f1c1c4d83', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:16:00.000Z', '2025-07-02T10:16:00.000Z'
),
(
  'hist2025_pay_9b44a4707458f50a', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:17:00.000Z', '2025-07-05T10:17:00.000Z'
),
(
  'hist2025_pay_21cf44d6ab36ecf2', 'treatment', '4th Payment', '4th Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:18:00.000Z', '2025-07-09T10:18:00.000Z'
),
(
  'hist2025_pay_e46dfbdfd3911e53', 'treatment', '5th Payment', '5th Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-15T10:19:00.000Z', '2025-07-15T10:19:00.000Z'
),
(
  'hist2025_pay_871e51e21c85e7ee', 'treatment', '6th Payment', '6th Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-19', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-19T10:20:00.000Z', '2025-07-19T10:20:00.000Z'
),
(
  'hist2025_pay_fb9dd5a2212b9cab', 'treatment', '7th Payment', '7th Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:21:00.000Z', '2025-07-23T10:21:00.000Z'
),
(
  'hist2025_pay_dcf9219b9ba3cd62', 'treatment', '8th Payment', '8th Payment', 'hist2025_d5a3c51145e1759c', '8146078470', 'Kishanganj', 'MAHAMAD MAHFUJ ALAM',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:22:00.000Z', '2025-07-30T10:22:00.000Z'
),
(
  'hist2025_pay_4da8128f02bacd5c', 'treatment', 'Advance', 'Advance', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-06-27', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-06-27T10:15:00.000Z', '2025-06-27T10:15:00.000Z'
),
(
  'hist2025_pay_6d3ae265da19c346', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-07-02', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:16:00.000Z', '2025-07-02T10:16:00.000Z'
),
(
  'hist2025_pay_d2f9dc3269789724', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-07-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:17:00.000Z', '2025-07-05T10:17:00.000Z'
),
(
  'hist2025_pay_b0d0392491b4b980', 'treatment', '4th Payment', '4th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-07-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-12T10:18:00.000Z', '2025-07-12T10:18:00.000Z'
),
(
  'hist2025_pay_c901dae909ec6c92', 'treatment', '5th Payment', '5th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-07-16', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:19:00.000Z', '2025-07-16T10:19:00.000Z'
),
(
  'hist2025_pay_02053c598631942b', 'treatment', '6th Payment', '6th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-07-23', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:20:00.000Z', '2025-07-23T10:20:00.000Z'
),
(
  'hist2025_pay_9b150c22f9e4b6ed', 'treatment', '7th Payment', '7th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-08-12', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-12T10:21:00.000Z', '2025-08-12T10:21:00.000Z'
),
(
  'hist2025_pay_b6aaae12c3dc3c38', 'treatment', '8th Payment', '8th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-08-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-20T10:22:00.000Z', '2025-08-20T10:22:00.000Z'
),
(
  'hist2025_pay_31eb565a8e66eb40', 'treatment', '9th Payment', '9th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-08-23', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-23T10:23:00.000Z', '2025-08-23T10:23:00.000Z'
),
(
  'hist2025_pay_c65c601ee4952669', 'treatment', '10th Payment', '10th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-09-03', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:24:00.000Z', '2025-09-03T10:24:00.000Z'
),
(
  'hist2025_pay_c6e6cd85586cf1f5', 'treatment', '11th Payment', '11th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-09-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:25:00.000Z', '2025-09-20T10:25:00.000Z'
),
(
  'hist2025_pay_2997e41191a0f532', 'treatment', '12th Payment', '12th Payment', 'hist2025_4ddd27499458b346', '8809705409', 'Kishanganj', 'MOHAMAD CHAND',
  '2025-09-24', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:26:00.000Z', '2025-09-24T10:26:00.000Z'
),
(
  'hist2025_pay_86d0dffd314b4bd1', 'treatment', 'Advance', 'Advance', 'hist2025_10d9644e504705ff', '9103252865', 'Kishanganj', 'ARSHAD NAIK',
  '2025-07-02', '10000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:15:00.000Z', '2025-07-02T10:15:00.000Z'
),
(
  'hist2025_pay_2dee319c6cf3d800', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_10d9644e504705ff', '9103252865', 'Kishanganj', 'ARSHAD NAIK',
  '2025-07-02', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-02T10:16:00.000Z', '2025-07-02T10:16:00.000Z'
),
(
  'hist2025_pay_642e4fbd7f6daf17', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_10d9644e504705ff', '9103252865', 'Kishanganj', 'ARSHAD NAIK',
  '2025-08-01', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-01T10:17:00.000Z', '2025-08-01T10:17:00.000Z'
),
(
  'hist2025_pay_1b1e03ee06a1add8', 'treatment', 'Advance', 'Advance', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-05T10:15:00.000Z', '2025-07-05T10:15:00.000Z'
),
(
  'hist2025_pay_ee6690c1050b5c16', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-09', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:16:00.000Z', '2025-07-09T10:16:00.000Z'
),
(
  'hist2025_pay_28ba7ff2c1074368', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-14', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-14T10:17:00.000Z', '2025-07-14T10:17:00.000Z'
),
(
  'hist2025_pay_b42eace1d468f126', 'treatment', '4th Payment', '4th Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-19', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-19T10:18:00.000Z', '2025-07-19T10:18:00.000Z'
),
(
  'hist2025_pay_a043f41a05980d77', 'treatment', '5th Payment', '5th Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:19:00.000Z', '2025-07-23T10:19:00.000Z'
),
(
  'hist2025_pay_84e570820173384c', 'treatment', '6th Payment', '6th Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-07-28', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-28T10:20:00.000Z', '2025-07-28T10:20:00.000Z'
),
(
  'hist2025_pay_fb8ef5eddd0e50bb', 'treatment', '7th Payment', '7th Payment', 'hist2025_f367f41b21d311a4', '7061321194', 'Kishanganj', 'MD SAHID',
  '2025-08-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:21:00.000Z', '2025-08-06T10:21:00.000Z'
),
(
  'hist2025_pay_ab082a0e683747bd', 'treatment', 'Advance', 'Advance', 'hist2025_35ae1d6fe56dc53f', '8822702712', 'Kishanganj', 'SUNIL JAIN',
  '2025-07-07', '8000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-07T10:15:00.000Z', '2025-07-07T10:15:00.000Z'
),
(
  'hist2025_pay_0f20b7ac9eada31c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_35ae1d6fe56dc53f', '8822702712', 'Kishanganj', 'SUNIL JAIN',
  '2025-07-08', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-08T10:16:00.000Z', '2025-07-08T10:16:00.000Z'
),
(
  'hist2025_pay_0c3ba2333807c1d6', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_35ae1d6fe56dc53f', '8822702712', 'Kishanganj', 'SUNIL JAIN',
  '2025-07-10', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-10T10:17:00.000Z', '2025-07-10T10:17:00.000Z'
),
(
  'hist2025_pay_dfa3360d0635fc8a', 'treatment', 'Advance', 'Advance', 'hist2025_9e912a547990c596', '8884841062', 'Kishanganj', 'LOVELY RANI',
  '2025-07-07', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-07T10:15:00.000Z', '2025-07-07T10:15:00.000Z'
),
(
  'hist2025_pay_e14de32dbce9f33e', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_9e912a547990c596', '8884841062', 'Kishanganj', 'LOVELY RANI',
  '2025-07-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:16:00.000Z', '2025-07-09T10:16:00.000Z'
),
(
  'hist2025_pay_51fe5dd76e72149d', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_9e912a547990c596', '8884841062', 'Kishanganj', 'LOVELY RANI',
  '2025-07-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-12T10:17:00.000Z', '2025-07-12T10:17:00.000Z'
),
(
  'hist2025_pay_b3c7c2a3279795be', 'treatment', '4th Payment', '4th Payment', 'hist2025_9e912a547990c596', '8884841062', 'Kishanganj', 'LOVELY RANI',
  '2025-07-16', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-16T10:18:00.000Z', '2025-07-16T10:18:00.000Z'
),
(
  'hist2025_pay_bff4e7cb4a1de378', 'treatment', 'Advance', 'Advance', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:15:00.000Z', '2025-07-09T10:15:00.000Z'
),
(
  'hist2025_pay_75d0188461c537ac', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-12T10:16:00.000Z', '2025-07-12T10:16:00.000Z'
),
(
  'hist2025_pay_1acb7a7cea47ac6d', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-18', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-18T10:17:00.000Z', '2025-07-18T10:17:00.000Z'
),
(
  'hist2025_pay_6a6e55a3b752ace4', 'treatment', '4th Payment', '4th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:18:00.000Z', '2025-07-23T10:18:00.000Z'
),
(
  'hist2025_pay_074a0339fa93fdec', 'treatment', '5th Payment', '5th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-26', '11000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-26T10:19:00.000Z', '2025-07-26T10:19:00.000Z'
),
(
  'hist2025_pay_51bbf1488b08ee98', 'treatment', '6th Payment', '6th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-07-30', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:20:00.000Z', '2025-07-30T10:20:00.000Z'
),
(
  'hist2025_pay_423889846855be35', 'treatment', '7th Payment', '7th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-08-06', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:21:00.000Z', '2025-08-06T10:21:00.000Z'
),
(
  'hist2025_pay_bff710e373775772', 'treatment', '8th Payment', '8th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-08-13', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:22:00.000Z', '2025-08-13T10:22:00.000Z'
),
(
  'hist2025_pay_dc083bbba3bdb114', 'treatment', '9th Payment', '9th Payment', 'hist2025_7c49eacd0e2ac5a7', '7908231003', 'Kishanganj', 'MARJINA',
  '2025-08-30', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-30T10:23:00.000Z', '2025-08-30T10:23:00.000Z'
),
(
  'hist2025_pay_580b7a9015843463', 'treatment', 'Advance', 'Advance', 'hist2025_293c0dff82e9c675', '8016775777', 'Kishanganj', 'CHADNI',
  '2025-07-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-09T10:15:00.000Z', '2025-07-09T10:15:00.000Z'
);
