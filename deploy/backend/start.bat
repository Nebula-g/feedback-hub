@echo off
REM ============================================================================
REM  企业报销软件用户反馈与功能改进助手 —— 后端启动脚本（Windows）
REM
REM  用途：以 jar 方式启动 Spring Boot 后端，监听 8081 端口
REM
REM  使用前请修改下面「配置区」的三个路径：
REM    JAVA_EXE  —— JDK 17 的 java.exe
REM    JAR_FILE  —— 打包好的后端 jar
REM    LOG_DIR   —— 日志目录
REM
REM  可选环境变量（不设置也能正常启动，默认走 rule 规则实现）：
REM    AI_PROVIDER=llm          启用真实大模型
REM    AI_API_KEY=sk-xxx        大模型密钥（务必用环境变量，别写进脚本）
REM    AI_BASE_URL=https://...  兼容 OpenAI 协议的端点
REM    AI_MODEL=deepseek-chat  模型名
REM ============================================================================

setlocal

REM ============================ 配置区 ============================
set "JAVA_EXE=C:\Program Files\Java\jdk-17\bin\java.exe"
set "JAR_FILE=D:\feedback-deploy\feedback-hub.jar"
set "LOG_DIR=D:\feedback-deploy\logs"
REM ================================================================

echo ==========================================================
echo   反馈改进助手 - 后端服务启动中
echo ==========================================================

REM ---- 前置检查 ----
if not exist "%JAVA_EXE%" (
    echo [ERROR] 找不到 java.exe：%JAVA_EXE%
    echo         请修改脚本中的 JAVA_EXE 路径
    goto :fail
)

if not exist "%JAR_FILE%" (
    echo [ERROR] 找不到后端 jar：%JAR_FILE%
    echo         请先执行： cd backend ^&^& bash mvn.sh -o clean package -DskipTests
    echo         产物名由 pom.xml 的 finalName 决定，当前为 feedback-hub.jar
    goto :fail
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

REM ---- 端口占用检查 ----
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo [WARN] 端口 8081 已被占用，可能已有实例在运行
    echo       如需重启，请先运行 stop.bat
    goto :fail
)

REM ---- JVM 参数说明 ----
REM   -Xms/-Xmx      堆内存上下限设为一致，避免运行期动态扩容带来的抖动
REM   -Dfile.encoding 显式指定 UTF-8，Windows 默认 GBK 会导致中文日志乱码
REM   -Dserver.port  双保险，application.yml 里已配 8081，这里再锁一次
REM   -Duser.timezone 避免 Linux/Windows 时区差异导致的时间字段偏差
set "JAVA_OPTS=-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

echo.
echo   JAR   : %JAR_FILE%
echo   PORT  : 8081
echo   LOG   : %LOG_DIR%\backend.log
echo   AI    : %AI_PROVIDER%
echo.
echo   启动中，请稍候...
echo.

REM ---- 启动（后台方式，日志重定向到文件）----
start "feedback-backend" /min "%JAVA_EXE%" %JAVA_OPTS% -jar "%JAR_FILE%" ^
  --server.port=8081 ^
  > "%LOG_DIR%\backend.log" 2>&1

echo [OK] 启动命令已发出，请等待约 10 秒后访问：
echo      http://localhost:8080
echo.
echo      查看日志： type "%LOG_DIR%\backend.log"
echo.
endlocal
exit /b 0

:fail
echo.
echo [FAILED] 启动失败
endlocal
exit /b 1
