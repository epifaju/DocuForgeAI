@echo off
REM Opens DocuForge AI in the default browser (port from registry or 5174)
set PORT=5174
for /f "tokens=2*" %%A in ('reg query "HKLM\Software\DocuForgeAI" /v UiPort 2^>nul') do set PORT=%%B
start "" "http://localhost:%PORT%/"
