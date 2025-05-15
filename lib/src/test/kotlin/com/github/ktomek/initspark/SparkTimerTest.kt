package com.github.ktomek.initspark

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark

@OptIn(ExperimentalTime::class)
class SparkTimerTest {

    @Test
    fun `GIVEN spark declarations WHEN start and stop called THEN timing is recorded`() = runTest {
        val marks = listOf(
            FakeTimeMark(60)
        )
        val provider = FakeTimeProvider(marks)
        val timer = SparkTimer(provider)
        val spark = testDeclaration("A".asKey(), SparkType.AWAITABLE)

        timer.start(spark)
        timer.stop(spark)

        assertEquals(60.milliseconds, timer.durationOf(spark))
    }

    @Test
    fun `GIVEN multiple sparks of same type WHEN stopped THEN grouped timing is summed`() =
        runTest {
            val marks = listOf(
                FakeTimeMark(20), // start spark1
                FakeTimeMark(20), // start spark2
            )
            val provider = FakeTimeProvider(marks)
            val timer = SparkTimer(provider)
            val spark1 = testDeclaration("A".asKey(), SparkType.TRACKABLE)
            val spark2 = testDeclaration("B".asKey(), SparkType.TRACKABLE)

            timer.start(spark1)
            timer.stop(spark1)
            timer.start(spark2)
            timer.stop(spark2)

            assertEquals(40.milliseconds, timer.sumOfDurationsByType()[SparkType.TRACKABLE])
        }

    @Test
    fun `GIVEN first and last spark WHEN stopped THEN executionDelta is valid`() = runTest {
        val firstTimeMark = mockk<TimeMark> {
            every { elapsedNow() } returnsMany listOf(
                10.milliseconds,
                35.milliseconds
            )
        }
        val marks = listOf(
            firstTimeMark,
            FakeTimeMark(25), // start spark2
        )
        val provider = FakeTimeProvider(marks)
        val timer = SparkTimer(provider)
        val spark1 = testDeclaration("X".asKey(), SparkType.AWAITABLE)
        val spark2 = testDeclaration("Y".asKey(), SparkType.AWAITABLE)

        timer.start(spark1)
        timer.stop(spark1)
        timer.start(spark2)
        timer.stop(spark2)

        val delta = timer.executionDelta()
        // windowDuration = last stop - first start = 45 - 10 = 35
        assertEquals(35.milliseconds, delta)
    }

    @Test
    fun `GIVEN different spark types WHEN measured THEN executionDeltaByType returns correct ranges`() =
        runTest {
            val firstTimeMark = mockk<TimeMark> {
                every { elapsedNow() } returnsMany listOf(
                    5.milliseconds,
                    35.milliseconds
                )
            }
            val marks = listOf(
                firstTimeMark, // start spark1 (TRACKABLE)
                FakeTimeMark(30), // start spark3 (TRACKABLE)
                FakeTimeMark(20), // start spark2 (DEFAULT)
            )
            val provider = FakeTimeProvider(marks)
            val timer = SparkTimer(provider)
            val spark1 = testDeclaration("X".asKey(), SparkType.TRACKABLE)
            val spark2 = testDeclaration("Y".asKey(), SparkType.FIRE_AND_FORGET)
            val spark3 = testDeclaration("Y".asKey(), SparkType.TRACKABLE)

            timer.start(spark1)
            timer.start(spark3)
            timer.stop(spark1)
            timer.start(spark2)
            timer.stop(spark2)
            timer.stop(spark3)

            val delta = timer.executionDeltaByType()
            assertEquals(35.milliseconds, delta[SparkType.TRACKABLE])
            assertEquals(20.milliseconds, delta[SparkType.FIRE_AND_FORGET])
        }

    @Test
    fun `GIVEN multiple sparks WHEN measured THEN total returns cumulative duration`() = runTest {
        val marks = listOf(
            FakeTimeMark(15),
            FakeTimeMark(25),
            FakeTimeMark(10),
            FakeTimeMark(8),
        )
        val provider = FakeTimeProvider(marks)
        val timer = SparkTimer(provider)
        val spark1 = testDeclaration("A".asKey(), SparkType.FIRE_AND_FORGET)
        val spark2 = testDeclaration("B".asKey(), SparkType.TRACKABLE)
        val spark3 = testDeclaration("C".asKey(), SparkType.TRACKABLE)
        val spark4 = testDeclaration("D".asKey(), SparkType.FIRE_AND_FORGET)

        timer.start(spark1)
        timer.stop(spark1)
        timer.start(spark2)
        timer.stop(spark2)
        timer.start(spark3)
        timer.stop(spark3)
        timer.start(spark4)
        timer.stop(spark4)

        assertEquals(58.milliseconds, timer.sumOfDurations())
    }

    @Test
    fun `GIVEN multiple sparks WHEN measured THEN all returns correct durations`() = runTest {
        val marks = listOf(
            FakeTimeMark(10),
            FakeTimeMark(20)
        )
        val provider = FakeTimeProvider(marks)
        val timer = SparkTimer(provider)
        val spark1 = testDeclaration("X".asKey(), SparkType.AWAITABLE)
        val spark2 = testDeclaration("Y".asKey(), SparkType.TRACKABLE)

        timer.start(spark1)
        timer.stop(spark1)
        timer.start(spark2)
        timer.stop(spark2)

        val results = timer.allDurations()
        assertEquals(10.milliseconds, results[spark1])
        assertEquals(20.milliseconds, results[spark2])
    }

    @Test
    fun `GIVEN spark not started WHEN stop called THEN throws`() = runTest {
        val provider = FakeTimeProvider(listOf(FakeTimeMark(10)))
        val timer = SparkTimer(provider)
        val spark = testDeclaration("Z".asKey(), SparkType.AWAITABLE)

        assertFailsWith<IllegalStateException> {
            timer.stop(spark)
        }
    }

    @Test
    fun `GIVEN spark declaration WHEN measured with inline function THEN duration is recorded`() = runTest {
        val marks = listOf(
            FakeTimeMark(100),
            FakeTimeMark(160)
        )
        val provider = FakeTimeProvider(marks)
        val timer = SparkTimer(provider)
        val spark = testDeclaration("M".asKey(), SparkType.TRACKABLE)
        timer.measure(spark) {
            // Simulated work
        }

        assertEquals(100.milliseconds, timer.durationOf(spark))
    }

    private fun testDeclaration(
        key: Key,
        type: SparkType
    ) = SparkDeclaration(
        key = key,
        needs = emptySet(),
        type = type,
        coroutineContext = EmptyCoroutineContext,
        spark = Spark {}
    )

    private class FakeTimeMark(private val duration: Long) : TimeMark {
        override fun elapsedNow(): Duration = duration.milliseconds
        override operator fun plus(duration: Duration): TimeMark =
            FakeTimeMark(this@FakeTimeMark.duration + duration.inWholeMilliseconds)

        override operator fun minus(duration: Duration): TimeMark =
            FakeTimeMark(this@FakeTimeMark.duration - duration.inWholeMilliseconds)
    }

    private class FakeTimeProvider(private val marks: List<TimeMark>) : TimeProvider {
        private var index = 0
        override fun markNow(): TimeMark = marks[index++]
    }
}
