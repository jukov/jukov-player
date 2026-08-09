#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS checks require macOS." >&2
  exit 1
fi

if ! xcodebuild -version >/dev/null 2>&1; then
  echo "xcodebuild requires a full Xcode installation. Select it with xcode-select before running iOS checks." >&2
  exit 1
fi

./gradlew --no-daemon --stacktrace :shared:iosSimulatorArm64Test

xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath .context/DerivedData/iosApp \
  CODE_SIGNING_ALLOWED=NO \
  build
