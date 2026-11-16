# -*- coding: utf-8 -*-
"""
v5.4: 1C "KO'PRIK" — kassa sotuvini 1C'ning O'Z kodi bilan yozish.

Nega kerak: bu bazada (8.3.10, kodi shifrlangan) ПродажаРозница hujjati
  * COM (V83.COMConnector) orqali yaratilmaydi — "Ошибка инициализации модуля" (umumiy modul tashqi ulanishda yo'q);
  * yashirin 1C (V83.Application) esa 1 soniyada yopiladi — "Сбой при удаленном вызове процедуры".
Oddiy usulda ochilgan 1C esa ishlaydi. Shuning uchun dastur 1C'ni ODDIY rejimda ishga tushiradi va unga kichik tashqi
ishlov (kassa_bridge.epf) beradi: /Execute kassa_bridge.epf /C <papka>. Ishlov papkadagi har bir *.job faylni
xuddi 1C sensor oynasidagi "Расчет"/"Возврат" tugmasi kabi ПродажаРозница qilib o'tkazadi (МахсулотКолдигиниАниклаш,
ПоучитьДолг, ОхиргиУртачаНархиниАниклаш_Махсулот — bazaning o'z funksiyalari), natijani *.res ga yozadi va 1C'ni yopadi.

Xavfsizlik: o'tkazishdan oldin *.wip belgisi yoziladi. 1C o'chib qolsa (*.wip bor, *.res yo'q) — hujjat yozildimi aniq emas,
shuning uchun avtomatik qayta yozilmaydi (ikki marta yozilmasin) — admin 1C'da tekshirib qaror qiladi.

.epf fayli shu yerda (1C/Konfigurator'siz) yasaladi: 1C konteyner formati (8.2/8.3 "216" format).
"""
import logging
import os
import re
import subprocess
import time
import uuid
import zlib

log = logging.getLogger("bridge")

EPF_NAME = "kassa_bridge.epf"
JOBS_DIR = "kassa_jobs"
BRIDGE_VERSION = "4"

# --------------------------------------------------------------------------- 1C kodi (tashqi ishlov moduli)

