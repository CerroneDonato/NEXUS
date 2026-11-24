@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0riavvia_nexus.ps1"
if errorlevel 1 pause
