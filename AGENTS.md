# Agent Instructions

## Project

- A small music player and Navidrome client using the OpenSubsonic API.
- Built with Kotlin Multiplatform (KMP).

## Current Focus

- Work only on Android for now.
- Do not modify or build the iOS app unless explicitly requested.

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

## Navigation

- Use Navigation 3 as the application's navigation framework.
- Render screens through `NavDisplay`; do not select top-level screens manually with conditional UI in `App`.
- Pass navigation callbacks into feature composables instead of passing the back stack or navigation framework types into them.
- Replace authentication-flow destinations instead of retaining them in history: Back must not return to login after a successful login or to authorized screens after logout.
