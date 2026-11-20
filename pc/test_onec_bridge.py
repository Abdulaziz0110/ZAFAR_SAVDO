# -*- coding: utf-8 -*-
"""1C ko'prigi: .epf konteyneri, topshiriq fayllari, 1C'ni ishga tushirish va natijalar."""
import os
import shutil
import sys
import tempfile
import unittest
from decimal import Decimal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import onec_bridge as ob  # noqa: E402

EXE = r"C:\Program Files (x86)\1cv8\8.3.10.2505\bin\1cv8.exe"


class EpfTest(unittest.TestCase):
    def test_container_round_trip(self):
        raw = ob.build_epf(ob.bsl_text())
        self.assertEqual(raw[:16].hex(), "ffffff7f000200000600000000000000")     # 6 ta element
        self.assertEqual(raw, ob.build_epf(ob.bsl_text()))                     # har safar bir xil
        ents = dict(ob.read_container(raw))
        self.assertEqual(set(ents) - {k for k in ents if len(k) in (36, 38)},
                         {"copyinfo", "root", "version", "versions"})
        meta_id = ents["root"].decode("utf-8-sig")[3:-2]
        self.assertIn(meta_id, ents)
        meta = ents[meta_id].decode("utf-8-sig")
        self.assertIn("c3831ec8-d8d5-4f93-8a22-f9bfae07327f", meta)              # tashqi ishlov
        self.assertIn('"ImsoftKassaBridge"', meta)
        mod = [k for k in ents if k.endswith(".0")][0]
        self.assertIn(mod[:-2], meta)                                          # modul fayli obyektga bog'langan
        inner = ents[mod]
        self.assertEqual(inner[:16].hex(), "ffffff7f000200000100000000000000")
        self.assertIn(b"\r\n00000018 00000018 7fffffff \r\n", inner[16:47])    # mundarija aniq o'lchamda
        files = dict(ob.read_container(inner, packed=False))
        self.assertEqual(files["info"], b'\xef\xbb\xbf{3,1,0,"",0}')
        text = files["text"].decode("utf-8-sig")
        self.assertIn("ЗавершитьРаботуСистемы(Ложь);", text)
        self.assertNotIn("\n", text.replace("\r\n", ""))                      # faqat CRLF
        self.assertEqual(ents["version"], b"\xef\xbb\xbf{\r\n{216,0}\r\n}")
        versions = ents["versions"].decode("utf-8-sig")
        self.assertTrue(versions.startswith("{1,7,"))                          # 6 element + umumiy
        for name in ents:
            self.assertIn('"%s"' % name, versions)

    def test_bsl_matches_1c_sensor_window(self):
        t = ob.bsl_text()
        self.assertNotRegex(t, r"Новый [^;\n(]+\([^)]*\)\.")      # 8.3.10: "Новый X(...).Metod()" kompilyatsiya xatosi
        self.assertIn('Выполнить("ПечатьНажатие_1(Сс, 1)")', t)
        self.assertIn('Выполнить("ТД.Напечатать(Истина)")', t)                 # hisobot — chek printeriga      # bazaning o'z cheki (yo'q bo'lsa ham modul buzilmaydi)
        for must in ("МахсулотКолдигиниАниклаш(ТекущаяДата(), Склад, Тов)", "ПоучитьДолг(ТекущаяДата(), Кл,",
                     "ОхиргиУртачаНархиниАниклаш_Махсулот(Тов, КлНарх, ТекущаяДата())", "Константы.Склад1.Получить()",
                     "Док.Записать(РежимЗаписиДокумента.Проведение)", '".wip"', '".res"', "Стр.Блок     = Стр.Кол / Стр.Шт"):
            self.assertIn(must, t)
        self.assertLess(t.index('".wip"'), t.index("Провести(Ф.ПолноеИмя)"))
        self.assertEqual(t.count("Процедура "), t.count("КонецПроцедуры"))
        self.assertEqual(t.count("Функция "), t.count("КонецФункции"))
        self.assertEqual(t.count("Если "), t.count("КонецЕсли;"))
        self.assertEqual(t.count("Попытка"), t.count("КонецПопытки;"))


