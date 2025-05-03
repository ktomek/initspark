package com.github.ktomek.initspark

/**
 * Encapsulates all spark declarations used for initialization.
 * The structure may change over time without impacting API consumers.
 */
@ConsistentCopyVisibility
data class SparkConfiguration internal constructor(
    internal val declarations: List<SparkDeclaration>
)
