@echo off
rem pc 11: kassa sotuvini 1C ga yozish sinovi (1C kopprigi - hech narsa yozmaydi). Natijani rasmga olib dasturchiga yuboring.
cd /d "%~dp0"
set PY=python
where python >nul 2>&1 || set PY=py
%PY% onec_export.py --app-test
pause
