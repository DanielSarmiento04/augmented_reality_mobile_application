// =============================================================================
// Additional Gradle Tasks for APK Generation and Distribution
// =============================================================================
// Add these tasks to your app/build.gradle.kts file

// Task to build all APK variants and organize them
task("buildAllApks") {
    group = "distribution"
    description = "Builds all APK variants and organizes them in a distribution folder"
    
    dependsOn("assembleDebug", "assembleRelease")
    
    doLast {
        val distDir = file("$rootDir/dist")
        distDir.deleteRecursively()
        distDir.mkdirs()
        
        // Copy debug APK
        val debugApk = file("$buildDir/outputs/apk/debug/app-debug.apk")
        if (debugApk.exists()) {
            debugApk.copyTo(file("$distDir/app-debug.apk"), overwrite = true)
            println("✓ Debug APK copied to dist/")
        }
        
        // Copy release APK
        val releaseApk = file("$buildDir/outputs/apk/release/app-release.apk")
        val releaseApkUnsigned = file("$buildDir/outputs/apk/release/app-release-unsigned.apk")
        
        when {
            releaseApk.exists() -> {
                releaseApk.copyTo(file("$distDir/app-release.apk"), overwrite = true)
                println("✓ Signed release APK copied to dist/")
            }
            releaseApkUnsigned.exists() -> {
                releaseApkUnsigned.copyTo(file("$distDir/app-release-unsigned.apk"), overwrite = true)
                println("✓ Unsigned release APK copied to dist/")
            }
        }
        
        // Copy mapping files if they exist
        val mappingFile = file("$buildDir/outputs/mapping/release/mapping.txt")
        if (mappingFile.exists()) {
            mappingFile.copyTo(file("$distDir/mapping.txt"), overwrite = true)
            println("✓ ProGuard mapping file copied to dist/")
        }
        
        // Generate build info
        val buildInfo = file("$distDir/build_info.txt")
        buildInfo.writeText("""
            Build Information
            ================
            Project: ${project.name}
            Application ID: ${android.defaultConfig.applicationId}
            Version Name: ${android.defaultConfig.versionName}
            Version Code: ${android.defaultConfig.versionCode}
            Build Date: ${java.time.LocalDateTime.now()}
            Gradle Version: ${gradle.gradleVersion}
            
            Target SDK: ${android.defaultConfig.targetSdk}
            Min SDK: ${android.defaultConfig.minSdk}
            Compile SDK: ${android.compileSdk}
            
            APK Files Generated:
            ${distDir.listFiles { file -> file.extension == "apk" }?.joinToString("\n") { "- ${it.name} (${it.length()} bytes)" } ?: "No APK files found"}
        """.trimIndent())
        
        println("\n🎉 APK generation complete!")
        println("📁 Distribution files are available in: $distDir")
        println("📱 APKs are ready for sharing and testing")
    }
}

// Task to build and install debug APK to connected device
task("buildAndInstallDebug") {
    group = "distribution"
    description = "Builds debug APK and installs it to connected device"
    
    dependsOn("assembleDebug")
    
    doLast {
        exec {
            commandLine("adb", "install", "-r", "$buildDir/outputs/apk/debug/app-debug.apk")
        }
        println("✓ Debug APK installed to connected device")
    }
}

// Task to generate APK with build timestamp in filename
task("buildTimestampedApk") {
    group = "distribution"
    description = "Builds APK with timestamp in filename for version tracking"
    
    dependsOn("assembleDebug")
    
    doLast {
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        
        val distDir = file("$rootDir/dist")
        distDir.mkdirs()
        
        val debugApk = file("$buildDir/outputs/apk/debug/app-debug.apk")
        if (debugApk.exists()) {
            val timestampedName = "AR-App-${android.defaultConfig.versionName}-$timestamp-debug.apk"
            debugApk.copyTo(file("$distDir/$timestampedName"), overwrite = true)
            println("✓ Timestamped APK created: $timestampedName")
        }
    }
}

// Task to show APK information
task("showApkInfo") {
    group = "distribution"
    description = "Shows information about generated APKs"
    
    doLast {
        val apkDir = file("$buildDir/outputs/apk")
        if (apkDir.exists()) {
            println("\n📱 APK Information:")
            println("==================")
            
            apkDir.walkTopDown()
                .filter { it.extension == "apk" }
                .forEach { apk ->
                    println("APK: ${apk.name}")
                    println("Path: ${apk.relativeTo(rootDir)}")
                    println("Size: ${apk.length()} bytes (${apk.length() / 1024 / 1024} MB)")
                    println("Last Modified: ${java.time.Instant.ofEpochMilli(apk.lastModified())}")
                    println("---")
                }
        } else {
            println("No APKs found. Run 'assembleDebug' or 'assembleRelease' first.")
        }
    }
}

// Task to clean and build for distribution
task("cleanBuildForDistribution") {
    group = "distribution"
    description = "Performs a clean build and generates distribution-ready APKs"
    
    dependsOn("clean", "buildAllApks")
    
    doLast {
        println("\n🚀 Clean build for distribution completed!")
        println("Your APKs are ready for sharing in the 'dist' directory")
    }
}
