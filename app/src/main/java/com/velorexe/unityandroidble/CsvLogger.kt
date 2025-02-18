package com.velorexe.unityandroidble

import android.util.Log
import java.io.File
import java.io.FileWriter

object CsvLogger {
    private const val TAG = "CsvLogger"
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
            BleMessage("CSV_LOGGER","SET_FILE_PATH", "$filePath").sendToUnity()
        } catch (e: Exception) {
            BleMessage("CSV_LOGGER","ERROR", "Error setting CSV file path: ${e.localizedMessage}").sendToUnity()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing CSV data: ${e.localizedMessage}")
        }
    }
}
