plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dropbridge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dropbridge.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ditandatangani kunci debug: cukup untuk dipakai sendiri lewat sideload
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Sengaja tanpa dependensi (tanpa AndroidX) agar kecil dan mudah dibangun.
dependencies {
}
