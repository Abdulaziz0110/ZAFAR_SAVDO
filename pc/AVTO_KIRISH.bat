@echo off
rem pc 12: kompyuter yonganda Windows'ga avtomatik kirish (IMSOFT dasturi, kassa va 1C o'zi ishga tushadi).
cd /d "%~dp0"
net session >nul 2>&1
if errorlevel 1 (
  echo Administrator huquqi so'ralmoqda...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
set PY=python
where python >nul 2>&1 || set PY=py
%PY% "%~dp0ornatish.py" --autologon
echo.
pause
