-- ২০২৫ শিট ধাপ ৪ -- ব্যাচ 2/2 (রোগী 55-107, মোট 53)
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
  'hist2025_c823748994221ff7', 'KNE-14072025-001', '2025-07-14', '2025-07-14', '2025-07-14',
  'HAKIM UDDIN', '8391991430', '', 'Kishanganj', '18', 'Male',
  'UTTAR DUMORIYA, THAKURBARI, CHAKULIYA, U.D', 'Other', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-14T10:00:00.000Z', '2025-07-14T10:00:00.000Z'
),
(
  'hist2025_3f830b538d44602f', 'KNE-17072025-001', '2025-07-17', '2025-07-17', '2025-07-17',
  'MD SAYED', '8089107966', '', 'Kishanganj', '28', 'Male',
  'CHOURAGACHH, GOTI, GOYALPOKHAR, U.D', 'Piles', '36000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-17T10:00:00.000Z', '2025-07-17T10:00:00.000Z'
),
(
  'hist2025_b3fd6639ece7bc72', 'KNE-17072025-002', '2025-07-17', '2025-07-17', '2025-07-17',
  'SAHANOR KHATOON', '8967399644', '', 'Kishanganj', '22', 'Female',
  'GOYALPOKHAR, LAL KHURI, GOYALPOKHAR, U.D', 'Fistula', '36000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-17T10:00:00.000Z', '2025-07-17T10:00:00.000Z'
),
(
  'hist2025_3e476c864c02cd0d', 'KNE-22072025-001', '2025-07-22', '2025-07-22', '2025-07-22',
  'HASAN ALI', '7872766362', '', 'Kishanganj', '32', 'Male',
  'CHAKULIYA, CHAKULIYA, CHAKULIYA, U.D', 'Other', '45000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-22T10:00:00.000Z', '2025-07-22T10:00:00.000Z'
),
(
  'hist2025_f22f28d45b7e5b6b', 'KNE-23072025-001', '2025-07-23', '2025-07-23', '2025-07-23',
  'FARHA PARVIN', '7865803067', '', 'Kishanganj', '18', 'Female',
  'BASTADANGI, SUMALIA, DALKHOLA, U. D', 'Piles', '24000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:00:00.000Z', '2025-07-23T10:00:00.000Z'
),
(
  'hist2025_4daaef4c04ad44ce', 'KNE-24072025-001', '2025-07-24', '2025-07-24', '2025-07-24',
  'MD SAKIL', '7888475776', '', 'Kishanganj', '27', 'Male',
  'GHORA, GHORA, Goyalpokhar, U. D', 'Fistula', '97500',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-24T10:00:00.000Z', '2025-07-24T10:00:00.000Z'
),
(
  'hist2025_d755c537eebc155c', 'KNE-29072025-001', '2025-07-29', '2025-07-29', '2025-07-29',
  'MAIMUL HOWK', '7009135532', '', 'Kishanganj', '26', 'Male',
  'CHURRAKUTTI, GHORRA, GOYALPOKHAR, U.D', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-29T10:00:00.000Z', '2025-07-29T10:00:00.000Z'
),
(
  'hist2025_78bdba7af4d2b9cb', 'KNE-08082025-001', '2025-08-08', '2025-08-08', '2025-08-08',
  'CHOTU MARDIN', '9907652834', '', 'Kishanganj', '45', 'Male',
  'DEUGOU, MUNDES, CHAKULIYA, U.D', 'Piles', '115000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-08T10:00:00.000Z', '2025-08-08T10:00:00.000Z'
),
(
  'hist2025_a1ab6455b6f93caa', 'KNE-21082025-001', '2025-08-21', '2025-08-21', '2025-08-21',
  'TARAFUL KHATOON', '9296362956', '', 'Kishanganj', '25', 'Female',
  'LILIYA CHOWK, MEHERGANCH, BAHADURGANJ, KISHANGANJ', 'Piles', '36000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-21T10:00:00.000Z', '2025-08-21T10:00:00.000Z'
),
(
  'hist2025_51de98e074702388', 'KNE-21082025-002', '2025-08-21', '2025-08-21', '2025-08-21',
  'NUSRAT JAHA', '9973609230', '', 'Kishanganj', '35', 'Male',
  'AMABOSE, HAPHONIYA, AMOU, PIURNIYA', 'Fistula', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-21T10:00:00.000Z', '2025-08-21T10:00:00.000Z'
),
(
  'hist2025_12561fcb3692e9f1', 'KNE-27082025-001', '2025-08-27', '2025-08-27', '2025-08-27',
  'KALACHAN SARKAR', '7872821364', '', 'Kishanganj', '53', 'Male',
  'HASAN, HASAN, DALKHOLA, U.D', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:00:00.000Z', '2025-08-27T10:00:00.000Z'
),
(
  'hist2025_d6581689e9f2d7eb', 'KNE-08092025-001', '2025-09-08', '2025-09-08', '2025-09-08',
  'GANESH MONDAL', '8918318739', '', 'Kishanganj', '40', 'Male',
  'KANKI, MAJLESHPUR, CHAKULIYA, U.D', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-08T10:00:00.000Z', '2025-09-08T10:00:00.000Z'
),
(
  'hist2025_d81645626033f40d', 'KNE-08092025-002', '2025-09-08', '2025-09-08', '2025-09-08',
  'MD BABUL', '7991127657', '', 'Kishanganj', '40', 'Male',
  'BARIYA MALA BASTI, BARIYA, PAHARKHATTA, KISHANGANJ', 'Fistula', '115000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-08T10:00:00.000Z', '2025-09-08T10:00:00.000Z'
),
(
  'hist2025_deaff4151b929790', 'KNE-10092025-001', '2025-09-10', '2025-09-10', '2025-09-10',
  'MD SARWAR', '8882020975', '', 'Kishanganj', '36', 'Male',
  'MAUJABARI, BAGALBARI, KOCHADHAMAN, KISHANGANJ', 'Piles', '20000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-10T10:00:00.000Z', '2025-09-10T10:00:00.000Z'
),
(
  'hist2025_4db832765a855869', 'KNE-13092025-001', '2025-09-13', '2025-09-13', '2025-09-13',
  'MD SHAKIL', '8292098117', '', 'Kishanganj', '34', 'Male',
  'KHAGRA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '36000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:00:00.000Z', '2025-09-13T10:00:00.000Z'
),
(
  'hist2025_8a64ff6dc6622e44', 'KNE-13092025-002', '2025-09-13', '2025-09-13', '2025-09-13',
  'NABAB ALAM', '7033231463', '', 'Kishanganj', '25', 'Male',
  'KHAGRA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '50000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:00:00.000Z', '2025-09-13T10:00:00.000Z'
),
(
  'hist2025_1fa2487956047d1b', 'KNE-15092025-001', '2025-09-15', '2025-09-15', '2025-09-15',
  'NAFIZ ALAM', '7004460126', '', 'Kishanganj', '35', 'Male',
  'KISHANGANJ, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Gupt Rog', '25000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-15T10:00:00.000Z', '2025-09-15T10:00:00.000Z'
),
(
  'hist2025_da0723f768c4d04b', 'KNE-18092025-001', '2025-09-18', '2025-09-18', '2025-09-18',
  'ARIF ALAM', '7888985454', '', 'Kishanganj', '25', 'Male',
  'KALASINGHIA, RAIPUR, PAHARKATTA, KISHANGANJ', 'Piles', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:00:00.000Z', '2025-09-18T10:00:00.000Z'
),
(
  'hist2025_958010866582efc7', 'KNE-18092025-002', '2025-09-18', '2025-09-18', '2025-09-18',
  'ROJINA KHATOON', '8016390092', '', 'Kishanganj', '18', 'Female',
  'SAMASTPUR, VIDYANANDPUR, CHAKULIA, UTTAR DINAJPUR', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:00:00.000Z', '2025-09-18T10:00:00.000Z'
),
(
  'hist2025_9edca24954aa2c17', 'KNE-20092025-001', '2025-09-20', '2025-09-20', '2025-09-20',
  'ATAUR RAHMAN', '8084528864', '', 'Kishanganj', '50', 'Male',
  'MASTANCHOWK, BAGALBARI, KOCHADHAMAN, KISHANGANJ', 'Piles', '45000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:00:00.000Z', '2025-09-20T10:00:00.000Z'
),
(
  'hist2025_ea463d34166bf6dd', 'KNE-24092025-001', '2025-09-24', '2025-09-24', '2025-09-24',
  'SHABNAM', '6297654766', '', 'Kishanganj', '26', 'Male',
  'BANBARI, SINGIA, KISHANGANJ, KISHANGANJ', 'Other', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:00:00.000Z', '2025-09-24T10:00:00.000Z'
),
(
  'hist2025_0a74354ebe510e10', 'KNE-24092025-002', '2025-09-24', '2025-09-24', '2025-09-24',
  'BABUL ALAM', '9264215216', '', 'Kishanganj', '35', 'Male',
  'BAGALBARI, BAGALBARI, KOCHADHAMAN, KISHANGANJ', 'Piles', '350000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:00:00.000Z', '2025-09-24T10:00:00.000Z'
),
(
  'hist2025_4dd959f33fd953fe', 'KNE-24092025-003', '2025-09-24', '2025-09-24', '2025-09-24',
  'WASIM AKHTER', '7063087735', '', 'Kishanganj', '30', 'Female',
  'GOALPOKHAR, BARBILLA, GOALPOKHAR, UTTAR DINAJPUR', 'Gupt Rog', '28000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:00:00.000Z', '2025-09-24T10:00:00.000Z'
),
(
  'hist2025_4afc2ef352904522', 'KNE-26092025-001', '2025-09-26', '2025-09-26', '2025-09-26',
  'MANJUR ALAM', '8670764872', '', 'Kishanganj', '39', 'Male',
  'NICHIT PUR, DALKHOLA, DALKHOLA, UD', 'Piles', '21000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-26T10:00:00.000Z', '2025-09-26T10:00:00.000Z'
),
(
  'hist2025_40b3c22e7ed2dcd3', 'KNE-26092025-002', '2025-09-26', '2025-09-26', '2025-09-26',
  'MD ASLAM', '9528607637', '', 'Kishanganj', '30', 'Male',
  'KALASINGIA, RAIPUR, PAHARKATTA, KISHANGANJ', 'Fissure', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-26T10:00:00.000Z', '2025-09-26T10:00:00.000Z'
),
(
  'hist2025_affb93bed7ec295b', 'KNE-27092025-001', '2025-09-27', '2025-09-27', '2025-09-27',
  'NASHIBA KHATOON', '8509744421', '', 'Kishanganj', '40', 'Female',
  'DAULA CHOWK, DAULA CHOWK, KISHANGANJ, KISHANGANJ', 'Piles', '37000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:00:00.000Z', '2025-09-27T10:00:00.000Z'
),
(
  'hist2025_95df6521767ba110', 'KNE-04102025-001', '2025-10-04', '2025-10-04', '2025-10-04',
  'SAMIM AKHTER', '6207143684', '', 'Kishanganj', '32', 'Female',
  'RAIPUR, RAIPUR, ARRABARI, KISHANGANJ', 'Piles', '35000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:00:00.000Z', '2025-10-04T10:00:00.000Z'
),
(
  'hist2025_50261a41cf73942e', 'KNE-05102025-001', '2025-10-05', '2025-10-05', '2025-10-05',
  'MURSLIM ALAM', '6296133324', '', 'Kishanganj', '25', 'Male',
  'DIGLI, MAZLISPUR, GOALPOKHER, UD', 'Fissure', '20000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-05T10:00:00.000Z', '2025-10-05T10:00:00.000Z'
),
(
  'hist2025_833e01e27316065e', 'KNE-06102025-001', '2025-10-06', '2025-10-06', '2025-10-06',
  'SURESH KR SINGH', '7858868682', '', 'Kishanganj', '31', 'Male',
  'GHAMBHIRGANJ, BHARATPUR, SUKHANI, KISHANGANJ', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-06T10:00:00.000Z', '2025-10-06T10:00:00.000Z'
),
(
  'hist2025_9428803fbfe88ea3', 'KNE-11102025-001', '2025-10-11', '2025-10-11', '2025-10-11',
  'ALIM UDDIN', '7091337563', '', 'Kishanganj', '12', 'Male',
  'PADAMPUR, PADAMPUR, GARBHANDANGA, KISHANGANJ', 'Piles', '25000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:00:00.000Z', '2025-10-11T10:00:00.000Z'
),
(
  'hist2025_08355e7f4f990e0d', 'KNE-22102025-001', '2025-10-22', '2025-10-22', '2025-10-22',
  'MONJIRA KHATUN', '7369084326', '', 'Kishanganj', '20', 'Female',
  'BEHERATOLA, GACHHPARA, KISHANGANJ, KISHANGANJ', 'Piles', '41000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:00:00.000Z', '2025-10-22T10:00:00.000Z'
),
(
  'hist2025_79b886f0512788b0', 'KNE-22102025-002', '2025-10-22', '2025-10-22', '2025-10-22',
  'TUSHAR KANTI', '8340333653', '', 'Kishanganj', '42', 'Male',
  'RUIDHASA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Fistula', '54000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:00:00.000Z', '2025-10-22T10:00:00.000Z'
),
(
  'hist2025_4acda601ba4cb971', 'KNE-22102025-003', '2025-10-22', '2025-10-22', '2025-10-22',
  'AMAL KUMAR', '8809972037', '', 'Kishanganj', '27', 'Male',
  'KHAGRA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '25000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:00:00.000Z', '2025-10-22T10:00:00.000Z'
),
(
  'hist2025_256c2f7db876d45b', 'KNE-24102025-001', '2025-10-24', '2025-10-24', '2025-10-24',
  'MD ASRAFUL', '7904328499', '', 'Kishanganj', '24', 'Male',
  'CHOUKAI CHANDONTOLA, GALIYA, CHAKULIYA, U.D', 'Fistula', '29750',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-24T10:00:00.000Z', '2025-10-24T10:00:00.000Z'
),
(
  'hist2025_7f94acee42316ef1', 'KNE-29102025-001', '2025-10-29', '2025-10-29', '2025-10-29',
  'SARFARAZ', '8084633866', '', 'Kishanganj', '28', 'Male',
  'KATHALBARI, BANGAUA, BAHADURGANJ, KISHANGANJ', 'Piles', '19000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:00:00.000Z', '2025-10-29T10:00:00.000Z'
),
(
  'hist2025_42937563c7a4a8f4', 'KNE-06112025-001', '2025-11-06', '2025-11-06', '2025-11-06',
  'SAMIRUDDIN', '9733237451', '', 'Kishanganj', '42', 'Male',
  'JAKIRBASTI, HATKHOLA, CHAKULIYA, U.D', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-06T10:00:00.000Z', '2025-11-06T10:00:00.000Z'
),
(
  'hist2025_e7d86a0278aa9418', 'KNE-06112025-002', '2025-11-06', '2025-11-06', '2025-11-06',
  'RIHANA KHATOON', '9572967488', '', 'Kishanganj', '45', 'Female',
  'BELUWA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '55000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-06T10:00:00.000Z', '2025-11-06T10:00:00.000Z'
),
(
  'hist2025_2beff50993684290', 'KNE-08112025-001', '2025-11-08', '2025-11-08', '2025-11-08',
  'NAYEEM AKHTER', '9647452857', '', 'Kishanganj', '25', 'Female',
  'SAMASTPUR, VIDYANANDPUR, CHAKULIA, UD', 'Gupt Rog', '24050',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:00:00.000Z', '2025-11-08T10:00:00.000Z'
),
(
  'hist2025_daf0bdf5f0c045a2', 'KNE-12112025-001', '2025-11-12', '2025-11-12', '2025-11-12',
  'MOHMAD IRFAN', '7250207063', '', 'Kishanganj', '23', 'Male',
  'TELIVITA, DIGHALBANK, DIGHALBANK, KISHANGANJ', 'Fistula', '37000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:00:00.000Z', '2025-11-12T10:00:00.000Z'
),
(
  'hist2025_cff877da7bc33de2', 'KNE-14112025-001', '2025-11-14', '2025-11-14', '2025-11-14',
  'RAHUL CHODHARY', '7903438083', '', 'Kishanganj', '33', 'Male',
  'DUMARIA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Other', '25000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-14T10:00:00.000Z', '2025-11-14T10:00:00.000Z'
),
(
  'hist2025_5a21fbb61e1852c2', 'KNE-15112025-001', '2025-11-15', '2025-11-15', '2025-11-15',
  'BAHADUR LAL', '6203686924', '', 'Kishanganj', '72', 'Male',
  'POWAKHALI, SARAYGURI, POWAKHALI, KISHANGANJ', 'Piles', '45000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:00:00.000Z', '2025-11-15T10:00:00.000Z'
),
(
  'hist2025_1ce78de2879d1882', 'KNE-17112025-001', '2025-11-17', '2025-11-17', '2025-11-17',
  'NASHIM', '9931647477', '', 'Kishanganj', '54', 'Male',
  'BELUYA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '28000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-17T10:00:00.000Z', '2025-11-17T10:00:00.000Z'
),
(
  'hist2025_0a88f305f63eb9dd', 'KNE-17112025-002', '2025-11-17', '2025-11-17', '2025-11-17',
  'MD DABIR ALAM', '9311088607', '', 'Kishanganj', '22', 'Male',
  'BHOGDABAR, BHAVINIGANJ, THAKURGANJ, KISHANGANJ', 'Fissure', '26000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-17T10:00:00.000Z', '2025-11-17T10:00:00.000Z'
),
(
  'hist2025_02ccc85535cc2865', 'KNE-18112025-001', '2025-11-18', '2025-11-18', '2025-11-18',
  'SALIMUDDIN', '9679159055', '', 'Kishanganj', '64', 'Male',
  'HASKUNDA, PANJIPARA, GOYALPOKHAR, UTTAR DINAJPUR', 'Piles', '28000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-18T10:00:00.000Z', '2025-11-18T10:00:00.000Z'
),
(
  'hist2025_11eb702a625a0049', 'KNE-18112025-002', '2025-11-18', '2025-11-18', '2025-11-18',
  'JAGADIS MAJUMDAR', '6296824807', '', 'Kishanganj', '34', 'Male',
  'KANKI, MAZLISPUR, CHAKULIYA, UTTAR DINAJPUR', 'Piles', '38000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-18T10:00:00.000Z', '2025-11-18T10:00:00.000Z'
),
(
  'hist2025_7e5d875290d39cb8', 'KNE-22112025-001', '2025-11-22', '2025-11-22', '2025-11-22',
  'SAHID', '8864007348', '', 'Kishanganj', '35', 'Male',
  'PADAM PUR, PADAMPUR, DIGHAL BANK, KISHANGANJ', 'Piles', '24000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:00:00.000Z', '2025-11-22T10:00:00.000Z'
),
(
  'hist2025_af8f3797b8caeef8', 'KNE-28112025-001', '2025-11-28', '2025-11-28', '2025-11-28',
  'HAMEDA BANU', '9693490945', '', 'Kishanganj', '25', 'Male',
  'PURANA KHAGRA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '30000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-28T10:00:00.000Z', '2025-11-28T10:00:00.000Z'
),
(
  'hist2025_d0f1991c2fe0eeed', 'KNE-13122025-001', '2025-12-13', '2025-12-13', '2025-12-13',
  'CHANDRAMOHAN', '9801016307', '', 'Kishanganj', '40', 'Male',
  'ORLAHA, ORLAHA, BARLA KOTHI, PURNEA', 'Piles', '32000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:00:00.000Z', '2025-12-13T10:00:00.000Z'
),
(
  'hist2025_b5a56ea4f8bd567c', 'KNE-22122025-001', '2025-12-22', '2025-12-22', '2025-12-22',
  'NAJRUL ALAM', '9741275034', '', 'Kishanganj', '30', 'Male',
  'TOPAMARI, KOCHADHAMAN, KOCHADHAMAN, KISHANGANJ', 'Fistula', '22000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-22T10:00:00.000Z', '2025-12-22T10:00:00.000Z'
),
(
  'hist2025_abad7c8f48a3a82f', 'KNE-22122025-002', '2025-12-22', '2025-12-22', '2025-12-22',
  'REKHA SING', '7484054594', '', 'Kishanganj', '50', 'Female',
  'DUMORIYA BHATTA, KISHANGANJ, KISHANGANJ, KISHANGANJ', 'Piles', '50000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-22T10:00:00.000Z', '2025-12-22T10:00:00.000Z'
),
(
  'hist2025_4f8c212c5aff57d4', 'KNE-24122025-001', '2025-12-24', '2025-12-24', '2025-12-24',
  'MUSARAF ALAM', '8670181777', '', 'Kishanganj', '24', 'Male',
  'ISLAMPUR, AMALJHARI, ISLAMPUR, UTTAR DINAJPUR', 'Piles', '150000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:00:00.000Z', '2025-12-24T10:00:00.000Z'
),
(
  'hist2025_f2fc98ae3434c92c', 'KNE-24122025-002', '2025-12-24', '2025-12-24', '2025-12-24',
  'GAYTRI DEVI', '9934966794', '', 'Kishanganj', '50', 'Female',
  'KAJLAMANI, MODHARHAAT, KOCHADHAMAN, KISHANGANJ', 'Piles', '40000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:00:00.000Z', '2025-12-24T10:00:00.000Z'
),
(
  'hist2025_cf9c35d0ffb07984', 'KNE-24122025-003', '2025-12-24', '2025-12-24', '2025-12-24',
  'SEHZAD SAMDANI', '9973811074', '', 'Kishanganj', '35', 'Male',
  'TALBARI, KASHIBARI, KOCHADHAMAN, KISHANGANJ', 'Piles', '42000',
  'Treatment Running', 'false', 'true',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:00:00.000Z', '2025-12-24T10:00:00.000Z'
);

