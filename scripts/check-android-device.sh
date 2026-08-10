#!/usr/bin/env bash
set -euo pipefail

mode="${1:---all}"

case "$mode" in
  --api-28)
    task=":androidApp:pixel2Api28DebugAndroidTest"
    ;;
  --api-36)
    task=":androidApp:pixel2Api36DebugAndroidTest"
    ;;
  --all)
    task=":androidApp:androidSmokeGroupDebugAndroidTest"
    ;;
  *)
    echo "Usage: $0 [--all|--api-28|--api-36]" >&2
    exit 2
    ;;
esac

./scripts/test-configure-android-sdk.sh

./gradlew --stacktrace \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  "$task"
