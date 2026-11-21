# -*- coding: utf-8 -*-
"""Panel tavsiyasi: tarozi tovarlari oilalar bo'yicha, keyin shtrix kodli, keyin boshqalar."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kassa_panel as kp  # noqa: E402


def g(c, n, e="kg"):
    return {"c": c, "n": n, "e": e}


class SuggestTest(unittest.TestCase):
    def test_families_sections_pages(self):
        goods = [g("1", "Pista Eron"), g("2", "Bodom Amerika"), g("3", "Pista Afg'on"), g("4", "Кешью жареный", "кг"),
                 g("5", "Bodom Xitoy"), g("6", "Cola 1.5", "dona"), g("7", "Non", "dona"), g("8", "Sotilmagan"),
                 g("9", "B Mayiz 134")]
        stats = {"1": 30, "2": 10, "3": 5, "4": 8, "5": 9, "6": 50, "7": 3, "9": 1}
        r = kp.suggest(goods, stats, {"4780001": "6"})
        names = [x[1] for x in r["pnl"][:6]]
        # pistalar (35) -> bodomlar (19) -> keshu (8) -> mayiz (1); oila ichida nomi bo'yicha
        self.assertEqual(names, ["Pista Afg'on", "Pista Eron", "Bodom Amerika", "Bodom Xitoy", "Кешью жареный", "B Mayiz 134"])
        self.assertEqual(r["pnl"][42], ["6", "Cola 1.5"])          # shtrix kodli — 2-sahifa
        self.assertEqual(r["pnl"][84], ["7", "Non"])               # boshqa — 3-sahifa
        self.assertNotIn(["8", "Sotilmagan"], r["pnl"])
        self.assertEqual([(m["t"], m["from"], m["n"]) for m in r["sec"]],
                         [("TAROZI (KG)", 0, 6), ("SHTRIX KODLI", 42, 1), ("BOSHQA", 84, 1)])
        self.assertEqual(len(r["pnl"]), 336)

    def test_family_not_split_and_caps(self):
        goods = [g(str(i), "Aaa %02d" % i) for i in range(40)] + [g(str(100 + i), "Pista %02d" % i) for i in range(10)]
        stats = {x["c"]: 1 for x in goods}
        stats.update({str(100 + i): 10 for i in range(10)})
        r = kp.suggest(goods, stats, {})
        self.assertTrue(r["pnl"][0][1].startswith("Pista"))
        self.assertEqual(r["pnl"][10], ["", ""])                     # 40 talik oila keyingi sahifaga to'liq
        self.assertEqual(r["pnl"][42][1], "Aaa 00")
        many = [g(str(i), "Tovar%d" % i) for i in range(500)]
        r = kp.suggest(many, {x["c"]: 500 - int(x["c"]) for x in many}, {})
        self.assertEqual(sum(1 for x in r["pnl"] if x[0]), kp.PAGE * 4)   # tarozi ko'pi bilan 4 sahifa
        self.assertEqual(r["pnl"][0][0], "0")                              # eng ko'p sotilgani birinchi

    def test_fold_family(self):
        self.assertEqual(kp.family("B Mayiz 134"), "mayiz")
        self.assertEqual(kp.family("Кешью"), "keshyu")
        self.assertEqual(kp.family("O'rik qoqi"), "orik")
        self.assertTrue(kp.is_weighted({"e": "КГ"}))
        self.assertFalse(kp.is_weighted({"e": "dona"}))


if __name__ == "__main__":
    unittest.main()


class FillTest(unittest.TestCase):
    """pc 40: bo'sh joylarga tavsiya."""

    def test_fill_only_empty_and_no_factory_barcodes(self):
        goods = [{"c": "1", "n": "Pista Eron", "e": "kg"}, {"c": "2", "n": "Cola 1L", "e": "dona"},
                 {"c": "3", "n": "Non", "e": "dona"}, {"c": "4", "n": "Tuz", "e": "dona"}, {"c": "5", "n": "Sabzi", "e": "kg"}]
        bars = {"4780001234567": "2", "2000000000031": "3", "4780009": "4"}       # 2 — zavod, 3 — 1C ichki, 4 — qisqa
        cur = [["9", "Qo'lda"], ["", ""], ["", ""]]
        r = kp.fill(cur, goods, {"1": 5, "2": 50, "3": 9, "5": 1}, {"4": 1}, bars, slots=6)
        self.assertEqual(r["pnl"][0], ["9", "Qo'lda"])
        self.assertEqual([x[0] for x in r["pnl"]], ["9", "1", "5", "3", "4", ""])  # kg, keyin 1C shtrixli (sotuv+qidiruv)
        self.assertEqual(r["new"], [1, 2, 3, 4])
        self.assertTrue(kp.is_factory_barcode("4780001234567"))
        self.assertFalse(kp.is_factory_barcode("2000000000031"))
        self.assertFalse(kp.is_factory_barcode("12345"))

    def test_apply_new_respects_later_changes(self):
        sug = {"pnl": [["", ""], ["1", "A"], ["2", "B"]], "new": [1, 2]}
        out = kp.apply_new([["", ""], ["", ""], ["7", "Q"], ["1", "A"]], sug, slots=4)
        self.assertEqual(out, [["", ""], ["", ""], ["7", "Q"], ["1", "A"]])         # 1 allaqachon bor, 2-joy band
