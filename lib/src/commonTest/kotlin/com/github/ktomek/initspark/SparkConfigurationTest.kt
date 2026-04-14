package com.github.ktomek.initspark

import kotlin.test.Test
import kotlin.test.assertEquals

class SparkConfigurationTest {

    @Test
    fun `GIVEN configuration WHEN dumpMermaidGraph called THEN dependencies are returned`() {
        val spark1 = Spark { }
        val spark2 = Spark { }
        val spark3 = Spark { }

        val config = buildSparks(setOf(spark1, spark2, spark3)) {
            async("s1".asKey(), spark = spark1)
            async("s2".asKey(), needs = setOf("s1".asKey()), spark = spark2)
            async("s3".asKey(), needs = setOf("s2".asKey()), spark = spark3)
        }

        val expected = """
            graph TD
                s1 --> s2
                s2 --> s3
        """.trimIndent()

        assertEquals(expected.trim(), config.dumpMermaidGraph().trim())
    }
}
