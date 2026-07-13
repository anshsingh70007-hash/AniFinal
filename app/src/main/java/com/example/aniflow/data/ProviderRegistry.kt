package com.example.aniflow.data

import com.example.aniflow.BuildConfig
import com.example.aniflow.data.model.ProviderId

object ProviderRegistry {
    var forceEnableAllForTesting = false

    private val circuitBreakers = mapOf(
        ProviderId.ANILIGHT to CircuitBreaker(),
        ProviderId.MIRURO to CircuitBreaker(),
        ProviderId.ANIKOTO to CircuitBreaker()
    )

    fun isProviderEnabled(provider: ProviderId): Boolean {
        return true
    }

    fun getCircuitBreaker(provider: ProviderId): CircuitBreaker {
        return circuitBreakers[provider] ?: throw IllegalArgumentException("Unknown provider: $provider")
    }

    fun getActiveProviders(): List<ProviderId> {
        return ProviderId.entries.filter { isProviderEnabled(it) }
    }

    fun getPriority(provider: ProviderId): Int {
        return when (provider) {
            ProviderId.ANILIGHT -> 1
            ProviderId.MIRURO -> 2
            ProviderId.ANIKOTO -> 3
        }
    }
}
