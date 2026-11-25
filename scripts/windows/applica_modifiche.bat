@echo off
rem Applies NEXUS-modifiche.zip from the development PC: drop the zip on this file, or double-click for the newest one in Downloads.
rem The zip can replace this file while it runs, and cmd reads a batch file line by line: keep everything on the next line.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0applica_modifiche.ps1" %* & pause & exit /b
