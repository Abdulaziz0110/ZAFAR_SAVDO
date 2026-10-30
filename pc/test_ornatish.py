#!/usr/bin/env python3
"""ornatish.py ning sof qismlari (Windows'siz):  python pc/test_ornatish.py"""
import configparser
import importlib.util
import os
import socket
import threading
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("ornatish", os.path.join(HERE, "ornatish.py"))
O = importlib.util.module_from_spec(spec)
spec.loader.exec_module(O)

IBASES = """\ufeff[Dokon baza]
Connect=File="D:\\1C\\Dokon";
ID=1234
OrderInList=1
[Server baza]
Connect=Srvr="srv1";Ref="trade";
[Bo'sh]
ID=1
"""


class T(unittest.TestCase):
    def test_ibases(self):
        b = O.parse_ibases(IBASES)
        self.assertEqual(b, [("Dokon baza", 'File="D:\\1C\\Dokon";'), ("Server baza", 'Srvr="srv1";Ref="trade";')])

    def test_conn_input(self):
        self.assertEqual(O.conn_from_input('D:\\1C\\Baza'), 'File="D:\\1C\\Baza";')
        self.assertEqual(O.conn_from_input('"D:\\1C\\Baza"'), 'File="D:\\1C\\Baza";')
        self.assertEqual(O.conn_from_input('Srvr="s";Ref="b"'), 'Srvr="s";Ref="b";')

    def test_onec_ini_keeps_codes(self):
        old = "[onec]\nconnection = X\nreport = Kartochka\n\n[codes]\nplu = attr:ПЛУ\n"
        t = O.onec_ini_text('File="D:\\B";', "Ali", "p%w;\"", keep=old)
        cp = configparser.ConfigParser(interpolation=None)
        cp.read_string(t)
        self.assertEqual(cp["onec"]["connection"], 'File="D:\\B";')
        self.assertEqual(cp["onec"]["password"], 'p%w;"')
        self.assertEqual(cp["onec"]["report"], "Kartochka")
        self.assertEqual(cp["codes"]["plu"], "attr:ПЛУ")
        t2 = O.onec_ini_text("c", "u", "", keep=None)
        self.assertIn("[codes]", t2)

    def test_firebase_check(self):
        good = "[firebase]\nurl = x.firebaseio.com\nsecret = s\nqueue =\ninbox = " + "a" * 32 + "\n[labels]\nkey = —\n"
        self.assertEqual(O.firebase_ok(good), (True, ""))
        self.assertFalse(O.firebase_ok("[firebase]\nurl = x\nsecret = s\ninbox = short\n")[0])
        self.assertFalse(O.firebase_ok("matn")[0])

    def test_startup_vbs(self):
        v = O.startup_vbs(r"C:\Py\pythonw.exe", r"D:\imsoft\onec_export.py", r"D:\imsoft")
        self.assertIn('sh.Run """C:\\Py\\pythonw.exe"" ""D:\\imsoft\\onec_export.py""", 0, False', v)
        v.encode("ascii")

    def test_lpr_send(self):
        srv = socket.socket()
        srv.bind(("127.0.0.1", 0))
        srv.listen(1)
        got = {}

        def serve():
            c, _ = srv.accept()
            f = c.makefile("rb")
            got["q"] = f.readline()
            c.sendall(b"\0")
            for _ in range(2):
                sub = f.readline()
                n = int(sub[1:].split(b" ")[0])
                c.sendall(b"\0")
                body = f.read(n)
                f.read(1)
                got[sub[:1]] = body
                c.sendall(b"\0")
            c.close()
        t = threading.Thread(target=serve)
        t.start()
        data = O.lpd_test_tspl()
        O.lpr_send("127.0.0.1", "xprinter", data, port=srv.getsockname()[1])
        t.join(5)
        srv.close()
        self.assertEqual(got["q"], b"\x02xprinter\n")
        self.assertEqual(got[b"\x03"], data)
        self.assertIn(b"ldfA", got[b"\x02"])
        self.assertTrue(data.startswith(b"SIZE 58 mm,40 mm"))


if __name__ == "__main__":
    unittest.main(verbosity=1)
