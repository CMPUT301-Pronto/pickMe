plugins {
    alias(libs.plugins.android.application)
    // Google Services plugin - processes google-services.json and generates Firebase config
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.pickme"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pickme"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // Firebase BOM (Bill of Materials) - manages all Firebase library versions
    // Using BOM ensures all Firebase libraries are compatible with each other
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))

    // Firebase Services (versions managed by BOM)
    implementation("com.google.firebase:firebase-analytics")

    // Firestore - NoSQL cloud database for storing event data, user profiles, etc.
    implementation("com.google.firebase:firebase-firestore")

    // Firebase Storage - For storing and retrieving user-uploaded images (profile pics, event posters)
    implementation("com.google.firebase:firebase-storage")

    // Firebase Cloud Messaging (FCM) - Push notifications for event updates, lottery results
    implementation("com.google.firebase:firebase-messaging")

    // Firebase Authentication - Device-based authentication without requiring user accounts
    implementation("com.google.firebase:firebase-auth")

    // ZXing Library for QR Code Generation and Scanning
    // Core library for QR code processing
    implementation("com.google.zxing:core:3.5.3")
    // Android integration for ZXing - provides easy-to-use scanning activities
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Google Play Services - Location Services for geolocation features
    // Used to track user location for location-based event features
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Additional helpful libraries
    // Glide for efficient image loading and caching
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // CircleImageView for rounded profile images
    implementation("de.hdodenhof:circleimageview:3.1.0")
    // Material UI Elements
    implementation("com.google.android.material:material:1.12.0")
}