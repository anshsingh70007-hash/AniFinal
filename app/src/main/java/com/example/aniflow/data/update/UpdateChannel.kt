package com.example.aniflow.data.update

import com.example.aniflow.data.model.AppUpdateInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/**
 * AniFlow's own update channel. Nothing here depends on GitHub or any single host.
 *
 * Every mirror serves the same `update.json`: an envelope holding a manifest and an ECDSA P-256
 * signature over it, made offline by `tools/update_channel.py`. The public keys are compiled into
 * the app, so a mirror that is hacked, stale, cached wrongly or taken over can serve nothing an
 * install will act on. It can at worst be unreachable, and then the other mirrors answer.
 *
 * The rules, each guarding against a way updates break in the field:
 *  - Unsigned, badly signed or malformed manifests are ignored, never half-trusted.
 *  - The highest signed `sequence` across mirrors wins, and an install never goes back below the
 *    highest it has accepted, so a replayed old manifest (say one with maintenance on) is inert.
 *  - A manifest from a newer schema than this build understands is ignored, not misread.
 *  - The update is only offered if this device can install it (`minSdk`).
 *  - Maintenance carries an end time: a forgotten flag switches itself off.
 *  - Staged rollout: a version reaches `rollout`% of installs first, picked by a stable per-install
 *    bucket, so a bad build can be halted before most people have it.
 *  - The APK itself is pinned by size and SHA-256 here, and AppUpdater additionally checks the
 *    signing certificate, package and version before Android is ever asked to install it.
 *
 * Pure Kotlin, no Android types: everything is unit-tested on the JVM.
 */
