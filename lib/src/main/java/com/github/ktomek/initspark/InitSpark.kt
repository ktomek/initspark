package com.github.ktomek.initspark

import com.github.ktomek.initspark.SparkType.AWAITABLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * - Trackable: executed asynchronously and signals completion via [isTrackAbleInitialized].
 * - Run-and-forget: executed asynchronously without tracking completion.
 *
 * @property config List of declared spark initialization tasks.
 * @property scope CoroutineScope used for launching asynchronous tasks.
 */
class InitSpark internal constructor(
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
     * Emits true once all TRACKABLE sparks have completed.
     */
    override val isTrackAbleInitialized: StateFlow<Boolean> = _isTrackAbleInitialized.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)

    /**
     * Emits true once all DEFAULT (run-and-forget) sparks have completed.
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
            checkDuplicates()
            checkMissingDependencies()
            startAwaitable()
            with(createAndRunSparksJobs()) {
                startAsyncSparks(this)
                startRunAndForgetSparks(this)
            }
        }
    }

    private fun createSparksMap(): Map<Key, SparkDeclaration> = config
        .declarations
        .filter { it.type != AWAITABLE }
        .associateBy { it.key }

    private fun createAndRunSparksJobs(): Map<Key, Deferred<Unit>> =
        mutableMapOf<Key, Deferred<Unit>>()
            .apply {
                config
                    .declarations
                    .filter { it.type != AWAITABLE }
                    .forEach { sparkDeclaration ->
                        this[sparkDeclaration.key] = sparkDeclaration.createJob(this)
                    }
            }
            .onEach { (_, job) -> job.start() }

    private fun SparkDeclaration.createJob(jobs: Map<Key, Deferred<Unit>>): Deferred<Unit> =
        scope.async(
            context = coroutineContext,
            start = CoroutineStart.LAZY
        ) {
            needs.mapNotNull(jobs::get).awaitAll()
            sparkTimer.measure(this@createJob) { spark() }
        }

    private fun checkMissingDependencies() {
        val sparksMap = createSparksMap()
        config
            .declarations
            .flatMap { it.needs }
            .forEach { need -> require(sparksMap.contains(need)) { "Initializer $need not found" } }
    }

    private fun checkDuplicates() = config
        .declarations
        .groupingBy { it.key }
        .eachCount()
        .filterValues { it > 1 }
        .forEach { (key, _) -> error("$key is duplicated") }

    private suspend fun startAwaitable() = config
        .declarations
        .filter { it.type == AWAITABLE }
        .forEach { sparkDeclaration ->
            sparkTimer.measure(sparkDeclaration) { sparkDeclaration.spark() }
        }

    private fun startAsyncSparks(jobs: Map<Key, Deferred<Unit>>) {
        scope.launch {
            config
                .declarations
                .filter { it.type == SparkType.TRACKABLE }
                .map(SparkDeclaration::key)
                .mapNotNull(jobs::get)
                .awaitAll()
            _isTrackAbleInitialized.update { true }
        }
    }

    private fun startRunAndForgetSparks(jobs: Map<Key, Deferred<Unit>>) {
        scope.launch {
            config
                .declarations
                .filter { it.type == SparkType.DEFAULT }
                .map(SparkDeclaration::key)
                .mapNotNull(jobs::get)
                .awaitAll()
            _isInitialized.update { true }
        }
    }

    companion object {
        @Volatile
        private var instance: InitSpark? = null

        /**
         * Factory method to obtain a singleton instance of InitSpark.
         *
         * @param config Configuration containing all spark declarations.
         * @param scope Coroutine scope used for asynchronous operations.
         * @return a singleton instance of InitSpark.
         */
        fun getInstance(config: SparkConfiguration, scope: CoroutineScope): InitSpark =
            instance ?: synchronized(this) {
                instance ?: InitSpark(config, scope).also { instance = it }
            }
    }
}
