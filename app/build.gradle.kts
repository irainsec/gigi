plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val configuredServerUrl = (findProperty("GIGI_SERVER_URL") as String?)
    ?: System.getenv("GIGI_SERVER_URL")
    ?: "wss://gigi.iamanraj.com"

val releaseStoreFilePath = (findProperty("GIGI_RELEASE_STORE_FILE") as String?)
    ?: System.getenv("GIGI_RELEASE_STORE_FILE")
    ?: rootProject.file("my-release-key.jks").absolutePath
val releaseStorePassword = (findProperty("GIGI_RELEASE_STORE_PASSWORD") as String?)
    ?: System.getenv("GIGI_RELEASE_STORE_PASSWORD")
    ?: "123456"
val releaseKeyAlias = (findProperty("GIGI_RELEASE_KEY_ALIAS") as String?)
    ?: System.getenv("GIGI_RELEASE_KEY_ALIAS")
    ?: "my-key-alias"
val releaseKeyPassword = (findProperty("GIGI_RELEASE_KEY_PASSWORD") as String?)
    ?: System.getenv("GIGI_RELEASE_KEY_PASSWORD")
    ?: "123456"
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.aman.gigi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aman.gigi"
        minSdk = 26
        targetSdk = 35
        versionCode = 85
        versionName = "v2.4.2"







        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SERVER_URL", "\"$configuredServerUrl\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            versionNameSuffix = "-debug"
            // applicationIdSuffix = ".debug"
        }
        // Staging: release-signed but no R8 minification → ~3-4 min builds for quick testing
        create("staging") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-staging"
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Native libs are ~59 MB of the APK across four architectures, and a phone can
    // only ever use one of them. Splitting means an arm64 device downloads ~15 MB of
    // libs instead of 59. The universal APK is still produced for manual installs,
    // emulators, and as the website's "just give me the file" fallback.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    ksp(libs.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media:media:1.7.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation(libs.cloudy)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Removed: io.github.sceneview:sceneview (Filament 3D engine).
    // It was added for 3D VRM avatars; Twigi moved to 2D LPC sprites and the only
    // remaining ".glb/.vrm" code paths draw a static "✨ 3D" placeholder. The AAR was
    // costing 16 MB of the APK (4 native libs + bundled models/environments/materials)
    // with zero call sites. Re-add it only alongside a real renderer.

    // Play Billing
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // CameraX + face detection — powers the Sparkle photo moments
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    implementation(libs.guava)
    implementation(libs.play.services.location)

    // Chrome Custom Tabs — hosts the Spotify PKCE consent screen. A WebView would be
    // both a phishing pattern and a breach of Spotify's terms, which require the user
    // to see the real accounts.spotify.com address bar.
    implementation("androidx.browser:browser:1.8.0")

    // Connected Screensaver Dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    // Live: nearby posts + meet-up map.
    // Maps are rendered from OpenStreetMap tiles (OsmTiles/OsmMapView), so the Google
    // Maps SDK is gone — no API key, no billing account, ~2 MB smaller.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Glance Widgets
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
