package com.velorexe.unityandroidble

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        private var H10StreamDisposable: Disposable? = null
        private var scanDisposable: Disposable? = null
        private var connectToOh1Disposable: Disposable? = null
        private var connectToH10Disposable: Disposable? = null
        private var sdkModeEnabledStatus = false
        private var devicesConnected = 0
        private var bluetoothEnabled = false
        private lateinit var api: PolarBleApi
        private var debugModeOn by Delegates.notNull<Boolean>()
        private lateinit var polarDeviceIds: List<String>

        /**
         * Map of all connectable Polar devices found during scan.
         */
        private val connectablePolarDevicesInfo: HashMap<String, PolarDeviceInfo> by lazy {
            HashMap<String, PolarDeviceInfo>()
        }

        /**
         * Ordered map of all connected Polar devices.
         */
        private val connectedPolarDevicesInfo: LinkedHashMap<String, PolarDeviceInfo> by lazy {
            LinkedHashMap<String, PolarDeviceInfo>()
        }

        private const val API_LOGGER_TAG = "POLAR API LOGGER"
        private const val UI_TAG = "UI"
        private const val PERMISSION_REQUEST_CODE = 1

        @Volatile
        private var initialized: Boolean = false
        private val lock = Any()

        internal fun getInitializedSingletonReference(applicationContext: Context,
                                                      debugModeOn: Boolean,
                                                      polarDeviceIds: List<String>): Companion {
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
                        // also use features for non-Polar filters
                        api.setPolarFilter(false)

                        if (debugModeOn) {
                            api.setApiLogger { s: String ->
                                BleMessage(API_LOGGER_TAG, s).sendToUnity()
                            }
                        }

                        //TODO: Set Callbacks, so they send information to Unity
                        //Principle - Polar..CallbackProvider ist Typ von callbacks = helper functions
                        // Prinzip in BDEBleApiImpl -> setAPICallback setzt verantwortlichen Callbackprovider
                        // -> Callbacks sind Helperfunktionen, dann Bescheidsagen wie Devicelistener aktiv ist?
                        api.setApiCallback(object : PolarBleApiCallback() {
                            override fun blePowerStateChanged(powered: Boolean) {
                                BleMessage(API_LOGGER_TAG,"BLE power: $powered").sendToUnity()
                                bluetoothEnabled = powered
                                if (powered) {
                                    BleMessage(UI_TAG,"Phone Bluetooth on").sendToUnity()
                                } else {
                                    BleMessage(UI_TAG,"Phone Bluetooth off").sendToUnity()
                                }
                            }

                            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(API_LOGGER_TAG, "CONNECTED: ${polarDeviceInfo.deviceId}").sendToUnity()
                                connectedPolarDevicesInfo[polarDeviceInfo.deviceId] = polarDeviceInfo
                                devicesConnected = connectedPolarDevicesInfo.size
                            }

                            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(API_LOGGER_TAG, "CONNECTING: ${polarDeviceInfo.deviceId}").sendToUnity()
                            }

                            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                                connectedPolarDevicesInfo.remove(polarDeviceInfo.deviceId)
                                devicesConnected = connectedPolarDevicesInfo.size
                                BleMessage(API_LOGGER_TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}").sendToUnity()
                            }

                            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                                BleMessage(API_LOGGER_TAG, "identifier: $identifier value: $disInfo").sendToUnity()
                            }

                            override fun batteryLevelReceived(identifier: String, level: Int) {
                                BleMessage(API_LOGGER_TAG, "BATTERY LEVEL: $level").sendToUnity()
                            }
                        })
                        return this
                    }
                }
            }
        }

        /**
         * Check Permissions and initialization if not already done somewhere else
         * - Right now done in AndroidManifest.xml
         */



        /**
         * Scan for all available Devices.
         * Actualize DeviceList.
         * Send all necessary Device Information to Unity.
         */
        fun scanForDevices(polarOnly: Boolean) {
            synchronized(lock) {
                api.setPolarFilter(polarOnly)
                scanDisposable?.dispose()

                scanDisposable = api.searchForDevice()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { polarDeviceInfo: PolarDeviceInfo ->
                            connectablePolarDevicesInfo[polarDeviceInfo.deviceId] = polarDeviceInfo
                            val message = BleMessage("SCAN_FOR_DEVICE", "Device Found").apply {
                                device = polarDeviceInfo.deviceId
                                name = polarDeviceInfo.name
                                deviceInfo = polarDeviceInfo
                            }
                            message.sendToUnity()
                        },
                        { error: Throwable ->
                            val errorMessage = BleMessage("SCAN_FOR_DEVICE_ERROR", "Scan Failed").apply {
                                setError("Device scan failed. Reason: $error")
                            }
                            errorMessage.sendToUnity()
                        },
                        {
                            val completeMessage = BleMessage("SCAN_FOR_DEVICE_COMPLETE", "Complete")
                            completeMessage.sendToUnity()
                            scanDisposable?.dispose()
                        }
                    )
            }
        }



        /**
         * Connect to a device via Polar API, invoking related callback.
         *
         * @param deviceId Polar device id found printed on the sensor/ device or bt address (in format "00:11:22:33:44:55"
         */
        fun connectToDevice(deviceId: String) {
            try {
                api.connectToDevice(deviceId)
            } catch (polarInvalidArgument: PolarInvalidArgument) {
                val message = BleMessage(API_LOGGER_TAG,"Connect To Device")
                message.setError("Failed to connect. Reason $polarInvalidArgument")
                message.sendToUnity()
            }
        }
        
        /**
         * Use the autoconnect feature of the Polar SDK to connect to a device by device type.
         * Invoking related callback.
         */
        fun connectToPolarDeviceOfType(deviceType: String, rssiLimit : Int = -60, service: String? = "180D") {
            if (deviceType == "OH1") {
                connectToOh1Disposable = api.autoConnectToDevice(rssiLimit, service, deviceType)
                    .subscribe(
                        { val message = BleMessage(API_LOGGER_TAG,"Connect To Device Of Type $deviceType")
                            message.sendToUnity()
                            connectToOh1Disposable?.dispose()
                        },
                        {throwable: Throwable ->
                            val message = BleMessage(API_LOGGER_TAG,"Connect To Device Of Type $deviceType")
                            message.setError("Failed to connect. Reason: $throwable")
                            message.sendToUnity()
                            connectToOh1Disposable?.dispose()
                        }
                    )
            } else if (deviceType == "H10") {
                connectToH10Disposable = api.autoConnectToDevice(rssiLimit, service, deviceType)
                    .subscribe(
                        { val message = BleMessage(API_LOGGER_TAG,"Connect To Device Of Type $deviceType")
                            message.sendToUnity()
                            connectToH10Disposable?.dispose()
                        },
                        {throwable: Throwable ->
                            val message = BleMessage(API_LOGGER_TAG,"Connect To Device Of Type $deviceType")
                            message.setError("Failed to connect. Reason: $throwable")
                            message.sendToUnity()
                            connectToH10Disposable?.dispose()
                        }
                    )
            } else {
                val message = BleMessage(API_LOGGER_TAG,"Connect To Device")
                message.setError("Failed to connect. Reason: Device $deviceType not supported.")
                message.sendToUnity()
            }
        }


        /**
         * Stream HR and RR Intervalls from Polar H10.
         */
        fun startH10Stream(deviceId: String) {
            val isDisposed = H10StreamDisposable?.isDisposed ?: true
            if (isDisposed) {
                H10StreamDisposable = api.startHrStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { hrData: PolarHrData ->
                            val message = BleMessage(API_LOGGER_TAG,"H10 HR Stream data")
                            message.jsonData = Json.encodeToString(hrData)
                            message.sendToUnity()
                        },
                        { error: Throwable ->
                            val message = BleMessage(API_LOGGER_TAG,"H10 HR Stream Error")
                            message.setError("HR stream failed. Reason $error")
                            message.sendToUnity()
                            H10StreamDisposable?.dispose()
                        },
                        { val message = BleMessage(API_LOGGER_TAG,"H10 HR Stream Complete")
                            message.sendToUnity()
                            H10StreamDisposable?.dispose()
                        }
                    )
                }
        }




        /**
         * Reconnect if stream is lost.
          */


        /**
         * Iterate through all connected devices. Close all streams. Disconnect from all. Dispose all disposables.
         */
        fun disposeAll() {
            synchronized(lock) {
                connectedPolarDevicesInfo.forEach { (deviceId, _) ->
                    api.disconnectFromDevice(deviceId)
                }
                connectedPolarDevicesInfo.clear()
                devicesConnected = 0
                scanDisposable?.dispose()
            }
        }
    }
}
