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
        importance: SparkImportance = SparkImportance.CRITICAL,
        spark: Spark
    ) {
        declarations.add(
            spark.toDeclaration(
                key = key,
                type = AWAITABLE,
                needs = emptySet(),
                context = context,
                importance = importance
            )
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
        importance: SparkImportance = SparkImportance.CRITICAL,
    ) {
        await(key = key, context = context, importance = importance, spark = sparks.requireSpark<T>())
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
        importance: SparkImportance = SparkImportance.CRITICAL,
        spark: Spark
    ) {
        declarations.add(
            spark.toDeclaration(
                key = key,
                type = TRACKABLE,
                needs = needs,
                context = context,
                importance = importance
            )
        )
    }

    inline fun <reified T : Spark> async(
        key: Key? = null,
        needs: Set<Key> = emptySet(),
        context: CoroutineContext = EmptyCoroutineContext,
        importance: SparkImportance = SparkImportance.CRITICAL,
    ) {
        async(key = key, needs = needs, context = context, importance = importance, spark = sparks.requireSpark<T>())
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
        importance: SparkImportance = SparkImportance.CRITICAL,
        spark: Spark
    ) {
        declarations.add(
            spark.toDeclaration(
                key = key,
                type = FIRE_AND_FORGET,
                needs = needs,
                context = context,
                importance = importance
            )
        )
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
        importance: SparkImportance = SparkImportance.CRITICAL,
    ) {
        spark(key = key, needs = needs, context = context, importance = importance, spark = sparks.requireSpark<T>())
    }

    private fun Spark.toDeclaration(
        key: Key? = null,
        type: SparkType,
        needs: Set<Key>,
        context: CoroutineContext,
        importance: SparkImportance,
    ): SparkDeclaration = SparkDeclaration(
        key = key ?: javaClass.simpleName.asKey(),
        needs = needs,
        type = type,
        coroutineContext = context,
        importance = importance,
        spark = this
    )

    /**
     * Returns all collected SparkDeclarations wrapped in a SparkConfiguration.
     *
     * @return SparkConfiguration built by this builder.
     */
    internal fun build(): SparkConfiguration = SparkConfiguration(declarations)
}
