#!/usr/bin/env bash
set -euo pipefail

./scripts/test-configure-android-sdk.sh

./gradlew --stacktrace \
  :shared:testAndroidHostTest \
  :androidApp:assembleDebug \
  :androidApp:lintDebug
