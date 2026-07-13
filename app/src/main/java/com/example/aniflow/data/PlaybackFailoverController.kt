package com.example.aniflow.data

import com.example.aniflow.data.model.*

data class PlaybackCheckpoint(
    val identity: AnimeIdentity,
    val episodeNumber: Int,
    val audioType: AudioType,
    val qualityPolicy: QualityPolicy,
    val subtitlePreference: SubtitleTrack?,
    val positionMs: Long,
    val speed: Float,
    val generationId: Long
)

sealed interface FailoverDecision {
    data class PlayEndpoint(val endpoint: SourceEndpoint, val checkpoint: PlaybackCheckpoint) : FailoverDecision
    data class ReResolve(val nextProvider: ProviderId, val checkpoint: PlaybackCheckpoint) : FailoverDecision
    data object PlaybackFailed : FailoverDecision
}

class PlaybackFailoverController(
    private val providerRegistry: ProviderRegistry = ProviderRegistry
) {
    private val maxAttemptsPerProvider = 3

    fun determineFailover(
        checkpoint: PlaybackCheckpoint,
        currentProvider: ProviderId,
        currentEndpoint: SourceEndpoint?,
        endpoints: List<SourceEndpoint>,
        errorType: PlaybackErrorType,
        attempts: Map<ProviderId, Int>
    ): FailoverDecision {
        val currentProviderAttempts = attempts[currentProvider] ?: 0

        // 1. IdentityMismatch: immediately invalidate and reject this provider, try backup provider
        if (errorType == PlaybackErrorType.IdentityMismatch) {
            val breaker = providerRegistry.getCircuitBreaker(currentProvider)
            breaker.recordFailure()
            return fallbackToNextProvider(checkpoint, currentProvider, attempts)
        }

        // Record failure in circuit breaker for specific types
        if (errorType == PlaybackErrorType.Network || errorType == PlaybackErrorType.NoSources) {
            providerRegistry.getCircuitBreaker(currentProvider).recordFailure()
        }

        // 2. Try same-provider recovery first if attempts are not exhausted
        if (currentProviderAttempts < maxAttemptsPerProvider) {
            // Under RateLimited or 401/403/410 (expiry/access error), try other servers or re-resolve
            if (errorType == PlaybackErrorType.RateLimited || 
                errorType == PlaybackErrorType.Network || 
                errorType == PlaybackErrorType.InvalidatedMapping
            ) {
                // Try to find an alternate server for the current provider
                val alternateEndpoint = endpoints.firstOrNull { endpoint ->
                    endpoint.provider == currentProvider &&
                    endpoint.server != currentEndpoint?.server &&
                    providerRegistry.getCircuitBreaker(currentProvider).canExecute()
                }

                if (alternateEndpoint != null) {
                    return FailoverDecision.PlayEndpoint(alternateEndpoint, checkpoint)
                }

                // If no alternative servers, attempt to re-resolve the current provider
                if (currentProviderAttempts < maxAttemptsPerProvider - 1) {
                    return FailoverDecision.ReResolve(currentProvider, checkpoint)
                }
            }
        }

        // 3. Fallback to next provider in priority order
        return fallbackToNextProvider(checkpoint, currentProvider, attempts)
    }

    private fun fallbackToNextProvider(
        checkpoint: PlaybackCheckpoint,
        currentProvider: ProviderId,
        attempts: Map<ProviderId, Int>
    ): FailoverDecision {
        val activeProviders = providerRegistry.getActiveProviders()
        val allProviders = mutableListOf<ProviderId>()
        allProviders.add(currentProvider)
        allProviders.addAll(activeProviders.filter { it != currentProvider }.sortedBy { providerRegistry.getPriority(it) })

        val currentIndex = allProviders.indexOf(currentProvider)
        if (currentIndex != -1) {
            // Scan next providers in priority
            for (i in (currentIndex + 1) until allProviders.size) {
                val nextProvider = allProviders[i]
                val breaker = providerRegistry.getCircuitBreaker(nextProvider)
                val nextAttempts = attempts[nextProvider] ?: 0

                if (breaker.canExecute() && nextAttempts < maxAttemptsPerProvider) {
                    return FailoverDecision.ReResolve(nextProvider, checkpoint)
                }
            }
        }

        return FailoverDecision.PlaybackFailed
    }
}
