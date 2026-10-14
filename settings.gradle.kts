rootProject.name = "hyper-sdk-kmp-root"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // Juspay's Gradle plugin: adds HyperSDK and the merchant's assets to the Android app
        maven("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
    }
}

include(":hyper-sdk-kmp")
include(":sample:common")
include(":sample:shared")
include(":sample:androidApp")
include(":sample:nativeAndroidApp")
