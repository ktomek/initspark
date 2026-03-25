package com.github.ktomek.initspark

/**
 * Encapsulates all spark declarations used for initialization.
 * The structure may change over time without impacting API consumers.
 */
@ConsistentCopyVisibility
data class SparkConfiguration internal constructor(
    internal val declarations: List<SparkDeclaration>
)

/**
 * Returns a Mermaid.js compatible graph string representing the spark initialization dependencies.
 */
fun SparkConfiguration.dumpMermaidGraph(): String {
    val builder = StringBuilder("graph TD\n")

    // Collect AWAITABLE sparks to show their sequential nature
    val awaitables = declarations.filter { it.type == SparkType.AWAITABLE }
    awaitables.zipWithNext().forEach { (first, second) ->
        builder.append("    ${first.key.toSafeString()} --> ${second.key.toSafeString()}\n")
    }

    // Explicit dependencies for TRACKABLE and FIRE_AND_FORGET
    declarations.forEach { declaration ->
        declaration.needs.forEach { dependency ->
            builder.append("    ${dependency.toSafeString()} --> ${declaration.key.toSafeString()}\n")
        }
    }

    return builder.toString()
}

private fun Key.toSafeString(): String = when (this) {
    is StringKey -> value
    else -> this.toString()
}
