package com.example.aniflow.data

import com.example.aniflow.data.model.HlsVariant
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import java.io.IOException

object HlsManifestNormalizer {

    suspend fun normalize(
        client: HttpClient,
        url: String,
        headers: Map<String, String>
    ): List<HlsVariant> {
        try {
            var currentUrl = url
            var redirectCount = 0
            var finalResponse: HttpResponse? = null
            val sourceHost = Url(url).host

            while (redirectCount < 5) {
                val uri = Url(currentUrl)
                val response = client.get(currentUrl) {
                    // Origin isolation: Only forward headers if target host matches original source host
                    if (uri.host.equals(sourceHost, ignoreCase = true)) {
                        headers.forEach { (k, v) -> header(k, v) }
                    }
                }

                if (response.status == HttpStatusCode.MovedPermanently ||
                    response.status == HttpStatusCode.Found ||
                    response.status == HttpStatusCode.TemporaryRedirect ||
                    response.status == HttpStatusCode.SeeOther
                ) {
                    val location = response.headers["Location"] ?: break
                    currentUrl = resolveUrl(currentUrl, location)
                    redirectCount++
                } else {
                    finalResponse = response
                    break
                }
            }

            val response = finalResponse ?: return emptyList()

            // Reject content-type mismatch (e.g. HTML responses)
            val contentType = response.contentType()
            if (contentType != null && contentType.contentType.equals("text", ignoreCase = true) &&
                contentType.contentSubtype.equals("html", ignoreCase = true)
            ) {
                return emptyList()
            }

            // Bounded streaming read up to 64KB to prevent downloading oversized files
            val channel = response.bodyAsChannel()
            val builder = StringBuilder()
            val buffer = ByteArray(4096)
            var bytesRead = 0
            val maxBytes = 65536 // 64KB

            while (!channel.isClosedForRead && bytesRead < maxBytes) {
                val read = channel.readAvailable(buffer, 0, minOf(buffer.size, maxBytes - bytesRead))
                if (read <= 0) break
                builder.append(String(buffer, 0, read, Charsets.UTF_8))
                bytesRead += read
            }

            val manifestContent = builder.toString()
            val lines = manifestContent.lineSequence().toList()

            val variants = mutableListOf<HlsVariant>()
            var currentStreamInf: String? = null

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    currentStreamInf = trimmed
                } else if (!trimmed.startsWith("#") && currentStreamInf != null) {
                    val variantUrl = resolveUrl(currentUrl, trimmed)
                    val variant = parseStreamInf(currentStreamInf, variantUrl)
                    variants.add(variant)
                    currentStreamInf = null
                }
            }
            return variants
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://", ignoreCase = true) || relativeUrl.startsWith("https://", ignoreCase = true)) {
            return relativeUrl
        }
        val base = Url(baseUrl)
        val portSuffix = if (base.port == 80 || base.port == 443 || base.port == 0) "" else ":${base.port}"
        val hostPart = "${base.host}$portSuffix"
        return if (relativeUrl.startsWith("/")) {
            "${base.protocol.name}://${hostPart}${relativeUrl}"
        } else {
            val basePath = base.encodedPath.substringBeforeLast('/', "")
            if (basePath.isEmpty()) {
                "${base.protocol.name}://${hostPart}/${relativeUrl}"
            } else {
                val path = if (basePath.endsWith("/")) basePath else "$basePath/"
                "${base.protocol.name}://${hostPart}${path}${relativeUrl}"
            }
        }
    }

    private fun parseStreamInf(streamInf: String, url: String): HlsVariant {
        val height = Regex("RESOLUTION=\\d+x(\\d+)").find(streamInf)?.groupValues?.get(1)?.toIntOrNull()
        val bandwidth = Regex("BANDWIDTH=(\\d+)").find(streamInf)?.groupValues?.get(1)?.toLongOrNull()
        val codecs = Regex("CODECS=\"([^\"]+)\"").find(streamInf)?.groupValues?.get(1)
            ?: Regex("CODECS=([^,]+)").find(streamInf)?.groupValues?.get(1)
        val frameRate = Regex("FRAME-RATE=([0-9.]+)").find(streamInf)?.groupValues?.get(1)?.toFloatOrNull()

        return HlsVariant(url, height, bandwidth, codecs, frameRate)
    }
}
