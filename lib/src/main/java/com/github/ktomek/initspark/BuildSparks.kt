package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.AWAITABLE

/**
 * Constructs a SparkConfiguration using a DSL builder.
 *
 * This function:
 * - Validates that no duplicate spark keys are declared.
 * - Validates that all `needs` dependencies point to existing sparks.
 * - Validates that all provided [sparks] are registered via the builder.
 *
 * If any of these conditions are not met, an exception will be thrown.
 *
 * @param sparks A set of Spark instances to be registered and validated.
 * @param block Lambda receiver used to declare sparks.
 * @return A SparkConfiguration containing all declared SparkDeclarations.
 * @throws IllegalStateException if duplicate keys are declared.
 * @throws IllegalArgumentException if a declared dependency is missing.
 */
fun buildSparks(
    sparks: Set<Spark>,
    block: SparkBuilder.() -> Unit
): SparkConfiguration = SparkBuilder(sparks)
    .apply {
        block()
        declarations.checkDuplicates()
        declarations.checkMissingDependencies()
        declarations.checkCycles()
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

private fun List<SparkDeclaration>.checkCycles() {
    val graph = this
        .associateBy { it.key }
        .mapValues { it.value.needs }

    val visited = mutableSetOf<Key>()
    val reStack = mutableSetOf<Key>()
    val path = mutableListOf<Key>()

    fun dfs(key: Key) {
        if (key in reStack) {
            val cycleStartIndex = path.indexOf(key)
            val cyclePath = path.subList(cycleStartIndex, path.size) + key
            error("Cycle detected: ${cyclePath.joinToString(" -> ") { it.value }}")
        }
        if (key in visited) return

        visited.add(key)
        reStack.add(key)
        path.add(key)

        for (neighbor in graph[key].orEmpty()) {
            dfs(neighbor)
        }

        reStack.remove(key)
        path.removeAt(path.size - 1)
    }

    graph.keys.forEach { key -> dfs(key) }
}
