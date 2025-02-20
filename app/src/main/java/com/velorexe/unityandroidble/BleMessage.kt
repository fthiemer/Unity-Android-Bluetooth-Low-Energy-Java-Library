package com.velorexe.unityandroidble

import com.polar.sdk.api.model.PolarDeviceInfo
import com.unity3d.player.UnityPlayer
import org.json.JSONException //easy to use JSON library, manuelles Zuweisen
import org.json.JSONObject

/**
 * Represents a message that is sent between Unity and the Android plugin.
 * Holds Properties for the Unity side of things to manage all it's interactions
 * id: PolarDeviceID or MacAddress
 * command: relevant method name
 */
data class BleMessage(val command: String, val deviceID: String, val message: String) {
    enum class FUNCTION {
        CONNECTION,
        INFO,
        CSV_LOGGER,
    }

    var device: String? = null
    var name: String? = null

    var deviceInfo : PolarDeviceInfo? = null
    var service: String? = null
    var characteristic: String? = null

    var base64Data: String? = null

    /**
     * JSON data that is sent to Unity.
     * This is used to send more complex data structures to Unity.
     */
    var jsonData: String? = null

    var hasError: Boolean = false
    var errorMessage: String? = null

    /**
     * Sends a [BleMessage] to Unity.
     *
     * @param message The [BleMessage] to send to Unity.
     */
    fun sendToUnity() {
        try {
            if (this.deviceID.isNotEmpty() && this.command.isNotEmpty()) {
                UnityPlayer.UnitySendMessage("BleManager", "OnBleMessage", this.toJsonString())
            } else {
                this.setError(if (this.deviceID.isEmpty()) "Task ID is empty." else "Command is empty.")
                UnityPlayer.UnitySendMessage("BleManager", "OnBleMessage", this.toJsonString())
            }
        } catch (e: Exception) {
            this.setError(e.message)
        }
    }

    fun setError(errorMessage: String?) {
        hasError = true
        this.errorMessage = errorMessage
    }

    fun toJsonString(): String {
        val obj = JSONObject()

        try {
            obj.put("id", deviceID)
            obj.put("command", command)
            obj.put("message", message)

            obj.put("device", device)
            obj.put("deviceInfo", deviceInfo)
            obj.put("name", name)

            obj.put("service", service)
            obj.put("characteristic", characteristic)

            obj.put("base64Data", base64Data)
            obj.put("jsonData", jsonData)

            obj.put("hasError", hasError)
            obj.put("errorMessage", errorMessage)
            return obj.toString()
        } catch (e: JSONException) {
            //TODO: Kommt die irgendwo an?
            e.printStackTrace()
        }

        return obj.toString()
    }
}
