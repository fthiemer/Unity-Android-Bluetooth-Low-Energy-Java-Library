package com.velorexe.unityandroidble

import android.Manifest
import android.content.Context
import android.os.Build
import com.polar.sdk.api.PolarBleApi
import com.unity3d.player.UnityPlayer

import android.util.Log
import com.polar.androidblesdk.MainActivity
import com.polar.androidblesdk.MainActivity.Companion
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.*
import java.util.*
import kotlin.properties.Delegates


/**
 * Instance created by [UnityBridgeFactory] to make Unity able to access relevant PolarSDK functions
 */
class UnityBridge private constructor(
    private val applicationContext: Context,
    private val debugModeOn: Boolean,
    private val polarDeviceIds: List<String>
) {
    companion object {
        private lateinit var api: PolarBleApi
        private var debugModeOn by Delegates.notNull<Boolean>()
        private lateinit var polarDeviceIds: List<String>

        private const val API_LOGGER_TAG = "POLAR API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1

        @Volatile
        private var initialized: Boolean = false
        private val lock = Any()

        internal fun getSingletonReference(applicationContext: Context,
                                 debugModeOn: Boolean,
                                 polarDeviceIds: List<String>): UnityBridge.Companion {
            // Make sure instance is not already initialized, then initialize threadsafe
            if (this.initialized) {
                return this
            } else {
                synchronized(lock) {
                    if (initialized) {
                        return this
                    } else {
                        initialized = true
                        //Set variables
                        api = PolarBleApiDefaultImpl.defaultImplementation(
                            applicationContext,
                            setOf(
                                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
                            )
                        )
                        this.debugModeOn = debugModeOn
                        // use features for non-Polar filters
                        api.setPolarFilter(false)
                        //TODO: Enable debugging to unity via function
                        //if (debugModeOn) {
                        //api.setApiLogger { s: String -> }
                        return this
                    }
                }
            }
        }

        /**
         * Check Permissions if not already done somewhere else
         */
        fun requestBLEPermissions() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                    MainActivity.PERMISSION_REQUEST_CODE
                )
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MainActivity.PERMISSION_REQUEST_CODE
                )
            }
        }

        /**
         * Scan for Devices
         */

    }
}