class UpdateChannel(
    private val mirrors: List<String>,
    trustedKeys: Map<String, String>,
    private val store: StateStore,
    private val fetch: suspend (url: String) -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What an install remembers between checks. */
    interface StateStore {
        var highestSequence: Long
        /** Random, made once per install; only ever used for the rollout bucket. */
        val installId: String
    }

    class Result(
        /** The verified manifest. */
        val manifest: Manifest,
        /** Where its APK can be downloaded, best first: one copy per mirror. */
        val apkUrls: List<String>,
    )

    private val keys: List<PublicKey> = trustedKeys.values.mapNotNull { runCatching { decodeKey(it) }.getOrNull() }

    /**
     * Asks every mirror and returns the newest validly signed manifest, or null if none could be
     * fetched and verified (offline, all mirrors down, or every answer rejected). Null means "could
     * not check", never "you're up to date".
     */
    suspend fun check(): Result? {
        val good = mirrors.filter { it.startsWith("https://", ignoreCase = true) }.mapNotNull { mirror ->
            val body = runCatching { fetch(mirror) }.getOrNull() ?: return@mapNotNull null
            open(body)?.let { mirror to it }
        }
        val best = good.maxByOrNull { it.second.sequence } ?: return null
        val manifest = best.second
        if (manifest.sequence < store.highestSequence) return null   // every mirror is behind what we've seen
        store.highestSequence = manifest.sequence
        // The mirror that answered newest first, then the rest: each serves its own copy of the APK.
        val order = listOf(best.first) + mirrors.filter { it != best.first }
        val urls = order.mapNotNull { resolve(it, manifest.apk.file) }.distinct()
        return Result(manifest, urls)
    }

    /** Verifies and parses one envelope; null if anything about it is off. */
    fun open(body: String): Manifest? = runCatching {
        if (body.length > MAX_MANIFEST_CHARS) return null
        val env = json.decodeFromString(Envelope.serializer(), body)
        if (env.format != FORMAT) return null
        val payload = Base64.decode(env.payload)
        val sig = Base64.decode(env.sig)
        if (keys.none { verify(it, payload, sig) }) return null
        val m = json.decodeFromString(Manifest.serializer(), payload.decodeToString())
        m.takeIf { it.schema in 1..SCHEMA && it.isSane() }
    }.getOrNull()

    /**
     * Turns a verified manifest into what the UI shows, for an install at [installedVersionCode] on
     * Android [sdkInt]. Null when there's nothing to say (up to date, or this device can't install it).
     */
    fun toUpdateInfo(result: Result, installedVersionCode: Long, sdkInt: Int): AppUpdateInfo? {
        val m = result.manifest
        val maintenance = m.maintenance?.takeIf { it.until * 1000 > now() }
        val newer = m.versionCode > installedVersionCode && sdkInt >= m.minSdk && result.apkUrls.isNotEmpty()
        if (!newer && maintenance == null) return null
        return AppUpdateInfo(
            // With nothing newer, the UI still needs a version to compare; it gates on maintenance.
            versionCode = (if (newer) m.versionCode else installedVersionCode).toInt(),
            versionName = m.versionName,
            updateUrl = result.apkUrls.firstOrNull().orEmpty(),
            updateNotes = maintenance?.message ?: m.notes.ifBlank { null },
            forceUpdate = newer && installedVersionCode < m.minVersionCode,
            // Outside this install's rollout bucket: no takeover prompt. A manual check in
            // Settings still offers it, since the person asked.
            silentUpdate = newer && !inRollout(m),
            maintenance = maintenance != null,
            apkUrls = if (newer) result.apkUrls else emptyList(),
            apkSha256 = m.apk.sha256,
            apkSize = m.apk.size,
        )
    }

    /** Stable per install and version: the same device stays in or out until the percentage moves. */
    fun inRollout(m: Manifest): Boolean {
        if (m.rollout >= 100) return true
        if (m.rollout <= 0) return false
        val h = MessageDigest.getInstance("SHA-256").digest("${store.installId}:${m.versionCode}".encodeToByteArray())
        val bucket = ((h[0].toLong() and 0xff) shl 24 or ((h[1].toLong() and 0xff) shl 16) or
            ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)) % 100
        return bucket < m.rollout
    }

    @Serializable
    class Envelope(val format: String, val key: String = "", val payload: String, val sig: String)

    @Serializable
    data class Manifest(
        val schema: Int,
        val sequence: Long,
        val channel: String = "stable",
        val versionCode: Long,
        val versionName: String,
        val notes: String = "",
        val minSdk: Int = 1,
        /** Installs below this must update (no "later" button). */
        val minVersionCode: Long = 0,
        val rollout: Int = 100,
        val apk: Apk,
        val maintenance: Maintenance? = null,
    ) {
        internal fun isSane(): Boolean =
            sequence > 0 && versionCode > 0 && rollout in 0..100 && apk.size in 1..MAX_APK_BYTES &&
                apk.sha256.matches(HEX64) && apk.signer.matches(HEX64) && apk.file.matches(SAFE_FILE)
    }

    @Serializable
    data class Apk(
        /** File name next to update.json on every mirror. */
        val file: String,
        val sha256: String,
        val size: Long,
        /** SHA-256 of the signing certificate the APK must carry. */
        val signer: String,
    )

    @Serializable
    data class Maintenance(val message: String, /** Epoch seconds; ignored after this. */ val until: Long)

    companion object {
        const val FORMAT = "aniflow-update/1"
        /** Highest manifest schema this build understands. */
        const val SCHEMA = 1
        const val MAX_MANIFEST_CHARS = 256 * 1024
        const val MAX_APK_BYTES = 250L * 1024 * 1024
        private val HEX64 = Regex("[0-9a-f]{64}")
        /** A plain file name: no path, no scheme, so it can only resolve next to the manifest. */
        private val SAFE_FILE = Regex("[A-Za-z0-9._-]{1,128}\\.apk")
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }

        fun decodeKey(spkiBase64: String): PublicKey =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(spkiBase64)))

        fun verify(key: PublicKey, payload: ByteArray, sig: ByteArray): Boolean = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key); update(payload); verify(sig)
            }
        }.getOrDefault(false)

        /** [file] next to [manifestUrl], only if that stays on HTTPS. */
        internal fun resolve(manifestUrl: String, file: String): String? = runCatching {
            URI(manifestUrl).resolve(file).toString().takeIf { it.startsWith("https://", ignoreCase = true) }
        }.getOrNull()
    }
}
