package com.example.aniflow.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads an update APK and hands it to the package installer.
 *
 * This is a self-updating app, so this object is the app's supply chain. Three rules it must never
 * break, all of which it used to:
 *  1. **HTTPS only, on every hop.** It previously followed the `Location` header of a redirect with
 *     no scheme check at all, so an https:// update URL could be redirected to plain http:// and the
 *     APK swapped in flight.
 *  2. **Verify what was downloaded before offering to install it.** It now compares the archive's
 *     signing certificate against the currently installed app's, checks the package name, and
 *     refuses version downgrades.
 *  3. **Stage in internal storage.** It previously wrote to `getExternalFilesDir()`, which any app
 *     holding legacy external-storage permission can overwrite between download and install.
 */
object AppUpdater {

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 30_000

    fun downloadAndInstall(
        context: Context,
        url: String,
        versionName: String,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val updatesDir = File(appContext.cacheDir, "updates")
        val destinationFile = File(updatesDir, "AniFlow_$versionName.apk")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updatesDir.mkdirs()
                // Drop any previously staged APK, including partial ones.
                updatesDir.listFiles()?.forEach { it.delete() }

                val connection = openHttpsConnection(url)
                try {
                    downloadFileStream(connection, destinationFile, onProgress)
                } finally {
                    connection.disconnect()
                }

                if (!destinationFile.exists() || destinationFile.length() == 0L) {
                    throw IllegalStateException("Downloaded update is empty.")
                }
                verifyApkOrThrow(appContext, destinationFile)

                withContext(Dispatchers.Main) {
                    onProgress?.invoke(1.0f)
                    installApk(appContext, destinationFile)
                }
            } catch (e: Exception) {
                destinationFile.delete()
                val message = when (e) {
                    is SecurityException -> "Update rejected: ${e.message}"
                    else -> "Download failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(-1.0f)
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Opens [url], following redirects manually so that every hop can be forced to HTTPS.
     * `HttpURLConnection` will not follow https -> http itself, but it also will not tell us, and
     * the old code re-opened the `Location` target by hand with no check whatsoever.
     */
    private fun openHttpsConnection(url: String): HttpURLConnection {
        var current = URL(requireHttps(url))
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
            }
            connection.connect()
            val code = connection.responseCode
            val isRedirect = code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            if (!isRedirect) {
                if (code != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    throw IllegalStateException("HTTP Error: $code")
                }
                return connection
            }
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) throw IllegalStateException("Redirect with no Location header.")
            if (++redirects > MAX_REDIRECTS) throw IllegalStateException("Too many redirects.")
            // Resolve relative redirects against the current URL, then re-check the scheme.
            current = URL(requireHttps(URL(current, location).toString()))
        }
    }

    private fun requireHttps(url: String): String {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw SecurityException("update must be served over HTTPS")
        }
        return url
    }

    private suspend fun downloadFileStream(
        connection: HttpURLConnection,
        destinationFile: File,
        onProgress: ((Float) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        val totalBytes = connection.contentLength
        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastUpdate = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (totalBytes > 0 && now - lastUpdate > 100) {
                        lastUpdate = now
                        val progress = downloadedBytes.toFloat() / totalBytes
                        withContext(Dispatchers.Main) { onProgress?.invoke(progress) }
                    }
                }
                output.flush()
            }
        }
    }
    /**
     * Refuses to install anything that is not a newer build of *this* app, signed by the same key.
     * Without this an attacker who can influence the update URL (or the update JSON in the public
     * GitHub repo it is read from) can install an arbitrary APK under the user's own tap.
     */
    private fun verifyApkOrThrow(context: Context, file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }

        @Suppress("DEPRECATION")
        val downloaded = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw SecurityException("file is not a readable APK")
        val installed = pm.getPackageInfo(context.packageName, flags)

        if (downloaded.packageName != installed.packageName) {
            throw SecurityException("APK is for ${downloaded.packageName}, not ${installed.packageName}")
        }
        if (versionCodeOf(downloaded) < versionCodeOf(installed)) {
            throw SecurityException("APK is older than the installed version")
        }

        val expected = certificateDigests(installed)
        val actual = certificateDigests(downloaded)
        if (expected.isEmpty() || actual.isEmpty() || expected != actual) {
            throw SecurityException("APK is signed by a different key")
        }
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty()
            .filterNotNull()
            .map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }
            .toSet()
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
            Toast.makeText(context, "Failed to launch package installer.", Toast.LENGTH_LONG).show()
        }
    }
}
