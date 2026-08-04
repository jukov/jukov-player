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
