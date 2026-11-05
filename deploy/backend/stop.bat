@echo off
REM ============================================================================
REM  企业报销软件用户反馈与功能改进助手 —— 后端停止脚本（Windows）
REM
REM  说明：Windows 的 taskkill 只能按进程名或 PID 杀进程，
REM        无法像 Linux 那样用 pkill -f 匹配完整命令行。
REM        这里用 wmic 按命令行内容精确筛选出本后端的 java 进程，
REM        避免误杀同一台机器上其他 java 应用。
REM ============================================================================

setlocal enabledelayedexpansion

echo ==========================================================
echo   反馈改进助手 - 停止后端服务
echo ==========================================================

set FOUND=0

REM 遍历所有 java.exe 进程，筛选出命令行里含 feedback-backend 的
for /f "skip=1 tokens=1" %%p in ('wmic process where "name='java.exe'" get processid 2^>nul') do (
    set "PID=%%p"
    REM 去掉 wmic 输出尾部的回车符
    set "PID=!PID:~0,-1!"
    if defined PID (
        wmic process where "processid=!PID!" get commandline 2>nul | findstr /i "feedback" >nul 2>&1
        if !errorlevel! equ 0 (
            echo   发现后端进程 PID=!PID! ，正在停止...
            taskkill /PID !PID! /F >nul 2>&1
            set FOUND=1
        )
    )
)

if %FOUND% equ 0 (
    echo   未发现运行中的后端进程
) else (
    echo [OK] 后端服务已停止
)

echo.
endlocal
