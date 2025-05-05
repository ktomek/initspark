package com.github.ktomek.initspark

import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InitSparkKtKtTest {

    @Test
    fun `GIVEN uninitialized InitSpark WHEN true is emitted THEN waitUntilInitialized returns`() = runTest {
        val initSpark = object : InitSpark {
            override val isTrackableInitialized: StateFlow<Boolean> = MutableStateFlow(false)
            override val isInitialized = MutableStateFlow(false)
            override val timing = mockk<SparkTimingInfo>(relaxed = true)
            override fun initialize() = Unit
        }

        val job = launch { initSpark.waitUntilInitialized() }

        assertFalse(job.isCompleted)
        initSpark.isInitialized.value = true
        job.join()
        assertTrue(job.isCompleted)
    }
}
