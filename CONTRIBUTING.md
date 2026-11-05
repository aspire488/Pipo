# Contributing to Pipo

Pipo is a character-led Android project with a deterministic life simulation and a browser-based 3D world. Contributions should preserve the character, reliability, accessibility and privacy boundaries.

## Before opening a pull request

1. Search existing issues and pull requests.
2. Describe the user-visible problem or engineering improvement.
3. Keep changes focused; avoid unrelated formatting or generated-file churn.
4. For behaviour changes, explain how Pipo's state, memory, mood or autonomy is affected.
5. Include screenshots or a short recording for visual changes when possible.

## Local validation

For Android changes:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

For browser-world changes, test the relevant page on desktop and a touch device where practical. Check console errors, interaction states, reduced-motion behaviour and performance.

## Pull request checklist

- [ ] The change has a clear scope and rationale.
- [ ] Relevant tests pass, or missing coverage is explained.
- [ ] No secrets, personal data, generated credentials or private files are included.
- [ ] UI changes include visual evidence where practical.
- [ ] Documentation is updated when behaviour or setup changes.
- [ ] Claims in the PR description match what was actually tested.

## Design and engineering boundaries

- Pipo remains the character; the user is not asked to manage a collection of meters.
- Core simulation behaviour should remain deterministic and testable.
- Optional online services must remain optional and fail gracefully.
- Do not add telemetry, tracking, or collection of personal data without a clear documented need.
- Avoid introducing heavyweight dependencies for small features.

By contributing, you agree that your contributions are provided under the repository's MIT license.
