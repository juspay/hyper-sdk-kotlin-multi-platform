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

// iOS headers for the Kotlin bindings; URLs and checksums come from the hypersdk-ios and hypercore-ios Package.swift files
val hyperSdkIosVersion = libs.versions.hypersdk.ios.get()
val iosFrameworksDir: File = layout.buildDirectory.dir("hypersdk-ios").get().asFile

val downloadHyperSdkIosHeaders by tasks.registering {
    val version = hyperSdkIosVersion
    val outputDir = iosFrameworksDir
    inputs.property("hyperSdkVersion", version)
    outputs.dir(outputDir)
    doLast {
        // Without timeouts a stalled connection hangs the build
        fun download(url: String): ByteArray = URI(url).toURL().openConnection().run {
            connectTimeout = 30_000
            readTimeout = 60_000
            getInputStream().use { it.readBytes() }
        }
        fun packageSwift(repo: String, tag: String) =
            download("https://raw.githubusercontent.com/juspay/$repo/$tag/Package.swift").decodeToString()
        fun binaryTarget(packageSwift: String, name: String): Triple<String, String, String> {
            val match = Regex("""name:\s*"$name",\s*url:\s*"([^"]+)",\s*checksum:\s*"([0-9a-f]{64})"""").find(packageSwift)
            val (url, sha256) = checkNotNull(match) { "No $name binary target in Package.swift" }.destructured
            return Triple(name, url, sha256)
        }
        val sdkPackage = packageSwift("hypersdk-ios", version)
        // HyperSDK's headers import HyperCore's, so use exactly the HyperCore version HyperSDK declares
        val coreVersion = checkNotNull(Regex("""hypercore-ios\.git",\s*\.exact\("([^"]+)"\)""").find(sdkPackage)) {
            "hypersdk-ios $version declares no exact HyperCore version"
        }.groupValues[1]
        val frameworks = listOf(
            binaryTarget(sdkPackage, "HyperSDK"),
            binaryTarget(packageSwift("hypercore-ios", coreVersion), "HyperCore"),
        )
        outputDir.deleteRecursively()
        frameworks.forEach { (name, url, sha256) ->
            val bytes = download(url)
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
