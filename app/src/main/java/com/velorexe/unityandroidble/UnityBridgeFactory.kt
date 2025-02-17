package com.velorexe.unityandroidble

import android.content.Context
import android.widget.Toast
import com.unity3d.player.UnityPlayer

/**
 * Only creation function for [UnityBridge] creation accessible from outside this module (=from Unity).
 * Makes sure that initialization can only be done threadsafe and once from unity.
 * Also better modularity for potential multiple instances later (e.g. Polar and general).
 */
object UnityBridgeFactory {
    @JvmStatic
    fun createUnityBridge(unityActivity: Context, debugModeOn: Boolean, polarDeviceIds: Array<String>): UnityBridge.Companion {
        return UnityBridge.getInitializedSingletonReference(unityActivity, debugModeOn, polarDeviceIds)
    }
    @JvmStatic
    fun test(unityActivity: Context) {
        Toast(unityActivity).apply {
            setText("Test")
            show()
        }
    }
}