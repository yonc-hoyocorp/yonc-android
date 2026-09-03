import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Attestation knobs for THIS sample only: baked in from local.properties so the sample can be
// tried without an account system. A real host passes its YOnC application id and the signed-in
// user's opaque id at runtime — see README, "Attestation".
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun knob(key: String): String = localProps.getProperty(key) ?: ""

android {
    namespace = "dev.yonc.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.yonc.sample"
        minSdk = 26 // the SDK's own floor
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // The SDK ships arm64-v8a natives only (LiteRT-LM). Other ABIs would install but could
        // not load the engine, so they are not packaged at all.
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "YONC_APPLICATION_ID", "\"${knob("yonc.attestation.applicationId")}\"")
        buildConfigField("String", "YONC_REPORTING_ID", "\"${knob("yonc.attestation.externalReportingId")}\"")
    }

    buildTypes {
        release {
            // Shrunk and optimised on purpose: this sample doubles as the check that the SDK
            // survives R8 with nothing but the default rules.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so the release build installs on a test device; use your own signing
            // configuration in a real app.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // The inference runtime is loaded from extracted native libraries.
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.yonc.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
