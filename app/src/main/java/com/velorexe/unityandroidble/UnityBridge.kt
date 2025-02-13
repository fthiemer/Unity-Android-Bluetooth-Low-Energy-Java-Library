package com.velorexe.unityandroidble

import android.content.Context
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.serialization.json.Json
import com.unity3d.player.UnityPlayer
import kotlin.properties.Delegates

/**
 * UnityBridge: Stellt die Verbindung zwischen Unity und der Polar BLE API her.
 * Erweiterungen: Zeitsynchronisation, parallele Verbindung von H10 und OH1,
 * HR/PPI-Streaming mit CSV-Logging, Biofeedback-Berechnung, u.a.
 */
class UnityBridge private constructor(
    private val unityActivity: Context,
    private val debugModeOn: Boolean,
    private val polarDeviceIds: List<String>
) {
    companion object {
        private var H10StreamDisposable: Disposable? = null
        private var oh1StreamDisposable: Disposable? = null
        private var scanDisposable: Disposable? = null
        private var connectToOh1Disposable: Disposable? = null
        private var connectToH10Disposable: Disposable? = null
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



        @Volatile
        private var initialized: Boolean = false
        private val lock = Any()
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
                            override fun blePowerStateChanged(powered: Boolean) {
                                BleMessage("INFO","BLE_POWER", "$powered").sendToUnity()
                                bluetoothEnabled = powered
                            }
                            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(polarDeviceInfo.deviceId, "CONNECT", "OnConnect").sendToUnity()
                                connectedPolarDevicesInfo[polarDeviceInfo.deviceId] = polarDeviceInfo
                                devicesConnected = connectedPolarDevicesInfo.size
                            }
                            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                                BleMessage(polarDeviceInfo.deviceId, "CONNECT", "OnConnecting").sendToUnity()
                            }
                            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                                connectedPolarDevicesInfo.remove(polarDeviceInfo.deviceId)
                                devicesConnected = connectedPolarDevicesInfo.size
                                BleMessage(polarDeviceInfo.deviceId, "CONNECT", "OnDisconnect").sendToUnity()
                            }
                            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                                BleMessage("INFO", "DIS", "identifier: $identifier, value: $disInfo").sendToUnity()
                            }
                            override fun batteryLevelReceived(identifier: String, level: Int) {
                                BleMessage("INFO", "BATTERY_LEVEL", "$level").sendToUnity()
                            }
                        })
                        return this
                    }
                }
            }
        }

        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // 1. Zeitsynchronisation & SetLocalTime
        // –––––––––––––––––––––––––––––––––––––––––––––––––––
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
                BleMessage("TIME_SYNC", "Try to set device time for $deviceId " +
                        "to ${calendar.timeInMillis}, it is currently " +
                        "set to ${api.getLocalTime(deviceId)}. Most likely restart needed to apply.", "XYZ").sendToUnity()
            } catch (e: Exception) {
                BleMessage("TIME_SYNC", "Error setting device time for $deviceId: ${e.localizedMessage}", "XYZ").sendToUnity()
            }
        }

        fun getDeviceTime(deviceId: String) {
            try {
                val deviceTime = api.getLocalTime(deviceId)
                val systemTime = java.util.Calendar.getInstance()
                BleMessage("TIME_SYNC", "Device time $deviceId is set " +
                        "to ${deviceTime}. System time is ${systemTime}.", "XYZ").sendToUnity()
            } catch (e: Exception) {
                BleMessage("TIME_SYNC", "Error getting device time for $deviceId: ${e.localizedMessage}", "XYZ").sendToUnity()
            }
        }

