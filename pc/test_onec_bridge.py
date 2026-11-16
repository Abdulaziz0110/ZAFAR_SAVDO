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


class Proc:
    def __init__(self, on_start):
        self.code = None
        self.killed = False
        on_start(self)

    def poll(self):
        return self.code

    def kill(self):
        self.killed = True
        self.code = -9


class BridgeTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.exe = os.path.join(self.dir, "1cv8.exe")
        open(self.exe, "w").close()
        self.t = [0.0]
        self.cmds = []

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def bridge(self, behave):
        def popen(cmd, **kw):
            self.cmds.append(cmd)
            jobs = cmd[cmd.index("/C") + 1]
            return Proc(lambda p: behave(p, jobs))

        def sleep(s):
            self.t[0] += s
        return ob.Bridge('File="D:\\Baza";', self.dir, self.exe, popen=popen, clock=lambda: self.t[0], sleep=sleep)

    @staticmethod
    def fake_1c(answers, stop_after=None):
        """1C o'rnida: har *.job -> .wip, keyin .res (javob answers[id]), oxirida finish.flag."""
        def behave(proc, jobs):
            n = 0
            for name in sorted(os.listdir(jobs)):
                if not name.endswith(".job"):
                    continue
                rid = name[:-4]
                with open(os.path.join(jobs, name), encoding="utf-8") as f:
                    head = f.readline().split("\t")
                assert head[0] == "H" and head[1] == rid
                open(os.path.join(jobs, rid + ".wip"), "w").close()
                if stop_after is not None and n >= stop_after:
                    proc.code = 1            # 1C o'chib qoldi
                    return
                with open(os.path.join(jobs, rid + ".res"), "w", encoding="utf-8-sig") as f:
                    f.write(answers.get(rid, "OK\t%05d\t15.11.2026 09:30" % (n + 1)))
                os.remove(os.path.join(jobs, name))
                os.remove(os.path.join(jobs, rid + ".wip"))
                n += 1
            open(os.path.join(jobs, "finish.flag"), "w").close()
            proc.code = 0
        return behave

    def job(self, rid):
        return {"id": rid, "kind": "sale", "ptype": "Цена1", "emp": "", "client": "", "total": 1, "discount": 0,
                "cash": 1, "card": 0, "click": 0, "doc": "Продажа", "note": "", "lines": []}

    def test_batch_in_one_start(self):
        br = self.bridge(self.fake_1c({"b": "ERR\tТовар етмаяпти"}))
        res = br.run([self.job("a"), self.job("b"), self.job("c")])
        self.assertEqual(len(self.cmds), 1)                                    # bitta 1C ochilishi
        self.assertEqual(res["a"], ("OK", "00001", "15.11.2026 09:30"))
        self.assertEqual(res["b"], ("ERR", "Товар етмаяпти"))
        self.assertEqual(res["c"][0], "OK")
        self.assertTrue(os.path.isfile(os.path.join(self.dir, ob.EPF_NAME)))
        self.assertEqual(br.leftovers(), ({}, set()))                         # hammasi tozalandi

    def test_crash_mid_write_is_unknown_not_retried(self):
        br = self.bridge(self.fake_1c({}, stop_after=1))
        res = br.run([self.job("a"), self.job("b"), self.job("c")])
        self.assertEqual(res["a"][0], "OK")
        self.assertEqual(res["b"][0], "UNKNOWN")                               # yozildimi — noma'lum
        self.assertNotIn("c", res)                                             # boshlanmagan — keyin qayta

    def test_1c_never_starts(self):
        def behave(proc, jobs):
            pass                                                               # osilib qoldi (masalan savol oynasi)
        br = self.bridge(behave)
        with self.assertRaises(RuntimeError):
            br.run([self.job("a")], timeout=30)
        self.assertGreaterEqual(self.t[0], 30)
        self.assertEqual(os.listdir(br.jobs), [])                              # topshiriq olib tashlandi

    def test_no_exe(self):
        br = ob.Bridge("File=x;", self.dir, "")
        with self.assertRaises(RuntimeError):
            br.run([self.job("a")])

    def test_leftovers_after_pc_restart(self):
        br = self.bridge(None)
        os.makedirs(br.jobs)
        with open(os.path.join(br.jobs, "x.res"), "w", encoding="utf-8") as f:
            f.write("OK\t7\t01.01.2026 10:00")
        open(os.path.join(br.jobs, "y.wip"), "w").close()
        self.assertEqual(br.leftovers(), ({"x": ("OK", "7", "01.01.2026 10:00")}, {"y"}))


if __name__ == "__main__":
    unittest.main()
