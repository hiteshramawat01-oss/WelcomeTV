// Self-updates from GitHub Releases for builds installed outside Google Play.
// Only the apps' `sideload` flavour depends on this module; Play builds never contain it.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.guestexperience.updater"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
