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
}

dependencies {

    // http request multiplatform ktor
    val activity_version = "1.9.1"

    //implementation("com.google.ar:core:1.44.0")
    implementation("androidx.core:core-splashscreen:1.0.0")
    implementation("androidx.activity:activity-ktx:$activity_version")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.material:material-icons-extended")

    // Androidx
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Augmented Reality
    implementation("io.github.sceneview:arsceneview:2.1.1")

//    implementation("com.google.ar:core:1.35.0")
//    implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.0")

    // Http Requests
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")

    // Coroutines and StateFlow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.2")

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
}