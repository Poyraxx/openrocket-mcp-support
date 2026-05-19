@echo off
setlocal

set "LAUNCHER_PS1=%~dp0run-openrocket-live.ps1"

if not exist "%LAUNCHER_PS1%" (
  echo OpenRocket PowerShell launcher not found at "%LAUNCHER_PS1%".
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%LAUNCHER_PS1%" %*
exit /b %ERRORLEVEL%
