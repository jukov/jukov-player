#!/usr/bin/env bash
set -euo pipefail

./scripts/test-configure-android-sdk.sh

./gradlew --no-daemon --stacktrace \
  :shared:testAndroidHostTest \
  :androidApp:assembleDebug \
  :androidApp:lintDebug
