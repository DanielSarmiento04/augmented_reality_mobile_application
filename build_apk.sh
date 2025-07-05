#!/bin/bash

# =============================================================================
# Android APK Build Script for Augmented Reality Mobile Application
# =============================================================================
# This script builds debug and release APKs for sharing the application
# Author: Senior Android Developer
# Date: $(date)
# =============================================================================

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project configuration
PROJECT_NAME="Augmented Reality Mobile Application"
APP_MODULE="app"
OUTPUT_DIR="build/outputs"
DIST_DIR="dist"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "\n${BLUE}==============================================================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}==============================================================================${NC}\n"
}

# Function to check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if we're in the correct directory
    if [ ! -f "build.gradle.kts" ] || [ ! -d "app" ]; then
        print_error "Please run this script from the root directory of your Android project"
        exit 1
    fi
    
    # Check if gradlew exists and is executable
    if [ ! -f "./gradlew" ]; then
        print_error "gradlew not found. Make sure you're in the Android project root directory."
        exit 1
    fi
    
    # Make gradlew executable if it isn't
    chmod +x ./gradlew
    
    # Check Java/JDK
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    
    # Check Android SDK
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        print_warning "ANDROID_HOME or ANDROID_SDK_ROOT not set. Trying to detect..."
        # Try common locations
        if [ -d "$HOME/Library/Android/sdk" ]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
            print_status "Found Android SDK at: $ANDROID_HOME"
        elif [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
            print_status "Found Android SDK at: $ANDROID_HOME"
        else
            print_error "Android SDK not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT"
            exit 1
        fi
    fi
    
    print_success "All prerequisites met"
}

# Function to clean the project
clean_project() {
    print_header "Cleaning Project"
    
    print_status "Running clean task..."
    ./gradlew clean
    
    # Remove previous dist directory
    if [ -d "$DIST_DIR" ]; then
        rm -rf "$DIST_DIR"
        print_status "Removed previous distribution directory"
    fi
    
    print_success "Project cleaned successfully"
}

# Function to build debug APK
build_debug_apk() {
    print_header "Building Debug APK"
    
    print_status "Building debug APK with full logging..."
    ./gradlew assembleDebug --info
    
    # Check if debug APK was created
    DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$DEBUG_APK" ]; then
        print_success "Debug APK built successfully: $DEBUG_APK"
        return 0
    else
        print_error "Debug APK build failed"
        return 1
    fi
}

# Function to build release APK
build_release_apk() {
    print_header "Building Release APK"
    
    print_status "Building release APK..."
    
    # Check if signing config exists
    if grep -q "signingConfigs" app/build.gradle.kts; then
        print_status "Signing configuration found, building signed release APK..."
        ./gradlew assembleRelease --info
    else
        print_warning "No signing configuration found, building unsigned release APK..."
        print_warning "You'll need to manually sign the APK before distribution"
        ./gradlew assembleRelease --info
    fi
    
    # Check if release APK was created
    RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
    RELEASE_APK_UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
    
    if [ -f "$RELEASE_APK" ]; then
        print_success "Release APK built successfully: $RELEASE_APK"
        return 0
    elif [ -f "$RELEASE_APK_UNSIGNED" ]; then
        print_success "Unsigned release APK built successfully: $RELEASE_APK_UNSIGNED"
        return 0
    else
        print_error "Release APK build failed"
        return 1
    fi
}

# Function to run tests
run_tests() {
    print_header "Running Tests"
    
    print_status "Running unit tests..."
    ./gradlew test || {
        print_warning "Some unit tests failed, but continuing with APK generation"
    }
    
    print_status "Running lint checks..."
    ./gradlew lint || {
        print_warning "Lint checks found issues, but continuing with APK generation"
    }
}

