package com.example.aniflow.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppUpdater {
    fun downloadAndInstall(
        context: Context,
        url: String,
        versionName: String,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "AniFinal_$versionName.apk"
        )

        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            try {
                // Delete existing file if present
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    val newConn = URL(newUrl).openConnection() as HttpURLConnection
                    newConn.connectTimeout = 30_000
                    newConn.readTimeout = 30_000
                    newConn.connect()
                    
                    if (newConn.responseCode != HttpURLConnection.HTTP_OK) {
                        throw Exception("HTTP Error: ${newConn.responseCode}")
                    }
                    downloadFileStream(newConn, destinationFile, onProgress)
                } else {
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw Exception("HTTP Error: $responseCode")
                    }
                    downloadFileStream(connection, destinationFile, onProgress)
                }
                
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(-1.0f)
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

            if (success && destinationFile.exists() && destinationFile.length() > 0) {
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(1.0f)
                    installApk(context, destinationFile)
                }
            }
        }
    }

    private suspend fun downloadFileStream(
        connection: HttpURLConnection,
        destinationFile: File,
        onProgress: ((Float) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        val totalBytes = connection.contentLength
        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(16384) // 16KB buffer for fast downloading
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastUpdate = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    val now = System.currentTimeMillis()
                    if (totalBytes > 0 && now - lastUpdate > 100) { // Throttle progress updates to UI to avoid spamming Main thread
                        lastUpdate = now
                        val progress = downloadedBytes.toFloat() / totalBytes
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(progress)
                        }
                    }
                }
                output.flush()
            }
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to launch package installer.", Toast.LENGTH_LONG).show()
        }
    }
}
