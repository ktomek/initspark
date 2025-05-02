package com.github.ktomek.initspark

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Encapsulates all spark declarations used for initialization.
 * The structure may change over time without impacting API consumers.
 */
class SparkConfiguration internal constructor(
    internal val declarations: List<SparkDeclaration>
)

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
    sparks: Set<Spark> = emptySet(),
    block: SparkBuilder.() -> Unit
): SparkConfiguration = SparkBuilder(sparks)
    .apply {
        block()
        sparks.forEach { spark ->
            require(declarations.any { it.spark == spark }) {
                "Not all sparks has been registered ${spark.javaClass.name}"
            }
        }
    }
    .build()

/**
 * DSL builder class for defining Spark declarations.
 */
class SparkBuilder internal constructor(val sparks: Set<Spark>) {
    internal val declarations = mutableListOf<SparkDeclaration>()

    /**
     * Declares a spark that will run sequentially and block until complete.
     *
     * @param key Unique identifier for the spark.
     * @param context Coroutine context in which to run the spark.
     * @param spark Lambda returning a Spark instance.
     */
    fun await(
        key: Key,
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(
            key,
            SparkType.AWAITABLE,
            emptySet(),
            context,
            spark
        )
    }

    inline fun <reified T : Spark> await(
        key: Key,
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        await(key = key, context = context, spark = sparks.get<T>())
    }

    /**
     * Declares an asynchronously executed spark that sets state when complete.
     *
     * @param key Unique identifier for the spark.
     * @param needs Dependencies to be resolved before this spark runs.
     * @param context Coroutine context in which to run the spark.
     * @param spark Lambda returning a Spark instance.
     */
    fun async(
        key: Key,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(key, SparkType.TRACKABLE, needs, context, spark)
    }

    inline fun <reified T : Spark> async(
        key: Key,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        async(key = key, needs = needs, context = context, spark = sparks.get<T>())
    }

    /**
     * Declares a fire-and-forget spark that is not tracked for completion.
     *
     * @param key Unique identifier for the spark.
     * @param needs Dependencies to be resolved before this spark runs.
     * @param context Coroutine context in which to run the spark.
     * @param spark Lambda returning a Spark instance.
     */
    fun spark(
        key: Key,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(key, SparkType.DEFAULT, needs, context, spark)
    }

    inline fun <reified T : Spark> spark(
        key: Key,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        spark(key = key, needs = needs, context = context, spark = sparks.get<T>())
    }

    /**
     * Internal helper to create and store a SparkDeclaration.
     *
     * @param key Unique key for the spark.
     * @param type Type of the spark execution.
     * @param needs Dependency keys required before execution.
     * @param context Coroutine context.
     * @param spark Lambda providing the Spark instance.
     */
    private fun addDeclaration(
        key: Key,
        type: SparkType,
        needs: Set<Key>,
        context: CoroutineContext,
        spark: Spark
    ) {
        declarations.add(
            SparkDeclaration(
                key = key,
                needs = needs,
                type = type,
                coroutineContext = context,
                spark = spark
            )
        )
    }

    /**
     * Returns all collected SparkDeclarations wrapped in a SparkConfiguration.
     *
     * @return SparkConfiguration built by this builder.
     */
    internal fun build(): SparkConfiguration = SparkConfiguration(declarations)
}

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
