@echo off
rem Builds NEXUS-<VERSION>.apk in the project folder. Extra arguments go to build_apk.ps1 (e.g. -SkipTests).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_apk.ps1" %*
pause
