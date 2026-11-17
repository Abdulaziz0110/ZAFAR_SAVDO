# -*- coding: utf-8 -*-
import os
import sys
import unittest
from decimal import Decimal as D

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import kassa_core as k  # noqa: E402


class ScanTest(unittest.TestCase):
    def test_weight_29(self):
        code, q, w = k.parse_scan("2900123012345")
        self.assertEqual(code, "2900123")
        self.assertEqual(q, D("1.234"))
        self.assertTrue(w)

    def test_piece_28_rongta_and_other(self):
        self.assertEqual(k.parse_scan("2800555031234", rongta=True)[:2], ("2800555", D("3")))
        self.assertEqual(k.parse_scan("2800555000071", rongta=False)[:2], ("2800555", D("7")))

    def test_21_25_and_plain(self):
        self.assertEqual(k.parse_scan("2112345")[0], "2112345")
        self.assertEqual(k.parse_scan("2512345678901")[0], "251234567890")
        self.assertEqual(k.parse_scan(" 4780012345678 ")[:2], ("4780012345678", D("1")))

    def test_leading_zero_kept_raw(self):
        # 1C: Лев(ТЕКНОЛ,1)="0" -> Код = ТЕКНОЛ (xom matn, nollar bilan)
        self.assertEqual(k.parse_scan("0012345")[0], "0012345")

    def test_digits_number_drops_noise(self):
        self.assertEqual(k.digits_number("ab12-03"), "1203")
        self.assertEqual(k.digits_number("000"), "0")
        self.assertEqual(k.digits_number("abc"), "")
        self.assertEqual(k.parse_scan("")[0], "")


class PriceTest(unittest.TestCase):
    def test_som_and_usd(self):
        g = {"u": 0, "p": [1000, 900, 1100, 950], "v": [0, 0, 0, 0]}
        self.assertEqual(k.price_of(g, "Цена3", 12000), D("1100.00"))
        u = {"u": 1, "p": [0, 0, 0, 0], "v": [1.5, 1.4, 0, 0]}
        self.assertEqual(k.price_of(u, "Цена1", 12750), D("19125.00"))
        self.assertEqual(k.price_of(u, "noma'lum", 10000), D("15000.00"))

    def test_cap_qty(self):
        self.assertEqual(k.cap_qty(5, 3, False), (D("3.000"), True))
        self.assertEqual(k.cap_qty(5, -2, False), (D("0.000"), True))
        self.assertEqual(k.cap_qty(5, 3, True), (D("5.000"), False))

    def test_cart_total(self):
        lines = [{"price": D("1000"), "qty": D("1.234")}, {"price": "500", "qty": 2}]
        self.assertEqual(k.cart_total(lines), D("2234.00"))


class SettleTest(unittest.TestCase):
    def test_exact_cash(self):
        r = k.settle(10000, 0, 10000, 0, 0, False)
        self.assertEqual((r["doc"], r["cash"], r["debt"], r["change"]), (k.DOC_SALE, D("10000.00"), D("0.00"), D("0")))

    def test_change_without_client(self):
        r = k.settle(9500, 0, 10000, 0, 0, False)
        self.assertEqual(r["cash"], D("9500.00"))
        self.assertEqual(r["change"], D("500.00"))

    def test_card_overpay_rejected(self):
        with self.assertRaises(k.KassaError):
            k.settle(9500, 0, 0, 10000, 0, False)

    def test_debt_needs_client(self):
        with self.assertRaises(k.KassaError):
            k.settle(10000, 0, 4000, 0, 0, False)
        r = k.settle(10000, 1000, 4000, 2000, 0, True)
        self.assertEqual(r["due"], D("9000.00"))
        self.assertEqual(r["debt"], D("3000.00"))

    def test_debt_payment_only(self):
        r = k.settle(0, 0, 50000, 0, 0, True)
        self.assertEqual(r["doc"], k.DOC_DEBT)
        self.assertEqual(r["debt"], D("-50000.00"))
        with self.assertRaises(k.KassaError):
            k.settle(0, 0, 50000, 0, 0, False)
        with self.assertRaises(k.KassaError):
            k.settle(0, 0, 0, 0, 0, True)

    def test_full_discount_resets_like_1c(self):
        r = k.settle(1000, 1000, 0, 0, 0, True)
        self.assertEqual((r["discount"], r["due"]), (D("0.00"), D("1000.00")))

    def test_discount_bigger_than_total(self):
        with self.assertRaises(k.KassaError):
            k.settle(1000, 2000, 0, 0, 0, True)

    def test_return(self):
        r = k.settle(3000, 0, 3000, 0, 0, False, kind=k.KIND_RETURN)
        self.assertEqual(r["doc"], k.DOC_RETURN)
        with self.assertRaises(k.KassaError):
            k.settle(3000, 0, 1000, 0, 0, False, kind=k.KIND_RETURN)


class RequestTest(unittest.TestCase):
    def body(self, **kw):
        b = {"id": "abc12345", "kind": "sale", "ptype": "Цена2", "lines": [{"code": "15", "qty": "2", "price": "1500"}], "cash": "3000"}
        b.update(kw)
        return b

    def test_clean(self):
        r = k.clean_sale(self.body())
        self.assertEqual(r["lines"][0]["qty"], D("2.000"))
        self.assertEqual(r["cash"], D("3000.00"))
        self.assertEqual(r["ptype"], "Цена2")

    def test_zero_qty_dropped_and_bad_values(self):
        r = k.clean_sale(self.body(lines=[{"code": "1", "qty": 0, "price": 1}]))
        self.assertEqual(r["lines"], [])
        for bad in (dict(id="x"), dict(id="abc 12345"), dict(kind="steal"), dict(ptype="Цена9"), dict(lines=[{"qty": 1}])):
            with self.assertRaises(k.KassaError):
                k.clean_sale(self.body(**bad))
        with self.assertRaises(k.KassaError):
            k.clean_sale([])

    def test_check_prices(self):
        goods = {"15": {"n": "Olma", "u": 0, "p": [1600, 1500, 0, 0]}}
        r = k.clean_sale(self.body())
        self.assertEqual(k.check_prices(r["lines"], goods, "Цена2", 0), [])
        self.assertEqual(k.check_prices(r["lines"], goods, "Цена1", 0), ["Olma"])
        with self.assertRaises(k.KassaError):
            k.check_prices(r["lines"], {}, "Цена1", 0)
        # kassir narxni qo'lda o'zgartirgan (parolsiz) — solishtirilmaydi
        body = self.body()
        body["lines"][0]["m"] = 1
        body["lines"][0]["price"] = "1234"
        m = k.clean_sale(body)
        self.assertTrue(m["lines"][0]["m"])
        self.assertEqual(k.check_prices(m["lines"], goods, "Цена1", 0), [])
        self.assertNotIn("m", r["lines"][0])


if __name__ == "__main__":
    unittest.main()
