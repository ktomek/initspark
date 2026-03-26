package com.github.ktomek.initspark

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetryPolicyTest {

    @Test
    fun `GIVEN Backoff None WHEN calculateDelay THEN returns 0`() {
        val policy = RetryPolicy(3, Backoff.None)
        assertEquals(0L, policy.calculateDelay(1))
        assertEquals(0L, policy.calculateDelay(2))
        assertEquals(0L, policy.calculateDelay(3))
    }

    @Test
    fun `GIVEN Backoff Fixed WHEN calculateDelay THEN returns fixed value`() {
        val policy = RetryPolicy(3, Backoff.Fixed(1000))
        assertEquals(1000L, policy.calculateDelay(1))
        assertEquals(1000L, policy.calculateDelay(2))
        assertEquals(1000L, policy.calculateDelay(3))
    }

    @Test
    fun `GIVEN Backoff Exponential WHEN calculateDelay THEN returns increasing values`() {
        val policy = RetryPolicy(3, Backoff.Exponential(1000, 2.0))
        // attempt is 1-based index (1st retry attempt)
        // 1st retry: 1000 * 2^0 = 1000
        assertEquals(1000L, policy.calculateDelay(1))
        // 2nd retry: 1000 * 2^1 = 2000
        assertEquals(2000L, policy.calculateDelay(2))
        // 3rd retry: 1000 * 2^2 = 4000
        assertEquals(4000L, policy.calculateDelay(3))
    }

    @Test
    fun `GIVEN Backoff Exponential with factor 1_5 WHEN calculateDelay THEN returns correct values`() {
        val policy = RetryPolicy(3, Backoff.Exponential(1000, 1.5))
        assertEquals(1000L, policy.calculateDelay(1))
        assertEquals(1500L, policy.calculateDelay(2))
        assertEquals(2250L, policy.calculateDelay(3))
    }
}
