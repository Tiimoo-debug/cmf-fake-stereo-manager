/**
 * Version derived from the commit count, so every build is distinguishable
 * without anyone remembering to bump a number - the first three builds all
 * shipped as versionCode 1 precisely because that relied on memory.
 *
 * Needs full history: CI must check out with fetch-depth: 0, or this falls
 * back to 1.
 */
fun gitCommitCount(): Int = try {
    val proc = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    proc.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
} catch (e: Exception) {
    1
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tiimoo.cmfstereo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tiimoo.cmfstereo"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount()
        versionName = "1.0.${gitCommitCount()}"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so CI can produce an installable APK without a
            // secret key. Replace with a real signing config before shipping
            // anything you expect to update in place.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
