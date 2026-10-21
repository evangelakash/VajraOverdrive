@echo off
rem The one-click handbook: the whole multiplayer codebase into code_notes\multiplayer.txt
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0collect-multiplayer.ps1"
