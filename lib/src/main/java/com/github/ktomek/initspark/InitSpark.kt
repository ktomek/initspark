package com.github.ktomek.initspark

import kotlinx.coroutines.CoroutineScope

/**
 * Initializes and manages the lifecycle of a set of SparkDeclarations.
 *
 * The InitSpark interface coordinates the execution of different types of sparks:
 * - Awaitable: executed synchronously and sequentially.
 * - Trackable: executed asynchronously and signals completion via [isTrackableInitialized].
 * - Run-and-forget: executed asynchronously without tracking completion.
 */
interface InitSpark : SparkState {

    /**
     * Exposes timing metrics collected during spark execution.
     *
     * Provides duration tracking per spark and per spark type,
     * as well as total and windowed timing statistics.
     */
    val timing: SparkTimingInfo

    /**
     * Starts the initialization process of all defined sparks suspending during execution.
     *
     * It ensures no duplicate keys exist, all dependencies are satisfied,
     * and then launches appropriate runners for each type of spark.
     */
    suspend fun initialize()

    /**
     * Starts the initialization process in a blocking manner using runBlocking.
     * Provided for backward compatibility and pure Java usage.
     */
    fun initializeBlocking()
}

fun InitSpark(
    config: SparkConfiguration,
    scope: CoroutineScope
): InitSpark = InitSparkImpl(config = config, scope = scope)
