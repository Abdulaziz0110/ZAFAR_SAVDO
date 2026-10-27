#!/usr/bin/env python3
"""
HistoryDb (dokon_tarix.sqlite) SQL tekshiruvi — telefonsiz.

HistoryDb.kt dan haqiqiy SQL matnlari (addColumn ta'riflari, COLS, claimNext so'rovi) o'qib olinadi va Python sqlite3 da:
  1) v2.2 sxemasidagi bazaga ma'lumot yoziladi (eski navbat qatorlari bilan),
  2) v3 migratsiyasi bajariladi,
  3) qatorlar soni va tarkibi o'zgarmaganligi, eski navbat qatorlari UNCERTAIN bo'lganligi,
  4) atomik "egallash" so'rovi ikki marta bir qatorni bermasligi tekshiriladi.
Bu Android SQLite'ning o'zi emas, lekin sintaksis va mantiq xatolarini ushlaydi.
Ishga tushirish: python3 tools/sql_smoke_test.py
"""
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/ilxomjon/dur_novvot_dokon/data/local/HistoryDb.kt")
LOCAL = os.path.join(ROOT, "app/src/main/java/com/ilxomjon/dur_novvot_dokon/data/local/LocalDb.kt")
kt = open(SRC, encoding="utf-8").read()


def fail(msg):
    print("FAIL:", msg)
    sys.exit(1)


# --- Kotlin'dan SQL olish ---
v3_block = kt[kt.index("private fun upgradeToV3"):kt.index("private fun addColumn")]
columns = re.findall(r'addColumn\(db, "(\w+)", "(\w+)", "([^"]+)"\)', v3_block)
creates = re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX) IF NOT EXISTS [^"]+)"\)', v3_block)
updates = re.findall(r'db\.execSQL\("(UPDATE outbox SET local_uuid[^"]+)"\)', v3_block)
cols = re.search(r'COLS = "([^"]+)" \+\s*"([^"]+)"', kt)
claim = re.search(r'val sql = "(SELECT \$COLS FROM outbox WHERE[^"]+)" \+\s*"([^"]+)"', kt)
claim_upd = re.search(r'"(UPDATE outbox SET state = \'SENDING\'[^"]+)"', kt)
if not (columns and creates and cols and claim and claim_upd):
    fail("HistoryDb.kt dan SQL topilmadi (fayl tuzilmasi o'zgarganmi?)")
COLS = cols.group(1) + cols.group(2)
CLAIM = (claim.group(1) + claim.group(2)).replace("$COLS", COLS)
CLAIM_UPD = claim_upd.group(1)

# --- v2.2 sxemasi (HistoryDb v2) ---
db = sqlite3.connect(":memory:", isolation_level=None)
db.executescript("""
CREATE TABLE sent_order (id INTEGER PRIMARY KEY AUTOINCREMENT, sent_at INTEGER NOT NULL, store_name TEXT, mode TEXT, price_type TEXT,
  currency TEXT, total REAL, discount REAL, paid REAL, lines TEXT, store_id TEXT DEFAULT '', pending INTEGER DEFAULT 0, outbox_id INTEGER DEFAULT 0);
CREATE TABLE sent_payment (id INTEGER PRIMARY KEY AUTOINCREMENT, sent_at INTEGER NOT NULL, store_name TEXT, cash REAL, transfer REAL, click REAL,
  note TEXT, store_id TEXT DEFAULT '', pending INTEGER DEFAULT 0, outbox_id INTEGER DEFAULT 0);
CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, agent_id TEXT, payload TEXT NOT NULL, created_at INTEGER,
  attempts INTEGER DEFAULT 0, last_error TEXT);
""")
for i in range(1, 51):
    db.execute("INSERT INTO sent_order (sent_at, store_name, mode, price_type, currency, total, discount, paid, lines, store_id, pending, outbox_id) "
               "VALUES (?, 'D', '1', '1', '0', 100, 0, 0, '[]', 'S1', ?, ?)", (i, 1 if i > 47 else 0, i if i > 47 else 0))
