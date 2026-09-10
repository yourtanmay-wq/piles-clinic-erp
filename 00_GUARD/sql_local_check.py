#!/usr/bin/env python3
"""
🔴 V1287 (১০.০৯.২০২৬) — TK: *"এরকম ভুল যেন বারবার না করেন — সত্যতা যাচাই করে
গভীরে যাচাই করে তবেই আমাকে পাঠাবেন"*

যে ভুলটা এটা ধরে:
  TK-কে কপি-পেস্টের SQL পাঠানোর আগে সেটা কোথাও চালিয়ে দেখা হত না — ফলে
  ১০.০৯.২০২৬-এ পরপর দু'বার (sum(text) · column "k" does not exist) TK-র
  পর্দায় গিয়ে ভুল ধরা পড়েছে। TK-র সময় নষ্ট, ভরসা কম।

কীভাবে ধরে (আন্দাজে নয়):
  এই কম্পিউটারে একটা **নকল, খালি PostgreSQL 16** চালু করে — TK-র আসল
  ডেটাবেসের টেবিল-গঠন (PILES_CLINIC_DB_SETUP.sql + সব PATCH + সব
  `add column if not exists` লাইন) বসিয়ে — SQL-টা সেখানে চালায়।
  ভুল থাকলে এখানেই থামে; TK-র কাছে যায় শুধু পাশ-করা SQL।
  ⛔ লাইভ Supabase ছোঁয়া হয় না। ⛔ চালানোর পরে ROLLBACK — কিছু থাকে না।

ব্যবহার:  python3 00_GUARD/sql_local_check.py 00_SQL/XYZ.sql
          python3 00_GUARD/sql_local_check.py --stdin  < query.sql
"""
import glob, os, re, subprocess, sys, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PGBIN = "/usr/lib/postgresql/16/bin"
PGDATA = os.environ.get("TK_PGDATA", "/var/lib/postgresql/tk16")
SOCK = "/var/run/postgresql"
PORT = os.environ.get("TK_PGPORT", "54329")
DB = "tk_sql_check"
ENV = dict(os.environ, PGHOST=SOCK, PGPORT=PORT, PGUSER="postgres")

def as_pg(cmd):
    return (["runuser", "-u", "postgres", "--"] if os.geteuid() == 0 else []) + cmd

def run(cmd, **kw):
    return subprocess.run(cmd, capture_output=True, text=True, env=ENV, **kw)

def psql(sql=None, file=None, db="postgres", stop=False):
    cmd = ["psql", "-X", "-q", "-At", "-d", db]
    if stop: cmd += ["-v", "ON_ERROR_STOP=1"]
    if file: cmd += ["-f", file]
    return run(cmd, input=sql)

def ensure_server():
    r = run(["pg_isready", "-h", SOCK, "-p", PORT])
    if r.returncode == 0: return
    if not os.path.isdir(os.path.join(PGDATA, "data")):
        subprocess.run(as_pg(["mkdir", "-p", PGDATA]), capture_output=True)
        r = subprocess.run(as_pg([f"{PGBIN}/initdb", "-D", f"{PGDATA}/data", "-U", "postgres", "-A", "trust", "-E", "UTF8", "--locale=C.utf8"]),
                           capture_output=True, text=True)
        if r.returncode: sys.exit("initdb ব্যর্থ:\n" + r.stderr[-800:])
    r = subprocess.run(as_pg([f"{PGBIN}/pg_ctl", "-D", f"{PGDATA}/data", "-l", f"{PGDATA}/log", "-w",
                              "-o", f"-k {SOCK} -p {PORT} -c listen_addresses=''", "start"]),
                       capture_output=True, text=True)
    for _ in range(20):
        if run(["pg_isready", "-h", SOCK, "-p", PORT]).returncode == 0: return
        time.sleep(0.5)
    sys.exit("নকল PostgreSQL চালু হল না:\n" + r.stderr[-800:])

