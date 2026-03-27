![Build](https://github.com/ktomek/initspark/actions/workflows/ci.yml/badge.svg)
[![codecov](https://codecov.io/gh/ktomek/initspark/branch/main/graph/badge.svg)](https://codecov.io/gh/ktomek/initspark)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![GitHub release](https://img.shields.io/github/v/release/ktomek/initspark)
![GitHub issues](https://img.shields.io/github/issues/ktomek/initspark)
![GitHub pull requests](https://img.shields.io/github/issues-pr/ktomek/initspark)
[![JitPack](https://jitpack.io/v/ktomek/initspark.svg)](https://jitpack.io/#ktomek/initspark)


# InitSpark

**InitSpark** is a lightweight, coroutine-based startup orchestration library for Kotlin applications. It provides a structured way to declare, sequence, and execute initialization tasks (called *sparks*) during your app's startup phase.

## Features

- 🔥 Declarative DSL to define sparks
- ⏱️ Time tracking for individual sparks and phases
- ⚙️ Three execution modes: `await`, `async`, and `spark`
- 🌲 Dependency management between sparks (with cycle detection)
- ⚠️ Spark importance levels: `CRITICAL` (fail-fast) and `OPTIONAL` (failure-tolerant)
- 🔁 Configurable retry policies with `None`, `Fixed`, and `Exponential` backoff
- 📡 Reactive `SparkEvent` stream for lifecycle monitoring
- 🔑 Flexible `Key` interface for spark identification
- 🧪 Built-in testing support
- 📊 Performance metrics via `SparkTimingInfo`

## Installation

Add to your `build.gradle`:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.ktomek:initspark:<version>")
}
```

## Getting Started

### 1. Implement `Spark`

```kotlin
class DatabaseSpark @Inject constructor(...) : Spark {
    override suspend fun execute() { /* initialize database */ }
}
```

### 2. Build a configuration

```kotlin
val sparks = setOf(
    DatabaseSpark(),
    NotificationSpark(),
    AnalyticsSpark(),
    /* ... */
)

val config = buildSparks(sparks) {
    // Sequential, must complete before next spark starts
    await { System.loadLibrary("crypto-lib") }
    await<LoggerSpark>()
    await<ActivityLifecycleSpark>()

    val ioContext = Dispatchers.IO
    val coreDeps = setOf(Key("Database"))

    // Parallel, completion is tracked
    async<DatabaseSpark>(
        key = "Database".asKey(),
        context = ioContext
    )
    async<NotificationSpark>(
        context = ioContext,
        needs = coreDeps,
        policy = SparkPolicy(importance = SparkImportance.OPTIONAL)
    )
    async<AnalyticsSpark>(
        context = ioContext,
        needs = coreDeps,
        policy = SparkPolicy(
            retry = RetryPolicy(
                retryCount = 3,
                backoff = Backoff.Exponential(initialDelayMillis = 200)
            )
        )
    )

    // Parallel, fire-and-forget (not tracked)
    spark<ConsentManagerSpark>(context = ioContext, needs = coreDeps)
}
```

### 3. Run

```kotlin
val initSpark = InitSpark(config, CoroutineScope(Dispatchers.Default))

// Suspending version (preferred)
initSpark.initialize()

// Blocking version (for Java interop or legacy code)
initSpark.initializeBlocking()
```

## Spark Types

| Builder function | Execution | Tracked | Default key |
|---|---|---|---|
| `await { }` / `await<T>()` | Sequential | ✅ | Class simple name |
| `async { }` / `async<T>()` | Parallel | ✅ | Class simple name |
| `spark { }` / `spark<T>()` | Parallel | ❌ | Class simple name |

Each builder function accepts:

| Parameter | Type | Description |
|---|---|---|
| `key` | `Key?` | Optional unique identifier (defaults to class name) |
| `needs` | `Set<Key>` | Keys of sparks that must complete first |
| `context` | `CoroutineContext` | Coroutine dispatcher |
| `policy` | `SparkPolicy` | Importance and retry configuration |

## Spark Keys

`Key` is an interface, letting you use any type with proper equality — `data object`, `enum` entry, or a plain string:

```kotlin
// String-backed key (default)
"Database".asKey()           // or Key("Database")

// Custom key type (recommended for robustness)
data object DatabaseKey : Key
enum class AppKey : Key { DATABASE, ANALYTICS }
```

## Importance Levels

Control how failures propagate using `SparkPolicy`:

```kotlin
// CRITICAL (default): failure throws and halts initialization
async<DatabaseSpark>(policy = SparkPolicy(importance = SparkImportance.CRITICAL))

// OPTIONAL: failure is logged and emitted as a SparkEvent.Failed, but other sparks continue
async<AnalyticsSpark>(policy = SparkPolicy(importance = SparkImportance.OPTIONAL))
```

## Retry Policies

Attach a `RetryPolicy` to automatically retry failing sparks:

```kotlin
val policy = SparkPolicy(
    retry = RetryPolicy(
        retryCount = 3,
        backoff = Backoff.Exponential(initialDelayMillis = 100L, factor = 2.0)
    )
)
```

### Backoff strategies

| Strategy | Description |
|---|---|
| `Backoff.None` | No delay between retries (default) |
| `Backoff.Fixed(delayMillis)` | Constant delay |
| `Backoff.Exponential(initialDelayMillis, factor)` | Delay × factor on each attempt |

## Observing Lifecycle Events

Use the `events` flow to receive real-time lifecycle updates from the orchestrator:

```kotlin
launch {
    initSpark.events.collect { event ->
        when (event) {
            is SparkEvent.Started   -> log("▶ ${event.key} started")
            is SparkEvent.Completed -> log("✅ ${event.key} done in ${event.duration}")
            is SparkEvent.Failed    -> log("❌ ${event.key} failed: ${event.error}")
            is SparkEvent.Retry     -> log("🔁 ${event.key} retry #${event.retryCount}")
        }
    }
}
```

## Waiting for Initialization

```kotlin
// Suspend until all TRACKABLE sparks are done
initSpark.waitUntilTrackableInitialized()

// Suspend until ALL sparks (including fire-and-forget) are done
initSpark.waitUntilInitialized()

// Or observe via StateFlow
initSpark.isTrackableInitialized.collect { ready -> if (ready) onReady() }
initSpark.isInitialized.collect { ready -> if (ready) onFullyReady() }
```

## Timing API

Access detailed performance metrics after initialization:

```kotlin
initSpark.waitUntilInitialized()

with(initSpark.timing) {
    // Per-spark durations
    allDurations().forEach { (declaration, duration) ->
        Timber.d("Spark '${declaration.key}' [${declaration.type}] took $duration")
    }

    // Cumulative total (sum of all individual durations)
    Timber.d("Sum of all durations: ${sumOfDurations()}")
    Timber.d("Sum by type: ${sumOfDurationsByType()}")

    // Wall-clock window (first start → last finish)
    Timber.d("Total wall-clock time: ${executionDelta()}")
    Timber.d("Wall-clock by type: ${executionDeltaByType()}")
}
```

### Timing methods

| Method | Returns |
|---|---|
| `durationOf(declaration)` | Duration for one spark, or `null` |
| `allDurations()` | `Map<SparkDeclaration, Duration>` |
| `sumOfDurations()` | Cumulative sum of all measured durations |
| `sumOfDurationsByType()` | Cumulative sum grouped by `SparkType` |
| `executionDelta()` | Wall-clock window (first start → last stop) |
| `executionDeltaByType()` | Wall-clock window grouped by `SparkType` |

## Contributing

Contributions are welcome!  
Please review our [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, testing, and how to submit pull requests.


## License

This project is licensed under the MIT License.

---

### MIT License

```
MIT License

Copyright (c) 2023 ktomek

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

