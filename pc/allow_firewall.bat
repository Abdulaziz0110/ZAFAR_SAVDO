@echo off
rem Windows brandmauerida yorliq printeri portlarini ochadi (hamma tarmoq turlari uchun).
rem O'ng tugma > "Run as administrator" (Запуск от имени администратора).
netsh advfirewall firewall delete rule name="IMSOFT yorliq TCP 9110" >nul 2>&1
netsh advfirewall firewall delete rule name="IMSOFT yorliq UDP 9111" >nul 2>&1
netsh advfirewall firewall add rule name="IMSOFT yorliq TCP 9110" dir=in action=allow protocol=TCP localport=9110 profile=any
netsh advfirewall firewall add rule name="IMSOFT yorliq UDP 9111" dir=in action=allow protocol=UDP localport=9111 profile=any
echo.
echo Tayyor. Endi start_labels.bat ni oching va telefon brauzerida http://192.168.1.200:9110 ni tekshiring.
pause
