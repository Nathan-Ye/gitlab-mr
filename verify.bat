@echo off
REM GitLab IDEA Plugin - Build Verification Script

echo ========================================
echo GitLab IDEA Plugin - Build Verification
echo ========================================
echo.

REM Check if Java is installed
echo [1/5] Checking Java installation...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install JDK 17 and add it to PATH
    pause
    exit /b 1
)
echo OK: Java is installed
echo.

REM Check Java version
echo [2/5] Checking Java version...
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%i
)
echo Java version: %JAVA_VERSION%
echo.

REM Clean previous builds
echo [3/5] Cleaning previous builds...
call gradlew.bat clean
if %errorlevel% neq 0 (
    echo WARNING: Clean failed, continuing anyway...
)
echo.

REM Build the plugin
echo [4/5] Building plugin...
echo This may take several minutes on first run...
call gradlew.bat buildPlugin
if %errorlevel% neq 0 (
    echo ERROR: Build failed!
    echo Please check the error messages above
    pause
    exit /b 1
)
echo.

REM Verify output
echo [5/5] Verifying output...
if exist "build\distributions\gitlab-idea-plugin-1.0.0.zip" (
    echo ========================================
    echo SUCCESS! Plugin built successfully!
    echo ========================================
    echo.
    echo Output: build\distributions\gitlab-idea-plugin-1.0.0.zip
    echo.
    echo To install in IDEA:
    echo   1. Go to File -^> Settings -^> Plugins
    echo   2. Click gear icon -^> Install Plugin from Disk...
    echo   3. Select the ZIP file above
    echo   4. Restart IDEA
    echo.
) else (
    echo ERROR: Output file not found!
    pause
    exit /b 1
)

pause
