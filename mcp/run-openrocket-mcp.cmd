@echo off
setlocal

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"
set "GRADLEW=%REPO_ROOT%\gradlew.bat"
set "OPENROCKET_LOG_DIR=%USERPROFILE%\.openrocket"
set "OPENROCKET_ACTIVITY_LOG=%OPENROCKET_LOG_DIR%\ai-automation.log"

if not exist "%OPENROCKET_LOG_DIR%" mkdir "%OPENROCKET_LOG_DIR%"
where java >nul 2>nul
if errorlevel 1 (
  echo Java not found. Install JDK 17 and make sure JAVA_HOME or PATH is set. >&2
  exit /b 1
)

if not exist "%GRADLEW%" (
  echo Gradle wrapper not found at "%GRADLEW%". >&2
  exit /b 1
)

call "%GRADLEW%" -p "%REPO_ROOT%" :mcp:installDist -PskipExternalComponents=true --console=plain 1>&2
if errorlevel 1 exit /b 1

if not exist "%REPO_ROOT%\mcp\build\install\mcp\lib" (
  echo OpenRocket MCP distribution not found. >&2
  exit /b 1
)

java "-Duser.home=%USERPROFILE%" "-Dopenrocket.remote.activityLogPath=%OPENROCKET_ACTIVITY_LOG%" -cp "%REPO_ROOT%\mcp\build\install\mcp\lib\*" info.openrocket.mcp.OpenRocketMcpServer %*
exit /b %ERRORLEVEL%
