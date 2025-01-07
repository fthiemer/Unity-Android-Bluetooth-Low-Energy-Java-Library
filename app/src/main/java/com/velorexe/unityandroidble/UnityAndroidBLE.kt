package com.velorexe.unityandroidble

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.unity3d.player.UnityPlayer
import com.velorexe.unityandroidble.connecting.BluetoothLeService
import com.velorexe.unityandroidble.searching.LeDeviceListAdapter
import com.velorexe.unityandroidble.searching.LeScanCallback
import java.util.UUID

/**
 * UnityAndroidBLE is a Singleton class that provides methods to interact with Bluetooth Low Energy (BLE) devices from a Unity application on Android.
 * It handles scanning, connecting, reading, writing, and subscribing to BLE characteristics.
 */
class UnityAndroidBLE {
    private val mBluetoothAdapter: BluetoothAdapter
    private val mBluetoothLeScanner: BluetoothLeScanner

    private val SDK_INT = Build.VERSION.SDK_INT

    private var mIsScanning = false
    private val mHandler: Handler

    private val mScanCallback: LeScanCallback
    private val mDeviceListAdapter = LeDeviceListAdapter()
    private val mConnectedServers: MutableMap<BluetoothDevice, BluetoothLeService>

    init {
        val ctx = UnityPlayer.currentActivity.applicationContext

        val mBluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = mBluetoothManager.adapter

        mBluetoothLeScanner = mBluetoothAdapter.bluetoothLeScanner

        if (SDK_INT <= Build.VERSION_CODES.S && ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            !mBluetoothAdapter.isEnabled
        ) {
            mBluetoothAdapter.enable()
        } else {
            //TODO: Ask user to activate bluetooth - test this
            if (!mBluetoothAdapter.isEnabled) {
                // Request to enable Bluetooth
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                UnityPlayer.currentActivity.startActivityForResult(enableBtIntent, 1)
            }
        }

        // Setup for scanning BLE devices
        mHandler = Handler(Looper.getMainLooper())
        mScanCallback = LeScanCallback(
            mDeviceListAdapter,
            this
        )

        val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val deviceRssi =
                        intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)

