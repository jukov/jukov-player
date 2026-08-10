# Agent Instructions

## Project

- A small music player and Navidrome client using the OpenSubsonic API.
- Built with Kotlin Multiplatform (KMP).

## Current Platforms

- Android and iOS are both active product targets.
- Keep shared implementation in `commonMain` where practical, and use platform source sets only for platform-specific APIs or integrations.
- Maintain thin Android and iOS entry points around shared UI and shared application logic.

## Shared Code

- Keep as much application code as possible in `commonMain`, including architecture, business logic, networking, storage abstractions, dependency injection, navigation, presentation logic, and shared UI.
- Add code to a platform source set only when it depends on an inherently platform-specific API or implementation.
- Keep platform entry points and integrations thin, and keep shared code compatible with a future iOS app.

## Dependency Injection

- Use Metro as the project's dependency injection framework.
- Register and resolve new application dependencies through Metro dependency graphs and binding containers.
- Prefer constructor injection or Metro providers; do not manually assemble dependency graphs in activities, composables, or feature code.

## Loading State

- Use the shared `LoadableState<T>` for asynchronous UI data instead of separate `isLoading`, `error`, and content fields.
- Represent successful data with `LoadableState.Content(content)`.
- Represent an in-progress request with `LoadableState.Loading(content)`. Preserve previously loaded or cached content when it is available; use `null` only when there is no content yet.
- Represent a failed request with `LoadableState.Failure(message, content)`. Preserve previously loaded or cached content when it is available so the UI can keep rendering it alongside the error.
- Reuse this state across features rather than creating feature-specific loading/error state models.

## State Updates

- Prefer `MutableStateFlow.update { ... }` when deriving a new state from the current value.
- Use direct `.value = ...` assignment only for intentional replacement that does not depend on the previous state.
- Always use named arguments when passing boolean literals.

## Kotlin Style

- Always use braces for conditional branches. Do not write single-line `if`/`else` branches without braces.

## Navigation

- Use Navigation 3 as the application's navigation framework.
- Render screens through `NavDisplay`; do not select top-level screens manually with conditional UI in `App`.
- Pass navigation callbacks into feature composables instead of passing the back stack or navigation framework types into them.
- Replace authentication-flow destinations instead of retaining them in history: Back must not return to login after a successful login or to authorized screens after logout.

## Development Workflow

- Work in one Conductor workspace and one task branch per task.
- Start by reading the relevant code and asking questions only when the requirements are materially ambiguous and cannot be answered from the repository.
- Keep implementation, tests, and documentation changes in the same branch when they belong to the same task.
- Before handoff, run `./scripts/check-fast.sh`. Run `./scripts/check-full.sh` when changes touch shared platform behavior, Gradle, CI, or iOS-relevant code.
- Always create or update the GitHub Pull Request yourself after local checks pass; do not hand PR creation back to the user.
- If the primary PR command fails, exhaust other available authenticated GitHub mechanisms before reporting a blocker. A compare link is only a fallback when the environment has no working PR-write capability.
- Keep the PR title, description, verification results, residual risks, and reviewed head SHA current.
- Merge remains manual.
- Do not enable auto-merge or merge from an agent unless the user explicitly requests it.

## Testing Requirements

- New or changed behavior must include meaningful regression tests at the lowest practical layer.
- Prefer `commonTest` for shared domain, data mapping, repository, use-case, ViewModel, navigation, and serialization behavior.
- Use Android host tests for Android-specific logic that can run on the JVM with Android resources.
- Use Android instrumented tests only for behavior requiring a device/emulator.
- Use iOS tests for iOS-specific shared logic and iOS integration boundaries.
- Do not add tests solely to increase coverage percentage. Existing low coverage must not block unrelated work.
- If a change cannot reasonably be tested in this task, state the reason and residual risk in the PR.

## Pull Request Readiness

- A PR is ready for manual merge only when CI is green, required local checks have run, and independent AI review has no blocking findings for the latest commit SHA.
- Any new push invalidates prior approval. Request review again for the new head SHA.
- Blocking findings are `critical`, `high`, and unresolved `medium` findings unless explicitly accepted by the user.

## Independent AI Review

- Code review is performed by a separate, ephemeral, read-only Codex session.
- The reviewer must not use the implementer's transcript or reasoning.
- The reviewer must not edit files or fix code.
- The reviewer reviews only `git diff origin/main...<head-sha>` and must report the exact reviewed SHA.
- Reviewer output must follow `docs/ai-code-review.md`.
