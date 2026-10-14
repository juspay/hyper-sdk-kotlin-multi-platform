import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// The sample merchant comes from a gitignored file; see sample/sandbox-credentials.properties.example
val credentialsFile = rootProject.file("sample/sandbox-credentials.properties")
val credentials = Properties().apply {
    if (credentialsFile.exists()) credentialsFile.inputStream().use { load(it) }
}
val sandboxClientId: String = credentials.getProperty("clientId", "").ifBlank { "geddit" }
val sandboxMerchantId: String = credentials.getProperty("merchantId", "").ifBlank { sandboxClientId }
val sandboxMerchantKeyId: String = credentials.getProperty("merchantKeyId", "")
val sandboxPrivateKey: String = credentials.getProperty("privateKey", "")

val generateSandboxCredentials by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sandboxCredentials/kotlin")
    // Local copies, because the action below must not reference the build script itself (configuration cache)
    val clientId = sandboxClientId
    val merchantId = sandboxMerchantId
    val merchantKeyId = sandboxMerchantKeyId
    val privateKey = sandboxPrivateKey
    inputs.files(credentialsFile)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("in/juspay/hypersdk/kmp/sample/SandboxCredentials.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package `in`.juspay.hypersdk.kmp.sample
            |
            |// Generated from sample/sandbox-credentials.properties. Do not edit or commit.
            |internal object SandboxCredentials {
            |    const val CLIENT_ID = "$clientId"
            |    const val MERCHANT_ID = "$merchantId"
            |    const val MERCHANT_KEY_ID = "$merchantKeyId"
            |    const val PRIVATE_KEY = "$privateKey"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SampleCommon"
            isStatic = true
            // Used by sample/nativeIosApp, whose Swift code creates the session with HyperSdk(viewController:)
            export(projects.hyperSdkKmp)
        }
    }

    android {
        namespace = "in.juspay.hypersdk.kmp.sample.common"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.sample.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSandboxCredentials)
            dependencies {
                // The library only: no Compose dependency in the shared logic
                api(projects.hyperSdkKmp)
            }
        }
    }
}
