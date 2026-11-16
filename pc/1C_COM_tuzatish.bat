@echo off
rem v5.4: 1C oynasi (V83.Application) COM-server sifatida qayta ro'yxatdan o'tkaziladi.
rem "1C ga oyna orqali ulanib bo'lmadi: Neizvestnyy interfeys" xatosida: o'ng tugma - Zapusk ot imeni administratora.
cd /d "%~dp0"
set PY=python
where python >nul 2>&1 || set PY=py
%PY% -c "import onec_export as o; print(\"OK\" if o.register_app_server() else \"1cv8.exe topilmadi\")"
pause
