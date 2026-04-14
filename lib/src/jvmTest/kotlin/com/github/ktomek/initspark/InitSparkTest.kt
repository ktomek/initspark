package com.github.ktomek.initspark

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class InitSparkTest {

    @Test
    fun `GIVEN config with all spark types WHEN initialize called THEN all sparks run`() =
        runTest {
            val awaitableSpark = mockk<Spark>(relaxed = true)
            val trackableSpark = mockk<Spark>(relaxed = true)
            val defaultSpark = mockk<Spark>(relaxed = true)

            val config = buildSparks(emptySet()) {
                await(
                    "await".asKey(),
                    spark = awaitableSpark
                )
                async(
                    "track".asKey(),
                    context = EmptyCoroutineContext,
                    spark = trackableSpark
                )
                spark(
                    "default".asKey(),
                    context = EmptyCoroutineContext,
                    spark = defaultSpark
                )
            }

            val initSpark = InitSpark(config, this)

            initSpark.initialize()
            advanceUntilIdle()

            coVerify { awaitableSpark.invoke() }
            coVerify { trackableSpark.invoke() }
            coVerify { defaultSpark.invoke() }
        }

    @Test
    fun `GIVEN config with all spark types WHEN initialize called THEN state updated`() =
        runTest {
            val awaitableSpark = mockk<Spark>(relaxed = true)
            val trackableSpark = mockk<Spark> {
                coEvery { this@mockk.invoke() } coAnswers { delay(100) }
            }
            val spark = mockk<Spark> {
                coEvery { this@mockk.invoke() } coAnswers { delay(200) }
            }

            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        key = "await".asKey(),
                        needs = emptySet(),
                        type = SparkType.AWAITABLE,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = awaitableSpark
                    ),
                    SparkDeclaration(
                        key = "track".asKey(),
                        needs = emptySet(),
                        type = SparkType.TRACKABLE,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = trackableSpark
                    ),
                    SparkDeclaration(
                        key = "default".asKey(),
                        needs = emptySet(),
                        type = SparkType.FIRE_AND_FORGET,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = spark
                    )
                )
            )

            val initSpark = InitSpark(config, this)

            initSpark.initialize()
            coVerify { awaitableSpark.invoke() }
            assertFalse(initSpark.isTrackableInitialized.first())
            assertFalse(initSpark.isInitialized.first())

            advanceTimeBy(110.milliseconds)
            coVerify { trackableSpark.invoke() }
            assertTrue(initSpark.isTrackableInitialized.first())
            assertFalse(initSpark.isInitialized.first())

            advanceTimeBy(110.milliseconds)
            coVerify { spark.invoke() }
            assertTrue(initSpark.isTrackableInitialized.first())
            assertTrue(initSpark.isInitialized.first())
        }

    @Test
    fun `GIVEN multiple awaitable sparks WHEN initialized THEN they run sequentially`() = runTest {
        val spark1 = mockk<Spark>(relaxed = true)
        val spark2 = mockk<Spark>(relaxed = true)

        val config = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    key = "s1".asKey(),
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = spark1
                ),
                SparkDeclaration(
                    key = "s2".asKey(),
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = spark2
                )
            )
        )

        val initSpark = InitSpark(config, this)
        initSpark.initialize()

        coVerifyOrder {
            spark1.invoke()
            spark2.invoke()
        }
    }

    @Test
    fun `GIVEN trackable spark WHEN initialized THEN isTrackAbleInitialized becomes true after run`() =
        runTest {
            val spark = mockk<Spark>(relaxed = true)
            val config = buildSparks(emptySet()) {
                async("t".asKey(), spark = spark)
            }
            val initSpark = InitSpark(config, this)

            initSpark.isTrackableInitialized.test {
                assertFalse(awaitItem())
                initSpark.initialize()
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `GIVEN default spark WHEN initialized THEN isInitialized becomes true after run`() =
        runTest {
            val spark = mockk<Spark>(relaxed = true)
            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        key = "d".asKey(),
                        needs = emptySet(),
                        type = SparkType.FIRE_AND_FORGET,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = spark
                    )
                )
            )
            val initSpark = InitSpark(config, this)

            initSpark.initialize()

            advanceUntilIdle()
            assertTrue(initSpark.isInitialized.first())
        }

    @Test
    fun `GIVEN await spark used by async WHEN initialized THEN await called once`() = runTest {
        val shared = mockk<Spark>(relaxed = true)
        val first = mockk<Spark>(relaxed = true)

        val config = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    key = "shared".asKey(),
                    needs = setOf("first".asKey()),
                    type = SparkType.TRACKABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = shared
                ),
                SparkDeclaration(
                    key = "first".asKey(),
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = first
                )
            )
        )

        val initSpark = InitSpark(config, this)
        initSpark.initialize()
        advanceUntilIdle()

        coVerify(exactly = 1) { first.invoke() }
    }

    @Test
    fun `GIVEN shared dependency used by multiple WHEN initialized THEN dependency runs once`() =
        runTest {
            val dep = mockk<Spark>(relaxed = true)
            val a = mockk<Spark>(relaxed = true)
            val b = mockk<Spark>(relaxed = true)

            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        key = "dep".asKey(),
                        needs = emptySet(),
                        type = SparkType.AWAITABLE,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = dep
                    ),
                    SparkDeclaration(
                        key = "a".asKey(),
                        needs = setOf("dep".asKey()),
                        type = SparkType.TRACKABLE,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = a
                    ),
                    SparkDeclaration(
                        key = "b".asKey(),
                        needs = setOf("dep".asKey()),
                        type = SparkType.FIRE_AND_FORGET,
                        coroutineContext = EmptyCoroutineContext,
                        policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                        spark = b
                    )
                )
            )

            val initSpark = InitSpark(config, this)
            initSpark.initialize()

            coVerify(exactly = 1) { dep.invoke() }
        }

    @Test
    fun `GIVEN dependent sparks WHEN initialized THEN runs in correct order`() = runTest {
        val a = mockk<Spark>(relaxed = true)
        val b = mockk<Spark>(relaxed = true)
        val c = mockk<Spark>(relaxed = true)

        val config = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    key = "a".asKey(),
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = a
                ),
                SparkDeclaration(
                    key = "b".asKey(),
                    needs = setOf("a".asKey()),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = b
                ),
                SparkDeclaration(
                    key = "c".asKey(),
                    needs = setOf("b".asKey()),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    policy = SparkPolicy(importance = SparkImportance.CRITICAL),
                    spark = c
                )
            )
        )

        val initSpark = InitSpark(config, this)
        initSpark.initialize()

        coVerifyOrder {
            a.invoke()
            b.invoke()
            c.invoke()
        }
    }

    @Test
    fun `GIVEN initialized InitSpark WHEN initialize called again THEN sparks run only once`() = runTest {
        val spark = mockk<Spark>(relaxed = true)
        val config = buildSparks(emptySet()) {
            await("s".asKey(), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.initialize()
        initSpark.initialize()

        coVerify(exactly = 1) { spark.invoke() }
    }

    @Test
    fun `GIVEN in progress InitSpark WHEN initialize called again THEN second call ignored`() = runTest {
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } coAnswers { delay(1000) }
        }
        val config = buildSparks(emptySet()) {
            await("s".asKey(), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        launch { initSpark.initialize() }
        delay(100)
        initSpark.initialize()

        advanceUntilIdle()
        coVerify(exactly = 1) { spark.invoke() }
    }

    @Test
    fun `GIVEN initialized InitSpark WHEN initialize called THEN events are emitted`() = runTest {
        val spark = mockk<Spark>(relaxed = true)
        val config = buildSparks(emptySet()) {
            await("s".asKey(), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.events.test {
            initSpark.initialize()
            assertTrue(awaitItem() is SparkEvent.Started)
            assertTrue(awaitItem() is SparkEvent.Completed)
        }
    }

    @Test
    fun `GIVEN failing spark WHEN initialize called THEN Failed event is emitted`() = runTest {
        val error = RuntimeException("Oh no!")
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws error
        }
        val config = buildSparks(emptySet()) {
            await("s".asKey(), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.events.test {
            assertFailsWith<RuntimeException> {
                initSpark.initialize()
            }
            assertTrue(awaitItem() is SparkEvent.Started)
            val failed = awaitItem() as SparkEvent.Failed
            assertEquals(error, failed.error)
            assertEquals("s".asKey(), failed.key)
        }
    }

    @Test
    fun `GIVEN cancelled spark WHEN initialize called THEN no Failed event is emitted`() = runTest {
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws CancellationException("Cancelled")
        }
        val config = buildSparks(emptySet()) {
            await("s".asKey(), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.events.test {
            assertFailsWith<CancellationException> {
                initSpark.initialize()
            }
            assertTrue(awaitItem() is SparkEvent.Started)
            expectNoEvents()
        }
    }

    @Test
    fun `GIVEN failing optional spark WHEN initialize called THEN initialize does not throw`() = runTest {
        val error = RuntimeException("Optional failure")
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws error
        }
        val config = buildSparks(emptySet()) {
            await(key = "s".asKey(), policy = SparkPolicy(importance = SparkImportance.OPTIONAL), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.events.test {
            initSpark.initialize() // Should not throw
            assertTrue(awaitItem() is SparkEvent.Started)
            val failed = awaitItem() as SparkEvent.Failed
            assertEquals(error, failed.error)
        }
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `GIVEN spark with retryCount WHEN fails twice and succeeds on third THEN initialization completes`() =
        runTest {
            val spark = mockk<Spark>()
            var attempts = 0
            coEvery { spark.invoke() } coAnswers {
                attempts++
                if (attempts < 3) throw RuntimeException("Fail $attempts")
            }

            val config = buildSparks(emptySet()) {
                await(key = "retry".asKey(), policy = SparkPolicy(retry = RetryPolicy(2)), spark = spark)
            }
            val initSpark = InitSpark(config, this)

            initSpark.events.test {
                initSpark.initialize()
                assertTrue(awaitItem() is SparkEvent.Started)
                val retry1 = awaitItem() as SparkEvent.Retry
                assertEquals(1, retry1.retryCount)
                val retry2 = awaitItem() as SparkEvent.Retry
                assertEquals(2, retry2.retryCount)
                assertTrue(awaitItem() is SparkEvent.Completed)
                expectNoEvents()
            }

            coVerify(exactly = 3) { spark.invoke() }
        }

    @Test
    fun `GIVEN spark with retryCount WHEN all attempts fail THEN initialization fails`() = runTest {
        val error = RuntimeException("Total failure")
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws error
        }

        val config = buildSparks(emptySet()) {
            await(key = "fail".asKey(), policy = SparkPolicy(retry = RetryPolicy(2)), spark = spark)
        }
        val initSpark = InitSpark(config, this)

        initSpark.events.test {
            assertFailsWith<RuntimeException> {
                initSpark.initialize()
            }
            assertTrue(awaitItem() is SparkEvent.Started)
            assertTrue(awaitItem() is SparkEvent.Retry)
            assertTrue(awaitItem() is SparkEvent.Retry)
            val failed = awaitItem() as SparkEvent.Failed
            assertEquals(error, failed.error)
        }

        coVerify(exactly = 3) { spark.invoke() }
    }

    @Test
    fun `GIVEN spark with Fixed backoff WHEN retrying THEN delay is respected`() = runTest {
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws RuntimeException("Fail")
        }

        val config = buildSparks(emptySet()) {
            await(
                key = "delay".asKey(),
                policy = SparkPolicy(retry = RetryPolicy(1, Backoff.Fixed(1000))),
                spark = spark
            )
        }
        val initSpark = InitSpark(config, this)

        launch {
            assertFailsWith<RuntimeException> {
                initSpark.initialize()
            }
        }

        advanceTimeBy(500)
        coVerify(exactly = 1) { spark.invoke() } // Initial attempt

        advanceTimeBy(600)
        coVerify(exactly = 2) { spark.invoke() } // Retry after 1000ms
    }

    @Test
    fun `GIVEN spark with Exponential backoff WHEN retrying THEN delay increases`() = runTest {
        val spark = mockk<Spark> {
            coEvery { this@mockk.invoke() } throws RuntimeException("Fail")
        }

        val config = buildSparks(emptySet()) {
            await(
                key = "expo".asKey(),
                policy = SparkPolicy(retry = RetryPolicy(2, Backoff.Exponential(1000, 2.0))),
                spark = spark
            )
        }
        val initSpark = InitSpark(config, this)

        launch {
            assertFailsWith<RuntimeException> {
                initSpark.initialize()
            }
        }

        advanceTimeBy(500)
        coVerify(exactly = 1) { spark.invoke() } // Initial attempt

        advanceTimeBy(600)
        coVerify(exactly = 2) { spark.invoke() } // First retry after 1000ms

        advanceTimeBy(1500)
        coVerify(exactly = 2) { spark.invoke() } // Still waiting for second retry (need 2000ms more)

        advanceTimeBy(600)
        coVerify(exactly = 3) { spark.invoke() } // Second retry after 2000ms more
    }
}
