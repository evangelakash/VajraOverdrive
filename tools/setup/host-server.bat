@echo off
title Vajra Overdrive - Host Server
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0host-server.ps1" %*
