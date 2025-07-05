@echo off
REM =============================================================================
REM Android APK Build Script for Augmented Reality Mobile Application (Windows)
REM =============================================================================
REM This script builds debug and release APKs for sharing the application
REM Author: Senior Android Developer
REM Date: %DATE%
REM =============================================================================

setlocal enabledelayedexpansion

REM Project configuration
set PROJECT_NAME=Augmented Reality Mobile Application
set APP_MODULE=app
set OUTPUT_DIR=build\outputs
set DIST_DIR=dist

REM Function to print status messages
:print_status
echo [INFO] %~1
goto :eof

:print_success
echo [SUCCESS] %~1
goto :eof

:print_warning
echo [WARNING] %~1
goto :eof

:print_error
echo [ERROR] %~1
goto :eof

:print_header
echo.
echo ==============================================================================
echo  %~1
echo ==============================================================================
echo.
goto :eof

REM Function to check prerequisites
:check_prerequisites
call :print_header "Checking Prerequisites"

REM Check if we're in the correct directory
if not exist "build.gradle.kts" (
    call :print_error "Please run this script from the root directory of your Android project"
    exit /b 1
)

if not exist "app" (
    call :print_error "App directory not found. Please run from the Android project root directory."
    exit /b 1
)

REM Check if gradlew exists
if not exist "gradlew.bat" (
    call :print_error "gradlew.bat not found. Make sure you're in the Android project root directory."
    exit /b 1
)

REM Check Java/JDK
java -version >nul 2>&1
if errorlevel 1 (
    call :print_error "Java is not installed or not in PATH"
    exit /b 1
)

REM Check Android SDK
if "%ANDROID_HOME%"=="" (
    if "%ANDROID_SDK_ROOT%"=="" (
        call :print_warning "ANDROID_HOME or ANDROID_SDK_ROOT not set. Trying to detect..."
        if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
            set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
            call :print_status "Found Android SDK at: !ANDROID_HOME!"
        ) else (
            call :print_error "Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT"
            exit /b 1
        )
    )
)

call :print_success "All prerequisites met"
goto :eof

REM Function to clean the project
:clean_project
call :print_header "Cleaning Project"

call :print_status "Running clean task..."
gradlew.bat clean
if errorlevel 1 (
    call :print_error "Clean task failed"
    exit /b 1
)

REM Remove previous dist directory
if exist "%DIST_DIR%" (
    rmdir /s /q "%DIST_DIR%"
    call :print_status "Removed previous distribution directory"
)

call :print_success "Project cleaned successfully"
goto :eof

REM Function to build debug APK
:build_debug_apk
call :print_header "Building Debug APK"

call :print_status "Building debug APK with full logging..."
gradlew.bat assembleDebug --info
if errorlevel 1 (
    call :print_error "Debug APK build failed"
    exit /b 1
)

REM Check if debug APK was created
set DEBUG_APK=app\build\outputs\apk\debug\app-debug.apk
if exist "%DEBUG_APK%" (
    call :print_success "Debug APK built successfully: %DEBUG_APK%"
) else (
    call :print_error "Debug APK build failed"
    exit /b 1
)
goto :eof

REM Function to build release APK
:build_release_apk
call :print_header "Building Release APK"

call :print_status "Building release APK..."

REM Check if signing config exists
findstr /c:"signingConfigs" app\build.gradle.kts >nul 2>&1
if %errorlevel%==0 (
    call :print_status "Signing configuration found, building signed release APK..."
    gradlew.bat assembleRelease --info
) else (
    call :print_warning "No signing configuration found, building unsigned release APK..."
    call :print_warning "You'll need to manually sign the APK before distribution"
    gradlew.bat assembleRelease --info
)

if errorlevel 1 (
    call :print_error "Release APK build failed"
    exit /b 1
)

REM Check if release APK was created
set RELEASE_APK=app\build\outputs\apk\release\app-release.apk
set RELEASE_APK_UNSIGNED=app\build\outputs\apk\release\app-release-unsigned.apk

if exist "%RELEASE_APK%" (
    call :print_success "Release APK built successfully: %RELEASE_APK%"
) else if exist "%RELEASE_APK_UNSIGNED%" (
    call :print_success "Unsigned release APK built successfully: %RELEASE_APK_UNSIGNED%"
) else (
    call :print_error "Release APK build failed"
    exit /b 1
)
goto :eof

REM Function to run tests
:run_tests
call :print_header "Running Tests"

