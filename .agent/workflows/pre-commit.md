---
description: Run pre-commit checks (build, test, detekt)
---

This workflow should be executed before any commit to ensure code quality and prevent CI failures.

// turbo
1. Run Build
```bash
./gradlew build --no-daemon
```

// turbo
2. Run Tests
```bash
./gradlew test --no-daemon
```

// turbo
3. Run Detekt
```bash
./gradlew detekt --no-daemon
```