//        /**
//         * Sendet einen Ping an Unity, um die Latenz (Ping-Pong-Zeit) zu ermitteln.
//         */
//        fun synchronizeTime() {
//            pingTimestamp = System.currentTimeMillis()
//            val pingMessage = BleMessage("TIME_SYNC", "Ping")
//            pingMessage.jsonData = pingTimestamp.toString()
//            sendToUnity(pingMessage)
//        }
//
//        /**
//         * Wird von Unity aufgerufen, wenn ein Pong empfangen wurde.
//         * unityTime: Der Zeitstempel von Unity.
//         */
//        fun onUnityPong(unityTime: Long) {
//            val currentTime = System.currentTimeMillis()
//            latency = (currentTime - pingTimestamp) / 2  // One-Way-Latenz
//            BleMessage("TIME_SYNC", "Latency: $latency ms").sendToUnity()
//        }

        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // 2. Gleichzeitiger Verbindungsaufbau zu H10 und OH1
        // –––––––––––––––––––––––––––––––––––––––––––––––––––

        fun connectToH10AndOH1(h10DeviceId: String, oh1DeviceId: String) {
            try {
                api.connectToDevice(h10DeviceId)
                api.connectToDevice(oh1DeviceId)
                BleMessage(API_LOGGER_TAG, "Connecting to H10 and OH1", "XYZ").sendToUnity()
            } catch (e: PolarInvalidArgument) {
                BleMessage(API_LOGGER_TAG, "Connect error: ${e.localizedMessage}", "XYZ").sendToUnity()
            }
            for (deviceInformation in connectedPolarDevicesInfo) {
                BleMessage(API_LOGGER_TAG, "Connected device information: $deviceInformation.", "XYZ").sendToUnity()
            }
        }

        fun endAllConnectedDeviceStreams() {
            for (deviceInformation in connectedStreamDisposables) {
                connectedStreamDisposables[deviceInformation.key]?.values?.forEach {disposable ->
                    disposable.dispose()
                }
            }
        }


        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // 3. HR-Streaming (H10), CSV-Logging & Biofeedback (Stress-Erfassung)
        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        //TODO: Setup so, dass ich wieder reconnecten kann
        fun startH10Stream(deviceId: String) {
            var H10StreamDisposable = connectedStreamDisposables[deviceId]?.get(PolarBleApi.PolarDeviceDataType.HR)
            val isDisposed = H10StreamDisposable?.isDisposed ?: true
            if (isDisposed) {
                H10StreamDisposable = api.startHrStreaming(deviceId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { hrData: PolarHrData ->
                            // Für HR/PPI in H10: Hinweis – wenn PPI aktiviert ist, werden HR-Daten nur alle 5 Sekunden aktualisiert.
                            val timestamp = System.currentTimeMillis()
                            // Alternative: Falls gewünscht, können Timestamps in Nanosekunden erzeugt werden:
                            // val nsTimestamp = System.nanoTime() + 946684800000000000L
                            val rrValues = hrData.samples.map { it.rrsMs }
                            val rrString = if (rrValues.isNotEmpty()) rrValues.joinToString(separator = "|") else "NA"
                            val csvLine = "$timestamp,${hrData.samples.firstOrNull()?.hr ?: 0},$rrString"
                            CsvLogger.logData(csvLine)

                            val message = BleMessage(API_LOGGER_TAG, "H10 HR Stream data", "XYZ")
                            message.jsonData = Json.encodeToString(hrData)
                            message.sendToUnity()

                            processBiofeedback(hrData)
                        },
                        { error: Throwable ->
                            val message = BleMessage(API_LOGGER_TAG, "H10 HR Stream Error", "XYZ")
                            message.setError("HR stream failed: $error")
                            message.sendToUnity()
                            H10StreamDisposable?.dispose()
                        },
                        {
                            val message = BleMessage(API_LOGGER_TAG, "H10 HR Stream Complete", "XYZ")
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

        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // 4. OH1 Streaming (PPI)
        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        /**
         * Startet den PPI-Stream für ein Polar OH1-Gerät.
         * Wichtige Hinweise:
         * - Wenn PPI-Aufzeichnung aktiviert ist, wird HR nur alle 5 Sekunden aktualisiert und erste Daten kommen nach ca. 25 Sekunden.
         *
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
                            //TODO: Synchronisation überdenken, wahrshcl. nicht sinnvoll local time des devices zu nehmeen
                            val timestamp = System.currentTimeMillis()
                            // Beispielhaft: Berechne den Durchschnitt der PPI-Werte
                            val avgPpi = if (ppiData.samples.isNotEmpty()) ppiData.samples.map { it.ppi }.average() else 0.0
                            val csvLine = "$timestamp,$avgPpi"
                            CsvLogger.logData(csvLine)

                            val message = BleMessage(API_LOGGER_TAG, "OH1 PPI Stream data", "XYZ")
                            message.jsonData = Json.encodeToString(ppiData)
                            message.sendToUnity()

                            // Optional: Hier können zusätzliche Biofeedback-Berechnungen für OH1 erfolgen.
                        },
                        { error: Throwable ->
                            val message = BleMessage(API_LOGGER_TAG, "OH1 PPI Stream Error", "XYZ")
                            message.setError("OH1 PPI stream failed: $error")
                            message.sendToUnity()
                            oh1StreamDisposable?.dispose()
                        },
                        {
                            val message = BleMessage(API_LOGGER_TAG, "OH1 PPI Stream Complete", "XYZ")
                            message.sendToUnity()
                            oh1StreamDisposable?.dispose()
                        }
                    )
                requestFullStreamSettings(deviceId)
                connectedStreamDisposables[deviceId] = LinkedHashMap()
                connectedStreamDisposables[deviceId]!![PolarBleApi.PolarDeviceDataType.HR] =
                    oh1StreamDisposable!!
            }
        }

        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // 5. Request Full Stream Settings (Stub)
        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        fun requestFullStreamSettings(deviceId: String) {
            BleMessage("STREAM_SETTINGS", "Requested full stream settings for $deviceId", "XYZ").sendToUnity()
        }

        // –––––––––––––––––––––––––––––––––––––––––––––––––––
        // Dispose: Aufräumarbeiten
        // –––––––––––––––––––––––––––––––––––––––––––––––––––
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

        fun sendToUnity(message: BleMessage) {
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
        }
    }
}
