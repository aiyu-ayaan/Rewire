package com.aiyu.rewire.core.update

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play builds (play, playLite): Google Play's own update screen replaces the GitHub updater. No permission needed.
 * Offered once per launch; an update the user already started is resumed on every return. No Play = silent no-op.
 */
object PlayUpdates {
    fun attach(activity: ComponentActivity, offer: Boolean) {
        val manager = AppUpdateManagerFactory.create(activity)
        val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { } // cancel = keep using this version
        var offerPending = offer
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val inProgress = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if ((inProgress || (available && offerPending)) && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    offerPending = false
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
                }
            }
        })
    }
}