BSL = r"""// IMSOFT kassa ko'prigi v__VER__: 1C ochiq turadi, papkadagi *.job -> ПродажаРозница (1C sensor oynasi bilan bir xil), natija *.res
Перем ПечатьСсылка, ПечатьВозврат;

Функция Ч(Знач С)
	С = СокрЛП(С);
	Если С = "" Тогда
		Возврат 0;
	КонецЕсли;
	Возврат Число(С);
КонецФункции

Функция НайтиСсылку(Менеджер, Знач Код)
	Код = СокрЛП(Код);
	Если Код = "" Тогда
		Возврат Неопределено;
	КонецЕсли;
	Р = Неопределено;
	Попытка
		Р = Менеджер.НайтиПоКоду(Число(Код));
	Исключение
		Р = Неопределено;
	КонецПопытки;
	Если Р = Неопределено Или Р.Пустая() Тогда
		Попытка
			Р = Менеджер.НайтиПоКоду(Код);
		Исключение
			Р = Неопределено;
		КонецПопытки;
	КонецЕсли;
	Если Р = Неопределено Или Р.Пустая() Тогда
		Возврат Неопределено;
	КонецЕсли;
	Возврат Р;
КонецФункции

Процедура ЗаписатьТекст(Путь, Текст)
	Т = Новый ТекстовыйДокумент;
	Т.УстановитьТекст(Текст);
	Т.Записать(Путь, КодировкаТекста.UTF8);
КонецПроцедуры

Функция Провести(Путь)
	Т = Новый ТекстовыйДокумент;
	Т.Прочитать(Путь, КодировкаТекста.UTF8);
	Ш = СтрРазделить(Т.ПолучитьСтроку(1), Символы.Таб, Истина);
	Если Ш.Количество() < 13 Или Ш[0] <> "H" Тогда
		ВызватьИсключение "Topshiriq fayli noto'g'ri";
	КонецЕсли;
	Вид    = Ш[2];
	Если Вид = "text" Тогда
		// hisobot: chap|o'ng ustunli qatorlar -> kompyuterdagi chek printeri (Константы.ЧекПритер1)
		ТД = Новый ТабличныйДокумент;
		Шр = Новый Шрифт("Arial", 9, Истина);
		Н = 0;
		Для Сч = 2 По Т.КоличествоСтрок() Цикл
			С = СтрРазделить(Т.ПолучитьСтроку(Сч), Символы.Таб, Истина);
			Если С.Количество() < 2 Или С[0] <> "L" Тогда
				Продолжить;
			КонецЕсли;
			Н = Н + 1;
			Ч1 = СтрРазделить(С[1], "|", Истина);
			О1 = ТД.Область(Н, 1);
			О1.Текст = Ч1[0];
			О1.Шрифт = Шр;
			Если Ч1.Количество() > 1 Тогда
				О2 = ТД.Область(Н, 2);
				О2.Текст = Ч1[1];
				О2.Шрифт = Шр;
				О2.ГоризонтальноеПоложение = ГоризонтальноеПоложение.Право;
			Иначе
				О1.Шрифт = Новый Шрифт("Arial", 12, Истина);
			КонецЕсли;
		КонецЦикла;
		ТД.Область(, 1, , 1).ШиринаКолонки = 24;
		ТД.Область(, 2, , 2).ШиринаКолонки = 16;
		ТД.ПолеСлева = 0;
		ТД.ПолеСправа = 0;
		Попытка
			Принтер = СокрЛП(Константы.ЧекПритер1.Получить());
			Если Принтер <> "" Тогда
				ТД.ИмяПринтера = Принтер;
			КонецЕсли;
		Исключение
		КонецПопытки;
		Выполнить("ТД.Напечатать(Истина)");
		Возврат "TEXT" + Символы.Таб + Строка(Н);
	КонецЕсли;
	Если Вид = "print" Тогда
		// chekni qayta chiqarish: 1C'ning o'z chek bosmasi (ПечатьНажатие_1 / ПечатьНажатиеВоз_1)
		Д = Ш[3];
		Найден = Документы.ПродажаРозница.НайтиПоНомеру(Ш[12], Дата(Прав(Д, 4) + Сред(Д, 4, 2) + Лев(Д, 2)));
		Если Найден.Пустая() Тогда
			ВызватьИсключение "1C'da hujjat topilmadi: " + Ш[12];
		КонецЕсли;
		ПечатьСсылка  = Найден;
		ПечатьВозврат = (Найден.ВидДокумента = Перечисления.ВидДокумента.Возврать);
		Возврат "PRINT" + Символы.Таб + СокрЛП(Найден.Номер);
	КонецЕсли;
	Склад  = Константы.Склад1.Получить();
	Док = Документы.ПродажаРозница.СоздатьДокумент();
	Если Вид = "probe" Тогда
		// sinov: hujjat moduli va bazaning funksiyalari ishlaydimi (hech narsa yozilmaydi)
		Список = ПоучитьДолг(ТекущаяДата(), Справочники.Контрагенты.Савдо, Справочники.Сотрудники.Дукон);
		Возврат "PROBE" + Символы.Таб + Формат(ТекущаяДата(), "ДФ='dd.MM.yyyy HH:mm'") + " / " + Строка(Склад);
	КонецЕсли;
	Док.Дата      = ТекущаяДата();
	Док.Курс      = Константы.Курс.Получить();
	Док.Склад     = Склад;
	Док.ВидВал    = Перечисления.ВидВал.Сум;
	Док.ВидПродаж = Перечисления.ТипЦена[Ш[3]];
	Док.ВсегоСум  = Ч(Ш[6]);
	Док.СкидкаСум = Ч(Ш[7]);
	Док.Нал       = Ч(Ш[8]);
	Док.Пер       = Ч(Ш[9]);
	Док.Клик      = Ч(Ш[10]);
	Сот = НайтиСсылку(Справочники.Сотрудники, Ш[4]);
	Если Сот <> Неопределено Тогда
		Док.Сотрудник = Сот;
		Если Сот <> Справочники.Сотрудники.Дукон Тогда
			Док.ДоляСотОп = Сот.ДоляСотОп;
			Док.ДоляСотПр = Сот.ДоляСотПр;
		КонецЕсли;
	КонецЕсли;
	Кл = НайтиСсылку(Справочники.Контрагенты, Ш[5]);
	Если СокрЛП(Ш[5]) <> "" И Кл = Неопределено Тогда
		ВызватьИсключение "Mijoz topilmadi: " + Ш[5];
	КонецЕсли;
	КлНарх = ?(Кл = Неопределено, Справочники.Контрагенты.ПустаяСсылка(), Кл);
	Если Кл <> Неопределено И Кл <> Справочники.Контрагенты.Савдо Тогда
		Список = ПоучитьДолг(ТекущаяДата(), Кл, Справочники.Сотрудники.Дукон);
		Док.Контрагент = Кл;
		Док.ДтКтСум    = Список.ДтКтСум;
		Док.ДтКтВал    = Список.ДтКтВал;
		Док.ЛимитСум   = Кл.ЛимитСум;
		Док.ЛимитВал   = Кл.ЛимитВал;
	Иначе
		Кл = Справочники.Контрагенты.Савдо;
		Док.Контрагент = Кл;
	КонецЕсли;
	Док.ВидДокумента = Перечисления.ВидДокумента[Ш[11]];
	Док.Комментарий  = Ш[12];
	Для Н = 2 По Т.КоличествоСтрок() Цикл
		С = СтрРазделить(Т.ПолучитьСтроку(Н), Символы.Таб, Истина);
		Если С.Количество() < 5 Или С[0] <> "L" Тогда
			Продолжить;
		КонецЕсли;
		Тов = НайтиСсылку(Справочники.Товары, С[1]);
		Если Тов = Неопределено Тогда
			ВызватьИсключение "Tovar topilmadi: " + С[1];
		КонецЕсли;
		Стр = Док.Таблица.Добавить();
		Стр.Товары = Тов;
		Стр.ЕдИзм  = Тов.ЕдИзм;
		Стр.Шт     = ?(Тов.Обьём > 0, Тов.Обьём, 1);
		Стр.ВидВал = Док.ВидВал;
		Стр.Цены   = Док.ВидПродаж;
		Стр.Кг     = Тов.Кг;
		Стр.ДоляП  = Тов.ДоляП;
		Если Вид = "return" Тогда
			СписокН = ОхиргиУртачаНархиниАниклаш_Махсулот(Тов, КлНарх, ТекущаяДата());
			Стр.УртачаНархСум = СписокН.НархиСум;
			Стр.УртачаНархВал = СписокН.НархиВал;
		Иначе
			Список = МахсулотКолдигиниАниклаш(ТекущаяДата(), Склад, Тов);
			Стр.Ост           = Список.Ост;
			Стр.ОстСуммаСум   = Список.ОстСуммаСум;
			Стр.ОстСуммаВал   = Список.ОстСуммаВал;
			Стр.УртачаНархСум = Список.УртачаНархСум;
			Стр.УртачаНархВал = Список.УртачаНархВал;
		КонецЕсли;
		Стр.Кол      = Ч(С[2]);
		Стр.Блок     = Стр.Кол / Стр.Шт;
		Стр.ЦенаСум  = Ч(С[3]);
		Стр.СуммаСум = Ч(С[4]);
	КонецЦикла;
	Док.Записать(РежимЗаписиДокумента.Проведение);
	Если Ш.Количество() > 13 И Ш[13] = "1" Тогда
		ПечатьСсылка  = Док.Ссылка;
		ПечатьВозврат = (Вид = "return");
	КонецЕсли;
	Возврат СокрЛП(Док.Номер) + Символы.Таб + Формат(Док.Дата, "ДФ='dd.MM.yyyy HH:mm'");
КонецФункции

Функция ФайлЕсть(Путь)
	Ф = Новый Файл(Путь);
	Возврат Ф.Существует();
КонецФункции

Процедура ИшлаТопшириклар(Папка)
	Файлы = НайтиФайлы(Папка, "*.job");
	Для Каждого Ф Из Файлы Цикл
		Асос = Папка + "\" + Ф.ИмяБезРасширения;
		// ikki marta yozilmasin: natijasi bor yoki oldin yozish boshlangan (1C o'chib qolgan) bo'lsa — tegilmaydi
		Если ФайлЕсть(Асос + ".res") Тогда
			УдалитьФайлы(Ф.ПолноеИмя);
			Продолжить;
		КонецЕсли;
		Если ФайлЕсть(Асос + ".wip") Тогда
			Продолжить;
		КонецЕсли;
		ЗаписатьТекст(Асос + ".wip", "1");
		ПечатьСсылка = Неопределено;
		Попытка
			Ответ = "OK" + Символы.Таб + Провести(Ф.ПолноеИмя);
		Исключение
			Ответ = "ERR" + Символы.Таб + СтрЗаменить(СтрЗаменить(ОписаниеОшибки(), Символы.ПС, " "), Символы.ВК, " ");
		КонецПопытки;
		ЗаписатьТекст(Асос + ".res", Ответ);
		УдалитьФайлы(Ф.ПолноеИмя);
		УдалитьФайлы(Асос + ".wip");
		Если ПечатьСсылка <> Неопределено Тогда
			// chek — 1C sensor oynasidagi bilan AYNAN bir xil (bazaning o'z bosma protsedurasi, chek printeriga)
			Попытка
				Сс = ПечатьСсылка;
				Если ПечатьВозврат Тогда
					Выполнить("ПечатьНажатиеВоз_1(Сс, 1)");
				Иначе
					Выполнить("ПечатьНажатие_1(Сс, 1)");
				КонецЕсли;
			Исключение
				ЗаписатьТекст(Асос + ".perr", ОписаниеОшибки());
			КонецПопытки;
			ПечатьСсылка = Неопределено;
		КонецЕсли;
	КонецЦикла;
КонецПроцедуры

Процедура Пауза(Оболочка)
	// ~1 soniya kutish (1C 8.3.10 da "uxlash" yo'q): yashirin ping
	Если Оболочка <> Неопределено Тогда
		Попытка
			Оболочка.Run("cmd /c ping -n 2 127.0.0.1 >nul", 0, Истина);
			Возврат;
		Исключение
		КонецПопытки;
	КонецЕсли;
	ЗапуститьПриложение("ping -n 2 127.0.0.1", , Истина);
КонецПроцедуры

Функция ДастурЖив(Папка)
	// kompyuter dasturi (Python) 5 daqiqa belgi bermasa — 1C o'zi yopiladi
	Ф = Новый Файл(Папка + "\py.alive");
	Если Не Ф.Существует() Тогда
		Возврат Ложь;
	КонецЕсли;
	Возврат ТекущаяДата() - Ф.ПолучитьВремяИзменения() < 300;
КонецФункции

Процедура Ишла()
	Папка = СокрЛП(ПараметрЗапуска);
	Если Папка = "" Тогда
		Возврат;
	КонецЕсли;
	Оболочка = Неопределено;
	Попытка
		Оболочка = Новый COMОбъект("WScript.Shell");
	Исключение
		Оболочка = Неопределено;
	КонецПопытки;
	Пульс = Дата(1, 1, 1);
	Пока Истина Цикл
		Если ФайлЕсть(Папка + "\stop.flag") Или Не ДастурЖив(Папка) Тогда
			Прервать;
		КонецЕсли;
		Если ТекущаяДата() - Пульс >= 5 Тогда
			ЗаписатьТекст(Папка + "\1c.alive", "__VER__");
			Пульс = ТекущаяДата();
		КонецЕсли;
		Попытка
			ИшлаТопшириклар(Папка);
		Исключение
			ЗаписатьТекст(Папка + "\1c.error", ОписаниеОшибки());
		КонецПопытки;
		Пауза(Оболочка);
	КонецЦикла;
	Попытка
		УдалитьФайлы(Папка + "\1c.alive");
	Исключение
	КонецПопытки;
КонецПроцедуры

// 1C bitta marta ochiladi va OCHIQ turadi: har ~1 soniyada papkani tekshiradi
Ишла();
ЗавершитьРаботуСистемы(Ложь);
"""


