package com.velorexe.unityandroidble

import com.polar.sdk.api.model.PolarDeviceInfo
import com.unity3d.player.UnityPlayer
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a message that is sent between Unity and the Android plugin.
 * Holds Properties for the Unity side of things to manage all it's interactions
 */
data class BleMessage(val id: String, val command: String) {
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
        //TODO: einfacherer Weg für für Errors (s. Connect to Device)
        if (this.id.isNotEmpty() && this.command.isNotEmpty()) {
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", this.toJsonString())
        } else {
            this.setError(if (this.id.isEmpty()) "Task ID is empty." else "Command is empty.")
            UnityPlayer.UnitySendMessage("BleMessageAdapter", "OnBleMessage", this.toJsonString())
        }
    }

    fun setError(errorMessage: String?) {
        hasError = true
        this.errorMessage = errorMessage
    }

    fun toJsonString(): String {
        val obj = JSONObject()

        try {
            obj.put("id", id)
            obj.put("command", command)

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