class JobTest(unittest.TestCase):
    def test_job_text(self):
        j = {"id": "s1", "kind": "sale", "ptype": "Цена1", "emp": "7", "client": "", "total": Decimal("9000.00"),
             "discount": Decimal("0"), "cash": Decimal("1E+4"), "card": 0, "click": Decimal("0.50"), "doc": "Продажа",
             "note": "a\tb\nc", "lines": [{"code": "15", "qty": Decimal("1.500"), "price": Decimal("6000"),
                                            "sum": Decimal("9000.00")}]}
        lines = ob.job_text(j).split("\r\n")
        self.assertEqual(lines[0].split("\t"), ["H", "s1", "sale", "Цена1", "7", "", "9000", "0", "10000", "0", "0.5",
                                                "Продажа", "a b c", "0"])
        j["print"] = True
        self.assertEqual(ob.job_text(j).split("\r\n")[0].split("\t")[13], "1")         # chekni 1C chiqaradi
        pj = ob.job_text(ob.print_job("p1", "000123", "15.11.2026 09:30")).split("\r\n")[0].split("\t")
        self.assertEqual((pj[2], pj[3], pj[12]), ("print", "15.11.2026", "000123"))
        self.assertEqual(lines[1], "L\t15\t1.5\t6000\t9000")
        self.assertEqual(ob.num(Decimal("-0.00")), "0")

    def test_parse_result(self):
        self.assertEqual(ob.parse_result("\ufeffOK\t000123\t15.11.2026 09:30\r\n"), ("OK", "000123", "15.11.2026 09:30"))
        self.assertEqual(ob.parse_result("ERR\t{Модуль}: Товар етмаяпти"), ("ERR", "{Модуль}: Товар етмаяпти"))
        self.assertEqual(ob.parse_result("salom")[0], "ERR")

    def test_conn_and_command(self):
        c = ob.parse_conn('File="D:\\Baza 1";Usr="Ad""min";Pwd="12;3";')
        self.assertEqual(c, {"file": "D:\\Baza 1", "usr": 'Ad"min', "pwd": "12;3"})
        cmd = ob.command(EXE, 'File="D:\\Baza";Usr="a";', "x.epf", "jobs")
        self.assertEqual(cmd, [EXE, "ENTERPRISE", "/F", "D:\\Baza", "/N", "a", "/Execute", "x.epf", "/C", "jobs",
                               "/DisableStartupMessages", "/DisableStartupDialogs"])
        self.assertEqual(ob.command(EXE, 'Srvr="srv";Ref="shop";', "x", "j")[2:4], ["/S", "srv\\shop"])

    def test_exe_and_conf(self):
        found = [r"C:\Program Files (x86)\1cv8\8.3.9.2170\bin\1cv8.exe", EXE]
        self.assertEqual(ob.pick_exe([], found, exists=lambda p: True), EXE)          # eng yangisi
        self.assertEqual(ob.pick_exe(["C:\\yo'q.exe", found[0]], found, exists=lambda p: "yo'q" not in p), found[0])
        self.assertEqual(ob.pick_exe([], [], exists=lambda p: True), "")
        self.assertEqual(ob.conf_paths(EXE), [r"C:\Program Files (x86)\1cv8\conf\conf.cfg",
                                              r"C:\Program Files (x86)\1cv8\8.3.10.2505\bin\conf\conf.cfg"])
        self.assertEqual(ob.conf_with_unsafe("SystemLanguage=RU"), "SystemLanguage=RU\r\n" + ob.UNSAFE_LINE + "\r\n")
        self.assertIsNone(ob.conf_with_unsafe("a=1\r\nDisableUnsafeActionProtection=.*Baza.*\r\n"))


