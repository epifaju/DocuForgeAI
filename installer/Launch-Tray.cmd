@echo off
REM Prefer native tray EXE when published; otherwise PowerShell tray (no console window).
set "DIR=%~dp0"
if exist "%DIR%DocuForge.Tray.exe" (
  start "" "%DIR%DocuForge.Tray.exe"
  exit /b 0
)
start "" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%DIR%scripts\DocuForge.Tray.ps1"
