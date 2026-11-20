plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.deskclock.standby"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.deskclock.standby"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("standby-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = (project.findProperty("standby.storePassword") as String?) ?: "standby123456"
                keyAlias = (project.findProperty("standby.keyAlias") as String?) ?: "standby"
                keyPassword = (project.findProperty("standby.keyPassword") as String?) ?: "standby123456"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只有本地存在签名密钥时才签名（密钥不提交到仓库）
            if (rootProject.file("standby-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
