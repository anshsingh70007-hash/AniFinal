package com.example.aniflow.data

interface Clock {
    fun elapsedRealtime(): Long
}

class SystemClock : Clock {
    override fun elapsedRealtime(): Long {
        return android.os.SystemClock.elapsedRealtime()
    }
}

class CircuitBreaker(
    private val clock: Clock = SystemClock(),
    val minSampleCount: Int = 10,
    val failureThreshold: Double = 0.5,
    val cooldownMs: Long = 60_000L
) {
    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }

    private var currentState = State.CLOSED
    private val samples = mutableListOf<Boolean>() // true = success, false = failure
    private var lastStateChangeTime: Long = clock.elapsedRealtime()

    fun getState(): State {
        val now = clock.elapsedRealtime()
        if (currentState == State.OPEN && now - lastStateChangeTime >= cooldownMs) {
            currentState = State.HALF_OPEN
            lastStateChangeTime = now
            samples.clear()
        }
        return currentState
    }

    @Synchronized
    fun canExecute(): Boolean {
        return getState() != State.OPEN
    }

    @Synchronized
    fun recordSuccess() {
        // Trigger state transition if in Half-Open
        if (getState() == State.HALF_OPEN) {
            currentState = State.CLOSED
            lastStateChangeTime = clock.elapsedRealtime()
            samples.clear()
        } else {
            samples.add(true)
            trimSamples()
        }
    }

    @Synchronized
    fun recordFailure() {
        val now = clock.elapsedRealtime()
        val state = getState()
        if (state == State.HALF_OPEN) {
            currentState = State.OPEN
            lastStateChangeTime = now
            samples.clear()
        } else {
            samples.add(false)
            trimSamples()
            if (state == State.CLOSED && samples.size >= minSampleCount) {
                val failureCount = samples.count { !it }
                val failureRate = failureCount.toDouble() / samples.size
                if (failureRate >= failureThreshold) {
                    currentState = State.OPEN
                    lastStateChangeTime = now
                    samples.clear()
                }
            }
        }
    }

    @Synchronized
    fun reset() {
        currentState = State.CLOSED
        samples.clear()
        lastStateChangeTime = clock.elapsedRealtime()
    }

    private fun trimSamples() {
        if (samples.size > 50) {
            samples.removeAt(0)
        }
    }
}
