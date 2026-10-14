# Changelog

## 0.1.0 (unreleased)

- First release: `hyper-sdk-kmp`, the `HyperSdk` API for Android and iOS.
- Shared code creates a session with `HyperSdk()`, which finds the screen the user is on;
  platform code can pass the host to `HyperSdk(activity)` or
  `HyperSdk(viewController)` instead. `process()` shows the payment UI on the screen the user
  is on when it is called, so a session initiated on one screen can pay on another.
- iOS apps add HyperSDK through CocoaPods or Swift Package Manager; no Swift code is needed.
- Built with Kotlin 2.4.10. Compiled against HyperSDK Android 2.2.8 and HyperSDK iOS 2.2.9.7.
