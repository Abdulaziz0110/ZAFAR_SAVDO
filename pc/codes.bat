@echo off
rem 1C dan tarozi PLU raqamlari va shtrix kodlarni olib telefonga yuboradi. Oynadagi "PLU manbasi" va "shtrix" qatorlariga qarang.
cd /d "%~dp0"
python onec_export.py --codes -v
pause