class ProcTest(unittest.TestCase):
    def test_parse_bridge_procs(self):
        jobs = r"D:\imsoft\kassa_jobs"
        out = ("Node,CommandLine,ProcessId\r\n"
               "PC,\"C:\\1cv8.exe\" ENTERPRISE /F \"D:\\Baza\" /Execute D:\\imsoft\\kassa_bridge.epf /C D:\\imsoft\\kassa_jobs,4120\r\n"
               "PC,\"C:\\1cv8.exe\" ENTERPRISE /F \"D:\\Baza\",777\r\n"                  # kassirning o'z 1C'i — tegilmaydi
               "PC,\"C:\\1cv8.exe\" /Execute D:\\imsoft\\kassa_bridge.epf /C D:\\imsoft\\kassa_jobs_test,999\r\n")
        self.assertEqual(ob.parse_bridge_procs(out, jobs), [4120])                     # sinov 1C'i ham tegilmaydi
        self.assertEqual(ob.parse_bridge_procs(out, r"D:\imsoft\kassa_jobs_test"), [999])

    def test_bsl_never_shows_dialog(self):
        t = ob.bsl_text()
        tail = t[t.rindex("КонецПроцедуры"):]
        self.assertIn("Попытка\n\tИшла();\nИсключение\nКонецПопытки;", tail)          # modul xatosi oyna chiqarmaydi
        self.assertIn("ПереместитьФайл(Врем, Путь)", t)                                 # natija fayli butun holda paydo bo'ladi
        self.assertIn('"\\1c_%s.alive"' % ob.BRIDGE_VERSION, t)


