package com.velorexe.unityandroidble

import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a message that is sent between Unity and the Android plugin.
 * Holds Properties for the Unity side of things to manage all it's interactions
 */
class BleMessage(val id: String, val command: String) {
    var device: String? = null
    var name: String? = null

    var service: String? = null
    var characteristic: String? = null

    var base64Data: String? = null

    var jsonData: String? = null

    var hasError: Boolean = false
    var errorMessage: String? = null

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
