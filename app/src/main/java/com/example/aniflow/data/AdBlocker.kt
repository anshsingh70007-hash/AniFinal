package com.example.aniflow.data

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import java.io.IOException

object AdBlocker {
    private val blockedDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "adnxs.com", "popads.net", "popunder.net",
        "ad.doubleclick.net", "googleadservices.com", "moatads.com", "amazon-adsystem.com",
        "facebook.com/tr", "scorecardresearch.com", "quantserve.com", "outbrain.com", "taboola.com",
        "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "sharethrough.com", "bidswitch.net", "contextweb.com", "media.net", "yieldmo.com",
        "advertising.com", "adsrvr.org", "adcolony.com", "unity3d.com/ads", "applovin.com",
        "vungle.com", "ironsrc.com", "mopub.com", "inmobi.com", "chartboost.com", "tapjoy.com",
        "admob.com", "smaato.net", "flurry.com", "adjust.com", "branch.io", "kochava.com",
        "appsflyer.com", "singular.net", "tenjin.com", "mixpanel.com", "amplitude.com",
        "segment.io", "track.", ".ads.", "pagead", "cdn-cgi/trace", "adsboosters.xyz",
        "sad.adsboosters.xyz", "googletagmanager.com", "google-analytics.com"
    )

    fun shouldBlock(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return blockedDomains.any { lowerUrl.contains(it) }
    }

    fun filterHeaders(headers: Map<String, String>): Map<String, String> {
        val sensitiveHeaders = setOf("cookie", "user-agent-original", "x-client-data")
        return headers.filterKeys { it.lowercase() !in sensitiveHeaders }
    }
}

class AdBlockingDataSource(private val wrappedDataSource: DataSource) : DataSource by wrappedDataSource {
    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString()
        if (AdBlocker.shouldBlock(uriString)) {
            throw IOException("Request blocked by AdBlocker: $uriString")
        }
        return wrappedDataSource.open(dataSpec)
    }
}

class AdBlockingDataSourceFactory(private val baseDataSourceFactory: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return AdBlockingDataSource(baseDataSourceFactory.createDataSource())
    }
}
