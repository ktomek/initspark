package com.github.ktomek.initspark

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Exposes app‑wide init state: `false` until all Awaitable initializers are done, then `true`.
 */
interface SparkState {
    /**
     * Emits true once all [com.github.ktomek.initspark.SparkType.TRACKABLE] sparks have completed.
     */
    val isTrackableInitialized: StateFlow<Boolean>

    /**
     * Emits true once all [com.github.ktomek.initspark.SparkType.TRACKABLE]
     * and [com.github.ktomek.initspark.SparkType.FIRE_AND_FORGET] sparks have completed.
     */
    val isInitialized: StateFlow<Boolean>

    /**
     * Emits lifecycle events as sparks are started, completed, or failed.
     */
    val events: SharedFlow<SparkEvent>
}

/**
 * Sealed interface representing atomic lifecycle events emitted by the InitSpark orchestrator.
 */
sealed interface SparkEvent {
    val key: Key

    data class Started(override val key: Key) : SparkEvent
    data class Completed(override val key: Key, val duration: Duration) : SparkEvent
    data class Failed(override val key: Key, val duration: Duration, val error: Throwable) : SparkEvent
    data class Retry(
        override val key: Key,
        val retryCount: Int,
        val duration: Duration,
        val error: Throwable
    ) : SparkEvent
}
