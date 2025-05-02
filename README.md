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
val config = buildSparks {
    await { SparkLogger() }
    async("LoadPrefs") { LoadPreferencesSpark() }
    spark("WarmUp") { WarmUpCacheSpark() }
}
```

Then run with:

```kotlin
val initSpark = InitSpark.getInstance(config, CoroutineScope(Dispatchers.Default))
initSpark.initialize()
```

## Spark Types

- `await {}`: runs sequentially, blocks next spark until finished
- `async(key) {}`: runs in parallel, sets `isInitialized` when done
- `spark(key) {}`: runs in parallel without tracking

## Timing API

Track execution times with:

```kotlin
val duration = initSpark.timing.get(mySpark)
val totalTime = initSpark.timing.total()
```

## Publishing

To install locally:

```bash
./gradlew publishToMavenLocal
```

## License

MIT License
