plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.guoyujie666.fakelauncher"
    compileSdk = 35
    buildFeatures {
        compose = true
    }
    defaultConfig {
        applicationId = "com.github.guoyujie666.fakelauncher"
        minSdk = 30
        targetSdk = 34
        versionCode = 25122022
        versionName = "v1.0.1"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        getByName("release") {
            versionNameSuffix = "release"
            isShrinkResources = true
            isMinifyEnabled = true
        }
        getByName("debug") {
            versionNameSuffix = "debug"
        }
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // Wear OS Material 3
    implementation("androidx.wear.compose:compose-material3:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")

    // Icons
    implementation("androidx.compose.material:material-icons-extended-android:1.7.8")

    // Compose Core
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material3:material3:1.4.0")

    // Lifecycle & SavedState (Explicit versions to ensure ViewTree owners are present)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Wear Services & Tooling
    implementation(libs.play.services.wearable)
    implementation(libs.wear.tooling.preview)
    implementation(libs.core.splashscreen)

    // Tiles & Horologist
    implementation(libs.tiles)
    implementation(libs.tiles.material)
    implementation(libs.horologist.compose.tools)
    implementation(libs.horologist.tiles)

    // Debug & Test
    debugImplementation("androidx.wear.compose:compose-ui-tooling:1.5.6")
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.tiles.tooling)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
}
