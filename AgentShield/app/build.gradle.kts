plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "ci.agent.shield"
    compileSdk = 34
    defaultConfig {
        applicationId = "ci.agent.shield"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1"
        ndk { abiFilters.add("arm64-v8a") }   // ton A54 est en arm64 : APK plus léger
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.5.6@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
}