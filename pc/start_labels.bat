@echo off
rem Narx yorlig'i printeri (Xprinter). Birinchi marta shu faylni oching: printer nomi, o'lcham va IP chiqadi.
rem Ishlasa: label_printer.vbs yorlig'ini Win+R > shell:startup papkasiga qo'ying.
cd /d "%~dp0"
python label_printer.py --list
echo.
python label_printer.py -v
pause
