import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
val uploadStoreFile = signing("storeFile", "GE_UPLOAD_STORE_FILE")
// Sideload (GitHub Releases) builds: signed with the same key as the APKs already installed by hand.
val sideloadStoreFile: String? = System.getenv("GE_SIDELOAD_STORE_FILE")

android {
    namespace = "com.guestexperience.host"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.guestexperience.host"
        minSdk = 26
        // Phone apps on Google Play must target the latest required level (API 36 from Aug 2026).
        targetSdk = 36
        // CI sets these for sideload builds (-Pge.versionCode=… -Pge.versionName=…).
        versionCode = (findProperty("ge.versionCode") as String?)?.toInt() ?: 2
        versionName = (findProperty("ge.versionName") as String?) ?: "1.1.0"
    }

    signingConfigs {
        if (sideloadStoreFile != null) {
            create("sideload") {
                storeFile = file(sideloadStoreFile)
                storePassword = System.getenv("GE_SIDELOAD_STORE_PASSWORD")
                keyAlias = System.getenv("GE_SIDELOAD_KEY_ALIAS")
                keyPassword = System.getenv("GE_SIDELOAD_KEY_PASSWORD")
            }
        }
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signing("storePassword", "GE_UPLOAD_STORE_PASSWORD")
                keyAlias = signing("keyAlias", "GE_UPLOAD_KEY_ALIAS")
                keyPassword = signing("keyPassword", "GE_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    // play: for Google Play (no self-updating). sideload: GitHub Releases with an in-app Update button.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("sideload") {
            dimension = "distribution"
            if (sideloadStoreFile != null) signingConfig = signingConfigs.getByName("sideload")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadStoreFile != null) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":shared"))
    "sideloadImplementation"(project(":updater"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
