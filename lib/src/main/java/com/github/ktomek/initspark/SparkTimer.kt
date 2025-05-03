package com.github.ktomek.initspark

import com.github.ktomek.funktional.lift
import com.github.ktomek.funktional.onNull
import com.github.ktomek.funktional.orDefault
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark

/**
 * Utility class for measuring execution time of sparks.
 */
internal class SparkTimer(private val timeProvider: TimeProvider = DefaultTimeProvider) :
    SparkTimingInfo {
    private val timings = mutableMapOf<SparkDeclaration, Duration>()
    private val startTimes = mutableMapOf<SparkDeclaration, TimeMark>()
    private lateinit var firstStartTime: TimeMark
    private var totalWindowDuration: Duration? = null
    private val typeWindowMarks = mutableMapOf<SparkType, Pair<TimeMark, Duration?>>()
    private val mutex = Mutex()

    /**
     * Starts measuring time for a given spark declaration.
     *
     * @param declaration SparkDeclaration to measure.
     */
    suspend fun start(declaration: SparkDeclaration) {
        mutex.withLock {
            val now = timeProvider.markNow()
            startTimes[declaration] = now
            if (!this::firstStartTime.isInitialized) {
                firstStartTime = now
            }
            val type = declaration.type
            val (startMark, duration) = typeWindowMarks[type].orDefault { null to null }
            startMark.onNull { typeWindowMarks[type] = now to duration }
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
            totalWindowDuration = firstStartTime.elapsedNow()

            val type = declaration.type
            val (startMark, _) = typeWindowMarks[type]!!
            typeWindowMarks[type] = startMark to startMark.elapsedNow()
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
    override fun windowDuration(): Duration? = totalWindowDuration

    override fun windowByType(): Map<SparkType, Duration> = typeWindowMarks
        .mapNotNull { (type, startAndDuration) ->
            val (_, duration) = startAndDuration
            lift(type, duration) { t, d -> t to d }
        }
        .toMap()

    internal companion object {
        @Volatile
        private var instance: SparkTimer? = null

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
