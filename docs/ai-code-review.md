# Independent AI Code Review

The reviewer is a separate, ephemeral, read-only Codex session. It must not use the implementer's transcript, reasoning, or local uncommitted context.

## Reviewer Inputs

- Base branch: `origin/main`
- Head SHA: the exact commit SHA requested for review
- Diff command: `git diff origin/main...<head-sha>`
- Optional PR context: title, description, and CI result summary

The reviewer must verify that the current head SHA matches the requested SHA. If it does not match, return `CHANGES_REQUESTED` with a stale-review finding.

## Scope

Review for bugs, behavioral regressions, missing tests, build/CI risks, security issues, data loss, and mismatches with `AGENTS.md`.

Do not request changes for subjective style unless it harms maintainability or violates repository instructions. Do not edit files.

## Output Format

Return JSON:

```json
{
  "decision": "APPROVE",
  "base_ref": "origin/main",
  "head_sha": "0000000000000000000000000000000000000000",
  "reviewed_diff": "git diff origin/main...0000000000000000000000000000000000000000",
  "findings": [
    {
      "severity": "high",
      "file": "shared/src/commonMain/kotlin/example/File.kt",
      "line": 42,
      "title": "Short issue title",
      "details": "Explain the concrete bug or risk.",
      "suggested_fix": "Describe the minimal fix."
    }
  ],
  "test_gaps": [
    {
      "file": "shared/src/commonMain/kotlin/example/File.kt",
      "details": "Untested behavior and why it matters."
    }
  ],
  "stale": false
}
```

Use `APPROVE` only when there are no blocking findings for the exact reviewed SHA.

## Severity

- `critical`: data loss, security issue, crash on common path, or release-blocking build break.
- `high`: clear user-visible regression, broken core flow, or missing test for risky changed behavior.
- `medium`: plausible bug or important maintainability/test gap that should usually be fixed before merge.
- `low`: minor issue that can safely follow up.
- `nit`: optional polish.

`critical` and `high` always block merge. `medium` blocks merge unless the user explicitly accepts the risk. `low` and `nit` do not block merge.

## State Machine

1. `IMPLEMENTING`
2. `PR_OPENED`
3. `CI_RUNNING`
4. `REVIEW_REQUESTED(head_sha)`
5. `APPROVED(head_sha)` or `CHANGES_REQUESTED(head_sha)`
6. If changes are requested: `FIXING -> PUSH_NEW_SHA -> CI_RUNNING -> REVIEW_REQUESTED(new_sha)`
7. `READY_FOR_MANUAL_MERGE` only when CI and approval are for the latest SHA.

Any push after approval returns the PR to `CI_RUNNING` and invalidates the prior approval.
