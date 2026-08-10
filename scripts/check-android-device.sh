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
  --release-api-36)
    task=":androidApp:pixel2Api36ReleaseSmokeAndroidTest"
    ;;
  *)
    echo "Usage: $0 [--all|--api-28|--api-36|--release-api-36]" >&2
    exit 2
    ;;
esac

./scripts/test-configure-android-sdk.sh

if [[ "$mode" == "--release-api-36" ]]; then
  release_test_dir="$(mktemp -d)"
  release_test_keystore="$release_test_dir/release-smoke.jks"
  keytool -genkeypair \
    -keystore "$release_test_keystore" \
    -storepass release-smoke \
    -keypass release-smoke \
    -alias release-smoke \
    -dname "CN=Jukov Release Smoke" \
    -keyalg RSA \
    -validity 1 \
    >/dev/null 2>&1

  export JUKOV_RELEASE_STORE_FILE="$release_test_keystore"
  export JUKOV_RELEASE_STORE_PASSWORD="release-smoke"
  export JUKOV_RELEASE_KEY_ALIAS="release-smoke"
  export JUKOV_RELEASE_KEY_PASSWORD="release-smoke"
  export JUKOV_ANDROID_TEST_BUILD_TYPE="releaseSmoke"
fi

./gradlew --stacktrace \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  "$task"
