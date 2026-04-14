package com.github.ktomek.initspark

import com.github.ktomek.funktional.orDefault
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

@Suppress("TooManyFunctions")
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
    private var isStarted = false
    private val initMutex = Mutex()

    private val _isTrackableInitialized = MutableStateFlow(false)
    final override val isTrackableInitialized: StateFlow<Boolean> get() = _isTrackableInitialized

    private val _isInitialized = MutableStateFlow(false)
    final override val isInitialized: StateFlow<Boolean> get() = _isInitialized

    private val _events = MutableSharedFlow<SparkEvent>()
    final override val events: SharedFlow<SparkEvent> get() = _events

    override suspend fun initialize() {
        initMutex.withLock {
            if (isStarted) return
            isStarted = true
        }
        coroutineScope {
            startAwaitable()
            with(createAndRunSparksJobs()) {
                scope.launch {
                    coroutineScope {
                        launch { startAsyncSparks(this@with) }
                        launch { startRunAndForgetSparks(this@with) }
                    }
                    _isInitialized.value = true
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
                    .filter { it.type != SparkType.AWAITABLE }
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
        flow { emit(sparkTimer.measure(this@runWithEvents) { spark() }) }
            .retryWithPolicy(retryPolicy) { cause, attempt -> emitRetry(cause, attempt) }
            .onEach { emitCompleted() }
            .catch { emitFailed(it) }
            .onStart { emitStarted() }
            .collect()
    }

    private suspend fun SparkDeclaration.emitRetry(cause: Throwable, attempt: Int) =
        _events.emit(
            SparkEvent.Retry(
                key = key,
                retryCount = attempt,
                error = cause,
                duration = sparkTimer
                    .durationOf(this)
                    .orDefault { Duration.Companion.ZERO },
            )
        )

    private suspend fun SparkDeclaration.emitCompleted() = _events
        .emit(
            SparkEvent.Completed(
                key = key,
                duration = sparkTimer
                    .durationOf(this)
                    .orDefault { Duration.Companion.ZERO }
            ),
        )

    private suspend fun SparkDeclaration.emitFailed(e: Throwable) {
        if (e is CancellationException) throw e
        _events.emit(
            SparkEvent.Failed(
                key = key,
                error = e,
                duration = sparkTimer
                    .durationOf(this)
                    .orDefault { Duration.Companion.ZERO },
            )
        )
        if (importance == SparkImportance.CRITICAL) throw e
    }

    private suspend fun SparkDeclaration.emitStarted() =
        _events.emit(SparkEvent.Started(key))

    private suspend fun startAwaitable() = config
        .declarations
        .filter { it.type == SparkType.AWAITABLE }
        .forEach { sparkDeclaration -> sparkDeclaration.runWithEvents() }

    private suspend fun startAsyncSparks(jobs: Map<Key, Deferred<Unit>>) {
        config
            .declarations
            .filter { it.type == SparkType.TRACKABLE }
            .map(SparkDeclaration::key)
            .mapNotNull(jobs::get)
            .awaitAll()
        _isTrackableInitialized.value = true
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
