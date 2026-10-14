# Architecture

How this repository is put together, what each part is for, and why it is built that way.
For integrating the library into an app, see [README.md](README.md).

## What this repo is

A Kotlin Multiplatform (KMP) wrapper around Juspay HyperSDK for Android and iOS. Shared
Kotlin code calls one `HyperSdk` API; on each platform the library forwards to the native
HyperSDK, which the app integrates the normal way:

- **Android:** Juspay's Gradle plugin (`hypersdk.plugin`) adds HyperSDK and the merchant's
  assets to the app; the library calls `in.juspay.services.HyperServices`.
- **iOS:** CocoaPods or Swift Package Manager (SPM) adds HyperSDK to the app; the library
  calls its Objective-C API through generated Kotlin bindings.

The library never bundles HyperSDK itself. That keeps assets, SDK versions and the payment
UI identical to a native integration, and lets merchants upgrade HyperSDK without waiting
for a new wrapper release.

## Repository map

```
hyper-sdk-kmp/                       Core library (in.juspay:hyper-sdk-kmp)
  src/commonMain/.../HyperSdk.kt     The public API shared by both platforms
  src/androidMain/.../HyperSdk.android.kt
                                     Android factories and implementation over HyperServices
  src/androidMain/.../CurrentActivity.kt
                                     Tracks the foreground activity for HyperSdk()
  src/androidMain/AndroidManifest.xml
                                     Registers that tracker at app start (androidx.startup)
  src/iosMain/.../HyperSdk.ios.kt    iOS factories and implementation over HyperSDK's Objective-C API
  src/nativeInterop/cinterop/HyperSDK.def
                                     Tells Kotlin which HyperSDK headers to generate bindings from
  build.gradle.kts                   Targets, header download, bindings, Android settings

sample/                              Demo apps (Juspay's demo clientId geddit by default)
  common/                            Non-UI sample code shared by every sample app
  shared/                            Compose Multiplatform UI and the iOS entry point
  androidApp/                        Android app for the Compose sample
  iosApp/                            iOS app for the Compose sample, HyperSDK via CocoaPods
  spmIosApp/                         The same iOS app, HyperSDK via Swift Package Manager
  nativeAndroidApp/                  Android Views app over sample/common
  nativeIosApp/                      SwiftUI app over sample/common, HyperSDK via CocoaPods
  sandbox-credentials.properties.example
                                     Template for the gitignored sample signing key file
  LICENSE-KMP-App-Template           Apache 2.0 license of the parts taken from KMP-App-Template

gradle/libs.versions.toml            Library version, Gradle dependency and plugin versions, Android SDK levels
settings.gradle.kts                  Module list and repositories
build.gradle.kts                     Declares the Gradle plugins (applied per module)
gradle.properties                    Gradle, Android and Kotlin build flags
README.md                            Merchant integration guide
CHANGELOG.md                         Release notes
LICENSE                              License text (AGPL-3.0)
```

## The library: `hyper-sdk-kmp`

### `HyperSdk.kt` (commonMain): the public API

Everything a merchant's shared code touches:

| Declaration | Purpose |
|---|---|
| `interface HyperSdk` | One payment session: `initiate`, `process`, `terminate`, `isInitialised`, `onBackPressed` |
| `expect fun HyperSdk(clientId, tenantId)` | Creates a session on the screen the user is on, so shared code needs no platform host |
| `fun interface HyperSdkCallback` | Receives every SDK event, always on the main thread |
| `data class HyperSdkEvent(name, json)` | One event: its `event` name and the full event as JSON. `toString()` prints only the name, so payment details don't end up in logs |
| `object HyperSdkEvents` | Constants for the common event names (`initiate_result`, `process_result`, ...) |

Design choices and why:

- **Payloads and events are JSON strings.** They are exactly the JSON shapes the HyperSDK
  docs describe, so documented payloads work unchanged, and the library adds no
  serialization dependency. Android wants `org.json.JSONObject` and iOS wants
  `NSDictionary`; a string is the one format both convert from.
