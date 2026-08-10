#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf -- "$test_root"' EXIT

assert_file_content() {
  local file="$1"
  local expected="$2"
  local actual
  actual="$(<"$file")"

  if [[ "$actual" != "$expected" ]]; then
    echo "Expected $file to contain '$expected', found '$actual'." >&2
    exit 1
  fi
}

mkdir -p "$test_root/home/Library/Android/sdk" "$test_root/default-workspace"
(
  cd "$test_root/default-workspace"
  env -u ANDROID_HOME -u ANDROID_SDK_ROOT HOME="$test_root/home" \
    "$repo_root/scripts/configure-android-sdk.sh"
  assert_file_content \
    "local.properties" \
    "sdk.dir=$test_root/home/Library/Android/sdk"
)

mkdir -p "$test_root/sdk with space" "$test_root/env-workspace"
(
  cd "$test_root/env-workspace"
  ANDROID_HOME="$test_root/sdk with space" HOME="$test_root/missing-home" \
    "$repo_root/scripts/configure-android-sdk.sh"
  assert_file_content \
    "local.properties" \
    "sdk.dir=$test_root/sdk\ with\ space"
)

mkdir -p "$test_root/existing-workspace"
(
  cd "$test_root/existing-workspace"
  printf 'custom=true\n' > local.properties
  ANDROID_HOME="$test_root/sdk with space" \
    "$repo_root/scripts/configure-android-sdk.sh"
  assert_file_content "local.properties" "custom=true"
)

echo "Android SDK configuration tests passed."
