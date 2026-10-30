@echo off
rem Narx yorlig'i printeri (Xprinter). Birinchi marta shu faylni oching: printer nomi, o'lcham va IP chiqadi.
rem Ishlasa: label_printer.vbs yorlig'ini Win+R > shell:startup papkasiga qo'ying.
cd /d "%~dp0"
rem Python PATH da bo'lmasa "py" (Python Launcher) ishlatiladi
set PY=python
where python >nul 2>&1 || set PY=py
%PY% label_printer.py --list
echo.
%PY% label_printer.py -v
pause
