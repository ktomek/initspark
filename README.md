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
- ⚙️ Support for `await`, `track`, and `spark` styles:
  - `await`: sequential execution
  - `track`: asynchronous with completion tracking
  - `spark`: fire-and-forget
- 🌲 Dependency management between sparks
- 🧪 Built-in testing support
- 📊 Access to performance metrics via `SparkTimer`

## Getting Started

```kotlin
val sparks = setOf(...)

val config = buildSparks(sparks) {
    await { System.loadLibrary("crypto-lib") }

    await<LoggerSpark>()
    await<ActivityLifecycleSpark>()
    await<AppObserverSpark>()

    val ioContext = Dispatchers.IO
    val coreDeps = setOf(Key("Database"))

    async<DatabaseSpark>(key = "Database".asKey(), context = ioContext)
    async<NotificationSpark>(
        context = ioContext,
        needs = coreDeps
    )
    async<AnalyticsSpark>(
        context = ioContext,
        needs = coreDeps
    )
    async<BackgroundServiceSpark>(
        context = ioContext,
        needs = coreDeps
    )
    async<MessagingSpark>(
        context = ioContext,
        needs = coreDeps
    )
    async<AttributionSpark>(
        context = ioContext,
        needs = coreDeps
    )
    spark<ConsentManagerSpark>(
        context = ioContext,
        needs = coreDeps
    )
}
```

Then run with:

```kotlin
val initSpark = InitSpark.create(config, CoroutineScope(Dispatchers.Default))
initSpark.initialize()
```

## Spark Types

- `await {}`: runs sequentially, blocks next spark until finished
- `async(key) {}`: runs in parallel, sets `isInitialized` when done
- `spark(key) {}`: runs in parallel without tracking

## Timing API

Track execution times with structured logging and insight into the InitSpark lifecycle:

```kotlin
spark.waitUntilInitialized()
with(spark.timing) {
    allDurations().forEach { (declaration, duration) ->
        Timber.d("Spark [${declaration.type}] '${declaration.key}' completed in $duration")
    }

    val windows = windowByType()
    Timber.d("Awaitable phase duration: ${windows[SparkType.AWAITABLE]}")
    Timber.d("Trackable phase duration: ${windows[SparkType.TRACKABLE]}")
    Timber.d("Fire-and-forget phase duration: ${windows[SparkType.FIRE_AND_FORGET]}")
    Timber.d("Total spark window duration: ${windowDuration()}")
    Timber.d("Overall InitSpark total duration: ${sumOfDurations()}")
}
```

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
of this software and associated documentation files (the \"Software\"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

