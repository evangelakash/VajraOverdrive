@echo off
rem VAJRA OVERDRIVE - launcher. Prefers the Java runtime bundled right here
rem (jre\), so the machine needs no Java of its own; falls back to PATH java.
rem The working directory is the install folder: the game reads relay.txt from
rem it and looks for the bundled relay (relay\lib) beside it.
set "DIR=%~dp0"
cd /d "%DIR%"
if exist "%DIR%jre\bin\javaw.exe" (
    start "" "%DIR%jre\bin\javaw.exe" -cp "%DIR%lib\*" vajra.desktop.DesktopLauncher
) else (
    set "JR=java"
    if exist "%DIR%jre\bin\java.exe" set "JR=%DIR%jre\bin\java.exe"
    "%JR%" -cp "lib\*" vajra.desktop.DesktopLauncher
)
