package `in`.juspay.hypersdk.kmp.sample

import `in`.juspay.hypersdk.kmp.HyperSdk

/**
 * The shared payment logic of the native sample: owns the HyperSDK session and drives the
 * flow, so the platform UI only forwards button taps and shows the [onEvent] lines.
 *
 * Platform code creates the [HyperSdk], because that needs the host Activity or
 * UIViewController, and hands it in here. Events arrive on the main thread.
 */
class Checkout(private val hyperSdk: HyperSdk, private val onEvent: (String) -> Unit) {
    val isInitialised: Boolean
        get() = hyperSdk.isInitialised

    fun initiate() {
        hyperSdk.initiate(SamplePayloads.initiate()) { event ->
            onEvent("${event.name}  ${event.json}")
        }
    }

    fun pay() {
        try {
            hyperSdk.process(SamplePayloads.signedPaymentPage())
        } catch (e: Exception) {
            onEvent("error  ${e.message}")
        }
    }

    fun terminate() = hyperSdk.terminate()
}
