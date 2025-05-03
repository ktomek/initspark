package com.github.ktomek.initspark

/**
 * Retrieves the first instance of the specified Spark subtype from the set.
 *
 * @return the first Spark instance of type [T], or throws [NoSuchElementException] if not found.
 */
inline fun <reified T> Set<Spark>.get(): T = filterIsInstance<T>().first()

/**
 * Retrieves and removes the first element of the specified subtype from the set.
 *
 * @return the first Spark of type [T], or throws [IllegalArgumentException] if not found.
 */
inline fun <reified T : Spark> MutableSet<Spark>.getAndRemove(): T = filterIsInstance<T>()
    .firstOrNull()
    ?.also(::remove)
    ?: throw IllegalArgumentException("Missing element in list")
