@echo off
setlocal

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"
set "GRADLEW=%REPO_ROOT%\gradlew.bat"
set "MCP_HOME=%REPO_ROOT%\mcp\build\install\mcp\bin"
set "OPENROCKET_LOG_DIR=%USERPROFILE%\.openrocket"
set "OPENROCKET_ACTIVITY_LOG=%OPENROCKET_LOG_DIR%\ai-automation.log"

if not exist "%OPENROCKET_LOG_DIR%" mkdir "%OPENROCKET_LOG_DIR%"
if not defined JAVA_TOOL_OPTIONS (
  set "JAVA_TOOL_OPTIONS=-Dopenrocket.remote.activityLogPath=%OPENROCKET_ACTIVITY_LOG%"
) else (
  set "JAVA_TOOL_OPTIONS=-Dopenrocket.remote.activityLogPath=%OPENROCKET_ACTIVITY_LOG% %JAVA_TOOL_OPTIONS%"
)

where java >nul 2>nul
if errorlevel 1 (
  echo Java not found. Install Java 17+ and make sure JAVA_HOME or PATH is set.
  exit /b 1
)

if exist "%GRADLEW%" (
  call "%GRADLEW%" :mcp:installDist -PskipExternalComponents=true
  if errorlevel 1 exit /b %ERRORLEVEL%
)

if not exist "%MCP_HOME%\mcp.bat" (
  echo OpenRocket MCP launcher not found at "%MCP_HOME%\mcp.bat".
  exit /b 1
)

call "%MCP_HOME%\mcp.bat"
