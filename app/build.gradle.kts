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

val mapsApiKey = (findProperty("GIGI_MAPS_API_KEY") as String?)
    ?: System.getenv("GIGI_MAPS_API_KEY")
    ?: ""

val releaseStoreFilePath = (findProperty("GIGI_RELEASE_STORE_FILE") as String?)
    ?: System.getenv("GIGI_RELEASE_STORE_FILE")
val releaseStorePassword = (findProperty("GIGI_RELEASE_STORE_PASSWORD") as String?)
    ?: System.getenv("GIGI_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = (findProperty("GIGI_RELEASE_KEY_ALIAS") as String?)
    ?: System.getenv("GIGI_RELEASE_KEY_ALIAS")
val releaseKeyPassword = (findProperty("GIGI_RELEASE_KEY_PASSWORD") as String?)
    ?: System.getenv("GIGI_RELEASE_KEY_PASSWORD")
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
        versionCode = 35
        versionName = "v1.7.5"





        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SERVER_URL", "\"$configuredServerUrl\"")
        // Maps key is injected, never committed. Set GIGI_MAPS_API_KEY in local.properties
        // or the environment; Live falls back to a list-only view when it is blank.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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

    // Sceneview Android (Google Filament 3D Engine for Native 3D VRM Avatars)
    implementation("io.github.sceneview:sceneview:2.2.1")

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
    // Live: nearby posts + meet-up map
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

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
