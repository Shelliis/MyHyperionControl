plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "shelli.com.myhyperioncontrol"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "shelli.com.myhyperioncontrol"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true   // R8 entfernt ungenutzten Code
            isShrinkResources = true   // entfernt ungenutzte Ressourcen (Strings, Drawables …)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        // Staging: schnelle Builds für die tägliche Entwicklung auf echten Geräten.
        // Kein R8, kein Shrinking → kurze Build-Zeit wie Debug,
        // aber mit Release-Signierung und nur arm64-v8a (kein Emulator-Overhead).
        create("staging") {
            initWith(getByName("debug"))          // Basis: Debug-Einstellungen
            isMinifyEnabled   = false
            isShrinkResources = false
            signingConfig     = signingConfigs.getByName("debug")
            ndk { abiFilters += listOf("arm64-v8a") }  // nur echte Geräte
        }
        debug {
            // Debug bleibt ohne Minifizierung (schnellere Builds, bessere Stack Traces)
            isMinifyEnabled = false
            // Emulator (x86_64) + moderne Android-Geräte (arm64-v8a)
            ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}