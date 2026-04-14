package com.github.ktomek.initspark

import kotlin.time.Duration

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
    fun durationOf(declaration: SparkDeclaration): Duration?

    /**
     * Returns all recorded spark timings.
     *
     * @return a map of spark declarations to their measured durations.
     */
    fun allDurations(): Map<SparkDeclaration, Duration>

    /**
     * Returns the total cumulative duration across all measured sparks.
     *
     * @return the aggregated duration.
     */
    fun sumOfDurations(): Duration

    /**
     * Returns the total duration grouped by SparkType.
     *
     * @return a map from spark type to total duration.
     */
    fun sumOfDurationsByType(): Map<SparkType, Duration>

    /**
     * Returns the duration between the first spark start and the last spark stop.
     *
     * @return the execution delta duration if available, or null.
     */
    fun executionDelta(): Duration?

    /**
     * Returns the execution duration grouped by SparkType.
     *
     * @return a map from spark type to execution delta duration.
     */
    fun executionDeltaByType(): Map<SparkType, Duration>
}