def bsl_text():
    return BSL.replace("__VER__", BRIDGE_VERSION)


# --------------------------------------------------------------------------- 1C konteyner (.epf) yasash

PAGE = 512
NO_NEXT = 0x7fffffff


def _block(data, page=PAGE, exact=False):
    size = len(data) if exact else max(len(data), page)
    return b"\r\n%08x %08x %08x \r\n" % (len(data), size, NO_NEXT) + data + b"\0" * (size - len(data))


def _deflate(data):
    c = zlib.compressobj(9, zlib.DEFLATED, -15)
    return c.compress(data) + c.flush()


def container(entries, storage=None, packed=True):
    """entries: [(nom, bayt)] — 1C konteyneri (har element alohida blokda).
    packed=True: tashqi konteyner (ma'lumot deflate, mundarija 512 baytli blok);
    packed=False: modul ichki konteyneri (info/text siqilmagan, mundarija aniq o'lchamda) — 1C shunday yozadi."""
    stamp = b"\x70\x5f\xde\x35\x64\x42\x02\x00"         # 1C sana (o'zgarmas — natija har safar bir xil)
    head = b"\xff\xff\xff\x7f" + PAGE.to_bytes(4, "little") + \
        (len(entries) if storage is None else storage).to_bytes(4, "little") + b"\0\0\0\0"
    toc_len = 12 * len(entries)
    pos = len(head) + 31 + (max(toc_len, PAGE) if packed else toc_len)
    toc = b""
    body = b""
    for name, data in entries:
        hdr = stamp + stamp + b"\0\0\0\0" + name.encode("utf-16-le") + b"\0\0\0\0"
        hb = _block(hdr, exact=True)
        db = _block(_deflate(data) if packed else data)
        toc += pos.to_bytes(4, "little") + (pos + len(hb)).to_bytes(4, "little") + NO_NEXT.to_bytes(4, "little")
        body += hb + db
        pos += len(hb) + len(db)
    return head + _block(toc, exact=not packed) + body


