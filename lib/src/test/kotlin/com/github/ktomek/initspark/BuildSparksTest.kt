package com.github.ktomek.initspark

import io.mockk.mockk
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildSparksTest {

    @Test
    fun `GIVEN sparks and builder WHEN buildSparks called THEN returns expected SparkConfiguration`() {
        val logger = Spark { }
        val warmup = Spark { }
        val upgrade = Spark { }
        val all = setOf(logger, warmup, upgrade)

        val config = buildSparks(sparks = all) {
            await("logger".asKey(), spark = logger)
            spark("warmup".asKey(), spark = warmup)
            async("upgrade".asKey(), spark = upgrade, needs = setOf("warmup".asKey()))
        }

        val expected = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    key = "logger".asKey(),
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    spark = logger
                ),
                SparkDeclaration(
                    key = "warmup".asKey(),
                    needs = emptySet(),
                    type = SparkType.FIRE_AND_FORGET,
                    coroutineContext = EmptyCoroutineContext,
                    spark = warmup
                ),
                SparkDeclaration(
                    key = "upgrade".asKey(),
                    needs = setOf("warmup".asKey()),
                    type = SparkType.TRACKABLE,
                    coroutineContext = EmptyCoroutineContext,
                    spark = upgrade
                )
            )
        )

        assertEquals(expected, config)
    }

    @Test
    fun `GIVEN missing spark WHEN buildSparks called THEN throws`() {
        val declared = Spark {}
        val other = Spark {}

        assertFailsWith<IllegalArgumentException> {
            buildSparks(sparks = setOf(declared)) {
                spark("oops".asKey(), spark = other)
            }
        }
    }

    @Test
    fun `GIVEN duplicate spark keys WHEN buildSparks called THEN throws`() {
        val logger = Spark {}
        val warmup = Spark {}

        assertFailsWith<IllegalStateException> {
            buildSparks(sparks = setOf(logger, warmup)) {
                await("dup".asKey(), spark = mockk<Spark>())
                async("dup".asKey(), spark = mockk<Spark>())
            }
        }
    }

    @Test
    fun `GIVEN unmet spark dependency WHEN buildSparks called THEN throws`() {
        val logger = Spark {}
        val warmup = Spark {}

        assertFailsWith<IllegalArgumentException>(
            "Initializer non-existent not found"
        ) {
            buildSparks(sparks = setOf(logger, warmup)) {
                await(key = "logger".asKey(), spark = logger)
                async(key = "warmup".asKey(), needs = setOf("non-existent".asKey()), spark = warmup)
            }
        }
    }

    @Test
    fun `GIVEN cyclic spark dependencies WHEN buildSparks called THEN throws`() {
        val a = Spark {}
        val b = Spark {}
        val c = Spark {}

        val exception = assertFailsWith<IllegalStateException> {
            buildSparks(sparks = setOf(a, b, c)) {
                async("A".asKey(), spark = a, needs = setOf("C".asKey()))
                async("B".asKey(), spark = b, needs = setOf("A".asKey()))
                async("C".asKey(), spark = c, needs = setOf("B".asKey()))
            }
        }
        assertEquals("Cycle detected: A -> C -> B -> A", exception.message)
    }
}
