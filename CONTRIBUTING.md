# Contributing to OkCache

Thanks for your interest in contributing to **OkCache**! We welcome contributions of all kinds—whether it's bug reports, new features, code improvements, or documentation updates.

## Getting Started

1. **Fork the repository**
2. **Clone your fork**
   ```bash
   git clone https://github.com/your-username/okcache.git
   ```
3. **Create a branch for your change**
   ```bash
   git checkout -b my-feature-branch
   ```
4. **Make your changes locally**
5. **Run the tests**
6. **Ensure code passes static analysis (Detekt)**
   ```bash
   ./gradlew detekt
   ```
7. **Commit your changes with a clear message**
8. **Push your branch to your fork**
9. **Open a Pull Request**

## Code Style

- Use Kotlin idiomatic conventions.
- Format code using `ktlint` or your IDE's auto-formatting.
- Keep code modular and testable.
- Include unit tests for new logic or behavior changes.
- Use descriptive names and keep classes/methods small and focused.
- All new code must be covered by unit tests.

## Commit Messages

Use clear and concise commit messages. Use imperative mood and keep the format:

```
type: short description

[optional body with more context]
```

Example:
```
fix: handle NoCachedResponseException in CACHE_ONLY strategy
```

## Reporting Issues

If you encounter bugs or unexpected behavior, please open an issue with:

- A descriptive title
- Reproduction steps
- Expected behavior vs actual behavior
- Version info (library, Android, etc.)

## Questions & Discussions

For help, suggestions, or discussions, you can open a [GitHub Discussion](https://github.com/ktomek/okcache/discussions) or post under an issue.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
