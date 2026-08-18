plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "uk.dansvlogs.clinicalwatch.companion"
    compileSdk = 36
    defaultConfig {
        applicationId = "uk.dansvlogs.clinicalwatch.companion"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(files("../app/libs/samsung-health-data-api-1.1.0.aar"))
}
