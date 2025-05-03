package com.github.ktomek.initspark

import kotlin.time.TimeMark
import kotlin.time.TimeSource.Monotonic

interface TimeProvider {
    fun markNow(): TimeMark
}

object DefaultTimeProvider : TimeProvider {
    override fun markNow(): TimeMark = Monotonic.markNow()
}
