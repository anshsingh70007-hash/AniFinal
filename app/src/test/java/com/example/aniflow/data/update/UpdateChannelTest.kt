package com.example.aniflow.data.update

import com.example.aniflow.utils.UpdateConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64

/** Every way the update channel must refuse to be fooled, and the ways it must keep working. */
class UpdateChannelTest {

    private class MemStore(override var highestSequence: Long = 0, override val installId: String = "install-1") : UpdateChannel.StateStore

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val k1 = keyPair()
    private val k2 = keyPair()
    private val attacker = keyPair()
    private fun spki(kp: KeyPair) = Base64.encode(kp.public.encoded)
    private val trusted = mapOf("k1" to spki(k1), "k2" to spki(k2))

    private fun manifest(
        seq: Long = 1000, code: Long = 60, minSdk: Int = 24, minVersion: Long = 0, rollout: Int = 100,
        file: String = "AniFlow-1.9.2-60.apk", maintenance: String = "",
    ) = """{"schema":1,"sequence":$seq,"versionCode":$code,"versionName":"1.9.2","notes":"Fixes","minSdk":$minSdk,
        "minVersionCode":$minVersion,"rollout":$rollout,"apk":{"file":"$file","sha256":"${"a".repeat(64)}","size":4291203,
        "signer":"${"b".repeat(64)}"}$maintenance}"""

