package com.github.ktomek.initspark

import kotlin.math.pow

/**
 * Strategy for delay between retry attempts.
 */
sealed interface Backoff {
    /** No delay between retries. */
    data object None : Backoff

    /** Fixed delay between retries. */
    data class Fixed(val delayMillis: Long) : Backoff

    /**
     * Exponential delay between retries.
     * Starts with [initialDelayMillis] and multiplies by [factor] each time.
     */
    data class Exponential(val initialDelayMillis: Long, val factor: Double = 2.0) : Backoff
}

/**
 * Policy defining how a Spark should be retried upon failure.
 *
 * @property retryCount Number of times to retry before giving up.
 * @property backoff Strategy to determine the delay between retries.
 */
data class RetryPolicy(
    val retryCount: Int,
    val backoff: Backoff = Backoff.None
) {
    /**
     * Calculates the delay for a given retry [attempt].
     * @param attempt The 1-based index of the retry attempt.
     */
    fun calculateDelay(attempt: Int): Long =
        when (backoff) {
            is Backoff.None -> 0L
            is Backoff.Fixed -> backoff.delayMillis
            is Backoff.Exponential -> calculateExponentialDelay(backoff, attempt)
        }

    private fun calculateExponentialDelay(
        backoff: Backoff.Exponential,
        attempt: Int
    ): Long = (backoff.initialDelayMillis * backoff.factor.pow(attempt - 1)).toLong()
}
