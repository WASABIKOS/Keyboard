plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wasabikos.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wasabikos.keyboard"
        minSdk = 24
        targetSdk = 35
        // Cada build de CI sube la versión para que Android acepte la actualización
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    // Clave fija (secretos de GitHub) para que cada APK actualice al anterior sin desinstalar
    val keystore = rootProject.file("release.jks")
    signingConfigs {
        create("release") {
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "wasabi"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore.exists()) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
