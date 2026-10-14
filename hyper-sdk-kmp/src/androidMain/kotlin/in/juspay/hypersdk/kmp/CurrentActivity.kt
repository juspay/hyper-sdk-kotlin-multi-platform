package `in`.juspay.hypersdk.kmp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.startup.Initializer
import java.lang.ref.WeakReference

/** The app's activities, most recent first, so [HyperSdk] can find the screen the user is on. */
internal object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private val activities = ArrayList<WeakReference<Activity>>()

    @Volatile
    var registered = false
        private set

    @Synchronized
    fun register(application: Application) {
        if (registered) return
        application.registerActivityLifecycleCallbacks(this)
        registered = true
    }

    // Skips finishing activities: during a result callback the child that returned is still the most recent
    fun get(): Activity? = synchronized(activities) {
        activities.firstNotNullOfOrNull { reference -> reference.get()?.takeIf { !it.isFinishing && !it.isDestroyed } }
    }

    private fun moveToFront(activity: Activity) = synchronized(activities) {
        remove(activity)
        activities.add(0, WeakReference(activity))
    }

    private fun remove(activity: Activity) {
        activities.removeAll { reference -> reference.get().let { it == null || it === activity } }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = moveToFront(activity)

    override fun onActivityStarted(activity: Activity) = moveToFront(activity)

    override fun onActivityResumed(activity: Activity) = moveToFront(activity)

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        synchronized(activities) { remove(activity) }
    }
}

/** Starts [CurrentActivity] at app start through androidx.startup, in the app's default process. */
class CurrentActivityInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        CurrentActivity.register(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
