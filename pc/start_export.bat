@echo off
rem 1C -> Excel -> telefon. Birinchi marta shu faylni oching va oynadagi natijani ko'ring.
rem Ishlasa: onec_export.vbs yorlig'ini Win+R > shell:startup papkasiga qo'ying.
cd /d "%~dp0"
python onec_export.py --once -v
pause
