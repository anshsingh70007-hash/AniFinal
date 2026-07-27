package com.example.aniflow.data

import com.example.aniflow.data.repository.DefaultAnimeRepository
import kotlinx.coroutines.*

class SelfHealingEngine(private val repository: DefaultAnimeRepository) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            android.util.Log.d("SelfHealingEngine", "Self-Healing Engine started in backend.")
            while (isActive) {
                try {
                    // Periodic heal cycle every 5 minutes
                    runHealingCycle()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("SelfHealingEngine", "Error in self-healing execution cycle", e)
                }
                delay(5 * 60 * 1000L) // 5 minutes
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        android.util.Log.d("SelfHealingEngine", "Self-Healing Engine stopped.")
    }

    private fun runHealingCycle() {
        android.util.Log.d("SelfHealingEngine", "Starting automatic healing cycle...")
        
        // Domain 3: Stuck-Open Circuit Breakers Healer
        try {
            ProviderRegistry.healAllCircuitBreakers()
        } catch (e: Exception) {
            android.util.Log.e("SelfHealingEngine", "Failed to heal circuit breakers", e)
        }

        // Domain 2: Repository Cache Healer (Evicts stale popular, trending, seasonal, schedule, etc.)
        try {
            repository.healAllCaches()
        } catch (e: Exception) {
            android.util.Log.e("SelfHealingEngine", "Failed to heal repo caches", e)
        }

        android.util.Log.d("SelfHealingEngine", "Automatic healing cycle completed successfully.")
    }
}
