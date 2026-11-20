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
BRIDGE_VERSION = "7"

# --------------------------------------------------------------------------- 1C kodi (tashqi ishlov moduli)

BSL = r"""// IMSOFT kassa ko'prigi v__VER__: 1C ochiq turadi, papkadagi *.job -> ПродажаРозница (1C sensor oynasi bilan bir xil), natija *.res
Перем ПечатьСсылка, ПечатьВозврат, РпцОбъекты, РпцСлед, РпцБанд;

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
	// avval vaqtinchalik faylga, keyin nomini almashtirish: dastur yarim yozilgan faylni o'qimaydi,
	// fayl band bo'lsa ham ("Ошибка совместного доступа") 3 marta urinadi
	Врем = Путь + ".part";
	Для Урин = 1 По 3 Цикл
		Попытка
			Т = Новый ТекстовыйДокумент;
			Т.УстановитьТекст(Текст);
			Т.Записать(Врем, КодировкаТекста.UTF8);
			Ф = Новый Файл(Путь);
			Если Ф.Существует() Тогда
				УдалитьФайлы(Путь);
			КонецЕсли;
			ПереместитьФайл(Врем, Путь);
			Возврат;
		Исключение
			Если Урин = 3 Тогда
				ВызватьИсключение;
			КонецЕсли;
		КонецПопытки;
	КонецЦикла;
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

// ---- v6 (pc 30): telefondan 1C'ga yozish ham SHU 1C orqali (alohida yashirin 1C ochilmaydi) ----
// Dastur har bir amalni (*.rpq, JSON) yuboradi: xususiyatni o'qish/yozish, metod chaqirish; obyektlar raqam bilan saqlanadi.
Функция РпцИз(З)
	Если ТипЗнч(З) <> Тип("Соответствие") Тогда
		Возврат З;
	КонецЕсли;
	Х = З.Получить("h");
	Если Х <> Неопределено Тогда
		Об = РпцОбъекты.Получить(Х);
		Если Об = Неопределено Тогда
			ВызватьИсключение "1C obyekti topilmadi (" + XMLСтрока(Х) + ")";
		КонецЕсли;
		Возврат Об;
	КонецЕсли;
	Д = З.Получить("d");
	Если Д <> Неопределено Тогда
		Возврат Дата(Д);
	КонецЕсли;
	Если З.Получить("u") <> Неопределено Тогда
		Возврат Неопределено;
	КонецЕсли;
	Возврат З.Получить("v");
КонецФункции

Функция РпцВ(Рез)
	С = Новый Соответствие;
	Т = ТипЗнч(Рез);
	Если Рез = Неопределено Или Рез = Null Тогда
		С.Вставить("u", 1);
		Возврат С;
	КонецЕсли;
	Если Т = Тип("Число") Или Т = Тип("Строка") Или Т = Тип("Булево") Тогда
		С.Вставить("v", Рез);
		Возврат С;
	КонецЕсли;
	Если Т = Тип("Дата") Тогда
		С.Вставить("d", Формат(Рез, "ДФ=yyyyMMddHHmmss; ДП=00010101000000"));
		Возврат С;
	КонецЕсли;
	РпцСлед = РпцСлед + 1;
	РпцОбъекты.Вставить(РпцСлед, Рез);
	С.Вставить("h", РпцСлед);
	Возврат С;
КонецФункции

Функция РпцАмал(Зап)
	Оп   = Зап.Получить("op");
	Имя  = Зап.Получить("n");
	Х    = Зап.Получить("h");
	Пред = "";
	Об   = Неопределено;
	Если Х <> Неопределено И Х <> 0 Тогда
		Об = РпцИз(Зап);
		Пред = "Об.";
	КонецЕсли;
	Арг = Новый Массив;
	Сп = Зап.Получить("a");
	Если Сп <> Неопределено Тогда
		Для Каждого А Из Сп Цикл
			Арг.Добавить(РпцИз(А));
		КонецЦикла;
	КонецЕсли;
	Рез = Неопределено;
	Если Оп = "get" Тогда
		Выполнить("Рез = " + Пред + Имя);
	КонецЕсли;
	Если Оп = "set" Тогда
		Зн = РпцИз(Зап.Получить("v"));
		Выполнить(Пред + Имя + " = Зн");
	КонецЕсли;
	Если Оп = "call" Или Оп = "proc" Тогда
		Код = Пред + Имя + "(";
		Для Н = 0 По Арг.ВГраница() Цикл
			Код = Код + ?(Н > 0, ", ", "") + "Арг[" + XMLСтрока(Н) + "]";
		КонецЦикла;
		Код = Код + ")";
		Если Оп = "call" Тогда
			Выполнить("Рез = " + Код);
		Иначе
			Выполнить(Код);
		КонецЕсли;
	КонецЕсли;
	Если Оп = "new" Тогда
		Рез = Новый(Имя);
	КонецЕсли;
	Если Оп = "str" Тогда
		Рез = Строка(Арг[0]);
	КонецЕсли;
	Если Оп = "eq" Тогда
		Рез = (Арг[0] = Арг[1]);
	КонецЕсли;
	Если Оп = "free" Тогда
		РпцОбъекты = Новый Соответствие;
		РпцСлед = 0;
	КонецЕсли;
	Возврат РпцВ(Рез);
КонецФункции

Процедура РпцФайл(Ф, Папка)
	Асос = Папка + "\" + Ф.ИмяБезРасширения;
	Отв = Новый Соответствие;
	// v7: fayl hali band bo'lishi mumkin (antivirus/Windows yangi faylni tekshirayotgan payt) — o'qib bo'lmasa javob
	// berilmaydi, fayl qoladi va keyingi aylanishda qayta o'qiladi (amal BAJARILMAGAN, ikki marta bajarilmaydi)
	Зап = Неопределено;
	Попытка
		Чт = Новый ЧтениеJSON;
		Чт.ОткрытьФайл(Ф.ПолноеИмя, "UTF-8");
		Зап = ПрочитатьJSON(Чт, Истина);
		Чт.Закрыть();
	Исключение
		Зап = Неопределено;
	КонецПопытки;
	Если Зап = Неопределено Тогда
		Сон = РпцБанд.Получить(Ф.Имя);
		Если Сон = Неопределено Тогда
			РпцБанд.Вставить(Ф.Имя, ТекущаяДата());
			Возврат;
		КонецЕсли;
		Если ТекущаяДата() - Сон < 30 Тогда
			Возврат;
		КонецЕсли;
	КонецЕсли;
	Попытка
		Если Зап = Неопределено Тогда
			ВызватьИсключение "So'rov faylini o'qib bo'lmadi (band): " + Ф.Имя;
		КонецЕсли;
		Отв.Вставить("r", РпцАмал(Зап));
		Отв.Вставить("ok", Истина);
	Исключение
		Отв.Вставить("ok", Ложь);
		Отв.Вставить("e", СтрЗаменить(СтрЗаменить(ОписаниеОшибки(), Символы.ПС, " "), Символы.ВК, " "));
	КонецПопытки;
	Зп = Новый ЗаписьJSON;
	Зп.УстановитьСтроку();
	ЗаписатьJSON(Зп, Отв);
	ЗаписатьТекст(Асос + ".rps", Зп.Закрыть());
	РпцБанд.Удалить(Ф.Имя);
	Попытка
		УдалитьФайлы(Ф.ПолноеИмя);
	Исключение
	КонецПопытки;
КонецПроцедуры

Процедура РпцСеанс(Папка, Оболочка)
	// tez rejim: so'rovlar kechikishsiz bajariladi; 20 soniya so'rov kelmasa — oddiy (1 soniyalik) aylanishga qaytadi
	Если РпцОбъекты = Неопределено Тогда
		РпцОбъекты = Новый Соответствие;
		РпцСлед = 0;
	КонецЕсли;
	РпцБанд = Новый Соответствие;
	Охирги = ТекущаяДата();
	Пульс  = ТекущаяДата();
	Бекор  = 0;
	Пока Истина Цикл
		Файлы = НайтиФайлы(Папка, "*.rpq");
		Если Файлы.Количество() > 0 Тогда
			Для Каждого Ф Из Файлы Цикл
				РпцФайл(Ф, Папка);
			КонецЦикла;
			Охирги = ТекущаяДата();
			Бекор  = 0;
		Иначе
			Бекор = Бекор + 1;
			Если ТекущаяДата() - Охирги > 20 Или ФайлЕсть(Папка + "\stop.flag") Тогда
				Прервать;
			КонецЕсли;
			Если Бекор > 200 Тогда
				// so'rovlar orasida kassa sotuvi ham kutib qolmasin; protsessorni band qilmaslik uchun qisqa kutish
				ИшлаТопшириклар(Папка);
				Если Оболочка <> Неопределено Тогда
					Попытка
						Оболочка.Run("cmd /c ping -n 1 127.0.0.1 >nul", 0, Истина);
					Исключение
					КонецПопытки;
				КонецЕсли;
			КонецЕсли;
		КонецЕсли;
		Если ТекущаяДата() - Пульс >= 5 Тогда
			Попытка
				ЗаписатьТекст(Папка + "\1c___VER__.alive", "1");
			Исключение
			КонецПопытки;
			Пульс = ТекущаяДата();
		КонецЕсли;
	КонецЦикла;
	РпцОбъекты = Новый Соответствие;
	РпцСлед = 0;
КонецПроцедуры

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
			// "tirikman" belgisi: versiya fayl NOMIDA (dastur faylni ochmaydi — faqat vaqtiga qaraydi)
			Попытка
				ЗаписатьТекст(Папка + "\1c___VER__.alive", "1");
			Исключение
			КонецПопытки;
			Пульс = ТекущаяДата();
		КонецЕсли;
		Попытка
			ИшлаТопшириклар(Папка);
			Если НайтиФайлы(Папка, "*.rpq").Количество() > 0 Тогда
				РпцСеанс(Папка, Оболочка);
			КонецЕсли;
		Исключение
			Попытка
				ЗаписатьТекст(Папка + "\1c.error", ОписаниеОшибки());
			Исключение
			КонецПопытки;
		КонецПопытки;
		Пауза(Оболочка);
	КонецЦикла;
	Попытка
		УдалитьФайлы(Папка + "\1c___VER__.alive");
	Исключение
	КонецПопытки;
КонецПроцедуры

// 1C bitta marta ochiladi va OCHIQ turadi: har ~1 soniyada papkani tekshiradi.
// Hech qanday xato oynasi chiqmasin (oyna chiqsa 1C to'xtab qoladi) — xato bo'lsa 1C yopiladi, dastur qayta ochadi.
Попытка
	Ишла();
Исключение
КонецПопытки;
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


def parse_bridge_procs(out, jobs):
    """wmic/powershell chiqishi ("uzel,buyruq qatori,PID") -> shu papkali ko'prik 1C PID'lari."""
    pids = []
    key = os.path.normcase(jobs).lower()
    for line in (out or "").splitlines():
        line = line.strip()
        if not line or line.lower().startswith("node,"):
            continue
        head, _, pid = line.rpartition(",")
        if not pid.strip().isdigit():
            continue
        cmd = os.path.normcase(head.partition(",")[2]).lower()
        if EPF_NAME.lower() in cmd and re.search(re.escape(key) + r'(?:["\s]|$)', cmd):
            pids.append(int(pid))
    return pids


def hide_windows(pids):
    """Berilgan jarayonlarning ko'rinib turgan oynalarini yashirish (ShowWindowAsync — 1C band bo'lsa ham osilmaydi)."""
    try:
        import ctypes
        from ctypes import wintypes
        user32 = ctypes.windll.user32
    except (ImportError, AttributeError, OSError):
        return 0
    hidden = []
    proto = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

    def cb(hwnd, _):
        if user32.IsWindowVisible(hwnd):
            pid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids:
                user32.ShowWindowAsync(hwnd, 0)          # SW_HIDE
                hidden.append(hwnd)
        return True
    try:
        user32.EnumWindows(proto(cb), 0)
    except OSError:
        return 0
    return len(hidden)


class Bridge:
    """1C'ni oddiy rejimda BIR MARTA ochadi va ochiq qoldiradi (pc 12): 1C har ~1 soniyada papkani tekshiradi,
    sotuv 1-2 soniyada yoziladi. Dastur (Python) ishlab turgani "py.alive" belgisi bilan bildiriladi — dastur
    to'xtasa 5 daqiqadan keyin 1C o'zi yopiladi; dastur qayta ishga tushsa ochiq 1C'dan foydalanadi."""

    ALIVE_SEC = 60         # 1C belgisi (1c.alive) shundan eski bo'lsa — 1C ishlamayapti (chek bosilayotganda ham)
    WIP_STUCK = 90         # .wip shundan eski va .res yo'q — 1C yozish paytida o'chgan (natija noma'lum)
    START_SEC = 180        # 1C ochilishini kutish

    def __init__(self, conn, here, exe, popen=subprocess.Popen, clock=time.time, sleep=time.sleep, jobs_dir=JOBS_DIR,
                 procs=None):
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
        self.procs = procs
        self.pids = set()

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
        """Ochiq 1C ko'prigining versiyasi yoki None (ochiq emas). Fayl OCHILMAYDI (1C yozayotgan bo'lsa ham
        "Ошибка совместного доступа" bo'lmasin) — versiya fayl nomida: 1c_<versiya>.alive."""
        try:
            names = os.listdir(self.jobs)
        except OSError:
            return None
        best = None
        for n in names:
            m = re.match(r"^1c_(\w+)\.alive$", n)
            if not m:
                continue
            age = self._age(n)
            if age is not None and age <= self.ALIVE_SEC and (best is None or age < best[0]):
                best = (age, m.group(1))
        return best[1] if best else None

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
        try:
            names = os.listdir(self.jobs)
        except OSError:
            names = []
        for n in ["stop.flag"] + [x for x in names if x.endswith(".alive") and x != "py.alive"]:
            try:
                os.remove(self._p(n))
            except OSError:
                pass
        self.kill_stale()          # osilib qolgan (xato oynasi chiqqan) ko'prik 1C'lari — bittadan ortiq ochilmasin
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
            self.hide()
            if self.alive():
                log.info("1C ko'prigi tayyor (%d s)", int(self.clock() - start))
                self.hide()
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

    def bridge_pids(self):
        """Shu papka bilan ishlayotgan ko'prik 1C jarayonlari (Windows)."""
        if self.procs is not None:
            return self.procs()
        if os.name != "nt":
            return []
        flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        out = ""
        for cmd in (["wmic", "process", "where", "name='1cv8.exe'", "get", "ProcessId,CommandLine", "/format:csv"],
                    ["powershell", "-NoProfile", "-Command",
                     "Get-CimInstance Win32_Process -Filter \"Name='1cv8.exe'\" | ForEach-Object { 'x,' + $_.CommandLine + ',' + $_.ProcessId }"]):
            try:
                r = subprocess.run(cmd, capture_output=True, text=True, timeout=30, creationflags=flags)
                out = r.stdout or ""
                if r.returncode == 0 and out.strip():
                    break
            except (OSError, subprocess.SubprocessError):
                continue
        return parse_bridge_procs(out, self.jobs)

    def kill_stale(self):
        for pid in self.bridge_pids():
            log.warning("1C ko'prigi osilib qolgan (jarayon %s) — yopilmoqda", pid)
            try:
                subprocess.run(["taskkill", "/PID", str(pid), "/F"], capture_output=True, timeout=20,
                               creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            except (OSError, subprocess.SubprocessError):
                pass

    def hide(self):
        """Ko'prik 1C oynalari yashiriladi — kompyuterda ishlaganda oldinga chiqib xalaqit bermasin."""
        if os.name != "nt":
            return 0
        pids = set(self.pids)
        if self.proc is not None:
            pids.add(self.proc.pid)
        elif not pids and self.alive():
            # dastur qayta ishga tushgan, 1C esa oldindan ochiq — uning jarayonini topib olamiz (bir marta)
            self.pids = pids = set(self.bridge_pids())
        return hide_windows(pids) if pids else 0

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


# --------------------------------------------------------------------------- pc 30: telefon yozuvlari shu 1C orqali

class RpcError(Exception):
    """1C amalni bajarmadi (xato matni 1C'dan) yoki ko'prik javob bermadi."""


class RpcAttrError(RpcError, AttributeError):
    """Xususiyat yo'q — getattr(obj, "X", standart) ishlashi uchun AttributeError ham."""


# Qiymat qaytarmaydigan metodlar (1C'da "Рез = Об.Записать()" xato bo'ladi) va qaytaradiganlar.
# Ro'yxatda yo'q nom — xususiyat deb o'qiladi.
RPC_PROCS = frozenset(("Записать", "Очистить", "УстановитьНовыйКод", "УстановитьПараметр", "Установить",
                       "УстановитьПометкуУдаления", "Удалить", "Вставить", "Заполнить", "Прочитать", "УстановитьСсылкуНового"))
RPC_FUNCS = frozenset(("НайтиПоКоду", "НайтиПоНаименованию", "НайтиПоРеквизиту", "НайтиПоНомеру", "Пустая",
                       "УникальныйИдентификатор", "СоздатьДокумент", "СоздатьЭлемент", "СоздатьГруппу", "Получить", "Добавить",
                       "Выполнить", "Выбрать", "Следующий", "ПолучитьОбъект", "Количество", "Скопировать", "Остатки",
                       "ПустаяСсылка", "СоздатьМенеджерЗаписи", "ПолучитьСсылку", "Выгрузить", "Пустой", "Найти",
                       "ЭтоГруппа", "Владелец", "ПолучитьНовыйКод"))
_RPC_NAME = re.compile(r"^[A-Za-z_Ѐ-ӿ][0-9A-Za-z_Ѐ-ӿ]*$")


class RObj:
    """Ko'prik 1C'idagi obyekt (raqami bilan). Xuddi COM obyekti kabi ishlatiladi: obj.Наименование, obj.Записать()."""
    __slots__ = ("_rpc", "_h")

    def __init__(self, rpc, h):
        object.__setattr__(self, "_rpc", rpc)
        object.__setattr__(self, "_h", h)

    def __getattr__(self, name):
        if name.startswith("_") or not _RPC_NAME.match(name):
            raise AttributeError(name)
        if name in RPC_PROCS:
            return lambda *a: self._rpc.op("proc", self._h, name, list(a))
        if name in RPC_FUNCS:
            return lambda *a: self._rpc.op("call", self._h, name, list(a))
        return self._rpc.op("get", self._h, name)

    def __setattr__(self, name, value):
        if not _RPC_NAME.match(name):
            raise AttributeError(name)
        self._rpc.op("set", self._h, name, value=value)

    def __bool__(self):
        return True

    def __str__(self):
        return str(self._rpc.op("str", 0, "", [self]))

    def __eq__(self, other):
        if isinstance(other, RObj):
            return bool(self._rpc.op("eq", 0, "", [self, other]))
        return NotImplemented

    def __hash__(self):
        return hash((id(self._rpc), self._h))

    def __repr__(self):
        return "<1C #%s>" % self._h


class RRoot(RObj):
    """1C global konteksti (V83.Application o'rnida): Справочники, Документы, Константы ...; String/NewObject."""
    __slots__ = ()

    def String(self, v):  # noqa: N802 — COM nomi
        return str(self._rpc.op("str", 0, "", [v]))

    def NewObject(self, name, *args):  # noqa: N802
        return self._rpc.op("new", 0, name, list(args))


class Rpc:
    """Telefondan 1C'ga yozish uchun alohida yashirin 1C ochilmaydi (bu kompyuterda V83.Application 1 soniyada yopiladi va
    ortiqcha 1C seansi bo'ladi): har bir amal ochiq turgan ko'prik 1C'iga *.rpq fayl bilan yuboriladi, javob *.rps."""

    OP_SEC = 300           # bitta amal (masalan katta hujjatni o'tkazish yoki kassa sotuvi tugashini kutish)
    START_SEC = 180

    def __init__(self, bridge, clock=time.time, sleep=time.sleep):
        self.br = bridge
        self.clock = clock
        self.sleep = sleep
        self.n = 0
        self.sid = uuid.uuid4().hex[:6]
        self.root = RRoot(self, 0)
        self.used = False

    def wait_ready(self, timeout=None):
        """Ko'prik 1C ochiq bo'lishini kutadi (kassa xizmati ochadi). @raise RpcError."""
        end = self.clock() + (self.START_SEC if timeout is None else timeout)
        while not self.br.alive():
            if self.clock() >= end:
                raise RpcError("1C ko'prigi ochilmadi (kassa dasturi 1C'ni ocholmadi) — keyinroq qayta yuboring")
            self.sleep(1)
        return self

    @staticmethod
    def enc(v):
        import datetime as _dt
        from decimal import Decimal
        if isinstance(v, RObj):
            return {"h": v._h}
        if v is None:
            return {"u": 1}
        if isinstance(v, bool):
            return {"v": v}
        if isinstance(v, Decimal):
            return {"v": float(v)}
        if isinstance(v, (int, float, str)):
            return {"v": v}
        if isinstance(v, _dt.datetime):
            return {"d": v.strftime("%Y%m%d%H%M%S")}
        if isinstance(v, _dt.date):
            return {"d": v.strftime("%Y%m%d") + "000000"}
        raise RpcError("1C'ga yuborib bo'lmaydigan qiymat: %r" % (v,))

    def dec(self, r):
        import datetime as _dt
        if not isinstance(r, dict) or "u" in r:
            return None
        if "h" in r:
            return RObj(self, r["h"])
        if "d" in r:
            d = str(r["d"] or "")
            if len(d) < 14 or d.startswith("0001"):
                return _dt.datetime(1, 1, 1)
            return _dt.datetime(int(d[0:4]), int(d[4:6]), int(d[6:8]), int(d[8:10]), int(d[10:12]), int(d[12:14]))
        return r.get("v")

    def op(self, kind, h, name="", args=None, value=None):
        import json
        if kind in ("get", "set", "call", "proc") and not _RPC_NAME.match(name or ""):
            raise RpcError("noto'g'ri nom: %r" % (name,))
        req = {"op": kind, "h": h, "n": name}
        if args is not None:
            req["a"] = [self.enc(x) for x in args]
        if kind == "set":
            req["v"] = self.enc(value)
        self.n += 1
        self.used = True
        os.makedirs(self.br.jobs, exist_ok=True)
        base = self.br._p("r%s_%07d" % (self.sid, self.n))
        tmp = base + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(req, f, ensure_ascii=False)
        os.replace(tmp, base + ".rpq")
        res = base + ".rps"
        start = check = self.clock()
        delay = 0.002
        while not os.path.exists(res):
            now = self.clock()
            if now - check >= 5:
                check = now
                self.br.heartbeat()
                if self.br.alive_version() is None:
                    self._drop(base)
                    raise RpcError("1C ko'prigi yopildi (amal: %s)" % name)
            if now - start >= self.OP_SEC:
                self._drop(base)
                raise RpcError("1C javob bermadi (amal: %s)" % name)
            self.sleep(delay)
            delay = min(delay * 1.6, 0.05)
        text = ""
        for _ in range(20):
            try:
                with open(res, "r", encoding="utf-8-sig") as f:
                    text = f.read()
                break
            except OSError:
                self.sleep(0.05)
        try:
            os.remove(res)
        except OSError:
            pass
        try:
            r = json.loads(text)
        except ValueError:
            raise RpcError("1C javobi tushunarsiz: %s" % text[:200])
        if not r.get("ok"):
            msg = str(r.get("e") or "1C xatosi")[:600]
            if kind == "get":
                raise RpcAttrError(msg)
            raise RpcError(msg)
        return self.dec(r.get("r"))

    def _drop(self, base):
        try:
            os.remove(base + ".rpq")
        except OSError:
            pass

    def close(self):
        """Seans tugadi — 1C'dagi obyektlar ro'yxati tozalanadi."""
        if self.used:
            try:
                self.op("free", 0)
            except (RpcError, OSError) as e:
                log.debug("ko'prik seansini yopish: %s", e)
            self.used = False
