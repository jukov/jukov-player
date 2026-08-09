#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required. Install JDK 21 and retry." >&2
  exit 1
fi

java_version="$(java -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')"
java_major="${java_version%%.*}"
if [[ "$java_major" != "21" ]]; then
  echo "Expected JDK 21, found Java $java_version." >&2
  exit 1
fi

if [[ ! -x "./gradlew" ]]; then
  echo "./gradlew must be executable." >&2
  exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f "local.properties" ]]; then
  echo "Android SDK location is not configured. Set ANDROID_HOME, ANDROID_SDK_ROOT, or provide local.properties." >&2
  exit 1
fi

if [[ "${CONDUCTOR_IS_LOCAL:-0}" == "1" ]]; then
  xcode_developer_dir="${XCODE_DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
  if ! xcodebuild -version >/dev/null 2>&1 &&
    [[ ! -x "$xcode_developer_dir/usr/bin/xcodebuild" ]]; then
    echo "xcodebuild requires a full Xcode installation. Set XCODE_DEVELOPER_DIR if Xcode is not installed at /Applications/Xcode.app." >&2
  fi
fi

echo "Environment looks ready for Android checks."
