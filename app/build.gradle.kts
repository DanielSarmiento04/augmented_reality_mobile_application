plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.augmented_mobile_application"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.augmented_mobile_application"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add ndk configuration
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    // Add JNI configuration
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets") // Ensure assets directory is included
        }
    }
    ndkVersion = "27.0.12077973"
    buildToolsVersion = "34.0.0"

    // Native development kit configuration - DISABLED temporarily for compilation
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }

    defaultConfig {        // NDK configuration - DISABLED temporarily
        // ndk {
        //     abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        // }
        
        // CMake arguments for optimization - DISABLED temporarily
        // externalNativeBuild {
        //     cmake {
        //         cppFlags += listOf("-std=c++17", "-O3", "-DANDROID")
        //         arguments += listOf(
        //             "-DANDROID_TOOLCHAIN=clang",
        //             "-DANDROID_STL=c++_shared"
        //         )
        //     }
        // }
    }
}

dependencies {
    // Androidx
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    // Coil
    implementation(libs.bundles.coil)

    // OpenCV
    implementation(libs.opencv) // Add this line

    // Augmented Reality
    implementation(libs.arsceneview)

    // Http Requests
    implementation(libs.bundles.retrofit)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)

    // CameraX
    implementation(libs.bundles.androidx.camera)

    // TensorFlow Lite
    implementation(libs.bundles.tensorflow.lite) // Add this line if you plan to use TensorFlow Lite as well
}