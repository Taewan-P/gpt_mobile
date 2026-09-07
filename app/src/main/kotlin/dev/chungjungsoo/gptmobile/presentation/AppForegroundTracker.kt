package dev.chungjungsoo.gptmobile.presentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlin.math.max

object AppForegroundTracker : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0

    val isBackgrounded: Boolean
        get() = startedActivities == 0

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = max(0, startedActivities - 1)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
