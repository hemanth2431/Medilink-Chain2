import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Read local.properties safely
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", localProps.getProperty("supabase.publishable.key", "sb_publishable_rlpcxBn8tKCobSjYQJFrvw_p-U2ZzM8"))
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY", localProps.getProperty("google.maps.key", "AIzaSyD3w1ZGMLz189GMwFFreuCYGqOh_Gsu4Qw"))

android {
    namespace = "com.example.medilinkchain"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.medilinkchain"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Inject Supabase anon key from local.properties into BuildConfig
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Inject Google Maps API key into BuildConfig and Manifest placeholders
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation(libs.junit)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Firebase
    implementation(
        platform(
            "com.google.firebase:firebase-bom:33.5.1"
        )
    )

    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Firebase Storage removed
    // implementation("com.google.firebase:firebase-storage")

    // Supabase / HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("org.json:json:20240303")

    // QR Code
    implementation(
        "com.journeyapps:zxing-android-embedded:4.3.0"
    )

    implementation(
        "com.google.zxing:core:3.5.1"
    )

    // Google Maps & Location Services
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation(
        "com.google.android.gms:play-services-location:21.0.1"
    )

    // OpenStreetMap
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
