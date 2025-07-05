#!/bin/bash

# =============================================================================
# Quick APK Generator for Augmented Reality Mobile Application
# =============================================================================
# This is a simplified script for quick APK generation
# =============================================================================

echo "🚀 Quick APK Generator for AR Mobile Application"
echo "================================================"

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ] || [ ! -d "app" ]; then
    echo "❌ Please run this script from the Android project root directory"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

echo "🧹 Cleaning project..."
./gradlew clean

echo "🔨 Building debug APK..."
./gradlew assembleDebug

# Check if build was successful
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ Debug APK built successfully!"
    
    # Create quick distribution
    mkdir -p quick_dist
    cp "app/build/outputs/apk/debug/app-debug.apk" "quick_dist/"
    
    # Get APK info
    APK_SIZE=$(ls -lh "quick_dist/app-debug.apk" | awk '{print $5}')
    
    echo ""
    echo "📱 APK Information:"
    echo "  File: quick_dist/app-debug.apk"
    echo "  Size: $APK_SIZE"
    echo ""
    echo "🎉 Your APK is ready for sharing!"
    echo "📁 Location: $(pwd)/quick_dist/app-debug.apk"
    echo ""
    echo "Next steps:"
    echo "1. Install on device: adb install -r quick_dist/app-debug.apk"
    echo "2. Share the APK file with testers"
    echo "3. For release builds, run: ./build_apk.sh --release-only"
    
else
    echo "❌ Build failed. Check the output above for errors."
    exit 1
fi
