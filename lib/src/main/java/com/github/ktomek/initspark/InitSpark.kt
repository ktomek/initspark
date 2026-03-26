package com.github.ktomek.initspark

import com.github.ktomek.funktional.letCo
import com.github.ktomek.funktional.orDefault
import com.github.ktomek.initspark.SparkType.AWAITABLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.ZERO

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

internal class InitSparkImpl(
    private val config: SparkConfiguration,
    private val scope: CoroutineScope
) : InitSpark {

    /**
     * Exposes timing metrics collected during spark execution.
     *
     * Provides duration tracking per spark and per spark type,
     * as well as total and windowed timing statistics.
     */
    override val timing: SparkTimingInfo = SparkTimer.getInstance()
    private val sparkTimer: SparkTimer = SparkTimer.getInstance()
    private val isStarted = AtomicBoolean(false)

    final override val isTrackableInitialized: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = MutableStateFlow(false)

    final override val isInitialized: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = MutableStateFlow(false)

    final override val events: SharedFlow<SparkEvent>
        field: MutableSharedFlow<SparkEvent> = MutableSharedFlow()

    override suspend fun initialize() {
        if (!isStarted.compareAndSet(false, true)) return
        coroutineScope {
            startAwaitable()
            with(createAndRunSparksJobs()) {
                scope.launch {
                    coroutineScope {
                        launch { startAsyncSparks(this@with) }
                        launch { startRunAndForgetSparks(this@with) }
                    }
                    isInitialized.value = true
                }
            }
        }
    }

    override fun initializeBlocking() {
        runBlocking { initialize() }
    }

    private fun createAndRunSparksJobs(): Map<Key, Deferred<Unit>> =
        mutableMapOf<Key, Deferred<Unit>>()
            .apply {
                config
                    .declarations
                    .filter { it.type != AWAITABLE }
                    .forEach { sparkDeclaration ->
                        this[sparkDeclaration.key] = sparkDeclaration.createJob(this)
                    }
                this.forEach { it.value.start() }
            }

    private fun SparkDeclaration.createJob(jobs: Map<Key, Deferred<Unit>>): Deferred<Unit> =
        scope.async(
            context = coroutineContext,
            start = CoroutineStart.LAZY
        ) {
            needs.mapNotNull(jobs::get).awaitAll()
            this@createJob.runWithEvents()
        }

    private suspend fun SparkDeclaration.runWithEvents() {
        events.emit(SparkEvent.Started(key))

        val error = flow<Throwable?> {
            sparkTimer.measure(this@runWithEvents) { spark() }
            emit(null)
        }
            .retryWithPolicy(retryPolicy) { cause, attempt ->
                sparkTimer
                    .durationOf(this@runWithEvents)
                    .orDefault { ZERO }
                    .let { SparkEvent.Retry(key, attempt, it, cause) }
                    .letCo(events::emit)
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(e)
            }
            .first()

        val duration = sparkTimer.durationOf(this) ?: ZERO
        if (error == null) {
            events.emit(SparkEvent.Completed(key, duration))
        } else {
            events.emit(SparkEvent.Failed(key, duration, error))
            if (importance == SparkImportance.CRITICAL) {
                throw error
            }
        }
    }

    private suspend fun startAwaitable() = config
        .declarations
        .filter { it.type == AWAITABLE }
        .forEach { sparkDeclaration -> sparkDeclaration.runWithEvents() }

    private suspend fun startAsyncSparks(jobs: Map<Key, Deferred<Unit>>) {
        config
            .declarations
            .filter { it.type == SparkType.TRACKABLE }
            .map(SparkDeclaration::key)
            .mapNotNull(jobs::get)
            .awaitAll()
        isTrackableInitialized.value = true
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

private fun <T> Flow<T>.retryWithPolicy(
    policy: RetryPolicy?,
    onRetry: suspend (cause: Throwable, attempt: Int) -> Unit
): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) throw cause
    val maxRetries = policy?.retryCount?.toLong() ?: 0L
    if (attempt < maxRetries) {
        val nextAttempt = (attempt + 1).toInt()
        onRetry(cause, nextAttempt)
        policy
            ?.calculateDelay(nextAttempt)
            ?.takeIf { delayMillis -> delayMillis > 0 }
            ?.let { delayMillis -> delay(delayMillis) }
        true
    } else {
        false
    }
}

fun InitSpark(
    config: SparkConfiguration,
    scope: CoroutineScope
): InitSpark = InitSparkImpl(config = config, scope = scope)
