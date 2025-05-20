package com.github.ktomek.initspark

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class SparkBuilderTest {

    @Test
    fun `GIVEN SparkBuilder WHEN await called THEN adds AWAITABLE declaration`() = runTest {
        val spark = mockk<Spark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "await-key".asKey()

        builder.await(key, spark = spark)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.AWAITABLE,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WHEN async called THEN adds TRACKABLE declaration`() = runTest {
        val spark = mockk<Spark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "async-key".asKey()

        builder.async(key, spark = spark)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.TRACKABLE,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WHEN spark called THEN adds DEFAULT declaration`() = runTest {
        val spark = mockk<Spark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "spark-key".asKey()

        builder.spark(key, spark = spark)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.FIRE_AND_FORGET,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WITH reified await WHEN spark is in set THEN it resolves correctly`() = runTest {
        val spark = mockk<TestSpark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "await-key".asKey()

        builder.await<TestSpark>(key)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.AWAITABLE,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WITH reified async WHEN spark is in set THEN it resolves correctly`() = runTest {
        val spark = mockk<TestSpark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "async-key".asKey()

        builder.async<TestSpark>(key)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.TRACKABLE,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WITH reified spark WHEN spark is in set THEN it resolves correctly`() = runTest {
        val spark = mockk<TestSpark>()
        val builder = SparkBuilder(setOf(spark))
        val key = "spark-key".asKey()

        builder.spark<TestSpark>(key)

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = key,
                needs = emptySet(),
                type = SparkType.FIRE_AND_FORGET,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    @Test
    fun `GIVEN SparkBuilder WITH reified spark without key WHEN spark is in set THEN it resolves correctly`() = runTest {
        val spark = TestSparkImpl()
        val builder = SparkBuilder(setOf(spark))

        builder.spark<TestSpark>()

        val declaration = builder.build().declarations.single()
        assertEquals(
            SparkDeclaration(
                key = "TestSparkImpl".asKey(),
                needs = emptySet(),
                type = SparkType.FIRE_AND_FORGET,
                coroutineContext = EmptyCoroutineContext,
                spark = spark
            ),
            declaration
        )
    }

    private interface TestSpark : Spark

    private class TestSparkImpl : TestSpark {
        override suspend fun invoke() = Unit
    }
}
