plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lifeos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifeos.app"
        minSdk = 26          // Android 8.0 — safe floor; the iQOO Z7 Pro is Android 15
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-m1"
    }

    buildTypes {
        release {
            // M1 is debug-only; leave release unshrunk so nothing surprising happens later.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose — pull the BOM first, then the libraries without versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit OCR + permission helper
    implementation(libs.mlkit.text.recognition)
    implementation(libs.accompanist.permissions)

    // On-device LLM (Gemma via MediaPipe)
    implementation(libs.mediapipe.tasks.genai)
}
