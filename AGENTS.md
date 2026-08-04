# Agent Instructions

## Project

- A small music player and Navidrome client using the OpenSubsonic API.
- Built with Kotlin Multiplatform (KMP).

## Current Focus

- Work only on Android for now.
- Do not modify or build the iOS app unless explicitly requested.
- Keep shared KMP code compatible with a future iOS app.

## Dependency Injection

- Use Metro as the project's dependency injection framework.
- Register and resolve new application dependencies through Metro dependency graphs and binding containers.
- Prefer constructor injection or Metro providers; do not manually assemble dependency graphs in activities, composables, or feature code.
- Keep platform-specific graph inputs and bindings in the corresponding platform source set while keeping shared bindings in `commonMain`.

## Loading State

- Use the shared `LoadableState<T>` for asynchronous UI data instead of separate `isLoading`, `error`, and content fields.
- Represent successful data with `LoadableState.Content(content)`.
- Represent an in-progress request with `LoadableState.Loading(content)`. Preserve previously loaded or cached content when it is available; use `null` only when there is no content yet.
- Represent a failed request with `LoadableState.Failure(message, content)`. Preserve previously loaded or cached content when it is available so the UI can keep rendering it alongside the error.
- Reuse this state across features rather than creating feature-specific loading/error state models.