class Fake1C:
    """1C o'rnida: ochilgach 1c.alive yozadi va har "soniyada" papkadagi *.job larni .res qiladi (BSL bilan bir xil)."""

    def __init__(self, jobs, answers=None, start_after=2, die_after=None, never=False):
        self.jobs = jobs
        self.answers = answers or {}
        self.wait = start_after
        self.die_after = die_after
        self.never = never
        self.code = None
        self.n = 0
        self.done = 0

    def poll(self):
        return self.code

    def kill(self):
        self.code = -9

    def cycle(self):
        if self.code is not None or self.never:
            return
        if self.wait > 0:
            self.wait -= 1
            return
        if os.path.exists(os.path.join(self.jobs, "stop.flag")):
            self.code = 0
            return
        with open(os.path.join(self.jobs, "1c_%s.alive" % ob.BRIDGE_VERSION), "w") as f:
            f.write("1")
        for name in sorted(os.listdir(self.jobs)):
            if not name.endswith(".job"):
                continue
            rid = name[:-4]
            base = os.path.join(self.jobs, rid)
            if os.path.exists(base + ".res"):
                os.remove(base + ".job")
                continue
            if os.path.exists(base + ".wip"):
                continue
            open(base + ".wip", "w").close()
            if self.die_after is not None and self.done >= self.die_after:
                self.code = 1                      # 1C yozish paytida o'chdi
                os.remove(os.path.join(self.jobs, "1c_%s.alive" % ob.BRIDGE_VERSION))
                return
            with open(base + ".res", "w", encoding="utf-8-sig") as f:
                self.n += 1
                f.write(self.answers.get(rid, "OK\t%05d\t15.11.2026 09:30" % self.n))
            os.remove(base + ".job")
            os.remove(base + ".wip")
            self.done += 1


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.exe = os.path.join(self.dir, "1cv8.exe")
        open(self.exe, "w").close()
        self.t = [0.0]
        self.cmds = []
        self.procs = []

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def bridge(self, **kw):
        def popen(cmd, **_):
            self.cmds.append(cmd)
            p = Fake1C(cmd[cmd.index("/C") + 1], **kw)
            self.procs.append(p)
            return p

        def sleep(s):
            self.t[0] += s
            for p in self.procs:
                p.cycle()
        return ob.Bridge('File="D:\\Baza";', self.dir, self.exe, popen=popen, clock=lambda: self.t[0], sleep=sleep)

    def job(self, rid):
        return {"id": rid, "kind": "sale", "ptype": "Цена1", "emp": "", "client": "", "total": 1, "discount": 0,
                "cash": 1, "card": 0, "click": 0, "doc": "Продажа", "note": "", "lines": []}

    def test_opened_once_and_stays(self):
        br = self.bridge(answers={"b": "ERR\tТовар етмаяпти"})
        res = br.run([self.job("a"), self.job("b")])
        self.assertEqual(res["a"][0], "OK")
        self.assertEqual(res["b"], ("ERR", "Товар етмаяпти"))
        self.assertTrue(br.alive())
        res = br.run([self.job("c")])                                          # 1C qayta ochilmaydi
        self.assertEqual(res["c"][0], "OK")
        self.assertEqual(len(self.cmds), 1)
        self.assertIn("/Execute", self.cmds[0])
        self.assertEqual(br.leftovers(), ({}, set()))
        br.stop()
        self.procs[0].cycle()
        self.assertEqual(self.procs[0].poll(), 0)                              # stop.flag — 1C o'zi yopiladi

    def test_slow_result_stays_in_folder(self):
        br = self.bridge()
        br.ensure_running()
        self.procs[0].never = True                                             # 1C band (hali yozmadi)
        os.utime(os.path.join(br.jobs, "1c_%s.alive" % ob.BRIDGE_VERSION), None)
        res = br.run([self.job("a")], timeout=2)
        self.assertEqual(res, {})
        self.assertTrue(os.path.exists(os.path.join(br.jobs, "a.job")))        # topshiriq joyida
        self.assertFalse(br.submit(self.job("a")))                             # ikkinchi marta yozilmaydi
        self.procs[0].never = False
        res = br.run([self.job("a")])
        self.assertEqual(res["a"][0], "OK")

    def test_crash_mid_write_is_unknown_not_retried(self):
        br = self.bridge(die_after=1)
        res = br.run([self.job("a"), self.job("b")], timeout=5)
        self.assertEqual(res["a"][0], "OK")
        self.assertNotIn("b", res)
        wip = os.path.join(br.jobs, "b.wip")
        self.assertTrue(os.path.exists(wip))
        self.assertEqual(br.leftovers(), ({}, set()))                         # hali yangi — kutiladi
        old = os.path.getmtime(wip) - 200
        os.utime(wip, (old, old))
        self.assertEqual(br.leftovers(), ({}, {"b"}))                          # eski .wip — noma'lum
        # yangi 1C ochilsa ham .wip li topshiriqqa tegmaydi (ikki marta yozilmasin)
        p = Fake1C(br.jobs)
        p.wait = 0
        p.cycle()
        self.assertTrue(os.path.exists(os.path.join(br.jobs, "b.job")))

    def test_1c_never_starts(self):
        br = self.bridge(never=True)
        with self.assertRaises(RuntimeError):
            br.run([self.job("a")])
        self.assertTrue(self.procs[0].poll() is not None)                      # osilgan 1C yopildi

    def test_no_exe(self):
        br = ob.Bridge("File=x;", self.dir, "")
        with self.assertRaises(RuntimeError):
            br.run([self.job("a")])

    def test_old_version_restarted(self):
        br = self.bridge()
        os.makedirs(br.jobs)
        with open(os.path.join(br.jobs, "1c_1.alive"), "w") as f:
            f.write("1")
        old = Fake1C(br.jobs)

        def stop_old(_):
            if os.path.exists(os.path.join(br.jobs, "stop.flag")):
                os.remove(os.path.join(br.jobs, "1c_1.alive"))
        orig = br.sleep
        br.sleep = lambda s: (stop_old(s), orig(s))
        self.assertTrue(br.ensure_running())
        self.assertEqual(len(self.cmds), 1)
        self.assertTrue(br.alive())
        del old

    def test_leftovers_after_pc_restart(self):
        br = self.bridge()
        os.makedirs(br.jobs)
        with open(os.path.join(br.jobs, "x.res"), "w", encoding="utf-8") as f:
            f.write("OK\t7\t01.01.2026 10:00")
        self.assertEqual(br.leftovers(), ({"x": ("OK", "7", "01.01.2026 10:00")}, set()))