for i in range(48, 51):
    db.execute("INSERT INTO outbox (id, kind, agent_id, payload, created_at, attempts) VALUES (?, 'order', '7', ?, ?, ?)", (i, "p%d" % i, i, i % 2))
before = {t: db.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0] for t in ("sent_order", "sent_payment", "outbox")}
snapshot = db.execute("SELECT id, kind, agent_id, payload, created_at, attempts FROM outbox ORDER BY id").fetchall()

# --- v3 migratsiyasi (tranzaksiyada, Android'dagidek) ---
db.execute("BEGIN")
for table, col, definition in columns:
    existing = [r[1] for r in db.execute("PRAGMA table_info(%s)" % table)]
    if col not in existing:
        db.execute("ALTER TABLE %s ADD COLUMN %s %s" % (table, col, definition))
for u in updates:
    db.execute(u)
for c in creates:
    db.execute(c)
db.execute("COMMIT")

after = {t: db.execute("SELECT COUNT(*) FROM %s" % t).fetchone()[0] for t in ("sent_order", "sent_payment", "outbox")}
if before != after:
    fail("migratsiyadan keyin qatorlar soni o'zgardi: %s -> %s" % (before, after))
if snapshot != db.execute("SELECT id, kind, agent_id, payload, created_at, attempts FROM outbox ORDER BY id").fetchall():
    fail("eski navbat qatorlari tarkibi o'zgardi")
states = {r[0] for r in db.execute("SELECT state FROM outbox")}
if states != {"UNCERTAIN"}:
    fail("eski navbat qatorlari UNCERTAIN bo'lishi kerak (avval tekshiriladi), topildi: %s" % states)
if db.execute("SELECT COUNT(*) FROM outbox WHERE local_uuid IS NULL OR length(local_uuid) <> 32").fetchone()[0]:
    fail("local_uuid berilmagan")

# Migratsiya ikkinchi marta ishlasa ham buzilmaydi (idempotent).
for table, col, definition in columns:
    existing = [r[1] for r in db.execute("PRAGMA table_info(%s)" % table)]
    if col not in existing:
        fail("ustun yo'q: %s.%s" % (table, col))
for c in creates:
    db.execute(c)

# --- claimNext: atomik egallash ---
db.execute("UPDATE outbox SET state = 'READY'")
now, lease = 1_000_000, 120_000
got = []
for _ in range(5):
    db.execute("BEGIN IMMEDIATE")
    row = db.execute(CLAIM.replace("?", str(now), 2)).fetchone()
    if row is None:
        db.execute("COMMIT")
        break
    state = row[6]
    db.execute(CLAIM_UPD.replace("?", "%s", 5) % ("'%s'" % state, now + lease, now, row[0], "'%s'" % state))
    if db.execute("SELECT changes()").fetchone()[0] != 1:
        fail("egallash changes() != 1")
    db.execute("COMMIT")
    got.append(row[0])
if got != [48, 49, 50]:
    fail("egallash tartibi/takrori noto'g'ri: %s" % got)
# Lease tugamaguncha qayta berilmaydi, tugagach beriladi (natija noma'lum -> probe).
if db.execute(CLAIM.replace("?", str(now + 10), 2)).fetchone() is not None:
    fail("lease tugamasdan qayta berildi")
if db.execute(CLAIM.replace("?", str(now + lease + 1), 2)).fetchone() is None:
    fail("lease tugagach qator berilmadi")

# --- LocalDb: yangi draft_cart jadvali faqat qo'shimcha ---
lk = open(LOCAL, encoding="utf-8").read()
m = re.search(r'"(CREATE TABLE IF NOT EXISTS draft_cart [^"]+)" \+\s*"([^"]+)"', lk)
if not m:
    fail("draft_cart CREATE topilmadi")
db.execute(m.group(1) + m.group(2))
if "DROP" in lk.upper() or "RENAME" in lk.upper():
    fail("LocalDb'da DROP/RENAME bor (C2 buziladi)")

print("OK: v2 -> v3 migratsiya %s qator saqlandi, egallash %s, draft_cart yaratildi" % (after, got))