def read_container(b, packed=True):
    """Tekshiruv uchun o'quvchi: [(nom, ochilgan bayt)]."""
    hdr = re.compile(rb"\r\n([0-9a-f]{8}) ([0-9a-f]{8}) ([0-9a-f]{8}) \r\n")

    def doc(pos):
        out = b""
        m = hdr.match(b, pos)
        total = int(m.group(1), 16)
        while m:
            size, nxt = int(m.group(2), 16), int(m.group(3), 16)
            out += b[m.end():m.end() + size]
            if nxt == NO_NEXT:
                break
            m = hdr.match(b, nxt)
        return out[:total]
    toc = doc(16)
    res = []
    for i in range(0, len(toc) - 11, 12):
        h = int.from_bytes(toc[i:i + 4], "little")
        d = int.from_bytes(toc[i + 4:i + 8], "little")
        name = doc(h)[20:].decode("utf-16-le").rstrip("\0")
        res.append((name, zlib.decompress(doc(d), -15) if packed else doc(d)))
    return res


def build_epf(module_text, name="ImsoftKassaBridge", synonym="IMSOFT kassa ko'prigi", seed="imsoft-kassa-bridge"):
    """Faqat obyekt modulidan iborat tashqi ishlov (.epf). UUID'lar [seed] dan — fayl har safar bir xil chiqadi."""
    def u(k):
        return str(uuid.uuid5(uuid.NAMESPACE_URL, seed + ":" + k))
    meta_id, obj_id, mod_id = u("meta"), u("obj"), u("module")
    bom = b"\xef\xbb\xbf"
    meta = (
        "{1,\r\n{%s},1,\r\n{c3831ec8-d8d5-4f93-8a22-f9bfae07327f,\r\n{1,\r\n{4,%s,%s,\r\n{0,\r\n{0,\r\n"
        "{0,0,%s},\"%s\",\r\n{1,\"ru\",\"%s\"},\"\"}\r\n},00000000-0000-0000-0000-000000000000,\"\","
        "00000000-0000-0000-0000-000000000000},4,\r\n{2bcef0d1-0981-11d6-b9b8-0050bae0a95d,0},\r\n"
        "{3daea016-69b7-4ed4-9453-127911372fe6,0},\r\n{d5b0e5ed-256d-401c-9c36-f630cafd8a62,0},\r\n"
        "{ec6bb5e5-b7a8-4d75-bec9-658107a699cf,0}\r\n}\r\n}\r\n}"
    ) % (meta_id, u("t1"), u("t2"), mod_id, name, synonym.replace('"', '""'))
    module_dir = container([("info", bom + b"{3,1,0,\"\",0}"),
                            ("text", bom + module_text.replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8"))],
                           storage=1, packed=False)
    names = [meta_id, "copyinfo", mod_id + ".0", "root", "version"]
    versions = "{1,%d,\"\",%s" % (len(names) + 2, u("v-all"))
    for n in ["versions"] + names:
        versions += ",\"%s\",%s" % (n, u("v-" + n))
    versions += "}"
    entries = [
        (meta_id, bom + meta.encode("utf-8")),
        ("copyinfo", bom + b"{4,\r\n{0},\r\n{0},\r\n{0},\r\n{0,0},\r\n{0}\r\n}"),
        (mod_id + ".0", module_dir),
        ("root", bom + ("{2,%s,}" % meta_id).encode("ascii")),
        ("version", bom + b"{\r\n{216,0}\r\n}"),
        ("versions", bom + versions.encode("ascii")),
    ]
    return container(entries)


# --------------------------------------------------------------------------- topshiriqlar

def num(v):
    """Decimal/float/int -> 1C Число() tushunadigan matn (nuqta bilan, eksponentasiz)."""
    from decimal import Decimal
    d = v if isinstance(v, Decimal) else Decimal(str(v))
    t = format(d, "f")
    if "." in t:
        t = t.rstrip("0").rstrip(".")
    return t if t not in ("", "-0") else "0"


def clean(s):
    return re.sub(r"[\t\r\n]+", " ", str(s or "")).strip()


def probe_job(rid="probe"):
    """Sinov topshirig'i: 1C ochiladi, hujjat yaratiladi (yozilmaydi), bazaning funksiyasi chaqiriladi."""
    return {"id": rid, "kind": "probe", "ptype": "Цена1", "emp": "", "client": "", "total": 0, "discount": 0,
            "cash": 0, "card": 0, "click": 0, "doc": "Продажа", "note": "", "lines": []}


def print_job(rid, no, date):
    """Chekni 1C'ning o'zi qayta chiqaradi: [no] — 1C hujjat raqami, [date] — "dd.mm.yyyy"."""
    return {"id": rid, "kind": "print", "ptype": clean(date)[:10], "emp": "", "client": "", "total": 0, "discount": 0,
            "cash": 0, "card": 0, "click": 0, "doc": "", "note": clean(no), "lines": [], "print": False}


def text_job(rid, lines):
    """Hisobotni 1C chek printeriga: lines[0] — sarlavha, keyingilari "chap|o'ng"."""
    return {"id": rid, "kind": "text", "ptype": "", "emp": "", "client": "", "total": 0, "discount": 0, "cash": 0,
            "card": 0, "click": 0, "doc": "", "note": "", "print": False,
            "lines": [{"code": clean(x)[:80], "qty": 0, "price": 0, "sum": 0} for x in lines]}


def job_text(job):
    """job: dict(id, kind, ptype, emp, client, total, discount, cash, card, click, doc, note, lines=[code, qty, price, sum])."""
    h = ["H", job["id"], job["kind"], job["ptype"], clean(job.get("emp")), clean(job.get("client")),
         num(job["total"]), num(job["discount"]), num(job["cash"]), num(job["card"]), num(job["click"]),
         job["doc"], clean(job.get("note"))[:300], "1" if job.get("print") else "0"]
    out = ["\t".join(h)]
    for l in job["lines"]:
        out.append("\t".join(["L", clean(l["code"]), num(l["qty"]), num(l["price"]), num(l["sum"])]))
    return "\r\n".join(out) + "\r\n"


def parse_result(text):
    """*.res -> ("OK", raqam, sana) | ("ERR", xabar)."""
    text = (text or "").lstrip("\ufeff").strip()
    parts = text.split("\t")
    if parts[0] == "OK" and len(parts) >= 3:
        return ("OK", parts[1].strip(), parts[2].strip())
    if parts[0] == "ERR":
        return ("ERR", "\t".join(parts[1:]).strip()[:400] or "1C xatosi")
    return ("ERR", "1C javobi tushunarsiz: %s" % text[:200])


def parse_conn(s):
    """'File="D:\\Base";Usr="a";Pwd="b";' -> {'file': .., 'usr': .., 'pwd': ..}"""
    out = {}
    for m in re.finditer(r'(\w+)\s*=\s*(?:"((?:[^"]|"")*)"|([^;]*))\s*;?', s or ""):
        out[m.group(1).lower()] = (m.group(2) if m.group(2) is not None else m.group(3) or "").replace('""', '"').strip()
    return out


def command(exe, conn, epf, jobs):
    c = parse_conn(conn)
    args = [exe, "ENTERPRISE"]
    if c.get("srvr"):
        args += ["/S", "%s\\%s" % (c["srvr"], c.get("ref", ""))]
    else:
        args += ["/F", c.get("file", "")]
    if c.get("usr"):
        args += ["/N", c["usr"]]
    if c.get("pwd"):
        args += ["/P", c["pwd"]]
    args += ["/Execute", epf, "/C", jobs, "/DisableStartupMessages", "/DisableStartupDialogs"]
    return args


# --------------------------------------------------------------------------- 1cv8.exe va sozlama

def version_key(path):
    m = re.search(r"(\d+)\.(\d+)\.(\d+)\.(\d+)", path or "")
    return tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)


def pick_exe(registry, found, exists=os.path.isfile):
    """Avval reestrdagi (ishlayotgan COMConnector bilan bir xil versiya), keyin topilganlarning eng yangisi."""
    for p in registry:
        if p and exists(p):
            return p
    for p in sorted(found, key=version_key, reverse=True):
        if exists(p):
            return p
    return ""


def find_exe():
    """Kompyuterdagi 1cv8.exe (masalan C:\\Program Files (x86)\\1cv8\\8.3.10.2505\\bin\\1cv8.exe)."""
    if os.name != "nt":
        return ""
    import glob
    reg = []
    try:
        import onec_export as oe
        srv = {}
        for progid, kind in (("V83.COMConnector", "InprocServer32"), ("V83.Application", "LocalServer32")):
            try:
                srv[progid] = oe._clsid_server(progid, kind)
            except OSError:
                srv[progid] = ""
        reg = oe.app_server_candidates(srv["V83.COMConnector"], srv["V83.Application"])
    except Exception:  # noqa: BLE001 - reestr o'qilmasa standart papkalar qidiriladi
        reg = []
    found = []
    for root in (os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)"),
                 os.environ.get("ProgramFiles", r"C:\Program Files")):
        found += glob.glob(os.path.join(root, "1cv8", "8.*", "bin", "1cv8.exe"))
    return pick_exe(reg, found)


