# -*- coding: utf-8 -*-
"""
v5.4: kassa (sensor) oynasining sof mantig'i — 1C'siz test qilinadi.

1C'dagi "РеализацияСенсор1" bilan aynan bir xil qoidalar:
  * shtrix kod tahlili (ПолеТекПриИзменении): 29 — tarozi (vazn), 28 — dona tarozi, 21, 25, "0" bilan boshlanuvchi;
  * narx (тов01 / ПолеТекПриИзменении): so'm tovarda ПродажЦенаN, $ tovarda ПродажВалN × Курс;
  * qoldiq (ЗапистьКолво): "ПродажаБезОстатков" o'chiq bo'lsa miqdor qoldiqdan oshmaydi;
  * to'lov (ФормаОплатаСенсор.КнопкаОКНажатие): to'liq to'lanmasa (yoki ortiqcha bo'lsa) mijoz tanlanishi shart;
  * hujjat turi (РасчетНажатие): jami 0 va to'lov bor — "ОплатаДолга", aks holda "Продажа"; qaytarish — "Возврать".
Pul — Decimal (2 xona), miqdor — Decimal (3 xona).
"""
from decimal import Decimal, ROUND_HALF_UP, InvalidOperation

PRICE_TYPES = ("Цена1", "Цена2", "Цена3", "Цена4")
KIND_SALE = "sale"
KIND_RETURN = "return"
DOC_SALE = "Продажа"
DOC_RETURN = "Возврать"
DOC_DEBT = "ОплатаДолга"

ZERO = Decimal("0")
CENT = Decimal("0.01")
MILLI = Decimal("0.001")


class KassaError(Exception):
    """Kassachiga ko'rsatiladigan xato (o'zbekcha matn)."""


def dec(v, places=CENT):
    """Har qanday son/matndan Decimal (yaxlitlangan). Noto'g'ri qiymat — 0."""
    if v is None or v == "":
        return ZERO.quantize(places)
    try:
        d = v if isinstance(v, Decimal) else Decimal(str(v).replace(",", ".").replace("\u00a0", "").replace(" ", ""))
    except (InvalidOperation, ValueError):
        return ZERO.quantize(places)
    if not d.is_finite():
        return ZERO.quantize(places)
    return d.quantize(places, rounding=ROUND_HALF_UP)


def money(v):
    return dec(v, CENT)


def qty(v):
    return dec(v, MILLI)


def jnum(d):
    """JSON uchun: butun bo'lsa int, aks holda float (2-3 xona)."""
    d = d if isinstance(d, Decimal) else dec(d)
    if d == d.to_integral_value():
        return int(d)
    return float(d)


# ---------------- shtrix kod ----------------

def digits_number(raw):
    """1C ПолучитьНачалоБуква + Формат(ЧГ=0): faqat raqamlar, son sifatida (oldidagi nollar tushadi). Raqam yo'q — ""."""
    s = "".join(ch for ch in str(raw or "") if ch.isdigit())
    if not s:
        return ""
    return str(int(s))


def _num(s):
    s = "".join(ch for ch in s if ch.isdigit())
    return int(s) if s else 0


def parse_scan(raw, rongta=False):
    """
    Skaner yoki qo'lda kiritilgan kod -> (Штрих kodi, miqdor, vaznli).
    vaznli=True bo'lsa savatda alohida qator bo'ladi (1C: ВесТов=1 — mavjud qatorga qo'shilmaydi).
    """
    raw = str(raw or "").strip()
    t = digits_number(raw)
    if not t:
        return "", ZERO, False
    if t.startswith("29"):
        return t[:7], qty(Decimal(_num(t[7:12])) / Decimal(1000)), True
    if t.startswith("28"):
        n = _num(t[7:9]) if rongta else _num(t[7:12])
        return t[:7], qty(n), False
    if t.startswith("21"):
        return t, qty(1), False
    if t.startswith("25"):
        return t[:12], qty(1), False
    if raw.startswith("0"):
        return raw, qty(1), False
    return t, qty(1), False


# ---------------- narx ----------------

def price_index(ptype):
    """"Цена3" -> 2. Noma'lum — 0 (Цена1, 1C'dagi kabi)."""
    try:
        return PRICE_TYPES.index(ptype)
    except ValueError:
        return 0


def price_of(goods, ptype, rate):
    """goods: {"u": 1 ($ tovar) | 0, "p": [4 so'm narx], "v": [4 $ narx]}. Natija — so'm, 2 xona."""
    i = price_index(ptype)
    if goods.get("u"):
        v = (goods.get("v") or [0, 0, 0, 0])
        return money(dec(v[i] if i < len(v) else 0, Decimal("0.0001")) * dec(rate, Decimal("0.0001")))
    p = goods.get("p") or [0, 0, 0, 0]
    return money(p[i] if i < len(p) else 0)


# ---------------- savat ----------------