call :print_status "Running unit tests..."
gradlew.bat test
if errorlevel 1 (
    call :print_warning "Some unit tests failed, but continuing with APK generation"
)

call :print_status "Running lint checks..."
gradlew.bat lint
if errorlevel 1 (
    call :print_warning "Lint checks found issues, but continuing with APK generation"
)
goto :eof

REM Function to organize output files
:organize_outputs
call :print_header "Organizing Output Files"

REM Create distribution directory
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"

REM Copy APK files
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy "app\build\outputs\apk\debug\app-debug.apk" "%DIST_DIR%\"
    call :print_status "Debug APK copied to %DIST_DIR%\"
)

if exist "app\build\outputs\apk\release\app-release.apk" (
    copy "app\build\outputs\apk\release\app-release.apk" "%DIST_DIR%\"
    call :print_status "Release APK copied to %DIST_DIR%\"
) else if exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    copy "app\build\outputs\apk\release\app-release-unsigned.apk" "%DIST_DIR%\"
    call :print_status "Unsigned release APK copied to %DIST_DIR%\"
)

REM Copy mapping files if they exist
if exist "app\build\outputs\mapping\release\mapping.txt" (
    copy "app\build\outputs\mapping\release\mapping.txt" "%DIST_DIR%\"
    call :print_status "ProGuard mapping file copied to %DIST_DIR%\"
)

REM Generate build info
(
echo Build Information
echo ================
echo Project: %PROJECT_NAME%
echo Build Date: %DATE% %TIME%
echo.
echo APK Files:
) > "%DIST_DIR%\build_info.txt"

dir "%DIST_DIR%\*.apk" /b >> "%DIST_DIR%\build_info.txt" 2>nul

call :print_success "Build artifacts organized in %DIST_DIR%\"
goto :eof

REM Function to display APK information
:display_apk_info
call :print_header "APK Information"

for %%f in ("%DIST_DIR%\*.apk") do (
    if exist "%%f" (
        call :print_status "APK: %%~nxf"
        for %%a in ("%%f") do call :print_status "Size: %%~za bytes"
        echo.
    )
)
goto :eof

REM Main execution
:main
call :print_header "Starting APK Build Process for %PROJECT_NAME%"

REM Parse command line arguments
set BUILD_TYPE=all
set RUN_TESTS=true
set CLEAN_BUILD=true

:parse_args
if "%~1"=="--debug-only" (
    set BUILD_TYPE=debug
    shift
    goto parse_args
)
if "%~1"=="--release-only" (
    set BUILD_TYPE=release
    shift
    goto parse_args
)
if "%~1"=="--skip-tests" (
    set RUN_TESTS=false
    shift
    goto parse_args
)
if "%~1"=="--no-clean" (
    set CLEAN_BUILD=false
    shift
    goto parse_args
)
if "%~1"=="--help" (
    echo Usage: %0 [OPTIONS]
    echo Options:
    echo   --debug-only    Build only debug APK
    echo   --release-only  Build only release APK
    echo   --skip-tests    Skip running tests
    echo   --no-clean      Skip clean build
    echo   --help          Show this help message
    exit /b 0
)
if "%~1"=="" goto start_build
call :print_error "Unknown option: %~1"
exit /b 1

:start_build
REM Execute build steps
call :check_prerequisites
if errorlevel 1 exit /b 1

if "%CLEAN_BUILD%"=="true" (
    call :clean_project
    if errorlevel 1 exit /b 1
)

if "%RUN_TESTS%"=="true" (
    call :run_tests
)

REM Build APKs based on build type
if "%BUILD_TYPE%"=="debug" (
    call :build_debug_apk
    if errorlevel 1 exit /b 1
) else if "%BUILD_TYPE%"=="release" (
    call :build_release_apk
    if errorlevel 1 exit /b 1
) else (
    call :build_debug_apk
    if errorlevel 1 exit /b 1
    call :build_release_apk
    if errorlevel 1 exit /b 1
)

call :organize_outputs
call :display_apk_info

call :print_header "Build Process Complete!"
call :print_success "APKs are ready for sharing in the '%DIST_DIR%' directory"
call :print_status "You can now share the APK files with testers or upload to distribution platforms"

echo.
echo Next Steps:
echo 1. Test the APKs on different devices
echo 2. Share debug APK for internal testing
echo 3. Sign and align release APK for distribution
echo 4. Upload to Google Play Console or other distribution platforms
echo 5. Generate signed AAB (Android App Bundle) for Play Store: gradlew bundleRelease

goto :eof

REM Call main function
call :main %*
