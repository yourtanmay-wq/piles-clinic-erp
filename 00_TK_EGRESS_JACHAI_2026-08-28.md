# 🔎 Egress ঝুঁকির পূর্ণ যাচাই — ২৮.০৮.২০২৬

TK-নির্দেশ: *"Supabase egress এর ঝুঁকি আর কোথায় কোথায় আছে, সম্পূর্ণ প্রজেক্টটা
খুঁটিয়ে খুঁটিয়ে দেখুন, আন্দাজে কিছু করবেন না।"*

**পদ্ধতি:** কোডের প্রতিটা `SupabaseClient.fetchList*` ও ওয়েবের প্রতিটা
`sb.from(...).select(...)` বার করে দেখা হয়েছে — কোন টেবিল, কত সারি, কোন কলাম,
আর কতবার চলে। নিচে যা লেখা আছে সবই **ফাইল ও লাইন নম্বর সহ প্রমাণিত**।

**ছবির মাপ (মেপে নেওয়া):** ছবি জমা হয় `PhotoUtils.encodeResized()` দিয়ে —
সর্বোচ্চ ৬০০ পিক্সেল, JPEG মান ৮৫, তারপর base64 (৩৩% বাড়ে)।
⇒ **একটা ছবি ≈ ৫৫–১২০ KB**।

---

## 🔴 ঝুঁকি ১ — `medical` টেবিল ছবিসহ নামছে (সবচেয়ে বড়)

`medical.photos` ঘরে চেক-আপের **before + during + after** তিনটে ছবিই base64
হিসেবে থাকে (`DoctorCheckupActivity.kt:1392-1402`) ⇒ **এক সারি ≈ ৩৬০ KB পর্যন্ত**।

| জায়গা | সীমা | কলাম |
|---|---|---|
| `PatientTimelineRepository.kt:483` | 500 সারি | `select=*` |
| `ClinicalCloudRepository.kt:210` (`loadMedical`) | 500 | `select=*` |
| `ClinicalCloudRepository.kt:223` (`loadMedicalRaw`) | 500 | `select=*` |
| `ClinicalCloudRepository.kt:235` (`loadMedicalRawOrNull`) | 500 | `select=*` |
| `PrintCenterActivity.kt:1069` | সীমা দেওয়াই নেই | `select=*` |

একজন রোগীর ৮টা চেক-আপে ছবি থাকলে **একবার Timeline খুললেই ≈ ৩ MB**।

## 🔴 ঝুঁকি ২ — `followups` ছবিসহ, ২০ সারি

`followups`-এ `photo` ও `history` দুটোই ভারী (`SafeWideColumns.kt:74`)।

| জায়গা | সীমা | কলাম |
|---|---|---|
| `PatientTimelineActivity.kt:543` | 20 | `select=*` |
| `FollowUpActivity.kt:3873` | 20 | `select=*` |

⇒ **একবারে ≈ ১.৮ MB পর্যন্ত**, আর Timeline প্রায়ই খোলা হয়।

## 🟠 ঝুঁকি ৩ — `patients` এক সারি, কিন্তু ছবিসহ

এক-একটা কল ≈ ৬০–১২০ KB, কিন্তু জায়গাগুলো **রোজকার পথে**:
`DoctorCheckupActivity.kt:1137,1138,1588,1589` · `PatientTimelineActivity.kt:540,576,2698` ·
`ReportCardActivity.kt:168` · `ReportCardPrinter.kt:59` · `DraftRepository.kt:1081` ·
`DoctorVisitRepository.kt:737`

## 🟠 ঝুঁকি ৪ — `trash`-এর পিছনের দরজা

`trash.record` ঘরে **মুছে ফেলা পুরো সারিটাই** থাকে (ছবিসহ)।
· `TrashRepository.kt:173` — সরু পড়া ব্যর্থ হলে **`select=*` ৫০০০ সারি** নামে।
· `TrashRepository.kt:127` (`fetchTrashRaw`) — একই মোটা পড়া, তবে **এটা এখন
  কোথাও ডাকা হয় না** (যাচাই করা — শুধু কমেন্টে নাম আছে)।

## 🟡 ঝুঁকি ৫ — পুরো টেবিল নামানো (ইচ্ছাকৃত, কিন্তু বিশাল)

· `ExportDataActivity.kt:100` — `fetchList(table, limit = 100000)`, `select=*`
· `CloudBackup.kt:35` — **সব টেবিল**, 100000 সারি, `select=*`
দুটোই ডাক্তার/মাস্টার নিজে বোতাম চাপলে চলে, তবু **একবার চালালেই বহু MB**।

---

## ✅ যেগুলো যাচাই করে দেখলাম — এগুলো ঠিক আছে

| ব্যবস্থা | কেন নিরাপদ |
|---|---|
| ফোনের ১৫-মিনিটের `BackgroundRefreshWorker` | ৪৫ মিনিটের থ্রটল; `includePhoto = false`; সব পড়া সরু |
| ওয়েবের ৩০–৬০ সেকেন্ডের টাইমার (৪টে) | আগে **সই** মেলায় (`wlv1CloudUnchanged`) — না বদলালে কিছুই নামে না; আর `RT_NO_PHOTO_COLS` |
| ওয়েবের Follow-up pull | ৪৫ সেকেন্ডের থ্রটল + গত ৩ ঘণ্টার সারি + ছবি-ছাড়া কলাম |
| অ্যানাটমি ছবির তালিকা | `updatedAt` মিলিয়ে **শুধু বদলানো ছবিই** নামে (`AnatomyPictureRepository.kt:100-140`) |
| Trash Bin পর্দা | সরু কলাম (`TRASH_LIST_COLS`) দিয়েই পড়ে |
| ফোনের তালিকার পড়াগুলো | বেশিরভাগই `fetchListSlim*` — কলাম গোনা |

---

## 📋 সারানোর সুপারিশ (TK-এর সিদ্ধান্তের অপেক্ষায়)

| ক্রম | কাজ | লাভ |
|---|---|---|
| ১ | `medical`-এর ৫টা পড়ায় ছবি-ছাড়া কলাম (ছবি লাগলে তখনই আলাদা করে) | সবচেয়ে বেশি |
| ২ | `followups`-এর ২টা পড়ায় সরু কলাম | বেশি |
| ৩ | `patients`-এর ১০টা পড়ায় সরু কলাম (ছবি লাগলে আলাদা) | মাঝারি, কিন্তু রোজকার |
| ৪ | `trash`-এর মোটা fallback-এ সীমা কমানো / বাদ | বিরল কিন্তু বিশাল |
| ৫ | Export/Backup-এ আগে সতর্কবার্তা ("কত MB নামবে") | ইচ্ছাকৃত, তাই শুধু জানানো |

⛔ **এখনো কিছু বদলানো হয়নি** — TK বলেছেন *"সঠিকভাবে সততার সাথে দেখুন আগে"*।