def cap_qty(want, stock, allow_negative):
    """1C ЗапистьКолво: qoldiqsiz sotish o'chiq bo'lsa — qoldiqdan ko'p emas. @return (miqdor, kesildimi)."""
    want, stock = qty(want), qty(stock)
    if allow_negative or want <= stock:
        return want, False
    return (stock if stock > ZERO else ZERO), True


def line_sum(price, q):
    return money(money(price) * qty(q))


def cart_total(lines):
    return sum((line_sum(l["price"], l["qty"]) for l in lines), ZERO).quantize(CENT)


# ---------------- to'lov ----------------

def settle(total, discount, cash, card, click, has_client, kind=KIND_SALE):
    """
    To'lovni tekshiradi va 1C hujjatiga yoziladigan summalarni qaytaradi.
    Mijoz tanlanmagan va naqd ortiqcha berilgan bo'lsa — qaytim (naqdga faqat qolgan qismi yoziladi).
    Mijozsiz to'liq to'lanmasa — xato (1C: "Avval mijozni tanlang").
    @return dict(total, discount, due, cash, card, click, change, debt, doc)
    """
    total, discount = money(total), money(discount)
    cash, card, click = money(cash), money(card), money(click)
    for v in (total, discount, cash, card, click):
        if v < ZERO:
            raise KassaError("Summa manfiy bo'lishi mumkin emas")
    if discount > total:
        raise KassaError("Chegirma jami summadan katta")
    due = total - discount
    if due == ZERO:
        discount = ZERO
        due = total
    paid = cash + card + click
    change = ZERO
    if kind == KIND_RETURN:
        if total <= ZERO:
            raise KassaError("Qaytariladigan tovar yo'q")
        rest = due - paid
        if rest != ZERO and not has_client:
            raise KassaError("Avval mijozni tanlang (pul to'liq qaytarilmayapti)")
        return dict(total=total, discount=discount, due=due, cash=cash, card=card, click=click,
                    change=ZERO, debt=rest, doc=DOC_RETURN)
    if total == ZERO and paid == ZERO:
        raise KassaError("Savat bo'sh")
    if total == ZERO and not has_client:
        raise KassaError("Qarz to'lovi uchun mijozni tanlang")
    if paid > due and not has_client:
        over = paid - due
        if cash < over:
            raise KassaError("Plastik/Click summasi to'lanadigandan ko'p")
        change = over
        cash -= over
        paid = due
    rest = due - paid
    if rest != ZERO and not has_client:
        raise KassaError("Avval mijozni tanlang (to'liq to'lanmadi — qarzga yoziladi)")
    doc = DOC_DEBT if total == ZERO else DOC_SALE
    return dict(total=total, discount=discount, due=due, cash=cash, card=card, click=click,
                change=change, debt=rest, doc=doc)


# ---------------- so'rov ----------------

def clean_sale(body):
    """Brauzerdan kelgan sotuv so'rovini tekshiradi va tozalaydi. Xato — KassaError."""
    if not isinstance(body, dict):
        raise KassaError("So'rov noto'g'ri")
    rid = str(body.get("id") or "").strip()
    if not (8 <= len(rid) <= 64) or not all(ch.isalnum() or ch in "-_" for ch in rid):
        raise KassaError("So'rov raqami noto'g'ri")
    kind = body.get("kind") or KIND_SALE
    if kind not in (KIND_SALE, KIND_RETURN):
        raise KassaError("Amal turi noto'g'ri")
    ptype = body.get("ptype") or PRICE_TYPES[0]
    if ptype not in PRICE_TYPES:
        raise KassaError("Narx turi noto'g'ri")
    lines = []
    for l in body.get("lines") or []:
        if not isinstance(l, dict):
            raise KassaError("Qator noto'g'ri")
        code = str(l.get("code") or "").strip()
        q = qty(l.get("qty"))
        if not code:
            raise KassaError("Tovar kodi yo'q")
        if q <= ZERO:
            continue
        lines.append({"code": code, "qty": q, "price": money(l.get("price")), "name": str(l.get("name") or "")[:150]})
    if len(lines) > 500:
        raise KassaError("Qatorlar juda ko'p")
    return {
        "id": rid, "kind": kind, "ptype": ptype, "lines": lines,
        "client": str(body.get("client") or "").strip(),
        "discount": money(body.get("discount")), "cash": money(body.get("cash")),
        "card": money(body.get("card")), "click": money(body.get("click")),
        "note": str(body.get("note") or "").strip()[:300],
        "token": str(body.get("token") or ""),
    }


def check_prices(lines, goods_by_code, ptype, rate):
    """Narxlar hozirgisi bilan bir xilmi (kassada eski narx qolgan bo'lmasin). @return o'zgarganlar nomlari."""
    changed = []
    for l in lines:
        g = goods_by_code.get(l["code"])
        if g is None:
            raise KassaError("Tovar topilmadi: %s" % (l.get("name") or l["code"]))
        if price_of(g, ptype, rate) != l["price"]:
            changed.append(g.get("n") or l["code"])
    return changed

