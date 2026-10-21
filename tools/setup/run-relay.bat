@echo off
rem VAJRA OVERDRIVE - the relay box. Start it where a friend's room should live:
rem on YOUR machine for LAN play (no port forward), or a small public VPS for
rem play over the internet (open the UDP port in that box's firewall).
rem Uses the game's bundled Java when this sits beside it; else needs Java 17+.
rem Default port 27040; pass another as the first argument.
set "JR=%~dp0jre\bin\java.exe"
if not exist "%JR%" set "JR=java"
cd /d "%~dp0"
"%JR%" -cp "relay\lib\*" vajra.relay.RelayServer %1
pause
