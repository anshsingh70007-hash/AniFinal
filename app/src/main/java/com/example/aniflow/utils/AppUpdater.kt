package com.example.aniflow.utils

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.aniflow.data.model.AppUpdateInfo
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
 *
 * On top of that, with the signed update channel (data/update/UpdateChannel.kt): the APK must have
 * exactly the size and SHA-256 named by the signed manifest and the manifest's version code, and
 * every mirror's copy is tried in turn, so one bad or unreachable host never fails the update.
 */
object AppUpdater {

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 30_000
    /** Far above any real AniFlow APK; stops a hostile or broken server from filling the disk. */
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
    private val downloading = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Signed-channel update: [info] carries the mirror URLs and the APK's pinned size and hash. */
    fun downloadAndInstall(context: Context, info: AppUpdateInfo, onProgress: ((Float) -> Unit)? = null) {
        val sha = info.apkSha256
        val size = info.apkSize
        val urls = info.apkUrls.ifEmpty { listOfNotNull(info.updateUrl.takeIf { it.isNotBlank() }) }
        if (sha == null || size == null || urls.isEmpty()) {
            // Never install something the signed manifest didn't pin.
            Toast.makeText(context.applicationContext, "This update can't be verified. Try again later.", Toast.LENGTH_LONG).show()
            onProgress?.invoke(-1f)
            return
        }
        download(context, urls, Expected(sha, size, info.versionCode.toLong()), onProgress)
    }

    private class Expected(val sha256: String, val size: Long, val versionCode: Long)

    /** A mirror's copy was wrong or unreachable: try the next one. */
    private class MirrorFailed(msg: String) : Exception(msg)

    private fun download(
        context: Context,
        urls: List<String>,
        expected: Expected,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val updatesDir = File(appContext.cacheDir, "updates")
        // Fixed name: versionName is remote input and must not shape a path.
        val destinationFile = File(updatesDir, "AniFlow_update.apk")
        // A second tap while downloading used to start a parallel download into the same file.
        if (!downloading.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                updatesDir.mkdirs()
                // Drop any previously staged APK, including partial ones.
                updatesDir.listFiles()?.forEach { it.delete() }
                if (updatesDir.usableSpace < expected.size * 2) {
                    throw IllegalStateException("Not enough free space for the update.")
                }

                var lastError: Exception? = null
                var got = false
                for (url in urls) {
                    try {
                        val connection = openHttpsConnection(url)
                        val sha = try {
                            downloadFileStream(connection, destinationFile, expected.size, onProgress)
                        } finally {
                            connection.disconnect()
                        }
                        if (destinationFile.length() != expected.size) throw MirrorFailed("size mismatch")
                        if (!sha.equals(expected.sha256, ignoreCase = true)) throw MirrorFailed("hash mismatch")
                        got = true
                        break
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.w("AppUpdater", "mirror failed: ${e.message}")
                        lastError = e
                        destinationFile.delete()
                    }
                }
                if (!got) throw IllegalStateException("Couldn't download the update from any server (${lastError?.message}).")
                verifyApkOrThrow(appContext, destinationFile, expected.versionCode)

                withContext(Dispatchers.Main) { onProgress?.invoke(1.0f) }
                installApk(appContext, destinationFile)
            } catch (e: Exception) {
                destinationFile.delete()
                val message = when (e) {
                    is SecurityException -> "Update rejected: it didn't pass AniFlow's safety checks. Nothing was installed."
                    else -> "Update failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(-1.0f)
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                downloading.set(false)
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

    /** Streams to [destinationFile] and returns its SHA-256 (hex). Stops past [expectedSize]. */
    private suspend fun downloadFileStream(
        connection: HttpURLConnection,
        destinationFile: File,
        expectedSize: Long,
        onProgress: ((Float) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val totalBytes = expectedSize
        val md = MessageDigest.getInstance("SHA-256")
        if (connection.contentLengthLong > MAX_APK_BYTES) throw IllegalStateException("Update is unexpectedly large.")
        connection.inputStream.use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastUpdate = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    md.update(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (downloadedBytes > MAX_APK_BYTES || downloadedBytes > expectedSize) throw MirrorFailed("larger than the manifest says")

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
        md.digest().joinToString("") { "%02x".format(it) }
    }
    /**
     * Refuses to install anything that is not a newer build of *this* app, signed by the same key.
     * Without this an attacker who can influence the update URL (or the update JSON in the public
     * GitHub repo it is read from) can install an arbitrary APK under the user's own tap.
     */
    private fun verifyApkOrThrow(context: Context, file: File, expectedVersionCode: Long) {
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
        if (versionCodeOf(downloaded) != expectedVersionCode) {
            throw SecurityException("APK is not the version the signed manifest names")
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

    /**
     * Hands the verified APK to Android through [PackageInstaller], the system install API.
     *
     * It used to fire `ACTION_VIEW` with the APK's MIME type, which depends on some app claiming
     * that intent. Many Android TVs ship none, so the update died with "You don't have an app that
     * can do this". A session needs no such app: the system itself shows the confirm screen
     * (InstallResultReceiver starts it). `ACTION_VIEW` stays as a last resort only.
     */
    private suspend fun installApk(context: Context, file: File) = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        input.copyTo(out, 1 shl 16)
                        session.fsync(out)
                    }
                }
                // Explicit: the receiver is not exported, and only this PendingIntent may reach it.
                val callback = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
                // Mutable: the system fills in the status extras.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, sessionId, callback, flags).intentSender)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("AppUpdater", "session install failed, trying ACTION_VIEW", e)
            withContext(Dispatchers.Main) { installWithViewIntent(context, file) }
        }
    }

    private fun installWithViewIntent(context: Context, file: File) {
        try {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "This device couldn't open the installer. Nothing was installed.", Toast.LENGTH_LONG).show()
        }
    }
}