ADD_COL = re.compile(r'alter\s+table\s+(?:if\s+exists\s+)?(?:public\.)?("?[A-Za-z_]+"?)\s+add\s+column\s+if\s+not\s+exists\s+("?[A-Za-z_]+"?)\s+([^;,]+?)\s*;', re.I | re.S)

def build_schema():
    psql(f"drop database if exists {DB}; create database {DB};")
    base = os.path.join(ROOT, "04_SUPABASE_DATABASE_SETUP", "PILES_CLINIC_DB_SETUP.sql")
    r = psql(file=base, db=DB)
    errs = [l for l in r.stderr.splitlines() if "ERROR" in l]
    files = sorted(glob.glob(os.path.join(ROOT, "04_SUPABASE_DATABASE_SETUP", "*PATCH*.sql")))
    for f in files:
        psql(file=f, db=DB)   # hr/wn schema নেই ⇒ ওই লাইনগুলো নিঃশব্দে বাদ
    # সব SQL ফাইল থেকে শুধু "add column if not exists" — লাইভে যা যা ঘর যোগ হয়েছে
    added = 0
    for f in sorted(glob.glob(os.path.join(ROOT, "00_SQL", "*.sql")) +
                    glob.glob(os.path.join(ROOT, "04_SUPABASE_DATABASE_SETUP", "*.sql"))):
        try: txt = open(f, encoding="utf-8", errors="ignore").read()
        except Exception: continue
        for tbl, col, typ in ADD_COL.findall(txt):
            typ = re.sub(r'\s+(default|references|check|generated)\b.*$', '', typ.strip(), flags=re.I | re.S)
            typ = re.sub(r'\s+not\s+null\b', '', typ, flags=re.I)   # নকলে not-null চাই না (পরীক্ষার সারি ঢোকাতে)
            r2 = psql(f'alter table public.{tbl} add column if not exists {col} {typ};', db=DB)
            if r2.returncode == 0: added += 1
    # লাইভে আছে, কিন্তু কোনো ফাইলে লেখা নেই — জানা ঘর
    for line in ["followups noMoreCalls boolean", "followups lastRemarkAt timestamptz",
                 "patients discount text", "patients billBeforeDiscount text"]:
        t, c, ty = line.split()
        psql(f'alter table public.{t} add column if not exists "{c}" {ty};', db=DB)
    r = psql("select count(*) from information_schema.columns where table_schema='public'", db=DB)
    return errs, added, r.stdout.strip()

def main():
    if len(sys.argv) < 2: sys.exit(__doc__)
    if sys.argv[1] == "--stdin": sql = sys.stdin.read(); name = "(stdin)"
    else:
        name = sys.argv[1]; sql = open(name, encoding="utf-8").read()
    ensure_server()
    errs, added, ncols = build_schema()
    print(f"নকল ডেটাবেস তৈরি — বাড়তি ঘর {added} · মোট ঘর {ncols} · গঠনে ভুল {len(errs)}")
    if re.search(r'"?updatedAt"?\s*=\s*now\(\)', sql, re.I):
        print("⚠️ সতর্কতা: \"updatedAt\" = now() — অ্যাপের ছাঁচ নয় (T…Z); to_char(now() at time zone 'utc','YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') লিখুন (তালিকা ৪১১-⑦)")
    wrapped = "begin;\n" + sql.rstrip().rstrip(";") + ";\nrollback;\n"
    r = psql(wrapped, db=DB, stop=True)
    out = (r.stdout or "").strip()
    err = [l for l in (r.stderr or "").splitlines() if "ERROR" in l or "LINE" in l or "HINT" in l]
    print("--- ফল (খালি ডেটাবেস, তাই সারি ০/ফাঁকা হওয়াই স্বাভাবিক) ---")
    print("\n".join(out.splitlines()[:12]) if out else "(কোনো output নেই)")
    if r.returncode or err:
        print("\n".join(err[:8]))
        print(f"\n❌ FAIL — এই SQL TK-কে পাঠানো যাবে না: {name}")
        sys.exit(1)
    print(f"\n✅ PASS — নকল ডেটাবেসে ভুল ছাড়া চলেছে: {name}")

if __name__ == "__main__":
    main()