    private fun envelope(payload: String, kp: KeyPair = k1): String {
        val bytes = payload.encodeToByteArray()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(bytes); sign() }
        return """{"format":"aniflow-update/1","key":"k1","payload":"${Base64.encode(bytes)}","sig":"${Base64.encode(sig)}"}"""
    }

    private fun channel(
        responses: Map<String, String?>, store: MemStore = MemStore(), now: Long = 1_000_000_000_000L,
    ) = UpdateChannel(responses.keys.toList(), trusted, store, { url -> responses[url] }, { now })

    private val a = "https://a.example/update/update.json"
    private val b = "https://b.example/update.json"

    @Test fun `valid manifest is accepted and APK urls come from every mirror`() = runBlocking {
        val env = envelope(manifest())
        val r = channel(mapOf(a to env, b to env)).check()
        assertNotNull(r)
        assertEquals(60L, r!!.manifest.versionCode)
        assertEquals(listOf("https://a.example/update/AniFlow-1.9.2-60.apk", "https://b.example/AniFlow-1.9.2-60.apk"), r.apkUrls)
    }

    @Test fun `spare key k2 is trusted too`() = runBlocking {
        assertNotNull(channel(mapOf(a to envelope(manifest(), k2))).check())
    }

    @Test fun `manifest signed by an unknown key is ignored`() = runBlocking {
        assertNull(channel(mapOf(a to envelope(manifest(), attacker))).check())
    }

    @Test fun `tampered payload fails the signature`() = runBlocking {
        val env = envelope(manifest(code = 60))
        val forged = Json.parseToJsonElement(env).toString().let { e ->
            val good = Base64.encode(manifest(code = 60).encodeToByteArray())
            e.replace(good, Base64.encode(manifest(code = 61).encodeToByteArray()))
        }
        assertNull(channel(mapOf(a to forged)).check())
    }

    @Test fun `hacked mirror cannot outvote a good one, newest signed answer wins`() = runBlocking {
        val r = channel(mapOf(a to envelope(manifest(seq = 999_999, code = 99), attacker), b to envelope(manifest(seq = 1000)))).check()
        assertEquals(60L, r!!.manifest.versionCode)
        assertEquals("https://b.example/AniFlow-1.9.2-60.apk", r.apkUrls.first())
    }

    @Test fun `down or garbage mirrors fall through to a working one`() = runBlocking {
        assertNotNull(channel(mapOf(a to null, "https://c.example/update.json" to "<html>502</html>", b to envelope(manifest()))).check())
    }

    @Test fun `all mirrors down means could-not-check, not up-to-date`() = runBlocking {
        assertNull(channel(mapOf(a to null, b to null)).check())
    }

    @Test fun `stale mirror loses to fresh one`() = runBlocking {
        val r = channel(mapOf(a to envelope(manifest(seq = 1000, code = 60)), b to envelope(manifest(seq = 2000, code = 61)))).check()
        assertEquals(61L, r!!.manifest.versionCode)
    }

    @Test fun `replayed older manifest is refused once a newer one was seen`() = runBlocking {
        val store = MemStore()
        channel(mapOf(a to envelope(manifest(seq = 2000))), store).check()
        assertEquals(2000L, store.highestSequence)
        assertNull(channel(mapOf(a to envelope(manifest(seq = 1000))), store).check())
    }

    @Test fun `plain http mirror is never asked`() = runBlocking {
        val asked = mutableListOf<String>()
        UpdateChannel(listOf("http://a.example/update.json"), trusted, MemStore(), { asked += it; envelope(manifest()) }).check()
        assertTrue(asked.isEmpty())
    }

    @Test fun `apk file name cannot escape the mirror`() = runBlocking {
        assertNull(channel(mapOf(a to envelope(manifest(file = "../evil.apk")))).check())
        assertNull(channel(mapOf(a to envelope(manifest(file = "https://evil.example/x.apk")))).check())
    }

    @Test fun `newer schema is ignored, unknown fields are fine`() = runBlocking {
        assertNull(channel(mapOf(a to envelope(manifest().replace("\"schema\":1", "\"schema\":2")))).check())
        assertNotNull(channel(mapOf(a to envelope(manifest().dropLast(1) + ",\"somethingNew\":[1,2]}"))).check())
    }

    @Test fun `update offered only when newer and installable on this Android`() = runBlocking {
        val ch = channel(mapOf(a to envelope(manifest(code = 60, minSdk = 26))))
        val r = ch.check()!!
        assertNull(ch.toUpdateInfo(r, installedVersionCode = 60, sdkInt = 34))     // up to date
        assertNull(ch.toUpdateInfo(r, installedVersionCode = 55, sdkInt = 24))     // device too old: skip, don't fail
        val info = ch.toUpdateInfo(r, installedVersionCode = 55, sdkInt = 34)!!
        assertEquals(60, info.versionCode)
        assertEquals("a".repeat(64), info.apkSha256)
        assertFalse(info.forceUpdate)
        assertFalse(info.silentUpdate)
    }

    @Test fun `min version forces the update`() = runBlocking {
        val ch = channel(mapOf(a to envelope(manifest(code = 60, minVersion = 58))))
        val r = ch.check()!!
        assertTrue(ch.toUpdateInfo(r, 55, 34)!!.forceUpdate)
        assertFalse(ch.toUpdateInfo(r, 59, 34)!!.forceUpdate)
    }

    @Test fun `maintenance shows until its end time, then switches itself off`() = runBlocking {
        val until = 1_000_000_000L + 3600   // seconds
        val m = manifest(maintenance = ""","maintenance":{"message":"Back soon","until":$until}""")
        val during = channel(mapOf(a to envelope(m)), now = 1_000_000_000_000L)
        val info = during.toUpdateInfo(during.check()!!, installedVersionCode = 60, sdkInt = 34)!!
        assertTrue(info.maintenance)
        assertEquals("Back soon", info.updateNotes)
        val after = channel(mapOf(a to envelope(m)), now = (until + 1) * 1000)
        assertNull(after.toUpdateInfo(after.check()!!, installedVersionCode = 60, sdkInt = 34))
    }

    @Test fun `rollout buckets are stable and roughly proportional`() = runBlocking {
        val m = UpdateChannel.Manifest(1, 1, versionCode = 60, versionName = "x", rollout = 25,
            apk = UpdateChannel.Apk("a.apk", "a".repeat(64), 1, "b".repeat(64)))
        val inCount = (0 until 2000).count { i -> UpdateChannel(emptyList(), trusted, MemStore(installId = "id-$i"), { null }).inRollout(m) }
        assertTrue("got $inCount/2000", inCount in 400..600)
        val one = UpdateChannel(emptyList(), trusted, MemStore(installId = "fixed"), { null })
        assertEquals(one.inRollout(m), one.inRollout(m))
        assertFalse(one.inRollout(m.copy(rollout = 0)))
        assertTrue(one.inRollout(m.copy(rollout = 100)))
    }

    @Test fun `out of rollout means no takeover prompt`() = runBlocking {
        val ch = channel(mapOf(a to envelope(manifest(rollout = 0))))
        assertTrue(ch.toUpdateInfo(ch.check()!!, 55, 34)!!.silentUpdate)
    }

    /** Produced by tools/update_channel.py's signing code (Python `cryptography`), verified here. */
    @Test fun `manifest signed by the Python publish tool verifies in the app`() {
        val pub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBxHKW12hoyL/x9nY3hDGpqRWTJrFgbaAMmbvT9orFxfJLG9cMXJBscCk/nAo0jB9km7bwkS09Ojd/GCd/L4p2w=="
        val env = """{"format": "aniflow-update/1", "key": "k1", "payload": "eyJhcGsiOnsiZmlsZSI6IkFuaUZsb3ctMS45LjItNjAuYXBrIiwic2hhMjU2IjoiYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYSIsInNpZ25lciI6ImI5YjE0MjRhNjM0YzU4ZWJlZDJmMDA1MDhkMmRhNTg1ODUzOTg4YTI3YWI1NjAxNzI5OTkxNDVmZDc4YTE2NTAiLCJzaXplIjo0MjkxMjAzfSwiY2hhbm5lbCI6InN0YWJsZSIsImZ1dHVyZUZpZWxkIjp7IngiOjF9LCJtaW5TZGsiOjI0LCJtaW5WZXJzaW9uQ29kZSI6MCwibm90ZXMiOiJUZXN0IG5vdGVzIOKAlCDDqSIsInJvbGxvdXQiOjEwMCwic2NoZW1hIjoxLCJzZXF1ZW5jZSI6MTc2MjYwMDAwMDAwMCwidmVyc2lvbkNvZGUiOjYwLCJ2ZXJzaW9uTmFtZSI6IjEuOS4yIn0=", "sig": "MEUCIB8dNXWpBipgCAI7GNpQqnzjg4FgvXfe1DCsyJKNTPN3AiEAlKDgeuuQfg56He5d6g8ZKM5Uu/3NCG7xK4gHuDFtttM="}"""
        val m = UpdateChannel(emptyList(), mapOf("k1" to pub), MemStore(), { null }).open(env)
        assertNotNull(m)
        assertEquals("1.9.2", m!!.versionName)
        assertEquals("Test notes — é", m.notes)
    }

    @Test fun `shipped config carries both keys and they parse`() {
        assertEquals(setOf("k1", "k2"), UpdateConfig.MANIFEST_KEYS.keys)
        UpdateConfig.MANIFEST_KEYS.values.forEach { UpdateChannel.decodeKey(it) }
        UpdateConfig.MANIFEST_MIRRORS.forEach { assertTrue("$it must be https", it.startsWith("https://")) }
    }
}
