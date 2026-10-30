@echo off
rem Windows brandmauerida yorliq printeri portlarini ochadi (faqat shu tarmoq). O'ng tugma > "Run as administrator".
netsh advfirewall firewall add rule name="IMSOFT yorliq TCP 9110" dir=in action=allow protocol=TCP localport=9110 profile=private,domain
netsh advfirewall firewall add rule name="IMSOFT yorliq UDP 9111" dir=in action=allow protocol=UDP localport=9111 profile=private,domain
pause