insert into public.payments (
  id, "payType", "payLabel", "paymentLabel", "patientId", mobile, branch, name,
  date, amount, mode, remarks,
  "createdBy", "receivedBy", "createdAt", "updatedAt"
) values
(
  'hist2025_pay_09f894ec3019a6ed', 'treatment', 'Advance', 'Advance', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-07-22', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-22T10:15:00.000Z', '2025-07-22T10:15:00.000Z'
),
(
  'hist2025_pay_56ffb642d92f4af0', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-07-23', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:16:00.000Z', '2025-07-23T10:16:00.000Z'
),
(
  'hist2025_pay_a2cfedd942332f1c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-07-26', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-26T10:17:00.000Z', '2025-07-26T10:17:00.000Z'
),
(
  'hist2025_pay_395ed519865cdf5e', 'treatment', '4th Payment', '4th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-08-02', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:18:00.000Z', '2025-08-02T10:18:00.000Z'
),
(
  'hist2025_pay_b2a7d2eabbb9c39a', 'treatment', '5th Payment', '5th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-08-06', '5500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:19:00.000Z', '2025-08-06T10:19:00.000Z'
),
(
  'hist2025_pay_9a69d1e3e085c86f', 'treatment', '6th Payment', '6th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-08-09', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-09T10:20:00.000Z', '2025-08-09T10:20:00.000Z'
),
(
  'hist2025_pay_e2296103a1c18556', 'treatment', '7th Payment', '7th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-08-13', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:21:00.000Z', '2025-08-13T10:21:00.000Z'
),
(
  'hist2025_pay_c396143ef53aa8bc', 'treatment', '8th Payment', '8th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-08-18', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-18T10:22:00.000Z', '2025-08-18T10:22:00.000Z'
),
(
  'hist2025_pay_b11953c223886752', 'treatment', '9th Payment', '9th Payment', 'hist2025_c823748994221ff7', '8391991430', 'Kishanganj', 'HAKIM UDDIN',
  '2025-09-03', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:23:00.000Z', '2025-09-03T10:23:00.000Z'
),
(
  'hist2025_pay_0682c6df065ddd91', 'treatment', 'Advance', 'Advance', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-07-17', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-17T10:15:00.000Z', '2025-07-17T10:15:00.000Z'
),
(
  'hist2025_pay_0eed1f8bdf647516', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-07-23', '9000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:16:00.000Z', '2025-07-23T10:16:00.000Z'
),
(
  'hist2025_pay_6757da8531b2a7be', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-02', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:17:00.000Z', '2025-08-02T10:17:00.000Z'
),
(
  'hist2025_pay_c66e928f2a55c2bd', 'treatment', '4th Payment', '4th Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-06', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-06T10:18:00.000Z', '2025-08-06T10:18:00.000Z'
),
(
  'hist2025_pay_205bb7a0b937db62', 'treatment', '5th Payment', '5th Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-10', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-10T10:19:00.000Z', '2025-08-10T10:19:00.000Z'
),
(
  'hist2025_pay_de35496d3d653df4', 'treatment', '6th Payment', '6th Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-13', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:20:00.000Z', '2025-08-13T10:20:00.000Z'
),
(
  'hist2025_pay_5dd8ae5212275961', 'treatment', '7th Payment', '7th Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-16', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-16T10:21:00.000Z', '2025-08-16T10:21:00.000Z'
),
(
  'hist2025_pay_0e762d93cf872e55', 'treatment', '8th Payment', '8th Payment', 'hist2025_3f830b538d44602f', '8089107966', 'Kishanganj', 'MD SAYED',
  '2025-08-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-20T10:22:00.000Z', '2025-08-20T10:22:00.000Z'
),
(
  'hist2025_pay_f52c7ce6557e0696', 'treatment', 'Advance', 'Advance', 'hist2025_b3fd6639ece7bc72', '8967399644', 'Kishanganj', 'SAHANOR KHATOON',
  '2025-07-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-17T10:15:00.000Z', '2025-07-17T10:15:00.000Z'
),
(
  'hist2025_pay_ef32363bbd962fbd', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_b3fd6639ece7bc72', '8967399644', 'Kishanganj', 'SAHANOR KHATOON',
  '2025-07-23', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:16:00.000Z', '2025-07-23T10:16:00.000Z'
),
(
  'hist2025_pay_928776b70f5c1c22', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_b3fd6639ece7bc72', '8967399644', 'Kishanganj', 'SAHANOR KHATOON',
  '2025-07-26', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-26T10:17:00.000Z', '2025-07-26T10:17:00.000Z'
),
(
  'hist2025_pay_44bc7c2c6be3f78d', 'treatment', '4th Payment', '4th Payment', 'hist2025_b3fd6639ece7bc72', '8967399644', 'Kishanganj', 'SAHANOR KHATOON',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:18:00.000Z', '2025-07-30T10:18:00.000Z'
),
(
  'hist2025_pay_7a46deeb8320a9eb', 'treatment', 'Advance', 'Advance', 'hist2025_3e476c864c02cd0d', '7872766362', 'Kishanganj', 'HASAN ALI',
  '2025-07-22', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-22T10:15:00.000Z', '2025-07-22T10:15:00.000Z'
),
(
  'hist2025_pay_2be8d5d966244ed1', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_3e476c864c02cd0d', '7872766362', 'Kishanganj', 'HASAN ALI',
  '2025-07-23', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:16:00.000Z', '2025-07-23T10:16:00.000Z'
),
(
  'hist2025_pay_c2492d12bf160b6a', 'treatment', 'Advance', 'Advance', 'hist2025_f22f28d45b7e5b6b', '7865803067', 'Kishanganj', 'FARHA PARVIN',
  '2025-07-23', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-23T10:15:00.000Z', '2025-07-23T10:15:00.000Z'
),
(
  'hist2025_pay_73505351b9b114f4', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_f22f28d45b7e5b6b', '7865803067', 'Kishanganj', 'FARHA PARVIN',
  '2025-07-30', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:16:00.000Z', '2025-07-30T10:16:00.000Z'
),
(
  'hist2025_pay_7437956b3a378d37', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_f22f28d45b7e5b6b', '7865803067', 'Kishanganj', 'FARHA PARVIN',
  '2025-08-11', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-11T10:17:00.000Z', '2025-08-11T10:17:00.000Z'
),
(
  'hist2025_pay_0444adfd90154c47', 'treatment', 'Advance', 'Advance', 'hist2025_4daaef4c04ad44ce', '7888475776', 'Kishanganj', 'MD SAKIL',
  '2025-07-26', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-26T10:15:00.000Z', '2025-07-26T10:15:00.000Z'
),
(
  'hist2025_pay_48e6d2e17e6837a4', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4daaef4c04ad44ce', '7888475776', 'Kishanganj', 'MD SAKIL',
  '2025-08-02', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-02T10:16:00.000Z', '2025-08-02T10:16:00.000Z'
),
(
  'hist2025_pay_990fcaaa7a359847', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4daaef4c04ad44ce', '7888475776', 'Kishanganj', 'MD SAKIL',
  '2025-08-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-09T10:17:00.000Z', '2025-08-09T10:17:00.000Z'
),
(
  'hist2025_pay_5c5eaacea0d83ae4', 'treatment', '4th Payment', '4th Payment', 'hist2025_4daaef4c04ad44ce', '7888475776', 'Kishanganj', 'MD SAKIL',
  '2025-08-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:18:00.000Z', '2025-08-13T10:18:00.000Z'
),
(
  'hist2025_pay_7c846f292e2f7b5c', 'treatment', '5th Payment', '5th Payment', 'hist2025_4daaef4c04ad44ce', '7888475776', 'Kishanganj', 'MD SAKIL',
  '2025-08-16', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-16T10:19:00.000Z', '2025-08-16T10:19:00.000Z'
),
(
  'hist2025_pay_4b386f28e8a1c260', 'treatment', 'Advance', 'Advance', 'hist2025_d755c537eebc155c', '7009135532', 'Kishanganj', 'MAIMUL HOWK',
  '2025-07-30', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-07-30T10:15:00.000Z', '2025-07-30T10:15:00.000Z'
),
(
  'hist2025_pay_7a687cb2c3388646', 'treatment', 'Advance', 'Advance', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-08', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-08T10:15:00.000Z', '2025-08-08T10:15:00.000Z'
),
(
  'hist2025_pay_0913568a05a5885d', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-13', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-13T10:16:00.000Z', '2025-08-13T10:16:00.000Z'
),
(
  'hist2025_pay_4f845ce5b3028fe4', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-16', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-16T10:17:00.000Z', '2025-08-16T10:17:00.000Z'
),
(
  'hist2025_pay_cc6b94ad5aa4e8e3', 'treatment', '4th Payment', '4th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-18', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-18T10:18:00.000Z', '2025-08-18T10:18:00.000Z'
),
(
  'hist2025_pay_6932bfc3c79f8ead', 'treatment', '5th Payment', '5th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-20', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-20T10:19:00.000Z', '2025-08-20T10:19:00.000Z'
),
(
  'hist2025_pay_e2cf3bd3a5460f9a', 'treatment', '6th Payment', '6th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-23', '14000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-23T10:20:00.000Z', '2025-08-23T10:20:00.000Z'
),
(
  'hist2025_pay_a34079e3c3b7f8e6', 'treatment', '7th Payment', '7th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-27', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:21:00.000Z', '2025-08-27T10:21:00.000Z'
),
(
  'hist2025_pay_36a41ac40a563db3', 'treatment', '8th Payment', '8th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-08-30', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-30T10:22:00.000Z', '2025-08-30T10:22:00.000Z'
),
(
  'hist2025_pay_e8e902a0ac9ca010', 'treatment', '9th Payment', '9th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-09-06', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-06T10:23:00.000Z', '2025-09-06T10:23:00.000Z'
),
(
  'hist2025_pay_950ce1e6549da1c5', 'treatment', '10th Payment', '10th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-09-11', '7500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-11T10:24:00.000Z', '2025-09-11T10:24:00.000Z'
),
(
  'hist2025_pay_20b6496c140ecedf', 'treatment', '11th Payment', '11th Payment', 'hist2025_78bdba7af4d2b9cb', '9907652834', 'Kishanganj', 'CHOTU MARDIN',
  '2025-09-15', '12500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-15T10:25:00.000Z', '2025-09-15T10:25:00.000Z'
),
(
  'hist2025_pay_b5c81333b0de43e8', 'treatment', 'Advance', 'Advance', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-08-21', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-21T10:15:00.000Z', '2025-08-21T10:15:00.000Z'
),
(
  'hist2025_pay_961206c93ac5d5d1', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-08-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-23T10:16:00.000Z', '2025-08-23T10:16:00.000Z'
),
(
  'hist2025_pay_6636623d0f9577f0', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-08-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-25T10:17:00.000Z', '2025-08-25T10:17:00.000Z'
),
(
  'hist2025_pay_3a047102db8218eb', 'treatment', '4th Payment', '4th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-08-27', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:18:00.000Z', '2025-08-27T10:18:00.000Z'
),
(
  'hist2025_pay_e1b064303e201e3c', 'treatment', '5th Payment', '5th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-08-30', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-30T10:19:00.000Z', '2025-08-30T10:19:00.000Z'
),
(
  'hist2025_pay_b02fb123551d7048', 'treatment', '6th Payment', '6th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-09-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:20:00.000Z', '2025-09-03T10:20:00.000Z'
),
(
  'hist2025_pay_d96dd2fa7b963a38', 'treatment', '7th Payment', '7th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-09-13', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:21:00.000Z', '2025-09-13T10:21:00.000Z'
),
(
  'hist2025_pay_cb41bdfcf43cf93f', 'treatment', '8th Payment', '8th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-09-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:22:00.000Z', '2025-09-20T10:22:00.000Z'
),
(
  'hist2025_pay_75287cbd4d5f36e3', 'treatment', '9th Payment', '9th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-10-04', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:23:00.000Z', '2025-10-04T10:23:00.000Z'
),
(
  'hist2025_pay_a5f005b44cb1c405', 'treatment', '10th Payment', '10th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-10-11', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:24:00.000Z', '2025-10-11T10:24:00.000Z'
),
(
  'hist2025_pay_d03a0e82cc74a52e', 'treatment', '11th Payment', '11th Payment', 'hist2025_a1ab6455b6f93caa', '9296362956', 'Kishanganj', 'TARAFUL KHATOON',
  '2025-11-07', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-07T10:25:00.000Z', '2025-11-07T10:25:00.000Z'
),
(
  'hist2025_pay_0fe56e1ea80f8365', 'treatment', 'Advance', 'Advance', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-08-21', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-21T10:15:00.000Z', '2025-08-21T10:15:00.000Z'
),
(
  'hist2025_pay_f55cf72ffb295567', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-08-23', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-23T10:16:00.000Z', '2025-08-23T10:16:00.000Z'
),
(
  'hist2025_pay_c245a717c272b209', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-08-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-25T10:17:00.000Z', '2025-08-25T10:17:00.000Z'
),
(
  'hist2025_pay_9e7c0abd2bd8f6b8', 'treatment', '4th Payment', '4th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-08-27', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:18:00.000Z', '2025-08-27T10:18:00.000Z'
),
(
  'hist2025_pay_4a3d6c7f3e3b8bef', 'treatment', '5th Payment', '5th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-08-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-29T10:19:00.000Z', '2025-08-29T10:19:00.000Z'
),
(
  'hist2025_pay_a1d2e96ced4fd348', 'treatment', '6th Payment', '6th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-03', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-03T10:20:00.000Z', '2025-09-03T10:20:00.000Z'
),
(
  'hist2025_pay_42b5091e2df1e997', 'treatment', '7th Payment', '7th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-06T10:21:00.000Z', '2025-09-06T10:21:00.000Z'
),
(
  'hist2025_pay_db23142fdfa1cdfc', 'treatment', '8th Payment', '8th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-10', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-10T10:22:00.000Z', '2025-09-10T10:22:00.000Z'
),
(
  'hist2025_pay_6c8f7d9df64d55e6', 'treatment', '9th Payment', '9th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-15T10:23:00.000Z', '2025-09-15T10:23:00.000Z'
),
(
  'hist2025_pay_51720c46e296ef2d', 'treatment', '10th Payment', '10th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:24:00.000Z', '2025-09-20T10:24:00.000Z'
),
(
  'hist2025_pay_831058c44bbf4141', 'treatment', '11th Payment', '11th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-09-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:25:00.000Z', '2025-09-27T10:25:00.000Z'
),
(
  'hist2025_pay_8e638b6e8d5effb1', 'treatment', '12th Payment', '12th Payment', 'hist2025_51de98e074702388', '9973609230', 'Kishanganj', 'NUSRAT JAHA',
  '2025-10-11', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:26:00.000Z', '2025-10-11T10:26:00.000Z'
),
(
  'hist2025_pay_d1fad217c1902e5b', 'treatment', 'Advance', 'Advance', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-08-27', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-27T10:15:00.000Z', '2025-08-27T10:15:00.000Z'
),
(
  'hist2025_pay_870f85816847454b', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-08-30', '12000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-08-30T10:16:00.000Z', '2025-08-30T10:16:00.000Z'
),
(
  'hist2025_pay_b81f0de34dd0dcbc', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-09-06', '12000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-06T10:17:00.000Z', '2025-09-06T10:17:00.000Z'
),
(
  'hist2025_pay_03dce4a1bba79b73', 'treatment', '4th Payment', '4th Payment', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-09-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:18:00.000Z', '2025-09-13T10:18:00.000Z'
),
(
  'hist2025_pay_f4c0aefcd821a0b1', 'treatment', '5th Payment', '5th Payment', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-09-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:19:00.000Z', '2025-09-17T10:19:00.000Z'
),
(
  'hist2025_pay_671a2f4cc5a06aee', 'treatment', '6th Payment', '6th Payment', 'hist2025_12561fcb3692e9f1', '7872821364', 'Kishanganj', 'KALACHAN SARKAR',
  '2025-09-24', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:20:00.000Z', '2025-09-24T10:20:00.000Z'
),
(
  'hist2025_pay_2e2ff89bd996272b', 'treatment', 'Advance', 'Advance', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-08T10:15:00.000Z', '2025-09-08T10:15:00.000Z'
),
(
  'hist2025_pay_02c78b60d960f3bc', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-10', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-10T10:16:00.000Z', '2025-09-10T10:16:00.000Z'
),
(
  'hist2025_pay_5f25d04ef953d984', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-13', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:17:00.000Z', '2025-09-13T10:17:00.000Z'
),
(
  'hist2025_pay_027b603b92de7cab', 'treatment', '4th Payment', '4th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:18:00.000Z', '2025-09-17T10:18:00.000Z'
),
(
  'hist2025_pay_e01b5211c419e7d4', 'treatment', '5th Payment', '5th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:19:00.000Z', '2025-09-20T10:19:00.000Z'
),
(
  'hist2025_pay_bac4484b263910a9', 'treatment', '6th Payment', '6th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-24', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:20:00.000Z', '2025-09-24T10:20:00.000Z'
),
(
  'hist2025_pay_c248731ab14b06df', 'treatment', '7th Payment', '7th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:21:00.000Z', '2025-09-27T10:21:00.000Z'
),
(
  'hist2025_pay_080eb028bdc8a38b', 'treatment', '8th Payment', '8th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-09-04', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-04T10:22:00.000Z', '2025-09-04T10:22:00.000Z'
),
(
  'hist2025_pay_2eb4aac5f6187f0f', 'treatment', '9th Payment', '9th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-10-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-08T10:23:00.000Z', '2025-10-08T10:23:00.000Z'
),
(
  'hist2025_pay_e8f8faa239f1811f', 'treatment', '10th Payment', '10th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-10-18', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:24:00.000Z', '2025-10-18T10:24:00.000Z'
),
(
  'hist2025_pay_94df0525aa1e352f', 'treatment', '11th Payment', '11th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-10-22', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:25:00.000Z', '2025-10-22T10:25:00.000Z'
),
(
  'hist2025_pay_c51cc1b085d53726', 'treatment', '12th Payment', '12th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-10-29', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:26:00.000Z', '2025-10-29T10:26:00.000Z'
),
(
  'hist2025_pay_62c52da360a1eed6', 'treatment', '13th Payment', '13th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-11-05', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:27:00.000Z', '2025-11-05T10:27:00.000Z'
),
(
  'hist2025_pay_36ba7a6e54eed929', 'treatment', '14th Payment', '14th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-11-22', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:28:00.000Z', '2025-11-22T10:28:00.000Z'
),
(
  'hist2025_pay_ae6948af18cf5f41', 'treatment', '15th Payment', '15th Payment', 'hist2025_d6581689e9f2d7eb', '8918318739', 'Kishanganj', 'GANESH MONDAL',
  '2025-11-29', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:29:00.000Z', '2025-11-29T10:29:00.000Z'
),
(
  'hist2025_pay_f166603a58622551', 'treatment', 'Advance', 'Advance', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-09-17', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:15:00.000Z', '2025-09-17T10:15:00.000Z'
),
(
  'hist2025_pay_8a2729bf1bbd1c99', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-09-20', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:16:00.000Z', '2025-09-20T10:16:00.000Z'
),
(
  'hist2025_pay_abac8bc50beb421c', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-09-24', '50000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:17:00.000Z', '2025-09-24T10:17:00.000Z'
),
(
  'hist2025_pay_12f00dade3261b66', 'treatment', '4th Payment', '4th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-09-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-29T10:18:00.000Z', '2025-09-29T10:18:00.000Z'
),
(
  'hist2025_pay_a4e601318bdd7743', 'treatment', '5th Payment', '5th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-10-04', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:19:00.000Z', '2025-10-04T10:19:00.000Z'
),
(
  'hist2025_pay_42e5cf2e3df05089', 'treatment', '6th Payment', '6th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-10-11', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:20:00.000Z', '2025-10-11T10:20:00.000Z'
),
(
  'hist2025_pay_69708267c0db50ff', 'treatment', '7th Payment', '7th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-10-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:21:00.000Z', '2025-10-18T10:21:00.000Z'
),
(
  'hist2025_pay_f29122468adef772', 'treatment', '8th Payment', '8th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-10-25', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:22:00.000Z', '2025-10-25T10:22:00.000Z'
),
(
  'hist2025_pay_fb25f35f349e28b9', 'treatment', '9th Payment', '9th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-10-05', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-05T10:23:00.000Z', '2025-10-05T10:23:00.000Z'
),
(
  'hist2025_pay_aa2d40a954e98a96', 'treatment', '10th Payment', '10th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-11-29', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:24:00.000Z', '2025-11-29T10:24:00.000Z'
),
(
  'hist2025_pay_869c06cd18b08f7a', 'treatment', '11th Payment', '11th Payment', 'hist2025_d81645626033f40d', '7991127657', 'Kishanganj', 'MD BABUL',
  '2025-12-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:25:00.000Z', '2025-12-13T10:25:00.000Z'
),
(
  'hist2025_pay_7ae2857b05fd37bd', 'treatment', 'Advance', 'Advance', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-09-11', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-11T10:15:00.000Z', '2025-09-11T10:15:00.000Z'
),
(
  'hist2025_pay_0e54d9e33de4adf0', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-09-13', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:16:00.000Z', '2025-09-13T10:16:00.000Z'
),
(
  'hist2025_pay_255b3c1bf391d5ac', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-09-17', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:17:00.000Z', '2025-09-17T10:17:00.000Z'
),
(
  'hist2025_pay_11f025e31e73d21d', 'treatment', '4th Payment', '4th Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-09-24', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:18:00.000Z', '2025-09-24T10:18:00.000Z'
),
(
  'hist2025_pay_a28dbb3afe729994', 'treatment', '5th Payment', '5th Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-10-15', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:19:00.000Z', '2025-10-15T10:19:00.000Z'
),
(
  'hist2025_pay_5de626d227f70c49', 'treatment', '6th Payment', '6th Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-10-22', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:20:00.000Z', '2025-10-22T10:20:00.000Z'
),
(
  'hist2025_pay_55bc5aa8b67fdffd', 'treatment', '7th Payment', '7th Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-12-04', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-04T10:21:00.000Z', '2025-12-04T10:21:00.000Z'
),
(
  'hist2025_pay_976c14c36a9f2cf9', 'treatment', '8th Payment', '8th Payment', 'hist2025_deaff4151b929790', '8882020975', 'Kishanganj', 'MD SARWAR',
  '2025-12-13', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:22:00.000Z', '2025-12-13T10:22:00.000Z'
),
(
  'hist2025_pay_d16e1c6318747889', 'treatment', 'Advance', 'Advance', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-09-13', '2500', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:15:00.000Z', '2025-09-13T10:15:00.000Z'
),
(
  'hist2025_pay_77384b01f22e5b24', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-09-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:16:00.000Z', '2025-09-13T10:16:00.000Z'
),
(
  'hist2025_pay_35f9cdca99c055f8', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-09-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:17:00.000Z', '2025-09-17T10:17:00.000Z'
),
(
  'hist2025_pay_26d7dcbc9fb50464', 'treatment', '4th Payment', '4th Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-09-20', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:18:00.000Z', '2025-09-20T10:18:00.000Z'
),
(
  'hist2025_pay_032c51ae7b843cbb', 'treatment', '5th Payment', '5th Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-09-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:19:00.000Z', '2025-09-27T10:19:00.000Z'
),
(
  'hist2025_pay_7e00bb64bda73aed', 'treatment', '6th Payment', '6th Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-10-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-08T10:20:00.000Z', '2025-10-08T10:20:00.000Z'
),
(
  'hist2025_pay_74f03c10ab52e85a', 'treatment', '7th Payment', '7th Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-10-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:21:00.000Z', '2025-10-15T10:21:00.000Z'
),
(
  'hist2025_pay_aa69b20d54f6fa5d', 'treatment', '8th Payment', '8th Payment', 'hist2025_4db832765a855869', '8292098117', 'Kishanganj', 'MD SHAKIL',
  '2025-12-13', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:22:00.000Z', '2025-12-13T10:22:00.000Z'
),
(
  'hist2025_pay_151d90ced60410bf', 'treatment', 'Advance', 'Advance', 'hist2025_8a64ff6dc6622e44', '7033231463', 'Kishanganj', 'NABAB ALAM',
  '2025-09-13', '800', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-13T10:15:00.000Z', '2025-09-13T10:15:00.000Z'
),
(
  'hist2025_pay_978498ac4eadec88', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_8a64ff6dc6622e44', '7033231463', 'Kishanganj', 'NABAB ALAM',
  '2025-09-17', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-17T10:16:00.000Z', '2025-09-17T10:16:00.000Z'
),
(
  'hist2025_pay_ff026e697d2cbfc1', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_8a64ff6dc6622e44', '7033231463', 'Kishanganj', 'NABAB ALAM',
  '2025-11-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:17:00.000Z', '2025-11-15T10:17:00.000Z'
),
(
  'hist2025_pay_b16d0d258270ea06', 'treatment', '4th Payment', '4th Payment', 'hist2025_8a64ff6dc6622e44', '7033231463', 'Kishanganj', 'NABAB ALAM',
  '2025-12-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:18:00.000Z', '2025-12-13T10:18:00.000Z'
),
(
  'hist2025_pay_41d3e4b6d7d4d6c3', 'treatment', 'Advance', 'Advance', 'hist2025_1fa2487956047d1b', '7004460126', 'Kishanganj', 'NAFIZ ALAM',
  '2025-09-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:15:00.000Z', '2025-09-18T10:15:00.000Z'
),
(
  'hist2025_pay_be53728a7fc70c52', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_1fa2487956047d1b', '7004460126', 'Kishanganj', 'NAFIZ ALAM',
  '2025-09-19', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-19T10:16:00.000Z', '2025-09-19T10:16:00.000Z'
),
(
  'hist2025_pay_69c3f174750782b1', 'treatment', 'Advance', 'Advance', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-09-18', '3000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:15:00.000Z', '2025-09-18T10:15:00.000Z'
),
(
  'hist2025_pay_0742611cbfade739', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-09-18', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:16:00.000Z', '2025-09-18T10:16:00.000Z'
),
(
  'hist2025_pay_d650c66b00956c96', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-09-20', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:17:00.000Z', '2025-09-20T10:17:00.000Z'
),
(
  'hist2025_pay_223ea3ff258a4961', 'treatment', '4th Payment', '4th Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-09-25', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-25T10:18:00.000Z', '2025-09-25T10:18:00.000Z'
),
(
  'hist2025_pay_7285afcf46fa7cb2', 'treatment', '5th Payment', '5th Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-09-29', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-29T10:19:00.000Z', '2025-09-29T10:19:00.000Z'
),
(
  'hist2025_pay_a9c9a5adcfed267e', 'treatment', '6th Payment', '6th Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-10-04', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:20:00.000Z', '2025-10-04T10:20:00.000Z'
),
(
  'hist2025_pay_3aa68afbf30bc639', 'treatment', '7th Payment', '7th Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-10-11', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:21:00.000Z', '2025-10-11T10:21:00.000Z'
),
(
  'hist2025_pay_ff48cdc2a249a8d6', 'treatment', '8th Payment', '8th Payment', 'hist2025_da0723f768c4d04b', '7888985454', 'Kishanganj', 'ARIF ALAM',
  '2025-10-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:22:00.000Z', '2025-10-22T10:22:00.000Z'
),
(
  'hist2025_pay_f53b42bb7f04b81f', 'treatment', 'Advance', 'Advance', 'hist2025_958010866582efc7', '8016390092', 'Kishanganj', 'ROJINA KHATOON',
  '2025-09-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-18T10:15:00.000Z', '2025-09-18T10:15:00.000Z'
),
(
  'hist2025_pay_fb637bb8b3835595', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_958010866582efc7', '8016390092', 'Kishanganj', 'ROJINA KHATOON',
  '2025-09-24', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:16:00.000Z', '2025-09-24T10:16:00.000Z'
),
(
  'hist2025_pay_60afcb67dcb5971f', 'treatment', 'Advance', 'Advance', 'hist2025_9edca24954aa2c17', '8084528864', 'Kishanganj', 'ATAUR RAHMAN',
  '2025-09-20', '1800', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-20T10:15:00.000Z', '2025-09-20T10:15:00.000Z'
),
(
  'hist2025_pay_b02321bfe129a5dc', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_9edca24954aa2c17', '8084528864', 'Kishanganj', 'ATAUR RAHMAN',
  '2025-09-27', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:16:00.000Z', '2025-09-27T10:16:00.000Z'
),
(
  'hist2025_pay_932de19e27e221c0', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_9edca24954aa2c17', '8084528864', 'Kishanganj', 'ATAUR RAHMAN',
  '2025-10-04', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:17:00.000Z', '2025-10-04T10:17:00.000Z'
),
(
  'hist2025_pay_491af333c955ffcf', 'treatment', '4th Payment', '4th Payment', 'hist2025_9edca24954aa2c17', '8084528864', 'Kishanganj', 'ATAUR RAHMAN',
  '2025-10-14', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-14T10:18:00.000Z', '2025-10-14T10:18:00.000Z'
),
(
  'hist2025_pay_0552e4c23f2b2920', 'treatment', 'Advance', 'Advance', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-09-24', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:15:00.000Z', '2025-09-24T10:15:00.000Z'
),
(
  'hist2025_pay_a71cd0fd1d2c7f2f', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-09-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:16:00.000Z', '2025-09-27T10:16:00.000Z'
),
(
  'hist2025_pay_b5041521a807a6df', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-10-04', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:17:00.000Z', '2025-10-04T10:17:00.000Z'
),
(
  'hist2025_pay_63b2ca848f367ee0', 'treatment', '4th Payment', '4th Payment', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-10-11', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:18:00.000Z', '2025-10-11T10:18:00.000Z'
),
(
  'hist2025_pay_0e3dea0c99aeaff9', 'treatment', '5th Payment', '5th Payment', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-12-20', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-20T10:19:00.000Z', '2025-12-20T10:19:00.000Z'
),
(
  'hist2025_pay_446787f18bc86064', 'treatment', '6th Payment', '6th Payment', 'hist2025_ea463d34166bf6dd', '6297654766', 'Kishanganj', 'SHABNAM',
  '2025-12-24', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:20:00.000Z', '2025-12-24T10:20:00.000Z'
),
(
  'hist2025_pay_f1ba5caadfa7caf2', 'treatment', 'Advance', 'Advance', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-09-24', '25000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:15:00.000Z', '2025-09-24T10:15:00.000Z'
),
(
  'hist2025_pay_a088f34b70d8233e', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-09-24', '25000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:16:00.000Z', '2025-09-24T10:16:00.000Z'
),
(
  'hist2025_pay_a6fd4a3ea2e8e194', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-10-04', '50000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-04T10:17:00.000Z', '2025-10-04T10:17:00.000Z'
),
(
  'hist2025_pay_1847324f76ee1dd0', 'treatment', '4th Payment', '4th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-10-08', '50000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-08T10:18:00.000Z', '2025-10-08T10:18:00.000Z'
),
(
  'hist2025_pay_24eefd666140974a', 'treatment', '5th Payment', '5th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-10-18', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:19:00.000Z', '2025-10-18T10:19:00.000Z'
),
(
  'hist2025_pay_6df79eb284142bbb', 'treatment', '6th Payment', '6th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-10-29', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:20:00.000Z', '2025-10-29T10:20:00.000Z'
),
(
  'hist2025_pay_8a75e02190b24294', 'treatment', '7th Payment', '7th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-11-08', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:21:00.000Z', '2025-11-08T10:21:00.000Z'
),
(
  'hist2025_pay_68618885c1cb7503', 'treatment', '8th Payment', '8th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-11-26', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:22:00.000Z', '2025-11-26T10:22:00.000Z'
),
(
  'hist2025_pay_cd6e1ecfa15de9b9', 'treatment', '9th Payment', '9th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-12-01', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-01T10:23:00.000Z', '2025-12-01T10:23:00.000Z'
),
(
  'hist2025_pay_7911fff8d3f86c52', 'treatment', '10th Payment', '10th Payment', 'hist2025_0a74354ebe510e10', '9264215216', 'Kishanganj', 'BABUL ALAM',
  '2025-12-03', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:24:00.000Z', '2025-12-03T10:24:00.000Z'
),
(
  'hist2025_pay_ba547f6a4bcf7ecd', 'treatment', 'Advance', 'Advance', 'hist2025_4dd959f33fd953fe', '7063087735', 'Kishanganj', 'WASIM AKHTER',
  '2025-09-24', '20000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:15:00.000Z', '2025-09-24T10:15:00.000Z'
),
(
  'hist2025_pay_9b83bbd6bcf17712', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4dd959f33fd953fe', '7063087735', 'Kishanganj', 'WASIM AKHTER',
  '2025-09-24', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-24T10:16:00.000Z', '2025-09-24T10:16:00.000Z'
),
(
  'hist2025_pay_9c37ed88038673bf', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4dd959f33fd953fe', '7063087735', 'Kishanganj', 'WASIM AKHTER',
  '2025-09-27', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-27T10:17:00.000Z', '2025-09-27T10:17:00.000Z'
),
(
  'hist2025_pay_77e5a393c8562202', 'treatment', 'Advance', 'Advance', 'hist2025_4afc2ef352904522', '8670764872', 'Kishanganj', 'MANJUR ALAM',
  '2025-09-26', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-26T10:15:00.000Z', '2025-09-26T10:15:00.000Z'
),
(
  'hist2025_pay_41b67c52eb16f239', 'treatment', 'Advance', 'Advance', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-10-06', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-06T10:15:00.000Z', '2025-10-06T10:15:00.000Z'
),
(
  'hist2025_pay_f43eff1d2bc8131c', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-10-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-13T10:16:00.000Z', '2025-10-13T10:16:00.000Z'
),
(
  'hist2025_pay_a4610b729e44c1b2', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-10-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:17:00.000Z', '2025-10-15T10:17:00.000Z'
),
(
  'hist2025_pay_2359dcae3a460d4c', 'treatment', '4th Payment', '4th Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-10-18', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:18:00.000Z', '2025-10-18T10:18:00.000Z'
),
(
  'hist2025_pay_39423e5748f2ec3b', 'treatment', '5th Payment', '5th Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-10-29', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:19:00.000Z', '2025-10-29T10:19:00.000Z'
),
(
  'hist2025_pay_e2f34c84dae6ebcd', 'treatment', '6th Payment', '6th Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-11-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:20:00.000Z', '2025-11-05T10:20:00.000Z'
),
(
  'hist2025_pay_f56e20a4c61d8c28', 'treatment', '7th Payment', '7th Payment', 'hist2025_40b3c22e7ed2dcd3', '9528607637', 'Kishanganj', 'MD ASLAM',
  '2025-11-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:21:00.000Z', '2025-11-19T10:21:00.000Z'
),
(
  'hist2025_pay_29d11dbb86baf893', 'treatment', 'Advance', 'Advance', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-09-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-26T10:15:00.000Z', '2025-09-26T10:15:00.000Z'
),
(
  'hist2025_pay_149b626b37104e49', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-09-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-09-29T10:16:00.000Z', '2025-09-29T10:16:00.000Z'
),
(
  'hist2025_pay_c94b8896525416c9', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-10-08', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-08T10:17:00.000Z', '2025-10-08T10:17:00.000Z'
),
(
  'hist2025_pay_fb1652aacc11c759', 'treatment', '4th Payment', '4th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-10-14', '4500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-14T10:18:00.000Z', '2025-10-14T10:18:00.000Z'
),
(
  'hist2025_pay_b941d5a9957d48a1', 'treatment', '5th Payment', '5th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-10-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-27T10:19:00.000Z', '2025-10-27T10:19:00.000Z'
),
(
  'hist2025_pay_1ca1cd020d82d821', 'treatment', '6th Payment', '6th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-10-29', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:20:00.000Z', '2025-10-29T10:20:00.000Z'
),
(
  'hist2025_pay_c6bd76e1135c438b', 'treatment', '7th Payment', '7th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-11-01', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:21:00.000Z', '2025-11-01T10:21:00.000Z'
),
(
  'hist2025_pay_b5950c7258fc2cd5', 'treatment', '8th Payment', '8th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-11-07', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-07T10:22:00.000Z', '2025-11-07T10:22:00.000Z'
),
(
  'hist2025_pay_ae0219795bc1a8c7', 'treatment', '9th Payment', '9th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-11-12', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:23:00.000Z', '2025-11-12T10:23:00.000Z'
),
(
  'hist2025_pay_8537f090d7f085aa', 'treatment', '10th Payment', '10th Payment', 'hist2025_affb93bed7ec295b', '8509744421', 'Kishanganj', 'NASHIBA KHATOON',
  '2025-11-17', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-17T10:24:00.000Z', '2025-11-17T10:24:00.000Z'
),
(
  'hist2025_pay_25a47f618336672a', 'treatment', 'Advance', 'Advance', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-10-06', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-06T10:15:00.000Z', '2025-10-06T10:15:00.000Z'
),
(
  'hist2025_pay_76d4b34b676952f9', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-10-15', '7000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:16:00.000Z', '2025-10-15T10:16:00.000Z'
),
(
  'hist2025_pay_a72cba209bee2479', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-10-18', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:17:00.000Z', '2025-10-18T10:17:00.000Z'
),
(
  'hist2025_pay_4dbdc401ae632f05', 'treatment', '4th Payment', '4th Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-10-25', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:18:00.000Z', '2025-10-25T10:18:00.000Z'
),
(
  'hist2025_pay_6ac52b651219b8df', 'treatment', '5th Payment', '5th Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-11-01', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:19:00.000Z', '2025-11-01T10:19:00.000Z'
),
(
  'hist2025_pay_3b38a6733a5ba275', 'treatment', '6th Payment', '6th Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-11-05', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:20:00.000Z', '2025-11-05T10:20:00.000Z'
),
(
  'hist2025_pay_a314e56a7a2b675a', 'treatment', '7th Payment', '7th Payment', 'hist2025_95df6521767ba110', '6207143684', 'Kishanganj', 'SAMIM AKHTER',
  '2025-11-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:21:00.000Z', '2025-11-15T10:21:00.000Z'
),
(
  'hist2025_pay_e52c04a0f1d06018', 'treatment', 'Advance', 'Advance', 'hist2025_50261a41cf73942e', '6296133324', 'Kishanganj', 'MURSLIM ALAM',
  '2025-10-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-05T10:15:00.000Z', '2025-10-05T10:15:00.000Z'
),
(
  'hist2025_pay_5e02943b07a5776e', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_50261a41cf73942e', '6296133324', 'Kishanganj', 'MURSLIM ALAM',
  '2025-10-09', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-09T10:16:00.000Z', '2025-10-09T10:16:00.000Z'
),
(
  'hist2025_pay_3232e8988ffd85e9', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_50261a41cf73942e', '6296133324', 'Kishanganj', 'MURSLIM ALAM',
  '2025-10-15', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:17:00.000Z', '2025-10-15T10:17:00.000Z'
),
(
  'hist2025_pay_2ca4b9286fefa609', 'treatment', '4th Payment', '4th Payment', 'hist2025_50261a41cf73942e', '6296133324', 'Kishanganj', 'MURSLIM ALAM',
  '2025-11-08', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:18:00.000Z', '2025-11-08T10:18:00.000Z'
),
(
  'hist2025_pay_3307d9fdfc119f78', 'treatment', 'Advance', 'Advance', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-06', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-06T10:15:00.000Z', '2025-10-06T10:15:00.000Z'
),
(
  'hist2025_pay_d4013c32379adc33', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-08', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-08T10:16:00.000Z', '2025-10-08T10:16:00.000Z'
),
(
  'hist2025_pay_4b0b5a35acc8c9f4', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-11', '5500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:17:00.000Z', '2025-10-11T10:17:00.000Z'
),
(
  'hist2025_pay_9e2e3bca107be9ef', 'treatment', '4th Payment', '4th Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-14', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-14T10:18:00.000Z', '2025-10-14T10:18:00.000Z'
),
(
  'hist2025_pay_959600ddf21ca8fd', 'treatment', '5th Payment', '5th Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-18', '3500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-18T10:19:00.000Z', '2025-10-18T10:19:00.000Z'
),
(
  'hist2025_pay_468f05a8781698c1', 'treatment', '6th Payment', '6th Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-23', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-23T10:20:00.000Z', '2025-10-23T10:20:00.000Z'
),
(
  'hist2025_pay_6b569d13101ad3ef', 'treatment', '7th Payment', '7th Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-10-25', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:21:00.000Z', '2025-10-25T10:21:00.000Z'
),
(
  'hist2025_pay_6c0ee734dd7765fc', 'treatment', '8th Payment', '8th Payment', 'hist2025_833e01e27316065e', '7858868682', 'Kishanganj', 'SURESH KR SINGH',
  '2025-11-12', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:22:00.000Z', '2025-11-12T10:22:00.000Z'
),
(
  'hist2025_pay_0e60b9cd4bc6ae90', 'treatment', 'Advance', 'Advance', 'hist2025_9428803fbfe88ea3', '7091337563', 'Kishanganj', 'ALIM UDDIN',
  '2025-10-11', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-11T10:15:00.000Z', '2025-10-11T10:15:00.000Z'
),
(
  'hist2025_pay_3c7b85f8e9291353', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_9428803fbfe88ea3', '7091337563', 'Kishanganj', 'ALIM UDDIN',
  '2025-10-15', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-15T10:16:00.000Z', '2025-10-15T10:16:00.000Z'
),
(
  'hist2025_pay_ef3056188d5fbb31', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_9428803fbfe88ea3', '7091337563', 'Kishanganj', 'ALIM UDDIN',
  '2025-10-22', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:17:00.000Z', '2025-10-22T10:17:00.000Z'
),
(
  'hist2025_pay_5b8083af345e6171', 'treatment', '4th Payment', '4th Payment', 'hist2025_9428803fbfe88ea3', '7091337563', 'Kishanganj', 'ALIM UDDIN',
  '2025-11-05', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:18:00.000Z', '2025-11-05T10:18:00.000Z'
),
(
  'hist2025_pay_350505ab0afd6748', 'treatment', 'Advance', 'Advance', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-10-22', '1000', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:15:00.000Z', '2025-10-22T10:15:00.000Z'
),
(
  'hist2025_pay_ee5b3dead572b23a', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-10-22', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:16:00.000Z', '2025-10-22T10:16:00.000Z'
),
(
  'hist2025_pay_b8fc90d62f87c891', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-10-25', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:17:00.000Z', '2025-10-25T10:17:00.000Z'
),
(
  'hist2025_pay_384c9058e6234bca', 'treatment', '4th Payment', '4th Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-11-01', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:18:00.000Z', '2025-11-01T10:18:00.000Z'
),
(
  'hist2025_pay_459d21e89e1a296b', 'treatment', '5th Payment', '5th Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-11-08', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:19:00.000Z', '2025-11-08T10:19:00.000Z'
),
(
  'hist2025_pay_a073b86bd9902da2', 'treatment', '6th Payment', '6th Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-11-12', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:20:00.000Z', '2025-11-12T10:20:00.000Z'
),
(
  'hist2025_pay_25b436cfea58e977', 'treatment', '7th Payment', '7th Payment', 'hist2025_08355e7f4f990e0d', '7369084326', 'Kishanganj', 'MONJIRA KHATUN',
  '2025-11-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:21:00.000Z', '2025-11-26T10:21:00.000Z'
),
(
  'hist2025_pay_2f088c489bfb97a2', 'treatment', 'Advance', 'Advance', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-10-22', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:15:00.000Z', '2025-10-22T10:15:00.000Z'
),
(
  'hist2025_pay_0fb5e0cf826c4d61', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-10-25', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:16:00.000Z', '2025-10-25T10:16:00.000Z'
),
(
  'hist2025_pay_2dec6c51d7bc0fe4', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-10-29', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:17:00.000Z', '2025-10-29T10:17:00.000Z'
),
(
  'hist2025_pay_b7d784e7f0827b37', 'treatment', '4th Payment', '4th Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-11-01', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:18:00.000Z', '2025-11-01T10:18:00.000Z'
),
(
  'hist2025_pay_b8920571d21e5619', 'treatment', '5th Payment', '5th Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-11-05', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:19:00.000Z', '2025-11-05T10:19:00.000Z'
),
(
  'hist2025_pay_b1188e87812e88f4', 'treatment', '6th Payment', '6th Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-11-08', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:20:00.000Z', '2025-11-08T10:20:00.000Z'
),
(
  'hist2025_pay_373bc43fe06f55bf', 'treatment', '7th Payment', '7th Payment', 'hist2025_79b886f0512788b0', '8340333653', 'Kishanganj', 'TUSHAR KANTI',
  '2025-11-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:21:00.000Z', '2025-11-15T10:21:00.000Z'
),
(
  'hist2025_pay_9c2ae55cc4168c3c', 'treatment', 'Advance', 'Advance', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-10-22', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-22T10:15:00.000Z', '2025-10-22T10:15:00.000Z'
),
(
  'hist2025_pay_30b185526a8cc4ef', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-10-25', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-25T10:16:00.000Z', '2025-10-25T10:16:00.000Z'
),
(
  'hist2025_pay_59a0e10b93cd92ac', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-11-08', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:17:00.000Z', '2025-11-08T10:17:00.000Z'
),
(
  'hist2025_pay_e633b2c8d76ca700', 'treatment', '4th Payment', '4th Payment', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-11-12', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:18:00.000Z', '2025-11-12T10:18:00.000Z'
),
(
  'hist2025_pay_5f882894bdd7dac1', 'treatment', '5th Payment', '5th Payment', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-11-19', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:19:00.000Z', '2025-11-19T10:19:00.000Z'
),
(
  'hist2025_pay_fb984176fc787486', 'treatment', '6th Payment', '6th Payment', 'hist2025_4acda601ba4cb971', '8809972037', 'Kishanganj', 'AMAL KUMAR',
  '2025-12-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-06T10:20:00.000Z', '2025-12-06T10:20:00.000Z'
),
(
  'hist2025_pay_15677d8cfe39b3a3', 'treatment', 'Advance', 'Advance', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-10-24', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-24T10:15:00.000Z', '2025-10-24T10:15:00.000Z'
),
(
  'hist2025_pay_5166f0c98653176f', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-10-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:16:00.000Z', '2025-10-29T10:16:00.000Z'
),
(
  'hist2025_pay_3d7e805f837f46e9', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-01', '8000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:17:00.000Z', '2025-11-01T10:17:00.000Z'
),
(
  'hist2025_pay_924d219e9c16ad58', 'treatment', '4th Payment', '4th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-05', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:18:00.000Z', '2025-11-05T10:18:00.000Z'
),
(
  'hist2025_pay_467648bf0c28064c', 'treatment', '5th Payment', '5th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-08T10:19:00.000Z', '2025-11-08T10:19:00.000Z'
),
(
  'hist2025_pay_b6be08e088ca14f8', 'treatment', '6th Payment', '6th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-12', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:20:00.000Z', '2025-11-12T10:20:00.000Z'
),
(
  'hist2025_pay_e4ed4dcaeec11c88', 'treatment', '7th Payment', '7th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-14', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-14T10:21:00.000Z', '2025-11-14T10:21:00.000Z'
),
(
  'hist2025_pay_79e472e43dec568e', 'treatment', '8th Payment', '8th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-15', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:22:00.000Z', '2025-11-15T10:22:00.000Z'
),
(
  'hist2025_pay_590ec3b0ae8b761b', 'treatment', '9th Payment', '9th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:23:00.000Z', '2025-11-19T10:23:00.000Z'
),
(
  'hist2025_pay_47b2a9b3bfbb7fdb', 'treatment', '10th Payment', '10th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-11-26', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:24:00.000Z', '2025-11-26T10:24:00.000Z'
),
(
  'hist2025_pay_7c3ccf8df2b64cbc', 'treatment', '11th Payment', '11th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-12-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:25:00.000Z', '2025-12-03T10:25:00.000Z'
),
(
  'hist2025_pay_d56f1b0cb10b8f35', 'treatment', '12th Payment', '12th Payment', 'hist2025_256c2f7db876d45b', '7904328499', 'Kishanganj', 'MD ASRAFUL',
  '2025-12-12', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-12T10:26:00.000Z', '2025-12-12T10:26:00.000Z'
),
(
  'hist2025_pay_4ed85547c93a3226', 'treatment', 'Advance', 'Advance', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-10-29', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-10-29T10:15:00.000Z', '2025-10-29T10:15:00.000Z'
),
(
  'hist2025_pay_e522659b4e4835a7', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-01', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-01T10:16:00.000Z', '2025-11-01T10:16:00.000Z'
),
(
  'hist2025_pay_aaef64e26555e852', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-05', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-05T10:17:00.000Z', '2025-11-05T10:17:00.000Z'
),
(
  'hist2025_pay_9706c3c1e53b479d', 'treatment', '4th Payment', '4th Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-12', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:18:00.000Z', '2025-11-12T10:18:00.000Z'
),
(
  'hist2025_pay_59fd0f48bba280c1', 'treatment', '5th Payment', '5th Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-14', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-14T10:19:00.000Z', '2025-11-14T10:19:00.000Z'
),
(
  'hist2025_pay_0498200727f2602b', 'treatment', '6th Payment', '6th Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:20:00.000Z', '2025-11-19T10:20:00.000Z'
),
(
  'hist2025_pay_3392585fca4475e9', 'treatment', '7th Payment', '7th Payment', 'hist2025_7f94acee42316ef1', '8084633866', 'Kishanganj', 'SARFARAZ',
  '2025-11-26', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:21:00.000Z', '2025-11-26T10:21:00.000Z'
),
(
  'hist2025_pay_f286c927f8bd6489', 'treatment', 'Advance', 'Advance', 'hist2025_42937563c7a4a8f4', '9733237451', 'Kishanganj', 'SAMIRUDDIN',
  '2025-11-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-06T10:15:00.000Z', '2025-11-06T10:15:00.000Z'
),
(
  'hist2025_pay_5a34e8f8726084b2', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_42937563c7a4a8f4', '9733237451', 'Kishanganj', 'SAMIRUDDIN',
  '2025-11-12', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:16:00.000Z', '2025-11-12T10:16:00.000Z'
),
(
  'hist2025_pay_9594c2f2acdf5b38', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_42937563c7a4a8f4', '9733237451', 'Kishanganj', 'SAMIRUDDIN',
  '2025-11-19', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:17:00.000Z', '2025-11-19T10:17:00.000Z'
),
(
  'hist2025_pay_48a40abb188ac3c6', 'treatment', 'Advance', 'Advance', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-12', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-12T10:15:00.000Z', '2025-11-12T10:15:00.000Z'
),
(
  'hist2025_pay_985d18a6db556baf', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-15', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:16:00.000Z', '2025-11-15T10:16:00.000Z'
),
(
  'hist2025_pay_649dcd2f64c273e2', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:17:00.000Z', '2025-11-19T10:17:00.000Z'
),
(
  'hist2025_pay_7bb19563c6125c02', 'treatment', '4th Payment', '4th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:18:00.000Z', '2025-11-22T10:18:00.000Z'
),
(
  'hist2025_pay_5ed71728c510b9c9', 'treatment', '5th Payment', '5th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-26', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:19:00.000Z', '2025-11-26T10:19:00.000Z'
),
(
  'hist2025_pay_03fa781c3fb4fb19', 'treatment', '6th Payment', '6th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-11-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:20:00.000Z', '2025-11-29T10:20:00.000Z'
),
(
  'hist2025_pay_70a8065e8cba9bde', 'treatment', '7th Payment', '7th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-03', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:21:00.000Z', '2025-12-03T10:21:00.000Z'
),
(
  'hist2025_pay_ce63e1d8e2f9eaaf', 'treatment', '8th Payment', '8th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-06T10:22:00.000Z', '2025-12-06T10:22:00.000Z'
),
(
  'hist2025_pay_bdaa6063cfd7ae99', 'treatment', '9th Payment', '9th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-13', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:23:00.000Z', '2025-12-13T10:23:00.000Z'
),
(
  'hist2025_pay_f0dc9b119ff3f6f7', 'treatment', '10th Payment', '10th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-17T10:24:00.000Z', '2025-12-17T10:24:00.000Z'
),
(
  'hist2025_pay_7849f5900a2570ab', 'treatment', '11th Payment', '11th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-20T10:25:00.000Z', '2025-12-20T10:25:00.000Z'
),
(
  'hist2025_pay_fb4e365ca3e5bb22', 'treatment', '12th Payment', '12th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-24', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:26:00.000Z', '2025-12-24T10:26:00.000Z'
),
(
  'hist2025_pay_f6b4d11040e366a7', 'treatment', '13th Payment', '13th Payment', 'hist2025_e7d86a0278aa9418', '9572967488', 'Kishanganj', 'RIHANA KHATOON',
  '2025-12-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:27:00.000Z', '2025-12-27T10:27:00.000Z'
),
(
  'hist2025_pay_595406f05b0c7a04', 'treatment', 'Advance', 'Advance', 'hist2025_2beff50993684290', '9647452857', 'Kishanganj', 'NAYEEM AKHTER',
  '2025-12-08', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-08T10:15:00.000Z', '2025-12-08T10:15:00.000Z'
),
(
  'hist2025_pay_82bf78540931a08f', 'treatment', 'Advance', 'Advance', 'hist2025_daf0bdf5f0c045a2', '7250207063', 'Kishanganj', 'MOHMAD IRFAN',
  '2025-11-17', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-17T10:15:00.000Z', '2025-11-17T10:15:00.000Z'
),
(
  'hist2025_pay_f620de66cb2c8f19', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_daf0bdf5f0c045a2', '7250207063', 'Kishanganj', 'MOHMAD IRFAN',
  '2025-11-19', '20000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:16:00.000Z', '2025-11-19T10:16:00.000Z'
),
(
  'hist2025_pay_4acb2360dc516509', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_daf0bdf5f0c045a2', '7250207063', 'Kishanganj', 'MOHMAD IRFAN',
  '2025-12-03', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:17:00.000Z', '2025-12-03T10:17:00.000Z'
),
(
  'hist2025_pay_a74bfe6d9622e9f2', 'treatment', '4th Payment', '4th Payment', 'hist2025_daf0bdf5f0c045a2', '7250207063', 'Kishanganj', 'MOHMAD IRFAN',
  '2025-12-25', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-25T10:18:00.000Z', '2025-12-25T10:18:00.000Z'
),
(
  'hist2025_pay_245648ba1e97c9c4', 'treatment', 'Advance', 'Advance', 'hist2025_cff877da7bc33de2', '7903438083', 'Kishanganj', 'RAHUL CHODHARY',
  '2025-11-14', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-14T10:15:00.000Z', '2025-11-14T10:15:00.000Z'
),
(
  'hist2025_pay_343fc61e363efdd1', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_cff877da7bc33de2', '7903438083', 'Kishanganj', 'RAHUL CHODHARY',
  '2025-11-15', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:16:00.000Z', '2025-11-15T10:16:00.000Z'
),
(
  'hist2025_pay_2cb2e9688df0f305', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_cff877da7bc33de2', '7903438083', 'Kishanganj', 'RAHUL CHODHARY',
  '2025-11-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:17:00.000Z', '2025-11-19T10:17:00.000Z'
),
(
  'hist2025_pay_432531aee511ce82', 'treatment', 'Advance', 'Advance', 'hist2025_5a21fbb61e1852c2', '6203686924', 'Kishanganj', 'BAHADUR LAL',
  '2025-11-15', '1500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-15T10:15:00.000Z', '2025-11-15T10:15:00.000Z'
),
(
  'hist2025_pay_c2f4115225287e94', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_5a21fbb61e1852c2', '6203686924', 'Kishanganj', 'BAHADUR LAL',
  '2025-11-19', '35000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:16:00.000Z', '2025-11-19T10:16:00.000Z'
),
(
  'hist2025_pay_a01e00e67d9ea384', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_5a21fbb61e1852c2', '6203686924', 'Kishanganj', 'BAHADUR LAL',
  '2025-11-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:17:00.000Z', '2025-11-22T10:17:00.000Z'
),
(
  'hist2025_pay_0c5d0620c067eb61', 'treatment', '4th Payment', '4th Payment', 'hist2025_5a21fbb61e1852c2', '6203686924', 'Kishanganj', 'BAHADUR LAL',
  '2025-11-26', '25000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:18:00.000Z', '2025-11-26T10:18:00.000Z'
),
(
  'hist2025_pay_f6d7af1b74d6db56', 'treatment', '5th Payment', '5th Payment', 'hist2025_5a21fbb61e1852c2', '6203686924', 'Kishanganj', 'BAHADUR LAL',
  '2025-11-29', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:19:00.000Z', '2025-11-29T10:19:00.000Z'
),
(
  'hist2025_pay_fa4dc8ec05ad1cd8', 'treatment', 'Advance', 'Advance', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-11-17', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-17T10:15:00.000Z', '2025-11-17T10:15:00.000Z'
),
(
  'hist2025_pay_c58b244322f649b0', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-11-19', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:16:00.000Z', '2025-11-19T10:16:00.000Z'
),
(
  'hist2025_pay_b690232923daf1cf', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-11-24', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-24T10:17:00.000Z', '2025-11-24T10:17:00.000Z'
),
(
  'hist2025_pay_3aca233a662ad714', 'treatment', '4th Payment', '4th Payment', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-11-26', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:18:00.000Z', '2025-11-26T10:18:00.000Z'
),
(
  'hist2025_pay_556aa5a9e6371be1', 'treatment', '5th Payment', '5th Payment', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-12-01', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-01T10:19:00.000Z', '2025-12-01T10:19:00.000Z'
),
(
  'hist2025_pay_30ff87fb9f418ffc', 'treatment', '6th Payment', '6th Payment', 'hist2025_1ce78de2879d1882', '9931647477', 'Kishanganj', 'NASHIM',
  '2025-12-08', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-08T10:20:00.000Z', '2025-12-08T10:20:00.000Z'
),
(
  'hist2025_pay_5e6505470ec7e184', 'treatment', 'Advance', 'Advance', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-11-19', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-19T10:15:00.000Z', '2025-11-19T10:15:00.000Z'
),
(
  'hist2025_pay_97fbbe370fb7a485', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-11-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:16:00.000Z', '2025-11-26T10:16:00.000Z'
),
(
  'hist2025_pay_c138b6756dc49d92', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-11-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:17:00.000Z', '2025-11-29T10:17:00.000Z'
),
(
  'hist2025_pay_d91cb50776f33ab1', 'treatment', '4th Payment', '4th Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-12-03', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:18:00.000Z', '2025-12-03T10:18:00.000Z'
),
(
  'hist2025_pay_d0a85bf812cbecd8', 'treatment', '5th Payment', '5th Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-12-08', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-08T10:19:00.000Z', '2025-12-08T10:19:00.000Z'
),
(
  'hist2025_pay_0d7ec81f9d78c81d', 'treatment', '6th Payment', '6th Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-12-19', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-19T10:20:00.000Z', '2025-12-19T10:20:00.000Z'
),
(
  'hist2025_pay_ad4a85966af1465f', 'treatment', '7th Payment', '7th Payment', 'hist2025_0a88f305f63eb9dd', '9311088607', 'Kishanganj', 'MD DABIR ALAM',
  '2025-12-22', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-22T10:21:00.000Z', '2025-12-22T10:21:00.000Z'
),
(
  'hist2025_pay_b69f97b7111b3194', 'treatment', 'Advance', 'Advance', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-11-18', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-18T10:15:00.000Z', '2025-11-18T10:15:00.000Z'
),
(
  'hist2025_pay_0eb2db4616b3704b', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-11-22', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:16:00.000Z', '2025-11-22T10:16:00.000Z'
),
(
  'hist2025_pay_12c59030515277a3', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-11-26', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:17:00.000Z', '2025-11-26T10:17:00.000Z'
),
(
  'hist2025_pay_ac98a31ed0f56469', 'treatment', '4th Payment', '4th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-11-29', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:18:00.000Z', '2025-11-29T10:18:00.000Z'
),
(
  'hist2025_pay_cd4dc2a5791b5024', 'treatment', '5th Payment', '5th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-12-06', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-06T10:19:00.000Z', '2025-12-06T10:19:00.000Z'
),
(
  'hist2025_pay_904cf00380b6aff9', 'treatment', '6th Payment', '6th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-12-10', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-10T10:20:00.000Z', '2025-12-10T10:20:00.000Z'
),
(
  'hist2025_pay_a80787e3d8f7d6e9', 'treatment', '7th Payment', '7th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-12-13', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:21:00.000Z', '2025-12-13T10:21:00.000Z'
),
(
  'hist2025_pay_97557557496b2710', 'treatment', '8th Payment', '8th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-12-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-17T10:22:00.000Z', '2025-12-17T10:22:00.000Z'
),
(
  'hist2025_pay_6dbca4c93b421ea4', 'treatment', '9th Payment', '9th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-12-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-20T10:23:00.000Z', '2025-12-20T10:23:00.000Z'
),
(
  'hist2025_pay_ac88a23b08145eb4', 'treatment', '10th Payment', '10th Payment', 'hist2025_02ccc85535cc2865', '9679159055', 'Kishanganj', 'SALIMUDDIN',
  '2025-11-20', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-20T10:24:00.000Z', '2025-11-20T10:24:00.000Z'
),
(
  'hist2025_pay_9bc6f2609e07a82d', 'treatment', 'Advance', 'Advance', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-11-18', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-18T10:15:00.000Z', '2025-11-18T10:15:00.000Z'
),
(
  'hist2025_pay_42f3435ece0ea326', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-11-26', '9000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:16:00.000Z', '2025-11-26T10:16:00.000Z'
),
(
  'hist2025_pay_abf6d439ade2f4f5', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-12-03', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-03T10:17:00.000Z', '2025-12-03T10:17:00.000Z'
),
(
  'hist2025_pay_697c9f3b97f391a3', 'treatment', '4th Payment', '4th Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-12-06', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-06T10:18:00.000Z', '2025-12-06T10:18:00.000Z'
),
(
  'hist2025_pay_20ccb6c4580998a9', 'treatment', '5th Payment', '5th Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-12-13', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:19:00.000Z', '2025-12-13T10:19:00.000Z'
),
(
  'hist2025_pay_97ff0213b6080f33', 'treatment', '6th Payment', '6th Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-12-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-17T10:20:00.000Z', '2025-12-17T10:20:00.000Z'
),
(
  'hist2025_pay_f4c85a45795f2f34', 'treatment', '7th Payment', '7th Payment', 'hist2025_11eb702a625a0049', '6296824807', 'Kishanganj', 'JAGADIS MAJUMDAR',
  '2025-12-27', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:21:00.000Z', '2025-12-27T10:21:00.000Z'
),
(
  'hist2025_pay_54fcc7beb584482c', 'treatment', 'Advance', 'Advance', 'hist2025_7e5d875290d39cb8', '8864007348', 'Kishanganj', 'SAHID',
  '2025-11-22', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-22T10:15:00.000Z', '2025-11-22T10:15:00.000Z'
),
(
  'hist2025_pay_a4711a2a138cd2c2', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_7e5d875290d39cb8', '8864007348', 'Kishanganj', 'SAHID',
  '2025-11-26', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-26T10:16:00.000Z', '2025-11-26T10:16:00.000Z'
),
(
  'hist2025_pay_954b354b784f6fdc', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_7e5d875290d39cb8', '8864007348', 'Kishanganj', 'SAHID',
  '2025-11-29', '4000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:17:00.000Z', '2025-11-29T10:17:00.000Z'
),
(
  'hist2025_pay_fae8c73d14c0a3b3', 'treatment', 'Advance', 'Advance', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-11-28', '500', 'CASH', 'Historical import (2025 Google Sheet) -- combined, no per-installment date in sheet',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-28T10:15:00.000Z', '2025-11-28T10:15:00.000Z'
),
(
  'hist2025_pay_073c1a26b62d29f2', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-11-28', '6000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-28T10:16:00.000Z', '2025-11-28T10:16:00.000Z'
),
(
  'hist2025_pay_214ded43696a9fc0', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-11-29', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-11-29T10:17:00.000Z', '2025-11-29T10:17:00.000Z'
),
(
  'hist2025_pay_f27363b8e95bd537', 'treatment', '4th Payment', '4th Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-12-10', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-10T10:18:00.000Z', '2025-12-10T10:18:00.000Z'
),
(
  'hist2025_pay_fe0aeb3a2c1cd0ba', 'treatment', '5th Payment', '5th Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-12-17', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-17T10:19:00.000Z', '2025-12-17T10:19:00.000Z'
),
(
  'hist2025_pay_416379667158dcb7', 'treatment', '6th Payment', '6th Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-12-20', '2500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-20T10:20:00.000Z', '2025-12-20T10:20:00.000Z'
),
(
  'hist2025_pay_e8fed52218776b7e', 'treatment', '7th Payment', '7th Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-12-24', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:21:00.000Z', '2025-12-24T10:21:00.000Z'
),
(
  'hist2025_pay_366fc1cbc5c58ab3', 'treatment', '8th Payment', '8th Payment', 'hist2025_af8f3797b8caeef8', '9693490945', 'Kishanganj', 'HAMEDA BANU',
  '2025-12-27', '3000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:22:00.000Z', '2025-12-27T10:22:00.000Z'
),
(
  'hist2025_pay_fa94314d7d229785', 'treatment', 'Advance', 'Advance', 'hist2025_d0f1991c2fe0eeed', '9801016307', 'Kishanganj', 'CHANDRAMOHAN',
  '2025-12-13', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-13T10:15:00.000Z', '2025-12-13T10:15:00.000Z'
),
(
  'hist2025_pay_811c3195c6fe345b', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_d0f1991c2fe0eeed', '9801016307', 'Kishanganj', 'CHANDRAMOHAN',
  '2025-12-17', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-17T10:16:00.000Z', '2025-12-17T10:16:00.000Z'
),
(
  'hist2025_pay_41642c1c0ab148ee', 'treatment', '3rd Payment', '3rd Payment', 'hist2025_d0f1991c2fe0eeed', '9801016307', 'Kishanganj', 'CHANDRAMOHAN',
  '2025-12-24', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:17:00.000Z', '2025-12-24T10:17:00.000Z'
),
(
  'hist2025_pay_4c943867b041ab88', 'treatment', 'Advance', 'Advance', 'hist2025_b5a56ea4f8bd567c', '9741275034', 'Kishanganj', 'NAJRUL ALAM',
  '2025-12-22', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-22T10:15:00.000Z', '2025-12-22T10:15:00.000Z'
),
(
  'hist2025_pay_424a82be8d54901a', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_b5a56ea4f8bd567c', '9741275034', 'Kishanganj', 'NAJRUL ALAM',
  '2025-12-23', '11000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-23T10:16:00.000Z', '2025-12-23T10:16:00.000Z'
),
(
  'hist2025_pay_ba2fdd45c1fcd7b7', 'treatment', 'Advance', 'Advance', 'hist2025_abad7c8f48a3a82f', '7484054594', 'Kishanganj', 'REKHA SING',
  '2025-12-22', '500', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-22T10:15:00.000Z', '2025-12-22T10:15:00.000Z'
),
(
  'hist2025_pay_e3d909fe4a8ce89e', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_abad7c8f48a3a82f', '7484054594', 'Kishanganj', 'REKHA SING',
  '2025-12-24', '5000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:16:00.000Z', '2025-12-24T10:16:00.000Z'
),
(
  'hist2025_pay_5abf4547acc91484', 'treatment', 'Advance', 'Advance', 'hist2025_4f8c212c5aff57d4', '8670181777', 'Kishanganj', 'MUSARAF ALAM',
  '2025-12-24', '30000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:15:00.000Z', '2025-12-24T10:15:00.000Z'
),
(
  'hist2025_pay_69eb61932f74759d', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_4f8c212c5aff57d4', '8670181777', 'Kishanganj', 'MUSARAF ALAM',
  '2025-12-27', '30000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:16:00.000Z', '2025-12-27T10:16:00.000Z'
),
(
  'hist2025_pay_14992d376afd6f31', 'treatment', 'Advance', 'Advance', 'hist2025_f2fc98ae3434c92c', '9934966794', 'Kishanganj', 'GAYTRI DEVI',
  '2025-12-24', '2000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:15:00.000Z', '2025-12-24T10:15:00.000Z'
),
(
  'hist2025_pay_df2f6ba73e373816', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_f2fc98ae3434c92c', '9934966794', 'Kishanganj', 'GAYTRI DEVI',
  '2025-12-27', '1000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:16:00.000Z', '2025-12-27T10:16:00.000Z'
),
(
  'hist2025_pay_f1ebe437b4d0cf42', 'treatment', 'Advance', 'Advance', 'hist2025_cf9c35d0ffb07984', '9973811074', 'Kishanganj', 'SEHZAD SAMDANI',
  '2025-12-24', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-24T10:15:00.000Z', '2025-12-24T10:15:00.000Z'
),
(
  'hist2025_pay_00f34690d41e7df0', 'treatment', '2nd Payment', '2nd Payment', 'hist2025_cf9c35d0ffb07984', '9973811074', 'Kishanganj', 'SEHZAD SAMDANI',
  '2025-12-27', '10000', 'CASH', 'Historical import (2025 Google Sheet)',
  'HISTORICAL IMPORT', 'HISTORICAL IMPORT', '2025-12-27T10:16:00.000Z', '2025-12-27T10:16:00.000Z'
);
