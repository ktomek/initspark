package com.github.ktomek.initspark

import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InitSparkTest {

    @Test
    fun `GIVEN config with all spark types WHEN initialize called THEN all sparks run and state updated`() =
        runTest {
            val awaitableSpark = mockk<Spark>(relaxed = true)
            val trackableSpark = mockk<Spark>(relaxed = true)
            val defaultSpark = mockk<Spark>(relaxed = true)

            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        "await",
                        emptySet(),
                        SparkType.AWAITABLE,
                        EmptyCoroutineContext,
                        awaitableSpark
                    ),
                    SparkDeclaration(
                        "track",
                        emptySet(),
                        SparkType.TRACKABLE,
                        EmptyCoroutineContext,
                        trackableSpark
                    ),
                    SparkDeclaration(
                        "default",
                        emptySet(),
                        SparkType.DEFAULT,
                        EmptyCoroutineContext,
                        defaultSpark
                    )
                )
            )

            val initSpark = InitSpark(config, this)

            initSpark.initialize()
            advanceUntilIdle()

            coVerify { awaitableSpark.invoke() }
            coVerify { trackableSpark.invoke() }
            coVerify { defaultSpark.invoke() }
            assertTrue(initSpark.isTrackAbleInitialized.first())
            assertTrue(initSpark.isInitialized.first())
        }

    @Test
    fun `GIVEN multiple awaitable sparks WHEN initialized THEN they run sequentially`() = runTest {
        val spark1 = mockk<Spark>(relaxed = true)
        val spark2 = mockk<Spark>(relaxed = true)

        val config = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    "s1",
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    spark1
                ),
                SparkDeclaration(
                    "s2",
                    emptySet(),
                    SparkType.AWAITABLE,
                    EmptyCoroutineContext,
                    spark2
                )
            )
        )

        val initSpark = InitSpark(config, CoroutineScope(Dispatchers.Default))
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
            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        "t",
                        emptySet(),
                        SparkType.TRACKABLE,
                        EmptyCoroutineContext,
                        spark
                    )
                )
            )
            val initSpark = InitSpark(config, CoroutineScope(Dispatchers.Default))

            initSpark.initialize()
            assertTrue(initSpark.isTrackAbleInitialized.first())
        }

    @Test
    fun `GIVEN default spark WHEN initialized THEN isInitialized becomes true after run`() =
        runTest {
            val spark = mockk<Spark>(relaxed = true)
            val config = SparkConfiguration(
                listOf(
                    SparkDeclaration(
                        "d",
                        emptySet(),
                        SparkType.DEFAULT,
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
                    "shared",
                    setOf("first"),
                    SparkType.TRACKABLE,
                    EmptyCoroutineContext,
                    shared
                ),
                SparkDeclaration(
                    "first",
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
                        "dep",
                        emptySet(),
                        SparkType.AWAITABLE,
                        EmptyCoroutineContext,
                        dep
                    ),
                    SparkDeclaration(
                        "a",
                        setOf("dep"),
                        SparkType.TRACKABLE,
                        EmptyCoroutineContext,
                        a
                    ),
                    SparkDeclaration("b", setOf("dep"), SparkType.DEFAULT, EmptyCoroutineContext, b)
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
                SparkDeclaration("a", emptySet(), SparkType.AWAITABLE, EmptyCoroutineContext, a),
                SparkDeclaration("b", setOf("a"), SparkType.AWAITABLE, EmptyCoroutineContext, b),
                SparkDeclaration("c", setOf("b"), SparkType.AWAITABLE, EmptyCoroutineContext, c)
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
