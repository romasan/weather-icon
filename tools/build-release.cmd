@echo off
setlocal

for %%I in ("%~dp0..") do set "PROJECT_DIR=%%~fI"
set "TOOLS_DIR=%PROJECT_DIR%\..\..\work\tools"

if not defined JAVA_HOME (
    for /d %%J in ("%TOOLS_DIR%\jdk\*") do if exist "%%~fJ\bin\java.exe" set "JAVA_HOME=%%~fJ"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

set "LOCAL_GRADLE=%TOOLS_DIR%\gradle-8.11.1\bin\gradle.bat"
pushd "%PROJECT_DIR%"
if exist "%LOCAL_GRADLE%" (
    call "%LOCAL_GRADLE%" :app:assembleRelease
) else (
    where gradle >nul 2>nul
    if errorlevel 1 (
        echo Gradle 8.11.1 was not found. Install Gradle or place it in work\tools beside the project.
        popd
        exit /b 1
    )
    call gradle :app:assembleRelease
)
set "BUILD_RESULT=%ERRORLEVEL%"
popd
if not "%BUILD_RESULT%"=="0" exit /b %BUILD_RESULT%

echo Release APK: %PROJECT_DIR%\app\build\outputs\apk\release\app-release-unsigned.apk
