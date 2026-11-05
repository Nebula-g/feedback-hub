@echo off
REM Stop both MySQL instances. Run as Administrator.

net stop MySQL80
net stop MySQL3308

echo.
echo --- remaining mysqld ports (empty = all stopped) ---
netstat -ano | findstr ":3306 :3308" | findstr LISTENING
echo.
pause
