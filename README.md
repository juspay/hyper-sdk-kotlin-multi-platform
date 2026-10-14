# HyperSDK for Kotlin Multiplatform

Juspay HyperSDK for Kotlin Multiplatform apps on Android and iOS, with Compose Multiplatform
or native screens.

`in.juspay:hyper-sdk-kmp` gives shared Kotlin code the `HyperSdk` API: create a session,
`initiate`, `process`, `terminate`, and events. It has no Compose dependency. You add HyperSDK
itself to each app as usual (Juspay's Gradle plugin on Android, CocoaPods or Swift Package
Manager on iOS); no Swift code is needed.

## Requirements

- Kotlin 2.4.10 or newer.
- Android: HyperSDK 2.2.8 or newer through `hypersdk.plugin`, a `FragmentActivity` host,
  `minSdk` 21 and `compileSdk` 34 or newer.
- iOS: HyperSDK 2.2.9.7 or newer, iOS 15 or newer, and a static Kotlin framework
  (`isStatic = true`, the default in the KMP template).

## 1. Shared module

```kotlin
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            // Static: HyperSDK is resolved when Xcode links the app
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("in.juspay:hyper-sdk-kmp:0.1.0")
        }
    }
}
```

That's all you need when only shared Kotlin code calls the library. If platform code calls it
directly, that code needs the dependency too:

- **Android app module**, for example `HyperSdk(activity)` in an Activity: add the same
  `implementation("in.juspay:hyper-sdk-kmp:0.1.0")` to that module.
- **Swift**, for example `HyperSdk_iosKt.HyperSdk(viewController:)` from a SwiftUI screen: add
  `export("in.juspay:hyper-sdk-kmp:0.1.0")` to the framework above, and declare the dependency
  with `api(...)` instead of `implementation(...)`, because Kotlin only exports `api`
  dependencies to Swift.

A dynamic Kotlin framework (`isStatic = false`) works too, but its link step then needs
HyperSDK as well: add `linkerOpts("-F<path to the HyperSDK.xcframework slice>", "-framework", "HyperSDK")`
for each iOS target.

Add Juspay's Maven repository wherever your dependencies are resolved:

```kotlin
maven("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
```

## 2. Android app

Apply Juspay's plugin in the module that applies `com.android.application`. It adds HyperSDK,
the micro-SDKs and the asset package for your `clientId`. With AGP 9 that module is separate
from the shared KMP module.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
    }
}

// androidApp/build.gradle.kts
plugins {
    id("com.android.application")
    id("hypersdk.plugin") version "2.2.15"
}

hyperSdkPlugin {
    clientId = "<your clientId>"
    sdkVersion = "2.2.8"
}
```

Then, in the activity that hosts the payment:

- Extend `androidx.fragment.app.FragmentActivity` (or `AppCompatActivity`). The default
  `ComponentActivity` of the KMP template is not enough, because the SDK attaches a headless
  fragment. `setContent { }` keeps working.

## 3. iOS app

Add HyperSDK to the app as a native iOS integration does.

With CocoaPods:

```ruby
platform :ios, '15.0'

target 'iosApp' do
  use_frameworks!
  pod 'HyperSDK', '2.2.9.7'
end

post_install do |installer|
  fuse_path = "./Pods/HyperSDK/Fuse.rb"
  clean_assets = true
  if File.exist?(fuse_path)
    system("ruby", fuse_path.to_s, clean_assets.to_s)
  end
end
```

Put `MerchantConfig.txt` next to the Podfile with one line, `clientId = <your clientId>`,
then run `pod install` and open the `.xcworkspace`. The `post_install` step needs network
access: it downloads your assets and adds the URL schemes HyperSDK needs to the app's
`Info.plist`, so check that file into version control after the first install.

With Swift Package Manager (no CocoaPods needed):

1. In Xcode, File → Add Package Dependencies, add `https://github.com/juspay/hypersdk-ios.git`
   and link the `HyperSDK` product to the app target. Pin an exact version: tag `2.2.9-rc.7`
   carries HyperSDK 2.2.9.7.