# Function to organize output files
organize_outputs() {
    print_header "Organizing Output Files"
    
    # Create distribution directory
    mkdir -p "$DIST_DIR"
    
    # Copy APK files
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        cp "app/build/outputs/apk/debug/app-debug.apk" "$DIST_DIR/"
        print_status "Debug APK copied to $DIST_DIR/"
    fi
    
    if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        cp "app/build/outputs/apk/release/app-release.apk" "$DIST_DIR/"
        print_status "Release APK copied to $DIST_DIR/"
    elif [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
        cp "app/build/outputs/apk/release/app-release-unsigned.apk" "$DIST_DIR/"
        print_status "Unsigned release APK copied to $DIST_DIR/"
    fi
    
    # Copy mapping files if they exist (for release builds with proguard)
    if [ -f "app/build/outputs/mapping/release/mapping.txt" ]; then
        cp "app/build/outputs/mapping/release/mapping.txt" "$DIST_DIR/"
        print_status "ProGuard mapping file copied to $DIST_DIR/"
    fi
    
    # Generate build info
    cat > "$DIST_DIR/build_info.txt" << EOF
Build Information
================
Project: $PROJECT_NAME
Build Date: $(date)
Git Commit: $(git rev-parse --short HEAD 2>/dev/null || echo "N/A")
Git Branch: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "N/A")
Gradle Version: $(./gradlew --version | grep "Gradle" | head -1)
Android Gradle Plugin: $(grep "android.application" gradle/libs.versions.toml | cut -d'"' -f4 2>/dev/null || echo "N/A")

APK Files:
$(ls -la $DIST_DIR/*.apk 2>/dev/null || echo "No APK files found")
EOF
    
    print_success "Build artifacts organized in $DIST_DIR/"
}

# Function to display APK information
display_apk_info() {
    print_header "APK Information"
    
    for apk in "$DIST_DIR"/*.apk; do
        if [ -f "$apk" ]; then
            filename=$(basename "$apk")
            filesize=$(ls -lh "$apk" | awk '{print $5}')
            print_status "APK: $filename"
            print_status "Size: $filesize"
            
            # Try to get APK info using aapt if available
            if command -v aapt &> /dev/null; then
                echo "Package info:"
                aapt dump badging "$apk" | grep -E "(package|application-label|sdkVersion|targetSdkVersion)" || true
            elif [ -n "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/build-tools/*/aapt" ]; then
                AAPT=$(find "$ANDROID_HOME/build-tools" -name "aapt" | head -1)
                if [ -n "$AAPT" ]; then
                    echo "Package info:"
                    "$AAPT" dump badging "$apk" | grep -E "(package|application-label|sdkVersion|targetSdkVersion)" || true
                fi
            fi
            echo ""
        fi
    done
}

# Function to generate QR code for easy sharing (if qrencode is available)
generate_qr_codes() {
    if command -v qrencode &> /dev/null; then
        print_header "Generating QR Codes for APK Sharing"
        
        for apk in "$DIST_DIR"/*.apk; do
            if [ -f "$apk" ]; then
                filename=$(basename "$apk" .apk)
                # This would typically point to where you host the APK
                qr_text="APK: $filename - Size: $(ls -lh "$apk" | awk '{print $5}')"
                qrencode -t PNG -o "$DIST_DIR/${filename}_qr.png" "$qr_text"
                print_status "QR code generated: ${filename}_qr.png"
            fi
        done
    else
        print_status "qrencode not found. Install it with: brew install qrencode (for QR code generation)"
    fi
}

# Main execution function
main() {
    print_header "Starting APK Build Process for $PROJECT_NAME"
    
    # Parse command line arguments
    BUILD_TYPE="all"
    RUN_TESTS=true
    CLEAN_BUILD=true
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --debug-only)
                BUILD_TYPE="debug"
                shift
                ;;
            --release-only)
                BUILD_TYPE="release"
                shift
                ;;
            --skip-tests)
                RUN_TESTS=false
                shift
                ;;
            --no-clean)
                CLEAN_BUILD=false
                shift
                ;;
            --help)
                echo "Usage: $0 [OPTIONS]"
                echo "Options:"
                echo "  --debug-only    Build only debug APK"
                echo "  --release-only  Build only release APK"
                echo "  --skip-tests    Skip running tests"
                echo "  --no-clean      Skip clean build"
                echo "  --help          Show this help message"
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Execute build steps
    check_prerequisites
    
    if [ "$CLEAN_BUILD" = true ]; then
        clean_project
    fi
    
    if [ "$RUN_TESTS" = true ]; then
        run_tests
    fi
    
    # Build APKs based on build type
    case $BUILD_TYPE in
        "debug")
            build_debug_apk
            ;;
        "release")
            build_release_apk
            ;;
        "all"|*)
            build_debug_apk
            build_release_apk
            ;;
    esac
    
    organize_outputs
    display_apk_info
    generate_qr_codes
    
    print_header "Build Process Complete!"
    print_success "APKs are ready for sharing in the '$DIST_DIR' directory"
    print_status "You can now share the APK files with testers or upload to distribution platforms"
    
    # Suggest next steps
    echo -e "\n${YELLOW}Next Steps:${NC}"
    echo "1. Test the APKs on different devices"
    echo "2. Share debug APK for internal testing"
    echo "3. Sign and align release APK for distribution"
    echo "4. Upload to Google Play Console or other distribution platforms"
    echo "5. Generate signed AAB (Android App Bundle) for Play Store: ./gradlew bundleRelease"
}

# Run the main function with all arguments
main "$@"
