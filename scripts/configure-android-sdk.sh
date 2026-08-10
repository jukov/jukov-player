#!/usr/bin/env bash
set -euo pipefail

if [[ -f "local.properties" ]]; then
  exit 0
fi

android_sdk=""
for candidate in \
  "${ANDROID_HOME:-}" \
  "${ANDROID_SDK_ROOT:-}" \
  "${HOME:-}/Library/Android/sdk" \
  "${HOME:-}/Android/Sdk"; do
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    android_sdk="$candidate"
    break
  fi
done

if [[ -z "$android_sdk" ]]; then
  echo "Android SDK location is not configured or could not be found." >&2
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT, or create local.properties with sdk.dir=<path>." >&2
  exit 1
fi

# local.properties uses Java properties escaping. Escape path characters that
# can otherwise be interpreted as separators or escape sequences.
escaped_android_sdk="${android_sdk//\\/\\\\}"
escaped_android_sdk="${escaped_android_sdk//:/\\:}"
escaped_android_sdk="${escaped_android_sdk// /\\ }"

printf 'sdk.dir=%s\n' "$escaped_android_sdk" > local.properties
echo "Configured Android SDK at $android_sdk."