                    if (device != null && deviceRssi != Short.MIN_VALUE) {
                        mDeviceListAdapter.setOrAdd(device, deviceRssi)
                    }
                }
            }
        }

        ctx.registerReceiver(mReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        mConnectedServers = HashMap()
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun searchForBleDevices(taskId: String?, scanPeriod: Int) {
        if (!mIsScanning) {
            mScanCallback.setCallbackId(taskId)
            mHandler.postDelayed({
                mIsScanning = false
                mBluetoothLeScanner.stopScan(mScanCallback)

                val message = BleMessage(taskId, "searchStop")
                sendTaskResponse(message)
            }, scanPeriod.toLong())

            mBluetoothLeScanner.startScan(mScanCallback)
            mBluetoothAdapter.startDiscovery()

            mIsScanning = true
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun searchForBleDevicesWithFilter(
        taskId: String?, scanPeriod: Int,
        deviceUuid: String,
        deviceName: String,
        serviceUuid: String
    ) {
        if (!mIsScanning) {
            mScanCallback.setCallbackId(taskId)
            mHandler.postDelayed({
                mIsScanning = false
                mBluetoothLeScanner.stopScan(mScanCallback)

                val message = BleMessage(taskId, "searchStop")
                sendTaskResponse(message)
            }, scanPeriod.toLong())

            val filter = ScanFilter.Builder()

            if (!deviceUuid.isEmpty()) {
                filter.setDeviceAddress(deviceUuid)
            }

            if (!deviceName.isEmpty()) {
                filter.setDeviceName(deviceName)
            }

            if (!serviceUuid.isEmpty()) {
                filter.setServiceUuid(ParcelUuid.fromString(serviceUuid))
            }

            val settings = ScanSettings.Builder()

            val scanFilters: MutableList<ScanFilter> = ArrayList()
            scanFilters.add(filter.build())

            mBluetoothLeScanner.startScan(scanFilters, settings.build(), mScanCallback)
            mBluetoothAdapter.startDiscovery()

            mIsScanning = true
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused")
            /**
             * Get RSSI of BLE device
             * The Received Signal Strength Indicator (RSSI) is a measure of the power level at the receiver.
             * It's measured in decibels, dBm, on a logarithmic scale and is negative.
             * A more negative number indicates the device is further away.
             * For example, a value of -20 to -30 dBm indicates the device is close while a value of -120 indicates the device is near the limit of detection.
             */
    // UnityAndroidBLE can't be created without the proper Permissions
    fun getRssiForDevice(taskId: String?, deviceAddress: String?) {
        val device = mDeviceListAdapter.getItem(deviceAddress)

        if (device != null) {
            val rssi = mDeviceListAdapter.getRssi(device)

            val msg = BleMessage(taskId, "getRssiForDevice")
            msg.device = device.address
            msg.name = device.name
            msg.base64Data = rssi.toString() + ""
            sendTaskResponse(msg)
        } else {
            val msg = BleMessage(taskId, "getRssiForDevice")
            msg.setError("Can't connect to a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun connectToBleDevice(taskId: String?, macAddress: String?, transport: Int) {
        val device = mDeviceListAdapter.getItem(macAddress)

        if (device != null) {
            val leService = BluetoothLeService(this, taskId)
            device.connectGatt(
                UnityPlayer.currentActivity.applicationContext,
                false,
                leService.GattCallback,
                transport
            )

            mConnectedServers[device] = leService
        } else {
            val msg = BleMessage(taskId, "connectToDevice")
            msg.setError("Can't connect to a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun disconnectFromBleDevice(taskId: String?, macAddress: String?) {
        val device = mDeviceListAdapter.getItem(macAddress)
        val msg = BleMessage(taskId, "disconnectedFromDevice")

        if (device != null) {
            val service = mConnectedServers[device]

            if (service != null) {
                service.DeviceGatt.disconnect()
                mConnectedServers.remove(device)

                msg.device = macAddress
                msg.name = device.name
            } else {
                msg.setError("Can't disconnect from BluetoothDevice if no proper connection has been made yet.")
            }
        } else {
            msg.setError("Can't disconnect from BluetoothDevice that hasn't been discovered yet.")
        }

        sendTaskResponse(msg)
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun changeMtuSize(taskId: String?, macAddress: String?, mtuSize: Int) {
        val device = mDeviceListAdapter.getItem(macAddress)

        if (device != null) {
            val leService = mConnectedServers[device]

            if (leService != null) {
                if (leService.DeviceGatt.requestMtu(mtuSize)) {
                    val msg = BleMessage(taskId, "requestMtuSize")

                    msg.device = device.address
                    msg.name = device.name

                    sendTaskResponse(msg)

                    leService.registerMtuSizeTask(taskId)
                } else {
                    val msg = BleMessage(taskId, "requestMtuSize")
                    msg.setError("Couldn't set the MTU size of the BluetoothDevice.")

                    sendTaskResponse(msg)
                }
            } else {
                val msg = BleMessage(taskId, "requestMtuSize")
                msg.setError("Can't set the MTU size of a BluetoothDevice that hasn't been connected to the device.")

                sendTaskResponse(msg)
            }
        } else {
            val msg = BleMessage(taskId, "requestMtuSize")
            msg.setError("Can't set the MTU size of a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun readFromCharacteristic(
        taskId: String?,
        deviceUuid: String?,
        serviceUuid: String?,
        characteristicUuid: String?
    ) {
        val device = mDeviceListAdapter.getItem(deviceUuid)

        if (device != null) {
            val leService = mConnectedServers[device]

            if (leService?.DeviceGatt != null) {
                val gatt = leService.DeviceGatt

                val service = gatt.getService(UUID.fromString(serviceUuid))
                val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))

                // Something goes wrong with reading if this is false
                if (gatt.readCharacteristic(characteristic)) {
                    if (SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        characteristic.value
                    } else {
                        gatt.readCharacteristic(characteristic)
                    }

                    leService.registerRead(characteristic, taskId)
                } else {
                    val msg = BleMessage(taskId, "readFromCharacteristic")
                    msg.setError("Can't read from Characteristic, are you sure the Characteristic is readable?")

                    sendTaskResponse(msg)
                }
            } else {
                val msg = BleMessage(taskId, "readFromCharacteristic")
                msg.setError("Can't write to a Characteristic of a BluetoothDevice that isn't connected to the device.")

                sendTaskResponse(msg)
            }
        } else {
            val msg = BleMessage(taskId, "readFromCharacteristic")
            msg.setError("Can't write to a Characteristic of a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun writeToCharacteristic(
        taskId: String?,
        deviceUuid: String?,
        serviceUuid: String?,
        characteristicUuid: String?,
        data: ByteArray
    ) {
        val device = mDeviceListAdapter.getItem(deviceUuid)

        if (device != null) {
            val leService = mConnectedServers[device]

            if (leService?.DeviceGatt != null) {
                val gatt = leService.DeviceGatt

                val service = gatt.getService(UUID.fromString(serviceUuid))
                val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))

                if (SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.setValue(data)
                    gatt.writeCharacteristic(characteristic)
                } else {
                    gatt.writeCharacteristic(
                        characteristic,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                }

                leService.registerWrite(characteristic, taskId)
            } else {
                val msg = BleMessage(taskId, "writeToCharacteristic")
                msg.setError("Can't write to a Characteristic of a BluetoothDevice that isn't connected to the device.")

                sendTaskResponse(msg)
            }
        } else {
            val msg = BleMessage(taskId, "writeToCharacteristic")
            msg.setError("Can't write to a Characteristic of a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
    fun subscribeToCharacteristic(
        taskId: String?,
        deviceUuid: String?,
        serviceUuid: String?,
        characteristicUuid: String?
    ) {
        val device = mDeviceListAdapter.getItem(deviceUuid)

        if (device != null) {
            val leService = mConnectedServers[device]

            if (leService?.DeviceGatt != null) {
                val gatt = leService.DeviceGatt

                val service = gatt.getService(UUID.fromString(serviceUuid))
                val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))

                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                // If either of these values is false, something went wrong
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) && gatt.writeDescriptor(
                        descriptor
                    ) && gatt.setCharacteristicNotification(characteristic, true)
                ) {
                    val msg = BleMessage(taskId, "subscribeToCharacteristic")
                    sendTaskResponse(msg)

                    leService.registerSubscribe(characteristic, taskId)
                } else {
                    val msg = BleMessage(taskId, "subscribeToCharacteristic")
                    msg.setError("Can't subscribe to Characteristic, are you sure the Characteristic has Notifications or Indicate properties?")

                    sendTaskResponse(msg)
                }
            } else {
                val msg = BleMessage(taskId, "subscribeToCharacteristic")
                msg.setError("Can't subscribe to a Characteristic of a BluetoothDevice that isn't connected to the device.")

                sendTaskResponse(msg)
            }
        } else {
            val msg = BleMessage(taskId, "subscribeToCharacteristic")
            msg.setError("Can't subscribe to a Characteristic of a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("unused") // UnityAndroidBLE can't be created without the proper Permissions
            /** <summary>
             * Unsubscribes from a BLE Characteristic.
            </summary> */
    fun unsubscribeFromCharacteristic(
        taskId: String?,
        deviceUuid: String?,
        serviceUuid: String?,
        characteristicUuid: String?
    ) {
        val device = mDeviceListAdapter.getItem(deviceUuid)

        if (device != null) {
            val leService = mConnectedServers[device]

            if (leService?.DeviceGatt != null) {
                val gatt = leService.DeviceGatt

                val service = gatt.getService(UUID.fromString(serviceUuid))
                val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))

                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                // If either of these values is false, something went wrong
                if (descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) && gatt.writeDescriptor(
                        descriptor
                    ) && gatt.setCharacteristicNotification(characteristic, false)
                ) {
                    val msg = BleMessage(taskId, "unsubscribeToCharacteristic")
                    sendTaskResponse(msg)

                    leService.unregisterSubscribe(characteristic)
                } else {
                    val msg = BleMessage(taskId, "unsubscribeToCharacteristic")
                    msg.setError("Can't unsubscribe from Characteristic, are you sure the Characteristic has Notifications or Indicate properties?")

                    sendTaskResponse(msg)
                }
            } else {
                val msg = BleMessage(taskId, "unsubscribeToCharacteristic")
                msg.setError("Can't unsubscribe from Characteristic of a BluetoothDevice that isn't connected to the device.")

                sendTaskResponse(msg)
            }
        } else {
            val msg = BleMessage(taskId, "unsubscribeToCharacteristic")
            msg.setError("Can't unsubscribe from Characteristic of a BluetoothDevice that hasn't been discovered yet.")

            sendTaskResponse(msg)
        }
    }

    /**
     * Sends a BleMessage to Unity.
     *
     * @param message The BleMessage to send to Unity.
     */
    fun sendTaskResponse(message: BleMessage) {
        if (!message.id.isEmpty() && !message.command.isEmpty()) {
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
        } else {
            message.setError(if (message.id.isEmpty()) "Task ID is empty." else "Command is empty.")
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", message.toJsonString())
        }
    }

    companion object {
        private var mInstance: UnityAndroidBLE? = null

        @get:Suppress("unused")
        val instance: UnityAndroidBLE?
            /**
             * Returns the Singleton instance of UnityAndroidBLE.
             * @return Singleton instance of UnityAndroidBLE
             */
            get() {
                if (mInstance == null) {
                    val ctx =
                        UnityPlayer.currentActivity.applicationContext

                    val packageManager = ctx.packageManager

                    // Check if Device has Bluetooth and Bluetooth Low Energy features
                    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) || !packageManager.hasSystemFeature(
                            PackageManager.FEATURE_BLUETOOTH_LE
                        )
                    ) {
                        return null
                    }

                    val sdkInt = Build.VERSION.SDK_INT
                    val activity = UnityPlayer.currentActivity

                    if (sdkInt <= Build.VERSION_CODES.R) {
                        if (!checkPermissionsAndroid11AndBelow(
                                ctx,
                                activity
                            )
                        ) {
                            return null
                        }
                    } else {
                        if (!checkPermissionsAndroid12AndUp(
                                ctx,
                                activity
                            )
                        ) {
                            return null
                        }
                    }

                    mInstance = UnityAndroidBLE()
                }

                return mInstance
            }

        private fun checkPermissionsAndroid11AndBelow(ctx: Context, activity: Activity): Boolean {
            // Check if App has permissions for Bluetooth necessary features
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ), 1
            )

            return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                    && ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        @TargetApi(Build.VERSION_CODES.S)
        private fun checkPermissionsAndroid12AndUp(ctx: Context, activity: Activity): Boolean {
            //TODO: Check if asking for permission is triggered
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ), 1
            )

            return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
    }
}
