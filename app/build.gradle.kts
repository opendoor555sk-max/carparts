plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Every GitHub build gets a higher version number, so a new APK installs
// over the old one as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.kabadimarket.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kabadimarket.app"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
        buildConfigField("String", "API_BASE", "\"https://kabadi-market-backend.onrender.com/api\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("kabadi-release.jks")
            storePassword = "kabadi123"
            keyAlias = "kabadi"
            keyPassword = "kabadi123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Networking (supports GET/POST/PATCH/DELETE and file downloads)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Google's ready-made barcode scanner (QR, DataMatrix, Code128 ...).
    // Uses Google Play services, so it adds almost nothing to the APK size
    // and needs no camera permission.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