- **An interface plus platform factories, not `expect class`.** Creating a session needs a
  platform host (a `FragmentActivity` or a `UIViewController`), which common code can't
  name. Factories keep that platform detail out of the shared API, and `expect class` is
  still Beta in Kotlin.
- **One contract for both platforms.** `process()` before `initiate()` or after
  `terminate()` throws `IllegalStateException`; a payload that can't be parsed as a JSON
  object throws `IllegalArgumentException`. Two platform differences remain: on iOS,
  `initiate()` and `process()` also throw `IllegalStateException` if the session's
  `UIViewController` has been deallocated, and Android's `org.json` parser is lenient, so
  some invalid JSON that iOS rejects is accepted there. All of these exceptions are declared
  with `@Throws`, so Swift callers get Swift errors instead of a crash.
- **The app makes every call.** A factory constructs the native `HyperServices` right away,
  and the library never creates, initiates or terminates a session on its own: when to do
  each is the merchant's decision, as in a native integration. For the same reason there is
  no Compose helper tied to composition.
- **`HyperSdk()` and `process()` find the screen.** `HyperSdk()` finds it so shared code,
  Compose screens included, can create a session without a platform host, and `process()`
  looks again so a session initiated on a home screen can pay on a checkout screen (HyperSDK
  Android's `process(activity, payload)` moves the SDK to the activity it is given).
  - Android: `CurrentActivity` (activity lifecycle callbacks, registered at app start by an
    androidx.startup `Initializer` that this library's manifest adds to the app) keeps weak
    references to the process's activities, most recently created, started or resumed first,
    and returns the first that isn't finishing or destroyed. Skipping finishing activities
    matters in result callbacks, which run before the screen underneath resumes. Any activity
    in the process counts, including other SDKs'. The tracker never calls the SDK, and
    androidx.startup runs it only in the app's default process. A session
    that renders into a container stays on that container's activity.
  - iOS: the root view controller of the app's main window (the foreground scene's visible,
    normal-level window, preferring the key one, so overlay windows are skipped). `initiate()`
    and `process()` present from the session's own controller while it is on screen and fall
    back to that main window otherwise.
  Compose's `LocalActivity` and `LocalUIViewController` would also find the host, but they
  bring Compose back into the library and only work inside composition.

### `HyperSdk.android.kt` (androidMain)

`HyperSdk(activity, clientId, tenantId, container)` creates the session over
`in.juspay.services.HyperServices`, constructing it right away.

- **`compileOnly` HyperSDK.** The library compiles against HyperSDK 2.2.8 but never ships
  it; the merchant's `hypersdk.plugin` adds the runtime version. Shipping it would pin
  merchants to our version and duplicate classes.
- **`FragmentActivity` host.** HyperSDK attaches a headless fragment to the activity for
  activity results and permissions, so a plain `ComponentActivity` can't host it.
- **Main-thread events.** Some SDK paths deliver events on a bridge thread; the wrapper
  posts those to the main thread so the callback contract holds.
- **Nullable callback parameters.** The SDK passes a null response handler on some paths,
  so the override declares both parameters nullable to avoid a crash.
- **Fail-loud `process()` after `terminate()`.** The native SDK silently drops that call;
  the wrapper throws instead, matching iOS.

### `HyperSdk.ios.kt` (iosMain) and the HyperSDK bindings

`HyperSdk(viewController, clientId, tenantId)` creates the session over HyperSDK's
`HyperServices` class, called directly from Kotlin.

How the call reaches HyperSDK:

1. At **our** build, `downloadHyperSdkIosHeaders` (in `build.gradle.kts`) downloads the
   HyperSDK 2.2.9.7 and HyperCore 1.0.7 frameworks from Juspay's release server, checks their
   SHA-256 (the values from Juspay's `Package.swift` files), and keeps only their `Headers/`
   and `Modules/` folders, never the binaries.
2. `HyperSDK.def` tells Kotlin's cinterop tool to generate Kotlin bindings from those headers
   (package `in.juspay.hypersdk.kmp.interop`). The bindings ship with the library
   as `*-cinterop-HyperSDK.klib`.
3. At the **merchant's** build, their Kotlin framework references `HyperServices` without
   containing it. Because the framework is static (the KMP template default), the reference
   is resolved when Xcode links the app, against the HyperSDK that CocoaPods or SPM added.

Why this design: an earlier, unreleased design used a Swift adapter file that apps had to
copy into their project. A copied file can't be updated by a library release and drifts
silently. With bindings, all iOS logic lives in this library and every change ships with
its version.

Other choices in the iOS implementation:

- **Weak view controller.** HyperSDK itself holds the presenting controller weakly; the
  wrapper does the same, so a session never keeps a screen alive.
- **No retain cycle through the SDK callback.** HyperSDK keeps the callback block until it
  is replaced. The block captures only the merchant's callback, never the wrapper.
  `terminate()` swaps in an empty block before ending the session, because HyperSDK ignores
  writes to the callback once its session is gone; an empty block rather than null keeps any
  late call from the SDK safe.
- **Main thread.** HyperSDK creates web views and drives UIKit, so every call into it,
  including creating `HyperServices` and reading `isInitialised`, runs on the main thread,
  and events are delivered there. A call from another thread waits for its main-thread run
  (`dispatch_sync`), so calls reach HyperSDK in the order they were made and `process()`'s
  order check sees every earlier `initiate()` and `terminate()`. The price is that a caller
  must never be a thread the main thread is blocked on. An exception raised during that run
  is carried back and rethrown on the caller's thread, because a Kotlin exception escaping a
  dispatched block aborts the app.
- **Presentation.** `process()` presents from the top-most presented controller with
  `shouldUseViewController = true`; HyperSDK only presents when nothing else is presented.
- **Which HyperSDK APIs it uses.** `initiate`, `process`, `terminate`, `isInitialised`
  (HyperSDK 2.0.14+), the `baseViewController` and `hyperSDKCallback` properties,
  `shouldUseViewController` (2.1.20+) and, only when a tenant is passed,
  `initWithTenantId:clientId:` (2.2.2.5+). All exist in the documented minimum, 2.2.9.7.
  Calling an API that a merchant's HyperSDK lacks crashes at runtime, so any new call needs
  the minimum raised or a version check.

### `build.gradle.kts` (core)

| Part | Why |
|---|---|
| `iosArm64()`, `iosSimulatorArm64()` | Devices and Apple-silicon simulators; Compose Multiplatform dropped Intel simulators |
| `downloadHyperSdkIosHeaders` | Pinned, checksum-verified headers for the bindings; also guards against zip entries escaping the output folder |
| `cinterops.create("HyperSDK")` | Generates the bindings per iOS target, pointing at the matching xcframework slice |
| `android { aarMetadata { minCompileSdk = 34 } }` | Without it the AAR would demand our own compileSdk (36) from every merchant |
| `api(androidx.fragment 1.8.8)` | `FragmentActivity` is in the public API. 1.8.x is the last fragment line supporting minSdk 21 |
| `implementation(androidx.startup 1.1.1)` | Runs the `CurrentActivity` initializer at app start; most AndroidX apps already include it |

## Samples

All samples use the sandbox merchant in `sample/sandbox-credentials.properties` (Juspay's
demo clientId `geddit` without it) and log every SDK event on
screen.

| Folder | What it shows |
|---|---|
| `common/` | Shared non-UI code: payloads, signing, the sandbox key, and `Checkout`. Depends on the library only |
| `shared/` + `androidApp/` + `iosApp/` | A Compose Multiplatform app: the screen in `shared/App.kt` creates the session with `HyperSdk()` on Initiate and terminates it on Terminate or when it leaves composition |
| `spmIosApp/` | The same Compose app on iOS, with HyperSDK from SPM instead of CocoaPods |
| `common/` + `nativeAndroidApp/` + `nativeIosApp/` | Native UIs (Android Views, SwiftUI) with the payment flow in shared Kotlin (`Checkout`) |

### `sample/common`

| File | Purpose |
|---|---|
| `SamplePayloads.kt` | Builds the initiate and payment-page JSON. Signs the customer details (`signaturePayload`) and the order (`orderDetails`) with the merchant's key |
| `Checkout.kt` | The shared payment flow used by the native apps: owns the session, calls `initiate()` and `process()` |
| `Platform.kt` + `Platform.android.kt` + `Platform.ios.kt` | SHA256withRSA signing (`java.security` on Android, the Security framework on iOS) and the current time |
| `RsaKeyDer.kt` | Converts the key between PKCS#8 (Android needs it) and PKCS#1 (iOS needs it) |
| `build.gradle.kts` | Generates `SandboxCredentials.kt` (clientId, merchant ID, key ID, key) from the gitignored `sample/sandbox-credentials.properties`, and builds the `SampleCommon` iOS framework for `nativeIosApp` |

Signing in the app is a demo shortcut. A real app creates the order and the signature (or
a session) on its backend; a merchant private key must never ship in an app.

### Android apps (`androidApp`, `nativeAndroidApp`)

Each is a separate `com.android.application` module that applies `hypersdk.plugin`
(clientId from `sample/sandbox-credentials.properties`, `geddit` by default). The plugin must
live in the application module: it needs the
application's dependency configurations, and AGP 9 no longer allows an application module
to also be a KMP module. Each activity extends `FragmentActivity` and declares
`android:configChanges`, so rotation doesn't recreate the activity mid-payment.

### iOS apps (`iosApp`, `nativeIosApp`, `spmIosApp`)

| File | Purpose |
|---|---|
| `iosApp.xcodeproj` | Xcode project. A build phase runs Gradle's `embedAndSignAppleFrameworkForXcode` to build and embed the Kotlin framework |
| `iosApp/iOSApp.swift`, `ContentView.swift` | SwiftUI entry point; the Compose apps just host `MainViewController()`, the native app is a SwiftUI screen over `Checkout` |
| `Configuration/Config.xcconfig` | Bundle id, app name and signing team. `TEAM_ID` is empty, so the apps run on a simulator; set it to run on a device |
| `iosApp/Info.plist` | Includes the URL schemes and UPI-app query schemes that HyperSDK's asset step adds |
| `Podfile`, `Podfile.lock` | CocoaPods apps: HyperSDK 2.2.9.7, and a `post_install` hook that runs Juspay's `Fuse.rb` to download the clientId's assets |
| `MerchantConfig.txt` | All three apps: the clientId for Juspay's asset step (`geddit`). A gitignored `MerchantConfig.json` next to it takes precedence, for testing with your own merchant |
| `spmIosApp`: `project.pbxproj` | Adds the `hypersdk-ios` package with an exact-version requirement, `2.2.9-rc.7` (HyperSDK 2.2.9.7) |
| `spmIosApp`: `Package.resolved` | What that resolved to: `hypersdk-ios` 2.2.9-rc.7 and `hypercore-ios` 1.0.7 |
| `spmIosApp`: `xcshareddata/xcschemes/iosApp.xcscheme` | The shared scheme, with Juspay's asset step as a Build pre-action (SPM has no install hook) |

## Build configuration

| File | Notable contents |
|---|---|
| `gradle/libs.versions.toml` | The library version (`hyper-sdk-kmp`, 0.1.0), Kotlin 2.4.10, Compose Multiplatform 1.11.1 (samples only), AGP 9.2.1, HyperSDK Android 2.2.8, hypersdk.plugin 2.2.15, compileSdk/targetSdk 36, minSdk 21 (library) / 24 (samples). Not here: the HyperSDK iOS and HyperCore versions (core `build.gradle.kts`, the Podfiles, the SPM project) and the Gradle version (`gradle/wrapper/gradle-wrapper.properties`) |
| `settings.gradle.kts` | Juspay's Maven repository, for HyperSDK and Juspay's Gradle plugin. The root project is named `hyper-sdk-kmp-root` because a root named like the `:hyper-sdk-kmp` module breaks Gradle's type-safe project accessors |
| `gradle.properties` | `kotlin.mpp.enableCInteropCommonization=true` lets the shared `iosMain` code see the HyperSDK bindings. Only this build needs it; consumers don't |

The toolchain matches the official KMP project template (Kotlin 2.4.10, compileSdk 36, and
Compose Multiplatform 1.11.1 for the samples). Kotlin libraries are not forward compatible: a
library built with a newer Kotlin can't be used by an app on an older one. Building with
the template's versions keeps the library usable by any project created from it.

## Checking a change

1. `./gradlew :hyper-sdk-kmp:assemble`
2. Android: `./gradlew :sample:androidApp:installDebug` (and `:sample:nativeAndroidApp:installDebug`),
   then tap **Initiate** and **Pay ₹1.00**; with a sandbox merchant's credentials in place, the
   payment page should open.
3. iOS with CocoaPods: in `sample/iosApp` (and `sample/nativeIosApp`), `pod install`, open the
   `.xcworkspace`, run on a simulator, then **Initiate** and **Pay ₹1.00**.
4. iOS with SPM: open `sample/spmIosApp/iosApp.xcodeproj`, Clean Build Folder, run on a
   simulator, same taps.
5. For a full sandbox payment: Netbanking → Axis → choose `CHARGED` on the bank simulator →
   Submit. `process_result` should report `charged`.

The sample payment needs `sample/sandbox-credentials.properties`; see the `.example` file.

## Common changes

- **Releasing:** bump `hyper-sdk-kmp` in the version catalog (it sets the library's version),
  update the versions in the README's dependency snippets, and date the CHANGELOG heading.
- **New HyperSDK Android version:** bump `hypersdk-android` in the version catalog (the
  compile-time API and the samples' runtime). Update the compile-against version in the
  CHANGELOG and this file, and the README's minimum if the wrapper starts using a newer API.
- **New HyperSDK iOS version:**
  1. In `hyper-sdk-kmp/build.gradle.kts`, update the HyperSDK URL and checksum from the new
     tag's `Package.swift` in `juspay/hypersdk-ios`. That file also names the HyperCore version
     it depends on; take HyperCore's URL and checksum from that tag of `juspay/hypercore-ios`.
  2. Update the version in both samples' `Podfile`s, run `pod install` in each, and commit
     the regenerated `Podfile.lock` files.
  3. Change the package's exact version in `spmIosApp` (Xcode → project → Package
     Dependencies) and commit the re-resolved `Package.resolved`.
  4. Update the versions in the README (Requirements and the Podfile and SPM instructions), the
     CHANGELOG and this file, then rerun all three iOS samples.
- **Calling a new HyperSDK iOS API:** Objective-C APIs reachable from `HyperSDK/HyperSDK.h`
  in the pinned headers are already in the generated bindings
  (`in.juspay.hypersdk.kmp.interop`). Swift-only APIs (`HyperSDK-Swift.h`) are not bound. For
  an API added after the pinned version, first do "New HyperSDK iOS version". Then check which
  HyperSDK version introduced the API: merchants on older versions would crash, so guard it
  or raise the documented minimum.
- **Exposing a new event name:** add a constant to `HyperSdkEvents`.
- **New Kotlin version:** bump `kotlin` in the version catalog, update the Kotlin minimum in
  the README's Requirements, and note it in the CHANGELOG as a breaking change: apps on the
  older version can no longer use the library. Compose Multiplatform is only used by the
  samples, so bumping `compose-multiplatform` affects nothing merchants get.

## Known gaps

Not done yet:

- `JuspayResponseHandler` replies, `getMerchantView`, `onStartWaitingDialogCreated` and
  `preFetch` are not exposed through the common API.
- No API to rebind a session to a new Android `Activity`; a recreated activity needs a new
  session.
- No `explicitApi()` mode or binary-compatibility validation.
- The samples live in the same Gradle build as the library, so every library build also runs
  `hypersdk.plugin`, which makes network calls while Gradle configures the build.
