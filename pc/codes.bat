@echo off
rem 1C dan tarozi PLU raqamlari va shtrix kodlarni olib telefonga yuboradi. Oynadagi "PLU manbasi" va "shtrix" qatorlariga qarang.
cd /d "%~dp0"
rem Python PATH da bo'lmasa "py" (Python Launcher) ishlatiladi
set PY=python
where python >nul 2>&1 || set PY=py
%PY% onec_export.py --codes -v
pause
