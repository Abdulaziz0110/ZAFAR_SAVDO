@echo off
rem 1C tuzilishini discover.txt ga yozadi (ichida parol yo'q). Shu faylni dasturchiga yuboring.
cd /d "%~dp0"
python onec_export.py --discover
pause
