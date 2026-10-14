package `in`.juspay.hypersdk.kmp

/**
 * One HyperSDK session. Create it with [HyperSdk] from shared code or with `HyperSdk(activity)` /
 * `HyperSdk(viewController)` from platform code, then [initiate] and [process]. Events arrive on
 * the main thread. [terminate] is optional: call it after the payment journey to free the SDK's
 * memory.
 *
 * Calls work from any thread. On iOS they run on the main thread and the caller waits, so never
 * call from a thread the main thread is blocked on.
 */
interface HyperSdk {
    /** True from [initiate] until [terminate]. Whenever it is true, [process] can be called. */
    val isInitialised: Boolean

    /**
     * Boots the SDK with the initiate JSON from the HyperSDK docs. Calling it again while
     * initialised reports `JP_017` to the earlier callback.
     *
     * @throws IllegalArgumentException if [payload] is not a JSON object.
     * @throws IllegalStateException on iOS, if no view controller is on screen.
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun initiate(payload: String, callback: HyperSdkCallback)

    /**
     * Starts a payment on the screen the user is on, so a session initiated on one screen can
     * pay on another. It can follow [initiate] right away. The outcome arrives as
     * `process_result`.
     *
     * @throws IllegalStateException before [initiate], after [terminate], or on iOS if no view
     * controller is on screen.
     * @throws IllegalArgumentException if [payload] is not a JSON object.
     */
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun process(payload: String)

    /** Releases the SDK UI and web view. The session can be initiated again. */
    fun terminate()

    /** Android: true when the SDK consumed the back press. Always false on iOS. */
    fun onBackPressed(): Boolean
}

/**
 * Creates a session on the screen the user is on: on Android the most recent open activity
 * (it must be a `FragmentActivity`), on iOS the app's main window. Apps with several windows
 * or processes should use the platform factories.
 *
 * @throws IllegalStateException if no such screen is found.
 * @throws IllegalArgumentException if [tenantId] is set without [clientId].
 */
@Throws(IllegalStateException::class, IllegalArgumentException::class)
expect fun HyperSdk(clientId: String? = null, tenantId: String? = null): HyperSdk

/** Receives every SDK event, on the main thread. */
fun interface HyperSdkCallback {
    fun onEvent(event: HyperSdkEvent)
}

/** An SDK event: its `event` [name] and the whole event as [json]. */
data class HyperSdkEvent(val name: String, val json: String) {
    // Events carry payment details; keep them out of logs
    override fun toString(): String = "HyperSdkEvent($name)"
}

/** Names of the events the SDK sends. */
object HyperSdkEvents {
    const val INITIATE_RESULT = "initiate_result"
    const val PROCESS_RESULT = "process_result"
    const val SHOW_LOADER = "show_loader"
    const val HIDE_LOADER = "hide_loader"
    const val LOG_STREAM = "log_stream"
}
