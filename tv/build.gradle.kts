import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Upload-key signing: from keystore.properties (local, git-ignored) or environment variables (CI).
// Without either, bundleRelease still builds an unsigned AAB.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
val uploadStoreFile = signing("storeFile", "GE_UPLOAD_STORE_FILE")

android {
    namespace = "com.guestexperience.tv"
    // Current AndroidX/Compose releases require compiling against API 37.1+. This does not change
    // which devices the app runs on (minSdk) or its runtime behaviour (targetSdk).
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.guestexperience.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signing("storePassword", "GE_UPLOAD_STORE_PASSWORD")
                keyAlias = signing("keyAlias", "GE_UPLOAD_KEY_ALIAS")
                keyPassword = signing("keyPassword", "GE_UPLOAD_KEY_PASSWORD")
            }
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
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Landscape is required for this TV product.
        disable += "LockedOrientationActivity"
        // The TV banner only exists at xhdpi, as Android TV requires.
        disable += "IconMissingDensityFolder"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
