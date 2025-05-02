package com.github.ktomek.initspark

import com.github.ktomek.funktional.lift
import com.github.ktomek.funktional.onNull
import com.github.ktomek.funktional.orDefault
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

/**
 * Public interface exposing read-only access to spark timing data.
 */
interface SparkTimingInfo {
    /**
     * Retrieves the measured duration for a specific spark declaration.
     *
     * @param declaration the spark declaration to query.
     * @return the recorded duration, or null if not available.
     */
    fun get(declaration: SparkDeclaration): Duration?

    /**
     * Returns all recorded spark timings.
     *
     * @return a map of spark declarations to their measured durations.
     */
    fun all(): Map<SparkDeclaration, Duration>

    /**
     * Returns the total cumulative duration across all measured sparks.
     *
     * @return the aggregated duration.
     */
    fun total(): Duration

    /**
     * Returns the total duration grouped by SparkType.
     *
     * @return a map from spark type to total duration.
     */
    fun groupedByType(): Map<SparkType, Duration>

    /**
     * Returns the duration between the first spark start and the last spark stop.
     *
     * @return the window duration if available, or null.
     */
    fun windowDuration(): Duration?

    /**
     * Returns the duration windows grouped by SparkType.
     *
     * @return a map from spark type to window duration.
     */
    fun windowByType(): Map<SparkType, Duration>
}

/**
 * Utility class for measuring execution time of sparks.
 */
internal class SparkTimer : SparkTimingInfo {
    private val timings = mutableMapOf<SparkDeclaration, Duration>()
    private val startTimes = mutableMapOf<SparkDeclaration, ValueTimeMark>()
    private var firstStartTime: ValueTimeMark? = null
    private var lastStopTime: ValueTimeMark? = null

    private val typeWindowMarks = mutableMapOf<SparkType, Pair<ValueTimeMark?, ValueTimeMark?>>()
    private val mutex = Mutex()

    /**
     * Starts measuring time for a given spark declaration.
     *
     * @param declaration SparkDeclaration to measure.
     */
    suspend fun start(declaration: SparkDeclaration) {
        mutex.withLock {
            val now = markNow()
            startTimes[declaration] = now
            firstStartTime.onNull { firstStartTime = now }
            val type = declaration.type
            val (startMark, stopMark) = typeWindowMarks[type].orDefault { null to null }
            startMark.onNull { typeWindowMarks[type] = now to stopMark }
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
            val mark = startTimes.remove(declaration)
                ?: error("Timer was not started for declaration: $declaration")
            timings[declaration] = mark.elapsedNow()
            lastStopTime = markNow()

            val type = declaration.type
            val (startMark, _) = typeWindowMarks[type].orDefault { null to null }
            typeWindowMarks[type] = startMark to lastStopTime
        }
    }

    /**
     * Returns the recorded duration for the given declaration, or null if not recorded.
     */
    override fun get(declaration: SparkDeclaration): Duration? = timings[declaration]

    /**
     * Returns all measured timings.
     */
    override fun all(): Map<SparkDeclaration, Duration> = timings.toMap()

    /**
     * Returns the total duration of all sparks.
     */
    override fun total(): Duration = timings.values.fold(Duration.ZERO, Duration::plus)

    /**
     * Groups timings by SparkType.
     *
     * @return Map of SparkType to aggregated Duration.
     */
    override fun groupedByType(): Map<SparkType, Duration> =
        timings.entries.groupBy { it.key.type }.mapValues { (_, entries) ->
            entries.fold(Duration.ZERO) { acc, entry -> acc + entry.value }
        }

    /**
     * Returns the duration between the first start and last stop.
     */
    override fun windowDuration(): Duration? = lift(firstStartTime, lastStopTime) { start, stop ->
        start.elapsedNow() - stop.elapsedNow()
    }

    override fun windowByType(): Map<SparkType, Duration> = typeWindowMarks
        .mapNotNull { (type, marks) ->
            val (start, stop) = marks
            lift(start, stop) { t1, t2 -> type to t1.elapsedNow() - t2.elapsedNow() }
        }
        .toMap()

    companion object {
        @Volatile
        private var instance: SparkTimer? = null

        /**
         * Factory method to obtain a singleton instance of InitSpark.
         *
         * @param config Configuration containing all spark declarations.
         * @param scope Coroutine scope used for asynchronous operations.
         * @return a singleton instance of InitSpark.
         */
        fun getInstance(): SparkTimer =
            instance.orDefault {
                synchronized(this) {
                    instance
                        .orDefault { SparkTimer() }
                        .also { instance = it }
                }
            }
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
