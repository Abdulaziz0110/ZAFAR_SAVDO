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
                                                "Продажа", "a b c"])
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
        with open(os.path.join(self.jobs, "1c.alive"), "w", encoding="utf-8-sig") as f:
            f.write(ob.BRIDGE_VERSION)
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
                os.remove(os.path.join(self.jobs, "1c.alive"))
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
        os.utime(os.path.join(br.jobs, "1c.alive"), None)
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
        with open(os.path.join(br.jobs, "1c.alive"), "w") as f:
            f.write("1")
        old = Fake1C(br.jobs)

        def stop_old(_):
            if os.path.exists(os.path.join(br.jobs, "stop.flag")):
                os.remove(os.path.join(br.jobs, "1c.alive"))
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


if __name__ == "__main__":
    unittest.main()
