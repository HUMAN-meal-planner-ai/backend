@echo off
setlocal

rem Always run from this batch file's directory, even when opened by double-click.
cd /d "%~dp0"

title MealFit Backend Server
echo ========================================
echo   MealFit Backend Server
echo ========================================
echo.

rem The backend imports Supabase connection values from the local .env file.
if not exist ".env" (
    echo [ERROR] backend\.env was not found.
    echo Create the file and configure DB_URL, DB_USERNAME, and DB_PASSWORD.
    pause
    exit /b 1
)

rem Prevent a second backend process from attempting to use the same port.
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [INFO] Port 8080 is already in use.
    echo The backend may already be running: http://localhost:8080/api/health
    pause
    exit /b 0
)

rem Use the repository's Gradle Wrapper so no separate Gradle installation is required.
if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat was not found.
    pause
    exit /b 1
)

echo [INFO] Starting Spring Boot on http://localhost:8080
echo [INFO] Health check: http://localhost:8080/api/health
echo [INFO] Press Ctrl+C to stop the server.
echo.

call gradlew.bat bootRun
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" echo [ERROR] Backend stopped with exit code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%

