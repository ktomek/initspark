package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.AWAITABLE
import com.github.ktomek.initspark.SparkType.FIRE_AND_FORGET
import com.github.ktomek.initspark.SparkType.TRACKABLE
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
     * @param spark The Spark instance to be registered.
     */
    fun await(
        key: Key? = null,
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(
            key,
            AWAITABLE,
            emptySet(),
            context,
            spark
        )
    }

    /**
     * Declares a sequential spark using a reified type lookup.
     *
     * This function fetches a spark instance of type [T] from the registered spark set
     * and adds it as an awaitable (sequential) task.
     *
     * @param key Unique identifier for the spark.
     * @param context Coroutine context in which to run the spark.
     */
    inline fun <reified T : Spark> await(
        key: Key? = null,
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        await(key = key, context = context, spark = sparks.requireSpark<T>())
    }

    /**
     * Declares an asynchronously executed spark that sets state when complete.
     *
     * @param key Unique identifier for the spark.
     * @param needs Dependencies to be resolved before this spark runs.
     * @param context Coroutine context in which to run the spark.
     * @param spark The Spark instance to be registered.
     */
    fun async(
        key: Key? = null,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(key, TRACKABLE, needs, context, spark)
    }

    inline fun <reified T : Spark> async(
        key: Key? = null,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        async(key = key, needs = needs, context = context, spark = sparks.requireSpark<T>())
    }

    /**
     * Declares a fire-and-forget spark that is not tracked for completion.
     *
     * @param key Unique identifier for the spark.
     * @param needs Dependencies to be resolved before this spark runs.
     * @param context Coroutine context in which to run the spark.
     * @param spark The Spark instance to be registered.
     */
    fun spark(
        key: Key? = null,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
        spark: Spark
    ) {
        addDeclaration(key, FIRE_AND_FORGET, needs, context, spark)
    }

    /**
     * Declares a fire-and-forget spark using a reified type lookup.
     *
     * @param key Unique identifier for the spark.
     * @param needs Dependencies to be resolved before this spark runs.
     * @param context Coroutine context in which to run the spark.
     */
    inline fun <reified T : Spark> spark(
        key: Key? = null,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
    ) {
        spark(key = key, needs = needs, context = context, spark = sparks.requireSpark<T>())
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
        key: Key? = null,
        type: SparkType,
        needs: Set<Key>,
        context: CoroutineContext,
        spark: Spark
    ) {
        declarations.add(
            SparkDeclaration(
                key = key ?: spark.javaClass.simpleName.asKey(),
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
