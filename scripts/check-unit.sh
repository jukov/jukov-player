#!/usr/bin/env bash
set -euo pipefail

./gradlew --stacktrace :shared:testAndroidHostTest
