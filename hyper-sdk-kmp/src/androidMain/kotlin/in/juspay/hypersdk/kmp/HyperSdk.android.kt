package `in`.juspay.hypersdk.kmp

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import `in`.juspay.hypersdk.data.JuspayResponseHandler
import `in`.juspay.hypersdk.ui.HyperPaymentsCallbackAdapter
import `in`.juspay.services.HyperServices
import org.json.JSONException
import org.json.JSONObject

/**
 * Creates a session for [activity], which must be a [FragmentActivity] because the SDK adds a
 * fragment to it.
 *
 * @param clientId only for multi-client apps (it selects that client's assets); single-client apps
 * put it in the initiate payload.
 * @param tenantId for non-India tenants, such as `juspayglobal`. Requires [clientId].
 * @param container renders the SDK inside this view instead of over the whole activity.
 * @throws IllegalArgumentException if [tenantId] is set without [clientId].
 */
fun HyperSdk(
    activity: FragmentActivity,
    clientId: String? = null,
    tenantId: String? = null,
    container: ViewGroup? = null,
): HyperSdk = AndroidHyperSdk(activity, clientId, tenantId, container)

@Throws(IllegalStateException::class, IllegalArgumentException::class)
actual fun HyperSdk(clientId: String?, tenantId: String?): HyperSdk {
    check(CurrentActivity.registered) { "Activity tracking isn't running in this process; use HyperSdk(activity)" }
    val activity = checkNotNull(CurrentActivity.get()) { "No activity is open" }
    check(activity is FragmentActivity) { "${activity.javaClass.name} must extend FragmentActivity (AppCompatActivity does)" }
    return HyperSdk(activity, clientId, tenantId)
}

private class AndroidHyperSdk(
    private val activity: FragmentActivity,
    clientId: String?,
    tenantId: String?,
    private val container: ViewGroup?,
) : HyperSdk {
    private val services: HyperServices = when {
        tenantId != null -> HyperServices(
            activity,
            tenantId,
            requireNotNull(clientId) { "clientId is required when tenantId is set" },
        )
        container != null -> HyperServices(activity, container, clientId)
        else -> HyperServices(activity, clientId)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override val isInitialised: Boolean
        get() = services.isInitialised()

    override fun initiate(payload: String, callback: HyperSdkCallback) {
        val adapter = object : HyperPaymentsCallbackAdapter() {
            // The SDK passes a null handler on some paths
            override fun onEvent(event: JSONObject?, handler: JuspayResponseHandler?) {
                if (event == null) return
                val sdkEvent = HyperSdkEvent(event.optString("event"), event.toString())
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    callback.onEvent(sdkEvent)
                } else {
                    mainHandler.post { callback.onEvent(sdkEvent) }
                }
            }
        }
        services.initiate(activity, jsonObject(payload), adapter)
    }

    override fun process(payload: String) {
        // The SDK silently drops this after terminate(); fail loudly like iOS
        check(services.isInitialised()) { "initiate() must be called before process(), and not after terminate()" }
        val json = jsonObject(payload)
        if (container != null) {
            services.process(activity, container, json)
        } else {
            // The screen the user is on now; the SDK moves there
            services.process(CurrentActivity.get() as? FragmentActivity ?: activity, json)
        }
    }

    override fun terminate() = services.terminate()

    override fun onBackPressed(): Boolean = services.onBackPressed()
}

// Same exception as iOS
private fun jsonObject(json: String): JSONObject =
    try {
        JSONObject(json)
    } catch (e: JSONException) {
        throw IllegalArgumentException("payload must be a JSON object", e)
    }
