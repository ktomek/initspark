package com.github.ktomek.initspark

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
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
                    EmptyCoroutineContext,
                    awaitableSpark
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
                        "await".asKey(),
                        emptySet(),
                        SparkType.AWAITABLE,
                        EmptyCoroutineContext,
                        awaitableSpark
                    ),
                    SparkDeclaration(
                        "track".asKey(),
                        emptySet(),
                        SparkType.TRACKABLE,
                        EmptyCoroutineContext,
                        trackableSpark
                    ),
                    SparkDeclaration(
                        "default".asKey(),
                        emptySet(),
                        SparkType.FIRE_AND_FORGET,
                        EmptyCoroutineContext,
                        spark
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
                    "s1".asKey(),
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    spark1
                ),
                SparkDeclaration(
                    "s2".asKey(),
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    spark2
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
                spark("t".asKey(), emptySet(), EmptyCoroutineContext, spark)
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
                        "d".asKey(),
                        emptySet(),
                        SparkType.FIRE_AND_FORGET,
                        EmptyCoroutineContext,
                        spark
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
                    "shared".asKey(),
                    setOf("first".asKey()),
                    SparkType.TRACKABLE,
                    EmptyCoroutineContext,
                    shared
                ),
                SparkDeclaration(
                    "first".asKey(),
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    first
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
                        "dep".asKey(),
                        emptySet(),
                        SparkType.AWAITABLE,
                        EmptyCoroutineContext,
                        dep
                    ),
                    SparkDeclaration(
                        "a".asKey(),
                        setOf("dep".asKey()),
                        SparkType.TRACKABLE,
                        EmptyCoroutineContext,
                        a
                    ),
                    SparkDeclaration(
                        "b".asKey(),
                        setOf("dep".asKey()),
                        SparkType.FIRE_AND_FORGET,
                        EmptyCoroutineContext,
                        b
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
                    "a".asKey(),
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    a
                ),
                SparkDeclaration(
                    "b".asKey(),
                    setOf("a".asKey()),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    b
                ),
                SparkDeclaration(
                    "c".asKey(),
                    setOf("b".asKey()),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    c
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
}
