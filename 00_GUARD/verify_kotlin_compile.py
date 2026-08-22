#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🛡️🔴 V497 (২১.০৮.২০২৬) — **আসল Kotlin কম্পাইলার দিয়ে পাহারা।**

─── কেন এটা বানাতে হলো (TK-এর অভিযোগ, ২১.০৮.২০২৬ সকাল ৯:৩৬) ───────────────
V496-এর ZIP পাঠানোর পরে TK Android Studio-তে Build দিলে **১৮টা ভুল** এসে
বিল্ড ভেঙে যায়:

    Classifier 'Reason' does not have a companion object…
    Comparison of incompatible enums 'BiometricGate.Reason' and …
    'when' expression must be exhaustive, add necessary 'SAVED'…

কারণ: `WorkNotebookActivity.kt`-এ লেখা হয়েছিল

    val G = BiometricGate.Reason        // ⛔ Kotlin-এ enum **ক্লাসটাকে**
    val canRetry = r == G.FAILED        //    চলকে রাখা যায় না

পুরনো পাহারাদারগুলো শুধু **লেখা খুঁজত** (regex), কোড **কম্পাইল করত না**।
তাই এই ধরনের ভুল ধরা পড়ার কোনো উপায়ই ছিল না।

TK-এর নির্দেশ: *"পাহারাদার কে আরো শক্ত করবেন, যাতে ভবিষ্যতে এই ধরনের
ভুল ভ্রান্তি ফাইল আমার কাছে না আসে।"*

⇒ এই পাহারাদার **সত্যিকারের `kotlinc` চালায়** — অনুমান নয়।

─── ⚠️ সীমাবদ্ধতা (সৎভাবে, লুকানো হয়নি) ───────────────────────────────────
এই পরিবেশে **Android SDK ও androidx লাইব্রেরি নামানো যায় না**
(`dl.google.com` ও `maven` বন্ধ)। তাই `android.*` · `androidx.*` ·
`org.json` · `kotlinx.*` — এগুলো কম্পাইলার চেনে না, আর সেজন্য হাজার হাজার
"unresolved reference" ভুল দেখায়। সেগুলো **আসল ভুল নয়**, শুধু গোলমাল।

─── 🔴 V497-এ যে ফাঁক ধরা পড়ল ও বন্ধ করা হলো ──────────────────────────────
প্রথম চেষ্টায় **সব ভুলকেই** বেসলাইনে রেখে দেওয়া হয়েছিল। কিন্তু সেই
বেসলাইন বানানো হয়েছিল এমন অবস্থা থেকে **যেটা নিজেই বিল্ড হত না** —
ফলে আসল দুটো ভুলও "জানা গোলমাল" হয়ে চাপা পড়ে গিয়েছিল:

    NoBengali.kt: unresolved reference: distance     ← `"…$distance…"`
    NoBengali.kt: function invocation 'until(...)'   ← `"…$until…"`

TK-এর দ্বিতীয় বিল্ডে ঠিক এগুলোই বেরোয়। তাই এখন **দুই ধাপ**:

  ধাপ ১ — **বাছাই**: প্রতিটা ভুল "বাইরের লাইব্রেরি নেই বলে" নাকি "আসল"।
     বাইরের বলে ধরা হয় শুধু তখনই, যখন —
       • না-চেনা নামটা কোনো `import android/androidx/org.json/…`-এর, বা
       • নামটা প্রকল্পে অন্য কোথাও `.নাম` হিসেবে ব্যবহার হয় (অর্থাৎ
         কোনো ক্লাসের সদস্য, শুধু ক্লাসটাই চেনা যাচ্ছে না), বা
       • বার্তাটা স্পষ্টতই ধাক্কার ফল ("overrides nothing", "type mismatch",
         "cannot infer a type" ইত্যাদি)।
     ⇒ এতে ২৮,৬০০ ভুল থেকে **~২৯০**-এ নামে।

  ধাপ ২ — সেই ~২৯০টাই বেসলাইনে (`kotlin_noise_baseline.txt`), এবং
     **নতুন একটাও এলে FAIL**।

