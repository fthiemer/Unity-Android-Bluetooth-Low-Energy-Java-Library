package com.velorexe.unityandroidble

import com.unity3d.player.UnityPlayerForGameActivity

/**
 * Only creation function for [UnityBridge] creation accessible from outside this module (=from Unity).
 * Makes sure that initialization can only be done threadsafe and once from unity.
 * Also better modularity for potential multiple instances later (e.g. Polar and general).
 */
object UnityBridgeFactory {
    @JvmStatic
    fun createUnityBridge(applicationContext: UnityPlayerForGameActivity, debugModeOn: Boolean, polarDeviceIds: Array<String>): UnityBridge.Companion {
        return UnityBridge.getInitializedSingletonReference(applicationContext, debugModeOn, polarDeviceIds)
    }
}