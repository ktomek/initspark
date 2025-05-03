package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.AWAITABLE

/**
 * Constructs a SparkConfiguration using a DSL builder.
 *
 * Validates that all provided [sparks] are registered through the builder.
 * If any are missing, it throws an IllegalArgumentException.
 *
 * @param sparks A set of Spark instances to be registered and validated.
 * @param block Lambda receiver used to declare sparks.
 * @return A SparkConfiguration containing all declared SparkDeclarations.
 */
fun buildSparks(
    sparks: Set<Spark>,
    block: SparkBuilder.() -> Unit
): SparkConfiguration = SparkBuilder(sparks)
    .apply {
        block()
        declarations.checkDuplicates()
        declarations.checkMissingDependencies()
        sparks.forEach { spark ->
            require(declarations.any { it.spark == spark }) {
                "Not all sparks has been registered ${spark.javaClass.name}"
            }
        }
    }
    .build()

private fun List<SparkDeclaration>.checkDuplicates() = this
    .groupingBy { it.key }
    .eachCount()
    .filterValues { it > 1 }
    .forEach { (key, _) -> error("$key is duplicated") }

private fun List<SparkDeclaration>.createSparksMap(): Map<Key, SparkDeclaration> = this
    .filter { it.type != AWAITABLE }
    .associateBy { it.key }

private fun List<SparkDeclaration>.checkMissingDependencies() {
    val sparksMap = createSparksMap()
    flatMap { it.needs }
        .forEach { need -> require(sparksMap.contains(need)) { "Initializer $need not found" } }
}