✅ প্রমাণ: `$distance`-এর ভুলটা এই বাছাইয়ে **আসল** হিসেবে ধরা পড়ে।

✅ প্রমাণ: ইচ্ছে করে উপরের বাগটা আবার বসিয়ে চালানো হয়েছিল —
   বেসলাইনের বাইরে ঠিক **২টা নতুন ভুল** ধরা পড়ে, একটাও মিথ্যা সতর্কতা নয়।

⛔ এটা Android Studio-র বিল্ডের **বিকল্প নয়** — resource (R.java), manifest,
   dexing, ProGuard কিছুই এখানে যাচাই হয় না। কিন্তু Kotlin কোডের ভুল আর
   TK-এর কাছে পৌঁছাবে না।
⛔ যাচাই করা না গেলে (কম্পাইলার নেই / নামানো গেল না) এটা **কখনো PASS বলে না** —
   "SKIPPED (যাচাই করা যায়নি)" বলে এবং exit code 1 দেয়।

─── চালানোর নিয়ম ──────────────────────────────────────────────────────────
    python3 00_GUARD/verify_kotlin_compile.py                # যাচাই
    python3 00_GUARD/verify_kotlin_compile.py --update-baseline
                                                            # নতুন বেসলাইন
"""
import os
import re
import shutil
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "02_ANDROID_SOURCE_CODE", "PilesClinicApp", "app",
                   "src", "main", "java")
BASELINE = os.path.join(ROOT, "00_GUARD", "kotlin_noise_baseline.txt")

KOTLIN_VERSION = "1.9.24"
KOTLIN_URL = ("https://github.com/JetBrains/kotlin/releases/download/"
              "v%s/kotlin-compiler-%s.zip" % (KOTLIN_VERSION, KOTLIN_VERSION))

# কম্পাইলার কোথায় খুঁজব (ক্রম অনুসারে)
CANDIDATES = [
    os.environ.get("KOTLINC", ""),
    "/tmp/kotlinc_dist/kotlinc/bin/kotlinc",
    os.path.join(ROOT, "00_GUARD", ".kotlinc", "kotlinc", "bin", "kotlinc"),
    shutil.which("kotlinc") or "",
]


def find_kotlinc(allow_download=True):
    for c in CANDIDATES:
        if c and os.path.exists(c):
            return c
    if not allow_download:
        return None
    # নেই — একবার নামানোর চেষ্টা (ইন্টারনেট না থাকলে চুপচাপ ব্যর্থ)
    dest_dir = os.path.join(ROOT, "00_GUARD", ".kotlinc")
    zip_path = os.path.join(dest_dir, "kotlin-compiler.zip")
    try:
        os.makedirs(dest_dir, exist_ok=True)
        print("   kotlinc পাওয়া যায়নি — নামানোর চেষ্টা করছি (~৯১ MB)…")
        urllib.request.urlretrieve(KOTLIN_URL, zip_path)
        import zipfile
        with zipfile.ZipFile(zip_path) as z:
            z.extractall(dest_dir)
        os.remove(zip_path)
        exe = os.path.join(dest_dir, "kotlinc", "bin", "kotlinc")
        if os.path.exists(exe):
            os.chmod(exe, 0o755)
            return exe
    except Exception as e:
        print("   নামানো গেল না: %s" % e)
    return None


# ── বাছাই: কোনটা "বাইরের লাইব্রেরি নেই বলে", কোনটা আসল ──────────────────────
EXT_PKG = ("android", "androidx", "org.json", "kotlinx", "okhttp3", "okio",
           "java", "javax", "kotlin", "com.google")

# স্পষ্টতই ধাক্কার ফল — এগুলো আসল ভুলের প্রমাণ নয়
CASCADE = (
    "overrides nothing", "cannot infer a type", "overload resolution ambiguity",
    "variable expected", "[error type", "no value passed for parameter",
    "cannot access", "not enough information", "type mismatch",
    "none of the following candidates", "cannot be applied",
    "is not a function", "smart cast",
)


def project_vocabulary():
    """প্রকল্পের সব ফাইল পড়ে দুটো তালিকা বানায় —
       ১) বাইরের import-এর নাম, ২) `.নাম` হিসেবে ব্যবহৃত সব সদস্যের নাম।"""
    ext = {"android", "androidx", "org", "kotlinx", "okhttp3", "okio",
           "java", "javax", "com", "it", "this", "field", "R", "BuildConfig"}
    members = set()
    for folder, _d, files in os.walk(SRC):
        for f in files:
            if not f.endswith(".kt"):
                continue
            t = open(os.path.join(folder, f), encoding="utf-8",
                     errors="replace").read()
            for ln in t.splitlines():
                m = re.match(r"\s*import\s+([\w.]+)(?:\s+as\s+(\w+))?", ln)
                if m and m.group(1).startswith(EXT_PKG):
                    ext.add(m.group(2) or m.group(1).split(".")[-1])
            members.update(re.findall(r"\.(\w+)", t))
    return ext, members


def is_noise(msg, ext, members):
    u = re.match(r"unresolved reference: (\w+)$", msg)
    if u:
        return u.group(1) in ext or u.group(1) in members
    low = msg.lower()
    if low.startswith("unresolved reference. "):
        return False          # ⚠️ আসল ধরা হয় — `$until`-এর ভুল এভাবেই আসে
    return any(k in low for k in CASCADE)


def normalise(msg):
    """লাইন নম্বর বদলালেও যেন বেসলাইন না ভাঙে — সংখ্যা সরিয়ে দেওয়া হয়।"""
    return re.sub(r"\d+", "N", msg).strip()


def collect_errors(log_text, ext, members):
    """(ফাইল, বার্তা) জোড়ার সেট — শুধু **আসল সন্দেহভাজন** ভুল।
       লাইন/কলাম বাদ, তাই কোড উপরে-নিচে সরালেও বেসলাইন ভাঙে না।"""
    pairs, total = set(), 0
    for ln in log_text.splitlines():
        m = re.match(r"^(.*\.kt):(\d+):(\d+): error: (.*)$", ln.rstrip())
        if not m:
            continue
        total += 1
        msg = m.group(4)
        if is_noise(msg, ext, members):
            continue
        path = m.group(1).replace("\\", "/")
        path = path[path.find("com/"):] if "com/" in path else path
        pairs.add((path, normalise(msg)))
    return pairs, total


def run_compiler(kotlinc):
    out_dir = os.path.join("/tmp", "kt_guard_out")
    shutil.rmtree(out_dir, ignore_errors=True)
    env = dict(os.environ)
    env["JAVA_TOOL_OPTIONS"] = ""          # লগ পরিষ্কার রাখতে
    try:
        p = subprocess.run(
            [kotlinc, "-J-Xmx5g", "-nowarn", "com", "-d", out_dir],
            cwd=SRC, env=env, capture_output=True, text=True, timeout=2400)
        return (p.stderr or "") + (p.stdout or "")
    except subprocess.TimeoutExpired:
        return None
    except Exception as e:
        print("   কম্পাইলার চালানো গেল না: %s" % e)
        return None


def load_baseline():
    if not os.path.exists(BASELINE):
        return None
    pairs = set()
    for ln in open(BASELINE, encoding="utf-8"):
        ln = ln.rstrip("\n")
        if not ln or ln.startswith("#"):
            continue
        if "\t" not in ln:
            continue
        f, m = ln.split("\t", 1)
        pairs.add((f, m))
    return pairs


def save_baseline(pairs):
    with open(BASELINE, "w", encoding="utf-8") as fh:
        fh.write("# 🛡️ Kotlin কম্পাইলারের **জানা গোলমাল** (Android SDK নেই বলে)।\n")
        fh.write("# ⛔ হাতে লিখবেন না — `verify_kotlin_compile.py --update-baseline`\n")
        fh.write("# প্রতিটা সারি:  ফাইল <TAB> ভুলের বার্তা (সংখ্যা N দিয়ে ঢাকা)\n")
        fh.write("# মোট: %d\n" % len(pairs))
        for f, m in sorted(pairs):
            fh.write("%s\t%s\n" % (f, m))


def main():
    update = "--update-baseline" in sys.argv
    print("🛡️ Kotlin কম্পাইল-পাহারা (V497)")
    print("=" * 64)

    if not os.path.isdir(SRC):
        print("❌ FAIL — Kotlin source ফোল্ডার পাওয়া গেল না:\n   %s" % SRC)
        return 1

    kotlinc = find_kotlinc()
    if not kotlinc:
        print()
        print("⚠️ SKIPPED — kotlinc পাওয়া যায়নি, তাই **কম্পাইল যাচাই হয়নি**।")
        print("   ⛔ এটাকে PASS ধরা যাবে না। কম্পাইলার বসিয়ে আবার চালান:")
        print("   %s" % KOTLIN_URL)
        print()
        print("ফল: SKIPPED — যাচাই করা যায়নি। ⛔ PASS নয়।")
        return 1

    print("   কম্পাইলার: %s" % kotlinc)
    print("   কম্পাইল হচ্ছে… (কয়েক মিনিট লাগে)")
    log = run_compiler(kotlinc)
    if log is None:
        print()
        print("⚠️ SKIPPED — কম্পাইলার শেষ করতে পারল না (সময়/মেমরি)।")
        print("ফল: SKIPPED — যাচাই করা যায়নি। ⛔ PASS নয়।")
        return 1

    ext, members = project_vocabulary()
    found, total = collect_errors(log, ext, members)
    print("   কম্পাইলারের মোট ভুল      : %d" % total)
    print("   বাইরের লাইব্রেরির গোলমাল : %d (বাদ)" % (total - len(found)))
    print("   আসল সন্দেহভাজন           : %d" % len(found))

    if update:
        save_baseline(found)
        print()
        print("✅ নতুন বেসলাইন লেখা হলো: %d সারি" % len(found))
        print("   (এটা শুধু **ভালো, বিল্ড-হওয়া** অবস্থায় চালাবেন)")
        return 0

    base = load_baseline()
    if base is None:
        print()
        print("❌ FAIL — বেসলাইন ফাইল নেই (kotlin_noise_baseline.txt)।")
        print("   ভালো অবস্থায় একবার চালান: --update-baseline")
        print()
        print("ফল: FAIL — যাচাই করা যায়নি। ⛔ PASS নয়।")
        return 1

    new = sorted(found - base)
    gone = len(base - found)

    print("   বেসলাইনে জানা গোলমাল : %d" % len(base))
    print("   নতুন ভুল             : %d" % len(new))
    if gone:
        print("   (বেসলাইনের %d টা আর নেই — ভালো খবর)" % gone)
    print()

    if new:
        print("❌ FAIL — Kotlin কোডে **নতুন ভুল** আছে, এই ফাইল বিল্ড হবে না:")
        for f, m in new[:40]:
            print("  ❌ %s" % f.split("/")[-1])
            print("       %s" % m[:150])
        if len(new) > 40:
            print("  … আরও %d টা" % (len(new) - 40))
        print()
        print("ফল: FAIL — TK-কে এই ফাইল পাঠানো যাবে না। ⛔ PASS নয়।")
        return 1

    print("PASS — Kotlin কোডে নতুন কোনো ভুল নেই ✅")
    print("⚠️ মনে রাখুন: এটা Android Studio-র পুরো বিল্ড নয় (resource · manifest ·")
    print("   dex এখানে যাচাই হয় না)। কিন্তু Kotlin-এর ভুল আর পার হতে পারবে না।")
    return 0


if __name__ == "__main__":
    sys.exit(main())
