package com.example.aniflow.data

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import android.net.Uri

class AdBlockedException(message: String) : IOException(message)

object AdBlocker {
    private val blockedDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "adnxs.com", "popads.net", "popunder.net",
        "ad.doubleclick.net", "googleadservices.com", "moatads.com", "amazon-adsystem.com",
        "facebook.com", "scorecardresearch.com", "quantserve.com", "outbrain.com", "taboola.com",
        "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "sharethrough.com", "bidswitch.net", "contextweb.com", "media.net", "yieldmo.com",
        "advertising.com", "adsrvr.org", "adcolony.com", "unity3d.com", "applovin.com",
        "vungle.com", "ironsrc.com", "mopub.com", "inmobi.com", "chartboost.com", "tapjoy.com",
        "admob.com", "smaato.net", "flurry.com", "adjust.com", "branch.io", "kochava.com",
        "appsflyer.com", "singular.net", "tenjin.com", "mixpanel.com", "amplitude.com",
        "segment.io", "adsboosters.xyz", "sad.adsboosters.xyz", "googletagmanager.com", "google-analytics.com"
    )

    private val blockedPaths = setOf(
        "pagead", "cdn-cgi/trace"
    )

    private val allowedHeaders = setOf(
        "referer", "origin", "user-agent", "accept", "accept-encoding", "accept-language"
    )

    fun shouldBlock(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            val isBlockedHost = blockedDomains.any { blocked ->
                host == blocked || host.endsWith(".$blocked")
            }
            if (isBlockedHost) return true

            val path = uri.path?.lowercase() ?: ""
            val isBlockedPath = blockedPaths.any { blocked ->
                path.contains(blocked)
            }
            isBlockedPath
        } catch (e: Exception) {
            false
        }
    }

    fun filterHeadersForHost(headers: Map<String, String>, sourceUrl: String, targetUrl: String): Map<String, String> {
        val sourceHost = try { Uri.parse(sourceUrl).host?.lowercase() } catch (e: Exception) { null }
        val targetHost = try { Uri.parse(targetUrl).host?.lowercase() } catch (e: Exception) { null }

        if (sourceHost == null || targetHost == null || sourceHost != targetHost) {
            // If target host does not match source host, do not forward custom headers
            return emptyMap()
        }

        return headers.filter { (key, _) ->
            val lowerKey = key.lowercase()
            lowerKey in allowedHeaders && lowerKey != "range"
        }
    }
}

class AdBlockingDataSource(private val wrappedDataSource: DataSource) : DataSource by wrappedDataSource {
    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString()
        if (AdBlocker.shouldBlock(uriString)) {
            throw AdBlockedException("Request blocked by AdBlocker: $uriString")
        }
        return wrappedDataSource.open(dataSpec)
    }
}

class AdBlockingDataSourceFactory(private val baseDataSourceFactory: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return AdBlockingDataSource(baseDataSourceFactory.createDataSource())
    }
}
