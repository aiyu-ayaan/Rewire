package com.aiyu.rewire.core.update

import androidx.activity.ComponentActivity

/** Sideloaded builds (full, lite) update through the GitHub [AppUpdater]; Play's update screen is the play flavors'. */
object PlayUpdates {
    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: ComponentActivity, offer: Boolean) = Unit
}
