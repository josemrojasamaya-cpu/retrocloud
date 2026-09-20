plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun configuredString(name: String, defaultValue: String): String = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .getOrElse(defaultValue)
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.retrosala.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retrosala.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.2"
        buildConfigField("String", "RETROSALA_SERVER_MODE", "\"${configuredString("RETROSALA_SERVER_MODE", "demo")}\"")
        buildConfigField("String", "RETROSALA_API_URL", "\"${configuredString("RETROSALA_API_URL", "")}\"")
        buildConfigField("String", "RETROSALA_SIGNALING_URL", "\"${configuredString("RETROSALA_SIGNALING_URL", "")}\"")
        buildConfigField("String", "RETROSALA_STREAMING_URL", "\"${configuredString("RETROSALA_STREAMING_URL", "")}\"")
        buildConfigField("String", "RETROSALA_SESSION_TOKEN", "\"${configuredString("RETROSALA_SESSION_TOKEN", "")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