2. Add HyperSDK's asset step as a Build pre-action of the app's scheme (Product → Scheme →
   Edit Scheme → Build → Pre-actions), with build settings provided from the app target. Use the
   script from the [hypersdk-ios README](https://github.com/juspay/hypersdk-ios#step-11a---add-build-pre-actions-script).
   It downloads your assets into the package's copy of HyperSDK and validates them.
3. Put `MerchantConfig.json` next to the `.xcodeproj` (a `MerchantConfig.txt` like the CocoaPods
   one works too):

    ```json
    { "clientConfigs": { "<your clientId>": {} } }
    ```

4. Run Product → Clean Build Folder once, then build. The pre-action needs network access the
   first time, and again after every clean.

The pre-action is part of the scheme, so share the scheme (it is stored in
`*.xcodeproj/xcshareddata/xcschemes`) so that CI and every developer run it.

## 4. Use it

Create a session, then call `initiate` and `process`:

```kotlin
val hyperSdk = HyperSdk()

hyperSdk.initiate(initiatePayloadJson) { event ->
    when (event.name) {
        HyperSdkEvents.INITIATE_RESULT -> ...
        HyperSdkEvents.PROCESS_RESULT -> ...   // event.json holds the full result
    }
}
hyperSdk.process(processPayloadJson)   // can follow initiate right away
```

`HyperSdk()` constructs HyperSDK's `HyperServices` on the screen the user is on: on Android the
most recent activity that is still open (it must be a `FragmentActivity`), on iOS the root
view controller of the app's main window. It works from any shared code, Compose included. In
Compose, create the session in an event handler (or `remember` it), not in a composable's
body, which runs again on every recomposition.

If your platform code creates the session, it can pass the screen instead of calling
`HyperSdk()`:

- Android: `HyperSdk(activity)`, with the `FragmentActivity` to show the payment UI in, and
  optionally `clientId`, `tenantId` or `container`.
- iOS: `HyperSdk(viewController)`, with the `UIViewController` to present from.

If that code is in the Android app module or in Swift, it needs the setup in step 1.

A session can be initiated on one screen and pay on another, for example initiated on a home
screen to warm up and processed on a checkout screen: `process()` shows the payment UI on the
screen the user is on when it is called. Keep the session object somewhere both screens can
reach, such as an app-level holder or a ViewModel scoped above navigation, rather than in a
composable that leaves composition in between.
[`sample/shared/.../App.kt`](sample/shared/src/commonMain/kotlin/in/juspay/hypersdk/kmp/sample/App.kt)
is a complete screen: it creates the session on Initiate and terminates it on Terminate or
when the screen leaves composition.

Payloads and events are JSON strings in the exact shapes the HyperSDK docs describe, so
the documented `initiate` and `process` payloads work unchanged. Parse `event.json` with
whatever JSON library the app already uses.

When the payment journey is done, you can call `hyperSdk.terminate()` to free the memory
HyperSDK holds. This is optional.

## Behaviour notes

- **clientId and tenantId.** The clientId goes in the initiate payload. The optional
  `clientId` and `tenantId` parameters of `HyperSdk(...)` are for
  multi-client apps and non-India tenants only, and `tenantId` requires `clientId`. On
  Android, a `clientId` alone selects that client's asset set (`juspay/<clientId>`), which a
  single-client build does not contain; on iOS a `clientId` alone has no effect. An unknown
  `tenantId` silently selects the India tenant.
- **Finding the screen (`HyperSdk()` and `process()`).** On Android the library keeps track of
  the app's activities with an androidx.startup initializer that it adds to the app's
  manifest, and uses the most recent one that is still open. Any activity counts, including
  other SDKs' activities. The initializer runs only in the app's default process. In another
  process, or if the app removes androidx.startup's `InitializationProvider`
  (`tools:node="remove"`), call
  `AppInitializer.getInstance(application).initializeComponent(CurrentActivityInitializer::class.java)`
  in `Application.onCreate`, or pass the activity to `HyperSdk(activity)`. On iOS the main
  window is the foreground scene's visible, normal-level window, preferring the key one; apps
  with several windows (iPad multi-window) should pass the controller to
  `HyperSdk(viewController)`.
- **Threads.** Create the session on the main thread. After that, `initiate`, `process`,
  `terminate` and `isInitialised` can be called from any thread, and events arrive on the main
  thread. On iOS every call runs on the main thread: a caller on another thread waits until it
  has run there, so calls reach HyperSDK in the order they were made. Don't make them from a
  thread the main thread is blocked waiting on (for example a worker that the main thread
  joins with `runBlocking`), or both wait forever.
- **Order of calls.** `process()` before `initiate()` or after `terminate()` throws
  `IllegalStateException` on both platforms. Whenever `isInitialised` is true you can call
  `process()`, even right after `initiate()`.
- **Payloads.** A payload that can't be parsed as a JSON object throws
  `IllegalArgumentException` on both platforms. Android's parser is lenient, so some invalid
  JSON (unquoted strings, comments) is accepted there and rejected on iOS; send strict JSON.
- **Calling from Swift.** `HyperSdk_iosKt.HyperSdk(viewController:clientId:tenantId:)`,
  `initiate(payload:callback:)` and `process(payload:)` are marked `@Throws`, so call them
  with `try`.
- **Sessions and screens.** The session holds the Activity or UIViewController it was created
  with (weakly on iOS), and `process()` uses the screen the user is on at that moment: on
  Android the most recent open activity, unless the session renders into a container; on iOS
  the session's controller while it is on screen, otherwise the app's main window.
- **Back press (Android).** If the app has its own back handling, call `hyperSdk.onBackPressed()`
  first and only navigate back when it returns `false`. It always returns `false` on iOS,
  where the SDK handles navigation itself.
- **Activity results and permissions (Android).** Nothing to forward: the SDK receives them
  through its own fragment.
- **Container mode (Android).** `HyperSdk(activity, container = viewGroup)` renders the
  SDK inside your view instead of over the whole screen.

## Samples

`sample/` holds two apps. Both initiate HyperSDK, open the payment page for a signed ₹1 order,
and log every event:

- **Compose Multiplatform** (`shared` + `androidApp` + `iosApp`): the UI is shared Compose
  code, and the screen creates the session with `HyperSdk()` when Initiate is tapped.
  `iosApp` gets HyperSDK through CocoaPods;
  `spmIosApp` is the same app with HyperSDK from Swift Package Manager instead.
- **Native UIs with shared logic** (`common` + `nativeAndroidApp` + `nativeIosApp`): Android
  Views and SwiftUI screens, with the payment flow in `sample/common`, which depends on the
  library only. The platform creates the session, `HyperSdk(activity)` in the Activity
  and `HyperSdk_iosKt.HyperSdk(viewController:clientId:tenantId:)` from Swift, and hands it
  to the shared `Checkout` class, which calls `initiate()` and `process()`.

`sample/common` also holds the payloads and the signing code used by both.

The samples sign the customer details for initiate (`signaturePayload`) and the order for the
payment page (`orderDetails`) with the merchant's sandbox key, and send each with `signature`
and `merchantKeyId`. To make a payment, copy `sample/sandbox-credentials.properties.example`
to `sample/sandbox-credentials.properties` (gitignored) and fill in your Juspay sandbox
merchant. On iOS, put its clientId in a `MerchantConfig.json` next to each sample's
`MerchantConfig.txt`; it is gitignored and takes precedence. Without credentials the samples use Juspay's demo
clientId `geddit`: the SDK boots, and **Pay** reports what is missing. Signing in the app is a
demo shortcut only: a real app creates the order, the signature or a session on its backend.

- Android: `./gradlew :sample:androidApp:installDebug` (or `:sample:nativeAndroidApp:installDebug`)
- iOS with CocoaPods: `cd sample/iosApp && pod install && open iosApp.xcworkspace` (or the
  same in `sample/nativeIosApp`), then run the `iosApp` scheme
- iOS with Swift Package Manager: `open sample/spmIosApp/iosApp.xcodeproj`, then Clean Build
  Folder and run the `iosApp` scheme

## Working on this repository

See [ARCHITECTURE.md](ARCHITECTURE.md) for how it is put together and how to build and check
changes.

## License

Copyright (c) Juspay Technologies. Licensed under the AGPL 3.0 license in [LICENSE](LICENSE).
Parts of `sample/` come from JetBrains' KMP-App-Template under the Apache 2.0 license; see
[`sample/LICENSE-KMP-App-Template`](sample/LICENSE-KMP-App-Template).
