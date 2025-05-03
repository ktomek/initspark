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
            await("logger", spark = logger)
            spark("warmup", spark = warmup)
            async("upgrade", spark = upgrade, needs = setOf("warmup"))
        }

        val expected = SparkConfiguration(
            listOf(
                SparkDeclaration(
                    key = "logger",
                    needs = emptySet(),
                    type = SparkType.AWAITABLE,
                    coroutineContext = EmptyCoroutineContext,
                    spark = logger
                ),
                SparkDeclaration(
                    key = "warmup",
                    needs = emptySet(),
                    type = SparkType.DEFAULT,
                    coroutineContext = EmptyCoroutineContext,
                    spark = warmup
                ),
                SparkDeclaration(
                    key = "upgrade",
                    needs = setOf("warmup"),
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
                spark("oops", spark = other)
            }
        }
    }

    @Test
    fun `GIVEN duplicate spark keys WHEN buildSparks called THEN throws`() {
        val logger = Spark {}
        val warmup = Spark {}

        assertFailsWith<IllegalStateException> {
            buildSparks(sparks = setOf(logger, warmup)) {
                await("dup", spark = mockk<Spark>())
                async("dup", spark = mockk<Spark>())
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
                await(key = "logger", spark = logger)
                async(key = "warmup", needs = setOf("non-existent"), spark = warmup)
            }
        }
    }
}
