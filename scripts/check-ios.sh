#!/usr/bin/env bash
set -euo pipefail

mode="${1:---simulator}"

if [[ "$#" -gt 1 ]]; then
  echo "Usage: $0 [--simulator|--tests|--debug-simulator|--release-device]" >&2
  exit 2
fi

case "$mode" in
  --simulator|--tests|--debug-simulator|--release-device)
    ;;
  *)
    echo "Usage: $0 [--simulator|--tests|--debug-simulator|--release-device]" >&2
    exit 2
    ;;
esac

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS checks require macOS." >&2
  exit 1
fi

xcode_developer_dir="${XCODE_DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
if ! xcodebuild -version >/dev/null 2>&1; then
  if [[ ! -x "$xcode_developer_dir/usr/bin/xcodebuild" ]]; then
    echo "xcodebuild requires a full Xcode installation. Set XCODE_DEVELOPER_DIR if Xcode is not installed at /Applications/Xcode.app." >&2
    exit 1
  fi
  export DEVELOPER_DIR="$xcode_developer_dir"
fi

if ! xcodebuild -version >/dev/null 2>&1; then
  echo "xcodebuild is not usable with DEVELOPER_DIR=$DEVELOPER_DIR." >&2
  exit 1
fi

release_build_settings="$(
  xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -showBuildSettings
)"

assert_release_setting() {
  local setting="$1"
  local expected="$2"
  if ! grep -Fq "    $setting = $expected" <<< "$release_build_settings"; then
    echo "Release signing setting $setting must resolve to '$expected'." >&2
    exit 1
  fi
}

assert_release_setting "CODE_SIGN_IDENTITY" "Apple Distribution"
assert_release_setting "CODE_SIGN_STYLE" "Manual"
assert_release_setting "DEVELOPMENT_TEAM" "UDTBP44Q7F"
assert_release_setting "PRODUCT_BUNDLE_IDENTIFIER" "com.nberezovskii.jukovplayer"
assert_release_setting "PROVISIONING_PROFILE_SPECIFIER" "Jukovplayer App Store"

for export_options in \
  iosApp/Configuration/AppStoreExportOptions.plist \
  iosApp/Configuration/AppStoreUploadOptions.plist; do
  if [[ "$(plutil -extract signingStyle raw -o - "$export_options")" != "manual" ||
        "$(plutil -extract signingCertificate raw -o - "$export_options")" != "Apple Distribution" ||
        "$(plutil -extract teamID raw -o - "$export_options")" != "UDTBP44Q7F" ||
        "$(/usr/libexec/PlistBuddy -c "Print :provisioningProfiles:com.nberezovskii.jukovplayer" "$export_options")" != "Jukovplayer App Store" ]]; then
    echo "Invalid manual signing configuration in $export_options." >&2
    exit 1
  fi
done

if [[ "$mode" == "--simulator" || "$mode" == "--tests" ]]; then
  ./gradlew --stacktrace :shared:iosSimulatorArm64Test
fi

if [[ "$mode" == "--simulator" || "$mode" == "--debug-simulator" ]]; then
  xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Debug \
    -sdk iphonesimulator \
    -derivedDataPath .context/DerivedData/iosApp \
    CODE_SIGNING_ALLOWED=NO \
    build
fi

if [[ "$mode" == "--release-device" ]]; then
  xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Release \
    -sdk iphoneos \
    -derivedDataPath .context/DerivedData/iosApp-device \
    CODE_SIGNING_ALLOWED=NO \
    build
fi
