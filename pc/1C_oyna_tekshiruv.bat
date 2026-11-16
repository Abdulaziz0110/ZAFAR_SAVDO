@echo off
rem v5.4: kassa sotuvini 1C ga yozish sinovi (1C oynasi orqali). Natijani rasmga olib dasturchiga yuboring.
cd /d "%~dp0"
set PY=python
where python >nul 2>&1 || set PY=py
%PY% onec_export.py --app-test
pause
