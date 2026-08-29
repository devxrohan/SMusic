# Contributing to SMusic

Thanks for taking the time to contribute! Whether it's a bug report, a feature idea, or a pull request, it's welcome here.

## Reporting Bugs

Before opening an issue:

1. Check the existing [Issues](../../issues) to see if it's already been reported.
2. Make sure you're on the latest release — the bug may already be fixed.

When filing a bug report, please include:

- **What happened** vs **what you expected to happen**
- **Steps to reproduce**
- Your **Android version** and **device model**
- The **app version** (Settings → About)
- Screenshots or a screen recording, if it's visual

## Suggesting Features

Open an issue describing:

- The problem you're trying to solve (not just the feature itself)
- Any examples of similar features in other apps, if relevant

## Submitting a Pull Request

1. **Fork** the repo and create your branch from `main`.
2. Keep your changes **focused** — one PR per feature/fix is easier to review than one giant PR touching everything.
3. Follow the **existing code style**:
   - Kotlin idioms, not Java-in-Kotlin
   - Jetpack Compose for all UI — no XML layouts
   - Match the naming/structure conventions already used in the file you're editing
4. **Test your changes** on a real device or emulator before opening the PR — at minimum, confirm the app builds and the feature/fix actually works.
5. Write a clear PR description: what changed, and why.
6. Reference any related issue with `Fixes #123` or `Relates to #123`.

## Code Style

- Prefer Kotlin's standard library functions (`map`, `filter`, `let`, etc.) over manual loops where it keeps things readable.
- Keep Composables focused — extract a sub-composable rather than letting one function grow unwieldy.
- Add a short comment for anything non-obvious, especially workarounds for platform quirks.
- Don't introduce new dependencies for something a few lines of code can already do.

## Project Structure

See the [README](README.md#modules) for a breakdown of what each Gradle module is responsible for.

## License

By contributing, you agree that your contributions will be licensed under the project's [GPL-3.0 License](LICENSE).
