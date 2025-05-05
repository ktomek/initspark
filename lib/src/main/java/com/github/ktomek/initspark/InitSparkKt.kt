package com.github.ktomek.initspark

import kotlinx.coroutines.flow.first

/**
 * Suspends the coroutine until [InitSpark] completes its initialization.
 *
 * This function collects from [InitSpark.isInitialized] and resumes once the first `true` value is emitted.
 * Use this to ensure all registered sparks are completed before proceeding.
 */
suspend fun InitSpark.waitUntilInitialized() = isInitialized.first { it }
