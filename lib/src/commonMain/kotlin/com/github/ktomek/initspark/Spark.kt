package com.github.ktomek.initspark

/**
 * Functional interface representing a unit of initialization logic.
 * This function is expected to be a suspendable operation.
 */
fun interface Spark {
    suspend operator fun invoke()
}
