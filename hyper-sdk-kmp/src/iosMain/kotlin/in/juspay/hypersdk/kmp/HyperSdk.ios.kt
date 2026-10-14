@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, BetaInteropApi::class)

package `in`.juspay.hypersdk.kmp

import `in`.juspay.hypersdk.kmp.interop.HyperServices
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSString
import platform.Foundation.NSThread
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

/**
 * Creates a session that presents from [viewController] (held weakly), or from the app's main
 * window once that controller is off screen. The app links HyperSDK itself.
 *
 * @param clientId only together with [tenantId]; on its own it has no effect on iOS.
 * @param tenantId for non-India tenants, such as `juspayglobal`. Requires [clientId].
 * @throws IllegalArgumentException if [tenantId] is set without [clientId].
 */
@Throws(IllegalArgumentException::class)
fun HyperSdk(
    viewController: UIViewController,
    clientId: String? = null,
    tenantId: String? = null,
): HyperSdk {
    if (tenantId != null) {
        requireNotNull(clientId) { "clientId is required when tenantId is set" }
    }
    val services = onMainSync {
        if (tenantId != null && clientId != null) {
            HyperServices(tenantId = tenantId, clientId = clientId)
        } else {
            HyperServices()
        }
    }
    return IosHyperSdk(viewController, services)
}

@Throws(IllegalStateException::class, IllegalArgumentException::class)
actual fun HyperSdk(clientId: String?, tenantId: String?): HyperSdk {
    val host = checkNotNull(onMainSync { mainWindowRootViewController() }) { "No app window is on screen" }
    return HyperSdk(host, clientId, tenantId)
}

// The foreground scene's visible normal-level window, preferring the key one, so overlay windows are skipped
private fun mainWindowRootViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().sortedBy {
        when (it.activationState) {
            UISceneActivationStateForegroundActive -> 0
            UISceneActivationStateForegroundInactive -> 1
            else -> 2
        }
    }
    for (scene in scenes) {
        val windows = scene.windows.filterIsInstance<UIWindow>()
            .filter { !it.isHidden() && it.windowLevel == UIWindowLevelNormal && it.rootViewController != null }
        val window = windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
        if (window != null) return window.rootViewController
    }
    return null
}

private class IosHyperSdk(
    viewController: UIViewController,
    private val services: HyperServices,
) : HyperSdk {
    private val viewController = WeakReference(viewController)

    override val isInitialised: Boolean
        get() = onMainSync { services.isInitialised() }

    override fun initiate(payload: String, callback: HyperSdkCallback) = onMainSync {
        val dictionary = jsonObject(payload)
        // The SDK keeps this block until terminate(), so it captures only the merchant's callback
        services.initiate(presenter(), dictionary) { data ->
            if (data != null) {
                val event = HyperSdkEvent(data["event"] as? String ?: "", jsonString(data))
                onMain { callback.onEvent(event) }
            }
        }
    }

    override fun process(payload: String) = onMainSync {
        // The SDK only logs this; fail loudly instead
        check(services.isInitialised()) { "initiate() must be called before process(), and not after terminate()" }
        val dictionary = jsonObject(payload)
        // The SDK only presents from a controller that has nothing presented on it
        services.baseViewController = topMost(presenter())
        services.shouldUseViewController = true
        services.process(dictionary)
    }

    override fun terminate() = onMainSync {
        // Before terminate(): HyperSDK ignores writes to the callback afterwards, so it would never be released
        services.hyperSDKCallback = { _ -> }
        services.terminate()
    }

    override fun onBackPressed(): Boolean = false

    // The session's controller while it is on screen, otherwise the app's main window
    private fun presenter(): UIViewController {
        val own = viewController.value
        if (own?.viewIfLoaded?.window != null) return own
        return checkNotNull(mainWindowRootViewController() ?: own) { "No view controller is on screen" }
    }
}

// Runs on the main thread, waiting when called from another one. Rethrows on the caller, because an
// exception escaping a dispatched block would abort the app
private fun <T> onMainSync(block: () -> T): T {
    if (NSThread.isMainThread) return block()
    var result: Result<T>? = null
    dispatch_sync(dispatch_get_main_queue()) { result = runCatching(block) }
    return result!!.getOrThrow()
}

private fun onMain(block: () -> Unit) {
    if (NSThread.isMainThread) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue(), block)
    }
}

private fun topMost(viewController: UIViewController): UIViewController {
    var top = viewController
    while (true) {
        top = top.presentedViewController ?: return top
    }
}

private fun jsonObject(json: String): Map<Any?, *> {
    val data = NSString.create(string = json).dataUsingEncoding(NSUTF8StringEncoding)
    val parsed = data?.let { NSJSONSerialization.JSONObjectWithData(it, 0u, null) }
    require(parsed is Map<*, *>) { "payload must be a JSON object" }
    @Suppress("UNCHECKED_CAST")
    return parsed as Map<Any?, *>
}

private fun jsonString(dictionary: Map<Any?, *>): String {
    if (!NSJSONSerialization.isValidJSONObject(dictionary)) return "{}"
    val data = NSJSONSerialization.dataWithJSONObject(dictionary, 0u, null) ?: return "{}"
    return data.bytes?.readBytes(data.length.toInt())?.decodeToString() ?: "{}"
}
