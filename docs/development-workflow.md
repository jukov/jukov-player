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
- Unit check: `./scripts/check-unit.sh`
- Fast check: `./scripts/check-fast.sh`
- Full local check: `./scripts/check-full.sh`
- iOS-only check: `./scripts/check-ios.sh`
- Android device smoke, API 28 and 36: `./scripts/check-android-device.sh`
- Android host coverage report: `./scripts/check-coverage.sh`

`check-unit.sh` runs shared Android host tests for the shortest development loop. `check-fast.sh` adds the debug Android build and Android lint. `check-full.sh` runs fast checks and adds iOS tests and a Debug simulator build on macOS. iOS checks require a full Xcode installation selected by `xcode-select`.

## CI

Every PR and push to `main` runs Linux fast checks:

- `:shared:testAndroidHostTest`
- `:androidApp:assembleDebug`
- `:androidApp:lintDebug`

macOS iOS checks run only when needed:

- on PRs that touch shared common/iOS sources, `iosApp/**`, Gradle files, or build logic;
- on nightly scheduled runs;
- on manual `workflow_dispatch`.

The iOS simulator checks start independently from Linux checks. The slower Release device build runs only in the nightly schedule and does not block pull requests.

Every PR also reports a stable `Required PR checks` gate. Branch protection for `main`
requires this gate and an up-to-date branch before merge. The gate requires Linux fast
checks on every PR and macOS iOS checks whenever the changed paths make them relevant.
Changes to `main` must go through a pull request; no approving GitHub review is required
by branch protection because independent AI review is tracked separately in the PR.

Android device smoke checks run nightly on API 28 and API 36 and can be started manually with
`workflow_dispatch`. They use deterministic in-process HTTP responses and do not contact a real
Navidrome server. Device checks do not block pull requests.

The coverage job publishes Kover XML and HTML reports for common and Android host code. It is
intentionally non-blocking while the project establishes a useful baseline; generated Metro,
Room, and resource code is excluded.

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

Gradle daemons, Kotlin daemons, Android emulators, iOS simulators, `~/.gradle`, and `~/.konan` are shared machine resources. Conductor run scripts in this workspace are nonconcurrent so a device check does not overlap another run started from the same workspace.

## Definition of Done

- Relevant tests were added or an explicit test-gap rationale is documented.
- `./scripts/check-fast.sh` passed locally, or the reason it could not run is documented.
- Required CI checks passed.
- Independent AI review approved the exact latest head SHA.
- No blocking findings remain.
- Merge is performed manually.
