package com.github.ktomek.initspark

import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes app‑wide init state: `false` until all Awaitable initializers are done, then `true`.
 */
interface SparkState {
    val isTrackableInitialized: StateFlow<Boolean>
    val isInitialized: StateFlow<Boolean>
}
