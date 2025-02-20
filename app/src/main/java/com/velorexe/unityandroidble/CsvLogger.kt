package com.velorexe.unityandroidble

import android.util.Log
import java.io.File
import java.io.FileWriter
import com.velorexe.unityandroidble.BleMessage.FUNCTION.CSV_LOGGER as CSV_LOGGER

object CsvLogger {
    private var filePath: String = ""
    private var fileWriter: FileWriter? = null

    /**
     * Setzt den Dateipfad für das CSV-Logging.
     * Dieser Pfad (z. B. Application.persistentDataPath) wird von Unity übergeben.
     */
    fun setFilePath(path: String) {
        filePath = path
        try {
            // Öffnet die Datei im Append-Modus. (Stelle sicher, dass Berechtigungen vorhanden sind.)
            fileWriter = FileWriter(File(filePath), true)
            BleMessage(CSV_LOGGER.name, "SET_FILE_PATH", "CSV file path set to $filePath").sendToUnity()
        } catch (e: Exception) {
            BleMessage(CSV_LOGGER.name, "ERROR", "Error setting CSV file path: ${e.localizedMessage}").sendToUnity()
        }
    }

    /**
     * Schreibt eine Zeile in die CSV-Datei.
     */
    fun logData(dataLine: String) {
        try {
            fileWriter?.apply {
                write(dataLine + "\n")
                flush()
                BleMessage(CSV_LOGGER.name, "LOG_DATA", "Data logged: $dataLine").sendToUnity()
            }
        } catch (e: Exception) {
            Log.e(CSV_LOGGER.name, "Error writing CSV data: ${e.localizedMessage}")
            BleMessage(CSV_LOGGER.name, "ERROR", "Error writing CSV data: ${e.localizedMessage}").sendToUnity()
        }
    }

    /**
     * Schließt den FileWriter und gibt eine Nachricht an Unity aus.
     */
    fun close() {
        try {
            fileWriter?.close()
            BleMessage(CSV_LOGGER.name, "CLOSE", "CSV file writer closed").sendToUnity()
        } catch (e: Exception) {
            Log.e(CSV_LOGGER.name, "Error closing CSV file writer: ${e.localizedMessage}")
            BleMessage(CSV_LOGGER.name, "ERROR", "Error closing CSV file writer: ${e.localizedMessage}").sendToUnity()
        }
    }
}
