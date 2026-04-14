package com.github.ktomek.initspark

import com.github.ktomek.funktional.lift
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Utility class for measuring execution time of sparks.
 */
internal class SparkTimer(private val timeProvider: TimeProvider = DefaultTimeProvider) :
    SparkTimingInfo {
    @Volatile
    private var timings = mapOf<SparkDeclaration, Duration>()

    @Volatile
    private var startTimes = mapOf<SparkDeclaration, TimeMark>()
    private var firstStartTime: TimeMark? = null

    @Volatile
    private var totalExecutionDeltaDuration: Duration? = null

    @Volatile
    private var typeExecutionDeltaMarks = mapOf<SparkType, Pair<TimeMark, Duration?>>()
    private val mutex = Mutex()

    /**
     * Starts measuring time for a given spark declaration.
     *
     * @param declaration SparkDeclaration to measure.
     */
    suspend fun start(declaration: SparkDeclaration) {
        mutex.withLock {
            val now = timeProvider.markNow()
            startTimes = startTimes + (declaration to now)
            if (firstStartTime == null) {
                firstStartTime = now
            }
            val type = declaration.type
            if (typeExecutionDeltaMarks[type] == null) {
                typeExecutionDeltaMarks = typeExecutionDeltaMarks + (type to (now to null))
            }
        }
    }

    /**
     * Stops timing for a given spark declaration and records the elapsed duration.
     *
     * @param declaration SparkDeclaration to stop timing.
     * @throws IllegalStateException if the timer was not started for the declaration.
     */
    suspend fun stop(declaration: SparkDeclaration) {
        mutex.withLock {
            val mark = startTimes[declaration]
                ?: error("Timer was not started for declaration: $declaration")
            startTimes = startTimes - declaration
            timings = timings + (declaration to mark.elapsedNow())
            totalExecutionDeltaDuration = firstStartTime?.elapsedNow()

            val type = declaration.type
            val startMark = typeExecutionDeltaMarks[type]?.first ?: mark
            typeExecutionDeltaMarks = typeExecutionDeltaMarks + (type to (startMark to startMark.elapsedNow()))
        }
    }

    /**
     * Returns the recorded duration for the given declaration, or null if not recorded.
     */
    override fun durationOf(declaration: SparkDeclaration): Duration? = timings[declaration]

    /**
     * Returns all measured timings.
     */
    override fun allDurations(): Map<SparkDeclaration, Duration> = timings.toMap()

    /**
     * Returns the total duration of all sparks.
     */
    override fun sumOfDurations(): Duration = timings.values.fold(Duration.ZERO, Duration::plus)

    /**
     * Groups timings by SparkType.
     *
     * @return Map of SparkType to aggregated Duration.
     */
    override fun sumOfDurationsByType(): Map<SparkType, Duration> =
        timings.entries.groupBy { it.key.type }.mapValues { (_, entries) ->
            entries.fold(Duration.ZERO) { acc, entry -> acc + entry.value }
        }

    /**
     * Returns the duration between the first start and last stop.
     */
    override fun executionDelta(): Duration? = totalExecutionDeltaDuration

    override fun executionDeltaByType(): Map<SparkType, Duration> = typeExecutionDeltaMarks
        .mapNotNull { (type, startAndDuration) ->
            val (_, duration) = startAndDuration
            lift(type, duration) { t, d -> t to d }
        }
        .toMap()

    internal companion object {
        private val INSTANCE by lazy { SparkTimer() }

        fun getInstance(): SparkTimer = INSTANCE
    }
}

/**
 * Inline extension function to measure the execution duration of a block
 * and store it in the SparkTimer for the specified SparkDeclaration.
 */
internal suspend inline fun SparkTimer.measure(declaration: SparkDeclaration, block: () -> Unit) {
    this.start(declaration)
    try {
        block()
    } finally {
        this.stop(declaration)
    }
}
