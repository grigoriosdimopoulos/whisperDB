package com.whisperlm.app.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun getRecordingsDir(context: Context): File {
        val dir = context.getExternalFilesDir("recordings") ?: context.filesDir.resolve("recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelsDir(context: Context): File {
        val dir = context.getExternalFilesDir("models") ?: context.filesDir.resolve("models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPhotosDir(context: Context): File {
        val dir = context.getExternalFilesDir("photos") ?: context.filesDir.resolve("photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newRecordingFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(getRecordingsDir(context), "REC_$timestamp.wav")
    }

    /**
     * Find the first Whisper model file (.bin) in the models directory.
     */
    fun findWhisperModel(context: Context): File? =
        getModelsDir(context).listFiles()
            ?.firstOrNull { it.extension == "bin" && it.name.startsWith("whisper") }

    /**
     * Find LLM model: .task (MediaPipe) first, then .gguf (llama.cpp)
     */
    fun findLlmModel(context: Context): File? {
        val modelsDir = getModelsDir(context)
        return modelsDir.listFiles()?.firstOrNull { it.extension == "task" }
            ?: modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }
    }

    fun isMediaPipeModel(file: File): Boolean = file.extension == "task"
    fun isLlamaModel(file: File): Boolean = file.extension == "gguf"

    /**
     * Copy a URI (e.g. from file picker) into the app's models directory.
     */
    fun copyUriToModelsDir(context: Context, uri: Uri, fileName: String): File {
        val dest = File(getModelsDir(context), fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    /**
     * Copy a URI (e.g. from file picker) into the app's recordings directory,
     * keeping the original file extension.
     */
    fun copyUriToRecordingsDir(context: Context, uri: Uri, suggestedName: String): File {
        val dest = File(getRecordingsDir(context), suggestedName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    /**
     * Copy a URI to the photos directory as a JPEG.
     */
    fun copyUriToPhotosDir(context: Context, uri: Uri, personId: Long): File {
        val dest = File(getPhotosDir(context), "person_$personId.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
        } ?: "unknown"
    }
}
