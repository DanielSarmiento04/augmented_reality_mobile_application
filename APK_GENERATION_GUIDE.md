# APK Generation Guide for Augmented Reality Mobile Application

## Overview

This guide provides multiple methods to generate APK files for sharing your augmented reality mobile application. The application is built with modern Android development practices using Kotlin, Jetpack Compose, and includes AR capabilities with native libraries.

## Prerequisites

Before generating APKs, ensure you have:

- ✅ **Android Studio** installed with Android SDK
- ✅ **Java JDK 11** or higher
- ✅ **Android SDK Build Tools** and platform tools
- ✅ **ANDROID_HOME** environment variable set
- ✅ **Git** (optional, for version tracking)

### Environment Setup

```bash
# macOS/Linux
export ANDROID_HOME=$HOME/Library/Android/sdk  # macOS
export ANDROID_HOME=$HOME/Android/Sdk          # Linux

# Windows
set ANDROID_HOME=%USERPROFILE%\AppData\Local\Android\Sdk
```

## Method 1: Automated Build Scripts (Recommended)

### For macOS/Linux Users

Use the comprehensive bash script:

```bash
# Make the script executable
chmod +x build_apk.sh

# Build all APKs (debug and release)
./build_apk.sh

# Build only debug APK
./build_apk.sh --debug-only

# Build only release APK
./build_apk.sh --release-only

# Skip tests during build
./build_apk.sh --skip-tests

# Skip clean build
./build_apk.sh --no-clean

# Show help
./build_apk.sh --help
```

### For Windows Users

Use the batch script:

```cmd
# Build all APKs
build_apk.bat

# Build only debug APK
build_apk.bat --debug-only

# Build only release APK
build_apk.bat --release-only

# Show help
build_apk.bat --help
```

## Method 2: Direct Gradle Commands

### Basic APK Generation

```bash
# Clean the project
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build all variants
./gradlew assemble
```

### Advanced Gradle Commands

```bash
# Build with detailed logging
./gradlew assembleDebug --info

# Build with stack traces for debugging
./gradlew assembleDebug --stacktrace

# Build and run tests
./gradlew build

# Build Android App Bundle (for Play Store)
./gradlew bundleRelease
```

## Method 3: Custom Gradle Tasks

Add the provided custom Gradle tasks to your `app/build.gradle.kts`:

```bash
# Build all APKs and organize in dist folder
./gradlew buildAllApks

# Build and install debug APK to connected device
./gradlew buildAndInstallDebug

# Build APK with timestamp in filename
./gradlew buildTimestampedApk

# Show APK information
./gradlew showApkInfo

# Clean build for distribution
./gradlew cleanBuildForDistribution
```

## APK Output Locations

After building, APKs will be located at:

```
app/build/outputs/apk/
├── debug/
│   └── app-debug.apk
└── release/
    ├── app-release.apk (signed)
    └── app-release-unsigned.apk (unsigned)
```

When using automated scripts, APKs are also copied to:
```
dist/
├── app-debug.apk
├── app-release.apk
├── build_info.txt
└── mapping.txt (if ProGuard is enabled)
```

## APK Types Explained

### Debug APK
- **Purpose**: Internal testing and development
- **Signing**: Signed with debug keystore
- **Optimization**: No code obfuscation or optimization
- **Size**: Larger due to debug symbols
- **Installation**: Can be installed alongside other debug builds

### Release APK
- **Purpose**: Production distribution
- **Signing**: Requires release keystore (or unsigned)
- **Optimization**: Code obfuscation and optimization enabled
- **Size**: Smaller due to optimizations
- **Installation**: Cannot coexist with debug builds

## Signing Configuration

### For Release Builds

Add signing configuration to `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("path/to/your/keystore.jks")
            storePassword = "your_store_password"
            keyAlias = "your_key_alias"
            keyPassword = "your_key_password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... other release config
        }
    }
}
```

### Create a Release Keystore

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
```

## Sharing APKs

### For Internal Testing

1. **Direct File Sharing**: Share the debug APK file directly
2. **Cloud Storage**: Upload to Google Drive, Dropbox, etc.
3. **Email**: Send as attachment (check size limits)
4. **USB Transfer**: Copy directly to test devices

### For External Testing

1. **Google Play Console**: Upload signed APK or AAB
2. **Firebase App Distribution**: Automated testing distribution
3. **TestFlight**: For iOS versions
4. **Custom Distribution**: Host on your own server

## Testing Generated APKs

### Device Installation

```bash
# Install debug APK
adb install -r app-debug.apk

# Install release APK
adb install -r app-release.apk

# Install over existing version
adb install -r -d app-debug.apk
```

### Verification Steps

1. ✅ Install on multiple devices/Android versions
2. ✅ Test core AR functionality
3. ✅ Verify camera permissions
4. ✅ Test native library loading
5. ✅ Check app signing and permissions
6. ✅ Performance testing on different hardware

## Troubleshooting

### Common Issues

**Build Fails with "SDK not found"**
```bash
# Check ANDROID_HOME is set
echo $ANDROID_HOME  # macOS/Linux
echo %ANDROID_HOME%  # Windows

# Update local.properties
echo "sdk.dir=/path/to/android/sdk" >> local.properties
```

**Out of Memory Error**
```bash
# Increase heap size in gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

**Native Library Issues**
- Ensure all ABI variants are included in `ndk.abiFilters`
- Check that native libraries are in `src/main/jniLibs`
- Verify NDK version compatibility

**Signing Issues**
- Verify keystore path and credentials
- Check that release signing config is properly applied
- Ensure keystore file permissions are correct

### Debug Commands

```bash
# Check APK contents
aapt dump badging app-debug.apk

# List APK files
unzip -l app-debug.apk

# Check APK signing
jarsigner -verify -verbose -certs app-release.apk

# Check native libraries
aapt dump --include-meta-data app-debug.apk | grep lib/
```

## Performance Optimization

### APK Size Reduction

1. **Enable ProGuard/R8** for release builds
2. **Use APK Analyzer** in Android Studio
3. **Remove unused resources** with resource shrinking
4. **Optimize images** and use vector drawables
5. **Split APKs by ABI** if needed

### Build Performance

1. **Enable Gradle daemon**: `--daemon`
2. **Use build cache**: `--build-cache`
3. **Parallel builds**: `--parallel`
4. **Configure heap size** in `gradle.properties`

## Security Considerations

1. **Never commit keystores** to version control
2. **Use environment variables** for sensitive data
3. **Enable ProGuard** for release builds
4. **Validate APK signing** before distribution
5. **Use secure distribution channels**

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build APK
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Setup JDK
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
    - name: Build APK
      run: ./gradlew assembleDebug
    - name: Upload APK
      uses: actions/upload-artifact@v2
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

## Support

For issues related to APK generation:

1. Check the build logs for specific error messages
2. Verify all prerequisites are met
3. Ensure the project builds successfully in Android Studio
4. Check that all native dependencies are properly configured
5. Review the AR-specific requirements and permissions

## Additional Resources

- [Android Developer Guide - Build and Run Your App](https://developer.android.com/studio/run)
- [Signing Your Applications](https://developer.android.com/studio/publish/app-signing)
- [Shrink, obfuscate, and optimize your app](https://developer.android.com/studio/build/shrink-code)
- [AR Core Developer Guide](https://developers.google.com/ar)

---

**Note**: This project includes augmented reality features and native libraries. Ensure test devices support AR capabilities and have the required hardware sensors.