UNSAFE_LINE = "DisableUnsafeActionProtection=.*"


def conf_with_unsafe(text):
    """conf.cfg matni -> ogohlantirish o'chirilgan matn; allaqachon bo'lsa None."""
    text = text or ""
    if re.search(r"(?im)^\s*DisableUnsafeActionProtection\s*=", text):
        return None
    if text and not text.endswith("\n"):
        text += "\r\n"
    return text + UNSAFE_LINE + "\r\n"


def conf_paths(exe):
    """1C sozlama fayllari: <1cv8>\\conf\\conf.cfg va <versiya>\\bin\\conf\\conf.cfg."""
    import ntpath
    b = ntpath.dirname(exe)
    return [ntpath.join(ntpath.dirname(ntpath.dirname(b)), "conf", "conf.cfg"), ntpath.join(b, "conf", "conf.cfg")]


def allow_external(exe):
    """1C 8.3.9+ tashqi ishlov ochilganda "Предупреждение безопасности" so'raydi (tugma bosish kerak) — kassa
    uchun bu ogohlantirish o'chiriladi (conf.cfg, administrator huquqi kerak). @return (o'zgartirildi, xatolar)."""
    changed, errs = False, []
    for p in conf_paths(exe):
        try:
            try:
                with open(p, "r", encoding="utf-8-sig") as f:
                    old = f.read()
            except FileNotFoundError:
                if not os.path.isdir(os.path.dirname(p)):
                    continue
                old = ""
            new = conf_with_unsafe(old)
            if new is None:
                continue
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(new)
            changed = True
        except OSError as e:
            errs.append("%s: %s" % (p, e))
    return changed, errs


