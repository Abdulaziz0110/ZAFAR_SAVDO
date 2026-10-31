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
rem 1C qaysi turdagi (32/64-bit) bo'lsa, Python ham shunday bo'lishi kerak (1C ga ulanish uchun)
set "PF86=%ProgramFiles(x86)%"
set PY=
if exist "%ProgramFiles%\1cv8\" goto py64
if exist "%PF86%\1cv8\" goto py32
:py64
where py >nul 2>&1 && set "PY=py"
if not defined PY where python >nul 2>&1 && set "PY=python"
if defined PY goto havepy
echo.
echo Python topilmadi. python.org saytidan Python 3.12 ni o'rnating
echo ("Add python.exe to PATH" belgisini qo'ying), keyin ORNATISH.bat ni qayta oching.
start "" https://www.python.org/downloads/windows/
pause
exit /b 1
:py32
py -3-32 -c "pass" >nul 2>&1 && set "PY=py -3-32"
if defined PY goto havepy
echo.
echo 1C 32-bit ekan - unga 32-bit Python kerak.
echo python.org dan "Python 3.12 - Windows installer (32-bit)" ni o'rnating va ORNATISH.bat ni qayta oching.
start "" https://www.python.org/downloads/windows/
pause
exit /b 1
:havepy
echo Python: %PY%
echo Kerakli kutubxona o'rnatilmoqda (pywin32)...
%PY% -m pip install --disable-pip-version-check -q pywin32
%PY% "%~dp0ornatish.py"
echo.
pause
