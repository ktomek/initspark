package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.AWAITABLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Initializes and manages the lifecycle of a set of SparkDeclarations.
 *
 * The InitSpark class coordinates the execution of different types of sparks:
 * - Awaitable: executed synchronously and sequentially.
 * - Trackable: executed asynchronously and signals completion via [isTrackableInitialized].
 * - Run-and-forget: executed asynchronously without tracking completion.
 *
 * @property config List of declared spark initialization tasks.
 * @property scope CoroutineScope used for launching asynchronous tasks.
 */
class InitSpark(
    private val config: SparkConfiguration,
    private val scope: CoroutineScope
) : SparkState {

    /**
     * Exposes timing metrics collected during spark execution.
     *
     * Provides duration tracking per spark and per spark type,
     * as well as total and windowed timing statistics.
     */
    val timing: SparkTimingInfo = SparkTimer.getInstance()
    private val sparkTimer: SparkTimer = SparkTimer.getInstance()

    private val _isTrackAbleInitialized = MutableStateFlow(false)

    /**
     * Emits true once all [com.github.ktomek.initspark.SparkType.TRACKABLE] sparks have completed.
     */
    override val isTrackableInitialized: StateFlow<Boolean> = _isTrackAbleInitialized.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)

    /**
     * Emits true once all [com.github.ktomek.initspark.SparkType.TRACKABLE]
     * and [com.github.ktomek.initspark.SparkType.FIRE_AND_FORGET] sparks have completed.
     */
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * Starts the initialization process of all defined sparks.
     *
     * It ensures no duplicate keys exist, all dependencies are satisfied,
     * and then launches appropriate runners for each type of spark.
     */
    fun initialize() {
        runBlocking {
            startAwaitable()
            with(createAndRunSparksJobs()) {
                scope.launch {
                    coroutineScope {
                        launch { startAsyncSparks(this@with) }
                        launch { startRunAndForgetSparks(this@with) }
                    }
                    _isInitialized.update { true }
                }
            }
        }
    }

    private fun createAndRunSparksJobs(): Map<Key, Deferred<Unit>> =
        mutableMapOf<Key, Deferred<Unit>>()
            .apply {
                config
                    .declarations
                    .filter { it.type != AWAITABLE }
                    .forEach { sparkDeclaration ->
                        put(sparkDeclaration.key, sparkDeclaration.createJob(this))
                    }
                this.forEach { it.value.start() }
            }

    private fun SparkDeclaration.createJob(jobs: Map<Key, Deferred<Unit>>): Deferred<Unit> =
        scope.async(
            context = coroutineContext,
            start = CoroutineStart.LAZY
        ) {
            needs.mapNotNull(jobs::get).awaitAll()
            sparkTimer.measure(this@createJob) { spark() }
        }

    private suspend fun startAwaitable() = config
        .declarations
        .filter { it.type == AWAITABLE }
        .forEach { sparkDeclaration ->
            sparkTimer.measure(sparkDeclaration) { sparkDeclaration.spark() }
        }

    private suspend fun startAsyncSparks(jobs: Map<Key, Deferred<Unit>>) {
        config
            .declarations
            .filter { it.type == SparkType.TRACKABLE }
            .map(SparkDeclaration::key)
            .mapNotNull(jobs::get)
            .awaitAll()
        _isTrackAbleInitialized.update { true }
    }

    private suspend fun startRunAndForgetSparks(jobs: Map<Key, Deferred<Unit>>) {
        config
            .declarations
            .filter { it.type == SparkType.FIRE_AND_FORGET }
            .map(SparkDeclaration::key)
            .mapNotNull(jobs::get)
            .awaitAll()
    }
}
