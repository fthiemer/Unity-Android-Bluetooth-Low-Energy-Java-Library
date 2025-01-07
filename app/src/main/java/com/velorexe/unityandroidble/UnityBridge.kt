package com.velorexe.unityandroidble

import android.content.Context
import android.util.Log
import com.polar.androidblesdk.MainActivity.Companion.TAG
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.unity3d.player.UnityPlayer

import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID
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
        private var scanDisposable: Disposable? = null
        private var deviceConnected: Boolean = false
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
                        //TODO: Check Debugging in Unity
                        if (debugModeOn) {
                            api.setApiLogger { s: String ->
                                sendToUnity(BleMessage("API-Logger", s))
                            }
                        }
                        //TODO: Set Callbacks, so they send information to Unity
                        /* Principle - Polar..CallbackProvider ist Typ von callbacks = helper functions
                        // Prinzip in BDEBleApiImpl -> setAPICallback setzt verantwortlichen Callbackprovider
                        // -> Callbacks sind Helperfunktionen, dann Bescheidsagen wie Devicelistener aktiv ist?
                        //Hmm.. naja Details
                        // override fun in Kotlin verstehen
                        //
                        api.setApiCallback(object : PolarBleApiCallback() {

                            override fun blePowerStateChanged(powered: Boolean) {
                                Log.d(TAG, "BLE power: $powered")
                                bluetoothEnabled = powered
                                if (powered) {
                                    enableAllButtons()
                                    showToast("Phone Bluetooth on")
                                } else {
                                    disableAllButtons()
                                    showToast("Phone Bluetooth off")
                                }
                            }

                            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                                deviceId = polarDeviceInfo.deviceId
                                deviceConnected = true
                                val buttonText = getString(R.string.disconnect_from_device, deviceId)
                                toggleButtonDown(connectButton, buttonText)
                            }

                            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
                            }

                            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                                deviceConnected = false
                                val buttonText = getString(R.string.connect_to_device, deviceId)
                                toggleButtonUp(connectButton, buttonText)
                                toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
                            }

                            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
                            }

                            override fun batteryLevelReceived(identifier: String, level: Int) {
                                Log.d(TAG, "BATTERY LEVEL: $level")
                            }

                            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                                // deprecated
                            }
                        })*/
                        return this
                    }
                }
            }
        }

        /**
         * Check Permissions if not already done somewhere else - Right now done in AndroidManifest.xml
         */


        /**
         * Scan for Devices
         */
        fun scanForDevices () {
            //TODO: Disposable verstehen und gucken ob Elvis Operator hier notwendig
            // Disposable scheint mit einer Funktione beladen zu werden. Sobald das
            // Disposable disposed wird, wird
            // TODO: Logs mit UnityMessages ersetzen
            val isDisposed = scanDisposable?.isDisposed ?: true
            if (isDisposed) {
                scanDisposable = api.searchForDevice()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarDeviceInfo: PolarDeviceInfo ->
                            Log.d("scanForDevice", "polar device found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable)
                        },
                        { error: Throwable ->
                            Log.e("scanForDevice", "Device scan failed. Reason $error")
                        },
                        {
                            Log.d("scanForDevice", "complete")
                        }
                    )
            } else {
                toggleButtonUp(scanButton, "Scan devices")
                scanDisposable?.dispose()
            }
        }

        /**
         * Connect to Device
         */
        fun connectToDevice(deviceId: String) {
            //TODO: Scheint sich nur zu einem Device zu connecten. Ich brauche aber 3 parallel XD
            //      Über threads regeln?
            try {
                if (deviceConnected) {
                    api.disconnectFromDevice(deviceId)
                } else {
                    api.connectToDevice(deviceId)
                }
            } catch (polarInvalidArgument: PolarInvalidArgument) {
                val attempt = if (deviceConnected) {
                    "disconnect"
                } else {
                    "connect"
                }
                //TODO: Funktioniert Log.e für Unity auch?
                Log.e("connectToDevice", "Failed to $attempt. Reason $polarInvalidArgument ")
            }
        }



        /**
        * Sends a [BleMessage] to Unity.
         *
         * @param message The [BleMessage] to send to Unity.
         */
        fun sendToUnity(message: BleMessage) {
            if (message.id.isNotEmpty() && message.command.isNotEmpty()) {
                UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
            } else {
                message.setError(if (message.id.isEmpty()) "Task ID is empty." else "Command is empty.")
                UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
            }
        }
    }
}
