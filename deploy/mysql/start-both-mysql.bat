@echo off
REM Start both MySQL instances with one command.
REM MySQL80 depends on MySQL3308, so SCM starts 3308 first, then 3306.
REM Must be run as Administrator.

net start MySQL80
if errorlevel 1 (
    echo.
    echo FAILED. Please run this file as Administrator.
    echo.
    pause
    exit /b 1
)

echo.
echo --- port 3306 ---
netstat -ano | findstr ":3306 " | findstr LISTENING
echo --- port 3308 ---
netstat -ano | findstr ":3308 " | findstr LISTENING
echo.
pause
