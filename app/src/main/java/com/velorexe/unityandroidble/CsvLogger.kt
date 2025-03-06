package com.velorexe.unityandroidble

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.io.IOException
import com.velorexe.unityandroidble.BleMessage.FUNCTION.CSV_LOGGER as CSV_LOGGER


object CsvLogger {
    private var basePath: String = ""
    private var baseDirectory: String = ""
    private var currentFileWriter: BufferedWriter? = null
    private var trialFiles: MutableList<File> = mutableListOf()
    private var currentTrialIndex: Int = 0

    /**
     * Initialisiert die Verzeichnisstruktur und erstellt die notwendigen Dateien.
     * @param basePath Der Basisdateipfad (z.B. Application.persistentDataPath), von Unity übergeben.
     * @param participantId Die ID des Teilnehmers.
     * @param condition Die aktuelle Bedingung.
     * @param blocks Eine Liste von Blöcken, die jeweils eine Liste von Trials enthalten.
     */
    fun setup(basePath: String, participantId: String, condition: String, blocks: List<List<String>>) {
        this.basePath = basePath
        baseDirectory = "$basePath/$participantId"
        val conditionDirectory = File(baseDirectory, condition)
        if (!conditionDirectory.exists()) {
            conditionDirectory.mkdirs()
        }

        blocks.forEachIndexed { blockIndex, trials ->
            val blockDir = File(conditionDirectory, "Block_${blockIndex + 1}")
            if (!blockDir.exists()) {
                blockDir.mkdirs()
            }
            trials.forEachIndexed { trialIndex, trial ->
                val trialFile = File(blockDir, "Trial_${trialIndex + 1}.csv")
                trialFiles.add(trialFile)
                if (!trialFile.exists()) {
                    trialFile.createNewFile()
                }
            }
        }

        // Datei erstellen, die den letzten abgeschlossenen Trial speichert
        val progressFile = File(baseDirectory, "progress.txt")
        if (!progressFile.exists()) {
            progressFile.createNewFile()
            progressFile.writeText("0") // Start bei Trial 0
        } else {
            val lastCompletedTrial = progressFile.readText().toIntOrNull() ?: 0
            currentTrialIndex = lastCompletedTrial
        }

        openCurrentFileWriter()
    }

    /**
     * Öffnet den FileWriter für die aktuelle Trial-Datei.
     */
    private fun openCurrentFileWriter() {
        closeCurrentFileWriter()
        if (currentTrialIndex < trialFiles.size) {
            val currentFile = trialFiles[currentTrialIndex]
            try {
                currentFileWriter = BufferedWriter(FileWriter(currentFile, true))
                BleMessage(CSV_LOGGER.name, "OPEN_FILE", "Opened file: ${currentFile.absolutePath}").sendToUnity()
            } catch (e: IOException) {
                Log.e(CSV_LOGGER.name, "Error opening file: ${e.localizedMessage}")
                BleMessage(CSV_LOGGER.name, "ERROR", "Error opening file: ${e.localizedMessage}").sendToUnity()
            }
        } else {
            Log.e(CSV_LOGGER.name, "No more trial files available.")
            BleMessage(CSV_LOGGER.name, "ERROR", "No more trial files available.").sendToUnity()
        }
    }

    /**
     * Schließt den aktuellen FileWriter.
     */
    private fun closeCurrentFileWriter() {
        try {
            currentFileWriter?.close()
            currentFileWriter = null
            BleMessage(CSV_LOGGER.name, "CLOSE_FILE", "Closed current file.").sendToUnity()
        } catch (e: IOException) {
            Log.e(CSV_LOGGER.name, "Error closing file: ${e.localizedMessage}")
            BleMessage(CSV_LOGGER.name, "ERROR", "Error closing file: ${e.localizedMessage}").sendToUnity()
        }
    }

    /**
     * Wechselt zur nächsten Trial-Datei.
     */
    fun switchToNextFile() {
        closeCurrentFileWriter()
        currentTrialIndex++
        if (currentTrialIndex < trialFiles.size) {
            openCurrentFileWriter()
            // Fortschritt speichern
            val progressFile = File(baseDirectory, "progress.txt")
            progressFile.writeText(currentTrialIndex.toString())
        } else {
            Log.i(CSV_LOGGER.name, "All trials completed.")
            BleMessage(CSV_LOGGER.name, "INFO", "All trials completed.").sendToUnity()
        }
    }

    /**
     * Schreibt eine Zeile in die aktuelle CSV-Datei.
     * @param dataLine Die zu schreibende Datenzeile.
     */
    fun logData(dataLine: String) {
        try {
            currentFileWriter?.apply {
                write(dataLine)
                newLine()
                flush()
                BleMessage(CSV_LOGGER.name, "LOG_DATA", "Data logged: $dataLine").sendToUnity()
            } ?: run {
                Log.e(CSV_LOGGER.name, "FileWriter is not initialized.")
                BleMessage(CSV_LOGGER.name, "ERROR", "FileWriter is not initialized.").sendToUnity()
            }
        } catch (e: IOException) {
            Log.e(CSV_LOGGER.name, "Error writing data: ${e.localizedMessage}")
            BleMessage(CSV_LOGGER.name, "ERROR", "Error writing data: ${e.localizedMessage}").sendToUnity()
        }
    }




    /**
     * Bereitet den Logger für die Fortsetzung vor, indem der Fortschritt geladen wird.
     */
    fun prepareForContinuation() {
        val progressFile = File(basePath, "progress.txt")
        if (progressFile.exists()) {
            val lastCompletedTrial = progressFile.readText().toIntOrNull() ?: 0
            currentTrialIndex = lastCompletedTrial
            openCurrentFileWriter()
        } else {
            Log.e(CSV_LOGGER.name, "Progress file not found.")
            BleMessage(CSV_LOGGER.name, "ERROR", "Progress file not found.").sendToUnity()
        }
    }
}
