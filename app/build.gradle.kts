plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Permanent signing key comes from GitHub Secrets (see workflow).
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val hasReleaseKey = keystorePath != null && keystorePassword != null && file(keystorePath).exists()

android {
    namespace = "lk.mqtt.monitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "lk.mqtt.monitor"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "mqttmonitor"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
