@echo off
rem ============================================================
rem Start the project MySQL instance on port 3308 (feedback-hub)
rem Put this file into the Windows Startup folder to auto-start.
rem Note: does not touch the system service MySQL80 on port 3306.
rem Keep this file ASCII-only: cmd decodes non-ASCII bytes by the
rem system codepage (GBK), and UTF-8 Chinese breaks the parsing.
rem ============================================================

set MYSQL_HOME=E:\MySQL\mysql-8.0.46-winx64
set MYSQL_INI=E:\Qoder CN\QC\fbcp-mysql-test\my-3308.ini

netstat -ano | findstr ":3308" | findstr LISTENING >nul
if %errorlevel%==0 (
    echo MySQL 3308 is already running. Nothing to do.
    exit /b 0
)

echo Starting MySQL 3308 (feedback-hub)...
start "MySQL3308" /MIN "%MYSQL_HOME%\bin\mysqld.exe" --defaults-file="%MYSQL_INI%"

echo Waiting for MySQL 3308 to accept connections
:wait
rem ping is used instead of timeout: timeout reads stdin and fails
rem when the script is launched by an automated host without a console
ping -n 3 127.0.0.1 >nul
netstat -ano | findstr ":3308" | findstr LISTENING >nul
if errorlevel 1 goto wait

echo MySQL 3308 is ready.
exit /b 0
