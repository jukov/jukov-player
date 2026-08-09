# Development Workflow MVP

This repository uses a lightweight task flow built around Conductor workspaces, required tests, GitHub Actions, and independent AI code review.

## Default Flow

1. Create one Conductor workspace and one branch per task.
2. Read the relevant code and clarify only materially ambiguous requirements.
3. Implement the change with focused regression tests.
4. Run local checks before handoff.
5. Create or update a GitHub Pull Request.
6. Let GitHub Actions run CI.
7. Request independent read-only Codex review for the latest commit SHA.
8. Fix blocking findings, push a new SHA, rerun CI and review.
9. Merge manually after CI is green and review has no blocking findings.

## Local Commands

- Setup check: `./scripts/check-env.sh`
- Fast check: `./scripts/check-fast.sh`
- Full local check: `./scripts/check-full.sh`
- iOS-only check: `./scripts/check-ios.sh`

`check-fast.sh` runs shared Android host tests, debug Android build, and Android lint. `check-full.sh` runs fast checks and adds iOS checks on macOS. iOS checks require a full Xcode installation selected by `xcode-select`.

## CI

Every PR and push to `main` runs Linux fast checks:

- `:shared:testAndroidHostTest`
- `:androidApp:assembleDebug`
- `:androidApp:lintDebug`

macOS iOS checks run only when needed:

- on PRs that touch `shared/**`, `iosApp/**`, Gradle files, or build logic;
- on nightly scheduled runs;
- on manual `workflow_dispatch`.

Android device smoke checks are manual-only in the MVP. Add real instrumented tests before making them required.

## Minutes Strategy

Prefer Linux for fast feedback. macOS runners are reserved for iOS-relevant changes, nightly checks, or manual verification. CI cancels superseded runs for the same ref. Artifact retention should stay short when artifacts are added later.

## Caching

CI uses `gradle/actions/setup-gradle` for Gradle caching. macOS jobs also cache `~/.konan` for Kotlin/Native dependencies. Xcode DerivedData is kept job-local through `-derivedDataPath` and is not cached in the MVP.

## Test Pyramid

- `commonTest`: shared domain, data mapping, repositories, use cases, ViewModels, navigation, serialization, and pure helpers.
- `androidHostTest`: Android-specific logic that can run without an emulator.
- Android instrumented tests: smoke and integration behavior requiring framework/device APIs.
- `iosTest`: iOS-specific shared logic and Kotlin/Native integration behavior.
- iOS simulator app tests: app launch and high-value flows once iOS development starts.

## Worktree Notes

The project can run from a Git worktree. Conductor copies `local.properties` into new workspaces when available. Avoid global build output paths; iOS scripts use `.context/DerivedData` to avoid DerivedData conflicts between workspaces.

Gradle daemons, Kotlin daemons, Android emulators, iOS simulators, `~/.gradle`, and `~/.konan` are shared machine resources. Avoid running heavy emulator/simulator checks concurrently across many local workspaces.

## Definition of Done

- Relevant tests were added or an explicit test-gap rationale is documented.
- `./scripts/check-fast.sh` passed locally, or the reason it could not run is documented.
- Required CI checks passed.
- Independent AI review approved the exact latest head SHA.
- No blocking findings remain.
- Merge is performed manually.
