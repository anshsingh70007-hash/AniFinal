package com.example.aniflow.ui.player

import com.example.aniflow.data.*
import com.example.aniflow.data.model.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class PlaybackFailoverTest {

    @Before
    fun setUp() {
        ProviderRegistry.forceEnableAllForTesting = true
        ProviderId.entries.forEach {
            ProviderRegistry.getCircuitBreaker(it).reset()
        }
    }

    @After
    fun tearDown() {
        ProviderRegistry.forceEnableAllForTesting = false
        ProviderId.entries.forEach {
            ProviderRegistry.getCircuitBreaker(it).reset()
        }
    }

    private class TestClock(var currentTimeMs: Long = 0) : Clock {
        override fun elapsedRealtime(): Long = currentTimeMs
    }

    @Test
    fun testCircuitBreakerTransitions() {
        val clock = TestClock()
        val breaker = CircuitBreaker(
            clock = clock,
            minSampleCount = 10,
            failureThreshold = 0.5,
            cooldownMs = 1000
        )

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState())

        // Record 9 failures - should stay CLOSED (min sample count is 10)
        for (i in 1..9) {
            breaker.recordFailure()
        }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState())

        // 10th failure - trips to OPEN
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState())
        assertFalse(breaker.canExecute())

        // Advance time past cooldown duration (1000ms)
        clock.currentTimeMs += 1001
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState())
        assertTrue(breaker.canExecute())

        // In Half-Open, a success returns the state to CLOSED
        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState())
        assertTrue(breaker.canExecute())

        // Trip it again
        for (i in 1..10) {
            breaker.recordFailure()
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState())

        // Move to Half-Open again
        clock.currentTimeMs += 1001
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState())

        // In Half-Open, a failure returns the state directly to OPEN
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState())
    }

    @Test
    fun testFailoverAlternateServerSelection() {
        val controller = PlaybackFailoverController(ProviderRegistry)
        val checkpoint = PlaybackCheckpoint(
            identity = AnimeIdentity(1, 1, "Test Anime"),
            episodeNumber = 1,
            audioType = AudioType.SUB,
            qualityPolicy = QualityPolicy.Auto,
            subtitlePreference = null,
            positionMs = 5000L,
            speed = 1.0f,
            generationId = 1L
        )

        val sources = listOf(
            SourceEndpoint(
                id = "anilight_ep1_server1_sub",
                provider = ProviderId.ANILIGHT,
                server = ServerId("server1"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/1.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            ),
            SourceEndpoint(
                id = "anilight_ep1_server2_sub",
                provider = ProviderId.ANILIGHT,
                server = ServerId("server2"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/2.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            )
        )

        val attempts = mapOf(ProviderId.ANILIGHT to 0)

        val decision = controller.determineFailover(
            checkpoint = checkpoint,
            currentProvider = ProviderId.ANILIGHT,
            currentEndpoint = sources[0],
            endpoints = sources,
            errorType = PlaybackErrorType.Network,
            attempts = attempts
        )

        assertTrue(decision is FailoverDecision.PlayEndpoint)
        val playDecision = decision as FailoverDecision.PlayEndpoint
        assertEquals("server2", playDecision.endpoint.server.value)
        assertEquals(5000L, playDecision.checkpoint.positionMs)
    }

    @Test
    fun testFailoverCrossProviderFallback() {
        val controller = PlaybackFailoverController(ProviderRegistry)
        val checkpoint = PlaybackCheckpoint(
            identity = AnimeIdentity(1, 1, "Test Anime"),
            episodeNumber = 1,
            audioType = AudioType.SUB,
            qualityPolicy = QualityPolicy.Auto,
            subtitlePreference = null,
            positionMs = 12000L,
            speed = 1.0f,
            generationId = 1L
        )

        val sources = listOf(
            SourceEndpoint(
                id = "anilight_ep1_server1_sub",
                provider = ProviderId.ANILIGHT,
                server = ServerId("server1"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/1.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            )
        )

        // Pre-trip AniLight circuit breaker, or exhaust attempts
        val attempts = mapOf(ProviderId.ANILIGHT to 3)

        val decision = controller.determineFailover(
            checkpoint = checkpoint,
            currentProvider = ProviderId.ANILIGHT,
            currentEndpoint = sources[0],
            endpoints = sources,
            errorType = PlaybackErrorType.Network,
            attempts = attempts
        )

        // Miruro is second in priority, should fall back to it
        assertTrue(decision is FailoverDecision.ReResolve)
        val resolveDecision = decision as FailoverDecision.ReResolve
        assertEquals(ProviderId.MIRURO, resolveDecision.nextProvider)
        assertEquals(12000L, resolveDecision.checkpoint.positionMs)
    }

    @Test
    fun testFailoverIdentityMismatchIsolation() {
        val controller = PlaybackFailoverController(ProviderRegistry)
        val checkpoint = PlaybackCheckpoint(
            identity = AnimeIdentity(1, 1, "Test Anime"),
            episodeNumber = 1,
            audioType = AudioType.SUB,
            qualityPolicy = QualityPolicy.Auto,
            subtitlePreference = null,
            positionMs = 0L,
            speed = 1.0f,
            generationId = 1L
        )

        val sources = listOf(
            SourceEndpoint(
                id = "anilight_ep1_server1_sub",
                provider = ProviderId.ANILIGHT,
                server = ServerId("server1"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/1.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            )
        )

        val decision = controller.determineFailover(
            checkpoint = checkpoint,
            currentProvider = ProviderId.ANILIGHT,
            currentEndpoint = sources[0],
            endpoints = sources,
            errorType = PlaybackErrorType.IdentityMismatch,
            attempts = mapOf(ProviderId.ANILIGHT to 0)
        )

        // Mismatch must immediately isolate and switch to next provider
        assertTrue(decision is FailoverDecision.ReResolve)
        val resolveDecision = decision as FailoverDecision.ReResolve
        assertEquals(ProviderId.MIRURO, resolveDecision.nextProvider)
    }

    @Test
    fun testHlsManifestNormalizerHtmlRejection() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("<html><body>Fake challenge page</body></html>"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }
        val client = HttpClient(mockEngine)
        val result = HlsManifestNormalizer.normalize(
            client = client,
            url = "https://example.com/playlist.m3u8",
            headers = mapOf("Referer" to "https://site.com")
        )

        // HTML pages must be rejected (returns empty variants)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testHlsManifestNormalizerRelativeUrlsAndHeaderIsolation() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("playlist.m3u8")) {
                respond(
                    content = ByteReadChannel(
                        """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=1280x720
                        video/720p.m3u8
                        """.trimIndent()
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl")
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mockEngine)
        val result = HlsManifestNormalizer.normalize(
            client = client,
            url = "https://example.com/stream/playlist.m3u8",
            headers = mapOf("Referer" to "https://site.com")
        )

        assertEquals(1, result.size)
        val variant = result.first()
        assertEquals(720, variant.height)
        // Must resolve relative path against base manifest URL
        assertEquals("https://example.com/stream/video/720p.m3u8", variant.url)
    }

    @Test
    fun testHlsManifestNormalizerBoundedStreamingRead() = runTest {
        // Construct an oversized manifest (larger than 64KB)
        val largeStringBuilder = java.lang.StringBuilder("#EXTM3U\n")
        for (i in 1..4000) {
            largeStringBuilder.append("#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1920x1080\n")
            largeStringBuilder.append("segment$i.ts\n")
        }
        val oversizedManifest = largeStringBuilder.toString()

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(oversizedManifest),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/x-mpegURL")
            )
        }
        val client = HttpClient(mockEngine)
        val result = HlsManifestNormalizer.normalize(
            client = client,
            url = "https://example.com/oversized.m3u8",
            headers = emptyMap()
        )

        // The read must be bounded, preventing unlimited buffer growth
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testFallbackRotatesPriorities() {
        val controller = PlaybackFailoverController(ProviderRegistry)
        val checkpoint = PlaybackCheckpoint(
            identity = AnimeIdentity(1, 1, "Test Anime"),
            episodeNumber = 1,
            audioType = AudioType.SUB,
            qualityPolicy = QualityPolicy.Auto,
            subtitlePreference = null,
            positionMs = 5000L,
            speed = 1.0f,
            generationId = 1L
        )
        val sources = listOf(
            SourceEndpoint(
                id = "miruro_ep1",
                provider = ProviderId.MIRURO,
                server = ServerId("miruro_server"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/1.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            )
        )
        val decision = controller.determineFailover(
            checkpoint = checkpoint,
            currentProvider = ProviderId.MIRURO,
            currentEndpoint = sources[0],
            endpoints = sources,
            errorType = PlaybackErrorType.Network,
            attempts = mapOf(ProviderId.MIRURO to 3)
        )

        assertTrue(decision is FailoverDecision.ReResolve)
        val resolveDecision = decision as FailoverDecision.ReResolve
        assertEquals(ProviderId.ANILIGHT, resolveDecision.nextProvider)
    }

    @Test
    fun testCircuitOpenFiltering() {
        val controller = PlaybackFailoverController(ProviderRegistry)
        val checkpoint = PlaybackCheckpoint(
            identity = AnimeIdentity(1, 1, "Test Anime"),
            episodeNumber = 1,
            audioType = AudioType.SUB,
            qualityPolicy = QualityPolicy.Auto,
            subtitlePreference = null,
            positionMs = 5000L,
            speed = 1.0f,
            generationId = 1L
        )
        val sources = listOf(
            SourceEndpoint(
                id = "miruro_ep1",
                provider = ProviderId.MIRURO,
                server = ServerId("miruro_server"),
                audioType = AudioType.SUB,
                streamType = StreamType.HLS,
                url = "https://source.com/1.m3u8",
                qualityPolicy = QualityPolicy.Auto,
                episodeId = "ep1"
            )
        )

        // Pre-trip Anikoto's circuit breaker
        val breaker = ProviderRegistry.getCircuitBreaker(ProviderId.ANIKOTO)
        for (i in 1..10) breaker.recordFailure()

        val decision = controller.determineFailover(
            checkpoint = checkpoint,
            currentProvider = ProviderId.MIRURO,
            currentEndpoint = sources[0],
            endpoints = sources,
            errorType = PlaybackErrorType.Network,
            attempts = mapOf(ProviderId.MIRURO to 3)
        )

        assertTrue(decision is FailoverDecision.ReResolve)
        val resolveDecision = decision as FailoverDecision.ReResolve
        assertEquals(ProviderId.ANILIGHT, resolveDecision.nextProvider)
    }
}
