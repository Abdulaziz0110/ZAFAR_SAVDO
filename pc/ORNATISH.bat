@echo off
rem IMSOFT - kompyuterni bir marta sozlash. Shu faylni ikki marta bosing: administrator so'raydi, keyin savollar beradi.
chcp 65001 >nul
cd /d "%~dp0"
net session >nul 2>&1
if errorlevel 1 (
  echo Administrator huquqi so'ralmoqda...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
set PY=
where py >nul 2>&1 && set PY=py
if not defined PY where python >nul 2>&1 && set PY=python
if not defined PY (
  echo.
  echo Python topilmadi. python.org saytidan Python 3.12 ni o'rnating
  echo ^(o'rnatishda "Add python.exe to PATH" belgisini qo'ying^), keyin ORNATISH.bat ni qayta oching.
  start "" https://www.python.org/downloads/windows/
  pause
  exit /b 1
)
echo Kerakli kutubxona o'rnatilmoqda (pywin32)...
%PY% -m pip install --disable-pip-version-check -q pywin32
%PY% "%~dp0ornatish.py"
echo.
pause
