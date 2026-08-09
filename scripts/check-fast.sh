#!/usr/bin/env bash
set -euo pipefail

./gradlew --no-daemon --stacktrace \
  :shared:testAndroidHostTest \
  :androidApp:assembleDebug \
  :androidApp:lintDebug
