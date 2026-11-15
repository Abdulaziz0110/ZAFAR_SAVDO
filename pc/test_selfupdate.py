#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import hashlib
import io
import json
import os
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import selfupdate  # noqa: E402


def make_zip(files):
    b = io.BytesIO()
    with zipfile.ZipFile(b, "w") as z:
        for n, d in files.items():
            z.writestr(n, d)
    raw = b.getvalue()
    return raw, hashlib.sha256(raw).hexdigest()


class Cloud:
    def __init__(self, url):
        self.url = url

    def call(self, method, path, timeout=30, **q):
        return self.url


GOOD = {"onec_export.py": "x = 2\n", "relay.py": "y = 1\n", "pc_version.txt": "7", "OQING.txt": "salom", "pos/index.html": "<html></html>"}


class SelfUpdateTest(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp()
        with open(os.path.join(self.d, "onec_export.py"), "w") as f:
            f.write("x = 1\n")
        with open(os.path.join(self.d, "forwarder.ini"), "w") as f:
            f.write("[firebase]\nsecret = MAXFIY\n")
        with open(os.path.join(self.d, "pc_version.txt"), "w") as f:
            f.write("5")

    def fetcher(self, raw, sha, v=7):
        def f(url, timeout=60, limit=0):
            return json.dumps({"v": v, "zip": "pc.zip", "sha": sha}).encode() if "pc.json" in url else raw
        return f

    def read(self, n):
        with open(os.path.join(self.d, n)) as f:
            return f.read()

    def test_updates_and_keeps_settings(self):
        files = dict(GOOD)
        files["forwarder.ini"] = "[firebase]\nsecret = BEGONA\n"
        files["relay.json"] = "{}"
        raw, sha = make_zip(files)
        self.assertTrue(selfupdate.check(Cloud("https://x.pages.dev/u/abc/"), "i", self.d, self.fetcher(raw, sha)))
        self.assertEqual(self.read("onec_export.py"), "x = 2\n")
        self.assertEqual(self.read("pc_version.txt"), "7")
        self.assertIn("MAXFIY", self.read("forwarder.ini"))
        self.assertFalse(os.path.exists(os.path.join(self.d, "relay.json")))
        self.assertTrue(os.path.exists(os.path.join(self.d, "pos", "index.html")))

    def test_same_or_older_version_ignored(self):
        raw, sha = make_zip(GOOD)
        self.assertFalse(selfupdate.check(Cloud("https://x/u/"), "i", self.d, self.fetcher(raw, sha, v=5)))
        self.assertEqual(self.read("onec_export.py"), "x = 1\n")

    def test_bad_sha_changes_nothing(self):
        raw, _ = make_zip(GOOD)
        with self.assertRaises(ValueError):
            selfupdate.check(Cloud("https://x/u/"), "i", self.d, self.fetcher(raw, "0" * 64))
        self.assertEqual(self.read("onec_export.py"), "x = 1\n")
        self.assertEqual(self.read("pc_version.txt"), "5")

    def test_broken_python_changes_nothing(self):
        files = dict(GOOD)
        files["relay.py"] = "def (:\n"
        raw, sha = make_zip(files)
        with self.assertRaises(SyntaxError):
            selfupdate.check(Cloud("https://x/u/"), "i", self.d, self.fetcher(raw, sha))
        self.assertEqual(self.read("onec_export.py"), "x = 1\n")

    def test_no_url_no_update(self):
        self.assertFalse(selfupdate.check(Cloud(None), "i", self.d, None))
        self.assertFalse(selfupdate.check(Cloud("http://x/"), "i", self.d, None))

    def test_safe_name(self):
        self.assertIsNone(selfupdate.safe_name("../evil.py"))
        self.assertIsNone(selfupdate.safe_name("C:/x.py"))
        self.assertIsNone(selfupdate.safe_name("a/b/c.py"))
        self.assertIsNone(selfupdate.safe_name("onec.ini"))
        self.assertIsNone(selfupdate.safe_name("x.exe"))
        self.assertEqual(selfupdate.safe_name("pos/app.js"), "pos/app.js")

    def test_watch(self):
        w = selfupdate.Watch(self.d)
        w.at = 0
        self.assertFalse(w.changed())
        with open(os.path.join(self.d, "pc_version.txt"), "w") as f:
            f.write("9")
        w.at = 0
        self.assertTrue(w.changed())


if __name__ == "__main__":
    unittest.main()
