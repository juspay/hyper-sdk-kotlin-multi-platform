import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

group = "in.juspay"
version = libs.versions.hyper.sdk.kmp.get()

// iOS headers for the Kotlin bindings; checksums from the hypersdk-ios and hypercore-ios Package.swift files
val iosFrameworks = listOf(
    Triple(
        "HyperSDK",
        "https://public.releases.juspay.in/release/ios/hyper-sdk/2.2.9.7/HyperSDK.zip",
        "af317d27d7e30edcf4bce9a5e6fe88dbb3df861f403b1da23bfbd63a7d194809",
    ),
    Triple(
        "HyperCore",
        "https://public.releases.juspay.in/release/ios/hyper-core/1.0.7/HyperCore.zip",
        "9688b1e6989b400f1379ace352ce5312930bb9859ef4569d99d9e262ef8dd832",
    ),
)
val iosFrameworksDir: File = layout.buildDirectory.dir("hypersdk-ios").get().asFile

val downloadHyperSdkIosHeaders by tasks.registering {
    val frameworks = iosFrameworks
    val outputDir = iosFrameworksDir
    inputs.property("frameworks", frameworks.map { "${it.second}#${it.third}" })
    outputs.dir(outputDir)
    doLast {
        outputDir.deleteRecursively()
        frameworks.forEach { (name, url, sha256) ->
            val bytes = URI(url).toURL().readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == sha256) { "Checksum mismatch for $url: expected $sha256, got $actual" }
            ZipInputStream(bytes.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    // cinterop needs only headers and module maps
                    val wanted = entry.name.startsWith("$name.xcframework/") &&
                        ("/Headers/" in entry.name || "/Modules/" in entry.name)
                    if (wanted && !entry.isDirectory) {
                        val target = outputDir.resolve(entry.name)
                        require(target.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)) {
                            "Zip entry outside the output directory: ${entry.name}"
                        }
                        target.parentFile.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
    }
}

kotlin {
    listOf(
        iosArm64() to "ios-arm64",
        iosSimulatorArm64() to "ios-arm64_x86_64-simulator",
    ).forEach { (target, slice) ->
        target.compilations.getByName("main").cinterops.create("HyperSDK") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/HyperSDK.def"))
            compilerOpts(
                "-F${iosFrameworksDir}/HyperSDK.xcframework/$slice",
                "-F${iosFrameworksDir}/HyperCore.xcframework/$slice",
            )
        }
    }

    android {
        namespace = "in.juspay.hypersdk.kmp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Otherwise the AAR would require our compileSdk from merchants
        aarMetadata {
            minCompileSdk = 34
        }
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        androidMain.dependencies {
            // Supplied at runtime by the merchant's hypersdk.plugin
            compileOnly(libs.hypersdk.android)
            // FragmentActivity is part of the public API
            api(libs.androidx.fragment)
            // Starts the activity tracker behind HyperSdk()
            implementation(libs.androidx.startup.runtime)
        }
    }
}

tasks.matching { it.name.startsWith("cinteropHyperSDK") }.configureEach {
    dependsOn(downloadHyperSdkIosHeaders)
}
