@echo off
setlocal enableextensions
title Vajra Overdrive - Setup
echo.
echo   ============================================
echo    VAJRA OVERDRIVE  --  SETUP
echo   ============================================
echo.

rem ---- where to install: default is under your own profile, no admin rights ----
set "DEST=%LOCALAPPDATA%\Programs\Vajra Overdrive"
set "RLY="
set "QUIET=0"
if /i "%~1"=="/quiet" (
    set "DEST=%~2"
    if not "%~3"=="" set "RLY=%~3"
    set "QUIET=1"
    goto answered
)
rem ---- interactive: ask both questions first (no child process has touched stdin) ----
set /p TGT=Install into [%DEST%]:
if not "%TGT%"=="" set "DEST=%TGT%"
set /p RLY=A SHARED relay box, or ENTER to skip (the game opens its own door):
:answered

rem ---- the installer exe carries the game compressed; the zip carries it loose ----
if not exist "%~dp0app" if exist "%~dp0_setup.zip" (
    echo   Unpacking the game ...
    powershell -NoProfile -Command "Expand-Archive -LiteralPath '%~dp0_setup.zip' -DestinationPath (Get-Item '%~dp0').FullName -Force" >nul 2>nul
)
if not exist "%~dp0app" (
    echo   GAME FILES MISSING from "%~dp0" - re-download the setup.
    if "%QUIET%"=="0" pause
    exit /b 1
)

rem ---- the game runs on Java 17+ — skipped entirely when the bundled runtime rides along ----
if exist "%~dp0app\jre\bin\java.exe" goto javaok
where java >nul 2>nul
if errorlevel 1 (
    echo.
        echo   JAVA NOT FOUND.  Either install "Temurin 17" from https://adoptium.net
        echo   ^(restart the machine after installing^), or rebuild the setup with a
        echo   bundled runtime (tools/jre) so no Java is needed at all.
        if "%QUIET%"=="0" pause
        exit /b 1
    )
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JV=%%~v"
echo   Found java %JV%
for /f "tokens=1 delims=. " %%a in ("%JV%") do set /a "VMAJOR=%%a" 2>nul
if not defined VMAJOR set VMAJOR=0
if %VMAJOR% lss 17 (
    echo.
    echo   JAVA %VMAJOR% IS TOO OLD.  Vajra Overdrive runs on Java 17+.
    echo   Install "Temurin 17" from https://adoptium.net and run this setup again.
    if "%QUIET%"=="0" pause
    exit /b 1
)

:javaok

rem ---- upgrading over a live install: the game and its relay hold the jars open ----
powershell -NoProfile -Command "Get-Process | Where-Object { try { $_.MainModule.FileName -like ($env:DEST + '*') } catch { $false } } | Stop-Process -Force" >nul 2>nul

rem ---- copy the game folder ----
echo   Installing to %DEST% ...
xcopy "%~dp0app" "%DEST%\" /e /i /q /y >nul
if errorlevel 1 (
    echo   COPY FAILED - is the folder open in another program?
    if "%QUIET%"=="0" pause
    exit /b 1
)
if exist "%~dp0relay" xcopy "%~dp0relay" "%DEST%\relay\" /e /i /q /y >nul
for %%F in ("run-relay.bat" "host-server.bat" "host-server.ps1") do if exist "%~dp0%%~F" copy /y "%~dp0%%~F" "%DEST%\" >nul

rem ---- the relay answers if internet play is wanted ----
if not "%RLY%"=="" echo %RLY%>"%DEST%\relay.txt"

rem ---- shortcuts: desktop + start menu, the mk1 ship as the icon ----
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0make-shortcuts.ps1" -Dest "%DEST%" >nul 2>nul

if not "%QUIET%"=="1" (
    echo.
    echo   ============================================
    echo   INSTALLED.  "Vajra Overdrive" is on your
    echo   desktop and in the start menu.
    echo   Folder: %DEST%
    echo.
    echo   Flying with a friend: MULTIPLAYER -^> HOST A RUN.
    echo   On a LAN your room appears in their list; over the
    echo   internet the game opens its own door and prints one
    echo   code to send. vajra.txt says more.
    echo   ============================================
    pause
)
