plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Версия = тег релиза. Порядок: VERSION_NAME из сборки GitHub → последний git-тег (v1.0.5) → 1.0.0.
// versionCode считается из номера: 1.0.5 → 10005, поэтому каждый следующий релиз всегда «новее».
fun gitTag(): String? = try {
    val p = ProcessBuilder("git", "describe", "--tags", "--abbrev=0").directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0 && Regex("^v?\\d+(\\.\\d+){0,2}$").matches(out)) out else null
} catch (e: Exception) { null }

val appVersionName: String = (System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: gitTag() ?: "1.0.0").removePrefix("v")
val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull()
    ?: appVersionName.split(".").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 1 }) * 10000 + (it.getOrElse(1) { 0 }) * 100 + (it.getOrElse(2) { 0 }) }

android {
    namespace = "ru.smolclimate.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "ru.smolclimate.app"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
