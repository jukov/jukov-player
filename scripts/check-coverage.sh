#!/usr/bin/env bash
set -euo pipefail

./scripts/test-configure-android-sdk.sh

./gradlew --stacktrace \
  :shared:koverXmlReportAndroid \
  :shared:koverHtmlReportAndroid
