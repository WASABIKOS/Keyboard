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

    // Clave fija versionada en el repo (decisión del dueño) para que cada APK actualice al anterior
    val keystore = rootProject.file("release.jks")
    val keystorePassword = rootProject.file("signing.properties").takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.startsWith("KEYSTORE_PASSWORD=") }?.substringAfter("=")
    signingConfigs {
        create("release") {
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "wasabi"
                keyPassword = keystorePassword
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
