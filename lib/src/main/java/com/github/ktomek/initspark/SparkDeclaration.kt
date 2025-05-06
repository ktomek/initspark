package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.FIRE_AND_FORGET
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@JvmInline
value class Key(val value: String)

fun String.asKey(): Key = Key(this)

/**
 * Holds metadata about a declared Spark, including its key, dependencies,
 * coroutine context, and type.
 *
 * @property key Unique identifier of the Spark.
 * @property needs Set of Spark keys that must be initialized before this one.
 * @property type Classification of the Spark's execution behavior.
 * @property coroutineContext Coroutine context in which the Spark runs.
 * @property spark The actual Spark implementation.
 */
@ConsistentCopyVisibility
data class SparkDeclaration
internal constructor(
    val key: Key,
    val needs: Set<Key> = emptySet(),
    val type: SparkType = FIRE_AND_FORGET,
    val coroutineContext: CoroutineContext = EmptyCoroutineContext,
    val spark: Spark
)

/**
 * Enum representing the type of Spark execution behavior.
 *
 * - [AWAITABLE]: Executed sequentially in a blocking manner.
 * - [TRACKABLE]: Runs asynchronously and updates state once complete.
 * - [FIRE_AND_FORGET]: Fire-and-forget execution without tracking.
 */
enum class SparkType {
    AWAITABLE,
    TRACKABLE,
    FIRE_AND_FORGET
}
