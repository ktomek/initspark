package com.github.ktomek.initspark

import kotlinx.coroutines.flow.StateFlow

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
}
