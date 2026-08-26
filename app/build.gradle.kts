plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chrisgrou.fbfeedwrapper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chrisgrou.fbfeedwrapper"
        minSdk = 26
        targetSdk = 34
        // CI sets GITHUB_RUN_NUMBER, giving every build a unique, increasing code the
        // in-app update checker can compare against; local builds fall back to 1.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        buildConfigField("String", "GITHUB_REPO", "\"chrisgrou/no-algo-fb\"")
    }

    signingConfigs {
        // Committed on purpose: this is a debug-only key (never used for a Play
        // Store release), fixed so every CI build is signed identically. Without
        // this, AGP falls back to ~/.android/debug.keystore, which CI regenerates
        // fresh on every run — a new signature each time means Android treats
        // the next APK as a different app and refuses to "update" over the last
        // one, forcing an uninstall before every install.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
