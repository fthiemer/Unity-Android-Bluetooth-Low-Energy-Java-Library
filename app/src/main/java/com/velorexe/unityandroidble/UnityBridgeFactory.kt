package com.velorexe.unityandroidble

import android.content.Context

/**
 * Only creation function for [UnityBridge] creation accessible from outside this module (=from Unity).
 * Makes sure that initialization can only be done threadsafe and once from unity.
 * Also better modularity for potential multiple instances later (e.g. Polar and general).
 */
object UnityBridgeFactory {
    fun createUnityBridge(applicationContext: Context, debugModeOn: Boolean, polarDeviceIds: List<String>): UnityBridge.Companion {
        return UnityBridge.getSingletonReference(applicationContext, debugModeOn, polarDeviceIds)
    }
}