class Bridge:
    """1C'ni oddiy rejimda BIR MARTA ochadi va ochiq qoldiradi (pc 12): 1C har ~1 soniyada papkani tekshiradi,
    sotuv 1-2 soniyada yoziladi. Dastur (Python) ishlab turgani "py.alive" belgisi bilan bildiriladi — dastur
    to'xtasa 5 daqiqadan keyin 1C o'zi yopiladi; dastur qayta ishga tushsa ochiq 1C'dan foydalanadi."""

    ALIVE_SEC = 60         # 1C belgisi (1c.alive) shundan eski bo'lsa — 1C ishlamayapti (chek bosilayotganda ham)
    WIP_STUCK = 90         # .wip shundan eski va .res yo'q — 1C yozish paytida o'chgan (natija noma'lum)
    START_SEC = 180        # 1C ochilishini kutish

    def __init__(self, conn, here, exe, popen=subprocess.Popen, clock=time.time, sleep=time.sleep, jobs_dir=JOBS_DIR):
        self.conn = conn
        self.here = here
        self.exe = exe
        self.popen = popen
        self.clock = clock
        self.sleep = sleep
        self.jobs = os.path.join(here, jobs_dir)
        self.epf = os.path.join(here, EPF_NAME)
        self.conf_done = False
        self.proc = None

    def _p(self, name):
        return os.path.join(self.jobs, name)

    def ensure_epf(self):
        raw = build_epf(bsl_text())
        try:
            with open(self.epf, "rb") as f:
                if f.read() == raw:
                    return self.epf
        except OSError:
            pass
        tmp = self.epf + ".tmp"
        with open(tmp, "wb") as f:
            f.write(raw)
        os.replace(tmp, self.epf)
        return self.epf

    def heartbeat(self):
        """Dastur ishlayapti — 1C ochiq turaversin."""
        os.makedirs(self.jobs, exist_ok=True)
        p = self._p("py.alive")
        try:
            with open(p, "a"):
                pass
            os.utime(p, None)
        except OSError:
            pass

    def _age(self, name):
        try:
            return time.time() - os.path.getmtime(self._p(name))
        except OSError:
            return None

    def alive_version(self):
        """Ochiq 1C ko'prigining versiyasi yoki None (ochiq emas)."""
        age = self._age("1c.alive")
        if age is None or age > self.ALIVE_SEC:
            return None
        try:
            with open(self._p("1c.alive"), "r", encoding="utf-8-sig") as f:
                return f.read().strip() or "?"
        except OSError:
            return None

    def alive(self):
        return self.alive_version() == BRIDGE_VERSION

    def ensure_running(self, timeout=None):
        """1C ochiq bo'lmasa — ochadi va tayyor bo'lishini kutadi. Eski versiyadagisi yopiladi."""
        if not self.exe or not os.path.isfile(self.exe):
            raise RuntimeError("1cv8.exe topilmadi: %s" % (self.exe or "-"))
        self.heartbeat()
        ver = self.alive_version()
        if ver == BRIDGE_VERSION:
            return False
        if ver is not None:
            log.info("1C ko'prigi eski versiyada (%s) — yopilmoqda", ver)
            with open(self._p("stop.flag"), "w") as f:
                f.write("1")
            end = self.clock() + 30
            while self.clock() < end and self.alive_version() is not None:
                self.sleep(1)
        for n in ("stop.flag", "1c.alive"):
            try:
                os.remove(self._p(n))
            except OSError:
                pass
        epf = self.ensure_epf()
        if not self.conf_done:
            self.conf_done = True
            changed, errs = allow_external(self.exe)
            if changed:
                log.info("1C: tashqi ishlov ogohlantirishi o'chirildi (conf.cfg)")
            for e in errs:
                log.warning("1C conf.cfg yozilmadi (administrator huquqi kerak): %s", e)
        kw = {}
        if os.name == "nt":
            si = subprocess.STARTUPINFO()
            si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            si.wShowWindow = 7          # SW_SHOWMINNOACTIVE — kichraytirilgan, kassirga xalaqit bermaydi
            kw["startupinfo"] = si
        log.info("1C ko'prigi ochilmoqda (bir marta, keyin ochiq turadi)")
        self.proc = self.popen(command(self.exe, self.conn, epf, self.jobs), **kw)
        start = self.clock()
        while self.clock() - start < (timeout or self.START_SEC):
            self.heartbeat()
            if self.alive():
                log.info("1C ko'prigi tayyor (%d s)", int(self.clock() - start))
                return True
            if self.proc.poll() is not None:
                raise RuntimeError("1C ochildi, lekin darhol yopildi (kod %s). 1C_oyna_tekshiruv.bat ni oching" %
                                   self.proc.poll())
            self.sleep(1)
        try:
            self.proc.kill()
        except OSError:
            pass
        raise RuntimeError("1C ochildi, lekin ko'prik ishga tushmadi (%d s) — kompyuterdagi 1C oynasida savol "
                           "chiqqan bo'lishi mumkin" % int(self.clock() - start))

    def stop(self):
        try:
            os.makedirs(self.jobs, exist_ok=True)
            with open(self._p("stop.flag"), "w") as f:
                f.write("1")
        except OSError:
            pass

    def leftovers(self):
        """{id: natija} (.res bor) va {id} (.wip eski, .res yo'q — 1C yozayotganda o'chgan, natija noma'lum)."""
        res, wip = {}, set()
        try:
            names = os.listdir(self.jobs)
        except OSError:
            return res, wip
        for n in names:
            base, ext = os.path.splitext(n)
            if ext == ".res":
                try:
                    with open(self._p(n), "r", encoding="utf-8-sig") as f:
                        res[base] = parse_result(f.read())
                except OSError:
                    continue
        for n in names:
            base, ext = os.path.splitext(n)
            if ext == ".wip" and base not in res:
                age = self._age(n)
                if age is not None and age > self.WIP_STUCK:
                    wip.add(base)
        return res, wip

    def print_errors(self):
        """1C chekni chiqara olmagan bo'lsa (*.perr): xabarlar, fayllar o'chiriladi."""
        out = []
        try:
            names = [n for n in os.listdir(self.jobs) if n.endswith(".perr")]
        except OSError:
            return out
        for n in names:
            try:
                with open(self._p(n), "r", encoding="utf-8-sig") as f:
                    out.append(re.sub(r"\s+", " ", f.read()).strip()[:300])
                os.remove(self._p(n))
            except OSError:
                continue
        return out

    def forget(self, rid):
        for ext in (".job", ".res", ".wip"):
            try:
                os.remove(self._p(rid + ext))
            except OSError:
                pass

    def submit(self, job):
        """Topshiriq fayli (allaqachon bor/boshlangan/natijali bo'lsa — qayta yozilmaydi)."""
        rid = job["id"]
        if any(os.path.exists(self._p(rid + e)) for e in (".job", ".wip", ".res")):
            return False
        tmp = self._p(rid + ".tmp")
        with open(tmp, "w", encoding="utf-8", newline="") as f:
            f.write(job_text(job))
        os.replace(tmp, self._p(rid + ".job"))
        return True

    def run(self, jobs, timeout=30):
        """jobs: [dict] -> {id: ("OK", raqam, sana) | ("ERR", xabar)} — [timeout] ichida natijasi kelganlar.
        Kelmaganlari papkada qoladi (1C keyin yozadi). 1C ochilmasa — RuntimeError."""
        os.makedirs(self.jobs, exist_ok=True)
        self.ensure_running()
        ids = [j["id"] for j in jobs]
        for j in jobs:
            self.submit(j)
        start = self.clock()
        out = {}
        while True:
            res, _ = self.leftovers()
            for rid in ids:
                if rid in res and rid not in out:
                    out[rid] = res[rid]
                    self.forget(rid)
            if len(out) == len(ids) or self.clock() - start >= timeout:
                break
            if not self.alive():
                self.heartbeat()
                if self.alive_version() is None:
                    break                 # 1C yopildi — keyingi safar qayta ochiladi
            self.sleep(0.3)
        return out
