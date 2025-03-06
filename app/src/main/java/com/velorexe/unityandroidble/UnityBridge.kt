package com.velorexe.unityandroidble

import android.content.Context
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.serialization.json.Json
import com.unity3d.player.UnityPlayer
import io.reactivex.rxjava3.core.Completable
import kotlin.properties.Delegates

import com.velorexe.unityandroidble.BleMessage.FUNCTION.INFO as INFO
import com.velorexe.unityandroidble.BleMessage.FUNCTION.CONNECTION as CONNECTION

/**
 * UnityBridge: Stellt die Verbindung zwischen Unity und der Polar BLE API her.
 * Erweiterungen: Zeitsynchronisation, parallele Verbindung von H10 und OH1,
 * HR/PPI-Streaming mit CSV-Logging, Biofeedback-Berechnung, u.a.
 */
class UnityBridge private constructor(
    private val unityActivity: Context,
    private val debugModeOn: Boolean,
    private val polarDeviceIds: Array<String>
) {
    companion object {
        //region Setup

        private var connectToOh1Disposable: Disposable? = null
        private var connectToH10Disposable: Disposable? = null
        private var connectToVsDisposable: Disposable? = null
        private var vsStreamDisposable: Disposable? = null
        private var oh1StreamDisposable: Disposable? = null
        private var H10StreamDisposable: Disposable? = null
        private var scanDisposable: Disposable? = null
        private var sdkModeEnabledStatus = false
        private var devicesConnected = 0
        private var bluetoothEnabled = false
        private lateinit var api: PolarBleApi
        private var debugModeOn by Delegates.notNull<Boolean>()
        private lateinit var polarDeviceIds: List<String>

        // Für Zeitsynchronisation
        private var pingTimestamp: Long = 0
        private var latency: Long = 0

        private const val API_LOGGER_TAG = "POLAR API LOGGER"
        private const val UI_TAG = "UI"
        private const val PERMISSION_REQUEST_CODE = 1

        // Verwaltung der gefundenen und verbundenen Geräte, initialized on first call
        private val connectablePolarDevicesInfo: HashMap<String, PolarDeviceInfo> by lazy { HashMap() }
        private val connectedPolarDevicesInfo: LinkedHashMap<String, PolarDeviceInfo> by lazy { LinkedHashMap() }
        private val connectedStreamDisposables: LinkedHashMap<String, LinkedHashMap<PolarBleApi.PolarDeviceDataType,Disposable>> by lazy { LinkedHashMap() }

        private val lock = Any()

        @Volatile
        private var initialized: Boolean = false
        //endregion
        //region Initialization

        //threadsafe singleton
        internal fun getInitializedSingletonReference(
            unityActivity: Context,
            debugModeOn: Boolean,
            polarDeviceIds: Array<String>
        ): UnityBridge.Companion {
            if (this.initialized) {
                return this
            } else {
                synchronized(lock) {
                    if (initialized) {
                        return this
                    } else {
                        initialized = true
                        api = PolarBleApiDefaultImpl.defaultImplementation(
                            unityActivity,
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
                        api.setPolarFilter(false)
                        if (debugModeOn) {
                            api.setApiLogger { s: String ->
                                BleMessage(API_LOGGER_TAG, API_LOGGER_TAG, s).sendToUnity()
                            }
                        }
                        api.setApiCallback(object : PolarBleApiCallback() {
                            override fun htsNotificationReceived(
                                identifier: String, data: PolarHealthThermometerData) {
                                BleMessage(INFO.name, identifier, "HTS data: $data").sendToUnity()
                            }
                            override fun blePowerStateChanged(powered: Boolean) {
                                BleMessage(INFO.name, "BLE_POWER", "Power state changed: $powered").sendToUnity()
                                bluetoothEnabled = powered
                            }
                            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(CONNECTION.name, polarDeviceInfo.deviceId, "Connected to:" + PolarDeviceInfoToString(polarDeviceInfo)).sendToUnity()
                                connectedPolarDevicesInfo[polarDeviceInfo.deviceId] = polarDeviceInfo
                                devicesConnected = connectedPolarDevicesInfo.size
                            }
                            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(CONNECTION.name, polarDeviceInfo.deviceId, "Connecting to:" + PolarDeviceInfoToString(polarDeviceInfo)).sendToUnity()
                            }
                            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                                connectedPolarDevicesInfo.remove(polarDeviceInfo.deviceId)
                                devicesConnected = connectedPolarDevicesInfo.size
                                BleMessage(CONNECTION.name, polarDeviceInfo.deviceId, "OnDisconnect").sendToUnity()
                            }
                            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                                BleMessage(INFO.name, identifier, "${disInfo.key}: ${disInfo.value}").sendToUnity()
                            }
                            override fun batteryLevelReceived(identifier: String, level: Int) {
                                BleMessage(INFO.name, identifier, "$identifier,$level").sendToUnity()
                            }
                        })
                        return this
                    }
                }
            }
        }
        //endregion

        //region 1. Connection

        fun connectToAll(h10DeviceId: String, oh1DeviceId: String, vSDeviceId : String) {
            try {
                api.connectToDevice(vSDeviceId)
                api.connectToDevice(oh1DeviceId)
                api.connectToDevice(h10DeviceId)
                BleMessage(CONNECTION.name, h10DeviceId,"Connecting To Devices").sendToUnity()
                BleMessage(CONNECTION.name, oh1DeviceId,"Connecting To Devices").sendToUnity()
                BleMessage(CONNECTION.name, vSDeviceId,"Connecting To Devices").sendToUnity()
            } catch (e: PolarInvalidArgument) {
                BleMessage(CONNECTION.name, h10DeviceId,"Connect error: ${e.localizedMessage}").sendToUnity()
                BleMessage(CONNECTION.name, oh1DeviceId,"Connect error: ${e.localizedMessage}").sendToUnity()
                BleMessage(CONNECTION.name, vSDeviceId,"Connect error: ${e.localizedMessage}").sendToUnity()
            }
            for (deviceID in connectedPolarDevicesInfo.keys) {
                BleMessage(CONNECTION.name, deviceID,"Connected device information: ${connectedPolarDevicesInfo[deviceID]}").sendToUnity()
            }
        }

        /**
         * Disconnect properly
         */
        fun disposeAll() {
            synchronized(lock) {
                connectedPolarDevicesInfo.forEach { (deviceId, _) ->
                    api.disconnectFromDevice(deviceId)
                }
                connectedPolarDevicesInfo.clear()
                devicesConnected = 0
                scanDisposable?.dispose()
                H10StreamDisposable?.dispose()
                oh1StreamDisposable?.dispose()
                connectToH10Disposable?.dispose()
                connectToOh1Disposable?.dispose()
            }
        }

        fun getConnectedDevicesInfo(){
            var connectedDevicesInfo = ""
            for (deviceID in connectedPolarDevicesInfo.keys) {
                connectedDevicesInfo += "Connected device information: ${PolarDeviceInfoToString(connectedPolarDevicesInfo[deviceID])}"
            }
            BleMessage(INFO.name, "ALL", connectedDevicesInfo).sendToUnity()
        }

        //endregion
        //region 2. Streams

        // Use PolarOnlineStreamingApi for general function

        fun startH10Stream(deviceId: String) {
            var H10StreamDisposable = connectedStreamDisposables[deviceId]?.get(PolarBleApi.PolarDeviceDataType.HR)
            val isDisposed = H10StreamDisposable?.isDisposed ?: true
            if (isDisposed) {
                H10StreamDisposable = api.startHrStreaming(deviceId) //TODO: Korrekter Stream?
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { hrData: PolarHrData ->
                            val timestamp = System.currentTimeMillis()
                            // Alternative: Falls gewünscht, können Timestamps in Nanosekunden erzeugt werden:
                            // val nsTimestamp = System.nanoTime() + 946684800000000000L
                            val rrValues = hrData.samples.map { it.rrsMs }
                            val rrString = if (rrValues.isNotEmpty()) rrValues.joinToString(separator = "|") else "NA"
                            val csvLine = "$timestamp,${hrData.samples.firstOrNull()?.hr ?: 0},$rrString"
                            CsvLogger.logData(csvLine)

                            val message = BleMessage(INFO.name, deviceId, "H10 HR Stream data received")
                            message.sendToUnity()

                            processBiofeedback(hrData)
                        },
                        { error: Throwable ->
                            var message : BleMessage = BleMessage(INFO.name, deviceId, "H10 HR Stream error: $error")
                            message.setError("HR stream failed: $error")
                            message.sendToUnity()
                            H10StreamDisposable?.dispose()
                        },
                        {
                            val message = BleMessage(INFO.name, deviceId, "H10 HR Stream complete (=finished)")
                            message.sendToUnity()
                            H10StreamDisposable?.dispose()
                        }
                    )
                requestFullStreamSettings(deviceId)
                connectedStreamDisposables[deviceId] = LinkedHashMap()
                connectedStreamDisposables[deviceId]!![PolarBleApi.PolarDeviceDataType.HR] = H10StreamDisposable
            }
        }


        /**
         * Startet den PPI-Stream für ein Polar OH1-Gerät.
         * Wichtige Hinweise:
         * - Wenn PPI-Aufzeichnung aktiviert ist, wird HR nur alle 5 Sekunden aktualisiert und erste Daten kommen nach ca. 25 Sekunden.
         * Annahme: PolarPpiData und PolarPpiSample besitzen folgende Felder:
         *   - ppi: Int
         *   - skinContactFlag: Int   (1 = Kontakt vorhanden)
         *   - motionDetectedFlag: Int  (1 = Bewegung erkannt)
         */
        fun startOH1Stream(deviceId: String) {
            val isDisposed = oh1StreamDisposable?.isDisposed ?: true
            if (isDisposed) {
                oh1StreamDisposable = api.startPpiStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { ppiData: PolarPpiData ->
                            val timestamp = System.currentTimeMillis()
                            val avgPpi = if (ppiData.samples.isNotEmpty()) ppiData.samples.map { it.ppi }.average() else 0.0
                            val csvLine = "$timestamp,$avgPpi"
                            CsvLogger.logData(csvLine)

                            val message = BleMessage(INFO.name, deviceId, "OH1 PPI Stream data received: ")
                            message.jsonData = Json.encodeToString(ppiData)
                            message.sendToUnity()
                        },
                        { error: Throwable ->
                            val message = BleMessage(INFO.name, deviceId, "OH1 PPI Stream error: $error")
                            message.setError("OH1 PPI stream failed: $error")
                            message.sendToUnity()
                            oh1StreamDisposable?.dispose()
                        },
                        {
                            val message = BleMessage(INFO.name, deviceId, "OH1 PPI Stream complete")
                            message.sendToUnity()
                            oh1StreamDisposable?.dispose()
                        }
                    )
                requestFullStreamSettings(deviceId)
                connectedStreamDisposables[deviceId] = LinkedHashMap()
                connectedStreamDisposables[deviceId]!![PolarBleApi.PolarDeviceDataType.PPI] =
                    oh1StreamDisposable!!
            }
        }

        /**
         * Startet den PPI-Stream für ein Polar VeritySense-Gerät.
         * Wichtige Hinweise:
         * - Wenn PPI-Aufzeichnung aktiviert ist, wird HR nur alle 5 Sekunden aktualisiert und erste Daten kommen nach ca. 25 Sekunden.
         * Annahme: PolarPpiData und PolarPpiSample besitzen folgende Felder:
         *   - ppi: Int
         *   - skinContactFlag: Int   (1 = Kontakt vorhanden)
         *   - motionDetectedFlag: Int  (1 = Bewegung erkannt)
         */
        fun startVsStream(deviceId: String) {
            val isDisposed = vsStreamDisposable?.isDisposed ?: true
            if (isDisposed) {
                vsStreamDisposable = api.startPpiStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { ppiData: PolarPpiData ->
                            val timestamp = System.currentTimeMillis()
                            val avgPpi = if (ppiData.samples.isNotEmpty()) ppiData.samples.map { it.ppi }.average() else 0.0
                            val csvLine = "$timestamp,$avgPpi"
                            CsvLogger.logData(csvLine)

                            //TODO: Notwendig?
                            val message = BleMessage(INFO.name, deviceId, "VS PPI Stream data received: ")
                            message.jsonData = Json.encodeToString(ppiData)
                            message.sendToUnity()
                        },
                        { error: Throwable ->
                            val message = BleMessage(INFO.name, deviceId, "VS PPI Stream error: $error")
                            message.setError("VS PPI stream failed: $error")
                            message.sendToUnity()
                            vsStreamDisposable?.dispose()
                        },
                        {
                            val message = BleMessage(INFO.name, deviceId, "VS PPI Stream complete")
                            message.sendToUnity()
                            vsStreamDisposable?.dispose()
                        }
                    )
                requestFullStreamSettings(deviceId)
                // Add
                connectedStreamDisposables[deviceId] = LinkedHashMap()
                connectedStreamDisposables[deviceId]!![PolarBleApi.PolarDeviceDataType.PPI] =
                    vsStreamDisposable!!
            }
        }

        fun requestFullStreamSettings(deviceId: String) {
            BleMessage(INFO.name, deviceId, "Request full stream settings called and not implemented yet.").sendToUnity()
            // requestStreamSettings oder bei VS requestFullStreamSettings von api
        }

        fun endAllConnectedDeviceStreams() {
            for (deviceInformation in connectedStreamDisposables) {
                connectedStreamDisposables[deviceInformation.key]?.values?.forEach {disposable ->
                    disposable.dispose()
                }
            }
        }
        //endregion
        //region 3. Data Processing & Log to Unity

        /**
         * Sendet eine [BleMessage] an Unity.
         *
         * @param message Die [BleMessage], die an Unity gesendet werden soll.
         * Sendet nur an das GameObject mit dem Namen "BleMessageAdapter" und ruft die Methode "OnBleMessage" auf.
         * Der Rest wird auf Unityseite gehandelt
         */
        fun sendToUnity(message: BleMessage) {
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
        }

        fun PolarDeviceInfoToString(info: PolarDeviceInfo?): String {
            if (info != null) {
                return """
                        PolarDeviceInfo:
                        Device ID: ${info.deviceId}
                        Address: ${info.address}
                        RSSI: ${info.rssi} dBm
                        Name: ${info.name}
                        Connectable: ${info.isConnectable}
                        Has Heart Rate Service: ${info.hasHeartRateService}
                        Has File System Service: ${info.hasFileSystemService}
                        """.trimIndent()
            } else {
                return "No device information available."
            }
        }


        /**
         * Berechnet einen Stress-Index basierend auf HR und HRV (RMSSD).
         * Formel (angelehnt an die Task Force der ESC, 1996):
         *     stressLevel = HR / (RMSSD + 1)
         * RMSSD: Root Mean Square of Successive Differences.
         */
        //TODO: Fix this shiit
        private fun processBiofeedback(hrData: PolarHrData) {
            val hr = hrData.samples.firstOrNull()?.hr ?: 0
            val rrIntervals = hrData.samples.map { it.rrsMs }
            if (rrIntervals.size < 2) return
            //val successiveDiffs = rrIntervals.zipWithNext { a, b -> (b - a) * (b - a) }
            //val rmssd = sqrt(successiveDiffs.average())
            //val stressLevel = hr.toDouble() / (rmssd + 1)
            //val bioFeedbackMessage = BleMessage("BIOFEEDBACK", "Stress level: $stressLevel (HR: $hr, RMSSD: $rmssd)")
            //bioFeedbackMessage.sendToUnity()
        }
        //endregion
        //region 4. Get & Set Device Information
        /**
         * Setzt (sofern unterstützt) die Gerätezeit des Polar-Geräts auf die aktuelle Systemzeit.
         * Hinweis: Bei einigen Sensoren (z. B. H10) muss der Stream vor dem Entfernen des Sensors beendet werden.
         */
        fun setDeviceTime(deviceId: String) {
            try {
                //TODO: Enum für die Commands
                BleMessage(deviceId,"TIME_SYNC", "Device time $deviceId is set to ${api.getLocalTime(deviceId)}").sendToUnity()
                // Falls die API eine Methode anbietet, z. B.:
                val calendar = java.util.Calendar.getInstance()
                api.setLocalTime(deviceId, calendar);
                BleMessage("TIME_SYNC", deviceId,  "Device time $deviceId is set " +
                        "to ${calendar.timeInMillis}, it is currently " +
                        "set to ${api.getLocalTime(deviceId)}. Most likely restart needed to apply.").sendToUnity()
            } catch (e: Exception) {
                BleMessage("TIME_SYNC", deviceId, "Error setting device time for: ${e.localizedMessage}").sendToUnity()
            }
        }

        fun getDeviceTime(deviceId: String) {
            try {
                val deviceTime = api.getLocalTime(deviceId)
                val systemTime = java.util.Calendar.getInstance()
                BleMessage("TIME_SYNC", deviceId,"Device time $deviceId is set " +
                        "to ${deviceTime}. System time is ${systemTime}.").sendToUnity()
            } catch (e: Exception) {
                BleMessage("TIME_SYNC", deviceId, "Error getting device time for $deviceId: ${e.localizedMessage}").sendToUnity()
            }
        }

        /**
         * Perform factory reset to given device.
         *
         * @param identifier Polar device ID or BT address
         * @param preservePairingInformation preserve pairing information during factory reset
         * @return [Completable] emitting success or error
         */
        fun factoryReset(identifier: String, preservePairingInformation: Boolean): Completable {
            return api.doFactoryReset(identifier, preservePairingInformation)
        }


        /**
         * Perform restart device.
         *
         * @param identifier Polar device ID or BT address
         * @return [Completable] emitting success or error
         */
        fun restart(identifier: String): Completable {
            return api.doRestart(identifier)
        }
        //endregion
    }
}
