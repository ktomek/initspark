package com.github.ktomek.initspark

import kotlinx.coroutines.flow.first

/**
 * Suspends the coroutine until [SparkState] completes its initialization.
 *
 * This function collects from [SparkState.isInitialized] and resumes once the first `true` value is emitted.
 * Use this to ensure all registered sparks are completed before proceeding.
 */
suspend fun SparkState.waitUntilInitialized() = isInitialized.first { it }

/**
 * Suspends the coroutine until [SparkState] completes its initialization.
 *
 * This function collects from [SparkState.isTrackableInitialized] and resumes once the first `true` value is emitted.
 * Use this to ensure all registered sparks are completed before proceeding.
 */
suspend fun SparkState.waitUntilTrackableInitialized() = isTrackableInitialized.first { it }