# ---------------------------------------------------------------- pc 30: telefon yozuvlari ko'prik 1C'i orqali (RPC)

class _Ref:
    def __init__(self, code="", name="", empty=False):
        self.Код = code
        self.Наименование = name
        self._empty = empty
        self.obj = None

    def Пустая(self):
        return self._empty

    def УникальныйИдентификатор(self):
        return "uuid-" + str(self.Код or self.Наименование)

    def ПолучитьОбъект(self):
        return self.obj


class _Rows:
    def __init__(self):
        self.rows = []

    def Добавить(self):
        r = type("Row", (), {})()
        self.rows.append(r)
        return r


class _Obj:
    def __init__(self, ref, log):
        self.Ссылка = ref
        self.log = log
        self.ТабШтрихкодов = _Rows()
        self.ШтрихКод = ""

    def Записать(self, *a):
        if hasattr(self, "Наименование"):
            self.Ссылка.Наименование = self.Наименование
        if hasattr(self, "Товар"):
            self.Ссылка.Товар = self.Товар
        self.log.append(("write", self.Ссылка.Наименование))


class _Cat:
    def __init__(self, items, log):
        self.items = items
        self.log = log

    def НайтиПоКоду(self, code):
        for r in self.items:
            if str(r.Код) == str(code):
                return r
        return _Ref(empty=True)

    def НайтиПоНаименованию(self, name, exact=False):
        for r in self.items:
            if r.Наименование == name:
                return r
        return _Ref(empty=True)

    def СоздатьЭлемент(self):
        ref = _Ref(code=str(len(self.items) + 900))
        o = _Obj(ref, self.log)
        ref.obj = o
        self.items.append(ref)
        return o


class Fake1CRpc:
    """BSL'dagi РпцАмал bilan bir xil qoidalar: get/set/call/proc/new/str/eq/free, obyektlar raqam bilan."""

    def __init__(self, folder, root):
        import threading
        self.folder, self.root, self.objs, self.n = folder, root, {}, 0
        self.stop = False
        self.calls = []
        self.t = threading.Thread(target=self.loop, daemon=True)
        self.t.start()

    def to1c(self, z):
        if "h" in z:
            return self.objs[z["h"]]
        if "d" in z:
            return ("date", z["d"])
        if "u" in z:
            return None
        return z["v"]

    def out(self, v):
        if v is None:
            return {"u": 1}
        if isinstance(v, (bool, int, float, str)):
            return {"v": v}
        self.n += 1
        self.objs[self.n] = v
        return {"h": self.n}

    def do(self, q):
        op, name, h = q["op"], q.get("n"), q.get("h", 0)
        ob_ = self.root if not h else self.objs[h]
        args = [self.to1c(a) for a in q.get("a", [])]
        self.calls.append((op, name))
        if op == "get":
            return getattr(ob_, name)
        if op == "set":
            setattr(ob_, name, self.to1c(q["v"]))
            return None
        if op == "call":
            return getattr(ob_, name)(*args)
        if op == "proc":
            getattr(ob_, name)(*args)
            return None
        if op == "str":
            v = args[0]
            return v if isinstance(v, str) else str(v)
        if op == "free":
            self.objs.clear()
            return None
        raise ValueError(op)

    def loop(self):
        import json
        import time
        while not self.stop:
            for f in sorted(os.listdir(self.folder)):
                if not f.endswith(".rpq"):
                    continue
                p = os.path.join(self.folder, f)
                with open(p, encoding="utf-8") as fh:
                    q = json.load(fh)
                try:
                    ans = {"ok": True, "r": self.out(self.do(q))}
                except Exception as e:  # noqa: BLE001
                    ans = {"ok": False, "e": "{Модуль}: %s" % e}
                tmp = p[:-4] + ".rps.part"
                with open(tmp, "w", encoding="utf-8-sig") as fh:
                    json.dump(ans, fh, ensure_ascii=False)
                os.replace(tmp, p[:-4] + ".rps")
                os.remove(p)
            time.sleep(0.002)


class RpcTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.br = ob.Bridge('File="D:\\Baza";', self.dir, "x.exe")
        os.makedirs(self.br.jobs, exist_ok=True)
        with open(os.path.join(self.br.jobs, "1c_%s.alive" % ob.BRIDGE_VERSION), "w") as f:
            f.write("1")
        self.log = []
        g = _Ref("1001", "Garden 1.2 L")
        g.obj = _Obj(g, self.log)
        root = type("Root", (), {})()
        root.Справочники = type("Sp", (), {})()
        root.Справочники.Товары = _Cat([g], self.log)
        root.Справочники.Штрих = _Cat([], self.log)
        self.fake = Fake1CRpc(self.br.jobs, root)
        self.g = g

    def tearDown(self):
        self.fake.stop = True
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_writer_barcode_through_bridge(self):
        import datetime as dt
        import onec_write
        w = onec_write.Writer.__new__(onec_write.Writer)
        w.now = lambda: dt.datetime(2026, 11, 20, 15, 0)
        w.goods_changed = False
        rpc = ob.Rpc(self.br).wait_ready(1)
        res = w.barcode(rpc.root, {"id": "r1", "party": "Garden 1.2 L", "party_code": "1001", "bc": "4870202522466"})
        self.assertTrue(res["ok"], res)
        self.assertEqual(res["goods"], "1001")
        bars = self.fake.root.Справочники.Штрих.items
        self.assertEqual(bars[0].obj.Наименование, "4870202522466")
        self.assertIs(bars[0].obj.Товар, self.g)                                # obyekt raqam orqali qaytdi
        self.assertEqual(bars[0].obj.ДатаПоступ, ("date", "20261120150000"))
        self.assertEqual(self.g.obj.ШтрихКод, "4870202522466")
        self.assertEqual(self.g.obj.ТабШтрихкодов.rows[0].ШтрихКод, "4870202522466")
        self.assertIn(("proc", "Записать"), self.fake.calls)                    # protsedura "Рез = ..." siz chaqiriladi
        # ikkinchi marta — "allaqachon shu tovarda"
        res2 = w.barcode(rpc.root, {"id": "r2", "party": "Garden 1.2 L", "party_code": "1001", "bc": "4870202522466"})
        self.assertEqual(res2["st"], "skip")
        rpc.close()
        self.assertEqual(self.fake.objs, {})

    def test_errors_and_missing_attr(self):
        rpc = ob.Rpc(self.br).wait_ready(1)
        self.assertEqual(getattr(rpc.root.Справочники.Товары.НайтиПоКоду(1001), "Нет", "std"), "std")
        with self.assertRaises(ob.RpcError):
            rpc.root.Справочники.Yoq.НайтиПоКоду(1)
        with self.assertRaises(ob.RpcError):
            rpc.op("get", 0, "Справочники; Удалить")                          # faqat oddiy nomlar
        self.assertEqual(ob.Rpc.enc(Decimal("1.5")), {"v": 1.5})

    def test_bridge_closed(self):
        self.fake.stop = True
        os.remove(os.path.join(self.br.jobs, "1c_%s.alive" % ob.BRIDGE_VERSION))
        with self.assertRaises(ob.RpcError):
            ob.Rpc(self.br, sleep=lambda s: None, clock=iter(range(0, 10000, 50)).__next__).wait_ready(100)


if __name__ == "__main__":
    unittest.main()
