import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    // Juspay's plugin: adds HyperSDK, the micro-SDKs and this clientId's asset package to the app
    alias(libs.plugins.hypersdkPlugin)
}

// The sample merchant's clientId selects the asset package; see sample/sandbox-credentials.properties.example
val sampleClientId: String = rootProject.file("sample/sandbox-credentials.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } }.getProperty("clientId") }
    ?.takeIf { it.isNotBlank() }
    ?: "geddit"

android {
    namespace = "in.juspay.hypersdk.kmp.sample.nativeapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "in.juspay.hypersdk.kmp.sample.native"
        minSdk = libs.versions.sample.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

hyperSdkPlugin {
    clientId = sampleClientId.lowercase()
    sdkVersion = libs.versions.hypersdk.android.get()
}

dependencies {
    // Shared payment logic; brings in the library, nothing from Compose
    implementation(projects.sample.common)
}
