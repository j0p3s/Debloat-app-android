import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeyPath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseSigningAvailable = !releaseKeyPath.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "io.github.j0p3s.debloater"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.j0p3s.debloater"
        // Android 11 (API 30) is the minimum. The app is tested against the
        // command set available up to Android 17 (API 37).
        minSdk = 30
        targetSdk = 36
        versionCode = 11
        versionName = "1.9.0"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseKeyPath!!)
                storePassword = releaseKeyPassword
                keyAlias = "debloater"
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            // Development APKs must not replace the permanently signed release app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 shrinks the APK a lot (Compose icons). proguard-rules.pro keeps the Shizuku
            // UserService, because Shizuku starts it by class name.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Lets unit tests create simple framework objects such as ApplicationInfo.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(releaseSigningAvailable) {
            "Release signing is required. Set ANDROID_KEYSTORE_PATH and ANDROID_KEYSTORE_PASSWORD; never generate a replacement key."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
