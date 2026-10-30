@echo off
rem 1C tuzilishini discover.txt ga yozadi (ichida parol yo'q). Shu faylni dasturchiga yuboring.
cd /d "%~dp0"
rem Python PATH da bo'lmasa "py" (Python Launcher) ishlatiladi
set PY=python
where python >nul 2>&1 || set PY=py
%PY% onec_export.py --discover
pause
